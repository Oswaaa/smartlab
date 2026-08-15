# Task Resource Binding UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让任务创建抽屉直接使用后端设备绑定要求，准确完成设备实例绑定、预检和任务创建。

**Architecture:** `requirements[].slotId` 是设备绑定的唯一标识。创建抽屉不再把流程详情节点与后端槽位二次匹配，而是直接将 requirements 渲染为按出现路径分组的设备绑定列表；预检与正式创建复用同一份 `deviceBindings`。

**Tech Stack:** Vue 3、Element Plus、Node.js test runner

## Global Constraints

- 不修改后端接口和数据库结构。
- 不在创建任务抽屉中实现流程图。
- 不向普通用户展示内部错误代码。
- 不兼容旧的前端名称路径绑定格式。

---

### Task 1: 以后端 requirements 驱动设备绑定

**Files:**
- Modify: `Frontend/src/utils/taskResourceBindings.js`
- Modify: `Frontend/src/views/task/TaskList/TaskList.vue`
- Test: `Frontend/tests/taskResourceBindings.test.js`

**Interfaces:**
- Consumes: `requirements[].slotId/occurrencePath/flowName/nodeName/deviceModelId/capabilityName`
- Produces: `groupRequirementsByOccurrencePath(requirements)` 与基于 slotId 的 `deviceBindings`

- [ ] 写失败测试，使用不含 `nodeIdRef` 的真实 `nodesDef` 结构，验证绑定列表完全由 requirements 生成。
- [ ] 运行 `node --test tests/taskResourceBindings.test.js`，确认测试因缺少按出现路径分组能力而失败。
- [ ] 删除 `bindRequirementsToExpandedWorkflow` 及其调用；保留流程展开数据仅供流程节点和接口校验使用。
- [ ] 让预检、约束资源和正式创建统一读取 `resourceBindings[slotId]`。
- [ ] 重新运行定向测试并确认通过。

### Task 2: 改造设备绑定列表和预检提示

**Files:**
- Modify: `Frontend/src/views/task/TaskList/components/TaskCreateDrawer.vue`
- Modify: `Frontend/src/views/task/TaskList/components/TaskResourceBindingCanvas.vue`
- Modify: `Frontend/src/views/task/TaskList/components/TaskPreflightPanel.vue`
- Test: `Frontend/tests/task-creation-constraint-workflow.test.mjs`

**Interfaces:**
- Consumes: Task 1 提供的 requirements 分组及 `resourceBindings[slotId]`
- Produces: 按路径分组的设备绑定列表和中文预检提示

- [ ] 写失败测试，验证创建抽屉传入 requirements、绑定区域不再显示“流程实例化视图”、预检不显示内部错误代码。
- [ ] 运行相关测试并确认因旧界面结构失败。
- [ ] 将绑定区域改成按出现路径分组的列表，每行显示节点、能力、设备模型、实例选择和绑定状态。
- [ ] 未完成绑定时在对应行提示，并在预检前给出明确数量提示。
- [ ] 将常见预检错误代码映射为中文业务标题，内部代码不直接展示。
- [ ] 运行完整前端测试和生产构建。

