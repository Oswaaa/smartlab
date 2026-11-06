package com.smartlab.agent.operator.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.agent.operator.ActionGuard;
import com.smartlab.agent.operator.CapabilityBinder;
import com.smartlab.agent.operator.ConfirmCard;
import com.smartlab.agent.operator.InstanceResolver;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorDeviceCatalog;
import com.smartlab.agent.operator.OperatorJson;
import com.smartlab.agent.operator.OperatorSessionStore;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.agent.operator.PendingAction;
import com.smartlab.management.entity.resource.device.DeviceModels;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生成"待确认"的设备动作。这是整条链路里唯一能改变设备状态的入口，
 * 但它<b>不执行</b>——只产出一张确认卡片交给用户点确认。
 */
@Component
public class PrepareDeviceActionTool implements OperatorTool {

    private static final JsonNode SCHEMA = OperatorJson.schema("""
            {"type":"object","properties":{
              "instance":{"type":"string","description":"设备名，原样传用户说的名字"},
              "capability":{"type":"string","description":"动作，例如 加热、停止加热"},
              "parameters":{"type":"object","description":"参数，键用能力里的参数名或用户说法，如 {\\"温度\\":500,\\"加热时间\\":30}；不要自己编 parameter_1"},
              "reason":{"type":"string","description":"一句话说明意图，会显示在确认页。不要在这里复述参数数值，参数由卡片单独展示"}
            },"required":["instance","capability"]}
            """);

    public static final String NAME = "prepare_device_action";

    private final OperatorDeviceCatalog catalog;
    private final OperatorSessionStore store;

    public PrepareDeviceActionTool(OperatorDeviceCatalog catalog, OperatorSessionStore store) {
        this.catalog = catalog;
        this.store = store;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "准备对一台设备下发一个动作。会先做完整预检并生成确认卡片，"
                + "用户点确认之后才会真正下发。一次只能准备一个动作，不支持把多个动作串起来。";
    }

    @Override
    public JsonNode parameterSchema() {
        return SCHEMA;
    }

    @Override
    public JsonNode execute(JsonNode arguments, OperatorContext context) {
        // 新操作直接覆盖旧的待确认动作：用户改主意时说一句就行，不用先点取消。
        // 旧卡片会在前端被标成"已被新的操作取代"，token 也随之作废。
        if (store.pendingAlive(context.session())) {
            context.session().clearPending();
        }

        String query = OperatorJson.text(arguments, "instance");
        String hint = OperatorJson.text(arguments, "capability");
        InstanceResolver.Resolution resolution = catalog.resolver().resolve(query);

        if (resolution.status() == InstanceResolver.Status.AMBIGUOUS) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "NEED_CLARIFICATION");
            result.put("question", "要操作哪一台设备？");
            var candidates = result.putArray("candidates");
            for (InstanceResolver.Candidate candidate : resolution.candidates()) {
                ObjectNode item = candidates.addObject();
                item.put("id", candidate.id());
                item.put("name", candidate.instanceName());
                item.put("modelName", catalog.modelName(candidate.modelId()));
            }
            result.put("instruction", "把 candidates 列给用户选，不要自己挑一个");
            return result;
        }
        if (!resolution.found()) {
            return OperatorJson.error("NOT_FOUND",
                    resolution.hint() == null ? "没有找到匹配的设备" : resolution.hint());
        }

        InstanceResolver.Candidate candidate = resolution.resolved();
        DeviceModels model = catalog.modelOrNull(candidate.modelId());
        if (model == null) {
            return OperatorJson.error("MODEL_MISSING", "设备模型不存在: " + candidate.modelId());
        }

        CapabilityBinder.CapabilityView capability = CapabilityBinder.findCapability(model, hint);
        if (capability == null) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "NEED_CLARIFICATION");
            result.put("question", "这台设备要执行哪个动作？");
            var options = result.putArray("capabilities");
            for (CapabilityBinder.CapabilityView item : CapabilityBinder.capabilities(model)) {
                ObjectNode node = options.addObject();
                node.put("name", item.capabilityName());
                node.put("displayName", item.displayName());
            }
            result.put("instruction", "把 capabilities 的可选项列给用户，不要猜");
            return result;
        }

        Map<String, Object> raw = new LinkedHashMap<>();
        JsonNode parametersNode = arguments == null ? null : arguments.get("parameters");
        if (parametersNode != null && parametersNode.isObject()) {
            parametersNode.fields().forEachRemaining(entry ->
                    raw.put(entry.getKey(), OperatorJson.MAPPER.convertValue(entry.getValue(), Object.class)));
        }

        CapabilityBinder.Binding binding = CapabilityBinder.bind(capability, raw);
        if (!binding.ok()) {
            ObjectNode result = OperatorJson.error("PARAMETER_INCOMPLETE", binding.error());
            result.put("instruction", "缺少信息时向用户提问，不要自己编一个值填进去");
            return result;
        }

        ActionGuard.Result guard = ActionGuard.check(
                catalog.snapshot(candidate.id()), capability, binding.parameters(), model);

        List<ConfirmCard.Row> rows = new ArrayList<>();
        rows.add(ConfirmCard.Row.of("设备", candidate.instanceName() + "（id=" + candidate.id() + "）"));
        rows.add(ConfirmCard.Row.of("点位", String.valueOf(candidate.devicePoint())));
        rows.add(ConfirmCard.Row.of("型号", model.getModelName()));
        rows.add(ConfirmCard.Row.of("动作", capability.displayName()
                + "（" + capability.capabilityName() + " / " + capability.commandName() + "）"));
        String reason = OperatorJson.text(arguments, "reason");
        if (reason != null && !reason.isBlank()) {
            rows.add(ConfirmCard.Row.of("原因", reason));
        }

        List<ConfirmCard.Row> parameterRows = new ArrayList<>();
        for (CapabilityBinder.BoundParameter parameter : binding.parameters()) {
            parameterRows.add(new ConfirmCard.Row(
                    parameter.displayName(),
                    format(parameter.value()) + (parameter.unit() == null ? "" : " " + parameter.unit()),
                    parameter.source().name()));
        }

        List<ConfirmCard.Issue> warnings = new ArrayList<>();
        for (String warning : binding.warnings()) {
            warnings.add(new ConfirmCard.Issue(ActionGuard.WARNING, warning));
        }
        for (ActionGuard.Issue issue : guard.warnings()) {
            warnings.add(new ConfirmCard.Issue(ActionGuard.WARNING, issue.text()));
        }
        List<ConfirmCard.Issue> blocking = new ArrayList<>();
        for (ActionGuard.Issue issue : guard.blocking()) {
            blocking.add(new ConfirmCard.Issue(ActionGuard.BLOCKING, issue.text()));
        }

        ConfirmCard card = new ConfirmCard(
                ConfirmCard.KIND_DEVICE_ACTION,
                "确认" + capability.displayName(),
                "即将对 " + candidate.instanceName() + " 执行「" + capability.displayName() + "」",
                List.copyOf(rows), List.copyOf(parameterRows), List.of(),
                List.copyOf(warnings), List.copyOf(blocking), blocking.isEmpty(), true);

        if (!card.ready()) {
            ObjectNode result = OperatorJson.obj();
            result.put("status", "BLOCKED");
            result.put("card", OperatorJson.node(card.toMap()));
            result.put("instruction", "把 blocking 的原因告诉用户，不要生成确认按钮，也不要说已经执行");
            return result;
        }

        PendingAction.Device action = new PendingAction.Device(
                UUID.randomUUID().toString(),
                Instant.now(),
                candidate.id(),
                candidate.instanceName(),
                capability.capabilityName(),
                capability.displayName(),
                CapabilityBinder.toParameterMap(binding),
                card
        );
        context.session().setPending(action);

        ObjectNode result = OperatorJson.obj();
        result.put("status", "AWAITING_CONFIRMATION");
        result.put("token", action.token());
        result.put("card", OperatorJson.node(card.toMap()));
        result.put("instruction",
                "操作尚未下发。请把确认卡片里的设备、参数（含单位和来源）、以及所有预警原文告诉用户，"
                        + "然后等用户点确认。绝对不能说「已经执行」「已经开始加热」。");
        return result;
    }

    private static String format(Object value) {
        if (value instanceof Double number) {
            if (Math.abs(number - Math.rint(number)) < 1e-9) return String.valueOf((long) (double) number);
            return String.valueOf(number);
        }
        return String.valueOf(value);
    }
}
