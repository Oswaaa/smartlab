# SmartLab 组会 HTML 演示稿 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 生成一份能离线打开、适合十分钟组会的 SmartLab 框架 HTML 演示稿。

**Architecture:** 单个 HTML 文件内包含语义化幻灯片、内嵌 CSS 与导航脚本。内容以任务为轴，把论文的六类模型映射到仓库已有的前端、后端引擎与边缘适配器。

**Tech Stack:** HTML5、CSS3、原生 JavaScript、内嵌 SVG。

## Global Constraints

- 仅新增 `output/html/` 内的交付物和一个轻量静态测试脚本。
- 不依赖 CDN 或外部字体。
- 幻灯片为 16:9，支持键盘与点击导航。

---

### Task 1: 定义演示稿验收测试

**Files:**
- Create: `output/html/test-smartlab-presentation.mjs`
- Test: `output/html/test-smartlab-presentation.mjs`

**Interfaces:**
- Consumes: `output/html/smartlab-task-oriented-modeling-group-meeting.html`
- Produces: Node 退出码 0；检查标题、11 页幻灯片与导航挂钩。

- [ ] **Step 1: 写入失败的验收测试**

```js
assert.match(html, /class="slide/);
assert.equal((html.match(/class="slide/g) || []).length, 11);
assert.match(html, /addEventListener\('keydown'/);
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `node output/html/test-smartlab-presentation.mjs`
Expected: FAIL with missing HTML file.

### Task 2: 制作叙事与交互成品

**Files:**
- Create: `output/html/smartlab-task-oriented-modeling-group-meeting.html`

**Interfaces:**
- Consumes: 本计划中的 11 页叙事与 Task 1 验收条件。
- Produces: 独立 HTML 幻灯片，`slide` 类、`goTo()` 与键盘导航。

- [ ] **Step 1: 实现 11 页中文幻灯片与内嵌样式脚本**

```html
<section class="slide active">...</section>
<script>document.addEventListener('keydown', event => goTo(index + 1));</script>
```

- [ ] **Step 2: 运行验收测试**

Run: `node output/html/test-smartlab-presentation.mjs`
Expected: PASS: 11 slides and navigation hooks present.

### Task 3: 视觉验证

**Files:**
- Modify: `output/html/smartlab-task-oriented-modeling-group-meeting.html`（仅在发现溢出或可读性问题时）

**Interfaces:**
- Consumes: 成品 HTML。
- Produces: 16:9 全页截图，确认每页文字不溢出。

- [ ] **Step 1: 用浏览器或截图工具逐页检查**

Run: `Start-Process output/html/smartlab-task-oriented-modeling-group-meeting.html`
Expected: 每页可读，图形无重叠，导航有效。

- [ ] **Step 2: 重新运行验收测试**

Run: `node output/html/test-smartlab-presentation.mjs`
Expected: PASS.
