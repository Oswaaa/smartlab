package com.smartlab.engine.statemachine;

import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotRegistry;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 状态机内部独立轮询设备属性快照的内置约束监测器。 */
@Service
public class IntrinsicConstraintMonitor {

    private static final Logger log = LoggerFactory.getLogger(IntrinsicConstraintMonitor.class);

    private final DeviceTwinSnapshotRegistry snapshots;
    private final IntrinsicConstraintPlanRegistry plans;
    private final StateMachineEngine stateMachineEngine;

    public IntrinsicConstraintMonitor(DeviceTwinSnapshotRegistry snapshots,
                                      IntrinsicConstraintPlanRegistry plans,
                                      StateMachineEngine stateMachineEngine) {
        this.snapshots = snapshots;
        this.plans = plans;
        this.stateMachineEngine = stateMachineEngine;
    }

    @Scheduled(fixedDelayString = "${smartlab.state-machine.intrinsic-scan-interval-ms:100}")
    public void scan() {
        for (DeviceTwinSnapshotVersion version : snapshots.snapshotVersions().values()) {
            if (!plans.needsEvaluation(version)) continue;
            try {
                DeviceTwinSnapshot snapshot = snapshots.snapshot(version.deviceInstanceId());
                if (snapshot == null || !plans.needsEvaluation(snapshot)) continue;
                if (snapshot.attributes() == null || !snapshot.attributes().isObject()) continue;
                stateMachineEngine.evaluateIntrinsicConstraints(snapshot);
                plans.markEvaluated(snapshot.deviceInstanceId(), snapshot.revision());
            } catch (RuntimeException failure) {
                log.warn("内置约束轮询失败, instanceId={}", version.deviceInstanceId(), failure);
            }
        }
    }
}
