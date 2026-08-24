package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowPortSnapshotsTest {

    @Test
    void initializesEveryPortInModelOrderForTheRequestedDirection() {
        ArrayNode snapshot = WorkflowPortSnapshots.initialize(ports(), "OUT");

        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).path("portName").asText()).isEqualTo("temperatureOut");
        assertThat(snapshot.get(0).path("value").isNull()).isTrue();
    }

    @Test
    void replacesOnlyTheTargetPortCurrentValue() {
        ArrayNode initial = WorkflowPortSnapshots.initialize(ports(), "IN");

        ArrayNode updated = WorkflowPortSnapshots.withValue(
                initial, ports(), "IN", "temperatureIn", JsonNodeSupport.toNode(26.5));

        assertThat(updated).isNotSameAs(initial);
        JsonNode input = WorkflowPortSnapshots.find(updated, "temperatureIn");
        assertThat(input.path("value").decimalValue()).isEqualByComparingTo("26.5");
        assertThat(initial.get(0).path("value").isNull()).isTrue();
    }

    @Test
    void nullValueClearsThePreviousPortValue() {
        ArrayNode snapshot = WorkflowPortSnapshots.withValue(
                WorkflowPortSnapshots.initialize(ports(), "OUT"), ports(), "OUT",
                "temperatureOut", JsonNodeSupport.toNode(30));

        ArrayNode updated = WorkflowPortSnapshots.withValue(
                snapshot, ports(), "OUT", "temperatureOut", null);

        assertThat(WorkflowPortSnapshots.find(updated, "temperatureOut").path("value").isNull()).isTrue();
        assertThat(WorkflowPortSnapshots.currentValue(updated, "temperatureOut").isNull()).isTrue();
    }

    @Test
    void rejectsUnknownOrWrongDirectionPorts() {
        ArrayNode initial = WorkflowPortSnapshots.initialize(ports(), "IN");

        assertThatThrownBy(() -> WorkflowPortSnapshots.withValue(
                initial, ports(), "IN", "missing", JsonNodeSupport.toNode(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowPortSnapshots.withValue(
                initial, ports(), "IN", "temperatureOut", JsonNodeSupport.toNode(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ArrayNode ports() {
        ArrayNode result = JsonNodeSupport.arrayNode();
        addPort(result, "temperatureIn", "IN", "target");
        addPort(result, "temperatureOut", "OUT", "measured");
        return result;
    }

    private void addPort(ArrayNode ports, String name, String direction, String variableName) {
        ObjectNode definition = ports.addObject();
        definition.put("name", name);
        definition.put("direction", direction);
        definition.put("internalVariableName", variableName);
    }
}
