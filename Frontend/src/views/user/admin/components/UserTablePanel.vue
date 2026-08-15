<template>
  <section class="ut-shell">
    <div class="ut-filters">
      <div class="ut-filter-group">
        <el-input v-model="keyword" placeholder="搜索用户名、实验室或说明" clearable class="ut-search" />
        <el-select v-model="roleFilter" placeholder="全部角色" clearable class="ut-role">
          <el-option label="系统管理员" value="system_admin" />
          <el-option label="实验室管理员" value="lab_admin" />
          <el-option label="研究员" value="researcher" />
          <el-option label="观察员" value="observer" />
        </el-select>
        <el-select v-model="labFilter" placeholder="全部实验室" clearable class="ut-lab">
          <el-option v-for="l in labs" :key="l" :label="l" :value="l" />
        </el-select>
        <el-button v-if="hasFilters" link @click="clearFilters">重置</el-button>
      </div>
      <div class="ut-actions">
        <span class="ut-result">{{ filtered.length }} 条结果</span>
        <el-button :icon="Refresh" size="small" @click="$emit('refresh')" />
        <el-button type="primary" size="small" @click="$emit('create')">新建</el-button>
      </div>
    </div>

    <el-table :data="paged" v-loading="loading" class="ut-table" height="100%" @row-click="row => $emit('permissions', row)">
      <el-table-column prop="id" label="ID" width="64" align="center">
        <template #default="{ row }"><span class="ut-id">{{ row.id }}</span></template>
      </el-table-column>
      <el-table-column label="用户" min-width="220">
        <template #default="{ row }">
          <div class="ut-user">
            <span class="ut-avatar" :style="{ background: color(row.userName) }">{{ (row.userName || '?')[0].toUpperCase() }}</span>
            <div class="ut-user-info">
              <strong>{{ row.userName }}</strong>
              <span>{{ desc(row) || '暂无说明' }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="140">
        <template #default="{ row }">
          <span class="ut-role-badge" :class="roleClass(role(row))">{{ roleLabel(role(row)) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="实验室" width="180">
        <template #default="{ row }">{{ row.lab || '—' }}</template>
      </el-table-column>
      <el-table-column label="等级" width="110" align="center">
        <template #default="{ row }"><span class="ut-level">L{{ lv(row) }} · {{ lvLabel(lv(row)) }}</span></template>
      </el-table-column>
      <el-table-column label="特权" width="90" align="center">
        <template #default="{ row }">
          <span class="ut-priv" :class="{ on: row.isPrivilegedUser }">{{ row.isPrivilegedUser ? '已扩展' : '默认' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <div class="ut-ops" @click.stop>
            <el-button link size="small" type="primary" @click.stop="$emit('permissions', row)">权限</el-button>
            <el-button link size="small" @click.stop="$emit('edit', row)">编辑</el-button>
            <el-dropdown trigger="click" @command="c => $emit('command', c, row)">
              <el-button link size="small">···</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="history">任务记录</el-dropdown-item>
                  <el-dropdown-item command="delete" :disabled="deletingId === row.id" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
      <template #empty><el-empty description="没有匹配的用户" :image-size="64" /></template>
    </el-table>

    <div class="ut-pager">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="filtered.length" :page-sizes="[15, 30, 50]" layout="total, sizes, prev, pager, next" background small />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

const props = defineProps<{ users: any[]; loading: boolean; deletingId?: number | null }>()
defineEmits<{ refresh: []; create: []; edit: [u: any]; permissions: [u: any]; command: [cmd: string, u: any] }>()

const keyword = ref(''); const roleFilter = ref(''); const labFilter = ref('')
const page = ref(1); const size = ref(15)
watch([keyword, roleFilter, labFilter], () => { page.value = 1 })

const role = (u: any) => u?.roleName || u?.role || ''
const roleLabel = (r: string) => ({ system_admin: '系统管理员', lab_admin: '实验室管理员', researcher: '研究员', observer: '观察员' } as any)[r] || r
const roleClass = (r: string) => ({ system_admin: 'r-red', lab_admin: 'r-amber', researcher: 'r-blue', observer: 'r-slate' } as any)[r] || 'r-slate'
const colors = ['#2563eb','#059669','#d97706','#7c3aed','#db2777','#0891b2']
const color = (n: string) => { let h = 0; for (let i = 0; i < (n || '').length; i++) h += n.charCodeAt(i); return colors[h % colors.length] }
const desc = (u: any) => { const s = u?.userBasicInfo ?? u?.userBasicinfo; if (!s) return ''; if (typeof s === 'object') return s.description || ''; try { return JSON.parse(s).description || '' } catch { return '' } }
const lv = (u: any) => { const rl = ({ system_admin: 4, lab_admin: 3, researcher: 2, observer: 1 } as any)[role(u)] || 1; return Number(u?.userLevel ?? rl) }
const lvLabel = (v: number) => ({ 1: '基础访问', 2: '研究操作', 3: '实验室管理', 4: '系统管理' } as any)[v] || ''

const labs = computed(() => [...new Set(props.users.map((u: any) => u.lab).filter(Boolean))].sort())
const hasFilters = computed(() => !!(keyword.value || roleFilter.value || labFilter.value))
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return props.users.filter((u: any) => (!roleFilter.value || role(u) === roleFilter.value) && (!labFilter.value || u.lab === labFilter.value) && (!q || [u.userName, u.lab, desc(u)].join(' ').toLowerCase().includes(q)))
})
const paged = computed(() => filtered.value.slice((page.value - 1) * size.value, page.value * size.value))
function clearFilters() { keyword.value = ''; roleFilter.value = ''; labFilter.value = '' }
</script>

<style scoped>
.ut-shell{display:flex;flex-direction:column;border:1px solid #e2e5e9;border-radius:8px;background:#fff;overflow:hidden}
.ut-filters{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid #edf0f3;background:#fafbfc}
.ut-filter-group{display:flex;align-items:center;gap:8px}
.ut-search{width:260px}
.ut-role,.ut-lab{width:150px}
.ut-actions{display:flex;align-items:center;gap:10px}
.ut-result{color:#8b95a5;font-size:12px}

.ut-table{flex:1}
.ut-table :deep(th){height:40px!important;background:#f7f8fa!important;color:#5f6b7a;font-size:11px;font-weight:600;text-transform:none;letter-spacing:0;border-bottom:1px solid #e8eaed}
.ut-table :deep(td){height:52px;padding:6px 0;border-bottom:1px solid #f0f1f3;font-size:13px;color:#3a3f4a}
.ut-table :deep(.el-table__row){cursor:pointer}
.ut-table :deep(.el-table__row:hover>td){background:#f5f8fc}
.ut-id{color:#a0a9b5;font-family:monospace;font-size:12px}
.ut-user{display:flex;align-items:center;gap:10px}
.ut-avatar{width:32px;height:32px;flex:none;display:grid;place-items:center;border-radius:50%;color:#fff;font-size:12px;font-weight:700}
.ut-user-info{display:grid;gap:1px;min-width:0}
.ut-user-info strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:600}
.ut-user-info span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#8b95a5;font-size:11px}
.ut-role-badge{display:inline-block;padding:2px 10px;border-radius:10px;font-size:11px;font-weight:600}
.r-red{background:#fef2f2;color:#b91c1c}
.r-amber{background:#fffbeb;color:#92400e}
.r-blue{background:#eff6ff;color:#1d4ed8}
.r-slate{background:#f1f5f9;color:#475569}
.ut-level{font-size:12px;color:#5f6b7a;font-weight:500}
.ut-priv{font-size:11px;color:#a0a9b5}
.ut-priv.on{color:#059669}
.ut-ops{display:flex;gap:4px}
.ut-pager{padding:10px 16px;border-top:1px solid #edf0f3;background:#fafbfc}
@media(max-width:860px){.ut-filters{flex-direction:column;align-items:stretch;gap:8px}.ut-filter-group{flex-wrap:wrap}.ut-search{flex:1;width:auto}}
</style>