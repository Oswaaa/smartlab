package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.agent.AgentProperties;
import com.smartlab.agent.llm.LlmClient;
import com.smartlab.agent.llm.LlmCompletion;
import com.smartlab.agent.llm.LlmMessage;
import com.smartlab.agent.llm.LlmToolCall;
import com.smartlab.agent.llm.LlmToolSpec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operator 的主循环。
 *
 * <p>和现有 AgentLoop 的区别：
 * <ul>
 *   <li>会话由调用方持有，因此"反问一句、用户回一句"能接上；</li>
 *   <li>工具集合是 OperatorTool，与画流程用的 AgentTool 物理隔离；</li>
 *   <li>循环里没有任何"执行"动作——真正下发只发生在用户点确认之后。</li>
 * </ul>
 */
@Service
public class OperatorLoop {

    private static final int MAX_ROUNDS = 8;
    private static final String PROMPT_LOCATION = "agent/prompts/operator-system.md";
    private static final String FALLBACK = "抱歉，这次没能处理完，请再说一次或者换个说法。";

    public record OperatorReply(String sessionId, String message, ConfirmCard card, String token,
                                long pendingTtlSeconds, List<String> trace) {
    }

    private final LlmClient llmClient;
    private final Map<String, OperatorTool> tools = new LinkedHashMap<>();
    private final AgentProperties properties;
    private final String systemPrompt;

    public OperatorLoop(LlmClient llmClient, List<OperatorTool> operatorTools, AgentProperties properties) {
        this.llmClient = llmClient;
        for (OperatorTool tool : operatorTools) {
            this.tools.put(tool.name(), tool);
        }
        this.properties = properties;
        this.systemPrompt = loadPrompt();
    }

    private static String loadPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_LOCATION);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("加载操作助手提示词失败: " + error.getMessage(), error);
        }
    }

    public OperatorReply chat(OperatorSession session, OperatorContext context, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("请输入要执行的操作");
        }
        if (!properties.isEnabled()) {
            throw new IllegalStateException("设备操作助手未启用");
        }

        List<LlmMessage> history = new ArrayList<>(session.messages());
        if (history.isEmpty()) {
            history.add(LlmMessage.system(systemPrompt));
        }
        history.add(LlmMessage.user(userMessage.trim()));

        List<LlmToolSpec> specs = specs();
        List<String> trace = new ArrayList<>();
        String reply = null;

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            LlmCompletion completion = llmClient.complete(history, specs);
            if (!completion.hasToolCalls()) {
                reply = completion.content() == null ? "" : completion.content().trim();
                if (!reply.isEmpty()) {
                    history.add(LlmMessage.assistant(reply, List.of()));
                }
                break;
            }
            history.add(LlmMessage.assistant(completion.content(), completion.toolCalls()));
            for (LlmToolCall call : completion.toolCalls()) {
                JsonNode result = dispatch(call, context, trace);
                history.add(LlmMessage.tool(
                        call.id() == null || call.id().isBlank() ? UUID.randomUUID().toString() : call.id(),
                        call.name(),
                        result.toString()));
            }
        }

        session.resetMessages(history);
        PendingAction pending = session.pending();
        if (reply == null || reply.isBlank()) {
            reply = pending == null ? FALLBACK : "已准备好操作，请确认后再执行。";
        }
        return new OperatorReply(
                session.id(),
                reply,
                pending == null ? null : pending.card(),
                pending == null ? null : pending.token(),
                pending == null ? 0 : 5 * 60,
                List.copyOf(trace)
        );
    }

    private JsonNode dispatch(LlmToolCall call, OperatorContext context, List<String> trace) {
        String name = call == null ? "" : call.name();
        OperatorTool tool = tools.get(name);
        if (tool == null) {
            trace.add("unknown:" + name);
            return OperatorJson.error("UNKNOWN_TOOL", "未注册的工具: " + name);
        }
        JsonNode arguments;
        try {
            String json = call.argumentsJson();
            arguments = json == null || json.isBlank() ? OperatorJson.obj() : OperatorJson.MAPPER.readTree(json);
        } catch (Exception error) {
            trace.add(name + ":bad-args");
            return OperatorJson.error("BAD_ARGUMENTS", "工具参数不是合法 JSON");
        }
        try {
            JsonNode result = tool.execute(arguments, context);
            trace.add(name);
            return result;
        } catch (RuntimeException error) {
            trace.add(name + ":error");
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return OperatorJson.error("TOOL_FAILED", message);
        }
    }

    private List<LlmToolSpec> specs() {
        List<LlmToolSpec> specs = new ArrayList<>();
        for (OperatorTool tool : tools.values()) {
            specs.add(new LlmToolSpec(tool.name(), tool.description(), tool.parameterSchema()));
        }
        return specs;
    }

    /** 供测试替换提示词路径外的依赖时使用。 */
    String systemPrompt() {
        return systemPrompt;
    }
}
