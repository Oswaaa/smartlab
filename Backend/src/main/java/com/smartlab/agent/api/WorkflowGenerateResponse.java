package com.smartlab.agent.api;

import com.smartlab.agent.operator.ConfirmCard;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;

import java.util.List;

/**
 * 一次对话的完整结果。
 *
 * <p>同一个结构同时承载两类产出：
 * <ul>
 *   <li>流程侧：{@code definition} 等字段，只有真的存了草稿才有值；</li>
 *   <li>设备侧：{@code card} 不为空表示有一张待确认卡片，{@code token} 是确认凭证。</li>
 * </ul>
 *
 * <p>{@code sessionId} 必须原样带回下一次请求，否则"反问一句、用户回一句"接不上。
 */
public record WorkflowGenerateResponse(
        WorkflowModelDocument definition,
        Integer version,
        String status,
        Long predecessorId,
        List<WorkflowIssue> issues,
        boolean executable,
        boolean published,
        Long flowModelId,
        List<String> trace,
        List<AgentInteractionLog> logs,
        String summary,
        String sessionId,
        ConfirmCard card,
        String token,
        long pendingTtlSeconds) {

    /** 兼容旧调用：没有会话与卡片的老结构。 */
    public WorkflowGenerateResponse(WorkflowModelDocument definition, Integer version, String status,
                                    Long predecessorId, List<WorkflowIssue> issues, boolean executable,
                                    boolean published, Long flowModelId, List<String> trace,
                                    List<AgentInteractionLog> logs, String summary) {
        this(definition, version, status, predecessorId, issues, executable, published, flowModelId,
                trace, logs, summary, null, null, null, 0);
    }
}
