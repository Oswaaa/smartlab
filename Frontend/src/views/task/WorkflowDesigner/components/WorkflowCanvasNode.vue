<template>
  <div class="canvas-node" :class="[{ selected, warning: issues.length }, meta.className]" :style="nodeDimensions">
    <div class="handle-layer input-interfaces">
      <div v-for="(item, index) in controlInputs" :key="item.name" class="side-handle" :style="sideHandleStyle(index)">
        <Handle :id="interfaceHandleId(item.name)" type="target" :position="Position.Left" class="workflow-handle interface-handle" />
        <span class="handle-label left" :title="interfaceLabel(item)">{{ interfaceLabel(item) }}</span>
      </div>
    </div>
    <div class="handle-layer output-ports">
      <div v-for="(item, index) in outputPorts" :key="item.name" class="horizontal-handle" :style="horizontalHandleStyle(index)">
        <Handle :id="portHandleId(item.name)" type="source" :position="Position.Top" class="workflow-handle port-handle" />
        <span class="handle-label top" :title="item.name">{{ item.name }}</span>
      </div>
    </div>
    <div class="node-main">
      <div class="node-topline">
        <span class="node-type-dot"></span>
        <span class="node-kind">{{ meta.label }}</span>
        <span v-if="issues.length" class="warning-dot" :title="issues[0]?.message || '节点配置未完成'">!</span>
      </div>
      <div class="node-body">
        <strong class="node-name" :title="node?.name">{{ node?.name || '未命名节点' }}</strong>
        <span class="node-summary" :title="summary">{{ summary }}</span>
      </div>
    </div>
    <div class="handle-layer output-interfaces">
      <div v-for="(item, index) in controlOutputs" :key="item.name" class="side-handle" :style="sideHandleStyle(index)">
        <span class="handle-label right" :title="interfaceLabel(item)">{{ interfaceLabel(item) }}</span>
        <Handle :id="interfaceHandleId(item.name)" type="source" :position="Position.Right" class="workflow-handle interface-handle" />
      </div>
    </div>
    <div class="handle-layer input-ports">
      <div v-for="(item, index) in inputPorts" :key="item.name" class="horizontal-handle" :style="horizontalHandleStyle(index)">
        <span class="handle-label bottom" :title="item.name">{{ item.name }}</span>
        <Handle :id="portHandleId(item.name)" type="target" :position="Position.Bottom" class="workflow-handle port-handle" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { interfaceHandleId, portHandleId } from '../../../../utils/workflowCanvas.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node?: Item | null, selected?: boolean, issues?: Item[] }>(), { node: null, selected: false, issues: () => [] })
const workflowInterfaces = computed(() => (props.node?.interfaces || []).filter((item: Item) => item.interfaceType === 'WORKFLOW'))
const controlInputs = computed(() => workflowInterfaces.value.filter((item: Item) => item.direction === 'IN'))
const controlOutputs = computed(() => workflowInterfaces.value.filter((item: Item) => item.direction === 'OUT'))
const inputPorts = computed(() => (props.node?.ports || []).filter((item: Item) => item.direction === 'IN'))
const outputPorts = computed(() => (props.node?.ports || []).filter((item: Item) => item.direction === 'OUT'))
const maxSideCount = computed(() => Math.max(controlInputs.value.length, controlOutputs.value.length))
const maxPortCount = computed(() => Math.max(inputPorts.value.length, outputPorts.value.length))
const nodeDimensions = computed(() => ({ minHeight: `${Math.max(70, maxSideCount.value * 18 + 36)}px`, minWidth: `${Math.max(178, maxPortCount.value * 30 + 48)}px` }))
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
  if (props.node?.functionType === 'START') return '发出激活信号'
  if (props.node?.functionType === 'END') return '接收流程结束信号'
  return '汇聚上游执行路径'
})
function interfaceLabel(item: Item) {
  return item.name
}
function sideHandleStyle(index: number) { return { top: `${18 + index * 18}px` } }
function horizontalHandleStyle(index: number) { return { left: `${24 + index * 30}px` } }
</script>

<style scoped>
.canvas-node{position:relative;width:178px;min-height:70px;display:flex;border:1px solid #b8c5d6;border-radius:2px;background:#fff;box-shadow:0 2px 7px rgba(0,0,0,.07);overflow:visible;transition:border-color .15s ease,box-shadow .15s ease,transform .15s ease;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",Arial,sans-serif}
.canvas-node:before{content:'';position:absolute;left:-1px;top:-1px;bottom:-1px;width:3px;border-radius:2px 0 0 2px;background:#1677ff}.canvas-node.start:before{background:#52c41a}.canvas-node.end:before{background:#8c8c8c}.canvas-node.branch:before{background:#faad14}.canvas-node.aggregate:before{background:#722ed1}.canvas-node.subflow:before{background:#13c2c2}
.canvas-node:hover{border-color:#4096ff;box-shadow:0 4px 12px rgba(22,119,255,.14);transform:translateY(-1px)}.canvas-node.selected{border-color:#1677ff;box-shadow:0 0 0 2px rgba(22,119,255,.18),0 4px 12px rgba(22,119,255,.12)}.canvas-node.warning{border-color:#faad14}
.node-main{min-width:0;display:flex;flex:1;flex-direction:column;padding:9px 11px 9px 13px}.node-topline{display:flex;align-items:center;gap:5px;min-width:0}.node-type-dot{width:7px;height:7px;flex:none;border-radius:50%;background:#1677ff}.start .node-type-dot{background:#52c41a}.end .node-type-dot{background:#8c8c8c}.branch .node-type-dot{background:#faad14}.aggregate .node-type-dot{background:#722ed1}.subflow .node-type-dot{background:#13c2c2}.node-kind{color:#8c8c8c;font-size:10px;font-weight:400}.warning-dot{width:14px;height:14px;display:grid;place-items:center;flex:none;margin-left:auto;border-radius:50%;background:#fff1b8;color:#ad6800;font-size:9px;font-weight:600}.node-body{display:grid;gap:2px;min-width:0;margin-top:7px}.node-name,.node-summary{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.node-name{color:#262626;font-size:12px;font-weight:500}.node-summary{color:#8c8c8c;font-size:9px}
.handle-layer{position:absolute;inset:0;pointer-events:none}
.side-handle,.horizontal-handle{position:absolute;display:flex;align-items:center;color:#94a3b8;font-size:9px;line-height:1;white-space:nowrap;pointer-events:none}
.side-handle{height:18px}
.input-interfaces .side-handle{left:0}
.output-interfaces .side-handle{right:0}
.handle-label{max-width:48px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#8c8c8c;font-size:8px;font-weight:400;background:rgba(255,255,255,.94);padding:1px 2px;border-radius:1px;opacity:.7;transition:opacity .15s ease}.canvas-node:hover .handle-label{opacity:1}
.handle-label.left{margin-left:8px}
.handle-label.right{margin-right:8px}
.horizontal-handle{width:30px;height:18px;justify-content:center}
.output-ports .horizontal-handle{top:0}
.input-ports .horizontal-handle{bottom:0}
.handle-label.top{position:absolute;top:7px;transform:translateX(-50%)}
.handle-label.bottom{position:absolute;bottom:7px;transform:translateX(-50%)}
.workflow-handle{width:9px!important;height:9px!important;border:2px solid #fff!important;background:#1677ff!important;box-shadow:0 0 0 1px #1677ff;pointer-events:auto}.interface-handle{border-radius:50%!important}
.interface-handle.vue-flow__handle-left{left:-5px!important}
.interface-handle.vue-flow__handle-right{right:-5px!important}
.port-handle{border-radius:1px!important;background:#722ed1!important;box-shadow:0 0 0 1px #722ed1}
.port-handle.vue-flow__handle-top{top:-5px!important}
.port-handle.vue-flow__handle-bottom{bottom:-5px!important}
.port-handle.vue-flow__handle-top,.port-handle.vue-flow__handle-bottom{left:15px!important}
</style>
