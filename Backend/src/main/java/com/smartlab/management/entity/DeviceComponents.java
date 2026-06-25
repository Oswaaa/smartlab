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
@TableName(value = "\"DEVICE_COMPONENTS\"", autoResultMap = true)
public class DeviceComponents {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("component_name")
    private String componentName;

    @TableField("category_id")
    private Long categoryId;

    @TableField("parent_instance_id")
    private Long parentInstanceId;

    @TableField("self_instance_id")
    private Long selfInstanceId;

    @TableField("status")
    private String status;

    @TableField(value = "specification", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode specification;

    @TableField("predecessor_id")
    private Long predecessorId;

    @TableField("install_time")
    private LocalDateTime installTime;

    @TableField("create_time")
    private LocalDateTime createTime;

}

