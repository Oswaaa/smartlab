package com.smartlab.engine.constraint;

import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.management.entity.constraint.ConstraintRule;

import java.util.Map;

public record RuntimeConstraint(
        RuntimeConstraintKey key,
        ConstraintRule rule,
        Map<String, ObservableKey> observableBindings,
        Long taskId,
        Long taskStepId,
        Long deviceInstanceId
) {
    public RuntimeConstraint {
        observableBindings = Map.copyOf(observableBindings);
    }
}
