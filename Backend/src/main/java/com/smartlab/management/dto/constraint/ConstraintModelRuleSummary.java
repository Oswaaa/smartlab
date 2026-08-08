package com.smartlab.management.dto.constraint;

public record ConstraintModelRuleSummary(
        String origin,
        Long ruleId,
        Integer taskRuleIndex,
        String ruleName,
        String expression,
        int bindingCount,
        int actionCount
) {
}
