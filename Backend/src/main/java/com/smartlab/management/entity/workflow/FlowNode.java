package com.smartlab.management.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"FLOW_NODE\"", autoResultMap = true)
/**
 * 工作流节点配置实体。对应 FLOW_NODE 表，保存设计画布上单个行为节点的逻辑判定属性。
 */
public class FlowNode {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_model_id")
    private Long flowModelId;

    @TableField("node_id_ref")
    private Long nodeIdRef;

    @TableField("node_type")
    private String nodeType;

    @TableField("sub_flow_model_id")
    private Long subFlowModelId;

    @TableField("device_model_id")
    private Long deviceModelId;

    @TableField(value = "capability", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode capability;

    @TableField(value = "in_variables", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode inVariables;

    @TableField(value = "lifecycle", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode lifecycle;

    @TableField(value = "interfaces", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode interfaces;

    @TableField(value = "ports", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode ports;

    @TableField(value = "actions", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode actions;

    @TableField("create_time")
    private OffsetDateTime createTime;

}

