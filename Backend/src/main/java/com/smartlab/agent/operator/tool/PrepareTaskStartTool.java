package com.smartlab.agent.operator.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.operator.ActionGuard;
import com.smartlab.agent.operator.ConfirmCard;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorJson;
import com.smartlab.agent.operator.OperatorSessionStore;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.agent.operator.PendingAction;
import com.smartlab.agent.operator.TaskPreparationService;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskExecutionKind;
import com.smartlab.management.service.db.workflow.TaskService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 准备"启动一个已存在的任务"。只准备，不启动。
 *
 * <p>和 {@link PrepareTaskTool} 的区别：那个负责建任务（落库，状态 PENDING），
 * 这个负责把 PENDING 的任务真正跑起来。两步都必须经过确认卡片。
 *
 * <p>不会自己猜是哪条任务：名字撞了、编号不存在，都把候选摊出来让用户挑。
 */
@Component
public class PrepareTaskStartTool implements OperatorTool {

    public static final String NAME = "prepare_task_start";

    private static final JsonNode SCHEMA = OperatorJson.schema("""
            {"type":"object","properties":{
              "task":{"type":"string","description":"要启动的任务：任务名或任务 id"},
              "executionKind":{"type":"string","enum":["PRODUCTION","SIMULATION"],"description":"执行方式。用户没说就不要填，默认沿用任务自身的执行方式"},
              "reason":{"type":"string","description":"一句话说明为什么要启动，会显示在确认页"}
            },"required":["task"]}
            """);

    private static final int CANDIDATE_LIMIT = 10;

    private final TaskService taskService;
    private final TaskPreparationService preparation;
    private final OperatorSessionStore store;

    public PrepareTaskStartTool(TaskService taskService, TaskPreparationService preparation,
                                OperatorSessionStore store) {
        this.taskService = taskService;
        this.preparation = preparation;
        this.store = store;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "准备启动一个已经创建好的任务（按任务名或任务 id 找到它、复检状态），然后生成确认卡片。"
                + "用户点确认之后才会真正启动。只启动已有任务，不创建新任务。";
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

        String query = OperatorJson.text(arguments, "task");
        if (query == null || query.isBlank()) {
            return clarification("要启动哪个任务？告诉我任务名或编号", List.of());
        }

        List<Task> all = allTasks();
        Task task = pickOne(query.trim(), all);
        if (task == null) {
            return clarification("没找到叫「" + query.trim() + "」的任务，最近的任务在下面，选一个",
                    recentCandidates(all));
        }

        String status = task.getTaskStatus() == null ? "" : task.getTaskStatus().trim().toUpperCase();
        String flowName = flowNameOf(task);

        if (!"PENDING".equals(status)) {
            ConfirmCard card = blockedCard(task, flowName, status);
            ObjectNode result = OperatorJson.obj();
            result.put("status", "BLOCKED");
            result.put("card", OperatorJson.node(card.toMap()));
            result.put("instruction", "把 blocking 的原因告诉用户。只有待开始（PENDING）的任务能启动，"
                    + "已经在跑的需要先暂停或终止，已经结束的要重新建任务");
            return result;
        }

        String requestedKind = OperatorJson.text(arguments, "executionKind");
        String executionKind;
        try {
            executionKind = TaskExecutionKind.normalize(
                    requestedKind == null || requestedKind.isBlank() ? task.getExecutionKind() : requestedKind);
        } catch (IllegalArgumentException error) {
            return OperatorJson.error("BAD_EXECUTION_KIND", "执行方式只能是正式执行或仿真执行");
        }

        List<ConfirmCard.Row> rows = new ArrayList<>();
        rows.add(ConfirmCard.Row.of("任务", task.getTaskName()));
        rows.add(ConfirmCard.Row.of("任务 id", String.valueOf(task.getId())));
        rows.add(ConfirmCard.Row.of("所属流程", flowName));
        rows.add(ConfirmCard.Row.of("当前状态", statusLabel(status)));
        rows.add(ConfirmCard.Row.of("启动后状态", "运行中（RUNNING）"));
        rows.add(ConfirmCard.Row.of("执行方式", TaskExecutionKind.SIMULATION.equals(executionKind)
                ? "仿真执行（不动真机）" : "正式执行（会驱动真实设备）"));
        String reason = OperatorJson.text(arguments, "reason");
        if (reason != null && !reason.isBlank()) {
            rows.add(ConfirmCard.Row.of("原因", reason));
        }

        List<ConfirmCard.Issue> warnings = new ArrayList<>();
        if (TaskExecutionKind.SIMULATION.equals(executionKind)) {
            warnings.add(new ConfirmCard.Issue(ActionGuard.WARNING,
                    "这次是仿真启动，只占用虚拟租约，不会真的驱动设备"));
        } else {
            warnings.add(new ConfirmCard.Issue(ActionGuard.WARNING,
                    "启动后流程会按节点顺序驱动设备，中途要停请及时终止任务"));
        }

        ConfirmCard card = new ConfirmCard(
                ConfirmCard.KIND_START_TASK,
                "确认启动任务",
                "即将启动任务「" + task.getTaskName() + "」（id=" + task.getId() + "）",
                List.copyOf(rows), List.of(), List.of(),
                List.copyOf(warnings), List.of(), true, true);

        PendingAction.TaskStart action = new PendingAction.TaskStart(
                UUID.randomUUID().toString(),
                Instant.now(),
                task.getId(),
                task.getTaskName(),
                task.getFlowModelId() == null ? 0L : task.getFlowModelId(),
                flowName,
                status,
                executionKind,
                card
        );
        context.session().setPending(action);

        ObjectNode result = OperatorJson.obj();
        result.put("status", "AWAITING_CONFIRMATION");
        result.put("token", action.token());
        result.put("card", OperatorJson.node(card.toMap()));
        result.put("instruction",
                "任务还没有启动。用一到两句话说明要启动哪个任务、执行方式是什么，然后等用户点确认。"
                        + "不要复述卡片里的每一行。");
        return result;
    }

    // ------------------------------------------------------------------ 查任务

    private List<Task> allTasks() {
        try {
            var page = taskService.page(1, 500, null, null, null);
            List<Task> records = page == null ? null : page.getRecords();
            return records == null ? List.of() : records;
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    /**
     * 从任务列表里挑出用户说的那一个。
     *
     * <p>优先级：任务 id 精确 → 任务名精确（唯一才算）→ 任务名包含（唯一才算）。
     * 任何一步出现多条就返回 null，交给调用方把候选列出来，绝不自己挑。
     */
    private Task pickOne(String query, List<Task> tasks) {
        if (tasks.isEmpty()) return null;

        Long asId = asLong(query);
        if (asId != null) {
            for (Task task : tasks) {
                if (Objects.equals(task.getId(), asId)) return task;
            }
        }

        List<Task> exact = tasks.stream()
                .filter(task -> task.getTaskName() != null && task.getTaskName().trim().equalsIgnoreCase(query))
                .toList();
        if (exact.size() == 1) return exact.get(0);

        List<Task> fuzzy = tasks.stream()
                .filter(task -> task.getTaskName() != null && task.getTaskName().contains(query))
                .toList();
        if (fuzzy.size() == 1) return fuzzy.get(0);
        return null;
    }

    private List<Task> recentCandidates(List<Task> tasks) {
        return tasks.stream().limit(CANDIDATE_LIMIT).collect(Collectors.toList());
    }

    private JsonNode clarification(String question, List<Task> candidates) {
        ObjectNode result = OperatorJson.obj();
        result.put("status", "NEED_CLARIFICATION");
        result.put("question", question);
        ArrayNode array = result.putArray("tasks");
        for (Task task : candidates) {
            ObjectNode item = array.addObject();
            item.put("id", task.getId());
            item.put("name", task.getTaskName());
            item.put("status", task.getTaskStatus());
            item.put("flow", flowNameOf(task));
        }
        if (!candidates.isEmpty()) {
            result.put("instruction", "把候选任务列给用户选，不要自己挑一个启动。没有待开始的任务就不能启动");
        } else {
            result.put("instruction", "如实告诉用户没有找到任务，可以先用 prepare_task 建一个");
        }
        return result;
    }

    private ConfirmCard blockedCard(Task task, String flowName, String status) {
        List<ConfirmCard.Row> rows = new ArrayList<>();
        rows.add(ConfirmCard.Row.of("任务", task.getTaskName()));
        rows.add(ConfirmCard.Row.of("任务 id", String.valueOf(task.getId())));
        rows.add(ConfirmCard.Row.of("所属流程", flowName));
        rows.add(ConfirmCard.Row.of("当前状态", statusLabel(status)));
        List<ConfirmCard.Issue> blocking = List.of(new ConfirmCard.Issue(ActionGuard.BLOCKING,
                "当前状态是「" + statusLabel(status) + "」，只有待开始的任务能启动"));
        return new ConfirmCard(ConfirmCard.KIND_START_TASK, "无法启动这个任务",
                "任务「" + task.getTaskName() + "」现在不能启动", List.copyOf(rows),
                List.of(), List.of(), List.of(), blocking, false, false);
    }

    private String flowNameOf(Task task) {
        Long flowModelId = task.getFlowModelId();
        if (flowModelId == null) return "—";
        try {
            return preparation.flows().stream()
                    .filter(flow -> flow.getId() != null && flow.getId().equals(flowModelId))
                    .map(FlowModels::getFlowName)
                    .findFirst()
                    .orElse("id=" + flowModelId);
        } catch (RuntimeException error) {
            return "id=" + flowModelId;
        }
    }

    private static String statusLabel(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "PENDING" -> "待开始";
            case "RUNNING" -> "运行中";
            case "PAUSED" -> "已暂停";
            case "TERMINATING" -> "终止中";
            case "SUCCEEDED" -> "已完成";
            case "FAILED" -> "已失败";
            case "TERMINATED" -> "已终止";
            default -> status;
        };
    }

    private static Long asLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
