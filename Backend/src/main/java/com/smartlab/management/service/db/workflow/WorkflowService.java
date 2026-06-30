package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartlab.global.util.JsonNodeSupport;
import com.smartlab.management.entity.workflow.FlowModels;
import com.smartlab.management.mapper.workflow.FlowModelsMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程模型表服务。
 * 对应 FLOW_MODELS 表，只负责流程模型的基础保存、查询和删除。
 */
@Service
/**
 * 工作流模板编排、定义与发布核心服务。
 */
public class WorkflowService extends ManagementCrudService<FlowModels> {

    private final FlowModelsMapper mapper;

    public WorkflowService(FlowModelsMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    @Override
    public List<FlowModels> list() {
        return mapper.selectList(Wrappers.<FlowModels>lambdaQuery().orderByDesc(FlowModels::getId));
    }

    public FlowModels getById(String id) {
        return mapper.selectById(parseId(id));
    }

    public FlowModels savePayload(Map<String, Object> payload) {
        FlowModels model = new FlowModels();
        Object id = first(payload, "id", "templateId", "workflowId");
        if (id != null && !String.valueOf(id).isBlank()) {
            model.setId(Long.valueOf(String.valueOf(id)));
        }
        model.setFlowName(stringValue(first(payload, "flowName", "templateName", "workflowName", "name")));
        model.setDescription(stringValue(first(payload, "description", "templateDesc")));
        model.setVersion(intValue(first(payload, "version"), 1));
        model.setStatus(stringValue(first(payload, "status", "workflowStatus")));
        if (model.getStatus() == null) {
            model.setStatus("ACTIVE");
        }
        putIfPresent(payload, "nodes", model::setNodes);
        putIfPresent(payload, "nodesDef", model::setNodes);
        putIfPresent(payload, "interfaceConnection", model::setInterfaceConnection);
        putIfPresent(payload, "interfaceConnections", model::setInterfaceConnection);
        putIfPresent(payload, "portConnection", model::setPortConnection);
        putIfPresent(payload, "portConnections", model::setPortConnection);
        Object creatorId = first(payload, "creatorId");
        if (creatorId != null && !String.valueOf(creatorId).isBlank()) {
            model.setCreatorId(Long.valueOf(String.valueOf(creatorId)));
        }
        if (model.getId() == null) {
            model.setCreateTime(OffsetDateTime.now());
            mapper.insert(model);
        } else {
            mapper.updateById(model);
        }
        return model;
    }

    public void delete(String id) {
        mapper.deleteById(parseId(id));
    }

    private Object first(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> payload, String key, java.util.function.Consumer<JsonNode> setter) {
        if (payload.containsKey(key)) {
            setter.accept(JsonNodeSupport.toNode(payload.get(key)));
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value, int defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Integer.valueOf(String.valueOf(value));
    }
}


