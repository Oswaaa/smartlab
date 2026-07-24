package com.smartlab.engine.workflow;

import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.TaskService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTaskControlService {
    private final TaskService taskService;
    private final WorkflowRuntimeService runtimeService;
    private final FlowNodeService flowNodeService;
    private final WorkflowExecutionOperations executionOperations;

    public WorkflowTaskControlService(TaskService taskService, WorkflowRuntimeService runtimeService,
                                      FlowNodeService flowNodeService, WorkflowExecutionOperations executionOperations) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.flowNodeService = flowNodeService;
        this.executionOperations = executionOperations;
    }

    public Task start(Long taskId) {
        return taskService.start(taskId);
    }

    public Task pause(Long taskId) {
        return taskService.pause(taskId);
    }

    public Task resume(Long taskId) {
        return taskService.resume(taskId);
    }

    public Task terminate(Long taskId) {
        Task task = taskService.requestTermination(taskId);
        for (TaskStep step : taskService.snapshots(taskId)) {
            if (!("PENDING".equals(step.getNodeStatus()) || "RUNNING".equals(step.getNodeStatus()))) continue;
            Long deviceInstanceId = deviceInstanceId(step);
            if (deviceInstanceId == null) {
                runtimeService.terminateStep(step, null);
                continue;
            }
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (node == null || !"DEV_NODE".equals(node.getNodeType())) {
                runtimeService.failTask(task, "终止中的任务步骤设备上下文与工作流节点不一致");
                throw new IllegalStateException("终止中的任务步骤设备上下文与工作流节点不一致");
            }
            String messageId = step.getInterfaceInSnapshot().path("messageId").asText("");
            if (messageId.isBlank()) {
                runtimeService.failTask(task, "设备能力节点缺少messageId，拒绝发送无法关联的终止命令");
                throw new IllegalStateException("设备能力节点缺少messageId，拒绝发送无法关联的终止命令");
            }
            try {
                runtimeService.beginTerminationStep(step);
                executionOperations.dispatchDeviceAbort(task, step, node, deviceInstanceId, messageId);
            } catch (RuntimeException error) {
                runtimeService.failStep(step, "任务终止命令下发失败: " + error.getMessage());
                throw new IllegalStateException("任务终止命令下发失败，任务已标记为FAILED: " + error.getMessage(), error);
            }
        }
        return taskService.completeTerminationIfSettled(task.getId());
    }

    private Long deviceInstanceId(TaskStep step) {
        if (step.getInterfaceInSnapshot() == null) return null;
        long value = step.getInterfaceInSnapshot().path("deviceInstanceId").asLong(0);
        return value > 0 ? value : null;
    }
}
