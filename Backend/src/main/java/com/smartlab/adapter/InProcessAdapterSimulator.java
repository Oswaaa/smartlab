package com.smartlab.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.engine.statemachine.StateMachineEngine;
import com.smartlab.engine.statemachine.StateMachineSendActionEvent;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.entity.resource.device.DeviceInstanceKind;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 流程模拟用的进程内 Adapter。行为来自 {@code ADAPTER_INDEX.parsed_config}，
 * 不是设备模型上的 {@code adapterContract}。状态机仍走原引擎入站。
 * 只回灌 parsed_config 已声明且模型转移唯一对应的 cmd 事件，不写遥测、不改孪生属性。
 */
@Service
public class InProcessAdapterSimulator {

    private static final Logger log = LoggerFactory.getLogger(InProcessAdapterSimulator.class);

    private final DeviceInstancesMapper deviceInstancesMapper;
    private final DeviceModelsMapper deviceModelsMapper;
    private final AdapterIndexService adapterIndexService;
    private final AdapterManifestService adapterManifestService;
    private final StateMachineEngine stateMachineEngine;
    private final Executor executor;
    private final ConcurrentHashMap<Long, String> lastErrors = new ConcurrentHashMap<>();

    public InProcessAdapterSimulator(DeviceInstancesMapper deviceInstancesMapper,
                                      DeviceModelsMapper deviceModelsMapper,
                                      AdapterIndexService adapterIndexService,
                                      AdapterManifestService adapterManifestService,
                                      StateMachineEngine stateMachineEngine,
                                      @Qualifier("workflowTaskExecutor") Executor executor) {
        this.deviceInstancesMapper = deviceInstancesMapper;
        this.deviceModelsMapper = deviceModelsMapper;
        this.adapterIndexService = adapterIndexService;
        this.adapterManifestService = adapterManifestService;
        this.stateMachineEngine = stateMachineEngine;
        this.executor = executor;
    }

    public void handleSendAction(StateMachineSendActionEvent event) {
        if (event == null || !"ADAPTER".equals(event.interfaceType()) || event.instanceId() == null) {
            return;
        }
        DeviceInstances instance = deviceInstancesMapper.selectById(event.instanceId());
        if (instance == null || !DeviceInstanceKind.isTemporary(instance)) {
            return;
        }
        Long instanceId = instance.getId();
        executor.execute(() -> {
            try {
                reply(instanceId, event);
            } catch (IllegalStateException error) {
                recordFailure(instanceId, "SIM_NO_ADAPTER_EVENT", error.getMessage());
            } catch (RuntimeException error) {
                log.warn("进程内 Adapter 模拟失败 instanceId={}: {}", instanceId, error.getMessage());
                recordFailure(instanceId, "SIM_NO_ADAPTER_EVENT", error.getMessage());
            }
        });
    }

    public void clearLastError(Long instanceId) {
        if (instanceId != null) {
            lastErrors.remove(instanceId);
        }
    }

    public String takeLastError(Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        return lastErrors.remove(instanceId);
    }

    private void reply(Long instanceId, StateMachineSendActionEvent event) {
        DeviceInstances instance = deviceInstancesMapper.selectById(instanceId);
        if (instance == null || !DeviceInstanceKind.isTemporary(instance)) {
            return;
        }
        DeviceModels model = deviceModelsMapper.selectById(instance.getDeviceModelId());
        if (model == null) {
            throw new IllegalStateException("临时设备引用的设备模型不存在");
        }
        JsonNode template = parsedTemplate(model);
        if ("CMD_START".equals(event.signalName())) {
            dispatchDeclared(instance, model, template, "SENT", "RUNNING", event);
            dispatchDeclared(instance, model, template, "RUNNING", "COMPLETED", event);
        } else if ("CMD_ABORT".equals(event.signalName())) {
            dispatchDeclared(instance, model, template, "ABORTING", "ABORTED", event);
        }
    }

    private JsonNode parsedTemplate(DeviceModels model) {
        String adapterName = model.getAdapterContract() == null
                ? "" : model.getAdapterContract().path("config").path("adapterName").asText("");
        String categoryName = model.getAdapterContract() == null
                ? "" : model.getAdapterContract().path("config").path("categoryName").asText("");
        if (adapterName.isBlank() || categoryName.isBlank()) {
            throw new IllegalStateException("设备模型未指向 Adapter 解析配置");
        }
        AdapterIndex adapter = adapterIndexService.requireAdapter(adapterName);
        JsonNode category = adapterManifestService.findCategory(adapter.getParsedConfig(), categoryName);
        if (category == null) {
            throw new IllegalStateException("Adapter 解析配置中没有类别: " + categoryName);
        }
        JsonNode template = category.path("deviceTemplate");
        if (template == null || template.isMissingNode() || template.isNull()) {
            throw new IllegalStateException("Adapter 解析配置缺少设备模板: " + categoryName);
        }
        return template;
    }

    private void dispatchDeclared(DeviceInstances instance, DeviceModels model, JsonNode template,
                                     String from, String to, StateMachineSendActionEvent event) {
        String signal = uniqueTransitionEvent(model, from, to);
        if (!hasCmdEvent(template, signal)) {
            throw new IllegalStateException("Adapter 解析配置未声明指令事件: " + signal);
        }
        ObjectNode message = JsonNodeSupport.objectNode();
        message.put("eventName", signal);
        if (event.messageId() != null && !event.messageId().isBlank()) {
            message.put("messageId", event.messageId());
        }
        stateMachineEngine.dispatchAdapterEvent(instance.getId(), signal, message);
    }

    private String uniqueTransitionEvent(DeviceModels model, String fromState, String toState) {
        List<String> matches = new ArrayList<>();
        for (JsonNode transition : iterable(model.getStateTransitions())) {
            if (!"CMD".equals(transition.path("stateSpace").asText())) {
                continue;
            }
            if (!fromState.equals(transition.path("fromStateName").asText())) {
                continue;
            }
            if (!toState.equals(transition.path("toStateName").asText())) {
                continue;
            }
            if (!"Interface_adapter_in".equals(transition.path("trigger").path("interfaceName").asText())) {
                continue;
            }
            String signal = transition.path("trigger").path("signalName").asText("");
            if (!signal.isBlank()) {
                matches.add(signal);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("模型未声明唯一的 " + fromState + "→" + toState + " Adapter 事件"
                    + (matches.isEmpty() ? "" : "，候选: " + matches));
        }
        return matches.get(0);
    }

    private boolean hasCmdEvent(JsonNode template, String eventName) {
        JsonNode cmdEvents = template.path("events").path("cmdEvents");
        if (cmdEvents.isArray()) {
            for (JsonNode event : cmdEvents) {
                String name = event.path("name").asText(event.path("eventName").asText(""));
                if (eventName.equals(name)) {
                    return true;
                }
            }
            return false;
        }
        return cmdEvents.isObject() && cmdEvents.has(eventName);
    }

    private void recordFailure(Long instanceId, String code, String message) {
        String reason = code + ":" + (message == null ? "流程模拟 Adapter 事件无法回灌" : message);
        lastErrors.put(instanceId, reason);
        log.warn("TEMPORARY 实例 {} 无法回灌 Adapter 事件: {}", instanceId, reason);
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }
}
