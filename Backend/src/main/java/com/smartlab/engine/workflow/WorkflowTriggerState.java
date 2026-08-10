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

final class WorkflowTriggerState {
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
