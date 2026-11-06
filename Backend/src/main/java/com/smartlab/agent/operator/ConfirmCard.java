package com.smartlab.agent.operator;

import java.util.List;
import java.util.Map;

/**
 * 确认卡片：用户点"确认"之前看到的全部信息。
 *
 * <p>设计要求是「不藏东西」——设备、能力、每个参数的取值与来源、预检结果、可能的后果，
 * 全部摊开。用户只需要扫一眼就能判断这次操作是不是他要的。
 *
 * <p>{@code canSimulate} 决定卡片上是否出现「仿真执行」按钮：只有单设备控制和启动任务
 * 这两种会真的驱动设备的操作才给这个出口；创建任务、保存草稿没有物理动作，不给。
 */
public record ConfirmCard(
        String kind,
        String title,
        String summary,
        List<Row> rows,
        List<Row> parameters,
        List<Row> bindings,
        List<Issue> warnings,
        List<Issue> blocking,
        boolean ready,
        boolean canSimulate
) {

    /** 一行"标签 → 值"。source 用于标注这个值是谁定的。 */
    public record Row(String label, String value, String source) {
        public static Row of(String label, String value) {
            return new Row(label, value, null);
        }
    }

    /** 一条问题。level 见 {@link ActionGuard#BLOCKING} / {@link ActionGuard#WARNING}。 */
    public record Issue(String level, String text) {
    }

    public static final String KIND_DEVICE_ACTION = "DEVICE_ACTION";
    public static final String KIND_CREATE_TASK = "CREATE_TASK";
    public static final String KIND_START_TASK = "START_TASK";
    public static final String KIND_FLOW_DRAFT = "FLOW_DRAFT";

    public static ConfirmCard blocking(String kind, String title, String summary, List<Issue> issues) {
        return new ConfirmCard(kind, title, summary, List.of(), List.of(), List.of(),
                List.of(), issues == null ? List.of() : issues, false, false);
    }

    public ConfirmCard withRows(List<Row> extra) {
        java.util.List<Row> merged = new java.util.ArrayList<>(rows);
        if (extra != null) merged.addAll(extra);
        return new ConfirmCard(kind, title, summary, List.copyOf(merged), parameters, bindings,
                warnings, blocking, ready, canSimulate);
    }

    /**
     * 仿真执行跑完之后追加的"正式执行"卡片。
     *
     * <p>不再给仿真出口——已经模拟过一遍了，再来一次只会让用户原地打转。
     */
    public ConfirmCard asFollowUp(String newTitle, String newSummary, List<Row> extraRows,
                                  List<Issue> newWarnings) {
        java.util.List<Row> merged = new java.util.ArrayList<>(rows);
        if (extraRows != null) merged.addAll(extraRows);
        java.util.List<Issue> mergedWarnings = new java.util.ArrayList<>(warnings);
        if (newWarnings != null) mergedWarnings.addAll(newWarnings);
        return new ConfirmCard(kind, newTitle, newSummary, List.copyOf(merged), parameters, bindings,
                List.copyOf(mergedWarnings), blocking, ready, false);
    }

    /** 转成给前端/模型的通用 Map，避免各处重复拼装。 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("kind", kind);
        map.put("title", title);
        map.put("summary", summary);
        map.put("ready", ready);
        map.put("canSimulate", canSimulate);
        map.put("rows", rows.stream().map(r -> row(r)).toList());
        map.put("parameters", parameters.stream().map(r -> row(r)).toList());
        map.put("bindings", bindings.stream().map(r -> row(r)).toList());
        map.put("warnings", warnings.stream().map(i -> issue(i)).toList());
        map.put("blocking", blocking.stream().map(i -> issue(i)).toList());
        return map;
    }

    private static Map<String, Object> row(Row row) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("label", row.label());
        map.put("value", row.value());
        if (row.source() != null) map.put("source", row.source());
        return map;
    }

    private static Map<String, Object> issue(Issue issue) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("level", issue.level());
        map.put("text", issue.text());
        return map;
    }
}
