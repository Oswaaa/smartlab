package com.smartlab.management.service.db.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.engine.workflow.WorkflowDefinitionCompiler;
import com.smartlab.global.contract.TaskLifecycleState;
import com.smartlab.global.contract.WorkflowNodeType;
import com.smartlab.management.dto.workflow.WorkflowDetailResponse;
import com.smartlab.management.dto.workflow.WorkflowIssue;
import com.smartlab.management.dto.workflow.WorkflowModelDocument;
import com.smartlab.management.dto.workflow.WorkflowModelDocuments;
import com.smartlab.management.dto.workflow.WorkflowPreparationResponse;
import com.smartlab.management.dto.workflow.WorkflowSimulateRequest;
import com.smartlab.management.dto.workflow.WorkflowSimulationReport;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.service.db.resource.device.DeviceInstanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowSimulationService {

    private final WorkflowService workflowService;
    private final DeviceInstanceService deviceInstanceService;
    private final WorkflowStructureWalker structureWalker;
    private final long timeoutSeconds;

    public WorkflowSimulationService(WorkflowService workflowService,
                                       DeviceInstanceService deviceInstanceService,
                                       WorkflowStructureWalker structureWalker,
                                       @Value("${smartlab.workflow.simulation-timeout-seconds:60}") long timeoutSeconds) {
        this.workflowService = workflowService;
        this.deviceInstanceService = deviceInstanceService;
        this.structureWalker = structureWalker;
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("流程模拟超时秒数必须大于0");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    public WorkflowSimulationReport simulate(WorkflowSimulateRequest request) {
        if (request == null || (request.flowModelId() == null && request.document() == null)) {
            return failed(null, List.of(issue("SIM_REQUEST_INVALID", "", "流程模拟请求缺少 flowModelId 或 document",
                    "传入已保存流程 ID，或提交当前画布 document")));
        }
        WorkflowModelDocument document = request.document();
        Long flowModelId = request.flowModelId();
        WorkflowDefinitionCompiler.CompiledWorkflow compiled;
        JsonNode interfaceConnections = null;
        boolean inMemory = document != null;
        if (document != null) {
            WorkflowPreparationResponse validated = workflowService.validate(document);
            List<WorkflowIssue> blocking = blocking(validated.issues());
            if (!blocking.isEmpty()) {
                return failed(validated.definition() == null ? flowModelId : validated.definition().getId(), blocking);
            }
            WorkflowDetailResponse definition = validated.definition();
            WorkflowModelDocument walkDocument = definition == null
                    ? WorkflowModelDocuments.sanitizeDocument(document)
                    : WorkflowModelDocuments.toDocument(definition);
            try {
                compiled = workflowService.compileDocument(walkDocument);
            } catch (RuntimeException error) {
                return failed(definition == null ? flowModelId : definition.getId(),
                        List.of(issue("SIM_FAILED", "", error.getMessage(), "根据错误修复流程后重试")));
            }
            interfaceConnections = walkDocument.getInterfaceConnections();
            flowModelId = definition == null ? document.flowModelId() : definition.getId();
        } else {
            WorkflowDetailResponse definition = workflowService.getDefinition(flowModelId);
            if (definition == null) {
                return failed(flowModelId, List.of(issue("WORKFLOW_NOT_FOUND", String.valueOf(flowModelId),
                        "工作流模型不存在: " + flowModelId, "先保存流程草稿")));
            }
            WorkflowPreparationResponse validated = workflowService.validate(WorkflowModelDocuments.toDocument(definition));
            List<WorkflowIssue> blocking = blocking(validated.issues());
            if (!blocking.isEmpty()) {
                return failed(flowModelId, blocking);
            }
            try {
                compiled = workflowService.compileDefinition(flowModelId);
            } catch (RuntimeException error) {
                return failed(flowModelId, List.of(issue("SIM_FAILED", "", error.getMessage(),
                        "根据错误修复流程后重试")));
            }
        }

        List<DeviceInstances> temps = new ArrayList<>();
        try {
            Map<Long, DeviceInstances> tempsByModel = new LinkedHashMap<>();
            for (Long deviceModelId : collectDeviceModelIds(compiled)) {
                DeviceInstances temp = tempsByModel.computeIfAbsent(deviceModelId,
                        deviceInstanceService::createTemporary);
                temps.add(temp);
            }

            WorkflowStructureWalker.WalkResult walked = inMemory
                    ? structureWalker.walk(compiled, interfaceConnections, flowModelId, tempsByModel,
                    Instant.now().plusSeconds(timeoutSeconds))
                    : structureWalker.walk(flowModelId, tempsByModel, Instant.now().plusSeconds(timeoutSeconds));
            return toReport(flowModelId, walked);
        } catch (RuntimeException error) {
            return failed(flowModelId, List.of(issue("SIM_FAILED", "", error.getMessage(),
                    "根据错误修复流程后重试")));
        } finally {
            for (DeviceInstances temp : tempsByUnique(temps)) {
                try {
                    deviceInstanceService.deleteTemporary(temp.getId());
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    public WorkflowSimulationReport report(Long taskId) {
        return failed(null, List.of(issue("SIM_NO_TASK", taskId == null ? "" : String.valueOf(taskId),
                "流程模拟不创建任务，报告只在当次模拟执行的返回里", "在设计器中重新点「模拟执行」")));
    }

    private LinkedHashSet<Long> collectDeviceModelIds(WorkflowDefinitionCompiler.CompiledWorkflow compiled) {
        LinkedHashSet<Long> models = new LinkedHashSet<>();
        collectDeviceModelIds(compiled, new LinkedHashSet<>(), models);
        return models;
    }

    private void collectDeviceModelIds(WorkflowDefinitionCompiler.CompiledWorkflow compiled, Set<Long> visiting,
                                          LinkedHashSet<Long> models) {
        if (compiled == null || compiled.nodes() == null) return;
        for (JsonNode node : compiled.nodes().values()) {
            if (node == null) continue;
            if (WorkflowNodeType.DEV_NODE.name().equals(node.path("nodeType").asText())) {
                long deviceModelId = node.path("deviceModelId").asLong(0);
                if (deviceModelId > 0) models.add(deviceModelId);
            } else if (WorkflowNodeType.SUBFLOW_NODE.name().equals(node.path("nodeType").asText())) {
                long subFlowId = node.path("subFlowModelId").asLong(0);
                if (subFlowId <= 0 || !visiting.add(subFlowId)) continue;
                collectDeviceModelIds(workflowService.compileDefinition(subFlowId), visiting, models);
            }
        }
    }

    private WorkflowSimulationReport toReport(Long flowModelId, WorkflowStructureWalker.WalkResult walked) {
        if (walked == null) {
            return failed(flowModelId, List.of(issue("SIM_FAILED", "", "流程模拟失败", "根据错误修复流程后重试")));
        }
        return new WorkflowSimulationReport(null, flowModelId,
                walked.walkable() ? TaskLifecycleState.SUCCEEDED.name() : TaskLifecycleState.FAILED.name(),
                walked.walkable(),
                walked.pathTaken() == null ? List.of() : List.copyOf(walked.pathTaken()),
                walked.paths() == null ? List.of() : List.copyOf(walked.paths()),
                walked.issues() == null ? List.of() : List.copyOf(walked.issues()));
    }

    private WorkflowSimulationReport failed(Long flowModelId, List<WorkflowIssue> issues) {
        return new WorkflowSimulationReport(null, flowModelId, TaskLifecycleState.FAILED.name(),
                false, List.of(), List.of(), issues == null ? List.of() : issues);
    }

    private List<DeviceInstances> tempsByUnique(List<DeviceInstances> temps) {
        Map<Long, DeviceInstances> unique = new LinkedHashMap<>();
        for (DeviceInstances temp : temps) {
            if (temp != null && temp.getId() != null) {
                unique.putIfAbsent(temp.getId(), temp);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<WorkflowIssue> blocking(List<WorkflowIssue> issues) {
        if (issues == null) return List.of();
        return issues.stream().filter(WorkflowIssue::blocking).toList();
    }

    private WorkflowIssue issue(String code, String elementId, String message, String suggestion) {
        return new WorkflowIssue(code, "SIMULATION", "simulate", "workflow", elementId, true, message, suggestion);
    }
}
