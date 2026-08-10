package com.smartlab.engine.workflow;

import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.TaskService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowTaskControlServiceTest {

    @Test
    void terminatesDeviceStepWithoutReadingCorrelationMetadataFromInterfaceSnapshot() {
        TaskService tasks = mock(TaskService.class);
        WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
        FlowNodeService nodes = mock(FlowNodeService.class);
        WorkflowExecutionOperations operations = mock(WorkflowExecutionOperations.class);
        WorkflowTaskControlService service = new WorkflowTaskControlService(tasks, runtime, nodes, operations);
        Task task = new Task();
        task.setId(9L);
        TaskStep step = new TaskStep();
        step.setId(12L);
        step.setTaskId(9L);
        step.setFlowNodeId(7L);
        step.setNodeStatus("RUNNING");
        FlowNode node = new FlowNode();
        node.setId(7L);
        node.setNodeType("DEV_NODE");
        node.setCapability(com.smartlab.global.util.JsonNodeSupport.objectNode().put("capabilityName", "mix"));
        when(tasks.requestTermination(9L)).thenReturn(task);
        when(tasks.snapshots(9L)).thenReturn(List.of(step));
        when(tasks.completeTerminationIfSettled(9L)).thenReturn(task);
        when(nodes.getById(7L)).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(21L);
        when(operations.ensureMessageId(step, 21L, "mix")).thenReturn("message-1");

        service.terminate(9L);

        verify(runtime).beginTerminationStep(step);
        verify(operations).dispatchDeviceAbort(task, step, node, 21L, "message-1");
    }
}
