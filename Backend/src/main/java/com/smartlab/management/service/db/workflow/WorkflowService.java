package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.contract.DataType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowModelDocuments;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.device.DeviceModelService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Transactional aggregate service for FLOW_MODELS and its FLOW_NODE members. */
@Service
public class WorkflowService extends ManagementCrudService<FlowModels> {

    private final FlowModelsMapper modelMapper;
    private final FlowNodeMapper nodeMapper;
    private final TaskMapper taskMapper;
    private final WorkflowDefinitionCompiler compiler;
    private final DeviceModelService deviceModelService;
    private final ConcurrentHashMap<Long, CachedDefinition> definitionCache = new ConcurrentHashMap<>();

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
        if (id == null) return null;
        CachedDefinition cached = definitionCache.get(id);
        if (cached != null) return cached.definition();
        WorkflowDetailResponse loaded = loadDefinition(id);
        if (loaded != null) definitionCache.putIfAbsent(id, new CachedDefinition(loaded, null));
        return loaded;
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
        if (id == null) throw new IllegalArgumentException("流程模型不存在: " + id);
        CachedDefinition cached = definitionCache.get(id);
        if (cached != null && cached.compiled() != null) return cached.compiled();
        WorkflowDetailResponse detail = cached != null ? cached.definition() : getDefinition(id);
        if (detail == null) throw new IllegalArgumentException("流程模型不存在: " + id);
        WorkflowDefinitionCompiler.CompiledWorkflow compiled = compiler.compile(WorkflowModelDocuments.toDocument(detail));
        definitionCache.put(id, new CachedDefinition(detail, compiled));
        return compiled;
    }

    public String nodeName(Long flowModelId, Long nodeIdRef) {
        if (nodeIdRef == null) return "?";
        if (flowModelId == null) return "#" + nodeIdRef;
        try {
            return compileDefinition(flowModelId).nodeName(nodeIdRef);
        } catch (RuntimeException ignored) {
            return "#" + nodeIdRef;
        }
    }

    public List<FlowNode> nodes(Long flowModelId) {
        return nodeMapper.selectList(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getFlowModelId, flowModelId)
                .orderByAsc(FlowNode::getNodeIdRef));
    }

    @Transactional(rollbackFor = Exception.class)
    public com.smartlab.management.dto.workflow.WorkflowPreparationResponse saveDraft(WorkflowModelDocument document) {
        WorkflowModelDocument sanitized = WorkflowModelDocuments.sanitizeDocument(document);
        com.smartlab.engine.workflow.WorkflowPreparation prepared = compiler.prepare(sanitized, com.smartlab.engine.workflow.WorkflowPreparation.Mode.DRAFT);
        return persistPrepared(prepared, "DRAFT", false);
    }

    @Transactional(rollbackFor = Exception.class)
    public com.smartlab.management.dto.workflow.WorkflowPreparationResponse saveAsNew(WorkflowModelDocument document) {
        WorkflowModelDocument copy = WorkflowModelDocuments.asNewDraft(document);
        com.smartlab.engine.workflow.WorkflowPreparation prepared = compiler.prepare(copy, com.smartlab.engine.workflow.WorkflowPreparation.Mode.DRAFT);
        prepared.normalized().flowModelId(null);
        return persistPrepared(prepared, "DRAFT", false);
    }

    public com.smartlab.management.dto.workflow.WorkflowPreparationResponse validate(WorkflowModelDocument document) {
        PublishEvaluation evaluation = evaluateForPublish(document);
        return new com.smartlab.management.dto.workflow.WorkflowPreparationResponse(
                toDetailResponse(evaluation.prepared().normalized()),
                evaluation.prepared().issues(),
                evaluation.executable(),
                false);
    }

    @Transactional(rollbackFor = Exception.class)
    public com.smartlab.management.dto.workflow.WorkflowPreparationResponse publish(WorkflowModelDocument document) {
        PublishEvaluation evaluation = evaluateForPublish(document);
        if (evaluation.executable()) return persistPrepared(evaluation.prepared(), "ACTIVE", true);
        if (evaluation.forkingActive()) return persistPrepared(evaluation.prepared(), "DRAFT", false);
        return new com.smartlab.management.dto.workflow.WorkflowPreparationResponse(
                toDetailResponse(evaluation.prepared().normalized()), evaluation.prepared().issues(), false, false);
    }

    private PublishEvaluation evaluateForPublish(WorkflowModelDocument document) {
        WorkflowModelDocument sanitized = WorkflowModelDocuments.sanitizeDocument(document);
        FlowModels existing = sanitized.flowModelId() == null ? null : modelMapper.selectById(sanitized.flowModelId());
        boolean forkingActive = existing != null && "ACTIVE".equalsIgnoreCase(existing.getStatus());
        com.smartlab.engine.workflow.WorkflowPreparation prepared = compiler.prepare(sanitized, com.smartlab.engine.workflow.WorkflowPreparation.Mode.PUBLISH);
        List<com.smartlab.management.dto.workflow.WorkflowIssue> issues = new ArrayList<>(prepared.issues());
        boolean executable = prepared.executable();
        if (executable) {
            try {
                validateDeviceConfiguration(prepared.normalized());
                validateSubFlowReferences(prepared.normalized(), prepared.compiled());
            } catch (IllegalArgumentException exception) {
                issues.add(new com.smartlab.management.dto.workflow.WorkflowIssue("WORKFLOW_PUBLISH_VALIDATION_FAILED", "PUBLISH", "", "workflow", "", true,
                        exception.getMessage(), "请修正发布阻断问题后重试"));
                executable = false;
            }
        }
        return new PublishEvaluation(
                new com.smartlab.engine.workflow.WorkflowPreparation(prepared.normalized(), List.copyOf(issues), prepared.compiled()),
                forkingActive,
                executable);
    }

    private com.smartlab.management.dto.workflow.WorkflowPreparationResponse persistPrepared(com.smartlab.engine.workflow.WorkflowPreparation prepared, String status, boolean published) {
        WorkflowModelDocument normalized = prepared.normalized();
        if (normalized == null) throw new IllegalArgumentException("工作流定义不能为空");
        Long documentId = normalized.flowModelId();
        FlowModels existing = documentId == null ? null : modelMapper.selectById(documentId);
        if (documentId != null && existing == null) throw new IllegalArgumentException("流程模型不存在: " + documentId);
        boolean successor = existing != null && "ACTIVE".equalsIgnoreCase(existing.getStatus());
        if (successor) {
            FlowModels existingSuccessor = successorOf(existing.getId());
            if (existingSuccessor != null) throw new WorkflowSuccessorExistsException(existing, existingSuccessor);
        }
        FlowModels model = successor || existing == null ? new FlowModels() : existing;
        if (successor) {
            model.setPredecessorId(existing.getId());
            model.setVersion((existing.getVersion() == null ? 0 : existing.getVersion()) + 1);
        }
        String flowModelName = normalized.flowModelName();
        model.setFlowName(flowModelName == null ? "" : flowModelName.trim());
        if (normalized.descriptionText() != null || model.getId() == null) model.setDescription(normalized.descriptionText());
        if (model.getVersion() == null) model.setVersion(1);
        model.setStatus(status);
        model.setInterfaceConnection(nonNullArray(normalized.getInterfaceConnections()));
        model.setPortConnection(nonNullArray(normalized.getPortConnections()));
        ArrayNode definitions = nonNullArray(normalized.getNodes());
        assignNodeRefs(definitions, existing);
        ArrayNode refs = JsonNodeSupport.arrayNode();
        for (JsonNode node : definitions) refs.addObject().put("nodeIdRef", node.path("nodeIdRef").asLong()).put("nodeName", node.path("name").asText());
        model.setNodes(refs);
        if (model.getId() == null) {
            model.setCreateTime(OffsetDateTime.now());
            try {
                modelMapper.insert(model);
            } catch (DataIntegrityViolationException exception) {
                throw successorConflictOr(exception, existing, successor);
            }
        } else {
            modelMapper.updateById(model);
            nodeMapper.delete(Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowModelId, model.getId()));
        }
        for (JsonNode node : definitions) nodeMapper.insert(toEntity(model.getId(), node));
        evictDefinition(model.getId());
        if (successor && existing != null) evictDefinition(existing.getId());
        return new com.smartlab.management.dto.workflow.WorkflowPreparationResponse(getDefinition(model.getId()), prepared.issues(), prepared.executable(), published);
    }

    private FlowModels successorOf(Long predecessorId) {
        if (predecessorId == null) return null;
        return modelMapper.selectByPredecessorId(predecessorId);
    }

    private RuntimeException successorConflictOr(DataIntegrityViolationException exception, FlowModels parent, boolean forking) {
        if (forking && parent != null) {
            FlowModels successor = successorOf(parent.getId());
            if (successor != null) return new WorkflowSuccessorExistsException(parent, successor);
        }
        return exception;
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

    private WorkflowDetailResponse toDetailResponse(WorkflowModelDocument document) {
        return WorkflowModelDocuments.toDetail(document);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailResponse saveDefinition(WorkflowModelDocument document) {
        WorkflowModelDocument sanitized = WorkflowModelDocuments.sanitizeDocument(document);
        if (sanitized.getNodes() == null || !sanitized.getNodes().isArray()) {
            throw new IllegalArgumentException("nodes 必须是数组");
        }

        WorkflowDefinitionCompiler.CompiledWorkflow compiled = compiler.compile(sanitized);
        validateDeviceConfiguration(sanitized);
        validateSubFlowReferences(sanitized, compiled);
        Long documentId = sanitized.flowModelId();
        FlowModels model = documentId == null ? new FlowModels() : modelMapper.selectById(documentId);
        if (documentId != null && model == null) {
            throw new IllegalArgumentException("流程模型不存在: " + documentId);
        }
        if (documentId != null && hasTaskSnapshotReference(documentId)) {
            throw new IllegalStateException("流程或其上级流程已有任务实例，不能覆盖节点定义；请新建流程版本");
        }
        if (model == null) model = new FlowModels();
        String flowModelName = sanitized.flowModelName();
        model.setFlowName(flowModelName == null ? "" : flowModelName.trim());
        if (sanitized.descriptionText() != null || model.getId() == null) model.setDescription(sanitized.descriptionText());
        if (model.getVersion() == null) model.setVersion(1);
        if (model.getStatus() == null) model.setStatus("DRAFT");
        model.setInterfaceConnection(nonNullArray(sanitized.getInterfaceConnections()));
        model.setPortConnection(nonNullArray(sanitized.getPortConnections()));
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
        evictDefinition(model.getId());
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
        evictDefinition(id);
    }

    private void validateSubFlowReferences(WorkflowModelDocument request,
                                           WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
        for (JsonNode node : compiled.nodes().values()) {
            if (!"SUBFLOW_NODE".equals(node.path("nodeType").asText())) continue;
            long subFlowId = node.path("subFlowModelId").asLong();
            if (modelMapper.selectById(subFlowId) == null)
                throw new IllegalArgumentException("子流程模型不存在: " + subFlowId);
            if (request.flowModelId() != null && (request.flowModelId() == subFlowId
                    || referencesFlow(subFlowId, request.flowModelId(), new HashSet<>())))
                throw new IllegalArgumentException("子流程引用形成递归环: " + request.flowModelId() + " -> " + subFlowId);
        }
    }

    public void validateDeviceConfiguration(WorkflowModelDocument request) {
        if (request == null || request.getNodes() == null || !request.getNodes().isArray()) {
            throw new IllegalArgumentException("nodes 必须是数组");
        }
        for (JsonNode node : request.getNodes()) {
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
            JsonNode value = values.isObject() ? values.get(entry.getKey()) : null;
            if (value == null || value.isNull() || value.isMissingNode()) continue;
            String parameterPath = "capability.capabilityParameters." + entry.getKey();
            requireStrictValueType(entry.getValue().path("dataType").asText(""), value, nodeName, parameterPath);
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

    private WorkflowDetailResponse loadDefinition(Long id) {
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
        response.setNodesDef(JsonNodeSupport.toNode(nodes.stream()
                .map(node -> toDefinition(node, nodeNames(model.getNodes()).get(node.getNodeIdRef()))).toList()));
        response.setInterfaceConnections(model.getInterfaceConnection());
        response.setPortConnections(model.getPortConnection());
        response.setCreatorId(model.getCreatorId());
        response.setCreateTime(model.getCreateTime());
        return response;
    }

    private void evictDefinition(Long id) {
        if (id != null) definitionCache.remove(id);
    }

    private record CachedDefinition(WorkflowDetailResponse definition,
            WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
    }

    private record PublishEvaluation(com.smartlab.engine.workflow.WorkflowPreparation prepared,
            boolean forkingActive, boolean executable) {
    }
}
