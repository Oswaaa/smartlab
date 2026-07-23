package com.smartlab.global.schema;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineInterfacePolicyServiceTest {

    private final StateMachineInterfacePolicyService service =
            new StateMachineInterfacePolicyService(new SchemaMetadataService());

    @Test
    void resolvesStandardInterfacesAndAdapterSpecificSignals() {
        ArrayNode events = JsonNodeSupport.arrayNode();
        events.addObject().put("name", "PRESSURE_ALARM");

        ArrayNode interfaces = service.standardInterfaces(events);

        assertEquals(6, interfaces.size());
        assertEquals(Set.of("PRESSURE_ALARM"),
                signals(interfaces, "Interface_adapter_in"));
        assertEquals(Set.of("OP_STATE", "CMD_STATE"),
                signals(interfaces, "Interface_status_out"));
        assertDoesNotThrow(() -> service.validateInterfaces(interfaces, events));
    }

    @Test
    void resolvesAdapterSpecificSignalsProvidedAsNames() {
        ArrayNode events = JsonNodeSupport.arrayNode();
        events.add("PRESSURE_ALARM");

        ArrayNode interfaces = service.standardInterfaces(events);

        assertEquals(Set.of("PRESSURE_ALARM"),
                signals(interfaces, "Interface_adapter_in"));
        assertDoesNotThrow(() -> service.validateInterfaces(interfaces, events));
    }
    @Test
    void rejectsSignalsThatRuntimeCannotProduce() {
        ArrayNode interfaces = service.standardInterfaces(JsonNodeSupport.arrayNode());
        for (var item : interfaces) {
            if ("Interface_workflow_in".equals(item.path("name").asText())) {
                ((ArrayNode) item.path("allowedSignals")).add("USER_DEFINED_SIGNAL");
            }
        }

        assertThrows(IllegalArgumentException.class,
                () -> service.validateInterfaces(interfaces, JsonNodeSupport.arrayNode()));
    }

    @Test
    void rejectsSendActionsTargetingInputInterfaces() {
        ArrayNode interfaces = service.standardInterfaces(JsonNodeSupport.arrayNode());
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        var transition = transitions.addObject();
        transition.putObject("trigger")
                .put("interfaceName", "Interface_workflow_in")
                .put("signalName", "WF_EXECUTE_START");
        transition.putArray("actions").addObject()
                .put("actionName", "SEND")
                .putObject("payload")
                .put("interfaceName", "Interface_adapter_in")
                .put("signalName", "PRESSURE_ALARM");

        assertThrows(IllegalArgumentException.class,
                () -> service.validateTransitions(transitions, interfaces));
    }

    @Test
    void rejectsTransitionWithoutExplicitStateSpace() {
        ArrayNode events = JsonNodeSupport.arrayNode();
        events.add("DONE");
        ArrayNode interfaces = service.standardInterfaces(events);
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        var transition = transitions.addObject();
        transition.put("fromStateName", "RUNNING");
        transition.put("toStateName", "COMPLETED");
        transition.putObject("trigger")
                .put("interfaceName", "Interface_adapter_in")
                .put("signalName", "DONE");

        assertThrows(IllegalArgumentException.class,
                () -> service.validateTransitions(transitions, interfaces));
    }

    @Test
    void rejectsTwoTargetsForTheSameStateAndTrigger() {
        ArrayNode events = JsonNodeSupport.arrayNode();
        events.add("DONE");
        ArrayNode interfaces = service.standardInterfaces(events);
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        addTransition(transitions, "CMD", "RUNNING", "COMPLETED", "DONE");
        addTransition(transitions, "CMD", "RUNNING", "FAILED", "DONE");

        assertThrows(IllegalArgumentException.class,
                () -> service.validateTransitions(transitions, interfaces));
    }

    @Test
    void acceptsOneAutomaticTransitionAndRejectsASecondTargetFromTheSameState() {
        ArrayNode interfaces = service.standardInterfaces(JsonNodeSupport.arrayNode());
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        addAutomaticTransition(transitions, "CMD", "SENT", "RECEIVED");
        assertDoesNotThrow(() -> service.validateTransitions(transitions, interfaces));

        addAutomaticTransition(transitions, "CMD", "SENT", "RUNNING");
        assertThrows(IllegalArgumentException.class,
                () -> service.validateTransitions(transitions, interfaces));
    }
    @Test
    void rejectsAutomaticOperationStateTransition() {
        ArrayNode interfaces = service.standardInterfaces(JsonNodeSupport.arrayNode());
        ArrayNode transitions = JsonNodeSupport.arrayNode();
        addAutomaticTransition(transitions, "OP", "IDLE", "RUNNING");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.validateTransitions(transitions, interfaces));
        assertTrue(error.getMessage().contains("CMD"));
    }


    private void addTransition(ArrayNode transitions, String stateSpace, String from, String to, String signal) {
        var transition = transitions.addObject();
        transition.put("stateSpace", stateSpace);
        transition.put("fromStateName", from);
        transition.put("toStateName", to);
        transition.putObject("trigger")
                .put("interfaceName", "Interface_adapter_in")
                .put("signalName", signal);
    }

    private void addAutomaticTransition(ArrayNode transitions, String stateSpace, String from, String to) {
        var transition = transitions.addObject();
        transition.put("stateSpace", stateSpace);
        transition.put("fromStateName", from);
        transition.put("toStateName", to);
        transition.putNull("trigger");
    }

    private Set<String> signals(ArrayNode interfaces, String name) {
        Set<String> values = new HashSet<>();
        for (var item : interfaces) {
            if (name.equals(item.path("name").asText())) {
                item.path("allowedSignals").forEach(value -> values.add(value.asText()));
            }
        }
        return values;
    }
}
