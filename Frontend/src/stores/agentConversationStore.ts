import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from './authStore'
import {
  agentConversationStorageKey,
  emptyConversation,
  newAgentId,
  parseConversationState,
  serializeConversationState,
  titleFromPrompt,
} from '../utils/agentConversationStorage.js'

export const useAgentConversationStore = defineStore('agentConversations', () => {
  const auth = useAuthStore()
  const conversations = ref([])
  const activeId = ref(null)

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

  function setResultState(state) {
    if (!active.value) return
    updateActive(conversation => {
      conversation.resultState = state
    })
  }

  load()
  watch(storageKey, load)

  return {
    conversations,
    activeId,
    active,
    ordered,
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
