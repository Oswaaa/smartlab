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
 * 遥测离开 MQTT 回调后的入库队列。队列本身不丢弃报文；写库失败的报文按原顺序放回队头，
 * 避免新到的采样先入库。非法报文只记录日志，与回调里原来捕获异常后的处理一致。
 */
final class TelemetryIngestQueue {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestQueue.class);
    private static final long RETRY_PAUSE_MS = 500L;

    private final AdapterPayloadMapperService protocolMapperService;
    private final LinkedBlockingDeque<QueuedTelemetry> queue = new LinkedBlockingDeque<>();
    private volatile boolean running;
    private Thread thread;

    TelemetryIngestQueue(AdapterPayloadMapperService protocolMapperService) {
        this.protocolMapperService = protocolMapperService;
    }

    void enqueue(String adapterName, String devicePoint, JsonNode payload) {
        queue.add(new QueuedTelemetry(adapterName, devicePoint, payload));
    }

    void start() {
        if (thread != null) {
            return;
        }
        running = true;
        thread = new Thread(this::ingestLoop, "telemetry-ingest");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        running = false;
        Thread current = thread;
        if (current != null) {
            current.interrupt();
            try {
                current.join(2_000L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
        thread = null;
        flushAvailable();
    }

    void flushAvailable() {
        List<QueuedTelemetry> burst = new ArrayList<>();
        queue.drainTo(burst);
        if (!burst.isEmpty()) {
            dispatch(burst);
        }
    }

    private void ingestLoop() {
        while (running) {
            try {
                QueuedTelemetry first = queue.poll(RETRY_PAUSE_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                List<QueuedTelemetry> burst = new ArrayList<>();
                burst.add(first);
                queue.drainTo(burst);
                if (dispatch(burst) && running) {
                    Thread.sleep(RETRY_PAUSE_MS);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean dispatch(List<QueuedTelemetry> burst) {
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
