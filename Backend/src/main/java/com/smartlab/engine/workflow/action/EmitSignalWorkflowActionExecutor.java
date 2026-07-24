package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class EmitSignalWorkflowActionExecutor implements WorkflowActionExecutor {
    @Override
    public String actionName() { return "EMIT"; }

    @Override
    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        String targetInterface = action.payload().path("targetInterfaceName").asText("");
        String signalName = action.payload().path("signalName").asText("");
        JsonNode target = interfaceByName(context.node().getInterfaces(), targetInterface);
        if (target == null || !"OUT".equals(target.path("direction").asText())) {
            throw new IllegalArgumentException("EMIT目标接口不存在或不是OUT: " + targetInterface);
        }
        if (!allows(target, signalName)) {
            throw new IllegalArgumentException("EMIT信号不在目标接口allowedSignals中: " + targetInterface + "." + signalName);
        }
        if ("STATE".equals(target.path("interfaceType").asText())) {
            long instanceId = context.operations().resolveDeviceInstance(context.task(), context.node());
            String capabilityName = context.node().getCapability().path("capabilityName").asText("");
            String messageId = context.operations().ensureMessageId(context.step(), instanceId, capabilityName);
            context.operations().dispatchDeviceSignal(context.task(), context.step(), context.node(), instanceId,
                    messageId, targetInterface, signalName, context.node().getCapability().path("capabilityParameters"));
            return WorkflowActionResult.awaitExternalSignal(messageId);
        }
        if ("WORKFLOW".equals(target.path("interfaceType").asText())) {
            return WorkflowActionResult.emitWorkflowSignal(targetInterface, signalName);
        }
        throw new IllegalArgumentException("EMIT不支持的接口类型: " + target.path("interfaceType").asText());
    }

    private JsonNode interfaceByName(JsonNode interfaces, String name) {
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode item : interfaces) if (name.equals(item.path("name").asText())) return item;
        }
        return null;
    }

    private boolean allows(JsonNode target, String signalName) {
        JsonNode allowed = target.path("allowedSignals");
        if (!allowed.isArray()) return false;
        for (JsonNode item : allowed) if (signalName.equals(item.asText())) return true;
        return false;
    }
}
