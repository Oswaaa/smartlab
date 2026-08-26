<template>
  <section ref="hostRef" class="workflow-stage-graph">
    <VueFlow
      class="workflow-flow"
      :edge-types="edgeTypes"
      :default-edge-options="defaultEdgeOptions"
      :nodes-draggable="false"
      :nodes-connectable="false"
      :elements-selectable="true"
      :only-render-visible-elements="false"
      :fit-view-on-init="true"
      :min-zoom="minZoom"
      :max-zoom="maxZoom"
      :delete-key-code="null"
      @nodes-initialized="onNodesInitialized"
      @pane-ready="refreshViewport"
      @node-click="handleNodeClick"
    >
      <Background variant="lines" pattern-color="#f3f5f8" :gap="8" :size="1" />
      <Controls :show-interactive="false" :show-zoom="true" :show-fit-view="true" />
      <template #node-workflow="{ data, selected }">
        <WorkflowCanvasNode
          :node="nodeByName(data.nodeName)"
          :selected="selected || isSelected(data.nodeName)"
          :issues="issuesForNode(data.nodeName)"
          :device-capabilities="capabilitiesForNode(data.nodeName)"
          :overlay="overlayForNode(data.nodeName)"
          readonly
        />
      </template>
      <template #edge-workflow="edgeProps">
        <WorkflowCanvasEdge v-bind="edgeProps" />
      </template>
    </VueFlow>
    <div v-if="$slots.legend || legendItems.length" class="graph-legend">
      <slot name="legend">
        <span v-for="item in legendItems" :key="item.label"><i :class="item.className"></i>{{ item.label }}</span>
      </slot>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import { buildFlowEdges, editorNodeId, workflowCanvasNodeSize } from '../../../utils/workflowCanvas.js'
import WorkflowCanvasNode from '../WorkflowDesigner/components/WorkflowCanvasNode.vue'
import WorkflowCanvasEdge from '../WorkflowDesigner/components/WorkflowCanvasEdge.vue'

type Item = Record<string, any>
const props = withDefaults(defineProps<{
  nodes?: Item[]
  interfaceConnections?: Item[]
  portConnections?: Item[]
  selectedNodeName?: string | null
  minZoom?: number
  maxZoom?: number
  legendItems?: Array<{ label: string, className?: string }>
  overlayFor?: (node: Item) => Item
  capabilitiesFor?: (node: Item) => Item[]
  issuesFor?: (node: Item) => Item[]
  decorateEdge?: (edge: Item, nodesByName: Map<string, Item>) => Item
}>(), {
  nodes: () => [],
  interfaceConnections: () => [],
  portConnections: () => [],
  selectedNodeName: null,
  minZoom: 0.35,
  maxZoom: 1.6,
  legendItems: () => [],
  // 函数类型 prop 的默认值会被 Vue 原样使用（不会作为工厂调用），
  // 因此这里必须直接写目标函数，不能再包一层工厂。
  overlayFor: () => ({}),
  capabilitiesFor: () => [],
  issuesFor: () => [],
  decorateEdge: (edge: Item) => edge,
})
const emit = defineEmits<{ 'node-click': [node: Item] }>()

// 通过 useVueFlow 创建并向下 provide 一个独立的 store 实例，随后直接用
// setNodes/setEdges 驱动它。不要改回 v-model:edges：Vue Flow 内部的
// model<->store 双向同步基于 watchPausable，在 store 回写 model 的暂停窗口内
// 对 model 的赋值会被丢弃，导致边永远进不了 store（连线不显示）。
const { fitView, updateNodeInternals, setNodes, setEdges, getNodes } = useVueFlow()
const edgeTypes = { workflow: markRaw(WorkflowCanvasEdge) }
const defaultEdgeOptions = { type: 'workflow', markerEnd: 'arrowclosed', style: { stroke: '#7c93b8', strokeWidth: 1.8 } }
const nodesByName = computed(() => new Map(props.nodes.map(node => [node.name, node])))
const hostRef = ref<HTMLElement | null>(null)
const refreshTimers: number[] = []

function graphNodes() {
  return props.nodes.map((node: Item, index: number) => {
    const size = workflowCanvasNodeSize(node || {})
    return {
      id: editorNodeId(node.name),
      type: 'workflow',
      position: node.position || { x: 80 + index * 280, y: 120 },
      selectable: true,
      draggable: false,
      width: size.width,
      height: size.height,
      style: { width: `${size.width}px`, height: `${size.height}px` },
      data: { nodeName: node.name, nodeType: node.nodeType, functionType: node.functionType },
    }
  })
}

function graphEdges() {
  return buildFlowEdges(
    props.interfaceConnections,
    props.portConnections,
    props.nodes,
  ).map((edge: Item) => props.decorateEdge(edge, nodesByName.value))
}

function refreshViewport() {
  nextTick(() => {
    try {
      const ids = getNodes.value.map((node: Item) => node.id).filter(Boolean)
      if (ids.length) updateNodeInternals(ids)
      else updateNodeInternals()
      fitView({ padding: 0.18, duration: 0 })
    } catch {
      // Vue Flow viewport is not ready yet
    }
  })
}

function scheduleViewportRefresh() {
  refreshTimers.splice(0).forEach(timer => window.clearTimeout(timer))
  refreshViewport()
  for (const ms of [48, 160, 360]) {
    refreshTimers.push(window.setTimeout(refreshViewport, ms) as unknown as number)
  }
}

function rebuildCanvas() {
  setNodes(graphNodes())
  setEdges(graphEdges())
  scheduleViewportRefresh()
}

function onNodesInitialized() {
  scheduleViewportRefresh()
}

watch(
  () => [
    props.nodes,
    props.interfaceConnections,
    props.portConnections,
  ],
  rebuildCanvas,
  { immediate: true, deep: true },
)

onMounted(() => {
  const el = hostRef.value
  if (!el || typeof ResizeObserver === 'undefined') return
  const observer = new ResizeObserver(() => scheduleViewportRefresh())
  observer.observe(el)
  onBeforeUnmount(() => observer.disconnect())
})
onBeforeUnmount(() => {
  refreshTimers.forEach(timer => window.clearTimeout(timer))
})

function nodeByName(name: string) {
  return nodesByName.value.get(name) || { name }
}
function isSelected(name: string) {
  return Boolean(props.selectedNodeName) && props.selectedNodeName === name
}
function overlayForNode(name: string) {
  return props.overlayFor(nodeByName(name)) || {}
}
function capabilitiesForNode(name: string) {
  return props.capabilitiesFor(nodeByName(name)) || []
}
function issuesForNode(name: string) {
  return props.issuesFor(nodeByName(name)) || []
}
function handleNodeClick({ node }: { node: Item }) {
  emit('node-click', nodeByName(node.data?.nodeName))
}
</script>

<style scoped>
.workflow-stage-graph {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 280px;
  overflow: hidden;
}
.workflow-flow {
  width: 100%;
  height: 100%;
}
.workflow-stage-graph :deep(.vue-flow__pane) {
  cursor: default;
}
.workflow-stage-graph :deep(.vue-flow__edge-path) {
  stroke: #7c93b8;
  stroke-width: 1.8;
  fill: none;
}
.workflow-stage-graph :deep(.execution-edge .vue-flow__edge-path) {
  stroke: #7c93b8;
}
.workflow-stage-graph :deep(.data-edge .vue-flow__edge-path) {
  stroke: #9a8ab5;
  stroke-dasharray: 6 5;
}
.workflow-stage-graph :deep(.used-runtime-edge .vue-flow__edge-path) {
  stroke: #52c41a;
}
.workflow-stage-graph :deep(.vue-flow__controls) {
  overflow: hidden;
  border: 1px solid var(--sl-border-input, #cbd5e1);
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
}
.graph-legend {
  position: absolute;
  right: 10px;
  bottom: 10px;
  z-index: 5;
  display: flex;
  max-width: 520px;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 4px 8px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--sl-text-secondary, #64748b);
  font-size: 10px;
}
.graph-legend span {
  display: flex;
  align-items: center;
  gap: 4px;
}
.graph-legend i {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: #aeb7c4;
}
.graph-legend i.device { background: var(--sl-primary, #2563eb); }
.graph-legend i.subflow { background: #7053b3; }
.graph-legend i.workflow { width: 14px; height: 0; border-radius: 0; border-top: 2px solid #7c93b8; background: transparent; }
.graph-legend i.data { width: 14px; height: 0; border-radius: 0; border-top: 2px dashed #9a8ab5; background: transparent; }
.graph-legend i.used { background: #52c41a; }
.graph-legend i.status-running { background: #1677ff; border-radius: 50%; }
.graph-legend i.status-succeeded { background: #52c41a; border-radius: 50%; }
.graph-legend i.status-failed { background: #ff4d4f; border-radius: 50%; }
.graph-legend i.status-waiting { background: #cbd5e1; border-radius: 50%; }
</style>
