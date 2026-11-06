package com.smartlab.agent.api;

/**
 * 一轮对话的输入。
 *
 * @param prompt    用户这一句
 * @param sessionId 上一轮返回的会话号；首轮留空，服务端新建
 */
public record WorkflowGenerateRequest(String prompt, String sessionId) {

    public WorkflowGenerateRequest(String prompt) {
        this(prompt, null);
    }
}
