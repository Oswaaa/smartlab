# 工作流画布 UI 优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除画布节点连接点重叠，统一资源侧边栏，并提供按真实节点尺寸计算的智能 DAG 自动布局。

**Architecture:** `workflowCanvas.js` 提供节点尺寸和自动布局纯函数，`WorkflowCanvasNode.vue` 只负责按共享尺寸呈现四向连接轨道，`WorkflowDesigner.vue` 负责资源树和 Vue Flow 交互。业务模型及后端协议保持不变。

**Tech Stack:** Vue 3、Vue Flow、Element Plus、Node.js 内置测试运行器。

## Global Constraints

- STATE 接口不显示在画布。
- 画布不常驻显示接口或端口文字，完整信息仅在悬停连接点时显示。
- 不改变工作流模型、连接校验或后端执行逻辑。
- 在当前 `main` 工作区修改，不创建分支，不自动提交或推送。

---

### Task 1: 节点尺寸与 DAG 自动布局

**Files:**
- Modify: `Frontend/src/utils/workflowCanvas.js`
- Test: `Frontend/tests/workflowCanvas.test.mjs`

**Interfaces:**
- Produces: `workflowCanvasNodeSize(node): { width: number, height: number }`
- Produces: `buildWorkflowAutoLayout(nodes, interfaceConnections, portConnections): Record<string, { x: number, y: number }>`

- [ ] **Step 1: 编写失败测试**

覆盖基础节点尺寸、接口增加时高度增长、端口增加时宽度增长；覆盖线性流程的从左到右分层、分支同层展开、汇聚收拢、断开节点和环路稳定回退。

- [ ] **Step 2: 运行测试并确认缺少新 API**

Run: `node --test tests/workflowCanvas.test.mjs`

- [ ] **Step 3: 实现共享尺寸和布局纯函数**

尺寸仅统计 WORKFLOW 接口及全部数据端口。布局使用拓扑层级、上游重心排序、实际节点尺寸、150px 层间距和 72px 同层间距。

- [ ] **Step 4: 运行测试并确认通过**

Run: `node --test tests/workflowCanvas.test.mjs`

---

### Task 2: 四向连接轨道节点

**Files:**
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue`
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`

**Interfaces:**
- Consumes: `workflowCanvasNodeSize(node)`

- [ ] **Step 1: 编写失败结构测试**

断言节点使用共享尺寸函数和四向连接点；断言画布不存在常驻连接文字，完整信息通过悬停提示提供；断言设备能力摘要包含能力名称与参数数量。

- [ ] **Step 2: 运行结构测试并确认旧节点失败**

Run: `node --test tests/workflow-designer-structure.test.mjs`

- [ ] **Step 3: 重构节点模板和样式**

连接点按总数均匀定位；圆形蓝色表示执行接口，方形紫色表示数据端口；节点主体只显示节点信息，连接点使用脱离节点布局的提示浮层显示方向、完整名称和端口数据类型。

- [ ] **Step 4: 运行结构测试并确认通过**

Run: `node --test tests/workflow-designer-structure.test.mjs`

---

### Task 3: 紧凑资源树、连线和自动布局接入

**Files:**
- Modify: `Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue`
- Modify: `Frontend/src/utils/workflowCanvas.js`
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/tests/workflowCanvas.test.mjs`

**Interfaces:**
- Consumes: `buildWorkflowAutoLayout(nodes, interfaceConnections, portConnections)`

- [ ] **Step 1: 编写失败测试**

结构测试断言资源树使用统一节点文字和元信息结构、无模型/实例彩色标签；画布测试断言执行流和数据流使用平滑正交路径及不同线型。

- [ ] **Step 2: 运行相关测试并确认旧实现失败**

Run: `node --test tests/workflowCanvas.test.mjs tests/workflow-designer-structure.test.mjs`

- [ ] **Step 3: 接入资源树与自动布局**

侧边栏使用 34px 树行、统一图标和元信息；`autoLayout()` 调用纯布局函数，下一帧刷新节点内部尺寸并执行 `fitView`。

- [ ] **Step 4: 优化连线和画布视觉**

执行流使用蓝灰实线，数据流使用紫色虚线；平滑正交路径保留 24px 出入口直线段；画布背景改为浅色 20px 点阵。

- [ ] **Step 5: 运行完整验证**

Run: `node --test --test-concurrency=1`

Run: `npm run build`

Run: `git diff --check -- Frontend/src/utils/workflowCanvas.js Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue Frontend/src/views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue Frontend/tests/workflowCanvas.test.mjs Frontend/tests/workflow-designer-structure.test.mjs`
