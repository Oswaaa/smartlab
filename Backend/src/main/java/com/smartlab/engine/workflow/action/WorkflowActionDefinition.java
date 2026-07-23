package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowActionDefinition(String actionName, JsonNode payload) {
    public static WorkflowActionDefinition from(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("工作流动作必须是对象");
        String name = node.path("actionName").asText("").trim();
        if (name.isBlank()) throw new IllegalArgumentException("工作流动作缺少 actionName");
        JsonNode payload = node.get("payload");
        if (payload == null || !payload.isObject()) throw new IllegalArgumentException(name + " 动作缺少 payload");
        return new WorkflowActionDefinition(name, payload);
    }
}
