package com.smartlab.global.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMetadataServiceTest {

    private final SchemaMetadataService service = new SchemaMetadataService();

    @Test
    void exposesFrontendMetadataBySchemaResponsibility() {
        ObjectNode metadata = service.frontendMetadata();

        assertTrue(metadata.path("protocol").path("mqttTopics").has("commandTopic"));
        assertTrue(metadata.path("protocol").path("adapterRegisterFormats").isArray());
        assertTrue(metadata.path("protocol").path("communicationProtocols").isArray());
        assertFalse(metadata.has("adapterConfig"));
        assertTrue(metadata.path("stateMachine").path("interfaceTypes").isArray());
        assertTrue(metadata.path("stateMachine").path("commandStateNames").isArray());
        assertTrue(metadata.path("workflow").path("functionTypes").isArray());
        assertEquals(
                List.of("IDLE", "SENT", "RECEIVED", "RUNNING", "COMPLETED", "ABORTED", "FAILED"),
                JsonNodeSupport.MAPPER.convertValue(
                        metadata.path("stateMachine").path("commandStateNames"),
                        new TypeReference<List<String>>() {}));
        assertTrue(metadata.path("stateMachine").path("executionLifecycleMainPath").isArray());
        assertTrue(metadata.path("stateMachine").path("systemTransitions").isArray());
        assertTrue(metadata.path("workflow").path("nodeLifecycleEvents").isArray());
        assertTrue(metadata.path("constraint").path("operators").isArray());
    }

    @Test
    void protocolMetadataDoesNotExposeStateMachineInternalDictionaries() {
        ObjectNode metadata = service.frontendMetadata();

        assertFalse(metadata.path("protocol").has("systemInterfaceNames"));
        assertFalse(metadata.path("protocol").has("systemInterfaceTypes"));
        assertFalse(metadata.path("protocol").has("statusSignals"));
        assertFalse(metadata.path("protocol").has("commandLifecycleStates"));
    }

    @Test
    void exposesDomainActionCatalogsInsteadOfProtocolActionEnums() {
        ObjectNode metadata = service.frontendMetadata();

        assertFalse(metadata.path("protocol").has("stateMachineActionNames"));
        assertEquals(List.of("SEND"), JsonNodeSupport.MAPPER.convertValue(
                metadata.path("stateMachine").path("actionCatalog").findValuesAsText("actionName"),
                new TypeReference<List<String>>() {}));
        assertEquals(List.of("WAIT", "ASSIGN", "CALCULATE", "EMIT_SIGNAL"),
                JsonNodeSupport.MAPPER.convertValue(
                        metadata.path("workflow").path("actionCatalog").findValuesAsText("actionName"),
                        new TypeReference<List<String>>() {}));
        assertEquals(List.of("ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "MOD", "MIN", "MAX", "ROUND"),
                JsonNodeSupport.MAPPER.convertValue(
                        metadata.path("workflow").path("calculationOperators"),
                        new TypeReference<List<String>>() {}));
    }
}
