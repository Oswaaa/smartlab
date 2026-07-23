package com.smartlab.engine.workflow.action;

import com.smartlab.global.schema.SchemaMetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowActionRegistry {
    private final Map<String, WorkflowActionExecutor> executors;

    @Autowired
    public WorkflowActionRegistry(List<WorkflowActionExecutor> executors, SchemaMetadataService metadata) {
        this(executors, Set.copyOf(metadata.workflowActionNames()));
    }

    public WorkflowActionRegistry(List<WorkflowActionExecutor> executors, Set<String> declaredNames) {
        Map<String, WorkflowActionExecutor> registered = new LinkedHashMap<>();
        for (WorkflowActionExecutor executor : executors) {
            String name = executor.actionName();
            if (name == null || name.isBlank()) throw new IllegalStateException("工作流动作执行器缺少名称");
            if (registered.putIfAbsent(name, executor) != null)
                throw new IllegalStateException("工作流动作执行器重复: " + name);
        }
        if (!registered.keySet().equals(declaredNames)) {
            Set<String> missing = new java.util.LinkedHashSet<>(declaredNames);
            missing.removeAll(registered.keySet());
            Set<String> extra = new java.util.LinkedHashSet<>(registered.keySet());
            extra.removeAll(declaredNames);
            throw new IllegalStateException("工作流动作执行器与 Schema 不一致，缺失=" + missing + "，多余=" + extra);
        }
        this.executors = Map.copyOf(registered);
    }

    public WorkflowActionExecutor required(String actionName) {
        WorkflowActionExecutor executor = executors.get(actionName);
        if (executor == null) throw new IllegalArgumentException("工作流动作没有执行器: " + actionName);
        return executor;
    }

    public Set<String> names() { return executors.keySet(); }
}
