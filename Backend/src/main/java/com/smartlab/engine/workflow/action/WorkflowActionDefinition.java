package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** actionName是供bindingTriggers引用的节点内唯一名称，actionType决定由EMIT或UPDATE执行器处理 */
public record WorkflowActionDefinition(String actionName, String actionType, ObjectNode payload) {
    public static WorkflowActionDefinition from(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("工作流动作必须是对象");
        String name = node.path("actionName").asText("").trim();
        String type = node.path("actionType").asText("").trim();
        if (name.isBlank()) throw new IllegalArgumentException("工作流动作缺少actionName");
        if (type.isBlank()) throw new IllegalArgumentException("工作流动作缺少actionType");
        ObjectNode payload = ((ObjectNode) node).deepCopy();
        payload.remove("actionName");
        payload.remove("actionType");
        return new WorkflowActionDefinition(name, type, payload);
    }
}
