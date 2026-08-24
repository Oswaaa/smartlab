package com.smartlab.engine.workflow;

/** 请求对某个运行中任务立即再跑一轮；若该任务本轮仍在飞则记为待跑，结束后接着跑。 */
public interface WorkflowTaskPollScheduler {
    void requestPoll(Long taskId);
}
