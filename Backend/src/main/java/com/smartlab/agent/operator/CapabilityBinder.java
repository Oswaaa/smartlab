package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.entity.resource.device.DeviceModels;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备能力与参数的绑定器。
 *
 * <p>核心约束：<b>能力标识（capability_1）绝不由模型构造</b>。模型只说"加热"，
 * 由本类反查出 {@code capability_1} 并原样回传，这样模型编辑模型、换型号都不会出错。
 *
 * <p>参数键同样是服务端生成的 {@code parameter_N}。模型嘴里是"温度 500 度"，
 * 落库前必须变成 {@code parameter_1=500}，中间那层映射只能在这里做。
 *
 * <p>两个来自真实契约的行为：
 * <ol>
 *   <li>后端组装报文时，<b>非必填且未提供的参数会被整段跳过</b>，不会补 0。
 *       所以"少填"是安全的，"填 0"是危险的——加热分钟数写 0 表示该段及后续段全部不使用。</li>
 *   <li>加热时长未指定时按 9999 分钟写入（契约定义"不等待 9999 分钟"，即持续保持）。</li>
 * </ol>
 */
public final class CapabilityBinder {

    /** 未指定加热时长时的默认值，依据 heat 命令契约原文。 */
    private static final int HOLD_FOREVER_MINUTES = 9999;

    public record ParameterSpec(
            String name,
            String displayName,
            String dataType,
            String unit,
            boolean required,
            Integer segment
    ) {
    }

    public record CapabilityView(
            String capabilityName,
            String displayName,
            String commandName,
            boolean abort,
            String abortCapabilityName,
            List<ParameterSpec> parameters
    ) {
        public boolean hasParameters() {
            return parameters != null && !parameters.isEmpty();
        }
    }

    /** 参数取值来源，用于确认页区分"用户明确说的"和"服务端补的"。 */
    public enum Source {
        /** 用户/模型明确给出。 */
        USER,
        /** 服务端按契约默认值补全。 */
        DEFAULT
    }

    public record BoundParameter(String name, String displayName, Object value, String unit, Source source) {
    }

    public record Binding(CapabilityView capability, List<BoundParameter> parameters,
                          List<String> warnings, String error) {
        public boolean ok() {
            return error == null;
        }

        public static Binding failure(String error) {
            return new Binding(null, List.of(), List.of(), error);
        }
    }

    private CapabilityBinder() {
    }

    /** 解析模型的能力清单。 */
    public static List<CapabilityView> capabilities(DeviceModels model) {
        if (model == null || model.getCapabilities() == null || !model.getCapabilities().isArray()) {
            return List.of();
        }
        List<CapabilityView> result = new ArrayList<>();
        for (JsonNode node : model.getCapabilities()) {
            CapabilityView view = toView(node);
            if (view != null) result.add(view);
        }
        return result;
    }

    /**
     * 按用户说法定位能力。支持 capabilityName、显示名、命令名三种匹配。
     * 命中多个时返回 null，由调用方要求模型说清楚。
     */
    public static CapabilityView findCapability(DeviceModels model, String hint) {
        List<CapabilityView> all = capabilities(model);
        if (all.isEmpty()) return null;
        String key = InstanceResolver.normalize(hint);
        if (key.isEmpty()) {
            return all.size() == 1 ? all.get(0) : null;
        }
        for (CapabilityView capability : all) {
            if (key.equals(InstanceResolver.normalize(capability.capabilityName()))) return capability;
        }
        for (CapabilityView capability : all) {
            if (key.equals(InstanceResolver.normalize(capability.commandName()))) return capability;
            if (key.equals(InstanceResolver.normalize(capability.displayName()))) return capability;
        }
        List<CapabilityView> fuzzy = new ArrayList<>();
        for (CapabilityView capability : all) {
            String display = InstanceResolver.normalize(capability.displayName());
            String command = InstanceResolver.normalize(capability.commandName());
            if ((!display.isEmpty() && (display.contains(key) || key.contains(display)))
                    || (!command.isEmpty() && command.contains(key))) {
                fuzzy.add(capability);
            }
        }
        return fuzzy.size() == 1 ? fuzzy.get(0) : null;
    }

    /**
     * 把语义参数绑定到 parameter_N。
     *
     * @param raw 模型给出的参数，键可以是 parameter_1、目标温度1、温度、第2段温度、temperature1
     */
    public static Binding bind(CapabilityView capability, Map<String, Object> raw) {
        if (capability == null) return Binding.failure("未识别到设备能力");
        Map<String, Object> source = raw == null ? Map.of() : raw;
        List<BoundParameter> bound = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> consumed = new LinkedHashSet<>();

        for (ParameterSpec spec : capability.parameters()) {
            String matchedKey = matchKey(source, spec, consumed);
            if (matchedKey != null) {
                Object value;
                try {
                    value = coerce(source.get(matchedKey), spec.dataType());
                } catch (RuntimeException error) {
                    return Binding.failure("参数「" + spec.displayName() + "」的取值不合法: " + source.get(matchedKey));
                }
                consumed.add(matchedKey);
                bound.add(new BoundParameter(spec.name(), spec.displayName(), value, spec.unit(), Source.USER));
                continue;
            }
            if (!spec.required()) {
                // 非必填且未提供：保持不传，后端会跳过，不要补 0
                continue;
            }
            Object fallback = defaultFor(capability, spec);
            if (fallback == null) {
                return Binding.failure("缺少必填参数「" + spec.displayName() + "」"
                        + (spec.unit() == null ? "" : "（单位 " + spec.unit() + "）")
                        + "，请向用户确认");
            }
            Object typedFallback = coerce(fallback, spec.dataType());
            bound.add(new BoundParameter(spec.name(), spec.displayName(), typedFallback, spec.unit(), Source.DEFAULT));
            warnings.add("未指定「" + spec.displayName() + "」，按契约默认值 " + typedFallback
                    + (spec.unit() == null ? "" : " " + spec.unit()) + " 处理");
        }
        return new Binding(capability, bound, warnings, null);
    }

    /** 把绑定结果转成下发用的 Map。 */
    public static Map<String, Object> toParameterMap(Binding binding) {
        Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        if (binding == null || binding.parameters() == null) return parameters;
        for (BoundParameter parameter : binding.parameters()) {
            parameters.put(parameter.name(), parameter.value());
        }
        return parameters;
    }

    private static Object defaultFor(CapabilityView capability, ParameterSpec spec) {
        // 加热时长未指定：契约规定 9999 分钟表示"不等待"，即持续保持
        boolean heatMinutes = "heat".equalsIgnoreCase(capability.commandName())
                && spec.displayName() != null
                && spec.displayName().contains("时间")
                && Integer.valueOf(1).equals(spec.segment());
        if (heatMinutes) return HOLD_FOREVER_MINUTES;
        return null;
    }

    private static String matchKey(Map<String, Object> raw, ParameterSpec spec, Set<String> consumed) {
        if (raw.containsKey(spec.name()) && !consumed.contains(spec.name())) return spec.name();
        String specNormalized = InstanceResolver.normalize(spec.displayName());
        String specBase = stripQualifiers(specNormalized);
        for (String key : raw.keySet()) {
            if (consumed.contains(key)) continue;
            String keyNormalized = InstanceResolver.normalize(key);
            if (keyNormalized.isEmpty()) continue;
            if (keyNormalized.equals(specNormalized)) return key;
            String keyBase = stripQualifiers(keyNormalized);
            if (keyBase.isEmpty()) continue;
            boolean baseMatches = keyBase.equals(specBase)
                    || (keyBase.length() >= 2 && (specBase.contains(keyBase) || keyBase.contains(specBase)));
            if (!baseMatches) continue;
            Integer keySegment = lastNumber(keyNormalized);
            if (keySegment == null) return key;
            if (keySegment.equals(spec.segment())) return key;
        }
        return null;
    }

    private static String stripQualifiers(String text) {
        return text.replace("第", "").replace("段", "").replace("个", "")
                .replace("目标", "").replace("的", "").replace("设置", "")
                .replaceAll("\\d+", "");
    }

    private static Integer lastNumber(String text) {
        Matcher matcher = Pattern.compile("(\\d+)(?!.*\\d)").matcher(text);
        if (matcher.find()) {
            try {
                return Integer.valueOf(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object coerce(Object raw, String dataType) {
        String type = dataType == null ? "" : dataType.trim().toUpperCase();
        if (raw == null) return null;
        switch (type) {
            case "DOUBLE", "FLOAT", "NUMBER", "DECIMAL" -> {
                if (raw instanceof Number number) return number.doubleValue();
                return Double.parseDouble(String.valueOf(raw).trim());
            }
            case "INTEGER", "INT", "LONG" -> {
                if (raw instanceof Number number) return number.longValue();
                return Long.parseLong(String.valueOf(raw).trim());
            }
            case "BOOLEAN", "BOOL" -> {
                if (raw instanceof Boolean value) return value;
                return Boolean.parseBoolean(String.valueOf(raw).trim());
            }
            case "STRING", "TEXT" -> {
                return String.valueOf(raw);
            }
            default -> {
                return raw;
            }
        }
    }

    private static CapabilityView toView(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        String capabilityName = node.path("capabilityName").asText("");
        if (capabilityName.isBlank()) return null;
        List<ParameterSpec> parameters = new ArrayList<>();
        for (JsonNode parameter : node.path("parameters")) {
            String name = parameter.path("name").asText("");
            if (name.isBlank()) continue;
            String displayName = parameter.path("displayName").asText(name);
            parameters.add(new ParameterSpec(
                    name,
                    displayName,
                    parameter.path("dataType").asText(""),
                    parameter.path("unit").isMissingNode() || parameter.path("unit").isNull()
                            ? null : parameter.path("unit").asText(null),
                    parameter.path("required").asBoolean(false),
                    lastNumber(InstanceResolver.normalize(displayName))
            ));
        }
        return new CapabilityView(
                capabilityName,
                node.path("displayName").asText(capabilityName),
                node.path("adapterCommandName").asText(""),
                node.path("isAbort").asBoolean(false),
                node.path("abortCapabilityName").isMissingNode() || node.path("abortCapabilityName").isNull()
                        ? null : node.path("abortCapabilityName").asText(null),
                List.copyOf(parameters)
        );
    }
}
