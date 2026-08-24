<template>
  <section v-if="step" class="step-detail">
    <div class="detail-head">
      <div>
        <strong>{{ step.nodeName || `节点 ${step.nodeIdRef ?? '-'}` }}</strong>
        <span>层级 {{ step.stepDepth ?? 0 }}<template v-if="step.parentStepId != null"> · 父步骤 #{{ step.parentStepId }}</template></span>
      </div>
      <span :class="['status-indicator', statusTone(step.nodeStatus)]">
        <span class="dot"></span>{{ nodeStatusLabel(step.nodeStatus) }}
      </span>
    </div>
    <dl class="meta-grid">
      <div><dt>开始时间</dt><dd class="mono">{{ formatLogDateTime(step.startTime) }}</dd></div>
      <div><dt>结束时间</dt><dd class="mono">{{ formatLogDateTime(step.endTime) }}</dd></div>
      <div><dt>耗时</dt><dd class="mono">{{ step.durationMs == null ? '-' : `${step.durationMs} ms` }}</dd></div>
      <div><dt>步骤深度</dt><dd>{{ step.stepDepth ?? 0 }}</dd></div>
    </dl>
    <div class="detail-section">
      <h4>接口快照</h4>
      <InterfaceSnapshotPanel :input-snapshot="step.interfaceInSnapshot" :output-snapshot="step.interfaceOutSnapshot" />
    </div>
    <div class="detail-section">
      <h4>变量空间</h4>
      <div v-if="variables.length" class="variable-grid">
        <div v-for="[name, value] in variables" :key="name">
          <strong>{{ name }}</strong>
          <WorkflowJsonValue :value="value" />
        </div>
      </div>
      <div v-else class="empty-hint">暂无用户变量</div>
    </div>
    <div class="detail-section">
      <h4>数据端口</h4>
      <div class="port-grid">
        <div><span>输入</span><WorkflowJsonValue :value="step.portInSnapshot || {}" /></div>
        <div><span>输出</span><WorkflowJsonValue :value="step.portOutSnapshot || {}" /></div>
      </div>
    </div>
  </section>
  <el-empty v-else description="请选择一个已创建的执行步骤" :image-size="52" />
</template>
<script setup lang="ts">
import { computed } from 'vue'
import WorkflowJsonValue from '../../components/WorkflowJsonValue.vue'
import InterfaceSnapshotPanel from './InterfaceSnapshotPanel.vue'
import { formatLogDateTime } from '../../../../utils/formatLogTime.js'
import { visibleVariableEntries } from '../../../../utils/workflowExecution.js'
import { nodeStatusLabel } from '../taskExecutionPresentation.js'
type Item = Record<string, any>
const props = defineProps<{ step?: Item | null }>()
const variables = computed(() => visibleVariableEntries(props.step?.variableSpace))
function statusTone(status: string) {
  const value = String(status || '').toLowerCase()
  if (value === 'running') return 'running'
  if (value === 'succeeded') return 'success'
  if (value === 'failed') return 'danger'
  if (value === 'terminating' || value === 'terminated') return 'warning'
  return 'pending'
}
</script>
<style scoped>
.step-detail { display: grid; gap: 12px; }
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 0 0;
}
.detail-head > div { display: grid; gap: 2px; min-width: 0; }
.detail-head strong { font-size: 13px; font-weight: 700; color: var(--sl-text-heading, #0f172a); }
.detail-head > div span { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.status-indicator { display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; font-weight: 600; flex-shrink: 0; }
.status-indicator .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--sl-text-disabled, #94a3b8); }
.status-indicator.pending { color: var(--sl-text-secondary, #64748b); }
.status-indicator.running { color: var(--sl-primary, #2563eb); }
.status-indicator.running .dot { background: var(--sl-primary, #2563eb); box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2); }
.status-indicator.success { color: var(--sl-success, #16a34a); }
.status-indicator.success .dot { background: var(--sl-success, #16a34a); }
.status-indicator.danger { color: var(--sl-danger, #dc2626); }
.status-indicator.danger .dot { background: var(--sl-danger, #dc2626); }
.status-indicator.warning { color: var(--sl-warning, #d97706); }
.status-indicator.warning .dot { background: var(--sl-warning, #d97706); }
.meta-grid {
  margin: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  overflow: hidden;
}
.meta-grid > div {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border-right: 1px solid var(--sl-border-base, #e2e8f0);
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}
.meta-grid > div:nth-child(2n) { border-right: 0; }
.meta-grid dt { margin: 0; font-size: 11px; color: var(--sl-text-secondary, #64748b); }
.meta-grid dd { margin: 0; font-size: 12px; color: var(--sl-text-heading, #0f172a); }
.mono { font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace); }
.detail-section { display: grid; gap: 8px; }
.detail-section h4 { margin: 0; color: var(--sl-text-heading, #0f172a); font-size: 12px; font-weight: 600; }
.variable-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.variable-grid > div { display: grid; gap: 4px; }
.variable-grid strong, .port-grid span { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.port-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.port-grid > div { display: grid; gap: 4px; }
.empty-hint { color: var(--sl-text-disabled, #94a3b8); font-size: 12px; }
@media (max-width: 760px) {
  .variable-grid, .port-grid, .meta-grid { grid-template-columns: 1fr; }
  .meta-grid > div { border-right: 0; }
}
</style>
