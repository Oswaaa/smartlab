import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildFlowEdges,
  buildFlowNodes,
  createCanvasConnection,
  createNodeConnection,
  interfaceHandleId,
  layoutKey,
  portHandleId,
  removeCanvasEdge,
  renameNodeConnections,
  sanitizeWorkflowPayload,
  serializeLayout
} from '../src/utils/workflowCanvas.js'

const startNode = {
  name: 'start1',
  nodeType: 'FUNC_NODE',
  functionType: 'START',
  interfaces: [{ name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW' }]
}

const deviceNode = {
  name: 'device1',
  nodeType: 'DEV_NODE',
  deviceModelId: 10,
  interfaces: [
    { name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW' },
    { name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW' }
  ]
}

const canvasNodes = [
  {
    name: 'a',
    interfaces: [{ name: 'Interface_workflow_out', direction: 'OUT', interfaceType: 'WORKFLOW' }],
    ports: [{ name: 'valueOut', direction: 'OUT', internalVariableName: 'temperature' }],
    internalVariables: [{ name: 'temperature', dataType: 'DOUBLE' }]
  },
  {
    name: 'b',
    interfaces: [{ name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW' }],
    ports: [{ name: 'valueIn', direction: 'IN', internalVariableName: 'targetTemperature' }],
    internalVariables: [{ name: 'targetTemperature', dataType: 'DOUBLE' }]
  }
]

test('builds deterministic editor nodes and restores saved positions by node name', () => {
  const nodes = buildFlowNodes([startNode, deviceNode], { device1: { x: 640, y: 180 } })
  assert.equal(nodes[0].id, 'workflow-node:start1')
  assert.deepEqual(nodes[0].position, { x: 80, y: 120 })
  assert.deepEqual(nodes[1].position, { x: 640, y: 180 })
})

test('serializes only node positions using business node names', () => {
  const layout = serializeLayout([
    { id: 'workflow-node:start1', position: { x: 20.8, y: 41.2 }, data: { nodeName: 'start1' } },
    { id: 'workflow-node:device1', position: { x: 333.4, y: 95.7 }, data: { nodeName: 'device1' } }
  ])
  assert.deepEqual(layout, {
    start1: { x: 21, y: 41 },
    device1: { x: 333, y: 96 }
  })
  assert.equal(layoutKey(12), 'smartlab:workflow-layout:12')
  assert.equal(layoutKey(null, 'draft-1'), 'smartlab:workflow-layout:draft-1')
})

test('renders only node-to-node interface connections as flow edges', () => {
  const edges = buildFlowEdges([
    {
      connectionType: 'NODE_TO_NODE',
      source: { nodeName: 'start1', interfaceName: 'Interface_workflow_out' },
      target: { nodeName: 'device1', interfaceName: 'Interface_workflow_in' }
    },
    {
      connectionType: 'NODE_TO_DEVICE',
      source: { nodeName: 'device1', interfaceName: 'Interface_state_out' },
      target: { deviceModelId: 10, interfaceName: 'Interface_workflow_in' }
    }
  ])
  assert.equal(edges.length, 1)
  assert.equal(edges[0].source, 'workflow-node:start1')
  assert.equal(edges[0].target, 'workflow-node:device1')
  assert.equal(edges[0].sourceHandle, 'interface:Interface_workflow_out')
  assert.equal(edges[0].targetHandle, 'interface:Interface_workflow_in')
})

test('接口边为蓝色实线且保留具体接口Handle', () => {
  const edges = buildFlowEdges([{
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: 'branch', interfaceName: 'high' },
    target: { nodeName: 'heater', interfaceName: 'Interface_workflow_in' }
  }], [])
  assert.equal(edges[0].data.connectionKind, 'INTERFACE')
  assert.equal(edges[0].sourceHandle, 'interface:high')
  assert.equal(edges[0].style.stroke, '#3276d2')
  assert.equal(edges[0].style.strokeDasharray, undefined)
})

test('端口边为紫色虚线', () => {
  const edges = buildFlowEdges([], [{
    source: { nodeName: 'sensor', portName: 'temperatureOut' },
    target: { nodeName: 'heater', portName: 'targetIn' }
  }])
  assert.equal(edges[0].data.connectionKind, 'PORT')
  assert.equal(edges[0].sourceHandle, 'port:temperatureOut')
  assert.equal(edges[0].targetHandle, 'port:targetIn')
  assert.equal(edges[0].style.stroke, '#7c4dce')
  assert.equal(edges[0].style.strokeDasharray, '7 5')
})

test('状态控制接口与数据端口使用独立的Handle命名空间', () => {
  assert.equal(interfaceHandleId('Interface_state_out'), 'interface:Interface_state_out')
  assert.equal(portHandleId('temperatureOut'), 'port:temperatureOut')
})

test('禁止接口连接点与端口连接点交叉连接', () => {
  assert.throws(() => createCanvasConnection({
    sourceNodeName: 'a', sourceHandle: 'interface:Interface_workflow_out',
    targetNodeName: 'b', targetHandle: 'port:valueIn', nodes: canvasNodes
  }), /接口连接点不能连接数据端口/)
})

test('数据端口必须连接同类型的OUT到IN', () => {
  const created = createCanvasConnection({
    sourceNodeName: 'a', sourceHandle: portHandleId('valueOut'),
    targetNodeName: 'b', targetHandle: portHandleId('valueIn'), nodes: canvasNodes
  })
  assert.deepEqual(created, { collection: 'portConnections', value: {
    source: { nodeName: 'a', portName: 'valueOut' }, target: { nodeName: 'b', portName: 'valueIn' }
  } })
  assert.throws(() => createCanvasConnection({
    sourceNodeName: 'a', sourceHandle: portHandleId('valueOut'),
    targetNodeName: 'b', targetHandle: portHandleId('missing'), nodes: canvasNodes
  }), /端口不存在/)
})


test('数据端口引用缺失内部变量时拒绝连接', () => {
  const nodes = [canvasNodes[0], { ...canvasNodes[1], internalVariables: [] }]
  assert.throws(() => createCanvasConnection({
    sourceNodeName: 'a', sourceHandle: portHandleId('valueOut'),
    targetNodeName: 'b', targetHandle: portHandleId('valueIn'), nodes
  }), /内部变量不存在/)
})

test('数据端口引用异类型内部变量时拒绝连接', () => {
  const nodes = [canvasNodes[0], {
    ...canvasNodes[1], internalVariables: [{ name: 'targetTemperature', dataType: 'STRING' }]
  }]
  assert.throws(() => createCanvasConnection({
    sourceNodeName: 'a', sourceHandle: portHandleId('valueOut'),
    targetNodeName: 'b', targetHandle: portHandleId('valueIn'), nodes
  }), /端口数据类型不一致/)
})
test('creates a valid workflow interface connection and rejects invalid connections', () => {
  const connections = []
  const created = createNodeConnection({
    sourceNodeName: 'start1',
    targetNodeName: 'device1',
    nodes: [startNode, deviceNode],
    connections
  })
  assert.equal(created.connectionType, 'NODE_TO_NODE')
  assert.equal(created.source.interfaceName, 'Interface_workflow_out')
  assert.equal(created.target.interfaceName, 'Interface_workflow_in')
  assert.throws(() => createNodeConnection({
    sourceNodeName: 'device1',
    targetNodeName: 'device1',
    nodes: [deviceNode],
    connections
  }), /不能连接到自身/)
  assert.throws(() => createNodeConnection({
    sourceNodeName: 'start1',
    targetNodeName: 'device1',
    nodes: [startNode, deviceNode],
    connections: [created]
  }), /连接已存在/)
})

test('renames node endpoints without changing device endpoints', () => {
  const connections = [
    {
      connectionType: 'NODE_TO_NODE',
      source: { nodeName: 'old', interfaceName: 'out' },
      target: { nodeName: 'next', interfaceName: 'in' }
    },
    {
      connectionType: 'NODE_TO_DEVICE',
      source: { nodeName: 'old', interfaceName: 'state' },
      target: { deviceModelId: 10, interfaceName: 'workflow' }
    }
  ]
  renameNodeConnections(connections, 'old', 'new')
  assert.equal(connections[0].source.nodeName, 'new')
  assert.equal(connections[1].source.nodeName, 'new')
  assert.equal(connections[1].target.deviceModelId, 10)
})


test('节点重命名同步两类连接', () => {
  const interfaces = [{ source: { nodeName: 'old' }, target: { nodeName: 'b' } }]
  const ports = [{ source: { nodeName: 'old' }, target: { nodeName: 'c' } }]
  renameNodeConnections(interfaces, 'old', 'next')
  renameNodeConnections(ports, 'old', 'next')
  assert.equal(interfaces[0].source.nodeName, 'next')
  assert.equal(ports[0].source.nodeName, 'next')
})

test('从正确的双连接集合删除画布边', () => {
  const interfaces = [{ source: { nodeName: 'a', interfaceName: 'out' }, target: { nodeName: 'b', interfaceName: 'in' } }]
  const ports = [{ source: { nodeName: 'a', portName: 'valueOut' }, target: { nodeName: 'b', portName: 'valueIn' } }]
  const result = removeCanvasEdge({ data: { connectionKind: 'PORT' }, source: 'workflow-node:a', sourceHandle: 'port:valueOut', target: 'workflow-node:b', targetHandle: 'port:valueIn' }, interfaces, ports)
  assert.deepEqual(result, { interfaceConnections: interfaces, portConnections: [] })
})

test('removes editor-only fields from the workflow save payload', () => {
  const payload = sanitizeWorkflowPayload({
    id: 1,
    name: '测试流程',
    _draftLayoutKey: 'draft-1',
    nodesDef: [{ ...deviceNode, _editorId: 'x', position: { x: 1, y: 2 } }],
    interfaceConnections: [],
    portConnections: []
  })
  assert.equal(payload._draftLayoutKey, undefined)
  assert.equal(payload.nodesDef[0]._editorId, undefined)
  assert.equal(payload.nodesDef[0].position, undefined)
  assert.equal(payload.nodesDef[0].name, 'device1')
})


test('保存payload递归移除系统标识与画布字段', () => {
  const payload = sanitizeWorkflowPayload({ nodesDef: [{
    name: 'start', _system: true,
    actions: [{ actionName: 'emitActive', _systemKey: 'start.emitActive' }], position: { x: 10, y: 20 }
  }] })
  assert.deepEqual(payload.nodesDef[0], { name: 'start', actions: [{ actionName: 'emitActive' }] })
})

test('保存payload不携带编译或运行时派生数据', () => {
  const payload = sanitizeWorkflowPayload({
    name: '流程',
    compiled: { nodeRefs: { start: 1 } },
    runtime: { state: 'RUNNING' },
    nodesDef: [{ name: 'start', compiled: { ref: 1 }, runtime: { status: 'RUNNING' } }],
  })

  assert.deepEqual(payload, { name: '流程', nodesDef: [{ name: 'start' }] })
})
