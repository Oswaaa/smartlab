import assert from 'node:assert/strict'
import test from 'node:test'
import axios from 'axios'
import { workflowApi, parseSseFrame, readAgentGenerateStream } from '../src/services/workflowApi.js'

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

test('generating a workflow posts the prompt to the agent endpoint', async () => {
  const previousAdapter = axios.defaults.adapter
  let request
  axios.defaults.adapter = async config => {
    request = config
    return {
      data: { success: true, data: { flowModelId: 9, status: 'DRAFT', issues: [], trace: ['list_device_catalog'] } },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }

  try {
    await workflowApi.generate('加热到80度')
  } finally {
    axios.defaults.adapter = previousAdapter
  }

  assert.equal(request.method, 'post')
  assert.equal(request.url, '/api/agent/workflow/generate')
  assert.equal(JSON.parse(request.data).prompt, '加热到80度')
  assert.equal(request.timeout, 600000)
})

test('SSE frames expose live agent logs before the done event', async () => {
  const log = parseSseFrame('event: log\ndata: {"kind":"llm_call","title":"正在请求大模型"}\n')
  assert.equal(log.event, 'log')
  assert.equal(log.data.kind, 'llm_call')

  const chunks = [
    'event: log\ndata: {"kind":"start"}\n\n',
    'event: done\ndata: {"success":true,"data":{"flowModelId":9}}\n\n',
  ]
  let index = 0
  const response = {
    body: {
      getReader() {
        return {
          async read() {
            if (index >= chunks.length) return { done: true, value: undefined }
            return { done: false, value: new TextEncoder().encode(chunks[index++]) }
          },
        }
      },
    },
  }
  const seen = []
  const terminal = await readAgentGenerateStream(response, entry => seen.push(entry.kind))
  assert.deepEqual(seen, ['start'])
  assert.equal(terminal.event, 'done')
  assert.equal(terminal.data.data.flowModelId, 9)
})
