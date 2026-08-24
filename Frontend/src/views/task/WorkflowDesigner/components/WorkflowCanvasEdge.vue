<template>
  <BaseEdge
    :id="id"
    :path="edgePath"
    :style="pathStyle"
    :marker-start="markerStart"
    :marker-end="markerEnd"
    :interaction-width="24"
  />
  <path
    :d="edgePath"
    class="edge-hit-stroke"
    fill="none"
    stroke="transparent"
    stroke-width="24"
    @pointerenter="onEnter"
    @pointerleave="onLeave"
  />
  <EdgeLabelRenderer>
    <div
      v-show="tooltipVisible"
      class="nodrag nopan edge-hover-tooltip"
      :style="labelStyle"
    >{{ tooltipText }}</div>
  </EdgeLabelRenderer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { BaseEdge, EdgeLabelRenderer, useVueFlow } from '@vue-flow/core'
import { workflowOrthogonalPath, workflowOrthogonalPoints } from '../../../../utils/workflowCanvas.js'

const props = withDefaults(defineProps<{
  id?: string
  source?: string
  target?: string
  sourceX?: number
  sourceY?: number
  targetX?: number
  targetY?: number
  sourcePosition?: string
  targetPosition?: string
  data?: Record<string, unknown>
  markerStart?: string
  markerEnd?: string
  style?: Record<string, unknown>
  selected?: boolean
  hideTooltips?: boolean
}>(), {
  sourceX: 0,
  sourceY: 0,
  targetX: 0,
  targetY: 0,
  sourcePosition: 'bottom',
  targetPosition: 'top',
  data: () => ({}),
  style: () => ({}),
  selected: false,
  hideTooltips: false,
})

const flow = useVueFlow()
const hovered = ref(false)
const tooltipArmed = ref(false)
let tooltipTimer: ReturnType<typeof setTimeout> | null = null

const obstacles = computed(() => {
  const list = flow.nodes?.value
    ?? flow.getNodes?.value
    ?? (typeof flow.getNodes === 'function' ? flow.getNodes() : [])
    ?? []
  return (Array.isArray(list) ? list : []).map((node: any) => ({
    id: node.id,
    x: Number(node.position?.x) || 0,
    y: Number(node.position?.y) || 0,
    width: Number(node.dimensions?.width || node.width || node.measured?.width) || 220,
    height: Number(node.dimensions?.height || node.height || node.measured?.height) || 108,
  }))
})

const routeParams = computed(() => ({
  sourceX: props.sourceX,
  sourceY: props.sourceY,
  targetX: props.targetX,
  targetY: props.targetY,
  sourcePosition: props.sourcePosition,
  targetPosition: props.targetPosition,
  laneOffset: Number(props.data?.laneOffset) || 0,
  wrapRank: Number(props.data?.wrapRank) || 0,
  wrapCount: Math.max(1, Number(props.data?.wrapCount) || 1),
  obstacles: obstacles.value,
  sourceId: props.source,
  targetId: props.target,
}))

const edgePath = computed(() => workflowOrthogonalPath(routeParams.value))
const tooltipText = computed(() => String(props.data?.tooltip || ''))
const tooltipVisible = computed(() => tooltipArmed.value && !props.hideTooltips && Boolean(tooltipText.value))
const connectionKind = computed(() => String(props.data?.connectionKind || 'INTERFACE'))

const labelStyle = computed(() => {
  const points = workflowOrthogonalPoints(routeParams.value)
  const a = points[2] || points[0]
  const b = points[3] || points[points.length - 1]
  const x = (a[0] + b[0]) / 2
  const y = Math.min(a[1], b[1]) - 10
  return {
    position: 'absolute',
    transform: `translate(-50%, -100%) translate(${x}px, ${y}px)`,
    pointerEvents: 'none',
  }
})

const pathStyle = computed(() => {
  const style = { ...(props.style || {}) }
  const active = props.selected || hovered.value
  const isData = connectionKind.value === 'PORT'
  return {
    ...style,
    fill: 'none',
    stroke: isData ? (active ? '#7a5fc0' : '#9a8ab5') : (active ? '#3b6fd4' : '#7c93b8'),
    strokeWidth: active ? 2.2 : (style.strokeWidth || 1.8),
  }
})

function onEnter() {
  hovered.value = true
  if (tooltipTimer) clearTimeout(tooltipTimer)
  tooltipTimer = setTimeout(() => { tooltipArmed.value = true }, 220)
}

function onLeave() {
  if (tooltipTimer) clearTimeout(tooltipTimer)
  tooltipTimer = null
  hovered.value = false
  tooltipArmed.value = false
}

onBeforeUnmount(onLeave)
</script>

<style>
.edge-hit-stroke {
  pointer-events: stroke;
  cursor: pointer;
}
.edge-hover-tooltip {
  z-index: 20;
  padding: 6px 10px;
  border-radius: 4px;
  background: #303133;
  color: #fff;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(15, 23, 42, .18);
}
</style>
