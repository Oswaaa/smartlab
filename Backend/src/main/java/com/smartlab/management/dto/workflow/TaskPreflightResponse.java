package com.smartlab.management.dto.workflow;

import java.util.List;

public record TaskPreflightResponse(boolean ready, List<WorkflowIssue> issues) {
}
