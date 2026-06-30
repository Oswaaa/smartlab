package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.mapper.common.PostgresJsonbTypeHandler;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_TWIN_STATES\"", autoResultMap = true)
/**
 * 设备孪生实时状态快照实体。对应 DEVICE_TWIN_STATES 表，高速缓存最新的遥测值属性与双状态机当前状态。
 */
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
    private OffsetDateTime lastOnlineTime;

    @TableField("update_time")
    private OffsetDateTime updateTime;

    public String getCurrentCommandState() {
        return currentCmdState;
    }

    public String getCurrentOperationState() {
        return currentOpState;
    }

    public JsonNode getLatestAttributes() {
        return currentAttr;
    }

}

