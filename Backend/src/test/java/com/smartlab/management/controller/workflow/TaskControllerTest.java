package com.smartlab.management.controller.workflow;

import com.smartlab.engine.workflow.WorkflowTaskControlService;
import com.smartlab.management.dto.workflow.TaskPreflightRequest;
import com.smartlab.management.dto.workflow.TaskPreflightResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.service.db.workflow.TaskExecutionViewService;
import com.smartlab.management.service.db.workflow.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {
    @Test
    void preflightDeserializesDeviceBindingsAndReturnsStructuredIssues() throws Exception {
        TaskService service = mock(TaskService.class);
        when(service.preflight(any())).thenReturn(new TaskPreflightResponse(false, List.of(
                new WorkflowIssue("TASK_BINDING_MISSING", "BINDING", "deviceBindings[slot-a]", "DEV_NODE", "slot-a", true, "缺少绑定", "绑定设备"))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TaskController(service, mock(WorkflowTaskControlService.class), mock(TaskExecutionViewService.class))).build();

        mvc.perform(post("/api/task/preflight").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowModelId\":11,\"deviceBindings\":[{\"slotId\":\"slot-a\",\"deviceInstanceId\":7}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ready").value(false)).andExpect(jsonPath("$.data.issues[0].code").value("TASK_BINDING_MISSING"))
                .andExpect(jsonPath("$.data.issues[0].stage").value("BINDING"))
                .andExpect(jsonPath("$.data.issues[0].path").value("deviceBindings[slot-a]"))
                .andExpect(jsonPath("$.data.issues[0].elementType").value("DEV_NODE"))
                .andExpect(jsonPath("$.data.issues[0].elementId").value("slot-a"))
                .andExpect(jsonPath("$.data.issues[0].blocking").value(true))
                .andExpect(jsonPath("$.data.issues[0].message").value("缺少绑定"))
                .andExpect(jsonPath("$.data.issues[0].suggestion").value("绑定设备"))
                .andExpect(jsonPath("$.data.resourceMap").doesNotExist()).andExpect(jsonPath("$.data.bindingKey").doesNotExist());
        var captor = forClass(TaskPreflightRequest.class);
        verify(service).preflight(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("slot-a", captor.getValue().deviceBindings().get(0).slotId());
        org.junit.jupiter.api.Assertions.assertEquals(7L, captor.getValue().deviceBindings().get(0).deviceInstanceId());
    }
}