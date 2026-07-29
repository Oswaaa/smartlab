import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createServer } from 'vite'

const virtualId = '\0final-review-device-metadata-cache'
const metadataFixture = {
  name: 'final-review-device-metadata-cache',
  enforce: 'pre',
  resolveId(source, importer) {
    if (source === 'virtual:final-review-device-metadata-cache') return virtualId
    return importer?.includes('deviceModelConstants.js') && source.includes('frontendContractMetadata.js')
      ? virtualId
      : undefined
  },
  load(id) {
    if (id !== virtualId) return undefined
    return `
      let cached
      let calls = 0
      const complete = {
        protocol: { communicationProtocols: ['MQTT'] },
        deviceCapability: { dataTypes: ['STRING'] },
        stateMachine: {
          standardInterfaces: [{ name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'] }],
          systemTransitions: [{ fromStateName: 'IDLE', toStateName: 'SENT' }],
          deviceCommandTransitionRequirements: [{ fromStateName: 'SENT', toStateName: 'RUNNING' }],
        },
      }
      export function invalidateFrontendContractMetadata() { cached = undefined }
      export function loadFrontendContractMetadata() {
        if (!cached) {
          calls += 1
          cached = Promise.resolve(calls === 1 ? {} : complete)
        }
        return cached
      }
      export function requestCount() { return calls }
    `
  },
}

const server = await createServer({ plugins: [metadataFixture], server: { middlewareMode: true }, appType: 'custom' })
const constants = await server.ssrLoadModule('/src/views/device/components/deviceModel/deviceModelConstants.js')
const fixture = await server.ssrLoadModule('virtual:final-review-device-metadata-cache')

after(() => server.close())

test('incomplete successful metadata response invalidates shared cache and retries', async () => {
  await assert.rejects(constants.ensureProtocolMetadataLoaded(), /设备模型规范元数据不完整/)
  await assert.doesNotReject(constants.ensureProtocolMetadataLoaded())
  assert.equal(fixture.requestCount(), 2)
})
