package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.PageResult;
import com.smartlab.management.entity.DeviceCategory;
import com.smartlab.management.entity.DeviceInstances;
import com.smartlab.management.entity.DeviceModels;
import com.smartlab.management.mapper.DeviceInstancesMapper;
import com.smartlab.management.mapper.DeviceModelsMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 设备模型表服务。
 * 对应 DEVICE_MODELS 表，只负责模型基础资料和 JSON 字段的保存读取。
 */
@Service
public class DeviceModelService extends ManagementCrudService<DeviceModels> {

    private final DeviceModelsMapper mapper;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceCategoryService deviceCategoryService;

    public DeviceModelService(DeviceModelsMapper mapper,
                              DeviceInstancesMapper deviceInstancesMapper,
                              DeviceCategoryService deviceCategoryService) {
        super(mapper);
        this.mapper = mapper;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceCategoryService = deviceCategoryService;
    }

    @Override
    public List<DeviceModels> list() {
        return mapper.selectList(Wrappers.<DeviceModels>lambdaQuery().orderByDesc(DeviceModels::getId));
    }

    public PageResult<DeviceModels> page(long pageNo, long pageSize, String keyword) {
        return super.page(pageNo, pageSize, keyword, "model_name");
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
        model.setAdapterContract(adapterContract);
        model.setUpdateTime(LocalDateTime.now());
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
    public String saveStateMachine(Map<String, Object> payload) {
        String modelId = Objects.toString(payload.getOrDefault("deviceModelRef", payload.get("modelId")), "");
        DeviceModels model = getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在，无法保存状态机");
        }
        model.setStateMachineInterfaces(JsonNodeSupport.toNode(payload.get("interfacesDef")));
        model.setCmdState(JsonNodeSupport.toNode(payload.get("commandLifecycleDef")));
        model.setOpState(JsonNodeSupport.toNode(payload.get("operationStateDef")));
        Object opState = payload.get("operationStateDef");
        if (opState instanceof Map<?, ?> opMap && opMap.containsKey("transitions")) {
            model.setStateTransitions(JsonNodeSupport.toNode(opMap.get("transitions")));
        }
        model.setUpdateTime(LocalDateTime.now());
        mapper.updateById(model);
        return Objects.toString(payload.getOrDefault("stateMachineId", model.getId() + "StateMachine"));
    }

    /**
     * 清空设备模型中的状态机相关 JSON 字段。
     */
    public void deleteStateMachine(String id) {
        String modelId = id.endsWith("StateMachine") ? id.substring(0, id.length() - "StateMachine".length()) : id;
        DeviceModels model = getById(modelId);
        if (model == null) {
            throw new IllegalArgumentException("设备模型不存在");
        }
        model.setStateMachineInterfaces(null);
        model.setCmdState(null);
        model.setOpState(null);
        model.setStateTransitions(null);
        model.setUpdateTime(LocalDateTime.now());
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
                        "active", true
                ));
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
        model.setUpdateTime(LocalDateTime.now());
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
        model.setUpdateTime(LocalDateTime.now());
        mapper.updateById(model);
    }

    public DeviceModels savePayload(Map<String, Object> payload) {
        DeviceModels model = new DeviceModels();
        Object id = first(payload, "id", "modelId");
        if (id != null && !String.valueOf(id).isBlank()) {
            model.setId(Long.valueOf(String.valueOf(id)));
        }
        model.setModelName(stringValue(first(payload, "modelName", "name")));
        Object categoryId = first(payload, "categoryId");
        if (categoryId != null && !String.valueOf(categoryId).isBlank()) {
            model.setCategoryId(Long.valueOf(String.valueOf(categoryId)));
        } else {
            DeviceCategory category = deviceCategoryService.findOrCreateByName(stringValue(first(payload, "deviceCategory", "categoryName")));
            if (category != null) {
                model.setCategoryId(category.getId());
            }
        }

        Object capabilitySpec = first(payload, "capabilitySpec");
        if (capabilitySpec instanceof Map<?, ?> spec) {
            model.setAttributes(JsonNodeSupport.toNode(spec.get("attributes")));
            model.setCapabilities(JsonNodeSupport.toNode(firstFromMap(spec, "capabilities", "functions")));
            model.setAdapterContract(JsonNodeSupport.toNode(spec.get("adapterContract")));
            model.setPorts(JsonNodeSupport.toNode(spec.get("ports")));
            JsonNode metadata = JsonNodeSupport.toNode(spec.get("metadataExtras"));
            if (metadata != null && model.getComponentsBom() == null && metadata.has("componentsBom")) {
                model.setComponentsBom(metadata.get("componentsBom"));
            }
        }

        putIfPresent(payload, "attributes", model::setAttributes);
        putIfPresent(payload, "capabilities", model::setCapabilities);
        putIfPresent(payload, "adapterContract", model::setAdapterContract);
        putIfPresent(payload, "ports", model::setPorts);
        putIfPresent(payload, "intrinsicConstraint", model::setIntrinsicConstraint);
        putIfPresent(payload, "intrinsicConstraints", model::setIntrinsicConstraint);
        putIfPresent(payload, "stateMachineInterfaces", model::setStateMachineInterfaces);
        putIfPresent(payload, "opState", model::setOpState);
        putIfPresent(payload, "cmdState", model::setCmdState);
        putIfPresent(payload, "stateTransitions", model::setStateTransitions);
        putIfPresent(payload, "componentsBom", model::setComponentsBom);

        LocalDateTime now = LocalDateTime.now();
        if (model.getId() == null) {
            model.setCreateTime(now);
        }
        model.setUpdateTime(now);

        if (model.getId() == null) {
            mapper.insert(model);
        } else {
            mapper.updateById(model);
        }
        return model;
    }

    public void delete(String id) {
        Long modelId = parseId(id);
        Long count = deviceInstancesMapper.selectCount(
                Wrappers.<DeviceInstances>lambdaQuery().eq(DeviceInstances::getDeviceModelId, modelId)
        );
        if (count != null && count > 0) {
            throw new IllegalStateException("该模型下仍有 " + count + " 台设备实例，无法删除");
        }
        mapper.deleteById(modelId);
    }

    private ObjectNode toStateMachineView(DeviceModels model) {
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("stateMachineId", model.getId() + "StateMachine");
        node.put("deviceModelRef", String.valueOf(model.getId()));
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

    private Object first(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private Object firstFromMap(Map<?, ?> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void putIfPresent(Map<String, Object> payload, String key, java.util.function.Consumer<JsonNode> setter) {
        if (payload.containsKey(key)) {
            setter.accept(JsonNodeSupport.toNode(payload.get(key)));
        }
    }
}


