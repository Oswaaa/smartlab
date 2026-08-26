package com.smartlab.agent.llm;

import java.util.List;

public record LlmToolCall(String id, String name, String argumentsJson) {
}
