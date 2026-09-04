package com.smartlab.management.mapper.resource.adapter;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlab.management.entity.resource.adapter.VirtualLease;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * VIRTUAL_LEASE 持久层映射。
 */
public interface VirtualLeaseMapper extends BaseMapper<VirtualLease> {
}
