# SmartLab 2.0 全局 UI 设计系统规范 (Design System SSOT)

> **版本**：Final Release 1.0  
> **设计定位**：工业级、高密度、克制现代的生命科学智能实验室平台（Seamless Canvas + Graphite Slate + Cobalt Blue）  
> **核心原则**：一体化无界画卷（拒绝卡片孤岛与巨大空隙）、8pt 紧凑栅格、严谨排版字阶、严格四级按钮体系。

---

## 一、核心设计哲学与架构原则

1. **一体化无界画卷 (Seamless Integrated Canvas)**：
   * 彻底消除散落的卡片边框（Boxed Cards）与松散的巨大外边距；
   * 工作台内部通过 **`1px solid var(--border-base)`** 极微细线实现自上而下的自然分区（指标横幅 ➔ 时序走势图 ➔ 历史明细表），信息流动紧凑一体。
2. **8pt 紧凑高密度栅格 (Tight 8pt Spacing Grid)**：
   * 所有内外边距严格锁定在 `4px / 8px / 12px / 16px / 20px / 24px`，严禁出现破坏视觉节奏的随意数值。
3. **严格排版层级 (Typography Hierarchy)**：
   * 主标题高对比度（碳黑 `#0f172a`），正文舒适阅读（`#334155`），辅助说明中低对比（`#64748b`）；
   * 时间戳、遥测数值、阈值强制使用等宽字体（`ui-monospace`）。
4. **统一矢量图标（零 Emoji 原则）**：
   * 侧边栏及全系统禁止使用任何 Emoji 字符，统一采用 Element Plus 语义化矢量 SVG 图标（`Folder`、`Document`、`Cpu`、`Tickets` 等）。

---

## 二、全局 Design Tokens 变量定义

建议统一注入到 `Frontend/src/assets/styles/design-tokens.css` 或全局样式根节点：

```css
:root {
  /* ==================== 1. 色彩体系 (Colors) ==================== */
  /* 品牌核心主色 (实体活力钴蓝) */
  --sl-primary: #2563eb;
  --sl-primary-hover: #1d4ed8;
  --sl-primary-active: #1e40af;
  --sl-primary-light: #eff6ff;
  --sl-primary-border: #bfdbfe;

  /* 状态语义色 */
  --sl-success: #16a34a;
  --sl-success-light: #f0fdf4;
  --sl-success-border: #bbf7d0;

  --sl-danger: #dc2626;
  --sl-danger-light: #fef2f2;
  --sl-danger-border: #fecaca;

  --sl-warning: #d97706;
  --sl-warning-light: #fffbeb;
  --sl-warning-border: #fde68a;

  /* 文本灰阶 (Slate 现代冷灰系统) */
  --sl-text-heading: #0f172a;    /* 大标题 / 强调 (碳黑) */
  --sl-text-body: #334155;       /* 正文 / 表格 / 字段内容 */
  --sl-text-secondary: #64748b;  /* 辅助说明 / 表头 / 次要信息 */
  --sl-text-disabled: #94a3b8;   /* 禁用 / 占位提示 */

  /* 表面与背景 */
  --sl-bg-page: #f8fafc;         /* 系统页面底层背景 */
  --sl-bg-surface: #ffffff;      /* 一体化工作台纯白表面 */
  --sl-bg-hover: #f1f5f9;        /* 列表/表格悬浮高亮底色 */
  --sl-bg-active: #eff6ff;       /* 选中项浅蓝底色 */

  /* 边框体系 (超细微对比线) */
  --sl-border-base: #e2e8f0;     /* 核心分区线 (1px) */
  --sl-border-input: #cbd5e1;    /* 输入框 / 普通按钮边框 */
  --sl-border-subtle: #f1f5f9;   /* 极浅表格内部分割线 */

  /* ==================== 2. 圆角体系 (Radius) ==================== */
  --sl-radius-sm: 6px;           /* 按钮、输入框、下拉框、Tag 徽章 */
  --sl-radius-md: 8px;           /* 弹窗小区块、提示框、内联容器 */
  --sl-radius-lg: 10px;          /* 页面全局外层工作台大容器 */

  /* ==================== 3. 字体与排版 (Typography) ==================== */
  --sl-font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  --sl-font-mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;

  /* ==================== 4. 动效与投影 (Transitions & Shadows) ==================== */
  --sl-ease-smooth: all 0.18s cubic-bezier(0.4, 0, 0.2, 1);
  --sl-shadow-sm: 0 1px 2px 0 rgba(15, 23, 42, 0.04);
  --sl-shadow-container: 0 1px 3px 0 rgba(15, 23, 42, 0.04), 0 1px 2px -1px rgba(15, 23, 42, 0.03);
}
```

---

## 三、排版与字阶标准（Typography Ladder）

| 层级 | 字号 (Font Size) | 行高 (Line Height) | 字重 (Weight) | 适用场景 |
| :--- | :--- | :--- | :--- | :--- |
| **页面主标题 (H1)** | `22px` | `30px` | 700 (Bold) | 页面大标题、首屏概览 |
| **区块标题 (H2)** | `14.5px` | `22px` | 600 (Semibold) | 工具栏标题、弹窗小标题、表格标题 |
| **遥测核心指标** | `18px` | `24px` | 700 (Bold) | 指标栏大字、实时测量值 (加 Mono) |
| **正文字体 (Body)** | `13px` | `20px` | 400 (Regular) | 表单项、表格数据、对话框正文 |
| **次级与时间 (Meta)** | `12px` | `18px` | 400 (Regular) | 表头、时间戳 (加 Mono)、辅助说明文案 |
| **徽章标签 (Tag)** | `11.5px` | `16px` | 500 (Medium) | 状态胶囊、计数气泡 |

---

## 四、全系统统一按钮分级标准 (Button Hierarchy)

> **核心原则（1+N 黄金法则）**：
> 在任何一个视口或工具栏中，**实体蓝主操作按钮只允许出现 1 个**，让用户视线一眼聚焦。

```text
┌─────────────────────────┬──────────────────────────────────┬────────────────────────────────────────────────────────┐
│ 按钮层级与 Class        │ 视觉表现                         │ 适用场景 (什么时候用)                                  │
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 1. 实体蓝主按钮         │ 实体活力蓝底白字                 │ 当前区块/表单的【唯一核心终结性操作】                  │
│    (.btn-primary-blue)  │ background: #2563eb; color: 白   │ 具有明确数据产出或最终保存：[保存规则]、[导出 CSV]、[导出图表]│
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 2. 引导/新建 CTA 按钮   │ 白底蓝边蓝字 (悬浮变蓝)          │ 导航栏或树顶部的【新增/创建向导操作】                  │
│    (.btn-aliyun-cta)    │ border: 1px #2563eb; color: 蓝   │ 点击后开启弹窗/抽屉流程：[+ 新建规则]、[+ 为该设备建表]│
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 3. 次要工具按钮         │ 白底浅灰边黑字                   │ 【辅助性工具操作】                                     │
│    (.btn-aliyun)        │ border: 1px #cbd5e1; color: 墨   │ 非破坏性辅助调节：[刷新数据]、[最近30分钟]、[取消]     │
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 4. 行内操作链接         │ 纯文字无框 (蓝/红)               │ 【高密度表格行级操作】                                 │
│    (.btn-link / danger) │ 仅文字链接，悬浮下划线           │ 表格每行末尾：[查看详情]、[删除 (Danger 红字)]         │
└─────────────────────────┴──────────────────────────────────┴────────────────────────────────────────────────────────┘
```

### 按钮样式参考 CSS：
```css
/* 1. 实体蓝主按钮 */
.btn-primary-blue {
  background: var(--sl-primary);
  border: 1px solid var(--sl-primary);
  color: #ffffff;
  font-size: 12.5px;
  font-weight: 500;
  height: 28px;
  padding: 0 12px;
  border-radius: var(--sl-radius-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: var(--sl-ease-smooth);
}
.btn-primary-blue:hover {
  background: var(--sl-primary-hover);
  border-color: var(--sl-primary-hover);
  transform: translateY(-1px);
  box-shadow: 0 3px 6px rgba(37, 99, 235, 0.25);
}

/* 2. 白底蓝边 CTA */
.btn-aliyun-cta {
  background: #ffffff;
  border: 1px solid var(--sl-primary);
  color: var(--sl-primary);
  font-size: 12px;
  font-weight: 500;
  height: 26px;
  padding: 0 10px;
  border-radius: var(--sl-radius-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: var(--sl-ease-smooth);
}
.btn-aliyun-cta:hover {
  background: var(--sl-primary);
  color: #ffffff;
  transform: translateY(-1px);
}

/* 3. 白底灰边次要 */
.btn-aliyun {
  background: #ffffff;
  border: 1px solid var(--sl-border-input);
  color: var(--sl-text-heading);
  font-size: 12.5px;
  font-weight: 400;
  height: 28px;
  padding: 0 11px;
  border-radius: var(--sl-radius-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: var(--sl-ease-smooth);
}
.btn-aliyun:hover {
  border-color: var(--sl-primary);
  color: var(--sl-primary);
  background: #f8fafc;
  transform: translateY(-1px);
}

/* 4. 行内链接 */
.btn-link {
  background: transparent;
  border: none;
  color: var(--sl-primary);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  transition: var(--sl-ease-smooth);
}
.btn-link:hover { text-decoration: underline; color: var(--sl-primary-hover); }
.btn-link.danger { color: var(--sl-danger); }
.btn-link.danger:hover { color: #b91c1c; }
```

---

## 五、标准模块结构规范（Component Layouts）

### 1. 左侧资产导航树（Asset Tree Pane - 280px ~ 290px）
* **Header**：标题 `13.5px / 600` + 右侧 `[+ 建表 (CTA)]`；
* **Search Row**：内嵌 30px 高度紧凑搜索框，激活时带 1px 蓝框；
* **Tree Node**：
  * 内边距 `6px 8px`，圆角 `6px`，悬浮向右轻微位移 `translateX(2px)`；
  * 图标采用统一灰色系 SVG，激活项背景 `#eff6ff`、图标与文字变为 `#2563eb`；
  * 右侧带有浅灰等宽计数胶囊（`.t-badge`）。

### 2. 顶部指标横幅（Metric Ribbon）
* 高度紧凑，融于一体化画卷最顶端，底部带 `1px solid var(--sl-border-base)`；
* 4 列网格：标签 `11.5px / #64748b`，数值 `14px / #0f172a`（主指标 `18px / Mono / #2563eb`）。

### 3. 一体化时序图表与数据明细表
* **区块工具栏 (Section Toolbar)**：浅灰底色 `#f8fafc` + 标题 `13.5px` + 右侧操作组；
* **时序折线图**：直接嵌入，平滑贝塞尔曲线，带红色虚线阈值警戒线；
* **高频明细表**：全宽紧凑铺开，表头白底加底线，隔行浅灰悬浮，时间戳使用等宽字体。

### 4. 弹窗与抽屉（Dialog & Drawer - 固定视口与独立滚动）
* **最大高度**：锁死为 `90vh`，外层定位 `top: 5vh`，严禁整体弹窗随滚轮漂移；
* **标题栏与底部操作栏**：始终常驻（Sticky/Fixed Header & Footer）；
* **表单内容区**：内部容器设置 `overflow-y: auto; max-height: calc(90vh - 128px)`，搭配 6px 工业灰细滚动条。

---

## 六、全系统后续优化改造清单（Action Roadmap）

1. **全局样式层**：
   * 创建 `Frontend/src/assets/styles/design-tokens.css`，注入上述全部 CSS 变量与通用按钮/表格/徽章类。
2. **数据中心 (`DataManagement.vue`)**：
   * 彻底消除所有 Emoji 图标，对齐左侧树矢量图标；
   * 右侧改造为一体化无界画卷（消除外层浮动卡片）；
   * 统一工具栏“导出图表”与“导出 CSV”为实体蓝按钮。
3. **约束管理 (`SecurityCenter.vue` & `ConstraintRuleEditor.vue`)**：
   * 对齐指标横幅、时序走势图与数据审计表的间距与字阶；
   * 保持弹窗/抽屉的固定视口与独立内部平滑滚动。
4. **设备模型与实例管理 (`DeviceModelManagement.vue` / `DeviceInstanceManagement.vue`)**：
   * 统一列表表格的紧凑行高、状态徽标与操作链接体系。
5. **任务与流程设计 (`TaskList.vue` / `WorkflowDesigner.vue`)**：
   * 统一设计面板的顶部工具栏、状态胶囊与操作按钮层级。
