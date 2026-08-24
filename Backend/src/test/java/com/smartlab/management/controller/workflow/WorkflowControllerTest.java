package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowPreparationApiResponse;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowResourceRequirementsResponse;
import com.smartlab.management.service.db.workflow.WorkflowTaskResourceService;
import com.smartlab.management.service.db.workflow.WorkflowService;
import com.smartlab.management.service.db.workflow.WorkflowSuccessorExistsException;
import com.smartlab.management.entity.workflow.FlowModels;
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
    private static final String MODEL_JSON = "{\"metadata\":{\"flowModelName\":\"workflow\"},\"nodes\":[],\"interfaceConnections\":[],\"portConnections\":[]}";

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
        when(service.saveDraft(any(WorkflowModelDocument.class))).thenReturn(expected);

        ApiResponse<WorkflowPreparationApiResponse> result = new WorkflowController(service, mock(WorkflowTaskResourceService.class)).saveDraft(document());

        assertTrue(result.isSuccess());
        assertEquals("DRAFT", result.getData().status());
        assertEquals("workflow", result.getData().definition().getMetadata().getFlowModelName());
        assertEquals(false, result.getData().executable());
    }

    @Test
    void legacySaveReturnsPublishPreparation() {
        WorkflowService service = mock(WorkflowService.class);
        WorkflowPreparationResponse expected = response("ACTIVE", true, true);
        when(service.publish(any(WorkflowModelDocument.class))).thenReturn(expected);

        ApiResponse<java.util.Map<String, Long>> result = new WorkflowController(service, mock(WorkflowTaskResourceService.class)).save(document());

        assertTrue(result.isSuccess());
        assertEquals(42L, result.getData().get("workflowId"));
    }

    @Test
    void httpRoutesKeepLegacyIdShapeAndExposePreparationRoutes() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.publish(any(WorkflowModelDocument.class))).thenReturn(response("ACTIVE", true, true));
        when(service.saveDraft(any(WorkflowModelDocument.class))).thenReturn(response("DRAFT", false, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(post("/api/workflow/save").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.workflowId").value(42));
        mvc.perform(post("/api/workflow/draft").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.definition.metadata.flowModelName").value("workflow"));
        mvc.perform(post("/api/workflow/publish").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void validateEndpointReturnsPreparationWithoutPersisting() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.validate(any(WorkflowModelDocument.class))).thenReturn(response("DRAFT", false, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(post("/api/workflow/validate").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executable").value(false))
                .andExpect(jsonPath("$.data.published").value(false))
                .andExpect(jsonPath("$.data.definition.metadata.flowModelName").value("workflow"));

        org.mockito.Mockito.verify(service).validate(any(WorkflowModelDocument.class));
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).publish(any());
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).saveDraft(any());
    }

    @Test
    void copyEndpointSavesANewDraftLineage() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.saveAsNew(any(WorkflowModelDocument.class))).thenReturn(response("DRAFT", false, false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(post("/api/workflow/copy").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.published").value(false));

        org.mockito.Mockito.verify(service).saveAsNew(any(WorkflowModelDocument.class));
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).saveDraft(any());
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).publish(any());
    }

    @Test
    void draftAndPublishRejectExistingSuccessorWithoutPersisting() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        FlowModels parent = new FlowModels();
        parent.setId(7L);
        parent.setFlowName("published-flow");
        parent.setVersion(1);
        parent.setStatus("ACTIVE");
        FlowModels successor = new FlowModels();
        successor.setId(8L);
        successor.setFlowName("published-flow");
        successor.setVersion(2);
        successor.setStatus("DRAFT");
        when(service.saveDraft(any(WorkflowModelDocument.class))).thenThrow(new WorkflowSuccessorExistsException(parent, successor));
        when(service.publish(any(WorkflowModelDocument.class))).thenThrow(new WorkflowSuccessorExistsException(parent, successor));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(post("/api/workflow/draft").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.successorId").value(8))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已有后续版本")));
        mvc.perform(post("/api/workflow/publish").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.successorId").value(8));
    }

    @Test
    void exportReturnsSchemaDocumentWithoutPersistenceFields() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.getDefinition(5L)).thenReturn(detail("ACTIVE", 5L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(get("/api/workflow/export/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metadata.flowModelId").value(5))
                .andExpect(jsonPath("$.data.metadata.flowModelName").value("workflow"))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.version").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.name").doesNotExist())
                .andExpect(jsonPath("$.data.nodesDef").doesNotExist());
    }

    @Test
    void detailReturnsModelFilePlusPersistenceFields() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.getDefinition(5L)).thenReturn(detail("DRAFT", 5L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(get("/api/workflow/detail/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metadata.flowModelName").value("workflow"))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.nodesDef").doesNotExist());
    }

    @Test
    void legacySaveDoesNotReportDraftSuccessOrBlockingPublishSuccess() throws Exception {
        WorkflowService service = mock(WorkflowService.class);
        when(service.publish(any(WorkflowModelDocument.class))).thenReturn(
                responseWithId("DRAFT", false, false, 7L),
                responseWithId("DRAFT", false, false, 8L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowController(service, mock(WorkflowTaskResourceService.class))).build();

        mvc.perform(post("/api/workflow/save").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(post("/api/workflow/save").contentType(MediaType.APPLICATION_JSON).content(MODEL_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist());
    }

    private WorkflowPreparationResponse response(String status, boolean executable, boolean published) {
        return new WorkflowPreparationResponse(detail(status, "ACTIVE".equals(status) ? 42L : 0L), List.of(), executable, published);
    }

    private WorkflowPreparationResponse responseWithId(String status, boolean executable, boolean published, Long id) {
        return new WorkflowPreparationResponse(detail(status, id), List.of(), executable, published);
    }

    private WorkflowDetailResponse detail(String status, Long id) {
        WorkflowDetailResponse definition = new WorkflowDetailResponse();
        definition.setStatus(status);
        definition.setId(id);
        definition.setName("workflow");
        definition.setVersion(1);
        definition.setNodesDef(com.smartlab.global.util.JsonNodeSupport.arrayNode());
        definition.setInterfaceConnections(com.smartlab.global.util.JsonNodeSupport.arrayNode());
        definition.setPortConnections(com.smartlab.global.util.JsonNodeSupport.arrayNode());
        return definition;
    }

    private WorkflowModelDocument document() {
        WorkflowModelDocument document = new WorkflowModelDocument();
        document.getMetadata().setFlowModelName("workflow");
        return document;
    }
}
