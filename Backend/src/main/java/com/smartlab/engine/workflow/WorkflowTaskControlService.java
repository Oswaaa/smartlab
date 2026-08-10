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
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (node == null) {
                runtimeService.failTask(task, "终止中的任务步骤设备上下文与工作流节点不一致");
                throw new IllegalStateException("终止中的任务步骤设备上下文与工作流节点不一致");
            }
            if (!"DEV_NODE".equals(node.getNodeType())) {
                runtimeService.terminateStep(step, null);
                continue;
            }
            try {
                long deviceInstanceId = executionOperations.resolveDeviceInstance(task, step, node);
                String capabilityName = node.getCapability() == null
                        ? "" : node.getCapability().path("capabilityName").asText("");
                String messageId = executionOperations.ensureMessageId(
                        step, deviceInstanceId, capabilityName);
                runtimeService.beginTerminationStep(step);
                executionOperations.dispatchDeviceAbort(task, step, node, deviceInstanceId, messageId);
            } catch (RuntimeException error) {
                runtimeService.failStep(step, "任务终止命令下发失败: " + error.getMessage());
                throw new IllegalStateException("任务终止命令下发失败，任务已标记为FAILED: " + error.getMessage(), error);
            }
        }
        return taskService.completeTerminationIfSettled(task.getId());
    }
}
