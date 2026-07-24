package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 任务只使用工作流定义中NODE_TO_DEVICE和DEVICE_TO_NODE声明的固定设备路由 */
@Service
public class WorkflowTaskResourceService {
    private final WorkflowService workflowService;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceModelsMapper deviceModelsMapper;

    public WorkflowTaskResourceService(WorkflowService workflowService, DeviceInstancesMapper deviceInstancesMapper,
                                       DeviceModelsMapper deviceModelsMapper) {
        this.workflowService = workflowService;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceModelsMapper = deviceModelsMapper;
    }

    public void validate(Long flowModelId) {
        validateFlow(flowModelId, new HashSet<>());
    }

    private void validateFlow(Long flowModelId, Set<Long> visitedFlowModels) {
        if (flowModelId == null) throw new IllegalArgumentException("工作流模型ID不能为空");
        if (!visitedFlowModels.add(flowModelId)) return;
        WorkflowDetailResponse definition = workflowService.getDefinition(flowModelId);
        if (definition == null) throw new IllegalArgumentException("工作流模型不存在: " + flowModelId);
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = workflowService.compileDefinition(flowModelId);
        Map<Long, String> namesByRef = namesByRef(compiled);
        for (Map.Entry<Long, JsonNode> entry : compiled.nodes().entrySet()) {
            JsonNode node = entry.getValue();
            String nodeType = node.path("nodeType").asText();
            if ("DEV_NODE".equals(nodeType)) {
                DeviceRoute route = deviceRoute(definition, namesByRef.get(entry.getKey()));
                DeviceInstances instance = requireUsableInstance(route.deviceInstanceId());
                long expectedModelId = node.path("deviceModelId").asLong(0);
                if (expectedModelId <= 0 || !Long.valueOf(expectedModelId).equals(instance.getDeviceModelId())) {
                    throw new IllegalStateException("NODE_TO_DEVICE连接的设备实例与DEV_NODE设备模型不匹配: " + namesByRef.get(entry.getKey()));
                }
                DeviceModels model = deviceModelsMapper.selectById(instance.getDeviceModelId());
                if (model == null) throw new IllegalStateException("设备实例引用的设备模型不存在: " + instance.getDeviceModelId());
                requireStateMachineInterface(model.getStateMachineInterfaces(), route.deviceInputInterfaceName(), "IN", "NODE_TO_DEVICE目标");
                requireStateMachineInterface(model.getStateMachineInterfaces(), route.deviceOutputInterfaceName(), "OUT", "DEVICE_TO_NODE源");
            } else if ("SUBFLOW_NODE".equals(nodeType)) {
                long subFlowModelId = node.path("subFlowModelId").asLong(0);
                if (subFlowModelId <= 0) throw new IllegalStateException("SUBFLOW_NODE缺少subFlowModelId");
                validateFlow(subFlowModelId, visitedFlowModels);
            }
        }
    }

    private Map<Long, String> namesByRef(WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
        Map<Long, String> result = new HashMap<>();
        compiled.refsByNodeName().forEach((name, ref) -> result.put(ref, name));
        return result;
    }

    private DeviceRoute deviceRoute(WorkflowDetailResponse definition, String nodeName) {
        JsonNode outbound = null;
        JsonNode inbound = null;
        for (JsonNode connection : iterable(definition.getInterfaceConnections())) {
            String type = connection.path("connectionType").asText("");
            if ("NODE_TO_DEVICE".equals(type) && nodeName.equals(connection.path("source").path("nodeName").asText())) outbound = connection;
            if ("DEVICE_TO_NODE".equals(type) && nodeName.equals(connection.path("target").path("nodeName").asText())) inbound = connection;
        }
        if (outbound == null || inbound == null) throw new IllegalStateException("DEV_NODE缺少完整设备接口连接: " + nodeName);
        long outboundDeviceId = outbound.path("target").path("deviceInstanceId").asLong(0);
        long inboundDeviceId = inbound.path("source").path("deviceInstanceId").asLong(0);
        if (outboundDeviceId <= 0 || outboundDeviceId != inboundDeviceId) {
            throw new IllegalStateException("DEV_NODE设备输入输出连接必须引用同一设备实例: " + nodeName);
        }
        String deviceInput = outbound.path("target").path("interfaceName").asText("");
        String deviceOutput = inbound.path("source").path("interfaceName").asText("");
        if (deviceInput.isBlank() || deviceOutput.isBlank()) throw new IllegalStateException("DEV_NODE设备接口名称不能为空: " + nodeName);
        return new DeviceRoute(outboundDeviceId, deviceInput, deviceOutput);
    }

    private void requireStateMachineInterface(JsonNode interfaces, String name, String direction, String label) {
        for (JsonNode item : iterable(interfaces)) {
            if (name.equals(item.path("name").asText()) && direction.equals(item.path("direction").asText())) return;
        }
        throw new IllegalStateException(label + "不是设备状态机已声明的" + direction + "接口: " + name);
    }

    public DeviceInstances requireUsableInstance(Long instanceId) {
        if (instanceId == null) throw new IllegalArgumentException("设备实例ID不能为空");
        DeviceInstances instance = deviceInstancesMapper.selectById(instanceId);
        if (instance == null) throw new IllegalArgumentException("设备实例不存在: " + instanceId);
        if (!DeviceInstanceLifecycle.isUsable(instance)) throw new IllegalStateException("设备实例已注销，不能执行工作流节点: " + instanceId);
        return instance;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : java.util.List.of();
    }

    private record DeviceRoute(long deviceInstanceId, String deviceInputInterfaceName, String deviceOutputInterfaceName) {
    }
}
