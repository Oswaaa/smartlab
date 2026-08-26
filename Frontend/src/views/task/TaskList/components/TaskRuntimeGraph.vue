<template>
  <WorkflowStageGraph
    class="runtime-graph"
    :nodes="graph.nodes"
    :interface-connections="interfaceConnections"
    :port-connections="workflow?.portConnections || []"
    :selected-node-name="resolvedSelectedName"
    :overlay-for="runtimeOverlay"
    :capabilities-for="capabilitiesFor"
    :decorate-edge="decorateRuntimeEdge"
    :legend-items="legendItems"
    @node-click="handleNodeClick"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { buildRuntimeGraph, runtimeNodeToNodeConnections } from '../../../../utils/workflowExecution.js'
import {
  nodeNameFromEditorId,
  parseHandleId,
  workflowInterfaceEdgeTooltip,
  workflowPortEdgeTooltip,
} from '../../../../utils/workflowCanvas.js'
import WorkflowStageGraph from '../../components/WorkflowStageGraph.vue'
import { nodeStatusLabel } from '../taskExecutionPresentation.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{
  workflow?: Item | null
  steps?: Item[]
  parentStepId?: number | string | null
  selectedStepId?: number | null
  activeNodeName?: string | null
  models?: Item[]
  bindings?: Item[]
}>(), { workflow: null, steps: () => [], parentStepId: null, selectedStepId: null, activeNodeName: null, models: () => [], bindings: () => [] })
const emit = defineEmits<{ 'select-step': [stepId: number | null, node: Item] }>()

const graph = computed(() => buildRuntimeGraph(props.workflow || {}, props.steps, { parentStepId: props.parentStepId }))
const interfaceConnections = computed(() => runtimeNodeToNodeConnections(props.workflow?.interfaceConnections))
const resolvedSelectedName = computed(() => {
  const byStep = graph.value.nodes.find((node: Item) => props.selectedStepId != null && node.stepId === props.selectedStepId)
  return byStep?.name || props.activeNodeName || null
})
const legendItems = [
  { label: '执行流', className: 'workflow' },
  { label: '数据流', className: 'data' },
  { label: '已走过', className: 'used' },
  { label: '子流程可进入内部', className: 'subflow' },
  { label: nodeStatusLabel('WAITING'), className: 'status-waiting' },
  { label: nodeStatusLabel('RUNNING'), className: 'status-running' },
  { label: nodeStatusLabel('SUCCEEDED'), className: 'status-succeeded' },
  { label: nodeStatusLabel('FAILED'), className: 'status-failed' },
]

function bindingFor(node: Item) {
  if (node?.nodeType !== 'DEV_NODE') return null
  const list = props.bindings || []
  return list.find(item => item.nodeName === node.name)
    || list.find(item => item.slotId && node.slotId && String(item.slotId) === String(node.slotId))
    || list.find(item => item.bindingKey && node.bindingKey && String(item.bindingKey) === String(node.bindingKey))
    || null
}

function runtimeOverlay(node: Item) {
  const status = node.status || 'WAITING'
  const binding = bindingFor(node)
  const bound = Boolean(binding?.deviceInstanceId)
  return {
    mode: 'runtime',
    status,
    statusLabel: nodeStatusLabel(status),
    showBinding: node?.nodeType === 'DEV_NODE',
    bound,
    boundLabel: bound ? (binding?.instanceName || binding?.deviceInstanceName || '已绑定') : '',
  }
}

function capabilitiesFor(node: Item) {
  if (node?.nodeType !== 'DEV_NODE') return []
  return props.models.find(model => Number(model.id ?? model.modelId) === Number(node.deviceModelId))?.capabilities || []
}

function decorateRuntimeEdge(edge: Item, nodesByName: Map<string, Item>) {
  if (!edge.sourceHandle || !edge.targetHandle) return edge
  const sourceName = nodeNameFromEditorId(edge.source)
  const targetName = nodeNameFromEditorId(edge.target)
  const target = nodesByName.get(targetName)
  const source = nodesByName.get(sourceName)
  let used = false
  let tooltip = edge.data?.tooltip
  let targetHandle
  let sourceHandle
  try {
    targetHandle = parseHandleId(edge.targetHandle)
    sourceHandle = parseHandleId(edge.sourceHandle)
  } catch {
    return edge
  }
  const connection = {
    source: { nodeName: sourceName, interfaceName: sourceHandle.kind === 'INTERFACE' ? sourceHandle.name : undefined, portName: sourceHandle.kind === 'PORT' ? sourceHandle.name : undefined },
    target: { nodeName: targetName, interfaceName: targetHandle.kind === 'INTERFACE' ? targetHandle.name : undefined, portName: targetHandle.kind === 'PORT' ? targetHandle.name : undefined },
  }
  if (targetHandle.kind === 'INTERFACE') {
    const snapshot = Array.isArray(target?.step?.interfaceInSnapshot) ? target.step.interfaceInSnapshot : []
    const outSnapshot = Array.isArray(source?.step?.interfaceOutSnapshot) ? source.step.interfaceOutSnapshot : []
    const accepted = snapshot.find((item: Item) => item?.interfaceName === targetHandle.name)
      || outSnapshot.find((item: Item) => item?.interfaceName === sourceHandle.name)
    used = typeof accepted?.signalName === 'string' && accepted.signalName.length > 0
    tooltip = workflowInterfaceEdgeTooltip(connection, graph.value.nodes, {
      signalName: accepted?.signalName ?? null,
      payload: accepted?.payload,
    })
  } else {
    const snapshot = Array.isArray(target?.step?.portInSnapshot) ? target.step.portInSnapshot : []
    const accepted = snapshot.find((item: Item) => item?.portName === targetHandle.name)
    used = accepted != null && accepted.value !== null && accepted.value !== undefined
    tooltip = workflowPortEdgeTooltip(connection, graph.value.nodes, used ? accepted.value : null)
  }
  return {
    ...edge,
    animated: used && target?.status === 'RUNNING' && targetHandle.kind === 'INTERFACE',
    class: [edge.class, used && targetHandle.kind === 'INTERFACE' ? 'used-runtime-edge' : ''].filter(Boolean).join(' '),
    data: { ...edge.data, used, tooltip },
  }
}

function handleNodeClick(node: Item) {
  emit('select-step', node.stepId ?? null, node)
}
</script>

<style scoped>
.runtime-graph {
  width: 100%;
  height: 100%;
  min-height: 0;
}
</style>
