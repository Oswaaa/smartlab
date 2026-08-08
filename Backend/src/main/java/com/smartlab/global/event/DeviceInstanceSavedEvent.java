package com.smartlab.global.event;

/** Published inside the instance transaction; runtime listeners consume it after commit. */
public record DeviceInstanceSavedEvent(Long deviceInstanceId, Long deviceModelId, boolean created) {
}
