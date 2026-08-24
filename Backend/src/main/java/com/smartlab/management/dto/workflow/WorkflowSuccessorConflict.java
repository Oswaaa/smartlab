package com.smartlab.management.dto.workflow;

import com.smartlab.management.entity.workflow.FlowModels;

/** 从已启用流程再次 fork 时，已存在的唯一后续版本。 */
public record WorkflowSuccessorConflict(
        Long successorId,
        Integer version,
        String status,
        String flowModelName) {
    public static WorkflowSuccessorConflict from(FlowModels model) {
        if (model == null) return null;
        return new WorkflowSuccessorConflict(model.getId(), model.getVersion(), model.getStatus(), model.getFlowName());
    }
}
