package com.smartlab.agent.operator.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorJson;
import com.smartlab.agent.operator.OperatorTool;
import org.springframework.stereotype.Component;

/** 取消当前待确认操作。 */
@Component
public class CancelPendingTool implements OperatorTool {

    private static final JsonNode SCHEMA = OperatorJson.schema("""
            {"type":"object","properties":{
              "reason":{"type":"string","description":"取消原因，可留空"}
            }}
            """);

    @Override
    public String name() {
        return "cancel_pending";
    }

    @Override
    public String description() {
        return "取消当前尚未确认的操作。用户说「算了」「不要了」「取消」时调用。";
    }

    @Override
    public JsonNode parameterSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode execute(JsonNode arguments, OperatorContext context) {
        ObjectNode result = OperatorJson.obj();
        if (context.session().pending() == null) {
            result.put("status", "NOTHING_TO_CANCEL");
            result.put("message", "当前没有待确认的操作");
            return result;
        }
        context.session().clearPending();
        result.put("status", "CANCELLED");
        result.put("message", "已取消，设备没有任何动作");
        return result;
    }
}
