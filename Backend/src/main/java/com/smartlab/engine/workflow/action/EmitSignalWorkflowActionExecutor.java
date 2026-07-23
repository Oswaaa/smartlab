package com.smartlab.engine.workflow.action;

import org.springframework.stereotype.Component;

@Component
public class EmitSignalWorkflowActionExecutor implements WorkflowActionExecutor {
    public String actionName() { return "EMIT_SIGNAL"; }

    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        if (!"DEVICE_CAPABILITY_NODE".equals(context.node().getNodeType()))
            throw new IllegalArgumentException("EMIT_SIGNAL 只能用于设备能力节点");
        if (context.operations() == null) throw new IllegalStateException("EMIT_SIGNAL 缺少执行操作边界");
        String interfaceType = action.payload().path("interfaceType").asText("");
        String signalName = action.payload().path("signalName").asText("");
        if (!"WORKFLOW".equals(interfaceType) || signalName.isBlank())
            throw new IllegalArgumentException("EMIT_SIGNAL 必须声明 WORKFLOW 接口类型和 signalName");
        String capabilityRef = context.node().getCapability().path("capabilityRef").asText("");
        if (capabilityRef.isBlank()) throw new IllegalArgumentException("设备能力节点缺少 capabilityRef");

        String existing = context.step().getInterfaceInSnapshot() == null ? ""
                : context.step().getInterfaceInSnapshot().path("messageId").asText("");
        long instanceId = context.operations().resolveDeviceInstance(context.task(), context.node());
        String messageId = existing.isBlank()
                ? context.operations().ensureMessageId(context.step(), instanceId, capabilityRef)
                : existing;
        if (existing.isBlank()) {
            context.operations().dispatchDeviceSignal(
                    context.task(), context.step(), context.node(), instanceId, messageId,
                    interfaceType, signalName, context.node().getCapability().path("parameters"));
        }
        return WorkflowActionResult.awaitExternalSignal(messageId);
    }
}
