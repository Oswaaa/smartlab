package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.adapter.InProcessAdapterSimulator;
import com.smartlab.engine.connection.InterfaceConnectionCatalog;
import com.smartlab.engine.statemachine.StateMachineCommandPort;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowStructureWalkerTest {

    @Test
    void walksBothBranchEdgesWithoutReadingAttributes() {
        WorkflowService workflows = mock(WorkflowService.class);
        StateMachineCommandPort commands = mock(StateMachineCommandPort.class);
        when(workflows.compileDefinition(1L)).thenReturn(diamond());
        when(workflows.nodeName(eq(1L), anyLong())).thenAnswer(invocation ->
                diamond().nodeName(invocation.getArgument(1)));
        WorkflowStructureWalker walker = new WorkflowStructureWalker(workflows,
                mock(InterfaceConnectionCatalog.class), commands,
                mock(DeviceTwinStateService.class), mock(InProcessAdapterSimulator.class));

        WorkflowStructureWalker.WalkResult result = walker.walk(1L, Map.of(), Instant.now().plusSeconds(5));

        assertTrue(result.walkable());
        assertEquals(List.of("start", "branch", "heat", "merge", "cool", "end"), result.pathTaken());
        assertEquals(List.of(
                List.of("start", "branch", "heat", "merge", "end"),
                List.of("start", "branch", "cool", "merge", "end")), result.paths());
        verify(commands, never()).dispatchInputSignal(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void walksInMemoryCompiledGraphWithoutLoadingSavedDefinition() {
        WorkflowService workflows = mock(WorkflowService.class);
        StateMachineCommandPort commands = mock(StateMachineCommandPort.class);
        WorkflowStructureWalker walker = new WorkflowStructureWalker(workflows,
                mock(InterfaceConnectionCatalog.class), commands,
                mock(DeviceTwinStateService.class), mock(InProcessAdapterSimulator.class));

        WorkflowStructureWalker.WalkResult result = walker.walk(diamond(), JsonNodeSupport.arrayNode(),
                null, Map.of(), Instant.now().plusSeconds(5));

        assertTrue(result.walkable());
        assertEquals(List.of("start", "branch", "heat", "merge", "cool", "end"), result.pathTaken());
        verify(workflows, never()).compileDefinition(any());
        verify(workflows, never()).getDefinition(any(Long.class));
        verify(commands, never()).dispatchInputSignal(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void probesTwoDevNodesOnTheSameTemporaryInstanceSequentially() {
        WorkflowService workflows = mock(WorkflowService.class);
        InterfaceConnectionCatalog catalog = mock(InterfaceConnectionCatalog.class);
        StateMachineCommandPort commands = mock(StateMachineCommandPort.class);
        DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
        InProcessAdapterSimulator simulator = mock(InProcessAdapterSimulator.class);
        when(workflows.compileDefinition(1L)).thenReturn(twoDevices());
        when(workflows.nodeName(eq(1L), anyLong())).thenAnswer(invocation ->
                twoDevices().nodeName(invocation.getArgument(1)));
        when(catalog.nodeToDevice(any(), any(), anyLong(), any())).thenReturn(
                new InterfaceConnectionCatalog.NodeToDeviceRoute("state_out", "Interface_workflow_in", 7L));
        ObjectNode accepted = JsonNodeSupport.objectNode();
        when(commands.dispatchInputSignal(eq(90L), eq("Interface_workflow_in"), eq("WF_EXECUTE_START"), any()))
                .thenReturn(List.of(accepted));
        AtomicInteger polls = new AtomicInteger();
        when(twins.getByInstanceId(90L)).thenAnswer(invocation -> {
            int n = polls.incrementAndGet();
            DeviceTwinStates twin = new DeviceTwinStates();
            twin.setInstanceId(90L);
            if (n == 1 || n == 4) {
                twin.setCurrentCmdState("SENT");
            } else if (n == 2 || n == 5) {
                twin.setCurrentCmdState("RUNNING");
            } else {
                twin.setCurrentCmdState("COMPLETED");
            }
            return twin;
        });
        DeviceInstances instance = temp(90L);
        instance.setInstanceKind(DeviceInstanceKind.TEMPORARY);
        WorkflowStructureWalker walker = new WorkflowStructureWalker(workflows, catalog, commands, twins, simulator);

        WorkflowStructureWalker.WalkResult result = walker.walk(1L, Map.of(7L, instance), Instant.now().plusSeconds(5));

        assertTrue(result.walkable());
        assertEquals(List.of("start", "heat", "cool", "end"), result.pathTaken());
        verify(commands, times(2)).dispatchInputSignal(eq(90L), eq("Interface_workflow_in"),
                eq("WF_EXECUTE_START"), any());
        verify(simulator, times(2)).clearLastError(90L);
    }

    @Test
    void missingCompleteEventStopsWithAdapterIssue() {
        WorkflowService workflows = mock(WorkflowService.class);
        InterfaceConnectionCatalog catalog = mock(InterfaceConnectionCatalog.class);
        StateMachineCommandPort commands = mock(StateMachineCommandPort.class);
        DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
        InProcessAdapterSimulator simulator = mock(InProcessAdapterSimulator.class);
        when(workflows.compileDefinition(1L)).thenReturn(oneDevice());
        when(workflows.nodeName(1L, 2L)).thenReturn("heat");
        when(catalog.nodeToDevice(any(), any(), anyLong(), any())).thenReturn(
                new InterfaceConnectionCatalog.NodeToDeviceRoute("state_out", "Interface_workflow_in", 7L));
        when(commands.dispatchInputSignal(anyLong(), anyString(), anyString(), any()))
                .thenReturn(List.of(JsonNodeSupport.objectNode()));
        DeviceTwinStates sent = new DeviceTwinStates();
        sent.setCurrentCmdState("SENT");
        when(twins.getByInstanceId(90L)).thenReturn(sent);
        when(simulator.takeLastError(90L)).thenReturn("SIM_NO_ADAPTER_EVENT:缺少完成事件");
        DeviceInstances instance = temp(90L);
        WorkflowStructureWalker walker = new WorkflowStructureWalker(workflows, catalog, commands, twins, simulator);

        WorkflowStructureWalker.WalkResult result = walker.walk(1L, Map.of(7L, instance), Instant.now().plusSeconds(5));

        assertFalse(result.walkable());
        assertEquals("SIM_NO_ADAPTER_EVENT", result.issues().get(0).code());
        assertTrue(result.pathTaken().contains("heat"));
    }

    @Test
    void sentThenIdleWithoutRunningIsNotWalkable() {
        WorkflowService workflows = mock(WorkflowService.class);
        InterfaceConnectionCatalog catalog = mock(InterfaceConnectionCatalog.class);
        StateMachineCommandPort commands = mock(StateMachineCommandPort.class);
        DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
        InProcessAdapterSimulator simulator = mock(InProcessAdapterSimulator.class);
        when(workflows.compileDefinition(1L)).thenReturn(oneDevice());
        when(catalog.nodeToDevice(any(), any(), anyLong(), any())).thenReturn(
                new InterfaceConnectionCatalog.NodeToDeviceRoute("state_out", "Interface_workflow_in", 7L));
        when(commands.dispatchInputSignal(anyLong(), anyString(), anyString(), any()))
                .thenReturn(List.of(JsonNodeSupport.objectNode()));
        AtomicInteger polls = new AtomicInteger();
        when(twins.getByInstanceId(90L)).thenAnswer(invocation -> {
            DeviceTwinStates twin = new DeviceTwinStates();
            twin.setInstanceId(90L);
            twin.setCurrentCmdState(polls.incrementAndGet() < 3 ? "SENT" : "IDLE");
            return twin;
        });
        DeviceInstances instance = temp(90L);
        WorkflowStructureWalker walker = new WorkflowStructureWalker(workflows, catalog, commands, twins, simulator);

        WorkflowStructureWalker.WalkResult result = walker.walk(1L, Map.of(7L, instance), Instant.now().plusSeconds(5));

        assertFalse(result.walkable());
        assertEquals("SIM_DEVICE_FAILED", result.issues().get(0).code());
        assertTrue(result.issues().get(0).message().contains("SENT"));
    }

    @Test
    void runningThenIdleCountsAsCompletedReset() {
        WorkflowService workflows = mock(WorkflowService.class);
        InterfaceConnectionCatalog catalog = mock(InterfaceConnectionCatalog.class);
        StateMachineCommandPort commands = mock(StateMachineCommandPort.class);
        DeviceTwinStateService twins = mock(DeviceTwinStateService.class);
        InProcessAdapterSimulator simulator = mock(InProcessAdapterSimulator.class);
        when(workflows.compileDefinition(1L)).thenReturn(oneDevice());
        when(catalog.nodeToDevice(any(), any(), anyLong(), any())).thenReturn(
                new InterfaceConnectionCatalog.NodeToDeviceRoute("state_out", "Interface_workflow_in", 7L));
        when(commands.dispatchInputSignal(anyLong(), anyString(), anyString(), any()))
                .thenReturn(List.of(JsonNodeSupport.objectNode()));
        AtomicInteger polls = new AtomicInteger();
        when(twins.getByInstanceId(90L)).thenAnswer(invocation -> {
            int n = polls.incrementAndGet();
            DeviceTwinStates twin = new DeviceTwinStates();
            twin.setInstanceId(90L);
            if (n == 1) twin.setCurrentCmdState("SENT");
            else if (n == 2) twin.setCurrentCmdState("RUNNING");
            else twin.setCurrentCmdState("IDLE");
            return twin;
        });
        WorkflowStructureWalker walker = new WorkflowStructureWalker(workflows, catalog, commands, twins, simulator);

        WorkflowStructureWalker.WalkResult result = walker.walk(1L, Map.of(7L, temp(90L)), Instant.now().plusSeconds(5));

        assertTrue(result.walkable());
        assertEquals(List.of("start", "heat", "end"), result.pathTaken());
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow diamond() {
        Map<Long, com.fasterxml.jackson.databind.JsonNode> nodes = new LinkedHashMap<>();
        nodes.put(1L, func("start", "START"));
        nodes.put(2L, func("branch", "BRANCH"));
        nodes.put(3L, func("heat", "START"));
        nodes.put(4L, func("cool", "START"));
        nodes.put(5L, func("merge", "AGGREGATE"));
        nodes.put(6L, func("end", "END"));
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> outgoing = new LinkedHashMap<>();
        outgoing.put(1L, List.of(conn(1, "out", 2, "in")));
        outgoing.put(2L, List.of(conn(2, "out_1", 3, "in"), conn(2, "out_2", 4, "in")));
        outgoing.put(3L, List.of(conn(3, "out", 5, "in_1")));
        outgoing.put(4L, List.of(conn(4, "out", 5, "in_2")));
        outgoing.put(5L, List.of(conn(5, "out", 6, "in")));
        outgoing.put(6L, List.of());
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> incoming = new LinkedHashMap<>();
        incoming.put(1L, List.of());
        incoming.put(2L, List.of(conn(1, "out", 2, "in")));
        incoming.put(3L, List.of(conn(2, "out_1", 3, "in")));
        incoming.put(4L, List.of(conn(2, "out_2", 4, "in")));
        incoming.put(5L, List.of(conn(3, "out", 5, "in_1"), conn(4, "out", 5, "in_2")));
        incoming.put(6L, List.of(conn(5, "out", 6, "in")));
        Map<String, Long> refs = new LinkedHashMap<>();
        refs.put("start", 1L);
        refs.put("branch", 2L);
        refs.put("heat", 3L);
        refs.put("cool", 4L);
        refs.put("merge", 5L);
        refs.put("end", 6L);
        return new WorkflowDefinitionCompiler.CompiledWorkflow(nodes, outgoing, incoming, refs, 1L, 6L);
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow twoDevices() {
        Map<Long, com.fasterxml.jackson.databind.JsonNode> nodes = new LinkedHashMap<>();
        nodes.put(1L, func("start", "START"));
        nodes.put(2L, device("heat", 7L));
        nodes.put(3L, device("cool", 7L));
        nodes.put(4L, func("end", "END"));
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> outgoing = new LinkedHashMap<>();
        outgoing.put(1L, List.of(conn(1, "out", 2, "in")));
        outgoing.put(2L, List.of(conn(2, "out", 3, "in")));
        outgoing.put(3L, List.of(conn(3, "out", 4, "in")));
        outgoing.put(4L, List.of());
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> incoming = new LinkedHashMap<>();
        incoming.put(1L, List.of());
        incoming.put(2L, List.of(conn(1, "out", 2, "in")));
        incoming.put(3L, List.of(conn(2, "out", 3, "in")));
        incoming.put(4L, List.of(conn(3, "out", 4, "in")));
        Map<String, Long> refs = Map.of("start", 1L, "heat", 2L, "cool", 3L, "end", 4L);
        return new WorkflowDefinitionCompiler.CompiledWorkflow(nodes, outgoing, incoming, refs, 1L, 4L);
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow oneDevice() {
        Map<Long, com.fasterxml.jackson.databind.JsonNode> nodes = new LinkedHashMap<>();
        nodes.put(1L, func("start", "START"));
        nodes.put(2L, device("heat", 7L));
        nodes.put(3L, func("end", "END"));
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> outgoing = new LinkedHashMap<>();
        outgoing.put(1L, List.of(conn(1, "out", 2, "in")));
        outgoing.put(2L, List.of(conn(2, "out", 3, "in")));
        outgoing.put(3L, List.of());
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> incoming = new LinkedHashMap<>();
        incoming.put(1L, List.of());
        incoming.put(2L, List.of(conn(1, "out", 2, "in")));
        incoming.put(3L, List.of(conn(2, "out", 3, "in")));
        return new WorkflowDefinitionCompiler.CompiledWorkflow(nodes, outgoing, incoming,
                Map.of("start", 1L, "heat", 2L, "end", 3L), 1L, 3L);
    }

    private ObjectNode func(String name, String functionType) {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("name", name);
        node.put("nodeType", "FUNC_NODE");
        node.put("functionType", functionType);
        return node;
    }

    private ObjectNode device(String name, long deviceModelId) {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("name", name);
        node.put("nodeType", "DEV_NODE");
        node.put("deviceModelId", deviceModelId);
        node.putObject("capability").put("capabilityName", name);
        return node;
    }

    private DeviceInstances temp(long instanceId) {
        DeviceInstances instance = new DeviceInstances();
        instance.setId(instanceId);
        instance.setDeviceModelId(7L);
        return instance;
    }

    private WorkflowDefinitionCompiler.Connection conn(long source, String sourceIface, long target, String targetIface) {
        return new WorkflowDefinitionCompiler.Connection(source, sourceIface, target, targetIface);
    }
}
