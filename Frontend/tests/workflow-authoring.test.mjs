import test from 'node:test'
import assert from 'node:assert/strict'
import {
  adoptPreparedWorkflow,
  indexWorkflowIssues,
  toDesignerWorkflow,
  toWorkflowModelDocument,
  workflowModelName,
  workflowNodes,
} from '../src/utils/workflowAuthoring.js'

test('issues are indexed by model element instead of raw JSON path', () => {
  const index = indexWorkflowIssues([{ elementType: 'PORT', elementId: 'temperatureOut', blocking: true }])
  assert.equal(index.byElement.PORT.temperatureOut.length, 1)
})

test('designer form converts to schema model document without version or system markers', () => {
  const document = toWorkflowModelDocument({
    id: 9,
    name: '恒温反应',
    description: '说明',
    version: 3,
    status: 'ACTIVE',
    nodesDef: [{
      name: 'start1',
      nodeType: 'FUNC_NODE',
      _system: true,
      _systemKey: 'start.lifecycle',
      lifecycle: { initialStateName: 'PENDING', _system: true },
    }],
    interfaceConnections: [],
    portConnections: [],
  })

  assert.deepEqual(document.metadata, {
    flowModelId: 9,
    flowModelName: '恒温反应',
    description: '说明',
  })
  assert.equal(document.nodes[0].name, 'start1')
  assert.equal(document.nodes[0]._system, undefined)
  assert.equal(document.nodes[0].lifecycle._system, undefined)
  assert.equal(document.version, undefined)
  assert.equal(document.status, undefined)
  assert.equal(document.nodesDef, undefined)
})

test('detail view and preparation response hydrate the designer form', () => {
  const designer = toDesignerWorkflow({
    metadata: { flowModelId: 4, flowModelName: '测试流程', description: '' },
    nodes: [{ name: 'start1' }],
    interfaceConnections: [],
    portConnections: [],
    version: 2,
    status: 'ACTIVE',
  })
  assert.equal(designer.id, 4)
  assert.equal(designer.name, '测试流程')
  assert.equal(designer.version, 2)
  assert.equal(designer.status, 'ACTIVE')
  assert.equal(designer.nodesDef[0].name, 'start1')

  const adopted = adoptPreparedWorkflow({
    definition: {
      metadata: { flowModelId: 4, flowModelName: '测试流程', description: '' },
      nodes: [{ name: 'end1' }],
      interfaceConnections: [],
      portConnections: [],
    },
    version: 2,
    status: 'DRAFT',
  })
  assert.equal(adopted.name, '测试流程')
  assert.equal(adopted.status, 'DRAFT')
  assert.equal(adopted.nodesDef[0].name, 'end1')
})

test('workflow display helpers read unified flowModelName', () => {
  assert.equal(workflowModelName({ flowModelName: 'A' }), 'A')
  assert.equal(workflowModelName({ metadata: { flowModelName: 'B' } }), 'B')
  assert.equal(workflowModelName({ name: 'C' }), 'C')
  assert.equal(workflowNodes({ nodes: [{ name: 'n' }] })[0].name, 'n')
  assert.equal(workflowNodes({ nodesDef: [{ name: 'n2' }] })[0].name, 'n2')
})
