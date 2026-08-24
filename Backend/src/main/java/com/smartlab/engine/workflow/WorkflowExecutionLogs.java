package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;

public final class WorkflowExecutionLogs {
    private WorkflowExecutionLogs() {
    }

    public static String nodeCreated(String nodeName, Long nodeIdRef) {
        return "节点已创建: " + nodeLabel(nodeName, nodeIdRef);
    }

    public static String lifecycleChanged(String nodeName, Long nodeIdRef, String fromState, String toState) {
        return "节点 " + nodeLabel(nodeName, nodeIdRef) + " 生命周期 " + fromState + " → " + toState;
    }

    public static String emitted(String nodeName, Long nodeIdRef, String interfaceName, String signalName, JsonNode payload) {
        return "节点 " + nodeLabel(nodeName, nodeIdRef) + " 接口 " + text(interfaceName)
                + " 发出信号 " + text(signalName) + payloadSuffix(payload);
    }

    public static String received(String nodeName, Long nodeIdRef, String interfaceName, String signalName, JsonNode payload) {
        return "节点 " + nodeLabel(nodeName, nodeIdRef) + " 接口 " + text(interfaceName)
                + " 收到信号 " + text(signalName) + payloadSuffix(payload);
    }

    public static String nodeLabel(String nodeName, Long nodeIdRef) {
        String name = nodeName == null ? "" : nodeName.trim();
        if (!name.isBlank() && nodeIdRef != null) return name + " (#" + nodeIdRef + ")";
        if (!name.isBlank()) return name;
        return nodeIdRef == null ? "?" : "#" + nodeIdRef;
    }

    static String payloadSuffix(JsonNode payload) {
        if (payload == null || !payload.isObject() || payload.isEmpty()) return "";
        StringBuilder summary = new StringBuilder();
        appendField(summary, payload, "stateName");
        appendField(summary, payload, "capabilityName");
        appendField(summary, payload, "messageId");
        return summary.isEmpty() ? "" : "，" + summary;
    }

    private static void appendField(StringBuilder summary, JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) return;
        String text = value.asText("");
        if (text.isBlank()) return;
        if (!summary.isEmpty()) summary.append(", ");
        summary.append(field).append("=").append(text);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }
}
