<template>
  <el-dialog :model-value="modelValue" title="组件更换与维修历史" width="600px" append-to-body @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading" class="history-timeline-wrap">
      <el-timeline v-if="rows.length > 0">
        <el-timeline-item
          v-for="(row, idx) in rows"
          :key="idx"
          :timestamp="formatTime(row.installTime)"
          :type="row.status === 'IN_USE' ? 'primary' : 'info'"
        >
          <h4 class="timeline-title">插槽槽位: {{ row.componentName }}</h4>
          <div class="timeline-desc">
            <div>绑定实例: <b>{{ instanceNameById(row.selfInstanceId) }}</b></div>
            <div>更换状态: <el-tag size="small" :type="componentStatusType(row.status)" effect="plain">{{ row.status }}</el-tag></div>
            <div>备注: {{ row.remark || '无' }}</div>
            <div>规格: <button class="btn-link" type="button" @click="emit('show-spec', row)">查看详情</button></div>
            <div v-if="row.predecessorId">前置坏件组件 ID: <code class="mono">{{ row.predecessorId }}</code></div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无该槽位的更换历史记录" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { componentStatusType, formatTime } from './normalizers'
import type { DeviceInstance } from './types'

const props = defineProps<{
  modelValue: boolean
  loading: boolean
  rows: any[]
  instances: DeviceInstance[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'show-spec': [row: any]
}>()

const instanceNameById = (id: any) =>
  props.instances.find(item => String(item.instanceId) === String(id))?.instanceName || (id ? String(id) : '-')
</script>

<style scoped>
.history-timeline-wrap { padding: 8px 12px; max-height: 420px; overflow-y: auto; }
.timeline-title { margin: 0 0 4px; font-size: 13px; color: var(--sl-text-heading); }
.timeline-desc { font-size: 12px; color: var(--sl-text-secondary); line-height: 1.6; display: flex; flex-direction: column; gap: 2px; }
.mono { font-family: var(--sl-font-mono); }
</style>
