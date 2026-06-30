package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"RESOURCE_STRUCTURE\"", autoResultMap = false)
/**
 * 实验室资源拓扑节点实体。对应 RESOURCE_STRUCTURE 表，构建实验室空间拓扑树。
 */
public class ResourceStructure {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("connection_name")
    private String connectionName;

    @TableField("connection_type")
    private String connectionType;

    @TableField("source_instance_id")
    private Long sourceInstanceId;

    @TableField("source_port_ref")
    private String sourcePortRef;

    @TableField("target_instance_id")
    private Long targetInstanceId;

    @TableField("target_port_ref")
    private String targetPortRef;

    @TableField("description")
    private String description;

    @TableField("create_time")
    private OffsetDateTime createTime;

}

