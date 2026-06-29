package com.smartlab.management.service.db.constraint;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.constraint.ViolationLog;
import com.smartlab.management.mapper.constraint.ViolationLogMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 约束违规日志表服务。
 * 对应 VIOLATION_LOG 表，只负责日志查询和人工维护，不执行约束判断。
 */
@Service
/**
 * ViolationLog业务持久层核心操作服务。
 */
public class ViolationLogService extends ManagementCrudService<ViolationLog> {

    private final ViolationLogMapper mapper;

    public ViolationLogService(ViolationLogMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public PageResult<ViolationLog> page(long pageNo,
                                         long pageSize,
                                         String keyword,
                                         Long constraintRuleId,
                                         Long taskId,
                                         Long deviceInstanceId) {
        QueryWrapper<ViolationLog> query = new QueryWrapper<>();
        if (constraintRuleId != null) {
            query.eq("constraint_rule_id", constraintRuleId);
        }
        if (taskId != null) {
            query.eq("task_id", taskId);
        }
        if (deviceInstanceId != null) {
            query.eq("device_instance_id", deviceInstanceId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("observed_variable", value)
                    .or()
                    .like("action_taken", value)
                    .or()
                    .like("constraint_type", value));
        }
        query.orderByDesc("violation_time", "id");
        Page<ViolationLog> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    public ViolationLog save(ViolationLog entity) {
        if (entity.getViolationTime() == null) {
            entity.setViolationTime(LocalDateTime.now());
        }
        return super.save(entity);
    }
}
