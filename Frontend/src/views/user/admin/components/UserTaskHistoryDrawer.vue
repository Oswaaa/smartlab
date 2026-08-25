<template>
  <el-drawer
    :model-value="modelValue"
    size="820px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="uh-drawer-header">
        <strong class="drawer-title">任务执行历史</strong>
        <span class="drawer-user-meta">用户：{{ userName }}</span>
      </div>
    </template>

    <div class="uh-drawer-body" v-loading="loading">
      <!-- 顶部过滤横幅 (38px 紧凑单行，1px 底边框，纯平铺) -->
      <div class="uh-filters-bar">
        <span class="uh-count-tag">共 {{ filtered.length }} 条执行记录</span>
        <div class="uh-filters-right">
          <el-input
            v-model="q"
            placeholder="搜索任务名称、说明或编号..."
            clearable
            size="small"
            class="uh-search-input"
          />
          <button class="btn-aliyun" type="button" @click="$emit('refresh')">
            刷新
          </button>
        </div>
      </div>

      <!-- 平铺历史表格 (唯一局部滚动，无图标) -->
      <div class="uh-table-wrapper">
        <el-table :data="filtered" height="100%" class="uh-table">
          <el-table-column label="任务编号" width="90" align="center">
            <template #default="{ row }">
              <span class="mono-id">#{{ row.taskId || row.id }}</span>
            </template>
          </el-table-column>

          <el-table-column label="任务名称" min-width="200">
            <template #default="{ row }">
              <div class="uh-name-cell">
                <strong class="task-title">{{ row.taskName || '未命名任务' }}</strong>
                <span class="task-desc">{{ row.taskDesc || '无详细描述' }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="流程模型" width="110" align="center">
            <template #default="{ row }">
              <span class="flow-pill">模型 #{{ row.flowModelId }}</span>
            </template>
          </el-table-column>

          <el-table-column label="运行状态" width="110" align="center">
            <template #default="{ row }">
              <span :class="['status-pill', statusPillClass(row.taskStatus)]">
                {{ row.taskStatus || '-' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="开始时间" width="160">
            <template #default="{ row }">
              <span class="mono-time">{{ ft(row.startTime) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="结束时间" width="160">
            <template #default="{ row }">
              <span class="mono-time">{{ ft(row.endTime) }}</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="compact-empty block-empty">
              <span>暂无关联的任务执行记录</span>
            </div>
          </template>
        </el-table>
      </div>
    </div>

    <template #footer>
      <div class="uh-drawer-footer">
        <button class="btn-aliyun" type="button" @click="$emit('update:modelValue', false)">
          关闭
        </button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  modelValue: boolean
  userName: string
  tasks: any[]
  loading: boolean
}>()

defineEmits<{
  'update:modelValue': [v: boolean]
  refresh: []
}>()

const q = ref('')

const filtered = computed(() => {
  const w = q.value.trim().toLowerCase()
  return w
    ? props.tasks.filter((t: any) => [t.taskName, t.taskDesc, t.taskId].join(' ').toLowerCase().includes(w))
    : props.tasks
})

function statusPillClass(s: string) {
  const v = (s || '').toUpperCase()
  if (v.includes('SUCCESS') || v.includes('COMPLETED')) return 'online'
  if (v.includes('RUNNING')) return 'busy'
  if (v.includes('FAIL') || v.includes('ERROR')) return 'offline'
  return 'offline'
}

function ft(v: any) {
  if (!v) return '-'
  try {
    return new Date(v).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return String(v)
  }
}
</script>

<style scoped>
/* ==========================================================================
   任务历史抽屉 - 平铺表格 · 无图标 · 对齐设备页面字阶与四级按钮规范
   ========================================================================== */
.uh-drawer-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.drawer-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.drawer-user-meta {
  font-size: 12px;
  color: #64748b;
}

.uh-drawer-body {
  height: calc(100vh - 110px);
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
  box-sizing: border-box;
}

.uh-filters-bar {
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

.uh-count-tag {
  font-size: 11.5px;
  color: #64748b;
}

.uh-filters-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.uh-search-input {
  width: 220px;
}

.uh-table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.uh-table {
  width: 100%;
}
.uh-table :deep(th) {
  height: 38px !important;
  background: #f8fafc !important;
  color: #64748b !important;
  font-size: 12px !important;
  font-weight: 600 !important;
  border-bottom: 1px solid #e2e8f0 !important;
}
.uh-table :deep(td) {
  height: 48px !important;
  padding: 4px 0 !important;
  border-bottom: 1px solid #f1f5f9 !important;
  font-size: 12px !important;
  color: #334155 !important;
}

.mono-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11.5px;
  color: #64748b;
}

.uh-name-cell {
  display: flex;
  flex-direction: column;
}

.task-title {
  font-size: 12.5px;
  font-weight: 700;
  color: #0f172a;
}

.task-desc {
  font-size: 11px;
  color: #64748b;
  margin-top: 1px;
}

.flow-pill {
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  padding: 2px 6px;
  border-radius: 3px;
}

.status-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
}
.status-pill.online { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }
.status-pill.busy { background: #fffbeb; color: #d97706; border: 1px solid #fde68a; }
.status-pill.offline { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }

.mono-time {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  color: #475569;
}

.compact-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  color: #94a3b8;
  font-size: 12px;
}

.uh-drawer-footer {
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid #e2e8f0;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: flex-end;
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
</style>