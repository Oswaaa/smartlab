package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;

public record CapabilityParameterRequirement(
        String name, String displayName, String dataType, JsonNode modelValue, boolean hole) {
}
