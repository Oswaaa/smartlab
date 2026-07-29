import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createServer } from 'vite'

const metadataLoaderFixture = {
  name: 'task-7-metadata-loader-fixture',
  enforce: 'pre',
  resolveId(source, importer) {
    return importer?.includes('deviceModelConstants.js') && source.includes('frontendContractMetadata.js') ? '\0task-7-metadata-loader-fixture' : undefined
  },
  load(id) {
    return id === '\0task-7-metadata-loader-fixture' ? 'export const loadFrontendContractMetadata = async () => ({})\nexport const invalidateFrontendContractMetadata = () => {}' : undefined
  }
}

const server = await createServer({ plugins: [metadataLoaderFixture], server: { middlewareMode: true }, appType: 'custom' })
const constants = await server.ssrLoadModule('/src/views/device/components/deviceModel/deviceModelConstants.js')
const normalizers = await server.ssrLoadModule('/src/views/device/components/deviceModel/normalizers.js')

after(() => server.close())

test('system command lifecycle transitions explicitly belong to CMD state space', () => {
  constants.applyProtocolMetadata({
    stateMachine: {
      commandStateNames: ['IDLE', 'SENT', 'RECEIVED', 'RUNNING', 'COMPLETED', 'ABORTED', 'FAILED'],
      systemTransitions: [{ stateSpace: 'CMD', fromStateName: 'IDLE', toStateName: 'SENT', trigger: { interfaceName: 'Interface_workflow_in', signalName: 'WF_EXECUTE_START' }, actions: [] }]
    }
  })
  const transitions = constants.defaultCommandLifecycleTransitions()
  assert.ok(transitions.length > 0)
  assert.ok(transitions.every(transition => transition.stateSpace === 'CMD'))
})

test('normalizes transition stateSpace without inferring it from state names', () => {
  const [transition] = normalizers.normalizeTransitions([{
    stateSpace: 'OP',
    fromStateName: 'IDLE',
    toStateName: 'RUNNING',
    trigger: { interfaceName: 'Interface_adapter_in', signalName: 'HEAT_STARTED' }
  }])
  assert.equal(transition.stateSpace, 'OP')
})

test('normalizes orthogonal operation regions and keeps transition region identity', () => {
  const stateSpace = normalizers.normalizeOperationStateSpace({
    regions: [
      { regionName: 'operatingMode', initialStateName: 'MANUAL', states: [{ stateName: 'MANUAL' }, { stateName: 'AUTOMATIC' }] },
      { regionName: 'cooling', initialStateName: 'IDLE', states: [{ stateName: 'IDLE' }, { stateName: 'COOLING' }] }
    ]
  })
  const [transition] = normalizers.normalizeTransitions([{
    stateSpace: 'OP',
    regionName: 'operatingMode',
    fromStateName: 'MANUAL',
    toStateName: 'AUTOMATIC',
    trigger: { interfaceName: 'Interface_adapter_in', signalName: 'AUTOMATIC_MODE_ENTERED' }
  }])
  assert.deepEqual(stateSpace.regions.map(region => region.regionName), ['operatingMode', 'cooling'])
  assert.equal(transition.regionName, 'operatingMode')
})

test('builds normal failure and termination lifecycle branches', async () => {
  const lifecycle = await server.ssrLoadModule('/src/views/device/components/deviceModel/deviceModelLifecycle.js')
  const rows = lifecycle.buildLifecycleRuleRows(
    [
      { fromStateName: 'IDLE', toStateName: 'SENT', triggerPolicy: 'SYSTEM' },
      { fromStateName: 'SENT', toStateName: 'RECEIVED', triggerPolicy: 'OPTIONAL' },
      { fromStateName: 'RECEIVED', toStateName: 'RUNNING', triggerPolicy: 'OPTIONAL' },
      { fromStateName: 'RUNNING', toStateName: 'COMPLETED', triggerPolicy: 'REQUIRED' }
    ],
    [
      { kind: 'FAILURE', targetStateName: 'FAILED', sourceStateNames: ['SENT', 'RECEIVED', 'RUNNING'] },
      { kind: 'TERMINATION', targetStateName: 'ABORTED', sourceStateNames: ['SENT', 'RECEIVED', 'RUNNING'] }
    ]
  )
  assert.equal(rows.filter(row => row.kind === 'MAIN').length, 3)
  assert.equal(rows.filter(row => row.kind === 'FAILURE').length, 3)
  assert.equal(rows.filter(row => row.kind === 'TERMINATION').length, 3)
})

test('preserves adapter command and operation event domains even when names look similar', () => {
  const contract = normalizers.normalizeAdapterContract({
    events: {
      cmdEvents: [{ eventName: 'DONE' }],
      opEvents: [{ eventName: 'COMMAND_FAULT' }]
    }
  })
  assert.deepEqual(
    contract.events.map(event => [event.eventName, event.eventType]),
    [['DONE', 'CMD'], ['COMMAND_FAULT', 'OP']]
  )
})
test('does not infer event domains from legacy flat event names', () => {
  const contract = normalizers.normalizeAdapterContract({
    events: [{ eventName: 'COMMAND_FAULT' }]
  })
  assert.deepEqual(contract.events, [])
})

test('device model adapter contract excludes adapter-internal command parameters', () => {
  const category = {
    categoryName: 'Reactor',
    deviceTemplate: {
      templateName: 'ReactorTemplate',
      commands: [{
        name: 'heat',
        parameters: [
          { name: 'target', dataType: 'DOUBLE', internal: false },
          { name: 'index', dataType: 'INTEGER', internal: true, sourceField: 'index' }
        ]
      }]
    }
  }
  const contract = constants.buildAdapterContractFromManifestCategory({}, category)
  assert.deepEqual(contract.commands[0].commandParameters.map(item => item.paramName), ['target'])
})

test('normalizing a persisted model contract defensively removes internal parameters', () => {
  const contract = normalizers.normalizeAdapterContract({
    commands: [{
      commandName: 'heat',
      commandParameters: [
        { paramName: 'target', dataType: 'DOUBLE' },
        { paramName: 'index', dataType: 'INTEGER', internal: true, sourceField: 'index' }
      ]
    }]
  })
  assert.deepEqual(contract.commands[0].commandParameters.map(item => item.paramName), ['target'])
})


test('execution lifecycle metadata removes STARTED and keeps automatic triggers null', () => {
  constants.applyProtocolMetadata({
    stateMachine: {
      commandStateNames: ['IDLE', 'SENT', 'RECEIVED', 'RUNNING', 'COMPLETED', 'ABORTED', 'FAILED']
    }
  })
  assert.equal(constants.commandLifecycleStateNames().includes('STARTED'), false)
  const [transition] = normalizers.normalizeTransitions([{
    stateSpace: 'CMD',
    fromStateName: 'SENT',
    toStateName: 'RECEIVED',
    trigger: null
  }])
  assert.equal(transition.trigger, null)
})

test('normalizing a model contract rejects every adapter-internal parameter representation', () => {
  const contract = normalizers.normalizeAdapterContract({
    commands: [{
      commandName: 'heat',
      commandParameters: [
        { paramName: 'target', dataType: 'DOUBLE' },
        { paramName: 'legacyBoolean', dataType: 'INTEGER', internal: true, sourceField: 'index' },
        { paramName: 'textBoolean', dataType: 'INTEGER', internal: 'true', sourceField: 'index' },
        { paramName: 'sourceBound', dataType: 'INTEGER', sourceField: 'index' }
      ]
    }]
  })
  assert.deepEqual(contract.commands[0].commandParameters.map(item => item.paramName), ['target'])
})

test('opening an editor requires complete model metadata instead of rendering local fallbacks', async () => {
  constants.executionLifecycleMainPath.splice(0)
  constants.systemTransitions.splice(0)
  let loadCount = 0
  await constants.ensureProtocolMetadataLoaded(async () => {
    loadCount += 1
    constants.applyProtocolMetadata({
      protocol: { communicationProtocols: ['MQTT'] },
      deviceCapability: { dataTypes: ['INTEGER', 'DOUBLE', 'STRING', 'BOOLEAN', 'JSON'] },
      stateMachine: {
        standardInterfaces: [{
          name: 'Interface_workflow_in',
          direction: 'IN',
          interfaceType: 'WORKFLOW',
          allowedSignals: ['WF_EXECUTE_START', 'WF_EXECUTE_ABORT']
        }],
        executionLifecycleMainPath: [
          { fromStateName: 'IDLE', toStateName: 'SENT', triggerPolicy: 'SYSTEM' },
          { fromStateName: 'SENT', toStateName: 'RECEIVED', triggerPolicy: 'OPTIONAL' }
        ],
        systemTransitions: [
          { stateSpace: 'CMD', fromStateName: 'IDLE', toStateName: 'SENT', trigger: { interfaceName: 'Interface_workflow_in', signalName: 'WF_EXECUTE_START' }, actions: [] }
        ]
      }
    })
  })
  assert.equal(loadCount, 1)
  assert.equal(constants.executionLifecycleMainPath.length, 2)
  assert.equal(constants.systemTransitions.length, 1)
})