package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Component;

@Component
public class WorkflowValueResolver {
    public JsonNode resolve(JsonNode source, JsonNode variables) {
        if (source == null || !source.isObject()) throw new IllegalArgumentException("值来源必须是对象");
        return switch (source.path("kind").asText("")) {
            case "LITERAL" -> {
                JsonNode value = source.get("value");
                if (value == null) throw new IllegalArgumentException("LITERAL 来源缺少 value");
                yield value.deepCopy();
            }
            case "VARIABLE" -> resolvePath(variables, requiredPath(source));
            default -> throw new IllegalArgumentException("不支持的值来源类型: " + source.path("kind").asText(""));
        };
    }

    public JsonNode resolvePath(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isObject() || !current.has(part))
                throw new IllegalArgumentException("变量路径不存在: " + path);
            current = current.get(part);
        }
        return current.deepCopy();
    }

    public void write(ObjectNode root, String path, JsonNode value) {
        if (root == null || path == null || path.isBlank()) throw new IllegalArgumentException("变量目标路径不能为空");
        String[] parts = path.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode existing = current.get(parts[i]);
            if (existing != null && !existing.isObject())
                throw new IllegalArgumentException("变量目标路径经过非对象节点: " + path);
            current = existing == null ? current.putObject(parts[i]) : (ObjectNode) existing;
        }
        current.set(parts[parts.length - 1], value == null ? JsonNodeSupport.MAPPER.nullNode() : value.deepCopy());
    }

    private String requiredPath(JsonNode source) {
        String path = source.path("path").asText("").trim();
        if (path.isBlank()) throw new IllegalArgumentException("VARIABLE 来源缺少 path");
        return path;
    }
}
