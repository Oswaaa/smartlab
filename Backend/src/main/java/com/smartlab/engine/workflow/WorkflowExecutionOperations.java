package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;

public interface WorkflowExecutionOperations {
    long resolveDeviceInstance(Task task, FlowNode node);
    String ensureMessageId(TaskStep step, long deviceInstanceId, String capabilityRef);
    void dispatchDeviceSignal(Task task, TaskStep step, FlowNode node, long deviceInstanceId,
                              String messageId, String interfaceType, String signalName, JsonNode parameters);
}
