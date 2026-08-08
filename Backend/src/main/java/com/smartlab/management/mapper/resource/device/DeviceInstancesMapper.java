package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
/**
 * DeviceInstances持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DeviceInstancesMapper extends BaseMapper<DeviceInstances> {
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{bindingKey}, 0))")
    Object lockAdapterBinding(@Param("bindingKey") String bindingKey);

    default DeviceInstances selectByIdForUpdate(Long id) {
        return selectOne(Wrappers.<DeviceInstances>lambdaQuery()
                .eq(DeviceInstances::getId, id)
                .last("FOR UPDATE"));
    }
}
