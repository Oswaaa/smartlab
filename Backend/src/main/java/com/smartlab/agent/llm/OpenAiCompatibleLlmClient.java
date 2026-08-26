package com.smartlab.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.AgentProperties;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {
    private final AgentProperties properties;
    private final RestClient restClient;

    @Autowired
    public OpenAiCompatibleLlmClient(AgentProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory())
                .build();
    }

    OpenAiCompatibleLlmClient(AgentProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public LlmCompletion complete(List<LlmMessage> messages, List<LlmToolSpec> tools) {
        ObjectNode body = JsonNodeSupport.objectNode();
        body.put("model", properties.getModel());
        body.put("temperature", 0.2);
        body.set("messages", messagesNode(messages));
        body.set("tools", toolsNode(tools));
        if (usesDeepSeekThinking()) {
            body.putObject("thinking").put("type", "disabled");
        }
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(completionsUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("调用大模型失败: " + exception.getMessage(), exception);
        }
        JsonNode message = response == null ? JsonNodeSupport.objectNode()
                : response.path("choices").path(0).path("message");
        return new LlmCompletion(message.path("content").asText(""), toolCalls(message.path("tool_calls")));
    }

    private ArrayNode messagesNode(List<LlmMessage> messages) {
        ArrayNode array = JsonNodeSupport.arrayNode();
        for (LlmMessage message : messages) {
            ObjectNode node = array.addObject();
            node.put("role", message.role());
            if (message.content() != null) node.put("content", message.content());
            if (message.name() != null) node.put("name", message.name());
            if (message.toolCallId() != null) node.put("tool_call_id", message.toolCallId());
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                ArrayNode calls = node.putArray("tool_calls");
                for (LlmToolCall call : message.toolCalls()) {
                    ObjectNode item = calls.addObject();
                    item.put("id", call.id() == null ? UUID.randomUUID().toString() : call.id());
                    item.put("type", "function");
                    ObjectNode function = item.putObject("function");
                    function.put("name", call.name());
                    function.put("arguments", call.argumentsJson() == null ? "{}" : call.argumentsJson());
                }
            }
        }
        return array;
    }

    private ArrayNode toolsNode(List<LlmToolSpec> tools) {
        ArrayNode array = JsonNodeSupport.arrayNode();
        for (LlmToolSpec tool : tools) {
            ObjectNode item = array.addObject();
            item.put("type", "function");
            ObjectNode function = item.putObject("function");
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.set("parameters", tool.parameterSchema());
        }
        return array;
    }

    private List<LlmToolCall> toolCalls(JsonNode source) {
        List<LlmToolCall> result = new ArrayList<>();
        if (source == null || !source.isArray()) return result;
        for (JsonNode item : source) {
            JsonNode function = item.path("function");
            result.add(new LlmToolCall(
                    item.path("id").asText(UUID.randomUUID().toString()),
                    function.path("name").asText(""),
                    function.path("arguments").asText("{}")));
        }
        return result;
    }

    private String completionsUrl() {
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat/completions")) return base;
        return base + "/chat/completions";
    }

    private boolean usesDeepSeekThinking() {
        String model = properties.getModel() == null ? "" : properties.getModel().toLowerCase();
        String base = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().toLowerCase();
        return model.contains("deepseek") || base.contains("deepseek");
    }

    private static org.springframework.http.client.ClientHttpRequestFactory requestFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofMinutes(2));
        return factory;
    }
}
