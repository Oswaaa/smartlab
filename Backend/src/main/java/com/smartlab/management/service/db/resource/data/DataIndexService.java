package com.smartlab.management.service.db.resource.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.entity.resource.device.PropertyType;
import com.smartlab.management.entity.resource.device.DeviceInstances;
import com.smartlab.management.entity.resource.device.DeviceInstanceLifecycle;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateMainMapper;
import com.smartlab.management.mapper.resource.device.PropertyTypeMapper;
import com.smartlab.management.mapper.resource.device.DeviceInstancesMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 数据索引表服务。
 *
 * 对应 DATA_INDEX 表，负责数据集索引和物理数据表的创建生命周期。
 */
@Service
/**
 * DataIndex业务持久层核心操作服务。
 */
public class DataIndexService extends ManagementCrudService<DataIndex> {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int DEFAULT_VARCHAR_LENGTH = 255;

    private final DataIndexMapper mapper;
    private final DataTemplateMainMapper templateMainMapper;
    private final DataTemplateDetailMapper templateDetailMapper;
    private final PropertyTypeMapper propertyTypeMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DeviceInstancesMapper deviceInstancesMapper;

    public DataIndexService(DataIndexMapper mapper,
                            DataTemplateMainMapper templateMainMapper,
                            DataTemplateDetailMapper templateDetailMapper,
                            PropertyTypeMapper propertyTypeMapper,
                            JdbcTemplate jdbcTemplate,
                            DeviceInstancesMapper deviceInstancesMapper) {
        super(mapper);
        this.mapper = mapper;
        this.templateMainMapper = templateMainMapper;
        this.templateDetailMapper = templateDetailMapper;
        this.propertyTypeMapper = propertyTypeMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.deviceInstancesMapper = deviceInstancesMapper;
    }

    public List<DataIndex> listByDeviceInstance(Long deviceInstanceId) {
        if (deviceInstanceId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<DataIndex>lambdaQuery()
                        .eq(DataIndex::getDeviceInstanceId, deviceInstanceId)
                        .orderByDesc(DataIndex::getId)
        );
    }

    public List<DataIndex> listByTemplate(Long templateId) {

        if (templateId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<DataIndex>lambdaQuery()
                        .eq(DataIndex::getDataTemplateId, templateId)
                        .orderByDesc(DataIndex::getId)
        );
    }

    public long countByDeviceInstance(Long deviceInstanceId) {
        if (deviceInstanceId == null) {
            return 0;
        }
        Long count = mapper.selectCount(
                Wrappers.<DataIndex>lambdaQuery().eq(DataIndex::getDeviceInstanceId, deviceInstanceId)
        );
        return count == null ? 0 : count;
    }

    public long countByTemplate(Long templateId) {

        if (templateId == null) {
            return 0;
        }
        Long count = mapper.selectCount(
                Wrappers.<DataIndex>lambdaQuery().eq(DataIndex::getDataTemplateId, templateId)
        );
        return count == null ? 0 : count;
    }

    /**
     * 创建一个数据集：写 DATA_INDEX，并按模板字段创建对应的 DATA_RECORD_xxxx 物理表。
     */
    @Transactional(rollbackFor = Exception.class)
    public DataIndex createDataSet(Long templateId, Long deviceInstanceId, String dataDesc) {
        if (deviceInstanceId != null) {
            DeviceInstances instance = deviceInstancesMapper.selectById(deviceInstanceId);
            if (instance == null) throw new IllegalArgumentException("设备实例不存在: " + deviceInstanceId);
            if (!DeviceInstanceLifecycle.isUsable(instance))
                throw new IllegalStateException("设备实例已注销，不能创建新数据集");
        }
        if (templateId == null) {
            throw new IllegalArgumentException("数据模板ID不能为空");
        }
        DataTemplateMain template = templateMainMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("数据模板不存在");
        }
        List<DataTemplateDetail> details = templateDetailMapper.selectList(
                Wrappers.<DataTemplateDetail>lambdaQuery()
                        .eq(DataTemplateDetail::getDataTemplateId, templateId)
                        .orderByAsc(DataTemplateDetail::getId)
        );
        if (details.isEmpty()) {
            throw new IllegalArgumentException("数据模板没有字段明细，无法创建物理数据表");
        }

        DataIndex index = new DataIndex();
        index.setDataTemplateId(templateId);
        index.setDeviceInstanceId(deviceInstanceId);
        index.setDataTable(generateDataTableName());
        index.setDataDesc(resolveDataDesc(dataDesc, template));
        index.setCreateTime(OffsetDateTime.now());
        mapper.insert(index);

        createPhysicalRecordTable(index, details);
        return index;
    }

    /**
     * 设备实例创建后，根据设备模型的默认模板自动创建该设备实例的数据集。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<DataIndex> createDefaultDataSetsForDeviceInstance(Long deviceModelId, Long deviceInstanceId, String instanceName) {
        if (deviceModelId == null || deviceInstanceId == null) {
            return List.of();
        }
        List<DataTemplateMain> defaultTemplates = templateMainMapper.selectList(
                Wrappers.<DataTemplateMain>lambdaQuery()
                        .eq(DataTemplateMain::getDeviceModelId, deviceModelId)
                        .eq(DataTemplateMain::getIsDefault, true)
                        .orderByAsc(DataTemplateMain::getId)
        );
        return defaultTemplates.stream()
                .filter(template -> !hasDataSet(deviceInstanceId, template.getId()))
                .map(template -> createDataSet(
                        template.getId(),
                        deviceInstanceId,
                        defaultDataDesc(instanceName, template.getTemplateName())
                ))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataIndex save(DataIndex entity) {
        if (entity.getId() == null) {
            return createDataSet(entity.getDataTemplateId(), entity.getDeviceInstanceId(), entity.getDataDesc());
        }
        return super.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(java.io.Serializable id) {
        if (id == null || String.valueOf(id).isBlank()) {
            throw new IllegalArgumentException("数据集ID不能为空");
        }
        Long dataIndexId = Long.valueOf(String.valueOf(id));
        DataIndex index = mapper.selectById(dataIndexId);
        if (index == null) {
            throw new IllegalArgumentException("数据集不存在");
        }
        if (index.getDataTable() == null || index.getDataTable().isBlank()) {
            throw new IllegalStateException("数据集缺少物理表名，无法删除");
        }
        jdbcTemplate.execute("drop table if exists " + quoteIdentifier(index.getDataTable()));
        mapper.deleteById(dataIndexId);
    }

    private boolean hasDataSet(Long deviceInstanceId, Long templateId) {
        Long count = mapper.selectCount(
                Wrappers.<DataIndex>lambdaQuery()
                        .eq(DataIndex::getDeviceInstanceId, deviceInstanceId)
                        .eq(DataIndex::getDataTemplateId, templateId)
        );
        return count != null && count > 0;
    }

    private void createPhysicalRecordTable(DataIndex index, List<DataTemplateDetail> details) {
        Map<Long, PropertyType> typeMap = loadPropertyTypes();
        Set<String> usedColumns = new HashSet<>(Set.of("id", "data_index_id", "create_time"));
        StringBuilder sql = new StringBuilder();
        sql.append("create table ").append(quoteIdentifier(index.getDataTable())).append(" (")
                .append(quoteIdentifier("id")).append(" bigserial primary key, ")
                .append(quoteIdentifier("data_index_id")).append(" integer not null default ").append(index.getId()).append(", ");

        for (DataTemplateDetail detail : details) {
            String columnName = normalizeIdentifier(detail.getColumnName(), "数据字段名");
            if (!usedColumns.add(columnName)) {
                    throw new IllegalArgumentException("数据模板字段名重复或占用系统字段: " + detail.getColumnName());
            }
            String sqlType = resolveSqlType(detail, typeMap);
            sql.append(quoteIdentifier(columnName))
                    .append(" ")
                    .append(sqlType)
                    .append(defaultClause(detail, sqlType))
                    .append(", ");
        }

        sql.append(quoteIdentifier("create_time")).append(" timestamptz not null default now())");
        jdbcTemplate.execute(sql.toString());
        jdbcTemplate.execute("create index " + quoteIdentifier(index.getDataTable() + "_idx_time")
                + " on " + quoteIdentifier(index.getDataTable()) + " (" + quoteIdentifier("create_time") + ")");
        jdbcTemplate.execute("create index " + quoteIdentifier(index.getDataTable() + "_idx_data_index")
                + " on " + quoteIdentifier(index.getDataTable()) + " (" + quoteIdentifier("data_index_id") + ")");
    }

    private Map<Long, PropertyType> loadPropertyTypes() {
        Map<Long, PropertyType> result = new HashMap<>();
        for (PropertyType type : propertyTypeMapper.selectList(Wrappers.<PropertyType>lambdaQuery())) {
            if (type.getId() != null) {
                result.put(type.getId(), type);
            }
        }
        return result;
    }

    private String resolveSqlType(DataTemplateDetail detail, Map<Long, PropertyType> typeMap) {
        PropertyType propertyType = typeMap.get(detail.getPropertyTypeId());
        String dbType = propertyType == null ? null : propertyType.getDbType();
        if (dbType == null || dbType.isBlank()) {
            return "text";
        }
        String normalized = dbType.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return switch (normalized) {
            case "int", "int4", "integer" -> "integer";
            case "bigint", "int8", "long" -> "bigint";
            case "numeric", "decimal" -> "numeric";
            case "real", "float4" -> "real";
            case "double", "float8", "double precision" -> "double precision";
            case "bool", "boolean" -> "boolean";
            case "date" -> "date";
            case "time" -> "time";
            case "datetime", "timestamp", "timestamp without time zone" -> "timestamp";
            case "timestamptz", "timestamp with time zone" -> "timestamptz";
            case "json", "jsonb" -> "jsonb";
            case "text" -> "text";
            case "string", "varchar", "character varying", "char", "character" ->
                    "varchar(" + varcharLength(detail.getColumnLength()) + ")";
            default -> throw new IllegalArgumentException("不支持的数据字段类型: " + dbType);
        };
    }

    private int varcharLength(Integer length) {
        if (length == null || length <= 0) {
            return DEFAULT_VARCHAR_LENGTH;
        }
        return Math.min(length, 4096);
    }

    private String defaultClause(DataTemplateDetail detail, String sqlType) {
        String value = detail.getDefaultValue();
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (isNumericType(sqlType)) {
            if (!trimmed.matches("[-+]?\\d+(\\.\\d+)?")) {
                throw new IllegalArgumentException("字段默认值不是合法数字: " + detail.getColumnName());
            }
            return " default " + trimmed;
        }
        if ("boolean".equals(sqlType)) {
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (!List.of("true", "false").contains(normalized)) {
                throw new IllegalArgumentException("字段默认值不是合法布尔值: " + detail.getColumnName());
            }
            return " default " + normalized;
        }
        if ("jsonb".equals(sqlType)) {
            return " default " + sqlLiteral(trimmed) + "::jsonb";
        }
        return " default " + sqlLiteral(trimmed);
    }

    private boolean isNumericType(String sqlType) {
        return List.of("integer", "bigint", "numeric", "real", "double precision").contains(sqlType);
    }

    private String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String generateDataTableName() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "data_record_" + System.currentTimeMillis() + "_" + suffix;
    }

    private String resolveDataDesc(String dataDesc, DataTemplateMain template) {
        if (dataDesc != null && !dataDesc.isBlank()) {
            return dataDesc.trim();
        }
        return template.getTemplateName() == null ? "未命名数据集" : template.getTemplateName();
    }

    private String defaultDataDesc(String instanceName, String templateName) {
        String left = instanceName == null || instanceName.isBlank() ? "设备实例" : instanceName.trim();
        String right = templateName == null || templateName.isBlank() ? "默认数据" : templateName.trim();
        return left + " - " + right;
    }

    private String normalizeIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + "只能包含字母、数字和下划线，且不能以数字开头: " + value);
        }
        return normalized;
    }

    private String quoteIdentifier(String identifier) {
        String normalized = normalizeIdentifier(identifier, "数据库标识符");
        return "\"" + normalized + "\"";
    }
}



