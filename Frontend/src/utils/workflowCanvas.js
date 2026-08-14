const LAYOUT_PREFIX = 'smartlab:workflow-layout:'

export function editorNodeId(nodeName) {
  return `workflow-node:${encodeURIComponent(nodeName)}`
}

export function layoutKey(workflowId, draftKey = 'draft') {
  return `${LAYOUT_PREFIX}${workflowId ?? draftKey}`
}

export function workflowCanvasNodeSize(node = {}) {
  const workflowInterfaces = (node.interfaces || []).filter(item => item.interfaceType === 'WORKFLOW')
  const maxSideCount = Math.max(
    workflowInterfaces.filter(item => item.direction === 'IN').length,
    workflowInterfaces.filter(item => item.direction === 'OUT').length,
  )
  const maxPortCount = Math.max(
    (node.ports || []).filter(item => item.direction === 'IN').length,
    (node.ports || []).filter(item => item.direction === 'OUT').length,
  )
  return {
    width: Math.max(220, 80 + maxPortCount * 40),
    height: Math.max(92, 48 + maxSideCount * 24),
  }
}

export function workflowInterfaceTooltip(interfaceDefinition = {}) {
  const direction = interfaceDefinition.direction === 'OUT' ? '控制输出' : '控制输入'
  return `${direction} · ${interfaceDefinition.name || '未命名接口'}`
}

export function workflowPortTooltip(port = {}, node = {}) {
  const direction = port.direction === 'OUT' ? '数据输出' : '数据输入'
  const variable = (node.internalVariables || []).find(item => item.name === port.internalVariableName)
  return [direction, port.name || '未命名端口', variable?.dataType].filter(Boolean).join(' · ')
}

export function buildWorkflowAutoLayout(nodes = [], interfaceConnections = [], portConnections = []) {
  const validNodes = nodes.filter(node => node?.name)
  const nodesByName = new Map(validNodes.map(node => [node.name, node]))
  const originalOrder = new Map(validNodes.map((node, index) => [node.name, index]))
  const outgoing = new Map(validNodes.map(node => [node.name, new Set()]))
  const incoming = new Map(validNodes.map(node => [node.name, new Set()]))
  const edgeKeys = new Set()

  const addEdge = connection => {
    const source = connection?.source?.nodeName
    const target = connection?.target?.nodeName
    if (!nodesByName.has(source) || !nodesByName.has(target) || source === target) return
    const key = `${source}\u0000${target}`
    if (edgeKeys.has(key)) return
    edgeKeys.add(key)
    outgoing.get(source).add(target)
    incoming.get(target).add(source)
  }
  interfaceConnections.filter(connection => connection?.connectionType === 'NODE_TO_NODE').forEach(addEdge)
  portConnections.forEach(addEdge)

  const indegree = new Map(validNodes.map(node => [node.name, incoming.get(node.name).size]))
  const levels = new Map(validNodes.map(node => [node.name, 0]))
  const queue = validNodes.filter(node => indegree.get(node.name) === 0).map(node => node.name)
  const processed = new Set()

  while (queue.length) {
    queue.sort((left, right) => originalOrder.get(left) - originalOrder.get(right))
    const current = queue.shift()
    if (processed.has(current)) continue
    processed.add(current)
    for (const target of outgoing.get(current)) {
      levels.set(target, Math.max(levels.get(target), levels.get(current) + 1))
      indegree.set(target, indegree.get(target) - 1)
      if (indegree.get(target) === 0) queue.push(target)
    }
  }

  let fallbackLevel = Math.max(0, ...processed.size ? [...processed].map(name => levels.get(name)) : [0]) + 1
  for (const node of validNodes) {
    if (processed.has(node.name)) continue
    levels.set(node.name, fallbackLevel)
    fallbackLevel += 1
  }

  const layerMap = new Map()
  for (const node of validNodes) {
    const level = levels.get(node.name)
    if (!layerMap.has(level)) layerMap.set(level, [])
    layerMap.get(level).push(node)
  }
  const orderedLevels = [...layerMap.keys()].sort((left, right) => left - right)
  const orderInLayer = new Map()
  for (const level of orderedLevels) {
    const layer = layerMap.get(level)
    layer.sort((left, right) => {
      const leftParents = [...incoming.get(left.name)].filter(name => levels.get(name) < level)
      const rightParents = [...incoming.get(right.name)].filter(name => levels.get(name) < level)
      const leftCenter = leftParents.length
        ? leftParents.reduce((sum, name) => sum + (orderInLayer.get(name) ?? originalOrder.get(name)), 0) / leftParents.length
        : originalOrder.get(left.name)
      const rightCenter = rightParents.length
        ? rightParents.reduce((sum, name) => sum + (orderInLayer.get(name) ?? originalOrder.get(name)), 0) / rightParents.length
        : originalOrder.get(right.name)
      return leftCenter - rightCenter || originalOrder.get(left.name) - originalOrder.get(right.name)
    })
    layer.forEach((node, index) => orderInLayer.set(node.name, index))
  }

  const sizes = new Map(validNodes.map(node => [node.name, workflowCanvasNodeSize(node)]))
  const layerWidths = new Map(orderedLevels.map(level => [
    level,
    Math.max(...layerMap.get(level).map(node => sizes.get(node.name).width)),
  ]))
  const layerHeights = new Map(orderedLevels.map(level => {
    const layer = layerMap.get(level)
    return [level, layer.reduce((sum, node) => sum + sizes.get(node.name).height, 0) + Math.max(0, layer.length - 1) * 72]
  }))
  const maxLayerHeight = Math.max(0, ...layerHeights.values())
  const xByLevel = new Map()
  let x = 80
  for (const level of orderedLevels) {
    xByLevel.set(level, x)
    x += layerWidths.get(level) + 150
  }

  const layout = {}
  for (const level of orderedLevels) {
    let y = 80 + (maxLayerHeight - layerHeights.get(level)) / 2
    for (const node of layerMap.get(level)) {
      layout[node.name] = { x: Math.round(xByLevel.get(level)), y: Math.round(y) }
      y += sizes.get(node.name).height + 72
    }
  }
  return layout
}

export function buildFlowNodes(nodes = [], layout = {}) {
  return nodes.map((node, index) => {
    const column = index % 4
    const row = Math.floor(index / 4)
    return {
      id: editorNodeId(node.name),
      type: 'workflow',
      position: layout[node.name] || { x: 80 + column * 280, y: 120 + row * 180 },
      data: {
        nodeName: node.name,
        nodeType: node.nodeType,
        functionType: node.functionType,
        interfaces: node.interfaces || [],
        ports: node.ports || []
      }
    }
  })
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

export function buildFlowEdges(interfaceConnections = [], portConnections = []) {
  const interfaceEdges = interfaceConnections
    .filter(connection => connection.connectionType === 'NODE_TO_NODE')
    .map((connection, index) => ({
      id: edgeId('interface', connection.source, connection.target, index),
      source: editorNodeId(connection.source.nodeName),
      target: editorNodeId(connection.target.nodeName),
      sourceHandle: interfaceHandleId(connection.source.interfaceName),
      targetHandle: interfaceHandleId(connection.target.interfaceName),
      type: 'smoothstep',
      pathOptions: { offset: 24, borderRadius: 8 },
      class: 'execution-edge',
      data: { connectionKind: 'INTERFACE' },
      style: { stroke: '#7890ad', strokeWidth: 1.6 }
    }))
  const portEdges = portConnections.map((connection, index) => ({
    id: edgeId('port', connection.source, connection.target, index),
    source: editorNodeId(connection.source.nodeName),
    target: editorNodeId(connection.target.nodeName),
    sourceHandle: portHandleId(connection.source.portName),
    targetHandle: portHandleId(connection.target.portName),
    type: 'smoothstep',
    pathOptions: { offset: 24, borderRadius: 8 },
    class: 'data-edge',
    data: { connectionKind: 'PORT' },
    style: { stroke: '#7569bd', strokeWidth: 1.6, strokeDasharray: '6 5' }
  }))
  return [...interfaceEdges, ...portEdges]
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
