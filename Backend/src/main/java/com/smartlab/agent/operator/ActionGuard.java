package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.resource.device.DeviceModels;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 动作守卫：在下发前、以及在用户点确认之后各跑一遍。
 *
 * <p>两次都要跑的原因很直白：预演和确认之间可能隔了几十秒，设备可能已经掉线、
 * 或者被别人占用。只信预演那一刻的快照是不安全的。
 *
 * <p>阻断与预警的区别：
 * <ul>
 *   <li><b>BLOCKING</b>：现在发出去一定会失败或被状态机拒绝，直接不给确认按钮。</li>
 *   <li><b>WARNING</b>：能执行，但后果需要用户知情（例如设定温度超过 600℃ 会让设备进入"温度异常"状态）。</li>
 * </ul>
 */
public final class ActionGuard {

    public static final String BLOCKING = "BLOCKING";
    public static final String WARNING = "WARNING";

    /** 后端心跳超时阈值，与 application.yml 的 adapter.heartbeat-timeout-seconds 保持一致。 */
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    /** 指令尚未结束的状态。 */
    private static final List<String> BUSY_COMMAND_STATES = List.of("SENT", "RUNNING", "ABORTING");

    public record Snapshot(
            String instanceName,
            String lifecycleStatus,
            String instanceKind,
            String adapterStatus,
            String onlineStatus,
            OffsetDateTime lastOnlineTime,
            String cmdState
    ) {
    }

    public record Issue(String code, String level, String text) {
    }

    public record Result(List<Issue> blocking, List<Issue> warnings) {
        public boolean pass() {
            return blocking.isEmpty();
        }

        public Result merge(Result other) {
            if (other == null) return this;
            List<Issue> mergedBlocking = new ArrayList<>(blocking);
            mergedBlocking.addAll(other.blocking);
            List<Issue> mergedWarnings = new ArrayList<>(warnings);
            mergedWarnings.addAll(other.warnings);
            return new Result(List.copyOf(mergedBlocking), List.copyOf(mergedWarnings));
        }
    }

    private ActionGuard() {
    }

    /**
     * 执行前检查。
     *
     * @param capability 目标能力，为 null 表示只做基础可用性检查（例如查询）
     */
    public static Result check(Snapshot snapshot, CapabilityBinder.CapabilityView capability,
                               List<CapabilityBinder.BoundParameter> parameters, DeviceModels model) {
        List<Issue> blocking = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        if (snapshot == null) {
            return new Result(List.of(new Issue("DEVICE_NOT_FOUND", BLOCKING, "设备不存在")), List.of());
        }

        String name = snapshot.instanceName() == null ? "该设备" : snapshot.instanceName();
        if (!"IN_USE".equalsIgnoreCase(snapshot.lifecycleStatus() == null ? "" : snapshot.lifecycleStatus())) {
            blocking.add(new Issue("DEVICE_RETIRED", BLOCKING, name + " 已注销，不能执行任何操作"));
        }
        if ("DISABLED".equalsIgnoreCase(snapshot.adapterStatus() == null ? "" : snapshot.adapterStatus())) {
            blocking.add(new Issue("ADAPTER_DISABLED", BLOCKING, name + " 绑定的采集网关已被停用，无法下发指令"));
        }
        if (!"ONLINE".equalsIgnoreCase(snapshot.onlineStatus() == null ? "" : snapshot.onlineStatus())) {
            blocking.add(new Issue("DEVICE_OFFLINE", BLOCKING, name + " 当前离线，指令发不到设备"));
        } else if (snapshot.lastOnlineTime() == null
                || Duration.between(snapshot.lastOnlineTime(), OffsetDateTime.now()).getSeconds() > HEARTBEAT_TIMEOUT_SECONDS) {
            blocking.add(new Issue("DEVICE_HEARTBEAT_LOST", BLOCKING,
                    name + " 超过 " + HEARTBEAT_TIMEOUT_SECONDS + " 秒没收到心跳，等同于离线"));
        }

        String cmdState = snapshot.cmdState() == null ? "" : snapshot.cmdState().toUpperCase();
        if (BUSY_COMMAND_STATES.contains(cmdState)) {
            boolean aborting = capability != null && capability.abort();
            if (!aborting) {
                blocking.add(new Issue("DEVICE_BUSY", BLOCKING,
                        name + " 正在执行指令（" + cmdState + "），需要先停止或等它结束"));
            }
        }

        checkParameterRanges(model, parameters, warnings);
        return new Result(List.copyOf(blocking), List.copyOf(warnings));
    }

    /**
     * 用模型的内置约束检查参数是否会让设备进入异常状态。
     * 这些约束不阻止下发（系统本身允许），但必须让用户看见。
     */
    private static void checkParameterRanges(DeviceModels model, List<CapabilityBinder.BoundParameter> parameters,
                                             List<Issue> warnings) {
        if (model == null || parameters == null || parameters.isEmpty()) return;
        JsonNode attributes = model.getAttributes();
        JsonNode constraints = model.getIntrinsicConstraint();
        if (attributes == null || !attributes.isArray() || constraints == null || !constraints.isArray()) return;

        for (CapabilityBinder.BoundParameter parameter : parameters) {
            Object value = parameter.value();
            if (!(value instanceof Number number)) continue;
            JsonNode attribute = matchAttribute(attributes, parameter);
            if (attribute == null) continue;
            String attributeName = attribute.path("attributeName").asText("");
            for (JsonNode constraint : constraints) {
                if (!attributeName.equals(constraint.path("objectAttributeName").asText(""))) continue;
                double boundary = constraint.path("boundaryValue").asDouble();
                String operator = constraint.path("operator").asText("");
                if (!violates(number.doubleValue(), operator, boundary)) continue;
                String state = constraint.path("violationStateName").asText("异常");
                String unit = parameter.unit() == null ? "" : parameter.unit();
                warnings.add(new Issue("PARAMETER_OUT_OF_SAFE_RANGE", WARNING,
                        "「" + parameter.displayName() + "」设为 " + format(number) + unit
                                + " 会超过 " + format(boundary) + unit + " 的限制，设备将进入「" + state + "」状态"));
            }
        }
    }

    private static JsonNode matchAttribute(JsonNode attributes, CapabilityBinder.BoundParameter parameter) {
        String unit = parameter.unit();
        if (unit != null && !unit.isBlank()) {
            for (JsonNode attribute : attributes) {
                if (unit.equals(attribute.path("unit").asText(""))) return attribute;
            }
        }
        String display = InstanceResolver.normalize(parameter.displayName());
        String base = display.replace("目标", "").replaceAll("\\d+", "");
        if (base.isEmpty()) return null;
        for (JsonNode attribute : attributes) {
            String attributeDisplay = InstanceResolver.normalize(attribute.path("displayName").asText(""));
            if (attributeDisplay.contains(base) || base.contains(attributeDisplay)) return attribute;
        }
        return null;
    }

    static boolean violates(double value, String operator, double boundary) {
        return switch (operator == null ? "" : operator.trim()) {
            case ">" -> value > boundary;
            case ">=" -> value >= boundary;
            case "<" -> value < boundary;
            case "<=" -> value <= boundary;
            case "==", "=" -> Math.abs(value - boundary) < 1e-9;
            case "!=" -> Math.abs(value - boundary) >= 1e-9;
            default -> false;
        };
    }

    private static String format(Number number) {
        double value = number.doubleValue();
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
