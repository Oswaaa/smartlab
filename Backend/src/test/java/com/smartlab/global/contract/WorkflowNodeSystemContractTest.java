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
                .anyMatch(value -> value.equals("payload.stateName")));
    }

    @Test
    void startTemplateHasOnlyWorkflowOutputAndCanonicalActions() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "START");
        assertEquals(List.of("Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals("OUT", interfaceByName(template, "Interface_workflow_out").path("direction").asText());
        assertEquals(List.of("PENDING", "RUNNING", "RUNNING"), triggerThresholds(
                interfaceByName(template, "Interface_workflow_out")));
        assertEquals("ACTIVE", firstAction(template, "Interface_workflow_out", "EMIT")
                .path("payload").path("signalName").asText());
        assertEquals("start.workflowOut", interfaceByName(template, "Interface_workflow_out").path("_systemKey").asText());
    }

    @Test
    void endTemplateHasOnlyWorkflowInputAndLifecycleUpdates() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "END");
        assertEquals(List.of("Interface_workflow_in"), names(template.path("interfaces")));
        assertEquals(List.of("UPDATE"), textValues(template.path("actions")));
        assertEquals(List.of("ACTIVE", "RUNNING"), triggerThresholds(
                interfaceByName(template, "Interface_workflow_in")));
        assertEquals("IN", interfaceByName(template, "Interface_workflow_in").path("direction").asText());
        assertEquals("end.workflowIn", interfaceByName(template, "Interface_workflow_in").path("_systemKey").asText());
    }

    @Test
    void branchTemplateLeavesOutputInterfacesAndRoutingTriggersToTheUser() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "BRANCH");
        assertEquals(List.of("Interface_workflow_in"), names(template.path("interfaces")));
        assertEquals(List.of("ACTIVE", "RUNNING"), triggerThresholds(template.path("interfaces").get(0)));
        assertEquals(List.of("UPDATE"), textValues(template.path("actions")));
        assertEquals(List.of("branch.workflowIn"), directSystemKeys(template.path("interfaces")));
    }

    @Test
    void aggregateTemplateReceivesAndEmitsActive() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "AGGREGATE");
        JsonNode input = interfaceByName(template, "Interface_workflow_in");
        JsonNode output = interfaceByName(template, "Interface_workflow_out");
        assertEquals(List.of("Interface_workflow_in", "Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("ACTIVE"), textValues(input.path("allowedSignals")));
        assertEquals("ACTIVE", input.path("bindingTriggers").get(0).path("condition").path("threshold").asText());
        assertEquals(List.of("ACTIVE"), textValues(output.path("allowedSignals")));
        assertEquals("ACTIVE", firstAction(template, "Interface_workflow_out", "EMIT")
                .path("payload").path("signalName").asText());
        assertEquals("aggregate.activate", input.path("bindingTriggers").get(0).path("_systemKey").asText());
        assertEquals("aggregate.emitActive", output.path("bindingTriggers").get(0).path("_systemKey").asText());
    }

    @Test
    void deviceTemplateContainsStateMachineInvocationAndCompletionFeedback() {
        JsonNode template = WorkflowNodeSystemContract.template("DEV_NODE", null);
        assertEquals(List.of("Interface_workflow_in", "Interface_state_out",
                        "Interface_state_in", "Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals(List.of("WF_EXECUTE_START"),
                textValues(interfaceByName(template, "Interface_state_out").path("allowedSignals")));
        assertEquals(List.of("CMD_STATE", "OP_STATE"),
                textValues(interfaceByName(template, "Interface_state_in").path("allowedSignals")));
        assertEquals("UPDATE", interfaceByName(template, "Interface_workflow_in")
                .path("bindingTriggers").get(0).path("action").path("actionName").asText());
        assertEquals("UPDATE", interfaceByName(template, "Interface_state_in")
                .path("bindingTriggers").get(0).path("action").path("actionName").asText());
        assertEquals(List.of("device.workflowIn", "device.stateOut", "device.stateIn", "device.workflowOut"),
                directSystemKeys(template.path("interfaces")));
    }

    @Test
    void deviceTemplateUsesLifecycleUpdatesAndInlineActions() {
        JsonNode template = WorkflowNodeSystemContract.template("DEV_NODE", null);

        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals("UPDATE", firstTrigger(template, "Interface_workflow_in")
                .path("action").path("actionName").asText());
        assertEquals("RUNNING", firstTrigger(template, "Interface_workflow_in")
                .path("action").path("payload").path("targetName").asText());
        assertEquals("EMIT", firstTrigger(template, "Interface_state_out")
                .path("action").path("actionName").asText());
        assertEquals("WF_EXECUTE_START", firstTrigger(template, "Interface_state_out")
                .path("action").path("payload").path("signalName").asText());
        assertEquals("UPDATE", firstTrigger(template, "Interface_state_in")
                .path("action").path("actionName").asText());
        assertEquals("SUCCEEDED", firstTrigger(template, "Interface_state_in")
                .path("action").path("payload").path("targetName").asText());
        assertEquals("EMIT", firstTrigger(template, "Interface_workflow_out")
                .path("action").path("actionName").asText());
        assertTrue(template.findValues("actionType").isEmpty());
    }

    @Test
    void subflowTemplateUsesLifecycleAndEmitCapabilities() {
        JsonNode template = WorkflowNodeSystemContract.template("SUBFLOW_NODE", null);
        assertEquals(List.of("Interface_workflow_in", "Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("UPDATE", "EMIT"), textValues(template.path("actions")));
        assertEquals(List.of("subflow.workflowIn", "subflow.workflowOut"),
                directSystemKeys(template.path("interfaces")));
    }

    @Test
    void everyTemplateHasCompleteLifecycleAndSystemMarkers() {
        WorkflowNodeSystemContract.templates().properties().forEach(entry -> {
            JsonNode template = entry.getValue();
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

    private JsonNode findBy(JsonNode items, String field, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.path(field).asText())) return item;
        }
        throw new AssertionError("missing " + field + "=" + value);
    }
}
