package com.smartlab.management.service.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.AdapterIndex;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parses and validates the adapter-side capability manifest.
 * The manifest is adapter-owned: system UI consumes it, while the adapter runtime
 * can use the same structure as its command routing description.
 */
@Service
public class AdapterManifestService {

    public static final String SPEC_VERSION = "smartlab.adapter.config.v1";

    public ObjectNode parseRawConfig(String adapterName, String rawConfigFormat, String rawConfigContent) {
        if (rawConfigContent == null || rawConfigContent.isBlank()) {
            throw new IllegalArgumentException("Adapter 配置内容不能为空");
        }
        String format = rawConfigFormat == null ? "JSON" : rawConfigFormat.trim().toUpperCase(Locale.ROOT);
        JsonNode raw;
        if ("JSON".equals(format) || rawConfigContent.trim().startsWith("{")) {
            raw = readJson(rawConfigContent);
        } else if ("INI".equals(format)) {
            raw = parseIniConfig(adapterName, rawConfigContent);
        } else {
            throw new IllegalArgumentException("暂不支持的 Adapter 配置格式: " + format);
        }
        ObjectNode normalized = normalize(raw, adapterName, format);
        validate(normalized);
        return normalized;
    }

    public ObjectNode normalize(JsonNode source, String fallbackAdapterName, String rawConfigFormat) {
        JsonNode root = firstObject(source, "parsedConfig", "parsed_config", "adapterManifest", "manifest", "adapterContract");
        if (root == null || root.isMissingNode() || root.isNull()) {
            root = source == null ? JsonNodeSupport.objectNode() : source;
        }

        ObjectNode manifest = JsonNodeSupport.objectNode();
        manifest.put("specVersion", text(root, "specVersion", SPEC_VERSION));
        manifest.put("adapterName", firstText(root, fallbackAdapterName, "adapterName", "name"));
        manifest.put("rawConfigFormat", rawConfigFormat == null ? text(root, "rawConfigFormat", "JSON") : rawConfigFormat);

        ArrayNode templates = manifest.putArray("deviceTemplates");
        for (JsonNode template : array(root.get("deviceTemplates"))) {
            templates.add(normalizeTemplate(template));
        }

        ArrayNode points = manifest.putArray("devicePoints");
        for (JsonNode point : array(root.get("devicePoints"))) {
            points.add(normalizeDevicePoint(point));
        }

        return manifest;
    }

    public void validate(JsonNode manifest) {
        if (manifest == null || !manifest.isObject()) {
            throw new IllegalArgumentException("Adapter manifest 必须是 JSON object");
        }
        String adapterName = manifest.path("adapterName").asText("");
        if (adapterName.isBlank()) {
            throw new IllegalArgumentException("Adapter manifest 缺少 adapterName");
        }

        Map<String, JsonNode> templates = new LinkedHashMap<>();
        for (JsonNode template : array(manifest.get("deviceTemplates"))) {
            String templateName = template.path("templateName").asText("");
            if (templateName.isBlank()) {
                throw new IllegalArgumentException("deviceTemplates 中存在空 templateName");
            }
            if (templates.put(templateName, template) != null) {
                throw new IllegalArgumentException("deviceTemplates 存在重复 templateName: " + templateName);
            }
            validateTemplate(template);
        }
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("Adapter manifest 至少需要一个 deviceTemplate");
        }

        Set<String> pointNames = new HashSet<>();
        for (JsonNode point : array(manifest.get("devicePoints"))) {
            String devicePoint = point.path("devicePoint").asText("");
            String templateName = point.path("templateName").asText("");
            if (devicePoint.isBlank()) {
                throw new IllegalArgumentException("devicePoints 中存在空 devicePoint");
            }
            if (!pointNames.add(devicePoint)) {
                throw new IllegalArgumentException("devicePoints 存在重复 devicePoint: " + devicePoint);
            }
            JsonNode template = templates.get(templateName);
            if (template == null) {
                throw new IllegalArgumentException("devicePoint " + devicePoint + " 引用了不存在的 templateName: " + templateName);
            }
            validatePointAgainstTemplate(point, template);
        }
    }

    public JsonNode findTemplate(JsonNode manifest, String templateName) {
        if (manifest == null || templateName == null || templateName.isBlank()) {
            return null;
        }
        for (JsonNode template : array(manifest.get("deviceTemplates"))) {
            if (templateName.equals(template.path("templateName").asText())) {
                return template;
            }
        }
        return null;
    }

    public JsonNode findDevicePoint(JsonNode manifest, String devicePoint) {
        if (manifest == null || devicePoint == null || devicePoint.isBlank()) {
            return null;
        }
        for (JsonNode point : array(manifest.get("devicePoints"))) {
            if (devicePoint.equals(point.path("devicePoint").asText())) {
                return point;
            }
        }
        return null;
    }

    public String templateNameForPoint(JsonNode manifest, String devicePoint) {
        JsonNode point = findDevicePoint(manifest, devicePoint);
        return point == null ? null : point.path("templateName").asText(null);
    }

    public ObjectNode buildAdapterContract(AdapterIndex adapter, String templateName) {
        if (adapter == null || adapter.getParsedConfig() == null) {
            throw new IllegalArgumentException("Adapter 未注册或没有解析后的配置");
        }
        JsonNode template = findTemplate(adapter.getParsedConfig(), templateName);
        if (template == null) {
            throw new IllegalArgumentException("Adapter 模板不存在: " + templateName);
        }

        ObjectNode contract = JsonNodeSupport.objectNode();
        ObjectNode config = contract.putObject("config");
        config.put("protocol", "MQTT");
        config.put("adapterName", adapter.getAdapterName());
        config.put("templateName", templateName);

        ArrayNode commands = contract.putArray("commands");
        for (JsonNode command : array(template.get("commands"))) {
            ObjectNode commandNode = commands.addObject();
            commandNode.put("commandName", command.path("name").asText());
            commandNode.put("description", command.path("description").asText(""));
            ArrayNode params = commandNode.putArray("commandParameters");
            for (JsonNode param : array(command.get("parameters"))) {
                ObjectNode paramNode = params.addObject();
                paramNode.put("paramName", param.path("name").asText());
                paramNode.put("dataType", normalizeDataType(param.path("dataType").asText("STRING")));
                if (param.path("hidden").asBoolean(false)) {
                    paramNode.put("hidden", true);
                    paramNode.put("sourceField", param.path("sourceField").asText(""));
                }
                if (param.hasNonNull("description")) {
                    paramNode.put("description", param.path("description").asText());
                }
            }
        }

        ObjectNode telemetry = contract.putObject("telemetry");
        ArrayNode adapterAttributes = telemetry.putArray("adapterAttributes");
        for (JsonNode attr : array(template.get("attributes"))) {
            ObjectNode attrNode = adapterAttributes.addObject();
            attrNode.put("name", attr.path("name").asText());
            attrNode.put("dataType", normalizeDataType(attr.path("dataType").asText("STRING")));
            attrNode.put("description", attr.path("description").asText(""));
        }
        telemetry.putArray("attributesMapping");

        ObjectNode eventGroups = contract.putObject("eventGroups");
        eventGroups.set("cmdEvents", copyArray(template.path("events").get("cmdEvents")));
        eventGroups.set("opEvents", copyArray(template.path("events").get("opEvents")));
        ArrayNode events = contract.putArray("events");
        appendEvents(events, eventGroups.path("cmdEvents"), "CMD");
        appendEvents(events, eventGroups.path("opEvents"), "OP");

        return contract;
    }

    public JsonNode manifestOf(AdapterIndex adapter) {
        return adapter == null ? null : adapter.getParsedConfig();
    }

    public boolean isHiddenCommandParameter(JsonNode commandParameter) {
        return commandParameter != null && commandParameter.path("hidden").asBoolean(false);
    }

    public String normalizeDataType(String value) {
        if (value == null || value.isBlank()) {
            return "STRING";
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "INT", "INTEGER", "LONG" -> "INTEGER";
            case "FLOAT", "DOUBLE", "NUMBER", "NUMERIC" -> "DOUBLE";
            case "BOOL", "BOOLEAN" -> "BOOLEAN";
            default -> "STRING";
        };
    }

    private ObjectNode normalizeTemplate(JsonNode template) {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("templateName", firstText(template, "", "templateName", "name", "deviceType"));
        node.put("description", text(template, "description", ""));

        ArrayNode attrs = node.putArray("attributes");
        for (JsonNode attr : array(template.get("attributes"))) {
            ObjectNode attrNode = attrs.addObject();
            attrNode.put("name", firstText(attr, "", "name", "fieldName", "key"));
            attrNode.put("dataType", normalizeDataType(firstText(attr, "DOUBLE", "dataType", "type")));
            attrNode.put("description", text(attr, "description", ""));
        }

        ArrayNode commands = node.putArray("commands");
        for (JsonNode command : array(template.get("commands"))) {
            ObjectNode commandNode = commands.addObject();
            commandNode.put("name", firstText(command, "", "name", "commandName", "command"));
            commandNode.put("description", text(command, "description", ""));
            ArrayNode params = commandNode.putArray("parameters");
            for (JsonNode param : array(firstNode(command, "parameters", "commandParameters", "params"))) {
                ObjectNode paramNode = params.addObject();
                paramNode.put("name", firstText(param, "", "name", "paramName", "key"));
                paramNode.put("dataType", normalizeDataType(firstText(param, "STRING", "dataType", "type")));
                paramNode.put("description", text(param, "description", ""));
                if (param.path("hidden").asBoolean(false)) {
                    paramNode.put("hidden", true);
                    paramNode.put("sourceField", text(param, "sourceField", ""));
                }
            }
        }

        node.set("events", normalizeEventGroups(template.get("events")));
        return node;
    }

    private ObjectNode normalizeDevicePoint(JsonNode point) {
        ObjectNode node = JsonNodeSupport.objectNode();
        if (point != null && point.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = point.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                node.set(field.getKey(), field.getValue());
            }
        }
        node.put("devicePoint", firstText(point, "", "devicePoint", "name", "point"));
        node.put("templateName", firstText(point, "", "templateName", "deviceTemplate", "type"));
        node.put("description", text(point, "description", ""));
        ObjectNode mapping = JsonNodeSupport.objectNode();
        JsonNode sourceMapping = firstNode(point, "attributeMapping", "attributeBindings", "attributesMapping");
        if (sourceMapping != null && sourceMapping.isObject()) {
            sourceMapping.fields().forEachRemaining(entry -> mapping.put(entry.getKey(), entry.getValue().asText()));
        }
        node.set("attributeMapping", mapping);
        return node;
    }

    private ObjectNode normalizeEventGroups(JsonNode events) {
        ObjectNode groups = JsonNodeSupport.objectNode();
        ArrayNode cmdEvents = groups.putArray("cmdEvents");
        ArrayNode opEvents = groups.putArray("opEvents");
        if (events == null || events.isNull() || events.isMissingNode()) {
            return groups;
        }
        if (events.isObject()) {
            for (JsonNode event : array(firstNode(events, "cmdEvents", "commandLifecycleEvents"))) {
                cmdEvents.add(normalizeEvent(event));
            }
            for (JsonNode event : array(firstNode(events, "opEvents", "businessEvents"))) {
                opEvents.add(normalizeEvent(event));
            }
            return groups;
        }
        for (JsonNode event : array(events)) {
            ObjectNode normalized = normalizeEvent(event);
            if (normalized.path("name").asText("").startsWith("COMMAND_")) {
                cmdEvents.add(normalized);
            } else {
                opEvents.add(normalized);
            }
        }
        return groups;
    }

    private ObjectNode normalizeEvent(JsonNode event) {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("name", firstText(event, "", "name", "eventName"));
        node.put("description", text(event, "description", ""));
        return node;
    }

    private void validateTemplate(JsonNode template) {
        Set<String> attrs = new HashSet<>();
        for (JsonNode attr : array(template.get("attributes"))) {
            String name = attr.path("name").asText("");
            if (name.isBlank()) {
                throw new IllegalArgumentException("template " + template.path("templateName").asText() + " 存在空属性名");
            }
            if (!attrs.add(name)) {
                throw new IllegalArgumentException("template " + template.path("templateName").asText() + " 存在重复属性: " + name);
            }
        }

        Set<String> commands = new HashSet<>();
        for (JsonNode command : array(template.get("commands"))) {
            String commandName = command.path("name").asText("");
            if (commandName.isBlank()) {
                throw new IllegalArgumentException("template " + template.path("templateName").asText() + " 存在空命令名");
            }
            if (!commands.add(commandName)) {
                throw new IllegalArgumentException("template " + template.path("templateName").asText() + " 存在重复命令: " + commandName);
            }
            Set<String> params = new HashSet<>();
            for (JsonNode param : array(command.get("parameters"))) {
                String paramName = param.path("name").asText("");
                if (paramName.isBlank()) {
                    throw new IllegalArgumentException("命令 " + commandName + " 存在空参数名");
                }
                if (!params.add(paramName)) {
                    throw new IllegalArgumentException("命令 " + commandName + " 存在重复参数: " + paramName);
                }
                if (param.path("hidden").asBoolean(false) && param.path("sourceField").asText("").isBlank()) {
                    throw new IllegalArgumentException("命令 " + commandName + " 的 hidden 参数 " + paramName + " 缺少 sourceField");
                }
            }
        }
    }

    private void validatePointAgainstTemplate(JsonNode point, JsonNode template) {
        String devicePoint = point.path("devicePoint").asText();
        Set<String> templateAttrs = new HashSet<>();
        for (JsonNode attr : array(template.get("attributes"))) {
            templateAttrs.add(attr.path("name").asText());
        }
        JsonNode mapping = point.path("attributeMapping");
        for (String templateAttr : templateAttrs) {
            if (!mapping.hasNonNull(templateAttr) || mapping.path(templateAttr).asText("").isBlank()) {
                throw new IllegalArgumentException("devicePoint " + devicePoint + " 缺少属性映射: " + templateAttr);
            }
        }
        Iterator<String> mappingNames = mapping.fieldNames();
        while (mappingNames.hasNext()) {
            String name = mappingNames.next();
            if (!templateAttrs.contains(name)) {
                throw new IllegalArgumentException("devicePoint " + devicePoint + " 声明了模板中不存在的属性映射: " + name);
            }
        }

        for (JsonNode command : array(template.get("commands"))) {
            for (JsonNode param : array(command.get("parameters"))) {
                if (!param.path("hidden").asBoolean(false)) {
                    continue;
                }
                String sourceField = param.path("sourceField").asText("");
                if (!point.hasNonNull(sourceField)) {
                    throw new IllegalArgumentException("devicePoint " + devicePoint + " 缺少 hidden 参数来源字段: " + sourceField);
                }
            }
        }
    }

    private JsonNode readJson(String content) {
        try {
            return JsonNodeSupport.MAPPER.readTree(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Adapter 配置不是有效 JSON: " + e.getOriginalMessage(), e);
        }
    }

    private ObjectNode parseIniConfig(String adapterName, String content) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        String current = "";
        sections.put(current, new LinkedHashMap<>());
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = line.substring(1, line.length() - 1).trim();
                sections.putIfAbsent(current, new LinkedHashMap<>());
                continue;
            }
            int index = line.indexOf('=');
            if (index <= 0) {
                continue;
            }
            sections.get(current).put(line.substring(0, index).trim(), line.substring(index + 1).trim());
        }

        String resolvedAdapterName = Objects.toString(sections.getOrDefault("ADAPTER", Map.of()).get("NAME"), adapterName);
        ObjectNode manifest = JsonNodeSupport.objectNode();
        manifest.put("adapterName", resolvedAdapterName);
        ArrayNode templates = manifest.putArray("deviceTemplates");
        ArrayNode points = manifest.putArray("devicePoints");

        Map<String, ObjectNode> templateByName = new LinkedHashMap<>();
        Map<String, Set<String>> templateAttrNames = new LinkedHashMap<>();
        String deviceList = sections.getOrDefault("DEVICES", Map.of()).getOrDefault("LIST", "");
        for (String device : deviceList.split(",")) {
            String devicePoint = device.trim();
            if (devicePoint.isBlank()) {
                continue;
            }
            String templateName = inferTemplateName(devicePoint);
            ObjectNode template = templateByName.computeIfAbsent(templateName, name -> {
                ObjectNode created = templates.addObject();
                created.put("templateName", name);
                created.put("description", "从 INI 配置解析的 " + name + " 模板");
                created.putArray("attributes");
                created.putArray("commands");
                ObjectNode events = created.putObject("events");
                events.putArray("cmdEvents");
                events.putArray("opEvents");
                templateAttrNames.put(name, new HashSet<>());
                return created;
            });

            ObjectNode point = points.addObject();
            point.put("devicePoint", devicePoint);
            point.put("templateName", templateName);
            point.put("description", devicePoint);
            ObjectNode mapping = point.putObject("attributeMapping");
            Map<String, Integer> semanticCounters = new LinkedHashMap<>();
            Map<String, String> attrs = sections.getOrDefault("DEVICE_" + devicePoint, Map.of());
            attrs.forEach((rawAttr, semanticName) -> {
                String attrName = iniAttributeName(semanticName, semanticCounters);
                mapping.put(attrName, rawAttr);
                Set<String> knownAttrs = templateAttrNames.get(templateName);
                if (knownAttrs.add(attrName)) {
                    ObjectNode attr = ((ArrayNode) template.get("attributes")).addObject();
                    attr.put("name", attrName);
                    attr.put("dataType", "DOUBLE");
                    attr.put("description", semanticName);
                }
            });
        }
        return manifest;
    }

    private String inferTemplateName(String devicePoint) {
        String inferred = devicePoint == null ? "DevicePoint" : devicePoint.replaceAll("\\d+$", "");
        return inferred.isBlank() ? "DevicePoint" : inferred;
    }

    private String iniAttributeName(String semanticName, Map<String, Integer> counters) {
        String base = semanticName == null || semanticName.isBlank() ? "attr" : semanticName.trim();
        int next = counters.getOrDefault(base, 0) + 1;
        counters.put(base, next);
        return next == 1 ? base : base + next;
    }

    private JsonNode firstObject(JsonNode source, String... keys) {
        JsonNode node = firstNode(source, keys);
        return node != null && node.isObject() ? node : null;
    }

    private JsonNode firstNode(JsonNode source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode node = source.get(key);
            if (node != null && !node.isNull() && !node.isMissingNode()) {
                return node;
            }
        }
        return null;
    }

    private Iterable<JsonNode> array(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return JsonNodeSupport.arrayNode();
        }
        if (node.isArray()) {
            return node;
        }
        ArrayNode single = JsonNodeSupport.arrayNode();
        single.add(node);
        return single;
    }

    private ArrayNode copyArray(JsonNode node) {
        ArrayNode copy = JsonNodeSupport.arrayNode();
        for (JsonNode item : array(node)) {
            copy.add(item);
        }
        return copy;
    }

    private void appendEvents(ArrayNode target, JsonNode events, String eventType) {
        for (JsonNode event : array(events)) {
            ObjectNode node = target.addObject();
            node.put("eventName", event.path("name").asText());
            node.put("description", event.path("description").asText(""));
            node.put("eventType", eventType);
        }
    }

    private String text(JsonNode node, String key, String fallback) {
        if (node == null || !node.hasNonNull(key)) {
            return fallback;
        }
        return node.path(key).asText(fallback);
    }

    private String firstText(JsonNode node, String fallback, String... keys) {
        if (node == null) {
            return fallback;
        }
        for (String key : keys) {
            if (node.hasNonNull(key)) {
                return node.path(key).asText(fallback);
            }
        }
        return fallback;
    }
}
