package com.smartlab.agent.api;

import com.smartlab.agent.loop.AgentGenerateException;
import com.smartlab.agent.loop.AgentLoop;
import com.smartlab.management.dto.common.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/agent/workflow")
public class WorkflowAgentController {
    private final AgentLoop agentLoop;
    private final Executor agentGenerateExecutor;

    public WorkflowAgentController(AgentLoop agentLoop, @Qualifier("agentGenerateExecutor") Executor agentGenerateExecutor) {
        this.agentLoop = agentLoop;
        this.agentGenerateExecutor = agentGenerateExecutor;
    }

    @PostMapping("/generate")
    public ApiResponse<WorkflowGenerateResponse> generate(@RequestBody WorkflowGenerateRequest request) {
        try {
            String prompt = request == null ? null : request.prompt();
            return ApiResponse.ok(agentLoop.generate(prompt));
        } catch (AgentGenerateException exception) {
            return ApiResponse.fail(exception.getMessage(), exception.partial());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ApiResponse.fail(exception.getMessage());
        } catch (RuntimeException exception) {
            return ApiResponse.fail(exception.getMessage() == null ? "生成失败" : exception.getMessage());
        }
    }

    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@RequestBody WorkflowGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
        String prompt = request == null ? null : request.prompt();
        agentGenerateExecutor.execute(() -> {
            try {
                WorkflowGenerateResponse result = agentLoop.generate(prompt, log -> send(emitter, "log", log));
                send(emitter, "done", ApiResponse.ok(result));
                emitter.complete();
            } catch (AgentGenerateException exception) {
                send(emitter, "error", ApiResponse.fail(exception.getMessage(), exception.partial()));
                emitter.complete();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                send(emitter, "error", ApiResponse.fail(exception.getMessage()));
                emitter.complete();
            } catch (RuntimeException exception) {
                send(emitter, "error", ApiResponse.fail(exception.getMessage() == null ? "生成失败" : exception.getMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) {
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            }
        }
    }
}
