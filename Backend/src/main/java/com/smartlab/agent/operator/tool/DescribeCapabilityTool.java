package com.smartlab.agent.operator.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.operator.CapabilityBinder;
import com.smartlab.agent.operator.InstanceResolver;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorDeviceCatalog;
import com.smartlab.agent.operator.OperatorJson;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.management.entity.resource.device.DeviceModels;
import org.springframework.stereotype.Component;

/**
 * 查某个能力的参数规格。
 *
 * <p>这里的价值不在于"告诉模型有什么参数"，而在于<b>由服务端给出 capabilityName 和 parameter_N</b>。
 * 这两个标识绝不能由模型拼，因为它们是模型编辑时自动生成的编号，换一份模型数据就变了。
 */
@Component
public class DescribeCapabilityTool implements OperatorTool {

    private static final JsonNode SCHEMA = OperatorJson.schema("""
            {"type":"object","properties":{
              "instance":{"type":"string","description":"设备名，原样传用户说的名字"},
              "capability":{"type":"string","description":"用户说的动作，例如 加热、加压、停止加热。不确定就先不传，会返回全部能力"}
            },"required":["instance"]}
            """);

    private final OperatorDeviceCatalog catalog;

    public DescribeCapabilityTool(OperatorDeviceCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "describe_capability";
    }

    @Override
    public String description() {
        return "查看某台设备某个能力需要哪些参数（含单位、是否必填）。"
                + "准备下发指令前先用它拿到参数编号，不要自己编 parameter 名字。";
    }

    @Override
    public JsonNode parameterSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode execute(JsonNode arguments, OperatorContext context) {
        String query = OperatorJson.text(arguments, "instance");
        String hint = OperatorJson.text(arguments, "capability");
        InstanceResolver.Resolution resolution = catalog.resolver().resolve(query);
        ObjectNode result = OperatorJson.obj();
        if (!resolution.found()) {
            result.put("status", resolution.status().name());
            if (resolution.status() == InstanceResolver.Status.AMBIGUOUS) {
                ArrayNode candidates = result.putArray("candidates");
                for (InstanceResolver.Candidate candidate : resolution.candidates()) {
                    candidates.add(candidate.instanceName());
                }
                result.put("hint", "命中多个设备，先让用户选一个");
            } else {
                result.put("error", resolution.hint() == null ? "没有找到匹配的设备" : resolution.hint());
            }
            return result;
        }
        InstanceResolver.Candidate candidate = resolution.resolved();
        DeviceModels model = catalog.modelOrNull(candidate.modelId());
        if (model == null) {
            result.put("error", "设备模型不存在: " + candidate.modelId());
            return result;
        }
        result.put("resolvedName", candidate.instanceName());
        result.put("instanceId", candidate.id());
        result.put("modelName", model.getModelName());

        if (hint == null || hint.isBlank()) {
            ArrayNode capabilities = result.putArray("capabilities");
            for (CapabilityBinder.CapabilityView capability : CapabilityBinder.capabilities(model)) {
                capabilities.add(summarize(capability));
            }
            result.put("hint", "请告诉我具体要执行哪个动作");
            return result;
        }

        CapabilityBinder.CapabilityView capability = CapabilityBinder.findCapability(model, hint);
        if (capability == null) {
            ArrayNode capabilities = result.putArray("capabilities");
            for (CapabilityBinder.CapabilityView item : CapabilityBinder.capabilities(model)) {
                capabilities.add(summarize(item));
            }
            result.put("error", "这台设备没有识别到「" + hint + "」这个能力，请从下列能力中选择");
            return result;
        }
        result.put("capabilityName", capability.capabilityName());
        result.put("displayName", capability.displayName());
        result.put("commandName", capability.commandName());
        result.put("abort", capability.abort());
        ArrayNode parameters = result.putArray("parameters");
        for (CapabilityBinder.ParameterSpec spec : capability.parameters()) {
            ObjectNode item = parameters.addObject();
            item.put("name", spec.name());
            item.put("displayName", spec.displayName());
            item.put("dataType", spec.dataType());
            if (spec.unit() != null) item.put("unit", spec.unit());
            item.put("required", spec.required());
            if (spec.segment() != null) item.put("segment", spec.segment());
        }
        if (capability.abort()) {
            result.put("note", "这是中止类能力，执行它不需要参数");
        }
        return result;
    }

    private ObjectNode summarize(CapabilityBinder.CapabilityView capability) {
        ObjectNode item = OperatorJson.obj();
        item.put("name", capability.capabilityName());
        item.put("displayName", capability.displayName());
        item.put("abort", capability.abort());
        return item;
    }
}
