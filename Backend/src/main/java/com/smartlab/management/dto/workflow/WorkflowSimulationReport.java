package com.smartlab.management.dto.workflow;

import java.util.List;

public record WorkflowSimulationReport(
        Long taskId,
        Long flowModelId,
        String taskStatus,
        boolean walkable,
        List<String> pathTaken,
        List<List<String>> paths,
        List<WorkflowIssue> issues) {
}
