package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DefaultWorkflowExecutionOperations implements WorkflowExecutionOperations {
    private final WorkflowRuntimeService runtime;
    private final WorkflowTaskResourceService taskResourceService;
    private final StateMachineEngine stateMachineEngine;

    public DefaultWorkflowExecutionOperations(WorkflowRuntimeService runtime,
                                              WorkflowTaskResourceService taskResourceService,
                                              StateMachineEngine stateMachineEngine) {
        this.runtime = runtime;
        this.taskResourceService = taskResourceService;
        this.stateMachineEngine = stateMachineEngine;
    }

    public long resolveDeviceInstance(Task task, FlowNode node) {
        JsonNode value = task.getResourceMap() == null ? null : task.getResourceMap().get(String.valueOf(node.getId()));
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0)
            throw new IllegalArgumentException("设备节点未分配实例，flowNodeId=" + node.getId());
        long instanceId = value.asLong();
        taskResourceService.requireUsableInstance(instanceId);
        return instanceId;
    }

    public String ensureMessageId(TaskStep step, long deviceInstanceId, String capabilityRef) {
        String existing = step.getInterfaceInSnapshot() == null ? ""
                : step.getInterfaceInSnapshot().path("messageId").asText("");
        if (!existing.isBlank()) return existing;
        String messageId = UUID.randomUUID().toString();
        ObjectNode snapshot = step.getInterfaceInSnapshot() != null && step.getInterfaceInSnapshot().isObject()
                ? (ObjectNode) step.getInterfaceInSnapshot().deepCopy() : JsonNodeSupport.objectNode();
        snapshot.put("messageId", messageId);
        snapshot.put("deviceInstanceId", deviceInstanceId);
        snapshot.put("capabilityRef", capabilityRef);
        runtime.updateInputSnapshot(step, snapshot);
        return messageId;
    }

    public void dispatchDeviceSignal(Task task, TaskStep step, FlowNode node, long deviceInstanceId,
                                     String messageId, String interfaceType, String signalName, JsonNode parameters) {
        Map<String, Object> context = new HashMap<>();
        context.put("messageId", messageId);
        context.put("commandName", node.getCapability().path("capabilityRef").asText());
        context.put("parameters", parameters != null && parameters.isObject()
                ? JsonNodeSupport.MAPPER.convertValue(parameters, Map.class) : Map.of());
        context.put("taskId", task.getId());
        context.put("taskStepId", step.getId());
        if (stateMachineEngine.dispatchSignalByType(deviceInstanceId, interfaceType, signalName, context).isEmpty())
            throw new IllegalStateException("设备状态机拒绝执行能力: " + node.getCapability().path("capabilityRef").asText());
    }
}
