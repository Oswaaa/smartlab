package com.smartlab.management.service.db.constraint;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ConstraintRule;
import com.smartlab.management.mapper.constraint.ConstraintRuleMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 约束规则表服务。
 *
 * CONSTRAINT_RULE 是规则集合表，一行数据表示一条约束规则。
 * 它不直接等同于 constraint-model.json 的完整模型文件；后续导出模型时，
 * 后端可基于多行规则组装 observableObjects 与 constraints。
 */
@Service
/**
 * 设备安全联锁控制与违规检测持久层核心服务。
 */
public class ConstraintRuleService extends ManagementCrudService<ConstraintRule> {

    private static final Set<String> SOURCE_TYPES = Set.of(
            "DEVICE_ATTRIBUTE",
            "DEVICE_OPERATION_STATE",
            "DEVICE_COMMAND_LIFECYCLE",
            "NODE_LIFECYCLE_STATE"
    );

    private static final Set<String> OPERATORS = Set.of("GT", "LT", "GE", "LE", "EQ", "NE", "BETWEEN", "IN");

    private static final Set<String> SYSTEM_ACTIONS = Set.of("HALT", "PAUSE", "ALERT", "LOG_ONLY");

    private final ConstraintRuleMapper mapper;

    public ConstraintRuleService(ConstraintRuleMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public List<ConstraintRule> list(String sourceType, String objectEndpoint, Boolean isEnabled) {
        QueryWrapper<ConstraintRule> query = baseQuery(sourceType, objectEndpoint, null, null, isEnabled);
        query.orderByDesc("create_time", "id");
        return mapper.selectList(query);
    }

    public PageResult<ConstraintRule> page(long pageNo,
                                           long pageSize,
                                           String keyword,
                                           String sourceType,
                                           String objectEndpoint,
                                           String objectName,
                                           String operator,
                                           Boolean isEnabled) {
        QueryWrapper<ConstraintRule> query = baseQuery(sourceType, objectEndpoint, objectName, operator, isEnabled);
        if (hasText(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("rule_name", value)
                    .or()
                    .like("source_type", value)
                    .or()
                    .like("object_endpoint", value)
                    .or()
                    .like("object_name", value)
                    .or()
                    .like("operator", value)
                    .or()
                    .like("threshold", value)
                    .or()
                    .like("description", value));
        }
        query.orderByDesc("create_time", "id");
        Page<ConstraintRule> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public ConstraintRule setEnabled(Long id, boolean enabled) {
        ConstraintRule rule = getById(id);
        if (rule == null) {
            throw new IllegalArgumentException("约束规则不存在");
        }
        rule.setIsEnabled(enabled);
        mapper.updateById(rule);
        return rule;
    }

    public Map<String, Object> options() {
        return Map.of(
                "sourceTypes", SOURCE_TYPES,
                "operators", OPERATORS,
                "systemViolationActions", SYSTEM_ACTIONS
        );
    }

    @Override
    public ConstraintRule save(ConstraintRule entity) {
        normalize(entity);
        validate(entity);
        return super.save(entity);
    }

    private QueryWrapper<ConstraintRule> baseQuery(String sourceType,
                                                   String objectEndpoint,
                                                   String objectName,
                                                   String operator,
                                                   Boolean isEnabled) {
        QueryWrapper<ConstraintRule> query = new QueryWrapper<>();
        eqIfPresent(query, "source_type", sourceType);
        eqIfPresent(query, "object_endpoint", objectEndpoint);
        eqIfPresent(query, "object_name", objectName);
        eqIfPresent(query, "operator", operator);
        if (isEnabled != null) {
            query.eq("is_enabled", isEnabled);
        }
        return query;
    }

    private void normalize(ConstraintRule entity) {
        if (entity == null) {
            throw new IllegalArgumentException("约束规则不能为空");
        }
        entity.setRuleName(trim(entity.getRuleName()));
        entity.setSourceType(upperTrim(entity.getSourceType()));
        entity.setObjectEndpoint(trim(entity.getObjectEndpoint()));
        entity.setObjectName(trim(entity.getObjectName()));
        entity.setOperator(upperTrim(entity.getOperator()));
        entity.setThreshold(trim(entity.getThreshold()));
        entity.setDescription(trim(entity.getDescription()));
        if (entity.getIsEnabled() == null) {
            entity.setIsEnabled(Boolean.TRUE);
        }
    }

    private void validate(ConstraintRule entity) {
        requireText(entity.getRuleName(), "约束名称不能为空");
        requireText(entity.getSourceType(), "来源类型不能为空");
        requireText(entity.getObjectEndpoint(), "监控对象端点不能为空");
        requireText(entity.getObjectName(), "约束对象名称不能为空");
        requireText(entity.getOperator(), "比较符不能为空");
        requireText(entity.getThreshold(), "阈值界限不能为空");

        if (!SOURCE_TYPES.contains(entity.getSourceType())) {
            throw new IllegalArgumentException("来源类型不符合约束模型规范: " + entity.getSourceType());
        }
        if (!OPERATORS.contains(entity.getOperator())) {
            throw new IllegalArgumentException("比较符不符合协议字典规范: " + entity.getOperator());
        }
        validateViolationActions(entity.getViolationActions());
    }

    private void validateViolationActions(JsonNode actions) {
        if (actions == null || !actions.isArray() || actions.isEmpty()) {
            throw new IllegalArgumentException("违规触发动作集必须是非空数组");
        }
        for (int i = 0; i < actions.size(); i++) {
            JsonNode action = actions.get(i);
            if (action == null || !action.isObject()) {
                throw new IllegalArgumentException("违规触发动作第 " + (i + 1) + " 项必须是对象");
            }
            String actionType = text(action, "actionType");
            if (!hasText(actionType)) {
                throw new IllegalArgumentException("违规触发动作第 " + (i + 1) + " 项缺少 actionType");
            }
            if ("SYSTEM".equals(actionType)) {
                validateSystemAction(action, i);
            } else if ("DEVICE_CAPABILITY".equals(actionType)) {
                validateDeviceCapabilityAction(action, i);
            } else {
                throw new IllegalArgumentException("违规触发动作第 " + (i + 1) + " 项 actionType 不符合约束模型规范: " + actionType);
            }
        }
    }

    private void validateSystemAction(JsonNode action, int index) {
        String systemAction = text(action, "action");
        if (!hasText(systemAction)) {
            throw new IllegalArgumentException("系统违规动作第 " + (index + 1) + " 项缺少 action");
        }
        if (!SYSTEM_ACTIONS.contains(systemAction)) {
            throw new IllegalArgumentException("系统违规动作第 " + (index + 1) + " 项 action 不符合协议字典规范: " + systemAction);
        }
    }

    private void validateDeviceCapabilityAction(JsonNode action, int index) {
        JsonNode deviceInstanceId = action.get("deviceInstanceId");
        if (deviceInstanceId == null || !deviceInstanceId.canConvertToLong()) {
            throw new IllegalArgumentException("设备能力动作第 " + (index + 1) + " 项缺少有效的 deviceInstanceId");
        }
        if (!hasText(text(action, "capabilityName"))) {
            throw new IllegalArgumentException("设备能力动作第 " + (index + 1) + " 项缺少 capabilityName");
        }
        JsonNode parameters = action.get("parameters");
        if (parameters != null && !parameters.isObject()) {
            throw new IllegalArgumentException("设备能力动作第 " + (index + 1) + " 项 parameters 必须是对象");
        }
    }

    private void eqIfPresent(QueryWrapper<ConstraintRule> query, String column, String value) {
        if (hasText(value)) {
            query.eq(column, value.trim());
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String upperTrim(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}