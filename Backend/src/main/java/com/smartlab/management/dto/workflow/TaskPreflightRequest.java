package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record TaskPreflightRequest(
        Long flowModelId, JsonNode taskVariables,
        List<TaskDeviceBindingRequest> deviceBindings, JsonNode taskConstraints) {
    public static TaskPreflightRequest from(TaskCreateRequest request) {
        return new TaskPreflightRequest(request.getFlowModelId(), request.getTaskVariables(),
                request.getDeviceBindings(), request.getTaskConstraints());
    }

}
