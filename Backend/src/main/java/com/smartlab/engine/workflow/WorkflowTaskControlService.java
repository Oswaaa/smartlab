package com.smartlab.engine.workflow;

import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Application boundary for commands that affect both task state and running device state machines. */
@Service
public class WorkflowTaskControlService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowTaskControlService.class);

    private final TaskService taskService;
    private final StateMachineEngine stateMachineEngine;

    public WorkflowTaskControlService(TaskService taskService, StateMachineEngine stateMachineEngine) {
        this.taskService = taskService;
        this.stateMachineEngine = stateMachineEngine;
    }

    public Task start(Long taskId) {
        return taskService.start(taskId);
    }

    public Task abort(Long taskId) {
        for (TaskStep step : taskService.snapshots(taskId)) {
            if (!"RUNNING".equals(step.getNodeStatus()) || step.getInterfaceInSnapshot() == null) continue;
            Long deviceInstanceId = positiveLong(step.getInterfaceInSnapshot().path("deviceInstanceId").asLong());
            if (deviceInstanceId == null) continue;
            Map<String, Object> context = new HashMap<>();
            context.put("taskId", taskId);
            context.put("taskStepId", step.getId());
            context.put("messageId", step.getInterfaceInSnapshot().path("messageId").asText(""));
            try {
                stateMachineEngine.dispatchSignalByType(deviceInstanceId, "WORKFLOW", "WF_EXECUTE_ABORT", context);
            } catch (RuntimeException e) {
                log.warn("任务 {} 终止时取消设备步骤 {} 失败，继续终止任务", taskId, step.getId(), e);
            }
        }
        return taskService.abort(taskId);
    }

    private Long positiveLong(long value) {
        return value > 0 ? value : null;
    }
}
