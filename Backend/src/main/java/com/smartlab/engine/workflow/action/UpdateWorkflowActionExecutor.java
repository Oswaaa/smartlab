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
    public String actionName() { return "UPDATE"; }

    @Override
    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        String variableName = action.payload().path("internalVariableName").asText("").trim();
        String expression = action.payload().path("valueExpression").asText("").trim();
        if (variableName.isBlank() || expression.isBlank()) throw new IllegalArgumentException("UPDATE动作字段不完整");
        ObjectNode update = JsonNodeSupport.objectNode();
        valueResolver.write(update, variableName, valueResolver.resolveExpression(expression, context.variables()));
        return WorkflowActionResult.continueWith(update);
    }
}
