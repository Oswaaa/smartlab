<template>
  <div class="canvas-node" :class="[{ selected, warning: issues.length }, meta.className]">
    <div class="handle-layer input-interfaces">
      <div v-for="(item, index) in workflowInputs" :key="item.name" class="side-handle" :style="sideHandleStyle(index, workflowInputs.length)">
        <Handle :id="interfaceHandleId(item.name)" type="target" :position="Position.Left" class="workflow-handle interface-handle" />
        <span>{{ interfaceLabel(item) }}</span>
      </div>
    </div>
    <div class="handle-layer output-ports">
      <div v-for="(item, index) in outputPorts" :key="item.name" class="horizontal-handle" :style="horizontalHandleStyle(index, outputPorts.length)">
        <Handle :id="portHandleId(item.name)" type="source" :position="Position.Top" class="workflow-handle port-handle" />
        <span>{{ item.name }}</span>
      </div>
    </div>
    <div class="node-accent"></div>
    <div class="node-main">
      <div class="node-topline"><span class="node-glyph">{{ meta.glyph }}</span><span class="node-kind">{{ meta.label }}</span><span v-if="issues.length" class="warning-dot" :title="issues[0]?.message || '节点配置未完成'">!</span></div>
      <strong class="node-name">{{ node?.name || '未命名节点' }}</strong>
      <span class="node-summary">{{ summary }}</span>
    </div>
    <div class="handle-layer output-interfaces">
      <div v-for="(item, index) in workflowOutputs" :key="item.name" class="side-handle" :style="sideHandleStyle(index, workflowOutputs.length)">
        <span>{{ interfaceLabel(item) }}</span>
        <Handle :id="interfaceHandleId(item.name)" type="source" :position="Position.Right" class="workflow-handle interface-handle" />
      </div>
    </div>
    <div class="handle-layer input-ports">
      <div v-for="(item, index) in inputPorts" :key="item.name" class="horizontal-handle" :style="horizontalHandleStyle(index, inputPorts.length)">
        <span>{{ item.name }}</span>
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
.canvas-node{position:relative;width:218px;min-height:96px;display:flex;border:1px solid #c8d5e5;border-radius:9px;background:#fff;box-shadow:0 4px 13px rgba(34,61,94,.09);overflow:visible;transition:border-color .15s,box-shadow .15s}.canvas-node:hover{border-color:#83a7d3;box-shadow:0 6px 18px rgba(34,61,94,.14)}.canvas-node.selected{border-color:#3277c5;box-shadow:0 0 0 3px rgba(50,119,197,.14),0 6px 18px rgba(34,61,94,.13)}.canvas-node.warning{border-color:#e0ae54}.node-accent{width:5px;flex:none;border-radius:8px 0 0 8px;background:#5f8fc8}.start .node-accent{background:#3ca36b}.end .node-accent{background:#657386}.branch .node-accent{background:#d89427}.aggregate .node-accent{background:#8566c2}.subflow .node-accent{background:#32958d}.node-main{min-width:0;display:flex;flex:1;flex-direction:column;padding:12px 14px}.node-topline{display:flex;align-items:center;gap:6px;margin-bottom:8px}.node-glyph{width:20px;height:20px;display:grid;place-items:center;border-radius:5px;background:#eef4fb;color:#3978bd;font-size:9px;font-weight:800}.start .node-glyph{background:#eaf8f0;color:#218654}.end .node-glyph{background:#f0f2f5;color:#536073}.branch .node-glyph{background:#fff4de;color:#ad7213}.aggregate .node-glyph{background:#f2edff;color:#7251b6}.subflow .node-glyph{background:#eaf7f6;color:#237f78}.node-kind{color:#78869a;font-size:10px;font-weight:700}.warning-dot{width:16px;height:16px;display:grid;place-items:center;margin-left:auto;border-radius:50%;background:#fff1d6;color:#aa6a00;font-size:10px;font-weight:800}.node-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#213047;font-size:14px}.node-summary{margin-top:5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#7d899a;font-size:11px}.handle-layer{position:absolute;inset:0;pointer-events:none}.side-handle,.horizontal-handle{position:absolute;display:flex;align-items:center;color:#718096;font-size:8px;line-height:1;white-space:nowrap;pointer-events:none}.side-handle{transform:translateY(-50%)}.input-interfaces .side-handle{left:0}.output-interfaces .side-handle{right:0}.input-interfaces span{position:absolute;left:7px}.output-interfaces span{position:absolute;right:7px}.horizontal-handle{transform:translateX(-50%)}.output-ports .horizontal-handle{top:0}.input-ports .horizontal-handle{bottom:0}.output-ports span{position:absolute;top:7px;transform:translateX(-50%)}.input-ports span{position:absolute;bottom:7px;transform:translateX(-50%)}.workflow-handle{width:11px!important;height:11px!important;border:2px solid #fff!important;background:#4f83bf!important;box-shadow:0 0 0 1px #4f83bf;pointer-events:auto}.interface-handle.vue-flow__handle-left{left:-6px!important}.interface-handle.vue-flow__handle-right{right:-6px!important}.port-handle{background:#7c4dce!important;box-shadow:0 0 0 1px #7c4dce}.port-handle.vue-flow__handle-top{top:-6px!important}.port-handle.vue-flow__handle-bottom{bottom:-6px!important}
</style>