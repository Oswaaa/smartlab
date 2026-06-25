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
@TableName(value = "\"DEVICE_INSTANCES\"", autoResultMap = true)
public class DeviceInstances {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public String getInstanceId() {
        return id == null ? null : String.valueOf(id);
    }

    @JsonSetter("instanceId")
    public void setInstanceId(String instanceId) {
        if (instanceId != null && !instanceId.isBlank()) {
            this.id = Long.valueOf(instanceId);
        }
    }

    @TableField("device_model_id")
    private Long deviceModelId;

    public String getModelId() {
        return deviceModelId == null ? null : String.valueOf(deviceModelId);
    }

    @JsonSetter("modelId")
    public void setModelId(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            this.deviceModelId = Long.valueOf(modelId);
        }
    }

    @TableField("instance_name")
    private String instanceName;

    @TableField(value = "instance_config", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode instanceConfig;

    @TableField("bound_adapter_name")
    private String boundAdapterName;

    @TableField("bound_device_point")
    private String boundDevicePoint;

    @TableField("picture")
    private String picture;

    @TableField("create_time")
    private LocalDateTime createTime;

}

