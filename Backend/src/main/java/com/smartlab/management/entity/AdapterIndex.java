package com.smartlab.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.PostgresJsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "\"ADAPTER_INDEX\"", autoResultMap = true)
public class AdapterIndex {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("adapter_name")
    private String adapterName;

    @TableField("original_config")
    private String originalConfig;

    @TableField(value = "parsed_config", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode parsedConfig;

    @TableField("status")
    private String status;

    @TableField("last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
