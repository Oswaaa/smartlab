# SmartLab Console UI Unification Implementation Plan

> **For agentic workers:** Execute inline in this session and review after each page group.

**Goal:** 将七个管理页面统一为安静、紧凑、可扫描的企业级实验室控制台，同时保持现有业务功能不变。

**Architecture:** 在全局样式中建立控制台设计令牌与 Element Plus 基线，再针对资产工作台、表格列表和流程设计器三类页面结构做局部调整。页面继续使用现有 Vue 组件和接口，仅改变展示层、操作层级和局部前端筛选状态。

**Tech Stack:** Vue 3、Element Plus、Vue Flow、ECharts、Vite。

## Global Constraints

- 不修改后端接口和数据库。
- 不删除现有业务能力。
- 不新增 UI 依赖。
- 不使用玻璃拟态、装饰渐变、悬浮位移动画或无意义大留白。
- 使用系统字体和统一的状态色、间距、边框与抽屉结构。

---

### Task 1: 全局设计基线

**Files:**
- Modify: Frontend/src/style.css

- [ ] 移除远程字体依赖和表头全大写规则。
- [ ] 建立页面、工具栏、数据表、抽屉、状态标签的统一样式。
- [ ] 保留现有类名兼容，但覆盖装饰性样式。

### Task 2: 设备资产工作台

**Files:**
- Modify: Frontend/src/views/device/DeviceModelManagement.vue
- Modify: Frontend/src/views/device/DeviceInstanceManagement.vue
- Modify: Frontend/src/views/device/AdapterManagement.vue
- Modify: Frontend/src/views/device/components/DeviceModelTree.vue

- [ ] 统一左栏宽度、搜索、对象计数和选中态。
- [ ] 统一右侧标题栏、摘要栏、标签页和表格密度。
- [ ] 收敛刷新、删除等次级操作。

### Task 3: 流程与任务

**Files:**
- Modify: Frontend/src/views/task/WorkflowDesigner.vue
- Modify: Frontend/src/views/task/TaskList.vue

- [ ] 将设计器调整为稳定三栏工作区，移除玻璃与悬浮效果。
- [ ] 将任务页调整为标准列表页，统一筛选、状态和危险操作层级。
- [ ] 保持任务详情局部静默轮询。

### Task 4: 数据与用户

**Files:**
- Modify: Frontend/src/views/data/DataManagement.vue
- Modify: Frontend/src/views/admin/UserManagement.vue

- [ ] 统一数据资产树、图表、详情和建表抽屉。
- [ ] 为用户页加入概览、搜索、角色筛选和统一详情抽屉。

### Task 5: 产品审查与验证

**Files:**
- Review all files above.

- [ ] 从定位效率、主操作可发现性、危险操作、空状态、密度和一致性六方面审查。
- [ ] 修复审查发现的问题。
- [ ] 运行 npm run build。
- [ ] 启动本地页面并完成关键路由截图审查。