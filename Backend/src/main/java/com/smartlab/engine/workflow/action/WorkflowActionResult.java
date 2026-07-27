package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import java.time.Instant;

/** 工作流动作结果包含变量更新、等待状态以及EMIT产生的接口信号 */
public record WorkflowActionResult(WorkflowActionStatus status, ObjectNode variableUpdates,
                                   Instant resumeAt, String messageId,
                                   String emittedInterfaceName, String emittedSignalName) {
    public WorkflowActionResult {
        if (status == null) throw new IllegalArgumentException("动作结果缺少status");
        variableUpdates = variableUpdates == null ? JsonNodeSupport.objectNode() : variableUpdates;
    }

    public static WorkflowActionResult continueExecution() {
        return new WorkflowActionResult(WorkflowActionStatus.CONTINUE, JsonNodeSupport.objectNode(), null, null, null, null);
    }

    public static WorkflowActionResult continueWith(ObjectNode updates) {
        return new WorkflowActionResult(WorkflowActionStatus.CONTINUE, updates, null, null, null, null);
    }

    public static WorkflowActionResult emitWorkflowSignal(String interfaceName, String signalName) {
        return new WorkflowActionResult(WorkflowActionStatus.CONTINUE, JsonNodeSupport.objectNode(), null, null, interfaceName, signalName);
    }

    public static WorkflowActionResult suspendUntil(Instant deadline) {
        return new WorkflowActionResult(WorkflowActionStatus.SUSPEND_UNTIL, JsonNodeSupport.objectNode(), deadline, null, null, null);
    }

    public static WorkflowActionResult waitDeviceIdle(String messageId) {
        return new WorkflowActionResult(WorkflowActionStatus.WAIT_DEVICE_IDLE, JsonNodeSupport.objectNode(), null, messageId, null, null);
    }

    public static WorkflowActionResult awaitExternalSignal(String messageId) {
        return new WorkflowActionResult(WorkflowActionStatus.AWAIT_EXTERNAL_SIGNAL, JsonNodeSupport.objectNode(), null, messageId, null, null);
    }
}
