package com.smartlab.global.contract;

public enum MqttTopic {
    REGISTER("registerTopic", "smartlab/adapter/register"),
    HEARTBEAT("heartbeatTopic", "smartlab/adapter/{adapterName}/heartbeat"),
    COMMAND("commandTopic", "smartlab/adapter/{adapterName}/{devicePoint}/command"),
    TELEMETRY("telemetryTopic", "smartlab/adapter/{adapterName}/{devicePoint}/telemetry"),
    EVENT("eventTopic", "smartlab/adapter/{adapterName}/{devicePoint}/event");

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
}
