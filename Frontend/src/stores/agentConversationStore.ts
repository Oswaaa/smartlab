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
      // 待确认卡片随消息一起存，刷新页面后卡片还在（状态会降级成已过期）
      ...(message.card ? { card: message.card } : {}),
      ...(message.token ? { token: message.token } : {}),
      ...(message.cardState ? { cardState: message.cardState } : {}),
      ...(message.cardResult ? { cardResult: message.cardResult } : {}),
    }
    updateActive(conversation => {
      conversation.messages.push(next)
      if (message.role === 'user' && conversation.messages.filter(item => item.role === 'user').length === 1) {
        conversation.title = titleFromPrompt(message.text)
      }
    })
    return next
  }

  /** 就地改一条消息（卡片状态、结果文案）。整条替换才能让 Vue 看到变化。 */
  function patchMessage(id, patch) {
    updateActive(conversation => {
      conversation.messages = conversation.messages.map(item =>
        item.id === id ? { ...item, ...patch } : item
      )
    })
  }

  /** 新卡片到手时，把还在等确认的旧卡片标成已过期——服务端那边旧 token 也已经作废。 */
  function supersedePendingCards() {
    updateActive(conversation => {
      conversation.messages = conversation.messages.map(item => {
        if (item.cardState !== 'PENDING') return item
        return { ...item, cardState: 'EXPIRED', cardResult: '已被新的操作取代' }
      })
    })
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
   * 全局发起一轮对话（即使切换页面、组件卸载，流连接与状态接收依然在全局持续执行）。
   *
   * <p>同一个输入框现在既能画流程也能操作设备，靠服务端的工具选择分流：
   * 返回 {@code card} 就是一张待确认卡片，返回 {@code flowModelId} 就是一张流程草稿。
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
      const terminal = await workflowApi.generateStream(
        text,
        active.value?.sessionId || null,
        (logEntry: any) => {
          appendLog(logEntry)
        }
      )

      const payload = terminal?.data?.data || terminal?.data || {}
      const curLogs = active.value?.logs || []
      if (!curLogs.length) {
        replaceLogs(Array.isArray(payload?.logs) ? payload.logs : [])
      }

      // 会话号必须存下来，否则"我反问一句、你回一句"下一轮就断线了
      if (payload?.sessionId) {
        updateActive(conversation => {
          conversation.sessionId = payload.sessionId
        })
      }

      const answerText = payload.summary || latestAnswerTextFromLogs(active.value?.logs || [])
      const rawDraftId = generatedDraftId(payload)
      const flowModelId = rawDraftId ? Number(rawDraftId) : draftIdFromLogs(active.value?.logs || [])
      const card = payload?.card || null

      if (card) {
        // 拿到了待确认卡片：这一轮没有执行任何动作，等用户点确认
        supersedePendingCards()
        setResultState('pending')
        appendMessage({
          role: 'assistant',
          text: answerText || (card.kind === 'FLOW_DRAFT'
            ? '流程草稿已整理好，确认后才会保存。'
            : '已准备好，请确认后再执行。'),
          card,
          token: payload?.token || '',
          cardState: 'PENDING',
          cardResult: '',
        })
      } else if (flowModelId && Number.isInteger(flowModelId) && flowModelId > 0) {
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
        title: 'AI 处理中断或失败',
        message: replyText,
        type: 'error',
        duration: 5000,
      })
    } finally {
      generating.value = false
      activePrompt.value = ''
    }
  }

  /**
   * 点确认：真正下发的唯一入口。
   *
   * <p>服务端会重新跑一遍预检，所以这里不看前端快照，一切以返回的 status 为准。
   *
   * <p>{@code mode = 'SIMULATION'} 是「仿真执行」按钮：设备走点动虚拟机，任务只做流程推演。
   * 跑完服务端会带回一张新的"正式执行"卡片，这里把它追加到对话里。
   */
  async function confirmCard(messageId: string, mode = '') {
    const conversation = active.value
    if (!conversation) return
    const target = (conversation.messages || []).find((item: any) => item.id === messageId)
    if (!target || target.cardState !== 'PENDING') return
    if (!conversation.sessionId || !target.token) {
      patchMessage(messageId, { cardState: 'EXPIRED', cardResult: '待确认信息已失效，请重新说一遍' })
      return
    }

    const simulated = mode === 'SIMULATION'
    patchMessage(messageId, { cardState: 'BUSY', cardAction: simulated ? 'simulate' : 'confirm' })
    const isFlowDraft = target.card?.kind === 'FLOW_DRAFT'
    try {
      const res = await workflowApi.confirmAction(conversation.sessionId, target.token, mode)
      const body = res?.data || {}
      const data = body.data || {}
      if (!body.success) {
        patchMessage(messageId, { cardState: 'BLOCKED', cardResult: body.message || '执行失败', cardAction: '' })
        return
      }
      const executed = data.status === 'EXECUTED'
      patchMessage(messageId, {
        cardState: executed ? 'DONE' : 'BLOCKED',
        cardResult: data.message || (executed ? (isFlowDraft ? '草稿已保存' : '已执行') : '执行被拒绝'),
        cardDetail: data.detail || null,
        cardAction: '',
      })
      if (executed) {
        const followUp = data.detail?.followUpCard
        const followUpToken = data.detail?.followUpToken
        if (followUp && followUpToken) {
          appendMessage({
            role: 'assistant',
            text: simulated
              ? '仿真执行已通过，真机没有任何动作。要正式执行就点下面的确认。'
              : '这一步已经完成。',
            card: followUp,
            token: followUpToken,
            cardState: 'PENDING',
          })
        }
        ElNotification({
          title: simulated ? '仿真执行完成' : (isFlowDraft ? '草稿已保存' : '指令已下发'),
          message: data.message || (simulated ? '真机没有动作' : (isFlowDraft ? '可以到设计器里继续修改' : '执行完成')),
          type: 'success',
          duration: 4000,
        })
      }
    } catch (error: any) {
      patchMessage(messageId, {
        cardState: 'BLOCKED',
        cardResult: error?.response?.data?.message || error?.message || '执行失败',
        cardAction: '',
      })
    }
  }

  async function cancelCard(messageId: string) {
    const conversation = active.value
    if (!conversation) return
    patchMessage(messageId, { cardState: 'CANCELLED', cardResult: '已取消，设备没有任何动作' })
    if (!conversation.sessionId) return
    try {
      await workflowApi.cancelAction(conversation.sessionId)
    } catch {
      // 取消失败不影响前端状态：服务端待确认会在 5 分钟后自然过期
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
    confirmCard,
    cancelCard,
    patchMessage,
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
