package com.smartlab.agent.operator;

/**
 * 一次工具调用所处的上下文。
 *
 * @param session  会话（跨请求）
 * @param userId   当前登录用户
 * @param userName 当前登录用户名
 */
public record OperatorContext(OperatorSession session, Long userId, String userName) {
}
