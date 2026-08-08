# 用户管理页面 UI 与权限交互重构方案

## 1. 文档目标

本文档用于指导 `Frontend/src/views/admin/UserManagement.vue` 的前端重构，实现以下目标：

- 将当前单文件页面拆分为职责清晰的页面级组件。
- 实现“用户表格 + 右侧权限抽屉”的最终合并设计。
- 将权限操作调整为“待添加 / 待撤销 / 统一保存”的暂存模型。
- 统一表格、按钮、弹窗、抽屉、状态标签和颜色规范。
- 保留现有后端接口，不修改数据库和后端业务模型。
- 为未来的账号停用、权限审计和服务端分页预留扩展位置。

本轮修改范围仅限前端。

---

## 2. 最终页面结构

### 2.1 桌面端总体布局

页面使用“主列表 + 右侧抽屉”结构：

- 用户列表占可视区域主体。
- 权限抽屉从右侧打开。
- 在 1360px 以上视口中，抽屉宽度建议控制在 `640px～760px`。
- 抽屉打开后仍保留足够的用户表格上下文。
- 低于 1100px 时，权限抽屉切换为全屏宽度。
- 低于 860px 时，权限双栏改为上下排列。

建议抽屉宽度：

```css
.permission-drawer {
  width: clamp(640px, 42vw, 760px) !important;
  max-width: 100vw;
}
```

### 2.2 左侧用户列表

左侧区域包含：

1. 顶部筛选工具栏。
2. 用户数据表格。
3. 底部分页栏。

工具栏字段：

- 用户名、实验室或账号说明搜索。
- 角色筛选。
- 实验室筛选。
- 重置筛选。
- 当前匹配数量。
- 刷新。
- 新建用户。

表格字段：

| 字段 | 展示方式 |
| --- | --- |
| ID | 短编号或原始用户 ID |
| 成员信息 | 头像、用户名、账号说明 |
| 角色权限 | 带颜色的角色标签 |
| 所属实验室 | 实验室图标和名称 |
| 访问等级 | `L1～L4` 与等级说明 |
| 特权状态 | “角色默认”或“已扩展” |
| 操作 | 编辑、权限、更多 |

“更多”菜单包含：

- 任务记录。
- 删除用户。

本轮不展示“正常 / 停用”等账号状态，因为当前后端没有账号启用状态字段。

### 2.3 权限抽屉上半部分

抽屉上半部分保持紧凑，包含：

1. 抽屉标题栏。
2. 用户摘要。
3. 未保存变更提示。

标题栏：

- 权限图标。
- 标题“权限与特权”。
- 副标题“用户名 · 管理角色继承与直接授权”。
- 关闭按钮。

用户摘要：

- 用户头像。
- 用户名。
- 角色标签。
- 账号说明。
- 所属实验室。
- 访问等级。
- 当前有效权限数量。

变更提示：

```text
权限配置有未保存更改
新增 N 项 · 撤销 N 项
撤销本次更改
```

### 2.4 权限抽屉下半部分

下半部分采用双栏权限矩阵：

```text
┌──────────────────────┬──────────────────────┐
│ 已拥有权限            │ 可分配权限            │
│ 角色默认 + 直接授权   │ 搜索 + 待添加选择     │
├──────────────────────┼──────────────────────┤
│ 设备与资源            │ 设备与资源            │
│ 数据资产              │ 数据资产              │
│ 流程与任务            │ 流程与任务            │
│ 安全与约束            │ 安全与约束            │
│ 系统与权限            │ 系统与权限            │
└──────────────────────┴──────────────────────┘
```

左栏“已拥有权限”：

- 按业务分类折叠。
- 角色继承权限显示锁图标。
- 角色继承权限显示“角色默认”标签。
- 直接授权显示“直接授权”标签。
- 直接授权提供“撤销”操作。
- 被角色默认权限覆盖的冗余直接授权显示“角色已覆盖”提示。

右栏“可分配权限”：

- 顶部提供权限搜索。
- 按业务分类折叠。
- 权限项使用复选框。
- 勾选后进入“待添加”状态。
- 勾选后权限项不立即从右栏消失，避免列表跳动。
- 支持批量勾选当前搜索结果。
- 批量操作超过一项时进行二次确认。

### 2.5 抽屉底部操作栏

底部操作栏固定显示：

- 左侧：权限变更说明。
- 右侧：关闭、撤销更改、保存权限。

按钮层级：

- “关闭”：普通按钮。
- “撤销更改”：次级按钮，仅有变更时显示。
- “保存权限”：主按钮，没有变更时禁用。

---

## 3. 组件拆分方案

### 3.1 目录结构

在 `views/admin` 下新增页面私有组件目录：

```text
Frontend/src/views/admin/
├─ UserManagement.vue
└─ components/
   ├─ UserTablePanel.vue
   ├─ PermissionDrawer.vue
   ├─ UserFormDialog.vue
   └─ UserTaskHistoryDrawer.vue
```

这些组件只服务于用户管理页面，因此不放入全局 `src/components`。

### 3.2 UserManagement.vue

职责：

- 页面入口和组件协调。
- 加载用户列表。
- 处理创建用户。
- 处理编辑用户。
- 处理删除用户。
- 控制权限抽屉和任务记录抽屉的当前用户。
- 接收权限更新事件并刷新用户特权状态。

建议保留的状态：

```ts
const users = ref<UserInfo[]>([])
const loadingUsers = ref(false)
const activeUser = ref<UserInfo | null>(null)

const formDialogVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')

const permissionDrawerVisible = ref(false)
const historyDrawerVisible = ref(false)
```

建议保留的接口调用：

```text
GET    /api/user/list
POST   /api/user/register
POST   /api/user/update
DELETE /api/user/delete/{id}
```

### 3.3 UserTablePanel.vue

职责：

- 搜索、角色筛选、实验室筛选。
- 客户端分页。
- 用户表格渲染。
- 用户操作菜单。
- 通过事件通知父组件执行具体业务操作。

Props：

```ts
interface Props {
  users: UserInfo[]
  loading: boolean
}
```

Emits：

```ts
defineEmits<{
  refresh: []
  create: []
  edit: [user: UserInfo]
  permissions: [user: UserInfo]
  history: [user: UserInfo]
  delete: [user: UserInfo]
}>()
```

内部状态：

```ts
const keyword = ref('')
const roleFilter = ref('')
const labFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
```

派生数据：

```ts
const filteredUsers = computed(...)
const pagedUsers = computed(...)
const availableLabs = computed(...)
```

筛选条件发生变化时，将页码重置为第一页。

### 3.4 PermissionDrawer.vue

职责：

- 加载权限字典。
- 加载用户直接授权 ID。
- 计算角色继承权限。
- 管理待添加与待撤销权限。
- 权限搜索和分类折叠。
- 保存前冲突检查。
- 未保存关闭拦截。
- 保存权限。

Props：

```ts
interface Props {
  modelValue: boolean
  user: UserInfo | null
}
```

Emits：

```ts
defineEmits<{
  'update:modelValue': [visible: boolean]
  updated: [payload: {
    userId: number
    permissionIds: number[]
  }]
}>()
```

内部调用接口：

```text
GET  /api/user/permissions/list
GET  /api/user/permissions/by-user/{id}
POST /api/user/permissions/assign
```

权限抽屉应独立处理权限相关接口，避免将大量权限状态重新提升到页面父组件。

### 3.5 UserFormDialog.vue

创建和编辑共用一个表单组件。

Props：

```ts
interface Props {
  modelValue: boolean
  mode: 'create' | 'edit'
  user?: UserInfo | null
  submitting?: boolean
}
```

Emits：

```ts
defineEmits<{
  'update:modelValue': [visible: boolean]
  submit: [payload: UserFormPayload]
}>()
```

创建模式字段：

- 用户名。
- 初始密码。
- 角色。
- 实验室。
- 账号说明。

编辑模式字段：

- 用户名。
- 角色。
- 实验室。
- 可选的新密码。
- 账号说明。

角色发生变化时显示警告：

```text
保存后角色默认权限会立即重新计算，原有直接授权记录仍然保留。
```

### 3.6 UserTaskHistoryDrawer.vue

职责：

- 加载当前用户任务记录。
- 搜索任务名称、ID、流程和状态。
- 展示任务状态、开始时间和结束时间。
- 支持刷新。

Props：

```ts
interface Props {
  modelValue: boolean
  user: UserInfo | null
}
```

接口：

```text
GET /api/user/task-history/{id}
```

---

## 4. 权限状态模型

### 4.1 后端返回数据含义

`GET /api/user/permissions/by-user/{id}` 返回用户的直接授权权限 ID，不包含角色继承权限。

角色继承权限由以下规则在前端计算：

```ts
permission.permissionLevel == null
  || currentUserLevel >= permission.permissionLevel
```

该逻辑与当前后端 `PermissionService#getUserPermissions` 保持一致。

### 4.2 前端暂存模型

权限抽屉使用以下三组核心状态：

```ts
const originalPermissionIds = ref<number[]>([])
const pendingAddIds = ref<number[]>([])
const pendingRemoveIds = ref<number[]>([])
```

含义：

- `originalPermissionIds`：打开抽屉时服务器返回的直接授权。
- `pendingAddIds`：本次操作准备新增的直接授权。
- `pendingRemoveIds`：本次操作准备撤销的直接授权。

### 4.3 最终提交权限

```ts
const finalPermissionIds = computed(() => {
  const removed = new Set(pendingRemoveIds.value)
  return [
    ...new Set([
      ...originalPermissionIds.value.filter(id => !removed.has(id)),
      ...pendingAddIds.value
    ])
  ].sort((a, b) => a - b)
})
```

保存时继续使用现有接口：

```ts
await axios.post('/api/user/permissions/assign', {
  userId: props.user.id,
  permissionIds: finalPermissionIds.value
})
```

后端不需要知道哪些权限是待添加或待撤销。

### 4.4 变更计数

```ts
const addedPermissionCount = computed(
  () => pendingAddIds.value.length
)

const removedPermissionCount = computed(
  () => pendingRemoveIds.value.length
)

const hasPermissionChanges = computed(
  () => addedPermissionCount.value > 0
    || removedPermissionCount.value > 0
)
```

### 4.5 权限操作规则

添加权限：

1. 从 `pendingRemoveIds` 中移除。
2. 如果原始权限中不存在，则加入 `pendingAddIds`。

撤销权限：

1. 如果属于本次待添加权限，则从 `pendingAddIds` 移除。
2. 如果属于服务器原始直接授权，则加入 `pendingRemoveIds`。

角色继承权限：

- 不允许撤销。
- 不进入 `pendingRemoveIds`。
- 显示锁图标。

冗余直接授权：

- 权限同时属于角色继承和直接授权时，显示“角色已覆盖”。
- 允许移除这条直接授权记录。
- 移除后不会影响用户当前实际权限。

### 4.6 保存前冲突检查

保存前重新请求：

```text
GET /api/user/permissions/by-user/{id}
```

将最新服务器 ID 与 `originalPermissionIds` 比较。

如果不一致：

- 阻止直接保存。
- 提示权限已被其他操作更新。
- 用户可以重新加载最新权限。
- 不静默覆盖服务器状态。

该检查属于前端的“尽力型并发保护”，可以降低覆盖风险，但不能替代后端原子化版本控制。

---

## 5. 权限分类与折叠

权限按对象映射到业务分类：

```ts
const permissionCategory = (object?: string) => {
  if (object === 'dashboard') return '概览'

  if ([
    'device_category',
    'device_model',
    'device_instance',
    'device_component',
    'adapter',
    'resource',
    'scene'
  ].includes(object || '')) {
    return '设备与资源'
  }

  if ([
    'data_template',
    'data_dataset',
    'property_type'
  ].includes(object || '')) {
    return '数据资产'
  }

  if (['workflow', 'task'].includes(object || '')) {
    return '流程与任务'
  }

  if ([
    'constraint_rule',
    'violation_log'
  ].includes(object || '')) {
    return '安全与约束'
  }

  if (['user', 'permission', 'all'].includes(object || '')) {
    return '系统与权限'
  }

  return '其他'
}
```

分类顺序：

```ts
const categoryOrder = [
  '概览',
  '设备与资源',
  '数据资产',
  '流程与任务',
  '安全与约束',
  '系统与权限',
  '其他'
]
```

左右两栏维护独立的展开状态：

```ts
const expandedOwnedCategories = ref<string[]>([])
const expandedAvailableCategories = ref<string[]>([])
```

默认展开第一个包含权限的分类。

---

## 6. 用户列表分页

当前 `/api/user/list` 返回全部用户，本轮使用客户端分页。

```ts
const pageSize = ref(20)
const currentPage = ref(1)

const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredUsers.value.slice(start, start + pageSize.value)
})
```

分页组件：

```vue
<el-pagination
  v-model:current-page="currentPage"
  v-model:page-size="pageSize"
  :total="filteredUsers.length"
  :page-sizes="[10, 20, 50]"
  layout="total, sizes, prev, pager, next, jumper"
/>
```

未来用户量显著增长时，再将分页迁移到后端。

---

## 7. 创建、编辑与删除流程

### 7.1 创建用户

流程：

1. 打开 `UserFormDialog` 创建模式。
2. 校验必填字段。
3. 调用 `/api/user/register`。
4. 成功后关闭弹窗并刷新列表。

### 7.2 编辑用户

流程：

1. 打开 `UserFormDialog` 编辑模式。
2. 回填用户信息。
3. 可选填写新密码。
4. 角色变化时显示权限影响提示。
5. 调用 `/api/user/update`。
6. 成功后关闭弹窗并刷新列表。

### 7.3 删除用户

删除前并行请求：

```text
GET /api/user/task-history/{id}
GET /api/user/permissions/by-user/{id}
```

确认框显示：

- 关联任务数量。
- 直接授权记录数量。
- 删除不可撤销提示。

确认后调用：

```text
DELETE /api/user/delete/{id}
```

如果影响检查失败，则不执行删除。

---

## 8. 未保存变更保护

以下操作必须触发未保存确认：

- 点击抽屉关闭按钮。
- 点击遮罩关闭。
- 按 `Esc` 关闭。
- 点击底部“关闭”按钮。
- 切换到其他用户。

提示内容：

```text
当前权限配置尚未保存，关闭后本次修改将全部丢失。
```

操作：

- “继续编辑”。
- “放弃更改”。

切换用户时：

1. 检查当前权限变更。
2. 用户确认放弃后再切换。
3. 未确认时保持当前用户和抽屉状态。

---

## 9. UI 设计规范

### 9.1 颜色

| 用途 | 色值 |
| --- | --- |
| 页面背景 | `#F5F7FA` |
| 主表面 | `#FFFFFF` |
| 次级表面 | `#FAFBFC` |
| 普通边框 | `#E5E7EB` |
| 强边框 | `#D1D5DB` |
| 主文字 | `#1D2129` |
| 次文字 | `#4E5969` |
| 弱文字 | `#86909C` |
| 主色 | `#1677FF` |
| 主色浅背景 | `#EAF3FF` |
| 成功 | `#00A870` |
| 警告 | `#D97706` |
| 危险 | `#D92D20` |

### 9.2 按钮

统一高度：

```css
min-height: 32px;
border-radius: 6px;
```

按钮层级：

- 主按钮：新建用户、保存权限。
- 次按钮：刷新、批量添加、保存资料。
- 文字按钮：编辑、权限、任务记录。
- 危险文字按钮：撤销直接授权。
- 危险实心按钮：永久删除确认。

约束：

- 同一操作区不超过一个主按钮。
- 红色只用于不可逆或撤销操作。
- 图标与文字间距统一为 6px。
- 表格文字按钮使用紧凑内边距。
- 不使用渐变按钮。

### 9.3 表格

- 表头高度约 42px。
- 行高约 56～60px。
- 表头背景 `#F7F8FA`。
- 行 Hover 背景 `#F3F8FF`。
- 固定操作列保持白色表面。
- 用户头像使用稳定哈希颜色。
- 角色标签使用浅背景和深色文字。

### 9.4 抽屉

- 标题栏高度约 64px。
- 内容区背景 `#F5F7FA`。
- 用户摘要使用白色表面和轻边框。
- 权限双栏使用白色表面和 1px 边框。
- 抽屉阴影保持克制：

```css
box-shadow: -8px 0 24px rgba(15, 23, 42, 0.12);
```

- 底部操作栏固定，不随权限列表滚动。
- 权限列表内部独立滚动。

### 9.5 权限行

权限行包含：

- 权限等级。
- 权限名称。
- 对象、动作和范围说明。
- 权限来源标签。
- 锁定、撤销或选择控件。

状态视觉：

| 状态 | 视觉 |
| --- | --- |
| 角色默认 | 蓝色浅标签 + 锁图标 |
| 直接授权 | 绿色浅标签 |
| 角色已覆盖 | 黄色浅标签 |
| 待添加 | 蓝色复选框 + 浅蓝背景 |
| 待撤销 | 红色文字 + 浅红背景 |

---

## 10. 响应式规则

### 大于 1360px

- 用户表格完整展示。
- 权限抽屉宽度约 42vw。
- 权限区域保持双栏。

### 1100px～1360px

- 抽屉宽度约 680～760px。
- 用户表格部分列允许隐藏或缩短。
- 权限区域保持双栏。

### 860px～1100px

- 权限抽屉全屏。
- 权限区域仍可双栏，但缩小辅助文字。

### 小于 860px

- 权限区域改为上下排列。
- 工具栏换行。
- 用户摘要统计改为两行。
- 表格允许横向滚动。

---

## 11. 无障碍与交互细节

- 所有图标按钮必须提供 `aria-label` 或 Tooltip。
- 权限分类折叠按钮支持键盘焦点。
- 可分配权限行支持点击整行切换复选框。
- 使用 `:focus-visible` 提供明确焦点环。
- 不仅依赖颜色表达状态，同时提供文字标签或图标。
- 危险操作必须有清晰动作名称。
- 保存期间禁用重复提交。
- 加载期间显示局部 Loading，不阻塞无关页面区域。

---

## 12. 本轮不涉及的后端能力

以下能力当前后端不存在，本轮不做前端假实现：

### 12.1 账号启用与停用

需要新增：

- 用户状态字段。
- 登录时状态校验。
- 启用、停用接口。

### 12.2 持久化权限审计

需要新增：

- 权限变更审计表。
- 操作人。
- 操作时间。
- 变更前后数据。
- 变更原因。
- 审计查询接口。

### 12.3 原子化并发控制

当前前端保存前重新读取权限只能降低覆盖风险。

真正的并发控制需要：

- 用户权限版本号。
- `expectedVersion`。
- 后端事务内比较版本并更新。

### 12.4 服务端分页

用户数量较大时需要新增：

```text
GET /api/user/page?pageNo=1&pageSize=20&keyword=...
```

---

## 13. 推荐实施顺序

### 阶段一：类型和基础工具

1. 定义 `UserInfo`、`PermissionInfo` 和表单类型。
2. 提取角色、权限等级、对象和动作名称映射。
3. 保持现有 API 行为不变。

### 阶段二：拆分用户列表

1. 新建 `components/UserTablePanel.vue`。
2. 迁移筛选逻辑。
3. 增加客户端分页。
4. 迁移表格和操作菜单。

### 阶段三：拆分用户表单

1. 新建 `components/UserFormDialog.vue`。
2. 合并创建和编辑表单。
3. 保留角色变化提示。

### 阶段四：重构权限状态模型

1. 新建 `components/PermissionDrawer.vue`。
2. 引入 `originalPermissionIds`。
3. 引入 `pendingAddIds`。
4. 引入 `pendingRemoveIds`。
5. 实现最终权限计算。
6. 实现未保存关闭保护。
7. 实现保存前冲突检查。

### 阶段五：实现权限矩阵

1. 按分类生成已拥有权限。
2. 按分类生成可分配权限。
3. 增加独立折叠状态。
4. 增加权限搜索。
5. 增加批量选择。
6. 增加待添加和待撤销状态。

### 阶段六：拆分任务记录

1. 新建 `components/UserTaskHistoryDrawer.vue`。
2. 迁移任务记录加载和搜索。

### 阶段七：视觉统一

1. 对照最终合并效果图调整布局。
2. 统一按钮高度、类型和间距。
3. 统一抽屉标题栏、内容区和底部栏。
4. 统一表格、标签、折叠栏和权限行颜色。
5. 完成响应式处理。

### 阶段八：验证

1. 执行前端生产构建。
2. 检查 Vue 模板和 TypeScript。
3. 检查未使用变量和旧样式残留。
4. 在非 5173 端口启动预览。
5. 使用已登录会话验证真实权限数据。

---

## 14. 验收清单

### 用户列表

- [ ] 搜索、角色和实验室筛选正常。
- [ ] 重置筛选正常。
- [ ] 分页正常。
- [ ] 筛选变化后自动回到第一页。
- [ ] 创建用户成功后刷新列表。
- [ ] 编辑用户成功后刷新列表。
- [ ] 删除前显示任务和直接授权数量。

### 权限抽屉

- [ ] 抽屉用户信息正确。
- [ ] 角色默认权限计算与后端一致。
- [ ] 直接授权正确显示。
- [ ] 冗余直接授权正确标记。
- [ ] 权限分类折叠正常。
- [ ] 权限搜索正常。
- [ ] 待添加状态正常。
- [ ] 待撤销状态正常。
- [ ] 批量添加有二次确认。
- [ ] 关闭抽屉时拦截未保存更改。
- [ ] 切换用户时拦截未保存更改。
- [ ] 保存前能够发现服务器权限变化。
- [ ] 保存成功后状态清零并更新列表特权标记。

### UI

- [ ] 页面符合最终合并设计图。
- [ ] 用户表格和权限抽屉比例合理。
- [ ] 按钮高度、颜色和间距统一。
- [ ] 抽屉上下区域层级清晰。
- [ ] 双栏权限矩阵滚动互不干扰。
- [ ] 低分辨率下无文字遮挡。
- [ ] 键盘焦点清晰。
- [ ] 危险操作只使用红色。

### 工程

- [ ] `npm run build` 通过。
- [ ] `git diff --check` 通过。
- [ ] 不修改现有后端文件。
- [ ] 不覆盖 `TaskList.vue` 等无关改动。
- [ ] 不占用 5173 端口。

---

## 15. 完成后的预期结果

完成后，`UserManagement.vue` 仅承担页面级协调职责，主要 UI 和交互分别位于页面私有组件中。

最终用户管理流程为：

```text
筛选用户
  → 选择用户
  → 打开权限抽屉
  → 查看角色默认与直接授权
  → 勾选待添加或标记待撤销
  → 查看变更摘要
  → 保存前冲突检查
  → 提交完整直接授权 ID
  → 刷新用户特权状态
```

该方案能在不修改后端的前提下，实现最终效果图中的主要布局、权限管理体验和业务安全控制，同时为后续账号停用、审计日志和原子化权限版本控制保留清晰的扩展边界。
