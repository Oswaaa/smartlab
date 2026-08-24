package com.smartlab.engine.workflow;

/** 同一任务的工作流轮询与接口投递共用锁。 */
public final class WorkflowTaskLocks {
    private static final Object[] LOCKS = create(256);

    private WorkflowTaskLocks() {
    }

    public static Object of(Long taskId) {
        if (taskId == null) throw new IllegalArgumentException("任务ID不能为空");
        return LOCKS[Math.floorMod(Long.hashCode(taskId), LOCKS.length)];
    }

    private static Object[] create(int count) {
        Object[] locks = new Object[count];
        for (int index = 0; index < count; index++) locks[index] = new Object();
        return locks;
    }
}
