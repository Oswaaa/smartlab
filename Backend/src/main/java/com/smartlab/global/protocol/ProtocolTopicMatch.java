package com.smartlab.global.protocol;

import java.util.Map;

public record ProtocolTopicMatch(String topicName, Map<String, String> variables) {
}
