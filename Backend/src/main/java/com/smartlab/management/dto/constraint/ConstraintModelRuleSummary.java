package com.smartlab.management.dto.constraint;

import com.fasterxml.jackson.databind.JsonNode;

public record ConstraintModelRuleSummary(
        String origin,
        Long ruleId,
        Integer taskRuleIndex,
        String ruleName,
        String expression,
        int bindingCount,
        int actionCount,
        Integer windowSeconds,
        JsonNode bindings,
        JsonNode violationActions
) {
}
