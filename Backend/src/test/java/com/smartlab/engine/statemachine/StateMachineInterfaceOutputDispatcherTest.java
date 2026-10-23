package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StateMachineInterfaceOutputDispatcherTest {

    @Test
    void submittingWhileHoldingDeviceLockDoesNotWaitForTaskLock() throws Exception {
        StateMachineInterfaceOutputDispatcher dispatcher = new StateMachineInterfaceOutputDispatcher();
        Object taskLock = new Object();
        java.util.concurrent.locks.ReentrantLock deviceLock = new java.util.concurrent.locks.ReentrantLock();
        java.util.concurrent.CountDownLatch returned = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch delivered = new java.util.concurrent.CountDownLatch(1);
        Thread producer = new Thread(() -> {
            deviceLock.lock();
            try {
            dispatcher.submitBatch(7L, List.of(request(command())), request -> {
                synchronized (taskLock) { delivered.countDown(); }
            });
            } finally { deviceLock.unlock(); }
            returned.countDown();
        });
        producer.setDaemon(true);
        try {
            synchronized (taskLock) {
                producer.start();
                org.junit.jupiter.api.Assertions.assertTrue(returned.await(2, java.util.concurrent.TimeUnit.SECONDS),
                        "producer must return without waiting for the workflow task lock");
                org.junit.jupiter.api.Assertions.assertTrue(deviceLock.tryLock(2, java.util.concurrent.TimeUnit.SECONDS));
                deviceLock.unlock();
            }
            org.junit.jupiter.api.Assertions.assertTrue(delivered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            producer.join(2000);
            dispatcher.stop();
        }
    }

    @Test
    void blockedDeviceDoesNotBlockOtherDevicesOrStartSecondConsumer() throws Exception {
        StateMachineInterfaceOutputDispatcher dispatcher = new StateMachineInterfaceOutputDispatcher();
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch other = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger second = new java.util.concurrent.atomic.AtomicInteger();
        try {
            dispatcher.submitBatch(7L, List.of(request(command())), request -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            org.junit.jupiter.api.Assertions.assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            dispatcher.submitBatch(7L, List.of(request(command())), request -> second.incrementAndGet());
            dispatcher.submitBatch(8L, List.of(request(command())), request -> other.countDown());
            org.junit.jupiter.api.Assertions.assertTrue(other.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, second.get());
            release.countDown();
            org.awaitility.Awaitility.await().until(() -> second.get() == 1);
        } finally { release.countDown(); dispatcher.stop(); }
    }

    @Test
    void failedDeliveryIsVisibleAndDoesNotReplayPartialSideEffects() {
        StateMachineInterfaceOutputDispatcher dispatcher = new StateMachineInterfaceOutputDispatcher();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        try {
            dispatcher.submitBatch(7L, List.of(request(command()), request(command())), request -> {
                calls.incrementAndGet();
                throw new IllegalStateException("database unavailable");
            });
            org.awaitility.Awaitility.await().until(() -> dispatcher.failedDevices().containsKey(7L));
            assertEquals(1, calls.get());
        } finally { dispatcher.stop(); }
    }

    @Test
    void ordersCompetingStateSignalsByExceptionCommandAndOperational() {
        StateMachineInterfaceOutputDispatcher dispatcher = new StateMachineInterfaceOutputDispatcher();
        List<String> sent = new java.util.concurrent.CopyOnWriteArrayList<>();

        dispatcher.submitBatch(7L, List.of(
                request(operation("Mode", "OPERATIONAL")),
                request(command()),
                request(operation("Exception", "EXCEPTION"))
        ), request -> sent.add(label(request.signal())));

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3)).untilAsserted(() -> assertEquals(List.of("EXCEPTION", "CMD_STATE", "OPERATIONAL"), sent));
        dispatcher.stop();
    }

    @Test
    void exceptionInsertedDuringDispatchPrecedesOnlySignalsNotYetSent() {
        StateMachineInterfaceOutputDispatcher dispatcher = new StateMachineInterfaceOutputDispatcher();
        List<String> sent = new java.util.concurrent.CopyOnWriteArrayList<>();

        dispatcher.submitBatch(7L, List.of(request(command()), request(operation("Mode", "OPERATIONAL"))), request -> {
            sent.add(label(request.signal()));
            if (sent.size() == 1) {
                dispatcher.submitBatch(7L, List.of(request(operation("Exception", "EXCEPTION"))),
                        inserted -> sent.add(label(inserted.signal())));
            }
        });

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3)).untilAsserted(() -> assertEquals(List.of("CMD_STATE", "EXCEPTION", "OPERATIONAL"), sent));
        dispatcher.stop();
    }

    private StateMachineInterfaceOutputDispatcher.OutputRequest request(ObjectNode signal) {
        return new StateMachineInterfaceOutputDispatcher.OutputRequest(
                "Interface_state_out", "STATE", signal, Map.of());
    }

    private ObjectNode command() {
        ObjectNode signal = JsonNodeSupport.objectNode();
        signal.put("signalName", "CMD_STATE");
        signal.putObject("payload").put("stateName", "RUNNING");
        return signal;
    }

    private ObjectNode operation(String regionName, String regionType) {
        ObjectNode signal = JsonNodeSupport.objectNode();
        signal.put("signalName", "OP_STATE");
        ObjectNode payload = signal.putObject("payload");
        payload.put("regionName", regionName);
        payload.put("regionType", regionType);
        payload.putArray("state").add("ACTIVE");
        return signal;
    }

    private String label(ObjectNode signal) {
        if ("CMD_STATE".equals(signal.path("signalName").asText())) return "CMD_STATE";
        return signal.path("payload").path("regionType").asText();
    }
}
