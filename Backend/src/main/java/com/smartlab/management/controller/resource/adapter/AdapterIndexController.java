package com.smartlab.management.controller.resource.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import com.smartlab.management.service.db.resource.adapter.AdapterIndexService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/adapter/index")
/**
 * 适配器静态元数据管理控制器。提供适配器模板配置的CRUD操作，用于管理支持的物理接入网关通道。
 */
public class AdapterIndexController {

    private final AdapterIndexService service;

    public AdapterIndexController(AdapterIndexService service) {
        this.service = service;
    }

    /**
     * 查询全部 Adapter 索引。
     */
    @GetMapping("/list")
    public ApiResponse<List<AdapterIndex>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * 分页查询 Adapter 索引。
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AdapterIndex>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "adapter_name", "status"));
    }

    /**
     * 按 ID 查询 Adapter 索引。
     */
    @GetMapping("/{id}")
    public ApiResponse<AdapterIndex> get(@PathVariable Long id) {
        AdapterIndex entity = service.getById(id);
        return entity == null ? ApiResponse.fail("Adapter 索引不存在") : ApiResponse.ok(entity);
    }

    /**
     * 保存 Adapter 索引。
     */
    @PostMapping("/save")
    public ApiResponse<AdapterIndex> save(@RequestBody AdapterIndex entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 解析 AdapterRegisterRequest，但不写入 ADAPTER_INDEX。
     */
    @PostMapping("/parse-register")
    public ApiResponse<ObjectNode> parseRegister(@RequestBody Map<String, Object> payload) {
        try {
            return ApiResponse.ok(service.previewRegisterPayload(payload));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 保存 AdapterRegisterRequest 到 ADAPTER_INDEX。
     */
    @PostMapping("/register")
    public ApiResponse<AdapterIndex> register(@RequestBody Map<String, Object> payload) {
        try {
            return ApiResponse.ok(service.register(payload));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{adapterName}/templates")
    public ApiResponse<JsonNode> templates(@PathVariable String adapterName) {
        try {
            return ApiResponse.ok(service.listTemplates(adapterName));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{adapterName}/categories")
    public ApiResponse<JsonNode> categories(@PathVariable String adapterName) {
        try {
            return ApiResponse.ok(service.listAdapterCategories(adapterName));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{adapterName}/device-points")
    public ApiResponse<JsonNode> devicePoints(@PathVariable String adapterName,
                                             @RequestParam String categoryName) {
        try {
            return ApiResponse.ok(service.listDevicePoints(adapterName, categoryName));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{adapterName}/adapter-contract")
    public ApiResponse<ObjectNode> adapterContract(@PathVariable String adapterName,
                                                   @RequestParam String categoryName) {
        try {
            return ApiResponse.ok(service.buildAdapterContract(adapterName, categoryName));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }


    /**
     * 删除 Adapter 索引。
     */
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
