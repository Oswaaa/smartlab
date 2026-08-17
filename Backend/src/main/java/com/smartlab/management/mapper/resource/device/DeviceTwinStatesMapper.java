package com.smartlab.management.mapper.resource.device;

import com.smartlab.management.entity.resource.device.DeviceTwinStates;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

@Mapper
/**
 * DeviceTwinStates持久层数据库映射访问接口（MyBatis-Plus）。
 */
public interface DeviceTwinStatesMapper extends BaseMapper<DeviceTwinStates> {

    @Update("""
            UPDATE "DEVICE_TWIN_STATES"
            SET current_cmd_state = CASE WHEN #{updateCommand} THEN #{commandState} ELSE current_cmd_state END,
                current_op_state = COALESCE(current_op_state, '{}'::jsonb) || CAST(#{operationPatchJson} AS jsonb),
                update_time = #{updateTime}
            WHERE instance_id = #{instanceId}
            """)
    int patchRuntimeState(@Param("instanceId") Long instanceId,
                          @Param("updateCommand") boolean updateCommand,
                          @Param("commandState") String commandState,
                          @Param("operationPatchJson") String operationPatchJson,
                          @Param("updateTime") OffsetDateTime updateTime);

    @Update("""
            UPDATE "DEVICE_TWIN_STATES"
            SET current_attr = COALESCE(current_attr, '{}'::jsonb) || CAST(#{attributePatchJson} AS jsonb),
                online_status = 'ONLINE',
                last_online_time = #{observedAt},
                update_time = #{updateTime}
            WHERE instance_id = #{instanceId}
            """)
    int patchAttributes(@Param("instanceId") Long instanceId,
                        @Param("attributePatchJson") String attributePatchJson,
                        @Param("observedAt") OffsetDateTime observedAt,
                        @Param("updateTime") OffsetDateTime updateTime);

    @Update("""
            UPDATE "DEVICE_TWIN_STATES"
            SET current_op_state = jsonb_set(
                    COALESCE(current_op_state, '{}'::jsonb),
                    ARRAY[#{regionName}]::text[],
                    COALESCE(current_op_state -> #{regionName}, '[]'::jsonb)
                        || jsonb_build_array(CAST(#{stateName} AS text)),
                    true),
                update_time = #{updateTime}
            WHERE instance_id = #{instanceId}
              AND NOT (COALESCE(current_op_state -> #{regionName}, '[]'::jsonb)
                  @> jsonb_build_array(CAST(#{stateName} AS text)))
            """)
    int addExceptionState(@Param("instanceId") Long instanceId,
                          @Param("regionName") String regionName,
                          @Param("stateName") String stateName,
                          @Param("updateTime") OffsetDateTime updateTime);

    @Update("""
            UPDATE "DEVICE_TWIN_STATES"
            SET current_op_state = jsonb_set(
                    COALESCE(current_op_state, '{}'::jsonb),
                    ARRAY[#{regionName}]::text[],
                    COALESCE(current_op_state -> #{regionName}, '[]'::jsonb) - CAST(#{stateName} AS text),
                    true),
                update_time = #{updateTime}
            WHERE instance_id = #{instanceId}
              AND COALESCE(current_op_state -> #{regionName}, '[]'::jsonb)
                  @> jsonb_build_array(CAST(#{stateName} AS text))
            """)
    int removeExceptionState(@Param("instanceId") Long instanceId,
                             @Param("regionName") String regionName,
                             @Param("stateName") String stateName,
                             @Param("updateTime") OffsetDateTime updateTime);

    @Update("""
            UPDATE "DEVICE_TWIN_STATES"
            SET online_status = #{onlineStatus},
                last_online_time = CASE WHEN #{onlineStatus} = 'ONLINE' THEN #{now} ELSE last_online_time END,
                update_time = #{now}
            WHERE instance_id IN (
                SELECT id FROM "DEVICE_INSTANCES"
                WHERE bound_adapter_name = #{adapterName}
                  AND lifecycle_status = 'IN_USE'
            )
            """)
    int updateOnlineStatusByAdapter(@Param("adapterName") String adapterName,
                                   @Param("onlineStatus") String onlineStatus,
                                   @Param("now") OffsetDateTime now);
}

