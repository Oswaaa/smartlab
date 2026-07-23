package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.schema.SchemaMetadataService;
import com.smartlab.global.util.JsonSchemaValidationService;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowDefinitionCompiler {

    private final ProtocolDictionaryService protocolDictionaryService;
    private final SchemaMetadataService schemaMetadataService;
    private final JsonSchemaValidationService schemaValidationService;

    public WorkflowDefinitionCompiler(ProtocolDictionaryService protocolDictionaryService,
                                      SchemaMetadataService schemaMetadataService) {
        this(protocolDictionaryService, schemaMetadataService, new JsonSchemaValidationService());
    }

    @Autowired
    public WorkflowDefinitionCompiler(ProtocolDictionaryService protocolDictionaryService,
                                      SchemaMetadataService schemaMetadataService,
                                      JsonSchemaValidationService schemaValidationService) {
        this.protocolDictionaryService = protocolDictionaryService;
        this.schemaMetadataService = schemaMetadataService;
        this.schemaValidationService = schemaValidationService;
    }

    public CompiledWorkflow compile(WorkflowSaveRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("流程名称不能为空");
        }
        JsonNode nodes = request.getNodesDef();
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            throw new IllegalArgumentException("流程必须包含节点定义");
        }
        Set<String> nodeTypes = Set.copyOf(schemaMetadataService.workflowNodeTypes());
        Set<String> functionTypes = Set.copyOf(schemaMetadataService.workflowFunctionTypes());

        Map<Long, JsonNode> nodeIndex = new LinkedHashMap<>();
        long startNodeIdRef = -1;
        long endNodeIdRef = -1;
        for (JsonNode node : nodes) {
            long ref = requiredPositiveLong(node, "nodeIdRef", "节点缺少有效 nodeIdRef");
            if (nodeIndex.put(ref, node) != null) {
                throw new IllegalArgumentException("流程内 nodeIdRef 重复: " + ref);
            }
            String nodeType = node.path("nodeType").asText("");
            if (!nodeTypes.contains(nodeType)) {
                throw new IllegalArgumentException("不支持的节点类型: " + nodeType);
            }
            if ("FUNCTIONAL_NODE".equals(nodeType)) {
                String functionType = node.path("capability").path("functionType").asText("");
                if (!functionTypes.contains(functionType)) {
                    throw new IllegalArgumentException("不支持的功能节点类型: " + functionType);
                }
                if ("START".equals(functionType)) {
                    if (startNodeIdRef > 0) throw new IllegalArgumentException("流程只能有一个 START 节点");
                    startNodeIdRef = ref;
                }
                if ("END".equals(functionType)) {
                    if (endNodeIdRef > 0) throw new IllegalArgumentException("流程只能有一个 END 节点");
                    endNodeIdRef = ref;
                }
            }
            validateActions(nodeType, node.path("actions"), ref);
            validateNodeSpecificFields(nodeType, node, ref);
        }
        if (startNodeIdRef < 0 || endNodeIdRef < 0) {
            throw new IllegalArgumentException("流程必须且只能包含一个 START 和一个 END 节点");
        }

        Map<Long, List<Connection>> outgoing = new LinkedHashMap<>();
        Map<Long, List<Connection>> incoming = new LinkedHashMap<>();
        nodeIndex.keySet().forEach(ref -> {
            outgoing.put(ref, new ArrayList<>());
            incoming.put(ref, new ArrayList<>());
        });
        JsonNode connections = request.getInterfaceConnections();
        if (connections == null || !connections.isArray()) {
            throw new IllegalArgumentException("interfaceConnections 必须是数组");
        }
        for (JsonNode connectionNode : connections) {
            JsonNode source = connectionNode.path("source");
            JsonNode target = connectionNode.path("target");
            long sourceRef = requiredPositiveLong(source, "nodeIdRef", "连接 source 缺少 nodeIdRef");
            long targetRef = requiredPositiveLong(target, "nodeIdRef", "连接 target 缺少 nodeIdRef");
            if (!nodeIndex.containsKey(sourceRef) || !nodeIndex.containsKey(targetRef)) {
                throw new IllegalArgumentException("连接引用了不存在的节点: " + sourceRef + " -> " + targetRef);
            }
            if (sourceRef == targetRef) throw new IllegalArgumentException("节点不能连接自身: " + sourceRef);
            String sourceInterface = requiredText(source, "interfaceName", "连接 source 缺少 interfaceName");
            String targetInterface = requiredText(target, "interfaceName", "连接 target 缺少 interfaceName");
            validateEndpoint(nodeIndex.get(sourceRef), "interfaces", sourceInterface, "OUT", "控制流源接口");
            validateEndpoint(nodeIndex.get(targetRef), "interfaces", targetInterface, "IN", "控制流目标接口");
            Connection connection = new Connection(sourceRef, sourceInterface, targetRef, targetInterface);
            outgoing.get(sourceRef).add(connection);
            incoming.get(targetRef).add(connection);
        }

        validatePortConnections(request.getPortConnections(), nodeIndex);
        validateTopology(nodeIndex, outgoing, incoming, startNodeIdRef, endNodeIdRef);
        validateAcyclic(nodeIndex.keySet(), outgoing);
        return new CompiledWorkflow(Map.copyOf(nodeIndex), immutable(outgoing), immutable(incoming),
                startNodeIdRef, endNodeIdRef);
    }

    private void validateActions(String nodeType, JsonNode actions, long ref) {
        if (!actions.isArray()) {
            throw new IllegalArgumentException("节点 actions 必须是数组: " + ref);
        }
        Set<String> supported = Set.copyOf(schemaMetadataService.workflowActionNames());
        int waitCount = 0;
        int emitCount = 0;
        int index = 0;
        for (JsonNode action : actions) {
            String actionName = requiredText(action, "actionName", "节点动作缺少 actionName: " + ref);
            if (!supported.contains(actionName)) {
                throw new IllegalArgumentException("工作流动作没有执行器: " + actionName);
            }
            JsonNode payload = action.get("payload");
            if (payload == null || !payload.isObject())
                throw new IllegalArgumentException(actionName + " 动作缺少 payload: " + ref);
            schemaValidationService.validateDefinition(payload, "workflow-model.json",
                    schemaMetadataService.workflowActionPayloadDefinition(actionName));
            validateValueSources(actionName, payload);
            if ("WAIT".equals(actionName)) {
                waitCount++;
                if (index != 0) throw new IllegalArgumentException("WAIT 必须是节点动作链的第一个动作: " + ref);
            }
            if ("EMIT_SIGNAL".equals(actionName)) {
                emitCount++;
                if (!"DEVICE_CAPABILITY_NODE".equals(nodeType))
                    throw new IllegalArgumentException("EMIT_SIGNAL 只能用于设备能力节点: " + ref);
                if (index != actions.size() - 1)
                    throw new IllegalArgumentException("EMIT_SIGNAL 必须是节点动作链的最后一个动作: " + ref);
            }
            index++;
        }
        if (waitCount > 1) throw new IllegalArgumentException("节点最多只能配置一个 WAIT: " + ref);
        if (emitCount > 1) throw new IllegalArgumentException("节点最多只能配置一个 EMIT_SIGNAL: " + ref);
        if ("DEVICE_CAPABILITY_NODE".equals(nodeType) && emitCount != 1)
            throw new IllegalArgumentException("设备能力节点必须且只能配置一个 EMIT_SIGNAL: " + ref);
    }

    private void validateValueSources(String actionName, JsonNode payload) {
        List<JsonNode> sources = new ArrayList<>();
        if ("ASSIGN".equals(actionName)) sources.add(payload.path("source"));
        if ("CALCULATE".equals(actionName)) payload.path("operands").forEach(sources::add);
        for (JsonNode source : sources) {
            String kind = source.path("kind").asText("");
            if ("LITERAL".equals(kind) && !source.has("value"))
                throw new IllegalArgumentException(actionName + " 的 LITERAL 来源缺少 value");
            if ("VARIABLE".equals(kind) && source.path("path").asText("").isBlank())
                throw new IllegalArgumentException(actionName + " 的 VARIABLE 来源缺少 path");
        }
    }

    private void validateNodeSpecificFields(String nodeType, JsonNode node, long ref) {
        if ("DEVICE_CAPABILITY_NODE".equals(nodeType)) {
            JsonNode capability = node.path("capability");
            requiredPositiveLong(capability, "deviceModelRef", "设备节点缺少 deviceModelRef: " + ref);
            requiredText(capability, "capabilityRef", "设备节点缺少 capabilityRef: " + ref);
            JsonNode action = findAction(node.path("actions"), "EMIT_SIGNAL");
            if (action == null) throw new IllegalArgumentException("设备节点缺少 EMIT_SIGNAL 动作: " + ref);
            JsonNode payload = action.path("payload");
            if (!"WORKFLOW".equals(requiredText(payload, "interfaceType", "EMIT_SIGNAL 缺少 interfaceType")))
                throw new IllegalArgumentException("设备节点 EMIT_SIGNAL 必须投递到 WORKFLOW 类型接口");
            String signal = requiredText(payload, "signalName", "EMIT_SIGNAL 缺少 signalName");
            if (!protocolDictionaryService.enumValues("WorkflowControlSignal").contains(signal))
                throw new IllegalArgumentException("不支持的工作流控制信号: " + signal);
        } else if ("SUB_FLOW_NODE".equals(nodeType)) {
            requiredPositiveLong(node, "subFlowModelId", "子流程节点缺少 subFlowModelId: " + ref);
        }
    }

    private void validateTopology(Map<Long, JsonNode> nodes,
                                  Map<Long, List<Connection>> outgoing,
                                  Map<Long, List<Connection>> incoming,
                                  long startRef, long endRef) {
        for (Map.Entry<Long, JsonNode> entry : nodes.entrySet()) {
            long ref = entry.getKey();
            JsonNode node = entry.getValue();
            if (ref == startRef && !incoming.get(ref).isEmpty()) {
                throw new IllegalArgumentException("START 节点不能有入边");
            }
            if (ref == endRef && !outgoing.get(ref).isEmpty()) {
                throw new IllegalArgumentException("END 节点不能有出边");
            }
            if (ref != startRef && incoming.get(ref).isEmpty()) {
                throw new IllegalArgumentException("非 START 节点必须有入边: " + ref);
            }
            if (ref != endRef && outgoing.get(ref).isEmpty()) {
                throw new IllegalArgumentException("非 END 节点必须有出边: " + ref);
            }
            String functionType = node.path("capability").path("functionType").asText("");
            if ("BRANCH".equals(functionType)) {
                JsonNode branches = node.path("capability").path("branches");
                if (!branches.isArray() || branches.size() < 2 || outgoing.get(ref).size() < 2) {
                    throw new IllegalArgumentException("BRANCH 节点至少需要两个结构化出口: " + ref);
                }
                Set<String> configured = new HashSet<>();
                int defaults = 0;
                for (JsonNode branch : branches) {
                    configured.add(requiredText(branch, "interfaceName", "分支缺少 interfaceName"));
                    if (branch.path("isDefault").asBoolean(false)) defaults++;
                    else {
                        JsonNode condition = branch.path("condition");
                        if (!condition.isObject()) throw new IllegalArgumentException("非默认分支必须有结构化 condition");
                        requiredText(condition, "subject", "分支 condition 缺少 subject");
                        String operator = requiredText(condition, "operator", "分支 condition 缺少 operator");
                        if (!protocolDictionaryService.enumValues("ConstraintOperator").contains(operator))
                            throw new IllegalArgumentException("分支 condition 使用了未定义操作符: " + operator);
                        JsonNode threshold = condition.get("threshold");
                        if (threshold == null || threshold.isNull())
                            throw new IllegalArgumentException("分支 condition 缺少 threshold");
                        if ("BETWEEN".equals(operator) && (!threshold.isArray() || threshold.size() != 2))
                            throw new IllegalArgumentException("BETWEEN threshold 必须是二元素数组");
                        if ("IN".equals(operator) && (!threshold.isArray() || threshold.isEmpty()))
                            throw new IllegalArgumentException("IN threshold 必须是非空数组");
                    }
                }
                if (defaults > 1) throw new IllegalArgumentException("BRANCH 最多只能有一个默认出口");
                Set<String> connected = new HashSet<>();
                for (Connection connection : outgoing.get(ref)) {
                    if (!configured.contains(connection.sourceInterface()))
                        throw new IllegalArgumentException("分支连接未声明条件: " + connection.sourceInterface());
                    connected.add(connection.sourceInterface());
                }
                if (!connected.equals(configured))
                    throw new IllegalArgumentException("BRANCH 的每个结构化出口都必须且只能连接一次: " + ref);
            }
            if ("AGGREGATE".equals(functionType) && incoming.get(ref).size() < 2) {
                throw new IllegalArgumentException("AGGREGATE 节点至少需要两条入边: " + ref);
            }
        }
    }

    private JsonNode findAction(JsonNode actions, String actionName) {
        if (actions != null && actions.isArray())
            for (JsonNode action : actions)
                if (actionName.equals(action.path("actionName").asText())) return action;
        return null;
    }

    private void validatePortConnections(JsonNode connections, Map<Long, JsonNode> nodeIndex) {
        if (connections == null || !connections.isArray())
            throw new IllegalArgumentException("portConnections 必须是数组");
        for (JsonNode connection : connections) {
            JsonNode source = connection.path("source");
            JsonNode target = connection.path("target");
            long sourceRef = requiredPositiveLong(source, "nodeIdRef", "端口连接 source 缺少 nodeIdRef");
            long targetRef = requiredPositiveLong(target, "nodeIdRef", "端口连接 target 缺少 nodeIdRef");
            if (!nodeIndex.containsKey(sourceRef) || !nodeIndex.containsKey(targetRef))
                throw new IllegalArgumentException("端口连接引用了不存在的节点: " + sourceRef + " -> " + targetRef);
            String sourcePort = requiredText(source, "portName", "端口连接 source 缺少 portName");
            String targetPort = requiredText(target, "portName", "端口连接 target 缺少 portName");
            validateEndpoint(nodeIndex.get(sourceRef), "ports", sourcePort, "OUT", "数据源端口");
            validateEndpoint(nodeIndex.get(targetRef), "ports", targetPort, "IN", "数据目标端口");
        }
    }

    private void validateEndpoint(JsonNode node, String collectionName, String endpointName,
                                  String expectedDirection, String label) {
        JsonNode endpoints = node.path(collectionName);
        if (!endpoints.isArray()) throw new IllegalArgumentException(label + "集合必须是数组");
        for (JsonNode endpoint : endpoints) {
            if (endpointName.equals(endpoint.path("name").asText())) {
                if (!expectedDirection.equals(endpoint.path("direction").asText()))
                    throw new IllegalArgumentException(label + "方向必须是 " + expectedDirection + ": " + endpointName);
                return;
            }
        }
        throw new IllegalArgumentException(label + "不存在: " + endpointName);
    }

    private void validateAcyclic(Set<Long> nodes, Map<Long, List<Connection>> outgoing) {
        Map<Long, Integer> indegree = new HashMap<>();
        nodes.forEach(ref -> indegree.put(ref, 0));
        outgoing.values().forEach(list -> list.forEach(c -> indegree.merge(c.targetNodeIdRef(), 1, Integer::sum)));
        ArrayDeque<Long> queue = new ArrayDeque<>();
        indegree.forEach((ref, degree) -> { if (degree == 0) queue.add(ref); });
        int visited = 0;
        while (!queue.isEmpty()) {
            long ref = queue.remove();
            visited++;
            for (Connection c : outgoing.get(ref)) {
                int next = indegree.merge(c.targetNodeIdRef(), -1, Integer::sum);
                if (next == 0) queue.add(c.targetNodeIdRef());
            }
        }
        if (visited != nodes.size()) throw new IllegalArgumentException("当前工作流不支持环形连接");
    }

    private Map<Long, List<Connection>> immutable(Map<Long, List<Connection>> source) {
        Map<Long, List<Connection>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    private long requiredPositiveLong(JsonNode node, String field, String message) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) throw new IllegalArgumentException(message);
        return value.asLong();
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    public record Connection(long sourceNodeIdRef, String sourceInterface,
                             long targetNodeIdRef, String targetInterface) {}

    public record CompiledWorkflow(Map<Long, JsonNode> nodes,
                                   Map<Long, List<Connection>> outgoingConnections,
                                   Map<Long, List<Connection>> incomingConnections,
                                   long startNodeIdRef, long endNodeIdRef) {
        public List<Connection> outgoing(long nodeIdRef) {
            return outgoingConnections.getOrDefault(nodeIdRef, List.of());
        }
        public List<Connection> incoming(long nodeIdRef) {
            return incomingConnections.getOrDefault(nodeIdRef, List.of());
        }
    }
}
