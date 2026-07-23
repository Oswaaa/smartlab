package com.smartlab.management.entity.resource.device;

/** Database values and predicates for the device-instance asset lifecycle. */
public final class DeviceInstanceLifecycle {
    public static final String IN_USE = "使用中";
    public static final String RETIRED = "已注销";

    private DeviceInstanceLifecycle() {}

    public static boolean isUsable(DeviceInstances instance) {
        return instance != null && IN_USE.equals(instance.getLifecycleStatus());
    }
}
