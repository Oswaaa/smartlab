package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "\"FLOW_MODELS\"", autoResultMap = true)
public class FlowModels {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("flow_name")
    private String flowName;
    @TableField("version")
    private Integer version;
    @TableField("predecessor_id")
    private Long predecessorId;
    @TableField("status")
    private String status;
    @TableField("description")
    private String description;
    @TableField(value = "nodes", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode nodes;
    @TableField(value = "interface_connection", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaceConnection;
    @TableField(value = "port_connection", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode portConnection;
    @TableField("creator_id")
    private Long creatorId;
    @TableField("create_time")
    private OffsetDateTime createTime;
}
