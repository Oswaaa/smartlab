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
@TableName(value = "\"DEVICE_COMPONENTS\"", autoResultMap = true)
/**
 * 设备内部组装部件实体。对应 DEVICE_COMPONENTS 表，表征复杂仪器的内部拓扑硬件清单。
 */
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

    @TableField("remark")
    private String remark;

    @TableField(value = "specification", typeHandler = PostgresJsonbTypeHandler.class)
    private JsonNode specification;

    @TableField("predecessor_id")
    private Long predecessorId;

    @TableField("install_time")
    private OffsetDateTime installTime;

    @TableField("create_time")
    private OffsetDateTime createTime;

}

