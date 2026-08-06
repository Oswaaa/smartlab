package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowSaveRequest;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.junit.jupiter.api.Test;

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

        ApiResponse<WorkflowPreparationResponse> result = new WorkflowController(service).save(request());

        assertTrue(result.isSuccess());
        assertEquals("ACTIVE", result.getData().definition().getStatus());
        assertTrue(result.getData().published());
    }

    private WorkflowPreparationResponse response(String status, boolean executable, boolean published) {
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setStatus(status);
        return new WorkflowPreparationResponse(definition, List.of(), executable, published);
    }

    private WorkflowSaveRequest request() {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setName("workflow");
        return request;
    }
}
