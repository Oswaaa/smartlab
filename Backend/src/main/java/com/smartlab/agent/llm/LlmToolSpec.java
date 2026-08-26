package com.smartlab.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record LlmToolSpec(String name, String description, JsonNode parameterSchema) {
}
