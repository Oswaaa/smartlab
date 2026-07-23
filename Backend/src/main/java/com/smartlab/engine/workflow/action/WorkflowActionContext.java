package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowExecutionOperations;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.entity.workflow.Task;
import com.smartlab.management.entity.workflow.TaskStep;

import java.time.Instant;

public record WorkflowActionContext(Task task, TaskStep step, FlowNode node, ObjectNode variables,
                                    Instant now, WorkflowExecutionOperations operations) {
    public WorkflowActionContext {
        if (task == null || step == null || node == null || variables == null || now == null)
            throw new IllegalArgumentException("工作流动作上下文不完整");
    }
}
