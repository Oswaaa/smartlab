package com.smartlab.engine.constraint;

import com.smartlab.engine.observation.ObservableKey;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record ConstraintMonitoringPlan(
        long revision,
        String modelHash,
        Instant compiledAt,
        Map<RuntimeConstraintKey, RuntimeConstraint> constraints,
        Map<ObservableKey, Set<RuntimeConstraintKey>> dependencies
) {
    public ConstraintMonitoringPlan {
        constraints = Map.copyOf(constraints);
        dependencies = Map.copyOf(dependencies);
    }

    public static ConstraintMonitoringPlan empty() {
        return new ConstraintMonitoringPlan(0, "", Instant.now(), Map.of(), Map.of());
    }
}
