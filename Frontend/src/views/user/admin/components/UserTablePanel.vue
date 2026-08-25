<template>
  <section class="ut-shell">
    <!-- 1. 过滤与操作横幅 (38px 紧凑单行，1px 底边框，纯平铺，无重复新建按钮) -->
    <div class="ut-filters-bar">
      <div class="ut-filter-left">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名、实验室或说明..."
          clearable
          size="small"
          class="ut-search-input"
        />
        <el-select
          v-model="roleFilter"
          placeholder="全部角色"
          clearable
          size="small"
          class="ut-select-ctrl"
        >
          <el-option label="系统管理员" value="system_admin" />
          <el-option label="实验室管理员" value="lab_admin" />
          <el-option label="研究员" value="researcher" />
          <el-option label="观察员" value="observer" />
        </el-select>
        <el-select
          v-model="labFilter"
          placeholder="全部实验室"
          clearable
          size="small"
          class="ut-select-ctrl"
        >
          <el-option v-for="l in labs" :key="l" :label="l" :value="l" />
        </el-select>
        <button v-if="hasFilters" class="btn-link" type="button" @click="clearFilters">
          重置筛选
        </button>
      </div>

      <div class="ut-filter-right">
        <span class="ut-result-tag">共 {{ filtered.length }} 条记录</span>
        <button class="btn-aliyun" type="button" @click="$emit('refresh')">
          刷新
        </button>
      </div>
    </div>

    <!-- 2. 用户平铺表格 (自适应充满，唯一局部滚动，无图标干扰) -->
    <div class="ut-table-wrapper">
      <el-table
        :data="paged"
        v-loading="loading"
        class="ut-table"
        height="100%"
        @row-click="row => $emit('permissions', row)"
      >
        <el-table-column prop="id" label="ID" width="70" align="center">
          <template #default="{ row }">
            <span class="mono-id">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column label="用户账号" min-width="200">
          <template #default="{ row }">
            <div class="ut-user-cell">
              <strong class="ut-user-name">{{ row.userName }}</strong>
              <span class="ut-user-desc" :title="desc(row)">{{ desc(row) || '暂无说明' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="系统角色" width="140">
          <template #default="{ row }">
            <span :class="['role-pill', roleClass(role(row))]">
              {{ roleLabel(role(row)) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="归属实验室" width="180">
          <template #default="{ row }">
            <span class="ut-lab-text">{{ row.lab || '未分配实验室' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="权限等级" width="130" align="center">
          <template #default="{ row }">
            <span class="level-badge">L{{ lv(row) }} · {{ lvLabel(lv(row)) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="特权状态" width="100" align="center">
          <template #default="{ row }">
            <span :class="['priv-tag', { on: row.isPrivilegedUser }]">
              {{ row.isPrivilegedUser ? '已扩展' : '默认' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <div class="ut-row-actions" @click.stop>
              <button class="btn-link" type="button" @click.stop="$emit('permissions', row)">
                权限配置
              </button>
              <span class="action-divider">|</span>
              <button class="btn-link" type="button" @click.stop="$emit('edit', row)">
                编辑
              </button>
              <span class="action-divider">|</span>
              <button class="btn-link" type="button" @click.stop="$emit('command', 'history', row)">
                任务历史
              </button>
              <span class="action-divider">|</span>
              <button
                class="btn-link danger"
                type="button"
                :disabled="deletingId === row.id"
                @click.stop="$emit('command', 'delete', row)"
              >
                删除
              </button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="compact-empty block-empty">
            <span>没有找到匹配的用户账号</span>
          </div>
        </template>
      </el-table>
    </div>

    <!-- 3. 底部分页栏 (32px 紧凑标准) -->
    <div class="ut-pager-bar">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="filtered.length"
        :page-sizes="[15, 30, 50]"
        layout="total, sizes, prev, pager, next"
        background
        size="small"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{ users: any[]; loading: boolean; deletingId?: number | null }>()
defineEmits<{
  refresh: []
  edit: [u: any]
  permissions: [u: any]
  command: [cmd: string, u: any]
}>()

const keyword = ref('')
const roleFilter = ref('')
const labFilter = ref('')
const page = ref(1)
const size = ref(15)

watch([keyword, roleFilter, labFilter], () => { page.value = 1 })

const role = (u: any) => u?.roleName || u?.role || ''
const roleLabel = (r: string) => ({
  system_admin: '系统管理员',
  lab_admin: '实验室管理员',
  researcher: '研究员',
  observer: '观察员'
} as any)[r] || r

const roleClass = (r: string) => ({
  system_admin: 'r-admin',
  lab_admin: 'r-lab',
  researcher: 'r-researcher',
  observer: 'r-observer'
} as any)[r] || 'r-observer'

const desc = (u: any) => {
  const s = u?.userBasicInfo ?? u?.userBasicinfo
  if (!s) return ''
  if (typeof s === 'object') return s.description || ''
  try {
    return JSON.parse(s).description || ''
  } catch {
    return ''
  }
}

const lv = (u: any) => {
  const rl = ({ system_admin: 4, lab_admin: 3, researcher: 2, observer: 1 } as any)[role(u)] || 1
  return Number(u?.userLevel ?? rl)
}

const lvLabel = (v: number) => ({
  1: '基础访问',
  2: '操作执行',
  3: '实验室管理',
  4: '全局管理'
} as any)[v] || ''

const labs = computed(() => [...new Set(props.users.map((u: any) => u.lab).filter(Boolean))].sort())
const hasFilters = computed(() => !!(keyword.value || roleFilter.value || labFilter.value))

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return props.users.filter((u: any) =>
    (!roleFilter.value || role(u) === roleFilter.value) &&
    (!labFilter.value || u.lab === labFilter.value) &&
    (!q || [u.userName, u.lab, desc(u)].join(' ').toLowerCase().includes(q))
  )
})

const paged = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))

function clearFilters() {
  keyword.value = ''
  roleFilter.value = ''
  labFilter.value = ''
}
</script>

<style scoped>
/* ==========================================================================
   用户平铺表格工作台 - 杜绝图标 · 严禁重复按钮 · 对齐设备页面字阶
   ========================================================================== */
.ut-shell {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #ffffff;
  overflow: hidden;
}

/* 过滤横幅 (38px) */
.ut-filters-bar {
  height: 38px;
  padding: 0 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
}

.ut-filter-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ut-search-input {
  width: 240px;
}

.ut-select-ctrl {
  width: 150px;
}

.ut-filter-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ut-result-tag {
  font-size: 11.5px;
  color: #64748b;
}

/* 四级按钮体系标准 (对齐系统 UI 设计规范) */
.btn-aliyun {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  color: #334155;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-aliyun:hover {
  border-color: #94a3b8;
  background: #f8fafc;
  color: #0f172a;
}
.btn-aliyun:active {
  background: #f1f5f9;
}

.btn-link {
  font-size: 12px;
  font-weight: 500;
  color: #2563eb;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  transition: color 0.15s;
}
.btn-link:hover {
  color: #1d4ed8;
  text-decoration: underline;
}
.btn-link.danger {
  color: #dc2626;
}
.btn-link.danger:hover {
  color: #b91c1c;
}

/* 表格容器 */
.ut-table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.ut-table {
  width: 100%;
}
.ut-table :deep(th) {
  height: 38px !important;
  background: #f8fafc !important;
  color: #64748b !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  border-bottom: 1px solid #e2e8f0 !important;
}
.ut-table :deep(td) {
  height: 48px !important;
  padding: 4px 0 !important;
  border-bottom: 1px solid #f1f5f9 !important;
  font-size: 12.5px !important;
  color: #334155 !important;
}
.ut-table :deep(.el-table__row:hover > td) {
  background: #f8fafc !important;
}

.mono-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11.5px;
  color: #64748b;
}

.ut-user-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ut-user-name {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ut-user-desc {
  font-size: 11.5px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 1px;
}

/* 角色标签药丸 */
.role-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
}
.role-pill.r-admin { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
.role-pill.r-lab { background: #fffbeb; color: #92400e; border: 1px solid #fde68a; }
.role-pill.r-researcher { background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe; }
.role-pill.r-observer { background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1; }

.ut-lab-text {
  font-size: 12px;
  color: #334155;
}

.level-badge {
  font-size: 11.5px;
  font-weight: 600;
  color: #475569;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 3px;
}

.priv-tag {
  font-size: 11px;
  color: #94a3b8;
}
.priv-tag.on {
  color: #16a34a;
  font-weight: 600;
}

.ut-row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.action-divider {
  color: #e2e8f0;
  font-size: 11px;
}

/* 空状态规范 (纯文字无图标) */
.compact-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  color: #94a3b8;
  font-size: 12px;
}

/* 底部分页 (32px) */
.ut-pager-bar {
  height: 32px;
  padding: 0 16px;
  border-top: 1px solid #e2e8f0;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
}
</style>