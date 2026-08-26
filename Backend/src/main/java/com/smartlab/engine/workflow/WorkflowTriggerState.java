package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorkflowTriggerState {
    public static final String TERMINAL_OBSERVED_KEY = "__terminalObserved";

    private WorkflowTriggerState() {
    }

    static String fingerprint(JsonNode trigger) {
        String canonical = canonical(trigger).toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JVM缺少SHA-256", error);
        }
    }

    static String key(String interfaceName, String fingerprint, int duplicateOrdinal) {
        return interfaceName + "::" + fingerprint + "::" + duplicateOrdinal;
    }

    /** UI 按当前 bindingTriggers 下标读取；一旦为 true 就不再清掉，表示曾经触发过。 */
    static String indexKey(String interfaceName, int index) {
        return interfaceName + "::__i::" + index;
    }

    static boolean rememberIndexFired(ObjectNode states, String interfaceName, int index, boolean currentlyHeld) {
        if (states == null || !currentlyHeld) return false;
        String key = indexKey(interfaceName, index);
        if (states.path(key).asBoolean(false)) return false;
        states.put(key, true);
        return true;
    }

    static boolean pruneIndexKeys(ObjectNode states, String interfaceName, int count) {
        if (states == null) return false;
        String prefix = interfaceName + "::__i::";
        List<String> remove = new ArrayList<>();
        states.fieldNames().forEachRemaining(name -> {
            if (!name.startsWith(prefix)) return;
            try {
                if (Integer.parseInt(name.substring(prefix.length())) >= count) remove.add(name);
            } catch (NumberFormatException ignored) {
                remove.add(name);
            }
        });
        remove.forEach(states::remove);
        return !remove.isEmpty();
    }

    public static List<JsonNode> actions(JsonNode trigger) {
        if (trigger == null || !trigger.isObject()) return List.of();
        JsonNode actions = trigger.path("actions");
        if (actions.isArray() && !actions.isEmpty()) {
            List<JsonNode> result = new ArrayList<>();
            actions.forEach(item -> {
                if (item != null && item.isObject()) result.add(item);
            });
            return List.copyOf(result);
        }
        JsonNode action = trigger.get("action");
        return action != null && action.isObject() ? List.of(action) : List.of();
    }

    private static JsonNode canonical(JsonNode source) {
        if (source == null || source.isNull() || source.isValueNode()) {
            return source == null ? JsonNodeSupport.MAPPER.nullNode() : source.deepCopy();
        }
        if (source.isArray()) {
            ArrayNode result = JsonNodeSupport.arrayNode();
            source.forEach(item -> result.add(canonical(item)));
            return result;
        }
        ObjectNode result = JsonNodeSupport.objectNode();
        List<String> names = new ArrayList<>();
        source.fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.naturalOrder());
        names.forEach(name -> result.set(name, canonical(source.get(name))));
        return result;
    }
}
