package com.smartlab.engine.workflow;

import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowConditionEvaluatorTest {
    private final WorkflowConditionEvaluator evaluator = new WorkflowConditionEvaluator();

    @Test
    void evaluatesScalarRangeAndMembershipConditions() {
        var variables = JsonNodeSupport.objectNode();
        variables.put("temperature", 75);
        variables.put("status", "RUNNING");

        assertTrue(evaluator.evaluate(condition("temperature", ">", JsonNodeSupport.toNode(60)), variables));
        assertTrue(evaluator.evaluate(condition("temperature", "BETWEEN", JsonNodeSupport.toNode(new int[]{20, 80})), variables));
        assertTrue(evaluator.evaluate(condition("status", "IN", JsonNodeSupport.toNode(new String[]{"IDLE", "RUNNING"})), variables));
        assertFalse(evaluator.evaluate(condition("temperature", "<", JsonNodeSupport.toNode(20)), variables));
    }

    @Test
    void evaluatesAndConditionGroupAgainstOneFrozenVariableSnapshot() {
        var variables = JsonNodeSupport.objectNode();
        variables.put("nodeLifecycleState", "RUNNING");
        variables.put("temperature", 120);
        variables.put("pressure", 8);

        var allSatisfied = andCondition(
                condition("nodeLifecycleState", "=", JsonNodeSupport.toNode("RUNNING")),
                condition("temperature", ">", JsonNodeSupport.toNode(100)),
                condition("pressure", "<", JsonNodeSupport.toNode(10)));
        var oneUnsatisfied = andCondition(
                condition("nodeLifecycleState", "=", JsonNodeSupport.toNode("RUNNING")),
                condition("pressure", ">", JsonNodeSupport.toNode(10)));

        assertTrue(evaluator.evaluate(allSatisfied, variables));
        assertFalse(evaluator.evaluate(oneUnsatisfied, variables));
    }

    @Test
    void rejectsUnsupportedConditionGroupLogic() {
        var condition = JsonNodeSupport.objectNode();
        condition.put("logic", "OR");
        condition.putArray("conditions")
                .add(condition("temperature", ">", JsonNodeSupport.toNode(100)));

        assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(condition, JsonNodeSupport.objectNode().put("temperature", 120)));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode andCondition(
            com.fasterxml.jackson.databind.node.ObjectNode... conditions) {
        var group = JsonNodeSupport.objectNode();
        group.put("logic", "AND");
        var items = group.putArray("conditions");
        for (var condition : conditions) items.add(condition);
        return group;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode condition(String object, String operator,
                                                                      com.fasterxml.jackson.databind.JsonNode threshold) {
        var condition = JsonNodeSupport.objectNode();
        condition.put("object", object);
        condition.put("operator", operator);
        condition.set("threshold", threshold);
        return condition;
    }
}
