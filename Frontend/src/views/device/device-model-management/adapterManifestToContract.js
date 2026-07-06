const adapterDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING']

const asArray = value => Array.isArray(value) ? value : []
const stringValue = value => value == null ? '' : String(value).trim()
const makeUiKey = prefix => prefix + '_' + Math.random().toString(36).substr(2, 9)

function normalizeDataType(value, fallback, allowed) {
  const text = String(value || '').toUpperCase()
  return allowed.includes(text) ? text : fallback
}

export function adapterDeviceCategoryOptions(parsed) {
  return asArray(parsed?.deviceCategories).map(category => {
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
}

export function adapterCategoryKey(option) {
  return stringValue(option?.key || option?.categoryName || option?.templateName || option?.name)
}

export function adapterCategoryLabel(option) {
  const name = adapterCategoryKey(option) || '未命名类别'
  return option?.description ? name + ' - ' + option.description : name
}

function normalizeCommandParameter(param) {
  return {
    _key: param._key || makeUiKey('cmd_param'),
    paramName: stringValue(param.paramName || param.name),
    dataType: normalizeDataType(param.dataType || param.type, 'DOUBLE', adapterDataTypes),
    description: stringValue(param.description),
    hidden: param.hidden === true,
    sourceField: stringValue(param.sourceField)
  }
}

function normalizeEventsToFlatList(events) {
  if (Array.isArray(events)) {
    return events.map(event => ({
      _key: event._key || makeUiKey('event'),
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: event.eventType || 'OP'
    }))
  }

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
    commandParameters: asArray(command.parameters || command.commandParameters).map(normalizeCommandParameter)
  }))

  const adapterAttributes = asArray(template.attributes).map(attr => ({
    name: stringValue(attr.name),
    dataType: normalizeDataType(attr.dataType, 'DOUBLE', adapterDataTypes),
    description: stringValue(attr.description)
  }))

  return {
    config: {
      protocol: 'MQTT',
      adapterName: stringValue(parsed.adapterName),
      categoryName: stringValue(category?.categoryName),
      templateName: stringValue(template.templateName)
    },
    commands,
    telemetry: { adapterAttributes, attributesMapping: [] },
    events: normalizeEventsToFlatList(template.events)
  }
}
