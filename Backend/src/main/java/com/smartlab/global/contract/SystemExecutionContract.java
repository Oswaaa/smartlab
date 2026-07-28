package com.smartlab.global.contract;

import java.util.List;
import java.util.Optional;

/** 定稿系统执行规范中所有系统拥有的固定接口与生命周期常量 */
public final class SystemExecutionContract {

    private SystemExecutionContract() {
    }

    public static List<InterfaceDefinition> stateMachineInterfaces(List<String> adapterEvents) {
        return List.of(
                new InterfaceDefinition("Interface_workflow_in", "IN", InterfaceType.WORKFLOW, names(WorkflowControlSignal.values())),
                new InterfaceDefinition("Interface_control_in", "IN", InterfaceType.CONTROL, names(ManualControlSignal.values())),
                new InterfaceDefinition("Interface_constraint_in", "IN", InterfaceType.CONSTRAINT, names(ConstraintControlSignal.values())),
                new InterfaceDefinition("Interface_adapter_in", "IN", InterfaceType.ADAPTER, adapterEvents == null ? List.of() : List.copyOf(adapterEvents)),
                new InterfaceDefinition("Interface_adapter_out", "OUT", InterfaceType.ADAPTER, names(AdapterOutboundSignal.values())),
                new InterfaceDefinition("Interface_state_out", "OUT", InterfaceType.STATE, names(StatusSignal.values())));
    }

    public static List<NodeInterfaceDefinition> workflowNodeInterfaces() {
        List<String> allNodes = names(WorkflowNodeType.values());
        return List.of(
                new NodeInterfaceDefinition(allNodes, "Interface_workflow_in", "IN", InterfaceType.WORKFLOW, names(WorkflowNodeSignal.values())),
                new NodeInterfaceDefinition(allNodes, "Interface_workflow_out", "OUT", InterfaceType.WORKFLOW, names(WorkflowNodeSignal.values())),
                new NodeInterfaceDefinition(List.of(WorkflowNodeType.DEV_NODE.name()), "Interface_state_out", "OUT", InterfaceType.STATE, names(WorkflowControlSignal.values())),
                new NodeInterfaceDefinition(List.of(WorkflowNodeType.DEV_NODE.name()), "Interface_state_in", "IN", InterfaceType.STATE, names(StatusSignal.values())));
    }

    public static List<String> commandStateNames() {
        return List.of("IDLE", "SENT", "RUNNING", "COMPLETED", "FAILED", "ABORTING", "ABORTED");
    }

    public static List<SystemTransitionDefinition> stateMachineSystemTransitions() {
        return List.of(
                systemTransition("IDLE", "SENT", "Interface_workflow_in", WorkflowControlSignal.WF_EXECUTE_START.name(), AdapterOutboundSignal.CMD_START.name()),
                systemTransition("IDLE", "SENT", "Interface_control_in", ManualControlSignal.MANUAL_EXECUTE_START.name(), AdapterOutboundSignal.CMD_START.name()),
                systemTransition("IDLE", "SENT", "Interface_constraint_in", ConstraintControlSignal.CONSTRAINT_EXECUTE.name(), AdapterOutboundSignal.CMD_START.name()),
                systemTransition("SENT", "ABORTING", "Interface_workflow_in", WorkflowControlSignal.WF_EXECUTE_ABORT.name(), AdapterOutboundSignal.CMD_ABORT.name()),
                systemTransition("RUNNING", "ABORTING", "Interface_workflow_in", WorkflowControlSignal.WF_EXECUTE_ABORT.name(), AdapterOutboundSignal.CMD_ABORT.name()),
                systemTransition("SENT", "ABORTING", "Interface_control_in", ManualControlSignal.MANUAL_EXECUTE_ABORT.name(), AdapterOutboundSignal.CMD_ABORT.name()),
                systemTransition("RUNNING", "ABORTING", "Interface_control_in", ManualControlSignal.MANUAL_EXECUTE_ABORT.name(), AdapterOutboundSignal.CMD_ABORT.name()),
                systemTransition("SENT", "ABORTING", "Interface_constraint_in", ConstraintControlSignal.CONSTRAINT_ABORT.name(), AdapterOutboundSignal.CMD_ABORT.name()),
                systemTransition("RUNNING", "ABORTING", "Interface_constraint_in", ConstraintControlSignal.CONSTRAINT_ABORT.name(), AdapterOutboundSignal.CMD_ABORT.name())
        );
    }

    public static Optional<SystemTransitionDefinition> findStateMachineSystemTransition(
            String stateSpace, String fromStateName, String triggerInterfaceName, String triggerSignalName) {
        return stateMachineSystemTransitions().stream()
                .filter(row -> row.stateSpace().equals(stateSpace))
                .filter(row -> row.fromStateName().equals(fromStateName))
                .filter(row -> row.triggerInterfaceName().equals(triggerInterfaceName))
                .filter(row -> row.triggerSignalName().equals(triggerSignalName))
                .findFirst();
    }

    public static boolean isCommandStartSignal(String signalName) {
        return stateMachineSystemTransitions().stream()
                .anyMatch(row -> row.triggerSignalName().equals(signalName)
                        && AdapterOutboundSignal.CMD_START.name().equals(row.actionSignalName()));
    }

    public static boolean isCommandAbortSignal(String signalName) {
        return stateMachineSystemTransitions().stream()
                .anyMatch(row -> row.triggerSignalName().equals(signalName)
                        && AdapterOutboundSignal.CMD_ABORT.name().equals(row.actionSignalName()));
    }

    public static List<String> terminalCommandStateNames() {
        return List.of("COMPLETED", "FAILED", "ABORTED");
    }

    public static String stateOutputInterfaceName() {
        return "Interface_state_out";
    }

    public static List<DeviceCommandTransitionRequirement> deviceCommandTransitionRequirements() {
        return List.of(
                new DeviceCommandTransitionRequirement("MAIN", "SENT", "RUNNING", "REQUIRED"),
                new DeviceCommandTransitionRequirement("MAIN", "RUNNING", "COMPLETED", "REQUIRED"),
                new DeviceCommandTransitionRequirement("FAILURE", "RUNNING", "FAILED", "REQUIRED"),
                new DeviceCommandTransitionRequirement("TERMINATION", "ABORTING", "ABORTED", "REQUIRED")
        );
    }

    private static SystemTransitionDefinition systemTransition(String fromStateName, String toStateName,
                                                                String triggerInterfaceName, String triggerSignalName,
                                                                String actionSignalName) {
        return new SystemTransitionDefinition("CMD", fromStateName, toStateName, triggerInterfaceName,
                triggerSignalName, "Interface_adapter_out", actionSignalName);
    }

    private static List<String> names(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }

    public record InterfaceDefinition(String name, String direction, InterfaceType interfaceType,
                                      List<String> allowedSignals) {
    }

    public record NodeInterfaceDefinition(List<String> nodeTypes, String name, String direction,
                                          InterfaceType interfaceType, List<String> allowedSignals) {
    }

    public record SystemTransitionDefinition(String stateSpace, String fromStateName, String toStateName,
                                             String triggerInterfaceName, String triggerSignalName,
                                             String actionInterfaceName, String actionSignalName) {
    }

    public record DeviceCommandTransitionRequirement(String kind, String fromStateName, String toStateName,
                                                     String triggerPolicy) {
    }
}