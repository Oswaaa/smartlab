package com.smartlab.engine.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 工作流接口连接表：从已定稿模型读取 NODE_TO_NODE / NODE_TO_DEVICE / DEVICE_TO_NODE。
 * 不做投递，只回答「谁连谁」。
 */
@Component
public class InterfaceConnectionCatalog {
    private final WorkflowService workflowService;

    public InterfaceConnectionCatalog(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public List<WorkflowDefinitionCompiler.Connection> nodeToNodeFrom(FlowNode node, String sourceInterfaceName) {
        if (node == null || node.getFlowModelId() == null || sourceInterfaceName == null || sourceInterfaceName.isBlank()) {
            return List.of();
        }
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        List<WorkflowDefinitionCompiler.Connection> result = new ArrayList<>();
        for (var connection : compiled.outgoing(node.getNodeIdRef())) {
            if (sourceInterfaceName.equals(connection.sourceInterface())) result.add(connection);
        }
        return List.copyOf(result);
    }

    public NodeToDeviceRoute nodeToDevice(FlowNode node) {
        NodeToDeviceRoute route = findNodeToDevice(node);
        if (route == null) throw new IllegalStateException("DEV_NODE缺少NODE_TO_DEVICE设备模型连接: " + node.getNodeIdRef());
        return route;
    }

    public NodeToDeviceRoute nodeToDevice(WorkflowDefinitionCompiler.CompiledWorkflow compiled,
                                              JsonNode interfaceConnections, long nodeIdRef, Long deviceModelId) {
        InterfaceConnectionEdge edge = uniqueDeviceEdge(compiled, interfaceConnections, nodeIdRef, deviceModelId,
                "NODE_TO_DEVICE", null);
        if (edge == null) throw new IllegalStateException("DEV_NODE缺少NODE_TO_DEVICE设备模型连接: " + nodeIdRef);
        return new NodeToDeviceRoute(edge.sourceInterfaceName(), edge.targetInterfaceName(), edge.deviceModelId());
    }

    public NodeToDeviceRoute findNodeToDevice(FlowNode node) {
        InterfaceConnectionEdge edge = uniqueDeviceEdge(node, "NODE_TO_DEVICE");
        if (edge == null) return null;
        return new NodeToDeviceRoute(edge.sourceInterfaceName(), edge.targetInterfaceName(), edge.deviceModelId());
    }

    public InterfaceConnectionEdge deviceToNode(FlowNode node) {
        return uniqueDeviceEdge(node, "DEVICE_TO_NODE");
    }

    private InterfaceConnectionEdge uniqueDeviceEdge(FlowNode node, String connectionType) {
        if (node == null || node.getFlowModelId() == null) return null;
        WorkflowDetailResponse definition = workflowService.getDefinition(node.getFlowModelId());
        if (definition == null) return null;
        var compiled = workflowService.compileDefinition(node.getFlowModelId());
        return uniqueDeviceEdge(compiled, definition.getInterfaceConnections(), node.getNodeIdRef(),
                node.getDeviceModelId(), connectionType, node.getFlowModelId());
    }

    private InterfaceConnectionEdge uniqueDeviceEdge(WorkflowDefinitionCompiler.CompiledWorkflow compiled,
                                                        JsonNode interfaceConnections, long nodeIdRef,
                                                        Long expectedDeviceModelId, String connectionType,
                                                        Long flowModelId) {
        if (compiled == null) return null;
        InterfaceConnectionEdge found = null;
        for (JsonNode connection : iterable(interfaceConnections)) {
            if (!connectionType.equals(connection.path("connectionType").asText())) continue;
            if ("NODE_TO_DEVICE".equals(connectionType)) {
                Long sourceRef = compiled.refsByNodeName().get(connection.path("source").path("nodeName").asText(""));
                if (sourceRef == null || sourceRef.longValue() != nodeIdRef) continue;
                long deviceModelId = connection.path("target").path("deviceModelId").asLong(0);
                String sourceInterface = connection.path("source").path("interfaceName").asText("");
                String targetInterface = connection.path("target").path("interfaceName").asText("");
                if (deviceModelId <= 0 || !Long.valueOf(deviceModelId).equals(expectedDeviceModelId)
                        || sourceInterface.isBlank() || targetInterface.isBlank()) {
                    throw new IllegalArgumentException("NODE_TO_DEVICE设备模型连接不完整或与DEV_NODE不一致: " + nodeIdRef);
                }
                InterfaceConnectionEdge edge = new InterfaceConnectionEdge(connectionType, flowModelId,
                        sourceRef, sourceInterface, null, targetInterface, deviceModelId);
                if (found != null) throw new IllegalStateException("DEV_NODE只能有一个NODE_TO_DEVICE连接: " + nodeIdRef);
                found = edge;
            } else {
                Long targetRef = compiled.refsByNodeName().get(connection.path("target").path("nodeName").asText(""));
                if (targetRef == null || !Objects.equals(targetRef, nodeIdRef)) continue;
                long deviceModelId = connection.path("source").path("deviceModelId").asLong(0);
                String sourceInterface = connection.path("source").path("interfaceName").asText("");
                String targetInterface = connection.path("target").path("interfaceName").asText("");
                if (deviceModelId <= 0 || !Long.valueOf(deviceModelId).equals(expectedDeviceModelId)
                        || sourceInterface.isBlank() || targetInterface.isBlank()) {
                    continue;
                }
                InterfaceConnectionEdge edge = new InterfaceConnectionEdge(connectionType, flowModelId,
                        null, sourceInterface, targetRef, targetInterface, deviceModelId);
                if (found != null) throw new IllegalStateException("DEV_NODE只能有一个DEVICE_TO_NODE连接: " + nodeIdRef);
                found = edge;
            }
        }
        return found;
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    public record NodeToDeviceRoute(String nodeOutputInterfaceName, String deviceInputInterfaceName, long deviceModelId) {
    }
}
