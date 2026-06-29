package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"TASK\"", autoResultMap = true)
/**
 * 实验流程执行任务主实体。对应 TASKS 表，持久化存储后台跑的协同控制链生命周期状态。
 */
public class Task {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public Long getTaskId() {
        return id;
    }

    @JsonSetter("taskId")
    public void setTaskId(Long taskId) {
        this.id = taskId;
    }

    @TableField("flow_model_id")
    private Long flowModelId;

    public String getTemplateId() {
        return flowModelId == null ? null : String.valueOf(flowModelId);
    }

    @JsonSetter("templateId")
    public void setTemplateId(String templateId) {
        if (templateId != null && !templateId.isBlank()) {
            this.flowModelId = Long.valueOf(templateId);
        }
    }

    @TableField("task_name")
    private String taskName;

    @TableField("task_desc")
    private String taskDesc;

    @TableField("parent_task_id")
    private Long parentTaskId;

    @TableField("task_status")
    private String taskStatus;

    public String getCurrentStatus() {
        return taskStatus;
    }

    @JsonSetter("currentStatus")
    public void setCurrentStatus(String currentStatus) {
        this.taskStatus = currentStatus;
    }

    @TableField(value = "task_constraints", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode taskConstraints;

    @TableField("current_flow_node_id")
    private Long currentFlowNodeId;

    @TableField("current_node_id_ref")
    private Long currentNodeIdRef;

    public JsonNode getGlobalConstraints() {
        return taskConstraints;
    }

    @JsonSetter("globalConstraints")
    public void setGlobalConstraints(JsonNode globalConstraints) {
        this.taskConstraints = globalConstraints;
    }

    @TableField(value = "resource_map", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode resourceMap;

    @TableField(value = "task_variables", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode taskVariables;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

}

