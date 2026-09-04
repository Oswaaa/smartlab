package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ListWorkflowCatalogTool implements AgentTool {
    public static final String NAME = "list_workflow_catalog";

    private final WorkflowService workflowService;

    public ListWorkflowCatalogTool(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "列出 ACTIVE 状态的已有流程的 flowModelId、名称、描述、状态和版本，供子流程节点选用。"
                + "不含节点、连线等完整定义。需要查看已有流程的详细信息时调用 get_workflow_model。";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode schema = JsonNodeSupport.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("keyword").put("type", "string").put("description", "可选，按流程名称或描述过滤");
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        String keyword = arguments != null ? arguments.path("keyword").asText("") : "";
        ObjectNode result = JsonNodeSupport.objectNode();
        ArrayNode workflows = result.putArray("workflows");
        List<FlowModels> models = workflowService.list();
        if (models == null) return result;
        for (FlowModels model : models) {
            if (model == null) continue;
            if (!"ACTIVE".equalsIgnoreCase(model.getStatus())) continue;
            if (!matches(model, keyword)) continue;
            ObjectNode item = workflows.addObject();
            if (model.getId() != null) item.put("flowModelId", model.getId());
            item.put("flowModelName", text(model.getFlowName()));
            item.put("description", text(model.getDescription()));
            item.put("status", text(model.getStatus()));
            if (model.getVersion() != null) item.put("version", model.getVersion());
        }
        return result;
    }

    private static boolean matches(FlowModels model, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(model.getFlowName(), needle) || contains(model.getDescription(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
