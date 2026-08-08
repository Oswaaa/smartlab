package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

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
            drain = !deviceQueue.draining;
            if (drain) deviceQueue.draining = true;
        } finally {
            deviceQueue.lock.unlock();
        }
        if (drain) drain(instanceId, deviceQueue);
    }

    private void drain(Long instanceId, DeviceQueue deviceQueue) {
        while (true) {
            QueuedOutput next;
            deviceQueue.lock.lock();
            try {
                next = deviceQueue.queue.poll();
                if (next == null) {
                    deviceQueue.draining = false;
                    if (deviceQueue.queue.isEmpty()) deviceQueues.remove(instanceId, deviceQueue);
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
                } finally {
                    deviceQueue.lock.unlock();
                }
                throw failure;
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
        private final ReentrantLock lock = new ReentrantLock();
        private final PriorityQueue<QueuedOutput> queue = new PriorityQueue<>(
                Comparator.comparingInt(QueuedOutput::priority).thenComparingLong(QueuedOutput::sequence));
        private boolean draining;
    }
}
