package com.smartlab.engine.observation.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;

import java.time.Instant;

public record DeviceTwinSnapshot(
        Long deviceInstanceId,
        Long deviceModelId,
        JsonNode attributes,
        String onlineStatus,
        Instant observedAt,
        Instant updatedAt,
        long revision,
        ObservationStatus status,
        SnapshotOrigin origin
) {
    public DeviceTwinSnapshot {
        attributes = attributes == null ? null : attributes.deepCopy();
    }

    @Override
    public JsonNode attributes() {
        return attributes == null ? null : attributes.deepCopy();
    }
}
