package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "\"TASK\"", autoResultMap = true)
public class Task {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("flow_model_id")
    private Long flowModelId;
    @TableField("task_name")
    private String taskName;
    @TableField("task_desc")
    private String taskDesc;
    @TableField("parent_task_id")
    private Long parentTaskId;
    @TableField("task_status")
    private String taskStatus;
    @TableField(value = "task_constraints", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode taskConstraints;
    @TableField("current_flow_node_id")
    private Long currentFlowNodeId;
    @TableField("current_node_id_ref")
    private Long currentNodeIdRef;
    @TableField(value = "resource_map", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode resourceMap;
    @TableField(value = "task_variables", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode taskVariables;
    @TableField("creator_id")
    private Long creatorId;
    @TableField("start_time")
    private OffsetDateTime startTime;
    @TableField("end_time")
    private OffsetDateTime endTime;
}
