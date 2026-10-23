package com.smartlab.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Keeps only the newest pending heartbeat for each adapter while database writes run off the MQTT callback. */
final class HeartbeatIngestQueue {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatIngestQueue.class);
    private final AdapterIndexService adapterIndexService;
    private final ConcurrentHashMap<String, HeartbeatSnapshot> latest = new ConcurrentHashMap<>();
    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingQueue<String> pending = new LinkedBlockingQueue<>();
    private volatile boolean running;
    private Thread thread;

    HeartbeatIngestQueue(AdapterIndexService adapterIndexService) {
        this.adapterIndexService = adapterIndexService;
    }

    void enqueue(String adapterName, JsonNode payload) {
        latest.put(adapterName, new HeartbeatSnapshot(payload, OffsetDateTime.now()));
        if (scheduled.add(adapterName)) {
            pending.offer(adapterName);
        }
    }

    synchronized void start() {
        if (thread != null) {
            return;
        }
        running = true;
        thread = new Thread(this::ingestLoop, "heartbeat-ingest");
        thread.setDaemon(true);
        thread.start();
    }

    synchronized void stop() {
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
        latest.clear();
        pending.clear();
        scheduled.clear();
    }

    void flushAvailable() {
        String adapterName;
        while ((adapterName = pending.poll()) != null) {
            process(adapterName);
        }
    }

    int pendingAdapterCount() {
        return latest.size();
    }

    private void ingestLoop() {
        while (running) {
            try {
                String adapterName = pending.poll(500L, TimeUnit.MILLISECONDS);
                if (adapterName != null) {
                    process(adapterName);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void process(String adapterName) {
        HeartbeatSnapshot snapshot = latest.remove(adapterName);
        try {
            if (snapshot != null) {
                adapterIndexService.heartbeat(adapterName, snapshot.payload(), snapshot.receivedAt());
            }
        } catch (RuntimeException error) {
            log.warn("心跳入库失败, adapter={}, reason={}", adapterName, error.getMessage());
        } finally {
            scheduled.remove(adapterName);
            if (latest.containsKey(adapterName) && scheduled.add(adapterName)) {
                pending.offer(adapterName);
            }
        }
    }

    private record HeartbeatSnapshot(JsonNode payload, OffsetDateTime receivedAt) {
    }
}
