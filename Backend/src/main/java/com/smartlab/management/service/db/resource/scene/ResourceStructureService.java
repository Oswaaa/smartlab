package com.smartlab.management.service.db.resource.scene;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.ResourceStructure;
import com.smartlab.management.mapper.ResourceStructureMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资源结构表服务。
 *
 * 对应 RESOURCE_STRUCTURE 表，用于记录场景内设备实例之间的连接关系。
 */
@Service
public class ResourceStructureService extends ManagementCrudService<ResourceStructure> {

    private final ResourceStructureMapper mapper;

    public ResourceStructureService(ResourceStructureMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public List<ResourceStructure> listByScene(Long sceneId) {
        if (sceneId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<ResourceStructure>lambdaQuery()
                        .eq(ResourceStructure::getSceneId, sceneId)
                        .orderByDesc(ResourceStructure::getId)
        );
    }
}



