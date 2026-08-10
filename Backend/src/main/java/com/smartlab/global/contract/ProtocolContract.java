package com.smartlab.global.contract;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProtocolContract {

    private static final Map<String, List<String>> ENUMERATIONS = enumerations();
    private static final Map<String, SignalPayloadPolicy> SIGNAL_PAYLOAD_POLICIES = signalPayloadPolicies();
    private static final Map<String, String> MQTT_TOPICS = mqttTopicPatterns();

    private ProtocolContract() {
    }

    public static List<String> enumValues(String definitionName) {
        List<String> values = ENUMERATIONS.get(definitionName);
        if (values == null) {
            throw new IllegalArgumentException("协议定义不存在或不是枚举: " + definitionName);
        }
        return values;
    }

    public static boolean hasEnumeration(String definitionName) {
        return ENUMERATIONS.containsKey(definitionName);
    }

    public static Map<String, String> mqttTopics() {
        return MQTT_TOPICS;
    }

    public static SignalPayloadPolicy payloadPolicy(String signalName) {
        SignalPayloadPolicy policy = SIGNAL_PAYLOAD_POLICIES.get(signalName);
        if (policy == null) {
            throw new IllegalArgumentException("协议未声明系统信号: " + signalName);
        }
        return policy;
    }

    public static boolean isSystemSignal(String signalName) {
        return signalName != null && SIGNAL_PAYLOAD_POLICIES.containsKey(signalName);
    }

    private static Map<String, List<String>> enumerations() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("DataType", names(DataType.values()));
        values.put("CommunicationProtocol", names(CommunicationProtocol.values()));
        values.put("WorkflowNodeSignal", names(WorkflowNodeSignal.values()));
        values.put("WorkflowControlSignal", names(WorkflowControlSignal.values()));
        values.put("ManualControlSignal", names(ManualControlSignal.values()));
        values.put("ConstraintControlSignal", names(ConstraintControlSignal.values()));
        values.put("AdapterOutboundSignal", names(AdapterOutboundSignal.values()));
        values.put("StatusSignal", names(StatusSignal.values()));
        values.put("AdapterRegisterRawConfigFormat", List.of("INI", "JSON"));
        return Map.copyOf(values);
    }

    private static Map<String, SignalPayloadPolicy> signalPayloadPolicies() {
        Map<String, SignalPayloadPolicy> policies = new LinkedHashMap<>();
        policies.put(WorkflowNodeSignal.ACTIVE.name(), SignalPayloadPolicy.NONE);
        policies.put(WorkflowNodeSignal.SUBFLOW_COMPLETED.name(), SignalPayloadPolicy.NONE);
        policies.put(WorkflowControlSignal.WF_EXECUTE_START.name(), SignalPayloadPolicy.COMMAND);
        policies.put(WorkflowControlSignal.WF_EXECUTE_ABORT.name(), SignalPayloadPolicy.NONE);
        policies.put(ManualControlSignal.MANUAL_EXECUTE_START.name(), SignalPayloadPolicy.COMMAND);
        policies.put(ManualControlSignal.MANUAL_EXECUTE_ABORT.name(), SignalPayloadPolicy.NONE);
        policies.put(ConstraintControlSignal.CONSTRAINT_EXECUTE.name(), SignalPayloadPolicy.CONSTRAINT_EXECUTE);
        policies.put(ConstraintControlSignal.CONSTRAINT_ABORT.name(), SignalPayloadPolicy.NONE);
        policies.put(AdapterOutboundSignal.CMD_START.name(), SignalPayloadPolicy.COMMAND);
        policies.put(AdapterOutboundSignal.CMD_ABORT.name(), SignalPayloadPolicy.NONE);
        policies.put(StatusSignal.CMD_STATE.name(), SignalPayloadPolicy.COMMAND_STATE);
        policies.put(StatusSignal.OP_STATE.name(), SignalPayloadPolicy.OPERATION_STATE);
        return Map.copyOf(policies);
    }

    private static Map<String, String> mqttTopicPatterns() {
        Map<String, String> topics = new LinkedHashMap<>();
        for (MqttTopic topic : MqttTopic.values()) {
            topics.put(topic.key(), topic.pattern());
        }
        return Map.copyOf(topics);
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
