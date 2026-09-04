import axios from 'axios'

export async function listCategories() {
  const res = await axios.get('/api/device/category/list')
  return res.data
}

export async function pageModels(params: Record<string, any> = {}) {
  const res = await axios.get('/api/device/model/page', { params })
  return res.data
}

export async function pageInstances(params: Record<string, any> = {}) {
  const res = await axios.get('/api/device/instance/page', { params })
  return res.data
}

export async function listInstances(lifecycleStatus?: string) {
  const res = await axios.get('/api/device/instance/list', { params: lifecycleStatus ? { lifecycleStatus } : {} })
  return res.data
}

export async function listAdapters() {
  const res = await axios.get('/api/adapter/index/list')
  return res.data
}

export async function listDataTemplates() {
  const res = await axios.get('/api/data/template/list')
  return res.data
}

export async function listDevicePoints(adapterName: string, categoryName?: string) {
  const res = await axios.get(`/api/adapter/index/${encodeURIComponent(adapterName)}/device-points`, {
    params: { categoryName }
  })
  return res.data
}

export async function saveInstance(payload: any) {
  const res = await axios.post('/api/device/instance/save', payload)
  return res.data
}

export async function retireInstance(id: string) {
  const res = await axios.post(`/api/device/instance/retire/${id}`)
  return res.data
}

export async function getSnapshot(id: string) {
  const res = await axios.get(`/api/device/instance/snapshot/${id}`)
  return res.data
}

export async function clearException(id: string, violationStateName: string) {
  const res = await axios.post(`/api/device/instance/${id}/exception/clear`, { violationStateName })
  return res.data
}

export async function controlInstance(id: string, body: Record<string, any>) {
  const res = await axios.post(`/api/device/instance/control/${id}`, body)
  return res.data
}

export async function listVirtualMachines(physicalId: string) {
  const res = await axios.get(`/api/device/instance/${physicalId}/virtuals`)
  return res.data
}

export async function applyVirtualMachine(physicalId: string) {
  const res = await axios.post(`/api/device/instance/${physicalId}/virtuals`)
  return res.data
}

export async function releaseVirtualMachine(leaseId: string | number) {
  const res = await axios.delete(`/api/device/instance/virtuals/${leaseId}`)
  return res.data
}

export async function listComponents(parentInstanceId: string) {
  const res = await axios.get('/api/device/component/list', { params: { parentInstanceId } })
  return res.data
}

export async function submitComponentAction(id: string | number, action: 'configure' | 'replace', payload: any) {
  const res = await axios.post(`/api/device/component/${id}/${action}`, payload)
  return res.data
}

export async function markPendingReplacement(id: string | number, remark: string) {
  const res = await axios.post(`/api/device/component/${id}/mark-pending-replacement`, { remark })
  return res.data
}

export async function getComponentHistory(id: string | number) {
  const res = await axios.get(`/api/device/component/${id}/history`)
  return res.data
}

export async function listDatasets(deviceInstanceId: string) {
  const res = await axios.get('/api/data/index/list', { params: { deviceInstanceId } })
  return res.data
}

export async function createDataset(payload: any) {
  const res = await axios.post('/api/data/index/create-dataset', payload)
  return res.data
}

export async function deleteDataset(id: string | number) {
  const res = await axios.delete('/api/data/index/delete/' + id)
  return res.data
}
