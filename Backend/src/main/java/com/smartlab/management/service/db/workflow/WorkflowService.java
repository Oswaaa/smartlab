package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.workflow.TaskMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Transactional aggregate service for FLOW_MODELS and its FLOW_NODE members. */
@Service
public class WorkflowService extends ManagementCrudService<FlowModels> {

    private final FlowModelsMapper modelMapper;
    private final FlowNodeMapper nodeMapper;
    private final TaskMapper taskMapper;
    private final WorkflowDefinitionCompiler compiler;

    public WorkflowService(FlowModelsMapper modelMapper, FlowNodeMapper nodeMapper,
                           TaskMapper taskMapper, WorkflowDefinitionCompiler compiler) {
        super(modelMapper);
        this.modelMapper = modelMapper;
        this.nodeMapper = nodeMapper;
        this.taskMapper = taskMapper;
        this.compiler = compiler;
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
    public WorkflowDetailResponse saveDefinition(WorkflowSaveRequest request) {
        if (request == null || request.getNodesDef() == null || !request.getNodesDef().isArray()) {
            throw new IllegalArgumentException("nodesDef 必须是数组");
        }

        WorkflowDefinitionCompiler.CompiledWorkflow compiled = compiler.compile(request);
        validateSubFlowReferences(request, compiled);
        FlowModels model = request.getId() == null ? new FlowModels() : modelMapper.selectById(request.getId());
        if (request.getId() != null && model == null) {
            throw new IllegalArgumentException("流程模型不存在: " + request.getId());
        }
        if (request.getId() != null) {
            Long existingTasks = taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                    .eq(Task::getFlowModelId, request.getId()));
            if (existingTasks != null && existingTasks > 0)
                throw new IllegalStateException("流程已有任务实例，不能覆盖节点定义；请新建流程版本");
        }
        if (model == null) model = new FlowModels();
        model.setFlowName(request.getName().trim());
        if (request.getDescription() != null || model.getId() == null) model.setDescription(request.getDescription());
        if (request.getVersion() != null) model.setVersion(request.getVersion());
        else if (model.getVersion() == null) model.setVersion(1);
        if (request.getStatus() != null && !request.getStatus().isBlank()) model.setStatus(request.getStatus());
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
            definition.put("subFlowModelId", node.getSubFlowModelId());
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
