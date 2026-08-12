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
@TableName(value = "\"VIOLATION_LOG\"", autoResultMap = true)
/**
 * 联锁违规报警记录实体。对应 VIOLATION_LOGS 表，保存触发安全联锁动作的现场异常参数记录。
 */
public class ViolationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("constraint_rule_id")
    private Long constraintRuleId;

    @TableField("constraint_type")
    private String constraintType;

    @TableField("task_id")
    private Long taskId;

    @TableField("task_step_id")
    private Long taskStepId;

    @TableField("device_instance_id")
    private Long deviceInstanceId;

    @TableField(value = "observed_variable", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode observedVariable;

    @TableField(value = "expression", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode expression;

    @TableField(value = "actual_value", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode actualValue;

    @TableField(value = "variable_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode variableSnapshot;

    @TableField("action_taken")
    private String actionTaken;

    @TableField("violation_time")
    private OffsetDateTime violationTime;

}

