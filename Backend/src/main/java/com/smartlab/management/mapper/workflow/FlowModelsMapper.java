package com.smartlab.management.mapper.workflow;

import com.smartlab.management.entity.workflow.FlowModels;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
/**
 * FlowModels持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface FlowModelsMapper extends BaseMapper<FlowModels> {
    @Select("SELECT id, flow_name, version, predecessor_id, status FROM \"FLOW_MODELS\" WHERE predecessor_id = #{predecessorId} ORDER BY id ASC LIMIT 1")
    FlowModels selectByPredecessorId(Long predecessorId);
}

