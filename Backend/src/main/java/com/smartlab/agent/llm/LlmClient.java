package com.smartlab.agent.llm;

import java.util.List;

public interface LlmClient {
    LlmCompletion complete(List<LlmMessage> messages, List<LlmToolSpec> tools);
}
