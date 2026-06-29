package com.smartlab.management.dto.common;

import lombok.Data;

import java.util.List;

@Data
/**
 * PageResult 领域实体/配置模型类。
 */
public class PageResult<T> {

    private long total;
    private long pageNo;
    private long pageSize;
    private List<T> records;

    public PageResult(long total, long pageNo, long pageSize, List<T> records) {
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.records = records;
    }
}

