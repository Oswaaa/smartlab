package com.smartlab.global.contract;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemExecutionContractTest {

    @Test
    void resolvesSystemTransitionByCurrentStateInterfaceAndSignal() {
        var transition = SystemExecutionContract.findStateMachineSystemTransition(
                "CMD", "IDLE", "Interface_workflow_in", "WF_EXECUTE_START");

        assertTrue(transition.isPresent());
        assertEquals("SENT", transition.orElseThrow().toStateName());
        assertEquals("Interface_adapter_out", transition.orElseThrow().actionInterfaceName());
        assertEquals("CMD_START", transition.orElseThrow().actionSignalName());
        assertTrue(SystemExecutionContract.isCommandStartSignal("WF_EXECUTE_START"));
        assertTrue(SystemExecutionContract.isCommandAbortSignal("CONSTRAINT_ABORT"));
        assertEquals(List.of("COMPLETED", "FAILED", "ABORTED"),
                SystemExecutionContract.terminalCommandStateNames());
    }

    @Test
    void rejectsSameSignalOnWrongInterfaceOrWrongCurrentState() {
        assertTrue(SystemExecutionContract.findStateMachineSystemTransition(
                "CMD", "RUNNING", "Interface_workflow_in", "WF_EXECUTE_START").isEmpty());
        assertTrue(SystemExecutionContract.findStateMachineSystemTransition(
                "CMD", "IDLE", "Interface_control_in", "WF_EXECUTE_START").isEmpty());
    }

    @Test
    void exposesEveryRuntimeOwnedCommandTransitionToModelEditors() {
        List<SystemExecutionContract.SystemTransitionDefinition> transitions =
                SystemExecutionContract.stateMachineSystemTransitions();

        assertEquals(9, transitions.size());
        assertEquals(3, transitions.stream()
                .filter(row -> "IDLE".equals(row.fromStateName()) && "SENT".equals(row.toStateName()))
                .count());
        assertEquals(6, transitions.stream()
                .filter(row -> List.of("SENT", "RUNNING").contains(row.fromStateName())
                        && "ABORTING".equals(row.toStateName()))
                .count());
    }

    @Test
    void exposesOnlyTheFourAdapterDrivenCommandTransitionRequirements() {
        assertEquals(
                List.of(
                        "MAIN:SENT:RUNNING",
                        "MAIN:RUNNING:COMPLETED",
                        "FAILURE:RUNNING:FAILED",
                        "TERMINATION:ABORTING:ABORTED"
                ),
                SystemExecutionContract.deviceCommandTransitionRequirements().stream()
                        .map(row -> row.kind() + ":" + row.fromStateName() + ":" + row.toStateName())
                        .toList()
        );
    }
}
