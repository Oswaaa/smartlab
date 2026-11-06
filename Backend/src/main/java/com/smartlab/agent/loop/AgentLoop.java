package com.smartlab.agent.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.AgentProperties;
import com.smartlab.agent.api.AgentInteractionLog;
import com.smartlab.agent.api.WorkflowGenerateResponse;
import com.smartlab.agent.chat.ChatTool;
import com.smartlab.agent.chat.ChatTools;
import com.smartlab.agent.llm.LlmClient;
import com.smartlab.agent.llm.LlmCompletion;
import com.smartlab.agent.llm.LlmMessage;
import com.smartlab.agent.llm.LlmToolCall;
import com.smartlab.agent.llm.LlmToolSpec;
import com.smartlab.agent.nudge.AgentConceptDictionary;
import com.smartlab.agent.nudge.AgentNudge;
import com.smartlab.agent.operator.CapabilityBinder;
import com.smartlab.agent.operator.ConfirmCard;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorDeviceCatalog;
import com.smartlab.agent.operator.OperatorSession;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.agent.operator.PendingAction;
import com.smartlab.agent.operator.tool.PrepareDeviceActionTool;
import com.smartlab.agent.operator.tool.PrepareTaskStartTool;
import com.smartlab.agent.operator.tool.PrepareTaskTool;
import com.smartlab.agent.skill.WorkflowGenerationSkill;
import com.smartlab.agent.tool.AgentDocuments;
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
import com.smartlab.management.entity.resource.device.DeviceModels;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 唯一的对话循环。
 *
 * <p>它同时装备两组工具：
 * <ul>
 *   <li>{@link ChatTool.Group#WORKFLOW}：查目录、校验、走图、存草稿——产出流程草稿；</li>
 *   <li>{@link ChatTool.Group#DEVICE}：查设备、准备动作、准备任务——产出确认卡片。</li>
 * </ul>
 *
 * <p>为什么合成一个循环而不是在前端或网关做意图分类：分类器要多一次模型调用，
 * 分错了用户得重说一遍，而"按流程99建任务然后再让反应釜加热"这种混合说法根本没有
 * 单一类别。把全部工具摆给模型，它自己在工具层面做选择，零额外延迟。
 *
 * <p>安全边界不变：这个循环<b>不具备</b>执行能力。设备动作只到
 * {@code prepare_device_action} 为止，真正下发必须用户在界面上点确认，
 * 走 {@code /api/agent/operator/confirm}。
 */
@Service
public class AgentLoop {
    static final int LOG_PAYLOAD_LIMIT = 80_000;

    /** 待确认卡片的有效期，与 {@code OperatorSessionStore} 里的 PENDING_TTL 保持一致。 */
    static final long PENDING_TTL_SECONDS = 300;

    private final LlmClient llmClient;
    private final Map<String, ChatTool> tools;
    private final WorkflowGenerationSkill skill;
    private final AgentConceptDictionary concepts;
    private final AgentProperties properties;
    /** 只用来把草稿里的 capability_1 / parameter_1 翻成人话，可以为 null（老单测）。 */
    private final OperatorDeviceCatalog deviceCatalog;

    @Autowired
    public AgentLoop(LlmClient llmClient, List<AgentTool> agentTools, List<OperatorTool> operatorTools,
                     WorkflowGenerationSkill skill, AgentConceptDictionary concepts,
                     AgentProperties properties, OperatorDeviceCatalog deviceCatalog) {
        this.llmClient = llmClient;
        this.tools = new LinkedHashMap<>();
        for (AgentTool tool : agentTools) register(ChatTools.of(tool));
        for (OperatorTool tool : operatorTools) register(ChatTools.of(tool));
        this.skill = skill;
        this.concepts = concepts == null ? new AgentConceptDictionary() : concepts;
        this.properties = properties;
        this.deviceCatalog = deviceCatalog;
    }

    /** 兼容只装了流程工具的旧调用（含老单测）。 */
    public AgentLoop(LlmClient llmClient, List<AgentTool> agentTools, WorkflowGenerationSkill skill,
                     AgentProperties properties) {
        this(llmClient, agentTools, List.of(), skill, new AgentConceptDictionary(), properties, null);
    }

    /** 兼容带概念字典的旧调用。 */
    public AgentLoop(LlmClient llmClient, List<AgentTool> agentTools, WorkflowGenerationSkill skill,
                     AgentConceptDictionary concepts, AgentProperties properties) {
        this(llmClient, agentTools, List.of(), skill, concepts, properties, null);
    }

    private void register(ChatTool tool) {
        ChatTool existing = tools.putIfAbsent(tool.name(), tool);
        if (existing != null) {
            throw new IllegalStateException("工具名重复: " + tool.name()
                    + "（" + existing.getClass().getName() + " 与 " + tool.getClass().getName() + "）");
        }
    }

    // ------------------------------------------------------------------ 入口

    /** 老入口：无会话、单轮、只为生成流程草稿。 */
    public WorkflowGenerateResponse generate(String prompt) {
        return generate(prompt, log -> {});
    }

    public WorkflowGenerateResponse generate(String prompt, Consumer<AgentInteractionLog> logListener) {
        OperatorSession session = new OperatorSession(UUID.randomUUID().toString());
        return chat(session, new OperatorContext(session, null, null), prompt, logListener);
    }

    /**
     * 会话式入口：一次调用 = 用户说一句话。
     *
     * <p>模型可能在这次调用里只问一句（返回文本，不调工具），也可能调完工具生成卡片或草稿。
     * 三种收尾都在这里判定，见 {@link #settle}。
     */
    public WorkflowGenerateResponse chat(OperatorSession session, OperatorContext context, String prompt,
                                         Consumer<AgentInteractionLog> logListener) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("请输入要做什么");
        if (!properties.llmConfigured()) throw new IllegalStateException(properties.missingConfigurationMessage());

        RunState state = new RunState(logListener);
        state.restore(session.messages());
        state.currentUserText = prompt.trim();
        state.flowIntentSeen = state.looksLikeFlowRequest();
        List<LlmMessage> messages = new ArrayList<>(session.messages());
        if (messages.isEmpty()) {
            messages.add(LlmMessage.system(skill.combinedSystemMessage()));
        }
        messages.add(LlmMessage.user(prompt.trim()));

        state.log(0, "start", "开始处理",
                properties.getModel() + " · 最多 " + maxRounds() + " 轮 · " + tools.size() + " 个工具",
                jsonObject()
                        .put("model", properties.getModel())
                        .put("maxRounds", maxRounds())
                        .put("toolCount", tools.size())
                        .put("userPrompt", prompt.trim())
                        .toString());

        List<LlmToolSpec> specs = toolSpecs();
        for (int round = 1; round <= maxRounds(); round++) {
            LlmCompletion completion;
            state.log(round, "llm_call", "请求模型", properties.getModel(), null);
            try {
                completion = llmClient.complete(messages, specs);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? "调用大模型失败" : exception.getMessage();
                state.log(round, "error", "调用大模型失败", message, null);
                session.resetMessages(messages);
                throw new AgentGenerateException(message, state.snapshot());
            }
            state.log(round, "llm_reply", completion.hasToolCalls() ? "模型调用工具" : "模型返回文本",
                    summarizeCompletion(completion), completionPayload(completion));

            if (completion.hasToolCalls()) {
                messages.add(LlmMessage.assistant(completion.content(), completion.toolCalls()));
                for (LlmToolCall call : completion.toolCalls()) {
                    state.log(round, "tool_call", call.name(), null, toolCallPayload(call));
                    JsonNode result = dispatch(call, context, state, round);
                    messages.add(LlmMessage.tool(safeId(call), call.name(), result.toString()));
                    state.log(round, "tool_result", call.name(), describeResult(result), pretty(result));
                    // 草稿一旦落库就收尾：后续再让模型说话容易把已保存的草稿改乱
                    if (state.savedResult != null) {
                        state.log(round, "done", "已保存草稿",
                                "flowModelId=" + flowModelIdOf(state.savedResult), null);
                        String summary = writeFinalAnswer(state, messages);
                        session.resetMessages(messages);
                        return build(state.savedResult, state, summary, session, null);
                    }
                    // 流程草稿改走确认卡片：同样在这里收尾，避免模型拿到"待确认"之后
                    // 又去补一轮工具调用，把已经整理好的稿子改乱
                    if (state.flowDraftPrepared) {
                        state.log(round, "done", "已生成确认卡片",
                                "流程草稿「" + state.flowDraftName + "」等待用户确认", null);
                        String summary = writeFinalAnswer(state, messages, true);
                        session.resetMessages(messages);
                        return build(null, state, summary, session, null);
                    }
                }
                replaceWith(messages, compactHistory(messages));
                continue;
            }

            String text = completion.content() == null ? "" : completion.content().trim();
            messages.add(LlmMessage.assistant(text, List.of()));

            // 纯文本收尾：设备卡片已备好，或者这一轮压根没开始画流程
            if (!state.flowInProgress(session)) {
                session.resetMessages(messages);
                return build(null, state, text, session, null);
            }

            // 画流程途中模型停下来讲话了，补一句让它继续（老行为）
            // 设备动作是另一套口径：不能拿"按第 7 步 simulate_workflow"去催它。
            boolean deviceTurn = state.looksLikeDeviceCommand();
            String nudge = deviceTurn ? AgentNudge.buildDevice()
                    : AgentNudge.build(state.deviceCatalogFetched, state.workflowCatalogFetched,
                    state.lastValidateClean, state.lastSimulateWalkable, text, concepts);
            messages.add(LlmMessage.user(nudge));
            state.log(round, "nudge", deviceTurn ? "要求改用设备工具" : "解答并继续", nudge, null);
            replaceWith(messages, compactHistory(messages));
        }

        session.resetMessages(messages);
        PendingAction pending = session.pending();
        if (pending != null) {
            // 轮次用完但卡片已经生成，照样把它交出去
            return build(null, state, null, session, pending);
        }
        state.log(maxRounds(), "error", "未完成", "已达到最大轮次仍未完成", null);
        throw new AgentGenerateException("这次没处理完：已经问了 " + maxRounds() + " 轮还没轮到结果，换个说法再试一次",
                state.snapshot());
    }

    private int maxRounds() {
        return Math.max(1, properties.getMaxRounds());
    }

    // ------------------------------------------------------------------ 工具调度

    private JsonNode dispatch(LlmToolCall call, OperatorContext context, RunState state, int round) {
        String name = call == null ? null : call.name();
        ChatTool tool = tools.get(name);
        if (tool == null) {
            state.trace.add("拒绝未知工具 " + name);
            state.log(round, "gate", "拒绝未知工具", name, null);
            return error("未注册的工具: " + name);
        }
        JsonNode arguments = parseArguments(call.argumentsJson());

        if (SaveDraftTool.NAME.equals(name)) {
            String blocked = state.saveBlockReason();
            if (blocked != null) {
                // 模型已经在试着存草稿，说明这条路是"画流程"，后面该催它补齐前置步骤
                state.flowIntentSeen = true;
                state.trace.add("拒绝 save_draft：" + blocked);
                state.log(round, "gate", "拒绝 save_draft", blocked, null);
                return error(blocked);
            }
        }

        // 模型把"设备动作"错当成"画流程"时，这里直接打回，让它重选。
        // 提示词里已经写明，这层是兜底——模型判断失误时不能让用户拿到一张莫名其妙的新草稿。
        if (SaveDraftTool.NAME.equals(name) && state.looksLikeDeviceCommand()) {
            String message = "这句话是在操作设备，不是在设计流程。不要调用 save_draft。"
                    + "请改用 prepare_device_action（单台设备一个动作）或 prepare_task（按流程建任务）。";
            state.trace.add("gate:save_draft -> device");
            state.log(round, "gate", "拒绝 save_draft", "识别为设备操作，要求改用设备工具", null);
            return error(message);
        }

        // 流程草稿同样要过确认：save_draft 不再直接落库，改成生成一张待确认卡片，
        // 用户点「确认保存草稿」之后才真正写库（见 OperatorController#confirm）。
        if (SaveDraftTool.NAME.equals(name)) {
            JsonNode carded = prepareFlowDraft(arguments, context, state, round);
            if (carded != null) return carded;
        }

        // 设备动作有没有"动过手"要按调用算，不能按结果算：工具抛异常也是动过手了，
        // 再催它一遍只会让它在同一句话上反复调用。
        if (PrepareDeviceActionTool.NAME.equals(name)) state.deviceActionAttempted = true;

        try {
            JsonNode result = tool.execute(arguments, context);
            state.trace.add(name);
            state.mark(tool, result);
            return result;
        } catch (RuntimeException exception) {
            state.trace.add(name + " 失败");
            String message = exception.getMessage() == null ? name + " 执行失败" : exception.getMessage();
            state.log(round, "tool_error", name, message, null);
            return error(message);
        }
    }

    /**
     * 把 {@code save_draft} 转成一张待确认卡片，不落库。
     *
     * <p>返回 {@code null} 表示参数里连 document 都解析不出来，交回原路径让工具自己抛标准错误，
     * 模型才知道该补哪个字段——在这里造一个它看不懂的错误只会让它反复重试。
     */
    private JsonNode prepareFlowDraft(JsonNode arguments, OperatorContext context, RunState state, int round) {
        if (context == null || context.session() == null) return null;
        WorkflowModelDocument document;
        try {
            document = AgentDocuments.requireDocument(arguments);
        } catch (RuntimeException parseFailure) {
            return null;
        }

        // 新操作直接覆盖旧的待确认动作（和设备/建任务同一个策略），旧 token 随之作废
        if (context.session().pending() != null) context.session().clearPending();

        String flowName = document.flowModelName() == null ? "" : document.flowModelName().trim();
        ConfirmCard card = flowDraftCard(document, flowName);
        int nodeCount = card.parameters().size();
        context.session().setPending(new PendingAction.FlowDraft(
                UUID.randomUUID().toString(), java.time.Instant.now(), document, flowName, nodeCount, card));

        state.savedResult = null;
        state.flowDraftPrepared = true;
        state.flowDraftName = flowName;
        state.flowDraftNodes = nodeCount;
        state.trace.add(SaveDraftTool.NAME);
        state.log(round, "gate", "生成确认卡片",
                "save_draft 已转为待确认，未写库（" + nodeCount + " 个节点）", null);

        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("status", "AWAITING_CONFIRMATION");
        result.put("cardGenerated", true);
        result.put("flowModelName", flowName);
        result.put("nodeCount", nodeCount);
        result.put("message", "确认卡片已生成，用户点「保存草稿」之后才会落库。"
                + "现在用 2～4 句把流程讲清楚，然后停止：不要说已保存，不要再调用任何工具。");
        return result;
    }

    /** 草稿卡片：节点清单直接铺开，用户扫一眼就能判断这玩意儿是不是他要的。 */
    private ConfirmCard flowDraftCard(WorkflowModelDocument document, String flowName) {
        List<ConfirmCard.Row> rows = new ArrayList<>();
        if (!flowName.isBlank()) rows.add(ConfirmCard.Row.of("流程名", flowName));

        JsonNode nodes = document.getNodes();
        List<ConfirmCard.Row> steps = new ArrayList<>();
        if (nodes != null && nodes.isArray()) {
            int index = 1;
            for (JsonNode node : nodes) {
                String name = node.path("name").asText("");
                steps.add(ConfirmCard.Row.of(index + " " + (name.isBlank() ? "未命名节点" : name), describeNode(node)));
                index++;
            }
        }
        rows.add(ConfirmCard.Row.of("节点", "共 " + steps.size() + " 个"));

        String description = document.descriptionText();
        String summary = description == null ? "" : description.trim();
        if (summary.length() > 160) summary = summary.substring(0, 160) + "…";
        if (summary.isBlank()) {
            summary = "确认后才会保存为草稿，保存后可以在设计器里继续修改。";
        } else {
            summary = summary + "\n确认后才会保存为草稿，保存后可以在设计器里继续修改。";
        }

        String title = flowName.isBlank() ? "流程草稿" : "流程草稿 · " + flowName;
        return new ConfirmCard(ConfirmCard.KIND_FLOW_DRAFT, title, summary, List.copyOf(rows),
                List.copyOf(steps), List.of(), List.of(), List.of(), true, false);
    }

    /** 一个节点压成一行：类型 · 能力 · 关键参数。 */
    private String describeNode(JsonNode node) {
        String type = node.path("nodeType").asText("");
        String functionType = node.path("functionType").asText("");
        String capabilityKey = node.path("capability").path("capabilityName").asText("");
        List<String> parts = new ArrayList<>();
        if ("SUBFLOW_NODE".equals(type)) {
            parts.add("子流程");
        } else if ("DEV_NODE".equals(type)) {
            parts.add("设备动作");
        } else if (!functionType.isBlank()) {
            parts.add(functionType);
        }
        if (!capabilityKey.isBlank()) parts.addAll(describeCapability(node, capabilityKey));
        return parts.isEmpty() ? "—" : String.join(" · ", parts);
    }

    /**
     * 把 {@code capability_1} / {@code parameter_1} 翻成人话。
     *
     * <p>草稿里存的是契约标识，直接铺到卡片上用户看不懂。查得到设备模型就用显示名；
     * 查不到（模型被删、或者老构造器没注入目录）就原样退回标识，至少不丢信息。
     */
    private List<String> describeCapability(JsonNode node, String capabilityKey) {
        CapabilityBinder.CapabilityView view = findCapability(node.path("deviceModelId").asLong(0), capabilityKey);
        String label = view == null || view.displayName() == null || view.displayName().isBlank()
                ? capabilityKey : view.displayName();
        List<String> parts = new ArrayList<>();
        parts.add(label);

        JsonNode values = node.path("capability").path("capabilityParameters");
        if (values != null && values.isObject()) {
            List<String> pairs = new ArrayList<>();
            values.fields().forEachRemaining(entry -> pairs.add(
                    parameterLabel(view, entry.getKey()) + "=" + briefValue(entry.getValue())
                            + parameterUnit(view, entry.getKey())));
            if (!pairs.isEmpty()) parts.add(String.join("、", pairs));
        }
        return parts;
    }

    private CapabilityBinder.CapabilityView findCapability(long modelId, String capabilityKey) {
        if (deviceCatalog == null || modelId <= 0) return null;
        DeviceModels model;
        try {
            model = deviceCatalog.modelOrNull(modelId);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (model == null) return null;
        for (CapabilityBinder.CapabilityView view : CapabilityBinder.capabilities(model)) {
            if (capabilityKey.equals(view.capabilityName())) return view;
        }
        return null;
    }

    /** parameter_1 → "目标温度1"。查不到就原样返回标识。 */
    private static String parameterLabel(CapabilityBinder.CapabilityView view, String parameterName) {
        if (view == null) return parameterName;
        for (CapabilityBinder.ParameterSpec spec : view.parameters()) {
            if (!parameterName.equals(spec.name())) continue;
            return spec.displayName() == null || spec.displayName().isBlank()
                    ? parameterName : spec.displayName();
        }
        return parameterName;
    }

    /** 参数单位，查不到返回空串。 */
    private static String parameterUnit(CapabilityBinder.CapabilityView view, String parameterName) {
        if (view == null) return "";
        for (CapabilityBinder.ParameterSpec spec : view.parameters()) {
            if (!parameterName.equals(spec.name())) continue;
            return spec.unit() == null ? "" : spec.unit().trim();
        }
        return "";
    }

    private static String briefValue(JsonNode value) {
        if (value == null || value.isNull()) return "—";
        String text = value.isValueNode() ? value.asText() : value.toString();
        return text.length() > 40 ? text.substring(0, 40) + "…" : text;
    }

    private List<LlmToolSpec> toolSpecs() {        List<LlmToolSpec> specs = new ArrayList<>();
        for (ChatTool tool : tools.values()) {
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

    private static String safeId(LlmToolCall call) {
        String id = call == null ? null : call.id();
        return id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
    }

    private static String describeResult(JsonNode result) {
        if (result == null) return "无返回";
        if (result.path("error").isTextual()) return result.path("error").asText();
        if (result.path("blocking").asBoolean(false)) return "有 blocking issue";
        String status = result.path("status").asText("");
        if (!status.isBlank()) return status;
        return "成功";
    }

    // ------------------------------------------------------------------ 收尾与打包

    private WorkflowGenerateResponse build(JsonNode saved, RunState state, String summary,
                                           OperatorSession session, PendingAction pending) {
        PendingAction action = pending == null ? session.pending() : pending;
        ConfirmCard card = action == null ? null : action.card();
        String token = action == null ? null : action.token();
        long ttl = action == null ? 0 : PENDING_TTL_SECONDS;

        if (saved == null) {
            String fallback = card == null ? ""
                    : ConfirmCard.KIND_FLOW_DRAFT.equals(card.kind())
                    ? "流程草稿已整理好，确认后才会保存。"
                    : "已准备好，请确认后再执行。";
            return new WorkflowGenerateResponse(null, null, null, null, List.of(), false, false, null,
                    List.copyOf(state.trace), List.copyOf(state.logs),
                    summary == null || summary.isBlank() ? fallback : summary,
                    session.id(), card, token, ttl);
        }

        WorkflowModelDocument definition = null;
        if (saved.path("definition").isObject()) {
            try {
                definition = JsonNodeSupport.MAPPER.treeToValue(saved.get("definition"), WorkflowModelDocument.class);
            } catch (Exception ignored) {
                definition = null;
            }
        }
        if (definition == null && (saved.path("flowModelId").isNumber() || saved.path("flowModelName").isTextual())) {
            definition = WorkflowModelDocuments.of(saved.path("flowModelName").asText(""));
            if (saved.path("flowModelId").isNumber()) definition.flowModelId(saved.path("flowModelId").asLong());
            if (saved.path("description").isTextual()) definition.descriptionText(saved.path("description").asText());
        }
        List<WorkflowIssue> issues = new ArrayList<>();
        if (saved.path("issues").isArray()) {
            for (JsonNode item : saved.get("issues")) {
                try {
                    issues.add(JsonNodeSupport.MAPPER.treeToValue(item, WorkflowIssue.class));
                } catch (Exception ignored) {
                    // 单条 issue 解析失败不影响整体
                }
            }
        }
        Long flowModelId = flowModelIdOf(saved);
        if (flowModelId == null && definition != null) flowModelId = definition.flowModelId();
        return new WorkflowGenerateResponse(
                definition,
                saved.path("version").isNumber() ? saved.path("version").asInt() : null,
                saved.path("status").asText(null),
                saved.path("predecessorId").isNumber() ? saved.path("predecessorId").asLong() : null,
                List.copyOf(issues),
                saved.path("executable").asBoolean(false),
                saved.path("published").asBoolean(false),
                flowModelId,
                List.copyOf(state.trace),
                List.copyOf(state.logs),
                summary,
                session.id(), card, token, ttl);
    }

    private String writeFinalAnswer(RunState state, List<LlmMessage> messages) {
        return writeFinalAnswer(state, messages, false);
    }

    /**
     * 让模型把结果讲成人话。
     *
     * @param pendingConfirm true 表示草稿只是备好了、还没落库，措辞不能说成"已保存"
     */
    private String writeFinalAnswer(RunState state, List<LlmMessage> messages, boolean pendingConfirm) {
        String fallback = pendingConfirm ? fallbackDraftSummary(state) : fallbackSummary(state.savedResult);
        String instruction = pendingConfirm
                ? "草稿已经整理好，但还没有保存——用户在卡片上点「保存草稿」之后才会落库。"
                + "请用中文对用户做 2～4 句说明：流程名称、包含哪些步骤（如散热、温度判断、分段加热）。"
                + "不要说「已保存」「已创建」「草稿编号」，也不要催用户确认。"
                + "不要输出节点、接口、JSON 或完整工作流定义。不要调用工具。"
                : "草稿已保存。请用中文对用户做 2～5 句说明：流程名称、对照需求做了哪些步骤（如散热、温度判断、分段加热），以及草稿编号。"
                + "不要输出节点、接口、JSON 或完整工作流定义。不要调用工具。";
        messages.add(LlmMessage.user(instruction));
        try {
            state.log(0, "llm_call", "请求整理回答", properties.getModel(), null);
            LlmCompletion completion = llmClient.complete(messages, List.of());
            state.log(0, "llm_reply", "模型返回文本", summarizeCompletion(completion),
                    completionPayload(completion));
            String text = completion.content() == null ? "" : completion.content().trim();
            if (text.isEmpty() || completion.hasToolCalls()) text = fallback;
            state.log(0, "answer", "最终回答", text, null);
            return text;
        } catch (RuntimeException exception) {
            state.log(0, "answer", "最终回答", fallback, null);
            return fallback;
        }
    }

    private static String fallbackDraftSummary(RunState state) {
        StringBuilder text = new StringBuilder("已按你的描述整理出流程草稿");
        if (state.flowDraftName != null && !state.flowDraftName.isBlank()) {
            text.append("「").append(state.flowDraftName).append("」");
        }
        if (state.flowDraftNodes > 0) text.append("，共 ").append(state.flowDraftNodes).append(" 个节点");
        text.append("。确认后才会保存，保存后可以在设计器里继续修改。");
        return text.toString();
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

    private static Long flowModelIdOf(JsonNode result) {
        if (result != null && result.path("flowModelId").isNumber()) return result.path("flowModelId").asLong();
        return null;
    }

    // ------------------------------------------------------------------ 历史压缩

    private static void replaceWith(List<LlmMessage> messages, List<LlmMessage> compacted) {
        messages.clear();
        messages.addAll(compacted);
    }

    /**
     * 丢旧轮次、保留最近一次目录与校验结果。
     *
     * <p>合并成会话式之后，开头只有一条 system（老版本是 system+user 两条），
     * 所以切片从下标 1 开始。裁的时候必须整轮裁，把 assistant(tool_calls) 和
     * 对应的 tool 结果一起裁掉，否则下一次请求会因为 tool_call_id 找不到调用而报错。
     */
    static List<LlmMessage> compactHistory(List<LlmMessage> messages) {
        if (messages == null || messages.size() <= 1) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        List<Turn> turns = turnsOf(messages.subList(1, messages.size()));
        int lastDeviceCatalog = lastIndexWith(turns, ListDeviceCatalogTool.NAME);
        int lastWorkflowCatalog = lastIndexWith(turns, ListWorkflowCatalogTool.NAME);
        int lastValidate = lastIndexWith(turns, ValidateWorkflowTool.NAME);
        int lastSimulate = lastIndexWith(turns, SimulateWorkflowTool.NAME);
        int lastSave = lastIndexWith(turns, SaveDraftTool.NAME);
        List<LlmMessage> kept = new ArrayList<>();
        kept.add(messages.get(0));
        // 用户原话里带着任务名、设备名、流程名这类关键信息，只存在于纯文本轮次里，
        // 按工具白名单裁的话会全丢，下一轮模型就只能自己编。保留最近 3 条用户输入。
        List<Integer> userTurns = new ArrayList<>();
        for (int index = 0; index < turns.size(); index++) {
            Turn turn = turns.get(index);
            if (!turn.nudge && turn.hasUser()) userTurns.add(index);
        }
        java.util.Set<Integer> keepUserTurns = new java.util.HashSet<>(
                userTurns.subList(Math.max(0, userTurns.size() - 3), userTurns.size()));
        for (int index = 0; index < turns.size(); index++) {
            Turn turn = turns.get(index);
            boolean keep = keepUserTurns.contains(index)
                    || turn.hasTool(GetDeviceModelTool.NAME)
                    || (turn.hasTool(ListDeviceCatalogTool.NAME) && index == lastDeviceCatalog)
                    || (turn.hasTool(ListWorkflowCatalogTool.NAME) && index == lastWorkflowCatalog)
                    || (turn.hasTool(ValidateWorkflowTool.NAME) && index == lastValidate)
                    || (turn.hasTool(SimulateWorkflowTool.NAME) && index == lastSimulate)
                    || (turn.hasTool(SaveDraftTool.NAME) && index == lastSave)
                    || turn.hasUnknownTools()
                    || (turn.nudge && index == turns.size() - 1)
                    // 设备侧工具的结果都很小，留着不影响上下文，而且模型常要看上一步结论
                    || turn.isDeviceTurn();
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

        /** 这一轮里有没有用户本人说的话（不含系统补的 nudge）。 */
        boolean hasUser() {
            for (LlmMessage message : messages) {
                if (message != null && "user".equals(message.role())) return true;
            }
            return false;
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

        boolean isDeviceTurn() {
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

    // ------------------------------------------------------------------ 运行态

    /**
     * 单次 chat 的运行态。
     *
     * <p>它会被写进会话消息，因此下一轮可以从中恢复"已经查过目录、已经校验过"这类事实，
     * 不然用户分两轮说话时，保存草稿的门禁会莫名其妙把模型挡下来。
     */
    private static final class RunState {
        boolean deviceCatalogFetched;
        boolean workflowCatalogFetched;
        Boolean lastValidateClean;
        Boolean lastSimulateWalkable;
        boolean deviceToolUsed;
        /**
         * 本轮已经动过 prepare_device_action（成不成功都算）。
         *
         * <p>用来区分"模型还没动手"和"模型已经试过、正在解释结果"：前者要催，
         * 后者再催就变成拿同一句话逼它反复调用，轮次会被耗光。
         */
        boolean deviceActionAttempted;
        /** 这一轮已经被判定为"在画流程"（用户说了流程，或模型已经开始动 flow 工具）。 */
        boolean flowIntentSeen;
        /**
         * 建任务 / 启动任务正在等用户回话（缺设备绑定、缺任务名、状态不允许）。
         *
         * <p>这种"反问"是正常收尾，不能催它继续——否则模型会拿着"不要向人提问"的提示
         * 一路查设备、反复 prepare，把轮次耗光。
         */
        boolean taskAwaitingUser;
        JsonNode savedResult;
        /**
         * 本轮已经有一份流程草稿被转成了确认卡片（还没落库）。
         *
         * <p>和 {@code savedResult} 是互斥的两条收尾路径：老路径是"落库即收尾"，
         * 现在流程草稿不落库了，改由这个标记把控制权交回给用户。
         */
        boolean flowDraftPrepared;
        String flowDraftName;
        int flowDraftNodes;
        final List<String> trace = new ArrayList<>();
        final List<AgentInteractionLog> logs = new ArrayList<>();
        final Consumer<AgentInteractionLog> logListener;

        RunState(Consumer<AgentInteractionLog> logListener) {
            this.logListener = logListener == null ? log -> {} : logListener;
        }

        void restore(List<LlmMessage> history) {
            if (history == null) return;
            for (LlmMessage message : history) {
                if (message.toolCalls() == null) continue;
                for (LlmToolCall call : message.toolCalls()) {
                    if (call == null || call.name() == null) continue;
                    switch (call.name()) {
                        case ListDeviceCatalogTool.NAME -> deviceCatalogFetched = true;
                        case ListWorkflowCatalogTool.NAME -> workflowCatalogFetched = true;
                        case ValidateWorkflowTool.NAME -> {
                            if (lastValidateClean == null) lastValidateClean = Boolean.TRUE;
                        }
                        default -> deviceToolUsed = true;
                    }
                }
            }
        }

        void mark(ChatTool tool, JsonNode result) {
            String name = tool.name();
            if (ListDeviceCatalogTool.NAME.equals(name) && !result.has("error")) deviceCatalogFetched = true;
            if (ListWorkflowCatalogTool.NAME.equals(name) && !result.has("error")) workflowCatalogFetched = true;
            if (ValidateWorkflowTool.NAME.equals(name)) recordValidate(result);
            if (SimulateWorkflowTool.NAME.equals(name)) recordSimulate(result);
            if (SaveDraftTool.NAME.equals(name) && !result.path("error").isTextual()) savedResult = result;
            if (PrepareTaskTool.NAME.equals(name) || PrepareTaskStartTool.NAME.equals(name)) {
                String status = result.path("status").asText("");
                taskAwaitingUser = "NEED_CLARIFICATION".equals(status)
                        || "BLOCKED".equals(status)
                        || "AWAITING_CONFIRMATION".equals(status);
            }
            if (tool.group() == ChatTool.Group.DEVICE) deviceToolUsed = true;
        }

        /** 流程画到一半了没：只有这种情况才需要催模型继续。 */
        boolean flowInProgress(OperatorSession session) {
            if (savedResult != null) return false;
            // 草稿已经转成确认卡片，该等用户点按钮了，不是"画到一半"
            if (flowDraftPrepared) return false;
            PendingAction current = session == null ? null : session.pending();
            if (current != null) {
                boolean deviceIntent = looksLikeDeviceCommand();
                boolean cardIsDevice = current instanceof PendingAction.Device;
                // 卡片和这句话是同一件事 → 等用户点按钮即可；
                // 不是同一件事（比如上一轮遗留的草稿卡，用户已经改口说设备动作）→ 不能让旧卡片挡住
                if (cardIsDevice == deviceIntent) return false;
            }
            // 建任务/启动任务在等用户答话，这时候催它"继续画"只会让它原地打转
            if (taskAwaitingUser) return false;
            // 用户这句话是单台设备动作：历史里的"查过目录/校验过"不算数，
            // 否则会拿流程的催促去催设备动作，模型一路 simulate/save_draft 打转。
            if (looksLikeDeviceCommand()) return !deviceActionAttempted;
            return flowIntentSeen || workflowToolTouched();
        }

        private boolean workflowToolTouched() {
            return deviceCatalogFetched || workflowCatalogFetched || lastValidateClean != null
                    || lastSimulateWalkable != null;
        }

        /** 用户这句话是不是在说"设计一个流程"，而不是"动某台设备"或"建/启动任务"。 */
        boolean looksLikeFlowRequest() {
            String text = currentUserText;
            if (text == null || text.isBlank()) return false;
            // "用流程99建个任务""启动流程100的任务"——流程只是查询条件，
            // 真正要做的是建/启动任务。这类必须排除，否则会被当成画流程一路催下去。
            if (mentionsTaskIntent() && !mentionsDraftIntent()) return false;
            for (String word : FLOW_WORDS) {
                if (text.contains(word)) return true;
            }
            return false;
        }

        private boolean mentionsTaskIntent() {
            for (String word : TASK_WORDS) {
                if (currentUserText.contains(word)) return true;
            }
            return false;
        }

        private boolean mentionsDraftIntent() {
            for (String word : DRAFT_WORDS) {
                if (currentUserText.contains(word)) return true;
            }
            return false;
        }

        /**
         * 用户这句话是不是"让某台设备做某个动作"。
         *
         * <p>只在模型已经决定调用 {@code save_draft} 时用来兜底，判错也只是让它重选一次，
         * 不会影响正常流程。故意用宽判：命中设备类词 + 命中动作词才算。
         * 一旦这句话明确在说流程（"设计一个反应釜加热流程"），就不算设备指令。
         */
        boolean looksLikeDeviceCommand() {
            if (looksLikeFlowRequest()) return false;
            String text = currentUserText;
            if (text == null || text.isBlank()) return false;
            boolean hasDeviceWord = false;
            for (String word : DEVICE_WORDS) {
                if (text.contains(word)) {
                    hasDeviceWord = true;
                    break;
                }
            }
            if (!hasDeviceWord) return false;
            for (String word : ACTION_WORDS) {
                if (text.contains(word)) return true;
            }
            return false;
        }

        String currentUserText;

        private static final List<String> FLOW_WORDS = List.of(
                "流程", "步骤", "编排", "设计", "生成", "画一", "画个", "方案", "工序", "子流程",
                "实验计划", "自动化流程", "工作流");

        /** 说明这句话落在"建任务 / 启动任务"上（流程只是它引用的对象）。 */
        private static final List<String> TASK_WORDS = List.of(
                "建任务", "建个任务", "新建任务", "创建任务", "起个任务", "开个任务",
                "启动任务", "启动这个任务", "任务启动", "跑起来", "开始执行", "执行任务");

        /** 说明这句话确实在要一份流程草稿。和 TASK_WORDS 同时出现时以草稿为准。 */
        private static final List<String> DRAFT_WORDS = List.of(
                "设计", "画", "编排", "生成流程", "改流程", "草稿", "工作流模型");

        private static final List<String> DEVICE_WORDS = List.of(
                "反应釜", "热阱", "保温箱", "色谱仪", "流量计", "气密", "连接器", "电磁阀", "阀门",
                "搅拌", "泵", "加热器", "天平", "机械臂", "位移台");
        private static final List<String> ACTION_WORDS = List.of(
                "加热", "升温", "降温", "冷却", "加压", "泄压", "抽真空", "搅拌", "停止", "关闭",
                "打开", "设成", "设置到", "调到", "开到", "转到", "移动到", "启动", "开始");

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

        void log(int round, String kind, String title, String detail, String payload) {
            AgentInteractionLog entry = new AgentInteractionLog(round, kind, title, detail, clip(payload));
            logs.add(entry);
            try {
                logListener.accept(entry);
            } catch (RuntimeException ignored) {
                // 流式推送失败不能中断生成
            }
        }

        private static String clip(String payload) {
            if (payload == null || payload.isBlank()) return null;
            if (payload.length() <= LOG_PAYLOAD_LIMIT) return payload;
            return payload.substring(0, LOG_PAYLOAD_LIMIT) + "\n…(已截断，共 " + payload.length() + " 字符)";
        }
    }

    // ------------------------------------------------------------------ JSON 工具

    private static ObjectNode jsonObject() {
        return JsonNodeSupport.objectNode();
    }

    private JsonNode error(String message) {
        return JsonNodeSupport.objectNode().put("error", message);
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
}
