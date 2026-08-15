import assert from 'node:assert/strict'
import test from 'node:test'
import axios from 'axios'
import { workflowApi } from '../src/services/workflowApi.js'

test('deleting a workflow sends DELETE to the workflow deletion endpoint', async () => {
  const previousAdapter = axios.defaults.adapter
  let request
  axios.defaults.adapter = async config => {
    request = config
    return {
      data: { success: true, data: '删除成功' },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }

  try {
    await workflowApi.delete(42)
  } finally {
    axios.defaults.adapter = previousAdapter
  }

  assert.equal(request.method, 'delete')
  assert.equal(request.url, '/api/workflow/delete/42')
})
