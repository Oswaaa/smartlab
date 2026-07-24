package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class WorkflowRuntimeServiceTest {
    @Test
    void rejectsNodeStatusTransitionThatIsNotDeclaredByLifecycle() {
        TaskStepMapper steps = mock(TaskStepMapper.class);
        FlowNodeMapper nodes = mock(FlowNodeMapper.class);
        WorkflowRuntimeService runtime = runtime(steps, nodes);
        TaskStep step = step("RUNNING");
        when(steps.selectById(11L)).thenReturn(step);
        when(nodes.selectById(7L)).thenReturn(node(lifecycle("PENDING", "RUNNING", "PENDING", "RUNNING")));

        assertThrows(IllegalStateException.class, () -> runtime.completeStep(step, JsonNodeSupport.objectNode()));
        verify(steps, never()).updateById(step);
    }

    @Test
    void terminatesDeviceStepThroughDeclaredRunningAndTerminatingTransitions() {
        TaskStepMapper steps = mock(TaskStepMapper.class);
        FlowNodeMapper nodes = mock(FlowNodeMapper.class);
        WorkflowRuntimeService runtime = runtime(steps, nodes);
        TaskStep step = step("RUNNING");
        when(steps.selectById(11L)).thenReturn(step);
        when(nodes.selectById(7L)).thenReturn(node(lifecycle("RUNNING", "TERMINATING", "TERMINATING", "TERMINATED")));

        assertDoesNotThrow(() -> runtime.beginTerminationStep(step));
        assertDoesNotThrow(() -> runtime.terminateStep(step, JsonNodeSupport.objectNode()));
        verify(steps, times(2)).updateById(step);
    }

    private WorkflowRuntimeService runtime(TaskStepMapper steps, FlowNodeMapper nodes) {
        return new WorkflowRuntimeService(mock(TaskMapper.class), steps, nodes,
                mock(ExecutionLogService.class), mock(ApplicationEventPublisher.class));
    }

    private TaskStep step(String status) {
        TaskStep step = new TaskStep();
        step.setId(11L);
        step.setTaskId(5L);
        step.setFlowNodeId(7L);
        step.setNodeIdRef(1L);
        step.setNodeStatus(status);
        return step;
    }

    private FlowNode node(ObjectNode lifecycle) {
        FlowNode node = new FlowNode();
        node.setId(7L);
        node.setFlowModelId(3L);
        node.setNodeIdRef(1L);
        node.setLifecycle(lifecycle);
        return node;
    }

    private ObjectNode lifecycle(String firstFrom, String firstTo, String secondFrom, String secondTo) {
        ObjectNode lifecycle = JsonNodeSupport.objectNode();
        lifecycle.put("initialStateName", "PENDING");
        lifecycle.putArray("states").add("PENDING").add("RUNNING").add("TERMINATING").add("TERMINATED").add("SUCCEEDED");
        var transitions = lifecycle.putArray("transitions");
        transitions.addObject().put("fromStateName", firstFrom).put("toStateName", firstTo);
        transitions.addObject().put("fromStateName", secondFrom).put("toStateName", secondTo);
        return lifecycle;
    }
}
