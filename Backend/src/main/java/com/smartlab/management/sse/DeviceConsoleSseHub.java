package com.smartlab.management.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineInterfaceSignalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 设备控制台实时事件流 SSE 集中调度管理器。
 * 订阅状态机事件总线，直通下发给前端调试终端。
 */
@Component
public class DeviceConsoleSseHub {

    private static final Logger log = LoggerFactory.getLogger(DeviceConsoleSseHub.class);

    /** 不设过期。空闲由心跳维持，断线由发送失败关闭，浏览器据此重连。 */
    private static final long NO_TIMEOUT = 0L;

    private final Map<Long, List<SseEmitter>> emittersByInstanceId = new ConcurrentHashMap<>();
    private final List<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();

    /**
     * 为指定设备实例注册 SSE 连接。主路径是 {@link #registerGlobal()}：登录后前端建一条全局流。
     * 本方法仅保留按实例订阅，控制台 UI 仍在实例抽屉中展示。
     */
    public SseEmitter register(Long instanceId) {
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId 不能为空");
        }
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        List<SseEmitter> list = emittersByInstanceId.computeIfAbsent(instanceId, id -> new CopyOnWriteArrayList<>());
        for (SseEmitter old : list) {
            try {
                old.complete();
            } catch (Exception ignored) {
            }
        }
        list.clear();
        list.add(emitter);

        Runnable cleanup = () -> {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByInstanceId.remove(instanceId, list);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("instanceId", instanceId, "status", "CONNECTED", "timestamp", System.currentTimeMillis())));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    /**
     * 注册全局 SSE 终端连接（全系统只需 1 条连接，接收所有设备的状态机信号）。
     */
    public SseEmitter registerGlobal() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        globalEmitters.add(emitter);

        Runnable cleanup = () -> globalEmitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("status", "CONNECTED", "timestamp", System.currentTimeMillis())));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    /**
     * 监听状态机底层信号事件，实时推送到前端控制台终端。
     * 必须先于工作流转发器执行：转发器会写远程库并可能抛错，Spring 默认同线程顺序通知，后面的监听器会被跳过。
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void handleStateMachineSignal(StateMachineInterfaceSignalEvent event) {
        if (event == null || event.instanceId() == null) return;
        Long instanceId = event.instanceId();
        List<SseEmitter> list = emittersByInstanceId.get(instanceId);
        boolean hasInstanceEmitters = list != null && !list.isEmpty();
        boolean hasGlobalEmitters = !globalEmitters.isEmpty();
        if (!hasInstanceEmitters && !hasGlobalEmitters) return;

        ObjectNode signal = event.signal();
        String signalName = signal != null ? signal.path("signalName").asText("") : "";
        JsonNode payload = signal != null ? signal.path("payload") : null;
        Map<String, Object> execCtx = event.executionContext() == null ? Map.of() : event.executionContext();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("instanceId", instanceId);
        String stateName = "CMD_STATE".equals(signalName) && payload != null ? payload.path("stateName").asText("") : "";
        data.put("stateName", stateName);
        data.put("capabilityName", execCtx.getOrDefault("capabilityName", ""));
        data.put("messageId", payload != null && payload.has("messageId") ? payload.path("messageId").asText() : String.valueOf(execCtx.getOrDefault("messageId", "")));
        data.put("timestamp", payload != null && payload.has("timestamp") ? payload.path("timestamp").asLong() : System.currentTimeMillis());
        if (execCtx.containsKey("parameters")) {
            data.put("parameters", execCtx.get("parameters"));
        }
        if (payload != null && payload.has("regionName")) {
            data.put("regionName", payload.path("regionName").asText());
            data.put("regionState", payload.path("state"));
        }

        if (hasInstanceEmitters) {
            for (SseEmitter emitter : list) {
                send(instanceId, list, emitter, "signal", data);
            }
        }

        if (hasGlobalEmitters) {
            for (SseEmitter emitter : globalEmitters) {
                send(null, globalEmitters, emitter, "signal", data);
            }
        }
    }

    /** 空闲时也发出字节，浏览器才能发现连接已死并重连。 */
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void heartbeat() {
        Map<String, Object> data = Map.of("timestamp", System.currentTimeMillis());
        for (SseEmitter emitter : List.copyOf(globalEmitters)) {
            send(null, globalEmitters, emitter, "ping", data);
        }
        for (Map.Entry<Long, List<SseEmitter>> entry : emittersByInstanceId.entrySet()) {
            for (SseEmitter emitter : List.copyOf(entry.getValue())) {
                send(entry.getKey(), entry.getValue(), emitter, "ping", data);
            }
        }
    }

    private void send(Long instanceId, List<SseEmitter> owner, SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception error) {
            log.debug("设备控制台连接发送失败，关闭以触发重连: {}", error.toString());
            drop(instanceId, owner, emitter);
        }
    }

    private void drop(Long instanceId, List<SseEmitter> owner, SseEmitter emitter) {
        owner.remove(emitter);
        if (instanceId != null && owner.isEmpty()) {
            emittersByInstanceId.remove(instanceId, owner);
        }
        try {
            emitter.completeWithError(new IOException("设备控制台连接已失效"));
        } catch (Exception ignored) {
        }
    }
}
