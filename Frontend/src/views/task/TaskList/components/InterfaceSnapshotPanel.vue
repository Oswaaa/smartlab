<template>
  <section class="snapshot-panel">
    <el-alert v-if="snapshotState.error" :title="snapshotState.error" type="error" :closable="false" show-icon />
    <div v-for="row in snapshotState.rows" :key="`${row.direction}:${row.interfaceName}`" class="snapshot-row">
      <div class="snapshot-head"><el-tag size="small" :type="row.direction === 'IN' ? 'primary' : 'success'">{{ row.direction }}</el-tag><strong>{{ row.interfaceName }}</strong><span>{{ signalText(row) }}</span></div>
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
.snapshot-panel{display:grid;gap:8px}.snapshot-row{display:grid;gap:7px;padding:9px;border:1px solid #e2e7ed;background:#fff}.snapshot-head{display:flex;align-items:center;gap:8px}.snapshot-head strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.snapshot-head span{margin-left:auto;color:#425b78;font:10px Consolas,monospace}
</style>
