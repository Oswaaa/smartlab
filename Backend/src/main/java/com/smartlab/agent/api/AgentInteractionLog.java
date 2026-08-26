package com.smartlab.agent.api;

public record AgentInteractionLog(
        int round,
        String kind,
        String title,
        String detail,
        String payload) {
}
