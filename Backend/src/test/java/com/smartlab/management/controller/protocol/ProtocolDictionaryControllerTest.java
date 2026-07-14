package com.smartlab.management.controller.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.management.dto.common.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolDictionaryControllerTest {

    @Test
    void returnsFrontendProtocolMetadata() {
        ProtocolDictionaryController controller = new ProtocolDictionaryController(new ProtocolDictionaryService());

        ApiResponse<ObjectNode> response = controller.frontendMetadata();

        assertTrue(response.isSuccess());
        assertTrue(response.getData().path("dataTypes").isArray());
        assertTrue(response.getData().path("mqttTopics").has("registerTopic"));
    }
}
