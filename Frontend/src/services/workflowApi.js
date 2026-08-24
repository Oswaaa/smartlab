import axios from 'axios'

export const workflowApi = {
  list: () => axios.get('/api/workflow/list'),
  detail: id => axios.get(`/api/workflow/detail/${id}`),
  export: id => axios.get(`/api/workflow/export/${id}`),
  saveDraft: definition => axios.post('/api/workflow/draft', definition),
  saveAsNew: definition => axios.post('/api/workflow/copy', definition),
  validate: definition => axios.post('/api/workflow/validate', definition),
  publish: definition => axios.post('/api/workflow/publish', definition),
  delete: id => axios.delete(`/api/workflow/delete/${id}`),
  requirements: id => axios.get(`/api/workflow/${id}/requirements`),
}
