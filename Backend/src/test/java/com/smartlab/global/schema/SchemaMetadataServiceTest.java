package com.smartlab.global.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaMetadataServiceTest {

    private final SchemaMetadataService service = new SchemaMetadataService(new ProtocolDictionaryService());

    @Test
    void exposesWorkflowSignalCandidatesFromProtocolContract() {
        JsonNode protocol = service.frontendMetadata().path("protocol");

        assertEquals(List.of("ACTIVE", "SUBFLOW_COMPLETED"), values(protocol, "workflowNodeSignals"));
        assertEquals(List.of("WF_EXECUTE_START", "WF_EXECUTE_ABORT"), values(protocol, "workflowControlSignals"));
        assertEquals(List.of("CMD_STATE", "OP_STATE"), values(protocol, "statusSignals"));
        assertEquals(List.of("+", "-", "*", "/", "(", ")"),
                values(service.frontendMetadata().path("workflow"), "calculationOperators"));
        assertEquals(List.of("rate", "delta", "avg", "max", "min", "abs"),
                StreamSupport.stream(service.frontendMetadata().path("workflow").path("temporalFunctions").spliterator(), false)
                        .map(item -> item.path("functionName").asText()).toList());
    }

    private List<String> values(JsonNode parent, String field) {
        return StreamSupport.stream(parent.path(field).spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }
}
