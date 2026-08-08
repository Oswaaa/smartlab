package com.smartlab.engine.observation;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * A live sample retained for temporal constraint functions such as delta/avg/rate.
 * The sample is immutable and never exposes the registry's mutable JsonNode.
 */
public record ObservationSample(
        ObservableKey key,
        JsonNode value,
        Instant observedAt,
        long revision
) {
    public ObservationSample {
        value = value == null ? null : value.deepCopy();
        observedAt = observedAt == null ? Instant.now() : observedAt;
    }

    @Override
    public JsonNode value() {
        return value == null ? null : value.deepCopy();
    }
}
