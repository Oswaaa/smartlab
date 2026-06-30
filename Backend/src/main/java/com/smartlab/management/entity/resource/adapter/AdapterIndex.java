package com.smartlab.management.entity.resource.adapter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "\"ADAPTER_INDEX\"", autoResultMap = true)
/**
 * 适配器网关注册实体类。对应 ADAPTER_INDEX 表，持久化存储物理接入通道的静态连接属性与模版关联。
 */
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
    private OffsetDateTime lastHeartbeat;

    @TableField("create_time")
    private OffsetDateTime createTime;

    @TableField("update_time")
    private OffsetDateTime updateTime;
}
