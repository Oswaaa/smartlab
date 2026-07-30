package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateWorkflowActionExecutorTest {

    private final UpdateWorkflowActionExecutor executor = new UpdateWorkflowActionExecutor(
            new WorkflowValueResolver(new ConstraintExpressionEvaluator()));

    @Test
    void evaluatesArithmeticAndNestedInputPayloadBeforeWritingVariable() {
        ObjectNode variables = JsonNodeSupport.objectNode();
        variables.put("offset", 2);
        variables.putObject("inputPayload").put("temperature", 30.5);

        WorkflowActionResult result = executor.execute(
                action("target", "inputPayload.temperature + offset"),
                context(variables, variable("target", "DOUBLE")));

        assertThat(result.variableUpdates().path("target").decimalValue()).isEqualByComparingTo("32.5");
    }

    @Test
    void rejectsUpdateResultThatDoesNotMatchDeclaredType() {
        assertThatThrownBy(() -> executor.execute(
                action("enabled", "\"true\""),
                context(JsonNodeSupport.objectNode(), variable("enabled", "BOOLEAN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("变量enabled要求BOOLEAN");
    }

    private WorkflowActionDefinition action(String variableName, String expression) {
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("internalVariableName", variableName);
        payload.put("valueExpression", expression);
        return new WorkflowActionDefinition("update-" + variableName, "UPDATE", payload);
    }

    private WorkflowActionContext context(ObjectNode variables, ObjectNode variable) {
        FlowNode node = new FlowNode();
        ArrayNode definitions = JsonNodeSupport.arrayNode();
        definitions.add(variable);
        node.setInVariables(definitions);
        return new WorkflowActionContext(new Task(), new TaskStep(), node, variables, Instant.now(), null);
    }

    private ObjectNode variable(String name, String dataType) {
        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("name", name);
        result.put("dataType", dataType);
        return result;
    }
}
