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
public class ConstraintRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("rule_name")
    private String ruleName;

    @TableField("expression")
    private String expression;

    @TableField(value = "bindings", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode bindings;

    @TableField("window_seconds")
    private Integer windowSeconds;

    @TableField(value = "violation_actions", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode violationActions;

    @TableField("description")
    private String description;

    @TableField("is_enabled")
    private Boolean isEnabled;

    @TableField("create_time")
    private OffsetDateTime createTime;
}
