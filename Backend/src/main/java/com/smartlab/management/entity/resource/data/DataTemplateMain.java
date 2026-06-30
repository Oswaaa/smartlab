package com.smartlab.management.entity.resource.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName(value = "\"DATA_TEMPLATE_MAIN\"", autoResultMap = false)
/**
 * 数据采集模板主表实体。对应 DATA_TEMPLATE_MAIN 表，存储数据模板基本描述与拥有者信息。
 */
public class DataTemplateMain {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    public String getTemplateId() {
        return id == null ? null : String.valueOf(id);
    }

    @JsonSetter("templateId")
    public void setTemplateId(String templateId) {
        if (templateId != null && !templateId.isBlank()) {
            this.id = Long.valueOf(templateId);
        }
    }

    @TableField("template_name")
    private String templateName;

    @TableField("template_desc")
    private String templateDesc;

    @TableField("device_model_id")
    private Long deviceModelId;

    @TableField("is_default")
    private Boolean isDefault;

    @TableField("create_time")
    private OffsetDateTime createTime;

}

