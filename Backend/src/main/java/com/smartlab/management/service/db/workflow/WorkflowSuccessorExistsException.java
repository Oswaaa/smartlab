package com.smartlab.management.service.db.workflow;

import com.smartlab.management.dto.workflow.WorkflowSuccessorConflict;
import com.smartlab.management.entity.workflow.FlowModels;

/** 一条谱系只允许一个后续版本：对已有 successor 的 ACTIVE 再次 fork 时抛出。 */
public class WorkflowSuccessorExistsException extends IllegalStateException {
    private final WorkflowSuccessorConflict conflict;

    public WorkflowSuccessorExistsException(FlowModels parent, FlowModels successor) {
        super(buildMessage(parent, successor));
        this.conflict = WorkflowSuccessorConflict.from(successor);
    }

    public WorkflowSuccessorConflict conflict() {
        return conflict;
    }

    private static String buildMessage(FlowModels parent, FlowModels successor) {
        String name = firstNonBlank(successor == null ? null : successor.getFlowName(),
                parent == null ? null : parent.getFlowName(), "流程");
        String successorVersion = successor == null || successor.getVersion() == null ? "" : " v" + successor.getVersion();
        String parentVersion = parent == null || parent.getVersion() == null ? "" : " v" + parent.getVersion();
        if (successor != null && "ACTIVE".equalsIgnoreCase(successor.getStatus())) {
            return "流程「" + name + "」已有后续版本" + successorVersion
                    + "（已启用）。请打开该版本继续编辑，不能从当前版本" + parentVersion + " 再创建后续。";
        }
        return "流程「" + name + "」已有后续版本" + successorVersion
                + "（草稿）。请打开该草稿继续编辑；若要重新从当前版本" + parentVersion + " 创建后续，请先删除该草稿。";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
