package com.smartlab.global.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.global.util.JsonNodeSupport;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Resolves and validates the system-owned state-machine interface contract. */
@Service
public class StateMachineInterfacePolicyService {

    private final SchemaMetadataService schemaMetadataService;

    public StateMachineInterfacePolicyService(SchemaMetadataService schemaMetadataService) {
        this.schemaMetadataService = schemaMetadataService;
    }

    public ArrayNode standardInterfaces(JsonNode adapterEvents) {
        ArrayNode result = JsonNodeSupport.arrayNode();
        JsonNode standard = schemaMetadataService.frontendMetadata()
                .path("stateMachine").path("standardInterfaces");
        Set<String> dynamicAdapterSignals = eventNames(adapterEvents);
        String adapterInputInterface = standardInterfaceName("IN", "ADAPTER");
        for (JsonNode interfaceNode : standard) {
            ObjectNode copy = interfaceNode.deepCopy();
            if (adapterInputInterface.equals(copy.path("name").asText())) {
                LinkedHashSet<String> signals = textSet(copy.path("allowedSignals"));
                signals.addAll(dynamicAdapterSignals);
                copy.set("allowedSignals", JsonNodeSupport.toNode(signals));
            }
            result.add(copy);
        }
        return result;
    }

    public void validateInterfaces(JsonNode interfaces, JsonNode adapterEvents) {
        if (interfaces == null || !interfaces.isArray()) {
            throw new IllegalArgumentException("状态机 interfaces 必须是数组");
        }
        Map<String, JsonNode> expected = indexByName(standardInterfaces(adapterEvents));
        Map<String, JsonNode> actual = indexByName(interfaces);
        if (!actual.keySet().equals(expected.keySet())) {
            throw new IllegalArgumentException("状态机必须且只能声明标准接口: " + expected.keySet());
        }
        for (Map.Entry<String, JsonNode> entry : expected.entrySet()) {
            JsonNode value = actual.get(entry.getKey());
            JsonNode standard = entry.getValue();
            if (!standard.path("direction").asText().equals(value.path("direction").asText())
                    || !standard.path("interfaceType").asText().equals(value.path("interfaceType").asText())) {
                throw new IllegalArgumentException("状态机接口方向或类型不符合系统定义: " + entry.getKey());
            }
            if (!textSet(standard.path("allowedSignals")).equals(textSet(value.path("allowedSignals")))) {
                throw new IllegalArgumentException("状态机接口包含运行时无法产生的信号: " + entry.getKey());
            }
        }
    }

    public void validateTransitions(JsonNode transitions, JsonNode interfaces) {
        Map<String, JsonNode> interfaceIndex = indexByName(interfaces);
        if (transitions == null || !transitions.isArray()) {
            throw new IllegalArgumentException("状态机 transitions 必须是数组");
        }
        Set<TransitionKey> transitionKeys = new LinkedHashSet<>();
        for (JsonNode transition : transitions) {
            String stateSpace = transition.path("stateSpace").asText("");
            if (!Set.of("CMD", "OP").contains(stateSpace)) {
                throw new IllegalArgumentException("状态转移必须明确声明 stateSpace 为 CMD 或 OP");
            }
            JsonNode trigger = transition.path("trigger");
            String interfaceName = trigger.isNull() ? null : trigger.path("interfaceName").asText("");
            String signalName = trigger.isNull() ? null : trigger.path("signalName").asText("");
            TransitionKey transitionKey = new TransitionKey(
                    stateSpace,
                    transition.path("fromStateName").asText(""),
                    interfaceName,
                    signalName);
            if (!transitionKeys.add(transitionKey)) {
                throw new IllegalArgumentException("同一状态与触发条件只能对应一条转移规则: "
                        + stateSpace + "." + transition.path("fromStateName").asText("")
                        + " / " + interfaceName + "." + signalName);
            }
            if (trigger.isNull()) {
                if (!automaticTransitionStateSpaces().contains(stateSpace)) {
                    throw new IllegalArgumentException("自动状态转移仅允许用于 CMD 状态空间");
                }
                validateActions(transition.path("actions"), interfaceIndex);
                continue;
            }
            JsonNode interfaceNode = interfaceIndex.get(interfaceName);
            if (interfaceNode == null) {
                throw new IllegalArgumentException("状态转移引用了不存在的接口: " + interfaceName);
            }
            if (!textSet(interfaceNode.path("allowedSignals")).contains(signalName)) {
                throw new IllegalArgumentException("状态转移信号不在接口 allowedSignals 中: "
                        + interfaceName + "." + signalName);
            }
            validateActions(transition.path("actions"), interfaceIndex);
        }
    }

    public void validateTransitionSemantics(
            JsonNode transitions,
            JsonNode cmdStateSpace,
            JsonNode opStateSpace,
            JsonNode adapterEvents
    ) {
        Set<String> commandStates = stateNames(cmdStateSpace);
        Set<String> operationStates = stateNames(opStateSpace);
        Set<String> commandEvents = eventNames(adapterEvents == null ? null : adapterEvents.path("cmdEvents"));
        Set<String> operationEvents = eventNames(adapterEvents == null ? null : adapterEvents.path("opEvents"));
        String adapterInputInterface = standardInterfaceName("IN", "ADAPTER");

        for (JsonNode transition : transitions) {
            String stateSpace = transition.path("stateSpace").asText("");
            Set<String> states = "CMD".equals(stateSpace) ? commandStates : operationStates;
            String fromState = transition.path("fromStateName").asText("");
            String toState = transition.path("toStateName").asText("");
            if (!states.contains(fromState) || !states.contains(toState)) {
                throw new IllegalArgumentException("状态转移引用的状态不属于 " + stateSpace + " 状态空间: "
                        + fromState + " -> " + toState);
            }

            JsonNode trigger = transition.path("trigger");
            if (!adapterInputInterface.equals(trigger.path("interfaceName").asText(""))) {
                continue;
            }
            String signalName = trigger.path("signalName").asText("");
            Set<String> allowedEvents = "CMD".equals(stateSpace) ? commandEvents : operationEvents;
            if (!allowedEvents.contains(signalName)) {
                throw new IllegalArgumentException("Adapter " + stateSpace + " 状态转移使用了错误事件域的信号: "
                        + signalName);
            }
        }
    }

    private Set<String> automaticTransitionStateSpaces() {
        return textSet(schemaMetadataService.frontendMetadata()
                .path("stateMachine").path("automaticTransitionStateSpaces"));
    }

    private String standardInterfaceName(String direction, String interfaceType) {
        for (JsonNode item : schemaMetadataService.frontendMetadata()
                .path("stateMachine").path("standardInterfaces")) {
            if (direction.equals(item.path("direction").asText())
                    && interfaceType.equals(item.path("interfaceType").asText())) {
                return item.path("name").asText();
            }
        }
        throw new IllegalStateException("状态机模型缺少标准接口: " + direction + "/" + interfaceType);
    }

    private Set<String> stateNames(JsonNode stateSpace) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (stateSpace != null) {
            if (stateSpace.has("regions") && stateSpace.get("regions").isArray()) {
                for (JsonNode region : stateSpace.get("regions")) {
                    for (JsonNode state : region.path("states")) {
                        String name = state.path("stateName").asText("");
                        if (!name.isBlank()) result.add(name);
                    }
                }
            } else {
                for (JsonNode state : stateSpace.path("states")) {
                    String name = state.path("stateName").asText("");
                    if (!name.isBlank()) result.add(name);
                }
            }
        }
        return result;
    }
    public void validateStateActions(JsonNode stateSpace, JsonNode interfaces) {
        Map<String, JsonNode> interfaceIndex = indexByName(interfaces);
        if (stateSpace != null) {
            if (stateSpace.has("regions") && stateSpace.get("regions").isArray()) {
                for (JsonNode region : stateSpace.get("regions")) {
                    for (JsonNode state : region.path("states")) {
                        validateActions(state.path("onEntry"), interfaceIndex);
                    }
                }
            } else {
                for (JsonNode state : stateSpace.path("states")) {
                    validateActions(state.path("onEntry"), interfaceIndex);
                }
            }
        }
    }

    private void validateActions(JsonNode actions, Map<String, JsonNode> interfaceIndex) {
        if (actions == null || !actions.isArray()) return;
        for (JsonNode action : actions) {
            if (!"SEND".equals(action.path("actionName").asText())) continue;
            JsonNode payload = action.path("payload");
            String interfaceName = payload.path("interfaceName").asText("");
            String signalName = payload.path("signalName").asText("");
            JsonNode target = interfaceIndex.get(interfaceName);
            if (target == null) throw new IllegalArgumentException("SEND 动作引用了不存在的接口: " + interfaceName);
            if (!"OUT".equals(target.path("direction").asText()))
                throw new IllegalArgumentException("SEND 动作只能投递到 OUT 接口: " + interfaceName);
            if (!textSet(target.path("allowedSignals")).contains(signalName))
                throw new IllegalArgumentException("SEND 动作信号不在接口 allowedSignals 中: "
                        + interfaceName + "." + signalName);
        }
    }

    private Map<String, JsonNode> indexByName(JsonNode interfaces) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode item : interfaces) {
            String name = item.path("name").asText("");
            if (name.isBlank() || result.put(name, item) != null) {
                throw new IllegalArgumentException("状态机接口名称为空或重复: " + name);
            }
        }
        return result;
    }

    private Set<String> eventNames(JsonNode events) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collectEventNames(events, result);
        return result;
    }

    private void collectEventNames(JsonNode node, Set<String> result) {
        if (node == null || node.isNull() || node.isMissingNode()) return;
        if (node.isTextual()) {
            String name = node.asText("");
            if (!name.isBlank()) result.add(name);
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) collectEventNames(item, result);
            return;
        }
        if (node.isObject()) {
            String name = node.path("name").asText(node.path("eventName").asText(""));
            if (!name.isBlank()) result.add(name);
            node.fields().forEachRemaining(entry -> collectEventNames(entry.getValue(), result));
        }
    }

    private LinkedHashSet<String> textSet(JsonNode values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null && values.isArray()) {
            values.forEach(value -> {
                if (!value.asText("").isBlank()) result.add(value.asText());
            });
        }
        return result;
    }
    private record TransitionKey(
            String stateSpace,
            String fromStateName,
            String interfaceName,
            String signalName
    ) {}
}
