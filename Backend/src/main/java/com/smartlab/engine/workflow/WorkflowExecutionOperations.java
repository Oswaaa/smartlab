package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;

public interface WorkflowExecutionOperations {
    enum DeviceDispatchResult { ACCEPTED, DEVICE_BUSY }

    default ObjectNode resolveMappedVariables(Task task, TaskStep step, FlowNode node) {
        return JsonNodeSupport.objectNode();
    }

    long resolveDeviceInstance(Task task, TaskStep step, FlowNode node);
    String ensureMessageId(TaskStep step, long deviceInstanceId, String capabilityRef);
    DeviceDispatchResult dispatchDeviceSignal(Task task, TaskStep step, FlowNode node, long deviceInstanceId,
                                              String messageId, String nodeOutputInterfaceName,
                                              String signalName, JsonNode parameters);
    void dispatchDeviceAbort(Task task, TaskStep step, FlowNode node, long deviceInstanceId, String messageId);
}
