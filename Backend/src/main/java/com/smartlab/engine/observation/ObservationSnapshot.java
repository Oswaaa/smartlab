package com.smartlab.engine.observation;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record ObservationSnapshot(
        ObservableKey key,
        JsonNode value,
        Instant observedAt,
        Instant updatedAt,
        long revision,
        ObservationStatus status,
        SnapshotOrigin origin
) {
    public ObservationSnapshot {
        value = value == null ? null : value.deepCopy();
    }

    @Override
    public JsonNode value() {
        return value == null ? null : value.deepCopy();
    }

    public static ObservationSnapshot unavailable(ObservableKey key) {
        Instant now = Instant.now();
        return new ObservationSnapshot(key, null, now, now, 0,
                ObservationStatus.UNAVAILABLE, SnapshotOrigin.LIVE);
    }
}
