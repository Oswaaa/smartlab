package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;

public record TaskDeviceBindingRequest(String slotId, Long deviceInstanceId, JsonNode capabilityParameters) {
    public TaskDeviceBindingRequest(String slotId, Long deviceInstanceId) {
        this(slotId, deviceInstanceId, null);
    }
}
