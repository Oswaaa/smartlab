import { mqttTopics } from '../deviceModel/deviceModelConstants'
import type { DeviceInstance, DeviceModel, InstanceConstraint, MqttTopicRow } from './types'

export function asArray<T = any>(value: any): T[] {
  return Array.isArray(value) ? value : []
}

export function emptyAssetInfo() {
  return {
    serialNumber: '',
    purchaseDate: '',
    instalDate: '',
    location: '',
    notes: ''
  }
}

export function normalizeModel(raw: any): DeviceModel {
  return {
    ...raw,
    modelId: String(raw.modelId || raw.id || ''),
    modelName: raw.modelName || raw.name || '',
    deviceCategory: raw.deviceCategory || raw.categoryName || '',
    capabilitySpec: raw.capabilitySpec || {
      attributes: raw.attributes || [],
      capabilities: raw.capabilities || [],
      adapterContract: raw.adapterContract || {
        config: { protocol: 'MQTT' },
        commands: [],
        telemetry: { adapterAttributes: [], attributesMapping: [] },
        events: []
      }
    }
  }
}

export function normalizeInstance(raw: any): DeviceInstance {
  const instanceConfig = raw.instanceConfig || {}
  return {
    ...raw,
    instanceId: String(raw.instanceId || raw.id || ''),
    modelId: String(raw.modelId || raw.deviceModelId || ''),
    stateMachineId: raw.stateMachineId || `${raw.modelId || raw.deviceModelId || ''}StateMachine`,
    instanceName: raw.instanceName || raw.name || '',
    boundAdapterName: raw.boundAdapterName || '',
    boundDevicePoint: raw.boundDevicePoint || '',
    instanceConfig,
    lifecycleStatus: raw.lifecycleStatus,
    isOnline: raw.isOnline === true || raw.onlineStatus === 'ONLINE'
  }
}

export function cloneInstanceForEdit(instance: DeviceInstance): DeviceInstance {
  const cloned = JSON.parse(JSON.stringify(instance)) as DeviceInstance
  if (!cloned.instanceConfig) cloned.instanceConfig = {}
  if (!cloned.instanceConfig.assetInfo) cloned.instanceConfig.assetInfo = emptyAssetInfo()
  cloned.boundAdapterName ||= ''
  cloned.boundDevicePoint ||= ''
  return cloned
}

export function findModelById(
  modelId: string,
  models: DeviceModel[],
  modelOptions: DeviceModel[] = []
): DeviceModel | null {
  if (!modelId) return null
  return modelOptions.find(v => v.modelId === modelId) || models.find(v => v.modelId === modelId) || null
}

export function getModelAttributes(modelId: string, models: DeviceModel[], modelOptions: DeviceModel[] = []) {
  const model = findModelById(modelId, models, modelOptions)
  return model?.capabilitySpec?.attributes || model?.attributes || []
}

export function getModelName(modelId: string, models: DeviceModel[], modelOptions: DeviceModel[] = []) {
  const model = findModelById(modelId, models, modelOptions)
  return model ? model.modelName : modelId
}

export function propKey(prop: any) {
  return String(prop?.attributeName || '')
}

export function propLabel(prop: any) {
  return String(prop?.displayName || prop?.attributeName || '未命名属性')
}

export function normalizeConstraintOperator(op: string) {
  return op || '<='
}

export function operatorLabel(op: string) {
  if (op === '<=') return '≤ 小于等于'
  if (op === '>=') return '≥ 大于等于'
  if (op === '<') return '< 小于'
  if (op === '>') return '> 大于'
  if (op === '=') return '= 等于'
  if (op === '!=' || op === 'NE') return '≠ 不等于'
  return op
}

export function constraintsFromSource(list: any[]): InstanceConstraint[] {
  return asArray(list).map((c: any) => ({
    objectAttributeName: c.objectAttributeName || '',
    operator: normalizeConstraintOperator(c.operator || '<='),
    boundaryValue: c.boundaryValue != null ? c.boundaryValue : null,
    unit: c.unit || '',
    violationStateName: c.violationStateName || '',
    description: c.description || ''
  }))
}

export function serializeConstraints(list: InstanceConstraint[]) {
  return list
    .filter(c => c.objectAttributeName && c.boundaryValue !== null)
    .map(c => ({
      objectAttributeName: c.objectAttributeName,
      operator: c.operator,
      boundaryValue: c.boundaryValue,
      unit: c.unit || '',
      violationStateName: c.violationStateName || '',
      description: c.description || ''
    }))
}

export function buildInstanceSavePayload(instance: DeviceInstance, constraints: InstanceConstraint[]) {
  const payload = JSON.parse(JSON.stringify(instance))
  const asset = instance.instanceConfig?.assetInfo || emptyAssetInfo()
  payload.boundAdapterName = instance.boundAdapterName
  payload.boundDevicePoint = instance.boundDevicePoint
  payload.instanceConfig = {
    assetInfo: {
      serialNumber: asset.serialNumber || '',
      purchaseDate: asset.purchaseDate || '',
      instalDate: asset.instalDate || '',
      location: asset.location || '',
      notes: asset.notes || ''
    },
    intrinsicConstraints: serializeConstraints(constraints)
  }
  delete payload.assetInfo
  delete payload.localConstraints
  delete payload.commConfig
  return payload
}

export function resolveTopicPattern(pattern: string, variables: Record<string, string>) {
  return String(pattern || '').replace(/\{([^}]+)\}/g, (_, key) => variables[key] || '')
}

export function mqttTopicRows(adapterName?: string, devicePoint?: string): MqttTopicRow[] {
  const ready = !!adapterName && !!devicePoint
  const variables = { adapterName: adapterName || '', devicePoint: devicePoint || '' }
  return [
    { type: 'command', label: '命令 (Command)', direction: '系统 → Adapter', topic: ready ? resolveTopicPattern(mqttTopics.commandTopic, variables) : '' },
    { type: 'telemetry', label: '遥测 (Telemetry)', direction: 'Adapter → 系统', topic: ready ? resolveTopicPattern(mqttTopics.telemetryTopic, variables) : '' },
    { type: 'event', label: '事件 (Event)', direction: 'Adapter → 系统', topic: ready ? resolveTopicPattern(mqttTopics.eventTopic, variables) : '' },
    { type: 'heartbeat', label: '心跳 (Heartbeat)', direction: 'Adapter → 系统', topic: adapterName ? resolveTopicPattern(mqttTopics.heartbeatTopic, variables) : '' }
  ]
}

export function devicePointLabel(point: { devicePoint: string, description?: string }) {
  return point.description ? `${point.devicePoint} · ${point.description}` : point.devicePoint
}

export function templateIdOf(template: any) {
  return String(template?.templateId || template?.id || '')
}

export function formatTime(time: any) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

export function paramKey(param: any) {
  return String(param.name || param.paramName || '')
}

export function normalizeType(type: any) {
  return String(type || '').toUpperCase()
}

export function isBooleanType(type: any) {
  return normalizeType(type) === 'BOOLEAN' || normalizeType(type) === 'BOOL'
}

export function isIntegerType(type: any) {
  return ['INTEGER', 'INT', 'LONG'].includes(normalizeType(type))
}

export function isNumberType(type: any) {
  return isIntegerType(type) || ['DOUBLE', 'FLOAT', 'NUMBER', 'DECIMAL'].includes(normalizeType(type))
}

export function defaultValueForType(type: any) {
  if (isBooleanType(type)) return false
  if (isNumberType(type)) return 0
  return ''
}

export function cmdStateClass(state?: string) {
  const s = String(state || '').toUpperCase()
  if (s.includes('ABORTING') || s.includes('SENT') || s.includes('RECEIV')) return 'running'
  if (s.includes('RUN') || s.includes('EXEC') || s.includes('DO')) return 'running'
  if (s.includes('COMPLET') || s.includes('FINISH') || s.includes('SUCCESS')) return 'completed'
  if (s.includes('FAIL') || s.includes('ERR') || s.includes('ABORT')) return 'error'
  return 'idle'
}

export function cmdStateDescription(state?: string) {
  const s = String(state || '').toUpperCase()
  if (!s || s === 'IDLE' || s === 'CMD_IDLE') return '空闲待命中'
  if (s === 'SENT' || s === 'CMD_SENT') return '指令已下发，等待驱动确认'
  if (s === 'RECEIVED' || s === 'CMD_RECEIVED') return '驱动已接收'
  if (s.includes('ABORTING')) return '正在终止中'
  if (s.includes('RUN') || s.includes('EXEC')) return '动作执行中'
  if (s.includes('COMPLET') || s.includes('SUCCESS')) return '执行成功完成'
  if (s.includes('ABORT')) return '执行已被终止'
  if (s.includes('FAIL') || s.includes('ERR')) return '执行失败异常'
  return `${state}`
}

export function componentStatusType(status?: string) {
  if (status === 'IN_USE') return 'success'
  if (status === 'PENDING_REPLACEMENT') return 'warning'
  if (status === '已更换') return 'info'
  return 'info'
}

export function componentSpecificationLabel(value: any) {
  if (!value || (typeof value === 'object' && !Object.keys(value).length)) return '未填写'
  return '查看详情'
}

export function buildCategoryTree(flat: any[]): any[] {
  const map = new Map<string, any>()
  flat.forEach(c => map.set(String(c.id), { ...c, children: [] }))
  const roots: any[] = []
  flat.forEach(c => {
    if (c.parentCategoryId) {
      const parent = map.get(String(c.parentCategoryId))
      if (parent) parent.children!.push(map.get(String(c.id))!)
    } else {
      roots.push(map.get(String(c.id))!)
    }
  })
  const markLeaf = (nodes: any[]) => {
    nodes.forEach(n => {
      if (!n.children || n.children.length === 0) {
        n.isLeaf = true
        n.children = undefined
      } else {
        markLeaf(n.children)
      }
    })
  }
  markLeaf(roots)
  return roots
}

export function categoryTreeForSelect(categories: any[]) {
  const tree = buildCategoryTree(categories)
  const mapNode = (n: any): any => ({
    id: String(n.id),
    label: n.categoryName,
    children: n.children && n.children.length ? n.children.map(mapNode) : undefined
  })
  return tree.map(mapNode)
}
