package com.smartlab.engine.workflow.action;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WaitWorkflowActionExecutor implements WorkflowActionExecutor {
    public String actionName() { return "WAIT"; }

    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        long durationMs = action.payload().path("durationMs").asLong(-1);
        if (durationMs < 0) throw new IllegalArgumentException("WAIT.durationMs 必须是非负整数");
        if (context.step().getStartTime() == null) throw new IllegalStateException("WAIT 执行前步骤必须已开始");
        Instant deadline = context.step().getStartTime().toInstant().plusMillis(durationMs);
        return context.now().isBefore(deadline)
                ? WorkflowActionResult.suspendUntil(deadline)
                : WorkflowActionResult.continueExecution();
    }
}
