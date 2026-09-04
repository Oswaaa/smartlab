export const AGENT_CONVERSATION_LIMIT = 40
export const AGENT_LOG_PAYLOAD_LIMIT = 4000

export function agentConversationStorageKey(username) {
  return `smartlab_agent_conversations:${username || 'anon'}`
}

export function newAgentId(prefix) {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

export function titleFromPrompt(prompt) {
  const text = String(prompt || '').replace(/\s+/g, ' ').trim()
  if (!text) return '新对话'
  return text.length > 22 ? `${text.slice(0, 22)}…` : text
}

export function emptyConversation(id = newAgentId('c')) {
  return {
    id,
    title: '新对话',
    updatedAt: Date.now(),
    messages: [],
    logs: [],
    resultState: 'idle',
  }
}

export function clipLogPayload(payload) {
  if (payload == null || payload === '') return payload
  const text = String(payload)
  if (text.length <= AGENT_LOG_PAYLOAD_LIMIT) return text
  return `${text.slice(0, AGENT_LOG_PAYLOAD_LIMIT)}\n…(已截断)`
}

export function clipConversation(conversation) {
  if (!conversation || typeof conversation !== 'object') return emptyConversation()
  const resultState = conversation.resultState === 'running' ? 'idle' : (conversation.resultState || 'idle')
  return {
    id: String(conversation.id || newAgentId('c')),
    title: conversation.title || '新对话',
    updatedAt: Number(conversation.updatedAt) || Date.now(),
    messages: Array.isArray(conversation.messages) ? conversation.messages : [],
    logs: (Array.isArray(conversation.logs) ? conversation.logs : []).map(entry => ({
      ...entry,
      payload: clipLogPayload(entry?.payload),
    })),
    resultState,
  }
}

export function parseConversationState(raw) {
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    const conversations = (Array.isArray(parsed?.conversations) ? parsed.conversations : [])
      .map(clipConversation)
      .slice(0, AGENT_CONVERSATION_LIMIT)
    const activeId = conversations.some(item => item.id === parsed?.activeId)
      ? parsed.activeId
      : (conversations[0]?.id || null)
    return { conversations, activeId }
  } catch {
    return { conversations: [], activeId: null }
  }
}

export function serializeConversationState(conversations, activeId) {
  const clipped = (conversations || []).map(clipConversation).slice(0, AGENT_CONVERSATION_LIMIT)
  return JSON.stringify({ activeId: activeId || clipped[0]?.id || null, conversations: clipped })
}
