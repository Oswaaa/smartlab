import assert from 'node:assert'
import { describe, test } from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const srcRoot = path.resolve(__dirname, '..', 'src')

function readSource(relativePath) {
  return readFileSync(path.join(srcRoot, relativePath), 'utf-8')
}

describe('Task 9 — Full-Chain Copy Regression', () => {
  const ordinaryUserSources = [
    readSource('views/task/TaskList/TaskList.vue'),
    readSource('views/task/WorkflowDesigner/WorkflowDesigner.vue'),
    readSource('views/task/TaskList/components/TaskResourceBindingCanvas.vue'),
    readSource('views/task/TaskList/components/TaskPreflightPanel.vue'),
    readSource('views/task/TaskList/components/TaskExecutionDrawer.vue'),
    readSource('views/task/WorkflowDesigner/components/WorkflowNodeInspector.vue'),
    readSource('views/task/WorkflowDesigner/components/inspector/WorkflowControlInterfacesPanel.vue'),
    readSource('views/task/WorkflowDesigner/components/inspector/WorkflowTriggerEditor.vue'),
  ]

  test('ordinary workflow and task UI hides internal transport vocabulary', () => {
    for (const source of ordinaryUserSources) {
      // Only check template sections for user-facing copy
      const templateSection = source.split('</template>')[0] || source
      assert.doesNotMatch(templateSection, /resourceMap/)
      assert.doesNotMatch(templateSection, /bindingKey/)
      assert.doesNotMatch(templateSection, /NODE_TO_DEVICE|DEVICE_TO_NODE/)
      assert.doesNotMatch(templateSection, /HTTP接口/)
      assert.doesNotMatch(templateSection, /消息主题/)
    }
  })

  test('preflight panel shows ready/blocked states and check results', () => {
    const source = readSource('views/task/TaskList/components/TaskPreflightPanel.vue')
    assert.match(source, /ready|preflight-result/)
    assert.match(source, /checks|preflight-checks/)
  })

  test('execution view maps node status without exposing transport identifiers', () => {
    const source = readSource('views/task/TaskList/components/TaskExecutionDrawer.vue')
    assert.match(source, /taskName|taskStatus/)
    const templateSection = source.split('</template>')[0] || source
    assert.doesNotMatch(templateSection, /messageId|resourceMap|bindingKey/)
  })

  test('task list delegates create flow without losing binding and constraints', () => {
    const source = readSource('views/task/TaskList/TaskList.vue')
    const drawer = readSource('views/task/TaskList/components/TaskCreateDrawer.vue')
    const bindingCanvas = readSource('views/task/TaskList/components/TaskResourceBindingCanvas.vue')
    assert.match(source, /TaskCreateDrawer/)
    assert.match(drawer, /TaskPreflightPanel/)
    assert.match(drawer, /TaskResourceBindingCanvas/)
    assert.match(drawer, /TaskConstraintPanel/)
    assert.match(drawer, /发布启用|已发布|可执行流程/)
    assert.match(bindingCanvas, /WorkflowStageGraph/)
    assert.match(bindingCanvas, /selectedRequirement\.slotId/)
    const stageGraph = readSource('views/task/components/WorkflowStageGraph.vue')
    assert.match(stageGraph, /WorkflowCanvasEdge/)
    assert.match(stageGraph, /useVueFlow/)
    assert.match(stageGraph, /#edge-workflow/)
    assert.doesNotMatch(stageGraph, /hide-tooltips/)
    assert.doesNotMatch(stageGraph, /:id="flowId"/)
    assert.doesNotMatch(stageGraph, /v-if="viewportReady"/)
    assert.doesNotMatch(stageGraph, /smoothstep/)
  })

  test('execution drawer exposes the confirmed runtime views', () => {
    const source = readSource('views/task/TaskList/components/TaskExecutionDrawer.vue')
    for (const label of ['运行状态', '任务日志', '设备绑定', '约束实例']) {
      assert.match(source, new RegExp(label))
    }
    assert.match(source, /设备绑定/)
    assert.match(source, /全局级|任务级/)
    assert.match(source, /约束实例/)
    assert.doesNotMatch(source.split('</template>')[0], /按节点区分生命周期、接口收发与约束动作/)
  })

  test('execution drawer and logs share subflow layer navigation', () => {
    const drawer = readSource('views/task/TaskList/components/TaskExecutionDrawer.vue')
    const logs = readSource('views/task/TaskList/components/TaskBusinessEventList.vue')
    const detail = readSource('views/task/TaskList/components/TaskStepDetail.vue')
    assert.match(drawer, /flow-breadcrumb/)
    assert.match(drawer, /enterSubflow/)
    assert.match(drawer, /parent-step-id/)
    assert.match(logs, /查看子流程日志/)
    assert.match(detail, /查看子流程运行图/)
    assert.match(readSource('views/task/TaskList/TaskList.vue'), /:groups="activeWorkflowGroups"/)
  })

  test('snapshot panel uses canonical fields and no routing envelope', () => {
    const source = readSource('views/task/TaskList/components/InterfaceSnapshotPanel.vue')
    assert.match(source, /interfaceName/)
    assert.match(source, /signalName/)
    assert.match(source, /payload/)
    assert.match(source, /bindingTriggers|绑定触发器/)
    assert.match(source, /当前信号/)
    assert.match(source, /触发器绑定列表/)
    assert.match(source, /触发条件/)
    assert.match(source, /触发动作/)
    assert.match(source, /action-verb/)
    assert.doesNotMatch(source, /条件摘要会展开/)
    assert.doesNotMatch(source, /messageId|capabilityRef|sourceNodeIdRef|inputSignalName/)
  })

  test('runtime graph source uses Vue Flow and emits selected step', () => {
    const source = readSource('views/task/TaskList/components/TaskRuntimeGraph.vue')
    assert.match(source, /WorkflowStageGraph/)
    assert.match(source, /select-step/)
    assert.match(source, /WAITING/)
  })

  test('task runtime refreshes the opened running task over the live stream', () => {
    const source = readSource('views/task/TaskList/TaskList.vue')
    assert.match(source, /EventSource/)
    assert.match(source, /monitorDrawerVisible/)
    assert.match(source, /RUNNING/)
    assert.match(source, /SUCCEEDED|FAILED|TERMINATED/)
  })

  test('ordinary task UI does not expose engine scheduling vocabulary', () => {
    const source = readSource('views/task/TaskList/components/TaskExecutionDrawer.vue')
    const template = source.split('</template>')[0]
    assert.doesNotMatch(template, /轮询|线程池|调度器|队列领取|心跳|锁续期/)
  })
})
