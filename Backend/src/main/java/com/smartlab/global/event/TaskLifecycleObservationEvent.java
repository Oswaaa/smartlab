package com.smartlab.global.event;

import java.time.Instant;

public record TaskLifecycleObservationEvent(
        Long taskId,
        String taskLifecycleState,
        Instant occurredAt
) {
}
