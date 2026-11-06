package com.smartlab.agent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.agent.operator.OperatorContext;
import com.smartlab.agent.operator.OperatorTool;
import com.smartlab.agent.tool.AgentTool;

/**
 * 把两套既有工具包成 {@link ChatTool}。
 *
 * <p>刻意不做"继承改写"：两个原始接口的实现类散落在十几个文件里，
 * 改接口会牵动全部实现和它们的单测。适配器只在这一层加壳。
 */
public final class ChatTools {

    private ChatTools() {
    }

    public static ChatTool of(AgentTool tool) {
        return new AgentSide(tool);
    }

    public static ChatTool of(OperatorTool tool) {
        return new DeviceSide(tool);
    }

    private static final class AgentSide implements ChatTool {
        private final AgentTool delegate;

        private AgentSide(AgentTool delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public JsonNode parameterSchema() {
            return delegate.parameterSchema();
        }

        @Override
        public JsonNode execute(JsonNode arguments, OperatorContext context) {
            // 画流程的工具不需要会话，签名里的上下文直接忽略
            return delegate.execute(arguments);
        }

        @Override
        public Group group() {
            return Group.WORKFLOW;
        }
    }

    private static final class DeviceSide implements ChatTool {
        private final OperatorTool delegate;

        private DeviceSide(OperatorTool delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public JsonNode parameterSchema() {
            return delegate.parameterSchema();
        }

        @Override
        public JsonNode execute(JsonNode arguments, OperatorContext context) {
            return delegate.execute(arguments, context);
        }

        @Override
        public Group group() {
            return Group.DEVICE;
        }
    }
}
