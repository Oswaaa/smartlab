package com.smartlab.management.dto.workflow;

import com.smartlab.management.entity.resource.data.DataIndex;

import java.util.List;

public record TaskDataAssetsResponse(
        Long taskId,
        String taskName,
        String executionKind,
        List<TaskInstanceDataAsset> instances
) {
    public record TaskInstanceDataAsset(
            Long physicalInstanceId,
            String instanceName,
            List<DataIndex> datasets
    ) {}
}
