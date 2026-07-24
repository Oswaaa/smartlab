package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskCreateRequest {
    @NotBlank
    private String taskName;
    private String taskDesc;
    @NotNull
    private Long flowModelId;
    private Long parentTaskId;
    private JsonNode taskVariables;
    private Long creatorId;
}
