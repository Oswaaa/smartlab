package com.smartlab.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.AgentProperties;
import com.smartlab.agent.api.AgentInteractionLog;
import com.smartlab.agent.api.WorkflowGenerateResponse;
import com.smartlab.agent.llm.LlmClient;
import com.smartlab.agent.llm.LlmCompletion;
import com.smartlab.agent.llm.LlmMessage;
import com.smartlab.agent.llm.LlmToolCall;
import com.smartlab.agent.llm.LlmToolSpec;
import com.smartlab.agent.skill.WorkflowGenerationSkill;
import com.smartlab.agent.tool.AgentTool;
import com.smartlab.agent.tool.ListDeviceCatalogTool;
import com.smartlab.agent.tool.SaveDraftTool;
import com.smartlab.agent.tool.ValidateWorkflowTool;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowModelDocuments;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AgentLoop {
    static final int LOG_PAYLOAD_LIMIT = 80_000;

    private final LlmClient llmClient;
    private final Map<String, AgentTool> tools;
    private final WorkflowGenerationSkill skill;
    private final AgentProperties properties;

    public AgentLoop(LlmClient llmClient, List<AgentTool> tools, WorkflowGenerationSkill skill, AgentProperties properties) {
        this.llmClient = llmClient;
        this.tools = new LinkedHashMap<>();
        for (AgentTool tool : tools) this.tools.put(tool.name(), tool);
        this.skill = skill;
        this.properties = properties;
    }

    public WorkflowGenerateResponse generate(String prompt) {
        return generate(prompt, log -> {});
    }

    public WorkflowGenerateResponse generate(String prompt, Consumer<AgentInteractionLog> logListener) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("请输入要生成的实验流程描述");
        if (!properties.llmConfigured()) throw new IllegalStateException(properties.missingConfigurationMessage());

        Session session = new Session(logListener);
        List<LlmMessage> messages = new ArrayList<>();
        String system = skill.combinedSystemMessage();
        messages.add(LlmMessage.system(system));
        messages.add(LlmMessage.user(prompt.trim()));
        session.log(0, "start", "开始生成",
                "模型 " + properties.getModel() + " · 最多 " + Math.max(1, properties.getMaxRounds()) + " 轮",
                jsonObject()
                        .put("model", properties.getModel())
                        .put("baseUrl", properties.getBaseUrl())
                        .put("userPrompt", prompt.trim())
                        .put("systemPrompt", system)
                        .toString());
        List<LlmToolSpec> specs = toolSpecs();
        int maxRounds = Math.max(1, properties.getMaxRounds());

        for (int round = 1; round <= maxRounds; round++) {
            LlmCompletion completion;
            session.log(round, "llm_call", "正在请求大模型",
                    "第 " + round + " 轮 · " + properties.getModel(),
                    null);
            try {
                completion = llmClient.complete(messages, specs);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? "调用大模型失败" : exception.getMessage();
                session.log(round, "error", "调用大模型失败", message, null);
                throw new AgentGenerateException(message, session.snapshot());
            }
            session.log(round, "llm_reply", completion.hasToolCalls() ? "模型请求调用工具" : "模型返回文本",
                    summarizeCompletion(completion),
                    completionPayload(completion));
            if (completion.hasToolCalls()) {
                messages.add(LlmMessage.assistant(completion.content(), completion.toolCalls()));
                for (LlmToolCall call : completion.toolCalls()) {
                    session.log(round, "tool_call", "调用 " + call.name(),
                            call.name(),
                            call.argumentsJson());
                    JsonNode result = dispatch(call, session, round);
                    messages.add(LlmMessage.tool(call.id(), call.name(), result.toString()));
                    session.log(round, "tool_result", "工具返回 " + call.name(),
                            result.path("error").isTextual() ? result.path("error").asText() : "成功",
                            pretty(result));
                    if (session.savedResult != null) {
                        session.log(round, "done", "已保存草稿",
                                "flowModelId=" + flowModelIdOf(session.savedResult),
                                null);
                        return toResponse(session.savedResult, session);
                    }
                }
                continue;
            }
            String text = completion.content() == null ? "" : completion.content().trim();
            messages.add(LlmMessage.assistant(text, List.of()));
            String nudge = "不要用散文结束。请按技能继续调用工具：先 list_device_catalog，再 validate_workflow，最后在无 blocking 时 save_draft。";
            messages.add(LlmMessage.user(nudge));
            session.log(round, "nudge", "系统催促继续调用工具", nudge, null);
        }

        session.log(maxRounds, "error", "未保存草稿", "已达到最大轮次仍未保存草稿", null);
        throw new AgentGenerateException("生成未完成：已达到最大轮次仍未保存草稿", session.snapshot());
    }

    private JsonNode dispatch(LlmToolCall call, Session session, int round) {
        String name = call == null ? "" : call.name();
        String id = call == null || call.id() == null || call.id().isBlank() ? UUID.randomUUID().toString() : call.id();
        AgentTool tool = tools.get(name);
        if (tool == null) {
            session.trace.add("拒绝未知工具 " + name);
            session.log(round, "gate", "拒绝未知工具", name, null);
            return error("未注册的工具: " + name);
        }
        JsonNode arguments = parseArguments(call.argumentsJson());
        if (SaveDraftTool.NAME.equals(name)) {
            String blocked = session.saveBlockReason();
            if (blocked != null) {
                session.trace.add("拒绝 save_draft：" + blocked);
                session.log(round, "gate", "拒绝 save_draft", blocked, null);
                return error(blocked);
            }
        }
        try {
            JsonNode result = tool.execute(arguments);
            session.trace.add(name);
            if (ListDeviceCatalogTool.NAME.equals(name) && !result.has("error")) session.catalogFetched = true;
            if (ValidateWorkflowTool.NAME.equals(name)) session.recordValidate(result);
            if (SaveDraftTool.NAME.equals(name) && !result.path("error").isTextual()) {
                session.savedResult = result;
            }
            return result;
        } catch (RuntimeException exception) {
            session.trace.add(name + " 失败");
            return error(exception.getMessage() == null ? name + " 执行失败" : exception.getMessage());
        } finally {
            session.lastCallId = id;
        }
    }

    private List<LlmToolSpec> toolSpecs() {
        List<LlmToolSpec> specs = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            specs.add(new LlmToolSpec(tool.name(), tool.description(), tool.parameterSchema()));
        }
        return specs;
    }

    private JsonNode parseArguments(String json) {
        if (json == null || json.isBlank()) return JsonNodeSupport.objectNode();
        try {
            JsonNode node = JsonNodeSupport.MAPPER.readTree(json);
            return node == null || node.isNull() ? JsonNodeSupport.objectNode() : node;
        } catch (Exception exception) {
            throw new IllegalArgumentException("工具参数不是合法 JSON");
        }
    }

    private JsonNode error(String message) {
        return JsonNodeSupport.objectNode().put("error", message);
    }

    private WorkflowGenerateResponse toResponse(JsonNode result, Session session) {
        WorkflowModelDocument definition = null;
        if (result.path("definition").isObject()) {
            try {
                definition = JsonNodeSupport.MAPPER.treeToValue(result.get("definition"), WorkflowModelDocument.class);
            } catch (Exception ignored) {
                definition = null;
            }
        }
        if (definition == null && (result.path("flowModelId").isNumber() || result.path("flowModelName").isTextual())) {
            definition = WorkflowModelDocuments.of(result.path("flowModelName").asText(""));
            if (result.path("flowModelId").isNumber()) definition.flowModelId(result.path("flowModelId").asLong());
            if (result.path("description").isTextual()) definition.descriptionText(result.path("description").asText());
        }
        List<WorkflowIssue> issues = new ArrayList<>();
        if (result.path("issues").isArray()) {
            for (JsonNode item : result.get("issues")) {
                try {
                    issues.add(JsonNodeSupport.MAPPER.treeToValue(item, WorkflowIssue.class));
                } catch (Exception ignored) {
                    // skip malformed issue
                }
            }
        }
        Long flowModelId = result.path("flowModelId").isNumber() ? result.path("flowModelId").asLong() : null;
        if (flowModelId == null && definition != null) flowModelId = definition.flowModelId();
        return new WorkflowGenerateResponse(
                definition,
                result.path("version").isNumber() ? result.path("version").asInt() : null,
                result.path("status").asText(null),
                result.path("predecessorId").isNumber() ? result.path("predecessorId").asLong() : null,
                List.copyOf(issues),
                result.path("executable").asBoolean(false),
                result.path("published").asBoolean(false),
                flowModelId,
                List.copyOf(session.trace),
                List.copyOf(session.logs));
    }

    private static Long flowModelIdOf(JsonNode result) {
        if (result != null && result.path("flowModelId").isNumber()) return result.path("flowModelId").asLong();
        return null;
    }

    private static ObjectNode jsonObject() {
        return JsonNodeSupport.objectNode();
    }

    private static String summarizeCompletion(LlmCompletion completion) {
        if (completion.hasToolCalls()) {
            List<String> names = new ArrayList<>();
            for (LlmToolCall call : completion.toolCalls()) names.add(call.name());
            return String.join("、", names);
        }
        String text = completion.content() == null ? "" : completion.content().trim();
        if (text.isEmpty()) return "(无文本、无工具调用)";
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    private static String completionPayload(LlmCompletion completion) {
        ObjectNode node = jsonObject();
        node.put("content", completion.content() == null ? "" : completion.content());
        ArrayNode calls = node.putArray("toolCalls");
        if (completion.toolCalls() != null) {
            for (LlmToolCall call : completion.toolCalls()) {
                ObjectNode item = calls.addObject();
                item.put("id", call.id());
                item.put("name", call.name());
                item.put("arguments", call.argumentsJson());
            }
        }
        return pretty(node);
    }

    private static String pretty(JsonNode node) {
        if (node == null) return "";
        try {
            return JsonNodeSupport.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ignored) {
            return node.toString();
        }
    }

    private static final class Session {
        boolean catalogFetched;
        Boolean lastValidateClean;
        JsonNode savedResult;
        final List<String> trace = new ArrayList<>();
        final List<AgentInteractionLog> logs = new ArrayList<>();
        final Consumer<AgentInteractionLog> logListener;
        String lastCallId;

        Session(Consumer<AgentInteractionLog> logListener) {
            this.logListener = logListener == null ? log -> {} : logListener;
        }

        void log(int round, String kind, String title, String detail, String payload) {
            AgentInteractionLog entry = new AgentInteractionLog(round, kind, title, detail, clip(payload));
            logs.add(entry);
            try {
                logListener.accept(entry);
            } catch (RuntimeException ignored) {
                // streaming failures must not abort generation
            }
        }

        void recordValidate(JsonNode result) {
            lastValidateClean = !result.path("blocking").asBoolean(false) && !hasBlockingIssues(result.path("issues"));
        }

        String saveBlockReason() {
            if (!catalogFetched) return "保存前必须先成功调用 list_device_catalog";
            if (lastValidateClean == null) return "保存前必须先调用 validate_workflow";
            if (!lastValidateClean) return "validate_workflow 仍有 blocking issue，不能保存";
            return null;
        }

        WorkflowGenerateResponse snapshot() {
            return new WorkflowGenerateResponse(null, null, null, null, List.of(), false, false, null,
                    List.copyOf(trace), List.copyOf(logs));
        }

        private boolean hasBlockingIssues(JsonNode issues) {
            if (issues == null || !issues.isArray()) return false;
            for (JsonNode issue : issues) {
                if (issue.path("blocking").asBoolean(false)) return true;
            }
            return false;
        }

        private static String clip(String payload) {
            if (payload == null || payload.isBlank()) return null;
            if (payload.length() <= LOG_PAYLOAD_LIMIT) return payload;
            return payload.substring(0, LOG_PAYLOAD_LIMIT) + "\n…(已截断，共 " + payload.length() + " 字符)";
        }
    }
}
