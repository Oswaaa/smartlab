package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowInterfaceSnapshotsTest {

    @Test
    void initializesEveryInterfaceInModelOrderForTheRequestedDirection() {
        ArrayNode snapshot = WorkflowInterfaceSnapshots.initialize(interfaces(), "IN");

        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.get(0).path("interfaceName").asText()).isEqualTo("workflow-in");
        assertThat(snapshot.get(1).path("interfaceName").asText()).isEqualTo("state-in");
        assertThat(snapshot.get(0).has("signalName")).isTrue();
        assertThat(snapshot.get(0).path("signalName").isNull()).isTrue();
        assertThat(snapshot.get(1).path("signalName").isNull()).isTrue();
    }

    @Test
    void replacesOnlyTheTargetInterfaceCurrentValue() {
        ArrayNode initial = WorkflowInterfaceSnapshots.initialize(interfaces(), "IN");
        ObjectNode payload = JsonNodeSupport.objectNode().put("stateName", "COMPLETED");

        ArrayNode updated = WorkflowInterfaceSnapshots.withSignal(
                initial, interfaces(), "IN", "state-in", "CMD_STATE", payload);

        assertThat(updated).isNotSameAs(initial);
        assertThat(updated.get(0)).isEqualTo(initial.get(0));
        JsonNode stateInput = WorkflowInterfaceSnapshots.find(updated, "state-in");
        assertThat(stateInput.path("signalName").asText()).isEqualTo("CMD_STATE");
        assertThat(stateInput.path("payload").path("stateName").asText()).isEqualTo("COMPLETED");
        assertThat(initial.get(1).path("signalName").isNull()).isTrue();
    }

    @Test
    void signalWithoutPayloadRemovesThePreviousPayload() {
        ArrayNode snapshot = WorkflowInterfaceSnapshots.withSignal(
                WorkflowInterfaceSnapshots.initialize(interfaces(), "IN"), interfaces(), "IN",
                "workflow-in", "ACTIVE", JsonNodeSupport.objectNode().put("old", true));

        ArrayNode updated = WorkflowInterfaceSnapshots.withSignal(
                snapshot, interfaces(), "IN", "workflow-in", "SUBFLOW_COMPLETED", null);

        JsonNode workflowInput = WorkflowInterfaceSnapshots.find(updated, "workflow-in");
        assertThat(workflowInput.path("signalName").asText()).isEqualTo("SUBFLOW_COMPLETED");
        assertThat(workflowInput.has("payload")).isFalse();
    }

    @Test
    void rejectsUnknownWrongDirectionAndDisallowedSignals() {
        ArrayNode initial = WorkflowInterfaceSnapshots.initialize(interfaces(), "IN");

        assertThatThrownBy(() -> WorkflowInterfaceSnapshots.withSignal(
                initial, interfaces(), "IN", "missing", "ACTIVE", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowInterfaceSnapshots.withSignal(
                initial, interfaces(), "IN", "workflow-out", "ACTIVE", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowInterfaceSnapshots.withSignal(
                initial, interfaces(), "IN", "state-in", "ACTIVE", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ArrayNode interfaces() {
        ArrayNode result = JsonNodeSupport.arrayNode();
        addInterface(result, "workflow-in", "IN", "ACTIVE", "SUBFLOW_COMPLETED");
        addInterface(result, "workflow-out", "OUT", "ACTIVE");
        addInterface(result, "state-in", "IN", "CMD_STATE", "OP_STATE");
        return result;
    }

    private void addInterface(ArrayNode interfaces, String name, String direction, String... allowedSignals) {
        ObjectNode definition = interfaces.addObject();
        definition.put("name", name);
        definition.put("direction", direction);
        ArrayNode allowed = definition.putArray("allowedSignals");
        for (String signal : allowedSignals) allowed.add(signal);
    }
}
