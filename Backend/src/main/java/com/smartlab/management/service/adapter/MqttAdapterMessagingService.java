package com.smartlab.management.service.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.StateMachineSendActionEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.dto.AdapterRouteDTO;
import com.smartlab.management.entity.AdapterIndex;
import com.smartlab.management.service.db.resource.device.AdapterIndexService;
import com.smartlab.management.service.protocol.DeviceProtocolMapperService;
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

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * MQTT bridge for the SmartLab adapter protocol.
 */
@Service
public class MqttAdapterMessagingService implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttAdapterMessagingService.class);

    private final AdapterIndexService adapterIndexService;
    private final DeviceProtocolMapperService protocolMapperService;
    private final Executor executor;
    private final Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();

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

    @Value("${mqtt.register-topic:smartlab/adapter/register}")
    private String registerTopic;

    @Value("${mqtt.adapter-heartbeat-topic-pattern:smartlab/adapter/{adapterName}/heartbeat}")
    private String adapterHeartbeatTopicPattern;

    @Value("${mqtt.device-telemetry-topic-pattern:smartlab/adapter/{adapterName}/{devicePoint}/telemetry}")
    private String deviceTelemetryTopicPattern;

    @Value("${mqtt.device-event-topic-pattern:smartlab/adapter/{adapterName}/{devicePoint}/event}")
    private String deviceEventTopicPattern;

    private volatile MqttClient client;
    private volatile String connectionStatus = "NOT_STARTED";
    private volatile String lastError;
    private volatile Instant lastAttemptAt;
    private volatile Instant lastConnectedAt;

    public MqttAdapterMessagingService(AdapterIndexService adapterIndexService,
                                       DeviceProtocolMapperService protocolMapperService,
                                       @Qualifier("workflowTaskExecutor") Executor executor) {
        this.adapterIndexService = adapterIndexService;
        this.protocolMapperService = protocolMapperService;
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
        status.put("registerTopic", registerTopic);
        status.put("lastError", lastError);
        status.put("lastAttemptAt", lastAttemptAt == null ? null : lastAttemptAt.toString());
        status.put("lastConnectedAt", lastConnectedAt == null ? null : lastConnectedAt.toString());
        ArrayList<String> topics = new ArrayList<>(subscribedTopics);
        Collections.sort(topics);
        status.put("subscribedTopics", topics);
        return status;
    }

    public Map<String, Object> reconnectRegistrationListener() {
        tryStartRegistrationListener(true);
        return statusSnapshot();
    }

    @EventListener
    public void handleStateMachineSendAction(StateMachineSendActionEvent event) {
        if (!"Interface_adapter_out".equals(event.interfaceName())) {
            return;
        }
        ObjectNode commandMessage = protocolMapperService.buildCommandMessage(
                String.valueOf(event.instanceId()),
                event.commandId(),
                event.parameters()
        );
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
        subscribeTopics(registerTopic);
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
                topic(deviceTelemetryTopicPattern, adapterName, devicePoint),
                topic(deviceEventTopicPattern, adapterName, devicePoint)
        );
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
        if (registerTopic.equals(topic)) {
            Map<String, Object> body = JsonNodeSupport.MAPPER.convertValue(payload, new TypeReference<>() {});
            AdapterIndex adapter = adapterIndexService.register(body);
            if (adapter != null) {
                trySubscribeAdapterHeartbeat(adapter.getAdapterName());
            }
            return;
        }
        String[] parts = topic.split("/");
        if (parts.length == 4 && "smartlab".equals(parts[0]) && "adapter".equals(parts[1]) && "heartbeat".equals(parts[3])) {
            adapterIndexService.heartbeat(parts[2], payload.path("status").asText("ONLINE"));
            return;
        }
        if (parts.length == 5 && "smartlab".equals(parts[0]) && "adapter".equals(parts[1])) {
            String adapterName = parts[2];
            String devicePoint = parts[3];
            switch (parts[4]) {
                case "telemetry" -> protocolMapperService.applyTelemetry(adapterName, devicePoint, payload);
                case "event" -> protocolMapperService.applyAdapterEvent(adapterName, devicePoint, payload);
                default -> {
                    // command topics are system-outbound.
                }
            }
        }
    }

    private void trySubscribeAdapterHeartbeat(String adapterName) {
        if (adapterName == null || adapterName.isBlank()) {
            return;
        }
        try {
            subscribeTopics(topic(adapterHeartbeatTopicPattern, adapterName, null));
        } catch (Exception e) {
            log.warn("MQTT Adapter 心跳订阅暂未成功, adapter={}, reason={}", adapterName, e.getMessage());
        }
    }

    private void subscribeKnownDevicePointTopics() {
        Map<String, AdapterRouteDTO> routes = protocolMapperService.refreshAdapterRouteTable();
        List<String> topics = new ArrayList<>();
        for (AdapterRouteDTO route : routes.values()) {
            String adapterName = route.getBoundAdapterName();
            String devicePoint = route.getBoundDevicePoint();
            if (adapterName == null || adapterName.isBlank() || devicePoint == null || devicePoint.isBlank()) {
                continue;
            }
            topics.add(topic(deviceTelemetryTopicPattern, adapterName, devicePoint));
            topics.add(topic(deviceEventTopicPattern, adapterName, devicePoint));
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

    private String topic(String pattern, String adapterName, String devicePoint) {
        String result = pattern == null ? "" : pattern;
        result = result.replace("{adapterName}", adapterName == null ? "" : adapterName.trim());
        result = result.replace("{devicePoint}", devicePoint == null ? "" : devicePoint.trim());
        return result;
    }
}
