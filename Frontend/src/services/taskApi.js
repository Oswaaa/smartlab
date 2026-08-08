import axios from 'axios'

export const taskApi = {
  list: (params) => axios.get('/api/task/list', { params }),
  detail: (id) => axios.get(`/api/task/detail/${id}`),
  preflight: (payload) => axios.post('/api/task/preflight', payload),
  create: (payload) => axios.post('/api/task/save', payload),
  start: (id) => axios.post(`/api/task/start/${id}`),
  abort: (id) => axios.post(`/api/task/abort/${id}`),
  executionView: (id) => axios.get(`/api/task/${id}/execution-view`),
  delete: (id) => axios.delete(`/api/task/${id}`),
  stepLogs: (id) => axios.get(`/api/task/${id}/step-logs`),
  executionLogs: (id) => axios.get(`/api/task/${id}/logs`),
  constraints: (id) => axios.get(`/api/task/${id}/constraints`),
  resourceMap: (id) => axios.get(`/api/task/${id}/resource-map`),
  summary: () => axios.get('/api/task/summary'),
}
