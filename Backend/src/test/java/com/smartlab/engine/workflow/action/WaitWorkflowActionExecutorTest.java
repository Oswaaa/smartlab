package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskExecutionKind;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaitWorkflowActionExecutorTest {

    @Test
    void workflowSimulationExpiresWaitImmediately() {
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceInstances temporary = new DeviceInstances();
        temporary.setId(9L);
        temporary.setInstanceKind(DeviceInstanceKind.TEMPORARY);
        when(resources.boundDeviceInstanceIds(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of(9L));
        when(instances.selectById(9L)).thenReturn(temporary);
        WaitWorkflowActionExecutor executor = new WaitWorkflowActionExecutor(resources, instances);

        WorkflowActionResult result = executor.execute(waitAction(5_000), context(TaskExecutionKind.SIMULATION, OffsetDateTime.now()));

        assertEquals(WorkflowActionStatus.CONTINUE, result.status());
        assertNull(result.resumeAt());
    }

    @Test
    void productionStillWaitsWallClock() {
        WaitWorkflowActionExecutor executor = new WaitWorkflowActionExecutor();
        OffsetDateTime started = OffsetDateTime.now().minusSeconds(1);
        WorkflowActionResult result = executor.execute(waitAction(5_000), context(TaskExecutionKind.PRODUCTION, started));
        assertEquals(WorkflowActionStatus.SUSPEND_UNTIL, result.status());
    }

    @Test
    void taskSimulationStillWaitsWallClock() {
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceInstances physical = new DeviceInstances();
        physical.setId(11L);
        physical.setInstanceKind(DeviceInstanceKind.PHYSICAL);
        when(resources.boundDeviceInstanceIds(org.mockito.ArgumentMatchers.any())).thenReturn(Set.of(11L));
        when(instances.selectById(11L)).thenReturn(physical);
        WaitWorkflowActionExecutor executor = new WaitWorkflowActionExecutor(resources, instances);

        WorkflowActionResult result = executor.execute(
                waitAction(5_000), context(TaskExecutionKind.SIMULATION, OffsetDateTime.now().minusSeconds(1)));

        assertEquals(WorkflowActionStatus.SUSPEND_UNTIL, result.status());
    }

    private WorkflowActionDefinition waitAction(long durationMs) {
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("durationMs", durationMs);
        return new WorkflowActionDefinition("WAIT", payload);
    }

    private WorkflowActionContext context(String executionKind, OffsetDateTime startTime) {
        Task task = new Task();
        task.setId(1L);
        task.setExecutionKind(executionKind);
        task.setResourceMap(JsonNodeSupport.objectNode().put("formatVersion", 1).set("deviceBindings", JsonNodeSupport.objectNode()));
        TaskStep step = new TaskStep();
        step.setStartTime(startTime);
        FlowNode node = new FlowNode();
        return new WorkflowActionContext(task, step, node, JsonNodeSupport.objectNode(), Instant.now(), null);
    }
}
