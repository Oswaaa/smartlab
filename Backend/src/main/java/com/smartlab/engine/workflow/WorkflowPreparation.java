package com.smartlab.engine.workflow;

import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;

import java.util.List;

public record WorkflowPreparation(
        WorkflowModelDocument normalized,
        List<WorkflowIssue> issues,
        WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
    public enum Mode { DRAFT, PUBLISH }

    public boolean executable() {
        return compiled != null && issues.stream().noneMatch(WorkflowIssue::blocking);
    }
}
