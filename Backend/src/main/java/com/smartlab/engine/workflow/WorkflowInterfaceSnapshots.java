package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;

/** 工作流节点接口当前值数组的唯一读写边界。 */
public final class WorkflowInterfaceSnapshots {

    private WorkflowInterfaceSnapshots() {
    }

    public static ArrayNode initialize(JsonNode interfaces, String direction) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        if (interfaces == null || !interfaces.isArray()) return result;
        for (JsonNode definition : interfaces) {
            if (!direction.equals(definition.path("direction").asText())) continue;
            String interfaceName = definition.path("name").asText("");
            if (interfaceName.isBlank()) {
                throw new IllegalArgumentException("节点接口缺少name");
            }
            ObjectNode slot = result.addObject();
            slot.put("interfaceName", interfaceName);
            slot.putNull("signalName");
        }
        return result;
    }

    public static ArrayNode withSignal(JsonNode current, JsonNode interfaces, String direction,
                                       String interfaceName, String signalName, JsonNode payload) {
        JsonNode definition = requireDeclaredInterface(interfaces, direction, interfaceName);
        if (signalName != null && !allows(definition, signalName)) {
            throw new IllegalArgumentException("接口不允许信号: " + interfaceName + "." + signalName);
        }
        ArrayNode result = current != null && current.isArray()
                ? (ArrayNode) current.deepCopy()
                : initialize(interfaces, direction);
        JsonNode found = find(result, interfaceName);
        if (!found.isObject()) {
            throw new IllegalStateException("接口快照缺少接口: " + interfaceName);
        }
        ObjectNode slot = (ObjectNode) found;
        if (signalName == null) slot.putNull("signalName");
        else slot.put("signalName", signalName);
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            slot.remove("payload");
        } else if (payload.isObject()) {
            slot.set("payload", payload.deepCopy());
        } else {
            throw new IllegalArgumentException("接口信号payload必须是对象");
        }
        return result;
    }

    public static JsonNode find(JsonNode snapshot, String interfaceName) {
        if (snapshot != null && snapshot.isArray()) {
            for (JsonNode slot : snapshot) {
                if (interfaceName.equals(slot.path("interfaceName").asText())) return slot;
            }
        }
        return MissingNode.getInstance();
    }

    private static JsonNode requireDeclaredInterface(JsonNode interfaces, String direction, String interfaceName) {
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode definition : interfaces) {
                if (interfaceName.equals(definition.path("name").asText())
                        && direction.equals(definition.path("direction").asText())) {
                    return definition;
                }
            }
        }
        throw new IllegalArgumentException("节点未声明" + direction + "接口: " + interfaceName);
    }

    private static boolean allows(JsonNode interfaceDefinition, String signalName) {
        JsonNode allowedSignals = interfaceDefinition.path("allowedSignals");
        if (!allowedSignals.isArray()) return false;
        for (JsonNode allowed : allowedSignals) {
            if (signalName.equals(allowed.asText())) return true;
        }
        return false;
    }
}
