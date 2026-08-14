package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.engine.workflow.WorkflowExecutionOperations;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

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
    void writesInlineConstantWithoutExpressionEvaluation() {
        ObjectNode actionNode = JsonNodeSupport.objectNode();
        actionNode.put("actionName", "UPDATE");
        actionNode.putObject("payload")
                .put("updateType", "INTERNAL_VARIABLE")
                .put("targetName", "target")
                .put("value", 200);

        WorkflowActionResult result = executor.execute(
                WorkflowActionDefinition.from(actionNode),
                context(JsonNodeSupport.objectNode(), variable("target", "INTEGER")));

        assertThat(result.variableUpdates().path("target").asInt()).isEqualTo(200);
    }

    @Test
    void normalizesExactArithmeticResultForIntegerVariable() {
        ObjectNode variables = JsonNodeSupport.objectNode().put("count", 0);

        WorkflowActionResult result = executor.execute(
                action("count", "count + 1"),
                context(variables, variable("count", "INTEGER")));

        assertThat(result.variableUpdates().path("count").isIntegralNumber()).isTrue();
        assertThat(result.variableUpdates().path("count").asInt()).isEqualTo(1);
    }

    @Test
    void requestsNodeLifecycleTransitionWithoutWritingVariableSpace() {
        List<String> requestedStates = new ArrayList<>();
        WorkflowExecutionOperations operations = (WorkflowExecutionOperations) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WorkflowExecutionOperations.class},
                (proxy, method, args) -> {
                    if ("transitionNodeLifecycle".equals(method.getName())) {
                        requestedStates.add((String) args[3]);
                        return null;
                    }
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
        ObjectNode actionNode = JsonNodeSupport.objectNode();
        actionNode.put("actionName", "UPDATE");
        actionNode.putObject("payload")
                .put("updateType", "NODE_LIFECYCLE")
                .put("targetName", "RUNNING");
        FlowNode node = new FlowNode();

        WorkflowActionResult result = executor.execute(
                WorkflowActionDefinition.from(actionNode),
                new WorkflowActionContext(new Task(), new TaskStep(), node,
                        JsonNodeSupport.objectNode(), Instant.now(), operations));

        assertThat(requestedStates).containsExactly("RUNNING");
        assertThat(result.variableUpdates()).isEmpty();
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
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionName", "UPDATE");
        action.putObject("payload")
                .put("updateType", "INTERNAL_VARIABLE")
                .put("targetName", variableName)
                .put("valueExpression", expression);
        return WorkflowActionDefinition.from(action);
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
