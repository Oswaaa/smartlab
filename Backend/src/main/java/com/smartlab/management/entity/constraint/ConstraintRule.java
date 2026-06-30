package com.smartlab.management.entity.constraint;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"CONSTRAINT_RULE\"", autoResultMap = true)
/**
 * 安全联锁与物理制约规则实体类。对应 CONSTRAINT_RULES 表，定义设备状态或数据指标异常时的联锁触发逻辑。
 */
public class ConstraintRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("rule_name")
    private String ruleName;

    @TableField("source_type")
    private String sourceType;

    @TableField("object_endpoint")
    private String objectEndpoint;

    /**
     * User-defined observable object name. This maps to observableObjects[].name
     * when rules are assembled into a constraint model file.
     */
    @TableField("object_name")
    private String objectName;

    @TableField("operator")
    private String operator;

    @TableField("threshold")
    private String threshold;

    @TableField(value = "violation_actions", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode violationActions;

    @TableField("description")
    private String description;

    @TableField("is_enabled")
    private Boolean isEnabled;

    @TableField("create_time")
    private OffsetDateTime createTime;

}