package com.smartlab.agent.api;

import com.smartlab.agent.loop.AgentLoop;
import com.smartlab.management.dto.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowAgentControllerTest {
    @Test
    void generateRejectsBlankPrompt() {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.generate(" ")).thenThrow(new IllegalArgumentException("请输入要生成的实验流程描述"));
        ApiResponse<WorkflowGenerateResponse> result = new WorkflowAgentController(loop, Runnable::run).generate(new WorkflowGenerateRequest(" "));
        assertFalse(result.isSuccess());
        assertEquals("请输入要生成的实验流程描述", result.getMessage());
    }

    @Test
    void generateRouteExists() throws Exception {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.generate("加热")).thenThrow(new IllegalStateException("未配置大模型接入：SMARTLAB_AGENT_API_KEY"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowAgentController(loop, Runnable::run)).build();
        mvc.perform(post("/api/agent/workflow/generate").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"加热\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("未配置大模型接入：SMARTLAB_AGENT_API_KEY"));
    }
}
