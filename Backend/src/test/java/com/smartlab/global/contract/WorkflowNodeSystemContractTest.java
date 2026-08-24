package com.smartlab.global.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowNodeSystemContractTest {
    private static final List<String> LIFECYCLE_STATES =
            List.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "TERMINATING", "TERMINATED");
    private static final List<String> LIFECYCLE_TRANSITIONS = List.of(
            "PENDING->RUNNING", "PENDING->TERMINATED", "RUNNING->SUCCEEDED",
            "RUNNING->FAILED", "RUNNING->TERMINATING", "TERMINATING->TERMINATED",
            "TERMINATING->FAILED");

    @Test
    void exposesAllStableTemplateKeys() {
        assertEquals(List.of("START", "END", "BRANCH", "AGGREGATE", "DEV_NODE", "SUBFLOW_NODE"),
                WorkflowNodeSystemContract.templates().properties().stream().map(Map.Entry::getKey).toList());
    }

    @Test
    void allTriggerConditionsUseCanonicalInterfaceLocalSignalNames() {
        JsonNode templates = WorkflowNodeSystemContract.templates();

        assertTrue(templates.findValues("object").stream()
                .map(JsonNode::asText)
                .noneMatch(value -> value.equals("inputSignalName") || value.startsWith("inputPayload")));
        assertTrue(templates.findValues("object").stream()
                .map(JsonNode::asText)
                .anyMatch(value -> value.equals("signalName")));
        assertTrue(templates.findValues("object").stream()
                .map(JsonNode::asText)
                .anyMatch(value -> value.equals("taskLifecycleState")));
    }

    @Test
    void startTemplateHasOnlyWorkflowOutputAndCanonicalActions() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "START");
        JsonNode output = interfaceByName(template, "Interface_workflow_out");
        assertEquals(List.of("Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals("OUT", output.path("direction").asText());
        assertEquals(List.of("start.pendingTerminate", "start.runningTerminate", "start.terminatingComplete",
                        "start.begin", "start.complete"),
                triggerKeys(output));
        JsonNode complete = triggerByKey(output, "start.complete");
        assertEquals("SUCCEEDED", complete.path("action").path("payload").path("targetName").asText());
        assertEquals(List.of("UPDATE", "EMIT"), actionNames(complete));
        assertEquals("ACTIVE", complete.path("actions").get(1).path("payload").path("signalName").asText());
        assertEquals("start.workflowOut", output.path("_systemKey").asText());
    }

    @Test
    void endTemplateHasOnlyWorkflowInputAndLifecycleUpdates() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "END");
        JsonNode input = interfaceByName(template, "Interface_workflow_in");
        assertEquals(List.of("Interface_workflow_in"), names(template.path("interfaces")));
        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals(List.of("end.pendingTerminate", "end.runningTerminate", "end.terminatingComplete",
                        "end.activate", "end.complete"),
                triggerKeys(input));
        assertEquals("IN", input.path("direction").asText());
        assertEquals("end.workflowIn", input.path("_systemKey").asText());
    }

    @Test
    void branchTemplateLeavesOutputInterfacesAndRoutingTriggersToTheUser() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "BRANCH");
        JsonNode input = template.path("interfaces").get(0);
        assertEquals(List.of("Interface_workflow_in"), names(template.path("interfaces")));
        assertEquals(List.of("branch.pendingTerminate", "branch.runningTerminate", "branch.terminatingComplete",
                        "branch.activate"),
                triggerKeys(input));
        assertEquals("RUNNING", triggerByKey(input, "branch.activate")
                .path("action").path("payload").path("targetName").asText());
        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals(List.of("branch.workflowIn"), directSystemKeys(template.path("interfaces")));
    }

    @Test
    void aggregateTemplateCountsEachInputAndLeavesCompletionRuleToTheUser() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "AGGREGATE");
        JsonNode input = interfaceByName(template, "Interface_workflow_in");
        JsonNode output = interfaceByName(template, "Interface_workflow_out");
        JsonNode count = triggerByKey(input, "aggregate.countInput");
        JsonNode activate = triggerByKey(input, "aggregate.activate");
        assertEquals(List.of("Interface_workflow_in", "Interface_workflow_out"), names(template.path("interfaces")));
        JsonNode aggregateCount = findBy(template.path("internalVariables"), "name", "aggregateCount");
        assertEquals("INTEGER", aggregateCount.path("dataType").asText());
        assertEquals(0, aggregateCount.path("initialValue").asInt());
        assertEquals("aggregate.count", aggregateCount.path("_systemKey").asText());
        assertEquals(List.of("ACTIVE"), textValues(input.path("allowedSignals")));
        assertEquals("ACTIVE", count.path("condition").path("threshold").asText());
        assertEquals("aggregateCount", count.path("action").path("payload").path("targetName").asText());
        assertEquals("aggregateCount + 1", count.path("action").path("payload").path("valueExpression").asText());
        assertEquals("AND", activate.path("condition").path("logic").asText());
        assertEquals(List.of("aggregateCount", "nodeLifecycleState"),
                activate.path("condition").path("conditions").findValuesAsText("object"));
        assertEquals(List.of("aggregate.pendingTerminate", "aggregate.runningTerminate",
                        "aggregate.terminatingComplete", "aggregate.countInput", "aggregate.activate"),
                triggerKeys(input));
        assertEquals(List.of("ACTIVE"), textValues(output.path("allowedSignals")));
        assertTrue(output.path("bindingTriggers").isEmpty());
    }

    @Test
    void deviceTemplateContainsStateMachineInvocationAndCompletionFeedback() {
        JsonNode template = WorkflowNodeSystemContract.template("DEV_NODE", null);
        assertEquals(List.of("Interface_workflow_in", "Interface_state_out",
                        "Interface_state_in", "Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals(List.of("WF_EXECUTE_START", "WF_EXECUTE_ABORT"),
                textValues(interfaceByName(template, "Interface_state_out").path("allowedSignals")));
        assertEquals(List.of("CMD_STATE"),
                textValues(interfaceByName(template, "Interface_state_in").path("allowedSignals")));
        assertEquals(List.of("device.pendingTerminate", "device.runningTerminate", "device.workflowStart"),
                triggerKeys(interfaceByName(template, "Interface_workflow_in")));
        assertEquals(List.of("device.abort", "device.execute"),
                triggerKeys(interfaceByName(template, "Interface_state_out")));
        assertEquals("UPDATE", triggerByKey(interfaceByName(template, "Interface_workflow_in"), "device.pendingTerminate")
                .path("action").path("actionName").asText());
        assertEquals("UPDATE", triggerByKey(interfaceByName(template, "Interface_state_in"), "device.stateCompleted")
                .path("action").path("actionName").asText());
        assertEquals(List.of("device.workflowIn", "device.stateOut", "device.stateIn", "device.workflowOut"),
                directSystemKeys(template.path("interfaces")));
    }

    @Test
    void deviceTemplateUsesLifecycleUpdatesAndInlineActions() {
        JsonNode template = WorkflowNodeSystemContract.template("DEV_NODE", null);

        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals("UPDATE", triggerByKey(interfaceByName(template, "Interface_workflow_in"), "device.runningTerminate")
                .path("action").path("actionName").asText());
        assertEquals("TERMINATING", triggerByKey(interfaceByName(template, "Interface_workflow_in"), "device.runningTerminate")
                .path("action").path("payload").path("targetName").asText());
        assertEquals("UPDATE", triggerByKey(interfaceByName(template, "Interface_workflow_in"), "device.workflowStart")
                .path("action").path("actionName").asText());
        assertEquals("RUNNING", triggerByKey(interfaceByName(template, "Interface_workflow_in"), "device.workflowStart")
                .path("action").path("payload").path("targetName").asText());
        assertEquals("EMIT", triggerByKey(interfaceByName(template, "Interface_state_out"), "device.execute")
                .path("action").path("actionName").asText());
        assertEquals("WF_EXECUTE_START", triggerByKey(interfaceByName(template, "Interface_state_out"), "device.execute")
                .path("action").path("payload").path("signalName").asText());
        assertEquals("WF_EXECUTE_ABORT", triggerByKey(interfaceByName(template, "Interface_state_out"), "device.abort")
                .path("action").path("payload").path("signalName").asText());
        assertEquals("UPDATE", triggerByKey(interfaceByName(template, "Interface_state_in"), "device.stateCompleted")
                .path("action").path("actionName").asText());
        assertEquals("SUCCEEDED", triggerByKey(interfaceByName(template, "Interface_state_in"), "device.stateCompleted")
                .path("action").path("payload").path("targetName").asText());
        JsonNode completionCondition = triggerByKey(interfaceByName(template, "Interface_state_in"), "device.stateCompleted")
                .path("condition");
        assertEquals("AND", completionCondition.path("logic").asText());
        assertEquals(List.of("nodeLifecycleState", "signalName", "payload.stateName"),
                completionCondition.path("conditions").findValuesAsText("object"));
        assertEquals(List.of("RUNNING", "CMD_STATE", "COMPLETED"),
                completionCondition.path("conditions").findValuesAsText("threshold"));
        assertEquals("EMIT", triggerByKey(interfaceByName(template, "Interface_workflow_out"), "device.completeNode")
                .path("action").path("actionName").asText());
        assertTrue(template.findValues("actionType").isEmpty());
    }

    @Test
    void subflowTemplateUsesLifecycleAndEmitCapabilities() {
        JsonNode template = WorkflowNodeSystemContract.template("SUBFLOW_NODE", null);
        JsonNode input = interfaceByName(template, "Interface_workflow_in");
        assertEquals(List.of("Interface_workflow_in", "Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals(List.of("ACTIVE", "SUBFLOW_COMPLETED"), textValues(input.path("allowedSignals")));
        assertEquals(List.of("subflow.pendingTerminate", "subflow.runningTerminate", "subflow.terminatingComplete",
                        "subflow.activate", "subflow.childCompleted"),
                triggerKeys(input));
        assertEquals("RUNNING", triggerByKey(input, "subflow.activate")
                .path("action").path("payload").path("targetName").asText());
        assertEquals("SUCCEEDED", triggerByKey(input, "subflow.childCompleted")
                .path("action").path("payload").path("targetName").asText());
        assertEquals(List.of("subflow.workflowIn", "subflow.workflowOut"),
                directSystemKeys(template.path("interfaces")));
    }

    @Test
    void everyTemplateHasCompleteLifecycleAndSystemMarkers() {
        WorkflowNodeSystemContract.templates().properties().forEach(entry -> {
            JsonNode template = entry.getValue();
            assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
            JsonNode lifecycle = template.path("lifecycle");
            assertSystemMarker(lifecycle);
            assertEquals("PENDING", lifecycle.path("initialStateName").asText());
            assertEquals(LIFECYCLE_STATES, textValues(lifecycle.path("states")));
            assertEquals(LIFECYCLE_TRANSITIONS, lifecycleTransitions(lifecycle.path("transitions")));
            template.path("interfaces").forEach(item -> {
                assertSystemMarker(item);
                item.path("bindingTriggers").forEach(this::assertSystemMarker);
            });
            template.path("actions").forEach(action -> assertTrue(action.isTextual()));
            lifecycle.path("transitions").forEach(this::assertSystemMarker);
        });
    }

    private void assertSystemMarker(JsonNode item) {
        assertTrue(item.path("_system").isBoolean());
        assertTrue(item.path("_system").asBoolean());
        assertTrue(item.path("_systemKey").isTextual());
        assertFalse(item.path("_systemKey").asText().isBlank());
    }

    private List<String> names(JsonNode items) {
        return items.findValuesAsText("name");
    }

    private List<String> directSystemKeys(JsonNode items) {
        List<String> values = new ArrayList<>();
        items.forEach(item -> values.add(item.path("_systemKey").asText()));
        return values;
    }

    private List<String> textValues(JsonNode items) {
        List<String> values = new ArrayList<>();
        items.forEach(item -> values.add(item.asText()));
        return values;
    }

    private List<String> lifecycleTransitions(JsonNode transitions) {
        List<String> values = new ArrayList<>();
        transitions.forEach(item -> values.add(item.path("fromStateName").asText()
                + "->" + item.path("toStateName").asText()));
        return values;
    }

    private JsonNode interfaceByName(JsonNode template, String name) {
        return findBy(template.path("interfaces"), "name", name);
    }

    private JsonNode firstTrigger(JsonNode template, String interfaceName) {
        return interfaceByName(template, interfaceName).path("bindingTriggers").get(0);
    }

    private JsonNode triggerByKey(JsonNode interfaceNode, String systemKey) {
        for (JsonNode trigger : interfaceNode.path("bindingTriggers")) {
            if (systemKey.equals(trigger.path("_systemKey").asText())) return trigger;
        }
        throw new AssertionError("missing trigger " + systemKey);
    }

    private List<String> triggerKeys(JsonNode interfaceNode) {
        List<String> values = new ArrayList<>();
        interfaceNode.path("bindingTriggers").forEach(trigger -> values.add(trigger.path("_systemKey").asText()));
        return values;
    }

    private List<String> actionNames(JsonNode trigger) {
        List<String> values = new ArrayList<>();
        JsonNode actions = trigger.path("actions");
        if (actions.isArray() && !actions.isEmpty()) {
            actions.forEach(item -> values.add(item.path("actionName").asText()));
            return values;
        }
        values.add(trigger.path("action").path("actionName").asText());
        return values;
    }

    private JsonNode firstAction(JsonNode template, String interfaceName, String actionName) {
        for (JsonNode trigger : interfaceByName(template, interfaceName).path("bindingTriggers")) {
            if (actionName.equals(trigger.path("action").path("actionName").asText())) {
                return trigger.path("action");
            }
        }
        throw new AssertionError("missing inline action=" + actionName);
    }

    private List<String> triggerThresholds(JsonNode interfaceNode) {
        List<String> values = new ArrayList<>();
        interfaceNode.path("bindingTriggers").forEach(trigger ->
                values.add(trigger.path("condition").path("threshold").asText()));
        return values;
    }

    private List<String> inputTriggerTargets(JsonNode template) {
        JsonNode input = interfaceByName(template, "Interface_workflow_in");
        return input.path("bindingTriggers").findValues("targetName").stream()
                .map(JsonNode::asText)
                .toList();
    }

    private JsonNode findBy(JsonNode items, String field, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.path(field).asText())) return item;
        }
        throw new AssertionError("missing " + field + "=" + value);
    }
}
