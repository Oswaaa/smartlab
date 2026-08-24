import { buildWorkflowAutoLayout } from './workflowCanvas.js'

export function escapeBindingSegment(value) {
  return String(value).replaceAll('~', '~0').replaceAll('/', '~1')
}

export function bindingKeyFromSegments(segments) {
  return segments.map(escapeBindingSegment).join('/')
}

function orderedNodes(definition) {
  const refsByName = new Map((definition?.nodeIdRefs || []).map(item => [
    String(item?.nodeName || ''),
    Number(item?.nodeIdRef)
  ]))
  return (definition?.nodesDef || definition?.nodes || []).map((node, index) => ({
    node: {
      ...node,
      nodeIdRef: Number(node?.nodeIdRef ?? refsByName.get(String(node?.name || '')) ?? node?.id ?? index + 1)
    },
    index
  }))
    .sort((left, right) => left.node.nodeIdRef - right.node.nodeIdRef || left.index - right.index)
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

  async function visit(flowModelId, slotSegments, breadcrumbNames, ancestors, parentGroupKey) {
    if (ancestors.includes(flowModelId)) {
      throw new Error(`检测到子流程循环引用：${[...ancestors, flowModelId].join(' -> ')}`)
    }
    const definition = await loadDefinition(flowModelId)
    if (!definition) throw new Error(`流程模型${flowModelId}不存在`)
    const flowName = definition.metadata?.flowModelName || definition.flowModelName || definition.flowName || definition.name || `流程#${flowModelId}`
    const groupBreadcrumb = breadcrumbNames.length ? breadcrumbNames : [flowName]
    const groupKey = slotSegments.length ? slotSegments.join('/') : 'root'
    const interfaceConnections = (definition.interfaceConnections || [])
      .filter(connection => connection.connectionType === 'NODE_TO_NODE')
    const group = {
      groupKey,
      parentGroupKey,
      depth: slotSegments.length,
      flowModelId: Number(flowModelId),
      flowName,
      occurrencePath: groupBreadcrumb.join(' / '),
      nodes: [],
      interfaceConnections,
      connections: interfaceConnections
        .map(connection => ({
          sourceNodeName: connection.source?.nodeName || '',
          targetNodeName: connection.target?.nodeName || ''
        }))
    }
    groups.push(group)

    for (const node of orderedNodes(definition)) {
      const nodeSlotSegments = [...slotSegments, `${flowModelId}:${node.nodeIdRef}`]
      const occurrenceKey = nodeSlotSegments.join('/')
      const occurrencePath = [...groupBreadcrumb, node.name].join(' / ')
      const visualNode = {
        ...node,
        flowModelId: Number(flowModelId),
        flowName: group.flowName,
        groupKey,
        occurrenceKey,
        occurrencePath,
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
          inheritanceKey: `${flowModelId}:${node.nodeIdRef}`,
          occurrencePath,
          depth: group.depth,
          deviceModelId: Number(node.deviceModelId),
          deviceInputInterfaceName: interfaces.deviceInputInterfaceName,
          deviceOutputInterfaceName: interfaces.deviceOutputInterfaceName,
          interfaceValid: interfaces.valid
        }
        deviceRoutes.push(route)
        Object.assign(visualNode, { bindingKey: route.bindingKey, deviceModelId: route.deviceModelId, interfaceValid: route.interfaceValid })
        if (!interfaces.valid) errors.push(`${occurrencePath}的设备状态接口连接不完整或设备模型不一致`)
      }
      if (node.nodeType === 'SUBFLOW_NODE') {
        const childFlowModelId = Number(node.subFlowModelId)
        if (!Number.isInteger(childFlowModelId) || childFlowModelId <= 0) {
          errors.push(`${occurrenceKey}未配置有效的subFlowModelId`)
          continue
        }
        visualNode.childGroupKey = occurrenceKey
        await visit(childFlowModelId, nodeSlotSegments, [...groupBreadcrumb, node.name], [...ancestors, flowModelId], groupKey)
      }
    }
  }

  await visit(Number(rootFlowModelId), [], [], [], null)
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

export function buildTaskCreatePayload(form, requirements) {
  return {
    taskName: form.taskName,
    flowModelId: form.flowModelId,
    deviceBindings: buildDeviceBindings(requirements, form.resourceBindings || {}),
    taskConstraints: form.taskConstraints || [],
    taskVariables: {}
  }
}

export function groupRequirementsByOccurrencePath(requirements) {
  if (!Array.isArray(requirements)) return []
  const groups = new Map()
  for (const requirement of requirements) {
    const segments = String(requirement?.occurrencePath || '').split(/\s+\/\s+/).filter(Boolean)
    const path = segments.length > 1
      ? segments.slice(0, -1).join(' / ')
      : String(requirement?.flowModelName || requirement?.flowName || '主流程')
    if (!groups.has(path)) groups.set(path, { path, requirements: [] })
    groups.get(path).requirements.push(requirement)
  }
  return Array.from(groups.values())
}

export function buildBindingWorkflowView(expanded = {}, requirements = []) {
  const requirementsBySlot = new Map((requirements || []).map(requirement => [String(requirement.slotId), requirement]))
  const matchedSlots = new Set()
  const errors = [...(expanded.errors || [])]
  const groups = (expanded.groups || []).map(group => {
    const layout = buildWorkflowAutoLayout(group.nodes || [], group.interfaceConnections || [], [])
    const nodes = (group.nodes || []).map(node => {
      if (node.nodeType !== 'DEV_NODE') {
        return { ...node, slotId: null, requirement: null, bindingInvalid: false, position: layout[node.name] }
      }
      const slotId = String(node.bindingKey || node.occurrenceKey || '')
      const requirement = requirementsBySlot.get(slotId) || null
      const modelMismatch = requirement != null && Number(requirement.deviceModelId) !== Number(node.deviceModelId)
      if (requirement) matchedSlots.add(slotId)
      if (!requirement) errors.push(`${node.occurrencePath || node.name}缺少后端设备绑定要求`)
      if (modelMismatch) errors.push(`${node.occurrencePath || node.name}的设备模型与绑定要求不一致`)
      return {
        ...node,
        slotId: requirement?.slotId || slotId || null,
        requirement,
        bindingInvalid: !requirement || modelMismatch,
        position: layout[node.name]
      }
    })
    return { ...group, nodes }
  })
  for (const requirement of requirements || []) {
    if (!matchedSlots.has(String(requirement.slotId))) {
      errors.push(`${requirement.occurrencePath || requirement.nodeName || '设备节点'}缺少流程图节点`)
    }
  }
  return { groups, errors: [...new Set(errors)] }
}

export function groupRequirementsByFlow(requirements) {
  if (!Array.isArray(requirements)) return []
  const groups = new Map()
  for (const req of requirements) {
    const key = String(req.flowModelId || 'unknown')
    if (!groups.has(key)) {
      groups.set(key, {
        flowModelId: req.flowModelId,
        flowName: req.flowModelName || req.flowName || ('流程#' + key),
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
