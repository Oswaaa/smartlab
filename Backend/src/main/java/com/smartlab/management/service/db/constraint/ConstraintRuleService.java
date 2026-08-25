package com.smartlab.management.service.db.constraint;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.contract.DataType;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.contract.SystemViolationAction;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.engine.constraint.ConstraintExpressionEvaluator;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.constraint.ViolationLog;
import com.smartlab.management.mapper.constraint.ConstraintRuleMapper;
import com.smartlab.management.mapper.constraint.ViolationLogMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import com.smartlab.global.event.ConstraintRulesChangedEvent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConstraintRuleService extends ManagementCrudService<ConstraintRule> {

    private static final Set<String> OBSERVABLE_SOURCE_TYPES = enumNames(ObservableObjectType.values());
    private static final Set<String> DATA_TYPES = enumNames(DataType.values());
    private static final Set<String> SYSTEM_ACTIONS = enumNames(SystemViolationAction.values());

    private final ConstraintRuleMapper mapper;
    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceModelsMapper deviceModelsMapper;
    private final ConstraintExpressionEvaluator expressionEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final ViolationLogMapper violationLogMapper;

    @Autowired
    public ConstraintRuleService(ConstraintRuleMapper mapper, DeviceInstancesMapper deviceInstancesMapper,
                                 ConstraintExpressionEvaluator expressionEvaluator,
                                 ApplicationEventPublisher eventPublisher,
                                 ViolationLogMapper violationLogMapper,
                                 DeviceModelsMapper deviceModelsMapper) {
        super(mapper);
        this.mapper = mapper;
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceModelsMapper = deviceModelsMapper;
        this.expressionEvaluator = expressionEvaluator;
        this.eventPublisher = eventPublisher;
        this.violationLogMapper = violationLogMapper;
    }

    public ConstraintRuleService(ConstraintRuleMapper mapper, DeviceInstancesMapper deviceInstancesMapper,
                                 ConstraintExpressionEvaluator expressionEvaluator,
                                 ApplicationEventPublisher eventPublisher,
                                 ViolationLogMapper violationLogMapper) {
        this(mapper, deviceInstancesMapper, expressionEvaluator, eventPublisher, violationLogMapper, null);
    }

    public ConstraintRuleService(ConstraintRuleMapper mapper, DeviceInstancesMapper deviceInstancesMapper,
                                 ConstraintExpressionEvaluator expressionEvaluator) {
        this(mapper, deviceInstancesMapper, expressionEvaluator, event -> { }, null, null);
    }

    public List<ConstraintRule> list(Boolean isEnabled) {
        QueryWrapper<ConstraintRule> query = new QueryWrapper<>();
        if (isEnabled != null) query.eq("is_enabled", isEnabled);
        query.orderByDesc("create_time", "id");
        return mapper.selectList(query);
    }

    public PageResult<ConstraintRule> page(long pageNo, long pageSize, String keyword, Boolean isEnabled) {
        QueryWrapper<ConstraintRule> query = new QueryWrapper<>();
        if (hasText(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like("rule_name", value).or().like("description", value));
        }
        if (isEnabled != null) query.eq("is_enabled", isEnabled);
        query.orderByDesc("create_time", "id");
        Page<ConstraintRule> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public ConstraintRule setEnabled(Long id, boolean enabled) {
        ConstraintRule rule = getById(id);
        if (rule == null) throw new IllegalArgumentException("约束规则不存在");
        rule.setIsEnabled(enabled);
        mapper.updateById(rule);
        eventPublisher.publishEvent(new ConstraintRulesChangedEvent(id));
        return rule;
    }

    public Map<String, Object> options() {
        return Map.of(
                "observableSourceTypes", OBSERVABLE_SOURCE_TYPES,
                "dataTypes", DATA_TYPES,
                "bindingTypes", List.of("OBSERVABLE", "LITERAL"),
                "systemViolationActions", SYSTEM_ACTIONS
        );
    }

    @Override
    public ConstraintRule save(ConstraintRule entity) {
        normalize(entity);
        validate(entity, false);
        ConstraintRule saved = super.save(entity);
        eventPublisher.publishEvent(new ConstraintRulesChangedEvent(saved.getId()));
        return saved;
    }

    @Override
    public void delete(java.io.Serializable id) {
        Long ruleId = id instanceof Long value ? value : id == null ? null : Long.valueOf(String.valueOf(id));
        if (violationLogMapper != null && ruleId != null) {
            Long count = violationLogMapper.selectCount(
                    new QueryWrapper<ViolationLog>().eq("constraint_rule_id", ruleId));
            if (count != null && count > 0) {
                throw new IllegalStateException("该约束规则存在违规审计记录（共 " + count + " 条），禁止删除，仅支持停用");
            }
        }
        super.delete(id);
        eventPublisher.publishEvent(new ConstraintRulesChangedEvent(ruleId));
    }

    private void normalize(ConstraintRule entity) {
        if (entity == null) throw new IllegalArgumentException("约束规则不能为空");
        entity.setRuleName(trim(entity.getRuleName()));
        entity.setExpression(trim(entity.getExpression()));
        entity.setDescription(trim(entity.getDescription()));
        if (entity.getIsEnabled() == null) entity.setIsEnabled(Boolean.TRUE);
    }

    public void validateTaskDefinition(ConstraintRule entity) {
        normalize(entity);
        validate(entity, true);
    }

    private void validate(ConstraintRule entity, boolean taskScoped) {
        requireText(entity.getRuleName(), "约束名称不能为空");
        requireText(entity.getExpression(), "expression不能为空");
        requireObject(entity.getBindings(), "bindings");
        validateBindings(entity.getBindings());
        if (!taskScoped) validateEvaluationScope(entity.getBindings());
        expressionEvaluator.validate(entity.getExpression(), sampleVariables(entity.getBindings()));
        if (entity.getWindowSeconds() != null && entity.getWindowSeconds() <= 0) {
            throw new IllegalArgumentException("windowSeconds必须大于0或null");
        }
        requireArray(entity.getViolationActions(), "violationActions");
        for (int index = 0; index < entity.getViolationActions().size(); index++) {
            validateViolationAction(entity.getViolationActions().get(index), entity.getBindings(), taskScoped,
                    "violationActions[" + index + "]");
        }
    }

    private void validateBindings(JsonNode bindings) {
        var fields = bindings.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!hasText(entry.getKey())) throw new IllegalArgumentException("bindings变量名不能为空");
            String scope = "bindings." + entry.getKey();
            JsonNode binding = entry.getValue();
            requireObject(binding, scope);
            String bindingType = requireText(binding, "bindingType", scope);
            if ("OBSERVABLE".equals(bindingType)) {
                validateSource(binding.get("source"), scope + ".source");
            } else if ("LITERAL".equals(bindingType)) {
                JsonNode value = binding.get("value");
                if (value == null || value.isNull() || !(value.isNumber() || value.isTextual() || value.isBoolean())) {
                    throw new IllegalArgumentException(scope + ".value必须是number、string或boolean");
                }
            } else {
                throw new IllegalArgumentException(scope + ".bindingType必须是OBSERVABLE或LITERAL");
            }
        }
    }

    private Map<String, JsonNode> sampleVariables(JsonNode bindings) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        var fields = bindings.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode binding = entry.getValue();
            if ("LITERAL".equals(text(binding, "bindingType"))) {
                result.put(entry.getKey(), binding.path("value").deepCopy());
                continue;
            }
            String dataType = binding.path("source").path("dataType").asText();
            JsonNode sample = switch (DataType.valueOf(dataType)) {
                case INTEGER -> JsonNodeSupport.MAPPER.getNodeFactory().numberNode(1);
                case DOUBLE -> JsonNodeSupport.MAPPER.getNodeFactory().numberNode(1.5D);
                case STRING -> JsonNodeSupport.MAPPER.getNodeFactory().textNode("sample");
                case BOOLEAN -> JsonNodeSupport.MAPPER.getNodeFactory().booleanNode(true);
                case JSON -> JsonNodeSupport.objectNode();
            };
            result.put(entry.getKey(), sample);
        }
        return result;
    }

    /** 最终约束模型没有跨设备与任务的关联键，保存时必须拒绝无法确定求值作用域的绑定组合 */
    private void validateEvaluationScope(JsonNode bindings) {
        Set<Long> deviceModelIds = new java.util.HashSet<>();
        Set<Long> explicitDeviceInstanceIds = new java.util.HashSet<>();
        Set<Long> workflowTemplateIds = new java.util.HashSet<>();
        boolean hasDeviceSource = false;
        boolean hasTaskScopedSource = false;
        boolean hasDeviceWildcard = false;
        Set<Long> explicitTaskIds = new java.util.HashSet<>();
        boolean hasTaskWildcard = false;
        var fields = bindings.fields();
        while (fields.hasNext()) {
            JsonNode binding = fields.next().getValue();
            if (!"OBSERVABLE".equals(text(binding, "bindingType"))) continue;
            JsonNode source = binding.path("source");
            String sourceType = text(source, "sourceType");
            if (Set.of("DEVICE_ATTRIBUTE", "DEVICE_OPERATION_STATE", "DEVICE_COMMAND_LIFECYCLE").contains(sourceType)) {
                hasDeviceSource = true;
                deviceModelIds.add(requirePositiveLong(source, "deviceModelId", "bindings.source"));
                Long instanceId = optionalLong(source.get("deviceInstanceId"));
                if (instanceId == null) hasDeviceWildcard = true;
                else explicitDeviceInstanceIds.add(instanceId);
            } else if (Set.of("NODE_LIFECYCLE_STATE", "NODE_INTERNAL_VARIABLE", "TASK_LIFECYCLE_STATE").contains(sourceType)) {
                hasTaskScopedSource = true;
                if (source.has("workflowTemplateId") && !source.path("workflowTemplateId").isNull()) {
                    workflowTemplateIds.add(requirePositiveLong(source, "workflowTemplateId", "bindings.source"));
                }
                if ("TASK_LIFECYCLE_STATE".equals(sourceType)) {
                    Long taskId = optionalLong(source.get("taskId"));
                    if (taskId == null) hasTaskWildcard = true;
                    else explicitTaskIds.add(taskId);
                }
            }
        }
        if (hasDeviceSource && hasTaskScopedSource) {
            throw new IllegalArgumentException("bindings不能混合设备数据源与任务或节点数据源；最终模型没有声明两者的关联作用域");
        }
        if (deviceModelIds.size() > 1) {
            throw new IllegalArgumentException("同一约束规则的设备数据源必须引用同一deviceModelId");
        }
        if (explicitDeviceInstanceIds.size() > 1 || (hasDeviceWildcard && !explicitDeviceInstanceIds.isEmpty())) {
            throw new IllegalArgumentException("同一约束规则的设备数据源必须使用同一明确deviceInstanceId，或全部不指定deviceInstanceId");
        }
        if (workflowTemplateIds.size() > 1) {
            throw new IllegalArgumentException("同一约束规则的节点数据源必须引用同一workflowTemplateId");
        }
        if (explicitTaskIds.size() > 1 || (hasTaskWildcard && !explicitTaskIds.isEmpty())) {
            throw new IllegalArgumentException("同一约束规则的任务数据源必须使用同一明确taskId，或全部不指定taskId");
        }
    }

    private Long optionalLong(JsonNode value) {
        return value != null && !value.isNull() && value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
    }
    private void validateSource(JsonNode source, String scope) {
        requireObject(source, scope);
        String sourceType = requireText(source, "sourceType", scope);
        if (!OBSERVABLE_SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException(scope + ".sourceType不符合约束模型规范: " + sourceType);
        }
        String dataType = requireText(source, "dataType", scope);
        if (!DATA_TYPES.contains(dataType)) {
            throw new IllegalArgumentException(scope + ".dataType不符合协议规范: " + dataType);
        }
        switch (sourceType) {
            case "DEVICE_ATTRIBUTE" -> {
                requirePositiveLong(source, "deviceModelId", scope);
                requireText(source, "targetName", scope);
                optionalPositiveLong(source, "deviceInstanceId", scope);
            }
            case "DEVICE_OPERATION_STATE" -> {
                requirePositiveLong(source, "deviceModelId", scope);
                requireText(source, "regionName", scope);
                optionalPositiveLong(source, "deviceInstanceId", scope);
            }
            case "DEVICE_COMMAND_LIFECYCLE" -> {
                requirePositiveLong(source, "deviceModelId", scope);
                optionalPositiveLong(source, "deviceInstanceId", scope);
            }
            case "NODE_LIFECYCLE_STATE" -> {
                requirePositiveLong(source, "workflowTemplateId", scope);
                requireText(source, "nodeName", scope);
            }
            case "NODE_INTERNAL_VARIABLE" -> {
                requirePositiveLong(source, "workflowTemplateId", scope);
                requireText(source, "nodeName", scope);
                requireText(source, "variableName", scope);
            }
            case "TASK_LIFECYCLE_STATE" -> {
                requirePositiveLong(source, "workflowTemplateId", scope);
                optionalPositiveLong(source, "taskId", scope);
            }
            default -> throw new IllegalArgumentException("未知可观测对象类型: " + sourceType);
        }
    }

    private void validateViolationAction(JsonNode action, JsonNode bindings, boolean taskScoped, String scope) {
        requireObject(action, scope);
        String actionType = requireText(action, "actionType", scope);
        if ("SYSTEM".equals(actionType)) {
            String systemAction = requireText(action, "action", scope);
            if (!SYSTEM_ACTIONS.contains(systemAction)) {
                throw new IllegalArgumentException(scope + ".action不符合约束模型规范: " + systemAction);
            }
            JsonNode targetTaskId = action.get("targetTaskId");
            boolean hasTargetTask = targetTaskId != null && !targetTaskId.isNull()
                    && targetTaskId.canConvertToLong() && targetTaskId.asLong() > 0;
            if (!"ALERT".equals(systemAction) && !hasTargetTask
                    && (taskScoped || !taskObservablesAreWildcard(bindings))) {
                throw new IllegalArgumentException(scope + ".targetTaskId必须是正整数；仅当任务或节点观测为全工作流实例时允许省略，执行时与触发任务同源");
            }
            return;
        }
        if (!"DEVICE_CAPABILITY".equals(actionType)) {
            throw new IllegalArgumentException(scope + ".actionType必须是SYSTEM或DEVICE_CAPABILITY");
        }
        String capabilityName = requireText(action, "capabilityName", scope);
        long actionModelId = requirePositiveLong(action, "deviceModelId", scope);
        Long deviceInstanceId = optionalLong(action.get("deviceInstanceId"));
        if (deviceInstanceId != null) {
            DeviceInstances instance = deviceInstancesMapper.selectById(deviceInstanceId);
            if (instance == null) throw new IllegalArgumentException(scope + "引用的设备实例不存在");
            if (!DeviceInstanceLifecycle.isUsable(instance)) throw new IllegalStateException(scope + "引用的设备实例已注销");
            if (!Long.valueOf(actionModelId).equals(instance.getDeviceModelId())) {
                throw new IllegalArgumentException(scope + ".deviceModelId必须与目标设备实例的模型一致");
            }
        } else if (taskScoped || !deviceObservablesAreWildcard(bindings)) {
            throw new IllegalArgumentException(scope + ".deviceInstanceId必须指定；仅当设备观测为全模型实例时允许省略，执行时与触发实例同源");
        }
        JsonNode parameters = action.get("parameters");
        if (parameters != null && !parameters.isNull() && !parameters.isObject()) {
            throw new IllegalArgumentException(scope + ".parameters必须是对象");
        }
        validateCapabilityParameters(actionModelId, capabilityName, parameters, scope);
    }

    private void validateCapabilityParameters(long deviceModelId, String capabilityName, JsonNode parameters, String scope) {
        if (deviceModelsMapper == null) return;
        DeviceModels model = deviceModelsMapper.selectById(deviceModelId);
        if (model == null) return;
        JsonNode capability = findCapability(model.getCapabilities(), capabilityName);
        if (capability == null) {
            throw new IllegalArgumentException(scope + "引用的设备能力不存在: " + capabilityName);
        }
        JsonNode defined = capability.path("parameters");
        if (!defined.isArray() || defined.isEmpty()) return;
        JsonNode values = parameters == null || parameters.isNull() ? JsonNodeSupport.objectNode() : parameters;
        for (JsonNode definition : defined) {
            String name = definition.path("name").asText("");
            if (name.isBlank()) continue;
            JsonNode value = values.get(name);
            String display = definition.path("displayName").asText(name);
            if (isBlankCapabilityParameter(value)) {
                throw new IllegalArgumentException(scope + ".parameters." + name + "不能为空（" + display + "）");
            }
            String dataType = definition.path("dataType").asText("STRING");
            if (!matchesCapabilityParameterType(dataType, value)) {
                throw new IllegalArgumentException(scope + ".parameters." + name + "类型不正确，要求" + dataType);
            }
        }
    }

    private JsonNode findCapability(JsonNode capabilities, String capabilityName) {
        if (capabilities == null || !capabilities.isArray() || capabilityName == null || capabilityName.isBlank()) return null;
        for (JsonNode capability : capabilities) {
            if (capabilityName.equals(capability.path("capabilityName").asText())) return capability;
        }
        return null;
    }

    private static boolean isBlankCapabilityParameter(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull()
                || (value.isTextual() && value.asText().isBlank());
    }

    private static boolean matchesCapabilityParameterType(String dataType, JsonNode value) {
        if (value == null || value.isNull() || dataType == null || dataType.isBlank()) return false;
        return switch (dataType) {
            case "INTEGER" -> value.isIntegralNumber();
            case "DOUBLE" -> value.isNumber();
            case "STRING" -> value.isTextual();
            case "BOOLEAN" -> value.isBoolean();
            case "JSON" -> value.isObject() || value.isArray();
            default -> false;
        };
    }

    /** 所有设备观测都未指定实例时，动作可以省略实例，运行时绑定到触发该规则的 twin。 */
    private boolean deviceObservablesAreWildcard(JsonNode bindings) {
        if (bindings == null || !bindings.isObject()) return false;
        boolean hasDeviceSource = false;
        var fields = bindings.fields();
        while (fields.hasNext()) {
            JsonNode binding = fields.next().getValue();
            if (!"OBSERVABLE".equals(text(binding, "bindingType"))) continue;
            JsonNode source = binding.path("source");
            String sourceType = text(source, "sourceType");
            if (!Set.of("DEVICE_ATTRIBUTE", "DEVICE_OPERATION_STATE", "DEVICE_COMMAND_LIFECYCLE").contains(sourceType)) {
                continue;
            }
            hasDeviceSource = true;
            if (optionalLong(source.get("deviceInstanceId")) != null) return false;
        }
        return hasDeviceSource;
    }

    /** 所有任务/节点观测都未指定 taskId 时，系统动作可以省略目标任务，运行时绑定到触发该规则的任务。 */
    private boolean taskObservablesAreWildcard(JsonNode bindings) {
        if (bindings == null || !bindings.isObject()) return false;
        boolean hasTaskSource = false;
        var fields = bindings.fields();
        while (fields.hasNext()) {
            JsonNode binding = fields.next().getValue();
            if (!"OBSERVABLE".equals(text(binding, "bindingType"))) continue;
            JsonNode source = binding.path("source");
            String sourceType = text(source, "sourceType");
            if (!Set.of("NODE_LIFECYCLE_STATE", "NODE_INTERNAL_VARIABLE", "TASK_LIFECYCLE_STATE").contains(sourceType)) {
                continue;
            }
            hasTaskSource = true;
            if ("TASK_LIFECYCLE_STATE".equals(sourceType) && optionalLong(source.get("taskId")) != null) return false;
        }
        return hasTaskSource;
    }

    private void requireArray(JsonNode node, String scope) {
        if (node == null || !node.isArray()) throw new IllegalArgumentException(scope + "必须是数组");
    }

    private void requireObject(JsonNode node, String scope) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(scope + "必须是对象");
    }

    private String requireText(JsonNode node, String fieldName, String scope) {
        String value = text(node, fieldName);
        if (!hasText(value)) throw new IllegalArgumentException(scope + "." + fieldName + "不能为空");
        return value.trim();
    }

    private long requirePositiveLong(JsonNode node, String fieldName, String scope) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException(scope + "." + fieldName + "必须是正整数");
        }
        return value.asLong();
    }

    private void optionalPositiveLong(JsonNode node, String fieldName, String scope) {
        JsonNode value = node.get(fieldName);
        if (value != null && !value.isNull() && (!value.canConvertToLong() || value.asLong() <= 0)) {
            throw new IllegalArgumentException(scope + "." + fieldName + "必须是正整数或null");
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) throw new IllegalArgumentException(message);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
