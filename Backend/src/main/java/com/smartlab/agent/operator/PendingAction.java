package com.smartlab.agent.operator;

import com.smartlab.management.dto.workflow.WorkflowModelDocument;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 待确认动作。
 *
 * <p>生命周期：prepare 工具生成 → 用户点确认 → 重新校验 → 执行 → 立即作废。
 * token 一次性，用过即失效，避免连点或重放。
 *
 * <p>同一会话里同时只允许存在一个待确认动作。这不是洁癖，而是用来<b>在物理上封死流程编排</b>：
 * 模型无法把"先加热再抽真空"拆成两个动作串起来，因为第二个 prepare 会被拒绝。
 */
public sealed interface PendingAction {

    String token();

    Instant createdAt();

    ConfirmCard card();

    /** 单设备控制。 */
    record Device(
            String token,
            Instant createdAt,
            long instanceId,
            String instanceName,
            String capabilityName,
            String capabilityDisplayName,
            Map<String, Object> parameters,
            ConfirmCard card
    ) implements PendingAction {
    }

    /** 创建一个任务（只创建，不启动）。 */
    record Task(
            String token,
            Instant createdAt,
            long flowModelId,
            String flowName,
            String taskName,
            List<SlotBinding> bindings,
            ConfirmCard card
    ) implements PendingAction {
    }

    /**
     * 启动一个已经存在的任务。
     *
     * <p>和上面两条一样，这里只负责"准备"，真正 {@code taskService.start} 发生在用户点确认之后。
     * 启动前的状态复检放到 confirm 里再做一次，因为预演到点确认之间任务可能已经被人启动了。
     */
    record TaskStart(
            String token,
            Instant createdAt,
            long taskId,
            String taskName,
            long flowModelId,
            String flowName,
            String statusBefore,
            String executionKind,
            ConfirmCard card
    ) implements PendingAction {
    }

    /**
     * 一份待确认的流程草稿。
     *
     * <p>模型调用 {@code save_draft} 时不直接落库，而是把整份 document 挂在这里，
     * 用户点确认之后才真正写库。这样做的好处是"画流程"和"控设备"在体感上一致：
     * 都不会因为模型说了一句就产生副作用。
     *
     * <p>document 原样保留，不做裁剪——确认时要拿它重新走一遍编译与规范化，
     * 中间任何一步丢字段都会导致落库结果和用户看到的卡片不一致。
     */
    record FlowDraft(
            String token,
            Instant createdAt,
            WorkflowModelDocument document,
            String flowName,
            int nodeCount,
            ConfirmCard card
    ) implements PendingAction {
    }

    /**
     * 槽位绑定结果。
     *
     * @param source USER 表示用户在对话里明确指定；AGENT_INFERRED 表示服务端按型号唯一实例推出来的
     */
    record SlotBinding(String slotId, String nodeName, long deviceInstanceId,
                       String instanceName, String modelName, String source) {
    }

    /** 剩余有效时间（毫秒），已过期返回 0。 */
    default long remainingMillis(long ttlMillis) {
        long elapsed = Instant.now().toEpochMilli() - createdAt().toEpochMilli();
        return Math.max(0, ttlMillis - elapsed);
    }
}
