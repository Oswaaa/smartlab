package com.smartlab.engine.statemachine;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单个设备实例的可变状态机运行时。状态机定义由设备模型共享，执行状态按设备实例隔离。
 */
final class DeviceStateMachineRuntime {

    private final long deviceInstanceId;
    private final ReentrantLock transitionLock = new ReentrantLock();
    private final ReentrantLock exceptionLock = new ReentrantLock();
    private CommandExecution normalExecution;
    private CommandExecution terminationExecution;

    DeviceStateMachineRuntime(long deviceInstanceId) {
        this.deviceInstanceId = deviceInstanceId;
    }

    long deviceInstanceId() {
        return deviceInstanceId;
    }

    ReentrantLock lock() {
        return transitionLock;
    }

    ReentrantLock exceptionLock() {
        return exceptionLock;
    }

    CommandExecution normalExecution() {
        return normalExecution;
    }

    void normalExecution(CommandExecution execution) {
        normalExecution = execution;
    }

    CommandExecution terminationExecution() {
        return terminationExecution;
    }

    void terminationExecution(CommandExecution execution) {
        terminationExecution = execution;
    }

    CommandExecution findExecution(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        if (normalExecution != null) {
            if (messageId.equals(normalExecution.messageId())) {
                return normalExecution;
            }
            CommandExecution attached = normalExecution.attachedAbortExecution();
            if (attached != null && messageId.equals(attached.messageId())) {
                return attached;
            }
        }
        if (terminationExecution != null && messageId.equals(terminationExecution.messageId())) {
            return terminationExecution;
        }
        return null;
    }

    void removeExecution(CommandExecution execution) {
        if (execution == null) {
            return;
        }
        if (execution == normalExecution) {
            normalExecution = null;
            return;
        }
        if (normalExecution != null && execution == normalExecution.attachedAbortExecution()) {
            normalExecution.attachedAbortExecution(null);
            return;
        }
        if (execution == terminationExecution) {
            terminationExecution = null;
        }
    }

    enum ExecutionRole {
        NORMAL,
        ATTACHED_ABORT,
        TERMINATION
    }

    static final class CommandExecution {

        private final String messageId;
        private final String capabilityName;
        private final String adapterCommandName;
        private final Map<String, Object> context;
        private final ExecutionRole role;
        private final Set<String> affectedMessageIds;
        private final Instant startedAt;
        private String state;
        private CommandExecution attachedAbortExecution;

        CommandExecution(String messageId,
                         String capabilityName,
                         String adapterCommandName,
                         String state,
                         Map<String, Object> context,
                         ExecutionRole role,
                         Set<String> affectedMessageIds) {
            this.messageId = messageId;
            this.capabilityName = capabilityName;
            this.adapterCommandName = adapterCommandName;
            this.state = state;
            this.context = context == null ? Map.of() : Map.copyOf(context);
            this.role = role;
            this.startedAt = Instant.now();
            this.affectedMessageIds = affectedMessageIds == null
                    ? Set.of()
                    : Set.copyOf(new LinkedHashSet<>(affectedMessageIds));
        }

        Instant startedAt() {
            return startedAt;
        }

        String messageId() {
            return messageId;
        }

        String capabilityName() {
            return capabilityName;
        }

        String adapterCommandName() {
            return adapterCommandName;
        }

        String state() {
            return state;
        }

        void state(String state) {
            this.state = state;
        }

        Map<String, Object> context() {
            return context;
        }

        ExecutionRole role() {
            return role;
        }

        Set<String> affectedMessageIds() {
            return affectedMessageIds;
        }

        CommandExecution attachedAbortExecution() {
            return attachedAbortExecution;
        }

        void attachedAbortExecution(CommandExecution execution) {
            attachedAbortExecution = execution;
        }
    }
}
