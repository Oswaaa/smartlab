package com.smartlab.agent.operator;

import com.smartlab.agent.llm.LlmMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次对话的会话状态。
 *
 * <p>为什么需要它：现有 {@code AgentLoop.generate()} 每次调用都新建 Session、消息从头开始，
 * 因此"我反问一句、用户回一句"这种两轮交互会把上一轮已经收齐的信息全部丢掉。
 * 会话必须由我们自己持有，才能支撑"缺什么问什么、问完接着聊"。
 */
public final class OperatorSession {

    /** 对话上下文最多保留的消息条数（含 system），防止越聊越长。 */
    private static final int MAX_MESSAGES = 60;

    private final String id;
    private final Instant createdAt;
    private volatile Instant lastTouched;
    private final List<LlmMessage> messages = new ArrayList<>();
    private final TaskDraft taskDraft = new TaskDraft();
    private PendingAction pending;

    public OperatorSession(String id) {
        this.id = id;
        this.createdAt = Instant.now();
        this.lastTouched = this.createdAt;
    }

    public String id() {
        return id;
    }

    public synchronized void touch() {
        this.lastTouched = Instant.now();
    }

    public synchronized boolean expired(Duration ttl) {
        return Duration.between(lastTouched, Instant.now()).compareTo(ttl) > 0;
    }

    public synchronized void append(LlmMessage message) {
        if (message == null) return;
        messages.add(message);
        trim();
        touch();
    }

    public synchronized void appendAll(List<LlmMessage> batch) {
        if (batch == null) return;
        for (LlmMessage message : batch) {
            if (message != null) messages.add(message);
        }
        trim();
        touch();
    }

    /** 返回不可变副本，避免调用方在迭代时被并发修改。 */
    public synchronized List<LlmMessage> messages() {
        return List.copyOf(messages);
    }

    public synchronized void resetMessages(List<LlmMessage> fresh) {
        messages.clear();
        if (fresh != null) messages.addAll(fresh);
        trim();
        touch();
    }

    private void trim() {
        if (messages.size() <= MAX_MESSAGES) return;
        int cut = messages.size() - MAX_MESSAGES;
        // 必须裁在 user 消息边界上，否则会把 assistant(tool_calls) 删掉却留下 tool 结果，
        // 后续请求会因 tool_call_id 找不到对应调用而报错
        int start = cut;
        while (start < messages.size() && !"user".equals(messages.get(start).role())) start++;
        if (start >= messages.size()) return;
        List<LlmMessage> kept = new ArrayList<>();
        kept.add(messages.get(0));
        kept.addAll(messages.subList(start, messages.size()));
        messages.clear();
        messages.addAll(kept);
    }

    public synchronized PendingAction pending() {
        return pending;
    }

    public synchronized void setPending(PendingAction action) {
        this.pending = action;
        touch();
    }

    public synchronized void clearPending() {
        this.pending = null;
        touch();
    }

    /** 取出并作废，保证 token 一次性。 */
    public synchronized PendingAction takePending(String token) {
        if (pending == null) return null;
        if (token != null && !token.isBlank() && !token.equals(pending.token())) return null;
        PendingAction taken = pending;
        pending = null;
        touch();
        return taken;
    }

    public TaskDraft taskDraft() {
        return taskDraft;
    }

    /**
     * 建任务草稿。跨请求累积：流程、任务名、槽位绑定逐步补齐，
     * 中途用户改主意直接覆盖同一个 slotId。
     */
    public static final class TaskDraft {
        private String flowName;
        private Long flowModelId;
        private String taskName;
        private final Map<String, Long> bindings = new LinkedHashMap<>();
        private final Map<String, String> bindingSource = new LinkedHashMap<>();

        public String flowName() {
            return flowName;
        }

        public Long flowModelId() {
            return flowModelId;
        }

        public String taskName() {
            return taskName;
        }

        public void setFlow(String flowName, Long flowModelId) {
            this.flowName = flowName;
            if (flowModelId != null) this.flowModelId = flowModelId;
        }

        public void setTaskName(String taskName) {
            this.taskName = taskName;
        }

        public Map<String, Long> bindings() {
            return Map.copyOf(bindings);
        }

        public String sourceOf(String slotId) {
            return bindingSource.getOrDefault(slotId, "AGENT_INFERRED");
        }

        public void bind(String slotId, Long instanceId, String source) {
            if (slotId == null) return;
            if (instanceId == null) {
                bindings.remove(slotId);
                bindingSource.remove(slotId);
                return;
            }
            bindings.put(slotId, instanceId);
            bindingSource.put(slotId, source == null ? "AGENT_INFERRED" : source);
        }

        public void unbind(String slotId) {
            bindings.remove(slotId);
            bindingSource.remove(slotId);
        }

        public void clear() {
            flowName = null;
            flowModelId = null;
            taskName = null;
            bindings.clear();
            bindingSource.clear();
        }

        public boolean hasFlow() {
            return flowModelId != null;
        }
    }
}
