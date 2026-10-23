package com.smartlab.adapter;

import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TelemetryIngestQueueTest {

    @Test
    void oneDeviceCanWriteWhileAnotherIsBlockedWithoutReorderingItsSamples() throws Exception {
        AdapterPayloadMapperService mapper = mock(AdapterPayloadMapperService.class);
        TelemetryIngestQueue queue = new TelemetryIngestQueue(mapper, 2);
        assertNotEquals(queue.shardFor("plc", "point-1"), queue.shardFor("plc", "point-2"));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch otherDeviceWritten = new CountDownLatch(1);
        CountDownLatch sameDeviceSecondWritten = new CountDownLatch(1);
        AtomicInteger pointOneWrites = new AtomicInteger();
        doAnswer(invocation -> {
            String point = invocation.getArgument(1);
            if ("point-1".equals(point)) {
                if (pointOneWrites.incrementAndGet() == 1) {
                    firstEntered.countDown();
                    releaseFirst.await(5, TimeUnit.SECONDS);
                } else {
                    sameDeviceSecondWritten.countDown();
                }
            } else {
                otherDeviceWritten.countDown();
            }
            return null;
        }).when(mapper).applyTelemetrySamples(any(), any(), any());

        queue.start();
        try {
            queue.enqueue("plc", "point-1", sample(1));
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
            queue.enqueue("plc", "point-1", sample(2));
            queue.enqueue("plc", "point-2", sample(3));

            assertTrue(otherDeviceWritten.await(2, TimeUnit.SECONDS));
            assertFalse(sameDeviceSecondWritten.await(200, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertTrue(sameDeviceSecondWritten.await(2, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            queue.stop();
        }
    }

    private static com.fasterxml.jackson.databind.JsonNode sample(long timestamp) {
        var node = JsonNodeSupport.objectNode();
        node.put("timestamp", timestamp);
        node.putObject("telemetryData").put("value", timestamp);
        return node;
    }
}
