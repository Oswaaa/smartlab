package com.smartlab.management.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TaskDTO {

    private Integer taskId;
    private String taskName;
    private String templateId;
    private Map<String, Object> globalConstraints;
    private String currentStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

