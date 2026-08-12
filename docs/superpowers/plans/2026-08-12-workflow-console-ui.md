# 流程设计器连续控制台界面实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将流程设计器改造成连续三栏控制台，并统一节点抽屉、触发器、节点、连线和画布的视觉语言。

**Architecture:** 保留现有 Vue 组件职责和业务事件，仅调整模板中的展示结构、中文文案和 scoped 样式。使用现有 Element Plus 与 Vue Flow，不增加依赖；通过源码结构测试锁定关键布局、字段宽度和业务边界。

**Tech Stack:** Vue 3、Element Plus、Vue Flow、Node.js 内置测试框架、Vite。

## Global Constraints

- 不改变流程模型 JSON、接口协议或后端执行逻辑。
- 固定界面文案全部使用中文。
- 系统默认接口和触发器保持只读，功能节点自定义能力保持不变。
- 主色使用 `#1677ff`，内容面使用白色，布局底色使用 `#f5f5f5`。
- 动效只用于悬停、选中和抽屉切换，持续时间为 150 至 200 毫秒。
- 不增加前端依赖。

---

### Task 1: 锁定连续工作台结构

**Files:**
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue`

**Interfaces:**
- Consumes: 现有 `designer-grid`、`resource-panel`、`workflow-overview-panel` 和 `workflow-element-drawer`。
- Produces: 218 像素资源面板、304 像素流程属性栏、960 像素抽屉上限和无卡片间距的连续布局。

- [ ] **Step 1:** 增加断言，要求三栏宽度、抽屉宽度、中文标题和连续布局类名。
- [ ] **Step 2:** 运行 `node --test tests/workflow-designer-structure.test.mjs`，确认旧布局断言失败。
- [ ] **Step 3:** 修改页面模板和样式，删除悬浮岛、灰色卡片间距和英文装饰文案。
- [ ] **Step 4:** 重新运行结构测试并确认通过。

### Task 2: 改造节点抽屉连续内容面

**Files:**
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowBusinessPanel.vue`

**Interfaces:**
- Consumes: `WorkflowNodeInspector` 的五个标签页和现有更新事件。
- Produces: 连续白色标题、节点统计、标签页和内容区；所有固定标题中文化。

- [ ] **Step 1:** 增加抽屉无外边距、无灰色间隙、无英文标题的失败断言。
- [ ] **Step 2:** 运行结构测试并确认失败原因来自旧样式。
- [ ] **Step 3:** 修改抽屉和业务配置面板样式，保留现有事件与数据绑定。
- [ ] **Step 4:** 运行结构测试并确认通过。

### Task 3: 将触发器改造成规则构建器

**Files:**
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue`

**Interfaces:**
- Consumes: `bindingTriggers[]`、内联 `condition` 和 `action`，以及现有编辑函数。
- Produces: “当……则……”规则行和按数据长度分配的字段宽度。

- [ ] **Step 1:** 增加 `condition-rule-row`、`action-rule-row`、中文动作标签和字段宽度断言。
- [ ] **Step 2:** 运行结构测试并确认旧三等分网格失败。
- [ ] **Step 3:** 重构模板展示结构和 CSS，不改变触发器对象格式。
- [ ] **Step 4:** 运行结构测试并确认通过。

### Task 4: 统一节点、端口、连线和画布

**Files:**
- Modify: `Frontend/tests/workflow-designer-structure.test.mjs`
- Modify: `Frontend/src/views/task/WorkflowDesigner/components/WorkflowCanvasNode.vue`
- Modify: `Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue`

**Interfaces:**
- Consumes: Vue Flow 节点、Handle 和 edge class。
- Produces: 紧凑节点、两类端口形状、点阵网格及两类连线样式。

- [ ] **Step 1:** 增加节点尺寸、端口形状和连线颜色的失败断言。
- [ ] **Step 2:** 运行结构测试并确认旧视觉值失败。
- [ ] **Step 3:** 修改节点与画布样式，保持拖拽、选择和连线逻辑不变。
- [ ] **Step 4:** 运行结构测试并确认通过。

### Task 5: 完整验证和通知

**Files:**
- Test: `Frontend/tests/`

**Interfaces:**
- Consumes: 前四项实施结果。
- Produces: 可构建的前端与完成通知。

- [ ] **Step 1:** 运行 `node --test tests`，确认全部测试通过。
- [ ] **Step 2:** 运行 `npm run build`，确认生产构建成功。
- [ ] **Step 3:** 启动本地页面并检查浏览器控制台无新增错误。
- [ ] **Step 4:** 使用已配置的邮件能力向 `2296029664@qq.com` 发送完成通知；若未安装邮件连接，则明确报告无法发送并给出所需连接。
