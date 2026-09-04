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
import com.smartlab.agent.nudge.AgentConceptDictionary;
import com.smartlab.agent.nudge.AgentNudge;
import com.smartlab.agent.skill.WorkflowGenerationSkill;
import com.smartlab.agent.tool.AgentTool;
import com.smartlab.agent.tool.GetDeviceModelTool;
import com.smartlab.agent.tool.ListDeviceCatalogTool;
import com.smartlab.agent.tool.ListWorkflowCatalogTool;
import com.smartlab.agent.tool.SaveDraftTool;
import com.smartlab.agent.tool.SimulateWorkflowTool;
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
    private final AgentConceptDictionary concepts;
    private final AgentProperties properties;

    public AgentLoop(LlmClient llmClient, List<AgentTool> tools, WorkflowGenerationSkill skill,
                     AgentConceptDictionary concepts, AgentProperties properties) {
        this.llmClient = llmClient;
        this.tools = new LinkedHashMap<>();
        for (AgentTool tool : tools) this.tools.put(tool.name(), tool);
        this.skill = skill;
        this.concepts = concepts == null ? new AgentConceptDictionary() : concepts;
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
                properties.getModel() + " · 最多 " + Math.max(1, properties.getMaxRounds()) + " 轮",
                jsonObject()
                        .put("model", properties.getModel())
                        .put("maxRounds", Math.max(1, properties.getMaxRounds()))
                        .put("userPrompt", prompt.trim())
                        .toString());
        List<LlmToolSpec> specs = toolSpecs();
        int maxRounds = Math.max(1, properties.getMaxRounds());

        for (int round = 1; round <= maxRounds; round++) {
            LlmCompletion completion;
            session.log(round, "llm_call", "请求模型",
                    properties.getModel(),
                    null);
            try {
                completion = llmClient.complete(messages, specs);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? "调用大模型失败" : exception.getMessage();
                session.log(round, "error", "调用大模型失败", message, null);
                throw new AgentGenerateException(message, session.snapshot());
            }
            session.log(round, "llm_reply", completion.hasToolCalls() ? "模型调用工具" : "模型返回文本",
                    summarizeCompletion(completion),
                    completionPayload(completion));
            if (completion.hasToolCalls()) {
                messages.add(LlmMessage.assistant(completion.content(), completion.toolCalls()));
                for (LlmToolCall call : completion.toolCalls()) {
                    session.log(round, "tool_call", call.name(),
                            null,
                            toolCallPayload(call));
                    JsonNode result = dispatch(call, session, round);
                    messages.add(LlmMessage.tool(call.id(), call.name(), result.toString()));
                    session.log(round, "tool_result", call.name(),
                            result.path("error").isTextual() ? result.path("error").asText()
                                    : result.path("blocking").asBoolean(false) ? "有 blocking issue"
                                    : "成功",
                            pretty(result));
                    if (session.savedResult != null) {
                        session.log(round, "done", "已保存草稿",
                                "flowModelId=" + flowModelIdOf(session.savedResult),
                                null);
                        String summary = writeFinalAnswer(session, messages, session.savedResult);
                        return toResponse(session.savedResult, session, summary);
                    }
                }
                replaceWith(messages, compactHistory(messages));
                continue;
            }
            String text = completion.content() == null ? "" : completion.content().trim();
            messages.add(LlmMessage.assistant(text, List.of()));
            String nudge = AgentNudge.build(session.deviceCatalogFetched, session.workflowCatalogFetched,
                    session.lastValidateClean, session.lastSimulateWalkable, text, concepts);
            messages.add(LlmMessage.user(nudge));
            session.log(round, "nudge", "解答并继续", nudge, null);
            replaceWith(messages, compactHistory(messages));
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
            if (ListDeviceCatalogTool.NAME.equals(name) && !result.has("error")) session.deviceCatalogFetched = true;
            if (ListWorkflowCatalogTool.NAME.equals(name) && !result.has("error")) session.workflowCatalogFetched = true;
            if (ValidateWorkflowTool.NAME.equals(name)) session.recordValidate(result);
            if (SimulateWorkflowTool.NAME.equals(name)) session.recordSimulate(result);
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

    private static void replaceWith(List<LlmMessage> messages, List<LlmMessage> compacted) {
        messages.clear();
        messages.addAll(compacted);
    }

    static List<LlmMessage> compactHistory(List<LlmMessage> messages) {
        if (messages == null || messages.size() <= 2) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        List<Turn> turns = turnsOf(messages.subList(2, messages.size()));
        int lastDeviceCatalog = lastIndexWith(turns, ListDeviceCatalogTool.NAME);
        int lastWorkflowCatalog = lastIndexWith(turns, ListWorkflowCatalogTool.NAME);
        int lastValidate = lastIndexWith(turns, ValidateWorkflowTool.NAME);
        int lastSimulate = lastIndexWith(turns, SimulateWorkflowTool.NAME);
        int lastSave = lastIndexWith(turns, SaveDraftTool.NAME);
        List<LlmMessage> kept = new ArrayList<>();
        kept.add(messages.get(0));
        kept.add(messages.get(1));
        for (int index = 0; index < turns.size(); index++) {
            Turn turn = turns.get(index);
            boolean keep = turn.hasTool(GetDeviceModelTool.NAME)
                    || (turn.hasTool(ListDeviceCatalogTool.NAME) && index == lastDeviceCatalog)
                    || (turn.hasTool(ListWorkflowCatalogTool.NAME) && index == lastWorkflowCatalog)
                    || (turn.hasTool(ValidateWorkflowTool.NAME) && index == lastValidate)
                    || (turn.hasTool(SimulateWorkflowTool.NAME) && index == lastSimulate)
                    || (turn.hasTool(SaveDraftTool.NAME) && index == lastSave)
                    || turn.hasUnknownTools()
                    || (turn.nudge && index == turns.size() - 1);
            if (keep) kept.addAll(turn.messages);
        }
        return kept;
    }

    private static int lastIndexWith(List<Turn> turns, String toolName) {
        int last = -1;
        for (int index = 0; index < turns.size(); index++) {
            if (turns.get(index).hasTool(toolName)) last = index;
        }
        return last;
    }

    private static List<Turn> turnsOf(List<LlmMessage> rest) {
        List<Turn> turns = new ArrayList<>();
        int index = 0;
        while (index < rest.size()) {
            LlmMessage message = rest.get(index);
            if ("assistant".equals(message.role()) && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                List<LlmMessage> group = new ArrayList<>();
                group.add(message);
                index++;
                while (index < rest.size() && "tool".equals(rest.get(index).role())) {
                    group.add(rest.get(index));
                    index++;
                }
                turns.add(Turn.tools(group));
                continue;
            }
            List<LlmMessage> group = new ArrayList<>();
            group.add(message);
            index++;
            if ("assistant".equals(message.role()) && index < rest.size() && "user".equals(rest.get(index).role())) {
                group.add(rest.get(index));
                index++;
                turns.add(Turn.nudge(group));
                continue;
            }
            turns.add(Turn.other(group));
        }
        return turns;
    }

    private static final class Turn {
        final List<LlmMessage> messages;
        final boolean nudge;
        private final List<String> toolNames;

        private Turn(List<LlmMessage> messages, boolean nudge, List<String> toolNames) {
            this.messages = messages;
            this.nudge = nudge;
            this.toolNames = toolNames;
        }

        static Turn tools(List<LlmMessage> messages) {
            List<String> names = new ArrayList<>();
            LlmMessage assistant = messages.get(0);
            if (assistant.toolCalls() != null) {
                for (LlmToolCall call : assistant.toolCalls()) {
                    if (call != null && call.name() != null && !call.name().isBlank()) names.add(call.name());
                }
            }
            return new Turn(messages, false, names);
        }

        static Turn nudge(List<LlmMessage> messages) {
            return new Turn(messages, true, List.of());
        }

        static Turn other(List<LlmMessage> messages) {
            return new Turn(messages, false, List.of());
        }

        boolean hasTool(String name) {
            return toolNames.contains(name);
        }

        boolean hasUnknownTools() {
            for (String name : toolNames) {
                if (!ListDeviceCatalogTool.NAME.equals(name)
                        && !ListWorkflowCatalogTool.NAME.equals(name)
                        && !GetDeviceModelTool.NAME.equals(name)
                        && !ValidateWorkflowTool.NAME.equals(name)
                        && !SimulateWorkflowTool.NAME.equals(name)
                        && !SaveDraftTool.NAME.equals(name)) {
                    return true;
                }
            }
            return false;
        }
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

    private String writeFinalAnswer(Session session, List<LlmMessage> messages, JsonNode saved) {
        String fallback = fallbackSummary(saved);
        messages.add(LlmMessage.user("草稿已保存。请用中文对用户做 2～5 句说明：流程名称、对照需求做了哪些步骤（如散热、温度判断、分段加热），以及草稿编号。"
                + "不要输出节点、接口、JSON 或完整工作流定义。不要调用工具。"));
        try {
            session.log(0, "llm_call", "请求整理回答", properties.getModel(), null);
            LlmCompletion completion = llmClient.complete(messages, List.of());
            session.log(0, "llm_reply", "模型返回文本",
                    summarizeCompletion(completion),
                    completionPayload(completion));
            String text = completion.content() == null ? "" : completion.content().trim();
            if (text.isEmpty() || completion.hasToolCalls()) text = fallback;
            session.log(0, "answer", "最终回答", text, null);
            return text;
        } catch (RuntimeException exception) {
            session.log(0, "answer", "最终回答", fallback, null);
            return fallback;
        }
    }

    private static String fallbackSummary(JsonNode saved) {
        String name = saved == null ? "" : saved.path("flowModelName").asText("");
        if (name.isBlank() && saved != null) {
            name = saved.path("definition").path("metadata").path("flowModelName").asText("");
        }
        Long id = flowModelIdOf(saved);
        StringBuilder text = new StringBuilder("已按你的描述保存工作流草稿");
        if (!name.isBlank()) text.append("「").append(name).append("」");
        if (id != null) text.append(" #").append(id);
        text.append("。可在流程设计器中打开核对步骤后发布，此处不展开完整模型。");
        return text.toString();
    }

    private WorkflowGenerateResponse toResponse(JsonNode result, Session session) {
        return toResponse(result, session, null);
    }

    private WorkflowGenerateResponse toResponse(JsonNode result, Session session, String summary) {
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
                List.copyOf(session.logs),
                summary);
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
        node.put("role", "assistant");
        node.put("content", completion.content() == null ? "" : completion.content());
        ArrayNode calls = node.putArray("tool_calls");
        if (completion.toolCalls() != null) {
            for (LlmToolCall call : completion.toolCalls()) {
                calls.add(toolCallNode(call));
            }
        }
        return pretty(node);
    }

    private static String toolCallPayload(LlmToolCall call) {
        return pretty(toolCallNode(call));
    }

    private static ObjectNode toolCallNode(LlmToolCall call) {
        ObjectNode item = jsonObject();
        item.put("id", call.id() == null ? "" : call.id());
        item.put("type", "function");
        ObjectNode function = item.putObject("function");
        function.put("name", call.name() == null ? "" : call.name());
        function.put("arguments", call.argumentsJson() == null ? "{}" : call.argumentsJson());
        return item;
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
        boolean deviceCatalogFetched;
        boolean workflowCatalogFetched;
        Boolean lastValidateClean;
        Boolean lastSimulateWalkable;
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
            lastSimulateWalkable = null;
        }

        void recordSimulate(JsonNode result) {
            lastSimulateWalkable = !result.path("error").isTextual() && result.path("walkable").asBoolean(false);
        }

        String saveBlockReason() {
            if (!deviceCatalogFetched) return "保存前必须先成功调用 list_device_catalog";
            if (!workflowCatalogFetched) return "保存前必须先成功调用 list_workflow_catalog";
            if (lastValidateClean == null) return "保存前必须先调用 validate_workflow";
            if (!lastValidateClean) return "validate_workflow 仍有 blocking issue，不能保存";
            return null;
        }

        WorkflowGenerateResponse snapshot() {
            return new WorkflowGenerateResponse(null, null, null, null, List.of(), false, false, null,
                    List.copyOf(trace), List.copyOf(logs), null);
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
