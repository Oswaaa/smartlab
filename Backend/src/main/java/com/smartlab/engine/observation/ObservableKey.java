package com.smartlab.engine.observation;

import com.smartlab.global.contract.ObservableObjectType;

public record ObservableKey(
        ObservableObjectType sourceType,
        Long deviceInstanceId,
        Long workflowTemplateId,
        Long taskId,
        String regionName,
        String nodeName,
        String targetName,
        String variableName
) {
}
