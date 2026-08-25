<template>
  <section class="lifecycle-panel system-lifecycle">
    <el-alert title="生命周期由系统模板维护，只读展示。" type="info" :closable="false" />
    <div class="lifecycle-section">
      <div class="section-head"><strong>生命周期</strong><el-tag size="small" type="info">系统</el-tag></div>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="初始状态">{{ lifecycle.initialStateName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态集合">{{ (lifecycle.states || []).join(', ') || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>
    <div v-if="lifecycle.transitions?.length" class="lifecycle-section">
      <div class="section-head"><strong>状态转移</strong><span>{{ lifecycle.transitions.length }} 条规则</span></div>
      <el-table :data="lifecycle.transitions" size="small" class="transitions-table">
        <el-table-column prop="fromStateName" label="源状态" width="110" />
        <el-table-column prop="toStateName" label="目标状态" width="110" />
        <el-table-column prop="description" label="说明" min-width="120">
          <template #default="{ row }">{{ row.description || '仅允许按该规则转移' }}</template>
        </el-table-column>
      </el-table>
    </div>
    <div v-if="!lifecycle.states?.length && !lifecycle.transitions?.length" class="empty-state">
      <span>此功能节点不使用节点生命周期</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Item = Record<string, any>

const props = defineProps<{ lifecycle?: Item | null }>()

const lifecycle = computed(() => props.lifecycle || { states: [], transitions: [] })
</script>

<style scoped>
.lifecycle-panel{display:grid;gap:12px;padding:0}.lifecycle-section{display:grid;gap:0;border:1px solid var(--sl-border-base, #e2e8f0);border-radius:var(--sl-radius-sm, 6px);overflow:hidden;background:#fff;box-shadow:0 1px 2px rgba(15,23,42,0.02)}.section-head{min-height:42px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-bottom:1px solid var(--sl-border-subtle, #f1f5f9);background:#fff}.section-head strong{color:var(--sl-text-heading, #0f172a);font-size:13px;font-weight:600}.section-head span{color:var(--sl-text-secondary, #64748b);font-size:11px}.lifecycle-section :deep(.el-descriptions){padding:12px 14px}.transitions-table :deep(th),.transitions-table :deep(td){font-size:12px}.empty-state{display:grid;place-items:center;padding:40px;color:var(--sl-text-secondary, #64748b);font-size:12px}
</style>
