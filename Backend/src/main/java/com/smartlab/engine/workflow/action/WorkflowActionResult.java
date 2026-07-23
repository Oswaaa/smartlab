package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;

import java.time.Instant;

public record WorkflowActionResult(WorkflowActionStatus status, ObjectNode variableUpdates,
                                   Instant resumeAt, String messageId) {
    public WorkflowActionResult {
        if (status == null) throw new IllegalArgumentException("动作结果缺少 status");
        variableUpdates = variableUpdates == null ? JsonNodeSupport.objectNode() : variableUpdates;
    }

    public static WorkflowActionResult continueExecution() {
        return new WorkflowActionResult(WorkflowActionStatus.CONTINUE, JsonNodeSupport.objectNode(), null, null);
    }

    public static WorkflowActionResult continueWith(ObjectNode updates) {
        return new WorkflowActionResult(WorkflowActionStatus.CONTINUE, updates, null, null);
    }

    public static WorkflowActionResult suspendUntil(Instant deadline) {
        return new WorkflowActionResult(WorkflowActionStatus.SUSPEND_UNTIL, JsonNodeSupport.objectNode(), deadline, null);
    }

    public static WorkflowActionResult awaitExternalSignal(String messageId) {
        return new WorkflowActionResult(WorkflowActionStatus.AWAIT_EXTERNAL_SIGNAL, JsonNodeSupport.objectNode(), null, messageId);
    }
}
