package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.SystemExecutionContract;
import com.smartlab.global.event.DeviceModelSavedEvent;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.dto.resource.device.DeviceModelSaveDTO;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.mapper.constraint.ConstraintRuleMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.data.DataTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 设备模型表服务。
 * 对应 DEVICE_MODELS 表，只负责模型基础资料和 JSON 字段的保存读取。
 */
@Service
/**
 * 设备物模型与状态机持久层核心服务。处理属性定义生成、标准指令动作生成及 states 保留拷贝。
 */
public class DeviceModelService extends ManagementCrudService<DeviceModels> {

    private final DeviceModelsMapper mapper;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceCategoryService deviceCategoryService;
    private final AdapterManifestService adapterManifestService;
    private final ProtocolDictionaryService protocolDictionaryService;
    private final DataTemplateService dataTemplateService;
    private final AdapterIndexService adapterIndexService;
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    FlowNodeMapper flowNodeMapper;

    @Autowired(required = false)
    ConstraintRuleMapper constraintRuleMapper;

    public DeviceModelService(DeviceModelsMapper mapper,
            DeviceInstancesMapper deviceInstancesMapper,
            DeviceCategoryService deviceCategoryService,
            AdapterManifestService adapterManifestService,
            ProtocolDictionaryService protocolDictionaryService,
            DataTemplateService dataTemplateService,
            AdapterIndexService adapterIndexService) {
        super(mapper);
        this.mapper = mapper;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceCategoryService = deviceCategoryService;
        this.adapterManifestService = adapterManifestService;
        this.protocolDictionaryService = protocolDictionaryService;
        this.dataTemplateService = dataTemplateService;
        this.adapterIndexService = adapterIndexService;
    }

    @Autowired
    void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<DeviceModels> list() {
        return mapper.selectList(Wrappers.<DeviceModels>lambdaQuery().orderByDesc(DeviceModels::getId));
    }

    @Override
    public DeviceModels save(DeviceModels entity) {
        throw new UnsupportedOperationException("设备模型必须通过savePayload完成完整校验");
    }

    public PageResult<DeviceModels> page(long pageNo, long pageSize, String keyword) {
        return page(pageNo, pageSize, keyword, (Long) null);
    }

    public PageResult<DeviceModels> page(long pageNo, long pageSize, String keyword, Long categoryId) {
        if (categoryId == null) {
            return super.page(pageNo, pageSize, keyword, "model_name");
        }
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeviceModels> wrapper = com.baomidou.mybatisplus.core.toolkit.Wrappers
                .lambdaQuery();
        wrapper.eq(DeviceModels::getCategoryId, categoryId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(DeviceModels::getModelName, keyword.trim());
        }
        wrapper.orderByDesc(DeviceModels::getId);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeviceModels> page = mapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public DeviceModels getById(String id) {
        return normalizeLegacyAbortFields(mapper.selectById(parseId(id)));
    }

    public DeviceModels requireRuntimeReady(Long modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("deviceModelId不能为空");
        }
        DeviceModels model = normalizeLegacyAbortFields(mapper.selectById(modelId));
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在: " + modelId);
        }
        validateForPersistence(model);
        return model;
    }

    public DeviceModels requireRuntimeReadyForUpdate(Long modelId) {
        DeviceModels model = normalizeLegacyAbortFields(lockExistingModel(modelId));
        validateForPersistence(model);
        return model;
    }

    private DeviceModels normalizeLegacyAbortFields(DeviceModels model) {
        if (model == null || model.getCapabilities() == null || !model.getCapabilities().isArray()) {
            return model;
        }
        ArrayNode capabilities = (ArrayNode) model.getCapabilities().deepCopy();
        List<ObjectNode> abortCapabilitiesWithoutScope = new ArrayList<>();
        for (JsonNode item : capabilities) {
            if (!(item instanceof ObjectNode capability)) {
                continue;
            }
            if (!capability.has("isAbort")) {
                capability.put("isAbort", false);
            }
            if (!capability.has("abortCapabilityName")) {
                capability.putNull("abortCapabilityName");
            }
            if (capability.path("isAbort").asBoolean(false) && !capability.has("scope")) {
                abortCapabilitiesWithoutScope.add(capability);
            }
        }
        if (abortCapabilitiesWithoutScope.size() == 1) {
            ObjectNode legacyAbort = abortCapabilitiesWithoutScope.getFirst();
            String abortCapabilityName = legacyAbort.path("capabilityName").asText("");
            ArrayNode scope = legacyAbort.putArray("scope");
            for (JsonNode item : capabilities) {
                if (!(item instanceof ObjectNode capability)
                        || capability.path("isAbort").asBoolean(false)) {
                    continue;
                }
                String capabilityName = capability.path("capabilityName").asText("");
                if (!capabilityName.isBlank()) {
                    scope.add(capabilityName);
                    if (!abortCapabilityName.isBlank()
                            && capability.path("abortCapabilityName").isNull()) {
                        capability.put("abortCapabilityName", abortCapabilityName);
                    }
                }
            }
        }
        model.setCapabilities(capabilities);
        return model;
    }

    private DeviceModels lockExistingModel(Long modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("deviceModelId不能为空");
        }
        DeviceModels model = mapper.selectByIdForUpdate(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在: " + modelId);
        }
        return model;
    }

    /**
     * 查询所有设备模型中的状态机配置。
     */
    public List<ObjectNode> listStateMachines() {
        return list().stream().map(this::toStateMachineView).toList();
    }


    /**
     * 查询设备模型内置约束规则。
     */
    public List<Map<String, Object>> listModelConstraintRules() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DeviceModels model : list()) {
            JsonNode constraints = model.getIntrinsicConstraint();
            if (constraints == null || !constraints.isArray()) {
                continue;
            }
            for (JsonNode rule : constraints) {
                result.add(Map.of(
                        "scope", "MODEL",
                        "targetId", String.valueOf(model.getId()),
                        "targetName", Objects.toString(model.getModelName(), ""),
                        "ruleId", Objects.toString(rule.path("constraintRuleId").asText(), ""),
                        "property", Objects.toString(rule.path("targetAttr").asText(), ""),
                        "operator", Objects.toString(rule.path("operator").asText(), ""),
                        "threshold", Objects.toString(rule.path("threshold").asText(), ""),
                        "action", Objects.toString(rule.path("violationStateRef").asText("WARN"), "WARN"),
                        "description", Objects.toString(rule.path("permisDesc").asText(), ""),
                        "active", true));
            }
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceModels savePayload(DeviceModelSaveDTO payload) {
        if (payload == null) {
            throw new IllegalArgumentException("设备模型保存请求不能为空");
        }
        DeviceModels model = new DeviceModels();
        if (payload.getModelId() != null) {
            DeviceModels existing = lockExistingModel(payload.getModelId());
            model.setId(payload.getModelId());
            model.setCreateTime(existing.getCreateTime());
            Long count = deviceInstancesMapper.selectCount(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<DeviceInstances>lambdaQuery()
                            .eq(DeviceInstances::getDeviceModelId, payload.getModelId()));
            if (count != null && count > 0) {
                throw new IllegalStateException("该物模型已实例化为 " + count + " 台设备，已被锁定无法编辑");
            }
        }
        model.setModelName(payload.getModelName());

        if (payload.getCategoryId() == null) {
            throw new IllegalArgumentException("请选择设备类别");
        }
        model.setCategoryId(payload.getCategoryId());
        deviceCategoryService.requireLeafCategory(model.getCategoryId());
        model.setAttributes(payload.getAttributes());
        model.setCapabilities(payload.getCapabilities());
        model.setAdapterContract(canonicalAdapterContract(payload.getAdapterContract()));
        model.setPorts(payload.getPorts());
        model.setIntrinsicConstraint(payload.getIntrinsicConstraints());
        model.setStateMachineInterfaces(payload.getStateMachineInterfaces());
        model.setOpState(payload.getOpState());
        model.setCmdState(payload.getCmdState());
        model.setStateTransitions(payload.getStateTransitions());
        model.setComponentsBom(payload.getComponentsBom());

        OffsetDateTime now = OffsetDateTime.now();
        if (model.getId() == null) {
            model.setCreateTime(now);
        }
        model.setUpdateTime(now);

        enrichStateMachine(model, payload.getOpState(), payload.getStateTransitions());
        validateForPersistence(model);

        if (model.getId() == null) {
            mapper.insert(model);
        } else {
            mapper.updateById(model);
        }
        saveDefaultDataTemplate(model.getId(), model.getModelName(), payload.getDefaultDataTemplate());
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DeviceModelSavedEvent(model.getId()));
        }
        return model;
    }

    private void saveDefaultDataTemplate(Long modelId, String modelName, JsonNode templateNode) {
        if (modelId == null || templateNode == null || templateNode.isNull() || templateNode.isMissingNode()) {
            throw new IllegalArgumentException("设备模型必须配置默认数据模板");
        }
        if (templateNode.has("enabled") && !templateNode.path("enabled").asBoolean(true)) {
            throw new IllegalArgumentException("默认数据模板不能禁用");
        }
        JsonNode mainNode = templateNode.has("main") ? templateNode.path("main") : templateNode;
        JsonNode detailsNode = templateNode.has("details") ? templateNode.path("details")
                : templateNode.path("columns");
        if (detailsNode == null || !detailsNode.isArray() || detailsNode.size() == 0) {
            throw new IllegalArgumentException("默认数据模板至少需要一个字段");
        }

        DataTemplateMain existing = dataTemplateService.findDefaultTemplateByModelId(modelId);
        DataTemplateMain main = new DataTemplateMain();
        if (existing != null) {
            main.setId(existing.getId());
            main.setCreateTime(existing.getCreateTime());
        }
        main.setTemplateName(textValue(mainNode, "templateName", modelName + " 默认数据模板"));
        main.setTemplateDesc(textValue(mainNode, "templateDesc", "系统根据设备模型自动生成的默认数据模板"));
        main.setDeviceModelId(modelId);
        main.setIsDefault(true);

        DataTemplateSaveDTO dto = new DataTemplateSaveDTO();
        dto.setMain(main);
        List<DataTemplateDetail> details = new ArrayList<>();
        for (JsonNode row : detailsNode) {
            String columnName = textValue(row, "columnName", "");
            if (columnName.isBlank()) {
                continue;
            }
            DataTemplateDetail detail = new DataTemplateDetail();
            detail.setColumnName(columnName);
            detail.setColumnDesc(textValue(row, "columnDesc", columnName));
            detail.setDeviceAttrKey(nullableTextValue(row, "deviceAttrKey"));
            detail.setDefaultValue(nullableTextValue(row, "defaultValue"));
            detail.setPropertyTypeId(longValue(row, "propertyTypeId"));
            Integer columnLength = integerValue(row, "columnLength");
            detail.setColumnLength(columnLength == null || columnLength <= 0 ? 255 : columnLength);
            details.add(detail);
        }
        if (details.isEmpty()) {
            throw new IllegalArgumentException("默认数据模板至少需要一个有效字段");
        }
        dto.setDetails(details);
        dataTemplateService.saveTemplate(dto);
    }

    private String textValue(JsonNode node, String field, String fallback) {
        String value = nullableTextValue(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String nullableTextValue(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private Long longValue(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()
                || node.get(field).asText().isBlank()) {
            return null;
        }
        return node.get(field).asLong();
    }

    private Integer integerValue(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()
                || node.get(field).asText().isBlank()) {
            return null;
        }
        return node.get(field).asInt();
    }

    public ObjectNode previewModel(DeviceModelSaveDTO payload) {
        if (payload == null) {
            throw new IllegalArgumentException("设备模型预览请求不能为空");
        }
        DeviceModels model = new DeviceModels();
        model.setId(payload.getModelId());
        model.setModelName(payload.getModelName());
        model.setCategoryId(payload.getCategoryId());
        model.setAttributes(payload.getAttributes());
        model.setCapabilities(payload.getCapabilities());
        model.setAdapterContract(canonicalAdapterContract(payload.getAdapterContract()));
        model.setPorts(payload.getPorts());
        model.setIntrinsicConstraint(payload.getIntrinsicConstraints());
        model.setStateMachineInterfaces(payload.getStateMachineInterfaces());
        model.setOpState(payload.getOpState());
        model.setCmdState(payload.getCmdState());
        model.setStateTransitions(payload.getStateTransitions());
        model.setComponentsBom(payload.getComponentsBom());

        enrichStateMachine(model, payload.getOpState(), payload.getStateTransitions());
        validateForPersistence(model);

        return toModelBundle(model);
    }

    public ObjectNode modelBundle(String id) {
        return toModelBundle(requireRuntimeReady(parseId(id)));
    }

    private ObjectNode toModelBundle(DeviceModels model) {
        ObjectNode result = JsonNodeSupport.objectNode();
        result.set("capabilityModel", toCapabilityModel(model));
        result.set("stateMachineModel", toStateMachineModel(model));
        return result;
    }

    private ObjectNode toCapabilityModel(DeviceModels model) {
        ObjectNode capabilityModel = JsonNodeSupport.objectNode();
        ObjectNode metadata = JsonNodeSupport.objectNode();
        if (model.getId() != null) {
            metadata.put("deviceModelId", model.getId());
        } else {
            metadata.putNull("deviceModelId");
        }
        metadata.put("deviceModelName", Objects.toString(model.getModelName(), ""));
        if (model.getCategoryId() != null) {
            metadata.put("deviceCategoryId", model.getCategoryId());
        } else {
            metadata.putNull("deviceCategoryId");
        }
        capabilityModel.set("metadata", metadata);
        capabilityModel.set("attributes", nullToArray(model.getAttributes()));
        capabilityModel.set("capabilities", nullToArray(model.getCapabilities()));
        capabilityModel.set("adapterContract", nullToObject(model.getAdapterContract()));
        capabilityModel.set("ports", nullToArray(model.getPorts()));
        capabilityModel.set("intrinsicConstraints", nullToArray(model.getIntrinsicConstraint()));
        return capabilityModel;
    }

    private ObjectNode toStateMachineModel(DeviceModels model) {
        ObjectNode stateMachineModel = JsonNodeSupport.objectNode();
        ObjectNode metadata = stateMachineModel.putObject("metadata");
        if (model.getId() == null) {
            metadata.putNull("deviceModelId");
        } else {
            metadata.put("deviceModelId", model.getId());
        }
        stateMachineModel.set("interfaces", nullToArray(model.getStateMachineInterfaces()));
        stateMachineModel.set("opStateSpace", nullToObject(model.getOpState()));
        stateMachineModel.set("cmdLifecycleSpace", nullToObject(model.getCmdState()));
        stateMachineModel.set("transitions", nullToArray(model.getStateTransitions()));
        return stateMachineModel;
    }

    private void enrichStateMachine(DeviceModels model, JsonNode opStateNode, JsonNode transitionNode) {
        model.setStateMachineInterfaces(generateStandardInterfaces(extractAdapterEvents(model.getAdapterContract())));
        model.setCmdState(generateStandardCmdLifecycleSpace());
        model.setOpState(parseOpStateSpace(opStateNode != null ? opStateNode : model.getOpState()));

        ArrayNode transitions = JsonNodeSupport.arrayNode();
        mergeCustomTransitions(transitions, transitionNode != null ? transitionNode : model.getStateTransitions());
        model.setStateTransitions(transitions);
    }

    private List<String> extractAdapterEvents(JsonNode adapterContract) {
        List<String> eventNames = new ArrayList<>();
        if (adapterContract == null || adapterContract.isNull() || !adapterContract.has("events")) {
            return eventNames;
        }
        JsonNode eventsNode = adapterContract.get("events");
        if (eventsNode.isArray()) {
            for (JsonNode event : eventsNode) {
                String name = event.has("eventName") ? event.get("eventName").asText()
                        : (event.has("name") ? event.get("name").asText() : "");
                if (!name.isBlank()) {
                    eventNames.add(name);
                }
            }
        } else if (eventsNode.isObject()) {
            if (eventsNode.has("cmdEvents") && eventsNode.get("cmdEvents").isArray()) {
                for (JsonNode event : eventsNode.get("cmdEvents")) {
                    String name = event.has("eventName") ? event.get("eventName").asText()
                            : (event.has("name") ? event.get("name").asText() : "");
                    if (!name.isBlank()) {
                        eventNames.add(name);
                    }
                }
            }
            if (eventsNode.has("opEvents") && eventsNode.get("opEvents").isArray()) {
                for (JsonNode event : eventsNode.get("opEvents")) {
                    String name = event.has("eventName") ? event.get("eventName").asText()
                            : (event.has("name") ? event.get("name").asText() : "");
                    if (!name.isBlank()) {
                        eventNames.add(name);
                    }
                }
            }
        }
        return eventNames;
    }

    private ArrayNode generateStandardInterfaces(List<String> adapterEvents) {
        ArrayNode interfaces = JsonNodeSupport.arrayNode();
        interfaces.add(
                createInterface("Interface_workflow_in", "IN", "WORKFLOW", protocolArray("WorkflowControlSignal")));
        interfaces.add(createInterface("Interface_control_in", "IN", "CONTROL", protocolArray("ManualControlSignal")));
        interfaces.add(createInterface("Interface_constraint_in", "IN", "CONSTRAINT",
                protocolArray("ConstraintControlSignal")));
        ArrayNode adapterInputSignals = JsonNodeSupport.arrayNode();
        adapterEvents.forEach(adapterInputSignals::add);
        interfaces.add(createInterface("Interface_adapter_in", "IN", "ADAPTER", adapterInputSignals));
        interfaces.add(
                createInterface("Interface_adapter_out", "OUT", "ADAPTER", protocolArray("AdapterOutboundSignal")));
        interfaces.add(createInterface("Interface_state_out", "OUT", "STATE", protocolArray("StatusSignal")));
        return interfaces;
    }

    private ObjectNode createInterface(String name, String direction, String interfaceType, ArrayNode allowedSignals) {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("name", name);
        node.put("direction", direction);
        node.put("interfaceType", interfaceType);
        node.set("allowedSignals", allowedSignals);
        return node;
    }

    private ObjectNode generateStandardCmdLifecycleSpace() {
        ObjectNode cmdSpace = JsonNodeSupport.objectNode();
        cmdSpace.put("initialStateName", "IDLE");
        ArrayNode states = cmdSpace.putArray("states");
        for (String stateName : SystemExecutionContract.commandStateNames()) {
            ObjectNode state = states.addObject();
            state.put("stateName", stateName);
            state.set("onEntry", createStatusOnEntryActions("CMD_STATE"));
        }
        return cmdSpace;
    }

    private ArrayNode createStatusOnEntryActions(String signalName) {
        ArrayNode actions = JsonNodeSupport.arrayNode();
        ObjectNode action = actions.addObject();
        action.put("actionName", "SEND");
        action.putObject("payload")
                .put("interfaceName", "Interface_state_out")
                .put("signalName", protocolSignal(signalName));
        return actions;
    }

    private ArrayNode protocolArray(String definitionName) {
        ArrayNode array = JsonNodeSupport.arrayNode();
        for (String value : protocolDictionaryService.enumValues(definitionName)) {
            array.add(value);
        }
        return array;
    }

    private String protocolSignal(String signalName) {
        if (signalName == null) {
            return null;
        }
        if (!standardSystemSignals().contains(signalName)) {
            throw new IllegalArgumentException("Protocol signal 未定义: " + signalName);
        }
        return signalName;
    }

    private Set<String> standardSystemSignals() {
        return Set.of(
                "WF_EXECUTE_START", "WF_EXECUTE_ABORT", "MANUAL_EXECUTE_START", "MANUAL_EXECUTE_ABORT", "MANUAL_EXECUTE_RESET",
                "CONSTRAINT_EXECUTE", "CONSTRAINT_ABORT", "CMD_START", "CMD_ABORT", "CMD_STATE", "OP_STATE");
    }

    private ObjectNode parseOpStateSpace(JsonNode node) {
        ObjectNode opSpace = JsonNodeSupport.objectNode();
        ArrayNode regions = opSpace.putArray("regions");
        if (node != null && node.has("regions") && node.path("regions").isArray()) {
            for (JsonNode regionNode : node.path("regions")) {
                if (regionNode.isObject()) {
                    ObjectNode region = regions.addObject();
                    region.put("regionName", regionNode.path("regionName").asText(""));
                    region.put("regionType", regionNode.path("regionType").asText(""));
                    region.put("initialStateName", regionNode.path("initialStateName").asText(""));

                    ArrayNode states = region.putArray("states");
                    JsonNode inputStates = regionNode.get("states");
                    if (inputStates != null && inputStates.isArray()) {
                        for (JsonNode s : inputStates) {
                            if (s.isObject() && s.has("stateName")) {
                                ObjectNode stateObj = states.addObject();
                                stateObj.put("stateName", s.get("stateName").asText());
                                JsonNode onEntry = s.get("onEntry");
                                stateObj.set("onEntry", onEntry != null && onEntry.isArray() ? onEntry.deepCopy()
                                        : JsonNodeSupport.arrayNode());
                            }
                        }
                    }
                }
            }
        }
        return opSpace;
    }

    private void mergeCustomTransitions(ArrayNode targetTransitions, JsonNode sourceTransitions) {
        if (sourceTransitions == null || sourceTransitions.isNull() || !sourceTransitions.isArray()) {
            return;
        }
        for (JsonNode source : sourceTransitions) {
            if (!source.isObject()) {
                throw new IllegalArgumentException("状态转移必须是对象");
            }
            String stateSpace = source.path("stateSpace").asText("");
            String fromState = source.path("fromStateName").asText("");
            String toState = source.path("toStateName").asText("");
            JsonNode trigger = source.path("trigger");
            if (!Set.of("CMD", "OP").contains(stateSpace) || fromState.isBlank() || toState.isBlank()
                    || !trigger.isObject()) {
                throw new IllegalArgumentException("设备状态转移必须包含stateSpace、fromStateName、toStateName和trigger");
            }
            String interfaceName = trigger.path("interfaceName").asText("");
            String signalName = trigger.path("signalName").asText("");
            if (signalName.isBlank()) {
                throw new IllegalArgumentException("状态转移trigger.signalName不能为空");
            }

            // 1. 系统内置规则分流与合法性比对
            if (!"Interface_adapter_in".equals(interfaceName)) {
                boolean isSystem = SystemExecutionContract.findStateMachineSystemTransition(
                        stateSpace, fromState, interfaceName, signalName)
                        .filter(expected -> expected.toStateName().equals(toState))
                        .isPresent();
                if (isSystem) {
                    // 合法系统内置规则：自动分流并忽略，不存入数据库（由状态机引擎内核硬编码统一保障）
                    continue;
                }
                throw new IllegalArgumentException("非法的系统内置转移规则或已被篡改: " + fromState + " ➔ " + toState + " (" + interfaceName + " / " + signalName + ")");
            }

            // 2. 自定义规则入库（仅允许由 Interface_adapter_in 触发）
            ObjectNode transition = targetTransitions.addObject();
            if (source.has("description")) {
                transition.put("description", source.path("description").asText(""));
            }
            transition.put("stateSpace", stateSpace);
            if ("OP".equals(stateSpace)) {
                String regionName = source.path("regionName").asText("");
                if (regionName.isBlank()) {
                    throw new IllegalArgumentException("OP状态转移缺少regionName");
                }
                transition.put("regionName", regionName);
            }
            transition.put("fromStateName", fromState);
            transition.put("toStateName", toState);
            transition.putObject("trigger").put("interfaceName", interfaceName).put("signalName", signalName);
            transition.set("actions",
                    source.path("actions").isArray() ? source.path("actions").deepCopy() : JsonNodeSupport.arrayNode());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Serializable id) {
        Long modelId = parseId(String.valueOf(id));
        lockExistingModel(modelId);

        // 1. 检查是否有设备实例（无论在役或已注销）
        Long count = deviceInstancesMapper.selectCount(
                Wrappers.<DeviceInstances>lambdaQuery().eq(DeviceInstances::getDeviceModelId, modelId));
        if (count != null && count > 0) {
            throw new IllegalStateException("该模型下仍有 " + count + " 台设备实例（包含在役或已注销），无法删除；请先彻底删除相关实例后再试");
        }

        // 2. 检查是否有工作流节点引用
        if (flowNodeMapper != null) {
            Long flowNodeCount = flowNodeMapper.selectCount(
                    Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getDeviceModelId, modelId));
            if (flowNodeCount != null && flowNodeCount > 0) {
                throw new IllegalStateException("该模型已被工作流画布节点引用（共 " + flowNodeCount + " 处），无法删除；请先修改或删除相关工作流");
            }
        }

        // 3. 检查是否有约束规则引用
        if (constraintRuleMapper != null) {
            List<ConstraintRule> rules = constraintRuleMapper.selectList(Wrappers.emptyWrapper());
            for (ConstraintRule rule : rules) {
                if (hasModelBinding(rule.getBindings(), modelId)) {
                    String ruleName = rule.getRuleName() != null ? rule.getRuleName() : String.valueOf(rule.getId());
                    throw new IllegalStateException("该模型已被约束规则【" + ruleName + "】引用，无法删除；请先修改或删除相关约束规则");
                }
            }
        }

        dataTemplateService.deleteByModelIdForModelRemoval(modelId);
        mapper.deleteById(modelId);
    }

    private boolean hasModelBinding(JsonNode bindings, Long modelId) {
        if (bindings == null || !bindings.isObject() || modelId == null) return false;
        var fields = bindings.fields();
        while (fields.hasNext()) {
            JsonNode binding = fields.next().getValue();
            JsonNode source = binding.path("source");
            if (source.hasNonNull("deviceModelId") && source.path("deviceModelId").asLong(0) == modelId) {
                return true;
            }
        }
        return false;
    }

    private void validateFinalDeviceModel(DeviceModels model) {
        if (model.getModelName() == null || model.getModelName().isBlank()) {
            throw new IllegalArgumentException("deviceModelName不能为空");
        }
        if (model.getCategoryId() == null) {
            throw new IllegalArgumentException("deviceCategoryId不能为空");
        }
        validateCapabilityModelShape(model);
        validateCapabilityModelIdentifiers(model);
        validateCapabilityAbortSemantics(model.getCapabilities());
        validateModelAdapterContract(model);
    }

    void validateForPersistence(DeviceModels model) {
        validateFinalDeviceModel(model);
        validateDeviceStateMachine(model);
    }

    private void validateCapabilityModelShape(DeviceModels model) {
        JsonNode attributes = requireObjectArray(model.getAttributes(), "attributes");
        JsonNode capabilities = requireObjectArray(model.getCapabilities(), "capabilities");
        JsonNode ports = requireObjectArray(model.getPorts(), "ports");
        JsonNode intrinsicConstraints = requireObjectArray(model.getIntrinsicConstraint(), "intrinsicConstraints");
        for (JsonNode capability : capabilities) {
            requireObjectArray(capability.get("parameters"), "capabilities[].parameters");
            requireObjectArray(capability.get("parameterMapping"), "capabilities[].parameterMapping");
        }
        JsonNode contract = requireObject(model.getAdapterContract(), "adapterContract");
        JsonNode config = requireObject(contract.get("config"), "adapterContract.config");
        JsonNode commands = requireObjectArray(contract.get("commands"), "adapterContract.commands");
        for (JsonNode command : commands) {
            requireObjectArray(command.get("commandParameters"),
                    "adapterContract.commands[].commandParameters");
        }
        JsonNode telemetry = requireObject(contract.get("telemetry"), "adapterContract.telemetry");
        requireObjectArray(telemetry.get("adapterAttributes"),
                "adapterContract.telemetry.adapterAttributes");
        requireObjectArray(telemetry.get("attributesMapping"),
                "adapterContract.telemetry.attributesMapping");
        JsonNode events = requireObject(contract.get("events"), "adapterContract.events");
        requireObjectArray(events.get("cmdEvents"), "adapterContract.events.cmdEvents");
        requireObjectArray(events.get("opEvents"), "adapterContract.events.opEvents");
        for (JsonNode attribute : attributes) {
            requireText(attribute, "attributeName", "设备属性");
            String valueKind = requireText(attribute, "valueKind", "设备属性");
            if (!Set.of("DISCRETE", "CONTINUOUS").contains(valueKind)) {
                throw new IllegalArgumentException("设备属性valueKind不合法: " + valueKind);
            }
            validateDataType(attribute, "dataType", "设备属性");
        }
        for (JsonNode capability : capabilities) {
            requireText(capability, "capabilityName", "设备能力");
            requireText(capability, "adapterCommandName", "设备能力");
            requireText(capability, "displayName", "设备能力");
            requireSchemaBoolean(capability, "isAbort", "device capability");
            JsonNode abortCapabilityName = capability.get("abortCapabilityName");
            if (abortCapabilityName == null
                    || (!abortCapabilityName.isNull()
                    && (!abortCapabilityName.isTextual() || abortCapabilityName.textValue().isBlank()))) {
                throw new IllegalArgumentException("device capability missing or invalid abortCapabilityName");
            }
            JsonNode scope = capability.get("scope");
            if (scope != null && !scope.isArray()) {
                throw new IllegalArgumentException("device capability scope must be an array");
            }
            for (JsonNode parameter : iterable(capability.path("parameters"))) {
                requireText(parameter, "name", "能力参数");
                requireText(parameter, "displayName", "能力参数");
                validateDataType(parameter, "dataType", "能力参数");
            }
            for (JsonNode mapping : iterable(capability.path("parameterMapping"))) {
                requireSchemaText(mapping, "commandParamName", "能力参数映射");
                requireSchemaBoolean(mapping, "isFixedValue", "能力参数映射");
                validateOptionalSchemaText(mapping, "capabilityParamName", "能力参数映射");
            }
        }
        for (JsonNode port : ports) {
            requireSchemaText(port, "portName", "设备端口");
            String direction = requireSchemaText(port, "direction", "设备端口");
            if (!Set.of("IN", "OUT").contains(direction)) {
                throw new IllegalArgumentException("设备端口direction不合法: " + direction);
            }
            requireSchemaText(port, "bindingAttrName", "设备端口");
        }
        for (JsonNode constraint : intrinsicConstraints) {
            requireSchemaText(constraint, "objectAttributeName", "设备内置约束");
            String operator = requireSchemaText(constraint, "operator", "设备内置约束");
            if (!Set.of(">", "<", ">=", "<=", "=", "!=").contains(operator)) {
                throw new IllegalArgumentException("设备内置约束operator不合法: " + operator);
            }
            requireSchemaNumber(constraint, "boundaryValue", "设备内置约束");
            requireSchemaText(constraint, "violationStateName", "设备内置约束");
        }
        requireText(config, "adapterName", "adapterContract.config");
        requireText(config, "categoryName", "adapterContract.config");
        String protocol = requireText(config, "protocol", "adapterContract.config");
        if (!protocolDictionaryService.enumValues("CommunicationProtocol").contains(protocol)) {
            throw new IllegalArgumentException("Adapter通信协议不合法: " + protocol);
        }
        for (JsonNode command : commands) {
            requireText(command, "commandName", "Adapter命令");
            for (JsonNode parameter : iterable(command.path("commandParameters"))) {
                if (parameter.path("internal").asBoolean(false)) {
                    throw new IllegalArgumentException(
                            "定稿设备能力模型不允许Adapter内部参数: " + command.path("commandName").asText());
                }
                requireText(parameter, "paramName", "Adapter命令参数");
                validateDataType(parameter, "dataType", "Adapter命令参数");
            }
        }
        for (JsonNode attribute : iterable(telemetry.path("adapterAttributes"))) {
            requireText(attribute, "telemetryName", "Adapter遥测属性");
            validateDataType(attribute, "dataType", "Adapter遥测属性");
        }
        for (String group : List.of("cmdEvents", "opEvents")) {
            for (JsonNode event : iterable(events.path(group))) {
                requireText(event, "eventName", "Adapter事件");
                requireText(event, "description", "Adapter事件");
            }
        }
    }


    private JsonNode requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
        return node;
    }

    private JsonNode requireObjectArray(JsonNode node, String path) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException(path + " must be an array");
        }
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw new IllegalArgumentException(path + "[] must contain objects");
            }
        }
        return node;
    }
    private String requireText(JsonNode node, String fieldName, String scope) {
        String value = node == null ? "" : node.path(fieldName).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException(scope + "缺少" + fieldName);
        }
        return value;
    }

    private String requireSchemaText(JsonNode node, String fieldName, String scope) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(scope + "缺少或非法" + fieldName);
        }
        return value.textValue();
    }

    private boolean requireSchemaBoolean(JsonNode node, String fieldName, String scope) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(scope + "缺少或非法" + fieldName);
        }
        return value.booleanValue();
    }

    private void validateOptionalSchemaText(JsonNode node, String fieldName, String scope) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isTextual()) {
            throw new IllegalArgumentException(scope + fieldName + "类型非法");
        }
    }

    private void requireSchemaNumber(JsonNode node, String fieldName, String scope) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(scope + "缺少或非法" + fieldName);
        }
    }

    private void validateDataType(JsonNode node, String fieldName, String scope) {
        String value = requireText(node, fieldName, scope);
        if (!protocolDictionaryService.enumValues("DataType").contains(value)) {
            throw new IllegalArgumentException(scope + "数据类型不合法: " + value);
        }
    }

    private void validateDeviceStateMachine(DeviceModels model) {
        JsonNode interfaces = model.getStateMachineInterfaces();
        Map<String, JsonNode> expected = new HashMap<>();
        for (JsonNode item : generateStandardInterfaces(extractAdapterEvents(model.getAdapterContract()))) {
            expected.put(item.path("name").asText(), item);
        }
        Map<String, JsonNode> actual = new HashMap<>();
        for (JsonNode item : iterable(interfaces)) {
            String name = item.path("name").asText("");
            if (name.isBlank() || actual.put(name, item) != null) {
                throw new IllegalArgumentException("状态机接口名称为空或重复: " + name);
            }
        }
        if (!expected.keySet().equals(actual.keySet())) {
            throw new IllegalArgumentException("状态机必须声明定稿标准接口: " + expected.keySet());
        }
        for (String name : expected.keySet()) {
            JsonNode actualInterface = actual.get(name);
            JsonNode expectedInterface = expected.get(name);
            if (!expectedInterface.path("direction").asText().equals(actualInterface.path("direction").asText())
                    || !expectedInterface.path("interfaceType").asText()
                            .equals(actualInterface.path("interfaceType").asText())
                    || !textSet(expectedInterface.path("allowedSignals"))
                            .containsAll(textSet(actualInterface.path("allowedSignals")))) {
                throw new IllegalArgumentException("状态机接口包含未定义的非法信号: " + name);
            }
        }

        Set<String> cmdStates = stateNames(model.getCmdState(), "CMD状态空间");
        if (!cmdStates.containsAll(SystemExecutionContract.commandStateNames())) {
            throw new IllegalArgumentException("CMD状态空间不完整");
        }
        Map<String, Set<String>> opStates = operationStateNames(model.getOpState());
        String exceptionRegionName = null;
        for (JsonNode region : iterable(model.getOpState().path("regions"))) {
            if ("EXCEPTION".equals(region.path("regionType").asText(""))) {
                if (exceptionRegionName != null) {
                    throw new IllegalArgumentException("OP状态空间最多只能有一个EXCEPTION区域");
                }
                exceptionRegionName = region.path("regionName").asText("");
            }
        }
        for (JsonNode constraint : iterable(model.getIntrinsicConstraint())) {
            String violation = constraint.path("violationStateName").asText("");
            if (exceptionRegionName == null || !opStates.getOrDefault(exceptionRegionName, Set.of()).contains(violation)) {
                throw new IllegalArgumentException("内置约束违规状态必须引用EXCEPTION区域状态: " + violation);
            }
        }
        if (exceptionRegionName != null) {
            Set<String> referencedExceptionStates = new HashSet<>();
            for (JsonNode constraint : iterable(model.getIntrinsicConstraint())) {
                referencedExceptionStates.add(constraint.path("violationStateName").asText(""));
            }
            for (String stateName : opStates.getOrDefault(exceptionRegionName, Set.of())) {
                if (!referencedExceptionStates.contains(stateName)) {
                    throw new IllegalArgumentException("EXCEPTION状态必须被至少一条内置约束引用: " + stateName);
                }
            }
        }
        Set<String> cmdEvents = eventNames(model.getAdapterContract().path("events").path("cmdEvents"));
        Set<String> opEvents = eventNames(model.getAdapterContract().path("events").path("opEvents"));
        Set<String> transitionKeys = new HashSet<>();
        Set<String> commandTransitionPaths = new HashSet<>();
        for (JsonNode transition : iterable(model.getStateTransitions())) {
            String stateSpace = transition.path("stateSpace").asText("");
            String fromState = transition.path("fromStateName").asText("");
            String toState = transition.path("toStateName").asText("");
            JsonNode trigger = transition.path("trigger");
            String interfaceName = trigger.path("interfaceName").asText("");
            String signalName = trigger.path("signalName").asText("");
            String regionName = "OP".equals(stateSpace) ? transition.path("regionName").asText("") : "";
            if (!Set.of("CMD", "OP").contains(stateSpace) || !"Interface_adapter_in".equals(interfaceName)
                    || signalName.isBlank()) {
                throw new IllegalArgumentException("设备状态转移只能由Interface_adapter_in的Adapter事件触发");
            }
            if (!transitionKeys.add(stateSpace + "|" + regionName + "|" + fromState + "|"
                    + interfaceName + "|" + signalName)) {
                throw new IllegalArgumentException("同一状态和Adapter事件只能对应一条转移: " + fromState + "/" + signalName);
            }
            if ("CMD".equals(stateSpace)) {
                if (!cmdStates.contains(fromState) || !cmdStates.contains(toState) || !cmdEvents.contains(signalName)) {
                    throw new IllegalArgumentException(
                            "CMD状态转移引用了错误状态或事件: " + fromState + "→" + toState + "/" + signalName);
                }
                commandTransitionPaths.add(fromState + "|" + toState);
            } else {
                Set<String> regionStates = opStates.get(regionName);
                JsonNode regionNode = findOperationRegion(model.getOpState(), regionName);
                if (regionNode == null || !"OPERATIONAL".equals(regionNode.path("regionType").asText(""))) {
                    throw new IllegalArgumentException("OP状态转移不能引用EXCEPTION区域: " + regionName);
                }
                if (regionStates == null || !regionStates.contains(fromState) || !regionStates.contains(toState)
                        || !opEvents.contains(signalName)) {
                    throw new IllegalArgumentException("OP状态转移引用了错误分区、状态或事件: " + regionName + "/" + signalName);
                }
            }
            validateStateMachineActions(transition.path("actions"), actual);
        }
        for (SystemExecutionContract.DeviceCommandTransitionRequirement requirement : SystemExecutionContract
                .deviceCommandTransitionRequirements()) {
            if ("REQUIRED".equals(requirement.triggerPolicy())
                    && !commandTransitionPaths.contains(requirement.fromStateName() + "|" + requirement.toStateName())) {
                throw new IllegalArgumentException("缺少必需Adapter事件CMD转移: " + requirement.fromStateName()
                        + "→" + requirement.toStateName());
            }
        }
        validateStateMachineActions(model.getCmdState().path("states"), actual);
        validateStatusOnEntry(model.getCmdState().path("states"), "CMD_STATE", "CMD");
        for (JsonNode region : model.getOpState().path("regions")) {
            validateStateMachineActions(region.path("states"), actual);
            validateStatusOnEntry(region.path("states"), "OP_STATE",
                    "OP区域" + region.path("regionName").asText(""));
        }
    }

    private void validateStatusOnEntry(JsonNode states, String expectedSignal, String owner) {
        for (JsonNode state : iterable(states)) {
            int count = 0;
            for (JsonNode action : iterable(state.path("onEntry"))) {
                JsonNode payload = action.path("payload");
                if ("SEND".equals(action.path("actionName").asText(""))
                        && "Interface_state_out".equals(payload.path("interfaceName").asText(""))
                        && expectedSignal.equals(payload.path("signalName").asText(""))) {
                    count++;
                }
            }
            if (count != 1) {
                throw new IllegalArgumentException(owner + "状态" + state.path("stateName").asText("")
                        + "的onEntry必须且只能声明一个" + expectedSignal + " SEND动作");
            }
        }
    }

    private void validateStateMachineActions(JsonNode nodes, Map<String, JsonNode> interfaces) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            JsonNode actions = node.has("actionName") ? JsonNodeSupport.arrayNode().add(node) : node.path("onEntry");
            for (JsonNode action : iterable(actions)) {
                if (!"SEND".equals(action.path("actionName").asText())) {
                    throw new IllegalArgumentException("状态机只支持SEND动作");
                }
                JsonNode payload = action.path("payload");
                String interfaceName = payload.path("interfaceName").asText("");
                String signalName = payload.path("signalName").asText("");
                JsonNode target = interfaces.get(interfaceName);
                if (target == null || !"OUT".equals(target.path("direction").asText())
                        || !textSet(target.path("allowedSignals")).contains(signalName)) {
                    throw new IllegalArgumentException("SEND动作目标接口或信号不合法: " + interfaceName + "." + signalName);
                }
            }
        }
    }

    private Map<String, Set<String>> operationStateNames(JsonNode opState) {
        Map<String, Set<String>> result = new HashMap<>();
        if (opState == null || !opState.isObject()) {
            throw new IllegalArgumentException("OP状态空间必须是对象");
        }
        for (JsonNode region : iterable(opState == null ? null : opState.path("regions"))) {
            String regionName = requireText(region, "regionName", "OP状态分区");
            String regionType = requireText(region, "regionType", "OP状态分区" + regionName);
            if (!Set.of("OPERATIONAL", "EXCEPTION").contains(regionType)) {
                throw new IllegalArgumentException("OP状态分区regionType不合法: " + regionType);
            }
            if (result.containsKey(regionName)) {
                throw new IllegalArgumentException("OP状态分区名称重复: " + regionName);
            }
            Set<String> names = stateNames(region, "OP状态分区" + regionName);
            String initialStateName = region.path("initialStateName").asText("");
            if ("EXCEPTION".equals(regionType)) {
                if (!initialStateName.isEmpty()) {
                    throw new IllegalArgumentException("EXCEPTION区域initialStateName必须为空字符串: " + regionName);
                }
            } else if (!names.contains(initialStateName)) {
                throw new IllegalArgumentException("OP状态分区initialStateName未引用分区内状态: " + initialStateName);
            }
            result.put(regionName, names);
        }
        return result;
    }

    private Set<String> stateNames(JsonNode stateSpace, String stateSpaceName) {
        Set<String> names = new HashSet<>();
        for (JsonNode state : iterable(stateSpace == null ? null : stateSpace.path("states"))) {
            String name = requireText(state, "stateName", stateSpaceName);
            if (!names.add(name)) {
                throw new IllegalArgumentException(stateSpaceName + "状态名重复: " + name);
            }
        }
        return names;
    }

    private JsonNode findOperationRegion(JsonNode opState, String regionName) {
        for (JsonNode region : iterable(opState == null ? null : opState.path("regions"))) {
            if (regionName.equals(region.path("regionName").asText(""))) return region;
        }
        return null;
    }

    private Set<String> eventNames(JsonNode events) {
        Set<String> names = new HashSet<>();
        for (JsonNode event : iterable(events)) {
            String name = event.path("eventName").asText("");
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private Set<String> textSet(JsonNode values) {
        Set<String> result = new HashSet<>();
        for (JsonNode value : iterable(values)) {
            String text = value.asText("");
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private void validateModelAdapterContract(DeviceModels model) {
        JsonNode contract = model.getAdapterContract();
        if (contract == null || contract.isNull() || contract.isMissingNode()) {
            return;
        }

        Map<String, String> modelAttrTypes = new HashMap<>();
        for (JsonNode attr : iterable(model.getAttributes())) {
            String name = attr.path("attributeName").asText("");
            if (!name.isBlank()) {
                modelAttrTypes.put(name, normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }

        Map<String, String> adapterAttrTypes = new HashMap<>();
        for (JsonNode attr : iterable(contract.path("telemetry").path("adapterAttributes"))) {
            String name = attr.path("telemetryName").asText("");
            if (!name.isBlank()) {
                adapterAttrTypes.put(name, normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }

        for (JsonNode mapping : iterable(contract.path("telemetry").path("attributesMapping"))) {
            String adapterAttr = mapping.path("adapterAttrName").asText("");
            String modelAttr = mapping.path("modelAttributeName").asText("");
            if (adapterAttr.isBlank() || modelAttr.isBlank()) {
                throw new IllegalArgumentException("属性映射缺少adapterAttrName或modelAttributeName");
            }
            String adapterType = adapterAttrTypes.get(adapterAttr);
            String modelType = modelAttrTypes.get(modelAttr);
            if (adapterType == null) {
                throw new IllegalArgumentException("属性映射引用了不存在的 Adapter 属性: " + adapterAttr);
            }
            if (modelType == null) {
                throw new IllegalArgumentException("属性映射引用了不存在的模型属性: " + modelAttr);
            }
            if (!adapterType.equals(modelType)) {
                throw new IllegalArgumentException(
                        "属性映射类型不一致: " + modelAttr + "(" + modelType + ") -> " + adapterAttr + "(" + adapterType + ")");
            }
        }

        Map<String, Map<String, JsonNode>> commandParamsByCommand = new HashMap<>();
        for (JsonNode command : iterable(contract.path("commands"))) {
            String commandName = command.path("commandName").asText(command.path("name").asText(""));
            if (commandName.isBlank()) {
                continue;
            }
            Map<String, JsonNode> params = new HashMap<>();
            for (JsonNode param : iterable(
                    command.path("commandParameters").isMissingNode() ? command.path("parameters")
                            : command.path("commandParameters"))) {
                String paramName = param.path("paramName").asText(param.path("name").asText(""));
                if (!paramName.isBlank()) {
                    params.put(paramName, param);
                }
            }
            commandParamsByCommand.put(commandName, params);
        }

        for (JsonNode capability : iterable(model.getCapabilities())) {
            String capabilityName = capability.path("capabilityName").asText("");
            String commandName = capability.path("adapterCommandName").asText("");
            if (commandName.isBlank()) {
                continue;
            }
            Map<String, JsonNode> commandParams = commandParamsByCommand.get(commandName);
            if (commandParams == null) {
                throw new IllegalArgumentException("操作 " + capabilityName + " 引用了不存在的 Adapter 命令: " + commandName);
            }
            validateCapabilityParameterMapping(capability, commandName, commandParams);
        }

    }

    private void validateCapabilityAbortSemantics(JsonNode capabilities) {
        Map<String, JsonNode> byName = new HashMap<>();
        for (JsonNode capability : iterable(capabilities)) {
            byName.put(capability.path("capabilityName").asText(""), capability);
        }

        for (JsonNode capability : iterable(capabilities)) {
            String capabilityName = capability.path("capabilityName").asText("");
            boolean isAbort = capability.path("isAbort").asBoolean(false);
            JsonNode abortNameNode = capability.get("abortCapabilityName");
            String abortCapabilityName = abortNameNode != null && abortNameNode.isTextual()
                    ? abortNameNode.textValue().trim() : "";
            JsonNode scope = capability.get("scope");

            if (isAbort) {
                if (!abortCapabilityName.isBlank()) {
                    throw new IllegalArgumentException(
                            "abort capability must set abortCapabilityName to null: " + capabilityName);
                }
                if (scope == null || !scope.isArray() || scope.isEmpty()) {
                    throw new IllegalArgumentException(
                            "abort capability must declare a non-empty scope: " + capabilityName);
                }
                Set<String> scopeNames = new HashSet<>();
                for (JsonNode item : scope) {
                    if (!item.isTextual() || item.textValue().isBlank()) {
                        throw new IllegalArgumentException(
                                "abort capability scope contains an invalid capability name: " + capabilityName);
                    }
                    String affectedName = item.textValue().trim();
                    if (!scopeNames.add(affectedName)) {
                        throw new IllegalArgumentException(
                                "abort capability scope contains a duplicate: " + capabilityName + " -> " + affectedName);
                    }
                    JsonNode affected = byName.get(affectedName);
                    if (affected == null) {
                        throw new IllegalArgumentException(
                                "abort capability scope references a missing capability: " + affectedName);
                    }
                    if (affected.path("isAbort").asBoolean(false)) {
                        throw new IllegalArgumentException(
                                "abort capability scope may reference only normal capabilities: " + affectedName);
                    }
                }
                continue;
            }

            if (scope != null && scope.size() > 0) {
                throw new IllegalArgumentException(
                        "normal capability must not declare scope: " + capabilityName);
            }
            if (abortCapabilityName.isBlank()) {
                continue;
            }
            JsonNode abortCapability = byName.get(abortCapabilityName);
            if (abortCapability == null) {
                throw new IllegalArgumentException(
                        "abortCapabilityName references a missing capability: " + abortCapabilityName);
            }
            if (!abortCapability.path("isAbort").asBoolean(false)) {
                throw new IllegalArgumentException(
                        "abortCapabilityName must reference an abort capability: " + abortCapabilityName);
            }
            boolean coveredByScope = false;
            for (JsonNode item : iterable(abortCapability.path("scope"))) {
                if (capabilityName.equals(item.asText(""))) {
                    coveredByScope = true;
                    break;
                }
            }
            if (!coveredByScope) {
                throw new IllegalArgumentException(
                        "referenced abort capability scope must include normal capability: "
                                + abortCapabilityName + " -> " + capabilityName);
            }
        }
    }

    private void validateCapabilityModelIdentifiers(DeviceModels model) {
        ensureUniqueNames(model.getAttributes(), "attributeName", "属性");
        ensureUniqueNames(model.getCapabilities(), "capabilityName", "能力");
        for (JsonNode capability : iterable(model.getCapabilities())) {
            String capabilityName = capability.path("capabilityName").asText("");
            ensureUniqueNames(capability.path("parameters"), "name", "能力" + capabilityName + "的参数");
        }
    }

    private void ensureUniqueNames(JsonNode rows, String fieldName, String label) {
        Set<String> names = new HashSet<>();
        for (JsonNode row : iterable(rows)) {
            String name = row.path(fieldName).asText("");
            if (name.isBlank()) {
                throw new IllegalArgumentException(label + "标识符不能为空: " + fieldName);
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException(label + "标识符重复: " + name);
            }
        }
    }

    private void validateCapabilityParameterMapping(JsonNode capability, String commandName,
            Map<String, JsonNode> commandParams) {
        Map<String, String> capabilityParamTypes = new HashMap<>();
        for (JsonNode param : iterable(capability.path("parameters"))) {
            String paramName = param.path("name").asText("");
            if (!paramName.isBlank()) {
                capabilityParamTypes.put(paramName, normalizeDataType(param.path("dataType").asText("STRING")));
            }
        }

        Set<String> mappedCommandParams = new HashSet<>();
        for (JsonNode mapping : iterable(capability.path("parameterMapping"))) {
            String commandParamName = mapping.path("commandParamName").asText("");
            if (commandParamName.isBlank()) {
                continue;
            }
            JsonNode commandParam = commandParams.get(commandParamName);
            if (commandParam == null) {
                throw new IllegalArgumentException("参数映射引用了不存在的命令参数: " + commandName + "." + commandParamName);
            }
            if (commandParam.path("internal").asBoolean(false)) {
                throw new IllegalArgumentException("隐藏命令参数不能在设备模型中映射: " + commandName + "." + commandParamName);
            }
            mappedCommandParams.add(commandParamName);
            String commandType = normalizeDataType(commandParam.path("dataType").asText("STRING"));
            if (mapping.path("isFixedValue").asBoolean(false)) {
                if (!mapping.has("fixedValue") || mapping.path("fixedValue").isNull()) {
                    throw new IllegalArgumentException("固定参数缺少 fixedValue: " + commandName + "." + commandParamName);
                }
                if (!isFixedValueCompatible(commandType, mapping.path("fixedValue"))) {
                    throw new IllegalArgumentException(
                            "固定参数值类型不匹配: " + commandName + "." + commandParamName + " 需要 " + commandType);
                }
                continue;
            }

            String capabilityParamName = mapping.path("capabilityParamName").asText("");
            String capabilityType = capabilityParamTypes.get(capabilityParamName);
            if (capabilityType == null) {
                throw new IllegalArgumentException("参数映射引用了不存在的操作参数: " + capabilityParamName);
            }
            if (!commandType.equals(capabilityType)) {
                throw new IllegalArgumentException("参数映射类型不一致: " + capabilityParamName + "(" + capabilityType + ") -> "
                        + commandParamName + "(" + commandType + ")");
            }
        }

        for (Map.Entry<String, JsonNode> entry : commandParams.entrySet()) {
            if (!entry.getValue().path("internal").asBoolean(false) && !mappedCommandParams.contains(entry.getKey())) {
                throw new IllegalArgumentException("命令 " + commandName + " 的参数未映射: " + entry.getKey());
            }
        }
    }

    private boolean isFixedValueCompatible(String dataType, JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        return switch (normalizeDataType(dataType)) {
            case "BOOLEAN" -> value.isBoolean() || "true".equalsIgnoreCase(value.asText())
                    || "false".equalsIgnoreCase(value.asText());
            case "INTEGER" -> value.isIntegralNumber() || value.asText().matches("-?\\d+");
            case "DOUBLE" -> value.isNumber() || isNumeric(value.asText());
            default -> !value.asText().isBlank();
        };
    }

    private boolean isNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String normalizeDataType(String dataType) {
        return adapterManifestService.normalizeDataType(dataType);
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isArray()) {
            return JsonNodeSupport.arrayNode();
        }
        return node;
    }

    private ObjectNode toStateMachineView(DeviceModels model) {
        ObjectNode node = JsonNodeSupport.objectNode();
        if (model.getId() != null) {
            node.put("stateMachineId", model.getId() + "StateMachine");
            node.put("deviceModelRef", String.valueOf(model.getId()));
        } else {
            node.putNull("stateMachineId");
            node.putNull("deviceModelRef");
        }
        node.set("interfacesDef", nullToObject(model.getStateMachineInterfaces()));
        node.set("commandLifecycleDef", nullToObject(model.getCmdState()));
        node.set("operationStateDef", nullToObject(model.getOpState()));
        node.set("transitions", nullToArray(model.getStateTransitions()));
        return node;
    }

    private JsonNode nullToObject(JsonNode node) {
        return node == null ? JsonNodeSupport.objectNode() : node;
    }

    private JsonNode nullToArray(JsonNode node) {
        return node == null ? JsonNodeSupport.arrayNode() : node;
    }

    private JsonNode canonicalAdapterContract(JsonNode inputContract) {
        if (inputContract == null || inputContract.isNull() || inputContract.isMissingNode()) {
            return inputContract;
        }
        JsonNode config = inputContract.path("config");
        String adapterName = config.path("adapterName").asText("");
        String categoryName = config.path("categoryName").asText("");
        if (!adapterName.isBlank() && !categoryName.isBlank()) {
            ObjectNode canonical = adapterIndexService.buildAdapterContract(adapterName, categoryName);
            JsonNode submittedMappings = inputContract.path("telemetry").path("attributesMapping");
            if (submittedMappings.isArray()) {
                ((ObjectNode) canonical.path("telemetry"))
                        .set("attributesMapping", submittedMappings.deepCopy());
            }
            return canonical;
        }
        return inputContract;
    }
}
