package com.smartlab.engine.statemachine;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.DeviceTwinStates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 状态机领域模型合集
 */
public class StateMachineModels {

    /**
     * 状态机完整定义
     */
    public static class Definition {
        private final DeviceModels model;
        private final List<TransitionRule> transitions;

        public Definition(DeviceModels model) {
            this.model = model;
            this.transitions = new ArrayList<>();
            JsonNode transitionsNode = model.getStateTransitions();
            if (transitionsNode != null && transitionsNode.isArray()) {
                for (JsonNode transitionNode : transitionsNode) {
                    this.transitions.add(TransitionRule.fromJson(transitionNode));
                }
            }
        }

        public DeviceModels getModel() {
            return model;
        }

        public List<TransitionRule> getTransitions() {
            return transitions;
        }
    }

    /**
     * 状态转移规则
     */
    public record TransitionRule(
            String stateSpace,
            String regionName,
            String fromState,
            String toState,
            String triggerInterface,
            String triggerSignal,
            List<ActionDefinition> actions
    ) {
        public static TransitionRule fromJson(JsonNode node) {
            JsonNode trigger = node.path("trigger");
            String trigInterface = trigger.path("interfaceName").asText("");
            String trigSignal = trigger.path("signalName").asText("");
            String stateSpace = node.path("stateSpace").asText("");
            String regionName = node.path("regionName").asText("");
            String fromState = node.path("fromStateName").asText("");
            String toState = node.path("toStateName").asText("");

            List<ActionDefinition> actions = new ArrayList<>();
            JsonNode actionsNode = node.path("actions");
            if (actionsNode.isArray()) {
                for (JsonNode actionNode : actionsNode) {
                    actions.add(ActionDefinition.fromJson(actionNode));
                }
            }
            return new TransitionRule(stateSpace, regionName, fromState, toState, trigInterface, trigSignal, actions);
        }

        public boolean automatic() {
            return triggerInterface.isBlank() && triggerSignal.isBlank();
        }
    }

    /**
     * 动作定义
     */
    public record ActionDefinition(
            String actionName,
            JsonNode payload
    ) {
        public static ActionDefinition fromJson(JsonNode node) {
            return new ActionDefinition(
                    node.path("actionName").asText(""),
                    node.path("payload")
            );
        }
    }

    /**
     * 事件执行上下文
     */
    public record EventContext(
            DeviceInstances instance,
            DeviceModels model,
            DeviceTwinStates twinState,
            String triggerInterface,
            String triggerSignal,
            Map<String, Object> executionContext
    ) {}
}
