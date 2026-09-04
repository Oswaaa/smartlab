package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowSimulationReport;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDocumentsTest {
    @Test
    void rejectsMetadataNameInsteadOfFlowModelName() throws Exception {
        JsonNode arguments = JsonNodeSupport.MAPPER.readTree("""
                {"document":{"metadata":{"name":"加热散热","description":"demo"},"nodes":[],"interfaceConnections":[],"portConnections":[]}}
                """);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AgentDocuments.requireDocument(arguments));
        assertTrue(error.getMessage().contains("flowModelName"));
        assertTrue(error.getMessage().contains("name"));
    }

    @Test
    void validationResultOmitsCanonicalDefinition() {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setName("加热");
        detail.setNodesDef(JsonNodeSupport.arrayNode());
        JsonNode result = AgentDocuments.validationNode(new WorkflowPreparationResponse(detail, List.of(
                new WorkflowIssue("WORKFLOW_DEFINITION_INVALID", "COMPILATION", "", "workflow", "", true, "nodes[0].name: 不能为空", "补 name")
        ), false, false));
        assertFalse(result.has("definition"));
        assertTrue(result.path("blocking").asBoolean());
        assertEquals("nodes[0].name: 不能为空", result.path("issues").get(0).path("message").asText());
        assertTrue(result.path("issues").get(0).path("repair").asText().contains("name"));
    }

    @Test
    void saveResultKeepsIdentityWithoutDefinition() {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setId(9L);
        detail.setName("加热");
        detail.setStatus("DRAFT");
        detail.setVersion(1);
        JsonNode result = AgentDocuments.saveNode(new WorkflowPreparationResponse(detail, List.of(), true, false));
        assertFalse(result.has("definition"));
        assertEquals(9L, result.path("flowModelId").asLong());
        assertEquals("加热", result.path("flowModelName").asText());
        assertEquals("DRAFT", result.path("status").asText());
    }

    @Test
    void simulationResultKeepsWalkSummaryWithoutDefinition() {
        JsonNode result = AgentDocuments.simulationNode(new WorkflowSimulationReport(
                null, 11L, null, true,
                List.of("start", "heat", "end"),
                List.of(List.of("start", "heat", "end")),
                List.of()));
        assertFalse(result.has("definition"));
        assertTrue(result.path("walkable").asBoolean());
        assertEquals(11L, result.path("flowModelId").asLong());
        assertEquals("heat", result.path("pathTaken").get(1).asText());
        assertFalse(result.path("blocking").asBoolean());
    }

    @Test
    void documentSchemaNamesAuthoringFields() {
        JsonNode schema = AgentDocuments.documentParameterSchema();
        JsonNode document = schema.path("properties").path("document").path("properties");
        JsonNode nodeProperties = document.path("nodes").path("items").path("properties");
        JsonNode connectionProperties = document.path("interfaceConnections").path("items").path("properties");
        assertTrue(document.has("metadata"));
        assertEquals("flowModelName", document.path("metadata").path("required").get(0).asText());
        assertEquals("FUNC_NODE", nodeProperties.path("nodeType").path("enum").get(0).asText());
        assertEquals("SUBFLOW_NODE", nodeProperties.path("nodeType").path("enum").get(2).asText());
        assertEquals("BRANCH", nodeProperties.path("functionType").path("enum").get(2).asText());
        assertTrue(nodeProperties.has("subFlowModelId"));
        assertTrue(nodeProperties.has("interfaces"));
        assertTrue(nodeProperties.path("capability").path("properties").has("capabilityParameters"));
        assertEquals("NODE_TO_DEVICE", connectionProperties.path("connectionType").path("enum").get(1).asText());
        assertFalse(connectionProperties.has("fromNodeId"));
        assertFalse(nodeProperties.has("fromNodeId"));
    }
}
