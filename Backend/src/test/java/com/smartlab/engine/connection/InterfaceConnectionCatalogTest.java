package com.smartlab.engine.connection;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterfaceConnectionCatalogTest {

    private final WorkflowService workflows = mock(WorkflowService.class);
    private final InterfaceConnectionCatalog catalog = new InterfaceConnectionCatalog(workflows);

    @Test
    void listsNodeToNodeEdgesFromCompiledOutgoing() {
        FlowNode node = node(3L, 2L, 21L);
        WorkflowDefinitionCompiler.Connection connection =
                new WorkflowDefinitionCompiler.Connection(2L, "out", 3L, "in");
        when(workflows.compileDefinition(3L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(2L, List.of(connection)), Map.of(), Map.of("source", 2L, "target", 3L), 1L, 2L));

        assertThat(catalog.nodeToNodeFrom(node, "out")).containsExactly(connection);
        assertThat(catalog.nodeToNodeFrom(node, "other")).isEmpty();
    }

    @Test
    void resolvesNodeToDeviceRouteFromModel() {
        FlowNode node = node(3L, 2L, 21L);
        stubDeviceConnections();

        InterfaceConnectionCatalog.NodeToDeviceRoute route = catalog.nodeToDevice(node);

        assertThat(route.nodeOutputInterfaceName()).isEqualTo("state-out");
        assertThat(route.deviceInputInterfaceName()).isEqualTo("Interface_cmd_in");
        assertThat(route.deviceModelId()).isEqualTo(21L);
    }

    @Test
    void findNodeToDeviceReturnsNullWhenTemplateMissing() {
        FlowNode node = node(3L, 2L, 21L);
        when(workflows.getDefinition(3L)).thenReturn(new WorkflowDetailResponse());
        when(workflows.compileDefinition(3L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(), Map.of(), Map.of("heat", 2L), 1L, 2L));

        assertThat(catalog.findNodeToDevice(node)).isNull();
    }

    @Test
    void findNodeToDeviceReturnsNullWhenDefinitionIsMissing() {
        FlowNode node = node(3L, 2L, 21L);

        assertThat(catalog.findNodeToDevice(node)).isNull();
    }

    @Test
    void resolvesDeviceToNodeEdgeFromModel() {
        FlowNode node = node(3L, 2L, 21L);
        stubDeviceConnections();

        InterfaceConnectionEdge edge = catalog.deviceToNode(node);

        assertThat(edge.sourceInterfaceName()).isEqualTo("Interface_state_out");
        assertThat(edge.targetInterfaceName()).isEqualTo("state-in");
        assertThat(edge.deviceModelId()).isEqualTo(21L);
    }

    private void stubDeviceConnections() {
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        ObjectNode outbound = JsonNodeSupport.objectNode().put("connectionType", "NODE_TO_DEVICE");
        outbound.set("source", JsonNodeSupport.objectNode().put("nodeName", "heat").put("interfaceName", "state-out"));
        outbound.set("target", JsonNodeSupport.objectNode().put("deviceModelId", 21L).put("interfaceName", "Interface_cmd_in"));
        ObjectNode inbound = JsonNodeSupport.objectNode().put("connectionType", "DEVICE_TO_NODE");
        inbound.set("source", JsonNodeSupport.objectNode().put("deviceModelId", 21L).put("interfaceName", "Interface_state_out"));
        inbound.set("target", JsonNodeSupport.objectNode().put("nodeName", "heat").put("interfaceName", "state-in"));
        definition.setInterfaceConnections(JsonNodeSupport.arrayNode().add(outbound).add(inbound));
        when(workflows.getDefinition(3L)).thenReturn(definition);
        when(workflows.compileDefinition(3L)).thenReturn(new WorkflowDefinitionCompiler.CompiledWorkflow(
                Map.of(), Map.of(), Map.of(), Map.of("heat", 2L), 1L, 2L));
    }

    private FlowNode node(long flowModelId, long nodeIdRef, long deviceModelId) {
        FlowNode node = new FlowNode();
        node.setFlowModelId(flowModelId);
        node.setNodeIdRef(nodeIdRef);
        node.setDeviceModelId(deviceModelId);
        node.setNodeType("DEV_NODE");
        return node;
    }
}
