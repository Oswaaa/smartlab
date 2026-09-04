package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.service.db.workflow.WorkflowService;
import org.springframework.stereotype.Component;

@Component
public class ValidateWorkflowTool implements AgentTool {
    public static final String NAME = "validate_workflow";

    private final WorkflowService workflowService;

    public ValidateWorkflowTool(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "用现有编译器校验工作流草稿，不写库。返回 issues 与 repair（不含规范化后的完整 definition）。保存前必须在无 blocking 时调用。";
    }

    @Override
    public JsonNode parameterSchema() {
        return AgentDocuments.documentParameterSchema();
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        WorkflowModelDocument document = AgentDocuments.requireDocument(arguments);
        WorkflowPreparationResponse prepared = workflowService.validate(document);
        return AgentDocuments.validationNode(prepared);
    }
}
