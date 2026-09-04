package com.smartlab.global.event;

/** Published after an Adapter index row is removed so MQTT subscriptions can be dropped. */
public record AdapterDeletedEvent(String adapterName) {
}
