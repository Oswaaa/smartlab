package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.stereotype.Component;

@Component
public class GetWorkflowModelTool implements AgentTool {
    public static final String NAME = "get_workflow_model";

    private final WorkflowService workflowService;

    public GetWorkflowModelTool(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "按 flowModelId 查看已有流程的详细信息：描述、状态、版本，以及节点概览"
                + "（每个节点的类型、使用的设备及设备能力、子流程引用）。"
                + "用于判断已有流程是否适合作为子流程引用。不返回完整连线和触发器定义。";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode schema = JsonNodeSupport.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("flowModelId").put("type", "integer").put("description", "已有流程的 flowModelId");
        ArrayNode required = schema.putArray("required");
        required.add("flowModelId");
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        long id = arguments == null ? 0 : arguments.path("flowModelId").asLong(0);
        ObjectNode result = JsonNodeSupport.objectNode();
        if (id <= 0) {
            result.put("error", "flowModelId 不能为空");
            return result;
        }
        WorkflowDetailResponse detail = workflowService.getDefinition(id);
        if (detail == null) {
            result.put("error", "已有流程不存在: " + id);
            return result;
        }
        result.set("workflow", summarize(detail));
        return result;
    }

    private JsonNode summarize(WorkflowDetailResponse detail) {
        ObjectNode summary = JsonNodeSupport.objectNode();
        if (detail.getId() != null) summary.put("flowModelId", detail.getId());
        summary.put("flowModelName", detail.getName() == null ? "" : detail.getName());
        summary.put("description", detail.getDescription() == null ? "" : detail.getDescription());
        summary.put("status", detail.getStatus() == null ? "" : detail.getStatus());
        if (detail.getVersion() != null) summary.put("version", detail.getVersion());
        summary.set("nodes", summarizeNodes(detail.getNodesDef()));
        return summary;
    }

    private JsonNode summarizeNodes(JsonNode nodesDef) {
        ArrayNode nodes = JsonNodeSupport.arrayNode();
        if (nodesDef == null || !nodesDef.isArray()) return nodes;
        for (JsonNode node : nodesDef) {
            ObjectNode item = nodes.addObject();
            item.put("name", node.path("name").asText(""));
            String nodeType = node.path("nodeType").asText("");
            item.put("nodeType", nodeType);
            switch (nodeType) {
                case "DEV_NODE" -> {
                    long deviceModelId = node.path("deviceModelId").asLong(0);
                    if (deviceModelId > 0) item.put("deviceModelId", deviceModelId);
                    String capabilityName = node.path("capability").path("capabilityName").asText("");
                    if (!capabilityName.isBlank()) item.put("capabilityName", capabilityName);
                }
                case "FUNC_NODE" -> {
                    String functionType = node.path("functionType").asText("");
                    if (!functionType.isBlank()) item.put("functionType", functionType);
                }
                case "SUBFLOW_NODE" -> {
                    long subFlowModelId = node.path("subFlowModelId").asLong(0);
                    if (subFlowModelId > 0) item.put("subFlowModelId", subFlowModelId);
                    String subFlowDesc = node.path("subFlowModelDescription").asText("");
                    if (!subFlowDesc.isBlank()) item.put("subFlowModelDescription", subFlowDesc);
                }
            }
        }
        return nodes;
    }
}
