package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservableSnapshotReader;
import com.smartlab.engine.observation.ObservationHistoryStore;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 节点计算表达式与 UPDATE.valueExpression 共用：沿端口找到与现值同一条观测序列。
 */
@Service
public class WorkflowExpressionHistoryResolver {
    private final WorkflowService workflowService;
    private final FlowNodeService flowNodeService;
    private final WorkflowRuntimeService runtime;
    private final WorkflowExecutionOperations executionOperations;
    private final ObservableSnapshotReader observationReader;
    private final ConstraintExpressionEvaluator expressionEvaluator;

    public WorkflowExpressionHistoryResolver(WorkflowService workflowService, FlowNodeService flowNodeService,
            WorkflowRuntimeService runtime, WorkflowExecutionOperations executionOperations,
            ObservableSnapshotReader observationReader, ConstraintExpressionEvaluator expressionEvaluator) {
        this.workflowService = workflowService;
        this.flowNodeService = flowNodeService;
        this.runtime = runtime;
        this.executionOperations = executionOperations;
        this.observationReader = observationReader;
        this.expressionEvaluator = expressionEvaluator;
    }

    public Map<String, List<ConstraintExpressionEvaluator.TimedValue>> histories(Task task, TaskStep step,
            FlowNode node, String expression, Map<String, JsonNode> values, Instant now) {
        Map<String, List<ConstraintExpressionEvaluator.TimedValue>> result = new LinkedHashMap<>();
        if (expression == null || !expression.matches("(?is).*\\b(?:rate|delta|avg|max|min)\\s*\\(.*")) {
            return result;
        }
        Instant evaluationTime = now == null ? Instant.now() : now;
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        String nodeName = compiled.nodeName(node.getNodeIdRef());
        if (!compiled.refsByNodeName().containsValue(node.getNodeIdRef())) {
            throw new IllegalStateException("流程节点缺少名称映射: " + node.getNodeIdRef());
        }
        Instant since = evaluationTime.minus(ObservationHistoryStore.DEFAULT_MAX_SECONDS, ChronoUnit.SECONDS);
        for (String variable : expressionEvaluator.referencedVariables(expression)) {
            ObservableKey key = observationKey(task, step, node, compiled, nodeName, variable);
            List<ConstraintExpressionEvaluator.TimedValue> samples = new ArrayList<>();
            List<com.smartlab.engine.observation.ObservationSample> history = observationReader.readHistory(key, since);
            if (history != null) {
                history.forEach(sample -> samples.add(new ConstraintExpressionEvaluator.TimedValue(
                        sample.observedAt(), sample.value())));
            }
            JsonNode current = values == null ? null : values.get(variable);
            if (current != null && current.isNumber()) {
                samples.add(new ConstraintExpressionEvaluator.TimedValue(evaluationTime, current.deepCopy()));
            }
            result.put(variable, List.copyOf(samples));
        }
        return result;
    }

    private ObservableKey observationKey(Task task, TaskStep step, FlowNode node,
            WorkflowDefinitionCompiler.CompiledWorkflow compiled, String nodeName, String variableName) {
        JsonNode connection = inboundPortConnection(node, compiled, nodeName, variableName);
        if (connection == null) {
            return nodeVariableKey(task, node.getFlowModelId(), nodeName, variableName);
        }
        String sourceNodeName = connection.path("source").path("nodeName").asText("");
        Long sourceRef = compiled.refsByNodeName().get(sourceNodeName);
        if (sourceRef == null || sourceNodeName.isBlank()) {
            return nodeVariableKey(task, node.getFlowModelId(), nodeName, variableName);
        }
        FlowNode sourceNode = resolveSourceNode(task, step, node.getFlowModelId(), sourceRef, compiled);
        if (sourceNode == null) {
            return nodeVariableKey(task, node.getFlowModelId(), nodeName, variableName);
        }
        JsonNode sourcePort = requiredNamed(sourceNode.getPorts(),
                connection.path("source").path("portName").asText(""), "源端口");
        String sourceVariableName = sourcePort.path("internalVariableName").asText("");
        JsonNode sourceVariable = requiredNamed(sourceNode.getInVariables(), sourceVariableName, "源变量");
        if ("DEV_NODE".equals(sourceNode.getNodeType())) {
            String attributeName = sourceVariable.path("attributesMapping").asText("").trim();
            if (!attributeName.isBlank()) {
                TaskStep sourceStep = findPeerStep(task, step, node.getFlowModelId(), sourceRef);
                TaskStep context = sourceStep != null ? sourceStep : step;
                try {
                    long instanceId = executionOperations.resolveDeviceInstance(task, context, sourceNode);
                    if (instanceId > 0) {
                        return new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE, instanceId,
                                null, null, null, null, attributeName, null);
                    }
                } catch (RuntimeException ignored) {
                    // 绑定尚未就绪时与端口拉取一致：回退到源节点变量历史。
                }
            }
        }
        return nodeVariableKey(task, sourceNode.getFlowModelId() != null
                ? sourceNode.getFlowModelId() : node.getFlowModelId(), sourceNodeName, sourceVariableName);
    }

    private JsonNode inboundPortConnection(FlowNode node, WorkflowDefinitionCompiler.CompiledWorkflow compiled,
            String nodeName, String variableName) {
        var definition = workflowService.getDefinition(node.getFlowModelId());
        JsonNode connections = definition == null ? null : definition.getPortConnections();
        if (connections == null || !connections.isArray()) return null;
        for (JsonNode connection : connections) {
            if (!nodeName.equals(connection.path("target").path("nodeName").asText(""))) continue;
            Long targetRef = compiled.refsByNodeName().get(nodeName);
            if (!Objects.equals(targetRef, node.getNodeIdRef())) continue;
            JsonNode targetPort = namedOrNull(node.getPorts(), connection.path("target").path("portName").asText(""));
            if (targetPort == null || !"IN".equals(targetPort.path("direction").asText())) continue;
            if (variableName.equals(targetPort.path("internalVariableName").asText(""))) return connection;
        }
        return null;
    }

    private FlowNode resolveSourceNode(Task task, TaskStep step, Long flowModelId, Long sourceRef,
            WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
        TaskStep sourceStep = findPeerStep(task, step, flowModelId, sourceRef);
        if (sourceStep != null) {
            FlowNode found = flowNodeService.getById(sourceStep.getFlowNodeId());
            if (found != null) return found;
        }
        try {
            return nodeByRef(flowModelId, sourceRef);
        } catch (RuntimeException ignored) {
            return sourceNodeFromCompiled(flowModelId, sourceRef, compiled);
        }
    }

    private FlowNode sourceNodeFromCompiled(Long flowModelId, Long sourceRef,
            WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
        if (compiled == null || compiled.nodes() == null) return null;
        JsonNode definition = compiled.nodes().get(sourceRef);
        if (definition == null || !definition.isObject()) return null;
        FlowNode node = new FlowNode();
        node.setFlowModelId(flowModelId);
        node.setNodeIdRef(sourceRef);
        node.setNodeType(definition.path("nodeType").asText(""));
        if (definition.has("deviceModelId") && definition.path("deviceModelId").isNumber()) {
            node.setDeviceModelId(definition.path("deviceModelId").asLong());
        }
        JsonNode variables = definition.has("internalVariables")
                ? definition.get("internalVariables") : definition.get("inVariables");
        node.setInVariables(variables);
        node.setPorts(definition.get("ports"));
        return node;
    }

    private TaskStep findPeerStep(Task task, TaskStep peer, Long flowModelId, Long nodeIdRef) {
        if (task == null || task.getId() == null || peer == null || nodeIdRef == null) return null;
        List<TaskStep> steps = runtime.steps(task.getId());
        if (steps == null) return null;
        int peerDepth = peer.getStepDepth() == null ? 0 : peer.getStepDepth();
        for (TaskStep candidate : steps) {
            if (!Objects.equals(nodeIdRef, candidate.getNodeIdRef())) continue;
            if (!Objects.equals(peer.getParentStepId(), candidate.getParentStepId())) continue;
            int depth = candidate.getStepDepth() == null ? 0 : candidate.getStepDepth();
            if (depth != peerDepth) continue;
            FlowNode candidateNode = flowNodeService.getById(candidate.getFlowNodeId());
            if (candidateNode == null || !Objects.equals(flowModelId, candidateNode.getFlowModelId())) continue;
            return candidate;
        }
        return null;
    }

    private FlowNode nodeByRef(Long flowModelId, long nodeIdRef) {
        return workflowService.nodes(flowModelId).stream()
                .filter(node -> node.getNodeIdRef() == nodeIdRef)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("流程节点不存在: " + nodeIdRef));
    }

    private static ObservableKey nodeVariableKey(Task task, Long flowModelId, String nodeName, String variableName) {
        Long taskId = task == null ? null : task.getId();
        return new ObservableKey(ObservableObjectType.NODE_INTERNAL_VARIABLE,
                null, flowModelId, taskId, null, nodeName, null, variableName);
    }

    private JsonNode namedOrNull(JsonNode definitions, String name) {
        if (name == null || name.isBlank()) return null;
        for (JsonNode definition : iterable(definitions)) {
            if (name.equals(definition.path("name").asText())) return definition;
        }
        return null;
    }

    private JsonNode requiredNamed(JsonNode definitions, String name, String label) {
        JsonNode found = namedOrNull(definitions, name);
        if (found == null) throw new IllegalStateException(label + "不存在: " + name);
        return found;
    }

    private static Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }
}
