package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.catalog.DeviceCatalogAssembler;
import com.smartlab.agent.catalog.DeviceCatalogItem;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Component;

@Component
public class GetDeviceModelTool implements AgentTool {
    public static final String NAME = "get_device_model";

    private final DeviceCatalogAssembler assembler;

    public GetDeviceModelTool(DeviceCatalogAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "按 deviceModelId 读取设备模型：能力参数、属性、数据端口，以及 WORKFLOW/STATE 状态机接口名。"
                + " NODE_TO_DEVICE 的目标取设备 IN+WORKFLOW 接口；DEVICE_TO_NODE 的源取设备 OUT+STATE 接口。";
    }

    @Override
    public JsonNode parameterSchema() {
        ObjectNode schema = JsonNodeSupport.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("deviceModelId").put("type", "integer").put("description", "设备模型 ID");
        ArrayNode required = schema.putArray("required");
        required.add("deviceModelId");
        return schema;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        long id = arguments == null ? 0 : arguments.path("deviceModelId").asLong(0);
        ObjectNode result = JsonNodeSupport.objectNode();
        if (id <= 0) {
            result.put("error", "deviceModelId不能为空");
            return result;
        }
        DeviceCatalogItem item = assembler.detail(id);
        if (item == null) {
            result.put("error", "设备模型不存在: " + id);
            return result;
        }
        result.set("device", JsonNodeSupport.toNode(item));
        return result;
    }
}
