package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
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
    private final WorkflowService workflowService;

    public DefaultWorkflowExecutionOperations(WorkflowRuntimeService runtime,
                                              WorkflowTaskResourceService taskResourceService,
                                              StateMachineEngine stateMachineEngine,
                                              WorkflowService workflowService) {
        this.runtime = runtime;
        this.taskResourceService = taskResourceService;
        this.stateMachineEngine = stateMachineEngine;
        this.workflowService = workflowService;
    }

    @Override
    public long resolveDeviceInstance(Task task, TaskStep step, FlowNode node) {
        DeviceInstances instance = taskResourceService.resolveDeviceInstance(task, step, node);
        return instance.getId();
    }

    @Override
    public String ensureMessageId(TaskStep step, long deviceInstanceId, String capabilityName) {
        String existing = step.getInterfaceInSnapshot() == null ? ""
                : step.getInterfaceInSnapshot().path("messageId").asText("");
        if (!existing.isBlank()) return existing;
        String messageId = UUID.randomUUID().toString();
        ObjectNode snapshot = step.getInterfaceInSnapshot() != null && step.getInterfaceInSnapshot().isObject()
                ? (ObjectNode) step.getInterfaceInSnapshot().deepCopy() : JsonNodeSupport.objectNode();
        snapshot.put("messageId", messageId);
        snapshot.put("deviceInstanceId", deviceInstanceId);
        snapshot.put("capabilityName", capabilityName);
        runtime.updateInputSnapshot(step, snapshot);
        return messageId;
    }

    @Override
    public DeviceDispatchResult dispatchDeviceSignal(Task task, TaskStep step, FlowNode node, long deviceInstanceId,
                                     String messageId, String nodeOutputInterfaceName, String signalName, JsonNode parameters) {
        DeviceRoute route = deviceRoute(node);
        if (!route.nodeOutputInterfaceName().equals(nodeOutputInterfaceName)) {
            throw new IllegalArgumentException("STATE动作输出接口未连接到设备模型: " + nodeOutputInterfaceName);
        }
        Map<String, Object> context = new HashMap<>();
        context.put("messageId", messageId);
        context.put("capabilityName", node.getCapability().path("capabilityName").asText());
        context.put("parameters", parameters != null && parameters.isObject()
                ? JsonNodeSupport.MAPPER.convertValue(parameters, Map.class) : Map.of());
        context.put("taskId", task.getId());
        context.put("taskStepId", step.getId());
        return stateMachineEngine.dispatchSignal(deviceInstanceId, route.deviceInputInterfaceName(), signalName, context).isEmpty()
                ? DeviceDispatchResult.DEVICE_BUSY : DeviceDispatchResult.ACCEPTED;
    }

    @Override
    public void dispatchDeviceAbort(Task task, TaskStep step, FlowNode node, long deviceInstanceId, String messageId) {
        DeviceRoute route = deviceRoute(node);
        Map<String, Object> context = new HashMap<>();
        context.put("messageId", messageId);
        context.put("taskId", task.getId());
        context.put("taskStepId", step.getId());
        if (stateMachineEngine.dispatchSignal(deviceInstanceId, route.deviceInputInterfaceName(), "WF_EXECUTE_ABORT", context).isEmpty()) {
            throw new IllegalStateException("设备状态机未接受WF_EXECUTE_ABORT");
        }
    }

    private DeviceRoute deviceRoute(FlowNode node) {
        WorkflowDetailResponse definition = workflowService.getDefinition(node.getFlowModelId());
        if (definition == null) throw new IllegalArgumentException("工作流模型不存在: " + node.getFlowModelId());
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        DeviceRoute result = null;
        for (JsonNode connection : iterable(definition.getInterfaceConnections())) {
            if (!"NODE_TO_DEVICE".equals(connection.path("connectionType").asText())) continue;
            Long sourceRef = compiled.refsByNodeName().get(connection.path("source").path("nodeName").asText(""));
            if (sourceRef == null || sourceRef.longValue() != node.getNodeIdRef()) continue;
            long deviceModelId = connection.path("target").path("deviceModelId").asLong(0);
            String sourceInterface = connection.path("source").path("interfaceName").asText("");
            String targetInterface = connection.path("target").path("interfaceName").asText("");
            if (deviceModelId <= 0 || !Long.valueOf(deviceModelId).equals(node.getDeviceModelId())
                    || sourceInterface.isBlank() || targetInterface.isBlank()) {
                throw new IllegalArgumentException("NODE_TO_DEVICE设备模型连接不完整或与DEV_NODE不一致: " + node.getNodeIdRef());
            }
            if (result != null) throw new IllegalStateException("DEV_NODE只能有一个NODE_TO_DEVICE连接: " + node.getNodeIdRef());
            result = new DeviceRoute(sourceInterface, targetInterface);
        }
        if (result == null) throw new IllegalStateException("DEV_NODE缺少NODE_TO_DEVICE设备模型连接: " + node.getNodeIdRef());
        return result;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : java.util.List.of();
    }

    private record DeviceRoute(String nodeOutputInterfaceName, String deviceInputInterfaceName) {
    }
}