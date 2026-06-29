package com.smartlab.management.controller.resource.data;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.service.db.resource.data.DataTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据模板控制器。
 *
 * 对应 DATA_TEMPLATE_MAIN 和 DATA_TEMPLATE_DETAIL。
 */
@RestController
@RequestMapping("/api/data/template")
/**
 * 实验数据采集模版配置控制器。定义特定实验方案下需要聚合监测的指标模板及图表映射规则。
 */
public class DataTemplateController {

    private final DataTemplateService dataTemplateService;

    public DataTemplateController(DataTemplateService dataTemplateService) {
        this.dataTemplateService = dataTemplateService;
    }

    @GetMapping("/list")
    public ApiResponse<List<DataTemplateMain>> list() {
        return ApiResponse.ok(dataTemplateService.list());
    }

    @GetMapping("/{id}/details")
    public ApiResponse<List<DataTemplateDetail>> details(@PathVariable Long id) {
        return ApiResponse.ok(dataTemplateService.listDetails(id));
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, String>> save(@RequestBody Map<String, Object> payload) {
        try {
            DataTemplateMain template = dataTemplateService.savePayload(payload);
            return ApiResponse.ok(Map.of("templateId", String.valueOf(template.getId())));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 使用明确 DTO 保存模板主表和字段明细。
     */
    @PostMapping("/save-structured")
    public ApiResponse<DataTemplateMain> saveStructured(@RequestBody DataTemplateSaveDTO dto) {
        try {
            return ApiResponse.ok(dataTemplateService.saveTemplate(dto));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        try {
            dataTemplateService.delete(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}


