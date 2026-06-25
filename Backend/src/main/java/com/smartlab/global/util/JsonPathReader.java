package com.smartlab.global.util;

import java.util.Map;

public final class JsonPathReader {

    private JsonPathReader() {
    }

    public static Object read(Map<String, Object> source, String path) {
        if (source == null || path == null || path.trim().isEmpty()) {
            return null;
        }
        String normalized = path.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        Object current = source;
        for (String token : normalized.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(token);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
