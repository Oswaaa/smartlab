package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态机动作字典与执行器策略
 */
public class StateMachineDictionary {

    /**
     * 动作执行器接口
     */
    public interface ActionExecutor {
        String getSupportedActionName();
        ObjectNode execute(StateMachineModels.ActionDefinition action, StateMachineModels.EventContext context);
    }

    /**
     * 动作注册表
     */
    @Component
    public static class Registry {
        private final Map<String, ActionExecutor> registry = new HashMap<>();

        @Autowired
        public Registry(List<ActionExecutor> executors) {
            for (ActionExecutor executor : executors) {
                registry.put(executor.getSupportedActionName(), executor);
            }
        }

        public ActionExecutor getExecutor(String actionName) {
            return registry.get(actionName);
        }
    }

    /**
     * SEND 动作执行器
     */
    @Component
    public static class SendActionExecutor implements ActionExecutor {
        private static final Logger log = LoggerFactory.getLogger(SendActionExecutor.class);
        private final ApplicationEventPublisher eventPublisher;

        public SendActionExecutor(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @Override
        public String getSupportedActionName() {
            return "SEND";
        }

        @Override
        public ObjectNode execute(StateMachineModels.ActionDefinition action, StateMachineModels.EventContext context) {
            JsonNode payload = action.payload();
            if (payload == null) {
                throw new IllegalArgumentException("SEND 动作缺少 payload");
            }
            
            String destInterface = payload.path("interfaceName").asText("");
            String destSignal = payload.path("signalName").asText("");
            if (destInterface.isBlank() || destSignal.isBlank()) {
                throw new IllegalArgumentException("SEND 动作缺少目标接口或信号");
            }

            ObjectNode signal = JsonNodeSupport.objectNode();
            signal.put("instanceId", context.instance().getId());
            signal.put("interfaceName", destInterface);
            signal.put("signalName", destSignal);
            signal.put("opState", context.twinState().getCurrentOpState());
            signal.put("cmdState", context.twinState().getCurrentCmdState());
            signal.set("attributes", context.twinState().getCurrentAttr() == null ? JsonNodeSupport.objectNode() : context.twinState().getCurrentAttr());
            signal.set("context", JsonNodeSupport.toNode(context.payloadContext() == null ? Map.of() : context.payloadContext()));
            signal.put("timestamp", Instant.now().toEpochMilli());

            if ("Interface_adapter_out".equals(destInterface)) {
                String commandId = context.payloadContext() != null ? (String) context.payloadContext().get("commandId") : null;
                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = context.payloadContext() != null ? (Map<String, Object>) context.payloadContext().get("parameters") : null;
                
                eventPublisher.publishEvent(new StateMachineSendActionEvent(
                        context.instance().getId(),
                        destInterface,
                        destSignal,
                        commandId,
                        parameters == null ? Map.of() : parameters,
                        signal
                ));
                log.info("设备 {} 状态机输出 Adapter 指令信号 {}，指令 {}", context.instance().getId(), destSignal, commandId);
            }

            return signal;
        }
    }
}
