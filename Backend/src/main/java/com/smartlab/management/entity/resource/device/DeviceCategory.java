package com.smartlab.management.entity.resource.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DEVICE_CATEGORY\"", autoResultMap = false)
/**
 * 设备基础品类定义实体。对应 DEVICE_CATEGORIES 表，划定硬件的分类大纲。
 */
public class DeviceCategory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("category_name")
    private String categoryName;

    @TableField("parent_category_id")
    private Long parentCategoryId;

    @TableField("description")
    private String description;

    @TableField("create_time")
    private LocalDateTime createTime;

}

