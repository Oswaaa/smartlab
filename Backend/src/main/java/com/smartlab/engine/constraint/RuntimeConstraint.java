package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;

import java.util.Map;

public record RuntimeConstraint(
        RuntimeConstraintKey key,
        ConstraintRule rule,
        Map<String, ObservableKey> observableBindings,
        Long taskId,
        Long taskStepId,
        Long deviceInstanceId,
        JsonNode observedVariables,
        JsonNode violationActions
) {
    public RuntimeConstraint {
        observableBindings = Map.copyOf(observableBindings);
        observedVariables = observedVariables == null
                ? JsonNodeSupport.objectNode()
                : observedVariables.deepCopy();
        violationActions = violationActions == null || violationActions.isNull()
                ? JsonNodeSupport.arrayNode()
                : violationActions.deepCopy();
    }

    public RuntimeConstraint(RuntimeConstraintKey key, ConstraintRule rule,
                             Map<String, ObservableKey> observableBindings,
                             Long taskId, Long taskStepId, Long deviceInstanceId,
                             JsonNode observedVariables) {
        this(key, rule, observableBindings, taskId, taskStepId, deviceInstanceId,
                observedVariables, rule == null ? null : rule.getViolationActions());
    }

    public RuntimeConstraint(RuntimeConstraintKey key, ConstraintRule rule,
                             Map<String, ObservableKey> observableBindings,
                             Long taskId, Long taskStepId, Long deviceInstanceId) {
        this(key, rule, observableBindings, taskId, taskStepId, deviceInstanceId,
                JsonNodeSupport.objectNode());
    }

    @Override
    public JsonNode observedVariables() {
        return observedVariables.deepCopy();
    }

    @Override
    public JsonNode violationActions() {
        return violationActions.deepCopy();
    }
}
