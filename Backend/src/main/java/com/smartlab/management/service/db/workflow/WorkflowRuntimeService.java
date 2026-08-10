package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.event.TaskLifecycleObservationEvent;
import com.smartlab.global.event.WorkflowNodeObservationEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
public class WorkflowRuntimeService {
    private static final Set<String> ACTIVE_NODE_STATES = Set.of("PENDING", "RUNNING", "TERMINATING");
    private static final Set<String> TERMINAL_NODE_STATES = Set.of("SUCCEEDED", "FAILED", "TERMINATED");

    private final TaskMapper taskMapper;
    private final TaskStepMapper stepMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final ExecutionLogService logService;
    private final ApplicationEventPublisher eventPublisher;

    public WorkflowRuntimeService(TaskMapper taskMapper, TaskStepMapper stepMapper, FlowNodeMapper flowNodeMapper,
                                  ExecutionLogService logService, ApplicationEventPublisher eventPublisher) {
        this.taskMapper = taskMapper;
        this.stepMapper = stepMapper;
        this.flowNodeMapper = flowNodeMapper;
        this.logService = logService;
        this.eventPublisher = eventPublisher;
    }

    public List<Task> runningTasks() {
        return taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getTaskStatus, "RUNNING")
                .orderByAsc(Task::getId));
    }

    public Task task(Long id) {
        return taskMapper.selectById(id);
    }

    public TaskStep step(Long id) {
        return stepMapper.selectById(id);
    }

    public List<TaskStep> steps(Long taskId) {
        return stepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                .eq(TaskStep::getTaskId, taskId).orderByAsc(TaskStep::getId));
    }

    public List<TaskStep> activeSteps(Long taskId) {
        return stepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                .eq(TaskStep::getTaskId, taskId).in(TaskStep::getNodeStatus, ACTIVE_NODE_STATES)
                .orderByAsc(TaskStep::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskStep createStep(Task task, FlowNode node, Long parentStepId, int depth, JsonNode input) {
        var query = Wrappers.<TaskStep>lambdaQuery()
                .eq(TaskStep::getTaskId, task.getId())
                .eq(TaskStep::getFlowNodeId, node.getId())
                .eq(TaskStep::getStepDepth, depth);
        if (parentStepId == null) query.isNull(TaskStep::getParentStepId);
        else query.eq(TaskStep::getParentStepId, parentStepId);
        TaskStep existing = stepMapper.selectOne(query);
        if (existing != null) return existing;
        TaskStep step = new TaskStep();
        step.setTaskId(task.getId());
        step.setFlowNodeId(node.getId());
        step.setNodeIdRef(node.getNodeIdRef());
        step.setParentStepId(parentStepId);
        step.setStepDepth(depth);
        step.setNodeStatus("PENDING");
        step.setInterfaceInSnapshot(input == null ? JsonNodeSupport.objectNode() : input);
        step.setVariableSpace(task.getTaskVariables() == null ? JsonNodeSupport.objectNode() : task.getTaskVariables().deepCopy());
        stepMapper.insert(step);
        logService.append("TASK", task.getId(), step.getId(), null, "INFO", "节点已触发: " + node.getNodeIdRef());
        publishNode(step);
        return step;
    }

    @Transactional(rollbackFor = Exception.class)
    public void startStep(Task task, TaskStep step) {
        if (!"PENDING".equals(step.getNodeStatus())) return;
        requireLifecycleTransition(step, "RUNNING");
        step.setNodeStatus("RUNNING");
        step.setStartTime(OffsetDateTime.now());
        stepMapper.updateById(step);
        task.setCurrentFlowNodeId(step.getFlowNodeId());
        task.setCurrentNodeIdRef(rootNodeIdRef(step));
        taskMapper.updateById(task);
        logService.append("TASK", task.getId(), step.getId(), null, "INFO", "节点开始执行: " + step.getNodeIdRef());
        publishNode(step);
    }

    @Transactional(rollbackFor = Exception.class)
    public void transitionNodeLifecycle(Task task, TaskStep step, FlowNode node, String targetState) {
        if (task == null || step == null || node == null || targetState == null || targetState.isBlank()) {
            throw new IllegalArgumentException("节点生命周期更新上下文不完整");
        }
        TaskStep persisted = stepMapper.selectById(step.getId());
        TaskStep target = persisted == null ? step : persisted;
        if (!node.getId().equals(target.getFlowNodeId())) {
            throw new IllegalArgumentException("任务步骤与生命周期节点不匹配");
        }
        if (targetState.equals(target.getNodeStatus())) return;
        requireLifecycleTransition(target, node, targetState);
        OffsetDateTime now = OffsetDateTime.now();
        target.setNodeStatus(targetState);
        if ("RUNNING".equals(targetState) && target.getStartTime() == null) target.setStartTime(now);
        if (TERMINAL_NODE_STATES.contains(targetState)) {
            target.setEndTime(now);
            if (target.getStartTime() != null) {
                target.setDurationMs(now.toInstant().toEpochMilli()
                        - target.getStartTime().toInstant().toEpochMilli());
            }
        }
        stepMapper.updateById(target);
        if (target != step) copyLifecycleState(target, step);
        if ("RUNNING".equals(targetState)) {
            task.setCurrentFlowNodeId(target.getFlowNodeId());
            task.setCurrentNodeIdRef(rootNodeIdRef(target));
            taskMapper.updateById(task);
        }
        logService.append("TASK", task.getId(), target.getId(), null, "INFO",
                "节点生命周期更新: " + target.getNodeStatus());
        publishNode(target);
    }

    public void updateInputSnapshot(TaskStep step, JsonNode snapshot) {
        step.setInterfaceInSnapshot(snapshot);
        stepMapper.updateById(step);
    }

    @Transactional(rollbackFor = Exception.class)
    public void mergeVariableSpace(TaskStep step, JsonNode values) {
        if (values == null || !values.isObject() || values.isEmpty()) return;
        TaskStep current = stepMapper.selectById(step.getId());
        if (current == null) throw new IllegalArgumentException("任务步骤不存在: " + step.getId());
        ObjectNode merged = current.getVariableSpace() != null && current.getVariableSpace().isObject()
                ? (ObjectNode) current.getVariableSpace().deepCopy() : JsonNodeSupport.objectNode();
        deepMerge(merged, values);
        current.setVariableSpace(merged);
        step.setVariableSpace(merged.deepCopy());
        stepMapper.updateById(current);
        publishNode(current);
    }

    public void appendStepLog(Task task, TaskStep step, String level, String message) {
        if (task == null || step == null) throw new IllegalArgumentException("任务步骤日志上下文不完整");
        logService.append("TASK", task.getId(), step.getId(), null, level, message);
    }

    @Transactional(rollbackFor = Exception.class)
    public void beginTerminationStep(TaskStep step) {
        TaskStep current = stepMapper.selectById(step.getId());
        TaskStep target = current == null ? step : current;
        if ("TERMINATING".equals(target.getNodeStatus()) || TERMINAL_NODE_STATES.contains(target.getNodeStatus())) return;
        requireLifecycleTransition(target, "TERMINATING");
        target.setNodeStatus("TERMINATING");
        stepMapper.updateById(target);
        if (target != step) step.setNodeStatus(target.getNodeStatus());
        publishNode(target);
    }
    @Transactional(rollbackFor = Exception.class)
    public void completeStep(TaskStep step, JsonNode output) {
        if (finishStep(step, "SUCCEEDED", output)) {
            logService.append("TASK", step.getTaskId(), step.getId(), null, "INFO", "节点执行完成: " + step.getNodeIdRef());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void failStep(TaskStep step, String reason) {
        if (finishStep(step, "FAILED", JsonNodeSupport.objectNode().put("reason", reason))) {
            logService.append("TASK", step.getTaskId(), step.getId(), null, "ERROR", "节点执行失败: " + step.getNodeIdRef() + ", " + reason);
            failTask(task(step.getTaskId()), reason);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void terminateStep(TaskStep step, JsonNode output) {
        if (finishStep(step, "TERMINATED", output)) {
            logService.append("TASK", step.getTaskId(), step.getId(), null, "WARN", "节点终止完成: " + step.getNodeIdRef());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Task task) {
        if (!"RUNNING".equals(task.getTaskStatus())) return;
        task.setTaskStatus("SUCCEEDED");
        task.setEndTime(OffsetDateTime.now());
        task.setCurrentFlowNodeId(null);
        task.setCurrentNodeIdRef(null);
        taskMapper.updateById(task);
        logService.append("TASK", task.getId(), null, null, "INFO", "任务执行成功");
        publishTask(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failTask(Task task, String reason) {
        if (task == null || !("RUNNING".equals(task.getTaskStatus())
                || "PAUSED".equals(task.getTaskStatus()) || "TERMINATING".equals(task.getTaskStatus()))) return;
        task.setTaskStatus("FAILED");
        task.setEndTime(OffsetDateTime.now());
        task.setCurrentFlowNodeId(null);
        task.setCurrentNodeIdRef(null);
        taskMapper.updateById(task);
        logService.append("TASK", task.getId(), null, null, "ERROR", "任务执行失败: " + reason);
        publishTask(task);
    }

    public TaskStep findRunningDeviceStepByMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) return null;
        return stepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                        .in(TaskStep::getNodeStatus, "RUNNING", "TERMINATING"))
                .stream().filter(step -> messageId.equals(
                        step.getInterfaceInSnapshot() == null ? "" : step.getInterfaceInSnapshot().path("messageId").asText("")))
                .findFirst().orElse(null);
    }

    public List<TaskStep> findRunningDeviceStepsByInstanceId(Long deviceInstanceId) {
        if (deviceInstanceId == null || deviceInstanceId <= 0) return List.of();
        return runningDeviceSteps().stream().filter(step -> step.getInterfaceInSnapshot() != null
                && step.getInterfaceInSnapshot().path("deviceInstanceId").canConvertToLong()
                && deviceInstanceId.equals(step.getInterfaceInSnapshot().path("deviceInstanceId").asLong()))
                .toList();
    }

    private List<TaskStep> runningDeviceSteps() {
        return stepMapper.selectList(Wrappers.<TaskStep>lambdaQuery().eq(TaskStep::getNodeStatus, "RUNNING"));
    }

    private void deepMerge(ObjectNode target, JsonNode values) {
        values.fields().forEachRemaining(entry -> {
            JsonNode existing = target.get(entry.getKey());
            if (existing != null && existing.isObject() && entry.getValue().isObject()) {
                deepMerge((ObjectNode) existing, entry.getValue());
            } else {
                target.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
    }

    private Long rootNodeIdRef(TaskStep step) {
        TaskStep current = step;
        while (current.getParentStepId() != null) {
            TaskStep parent = stepMapper.selectById(current.getParentStepId());
            if (parent == null) throw new IllegalStateException("子流程父步骤不存在: " + current.getParentStepId());
            current = parent;
        }
        return current.getNodeIdRef();
    }

    private boolean finishStep(TaskStep step, String status, JsonNode output) {
        TaskStep persisted = stepMapper.selectById(step.getId());
        TaskStep target = persisted == null ? step : persisted;
        if (TERMINAL_NODE_STATES.contains(target.getNodeStatus())) return false;
        requireLifecycleTransition(target, status);
        target.setNodeStatus(status);
        target.setEndTime(OffsetDateTime.now());
        target.setInterfaceOutSnapshot(output == null ? JsonNodeSupport.objectNode() : output);
        if (target.getStartTime() != null) {
            target.setDurationMs(target.getEndTime().toInstant().toEpochMilli() - target.getStartTime().toInstant().toEpochMilli());
        }
        stepMapper.updateById(target);
        if (target != step) copyTerminalState(target, step);
        publishNode(target);
        return true;
    }

    private void requireLifecycleTransition(TaskStep step, String toState) {
        FlowNode node = flowNodeMapper.selectById(step.getFlowNodeId());
        if (node == null) throw new IllegalStateException("任务步骤引用的FLOW_NODE不存在: " + step.getFlowNodeId());
        requireLifecycleTransition(step, node, toState);
    }

    private void requireLifecycleTransition(TaskStep step, FlowNode node, String toState) {
        JsonNode lifecycle = node.getLifecycle();
        if (lifecycle == null || !lifecycle.isObject() || lifecycle.isEmpty()) return;
        String fromState = step.getNodeStatus();
        boolean sourceDeclared = false;
        for (JsonNode state : lifecycle.path("states")) if (fromState.equals(state.asText())) sourceDeclared = true;
        if (!sourceDeclared) throw new IllegalStateException("节点生命周期未声明当前状态: " + fromState);
        for (JsonNode transition : lifecycle.path("transitions")) {
            if (fromState.equals(transition.path("fromStateName").asText())
                    && toState.equals(transition.path("toStateName").asText())) return;
        }
        throw new IllegalStateException("节点生命周期不允许状态转移: " + fromState + "→" + toState);
    }
    private void copyLifecycleState(TaskStep source, TaskStep target) {
        target.setNodeStatus(source.getNodeStatus());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setDurationMs(source.getDurationMs());
    }
    private void copyTerminalState(TaskStep source, TaskStep target) {
        target.setNodeStatus(source.getNodeStatus());
        target.setEndTime(source.getEndTime());
        target.setDurationMs(source.getDurationMs());
        target.setInterfaceOutSnapshot(source.getInterfaceOutSnapshot());
    }

    private void publishNode(TaskStep step) {
        FlowNode node = flowNodeMapper.selectById(step.getFlowNodeId());
        if (node == null) return;
        eventPublisher.publishEvent(new WorkflowNodeObservationEvent(step.getTaskId(), node.getFlowModelId(),
                step.getNodeIdRef(), step.getId(), step.getNodeStatus(),
                step.getVariableSpace() == null ? JsonNodeSupport.objectNode() : step.getVariableSpace().deepCopy(), Instant.now()));
    }

    private void publishTask(Task task) {
        eventPublisher.publishEvent(new TaskLifecycleObservationEvent(task.getId(), task.getTaskStatus(), Instant.now()));
    }
}
