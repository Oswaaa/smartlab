package com.smartlab.engine.workflow.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Component;

@Component
public class AssignWorkflowActionExecutor implements WorkflowActionExecutor {
    private final WorkflowValueResolver resolver;

    public AssignWorkflowActionExecutor(WorkflowValueResolver resolver) { this.resolver = resolver; }
    public String actionName() { return "ASSIGN"; }

    public WorkflowActionResult execute(WorkflowActionDefinition action, WorkflowActionContext context) {
        String target = action.payload().path("target").asText("").trim();
        if (target.isBlank()) throw new IllegalArgumentException("ASSIGN 缺少 target");
        ObjectNode updates = JsonNodeSupport.objectNode();
        resolver.write(updates, target, resolver.resolve(action.payload().get("source"), context.variables()));
        return WorkflowActionResult.continueWith(updates);
    }
}
