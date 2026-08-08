package com.smartlab.engine.constraint;

import com.smartlab.engine.observation.ObservableKey;
import com.smartlab.engine.observation.ObservableSnapshotReader;
import com.smartlab.engine.observation.ObservationHistoryStore;
import com.smartlab.engine.observation.ObservationSnapshot;
import com.smartlab.engine.observation.event.ObservableChangedEvent;
import com.smartlab.engine.observation.event.ObservableTopologyChangedEvent;
import com.smartlab.global.event.ConstraintRulesChangedEvent;
import com.smartlab.global.event.TaskLifecycleObservationEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ConstraintEvaluationCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ConstraintEvaluationCoordinator.class);

    private final EffectiveConstraintModelCompiler compiler;
    private final ObservableSnapshotReader snapshots;
    private final ConstraintEngine engine;
    private final AtomicReference<ConstraintMonitoringPlan> plan = new AtomicReference<>(ConstraintMonitoringPlan.empty());
    private final AtomicReference<EffectiveConstraintModel> effectiveModel = new AtomicReference<>();
    private final ConcurrentHashMap<ObservableKey, Long> revisions = new ConcurrentHashMap<>();
    private final Set<ObservableKey> dirtyKeys = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean evaluationRequested = new AtomicBoolean();
    private final AtomicBoolean planRefreshRequested = new AtomicBoolean(true);
    private final AtomicBoolean fullEvaluationRequested = new AtomicBoolean(true);
    private final AtomicBoolean periodicScanRequested = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "constraint-evaluation");
        thread.setDaemon(true);
        return thread;
    });

    public ConstraintEvaluationCoordinator(EffectiveConstraintModelCompiler compiler,
                                           ObservableSnapshotReader snapshots,
                                           ConstraintEngine engine) {
        this.compiler = compiler;
        this.snapshots = snapshots;
        this.engine = engine;
    }

    @PostConstruct
    public void initialize() {
        requestEvaluation();
    }

    /** Periodic snapshot scan is a safety net for windows, missed events and recovery. */
    @Scheduled(fixedDelayString = "${smartlab.constraint.scan-interval-ms:200}")
    public void requestPeriodicEvaluation() {
        periodicScanRequested.set(true);
        requestEvaluation();
    }

    @EventListener
    public void observeChange(ObservableChangedEvent event) {
        dirtyKeys.add(event.key());
        requestEvaluation();
    }

    @EventListener
    public void observeRuleChange(ConstraintRulesChangedEvent ignored) {
        planRefreshRequested.set(true);
        fullEvaluationRequested.set(true);
        requestEvaluation();
    }

    @EventListener
    public void observeTopologyChange(ObservableTopologyChangedEvent ignored) {
        planRefreshRequested.set(true);
        fullEvaluationRequested.set(true);
        requestEvaluation();
    }

    @EventListener
    public void observeTaskChange(TaskLifecycleObservationEvent ignored) {
        planRefreshRequested.set(true);
        fullEvaluationRequested.set(true);
        requestEvaluation();
    }

    public ConstraintMonitoringPlan currentPlan() {
        return plan.get();
    }

    public EffectiveConstraintModel currentModel() {
        return effectiveModel.get();
    }

    private void requestEvaluation() {
        if (!evaluationRequested.compareAndSet(false, true)) return;
        worker.execute(this::drainRequests);
    }

    private void drainRequests() {
        try {
            do {
                evaluationRequested.set(false);
                if (planRefreshRequested.compareAndSet(true, false)) refreshPlan();
                evaluateOnce(fullEvaluationRequested.getAndSet(false), periodicScanRequested.getAndSet(false));
            } while (evaluationRequested.get());
        } catch (RuntimeException e) {
            log.error("约束检测失败", e);
        }
    }

    private void refreshPlan() {
        EffectiveConstraintModel updated = compiler.compileRuntime();
        effectiveModel.set(updated);
        ConstraintMonitoringPlan updatedPlan = updated.monitoringPlan();
        plan.set(updatedPlan);
        revisions.keySet().retainAll(updatedPlan.dependencies().keySet());
        dirtyKeys.addAll(updatedPlan.dependencies().keySet());
        fullEvaluationRequested.set(true);
        engine.retainRuntimeKeys(updatedPlan.constraints().keySet());
    }

    private void evaluateOnce(boolean fullEvaluation, boolean periodicScan) {
        ConstraintMonitoringPlan current = plan.get();
        if (current.constraints().isEmpty()) return;
        Set<ObservableKey> changed = drainDirtyKeys();
        Map<ObservableKey, ObservationSnapshot> observed = new HashMap<>();
        if (periodicScan) {
            observed.putAll(snapshots.readBatch(current.dependencies().keySet()));
            observed.forEach((key, snapshot) -> {
                Long previous = revisions.put(key, snapshot.revision());
                if (previous == null || previous.longValue() != snapshot.revision()) changed.add(key);
            });
        }
        Set<RuntimeConstraintKey> affected = new HashSet<>();
        if (fullEvaluation) affected.addAll(current.constraints().keySet());
        changed.forEach(key -> affected.addAll(current.dependencies().getOrDefault(key, Set.of())));
        if (periodicScan) current.constraints().forEach((key, constraint) -> {
            if (requiresClockTick(constraint)) affected.add(key);
        });
        if (affected.isEmpty()) return;

        Set<ObservableKey> required = new HashSet<>();
        Set<ObservableKey> historyRequired = new HashSet<>();
        affected.forEach(key -> {
            RuntimeConstraint constraint = current.constraints().get(key);
            if (constraint == null) return;
            required.addAll(constraint.observableBindings().values());
            if (usesHistory(constraint)) historyRequired.addAll(constraint.observableBindings().values());
        });
        Set<ObservableKey> missing = new HashSet<>(required);
        missing.removeAll(observed.keySet());
        observed.putAll(snapshots.readBatch(missing));
        observed.forEach((key, snapshot) -> revisions.put(key, snapshot.revision()));
        Map<ObservableKey, java.util.List<com.smartlab.engine.observation.ObservationSample>> histories =
                snapshots.readHistoryBatch(historyRequired,
                        Instant.now().minusSeconds(ObservationHistoryStore.DEFAULT_MAX_SECONDS));
        for (RuntimeConstraintKey key : affected) {
            RuntimeConstraint constraint = current.constraints().get(key);
            if (constraint != null) engine.evaluate(constraint, observed, histories);
        }
    }

    private boolean usesHistory(RuntimeConstraint constraint) {
        String expression = constraint.rule().getExpression();
        return expression != null && expression.matches("(?s).*\\b(delta|avg|rate)\\s*\\(.*");
    }

    private boolean requiresClockTick(RuntimeConstraint constraint) {
        return constraint.rule().getWindowSeconds() != null || usesHistory(constraint);
    }

    /**
     * Do not call clear() after copying: an event added between copy and clear
     * would otherwise be lost until the next periodic safety sweep.
     */
    private Set<ObservableKey> drainDirtyKeys() {
        Set<ObservableKey> changed = new HashSet<>();
        for (ObservableKey key : dirtyKeys) {
            if (dirtyKeys.remove(key)) changed.add(key);
        }
        return changed;
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdownNow();
    }
}
