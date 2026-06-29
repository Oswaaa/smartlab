package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"STEP_LOG\"", autoResultMap = false)
/**
 * 工作流节点步骤执行历史日志实体。对应 STEP_LOGS 表，保存特定工作流任务中单个节点执行成败与返回报文。
 */
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

