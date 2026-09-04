package com.smartlab.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowSimulateRequest;
import com.smartlab.management.service.db.workflow.WorkflowSimulationService;
import org.springframework.stereotype.Component;

@Component
public class SimulateWorkflowTool implements AgentTool {
    public static final String NAME = "simulate_workflow";

    private final WorkflowSimulationService simulationService;

    public SimulateWorkflowTool(WorkflowSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "在 validate_workflow 无 blocking 之后做流程结构走图：不求值分支条件，设备节点走真实状态机与进程内 Adapter 模拟器。"
                + "在内存编译当前 document，不调用 save_draft、不写库。返回 walkable、pathTaken、paths、issues（含 repair）。不含完整 definition。"
                + "未走通时按 issues 改稿，不要改引擎。";
    }

    @Override
    public JsonNode parameterSchema() {
        return AgentDocuments.documentParameterSchema();
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        WorkflowModelDocument document = AgentDocuments.requireDocument(arguments);
        return AgentDocuments.simulationNode(simulationService.simulate(new WorkflowSimulateRequest(null, document)));
    }
}
