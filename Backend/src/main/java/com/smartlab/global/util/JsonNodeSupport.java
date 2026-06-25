package com.smartlab.global.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonNodeSupport {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNodeSupport() {
    }

    public static JsonNode toNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        return MAPPER.valueToTree(value);
    }

    public static ObjectNode objectNode() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arrayNode() {
        return MAPPER.createArrayNode();
    }
}
