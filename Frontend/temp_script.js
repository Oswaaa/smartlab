
import { computed, defineComponent, h, onMounted, reactive, ref, resolveComponent, watch, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Cpu, Delete, Download, EditPen, Lock, Notification, Plus, Refresh, Search, Unlock, Upload, Close, Right, Warning, InfoFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'

const authStore = useAuthStore()

function getSignalTagType(signalName) {
  if (signalName === 'OP_STATE') return 'success'
  if (signalName === 'CMD_STATE') return 'primary'
  if (signalName === 'CMD_START') return 'danger'
  if (signalName === 'CMD_CANCEL') return 'warning'
  return 'info'
}

function formatSignalName(signalName) {
  if (signalName === 'OP_STATE') return '输出功能状态 (OP_STATE)'
  if (signalName === 'CMD_STATE') return '输出指令周期 (CMD_STATE)'
  if (signalName === 'CMD_START') return '下发启动命令 (CMD_START)'
  if (signalName === 'CMD_CANCEL') return '下发取消命令 (CMD_CANCEL)'
  return signalName || ''
}

function formatSignalShortName(signalName) {
  if (signalName === 'OP_STATE') return '输出状态'
  if (signalName === 'CMD_STATE') return '输出指令'
  if (signalName === 'CMD_START') return '启动命令'
  if (signalName === 'CMD_CANCEL') return '取消命令'
  if (signalName === 'CMD_PAUSE') return '暂停命令'
  if (signalName === 'CMD_RESUME') return '恢复命令'
  if (signalName === 'CMD_RESET') return '重置命令'
  return signalName || ''
}

function getSignalsForInterface(interfaceName) {
  if (interfaceName === 'Interface_status_out') return ['OP_STATE', 'CMD_STATE']
  if (interfaceName === 'Interface_adapter_out') return ['CMD_START', 'CMD_CANCEL', 'CMD_PAUSE', 'CMD_RESUME', 'CMD_RESET']
  return []
}

function onActionInterfaceChange(act) {
  if (!act.payload) act.payload = {}
  const sigs = getSignalsForInterface(act.payload.interfaceName)
  if (sigs.length > 0) {
    act.payload.signalName = sigs[0]
  } else {
    act.payload.signalName = ''
  }
}
const attributeDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN']
const adapterDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING']
const operators = ['GT', 'LT', 'GE', 'LE', 'EQ', 'NE', 'BETWEEN', 'IN']
const interfaceTypes = ['WORKFLOW', 'STAT', 'ADAPTER', 'CONTROL', 'CONSTRAINT']
const stateActionNames = ['SEND', 'ASSIGN']
const standardCmdEvents = ['COMMAND_RECEIVED', 'COMMAND_RUNNING', 'COMMAND_COMPLETED', 'COMMAND_FAILED', 'COMMAND_TIMEOUT', 'COMMAND_CANCELLED']
const adapterOutSignals = ['CMD_START', 'CMD_CANCEL', 'CMD_PAUSE', 'CMD_RESUME', 'CMD_RESET']

const DataTypeSelect = defineComponent({
  name: 'DataTypeSelect',
  props: { modelValue: String, options: { type: Array, default: () => [] } },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const ElSelect = resolveComponent('ElSelect')
    const ElOption = resolveComponent('ElOption')
    return () => h(ElSelect, {
      modelValue: props.modelValue,
      size: 'small',
      'onUpdate:modelValue': value => emit('update:modelValue', value)
    }, () => dataTypeOptions(props.options, props.modelValue).map(type => h(ElOption, { key: type, label: type, value: type })))
  }
})

const StateSelect = defineComponent({
  name: 'StateSelect',
  props: { modelValue: String, options: { type: Array, default: () => [] } },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const ElSelect = resolveComponent('ElSelect')
    const ElOption = resolveComponent('ElOption')
    return () => h(ElSelect, {
      modelValue: props.modelValue,
      size: 'small',
      filterable: true,
      allowCreate: true,
      'onUpdate:modelValue': value => { emit('update:modelValue', value); emit('change', value) }
    }, () => props.options.map(option => h(ElOption, { key: option, label: option, value: option })))
  }
})


const models = ref([])
const categories = ref([])
const selectedModelId = ref('')
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const drawerMode = ref('create')

const showAddCategoryDialog = ref(false)
const savingCategory = ref(false)
const newCategoryDraft = ref({ name: '', code: '' })
const newCategoryFormRef = ref(null)
const newCategoryRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入分类代码', trigger: 'blur' }]
}

const draft = reactive(emptyDraft())
const interfaceLocked = ref(true)

const canCreateModel = computed(() => authStore.hasPermission('device_model:create'))
const canEditModel = computed(() => authStore.hasPermission('device_model:edit'))
const canDeleteModel = computed(() => authStore.hasPermission('device_model:delete'))
const selectedModel = computed(() => models.value.find(item => item.modelId === selectedModelId.value) || null)
const drawerTitle = computed(() => drawerMode.value === 'create' ? '新建设备模型' : '编辑设备模型')
const attributeOptions = computed(() => draft.attributes.map((item, index) => ({ key: item._key, label: item.displayName || item.name || '属性' + (index + 1) })).filter(item => item.key))
const adapterAttributeNameOptions = computed(() => draft.adapterContract.telemetry.adapterAttributes.map(item => item.name).filter(Boolean))
const commandNameOptions = computed(() => draft.adapterContract.commands.map(item => item.commandName).filter(Boolean))
const adapterEventOptions = computed(() => opEventNames(draft.adapterContract.events))
const adapterSignalOptions = computed(() => uniqueStrings([...standardCmdEvents, ...adapterEventOptions.value]))
const capabilitySelectOptions = computed(() => draft.capabilities.map((item, index) => ({ key: item._key, label: item.displayName || item.name || '操作' + (index + 1) })).filter(item => item.key))
const generatedInterfaces = computed(() => defaultInterfaces(adapterSignalOptions.value))
const stateMachineInterfaceRows = computed(() => interfaceLocked.value ? generatedInterfaces.value : draft.stateMachineInterfaces)
const commandLifecycleTransitionRows = computed(() => defaultCommandLifecycleTransitions())
const opStateNameOptions = computed(() => draft.opState.states.map(item => item.stateName).filter(Boolean))
const isInitialOpStateInvalid = computed(() => !!draft.opState.initialStateName && !opStateNameOptions.value.includes(draft.opState.initialStateName))
const stateMachineWarningMessages = computed(() => {
  const messages = []
  if (draft.stateTransitions.length > 0) {
    draft.opState.states.forEach(state => {
      const warning = stateUsageWarning(state.stateName)
      if (warning) messages.push('状态 ' + (state.stateName || '未命名') + '：' + warning)
    })
  }
  draft.stateTransitions.forEach((row, index) => {
    const warning = transitionWarning(row)
    if (warning) messages.push('规则 ' + (index + 1) + '：' + warning)
  })
  return messages.slice(0, 5)
})
const capabilityModelJson = computed(() => selectedModel.value ? buildCapabilityModel(selectedModel.value) : {})
const stateMachineModelJson = computed(() => selectedModel.value ? buildStateMachineModel(selectedModel.value) : {})
const draftCapabilityModelJson = ref({})
const draftStateMachineModelJson = ref({})
const generatingPreview = ref(false)

const generatePreview = async () => {
  generatingPreview.value = true
  try {
    const payload = buildSavePayload(false)
    const res = await axios.post('/api/device/model/preview', payload)
    if (res.data.success) {
      draftCapabilityModelJson.value = res.data.data.capabilityModel || {}
      draftStateMachineModelJson.value = res.data.data.stateMachineModel || {}
    } else {
      ElMessage.error(res.data.message || '生成预览失败')
    }
  } catch (error) {
    ElMessage.error('生成预览异常')
    console.error(error)
  } finally {
    generatingPreview.value = false
  }
}

function emptyDraft() {
  return {
    basic: { modelId: '', modelName: '', categoryValue: '' },
    attributes: [],
    capabilities: [],
    functionMappings: [],
    adapterContract: defaultAdapterContract(),
    ports: [],
    intrinsicConstraints: [],
    stateMachineInterfaces: defaultInterfaces([]),
    opState: defaultStateSpace('IDLE'),
    cmdState: defaultCommandLifecycle(),
    stateTransitions: [],
    componentsBom: []
  }
}

function defaultAdapterContract() {
  return { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
}

function defaultStateSpace(initialStateName) {
  return { initialStateName, states: [{ _key: makeUiKey('state'), stateName: initialStateName, onEntry: [{ actionName: 'SEND', payload: { interfaceName: 'Interface_status_out', signalName: 'OP_STATE' } }] }] }
}

function defaultCommandLifecycle() {
  return { initialStateName: 'IDLE', states: commandLifecycleStateNames().map(stateName => ({ _key: makeUiKey('cmd_state'), stateName, onEntry: [{ actionName: 'SEND', payload: { interfaceName: 'Interface_status_out', signalName: 'CMD_STATE' } }] })) }
}

function commandLifecycleStateNames() {
  return ['IDLE', 'SENT', 'RECEIVED', 'RUNNING', 'DONE', 'FAILED', 'TIMEOUT', 'CANCELLED']
}

function defaultInterfaces(adapterSignals = []) {
  const signals = uniqueStrings([...standardCmdEvents, ...asArray(adapterSignals).filter(Boolean)])
  return [
    { _key: 'iface_workflow', name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['EXECUTE_START', 'EXECUTE_PAUSE', 'EXECUTE_RESUME', 'EXECUTE_CANCEL', 'EXECUTE_RESET'] },
    { _key: 'iface_status', name: 'Interface_status_out', direction: 'OUT', interfaceType: 'STAT', allowedSignals: ['OP_STATE', 'CMD_STATE'] },
    { _key: 'iface_control', name: 'Interface_control_in', direction: 'IN', interfaceType: 'CONTROL', allowedSignals: ['MANUAL_EXECUTE', 'MANUAL_CANCEL', 'MANUAL_PAUSE', 'MANUAL_RESUME', 'MANUAL_RESET'] },
    { _key: 'iface_constraint', name: 'Interface_constraint_in', direction: 'IN', interfaceType: 'CONSTRAINT', allowedSignals: ['CONSTRAINT_CANCEL', 'CONSTRAINT_PAUSE', 'CONSTRAINT_RESUME', 'CONSTRAINT_RESET'] },
    { _key: 'iface_adapter_in', name: 'Interface_adapter_in', direction: 'IN', interfaceType: 'ADAPTER', allowedSignals: signals },
    { _key: 'iface_adapter_out', name: 'Interface_adapter_out', direction: 'OUT', interfaceType: 'ADAPTER', allowedSignals: adapterOutSignals }
  ]
}

function adapterInterfaceName() {
  return 'Interface_adapter_in'
}

function adapterOutAction(signalName) {
  return { actionName: 'SEND', payload: { interfaceName: 'Interface_adapter_out', signalName } }
}

function defaultCommandLifecycleTransitions() {
  return [
    { description: '工作流触发指令下发', fromStateName: 'IDLE', toStateName: 'SENT', trigger: { interfaceName: 'Interface_workflow_in', signalName: 'EXECUTE_START' }, actions: [adapterOutAction('CMD_START')] },
    { description: '用户手动触发指令下发', fromStateName: 'IDLE', toStateName: 'SENT', trigger: { interfaceName: 'Interface_control_in', signalName: 'MANUAL_EXECUTE' }, actions: [adapterOutAction('CMD_START')] },
    { description: 'Adapter 已接收', fromStateName: 'SENT', toStateName: 'RECEIVED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_RECEIVED' }, actions: [] },
    { description: 'Adapter 执行中', fromStateName: 'RECEIVED', toStateName: 'RUNNING', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_RUNNING' }, actions: [] },
    { description: '执行完成', fromStateName: 'RUNNING', toStateName: 'DONE', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_COMPLETED' }, actions: [] },
    { description: '执行失败', fromStateName: 'RUNNING', toStateName: 'FAILED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_FAILED' }, actions: [] },
    { description: '执行超时', fromStateName: 'RUNNING', toStateName: 'TIMEOUT', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_TIMEOUT' }, actions: [] },
    { description: 'Adapter 确认取消', fromStateName: 'SENT', toStateName: 'CANCELLED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_CANCELLED' }, actions: [] },
    { description: '工作流取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_workflow_in', signalName: 'EXECUTE_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] },
    { description: '用户手动取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_control_in', signalName: 'MANUAL_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] },
    { description: '约束引擎取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_constraint_in', signalName: 'CONSTRAINT_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] }
  ]
}

function isCommandLifecycleTransition(row) {
  return defaultCommandLifecycleTransitions().some(item => item.fromStateName === row?.fromStateName && item.toStateName === row?.toStateName && item.trigger.interfaceName === row?.trigger?.interfaceName && item.trigger.signalName === row?.trigger?.signalName)
}

async function loadData() {
  loading.value = true
  try {
    const [modelRes, categoryRes] = await Promise.all([
      axios.get('/api/device/model/page', { params: { pageNo: pageNo.value, pageSize: pageSize.value, keyword: keyword.value.trim() || undefined } }),
      axios.get('/api/device/category/list')
    ])
    categories.value = asArray(categoryRes.data?.data)
    const pageData = modelRes.data?.data || {}
    models.value = asArray(pageData.records).map(normalizeModel)
    total.value = Number(pageData.total || 0)
    if (!models.value.some(item => item.modelId === selectedModelId.value)) selectedModelId.value = models.value[0]?.modelId || ''
  } catch (err) {
    console.error('loadData error:', err); ElMessage.error(err.response?.data?.message || '加载设备模型失败')
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  const res = await axios.get('/api/device/category/list')
  categories.value = asArray(res.data?.data)
}

function normalizeModel(raw) {
  const modelId = String(raw?.modelId || raw?.id || '')
  const categoryId = raw?.categoryId == null ? '' : String(raw.categoryId)
  const attributes = normalizeAttributes(firstDefined(raw?.attributes, raw?.capabilitySpec?.attributes))
  const adapterContract = normalizeAdapterContract(firstDefined(raw?.adapterContract, raw?.capabilitySpec?.adapterContract), attributes)
  const capabilities = normalizeCapabilities(firstDefined(raw?.capabilities, raw?.capabilitySpec?.capabilities, raw?.capabilitySpec?.functions))
  return {
    ...raw,
    modelId,
    categoryId,
    categoryName: raw?.categoryName || categoryNameById(categoryId),
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

function openCreateDrawer() {
  replaceDraft(emptyDraft())
  drawerMode.value = 'create'
  drawerVisible.value = true
}

function openEditDrawer(model) {
  replaceDraft(fromModelToDraft(model))
  drawerMode.value = 'edit'
  drawerVisible.value = true
}

async function saveDraft() {
  if (!draft.basic.modelName?.trim() || !draft.basic.categoryValue) {
    ElMessage.error('请填写完整模型基础信息')
    return
  }
  saving.value = true
  try {
    const payload = buildSavePayload(true)
    const res = await axios.post('/api/device/model/save', payload)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '保存失败')
      return
    }
    selectedModelId.value = String(res.data?.data?.modelId || payload.modelId || '')
    drawerVisible.value = false
    ElMessage.success('保存成功')
    await loadData()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function saveNewCategory() {
  if (!newCategoryFormRef.value) return
  await newCategoryFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingCategory.value = true
    try {
      const res = await axios.post('/api/device/category/save', newCategoryDraft.value)
      if (res.data.success) {
        ElMessage.success('新增分类成功')
        showAddCategoryDialog.value = false
        await loadCategories()
        const newCat = categories.value.find(c => c.categoryName === newCategoryDraft.value.name)
        if (newCat) {
          draft.basic.categoryValue = String(newCat.id || newCat.categoryId || '')
        }
        newCategoryDraft.value = { name: '', code: '' }
      } else {
        ElMessage.error(res.data.message || '新增分类失败')
      }
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '新增分类失败')
    } finally {
      savingCategory.value = false
    }
  })
}

async function deleteModel(id) {
  try {
    const res = await axios.delete('/api/device/model/delete/' + id)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '删除失败')
      return
    }
    ElMessage.success('删除成功')
    selectedModelId.value = ''
    await loadData()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}


function cleanInterfaces(rows) {
  return asArray(rows).map(row => ({
    name: stringValue(row.name),
    direction: row.direction || 'IN',
    interfaceType: row.interfaceType || 'ADAPTER',
    allowedSignals: asArray(row.allowedSignals).filter(Boolean)
  }))
}

function cleanStateSpace(space, fallback, type) {
  const norm = normalizeStateSpace(space, fallback, type)
  return {
    initialStateName: norm.initialStateName,
    states: norm.states.map(s => ({
      stateName: s.stateName,
      onEntry: s.onEntry.map(a => ({ actionName: a.actionName, payload: normalizePayload(a.payload || a.parameters) }))
    }))
  }
}

function cleanTransitions(rows) {
  return asArray(rows).map(r => ({
    description: stringValue(r.description),
    fromStateName: stringValue(r.fromStateName),
    toStateName: stringValue(r.toStateName),
    trigger: { interfaceName: stringValue(r.trigger?.interfaceName), signalName: stringValue(r.trigger?.signalName) },
    actions: asArray(r.actions).map(a => ({ actionName: stringValue(a.actionName), payload: normalizePayload(a.payload || a.parameters) }))
  }))
}

function cleanAdapterContract(contract, attrNameByKey) {
  const norm = normalizeAdapterContract(contract, [])
  return {
    config: norm.config,
    commands: norm.commands.map(cmd => ({
      commandName: cmd.commandName,
      description: cmd.description,
      commandParameters: cmd.commandParameters.map(p => {
        const p_out = {
          paramName: p.paramName,
          dataType: p.dataType,
          description: p.description
        }
        if (p.hidden) {
          p_out.hidden = true
          p_out.sourceField = p.sourceField
        }
        return p_out
      })
    })),
    telemetry: {
      adapterAttributes: norm.telemetry.adapterAttributes.map(a => ({
        name: a.name, dataType: a.dataType, description: a.description
      })),
      attributesMapping: norm.telemetry.attributesMapping.map(m => ({
        adapterAttrName: m.adapterAttrName,
        modelAttributeName: attrNameByKey[m.modelAttributeKey] || m.modelAttributeName
      }))
    },
    events: {
      cmdEvents: norm.events.filter(e => e.eventType === 'CMD').map(e => ({ eventName: e.eventName, description: e.description })),
      opEvents: norm.events.filter(e => e.eventType === 'OP').map(e => ({ eventName: e.eventName, description: e.description }))
    }
  }
}function buildSavePayload(includeBlankBasic = true) {
  const attributeResult = materializeAttributes(draft.attributes)
  const attributes = attributeResult.rows
  const attrNameByKey = attributeResult.nameByKey
  const functionMappings = materializeFunctionMappings(draft.functionMappings)
  const capabilities = materializeCapabilities(draft.capabilities, functionMappings)
  const payload = {
    modelId: numericOrNull(draft.basic.modelId),
    modelName: includeBlankBasic ? draft.basic.modelName.trim() : draft.basic.modelName?.trim() || '',
    attributes,
    capabilities,
    adapterContract: cleanAdapterContract(draft.adapterContract, attrNameByKey),
    ports: materializePorts(draft.ports, attrNameByKey),
    intrinsicConstraints: materializeIntrinsicConstraints(draft.intrinsicConstraints, attrNameByKey),
    stateMachineInterfaces: cleanInterfaces(stateMachineInterfaceRows.value),
    opState: cleanStateSpace(draft.opState, 'IDLE', 'OP'),
    cmdState: cleanStateSpace(draft.cmdState, 'IDLE', 'CMD'),
    stateTransitions: cleanTransitions(draft.stateTransitions),
    componentsBom: asArray(draft.componentsBom)
  }
  if (isNumeric(draft.basic.categoryValue)) payload.categoryId = Number(draft.basic.categoryValue)
  else if (draft.basic.categoryValue) payload.categoryName = String(draft.basic.categoryValue).trim()
  return payload
}

function fromModelToDraft(model) {
  return {
    basic: { modelId: model.modelId, modelName: model.modelName || '', categoryValue: model.categoryId || '' },
    attributes: deepClone(model.attributes),
    capabilities: deepClone(model.capabilities),
    adapterContract: deepClone(model.adapterContract),
    ports: deepClone(model.ports),
    intrinsicConstraints: deepClone(model.intrinsicConstraints),
    stateMachineInterfaces: normalizeInterfaces(model.stateMachineInterfaces),
    functionMappings: extractFunctionMappings(model.capabilities, model.capabilities),
    opState: deepClone(model.opState),
    cmdState: deepClone(model.cmdState),
    stateTransitions: deepClone(model.stateTransitions),
    componentsBom: deepClone(model.componentsBom)
  }
}

function replaceDraft(next) {
  Object.assign(draft.basic, next.basic)
  const normalizedCapabilities = normalizeCapabilities(next.capabilities)
  draft.attributes.splice(0, draft.attributes.length, ...normalizeAttributes(next.attributes))
  draft.capabilities.splice(0, draft.capabilities.length, ...normalizedCapabilities)
  draft.functionMappings.splice(0, draft.functionMappings.length, ...normalizeFunctionMappings(next.functionMappings))
  draft.ports.splice(0, draft.ports.length, ...normalizePorts(next.ports, draft.attributes))
  draft.intrinsicConstraints.splice(0, draft.intrinsicConstraints.length, ...normalizeIntrinsicConstraints(next.intrinsicConstraints, draft.attributes))
  draft.adapterContract = normalizeAdapterContract(next.adapterContract, draft.attributes)
  draft.stateMachineInterfaces.splice(0, draft.stateMachineInterfaces.length, ...normalizeInterfaces(next.stateMachineInterfaces))
  draft.stateTransitions.splice(0, draft.stateTransitions.length, ...normalizeTransitions(next.stateTransitions))
  draft.componentsBom.splice(0, draft.componentsBom.length, ...asArray(next.componentsBom))
  draft.opState = normalizeStateSpace(next.opState, 'IDLE', 'OP')
  draft.cmdState = normalizeStateSpace(next.cmdState, 'IDLE', 'CMD')
}

function addAttribute() { draft.attributes.push({ _key: makeUiKey('attr'), name: '', displayName: '', valueKind: 'CONTINUOUS', dataType: 'DOUBLE', unit: '' }) }

function removeAttribute(index) {
  const removed = draft.attributes[index]
  removeRow(draft.attributes, index)
  if (!removed?._key) return
  draft.ports.forEach(port => { if (port.bindingAttrKey === removed._key) port.bindingAttrKey = '' })
}

function addPort() { draft.ports.push({ _key: makeUiKey('port'), portName: '', displayName: '', direction: 'OUT', bindingAttrKey: '', bindingAttrName: '' }) }
function addCapability() { draft.capabilities.push({ _key: makeUiKey('cap'), name: '', displayName: '', adapterCommandName: '', parameters: [], parameterMapping: [] }) }
function removeCapability(capability) {
  removeObjectRow(draft.capabilities, capability)
  draft.functionMappings = draft.functionMappings.filter(mapping => mapping.capabilityKey !== capability._key)
}
function addCapabilityParameter(capability) { ensureArrayField(capability, 'parameters').push({ _key: makeUiKey('param'), name: '', displayName: '', dataType: 'DOUBLE' }) }

function removeCapabilityParameter(capability, index) {
  const removed = capability.parameters[index]
  removeRow(capability.parameters, index)
  if (removed?._key) capability.parameterMapping = asArray(capability.parameterMapping).filter(item => item.capabilityParamKey !== removed._key)
}

function addAdapterCommand() {
  draft.adapterContract.commands.push({ _key: makeUiKey('cmd'), commandName: '', commandParameters: [] })
}
function removeAdapterCommand(command) {
  removeObjectRow(draft.adapterContract.commands, command)
}
function addCommandParameter(command) {
  ensureArrayField(command, 'commandParameters').push({ _key: makeUiKey('cmd_param'), paramName: '', dataType: 'DOUBLE' })
}
function addAdapterAttribute() { draft.adapterContract.telemetry.adapterAttributes.push({ _key: makeUiKey('adapter_attr'), name: '', dataType: 'DOUBLE', description: '' }) }

function removeAdapterAttribute(index) {
  removeRow(draft.adapterContract.telemetry.adapterAttributes, index)
}

function addAdapterEvent() { draft.adapterContract.events.push({ _key: makeUiKey('event'), eventName: '', description: '' }) }
function removeAdapterEvent(index) { removeRow(draft.adapterContract.events, index) }

function addAttributeMapping() {
  draft.adapterContract.telemetry.attributesMapping.push({ _key: makeUiKey('attr_map'), adapterAttrName: '', modelAttributeKey: '', modelAttributeName: '' })
}

function handleAttributeMappingModelChange(row) {
  if (!row.modelAttributeKey) return
  if (draft.adapterContract.telemetry.attributesMapping.find(item => item !== row && item.modelAttributeKey === row.modelAttributeKey)) row.modelAttributeKey = ''
}

function handleAdapterAttributeMappingChange(row) {
  if (!row.adapterAttrName) return
  if (draft.adapterContract.telemetry.attributesMapping.find(item => item !== row && item.adapterAttrName === row.adapterAttrName)) row.adapterAttrName = ''
}

function isAttributeOptionUsed(key, row) { return !!key && draft.adapterContract.telemetry.attributesMapping.some(item => item !== row && item.modelAttributeKey === key) }
function isAdapterAttributeUsed(name, row) { return !!name && draft.adapterContract.telemetry.attributesMapping.some(item => item !== row && item.adapterAttrName === name) }

function addFunctionMapping() {
  draft.functionMappings.push({ _key: makeUiKey('function_map'), capabilityKey: '', adapterCommandName: '', parameterMapping: [] })
}

function addParameterMapping(mappingOwner) {
  ensureArrayField(mappingOwner, 'parameterMapping').push({ _key: makeUiKey('param_map'), commandParamName: '', capabilityParamKey: '', capabilityParamName: '', isFixedValue: false, fixedValue: '' })
}

function handleFunctionMappingCapabilityChange(row) {
  asArray(row.parameterMapping).forEach(mapping => mapping.capabilityParamKey = '')
}

function handleFunctionMappingCommandChange(row) {
  if (isAdapterCommandMapped(row.adapterCommandName, row)) row.adapterCommandName = ''
  asArray(row.parameterMapping).forEach(mapping => mapping.commandParamName = '')
}

function handleParameterCommandChange(row, mapping) {
  if (isCommandParamMapped(row, mapping.commandParamName, mapping)) {
    mapping.commandParamName = ''
    return
  }
}

function handleCapabilityParameterChange(row, mapping) {
  const capability = draft.capabilities.find(c => c._key === row.capabilityKey)
  if (capability) {
    const param = (capability.parameters || []).find(p => p._key === mapping.capabilityParamKey)
    mapping.capabilityParamName = param ? param.name : ''
  }
}

function isCapabilityMapped(key, row) {
  return !!key && draft.functionMappings.some(mapping => mapping !== row && mapping.capabilityKey === key)
}

function isAdapterCommandMapped(name, row) {
  return !!name && draft.functionMappings.some(mapping => mapping !== row && mapping.adapterCommandName === name)
}

function isCommandParamMapped(row, paramName, current) {
  return !!paramName && asArray(row.parameterMapping).some(mapping => mapping !== current && mapping.commandParamName === paramName)
}

function isCapabilityParamMapped(row, paramKey, current) {
  return !!paramKey && asArray(row.parameterMapping).some(mapping => mapping !== current && !mapping.isFixedValue && mapping.capabilityParamKey === paramKey)
}

function visibleCommandParameters(command) {
  return asArray(command?.commandParameters).filter(param => param.hidden !== true)
}

function addIntrinsicConstraint() {
  draft.intrinsicConstraints.push({ _key: makeUiKey('constraint'), objectAttributeKey: '', objectAttributeName: '', operator: 'GT', boundaryValue: '', violationStateName: '' })
}

function removeRow(rows, index) { rows.splice(index, 1) }
function removeObjectRow(rows, row) { const index = rows.indexOf(row); if (index >= 0) rows.splice(index, 1) }

function buildCapabilityModel(model) {
  return { metadata: { modelId: numericOrNull(model.modelId), modelName: model.modelName, deviceCategoryId: numericOrNull(model.categoryId) }, attributes: cleanAttributesForExport(model.attributes), capabilities: cleanCapabilitiesForExport(model.capabilities), adapterContract: cleanAdapterContract(model.adapterContract), ports: cleanPortsForExport(model.ports), intrinsicConstraints: cleanIntrinsicConstraintsForExport(model.intrinsicConstraints) }
}

function buildStateMachineModel(model) {
  return { deviceModelId: numericOrNull(model.modelId), interfaces: cleanInterfaces(defaultInterfaces(opEventNames(model.adapterContract?.events))), opStateSpace: cleanStateSpace(model.opState, 'IDLE', 'OP'), cmdLifecycleSpace: cleanStateSpace(model.cmdState, 'IDLE', 'CMD'), transitions: allStateTransitions(model.stateTransitions) }
}

function opEventNames(events) {
  return asArray(events).filter(event => event.eventType !== 'CMD' && !standardCmdEvents.includes(event.eventName)).map(event => event.eventName).filter(Boolean)
}

function allStateTransitions(rows) {
  return [...defaultCommandLifecycleTransitions(), ...cleanTransitions(rows)]
}

function signalOptionsForInterface(interfaceName) {
  const iface = stateMachineInterfaceRows.value.find(item => item.name === interfaceName)
  return asArray(iface?.allowedSignals).filter(Boolean)
}

function uniqueStrings(values) {
  return [...new Set(asArray(values).map(stringValue).filter(Boolean))]
}

function downloadModelBundle() {
  if (!selectedModel.value) return
  downloadJson('device-model-' + selectedModel.value.modelId + '.json', { capabilityModel: capabilityModelJson.value, stateMachineModel: stateMachineModelJson.value })
}

function downloadJson(filename, data) {
  const blob = new Blob([formatJson(data)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

function importAdapterFile(file) {
  const rawFile = file?.raw
  if (!rawFile) return false
  const reader = new FileReader()
  reader.onload = () => { adapterConfigText.value = String(reader.result || '') }
  reader.readAsText(rawFile, 'utf-8')
  return false
}

function applyAdapterConfigText() {
  try {
    const parsed = JSON.parse(adapterConfigText.value)
    draft.adapterContract = normalizeAdapterContract(extractAdapterContract(parsed), draft.attributes)
    ElMessage.success('已解析到 Adapter 契约')
  } catch {
    ElMessage.error('配置文本不是有效的 JSON')
  }
}

function extractAdapterContract(source) {
  const root = source?.adapterContract || source || {}
  return {
    config: { protocol: root.protocol || 'MQTT', adapterName: root.adapterName || '' },
    commands: asArray(root.commands).map(command => ({ commandName: stringValue(command.commandName), commandParameters: asArray(command.commandParameters).map(normalizeCommandParameter) })),
    telemetry: { adapterAttributes: asArray(root.adapterAttributes).map(attr => ({ name: stringValue(attr.name), dataType: normalizeDataType(attr.dataType, 'DOUBLE', adapterDataTypes) })), attributesMapping: asArray(root.attributesMapping) },
    events: normalizeEventsToFlatList(root.events)
  }
}

function normalizeCommandParameter(param) {
  return { paramName: stringValue(param.paramName), dataType: normalizeDataType(param.dataType, 'DOUBLE', adapterDataTypes), hidden: !!param.hidden }
}

function normalizeEventsToFlatList(events) {
  return asArray(events).map(event => ({ eventName: stringValue(event.eventName), description: stringValue(event.description), eventType: event.eventType }))
}

function capabilityMappingRows(capabilities) {
  return asArray(capabilities).flatMap(capability => asArray(capability.parameterMapping).map(mapping => ({ capabilityDisplayName: capability.displayName || capability.name || '-', adapterCommandName: capability.adapterCommandName || '-', commandParamName: mapping.commandParamName || '-', sourceLabel: mapping.isFixedValue ? '固定值：' + (mapping.fixedValue ?? '') : mapping.capabilityParamName || '-' })))
}

function summaryText(model) {
  const parts = []
  if (model.attributes.length) parts.push('属性')
  if (model.capabilities.length) parts.push('操作')
  return parts.length ? parts.join('、') : '尚未补充模型内容'
}

function onKeywordInput() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { pageNo.value = 1; loadData() }, 260)
}

function categoryNameById(id) { return categories.value.find(item => String(item.id) === String(id))?.categoryName || '' }
function displayAttributeName(name, attributes) { if (!name) return '-'; const attr = asArray(attributes).find(item => item.name === name); return attr?.displayName || name }
function describeAction(action) {
  if (!action?.actionName) return ''
  if (action.actionName === 'SEND') {
    const interfaceName = action.payload?.interfaceName || '接口'
    const signalName = action.payload?.signalName || '信号'
    return '输出 ' + interfaceName + ' / ' + signalName
  }
  return action.actionName
}

function valueKindLabel(value) { return value === 'DISCRETE' ? '离散值' : '连续值' }
function directionLabel(value) { return value === 'IN' ? '输入' : '输出' }
function formatTime(value) { return value ? String(value).replace('T', ' ') : '-' }
function formatJson(value) { return JSON.stringify(value || {}, null, 2) }
function dataTypeOptions(base, current) { return current && !base.includes(current) ? [...base, current] : base }
function asArray(value) { return Array.isArray(value) ? value : [] }
function firstDefined(...values) { return values.find(value => value !== undefined && value !== null) }
function deepClone(value) { return JSON.parse(JSON.stringify(value ?? [])) }
function ensureArrayField(target, key) { if (!Array.isArray(target[key])) target[key] = []; return target[key] }
function isNumeric(value) { return value !== '' && value != null && !Number.isNaN(Number(value)) }
function numericOrNull(value) { return isNumeric(value) ? Number(value) : null }
function stringValue(value) { return value == null ? '' : String(value).trim() }
function normalizeDataType(value, fallback, allowed) { const text = String(value || '').toUpperCase(); return allowed.includes(text) ? text : fallback }
function makeUiKey(prefix) { return prefix + '_' + Math.random().toString(36).substr(2, 9) }
function findKeyByName(rows, name) { return asArray(rows).find(item => item.name === name)?._key || '' }

function materializeAttributes(rows) {
  const nameByKey = new Map()
  const materializedRows = asArray(rows).map(item => {
    const name = item.name || 'attr_' + item._key
    nameByKey.set(item._key, name)
    return { name, displayName: item.displayName, valueKind: item.valueKind, dataType: item.dataType, unit: item.unit }
  })
  return { rows: materializedRows, nameByKey }
}

function materializePorts(rows, attrNameByKey) {
  return asArray(rows).map(item => ({ portName: item.portName, direction: item.direction, bindingAttrName: attrNameByKey.get(item.bindingAttrKey) || item.bindingAttrName }))
}

function materializeCapabilities(rows, functionMappings) {
  return asArray(rows).map(item => {
    const mapping = functionMappings.find(fm => fm.capabilityKey === item._key)
    return {
      name: item.name || 'cap_' + item._key,
      adapterCommandName: mapping?.adapterCommandName,
      displayName: item.displayName,
      parameters: item.parameters,
      parameterMapping: mapping?.parameterMapping?.map(pm => ({ commandParamName: pm.commandParamName, isFixedValue: pm.isFixedValue, fixedValue: pm.fixedValue, capabilityParamName: pm.capabilityParamName })) || []
    }
  })
}

function materializeFunctionMappings(rows) {
  return asArray(rows).map(row => ({ capabilityKey: row.capabilityKey, adapterCommandName: row.adapterCommandName, parameterMapping: row.parameterMapping }))
}

function materializeIntrinsicConstraints(rows, attrNameByKey) {
  return asArray(rows).map(row => ({
    objectAttributeName: attrNameByKey[row.objectAttributeKey] || row.objectAttributeName,
    operator: row.operator,
    boundaryValue: row.boundaryValue,
    violationStateName: row.violationStateName
  }))
}

function normalizeAttributes(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('attr'), name: stringValue(item.name), displayName: stringValue(item.displayName || item.name), valueKind: item.valueKind === 'DISCRETE' ? 'DISCRETE' : 'CONTINUOUS', dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes), unit: stringValue(item.unit) })) }

// Normalize capabilities
function normalizeCapabilities(value) {
  return asArray(value).map(item => {
    const params = asArray(item.parameters).map(param => ({ _key: param._key || makeUiKey('param'), name: stringValue(param.name), displayName: stringValue(param.displayName || param.name), dataType: normalizeDataType(param.dataType, 'DOUBLE', attributeDataTypes) }))
    return { _key: item._key || makeUiKey('cap'), name: stringValue(item.name), displayName: stringValue(item.displayName || item.name), adapterCommandName: stringValue(item.adapterCommandName), parameters: params, parameterMapping: [] }
  })
}

function extractFunctionMappings(capabilities, normalizedCapabilities = null) {
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
        capabilityParamKey: findKeyByName(capability.parameters, mapping.capabilityParamName),
        isFixedValue: !!mapping.isFixedValue,
        fixedValue: mapping.fixedValue ?? ''
      }))
    }
  })
}

function normalizeFunctionMappings(value) {
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

function normalizeAdapterContract(value, attributes = []) {
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
    eventsList = asArray(contract.events || contract.adapterEvents).map(event => ({
      _key: event._key || makeUiKey('event'),
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: event.eventType || (event.eventName?.startsWith('COMMAND_') ? 'CMD' : 'OP')
    }))
  }

  return {
    config: { protocol: contract.config?.protocol || contract.protocol || 'MQTT', adapterName: stringValue(contract.config?.adapterName || contract.adapterName), templateName: stringValue(contract.config?.templateName || contract.templateName) },
    commands: asArray(contract.commands).map(command => ({
      _key: command._key || makeUiKey('cmd'),
      commandName: stringValue(command.commandName || command.name),
      description: stringValue(command.description),
      commandParameters: asArray(command.commandParameters || command.parameters).map(param => {
        const row = { _key: param._key || makeUiKey('cmd_param'), paramName: stringValue(param.paramName || param.name), dataType: normalizeDataType(param.dataType || param.type, 'DOUBLE', adapterDataTypes), description: stringValue(param.description) }
        if (param.hidden === true) {
          row.hidden = true
          row.sourceField = stringValue(param.sourceField)
        }
        return row
      })
    })),
    telemetry: { adapterAttributes: asArray(telemetry.adapterAttributes).map(attr => ({ _key: attr._key || makeUiKey('adapter_attr'), name: stringValue(attr.name), dataType: normalizeDataType(attr.dataType || attr.type, 'DOUBLE', adapterDataTypes), description: stringValue(attr.description) })), attributesMapping: asArray(telemetry.attributesMapping).map(mapping => ({ _key: mapping._key || makeUiKey('attr_map'), adapterAttrName: stringValue(mapping.adapterAttrName), modelAttributeName: stringValue(mapping.modelAttributeName), modelAttributeKey: mapping.modelAttributeKey || findKeyByName(attributes, mapping.modelAttributeName) })) },
    events: eventsList
  }
}

function normalizePorts(value, attributes = []) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('port'), portName: stringValue(item.portName), displayName: stringValue(item.displayName || item.portName), direction: item.direction || 'OUT', bindingAttrName: stringValue(item.bindingAttrName), bindingAttrKey: item.bindingAttrKey || findKeyByName(attributes, item.bindingAttrName) })) }
function normalizeOperator(value) { const text = String(value || '').toUpperCase(); return operators.includes(text) ? text : 'GT' }
function normalizeIntrinsicConstraints(value, attributes = []) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('constraint'), objectAttributeName: stringValue(item.objectAttributeName || item.targetAttr), objectAttributeKey: item.objectAttributeKey || findKeyByName(attributes, item.objectAttributeName || item.targetAttr), operator: normalizeOperator(item.operator), boundaryValue: firstDefined(item.boundaryValue, item.threshold, ''), violationStateName: stringValue(item.violationStateName || item.violationStateRef) })) }
function normalizeInterfaces(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('iface'), name: stringValue(item.name), direction: item.direction || 'IN', interfaceType: item.interfaceType || 'ADAPTER', allowedSignals: asArray(item.allowedSignals).map(stringValue).filter(Boolean) })) }

function normalizeStateSpace(value, fallback, spaceType) {
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

function normalizeTransitions(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('transition'), description: stringValue(item.description), fromStateName: stringValue(item.fromStateName), toStateName: stringValue(item.toStateName), trigger: { interfaceName: stringValue(item.trigger?.interfaceName || adapterInterfaceName()), signalName: stringValue(item.trigger?.signalName) }, actions: asArray(item.actions).map(action => ({ _key: action._key || makeUiKey('action'), actionName: stringValue(action.actionName), payload: normalizePayload(action.payload || action.parameters) })) })) }
function normalizePayload(value) { return value && typeof value === 'object' && !Array.isArray(value) ? value : {} }

onMounted(loadData)
