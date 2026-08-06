package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowControllerTest {

    @Test
    void draftEndpointReturnsNormalizedPreparation() {
        WorkflowService service = mock(WorkflowService.class);
        WorkflowPreparationResponse expected = response("DRAFT", false, false);
        when(service.saveDraft(any(WorkflowSaveRequest.class))).thenReturn(expected);

        ApiResponse<WorkflowPreparationResponse> result = new WorkflowController(service).saveDraft(request());

        assertTrue(result.isSuccess());
        assertEquals("DRAFT", result.getData().definition().getStatus());
        assertEquals(false, result.getData().executable());
    }

    @Test
    void legacySaveReturnsPublishPreparation() {
        WorkflowService service = mock(WorkflowService.class);
        WorkflowPreparationResponse expected = response("ACTIVE", true, true);
        when(service.publish(any(WorkflowSaveRequest.class))).thenReturn(expected);

        ApiResponse<java.util.Map<String, Long>> result = new WorkflowController(service).save(request());

        assertTrue(result.isSuccess());
        assertEquals(42L, result.getData().get("workflowId"));
    }

    @Test
    void httpRoutesKeepLegacyIdShapeAndExposePreparationRoutes() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.publish(any(WorkflowSaveRequest.class))).thenReturn(response("ACTIVE", true, true));
        when(service.saveDraft(any(WorkflowSaveRequest.class))).thenReturn(response("DRAFT", false, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service)).build();

        mvc.perform(post("/api/workflow/save").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"workflow\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.workflowId").value(42));
        mvc.perform(post("/api/workflow/draft").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"workflow\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.definition.status").value("DRAFT"));
        mvc.perform(post("/api/workflow/publish").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"workflow\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.definition.status").value("ACTIVE"));
    }
    private WorkflowPreparationResponse response(String status, boolean executable, boolean published) {
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setStatus(status);
        definition.setId("ACTIVE".equals(status) ? 42L : 0L);
        return new WorkflowPreparationResponse(definition, List.of(), executable, published);
    }

    private WorkflowSaveRequest request() {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("workflow");
        return request;
    }
}
