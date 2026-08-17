package com.smartlab.global.event;

import com.smartlab.management.entity.workflow.ExecutionLog;

/**
 * 工作流执行日志广播事件。
 */
public record TaskExecutionLogEvent(
        ExecutionLog log
) {
}
