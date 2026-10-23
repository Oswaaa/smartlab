package com.smartlab.management.mapper.resource.adapter;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlab.management.entity.resource.adapter.AdapterIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.OffsetDateTime;
import java.util.List;

@Mapper
/**
 * AdapterIndex持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface AdapterIndexMapper extends BaseMapper<AdapterIndex> {
    @Update("""
            UPDATE "ADAPTER_INDEX"
            SET last_heartbeat = #{at}, update_time = #{at},
                status = CASE WHEN status IS NULL OR BTRIM(status) = '' THEN 'ENABLED' ELSE status END
            WHERE id = #{id}
            """)
    int updateHeartbeat(@Param("id") Long id, @Param("at") OffsetDateTime at);

    @Select("""
            SELECT adapter_name FROM "ADAPTER_INDEX"
            WHERE last_heartbeat IS NOT NULL AND last_heartbeat < #{threshold}
            """)
    List<String> selectExpiredAdapterNames(@Param("threshold") OffsetDateTime threshold);
}
