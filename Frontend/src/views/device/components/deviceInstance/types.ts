export interface DeviceModel {
  modelId: string
  modelName: string
  deviceCategory: string
  categoryId?: number | string
  capabilitySpec: any
  opState?: any
  cmdState?: any
  attributes?: any[]
  capabilities?: any[]
  intrinsicConstraints?: any[]
  intrinsicConstraint?: any[]
}

export interface AdapterOption {
  adapterName: string
  parsedConfig?: any
}

export interface AdapterDevicePoint {
  devicePoint: string
  templateName: string
  description?: string
}

export interface DeviceInstance {
  instanceId: string
  modelId: string
  stateMachineId: string
  instanceName: string
  boundAdapterName?: string
  boundDevicePoint?: string
  instanceConfig?: any
  lifecycleStatus: 'IN_USE' | 'RETIRED'
  isOnline: boolean
  onlineStatus?: string
  online?: boolean
}

export interface DeviceSnapshot {
  instanceId: string
  currentCommandState?: string
  currentOperationState?: Record<string, string[]>
  latestAttributes?: Record<string, any>
  onlineStatus?: string
}

export interface InstanceConstraint {
  objectAttributeName: string
  operator: '>' | '<' | '>=' | '<=' | '=' | '!=' | 'BETWEEN' | 'IN'
  boundaryValue: number | null
  unit: string
  violationStateName: string
  description: string
}

export interface AssetInfo {
  serialNumber: string
  purchaseDate: string
  instalDate: string
  location: string
  notes: string
}

export interface MqttTopicRow {
  type: string
  label: string
  direction: string
  topic: string
}

export interface ControlGroup {
  key: string
  name: string
  shortName: string
  modelName: string
  isComponent: boolean
  targetInstanceId: string
  boundAdapterName: string
  boundDevicePoint: string
  capabilities: any[]
}
