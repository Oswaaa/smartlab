package com.smartlab.management.service.db.resource.scene;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.scene.SceneDetail;
import com.smartlab.management.entity.resource.scene.SceneMain;
import com.smartlab.management.mapper.resource.scene.SceneDetailMapper;
import com.smartlab.management.mapper.resource.scene.SceneMainMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景表服务。
 *
 * 对应 SCENE_MAIN 与 SCENE_DETAIL 表。
 */
@Service
/**
 * Scene业务持久层核心操作服务。
 */
public class SceneService {

    private final SceneMainMapper sceneMainMapper;
    private final SceneDetailMapper sceneDetailMapper;

    public SceneService(SceneMainMapper sceneMainMapper, SceneDetailMapper sceneDetailMapper) {
        this.sceneMainMapper = sceneMainMapper;
        this.sceneDetailMapper = sceneDetailMapper;
    }

    public List<SceneMain> listScenes() {
        return sceneMainMapper.selectList(Wrappers.<SceneMain>lambdaQuery().orderByDesc(SceneMain::getId));
    }

    public SceneMain getScene(Long id) {
        return sceneMainMapper.selectById(id);
    }

    public SceneMain saveScene(SceneMain scene) {
        LocalDateTime now = LocalDateTime.now();
        if (scene.getId() == null) {
            scene.setCreateTime(now);
            scene.setUpdateTime(now);
            sceneMainMapper.insert(scene);
        } else {
            scene.setUpdateTime(now);
            sceneMainMapper.updateById(scene);
        }
        return scene;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteScene(Long id) {
        sceneDetailMapper.delete(Wrappers.<SceneDetail>lambdaQuery().eq(SceneDetail::getSceneId, id));
        sceneMainMapper.deleteById(id);
    }

    public List<SceneDetail> listSceneDetails() {
        return sceneDetailMapper.selectList(Wrappers.<SceneDetail>lambdaQuery().orderByDesc(SceneDetail::getId));
    }

    public List<SceneDetail> listSceneDetails(Long sceneId) {
        if (sceneId == null) {
            return listSceneDetails();
        }
        return sceneDetailMapper.selectList(
                Wrappers.<SceneDetail>lambdaQuery()
                        .eq(SceneDetail::getSceneId, sceneId)
                        .orderByDesc(SceneDetail::getId)
        );
    }

    public SceneDetail saveSceneDetail(SceneDetail detail) {
        if (detail.getId() == null) {
            detail.setCreateTime(LocalDateTime.now());
            sceneDetailMapper.insert(detail);
        } else {
            sceneDetailMapper.updateById(detail);
        }
        return detail;
    }

    public void deleteSceneDetail(Long id) {
        sceneDetailMapper.deleteById(id);
    }
}



