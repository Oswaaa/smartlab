package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** Internal persistence boundary used by the workflow engine. */
@Service
public class WorkflowRuntimeService {
    private final TaskMapper taskMapper;
    private final TaskStepMapper stepMapper;
    private final ExecutionLogService logService;

    public WorkflowRuntimeService(TaskMapper taskMapper, TaskStepMapper stepMapper, ExecutionLogService logService) {
        this.taskMapper = taskMapper;
        this.stepMapper = stepMapper;
        this.logService = logService;
    }

    public List<Task> runningTasks() {
        return taskMapper.selectList(Wrappers.<Task>lambdaQuery().eq(Task::getTaskStatus, "RUNNING"));
    }

    public Task task(Long id) { return taskMapper.selectById(id); }
    public TaskStep step(Long id) { return stepMapper.selectById(id); }

    public List<TaskStep> steps(Long taskId) {
        return stepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                .eq(TaskStep::getTaskId, taskId).orderByAsc(TaskStep::getId));
    }

    public List<TaskStep> activeSteps(Long taskId) {
        return stepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                .eq(TaskStep::getTaskId, taskId)
                .in(TaskStep::getNodeStatus, "PENDING", "RUNNING")
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
        logService.append("TASK", task.getId(), step.getId(), null, "INFO",
                "节点已触发: " + node.getNodeIdRef());
        return step;
    }

    @Transactional(rollbackFor = Exception.class)
    public void startStep(Task task, TaskStep step) {
        if (!"PENDING".equals(step.getNodeStatus())) return;
        step.setNodeStatus("RUNNING");
        step.setStartTime(OffsetDateTime.now());
        stepMapper.updateById(step);
        task.setCurrentFlowNodeId(step.getFlowNodeId());
        task.setCurrentNodeIdRef(rootNodeIdRef(step));
        taskMapper.updateById(task);
        logService.append("TASK", task.getId(), step.getId(), null, "INFO",
                "节点开始执行: " + step.getNodeIdRef());
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
        var merged = current.getVariableSpace() != null && current.getVariableSpace().isObject()
                ? (com.fasterxml.jackson.databind.node.ObjectNode) current.getVariableSpace().deepCopy()
                : JsonNodeSupport.objectNode();
        deepMerge(merged, values);
        current.setVariableSpace(merged);
        step.setVariableSpace(merged.deepCopy());
        stepMapper.updateById(current);
    }

    private void deepMerge(com.fasterxml.jackson.databind.node.ObjectNode target, JsonNode values) {
        values.fields().forEachRemaining(entry -> {
            JsonNode existing = target.get(entry.getKey());
            if (existing != null && existing.isObject() && entry.getValue().isObject()) {
                deepMerge((com.fasterxml.jackson.databind.node.ObjectNode) existing, entry.getValue());
            } else {
                target.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeStep(TaskStep step, JsonNode output) {
        if (finishStep(step, "COMPLETED", output)) {
            logService.append("TASK", step.getTaskId(), step.getId(), null, "INFO",
                    "节点执行完成: " + step.getNodeIdRef());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void failStep(TaskStep step, String reason) {
        if (finishStep(step, "FAILED", JsonNodeSupport.objectNode().put("reason", reason))) {
            logService.append("TASK", step.getTaskId(), step.getId(), null, "ERROR",
                    "节点执行失败: " + step.getNodeIdRef() + ", " + reason);
            failTask(task(step.getTaskId()), reason);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Task task) {
        if (!"RUNNING".equals(task.getTaskStatus())) return;
        task.setTaskStatus("COMPLETED");
        task.setEndTime(OffsetDateTime.now());
        task.setCurrentFlowNodeId(null);
        task.setCurrentNodeIdRef(null);
        taskMapper.updateById(task);
        logService.append("TASK", task.getId(), null, null, "INFO", "任务执行完成");
    }

    @Transactional(rollbackFor = Exception.class)
    public void failTask(Task task, String reason) {
        if (task == null || !"RUNNING".equals(task.getTaskStatus())) return;
        task.setTaskStatus("FAILED");
        task.setEndTime(OffsetDateTime.now());
        task.setCurrentFlowNodeId(null);
        task.setCurrentNodeIdRef(null);
        taskMapper.updateById(task);
        logService.append("TASK", task.getId(), null, null, "ERROR", "任务执行失败: " + reason);
    }

    public TaskStep findRunningDeviceStepByMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) return null;
        List<TaskStep> running = stepMapper.selectList(Wrappers.<TaskStep>lambdaQuery()
                .eq(TaskStep::getNodeStatus, "RUNNING"));
        return running.stream().filter(step -> messageId.equals(
                step.getInterfaceInSnapshot() == null ? "" : step.getInterfaceInSnapshot().path("messageId").asText("")))
                .findFirst().orElse(null);
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
        if ("COMPLETED".equals(target.getNodeStatus()) || "FAILED".equals(target.getNodeStatus())
                || "ABORTED".equals(target.getNodeStatus())) return false;
        target.setNodeStatus(status);
        target.setEndTime(OffsetDateTime.now());
        target.setInterfaceOutSnapshot(output == null ? JsonNodeSupport.objectNode() : output);
        if (target.getStartTime() != null)
            target.setDurationMs(target.getEndTime().toInstant().toEpochMilli()
                    - target.getStartTime().toInstant().toEpochMilli());
        stepMapper.updateById(target);
        if (target != step) copyTerminalState(target, step);
        return true;
    }

    private void copyTerminalState(TaskStep source, TaskStep target) {
        target.setNodeStatus(source.getNodeStatus());
        target.setEndTime(source.getEndTime());
        target.setDurationMs(source.getDurationMs());
        target.setInterfaceOutSnapshot(source.getInterfaceOutSnapshot());
    }
}
