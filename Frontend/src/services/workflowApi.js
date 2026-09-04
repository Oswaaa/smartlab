import axios from 'axios'

function authHeader() {
  try {
    const raw = localStorage.getItem('smartlab_auth')
    const token = raw ? JSON.parse(raw).token : ''
    return token ? { Authorization: 'Bearer ' + token } : {}
  } catch {
    return {}
  }
}

export function parseSseFrame(block) {
  let event = 'message'
  const dataLines = []
  for (const line of String(block || '').split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  const raw = dataLines.join('\n')
  if (!raw) return { event, data: null }
  try {
    return { event, data: JSON.parse(raw) }
  } catch {
    return { event, data: raw }
  }
}

export async function readAgentGenerateStream(response, onLog) {
  if (!response?.body?.getReader) throw Error('生成流不可读')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let terminal = null
  const consume = frame => {
    const parsed = parseSseFrame(frame)
    if (parsed.event === 'log' && parsed.data) onLog?.(parsed.data)
    if (parsed.event === 'done' || parsed.event === 'error') terminal = parsed
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() || ''
      frames.forEach(consume)
      if (terminal) {
        try {
          await reader.cancel()
        } catch {
          // ignore cancel error
        }
        break
      }
    }
    if (buffer.trim() && !terminal) consume(buffer)
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // ignore
    }
  }
  return terminal
}

export const workflowApi = {
  list: () => axios.get('/api/workflow/list'),
  detail: id => axios.get(`/api/workflow/detail/${id}`),
  export: id => axios.get(`/api/workflow/export/${id}`),
  saveDraft: definition => axios.post('/api/workflow/draft', definition),
  saveAsNew: definition => axios.post('/api/workflow/copy', definition),
  validate: definition => axios.post('/api/workflow/validate', definition),
  simulate: payload => axios.post('/api/workflow/simulate', payload, { timeout: 70000 }),
  generate: prompt => axios.post('/api/agent/workflow/generate', { prompt }, { timeout: 600000 }),
  generateStream: async (prompt, onLog) => {
    const response = await fetch('/api/agent/workflow/generate/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...authHeader(),
      },
      body: JSON.stringify({ prompt }),
    })
    if (!response.ok) throw Error((await response.text()) || '生成失败')
    const terminal = await readAgentGenerateStream(response, onLog)
    if (!terminal) throw Error('生成中断：未收到完成事件')
    return terminal
  },
  publish: definition => axios.post('/api/workflow/publish', definition),
  delete: id => axios.delete(`/api/workflow/delete/${id}`),
  requirements: id => axios.get(`/api/workflow/${id}/requirements`),
}
