package com.smartlab.agent.api;

import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;

import java.util.List;

public record WorkflowGenerateResponse(
        WorkflowModelDocument definition,
        Integer version,
        String status,
        Long predecessorId,
        List<WorkflowIssue> issues,
        boolean executable,
        boolean published,
        Long flowModelId,
        List<String> trace,
        List<AgentInteractionLog> logs,
        String summary) {
}
