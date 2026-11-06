package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Operator 内部统一的 JSON 工具，避免各处重复抓 ObjectMapper。 */
public final class OperatorJson {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private OperatorJson() {
    }

    public static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    public static JsonNode node(Object value) {
        return MAPPER.valueToTree(value);
    }

    public static JsonNode schema(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception error) {
            throw new IllegalStateException("工具参数定义不合法: " + error.getMessage(), error);
        }
    }

    public static ObjectNode error(String code, String message) {
        ObjectNode node = obj();
        node.put("error", message);
        node.put("code", code);
        return node;
    }

    public static ObjectNode ok() {
        ObjectNode node = obj();
        node.put("ok", true);
        return node;
    }

    public static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public static Long longValue(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (value.isNumber()) return value.asLong();
        try {
            return Long.valueOf(value.asText().trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
