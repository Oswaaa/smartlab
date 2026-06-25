package com.smartlab.management.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DATA_TEMPLATE_DETAIL\"", autoResultMap = false)
public class DataTemplateDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("data_template_id")
    private Long dataTemplateId;

    @TableField("column_name")
    private String columnName;

    @TableField("column_desc")
    private String columnDesc;

    @TableField("property_type_id")
    private Long propertyTypeId;

    @TableField("column_length")
    private Integer columnLength;

    @TableField("device_attr_key")
    private String deviceAttrKey;

    @TableField("default_value")
    private String defaultValue;

    @TableField("create_time")
    private LocalDateTime createTime;

}

