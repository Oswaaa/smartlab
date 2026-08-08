package com.smartlab.engine.constraint;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.constraint.ConstraintModelRuleSummary;

import java.time.Instant;
import java.util.List;

/**
 * One immutable compilation product shared by model export and runtime evaluation.
 */
public record EffectiveConstraintModel(
        Long taskId,
        String taskName,
        String taskStatus,
        Instant compiledAt,
        long revision,
        String modelHash,
        List<ConstraintModelRuleSummary> globalConstraints,
        List<ConstraintModelRuleSummary> taskConstraints,
        JsonNode jsonModel,
        ConstraintMonitoringPlan monitoringPlan
) {
    public EffectiveConstraintModel {
        globalConstraints = List.copyOf(globalConstraints);
        taskConstraints = List.copyOf(taskConstraints);
        jsonModel = jsonModel.deepCopy();
    }

    @Override
    public JsonNode jsonModel() {
        return jsonModel.deepCopy();
    }
}
