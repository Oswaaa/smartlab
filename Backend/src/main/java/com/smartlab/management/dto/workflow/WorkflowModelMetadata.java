package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 工作流模型文件的 metadata 段，对齐 schemas/工作流模型.json。 */
@Data
public class WorkflowModelMetadata {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Long flowModelId;
    @NotBlank
    private String flowModelName;
    private String description;
}
