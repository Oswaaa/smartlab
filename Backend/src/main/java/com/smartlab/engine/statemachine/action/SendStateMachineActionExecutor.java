package com.smartlab.engine.statemachine.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineModels;
import com.smartlab.engine.statemachine.StateMachineSendActionEvent;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class SendStateMachineActionExecutor implements StateMachineActionExecutor {

    private final ApplicationEventPublisher eventPublisher;
    private final ProtocolDictionaryService protocolDictionaryService;

    public SendStateMachineActionExecutor(ApplicationEventPublisher eventPublisher,
                                          ProtocolDictionaryService protocolDictionaryService) {
        this.eventPublisher = eventPublisher;
        this.protocolDictionaryService = protocolDictionaryService;
    }

    @Override
    public String actionName() {
        return "SEND";
    }

    @Override
    public ObjectNode execute(StateMachineModels.ActionDefinition action, StateMachineModels.EventContext context) {
        JsonNode actionPayload = action.payload();
        if (actionPayload == null || !actionPayload.isObject()) {
            throw new IllegalArgumentException("SEND动作缺少payload");
        }
        String interfaceName = actionPayload.path("interfaceName").asText("");
        String signalName = actionPayload.path("signalName").asText("");
        JsonNode outputInterface = outputInterface(context.model().getStateMachineInterfaces(), interfaceName);
        if (!contains(outputInterface.path("allowedSignals"), signalName)) {
            throw new IllegalArgumentException("SEND信号不在输出接口allowedSignals中: " + signalName);
        }

        Map<String, Object> execution = context.executionContext() == null ? Map.of() : context.executionContext();
        ObjectNode signal = JsonNodeSupport.objectNode();
        signal.put("signalName", signalName);
        switch (signalName) {
            case "CMD_STATE" -> signal.set("payload", commandStatePayload(context, execution));
            case "OP_STATE" -> signal.set("payload", operationStatePayload(context, execution, actionPayload));
            case "CMD_START" -> signal.set("payload", commandPayload(signalName, execution));
            case "CMD_ABORT" -> {
                // 终止能力由状态机根据isAbort=true解析，协议消息不携带业务载荷
            }
            default -> throw new IllegalArgumentException("状态机SEND不支持信号: " + signalName);
        }
        protocolDictionaryService.validateDefinition("SystemSignalFormat", signal);

        String interfaceType = outputInterface.path("interfaceType").asText("");
        if ("ADAPTER".equals(interfaceType)) {
            publishAdapterEvent(context, interfaceName, interfaceType, signalName, execution, signal);
        }
        return signal;
    }

    private ObjectNode commandStatePayload(StateMachineModels.EventContext context, Map<String, Object> execution) {
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("deviceModelId", context.model().getId());
        payload.put("deviceInstanceId", context.instance().getId());
        Object messageId = execution.get("messageId");
        if (messageId == null || String.valueOf(messageId).isBlank()) {
            payload.putNull("messageId");
        } else {
            payload.put("messageId", String.valueOf(messageId));
        }
        payload.put("stateName", context.twinState().getCurrentCmdState());
        payload.put("timestamp", Instant.now().toEpochMilli());
        protocolDictionaryService.validateDefinition("CommandStatePayload", payload);
        return payload;
    }

    private ObjectNode operationStatePayload(StateMachineModels.EventContext context, Map<String, Object> execution,
                                             JsonNode actionPayload) {
        String regionName = actionPayload.path("regionName").asText("");
        if (regionName.isBlank()) regionName = String.valueOf(execution.getOrDefault("regionName", ""));
        if (regionName.isBlank() && context.twinState().getCurrentOpState() != null
                && context.twinState().getCurrentOpState().isObject()
                && context.twinState().getCurrentOpState().size() == 1) {
            regionName = context.twinState().getCurrentOpState().fieldNames().next();
        }
        if (regionName.isBlank()) {
            throw new IllegalArgumentException("OP_STATE缺少regionName");
        }
        String stateName = context.twinState().getCurrentOpState().path(regionName).asText("");
        if (stateName.isBlank()) {
            throw new IllegalArgumentException("OP_STATE找不到分区当前状态: " + regionName);
        }
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("deviceModelId", context.model().getId());
        payload.put("deviceInstanceId", context.instance().getId());
        payload.put("regionName", regionName);
        payload.put("stateName", stateName);
        payload.put("timestamp", Instant.now().toEpochMilli());
        protocolDictionaryService.validateDefinition("OperationStatePayload", payload);
        return payload;
    }

    private ObjectNode commandPayload(String signalName, Map<String, Object> execution) {
        protocolDictionaryService.validateSignalExecutionContext(signalName, execution);
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("commandName", String.valueOf(execution.get("commandName")));
        payload.set("parameters", JsonNodeSupport.toNode(execution.get("parameters")));
        return payload;
    }

    private JsonNode outputInterface(JsonNode interfaces, String name) {
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode item : interfaces) {
                if (name.equals(item.path("name").asText())) {
                    if (!"OUT".equals(item.path("direction").asText())) {
                        throw new IllegalArgumentException("SEND只能引用状态机输出接口: " + name);
                    }
                    return item;
                }
            }
        }
        throw new IllegalArgumentException("SEND动作引用了不存在的状态机接口: " + name);
    }

    private boolean contains(JsonNode values, String expected) {
        if (values.isArray()) {
            for (JsonNode value : values) {
                if (expected.equals(value.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void publishAdapterEvent(StateMachineModels.EventContext context, String interfaceName,
                                     String interfaceType, String signalName, Map<String, Object> execution,
                                     ObjectNode signal) {
        String capabilityName = execution.get("capabilityName") == null ? null : String.valueOf(execution.get("capabilityName"));
        String commandName = execution.get("commandName") == null ? null : String.valueOf(execution.get("commandName"));
        String messageId = execution.get("messageId") == null ? null : String.valueOf(execution.get("messageId"));
        Map<String, Object> parameters = execution.get("parameters") instanceof Map<?, ?> values
                ? (Map<String, Object>) values : Map.of();
        eventPublisher.publishEvent(new StateMachineSendActionEvent(
                context.instance().getId(), interfaceName, interfaceType, signalName,
                capabilityName, commandName, messageId, parameters, signal.deepCopy()));
    }
}
