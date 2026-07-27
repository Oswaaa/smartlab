package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskStepMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 解析并校验任务resourceMap中的DEV_NODE到设备实例绑定 */
@Service
public class WorkflowTaskResourceService {
    private static final int RESOURCE_MAP_FORMAT_VERSION = 1;

    private final WorkflowService workflowService;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceModelsMapper deviceModelsMapper;
    private final TaskStepMapper taskStepMapper;
    private final FlowNodeMapper flowNodeMapper;

    public WorkflowTaskResourceService(WorkflowService workflowService, DeviceInstancesMapper deviceInstancesMapper,
                                       DeviceModelsMapper deviceModelsMapper, TaskStepMapper taskStepMapper,
                                       FlowNodeMapper flowNodeMapper) {
        this.workflowService = workflowService;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceModelsMapper = deviceModelsMapper;
        this.taskStepMapper = taskStepMapper;
        this.flowNodeMapper = flowNodeMapper;
    }

    public void validate(Long flowModelId, JsonNode resourceMap) {
        JsonNode bindings = bindings(resourceMap);
        Set<String> expectedKeys = new LinkedHashSet<>();
        for (DeviceBindingSlot slot : deviceBindingSlots(flowModelId)) {
            expectedKeys.add(slot.bindingKey());
            JsonNode taskBinding = binding(bindings, slot.bindingKey());
            long declaredModelId = taskBinding.path("deviceModelId").asLong(0);
            if (declaredModelId != slot.deviceModelId()) {
                throw new IllegalStateException("任务绑定的deviceModelId与DEV_NODE不一致: " + slot.bindingKey());
            }
            long instanceId = taskBinding.path("deviceInstanceId").asLong(0);
            if (instanceId <= 0) throw new IllegalStateException("DEV_NODE缺少任务设备实例绑定: " + slot.bindingKey());
            DeviceInstances instance = requireUsableInstance(instanceId);
            if (!Long.valueOf(slot.deviceModelId()).equals(instance.getDeviceModelId())) {
                throw new IllegalStateException("任务绑定设备实例的模型与DEV_NODE.deviceModelId不匹配: " + slot.bindingKey());
            }
            validateModelInterfaces(workflowService.getDefinition(slot.flowModelId()), slot.nodeName(), slot.deviceModelId());
        }
        bindings.fieldNames().forEachRemaining(key -> {
            if (!expectedKeys.contains(key)) throw new IllegalStateException("resourceMap包含工作流未声明的设备节点: " + key);
        });
    }

    public List<DeviceBindingSlot> deviceBindingSlots(Long rootFlowModelId) {
        if (rootFlowModelId == null) throw new IllegalArgumentException("工作流模型ID不能为空");
        List<DeviceBindingSlot> result = new ArrayList<>();
        collectBindingSlots(rootFlowModelId, List.of(), new LinkedHashSet<>(), result);
        return List.copyOf(result);
    }

    public DeviceInstances resolveDeviceInstance(Long flowModelId, String nodeName, JsonNode resourceMap) {
        if (flowModelId == null || nodeName == null || nodeName.isBlank()) {
            throw new IllegalArgumentException("设备绑定缺少工作流模型或节点名称");
        }
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = workflowService.compileDefinition(flowModelId);
        Long nodeRef = compiled.refsByNodeName().get(nodeName);
        JsonNode node = nodeRef == null ? null : compiled.nodes().get(nodeRef);
        String key = bindingKey(List.of(), nodeName);
        if (node == null || !"DEV_NODE".equals(node.path("nodeType").asText())) {
            throw new IllegalArgumentException("工作流不存在DEV_NODE: " + key);
        }
        JsonNode taskBinding = binding(bindings(resourceMap), key);
        long declaredModelId = taskBinding.path("deviceModelId").asLong(0);
        long expectedModelId = node.path("deviceModelId").asLong(0);
        if (declaredModelId != expectedModelId) {
            throw new IllegalStateException("任务绑定的deviceModelId与DEV_NODE不一致: " + key);
        }
        long instanceId = taskBinding.path("deviceInstanceId").asLong(0);
        if (instanceId <= 0) throw new IllegalStateException("DEV_NODE缺少任务设备实例绑定: " + key);
        DeviceInstances instance = requireUsableInstance(instanceId);
        if (expectedModelId <= 0 || !Long.valueOf(expectedModelId).equals(instance.getDeviceModelId())) {
            throw new IllegalStateException("任务绑定设备实例的模型与DEV_NODE.deviceModelId不匹配: " + key);
        }
        return instance;
    }

    public DeviceInstances resolveDeviceInstance(Task task, TaskStep step, FlowNode node) {
        if (task == null || task.getResourceMap() == null || step == null || node == null) {
            throw new IllegalArgumentException("任务步骤设备绑定上下文不完整");
        }
        List<String> subFlowPath = new ArrayList<>();
        Set<Long> visitedSteps = new HashSet<>();
        Long parentStepId = step.getParentStepId();
        while (parentStepId != null) {
            if (!visitedSteps.add(parentStepId)) throw new IllegalStateException("TASK_STEP父子关系存在循环");
            TaskStep parentStep = taskStepMapper.selectById(parentStepId);
            if (parentStep == null) throw new IllegalStateException("子流程父步骤不存在: " + parentStepId);
            if (parentStep.getTaskId() != null && task.getId() != null && !task.getId().equals(parentStep.getTaskId())) {
                throw new IllegalStateException("子流程父步骤不属于当前任务: " + parentStepId);
            }
            FlowNode parentNode = flowNodeMapper.selectById(parentStep.getFlowNodeId());
            if (parentNode == null || !"SUBFLOW_NODE".equals(parentNode.getNodeType())) {
                throw new IllegalStateException("子流程父步骤没有引用SUBFLOW_NODE: " + parentStepId);
            }
            subFlowPath.add(0, nodeName(parentNode.getFlowModelId(), parentNode.getNodeIdRef()));
            parentStepId = parentStep.getParentStepId();
        }
        String nodeName = nodeName(node.getFlowModelId(), node.getNodeIdRef());
        String key = bindingKey(subFlowPath, nodeName);
        return resolveBinding(key, node.getDeviceModelId(), task.getResourceMap());
    }

    private DeviceInstances resolveBinding(String key, Long expectedModelId, JsonNode resourceMap) {
        if (expectedModelId == null || expectedModelId <= 0) throw new IllegalStateException("DEV_NODE缺少deviceModelId: " + key);
        JsonNode taskBinding = binding(bindings(resourceMap), key);
        long declaredModelId = taskBinding.path("deviceModelId").asLong(0);
        if (declaredModelId != expectedModelId) {
            throw new IllegalStateException("任务绑定的deviceModelId与DEV_NODE不一致: " + key);
        }
        long instanceId = taskBinding.path("deviceInstanceId").asLong(0);
        if (instanceId <= 0) throw new IllegalStateException("DEV_NODE缺少任务设备实例绑定: " + key);
        DeviceInstances instance = requireUsableInstance(instanceId);
        if (!expectedModelId.equals(instance.getDeviceModelId())) {
            throw new IllegalStateException("任务绑定设备实例的模型与DEV_NODE.deviceModelId不匹配: " + key);
        }
        return instance;
    }

    public Set<Long> boundDeviceInstanceIds(JsonNode resourceMap) {
        JsonNode deviceBindings = bindings(resourceMap);
        Set<Long> result = new LinkedHashSet<>();
        deviceBindings.forEach(binding -> {
            long instanceId = binding.path("deviceInstanceId").asLong(0);
            if (instanceId > 0) result.add(instanceId);
        });
        return Set.copyOf(result);
    }

    public Set<Long> workflowModelIds(Long rootFlowModelId) {
        Set<Long> result = new LinkedHashSet<>();
        collectWorkflowModelIds(rootFlowModelId, result);
        return Set.copyOf(result);
    }

    public boolean containsNode(Long flowModelId, String nodeName) {
        return flowModelId != null && nodeName != null && !nodeName.isBlank()
                && workflowService.compileDefinition(flowModelId).refsByNodeName().containsKey(nodeName);
    }

    private void collectWorkflowModelIds(Long flowModelId, Set<Long> result) {
        if (flowModelId == null || !result.add(flowModelId)) return;
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = workflowService.compileDefinition(flowModelId);
        for (JsonNode node : compiled.nodes().values()) {
            if (!"SUBFLOW_NODE".equals(node.path("nodeType").asText())) continue;
            long subFlowModelId = node.path("subFlowModelId").asLong(0);
            if (subFlowModelId <= 0) throw new IllegalStateException("SUBFLOW_NODE缺少subFlowModelId");
            collectWorkflowModelIds(subFlowModelId, result);
        }
    }

    public String nodeName(Long flowModelId, long nodeIdRef) {
        return workflowService.compileDefinition(flowModelId).refsByNodeName().entrySet().stream()
                .filter(entry -> entry.getValue() == nodeIdRef).map(Map.Entry::getKey)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("工作流节点引用不存在: " + nodeIdRef));
    }

    public static String bindingKey(List<String> subFlowNodeNames, String nodeName) {
        if (nodeName == null || nodeName.isBlank()) throw new IllegalArgumentException("设备节点名称不能为空");
        List<String> segments = new ArrayList<>();
        segments.add("root");
        if (subFlowNodeNames != null) subFlowNodeNames.forEach(name -> segments.add(escapePathSegment(name)));
        segments.add(escapePathSegment(nodeName));
        return String.join("/", segments);
    }

    private static String escapePathSegment(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("节点出现路径不能包含空名称");
        return value.replace("~", "~0").replace("/", "~1");
    }

    private void collectBindingSlots(Long flowModelId, List<String> subFlowPath, Set<Long> ancestors,
                                     List<DeviceBindingSlot> result) {
        if (!ancestors.add(flowModelId)) {
            throw new IllegalStateException("工作流存在循环子流程引用: " + flowModelId);
        }
        try {
            WorkflowDetailResponse definition = workflowService.getDefinition(flowModelId);
            if (definition == null) throw new IllegalArgumentException("工作流模型不存在: " + flowModelId);
            WorkflowDefinitionCompiler.CompiledWorkflow compiled = workflowService.compileDefinition(flowModelId);
            Map<Long, String> namesByRef = namesByRef(compiled);
            compiled.nodes().entrySet().stream().sorted(Comparator.comparingLong(Map.Entry::getKey)).forEach(entry -> {
                JsonNode node = entry.getValue();
                String nodeName = namesByRef.get(entry.getKey());
                String nodeType = node.path("nodeType").asText();
                if ("DEV_NODE".equals(nodeType)) {
                    long deviceModelId = node.path("deviceModelId").asLong(0);
                    if (deviceModelId <= 0) throw new IllegalStateException("DEV_NODE缺少deviceModelId: " + nodeName);
                    result.add(new DeviceBindingSlot(bindingKey(subFlowPath, nodeName), flowModelId,
                            definition.getName(), nodeName, deviceModelId,
                            node.path("capability").path("capabilityName").asText(""), List.copyOf(subFlowPath)));
                } else if ("SUBFLOW_NODE".equals(nodeType)) {
                    long subFlowModelId = node.path("subFlowModelId").asLong(0);
                    if (subFlowModelId <= 0) throw new IllegalStateException("SUBFLOW_NODE缺少subFlowModelId");
                    List<String> childPath = new ArrayList<>(subFlowPath);
                    childPath.add(nodeName);
                    collectBindingSlots(subFlowModelId, childPath, ancestors, result);
                }
            });
        } finally {
            ancestors.remove(flowModelId);
        }
    }

    private void validateModelInterfaces(WorkflowDetailResponse definition, String nodeName, long deviceModelId) {
        DeviceRoute route = deviceRoute(definition, nodeName, deviceModelId);
        DeviceModels model = deviceModelsMapper.selectById(deviceModelId);
        if (model == null) throw new IllegalStateException("DEV_NODE引用的设备模型不存在: " + deviceModelId);
        requireStateMachineInterface(model.getStateMachineInterfaces(), route.deviceInputInterfaceName(), "IN", "NODE_TO_DEVICE目标");
        requireStateMachineInterface(model.getStateMachineInterfaces(), route.deviceOutputInterfaceName(), "OUT", "DEVICE_TO_NODE源");
    }

    private JsonNode bindings(JsonNode resourceMap) {
        if (resourceMap == null || !resourceMap.isObject()) {
            throw new IllegalArgumentException("resourceMap必须是对象");
        }
        if (resourceMap.path("formatVersion").asInt(0) != RESOURCE_MAP_FORMAT_VERSION) {
            throw new IllegalArgumentException("resourceMap.formatVersion必须为" + RESOURCE_MAP_FORMAT_VERSION);
        }
        if (!resourceMap.path("deviceBindings").isObject()) {
            throw new IllegalArgumentException("resourceMap.deviceBindings必须是对象");
        }
        return resourceMap.path("deviceBindings");
    }

    private JsonNode binding(JsonNode bindings, String bindingKey) {
        JsonNode result = bindings.path(bindingKey);
        return result.isObject() ? result : JsonNodeSupport.objectNode();
    }

    private Map<Long, String> namesByRef(WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
        Map<Long, String> result = new HashMap<>();
        compiled.refsByNodeName().forEach((name, ref) -> result.put(ref, name));
        return result;
    }

    private DeviceRoute deviceRoute(WorkflowDetailResponse definition, String nodeName, long expectedModelId) {
        JsonNode outbound = null;
        JsonNode inbound = null;
        for (JsonNode connection : iterable(definition.getInterfaceConnections())) {
            String type = connection.path("connectionType").asText("");
            if ("NODE_TO_DEVICE".equals(type) && nodeName.equals(connection.path("source").path("nodeName").asText())) outbound = connection;
            if ("DEVICE_TO_NODE".equals(type) && nodeName.equals(connection.path("target").path("nodeName").asText())) inbound = connection;
        }
        if (outbound == null || inbound == null) throw new IllegalStateException("DEV_NODE缺少完整设备模型接口连接: " + nodeName);
        long outboundModelId = outbound.path("target").path("deviceModelId").asLong(0);
        long inboundModelId = inbound.path("source").path("deviceModelId").asLong(0);
        if (outboundModelId != expectedModelId || inboundModelId != expectedModelId) {
            throw new IllegalStateException("DEV_NODE设备接口连接的deviceModelId与节点不一致: " + nodeName);
        }
        String deviceInput = outbound.path("target").path("interfaceName").asText("");
        String deviceOutput = inbound.path("source").path("interfaceName").asText("");
        if (deviceInput.isBlank() || deviceOutput.isBlank()) throw new IllegalStateException("DEV_NODE设备接口名称不能为空: " + nodeName);
        return new DeviceRoute(deviceInput, deviceOutput);
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

    public record DeviceBindingSlot(String bindingKey, Long flowModelId, String flowName, String nodeName,
                                    long deviceModelId, String capabilityName, List<String> subFlowPath) {
    }

    private record DeviceRoute(String deviceInputInterfaceName, String deviceOutputInterfaceName) {
    }
}
