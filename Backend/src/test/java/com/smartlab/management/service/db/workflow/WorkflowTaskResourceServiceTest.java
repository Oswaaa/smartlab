package com.smartlab.management.service.db.workflow;

import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTaskResourceServiceTest {
    @Test
    void validatesBindingsByGlobalFlowNodeIdAndModel() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, instances);
        FlowNode node = new FlowNode();
        node.setId(101L);
        node.setDeviceModelId(7L);
        when(workflows.requiredDeviceNodes(3L)).thenReturn(List.of(node));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(55L);
        instance.setDeviceModelId(7L);
        instance.setLifecycleStatus("使用中");
        when(instances.selectById(55L)).thenReturn(instance);

        assertDoesNotThrow(() -> service.validate(3L, JsonNodeSupport.objectNode().put("101", 55L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.validate(3L, JsonNodeSupport.objectNode().put("1", 55L)));
    }
    @Test
    void rejectsRetiredDeviceInstance() {
        WorkflowService workflows = mock(WorkflowService.class);
        DeviceInstancesMapper instances = mock(DeviceInstancesMapper.class);
        WorkflowTaskResourceService service = new WorkflowTaskResourceService(workflows, instances);
        FlowNode node = new FlowNode();
        node.setId(101L);
        node.setDeviceModelId(7L);
        when(workflows.requiredDeviceNodes(3L)).thenReturn(List.of(node));
        DeviceInstances instance = new DeviceInstances();
        instance.setId(55L);
        instance.setDeviceModelId(7L);
        instance.setLifecycleStatus("已注销");
        when(instances.selectById(55L)).thenReturn(instance);

        assertThrows(IllegalArgumentException.class,
                () -> service.validate(3L, JsonNodeSupport.objectNode().put("101", 55L)));
    }
}
