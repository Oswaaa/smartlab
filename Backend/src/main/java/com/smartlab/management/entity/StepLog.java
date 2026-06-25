package com.smartlab.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"STEP_LOG\"", autoResultMap = false)
public class StepLog {

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
    private LocalDateTime logTime;

}

