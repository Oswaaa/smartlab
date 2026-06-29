package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"TASK_STEP\"", autoResultMap = true)
/**
 * 任务节点执行明细实体。对应 TASK_STEPS 表，记录特定任务中每个被实例化执行节点的实时调度属性。
 */
public class TaskStep {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("flow_node_id")
    private Long flowNodeId;

    @TableField("node_id_ref")
    private Long nodeIdRef;

    @TableField("parent_step_id")
    private Long parentStepId;

    @TableField("step_depth")
    private Integer stepDepth;

    @TableField("node_status")
    private String nodeStatus;

    @TableField(value = "interface_in_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaceInSnapshot;

    @TableField(value = "interface_out_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaceOutSnapshot;

    @TableField(value = "port_in_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode portInSnapshot;

    @TableField(value = "port_out_snapshot", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode portOutSnapshot;

    @TableField(value = "variable_space", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode variableSpace;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("duration_ms")
    private Long durationMs;

}

