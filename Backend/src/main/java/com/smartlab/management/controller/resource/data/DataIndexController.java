package com.smartlab.management.controller.resource.data;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.service.db.resource.data.DataIndexService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data/index")
/**
 * 数据采集指标主索引控制器。管理系统支持采集的所有实验环境与设备运行原始指标项。
 */
public class DataIndexController {

    private final DataIndexService service;

    public DataIndexController(DataIndexService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<DataIndex>> list(@RequestParam(required = false) Long deviceInstanceId,
                                             @RequestParam(required = false) Long templateId) {
        if (deviceInstanceId != null) {
            return ApiResponse.ok(service.listByDeviceInstance(deviceInstanceId));
        }
        if (templateId != null) {
            return ApiResponse.ok(service.listByTemplate(templateId));
        }
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<DataIndex>> page(@RequestParam(defaultValue = "1") long pageNo,
                                                   @RequestParam(defaultValue = "20") long pageSize,
                                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNo, pageSize, keyword, "data_table", "data_desc"));
    }

    @GetMapping("/{id}")
    public ApiResponse<DataIndex> get(@PathVariable Long id) {
        DataIndex entity = service.getById(id);
        return entity == null ? ApiResponse.fail("数据索引不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<DataIndex> save(@RequestBody DataIndex entity) {
        try {
            return ApiResponse.ok(service.save(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 创建数据集并自动创建对应的物理数据表。
     */
    @PostMapping("/create-dataset")
    public ApiResponse<DataIndex> createDataSet(@RequestBody Map<String, Object> payload) {
        try {
            Long templateId = longValue(payload.get("templateId"));
            Long deviceInstanceId = longValue(payload.get("deviceInstanceId"));
            String dataDesc = payload.get("dataDesc") == null ? null : String.valueOf(payload.get("dataDesc"));
            return ApiResponse.ok(service.createDataSet(templateId, deviceInstanceId, dataDesc));
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

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }
}

