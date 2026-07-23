package com.smartlab.engine.statemachine.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineModels;
import com.smartlab.engine.statemachine.StateMachineSendActionEvent;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.global.util.JsonSchemaValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SendStateMachineActionExecutor implements StateMachineActionExecutor {
    private final ApplicationEventPublisher eventPublisher;
    private final JsonSchemaValidationService schemaValidation;
    private final ProtocolDictionaryService protocolDictionaryService;

    public SendStateMachineActionExecutor(ApplicationEventPublisher eventPublisher,
                                          JsonSchemaValidationService schemaValidation) {
        this(eventPublisher, schemaValidation, new ProtocolDictionaryService());
    }

    @Autowired
    public SendStateMachineActionExecutor(ApplicationEventPublisher eventPublisher,
                                          JsonSchemaValidationService schemaValidation,
                                          ProtocolDictionaryService protocolDictionaryService) {
        this.eventPublisher = eventPublisher;
        this.schemaValidation = schemaValidation;
        this.protocolDictionaryService = protocolDictionaryService;
    }

    public String actionName() { return "SEND"; }

    public ObjectNode execute(StateMachineModels.ActionDefinition action, StateMachineModels.EventContext context) {
        JsonNode payloadDefinition = action.payload();
        if (payloadDefinition == null || !payloadDefinition.isObject()) throw new IllegalArgumentException("SEND 动作缺少 payload");
        String interfaceName = payloadDefinition.path("interfaceName").asText("");
        String signalName = payloadDefinition.path("signalName").asText("");
        JsonNode outputInterface = outputInterface(context.model().getStateMachineInterfaces(), interfaceName);
        if (!contains(outputInterface.path("allowedSignals"), signalName))
            throw new IllegalArgumentException("SEND 信号不在输出接口 allowedSignals 中: " + signalName);

        Map<String, Object> execution = context.executionContext() == null ? Map.of() : context.executionContext();
        ObjectNode signal = JsonNodeSupport.objectNode().put("signalName", signalName);
        if ("CMD_STATE".equals(signalName)) {
            signal.putObject("payload").put("state", declaredState(payloadDefinition, context.twinState().getCurrentCmdState()));
        } else if ("OP_STATE".equals(signalName)) {
            signal.putObject("payload").set("state", declaredState(payloadDefinition, context.twinState().getCurrentOpState()));
        } else if ("CMD_START".equals(signalName)) {
            protocolDictionaryService.validateSignalExecutionContext(signalName, execution);
            signal.putObject("payload")
                    .put("commandName", String.valueOf(execution.get("commandName")))
                    .set("parameters", JsonNodeSupport.toNode(execution.get("parameters")));
        }
        schemaValidation.validateDefinition(signal, "protocol-dict.json", "SystemSignalFormat");

        String interfaceType = outputInterface.path("interfaceType").asText("");
        if ("ADAPTER".equals(interfaceType)) publishAdapterEvent(context, interfaceName, interfaceType, signalName, execution, signal);
        return signal;
    }

    private JsonNode outputInterface(JsonNode interfaces, String name) {
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode item : interfaces) {
                if (name.equals(item.path("name").asText())) {
                    if (!"OUT".equals(item.path("direction").asText()))
                        throw new IllegalArgumentException("SEND 只能引用状态机输出接口: " + name);
                    return item;
                }
            }
        }
        throw new IllegalArgumentException("SEND 动作引用了不存在的状态机接口: " + name);
    }

    private boolean contains(JsonNode values, String expected) {
        if (values.isArray()) for (JsonNode value : values) if (expected.equals(value.asText())) return true;
        return false;
    }

    private String declaredState(JsonNode payload, String currentState) {
        String state = payload.path("stateName").asText("");
        return state.isBlank() ? currentState : state;
    }

    private JsonNode declaredState(JsonNode payload, JsonNode currentState) {
        String state = payload.path("stateName").asText("");
        if (!state.isBlank()) {
            return JsonNodeSupport.objectNode().textNode(state);
        }
        return currentState == null ? JsonNodeSupport.objectNode() : currentState;
    }

    @SuppressWarnings("unchecked")
    private void publishAdapterEvent(StateMachineModels.EventContext context, String interfaceName,
                                     String interfaceType, String signalName, Map<String, Object> execution,
                                     ObjectNode signal) {
        String commandName = execution.get("commandName") == null ? null : String.valueOf(execution.get("commandName"));
        String messageId = execution.get("messageId") == null ? null : String.valueOf(execution.get("messageId"));
        Map<String, Object> parameters = execution.get("parameters") instanceof Map<?, ?> values
                ? (Map<String, Object>) values : Map.of();
        eventPublisher.publishEvent(new StateMachineSendActionEvent(
                context.instance().getId(), interfaceName, interfaceType, signalName,
                commandName, messageId, parameters, signal.deepCopy()));
    }
}
