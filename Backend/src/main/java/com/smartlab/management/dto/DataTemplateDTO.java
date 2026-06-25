package com.smartlab.management.dto;

import lombok.Data;
import java.util.Map;

@Data
public class DataTemplateDTO {

    private String templateId;
    private String templateName;
    private Map<String, Object> dataSchemaSpec;
    private String description;
}

