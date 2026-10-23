package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/** 单个设备的 Interface_state_out 并发信号仲裁器。 */
@Component
public class StateMachineInterfaceOutputDispatcher {

    private final ConcurrentHashMap<Long, DeviceQueue> deviceQueues = new ConcurrentHashMap<>();
    private final AtomicLong sequences = new AtomicLong();
    private static final Logger log = LoggerFactory.getLogger(StateMachineInterfaceOutputDispatcher.class);
    private final ExecutorService workers = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "state-output");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void stop() {
        workers.shutdown();
    }

    public Map<Long, String> failedDevices() {
        Map<Long, String> failures = new LinkedHashMap<>();
        deviceQueues.forEach((id, queue) -> {
            queue.lock.lock();
            try {
                if (queue.failure != null) failures.put(id, queue.failure.toString());
            } finally { queue.lock.unlock(); }
        });
        return Map.copyOf(failures);
    }

    public void submitBatch(Long instanceId, List<OutputRequest> requests,
                            Consumer<OutputRequest> sender) {
        if (instanceId == null || requests == null || requests.isEmpty()) return;
        DeviceQueue deviceQueue = deviceQueues.computeIfAbsent(instanceId, ignored -> new DeviceQueue());
        List<OutputRequest> normalized = coalesceExceptionRegions(requests);
        boolean drain;
        deviceQueue.lock.lock();
        try {
            for (OutputRequest request : normalized) {
                deviceQueue.queue.add(new QueuedOutput(request, priority(request), sequences.incrementAndGet(), sender));
            }
            drain = !deviceQueue.draining && deviceQueue.failure == null;
            if (drain) deviceQueue.draining = true;
        } finally {
            deviceQueue.lock.unlock();
        }
        if (drain) {
            try {
                workers.execute(() -> drain(instanceId, deviceQueue));
            } catch (RuntimeException failure) {
                deviceQueue.lock.lock();
                try {
                    deviceQueue.draining = false;
                    deviceQueue.failure = failure;
                } finally {
                    deviceQueue.lock.unlock();
                }
                throw failure;
            }
        }
    }

    private void drain(Long instanceId, DeviceQueue deviceQueue) {
        while (true) {
            QueuedOutput next;
            deviceQueue.lock.lock();
            try {
                next = deviceQueue.queue.poll();
                if (next == null) {
                    deviceQueue.draining = false;
                    // Keep a stable queue identity: a producer may already hold this queue reference.
                    return;
                }
            } finally {
                deviceQueue.lock.unlock();
            }
            try {
                next.sender().accept(next.request());
            } catch (RuntimeException failure) {
                deviceQueue.lock.lock();
                try {
                    deviceQueue.draining = false;
                    deviceQueue.queue.add(next);
                    deviceQueue.failure = failure;
                } finally {
                    deviceQueue.lock.unlock();
                }
                // Delivery may have partially succeeded. Retain it for diagnosis rather than
                // automatically replaying listeners and possibly advancing a task twice.
                log.error("状态输出失败，设备队列已暂停，instanceId={}, signal={}", instanceId,
                        next.request().signal(), failure);
                return;
            }
        }
    }

    private List<OutputRequest> coalesceExceptionRegions(List<OutputRequest> requests) {
        Map<String, OutputRequest> exceptionRegions = new LinkedHashMap<>();
        List<OutputRequest> result = new ArrayList<>();
        for (OutputRequest request : requests) {
            if (!isException(request)) {
                result.add(request);
                continue;
            }
            String regionName = request.signal().path("payload").path("regionName").asText("");
            String key = request.interfaceName() + "::" + regionName;
            exceptionRegions.put(key, request);
        }
        result.addAll(exceptionRegions.values());
        return List.copyOf(result);
    }

    private int priority(OutputRequest request) {
        if (isException(request)) return 0;
        String signalName = request.signal().path("signalName").asText("");
        if ("CMD_STATE".equals(signalName)) return 1;
        if ("OP_STATE".equals(signalName)) return 2;
        return 3;
    }

    private boolean isException(OutputRequest request) {
        return "OP_STATE".equals(request.signal().path("signalName").asText(""))
                && "EXCEPTION".equals(request.signal().path("payload").path("regionType").asText(""));
    }

    public record OutputRequest(String interfaceName, String interfaceType, ObjectNode signal,
                                Map<String, Object> executionContext) {
        public OutputRequest {
            signal = signal.deepCopy();
            executionContext = executionContext == null ? Map.of() : Map.copyOf(executionContext);
        }

        @Override
        public ObjectNode signal() {
            return signal.deepCopy();
        }
    }

    private record QueuedOutput(OutputRequest request, int priority, long sequence,
                                Consumer<OutputRequest> sender) {
    }

    private static final class DeviceQueue {
        private RuntimeException failure;
        private final ReentrantLock lock = new ReentrantLock();
        private final PriorityQueue<QueuedOutput> queue = new PriorityQueue<>(
                Comparator.comparingInt(QueuedOutput::priority).thenComparingLong(QueuedOutput::sequence));
        private boolean draining;
    }
}
