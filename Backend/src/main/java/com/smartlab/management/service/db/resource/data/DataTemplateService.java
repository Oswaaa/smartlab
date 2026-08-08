package com.smartlab.management.service.db.resource.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.entity.resource.data.DataIndex;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.entity.resource.device.DeviceModels;
import com.smartlab.management.entity.resource.device.PropertyType;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateMainMapper;
import com.smartlab.management.mapper.resource.device.DeviceModelsMapper;
import com.smartlab.management.mapper.resource.device.PropertyTypeMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 模板从属于设备模型；一旦生成数据集，模板及字段结构即冻结。 */
@Service
public class DataTemplateService extends ManagementCrudService<DataTemplateMain> {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SYSTEM_COLUMNS = Set.of("id", "data_index_id", "create_time", "ingest_time");

    private final DataTemplateMainMapper mainMapper;
    private final DataTemplateDetailMapper detailMapper;
    private final DataIndexMapper dataIndexMapper;
    private final DeviceModelsMapper deviceModelsMapper;
    private final PropertyTypeMapper propertyTypeMapper;

    public DataTemplateService(DataTemplateMainMapper mainMapper,
                               DataTemplateDetailMapper detailMapper,
                               DataIndexMapper dataIndexMapper,
                               DeviceModelsMapper deviceModelsMapper,
                               PropertyTypeMapper propertyTypeMapper) {
        super(mainMapper);
        this.mainMapper = mainMapper;
        this.detailMapper = detailMapper;
        this.dataIndexMapper = dataIndexMapper;
        this.deviceModelsMapper = deviceModelsMapper;
        this.propertyTypeMapper = propertyTypeMapper;
    }

    @Override
    public List<DataTemplateMain> list() {
        return mainMapper.selectList(Wrappers.<DataTemplateMain>lambdaQuery().orderByDesc(DataTemplateMain::getId));
    }

    public List<DataTemplateDetail> listDetails(Long templateId) {
        return detailMapper.selectList(Wrappers.<DataTemplateDetail>lambdaQuery()
                .eq(DataTemplateDetail::getDataTemplateId, templateId)
                .orderByAsc(DataTemplateDetail::getId));
    }

    public DataTemplateMain findDefaultTemplateByModelId(Long deviceModelId) {
        if (deviceModelId == null) return null;
        return mainMapper.selectOne(Wrappers.<DataTemplateMain>lambdaQuery()
                .eq(DataTemplateMain::getDeviceModelId, deviceModelId)
                .eq(DataTemplateMain::getIsDefault, true));
    }

    /** 仅供设备模型生命周期保存默认模板。 */
    @Transactional(rollbackFor = Exception.class)
    public DataTemplateMain saveTemplate(DataTemplateSaveDTO dto) {
        return persistTemplate(dto, true);
    }

    /** 数据中心只能保存自定义模板。 */
    @Transactional(rollbackFor = Exception.class)
    public DataTemplateMain saveCustomTemplate(DataTemplateSaveDTO dto) {
        return persistTemplate(dto, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public DataTemplateMain savePayload(Map<String, Object> payload) {
        if (payload == null) throw new IllegalArgumentException("模板保存请求不能为空");
        DataTemplateMain main = new DataTemplateMain();
        Object id = first(payload, "id", "templateId");
        if (id != null && !String.valueOf(id).isBlank()) main.setId(Long.valueOf(String.valueOf(id)));
        main.setTemplateName(stringValue(first(payload, "templateName", "name")));
        main.setTemplateDesc(stringValue(first(payload, "templateDesc", "description")));
        Object modelId = first(payload, "deviceModelId", "modelId");
        if (modelId != null && !String.valueOf(modelId).isBlank()) {
            main.setDeviceModelId(Long.valueOf(String.valueOf(modelId)));
        }
        main.setIsDefault(false);

        List<DataTemplateDetail> details = new ArrayList<>();
        Object detailPayload = first(payload, "details", "columns");
        if (detailPayload instanceof List<?> rows) {
            for (Object item : rows) if (item instanceof Map<?, ?> row) details.add(toDetail(row));
        } else if (payload.get("dataSchemaSpec") instanceof Map<?, ?> schemaSpec) {
            details.addAll(schemaSpecDetails(schemaSpec));
        }
        DataTemplateSaveDTO dto = new DataTemplateSaveDTO();
        dto.setMain(main);
        dto.setDetails(details);
        return saveCustomTemplate(dto);
    }

    private DataTemplateMain persistTemplate(DataTemplateSaveDTO dto, boolean allowDefault) {
        if (dto == null || dto.getMain() == null) throw new IllegalArgumentException("模板主表不能为空");
        DataTemplateMain main = dto.getMain();
        DataTemplateMain existing = null;
        if (main.getId() != null) {
            existing = requireLockedTemplate(main.getId());
            assertTemplateMutable(existing.getId());
            if (!allowDefault && Boolean.TRUE.equals(existing.getIsDefault())) {
                throw new IllegalStateException("默认数据模板只能随设备模型修改");
            }
            if (main.getDeviceModelId() == null) main.setDeviceModelId(existing.getDeviceModelId());
            if (!Objects.equals(existing.getDeviceModelId(), main.getDeviceModelId())) {
                throw new IllegalStateException("数据模板创建后不能更换所属设备模型");
            }
            main.setCreateTime(existing.getCreateTime());
            if (allowDefault && main.getIsDefault() == null) main.setIsDefault(existing.getIsDefault());
        }
        if (!allowDefault) main.setIsDefault(false);
        if (main.getIsDefault() == null) main.setIsDefault(false);
        if (main.getTemplateName() == null || main.getTemplateName().isBlank()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        main.setTemplateName(main.getTemplateName().trim());
        DeviceModels model = requireModel(main.getDeviceModelId());
        List<DataTemplateDetail> details = normalizeAndValidateDetails(model, dto.getDetails());

        if (existing == null) {
            main.setCreateTime(OffsetDateTime.now());
            mainMapper.insert(main);
        } else {
            mainMapper.updateById(main);
            detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery()
                    .eq(DataTemplateDetail::getDataTemplateId, main.getId()));
        }
        for (DataTemplateDetail detail : details) {
            detail.setId(null);
            detail.setDataTemplateId(main.getId());
            detail.setCreateTime(OffsetDateTime.now());
            detailMapper.insert(detail);
        }
        return main;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        DataTemplateMain template = requireLockedTemplate(parseId(id));
        if (Boolean.TRUE.equals(template.getIsDefault())) {
            throw new IllegalStateException("默认数据模板由设备模型统一管理，不能单独删除");
        }
        deleteTemplateInternal(template);
    }

    /** 模型删除时在同一事务内删除全部未使用模板。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByModelIdForModelRemoval(Long modelId) {
        List<DataTemplateMain> templates = mainMapper.selectList(Wrappers.<DataTemplateMain>lambdaQuery()
                .eq(DataTemplateMain::getDeviceModelId, modelId).orderByAsc(DataTemplateMain::getId));
        for (DataTemplateMain row : templates) deleteTemplateInternal(requireLockedTemplate(row.getId()));
    }

    private void deleteTemplateInternal(DataTemplateMain template) {
        assertTemplateMutable(template.getId());
        detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery()
                .eq(DataTemplateDetail::getDataTemplateId, template.getId()));
        mainMapper.deleteById(template.getId());
    }

    private DataTemplateMain requireLockedTemplate(Long templateId) {
        if (templateId == null) throw new IllegalArgumentException("数据模板ID不能为空");
        DataTemplateMain template = mainMapper.selectByIdForUpdate(templateId);
        if (template == null) throw new IllegalArgumentException("数据模板不存在: " + templateId);
        return template;
    }

    private void assertTemplateMutable(Long templateId) {
        Long count = dataIndexMapper.selectCount(Wrappers.<DataIndex>lambdaQuery()
                .eq(DataIndex::getDataTemplateId, templateId));
        if (count != null && count > 0) {
            throw new IllegalStateException("该数据模板已生成 " + count + " 个数据集，字段结构已经冻结");
        }
    }

    private DeviceModels requireModel(Long modelId) {
        if (modelId == null) throw new IllegalArgumentException("数据模板必须绑定设备模型");
        DeviceModels model = deviceModelsMapper.selectById(modelId);
        if (model == null) throw new IllegalArgumentException("数据模板引用的设备模型不存在: " + modelId);
        return model;
    }

    private List<DataTemplateDetail> normalizeAndValidateDetails(DeviceModels model, List<DataTemplateDetail> source) {
        if (source == null || source.isEmpty()) throw new IllegalArgumentException("数据模板至少需要一个字段");
        Map<String, String> modelAttributeTypes = modelAttributeTypes(model.getAttributes());
        List<PropertyType> propertyTypes = propertyTypeMapper.selectList(Wrappers.lambdaQuery());
        Map<Long, PropertyType> propertyTypeById = new HashMap<>();
        for (PropertyType type : propertyTypes) if (type.getId() != null) propertyTypeById.put(type.getId(), type);
        Set<String> usedColumns = new HashSet<>();
        List<DataTemplateDetail> details = new ArrayList<>();
        for (DataTemplateDetail sourceDetail : source) {
            if (sourceDetail == null) continue;
            DataTemplateDetail detail = new DataTemplateDetail();
            String column = normalizeIdentifier(sourceDetail.getColumnName());
            if (SYSTEM_COLUMNS.contains(column) || !usedColumns.add(column)) {
                throw new IllegalArgumentException("模板字段重复或占用系统字段: " + column);
            }
            detail.setColumnName(column);
            detail.setColumnDesc(sourceDetail.getColumnDesc() == null || sourceDetail.getColumnDesc().isBlank()
                    ? column : sourceDetail.getColumnDesc().trim());
            String binding = trimToNull(sourceDetail.getDeviceAttrKey());
            detail.setDeviceAttrKey(binding);
            detail.setDefaultValue(trimToNull(sourceDetail.getDefaultValue()));
            if (binding != null) {
                String dataType = modelAttributeTypes.get(binding);
                if (dataType == null) throw new IllegalArgumentException("模板字段绑定了不存在的模型属性: " + binding);
                detail.setPropertyTypeId(resolvePropertyTypeId(dataType, propertyTypes));
            } else {
                if (detail.getDefaultValue() == null) {
                    throw new IllegalArgumentException("未绑定模型属性的字段必须设置默认值: " + column);
                }
                Long typeId = sourceDetail.getPropertyTypeId();
                if (typeId == null) typeId = resolvePropertyTypeId("STRING", propertyTypes);
                if (!propertyTypeById.containsKey(typeId)) {
                    throw new IllegalArgumentException("模板字段引用了不存在的数据类型: " + typeId);
                }
                detail.setPropertyTypeId(typeId);
            }
            int length = sourceDetail.getColumnLength() == null ? 255 : sourceDetail.getColumnLength();
            detail.setColumnLength(Math.max(1, Math.min(length, 4096)));
            details.add(detail);
        }
        if (details.isEmpty()) throw new IllegalArgumentException("数据模板至少需要一个有效字段");
        return details;
    }

    private Map<String, String> modelAttributeTypes(JsonNode attributes) {
        Map<String, String> result = new HashMap<>();
        if (attributes == null || !attributes.isArray()) return result;
        for (JsonNode attribute : attributes) {
            String name = attribute.path("attributeName").asText("").trim();
            String type = attribute.path("dataType").asText("STRING").trim().toUpperCase(Locale.ROOT);
            if (!name.isBlank()) result.put(name, type);
        }
        return result;
    }

    private Long resolvePropertyTypeId(String dataType, List<PropertyType> propertyTypes) {
        List<String> candidates = switch (String.valueOf(dataType).toUpperCase(Locale.ROOT)) {
            case "INTEGER" -> List.of("integer", "int4", "int", "bigint");
            case "DOUBLE" -> List.of("double precision", "double", "float8", "numeric", "decimal");
            case "BOOLEAN" -> List.of("boolean", "bool");
            case "JSON" -> List.of("jsonb", "json");
            default -> List.of("varchar", "character varying", "text", "string");
        };
        for (PropertyType type : propertyTypes) {
            String dbType = String.valueOf(type.getDbType()).trim().toLowerCase(Locale.ROOT);
            if (candidates.contains(dbType)) return type.getId();
        }
        throw new IllegalStateException("系统未配置数据类型映射: " + dataType);
    }

    private List<DataTemplateDetail> schemaSpecDetails(Map<?, ?> schemaSpec) {
        List<DataTemplateDetail> result = new ArrayList<>();
        Object fields = schemaSpec.containsKey("fields") ? schemaSpec.get("fields") : schemaSpec.get("properties");
        if (fields instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> row) result.add(toDetail(row));
        } else if (fields instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> row) {
                    DataTemplateDetail detail = toDetail(row);
                    if (detail.getColumnName() == null) detail.setColumnName(String.valueOf(entry.getKey()));
                    result.add(detail);
                }
            }
        }
        return result;
    }

    private DataTemplateDetail toDetail(Map<?, ?> row) {
        DataTemplateDetail detail = new DataTemplateDetail();
        detail.setColumnName(stringValue(first(row, "columnName", "fieldName", "name")));
        detail.setColumnDesc(stringValue(first(row, "columnDesc", "description", "label")));
        detail.setDeviceAttrKey(stringValue(first(row, "deviceAttrKey", "attributeId", "attrKey")));
        detail.setDefaultValue(stringValue(first(row, "defaultValue")));
        Object typeId = first(row, "propertyTypeId", "typeId");
        if (typeId != null && !String.valueOf(typeId).isBlank()) detail.setPropertyTypeId(Long.valueOf(String.valueOf(typeId)));
        Object length = first(row, "columnLength", "length");
        detail.setColumnLength(length == null || String.valueOf(length).isBlank() ? 255 : Integer.valueOf(String.valueOf(length)));
        return detail;
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("模板字段名不能为空");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("模板字段名只能包含字母、数字和下划线，且不能以数字开头: " + value);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Object first(Map<?, ?> payload, String... keys) {
        for (String key : keys) if (payload.containsKey(key)) return payload.get(key);
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}