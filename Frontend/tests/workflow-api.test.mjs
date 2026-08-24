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

test('saving a workflow as new posts the current model to the copy endpoint', async () => {
  const previousAdapter = axios.defaults.adapter
  let request
  axios.defaults.adapter = async config => {
    request = config
    return {
      data: { success: true, data: { issues: [], executable: false, published: false, version: 1, status: 'DRAFT' } },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }

  try {
    await workflowApi.saveAsNew({ metadata: { flowModelId: 7, flowModelName: '测试流程' }, nodes: [] })
  } finally {
    axios.defaults.adapter = previousAdapter
  }

  assert.equal(request.method, 'post')
  assert.equal(request.url, '/api/workflow/copy')
})

test('validating a workflow posts the current model to the backend', async () => {
  const previousAdapter = axios.defaults.adapter
  let request
  axios.defaults.adapter = async config => {
    request = config
    return {
      data: { success: true, data: { issues: [], executable: false, published: false } },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }

  try {
    await workflowApi.validate({ metadata: { flowModelName: '测试流程' }, nodes: [] })
  } finally {
    axios.defaults.adapter = previousAdapter
  }

  assert.equal(request.method, 'post')
  assert.equal(request.url, '/api/workflow/validate')
})

test('exporting a workflow loads the schema document from the backend', async () => {
  const previousAdapter = axios.defaults.adapter
  let request
  axios.defaults.adapter = async config => {
    request = config
    return {
      data: { success: true, data: { metadata: { flowModelId: 42, flowModelName: '测试流程' }, nodes: [] } },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }

  try {
    await workflowApi.export(42)
  } finally {
    axios.defaults.adapter = previousAdapter
  }

  assert.equal(request.method, 'get')
  assert.equal(request.url, '/api/workflow/export/42')
})
