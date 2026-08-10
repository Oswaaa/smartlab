<template>
  <div class="canvas-node" :class="[{ selected, warning: issues.length }, meta.className]">
    <div class="handle-layer input-interfaces">
      <div v-for="(item, index) in workflowInputs" :key="item.name" class="side-handle" :style="sideHandleStyle(index, workflowInputs.length)">
        <Handle :id="interfaceHandleId(item.name)" type="target" :position="Position.Left" class="workflow-handle interface-handle" />
        <span class="handle-label" :title="interfaceLabel(item)">{{ interfaceLabel(item) }}</span>
      </div>
    </div>
    <div class="handle-layer output-ports">
      <div v-for="(item, index) in outputPorts" :key="item.name" class="horizontal-handle" :style="horizontalHandleStyle(index, outputPorts.length)">
        <Handle :id="portHandleId(item.name)" type="source" :position="Position.Top" class="workflow-handle port-handle" />
        <span class="handle-label" :title="item.name">{{ item.name }}</span>
      </div>
    </div>
    <div class="node-accent"></div>
    <div class="node-main">
      <div class="node-topline">
        <span class="node-glyph">{{ meta.glyph }}</span>
        <span class="node-kind">{{ meta.label }}</span>
        <span class="node-title-sep">·</span>
        <strong class="node-name" :title="node?.name">{{ node?.name || '未命名节点' }}</strong>
        <span v-if="issues.length" class="warning-dot" :title="issues[0]?.message || '节点配置未完成'">!</span>
      </div>
      <div class="node-body">
        <span class="node-summary" :title="summary">{{ summary }}</span>
      </div>
    </div>
    <div class="handle-layer output-interfaces">
      <div v-for="(item, index) in workflowOutputs" :key="item.name" class="side-handle" :style="sideHandleStyle(index, workflowOutputs.length)">
        <span class="handle-label" :title="interfaceLabel(item)">{{ interfaceLabel(item) }}</span>
        <Handle :id="interfaceHandleId(item.name)" type="source" :position="Position.Right" class="workflow-handle interface-handle" />
      </div>
    </div>
    <div class="handle-layer input-ports">
      <div v-for="(item, index) in inputPorts" :key="item.name" class="horizontal-handle" :style="horizontalHandleStyle(index, inputPorts.length)">
        <span class="handle-label" :title="item.name">{{ item.name }}</span>
        <Handle :id="portHandleId(item.name)" type="target" :position="Position.Bottom" class="workflow-handle port-handle" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { interfaceHandleId, portHandleId } from '../../../utils/workflowCanvas.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node?: Item | null, selected?: boolean, issues?: Item[] }>(), { node: null, selected: false, issues: () => [] })
const workflowInputs = computed(() => (props.node?.interfaces || []).filter((item: Item) => item.interfaceType === 'WORKFLOW' && item.direction === 'IN'))
const workflowOutputs = computed(() => (props.node?.interfaces || []).filter((item: Item) => item.interfaceType === 'WORKFLOW' && item.direction === 'OUT'))
const inputPorts = computed(() => (props.node?.ports || []).filter((item: Item) => item.direction === 'IN'))
const outputPorts = computed(() => (props.node?.ports || []).filter((item: Item) => item.direction === 'OUT'))
const meta = computed(() => {
  if (props.node?.nodeType === 'DEV_NODE') return { className: 'device', label: '设备能力', glyph: 'D' }
  if (props.node?.nodeType === 'SUBFLOW_NODE') return { className: 'subflow', label: '子流程', glyph: '↳' }
  if (props.node?.functionType === 'START') return { className: 'start', label: '开始节点', glyph: '▶' }
  if (props.node?.functionType === 'END') return { className: 'end', label: '结束节点', glyph: '■' }
  if (props.node?.functionType === 'BRANCH') return { className: 'branch', label: '分支节点', glyph: '◇' }
  return { className: 'aggregate', label: '汇聚节点', glyph: '◆' }
})
const summary = computed(() => {
  if (props.node?.nodeType === 'DEV_NODE') return props.node.capability?.capabilityName || '请选择执行能力'
  if (props.node?.nodeType === 'SUBFLOW_NODE') return props.node.subFlowModelDescription || `流程模型 ${props.node.subFlowModelId || '-'}`
  if (props.node?.functionType === 'BRANCH') return props.node.expression || '未配置分支表达式'
  if (props.node?.functionType === 'START') return '发出ACTIVE信号'
  if (props.node?.functionType === 'END') return '接收流程结束信号'
  return '汇聚上游执行路径'
})
function interfaceLabel(item: Item) {
  if (props.node?.functionType === 'BRANCH' && item.direction === 'OUT') {
    if (item.name === 'Interface_true_out') return 'true'
    if (item.name === 'Interface_false_out') return 'false'
  }
  return item.name
}
function sideHandleStyle(index: number, total: number) { return { top: `${((index + 1) * 100) / (total + 1)}%` } }
function horizontalHandleStyle(index: number, total: number) { return { left: `${((index + 1) * 100) / (total + 1)}%` } }
</script>

<style scoped>
.canvas-node{position:relative;width:226px;min-height:92px;display:flex;border:1px solid #cbd5e1;border-radius:8px;background:#fff;box-shadow:0 2px 10px rgba(15,23,42,.06);overflow:visible;transition:all .15s ease}
.canvas-node:hover{border-color:#3b82f6;box-shadow:0 4px 14px rgba(37,99,235,.12)}
.canvas-node.selected{border-color:#2563eb;box-shadow:0 0 0 3px rgba(37,99,235,.16),0 4px 14px rgba(37,99,235,.12)}
.canvas-node.warning{border-color:#f59e0b}
.node-accent{width:4px;flex:none;border-radius:7px 0 0 7px;background:#64748b}
.start .node-accent{background:#10b981}
.end .node-accent{background:#64748b}
.branch .node-accent{background:#f59e0b}
.aggregate .node-accent{background:#8b5cf6}
.subflow .node-accent{background:#06b6d4}
.device .node-accent{background:#2563eb}
.node-main{min-width:0;display:flex;flex:1;flex-direction:column;padding:10px 12px}
.node-topline{display:flex;align-items:center;gap:5px;min-width:0;margin-bottom:6px}
.node-glyph{width:18px;height:18px;display:grid;place-items:center;flex:none;border-radius:4px;background:#eff6ff;color:#2563eb;font-size:9px;font-weight:800}
.start .node-glyph{background:#ecfdf5;color:#059669}
.end .node-glyph{background:#f1f5f9;color:#475569}
.branch .node-glyph{background:#fffbe6;color:#d97706}
.aggregate .node-glyph{background:#f5f3ff;color:#7c3aed}
.subflow .node-glyph{background:#ecfeff;color:#0891b2}
.node-kind{color:#64748b;font-size:11px;font-weight:600;flex:none}
.node-title-sep{color:#cbd5e1;font-size:11px;flex:none}
.node-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#0f172a;font-size:12px;font-weight:700;flex:1;min-width:0}
.warning-dot{width:15px;height:15px;display:grid;place-items:center;flex:none;margin-left:auto;border-radius:50%;background:#fef3c7;color:#b45309;font-size:10px;font-weight:800}
.node-body{display:flex;flex-direction:column;min-width:0;padding:2px 0}
.node-summary{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#475569;font-size:11px}
.handle-layer{position:absolute;inset:0;pointer-events:none}
.side-handle,.horizontal-handle{position:absolute;display:flex;align-items:center;color:#94a3b8;font-size:9px;line-height:1;white-space:nowrap;pointer-events:none}
.side-handle{transform:translateY(-50%)}
.input-interfaces .side-handle{left:0}
.output-interfaces .side-handle{right:0}
.handle-label{max-width:70px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#94a3b8;font-size:9px;font-weight:500}
.input-interfaces .handle-label{position:absolute;left:8px}
.output-interfaces .handle-label{position:absolute;right:8px}
.horizontal-handle{transform:translateX(-50%)}
.output-ports .horizontal-handle{top:0}
.input-ports .horizontal-handle{bottom:0}
.output-ports .handle-label{position:absolute;top:8px;transform:translateX(-50%)}
.input-ports .handle-label{position:absolute;bottom:8px;transform:translateX(-50%)}
.workflow-handle{width:10px!important;height:10px!important;border:2px solid #fff!important;background:#3b82f6!important;box-shadow:0 0 0 1px #3b82f6;pointer-events:auto}
.interface-handle.vue-flow__handle-left{left:-5px!important}
.interface-handle.vue-flow__handle-right{right:-5px!important}
.port-handle{background:#8b5cf6!important;box-shadow:0 0 0 1px #8b5cf6}
.port-handle.vue-flow__handle-top{top:-5px!important}
.port-handle.vue-flow__handle-bottom{bottom:-5px!important}
</style>