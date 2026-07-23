package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowSaveRequest {
    private Long id;
    @NotBlank
    private String name;
    private String description;
    private Integer version;
    private String status;
    private Long creatorId;
    private JsonNode nodesDef;
    private JsonNode interfaceConnections;
    private JsonNode portConnections;
}
