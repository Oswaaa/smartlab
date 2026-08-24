package com.smartlab.engine.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowExecutionOperations;
import com.smartlab.engine.workflow.WorkflowPortSnapshots;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 端口连接按目标拉取：下游步骤活着才建边，只写入目标 PORT_IN 与目标内部变量。
 * 不改源步骤的变量空间、PORT_OUT 和观测事件。
 */
@Service
public class PortConnectionPuller {
    static final Set<String> ACTIVE_NODE_STATES = Set.of("PENDING", "RUNNING", "TERMINATING");

    private final WorkflowRuntimeService runtime;
    private final WorkflowService workflowService;
    private final FlowNodeService flowNodeService;
    private final WorkflowExecutionOperations executionOperations;
    private final PortConnectionIndex index;

    @Autowired
    public PortConnectionPuller(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, @Lazy WorkflowExecutionOperations executionOperations,
            PortConnectionIndex index) {
        this.runtime = runtime;
        this.workflowService = workflowService;
        this.flowNodeService = flowNodeService;
        this.executionOperations = executionOperations;
        this.index = index;
    }

    public PortConnectionPuller(WorkflowRuntimeService runtime, WorkflowService workflowService,
            FlowNodeService flowNodeService, WorkflowExecutionOperations executionOperations) {
        this(runtime, workflowService, flowNodeService, executionOperations, new PortConnectionIndex());
    }

    public void pullForTask(Task task) {
        if (task == null || task.getId() == null) return;
        pullForTask(task, runtime.steps(task.getId()));
    }

    public void pullForTask(Task task, List<TaskStep> steps) {
        if (task == null || task.getId() == null) return;
        if (steps == null || steps.isEmpty()) {
            index.replaceTaskEdges(task.getId(), List.of());
            return;
        }
        Map<Long, FlowNode> nodesByStepId = new HashMap<>();
        for (TaskStep step : steps) {
            FlowNode node = flowNodeService.getById(step.getFlowNodeId());
            if (node != null) nodesByStepId.put(step.getId(), node);
        }
        List<PortConnectionEdge> active = new ArrayList<>();
        java.util.Set<Long> processedModels = new java.util.HashSet<>();
        for (FlowNode node : nodesByStepId.values()) {
            Long flowModelId = node.getFlowModelId();
            if (flowModelId == null || !processedModels.add(flowModelId)) continue;
            WorkflowDetailResponse definition = workflowService.getDefinition(flowModelId);
            JsonNode connections = definition == null ? null : definition.getPortConnections();
            if (connections == null || !connections.isArray() || connections.isEmpty()) continue;
            var compiled = workflowService.compileDefinition(flowModelId);
            Map<Long, FlowNode> nodesByRef = nodesByRef(flowModelId, nodesByStepId);
            for (JsonNode connection : connections) {
                Long sourceRef = compiled.refsByNodeName().get(connection.path("source").path("nodeName").asText(""));
                Long targetRef = compiled.refsByNodeName().get(connection.path("target").path("nodeName").asText(""));
                if (sourceRef == null || targetRef == null) continue;
                for (TaskStep targetStep : steps) {
                    FlowNode targetNode = nodesByStepId.get(targetStep.getId());
                    if (targetNode == null || !flowModelId.equals(targetNode.getFlowModelId())) continue;
                    if (!Objects.equals(targetRef, targetStep.getNodeIdRef())) continue;
                    if (!ACTIVE_NODE_STATES.contains(targetStep.getNodeStatus())) continue;
                    FlowNode sourceNode = nodesByRef.get(sourceRef);
                    if (sourceNode == null) {
                        sourceNode = nodeByRef(flowModelId, sourceRef);
                    }
                    if (sourceNode == null) continue;
                    TaskStep sourceStep = findPeerStep(steps, nodesByStepId, flowModelId, sourceRef, targetStep);
                    active.add(new PortConnectionEdge(task.getId(), flowModelId, sourceRef, targetRef,
                            connection.path("source").path("portName").asText(""),
                            connection.path("target").path("portName").asText(""),
                            targetStep.getId(), targetStep.getParentStepId(),
                            targetStep.getStepDepth() == null ? 0 : targetStep.getStepDepth()));
                    deliver(task, sourceNode, sourceStep, targetStep, targetNode, connection);
                }
            }
        }
        index.replaceTaskEdges(task.getId(), active);
    }

    public void pullBetween(Task task, TaskStep sourceStep, FlowNode sourceNode,
            TaskStep targetStep, FlowNode targetNode) {
        if (task == null || sourceNode == null || targetStep == null || targetNode == null) return;
        if (!ACTIVE_NODE_STATES.contains(targetStep.getNodeStatus())) return;
        WorkflowDetailResponse definition = workflowService.getDefinition(sourceNode.getFlowModelId());
        JsonNode connections = definition == null ? null : definition.getPortConnections();
        if (connections == null || !connections.isArray()) return;
        var compiled = workflowService.compileDefinition(sourceNode.getFlowModelId());
        for (JsonNode connection : connections) {
            Long sourceRef = compiled.refsByNodeName().get(connection.path("source").path("nodeName").asText(""));
            Long targetRef = compiled.refsByNodeName().get(connection.path("target").path("nodeName").asText(""));
            if (!Objects.equals(sourceRef, sourceNode.getNodeIdRef())
                    || !Objects.equals(targetRef, targetNode.getNodeIdRef())) continue;
            deliver(task, sourceNode, sourceStep, targetStep, targetNode, connection);
        }
    }

    /** 仅把当前步骤自己的内部变量投影到 PORT_OUT；活节点在本轮接口求值后调用，死节点不再调用。 */
    public void syncOutputMailbox(FlowNode node, TaskStep step) {
        if (node == null || step == null) return;
        JsonNode ports = node.getPorts();
        if (ports == null || !ports.isArray() || ports.isEmpty()) return;
        ArrayNode snapshot = step.getPortOutSnapshot() != null && step.getPortOutSnapshot().isArray()
                ? (ArrayNode) step.getPortOutSnapshot().deepCopy()
                : WorkflowPortSnapshots.initialize(ports, "OUT");
        for (JsonNode port : iterable(ports)) {
            if (!"OUT".equals(port.path("direction").asText())) continue;
            String portName = port.path("name").asText("");
            String variableName = port.path("internalVariableName").asText("");
            JsonNode value = step.getVariableSpace() == null ? null : step.getVariableSpace().get(variableName);
            if (value != null && (value.isNull() || value.isMissingNode())) value = null;
            snapshot = WorkflowPortSnapshots.withValue(snapshot, ports, "OUT", portName, value);
        }
        if (!snapshot.equals(step.getPortOutSnapshot())) {
            runtime.updatePortOutSnapshot(step, snapshot);
        }
    }

    private void deliver(Task task, FlowNode sourceNode, TaskStep sourceStep,
            TaskStep targetStep, FlowNode targetNode, JsonNode connection) {
        String sourcePortName = connection.path("source").path("portName").asText("");
        String targetPortName = connection.path("target").path("portName").asText("");
        JsonNode sourcePort = requiredNamed(sourceNode.getPorts(), sourcePortName, "源端口");
        JsonNode targetPort = requiredNamed(targetNode.getPorts(), targetPortName, "目标端口");
        if (!"OUT".equals(sourcePort.path("direction").asText())
                || !"IN".equals(targetPort.path("direction").asText())) {
            throw new IllegalStateException("端口连接必须从OUT指向IN: " + sourcePortName + "→" + targetPortName);
        }
        JsonNode sourceVariable = requiredNamed(sourceNode.getInVariables(),
                sourcePort.path("internalVariableName").asText(""), "源变量");
        JsonNode targetVariable = requiredNamed(targetNode.getInVariables(),
                targetPort.path("internalVariableName").asText(""), "目标变量");
        String sourceType = sourceVariable.path("dataType").asText("");
        String targetType = targetVariable.path("dataType").asText("");
        if (!sourceType.equals(targetType)) {
            throw new IllegalStateException("端口连接变量数据类型不一致: " + sourceType + "→" + targetType);
        }
        JsonNode value = resolvePulledValue(task, sourceNode, sourceStep, targetStep, sourcePort);
        ArrayNode targetIn = WorkflowPortSnapshots.withValue(
                targetStep.getPortInSnapshot(), targetNode.getPorts(), "IN", targetPortName,
                value == null || value.isNull() || value.isMissingNode() ? null : value);
        if (!targetIn.equals(targetStep.getPortInSnapshot())) {
            runtime.updatePortInSnapshot(targetStep, targetIn);
        }
        if (value == null || value.isNull() || value.isMissingNode()) return;
        String targetVariableName = targetPort.path("internalVariableName").asText("");
        requireValueType(targetVariableName, targetType, value);
        ObjectNode updates = JsonNodeSupport.objectNode();
        updates.set(targetVariableName, value.deepCopy());
        runtime.mergeVariableSpace(targetStep, updates);
    }

    private JsonNode resolvePulledValue(Task task, FlowNode sourceNode, TaskStep sourceStep,
            TaskStep targetStep, JsonNode sourcePort) {
        String variableName = sourcePort.path("internalVariableName").asText("");
        if ("DEV_NODE".equals(sourceNode.getNodeType())) {
            TaskStep context = sourceStep != null ? sourceStep : targetStep;
            try {
                ObjectNode mapped = executionOperations.resolveMappedVariables(task, context, sourceNode);
                JsonNode mappedValue = mapped == null ? null : mapped.get(variableName);
                if (mappedValue != null && !mappedValue.isNull() && !mappedValue.isMissingNode()) {
                    return mappedValue;
                }
            } catch (RuntimeException ignored) {
                // 绑定尚未就绪或实例不可用时本轮跳过，不改源步骤。
            }
        }
        if (sourceStep == null || sourceStep.getVariableSpace() == null) return null;
        JsonNode frozen = sourceStep.getVariableSpace().get(variableName);
        if (frozen == null || frozen.isNull() || frozen.isMissingNode()) return null;
        return frozen;
    }

    private Map<Long, FlowNode> nodesByRef(Long flowModelId, Map<Long, FlowNode> nodesByStepId) {
        Map<Long, FlowNode> result = new HashMap<>();
        for (FlowNode node : nodesByStepId.values()) {
            if (flowModelId.equals(node.getFlowModelId())) result.put(node.getNodeIdRef(), node);
        }
        return result;
    }

    private FlowNode nodeByRef(Long flowModelId, Long nodeIdRef) {
        for (FlowNode node : workflowService.nodes(flowModelId)) {
            if (Objects.equals(node.getNodeIdRef(), nodeIdRef)) return node;
        }
        return null;
    }

    private TaskStep findPeerStep(List<TaskStep> steps, Map<Long, FlowNode> nodesByStepId,
            Long flowModelId, Long nodeIdRef, TaskStep peer) {
        for (TaskStep step : steps) {
            FlowNode node = nodesByStepId.get(step.getId());
            if (node == null || !flowModelId.equals(node.getFlowModelId())) continue;
            if (!Objects.equals(nodeIdRef, step.getNodeIdRef())) continue;
            if (!Objects.equals(peer.getParentStepId(), step.getParentStepId())) continue;
            int peerDepth = peer.getStepDepth() == null ? 0 : peer.getStepDepth();
            int depth = step.getStepDepth() == null ? 0 : step.getStepDepth();
            if (peerDepth == depth) return step;
        }
        return null;
    }

    private JsonNode requiredNamed(JsonNode definitions, String name, String label) {
        for (JsonNode definition : iterable(definitions)) {
            if (name.equals(definition.path("name").asText())) return definition;
        }
        throw new IllegalStateException(label + "不存在: " + name);
    }

    private void requireValueType(String variableName, String dataType, JsonNode value) {
        boolean valid = switch (dataType) {
            case "INTEGER" -> value.isIntegralNumber();
            case "DOUBLE" -> value.isNumber();
            case "STRING" -> value.isTextual();
            case "BOOLEAN" -> value.isBoolean();
            case "JSON" -> value.isObject() || value.isArray();
            default -> false;
        };
        if (!valid) throw new IllegalStateException("端口目标变量" + variableName + "要求" + dataType);
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }
}
