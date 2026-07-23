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
    void requiresExactUniqueSchemaCoverage() {
        StateMachineActionExecutor send = executor("SEND");
        StateMachineActionRegistry registry = new StateMachineActionRegistry(List.of(send), Set.of("SEND"));
        assertSame(send, registry.required("SEND"));
        assertThrows(IllegalStateException.class,
                () -> new StateMachineActionRegistry(List.of(), Set.of("SEND")));
        assertThrows(IllegalStateException.class,
                () -> new StateMachineActionRegistry(List.of(send, send), Set.of("SEND")));
        assertThrows(IllegalStateException.class,
                () -> new StateMachineActionRegistry(List.of(send, executor("OTHER")), Set.of("SEND")));
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
