package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.global.util.JsonNodeSupport;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkflowValueResolver {
    private final ConstraintExpressionEvaluator expressionEvaluator;

    public WorkflowValueResolver(ConstraintExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    public JsonNode resolve(JsonNode source, JsonNode variables) {
        if (source == null || !source.isObject())
            throw new IllegalArgumentException("值来源必须是对象");
        return switch (source.path("kind").asText("")) {
            case "LITERAL" -> {
                JsonNode value = source.get("value");
                if (value == null)
                    throw new IllegalArgumentException("LITERAL来源缺少value");
                yield value.deepCopy();
            }
            case "VARIABLE" -> resolvePath(variables, requiredPath(source));
            default -> throw new IllegalArgumentException("不支持的值来源类型: " + source.path("kind").asText(""));
        };
    }

    /** UPDATE与约束使用同一表达式语法；裸字符串不再作为解析失败的兜底 */
    public JsonNode resolveExpression(String expression, JsonNode variables) {
        String value = expression == null ? "" : expression.trim();
        if (value.isBlank())
            throw new IllegalArgumentException("valueExpression不能为空");
        JsonNode resolved = tryResolvePath(variables, value);
        if (resolved != null)
            return resolved;
        Map<String, JsonNode> roots = new LinkedHashMap<>();
        if (variables != null && variables.isObject()) {
            variables.fields().forEachRemaining(entry -> roots.put(entry.getKey(), entry.getValue()));
        }
        return expressionEvaluator.evaluateValue(value, roots, Map.of(), Instant.now());
    }

    public JsonNode resolvePath(JsonNode root, String path) {
        JsonNode resolved = tryResolvePath(root, path);
        if (resolved == null)
            throw new IllegalArgumentException("变量路径不存在: " + path);
        return resolved;
    }

    private JsonNode tryResolvePath(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank())
            return null;
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isObject() || !current.has(part))
                return null;
            current = current.get(part);
        }
        return current.deepCopy();
    }

    public void write(ObjectNode root, String path, JsonNode value) {
        if (root == null || path == null || path.isBlank())
            throw new IllegalArgumentException("变量目标路径不能为空");
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
        if (path.isBlank())
            throw new IllegalArgumentException("VARIABLE来源缺少path");
        return path;
    }
}
