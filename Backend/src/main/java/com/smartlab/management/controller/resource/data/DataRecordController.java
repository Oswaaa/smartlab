package com.smartlab.management.controller.resource.data;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.service.db.resource.data.DataRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 动态数据记录查询控制器。
 *
 * 通过 DATA_INDEX 定位真实物理数据表，然后分页读取记录。
 */
@RestController
@RequestMapping("/api/data/record")
/**
 * 物理实验历史采集数据流记录检索控制器。用于查询设备上传的历史时序遥测值或事件快照。
 */
public class DataRecordController {

    private final DataRecordService dataRecordService;

    public DataRecordController(DataRecordService dataRecordService) {
        this.dataRecordService = dataRecordService;
    }

    /**
     * 查询所有数据集索引。
     */
    @GetMapping("/datasets")
    public ApiResponse<List<DataIndex>> datasets() {
        return ApiResponse.ok(dataRecordService.listDataSets());
    }

    /**
     * 按模板 ID 查询第一张匹配的数据表记录。
     */
    @GetMapping("/page/{templateId}")
    public ApiResponse<PageResult<Map<String, Object>>> pageByTemplate(@PathVariable Long templateId,
                                                                       @RequestParam(defaultValue = "1") long pageNo,
                                                                       @RequestParam(defaultValue = "50") long pageSize) {
        try {
            return ApiResponse.ok(dataRecordService.pageByTemplateId(templateId, pageNo, pageSize));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 按 DATA_INDEX.ID 查询指定数据集记录。
     */
    @GetMapping("/dataset/{dataIndexId}")
    public ApiResponse<PageResult<Map<String, Object>>> pageByDataIndex(@PathVariable Long dataIndexId,
                                                                        @RequestParam(defaultValue = "1") long pageNo,
                                                                        @RequestParam(defaultValue = "50") long pageSize) {
        try {
            return ApiResponse.ok(dataRecordService.pageByDataIndexId(dataIndexId, pageNo, pageSize));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}

