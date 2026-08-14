# 工作流动作编辑与子流程拖拽 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 固定工作流节点的 UPDATE/EMIT 动作能力，按内部变量类型编辑 UPDATE 常量，并恢复流程库拖拽创建子流程节点。

**Architecture:** 节点动作能力由工作流系统契约统一提供，前端只展示和使用，不再允许增删。UPDATE 常量沿用统一的类型值输入组件，并由目标内部变量推导类型。流程库拖拽通过纯函数判定是否可拖拽，画布入口再次阻止当前流程引用自身。

**Tech Stack:** Vue 3、Element Plus、Vue Flow、Node.js test runner、Spring Boot/JUnit 5

## Global Constraints

- 本轮不修改设备命令状态的适用范围。
- 本轮不增加多条件触发器。
- 保留现有工作区内其他未提交改动，不提交或推送。

---

### Task 1: 固定节点动作能力

**Files:**
- Modify: `Frontend/src/utils/workflowNodeDefinition.js`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue`
- Modify: `Backend/src/main/java/com/smartlab/global/contract/WorkflowNodeSystemContract.java`
- Test: `Frontend/tests/workflowNodeDefinition.test.mjs`
- Test: `Frontend/tests/workflow-designer-structure.test.mjs`
- Test: `Backend/src/test/java/com/smartlab/global/contract/WorkflowNodeSystemContractTest.java`

- [ ] 写入失败测试：所有节点及旧节点规范化后均包含 `UPDATE`、`EMIT`，界面不存在启用按钮。
- [ ] 运行定向测试并确认因当前动作子集设计失败。
- [ ] 将动作能力改为系统固定能力，并删除启用/移除入口。
- [ ] 运行定向测试并确认通过。

### Task 2: UPDATE 常量按目标变量类型编辑

**Files:**
- Modify: `Frontend/src/utils/workflowNodeDefinition.js`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue`
- Test: `Frontend/tests/workflowNodeDefinition.test.mjs`
- Test: `Frontend/tests/workflow-designer-structure.test.mjs`

- [ ] 写入失败测试：目标变量决定 INTEGER、DOUBLE、STRING、BOOLEAN 输入类型，切换目标时重置对应空值。
- [ ] 运行定向测试并确认失败。
- [ ] 使用 `WorkflowTypedValueInput` 替代 JSON 文本输入，删除字符串解析和 `null` 文本显示。
- [ ] 运行定向测试并确认通过。

### Task 3: 恢复流程库拖拽创建子流程

**Files:**
- Modify: `Frontend/src/utils/workflowDesignerRules.js`
- Modify: `Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue`
- Test: `Frontend/tests/workflow-designer-rules.test.mjs`
- Test: `Frontend/tests/workflow-designer-structure.test.mjs`

- [ ] 写入失败测试：编辑状态下其他流程可拖拽，当前流程和只读状态不可拖拽。
- [ ] 运行定向测试并确认失败。
- [ ] 恢复流程叶子项的原生拖拽绑定并向画布写入 workflow 资源载荷。
- [ ] 在拖拽入口和添加入口同时阻止当前流程引用自身。
- [ ] 运行定向测试并确认通过。

### Task 4: 回归验证

**Files:**
- Verify: `Frontend/tests/*.test.*`
- Verify: `Frontend/src/**`

- [ ] 运行工作流相关定向测试。
- [ ] 运行全部前端测试。
- [ ] 运行前端生产构建并检查没有编译错误。
