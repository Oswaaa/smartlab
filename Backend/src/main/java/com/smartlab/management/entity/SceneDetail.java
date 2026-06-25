package com.smartlab.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"SCENE_DETAIL\"", autoResultMap = false)
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
    private LocalDateTime createTime;

}

