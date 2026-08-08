import { attributeDataTypes, adapterDataTypes, operators, defaultStateSpace, defaultOpStateSpace, defaultAdapterContract, defaultInterfaces, defaultCommandLifecycleTransitions } from './deviceModelConstants'

export function asArray(value) { return Array.isArray(value) ? value : [] }

export function firstDefined(...values) { return values.find(value => value !== undefined && value !== null) }

export function deepClone(value) { return JSON.parse(JSON.stringify(value ?? [])) }

export function ensureArrayField(target, key) { if (!Array.isArray(target[key])) target[key] = []; return target[key] }

export function isNumeric(value) { return value !== '' && value != null && !Number.isNaN(Number(value)) }

export function numericOrNull(value) { return isNumeric(value) ? Number(value) : null }

export function stringValue(value) { return value == null ? '' : String(value).trim() }

export function stringId(value) { return value == null ? '' : String(value) }

export function normalizeDataType(value, fallback, allowed) { const text = String(value || '').toUpperCase(); return allowed.includes(text) ? text : fallback }

export function makeUiKey(prefix) { return prefix + '_' + Math.random().toString(36).substr(2, 9) }

export function findKeyByName(rows, name) { return asArray(rows).find(item => item.name === name)?._key || '' }

export function lookupName(nameByKey, key) {
  if (!key || !nameByKey) return ''
  return nameByKey instanceof Map ? nameByKey.get(key) || '' : nameByKey[key] || ''
}

export function reserveIdentifier(value, fallback, used) {
  const base = stringValue(value) || fallback
  let candidate = base
  let index = 2
  while (used.has(candidate)) {
    candidate = base + '_' + index
    index += 1
  }
  used.add(candidate)
  return candidate
}

export function materializedName(item, fallback, used) {
  return reserveIdentifier(item?.name, fallback, used)
}

export function eventTypeLabel(type) {
  const upper = String(type || '').toUpperCase()
  if (upper === 'CMD') return '执行生命周期'
  if (upper === 'OP') return '业务事件'
  return '事件'
}

export function operatorLabel(operator) {
  const labels = { '>': '大于', '<': '小于', '>=': '大于等于', '<=': '小于等于', '=': '等于', '!=': '不等于', BETWEEN: '介于', IN: '属于' }
  return labels[operator] || operator || '-'
}

export function valueKindLabel(value) { return value === 'DISCRETE' ? '离散值' : '连续值' }

export function directionLabel(value) { return value === 'IN' ? '输入' : '输出' }

export function formatTime(value) { return value ? String(value).replace('T', ' ') : '-' }

export function formatJson(value) { return JSON.stringify(value || {}, null, 2) }

export function dataTypeOptions(base, current) { return current && !base.includes(current) ? [...base, current] : base }

export function describeAction(action) {
  if (!action?.actionName) return ''
  if (action.actionName === 'SEND') {
    const interfaceName = action.payload?.interfaceName || '接口'
    const signalName = action.payload?.signalName || '信号'
    return '输出 ' + interfaceName + ' / ' + signalName
  }
  return action.actionName
}

export function capabilityParamDisplayName(capability, paramName) {
  if (!paramName) return '-'
  const param = asArray(capability?.parameters).find(item => item.name === paramName || item.displayName === paramName)
  return param?.displayName || paramName
}

export function functionMappingGroups(capabilities) {
  return asArray(capabilities).map((capability, index) => {
    const mappings = asArray(capability.parameterMapping).map((mapping, mappingIndex) => ({
      key: String(index) + '-' + (mapping.commandParamName || mappingIndex),
      commandParamName: mapping.commandParamName || '-',
      capabilityParamDisplayName: mapping.isFixedValue ? '' : capabilityParamDisplayName(capability, mapping.capabilityParamName),
      isFixedValue: !!mapping.isFixedValue,
      fixedValue: mapping.fixedValue ?? ''
    }))
    return {
      key: capability.name || capability.displayName || String(index),
      capabilityDisplayName: capability.displayName || '未命名操作',
      adapterCommandName: capability.adapterCommandName || '-',
      parameters: mappings
    }
  })
}

export function capabilityMappingRows(capabilities) {
  return functionMappingGroups(capabilities).flatMap(group => {
    if (group.parameters.length === 0) {
      return [{
        capabilityDisplayName: group.capabilityDisplayName,
        adapterCommandName: group.adapterCommandName,
        commandParamName: '-',
        sourceLabel: '未配置参数映射'
      }]
    }
    return group.parameters.map(param => ({
      capabilityDisplayName: group.capabilityDisplayName,
      adapterCommandName: group.adapterCommandName,
      commandParamName: param.commandParamName,
      sourceLabel: param.isFixedValue ? '固定值：' + (param.fixedValue ?? '') : param.capabilityParamDisplayName
    }))
  })
}

export function displayAttributeName(name, attributes) { if (!name) return '-'; const attr = asArray(attributes).find(item => item.name === name); return attr?.displayName || name }

export function isAdapterInternalParameter(param) {
  const internalValue = param?.internal
  return internalValue === true
    || String(internalValue ?? '').trim().toLowerCase() === 'true'
    || stringValue(param?.sourceField).length > 0
}

export function normalizeCommandParameter(param) {
  const internal = isAdapterInternalParameter(param)
  const normalized = {
    paramName: stringValue(param.paramName || param.name),
    dataType: normalizeDataType(param.dataType, 'DOUBLE', adapterDataTypes),
    description: stringValue(param.description),
    internal
  }
  if (internal) {
    normalized.sourceField = stringValue(param.sourceField)
  }
  return normalized
}

export function normalizeEventsToFlatList(events) {
  if (!events || Array.isArray(events)) return []
  return [
    ...asArray(events.cmdEvents).map(event => ({
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: 'CMD'
    })),
    ...asArray(events.opEvents).map(event => ({
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: 'OP'
    }))
  ]
}

export function normalizeAttributes(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('attr'), name: stringValue(item.attributeName || item.name), displayName: stringValue(item.displayName || item.attributeName || item.name), valueKind: item.valueKind === 'DISCRETE' ? 'DISCRETE' : 'CONTINUOUS', dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes), unit: stringValue(item.unit) })) }

export function normalizeCapabilities(value) {
  const capabilities = asArray(value).map(item => {
    const params = asArray(item.parameters).map(param => ({
      _key: param._key || makeUiKey('param'),
      name: stringValue(param.name),
      displayName: stringValue(param.displayName || param.name),
      dataType: normalizeDataType(param.dataType, 'DOUBLE', attributeDataTypes)
    }))
    const parameterMapping = asArray(item.parameterMapping).map(mapping => ({
      _key: mapping._key || makeUiKey('param_map'),
      commandParamName: stringValue(mapping.commandParamName),
      capabilityParamName: stringValue(mapping.capabilityParamName),
      capabilityParamKey: mapping.capabilityParamKey || findKeyByName(params, mapping.capabilityParamName),
      isFixedValue: !!mapping.isFixedValue,
      fixedValue: mapping.fixedValue ?? ''
    }))
    const isAbort = item.isAbort === true
    return {
      _key: item._key || makeUiKey('cap'),
      name: stringValue(item.capabilityName || item.name),
      displayName: stringValue(item.displayName || item.capabilityName || item.name),
      adapterCommandName: stringValue(item.adapterCommandName),
      isAbort,
      abortCapabilityName: isAbort ? null : (stringValue(item.abortCapabilityName) || null),
      abortCapabilityKey: '',
      scope: isAbort ? asArray(item.scope).map(stringValue).filter(Boolean) : [],
      scopeCapabilityKeys: [],
      parameters: params,
      parameterMapping
    }
  })
  const capabilityKeyByName = new Map()
  capabilities.forEach(capability => {
    if (capability.name && !capabilityKeyByName.has(capability.name)) capabilityKeyByName.set(capability.name, capability._key)
  })
  capabilities.forEach(capability => {
    if (!capability.isAbort) {
      capability.abortCapabilityKey = capabilityKeyByName.get(capability.abortCapabilityName) || ''
      return
    }
    capability.scopeCapabilityKeys = capability.scope.map(name => capabilityKeyByName.get(name)).filter(Boolean)
  })
  return capabilities
}

export function extractFunctionMappings(capabilities, normalizedCapabilities = null) {
  const sourceRows = asArray(capabilities)
  const normalizedRows = normalizedCapabilities || normalizeCapabilities(capabilities)
  return normalizedRows.map((capability, index) => {
    const source = sourceRows.find(item => stringValue(item.name) === capability.name) || sourceRows[index] || {}
    return {
      _key: makeUiKey('function_map'),
      capabilityKey: capability._key,
      adapterCommandName: stringValue(source.adapterCommandName || capability.adapterCommandName),
      parameterMapping: asArray(source.parameterMapping).map(mapping => ({
        _key: mapping._key || makeUiKey('param_map'),
        commandParamName: stringValue(mapping.commandParamName),
        capabilityParamName: stringValue(mapping.capabilityParamName),
        capabilityParamKey: mapping.capabilityParamKey || findKeyByName(capability.parameters, mapping.capabilityParamName),
        isFixedValue: !!mapping.isFixedValue,
        fixedValue: mapping.fixedValue ?? ''
      }))
    }
  })
}

export function normalizeFunctionMappings(value) {
  return asArray(value).map(item => ({
    _key: item._key || makeUiKey('function_map'),
    capabilityKey: item.capabilityKey || '',
    adapterCommandName: stringValue(item.adapterCommandName),
    parameterMapping: asArray(item.parameterMapping).map(mapping => ({
      _key: mapping._key || makeUiKey('param_map'),
      commandParamName: stringValue(mapping.commandParamName),
      capabilityParamName: stringValue(mapping.capabilityParamName),
      capabilityParamKey: mapping.capabilityParamKey || '',
      isFixedValue: !!mapping.isFixedValue,
      fixedValue: mapping.fixedValue ?? ''
    }))
  }))
}

export function normalizeAdapterContract(value, attributes = []) {
  const contract = value || {}
  const telemetry = contract.telemetry || {}

  let eventsList = []
  if (contract.events && typeof contract.events === 'object' && !Array.isArray(contract.events)) {
    asArray(contract.events.cmdEvents).forEach(event => {
      eventsList.push({
        _key: event._key || makeUiKey('event'),
        eventName: stringValue(event.eventName || event.name),
        description: stringValue(event.description),
        eventType: 'CMD'
      })
    })
    asArray(contract.events.opEvents).forEach(event => {
      eventsList.push({
        _key: event._key || makeUiKey('event'),
        eventName: stringValue(event.eventName || event.name),
        description: stringValue(event.description),
        eventType: 'OP'
      })
    })
  } else {
    eventsList = []
  }

  return {
    config: { protocol: contract.config?.protocol || contract.protocol || '', adapterName: stringValue(contract.config?.adapterName || contract.adapterName), categoryName: stringValue(contract.config?.categoryName || contract.categoryName) },
    commands: asArray(contract.commands).map(command => ({
      _key: command._key || makeUiKey('cmd'),
      commandName: stringValue(command.commandName || command.name),
      description: stringValue(command.description),
      commandParameters: asArray(command.commandParameters || command.parameters)
        .filter(param => !isAdapterInternalParameter(param))
        .map(param => ({ _key: param._key || makeUiKey('cmd_param'), paramName: stringValue(param.paramName || param.name), dataType: normalizeDataType(param.dataType || param.type, 'DOUBLE', adapterDataTypes), description: stringValue(param.description) }))
    })),
    telemetry: { adapterAttributes: asArray(telemetry.adapterAttributes).map(attr => ({ _key: attr._key || makeUiKey('adapter_attr'), telemetryName: stringValue(attr.telemetryName), dataType: normalizeDataType(attr.dataType || attr.type, 'DOUBLE', adapterDataTypes), description: stringValue(attr.description) })), attributesMapping: asArray(telemetry.attributesMapping).map(mapping => ({ _key: mapping._key || makeUiKey('attr_map'), adapterAttrName: stringValue(mapping.adapterAttrName), modelAttributeName: stringValue(mapping.modelAttributeName), modelAttributeKey: mapping.modelAttributeKey || findKeyByName(attributes, mapping.modelAttributeName) })) },
    events: eventsList
  }
}

export function normalizePorts(value, attributes = []) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('port'), portName: stringValue(item.portName), displayName: stringValue(item.displayName || item.portName), direction: item.direction || 'OUT', bindingAttrName: stringValue(item.bindingAttrName), bindingAttrKey: item.bindingAttrKey || findKeyByName(attributes, item.bindingAttrName) })) }

export function normalizeOperator(value) {
  const text = String(value || '').trim()
  if (!text) return '>'
  if (!operators.includes(text)) throw new Error(`不支持的约束操作符: ${text}`)
  return text
}

export function normalizeIntrinsicConstraints(value, attributes = []) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('constraint'), objectAttributeName: stringValue(item.objectAttributeName), objectAttributeKey: item.objectAttributeKey || findKeyByName(attributes, item.objectAttributeName), operator: normalizeOperator(item.operator), boundaryValue: firstDefined(item.boundaryValue, ''), violationStateName: stringValue(item.violationStateName) })) }

export function normalizeInterfaces(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('iface'), name: stringValue(item.name), direction: item.direction || 'IN', interfaceType: item.interfaceType || 'ADAPTER', allowedSignals: asArray(item.allowedSignals).map(stringValue).filter(Boolean) })) }

export function normalizeStateSpace(value, fallback, spaceType) {
  if (spaceType === 'OP') {
    const source = value && typeof value === 'object' ? value : { regions: [] }
    const regions = asArray(source.regions).length > 0 ? asArray(source.regions) : defaultOpStateSpace(fallback).regions
    return {
      regions: regions.map((region, index) => {
        const regionType = stringValue(region.regionType).toUpperCase()
        const states = asArray(region.states).length > 0 ? asArray(region.states) : [{ stateName: fallback }]
        return {
          _key: region._key || makeUiKey('region'),
          regionName: stringValue(region.regionName || ('分区 ' + (index + 1))),
          regionType,
          initialStateName: regionType === 'EXCEPTION' ? '' : stringValue(region.initialStateName || states[0]?.stateName || fallback),
          states: states.map(item => {
            const onEntry = asArray(item.onEntry).map(action => ({
              _key: action._key || makeUiKey('action'),
              actionName: stringValue(action.actionName),
              payload: normalizePayload(action.payload || action.parameters)
            }))
            return {
              _key: item._key || makeUiKey('state'),
              stateName: stringValue(item.stateName || item.name),
              onEntry
            }
          })
        }
      })
    }
  }

  const source = value && typeof value === 'object' ? value : defaultStateSpace(fallback)
  const states = asArray(source.states).length ? asArray(source.states) : defaultStateSpace(fallback).states
  return {
    initialStateName: stringValue(source.initialStateName || states[0]?.stateName || fallback),
    states: states.map(item => {
      const onEntry = asArray(item.onEntry).map(action => ({
        _key: action._key || makeUiKey('action'),
        actionName: stringValue(action.actionName),
        payload: normalizePayload(action.payload || action.parameters)
      }))
      return {
        _key: item._key || makeUiKey('state'),
        stateName: stringValue(item.stateName || item.name),
        onEntry
      }
    })
  }
}

export function normalizeTransitions(value) {
  return asArray(value).map(item => {
    const stateSpace = String(item.stateSpace || '').toUpperCase()
    if (!['CMD', 'OP'].includes(stateSpace)) {
      throw new Error('状态转移必须明确声明 stateSpace 为 CMD 或 OP')
    }
    const regionName = stateSpace === 'OP' ? stringValue(item.regionName) : undefined
    if (stateSpace === 'OP' && !regionName) {
      throw new Error('OP 状态转移必须指定 regionName')
    }
    return {
      _key: item._key || makeUiKey('transition'),
      stateSpace,
      regionName,
      description: stringValue(item.description),
      fromStateName: stringValue(item.fromStateName),
      toStateName: stringValue(item.toStateName),
      trigger: item.trigger == null ? null : {
        interfaceName: stringValue(item.trigger.interfaceName || adapterInterfaceName()),
        signalName: stringValue(item.trigger.signalName)
      },
      actions: asArray(item.actions).map(action => ({
        _key: action._key || makeUiKey('action'),
        actionName: stringValue(action.actionName),
        payload: normalizePayload(action.payload || action.parameters)
      }))
    }
  })
}

export function normalizePayload(value) { return value && typeof value === 'object' && !Array.isArray(value) ? value : {} }

export function normalizeModelBundle(value) {
  return {
    capabilityModel: value?.capabilityModel || {},
    stateMachineModel: value?.stateMachineModel || {}
  }
}

export function materializeAttributes(rows) {
  const nameByKey = new Map()
  const rowByKey = new Map()
  const usedNames = new Set()
  const materializedRows = asArray(rows).filter(item => stringValue(item.name || item.displayName)).map((item, index) => {
    const name = materializedName(item, 'attribute_' + (index + 1), usedNames)
    const row = { attributeName: name, displayName: stringValue(item.displayName || name), valueKind: item.valueKind || 'CONTINUOUS', dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes), unit: stringValue(item.unit) }
    nameByKey.set(item._key, name)
    rowByKey.set(item._key, row)
    return row
  })
  return { rows: materializedRows, nameByKey, rowByKey }
}

export function materializePorts(rows, attrNameByKey) {
  const usedNames = new Set()
  return asArray(rows).filter(item => stringValue(item.portName || item.displayName) || item.bindingAttrKey || item.bindingAttrName).map((item, index) => ({
    portName: reserveIdentifier(item.portName || item.displayName, 'port_' + (index + 1), usedNames),
    direction: item.direction || 'OUT',
    bindingAttrName: lookupName(attrNameByKey, item.bindingAttrKey) || stringValue(item.bindingAttrName)
  })).filter(item => item.portName && item.bindingAttrName)
}

export function materializeCapabilities(rows, functionMappings = []) {
  const usedCapabilityNames = new Set()
  const preparedRows = asArray(rows).filter(item => stringValue(item.name || item.displayName)).map((item, index) => ({
    item,
    name: materializedName(item, 'capability_' + (index + 1), usedCapabilityNames),
    mapping: functionMappings.find(fm => fm.capabilityKey === item._key)
  }))
  const capabilityNameByKey = new Map(preparedRows.map(row => [row.item._key, row.name]))
  const capabilityByKey = new Map(preparedRows.map(row => [row.item._key, row.item]))

  return preparedRows.map(({ item, name, mapping }) => {
    const paramNames = new Map()
    const usedParamNames = new Set()
    const parameters = asArray(item.parameters).filter(param => stringValue(param.name || param.displayName)).map((param, paramIndex) => {
      const name = materializedName(param, 'parameter_' + (paramIndex + 1), usedParamNames)
      paramNames.set(param._key, name)
      return { name, displayName: stringValue(param.displayName || name), dataType: normalizeDataType(param.dataType, 'DOUBLE', attributeDataTypes) }
    })
    const isAbort = item.isAbort === true
    const capability = {
      capabilityName: name,
      adapterCommandName: mapping?.adapterCommandName || item.adapterCommandName,
      displayName: stringValue(item.displayName || name),
      isAbort,
      abortCapabilityName: isAbort
        ? null
        : (lookupName(capabilityNameByKey, item.abortCapabilityKey) || stringValue(item.abortCapabilityName) || null),
      parameters,
      parameterMapping: materializeParameterMappings(mapping?.parameterMapping || item.parameterMapping, paramNames)
    }
    if (isAbort) {
      const scopeNames = asArray(item.scopeCapabilityKeys)
        .filter(key => capabilityByKey.get(key)?.isAbort !== true)
        .map(key => lookupName(capabilityNameByKey, key))
        .filter(Boolean)
      const fallbackScopeNames = asArray(item.scope).map(stringValue).filter(Boolean)
      capability.scope = [...new Set(scopeNames.length ? scopeNames : fallbackScopeNames)]
    }
    return capability
  })
}

export function materializeParameterMappings(rows, paramNames) {
  return asArray(rows).map(pm => {
    const commandParamName = stringValue(pm.commandParamName)
    const isFixedValue = !!pm.isFixedValue
    const out = { commandParamName, isFixedValue }
    if (isFixedValue) {
      out.fixedValue = pm.fixedValue
    } else {
      out.capabilityParamName = lookupName(paramNames, pm.capabilityParamKey) || stringValue(pm.capabilityParamName)
    }
    return out
  }).filter(pm => pm.commandParamName && (pm.isFixedValue || pm.capabilityParamName))
}

export function materializeFunctionMappings(rows) {
  return asArray(rows).map(row => ({ capabilityKey: row.capabilityKey, adapterCommandName: row.adapterCommandName, parameterMapping: row.parameterMapping }))
}

export function materializeIntrinsicConstraints(rows, attrNameByKey) {
  return asArray(rows).map(row => ({
    objectAttributeName: lookupName(attrNameByKey, row.objectAttributeKey) || stringValue(row.objectAttributeName),
    operator: normalizeOperator(row.operator),
    boundaryValue: isNumeric(row.boundaryValue) ? Number(row.boundaryValue) : row.boundaryValue,
    violationStateName: stringValue(row.violationStateName)
  })).filter(row => row.objectAttributeName && row.operator && row.violationStateName)
}

export function categoryNameById(id, categories = []) {
  return asArray(categories).find(item => String(item.id) === String(id))?.categoryName || ''
}

export function isCommandLifecycleTransition(row) {
  return defaultCommandLifecycleTransitions().some(item =>
    item.fromStateName === row?.fromStateName &&
    item.toStateName === row?.toStateName &&
    item.trigger.interfaceName === row?.trigger?.interfaceName &&
    item.trigger.signalName === row?.trigger?.signalName
  )
}

export function normalizeModel(raw, categories = []) {
  const modelId = String(raw?.modelId || raw?.id || '')
  const categoryId = raw?.categoryId == null ? '' : String(raw.categoryId)
  const attributes = normalizeAttributes(firstDefined(raw?.attributes, raw?.capabilitySpec?.attributes))
  const adapterContract = normalizeAdapterContract(firstDefined(raw?.adapterContract, raw?.capabilitySpec?.adapterContract), attributes)
  const capabilities = normalizeCapabilities(firstDefined(raw?.capabilities, raw?.capabilitySpec?.capabilities, raw?.capabilitySpec?.functions))
  return {
    ...raw,
    modelId,
    categoryId,
    categoryName: raw?.categoryName || categoryNameById(categoryId, categories),
    attributes,
    capabilities,
    adapterContract,
    ports: normalizePorts(firstDefined(raw?.ports, raw?.capabilitySpec?.ports), attributes),
    intrinsicConstraints: normalizeIntrinsicConstraints(firstDefined(raw?.intrinsicConstraints, raw?.intrinsicConstraint), attributes),
    stateMachineInterfaces: normalizeInterfaces(raw?.stateMachineInterfaces),
    opState: normalizeStateSpace(raw?.opState, 'IDLE', 'OP'),
    cmdState: normalizeStateSpace(raw?.cmdState, 'IDLE', 'CMD'),
    stateTransitions: normalizeTransitions(firstDefined(raw?.stateTransitions, raw?.opState?.transitions)).filter(row => !isCommandLifecycleTransition(row)),
    componentsBom: asArray(raw?.componentsBom)
  }
}
