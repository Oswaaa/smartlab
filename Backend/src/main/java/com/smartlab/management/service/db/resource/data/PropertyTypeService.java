package com.smartlab.management.service.db.resource.data;

import com.smartlab.management.entity.resource.device.PropertyType;
import com.smartlab.management.mapper.resource.device.PropertyTypeMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

/**
 * 字段类型表服务。
 *
 * 对应 PROPERTY_TYPE 表，用于数据模板字段的类型选择。
 */
@Service
/**
 * PropertyType业务持久层核心操作服务。
 */
public class PropertyTypeService extends ManagementCrudService<PropertyType> {

    public PropertyTypeService(PropertyTypeMapper mapper) {
        super(mapper);
    }
}



