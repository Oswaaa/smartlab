import axios from 'axios'

export const taskApi = {
  list: (params) => axios.get('/api/task/page', { params }),
  detail: (id) => axios.get(`/api/task/${id}`),
  preflight: (payload) => axios.post('/api/task/preflight', payload),
  create: (payload) => axios.post('/api/task/save', payload),
  start: (id) => axios.post(`/api/task/start/${id}`),
  pause: (id) => axios.post(`/api/task/pause/${id}`),
  resume: (id) => axios.post(`/api/task/resume/${id}`),
  terminate: (id) => axios.post(`/api/task/terminate/${id}`),
  abort: (id) => axios.post(`/api/task/terminate/${id}`),
  delete: (id) => axios.delete(`/api/task/delete/${id}`),
  snapshots: (id) => axios.get(`/api/task/snapshots/${id}`),
  executionLogs: (id, params) => axios.get(`/api/task/logs/${id}`, { params }),
  executionView: (id) => axios.get(`/api/task/execution-view/${id}`),
  summary: () => axios.get('/api/task/summary'),
  monitorSummary: () => axios.get('/api/task/monitor/summary'),
}
