package com.smartlab.agent.api;

import com.smartlab.agent.loop.AgentGenerateException;
import com.smartlab.agent.loop.AgentLoop;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorSession;
import com.smartlab.agent.operator.OperatorSessionStore;
import com.smartlab.management.dto.common.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 唯一的对话入口。
 *
 * <p>以前这里只挂"画流程"的工具，设备操作在另一个 controller 上，
 * 结果同一个问题在首页输入框里只能得到流程草稿。现在两组工具在
 * {@link AgentLoop} 里合流，一个口子进、一个口子出。
 *
 * <p>返回体里的 {@code card} 不为空就代表有一张待确认卡片，
 * 前端必须渲染出来让用户点，服务端不会自己执行。
 */
@RestController
@RequestMapping("/api/agent/workflow")
public class WorkflowAgentController {
    private final AgentLoop agentLoop;
    private final OperatorSessionStore sessions;
    private final Executor agentGenerateExecutor;

    public WorkflowAgentController(AgentLoop agentLoop, OperatorSessionStore sessions,
                                   @Qualifier("agentGenerateExecutor") Executor agentGenerateExecutor) {
        this.agentLoop = agentLoop;
        this.sessions = sessions;
        this.agentGenerateExecutor = agentGenerateExecutor;
    }

    @PostMapping("/generate")
    public ApiResponse<WorkflowGenerateResponse> generate(@RequestBody WorkflowGenerateRequest request,
                                                          Authentication authentication) {
        try {
            return ApiResponse.ok(run(request, authentication, log -> {}));
        } catch (AgentGenerateException exception) {
            return ApiResponse.fail(exception.getMessage(), exception.partial());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ApiResponse.fail(exception.getMessage());
        } catch (RuntimeException exception) {
            return ApiResponse.fail(exception.getMessage() == null ? "处理失败" : exception.getMessage());
        }
    }

    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@RequestBody WorkflowGenerateRequest request, Authentication authentication) {
        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
        agentGenerateExecutor.execute(() -> {
            try {
                WorkflowGenerateResponse result = run(request, authentication, log -> send(emitter, "log", log));
                send(emitter, "done", ApiResponse.ok(result));
                emitter.complete();
            } catch (AgentGenerateException exception) {
                send(emitter, "error", ApiResponse.fail(exception.getMessage(), exception.partial()));
                emitter.complete();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                send(emitter, "error", ApiResponse.fail(exception.getMessage()));
                emitter.complete();
            } catch (RuntimeException exception) {
                send(emitter, "error", ApiResponse.fail(exception.getMessage() == null ? "处理失败" : exception.getMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    private WorkflowGenerateResponse run(WorkflowGenerateRequest request, Authentication authentication,
                                         Consumer<AgentInteractionLog> logListener) {
        String prompt = request == null ? null : request.prompt();
        OperatorSession session = sessions.getOrCreate(request == null ? null : request.sessionId());
        OperatorContext context = new OperatorContext(session, null,
                authentication == null ? null : authentication.getName());
        return agentLoop.chat(session, context, prompt, logListener);
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
