package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.DeviceModels;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DeviceModels持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DeviceModelsMapper extends BaseMapper<DeviceModels> {
    default DeviceModels selectByIdForUpdate(Long id) {
        return selectOne(Wrappers.<DeviceModels>lambdaQuery()
                .eq(DeviceModels::getId, id)
                .last("FOR UPDATE"));
    }
}

