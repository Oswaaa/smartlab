# 工作流接口方向切换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将控制接口创建改为单一入口，并在方向切换时安全清理连线和不兼容触发器。

**Architecture:** 方向默认值和切换规则放在 `workflowNodeDefinition.js` 的纯函数中，界面只负责调用纯函数、展示确认框并发布结果。聚合计数触发器仍由统一工厂生成，节点级激活触发器不复制到自定义输入接口。

**Tech Stack:** Vue 3、Element Plus、Node.js 内置测试运行器。

## Global Constraints

- 不创建新分支或工作区，不自动提交。
- 聚合 UPDATE 继续使用 `targetName: aggregateCount` 和 `valueExpression: aggregateCount + 1`。
- 聚合节点只保留一条节点级 `PENDING -> RUNNING` 激活触发器。
- 方向切换清理数据前必须由用户确认。

---

### Task 1: 纯数据方向切换规则

**Files:**
- Modify: `Frontend/src/utils/workflowNodeDefinition.js`
- Test: `Frontend/tests/workflowNodeDefinition.test.mjs`

**Interfaces:**
- Produces: `defaultWorkflowInterfaceDirection(node): 'IN' | 'OUT'`
- Produces: `changeWorkflowInterfaceDirection(node, interfaceName, direction, interfaceConnections): { node, interfaceConnections, removedConnectionCount, removedTriggerCount }`

- [x] **Step 1: 编写失败测试**

覆盖分支默认 `OUT`、聚合默认 `IN`，以及聚合接口在方向切换时补齐/移除系统计数触发器、删除相关连线和清理 `IN` 接口上的 `EMIT` 触发器。

- [x] **Step 2: 验证测试因缺少方向切换 API 而失败**

Run: `node --test tests/workflowNodeDefinition.test.mjs`

- [x] **Step 3: 实现最小纯函数**

方向切换到 `IN` 时过滤 `EMIT`，聚合节点确保恰好存在一条 `aggregateCounterTrigger(interfaceName)`；切换到 `OUT` 时移除 `isAggregateCounterTrigger(trigger)`。方向真正变化时过滤涉及该接口的全部连接，并返回清理数量供 UI 决定是否确认。

- [x] **Step 4: 验证测试通过**

Run: `node --test tests/workflowNodeDefinition.test.mjs`

---

### Task 2: 单一新增入口与确认交互

**Files:**
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue`
- Test: `Frontend/tests/workflow-designer-structure.test.mjs`

**Interfaces:**
- Consumes: `defaultWorkflowInterfaceDirection`
- Consumes: `changeWorkflowInterfaceDirection`

- [x] **Step 1: 编写失败结构测试**

断言面板不再包含“新增输入接口/新增输出接口”，单一按钮调用 `addInterface`，方向选择调用异步确认处理器，并使用 `ElMessageBox.confirm`。

- [x] **Step 2: 验证测试因旧下拉创建方式而失败**

Run: `node --test tests/workflow-designer-structure.test.mjs`

- [x] **Step 3: 实现界面交互**

将 `el-dropdown` 替换为单一按钮。新增接口时使用节点类型默认方向。方向变化先计算清理结果；有连线或不兼容触发器时显示确认框，确认后同时发布节点和连接集合，取消时保持原值。

- [x] **Step 4: 运行相关测试和生产构建**

Run: `node --test tests/workflowNodeDefinition.test.mjs tests/workflow-designer-structure.test.mjs`

Run: `npm run build`
