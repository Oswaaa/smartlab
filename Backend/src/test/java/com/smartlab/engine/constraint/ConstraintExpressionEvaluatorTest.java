package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintExpressionEvaluatorTest {
    private final ConstraintExpressionEvaluator evaluator = new ConstraintExpressionEvaluator();

    @Test
    void evaluatesBindingsAndArithmeticComparisons() {
        Map<String, JsonNode> variables = Map.of(
                "temperature", JsonNodeSupport.MAPPER.getNodeFactory().numberNode(82),
                "limit", JsonNodeSupport.MAPPER.getNodeFactory().numberNode(80));
        assertTrue(evaluator.evaluate("temperature > limit && temperature - limit == 2", variables, Map.of(), Instant.now()));
        assertFalse(evaluator.evaluate("temperature < limit", variables, Map.of(), Instant.now()));
    }

    @Test
    void evaluatesRollingFunctionsFromObservableHistory() {
        Instant now = Instant.now();
        var history = List.of(
                new ConstraintExpressionEvaluator.TimedValue(now.minusSeconds(10), JsonNodeSupport.MAPPER.getNodeFactory().numberNode(10)),
                new ConstraintExpressionEvaluator.TimedValue(now, JsonNodeSupport.MAPPER.getNodeFactory().numberNode(20)));
        Map<String, JsonNode> variables = Map.of("temperature", JsonNodeSupport.MAPPER.getNodeFactory().numberNode(20));
        assertTrue(evaluator.evaluate("delta(temperature, 30) >= 10 && avg(temperature, 30) == 15 && rate(temperature, 30) >= 1", variables,
                Map.of("temperature", history), now));
    }
}
