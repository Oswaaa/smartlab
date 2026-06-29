package com.smartlab.management.service.db.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlab.management.dto.common.PageResult;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理模块通用 CRUD 基类。
 * 该类只封装数据库表的基础增删改查，不承载设备控制、工作流执行等业务引擎逻辑。
 */
public abstract class ManagementCrudService<T> {

    private final BaseMapper<T> mapper;

    protected ManagementCrudService(BaseMapper<T> mapper) {
        this.mapper = mapper;
    }

    protected BaseMapper<T> mapper() {
        return mapper;
    }

    public List<T> list() {
        return mapper.selectList(new QueryWrapper<T>().orderByDesc("id"));
    }

    public PageResult<T> page(long pageNo, long pageSize, String keyword, String... keywordColumns) {
        QueryWrapper<T> query = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank() && keywordColumns != null && keywordColumns.length > 0) {
            query.and(wrapper -> {
                for (String column : keywordColumns) {
                    wrapper.or().like(column, keyword.trim());
                }
            });
        }
        query.orderByDesc("id");
        Page<T> page = mapper.selectPage(new Page<>(Math.max(1, pageNo), Math.max(1, pageSize)), query);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    public T getById(Serializable id) {
        return mapper.selectById(id);
    }

    public long countAll() {
        Long count = mapper.selectCount(new QueryWrapper<>());
        return count == null ? 0 : count;
    }

    public T save(T entity) {
        Serializable id = readId(entity);
        LocalDateTime now = LocalDateTime.now();
        if (id == null) {
            invokeSetter(entity, "setCreateTime", now);
            invokeSetter(entity, "setUpdateTime", now);
            mapper.insert(entity);
        } else {
            invokeSetter(entity, "setUpdateTime", now);
            mapper.updateById(entity);
        }
        return entity;
    }

    public void delete(Serializable id) {
        mapper.deleteById(id);
    }

    protected Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }

    private Serializable readId(T entity) {
        try {
            Method method = entity.getClass().getMethod("getId");
            Object value = method.invoke(entity);
            return value instanceof Serializable serializable ? serializable : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void invokeSetter(T entity, String methodName, LocalDateTime value) {
        try {
            Method method = entity.getClass().getMethod(methodName, LocalDateTime.class);
            method.invoke(entity, value);
        } catch (Exception ignored) {
            // 不是所有表都有 create_time/update_time 字段。
        }
    }

}


