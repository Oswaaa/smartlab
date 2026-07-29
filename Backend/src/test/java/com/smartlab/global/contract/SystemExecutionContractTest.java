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

        assertEquals(List.of(
                "CMD|IDLE|SENT|Interface_workflow_in|WF_EXECUTE_START|Interface_adapter_out|CMD_START",
                "CMD|IDLE|SENT|Interface_control_in|MANUAL_EXECUTE_START|Interface_adapter_out|CMD_START",
                "CMD|IDLE|SENT|Interface_constraint_in|CONSTRAINT_EXECUTE|Interface_adapter_out|CMD_START",
                "CMD|SENT|ABORTING|Interface_workflow_in|WF_EXECUTE_ABORT|Interface_adapter_out|CMD_ABORT",
                "CMD|RUNNING|ABORTING|Interface_workflow_in|WF_EXECUTE_ABORT|Interface_adapter_out|CMD_ABORT",
                "CMD|SENT|ABORTING|Interface_control_in|MANUAL_EXECUTE_ABORT|Interface_adapter_out|CMD_ABORT",
                "CMD|RUNNING|ABORTING|Interface_control_in|MANUAL_EXECUTE_ABORT|Interface_adapter_out|CMD_ABORT",
                "CMD|SENT|ABORTING|Interface_constraint_in|CONSTRAINT_ABORT|Interface_adapter_out|CMD_ABORT",
                "CMD|RUNNING|ABORTING|Interface_constraint_in|CONSTRAINT_ABORT|Interface_adapter_out|CMD_ABORT"
        ), transitions.stream().map(row -> String.join("|",
                row.stateSpace(),
                row.fromStateName(),
                row.toStateName(),
                row.triggerInterfaceName(),
                row.triggerSignalName(),
                row.actionInterfaceName(),
                row.actionSignalName())).toList());
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
