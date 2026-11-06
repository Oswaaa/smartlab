package com.smartlab.agent.operator.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.operator.InstanceResolver;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorDeviceCatalog;
import com.smartlab.agent.operator.OperatorJson;
import com.smartlab.agent.operator.OperatorTool;
import org.springframework.stereotype.Component;

import java.util.List;

/** 列出设备实例，供模型在不知道确切名字时先看一眼。 */
@Component
public class ListInstancesTool implements OperatorTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final JsonNode SCHEMA = OperatorJson.schema("""
            {"type":"object","properties":{
              "keyword":{"type":"string","description":"设备名关键词，例如 反应釜、流量计、电磁阀。留空则列出前若干台"},
              "limit":{"type":"integer","description":"最多返回多少台，默认 20"}
            }}
            """);

    private final OperatorDeviceCatalog catalog;

    public ListInstancesTool(OperatorDeviceCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "list_instances";
    }

    @Override
    public String description() {
        return "按关键词列出设备实例，返回 id、真实名称、点位名、型号、在线状态和指令状态。"
                + "不确定设备叫什么的时候先调用它。";
    }

    @Override
    public JsonNode parameterSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode execute(JsonNode arguments, OperatorContext context) {
        String keyword = OperatorJson.text(arguments, "keyword");
        Integer limit = OperatorJson.longValue(arguments, "limit") == null
                ? DEFAULT_LIMIT : OperatorJson.longValue(arguments, "limit").intValue();
        List<InstanceResolver.Candidate> hits = catalog.resolver().search(keyword);
        ObjectNode result = OperatorJson.obj();
        result.put("matched", hits.size());
        result.put("returned", Math.min(hits.size(), Math.max(1, limit)));
        var items = result.putArray("items");
        for (int i = 0; i < hits.size() && i < Math.max(1, limit); i++) {
            InstanceResolver.Candidate candidate = hits.get(i);
            ObjectNode item = items.addObject();
            item.put("id", candidate.id());
            item.put("name", candidate.instanceName());
            item.put("point", candidate.devicePoint());
            item.put("modelId", candidate.modelId());
            item.put("modelName", catalog.modelName(candidate.modelId()));
            item.put("controllable", candidate.controllable());
        }
        if (hits.size() > Math.max(1, limit)) {
            result.put("truncated", true);
            result.put("hint", "结果已截断，请用更精确的关键词再查");
        }
        return result;
    }
}
