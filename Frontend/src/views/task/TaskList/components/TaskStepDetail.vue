<template>
  <div class="step-detail">
    <section class="detail-card">
      <header>
        <strong>节点信息</strong>
        <span>{{ node?.name || step?.nodeName || '未选择节点' }}</span>
        <button
          v-if="canEnterSubflow"
          class="btn-link"
          type="button"
          @click="emit('enter-subflow')"
        >查看子流程运行图</button>
      </header>
      <table class="detail-table">
        <tbody>
          <tr v-for="row in nodeInfoRows" :key="row.label">
            <th>{{ row.label }}</th>
            <td :class="{ mono: row.mono }">{{ row.value }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="detail-card">
      <header>
        <strong>接口快照</strong>
        <span>{{ interfaceCount }} 个接口</span>
      </header>
      <InterfaceSnapshotPanel
        v-if="step"
        :input-snapshot="step.interfaceInSnapshot"
        :output-snapshot="step.interfaceOutSnapshot"
        :node="node"
        :trigger-states="triggerStates"
      />
      <div v-else class="empty-hint">该节点尚未创建执行步骤，暂无接口快照</div>
    </section>

    <section class="detail-card">
      <header>
        <strong>变量空间</strong>
        <span>{{ variables.length }} 项</span>
      </header>
      <table v-if="variables.length" class="detail-table">
        <tbody>
          <tr v-for="[name, value] in variables" :key="name">
            <th>{{ name }}</th>
            <td><RuntimeStructuredValue :value="value" /></td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty-hint">{{ step ? '暂无用户变量' : '该节点尚未创建执行步骤' }}</div>
    </section>

    <section class="detail-card">
      <header>
        <strong>数据端口</strong>
        <span>{{ portCount }} 个端口</span>
      </header>
      <div v-if="portState.error" class="empty-hint">{{ portState.error }}</div>
      <template v-else>
        <h5 class="port-group-title">输入类</h5>
        <table v-if="portState.inputs.length" class="detail-table">
          <tbody>
            <tr v-for="row in portState.inputs" :key="`in-${row.portName}`">
              <th>{{ row.portName }}</th>
              <td><RuntimeStructuredValue :value="row.value" /></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-hint nested">暂无输入端口</div>
        <h5 class="port-group-title">输出类</h5>
        <table v-if="portState.outputs.length" class="detail-table">
          <tbody>
            <tr v-for="row in portState.outputs" :key="`out-${row.portName}`">
              <th>{{ row.portName }}</th>
              <td><RuntimeStructuredValue :value="row.value" /></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-hint nested">暂无输出端口</div>
      </template>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import InterfaceSnapshotPanel from './InterfaceSnapshotPanel.vue'
import RuntimeStructuredValue from './RuntimeStructuredValue.vue'
import { formatLogDateTime } from '../../../../utils/formatLogTime.js'
import { normalizeInterfaceSnapshot, normalizePortSnapshot, triggerStatesOf, visibleVariableEntries } from '../../../../utils/workflowExecution.js'
import { nodeStatusLabel } from '../taskExecutionPresentation.js'
type Item = Record<string, any>
const props = defineProps<{ step?: Item | null, node?: Item | null, canEnterSubflow?: boolean }>()
const emit = defineEmits<{ 'enter-subflow': [] }>()
const variables = computed(() => visibleVariableEntries(props.step?.variableSpace))
const triggerStates = computed(() => triggerStatesOf(props.step?.variableSpace))
const interfaceCount = computed(() => {
  if (!props.step) return 0
  try {
    return normalizeInterfaceSnapshot(props.step.interfaceInSnapshot, 'IN').length
      + normalizeInterfaceSnapshot(props.step.interfaceOutSnapshot, 'OUT').length
  } catch {
    return 0
  }
})
const portState = computed(() => {
  if (!props.step) return { inputs: [], outputs: [], error: '' }
  try {
    return {
      inputs: normalizePortSnapshot(props.step.portInSnapshot, 'IN'),
      outputs: normalizePortSnapshot(props.step.portOutSnapshot, 'OUT'),
      error: '',
    }
  } catch (error: any) {
    return { inputs: [], outputs: [], error: error.message || '端口快照格式错误' }
  }
})
const portCount = computed(() => portState.value.inputs.length + portState.value.outputs.length)
const nodeInfoRows = computed(() => {
  const node = props.node || {}
  const step = props.step
  const typeLabel = node.nodeType === 'DEV_NODE'
    ? '设备能力节点'
    : node.nodeType === 'SUBFLOW_NODE'
      ? '子流程节点'
      : node.functionType === 'START'
        ? '开始节点'
        : node.functionType === 'END'
          ? '结束节点'
          : node.functionType === 'BRANCH'
            ? '分支节点'
            : '功能节点'
  return [
    { label: '节点名称', value: node.name || step?.nodeName || '-' },
    { label: '节点类型', value: typeLabel },
    { label: '运行状态', value: step ? nodeStatusLabel(step.nodeStatus) : '等待创建' },
    { label: '开始时间', value: step ? formatLogDateTime(step.startTime) : '-', mono: true },
    { label: '结束时间', value: step ? formatLogDateTime(step.endTime) : '-', mono: true },
    { label: '耗时', value: step?.durationMs == null ? '-' : `${step.durationMs} ms`, mono: true },
    { label: '步骤深度', value: String(step?.stepDepth ?? node.depth ?? 0) },
    { label: '步骤编号', value: step?.id == null ? '尚未创建' : `#${step.id}`, mono: true },
  ]
})
</script>
<style scoped>
.step-detail {
  overflow: hidden;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  background: #fff;
  font-family: var(--sl-font-family);
  color: var(--sl-text-heading, #0f172a);
}
.detail-card {
  overflow: hidden;
  border: 0;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #fff;
}
.detail-card:last-child { border-bottom: 0; }
.detail-card > header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}
.detail-card > header strong { color: var(--sl-text-heading, #0f172a); font-size: 13px; font-weight: 600; }
.detail-card > header span { color: var(--sl-text-secondary, #64748b); font-size: 12px; margin-right: auto; }
.detail-table {
  width: 100%;
  border-collapse: collapse;
}
.detail-table th,
.detail-table td {
  padding: 8px 12px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  text-align: left;
  vertical-align: top;
  font-size: 12.5px;
}
.detail-table tr:last-child th,
.detail-table tr:last-child td { border-bottom: 0; }
.detail-table th {
  width: 108px;
  color: var(--sl-text-secondary, #64748b);
  font-weight: 600;
}
.detail-table td { color: var(--sl-text-heading, #0f172a); word-break: break-all; }
.mono { font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace); }
.port-group-title {
  margin: 0;
  padding: 6px 12px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  color: var(--sl-text-secondary, #64748b);
  font-size: 11.5px;
  font-weight: 600;
}
.port-group-title:not(:first-child) {
  border-top: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.empty-hint {
  padding: 12px;
  color: var(--sl-text-disabled, #94a3b8);
  font-size: 12.5px;
}
.empty-hint.nested { padding: 8px 12px; }
</style>
