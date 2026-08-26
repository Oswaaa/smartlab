package com.smartlab.agent.llm;

import java.util.List;

public record LlmMessage(String role, String content, String name, String toolCallId, List<LlmToolCall> toolCalls) {
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, null, null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, null, null, null);
    }

    public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", content, null, null, toolCalls);
    }

    public static LlmMessage tool(String toolCallId, String name, String content) {
        return new LlmMessage("tool", content, name, toolCallId, null);
    }
}
