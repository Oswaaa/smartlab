package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
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