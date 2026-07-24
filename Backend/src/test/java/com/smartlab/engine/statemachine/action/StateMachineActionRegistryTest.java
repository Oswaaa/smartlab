package com.smartlab.engine.statemachine.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineModels;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StateMachineActionRegistryTest {
    @Test
    void requiresExactlyOneSendExecutor() {
        StateMachineActionExecutor send = executor("SEND");
        StateMachineActionRegistry registry = new StateMachineActionRegistry(List.of(send));
        assertSame(send, registry.required("SEND"));
        assertThrows(IllegalStateException.class,
                () -> new StateMachineActionRegistry(List.of()));
        assertThrows(IllegalStateException.class,
                () -> new StateMachineActionRegistry(List.of(send, send)));
        assertThrows(IllegalStateException.class,
                () -> new StateMachineActionRegistry(List.of(send, executor("OTHER"))));
    }

    private StateMachineActionExecutor executor(String name) {
        return new StateMachineActionExecutor() {
            public String actionName() { return name; }
            public ObjectNode execute(StateMachineModels.ActionDefinition action, StateMachineModels.EventContext context) {
                return null;
            }
        };
    }
}
