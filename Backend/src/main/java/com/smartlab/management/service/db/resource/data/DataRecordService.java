package com.smartlab.management.service.db.resource.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.dto.common.PageResult;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动态数据记录读写服务。
 *
 * DATA_INDEX.DATA_TABLE 指向真实物理数据表，本服务负责按数据集读写记录。
 */
@Service
/**
 * DataRecord业务持久层核心操作服务。
 */
public class DataRecordService {

    private static final Pattern SAFE_TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Object MISSING_VALUE = new Object();

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
        List<DataTemplateDetail> templateFields = loadTemplateFields(index.getDataTemplateId());
        int inserted = 0;
        for (Map<String, Object> record : records) {
            Map<String, Object> row = normalizeRecord(record, templateFields);
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
     * 加载模板字段定义。字段定义里的 column_name 是物理表列，device_attr_key 是设备模型属性标识。
     */
    private List<DataTemplateDetail> loadTemplateFields(Long templateId) {
        return detailMapper.selectList(
                Wrappers.<DataTemplateDetail>lambdaQuery()
                        .eq(DataTemplateDetail::getDataTemplateId, templateId)
                        .orderByAsc(DataTemplateDetail::getId)
        );
    }

    /**
     * 按 device_attr_key 从记录里取值，再写入对应的物理列 column_name。
     */
    private Map<String, Object> normalizeRecord(Map<String, Object> record, List<DataTemplateDetail> templateFields) {
        if (record == null || record.isEmpty() || templateFields == null || templateFields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (DataTemplateDetail detail : templateFields) {
            String column = normalizeIdentifier(detail.getColumnName(), "数据字段名");
            String sourceKey = firstNonBlank(detail.getDeviceAttrKey(), detail.getColumnName());
            Object value = valueBySourceKey(record, sourceKey);
            if (value != MISSING_VALUE) {
                row.put(column, value);
            }
        }
        return row;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private Object valueBySourceKey(Map<String, Object> record, String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return MISSING_VALUE;
        }
        if (record.containsKey(sourceKey)) {
            return record.get(sourceKey);
        }
        String trimmed = sourceKey.trim();
        if (!trimmed.equals(sourceKey) && record.containsKey(trimmed)) {
            return record.get(trimmed);
        }
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.trim().equals(trimmed)) {
                return entry.getValue();
            }
        }
        return MISSING_VALUE;
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

    /**
     * 导出数据集记录为 CSV。
     */
    public void exportCsv(Long dataIndexId, jakarta.servlet.http.HttpServletResponse response) throws Exception {
        DataIndex index = requireDataIndex(dataIndexId);
        String table = quoteIdentifier(index.getDataTable());
        
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + index.getDataTable() + ".csv\"");
        
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(response.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            writer.write('\ufeff'); // BOM for Excel
            List<DataTemplateDetail> templateFields = loadTemplateFields(index.getDataTemplateId());
            List<String> columns = new ArrayList<>();
            columns.add("create_time");
            columns.addAll(templateFields.stream()
                    .map(detail -> normalizeIdentifier(detail.getColumnName(), "数据字段名"))
                    .toList());
            
            writer.println(String.join(",", columns));
            
            jdbcTemplate.query("select * from " + table + " order by create_time desc", rs -> {
                List<String> row = new ArrayList<>();
                for (String col : columns) {
                    Object val = rs.getObject(col);
                    String valStr = val == null ? "" : val.toString().replace("\"", "\"\"");
                    row.add("\"" + valStr + "\"");
                }
                writer.println(String.join(",", row));
            });
        }
    }
}



