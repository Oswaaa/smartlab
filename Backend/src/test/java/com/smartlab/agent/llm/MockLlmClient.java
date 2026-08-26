package com.smartlab.agent.llm;

import java.util.ArrayList;
import java.util.List;

/** 单测用：按预定顺序返回 completion，不访问外网。 */
public class MockLlmClient implements LlmClient {
    private final List<LlmCompletion> completions;
    private int index;

    public MockLlmClient(List<LlmCompletion> completions) {
        this.completions = new ArrayList<>(completions);
    }

    @Override
    public LlmCompletion complete(List<LlmMessage> messages, List<LlmToolSpec> tools) {
        if (index >= completions.size()) {
            return new LlmCompletion("", List.of());
        }
        return completions.get(index++);
    }
}
