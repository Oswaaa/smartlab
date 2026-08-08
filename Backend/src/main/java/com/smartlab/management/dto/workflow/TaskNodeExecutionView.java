package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;

public record TaskNodeExecutionView(
        Long stepId,
        Long flowModelId,
        Integer flowVersion,
        long nodeIdRef,
        String occurrencePath,
        String nodeName,
        String nodeType,
        String status,
        String deviceName,
        String capabilityName,
        JsonNode inputs,
        JsonNode outputs,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Long durationMs,
        List<WorkflowIssue> issues
) {}
