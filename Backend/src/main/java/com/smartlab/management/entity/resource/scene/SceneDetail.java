package com.smartlab.management.entity.resource.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"SCENE_DETAIL\"", autoResultMap = false)
/**
 * 场景明细控制指令实体。对应 SCENE_DETAILS 表，保存场景一键启动时关联每个设备实例应发出的标准目标指令。
 */
public class SceneDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private Long sceneId;

    @TableField("device_instance_id")
    private Long deviceInstanceId;

    @TableField("pos_x")
    private Double posX;

    @TableField("pos_y")
    private Double posY;

    @TableField("pos_z")
    private Double posZ;

    @TableField("create_time")
    private OffsetDateTime createTime;

}

