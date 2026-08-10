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
            value = valueResolver.resolveExpression(expression, context.variables());
        }
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
