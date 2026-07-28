import { reactive } from 'vue'
import { loadFrontendContractMetadata } from '../../../../services/frontendContractMetadata.js'

// ==========================================
// 1. UI 静态常量与下拉菜单项
// ==========================================
export const attributeDataTypes = reactive([])
export const adapterDataTypes = reactive([])
export const operators = reactive([])
export const interfaceTypes = reactive([])
export const stateActionNames = reactive([])
export const adapterOutSignals = reactive([])
export const communicationProtocols = reactive([])
export const adapterRegisterFormats = reactive([])
export const commandLifecycleStates = reactive([])
export const workflowControlSignals = reactive([])
export const manualControlSignals = reactive([])
export const constraintControlSignals = reactive([])
export const statusSignals = reactive([])
export const standardInterfaces = reactive([])
export const executionLifecycleMainPath = reactive([])
export const systemTransitions = reactive([])
export const mqttTopics = reactive({
  registerTopic: '',
  heartbeatTopic: '',
  commandTopic: '',
  telemetryTopic: '',
  eventTopic: ''
})


function replaceArray(target, values) {
  if (!Array.isArray(values) || values.length === 0) return
  target.splice(0, target.length, ...values)
}

export function applyProtocolMetadata(metadata = {}) {
  const protocol = metadata.protocol || {}
  const stateMachine = metadata.stateMachine || {}
  const deviceCapability = metadata.deviceCapability || {}
  const constraint = metadata.constraint || {}
  const interfaces = Array.isArray(stateMachine.standardInterfaces) ? stateMachine.standardInterfaces : []
  const interfaceOf = (direction, type) => interfaces.find(item => item.direction === direction && item.interfaceType === type)
  const signalsOf = (direction, type) => interfaceOf(direction, type)?.allowedSignals || []

  replaceArray(attributeDataTypes, deviceCapability.dataTypes?.filter(type => type !== 'JSON'))
  replaceArray(adapterDataTypes, deviceCapability.dataTypes)
  replaceArray(operators, constraint.operators)
  replaceArray(communicationProtocols, protocol.communicationProtocols)
  replaceArray(adapterRegisterFormats, protocol.adapterRegisterFormats)
  replaceArray(stateActionNames, stateMachine.actionTypes)
  replaceArray(interfaceTypes, [...new Set(interfaces.map(item => item.interfaceType).filter(Boolean))])
  replaceArray(adapterOutSignals, signalsOf('OUT', 'ADAPTER'))
  const cmdStates = Array.isArray(stateMachine.commandStateNames) ? stateMachine.commandStateNames : []
  const normCmdStates = cmdStates.includes('ABORTING') ? cmdStates : [...cmdStates.filter(s => s !== 'ABORTED'), 'ABORTING', 'ABORTED']
  replaceArray(commandLifecycleStates, normCmdStates)
  replaceArray(workflowControlSignals, signalsOf('IN', 'WORKFLOW'))
  replaceArray(manualControlSignals, signalsOf('IN', 'CONTROL'))
  replaceArray(constraintControlSignals, signalsOf('IN', 'CONSTRAINT'))
  replaceArray(statusSignals, signalsOf('OUT', 'STATE'))
  replaceArray(standardInterfaces, interfaces)
  replaceArray(executionLifecycleMainPath, stateMachine.executionLifecycleMainPath)
  replaceArray(systemTransitions, stateMachine.systemTransitions)
  if (protocol.mqttTopics && typeof protocol.mqttTopics === 'object') {
    Object.assign(mqttTopics, protocol.mqttTopics)
  }
}
export async function loadProtocolMetadata() {
  const metadata = await loadFrontendContractMetadata()
  applyProtocolMetadata(metadata)
  return metadata
}

let protocolMetadataLoading = null

export async function ensureProtocolMetadataLoaded(loader = loadProtocolMetadata) {
  const metadataReady = () => communicationProtocols.length > 0
    && adapterDataTypes.length > 0
    && standardInterfaces.length > 0
  if (metadataReady()) return
  if (!protocolMetadataLoading) {
    protocolMetadataLoading = Promise.resolve().then(loader).finally(() => {
      protocolMetadataLoading = null
    })
  }
  await protocolMetadataLoading
  if (!metadataReady()) {
    throw new Error('设备模型规范元数据不完整')
  }
}
// ==========================================
// 2. 信号格式化与展示辅助函数
// ==========================================
export function getSignalTagType(signalName) {
  if (signalName === 'OP_STATE') return 'success'
  if (signalName === 'CMD_STATE') return 'primary'
  if (signalName === 'CMD_START') return 'danger'
  if (signalName === 'CMD_ABORT') return 'warning'
  return 'info'
}

export function formatSignalName(signalName) {
  if (signalName === 'OP_STATE') return '输出功能状态 (OP_STATE)'
  if (signalName === 'CMD_STATE') return '输出执行状态 (CMD_STATE)'
  if (signalName === 'CMD_START') return '下发启动命令 (CMD_START)'
  if (signalName === 'CMD_ABORT') return '下发中止命令 (CMD_ABORT)'
  return signalName || ''
}

export function formatSignalShortName(signalName) {
  if (signalName === 'OP_STATE') return '输出状态'
  if (signalName === 'CMD_STATE') return '输出指令'
  if (signalName === 'CMD_START') return '启动命令'
  if (signalName === 'CMD_ABORT') return '中止命令'
  return signalName || ''
}

export function getSignalsForInterface(interfaceName) {
  return asArray(standardInterfaces.find(item => item.name === interfaceName)?.allowedSignals)
}

// ==========================================
// 3. 状态机与契约默认配置结构
// ==========================================
const asArray = value => Array.isArray(value) ? value : []
const stringValue = value => value == null ? '' : String(value).trim()
const uniqueStrings = values => [...new Set(asArray(values).map(stringValue).filter(Boolean))]
const makeUiKey = prefix => prefix + '_' + Math.random().toString(36).substr(2, 9)

function normalizeDataType(value, fallback, allowed) {
  const text = String(value || '').toUpperCase()
  return allowed.includes(text) ? text : fallback
}

function configuredCommunicationProtocol() {
  return communicationProtocols[0] || ''
}

export function defaultAdapterContract() {
  return { config: { protocol: configuredCommunicationProtocol(), adapterName: '', categoryName: '' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
}

export function defaultStateSpace(initialStateName) {
  return {
    initialStateName,
    states: [{ _key: makeUiKey('state'), stateName: initialStateName, onEntry: defaultStateEntryActions('OP', initialStateName) }]
  }
}

export function defaultOpStateSpace(initialStateName) {
  return {
    regions: [
      {
        _key: makeUiKey('region'),
        regionName: 'Main',
        initialStateName,
        states: [{ _key: makeUiKey('state'), stateName: initialStateName, onEntry: defaultStateEntryActions('OP', initialStateName) }]
      },
      {
        _key: makeUiKey('region'),
        regionName: 'Exception',
        initialStateName: 'ABNORMAL',
        states: [{ _key: makeUiKey('state'), stateName: 'ABNORMAL', onEntry: defaultStateEntryActions('OP', 'ABNORMAL') }]
      }
    ]
  }
}

export function defaultCommandLifecycle() {
  return {
    initialStateName: 'IDLE',
    states: commandLifecycleStateNames().map(stateName => ({
      _key: makeUiKey('cmd_state'),
      stateName,
      onEntry: defaultStateEntryActions('CMD', stateName)
    }))
  }
}

export function commandLifecycleStateNames() {
  return commandLifecycleStates
}

export function defaultInterfaces(adapterSignals = []) {
  const adapterInName = standardInterfaceName('IN', 'ADAPTER')
  const adapterInSignals = uniqueStrings(asArray(adapterSignals).filter(Boolean))
  return asArray(standardInterfaces).map(item => ({
    _key: makeUiKey('iface'),
    name: stringValue(item.name),
    direction: stringValue(item.direction),
    interfaceType: stringValue(item.interfaceType),
    allowedSignals: item.name === adapterInName
      ? uniqueStrings([...asArray(item.allowedSignals), ...adapterInSignals])
      : uniqueStrings(item.allowedSignals)
  })).filter(item => item.name)
}

function standardInterfaceName(direction, type) {
  return standardInterfaces.find(item => item.direction === direction && item.interfaceType === type)?.name || ''
}

export function adapterInterfaceName() {
  return standardInterfaceName('IN', 'ADAPTER')
}

export function adapterOutAction(signalName) {
  return { actionName: 'SEND', payload: { interfaceName: standardInterfaceName('OUT', 'ADAPTER'), signalName } }
}

export function defaultStateEntryActions(type, stateName) {
  return [{
    actionName: 'SEND',
    payload: {
      interfaceName: standardInterfaceName('OUT', 'STATE'),
      signalName: type === 'CMD' ? 'CMD_STATE' : 'OP_STATE',
      stateName
    }
  }]
}

export function defaultCommandLifecycleTransitions() {
  return systemTransitions.map(transition => JSON.parse(JSON.stringify(transition)))
}
// ==========================================
// 4. 适配器 Manifest 配置映射逻辑
// ==========================================
export function adapterDeviceCategoryOptions(parsed) {
  if (parsed?.deviceCategories) {
    return asArray(parsed.deviceCategories).map(category => {
      const template = category?.deviceTemplate || {}
      const categoryName = stringValue(category?.categoryName)
      const templateName = stringValue(template.templateName)
      return {
        key: categoryName || templateName,
        name: categoryName || templateName || '未命名类别',
        description: stringValue(category?.categoryDescription || template.description),
        categoryName,
        templateName,
        category
      }
    }).filter(item => item.key)
  } else if (parsed?.deviceTemplates) {
    return asArray(parsed.deviceTemplates).map(template => {
      const templateName = stringValue(template?.templateName)
      const categoryName = stringValue(template?.categoryName || templateName)
      const description = stringValue(template?.categoryDescription || template?.description)
      const category = {
        categoryName,
        categoryDescription: description,
        deviceTemplate: template,
        devicePoints: asArray(parsed?.devicePoints).filter(pt => stringValue(pt?.templateName) === templateName)
      }
      return {
        key: categoryName || templateName,
        name: categoryName || templateName || '未命名类别',
        description,
        categoryName,
        templateName,
        category
      }
    }).filter(item => item.key)
  }
  return []
}

export function adapterCategoryKey(option) {
  return stringValue(option?.key || option?.categoryName || option?.templateName || option?.name)
}

export function adapterCategoryLabel(option) {
  const name = adapterCategoryKey(option) || '未命名类别'
  return option?.description ? name + ' - ' + option.description : name
}

function isManifestInternalParameter(param) {
  const internalValue = param?.internal
  return internalValue === true
    || String(internalValue ?? '').trim().toLowerCase() === 'true'
    || stringValue(param?.sourceField).length > 0
}
function normalizeCommandParameter(param) {
  return {
    _key: param._key || makeUiKey('cmd_param'),
    paramName: stringValue(param.paramName || param.name),
    dataType: normalizeDataType(param.dataType || param.type, 'DOUBLE', adapterDataTypes),
    description: stringValue(param.description),
    internal: param.internal === true,
    sourceField: stringValue(param.sourceField)
  }
}

function normalizeEventsToFlatList(events) {
  if (Array.isArray(events)) return []

  const rows = []
  asArray(events?.cmdEvents).forEach(event => {
    rows.push({
      _key: event._key || makeUiKey('event'),
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: 'CMD'
    })
  })
  asArray(events?.opEvents).forEach(event => {
    rows.push({
      _key: event._key || makeUiKey('event'),
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: 'OP'
    })
  })
  return rows
}

export function buildAdapterContractFromManifestCategory(parsed, category) {
  const template = category?.deviceTemplate || {}
  const commands = asArray(template.commands).map(command => ({
    commandName: stringValue(command.name || command.commandName),
    description: stringValue(command.description),
    commandParameters: asArray(command.parameters || command.commandParameters)
      .filter(param => !isManifestInternalParameter(param))
      .map(normalizeCommandParameter)
  }))

  const adapterAttributes = asArray(template.attributes).map(attr => ({
    telemetryName: stringValue(attr.telemetryName || attr.name),
    dataType: normalizeDataType(attr.dataType, 'DOUBLE', adapterDataTypes),
    description: stringValue(attr.description)
  }))

  return {
    config: {
      protocol: configuredCommunicationProtocol(),
      adapterName: stringValue(parsed.adapterName),
      categoryName: stringValue(category?.categoryName)
    },
    commands,
    telemetry: { adapterAttributes, attributesMapping: [] },
    events: normalizeEventsToFlatList(template.events)
  }
}
