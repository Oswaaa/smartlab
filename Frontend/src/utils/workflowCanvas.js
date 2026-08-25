import { workflowTriggerActions, workflowTriggerConditions } from './workflowNodeDefinition.js'

const LAYOUT_PREFIX = 'smartlab:workflow-layout:'

export function editorNodeId(nodeName) {
  return `workflow-node:${encodeURIComponent(nodeName)}`
}

export function nodeNameFromEditorId(id) {
  const value = String(id || '')
  const prefix = 'workflow-node:'
  if (!value.startsWith(prefix)) return value
  return decodeURIComponent(value.slice(prefix.length))
}

export function layoutKey(workflowId, draftKey = 'draft') {
  return `${LAYOUT_PREFIX}${workflowId ?? draftKey}`
}

export const WORKFLOW_LAYOUT_VERSION = 2

export function unwrapStoredLayout(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {}
  if (Number(raw.version) === WORKFLOW_LAYOUT_VERSION && raw.nodes && typeof raw.nodes === 'object') {
    return raw.nodes
  }
  return {}
}

export function wrapStoredLayout(nodes = []) {
  return {
    version: WORKFLOW_LAYOUT_VERSION,
    nodes: serializeLayout(nodes),
  }
}

export function workflowCanvasNodeSize(node = {}) {
  const workflowInterfaces = (node.interfaces || []).filter(item => item.interfaceType === 'WORKFLOW')
  const inCount = workflowInterfaces.filter(item => item.direction === 'IN').length
  const outCount = workflowInterfaces.filter(item => item.direction === 'OUT').length
  const maxSideCount = Math.max(inCount, outCount)
  const maxPortCount = Math.max(
    (node.ports || []).filter(item => item.direction === 'IN').length,
    (node.ports || []).filter(item => item.direction === 'OUT').length,
  )
  const width = Math.max(220, 80 + maxPortCount * 40)
  if (node.functionType === 'START' || node.functionType === 'END') {
    return { width, height: Math.max(44, 20 + maxSideCount * 24) }
  }
  if (node.functionType === 'BRANCH') {
    return { width, height: Math.max(96, 56 + Math.max(1, outCount) * 30, 48 + inCount * 24) }
  }
  return { width, height: Math.max(118, 48 + maxSideCount * 24) }
}

export function workflowSideHandleTop(node = {}, index, total) {
  const size = workflowCanvasNodeSize(node)
  const compact = node.functionType === 'START' || node.functionType === 'END'
  const count = Math.max(1, Number(total) || 1)
  const i = Number(index) || 0
  if (compact) {
    const center = size.height / 2
    if (count <= 1) return Math.round(center)
    const spread = Math.min(24 * (count - 1), size.height - 16)
    const start = center - spread / 2
    return Math.round(start + spread * i / (count - 1))
  }
  const available = Math.max(24, size.height - 52)
  return Math.round(26 + available * (i + 1) / (count + 1))
}

export function workflowOutputHandleTop(node = {}, index, outputCount) {
  if (node.functionType === 'BRANCH') return 65 + 30 * (Number(index) || 0)
  return workflowSideHandleTop(node, index, outputCount)
}

export function workflowPortHandlePercent(index, total) {
  const count = Math.max(1, Number(total) || 1)
  return Math.round(((Number(index) || 0) + 1) * 100 / (count + 1))
}

export function workflowInterfaceTooltip(interfaceDefinition = {}) {
  const direction = interfaceDefinition.direction === 'OUT' ? '控制输出' : '控制输入'
  return `${direction} · ${interfaceDefinition.name || '未命名接口'}`
}

export function workflowPortTooltip(port = {}) {
  const direction = port.direction === 'OUT' ? '数据输出' : '数据输入'
  return [direction, port.name || '未命名端口', port.internalVariableName || '未绑定变量'].filter(Boolean).join(' · ')
}

export function workflowPortEdgeTooltip(connection = {}, nodes = [], runtimeValue) {
  const node = (nodes || []).find(item => item.name === connection?.source?.nodeName)
  const port = (node?.ports || []).find(item => item.name === connection?.source?.portName)
  const name = port?.internalVariableName || '未绑定变量'
  return `${name} · ${formatEdgeValue(runtimeValue)}`
}

export function workflowInterfaceEdgeTooltip(connection = {}) {
  const source = connection?.source?.nodeName
  const target = connection?.target?.nodeName
  if (source && target) return `${source} → ${target}`
  return '执行流'
}

function formatEdgeValue(value) {
  if (value == null || value === '') return '空'
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return '空'
    }
  }
  return String(value)
}

export function workflowDeviceCapabilityPresentation(node = {}, capabilities = []) {
  const capability = node.capability || {}
  const capabilityName = capability.capabilityName
  if (!capabilityName) return { title: '未选择能力', detail: '' }
  const definition = (capabilities || []).find(item => item.capabilityName === capabilityName)
  const title = capability.displayName
    || capability.capabilityDisplayName
    || definition?.displayName
    || capabilityName
  const parameters = definition?.parameters?.length
    ? definition.parameters
    : Object.keys(capability.capabilityParameters || {}).map(name => ({ name, displayName: name }))
  const detail = parameters.map(item => item.displayName || item.name).filter(Boolean).join('、')
  return { title, detail }
}

export function workflowDeviceCapabilitySummary(node = {}, capabilities = []) {
  const { title, detail } = workflowDeviceCapabilityPresentation(node, capabilities)
  return detail ? `${title} · ${detail}` : title
}

const ROUTE_STUB = 14
const ROUTE_PAD = 12
const ROUTE_LANE = 14
export const GRID_SNAP = 12

const TRIGGER_OBJECT_LABELS = {
  signalName: '接收信号',
  nodeLifecycleState: '节点生命周期',
  taskLifecycleState: '任务生命周期',
  'payload.stateName': '指令状态',
}

const TRIGGER_OPERATOR_LABELS = {
  '=': '=',
  '!=': '≠',
  '>': '>',
  '<': '<',
  '>=': '≥',
  '<=': '≤',
  IN: '属于',
}

export function formatWorkflowValue(value) {
  if (value == null || value === '') return '空'
  if (Array.isArray(value)) return value.map(formatWorkflowValue).join('、')
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return '空'
    }
  }
  return String(value)
}

export const WORKFLOW_TRIGGER_CONDITION_SUMMARY_HINT = '条件摘要会展开同一触发器下的全部判定，用“且”连接；变量等业务条件排在节点/任务生命周期和信号之前。'

export function formatWorkflowTriggerConditionText(trigger = {}) {
  const systemObjects = new Set(['nodeLifecycleState', 'taskLifecycleState', 'signalName', 'payload.stateName'])
  const conditions = workflowTriggerConditions(trigger.condition)
  if (!conditions.length) return ''
  const sorted = [...conditions].sort((a, b) => {
    const isSystemA = systemObjects.has(a.object)
    const isSystemB = systemObjects.has(b.object)
    if (!isSystemA && isSystemB) return -1
    if (isSystemA && !isSystemB) return 1
    return 0
  })
  return sorted.map(condition => {
    const object = TRIGGER_OBJECT_LABELS[condition.object] || condition.object || '条件'
    const operator = TRIGGER_OPERATOR_LABELS[condition.operator] || condition.operator || '='
    return `${object} ${operator} ${formatWorkflowValue(condition.threshold)}`
  }).join(' 且 ')
}

function triggerActionPayload(action = {}) {
  return action.payload && typeof action.payload === 'object' && !Array.isArray(action.payload)
    ? action.payload
    : action
}

export function formatWorkflowTriggerActionText(action = {}) {
  const name = action.actionName || action.actionType || 'ACTION'
  const payload = triggerActionPayload(action)
  if (name === 'EMIT') {
    const signal = payload.signalName ? `信号 ${payload.signalName}` : '信号（未指定）'
    return payload.targetInterfaceName
      ? `发出接口 ${payload.targetInterfaceName}，${signal}`
      : `发出${signal}`
  }
  if (name === 'UPDATE') {
    const target = payload.targetName || '（未指定目标）'
    if (payload.updateType === 'NODE_LIFECYCLE') return `UPDATE 节点生命周期 → ${target}`
    const valueText = Object.hasOwn(payload, 'valueExpression') && payload.valueExpression
      ? `表达式 ${payload.valueExpression}`
      : Object.hasOwn(payload, 'value')
        ? formatWorkflowValue(payload.value)
        : '（未指定目标值）'
    return `UPDATE ${target} → ${valueText}`
  }
  return name
}

export function formatWorkflowTriggerActionLabel(trigger = {}) {
  const actions = workflowTriggerActions(trigger)
  if (!actions.length) return '未配置动作'
  return actions.map(formatWorkflowTriggerActionText).join('；')
}

export function workflowTriggerSummaries(interfaceItem = {}, previewCount = 2) {
  const items = (interfaceItem.bindingTriggers || []).map((trigger, index) => ({
    index,
    title: `${index + 1}#`,
    actionLabel: formatWorkflowTriggerActionLabel(trigger),
    conditionText: formatWorkflowTriggerConditionText(trigger) || '未配置条件',
  }))
  const preview = Math.max(0, Number(previewCount) || 0)
  return {
    items,
    previewItems: items.slice(0, preview),
    restCount: Math.max(0, items.length - preview),
  }
}

export function formatWorkflowTriggerText(interfaceItem = {}) {
  return (interfaceItem.bindingTriggers || [])
    .map(trigger => formatWorkflowTriggerConditionText(trigger))
    .filter(Boolean)
    .join('；')
}

export function workflowPortValueSummary(node = {}, portName) {
  const port = (node.ports || []).find(item => item.name === portName) || {}
  const variable = (node.internalVariables || []).find(item => item.name === port.internalVariableName)
  return {
    portName: port.name || portName || '未命名端口',
    variableName: port.internalVariableName || '未绑定变量',
    dataType: variable?.dataType || '',
    value: variable && Object.hasOwn(variable, 'initialValue') ? formatWorkflowValue(variable.initialValue) : '空',
  }
}

export function workflowNodeSnapAnchor(node = {}) {
  const size = workflowCanvasNodeSize(node)
  const workflow = (node.interfaces || []).filter(item => item.interfaceType === 'WORKFLOW')
  const ins = workflow.filter(item => item.direction === 'IN')
  const outs = workflow.filter(item => item.direction === 'OUT')
  if (node.functionType === 'START' || node.functionType === 'END') return size.height / 2
  if (node.functionType === 'BRANCH' && ins.length === 0 && outs.length > 0) return 65
  const count = ins.length || outs.length || 1
  const available = Math.max(24, size.height - 52)
  return 26 + available * 1 / (count + 1)
}

export function snapWorkflowNodePosition(node, x, y, grid = GRID_SNAP) {
  const anchor = workflowNodeSnapAnchor(node)
  return {
    x: Math.round(Number(x) / grid) * grid,
    y: Math.round((Number(y) + anchor) / grid) * grid - anchor,
  }
}

export function edgeLaneOffset(index, count, step = 28) {
  if (count <= 1) return 0
  const spread = Math.min(140, (count - 1) * step)
  return (index - (count - 1) / 2) * (spread / (count - 1))
}

export function workflowOrthogonalPoints(params = {}) {
  const src = String(params.sourcePosition || 'bottom').toLowerCase()
  const tgt = String(params.targetPosition || 'top').toLowerCase()
  const startX = Number(params.sourceX) || 0
  const startY = Number(params.sourceY) || 0
  const endX = Number(params.targetX) || 0
  const endY = Number(params.targetY) || 0
  const { boxes, sourceBox, targetBox } = routeBoxes(params, startX, startY, endX, endY, src, tgt)
  const wrapRank = Number(params.wrapRank) || 0
  const wrapCount = Math.max(1, Number(params.wrapCount) || 1)
  const laneOffset = Number(params.laneOffset) || 0

  if (src === 'bottom' && tgt === 'top') {
    return routeBottomToTop({ startX, startY, endX, endY, wrapRank, wrapCount, boxes, sourceBox, targetBox })
  }
  if (src === 'right' && tgt === 'left') {
    return routeRightToLeft({ startX, startY, endX, endY, laneOffset, boxes, sourceBox, targetBox })
  }
  if (src === 'top' || src === 'bottom') {
    return routeBottomToTop({
      startX, startY, endX, endY, wrapRank, wrapCount, boxes, sourceBox, targetBox,
    })
  }
  return routeRightToLeft({ startX, startY, endX, endY, laneOffset, boxes, sourceBox, targetBox })
}

export function workflowOrthogonalPath(params) {
  return roundedPolylinePath(workflowOrthogonalPoints(params), ROUTE_CORNER)
}

const ROUTE_CORNER = 10

function roundedPolylinePath(points, radius) {
  if (points.length < 3) {
    return points.map((point, index) => `${index ? 'L' : 'M'} ${point[0]} ${point[1]}`).join(' ')
  }
  const parts = [`M ${points[0][0]} ${points[0][1]}`]
  for (let index = 1; index < points.length - 1; index += 1) {
    const [px, py] = points[index - 1]
    const [cx, cy] = points[index]
    const [nx, ny] = points[index + 1]
    const inX = Math.sign(cx - px)
    const inY = Math.sign(cy - py)
    const outX = Math.sign(nx - cx)
    const outY = Math.sign(ny - cy)
    const inLen = Math.abs(cx - px) + Math.abs(cy - py)
    const outLen = Math.abs(nx - cx) + Math.abs(ny - cy)
    const r = Math.max(0, Math.min(radius, inLen / 2, outLen / 2))
    if (!r) {
      parts.push(`L ${cx} ${cy}`)
      continue
    }
    parts.push(`L ${cx - inX * r} ${cy - inY * r} Q ${cx} ${cy} ${cx + outX * r} ${cy + outY * r}`)
  }
  const last = points[points.length - 1]
  parts.push(`L ${last[0]} ${last[1]}`)
  return parts.join(' ')
}

export function polylineHitsNodeInteriors(points = [], nodes = []) {
  for (let index = 0; index < points.length - 1; index += 1) {
    const [x1, y1] = points[index]
    const [x2, y2] = points[index + 1]
    if (nodes.some(node => segmentHitsNodeInterior(x1, y1, x2, y2, node))) return true
  }
  return false
}

export function buildWorkflowAutoLayout(nodes = [], interfaceConnections = [], portConnections = []) {
  const validNodes = nodes.filter(node => node?.name)
  const nodesByName = new Map(validNodes.map(node => [node.name, node]))
  const originalOrder = new Map(validNodes.map((node, index) => [node.name, index]))
  const outgoing = new Map(validNodes.map(node => [node.name, new Set()]))
  const incoming = new Map(validNodes.map(node => [node.name, new Set()]))
  const outgoingByInterface = new Map()

  const addExecution = connection => {
    if (connection?.connectionType && connection.connectionType !== 'NODE_TO_NODE') return
    if (connection?.target?.deviceModelId && !connection?.target?.nodeName) return
    const source = connection?.source?.nodeName
    const target = connection?.target?.nodeName
    if (!nodesByName.has(source) || !nodesByName.has(target) || source === target) return
    outgoing.get(source).add(target)
    incoming.get(target).add(source)
    const key = `${source}\u0000${connection.source?.interfaceName || ''}`
    if (!outgoingByInterface.has(key)) outgoingByInterface.set(key, [])
    if (!outgoingByInterface.get(key).includes(target)) outgoingByInterface.get(key).push(target)
  }
  interfaceConnections.forEach(addExecution)

  const columns = new Map(validNodes.map(node => [node.name, 0]))
  const indegree = new Map(validNodes.map(node => [node.name, incoming.get(node.name).size]))
  const queue = validNodes.filter(node => indegree.get(node.name) === 0).map(node => node.name)
  const processed = new Set()
  while (queue.length) {
    queue.sort((left, right) => compareLayoutRoots(nodesByName.get(left), nodesByName.get(right), originalOrder))
    const current = queue.shift()
    if (processed.has(current)) continue
    processed.add(current)
    for (const target of outgoing.get(current)) {
      columns.set(target, Math.max(columns.get(target), columns.get(current) + 1))
      indegree.set(target, indegree.get(target) - 1)
      if (indegree.get(target) === 0) queue.push(target)
    }
  }

  const inExecution = new Set()
  for (const node of validNodes) {
    if (outgoing.get(node.name).size || incoming.get(node.name).size) inExecution.add(node.name)
  }

  const splitArms = node => {
    if (isSplittingBranch(node)) {
      return workflowOutInterfaces(node).map(item => ({
        targets: outgoingByInterface.get(`${node.name}\u0000${item.name}`) || [],
      }))
    }
    const exclusive = [...outgoing.get(node.name)].filter(name => incoming.get(name).size <= 1)
    if (exclusive.length >= 2) {
      exclusive.sort((left, right) => originalOrder.get(left) - originalOrder.get(right))
      return exclusive.map(name => ({ targets: [name] }))
    }
    return null
  }

  const spanMemo = new Map()
  const nodeSpan = (name, visiting = new Set()) => {
    if (spanMemo.has(name)) return spanMemo.get(name)
    if (visiting.has(name)) return 1
    visiting.add(name)
    const node = nodesByName.get(name)
    const arms = splitArms(node)
    const span = arms
      ? arms.reduce((sum, arm) => sum + armSpan(arm, visiting), 0) || 1
      : Math.max(1, ...[...outgoing.get(name)].filter(target => incoming.get(target).size <= 1).map(target => nodeSpan(target, visiting)), 1)
    visiting.delete(name)
    spanMemo.set(name, span)
    return span
  }
  const armSpan = (arm, visiting) => {
    if (!arm.targets.length) return 1
    return Math.max(1, ...arm.targets.map(target => incoming.get(target).size > 1 ? 1 : nodeSpan(target, visiting)))
  }

  const bandStart = new Map()
  const placed = new Set()
  const placeTree = (name, start) => {
    if (placed.has(name)) return
    placed.add(name)
    bandStart.set(name, start)
    const node = nodesByName.get(name)
    const arms = splitArms(node)
    if (arms) {
      let cursor = start
      for (const arm of arms) {
        const span = armSpan(arm, new Set())
        for (const target of arm.targets) {
          if (incoming.get(target).size > 1) continue
          placeTree(target, cursor)
        }
        cursor += span
      }
      return
    }
    for (const target of outgoing.get(name)) {
      if (incoming.get(target).size > 1) continue
      placeTree(target, start)
    }
  }

  const executionRoots = validNodes
    .filter(node => incoming.get(node.name).size === 0 && (inExecution.has(node.name) || node.functionType === 'START'))
    .sort((left, right) => compareLayoutRoots(left, right, originalOrder))
  let componentBand = 0
  for (const root of executionRoots) {
    if (placed.has(root.name)) continue
    placeTree(root.name, componentBand)
    componentBand += nodeSpan(root.name)
  }

  let progress = true
  while (progress) {
    progress = false
    const remaining = validNodes
      .filter(node => !placed.has(node.name) && inExecution.has(node.name))
      .sort((left, right) => (columns.get(left.name) || 0) - (columns.get(right.name) || 0) || originalOrder.get(left.name) - originalOrder.get(right.name))
    for (const node of remaining) {
      const preds = [...incoming.get(node.name)]
      if (!preds.length || !preds.every(name => placed.has(name))) continue
      const centers = preds.map(name => (bandStart.get(name) || 0) + (nodeSpan(name) - 1) / 2)
      const span = nodeSpan(node.name)
      placeTree(node.name, centers.reduce((sum, value) => sum + value, 0) / centers.length - (span - 1) / 2)
      progress = true
    }
  }

  const portPairs = (portConnections || []).flatMap(connection => {
    const source = connection?.source?.nodeName
    const target = connection?.target?.nodeName
    if (!nodesByName.has(source) || !nodesByName.has(target) || source === target) return []
    return [[source, target], [target, source]]
  })
  progress = true
  while (progress) {
    progress = false
    for (const node of validNodes) {
      if (placed.has(node.name)) continue
      const neighbor = portPairs.find(pair => pair[0] === node.name && placed.has(pair[1]))?.[1]
      if (!neighbor) continue
      bandStart.set(node.name, bandStart.get(neighbor) || 0)
      columns.set(node.name, (columns.get(neighbor) || 0) + 1)
      placed.add(node.name)
      progress = true
    }
  }

  let leftoverBand = Math.max(0, ...[...bandStart.values(), componentBand])
  let leftoverColumn = Math.max(0, ...columns.values()) + 1
  const startName = validNodes.find(node => node.functionType === 'START')?.name
  const mainBand = startName && bandStart.has(startName)
    ? (bandStart.get(startName) || 0) + (nodeSpan(startName) - 1) / 2
    : 0
  for (const node of validNodes) {
    if (placed.has(node.name)) continue
    const alignWithStart = node.functionType === 'END'
    bandStart.set(node.name, alignWithStart ? mainBand : leftoverBand)
    columns.set(node.name, leftoverColumn)
    placed.add(node.name)
    leftoverColumn += 1
    if (!alignWithStart) leftoverBand += 1
  }

  const sizes = new Map(validNodes.map(node => [node.name, workflowCanvasNodeSize(node)]))
  const columnIds = [...new Set([...columns.values()])].sort((left, right) => left - right)
  const columnWidth = column => Math.max(0, ...validNodes.filter(node => columns.get(node.name) === column).map(node => sizes.get(node.name).width))
  const rowPitch = Math.max(168, ...[...sizes.values()].map(size => size.height + 72))
  const xByColumn = new Map()
  let x = 80
  for (const column of columnIds) {
    xByColumn.set(column, x)
    const names = validNodes.filter(node => columns.get(node.name) === column).map(node => node.name)
    const nextNames = validNodes.filter(node => columns.get(node.name) === column + 1).map(node => node.name)
    x += columnWidth(column) + 96 + Math.min(72, Math.max(0, maxParallelPortsBetween(names, nextNames, portConnections) - 1) * 18)
  }

  const layout = {}
  for (const node of validNodes) {
    const span = nodeSpan(node.name)
    const start = bandStart.get(node.name) || 0
    const handleY = Math.round((96 + (start + (span - 1) / 2) * rowPitch) / GRID_SNAP) * GRID_SNAP
    layout[node.name] = {
      x: Math.round((xByColumn.get(columns.get(node.name)) || 80) / GRID_SNAP) * GRID_SNAP,
      y: handleY - workflowNodeSnapAnchor(node),
    }
  }
  separateOverlappingNodes(layout, sizes)
  alignEndNodesWithStart(layout, validNodes)
  for (const node of validNodes) {
    const position = layout[node.name]
    layout[node.name] = snapWorkflowNodePosition(node, position.x, position.y)
  }
  return layout
}

function workflowOutInterfaces(node) {
  return (node?.interfaces || []).filter(item => item.interfaceType === 'WORKFLOW' && item.direction === 'OUT')
}

function isSplittingBranch(node) {
  return node?.functionType === 'BRANCH' && workflowOutInterfaces(node).length >= 2
}

function compareLayoutRoots(left, right, originalOrder) {
  const rank = node => node?.functionType === 'START' ? 0 : 1
  return rank(left) - rank(right) || originalOrder.get(left?.name) - originalOrder.get(right?.name)
}

function alignEndNodesWithStart(layout, nodes = []) {
  const start = nodes.find(node => node.functionType === 'START')
  const startPos = layout[start?.name]
  if (!startPos) return
  const handleY = startPos.y + workflowNodeSnapAnchor(start)
  for (const node of nodes) {
    if (node.functionType !== 'END' || !layout[node.name]) continue
    layout[node.name] = { ...layout[node.name], y: handleY - workflowNodeSnapAnchor(node) }
  }
}

function separateOverlappingNodes(layout, sizes) {
  const names = Object.keys(layout)
  for (let pass = 0; pass < 4; pass += 1) {
    for (let i = 0; i < names.length; i += 1) {
      for (let j = i + 1; j < names.length; j += 1) {
        const left = layout[names[i]]
        const right = layout[names[j]]
        const leftSize = sizes.get(names[i])
        const rightSize = sizes.get(names[j])
        const overlapX = Math.min(left.x + leftSize.width, right.x + rightSize.width) - Math.max(left.x, right.x)
        const overlapY = Math.min(left.y + leftSize.height, right.y + rightSize.height) - Math.max(left.y, right.y)
        if (overlapX <= 0 || overlapY <= 0) continue
        if (left.y <= right.y) right.y = Math.round(left.y + leftSize.height + 48)
        else left.y = Math.round(right.y + rightSize.height + 48)
      }
    }
  }
}

function maxParallelPortsBetween(sourceNames, targetNames, portConnections = []) {
  const sources = new Set(sourceNames)
  const targets = new Set(targetNames)
  const counts = new Map()
  for (const connection of portConnections) {
    const source = connection?.source?.nodeName
    const target = connection?.target?.nodeName
    if (!sources.has(source) || !targets.has(target)) continue
    const key = `${source}\u0000${target}`
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return Math.max(0, ...counts.values(), 0)
}

function routeBoxes(params, startX, startY, endX, endY, sourcePosition, targetPosition) {
  const boxes = [...(params.obstacles || [])].map(normalizeObstacle).filter(Boolean)
  const sourceBox = endpointBox(startX, startY, sourcePosition, params.sourceId, boxes)
  const targetBox = endpointBox(endX, endY, targetPosition, params.targetId, boxes)
  const known = new Set(boxes.map(item => item.id).filter(Boolean))
  if (sourceBox.id && !known.has(sourceBox.id)) boxes.push(sourceBox)
  if (targetBox.id && targetBox.id !== sourceBox.id && !known.has(targetBox.id)) boxes.push(targetBox)
  return { boxes, sourceBox, targetBox }
}

function normalizeObstacle(item) {
  if (!item) return null
  return {
    id: item.id,
    x: Number(item.x) || 0,
    y: Number(item.y) || 0,
    width: Math.max(1, Number(item.width) || 220),
    height: Math.max(1, Number(item.height) || 108),
  }
}

function endpointBox(x, y, position, id, boxes) {
  if (id) {
    const found = boxes.find(item => item.id === id)
    if (found) return found
  }
  const touching = boxes.find(item => pointTouchesRect(x, y, item, 3))
  if (touching) return touching
  return inferEndpointBox(x, y, position, id || `inferred:${position}:${x}:${y}`)
}

function inferEndpointBox(x, y, position, id) {
  const width = 220
  const height = 108
  const pos = String(position || '').toLowerCase()
  if (pos === 'right') return { id, x: x - width, y: y - height / 2, width, height }
  if (pos === 'left') return { id, x, y: y - height / 2, width, height }
  if (pos === 'bottom') return { id, x: x - width / 2, y: y - height, width, height }
  if (pos === 'top') return { id, x: x - width / 2, y: y, width, height }
  return { id, x: x - width / 2, y: y - height / 2, width, height }
}

function pointTouchesRect(x, y, box, pad) {
  return x >= box.x - pad && x <= box.x + box.width + pad && y >= box.y - pad && y <= box.y + box.height + pad
}

function routeBottomToTop({ startX, startY, endX, endY, wrapRank, wrapCount, boxes, sourceBox, targetBox }) {
  const sourceBottom = sourceBox.y + sourceBox.height
  const targetTop = targetBox.y
  const stackedWithGap = targetTop >= sourceBottom + 8
    && rangesOverlap(sourceBox.x, sourceBox.x + sourceBox.width, targetBox.x, targetBox.x + targetBox.width)
  if (stackedWithGap) {
    const gapTop = sourceBottom + ROUTE_PAD
    const gapBottom = Math.max(gapTop + 8, targetTop - ROUTE_PAD)
    const midY = findHorizontalChannel(
      gapTop + (gapBottom - gapTop) * (wrapRank + 1) / (wrapCount + 1),
      startX,
      endX,
      boxes,
      false,
    )
    return simplifyPolyline([[startX, startY], [startX, midY], [endX, midY], [endX, endY]])
  }

  const lane = wrapRank * ROUTE_LANE
  const preferredX = gapChannelX(sourceBox, targetBox, wrapRank)
    ?? (Math.min(sourceBox.x, targetBox.x) - ROUTE_PAD - wrapRank * ROUTE_LANE)
  let drop = ROUTE_STUB + lane
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const exitY = startY + drop
    const approachY = endY - drop
    const channelX = findVerticalChannel(preferredX, exitY, approachY, boxes)
    if (!horizontalBlocked(exitY, startX, channelX, boxes)
      && !verticalBlocked(channelX, exitY, approachY, boxes)
      && !horizontalBlocked(approachY, channelX, endX, boxes)) {
      return simplifyPolyline([
        [startX, startY],
        [startX, exitY],
        [channelX, exitY],
        [channelX, approachY],
        [endX, approachY],
        [endX, endY],
      ])
    }
    drop += 10
  }
  const exitY = startY + drop
  const approachY = endY - drop
  const channelX = findVerticalChannel(preferredX, exitY, approachY, boxes)
  return simplifyPolyline([
    [startX, startY],
    [startX, exitY],
    [channelX, exitY],
    [channelX, approachY],
    [endX, approachY],
    [endX, endY],
  ])
}

function routeRightToLeft({ startX, startY, endX, endY, laneOffset, boxes, sourceBox, targetBox }) {
  const exitX = startX + ROUTE_STUB
  const approachX = endX - ROUTE_STUB
  const lane = Number(laneOffset) || 0

  if (approachX > exitX + 8) {
    const midX = findVerticalChannel((exitX + approachX) / 2 + lane, startY, endY, boxes)
    const forward = [[startX, startY], [midX, startY], [midX, endY], [endX, endY]]
    if (orthogonalClear(forward, boxes)) return simplifyPolyline(forward)
  }

  const gap = verticalGap(sourceBox, targetBox)
  if (gap) {
    const preferredY = clamp(gap.top + (gap.bottom - gap.top) / 2 + lane, gap.top + 4, gap.bottom - 4)
    const viaGap = routeViaHorizontalChannel({
      startX, startY, endX, endY, exitX, approachX, preferredY, boxes,
      yMin: gap.top,
      yMax: gap.bottom,
    })
    if (viaGap) return viaGap
  }

  const stub = ROUTE_STUB + Math.abs(lane)
  const towardTarget = endY >= startY
  const candidates = towardTarget
    ? [
      targetBox.y + targetBox.height + stub,
      sourceBox.y + sourceBox.height + stub,
      targetBox.y - stub,
    ]
    : [
      targetBox.y - stub,
      sourceBox.y - stub,
      targetBox.y + targetBox.height + stub,
    ]
  for (const preferredY of candidates) {
    const viaTarget = routeViaHorizontalChannel({
      startX, startY, endX, endY, exitX, approachX, preferredY, boxes,
    })
    if (viaTarget) return viaTarget
  }

  const aroundY = findHorizontalChannel(
    towardTarget
      ? Math.max(sourceBox.y + sourceBox.height, targetBox.y + targetBox.height) + stub
      : Math.min(sourceBox.y, targetBox.y) - stub,
    Math.min(startX, endX) - stub,
    Math.max(startX, endX) + stub,
    boxes,
    !towardTarget,
  )
  return simplifyPolyline([
    [startX, startY],
    [exitX, startY],
    [exitX, aroundY],
    [approachX, aroundY],
    [approachX, endY],
    [endX, endY],
  ])
}

function routeViaHorizontalChannel({ startX, startY, endX, endY, exitX, approachX, preferredY, boxes, yMin, yMax }) {
  let channelY = preferredY
  if (yMin != null && yMax != null) {
    const found = findHorizontalChannelInRange(preferredY, exitX, approachX, boxes, yMin, yMax)
    if (found == null) return null
    channelY = found
  } else if (horizontalBlocked(preferredY, exitX, approachX, boxes)) {
    channelY = findHorizontalChannel(preferredY, exitX, approachX, boxes, preferredY <= startY)
  }
  const outX = findVerticalChannel(exitX, startY, channelY, boxes)
  const inX = findVerticalChannel(approachX, channelY, endY, boxes, true)
  if (horizontalBlocked(channelY, outX, inX, boxes)) {
    const found = yMin != null && yMax != null
      ? findHorizontalChannelInRange(channelY, outX, inX, boxes, yMin, yMax)
      : findHorizontalChannel(channelY, outX, inX, boxes, channelY <= startY)
    if (found == null) return null
    channelY = found
  }
  const points = [
    [startX, startY],
    [outX, startY],
    [outX, channelY],
    [inX, channelY],
    [inX, endY],
    [endX, endY],
  ]
  return orthogonalClear(points, boxes) ? simplifyPolyline(points) : null
}

function verticalGap(sourceBox, targetBox) {
  let top
  let bottom
  if (sourceBox.y + sourceBox.height <= targetBox.y) {
    top = sourceBox.y + sourceBox.height + ROUTE_PAD
    bottom = targetBox.y - ROUTE_PAD
  } else if (targetBox.y + targetBox.height <= sourceBox.y) {
    top = targetBox.y + targetBox.height + ROUTE_PAD
    bottom = sourceBox.y - ROUTE_PAD
  } else {
    return null
  }
  if (bottom - top < 8) return null
  return { top, bottom }
}

function findHorizontalChannelInRange(preferredY, x1, x2, boxes, yMin, yMax) {
  const mid = clamp(preferredY, yMin, yMax)
  if (!horizontalBlocked(mid, x1, x2, boxes)) return mid
  const limit = Math.max(Math.abs(mid - yMin), Math.abs(yMax - mid))
  for (let index = 1; index <= limit; index += 1) {
    const down = mid + index
    const up = mid - index
    if (down <= yMax && !horizontalBlocked(down, x1, x2, boxes)) return down
    if (up >= yMin && !horizontalBlocked(up, x1, x2, boxes)) return up
  }
  return null
}

function orthogonalClear(points, boxes) {
  for (let index = 0; index < points.length - 1; index += 1) {
    const [x1, y1] = points[index]
    const [x2, y2] = points[index + 1]
    if (Math.abs(x1 - x2) < 0.01 && verticalBlocked(x1, y1, y2, boxes)) return false
    if (Math.abs(y1 - y2) < 0.01 && horizontalBlocked(y1, x1, x2, boxes)) return false
  }
  return true
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function gapChannelX(sourceBox, targetBox, wrapRank) {
  let gapLeft
  let gapRight
  if (sourceBox.x + sourceBox.width <= targetBox.x) {
    gapLeft = sourceBox.x + sourceBox.width + ROUTE_PAD
    gapRight = targetBox.x - ROUTE_PAD
  } else if (targetBox.x + targetBox.width <= sourceBox.x) {
    gapLeft = targetBox.x + targetBox.width + ROUTE_PAD
    gapRight = sourceBox.x - ROUTE_PAD
  } else {
    return null
  }
  if (gapRight - gapLeft < 12) return null
  const channelX = gapLeft + 10 + wrapRank * ROUTE_LANE
  return Math.min(channelX, gapRight - 8)
}

function findVerticalChannel(preferredX, y1, y2, boxes, preferLeft = false) {
  if (!verticalBlocked(preferredX, y1, y2, boxes)) return preferredX
  for (let index = 1; index <= 80; index += 1) {
    const right = preferredX + index * 10
    const left = preferredX - index * 10
    const first = preferLeft ? left : right
    const second = preferLeft ? right : left
    if (!verticalBlocked(first, y1, y2, boxes)) return first
    if (!verticalBlocked(second, y1, y2, boxes)) return second
  }
  return preferredX
}

function findHorizontalChannel(preferredY, x1, x2, boxes, searchUp) {
  if (!horizontalBlocked(preferredY, x1, x2, boxes)) return preferredY
  for (let index = 1; index <= 80; index += 1) {
    const y = preferredY + (searchUp ? -index : index) * 10
    if (!horizontalBlocked(y, x1, x2, boxes)) return y
  }
  return preferredY
}

function verticalBlocked(x, y1, y2, boxes) {
  const lo = Math.min(y1, y2)
  const hi = Math.max(y1, y2)
  return boxes.some(box => x > box.x && x < box.x + box.width && hi > box.y && lo < box.y + box.height)
}

function horizontalBlocked(y, x1, x2, boxes) {
  const lo = Math.min(x1, x2)
  const hi = Math.max(x1, x2)
  return boxes.some(box => y > box.y && y < box.y + box.height && hi > box.x && lo < box.x + box.width)
}

function segmentHitsNodeInterior(x1, y1, x2, y2, node, inset = 0.6) {
  const left = (Number(node.x) || 0) + inset
  const top = (Number(node.y) || 0) + inset
  const right = left + Math.max(1, Number(node.width) || 220) - inset * 2
  const bottom = top + Math.max(1, Number(node.height) || 108) - inset * 2
  if (right <= left || bottom <= top) return false
  if (Math.abs(x1 - x2) < 0.01) {
    if (x1 <= left || x1 >= right) return false
    return Math.max(y1, y2) > top && Math.min(y1, y2) < bottom
  }
  if (Math.abs(y1 - y2) < 0.01) {
    if (y1 <= top || y1 >= bottom) return false
    return Math.max(x1, x2) > left && Math.min(x1, x2) < right
  }
  return false
}

function rangesOverlap(leftStart, leftEnd, rightStart, rightEnd) {
  return Math.min(leftEnd, rightEnd) - Math.max(leftStart, rightStart) > 0
}

function simplifyPolyline(points) {
  const snapped = []
  for (const [x, y] of points) {
    const next = [Math.round(x * 10) / 10, Math.round(y * 10) / 10]
    const prev = snapped[snapped.length - 1]
    if (prev && prev[0] === next[0] && prev[1] === next[1]) continue
    snapped.push(next)
  }
  const result = []
  for (const point of snapped) {
    const first = result[result.length - 2]
    const second = result[result.length - 1]
    if (first && second && ((first[0] === second[0] && second[0] === point[0]) || (first[1] === second[1] && second[1] === point[1]))) {
      result[result.length - 1] = point
      continue
    }
    result.push(point)
  }
  return result.length ? result : [[0, 0]]
}

export function buildFlowNodes(nodes = [], layout = {}) {
  return nodes.map((node, index) => ({
    id: editorNodeId(node.name),
    type: 'workflow',
    position: layout[node.name] || { x: 80 + index * 280, y: 120 },
    data: {
      nodeName: node.name,
      nodeType: node.nodeType,
      functionType: node.functionType,
      interfaces: node.interfaces || [],
      ports: node.ports || []
    }
  }))
}

export function serializeLayout(nodes = []) {
  return nodes.reduce((result, node) => {
    const nodeName = node.data?.nodeName
    if (!nodeName || !node.position) return result
    result[nodeName] = {
      x: Math.round(Number(node.position.x) || 0),
      y: Math.round(Number(node.position.y) || 0)
    }
    return result
  }, {})
}

export const interfaceHandleId = name => `interface:${name}`
export const portHandleId = name => `port:${name}`

export function parseHandleId(handleId) {
  const [prefix, ...parts] = String(handleId || '').split(':')
  if (prefix === 'interface') return { kind: 'INTERFACE', name: parts.join(':') }
  if (prefix === 'port') return { kind: 'PORT', name: parts.join(':') }
  throw new Error(`未知连接点:${handleId}`)
}

function nodesByNameMap(nodes = []) {
  return new Map((nodes || []).map(node => [node?.name, node]))
}

function resolveInterfaceHandle(endpoint, nodesByName, direction) {
  const preferred = endpoint?.interfaceName
  if (preferred) return interfaceHandleId(preferred)
  const fallback = workflowInterface(nodesByName.get(endpoint?.nodeName), direction)?.name
  return fallback ? interfaceHandleId(fallback) : undefined
}

function resolvePortHandle(endpoint, nodesByName, direction) {
  const preferred = endpoint?.portName
  if (preferred) return portHandleId(preferred)
  const ports = (nodesByName.get(endpoint?.nodeName)?.ports || []).filter(item => item.direction === direction)
  return ports[0]?.name ? portHandleId(ports[0].name) : undefined
}

export function buildFlowEdges(interfaceConnections = [], portConnections = [], nodes = []) {
  const nodesByName = nodesByNameMap(nodes)
  const interfaceEdges = interfaceConnections
    .filter(connection => !connection.connectionType || connection.connectionType === 'NODE_TO_NODE')
    .filter(connection => connection?.source?.nodeName && connection?.target?.nodeName)
    .map((connection, index) => ({
      id: edgeId('interface', connection.source, connection.target, index),
      source: editorNodeId(connection.source.nodeName),
      target: editorNodeId(connection.target.nodeName),
      sourceHandle: resolveInterfaceHandle(connection.source, nodesByName, 'OUT'),
      targetHandle: resolveInterfaceHandle(connection.target, nodesByName, 'IN'),
      type: 'workflow',
      class: 'execution-edge',
      markerEnd: 'arrowclosed',
      data: { connectionKind: 'INTERFACE', tooltip: workflowInterfaceEdgeTooltip(connection) },
      style: { stroke: '#7c93b8', strokeWidth: 1.8 }
    }))
  const portEdges = portConnections
    .filter(connection => connection?.source?.nodeName && connection?.target?.nodeName)
    .map((connection, index) => ({
      id: edgeId('port', connection.source, connection.target, index),
      source: editorNodeId(connection.source.nodeName),
      target: editorNodeId(connection.target.nodeName),
      sourceHandle: resolvePortHandle(connection.source, nodesByName, 'OUT'),
      targetHandle: resolvePortHandle(connection.target, nodesByName, 'IN'),
      type: 'workflow',
      class: 'data-edge',
      markerEnd: 'arrowclosed',
      data: { connectionKind: 'PORT', tooltip: workflowPortEdgeTooltip(connection, nodes) },
      style: { stroke: '#9a8ab5', strokeWidth: 1.6, strokeDasharray: '6 5' }
    }))
  return assignEdgeLanes([...interfaceEdges, ...portEdges], nodes)
}

function assignEdgeLanes(edges, nodes = []) {
  const portOrder = new Map()
  for (const node of nodes) {
    (node.ports || []).filter(item => item.direction === 'OUT').forEach((item, index) => {
      portOrder.set(`${editorNodeId(node.name)}\u0000${portHandleId(item.name)}`, index)
    })
  }
  const portGroups = new Map()
  const otherGroups = new Map()
  for (const edge of edges) {
    if (edge.data?.connectionKind === 'PORT') {
      if (!portGroups.has(edge.source)) portGroups.set(edge.source, [])
      portGroups.get(edge.source).push(edge)
      continue
    }
    const key = `${edge.source}\u0000${edge.target}\u0000${edge.data?.connectionKind || ''}`
    if (!otherGroups.has(key)) otherGroups.set(key, [])
    otherGroups.get(key).push(edge)
  }
  for (const group of portGroups.values()) {
    group.sort((left, right) => {
      const leftOrder = portOrder.get(`${left.source}\u0000${left.sourceHandle}`)
      const rightOrder = portOrder.get(`${right.source}\u0000${right.sourceHandle}`)
      if (leftOrder != null && rightOrder != null && leftOrder !== rightOrder) return leftOrder - rightOrder
      return String(left.sourceHandle).localeCompare(String(right.sourceHandle))
        || String(left.targetHandle).localeCompare(String(right.targetHandle))
    })
    group.forEach((edge, index) => {
      edge.data = {
        ...edge.data,
        laneOffset: edgeLaneOffset(index, group.length),
        wrapRank: group.length - 1 - index,
        wrapCount: group.length,
        laneIndex: index,
        laneCount: group.length,
      }
    })
  }
  for (const group of otherGroups.values()) {
    group.sort((left, right) => String(left.sourceHandle).localeCompare(String(right.sourceHandle))
      || String(left.targetHandle).localeCompare(String(right.targetHandle)))
    group.forEach((edge, index) => {
      edge.data = {
        ...edge.data,
        laneOffset: edgeLaneOffset(index, group.length),
        wrapRank: 0,
        wrapCount: 1,
        laneIndex: index,
        laneCount: group.length,
      }
    })
  }
  return edges
}

function edgeId(kind, source, target, index) {
  return [
    'workflow-edge', kind,
    encodeURIComponent(source.nodeName),
    encodeURIComponent(source.interfaceName || source.portName),
    encodeURIComponent(target.nodeName),
    encodeURIComponent(target.interfaceName || target.portName),
    index
  ].join(':')
}

function workflowInterface(node, direction) {
  return (node?.interfaces || []).find(item =>
    item.direction === direction && item.interfaceType === 'WORKFLOW'
  )
}

export function createNodeConnection({
  sourceNodeName,
  targetNodeName,
  nodes = [],
  connections = []
}) {
  if (!sourceNodeName || !targetNodeName) throw new Error('连接两端不能为空')
  if (sourceNodeName === targetNodeName) throw new Error('节点不能连接到自身')

  const sourceNode = nodes.find(node => node.name === sourceNodeName)
  const targetNode = nodes.find(node => node.name === targetNodeName)
  if (!sourceNode || !targetNode) throw new Error('连接节点不存在')

  const sourceInterface = workflowInterface(sourceNode, 'OUT')
  const targetInterface = workflowInterface(targetNode, 'IN')
  if (!sourceInterface) throw new Error(`节点${sourceNodeName}没有WORKFLOW输出接口`)
  if (!targetInterface) throw new Error(`节点${targetNodeName}没有WORKFLOW输入接口`)

  const duplicate = connections.some(connection =>
    connection.connectionType === 'NODE_TO_NODE' &&
    connection.source?.nodeName === sourceNodeName &&
    connection.source?.interfaceName === sourceInterface.name &&
    connection.target?.nodeName === targetNodeName &&
    connection.target?.interfaceName === targetInterface.name
  )
  if (duplicate) throw new Error('连接已存在')
  if (connections.some(connection =>
    connection.connectionType === 'NODE_TO_NODE' &&
    connection.target?.nodeName === targetNodeName &&
    connection.target?.interfaceName === targetInterface.name
  )) throw new Error('该输入接口已连接上游，请使用其他输入接口')

  return {
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: sourceNodeName, interfaceName: sourceInterface.name },
    target: { nodeName: targetNodeName, interfaceName: targetInterface.name }
  }
}

function namedInterface(node, name, direction) {
  const item = (node?.interfaces || []).find(candidate => candidate.name === name)
  if (!item) throw new Error(`接口${name}不存在`)
  if (item.interfaceType !== 'WORKFLOW' || item.direction !== direction) throw new Error('接口必须按WORKFLOW OUT→IN方向连接')
  return item
}

function namedPort(node, name, direction) {
  const item = (node?.ports || []).find(candidate => candidate.name === name)
  if (!item) throw new Error(`端口不存在:${name}`)
  if (item.direction !== direction) throw new Error('端口必须按OUT→IN方向连接')
  return item
}

function portVariable(node, port) {
  if (!port.internalVariableName) throw new Error(`端口${port.name}未绑定内部变量`)
  const variable = (node?.internalVariables || []).find(item => item.name === port.internalVariableName)
  if (!variable) throw new Error(`内部变量不存在:${port.internalVariableName}`)
  return variable
}

function canvasNodes(args) {
  const sourceNode = (args.nodes || []).find(node => node.name === args.sourceNodeName)
  const targetNode = (args.nodes || []).find(node => node.name === args.targetNodeName)
  if (!args.sourceNodeName || !args.targetNodeName) throw new Error('连接两端不能为空')
  if (args.sourceNodeName === args.targetNodeName) throw new Error('节点不能连接到自身')
  if (!sourceNode || !targetNode) throw new Error('连接节点不存在')
  return { sourceNode, targetNode }
}

function createInterfaceConnection(args, sourceName, targetName) {
  const { sourceNode, targetNode } = canvasNodes(args)
  namedInterface(sourceNode, sourceName, 'OUT')
  namedInterface(targetNode, targetName, 'IN')
  const value = {
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: args.sourceNodeName, interfaceName: sourceName },
    target: { nodeName: args.targetNodeName, interfaceName: targetName }
  }
  if ((args.interfaceConnections || []).some(connection => sameConnection(connection, value, 'interfaceName'))) throw new Error('连接已存在')
  if ((args.interfaceConnections || []).some(connection =>
    connection.connectionType === 'NODE_TO_NODE' &&
    connection.target?.nodeName === args.targetNodeName &&
    connection.target?.interfaceName === targetName
  )) throw new Error('该输入接口已连接上游，请使用其他输入接口')
  return { collection: 'interfaceConnections', value }
}

function createPortConnection(args, sourceName, targetName) {
  const { sourceNode, targetNode } = canvasNodes(args)
  const sourcePort = namedPort(sourceNode, sourceName, 'OUT')
  const targetPort = namedPort(targetNode, targetName, 'IN')
  const sourceVariable = portVariable(sourceNode, sourcePort)
  const targetVariable = portVariable(targetNode, targetPort)
  if (sourceVariable.dataType !== targetVariable.dataType) throw new Error('端口数据类型不一致')
  const value = {
    source: { nodeName: args.sourceNodeName, portName: sourceName },
    target: { nodeName: args.targetNodeName, portName: targetName }
  }
  if ((args.portConnections || []).some(connection => sameConnection(connection, value, 'portName'))) throw new Error('连接已存在')
  return { collection: 'portConnections', value }
}

function sameConnection(left, right, handleName) {
  return left.source?.nodeName === right.source.nodeName &&
    left.source?.[handleName] === right.source[handleName] &&
    left.target?.nodeName === right.target.nodeName &&
    left.target?.[handleName] === right.target[handleName]
}

export function createCanvasConnection(args) {
  const sourceHandle = parseHandleId(args.sourceHandle)
  const targetHandle = parseHandleId(args.targetHandle)
  if (sourceHandle.kind !== targetHandle.kind) throw new Error('接口连接点不能连接数据端口')
  return sourceHandle.kind === 'INTERFACE'
    ? createInterfaceConnection(args, sourceHandle.name, targetHandle.name)
    : createPortConnection(args, sourceHandle.name, targetHandle.name)
}

export function removeCanvasEdge(edge, interfaceConnections = [], portConnections = []) {
  const kind = edge.data?.connectionKind || parseHandleId(edge.sourceHandle).kind
  const sourceName = decodeURIComponent(edge.source?.replace(/^workflow-node:/, '') || '')
  const targetName = decodeURIComponent(edge.target?.replace(/^workflow-node:/, '') || '')
  const handleName = kind === 'INTERFACE' ? 'interfaceName' : 'portName'
  const sourceHandle = parseHandleId(edge.sourceHandle).name
  const targetHandle = parseHandleId(edge.targetHandle).name
  const matches = connection => connection.source?.nodeName === sourceName &&
    connection.source?.[handleName] === sourceHandle &&
    connection.target?.nodeName === targetName &&
    connection.target?.[handleName] === targetHandle
  return kind === 'INTERFACE'
    ? { interfaceConnections: interfaceConnections.filter(connection => !matches(connection)), portConnections }
    : { interfaceConnections, portConnections: portConnections.filter(connection => !matches(connection)) }
}

export function renameNodeConnections(connections = [], oldName, newName) {
  connections.forEach(connection => {
    if (connection.source?.nodeName === oldName) connection.source.nodeName = newName
    if (connection.target?.nodeName === oldName) connection.target.nodeName = newName
  })
  return connections
}

export function sanitizeWorkflowPayload(form) {
  return sanitizeValue(JSON.parse(JSON.stringify(form)))
}

function sanitizeValue(value) {
  if (Array.isArray(value)) return value.map(sanitizeValue)
  if (!value || typeof value !== 'object') return value
  return Object.entries(value).reduce((result, [key, child]) => {
    if (key.startsWith('_') || key === 'position' || key === 'compiled' || key === 'runtime') return result
    result[key] = sanitizeValue(child)
    return result
  }, {})
}
