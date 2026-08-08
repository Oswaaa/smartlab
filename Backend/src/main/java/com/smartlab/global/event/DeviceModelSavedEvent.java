package com.smartlab.global.event;

/** Published inside the model transaction; runtime listeners consume it after commit. */
public record DeviceModelSavedEvent(Long deviceModelId) {
}
