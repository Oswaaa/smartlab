package com.smartlab.management.controller.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.management.dto.common.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolDictionaryControllerTest {

    @Test
    void returnsProtocolDictionary() {
        ProtocolDictionaryController controller = new ProtocolDictionaryController(new ProtocolDictionaryService());

        ApiResponse<JsonNode> response = controller.dictionary();

        assertTrue(response.isSuccess());
        assertTrue(response.getData().path("definitions").has("MqttTopicConvention"));
        assertTrue(response.getData().path("definitions").has("LeaseRequestFormat"));
        assertTrue(response.getData().path("definitions").has("LeaseResultFormat"));
    }
}
