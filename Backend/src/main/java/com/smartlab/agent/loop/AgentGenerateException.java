package com.smartlab.agent.loop;

public class AgentGenerateException extends RuntimeException {
    private final com.smartlab.agent.api.WorkflowGenerateResponse partial;

    public AgentGenerateException(String message, com.smartlab.agent.api.WorkflowGenerateResponse partial) {
        super(message);
        this.partial = partial;
    }

    public com.smartlab.agent.api.WorkflowGenerateResponse partial() {
        return partial;
    }
}
