import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createServer } from 'vite'

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
const auth = await server.ssrLoadModule('/src/stores/authStore.ts')

after(() => server.close())

test('empty error responses do not crash route profile refresh', async () => {
  const response = new Response('', { status: 500 })
  assert.equal(await auth.readJsonResponse(response), null)
})

test('valid JSON responses are parsed normally', async () => {
  const response = new Response(JSON.stringify({ success: true }), {
    status: 200,
    headers: { 'content-type': 'application/json' }
  })
  assert.deepEqual(await auth.readJsonResponse(response), { success: true })
})
