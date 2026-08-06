package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowResourceRequirementsResponse;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void requirementsHttpEndpointExposesOnlyBindingRequirementFields() throws Exception {
        WorkflowService workflows = mock(WorkflowService.class);
        WorkflowTaskResourceService resources = mock(WorkflowTaskResourceService.class);
        com.smartlab.management.dto.workflow.DeviceBindingRequirement binding = new com.smartlab.management.dto.workflow.DeviceBindingRequirement("1:12", "主流程 / 温控", 1L, 3, 12L, "主流程", "温控", 7L, "TEMP");
        when(resources.requirements(1L)).thenReturn(new WorkflowResourceRequirementsResponse(1L, 3, List.of(binding)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(workflows, resources)).build();
        mvc.perform(get("/api/workflow/1/requirements")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowId").value(1)).andExpect(jsonPath("$.data.bindings[0].slotId").value("1:12"))
                .andExpect(jsonPath("$.data.resourceMap").doesNotExist()).andExpect(jsonPath("$.data.bindings[0].bindingKey").doesNotExist())
                .andExpect(jsonPath("$.data.bindings[0].NODE_TO_DEVICE").doesNotExist());
    }

    @Test
    void draftEndpointReturnsNormalizedPreparation() {
        WorkflowService service = mock(WorkflowService.class);
        WorkflowPreparationResponse expected = response("DRAFT", false, false);
        when(service.saveDraft(any(WorkflowSaveRequest.class))).thenReturn(expected);

        ApiResponse<WorkflowPreparationResponse> result = new WorkflowController(service, mock(WorkflowTaskResourceService.class)).saveDraft(request());

        assertTrue(result.isSuccess());
        assertEquals("DRAFT", result.getData().definition().getStatus());
        assertEquals(false, result.getData().executable());
    }

    @Test
    void legacySaveReturnsPublishPreparation() {
        WorkflowService service = mock(WorkflowService.class);
        WorkflowPreparationResponse expected = response("ACTIVE", true, true);
        when(service.publish(any(WorkflowSaveRequest.class))).thenReturn(expected);

        ApiResponse<java.util.Map<String, Long>> result = new WorkflowController(service, mock(WorkflowTaskResourceService.class)).save(request());

        assertTrue(result.isSuccess());
        assertEquals(42L, result.getData().get("workflowId"));
    }

    @Test
    void httpRoutesKeepLegacyIdShapeAndExposePreparationRoutes() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.publish(any(WorkflowSaveRequest.class))).thenReturn(response("ACTIVE", true, true));
        when(service.saveDraft(any(WorkflowSaveRequest.class))).thenReturn(response("DRAFT", false, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(post("/api/workflow/save").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"workflow\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.workflowId").value(42));
        mvc.perform(post("/api/workflow/draft").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"workflow\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.definition.status").value("DRAFT"));
        mvc.perform(post("/api/workflow/publish").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"workflow\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.definition.status").value("ACTIVE"));
    }
    @Test
    void legacySaveDoesNotReportDraftSuccessOrBlockingPublishSuccess() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.publish(any(WorkflowSaveRequest.class))).thenReturn(
                responseWithId("DRAFT", false, false, 7L),
                responseWithId("DRAFT", false, false, 8L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(post("/api/workflow/save").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"workflow\",\"id\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(post("/api/workflow/save").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"workflow\",\"id\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist());
    }
    private WorkflowPreparationResponse response(String status, boolean executable, boolean published) {
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setStatus(status);
        definition.setId("ACTIVE".equals(status) ? 42L : 0L);
        return new WorkflowPreparationResponse(definition, List.of(), executable, published);
    }

    private WorkflowPreparationResponse responseWithId(String status, boolean executable, boolean published, Long id) {
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setStatus(status);
        definition.setId(id);
        return new WorkflowPreparationResponse(definition, List.of(), executable, published);
    }
    private WorkflowSaveRequest request() {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("workflow");
        return request;
    }
}
