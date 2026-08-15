<template>
  <div :class="['binding-node-shell', nodeClass, { selected: data.selected, invalid: data.bindingInvalid }]">
    <Handle type="target" :position="Position.Left" class="binding-handle" />
    <article class="binding-node-card">
      <header>
        <span class="node-glyph">{{ nodeGlyph }}</span>
        <div>
          <strong>{{ data.name }}</strong>
          <small>{{ nodeTypeLabel }}</small>
        </div>
      </header>
      <div v-if="data.nodeType === 'DEV_NODE'" class="binding-state">
        <i :class="{ bound: data.bound }"></i>
        <span>{{ data.bound ? data.boundInstanceName : '待绑定设备' }}</span>
      </div>
      <div v-else-if="data.nodeType === 'SUBFLOW_NODE'" class="binding-state subflow-entry">
        <span>进入子流程</span><b>›</b>
      </div>
      <div v-else class="binding-state muted">
        <span>{{ functionDescription }}</span>
      </div>
    </article>
    <Handle type="source" :position="Position.Right" class="binding-handle" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

type Item = Record<string, any>
const props = defineProps<{ data: Item }>()
const nodeClass = computed(() => {
  if (props.data.nodeType === 'DEV_NODE') return 'device'
  if (props.data.nodeType === 'SUBFLOW_NODE') return 'subflow'
  return String(props.data.functionType || 'function').toLowerCase()
})
const nodeGlyph = computed(() => {
  if (props.data.nodeType === 'DEV_NODE') return 'D'
  if (props.data.nodeType === 'SUBFLOW_NODE') return '↳'
  return ({ START: '▶', END: '■', BRANCH: '◇', AGGREGATE: '◆' } as Record<string, string>)[props.data.functionType] || 'N'
})
const nodeTypeLabel = computed(() => {
  if (props.data.nodeType === 'DEV_NODE') return '设备能力节点'
  if (props.data.nodeType === 'SUBFLOW_NODE') return '子流程节点'
  return ({ START: '开始节点', END: '结束节点', BRANCH: '条件分支', AGGREGATE: '聚合节点' } as Record<string, string>)[props.data.functionType] || '功能节点'
})
const functionDescription = computed(() => ({
  START: '流程入口',
  END: '流程结束',
  BRANCH: '按条件选择路径',
  AGGREGATE: '汇聚上游路径'
} as Record<string, string>)[props.data.functionType] || '流程控制')
</script>

<style scoped>
.binding-node-shell{position:relative;width:196px;cursor:default}.binding-node-shell.device,.binding-node-shell.subflow{cursor:pointer}.binding-node-card{overflow:hidden;border:1px solid #d5dbe3;border-left:4px solid #aeb7c4;border-radius:6px;background:#fff;box-shadow:0 3px 10px rgba(31,45,61,.07);transition:border-color .16s ease,box-shadow .16s ease,transform .16s ease}.binding-node-shell.device:hover .binding-node-card,.binding-node-shell.subflow:hover .binding-node-card,.binding-node-shell.selected .binding-node-card{border-color:#1677ff;box-shadow:0 0 0 3px rgba(22,119,255,.12),0 4px 12px rgba(31,45,61,.08);transform:translateY(-1px)}.binding-node-shell.device .binding-node-card{border-left-color:#1677ff}.binding-node-shell.subflow .binding-node-card{border-left-color:#6f55b5}.binding-node-shell.start .binding-node-card{border-left-color:#249a5b}.binding-node-shell.end .binding-node-card{border-left-color:#5b6675}.binding-node-shell.branch .binding-node-card{border-left-color:#d89714}.binding-node-shell.aggregate .binding-node-card{border-left-color:#7756b7}.binding-node-shell.invalid .binding-node-card{border-color:#e45555;border-left-color:#e45555}.binding-node-card header{display:flex;align-items:center;gap:9px;padding:10px 11px;border-bottom:1px solid #eef0f3}.node-glyph{display:grid;place-items:center;width:27px;height:27px;flex:none;border-radius:5px;background:#edf5ff;color:#1677ff;font-size:10px;font-weight:800}.subflow .node-glyph{background:#f2edff;color:#7053b3}.start .node-glyph{background:#eaf8f0;color:#218654}.end .node-glyph{background:#eef1f4;color:#536073}.branch .node-glyph{background:#fff4de;color:#ad7213}.aggregate .node-glyph{background:#f2edff;color:#7251b6}.binding-node-card header>div{display:grid;gap:2px;min-width:0}.binding-node-card strong{overflow:hidden;color:#273142;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.binding-node-card small{color:#8a94a3;font-size:9px}.binding-state{min-height:33px;display:flex;align-items:center;gap:7px;padding:7px 11px;box-sizing:border-box;color:#6d7888;font-size:10px}.binding-state i{width:7px;height:7px;flex:none;border-radius:50%;background:#d89714}.binding-state i.bound{background:#22a06b}.binding-state span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.binding-state.subflow-entry{justify-content:space-between;color:#7053b3}.binding-state.subflow-entry b{font-size:16px}.binding-state.muted{color:#8a94a3}.binding-handle{width:10px!important;height:10px!important;border:2px solid #fff!important;background:#7890ad!important;box-shadow:0 0 0 1px #7890ad}.binding-node-shell.device .binding-handle{background:#1677ff!important;box-shadow:0 0 0 1px #1677ff}.binding-node-shell :deep(.vue-flow__handle-left){left:-5px}.binding-node-shell :deep(.vue-flow__handle-right){right:-5px}
</style>
