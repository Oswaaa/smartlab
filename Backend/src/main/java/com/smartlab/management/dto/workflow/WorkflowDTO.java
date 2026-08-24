package com.smartlab.management.dto.workflow;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
/**
 * Workflow业务数据传输载体对象（DTO）。
 */
public class WorkflowDTO {

    private Long flowModelId;
    private String flowModelName;
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> interfaceConnections;
    private List<Map<String, Object>> portConnections;
}

