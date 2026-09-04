package com.smartlab.global.contract;

public enum MqttTopic {
    REGISTER("registerTopic", "smartlab/adapter/register"),
    HEARTBEAT("heartbeatTopic", "smartlab/adapter/{adapterName}/heartbeat"),
    COMMAND("commandTopic", "smartlab/adapter/{adapterName}/{devicePoint}/command"),
    TELEMETRY("telemetryTopic", "smartlab/adapter/{adapterName}/{devicePoint}/telemetry"),
    EVENT("eventTopic", "smartlab/adapter/{adapterName}/{devicePoint}/event"),
    LEASE_REQUEST("leaseRequestTopic", "smartlab/adapter/{adapterName}/leaserequest"),
    LEASE_RESULT("leaseResultTopic", "smartlab/adapter/{adapterName}/leaseresult");

    private final String key;
    private final String pattern;

    MqttTopic(String key, String pattern) {
        this.key = key;
        this.pattern = pattern;
    }

    public String key() {
        return key;
    }

    public String pattern() {
        return pattern;
    }

    public static String requireSafeSegment(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        String text = value.trim();
        if (text.indexOf('/') >= 0 || text.indexOf('+') >= 0 || text.indexOf('#') >= 0) {
            throw new IllegalArgumentException(field + " 不能包含 MQTT 分隔符或通配符: " + text);
        }
        return text;
    }
}
