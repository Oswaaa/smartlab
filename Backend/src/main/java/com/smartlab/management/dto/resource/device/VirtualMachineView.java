package com.smartlab.management.dto.resource.device;

import java.time.OffsetDateTime;

public record VirtualMachineView(
        Long physicalInstanceId,
        Long virtualInstanceId,
        Long leaseId,
        String virtualDevicePoint,
        String instanceName,
        String status,
        OffsetDateTime lastUsedTime,
        OffsetDateTime createTime
) {
}
