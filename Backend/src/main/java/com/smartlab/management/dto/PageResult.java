package com.smartlab.management.dto;

import lombok.Data;

import java.util.List;

@Data
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

