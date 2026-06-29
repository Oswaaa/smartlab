package com.smartlab.management.dto.resource.data;

import lombok.Data;
import java.util.Map;

@Data
/**
 * DataTemplate业务数据传输载体对象（DTO）。
 */
public class DataTemplateDTO {

    private String templateId;
    private String templateName;
    private Map<String, Object> dataSchemaSpec;
    private String description;
}

