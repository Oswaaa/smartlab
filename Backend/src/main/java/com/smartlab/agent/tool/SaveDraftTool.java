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
        return "将通过校验的工作流模型文件存为 DRAFT。不要发布。成功时只返回 flowModelId/status，不含完整 definition。";
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
