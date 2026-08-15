# SmartLab 2.0 全局 UI 设计系统规范 (Design System SSOT)

> **版本**：Release 1.1 (数据中心实战终版)  
> **设计定位**：工业级、高密度、克制现代的生命科学智能实验室平台（Seamless Canvas + Graphite Slate + Cobalt Blue）  
> **核心原则**：一体化无界画卷（拒绝卡片孤岛与松散空隙）、视口绝对锁屏（唯一局部滚动）、严格 8pt 紧凑栅格、严谨排版字阶、严格四级按钮体系。

---

## 一、核心设计哲学与架构原则

1. **一体化无界画卷 (Seamless Integrated Canvas)**：
   * 彻底消除散落漂浮的卡片边框（Boxed Cards）与松散的外边距；
   * 工作台内部通过 **`1px solid var(--sl-border-base)`** 极微细线实现自上而下的自然分区（指标横幅 ➔ 时序走势图 ➔ 历史明细卡片），信息流动紧凑一体。
2. **视口绝对锁屏与唯一局部滚动体系 (Zero Page-Level Scroll & Isolated Table Scrolling)**：
   * 工作台主视口锁死为 `calc(100vh - 50px)`（`overflow: hidden`），严禁整个页面随鼠标滚轮上下晃动；
   * 顶部指标横幅、时序走势图、工具栏与底部分页器始终常驻锁定；
   * **系统唯一允许滚动的区域是明细表格内部容器**（`.table-scroll-container` 内的 `.table-card`），表格数据行自适应滚动，表头牢固吸顶。
3. **8pt 紧凑高密度栅格 (Tight 8pt Spacing Grid)**：
   * 所有内外边距严格锁定在 `4px / 6px / 8px / 12px / 16px / 20px / 24px`，严禁出现破坏视觉节奏的随意数值。
4. **严格排版层级与等宽数字 (Typography Ladder & Monospace)**：
   * 主标题高对比度（碳黑 `#0f172a`），正文舒适阅读（`#334155`），辅助说明中低对比（`#64748b`）；
   * 时间戳、采样数值、硬件寄存器、遥测指标强制使用等宽字体（`ui-monospace`）。
5. **统一语义化矢量图标（零 Emoji 原则）**：
   * 全系统严禁使用任何 Emoji 字符，统一采用 Element Plus 语义化矢量 SVG 图标（`Folder`、`Document`、`Cpu`、`Tickets` 等）。

---

## 二、全局 Design Tokens 变量定义

统一配置于 `Frontend/src/assets/styles/variables.css`，作为全平台样式的底层基石：

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
  --sl-bg-page: #f1f5f9;         /* 系统页面底层背景 */
  --sl-bg-surface: #ffffff;      /* 一体化工作台纯白表面 */
  --sl-bg-hover: #f8fafc;        /* 列表/表格悬浮高亮底色 */
  --sl-bg-active: #eff6ff;       /* 选中项浅蓝底色 */

  /* 边框体系 (超细微对比线) */
  --sl-border-base: #e2e8f0;     /* 核心分区线 (1px) */
  --sl-border-input: #cbd5e1;    /* 输入框 / 普通按钮边框 */
  --sl-border-subtle: #f1f5f9;   /* 极浅表格内部分割线 */

  /* ==================== 2. 圆角体系 (Radius) ==================== */
  --sl-radius-sm: 6px;           /* 按钮、输入框、下拉框、Tag 徽章、表格卡片 */
  --sl-radius-md: 8px;           /* 弹窗小区块、提示框、内联容器 */
  --sl-radius-lg: 10px;          /* 页面全局外层工作台大容器 */

  /* ==================== 3. 字体与排版 (Typography) ==================== */
  --sl-font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
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
| **区块工具栏标题 (H2)** | `13px` | `20px` | 600 (Semibold) | 走势图/数据明细/规则列表标题 |
| **横幅指标大字** | `14.5px` | `1.3` | 700 (Bold) | 指标栏大字、采样总量 (加 Mono + 主色) |
| **横幅普通指标** | `13px` | `1.3` | 600 (Semibold) | 设备模型、实例名称、数据模板名称 |
| **横幅属性标签** | `11px` | `1.2` | 400 (Regular) | 指标横幅小标签 (`#64748b`) |
| **正文字体 (Body)** | `12.5px ~ 13px` | `20px` | 400 (Regular) | 表单项、表格数据、对话框正文 |
| **次级与时间戳 (Meta)** | `12px` | `18px` | 400 (Regular) | 表头、时间戳 (加 Mono)、辅助说明文案 |
| **徽章标签 (Tag)** | `10.5px ~ 11.5px` | `1` | 500 (Medium) | 状态胶囊、计数气泡 |

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
│    (.btn-aliyun)        │ border: 1px #cbd5e1; color: 墨   │ 非破坏性辅助调节：[刷新数据]、[刷新采样]、[取消]       │
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 4. 行内操作链接         │ 纯文字无框 (蓝/红)               │ 【高密度表格行级操作】                                 │
│    (.btn-link / danger) │ 仅文字链接，悬浮下划线           │ 表格每行末尾：[查看详情]、[删除 (Danger 红字)]         │
└─────────────────────────┴──────────────────────────────────┴────────────────────────────────────────────────────────┘
```

---

## 五、标准核心业务组件规范（Authoritative Component Standards）

### 1. 侧边栏资产树规范（Asset Tree Pane - 270px）

侧边栏采用 **【现代极简无界流 (Modern Minimal)】**，实现 0 外边距紧密咬合与清晰的层级表达：

```text
┌──────────────────────────────────────────────────────────┐
│ 数据中心资产树                                    [+ 建表]│
│ 🔍 搜索类别、模型、设备或模板...                          │
├──────────────────────────────────────────────────────────┤
│ ┌─【设备类别】───────────────────────────┐ (微卡片标题栏) │
│ │ ▼ 📁 高压反应系统                      │ (文件夹图标)   │
│ │   ┆                                    │ (10px 虚线引导)│
│ │   ├─ ▼ 📄 反应釜主控模型               │ (文档图标)     │
│ │   │  ┆                                 │                │
│ │   │  └─ ▼ 🖥️ 1号反应釜                 │ (设备图标)     │
│ │   │     ┆                              │                │
│ │   │     └─ 📋 实时遥测采样表_01        │ (数据表图标)   │
│ └────────────────────────────────────────┘                │
│ ┌─【数据模板】───────────────────────────┐ (微卡片标题栏) │
│ │   📄 反应釜通用数据模板                │ (文档图标)     │
│ │   📄 温控探针专用采集模板              │ (文档图标)     │
└──────────────────────────────────────────────────────────┘
```

* **双一级独立分区**：
  * 大标题【设备类别】与【数据模板】采用 `#f1f5f9` 浅灰微卡片底色 + `1px solid #e2e8f0` 边框 + `padding: 3px 8px;`，分区明确；
* **下级树节点标准**：
  * **0 外边距**：`margin: 0` 严格连续紧密贴合，消除了原本碎裂的灰底色块；
  * **层级引导线**：子节点容器增加 `border-left: 1px dashed #e2e8f0; margin-left: 10px; padding-left: 2px;` 细虚线架构；
  * **节点高度与交互**：高度 `27px`，悬浮 `#f1f5f9`，选中项为柔和微圆角浅蓝底（`#eff6ff`）+ 活力蓝文字/图标（`#2563eb`）；
  * **语义化图标映射**：
    * 设备类别 ➔ `Folder`（📁 灰色系）
    * 设备模型 ➔ `Document`（📄 蓝色系）
    * 设备实例 ➔ `Cpu`（🖥️ 青色系）
    * 数据表 ➔ `Tickets`（📋 翠绿系）
    * 数据模板 ➔ `Document`（📄 紫罗兰系）

---

### 2. 顶部指标横幅规范（Metric Ribbon - 35px 像素级高度锁定）

指标横幅作为上下文元数据，**高度必须严格统一锁定在 35px**，严禁因内部徽章造成高度抖动：

* **栅格间距**：`padding: 5px 16px; border-bottom: 1px solid var(--sl-border-base); display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;`
* **单元格排版**：
  * 标签：`font-size: 11px; color: var(--sl-text-secondary); line-height: 1.2;`
  * 数值：`font-size: 13px; font-weight: 600; color: var(--sl-text-heading); line-height: 1.3;`
  * 核心突出数值：`font-size: 14.5px; color: var(--sl-primary); font-family: var(--sl-font-mono);`
* **内联轻量状态指示灯（`.m-val.status-val`）**：
  * **禁止在横幅内使用带外边距/外边框的独立胶囊（防止撑高横幅）**；
  * 必须使用内联小圆点与文本结构：
    ```css
    .m-val.status-val { display: inline-flex; align-items: center; gap: 5px; line-height: 1.3; font-size: 13px; font-weight: 600; }
    .m-val.status-val .dot { width: 6px; height: 6px; border-radius: 50%; }
    .m-val.status-val.in-use { color: var(--sl-success); }
    .m-val.status-val.in-use .dot { background: var(--sl-success); }
    .m-val.status-val.retired { color: var(--sl-text-secondary); }
    .m-val.status-val.retired .dot { background: var(--sl-text-secondary); }
    ```

---

### 3. 遥测趋势走势图规范（Waveform Chart - 满幅填满）

* **容器尺寸**：高度固定为 **`255px`**，宽度 100% 满幅自适应；
* **ECharts 贴边配置**：
  ```javascript
  grid: {
    left: 10,
    right: 16,
    top: 26,
    bottom: 8,
    containLabel: true  // 强制将坐标轴标签纳入计算，实现极致贴边
  }
  ```
* **生命周期防踩坑要求**：
  * 动态切换实体/表时，必须检测 `chart.getDom() !== chartRef.value`，及时 `dispose()` 并基于新挂载的 DOM 重新 `init()`，并在渲染完成后触发 `resize()`。

---

### 4. 一体化表格卡片规范（Table Card Container）

数据表格统一封装在微边距卡片容器内部，消除表头悬空感并提供独立纵向滚动：

* **外层滚动容器**：`padding: 6px 12px; flex: 1; min-height: 0; overflow: hidden;`
* **卡片框体（`.table-card`）**：
  ```css
  .table-card {
    flex: 1;
    min-height: 0;
    border: 1px solid var(--sl-border-base);
    border-radius: var(--sl-radius-sm);
    overflow: hidden;
    background: #ffffff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
    display: flex;
    flex-direction: column;
  }
  ```
* **内嵌表格（`el-table`）**：
  * 声明 `height="100%"`，表头自动吸附在卡片顶部；
  * `border: none !important;` 消除内层重复边框，与卡片外框融为一体；
* **底部分页器（`.pager-wrap`）**：
  * 常驻固定在卡片下方底边（`padding: 6px 14px; border-top: 1px solid var(--sl-border-base);`）。

---

### 5. 状态语义化标准文案对照表（Status Localization）

系统严格区分 **【资产生命周期】** 与 **【实时网络通信】** 两个独立维度，文案统一规范如下：

| 原枚举值 (Enum) | 规范中文呈现 (Standard Label) | 视觉指示灯色彩 | 含义说明 |
| :--- | :--- | :--- | :--- |
| **`IN_USE`** | **使用中 (在役)** | 翠绿点 (`#16a34a`) | 设备正常服役中，未被注销报废，允许调度建表 |
| **`RETIRED`** | **已注销 (退役)** | 石墨灰点 (`#64748b`) | 设备已报废或出库注销，禁止新增绑定 |
| **`ONLINE`** | **在线** | 翠绿胶囊 (`#16a34a`) | MQTT 适配器心跳正常通信中 |
| **`OFFLINE`** | **离线** | 石墨灰胶囊 (`#64748b`) | 物理设备关机断网，通信中断 |

---

### 6. 弹窗与抽屉标准（Dialog & Drawer - 固定视口与独立滚动）

* **最大高度**：弹窗外层 `top: 5vh; max-height: 90vh;`，严格防范随页面滚轮位移；
* **操作常驻**：标题栏（Header）与底部按钮栏（Footer）始终常驻锁定；
* **表单内容区**：设置 `overflow-y: auto; max-height: calc(90vh - 128px)`，搭配 6px 细滚动条。

---

## 六、全系统后续优化改造清单（Next Implementation Steps）

以本规范为绝对准绳，按顺序对剩余业务模块推进重构：

1. **约束管理中心 (`ConstraintManagement.vue` / `ConstraintRuleEditor.vue`)**：
   * 采用一体化无界画卷与指标横幅（35px 锁死）；
   * 告警规则审计表格统一采用 `.table-card` 封装与自适应滚动；
   * 规则编辑器弹窗/抽屉严格落地 90vh 锁屏。
2. **设备管理模块 (`DeviceModelManagement.vue` / `DeviceInstanceManagement.vue` / `AdapterManagement.vue`)**：
   * 左侧设备分类树对齐方案三极简无界规范（0 边距、虚线引导、语义化图标）；
   * 设备实例列表与组件列表统一采用微边距 `.table-card`；
   * 资产生命周期状态严格使用“使用中 (在役)”/“已注销 (退役)”。
3. **工作流与任务设计 (`TaskList.vue` / `WorkflowDesigner.vue`)**：
   * 任务看板与设计画布落地 100% 视口无界锁屏；
   * 工具栏严格执行“1 个实体蓝按钮 + N 个白底灰边工具按钮”标准。
4. **用户与权限中心 (`UserManagement.vue`)**：
   * 用户表格与角色权限抽屉统一对齐新四级按钮体系与 6px 细滚动条。
