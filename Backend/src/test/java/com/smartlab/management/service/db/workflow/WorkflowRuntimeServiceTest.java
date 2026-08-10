package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowTriggerState;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class WorkflowRuntimeServiceTest {
    @Test
    void createStepInitializesCanonicalInputAndOutputInterfaceArrays() {
        TaskStepMapper steps = mock(TaskStepMapper.class);
        WorkflowRuntimeService runtime = runtime(steps, mock(FlowNodeMapper.class));
        Task task = new Task();
        task.setId(5L);
        task.setTaskVariables(JsonNodeSupport.objectNode());
        FlowNode node = node(lifecycle("PENDING", "RUNNING", "RUNNING", "SUCCEEDED"));
        node.setInterfaces(interfaces());
        when(steps.selectOne(any())).thenReturn(null);

        TaskStep created = runtime.createStep(task, node, null, 0, null);

        assertTrue(created.getInterfaceInSnapshot().isArray());
        assertTrue(created.getInterfaceOutSnapshot().isArray());
        assertEquals(2, created.getInterfaceInSnapshot().size());
        assertEquals("workflow-in", created.getInterfaceInSnapshot().get(0).path("interfaceName").asText());
        assertTrue(created.getInterfaceInSnapshot().get(0).path("signalName").isNull());
        assertEquals(1, created.getInterfaceOutSnapshot().size());
        assertEquals("workflow-out", created.getInterfaceOutSnapshot().get(0).path("interfaceName").asText());
        assertTrue(created.getInterfaceOutSnapshot().get(0).path("signalName").isNull());
    }

    @Test
    void pollsActiveAndUnobservedTerminalStepsOnly() {
        TaskStepMapper steps = mock(TaskStepMapper.class);
        WorkflowRuntimeService runtime = runtime(steps, mock(FlowNodeMapper.class));
        TaskStep pending = stepWithId(11L, "PENDING");
        TaskStep running = stepWithId(12L, "RUNNING");
        TaskStep terminating = stepWithId(13L, "TERMINATING");
        TaskStep succeeded = stepWithId(14L, "SUCCEEDED");
        TaskStep failed = stepWithId(15L, "FAILED");
        TaskStep terminated = stepWithId(16L, "TERMINATED");
        TaskStep observed = stepWithId(17L, "SUCCEEDED");
        observed.setVariableSpace(JsonNodeSupport.objectNode().set("_triggerStates",
                JsonNodeSupport.objectNode().put(WorkflowTriggerState.TERMINAL_OBSERVED_KEY, true)));
        when(steps.selectList(any())).thenReturn(List.of(
                pending, running, terminating, succeeded, failed, terminated, observed));

        List<TaskStep> pollable = runtime.pollableSteps(5L);

        assertEquals(List.of(pending, running, terminating, succeeded, failed, terminated), pollable);
    }

    @Test
    void explicitlyTransitionsPendingNodeToRunningThroughDeclaredLifecycleEdge() throws Exception {
        TaskStepMapper steps = mock(TaskStepMapper.class);
        FlowNodeMapper nodes = mock(FlowNodeMapper.class);
        WorkflowRuntimeService runtime = runtime(steps, nodes);
        TaskStep step = step("PENDING");
        FlowNode node = node(lifecycle("PENDING", "RUNNING", "RUNNING", "SUCCEEDED"));
        Task task = new Task();
        task.setId(5L);
        task.setTaskStatus("RUNNING");
        when(steps.selectById(11L)).thenReturn(step);

        runtime.getClass()
                .getMethod("transitionNodeLifecycle", Task.class, TaskStep.class, FlowNode.class, String.class)
                .invoke(runtime, task, step, node, "RUNNING");

        assertEquals("RUNNING", step.getNodeStatus());
        assertNotNull(step.getStartTime());
        verify(steps).updateById(step);
    }

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
        return stepWithId(11L, status);
    }

    private TaskStep stepWithId(Long id, String status) {
        TaskStep step = new TaskStep();
        step.setId(id);
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

    private ArrayNode interfaces() {
        ArrayNode interfaces = JsonNodeSupport.arrayNode();
        addInterface(interfaces, "workflow-in", "IN", "ACTIVE");
        addInterface(interfaces, "state-in", "IN", "CMD_STATE");
        addInterface(interfaces, "workflow-out", "OUT", "ACTIVE");
        return interfaces;
    }

    private void addInterface(ArrayNode interfaces, String name, String direction, String signalName) {
        ObjectNode definition = interfaces.addObject();
        definition.put("name", name);
        definition.put("direction", direction);
        definition.putArray("allowedSignals").add(signalName);
    }
}
