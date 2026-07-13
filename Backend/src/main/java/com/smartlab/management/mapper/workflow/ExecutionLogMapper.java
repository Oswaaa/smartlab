package com.smartlab.management.mapper.workflow;

import com.smartlab.management.entity.workflow.ExecutionLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * ExecutionLog持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface ExecutionLogMapper extends BaseMapper<ExecutionLog> {
}

