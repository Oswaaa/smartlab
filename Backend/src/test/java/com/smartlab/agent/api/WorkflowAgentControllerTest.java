package com.smartlab.agent.api;

import com.smartlab.agent.loop.AgentLoop;
import com.smartlab.agent.operator.OperatorSession;
import com.smartlab.agent.operator.OperatorSessionStore;
import com.smartlab.management.dto.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowAgentControllerTest {

    private static WorkflowAgentController controller(AgentLoop loop) {
        OperatorSessionStore sessions = mock(OperatorSessionStore.class);
        when(sessions.getOrCreate(any())).thenReturn(new OperatorSession("s-test"));
        return new WorkflowAgentController(loop, sessions, Runnable::run);
    }

    @Test
    void generateRejectsBlankPrompt() {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.chat(any(), any(), eq(" "), any())).thenThrow(new IllegalArgumentException("请输入要做什么"));
        ApiResponse<WorkflowGenerateResponse> result = controller(loop).generate(new WorkflowGenerateRequest(" "), null);
        assertFalse(result.isSuccess());
        assertEquals("请输入要做什么", result.getMessage());
    }

    @Test
    void generateRouteExists() throws Exception {
        AgentLoop loop = mock(AgentLoop.class);
        when(loop.chat(any(), any(), eq("加热"), any()))
                .thenThrow(new IllegalStateException("未配置大模型接入：SMARTLAB_AGENT_API_KEY"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(loop)).build();
        mvc.perform(post("/api/agent/workflow/generate").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"加热\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("未配置大模型接入：SMARTLAB_AGENT_API_KEY"));
    }
}
