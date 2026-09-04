import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  agentConversationStorageKey,
  clipLogPayload,
  parseConversationState,
  serializeConversationState,
  titleFromPrompt,
} from '../src/utils/agentConversationStorage.js'

describe('agent conversation storage', () => {
  test('keys conversations per user and titles from the first prompt', () => {
    assert.equal(agentConversationStorageKey('alice'), 'smartlab_agent_conversations:alice')
    assert.equal(titleFromPrompt('  先散热30秒然后判断温度  '), '先散热30秒然后判断温度')
    assert.match(titleFromPrompt('a'.repeat(40)), /…$/)
  })

  test('round-trips conversations and clips oversized log payloads', () => {
    const payload = 'x'.repeat(5000)
    const raw = serializeConversationState([{
      id: 'c-1',
      title: '加热',
      updatedAt: 1,
      resultState: 'running',
      messages: [{ id: 'm-1', role: 'user', text: '加热' }],
      logs: [{ round: 1, kind: 'tool_result', payload }],
    }], 'c-1')
    const loaded = parseConversationState(raw)
    assert.equal(loaded.activeId, 'c-1')
    assert.equal(loaded.conversations[0].resultState, 'idle')
    assert.ok((loaded.conversations[0].logs[0].payload || '').length < payload.length)
    assert.match(clipLogPayload(payload), /已截断/)
  })
})
