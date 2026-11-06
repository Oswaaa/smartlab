package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Operator 专用工具接口。
 *
 * <p>刻意<b>不实现</b> {@code AgentTool}：现有 AgentLoop 的构造器是 {@code List<AgentTool>}，
 * 会把容器里所有实现全量收进去，而那个 Agent 的职责是画流程草稿、明确不碰设备。
 * 两个接口物理隔离，才不会让画流程的模型突然看见"下发控制指令"的能力。
 */
public interface OperatorTool {

    String name();

    String description();

    JsonNode parameterSchema();

    JsonNode execute(JsonNode arguments, OperatorContext context);
}
