package com.smartlab.global.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record DeviceTelemetryUpdatedEvent(
        Long deviceInstanceId,
        Long deviceModelId,
        JsonNode attributes,
        Instant occurredAt
) {
}
