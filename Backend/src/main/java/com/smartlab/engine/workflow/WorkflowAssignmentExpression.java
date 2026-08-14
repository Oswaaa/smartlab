package com.smartlab.engine.workflow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** BRANCH/AGGREGATE 节点的单目标赋值表达式。 */
public record WorkflowAssignmentExpression(String targetName, String valueExpression) {
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^\\s*([\\p{L}_][\\p{L}\\p{N}_]*)\\s*=\\s*(.+?)\\s*$");

    public static WorkflowAssignmentExpression parse(String source) {
        Matcher matcher = ASSIGNMENT.matcher(source == null ? "" : source);
        if (!matcher.matches() || matcher.group(2).isBlank()) {
            throw new IllegalArgumentException("节点expression必须使用‘目标内部变量 = 计算表达式’赋值格式");
        }
        return new WorkflowAssignmentExpression(matcher.group(1), matcher.group(2));
    }
}
