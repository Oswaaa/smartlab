package com.smartlab.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * 遥测离开 MQTT 回调后按 Adapter 和设备点分片入库。同一设备点始终落在同一队列，
 * 写库失败的报文按原顺序放回该分片队头。非法报文只记录日志，与回调原有处理一致。
 */
final class TelemetryIngestQueue {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestQueue.class);
    private static final long RETRY_PAUSE_MS = 500L;
    private static final int MAX_BURST_MESSAGES = 256;
    private static final int DEFAULT_SHARDS = 4;

    private final AdapterPayloadMapperService protocolMapperService;
    private final List<LinkedBlockingDeque<QueuedTelemetry>> queues;
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    TelemetryIngestQueue(AdapterPayloadMapperService protocolMapperService) {
        this(protocolMapperService, DEFAULT_SHARDS);
    }

    TelemetryIngestQueue(AdapterPayloadMapperService protocolMapperService, int shardCount) {
        this.protocolMapperService = protocolMapperService;
        if (shardCount < 1) {
            throw new IllegalArgumentException("Telemetry shard count must be positive");
        }
        queues = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            queues.add(new LinkedBlockingDeque<>());
        }
    }

    void enqueue(String adapterName, String devicePoint, JsonNode payload) {
        queues.get(shardFor(adapterName, devicePoint)).add(new QueuedTelemetry(adapterName, devicePoint, payload));
    }

    int shardFor(String adapterName, String devicePoint) {
        return Math.floorMod(31 * adapterName.hashCode() + devicePoint.hashCode(), queues.size());
    }

    synchronized void start() {
        if (!threads.isEmpty()) {
            return;
        }
        running = true;
        for (int i = 0; i < queues.size(); i++) {
            LinkedBlockingDeque<QueuedTelemetry> queue = queues.get(i);
            Thread thread = new Thread(() -> ingestLoop(queue), "telemetry-ingest-" + i);
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }
    }

    synchronized void stop() {
        running = false;
        for (Thread thread : threads) {
            thread.interrupt();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        for (Thread thread : threads) {
            try {
                long remaining = deadline - System.nanoTime();
                if (remaining > 0) {
                    thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (threads.stream().anyMatch(Thread::isAlive)) {
            log.warn("遥测写入线程仍在执行，跳过并发清空队列以避免重复或乱序写入");
            return;
        }
        threads.clear();
        for (LinkedBlockingDeque<QueuedTelemetry> queue : queues) {
            while (!queue.isEmpty() && !flushOneBurst(queue)) {
                // 成功写完一批后继续清空本分片；写库失败则停止，避免无限重试。
            }
        }
    }

    void flushAvailable() {
        for (LinkedBlockingDeque<QueuedTelemetry> queue : queues) {
            flushOneBurst(queue);
        }
    }

    int pendingCount() {
        return queues.stream().mapToInt(LinkedBlockingDeque::size).sum();
    }

    private boolean flushOneBurst(LinkedBlockingDeque<QueuedTelemetry> queue) {
        List<QueuedTelemetry> burst = new ArrayList<>();
        queue.drainTo(burst, MAX_BURST_MESSAGES);
        if (!burst.isEmpty()) {
            return dispatch(burst, queue);
        }
        return false;
    }

    private void ingestLoop(LinkedBlockingDeque<QueuedTelemetry> queue) {
        while (running) {
            try {
                QueuedTelemetry first = queue.poll(RETRY_PAUSE_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                List<QueuedTelemetry> burst = new ArrayList<>();
                burst.add(first);
                queue.drainTo(burst, MAX_BURST_MESSAGES - 1);
                if (dispatch(burst, queue) && running) {
                    Thread.sleep(RETRY_PAUSE_MS);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean dispatch(List<QueuedTelemetry> burst, LinkedBlockingDeque<QueuedTelemetry> queue) {
        Map<String, List<QueuedTelemetry>> byDevice = new LinkedHashMap<>();
        for (QueuedTelemetry item : burst) {
            byDevice.computeIfAbsent(item.adapterName + "\n" + item.devicePoint, key -> new ArrayList<>()).add(item);
        }
        List<QueuedTelemetry> retry = new ArrayList<>();
        for (List<QueuedTelemetry> deviceMessages : byDevice.values()) {
            List<QueuedTelemetry> singles = new ArrayList<>();
            for (QueuedTelemetry item : deviceMessages) {
                if (isBatch(item.payload)) {
                    retry.addAll(writeSingles(singles));
                    singles = new ArrayList<>();
                    retry.addAll(writeBatch(item));
                } else {
                    singles.add(item);
                }
            }
            retry.addAll(writeSingles(singles));
        }
        if (retry.isEmpty()) {
            return false;
        }
        for (int i = retry.size() - 1; i >= 0; i--) {
            queue.addFirst(retry.get(i));
        }
        return true;
    }

    private List<QueuedTelemetry> writeSingles(List<QueuedTelemetry> singles) {
        if (singles.isEmpty()) {
            return List.of();
        }
        QueuedTelemetry first = singles.get(0);
        List<JsonNode> messages = new ArrayList<>(singles.size());
        for (QueuedTelemetry item : singles) {
            messages.add(item.payload);
        }
        try {
            protocolMapperService.applyTelemetrySamples(first.adapterName, first.devicePoint, messages);
            return List.of();
        } catch (IllegalArgumentException | IllegalStateException error) {
            log.warn("遥测采样无法入库 {}/{} 共{}条: {}", first.adapterName, first.devicePoint, messages.size(), error.getMessage());
            return List.of();
        } catch (RuntimeException error) {
            log.warn("遥测入库失败，已放回队头 {}/{} 共{}条: {}", first.adapterName, first.devicePoint, messages.size(), error.getMessage());
            return singles;
        }
    }

    private List<QueuedTelemetry> writeBatch(QueuedTelemetry item) {
        try {
            protocolMapperService.applyBatchTelemetry(item.adapterName, item.devicePoint, item.payload);
            return List.of();
        } catch (IllegalArgumentException | IllegalStateException error) {
            log.warn("批量遥测无法入库 {}/{}: {}", item.adapterName, item.devicePoint, error.getMessage());
            return List.of();
        } catch (RuntimeException error) {
            log.warn("批量遥测入库失败，已放回队头 {}/{}: {}", item.adapterName, item.devicePoint, error.getMessage());
            return List.of(item);
        }
    }

    private static boolean isBatch(JsonNode payload) {
        return "BATCH".equals(payload.path("formatType").asText("SINGLE").trim().toUpperCase());
    }

    private record QueuedTelemetry(String adapterName, String devicePoint, JsonNode payload) {
    }
}
