package com.smartlab.management.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WorkflowDTO {

    private String templateId;
    private String templateName;
    private List<Map<String, Object>> nodesDef;
    private List<Map<String, Object>> interfaceConnections;
    private List<Map<String, Object>> portConnections;
}

