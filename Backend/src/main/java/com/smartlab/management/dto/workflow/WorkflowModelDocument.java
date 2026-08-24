package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工作流模型文件合同，对齐 {@code schemas/工作流模型.json}。
 * 保存、编译、拆表都直接使用这份对象，不再转成另一套 name/nodesDef DTO。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowModelDocument {
    @NotNull
    @Valid
    private WorkflowModelMetadata metadata = new WorkflowModelMetadata();
    private JsonNode nodes;
    private JsonNode interfaceConnections;
    private JsonNode portConnections;

    @JsonIgnore
    public Long flowModelId() {
        return metadata == null ? null : metadata.getFlowModelId();
    }

    @JsonIgnore
    public String flowModelName() {
        return metadata == null ? null : metadata.getFlowModelName();
    }

    @JsonIgnore
    public String descriptionText() {
        return metadata == null ? null : metadata.getDescription();
    }

    public void flowModelId(Long id) {
        ensureMetadata().setFlowModelId(id);
    }

    public void flowModelName(String name) {
        ensureMetadata().setFlowModelName(name);
    }

    public void descriptionText(String description) {
        ensureMetadata().setDescription(description);
    }

    private WorkflowModelMetadata ensureMetadata() {
        if (metadata == null) metadata = new WorkflowModelMetadata();
        return metadata;
    }
}
