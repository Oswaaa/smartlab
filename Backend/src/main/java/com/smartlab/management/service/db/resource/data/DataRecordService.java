package com.smartlab.management.service.db.resource.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.dto.PageResult;
import com.smartlab.management.entity.DataIndex;
import com.smartlab.management.entity.DataTemplateDetail;
import com.smartlab.management.mapper.DataIndexMapper;
import com.smartlab.management.mapper.DataTemplateDetailMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动态数据记录读写服务。
 *
 * DATA_INDEX.DATA_TABLE 指向真实物理数据表，本服务负责按数据集读写记录。
 */
@Service
public class DataRecordService {

    private static final Pattern SAFE_TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final DataIndexMapper dataIndexMapper;
    private final DataTemplateDetailMapper detailMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入数据索引、模板字段和 JDBC 访问能力。
     */
    public DataRecordService(DataIndexMapper dataIndexMapper,
                             DataTemplateDetailMapper detailMapper,
                             JdbcTemplate jdbcTemplate) {
        this.dataIndexMapper = dataIndexMapper;
        this.detailMapper = detailMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有数据集索引。
     */
    public List<DataIndex> listDataSets() {
        return dataIndexMapper.selectList(Wrappers.<DataIndex>lambdaQuery().orderByDesc(DataIndex::getId));
    }

    /**
     * 按模板 ID 查询最新一个匹配数据集的记录页。
     */
    public PageResult<Map<String, Object>> pageByTemplateId(Long templateId, long pageNo, long pageSize) {
        DataIndex index = dataIndexMapper.selectOne(
                Wrappers.<DataIndex>lambdaQuery()
                        .eq(DataIndex::getDataTemplateId, templateId)
                        .orderByDesc(DataIndex::getId)
                        .last("limit 1")
        );
        if (index == null) {
            return new PageResult<>(0, pageNo, pageSize, List.of());
        }
        return pageByDataIndex(index, pageNo, pageSize);
    }

    /**
     * 按 DATA_INDEX.ID 查询指定数据集的记录页。
     */
    public PageResult<Map<String, Object>> pageByDataIndexId(Long dataIndexId, long pageNo, long pageSize) {
        DataIndex index = dataIndexMapper.selectById(dataIndexId);
        if (index == null) {
            return new PageResult<>(0, pageNo, pageSize, List.of());
        }
        return pageByDataIndex(index, pageNo, pageSize);
    }

    /**
     * 向指定数据集追加一行记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public int appendRecord(Long dataIndexId, Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            return 0;
        }
        return appendRecords(dataIndexId, List.of(record));
    }

    /**
     * 向指定数据集批量追加记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public int appendRecords(Long dataIndexId, List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        DataIndex index = requireDataIndex(dataIndexId);
        String table = quoteIdentifier(index.getDataTable());
        Map<String, DataTemplateDetail> allowedColumns = loadAllowedColumns(index.getDataTemplateId());
        int inserted = 0;
        for (Map<String, Object> record : records) {
            Map<String, Object> row = normalizeRecord(record, allowedColumns);
            if (row.isEmpty()) {
                continue;
            }
            insertRow(table, row);
            inserted++;
        }
        return inserted;
    }

    /**
     * 根据数据集索引分页读取真实物理表。
     */
    private PageResult<Map<String, Object>> pageByDataIndex(DataIndex index, long pageNo, long pageSize) {
        String table = quoteIdentifier(index.getDataTable());
        long current = Math.max(1, pageNo);
        long size = Math.max(1, pageSize);
        long offset = (current - 1) * size;
        Long total = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "select * from " + table + " order by create_time desc limit ? offset ?",
                size,
                offset
        );
        return new PageResult<>(total == null ? 0 : total, current, size, records);
    }

    /**
     * 查询并校验数据集索引。
     */
    private DataIndex requireDataIndex(Long dataIndexId) {
        if (dataIndexId == null) {
            throw new IllegalArgumentException("数据集ID不能为空");
        }
        DataIndex index = dataIndexMapper.selectById(dataIndexId);
        if (index == null) {
            throw new IllegalArgumentException("数据集不存在: " + dataIndexId);
        }
        return index;
    }

    /**
     * 加载模板允许写入的字段集合。
     */
    private Map<String, DataTemplateDetail> loadAllowedColumns(Long templateId) {
        List<DataTemplateDetail> details = detailMapper.selectList(
                Wrappers.<DataTemplateDetail>lambdaQuery()
                        .eq(DataTemplateDetail::getDataTemplateId, templateId)
                        .orderByAsc(DataTemplateDetail::getId)
        );
        return details.stream().collect(Collectors.toMap(
                detail -> normalizeIdentifier(detail.getColumnName(), "数据字段名"),
                detail -> detail,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    /**
     * 过滤并规范化待写入记录，只保留模板允许的字段。
     */
    private Map<String, Object> normalizeRecord(Map<String, Object> record, Map<String, DataTemplateDetail> allowedColumns) {
        if (record == null || record.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        Set<String> rejectedColumns = new HashSet<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String column = normalizeIdentifier(entry.getKey(), "数据字段名");
            if (Set.of("id", "data_index_id", "create_time").contains(column)) {
                rejectedColumns.add(entry.getKey());
                continue;
            }
            if (!allowedColumns.containsKey(column)) {
                rejectedColumns.add(entry.getKey());
                continue;
            }
            row.put(column, entry.getValue());
        }
        if (!rejectedColumns.isEmpty()) {
            throw new IllegalArgumentException("记录包含模板未定义字段: " + rejectedColumns);
        }
        return row;
    }

    /**
     * 执行单行动态插入。
     */
    private void insertRow(String table, Map<String, Object> row) {
        List<String> columns = new ArrayList<>(row.keySet());
        String columnSql = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String valuesSql = columns.stream().map(column -> "?").collect(Collectors.joining(", "));
        String sql = "insert into " + table + " (" + columnSql + ") values (" + valuesSql + ")";
        Object[] values = columns.stream().map(row::get).toArray();
        jdbcTemplate.update(sql, values);
    }

    /**
     * 校验并规范化数据库标识符。
     */
    private String normalizeIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim().toLowerCase();
        if (!SAFE_TABLE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + "只能包含字母、数字和下划线，且不能以数字开头: " + value);
        }
        return normalized;
    }

    /**
     * 生成安全的 PostgreSQL 标识符引用。
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + normalizeIdentifier(identifier, "数据库标识符") + "\"";
    }
}



