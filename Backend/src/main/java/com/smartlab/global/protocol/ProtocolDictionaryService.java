package com.smartlab.global.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProtocolDictionaryService {

    private static final String PROTOCOL_DICT_RESOURCE = "schemas/protocol-dict.json";
    private volatile JsonNode dictionary;
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final ConcurrentHashMap<String, JsonSchema> definitionSchemas = new ConcurrentHashMap<>();

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

    public JsonNode signalContract(String signalName) {
        if (signalName == null || signalName.isBlank()) throw new IllegalArgumentException("信号名不能为空");
        JsonNode contract = dictionary().path("x-signalContracts").path(signalName);
        if (!contract.isObject()) throw new IllegalArgumentException("Protocol 未声明系统信号: " + signalName);
        return contract.deepCopy();
    }

    public boolean isSystemSignal(String signalName) {
        return signalName != null && !signalName.isBlank()
                && dictionary().path("x-signalContracts").path(signalName).isObject();
    }
    public boolean requiresCommandPayload(String signalName) {
        return "COMMAND".equals(signalContract(signalName).path("payloadPolicy").asText());
    }

    public void validateSignalExecutionContext(String signalName, Map<String, Object> context) {
        if (!requiresCommandPayload(signalName)) return;
        if (context == null) throw new IllegalArgumentException(signalName + " 必须携带 commandName 和 parameters");
        Object commandName = context.get("commandName");
        if (commandName == null || String.valueOf(commandName).isBlank()) {
            throw new IllegalArgumentException(signalName + " 缺少 commandName");
        }
        Object parameters = context.get("parameters");
        if (!(parameters instanceof Map<?, ?>)
                && !(parameters instanceof JsonNode node && node.isObject())) {
            throw new IllegalArgumentException(signalName + " 的 parameters 必须是对象");
        }
    }

    public void validateDefinition(String definitionName, JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            throw new IllegalArgumentException(definitionName + " 不能为空");
        }
        JsonSchema schema = definitionSchemas.computeIfAbsent(definitionName,
                name -> schemaFactory.getSchema(definition(name)));
        Set<ValidationMessage> messages = schema.validate(payload);
        if (!messages.isEmpty()) {
            StringBuilder error = new StringBuilder("Protocol 校验失败: ").append(definitionName);
            messages.stream().map(ValidationMessage::getMessage).sorted()
                    .forEach(message -> error.append("\n- ").append(message));
            throw new IllegalArgumentException(error.toString());
        }
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
}
