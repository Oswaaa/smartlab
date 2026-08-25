package com.smartlab.management.dto.workflow;

import java.util.List;

public record DeviceBindingRequirement(
        String slotId, String occurrencePath, Long flowModelId, Integer flowVersion,
        long nodeIdRef, String flowModelName, String nodeName,
        long deviceModelId, String capabilityName, String capabilityDisplayName,
        List<CapabilityParameterRequirement> capabilityParameters) {
    public DeviceBindingRequirement {
        capabilityParameters = capabilityParameters == null ? List.of() : List.copyOf(capabilityParameters);
        if (capabilityDisplayName == null || capabilityDisplayName.isBlank()) {
            capabilityDisplayName = capabilityName;
        }
    }

    public DeviceBindingRequirement(String slotId, String occurrencePath, Long flowModelId, Integer flowVersion,
                                    long nodeIdRef, String flowModelName, String nodeName,
                                    long deviceModelId, String capabilityName) {
        this(slotId, occurrencePath, flowModelId, flowVersion, nodeIdRef, flowModelName, nodeName,
                deviceModelId, capabilityName, capabilityName, List.of());
    }
}
