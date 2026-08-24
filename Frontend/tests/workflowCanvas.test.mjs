import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildWorkflowAutoLayout,
  buildFlowEdges,
  buildFlowNodes,
  createCanvasConnection,
  createNodeConnection,
  interfaceHandleId,
  layoutKey,
  portHandleId,
  polylineHitsNodeInteriors,
  removeCanvasEdge,
  renameNodeConnections,
  sanitizeWorkflowPayload,
  serializeLayout,
  workflowCanvasNodeSize,
  workflowDeviceCapabilitySummary,
  workflowOrthogonalPoints,
  edgeLaneOffset,
  workflowInterfaceTooltip,
  workflowPortTooltip,
  workflowPortEdgeTooltip,
  workflowInterfaceEdgeTooltip,
  unwrapStoredLayout,
  wrapStoredLayout,
  WORKFLOW_LAYOUT_VERSION,
  formatWorkflowTriggerText,
  workflowPortValueSummary,
  workflowNodeSnapAnchor,
  snapWorkflowNodePosition,
} from '../src/utils/workflowCanvas.js'

function handleY(node, layout) {
  return layout[node.name].y + workflowNodeSnapAnchor(node)
}

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

test('连接点悬停信息包含方向、完整名称和内部变量名', () => {
  assert.equal(
    workflowInterfaceTooltip({ name: 'Interface_workflow_in', direction: 'IN' }),
    '控制输入 · Interface_workflow_in',
  )
  assert.equal(
    workflowInterfaceTooltip({ name: 'Interface_workflow_out', direction: 'OUT' }),
    '控制输出 · Interface_workflow_out',
  )
  assert.equal(
    workflowPortTooltip(canvasNodes[0].ports[0], canvasNodes[0]),
    '数据输出 · valueOut · temperature',
  )
  assert.equal(
    workflowPortEdgeTooltip(
      { source: { nodeName: 'a', portName: 'valueOut' }, target: { nodeName: 'b', portName: 'valueIn' } },
      canvasNodes,
    ),
    'temperature · 空',
  )
  assert.equal(
    workflowPortEdgeTooltip(
      { source: { nodeName: 'a', portName: 'valueOut' }, target: { nodeName: 'b', portName: 'valueIn' } },
      canvasNodes,
      36.5,
    ),
    'temperature · 36.5',
  )
  assert.equal(
    workflowInterfaceEdgeTooltip({
      source: { nodeName: 'device1', interfaceName: 'out' },
      target: { nodeName: 'branch1', interfaceName: 'in' },
    }),
    'device1 → branch1',
  )
})

test('设备能力节点摘要使用 displayName 和具体参数名', () => {
  assert.equal(workflowDeviceCapabilitySummary({}), '未选择能力')
  assert.equal(workflowDeviceCapabilitySummary({
    capability: { capabilityName: 'capability_1', capabilityParameters: { a: 1, b: 2 } },
  }), 'capability_1 · a、b')
  assert.equal(workflowDeviceCapabilitySummary({
    capability: { capabilityName: 'capability_1' },
  }, [{
    capabilityName: 'capability_1',
    displayName: '加热控制',
    parameters: [
      { name: 'targetTemp', displayName: '目标温度' },
      { name: 'duration', displayName: '持续时间' },
    ],
  }]), '加热控制 · 目标温度、持续时间')
})

test('画布节点尺寸随WORKFLOW接口和数据端口数量增长', () => {
  assert.deepEqual(workflowCanvasNodeSize(startNode), { width: 220, height: 44 })
  assert.deepEqual(workflowCanvasNodeSize({
    name: 'many-interfaces',
    interfaces: Array.from({ length: 5 }, (_, index) => ({ name: `in${index}`, direction: 'IN', interfaceType: 'WORKFLOW' })),
    ports: [],
  }), { width: 220, height: 168 })
  assert.deepEqual(workflowCanvasNodeSize({
    name: 'many-ports',
    interfaces: [],
    ports: Array.from({ length: 5 }, (_, index) => ({ name: `out${index}`, direction: 'OUT' })),
  }), { width: 280, height: 118 })
  assert.deepEqual(workflowCanvasNodeSize({
    name: 'branch',
    functionType: 'BRANCH',
    interfaces: [
      { name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' },
      ...Array.from({ length: 4 }, (_, index) => ({ name: `out${index}`, direction: 'OUT', interfaceType: 'WORKFLOW' })),
    ],
    ports: [],
  }), { width: 220, height: 176 })
})

test('trigger text summarizes output-interface conditions for branch rows', () => {
  assert.equal(formatWorkflowTriggerText({
    bindingTriggers: [{
      condition: { object: 'temperature', operator: '>', threshold: 80 },
    }],
  }), 'temperature > 80')
  assert.equal(formatWorkflowTriggerText({
    bindingTriggers: [{
      condition: { logic: 'AND', conditions: [
        { object: 'signalName', operator: '=', threshold: 'DONE' },
        { object: 'retryCount', operator: '>=', threshold: 3 },
      ] },
    }],
  }), '接收信号 = DONE 且 retryCount ≥ 3')
})

test('port value summary includes bound variable and initial value', () => {
  assert.deepEqual(workflowPortValueSummary({
    ports: [{ name: 'tempOut', internalVariableName: 'temperature' }],
    internalVariables: [{ name: 'temperature', dataType: 'DOUBLE', initialValue: 36.5 }],
  }, 'tempOut'), {
    portName: 'tempOut',
    variableName: 'temperature',
    dataType: 'DOUBLE',
    value: '36.5',
  })
})

test('snapping places control handles on the grid so different-height nodes can share a horizontal line', () => {
  const start = { functionType: 'START', interfaces: [{ name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' }] }
  const device = { interfaces: [
    { name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' },
    { name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' },
  ] }
  const startPos = snapWorkflowNodePosition(start, 80, 100)
  const handleLine = startPos.y + workflowNodeSnapAnchor(start)
  const devicePos = snapWorkflowNodePosition(device, 300, handleLine - workflowNodeSnapAnchor(device))
  assert.equal(startPos.x % 12, 0)
  assert.equal(handleLine % 12, 0)
  assert.equal(devicePos.y + workflowNodeSnapAnchor(device), handleLine)
})

test('自动布局从开始节点向右横排，单输出保持一行', () => {
  const nodes = [
    { name: 'start', functionType: 'START', interfaces: [{ name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' }] },
    { name: 'branch', functionType: 'BRANCH', interfaces: [{ name: 'only', direction: 'OUT', interfaceType: 'WORKFLOW' }] },
    { name: 'end', functionType: 'END', interfaces: [{ name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' }] },
  ]
  const connection = (source, target, interfaceName = 'out') => ({
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: source, interfaceName },
    target: { nodeName: target, interfaceName: 'in' },
  })
  const layout = buildWorkflowAutoLayout(nodes, [
    connection('start', 'branch', 'out'),
    connection('branch', 'end', 'only'),
  ], [])

  assert.ok(layout.start.x < layout.branch.x)
  assert.ok(layout.branch.x < layout.end.x)
  assert.equal(handleY(nodes[0], layout), handleY(nodes[1], layout))
  assert.equal(handleY(nodes[1], layout), handleY(nodes[2], layout))
})

test('自动布局按分支输出接口数分层，两路上下展开并在汇聚处收回', () => {
  const nodes = [
    { name: 'start', functionType: 'START', interfaces: [{ name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' }] },
    { name: 'branch', functionType: 'BRANCH', interfaces: [
      { name: 'high', direction: 'OUT', interfaceType: 'WORKFLOW' },
      { name: 'low', direction: 'OUT', interfaceType: 'WORKFLOW' },
    ] },
    { name: 'left', interfaces: [
      { name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' },
      { name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' },
    ] },
    { name: 'right', interfaces: [
      { name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' },
      { name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' },
    ] },
    { name: 'aggregate', functionType: 'AGGREGATE', interfaces: [
      { name: 'in1', direction: 'IN', interfaceType: 'WORKFLOW' },
      { name: 'in2', direction: 'IN', interfaceType: 'WORKFLOW' },
      { name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' },
    ] },
    { name: 'end', functionType: 'END', interfaces: [{ name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' }] },
  ]
  const connection = (source, target, sourceInterface, targetInterface = 'in') => ({
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: source, interfaceName: sourceInterface },
    target: { nodeName: target, interfaceName: targetInterface },
  })
  const layout = buildWorkflowAutoLayout(nodes, [
    connection('start', 'branch', 'out'),
    connection('branch', 'left', 'high'),
    connection('branch', 'right', 'low'),
    connection('left', 'aggregate', 'out', 'in1'),
    connection('right', 'aggregate', 'out', 'in2'),
    connection('aggregate', 'end', 'out'),
  ], [])

  assert.ok(layout.start.x < layout.branch.x)
  assert.ok(layout.branch.x < layout.left.x)
  assert.equal(layout.left.x, layout.right.x)
  assert.ok(layout.left.y < layout.right.y)
  assert.ok(layout.branch.y > layout.left.y)
  assert.ok(layout.branch.y < layout.right.y)
  assert.ok(layout.aggregate.x > layout.left.x)
  assert.ok(layout.aggregate.y > layout.left.y)
  assert.ok(layout.aggregate.y < layout.right.y)
  assert.ok(layout.end.x > layout.aggregate.x)
  assert.equal(handleY(nodes[0], layout), handleY(nodes[5], layout))
})

test('三路分支自动布局分成三层', () => {
  const nodes = [
    { name: 'branch', functionType: 'BRANCH', interfaces: [
      { name: 'a', direction: 'OUT', interfaceType: 'WORKFLOW' },
      { name: 'b', direction: 'OUT', interfaceType: 'WORKFLOW' },
      { name: 'c', direction: 'OUT', interfaceType: 'WORKFLOW' },
    ] },
    { name: 'one', interfaces: [{ name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' }] },
    { name: 'two', interfaces: [{ name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' }] },
    { name: 'three', interfaces: [{ name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' }] },
  ]
  const connection = (target, sourceInterface) => ({
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: 'branch', interfaceName: sourceInterface },
    target: { nodeName: target, interfaceName: 'in' },
  })
  const layout = buildWorkflowAutoLayout(nodes, [
    connection('one', 'a'),
    connection('two', 'b'),
    connection('three', 'c'),
  ], [])
  const ys = [layout.one.y, layout.two.y, layout.three.y]
  assert.equal(new Set(ys).size, 3)
  assert.ok(layout.one.y < layout.two.y)
  assert.ok(layout.two.y < layout.three.y)
  assert.equal(layout.one.x, layout.two.x)
  assert.equal(layout.two.x, layout.three.x)
  assert.ok(layout.branch.x < layout.one.x)
})

test('自动布局为断开节点和环路节点返回有限且互不重叠的位置', () => {
  const nodes = ['a', 'b', 'c'].map(name => ({ name, interfaces: [], ports: [] }))
  const connections = [
    { connectionType: 'NODE_TO_NODE', source: { nodeName: 'a' }, target: { nodeName: 'b' } },
    { connectionType: 'NODE_TO_NODE', source: { nodeName: 'b' }, target: { nodeName: 'a' } },
  ]
  const layout = buildWorkflowAutoLayout(nodes, connections, [])
  const positions = Object.values(layout)

  assert.deepEqual(Object.keys(layout).sort(), ['a', 'b', 'c'])
  assert.equal(positions.every(position => Number.isFinite(position.x) && Number.isFinite(position.y)), true)
  assert.equal(new Set(positions.map(position => `${position.x}:${position.y}`)).size, 3)
})

test('未接入执行流的结束节点与开始节点同一行', () => {
  const nodes = [
    { name: 'start', functionType: 'START', interfaces: [{ name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' }] },
    { name: 'device', interfaces: [
      { name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' },
      { name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' },
    ] },
    { name: 'end', functionType: 'END', interfaces: [{ name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' }] },
  ]
  const layout = buildWorkflowAutoLayout(nodes, [{
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: 'start', interfaceName: 'out' },
    target: { nodeName: 'device', interfaceName: 'in' },
  }], [])
  assert.equal(handleY(nodes[0], layout), handleY(nodes[2], layout))
  assert.equal(handleY(nodes[0], layout), handleY(nodes[1], layout))
  assert.ok(layout.end.x > layout.device.x)
})

test('builds deterministic editor nodes and restores saved positions by node name', () => {
  const nodes = buildFlowNodes([startNode, deviceNode], { device1: { x: 640, y: 180 } })
  assert.equal(nodes[0].id, 'workflow-node:start1')
  assert.deepEqual(nodes[0].position, { x: 80, y: 120 })
  assert.deepEqual(nodes[1].position, { x: 640, y: 180 })
})

test('未保存布局时多个节点默认排在同一行', () => {
  const nodes = buildFlowNodes(['a', 'b', 'c', 'd', 'e'].map(name => ({ name })))
  assert.equal(new Set(nodes.map(node => node.position.y)).size, 1)
  assert.ok(nodes[4].position.x > nodes[3].position.x)
})

test('stored layout version 2 round-trips and ignores legacy maps', () => {
  const wrapped = wrapStoredLayout([
    { position: { x: 10.2, y: 20.6 }, data: { nodeName: 'start1' } },
  ])
  assert.equal(wrapped.version, WORKFLOW_LAYOUT_VERSION)
  assert.deepEqual(unwrapStoredLayout(wrapped), { start1: { x: 10, y: 21 } })
  assert.deepEqual(unwrapStoredLayout({ start1: { x: 80, y: 400 } }), {})
  assert.deepEqual(unwrapStoredLayout({ version: 1, nodes: { start1: { x: 80, y: 400 } } }), {})
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
  assert.equal(edges[0].data.tooltip, 'start1 → device1')
})

test('间距足够的接口连线仍走简单折线', () => {
  const nodes = [
    { id: 'workflow-node:a', x: 40, y: 48, width: 220, height: 108 },
    { id: 'workflow-node:b', x: 400, y: 48, width: 220, height: 108 },
  ]
  const points = workflowOrthogonalPoints({
    sourceX: 260, sourceY: 102, targetX: 400, targetY: 102,
    sourcePosition: 'right', targetPosition: 'left',
    obstacles: nodes, sourceId: nodes[0].id, targetId: nodes[1].id,
  })
  assert.ok(points.length <= 4)
  assert.equal(new Set(points.map(point => point[1])).size, 1)
  assert.equal(polylineHitsNodeInteriors(points, nodes), false)
})

test('接口连线先走进两节点缝隙再从目标左侧进入', () => {
  const nodes = [
    { id: 'workflow-node:device2', x: 400, y: 48, width: 220, height: 108 },
    { id: 'workflow-node:end1', x: 400, y: 220, width: 220, height: 108 },
  ]
  const points = workflowOrthogonalPoints({
    sourceX: 620, sourceY: 102, targetX: 400, targetY: 274,
    sourcePosition: 'right', targetPosition: 'left',
    obstacles: nodes, sourceId: nodes[0].id, targetId: nodes[1].id,
  })
  const stub = points[1]
  assert.ok(stub[0] > 620)
  assert.ok(stub[0] - 620 <= 20)
  assert.equal(stub[1], 102)
  assert.ok(Math.min(...points.map(point => point[1])) >= 48)
  const gapY = points.find((point, index) => index > 0 && point[1] > 156 && point[1] < 220)?.[1]
  assert.equal(typeof gapY, 'number')
  assert.ok(points.some(point => point[1] === gapY && point[0] < 400))
  const last = points[points.length - 1]
  const prev = points[points.length - 2]
  assert.deepEqual(last, [400, 274])
  assert.ok(prev[0] < 400)
  assert.equal(prev[1], 274)
  assert.equal(polylineHitsNodeInteriors(points, nodes), false)
})

test('四节点叠放时接口线走缝隙且不穿过节点', () => {
  const nodes = [
    { id: 'workflow-node:device1', x: 40, y: 48, width: 220, height: 108 },
    { id: 'workflow-node:device2', x: 400, y: 48, width: 220, height: 108 },
    { id: 'workflow-node:branch1', x: 40, y: 220, width: 220, height: 108 },
    { id: 'workflow-node:end1', x: 400, y: 220, width: 220, height: 108 },
  ]
  const d2ToEnd = workflowOrthogonalPoints({
    sourceX: 620, sourceY: 102, targetX: 400, targetY: 274,
    sourcePosition: 'right', targetPosition: 'left',
    obstacles: nodes, sourceId: nodes[1].id, targetId: nodes[3].id,
  })
  const d1ToBranch = workflowOrthogonalPoints({
    sourceX: 260, sourceY: 102, targetX: 40, targetY: 274,
    sourcePosition: 'right', targetPosition: 'left',
    obstacles: nodes, sourceId: nodes[0].id, targetId: nodes[2].id,
  })
  for (const points of [d2ToEnd, d1ToBranch]) {
    assert.ok(Math.min(...points.map(point => point[1])) >= 48)
    assert.ok(points.some(point => point[1] > 156 && point[1] < 220))
    assert.equal(polylineHitsNodeInteriors(points, nodes), false)
  }
})

test('目标在上方时接口连线同样先走缝隙再进入左侧输入', () => {
  const nodes = [
    { id: 'workflow-node:end1', x: 400, y: 48, width: 220, height: 108 },
    { id: 'workflow-node:device2', x: 400, y: 220, width: 220, height: 108 },
  ]
  const points = workflowOrthogonalPoints({
    sourceX: 620, sourceY: 274, targetX: 400, targetY: 102,
    sourcePosition: 'right', targetPosition: 'left',
    obstacles: nodes, sourceId: nodes[1].id, targetId: nodes[0].id,
  })
  const gapY = points.find((point, index) => index > 0 && point[1] > 156 && point[1] < 220)?.[1]
  assert.equal(typeof gapY, 'number')
  assert.ok(Math.max(...points.map(point => point[1])) <= 328)
  assert.equal(polylineHitsNodeInteriors(points, nodes), false)
})

test('接口边为雾蓝实线且保留具体接口Handle', () => {
  const edges = buildFlowEdges([{
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: 'branch', interfaceName: 'high' },
    target: { nodeName: 'heater', interfaceName: 'Interface_workflow_in' }
  }], [])
  assert.equal(edges[0].data.connectionKind, 'INTERFACE')
  assert.equal(edges[0].sourceHandle, 'interface:high')
  assert.equal(edges[0].style.stroke, '#7c93b8')
  assert.equal(edges[0].style.strokeDasharray, undefined)
  assert.equal(edges[0].type, 'workflow')
  assert.equal(edges[0].data.laneOffset, 0)
})

test('端口边为灰紫虚线', () => {
  const edges = buildFlowEdges([], [{
    source: { nodeName: 'sensor', portName: 'temperatureOut' },
    target: { nodeName: 'heater', portName: 'targetIn' }
  }])
  assert.equal(edges[0].data.connectionKind, 'PORT')
  assert.equal(edges[0].sourceHandle, 'port:temperatureOut')
  assert.equal(edges[0].targetHandle, 'port:targetIn')
  assert.equal(edges[0].style.stroke, '#9a8ab5')
  assert.equal(edges[0].style.strokeDasharray, '6 5')
  assert.equal(edges[0].type, 'workflow')
  assert.equal(edges[0].data.laneOffset, 0)
  assert.equal(edges[0].data.tooltip, '未绑定变量 · 空')
})

test('同一来源的多条数据边右端口贴节点走内圈，竖段靠左，且进出下降高度一致', () => {
  const sensor = {
    name: 'sensor',
    ports: [
      { name: 'out1', direction: 'OUT' },
      { name: 'out2', direction: 'OUT' },
      { name: 'out3', direction: 'OUT' },
    ],
  }
  const edges = buildFlowEdges([], [
    { source: { nodeName: 'sensor', portName: 'out1' }, target: { nodeName: 'heater', portName: 'in1' } },
    { source: { nodeName: 'sensor', portName: 'out2' }, target: { nodeName: 'heater', portName: 'in2' } },
    { source: { nodeName: 'sensor', portName: 'out3' }, target: { nodeName: 'heater', portName: 'in3' } },
  ], [sensor])
  const ranks = edges.map(edge => edge.data.wrapRank)
  assert.deepEqual(ranks, [2, 1, 0])
  assert.equal(edges[2].data.wrapRank, 0)
  const nodes = [
    { id: 'workflow-node:sensor', x: 0, y: 80, width: 220, height: 108 },
    { id: 'workflow-node:heater', x: 340, y: 80, width: 220, height: 108 },
  ]
  const left = workflowOrthogonalPoints({
    sourceX: 55, sourceY: 188, targetX: 395, targetY: 80,
    sourcePosition: 'bottom', targetPosition: 'top',
    wrapRank: 2, wrapCount: 3,
    obstacles: nodes, sourceId: nodes[0].id, targetId: nodes[1].id,
  })
  const right = workflowOrthogonalPoints({
    sourceX: 165, sourceY: 188, targetX: 505, targetY: 80,
    sourcePosition: 'bottom', targetPosition: 'top',
    wrapRank: 0, wrapCount: 3,
    obstacles: nodes, sourceId: nodes[0].id, targetId: nodes[1].id,
  })
  assert.ok(exitDrop(right, 188) < exitDrop(left, 188))
  assert.ok(Math.min(...left.map(point => point[1])) < Math.min(...right.map(point => point[1])))
  assert.equal(exitDrop(left, 188), entryDrop(left, 80))
  assert.equal(exitDrop(right, 188), entryDrop(right, 80))
  assert.ok(verticalChannelX(right) < verticalChannelX(left))
  assert.ok(verticalChannelX(right) < 340)
  assert.equal(polylineHitsNodeInteriors(left, nodes), false)
  assert.equal(polylineHitsNodeInteriors(right, nodes), false)
  assert.equal(edgeLaneOffset(0, 1), 0)
})

function exitDrop(points, startY) {
  const turn = points.find((point, index) => index > 0 && point[0] === points[0][0])
  return Math.abs((turn?.[1] ?? startY) - startY)
}

function entryDrop(points, endY) {
  const last = points[points.length - 1]
  let approach = points[points.length - 2]
  for (let index = points.length - 2; index >= 0; index -= 1) {
    if (points[index][0] !== last[0]) break
    approach = points[index]
  }
  return Math.abs(endY - approach[1])
}

function verticalChannelX(points) {
  let bestX = points[0][0]
  let bestSpan = 0
  for (let index = 0; index < points.length - 1; index += 1) {
    if (Math.abs(points[index][0] - points[index + 1][0]) > 0.01) continue
    const span = Math.abs(points[index][1] - points[index + 1][1])
    if (span <= bestSpan) continue
    bestSpan = span
    bestX = points[index][0]
  }
  return bestX
}

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

test('每个WORKFLOW输入接口最多连接一个上游接口', () => {
  const sourceC = {
    name: 'c',
    interfaces: [{ name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' }],
  }
  const existing = {
    connectionType: 'NODE_TO_NODE',
    source: { nodeName: 'a', interfaceName: 'Interface_workflow_out' },
    target: { nodeName: 'b', interfaceName: 'Interface_workflow_in' },
  }

  assert.throws(() => createCanvasConnection({
    sourceNodeName: 'c', sourceHandle: interfaceHandleId('out'),
    targetNodeName: 'b', targetHandle: interfaceHandleId('Interface_workflow_in'),
    nodes: [...canvasNodes, sourceC], interfaceConnections: [existing],
  }), /输入接口已连接上游/)
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
