import axios from 'axios'

export const workflowApi = {
  list: () => axios.get('/api/workflow/list'),
  detail: id => axios.get(`/api/workflow/detail/${id}`),
  saveDraft: definition => axios.post('/api/workflow/draft', definition),
  publish: definition => axios.post('/api/workflow/publish', definition),
  requirements: id => axios.get(`/api/workflow/${id}/requirements`),
}
