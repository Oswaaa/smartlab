package com.smartlab.engine.connection;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineCommandPort;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import com.smartlab.engine.workflow.WorkflowExecutionOperations;
import com.smartlab.engine.workflow.WorkflowInterfaceSnapshots;
import com.smartlab.engine.workflow.WorkflowTaskPollScheduler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterfaceConnectionForwarderTest {

    private final WorkflowRuntimeService runtime = mock(WorkflowRuntimeService.class);
    private final WorkflowService workflows = mock(WorkflowService.class);
    private final FlowNodeService flowNodes = mock(FlowNodeService.class);
    private final WorkflowExecutionOperations operations = mock(WorkflowExecutionOperations.class);
    private final PortConnectionPuller puller = mock(PortConnectionPuller.class);
    private final StateMachineCommandPort stateMachine = mock(StateMachineCommandPort.class);
    private final InterfaceConnectionIndex index = new InterfaceConnectionIndex();
    private final InterfaceConnectionForwarder forwarder = new InterfaceConnectionForwarder(
            runtime, workflows, flowNodes, operations, new InterfaceConnectionCatalog(workflows),
            puller, stateMachine, index);

    @Test
    void deliversCommandStateAlongOccupiedLiveEdge() {
        Task task = task();
        TaskStep step = step("RUNNING");
        FlowNode node = deviceNode();
        stubDevicePair(node);
        when(runtime.step(step.getId())).thenReturn(step);
        when(runtime.task(task.getId())).thenReturn(task);
        when(runtime.runningDeviceSteps()).thenReturn(List.of(step));
        when(flowNodes.getById(node.getId())).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(99L);
        when(operations.resolveMappedVariables(task, step, node)).thenReturn(JsonNodeSupport.objectNode());
        doUpdateInput(step);
        index.occupy(99L, task.getId(), step.getId(), "m-1");

        ObjectNode signal = JsonNodeSupport.objectNode().put("signalName", "CMD_STATE");
        signal.set("payload", JsonNodeSupport.objectNode()
                .put("deviceInstanceId", 99L).put("messageId", "m-1").put("stateName", "RUNNING"));
        forwarder.onStateMachineSignal(new StateMachineInterfaceSignalEvent(
                99L, "Interface_state_out", "STATE", signal, Map.of()));

        ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> snapshot =
                ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(runtime).updateInputSnapshot(eq(step), snapshot.capture());
        assertThat(WorkflowInterfaceSnapshots.find(snapshot.getValue(), "state-in")
                .path("signalName").asText()).isEqualTo("CMD_STATE");
    }

    @Test
    void requestsImmediatePollAfterCommandStateIsPersisted() {
        WorkflowTaskPollScheduler scheduler = mock(WorkflowTaskPollScheduler.class);
        InterfaceConnectionForwarder kicking = new InterfaceConnectionForwarder(
                runtime, workflows, flowNodes, operations, new InterfaceConnectionCatalog(workflows),
                puller, stateMachine, index, scheduler);
        Task task = task();
        TaskStep step = step("RUNNING");
        FlowNode node = deviceNode();
        stubDevicePair(node);
        when(runtime.step(step.getId())).thenReturn(step);
        when(runtime.task(task.getId())).thenReturn(task);
        when(runtime.runningDeviceSteps()).thenReturn(List.of(step));
        when(flowNodes.getById(node.getId())).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(99L);
        when(operations.resolveMappedVariables(task, step, node)).thenReturn(JsonNodeSupport.objectNode());
        doUpdateInput(step);
        index.occupy(99L, task.getId(), step.getId(), "m-1");

        ObjectNode signal = JsonNodeSupport.objectNode().put("signalName", "CMD_STATE");
        signal.set("payload", JsonNodeSupport.objectNode()
                .put("deviceInstanceId", 99L).put("messageId", "m-1").put("stateName", "COMPLETED"));
        kicking.onStateMachineSignal(new StateMachineInterfaceSignalEvent(
                99L, "Interface_state_out", "STATE", signal, Map.of()));

        verify(scheduler).requestPoll(task.getId());
        verify(runtime).appendStepLog(eq(task), eq(step), eq("INFO"),
                eq("节点 #2 接口 state-in 收到信号 CMD_STATE，stateName=COMPLETED, messageId=m-1"));
    }

    @Test
    void closesOccupancyAfterTerminalCommandState() {
        Task task = task();
        TaskStep step = step("RUNNING");
        FlowNode node = deviceNode();
        stubDevicePair(node);
        when(runtime.step(step.getId())).thenReturn(step);
        when(runtime.task(task.getId())).thenReturn(task);
        when(runtime.runningDeviceSteps()).thenReturn(List.of(step));
        when(flowNodes.getById(node.getId())).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(99L);
        when(operations.resolveMappedVariables(task, step, node)).thenReturn(JsonNodeSupport.objectNode());
        doUpdateInput(step);
        index.occupy(99L, task.getId(), step.getId(), "m-1");

        ObjectNode completed = JsonNodeSupport.objectNode().put("signalName", "CMD_STATE");
        completed.set("payload", JsonNodeSupport.objectNode()
                .put("deviceInstanceId", 99L).put("messageId", "m-1").put("stateName", "COMPLETED"));
        forwarder.onStateMachineSignal(new StateMachineInterfaceSignalEvent(
                99L, "Interface_state_out", "STATE", completed, Map.of()));

        assertThat(index.occupancy(99L)).isNull();
        assertThat(index.isClosed(99L, "m-1")).isTrue();

        ObjectNode idle = JsonNodeSupport.objectNode().put("signalName", "CMD_STATE");
        idle.set("payload", JsonNodeSupport.objectNode()
                .put("deviceInstanceId", 99L).put("messageId", "m-1").put("stateName", "IDLE"));
        forwarder.onStateMachineSignal(new StateMachineInterfaceSignalEvent(
                99L, "Interface_state_out", "STATE", idle, Map.of()));
        verify(runtime).updateInputSnapshot(eq(step), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresUncorrelatedCommandState() {
        Task task = task();
        TaskStep step = step("RUNNING");
        FlowNode node = deviceNode();
        stubDevicePair(node);
        when(runtime.runningDeviceSteps()).thenReturn(List.of(step));
        when(runtime.step(step.getId())).thenReturn(step);
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(node.getId())).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(99L);
        when(operations.ensureMessageId(step, 99L, "HEAT")).thenReturn("workflow-message");

        ObjectNode signal = JsonNodeSupport.objectNode().put("signalName", "CMD_STATE");
        signal.set("payload", JsonNodeSupport.objectNode()
                .put("deviceInstanceId", 99L).put("messageId", "console-message").put("stateName", "COMPLETED"));
        forwarder.onStateMachineSignal(new StateMachineInterfaceSignalEvent(
                99L, "Interface_state_out", "STATE", signal, Map.of()));

        verify(runtime, never()).updateInputSnapshot(eq(step), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresOperationStateNotAllowedOnNodeInterface() {
        Task task = task();
        TaskStep step = step("RUNNING");
        FlowNode node = deviceNode();
        ArrayNode existing = WorkflowInterfaceSnapshots.withSignal(
                WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "IN"),
                node.getInterfaces(), "IN", "state-in", "CMD_STATE",
                JsonNodeSupport.objectNode().put("stateName", "RUNNING"));
        step.setInterfaceInSnapshot(existing);
        stubDevicePair(node);
        when(runtime.runningDeviceSteps()).thenReturn(List.of(step));
        when(runtime.step(step.getId())).thenReturn(step);
        when(runtime.task(task.getId())).thenReturn(task);
        when(flowNodes.getById(node.getId())).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(99L);

        ObjectNode signal = JsonNodeSupport.objectNode().put("signalName", "OP_STATE");
        signal.set("payload", JsonNodeSupport.objectNode()
                .put("deviceInstanceId", 99L).put("stateName", "RUNNING"));
        forwarder.onStateMachineSignal(new StateMachineInterfaceSignalEvent(
                99L, "Interface_state_out", "STATE", signal, Map.of()));

        verify(runtime, never()).updateInputSnapshot(eq(step), org.mockito.ArgumentMatchers.any());
        assertThat(WorkflowInterfaceSnapshots.find(step.getInterfaceInSnapshot(), "state-in")
                .path("signalName").asText()).isEqualTo("CMD_STATE");
    }

    @Test
    void forwardsNodeToDeviceThroughLiveEdgeWithoutTaskContext() {
        Task task = task();
        TaskStep step = step("RUNNING");
        FlowNode node = deviceNode();
        stubDevicePair(node);
        when(stateMachine.dispatchInputSignal(eq(99L), eq("Interface_cmd_in"), eq("WF_EXECUTE_START"),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(JsonNodeSupport.objectNode()));

        WorkflowExecutionOperations.DeviceDispatchResult result = forwarder.forwardToDevice(
                task, step, node, 99L, "m-1", "state-out", "WF_EXECUTE_START", null);

        assertThat(result).isEqualTo(WorkflowExecutionOperations.DeviceDispatchResult.ACCEPTED);
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(stateMachine).dispatchInputSignal(eq(99L), eq("Interface_cmd_in"), eq("WF_EXECUTE_START"), context.capture());
        assertThat(context.getValue()).doesNotContainKeys("taskId", "taskStepId");
        assertThat(context.getValue()).containsEntry("messageId", "m-1");
        assertThat(index.occupancy(99L)).isEqualTo(new InterfaceConnectionIndex.Occupancy(task.getId(), step.getId(), "m-1"));
        assertThat(index.nodeToDevice(task.getId(), step.getId()).deviceInstanceId()).isEqualTo(99L);
        assertThat(index.deviceToNode(task.getId(), step.getId()).targetStepId()).isEqualTo(step.getId());
    }

    @Test
    void refreshForTaskInstantiatesDeviceLiveEdges() {
        Task task = task();
        TaskStep step = step("RUNNING");
        FlowNode node = deviceNode();
        stubDevicePair(node);
        when(runtime.steps(task.getId())).thenReturn(List.of(step));
        when(flowNodes.getById(node.getId())).thenReturn(node);
        when(operations.resolveDeviceInstance(task, step, node)).thenReturn(99L);

        forwarder.refreshForTask(task);

        assertThat(index.nodeToDevice(task.getId(), step.getId()).targetInterfaceName()).isEqualTo("Interface_cmd_in");
        assertThat(index.deviceToNode(99L)).hasSize(1);
    }

    private void stubDevicePair(FlowNode node) {
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        ObjectNode outbound = JsonNodeSupport.objectNode().put("connectionType", "NODE_TO_DEVICE");
        outbound.set("source", JsonNodeSupport.objectNode()
                .put("nodeName", "device").put("interfaceName", "state-out"));
        outbound.set("target", JsonNodeSupport.objectNode()
                .put("deviceModelId", 21L).put("interfaceName", "Interface_cmd_in"));
        ObjectNode inbound = JsonNodeSupport.objectNode().put("connectionType", "DEVICE_TO_NODE");
        inbound.set("source", JsonNodeSupport.objectNode()
                .put("deviceModelId", 21L).put("interfaceName", "Interface_state_out"));
        inbound.set("target", JsonNodeSupport.objectNode()
                .put("nodeName", "device").put("interfaceName", "state-in"));
        definition.setInterfaceConnections(JsonNodeSupport.arrayNode().add(outbound).add(inbound));
        when(workflows.getDefinition(node.getFlowModelId())).thenReturn(definition);
        when(workflows.compileDefinition(node.getFlowModelId())).thenReturn(
                new com.smartlab.engine.workflow.WorkflowDefinitionCompiler.CompiledWorkflow(
                        Map.of(), Map.of(), Map.of(), Map.of("device", node.getNodeIdRef()), 1L, 2L));
    }

    private void doUpdateInput(TaskStep step) {
        org.mockito.Mockito.doAnswer(invocation -> {
            step.setInterfaceInSnapshot(invocation.getArgument(1));
            return null;
        }).when(runtime).updateInputSnapshot(eq(step), org.mockito.ArgumentMatchers.any());
    }

    private Task task() {
        Task task = new Task();
        task.setId(9L);
        return task;
    }

    private TaskStep step(String status) {
        TaskStep step = new TaskStep();
        step.setId(12L);
        step.setTaskId(9L);
        step.setFlowNodeId(7L);
        step.setNodeIdRef(2L);
        step.setNodeStatus(status);
        FlowNode node = deviceNode();
        step.setInterfaceInSnapshot(WorkflowInterfaceSnapshots.initialize(node.getInterfaces(), "IN"));
        return step;
    }

    private FlowNode deviceNode() {
        FlowNode node = new FlowNode();
        node.setId(7L);
        node.setFlowModelId(3L);
        node.setNodeIdRef(2L);
        node.setNodeType("DEV_NODE");
        node.setDeviceModelId(21L);
        ObjectNode stateIn = JsonNodeSupport.objectNode();
        stateIn.put("name", "state-in");
        stateIn.put("direction", "IN");
        stateIn.put("interfaceType", "STATE");
        stateIn.putArray("allowedSignals").add("CMD_STATE");
        node.setInterfaces(JsonNodeSupport.arrayNode().add(stateIn));
        node.setCapability(JsonNodeSupport.objectNode().put("capabilityName", "HEAT"));
        return node;
    }
}
