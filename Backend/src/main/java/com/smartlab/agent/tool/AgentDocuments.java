package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;

final class AgentDocuments {
    private AgentDocuments() {
    }

    static WorkflowModelDocument requireDocument(JsonNode arguments) {
        JsonNode source = arguments == null ? null : arguments.get("document");
        if (source == null || source.isNull() || source.isMissingNode()) source = arguments;
        if (source == null || !source.isObject() || !source.has("metadata")) {
            throw new IllegalArgumentException("缺少 document（工作流模型文件）");
        }
        try {
            return JsonNodeSupport.MAPPER.treeToValue(source, WorkflowModelDocument.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析工作流模型文件: " + exception.getMessage());
        }
    }

    static JsonNode validationNode(WorkflowPreparationResponse prepared) {
        return compactPreparation(prepared, false);
    }

    static JsonNode saveNode(WorkflowPreparationResponse prepared) {
        return compactPreparation(prepared, true);
    }

    static JsonNode documentParameterSchema() {
        try {
            return JsonNodeSupport.MAPPER.readTree("""
                    {
                      "type": "object",
                      "required": ["document"],
                      "properties": {
                        "document": {
                          "type": "object",
                          "description": "工作流模型文件。节点用 name 标识；控制流只走 interfaceConnections 的 source/target，不要写 fromNodeId。lifecycle/interfaces 由后端补全。",
                          "required": ["metadata", "nodes", "interfaceConnections", "portConnections"],
                          "properties": {
                            "metadata": {
                              "type": "object",
                              "required": ["flowModelName"],
                              "properties": {
                                "flowModelId": { "type": ["integer", "null"] },
                                "flowModelName": { "type": "string" },
                                "description": { "type": "string" }
                              }
                            },
                            "nodes": {
                              "type": "array",
                              "items": {
                                "type": "object",
                                "required": ["name", "nodeType"],
                                "properties": {
                                  "name": { "type": "string" },
                                  "nodeType": { "type": "string", "enum": ["FUNC_NODE", "DEV_NODE"] },
                                  "functionType": { "type": "string", "enum": ["START", "END"] },
                                  "deviceModelId": { "type": "integer" },
                                  "capability": {
                                    "type": "object",
                                    "required": ["capabilityName"],
                                    "properties": {
                                      "capabilityName": { "type": "string" },
                                      "capabilityParameters": { "type": "object" }
                                    }
                                  },
                                  "ports": { "type": "array" }
                                }
                              }
                            },
                            "interfaceConnections": {
                              "type": "array",
                              "items": {
                                "type": "object",
                                "required": ["connectionType", "source", "target"],
                                "properties": {
                                  "connectionType": { "type": "string", "enum": ["NODE_TO_NODE", "NODE_TO_DEVICE", "DEVICE_TO_NODE"] },
                                  "source": { "type": "object" },
                                  "target": { "type": "object" }
                                }
                              }
                            },
                            "portConnections": { "type": "array" }
                          }
                        }
                      }
                    }
                    """);
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建工作流 document 参数模式", exception);
        }
    }

    private static JsonNode compactPreparation(WorkflowPreparationResponse prepared, boolean includeSavedIdentity) {
        ObjectNode result = JsonNodeSupport.objectNode();
        java.util.List<WorkflowIssue> issues = prepared == null ? java.util.List.of() : prepared.issues();
        result.set("issues", issuesNode(issues));
        result.put("executable", prepared != null && prepared.executable());
        result.put("published", prepared != null && prepared.published());
        result.put("blocking", hasBlocking(issues));
        if (includeSavedIdentity && prepared != null && prepared.definition() != null) {
            var definition = prepared.definition();
            if (definition.getId() != null) result.put("flowModelId", definition.getId());
            if (definition.getVersion() != null) result.put("version", definition.getVersion());
            if (definition.getStatus() != null) result.put("status", definition.getStatus());
            if (definition.getPredecessorId() != null) result.put("predecessorId", definition.getPredecessorId());
            if (definition.getName() != null) result.put("flowModelName", definition.getName());
        }
        return result;
    }

    private static JsonNode issuesNode(java.util.List<WorkflowIssue> issues) {
        ArrayNode array = JsonNodeSupport.arrayNode();
        if (issues == null) return array;
        for (WorkflowIssue issue : issues) {
            if (issue == null) continue;
            ObjectNode item = array.addObject();
            item.put("blocking", issue.blocking());
            if (issue.code() != null) item.put("code", issue.code());
            if (issue.path() != null && !issue.path().isBlank()) item.put("path", issue.path());
            if (issue.message() != null) item.put("message", issue.message());
            if (issue.suggestion() != null) item.put("suggestion", issue.suggestion());
        }
        return array;
    }

    static boolean hasBlocking(java.util.List<WorkflowIssue> issues) {
        if (issues == null) return false;
        return issues.stream().anyMatch(WorkflowIssue::blocking);
    }
}
