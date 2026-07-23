package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowActionRuntimeTest {

    private final WorkflowValueResolver resolver = new WorkflowValueResolver();

    @Test
    void registryRequiresExactUniqueSchemaCoverage() {
        WorkflowActionExecutor wait = executor("WAIT");
        WorkflowActionExecutor assign = executor("ASSIGN");
        WorkflowActionExecutor calculate = executor("CALCULATE");
        WorkflowActionExecutor emit = executor("EMIT_SIGNAL");

        WorkflowActionRegistry registry = new WorkflowActionRegistry(
                List.of(wait, assign, calculate, emit),
                Set.of("WAIT", "ASSIGN", "CALCULATE", "EMIT_SIGNAL"));

        assertSame(calculate, registry.required("CALCULATE"));
        assertThrows(IllegalStateException.class, () -> new WorkflowActionRegistry(
                List.of(wait, wait, assign, calculate, emit),
                Set.of("WAIT", "ASSIGN", "CALCULATE", "EMIT_SIGNAL")));
        assertThrows(IllegalStateException.class, () -> new WorkflowActionRegistry(
                List.of(wait, assign, calculate),
                Set.of("WAIT", "ASSIGN", "CALCULATE", "EMIT_SIGNAL")));
    }

    @Test
    void resolvesLiteralAndNestedVariablesAndWritesNestedTargets() {
        ObjectNode variables = JsonNodeSupport.objectNode();
        variables.putObject("reactor").put("temperature", 42.5);

        var literal = JsonNodeSupport.objectNode().put("kind", "LITERAL").put("value", true);
        var variable = JsonNodeSupport.objectNode().put("kind", "VARIABLE").put("path", "reactor.temperature");

        assertTrue(resolver.resolve(literal, variables).asBoolean());
        assertEquals(42.5, resolver.resolve(variable, variables).asDouble());
        resolver.write(variables, "result.heating.target", JsonNodeSupport.toNode(60));
        assertEquals(60, variables.at("/result/heating/target").asInt());
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(JsonNodeSupport.objectNode().put("kind", "VARIABLE").put("path", "missing"), variables));
    }

    @Test
    void waitIsNonBlockingAndUsesStepStartTimeAsStableDeadline() {
        WaitWorkflowActionExecutor executor = new WaitWorkflowActionExecutor();
        TaskStep step = new TaskStep();
        step.setStartTime(OffsetDateTime.ofInstant(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC));
        var action = action("WAIT", JsonNodeSupport.objectNode().put("durationMs", 1500));

        var waiting = executor.execute(action, context(step, Instant.parse("2026-07-19T00:00:01Z")));
        var complete = executor.execute(action, context(step, Instant.parse("2026-07-19T00:00:02Z")));

        assertEquals(WorkflowActionStatus.SUSPEND_UNTIL, waiting.status());
        assertEquals(Instant.parse("2026-07-19T00:00:01.500Z"), waiting.resumeAt());
        assertEquals(WorkflowActionStatus.CONTINUE, complete.status());
    }

    @Test
    void assignAndCalculateProduceDeterministicVariableUpdates() {
        ObjectNode variables = JsonNodeSupport.objectNode();
        variables.put("base", 12.5);
        var context = context(new TaskStep(), Instant.EPOCH, variables);

        var assignPayload = JsonNodeSupport.objectNode().put("target", "settings.enabled");
        assignPayload.set("source", JsonNodeSupport.objectNode().put("kind", "LITERAL").put("value", true));
        var assigned = new AssignWorkflowActionExecutor(resolver).execute(action("ASSIGN", assignPayload), context);
        assertTrue(assigned.variableUpdates().at("/settings/enabled").asBoolean());

        var calculatePayload = JsonNodeSupport.objectNode().put("target", "result").put("operator", "MULTIPLY");
        calculatePayload.putArray("operands")
                .add(JsonNodeSupport.objectNode().put("kind", "VARIABLE").put("path", "base"))
                .add(JsonNodeSupport.objectNode().put("kind", "LITERAL").put("value", 2));
        var calculated = new CalculateWorkflowActionExecutor(resolver)
                .execute(action("CALCULATE", calculatePayload), context);
        assertEquals(0, new BigDecimal("25.0")
                .compareTo(calculated.variableUpdates().path("result").decimalValue()));

        calculatePayload.put("operator", "DIVIDE");
        calculatePayload.withArray("operands").set(1,
                JsonNodeSupport.objectNode().put("kind", "LITERAL").put("value", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CalculateWorkflowActionExecutor(resolver).execute(action("CALCULATE", calculatePayload), context));
    }

    private WorkflowActionExecutor executor(String name) {
        return new WorkflowActionExecutor() {
            public String actionName() { return name; }
            public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
                return WorkflowActionResult.continueExecution();
            }
        };
    }

    private WorkflowActionDefinition action(String name, ObjectNode payload) {
        return new WorkflowActionDefinition(name, payload);
    }

    private WorkflowActionContext context(TaskStep step, Instant now) {
        return context(step, now, JsonNodeSupport.objectNode());
    }

    private WorkflowActionContext context(TaskStep step, Instant now, ObjectNode variables) {
        return new WorkflowActionContext(new Task(), step, new FlowNode(), variables, now, null);
    }
}
