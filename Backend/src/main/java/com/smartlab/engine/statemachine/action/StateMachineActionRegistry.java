package com.smartlab.engine.statemachine.action;

import com.smartlab.global.schema.SchemaMetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StateMachineActionRegistry {
    private final Map<String, StateMachineActionExecutor> executors;

    @Autowired
    public StateMachineActionRegistry(List<StateMachineActionExecutor> executors, SchemaMetadataService metadata) {
        this(executors, Set.copyOf(metadata.stateMachineActionNames()));
    }

    public StateMachineActionRegistry(List<StateMachineActionExecutor> executors, Set<String> declaredNames) {
        Map<String, StateMachineActionExecutor> registered = new LinkedHashMap<>();
        for (StateMachineActionExecutor executor : executors) {
            if (registered.putIfAbsent(executor.actionName(), executor) != null)
                throw new IllegalStateException("状态机动作执行器重复: " + executor.actionName());
        }
        if (!registered.keySet().equals(declaredNames)) {
            Set<String> missing = new java.util.LinkedHashSet<>(declaredNames);
            missing.removeAll(registered.keySet());
            Set<String> extra = new java.util.LinkedHashSet<>(registered.keySet());
            extra.removeAll(declaredNames);
            throw new IllegalStateException("状态机动作执行器与 Schema 不一致，缺失=" + missing + "，多余=" + extra);
        }
        this.executors = Map.copyOf(registered);
    }

    public StateMachineActionExecutor required(String actionName) {
        StateMachineActionExecutor executor = executors.get(actionName);
        if (executor == null) throw new IllegalArgumentException("状态机动作没有执行器: " + actionName);
        return executor;
    }
}
