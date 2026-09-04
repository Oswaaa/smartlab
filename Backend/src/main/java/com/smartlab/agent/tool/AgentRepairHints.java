package com.smartlab.agent.tool;

final class AgentRepairHints {
    private AgentRepairHints() {
    }

    static String forParseFailure(String jacksonMessage) {
        String message = jacksonMessage == null ? "" : jacksonMessage;
        String repair = infer(null, message);
        if (repair == null) {
            repair = "对照技能里的工作流模型结构说明重写 document，不要抄导出文件中的系统字段";
        }
        return repair + "。原始错误: " + (message.isBlank() ? "无法解析工作流草稿" : message);
    }

    static String forIssue(String code, String message, String suggestion) {
        String inferred = infer(code, message);
        if (inferred != null) return inferred;
        if (suggestion != null && !suggestion.isBlank() && !"请修正工作流定义后重试".equals(suggestion)) {
            return suggestion;
        }
        if (message != null && !message.isBlank()) {
            return "根据 message 修改工作流草稿后再次 validate_workflow，不要改系统 lifecycle/接口";
        }
        return suggestion;
    }

    private static String infer(String code, String message) {
        String text = message == null ? "" : message;
        if ("WORKFLOW_SYSTEM_FIELD_OVERRIDDEN".equals(code) || "WORKFLOW_SYSTEM_NAME_RESERVED".equals(code)) {
            return "不要写 lifecycle、系统默认控制接口、_system/_systemKey；这些由规范化器补全。用户自定义控制接口（分支出口等）可以保留";
        }
        if (containsAny(text, "\"name\"", "metadata.name", "Unrecognized field \"name\"")) {
            return "metadata.name 不是合法字段，改成 metadata.flowModelName";
        }
        if (containsAny(text, "fromNodeId", "toNodeId")) {
            return "控制连线不要写 fromNodeId/toNodeId，使用 interfaceConnections 的 source/target（nodeName + interfaceName）";
        }
        if (containsAny(text, "不支持的节点类型: START", "不支持的节点类型: END", "不支持的节点类型: BRANCH", "不支持的节点类型: AGGREGATE")) {
            return "START/END/BRANCH/AGGREGATE 是 functionType，nodeType 必须是 FUNC_NODE";
        }
        if (text.contains("capability") && text.contains("parameters") && !text.contains("capabilityParameters")) {
            return "能力参数写在 capability.capabilityParameters，不要写 capability.parameters";
        }
        if (containsAny(text, "ports必须是数组", "ports: 必须是数组", ".ports") && containsAny(text, "必须是数组", "不能为null", "不能为空", "缺少")) {
            return "每个节点都要有 ports 数组；无数据传递时写 []";
        }
        if (text.contains("必须且只能包含一个START") || text.contains("必须且只能包含一个END")
                || text.contains("必须且只能包含一个START和一个END")) {
            return "必须有且仅有一个 FUNC_NODE+functionType=START，以及一个 FUNC_NODE+functionType=END";
        }
        if (text.contains("触发条件object必须是本节点内部变量或系统标识")) {
            return "condition.object 只能写本节点 internalVariables[].name，或系统标识 nodeLifecycleState / taskLifecycleState / signalName / payload.stateName；不要写设备属性名、别的节点的变量名，也不要写 inputSignalName";
        }
        if (containsAny(text, "设备状态机不存在接口", "NODE_TO_DEVICE目标必须是", "DEVICE_TO_NODE源必须是")) {
            return "设备控制连线的 interfaceName 必须来自 get_device_model 的状态机接口：NODE_TO_DEVICE 目标为 IN+WORKFLOW，DEVICE_TO_NODE 源为 OUT+STATE";
        }
        if (containsAny(text, "生命周期状态不存在", "任务生命周期状态不存在", "指令状态不存在")) {
            return "系统标识的 threshold 必须落在对应枚举里：nodeLifecycleState 用节点 lifecycle.states，taskLifecycleState 用任务状态，payload.stateName 用指令状态";
        }
        if (text.contains("信号不在接口允许列表中")) {
            return "IN 接口上 signalName 的 threshold 必须是该接口 allowedSignals 中的信号，WORKFLOW IN 通常是 ACTIVE";
        }
        if (text.contains("attributesMapping只允许出现在DEV_NODE")) {
            return "attributesMapping 只能写在 DEV_NODE 的变量上，用来映射该设备模型属性";
        }
        if (text.contains("子流程必须是ACTIVE才能被引用")) {
            return "SUBFLOW_NODE.subFlowModelId 只能引用 ACTIVE 流程，草稿不能当子流程";
        }
        if (containsAny(text, "nodes必须是非空数组", "nodes必须是")) {
            return "nodes 写成非空数组，至少包含 START、设备或功能步骤、END";
        }
        if (text.contains("SIM_NO_ADAPTER_EVENT") || "SIM_NO_ADAPTER_EVENT".equals(code)) {
            return "走图缺 Adapter cmd 事件，改稿通常走不通。确认设备模型 SENT→RUNNING 与 RUNNING→COMPLETED 各有唯一 Interface_adapter_in 事件，且事件名出现在 parsed_config.deviceTemplate.events.cmdEvents";
        }
        if (text.contains("SIM_DEVICE_FAILED") || "SIM_DEVICE_FAILED".equals(code)
                || text.contains("SIM_DEVICE_REJECTED") || "SIM_DEVICE_REJECTED".equals(code)) {
            return "走图时设备状态机没有正常完成。检查 NODE_TO_DEVICE/DEVICE_TO_NODE 与设备接口；SENT 后回到 IDLE 可能是看门狗超时，不是 COMPLETED";
        }
        if (text.contains("SIM_CYCLE") || "SIM_CYCLE".equals(code)) {
            return "NODE_TO_NODE 或子流程引用成环，删掉环路后再 simulate_workflow";
        }
        return null;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }
}
