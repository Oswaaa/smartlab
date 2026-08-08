package com.smartlab.global.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.ProtocolContract;
import com.smartlab.global.contract.SignalPayloadPolicy;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 协议契约访问入口。协议规范已经定稿，运行时直接使用固定Java契约，不再读取或解释JSONSchema文件
 */
@Service
public class ProtocolDictionaryService {

    public JsonNode dictionary() {
        ObjectNode root = JsonNodeSupport.objectNode();
        ObjectNode definitions = root.putObject("definitions");
        for (String definitionName : protocolDefinitionNames()) {
            definitions.set(definitionName, definition(definitionName));
        }
        return root;
    }

    public JsonNode definition(String definitionName) {
        if (ProtocolContract.hasEnumeration(definitionName)) {
            ObjectNode definition = JsonNodeSupport.objectNode();
            definition.put("type", "string");
            ArrayNode values = definition.putArray("enum");
            enumValues(definitionName).forEach(values::add);
            return definition;
        }
        return switch (definitionName) {
            case "MqttTopicConvention" -> mqttTopicDefinition();
            case "AdapterRegisterRequest" -> objectDefinition(Map.of(
                    "adapterName", "string", "rawConfigFormat", "string", "rawConfigContent", "string", "timestamp", "number"));
            case "CommandMessageFormat" -> objectDefinition(Map.of(
                    "messageId", "string", "adapterName", "string", "devicePoint", "string", "commandName", "string", "parameters", "object", "timestamp", "number"));
            case "TelemetryMessageFormat" -> objectDefinition(Map.of(
                    "timestamp", "number", "adapterName", "string", "devicePoint", "string", "telemetryData", "object"));
            case "EventMessageFormat" -> objectDefinition(Map.of(
                    "timestamp", "number", "adapterName", "string", "devicePoint", "string", "eventName", "string", "payload", "object"));
            case "AdapterHeartbeat" -> objectDefinition(Map.of("status", "string", "timestamp", "number"));
            case "ConstraintExecutePayload" -> objectDefinition(Map.of(
                    "deviceInstanceId", "integer", "capabilityName", "string", "parameters", "object"));
            case "CommandStatePayload" -> objectDefinition(Map.of(
                    "deviceModelId", "integer", "deviceInstanceId", "integer", "messageId", "string", "stateName", "string", "timestamp", "number"));
            case "OperationStatePayload" -> objectDefinition(Map.of(
                    "deviceModelId", "integer", "deviceInstanceId", "integer", "regionName", "string", "regionType", "string", "state", "array", "timestamp", "number"));
            case "SystemSignalFormat" -> objectDefinition(Map.of("signalName", "string", "payload", "object"));
            default -> throw new IllegalArgumentException("协议定义不存在: " + definitionName);
        };
    }

    public List<String> enumValues(String definitionName) {
        return ProtocolContract.enumValues(definitionName);
    }

    public Map<String, String> mqttTopicConvention() {
        return ProtocolContract.mqttTopics();
    }

    public String resolveMqttTopic(String topicName, Map<String, ?> variables) {
        String pattern = mqttTopicConvention().get(topicName);
        if (pattern == null) {
            throw new IllegalArgumentException("MQTT主题约定不存在: " + topicName);
        }
        return resolvePattern(pattern, variables == null ? Map.of() : variables);
    }

    public Optional<ProtocolTopicMatch> matchMqttTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> entry : mqttTopicConvention().entrySet()) {
            Optional<Map<String, String>> variables = matchPattern(entry.getValue(), topic);
            if (variables.isPresent()) {
                return Optional.of(new ProtocolTopicMatch(entry.getKey(), variables.get()));
            }
        }
        return Optional.empty();
    }

    public JsonNode signalContract(String signalName) {
        SignalPayloadPolicy policy = ProtocolContract.payloadPolicy(signalName);
        ObjectNode contract = JsonNodeSupport.objectNode();
        contract.put("signalName", signalName);
        contract.put("payloadPolicy", policy.name());
        return contract;
    }

    public boolean isSystemSignal(String signalName) {
        return ProtocolContract.isSystemSignal(signalName);
    }

    public boolean requiresCommandPayload(String signalName) {
        return ProtocolContract.payloadPolicy(signalName) == SignalPayloadPolicy.COMMAND;
    }

    public void validateSignalExecutionContext(String signalName, Map<String, Object> context) {
        SignalPayloadPolicy policy = ProtocolContract.payloadPolicy(signalName);
        if (policy == SignalPayloadPolicy.NONE) {
            return;
        }
        if (context == null) {
            throw new IllegalArgumentException(signalName + " 缺少执行上下文");
        }
        switch (policy) {
            case COMMAND -> requireCommandContext(signalName, context);
            case CONSTRAINT_EXECUTE -> requireConstraintExecuteContext(context);
            case COMMAND_STATE, OPERATION_STATE -> throw new IllegalArgumentException(signalName + " 只能由状态机广播");
            case NONE -> {
            }
        }
    }

    public void validateDefinition(String definitionName, JsonNode payload) {
        requireObject(definitionName, payload);
        switch (definitionName) {
            case "SystemSignalFormat" -> validateSystemSignal(payload);
            case "AdapterRegisterRequest" -> {
                requireText(payload, "adapterName");
                requireEnum(payload, "rawConfigFormat", enumValues("AdapterRegisterRawConfigFormat"));
                requireText(payload, "rawConfigContent");
                requireNumber(payload, "timestamp");
            }
            case "CommandMessageFormat" -> {
                requireText(payload, "messageId");
                requireText(payload, "adapterName");
                requireText(payload, "devicePoint");
                requireText(payload, "commandName");
                requireObjectField(payload, "parameters");
                requireNumber(payload, "timestamp");
                rejectField(payload, "operation");
            }
            case "TelemetryMessageFormat" -> {
                requireNumber(payload, "timestamp");
                requireText(payload, "adapterName");
                requireText(payload, "devicePoint");
                requireObjectField(payload, "telemetryData");
            }
            case "EventMessageFormat" -> {
                requireNumber(payload, "timestamp");
                requireText(payload, "adapterName");
                requireText(payload, "devicePoint");
                requireText(payload, "eventName");
                if (payload.has("payload")) {
                    requireObjectField(payload, "payload");
                }
            }
            case "AdapterHeartbeat" -> {
                requireEnum(payload, "status", List.of("ALIVE", "ONLINE"));
                requireNumber(payload, "timestamp");
            }
            case "ConstraintExecutePayload" -> {
                requireNumber(payload, "deviceInstanceId");
                requireText(payload, "capabilityName");
                requireObjectField(payload, "parameters");
            }
            case "CommandStatePayload" -> {
                requireNumber(payload, "deviceModelId");
                requireNumber(payload, "deviceInstanceId");
                requireNullableText(payload, "messageId");
                requireText(payload, "stateName");
                requireNumber(payload, "timestamp");
            }
            case "OperationStatePayload" -> {
                requireNumber(payload, "deviceModelId");
                requireNumber(payload, "deviceInstanceId");
                requireText(payload, "regionName");
                requireEnum(payload, "regionType", List.of("OPERATIONAL", "EXCEPTION"));
                JsonNode state = payload.get("state");
                if (state == null || !state.isArray()) throw new IllegalArgumentException("state必须是数组");
                Set<String> values = new java.util.HashSet<>();
                for (JsonNode item : state) {
                    if (!item.isTextual() || item.asText().isBlank() || !values.add(item.asText())) {
                        throw new IllegalArgumentException("state必须是非空且不重复的字符串数组");
                    }
                }
                if ("OPERATIONAL".equals(payload.path("regionType").asText()) && state.size() != 1) {
                    throw new IllegalArgumentException("OPERATIONAL区域state必须恰好包含一个状态");
                }
                requireNumber(payload, "timestamp");
            }
            default -> throw new IllegalArgumentException("不支持的协议报文定义: " + definitionName);
        }
    }

    public List<String> enumValuesFromProperty(String definitionName, String propertyName) {
        if ("AdapterRegisterRequest".equals(definitionName) && "rawConfigFormat".equals(propertyName)) {
            return enumValues("AdapterRegisterRawConfigFormat");
        }
        return List.of();
    }

    private List<String> protocolDefinitionNames() {
        return List.of(
                "DataType", "CommunicationProtocol", "WorkflowNodeSignal", "WorkflowControlSignal", "ManualControlSignal",
                "ConstraintControlSignal", "AdapterOutboundSignal", "StatusSignal", "MqttTopicConvention", "AdapterRegisterRequest",
                "CommandMessageFormat", "TelemetryMessageFormat", "EventMessageFormat", "AdapterHeartbeat", "ConstraintExecutePayload",
                "CommandStatePayload", "OperationStatePayload", "SystemSignalFormat");
    }

    private ObjectNode mqttTopicDefinition() {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.put("type", "object");
        ObjectNode properties = definition.putObject("properties");
        mqttTopicConvention().forEach((name, pattern) -> properties.putObject(name).put("const", pattern));
        return definition;
    }

    private ObjectNode objectDefinition(Map<String, String> propertyTypes) {
        ObjectNode definition = JsonNodeSupport.objectNode();
        definition.put("type", "object");
        ObjectNode properties = definition.putObject("properties");
        propertyTypes.forEach((name, type) -> properties.putObject(name).put("type", type));
        return definition;
    }

    private void validateSystemSignal(JsonNode payload) {
        String signalName = requireText(payload, "signalName");
        if (!isSystemSignal(signalName)) {
            throw new IllegalArgumentException("协议未声明系统信号: " + signalName);
        }
        JsonNode signalPayload = payload.get("payload");
        if (signalPayload != null && !signalPayload.isNull() && !signalPayload.isObject()) {
            throw new IllegalArgumentException("payload 必须是对象");
        }
        SignalPayloadPolicy policy = ProtocolContract.payloadPolicy(signalName);
        if (policy == SignalPayloadPolicy.NONE) {
            return;
        }
        if (signalPayload == null || !signalPayload.isObject()) {
            throw new IllegalArgumentException(signalName + " 缺少payload");
        }
        switch (policy) {
            case COMMAND -> requireCommandContext(signalName, JsonNodeSupport.MAPPER.convertValue(signalPayload, Map.class));
            case CONSTRAINT_EXECUTE -> validateDefinition("ConstraintExecutePayload", signalPayload);
            case COMMAND_STATE -> validateDefinition("CommandStatePayload", signalPayload);
            case OPERATION_STATE -> validateDefinition("OperationStatePayload", signalPayload);
            case NONE -> {
            }
        }
    }

    private void requireCommandContext(String signalName, Map<String, Object> context) {
        Object commandName = context.get("commandName");
        if (commandName == null || String.valueOf(commandName).isBlank()) {
            throw new IllegalArgumentException(signalName + " 缺少commandName");
        }
        Object parameters = context.get("parameters");
        if (!(parameters instanceof Map<?, ?>) && !(parameters instanceof JsonNode node && node.isObject())) {
            throw new IllegalArgumentException(signalName + " 的parameters必须是对象");
        }
    }

    private void requireConstraintExecuteContext(Map<String, Object> context) {
        validateDefinition("ConstraintExecutePayload", JsonNodeSupport.toNode(context));
    }

    private void requireObject(String definitionName, JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw new IllegalArgumentException(definitionName + " 必须是对象");
        }
    }

    private String requireText(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(name + " 必须是非空字符串");
        }
        return value.asText();
    }

    private void requireNullableText(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        if (value == null || (!value.isNull() && (!value.isTextual() || value.asText().isBlank()))) {
            throw new IllegalArgumentException(name + " 必须是字符串或null");
        }
    }

    private void requireNumber(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(name + " 必须是数字");
        }
    }

    private void requireObjectField(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(name + " 必须是对象");
        }
    }

    private void requireEnum(JsonNode payload, String name, List<String> acceptedValues) {
        String value = requireText(payload, name);
        if (!acceptedValues.contains(value)) {
            throw new IllegalArgumentException(name + " 取值不合法: " + value);
        }
    }

    private void rejectField(JsonNode payload, String name) {
        if (payload.has(name)) {
            throw new IllegalArgumentException("定稿协议不允许字段: " + name);
        }
    }

    private String resolvePattern(String pattern, Map<String, ?> variables) {
        Matcher matcher = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}").matcher(pattern);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = variables.get(name);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new IllegalArgumentException("MQTT主题缺少变量: " + name);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value).trim()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Optional<Map<String, String>> matchPattern(String pattern, String topic) {
        Matcher placeholders = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}").matcher(pattern);
        StringBuilder regex = new StringBuilder("^");
        List<String> names = new ArrayList<>();
        int last = 0;
        while (placeholders.find()) {
            regex.append(Pattern.quote(pattern.substring(last, placeholders.start())));
            regex.append("([^/]+)");
            names.add(placeholders.group(1));
            last = placeholders.end();
        }
        regex.append(Pattern.quote(pattern.substring(last))).append("$");
        Matcher matcher = Pattern.compile(regex.toString()).matcher(topic);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            variables.put(names.get(i), matcher.group(i + 1));
        }
        return Optional.of(Map.copyOf(variables));
    }
}
