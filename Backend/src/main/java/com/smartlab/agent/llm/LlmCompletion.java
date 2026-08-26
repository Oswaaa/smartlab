package com.smartlab.agent.llm;

import java.util.List;

public record LlmCompletion(String content, List<LlmToolCall> toolCalls) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
