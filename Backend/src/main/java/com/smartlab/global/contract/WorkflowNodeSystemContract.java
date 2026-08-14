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
        ArrayNode triggers = JsonNodeSupport.arrayNode();
        triggers.add(trigger("start.begin", "nodeLifecycleState", "=", "PENDING",
                updateLifecycleAction("RUNNING")));
        triggers.add(trigger("start.emitActive", "nodeLifecycleState", "=", "RUNNING",
                emitAction("Interface_workflow_out", WorkflowNodeSignal.ACTIVE.name())));
        triggers.add(trigger("start.complete", "nodeLifecycleState", "=", "RUNNING",
                updateLifecycleAction("SUCCEEDED")));
        template.withArray("interfaces").add(workflowInterface(
                "start.workflowOut", "Interface_workflow_out", "OUT", triggers));
        addActions(template, WorkflowNodeActionType.UPDATE, WorkflowNodeActionType.EMIT);
        return template;
    }

    private static ObjectNode endTemplate() {
        ObjectNode template = baseTemplate();
        ArrayNode triggers = JsonNodeSupport.arrayNode();
        triggers.add(trigger("end.activate", "signalName", "=", WorkflowNodeSignal.ACTIVE.name(),
                updateLifecycleAction("RUNNING")));
        triggers.add(trigger("end.complete", "nodeLifecycleState", "=", "RUNNING",
                updateLifecycleAction("SUCCEEDED")));
        template.withArray("interfaces").add(workflowInterface(
                "end.workflowIn", "Interface_workflow_in", "IN", triggers));
        addActions(template, WorkflowNodeActionType.UPDATE, WorkflowNodeActionType.EMIT);
        return template;
    }

    private static ObjectNode branchTemplate() {
        ObjectNode template = baseTemplate();
        ArrayNode triggers = activationTriggers("branch");
        template.withArray("interfaces").add(workflowInterface(
                "branch.workflowIn", "Interface_workflow_in", "IN", triggers));
        addActions(template, WorkflowNodeActionType.UPDATE, WorkflowNodeActionType.EMIT);
        return template;
    }

    private static ObjectNode aggregateTemplate() {
        ObjectNode template = baseTemplate();
        template.withArray("interfaces").add(workflowInterface("aggregate.workflowIn", "Interface_workflow_in", "IN",
                activationTriggers("aggregate")));
        ArrayNode outputTriggers = JsonNodeSupport.arrayNode();
        outputTriggers.add(trigger("aggregate.emitActive", "nodeLifecycleState", "=", "RUNNING",
                emitAction("Interface_workflow_out", WorkflowNodeSignal.ACTIVE.name())));
        template.withArray("interfaces").add(workflowInterface(
                "aggregate.workflowOut", "Interface_workflow_out", "OUT", outputTriggers));
        addActions(template, WorkflowNodeActionType.UPDATE, WorkflowNodeActionType.EMIT);
        return template;
    }

    private static ObjectNode deviceTemplate() {
        ObjectNode template = baseTemplate();
        ArrayNode workflowTriggers = JsonNodeSupport.arrayNode();
        workflowTriggers.add(trigger("device.workflowStart", "signalName", "=",
                WorkflowNodeSignal.ACTIVE.name(), updateLifecycleAction("RUNNING")));
        ArrayNode stateOutputTriggers = JsonNodeSupport.arrayNode();
        stateOutputTriggers.add(trigger("device.execute", "nodeLifecycleState", "=", "RUNNING",
                emitAction("Interface_state_out", WorkflowControlSignal.WF_EXECUTE_START.name())));
        ArrayNode stateTriggers = JsonNodeSupport.arrayNode();
        stateTriggers.add(andTrigger("device.stateCompleted", updateLifecycleAction("SUCCEEDED"),
                predicate("nodeLifecycleState", "=", "RUNNING"),
                predicate("signalName", "=", StatusSignal.CMD_STATE.name()),
                predicate("payload.stateName", "=", "COMPLETED")));
        ArrayNode workflowOutputTriggers = JsonNodeSupport.arrayNode();
        workflowOutputTriggers.add(trigger("device.completeNode", "nodeLifecycleState", "=", "SUCCEEDED",
                emitAction("Interface_workflow_out", WorkflowNodeSignal.ACTIVE.name())));
        template.withArray("interfaces").add(workflowInterface("device.workflowIn", "Interface_workflow_in", "IN", workflowTriggers));
        template.withArray("interfaces").add(interfaceDefinition("device.stateOut", "Interface_state_out", "OUT", "STATE",
                List.of(WorkflowControlSignal.WF_EXECUTE_START.name()), stateOutputTriggers));
        template.withArray("interfaces").add(interfaceDefinition("device.stateIn", "Interface_state_in", "IN", "STATE",
                List.of(StatusSignal.CMD_STATE.name()), stateTriggers));
        template.withArray("interfaces").add(workflowInterface(
                "device.workflowOut", "Interface_workflow_out", "OUT", workflowOutputTriggers));
        addActions(template, WorkflowNodeActionType.UPDATE, WorkflowNodeActionType.EMIT);
        return template;
    }

    private static ObjectNode subflowTemplate() {
        ObjectNode template = baseTemplate();
        ArrayNode inputTriggers = JsonNodeSupport.arrayNode();
        inputTriggers.add(trigger("subflow.activate", "signalName", "=", WorkflowNodeSignal.ACTIVE.name(),
                updateLifecycleAction("RUNNING")));
        inputTriggers.add(trigger("subflow.childCompleted", "signalName", "=",
                WorkflowNodeSignal.SUBFLOW_COMPLETED.name(), updateLifecycleAction("SUCCEEDED")));
        ArrayNode outputTriggers = JsonNodeSupport.arrayNode();
        outputTriggers.add(trigger("subflow.complete", "nodeLifecycleState", "=", "SUCCEEDED",
                emitAction("Interface_workflow_out", WorkflowNodeSignal.ACTIVE.name())));
        template.withArray("interfaces").add(interfaceDefinition(
                "subflow.workflowIn", "Interface_workflow_in", "IN", "WORKFLOW",
                List.of(WorkflowNodeSignal.ACTIVE.name(), WorkflowNodeSignal.SUBFLOW_COMPLETED.name()), inputTriggers));
        template.withArray("interfaces").add(workflowInterface(
                "subflow.workflowOut", "Interface_workflow_out", "OUT", outputTriggers));
        addActions(template, WorkflowNodeActionType.UPDATE, WorkflowNodeActionType.EMIT);
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
        return interfaceDefinition(systemKey, name, direction, "WORKFLOW",
                List.of(WorkflowNodeSignal.ACTIVE.name()), triggers);
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

    private static ArrayNode activationTriggers(String prefix) {
        ArrayNode triggers = JsonNodeSupport.arrayNode();
        triggers.add(trigger(prefix + ".activate", "signalName", "=", WorkflowNodeSignal.ACTIVE.name(),
                updateLifecycleAction("RUNNING")));
        return triggers;
    }

    private static ObjectNode trigger(String systemKey, String object, String operator, Object threshold,
                                      ObjectNode action) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.set("action", action);
        ObjectNode condition = definition.putObject("condition");
        condition.put("object", object);
        condition.put("operator", operator);
        if (threshold instanceof Boolean value) condition.put("threshold", value);
        else condition.put("threshold", (String) threshold);
        return system(systemKey, definition);
    }

    private static ObjectNode andTrigger(String systemKey, ObjectNode action, ObjectNode... predicates) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.set("action", action);
        ObjectNode condition = definition.putObject("condition");
        condition.put("logic", "AND");
        ArrayNode conditions = condition.putArray("conditions");
        for (ObjectNode predicate : predicates) conditions.add(predicate);
        return system(systemKey, definition);
    }

    private static ObjectNode predicate(String object, String operator, Object threshold) {
        ObjectNode condition = JsonNodeSupport.objectNode();
        condition.put("object", object);
        condition.put("operator", operator);
        if (threshold instanceof Boolean value) condition.put("threshold", value);
        else condition.put("threshold", String.valueOf(threshold));
        return condition;
    }

    private static ObjectNode emitAction(String targetInterfaceName, String signalName) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.put("actionName", WorkflowNodeActionType.EMIT.name());
        definition.putObject("payload")
                .put("targetInterfaceName", targetInterfaceName)
                .put("signalName", signalName);
        return definition;
    }

    private static ObjectNode updateLifecycleAction(String targetState) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.put("actionName", WorkflowNodeActionType.UPDATE.name());
        definition.putObject("payload")
                .put("updateType", "NODE_LIFECYCLE")
                .put("targetName", targetState);
        return definition;
    }

    private static void addActions(ObjectNode template, WorkflowNodeActionType... actions) {
        ArrayNode definitions = template.withArray("actions");
        for (WorkflowNodeActionType action : actions) definitions.add(action.name());
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
