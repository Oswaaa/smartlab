package com.smartlab.engine.workflow.action;

public interface WorkflowActionExecutor {
    String actionName();
    WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context);
}
