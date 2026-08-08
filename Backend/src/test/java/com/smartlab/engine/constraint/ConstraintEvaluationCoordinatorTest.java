package com.smartlab.engine.constraint;

import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservableSnapshotReader;
import com.smartlab.engine.observation.ObservationSnapshot;
import com.smartlab.engine.observation.ObservationStatus;
import com.smartlab.engine.observation.SnapshotOrigin;
import com.smartlab.engine.observation.event.ObservableChangedEvent;
import com.smartlab.global.contract.ObservableObjectType;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.constraint.ConstraintRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConstraintEvaluationCoordinatorTest {

    @Test
    void eventsEvaluateAffectedRulesAndPeriodicScanDetectsMissedChanges() throws InterruptedException {
        ObservableKey temperature = new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE, 1L, null, null,
                null, null, "temperature", null);
        ObservableKey pressure = new ObservableKey(ObservableObjectType.DEVICE_ATTRIBUTE, 2L, null, null,
                null, null, "pressure", null);
        RuntimeConstraint temperatureRule = runtime("temperature", temperature);
        RuntimeConstraint pressureRule = runtime("pressure", pressure);
        ConstraintMonitoringPlan plan = new ConstraintMonitoringPlan(1, "model-hash", Instant.now(),
                Map.of(temperatureRule.key(), temperatureRule, pressureRule.key(), pressureRule),
                Map.of(temperature, Set.of(temperatureRule.key()), pressure, Set.of(pressureRule.key())));

        EffectiveConstraintModelCompiler compiler = mock(EffectiveConstraintModelCompiler.class);
        EffectiveConstraintModel model = new EffectiveConstraintModel(null, null, null, Instant.now(),
                1, "model-hash", List.of(), List.of(),
                JsonNodeSupport.objectNode().set("observableObjects", JsonNodeSupport.arrayNode()), plan);
        when(compiler.compileRuntime()).thenReturn(model);
        AtomicReference<Map<ObservableKey, ObservationSnapshot>> current = new AtomicReference<>(Map.of(
                temperature, snapshot(temperature, 20, 1), pressure, snapshot(pressure, 2, 1)));
        ObservableSnapshotReader snapshots = mock(ObservableSnapshotReader.class);
        when(snapshots.readBatch(any())).thenAnswer(invocation -> {
            Map<ObservableKey, ObservationSnapshot> all = current.get();
            Map<ObservableKey, ObservationSnapshot> result = new java.util.LinkedHashMap<>();
            for (Object value : (Iterable<?>) invocation.getArgument(0)) {
                ObservableKey key = (ObservableKey) value;
                if (all.containsKey(key)) result.put(key, all.get(key));
            }
            return result;
        });
        when(snapshots.readHistoryBatch(any(), any())).thenReturn(Map.of());
        ConstraintEngine engine = mock(ConstraintEngine.class);
        AtomicReference<CountDownLatch> evaluations = new AtomicReference<>(new CountDownLatch(2));
        doAnswer(invocation -> {
            evaluations.get().countDown();
            return null;
        }).when(engine).evaluate(any(RuntimeConstraint.class), anyMap(), anyMap());

        ConstraintEvaluationCoordinator coordinator = new ConstraintEvaluationCoordinator(compiler, snapshots, engine);
        try {
            coordinator.initialize();
            assertTrue(evaluations.get().await(2, TimeUnit.SECONDS));
            assertSame(model, coordinator.currentModel());
            assertSame(model.monitoringPlan(), coordinator.currentPlan());

            clearInvocations(engine);
            evaluations.set(new CountDownLatch(1));
            coordinator.observeChange(new ObservableChangedEvent(temperature, 2, Instant.now()));
            assertTrue(evaluations.get().await(2, TimeUnit.SECONDS));
            verify(engine).evaluate(eq(temperatureRule), anyMap(), anyMap());
            verify(engine, never()).evaluate(eq(pressureRule), anyMap(), anyMap());

            clearInvocations(engine);
            evaluations.set(new CountDownLatch(1));
            current.set(Map.of(temperature, snapshot(temperature, 20, 1), pressure, snapshot(pressure, 3, 2)));
            coordinator.requestPeriodicEvaluation();
            assertTrue(evaluations.get().await(2, TimeUnit.SECONDS));
            verify(engine).evaluate(eq(pressureRule), anyMap(), anyMap());
            verify(engine, never()).evaluate(eq(temperatureRule), anyMap(), anyMap());
        } finally {
            coordinator.shutdown();
        }
    }

    private RuntimeConstraint runtime(String name, ObservableKey key) {
        ConstraintRule rule = new ConstraintRule();
        rule.setId((long) name.hashCode());
        rule.setRuleName(name);
        rule.setExpression(name + " > 0");
        return new RuntimeConstraint(new RuntimeConstraintKey("GLOBAL", name, "device:" + key.deviceInstanceId(), "v1"),
                rule, Map.of(name, key), null, null, key.deviceInstanceId());
    }

    private ObservationSnapshot snapshot(ObservableKey key, int value, long revision) {
        Instant now = Instant.now();
        return new ObservationSnapshot(key, JsonNodeSupport.toNode(value), now, now, revision,
                ObservationStatus.VALID, SnapshotOrigin.LIVE);
    }
}
