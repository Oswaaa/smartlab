package com.smartlab.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and validates the adapter-side capability manifest.
 * The manifest is adapter-owned: system UI consumes it, while the adapter
 * runtime
 * can use the same structure as its command routing description.
 */
@Service
/**
 * 适配器配置文件描述符（Manifest）读取与格式校验业务服务。
 */
public class AdapterManifestService {

    public static final String SPEC_VERSION = "smartlab.adapter.config.v1";
    private static final Pattern INI_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final Pattern TEMPLATE_SECTION = Pattern.compile("deviceTemplates\\.([A-Za-z][A-Za-z0-9_-]*)");
    private static final Pattern TEMPLATE_COMMAND_SECTION = Pattern
            .compile("deviceTemplates\\.([A-Za-z][A-Za-z0-9_-]*)\\.commands\\.([A-Za-z][A-Za-z0-9_-]*)");
    private static final Pattern POINT_SECTION = Pattern.compile("devicePoints\\.([A-Za-z0-9][A-Za-z0-9_-]*)");

    public ObjectNode parseRawConfig(String adapterName, String rawConfigFormat, String rawConfigContent) {
        return parseRawConfig(adapterName, rawConfigFormat, rawConfigContent, null);
    }

    public ObjectNode parseRawConfig(String adapterName, String rawConfigFormat, String rawConfigContent,
            Long registeredAt) {
        if (rawConfigContent == null || rawConfigContent.isBlank()) {
            throw new IllegalArgumentException("Adapter 配置内容不能为空");
        }
        String format = rawConfigFormat == null ? "JSON" : rawConfigFormat.trim().toUpperCase(Locale.ROOT);
        JsonNode raw;
        if ("JSON".equals(format)) {
            raw = readJson(rawConfigContent);
        } else if ("INI".equals(format)) {
            raw = parseIniConfig(adapterName, rawConfigContent);
        } else {
            throw new IllegalArgumentException("暂不支持的 Adapter 配置格式: " + format);
        }
        ObjectNode normalized = normalize(raw, adapterName, format, registeredAt);
        validate(normalized);
        return normalized;
    }

    public ObjectNode normalize(JsonNode source, String fallbackAdapterName, String rawConfigFormat) {
        return normalize(source, fallbackAdapterName, rawConfigFormat, null);
    }

    public ObjectNode normalize(JsonNode source, String fallbackAdapterName, String rawConfigFormat,
            Long registeredAt) {
        JsonNode root = firstObject(source, "parsedConfig", "parsed_config", "adapterManifest", "manifest",
                "adapterContract");
        if (root == null || root.isMissingNode() || root.isNull()) {
            root = source == null ? JsonNodeSupport.objectNode() : source;
        }

        String configuredAdapterName = firstText(root, "", "adapterName", "name");
        if (fallbackAdapterName != null && !fallbackAdapterName.isBlank() && !configuredAdapterName.isBlank()
                && !fallbackAdapterName.trim().equals(configuredAdapterName)) {
            throw new IllegalArgumentException("注册报文的 adapterName 与配置文件不一致");
        }

        ObjectNode manifest = JsonNodeSupport.objectNode();
        manifest.put("specVersion", text(root, "specVersion", SPEC_VERSION));
        manifest.put("adapterName", firstText(root, fallbackAdapterName, "adapterName", "name"));
        manifest.put("adapterDescription", firstText(root, "", "adapterDescription", "description"));
        manifest.put("rawConfigFormat",
                rawConfigFormat == null ? text(root, "rawConfigFormat", "JSON") : rawConfigFormat);

        ObjectNode registerMeta = manifest.putObject("registerMeta");
        JsonNode sourceMeta = firstObject(root, "registerMeta", "meta");
        registerMeta.put("rawConfigFormat",
                rawConfigFormat == null ? text(root, "rawConfigFormat", "JSON") : rawConfigFormat);
        registerMeta.put("specVersion", sourceMeta == null ? text(root, "specVersion", SPEC_VERSION)
                : text(sourceMeta, "specVersion", text(root, "specVersion", SPEC_VERSION)));
        if (registeredAt != null) {
            registerMeta.put("registeredAt", registeredAt);
        } else if (sourceMeta != null && sourceMeta.hasNonNull("registeredAt")) {
            registerMeta.set("registeredAt", sourceMeta.get("registeredAt"));
        }

        ArrayNode categories = manifest.putArray("deviceCategories");
        if (!root.has("deviceTemplates")) {
            throw new IllegalArgumentException("Adapter 配置文件必须使用新的 flat 格式（包含 deviceTemplates 且可选包含 devicePoints 列表）");
        }

        Set<String> knownTemplateNames = new HashSet<>();
        for (JsonNode templateNode : array(root.get("deviceTemplates"))) {
            String tName = firstText(templateNode, "", "templateName", "name", "deviceType");
            if (!tName.isBlank()) {
                knownTemplateNames.add(tName);
            }
        }

        for (JsonNode pointNode : array(root.get("devicePoints"))) {
            String pointTemplateName = firstText(pointNode, "", "templateName", "deviceTemplate", "type");
            if (pointTemplateName.isBlank()) {
                throw new IllegalArgumentException(
                        "devicePoint " + pointNode.path("devicePoint").asText("") + " 缺少 templateName 字段");
            }
            if (!knownTemplateNames.contains(pointTemplateName)) {
                throw new IllegalArgumentException("devicePoint " + pointNode.path("devicePoint").asText("")
                        + " 引用的 templateName \"" + pointTemplateName + "\" 在 deviceTemplates 中不存在");
            }
        }

        for (JsonNode templateNode : array(root.get("deviceTemplates"))) {
            ObjectNode catNode = JsonNodeSupport.objectNode();
            ObjectNode template = normalizeTemplate(templateNode);

            String templateName = template.path("templateName").asText("");
            String categoryName = firstText(templateNode, templateName, "categoryName", "deviceCategory");
            String categoryDesc = firstText(templateNode, template.path("description").asText(""),
                    "categoryDescription", "description");

            catNode.put("categoryName", categoryName);
            catNode.put("categoryDescription", categoryDesc);
            catNode.set("deviceTemplate", template);

            ArrayNode points = catNode.putArray("devicePoints");
            for (JsonNode pointNode : array(root.get("devicePoints"))) {
                String pointTemplateName = firstText(pointNode, "", "templateName", "deviceTemplate", "type");
                if (templateName.equals(pointTemplateName)) {
                    ObjectNode normalizedPoint = normalizeDevicePoint(pointNode, templateName);
                    normalizedPoint.put("categoryName", categoryName);
                    points.add(normalizedPoint);
                }
            }
            categories.add(catNode);
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
        Set<String> categoryNames = new HashSet<>();
        for (JsonNode category : array(manifest.get("deviceCategories"))) {
            String categoryName = category.path("categoryName").asText("");
            if (categoryName.isBlank()) {
                throw new IllegalArgumentException("deviceCategories 中存在空 categoryName");
            }
            if (!categoryNames.add(categoryName)) {
                throw new IllegalArgumentException("deviceCategories 存在重复 categoryName: " + categoryName);
            }
            JsonNode template = category.path("deviceTemplate");
            String templateName = template.path("templateName").asText("");
            if (templateName.isBlank()) {
                throw new IllegalArgumentException(
                        "deviceCategory " + categoryName + " 缺少 deviceTemplate.templateName");
            }
            if (templates.put(templateName, template) != null) {
                throw new IllegalArgumentException("deviceTemplate 存在重复 templateName: " + templateName);
            }
            validateTemplate(template);
        }
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("Adapter manifest 至少需要一个 deviceCategory/deviceTemplate");
        }

        Set<String> pointNames = new HashSet<>();
        for (JsonNode category : array(manifest.get("deviceCategories"))) {
            JsonNode template = category.path("deviceTemplate");
            String templateName = template.path("templateName").asText("");
            for (JsonNode point : array(category.get("devicePoints"))) {
                String devicePoint = point.path("devicePoint").asText("");
                if (devicePoint.isBlank()) {
                    throw new IllegalArgumentException("devicePoints 中存在空 devicePoint");
                }
                if (!pointNames.add(devicePoint)) {
                    throw new IllegalArgumentException("devicePoints 存在重复 devicePoint: " + devicePoint);
                }
                String pointTemplateName = point.path("templateName").asText(templateName);
                if (!templateName.equals(pointTemplateName)) {
                    throw new IllegalArgumentException(
                            "devicePoint " + devicePoint + " 引用了错误的 templateName: " + pointTemplateName);
                }
                validatePointAgainstTemplate(point, template);
            }
        }
    }

    public JsonNode findTemplate(JsonNode manifest, String templateName) {
        if (manifest == null || templateName == null || templateName.isBlank()) {
            return null;
        }
        for (JsonNode category : array(manifest.get("deviceCategories"))) {
            JsonNode template = category.path("deviceTemplate");
            if (templateName.equals(template.path("templateName").asText())) {
                return template;
            }
        }
        return null;
    }

    public JsonNode findCategory(JsonNode manifest, String categoryName) {
        if (manifest == null || categoryName == null || categoryName.isBlank()) {
            return null;
        }
        for (JsonNode category : array(manifest.get("deviceCategories"))) {
            if (categoryName.equals(category.path("categoryName").asText())) {
                return category;
            }
        }
        return null;
    }

    public JsonNode findDevicePoint(JsonNode manifest, String devicePoint) {
        if (manifest == null || devicePoint == null || devicePoint.isBlank()) {
            return null;
        }
        for (JsonNode category : array(manifest.get("deviceCategories"))) {
            for (JsonNode point : array(category.get("devicePoints"))) {
                if (devicePoint.equals(point.path("devicePoint").asText())) {
                    return point;
                }
            }
        }
        return null;
    }

    public String templateNameForPoint(JsonNode manifest, String devicePoint) {
        JsonNode point = findDevicePoint(manifest, devicePoint);
        return point == null ? null : point.path("templateName").asText(null);
    }

    public ObjectNode buildAdapterContract(AdapterIndex adapter, String categoryName) {
        if (adapter == null || adapter.getParsedConfig() == null) {
            throw new IllegalArgumentException("Adapter 未注册或没有解析后的配置");
        }
        JsonNode category = findCategory(adapter.getParsedConfig(), categoryName);
        if (category == null) {
            throw new IllegalArgumentException("Adapter 类别不存在: " + categoryName);
        }
        JsonNode template = category.path("deviceTemplate");
        if (template.isMissingNode() || template.isNull()) {
            throw new IllegalArgumentException("Adapter 类别缺少唯一设备模板: " + categoryName);
        }

        ObjectNode contract = JsonNodeSupport.objectNode();
        ObjectNode config = contract.putObject("config");
        config.put("protocol", "MQTT");
        config.put("adapterName", adapter.getAdapterName());
        config.put("categoryName", category.path("categoryName").asText());

        ArrayNode commands = contract.putArray("commands");
        for (JsonNode command : array(template.get("commands"))) {
            ObjectNode commandNode = commands.addObject();
            commandNode.put("commandName", command.path("name").asText());
            commandNode.put("description", command.path("description").asText(""));
            ArrayNode params = commandNode.putArray("commandParameters");
            for (JsonNode param : array(command.get("parameters"))) {
                if (isInternalCommandParameter(param)) {
                    continue;
                }
                ObjectNode paramNode = params.addObject();
                paramNode.put("paramName", param.path("name").asText());
                paramNode.put("dataType", normalizeDataType(param.path("dataType").asText("STRING")));
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

        ObjectNode events = contract.putObject("events");
        events.set("cmdEvents", toModelContractEvents(template.path("events").get("cmdEvents")));
        events.set("opEvents", toModelContractEvents(template.path("events").get("opEvents")));

        return contract;
    }

    public JsonNode manifestOf(AdapterIndex adapter) {
        return adapter == null ? null : adapter.getParsedConfig();
    }

    public boolean isInternalCommandParameter(JsonNode commandParameter) {
        return commandParameter != null && (commandParameter.path("internal").asBoolean(false)
                || !commandParameter.path("sourceField").asText("").isBlank());
    }

    public String normalizeDataType(String value) {
        if (value == null || value.isBlank()) {
            return "STRING";
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "INTEGER" -> "INTEGER";
            case "DOUBLE" -> "DOUBLE";
            case "BOOLEAN" -> "BOOLEAN";
            case "STRING" -> "STRING";
            case "JSON" -> "JSON";
            default -> throw new IllegalArgumentException("不支持的数据类型: " + value);
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
                boolean internal = param.path("internal").asBoolean(false);
                paramNode.put("internal", internal);
                if (internal) {
                    paramNode.put("sourceField", text(param, "sourceField", ""));
                }
            }
        }

        node.set("events", normalizeEventGroups(template.get("events")));
        return node;
    }

    private ObjectNode normalizeDevicePoint(JsonNode point, String defaultTemplateName) {
        ObjectNode node = JsonNodeSupport.objectNode();
        if (point != null && point.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = point.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                node.set(field.getKey(), field.getValue());
            }
        }
        node.put("devicePoint", firstText(point, "", "devicePoint", "name", "point"));
        node.put("templateName", firstText(point, defaultTemplateName == null ? "" : defaultTemplateName,
                "templateName", "deviceTemplate", "type"));
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
                throw new IllegalArgumentException(
                        "template " + template.path("templateName").asText() + " 存在重复属性: " + name);
            }
        }

        Set<String> commands = new HashSet<>();
        for (JsonNode command : array(template.get("commands"))) {
            String commandName = command.path("name").asText("");
            if (commandName.isBlank()) {
                throw new IllegalArgumentException("template " + template.path("templateName").asText() + " 存在空命令名");
            }
            if (!commands.add(commandName)) {
                throw new IllegalArgumentException(
                        "template " + template.path("templateName").asText() + " 存在重复命令: " + commandName);
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
                if (param.path("internal").asBoolean(false) && param.path("sourceField").asText("").isBlank()) {
                    throw new IllegalArgumentException(
                            "命令 " + commandName + " 的 internal 参数 " + paramName + " 缺少 sourceField");
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
                if (!param.path("internal").asBoolean(false)) {
                    continue;
                }
                String sourceField = param.path("sourceField").asText("");
                if (!point.hasNonNull(sourceField)) {
                    throw new IllegalArgumentException(
                            "devicePoint " + devicePoint + " 缺少 internal 参数来源字段: " + sourceField);
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
        Map<String, Map<String, String>> sections = readIniSections(content);
        Map<String, String> adapter = sections.get("adapter");
        if (adapter == null) {
            throw new IllegalArgumentException("INI 配置缺少 [adapter] section");
        }
        requireOnlyKeys(adapter, Set.of("specVersion", "adapterName", "adapterDescription", "rawConfigFormat"),
                "[adapter]");
        String configAdapterName = requiredValue(adapter, "adapterName", "[adapter]");
        if (adapterName != null && !adapterName.isBlank() && !adapterName.trim().equals(configAdapterName)) {
            throw new IllegalArgumentException("INI 的 adapterName 必须与注册报文中的 adapterName 一致");
        }
        if (!SPEC_VERSION.equals(requiredValue(adapter, "specVersion", "[adapter]"))) {
            throw new IllegalArgumentException("INI 的 specVersion 必须为 " + SPEC_VERSION);
        }
        if (!"INI".equalsIgnoreCase(requiredValue(adapter, "rawConfigFormat", "[adapter]"))) {
            throw new IllegalArgumentException("INI 的 rawConfigFormat 必须为 INI");
        }

        ObjectNode root = JsonNodeSupport.objectNode();
        root.put("specVersion", SPEC_VERSION);
        root.put("adapterName", configAdapterName);
        root.put("adapterDescription", adapter.getOrDefault("adapterDescription", ""));
        root.put("rawConfigFormat", "INI");
        ArrayNode templates = root.putArray("deviceTemplates");
        ArrayNode points = root.putArray("devicePoints");
        Set<String> consumed = new HashSet<>();
        consumed.add("");
        consumed.add("adapter");

        Map<String, Map<String, String>> templateSections = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : sections.entrySet()) {
            Matcher matcher = TEMPLATE_SECTION.matcher(entry.getKey());
            if (matcher.matches()) {
                templateSections.put(matcher.group(1), entry.getValue());
                consumed.add(entry.getKey());
            }
        }
        if (templateSections.isEmpty()) {
            throw new IllegalArgumentException("INI 配置至少需要一个 [deviceTemplates.<templateName>] section");
        }

        Set<String> categoryNames = new HashSet<>();
        for (Map.Entry<String, Map<String, String>> entry : templateSections.entrySet()) {
            String templateName = entry.getKey();
            Map<String, String> templateSection = entry.getValue();
            requireOnlyKeys(templateSection, Set.of("categoryName", "categoryDescription", "description"),
                    "[deviceTemplates." + templateName + "]");
            String categoryName = requiredValue(templateSection, "categoryName",
                    "[deviceTemplates." + templateName + "]");
            if (!categoryNames.add(categoryName)) {
                throw new IllegalArgumentException("INI 配置存在重复 categoryName: " + categoryName);
            }

            ObjectNode template = templates.addObject();
            template.put("templateName", templateName);
            template.put("categoryName", categoryName);
            template.put("categoryDescription", templateSection.getOrDefault("categoryDescription", ""));
            template.put("description", templateSection.getOrDefault("description", ""));
            ArrayNode attributes = template.putArray("attributes");
            ArrayNode commands = template.putArray("commands");
            ObjectNode events = template.putObject("events");
            ArrayNode cmdEvents = events.putArray("cmdEvents");
            ArrayNode opEvents = events.putArray("opEvents");

            String attributesSectionName = "deviceTemplates." + templateName + ".attributes";
            Map<String, String> attributesSection = sections.get(attributesSectionName);
            if (attributesSection != null) {
                consumed.add(attributesSectionName);
                for (Map.Entry<String, String> attribute : attributesSection.entrySet()) {
                    requireIdentifier(attribute.getKey(), "属性名");
                    IniTypedValue value = parseIniTypedValue(attribute.getValue(), "属性 " + attribute.getKey(), false);
                    ObjectNode attr = attributes.addObject();
                    attr.put("name", attribute.getKey());
                    attr.put("dataType", value.dataType());
                    attr.put("description", value.description());
                }
            }

            Map<String, ObjectNode> commandsByName = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> section : sections.entrySet()) {
                Matcher matcher = TEMPLATE_COMMAND_SECTION.matcher(section.getKey());
                if (!matcher.matches() || !templateName.equals(matcher.group(1))) {
                    continue;
                }
                String commandName = matcher.group(2);
                requireOnlyKeys(section.getValue(), Set.of("description"), "[" + section.getKey() + "]");
                ObjectNode command = commands.addObject();
                command.put("name", commandName);
                command.put("description", section.getValue().getOrDefault("description", ""));
                command.putArray("parameters");
                commandsByName.put(commandName, command);
                consumed.add(section.getKey());
            }
            for (Map.Entry<String, ObjectNode> commandEntry : commandsByName.entrySet()) {
                String parametersSectionName = "deviceTemplates." + templateName + ".commands."
                        + commandEntry.getKey() + ".parameters";
                Map<String, String> parametersSection = sections.get(parametersSectionName);
                if (parametersSection == null) {
                    continue;
                }
                consumed.add(parametersSectionName);
                ArrayNode parameters = (ArrayNode) commandEntry.getValue().get("parameters");
                for (Map.Entry<String, String> parameter : parametersSection.entrySet()) {
                    requireIdentifier(parameter.getKey(), "参数名");
                    IniTypedValue value = parseIniTypedValue(parameter.getValue(), "参数 " + parameter.getKey(), true);
                    ObjectNode param = parameters.addObject();
                    param.put("name", parameter.getKey());
                    param.put("dataType", value.dataType());
                    param.put("description", value.description());
                    param.put("internal", value.internal());
                    if (value.internal()) {
                        param.put("sourceField", value.sourceField());
                    }
                }
            }

            appendIniEvents(sections, templateName, "cmdEvents", cmdEvents, consumed);
            appendIniEvents(sections, templateName, "opEvents", opEvents, consumed);
        }

        for (Map.Entry<String, Map<String, String>> entry : sections.entrySet()) {
            Matcher matcher = POINT_SECTION.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            String pointName = matcher.group(1);
            Map<String, String> pointSection = entry.getValue();
            String templateName = requiredValue(pointSection, "templateName", "[" + entry.getKey() + "]");
            if (!templateSections.containsKey(templateName)) {
                throw new IllegalArgumentException(
                        "devicePoint " + pointName + " 引用的 templateName 不存在: " + templateName);
            }
            ObjectNode point = points.addObject();
            point.put("devicePoint", pointName);
            point.put("templateName", templateName);
            point.put("description", pointSection.getOrDefault("description", ""));
            for (Map.Entry<String, String> field : pointSection.entrySet()) {
                if (Set.of("templateName", "description", "devicePoint").contains(field.getKey())) {
                    continue;
                }
                requireIdentifier(field.getKey(), "设备点字段名");
                putIniScalar(point, field.getKey(), field.getValue());
            }
            String mappingSectionName = "devicePoints." + pointName + ".attributeMapping";
            Map<String, String> mappingSection = sections.get(mappingSectionName);
            ObjectNode mapping = point.putObject("attributeMapping");
            if (mappingSection != null) {
                consumed.add(mappingSectionName);
                for (Map.Entry<String, String> mappingEntry : mappingSection.entrySet()) {
                    requireIdentifier(mappingEntry.getKey(), "属性映射名");
                    if (mappingEntry.getValue().isBlank()) {
                        throw new IllegalArgumentException("设备点 " + pointName + " 的属性映射不能为空: " + mappingEntry.getKey());
                    }
                    mapping.put(mappingEntry.getKey(), mappingEntry.getValue());
                }
            }
            consumed.add(entry.getKey());
        }

        for (String sectionName : sections.keySet()) {
            if (!consumed.contains(sectionName)) {
                throw new IllegalArgumentException("INI 配置存在未知或无效 section: [" + sectionName + "]");
            }
        }
        return root;
    }

    private Map<String, Map<String, String>> readIniSections(String content) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        String current = "";
        sections.put(current, new LinkedHashMap<>());
        int lineNumber = 0;
        for (String rawLine : content.split("\\R")) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = line.substring(1, line.length() - 1).trim();
                if (current.isBlank() || sections.containsKey(current)) {
                    throw new IllegalArgumentException("INI 第 " + lineNumber + " 行存在空或重复 section: " + line);
                }
                sections.put(current, new LinkedHashMap<>());
                continue;
            }
            int index = line.indexOf('=');
            if (index <= 0 || current.isBlank()) {
                throw new IllegalArgumentException("INI 第 " + lineNumber + " 行不是有效的 key=value 配置");
            }
            String key = line.substring(0, index).trim();
            String value = line.substring(index + 1).trim();
            if (key.isBlank() || sections.get(current).putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("INI 第 " + lineNumber + " 行存在空或重复 key: " + key);
            }
        }
        if (!sections.get("").isEmpty()) {
            throw new IllegalArgumentException("INI 配置的 key=value 必须位于 section 内");
        }
        return sections;
    }

    private void appendIniEvents(Map<String, Map<String, String>> sections, String templateName, String eventGroup,
            ArrayNode target, Set<String> consumed) {
        String sectionName = "deviceTemplates." + templateName + ".events." + eventGroup;
        Map<String, String> eventSection = sections.get(sectionName);
        if (eventSection == null) {
            return;
        }
        consumed.add(sectionName);
        for (Map.Entry<String, String> event : eventSection.entrySet()) {
            requireIdentifier(event.getKey(), "事件名");
            ObjectNode eventNode = target.addObject();
            eventNode.put("name", event.getKey());
            eventNode.put("description", event.getValue());
        }
    }

    private IniTypedValue parseIniTypedValue(String source, String context, boolean allowModifiers) {
        List<String> tokens = parseIniBracketTokens(source, context);
        if (tokens.size() < 2 || (!allowModifiers && tokens.size() != 2)) {
            throw new IllegalArgumentException(context + " 的格式应为 [数据类型][描述]" + (allowModifiers ? "[可选修饰符]..." : ""));
        }
        String dataType = normalizeIniDataType(tokens.get(0), context);
        String description = tokens.get(1);
        boolean internal = false;
        String sourceField = null;
        Set<String> modifiers = new HashSet<>();
        for (int i = 2; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int equals = token.indexOf('=');
            if (equals <= 0 || equals == token.length() - 1) {
                throw new IllegalArgumentException(context + " 的修饰符必须是 key=value: [" + token + "]");
            }
            String key = token.substring(0, equals).trim();
            String value = token.substring(equals + 1).trim();
            if (!modifiers.add(key)) {
                throw new IllegalArgumentException(context + " 存在重复修饰符: " + key);
            }
            switch (key) {
                case "internal" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException(context + " 的 internal 必须为 true 或 false");
                    }
                    internal = Boolean.parseBoolean(value);
                }
                case "sourceField" -> {
                    requireIdentifier(value, context + " 的 sourceField");
                    sourceField = value;
                }
                default -> throw new IllegalArgumentException(context + " 包含未知修饰符: " + key);
            }
        }
        if (internal && (sourceField == null || sourceField.isBlank())) {
            throw new IllegalArgumentException(context + " 的 internal=true 必须同时提供 sourceField");
        }
        return new IniTypedValue(dataType, description, internal, sourceField);
    }

    private List<String> parseIniBracketTokens(String source, String context) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
            if (index == source.length()) {
                break;
            }
            if (source.charAt(index++) != '[') {
                throw new IllegalArgumentException(context + " 必须使用 [数据类型][描述] 格式");
            }
            StringBuilder token = new StringBuilder();
            boolean closed = false;
            while (index < source.length()) {
                char current = source.charAt(index++);
                if (current == '\\') {
                    if (index >= source.length()) {
                        throw new IllegalArgumentException(context + " 包含未完成的转义字符");
                    }
                    char escaped = source.charAt(index++);
                    if (escaped != ']' && escaped != '\\') {
                        throw new IllegalArgumentException(context + " 仅支持 \\] 和 \\\\ 转义");
                    }
                    token.append(escaped);
                } else if (current == ']') {
                    closed = true;
                    break;
                } else {
                    token.append(current);
                }
            }
            if (!closed) {
                throw new IllegalArgumentException(context + " 存在未闭合的 []");
            }
            tokens.add(token.toString().trim());
        }
        return tokens;
    }

    private String normalizeIniDataType(String value, String context) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "DOUBLE" -> "DOUBLE";
            case "INTEGER" -> "INTEGER";
            case "BOOLEAN" -> "BOOLEAN";
            case "STRING" -> "STRING";
            default -> throw new IllegalArgumentException(context + " 的数据类型必须为 DOUBLE、INTEGER、BOOLEAN 或 STRING");
        };
    }

    private void putIniScalar(ObjectNode target, String key, String value) {
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            target.put(key, Boolean.parseBoolean(trimmed));
            return;
        }
        if (trimmed.matches("-?(0|[1-9]\\d*)")) {
            try {
                target.put(key, Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                target.put(key, Long.parseLong(trimmed));
            }
            return;
        }
        target.put(key, value);
    }

    private void requireOnlyKeys(Map<String, String> values, Set<String> allowed, String context) {
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(context + " 包含未知字段: " + key);
            }
        }
    }

    private String requiredValue(Map<String, String> values, String key, String context) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(context + " 缺少必填字段: " + key);
        }
        return value.trim();
    }

    private void requireIdentifier(String value, String context) {
        if (value == null || !INI_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(context + " 必须匹配 " + INI_IDENTIFIER.pattern());
        }
    }

    private record IniTypedValue(String dataType, String description, boolean internal, String sourceField) {
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

    private ArrayNode toModelContractEvents(JsonNode node) {
        ArrayNode events = JsonNodeSupport.arrayNode();
        for (JsonNode item : array(node)) {
            ObjectNode event = events.addObject();
            event.put("eventName", item.path("name").asText(item.path("eventName").asText("")));
            if (item.hasNonNull("description")) {
                event.put("description", item.path("description").asText());
            }
        }
        return events;
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
