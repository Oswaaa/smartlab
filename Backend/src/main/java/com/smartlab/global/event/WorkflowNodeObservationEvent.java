package com.smartlab.global.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record WorkflowNodeObservationEvent(
        Long taskId,
        Long workflowTemplateId,
        Long nodeIdRef,
        Long taskStepId,
        String nodeLifecycleState,
        JsonNode variableSpace,
        Instant occurredAt
) {
}
