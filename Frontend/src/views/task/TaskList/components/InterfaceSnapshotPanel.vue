<template>
  <section class="snapshot-panel">
    <el-alert v-if="snapshotState.error" :title="snapshotState.error" type="error" :closable="false" show-icon />
    <div v-for="row in snapshotState.rows" :key="`${row.direction}:${row.interfaceName}`" class="snapshot-row">
      <div class="snapshot-head"><span class="dir-chip">{{ row.direction === 'IN' ? '输入' : '输出' }}</span><strong>{{ row.interfaceName }}</strong><span class="signal-name">{{ signalText(row) }}</span></div>
      <WorkflowJsonValue v-if="row.payload !== undefined" :value="row.payload" />
    </div>
    <el-empty v-if="!snapshotState.error && !snapshotState.rows.length" description="暂无接口快照" :image-size="36" />
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import WorkflowJsonValue from '../../components/WorkflowJsonValue.vue'
import { normalizeInterfaceSnapshot } from '../../../../utils/workflowExecution.js'
type Item = Record<string, any>
const props = defineProps<{ inputSnapshot?: unknown, outputSnapshot?: unknown }>()
const snapshotState = computed(() => {
  try {
    return { rows:[...normalizeInterfaceSnapshot(props.inputSnapshot, 'IN'), ...normalizeInterfaceSnapshot(props.outputSnapshot, 'OUT')], error:'' }
  } catch (error:any) { return { rows:[] as Item[], error:error.message || '接口快照格式错误' } }
})
function signalText(row: Item) { return row.signalName === null ? (row.direction === 'IN' ? '尚未收到' : '尚未发送') : row.signalName }
</script>
<style scoped>
.snapshot-panel{display:grid;gap:8px}
.snapshot-row{display:grid;gap:7px;padding:8px;border:1px solid var(--sl-border-base,#e2e8f0);border-radius:var(--sl-radius-sm,6px);background:var(--sl-bg-hover,#f8fafc)}
.snapshot-head{display:flex;align-items:center;gap:8px}
.dir-chip{flex-shrink:0;padding:1px 6px;border-radius:4px;background:#eff6ff;color:var(--sl-primary,#2563eb);font-size:11px;font-weight:600}
.snapshot-head strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;color:var(--sl-text-heading,#0f172a)}
.signal-name{margin-left:auto;color:var(--sl-text-secondary,#64748b);font-family:var(--sl-font-mono,ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace);font-size:11px}
</style>
