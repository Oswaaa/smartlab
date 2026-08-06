package com.smartlab.management.dto.workflow;

public record WorkflowIssue(
        String code, String stage, String path, String elementType,
        String elementId, boolean blocking, String message, String suggestion) {
}
