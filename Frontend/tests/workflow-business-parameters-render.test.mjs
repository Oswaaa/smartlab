import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import ElementPlus, { ID_INJECTION_KEY, ZINDEX_INJECTION_KEY } from 'element-plus'

let server
let Panel
before(async () => {
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  Panel = (await server.ssrLoadModule('/src/views/task/WorkflowDesigner/components/inspector/WorkflowBusinessPanel.vue')).default
})
after(async () => { await server?.close() })

function capability(required = true) {
  return {
    capabilityName: 'move', displayName: '上下移动到目标位置',
    parameters: [
      { name: 'parameter_1', displayName: '目标轴', dataType: 'INTEGER', required: true },
      { name: 'parameter_2', displayName: '目标脉冲位置', dataType: 'INTEGER', required },
    ],
    parameterMapping: [
      { commandParamName: 'axis', isFixedValue: true, fixedValue: 1 },
      { commandParamName: 'position', isFixedValue: false, capabilityParamName: 'parameter_2' },
    ],
  }
}

async function render(definition) {
  const app = createSSRApp(Panel, {
    node: { nodeType: 'DEV_NODE', capability: { capabilityName: 'move', capabilityParameters: {} } },
    deviceCapabilities: [definition], contractReady: true,
  })
  app.use(ElementPlus)
  app.provide(ID_INJECTION_KEY, { prefix: 0, current: 0 })
  app.provide(ZINDEX_INJECTION_KEY, { current: 0 })
  return renderToString(app)
}

test('流程配置隐藏固定映射参数并同步参数数量', async () => {
  const html = await render(capability())
  assert.doesNotMatch(html, /目标轴/)
  assert.match(html, /目标脉冲位置/)
  assert.match(html, /1 项参数/)
  assert.match(html, /留空则创建任务时填写/)
})

test('可选的外部参数仍显示，全部固定时显示无需配置', async () => {
  assert.match(await render(capability(false)), /可以不写/)
  const definition = capability()
  definition.parameterMapping[1] = { commandParamName: 'position', isFixedValue: true, fixedValue: 100 }
  const html = await render(definition)
  assert.match(html, /0 项参数/)
  assert.match(html, /该能力无需配置参数/)
  assert.doesNotMatch(html, /目标轴|目标脉冲位置/)
})
