package com.smartlab.management.controller.resource.scene;

import com.smartlab.management.dto.common.ApiResponse;
import com.smartlab.management.entity.resource.scene.SceneDetail;
import com.smartlab.management.entity.resource.scene.SceneMain;
import com.smartlab.management.service.db.resource.scene.SceneService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scene")
/**
 * 实验室多设备协同工作场景（Scene）定义与执行控制器。用于一键编排并控制场景下所有设备的初始逻辑状态与指令广播。
 */
public class SceneController {

    private final SceneService service;

    public SceneController(SceneService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<SceneMain>> listScenes() {
        return ApiResponse.ok(service.listScenes());
    }

    @GetMapping("/{id}")
    public ApiResponse<SceneMain> getScene(@PathVariable Long id) {
        SceneMain entity = service.getScene(id);
        return entity == null ? ApiResponse.fail("场景不存在") : ApiResponse.ok(entity);
    }

    @PostMapping("/save")
    public ApiResponse<SceneMain> saveScene(@RequestBody SceneMain entity) {
        try {
            return ApiResponse.ok(service.saveScene(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<String> deleteScene(@PathVariable Long id) {
        try {
            service.deleteScene(id);
            return ApiResponse.ok("删除成功");
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/detail/list")
    public ApiResponse<List<SceneDetail>> listDetails(@RequestParam(required = false) Long sceneId) {
        return ApiResponse.ok(service.listSceneDetails(sceneId));
    }

    @PostMapping("/detail/save")
    public ApiResponse<SceneDetail> saveDetail(@RequestBody SceneDetail entity) {
        try {
            return ApiResponse.ok(service.saveSceneDetail(entity));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }


}

