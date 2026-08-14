<template>
  <div class="canvas-node" :class="[{ selected, warning: issues.length }, meta.className]" :style="nodeDimensions">
    <div class="handle-layer input-interfaces">
      <el-tooltip
        v-for="(item, index) in controlInputs"
        :key="item.name"
        :content="workflowInterfaceTooltip(item)"
        placement="left"
        :show-after="220"
        :teleported="true"
      >
        <div
          class="connector-hit-area side-connector"
          :style="sideHandleStyle(index, controlInputs.length)"
          :aria-label="workflowInterfaceTooltip(item)"
        >
          <Handle :id="interfaceHandleId(item.name)" type="target" :position="Position.Left" class="workflow-handle interface-handle" />
        </div>
      </el-tooltip>
    </div>

    <div class="handle-layer output-ports">
      <el-tooltip
        v-for="(item, index) in outputPorts"
        :key="item.name"
        :content="workflowPortTooltip(item, node || {})"
        placement="top"
        :show-after="220"
        :teleported="true"
      >
        <div
          class="connector-hit-area horizontal-connector"
          :style="horizontalHandleStyle(index, outputPorts.length)"
          :aria-label="workflowPortTooltip(item, node || {})"
        >
          <Handle :id="portHandleId(item.name)" type="source" :position="Position.Top" class="workflow-handle port-handle" />
        </div>
      </el-tooltip>
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
      <el-tooltip
        v-for="(item, index) in controlOutputs"
        :key="item.name"
        :content="workflowInterfaceTooltip(item)"
        placement="right"
        :show-after="220"
        :teleported="true"
      >
        <div
          class="connector-hit-area side-connector"
          :style="sideHandleStyle(index, controlOutputs.length)"
          :aria-label="workflowInterfaceTooltip(item)"
        >
          <Handle :id="interfaceHandleId(item.name)" type="source" :position="Position.Right" class="workflow-handle interface-handle" />
        </div>
      </el-tooltip>
    </div>

    <div class="handle-layer input-ports">
      <el-tooltip
        v-for="(item, index) in inputPorts"
        :key="item.name"
        :content="workflowPortTooltip(item, node || {})"
        placement="bottom"
        :show-after="220"
        :teleported="true"
      >
        <div
          class="connector-hit-area horizontal-connector"
          :style="horizontalHandleStyle(index, inputPorts.length)"
          :aria-label="workflowPortTooltip(item, node || {})"
        >
          <Handle :id="portHandleId(item.name)" type="target" :position="Position.Bottom" class="workflow-handle port-handle" />
        </div>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import {
  interfaceHandleId,
  portHandleId,
  workflowCanvasNodeSize,
  workflowInterfaceTooltip,
  workflowPortTooltip,
} from '../../../../utils/workflowCanvas.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node?: Item | null, selected?: boolean, issues?: Item[] }>(), { node: null, selected: false, issues: () => [] })
const workflowInterfaces = computed(() => (props.node?.interfaces || []).filter((item: Item) => item.interfaceType === 'WORKFLOW'))
const controlInputs = computed(() => workflowInterfaces.value.filter((item: Item) => item.direction === 'IN'))
const controlOutputs = computed(() => workflowInterfaces.value.filter((item: Item) => item.direction === 'OUT'))
const inputPorts = computed(() => (props.node?.ports || []).filter((item: Item) => item.direction === 'IN'))
const outputPorts = computed(() => (props.node?.ports || []).filter((item: Item) => item.direction === 'OUT'))
const canvasSize = computed(() => workflowCanvasNodeSize(props.node || {}))
const nodeDimensions = computed(() => ({ width: `${canvasSize.value.width}px`, height: `${canvasSize.value.height}px` }))
const meta = computed(() => {
  if (props.node?.nodeType === 'DEV_NODE') return { className: 'device', label: '设备能力' }
  if (props.node?.nodeType === 'SUBFLOW_NODE') return { className: 'subflow', label: '子流程' }
  if (props.node?.functionType === 'START') return { className: 'start', label: '开始节点' }
  if (props.node?.functionType === 'END') return { className: 'end', label: '结束节点' }
  if (props.node?.functionType === 'BRANCH') return { className: 'branch', label: '分支节点' }
  return { className: 'aggregate', label: '汇聚节点' }
})
const capabilityDisplayName = computed(() => (
  props.node?.capability?.displayName
  || props.node?.capability?.capabilityDisplayName
  || props.node?.capability?.capabilityName
  || '未选择能力'
))
const parameterCount = computed(() => Object.keys(props.node?.capability?.capabilityParameters || {}).length)
const summary = computed(() => {
  if (props.node?.nodeType === 'DEV_NODE') return `${capabilityDisplayName.value} · ${parameterCount.value} 个参数`
  if (props.node?.nodeType === 'SUBFLOW_NODE') return props.node.subFlowModelDescription || `流程模型 ${props.node.subFlowModelId || '-'}`
  if (props.node?.functionType === 'BRANCH') return props.node.expression || '按控制接口判断下游路径'
  if (props.node?.functionType === 'START') return '启动流程执行'
  if (props.node?.functionType === 'END') return '结束流程执行'
  return '汇聚上游执行结果'
})

function sideHandleStyle(index: number, total: number) {
  const available = Math.max(24, canvasSize.value.height - 52)
  return { top: `${Math.round(26 + available * (index + 1) / (total + 1))}px` }
}

function horizontalHandleStyle(index: number, total: number) {
  return { left: `${Math.round((index + 1) * 100 / (total + 1))}%` }
}
</script>

<style scoped>
.canvas-node{position:relative;width:220px;min-height:92px;display:flex;border:1px solid #b9c6d7;border-radius:6px;background:#fff;box-shadow:0 2px 8px rgba(30,55,80,.08);overflow:visible;transition:border-color .16s ease,box-shadow .16s ease,transform .16s ease;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",Arial,sans-serif}
.canvas-node:before{content:'';position:absolute;left:-1px;top:8px;bottom:8px;width:3px;border-radius:0 2px 2px 0;background:#1677ff}.canvas-node.start:before{background:#52a56b}.canvas-node.end:before{background:#7d8794}.canvas-node.branch:before{background:#d99a26}.canvas-node.aggregate:before{background:#7367c7}.canvas-node.subflow:before{background:#2b9ea1}
.canvas-node:hover{border-color:#6da5e8;box-shadow:0 5px 14px rgba(48,93,142,.14);transform:translateY(-1px)}.canvas-node.selected{border-color:#1677ff;box-shadow:0 0 0 2px rgba(22,119,255,.16),0 5px 14px rgba(48,93,142,.12)}.canvas-node.warning{border-color:#dc9f32}
.node-main{min-width:0;display:flex;flex:1;flex-direction:column;justify-content:center;padding:14px 20px}.node-topline{display:flex;align-items:center;gap:6px;min-width:0}.node-type-dot{width:7px;height:7px;flex:none;border-radius:50%;background:#1677ff}.start .node-type-dot{background:#52a56b}.end .node-type-dot{background:#7d8794}.branch .node-type-dot{background:#d99a26}.aggregate .node-type-dot{background:#7367c7}.subflow .node-type-dot{background:#2b9ea1}.node-kind{color:#7d8998;font-size:11px;font-weight:500}.warning-dot{width:15px;height:15px;display:grid;place-items:center;flex:none;margin-left:auto;border-radius:50%;background:#fff2cf;color:#9b6508;font-size:10px;font-weight:600}.node-body{display:grid;gap:4px;min-width:0;margin-top:7px}.node-name,.node-summary{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.node-name{color:#1f2d3d;font-size:13px;font-weight:600}.node-summary{color:#8190a2;font-size:10px}
.handle-layer{position:absolute;inset:0;pointer-events:none}.connector-hit-area{position:absolute;z-index:4;width:22px;height:22px;display:block;pointer-events:auto;cursor:crosshair}.side-connector{transform:translateY(-50%)}.horizontal-connector{transform:translateX(-50%)}.input-interfaces .connector-hit-area{left:-11px}.output-interfaces .connector-hit-area{right:-11px}.output-ports .connector-hit-area{top:-11px}.input-ports .connector-hit-area{bottom:-11px}
.workflow-handle{left:50%!important;right:auto!important;top:50%!important;bottom:auto!important;width:10px!important;height:10px!important;margin:0!important;transform:translate(-50%,-50%)!important;border:2px solid #fff!important;background:#407fbd!important;box-shadow:0 0 0 1px #407fbd;pointer-events:auto;transition:width .14s ease,height .14s ease,box-shadow .14s ease}.connector-hit-area:hover .workflow-handle{width:12px!important;height:12px!important;box-shadow:0 0 0 2px rgba(64,127,189,.2),0 0 0 1px #407fbd}.interface-handle{border-radius:50%!important}.port-handle{border-radius:2px!important;background:#7367c7!important;box-shadow:0 0 0 1px #7367c7}.connector-hit-area:hover .port-handle{box-shadow:0 0 0 2px rgba(115,103,199,.2),0 0 0 1px #7367c7}
</style>
