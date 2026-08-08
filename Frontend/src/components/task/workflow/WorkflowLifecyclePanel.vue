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
        <el-table-column prop="fromState" label="源状态" width="90" />
        <el-table-column label="触发器" width="80">
          <template #default="{ row }">{{ row.trigger || '自动' }}</template>
        </el-table-column>
        <el-table-column prop="toState" label="目标状态" width="90" />
        <el-table-column prop="description" label="说明" min-width="120">
          <template #default="{ row }">{{ row.description || row.trigger ? '接收到 ' + row.trigger + ' 后转移' : '无条件自动转移' }}</template>
        </el-table-column>
      </el-table>
    </div>
    <div v-if="!lifecycle.states?.length && !lifecycle.transitions?.length" class="empty-state">
      <span>该节点类型未定义生命周期</span>
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
.lifecycle-panel{display:grid;gap:10px;padding:12px}.lifecycle-section{display:grid;gap:8px}.section-head{display:flex;align-items:center;gap:8px}.section-head strong{font-size:12px}.section-head span{color:#8591a4;font-size:10px}.transitions-table :deep(th){font-size:10px}.transitions-table :deep(td){font-size:10px}.empty-state{display:grid;place-items:center;padding:24px;color:#8d98a8;font-size:11px}
</style>
