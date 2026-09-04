package com.smartlab.management.entity.resource.adapter;

/** VIRTUAL_LEASE.status：租赁中 / 可调度 / 释放中 / 已释放 / 失败。 */
public final class VirtualLeaseStatus {
    public static final String LEASING = "LEASING";
    public static final String ACTIVE = "ACTIVE";
    public static final String RELEASING = "RELEASING";
    public static final String RELEASED = "RELEASED";
    public static final String FAILED = "FAILED";

    private VirtualLeaseStatus() {}

    public static String normalize(String status) {
        if (status == null || status.isBlank()) {
            return LEASING;
        }
        String normalized = status.trim().toUpperCase();
        if (LEASING.equals(normalized) || ACTIVE.equals(normalized) || RELEASING.equals(normalized)
                || RELEASED.equals(normalized) || FAILED.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("不支持的虚拟租约状态: " + status);
    }
}
