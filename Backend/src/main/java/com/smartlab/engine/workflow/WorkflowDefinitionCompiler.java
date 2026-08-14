package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.ConstraintOperator;
import com.smartlab.global.contract.DataType;
import com.smartlab.global.contract.WorkflowNodeActionType;
import com.smartlab.global.contract.WorkflowNodeFunctionType;
import com.smartlab.global.contract.WorkflowNodeSystemContract;
import com.smartlab.global.contract.WorkflowNodeType;
import com.smartlab.global.contract.ProtocolContract;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.global.util.JsonNodeSupport;
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


    private final WorkflowDefinitionCanonicalizer canonicalizer = new WorkflowDefinitionCanonicalizer();
    private final ConstraintExpressionEvaluator expressionEvaluator = new ConstraintExpressionEvaluator();
    private CompiledWorkflow compileStrict(WorkflowSaveRequest request) {
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
        validateAggregateThresholds(nodes, incoming);
        validateAcyclic(nodes.keySet(), outgoing);
        return new CompiledWorkflow(Map.copyOf(nodes), immutable(outgoing), immutable(incoming), Map.copyOf(refsByName), startRef, endRef);
    }

    public CompiledWorkflow compile(WorkflowSaveRequest request) {
        WorkflowPreparation prepared = prepare(request, WorkflowPreparation.Mode.PUBLISH);
        if (!prepared.executable()) throw new IllegalArgumentException(firstBlockingMessage(prepared.issues()));
        return prepared.compiled();
    }

    public WorkflowPreparation prepare(WorkflowSaveRequest request, WorkflowPreparation.Mode mode) {
        WorkflowPreparation.Mode effectiveMode = mode == null ? WorkflowPreparation.Mode.PUBLISH : mode;
        WorkflowDefinitionCanonicalizer.CanonicalizationResult canonical = canonicalizer.canonicalize(request);
        List<WorkflowIssue> issues = new ArrayList<>();
        for (WorkflowIssue issue : canonical.issues()) {
            issues.add(withBlocking(issue, effectiveMode == WorkflowPreparation.Mode.PUBLISH));
        }
        CompiledWorkflow compiled = null;
        try {
            compiled = compileStrict(canonical.normalized());
        } catch (IllegalArgumentException error) {
            issues.add(new WorkflowIssue("WORKFLOW_DEFINITION_INVALID", "COMPILATION", "", "workflow", "", effectiveMode == WorkflowPreparation.Mode.PUBLISH,
                    error.getMessage(), "请修正工作流定义后重试"));
        }
        return new WorkflowPreparation(canonical.normalized(), List.copyOf(issues), compiled);
    }

    private WorkflowIssue withBlocking(WorkflowIssue issue, boolean blocking) {
        return new WorkflowIssue(issue.code(), issue.stage(), issue.path(), issue.elementType(), issue.elementId(),
                blocking, issue.message(), issue.suggestion());
    }

    private String firstBlockingMessage(List<WorkflowIssue> issues) {
        return issues.stream().filter(WorkflowIssue::blocking).map(WorkflowIssue::message).findFirst()
                .orElse("工作流定义不可执行");
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
        Set<String> actions = indexActionNames(actionsNode, name);

        int position = 0;
        for (JsonNode variable : variablesNode) {
            String dataTypePath = nodePath(name, "internalVariables[" + position + "].dataType");
            String dataType = requiredText(variable, "dataType", dataTypePath + ": 不能为空");
            requireDataType(dataType, dataTypePath);
            if (variable.has("initialValue") && !matchesDataType(variable.path("initialValue"), dataType)) {
                throw nodeError(name, "internalVariables[" + position + "].initialValue",
                        "initialValue与数据类型不一致: " + dataType);
            }
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
            Set<String> protocolSignals = protocolSignals(interfaceType, direction);
            for (JsonNode signal : allowedSignals) {
                if (!signal.isTextual() || signal.asText().isBlank()) throw nodeError(name, itemPath + ".allowedSignals", "只能包含非空字符串");
                if (!protocolSignals.contains(signal.asText())) {
                    throw nodeError(name, itemPath + ".allowedSignals",
                            interfaceType + " " + direction + "接口信号不合法: " + signal.asText());
                }
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
            if (Set.of(WorkflowNodeFunctionType.BRANCH.name(), WorkflowNodeFunctionType.AGGREGATE.name())
                    .contains(functionType) && node.has("expression") && !node.path("expression").asText().isBlank()) {
                validateCalculationExpression(index, node.path("expression").asText(), "expression");
            }
        }
        ObjectNode expected = WorkflowNodeSystemContract.template(index.nodeType(), functionType);
        validateTemplateLifecycle(node.path("lifecycle"), expected.path("lifecycle"), name);
        expected = canonicalizer.canonicalizeTemplate(expected);
        validateSystemInterfaces(index, node.path("interfaces"), expected.path("interfaces"));
        validateSystemActions(index, node.path("actions"), expected.path("actions"));
        if (WorkflowNodeFunctionType.AGGREGATE.name().equals(functionType)) {
            validateAggregateInputCounters(index, node.path("interfaces"));
        }
    }

    private void validateAggregateInputCounters(NodeIndex index, JsonNode interfaces) {
        for (JsonNode interfaceNode : iterable(interfaces)) {
            if (!"IN".equals(interfaceNode.path("direction").asText())
                    || !"WORKFLOW".equals(interfaceNode.path("interfaceType").asText())) continue;
            boolean hasCounter = false;
            for (JsonNode trigger : iterable(interfaceNode.path("bindingTriggers"))) {
                JsonNode condition = trigger.path("condition");
                JsonNode payload = trigger.path("action").path("payload");
                if ("signalName".equals(condition.path("object").asText())
                        && "=".equals(condition.path("operator").asText())
                        && "ACTIVE".equals(condition.path("threshold").asText())
                        && "UPDATE".equals(trigger.path("action").path("actionName").asText())
                        && "INTERNAL_VARIABLE".equals(payload.path("updateType").asText())
                        && "aggregateCount".equals(payload.path("targetName").asText())
                        && "aggregateCount+1".equals(payload.path("valueExpression").asText().replaceAll("\\s+", ""))) {
                    hasCounter = true;
                    break;
                }
            }
            if (!hasCounter) {
                throw nodeError(index.nodeName(), "interfaces." + interfaceNode.path("name").asText()
                        + ".bindingTriggers", "聚合输入接口必须包含计数触发器");
            }
        }
    }
    private void validateActions(NodeIndex index, JsonNode node, String path) {
        // action-name membership is validated while indexing; payloads live on triggers.
    }

    private void validateCalculationExpression(NodeIndex index, String expression, String fieldPath) {
        Map<String, JsonNode> samples = new LinkedHashMap<>();
        index.variables().forEach((name, definition) -> {
            String dataType = definition.path("dataType").asText();
            if (DataType.INTEGER.name().equals(dataType)) samples.put(name, JsonNodeSupport.toNode(1));
            if (DataType.DOUBLE.name().equals(dataType)) samples.put(name, JsonNodeSupport.toNode(1.0));
        });
        try {
            WorkflowAssignmentExpression assignment = WorkflowAssignmentExpression.parse(expression);
            JsonNode target = index.variables().get(assignment.targetName());
            if (target == null) {
                throw new IllegalArgumentException("赋值目标未在变量空间中声明：" + assignment.targetName());
            }
            String targetType = target.path("dataType").asText();
            if (!Set.of(DataType.INTEGER.name(), DataType.DOUBLE.name()).contains(targetType)) {
                throw new IllegalArgumentException("赋值目标必须是数值类型（INTEGER 或 DOUBLE）：" + assignment.targetName());
            }
            expressionEvaluator.validateTemporalCalculation(assignment.valueExpression(), samples);
        } catch (IllegalArgumentException error) {
            throw nodeError(index.nodeName(), fieldPath, error.getMessage());
        }
    }

    private void validateTriggers(NodeIndex index, JsonNode node, String path) {
        int interfacePosition = 0;
        for (JsonNode item : node.path("interfaces")) {
            JsonNode triggers = item.path("bindingTriggers");
            int triggerPosition = 0;
            for (JsonNode trigger : iterable(triggers)) {
                String triggerPath = "interfaces[" + interfacePosition + "].bindingTriggers[" + triggerPosition + "]";
                JsonNode condition = trigger.path("condition");
                if (!condition.isObject()) throw nodeError(index.nodeName(), triggerPath + ".condition", "必须是对象");
                validateTriggerCondition(index, condition, triggerPath + ".condition");
                validateTriggerAction(index, node, item.path("name").asText(), trigger.path("action"), triggerPath + ".action");
                triggerPosition++;
            }
            interfacePosition++;
        }
    }

    private void validateTriggerAction(NodeIndex index, JsonNode node, String hostInterfaceName,
                                       JsonNode action, String actionPath) {
        if (!action.isObject()) throw nodeError(index.nodeName(), actionPath, "必须是对象");
        String actionName = requiredText(action, "actionName",
                nodePath(index.nodeName(), actionPath + ".actionName") + ": 不能为空");
        if (!index.actions().contains(actionName)) {
            throw nodeError(index.nodeName(), actionPath + ".actionName", "动作未在node.actions中声明: " + actionName);
        }
        JsonNode payload = action.path("payload");
        if (!payload.isObject()) throw nodeError(index.nodeName(), actionPath + ".payload", "必须是对象");
        if (WorkflowNodeActionType.EMIT.name().equals(actionName)) {
            String targetName = requiredText(payload, "targetInterfaceName",
                    nodePath(index.nodeName(), actionPath + ".payload.targetInterfaceName") + ": 不能为空");
            if (!hostInterfaceName.equals(targetName)) {
                throw nodeError(index.nodeName(), actionPath + ".payload.targetInterfaceName",
                        "EMIT目标必须是触发器所在接口" + hostInterfaceName + ": " + targetName);
            }
            JsonNode target = index.interfaces().get(targetName);
            if (target == null) throw nodeError(index.nodeName(), actionPath + ".payload.targetInterfaceName", "EMIT目标接口不存在: " + targetName);
            if (!"OUT".equals(target.path("direction").asText())) throw nodeError(index.nodeName(), actionPath + ".payload.targetInterfaceName", "EMIT目标必须是OUT接口: " + targetName);
            String signal = requiredText(payload, "signalName",
                    nodePath(index.nodeName(), actionPath + ".payload.signalName") + ": 不能为空");
            if (!contains(target.path("allowedSignals"), signal)) throw nodeError(index.nodeName(), actionPath + ".payload.signalName", "EMIT信号不在接口allowedSignals中: " + signal);
            return;
        }
        if (!WorkflowNodeActionType.UPDATE.name().equals(actionName)) {
            throw nodeError(index.nodeName(), actionPath + ".actionName", "只允许EMIT或UPDATE: " + actionName);
        }
        String updateType = requiredText(payload, "updateType",
                nodePath(index.nodeName(), actionPath + ".payload.updateType") + ": 不能为空");
        String targetName = requiredText(payload, "targetName",
                nodePath(index.nodeName(), actionPath + ".payload.targetName") + ": 不能为空");
        if ("INTERNAL_VARIABLE".equals(updateType)) {
            JsonNode variable = index.variables().get(targetName);
            if (variable == null) throw nodeError(index.nodeName(), actionPath + ".payload.targetName", "UPDATE引用不存在的内部变量: " + targetName);
            boolean hasValue = payload.has("value");
            boolean hasExpression = payload.has("valueExpression")
                    && payload.path("valueExpression").isTextual()
                    && !payload.path("valueExpression").asText().isBlank();
            if (hasValue == hasExpression) {
                throw nodeError(index.nodeName(), actionPath + ".payload", "value与valueExpression必须且只能存在一个");
            }
            if (hasValue && !matchesUpdateValue(payload.get("value"), variable.path("dataType").asText())) {
                throw nodeError(index.nodeName(), actionPath + ".payload.value", "与内部变量" + targetName + "的数据类型不一致");
            }
            if (hasExpression) {
                validateValueExpression(index, payload.path("valueExpression").asText(),
                        actionPath + ".payload.valueExpression");
            }
            return;
        }
        if ("NODE_LIFECYCLE".equals(updateType)) {
            if (payload.has("value") || payload.has("valueExpression")) {
                throw nodeError(index.nodeName(), actionPath + ".payload", "生命周期UPDATE不能包含value或valueExpression");
            }
            if (!contains(node.path("lifecycle").path("states"), targetName)) {
                throw nodeError(index.nodeName(), actionPath + ".payload.targetName", "目标生命周期状态未声明: " + targetName);
            }
            boolean declaredTarget = false;
            for (JsonNode transition : iterable(node.path("lifecycle").path("transitions"))) {
                if (targetName.equals(transition.path("toStateName").asText())) declaredTarget = true;
            }
            if (!declaredTarget) throw nodeError(index.nodeName(), actionPath + ".payload.targetName", "没有任何transition可到达目标状态: " + targetName);
            return;
        }
        throw nodeError(index.nodeName(), actionPath + ".payload.updateType", "只允许INTERNAL_VARIABLE或NODE_LIFECYCLE: " + updateType);
    }

    private void validateValueExpression(NodeIndex index, String expression, String fieldPath) {
        try {
            expressionEvaluator.validateCalculation(expression, numericVariableSamples(index));
        } catch (IllegalArgumentException error) {
            throw nodeError(index.nodeName(), fieldPath, error.getMessage());
        }
    }

    private void validateTriggerCondition(NodeIndex index, JsonNode condition, String conditionPath) {
        if (condition.has("logic") || condition.has("conditions")) {
            String logic = requiredText(condition, "logic",
                    nodePath(index.nodeName(), conditionPath + ".logic") + ": 不能为空");
            if (!"AND".equals(logic)) {
                throw nodeError(index.nodeName(), conditionPath + ".logic", "条件组合当前只支持AND: " + logic);
            }
            JsonNode conditions = condition.path("conditions");
            if (!conditions.isArray() || conditions.size() < 2) {
                throw nodeError(index.nodeName(), conditionPath + ".conditions", "AND条件组至少需要两个条件");
            }
            int position = 0;
            for (JsonNode item : conditions) {
                String itemPath = conditionPath + ".conditions[" + position + "]";
                if (!item.isObject()) throw nodeError(index.nodeName(), itemPath, "必须是对象");
                if (item.has("logic") || item.has("conditions")) {
                    throw nodeError(index.nodeName(), itemPath, "工作流条件组不支持嵌套");
                }
                validateTriggerPredicate(index, item, itemPath);
                position++;
            }
            return;
        }
        validateTriggerPredicate(index, condition, conditionPath);
    }

    private void validateTriggerPredicate(NodeIndex index, JsonNode condition, String conditionPath) {
        String object = requiredText(condition, "object",
                nodePath(index.nodeName(), conditionPath + ".object") + ": 不能为空");
        String operator = requiredText(condition, "operator",
                nodePath(index.nodeName(), conditionPath + ".operator") + ": 不能为空");
        if (!ConstraintOperator.supports(operator)) {
            throw nodeError(index.nodeName(), conditionPath + ".operator", "不支持的运算符: " + operator);
        }
        if (!condition.has("threshold") || condition.path("threshold").isNull()) {
            throw nodeError(index.nodeName(), conditionPath + ".threshold", "不能为空");
        }
        JsonNode variable = index.variables().get(object);
        if (variable != null && !matchesDataType(condition.path("threshold"), variable.path("dataType").asText())) {
            throw nodeError(index.nodeName(), conditionPath + ".threshold",
                    "与内部变量" + object + "的数据类型不一致");
        }
    }

    private Map<String, JsonNode> numericVariableSamples(NodeIndex index) {
        Map<String, JsonNode> samples = new LinkedHashMap<>();
        index.variables().forEach((name, definition) -> {
            String dataType = definition.path("dataType").asText();
            if (DataType.INTEGER.name().equals(dataType)) samples.put(name, JsonNodeSupport.toNode(1));
            if (DataType.DOUBLE.name().equals(dataType)) samples.put(name, JsonNodeSupport.toNode(1.0));
        });
        return samples;
    }

    private void validateInterfaceConnections(JsonNode connections, Map<String, Long> refs,
                                              Map<String, NodeIndex> indexes, Map<Long, JsonNode> nodes,
                                              Map<Long, List<Connection>> outgoing, Map<Long, List<Connection>> incoming) {
        if (connections == null || !connections.isArray()) throw new IllegalArgumentException("interfaceConnections必须是数组");
        Map<String, Long> nodeToDevice = new HashMap<>();
        Map<String, Long> deviceToNode = new HashMap<>();
        Set<String> connectedWorkflowInputs = new HashSet<>();
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
                String targetKey = targetNode.nodeName() + "\u0000" + targetInterface.path("name").asText();
                if (!connectedWorkflowInputs.add(targetKey)) {
                    throw new IllegalArgumentException(path
                            + ": 每个WORKFLOW IN接口最多连接一个上游接口: "
                            + targetNode.nodeName() + "." + targetInterface.path("name").asText());
                }
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
        }
    }

    private void validateAggregateThresholds(Map<Long, JsonNode> nodes,
                                             Map<Long, List<Connection>> incoming) {
        for (Map.Entry<Long, JsonNode> entry : nodes.entrySet()) {
            JsonNode node = entry.getValue();
            if (!WorkflowNodeFunctionType.AGGREGATE.name().equals(node.path("functionType").asText())) continue;
            int availableInputs = incoming.getOrDefault(entry.getKey(), List.of()).size();
            for (JsonNode interfaceNode : iterable(node.path("interfaces"))) {
                if (!"OUT".equals(interfaceNode.path("direction").asText())
                        || !"WORKFLOW".equals(interfaceNode.path("interfaceType").asText())) continue;
                for (JsonNode trigger : iterable(interfaceNode.path("bindingTriggers"))) {
                    for (JsonNode predicate : triggerPredicates(trigger.path("condition"))) {
                        if (!"aggregateCount".equals(predicate.path("object").asText())
                                || !predicate.path("threshold").isIntegralNumber()) continue;
                        long threshold = predicate.path("threshold").asLong();
                        String operator = predicate.path("operator").asText();
                        long required = switch (operator) {
                            case ">" -> threshold + 1;
                            case ">=", "=", "==" -> threshold;
                            default -> -1;
                        };
                        if (required > availableInputs) {
                            throw nodeError(node.path("name").asText(),
                                    "interfaces." + interfaceNode.path("name").asText() + ".bindingTriggers",
                                    "聚合阈值" + required + "超过有效输入接口数量" + availableInputs);
                        }
                    }
                }
            }
        }
    }

    private List<JsonNode> triggerPredicates(JsonNode condition) {
        if (condition.path("conditions").isArray()) return nodeList(condition.path("conditions"));
        return condition.isObject() ? List.of(condition) : List.of();
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
            validateSystemTriggers(index, interfaceName, actual.path("bindingTriggers"),
                    expected.path("bindingTriggers"));
        }
        // System interfaces are mandatory; additional user interfaces are valid trigger channels.
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

    private void validateSystemTriggers(NodeIndex nodeIndex, String interfaceName,
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
                throw nodeError(nodeIndex.nodeName(), "interfaces." + interfaceName + ".bindingTriggers",
                        "系统触发器缺失或已篡改");
            }
            matchedIndexes.add(matchedIndex);
        }
        for (int index = 0; index < actualItems.size(); index++) {
            if (!matchedIndexes.contains(index) && claimsSystemIdentity(actualItems.get(index))) {
                throw nodeError(nodeIndex.nodeName(), "interfaces." + interfaceName + ".bindingTriggers",
                        "存在未知或已篡改的系统触发器");
            }
        }
    }

    private boolean triggerBusinessEquals(JsonNode actual, JsonNode expected) {
        return actual != null && actual.isObject()
                && expected.path("action").equals(actual.path("action"))
                && expected.path("condition").equals(actual.path("condition"));
    }

    private void validateSystemActions(NodeIndex index, JsonNode actualActions, JsonNode expectedActions) {
        Set<String> expected = uniqueTextSet(expectedActions);
        if (expected == null || !index.actions().containsAll(expected)) {
            throw nodeError(index.nodeName(), "actions", "系统动作能力声明缺失");
        }
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
                && systemKeyMatches(expected.path("_systemKey").asText(), actual.path("_systemKey").asText());
    }

    private boolean systemKeyMatches(String expectedKey, String actualKey) {
        return expectedKey.equals(actualKey)
                || ("lifecycle".equals(expectedKey) && actualKey.endsWith(".lifecycle"));
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

    private Set<String> indexActionNames(JsonNode items, String nodeName) {
        Set<String> result = new java.util.LinkedHashSet<>();
        int position = 0;
        for (JsonNode item : items) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw nodeError(nodeName, "actions[" + position + "]", "必须是EMIT或UPDATE字符串");
            }
            String actionName = item.asText();
            if (!ACTION_TYPES.contains(actionName)) {
                throw nodeError(nodeName, "actions[" + position + "]", "只允许EMIT或UPDATE: " + actionName);
            }
            if (!result.add(actionName)) {
                throw nodeError(nodeName, "actions[" + position + "]", "动作声明重复: " + actionName);
            }
            position++;
        }
        return Set.copyOf(result);
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

    private boolean matchesUpdateValue(JsonNode value, String dataType) {
        if (value == null) return false;
        if (DataType.JSON.name().equals(dataType)) return true;
        if (value.isNull()) return false;
        return matchesDataType(value, dataType);
    }

    private boolean contains(JsonNode values, String value) {
        for (JsonNode item : iterable(values)) if (value.equals(item.asText())) return true;
        return false;
    }

    private Set<String> protocolSignals(String interfaceType, String direction) {
        if ("WORKFLOW".equals(interfaceType)) {
            return Set.copyOf(ProtocolContract.enumValues("WorkflowNodeSignal"));
        }
        return Set.copyOf(ProtocolContract.enumValues(
                "OUT".equals(direction) ? "WorkflowControlSignal" : "StatusSignal"));
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
                              Set<String> actions) {
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
