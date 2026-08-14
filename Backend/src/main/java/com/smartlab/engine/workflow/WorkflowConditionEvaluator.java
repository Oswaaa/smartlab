package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class WorkflowConditionEvaluator {
    public boolean evaluate(JsonNode condition, JsonNode variables) {
        if (condition == null || !condition.isObject())
            throw new IllegalArgumentException("触发器condition必须是对象");
        if (condition.has("logic") || condition.has("conditions")) {
            return evaluateGroup(condition, variables);
        }
        return evaluatePredicate(condition, variables);
    }

    private boolean evaluateGroup(JsonNode condition, JsonNode variables) {
        String logic = condition.path("logic").asText("").trim();
        if (!"AND".equals(logic)) {
            throw new IllegalArgumentException("不支持的工作流条件组合方式: " + logic);
        }
        JsonNode conditions = condition.path("conditions");
        if (!conditions.isArray() || conditions.isEmpty()) {
            throw new IllegalArgumentException("AND条件组至少需要一个条件");
        }
        for (JsonNode item : conditions) {
            if (item.has("logic") || item.has("conditions")) {
                throw new IllegalArgumentException("工作流条件组不支持嵌套");
            }
            if (!evaluatePredicate(item, variables)) return false;
        }
        return true;
    }

    private boolean evaluatePredicate(JsonNode condition, JsonNode variables) {
        if (condition == null || !condition.isObject())
            throw new IllegalArgumentException("触发器原子条件必须是对象");
        String object = condition.path("object").asText("").trim();
        String operator = condition.path("operator").asText("").trim();
        JsonNode actual = resolve(variables, object);
        JsonNode expected = condition.get("threshold");
        if (actual == null || actual.isMissingNode() || expected == null)
            return false;
        return switch (operator) {
            case ">" -> compare(actual, expected) > 0;
            case "<" -> compare(actual, expected) < 0;
            case ">=" -> compare(actual, expected) >= 0;
            case "<=" -> compare(actual, expected) <= 0;
            case "=" -> equal(actual, expected);
            case "!=" -> !equal(actual, expected);
            case "BETWEEN" -> expected.isArray() && expected.size() == 2 && compare(actual, expected.get(0)) >= 0
                    && compare(actual, expected.get(1)) <= 0;
            case "IN" -> contains(expected, actual);
            default -> throw new IllegalArgumentException("不支持的工作流条件操作符: " + operator);
        };
    }

    private JsonNode resolve(JsonNode variables, String path) {
        if (variables == null || path == null || path.isBlank())
            return null;
        JsonNode current = variables;
        for (String part : path.split("\\."))
            current = current.path(part);
        return current;
    }

    private int compare(JsonNode left, JsonNode right) {
        if (left.isNumber() && right.isNumber())
            return left.decimalValue().compareTo(right.decimalValue());
        if (left.isBoolean() && right.isBoolean())
            return Boolean.compare(left.asBoolean(), right.asBoolean());
        return left.asText().compareTo(right.asText());
    }

    private boolean equal(JsonNode left, JsonNode right) {
        if (left.isArray() && !right.isArray()) return contains(left, right);
        if (right.isArray() && !left.isArray()) return contains(right, left);
        if (left.isNumber() && right.isNumber())
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        return left.equals(right) || left.asText().equals(right.asText());
    }

    private boolean contains(JsonNode candidates, JsonNode actual) {
        if (!candidates.isArray() || candidates.isEmpty())
            return false;
        for (JsonNode candidate : candidates)
            if (equal(actual, candidate))
                return true;
        return false;
    }
}
