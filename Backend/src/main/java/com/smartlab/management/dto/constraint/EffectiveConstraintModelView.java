package com.smartlab.management.dto.constraint;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

public record EffectiveConstraintModelView(
        Long taskId,
        String taskName,
        String taskStatus,
        OffsetDateTime compiledAt,
        long revision,
        String modelHash,
        List<ConstraintModelRuleSummary> globalConstraints,
        List<ConstraintModelRuleSummary> taskConstraints,
        JsonNode model
) {
}
