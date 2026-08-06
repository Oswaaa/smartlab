package com.smartlab.management.dto.workflow;

public record DeviceBindingRequirement(
        String slotId, String occurrencePath, Long flowModelId, Integer flowVersion,
        long nodeIdRef, String flowName, String nodeName,
        long deviceModelId, String capabilityName) {
}
