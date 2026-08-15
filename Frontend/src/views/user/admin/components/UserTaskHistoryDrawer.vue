<template>
  <el-drawer :model-value="modelValue" title="任务记录" size="800px" destroy-on-close @update:model-value="$emit('update:modelValue', $event)">
    <template #header><div class="uh-head"><strong>任务记录</strong><span>{{ userName }}</span></div></template>
    <div class="uh-body" v-loading="loading">
      <div class="uh-bar">
        <span>共 {{ filtered.length }} 条</span>
        <div class="uh-bar-right">
          <el-input v-model="q" placeholder="搜索" clearable size="small" class="uh-bar-search" />
          <el-button size="small" :icon="Refresh" @click="$emit('refresh')" />
        </div>
      </div>
      <el-table :data="filtered" height="100%" size="small" class="uh-table">
        <el-table-column label="ID" width="72" align="center"><template #default="{ row }"><span class="uh-tid">#{{ row.taskId || row.id }}</span></template></el-table-column>
        <el-table-column label="任务名称" min-width="220"><template #default="{ row }"><strong>{{ row.taskName || '未命名' }}</strong><div class="uh-sub">{{ row.taskDesc }}</div></template></el-table-column>
        <el-table-column label="流程" width="100" align="center"><template #default="{ row }"><span class="uh-flow">#{{ row.flowModelId }}</span></template></el-table-column>
        <el-table-column label="状态" width="96" align="center"><template #default="{ row }"><el-tag size="small" :type="st(row.taskStatus)">{{ row.taskStatus || '-' }}</el-tag></template></el-table-column>
        <el-table-column label="开始" width="160"><template #default="{ row }">{{ ft(row.startTime) }}</template></el-table-column>
        <el-table-column label="结束" width="160"><template #default="{ row }">{{ ft(row.endTime) }}</template></el-table-column>
        <template #empty><el-empty description="暂无任务记录" :image-size="48" /></template>
      </el-table>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
const props = defineProps<{ modelValue: boolean; userName: string; tasks: any[]; loading: boolean }>()
defineEmits<{ 'update:modelValue': [v: boolean]; refresh: [] }>()
const q = ref('')
const filtered = computed(() => { const w = q.value.trim().toLowerCase(); return w ? props.tasks.filter((t: any) => [t.taskName, t.taskDesc, t.taskId].join(' ').toLowerCase().includes(w)) : props.tasks })
function st(s: string) { const v = (s || '').toUpperCase(); return v.includes('SUCCESS') || v.includes('COMPLETED') ? 'success' : v.includes('RUNNING') ? '' : v.includes('FAIL') ? 'danger' : 'info' }
function ft(v: any) { if (!v) return '-'; try { return new Date(v).toLocaleString('zh-CN', { hour12: false }) } catch { return String(v) } }
</script>

<style scoped>
.uh-head{display:grid;gap:2px}.uh-head strong{font-size:16px;color:#1a1d23}.uh-head span{color:#8b95a5;font-size:12px}
.uh-body{height:calc(100vh - 100px);display:flex;flex-direction:column;padding:0 24px}
.uh-bar{display:flex;align-items:center;justify-content:space-between;padding:12px 0;border-bottom:1px solid #edf0f3;color:#5f6b7a;font-size:12px;flex:none}
.uh-bar-right{display:flex;align-items:center;gap:8px}.uh-bar-search{width:200px}
.uh-table{flex:1;margin-top:4px}
.uh-tid{color:#a0a9b5;font-family:monospace;font-size:11px}
.uh-sub{color:#8b95a5;font-size:11px;margin-top:2px}
.uh-flow{padding:1px 6px;border-radius:4px;background:#eff6ff;color:#3b6fc8;font-size:11px}
</style>