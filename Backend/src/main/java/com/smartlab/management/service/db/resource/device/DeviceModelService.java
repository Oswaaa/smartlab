package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.schema.SchemaMetadataService;
import com.smartlab.global.schema.StateMachineInterfacePolicyService;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.global.util.JsonSchemaValidationService;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.dto.resource.device.DeviceModelSaveDTO;
import com.smartlab.management.dto.resource.device.DeviceStateMachineSaveDTO;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.adapter.AdapterManifestService;
import com.smartlab.management.service.db.common.ManagementCrudService;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.db.resource.data.DataTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
    private final SchemaMetadataService schemaMetadataService;
    private final StateMachineInterfacePolicyService stateMachineInterfacePolicyService;
    private final DataTemplateService dataTemplateService;
    private final JsonSchemaValidationService schemaValidationService;
    private final AdapterIndexService adapterIndexService;

    public DeviceModelService(DeviceModelsMapper mapper,
            DeviceInstancesMapper deviceInstancesMapper,
            DeviceCategoryService deviceCategoryService,
            AdapterManifestService adapterManifestService,
            ProtocolDictionaryService protocolDictionaryService,
            SchemaMetadataService schemaMetadataService,
            StateMachineInterfacePolicyService stateMachineInterfacePolicyService,
            DataTemplateService dataTemplateService,
            JsonSchemaValidationService schemaValidationService,
            AdapterIndexService adapterIndexService) {
        super(mapper);
        this.mapper = mapper;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceCategoryService = deviceCategoryService;
        this.adapterManifestService = adapterManifestService;
        this.protocolDictionaryService = protocolDictionaryService;
        this.schemaMetadataService = schemaMetadataService;
        this.stateMachineInterfacePolicyService = stateMachineInterfacePolicyService;
        this.dataTemplateService = dataTemplateService;
        this.schemaValidationService = schemaValidationService;
        this.adapterIndexService = adapterIndexService;
    }

    @Override
    public List<DeviceModels> list() {
        return mapper.selectList(Wrappers.<DeviceModels>lambdaQuery().orderByDesc(DeviceModels::getId));
    }

    public PageResult<DeviceModels> page(long pageNo, long pageSize, String keyword) {
        return page(pageNo, pageSize, keyword, (Long) null);
    }

    public PageResult<DeviceModels> page(long pageNo, long pageSize, String keyword, Long categoryId) {
        if (categoryId == null) {
            return super.page(pageNo, pageSize, keyword, "model_name");
        }
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeviceModels> wrapper = com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery();
        wrapper.eq(DeviceModels::getCategoryId, categoryId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(DeviceModels::getModelName, keyword.trim());
        }
        wrapper.orderByDesc(DeviceModels::getId);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeviceModels> page = mapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public DeviceModels getById(String id) {
        return mapper.selectById(parseId(id));
    }

    /**
     * 更新设备模型的 Adapter 北向契约字段。
     */
    public DeviceModels updateAdapterContract(Long modelId, JsonNode adapterContract) {
        DeviceModels model = mapper.selectById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        model.setAdapterContract(canonicalAdapterContract(adapterContract));
        validateModelAdapterContract(model);
        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
        return model;
    }

    /**
     * 查询所有设备模型中的状态机配置。
     */
    public List<ObjectNode> listStateMachines() {
        return list().stream().map(this::toStateMachineView).toList();
    }

    /**
     * 保存设备模型中的状态机相关 JSON 字段。
     */
    public String saveStateMachine(DeviceStateMachineSaveDTO payload) {
        if (payload == null || payload.getModelId() == null) {
            throw new IllegalArgumentException("modelId 不能为空");
        }
        DeviceModels model = getById(String.valueOf(payload.getModelId()));
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在，无法保存状态机");
        }
        if (payload.getInterfacesDef() != null) {
            model.setStateMachineInterfaces(payload.getInterfacesDef());
        }
        if (payload.getCommandLifecycleDef() != null) {
            model.setCmdState(payload.getCommandLifecycleDef());
        }
        if (payload.getOperationStateDef() != null) {
            model.setOpState(payload.getOperationStateDef());
        }
        if (payload.getTransitions() != null) {
            model.setStateTransitions(payload.getTransitions());
        }

        enrichStateMachine(model, payload.getOperationStateDef(), payload.getTransitions());

        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
        return String.valueOf(model.getId());
    }

    /**
     * 清空设备模型中的状态机相关 JSON 字段。
     */
    public void deleteStateMachine(String modelId) {
        DeviceModels model = getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        model.setStateMachineInterfaces(null);
        model.setCmdState(null);
        model.setOpState(null);
        model.setStateTransitions(null);
        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
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

    /**
     * 保存设备模型内置约束规则。
     */
    public void saveModelConstraintRule(Map<String, Object> payload) {
        String modelId = Objects.toString(payload.get("modelId"), "");
        DeviceModels model = getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        ArrayNode constraints = model.getIntrinsicConstraint() != null && model.getIntrinsicConstraint().isArray()
                ? (ArrayNode) model.getIntrinsicConstraint()
                : JsonNodeSupport.arrayNode();
        String ruleId = Objects.toString(payload.getOrDefault("constraintRuleId", "RULE_" + UUID.randomUUID()), "");
        ArrayNode next = JsonNodeSupport.arrayNode();
        constraints.forEach(rule -> {
            if (!ruleId.equals(rule.path("constraintRuleId").asText())) {
                next.add(rule);
            }
        });
        Map<String, Object> storedRule = new HashMap<>(payload);
        storedRule.put("constraintRuleId", ruleId);
        if (!storedRule.containsKey("violationStateRef") && storedRule.containsKey("action")) {
            storedRule.put("violationStateRef", storedRule.get("action"));
        }
        next.add(JsonNodeSupport.toNode(storedRule));
        model.setIntrinsicConstraint(next);
        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
    }

    /**
     * 删除设备模型内置约束规则。
     */
    public void deleteModelConstraintRule(String modelId, String constraintRuleId) {
        DeviceModels model = getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        ArrayNode next = JsonNodeSupport.arrayNode();
        JsonNode current = model.getIntrinsicConstraint();
        if (current != null && current.isArray()) {
            current.forEach(rule -> {
                if (!constraintRuleId.equals(rule.path("constraintRuleId").asText())) {
                    next.add(rule);
                }
            });
        }
        model.setIntrinsicConstraint(next);
        model.setUpdateTime(OffsetDateTime.now());
        mapper.updateById(model);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceModels savePayload(DeviceModelSaveDTO payload) {
        if (payload == null) {
            throw new IllegalArgumentException("设备模型保存请求不能为空");
        }
        DeviceModels model = new DeviceModels();
        if (payload.getModelId() != null) {
            model.setId(payload.getModelId());
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
        validateModelAdapterContract(model);

        if (model.getId() == null) {
            mapper.insert(model);
        } else {
            mapper.updateById(model);
        }
        saveDefaultDataTemplate(model.getId(), model.getModelName(), payload.getDefaultDataTemplate());
        return model;
    }

    private void saveDefaultDataTemplate(Long modelId, String modelName, JsonNode templateNode) {
        if (modelId == null || templateNode == null || templateNode.isNull() || templateNode.isMissingNode()) {
            return;
        }
        if (templateNode.has("enabled") && !templateNode.path("enabled").asBoolean(true)) {
            return;
        }
        JsonNode mainNode = templateNode.has("main") ? templateNode.path("main") : templateNode;
        JsonNode detailsNode = templateNode.has("details") ? templateNode.path("details") : templateNode.path("columns");
        if (detailsNode == null || !detailsNode.isArray() || detailsNode.size() == 0) {
            return;
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
            return;
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
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull() || node.get(field).asText().isBlank()) {
            return null;
        }
        return node.get(field).asLong();
    }

    private Integer integerValue(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull() || node.get(field).asText().isBlank()) {
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
        validateModelAdapterContract(model);

        return toModelBundle(model);
    }

    public ObjectNode modelBundle(String id) {
        DeviceModels model = getById(id);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        return toModelBundle(model);
    }

    private ObjectNode toModelBundle(DeviceModels model) {
        ObjectNode capabilityModel = toCapabilityModel(model);
        ObjectNode stateMachineModel = toStateMachineModel(model);
        schemaValidationService.validate(capabilityModel, "device-capability-model.json");
        schemaValidationService.validate(stateMachineModel, "device-state-machine-model.json");

        ObjectNode result = JsonNodeSupport.objectNode();
        result.set("capabilityModel", capabilityModel);
        result.set("stateMachineModel", stateMachineModel);
        return result;
    }

    private ObjectNode toCapabilityModel(DeviceModels model) {
        ObjectNode capabilityModel = JsonNodeSupport.objectNode();
        ObjectNode metadata = JsonNodeSupport.objectNode();
        if (model.getId() != null) {
            metadata.put("modelId", model.getId());
        } else {
            metadata.putNull("modelId");
        }
        metadata.put("modelName", Objects.toString(model.getModelName(), ""));
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
        if (model.getId() != null) {
            stateMachineModel.put("deviceModelId", model.getId());
        } else {
            stateMachineModel.putNull("deviceModelId");
        }
        stateMachineModel.set("interfaces", nullToArray(model.getStateMachineInterfaces()));
        stateMachineModel.set("opStateSpace", nullToObject(model.getOpState()));
        stateMachineModel.set("cmdLifecycleSpace", nullToObject(model.getCmdState()));
        stateMachineModel.set("transitions", nullToArray(model.getStateTransitions()));
        return stateMachineModel;
    }

    private void enrichStateMachine(DeviceModels model, JsonNode opStateNode, JsonNode transitionNode) {
        List<String> adapterEvents = extractAdapterEvents(model.getAdapterContract());
        ArrayNode interfaces = generateStandardInterfaces(adapterEvents);
        model.setStateMachineInterfaces(interfaces);

        ObjectNode cmdSpace = generateStandardCmdLifecycleSpace();
        model.setCmdState(cmdSpace);

        JsonNode opNode = opStateNode != null ? opStateNode : model.getOpState();
        ObjectNode opSpace = parseOpStateSpace(opNode);
        model.setOpState(opSpace);

        ArrayNode transitions = JsonNodeSupport.arrayNode();
        addStandardTransitions(transitions);

        JsonNode transNode = transitionNode != null ? transitionNode : model.getStateTransitions();
        mergeCustomTransitions(transitions, transNode);

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
        interfaces.add(createInterface("Interface_workflow_in", "IN", "WORKFLOW", protocolArray("WorkflowControlSignal")));
        ArrayNode statusSignals = JsonNodeSupport.arrayNode();
        statusSignals.add("OP_STATE");
        statusSignals.add("CMD_STATE");
        interfaces.add(createInterface("Interface_status_out", "OUT", "STAT", statusSignals));
        interfaces.add(createInterface("Interface_control_in", "IN", "CONTROL", protocolArray("ManualControlSignal")));
        interfaces.add(createInterface("Interface_constraint_in", "IN", "CONSTRAINT", protocolArray("ConstraintControlSignal")));
        interfaces.add(createInterface("Interface_adapter_out", "OUT", "ADAPTER", protocolArray("AdapterOutboundSignal")));

        ArrayNode adapterInSignals = JsonNodeSupport.arrayNode();
        for (String sig : adapterEvents) {
            adapterInSignals.add(sig);
        }
        interfaces.add(createInterface("Interface_adapter_in", "IN", "ADAPTER", adapterInSignals));
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
        ArrayNode states = JsonNodeSupport.arrayNode();
        for (String stateName : new String[]{"IDLE", "SENT", "RECEIVED", "RUNNING", "COMPLETED", "ABORTED", "FAILED"}) {
            ObjectNode state = JsonNodeSupport.objectNode();
            state.put("stateName", stateName);
            state.set("onEntry", createStatusOnEntryActions("CMD_STATE", stateName));
            states.add(state);
        }
        cmdSpace.set("states", states);
        return cmdSpace;
    }
    private ArrayNode createStatusOnEntryActions(String signalName, String stateName) {
        ArrayNode actions = JsonNodeSupport.arrayNode();
        ObjectNode action = JsonNodeSupport.objectNode();
        action.put("actionName", "SEND");
        ObjectNode payload = JsonNodeSupport.objectNode();
        payload.put("interfaceName", "Interface_status_out");
        payload.put("signalName", protocolSignal(signalName));
        payload.put("stateName", stateName);
        action.set("payload", payload);
        actions.add(action);
        return actions;
    }

    private void addStandardTransitions(ArrayNode transitions) {
        JsonNode systemTransitions = schemaMetadataService.frontendMetadata().path("stateMachine").path("systemTransitions");
        if (systemTransitions.isArray()) {
            for (JsonNode t : systemTransitions) {
                transitions.add(t.deepCopy());
            }
        }
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

    private Set<String> standardTriggerSignals() {
        return Set.of(
            "WF_EXECUTE_START",
            "WF_EXECUTE_ABORT",
            "MANUAL_EXECUTE_START",
            "MANUAL_EXECUTE_ABORT",
            "CONSTRAINT_ABORT",
            "COMMAND_RECEIVED",
            "COMMAND_RUNNING",
            "COMMAND_COMPLETED",
            "COMMAND_FAILED",
            "COMMAND_TIMEOUT",
            "COMMAND_CANCELLED"
        );
    }

    private Set<String> standardSystemSignals() {
        return Set.of(
            "WF_EXECUTE_START",
            "WF_EXECUTE_ABORT",
            "MANUAL_EXECUTE_START",
            "MANUAL_EXECUTE_ABORT",
            "CONSTRAINT_ABORT",
            "COMMAND_RECEIVED",
            "COMMAND_RUNNING",
            "COMMAND_COMPLETED",
            "COMMAND_FAILED",
            "COMMAND_TIMEOUT",
            "COMMAND_CANCELLED",
            "CMD_START",
            "CMD_ABORT",
            "CMD_STATE",
            "OP_STATE"
        );
    }
    private ObjectNode parseOpStateSpace(JsonNode node) {
        ObjectNode opSpace = JsonNodeSupport.objectNode();
        ArrayNode regions = opSpace.putArray("regions");
        if (node != null && node.has("regions") && node.path("regions").isArray()) {
            for (JsonNode regionNode : node.path("regions")) {
                if (regionNode.isObject()) {
                    ObjectNode region = regions.addObject();
                    region.put("regionName", regionNode.path("regionName").asText(""));
                    region.put("initialStateName", regionNode.path("initialStateName").asText(""));

                    ArrayNode states = region.putArray("states");
                    JsonNode inputStates = regionNode.get("states");
                    if (inputStates != null && inputStates.isArray()) {
                        for (JsonNode s : inputStates) {
                            if (s.isObject() && s.has("stateName")) {
                                ObjectNode stateObj = states.addObject();
                                stateObj.put("stateName", s.get("stateName").asText());
                                JsonNode onEntry = s.get("onEntry");
                                stateObj.set("onEntry", onEntry != null && onEntry.isArray() ? onEntry.deepCopy() : JsonNodeSupport.arrayNode());
                            } else if (s.isTextual()) {
                                ObjectNode stateObj = states.addObject();
                                stateObj.put("stateName", s.asText());
                                stateObj.putArray("onEntry");
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

        Set<String> standardTriggerSignals = standardTriggerSignals();

        for (JsonNode t : sourceTransitions) {
            if (!t.isObject() || !t.hasNonNull("fromStateName") || !t.hasNonNull("toStateName")) {
                throw new IllegalArgumentException("自定义状态转移必须包含 fromStateName 和 toStateName");
            }
            String stateSpace = t.path("stateSpace").asText("OP");
            if (!"CMD".equals(stateSpace) && !"OP".equals(stateSpace)) {
                throw new IllegalArgumentException("无效的 stateSpace: " + stateSpace);
            }

            JsonNode trigger = t.get("trigger");
            ObjectNode transition = JsonNodeSupport.objectNode();
            transition.put("description", t.path("description").asText(""));
            transition.put("stateSpace", stateSpace);
            if (t.hasNonNull("regionName")) {
                transition.put("regionName", t.path("regionName").asText());
            }
            transition.put("fromStateName", t.path("fromStateName").asText());
            transition.put("toStateName", t.path("toStateName").asText());

            if (trigger == null || trigger.isNull() || trigger.isMissingNode()) {
                if (!"CMD".equals(stateSpace)) {
                    throw new IllegalArgumentException("非 CMD 状态空间的转移必须有触发信号 (trigger)");
                }
                transition.putNull("trigger");
            } else {
                String signalName = trigger.path("signalName").asText("");
                if (signalName.isBlank()) {
                    throw new IllegalArgumentException("触发信号名称不能为空");
                }
                if (standardTriggerSignals.contains(signalName)) {
                    continue;
                }
                ObjectNode newTrigger = JsonNodeSupport.objectNode();
                newTrigger.put("interfaceName", trigger.path("interfaceName").asText("Interface_adapter_in"));
                newTrigger.put("signalName", signalName);
                transition.set("trigger", newTrigger);
            }

            JsonNode actions = t.get("actions");
            transition.set("actions",
                    actions != null && actions.isArray() ? actions.deepCopy() : JsonNodeSupport.arrayNode());

            targetTransitions.add(transition);
        }
    }

    public void delete(String id) {
        Long modelId = parseId(id);
        Long count = deviceInstancesMapper.selectCount(
                Wrappers.<DeviceInstances>lambdaQuery().eq(DeviceInstances::getDeviceModelId, modelId));
        if (count != null && count > 0) {
            throw new IllegalStateException("该模型下仍有 " + count + " 台设备实例，无法删除");
        }
        mapper.deleteById(modelId);
    }

    private void validateModelAdapterContract(DeviceModels model) {
        validateCapabilityModelIdentifiers(model);
        JsonNode contract = model.getAdapterContract();
        if (contract == null || contract.isNull() || contract.isMissingNode()) {
            return;
        }

        Map<String, String> modelAttrTypes = new HashMap<>();
        for (JsonNode attr : iterable(model.getAttributes())) {
            String name = attr.path("name").asText("");
            if (!name.isBlank()) {
                modelAttrTypes.put(name, normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }

        Map<String, String> adapterAttrTypes = new HashMap<>();
        for (JsonNode attr : iterable(contract.path("telemetry").path("adapterAttributes"))) {
            String name = attr.path("name").asText("");
            if (!name.isBlank()) {
                adapterAttrTypes.put(name, normalizeDataType(attr.path("dataType").asText("STRING")));
            }
        }

        for (JsonNode mapping : iterable(contract.path("telemetry").path("attributesMapping"))) {
            String adapterAttr = mapping.path("adapterAttrName").asText("");
            String modelAttr = mapping.path("modelAttributeName").asText("");
            if (adapterAttr.isBlank() || modelAttr.isBlank()) {
                continue;
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
            String capabilityName = capability.path("name").asText("");
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

        stateMachineInterfacePolicyService.validateTransitionSemantics(
                model.getStateTransitions(),
                model.getCmdState(),
                model.getOpState(),
                contract.path("events")
        );
    }

    private void validateCapabilityModelIdentifiers(DeviceModels model) {
        ensureUniqueNames(model.getAttributes(), "属性");
        ensureUniqueNames(model.getCapabilities(), "操作");
        for (JsonNode capability : iterable(model.getCapabilities())) {
            String capabilityName = capability.path("name").asText("");
            ensureUniqueNames(capability.path("parameters"), "操作 " + capabilityName + " 的参数");
        }
    }

    private void ensureUniqueNames(JsonNode rows, String label) {
        Set<String> names = new HashSet<>();
        for (JsonNode row : iterable(rows)) {
            String name = row.path("name").asText("");
            if (name.isBlank()) {
                throw new IllegalArgumentException(label + "标识符不能为空");
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
            return adapterIndexService.buildAdapterContract(adapterName, categoryName);
        }
        return inputContract;
    }
}