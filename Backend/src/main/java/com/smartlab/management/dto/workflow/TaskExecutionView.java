package com.smartlab.management.dto.workflow;

import java.util.List;

public record TaskExecutionView(
        Long taskId,
        String taskName,
        String status,
        int completedNodes,
        int totalNodes,
        List<TaskNodeExecutionView> nodes
) {}
