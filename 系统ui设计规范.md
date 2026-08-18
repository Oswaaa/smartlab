# SmartLab 2.0 全局 UI 设计系统规范 (Design System SSOT)

> **版本**：Release 1.3 (设备调试一体化与全系统终极对齐版)  
> **设计定位**：工业级、高密度、克制现代的生命科学智能实验室平台（Seamless Canvas + Graphite Slate + Cobalt Blue）  
> **核心原则**：一体化无界画卷（拒绝卡片孤岛与松散空隙）、视口绝对锁屏（唯一局部滚动）、严格 8pt 紧凑栅格、严谨排版字阶、严格四级按钮体系、纯中文精简标题、契约字段标准唯一。

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
   * 时间戳、采样数值、硬件寄存器、遥测指标、信号标识强制使用等宽字体（`ui-monospace`）。
5. **纯中文精简标题与表头（Zero Redundant English Suffixes）**：
   * 标题、锚点导航与表头一律使用简洁纯粹的中文，严禁夹带中英双语括号后缀（如禁止使用“命令清单 (Commands)”，统一为“命令清单”）；
   * 用最直接、字数最克制的中文概括模块职责，消除视觉噪点。
6. **契约专有名词全局统一定义**：
   * 物理驱动与协议统一使用首字母大写的 **`Adapter`**，不再混用“适配器”；
   * 状态机转移统一使用 **`转移规则`** 与 **`状态转移`**（禁止混用“流转”）；
   * 触发机制统一使用 **`触发接口信号`**，清晰指明其结构为“接口 + 信号”。
7. **数据契约与标准字段零防御规范 (Clean Schema SSOT)**：
   * 严禁在模板和规范化函数中堆叠冗余的防御性多重回退代码（如禁止 `row.capabilityName || row.name || row.key`）；
   * 严格按照物模型标准契约字段（`displayName`、`capabilityName`、`portName`、`commandName`、`telemetryName` 等）进行唯一正确的直接访问。

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
| **触发器上行接口** | `11px` | `1.2` | 400 (Regular) | 触发接口行（浅灰色 `#64748b` + 等宽字体） |
| **触发器下行信号** | `11.5px` | `1.3` | 600 (Semibold) | 触发信号行（深色加粗 `#0f172a` + 等宽字体） |
| **徽章标签 (Tag)** | `10.5px ~ 11.5px` | `1` | 500 (Medium) | 状态胶囊、计数气泡、动作芯片 |

---

## 四、全系统统一按钮分级标准 (Button Hierarchy)

> **核心原则（1+N 黄金法则）**：
> 在任何一个视口或工具栏中，**实体蓝主操作按钮只允许出现 1 个**，让用户视线一眼聚焦。

```text
┌─────────────────────────┬──────────────────────────────────┬────────────────────────────────────────────────────────┐
│ 按钮层级与 Class        │ 视觉表现                         │ 适用场景 (什么时候用)                                  │
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 1. 实体蓝主按钮         │ 实体活力蓝底白字                 │ 当前区块/表单的【唯一核心终结性操作】                  │
│    (.btn-primary-blue)  │ background: #2563eb; color: 白   │ 具有明确数据产出或最终保存：[保存修改]、[导出 CSV]     │
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 2. 引导/新建 CTA 按钮   │ 白底蓝边蓝字 (悬浮变蓝)          │ 导航栏或树顶部的【新增/创建向导操作】                  │
│    (.btn-aliyun-cta)    │ border: 1px #2563eb; color: 蓝   │ 点击后开启弹窗/抽屉流程：[+ 新增分区]、[+ 新增规则]    │
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 3. 次要工具按钮         │ 白底浅灰边黑字                   │ 【辅助性工具操作】                                     │
│    (.btn-aliyun)        │ border: 1px #cbd5e1; color: 墨   │ 非破坏性辅助调节：[刷新预览]、[导出模型文件]、[取消]   │
├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────────────────────────┤
│ 4. 行内操作链接         │ 纯文字无框 (蓝/红)               │ 【高密度表格行级操作】                                 │
│    (.btn-link / danger) │ 仅文字链接，悬浮下划线           │ 表格每行末尾：[编辑]、[删除 (Danger 红字)]             │
└─────────────────────────┴──────────────────────────────────┴────────────────────────────────────────────────────────┘
```

---

## 五、标准核心业务组件规范（Authoritative Component Standards）

### 1. 侧边栏资产树规范（Asset Tree Pane - 270px）

侧边栏采用 **【现代极简无界流 (Modern Minimal)】**，实现 0 外边距紧密咬合与清晰的层级表达：
* **双一级独立分区**：【设备类别】与【数据模板】采用 `#f1f5f9` 浅灰微卡片底色 + `1px solid #e2e8f0` 边框；
* **层级引导线**：子节点容器增加 `border-left: 1px dashed #e2e8f0; margin-left: 10px; padding-left: 2px;` 细虚线架构；
* **节点高度与交互**：高度 `27px`，悬浮 `#f1f5f9`，选中项为柔和微圆角浅蓝底（`#eff6ff`）+ 活力蓝文字/图标（`#2563eb`）；
* **语义化图标映射**：类别 ➔ `Folder` / 模型 ➔ `Document` / 实例 ➔ `Cpu` / 数据表 ➔ `Tickets` / 模板 ➔ `Document`。

---

### 2. 顶部指标横幅规范（Metric Ribbon - 35px 像素级高度锁定）

* **栅格间距**：`padding: 5px 16px; border-bottom: 1px solid var(--sl-border-base); display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;`
* **单元格排版**：标签 11px 浅灰，数值 13px 加粗深色，核心数值 14.5px 蓝色等宽字体；
* **内联轻量状态指示灯（`.m-val.status-val`）**：禁止使用外框胶囊撑高横幅，统一使用 6px 小圆点 + 文字结构。

---

### 3. 一体化章节大卡片规范（Holistic Section Card - 复杂表单抽屉标准）

针对设备模型、复杂工作流等重型多步骤表单，采用**一体化章节大卡片**与**左侧锚点导航（148px）**结合：
* **卡片框体（`.holistic-section-card`）**：`border: 1px solid var(--sl-border-base); border-radius: var(--sl-radius-sm); margin-bottom: 14px; background: #ffffff;`
* **卡片头部（`.section-card-head`）**：左侧数字徽章（`.sec-idx-badge`） + 章节纯中文标题（`13.5px / 700`） + 浅灰辅助说明（`12px / 400`），右侧 CTA 按钮；
* **输入框规范**：卡片内输入框、下拉框高度严格统一为 `28px`（`padding: 1px 7px`）。

---

### 4. 端口与接口方向呈现规范 (Direction Standardization)

* **表头命名**：统一使用 **「方向」**（禁用“流向”）；
* **呈现内容**：统一解析为中文 **「输入」** / **「输出」**（禁用裸写英文 `IN` / `OUT`）；
* **端口字段**：契约仅包含 `portName`、`direction`、`bindingAttrName` 与 `description`，彻底消除虚构的 `portKey` 与 `portType`。

---

### 5. 属性映射与参数映射排版规范 (Mapping Standardization)

* **4.1 属性映射**：
  * 属性映射为绑定关系，**彻底移除流向列（→）**；
  * 表格精简保留两列：**「模型逻辑属性」** 与 **「Adapter 物理遥测属性」**。
* **4.2 功能映射与参数映射**：
  * 流向统一为 **「功能操作参数 ➔ Adapter 命令参数」**；
  * 左侧展示功能操作参数定义，右侧配置 Adapter 驱动指令参数绑定与固定值。

---

### 6. 状态机与状态转移规则体系规范 (State Machine & Transition SSOT)

* **命名统一**：
  * 模块名称：**「5.4 指令转移规则」**、**「5.5 业务状态转移规则」**；
  * 表头名称：**「状态转移」**、**「触发接口信号」**、**「过程转移动作」**。
* **触发接口信号（Trigger）双行等宽排版**：
  ```text
  ┌─────────────────────────┐
  │ Interface_adapter_in    │  <-- 上行：接口名称 (11px 浅灰色, 等宽字体)
  │ HEAT_START_EVENT        │  <-- 下行：信号/事件 (11.5px 加粗碳黑, 等宽字体)
  └─────────────────────────┘
  ```
* **过程转移动作规范（Action Flow）**：
  * 彻底移除转移规则中多余的“进入动作 (onEntry)”列；
  * 转移动作采用动词与目标分离的药丸芯片：`SEND [Signal] ➔ [Interface]`。
* **5.2 执行生命周期色彩与列精简**：
  * 生命周期状态气泡统一采用**中性灰胶囊（`.tag-gray`）**，保持克制与统一；
  * 彻底移除 CMD 空间归属列，降低冗余信息。
* **5.3 功能状态分区逻辑**：
  * 支持多个业务功能分区，全局至多允许 1 个“异常区域”；
  * 当无异常分区时，点击“新增分区”下拉支持“添加异常分区”；已存在时默认新增功能分区，按钮外观保持统一。

---

### 7. 模型文件工业级暗色代码框规范 (Dark Theme JSON Viewer)

针对 **「09 模型文件」**，展示态详情页与编辑态抽屉统一采用高对比度暗色工业风代码框：
* **容器分栏**：白底微边框卡片（`.json-panel`），包含独立的卡片标题栏；
* **代码视图（`pre`）**：
  * 背景：`background: #111827;`
  * 文字：`color: #e5e7eb;`
  * 字体：`var(--sl-font-mono)` 等宽字体，字号 `12px`，行高 `1.55`；
  * 高度与滚动：`max-height: 560px; overflow: auto; border-radius: 6px;`。

---

### 8. 状态语义化标准文案对照表（Status Localization）

系统严格区分 **【资产生命周期】** 与 **【实时网络通信】** 两个独立维度：

| 原枚举值 (Enum) | 规范中文呈现 (Standard Label) | 视觉指示灯色彩 | 含义说明 |
| :--- | :--- | :--- | :--- |
| **`IN_USE`** | **使用中 (在役)** | 翠绿点 (`#16a34a`) | 设备正常服役中，未被注销报废，允许调度建表 |
| **`RETIRED`** | **已注销 (退役)** | 石墨灰点 (`#64748b`) | 设备已报废或出库注销，禁止新增绑定 |
| **`ONLINE`** | **在线** | 翠绿胶囊 (`#16a34a`) | MQTT 适配器心跳正常通信中 |
| **`OFFLINE`** | **离线** | 石墨灰胶囊 (`#64748b`) | 物理设备关机断网，通信中断 |

---

### 9. 设备实例抽屉与控制调试一体化规范 (Device Instance Control Workbench)

针对设备实例详情抽屉（`DeviceInstanceManagement.vue`），统一确立以下工业级交互与排版准则：
* **抽屉尺寸全系统统一**：所有实例详情抽屉 (`drawerVisible`) 与新建向导抽屉 (`createDrawerVisible`) 严格锁定为 **`size="80%"`**，保证大屏与笔记本下的宽屏阅读舒适度；
* **一体化树状折叠单下拉（Tree-Select Accordion）**：
  * **单框一体化**：触发框保持标准 `32px` 高度，呈现纯净路径：`主设备 · 加热` 或 `进样机械臂 · 抓取`，体现设备是一个有机整体；
  * **右侧倒三角**：触发框右侧使用纯正倒三角指示符（`.custom-select-caret`），展开时平滑翻转 180°；
  * **折叠分组列表**：下拉面板内部支持按主设备与已挂载的拓扑组件（BOM）分层折叠与展开。分组左侧使用纯 CSS 绘制的小三角形指示符（展开 `▼`，折叠 `▶`）；
  * **模型名标注**：分组标题行右侧通过浅灰等宽徽章展示对应的物模型名称（`modelName`），方便识别组件归属物模型；
  * **纯净克制无 Emoji**：严格禁止在下拉选项、分组名称或触发框中滥用 Emoji 图标（如 👑、🧩），保持严肃工业风；
* **控制台报文中文呈现（`displayName` SSOT）**：
  * 通信终端下发指令与反馈日志必须统一提取物模型中的 **`displayName`** 中文显示名（例如：`向【主设备】下发指令: 加热 (点位: Sensor1)`），严禁直接裸写英文函数代码；
* **全高锁屏与生命周期卡片无滚动条规范**：
  * 控制调试工作台整体为 `flex: 1; height: 100%; min-height: 0; overflow: hidden;`；
  * 上方【下发指令】卡片内边距收紧为 `10px 12px`，下方【指令执行生命周期】卡片表格内边距为 `5px 8px`；
  * 下方卡片 5 行元数据（目标实体、当前动作、生命周期状态、下发点位、指令主题）一次性完整呈现，**严禁出现卡片内部滚动条**。

---

### 10. Adapter 驱动管理与通信契约规范 (Adapter Management Standards)

针对 Adapter 物理适配器管理（`AdapterManagement.vue`）：
* **系统 Broker 状态胶囊常驻**：在侧边栏顶部常驻全局 MQTT Broker 在线状态指示胶囊，实时呈现连接状态与重连操作；
* **表格标题栏垂直居中与顶格**：`.section-title-bar` 高度锁定为 `36px`，`align-items: center; justify-content: flex-start;`，实现上下绝对居中、左侧紧密顶格；
* **契约专有名词统一定义**：点位所属物理驱动一律统一为 **`(所属 Adapter: xxx)`**，禁止与物模型中的“驱动”或“适配器”混用。

---

### 11. 数据中心资产树与时序走势画卷规范 (Data Center Canvas Standards)

针对数据中心（`DataManagement.vue`）：
* **资产树双一级分区**：清晰划分【设备类别 ➔ 设备模型 ➔ 设备实例】与【数据模板 ➔ 数据表】两大主干；
* **时序图表与采样表格无界咬合**：
  * 顶部指标横幅（35px 像素级高度锁定）；
  * 遥测走势图（固定高度填满）+ 采样明细数据表格（自适应填满剩余空间，唯一局部滚动）；
  * 工具栏严格对齐四级按钮标准：1 个实体蓝导出主按钮 + 白底浅灰边工具按钮。

---

## 六、全系统优化达成与后续路线（Implementation Status & Roadmap）

| 模块名称 | 当前规范状态 | 落实的核心标准 |
| :--- | :--- | :--- |
| **设备模型管理 (`DeviceModelManagement.vue`)** | **100% 达成 (Release 1.2)** | 一体化章节卡片、状态机正交分区、Adapter Contract 只读化、暗色 JSON 视图 |
| **设备实例与控制调试 (`DeviceInstanceManagement.vue`)** | **100% 达成 (Release 1.3)** | 80% 统一抽屉、树状折叠单下拉、动态指令路由、displayName 终端日志、无滚动条卡片 |
| **Adapter 驱动管理 (`AdapterManagement.vue`)** | **100% 达成 (Release 1.3)** | Broker 状态胶囊常驻、标题栏 36px 垂直居中、点位契约只读化 |
| **数据中心工作台 (`DataManagement.vue`)** | **100% 达成 (Release 1.3)** | 双主干资产树、遥测走势画卷、唯一局部表格滚动、四级按钮标准 |
| **约束管理中心 (`ConstraintManagement.vue`)** | **待对齐 (Next)** | 对齐 35px 指标横幅与 90vh 规则抽屉四列等宽均分 |
| **任务工作流中心 (`TaskList.vue` / `WorkflowDesigner.vue`)** | **待对齐 (Next)** | 对齐 100% 视口无界锁屏与统一执行生命周期胶囊 |
| **用户与权限中心 (`UserManagement.vue`)** | **待对齐 (Next)** | 对齐新四级按钮体系与 6px 细滚动条 |
