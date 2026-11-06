package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.stereotype.Component;

@Component
public class SaveDraftTool implements AgentTool {
    public static final String NAME = "save_draft";

    private final WorkflowService workflowService;

    public SaveDraftTool(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "把通过校验的工作流草稿交给用户确认：不写库、不返回 flowModelId，"
                + "只生成一张待确认卡片，用户点「保存草稿」之后才真正存为 DRAFT。"
                + "调用完用两三句话说明流程就停止，不要说已保存。";
    }

    @Override
    public JsonNode parameterSchema() {
        return AgentDocuments.documentParameterSchema();
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        WorkflowModelDocument document = AgentDocuments.requireDocument(arguments);
        return AgentDocuments.saveNode(workflowService.saveDraft(document));
    }
}
