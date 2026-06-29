package com.smartlab.management.service.db.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smartlab.management.entity.workflow.FlowNode;
import com.smartlab.management.mapper.workflow.FlowNodeMapper;
import com.smartlab.management.service.db.common.ManagementCrudService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流程节点表服务。
 * 对应 FLOW_NODE 表，用于维护流程模型中的节点明细。
 */
@Service
/**
 * FlowNode业务持久层核心操作服务。
 */
public class FlowNodeService extends ManagementCrudService<FlowNode> {

    private final FlowNodeMapper mapper;

    public FlowNodeService(FlowNodeMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public List<FlowNode> listByFlowModel(Long flowModelId) {
        if (flowModelId == null) {
            return list();
        }
        return mapper.selectList(
                Wrappers.<FlowNode>lambdaQuery()
                        .eq(FlowNode::getFlowModelId, flowModelId)
                        .orderByAsc(FlowNode::getId));
    }
}
