package com.smartlab.agent.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.agent.AgentProperties;
import com.smartlab.agent.llm.LlmCompletion;
import com.smartlab.agent.llm.LlmMessage;
import com.smartlab.agent.llm.LlmToolCall;
import com.smartlab.agent.llm.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorLoopTest {

    /** 固定返回一个 JSON 的假工具，用来验证路由与消息写回。 */
    private static OperatorTool echoTool() {
        return new OperatorTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "原样返回传入内容";
            }

            @Override
            public JsonNode parameterSchema() {
                return OperatorJson.schema("{\"type\":\"object\",\"properties\":{}}");
            }

            @Override
            public JsonNode execute(JsonNode arguments, OperatorContext context) {
                return OperatorJson.ok();
            }
        };
    }

    private static AgentProperties properties() {
        AgentProperties properties = new AgentProperties();
        properties.setEnabled(true);
        return properties;
    }

    @Test
    void 模型调用工具后把结果写回会话() {
        MockLlmClient client = new MockLlmClient(List.of(
                new LlmCompletion(null, List.of(new LlmToolCall("c1", "echo", "{}"))),
                new LlmCompletion("已经查完了", List.of())
        ));
        OperatorLoop loop = new OperatorLoop(client, List.of(echoTool()), properties());
        OperatorSession session = new OperatorSession("s1");

        OperatorLoop.OperatorReply reply = loop.chat(session, new OperatorContext(session, null, "admin"), "随便看看");

        assertEquals("已经查完了", reply.message());
        assertEquals(List.of("echo"), reply.trace());
        assertNull(reply.card());

        List<LlmMessage> history = session.messages();
        assertEquals("system", history.get(0).role());
        assertEquals("user", history.get(1).role());
        assertTrue(history.stream().anyMatch(m -> "assistant".equals(m.role()) && m.toolCalls() != null));
        assertTrue(history.stream().anyMatch(m -> "tool".equals(m.role()) && "echo".equals(m.name())));
        assertEquals("已经查完了", history.get(history.size() - 1).content());
    }

    @Test
    void 第二轮对话能带上第一轮的历史() {
        MockLlmClient client = new MockLlmClient(List.of(
                new LlmCompletion("先回答第一句", List.of()),
                new LlmCompletion("再回答第二句", List.of())
        ));
        OperatorLoop loop = new OperatorLoop(client, List.of(echoTool()), properties());
        OperatorSession session = new OperatorSession("s2");
        OperatorContext context = new OperatorContext(session, null, "admin");

        loop.chat(session, context, "第一句");
        loop.chat(session, context, "第二句");

        List<LlmMessage> secondCall = client.messagesAt(1);
        assertTrue(secondCall.stream().anyMatch(m -> "第一句".equals(m.content())),
                "第二次请求必须包含第一轮的用户消息，否则反问就接不上");
        assertEquals(2, session.messages().stream().filter(m -> "user".equals(m.role())).count());
    }

    @Test
    void 未知工具不会让整轮崩掉() {
        MockLlmClient client = new MockLlmClient(List.of(
                new LlmCompletion(null, List.of(new LlmToolCall("c1", "not_exists", "{}"))),
                new LlmCompletion("工具不存在，我换个方式", List.of())
        ));
        OperatorLoop loop = new OperatorLoop(client, List.of(echoTool()), properties());
        OperatorSession session = new OperatorSession("s3");

        OperatorLoop.OperatorReply reply = loop.chat(session, new OperatorContext(session, null, "admin"), "试试");

        assertEquals("工具不存在，我换个方式", reply.message());
        assertTrue(reply.trace().get(0).startsWith("unknown"));
    }

    @Test
    void 参数不是合法JSON时返回结构化错误() {
        MockLlmClient client = new MockLlmClient(List.of(
                new LlmCompletion(null, List.of(new LlmToolCall("c1", "echo", "{不是json"))),
                new LlmCompletion("参数格式有问题", List.of())
        ));
        OperatorLoop loop = new OperatorLoop(client, List.of(echoTool()), properties());
        OperatorSession session = new OperatorSession("s4");

        OperatorLoop.OperatorReply reply = loop.chat(session, new OperatorContext(session, null, "admin"), "试试");

        assertEquals("参数格式有问题", reply.message());
        assertTrue(reply.trace().contains("echo:bad-args"));
    }

    @Test
    void 空输入被拒绝() {
        OperatorLoop loop = new OperatorLoop(new MockLlmClient(List.of()), List.of(), properties());
        OperatorSession session = new OperatorSession("s5");
        try {
            loop.chat(session, new OperatorContext(session, null, "admin"), "  ");
            throw new AssertionError("应当抛出参数异常");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    void 待确认动作会随回复一起返回() {
        MockLlmClient client = new MockLlmClient(List.of(
                new LlmCompletion("已准备好，请确认", List.of())
        ));
        OperatorLoop loop = new OperatorLoop(client, List.of(echoTool()), properties());
        OperatorSession session = new OperatorSession("s6");
        PendingAction.Device pending = new PendingAction.Device("t1", java.time.Instant.now(),
                220, "反应釜#1", "capability_1", "加热", java.util.Map.of("parameter_1", 500.0),
                new ConfirmCard(ConfirmCard.KIND_DEVICE_ACTION, "确认加热", "即将加热", List.of(), List.of(),
                        List.of(), List.of(), List.of(), true, true));
        session.setPending(pending);

        OperatorLoop.OperatorReply reply = loop.chat(session, new OperatorContext(session, null, "admin"), "加热");

        assertEquals("t1", reply.token());
        assertNotNull(reply.card());
        assertEquals(ConfirmCard.KIND_DEVICE_ACTION, reply.card().kind());
    }
}
