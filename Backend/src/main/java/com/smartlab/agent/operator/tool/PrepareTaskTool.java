package com.smartlab.agent.operator.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.llm.LlmMessage;
import com.smartlab.agent.operator.ActionGuard;
import com.smartlab.agent.operator.ConfirmCard;
import com.smartlab.agent.operator.InstanceResolver;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorJson;
import com.smartlab.agent.operator.OperatorSessionStore;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.agent.operator.PendingAction;
import com.smartlab.agent.operator.TaskPreparationService;
import com.smartlab.management.dto.workflow.TaskCreateRequest;
import com.smartlab.management.dto.workflow.TaskPreflightResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.entity.workflow.FlowModels;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 准备"创建一个任务"。只创建，不启动。
 *
 * <p>信息不全时不会猜：缺哪个型号的设备就返回一组候选让人选，
 * 用户答一次，同一个型号下的所有节点一起绑上。
 */
@Component
public class PrepareTaskTool implements OperatorTool {

    public static final String NAME = "prepare_task";

    private static final JsonNode SCHEMA = OperatorJson.schema("""
            {"type":"object","properties":{
              "flow":{"type":"string","description":"流程名或流程 id"},
              "taskName":{"type":"string","description":"任务名称。用户在前面任何一轮说过名字，就必须原样沿用（补设备绑定的那一次也要带上）；只有用户从头到尾没提过名字，才可以自己起一个带时间的名字并说明"},
              "assignments":{"type":"object","description":"设备绑定，键可以是型号名或槽位号，值是设备名。例如 {\\"气密连接器\\":\\"气密连接器#1\\"}。用户没说就不要填，系统会返回需要询问的清单"},
              "reason":{"type":"string","description":"一句话说明建这个任务的目的，会显示在确认页"}
            },"required":["flow"]}
            """);

    private final TaskPreparationService preparation;
    private final OperatorSessionStore store;

    public PrepareTaskTool(TaskPreparationService preparation, OperatorSessionStore store) {
        this.preparation = preparation;
        this.store = store;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "准备创建一个任务：按流程名找到流程、绑定设备、跑完整预检，然后生成确认卡片。"
                + "用户点确认之后才会真正创建。只创建，不启动。";
    }

    @Override
    public JsonNode parameterSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode execute(JsonNode arguments, OperatorContext context) {
        // 新操作直接覆盖旧的待确认动作，旧 token 随之作废
        if (store.pendingAlive(context.session())) {
            context.session().clearPending();
        }

        String flowQuery = OperatorJson.text(arguments, "flow");
        TaskPreparationService.FlowMatch match = preparation.matchFlow(flowQuery);
        if (match.status() != InstanceResolver.Status.FOUND) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "NEED_CLARIFICATION");
            result.put("question", match.hint() == null ? "要用哪个流程？" : match.hint());
            ArrayNode candidates = result.putArray("flows");
            for (FlowModels flow : match.candidates()) {
                ObjectNode item = candidates.addObject();
                item.put("id", flow.getId());
                item.put("name", flow.getFlowName());
                item.put("version", flow.getVersion());
                item.put("status", flow.getStatus());
            }
            result.put("instruction", "把候选流程列给用户选，不要自己挑");
            return result;
        }

        FlowModels flow = match.resolved();
        if (!"ACTIVE".equalsIgnoreCase(TaskPreparationService.normalizeStatus(flow.getStatus()))) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "NEED_CLARIFICATION");
            result.put("question", "流程「" + flow.getFlowName() + "」当前状态是 "
                    + flow.getStatus() + "，不是已发布状态，确定要用它建任务吗？");
            result.put("flowId", flow.getId());
            return result;
        }

        String taskName = OperatorJson.text(arguments, "taskName");
        // 名字是用户给的，模型在多轮补绑定时容易丢掉它、自己另起一个，这里以用户原话为准
        String userNamedTask = taskNameFromHistory(context);
        if (userNamedTask != null) {
            taskName = userNamedTask;
        }
        if (taskName == null || taskName.isBlank()) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "NEED_CLARIFICATION");
            result.put("question", "任务叫什么名字？");
            result.put("instruction", "向用户询问任务名称");
            return result;
        }

        Map<String, String> assignments = new LinkedHashMap<>();
        JsonNode assignmentNode = arguments == null ? null : arguments.get("assignments");
        if (assignmentNode != null && assignmentNode.isObject()) {
            assignmentNode.fields().forEachRemaining(entry ->
                    assignments.put(entry.getKey(), entry.getValue().asText()));
        }

        TaskPreparationService.BindingPlan plan;
        try {
            plan = preparation.plan(flow.getId(), assignments);
        } catch (RuntimeException error) {
            return OperatorJson.error("FLOW_INVALID",
                    "读取流程设备要求失败: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }

        if (!plan.problems().isEmpty()) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "NEED_CLARIFICATION");
            result.put("question", "设备绑定有问题，需要用户确认");
            ArrayNode problems = result.putArray("problems");
            plan.problems().forEach(problems::add);
            result.put("instruction", "把这些问题原样告诉用户，不要自己猜一个绑定");
            return result;
        }

        if (!plan.questions().isEmpty()) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "NEED_CLARIFICATION");
            result.put("question", "有几类设备需要指定具体是哪一台");
            ArrayNode groups = result.putArray("groups");
            for (TaskPreparationService.ModelQuestion question : plan.questions()) {
                ObjectNode group = groups.addObject();
                group.put("model", question.modelName());
                group.put("nodeCount", question.slotIds().size());
                ArrayNode nodes = group.putArray("nodes");
                question.nodeNames().forEach(nodes::add);
                ArrayNode candidates = group.putArray("candidates");
                for (InstanceResolver.Candidate candidate : question.candidates()) {
                    ObjectNode item = candidates.addObject();
                    item.put("id", candidate.id());
                    item.put("name", candidate.instanceName());
                    item.put("point", candidate.devicePoint());
                }
            }
            result.put("instruction",
                    "把 groups 里每一类的候选列给用户，让他按型号指定。"
                            + "用户答完后重新调用本工具，assignments 的键用型号名，值用设备名。");
            return result;
        }

        TaskCreateRequest request = preparation.buildRequest(flow.getId(), taskName, plan.bindings(), context.userId());
        TaskPreflightResponse preflight;
        try {
            preflight = preparation.preflight(request);
        } catch (RuntimeException error) {
            return OperatorJson.error("PREFLIGHT_FAILED",
                    "预检失败: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }

        List<ConfirmCard.Issue> warnings = new ArrayList<>();
        List<ConfirmCard.Issue> blocking = new ArrayList<>();
        if (preflight != null && preflight.issues() != null) {
            for (WorkflowIssue issue : preflight.issues()) {
                if (issue.blocking()) {
                    blocking.add(new ConfirmCard.Issue(ActionGuard.BLOCKING, issue.message()));
                } else {
                    warnings.add(new ConfirmCard.Issue(ActionGuard.WARNING, issue.message()));
                }
            }
        }

        List<ConfirmCard.Row> rows = new ArrayList<>();
        rows.add(ConfirmCard.Row.of("流程", flow.getFlowName() + "（id=" + flow.getId() + "，版本 "
                + flow.getVersion() + "，" + flow.getStatus() + "）"));
        rows.add(ConfirmCard.Row.of("任务名", taskName));
        rows.add(ConfirmCard.Row.of("创建后状态", "待开始（不会自动启动）"));
        String reason = OperatorJson.text(arguments, "reason");
        if (reason != null && !reason.isBlank()) {
            rows.add(ConfirmCard.Row.of("原因", reason));
        }

        List<ConfirmCard.Row> bindingRows = new ArrayList<>();
        for (PendingAction.SlotBinding binding : plan.bindings()) {
            bindingRows.add(new ConfirmCard.Row(
                    binding.nodeName(),
                    binding.instanceName() + "（" + binding.modelName() + "）",
                    binding.source()));
        }

        ConfirmCard card = new ConfirmCard(
                ConfirmCard.KIND_CREATE_TASK,
                "确认创建任务",
                "即将创建任务「" + taskName + "」，绑定 " + plan.bindings().size() + " 个设备节点",
                List.copyOf(rows), List.of(), List.copyOf(bindingRows),
                List.copyOf(warnings), List.copyOf(blocking), blocking.isEmpty(), false);

        if (!card.ready()) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "BLOCKED");
            result.put("card", OperatorJson.node(card.toMap()));
            result.put("instruction", "把 blocking 的原因告诉用户。最常见的是设备离线，需要先让设备上线");
            return result;
        }

        PendingAction.Task action = new PendingAction.Task(
                UUID.randomUUID().toString(),
                Instant.now(),
                flow.getId(),
                flow.getFlowName(),
                taskName,
                plan.bindings(),
                card
        );
        context.session().setPending(action);

        ObjectNode result = OperatorJson.obj();
        result.put("status", "AWAITING_CONFIRMATION");
        result.put("token", action.token());
        result.put("card", OperatorJson.node(card.toMap()));
        result.put("instruction",
                "任务还没有创建。请把确认卡片里的流程、任务名、每个节点的设备绑定（标出哪些是自动推断的）"
                        + "原文告诉用户，然后等用户点确认。");
        return result;
    }

    /** 用户原话里明确给过的任务名，例如"任务名叫 test1030""任务名是 abc"。 */
    private static final Pattern TASK_NAME_IN_TEXT = Pattern.compile(
            "任务名(?:字)?(?:就叫|叫|是|为)?\\s*[:：]?\\s*([A-Za-z0-9_\\-\\u4e00-\\u9fa5]{1,40})");

    /**
     * 从会话历史里取用户说过的任务名。
     *
     * <p>多轮场景下模型经常在"补设备绑定"那一轮把名字丢掉、自己另起一个默认名。
     * 名字是用户给的，不该被模型改掉，所以这里兜一层：历史里有明确任务名就以用户为准。
     */
    private String taskNameFromHistory(OperatorContext context) {
        if (context == null || context.session() == null) return null;
        List<LlmMessage> messages = context.session().messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmMessage message = messages.get(i);
            if (message == null || !"user".equals(message.role())) continue;
            String content = message.content();
            if (content == null || content.isBlank()) continue;
            Matcher matcher = TASK_NAME_IN_TEXT.matcher(content);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                if (!name.isEmpty()) return name;
            }
        }
        return null;
    }
}
