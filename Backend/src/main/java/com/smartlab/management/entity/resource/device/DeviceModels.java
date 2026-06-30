package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_MODELS\"", autoResultMap = true)
/**
 * 设备物模型核心定义实体。对应 DEVICE_MODELS 表，保存属性、服务及双状态机空间的大JSON配置定义。
 */
public class DeviceModels {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public String getModelId() {
        return id == null ? null : String.valueOf(id);
    }

    @JsonSetter("modelId")
    public void setModelId(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            this.id = Long.valueOf(modelId);
        }
    }

    @TableField("model_name")
    private String modelName;

    @TableField("category_id")
    private Long categoryId;

    @TableField(value = "attributes", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode attributes;

    @TableField(value = "capabilities", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode capabilities;

    @TableField(value = "adapter_contract", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode adapterContract;

    @TableField(value = "ports", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode ports;

    @JsonAlias("intrinsicConstraints")
    @TableField(value = "intrinsic_constraint", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode intrinsicConstraint;

    @TableField(value = "state_machine_interfaces", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode stateMachineInterfaces;

    @TableField(value = "op_state", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode opState;

    @TableField(value = "cmd_state", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode cmdState;

    @TableField(value = "state_transitions", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode stateTransitions;

    @TableField(value = "components_bom", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode componentsBom;

    @TableField("create_time")
    private OffsetDateTime createTime;

    @TableField("update_time")
    private OffsetDateTime updateTime;

    public JsonNode getIntrinsicConstraints() {
        return intrinsicConstraint;
    }

    @JsonSetter("intrinsicConstraints")
    public void setIntrinsicConstraints(JsonNode intrinsicConstraints) {
        this.intrinsicConstraint = intrinsicConstraints;
    }

}

