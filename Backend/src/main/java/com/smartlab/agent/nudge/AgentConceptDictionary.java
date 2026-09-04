package com.smartlab.agent.nudge;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AgentConceptDictionary {
    static final int MATCH_LIMIT = 3;

    private final List<Concept> concepts;

    public AgentConceptDictionary() {
        this(read("agent/prompts/concepts.md"));
    }

    AgentConceptDictionary(String markdown) {
        this.concepts = parse(markdown);
    }

    public List<String> match(String text) {
        if (text == null || text.isBlank() || concepts.isEmpty()) return List.of();
        String haystack = text.toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (Concept concept : concepts) {
            if (matched.size() >= MATCH_LIMIT) break;
            if (concept.matches(haystack)) {
                matched.add(concept.card());
            }
        }
        return List.copyOf(matched);
    }

    List<Concept> concepts() {
        return concepts;
    }

    static List<Concept> parse(String markdown) {
        List<Concept> parsed = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) return parsed;
        String normalized = markdown.replace("\r\n", "\n");
        String[] parts = normalized.split("(?m)^## ");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("# ")) continue;
            int newline = indexOfNewline(trimmed);
            if (newline < 0) continue;
            String title = trimmed.substring(0, newline).trim();
            String rest = trimmed.substring(newline + 1).trim();
            if (title.isEmpty() || rest.isEmpty()) continue;
            List<String> keywords = List.of();
            String body = rest;
            if (startsWithIgnoreCase(rest, "keywords:")) {
                int bodyBreak = indexOfNewline(rest);
                String keywordLine = bodyBreak < 0 ? rest.substring("keywords:".length()) : rest.substring("keywords:".length(), bodyBreak);
                keywords = splitKeywords(keywordLine);
                body = bodyBreak < 0 ? "" : rest.substring(bodyBreak + 1).trim();
            }
            if (body.startsWith("---")) {
                int afterRule = indexOfNewline(body);
                body = afterRule < 0 ? "" : body.substring(afterRule + 1).trim();
            }
            if (title.isEmpty() || body.isEmpty()) continue;
            parsed.add(new Concept(title, keywords, body));
        }
        return parsed;
    }

    private static List<String> splitKeywords(String line) {
        List<String> keywords = new ArrayList<>();
        if (line == null) return keywords;
        for (String piece : line.split("[,，]")) {
            String keyword = piece.trim();
            if (!keyword.isEmpty()) keywords.add(keyword);
        }
        return keywords;
    }

    private static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.length() >= prefix.length() && text.substring(0, prefix.length()).equalsIgnoreCase(prefix);
    }

    private static int indexOfNewline(String text) {
        int unix = text.indexOf('\n');
        return unix < 0 ? text.indexOf('\r') : unix;
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取代理资源: " + path, exception);
        }
    }

    static final class Concept {
        final String title;
        final List<String> keywords;
        final String body;

        Concept(String title, List<String> keywords, String body) {
            this.title = title;
            this.keywords = List.copyOf(keywords);
            this.body = body;
        }

        boolean matches(String haystack) {
            for (String keyword : keywords) {
                if (keyword.isEmpty()) continue;
                if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        }

        String card() {
            return "【" + title + "】\n" + body;
        }
    }
}
