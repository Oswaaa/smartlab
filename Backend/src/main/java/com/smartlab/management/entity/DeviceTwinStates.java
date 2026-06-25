package com.smartlab.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.PostgresJsonbTypeHandler;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_TWIN_STATES\"", autoResultMap = true)
public class DeviceTwinStates {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("instance_id")
    private Long instanceId;

    @TableField("current_op_state")
    private String currentOpState;

    @TableField("current_cmd_state")
    private String currentCmdState;

    @TableField(value = "current_attr", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode currentAttr;

    @TableField("online_status")
    private String onlineStatus;

    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

}

