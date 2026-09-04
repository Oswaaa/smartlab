package com.smartlab.management.entity.resource.device;

/** DEVICE_INSTANCES.instance_kind：PHYSICAL / VIRTUAL（租约会话） / TEMPORARY（画布走图）。 */
public final class DeviceInstanceKind {
    public static final String PHYSICAL = "PHYSICAL";
    public static final String VIRTUAL = "VIRTUAL";
    public static final String TEMPORARY = "TEMPORARY";

    private DeviceInstanceKind() {}

    public static String normalize(String kind) {
        if (kind == null || kind.isBlank()) return PHYSICAL;
        String normalized = kind.trim().toUpperCase();
        if (PHYSICAL.equals(normalized) || VIRTUAL.equals(normalized) || TEMPORARY.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("不支持的设备实例种类: " + kind);
    }

    public static boolean isPhysical(DeviceInstances instance) {
        return instance != null && PHYSICAL.equals(normalize(instance.getInstanceKind()));
    }

    public static boolean isVirtual(DeviceInstances instance) {
        return instance != null && VIRTUAL.equals(normalize(instance.getInstanceKind()));
    }

    public static boolean isTemporary(DeviceInstances instance) {
        return instance != null && TEMPORARY.equals(normalize(instance.getInstanceKind()));
    }

    public static boolean allowsManualControl(DeviceInstances instance) {
        return isPhysical(instance) || isVirtual(instance);
    }

    /** 约束引擎与活体属性快照只覆盖真机和租约虚拟机，不含画布 TEMPORARY。 */
    public static boolean isConstraintVisible(DeviceInstances instance) {
        return allowsManualControl(instance);
    }
}
