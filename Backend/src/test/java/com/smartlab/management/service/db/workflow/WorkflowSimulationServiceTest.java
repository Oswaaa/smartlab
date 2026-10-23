package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowSimulateRequest;
import com.smartlab.management.dto.workflow.WorkflowSimulationReport;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowSimulationServiceTest {

    @Test
    void blockingValidationDoesNotCreateTemporaryDevices() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstanceService instances = mock(DeviceInstanceService.class);
        WorkflowStructureWalker walker = mock(WorkflowStructureWalker.class);
        when(workflows.validate(any())).thenReturn(new WorkflowPreparationResponse(
                null, List.of(new WorkflowIssue("FLOW_INVALID", "VALIDATE", "nodes", "FLOW_NODE",
                        "start", true, "缺少 END", "补上 END 节点")),
                false, false));
        WorkflowSimulationService service = service(workflows, instances, walker, 60);

        WorkflowSimulationReport report = service.simulate(new WorkflowSimulateRequest(null, new WorkflowModelDocument()));

        assertFalse(report.walkable());
        assertNull(report.taskId());
        assertEquals("FLOW_INVALID", report.issues().get(0).code());
        verify(workflows, never()).saveDraft(any());
        verify(workflows, never()).compileDocument(any());
        verify(instances, never()).createTemporary(any());
        verify(walker, never()).walk(anyLong(), any(), any());
        verify(walker, never()).walk(any(), any(), any(), any(), any());
    }

    @Test
    void draftDocumentWalksWithoutPersistingATask() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstanceService instances = mock(DeviceInstanceService.class);
        WorkflowStructureWalker walker = mock(WorkflowStructureWalker.class);
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setId(11L);
        definition.setName("draft");
        when(workflows.validate(any())).thenReturn(new WorkflowPreparationResponse(definition, List.of(), false, false));
        when(workflows.compileDocument(any())).thenReturn(oneDevice());
        DeviceInstances temp = new DeviceInstances();
        temp.setId(90L);
        temp.setDeviceModelId(7L);
        temp.setInstanceKind(DeviceInstanceKind.TEMPORARY);
        when(instances.createTemporary(7L)).thenReturn(temp);
        when(walker.walk(any(WorkflowDefinitionCompiler.CompiledWorkflow.class), nullable(com.fasterxml.jackson.databind.JsonNode.class),
                eq(11L), any(), any())).thenReturn(new WorkflowStructureWalker.WalkResult(
                true, List.of("start", "heat", "end"),
                List.of(List.of("start", "heat", "end")), List.of()));
        WorkflowSimulationService service = service(workflows, instances, walker, 60);

        WorkflowSimulationReport report = service.simulate(new WorkflowSimulateRequest(null, new WorkflowModelDocument()));

        assertTrue(report.walkable());
        assertNull(report.taskId());
        assertEquals(List.of("start", "heat", "end"), report.pathTaken());
        assertEquals(List.of(List.of("start", "heat", "end")), report.paths());
        verify(workflows, never()).saveDraft(any());
        verify(workflows).compileDocument(any());
        verify(instances).createTemporary(7L);
        verify(instances).deleteTemporary(90L);
    }

    @Test
    void missingAdapterEventStopsWithNodeIssue() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstanceService instances = mock(DeviceInstanceService.class);
        WorkflowStructureWalker walker = mock(WorkflowStructureWalker.class);
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setId(11L);
        when(workflows.validate(any())).thenReturn(new WorkflowPreparationResponse(definition, List.of(), false, false));
        when(workflows.compileDocument(any())).thenReturn(oneDevice());
        DeviceInstances temp = new DeviceInstances();
        temp.setId(90L);
        temp.setDeviceModelId(7L);
        temp.setInstanceKind(DeviceInstanceKind.TEMPORARY);
        when(instances.createTemporary(7L)).thenReturn(temp);
        WorkflowIssue adapterIssue = new WorkflowIssue("SIM_NO_ADAPTER_EVENT", "SIMULATION", "nodes[12]", "FLOW_NODE",
                "12", true, "heat: 缺少完成事件", "补事件");
        when(walker.walk(any(WorkflowDefinitionCompiler.CompiledWorkflow.class), any(), eq(11L), any(), any()))
                .thenReturn(new WorkflowStructureWalker.WalkResult(
                false, List.of("start", "heat"), List.of(adapterIssue)));
        WorkflowSimulationService service = service(workflows, instances, walker, 60);

        WorkflowSimulationReport report = service.simulate(new WorkflowSimulateRequest(null, new WorkflowModelDocument()));

        assertFalse(report.walkable());
        assertNull(report.taskId());
        assertEquals("SIM_NO_ADAPTER_EVENT", report.issues().get(0).code());
        assertEquals(List.of("start", "heat"), report.pathTaken());
        verify(workflows, never()).saveDraft(any());
        verify(instances).createTemporary(7L);
        verify(instances).deleteTemporary(90L);
    }

    @Test
    void wallTimeoutFailsAndDeletesTemporaryInstances() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstanceService instances = mock(DeviceInstanceService.class);
        WorkflowStructureWalker walker = mock(WorkflowStructureWalker.class);
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setId(11L);
        when(workflows.getDefinition(11L)).thenReturn(definition);
        when(workflows.validate(any())).thenReturn(new WorkflowPreparationResponse(definition, List.of(), false, false));
        when(workflows.compileDefinition(11L)).thenReturn(oneDevice());
        DeviceInstances temp = new DeviceInstances();
        temp.setId(90L);
        temp.setDeviceModelId(7L);
        temp.setInstanceKind(DeviceInstanceKind.TEMPORARY);
        when(instances.createTemporary(7L)).thenReturn(temp);
        WorkflowIssue timeout = new WorkflowIssue("SIM_WALL_TIMEOUT", "SIMULATION", "nodes[]", "FLOW_NODE",
                "", true, "流程模拟超过时限仍未走完图", "检查设备");
        when(walker.walk(eq(11L), any(), any())).thenReturn(new WorkflowStructureWalker.WalkResult(
                false, List.of("start"), List.of(timeout)));
        WorkflowSimulationService service = service(workflows, instances, walker, 1);

        WorkflowSimulationReport report = service.simulate(new WorkflowSimulateRequest(11L, null));

        assertFalse(report.walkable());
        assertEquals("SIM_WALL_TIMEOUT", report.issues().get(0).code());
        verify(workflows, never()).saveDraft(any());
        verify(instances).deleteTemporary(90L);
    }

    @Test
    void budgetCountsEveryInvocationOfNestedSubflowDevices() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowSimulationService service = service(workflows, mock(DeviceInstanceService.class),
                mock(WorkflowStructureWalker.class), 60);
        when(workflows.compileDefinition(22L)).thenReturn(twoDeviceSubflow());

        assertEquals(90L, service.simulationBudgetSeconds(twoCallsToSubflow()));
    }

    @Test
    void budgetKeepsMinimumAndMaximumBounds() {
        WorkflowSimulationService service = service(mock(WorkflowService.class), mock(DeviceInstanceService.class),
                mock(WorkflowStructureWalker.class), 60);
        assertEquals(60L, service.simulationBudgetSeconds(
                new WorkflowDefinitionCompiler.CompiledWorkflow(Map.of(), Map.of(), Map.of(), Map.of(), 1L, 2L)));
        Map<Long, com.fasterxml.jackson.databind.JsonNode> manyDevices = new LinkedHashMap<>();
        for (long id = 1; id <= 50; id++) manyDevices.put(id, device("probe" + id, 7L));
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> chain = new LinkedHashMap<>();
        for (long id = 1; id < 50; id++) chain.put(id, List.of(conn(id, "out", id + 1, "in")));
        chain.put(50L, List.of());
        assertEquals(600L, service.simulationBudgetSeconds(
                new WorkflowDefinitionCompiler.CompiledWorkflow(manyDevices, chain, Map.of(), Map.of(), 1L, 50L)));
    }

    @Test
    void disconnectedDeviceNodesDoNotConsumeSimulationBudget() {
        WorkflowSimulationService service = service(mock(WorkflowService.class), mock(DeviceInstanceService.class),
                mock(WorkflowStructureWalker.class), 60);
        Map<Long, com.fasterxml.jackson.databind.JsonNode> nodes = new LinkedHashMap<>();
        nodes.put(1L, func("start", "START"));
        nodes.put(2L, func("end", "END"));
        nodes.put(3L, device("unreachable1", 7L));
        nodes.put(4L, device("unreachable2", 7L));
        nodes.put(5L, device("unreachable3", 7L));
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = new WorkflowDefinitionCompiler.CompiledWorkflow(
                nodes, Map.of(1L, List.of(conn(1, "out", 2, "in")), 2L, List.of()), Map.of(), Map.of(), 1L, 2L);

        assertEquals(60L, service.simulationBudgetSeconds(compiled));
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow twoCallsToSubflow() {
        Map<Long, com.fasterxml.jackson.databind.JsonNode> nodes = new LinkedHashMap<>();
        nodes.put(1L, func("start", "START"));
        nodes.put(2L, subflow("first", 22L));
        nodes.put(3L, subflow("second", 22L));
        nodes.put(4L, func("end", "END"));
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> outgoing = Map.of(
                1L, List.of(conn(1, "out", 2, "in")),
                2L, List.of(conn(2, "out", 3, "in")),
                3L, List.of(conn(3, "out", 4, "in")),
                4L, List.of());
        return new WorkflowDefinitionCompiler.CompiledWorkflow(nodes, outgoing, Map.of(), Map.of(), 1L, 4L);
    }

    private WorkflowDefinitionCompiler.CompiledWorkflow twoDeviceSubflow() {
        Map<Long, com.fasterxml.jackson.databind.JsonNode> nodes = new LinkedHashMap<>();
        nodes.put(1L, func("start", "START"));
        nodes.put(2L, device("heat", 7L));
        nodes.put(3L, device("cool", 7L));
        nodes.put(4L, func("end", "END"));
        Map<Long, List<WorkflowDefinitionCompiler.Connection>> outgoing = Map.of(
                1L, List.of(conn(1, "out", 2, "in")),
                2L, List.of(conn(2, "out", 3, "in")),
                3L, List.of(conn(3, "out", 4, "in")),
                4L, List.of());
        return new WorkflowDefinitionCompiler.CompiledWorkflow(nodes, outgoing, Map.of(), Map.of(), 1L, 4L);
    }

    private ObjectNode subflow(String name, long id) {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("name", name);
        node.put("nodeType", "SUBFLOW_NODE");
        node.put("subFlowModelId", id);
        return node;
    }

    private WorkflowSimulationService service(WorkflowService workflows, DeviceInstanceService instances,
                                               WorkflowStructureWalker walker, long timeoutSeconds) {
        return new WorkflowSimulationService(workflows, instances, walker, timeoutSeconds);
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

    private WorkflowDefinitionCompiler.Connection conn(long source, String sourceIface, long target, String targetIface) {
        return new WorkflowDefinitionCompiler.Connection(source, sourceIface, target, targetIface);
    }
}
