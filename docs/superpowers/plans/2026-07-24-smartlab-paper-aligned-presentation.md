# SmartLab 论文对齐 HTML 演示稿 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 输出一份 16:9、按论文小节组织的中文 HTML 组会演示稿，清晰展示模型逻辑、系统链路与跨层时序。

**Architecture:** 单文件 HTML 使用原生 CSS 与内嵌 SVG 图形。第 1 至 7 页为理论部分，第 8 至 10 页为系统实现与案例，第 11 页为结论；连接线先于节点绘制，以保证不穿越标签。

**Tech Stack:** HTML5、CSS3、原生 JavaScript、内嵌 SVG。

## Global Constraints

- 每个内容页在顶部显示对应论文小节号与标题。
- 所有主要图的 SVG 画布为 1600×900 的 16:9 比例。
- 不复写论文原文，不呈现公式、多元组或数据库字段。
- 仅新增 `output/html/smartlab-paper-aligned-group-meeting.html` 及其测试。

---

### Task 1: 写入失败的演示稿结构测试

**Files:**
- Create: `output/html/test-smartlab-paper-aligned-presentation.mjs`
- Test: `output/html/test-smartlab-paper-aligned-presentation.mjs`

**Interfaces:**
- Consumes: `output/html/smartlab-paper-aligned-group-meeting.html`。
- Produces: Node 退出码 0，验证 11 页、小节标题、三类图和导航。

- [ ] **Step 1: 创建验收测试**

```js
assert.equal((html.match(/class="slide/g) || []).length, 11);
assert.match(html, /模型逻辑图/);
assert.match(html, /系统链路图/);
assert.match(html, /跨层执行时序图/);
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `node output/html/test-smartlab-paper-aligned-presentation.mjs`
Expected: FAIL with missing HTML file.

### Task 2: 实现论文对齐的 11 页演示稿

**Files:**
- Create: `output/html/smartlab-paper-aligned-group-meeting.html`

**Interfaces:**
- Consumes: 论文第 1 至 5 节的论点及 Task 1 验收条件。
- Produces: 内嵌 SVG 图、`goTo(index)` 和键盘导航。

- [ ] **Step 1: 编写理论部分**

```html
<section class="slide" data-section="3.1 System Abstraction">...</section>
```

- [ ] **Step 2: 编写实现部分的链路图与时序图**

```html
<svg viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid meet">...</svg>
```

- [ ] **Step 3: 编写导航并运行测试**

Run: `node output/html/test-smartlab-paper-aligned-presentation.mjs`
Expected: PASS.

### Task 3: 进行静态质量检查

**Files:**
- Modify: `output/html/smartlab-paper-aligned-group-meeting.html`（仅在测试发现问题时）

**Interfaces:**
- Consumes: 成品 HTML。
- Produces: 无空白占位、无格式错误的离线演示稿。

- [ ] **Step 1: 执行格式与内容检查**

Run: `git diff --check -- output/html/smartlab-paper-aligned-group-meeting.html && node output/html/test-smartlab-paper-aligned-presentation.mjs`
Expected: exit 0.
