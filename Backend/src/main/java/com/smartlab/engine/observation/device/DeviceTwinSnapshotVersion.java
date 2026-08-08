package com.smartlab.engine.observation.device;

/** Lightweight metadata used to skip copying unchanged attribute snapshots. */
public record DeviceTwinSnapshotVersion(
        Long deviceInstanceId,
        Long deviceModelId,
        long revision
) {
}
