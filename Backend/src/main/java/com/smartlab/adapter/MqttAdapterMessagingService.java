package com.smartlab.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineSendActionEvent;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.protocol.ProtocolTopicMatch;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.resource.adapter.AdapterRouteDTO;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * MQTT bridge for the SmartLab adapter protocol.
 */
@Service
/**
 * MQTT长连接物理接入与心跳监听底座服务。实现设备端原始数据订阅接收与下行控制指令的发布编译。
 */
public class MqttAdapterMessagingService implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttAdapterMessagingService.class);

    private final AdapterIndexService adapterIndexService;
    private final AdapterPayloadMapperService protocolMapperService;
    private final ProtocolDictionaryService protocolDictionaryService;
    private final Executor executor;
    private final Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, Object>> pendingAdapterRegistrations = new ConcurrentHashMap<>();
    private final List<SseEmitter> registrationEmitters = new CopyOnWriteArrayList<>();

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String broker;

    @Value("${mqtt.client-id:smartlab_backend_2.0}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.auto-start:true}")
    private boolean autoStart;


    private volatile MqttClient client;
    private volatile String connectionStatus = "NOT_STARTED";
    private volatile String lastError;
    private volatile Instant lastAttemptAt;
    private volatile Instant lastConnectedAt;

    public MqttAdapterMessagingService(AdapterIndexService adapterIndexService,
            AdapterPayloadMapperService protocolMapperService,
            ProtocolDictionaryService protocolDictionaryService,
            @Qualifier("workflowTaskExecutor") Executor executor) {
        this.adapterIndexService = adapterIndexService;
        this.protocolMapperService = protocolMapperService;
        this.protocolDictionaryService = protocolDictionaryService;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() {
        if (!autoStart) {
            connectionStatus = "DISABLED";
            return;
        }
        executor.execute(() -> tryStartRegistrationListener(true));
    }

    @Scheduled(fixedDelayString = "${mqtt.reconnect-delay-ms:15000}", initialDelayString = "${mqtt.reconnect-delay-ms:15000}")
    public void retryRegistrationListener() {
        if (!autoStart) {
            return;
        }
        if (!isConnected()) {
            tryStartRegistrationListener(false);
            return;
        }
        try {
            subscribeKnownDevicePointTopics();
        } catch (Exception e) {
            log.debug("MQTT 订阅保活失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception e) {
            log.warn("断开 MQTT 连接失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> statusSnapshot() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean connected = isConnected();
        status.put("enabled", autoStart);
        status.put("connected", connected);
        status.put("status", connected ? "CONNECTED" : normalizeDisconnectedStatus());
        status.put("broker", broker);
        status.put("clientId", clientId);
        status.put("usernameConfigured", username != null && !username.isBlank());
        status.put("registerTopic", registerTopic());
        status.put("lastError", lastError);
        status.put("lastAttemptAt", lastAttemptAt == null ? null : lastAttemptAt.toString());
        status.put("lastConnectedAt", lastConnectedAt == null ? null : lastConnectedAt.toString());
        ArrayList<String> topics = new ArrayList<>(subscribedTopics);
        Collections.sort(topics);
        status.put("subscribedTopics", topics);
        status.put("pendingRegistrationCount", pendingAdapterRegistrations.size());
        return status;
    }

    public List<Map<String, Object>> pendingAdapterRegistrations() {
        List<Map<String, Object>> list = new ArrayList<>(pendingAdapterRegistrations.values());
        list.sort((left, right) -> String.valueOf(right.get("receivedAt")).compareTo(String.valueOf(left.get("receivedAt"))));
        return list;
    }

    public SseEmitter subscribePendingRegistrationEvents() {
        SseEmitter emitter = new SseEmitter(0L);
        registrationEmitters.add(emitter);
        emitter.onCompletion(() -> registrationEmitters.remove(emitter));
        emitter.onTimeout(() -> registrationEmitters.remove(emitter));
        emitter.onError(error -> registrationEmitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("pending_snapshot").data(pendingAdapterRegistrations()));
        } catch (IOException | IllegalStateException e) {
            registrationEmitters.remove(emitter);
        }
        return emitter;
    }

    public AdapterIndex completePendingAdapterRegistration(String adapterName) {
        return completePendingAdapterRegistration(adapterName, null);
    }

    public AdapterIndex completePendingAdapterRegistration(String adapterName, JsonNode reviewedConfig) {
        if (adapterName == null || adapterName.isBlank()) {
            throw new IllegalArgumentException("Adapter 标识名不能为空");
        }
        String key = adapterName.trim();
        Map<String, Object> pending = pendingAdapterRegistrations.get(key);
        if (pending == null) {
            throw new IllegalArgumentException("没有待确认的 Adapter 注册请求: " + adapterName);
        }
        Object payload = pending.get("payload");
        Map<String, Object> registerPayload = JsonNodeSupport.MAPPER.convertValue(payload, new TypeReference<>() {
        });
        if (reviewedConfig != null && !reviewedConfig.isNull()) {
            registerPayload.put("parsedConfig", reviewedConfig);
        }
        AdapterIndex adapter = adapterIndexService.register(registerPayload);
        pendingAdapterRegistrations.remove(key);
        emitRegistrationEvent("pending_snapshot", pendingAdapterRegistrations());
        if (adapter != null) {
            trySubscribeAdapterHeartbeat(adapter.getAdapterName());
        }
        return adapter;
    }

    public void discardPendingAdapterRegistration(String adapterName) {
        if (adapterName != null) {
            pendingAdapterRegistrations.remove(adapterName.trim());
            emitRegistrationEvent("pending_snapshot", pendingAdapterRegistrations());
        }
    }

    private void emitRegistrationEvent(String eventName, Object data) {
        for (SseEmitter emitter : registrationEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                registrationEmitters.remove(emitter);
            }
        }
    }

    public Map<String, Object> reconnectRegistrationListener() {
        tryStartRegistrationListener(true);
        return statusSnapshot();
    }

    @EventListener
    public void handleStateMachineSendAction(StateMachineSendActionEvent event) {
        if (!"ADAPTER".equals(event.interfaceType())) {
            return;
        }
        ObjectNode commandMessage;
        if ("CMD_START".equals(event.signalName())) {
            commandMessage = protocolMapperService.buildCommandMessage(
                    String.valueOf(event.instanceId()), event.capabilityName(), event.messageId(), event.parameters());
        } else if ("CMD_ABORT".equals(event.signalName())) {
            commandMessage = protocolMapperService.buildAbortMessage(
                    String.valueOf(event.instanceId()), event.messageId());
        } else {
            throw new IllegalArgumentException("不支持的 Adapter 下行信号: " + event.signalName());
        }
        publishCommand(commandMessage);
    }

    public void publishCommand(ObjectNode commandMessage) {
        if (commandMessage == null || commandMessage.path("topic").asText("").isBlank()) {
            throw new IllegalArgumentException("MQTT command message 缺少 topic");
        }
        try {
            connectIfNeeded();
            String topic = commandMessage.path("topic").asText();
            byte[] payload = JsonNodeSupport.MAPPER.writeValueAsBytes(commandMessage.path("payload"));
            client.publish(topic, payload, 1, false);
        } catch (Exception e) {
            markError(e);
            throw new IllegalStateException("MQTT 指令发布失败: " + e.getMessage(), e);
        }
    }

    public void subscribeRegistrationTopics() {
        subscribeTopics(registerTopic());
    }

    public void trySubscribeDevicePointTopics(String adapterName, String devicePoint) {
        if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) {
            return;
        }
        try {
            subscribeDevicePointTopics(adapterName, devicePoint);
        } catch (Exception e) {
            log.warn("MQTT 设备点订阅暂未成功, adapter={}, point={}, reason={}", adapterName, devicePoint, e.getMessage());
        }
    }

    public void subscribeDevicePointTopics(String adapterName, String devicePoint) {
        if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) {
            throw new IllegalArgumentException("Adapter 标识和设备点字段不能为空");
        }
        subscribeTopics(
                protocolDictionaryService.resolveMqttTopic("telemetryTopic", Map.of(
                        "adapterName", adapterName,
                        "devicePoint", devicePoint)),
                protocolDictionaryService.resolveMqttTopic("eventTopic", Map.of(
                        "adapterName", adapterName,
                        "devicePoint", devicePoint)));
    }

    @Override
    public void connectionLost(Throwable cause) {
        connectionStatus = "DISCONNECTED";
        lastError = cause == null ? "unknown" : cause.getMessage();
        log.warn("MQTT 连接断开: {}", lastError);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String text = new String(message.getPayload(), StandardCharsets.UTF_8);
            JsonNode payload = text.isBlank() ? JsonNodeSupport.objectNode() : JsonNodeSupport.MAPPER.readTree(text);
            routeIncomingMessage(topic, payload);
        } catch (Exception e) {
            log.warn("处理 MQTT 消息失败, topic={}, reason={}", topic, e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op
    }

    private void tryStartRegistrationListener(boolean warnOnFailure) {
        try {
            subscribeRegistrationTopics();
            subscribeKnownDevicePointTopics();
        } catch (Exception e) {
            if (warnOnFailure) {
                log.warn("MQTT 未连接，系统将继续启动并在首页展示状态: {}", e.getMessage());
            }
        }
    }

    private void routeIncomingMessage(String topic, JsonNode payload) {
        ProtocolTopicMatch topicMatch = protocolDictionaryService.matchMqttTopic(topic)
                .orElseThrow(() -> new IllegalArgumentException("未知 MQTT topic: " + topic));
        if ("registerTopic".equals(topicMatch.topicName())) {
            protocolDictionaryService.validateDefinition("AdapterRegisterRequest", payload);
            Map<String, Object> body = JsonNodeSupport.MAPPER.convertValue(payload, new TypeReference<>() {
            });
            ObjectNode manifest = adapterIndexService.previewRegisterPayload(body);
            String adapterName = manifest.path("adapterName").asText("").trim();
            if (adapterName.isBlank()) {
                throw new IllegalArgumentException("Adapter 注册报文缺少 adapterName");
            }
            body.put("adapterName", adapterName);
            Map<String, Object> pending = new LinkedHashMap<>();
            pending.put("adapterName", adapterName);
            pending.put("rawConfigFormat", body.getOrDefault("rawConfigFormat", "JSON"));
            pending.put("timestamp", body.get("timestamp"));
            pending.put("receivedAt", Instant.now().toString());
            int categoryCount = manifest.path("deviceCategories").size();
            int devicePointCount = 0;
            for (JsonNode categoryNode : manifest.path("deviceCategories")) {
                devicePointCount += categoryNode.path("devicePoints").size();
            }
            pending.put("parsedConfig", manifest);
            pending.put("categoryCount", categoryCount);
            pending.put("templateCount", categoryCount);
            pending.put("devicePointCount", devicePointCount);
            pending.put("payload", body);
            pendingAdapterRegistrations.put(adapterName, pending);
            emitRegistrationEvent("adapter_register_request", pending);
            emitRegistrationEvent("pending_snapshot", pendingAdapterRegistrations());
            log.info("收到 Adapter 注册请求，已进入待确认队列: {}", adapterName);
            return;
        }

        Map<String, String> variables = topicMatch.variables();
        if ("heartbeatTopic".equals(topicMatch.topicName())) {
            protocolDictionaryService.validateDefinition("AdapterHeartbeat", payload);
            adapterIndexService.heartbeat(variables.get("adapterName"), payload.path("status").asText("ONLINE"));
            return;
        }
        if ("telemetryTopic".equals(topicMatch.topicName())) {
            protocolDictionaryService.validateDefinition("TelemetryMessageFormat", payload);
            validatePayloadIdentity(variables, payload);
            protocolMapperService.applyTelemetry(variables.get("adapterName"), variables.get("devicePoint"), payload);
            return;
        }
        if ("eventTopic".equals(topicMatch.topicName())) {
            protocolDictionaryService.validateDefinition("EventMessageFormat", payload);
            validatePayloadIdentity(variables, payload);
            protocolMapperService.applyAdapterEvent(variables.get("adapterName"), variables.get("devicePoint"), payload);
        }
    }

    private void validatePayloadIdentity(Map<String, String> topicVariables, JsonNode payload) {
        String topicAdapter = topicVariables.getOrDefault("adapterName", "");
        String topicPoint = topicVariables.getOrDefault("devicePoint", "");
        String payloadAdapter = payload.path("adapterName").asText("");
        String payloadPoint = payload.path("devicePoint").asText("");
        if (!topicAdapter.equals(payloadAdapter) || !topicPoint.equals(payloadPoint)) {
            throw new IllegalArgumentException("MQTT topic 与 payload 身份不一致: topic="
                    + topicAdapter + "/" + topicPoint + ", payload=" + payloadAdapter + "/" + payloadPoint);
        }
    }
    private void trySubscribeAdapterHeartbeat(String adapterName) {
        if (adapterName == null || adapterName.isBlank()) {
            return;
        }
        try {
            subscribeTopics(protocolDictionaryService.resolveMqttTopic("heartbeatTopic", Map.of("adapterName", adapterName)));
        } catch (Exception e) {
            log.warn("MQTT Adapter 心跳订阅暂未成功, adapter={}, reason={}", adapterName, e.getMessage());
        }
    }

    private void subscribeKnownDevicePointTopics() {
        Map<String, AdapterRouteDTO> routes = protocolMapperService.refreshAdapterRouteTable();
        List<String> topics = new ArrayList<>();
        Set<String> adapterNames = new java.util.LinkedHashSet<>();
        for (AdapterIndex adapter : adapterIndexService.list()) {
            if (adapter.getParsedConfig() != null && adapter.getAdapterName() != null && !adapter.getAdapterName().isBlank()) {
                adapterNames.add(adapter.getAdapterName());
            }
        }
        for (AdapterRouteDTO route : routes.values()) {
            String adapterName = route.getBoundAdapterName();
            String devicePoint = route.getBoundDevicePoint();
            if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) continue;
            adapterNames.add(adapterName);
            topics.add(protocolDictionaryService.resolveMqttTopic("telemetryTopic", Map.of(
                    "adapterName", adapterName, "devicePoint", devicePoint)));
            topics.add(protocolDictionaryService.resolveMqttTopic("eventTopic", Map.of(
                    "adapterName", adapterName, "devicePoint", devicePoint)));
        }
        for (String adapterName : adapterNames) {
            topics.add(protocolDictionaryService.resolveMqttTopic("heartbeatTopic", Map.of("adapterName", adapterName)));
        }
        subscribeTopics(topics.toArray(String[]::new));
    }

    private synchronized void connectIfNeeded() throws MqttException {
        if (client != null && client.isConnected()) {
            connectionStatus = "CONNECTED";
            return;
        }
        lastAttemptAt = Instant.now();
        connectionStatus = "CONNECTING";
        try {
            if (client == null) {
                client = new MqttClient(broker, clientId, new MemoryPersistence());
                client.setCallback(this);
            }
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            if (username != null && !username.isBlank()) {
                options.setUserName(username);
            }
            if (password != null && !password.isBlank()) {
                options.setPassword(password.toCharArray());
            }
            client.connect(options);
            connectionStatus = "CONNECTED";
            lastConnectedAt = Instant.now();
            lastError = null;
        } catch (MqttException e) {
            markError(e);
            throw e;
        }
    }

    private synchronized void subscribeTopics(String... topics) {
        try {
            for (String topic : topics) {
                if (topic != null && !topic.isBlank()) {
                    subscribedTopics.add(topic);
                }
            }
            connectIfNeeded();
            for (String topic : subscribedTopics) {
                client.subscribe(topic, 1);
            }
        } catch (Exception e) {
            markError(e);
            throw new IllegalStateException("MQTT 订阅失败: " + e.getMessage(), e);
        }
    }

    private boolean isConnected() {
        return client != null && client.isConnected();
    }

    private String normalizeDisconnectedStatus() {
        if ("CONNECTED".equals(connectionStatus) || "CONNECTING".equals(connectionStatus)) {
            return "DISCONNECTED";
        }
        return connectionStatus;
    }

    private void markError(Exception e) {
        connectionStatus = "ERROR";
        lastError = e == null ? "unknown" : e.getMessage();
    }

    private String registerTopic() {
        return protocolDictionaryService.resolveMqttTopic("registerTopic", Map.of());
    }
}
