package com.smartlab.engine.workflow;

import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private com.fasterxml.jackson.databind.node.ObjectNode condition(String subject, String operator,
                                                                      com.fasterxml.jackson.databind.JsonNode threshold) {
        var condition = JsonNodeSupport.objectNode();
        condition.put("subject", subject);
        condition.put("operator", operator);
        condition.set("threshold", threshold);
        return condition;
    }
}
