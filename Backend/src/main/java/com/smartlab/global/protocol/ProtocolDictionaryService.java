package com.smartlab.global.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProtocolDictionaryService {

    private static final String PROTOCOL_DICT_RESOURCE = "schemas/protocol-dict.json";
    private volatile JsonNode dictionary;

    public JsonNode dictionary() {
        JsonNode cached = dictionary;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (dictionary == null) {
                dictionary = loadDictionary();
            }
            return dictionary;
        }
    }

    public JsonNode definition(String definitionName) {
        JsonNode definition = dictionary().path("definitions").path(definitionName);
        if (definition.isMissingNode() || definition.isNull()) {
            throw new IllegalArgumentException("Protocol definition 不存在: " + definitionName);
        }
        return definition;
    }

    public List<String> enumValues(String definitionName) {
        JsonNode values = definition(definitionName).path("enum");
        if (!values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(value.asText());
        }
        return List.copyOf(result);
    }

    public Map<String, String> mqttTopicConvention() {
        JsonNode properties = definition("MqttTopicConvention").path("properties");
        Map<String, String> result = new LinkedHashMap<>();
        properties.fields().forEachRemaining(entry -> {
            String value = entry.getValue().path("const").asText("");
            if (!value.isBlank()) {
                result.put(entry.getKey(), value);
            }
        });
        return Map.copyOf(result);
    }

    public String resolveMqttTopic(String topicName, Map<String, ?> variables) {
        String pattern = mqttTopicConvention().get(topicName);
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("MQTT topic 约定不存在: " + topicName);
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

    public void validateDefinition(String definitionName, JsonNode payload) {
        JsonNode definition = definition(definitionName);
        validateNode(definitionName, definition, payload);
    }

    public ObjectNode frontendMetadata() {
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("dataTypes", textArray(enumValues("DataType")));
        metadata.set("constraintOperators", textArray(enumValues("ConstraintOperator")));
        metadata.set("workflowNodeFunctionTypes", textArray(enumValues("WorkflowNodeFunctionType")));
        metadata.set("communicationProtocols", textArray(enumValues("CommunicationProtocol")));
        metadata.set("systemInterfaceNames", textArray(enumValues("SystemInterfaceName")));
        metadata.set("systemInterfaceTypes", textArray(enumValues("SystemInterfaceType")));
        metadata.set("workflowControlSignals", textArray(enumValues("WorkflowControlSignal")));
        metadata.set("manualControlSignals", textArray(enumValues("ManualControlSignal")));
        metadata.set("constraintControlSignals", textArray(enumValues("ConstraintControlSignal")));
        metadata.set("adapterOutboundSignals", textArray(enumValues("AdapterOutboundSignal")));
        metadata.set("statusSignals", textArray(enumValues("StatusSignal")));
        metadata.set("adapterCommandLifecycleEvents", textArray(enumValues("AdapterCommandLifecycleEvent")));
        metadata.set("commandLifecycleStates", textArray(enumValues("CommandLifecycleState")));
        metadata.set("adapterRegisterFormats",
                textArray(enumValuesFromProperty("AdapterRegisterRequest", "rawConfigFormat")));

        ObjectNode mqttTopics = metadata.putObject("mqttTopics");
        mqttTopicConvention().forEach(mqttTopics::put);
        return metadata;
    }

    public List<String> enumValuesFromProperty(String definitionName, String propertyName) {
        JsonNode values = definition(definitionName).path("properties").path(propertyName).path("enum");
        if (!values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(value.asText());
        }
        return List.copyOf(result);
    }

    private JsonNode loadDictionary() {
        try {
            ClassPathResource resource = new ClassPathResource(PROTOCOL_DICT_RESOURCE);
            try (InputStream inputStream = resource.getInputStream()) {
                return JsonNodeSupport.MAPPER.readTree(inputStream);
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载 protocol-dict.json 失败", e);
        }
    }

    private String resolvePattern(String pattern, Map<String, ?> variables) {
        Matcher matcher = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}").matcher(pattern);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = variables.get(name);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new IllegalArgumentException("MQTT topic 缺少变量: " + name);
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

    private void validateNode(String path, JsonNode schema, JsonNode payload) {
        if (schema == null || schema.isMissingNode() || payload == null || payload.isMissingNode() || payload.isNull()) {
            throw new IllegalArgumentException(path + " 不能为空");
        }
        String expectedType = schema.path("type").asText("");
        if (!expectedType.isBlank() && !matchesType(expectedType, payload)) {
            throw new IllegalArgumentException(path + " 类型应为 " + expectedType);
        }
        JsonNode enumValues = schema.path("enum");
        if (enumValues.isArray()) {
            boolean matched = false;
            for (JsonNode enumValue : enumValues) {
                if (enumValue.asText().equals(payload.asText())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new IllegalArgumentException(path + " 不在协议枚举范围内: " + payload.asText());
            }
        }
        if (schema.has("const") && !schema.path("const").asText().equals(payload.asText())) {
            throw new IllegalArgumentException(path + " 必须为 " + schema.path("const").asText());
        }
        if ("object".equals(expectedType)) {
            validateObject(path, schema, payload);
        }
    }

    private void validateObject(String path, JsonNode schema, JsonNode payload) {
        JsonNode required = schema.path("required");
        for (JsonNode field : required) {
            String fieldName = field.asText();
            if (!payload.has(fieldName) || payload.get(fieldName).isNull()) {
                throw new IllegalArgumentException(path + " 缺少必填字段: " + fieldName);
            }
        }
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            return;
        }
        properties.fields().forEachRemaining(entry -> {
            if (payload.has(entry.getKey()) && !payload.get(entry.getKey()).isNull()) {
                validateNode(path + "." + entry.getKey(), entry.getValue(), payload.get(entry.getKey()));
            }
        });
    }

    private boolean matchesType(String expectedType, JsonNode payload) {
        return switch (expectedType.toLowerCase(Locale.ROOT)) {
            case "object" -> payload.isObject();
            case "array" -> payload.isArray();
            case "string" -> payload.isTextual();
            case "integer" -> payload.isIntegralNumber();
            case "number" -> payload.isNumber();
            case "boolean" -> payload.isBoolean();
            default -> true;
        };
    }

    private ArrayNode textArray(List<String> values) {
        ArrayNode array = JsonNodeSupport.arrayNode();
        values.forEach(array::add);
        return array;
    }
}
