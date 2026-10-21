import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'vite'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import * as ElementPlus from 'element-plus'

let server
let Detail
before(async () => {
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  Detail = (await server.ssrLoadModule('/src/views/device/components/deviceModel/DeviceModelDetail.vue')).default
})
after(async () => { await server?.close() })

async function render(capabilities) {
  const app = createSSRApp(Detail, {
    model: { modelName: '测试模型', capabilities },
    modelBundle: {}, defaultTemplateAttributes: [], categories: []
  })
  for (const name of ['ElAnchor', 'ElAnchorLink', 'ElScrollbar', 'ElDescriptions', 'ElDescriptionsItem', 'ElTableColumn', 'ElTooltip', 'ElPopconfirm', 'ElIcon']) {
    app.component(name, ElementPlus[name])
  }
  app.provide(ElementPlus.ID_INJECTION_KEY, { prefix: 0, current: 0 })
  // Element Plus tables populate rows on mount. Exercise the real detail cell
  // slots on the server without depending on browser layout or table mounting.
  app.component('ElTable', {
    props: ['data'],
    setup(props, { slots }) {
      return () => h('div', props.data.flatMap(row =>
        (slots.default?.() || []).map(column => column.children?.default?.({ row }))
      ))
    }
  })
  return renderToString(app)
}

test('detail renders abort targets and affected operation names', async () => {
  const html = await render([
    { _key: 'move-key', capabilityName: 'move', displayName: '移动', abortCapabilityKey: 'stop-key' },
    { _key: 'stop-key', capabilityName: 'stop', displayName: '停止移动', isAbort: true, scopeCapabilityKeys: ['move-key'] }
  ])
  assert.match(html, /指定终止操作:[\s\S]*停止移动/)
  assert.match(html, /受影响操作:[\s\S]*移动/)
})

test('detail resolves operation names and preserves unknown references', async () => {
  const html = await render([
    { capabilityName: 'move', abortCapabilityName: 'stop' },
    { capabilityName: 'stop', displayName: '停止移动' },
    { capabilityName: 'other', abortCapabilityName: 'unknown-stop' }
  ])
  assert.match(html, /指定终止操作:[\s\S]*停止移动/)
  assert.match(html, /unknown-stop/)
})
