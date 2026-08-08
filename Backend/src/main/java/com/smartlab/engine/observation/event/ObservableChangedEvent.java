package com.smartlab.engine.observation.event;

import com.smartlab.engine.observation.ObservableKey;

import java.time.Instant;

public record ObservableChangedEvent(ObservableKey key, long revision, Instant changedAt) {
}
