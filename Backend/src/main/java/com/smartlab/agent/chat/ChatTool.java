package com.smartlab.agent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.agent.operator.OperatorContext;

/**
 * 统一对话引擎使用的工具接口。
 *
 * <p>系统里原本有两套互不相通的工具：画流程的 {@code AgentTool}（无上下文）
 * 和设备操作的 {@code OperatorTool}（需要会话与用户）。两套各缺一半能力，
 * 导致"让反应釜加热"被接成"保存一张流程草稿"。
 *
 * <p>这个接口只做一件事：把两者包成同一种形状，交给同一个循环调度。
 * 工具本体的实现和权限校验都不变。
 */
public interface ChatTool {

    /** 工具归属，决定模型在没有明确指令时该往哪边靠。 */
    enum Group {
        /** 画流程、改流程、校验、存草稿。 */
        WORKFLOW,
        /** 单台设备的动作、建任务。 */
        DEVICE
    }

    String name();

    String description();

    JsonNode parameterSchema();

    JsonNode execute(JsonNode arguments, OperatorContext context);

    Group group();
}
