package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.contract.DataType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Transactional aggregate service for FLOW_MODELS and its FLOW_NODE members. */
@Service
public class WorkflowService extends ManagementCrudService<FlowModels> {

    private final FlowModelsMapper modelMapper;
    private final FlowNodeMapper nodeMapper;
    private final TaskMapper taskMapper;
    private final WorkflowDefinitionCompiler compiler;
    private final DeviceModelService deviceModelService;

    public WorkflowService(FlowModelsMapper modelMapper, FlowNodeMapper nodeMapper,
                           TaskMapper taskMapper, WorkflowDefinitionCompiler compiler,
                           DeviceModelService deviceModelService) {
        super(modelMapper);
        this.modelMapper = modelMapper;
        this.nodeMapper = nodeMapper;
        this.taskMapper = taskMapper;
        this.compiler = compiler;
        this.deviceModelService = deviceModelService;
    }

    @Override
    public List<FlowModels> list() {
        return modelMapper.selectList(Wrappers.<FlowModels>lambdaQuery().orderByDesc(FlowModels::getId));
    }

    public WorkflowDetailResponse getDefinition(Long id) {
        FlowModels model = modelMapper.selectById(id);
        if (model == null) return null;
        List<FlowNode> nodes = nodes(id);
        WorkflowDetailResponse response = new WorkflowDetailResponse();
        response.setId(model.getId());
        response.setName(model.getFlowName());
        response.setDescription(model.getDescription());
        response.setVersion(model.getVersion());
        response.setStatus(model.getStatus());
        response.setPredecessorId(model.getPredecessorId());
        response.setNodeIdRefs(model.getNodes());
        response.setNodesDef(JsonNodeSupport.toNode(nodes.stream().map(node -> toDefinition(node, nodeNames(model.getNodes()).get(node.getNodeIdRef()))).toList()));
        response.setInterfaceConnections(model.getInterfaceConnection());
        response.setPortConnections(model.getPortConnection());
        response.setCreatorId(model.getCreatorId());
        response.setCreateTime(model.getCreateTime());
        return response;
    }

    public WorkflowDetailResponse getDefinition(String id) {
        return getDefinition(parseId(id));
    }

    /**
     * 返回可用于创建/启动任务的工作流定义。
     *
     * 工作流状态是执行边界的一部分，不能只在前端用下拉框过滤；这里同时检查
     * 根流程和所有递归引用的子流程，避免 ACTIVE 根流程间接执行 DRAFT 子流程。
     */
    public WorkflowDetailResponse requireExecutableDefinition(Long id) {
        return requireExecutableDefinition(id, new HashSet<>());
    }

    private WorkflowDetailResponse requireExecutableDefinition(Long id, Set<Long> visiting) {
        if (id == null) throw new IllegalArgumentException("工作流模型ID不能为空");
        if (!visiting.add(id)) throw new IllegalStateException("工作流存在循环子流程引用: " + id);
        try {
            FlowModels model = modelMapper.selectById(id);
            if (model == null) throw new IllegalArgumentException("工作流模型不存在: " + id);
            String status = model.getStatus() == null ? "" : model.getStatus().trim();
            if (!"ACTIVE".equalsIgnoreCase(status)) {
                throw new IllegalStateException("工作流“" + model.getFlowName() + "”当前为"
                        + (status.isBlank() ? "未设置状态" : status) + "，只有ACTIVE工作流可以创建或启动任务");
            }
            WorkflowDetailResponse detail = getDefinition(id);
            WorkflowDefinitionCompiler.CompiledWorkflow compiled = compileDefinition(id);
            for (JsonNode node : compiled.nodes().values()) {
                if ("SUBFLOW_NODE".equals(node.path("nodeType").asText())) {
                    long subFlowId = node.path("subFlowModelId").asLong(0);
                    if (subFlowId <= 0) throw new IllegalStateException("SUBFLOW_NODE缺少subFlowModelId");
                    requireExecutableDefinition(subFlowId, visiting);
                }
            }
            return detail;
        } finally {
            visiting.remove(id);
        }
    }

    public WorkflowDefinitionCompiler.CompiledWorkflow compileDefinition(Long id) {
        WorkflowDetailResponse detail = getDefinition(id);
        if (detail == null) throw new IllegalArgumentException("流程模型不存在: " + id);
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setId(detail.getId());
        request.setName(detail.getName());
        request.setDescription(detail.getDescription());
        request.setVersion(detail.getVersion());
        request.setStatus(detail.getStatus());
        request.setNodesDef(detail.getNodesDef());
        request.setInterfaceConnections(detail.getInterfaceConnections());
        request.setPortConnections(detail.getPortConnections());
        return compiler.compile(request);
    }

    public List<FlowNode> nodes(Long flowModelId) {
        return nodeMapper.selectList(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getFlowModelId, flowModelId)
                .orderByAsc(FlowNode::getNodeIdRef));
    }

    @Transactional(rollbackFor = Exception.class)
    public com.smartlab.management.dto.workflow.WorkflowPreparationResponse saveDraft(WorkflowSaveRequest request) {
        com.smartlab.engine.workflow.WorkflowPreparation prepared = compiler.prepare(request, com.smartlab.engine.workflow.WorkflowPreparation.Mode.DRAFT);
        return persistPrepared(prepared, "DRAFT", false);
    }

    @Transactional(rollbackFor = Exception.class)
    public com.smartlab.management.dto.workflow.WorkflowPreparationResponse publish(WorkflowSaveRequest request) {
        com.smartlab.engine.workflow.WorkflowPreparation prepared = compiler.prepare(request, com.smartlab.engine.workflow.WorkflowPreparation.Mode.PUBLISH);
        if (!prepared.executable()) return new com.smartlab.management.dto.workflow.WorkflowPreparationResponse(toDetailResponse(prepared.normalized()), prepared.issues(), false, false);
        validateDeviceConfiguration(prepared.normalized());
        validateSubFlowReferences(prepared.normalized(), prepared.compiled());
        return persistPrepared(prepared, "ACTIVE", true);
    }

    private com.smartlab.management.dto.workflow.WorkflowPreparationResponse persistPrepared(com.smartlab.engine.workflow.WorkflowPreparation prepared, String status, boolean published) {
        WorkflowSaveRequest normalized = prepared.normalized();
        if (normalized == null) throw new IllegalArgumentException("工作流定义不能为空");
        FlowModels existing = normalized.getId() == null ? null : modelMapper.selectById(normalized.getId());
        if (normalized.getId() != null && existing == null) throw new IllegalArgumentException("流程模型不存在: " + normalized.getId());
        boolean successor = existing != null && "ACTIVE".equalsIgnoreCase(existing.getStatus());
        FlowModels model = successor || existing == null ? new FlowModels() : existing;
        if (successor) { model.setPredecessorId(existing.getId()); model.setVersion((existing.getVersion() == null ? 0 : existing.getVersion()) + 1); }
        model.setFlowName(normalized.getName() == null ? "" : normalized.getName().trim());
        if (normalized.getDescription() != null || model.getId() == null) model.setDescription(normalized.getDescription());
        if (!successor && normalized.getVersion() != null) model.setVersion(normalized.getVersion()); else if (model.getVersion() == null) model.setVersion(1);
        model.setStatus(status);
        if (normalized.getCreatorId() != null || model.getId() == null) model.setCreatorId(normalized.getCreatorId());
        model.setInterfaceConnection(nonNullArray(normalized.getInterfaceConnections()));
        model.setPortConnection(nonNullArray(normalized.getPortConnections()));
        ArrayNode definitions = nonNullArray(normalized.getNodesDef());
        assignNodeRefs(definitions, successor ? null : existing);
        ArrayNode refs = JsonNodeSupport.arrayNode();
        for (JsonNode node : definitions) refs.addObject().put("nodeIdRef", node.path("nodeIdRef").asLong()).put("nodeName", node.path("name").asText());
        model.setNodes(refs);
        if (model.getId() == null) { model.setCreateTime(OffsetDateTime.now()); modelMapper.insert(model); }
        else { modelMapper.updateById(model); nodeMapper.delete(Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowModelId, model.getId())); }
        for (JsonNode node : definitions) nodeMapper.insert(toEntity(model.getId(), node));
        return new com.smartlab.management.dto.workflow.WorkflowPreparationResponse(getDefinition(model.getId()), prepared.issues(), prepared.executable(), published);
    }

    private void assignNodeRefs(ArrayNode definitions, FlowModels existing) {
        Map<String, Long> existingByName = new LinkedHashMap<>();
        long next = 1;
        if (existing != null && existing.getNodes() != null) for (JsonNode ref : existing.getNodes()) { long value = ref.path("nodeIdRef").asLong(); existingByName.put(ref.path("nodeName").asText(), value); next = Math.max(next, value + 1); }
        Set<Long> used = new HashSet<>();
        for (JsonNode node : definitions) if (node instanceof ObjectNode object) {
            long requested = object.path("nodeIdRef").asLong();
            long assigned = requested > 0 && existingByName.containsValue(requested) && used.add(requested) ? requested : existingByName.getOrDefault(object.path("name").asText(), 0L);
            if (assigned > 0 && !used.contains(assigned)) used.add(assigned);
            if (assigned <= 0) { while (used.contains(next)) next++; assigned = next++; used.add(assigned); }
            object.put("nodeIdRef", assigned);
        }
    }

    private WorkflowDetailResponse toDetailResponse(WorkflowSaveRequest request) {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        if (request == null) return detail;
        detail.setId(request.getId()); detail.setName(request.getName()); detail.setDescription(request.getDescription()); detail.setVersion(request.getVersion()); detail.setStatus(request.getStatus());
        detail.setNodesDef(nonNullArray(request.getNodesDef())); detail.setInterfaceConnections(nonNullArray(request.getInterfaceConnections())); detail.setPortConnections(nonNullArray(request.getPortConnections())); detail.setCreatorId(request.getCreatorId());
        return detail;
    }
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailResponse saveDefinition(WorkflowSaveRequest request) {
        if (request == null || request.getNodesDef() == null || !request.getNodesDef().isArray()) {
            throw new IllegalArgumentException("nodesDef 必须是数组");
        }

        WorkflowDefinitionCompiler.CompiledWorkflow compiled = compiler.compile(request);
        validateDeviceConfiguration(request);
        validateSubFlowReferences(request, compiled);
        FlowModels model = request.getId() == null ? new FlowModels() : modelMapper.selectById(request.getId());
        if (request.getId() != null && model == null) {
            throw new IllegalArgumentException("流程模型不存在: " + request.getId());
        }
        if (request.getId() != null && hasTaskSnapshotReference(request.getId())) {
            throw new IllegalStateException("流程或其上级流程已有任务实例，不能覆盖节点定义；请新建流程版本");
        }
        if (model == null) model = new FlowModels();
        model.setFlowName(request.getName().trim());
        if (request.getDescription() != null || model.getId() == null) model.setDescription(request.getDescription());
        if (request.getVersion() != null) model.setVersion(request.getVersion());
        else if (model.getVersion() == null) model.setVersion(1);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String requestedStatus = request.getStatus().trim().toUpperCase();
            if (!Set.of("DRAFT", "ACTIVE").contains(requestedStatus)) {
                throw new IllegalArgumentException("工作流状态只能是DRAFT或ACTIVE");
            }
            model.setStatus(requestedStatus);
        }
        else if (model.getStatus() == null) model.setStatus("DRAFT");
        if (request.getCreatorId() != null || model.getId() == null) model.setCreatorId(request.getCreatorId());
        model.setInterfaceConnection(nonNullArray(request.getInterfaceConnections()));
        model.setPortConnection(nonNullArray(request.getPortConnections()));
        ArrayNode refs = JsonNodeSupport.arrayNode();
        compiled.nodes().forEach((ref, node) -> refs.addObject().put("nodeIdRef", ref).put("nodeName", node.path("name").asText()));
        model.setNodes(refs);
        if (model.getId() == null) {
            model.setCreateTime(OffsetDateTime.now());
            modelMapper.insert(model);
        } else {
            modelMapper.updateById(model);
            nodeMapper.delete(Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowModelId, model.getId()));
        }
        for (JsonNode definition : compiled.nodes().values()) {
            nodeMapper.insert(toEntity(model.getId(), definition));
        }
        return getDefinition(model.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDefinition(Long id) {
        Long referenceCount = nodeMapper.selectCount(Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getSubFlowModelId, id));
        if (referenceCount != null && referenceCount > 0)
            throw new IllegalStateException("流程正被 " + referenceCount + " 个子流程节点引用，不能删除");
        Long taskCount = taskMapper.selectCount(Wrappers.<Task>lambdaQuery().eq(Task::getFlowModelId, id));
        if (taskCount != null && taskCount > 0) {
            throw new IllegalStateException("流程已有任务实例，不能删除");
        }
        nodeMapper.delete(Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowModelId, id));
        modelMapper.deleteById(id);
    }

    private void validateSubFlowReferences(WorkflowSaveRequest request,
                                           WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
        for (JsonNode node : compiled.nodes().values()) {
            if (!"SUBFLOW_NODE".equals(node.path("nodeType").asText())) continue;
            long subFlowId = node.path("subFlowModelId").asLong();
            if (modelMapper.selectById(subFlowId) == null)
                throw new IllegalArgumentException("子流程模型不存在: " + subFlowId);
            if (request.getId() != null && (request.getId() == subFlowId
                    || referencesFlow(subFlowId, request.getId(), new HashSet<>())))
                throw new IllegalArgumentException("子流程引用形成递归环: " + request.getId() + " -> " + subFlowId);
        }
    }

    public void validateDeviceConfiguration(WorkflowSaveRequest request) {
        if (request == null || request.getNodesDef() == null || !request.getNodesDef().isArray()) {
            throw new IllegalArgumentException("nodesDef 必须是数组");
        }
        for (JsonNode node : request.getNodesDef()) {
            if (!"DEV_NODE".equals(node.path("nodeType").asText())) continue;
            String nodeName = node.path("name").asText("");
            long modelId = node.path("deviceModelId").asLong(0);
            DeviceModels model = modelId > 0 ? deviceModelService.getById(String.valueOf(modelId)) : null;
            if (model == null) throw configurationError(nodeName, "deviceModelId", "设备模型不存在: " + modelId);

            JsonNode capabilityNode = node.path("capability");
            String capabilityName = capabilityNode.path("capabilityName").asText("");
            JsonNode capabilityDefinition = findByText(model.getCapabilities(), "capabilityName", capabilityName);
            if (capabilityDefinition == null) {
                throw configurationError(nodeName, "capability.capabilityName", "设备模型未声明能力: " + capabilityName);
            }
            validateCapabilityParameters(nodeName, capabilityNode.path("capabilityParameters"), capabilityDefinition.path("parameters"));
            validateAttributeMappings(nodeName, node.path("internalVariables"), model.getAttributes());
        }
    }

    private void validateCapabilityParameters(String nodeName, JsonNode values, JsonNode definitions) {
        if (!values.isMissingNode() && !values.isObject()) {
            throw configurationError(nodeName, "capability.capabilityParameters", "必须是对象");
        }
        Map<String, JsonNode> expected = new LinkedHashMap<>();
        for (JsonNode definition : iterable(definitions)) {
            String name = definition.path("name").asText("");
            if (!name.isBlank()) expected.put(name, definition);
        }
        if (values.isObject()) {
            values.fieldNames().forEachRemaining(name -> {
                if (!expected.containsKey(name)) {
                    throw configurationError(nodeName, "capability.capabilityParameters." + name, "能力未声明该参数");
                }
            });
        }
        for (Map.Entry<String, JsonNode> entry : expected.entrySet()) {
            String parameterPath = "capability.capabilityParameters." + entry.getKey();
            if (!values.isObject() || !values.has(entry.getKey())) {
                throw configurationError(nodeName, parameterPath, "缺少必需能力参数");
            }
            String dataType = entry.getValue().path("dataType").asText("");
            requireStrictValueType(dataType, values.get(entry.getKey()), nodeName, parameterPath);
        }
    }

    private void validateAttributeMappings(String nodeName, JsonNode variables, JsonNode attributes) {
        Map<String, JsonNode> definitions = new LinkedHashMap<>();
        for (JsonNode attribute : iterable(attributes)) {
            String name = attribute.path("attributeName").asText("");
            if (!name.isBlank()) definitions.put(name, attribute);
        }
        int position = 0;
        for (JsonNode variable : iterable(variables)) {
            String mapping = variable.path("attributesMapping").asText("").trim();
            if (!mapping.isBlank()) {
                String path = "internalVariables[" + position + "].attributesMapping";
                JsonNode attribute = definitions.get(mapping);
                if (attribute == null) throw configurationError(nodeName, path, "设备模型属性不存在: " + mapping);
                String variableType = variable.path("dataType").asText("");
                String attributeType = attribute.path("dataType").asText("");
                if (!variableType.equals(attributeType)) {
                    throw configurationError(nodeName, path, "映射变量与设备属性数据类型不一致: " + variableType + " -> " + attributeType);
                }
            }
            position++;
        }
    }

    private void requireStrictValueType(String dataType, JsonNode value, String nodeName, String path) {
        boolean valid;
        try {
            valid = value != null && !value.isNull() && switch (DataType.valueOf(dataType)) {
                case INTEGER -> value.isIntegralNumber();
                case DOUBLE -> value.isNumber();
                case STRING -> value.isTextual();
                case BOOLEAN -> value.isBoolean();
                case JSON -> value.isObject() || value.isArray();
            };
        } catch (IllegalArgumentException error) {
            throw configurationError(nodeName, path, "设备模型声明了不支持的数据类型: " + dataType);
        }
        if (!valid) throw configurationError(nodeName, path, "参数值数据类型不一致，要求" + dataType);
    }

    private JsonNode findByText(JsonNode values, String field, String expected) {
        for (JsonNode value : iterable(values)) {
            if (expected.equals(value.path(field).asText())) return value;
        }
        return null;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private IllegalArgumentException configurationError(String nodeName, String path, String reason) {
        return new IllegalArgumentException("节点" + nodeName + "." + path + ": " + reason);
    }

    private boolean hasTaskSnapshotReference(long targetFlowModelId) {
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>query().select("flow_model_id"));
        if (tasks == null) return false;
        return tasks.stream().map(Task::getFlowModelId).filter(java.util.Objects::nonNull).distinct()
                .anyMatch(rootFlowModelId -> rootFlowModelId == targetFlowModelId
                        || referencesFlow(rootFlowModelId, targetFlowModelId, new HashSet<>()));
    }

    private boolean referencesFlow(long flowModelId, long targetFlowModelId, Set<Long> visited) {
        if (!visited.add(flowModelId)) return false;
        for (FlowNode node : nodes(flowModelId)) {
            Long child = node.getSubFlowModelId();
            if (child == null) continue;
            if (child == targetFlowModelId || referencesFlow(child, targetFlowModelId, visited)) return true;
        }
        return false;
    }

    private FlowNode toEntity(Long flowModelId, JsonNode node) {
        FlowNode entity = new FlowNode();
        entity.setFlowModelId(flowModelId);
        entity.setNodeIdRef(node.path("nodeIdRef").asLong());
        String nodeType = node.path("nodeType").asText();
        entity.setNodeType(nodeType);
        if (node.hasNonNull("subFlowModelId")) entity.setSubFlowModelId(node.path("subFlowModelId").asLong());
        ObjectNode capability = JsonNodeSupport.objectNode();
        if ("DEV_NODE".equals(nodeType)) {
            entity.setDeviceModelId(node.path("deviceModelId").asLong());
            capability.setAll((ObjectNode) nonNullObject(node.path("capability")));
        } else if ("FUNC_NODE".equals(nodeType)) {
            capability.put("functionType", node.path("functionType").asText());
            if (node.has("expression")) capability.put("expression", node.path("expression").asText());
        } else if ("SUBFLOW_NODE".equals(nodeType)) {
            capability.put("subFlowModelId", node.path("subFlowModelId").asLong());
            capability.put("subFlowModelDescription", node.path("subFlowModelDescription").asText(""));
        }
        entity.setCapability(capability);
        entity.setInVariables(nonNullArray(node.path("internalVariables")));
        entity.setLifecycle(nonNullObject(node.path("lifecycle")));
        entity.setInterfaces(nonNullArray(node.path("interfaces")));
        entity.setPorts(nonNullArray(node.path("ports")));
        entity.setActions(nonNullArray(node.path("actions")));
        entity.setCreateTime(OffsetDateTime.now());
        return entity;
    }

    private JsonNode toDefinition(FlowNode node, String nodeName) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.put("name", nodeName == null || nodeName.isBlank() ? "node-" + node.getNodeIdRef() : nodeName);
        definition.put("nodeType", node.getNodeType());
        if ("DEV_NODE".equals(node.getNodeType())) {
            definition.put("deviceModelId", node.getDeviceModelId());
            definition.set("capability", nonNullObject(node.getCapability()));
        } else if ("FUNC_NODE".equals(node.getNodeType())) {
            definition.put("functionType", node.getCapability().path("functionType").asText());
            if (node.getCapability().has("expression")) definition.put("expression", node.getCapability().path("expression").asText());
        } else if ("SUBFLOW_NODE".equals(node.getNodeType())) {
            JsonNode capability = nonNullObject(node.getCapability());
            long subFlowModelId = capability.path("subFlowModelId").asLong(
                    node.getSubFlowModelId() == null ? 0 : node.getSubFlowModelId());
            definition.put("subFlowModelId", subFlowModelId);
            definition.put("subFlowModelDescription", capability.path("subFlowModelDescription").asText(""));
        }
        definition.set("internalVariables", nonNullArray(node.getInVariables()));
        definition.set("lifecycle", nonNullObject(node.getLifecycle()));
        definition.set("interfaces", nonNullArray(node.getInterfaces()));
        definition.set("ports", nonNullArray(node.getPorts()));
        definition.set("actions", nonNullArray(node.getActions()));
        return definition;
    }

    private java.util.Map<Long, String> nodeNames(JsonNode refs) {
        java.util.Map<Long, String> result = new java.util.HashMap<>();
        if (refs != null && refs.isArray()) {
            for (JsonNode item : refs) {
                if (item.isObject() && item.path("nodeIdRef").canConvertToLong()) {
                    result.put(item.path("nodeIdRef").asLong(), item.path("nodeName").asText());
                }
            }
        }
        return result;
    }
    private ArrayNode nonNullArray(JsonNode node) {
        return node != null && node.isArray() ? node.deepCopy() : JsonNodeSupport.arrayNode();
    }

    private JsonNode nonNullObject(JsonNode node) {
        return node != null && node.isObject() ? node.deepCopy() : JsonNodeSupport.objectNode();
    }
}
