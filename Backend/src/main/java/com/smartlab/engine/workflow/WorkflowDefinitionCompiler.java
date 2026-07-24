package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.ConstraintOperator;
import com.smartlab.global.contract.WorkflowNodeActionType;
import com.smartlab.global.contract.WorkflowNodeFunctionType;
import com.smartlab.global.contract.WorkflowNodeType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 定稿工作流模型编译器：运行时直接校验固定节点契约，不解释JSONSchema */
@Component
public class WorkflowDefinitionCompiler {

    public CompiledWorkflow compile(WorkflowSaveRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("flowModelName不能为空");
        }
        if (request.getNodesDef() == null || !request.getNodesDef().isArray() || request.getNodesDef().isEmpty()) {
            throw new IllegalArgumentException("nodes必须是非空数组");
        }
        Map<String, Long> refsByName = new LinkedHashMap<>();
        Map<Long, JsonNode> nodes = new LinkedHashMap<>();
        long startRef = -1;
        long endRef = -1;
        long nextRef = 1;
        for (JsonNode source : request.getNodesDef()) {
            String name = requiredText(source, "name", "节点缺少name");
            if (refsByName.put(name, nextRef) != null) {
                throw new IllegalArgumentException("节点name重复: " + name);
            }
            ObjectNode node = source.deepCopy();
            node.put("nodeIdRef", nextRef);
            validateNode(node);
            String type = node.path("nodeType").asText();
            if (WorkflowNodeType.FUNC_NODE.name().equals(type)) {
                String functionType = node.path("functionType").asText();
                if (WorkflowNodeFunctionType.START.name().equals(functionType)) startRef = unique(startRef, nextRef, "START");
                if (WorkflowNodeFunctionType.END.name().equals(functionType)) endRef = unique(endRef, nextRef, "END");
            }
            nodes.put(nextRef, node);
            nextRef++;
        }
        if (startRef < 0 || endRef < 0) {
            throw new IllegalArgumentException("工作流必须且只能包含一个START和一个END节点");
        }

        Map<Long, List<Connection>> outgoing = indexedConnections(nodes.keySet());
        Map<Long, List<Connection>> incoming = indexedConnections(nodes.keySet());
        validateInterfaceConnections(request.getInterfaceConnections(), refsByName, nodes, outgoing, incoming);
        validateDeviceInterfaceConnections(request.getInterfaceConnections(), refsByName, nodes);
        validatePortConnections(request.getPortConnections(), refsByName, nodes);
        validateTopology(nodes, outgoing, incoming, startRef, endRef);
        validateAcyclic(nodes.keySet(), outgoing);
        return new CompiledWorkflow(Map.copyOf(nodes), immutable(outgoing), immutable(incoming), Map.copyOf(refsByName), startRef, endRef);
    }

    private void validateNode(JsonNode node) {
        String type = requiredText(node, "nodeType", "节点缺少nodeType");
        if (!Set.of(WorkflowNodeType.values()).stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()).contains(type)) {
            throw new IllegalArgumentException("不支持的节点类型: " + type);
        }
        validateVariables(node.path("internalVariables"));
        validateInterfaces(node);
        validatePorts(node);
        validateActions(node);
        if (WorkflowNodeType.DEV_NODE.name().equals(type)) {
            if (!node.path("deviceModelId").canConvertToLong()) throw new IllegalArgumentException("DEV_NODE缺少deviceModelId");
            requiredText(node.path("capability"), "capabilityName", "DEV_NODE缺少capability.capabilityName");
            validateLifecycle(node.path("lifecycle"));
        } else if (WorkflowNodeType.SUBFLOW_NODE.name().equals(type)) {
            if (!node.path("subFlowModelId").canConvertToLong()) throw new IllegalArgumentException("SUBFLOW_NODE缺少subFlowModelId");
            validateLifecycle(node.path("lifecycle"));
        } else {
            String functionType = requiredText(node, "functionType", "FUNC_NODE缺少functionType");
            try {
                WorkflowNodeFunctionType.valueOf(functionType);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("不支持的功能节点类型: " + functionType);
            }
        }
    }

    private void validateVariables(JsonNode variables) {
        Set<String> names = new HashSet<>();
        for (JsonNode variable : iterable(variables)) {
            String name = requiredText(variable, "name", "内部变量缺少name");
            if (!names.add(name)) throw new IllegalArgumentException("内部变量name重复: " + name);
        }
    }

    private void validateLifecycle(JsonNode lifecycle) {
        if (!lifecycle.isObject()) throw new IllegalArgumentException("节点lifecycle必须是对象");
        String initial = requiredText(lifecycle, "initialStateName", "节点lifecycle缺少initialStateName");
        Set<String> states = new HashSet<>();
        for (JsonNode state : iterable(lifecycle.path("states"))) states.add(state.asText());
        if (!states.contains(initial)) throw new IllegalArgumentException("节点初始生命周期状态未声明: " + initial);
        if (!"PENDING".equals(initial)) throw new IllegalArgumentException("节点lifecycle.initialStateName必须为PENDING");
        Set<String> transitions = new HashSet<>();
        for (JsonNode transition : iterable(lifecycle.path("transitions"))) {
            transitions.add(requiredText(transition, "fromStateName", "节点生命周期转移缺少fromStateName")
                    + "→" + requiredText(transition, "toStateName", "节点生命周期转移缺少toStateName"));
        }
        for (String expected : List.of("PENDING→RUNNING", "PENDING→TERMINATED", "RUNNING→SUCCEEDED", "RUNNING→FAILED",
                "RUNNING→TERMINATING", "TERMINATING→TERMINATED", "TERMINATING→FAILED")) {
            if (!transitions.contains(expected)) throw new IllegalArgumentException("节点lifecycle缺少引擎必需转移: " + expected);
        }        for (JsonNode transition : iterable(lifecycle.path("transitions"))) {
            if (!states.contains(requiredText(transition, "fromStateName", "节点生命周期转移缺少fromStateName"))
                    || !states.contains(requiredText(transition, "toStateName", "节点生命周期转移缺少toStateName"))) {
                throw new IllegalArgumentException("节点生命周期转移引用未声明状态");
            }
        }
    }

    private void validateInterfaces(JsonNode node) {
        Set<String> names = new HashSet<>();
        for (JsonNode item : iterable(node.path("interfaces"))) {
            String name = requiredText(item, "name", "节点接口缺少name");
            if (!names.add(name)) throw new IllegalArgumentException("节点接口name重复: " + name);
            if (!Set.of("IN", "OUT").contains(requiredText(item, "direction", "节点接口缺少direction"))
                    || !Set.of("WORKFLOW", "STATE").contains(requiredText(item, "interfaceType", "节点接口缺少interfaceType"))) {
                throw new IllegalArgumentException("节点接口方向或类型不合法: " + name);
            }
        }
    }

    private void validatePorts(JsonNode node) {
        Set<String> variables = names(node.path("internalVariables"), "name");
        Set<String> ports = new HashSet<>();
        for (JsonNode port : iterable(node.path("ports"))) {
            String name = requiredText(port, "name", "节点端口缺少name");
            if (!ports.add(name)) throw new IllegalArgumentException("节点端口name重复: " + name);
            if (!Set.of("IN", "OUT").contains(requiredText(port, "direction", "节点端口缺少direction"))
                    || !variables.contains(requiredText(port, "internalVariableName", "节点端口缺少internalVariableName"))) {
                throw new IllegalArgumentException("节点端口引用了不存在的内部变量: " + name);
            }
        }
    }

    private void validateActions(JsonNode node) {
        Map<String, JsonNode> interfaces = index(node.path("interfaces"), "name");
        Set<String> variables = names(node.path("internalVariables"), "name");
        Set<String> actions = new HashSet<>();
        for (JsonNode action : iterable(node.path("actions"))) {
            String actionName = requiredText(action, "actionName", "节点动作缺少actionName");
            String actionType = requiredText(action, "actionType", "节点动作缺少actionType");
            if (!Set.of(WorkflowNodeActionType.values()).stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()).contains(actionType)
                    || !actions.add(actionName)) {
                throw new IllegalArgumentException("节点动作类型不合法或actionName重复: " + actionName);
            }
            if (WorkflowNodeActionType.EMIT.name().equals(actionType)) {
                String interfaceName = requiredText(action, "targetInterfaceName", "EMIT缺少targetInterfaceName");
                String signalName = requiredText(action, "signalName", "EMIT缺少signalName");
                JsonNode target = interfaces.get(interfaceName);
                if (target == null || !"OUT".equals(target.path("direction").asText())
                        || !contains(target.path("allowedSignals"), signalName)) {
                    throw new IllegalArgumentException("EMIT目标接口或信号不合法: " + interfaceName + "." + signalName);
                }
            } else {
                if (!variables.contains(requiredText(action, "internalVariableName", "UPDATE缺少internalVariableName"))
                        || requiredText(action, "valueExpression", "UPDATE缺少valueExpression").isBlank()) {
                    throw new IllegalArgumentException("UPDATE动作不合法");
                }
            }
        }
        for (JsonNode item : iterable(node.path("interfaces"))) {
            if (!iterable(item.path("bindingTriggers")).iterator().hasNext()) continue;
            if (!"IN".equals(item.path("direction").asText())) throw new IllegalArgumentException("bindingTriggers只能声明在IN接口: " + item.path("name").asText());
            for (JsonNode trigger : iterable(item.path("bindingTriggers"))) {
                String actionName = requiredText(trigger, "action", "接口触发器缺少action");
                if (!actions.contains(actionName)) throw new IllegalArgumentException("接口触发器引用不存在动作: " + actionName);
                JsonNode condition = trigger.path("condition");
                requiredText(condition, "object", "接口触发器条件缺少object");
                String operator = requiredText(condition, "operator", "接口触发器条件缺少operator");
                if (!Arrays.stream(ConstraintOperator.values()).map(ConstraintOperator::value).toList().contains(operator)
                        || !condition.has("threshold")) throw new IllegalArgumentException("接口触发器条件不合法");
            }
        }
    }

    private void validateInterfaceConnections(JsonNode connections, Map<String, Long> refs, Map<Long, JsonNode> nodes,
                                              Map<Long, List<Connection>> outgoing, Map<Long, List<Connection>> incoming) {
        if (connections == null || !connections.isArray()) throw new IllegalArgumentException("interfaceConnections必须是数组");
        for (JsonNode connection : connections) {
            String type = requiredText(connection, "connectionType", "接口连接缺少connectionType");
            JsonNode source = connection.path("source");
            JsonNode target = connection.path("target");
            if ("NODE_TO_NODE".equals(type)) {
                long sourceRef = ref(source, refs, "接口连接source");
                long targetRef = ref(target, refs, "接口连接target");
                validateEndpointType(nodes.get(sourceRef), source.path("interfaceName").asText(), "OUT", "WORKFLOW", "节点到节点连接source");
                validateEndpointType(nodes.get(targetRef), target.path("interfaceName").asText(), "IN", "WORKFLOW", "节点到节点连接target");
                Connection item = new Connection(sourceRef, source.path("interfaceName").asText(), targetRef, target.path("interfaceName").asText());
                outgoing.get(sourceRef).add(item);
                incoming.get(targetRef).add(item);
            } else if ("NODE_TO_DEVICE".equals(type)) {
                long sourceRef = ref(source, refs, "节点到设备连接source");
                validateEndpointType(nodes.get(sourceRef), source.path("interfaceName").asText(), "OUT", "STATE", "节点到设备连接source");
                if (!target.path("deviceInstanceId").canConvertToLong()) throw new IllegalArgumentException("节点到设备连接缺少deviceInstanceId");
            } else if ("DEVICE_TO_NODE".equals(type)) {
                long targetRef = ref(target, refs, "设备到节点连接target");
                validateEndpointType(nodes.get(targetRef), target.path("interfaceName").asText(), "IN", "STATE", "设备到节点连接target");
                if (!source.path("deviceInstanceId").canConvertToLong()) throw new IllegalArgumentException("设备到节点连接缺少deviceInstanceId");
            } else {
                throw new IllegalArgumentException("接口连接类型不合法: " + type);
            }
        }
    }

    private void validateDeviceInterfaceConnections(JsonNode connections, Map<String, Long> refs, Map<Long, JsonNode> nodes) {
        Map<Long, Long> nodeToDevice = new HashMap<>();
        Map<Long, Long> deviceToNode = new HashMap<>();
        for (JsonNode connection : iterable(connections)) {
            String type = connection.path("connectionType").asText("");
            if ("NODE_TO_DEVICE".equals(type)) {
                long sourceRef = ref(connection.path("source"), refs, "节点到设备连接source");
                if (!WorkflowNodeType.DEV_NODE.name().equals(nodes.get(sourceRef).path("nodeType").asText())) {
                    throw new IllegalArgumentException("NODE_TO_DEVICE只能从DEV_NODE发出");
                }
                long deviceInstanceId = connection.path("target").path("deviceInstanceId").asLong(0);
                if (deviceInstanceId <= 0 || connection.path("target").path("interfaceName").asText("").isBlank()) {
                    throw new IllegalArgumentException("NODE_TO_DEVICE目标设备或接口不完整");
                }
                if (nodeToDevice.putIfAbsent(sourceRef, deviceInstanceId) != null) {
                    throw new IllegalArgumentException("DEV_NODE只能有一个NODE_TO_DEVICE连接");
                }
            } else if ("DEVICE_TO_NODE".equals(type)) {
                long targetRef = ref(connection.path("target"), refs, "设备到节点连接target");
                if (!WorkflowNodeType.DEV_NODE.name().equals(nodes.get(targetRef).path("nodeType").asText())) {
                    throw new IllegalArgumentException("DEVICE_TO_NODE只能连接到DEV_NODE");
                }
                long deviceInstanceId = connection.path("source").path("deviceInstanceId").asLong(0);
                if (deviceInstanceId <= 0 || connection.path("source").path("interfaceName").asText("").isBlank()) {
                    throw new IllegalArgumentException("DEVICE_TO_NODE源设备或接口不完整");
                }
                if (deviceToNode.putIfAbsent(targetRef, deviceInstanceId) != null) {
                    throw new IllegalArgumentException("DEV_NODE只能有一个DEVICE_TO_NODE连接");
                }
            }
        }
        for (Map.Entry<Long, JsonNode> entry : nodes.entrySet()) {
            if (!WorkflowNodeType.DEV_NODE.name().equals(entry.getValue().path("nodeType").asText())) continue;
            Long outboundDevice = nodeToDevice.get(entry.getKey());
            Long inboundDevice = deviceToNode.get(entry.getKey());
            if (outboundDevice == null || inboundDevice == null) {
                throw new IllegalArgumentException("DEV_NODE必须同时声明NODE_TO_DEVICE和DEVICE_TO_NODE连接");
            }
            if (!outboundDevice.equals(inboundDevice)) {
                throw new IllegalArgumentException("DEV_NODE的设备输入输出连接必须引用同一设备实例");
            }
        }
    }
    private void validatePortConnections(JsonNode connections, Map<String, Long> refs, Map<Long, JsonNode> nodes) {
        if (connections == null || !connections.isArray()) throw new IllegalArgumentException("portConnections必须是数组");
        for (JsonNode connection : connections) {
            JsonNode source = connection.path("source");
            JsonNode target = connection.path("target");
            validatePort(nodes.get(ref(source, refs, "端口连接source")), source.path("portName").asText(), "OUT");
            validatePort(nodes.get(ref(target, refs, "端口连接target")), target.path("portName").asText(), "IN");
        }
    }

    private void validateTopology(Map<Long, JsonNode> nodes, Map<Long, List<Connection>> outgoing,
                                  Map<Long, List<Connection>> incoming, long startRef, long endRef) {
        for (Map.Entry<Long, JsonNode> entry : nodes.entrySet()) {
            long ref = entry.getKey();
            String function = entry.getValue().path("functionType").asText();
            if (ref == startRef && !incoming.get(ref).isEmpty()) throw new IllegalArgumentException("START节点不能有NODE_TO_NODE输入");
            if (ref == endRef && !outgoing.get(ref).isEmpty()) throw new IllegalArgumentException("END节点不能有NODE_TO_NODE输出");
            if (ref != startRef && incoming.get(ref).isEmpty()) throw new IllegalArgumentException("非START节点必须有NODE_TO_NODE输入");
            if (ref != endRef && outgoing.get(ref).isEmpty()) throw new IllegalArgumentException("非END节点必须有NODE_TO_NODE输出");
            if (WorkflowNodeFunctionType.AGGREGATE.name().equals(function) && incoming.get(ref).size() < 2) throw new IllegalArgumentException("AGGREGATE节点至少需要两个输入");
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

    private long ref(JsonNode endpoint, Map<String, Long> refs, String label) {
        Long ref = refs.get(requiredText(endpoint, "nodeName", label + "缺少nodeName"));
        if (ref == null) throw new IllegalArgumentException(label + "引用不存在节点");
        return ref;
    }

    private void validateEndpointType(JsonNode node, String name, String direction, String interfaceType, String label) {
        for (JsonNode item : iterable(node.path("interfaces"))) {
            if (name.equals(item.path("name").asText()) && direction.equals(item.path("direction").asText())
                    && interfaceType.equals(item.path("interfaceType").asText())) return;
        }
        throw new IllegalArgumentException(label + "接口不存在、方向不匹配或接口类型不匹配: " + name);
    }

    private void validatePort(JsonNode node, String name, String direction) {
        for (JsonNode port : iterable(node.path("ports"))) if (name.equals(port.path("name").asText()) && direction.equals(port.path("direction").asText())) return;
        throw new IllegalArgumentException("端口不存在或方向不匹配: " + name);
    }

    private Map<String, JsonNode> index(JsonNode nodes, String field) {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode node : iterable(nodes)) result.put(node.path(field).asText(), node);
        return result;
    }

    private Set<String> names(JsonNode nodes, String field) {
        Set<String> result = new HashSet<>();
        for (JsonNode node : iterable(nodes)) result.add(node.path(field).asText());
        return result;
    }

    private boolean contains(JsonNode values, String value) {
        for (JsonNode item : iterable(values)) if (value.equals(item.asText())) return true;
        return false;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private String requiredText(JsonNode node, String field, String message) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    private Map<Long, List<Connection>> immutable(Map<Long, List<Connection>> source) {
        Map<Long, List<Connection>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
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
