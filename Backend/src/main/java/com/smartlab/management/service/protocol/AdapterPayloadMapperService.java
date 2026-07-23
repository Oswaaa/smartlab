package com.smartlab.management.service.protocol;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.dto.resource.adapter.AdapterRouteDTO;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.DeviceTwinStatesMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import com.smartlab.management.service.db.resource.data.DataRecordService;
import com.smartlab.management.entity.resource.data.DataIndex;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
/**
 * 适配器物理报文与物模型逻辑属性双向翻译映射核心转换层服务（原DeviceProtocolMapperService）。
 */
public class AdapterPayloadMapperService {
    private static final Logger log = LoggerFactory.getLogger(AdapterPayloadMapperService.class);

    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceModelsMapper deviceModelsMapper;
    private final DeviceTwinStatesMapper twinStatesMapper;
    private final AdapterIndexService adapterIndexService;
    private final AdapterManifestService adapterManifestService;
    private final ProtocolDictionaryService protocolDictionaryService;
    private final StateMachineEngine stateMachineEngine;
    private final DataIndexService dataIndexService;
    private final DataRecordService dataRecordService;
    private final ConcurrentHashMap<String, AdapterRouteDTO> adapterRouteTable = new ConcurrentHashMap<>();

    public AdapterPayloadMapperService(DeviceInstancesMapper deviceInstancesMapper,
            DeviceModelsMapper deviceModelsMapper,
            DeviceTwinStatesMapper twinStatesMapper,
            AdapterIndexService adapterIndexService,
            AdapterManifestService adapterManifestService,
            ProtocolDictionaryService protocolDictionaryService,
            DataIndexService dataIndexService,
            DataRecordService dataRecordService,
            @Lazy StateMachineEngine stateMachineEngine) {
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceModelsMapper = deviceModelsMapper;
        this.twinStatesMapper = twinStatesMapper;
        this.adapterIndexService = adapterIndexService;
        this.adapterManifestService = adapterManifestService;
        this.protocolDictionaryService = protocolDictionaryService;
        this.dataIndexService = dataIndexService;
        this.dataRecordService = dataRecordService;
        this.stateMachineEngine = stateMachineEngine;
    }

    public Map<String, AdapterRouteDTO> refreshAdapterRouteTable() {
        adapterRouteTable.clear();
        List<DeviceInstances> instances = deviceInstancesMapper.selectList(Wrappers.<DeviceInstances>lambdaQuery()
                .eq(DeviceInstances::getLifecycleStatus, DeviceInstanceLifecycle.IN_USE)
                .isNotNull(DeviceInstances::getBoundAdapterName)
                .isNotNull(DeviceInstances::getBoundDevicePoint)
                .orderByAsc(DeviceInstances::getId));
        for (DeviceInstances instance : instances) {
            if (!DeviceInstanceLifecycle.isUsable(instance)) continue;
            adapterRouteTable.put(routeKey(instance.getBoundAdapterName(), instance.getBoundDevicePoint()),
                    toRoute(instance));
        }
        return getAdapterRouteTable();
    }

    public Map<String, AdapterRouteDTO> getAdapterRouteTable() {
        return Collections.unmodifiableMap(new HashMap<>(adapterRouteTable));
    }

    public AdapterRouteDTO resolveAdapterRoute(String adapterName, String devicePoint) {
        if (adapterRouteTable.isEmpty()) {
            refreshAdapterRouteTable();
        }
        return adapterRouteTable.get(routeKey(adapterName, devicePoint));
    }

    public ObjectNode buildCommandMessage(String id, String commandId, Map<String, Object> parameters) {
        return buildCommandMessage(id, commandId, null, parameters);
    }

    public ObjectNode buildCommandMessage(String id, String commandId, String messageId, Map<String, Object> parameters) {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId 不能为空");
        }
        DeviceInstances instance = deviceInstancesMapper.selectById(parseId(id));
        if (instance == null) {
            throw new IllegalArgumentException("设备实例不存在");
        }
        requireUsable(instance);
        if (!hasAdapterBinding(instance)) {
            throw new IllegalStateException("设备实例未绑定 Adapter 设备点");
        }
        DeviceModels model = deviceModelsMapper.selectById(instance.getDeviceModelId());
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        JsonNode command = resolveCommandDefinition(model, commandId);
        String commandName = command.path("commandName").asText(command.path("name").asText(commandId));
        ObjectNode mappedParameters = buildOutgoingParameters(model, commandName, commandId,
                parameters == null ? Map.of() : parameters);

        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("messageId", messageId == null || messageId.isBlank() ? UUID.randomUUID().toString() : messageId);
        payload.put("adapterName", instance.getBoundAdapterName());
        payload.put("devicePoint", instance.getBoundDevicePoint());
        payload.put("commandName", commandName);
        payload.set("parameters", mappedParameters);
        payload.put("timestamp", Instant.now().toEpochMilli());
        protocolDictionaryService.validateDefinition("CommandMessageFormat", payload);

        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("topic", commandTopic(instance.getBoundAdapterName(), instance.getBoundDevicePoint()));
        result.set("payload", payload);
        return result;
    }

    public ObjectNode buildAbortMessage(String id, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("中止指令缺少 messageId");
        }
        DeviceInstances instance = deviceInstancesMapper.selectById(parseId(id));
        if (instance == null) {
            throw new IllegalArgumentException("设备实例不存在");
        }
        requireUsable(instance);
        if (!hasAdapterBinding(instance)) {
            throw new IllegalStateException("设备实例未绑定 Adapter 设备点");
        }

        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("messageId", messageId);
        payload.put("adapterName", instance.getBoundAdapterName());
        payload.put("devicePoint", instance.getBoundDevicePoint());
        payload.put("operation", "ABORT");
        payload.put("timestamp", Instant.now().toEpochMilli());
        protocolDictionaryService.validateDefinition("CommandAbortMessageFormat", payload);

        ObjectNode result = JsonNodeSupport.objectNode();
        result.put("topic", commandTopic(instance.getBoundAdapterName(), instance.getBoundDevicePoint()));
        result.set("payload", payload);
        return result;
    }
    public ObjectNode buildAdapterBinding(Long modelId, String adapterName, String devicePoint) {
        if (modelId == null) {
            throw new IllegalArgumentException("设备实例缺少 deviceModelId");
        }
        AdapterIndex adapter = adapterIndexService.requireAdapter(adapterName);
        JsonNode manifest = adapter.getParsedConfig();
        JsonNode point = adapterManifestService.findDevicePoint(manifest, devicePoint);
        if (point == null) {
            throw new IllegalArgumentException("Adapter 设备点不存在: " + devicePoint);
        }
        String templateName = point.path("templateName").asText();
        JsonNode template = adapterManifestService.findTemplate(manifest, templateName);
        if (template == null) {
            throw new IllegalArgumentException("Adapter 模板不存在: " + templateName);
        }
        DeviceModels model = deviceModelsMapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        JsonNode contract = model.getAdapterContract();

        String pointCategoryName = point.path("categoryName").asText(null);
        String modelCategoryName = contract == null ? null : contract.path("config").path("categoryName").asText(null);
        if (modelCategoryName != null && !modelCategoryName.isBlank()
                && pointCategoryName != null && !pointCategoryName.isBlank()
                && !modelCategoryName.equals(pointCategoryName)) {
            throw new IllegalArgumentException("设备模型绑定的是 Adapter 类别 " + modelCategoryName + "，不能绑定设备点类别 " + pointCategoryName);
        }

        ObjectNode binding = JsonNodeSupport.objectNode();
        binding.put("adapterName", adapterName);
        binding.put("devicePoint", devicePoint);
        binding.put("templateName", templateName);
        if (pointCategoryName != null && !pointCategoryName.isBlank()) {
            binding.put("categoryName", pointCategoryName);
        }        binding.set("attributeMapping", point.path("attributeMapping"));
        ObjectNode topics = binding.putObject("topics");
        topics.put("command", commandTopic(adapterName, devicePoint));
        topics.put("telemetry", telemetryTopic(adapterName, devicePoint));
        topics.put("event", eventTopic(adapterName, devicePoint));

        ObjectNode modelToRaw = binding.putObject("modelToRawAttribute");
        ObjectNode rawToModel = binding.putObject("rawToModelAttribute");
        ArrayNode resolved = binding.putArray("resolvedAttributes");
        Map<String, String> modelAttrTypes = modelAttributeTypes(model.getAttributes());
        Map<String, String> adapterAttrTypes = adapterAttributeTypes(contract);
        for (JsonNode mapping : iterable(
                contract == null ? null : contract.path("telemetry").path("attributesMapping"))) {
            String modelAttr = mapping.path("modelAttributeName").asText("");
            String templateAttr = mapping.path("adapterAttrName").asText("");
            String rawAttr = point.path("attributeMapping").path(templateAttr).asText("");
            if (modelAttr.isBlank() || templateAttr.isBlank() || rawAttr.isBlank()) {
                continue;
            }
            String modelType = modelAttrTypes.get(modelAttr);
            String adapterType = adapterAttrTypes.get(templateAttr);
            if (modelType != null && adapterType != null && !modelType.equals(adapterType)) {
                throw new IllegalArgumentException("实例属性映射类型不一致: " + modelAttr + " -> " + templateAttr);
            }
            modelToRaw.put(modelAttr, rawAttr);
            rawToModel.put(rawAttr, modelAttr);
            ObjectNode row = resolved.addObject();
            row.put("modelAttributeName", modelAttr);
            row.put("templateAttributeName", templateAttr);
            row.put("rawAttributeName", rawAttr);
            row.put("dataType", modelType == null ? Objects.toString(adapterType, "STRING") : modelType);
        }
        return binding;
    }

    public void applyTelemetry(String adapterName, String devicePoint, JsonNode message) {
        AdapterRouteDTO route = resolveAdapterRoute(adapterName, devicePoint);
        if (route == null) {
            return;
        }
        JsonNode data = message == null ? null : message.path("data");
        if (data == null || !data.isObject()) {
            return;
        }
        Map<String, String> rawToModel = route.getRawToModelMap();
        if (rawToModel == null || rawToModel.isEmpty()) {
            return;
        }
        ObjectNode mappedValues = JsonNodeSupport.objectNode();
        data.fields().forEachRemaining(entry -> {
            String modelAttr = rawToModel.get(entry.getKey());
            if (modelAttr == null || modelAttr.isBlank()) return;
            JsonNode definition = findResolvedAttribute(route.getResolvedAttributes(), entry.getKey(), modelAttr);
            if (definition == null) {
                throw new IllegalStateException("Adapter 路由缺少属性类型定义: " + entry.getKey());
            }
            mappedValues.set(modelAttr, validatedValue(
                    entry.getValue(), definition, "telemetry." + entry.getKey()));
        });
        if (mappedValues.isEmpty()) return;
        Long instanceId = route.getDeviceInstanceId();
        DeviceTwinStates state = getOrCreateTwinState(instanceId);
        ObjectNode current = state.getCurrentAttr() != null && state.getCurrentAttr().isObject()
                ? (ObjectNode) state.getCurrentAttr().deepCopy()
                : JsonNodeSupport.objectNode();
        mappedValues.fields().forEachRemaining(entry -> current.set(entry.getKey(), entry.getValue()));
        state.setCurrentAttr(current);
        state.setOnlineStatus("ONLINE");
        state.setLastOnlineTime(OffsetDateTime.now());
        state.setUpdateTime(OffsetDateTime.now());
        twinStatesMapper.updateById(state);

        try {
            Map<String, Object> recordMap = new HashMap<>();
            current.fields().forEachRemaining(e -> {
                JsonNode val = e.getValue();
                if (val.isNumber()) {
                    recordMap.put(e.getKey(), val.numberValue());
                } else if (val.isBoolean()) {
                    recordMap.put(e.getKey(), val.booleanValue());
                } else {
                    recordMap.put(e.getKey(), val.asText());
                }
            });
            List<DataIndex> dataIndexes = dataIndexService.listByDeviceInstance(instanceId);
            if (dataIndexes != null) {
                for (DataIndex index : dataIndexes) {
                    try {
                        dataRecordService.appendRecord(index.getId(), recordMap);
                    } catch (Exception e) {
                        log.warn("遥测数据写入数据集失败，dataIndexId={}", index.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("应用 Adapter 遥测数据失败，adapter={}, devicePoint={}", adapterName, devicePoint, e);
        }
    }

    public void applyAdapterEvent(String adapterName, String devicePoint, JsonNode message) {
        AdapterRouteDTO route = resolveAdapterRoute(adapterName, devicePoint);
        if (route == null || message == null) {
            return;
        }
        String eventName = message.path("eventName").asText("");
        if (eventName.isBlank()) {
            return;
        }
        DeviceModels model = deviceModelsMapper.selectById(route.getDeviceModelId());
        if (isConfiguredEvent(model, "cmdEvents", eventName)
                && message.path("messageId").asText("").isBlank()) {
            throw new IllegalArgumentException("Adapter 指令事件必须携带 messageId: " + eventName);
        }

        stateMachineEngine.dispatchAdapterEvent(route.getDeviceInstanceId(), eventName, message);
    }

    private boolean isConfiguredEvent(DeviceModels model, String groupName, String eventName) {
        if (model == null || model.getAdapterContract() == null || eventName == null) {
            return false;
        }
        JsonNode events = model.getAdapterContract().path("events").path(groupName);
        for (JsonNode event : iterable(events)) {
            String configuredName = event.path("name").asText(event.path("eventName").asText(""));
            if (eventName.equals(configuredName)) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode buildOutgoingParameters(DeviceModels model, String commandName, String commandId,
            Map<String, Object> parameters) {
        JsonNode capability = findCapability(model.getCapabilities(), commandId);
        if (capability != null && !capability.path("adapterCommandName").asText("").equals(commandName)) {
            capability = null;
        }
        JsonNode command = findCommand(model.getAdapterContract(), commandName);
        if (command == null) {
            throw new IllegalArgumentException("Adapter 命令不存在: " + commandName);
        }
        ObjectNode result = JsonNodeSupport.objectNode();
        if (capability != null) {
            for (JsonNode mapping : iterable(capability.path("parameterMapping"))) {
                String commandParamName = mapping.path("commandParamName").asText("");
                JsonNode commandParam = findCommandParam(command, commandParamName);
                if (commandParam == null || commandParam.path("internal").asBoolean(false)) {
                    continue;
                }
                if (mapping.path("isFixedValue").asBoolean(false)) {
                    result.set(commandParamName, validatedValue(
                            mapping.path("fixedValue"), commandParam, "parameters." + commandParamName));
                } else {
                    String capabilityParamName = mapping.path("capabilityParamName").asText("");
                    if (!parameters.containsKey(capabilityParamName)) {
                        throw new IllegalArgumentException("缺少操作参数: " + capabilityParamName);
                    }
                    result.set(commandParamName, validatedValue(
                            JsonNodeSupport.toNode(parameters.get(capabilityParamName)), commandParam,
                            "parameters." + capabilityParamName));
                }
            }
            return result;
        }

        for (JsonNode param : iterable(command.path("commandParameters"))) {
            String paramName = param.path("paramName").asText("");
            if (paramName.isBlank() || param.path("internal").asBoolean(false)) {
                continue;
            }
            if (!parameters.containsKey(paramName)) {
                throw new IllegalArgumentException("缺少命令参数: " + paramName);
            }
            result.set(paramName, validatedValue(
                    JsonNodeSupport.toNode(parameters.get(paramName)), param, "parameters." + paramName));
        }
        return result;
    }

    private JsonNode findResolvedAttribute(JsonNode resolvedAttributes, String rawName, String modelName) {
        for (JsonNode definition : iterable(resolvedAttributes)) {
            if (rawName.equals(definition.path("rawAttributeName").asText(""))
                    && modelName.equals(definition.path("modelAttributeName").asText(""))) {
                return definition;
            }
        }
        return null;
    }

    private JsonNode validatedValue(JsonNode value, JsonNode definition, String path) {
        String dataType = adapterManifestService.normalizeDataType(
                definition.path("dataType").asText("STRING"));
        boolean valid = value != null && !value.isNull() && switch (dataType) {
            case "INTEGER" -> value.isIntegralNumber();
            case "DOUBLE" -> value.isNumber();
            case "BOOLEAN" -> value.isBoolean();
            case "STRING" -> value.isTextual();
            case "JSON" -> value.isObject() || value.isArray();
            default -> false;
        };
        if (!valid) {
            String actual = value == null || value.isNull() ? "NULL" : value.getNodeType().name();
            throw new IllegalArgumentException(path + " 类型不匹配，要求 " + dataType + "，实际 " + actual);
        }
        return value.deepCopy();
    }

    private JsonNode resolveCommandDefinition(DeviceModels model, String commandId) {
        JsonNode capability = findCapability(model.getCapabilities(), commandId);
        String commandName = capability == null ? commandId : capability.path("adapterCommandName").asText(commandId);
        JsonNode command = findCommand(model.getAdapterContract(), commandName);
        if (command == null) {
            throw new IllegalArgumentException("Adapter 命令不存在: " + commandName);
        }
        return command;
    }

    private JsonNode findCapability(JsonNode capabilities, String id) {
        if (id == null) {
            return null;
        }
        for (JsonNode capability : iterable(capabilities)) {
            if (id.equals(capability.path("name").asText()) || id.equals(capability.path("capabilityId").asText())) {
                return capability;
            }
        }
        return null;
    }

    private JsonNode findCommand(JsonNode adapterContract, String commandName) {
        if (adapterContract == null || commandName == null) {
            return null;
        }
        for (JsonNode command : iterable(adapterContract.path("commands"))) {
            String current = command.path("commandName").asText(command.path("name").asText(""));
            if (commandName.equals(current)) {
                return command;
            }
        }
        return null;
    }

    private JsonNode findCommandParam(JsonNode command, String paramName) {
        for (JsonNode param : iterable(command.path("commandParameters"))) {
            if (paramName.equals(param.path("paramName").asText(param.path("name").asText("")))) {
                return param;
            }
        }
        return null;
    }

    private Map<String, String> modelAttributeTypes(JsonNode attributes) {
        Map<String, String> result = new HashMap<>();
        for (JsonNode attr : iterable(attributes)) {
            String name = attr.path("name").asText("");
            if (!name.isBlank()) {
                result.put(name, adapterManifestService.normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }
        return result;
    }

    private Map<String, String> adapterAttributeTypes(JsonNode contract) {
        Map<String, String> result = new HashMap<>();
        for (JsonNode attr : iterable(contract == null ? null : contract.path("telemetry").path("adapterAttributes"))) {
            String name = attr.path("name").asText("");
            if (!name.isBlank()) {
                result.put(name, adapterManifestService.normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }
        return result;
    }

    private DeviceTwinStates getOrCreateTwinState(Long instanceId) {
        DeviceTwinStates state = twinStatesMapper
                .selectOne(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId));
        if (state != null) {
            return state;
        }
        DeviceTwinStates created = new DeviceTwinStates();
        created.setInstanceId(instanceId);
        created.setCurrentOpState(JsonNodeSupport.objectNode());
        created.setCurrentCmdState("IDLE");
        created.setOnlineStatus("UNKNOWN");
        created.setCurrentAttr(JsonNodeSupport.objectNode());
        created.setUpdateTime(OffsetDateTime.now());
        twinStatesMapper.insert(created);
        return twinStatesMapper
                .selectOne(Wrappers.<DeviceTwinStates>lambdaQuery().eq(DeviceTwinStates::getInstanceId, instanceId));
    }

    private AdapterRouteDTO toRoute(DeviceInstances instance) {
        AdapterRouteDTO route = new AdapterRouteDTO();
        route.setDeviceInstanceId(instance.getId());
        route.setDeviceModelId(instance.getDeviceModelId());
        route.setInstanceName(instance.getInstanceName());
        route.setBoundAdapterName(instance.getBoundAdapterName());
        route.setBoundDevicePoint(instance.getBoundDevicePoint());
        JsonNode config = instance.getInstanceConfig();
        if (config != null && config.has("adapterBinding")) {
            JsonNode binding = config.path("adapterBinding");
            route.setTemplateName(binding.path("templateName").asText(null));
            route.setCategoryName(binding.path("categoryName").asText(null));
            route.setResolvedAttributes(binding.path("resolvedAttributes"));

            JsonNode rawToModel = binding.path("rawToModelAttribute");
            if (rawToModel != null && rawToModel.isObject()) {
                Map<String, String> map = new HashMap<>();
                rawToModel.fields().forEachRemaining(entry -> {
                    map.put(entry.getKey(), entry.getValue().asText());
                });
                route.setRawToModelMap(map);
            }
        }
        return route;
    }

    private void requireUsable(DeviceInstances instance) {
        if (!DeviceInstanceLifecycle.isUsable(instance)) {
            throw new IllegalStateException("设备实例已注销，不能下发 Adapter 指令");
        }
    }

    private boolean hasAdapterBinding(DeviceInstances instance) {
        return instance != null
                && instance.getBoundAdapterName() != null && !instance.getBoundAdapterName().isBlank()
                && instance.getBoundDevicePoint() != null && !instance.getBoundDevicePoint().isBlank();
    }

    private Long parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return Long.valueOf(id);
    }

    private String routeKey(String adapterName, String devicePoint) {
        if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) {
            throw new IllegalArgumentException("Adapter 标识和设备点字段不能为空");
        }
        return adapterName.trim() + "::" + devicePoint.trim();
    }

    private String commandTopic(String adapterName, String devicePoint) {
        return protocolDictionaryService.resolveMqttTopic("commandTopic", Map.of(
                "adapterName", adapterName,
                "devicePoint", devicePoint));
    }

    private String telemetryTopic(String adapterName, String devicePoint) {
        return protocolDictionaryService.resolveMqttTopic("telemetryTopic", Map.of(
                "adapterName", adapterName,
                "devicePoint", devicePoint));
    }

    private String eventTopic(String adapterName, String devicePoint) {
        return protocolDictionaryService.resolveMqttTopic("eventTopic", Map.of(
                "adapterName", adapterName,
                "devicePoint", devicePoint));
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isArray()) {
            return JsonNodeSupport.arrayNode();
        }
        return node;
    }
}
