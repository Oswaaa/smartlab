package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Read-only view of FLOW_NODE; writes are owned by the workflow aggregate. */
@RestController
@RequestMapping("/api/workflow/node")
public class FlowNodeController {
    private final FlowNodeService service;

    public FlowNodeController(FlowNodeService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<FlowNode>> list(@RequestParam(required = false) Long flowModelId) {
        return ApiResponse.ok(service.listByFlowModel(flowModelId));
    }

    @GetMapping("/{id}")
    public ApiResponse<FlowNode> get(@PathVariable Long id) {
        FlowNode entity = service.getById(id);
        return entity == null ? ApiResponse.fail("流程节点不存在") : ApiResponse.ok(entity);
    }
}
