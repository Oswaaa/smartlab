package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class WorkflowDetailResponse {
    private Long id;
    private String name;
    private String description;
    private Integer version;
    private String status;
    private JsonNode nodeIdRefs;
    private JsonNode nodesDef;
    private JsonNode interfaceConnections;
    private JsonNode portConnections;
    private Long creatorId;
    private OffsetDateTime createTime;
}
