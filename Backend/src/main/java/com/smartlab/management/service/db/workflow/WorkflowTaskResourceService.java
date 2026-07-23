package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** Validates the task-time binding from globally unique FLOW_NODE.ID to a concrete device instance. */
@Service
public class WorkflowTaskResourceService {
    private final WorkflowService workflowService;
    private final DeviceInstancesMapper deviceInstancesMapper;

    public WorkflowTaskResourceService(WorkflowService workflowService, DeviceInstancesMapper deviceInstancesMapper) {
        this.workflowService = workflowService;
        this.deviceInstancesMapper = deviceInstancesMapper;
    }

    public void validate(Long flowModelId, JsonNode resourceMap) {
        List<FlowNode> required = workflowService.requiredDeviceNodes(flowModelId);
        if (required.isEmpty()) return;
        if (resourceMap == null || !resourceMap.isObject())
            throw new IllegalArgumentException("任务缺少设备资源映射");
        for (FlowNode node : required) {
            JsonNode value = resourceMap.get(String.valueOf(node.getId()));
            if (value == null || !value.canConvertToLong() || value.asLong() <= 0)
                throw new IllegalArgumentException("设备节点未绑定实例，flowNodeId=" + node.getId());
            DeviceInstances instance = deviceInstancesMapper.selectById(value.asLong());
            if (instance == null)
                throw new IllegalArgumentException("设备实例不存在: " + value.asLong());
            if (!DeviceInstanceLifecycle.isUsable(instance))
                throw new IllegalArgumentException("设备实例已注销: " + value.asLong());
            if (!node.getDeviceModelId().equals(instance.getDeviceModelId()))
                throw new IllegalArgumentException("设备实例与节点模型不匹配，flowNodeId=" + node.getId());
        }
    }
    public DeviceInstances requireUsableInstance(Long instanceId) {
        if (instanceId == null) throw new IllegalArgumentException("设备实例ID不能为空");
        DeviceInstances instance = deviceInstancesMapper.selectById(instanceId);
        if (instance == null) throw new IllegalArgumentException("设备实例不存在: " + instanceId);
        if (!DeviceInstanceLifecycle.isUsable(instance))
            throw new IllegalStateException("设备实例已注销，不能执行工作流节点: " + instanceId);
        return instance;
    }
}
