package com.smartlab.engine.statemachine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.observation.device.DeviceTwinSnapshot;
import com.smartlab.engine.observation.device.DeviceTwinSnapshotVersion;
import com.smartlab.global.event.DeviceInstanceDeletedEvent;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.global.event.DeviceInstanceSavedEvent;
import com.smartlab.global.event.DeviceModelSavedEvent;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Compiled, in-memory execution plans used by the state machine's intrinsic-constraint monitor. */
@Service
public class IntrinsicConstraintPlanRegistry {

    private final ConcurrentHashMap<Long, IntrinsicConstraintPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> evaluatedRevisions = new ConcurrentHashMap<>();
    private final DeviceInstancesMapper instancesMapper;
    private final DeviceModelsMapper modelsMapper;

    public IntrinsicConstraintPlanRegistry(DeviceInstancesMapper instancesMapper,
                                           DeviceModelsMapper modelsMapper) {
        this.instancesMapper = instancesMapper;
        this.modelsMapper = modelsMapper;
    }

    @PostConstruct
    public void restoreRuntimePlans() {
        plans.clear();
        evaluatedRevisions.clear();
        for (DeviceInstances instance : instancesMapper.selectList(
                Wrappers.<DeviceInstances>lambdaQuery()
                        .eq(DeviceInstances::getLifecycleStatus, DeviceInstanceLifecycle.IN_USE))) {
            refresh(instance.getId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInstanceSaved(DeviceInstanceSavedEvent event) {
        if (event != null) refresh(event.deviceInstanceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleModelSaved(DeviceModelSavedEvent event) {
        if (event == null || event.deviceModelId() == null) return;
        for (DeviceInstances instance : instancesMapper.selectList(
                Wrappers.<DeviceInstances>lambdaQuery()
                        .eq(DeviceInstances::getDeviceModelId, event.deviceModelId())
                        .eq(DeviceInstances::getLifecycleStatus, DeviceInstanceLifecycle.IN_USE))) {
            refresh(instance.getId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInstanceRetired(DeviceInstanceRetiredEvent event) {
        if (event != null) remove(event.deviceInstanceId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleInstanceDeleted(DeviceInstanceDeletedEvent event) {
        if (event != null) remove(event.deviceInstanceId());
    }

    public IntrinsicConstraintPlan require(Long instanceId) {
        IntrinsicConstraintPlan plan = instanceId == null ? null : plans.get(instanceId);
        if (plan == null) {
            throw new IllegalStateException("设备实例没有可用的内置约束运行计划: " + instanceId);
        }
        return plan;
    }

    public IntrinsicConstraintPlan get(Long instanceId) {
        return instanceId == null ? null : plans.get(instanceId);
    }

    public IntrinsicConstraintPlan plan(Long instanceId) {
        return get(instanceId);
    }

    public boolean needsEvaluation(DeviceTwinSnapshot snapshot) {
        return snapshot != null && needsEvaluation(new DeviceTwinSnapshotVersion(
                snapshot.deviceInstanceId(), snapshot.deviceModelId(), snapshot.revision()));
    }

    public boolean needsEvaluation(DeviceTwinSnapshotVersion version) {
        if (version == null || version.deviceInstanceId() == null) return false;
        IntrinsicConstraintPlan plan = plans.get(version.deviceInstanceId());
        if (plan == null || !plan.deviceModelId().equals(version.deviceModelId())) return false;
        Long evaluated = evaluatedRevisions.get(version.deviceInstanceId());
        return evaluated == null || version.revision() > evaluated;
    }

    public void markEvaluated(Long instanceId, long revision) {
        if (instanceId != null && plans.containsKey(instanceId)) {
            evaluatedRevisions.merge(instanceId, revision, Math::max);
        }
    }

    void refresh(Long instanceId) {
        if (instanceId == null) return;
        DeviceInstances instance = instancesMapper.selectById(instanceId);
        if (!DeviceInstanceLifecycle.isUsable(instance)) {
            remove(instanceId);
            return;
        }
        DeviceModels model = modelsMapper.selectById(instance.getDeviceModelId());
        if (model == null) {
            throw new IllegalStateException("设备实例引用的设备模型不存在: " + instance.getDeviceModelId());
        }
        plans.put(instanceId, compile(instance, model));
        evaluatedRevisions.remove(instanceId);
    }

    void remove(Long instanceId) {
        if (instanceId == null) return;
        plans.remove(instanceId);
        evaluatedRevisions.remove(instanceId);
    }

    int size() {
        return plans.size();
    }

    private IntrinsicConstraintPlan compile(DeviceInstances instance, DeviceModels model) {
        Map<String, String> exceptionRegions = new LinkedHashMap<>();
        JsonNode regions = model.getOpState() == null ? null : model.getOpState().path("regions");
        if (regions != null && regions.isArray()) {
            for (JsonNode region : regions) {
                if (!"EXCEPTION".equals(region.path("regionType").asText(""))) continue;
                String regionName = region.path("regionName").asText("");
                for (JsonNode state : region.path("states")) {
                    String stateName = state.path("stateName").asText("");
                    if (!regionName.isBlank() && !stateName.isBlank()) {
                        exceptionRegions.put(stateName, regionName);
                    }
                }
            }
        }

        List<CompiledIntrinsicConstraint> constraints = new ArrayList<>();
        JsonNode definitions = model.getIntrinsicConstraint();
        if (definitions != null && definitions.isArray()) {
            for (JsonNode definition : definitions) {
                String attributeName = definition.path("objectAttributeName").asText("");
                String operator = definition.path("operator").asText("");
                String violationStateName = definition.path("violationStateName").asText("");
                JsonNode boundaryNode = definition.get("boundaryValue");
                String regionName = exceptionRegions.get(violationStateName);
                if (attributeName.isBlank() || operator.isBlank() || regionName == null
                        || boundaryNode == null || !boundaryNode.isNumber()) {
                    throw new IllegalStateException("设备模型包含无法编译的内置约束: " + model.getId());
                }
                constraints.add(new CompiledIntrinsicConstraint(attributeName, operator,
                        boundaryNode.asDouble(), violationStateName, regionName));
            }
        }
        return new IntrinsicConstraintPlan(instance, model, List.copyOf(constraints), Map.copyOf(exceptionRegions));
    }

    public record IntrinsicConstraintPlan(DeviceInstances instance,
                                          DeviceModels model,
                                          List<CompiledIntrinsicConstraint> constraints,
                                          Map<String, String> exceptionRegions) {
        public Long deviceInstanceId() {
            return instance.getId();
        }

        public Long deviceModelId() {
            return model.getId();
        }

        public String exceptionRegion(String stateName) {
            return exceptionRegions.get(stateName);
        }

        public List<CompiledIntrinsicConstraint> constraintsForState(String stateName) {
            return constraints.stream()
                    .filter(constraint -> constraint.violationStateName().equals(stateName))
                    .toList();
        }
    }

    public record CompiledIntrinsicConstraint(String attributeName,
                                              String operator,
                                              double boundaryValue,
                                              String violationStateName,
                                              String regionName) {
    }
}
