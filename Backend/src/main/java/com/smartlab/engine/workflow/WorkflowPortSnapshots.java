package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;

/** 工作流节点端口当前值数组的唯一读写边界。 */
public final class WorkflowPortSnapshots {

    private WorkflowPortSnapshots() {
    }

    public static ArrayNode initialize(JsonNode ports, String direction) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        if (ports == null || !ports.isArray()) return result;
        for (JsonNode definition : ports) {
            if (!direction.equals(definition.path("direction").asText())) continue;
            String portName = definition.path("name").asText("");
            if (portName.isBlank()) {
                throw new IllegalArgumentException("节点端口缺少name");
            }
            ObjectNode slot = result.addObject();
            slot.put("portName", portName);
            slot.putNull("value");
        }
        return result;
    }

    public static ArrayNode withValue(JsonNode current, JsonNode ports, String direction,
                                      String portName, JsonNode value) {
        requireDeclaredPort(ports, direction, portName);
        ArrayNode result = current != null && current.isArray()
                ? (ArrayNode) current.deepCopy()
                : initialize(ports, direction);
        JsonNode found = find(result, portName);
        if (!found.isObject()) {
            throw new IllegalStateException("端口快照缺少端口: " + portName);
        }
        ObjectNode slot = (ObjectNode) found;
        if (value == null || value.isNull() || value.isMissingNode()) {
            slot.putNull("value");
        } else {
            slot.set("value", value.deepCopy());
        }
        return result;
    }

    public static JsonNode find(JsonNode snapshot, String portName) {
        if (snapshot != null && snapshot.isArray()) {
            for (JsonNode slot : snapshot) {
                if (portName.equals(slot.path("portName").asText())) return slot;
            }
        }
        return MissingNode.getInstance();
    }

    public static JsonNode currentValue(JsonNode snapshot, String portName) {
        JsonNode slot = find(snapshot, portName);
        JsonNode value = slot.get("value");
        return value == null ? MissingNode.getInstance() : value;
    }

    private static void requireDeclaredPort(JsonNode ports, String direction, String portName) {
        if (ports != null && ports.isArray()) {
            for (JsonNode definition : ports) {
                if (portName.equals(definition.path("name").asText())
                        && direction.equals(definition.path("direction").asText())) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException("节点未声明" + direction + "端口: " + portName);
    }
}
