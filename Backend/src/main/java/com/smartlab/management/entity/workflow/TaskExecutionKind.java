package com.smartlab.management.entity.workflow;

/** TASK.execution_kind：正式执行 / 模拟执行。 */
public final class TaskExecutionKind {
    public static final String PRODUCTION = "PRODUCTION";
    public static final String SIMULATION = "SIMULATION";

    private TaskExecutionKind() {}

    public static String normalize(String kind) {
        if (kind == null || kind.isBlank()) return PRODUCTION;
        String normalized = kind.trim().toUpperCase();
        if (PRODUCTION.equals(normalized) || SIMULATION.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("不支持的任务执行种类: " + kind);
    }

    public static boolean isSimulation(Task task) {
        return task != null && SIMULATION.equals(normalize(task.getExecutionKind()));
    }

    public static boolean isProduction(Task task) {
        return task != null && PRODUCTION.equals(normalize(task.getExecutionKind()));
    }
}
