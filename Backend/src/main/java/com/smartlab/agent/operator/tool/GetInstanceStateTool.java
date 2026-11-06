package com.smartlab.agent.operator.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.operator.ActionGuard;
import com.smartlab.agent.operator.CapabilityBinder;
import com.smartlab.agent.operator.InstanceResolver;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorDeviceCatalog;
import com.smartlab.agent.operator.OperatorJson;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.management.entity.resource.device.DeviceModels;
import org.springframework.stereotype.Component;

/** 查一台设备的实时状态与可用能力。 */
@Component
public class GetInstanceStateTool implements OperatorTool {

    private static final JsonNode SCHEMA = OperatorJson.schema("""
            {"type":"object","properties":{
              "instance":{"type":"string","description":"设备名，原样传用户说的名字，例如 反应釜1、1号反应釜、Reactor1"}
            },"required":["instance"]}
            """);

    private final OperatorDeviceCatalog catalog;

    public GetInstanceStateTool(OperatorDeviceCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "get_instance_state";
    }

    @Override
    public String description() {
        return "查询一台设备的实时状态（在线、指令状态）以及它能执行哪些能力。"
                + "用户问「现在什么状态」「能做什么」时用它。";
    }

    @Override
    public JsonNode parameterSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode execute(JsonNode arguments, OperatorContext context) {
        String query = OperatorJson.text(arguments, "instance");
        InstanceResolver.Resolution resolution = catalog.resolver().resolve(query);
        ObjectNode result = OperatorJson.obj();
        result.put("query", query);
        result.put("status", resolution.status().name());
        if (resolution.status() == InstanceResolver.Status.AMBIGUOUS) {
            result.put("hint", "命中多个设备，必须让用户从中选择一个，不要自己挑");
            ArrayNode candidates = result.putArray("candidates");
            for (InstanceResolver.Candidate candidate : resolution.candidates()) {
                ObjectNode item = candidates.addObject();
                item.put("id", candidate.id());
                item.put("name", candidate.instanceName());
                item.put("point", candidate.devicePoint());
                item.put("modelName", catalog.modelName(candidate.modelId()));
            }
            return result;
        }
        if (!resolution.found()) {
            result.put("error", resolution.hint() == null ? "没有找到匹配的设备" : resolution.hint());
            return result;
        }

        InstanceResolver.Candidate candidate = resolution.resolved();
        result.put("resolvedName", candidate.instanceName());
        result.put("instanceId", candidate.id());
        result.put("point", candidate.devicePoint());
        result.put("modelName", catalog.modelName(candidate.modelId()));

        ActionGuard.Snapshot snapshot = catalog.snapshot(candidate.id());
        result.put("online", "ONLINE".equalsIgnoreCase(snapshot.onlineStatus()));
        result.put("onlineStatus", snapshot.onlineStatus());
        result.put("cmdState", snapshot.cmdState());
        result.put("lifecycle", snapshot.lifecycleStatus());
        result.put("adapterStatus", snapshot.adapterStatus());

        DeviceModels model = catalog.modelOrNull(candidate.modelId());
        ArrayNode capabilities = result.putArray("capabilities");
        for (CapabilityBinder.CapabilityView capability : CapabilityBinder.capabilities(model)) {
            ObjectNode item = capabilities.addObject();
            item.put("name", capability.capabilityName());
            item.put("displayName", capability.displayName());
            item.put("abort", capability.abort());
            item.put("hasParameters", capability.hasParameters());
        }
        return result;
    }
}
