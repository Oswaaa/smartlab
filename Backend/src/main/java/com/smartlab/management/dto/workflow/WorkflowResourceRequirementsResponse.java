package com.smartlab.management.dto.workflow;

import java.util.List;

public record WorkflowResourceRequirementsResponse(
        Long workflowId, Integer workflowVersion, List<DeviceBindingRequirement> bindings) {
}
