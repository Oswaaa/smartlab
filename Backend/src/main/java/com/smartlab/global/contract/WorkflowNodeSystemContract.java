package com.smartlab.global.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;

import java.util.Arrays;
import java.util.List;

/** 工作流节点由系统维护的生命周期、接口、触发器和动作模板。 */
public final class WorkflowNodeSystemContract {

    private WorkflowNodeSystemContract() {
    }

    public static ObjectNode template(String nodeType, String functionType) {
        if (nodeType == null) {
            throw new IllegalArgumentException("工作流节点类型不能为空");
        }
        return switch (nodeType) {
            case "DEV_NODE" -> deviceTemplate();
            case "SUBFLOW_NODE" -> subflowTemplate();
            case "FUNC_NODE" -> functionTemplate(functionType);
            default -> throw new IllegalArgumentException("不支持的工作流节点模板: " + nodeType);
        };
    }

    public static ObjectNode templates() {
        ObjectNode result = JsonNodeSupport.objectNode();
        result.set("START", template("FUNC_NODE", "START"));
        result.set("END", template("FUNC_NODE", "END"));
        result.set("BRANCH", template("FUNC_NODE", "BRANCH"));
        result.set("AGGREGATE", template("FUNC_NODE", "AGGREGATE"));
        result.set("DEV_NODE", template("DEV_NODE", null));
        result.set("SUBFLOW_NODE", template("SUBFLOW_NODE", null));
        return result;
    }

    private static ObjectNode functionTemplate(String functionType) {
        if (functionType == null) {
            throw new IllegalArgumentException("功能节点类型不能为空");
        }
        return switch (functionType) {
            case "START" -> startTemplate();
            case "END" -> endTemplate();
            case "BRANCH" -> branchTemplate();
            case "AGGREGATE" -> aggregateTemplate();
            default -> throw new IllegalArgumentException("不支持的功能节点模板: " + functionType);
        };
    }

    private static ObjectNode startTemplate() {
        ObjectNode template = baseTemplate();
        template.withArray("interfaces").add(workflowInterface("start.workflowOut", "Interface_workflow_out", "OUT", List.of()));
        template.withArray("actions").add(emitAction("start.emitActive", "emitActive", "Interface_workflow_out", WorkflowNodeSignal.ACTIVE.name()));
        return template;
    }

    private static ObjectNode endTemplate() {
        ObjectNode template = baseTemplate();
        template.withArray("interfaces").add(workflowInterface("end.workflowIn", "Interface_workflow_in", "IN", List.of()));
        return template;
    }

    private static ObjectNode branchTemplate() {
        ObjectNode template = baseTemplate();
        template.withArray("interfaces").add(workflowInterface(
                "branch.workflowIn", "Interface_workflow_in", "IN", List.of()));
        return template;
    }

    private static ObjectNode aggregateTemplate() {
        ObjectNode template = baseTemplate();
        ArrayNode triggers = JsonNodeSupport.arrayNode();
        triggers.add(trigger("aggregate.active", "inputSignalName", "=", WorkflowNodeSignal.ACTIVE.name(), "emitActive"));
        template.withArray("interfaces").add(workflowInterface("aggregate.workflowIn", "Interface_workflow_in", "IN", triggers));
        template.withArray("interfaces").add(workflowInterface("aggregate.workflowOut", "Interface_workflow_out", "OUT", List.of()));
        template.withArray("actions").add(emitAction("aggregate.emitActive", "emitActive", "Interface_workflow_out", WorkflowNodeSignal.ACTIVE.name()));
        return template;
    }

    private static ObjectNode deviceTemplate() {
        ObjectNode template = baseTemplate();
        ArrayNode workflowTriggers = JsonNodeSupport.arrayNode();
        workflowTriggers.add(trigger("device.workflowStart", "inputSignalName", "=", WorkflowNodeSignal.ACTIVE.name(), "startDevice"));
        ArrayNode stateTriggers = JsonNodeSupport.arrayNode();
        stateTriggers.add(trigger("device.stateCompleted", "inputPayload.stateName", "=", "COMPLETED", "completeNode"));
        template.withArray("interfaces").add(workflowInterface("device.workflowIn", "Interface_workflow_in", "IN", workflowTriggers));
        template.withArray("interfaces").add(interfaceDefinition("device.stateOut", "Interface_state_out", "OUT", "STATE",
                List.of(WorkflowControlSignal.WF_EXECUTE_START.name()), JsonNodeSupport.arrayNode()));
        template.withArray("interfaces").add(interfaceDefinition("device.stateIn", "Interface_state_in", "IN", "STATE",
                names(StatusSignal.values()), stateTriggers));
        template.withArray("interfaces").add(workflowInterface("device.workflowOut", "Interface_workflow_out", "OUT", List.of()));
        template.withArray("actions").add(emitAction("device.startDevice", "startDevice", "Interface_state_out", WorkflowControlSignal.WF_EXECUTE_START.name()));
        template.withArray("actions").add(emitAction("device.completeNode", "completeNode", "Interface_workflow_out", WorkflowNodeSignal.ACTIVE.name()));
        return template;
    }

    private static ObjectNode subflowTemplate() {
        ObjectNode template = baseTemplate();
        template.withArray("interfaces").add(workflowInterface("subflow.workflowIn", "Interface_workflow_in", "IN", List.of()));
        template.withArray("interfaces").add(workflowInterface("subflow.workflowOut", "Interface_workflow_out", "OUT", List.of()));
        return template;
    }

    private static ObjectNode baseTemplate() {
        ObjectNode template = JsonNodeSupport.objectNode();
        template.set("lifecycle", lifecycle());
        template.putArray("interfaces");
        template.putArray("actions");
        return template;
    }

    private static ObjectNode lifecycle() {
        ObjectNode lifecycle = JsonNodeSupport.objectNode();
        lifecycle.put("initialStateName", NodeLifecycleState.PENDING.name());
        ArrayNode states = lifecycle.putArray("states");
        for (NodeLifecycleState state : NodeLifecycleState.values()) states.add(state.name());
        ArrayNode transitions = lifecycle.putArray("transitions");
        transition(transitions, NodeLifecycleState.PENDING, NodeLifecycleState.RUNNING);
        transition(transitions, NodeLifecycleState.PENDING, NodeLifecycleState.TERMINATED);
        transition(transitions, NodeLifecycleState.RUNNING, NodeLifecycleState.SUCCEEDED);
        transition(transitions, NodeLifecycleState.RUNNING, NodeLifecycleState.FAILED);
        transition(transitions, NodeLifecycleState.RUNNING, NodeLifecycleState.TERMINATING);
        transition(transitions, NodeLifecycleState.TERMINATING, NodeLifecycleState.TERMINATED);
        transition(transitions, NodeLifecycleState.TERMINATING, NodeLifecycleState.FAILED);
        return system("lifecycle", lifecycle);
    }

    private static void transition(ArrayNode transitions, NodeLifecycleState from, NodeLifecycleState to) {
        ObjectNode transition = transitions.addObject();
        transition.put("fromStateName", from.name());
        transition.put("toStateName", to.name());
        system("lifecycle." + from.name().toLowerCase() + "." + to.name().toLowerCase(), transition);
    }

    private static ObjectNode workflowInterface(String systemKey, String name, String direction, Iterable<? extends JsonNode> bindingTriggers) {
        ArrayNode triggers = JsonNodeSupport.arrayNode();
        bindingTriggers.forEach(triggers::add);
        return interfaceDefinition(systemKey, name, direction, "WORKFLOW", names(WorkflowNodeSignal.values()), triggers);
    }

    private static ObjectNode interfaceDefinition(String systemKey, String name, String direction, String interfaceType,
                                                  List<String> allowedSignals, ArrayNode bindingTriggers) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.put("name", name);
        definition.put("direction", direction);
        definition.put("interfaceType", interfaceType);
        ArrayNode signals = definition.putArray("allowedSignals");
        allowedSignals.forEach(signals::add);
        definition.set("bindingTriggers", bindingTriggers);
        return system(systemKey, definition);
    }

    private static ObjectNode trigger(String systemKey, String object, String operator, Object threshold, String action) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.put("action", action);
        ObjectNode condition = definition.putObject("condition");
        condition.put("object", object);
        condition.put("operator", operator);
        if (threshold instanceof Boolean value) condition.put("threshold", value);
        else condition.put("threshold", (String) threshold);
        return system(systemKey, definition);
    }

    private static ObjectNode emitAction(String systemKey, String actionName, String targetInterfaceName, String signalName) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.put("actionName", actionName);
        definition.put("actionType", WorkflowNodeActionType.EMIT.name());
        definition.put("targetInterfaceName", targetInterfaceName);
        definition.put("signalName", signalName);
        return system(systemKey, definition);
    }

    private static ObjectNode system(String systemKey, ObjectNode definition) {
        definition.put("_system", true);
        definition.put("_systemKey", systemKey);
        return definition;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
