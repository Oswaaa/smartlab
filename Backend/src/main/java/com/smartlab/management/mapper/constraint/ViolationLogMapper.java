package com.smartlab.management.mapper.constraint;

import com.smartlab.management.entity.constraint.ViolationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * ViolationLog持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface ViolationLogMapper extends BaseMapper<ViolationLog> {
}

