package com.smartlab.global.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ConstraintAlertEvent(
        Long constraintRuleId,
        String constraintName,
        Long taskId,
        JsonNode snapshot,
        Instant occurredAt
) {
}
