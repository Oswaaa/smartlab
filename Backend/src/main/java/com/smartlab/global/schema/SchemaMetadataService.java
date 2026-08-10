package com.smartlab.global.schema;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.contract.ConstraintOperator;
import com.smartlab.global.contract.DataType;
import com.smartlab.global.contract.NodeLifecycleState;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.contract.SystemExecutionContract;
import com.smartlab.global.contract.SystemViolationAction;
import com.smartlab.global.contract.TaskLifecycleState;
import com.smartlab.global.contract.WorkflowNodeActionType;
import com.smartlab.global.contract.WorkflowNodeFunctionType;
import com.smartlab.global.contract.WorkflowNodeSystemContract;
import com.smartlab.global.contract.WorkflowNodeType;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 前端模型编辑器的固定元数据入口。定稿模型不再作为运行时Schema解析，只在这里暴露后端实现的固定契约
 */
@Service
public class SchemaMetadataService {

    private final ProtocolDictionaryService protocolDictionaryService;

    public SchemaMetadataService(ProtocolDictionaryService protocolDictionaryService) {
        this.protocolDictionaryService = protocolDictionaryService;
    }

    public ObjectNode frontendMetadata() {
        ObjectNode root = JsonNodeSupport.objectNode();
        root.set("protocol", protocolMetadata());
        root.set("stateMachine", stateMachineMetadata());
        root.set("workflow", workflowMetadata());
        root.set("constraint", constraintMetadata());
        root.set("deviceCapability", deviceCapabilityMetadata());
        return root;
    }

    public List<String> stateMachineActionNames() {
        return List.of("SEND");
    }

    public List<String> workflowActionNames() {
        return names(WorkflowNodeActionType.values());
    }

    public List<String> workflowCalculationOperators() {
        return Arrays.stream(ConstraintOperator.values()).map(ConstraintOperator::value).toList();
    }

    public String workflowActionPayloadDefinition(String actionName) {
        return switch (actionName) {
            case "EMIT" -> "targetInterfaceName,signalName";
            case "UPDATE" -> "updateType,targetName,value|valueExpression";
            default -> throw new IllegalArgumentException("工作流动作类型不存在: " + actionName);
        };
    }

    public List<String> workflowNodeTypes() {
        return names(WorkflowNodeType.values());
    }

    public List<String> workflowFunctionTypes() {
        return names(WorkflowNodeFunctionType.values());
    }

    private ObjectNode protocolMetadata() {
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("mqttTopics", JsonNodeSupport.toNode(protocolDictionaryService.mqttTopicConvention()));
        metadata.set("adapterRegisterFormats", textArray(protocolDictionaryService.enumValues("AdapterRegisterRawConfigFormat")));
        metadata.set("communicationProtocols", textArray(protocolDictionaryService.enumValues("CommunicationProtocol")));
        return metadata;
    }

    private ObjectNode stateMachineMetadata() {
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("commandStateNames", textArray(SystemExecutionContract.commandStateNames()));
        metadata.set("standardInterfaces", interfaces(SystemExecutionContract.stateMachineInterfaces(List.of())));
        metadata.set("systemTransitions", systemTransitions(SystemExecutionContract.stateMachineSystemTransitions()));
        metadata.set("deviceCommandTransitionRequirements", deviceCommandTransitionRequirements(
                SystemExecutionContract.deviceCommandTransitionRequirements()));
        metadata.set("actionTypes", textArray(stateMachineActionNames()));
        return metadata;
    }

    private ObjectNode workflowMetadata() {
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("nodeTypes", textArray(workflowNodeTypes()));
        metadata.set("functionTypes", textArray(workflowFunctionTypes()));
        metadata.set("actionTypes", textArray(workflowActionNames()));
        metadata.set("standardNodeInterfaces", nodeInterfaces(SystemExecutionContract.workflowNodeInterfaces()));
        metadata.set("nodeLifecycleStates", textArray(names(NodeLifecycleState.values())));
        metadata.set("nodeTemplates", WorkflowNodeSystemContract.templates());
        return metadata;
    }

    private ObjectNode constraintMetadata() {
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("objectTypes", textArray(names(ObservableObjectType.values())));
        metadata.set("operators", textArray(workflowCalculationOperators()));
        metadata.set("dataTypes", textArray(names(DataType.values())));
        metadata.set("systemViolationActions", textArray(names(SystemViolationAction.values())));
        metadata.set("taskLifecycleStates", textArray(names(TaskLifecycleState.values())));
        return metadata;
    }

    private ObjectNode deviceCapabilityMetadata() {
        ObjectNode metadata = JsonNodeSupport.objectNode();
        metadata.set("dataTypes", textArray(names(DataType.values())));
        metadata.set("constraintOperators", textArray(workflowCalculationOperators()));
        return metadata;
    }

    private ArrayNode interfaces(List<SystemExecutionContract.InterfaceDefinition> definitions) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        for (SystemExecutionContract.InterfaceDefinition definition : definitions) {
            ObjectNode item = result.addObject();
            item.put("name", definition.name());
            item.put("direction", definition.direction());
            item.put("interfaceType", definition.interfaceType().name());
            item.set("allowedSignals", textArray(definition.allowedSignals()));
        }
        return result;
    }

    private ArrayNode nodeInterfaces(List<SystemExecutionContract.NodeInterfaceDefinition> definitions) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        for (SystemExecutionContract.NodeInterfaceDefinition definition : definitions) {
            ObjectNode item = result.addObject();
            item.set("nodeTypes", textArray(definition.nodeTypes()));
            item.put("name", definition.name());
            item.put("direction", definition.direction());
            item.put("interfaceType", definition.interfaceType().name());
            item.set("allowedSignals", textArray(definition.allowedSignals()));
        }
        return result;
    }

    private ArrayNode systemTransitions(List<SystemExecutionContract.SystemTransitionDefinition> definitions) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        for (SystemExecutionContract.SystemTransitionDefinition definition : definitions) {
            ObjectNode item = result.addObject();
            item.put("stateSpace", definition.stateSpace());
            item.put("fromStateName", definition.fromStateName());
            item.put("toStateName", definition.toStateName());
            item.putObject("trigger")
                    .put("interfaceName", definition.triggerInterfaceName())
                    .put("signalName", definition.triggerSignalName());
            ObjectNode action = item.putArray("actions").addObject();
            action.put("actionName", "SEND");
            action.putObject("payload")
                    .put("interfaceName", definition.actionInterfaceName())
                    .put("signalName", definition.actionSignalName());
        }
        return result;
    }

    private ArrayNode deviceCommandTransitionRequirements(
            List<SystemExecutionContract.DeviceCommandTransitionRequirement> requirements) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        for (SystemExecutionContract.DeviceCommandTransitionRequirement requirement : requirements) {
            ObjectNode item = result.addObject();
            item.put("kind", requirement.kind());
            item.put("fromStateName", requirement.fromStateName());
            item.put("toStateName", requirement.toStateName());
            item.put("triggerPolicy", requirement.triggerPolicy());
        }
        return result;
    }

    private ArrayNode textArray(List<String> values) {
        ArrayNode array = JsonNodeSupport.arrayNode();
        values.forEach(array::add);
        return array;
    }

    private List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
