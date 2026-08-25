package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.workflow.WorkflowExpressionHistoryResolver;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UpdateWorkflowActionExecutor implements WorkflowActionExecutor {
    private final ConstraintExpressionEvaluator expressionEvaluator;
    private final WorkflowExpressionHistoryResolver historyResolver;

    public UpdateWorkflowActionExecutor(ConstraintExpressionEvaluator expressionEvaluator,
            WorkflowExpressionHistoryResolver historyResolver) {
        this.expressionEvaluator = expressionEvaluator;
        this.historyResolver = historyResolver;
    }

    @Override
    public String actionName() {
        return "UPDATE";
    }

    @Override
    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        String updateType = action.payload().path("updateType").asText("").trim();
        String variableName = action.payload().path("targetName").asText("").trim();
        if (variableName.isBlank())
            throw new IllegalArgumentException("UPDATE动作字段不完整");
        if ("NODE_LIFECYCLE".equals(updateType)) {
            if (context.operations() == null) throw new IllegalArgumentException("生命周期UPDATE缺少执行边界");
            context.operations().transitionNodeLifecycle(context.task(), context.step(), context.node(), variableName);
            return WorkflowActionResult.continueExecution();
        }
        if (!"INTERNAL_VARIABLE".equals(updateType)) throw new IllegalArgumentException("UPDATE动作updateType不支持: " + updateType);
        JsonNode value;
        if (action.payload().has("value")) {
            value = action.payload().get("value").deepCopy();
        } else {
            String expression = action.payload().path("valueExpression").asText("").trim();
            try {
                value = resolveExpression(expression, context);
            } catch (ConstraintExpressionEvaluator.TemporalDataUnavailableException unavailable) {
                return WorkflowActionResult.awaitExternalSignal(null);
            }
        }
        JsonNode declaration = declaredVariable(context.node().getInVariables(), variableName);
        if (declaration == null) throw new IllegalArgumentException("UPDATE动作引用的内部变量不存在: " + variableName);
        value = normalizeValue(declaration.path("dataType").asText(""), value);
        requireCompatibleType(variableName, declaration.path("dataType").asText(""), value);
        ObjectNode update = JsonNodeSupport.objectNode();
        update.set(variableName, value.deepCopy());
        return WorkflowActionResult.continueWith(update);
    }

    private JsonNode resolveExpression(String expression, WorkflowActionContext context) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("valueExpression不能为空");
        }
        Map<String, JsonNode> values = new LinkedHashMap<>();
        if (context.variables() != null && context.variables().isObject()) {
            context.variables().fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue()));
        }
        Instant now = context.now() == null ? Instant.now() : context.now();
        return expressionEvaluator.evaluateWorkflowValue(expression, values,
                historyResolver.histories(context.task(), context.step(), context.node(), expression, values, now),
                now);
    }

    private JsonNode declaredVariable(JsonNode definitions, String variableName) {
        if (definitions == null || !definitions.isArray()) return null;
        for (JsonNode definition : definitions) {
            if (variableName.equals(definition.path("name").asText())) return definition;
        }
        return null;
    }

    private JsonNode normalizeValue(String dataType, JsonNode value) {
        if (!"INTEGER".equals(dataType) || value == null || !value.isNumber() || value.isIntegralNumber()) {
            return value;
        }
        try {
            return JsonNodeSupport.toNode(value.decimalValue().longValueExact());
        } catch (ArithmeticException ignored) {
            return value;
        }
    }

    private void requireCompatibleType(String variableName, String dataType, JsonNode value) {
        boolean valid = value != null && ("JSON".equals(dataType) || !value.isNull()) && switch (dataType) {
            case "INTEGER" -> value.isIntegralNumber();
            case "DOUBLE" -> value.isNumber();
            case "STRING" -> value.isTextual();
            case "BOOLEAN" -> value.isBoolean();
            case "JSON" -> true;
            default -> throw new IllegalArgumentException("变量" + variableName + "声明了不支持的数据类型" + dataType);
        };
        if (!valid) throw new IllegalArgumentException("变量" + variableName + "要求" + dataType + "，表达式结果类型不一致");
    }
}
