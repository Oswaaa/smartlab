package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListWorkflowCatalogToolTest {
    @Test
    void listsIdNameAndDescriptionOnly() {
        WorkflowService workflows = mock(WorkflowService.class);
        when(workflows.list()).thenReturn(List.of(
                model(7L, "开盖", "机械臂打开反应釜"),
                model(8L, "加热", null)
        ));

        JsonNode result = new ListWorkflowCatalogTool(workflows).execute(JsonNodeSupport.objectNode());

        assertEquals(2, result.path("workflows").size());
        JsonNode first = result.path("workflows").get(0);
        assertEquals(7L, first.path("flowModelId").asLong());
        assertEquals("开盖", first.path("flowModelName").asText());
        assertEquals("机械臂打开反应釜", first.path("description").asText());
        assertFalse(first.has("nodes"));
        assertEquals("ACTIVE", first.path("status").asText());
        assertEquals("", result.path("workflows").get(1).path("description").asText());
    }

    @Test
    void filtersByKeywordOnNameOrDescription() {
        WorkflowService workflows = mock(WorkflowService.class);
        when(workflows.list()).thenReturn(List.of(
                model(7L, "开盖", "机械臂打开反应釜"),
                model(8L, "加热", "把样品加热到目标温度")
        ));
        JsonNode arguments = JsonNodeSupport.objectNode().put("keyword", "机械臂");

        JsonNode result = new ListWorkflowCatalogTool(workflows).execute(arguments);

        assertEquals(1, result.path("workflows").size());
        assertEquals(7L, result.path("workflows").get(0).path("flowModelId").asLong());
    }

    private FlowModels model(Long id, String name, String description) {
        FlowModels model = new FlowModels();
        model.setId(id);
        model.setFlowName(name);
        model.setDescription(description);
        model.setStatus("ACTIVE");
        return model;
    }
}
