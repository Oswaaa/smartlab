package com.smartlab.management.dto.workflow;

import java.util.List;

/**
 * 草稿/发布接口的对外响应。definition 是模型文件；version/status 来自持久化结果。
 */
public record WorkflowPreparationApiResponse(
        WorkflowModelDocument definition,
        Integer version,
        String status,
        Long predecessorId,
        List<WorkflowIssue> issues,
        boolean executable,
        boolean published) {
}
