<template>
  <section class="agent-workspace">
    <div class="agent-main">
      <div class="pane-head">
        <span class="pane-title">生成流程</span>
        <span :class="['result-pill', resultState]">{{ resultLabel }}</span>
      </div>

      <div ref="messageList" class="agent-chat-messages">
        <article
          v-for="message in messages"
          :key="message.id"
          :class="['chat-row', message.role]"
        >
          <div class="chat-bubble">
            <p class="chat-text">{{ message.text }}</p>
            <button
              v-if="message.flowModelId"
              class="btn-aliyun-cta"
              type="button"
              @click="openDesigner(message.flowModelId)"
            >
              打开流程设计
            </button>
          </div>
        </article>
      </div>

      <form class="agent-chat-composer" @submit.prevent="sendPrompt">
        <textarea
          v-model="draft"
          :disabled="generating"
          rows="3"
          placeholder="例如：用加热套把样品加热到 80℃，到达温度后结束"
          @keydown.enter.exact.prevent="sendPrompt"
        />
        <button class="btn-primary-blue" type="submit" :disabled="generating || !draft.trim()">
          {{ generating ? '生成中' : '发送' }}
        </button>
      </form>
    </div>

    <aside class="agent-log">
      <div class="pane-head">
        <span class="pane-title">交互日志</span>
        <span class="log-count">{{ logs.length }} 条</span>
        <button class="btn-link" type="button" :disabled="!logs.length" @click="copyLogs">复制</button>
      </div>
      <div ref="logList" class="log-scroll">
        <p v-if="!logs.length" class="log-empty">发送后日志会即时出现。若停在「正在请求大模型」，说明还在等 DeepSeek 返回这一轮。</p>
        <article v-for="(entry, index) in logs" :key="index" class="log-entry" :data-kind="entry.kind">
          <div class="log-meta">
            <span class="log-kind">{{ kindLabel(entry.kind) }}</span>
            <span v-if="entry.round" class="log-round">第 {{ entry.round }} 轮</span>
            <span class="log-title">{{ entry.title }}</span>
          </div>
          <p v-if="entry.detail" class="log-detail">{{ entry.detail }}</p>
          <pre v-if="entry.payload" class="log-payload">{{ formatPayload(entry.payload) }}</pre>
        </article>
      </div>
    </aside>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { workflowApi } from '../../../services/workflowApi.js'
import { workflowModelId, workflowModelName } from '../../../utils/workflowAuthoring.js'

type ChatMessage = {
  id: number
  role: 'assistant' | 'user' | 'status'
  text: string
  flowModelId?: number
}

type InteractionLog = {
  round?: number
  kind?: string
  title?: string
  detail?: string
  payload?: string
}

const KIND_LABELS: Record<string, string> = {
  start: '开始',
  llm_call: '请求模型',
  llm_reply: '模型回复',
  tool_call: '调用工具',
  tool_result: '工具结果',
  gate: '拦截',
  nudge: '系统催促',
  error: '错误',
  done: '完成',
}

const router = useRouter()
const draft = ref('')
const generating = ref(false)
const resultState = ref<'idle' | 'running' | 'success' | 'failure'>('idle')
const logs = ref<InteractionLog[]>([])
const messageList = ref<HTMLElement | null>(null)
const logList = ref<HTMLElement | null>(null)
let nextId = 1

const messages = ref<ChatMessage[]>([{
  id: nextId++,
  role: 'assistant',
  text: '用自然语言描述实验流程。系统会查询设备目录、生成模型文件并保存为草稿。生成成功或失败都会把与大模型的交互写进右侧日志，确认后再打开流程设计。不会自动发布。',
}])

const resultLabel = computed(() => {
  if (resultState.value === 'running') return '生成中'
  if (resultState.value === 'success') return '生成成功'
  if (resultState.value === 'failure') return '生成失败'
  return '待发送'
})

function generatedDraftId(payload: any) {
  return workflowModelId(payload) ?? workflowModelId(payload?.definition || {})
}

function generatedDraftName(payload: any) {
  return workflowModelName(payload?.definition || payload) || '未命名流程'
}

function kindLabel(kind?: string) {
  return KIND_LABELS[kind || ''] || kind || '日志'
}

function formatPayload(payload?: string) {
  if (!payload) return ''
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

function applyLogs(payload: any) {
  logs.value = Array.isArray(payload?.logs) ? payload.logs : []
}

function payloadFromError(error: any) {
  return error?.response?.data?.data || null
}

async function scrollPanes() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
  if (logList.value) logList.value.scrollTop = logList.value.scrollHeight
}

function openDesigner(id: number) {
  router.push({ path: '/task-designer', query: { id: String(id) } })
}

async function copyLogs() {
  const text = logs.value.map(entry => {
    const lines = [
      `[${kindLabel(entry.kind)}] 第${entry.round || 0}轮 ${entry.title || ''}`,
      entry.detail || '',
      entry.payload ? formatPayload(entry.payload) : '',
    ]
    return lines.filter(Boolean).join('\n')
  }).join('\n\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制交互日志')
  } catch {
    ElMessage.error('复制失败')
  }
}

function appendLog(entry) {
  if (!entry) return
  logs.value.push(entry)
  void scrollPanes()
}

async function sendPrompt() {
  const prompt = draft.value.trim()
  if (!prompt || generating.value) return

  messages.value.push({ id: nextId++, role: 'user', text: prompt })
  draft.value = ''
  generating.value = true
  resultState.value = 'running'
  logs.value = []
  messages.value.push({
    id: nextId++,
    role: 'status',
    text: '正在生成草稿，右侧日志会实时更新…',
  })
  await scrollPanes()

  try {
    const terminal = await workflowApi.generateStream(prompt, appendLog)
    const payload = terminal?.data?.data || {}
    if (!logs.value.length) applyLogs(payload)
    messages.value = messages.value.filter(message => message.role !== 'status')
    if (terminal?.event === 'error' || terminal?.data?.success === false) {
      const failure = Error(terminal?.data?.message || '生成失败')
      ;(failure as any).alreadyLogged = true
      throw failure
    }
    const flowModelId = Number(generatedDraftId(payload))
    if (!Number.isInteger(flowModelId) || flowModelId <= 0) {
      const failure = Error('生成成功但未返回草稿 ID')
      ;(failure as any).alreadyLogged = true
      throw failure
    }
    const name = generatedDraftName(payload)
    const status = payload.status || 'DRAFT'
    resultState.value = 'success'
    messages.value.push({
      id: nextId++,
      role: 'assistant',
      text: `生成成功。草稿「${name}」#${flowModelId}（${status}）已保存。请先查看右侧日志，确认后再打开流程设计。`,
      flowModelId,
    })
    await scrollPanes()
    ElMessage.success(`生成成功：草稿 ${name} #${flowModelId}`)
  } catch (error: any) {
    if (!error?.alreadyLogged) applyLogs(payloadFromError(error) || {})
    messages.value = messages.value.filter(message => message.role !== 'status')
    resultState.value = 'failure'
    const message = error?.response?.data?.message || error?.message || '生成失败'
    messages.value.push({
      id: nextId++,
      role: 'assistant',
      text: `生成失败：${message}。右侧日志包含已发生的后端与大模型交互。`,
    })
    await scrollPanes()
    ElMessage.error(message)
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.agent-workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 42%);
  background: #ffffff;
}

.agent-main,
.agent-log {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.agent-log {
  border-left: 1px solid #e2e8f0;
  background: #f8fafc;
}

.pane-head {
  height: 38px;
  padding: 0 14px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fafbfc;
  flex-shrink: 0;
}

.pane-title {
  font-size: 12.5px;
  font-weight: 700;
  color: #0f172a;
}

.log-count {
  margin-left: auto;
  font-size: 11px;
  color: #64748b;
}

.result-pill {
  margin-left: auto;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #cbd5e1;
  color: #64748b;
  background: #f1f5f9;
}

.result-pill.running {
  color: #d97706;
  background: #fffbeb;
  border-color: #fde68a;
}

.result-pill.success {
  color: #16a34a;
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.result-pill.failure {
  color: #dc2626;
  background: #fef2f2;
  border-color: #fecaca;
}

.agent-chat-messages,
.log-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.agent-chat-messages {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #ffffff;
}

.chat-row {
  display: flex;
}

.chat-row.user {
  justify-content: flex-end;
}

.chat-row.status {
  justify-content: center;
}

.chat-bubble {
  max-width: 88%;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.chat-row.user .chat-bubble {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.chat-row.status .chat-bubble {
  background: transparent;
  border: none;
  padding: 0;
}

.chat-text {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.55;
  color: #0f172a;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-row.status .chat-text {
  color: #64748b;
  font-size: 12px;
}

.agent-chat-composer {
  flex-shrink: 0;
  padding: 10px 12px 12px;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.agent-chat-composer textarea {
  width: 100%;
  resize: none;
  box-sizing: border-box;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 12.5px;
  line-height: 1.5;
  color: #0f172a;
  font-family: inherit;
  outline: none;
}

.agent-chat-composer textarea:focus {
  border-color: #2563eb;
}

.agent-chat-composer textarea:disabled {
  background: #f8fafc;
  color: #94a3b8;
}

.btn-primary-blue {
  height: 26px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #ffffff;
  background: #2563eb;
  border: 1px solid #2563eb;
  border-radius: 4px;
  cursor: pointer;
  align-self: flex-end;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-primary-blue:hover:not(:disabled) {
  background: #1d4ed8;
  border-color: #1d4ed8;
}

.btn-primary-blue:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-aliyun-cta {
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  cursor: pointer;
}

.btn-link {
  font-size: 12px;
  font-weight: 500;
  color: #2563eb;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
}

.btn-link:disabled {
  color: #94a3b8;
  cursor: not-allowed;
}

.log-scroll {
  padding: 10px 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.log-empty {
  margin: 24px 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #94a3b8;
  text-align: center;
}

.log-entry {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 8px 10px;
}

.log-entry[data-kind="error"],
.log-entry[data-kind="gate"] {
  border-color: #fecaca;
  background: #fff7f7;
}

.log-entry[data-kind="done"] {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.log-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
}

.log-kind {
  font-size: 10.5px;
  font-weight: 700;
  color: #2563eb;
}

.log-round {
  font-size: 11px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.log-title {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.log-detail {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-word;
}

.log-payload {
  margin: 6px 0 0;
  max-height: 240px;
  overflow: auto;
  padding: 8px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
  line-height: 1.45;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
