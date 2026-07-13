package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"EXECUTION_LOG\"", autoResultMap = false)
/**
 * 统一执行日志实体。对应 EXECUTION_LOG 表，记录任务、手动控制、约束与系统来源的执行日志。
 */
public class ExecutionLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("source_type")
    private String sourceType;

    @TableField("task_id")
    private Long taskId;

    @TableField("device_instance_id")
    private Long deviceInstanceId;

    @TableField("task_step_id")
    private Long taskStepId;

    @TableField("log_level")
    private String logLevel;

    @TableField("log_info")
    private String logInfo;

    @TableField("log_time")
    private OffsetDateTime logTime;

}

