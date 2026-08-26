package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {
    String name();

    String description();

    JsonNode parameterSchema();

    JsonNode execute(JsonNode arguments);
}
