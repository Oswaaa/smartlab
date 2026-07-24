package com.smartlab.engine.statemachine.action;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 定稿状态机模型仅声明SEND动作，注册器只负责保证该动作有唯一执行器 */
@Component
public class StateMachineActionRegistry {

    private static final String SEND = "SEND";
    private final Map<String, StateMachineActionExecutor> executors;

    public StateMachineActionRegistry(List<StateMachineActionExecutor> executors) {
        Map<String, StateMachineActionExecutor> registered = new LinkedHashMap<>();
        for (StateMachineActionExecutor executor : executors) {
            if (registered.putIfAbsent(executor.actionName(), executor) != null) {
                throw new IllegalStateException("状态机动作执行器重复: " + executor.actionName());
            }
        }
        if (!registered.keySet().equals(java.util.Set.of(SEND))) {
            throw new IllegalStateException("状态机动作执行器必须且只能实现SEND，实际=" + registered.keySet());
        }
        this.executors = Map.copyOf(registered);
    }

    public StateMachineActionExecutor required(String actionName) {
        StateMachineActionExecutor executor = executors.get(actionName);
        if (executor == null) {
            throw new IllegalArgumentException("状态机动作没有执行器: " + actionName);
        }
        return executor;
    }
}