package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.DeviceBindingRequirement;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTaskResourceServiceTest {
    @Test
    void requirementsKeepSlotIdWhenNodeIsRenamedAndDefinitionsAreReordered() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, mock(DeviceInstancesMapper.class), mock(DeviceModelsMapper.class), mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        WorkflowDetailResponse detail = new WorkflowDetailResponse(); detail.setId(1L); detail.setName("root"); detail.setVersion(1);
        ObjectNode heat = JsonNodeSupport.objectNode(); heat.put("name", "heat"); heat.put("nodeType", "DEV_NODE"); heat.put("deviceModelId", 7L);
        ObjectNode cool = JsonNodeSupport.objectNode(); cool.put("name", "cool"); cool.put("nodeType", "DEV_NODE"); cool.put("deviceModelId", 8L);
        Map<Long, JsonNode> before = new java.util.LinkedHashMap<>(); before.put(12L, heat); before.put(18L, cool);
        ObjectNode renamedHeat = heat.deepCopy(); renamedHeat.put("name", "renamedHeat"); ObjectNode renamedCool = cool.deepCopy(); renamedCool.put("name", "renamedCool");
        Map<Long, JsonNode> after = new java.util.LinkedHashMap<>(); after.put(18L, renamedCool); after.put(12L, renamedHeat);
        when(workflows.getDefinition(1L)).thenReturn(detail);
        when(workflows.compileDefinition(1L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(before, Map.of(), Map.of(), Map.of("heat", 12L, "cool", 18L), 12L, 18L), new WorkflowDefinitionCompiler.CompiledWorkflow(after, Map.of(), Map.of(), Map.of("renamedHeat", 12L, "renamedCool", 18L), 12L, 18L));
        assertEquals(List.of("1:12", "1:18"), service.requirements(1L).bindings().stream().map(DeviceBindingRequirement::slotId).toList());
        assertEquals(List.of("1:12", "1:18"), service.requirements(1L).bindings().stream().map(DeviceBindingRequirement::slotId).toList());
    }

    @Test
    void requirementsRejectSubflowCycle() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, mock(DeviceInstancesMapper.class), mock(DeviceModelsMapper.class), mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        WorkflowDetailResponse first = new WorkflowDetailResponse(); first.setId(1L); first.setName("A");
        WorkflowDetailResponse second = new WorkflowDetailResponse(); second.setId(2L); second.setName("B");
        ObjectNode aToB = JsonNodeSupport.objectNode(); aToB.put("name", "toB"); aToB.put("nodeType", "SUBFLOW_NODE"); aToB.put("subFlowModelId", 2L);
        ObjectNode bToA = JsonNodeSupport.objectNode(); bToA.put("name", "toA"); bToA.put("nodeType", "SUBFLOW_NODE"); bToA.put("subFlowModelId", 1L);
        when(workflows.getDefinition(1L)).thenReturn(first); when(workflows.getDefinition(2L)).thenReturn(second);
        when(workflows.compileDefinition(1L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(Map.of(1L, aToB), Map.of(), Map.of(), Map.of("toB", 1L), 1L, 1L));
        when(workflows.compileDefinition(2L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(Map.of(2L, bToA), Map.of(), Map.of(), Map.of("toA", 2L), 2L, 2L));
        assertThrows(IllegalStateException.class, () -> service.requirements(1L));
    }
    @Test
    void rejectsConflictingSlotAndLegacyBindings() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, instances, mock(DeviceModelsMapper.class),
                mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        stubWorkflow(workflows, 3L, 7L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.resolveDeviceInstance(3L, "heat", resourceMapWithBindings("3:1", 7L, 55L, "root/heat", 7L, 56L)));

        assertEquals("同一设备绑定槽位存在不一致的别名值: 3:1", error.getMessage());
    }

    @Test
    void acceptsMatchingSlotAndLegacyBindingsWithoutDuplicatingBoundInstances() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, instances, mock(DeviceModelsMapper.class),
                mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        stubWorkflow(workflows, 3L, 7L);
        when(instances.selectById(55L)).thenReturn(instance(55L, 7L, "使用中"));
        ObjectNode map = resourceMapWithBindings("3:1", 7L, 55L, "root/heat", 7L, 55L);

        assertEquals(55L, service.resolveDeviceInstance(3L, "heat", map).getId());
        assertEquals(java.util.Set.of(55L), service.boundDeviceInstanceIds(map));
    }
    @Test
    void requirementsUseNodeRefsForIdentityAndNamesOnlyForDisplay() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(
                workflows, mock(DeviceInstancesMapper.class), mock(DeviceModelsMapper.class),
                mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        stubRequirementsWithRepeatedSubFlow(workflows);

        List<DeviceBindingRequirement> result = service.requirements(1L).bindings();

        assertEquals(List.of("1:12/2:31", "1:18/2:31"),
                result.stream().map(DeviceBindingRequirement::slotId).toList());
        assertEquals(List.of("主流程 / 加热A / 温控", "主流程 / 加热B / 温控"),
                result.stream().map(DeviceBindingRequirement::occurrencePath).toList());
    }
    @Test
    void keepsSeparateBindingPathsWhenTheSameSubFlowModelAppearsTwice() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(
                workflows, mock(DeviceInstancesMapper.class), mock(DeviceModelsMapper.class),
                mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        stubRepeatedSubFlow(workflows);

        List<WorkflowTaskResourceService.DeviceBindingSlot> slots = service.deviceBindingSlots(10L);

        assertEquals(List.of("root/openLidFirst/armUp", "root/openLidSecond/armUp"),
                slots.stream().map(WorkflowTaskResourceService.DeviceBindingSlot::bindingKey).toList());
        assertEquals(List.of(20L, 20L),
                slots.stream().map(WorkflowTaskResourceService.DeviceBindingSlot::flowModelId).toList());
        assertEquals(List.of(7L, 7L),
                slots.stream().map(WorkflowTaskResourceService.DeviceBindingSlot::deviceModelId).toList());
    }

    @Test
    void resolvesNestedDeviceBindingFromTaskStepParentChain() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        TaskStepMapper steps = mock(TaskStepMapper.class);
        FlowNodeMapper nodes = mock(FlowNodeMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(
                workflows, instances, mock(DeviceModelsMapper.class), steps, nodes);
        stubRepeatedSubFlow(workflows);
        when(instances.selectById(55L)).thenReturn(instance(55L, 7L, "使用中"));

        FlowNode parentNode = flowNode(1000L, 10L, 1L, "SUBFLOW_NODE", null);
        FlowNode childNode = flowNode(2000L, 20L, 1L, "DEV_NODE", 7L);
        TaskStep parentStep = step(100L, 1000L, null);
        TaskStep childStep = step(101L, 2000L, 100L);
        when(steps.selectById(100L)).thenReturn(parentStep);
        when(nodes.selectById(1000L)).thenReturn(parentNode);

        Task task = new Task();
        task.setResourceMap(resourceMap("root/openLidFirst/armUp", 7L, 55L));

        assertEquals(55L, service.resolveDeviceInstance(task, childStep, childNode).getId());
    }

    @Test
    void resolvesTaskDeviceBindingForDevNode() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, instances, mock(DeviceModelsMapper.class),
                mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        stubWorkflow(workflows, 3L, 7L);
        when(instances.selectById(55L)).thenReturn(instance(55L, 7L, "使用中"));

        ObjectNode resourceMap = resourceMap("root/heat", 7L, 55L);

        assertEquals(55L, service.resolveDeviceInstance(3L, "heat", resourceMap).getId());
    }

    @Test
    void rejectsTaskBindingWhoseInstanceModelDoesNotMatchDevNode() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, instances, mock(DeviceModelsMapper.class),
                mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        stubWorkflow(workflows, 3L, 7L);
        when(instances.selectById(55L)).thenReturn(instance(55L, 8L, "使用中"));

        assertThrows(IllegalStateException.class, () -> service.validate(3L, resourceMap("root/heat", 7L, 55L)));
    }

    @Test
    void rejectsMissingAndUnknownTaskBindings() {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, mock(DeviceInstancesMapper.class), mock(DeviceModelsMapper.class),
                mock(TaskStepMapper.class), mock(FlowNodeMapper.class));
        stubWorkflow(workflows, 3L, 7L);

        assertThrows(IllegalArgumentException.class, () -> service.validate(3L, JsonNodeSupport.objectNode()));
        assertThrows(IllegalStateException.class, () -> service.validate(3L, resourceMap("root/unknown", 7L, 55L)));
    }

    private ObjectNode resourceMap(String bindingKey, long deviceModelId, long instanceId) {
        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("formatVersion", 1);
        ObjectNode binding = result.putObject("deviceBindings").putObject(bindingKey);
        binding.put("deviceModelId", deviceModelId);
        binding.put("deviceInstanceId", instanceId);
        return result;
    }

    private ObjectNode resourceMapWithBindings(String firstKey, long firstModelId, long firstInstanceId,
                                               String secondKey, long secondModelId, long secondInstanceId) {
        ObjectNode result = resourceMap(firstKey, firstModelId, firstInstanceId);
        ObjectNode binding = result.path("deviceBindings").withObject(secondKey);
        binding.put("deviceModelId", secondModelId);
        binding.put("deviceInstanceId", secondInstanceId);
        return result;
    }
    private void stubWorkflow(WorkflowService workflows, long flowModelId, long deviceModelId) {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setId(flowModelId);
        detail.setInterfaceConnections(JsonNodeSupport.arrayNode());
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("name", "heat");
        node.put("nodeType", "DEV_NODE");
        node.put("deviceModelId", deviceModelId);
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(1L, node), Map.of(1L, java.util.List.of()), Map.of(1L, java.util.List.of()), Map.of("heat", 1L), 1L, 1L);
        when(workflows.getDefinition(flowModelId)).thenReturn(detail);
        when(workflows.compileDefinition(flowModelId)).thenReturn(compiled);
    }

    private void stubRepeatedSubFlow(WorkflowService workflows) {
        ObjectNode first = JsonNodeSupport.objectNode();
        first.put("name", "openLidFirst");
        first.put("nodeType", "SUBFLOW_NODE");
        first.put("subFlowModelId", 20L);
        ObjectNode second = first.deepCopy();
        second.put("name", "openLidSecond");
        WorkflowDefinitionCompiler.CompiledWorkflow root = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(1L, first, 2L, second), Map.of(), Map.of(),
                Map.of("openLidFirst", 1L, "openLidSecond", 2L), 1L, 2L);

        ObjectNode arm = JsonNodeSupport.objectNode();
        arm.put("name", "armUp");
        arm.put("nodeType", "DEV_NODE");
        arm.put("deviceModelId", 7L);
        arm.putObject("capability").put("capabilityName", "MOVE_UP");
        WorkflowDefinitionCompiler.CompiledWorkflow child = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(1L, arm), Map.of(), Map.of(), Map.of("armUp", 1L), 1L, 1L);

        WorkflowDetailResponse rootDetail = new WorkflowDetailResponse();
        rootDetail.setId(10L);
        rootDetail.setName("rootFlow");
        WorkflowDetailResponse childDetail = new WorkflowDetailResponse();
        childDetail.setId(20L);
        childDetail.setName("openLid");
        when(workflows.getDefinition(10L)).thenReturn(rootDetail);
        when(workflows.getDefinition(20L)).thenReturn(childDetail);
        when(workflows.compileDefinition(10L)).thenReturn(root);
        when(workflows.compileDefinition(20L)).thenReturn(child);
    }

    private void stubRequirementsWithRepeatedSubFlow(WorkflowService workflows) {
        ObjectNode first = JsonNodeSupport.objectNode();
        first.put("name", "加热A");
        first.put("nodeType", "SUBFLOW_NODE");
        first.put("subFlowModelId", 2L);
        ObjectNode second = first.deepCopy();
        second.put("name", "加热B");
        WorkflowDefinitionCompiler.CompiledWorkflow root = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(12L, first, 18L, second), Map.of(), Map.of(),
                Map.of("加热A", 12L, "加热B", 18L), 12L, 18L);

        ObjectNode temperature = JsonNodeSupport.objectNode();
        temperature.put("name", "温控");
        temperature.put("nodeType", "DEV_NODE");
        temperature.put("deviceModelId", 7L);
        temperature.putObject("capability").put("capabilityName", "TEMPERATURE_CONTROL");
        WorkflowDefinitionCompiler.CompiledWorkflow child = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(31L, temperature), Map.of(), Map.of(), Map.of("温控", 31L), 31L, 31L);

        WorkflowDetailResponse rootDetail = new WorkflowDetailResponse();
        rootDetail.setId(1L);
        rootDetail.setName("主流程");
        rootDetail.setVersion(3);
        WorkflowDetailResponse childDetail = new WorkflowDetailResponse();
        childDetail.setId(2L);
        childDetail.setName("加热子流程");
        childDetail.setVersion(4);
        when(workflows.getDefinition(1L)).thenReturn(rootDetail);
        when(workflows.getDefinition(2L)).thenReturn(childDetail);
        when(workflows.compileDefinition(1L)).thenReturn(root);
        when(workflows.compileDefinition(2L)).thenReturn(child);
    }
    private FlowNode flowNode(long id, long flowModelId, long nodeIdRef, String nodeType, Long deviceModelId) {
        FlowNode result = new FlowNode();
        result.setId(id);
        result.setFlowModelId(flowModelId);
        result.setNodeIdRef(nodeIdRef);
        result.setNodeType(nodeType);
        result.setDeviceModelId(deviceModelId);
        return result;
    }

    private TaskStep step(long id, long flowNodeId, Long parentStepId) {
        TaskStep result = new TaskStep();
        result.setId(id);
        result.setFlowNodeId(flowNodeId);
        result.setParentStepId(parentStepId);
        return result;
    }

    private DeviceInstances instance(long id, long modelId, String lifecycle) {
        DeviceInstances result = new DeviceInstances();
        result.setId(id);
        result.setDeviceModelId(modelId);
        result.setLifecycleStatus(lifecycle);
        return result;
    }
}


