<template>
  <div class="canvas-node" :class="[{ selected, warning: issues.length, compact: isCompact, readonly, unbound: showUnboundState }, meta.className, statusClass]" :style="nodeDimensions">
    <div class="handle-layer input-interfaces">
      <el-tooltip
        v-for="(item, index) in controlInputs"
        :key="item.name"
        :content="workflowInterfaceTooltip(item)"
        placement="left"
        :disabled="hidePortTooltips"
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
        :content="workflowPortTooltip(item)"
        placement="bottom"
        :disabled="hidePortTooltips"
        :show-after="220"
        :teleported="true"
      >
        <div
          class="connector-hit-area horizontal-connector"
          :style="horizontalHandleStyle(index, outputPorts.length)"
          :aria-label="workflowPortTooltip(item)"
        >
          <Handle :id="portHandleId(item.name)" type="source" :position="Position.Bottom" class="workflow-handle port-handle" />
        </div>
      </el-tooltip>
    </div>

    <div class="node-main">
      <header class="node-header">
        <span class="node-chip" v-html="meta.icon"></span>
        <div class="node-title">
          <strong class="node-name" :title="node?.name">{{ node?.name || '未命名节点' }}</strong>
          <small class="node-kind">{{ meta.label }}</small>
        </div>
        <span v-if="issues.length" class="warning-dot" :title="issues[0]?.message || '节点配置未完成'">!</span>
        <div v-if="statusLabel || showBindingLine" class="node-badges">
          <span v-if="showBindingLine" class="bind-pill" :class="overlay.bound ? 'is-bound' : 'is-unbound'">
            {{ overlay.bound ? (overlay.boundLabel || '已绑定') : '待绑定' }}
          </span>
          <span v-if="statusLabel" class="status-pill" :class="statusTone">{{ statusLabel }}</span>
        </div>
      </header>
      <div v-if="isBranch" class="node-body rows">
        <div v-for="item in controlOutputs" :key="item.name" class="out-row">
          <b :title="outputRowText(item)">{{ outputRowText(item) }}</b>
        </div>
        <div v-if="!controlOutputs.length" class="out-row muted"><b>未配置输出接口</b></div>
      </div>
      <div v-else-if="!isCompact" class="node-body">
        <div class="node-op">
          <label>操作</label>
          <span :title="operationTitle">{{ operationTitle }}</span>
        </div>
        <div v-if="paramChips.length" class="node-params">
          <span v-for="chip in paramChips" :key="chip" class="kv">{{ chip }}</span>
        </div>
      </div>
    </div>

    <div class="handle-layer output-interfaces">
      <el-tooltip
        v-for="(item, index) in controlOutputs"
        :key="item.name"
        :content="workflowInterfaceTooltip(item)"
        placement="right"
        :disabled="hidePortTooltips"
        :show-after="220"
        :teleported="true"
      >
        <div
          class="connector-hit-area side-connector"
          :style="outputHandleStyle(index)"
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
        :content="workflowPortTooltip(item)"
        placement="top"
        :disabled="hidePortTooltips"
        :show-after="220"
        :teleported="true"
      >
        <div
          class="connector-hit-area horizontal-connector"
          :style="horizontalHandleStyle(index, inputPorts.length)"
          :aria-label="workflowPortTooltip(item)"
        >
          <Handle :id="portHandleId(item.name)" type="target" :position="Position.Top" class="workflow-handle port-handle" />
        </div>
      </el-tooltip>
    </div>

    <span
      v-for="(item, index) in inputPorts"
      :key="`in-label-${item.name}`"
      class="port-label top"
      :style="portLabelStyle(index, inputPorts.length)"
    >{{ item.name }}</span>
    <span
      v-for="(item, index) in outputPorts"
      :key="`out-label-${item.name}`"
      class="port-label bottom"
      :style="portLabelStyle(index, outputPorts.length)"
    >{{ item.name }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import {
  interfaceHandleId,
  portHandleId,
  workflowCanvasNodeSize,
  workflowDeviceCapabilityPresentation,
  workflowInterfaceTooltip,
  workflowPortTooltip,
  formatWorkflowTriggerText,
} from '../../../../utils/workflowCanvas.js'

const NODE_ICONS: Record<string, string> = {
  start: '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z" fill="currentColor"/></svg>',
  end: '<svg viewBox="0 0 24 24"><rect x="7" y="7" width="10" height="10" rx="2" fill="currentColor"/></svg>',
  branch: '<svg viewBox="0 0 24 24"><rect x="7" y="7" width="10" height="10" rx="1.5" transform="rotate(45 12 12)" fill="none" stroke="currentColor" stroke-width="1.8"/></svg>',
  device: '<svg viewBox="0 0 24 24"><rect x="6" y="6" width="12" height="12" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><rect x="10" y="10" width="4" height="4" fill="currentColor"/></svg>',
  aggregate: '<svg viewBox="0 0 24 24"><path d="M4 7h6M4 17h6M14 12h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M10 7l6 5-6 5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  subflow: '<svg viewBox="0 0 24 24"><rect x="4" y="4" width="10" height="10" rx="2" fill="none" stroke="currentColor" stroke-width="1.8"/><rect x="10" y="10" width="10" height="10" rx="2" fill="currentColor" opacity=".45"/></svg>',
}

type Item = Record<string, any>
const props = withDefaults(defineProps<{
  node?: Item | null
  selected?: boolean
  issues?: Item[]
  deviceCapabilities?: Item[]
  hidePortTooltips?: boolean
  readonly?: boolean
  overlay?: Item
}>(), {
  node: null,
  selected: false,
  issues: () => [],
  deviceCapabilities: () => [],
  hidePortTooltips: false,
  readonly: false,
  overlay: () => ({}),
})
const workflowInterfaces = computed(() => (props.node?.interfaces || []).filter((item: Item) => item.interfaceType === 'WORKFLOW'))
const controlInputs = computed(() => workflowInterfaces.value.filter((item: Item) => item.direction === 'IN'))
const controlOutputs = computed(() => workflowInterfaces.value.filter((item: Item) => item.direction === 'OUT'))
const inputPorts = computed(() => (props.node?.ports || []).filter((item: Item) => item.direction === 'IN'))
const outputPorts = computed(() => (props.node?.ports || []).filter((item: Item) => item.direction === 'OUT'))
const canvasSize = computed(() => workflowCanvasNodeSize(props.node || {}))
const nodeDimensions = computed(() => ({ width: `${canvasSize.value.width}px`, height: `${canvasSize.value.height}px` }))
const isCompact = computed(() => props.node?.functionType === 'START' || props.node?.functionType === 'END')
const isBranch = computed(() => props.node?.functionType === 'BRANCH')
const meta = computed(() => {
  if (props.node?.nodeType === 'DEV_NODE') return { className: 'device', label: '设备能力', icon: NODE_ICONS.device }
  if (props.node?.nodeType === 'SUBFLOW_NODE') return { className: 'subflow', label: '子流程', icon: NODE_ICONS.subflow }
  if (props.node?.functionType === 'START') return { className: 'start', label: '开始节点', icon: NODE_ICONS.start }
  if (props.node?.functionType === 'END') return { className: 'end', label: '结束节点', icon: NODE_ICONS.end }
  if (props.node?.functionType === 'BRANCH') return { className: 'branch', label: '分支节点', icon: NODE_ICONS.branch }
  return { className: 'aggregate', label: '汇聚节点', icon: NODE_ICONS.aggregate }
})
const capabilityCopy = computed(() => workflowDeviceCapabilityPresentation(props.node, props.deviceCapabilities))
const operationTitle = computed(() => {
  if (props.node?.nodeType === 'DEV_NODE') return capabilityCopy.value.title
  if (props.node?.nodeType === 'SUBFLOW_NODE') return props.node.subFlowModelDescription || `流程模型 ${props.node.subFlowModelId || '-'}`
  if (props.node?.functionType === 'BRANCH') return props.node.expression || '按控制接口判断下游路径'
  if (props.node?.functionType === 'START') return '启动流程执行'
  if (props.node?.functionType === 'END') return '结束流程执行'
  return '汇聚上游执行结果'
})
const operationDetail = computed(() => props.node?.nodeType === 'DEV_NODE' ? capabilityCopy.value.detail : '')
const paramChips = computed(() => (operationDetail.value || '').split('、').map(item => item.trim()).filter(Boolean))
const statusLabel = computed(() => props.overlay?.mode === 'runtime' ? (props.overlay.statusLabel || '') : '')
const statusTone = computed(() => String(props.overlay?.status || '').toLowerCase())
const statusClass = computed(() => statusTone.value ? `status-${statusTone.value}` : '')
const showBindingLine = computed(() => {
  if (props.node?.nodeType !== 'DEV_NODE') return false
  if (props.overlay?.mode === 'binding' || props.overlay?.showBinding) return true
  return props.overlay?.bound === true || props.overlay?.bound === false || Boolean(props.overlay?.boundLabel)
})
const showUnboundState = computed(() => showBindingLine.value && !props.overlay?.bound)

function outputRowText(item: Item) {
  return formatWorkflowTriggerText(item) || '未配置触发条件'
}

function sideHandleStyle(index: number, total: number) {
  if (isCompact.value) {
    const center = canvasSize.value.height / 2
    if (total <= 1) return { top: `${Math.round(center)}px` }
    const spread = Math.min(24 * (total - 1), canvasSize.value.height - 16)
    const start = center - spread / 2
    return { top: `${Math.round(start + spread * index / (total - 1))}px` }
  }
  const available = Math.max(24, canvasSize.value.height - 52)
  return { top: `${Math.round(26 + available * (index + 1) / (total + 1))}px` }
}

function outputHandleStyle(index: number) {
  if (isBranch.value) return { top: `${65 + 30 * index}px` }
  return sideHandleStyle(index, controlOutputs.value.length)
}

function horizontalHandleStyle(index: number, total: number) {
  return { left: `${Math.round((index + 1) * 100 / (total + 1))}%` }
}

function portLabelStyle(index: number, total: number) {
  return { left: `calc(${Math.round((index + 1) * 100 / (total + 1))}% + 10px)` }
}
</script>

<style scoped>
.canvas-node{position:relative;width:220px;min-height:44px;display:flex;border:1px solid var(--sl-border-base,#e4e7ed);border-radius:10px;background:#fff;box-shadow:var(--sl-shadow-sm);overflow:visible;transition:border-color .15s ease,box-shadow .15s ease,transform .15s ease;font-family:var(--sl-font-family)}
.canvas-node:hover{border-color:#c4cad3;box-shadow:0 2px 4px rgba(16,24,40,.06),0 12px 28px -8px rgba(16,24,40,.14);transform:translateY(-1px)}
.canvas-node.selected{border-color:var(--sl-primary,#2563eb);box-shadow:0 0 0 3px rgba(37,99,235,.14),var(--sl-shadow-sm)}
.canvas-node.selected:hover{border-color:var(--sl-primary,#2563eb)}
.canvas-node.warning{border-color:#e8c47f}
.node-main{min-width:0;display:flex;flex:1;flex-direction:column}
.node-header{display:flex;align-items:flex-start;gap:8px;padding:10px 12px 8px}
.compact .node-header{padding:9px 12px;align-items:center}
.node-chip{flex:none;display:grid;place-items:center;width:26px;height:26px;border-radius:7px}
.compact .node-chip{width:22px;height:22px;border-radius:6px}
.node-chip :deep(svg){width:14px;height:14px;display:block}
.canvas-node.start .node-chip{background:#eaf7f0;color:#1e9e62}
.canvas-node.device .node-chip{background:#edf3fe;color:#2563eb}
.canvas-node.branch .node-chip{background:#fdf4e3;color:#c77414}
.canvas-node.end .node-chip{background:#f1f2f4;color:#6b7280}
.canvas-node.aggregate .node-chip{background:#f5f3ff;color:#6d28d9}
.canvas-node.subflow .node-chip{background:#f0fdfa;color:#0f766e}
.node-title{min-width:0;flex:1}
.node-name{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--sl-text-heading,#1f2329);font-size:13px;font-weight:600;line-height:1.25}
.compact .node-name{font-size:12.5px}
.node-kind{display:block;color:var(--sl-text-secondary,#8f959e);font-size:10.5px}
.warning-dot{width:16px;height:16px;display:grid;place-items:center;flex:none;border-radius:50%;background:var(--sl-warning-light,#fffbeb);color:var(--sl-warning,#d97706);font-size:10px;font-weight:600}
.node-badges{margin-left:auto;flex:none;display:flex;flex-direction:column;align-items:flex-end;justify-content:flex-start;gap:2px;max-width:96px}
.status-pill{flex:none;font-size:10px;font-weight:700;line-height:1.2;text-align:right;white-space:nowrap;color:#94a3b8}
.status-pill.running{color:var(--sl-primary,#2563eb)}
.status-pill.succeeded{color:var(--sl-success,#16a34a)}
.status-pill.failed{color:var(--sl-danger,#dc2626)}
.status-pill.terminating,.status-pill.terminated{color:var(--sl-warning,#d97706)}
.canvas-node.status-running{box-shadow:0 0 0 3px rgba(37,99,235,.16),var(--sl-shadow-sm)}
.canvas-node.status-failed{border-color:#fca5a5}
.canvas-node.status-succeeded{border-color:#bbf7d0}
.canvas-node.unbound{border-color:#f0b429;box-shadow:0 0 0 2px rgba(240,180,41,.22),var(--sl-shadow-sm)}
.bind-pill{flex:none;max-width:96px;padding:1px 6px;border-radius:999px;font-size:10px;font-weight:700;line-height:16px}
.bind-pill.is-unbound{background:#fff7e8;color:#b45309;border:1px solid #f5d08a}
.bind-pill.is-bound{background:#f0fdf4;color:#15803d;border:1px solid #bbf7d0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.node-body{flex:1;display:flex;flex-direction:column;padding:4px 12px 18px;border-top:1px solid var(--sl-border-light,#f0f1f4)}
.node-body:not(.rows){align-items:flex-start;justify-content:flex-start;text-align:left}
.node-op{display:flex;align-items:baseline;justify-content:flex-start;gap:6px;min-width:0;max-width:100%}
.node-op label{flex:none;color:#a9aeb8;font-size:10px}
.node-op span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--sl-text-heading,#1f2329);font-size:12.5px;font-weight:650}
.node-params{display:flex;flex-wrap:wrap;justify-content:flex-start;gap:4px;margin-top:4px;max-height:24px;overflow:hidden}
.kv{padding:2px 6px;border:1px solid #e9ebef;border-radius:4px;background:#f5f6f8;color:#5b6470;font-size:10.5px;white-space:nowrap}
.node-body.rows{padding:6px 0}
.out-row{display:flex;align-items:center;gap:6px;height:30px;padding:0 12px}
.out-row+.out-row{border-top:1px solid var(--sl-border-light,#f0f1f4)}
.out-row b{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#4e5969;font-size:11px;font-weight:600}
.out-row.muted b{color:#a9aeb8;font-weight:500}
.port-label{position:absolute;z-index:4;color:#a9aeb8;font-size:9px;font-family:Consolas,"JetBrains Mono",monospace;white-space:nowrap;pointer-events:none}
.port-label.top{top:-15px}
.port-label.bottom{top:calc(100% + 4px)}
.handle-layer{position:absolute;inset:0;pointer-events:none}.connector-hit-area{position:absolute;z-index:4;width:22px;height:22px;display:block;pointer-events:auto;cursor:crosshair}.side-connector{transform:translateY(-50%)}.horizontal-connector{transform:translateX(-50%)}.input-interfaces .connector-hit-area{left:-11px}.output-interfaces .connector-hit-area{right:-11px}.output-ports .connector-hit-area{bottom:-11px}.input-ports .connector-hit-area{top:-11px}
.canvas-node.readonly .connector-hit-area{cursor:default}
.canvas-node.readonly:hover{transform:none}
.workflow-handle{left:50%!important;right:auto!important;top:50%!important;bottom:auto!important;width:10px!important;height:10px!important;margin:0!important;transform:translate(-50%,-50%)!important;border:2px solid #9aa5b1!important;background:#fff!important;box-shadow:none;pointer-events:auto;transition:width .14s ease,height .14s ease,border-color .14s ease,box-shadow .14s ease}
.connector-hit-area:hover .workflow-handle{width:12px!important;height:12px!important;border-color:var(--sl-primary,#2563eb)!important;box-shadow:0 0 0 3px rgba(37,99,235,.18)}
.canvas-node.selected .workflow-handle{border-color:var(--sl-primary,#2563eb)!important}
.interface-handle{border-radius:50%!important}
.port-handle{border-radius:2px!important}
</style>
