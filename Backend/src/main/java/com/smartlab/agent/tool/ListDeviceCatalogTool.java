package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.catalog.DeviceCatalogAssembler;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Component;

@Component
public class ListDeviceCatalogTool implements AgentTool {
    public static final String NAME = "list_device_catalog";

    private final DeviceCatalogAssembler assembler;

    public ListDeviceCatalogTool(DeviceCatalogAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "列出设备模型的能力与属性（含参数名/显示名/类型，供填写 capabilityParameters）。不含端口、状态机接口、适配器字段。生成流程前必须先调用。";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode schema = JsonNodeSupport.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("keyword").put("type", "string").put("description", "可选，按设备名、类别或能力过滤");
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        String keyword = arguments != null ? arguments.path("keyword").asText("") : "";
        ObjectNode result = JsonNodeSupport.objectNode();
        result.set("devices", JsonNodeSupport.toNode(assembler.list(keyword.isBlank() ? null : keyword)));
        return result;
    }
}
