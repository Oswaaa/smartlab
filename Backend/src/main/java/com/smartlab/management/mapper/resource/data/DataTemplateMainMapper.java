package com.smartlab.management.mapper.resource.data;

import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * DataTemplateMain持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DataTemplateMainMapper extends BaseMapper<DataTemplateMain> {
    default DataTemplateMain selectByIdForUpdate(Long id) {
        return selectOne(Wrappers.<DataTemplateMain>lambdaQuery()
                .eq(DataTemplateMain::getId, id)
                .last("FOR UPDATE"));
    }
}

