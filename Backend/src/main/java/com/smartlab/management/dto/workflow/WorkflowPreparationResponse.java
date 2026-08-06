package com.smartlab.management.dto.workflow;

import java.util.List;

public record WorkflowPreparationResponse(
        WorkflowDetailResponse definition, List<WorkflowIssue> issues,
        boolean executable, boolean published) {}
