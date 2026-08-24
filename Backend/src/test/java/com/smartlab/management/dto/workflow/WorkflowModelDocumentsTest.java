package com.smartlab.management.dto.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowModelDocumentsTest {
    @Test
    void sanitizeDocumentDropsSystemMarkersAndKeepsSchemaFields() {
        WorkflowModelDocument document = new WorkflowModelDocument();
        WorkflowModelMetadata metadata = new WorkflowModelMetadata();
        metadata.setFlowModelId(9L);
        metadata.setFlowModelName("恒温反应");
        metadata.setDescription("desc");
        document.setMetadata(metadata);
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("name", "start1");
        node.put("_system", true);
        node.put("_systemKey", "start.lifecycle");
        document.setNodes(JsonNodeSupport.arrayNode().add(node));

        WorkflowModelDocument sanitized = WorkflowModelDocuments.sanitizeDocument(document);

        assertEquals(9L, sanitized.flowModelId());
        assertEquals("恒温反应", sanitized.flowModelName());
        assertEquals("desc", sanitized.descriptionText());
        assertEquals("start1", sanitized.getNodes().get(0).path("name").asText());
        assertFalse(sanitized.getNodes().get(0).has("_system"));
        assertFalse(sanitized.getNodes().get(0).has("_systemKey"));
    }

    @Test
    void documentAndViewUseFlowModelNameInsteadOfName() {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setId(3L);
        detail.setName("测试流程");
        detail.setDescription("");
        detail.setVersion(2);
        detail.setStatus("ACTIVE");
        ObjectNode node = JsonNodeSupport.objectNode();
        node.put("name", "start1");
        node.put("_system", true);
        detail.setNodesDef(JsonNodeSupport.arrayNode().add(node));
        detail.setInterfaceConnections(JsonNodeSupport.arrayNode());
        detail.setPortConnections(JsonNodeSupport.arrayNode());

        WorkflowModelDocument document = WorkflowModelDocuments.toDocument(detail);
        assertEquals(3L, document.getMetadata().getFlowModelId());
        assertEquals("测试流程", document.getMetadata().getFlowModelName());
        assertEquals("start1", document.getNodes().get(0).path("name").asText());
        assertFalse(document.getNodes().get(0).has("_system"));

        WorkflowViewResponse view = WorkflowModelDocuments.toView(detail);
        assertEquals(2, view.getVersion());
        assertEquals("ACTIVE", view.getStatus());
        assertEquals("测试流程", view.getMetadata().getFlowModelName());
    }

    @Test
    void summaryUsesFlowModelNameFromEntity() {
        FlowModels model = new FlowModels();
        model.setId(4L);
        model.setFlowName("列表名");
        model.setStatus("DRAFT");
        model.setVersion(1);
        WorkflowSummaryResponse summary = WorkflowModelDocuments.toSummary(model);
        assertEquals(4L, summary.getId());
        assertEquals("列表名", summary.getFlowModelName());
        assertEquals("DRAFT", summary.getStatus());
        assertNull(summary.getPredecessorId());
    }

    @Test
    void preparationExposesModelFileAndPersistenceFieldsSeparately() {
        WorkflowDetailResponse detail = new WorkflowDetailResponse();
        detail.setId(1L);
        detail.setName("流程");
        detail.setVersion(1);
        detail.setStatus("DRAFT");
        detail.setNodesDef(JsonNodeSupport.arrayNode());
        WorkflowPreparationResponse prepared = new WorkflowPreparationResponse(detail, java.util.List.of(), false, false);

        WorkflowPreparationApiResponse api = WorkflowModelDocuments.toPreparation(prepared);
        assertEquals("流程", api.definition().getMetadata().getFlowModelName());
        assertEquals(1, api.version());
        assertEquals("DRAFT", api.status());
        assertTrue(api.definition().getNodes().isArray());
    }

    @Test
    void copyFlowNameCreatesANewLineageLabel() {
        assertEquals("恒温反应（副本）", WorkflowModelDocuments.copyFlowName("恒温反应"));
        assertEquals("恒温反应（副本 2）", WorkflowModelDocuments.copyFlowName("恒温反应（副本）"));
        assertEquals("恒温反应（副本 3）", WorkflowModelDocuments.copyFlowName("恒温反应（副本 2）"));
        assertEquals("未命名流程（副本）", WorkflowModelDocuments.copyFlowName("  "));
        assertEquals(80, WorkflowModelDocuments.copyFlowName("A".repeat(80)).length());
        assertTrue(WorkflowModelDocuments.copyFlowName("A".repeat(80)).endsWith("（副本）"));
    }

    @Test
    void asNewDraftClearsIdentityAndRenames() {
        WorkflowModelDocument document = WorkflowModelDocuments.of("恒温反应");
        document.flowModelId(7L);
        WorkflowModelDocument copy = WorkflowModelDocuments.asNewDraft(document);
        assertNull(copy.flowModelId());
        assertEquals("恒温反应（副本）", copy.flowModelName());
    }
}
