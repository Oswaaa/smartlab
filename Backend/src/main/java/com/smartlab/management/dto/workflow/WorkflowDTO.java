package com.smartlab.management.dto.workflow;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
/**
 * Workflow业务数据传输载体对象（DTO）。
 */
public class WorkflowDTO {

    private Long id;
    private String flowName;
    private List<Map<String, Object>> nodesDef;
    private List<Map<String, Object>> interfaceConnections;
    private List<Map<String, Object>> portConnections;
}

