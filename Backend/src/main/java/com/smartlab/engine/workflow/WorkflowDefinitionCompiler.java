package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.ConstraintOperator;
import com.smartlab.global.contract.DataType;
import com.smartlab.global.contract.WorkflowNodeActionType;
import com.smartlab.global.contract.WorkflowNodeFunctionType;
import com.smartlab.global.contract.WorkflowNodeSystemContract;
import com.smartlab.global.contract.WorkflowNodeType;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 定稿工作流模型编译器：运行时直接校验固定节点契约，不解释JSONSchema。 */
@Component
public class WorkflowDefinitionCompiler {

    private static final Set<String> NODE_TYPES = enumNames(WorkflowNodeType.values());
    private static final Set<String> FUNCTION_TYPES = enumNames(WorkflowNodeFunctionType.values());
    private static final Set<String> ACTION_TYPES = enumNames(WorkflowNodeActionType.values());
    private static final Set<String> DATA_TYPES = enumNames(DataType.values());

    public CompiledWorkflow compile(WorkflowSaveRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("flowModelName不能为空");
        }
        if (request.getNodesDef() == null || !request.getNodesDef().isArray() || request.getNodesDef().isEmpty()) {
            throw new IllegalArgumentException("nodes必须是非空数组");
        }
        Map<String, Long> refsByName = new LinkedHashMap<>();
        Map<String, NodeIndex> indexesByName = new LinkedHashMap<>();
        Map<Long, JsonNode> nodes = new LinkedHashMap<>();
        long startRef = -1;
        long endRef = -1;
        long nextRef = 1;
        int nodePosition = 0;
        for (JsonNode source : request.getNodesDef()) {
            if (!source.isObject()) throw new IllegalArgumentException("nodes[" + nodePosition + "]: 节点必须是对象");
            ObjectNode node = source.deepCopy();
            node.put("nodeIdRef", nextRef);
            NodeIndex index = validateAndIndexNode(node, "nodes[" + nodePosition + "]");
            if (refsByName.put(index.nodeName(), nextRef) != null) {
                throw nodeError(index.nodeName(), "name", "节点名称重复");
            }
            indexesByName.put(index.nodeName(), index);
            if (WorkflowNodeType.FUNC_NODE.name().equals(index.nodeType())) {
                String functionType = node.path("functionType").asText();
                if (WorkflowNodeFunctionType.START.name().equals(functionType)) startRef = unique(startRef, nextRef, "START");
                if (WorkflowNodeFunctionType.END.name().equals(functionType)) endRef = unique(endRef, nextRef, "END");
            }
            nodes.put(nextRef, node);
            nextRef++;
            nodePosition++;
        }
        if (startRef < 0 || endRef < 0) {
            throw new IllegalArgumentException("工作流必须且只能包含一个START和一个END节点");
        }

        Map<Long, List<Connection>> outgoing = indexedConnections(nodes.keySet());
        Map<Long, List<Connection>> incoming = indexedConnections(nodes.keySet());
        validateInterfaceConnections(request.getInterfaceConnections(), refsByName, indexesByName, nodes, outgoing, incoming);
        validatePortConnections(request.getPortConnections(), indexesByName);
        validateTopology(nodes, outgoing, incoming, startRef, endRef);
        validateAcyclic(nodes.keySet(), outgoing);
        return new CompiledWorkflow(Map.copyOf(nodes), immutable(outgoing), immutable(incoming), Map.copyOf(refsByName), startRef, endRef);
    }

    private NodeIndex validateAndIndexNode(JsonNode node, String path) {
        String name = requiredText(node, "name", path + ".name: 不能为空");
        String type = requiredText(node, "nodeType", nodePath(name, "nodeType") + ": 不能为空");
        if (!NODE_TYPES.contains(type)) throw nodeError(name, "nodeType", "不支持的节点类型: " + type);

        JsonNode variablesNode = requireArray(node, "internalVariables", name);
        JsonNode interfacesNode = requireArray(node, "interfaces", name);
        JsonNode portsNode = requireArray(node, "ports", name);
        JsonNode actionsNode = requireArray(node, "actions", name);
        Map<String, JsonNode> variables = indexNamedItems(variablesNode, "name", name, "internalVariables");
        Map<String, JsonNode> interfaces = indexNamedItems(interfacesNode, "name", name, "interfaces");
        Map<String, JsonNode> ports = indexNamedItems(portsNode, "name", name, "ports");
        Map<String, JsonNode> actions = indexNamedItems(actionsNode, "actionName", name, "actions");

        int position = 0;
        for (JsonNode variable : variablesNode) {
            String dataTypePath = nodePath(name, "internalVariables[" + position + "].dataType");
            requireDataType(requiredText(variable, "dataType", dataTypePath + ": 不能为空"), dataTypePath);
            if (variable.has("attributesMapping") && !variable.path("attributesMapping").isTextual()) {
                throw nodeError(name, "internalVariables[" + position + "].attributesMapping", "必须是字符串");
            }
            position++;
        }

        position = 0;
        for (JsonNode item : interfacesNode) {
            String itemPath = "interfaces[" + position + "]";
            String direction = requiredText(item, "direction", nodePath(name, itemPath + ".direction") + ": 不能为空");
            String interfaceType = requiredText(item, "interfaceType", nodePath(name, itemPath + ".interfaceType") + ": 不能为空");
            if (!Set.of("IN", "OUT").contains(direction)) throw nodeError(name, itemPath + ".direction", "只允许IN或OUT");
            if (!Set.of("WORKFLOW", "STATE").contains(interfaceType)) throw nodeError(name, itemPath + ".interfaceType", "只允许WORKFLOW或STATE");
            JsonNode allowedSignals = item.path("allowedSignals");
            if (!allowedSignals.isArray()) throw nodeError(name, itemPath + ".allowedSignals", "必须是数组");
            for (JsonNode signal : allowedSignals) {
                if (!signal.isTextual() || signal.asText().isBlank()) throw nodeError(name, itemPath + ".allowedSignals", "只能包含非空字符串");
            }
            if (item.has("bindingTriggers") && !item.path("bindingTriggers").isArray()) {
                throw nodeError(name, itemPath + ".bindingTriggers", "必须是数组");
            }
            position++;
        }

        position = 0;
        for (JsonNode port : portsNode) {
            String itemPath = "ports[" + position + "]";
            String direction = requiredText(port, "direction", nodePath(name, itemPath + ".direction") + ": 不能为空");
            if (!Set.of("IN", "OUT").contains(direction)) throw nodeError(name, itemPath + ".direction", "只允许IN或OUT");
            String variableName = requiredText(port, "internalVariableName", nodePath(name, itemPath + ".internalVariableName") + ": 不能为空");
            if (!variables.containsKey(variableName)) throw nodeError(name, itemPath + ".internalVariableName", "引用不存在的内部变量: " + variableName);
            position++;
        }

        NodeIndex index = new NodeIndex(name, type, variables, interfaces, ports, actions);
        validateActions(index, node, path);
        validateTriggers(index, node, path);
        validateSystemSkeleton(index, node, path);
        return index;
    }

    private void validateSystemSkeleton(NodeIndex index, JsonNode node, String path) {
        String name = index.nodeName();
        String functionType = null;
        if (WorkflowNodeType.DEV_NODE.name().equals(index.nodeType())) {
            requirePositiveLong(node, "deviceModelId", name);
            requiredText(node.path("capability"), "capabilityName",
                    nodePath(name, "capability.capabilityName") + ": 不能为空");
        } else if (WorkflowNodeType.SUBFLOW_NODE.name().equals(index.nodeType())) {
            requirePositiveLong(node, "subFlowModelId", name);
        } else {
            functionType = requiredText(node, "functionType", nodePath(name, "functionType") + ": 不能为空");
            if (!FUNCTION_TYPES.contains(functionType)) {
                throw nodeError(name, "functionType", "不支持的功能节点类型: " + functionType);
            }
            if (WorkflowNodeFunctionType.BRANCH.name().equals(functionType)) {
                requiredText(node, "expression", nodePath(name, "expression") + ": 不能为空");
            }
        }
        ObjectNode expected = WorkflowNodeSystemContract.template(index.nodeType(), functionType);
        validateTemplateLifecycle(node.path("lifecycle"), expected.path("lifecycle"), name);
        validateSystemInterfaces(index, node.path("interfaces"), expected.path("interfaces"));
        validateSystemActions(index, node.path("actions"), expected.path("actions"));
    }
    private void validateActions(NodeIndex index, JsonNode node, String path) {
        int position = 0;
        for (JsonNode action : node.path("actions")) {
            String actionPath = "actions[" + position + "]";
            String type = requiredText(action, "actionType", nodePath(index.nodeName(), actionPath + ".actionType") + ": 不能为空");
            if (!ACTION_TYPES.contains(type)) throw nodeError(index.nodeName(), actionPath + ".actionType", "只允许EMIT或UPDATE: " + type);
            if (WorkflowNodeActionType.EMIT.name().equals(type)) {
                String targetName = requiredText(action, "targetInterfaceName", nodePath(index.nodeName(), actionPath + ".targetInterfaceName") + ": 不能为空");
                JsonNode target = index.interfaces().get(targetName);
                if (target == null) throw nodeError(index.nodeName(), actionPath + ".targetInterfaceName", "EMIT目标接口不存在: " + targetName);
                if (!"OUT".equals(target.path("direction").asText())) throw nodeError(index.nodeName(), actionPath + ".targetInterfaceName", "EMIT目标必须是OUT接口: " + targetName);
                String signal = requiredText(action, "signalName", nodePath(index.nodeName(), actionPath + ".signalName") + ": 不能为空");
                if (!contains(target.path("allowedSignals"), signal)) throw nodeError(index.nodeName(), actionPath + ".signalName", "EMIT信号不在接口allowedSignals中: " + signal);
            } else {
                String variable = requiredText(action, "internalVariableName", nodePath(index.nodeName(), actionPath + ".internalVariableName") + ": 不能为空");
                if (!index.variables().containsKey(variable)) throw nodeError(index.nodeName(), actionPath + ".internalVariableName", "UPDATE引用不存在的内部变量: " + variable);
                requiredText(action, "valueExpression", nodePath(index.nodeName(), actionPath + ".valueExpression") + ": 不能为空");
            }
            position++;
        }
    }

    private void validateTriggers(NodeIndex index, JsonNode node, String path) {
        int interfacePosition = 0;
        for (JsonNode item : node.path("interfaces")) {
            JsonNode triggers = item.path("bindingTriggers");
            if (triggers.isArray() && !triggers.isEmpty() && !"IN".equals(item.path("direction").asText())) {
                throw nodeError(index.nodeName(), "interfaces[" + interfacePosition + "].bindingTriggers", "OUT接口不能声明bindingTriggers");
            }
            int triggerPosition = 0;
            for (JsonNode trigger : iterable(triggers)) {
                String triggerPath = "interfaces[" + interfacePosition + "].bindingTriggers[" + triggerPosition + "]";
                String actionName = requiredText(trigger, "action", nodePath(index.nodeName(), triggerPath + ".action") + ": 不能为空");
                if (!index.actions().containsKey(actionName)) throw nodeError(index.nodeName(), triggerPath + ".action", "引用不存在的动作: " + actionName);
                JsonNode condition = trigger.path("condition");
                if (!condition.isObject()) throw nodeError(index.nodeName(), triggerPath + ".condition", "必须是对象");
                String object = requiredText(condition, "object", nodePath(index.nodeName(), triggerPath + ".condition.object") + ": 不能为空");
                String operator = requiredText(condition, "operator", nodePath(index.nodeName(), triggerPath + ".condition.operator") + ": 不能为空");
                if (!ConstraintOperator.supports(operator)) throw nodeError(index.nodeName(), triggerPath + ".condition.operator", "不支持的运算符: " + operator);
                if (!condition.has("threshold") || condition.path("threshold").isNull()) throw nodeError(index.nodeName(), triggerPath + ".condition.threshold", "不能为空");
                JsonNode variable = index.variables().get(object);
                if (variable != null && !matchesDataType(condition.path("threshold"), variable.path("dataType").asText())) {
                    throw nodeError(index.nodeName(), triggerPath + ".condition.threshold",
                            "与内部变量" + object + "的数据类型不一致");
                }
                triggerPosition++;
            }
            interfacePosition++;
        }
    }

    private void validateInterfaceConnections(JsonNode connections, Map<String, Long> refs,
                                              Map<String, NodeIndex> indexes, Map<Long, JsonNode> nodes,
                                              Map<Long, List<Connection>> outgoing, Map<Long, List<Connection>> incoming) {
        if (connections == null || !connections.isArray()) throw new IllegalArgumentException("interfaceConnections必须是数组");
        Map<String, Long> nodeToDevice = new HashMap<>();
        Map<String, Long> deviceToNode = new HashMap<>();
        int position = 0;
        for (JsonNode connection : connections) {
            String path = "interfaceConnections[" + position + "]";
            String type = requiredText(connection, "connectionType", path + ".connectionType: 不能为空");
            JsonNode source = connection.path("source");
            JsonNode target = connection.path("target");
            if ("NODE_TO_NODE".equals(type)) {
                NodeIndex sourceNode = referencedNode(source, indexes, path + ".source");
                NodeIndex targetNode = referencedNode(target, indexes, path + ".target");
                JsonNode sourceInterface = referencedInterface(source, sourceNode, "OUT", "WORKFLOW", path + ".source");
                JsonNode targetInterface = referencedInterface(target, targetNode, "IN", "WORKFLOW", path + ".target");
                long sourceRef = refs.get(sourceNode.nodeName());
                long targetRef = refs.get(targetNode.nodeName());
                Connection item = new Connection(sourceRef, sourceInterface.path("name").asText(), targetRef, targetInterface.path("name").asText());
                outgoing.get(sourceRef).add(item);
                incoming.get(targetRef).add(item);
            } else if ("NODE_TO_DEVICE".equals(type)) {
                NodeIndex sourceNode = referencedNode(source, indexes, path + ".source");
                if (!WorkflowNodeType.DEV_NODE.name().equals(sourceNode.nodeType())) throw new IllegalArgumentException(path + ".source: NODE_TO_DEVICE只能从DEV_NODE发出");
                referencedInterface(source, sourceNode, "OUT", "STATE", path + ".source");
                long modelId = positiveLong(target, "deviceModelId", path + ".target.deviceModelId");
                requiredText(target, "interfaceName", path + ".target.interfaceName: 不能为空");
                if (nodeToDevice.putIfAbsent(sourceNode.nodeName(), modelId) != null) throw new IllegalArgumentException(path + ": DEV_NODE只能有一个NODE_TO_DEVICE连接");
            } else if ("DEVICE_TO_NODE".equals(type)) {
                NodeIndex targetNode = referencedNode(target, indexes, path + ".target");
                if (!WorkflowNodeType.DEV_NODE.name().equals(targetNode.nodeType())) throw new IllegalArgumentException(path + ".target: DEVICE_TO_NODE只能连接到DEV_NODE");
                referencedInterface(target, targetNode, "IN", "STATE", path + ".target");
                long modelId = positiveLong(source, "deviceModelId", path + ".source.deviceModelId");
                requiredText(source, "interfaceName", path + ".source.interfaceName: 不能为空");
                if (deviceToNode.putIfAbsent(targetNode.nodeName(), modelId) != null) throw new IllegalArgumentException(path + ": DEV_NODE只能有一个DEVICE_TO_NODE连接");
            } else throw new IllegalArgumentException(path + ".connectionType: 不支持的接口连接类型: " + type);
            position++;
        }
        for (Map.Entry<String, NodeIndex> entry : indexes.entrySet()) {
            NodeIndex index = entry.getValue();
            if (!WorkflowNodeType.DEV_NODE.name().equals(index.nodeType())) continue;
            Long outbound = nodeToDevice.get(index.nodeName());
            Long inbound = deviceToNode.get(index.nodeName());
            if (outbound == null || inbound == null) throw nodeError(index.nodeName(), "interfaceConnections", "DEV_NODE必须同时声明NODE_TO_DEVICE和DEVICE_TO_NODE连接");
            if (!outbound.equals(inbound)) throw nodeError(index.nodeName(), "interfaceConnections", "设备输入输出连接必须引用同一设备模型");
            JsonNode definition = nodes.get(refs.get(index.nodeName()));
            if (outbound.longValue() != definition.path("deviceModelId").asLong()) throw nodeError(index.nodeName(), "interfaceConnections", "连接deviceModelId必须与节点deviceModelId一致");
        }
    }

    private void validatePortConnections(JsonNode connections, Map<String, NodeIndex> nodes) {
        if (connections == null || !connections.isArray()) throw new IllegalArgumentException("portConnections必须是数组");
        int position = 0;
        for (JsonNode connection : connections) {
            String path = "portConnections[" + position + "]";
            JsonNode sourceEndpoint = connection.path("source");
            JsonNode targetEndpoint = connection.path("target");
            NodeIndex sourceNode = referencedNode(sourceEndpoint, nodes, path + ".source");
            NodeIndex targetNode = referencedNode(targetEndpoint, nodes, path + ".target");
            JsonNode sourcePort = referencedPort(sourceEndpoint, sourceNode, "OUT", path + ".source");
            JsonNode targetPort = referencedPort(targetEndpoint, targetNode, "IN", path + ".target");
            JsonNode sourceVariable = sourceNode.variables().get(sourcePort.path("internalVariableName").asText());
            JsonNode targetVariable = targetNode.variables().get(targetPort.path("internalVariableName").asText());
            String sourceType = sourceVariable.path("dataType").asText();
            String targetType = targetVariable.path("dataType").asText();
            if (!sourceType.equals(targetType)) throw new IllegalArgumentException(path + ": 源目标内部变量数据类型不一致: " + sourceType + " -> " + targetType);
            position++;
        }
    }

    private void validateTopology(Map<Long, JsonNode> nodes, Map<Long, List<Connection>> outgoing,
                                  Map<Long, List<Connection>> incoming, long startRef, long endRef) {
        for (Map.Entry<Long, JsonNode> entry : nodes.entrySet()) {
            long ref = entry.getKey();
            String name = entry.getValue().path("name").asText();
            String function = entry.getValue().path("functionType").asText();
            if (ref == startRef && !incoming.get(ref).isEmpty()) throw nodeError(name, "interfaceConnections", "START节点不能有NODE_TO_NODE输入");
            if (ref == endRef && !outgoing.get(ref).isEmpty()) throw nodeError(name, "interfaceConnections", "END节点不能有NODE_TO_NODE输出");
            if (ref != startRef && incoming.get(ref).isEmpty()) throw nodeError(name, "interfaceConnections", "非START节点必须有NODE_TO_NODE输入");
            if (ref != endRef && outgoing.get(ref).isEmpty()) throw nodeError(name, "interfaceConnections", "非END节点必须有NODE_TO_NODE输出");
            if (WorkflowNodeFunctionType.AGGREGATE.name().equals(function) && incoming.get(ref).size() < 2) throw nodeError(name, "interfaceConnections", "AGGREGATE节点至少需要两个输入");
        }
    }

    private long unique(long current, long next, String label) {
        if (current >= 0) throw new IllegalArgumentException("工作流只能有一个" + label + "节点");
        return next;
    }

    private Map<Long, List<Connection>> indexedConnections(Set<Long> refs) {
        Map<Long, List<Connection>> result = new LinkedHashMap<>();
        refs.forEach(ref -> result.put(ref, new ArrayList<>()));
        return result;
    }

    private void validateAcyclic(Set<Long> nodes, Map<Long, List<Connection>> outgoing) {
        Map<Long, Integer> degree = new HashMap<>();
        nodes.forEach(ref -> degree.put(ref, 0));
        outgoing.values().forEach(items -> items.forEach(item -> degree.merge(item.targetNodeIdRef(), 1, Integer::sum)));
        ArrayDeque<Long> queue = new ArrayDeque<>();
        degree.forEach((ref, value) -> { if (value == 0) queue.add(ref); });
        int visited = 0;
        while (!queue.isEmpty()) {
            long ref = queue.remove();
            visited++;
            for (Connection item : outgoing.get(ref)) if (degree.merge(item.targetNodeIdRef(), -1, Integer::sum) == 0) queue.add(item.targetNodeIdRef());
        }
        if (visited != nodes.size()) throw new IllegalArgumentException("工作流不支持环形NODE_TO_NODE连接");
    }

    private void validateTemplateLifecycle(JsonNode lifecycle, JsonNode expected, String nodeName) {
        if (!lifecycle.isObject() || !matchesOptionalSystemIdentity(lifecycle, expected)
                || !expected.path("initialStateName").equals(lifecycle.path("initialStateName"))) {
            throw nodeError(nodeName, "lifecycle", "系统生命周期定义被修改");
        }
        Set<String> actualStates = uniqueTextSet(lifecycle.path("states"));
        Set<String> expectedStates = uniqueTextSet(expected.path("states"));
        if (actualStates == null || !actualStates.equals(expectedStates)
                || !sameLifecycleTransitions(lifecycle.path("transitions"), expected.path("transitions"))) {
            throw nodeError(nodeName, "lifecycle", "系统生命周期定义被修改");
        }
    }

    private boolean sameLifecycleTransitions(JsonNode actualTransitions, JsonNode expectedTransitions) {
        Map<String, JsonNode> actualByKey = lifecycleTransitions(actualTransitions);
        Map<String, JsonNode> expectedByKey = lifecycleTransitions(expectedTransitions);
        if (actualByKey == null || expectedByKey == null || !actualByKey.keySet().equals(expectedByKey.keySet())) {
            return false;
        }
        for (Map.Entry<String, JsonNode> entry : expectedByKey.entrySet()) {
            if (!matchesOptionalSystemIdentity(actualByKey.get(entry.getKey()), entry.getValue())) return false;
        }
        return true;
    }

    private Map<String, JsonNode> lifecycleTransitions(JsonNode transitions) {
        if (!transitions.isArray()) return null;
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode transition : transitions) {
            if (!transition.isObject()
                    || !transition.path("fromStateName").isTextual()
                    || transition.path("fromStateName").asText().isBlank()
                    || !transition.path("toStateName").isTextual()
                    || transition.path("toStateName").asText().isBlank()) return null;
            String key = transition.path("fromStateName").asText() + "→" + transition.path("toStateName").asText();
            if (result.putIfAbsent(key, transition) != null) return null;
        }
        return result;
    }

    private void validateSystemInterfaces(NodeIndex index, JsonNode actualInterfaces, JsonNode expectedInterfaces) {
        Map<String, JsonNode> expectedByName = namedItems(expectedInterfaces, "name");
        for (JsonNode expected : iterable(expectedInterfaces)) {
            String interfaceName = expected.path("name").asText();
            JsonNode actual = index.interfaces().get(interfaceName);
            if (!matchesSystemInterface(actual, expected)) {
                throw nodeError(index.nodeName(), "interfaces." + interfaceName, "系统接口定义被修改");
            }
            validateSystemTriggers(index.nodeName(), interfaceName,
                    actual.path("bindingTriggers"), expected.path("bindingTriggers"));
        }
        for (JsonNode actual : iterable(actualInterfaces)) {
            if (!expectedByName.containsKey(actual.path("name").asText())) {
                throw nodeError(index.nodeName(), "interfaces", "不允许新增接口");
            }
        }
    }

    private boolean matchesSystemInterface(JsonNode actual, JsonNode expected) {
        if (actual == null || !actual.isObject() || !matchesOptionalSystemIdentity(actual, expected)) return false;
        Set<String> actualSignals = uniqueTextSet(actual.path("allowedSignals"));
        Set<String> expectedSignals = uniqueTextSet(expected.path("allowedSignals"));
        return sameTextField(actual, expected, "name")
                && sameTextField(actual, expected, "direction")
                && sameTextField(actual, expected, "interfaceType")
                && actualSignals != null
                && actualSignals.equals(expectedSignals);
    }

    private void validateSystemTriggers(String nodeName, String interfaceName,
                                        JsonNode actualTriggers, JsonNode expectedTriggers) {
        List<JsonNode> actualItems = nodeList(actualTriggers);
        Set<Integer> matchedIndexes = new HashSet<>();
        for (JsonNode expected : iterable(expectedTriggers)) {
            int matchedIndex = -1;
            for (int index = 0; index < actualItems.size(); index++) {
                JsonNode actual = actualItems.get(index);
                if (!matchedIndexes.contains(index)
                        && triggerBusinessEquals(actual, expected)
                        && matchesOptionalSystemIdentity(actual, expected)) {
                    matchedIndex = index;
                    break;
                }
            }
            if (matchedIndex < 0) {
                throw nodeError(nodeName, "interfaces." + interfaceName + ".bindingTriggers",
                        "系统触发器缺失或已篡改");
            }
            matchedIndexes.add(matchedIndex);
        }
        for (int index = 0; index < actualItems.size(); index++) {
            if (!matchedIndexes.contains(index) && claimsSystemIdentity(actualItems.get(index))) {
                throw nodeError(nodeName, "interfaces." + interfaceName + ".bindingTriggers",
                        "存在未知或已篡改的系统触发器");
            }
        }
    }

    private boolean triggerBusinessEquals(JsonNode actual, JsonNode expected) {
        return actual != null && actual.isObject()
                && sameTextField(actual, expected, "action")
                && expected.path("condition").equals(actual.path("condition"));
    }

    private void validateSystemActions(NodeIndex index, JsonNode actualActions, JsonNode expectedActions) {
        Map<String, JsonNode> expectedByName = namedItems(expectedActions, "actionName");
        for (JsonNode expected : iterable(expectedActions)) {
            String actionName = expected.path("actionName").asText();
            JsonNode actual = index.actions().get(actionName);
            if (!matchesSystemAction(actual, expected)) {
                throw nodeError(index.nodeName(), "actions." + actionName, "系统动作定义被修改");
            }
        }
        for (JsonNode actual : iterable(actualActions)) {
            String actionName = actual.path("actionName").asText();
            if (expectedByName.containsKey(actionName)) continue;
            if (claimsSystemIdentity(actual)
                    || !WorkflowNodeActionType.UPDATE.name().equals(actual.path("actionType").asText())) {
                throw nodeError(index.nodeName(), "actions." + actionName, "只允许新增非系统UPDATE动作");
            }
        }
    }

    private boolean matchesSystemAction(JsonNode actual, JsonNode expected) {
        return actual != null && actual.isObject()
                && matchesOptionalSystemIdentity(actual, expected)
                && sameTextField(actual, expected, "actionName")
                && sameTextField(actual, expected, "actionType")
                && sameTextField(actual, expected, "targetInterfaceName")
                && sameTextField(actual, expected, "signalName");
    }

    private boolean matchesOptionalSystemIdentity(JsonNode actual, JsonNode expected) {
        boolean hasSystemFlag = actual.has("_system");
        boolean hasSystemKey = actual.has("_systemKey");
        if (!hasSystemFlag && !hasSystemKey) return true;
        return hasSystemFlag
                && hasSystemKey
                && actual.path("_system").isBoolean()
                && actual.path("_system").asBoolean()
                && actual.path("_systemKey").isTextual()
                && !actual.path("_systemKey").asText().isBlank()
                && expected.path("_systemKey").asText().equals(actual.path("_systemKey").asText());
    }

    private boolean claimsSystemIdentity(JsonNode item) {
        if (item.has("_systemKey")) return true;
        return item.has("_system")
                && (!item.path("_system").isBoolean() || item.path("_system").asBoolean());
    }

    private boolean sameTextField(JsonNode actual, JsonNode expected, String field) {
        return actual.path(field).isTextual()
                && expected.path(field).asText().equals(actual.path(field).asText());
    }

    private Set<String> uniqueTextSet(JsonNode values) {
        if (!values.isArray()) return null;
        Set<String> result = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank() || !result.add(value.asText())) return null;
        }
        return result;
    }

    private List<JsonNode> nodeList(JsonNode values) {
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode value : iterable(values)) result.add(value);
        return result;
    }

    private Map<String, JsonNode> namedItems(JsonNode items, String nameField) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode item : iterable(items)) result.put(item.path(nameField).asText(), item);
        return result;
    }
    private NodeIndex referencedNode(JsonNode endpoint, Map<String, NodeIndex> nodes, String path) {
        String name = requiredText(endpoint, "nodeName", path + ".nodeName: 不能为空");
        NodeIndex node = nodes.get(name);
        if (node == null) throw new IllegalArgumentException(path + ".nodeName: 引用不存在的节点: " + name);
        return node;
    }

    private JsonNode referencedInterface(JsonNode endpoint, NodeIndex node, String direction, String type, String path) {
        String name = requiredText(endpoint, "interfaceName", path + ".interfaceName: 不能为空");
        JsonNode item = node.interfaces().get(name);
        if (item == null || !direction.equals(item.path("direction").asText()) || !type.equals(item.path("interfaceType").asText())) {
            throw new IllegalArgumentException(path + ".interfaceName: 接口不存在、方向不匹配或类型不匹配: " + name);
        }
        return item;
    }

    private JsonNode referencedPort(JsonNode endpoint, NodeIndex node, String direction, String path) {
        String name = requiredText(endpoint, "portName", path + ".portName: 不能为空");
        JsonNode port = node.ports().get(name);
        if (port == null || !direction.equals(port.path("direction").asText())) {
            throw new IllegalArgumentException(path + ".portName: 端口不存在或方向不匹配: " + name);
        }
        return port;
    }

    private Map<String, JsonNode> indexNamedItems(JsonNode items, String nameField, String nodeName, String fieldPath) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        int position = 0;
        for (JsonNode item : items) {
            if (!item.isObject()) throw nodeError(nodeName, fieldPath + "[" + position + "]", "必须是对象");
            String name = requiredText(item, nameField, nodePath(nodeName, fieldPath + "[" + position + "]." + nameField) + ": 不能为空");
            if (result.putIfAbsent(name, item) != null) throw nodeError(nodeName, fieldPath + "[" + position + "]." + nameField, "名称重复: " + name);
            position++;
        }
        return Map.copyOf(result);
    }

    private JsonNode requireArray(JsonNode node, String field, String nodeName) {
        JsonNode value = node.path(field);
        if (!value.isArray()) throw nodeError(nodeName, field, "必须是数组");
        return value;
    }

    private void requirePositiveLong(JsonNode node, String field, String nodeName) {
        positiveLong(node, field, nodePath(nodeName, field));
    }

    private long positiveLong(JsonNode node, String field, String path) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException(path + ": 必须是正整数");
        }
        return value.asLong();
    }

    private void requireDataType(String dataType, String path) {
        if (!DATA_TYPES.contains(dataType)) throw new IllegalArgumentException(path + ": 不支持的数据类型: " + dataType);
    }

    private boolean matchesDataType(JsonNode value, String dataType) {
        if (value == null || value.isNull()) return false;
        return switch (DataType.valueOf(dataType)) {
            case INTEGER -> value.isIntegralNumber();
            case DOUBLE -> value.isNumber();
            case STRING -> value.isTextual();
            case BOOLEAN -> value.isBoolean();
            case JSON -> value.isObject() || value.isArray();
        };
    }

    private boolean contains(JsonNode values, String value) {
        for (JsonNode item : iterable(values)) if (value.equals(item.asText())) return true;
        return false;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private String requiredText(JsonNode node, String field, String message) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) throw new IllegalArgumentException(message);
        return value.asText().trim();
    }

    private Map<Long, List<Connection>> immutable(Map<Long, List<Connection>> source) {
        Map<Long, List<Connection>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        Set<String> names = new HashSet<>();
        for (Enum<?> value : values) names.add(value.name());
        return Set.copyOf(names);
    }

    private static IllegalArgumentException nodeError(String nodeName, String fieldPath, String reason) {
        return new IllegalArgumentException(nodePath(nodeName, fieldPath) + ": " + reason);
    }

    private static String nodePath(String nodeName, String fieldPath) {
        return "节点" + nodeName + "." + fieldPath;
    }

    private record NodeIndex(String nodeName, String nodeType, Map<String, JsonNode> variables,
                             Map<String, JsonNode> interfaces, Map<String, JsonNode> ports,
                             Map<String, JsonNode> actions) {
    }

    public record Connection(long sourceNodeIdRef, String sourceInterface, long targetNodeIdRef, String targetInterface) {
    }

    public record CompiledWorkflow(Map<Long, JsonNode> nodes, Map<Long, List<Connection>> outgoingConnections,
                                   Map<Long, List<Connection>> incomingConnections, Map<String, Long> refsByNodeName,
                                   long startNodeIdRef, long endNodeIdRef) {
        public List<Connection> outgoing(long nodeIdRef) { return outgoingConnections.getOrDefault(nodeIdRef, List.of()); }
        public List<Connection> incoming(long nodeIdRef) { return incomingConnections.getOrDefault(nodeIdRef, List.of()); }
    }
}
