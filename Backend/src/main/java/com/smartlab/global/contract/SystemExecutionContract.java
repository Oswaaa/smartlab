package com.smartlab.global.contract;

import java.util.List;

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

    private static List<String> names(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }

    public record InterfaceDefinition(String name, String direction, InterfaceType interfaceType,
                                      List<String> allowedSignals) {
    }

    public record NodeInterfaceDefinition(List<String> nodeTypes, String name, String direction,
                                          InterfaceType interfaceType, List<String> allowedSignals) {
    }
}
