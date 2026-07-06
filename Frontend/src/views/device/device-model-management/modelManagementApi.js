import axios from 'axios'

const asArray = value => Array.isArray(value) ? value : []
const okData = res => res?.data?.data

export async function loadModelWorkspace() {
  const [modelRes, categoryRes, instanceRes, componentRes, dataIndexRes] = await Promise.all([
    axios.get('/api/device/model/list'),
    axios.get('/api/device/category/list'),
    axios.get('/api/device/instance/list').catch(() => ({ data: { data: [] } })),
    axios.get('/api/device/component/list').catch(() => ({ data: { data: [] } })),
    axios.get('/api/data/index/list').catch(() => ({ data: { data: [] } }))
  ])

  return {
    models: asArray(okData(modelRes)),
    categories: asArray(okData(categoryRes)),
    instances: asArray(okData(instanceRes)),
    components: asArray(okData(componentRes)),
    dataIndexes: asArray(okData(dataIndexRes))
  }
}

export async function listDeviceCategories() {
  const res = await axios.get('/api/device/category/list')
  return asArray(okData(res))
}

export async function saveDeviceCategory(payload) {
  const res = await axios.post('/api/device/category/save', payload)
  return res.data
}

export async function deleteDeviceCategory(categoryId) {
  const res = await axios.delete('/api/device/category/delete/' + categoryId)
  return res.data
}

export async function saveDeviceModel(payload) {
  const res = await axios.post('/api/device/model/save', payload)
  return res.data
}

export async function deleteDeviceModel(modelId) {
  const res = await axios.delete('/api/device/model/delete/' + modelId)
  return res.data
}

export async function previewDeviceModel(payload) {
  const res = await axios.post('/api/device/model/preview', payload)
  return res.data
}

export async function fetchModelBundle(modelId) {
  if (!modelId) return { capabilityModel: {}, stateMachineModel: {} }
  const res = await axios.get('/api/device/model/' + modelId + '/bundle')
  if (!res.data?.success) throw new Error(res.data?.message || 'Failed to load model bundle')
  return res.data?.data || { capabilityModel: {}, stateMachineModel: {} }
}

export async function listDataPropertyTypes() {
  const res = await axios.get('/api/data/property-type/list')
  return asArray(okData(res))
}

export async function fetchDefaultTemplateAttributes(modelId, attributes = []) {
  if (!modelId) return []
  const tplRes = await axios.get('/api/data/template/list')
  const templates = asArray(okData(tplRes))
  const defaultTpl = templates.find(t => String(t.deviceModelId) === String(modelId) && t.isDefault)
  if (!defaultTpl) return []

  const detailRes = await axios.get('/api/data/template/' + defaultTpl.id + '/details')
  const keys = asArray(okData(detailRes)).map(d => d.deviceAttrKey).filter(Boolean)
  if (keys.length === 0) return []
  return asArray(attributes).filter(attr => keys.includes(attr.name))
}

export async function listRegisteredAdapters() {
  const res = await axios.get('/api/adapter/index/list')
  return res.data?.success ? asArray(res.data.data) : []
}

export async function fetchRegisteredAdapterContract(adapterName, categoryName) {
  const res = await axios.get('/api/adapter/index/' + encodeURIComponent(adapterName) + '/adapter-contract', {
    params: { categoryName }
  })
  return res.data
}
