package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTaskResourceServiceTest {
    @Test
    void validatesTheFixedDeviceRouteDeclaredByWorkflow() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, instances, models);
        stubWorkflow(workflows, 3L, "Interface_workflow_in", "Interface_state_out");
        DeviceInstances instance = instance(55L, 7L, "使用中");
        when(instances.selectById(55L)).thenReturn(instance);
        when(models.selectById(7L)).thenReturn(model("Interface_workflow_in", "Interface_state_out"));

        assertDoesNotThrow(() -> service.validate(3L));
    }

    @Test
    void rejectsAWorkflowRouteWhoseStateMachineInterfaceDoesNotExist() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        DeviceModelsMapper models = mock(DeviceModelsMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, instances, models);
        stubWorkflow(workflows, 3L, "Interface_workflow_in", "Interface_state_out");
        when(instances.selectById(55L)).thenReturn(instance(55L, 7L, "使用中"));
        when(models.selectById(7L)).thenReturn(model("other_input", "Interface_state_out"));

        assertThrows(IllegalStateException.class, () -> service.validate(3L));
    }

    private void stubWorkflow(WorkflowService workflows, long flowModelId, String inputInterface, String outputInterface) {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setId(flowModelId);
        var connections = JsonNodeSupport.arrayNode();
        ObjectNode outbound = connections.addObject();
        outbound.put("connectionType", "NODE_TO_DEVICE");
        outbound.set("source", endpoint("heat", "Interface_state_out"));
        outbound.set("target", deviceEndpoint(55L, inputInterface));
        ObjectNode inbound = connections.addObject();
        inbound.put("connectionType", "DEVICE_TO_NODE");
        inbound.set("source", deviceEndpoint(55L, outputInterface));
        inbound.set("target", endpoint("heat", "Interface_state_in"));
        detail.setInterfaceConnections(connections);
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("nodeType", "DEV_NODE");
        node.put("deviceModelId", 7L);
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(1L, node), Map.of(1L, java.util.List.of()), Map.of(1L, java.util.List.of()), Map.of("heat", 1L), 1L, 1L);
        when(workflows.getDefinition(flowModelId)).thenReturn(detail);
        when(workflows.compileDefinition(flowModelId)).thenReturn(compiled);
    }

    private DeviceInstances instance(long id, long modelId, String lifecycle) {
        DeviceInstances result = new DeviceInstances();
        result.setId(id);
        result.setDeviceModelId(modelId);
        result.setLifecycleStatus(lifecycle);
        return result;
    }

    private DeviceModels model(String inputInterface, String outputInterface) {
        DeviceModels result = new DeviceModels();
        var interfaces = JsonNodeSupport.arrayNode();
        interfaces.addObject().put("name", inputInterface).put("direction", "IN");
        interfaces.addObject().put("name", outputInterface).put("direction", "OUT");
        result.setStateMachineInterfaces(interfaces);
        return result;
    }

    private ObjectNode endpoint(String nodeName, String interfaceName) {
        return JsonNodeSupport.objectNode().put("nodeName", nodeName).put("interfaceName", interfaceName);
    }

    private ObjectNode deviceEndpoint(long deviceInstanceId, String interfaceName) {
        return JsonNodeSupport.objectNode().put("deviceInstanceId", deviceInstanceId).put("interfaceName", interfaceName);
    }
}
