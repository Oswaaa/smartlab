package com.smartlab.engine.workflow.action;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowActionRegistry {
    private static final Set<String> REQUIRED = Set.of("EMIT", "UPDATE");
    private final Map<String, WorkflowActionExecutor> executors;

    public WorkflowActionRegistry(List<WorkflowActionExecutor> candidates) {
        Map<String, WorkflowActionExecutor> result = new LinkedHashMap<>();
        for (WorkflowActionExecutor candidate : candidates) {
            if (REQUIRED.contains(candidate.actionName()) && result.putIfAbsent(candidate.actionName(), candidate) != null) {
                throw new IllegalStateException("工作流动作执行器重复: " + candidate.actionName());
            }
        }
        if (!result.keySet().equals(REQUIRED)) throw new IllegalStateException("工作流动作执行器必须实现EMIT和UPDATE");
        this.executors = Map.copyOf(result);
    }

    public WorkflowActionExecutor required(String actionName) {
        WorkflowActionExecutor executor = executors.get(actionName);
        if (executor == null) throw new IllegalArgumentException("工作流动作没有执行器: " + actionName);
        return executor;
    }

    public Set<String> names() { return executors.keySet(); }
}