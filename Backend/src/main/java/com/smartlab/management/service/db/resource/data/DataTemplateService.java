package com.smartlab.management.service.db.resource.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.dto.resource.data.DataTemplateSaveDTO;
import com.smartlab.management.entity.resource.data.DataTemplateDetail;
import com.smartlab.management.entity.resource.data.DataTemplateMain;
import com.smartlab.management.mapper.resource.data.DataIndexMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateDetailMapper;
import com.smartlab.management.mapper.resource.data.DataTemplateMainMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据模板表服务。
 * 对应 DATA_TEMPLATE_MAIN 和 DATA_TEMPLATE_DETAIL 两张表。
 */
@Service
/**
 * DataTemplate业务持久层核心操作服务。
 */
public class DataTemplateService extends ManagementCrudService<DataTemplateMain> {

    private final DataTemplateMainMapper mainMapper;
    private final DataTemplateDetailMapper detailMapper;
    private final DataIndexMapper dataIndexMapper;

    public DataTemplateService(DataTemplateMainMapper mainMapper,
                               DataTemplateDetailMapper detailMapper,
                               DataIndexMapper dataIndexMapper) {
        super(mainMapper);
        this.mainMapper = mainMapper;
        this.detailMapper = detailMapper;
        this.dataIndexMapper = dataIndexMapper;
    }

    @Override
    public List<DataTemplateMain> list() {
        return mainMapper.selectList(Wrappers.<DataTemplateMain>lambdaQuery().orderByDesc(DataTemplateMain::getId));
    }

    public List<DataTemplateDetail> listDetails(Long templateId) {
        return detailMapper.selectList(
                Wrappers.<DataTemplateDetail>lambdaQuery()
                        .eq(DataTemplateDetail::getDataTemplateId, templateId)
                        .orderByAsc(DataTemplateDetail::getId)
        );
    }

    /**
     * 按 DTO 保存数据模板主表和字段明细。
     */
    @Transactional(rollbackFor = Exception.class)
    public DataTemplateMain saveTemplate(DataTemplateSaveDTO dto) {
        if (dto == null || dto.getMain() == null) {
            throw new IllegalArgumentException("模板主表不能为空");
        }
        DataTemplateMain main = dto.getMain();
        if (main.getId() == null) {
            main.setCreateTime(LocalDateTime.now());
            mainMapper.insert(main);
        } else {
            mainMapper.updateById(main);
            detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery().eq(DataTemplateDetail::getDataTemplateId, main.getId()));
        }

        for (DataTemplateDetail detail : dto.getDetails()) {
            detail.setId(null);
            detail.setDataTemplateId(main.getId());
            if (detail.getCreateTime() == null) {
                detail.setCreateTime(LocalDateTime.now());
            }
            detailMapper.insert(detail);
        }
        ensureSingleDefaultTemplate(main);
        return main;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataTemplateMain savePayload(Map<String, Object> payload) {
        DataTemplateMain main = new DataTemplateMain();
        Object id = first(payload, "id", "templateId");
        if (id != null && !String.valueOf(id).isBlank()) {
            main.setId(Long.valueOf(String.valueOf(id)));
        }
        main.setTemplateName(stringValue(first(payload, "templateName", "name")));
        main.setTemplateDesc(stringValue(first(payload, "templateDesc", "description")));
        Object modelId = first(payload, "deviceModelId", "modelId");
        if (modelId != null && !String.valueOf(modelId).isBlank()) {
            main.setDeviceModelId(Long.valueOf(String.valueOf(modelId)));
        }
        if (payload.containsKey("isDefault")) {
            main.setIsDefault(Boolean.valueOf(String.valueOf(payload.get("isDefault"))));
        }
        if (main.getId() == null) {
            main.setCreateTime(LocalDateTime.now());
            mainMapper.insert(main);
        } else {
            mainMapper.updateById(main);
        }

        Object detailPayload = first(payload, "details", "columns");
        if (detailPayload instanceof List<?> details) {
            detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery().eq(DataTemplateDetail::getDataTemplateId, main.getId()));
            for (Object item : details) {
                if (item instanceof Map<?, ?> row) {
                    DataTemplateDetail detail = toDetail(main.getId(), row);
                    detailMapper.insert(detail);
                }
            }
        } else if (payload.get("dataSchemaSpec") instanceof Map<?, ?> schemaSpec) {
            upsertSchemaSpecDetails(main.getId(), schemaSpec);
        }
        ensureSingleDefaultTemplate(main);
        return main;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Long templateId = parseId(id);
        Long dataSetCount = dataIndexMapper.selectCount(
                Wrappers.<com.smartlab.management.entity.resource.data.DataIndex>lambdaQuery()
                        .eq(com.smartlab.management.entity.resource.data.DataIndex::getDataTemplateId, templateId)
        );
        if (dataSetCount != null && dataSetCount > 0) {
            throw new IllegalStateException("该数据模板已生成 " + dataSetCount + " 个数据集，不能删除。请保留模板与历史数据表的解释关系。");
        }
        detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery().eq(DataTemplateDetail::getDataTemplateId, templateId));
        mainMapper.deleteById(templateId);
    }

    private void upsertSchemaSpecDetails(Long templateId, Map<?, ?> schemaSpec) {
        if (!schemaSpec.containsKey("fields") && !schemaSpec.containsKey("properties")) {
            return;
        }
        detailMapper.delete(Wrappers.<DataTemplateDetail>lambdaQuery().eq(DataTemplateDetail::getDataTemplateId, templateId));
        Object fields = schemaSpec.containsKey("fields") ? schemaSpec.get("fields") : schemaSpec.get("properties");
        if (fields instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    detailMapper.insert(toDetail(templateId, row));
                }
            }
        } else if (fields instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> row) {
                    DataTemplateDetail detail = toDetail(templateId, row);
                    if (detail.getColumnName() == null) {
                        detail.setColumnName(String.valueOf(entry.getKey()));
                    }
                    detailMapper.insert(detail);
                }
            }
        }
    }

    private DataTemplateDetail toDetail(Long templateId, Map<?, ?> row) {
        DataTemplateDetail detail = new DataTemplateDetail();
        detail.setDataTemplateId(templateId);
        detail.setColumnName(stringValue(first(row, "columnName", "fieldName", "name")));
        detail.setColumnDesc(stringValue(first(row, "columnDesc", "description", "label")));
        detail.setDeviceAttrKey(stringValue(first(row, "deviceAttrKey", "attributeId", "attrKey")));
        detail.setDefaultValue(stringValue(first(row, "defaultValue")));
        Object propertyTypeId = first(row, "propertyTypeId", "typeId");
        if (propertyTypeId != null && !String.valueOf(propertyTypeId).isBlank()) {
            detail.setPropertyTypeId(Long.valueOf(String.valueOf(propertyTypeId)));
        }
        Object length = first(row, "columnLength", "length");
        if (length != null && !String.valueOf(length).isBlank()) {
            detail.setColumnLength(Integer.valueOf(String.valueOf(length)));
        }
        detail.setCreateTime(LocalDateTime.now());
        return detail;
    }

    private void ensureSingleDefaultTemplate(DataTemplateMain main) {
        if (!Boolean.TRUE.equals(main.getIsDefault()) || main.getDeviceModelId() == null || main.getId() == null) {
            return;
        }
        mainMapper.update(
                null,
                Wrappers.<DataTemplateMain>lambdaUpdate()
                        .eq(DataTemplateMain::getDeviceModelId, main.getDeviceModelId())
                        .eq(DataTemplateMain::getIsDefault, true)
                        .ne(DataTemplateMain::getId, main.getId())
                        .set(DataTemplateMain::getIsDefault, false)
        );
    }

    private Object first(Map<?, ?> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}


