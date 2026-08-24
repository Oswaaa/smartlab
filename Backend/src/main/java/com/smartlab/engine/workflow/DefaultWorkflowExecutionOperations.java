package com.smartlab.engine.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.connection.InterfaceConnectionForwarder;
import com.smartlab.engine.statemachine.StateMachineCommandPort;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;
import com.smartlab.management.service.db.resource.device.DeviceTwinStateService;
import com.smartlab.management.service.db.workflow.WorkflowRuntimeService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class DefaultWorkflowExecutionOperations implements WorkflowExecutionOperations {
    private final WorkflowRuntimeService runtime;
    private final WorkflowTaskResourceService taskResourceService;
    private final DeviceTwinStateService twinStateService;
    private final InterfaceConnectionForwarder interfaceConnections;

    public DefaultWorkflowExecutionOperations(WorkflowRuntimeService runtime,
            WorkflowTaskResourceService taskResourceService,
            StateMachineCommandPort stateMachineCommandPort,
            WorkflowService workflowService,
            DeviceTwinStateService twinStateService) {
        this(runtime, taskResourceService, twinStateService, null);
    }

    @Autowired
    public DefaultWorkflowExecutionOperations(WorkflowRuntimeService runtime,
            WorkflowTaskResourceService taskResourceService,
            DeviceTwinStateService twinStateService,
            @Lazy InterfaceConnectionForwarder interfaceConnections) {
        this.runtime = runtime;
        this.taskResourceService = taskResourceService;
        this.twinStateService = twinStateService;
        this.interfaceConnections = interfaceConnections;
    }

    @Override
    public ObjectNode resolveMappedVariables(Task task, TaskStep step, FlowNode node) {
        ObjectNode result = JsonNodeSupport.objectNode();
        if (!"DEV_NODE".equals(node.getNodeType()) || node.getInVariables() == null || !node.getInVariables().isArray()) {
            return result;
        }
        DeviceInstances instance = taskResourceService.resolveDeviceInstance(task, step, node);
        DeviceTwinStates twin = twinStateService.getByInstanceId(instance.getId());
        JsonNode attributes = twin == null ? null : twin.getCurrentAttr();
        if (attributes == null || !attributes.isObject()) return result;
        for (JsonNode variable : node.getInVariables()) {
            String variableName = variable.path("name").asText("").trim();
            String attributeName = variable.path("attributesMapping").asText("").trim();
            if (variableName.isBlank() || attributeName.isBlank() || !attributes.has(attributeName)) continue;
            JsonNode value = attributes.get(attributeName);
            requireCompatibleType(variableName, attributeName, variable.path("dataType").asText(""), value);
            result.set(variableName, value.deepCopy());
        }
        return result;
    }

    @Override
    public void transitionNodeLifecycle(Task task, TaskStep step, FlowNode node, String targetState) {
        runtime.transitionNodeLifecycle(task, step, node, targetState);
    }

    @Override
    public long resolveDeviceInstance(Task task, TaskStep step, FlowNode node) {
        DeviceInstances instance = taskResourceService.resolveDeviceInstance(task, step, node);
        return instance.getId();
    }

    @Override
    public String ensureMessageId(TaskStep step, long deviceInstanceId, String capabilityName) {
        String seed = "workflow:" + step.getTaskId() + ":" + step.getId() + ":"
                + deviceInstanceId + ":" + capabilityName;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    public DeviceDispatchResult dispatchDeviceSignal(Task task, TaskStep step, FlowNode node, long deviceInstanceId,
                                     String messageId, String nodeOutputInterfaceName, String signalName, JsonNode parameters) {
        return requireForwarder().forwardToDevice(task, step, node, deviceInstanceId, messageId,
                nodeOutputInterfaceName, signalName, parameters);
    }

    @Override
    public void dispatchDeviceAbort(Task task, TaskStep step, FlowNode node, long deviceInstanceId, String messageId) {
        requireForwarder().forwardAbort(task, step, node, deviceInstanceId, messageId);
    }

    private InterfaceConnectionForwarder requireForwarder() {
        if (interfaceConnections == null) {
            throw new IllegalStateException("接口连接转发器未初始化");
        }
        return interfaceConnections;
    }

    private void requireCompatibleType(String variableName, String attributeName, String dataType, JsonNode value) {
        boolean valid = value != null && !value.isNull() && switch (dataType) {
            case "INTEGER" -> value.isIntegralNumber();
            case "DOUBLE" -> value.isNumber();
            case "STRING" -> value.isTextual();
            case "BOOLEAN" -> value.isBoolean();
            case "JSON" -> value.isObject() || value.isArray();
            default -> throw new IllegalArgumentException("变量" + variableName + "声明了不支持的数据类型" + dataType);
        };
        if (!valid) {
            throw new IllegalArgumentException("变量" + variableName + "要求" + dataType
                    + "，设备属性" + attributeName + "的实际类型不一致");
        }
    }
}
