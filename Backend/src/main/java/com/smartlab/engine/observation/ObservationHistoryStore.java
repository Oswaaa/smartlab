package com.smartlab.engine.observation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Bounded in-memory history for observable values. It is a read model, not an
 * authority and is intentionally not backed by a database on the hot path.
 */
@Service
public class ObservationHistoryStore {
    public static final int DEFAULT_MAX_SAMPLES = 4096;
    public static final long DEFAULT_MAX_SECONDS = 1800;

    private final ConcurrentHashMap<ObservableKey, History> values = new ConcurrentHashMap<>();
    private final int maxSamples;
    private final Duration maxAge;

    public ObservationHistoryStore() {
        this(DEFAULT_MAX_SAMPLES, Duration.ofSeconds(DEFAULT_MAX_SECONDS));
    }

    public ObservationHistoryStore(int maxSamples, Duration maxAge) {
        if (maxSamples <= 0) throw new IllegalArgumentException("历史样本数上限必须大于0");
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("历史保留时间必须大于0");
        }
        this.maxSamples = maxSamples;
        this.maxAge = maxAge;
    }

    public void append(ObservableKey key, JsonNode value, Instant observedAt, long revision) {
        if (key == null || value == null || value.isNull()) return;
        Instant timestamp = observedAt == null ? Instant.now() : observedAt;
        History history = values.computeIfAbsent(key, ignored -> new History());
        synchronized (history) {
            if (!history.samples.isEmpty() && revision <= history.samples.getLast().revision()) return;
            history.samples.addLast(new ObservationSample(key, value, timestamp, revision));
            prune(history, Instant.now().minus(maxAge));
        }
    }

    public List<ObservationSample> read(ObservableKey key, Instant since) {
        History history = key == null ? null : values.get(key);
        if (history == null) return List.of();
        Instant threshold = since == null ? Instant.now().minus(maxAge) : since;
        synchronized (history) {
            prune(history, Instant.now().minus(maxAge));
            List<ObservationSample> result = new ArrayList<>();
            for (ObservationSample sample : history.samples) {
                if (!sample.observedAt().isBefore(threshold)) result.add(sample);
            }
            return List.copyOf(result);
        }
    }

    public Map<ObservableKey, List<ObservationSample>> readBatch(Collection<ObservableKey> keys, Instant since) {
        Map<ObservableKey, List<ObservationSample>> result = new LinkedHashMap<>();
        if (keys != null) for (ObservableKey key : keys) result.put(key, read(key, since));
        return Map.copyOf(result);
    }

    public void removeIf(Predicate<ObservableKey> predicate) {
        if (predicate != null) values.keySet().removeIf(predicate);
    }

    private void prune(History history, Instant threshold) {
        history.samples.removeIf(sample -> sample.observedAt().isBefore(threshold));
        while (history.samples.size() > maxSamples) history.samples.removeFirst();
    }

    private static final class History {
        private final ArrayDeque<ObservationSample> samples = new ArrayDeque<>();
    }
}
