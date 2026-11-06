package com.smartlab.agent.operator.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorJson;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.agent.operator.TaskPreparationService;
import com.smartlab.management.entity.workflow.FlowModels;
import org.springframework.stereotype.Component;

/** 列出可用流程，供建任务时选择。 */
@Component
public class ListFlowsTool implements OperatorTool {

    private static final JsonNode SCHEMA = OperatorJson.schema("""
            {"type":"object","properties":{
              "keyword":{"type":"string","description":"流程名关键词，可留空列出全部"}
            }}
            """);

    private final TaskPreparationService preparation;

    public ListFlowsTool(TaskPreparationService preparation) {
        this.preparation = preparation;
    }

    @Override
    public String name() {
        return "list_flows";
    }

    @Override
    public String description() {
        return "列出系统中的实验流程（含 id、名称、版本、状态）。用户说流程名但不确定全名时先查一下。";
    }

    @Override
    public JsonNode parameterSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode execute(JsonNode arguments, OperatorContext context) {
        String keyword = OperatorJson.text(arguments, "keyword");
        String normalized = keyword == null ? "" : com.smartlab.agent.operator.InstanceResolver.normalize(keyword);
        ObjectNode result = OperatorJson.obj();
        ArrayNode items = result.putArray("items");
        for (FlowModels flow : preparation.flows()) {
            String name = flow.getFlowName() == null ? "" : flow.getFlowName();
            if (!normalized.isEmpty()
                    && !com.smartlab.agent.operator.InstanceResolver.normalize(name).contains(normalized)) {
                continue;
            }
            ObjectNode item = items.addObject();
            item.put("id", flow.getId());
            item.put("name", name);
            item.put("version", flow.getVersion());
            item.put("status", flow.getStatus());
            item.put("nodeCount", flow.getNodes() == null || !flow.getNodes().isArray() ? 0 : flow.getNodes().size());
        }
        result.put("count", items.size());
        result.put("note", "状态为 DRAFT 的流程是未发布的草稿，一般不要用它建任务");
        return result;
    }
}
