import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canDragWorkflowResource,
  clearWorkflowCanvas,
  formatWorkflowExpression,
  protocolSignalCandidates,
  serializeWorkflowExpression,
  validateWorkflowExpression,
  workflowExpressionDisplayTokens,
  workflowExpressionMentions,
  workflowNodeConnectionIssues,
  workflowUnconnectedPortIssues,
  workflowTemporalFunctions,
  workflowCopyName,
  workflowLibraryGroups,
  workflowStatusLabel,
  workflowSuccessorConflict,
  workflowVersionLabel,
} from '../src/utils/workflowDesignerRules.js'

test('workflow library allows dragging another workflow only while editing', () => {
  const current = { id: 12, flowName: '当前流程' }
  const child = { id: 13, flowName: '子流程' }
  assert.equal(canDragWorkflowResource(child, current.id, true), true)
  assert.equal(canDragWorkflowResource(current, current.id, true), false)
  assert.equal(canDragWorkflowResource(child, current.id, false), false)
  assert.equal(canDragWorkflowResource({ flowName: '未保存流程' }, current.id, true), false)
})

test('workflow library groups active and draft models without changing records', () => {
  const active = { id: 1, flowName: '已启用', status: 'ACTIVE' }
  const draft = { id: 2, flowName: '草稿', status: 'DRAFT' }
  assert.deepEqual(workflowLibraryGroups([draft, active]), [
    { key: 'active', label: '已启用流程', children: [active] },
    { key: 'draft', label: '草稿流程', children: [draft] },
  ])
})

test('workflow library groups always keep both empty sections', () => {
  assert.deepEqual(workflowLibraryGroups([]), [
    { key: 'active', label: '已启用流程', children: [] },
    { key: 'draft', label: '草稿流程', children: [] },
  ])
})

test('workflow library item badges use stored version and status', () => {
  assert.equal(workflowVersionLabel({ version: 2 }), 'v2')
  assert.equal(workflowVersionLabel({}), 'v1')
  assert.equal(workflowStatusLabel('ACTIVE'), '已启用')
  assert.equal(workflowStatusLabel('DRAFT'), '草稿')
})

test('copy name starts a new lineage label and increments copies', () => {
  assert.equal(workflowCopyName('恒温反应'), '恒温反应（副本）')
  assert.equal(workflowCopyName('恒温反应（副本）'), '恒温反应（副本 2）')
  assert.equal(workflowCopyName('恒温反应（副本 2）'), '恒温反应（副本 3）')
  assert.equal(workflowCopyName('  '), '未命名流程（副本）')
  assert.equal(workflowCopyName('A'.repeat(80)).length, 80)
  assert.ok(workflowCopyName('A'.repeat(80)).endsWith('（副本）'))
})

test('successor conflict payload exposes the existing follow-up version', () => {
  assert.equal(workflowSuccessorConflict({ success: true, data: { successorId: 8 } }), null)
  assert.deepEqual(workflowSuccessorConflict({
    success: false,
    message: '已有后续版本',
    data: { successorId: 8, version: 2, status: 'DRAFT', flowModelName: '恒温反应' },
  }), {
    successorId: 8,
    version: 2,
    status: 'DRAFT',
    flowModelName: '恒温反应',
    message: '已有后续版本',
  })
})

test('clear canvas preserves flow identity and removes only nodes and connections', () => {
  const source = { id: 8, name: '反应流程', description: '说明', nodesDef: [{}], interfaceConnections: [{}], portConnections: [{}] }
  assert.deepEqual(clearWorkflowCanvas(source), {
    ...source,
    nodesDef: [],
    interfaceConnections: [],
    portConnections: [],
  })
})

test('signal candidates come from protocol metadata and hide subflow completion from custom interfaces', () => {
  const protocol = {
    workflowNodeSignals: ['ACTIVE', 'SUBFLOW_COMPLETED'],
    workflowControlSignals: ['WF_EXECUTE_START', 'WF_EXECUTE_ABORT'],
    statusSignals: ['CMD_STATE', 'OP_STATE'],
  }
  assert.deepEqual(protocolSignalCandidates(protocol, { interfaceType: 'WORKFLOW', direction: 'OUT' }, false), ['ACTIVE'])
  assert.deepEqual(protocolSignalCandidates(protocol, { interfaceType: 'STATE', direction: 'OUT' }, false), protocol.workflowControlSignals)
  assert.deepEqual(protocolSignalCandidates(protocol, { interfaceType: 'STATE', direction: 'IN' }, false), protocol.statusSignals)
  assert.deepEqual(protocolSignalCandidates(protocol, { interfaceType: 'WORKFLOW', direction: 'IN' }, true), protocol.workflowNodeSignals)
})

test('workflow assignment expression serializes mentions and validates temporal functions', () => {
  assert.equal(serializeWorkflowExpression('@temperature / 100'), 'temperature / 100')
  const variables = [{ name: 'temperature', dataType: 'DOUBLE' }, { name: 'temperatureRate', dataType: 'DOUBLE' }]
  assert.deepEqual(validateWorkflowExpression('@temperatureRate = abs(rate(@temperature, 10))', variables, { assignment: true, temporal: true }), [])
  assert.match(validateWorkflowExpression('@temperature / 100', variables, { assignment: true })[0], /赋值/)
  assert.match(validateWorkflowExpression('@unknown + 1', variables)[0], /未匹配/)
  assert.match(validateWorkflowExpression('rate(@temperature, 10)', variables)[0], /不支持函数/)
  assert.deepEqual(workflowTemporalFunctions.map(item => item.name), ['变化速率', '变化量', '窗口平均值', '窗口最大值', '窗口最小值', '绝对值'])
})

test('workflow assignment validation distinguishes an undeclared target from a non-numeric target', () => {
  const variables = [
    { name: 'temperature', dataType: 'DOUBLE' },
    { name: 'statusText', dataType: 'STRING' },
  ]

  assert.deepEqual(
    validateWorkflowExpression('@missing = @temperature / 2', variables, { assignment: true }),
    ['赋值目标未在变量空间中声明：missing'],
  )
  assert.deepEqual(
    validateWorkflowExpression('@statusText = @temperature / 2', variables, { assignment: true }),
    ['赋值目标必须是数值类型（INTEGER 或 DOUBLE）：statusText'],
  )
})

test('temporal function metadata explains editable window arguments', () => {
  const average = workflowTemporalFunctions.find(item => item.functionName === 'avg')
  const absolute = workflowTemporalFunctions.find(item => item.functionName === 'abs')

  assert.equal(average.defaultWindowSeconds, 60)
  assert.match(average.description, /窗口秒数.*可修改/)
  assert.equal(average.template, 'avg(, 60)')
  assert.equal(average.example, 'avg(@温度, 60)')
  assert.equal(absolute.defaultWindowSeconds, undefined)
  assert.equal(absolute.template, 'abs()')
})

test('workflow mentions resolve by exact variable lookup instead of identifier grammar', () => {
  const variables = [{ name: '温度输入', dataType: 'DOUBLE' }, { name: '温度变量', dataType: 'DOUBLE' }, { name: 'in', dataType: 'DOUBLE' }, { name: 'internal', dataType: 'DOUBLE' }]
  const source = '@温度变量 = @温度输入 / 10'
  assert.deepEqual(workflowExpressionMentions(source), [
    { name: '温度变量', start: 0, end: 5 },
    { name: '温度输入', start: 8, end: 13 },
  ])
  assert.equal(serializeWorkflowExpression(source, variables.map(item => item.name)), '温度变量 = 温度输入 / 10')
  assert.deepEqual(validateWorkflowExpression(source, variables, { assignment: true }), [])
  assert.deepEqual(workflowExpressionMentions('@in + @internal'), [
    { name: 'in', start: 0, end: 3 },
    { name: 'internal', start: 6, end: 15 },
  ])
})

test('workflow expression editor highlights mentions while result text is compiled and formatted', () => {
  const variableNames = ['variable1', 'variable2']
  assert.deepEqual(workflowExpressionDisplayTokens('@variable1 =@variable2 /2', variableNames), [
    { value: '@variable1', variable: true },
    { value: ' =', variable: false },
    { value: '@variable2', variable: true },
    { value: ' /2', variable: false },
  ])
  assert.equal(
    formatWorkflowExpression('@variable1 =@variable2 /2', variableNames),
    'variable1 = variable2 / 2',
  )
  assert.equal(
    formatWorkflowExpression('@variable1=-2+abs(@variable2)+rate(@variable2,10)', variableNames),
    'variable1 = -2 + abs(variable2) + rate(variable2, 10)',
  )
})

test('isolated branch reports only missing upstream and downstream connections', () => {
  const nodes = [
    { name: 'start', functionType: 'START', interfaces: [] },
    { name: 'branch', functionType: 'BRANCH', interfaces: [] },
    { name: 'end', functionType: 'END', interfaces: [] },
  ]
  assert.deepEqual(workflowNodeConnectionIssues(nodes, []), [
    {
      code: 'missing-out-start',
      title: '未连接下游节点',
      detail: '请将此节点连接到后续节点。',
      nodeName: 'start',
      path: 'topology',
    },
    {
      code: 'missing-in-branch',
      title: '未连接上游节点',
      detail: '请将前一个节点连接到此节点。',
      nodeName: 'branch',
      path: 'topology',
    },
    {
      code: 'missing-out-branch',
      title: '未连接下游节点',
      detail: '请将此节点连接到后续节点。',
      nodeName: 'branch',
      path: 'topology',
    },
    {
      code: 'missing-in-end',
      title: '未连接上游节点',
      detail: '请将前一个节点连接到此节点。',
      nodeName: 'end',
      path: 'topology',
    },
  ])
})

test('branch with a single connected output is a valid workflow topology', () => {
  const nodes = [
    { name: 'start', functionType: 'START', interfaces: [{ name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW' }] },
    { name: 'branch', functionType: 'BRANCH', interfaces: [{ name: 'only_out', direction: 'OUT', interfaceType: 'WORKFLOW' }] },
    { name: 'end', functionType: 'END', interfaces: [{ name: 'in', direction: 'IN', interfaceType: 'WORKFLOW' }] },
  ]
  const connections = [
    { source: { nodeName: 'start', interfaceName: 'out' }, target: { nodeName: 'branch', interfaceName: 'in' } },
    { source: { nodeName: 'branch', interfaceName: 'only_out' }, target: { nodeName: 'end', interfaceName: 'in' } },
  ]
  assert.deepEqual(workflowNodeConnectionIssues(nodes, connections), [])
})

test('聚合节点由输出阈值决定N-of-M且每个输入接口只能连接一次', () => {
  const aggregate = {
    name: 'aggregate', functionType: 'AGGREGATE',
    interfaces: [
      { name: 'in_1', direction: 'IN', interfaceType: 'WORKFLOW' },
      { name: 'in_2', direction: 'IN', interfaceType: 'WORKFLOW' },
      {
        name: 'out', direction: 'OUT', interfaceType: 'WORKFLOW', bindingTriggers: [{
          condition: { logic: 'AND', conditions: [
            { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' },
            { object: 'aggregateCount', operator: '>=', threshold: 3 },
          ] },
          action: { actionName: 'EMIT', payload: { targetInterfaceName: 'out', signalName: 'ACTIVE' } },
        }],
      },
    ],
  }
  const nodes = [
    { name: 'start', functionType: 'START' },
    { name: 'relay', nodeType: 'SUBFLOW_NODE' },
    aggregate,
    { name: 'end', functionType: 'END' },
  ]
  const connections = [
    { source: { nodeName: 'start', interfaceName: 'out' }, target: { nodeName: 'aggregate', interfaceName: 'in_1' } },
    { source: { nodeName: 'start', interfaceName: 'out' }, target: { nodeName: 'relay', interfaceName: 'in' } },
    { source: { nodeName: 'relay', interfaceName: 'out' }, target: { nodeName: 'aggregate', interfaceName: 'in_2' } },
    { source: { nodeName: 'aggregate', interfaceName: 'out' }, target: { nodeName: 'end', interfaceName: 'in' } },
  ]

  const thresholdIssues = workflowNodeConnectionIssues(nodes, connections)
  assert.ok(thresholdIssues.some(issue => /聚合阈值不可达/.test(issue.title)))
  aggregate.interfaces[2].bindingTriggers[0].condition.conditions[1].threshold = 2
  assert.deepEqual(workflowNodeConnectionIssues(nodes, connections), [])

  connections[2].target.interfaceName = 'in_1'
  assert.ok(workflowNodeConnectionIssues(nodes, connections)
    .some(issue => /输入接口重复连接/.test(issue.title)))
})

test('reachability issues appear only after a node already has the relevant connection', () => {
  const nodes = [
    { name: 'start', functionType: 'START', interfaces: [] },
    { name: 'main', nodeType: 'DEV_NODE', interfaces: [] },
    { name: 'detachedA', nodeType: 'DEV_NODE', interfaces: [] },
    { name: 'detachedB', nodeType: 'DEV_NODE', interfaces: [] },
    { name: 'end', functionType: 'END', interfaces: [] },
  ]
  const connections = [
    { source: { nodeName: 'start' }, target: { nodeName: 'main' } },
    { source: { nodeName: 'main' }, target: { nodeName: 'end' } },
    { source: { nodeName: 'detachedA' }, target: { nodeName: 'detachedB' } },
  ]
  const issues = workflowNodeConnectionIssues(nodes, connections)
  assert.ok(issues.some(issue => issue.code === 'unreachable-detachedB'))
  assert.ok(issues.some(issue => issue.code === 'no-end-detachedA'))
  assert.ok(!issues.some(issue => issue.code === 'unreachable-detachedA'))
  assert.ok(!issues.some(issue => issue.code === 'no-end-detachedB'))
})

test('unconnected data ports are reminders and empty port lists are not errors', () => {
  const nodes = [
    { name: 'start', functionType: 'START', ports: [] },
    { name: 'heater', functionType: undefined, ports: [{ name: 'tempOut', direction: 'OUT' }, { name: 'setpointIn', direction: 'IN' }] },
    { name: 'end', functionType: 'END', ports: [{ name: 'resultIn', direction: 'IN' }] },
  ]
  assert.deepEqual(workflowUnconnectedPortIssues(nodes, []), [
    {
      code: 'unconnected-ports-heater',
      title: '有未连接端口',
      detail: '数据端口 tempOut、setpointIn 尚未连接，节点仍可使用内部变量运行。',
      nodeName: 'heater',
      path: 'ports',
    },
    {
      code: 'unconnected-ports-end',
      title: '有未连接端口',
      detail: '数据端口 resultIn 尚未连接，节点仍可使用内部变量运行。',
      nodeName: 'end',
      path: 'ports',
    },
  ])
  assert.deepEqual(workflowUnconnectedPortIssues(nodes, [
    { source: { nodeName: 'heater', portName: 'tempOut' }, target: { nodeName: 'end', portName: 'resultIn' } },
  ]), [
    {
      code: 'unconnected-ports-heater',
      title: '有未连接端口',
      detail: '数据端口 setpointIn 尚未连接，节点仍可使用内部变量运行。',
      nodeName: 'heater',
      path: 'ports',
    },
  ])
})
