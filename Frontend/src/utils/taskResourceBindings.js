export function escapeBindingSegment(value) {
  return String(value).replaceAll('~', '~0').replaceAll('/', '~1')
}

export function bindingKeyFromSegments(segments) {
  return segments.map(escapeBindingSegment).join('/')
}

function orderedNodes(definition) {
  return (definition?.nodesDef || []).map((node, index) => ({ node, index }))
    .sort((left, right) => Number(left.node.nodeIdRef ?? left.node.id ?? left.index) - Number(right.node.nodeIdRef ?? right.node.id ?? right.index))
    .map(item => item.node)
}

function routeInterfaces(definition, node) {
  const connections = definition?.interfaceConnections || []
  const outbound = connections.find(connection => connection.connectionType === 'NODE_TO_DEVICE' && connection.source?.nodeName === node.name)
  const inbound = connections.find(connection => connection.connectionType === 'DEVICE_TO_NODE' && connection.target?.nodeName === node.name)
  const expectedModelId = Number(node.deviceModelId)
  const valid = outbound && inbound
    && Number(outbound.target?.deviceModelId) === expectedModelId
    && Number(inbound.source?.deviceModelId) === expectedModelId
  return {
    valid: Boolean(valid),
    deviceInputInterfaceName: outbound?.target?.interfaceName || '',
    deviceOutputInterfaceName: inbound?.source?.interfaceName || ''
  }
}

export async function expandWorkflowDefinition(rootFlowModelId, loadDefinition) {
  const groups = []
  const deviceRoutes = []
  const workflowNodes = []
  const errors = []

  async function visit(flowModelId, pathSegments, ancestors, parentGroupKey) {
    if (ancestors.includes(flowModelId)) {
      throw new Error(`检测到子流程循环引用：${[...ancestors, flowModelId].join(' -> ')}`)
    }
    const definition = await loadDefinition(flowModelId)
    if (!definition) throw new Error(`流程模型${flowModelId}不存在`)
    const groupKey = bindingKeyFromSegments(pathSegments)
    const group = {
      groupKey,
      parentGroupKey,
      depth: pathSegments.length - 1,
      flowModelId: Number(flowModelId),
      flowName: definition.flowName || definition.name || `流程#${flowModelId}`,
      occurrencePath: groupKey,
      nodes: [],
      connections: (definition.interfaceConnections || [])
        .filter(connection => connection.connectionType === 'NODE_TO_NODE')
        .map(connection => ({
          sourceNodeName: connection.source?.nodeName || '',
          targetNodeName: connection.target?.nodeName || ''
        }))
    }
    groups.push(group)

    for (const node of orderedNodes(definition)) {
      const nodeSegments = [...pathSegments, node.name]
      const occurrenceKey = bindingKeyFromSegments(nodeSegments)
      const visualNode = {
        ...node,
        flowModelId: Number(flowModelId),
        flowName: group.flowName,
        groupKey,
        occurrenceKey,
        depth: group.depth
      }
      group.nodes.push(visualNode)
      workflowNodes.push(visualNode)
      if (node.nodeType === 'DEV_NODE') {
        const interfaces = routeInterfaces(definition, node)
        const route = {
          flowModelId: Number(flowModelId),
          flowName: group.flowName,
          nodeName: node.name,
          nodeType: node.nodeType,
          bindingKey: occurrenceKey,
          inheritanceKey: `${flowModelId}:${escapeBindingSegment(node.name)}`,
          occurrencePath: occurrenceKey,
          depth: group.depth,
          deviceModelId: Number(node.deviceModelId),
          deviceInputInterfaceName: interfaces.deviceInputInterfaceName,
          deviceOutputInterfaceName: interfaces.deviceOutputInterfaceName,
          interfaceValid: interfaces.valid
        }
        deviceRoutes.push(route)
        Object.assign(visualNode, { bindingKey: route.bindingKey, deviceModelId: route.deviceModelId, interfaceValid: route.interfaceValid })
        if (!interfaces.valid) errors.push(`${occurrenceKey}缺少完整且匹配设备模型的NODE_TO_DEVICE或DEVICE_TO_NODE连接`)
      }
      if (node.nodeType === 'SUBFLOW_NODE') {
        const childFlowModelId = Number(node.subFlowModelId)
        if (!Number.isInteger(childFlowModelId) || childFlowModelId <= 0) {
          errors.push(`${occurrenceKey}未配置有效的subFlowModelId`)
          continue
        }
        await visit(childFlowModelId, nodeSegments, [...ancestors, flowModelId], groupKey)
      }
    }
  }

  await visit(Number(rootFlowModelId), ['root'], [], null)
  return { groups, deviceRoutes, workflowNodes, errors, hasDeviceNodes: deviceRoutes.length > 0 }
}

export function applyInheritedBinding(routes, currentBindings, sourceRoute, deviceInstanceId) {
  const next = { ...currentBindings, [sourceRoute.bindingKey]: deviceInstanceId }
  if (!sourceRoute.inheritanceKey) return next
  for (const route of routes) {
    if (route.inheritanceKey === sourceRoute.inheritanceKey && (next[route.bindingKey] == null || next[route.bindingKey] === '')) {
      next[route.bindingKey] = deviceInstanceId
    }
  }
  return next
}


// --- Backend-requirement-based helpers (Task 7) ---

export function buildDeviceBindings(requirements, selections) {
  if (!Array.isArray(requirements)) return []
  return requirements
    .filter(item => selections[item.slotId] != null && selections[item.slotId] !== '')
    .map(item => ({ slotId: item.slotId, deviceInstanceId: Number(selections[item.slotId]) }))
}

export function groupRequirementsByFlow(requirements) {
  if (!Array.isArray(requirements)) return []
  const groups = new Map()
  for (const req of requirements) {
    const key = String(req.flowModelId || 'unknown')
    if (!groups.has(key)) {
      groups.set(key, {
        flowModelId: req.flowModelId,
        flowName: req.flowName || ('流程#' + key),
        count: 0,
        slots: []
      })
    }
    const group = groups.get(key)
    group.count++
    group.slots.push(req)
  }
  return Array.from(groups.values())
}

export function compatibleInstances(requirement, instances, models) {
  if (!Array.isArray(instances)) return []
  const targetModelId = Number(requirement.deviceModelId)
  return instances.filter(instance => {
    const instanceModelId = Number(instance.deviceModelId || instance.modelId)
    return instanceModelId === targetModelId && String(instance.lifecycleStatus || '').toUpperCase() !== 'RETIRED'
  })
}
