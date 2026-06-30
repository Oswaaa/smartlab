package com.smartlab.management.dto.workflow;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
/**
 * Task业务数据传输载体对象（DTO）。
 */
public class TaskDTO {

    private Integer taskId;
    private String taskName;
    private String templateId;
    private Map<String, Object> globalConstraints;
    private String currentStatus;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}

