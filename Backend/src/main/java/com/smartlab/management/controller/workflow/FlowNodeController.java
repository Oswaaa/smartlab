package com.smartlab.management.controller.workflow;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.service.db.workflow.FlowNodeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflow/node")
/**
 * 工作流设计节点定义与参数模型控制器。管理流程编排画布中各任务节点（如延时、读属性、发指令）的结构规范。
 */
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

    @PostMapping("/save")
    public ApiResponse<FlowNode> save(@RequestBody FlowNode entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}

