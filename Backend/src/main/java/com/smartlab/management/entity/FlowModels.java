package com.smartlab.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.PostgresJsonbTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"FLOW_MODELS\"", autoResultMap = true)
public class FlowModels {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public String getTemplateId() {
        return id == null ? null : String.valueOf(id);
    }

    @JsonSetter("templateId")
    public void setTemplateId(String templateId) {
        if (templateId != null && !templateId.isBlank()) {
            this.id = Long.valueOf(templateId);
        }
    }

    @TableField("flow_name")
    private String flowName;

    public String getTemplateName() {
        return flowName;
    }

    @JsonSetter("templateName")
    public void setTemplateName(String templateName) {
        this.flowName = templateName;
    }

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

    public JsonNode getNodesDef() {
        return nodes;
    }

    @JsonSetter("nodesDef")
    public void setNodesDef(JsonNode nodesDef) {
        this.nodes = nodesDef;
    }

    @TableField(value = "interface_connection", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaceConnection;

    public JsonNode getInterfaceConnections() {
        return interfaceConnection;
    }

    @JsonSetter("interfaceConnections")
    public void setInterfaceConnections(JsonNode interfaceConnections) {
        this.interfaceConnection = interfaceConnections;
    }

    @TableField(value = "port_connection", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode portConnection;

    public JsonNode getPortConnections() {
        return portConnection;
    }

    @JsonSetter("portConnections")
    public void setPortConnections(JsonNode portConnections) {
        this.portConnection = portConnections;
    }

    @TableField("creator_id")
    private Long creatorId;

    @TableField("create_time")
    private LocalDateTime createTime;

}

