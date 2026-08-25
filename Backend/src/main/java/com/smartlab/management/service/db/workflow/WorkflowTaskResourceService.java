package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.CapabilityParameterRequirement;
import com.smartlab.management.dto.workflow.DeviceBindingRequirement;
import com.smartlab.management.dto.workflow.TaskDeviceBindingRequest;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowResourceRequirementsResponse;
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
import java.util.LinkedHashMap;
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

    public WorkflowResourceRequirementsResponse requirements(Long rootFlowModelId) {
        if (rootFlowModelId == null) throw new IllegalArgumentException("工作流模型ID不能为空");
        WorkflowDetailResponse rootDefinition = workflowService.getDefinition(rootFlowModelId);
        if (rootDefinition == null) throw new IllegalArgumentException("工作流模型不存在: " + rootFlowModelId);
        List<DeviceBindingRequirement> bindings = new ArrayList<>();
        collectBindingRequirements(rootFlowModelId, rootDefinition.getName(), List.of(), new LinkedHashSet<>(), bindings);
        return new WorkflowResourceRequirementsResponse(rootFlowModelId, rootDefinition.getVersion(), List.copyOf(bindings));
    }
    public PreparedTaskResources prepare(Long flowModelId, List<TaskDeviceBindingRequest> deviceBindings) {
        ObjectNode resourceMap = JsonNodeSupport.objectNode();
        resourceMap.put("formatVersion", RESOURCE_MAP_FORMAT_VERSION);
        ObjectNode canonicalBindings = resourceMap.putObject("deviceBindings");
        List<WorkflowIssue> issues = new ArrayList<>();
        if (flowModelId == null) {
            issues.add(bindingIssue("TASK_BINDING_FLOW_MISSING", "", "任务引用的工作流模型不能为空", "请选择工作流模型"));
            return new PreparedTaskResources(resourceMap, List.copyOf(issues));
        }
        List<DeviceBindingRequirement> requirements;
        try { requirements = requirements(flowModelId).bindings(); }
        catch (RuntimeException error) {
            issues.add(bindingIssue("TASK_BINDING_REQUIREMENTS_INVALID", "", error.getMessage(), "修复工作流设备节点定义"));
            return new PreparedTaskResources(resourceMap, List.copyOf(issues));
        }
        Map<String, TaskDeviceBindingRequest> supplied = new LinkedHashMap<>();
        if (deviceBindings != null) for (TaskDeviceBindingRequest binding : deviceBindings) {
            String slotId = binding == null ? null : binding.slotId();
            if (slotId == null || slotId.isBlank()) {
                issues.add(bindingIssue("TASK_BINDING_SLOT_INVALID", "", "设备绑定缺少slotId", "使用工作流 requirements 返回的 slotId"));
                continue;
            }
            TaskDeviceBindingRequest previous = supplied.putIfAbsent(slotId, binding);
            if (previous != null && !java.util.Objects.equals(previous.deviceInstanceId(), binding.deviceInstanceId()))
                issues.add(bindingIssue("TASK_BINDING_CONFLICT", slotId, "同一设备绑定槽位存在不一致的实例值", "每个 slotId 只提交一个设备实例"));
        }
        Set<String> expected = new LinkedHashSet<>();
        for (DeviceBindingRequirement requirement : requirements) expected.add(requirement.slotId());
        supplied.keySet().stream().filter(slotId -> !expected.contains(slotId)).sorted().forEach(slotId ->
                issues.add(bindingIssue("TASK_BINDING_UNKNOWN", slotId, "设备绑定槽位不属于当前工作流", "刷新工作流 requirements 后重新绑定")));
        for (DeviceBindingRequirement requirement : requirements) {
            TaskDeviceBindingRequest suppliedBinding = supplied.get(requirement.slotId());
            if (suppliedBinding == null || suppliedBinding.deviceInstanceId() == null || suppliedBinding.deviceInstanceId() <= 0) {
                issues.add(bindingIssue("TASK_BINDING_MISSING", requirement.slotId(), "DEV_NODE缺少任务设备实例绑定", "为该槽位选择一个可用设备实例"));
                continue;
            }
            ObjectNode binding = canonicalBindings.putObject(requirement.slotId());
            binding.put("deviceModelId", requirement.deviceModelId());
            binding.put("deviceInstanceId", suppliedBinding.deviceInstanceId());
            ObjectNode storedParameters = storedHoleParameters(requirement, suppliedBinding.capabilityParameters(), issues);
            if (storedParameters.size() > 0) binding.set("capabilityParameters", storedParameters);
        }
        issues.addAll(inspectCapabilityParameters(flowModelId, resourceMap));
        if (issues.stream().noneMatch(WorkflowIssue::blocking)) try { validate(flowModelId, resourceMap); }
        catch (RuntimeException error) { issues.add(bindingIssue("TASK_BINDING_INVALID", "", error.getMessage(), "检查设备实例、模型和设备接口")); }
        return new PreparedTaskResources(resourceMap, List.copyOf(issues));
    }

    private WorkflowIssue bindingIssue(String code, String slotId, String message, String suggestion) {
        String path = slotId == null || slotId.isBlank() ? "deviceBindings" : "deviceBindings." + slotId;
        return new WorkflowIssue(code, "BINDING", path, "DEV_NODE", slotId == null ? "" : slotId, true,
                message == null ? "任务设备绑定无效" : message, suggestion);
    }
    public void validate(Long flowModelId, JsonNode resourceMap) {
        JsonNode bindings = bindings(resourceMap);
        List<DeviceBindingSlot> legacySlots = deviceBindingSlots(flowModelId);
        List<DeviceBindingRequirement> requirements = requirements(flowModelId).bindings();
        if (legacySlots.size() != requirements.size()) throw new IllegalStateException("工作流设备绑定要求不一致");
        Set<String> expectedKeys = new LinkedHashSet<>();
        for (int index = 0; index < legacySlots.size(); index++) {
            DeviceBindingSlot slot = legacySlots.get(index);
            DeviceBindingRequirement requirement = requirements.get(index);
            expectedKeys.add(slot.bindingKey());
            expectedKeys.add(requirement.slotId());
            JsonNode taskBinding = binding(bindings, requirement.slotId(), slot.bindingKey());
            long declaredModelId = taskBinding.path("deviceModelId").asLong(0);
            if (declaredModelId != slot.deviceModelId()) {
                throw new IllegalStateException("任务绑定的deviceModelId与DEV_NODE不一致: " + requirement.slotId());
            }
            long instanceId = taskBinding.path("deviceInstanceId").asLong(0);
            if (instanceId <= 0) throw new IllegalStateException("DEV_NODE缺少任务设备实例绑定: " + requirement.slotId());
            DeviceInstances instance = requireUsableInstance(instanceId);
            if (!Long.valueOf(slot.deviceModelId()).equals(instance.getDeviceModelId())) {
                throw new IllegalStateException("任务绑定设备实例的模型与DEV_NODE.deviceModelId不匹配: " + requirement.slotId());
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
        JsonNode taskBinding = binding(bindings(resourceMap), slotId(List.of(new BindingOccurrence(flowModelId, nodeRef, nodeName))), key);
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
        List<BindingOccurrence> subFlowOccurrences = new ArrayList<>();
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
            String parentNodeName = nodeName(parentNode.getFlowModelId(), parentNode.getNodeIdRef());
            subFlowPath.add(0, parentNodeName);
            subFlowOccurrences.add(0, new BindingOccurrence(parentNode.getFlowModelId(), parentNode.getNodeIdRef(), parentNodeName));
            parentStepId = parentStep.getParentStepId();
        }
        String nodeName = nodeName(node.getFlowModelId(), node.getNodeIdRef());
        String key = bindingKey(subFlowPath, nodeName);
        subFlowOccurrences.add(new BindingOccurrence(node.getFlowModelId(), node.getNodeIdRef(), nodeName));
        return resolveBinding(node.getDeviceModelId(), task.getResourceMap(), slotId(subFlowOccurrences), key);
    }

    public JsonNode resolveCapabilityParameters(Task task, TaskStep step, FlowNode node) {
        if (task == null || task.getResourceMap() == null || step == null || node == null) {
            throw new IllegalArgumentException("任务步骤能力参数解析上下文不完整");
        }
        List<String> subFlowPath = new ArrayList<>();
        List<BindingOccurrence> occurrences = new ArrayList<>();
        Set<Long> visitedSteps = new HashSet<>();
        Long parentStepId = step.getParentStepId();
        while (parentStepId != null) {
            if (!visitedSteps.add(parentStepId)) throw new IllegalStateException("TASK_STEP父子关系存在循环");
            TaskStep parentStep = taskStepMapper.selectById(parentStepId);
            if (parentStep == null) throw new IllegalStateException("子流程父步骤不存在: " + parentStepId);
            FlowNode parentNode = flowNodeMapper.selectById(parentStep.getFlowNodeId());
            if (parentNode == null || !"SUBFLOW_NODE".equals(parentNode.getNodeType())) {
                throw new IllegalStateException("子流程父步骤没有引用SUBFLOW_NODE: " + parentStepId);
            }
            String parentNodeName = nodeName(parentNode.getFlowModelId(), parentNode.getNodeIdRef());
            subFlowPath.add(0, parentNodeName);
            occurrences.add(0, new BindingOccurrence(parentNode.getFlowModelId(), parentNode.getNodeIdRef(), parentNodeName));
            parentStepId = parentStep.getParentStepId();
        }
        String nodeName = nodeName(node.getFlowModelId(), node.getNodeIdRef());
        occurrences.add(new BindingOccurrence(node.getFlowModelId(), node.getNodeIdRef(), nodeName));
        JsonNode slotBinding = binding(bindings(task.getResourceMap()), slotId(occurrences), bindingKey(subFlowPath, nodeName));
        return mergeCapabilityParameters(node, slotBinding.path("capabilityParameters"));
    }

    public List<WorkflowIssue> inspectCapabilityParameters(Long flowModelId, JsonNode resourceMap) {
        List<WorkflowIssue> issues = new ArrayList<>();
        if (flowModelId == null) return issues;
        JsonNode deviceBindings;
        try { deviceBindings = bindings(resourceMap); }
        catch (RuntimeException error) { return issues; }
        for (DeviceBindingRequirement requirement : requirements(flowModelId).bindings()) {
            JsonNode slot = deviceBindings.path(requirement.slotId());
            if (!slot.isObject() || slot.path("deviceInstanceId").asLong(0) <= 0) continue;
            JsonNode slotParameters = slot.path("capabilityParameters");
            Set<String> holeNames = new LinkedHashSet<>();
            for (CapabilityParameterRequirement parameter : requirement.capabilityParameters()) {
                if (!parameter.hole()) continue;
                holeNames.add(parameter.name());
                JsonNode value = slotParameters.path(parameter.name());
                if (isParameterHole(value)) {
                    issues.add(bindingIssue("TASK_BINDING_PARAM_MISSING", requirement.slotId(),
                            "能力参数未填写: " + parameter.name(), "在创建任务时填写该设备节点的空洞参数"));
                } else if (!matchesDataType(parameter.dataType(), value)) {
                    issues.add(bindingIssue("TASK_BINDING_PARAM_TYPE", requirement.slotId(),
                            "能力参数类型不正确: " + parameter.name() + "，要求" + parameter.dataType(),
                            "按设备能力声明的数据类型填写参数"));
                }
            }
            if (slotParameters.isObject()) {
                slotParameters.fieldNames().forEachRemaining(name -> {
                    if (!holeNames.contains(name)) {
                        issues.add(bindingIssue("TASK_BINDING_PARAM_UNEXPECTED", requirement.slotId(),
                                "任务不能覆盖模型已写死的能力参数: " + name, "只填写模型中为 null 的能力参数"));
                    }
                });
            } else if (!slotParameters.isMissingNode() && !slotParameters.isNull()) {
                issues.add(bindingIssue("TASK_BINDING_PARAM_INVALID", requirement.slotId(),
                        "capabilityParameters必须是对象", "按槽位提交空洞参数对象"));
            }
        }
        return issues;
    }

    private ObjectNode storedHoleParameters(DeviceBindingRequirement requirement, JsonNode suppliedParameters,
                                            List<WorkflowIssue> issues) {
        ObjectNode stored = JsonNodeSupport.objectNode();
        Set<String> holeNames = new LinkedHashSet<>();
        for (CapabilityParameterRequirement parameter : requirement.capabilityParameters()) {
            if (!parameter.hole()) continue;
            holeNames.add(parameter.name());
            JsonNode value = suppliedParameters == null ? null : suppliedParameters.get(parameter.name());
            if (isParameterHole(value)) continue;
            stored.set(parameter.name(), value.deepCopy());
        }
        if (suppliedParameters != null && suppliedParameters.isObject()) {
            suppliedParameters.fieldNames().forEachRemaining(name -> {
                if (!holeNames.contains(name)) {
                    issues.add(bindingIssue("TASK_BINDING_PARAM_UNEXPECTED", requirement.slotId(),
                            "任务不能覆盖模型已写死的能力参数: " + name, "只填写模型中为 null 的能力参数"));
                }
            });
        } else if (suppliedParameters != null && !suppliedParameters.isNull()) {
            issues.add(bindingIssue("TASK_BINDING_PARAM_INVALID", requirement.slotId(),
                    "capabilityParameters必须是对象", "按槽位提交空洞参数对象"));
        }
        return stored;
    }

    private String capabilityDisplayName(JsonNode node, long deviceModelId) {
        String capabilityName = node.path("capability").path("capabilityName").asText("");
        DeviceModels model = deviceModelsMapper.selectById(deviceModelId);
        if (model == null) return capabilityName;
        JsonNode capability = findByText(model.getCapabilities(), "capabilityName", capabilityName);
        if (capability == null) return capabilityName;
        String displayName = capability.path("displayName").asText("");
        return displayName.isBlank() ? capabilityName : displayName;
    }

    private List<CapabilityParameterRequirement> capabilityParameterContracts(JsonNode node, long deviceModelId) {
        DeviceModels model = deviceModelsMapper.selectById(deviceModelId);
        if (model == null) return List.of();
        String capabilityName = node.path("capability").path("capabilityName").asText("");
        JsonNode capability = findByText(model.getCapabilities(), "capabilityName", capabilityName);
        if (capability == null) return List.of();
        JsonNode modelValues = node.path("capability").path("capabilityParameters");
        List<CapabilityParameterRequirement> result = new ArrayList<>();
        for (JsonNode definition : iterable(capability.path("parameters"))) {
            String name = definition.path("name").asText("");
            if (name.isBlank()) continue;
            JsonNode modelValue = modelValues.get(name);
            boolean hole = isParameterHole(modelValue);
            result.add(new CapabilityParameterRequirement(
                    name,
                    definition.path("displayName").asText(name),
                    definition.path("dataType").asText(""),
                    hole ? JsonNodeSupport.MAPPER.nullNode() : modelValue,
                    hole));
        }
        return result;
    }

    private JsonNode mergeCapabilityParameters(FlowNode node, JsonNode slotParameters) {
        JsonNode modelValues = node.getCapability() == null
                ? JsonNodeSupport.objectNode() : node.getCapability().path("capabilityParameters");
        ObjectNode effective = JsonNodeSupport.objectNode();
        List<CapabilityParameterRequirement> contracts = capabilityParameterContracts(
                toCapabilityContractNode(node), node.getDeviceModelId() == null ? 0 : node.getDeviceModelId());
        if (contracts.isEmpty()) {
            if (modelValues.isObject()) {
                modelValues.fields().forEachRemaining(entry -> {
                    if (!isParameterHole(entry.getValue())) effective.set(entry.getKey(), entry.getValue().deepCopy());
                });
            }
            return effective;
        }
        for (CapabilityParameterRequirement parameter : contracts) {
            JsonNode modelValue = modelValues.get(parameter.name());
            if (!isParameterHole(modelValue)) {
                effective.set(parameter.name(), modelValue.deepCopy());
                continue;
            }
            JsonNode slotValue = slotParameters == null ? null : slotParameters.get(parameter.name());
            if (isParameterHole(slotValue)) {
                throw new IllegalStateException("DEV_NODE能力参数未绑定: " + parameter.name());
            }
            effective.set(parameter.name(), slotValue.deepCopy());
        }
        return effective;
    }

    private ObjectNode toCapabilityContractNode(FlowNode node) {
        ObjectNode contract = JsonNodeSupport.objectNode();
        contract.put("deviceModelId", node.getDeviceModelId() == null ? 0 : node.getDeviceModelId());
        if (node.getCapability() != null && node.getCapability().isObject()) {
            contract.set("capability", node.getCapability());
        }
        return contract;
    }

    private JsonNode findByText(JsonNode values, String field, String expected) {
        for (JsonNode value : iterable(values)) {
            if (expected.equals(value.path(field).asText())) return value;
        }
        return null;
    }

    private static boolean isParameterHole(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull();
    }

    private static boolean matchesDataType(String dataType, JsonNode value) {
        if (value == null || value.isNull() || dataType == null || dataType.isBlank()) return false;
        return switch (dataType) {
            case "INTEGER" -> value.isIntegralNumber();
            case "DOUBLE" -> value.isNumber();
            case "STRING" -> value.isTextual();
            case "BOOLEAN" -> value.isBoolean();
            case "JSON" -> value.isObject() || value.isArray();
            default -> false;
        };
    }

    private DeviceInstances resolveBinding(Long expectedModelId, JsonNode resourceMap, String... bindingKeys) {
        String key = bindingKeys.length == 0 ? "" : bindingKeys[0];
        if (expectedModelId == null || expectedModelId <= 0) throw new IllegalStateException("DEV_NODE缺少deviceModelId: " + key);
        JsonNode taskBinding = binding(bindings(resourceMap), bindingKeys);
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

    public List<BoundDeviceBinding> boundDeviceBindings(JsonNode resourceMap) {
        JsonNode deviceBindings = bindings(resourceMap);
        List<BoundDeviceBinding> result = new ArrayList<>();
        deviceBindings.fields().forEachRemaining(entry -> {
            long instanceId = entry.getValue().path("deviceInstanceId").asLong(0);
            if (instanceId > 0) result.add(new BoundDeviceBinding(entry.getKey(), instanceId));
        });
        result.sort(Comparator.comparing(BoundDeviceBinding::slotId));
        return List.copyOf(result);
    }    public Set<Long> workflowModelIds(Long rootFlowModelId) {
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

    private void collectBindingRequirements(Long flowModelId, String rootFlowName, List<BindingOccurrence> ancestors,
                                            Set<Long> visitedModels, List<DeviceBindingRequirement> result) {
        if (!visitedModels.add(flowModelId)) {
            throw new IllegalStateException("工作流存在循环子流程引用: " + flowModelId);
        }
        try {
            WorkflowDetailResponse definition = workflowService.getDefinition(flowModelId);
            if (definition == null) throw new IllegalArgumentException("工作流模型不存在: " + flowModelId);
            WorkflowDefinitionCompiler.CompiledWorkflow compiled = workflowService.compileDefinition(flowModelId);
            Map<Long, String> namesByRef = namesByRef(compiled);
            compiled.nodes().entrySet().stream().sorted(Comparator.comparingLong(Map.Entry::getKey)).forEach(entry -> {
                long nodeIdRef = entry.getKey();
                JsonNode node = entry.getValue();
                String nodeName = namesByRef.get(nodeIdRef);
                String nodeType = node.path("nodeType").asText();
                List<BindingOccurrence> occurrence = new ArrayList<>(ancestors);
                occurrence.add(new BindingOccurrence(flowModelId, nodeIdRef, nodeName));
                if ("DEV_NODE".equals(nodeType)) {
                    long deviceModelId = node.path("deviceModelId").asLong(0);
                    if (deviceModelId <= 0) throw new IllegalStateException("DEV_NODE缺少deviceModelId: " + nodeName);
                    String capabilityName = node.path("capability").path("capabilityName").asText("");
                    result.add(new DeviceBindingRequirement(slotId(occurrence), occurrencePath(rootFlowName, occurrence),
                            flowModelId, definition.getVersion(), nodeIdRef, definition.getName(), nodeName,
                            deviceModelId, capabilityName, capabilityDisplayName(node, deviceModelId),
                            capabilityParameterContracts(node, deviceModelId)));
                } else if ("SUBFLOW_NODE".equals(nodeType)) {
                    long subFlowModelId = node.path("subFlowModelId").asLong(0);
                    if (subFlowModelId <= 0) throw new IllegalStateException("SUBFLOW_NODE缺少subFlowModelId");
                    collectBindingRequirements(subFlowModelId, rootFlowName, List.copyOf(occurrence), visitedModels, result);
                }
            });
        } finally {
            visitedModels.remove(flowModelId);
        }
    }

    private static String slotId(List<BindingOccurrence> occurrence) {
        return occurrence.stream().map(item -> item.flowModelId() + ":" + item.nodeIdRef()).collect(java.util.stream.Collectors.joining("/"));
    }

    private static String occurrencePath(String rootFlowName, List<BindingOccurrence> occurrence) {
        List<String> names = new ArrayList<>();
        names.add(rootFlowName == null || rootFlowName.isBlank() ? "root" : rootFlowName);
        occurrence.forEach(item -> names.add(item.nodeName()));
        return String.join(" / ", names);
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

    private JsonNode binding(JsonNode bindings, String... bindingKeys) {
        JsonNode resolved = null;
        String canonicalKey = bindingKeys.length == 0 ? "" : bindingKeys[0];
        for (String bindingKey : bindingKeys) {
            JsonNode candidate = bindings.path(bindingKey);
            if (!candidate.isObject()) continue;
            if (resolved != null && !resolved.equals(candidate)) {
                throw new IllegalStateException("同一设备绑定槽位存在不一致的别名值: " + canonicalKey);
            }
            resolved = candidate;
        }
        return resolved == null ? JsonNodeSupport.objectNode() : resolved;
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

    public record BoundDeviceBinding(String slotId, Long deviceInstanceId) {}

    public record PreparedTaskResources(JsonNode resourceMap, List<WorkflowIssue> issues) {
        public boolean blocked() { return issues.stream().anyMatch(WorkflowIssue::blocking); }
    }

    public record DeviceBindingSlot(String bindingKey, Long flowModelId, String flowName, String nodeName,
                                    long deviceModelId, String capabilityName, List<String> subFlowPath) {
    }

    private record BindingOccurrence(Long flowModelId, long nodeIdRef, String nodeName) {}

    private record DeviceRoute(String deviceInputInterfaceName, String deviceOutputInterfaceName) {
    }
}
