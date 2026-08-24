package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/** 设计器/详情读取：模型文件字段 + 库中的运营信息。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkflowViewResponse extends WorkflowModelDocument {
    private Integer version;
    private String status;
    private Long predecessorId;
    private JsonNode nodeIdRefs;
    private Long creatorId;
    private OffsetDateTime createTime;
}
