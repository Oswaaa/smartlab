package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class CalculateWorkflowActionExecutor implements WorkflowActionExecutor {
    private final WorkflowValueResolver resolver;

    public CalculateWorkflowActionExecutor(WorkflowValueResolver resolver) { this.resolver = resolver; }
    public String actionName() { return "CALCULATE"; }

    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        String target = action.payload().path("target").asText("").trim();
        String operator = action.payload().path("operator").asText("").trim();
        if (target.isBlank() || operator.isBlank()) throw new IllegalArgumentException("CALCULATE 缺少 target 或 operator");
        JsonNode operandsNode = action.payload().path("operands");
        if (!operandsNode.isArray() || operandsNode.isEmpty()) throw new IllegalArgumentException("CALCULATE.operands 不能为空");
        List<BigDecimal> operands = new ArrayList<>();
        for (JsonNode source : operandsNode) {
            JsonNode resolved = resolver.resolve(source, context.variables());
            if (!resolved.isNumber()) throw new IllegalArgumentException("CALCULATE 只接受数值操作数");
            operands.add(resolved.decimalValue());
        }
        int scale = action.payload().path("scale").asInt(6);
        BigDecimal result = calculate(operator, operands, scale);
        ObjectNode updates = JsonNodeSupport.objectNode();
        resolver.write(updates, target, JsonNodeSupport.toNode(result));
        return WorkflowActionResult.continueWith(updates);
    }

    private BigDecimal calculate(String operator, List<BigDecimal> values, int scale) {
        return switch (operator) {
            case "ADD" -> values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case "SUBTRACT" -> fold(values, BigDecimal::subtract);
            case "MULTIPLY" -> values.stream().reduce(BigDecimal.ONE, BigDecimal::multiply);
            case "DIVIDE" -> fold(values, (left, right) -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) throw new IllegalArgumentException("CALCULATE 不能除以零");
                return left.divide(right, scale, RoundingMode.HALF_UP);
            });
            case "MOD" -> fold(values, (left, right) -> {
                if (right.compareTo(BigDecimal.ZERO) == 0) throw new IllegalArgumentException("CALCULATE 不能对零取模");
                return left.remainder(right);
            });
            case "MIN" -> values.stream().min(BigDecimal::compareTo).orElseThrow();
            case "MAX" -> values.stream().max(BigDecimal::compareTo).orElseThrow();
            case "ROUND" -> {
                if (values.size() != 1) throw new IllegalArgumentException("ROUND 只接受一个操作数");
                yield values.getFirst().setScale(scale, RoundingMode.HALF_UP);
            }
            default -> throw new IllegalArgumentException("不支持的计算操作符: " + operator);
        };
    }

    private BigDecimal fold(List<BigDecimal> values, java.util.function.BinaryOperator<BigDecimal> operation) {
        BigDecimal result = values.getFirst();
        for (int i = 1; i < values.size(); i++) result = operation.apply(result, values.get(i));
        return result;
    }
}
