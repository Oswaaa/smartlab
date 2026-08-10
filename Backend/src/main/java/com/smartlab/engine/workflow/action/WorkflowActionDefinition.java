package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** bindingTriggers内联动作；actionName直接选择EMIT或UPDATE执行器。 */
public record WorkflowActionDefinition(String actionName, ObjectNode payload) {
    public static WorkflowActionDefinition from(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("工作流动作必须是对象");
        String name = node.path("actionName").asText("").trim();
        if (name.isBlank()) throw new IllegalArgumentException("工作流动作缺少actionName");
        JsonNode payloadNode = node.path("payload");
        if (!payloadNode.isObject()) throw new IllegalArgumentException("工作流动作缺少payload");
        return new WorkflowActionDefinition(name, (ObjectNode) payloadNode.deepCopy());
    }
}
