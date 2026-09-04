package com.smartlab.agent.nudge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConceptDictionaryTest {
    private final AgentConceptDictionary dictionary = new AgentConceptDictionary();

    @Test
    void loadsConceptCardsWithoutJsonSamples() {
        assertFalse(dictionary.concepts().isEmpty());
        for (AgentConceptDictionary.Concept concept : dictionary.concepts()) {
            assertFalse(concept.body.contains("```"), concept.title);
            assertFalse(concept.body.contains("{"), concept.title);
            assertFalse(concept.keywords.isEmpty(), concept.title);
        }
    }

    @Test
    void matchesPortQuestionToDataflowCard() {
        String blob = String.join("\n", dictionary.match("温度从哪来？数据端口怎么连？"));
        assertTrue(blob.contains("【数据端口】"));
        assertTrue(blob.contains("控制连线只决定谁被激活") || blob.contains("读数不会"));
        assertFalse(blob.contains("list_device_catalog"));
    }

    @Test
    void matchesConfirmationPauseToAmbiguityCard() {
        String blob = String.join("\n", dictionary.match("请确认第 3 步的语义，我先不保存。"));
        assertTrue(blob.contains("【含糊描述】"));
        assertTrue(blob.contains("没有人在线回答"));
    }
}
