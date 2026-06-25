package com.smartlab.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"PROPERTY_TYPE\"", autoResultMap = false)
public class PropertyType {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("type_name")
    private String typeName;

    @TableField("db_type")
    private String dbType;

    @TableField("description")
    private String description;

    @TableField("create_time")
    private LocalDateTime createTime;

}

