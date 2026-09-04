package com.smartlab.management.controller.resource.adapter;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.dto.resource.adapter.VirtualLeaseRequest;
import com.smartlab.management.entity.resource.adapter.VirtualLease;
import com.smartlab.management.service.db.resource.adapter.VirtualLeaseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/adapter/lease")
/**
 * 虚拟点租约查询、申请与释放。租赁/释放走 Adapter MQTT，不把 /save 当租赁入口。
 */
public class VirtualLeaseController {

    private final VirtualLeaseService service;

    public VirtualLeaseController(VirtualLeaseService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<VirtualLease>> list(@RequestParam(required = false) Long physicalInstanceId,
                                                  @RequestParam(required = false) Long taskId,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(physicalInstanceId, taskId, status));
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<VirtualLease>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                     @RequestParam(defaultValue = "20") long pageSize,
                                                     @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "adapter_name", "virtual_device_point", "status"));
    }

    @GetMapping("/{id}")
    public ApiResponse<VirtualLease> get(@PathVariable Long id) {
        VirtualLease entity = service.getById(id);
        return entity == null ? ApiResponse.fail("虚拟租约不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/request")
    public ApiResponse<VirtualLease> request(@RequestBody VirtualLeaseRequest body) {
        try {
            Long physicalInstanceId = body == null ? null : body.physicalInstanceId();
            Long taskId = body == null ? null : body.taskId();
            return ApiResponse.ok(service.beginLease(physicalInstanceId, taskId));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/release/{id}")
    public ApiResponse<VirtualLease> release(@PathVariable Long id) {
        try {
            return ApiResponse.ok(service.beginRelease(id));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/save")
    public ApiResponse<VirtualLease> save(@RequestBody VirtualLease entity) {
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
