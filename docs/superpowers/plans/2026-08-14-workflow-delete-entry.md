# Workflow Delete Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在流程设计页面为已保存流程增加安全、可反馈的删除入口。

**Architecture:** 复用现有 `WorkflowController.delete` 后端接口，在 `workflowApi` 增加删除方法，并由 `WorkflowDesigner` 负责确认、请求、布局清理、页面重置和流程库刷新。删除约束继续完全由后端服务判定。

**Tech Stack:** Vue 3、Element Plus、Axios、Node Test Runner、Vite

## Global Constraints

- 仅删除已持久化流程，新建未保存流程不显示删除入口。
- 删除必须二次确认，并展示后端拒绝原因。
- 不修改后端删除规则，不增加批量删除。
- 保留工作区内无关的未跟踪文件。

---

### Task 1: 删除接口与页面契约

**Files:**
- Modify: `Frontend/src/services/workflowApi.js`
- Modify: `Frontend/src/views/task/WorkflowDesigner/WorkflowDesigner.vue`
- Test: `Frontend/tests/workflow-api.test.mjs`
- Test: `Frontend/tests/workflow-designer-structure.test.mjs`

**Interfaces:**
- Consumes: `DELETE /api/workflow/delete/{id}`
- Produces: `workflowApi.delete(id)` 与 `deleteCurrentWorkflow()` 页面操作

- [ ] **Step 1: 编写失败测试**

使用 Axios 测试适配器验证 `workflowApi.delete(42)` 发出 `DELETE /api/workflow/delete/42`；在既有页面结构测试中覆盖仅对已保存流程显示的删除按钮、确认文案、删除调用、布局清理、页面重置和列表刷新。

- [ ] **Step 2: 运行测试并确认因删除能力缺失而失败**

Run: `node --test tests/workflow-designer-structure.test.mjs`

- [ ] **Step 3: 实现最小删除功能**

在 `workflowApi` 增加删除请求；在设计器增加 `deleteLoading`、顶部危险按钮和 `deleteCurrentWorkflow()`，成功后删除布局缓存、重置新流程并刷新列表，失败时显示响应中的业务错误。

- [ ] **Step 4: 运行针对性测试并确认通过**

Run: `node --test tests/workflow-designer-structure.test.mjs`

- [ ] **Step 5: 执行完整验证**

Run: `node --test`

Run: `npm run build`

Run: `git diff --check`
