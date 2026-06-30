package com.smartlab.management.service.db.resource.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.device.DeviceCategory;
import com.smartlab.management.mapper.resource.device.DeviceCategoryMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 设备类别表服务。
 *
 * 对应 DEVICE_CATEGORY 表，用于维护设备类别树。
 */
@Service
/**
 * DeviceCategory业务持久层核心操作服务。
 */
public class DeviceCategoryService extends ManagementCrudService<DeviceCategory> {

    private final DeviceCategoryMapper mapper;

    public DeviceCategoryService(DeviceCategoryMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public DeviceCategory findOrCreateByName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        DeviceCategory existing = mapper.selectOne(
                Wrappers.<DeviceCategory>lambdaQuery().eq(DeviceCategory::getCategoryName, categoryName.trim())
        );
        if (existing != null) {
            return existing;
        }
        DeviceCategory category = new DeviceCategory();
        category.setCategoryName(categoryName.trim());
        category.setCreateTime(OffsetDateTime.now());
        mapper.insert(category);
        return category;
    }
}



