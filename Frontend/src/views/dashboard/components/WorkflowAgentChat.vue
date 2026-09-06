<template>
  <section class="agent-workspace">
    <!-- 1. 左侧：极简历史会话侧边栏 -->
    <aside class="conversation-rail">
      <div class="rail-header-actions">
        <button class="btn-return-home" type="button" @click="$emit('close')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="19" y1="12" x2="5" y2="12"></line>
            <polyline points="12 19 5 12 12 5"></polyline>
          </svg>
          <span>返回实验室主控</span>
        </button>

        <button class="btn-new-conversation" type="button" @click="startNewConversation">
          <span>+ 新对话</span>
        </button>
      </div>

      <div class="conversation-list">
        <div class="history-label">对话历史</div>
        <p v-if="!store.ordered.length" class="list-empty">暂无历史对话</p>
        <button
          v-for="item in store.ordered"
          :key="item.id"
          type="button"
          class="conversation-item"
          :class="{ active: item.id === store.activeId }"
          @click="selectConversation(item.id)"
        >
          <span class="conversation-title">{{ item.title }}</span>
          <div class="conversation-meta-row">
            <span class="conversation-time">{{ formatTime(item.updatedAt) }}</span>
            <span
              class="conversation-delete"
              title="删除"
              @click.stop="store.removeConversation(item.id)"
            >×</span>
          </div>
        </button>
      </div>
    </aside>

    <!-- 2. 中间：全宽交互区域 (实时显示每轮过程与思维链) -->
    <div class="agent-main">
      <header class="agent-stage-header">
        <div class="stage-title-group">
          <span class="stage-title">SmartLab 流程编排 Agent</span>
        </div>
        <div class="stage-status-group">
          <span :class="['result-pill', computedResultState]">{{ resultLabel }}</span>
          <span v-if="roundCountLabel" class="model-trace-tip">模型交互 · {{ roundCountLabel }}</span>
          <button v-if="logs.length" class="btn-link copy-logs-btn" type="button" @click="copyLogs">复制过程日志</button>
        </div>
      </header>

      <!-- 对话与实时思考滚动视口 (充分利用页面全宽) -->
      <div ref="messageList" class="agent-chat-messages">
        <div class="chat-full-flow">
          <div v-if="!messages.length && !logs.length" class="chat-welcome-state">
            <h2>SmartLab 流程设计 Agent</h2>
            <p>使用自然语言描述您的实验流程逻辑，Agent 将实时调用设备检索、模型校验与草稿保存规程，生成符合工业规范的工作流模型。</p>
          </div>

          <article
            v-for="(message, index) in messages"
            :key="message.id"
            :class="['chat-msg-row', message.role]"
          >
            <div v-if="message.role === 'user'" class="user-msg-container">
              <div class="user-msg-bubble">
                <p class="chat-text">{{ message.text }}</p>
              </div>
            </div>

            <section v-if="showThinkingAfter(index)" class="live-thinking-section">
              <div class="ds-thinking-card">
                <button class="ds-thinking-toggle" type="button" @click="toggleGlobalThinking">
                  <svg
                    :class="['caret-icon', { collapsed: isThinkingCollapsed }]"
                    width="12"
                    height="12"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2.5"
                  >
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                  <strong class="thinking-title">思考过程</strong>
                  <span v-if="generating" class="live-generating-indicator">
                    <i class="pulse-dot"></i> 正在调用工具…
                  </span>
                  <span v-else class="thinking-summary-count">已完成 · {{ rounds.length }} 轮</span>
                </button>

                <div class="fold" :class="{ open: !isThinkingCollapsed }">
                  <div class="fold-inner">
                    <div class="ds-thinking-content">
                      <div v-for="round in rounds" :key="round.number" class="round-block">
                        <button class="round-head-toggle" type="button" @click="toggleRoundCollapse(round.number)">
                          <svg
                            :class="['caret-round', { collapsed: isRoundCollapsed(round.number) }]"
                            width="11"
                            height="11"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="2.5"
                          >
                            <polyline points="6 9 12 15 18 9"></polyline>
                          </svg>
                          <span>第 {{ round.number }} 轮</span>
                          <span class="round-item-count">{{ round.entries.length }} 项</span>
                        </button>

                        <div class="fold" :class="{ open: !isRoundCollapsed(round.number) }">
                          <div class="fold-inner">
                            <div class="round-entries-list">
                              <div
                                v-for="(entry, entryIndex) in round.entries"
                                :key="entryIndex"
                                class="round-log-entry"
                                :data-kind="entry.kind"
                                :style="{ animationDelay: `${Math.min(entryIndex, 12) * 40}ms` }"
                              >
                                <button
                                  v-if="hasEntryDetail(entry)"
                                  class="entry-main-line expandable"
                                  type="button"
                                  @click="togglePayload(round.number, entryIndex)"
                                >
                                  <svg
                                    :class="['caret-sub', { collapsed: !isPayloadOpen(round.number, entryIndex) }]"
                                    width="10"
                                    height="10"
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    stroke-width="2.5"
                                  >
                                    <polyline points="6 9 12 15 18 9"></polyline>
                                  </svg>
                                  <span class="log-kind-tag">[{{ kindLabel(entry.kind) }}]</span>
                                  <span class="log-entry-title">{{ formatEntryTitle(entry) }}</span>
                                </button>
                                <div v-else class="entry-main-line">
                                  <span class="log-kind-tag">[{{ kindLabel(entry.kind) }}]</span>
                                  <span class="log-entry-title">{{ formatEntryTitle(entry) }}</span>
                                </div>

                                <div class="fold" :class="{ open: hasEntryDetail(entry) && isPayloadOpen(round.number, entryIndex) }">
                                  <div class="fold-inner">
                                    <div class="entry-detail-box">
                                      <pre class="entry-payload-pre">{{ formatPayload(entry.payload || entry.detail) }}</pre>
                                    </div>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <div v-if="generating && isLatestUserMessage(index) && !latestAnswerText" class="answer-pending">
              <span class="answer-pending-dots"></span>
              正在生成回答
            </div>

            <div
              v-else-if="isLatestUserMessage(index) && latestAnswerText && !hasAssistantAfter(index)"
              class="ai-msg-container"
            >
              <div class="ds-markdown-body answer-appear">
                <div class="ai-answer-label">回答</div>
                <div class="chat-text formatted-text">{{ latestAnswerText }}</div>
              </div>
            </div>

            <div v-else-if="message.role === 'assistant'" class="ai-msg-container">
              <div class="ds-markdown-body answer-appear">
                <div class="ai-answer-label">回答</div>
                <div class="chat-text formatted-text">{{ message.text }}</div>
              </div>
            </div>
          </article>
        </div>
      </div>

      <!-- 3. 底部：全宽输入框 -->
      <form class="agent-chat-composer" @submit.prevent="sendPrompt">
        <div class="composer-card-box">
          <textarea
            v-model="draft"
            :disabled="generating"
            rows="2"
            placeholder="给 SmartLab Agent 发送消息或修改指令（例如：把加热保持时间延长到 60 秒）..."
            @keydown.enter.exact.prevent="sendPrompt"
          />
          <div class="composer-card-bottom">
            <span class="composer-note">发送将再生成一份草稿，不会改写上一份</span>
            <button
              class="btn-send-round"
              type="submit"
              :disabled="generating || !draft.trim()"
              :title="generating ? '生成中' : '发送'"
            >
              <svg v-if="!generating" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <line x1="12" y1="19" x2="12" y2="5"></line>
                <polyline points="5 12 12 5 19 12"></polyline>
              </svg>
              <span v-else class="sending-spinner">...</span>
            </button>
          </div>
        </div>
        <div class="composer-bottom-disclaimer">内容由 SmartLab 实验室 AI Agent 规程推演生成，请在流程设计器中确认后发布使用。</div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAgentConversationStore } from '../../../stores/agentConversationStore'
import { workflowApi } from '../../../services/workflowApi.js'
import { workflowModelId, workflowModelName } from '../../../utils/workflowAuthoring.js'

defineEmits(['close'])

type InteractionLog = {
  round?: number
  kind?: string
  title?: string
  detail?: string
  payload?: string
}

const KIND_LABELS: Record<string, string> = {
  start: '会话',
  llm_call: '请求',
  llm_reply: '回复',
  tool_call: '调用',
  tool_result: '返回',
  gate: '拦截',
  nudge: '解答',
  error: '错误',
  done: '完成',
  answer: '回答',
}

const router = useRouter()
const store = useAgentConversationStore()
const draft = ref('')
const generating = computed(() => store.generating)
const messageList = ref<HTMLElement | null>(null)
const openPayloads = ref<Record<string, boolean>>({})
const isThinkingCollapsed = ref(true)
const collapsedRounds = ref<Record<number, boolean>>({})

const messages = computed(() => store.active?.messages || [])
const logs = computed(() => store.active?.logs || [])
const resultState = computed(() => store.active?.resultState || 'idle')

const computedResultState = computed(() => {
  if (generating.value) return 'running'
  if (resultState.value === 'running') return 'idle'
  return resultState.value
})

const resultLabel = computed(() => {
  if (generating.value) return '生成中'
  if (resultState.value === 'success') return '已完成'
  if (resultState.value === 'failure') return '执行失败'
  if (resultState.value === 'replied') return '已回复'
  return '就绪'
})

const rounds = computed(() => {
  const grouped = new Map<number, InteractionLog[]>()
  for (const entry of logs.value) {
    const number = Number(entry.round) || 0
    if (number <= 0) continue
    const list = grouped.get(number) || []
    list.push(entry)
    grouped.set(number, list)
  }
  return [...grouped.entries()]
    .sort((left, right) => left[0] - right[0])
    .map(([number, entries]) => ({ number, entries }))
})

const roundCountLabel = computed(() => {
  if (!rounds.value.length) return ''
  return `${rounds.value.length} 轮`
})

const lastRoundNumber = computed(() => {
  if (!rounds.value.length) return 1
  return rounds.value[rounds.value.length - 1].number
})

function isLatestUserMessage(index: number) {
  let lastUserIndex = -1
  messages.value.forEach((item, itemIndex) => {
    if (item.role === 'user') lastUserIndex = itemIndex
  })
  return index === lastUserIndex
}

function showThinkingAfter(index: number) {
  return isLatestUserMessage(index) && !!(logs.value.length || generating.value)
}

function hasAssistantAfter(index: number) {
  return messages.value.slice(index + 1).some(item => item.role === 'assistant')
}

const latestAnswerText = computed(() => {
  const answer = [...logs.value].reverse().find(item => item.kind === 'answer')
  if (answer?.detail?.trim()) return answer.detail.trim()
  return ''
})

function toggleGlobalThinking() {
  isThinkingCollapsed.value = !isThinkingCollapsed.value
}

function isRoundCollapsed(roundNumber: number) {
  if (collapsedRounds.value[roundNumber] !== undefined) {
    return collapsedRounds.value[roundNumber]
  }
  // 默认：仅展开最后一轮，前面的历史轮次自动折叠
  return roundNumber !== lastRoundNumber.value
}

function toggleRoundCollapse(roundNumber: number) {
  const current = isRoundCollapsed(roundNumber)
  collapsedRounds.value = {
    ...collapsedRounds.value,
    [roundNumber]: !current,
  }
}

function formatEntryTitle(entry: InteractionLog) {
  if (entry.kind === 'tool_call') {
    return `模型调用工具：${toolCallName(entry) || entry.title || entry.detail || ''}`
  }
  if (entry.kind === 'tool_result') {
    return `工具执行返回：${entry.title || ''}`
  }
  if (entry.kind === 'llm_reply') {
    const parts: string[] = []
    const content = extractTextContent(entry)
    if (content) {
      const firstLine = content.split('\n')[0].replace(/^#+\s*/, '').trim()
      parts.push(firstLine.length > 50 ? firstLine.slice(0, 50) + '...' : firstLine)
    }
    const names = assistantToolCallNames(entry)
    if (names.length) parts.push(names.join('、'))
    if (parts.length) return parts.join(' · ')
    return entry.title || '模型返回回复'
  }
  return entry.title || entry.detail || ''
}

function hasEntryDetail(entry: InteractionLog) {
  return !!(entry.payload || (entry.detail && entry.detail !== entry.title))
}

function extractTextContent(entry: InteractionLog): string | null {
  if (entry.payload) {
    try {
      const parsed = JSON.parse(entry.payload)
      if (parsed && typeof parsed.content === 'string' && parsed.content.trim()) {
        return parsed.content.trim()
      }
    } catch {
      // not json, fallback
    }
  }
  if (entry.kind === 'llm_reply' && entry.detail && entry.detail.length > 30) {
    return entry.detail
  }
  return null
}

function parseLogPayload(entry: InteractionLog): any {
  if (!entry.payload) return null
  try {
    return JSON.parse(entry.payload)
  } catch {
    return null
  }
}

function assistantToolCallNames(entry: InteractionLog): string[] {
  const parsed = parseLogPayload(entry)
  if (!parsed) return []
  const calls = parsed.tool_calls || parsed.toolCalls
  if (!Array.isArray(calls)) return []
  return calls
    .map((call: any) => call?.function?.name || call?.name)
    .filter((name: unknown): name is string => typeof name === 'string' && name.length > 0)
}

function toolCallName(entry: InteractionLog): string {
  const parsed = parseLogPayload(entry)
  if (!parsed) return ''
  if (typeof parsed.function?.name === 'string') return parsed.function.name
  if (typeof parsed.name === 'string') return parsed.name
  return ''
}

function generatedDraftId(payload: any) {
  return workflowModelId(payload) ?? workflowModelId(payload?.definition || {})
}

function generatedDraftName(payload: any) {
  return workflowModelName(payload?.definition || payload) || '未命名流程'
}

function draftIdFromLogs() {
  const done = [...logs.value].reverse().find(item => item.kind === 'done' && item.detail)
  const matched = String(done?.detail || '').match(/flowModelId=(\d+)/)
  if (!matched) return null
  return Number(matched[1])
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

function formatTime(value?: number) {
  if (!value) return ''
  const date = new Date(value)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function payloadKey(round: number, index: number) {
  return `${round}-${index}`
}

function isPayloadOpen(round: number, index: number) {
  const entry = rounds.value.find(item => item.number === round)?.entries[index]
  if (entry?.kind === 'error' || entry?.kind === 'gate') return true
  return !!openPayloads.value[payloadKey(round, index)]
}

function togglePayload(round: number, index: number) {
  const key = payloadKey(round, index)
  openPayloads.value = { ...openPayloads.value, [key]: !openPayloads.value[key] }
}

function payloadFromError(error: any) {
  return error?.response?.data?.data || null
}

async function scrollPanes() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

function selectConversation(id: string) {
  store.selectConversation(id)
  openPayloads.value = {}
  collapsedRounds.value = {}
  isThinkingCollapsed.value = true
  void scrollPanes()
}

function startNewConversation() {
  store.createConversation()
  openPayloads.value = {}
  collapsedRounds.value = {}
  isThinkingCollapsed.value = true
  void scrollPanes()
}

function appendLog(entry: any) {
  store.appendLog(entry)
  void scrollPanes()
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
    ElMessage.success('已复制过程日志')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function sendPrompt() {
  const prompt = draft.value.trim()
  if (!prompt || generating.value) return

  draft.value = ''
  openPayloads.value = {}
  isThinkingCollapsed.value = false // 发送时自动展开思考过程
  await scrollPanes()

  await store.sendPrompt(prompt)
  await scrollPanes()
}

watch(() => logs.value.length, () => {
  void scrollPanes()
})

watch(() => messages.value.length, () => {
  void scrollPanes()
})

onMounted(() => {
  if (generating.value) {
    isThinkingCollapsed.value = false
  }
  void scrollPanes()
})
</script>

<style scoped>
/* ==========================================================================
   DeepSeek 极简全宽 AI 对话工作台 (实时过程呈现 · 优雅下三角折叠)
   ========================================================================== */
.agent-workspace {
  width: 100%;
  height: calc(100vh - 54px);
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  background: #ffffff;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif;
  overflow: hidden;
  box-sizing: border-box;
}

/* 1. 左侧：极简侧边栏 */
.conversation-rail {
  background: #f7f7f8;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.rail-header-actions {
  padding: 12px 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-bottom: 1px solid #f0f0f2;
}

.btn-return-home {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #4b5563;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-return-home:hover {
  background: #ececec;
  color: #111827;
}

.btn-new-conversation {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 12px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: #111827;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-new-conversation:hover {
  background: #fdfdfd;
  border-color: #d1d5db;
}
.btn-new-conversation:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.conversation-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.history-label {
  font-size: 11px;
  font-weight: 600;
  color: #8e8ea0;
  padding: 6px 8px 4px;
}

.list-empty {
  margin: 20px 8px;
  text-align: center;
  font-size: 12px;
  color: #8e8ea0;
}

.conversation-item {
  position: relative;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 3px;
  transition: background 0.15s;
}
.conversation-item:hover {
  background: #ececec;
}
.conversation-item.active {
  background: #e9ecef;
  border-color: #e5e7eb;
}

.conversation-title {
  font-size: 12.5px;
  font-weight: 500;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conversation-item.active .conversation-title {
  font-weight: 600;
  color: #4d6bfe;
}

.conversation-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.conversation-time {
  font-size: 10.5px;
  color: #8e8ea0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.conversation-delete {
  font-size: 14px;
  line-height: 1;
  color: #8e8ea0;
  padding: 0 2px;
}
.conversation-delete:hover {
  color: #dc2626;
}

/* 2. 中间主区域：全宽排版 */
.agent-main {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  background: #ffffff;
  position: relative;
}

.agent-stage-header {
  height: 44px;
  padding: 0 20px;
  border-bottom: 1px solid #f0f0f2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  flex-shrink: 0;
}

.stage-title-group {
  display: flex;
  align-items: center;
}
.stage-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.stage-status-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.model-trace-tip {
  font-size: 11px;
  color: #8e8ea0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.copy-logs-btn {
  font-size: 11.5px;
}

.result-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
  color: #6b7280;
  background: #f9fafb;
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
.result-pill.replied {
  color: #4d6bfe;
  background: #eff2fe;
  border-color: #bfdbfe;
}

/* 对话流：全宽自适应 */
.agent-chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  padding: 16px 20px;
}

.chat-full-flow {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-welcome-state {
  margin: 30px auto;
  max-width: 600px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.chat-welcome-state h2 {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  margin: 0;
}
.chat-welcome-state p {
  font-size: 13px;
  line-height: 1.6;
  color: #6b7280;
  margin: 0;
}

.chat-msg-row {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 12px;
  animation: msg-in 0.28s ease;
}

@keyframes msg-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.user-msg-container {
  display: flex;
  justify-content: flex-end;
}
.user-msg-bubble {
  max-width: 85%;
  background: #f4f4f4;
  color: #111827;
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 13.5px;
  line-height: 1.6;
  word-break: break-word;
}

.ai-msg-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.ai-answer-label {
  font-size: 12px;
  font-weight: 700;
  color: #111827;
  letter-spacing: 0.02em;
}

.answer-appear {
  animation: answer-in 0.36s ease;
}

@keyframes answer-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.answer-pending {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7280;
  padding: 4px 0 8px;
}

.answer-pending-dots {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4d6bfe;
  animation: pulse 1.2s ease infinite;
}

/* 思考过程卡片 */
.live-thinking-section {
  width: 100%;
}

.ds-thinking-card {
  border-left: 2.5px solid #cbd5e1;
  padding-left: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ds-thinking-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: #4b5563;
  cursor: pointer;
  user-select: none;
  width: fit-content;
  max-width: 100%;
  background: transparent;
  border: none;
  padding: 0;
  font-family: inherit;
  transition: color 0.18s ease;
}
.ds-thinking-toggle:hover { color: #111827; }

.caret-icon {
  transition: transform 0.22s ease;
  color: #6b7280;
  flex-shrink: 0;
}
.caret-icon.collapsed {
  transform: rotate(-90deg);
}

.thinking-title {
  font-weight: 600;
}

.live-generating-indicator {
  font-size: 11.5px;
  color: #d97706;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: 6px;
}
.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d97706;
  animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0% { opacity: 0.4; }
  50% { opacity: 1; }
  100% { opacity: 0.4; }
}

.thinking-summary-count {
  font-size: 11.5px;
  color: #8e8ea0;
  margin-left: 4px;
}

.ds-thinking-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}

.round-block {
  background: #fafbfc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fold {
  display: grid;
  grid-template-rows: 0fr;
  pointer-events: none;
  transition: grid-template-rows 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}
.fold.open {
  grid-template-rows: 1fr;
  pointer-events: auto;
}
.fold:not(.open) {
  max-height: 0;
  overflow: hidden;
}
.fold-inner {
  min-height: 0;
  overflow: hidden;
}

.round-head-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  cursor: pointer;
  user-select: none;
  background: transparent;
  border: none;
  padding: 0;
  width: 100%;
  text-align: left;
  transition: color 0.18s ease;
}
.round-head-toggle:hover {
  color: #4d6bfe;
}
.caret-round {
  transition: transform 0.22s ease;
  color: #6b7280;
  flex-shrink: 0;
}
.caret-round.collapsed {
  transform: rotate(-90deg);
}

.round-item-count {
  font-size: 11px;
  font-weight: normal;
  color: #8e8ea0;
}

.round-entries-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.round-log-entry {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  animation: entry-in 0.24s ease both;
}

@keyframes entry-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.entry-main-line {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  min-width: 0;
}

.entry-main-line.expandable {
  cursor: pointer;
  border-radius: 4px;
  padding: 2px 4px;
  margin: 0 -4px;
  width: 100%;
  background: transparent;
  border: none;
  font: inherit;
  text-align: left;
  transition: background 0.15s ease;
}
.entry-main-line.expandable:hover {
  background: #eef2ff;
}

.log-kind-tag {
  color: #4d6bfe;
  font-weight: 600;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  flex-shrink: 0;
}
.round-log-entry[data-kind="error"] .log-kind-tag,
.round-log-entry[data-kind="gate"] .log-kind-tag {
  color: #dc2626;
}
.round-log-entry[data-kind="done"] .log-kind-tag {
  color: #16a34a;
}

.log-entry-title {
  color: #374151;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.caret-sub {
  transition: transform 0.22s ease;
  color: #9ca3af;
  flex-shrink: 0;
}
.caret-sub.collapsed {
  transform: rotate(-90deg);
}

.entry-main-line.expandable:hover .caret-sub {
  color: #4d6bfe;
}

.entry-detail-box {
  margin: 4px 0 2px 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 12px;
}

.detail-text-content {
  font-size: 12px;
  line-height: 1.65;
  color: #1e293b;
  white-space: pre-wrap;
  word-break: break-word;
}

.entry-payload-pre {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  font-size: 11px;
  line-height: 1.45;
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 纯文本 Markdown 结构化输出 */
.ds-markdown-body {
  font-size: 13.5px;
  line-height: 1.68;
  color: #111827;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.formatted-text {
  background: #ffffff;
  padding: 8px 0;
}

/* 3. 底部全宽输入框 */
.agent-chat-composer {
  flex-shrink: 0;
  width: 100%;
  padding: 0 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-sizing: border-box;
}

.composer-card-box {
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  padding: 10px 14px 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.composer-card-box:focus-within {
  border-color: #9ca3af;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.composer-card-box textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.5;
  color: #111827;
  background: transparent;
  min-height: 44px;
  box-sizing: border-box;
}

.composer-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.composer-note {
  margin: 0;
  font-size: 11.5px;
  color: #8e8ea0;
}

.btn-send-round {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #4d6bfe;
  color: #ffffff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
  flex-shrink: 0;
}
.btn-send-round:hover {
  background: #3b57e8;
}
.btn-send-round:disabled {
  background: #e5e7eb;
  color: #9ca3af;
  cursor: not-allowed;
}

.sending-spinner {
  font-size: 11px;
  font-weight: 700;
}

.composer-bottom-disclaimer {
  text-align: center;
  font-size: 11px;
  color: #8e8ea0;
}

.btn-link {
  font-size: 12px;
  font-weight: 500;
  color: #4d6bfe;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
}
.btn-link:hover {
  text-decoration: underline;
}
</style>
