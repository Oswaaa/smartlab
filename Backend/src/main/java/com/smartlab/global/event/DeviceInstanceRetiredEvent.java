package com.smartlab.global.event;

/** Published after a device instance is retired so live runtime caches can be released. */
public record DeviceInstanceRetiredEvent(Long deviceInstanceId) {
}
