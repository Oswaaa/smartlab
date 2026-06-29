package com.smartlab.management.entity.resource.scene;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"SCENE_MAIN\"", autoResultMap = false)
/**
 * 场景模板主表实体。对应 SCENE_MAIN 表，存储复合设备协同场景的方案名称与执行状态。
 */
public class SceneMain {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("scene_name")
    private String sceneName;

    @TableField("description")
    private String description;

    @TableField("scene_picture")
    private String scenePicture;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("create_time")
    private LocalDateTime createTime;

}

