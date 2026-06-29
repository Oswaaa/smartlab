package com.smartlab.management.entity.resource.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DATA_INDEX\"", autoResultMap = false)
/**
 * 数据采集指标配置实体。对应 DATA_INDEX 表，持久化管理每个采集点位的量纲和属性。
 */
public class DataIndex {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("data_template_id")
    private Long dataTemplateId;

    @TableField("data_table")
    private String dataTable;

    @TableField("data_desc")
    private String dataDesc;

    @TableField("device_instance_id")
    private Long deviceInstanceId;

    @TableField("create_time")
    private LocalDateTime createTime;

}

