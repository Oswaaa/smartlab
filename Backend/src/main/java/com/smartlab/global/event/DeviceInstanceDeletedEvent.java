package com.smartlab.global.event;

/** Published after a device instance is physically deleted so MQTT subscriptions can be dropped. */
public record DeviceInstanceDeletedEvent(Long deviceInstanceId) {
}
