package com.smartlab.engine.workflow.action;

import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.TaskExecutionKind;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
public class WaitWorkflowActionExecutor implements WorkflowActionExecutor {
    private final WorkflowTaskResourceService resourceService;
    private final DeviceInstancesMapper deviceInstancesMapper;

    public WaitWorkflowActionExecutor() {
        this(null, null);
    }

    @Autowired
    public WaitWorkflowActionExecutor(WorkflowTaskResourceService resourceService,
                                       DeviceInstancesMapper deviceInstancesMapper) {
        this.resourceService = resourceService;
        this.deviceInstancesMapper = deviceInstancesMapper;
    }

    public String actionName() { return "WAIT"; }

    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        long durationMs = action.payload().path("durationMs").asLong(-1);
        if (durationMs < 0) throw new IllegalArgumentException("WAIT.durationMs 必须是非负整数");
        if (context.step().getStartTime() == null) throw new IllegalStateException("WAIT 执行前步骤必须已开始");
        if (shouldExpireImmediately(context)) {
            return WorkflowActionResult.continueExecution();
        }
        Instant deadline = context.step().getStartTime().toInstant().plusMillis(durationMs);
        return context.now().isBefore(deadline)
                ? WorkflowActionResult.suspendUntil(deadline)
                : WorkflowActionResult.continueExecution();
    }

    private boolean shouldExpireImmediately(WorkflowActionContext context) {
        if (resourceService == null || deviceInstancesMapper == null || context.task() == null) {
            return false;
        }
        if (!TaskExecutionKind.isSimulation(context.task())) {
            return false;
        }
        Set<Long> bound;
        try {
            bound = resourceService.boundDeviceInstanceIds(context.task().getResourceMap());
        } catch (RuntimeException ignored) {
            return false;
        }
        if (bound.isEmpty()) {
            return true;
        }
        for (Long instanceId : bound) {
            DeviceInstances instance = deviceInstancesMapper.selectById(instanceId);
            if (instance == null || !DeviceInstanceKind.isTemporary(instance)) {
                return false;
            }
        }
        return true;
    }
}
