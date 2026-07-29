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
    void startTemplateHasOnlyWorkflowOutputAndEmitActive() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "START");
        assertEquals(List.of("Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("emitActive"), actionNames(template.path("actions")));
        assertEquals("OUT", interfaceByName(template, "Interface_workflow_out").path("direction").asText());
        assertEquals("ACTIVE", actionByName(template, "emitActive").path("signalName").asText());
        assertEquals("start.workflowOut", interfaceByName(template, "Interface_workflow_out").path("_systemKey").asText());
        assertEquals("start.emitActive", actionByName(template, "emitActive").path("_systemKey").asText());
    }

    @Test
    void endTemplateHasOnlyWorkflowInputAndNoEmit() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "END");
        assertEquals(List.of("Interface_workflow_in"), names(template.path("interfaces")));
        assertTrue(template.path("actions").isEmpty());
        assertEquals("IN", interfaceByName(template, "Interface_workflow_in").path("direction").asText());
        assertEquals("end.workflowIn", interfaceByName(template, "Interface_workflow_in").path("_systemKey").asText());
    }

    @Test
    void branchTemplateContainsMutuallyExclusiveOutputs() {
        JsonNode template = WorkflowNodeSystemContract.template("FUNC_NODE", "BRANCH");
        assertEquals(List.of("Interface_workflow_in", "Interface_true_out", "Interface_false_out"),
                names(template.path("interfaces")));
        assertEquals(List.of(true, false),
                template.path("interfaces").get(0).path("bindingTriggers").findValues("threshold")
                        .stream().map(JsonNode::asBoolean).toList());
        assertEquals(List.of("emitTrue", "emitFalse"), actionNames(template.path("actions")));
        assertEquals(List.of("branch.workflowIn", "branch.trueOut", "branch.falseOut"),
                directSystemKeys(template.path("interfaces")));
        assertEquals(List.of("branch.emitTrue", "branch.emitFalse"), directSystemKeys(template.path("actions")));
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
        assertEquals("ACTIVE", actionByName(template, "emitActive").path("signalName").asText());
        assertEquals("aggregate.active", input.path("bindingTriggers").get(0).path("_systemKey").asText());
        assertEquals("aggregate.emitActive", actionByName(template, "emitActive").path("_systemKey").asText());
    }

    @Test
    void deviceTemplateContainsStateMachineInvocationAndCompletionFeedback() {
        JsonNode template = WorkflowNodeSystemContract.template("DEV_NODE", null);
        assertEquals(List.of("Interface_workflow_in", "Interface_state_out",
                        "Interface_state_in", "Interface_workflow_out"), names(template.path("interfaces")));
        assertEquals(List.of("startDevice", "completeNode"), actionNames(template.path("actions")));
        assertEquals(List.of("WF_EXECUTE_START"),
                textValues(interfaceByName(template, "Interface_state_out").path("allowedSignals")));
        assertEquals(List.of("CMD_STATE", "OP_STATE"),
                textValues(interfaceByName(template, "Interface_state_in").path("allowedSignals")));
        assertEquals("startDevice", interfaceByName(template, "Interface_workflow_in")
                .path("bindingTriggers").get(0).path("action").asText());
        assertEquals("completeNode", interfaceByName(template, "Interface_state_in")
                .path("bindingTriggers").get(0).path("action").asText());
        assertEquals(List.of("device.workflowIn", "device.stateOut", "device.stateIn", "device.workflowOut"),
                directSystemKeys(template.path("interfaces")));
        assertEquals(List.of("device.startDevice", "device.completeNode"), directSystemKeys(template.path("actions")));
    }

    @Test
    void subflowTemplateContainsNoSystemEmitAction() {
        JsonNode template = WorkflowNodeSystemContract.template("SUBFLOW_NODE", null);
        assertEquals(List.of("Interface_workflow_in", "Interface_workflow_out"), names(template.path("interfaces")));
        assertTrue(template.path("actions").isEmpty());
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
            template.path("interfaces").forEach(this::assertSystemMarker);
            template.path("actions").forEach(this::assertSystemMarker);
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

    private List<String> actionNames(JsonNode items) {
        return items.findValuesAsText("actionName");
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

    private JsonNode actionByName(JsonNode template, String name) {
        return findBy(template.path("actions"), "actionName", name);
    }

    private JsonNode findBy(JsonNode items, String field, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.path(field).asText())) return item;
        }
        throw new AssertionError("missing " + field + "=" + value);
    }
}
