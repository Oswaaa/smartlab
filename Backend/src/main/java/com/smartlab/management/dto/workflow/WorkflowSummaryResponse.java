package com.smartlab.management.dto.workflow;

import lombok.Data;

/** 流程列表项。名称与模型文件一致使用 flowModelName。 */
@Data
public class WorkflowSummaryResponse {
    private Long id;
    private String flowModelName;
    private String description;
    private Integer version;
    private String status;
    private Long predecessorId;
}
