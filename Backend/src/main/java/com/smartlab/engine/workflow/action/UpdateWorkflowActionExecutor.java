package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Component;

@Component
public class UpdateWorkflowActionExecutor implements WorkflowActionExecutor {
    private final WorkflowValueResolver valueResolver;

    public UpdateWorkflowActionExecutor(WorkflowValueResolver valueResolver) {
        this.valueResolver = valueResolver;
    }

    @Override
    public String actionName() {
        return "UPDATE";
    }

    @Override
    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        String variableName = action.payload().path("internalVariableName").asText("").trim();
        String expression = action.payload().path("valueExpression").asText("").trim();
        if (variableName.isBlank() || expression.isBlank())
            throw new IllegalArgumentException("UPDATE动作字段不完整");
        JsonNode value = valueResolver.resolveExpression(expression, context.variables());
        JsonNode declaration = declaredVariable(context.node().getInVariables(), variableName);
        if (declaration == null) throw new IllegalArgumentException("UPDATE动作引用的内部变量不存在: " + variableName);
        requireCompatibleType(variableName, declaration.path("dataType").asText(""), value);
        ObjectNode update = JsonNodeSupport.objectNode();
        valueResolver.write(update, variableName, value);
        return WorkflowActionResult.continueWith(update);
    }

    private JsonNode declaredVariable(JsonNode definitions, String variableName) {
        if (definitions == null || !definitions.isArray()) return null;
        for (JsonNode definition : definitions) {
            if (variableName.equals(definition.path("name").asText())) return definition;
        }
        return null;
    }

    private void requireCompatibleType(String variableName, String dataType, JsonNode value) {
        boolean valid = value != null && !value.isNull() && switch (dataType) {
            case "INTEGER" -> value.isIntegralNumber();
            case "DOUBLE" -> value.isNumber();
            case "STRING" -> value.isTextual();
            case "BOOLEAN" -> value.isBoolean();
            case "JSON" -> value.isObject() || value.isArray();
            default -> throw new IllegalArgumentException("变量" + variableName + "声明了不支持的数据类型" + dataType);
        };
        if (!valid) throw new IllegalArgumentException("变量" + variableName + "要求" + dataType + "，表达式结果类型不一致");
    }
}
