import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import { useAuthStore } from './authStore'
import {
  agentConversationStorageKey,
  emptyConversation,
  newAgentId,
  parseConversationState,
  serializeConversationState,
  titleFromPrompt,
} from '../utils/agentConversationStorage.js'
import { workflowApi } from '../services/workflowApi.js'
import { workflowModelId, workflowModelName } from '../utils/workflowAuthoring.js'

function generatedDraftId(payload: any) {
  return workflowModelId(payload) ?? workflowModelId(payload?.definition || {})
}

function generatedDraftName(payload: any) {
  return workflowModelName(payload?.definition || payload) || '未命名流程'
}

function draftIdFromLogs(logs: any[]) {
  const done = [...logs].reverse().find(item => item.kind === 'done' && item.detail)
  const matched = String(done?.detail || '').match(/flowModelId=(\d+)/)
  if (!matched) return null
  return Number(matched[1])
}

function latestAnswerTextFromLogs(logs: any[]) {
  const answer = [...logs].reverse().find(item => item.kind === 'answer')
  if (answer?.detail?.trim()) return answer.detail.trim()
  return ''
}

function extractTextContent(entry: any): string | null {
  if (entry?.payload) {
    try {
      const parsed = JSON.parse(entry.payload)
      if (parsed && typeof parsed.content === 'string' && parsed.content.trim()) {
        return parsed.content.trim()
      }
    } catch {
      // ignore
    }
  }
  if (entry?.kind === 'llm_reply' && entry?.detail && entry.detail.length > 30) {
    return entry.detail
  }
  return null
}

export const useAgentConversationStore = defineStore('agentConversations', () => {
  const auth = useAuthStore()
  const conversations = ref<any[]>([])
  const activeId = ref<string | null>(null)

  // 全局持久状态（跨路由切页不销毁）
  const generating = ref(false)
  const isChatOpen = ref(false)
  const activePrompt = ref('')

  function openChat() {
    isChatOpen.value = true
  }

  function closeChat() {
    isChatOpen.value = false
  }

  const storageKey = computed(() => agentConversationStorageKey(auth.username))
  const active = computed(() => conversations.value.find(item => item.id === activeId.value) || null)
  const ordered = computed(() =>
    [...conversations.value].sort((left, right) => (right.updatedAt || 0) - (left.updatedAt || 0))
  )

  function persist() {
    try {
      localStorage.setItem(storageKey.value, serializeConversationState(conversations.value, activeId.value))
    } catch {
      conversations.value = conversations.value.slice(0, Math.max(8, conversations.value.length - 4))
      try {
        localStorage.setItem(storageKey.value, serializeConversationState(conversations.value, activeId.value))
      } catch {
        // quota
      }
    }
  }

  function load() {
    const saved = parseConversationState(localStorage.getItem(storageKey.value) || '')
    conversations.value = saved.conversations
    activeId.value = saved.activeId
  }

  function updateActive(mutator) {
    requireActive()
    conversations.value = conversations.value.map(item => {
      if (item.id !== activeId.value) return item
      const next = {
        ...item,
        messages: [...(item.messages || [])],
        logs: [...(item.logs || [])],
      }
      mutator(next)
      next.updatedAt = Date.now()
      return next
    })
    persist()
  }

  function requireActive() {
    if (active.value) return active.value
    const created = emptyConversation()
    conversations.value = [created, ...conversations.value]
    activeId.value = created.id
    persist()
    return created
  }

  function createConversation() {
    if (active.value && active.value.messages.length === 0) return active.value
    const created = emptyConversation()
    conversations.value = [created, ...conversations.value]
    activeId.value = created.id
    persist()
    return created
  }

  function selectConversation(id) {
    if (!conversations.value.some(item => item.id === id)) return
    activeId.value = id
    persist()
  }

  function removeConversation(id) {
    const remaining = conversations.value.filter(item => item.id !== id)
    conversations.value = remaining
    if (activeId.value === id) activeId.value = remaining[0]?.id || null
    persist()
  }

  function appendMessage(message) {
    const next = {
      id: message.id || newAgentId('m'),
      role: message.role,
      text: message.text,
      ...(message.flowModelId ? { flowModelId: message.flowModelId } : {}),
    }
    updateActive(conversation => {
      conversation.messages.push(next)
      if (message.role === 'user' && conversation.messages.filter(item => item.role === 'user').length === 1) {
        conversation.title = titleFromPrompt(message.text)
      }
    })
    return next
  }

  function beginRun() {
    updateActive(conversation => {
      conversation.logs = []
      conversation.resultState = 'running'
    })
  }

  function appendLog(entry) {
    if (!active.value || !entry) return
    updateActive(conversation => {
      conversation.logs.push(entry)
    })
  }

  function replaceLogs(logs) {
    if (!active.value) return
    updateActive(conversation => {
      conversation.logs = Array.isArray(logs) ? logs : []
    })
  }

  function setResultState(state: string) {
    if (!active.value) return
    updateActive(conversation => {
      conversation.resultState = state
    })
  }

  /**
   * 全局发起工作流流式生成任务（即使切换页面、组件卸载，流连接与状态接收依然在全局持续执行）
   */
  async function sendPrompt(prompt: string) {
    const text = prompt.trim()
    if (!text || generating.value) return

    requireActive()
    appendMessage({ role: 'user', text })
    generating.value = true
    activePrompt.value = text
    beginRun()

    try {
      const terminal = await workflowApi.generateStream(text, (logEntry: any) => {
        appendLog(logEntry)
      })

      const payload = terminal?.data?.data || terminal?.data || {}
      const curLogs = active.value?.logs || []
      if (!curLogs.length) {
        replaceLogs(Array.isArray(payload?.logs) ? payload.logs : [])
      }

      const answerText = payload.summary || latestAnswerTextFromLogs(active.value?.logs || [])
      const rawDraftId = generatedDraftId(payload)
      const flowModelId = rawDraftId ? Number(rawDraftId) : draftIdFromLogs(active.value?.logs || [])

      if (flowModelId && Number.isInteger(flowModelId) && flowModelId > 0) {
        const name = generatedDraftName(payload)
        const status = payload.status || 'DRAFT'
        setResultState('success')
        appendMessage({
          role: 'assistant',
          text: answerText || `已根据您的实验需求完成工作流模型推演「${name}」#${flowModelId}（状态：${status}）。`,
          flowModelId,
        })
        ElNotification({
          title: 'AI 流程推演完成',
          message: `已成功生成工作流模型「${name}」#${flowModelId}`,
          type: 'success',
          duration: 5000,
        })
      } else if (answerText) {
        setResultState('replied')
        appendMessage({ role: 'assistant', text: answerText })
      } else {
        const lastReplyLog = [...(active.value?.logs || [])].reverse().find((l: any) => l.kind === 'llm_reply' || l.kind === 'nudge' || l.kind === 'start')
        const textContent = lastReplyLog ? extractTextContent(lastReplyLog) : null
        const replyText = textContent || terminal?.data?.message || payload?.message || 'Agent 已完成分析。请查看思考过程，或继续输入指令。'
        setResultState('replied')
        appendMessage({
          role: 'assistant',
          text: replyText,
        })
      }
    } catch (error: any) {
      setResultState('failure')
      const message = error?.response?.data?.message || error?.message || '生成未完成'
      const lastReplyLog = [...(active.value?.logs || [])].reverse().find((l: any) => l.kind === 'llm_reply')
      const textContent = lastReplyLog ? extractTextContent(lastReplyLog) : null
      const replyText = textContent || message
      appendMessage({ role: 'assistant', text: replyText })
      ElNotification({
        title: 'AI 流程推演中断或失败',
        message: replyText,
        type: 'error',
        duration: 5000,
      })
    } finally {
      generating.value = false
      activePrompt.value = ''
    }
  }

  load()
  watch(storageKey, load)

  return {
    conversations,
    activeId,
    active,
    ordered,
    generating,
    isChatOpen,
    activePrompt,
    openChat,
    closeChat,
    sendPrompt,
    createConversation,
    selectConversation,
    removeConversation,
    appendMessage,
    beginRun,
    appendLog,
    replaceLogs,
    setResultState,
  }
})
