package com.smartlab.agent.nudge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AgentNudge {
    private AgentNudge() {
    }

    public static String build(boolean deviceCatalogFetched, boolean workflowCatalogFetched,
                               Boolean lastValidateClean, Boolean lastSimulateWalkable,
                               String assistantText, AgentConceptDictionary dictionary) {
        String action = nextAction(deviceCatalogFetched, workflowCatalogFetched,
                lastValidateClean, lastSimulateWalkable, assistantText);
        List<String> cards = dictionary == null ? List.of() : dictionary.match(assistantText);
        if (cards.isEmpty()) return action;
        return action + "\n\n下面是针对你刚才疑问的概念说明，用来写稿，不是让你再向人确认：\n\n"
                + String.join("\n\n", cards);
    }

    static String nextAction(boolean deviceCatalogFetched, boolean workflowCatalogFetched,
                              Boolean lastValidateClean, Boolean lastSimulateWalkable,
                              String assistantText) {
        if (!deviceCatalogFetched || !workflowCatalogFetched) {
            return missingCatalogAction(deviceCatalogFetched, workflowCatalogFetched);
        }
        if (lastValidateClean == null) {
            return "设备目录和流程目录已经查过，不要再整表列出。不要向人提问。"
                    + "对初筛设备调用 get_device_model 做终判，按结构说明编写稿件并调用 validate_workflow。"
                    + "含糊处按最合理解读，写进 metadata.description。";
        }
        if (!lastValidateClean) {
            return "根据最近一次 validate_workflow 的 issues 改稿后再校验。"
                    + "需要重新查看设备或流程时可以重新调用对应工具。不要向人提问。";
        }
        if (lastSimulateWalkable == null) {
            return "校验已无 blocking。按第 7 步调用 simulate_workflow。不要向人提问。";
        }
        if (!lastSimulateWalkable) {
            return "走图未通过。根据 simulate_workflow 的 issues 改稿后从第 5 步再校验；"
                    + "SIM_NO_ADAPTER_EVENT 通常改稿走不通，停止并向人说明。不要向人提问。";
        }
        if (looksLikeQuestion(assistantText)) {
            return "走图已通过。不要向人确认。先按第 8 步检查稿是否覆盖需求和细节；"
                    + "若还漏了数据端口或条件变量，先按下面的概念改稿并再次 validate_workflow；"
                    + "改完无 blocking 后再 simulate_workflow，通过后 save_draft。";
        }
        return "走图已通过。按第 8 步确认细节到位后立即调用 save_draft。不要向人确认。";
    }

    private static String missingCatalogAction(boolean deviceCatalogFetched, boolean workflowCatalogFetched) {
        List<String> missing = new ArrayList<>();
        if (!deviceCatalogFetched) missing.add("list_device_catalog");
        if (!workflowCatalogFetched) missing.add("list_workflow_catalog");
        String tools = String.join(" 和 ", missing);
        if (!deviceCatalogFetched) {
            return "不要用散文结束，也不要向人提问。先调用 " + tools + "，对候选设备再 get_device_model。";
        }
        return "不要用散文结束，也不要向人提问。先调用 " + tools + "。";
    }

    static boolean looksLikeQuestion(String text) {
        if (text == null || text.isBlank()) return false;
        String haystack = text.toLowerCase(Locale.ROOT);
        return containsAny(haystack,
                "请确认", "请补充", "需要确认", "向你确认", "先不保存", "请明确", "等你确认", "歧义");
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
