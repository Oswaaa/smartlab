package com.smartlab.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineSendActionEvent;
import com.smartlab.global.event.AdapterDeletedEvent;
import com.smartlab.global.event.AdapterLeaseResultEvent;
import com.smartlab.global.event.DeviceInstanceDeletedEvent;
import com.smartlab.global.event.DeviceInstanceRetiredEvent;
import com.smartlab.global.event.DeviceInstanceSavedEvent;
import com.smartlab.global.protocol.ProtocolDictionaryService;
import com.smartlab.global.protocol.ProtocolTopicMatch;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.resource.adapter.AdapterRouteDTO;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import com.smartlab.management.service.protocol.AdapterPayloadMapperService;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public class MqttAdapterMessagingService implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttAdapterMessagingService.class);

    private final AdapterIndexService adapterIndexService;
    private final AdapterPayloadMapperService protocolMapperService;
    private final ProtocolDictionaryService protocolDictionaryService;
    private final Executor executor;
    private DeviceInstancesMapper deviceInstancesMapper;
    private InProcessAdapterSimulator adapterSimulator;
    private ApplicationEventPublisher events;
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

    @Autowired(required = false)
    public void setDeviceInstancesMapper(DeviceInstancesMapper deviceInstancesMapper) {
        this.deviceInstancesMapper = deviceInstancesMapper;
    }

    @Autowired(required = false)
    public void setAdapterSimulator(InProcessAdapterSimulator adapterSimulator) {
        this.adapterSimulator = adapterSimulator;
    }

    @Autowired(required = false)
    public void setApplicationEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
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
            reconcileSubscriptions();
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
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> item : pendingAdapterRegistrations.values()) {
            String name = (String) item.get("adapterName");
            if (name != null && !adapterIndexService.hasCompletedRegistration(name.trim())) {
                list.add(item);
            }
        }
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
        syncRuntimeSubscriptions();
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
        if (deviceInstancesMapper != null) {
            DeviceInstances instance = deviceInstancesMapper.selectById(event.instanceId());
            if (instance == null) {
                throw new IllegalStateException("设备实例不存在，无法下发 Adapter 指令: " + event.instanceId());
            }
            String kind = instance.getInstanceKind();
            if (DeviceInstanceKind.TEMPORARY.equals(kind)) {
                if (adapterSimulator == null) {
                    throw new IllegalStateException("流程模拟缺少 Adapter 模拟器，无法下发临时实例指令");
                }
                adapterSimulator.handleSendAction(event);
                return;
            }
            if (!DeviceInstanceKind.PHYSICAL.equals(kind) && !DeviceInstanceKind.VIRTUAL.equals(kind)) {
                throw new IllegalStateException("无法识别的设备实例种类，拒绝出站: " + kind);
            }
        }
        ObjectNode commandMessage;
        if ("CMD_START".equals(event.signalName())) {
            commandMessage = protocolMapperService.buildCommandMessage(
                    String.valueOf(event.instanceId()), event.capabilityName(), event.messageId(), event.parameters());
        } else if ("CMD_ABORT".equals(event.signalName())) {
            commandMessage = protocolMapperService.buildCommandMessage(
                    String.valueOf(event.instanceId()), event.capabilityName(), event.messageId(), event.parameters());
        } else {
            throw new IllegalArgumentException("不支持的 Adapter 下行信号: " + event.signalName());
        }
        publishCommand(commandMessage);
    }

    public void publishLeaseRequest(JsonNode payload) {
        protocolDictionaryService.validateDefinition("LeaseRequestFormat", payload);
        String adapterName = payload.path("adapterName").asText();
        String topic = protocolDictionaryService.resolveMqttTopic("leaseRequestTopic", Map.of("adapterName", adapterName));
        publishJson(topic, payload);
    }

    public void publishCommand(ObjectNode commandMessage) {
        if (commandMessage == null || commandMessage.path("topic").asText("").isBlank()) {
            throw new IllegalArgumentException("MQTT command message 缺少 topic");
        }
        try {
            publishJson(commandMessage.path("topic").asText(), commandMessage.path("payload"));
        } catch (IllegalStateException e) {
            throw new IllegalStateException("MQTT 指令发布失败: " + e.getMessage(), e);
        }
    }

    private void publishJson(String topic, JsonNode payload) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("MQTT topic 不能为空");
        }
        try {
            connectIfNeeded();
            byte[] body = JsonNodeSupport.MAPPER.writeValueAsBytes(payload == null ? JsonNodeSupport.objectNode() : payload);
            client.publish(topic, body, 1, false);
        } catch (Exception e) {
            markError(e);
            throw new IllegalStateException("MQTT 发布失败: " + e.getMessage(), e);
        }
    }

    public void subscribeRegistrationTopics() {
        syncRuntimeSubscriptions();
    }

    public void trySubscribeDevicePointTopics(String adapterName, String devicePoint) {
        syncRuntimeSubscriptions();
    }

    public void subscribeDevicePointTopics(String adapterName, String devicePoint) {
        syncRuntimeSubscriptions();
    }

    public void syncRuntimeSubscriptions() {
        try {
            reconcileSubscriptions();
        } catch (Exception e) {
            log.warn("MQTT 订阅同步失败: {}", e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleDeviceInstanceSaved(DeviceInstanceSavedEvent event) {
        syncRuntimeSubscriptions();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleDeviceInstanceRetired(DeviceInstanceRetiredEvent event) {
        syncRuntimeSubscriptions();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleDeviceInstanceDeleted(DeviceInstanceDeletedEvent event) {
        syncRuntimeSubscriptions();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAdapterDeleted(AdapterDeletedEvent event) {
        syncRuntimeSubscriptions();
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        executor.execute(this::syncRuntimeSubscriptions);
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
            reconcileSubscriptions();
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
            if (adapterIndexService.hasCompletedRegistration(adapterName)) {
                pendingAdapterRegistrations.remove(adapterName);
                emitRegistrationEvent("pending_snapshot", pendingAdapterRegistrations());
                log.info("收到 Adapter 注册请求，但 {} 已在数据库中完成注册，忽略该待审核请求", adapterName);
                return;
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
        String adapterName = variables.get("adapterName");
        if ("heartbeatTopic".equals(topicMatch.topicName())) {
            protocolDictionaryService.validateDefinition("AdapterHeartbeat", payload);
            adapterIndexService.heartbeat(adapterName, payload.path("status").asText("ONLINE"));
            return;
        }
        if ("telemetryTopic".equals(topicMatch.topicName())) {
            protocolDictionaryService.validateDefinition("TelemetryMessageFormat", payload);
            validatePayloadIdentity(variables, payload);
            String formatType = payload.path("formatType").asText("SINGLE").trim().toUpperCase();
            if ("BATCH".equals(formatType)) {
                protocolMapperService.applyBatchTelemetry(adapterName, variables.get("devicePoint"), payload);
            } else {
                protocolMapperService.applyTelemetry(adapterName, variables.get("devicePoint"), payload);
            }
            return;
        }
        if ("eventTopic".equals(topicMatch.topicName())) {
            protocolDictionaryService.validateDefinition("EventMessageFormat", payload);
            validatePayloadIdentity(variables, payload);
            protocolMapperService.applyAdapterEvent(adapterName, variables.get("devicePoint"), payload);
            return;
        }
        if ("leaseResultTopic".equals(topicMatch.topicName())) {
            protocolDictionaryService.validateDefinition("LeaseResultFormat", payload);
            if (!adapterName.equals(payload.path("adapterName").asText(""))) {
                throw new IllegalArgumentException("MQTT topic 与 payload adapterName 不一致: topic="
                        + adapterName + ", payload=" + payload.path("adapterName").asText(""));
            }
            if (events == null) {
                log.warn("收到租约结果但未装配事件总线, leaseId={}", payload.path("leaseId"));
                return;
            }
            events.publishEvent(new AdapterLeaseResultEvent(payload));
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
    private void reconcileSubscriptions() {
        Set<String> desired = desiredRuntimeTopics();
        try {
            connectIfNeeded();
        } catch (Exception e) {
            markError(e);
            throw new IllegalStateException("MQTT 订阅同步失败: " + e.getMessage(), e);
        }
        applySubscriptionDiff(desired);
    }

    Set<String> desiredRuntimeTopics() {
        Set<String> desired = new LinkedHashSet<>();
        desired.add(registerTopic());
        Set<String> adapterNames = new LinkedHashSet<>();
        for (AdapterIndex adapter : adapterIndexService.list()) {
            if (adapter == null || adapter.getAdapterName() == null || adapter.getAdapterName().isBlank()) {
                continue;
            }
            if (!adapterIndexService.hasCompletedRegistration(adapter)) {
                continue;
            }
            adapterNames.add(adapter.getAdapterName());
        }
        Map<String, AdapterRouteDTO> routes = protocolMapperService.refreshAdapterRouteTable();
        for (AdapterRouteDTO route : routes.values()) {
            String adapterName = route.getBoundAdapterName();
            String devicePoint = route.getBoundDevicePoint();
            if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) {
                continue;
            }
            adapterNames.add(adapterName);
            desired.add(protocolDictionaryService.resolveMqttTopic("telemetryTopic", Map.of(
                    "adapterName", adapterName, "devicePoint", devicePoint)));
            desired.add(protocolDictionaryService.resolveMqttTopic("eventTopic", Map.of(
                    "adapterName", adapterName, "devicePoint", devicePoint)));
        }
        for (String adapterName : adapterNames) {
            desired.add(protocolDictionaryService.resolveMqttTopic("heartbeatTopic", Map.of("adapterName", adapterName)));
            desired.add(protocolDictionaryService.resolveMqttTopic("leaseResultTopic", Map.of("adapterName", adapterName)));
        }
        return desired;
    }

    TopicSubscriptionDiff diffAgainst(Set<String> desired) {
        Set<String> toUnsubscribe = new LinkedHashSet<>(subscribedTopics);
        toUnsubscribe.removeAll(desired);
        Set<String> toSubscribe = new LinkedHashSet<>(desired);
        toSubscribe.removeAll(subscribedTopics);
        return new TopicSubscriptionDiff(Set.copyOf(toSubscribe), Set.copyOf(toUnsubscribe));
    }

    void replaceTrackedSubscriptions(Collection<String> topics) {
        subscribedTopics.clear();
        if (topics != null) {
            subscribedTopics.addAll(topics);
        }
    }

    Set<String> trackedSubscriptions() {
        return Set.copyOf(subscribedTopics);
    }

    record TopicSubscriptionDiff(Set<String> toSubscribe, Set<String> toUnsubscribe) {
    }

    private synchronized void applySubscriptionDiff(Set<String> desired) {
        if (!isConnected()) {
            return;
        }
        TopicSubscriptionDiff diff = diffAgainst(desired);
        for (String topic : diff.toUnsubscribe()) {
            try {
                client.unsubscribe(topic);
                subscribedTopics.remove(topic);
                log.info("MQTT 已退订: {}", topic);
            } catch (Exception e) {
                log.warn("MQTT 退订失败, topic={}, reason={}", topic, e.getMessage());
            }
        }
        for (String topic : desired) {
            try {
                client.subscribe(topic, 1);
                subscribedTopics.add(topic);
            } catch (Exception e) {
                log.warn("MQTT 订阅失败, topic={}, reason={}", topic, e.getMessage());
            }
        }
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
