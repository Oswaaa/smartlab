package com.smartlab.management.controller.schema;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.schema.SchemaMetadataService;
import com.smartlab.management.dto.common.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMetadataControllerTest {

    @Test
    void returnsAggregatedFrontendSchemaMetadata() {
        SchemaMetadataController controller = new SchemaMetadataController(new SchemaMetadataService());

        ApiResponse<ObjectNode> response = controller.frontendMetadata();

        assertTrue(response.isSuccess());
        assertTrue(response.getData().path("protocol").path("mqttTopics").has("registerTopic"));
        assertTrue(response.getData().path("stateMachine").path("interfaceTypes").isArray());
        assertTrue(response.getData().path("deviceCapability").path("dataTypes").isArray());
        assertTrue(response.getData().path("protocol").path("communicationProtocols").isArray());
    }
}
