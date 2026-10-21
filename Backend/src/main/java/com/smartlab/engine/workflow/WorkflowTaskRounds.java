package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个任务在进程内的调度内存。节点定义和已处理完的步骤留在这里；
 * 之后每一轮只向数据库要还要推进的步骤。
 */
final class WorkflowTaskRounds {
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "FAILED", "TERMINATED");
    private final ConcurrentHashMap<Long, TaskRound> rounds = new ConcurrentHashMap<>();

    List<TaskStep> stepsFor(Task task, WorkflowRuntimeService runtime, WorkflowService workflows,
            FlowNodeService nodes) {
        if (task == null || task.getId() == null) {
            List<TaskStep> loaded = runtime.steps(task == null ? null : task.getId());
            return loaded == null ? List.of() : new ArrayList<>(loaded);
        }
        TaskRound round = rounds.computeIfAbsent(task.getId(), ignored -> new TaskRound());
        if (!round.installed) {
            List<TaskStep> loaded = runtime.steps(task.getId());
            if (loaded == null) loaded = List.of();
            round.install(task, loaded, workflows, nodes);
            return new ArrayList<>(loaded);
        }
        List<TaskStep> fresh = runtime.stepsForRound(task.getId(), round.unobservedIds());
        if (fresh == null) fresh = List.of();
        round.ensureNodes(fresh, nodes);
        return round.compose(fresh);
    }

    Map<Long, FlowNode> nodes(Long taskId) {
        TaskRound round = taskId == null ? null : rounds.get(taskId);
        return round == null ? Map.of() : round.nodesById;
    }

    void capture(Long taskId, List<TaskStep> steps) {
        TaskRound round = taskId == null ? null : rounds.get(taskId);
        if (round != null) round.capture(steps);
    }

    void ensureModel(Long taskId, Long modelId, WorkflowService workflows) {
        if (taskId == null) return;
        rounds.computeIfAbsent(taskId, ignored -> new TaskRound()).ensureModel(modelId, workflows);
    }

    FlowNode findNode(Long taskId, Long modelId, long nodeIdRef) {
        TaskRound round = taskId == null ? null : rounds.get(taskId);
        return round == null ? null : round.find(modelId, nodeIdRef);
    }

    void rememberNode(Long taskId, FlowNode node) {
        if (taskId == null) return;
        rounds.computeIfAbsent(taskId, ignored -> new TaskRound()).remember(node);
    }

    void evict(Long taskId) {
        if (taskId != null) rounds.remove(taskId);
    }

    private static boolean observed(TaskStep step) {
        JsonNode variableSpace = step.getVariableSpace();
        return variableSpace != null
                && variableSpace.path("_triggerStates")
                .path(WorkflowTriggerState.TERMINAL_OBSERVED_KEY).asBoolean(false);
    }

    private static final class TaskRound {
        private boolean installed;
        private final Map<Long, FlowNode> nodesById = new LinkedHashMap<>();
        private final Map<Long, TaskStep> settled = new LinkedHashMap<>();
        private final Set<Long> unobserved = new LinkedHashSet<>();
        private final Set<Long> loadedModels = new LinkedHashSet<>();

        private void install(Task task, List<TaskStep> loaded, WorkflowService workflows, FlowNodeService nodes) {
            ensureModel(task.getFlowModelId(), workflows);
            ensureNodes(loaded, nodes);
            installed = true;
        }

        private Set<Long> unobservedIds() {
            return Set.copyOf(unobserved);
        }

        private void ensureModel(Long modelId, WorkflowService workflows) {
            if (modelId == null || loadedModels.contains(modelId) || workflows == null) return;
            loadedModels.add(modelId);
            List<FlowNode> loaded = workflows.nodes(modelId);
            if (loaded == null) return;
            for (FlowNode node : loaded) remember(node);
        }

        private void ensureNodes(List<TaskStep> steps, FlowNodeService service) {
            if (steps == null || service == null) return;
            List<Long> missing = new ArrayList<>();
            for (TaskStep step : steps) {
                Long id = step == null ? null : step.getFlowNodeId();
                if (id != null && !nodesById.containsKey(id) && !missing.contains(id)) missing.add(id);
            }
            if (missing.isEmpty()) return;
            List<FlowNode> loaded = service.listByIds(missing);
            if (loaded == null) return;
            for (FlowNode node : loaded) remember(node);
        }

        private void remember(FlowNode node) {
            if (node != null && node.getId() != null) nodesById.put(node.getId(), node);
        }

        private FlowNode find(Long modelId, long nodeIdRef) {
            for (FlowNode node : nodesById.values()) {
                if (node.getNodeIdRef() != null && node.getNodeIdRef() == nodeIdRef
                        && java.util.Objects.equals(modelId, node.getFlowModelId())) {
                    return node;
                }
            }
            return null;
        }

        private List<TaskStep> compose(List<TaskStep> fresh) {
            Set<Long> returned = new LinkedHashSet<>();
            for (TaskStep step : fresh) {
                if (step != null && step.getId() != null) returned.add(step.getId());
            }
            unobserved.removeIf(id -> !returned.contains(id));
            Map<Long, TaskStep> view = new LinkedHashMap<>();
            for (TaskStep step : settled.values()) {
                if (step.getId() != null) view.put(step.getId(), step);
            }
            for (TaskStep step : fresh) {
                if (step != null && step.getId() != null) view.put(step.getId(), step);
            }
            return new ArrayList<>(view.values());
        }

        private void capture(List<TaskStep> steps) {
            if (steps == null) return;
            for (TaskStep step : steps) {
                if (step == null || step.getId() == null) continue;
                boolean terminal = step.getNodeStatus() != null && TERMINAL.contains(step.getNodeStatus());
                if (terminal && observed(step)) {
                    unobserved.remove(step.getId());
                    settled.put(step.getId(), step);
                } else if (terminal) {
                    settled.remove(step.getId());
                    unobserved.add(step.getId());
                } else {
                    settled.remove(step.getId());
                    unobserved.remove(step.getId());
                }
            }
        }
    }
}
