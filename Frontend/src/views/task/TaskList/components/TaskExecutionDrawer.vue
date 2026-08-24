<template>
  <el-drawer
    :model-value="modelValue"
    size="80%"
    append-to-body
    :with-header="false"
    class="task-execution-drawer unified-workflow-drawer"
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleDrawerOpening"
    @opened="handleDrawerOpened"
    @closed="handleDrawerClosed"
  >
    <div v-if="task" class="drawer-container" v-loading="loading">
      <header class="drawer-custom-head">
        <div class="head-top-row">
          <div class="head-title-wrap">
            <span class="inst-type-tag">任务</span>
            <h3 class="head-title-text">{{ task.taskName }}</h3>
            <button class="copy-id-btn" type="button" title="复制任务 ID" @click="copyTaskId">
              <span class="mono">ID: {{ task.id }}</span>
              <el-icon><CopyDocument /></el-icon>
            </button>
          </div>
          <div class="head-status-group">
            <span :class="['status-indicator', statusTone(task.taskStatus)]">
              <span class="dot"></span>{{ statusLabel(task.taskStatus) }}
            </span>
            <button class="close-drawer-btn" type="button" @click="emit('update:modelValue', false)">
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </div>
        <div class="drawer-metric-ribbon">
          <div class="m-col">
            <span class="m-lbl">关联流程</span>
            <span class="m-val highlight">{{ workflowModelName(workflow) || '-' }}</span>
          </div>
          <div class="m-col">
            <span class="m-lbl">执行步骤</span>
            <span class="m-val mono">{{ completedStepCount }} / {{ steps.length }}</span>
          </div>
          <div class="m-col">
            <span class="m-lbl">当前节点</span>
            <span class="m-val">{{ currentNodeDisplay }}</span>
          </div>
          <div class="m-col">
            <span class="m-lbl">开始时间</span>
            <span class="m-val mono">{{ formatLogDateTime(task.startTime) }}</span>
          </div>
        </div>
      </header>

      <div class="drawer-tabs-wrap">
        <el-tabs v-model="activeTab" class="execution-tabs">
          <el-tab-pane label="运行状态" name="runtime">
            <div class="graph-layout">
              <section class="graph-workspace">
                <div class="panel-heading">
                  <div>
                    <strong>执行拓扑</strong>
                    <span>{{ steps.length }} 个已创建步骤</span>
                  </div>
                </div>
                <TaskRuntimeGraph v-if="graphReady" class="graph-canvas" :workflow="workflow" :steps="steps" :selected-step-id="selectedStepId" @select-step="selectGraphStep" />
                <div v-else class="graph-loading"><el-skeleton :rows="5" animated /></div>
              </section>
              <aside class="graph-detail">
                <div class="panel-heading">
                  <div>
                    <strong>步骤详情</strong>
                    <span>{{ selectedStep ? (selectedStep.nodeName || `步骤 #${selectedStep.id}`) : '选择节点查看' }}</span>
                  </div>
                </div>
                <div class="graph-detail-scroll">
                  <TaskStepDetail v-if="selectedStep" :step="selectedStep" />
                  <div v-else-if="selectedGraphNode" class="waiting-detail">
                    <div>
                      <strong>{{ selectedGraphNode.name }}</strong>
                      <span>{{ nodeTypeLabel(selectedGraphNode) }}</span>
                    </div>
                    <span class="status-indicator pending"><span class="dot"></span>等待创建</span>
                    <p>该节点尚未创建执行步骤。</p>
                  </div>
                  <el-empty v-else description="选择流程节点查看详情" :image-size="44" />
                </div>
              </aside>
            </div>
          </el-tab-pane>

          <el-tab-pane label="业务事件" name="events">
            <div class="log-workbench">
              <div class="panel-heading">
                <div>
                  <strong>业务事件</strong>
                  <span>节点生命周期、接口收发与约束动作</span>
                </div>
                <small>{{ eventCount }} 条</small>
              </div>
              <div class="log-scroll">
                <TaskBusinessEventList :logs="logs" />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="绑定与约束" name="resources">
            <div class="resource-layout">
              <section class="holistic-section-card">
                <div class="section-card-head">
                  <div>
                    <strong>设备绑定</strong>
                    <span>任务启动时冻结的节点与实例对应关系</span>
                  </div>
                  <small>{{ bindings.length }} 项</small>
                </div>
                <div class="section-card-body">
                  <div v-for="binding in bindings" :key="binding.slotId || binding.nodeName" class="binding-row">
                    <div>
                      <strong>{{ binding.nodeName }}</strong>
                      <span>{{ binding.deviceModelName || binding.deviceModelId || '-' }}</span>
                    </div>
                    <span class="binding-to">绑定至</span>
                    <div>
                      <strong>{{ binding.instanceName || binding.deviceInstanceName || binding.deviceInstanceId || '待绑定' }}</strong>
                      <span>设备实例</span>
                    </div>
                  </div>
                  <div v-if="!bindings.length" class="empty-hint">该任务没有设备实例绑定</div>
                </div>
              </section>

              <section class="holistic-section-card">
                <div class="section-card-head">
                  <div>
                    <strong>约束</strong>
                    <span>全局级实时生效，任务级启动后固定</span>
                  </div>
                </div>
                <div class="section-card-body constraint-view">
                  <template v-if="constraints">
                    <div class="rule-group">
                      <h4>全局级约束</h4>
                      <div v-for="rule in constraints.globalConstraints || []" :key="rule.ruleId" class="rule-row">
                        <strong>{{ rule.ruleName }}</strong>
                        <code>{{ rule.expression }}</code>
                      </div>
                      <div v-if="!constraints.globalConstraints?.length" class="empty-hint">当前没有启用的全局级约束</div>
                    </div>
                    <div class="rule-group">
                      <h4>任务级约束</h4>
                      <div v-for="rule in constraints.taskConstraints || []" :key="rule.taskRuleIndex" class="rule-row">
                        <strong>{{ rule.ruleName }}</strong>
                        <code>{{ rule.expression }}</code>
                      </div>
                      <div v-if="!constraints.taskConstraints?.length" class="empty-hint">该任务未配置任务级约束</div>
                    </div>
                  </template>
                  <div v-else class="empty-hint">暂无有效约束模型</div>
                </div>
              </section>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <footer class="drawer-footer-bar">
        <div class="footer-left">
          <button v-if="task.taskStatus === 'RUNNING'" class="btn-link danger" type="button" @click="emit('terminate')">终止任务</button>
        </div>
        <div class="footer-right">
          <button class="btn-aliyun" type="button" :disabled="loading" @click="emit('refresh')">刷新</button>
          <button class="btn-aliyun" type="button" @click="emit('update:modelValue', false)">关闭</button>
        </div>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Close, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { workflowModelName } from '../../../../utils/workflowAuthoring.js'
import { formatLogDateTime } from '../../../../utils/formatLogTime.js'
import { businessExecutionEvents, workflowNodeNameByIdRef } from '../../../../utils/workflowExecution.js'
import TaskBusinessEventList from './TaskBusinessEventList.vue'
import TaskRuntimeGraph from './TaskRuntimeGraph.vue'
import TaskStepDetail from './TaskStepDetail.vue'
import { selectPreferredStepId, statusLabel } from '../taskExecutionPresentation.js'

type Item = Record<string, any>

const props = withDefaults(defineProps<{
  modelValue: boolean
  task?: Item | null
  workflow?: Item | null
  steps?: Item[]
  logs?: Item[]
  bindings?: Item[]
  constraints?: Item | null
  loading?: boolean
}>(), {
  task: null,
  workflow: null,
  steps: () => [],
  logs: () => [],
  bindings: () => [],
  constraints: null,
  loading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
  terminate: []
  'select-step': [stepId: number | null]
}>()

const activeTab = ref('runtime')
const graphReady = ref(false)
const selectedStepId = ref<number | null>(null)
const selectedGraphNode = ref<Item | null>(null)
const selectedStep = computed(() => {
  const step = props.steps.find(item => item.id === selectedStepId.value) || null
  if (!step) return null
  if (step.nodeName) return step
  const fromGraph = selectedGraphNode.value?.name
  const fromWorkflow = workflowNodeNameByIdRef(props.workflow, step.nodeIdRef)
  const nodeName = fromGraph || fromWorkflow
  return nodeName ? { ...step, nodeName } : step
})
const completedStepCount = computed(() => props.steps.filter(step => step.nodeStatus === 'SUCCEEDED').length)
const eventCount = computed(() => businessExecutionEvents(props.logs).length)
const currentNodeDisplay = computed(() => {
  if (selectedStep.value?.nodeName) return selectedStep.value.nodeName
  const refId = props.task?.currentNodeIdRef
  if (refId == null) return '-'
  return workflowNodeNameByIdRef(props.workflow, refId) || `#${refId}`
})

watch(() => props.task?.id, () => {
  activeTab.value = 'runtime'
  selectedGraphNode.value = null
  selectedStepId.value = selectPreferredStepId(props.steps, null)
}, { immediate: true })

watch(() => props.steps, current => {
  if (selectedGraphNode.value && selectedStepId.value == null) return
  selectedStepId.value = selectPreferredStepId(current, selectedStepId.value)
}, { immediate: true })

function handleDrawerOpening() {
  graphReady.value = false
}

function handleDrawerOpened() {
  graphReady.value = true
}

function handleDrawerClosed() {
  graphReady.value = false
}

function selectGraphStep(stepId: number | null, node: Item) {
  selectedGraphNode.value = node
  const resolved = resolveStepId(stepId, node)
  selectedStepId.value = resolved
  emit('select-step', resolved)
}

function resolveStepId(stepId: number | null, node: Item | null) {
  if (stepId != null && props.steps.some(step => step.id === stepId)) return stepId
  if (node?.stepId != null && props.steps.some(step => step.id === node.stepId)) return node.stepId
  const name = node?.name
  const idRef = node?.nodeIdRef ?? node?.idRef
  const matched = props.steps.find(step => {
    if (name && (step.nodeName === name || step.flowNodeName === name)) return true
    return idRef != null && step.nodeIdRef != null && String(step.nodeIdRef) === String(idRef)
  })
  return matched?.id ?? null
}

function nodeTypeLabel(node: Item) {
  if (node.nodeType === 'DEV_NODE') return '设备能力节点'
  if (node.nodeType === 'SUBFLOW_NODE') return '子流程节点'
  return '功能节点'
}

function statusTone(status: unknown) {
  const value = String(status || '').toLowerCase()
  if (value === 'running') return 'running'
  if (value === 'succeeded') return 'success'
  if (value === 'failed') return 'danger'
  if (value === 'terminating' || value === 'terminated') return 'warning'
  return 'pending'
}

function copyTaskId() {
  const id = props.task?.id
  if (id == null) return
  navigator.clipboard.writeText(String(id)).then(
    () => ElMessage.success('已复制任务 ID'),
    () => ElMessage.error('复制失败')
  )
}
</script>

<style scoped>
.task-execution-drawer :deep(.el-drawer__body) {
  padding: 0 !important;
  overflow: hidden;
  background: var(--sl-bg-page, #f1f5f9);
}
.drawer-container { display: flex; flex-direction: column; height: 100%; overflow: hidden; background: var(--sl-bg-page, #f1f5f9); }
.drawer-custom-head { background: #ffffff; border-bottom: 1px solid var(--sl-border-base, #e2e8f0); flex-shrink: 0; }
.head-top-row {
  padding: 10px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  gap: 12px;
}
.head-title-wrap { display: flex; align-items: center; gap: 8px; min-width: 0; }
.inst-type-tag {
  background: var(--sl-primary-light, #eff6ff); color: var(--sl-primary, #2563eb);
  border: 1px solid var(--sl-primary-border, #bfdbfe); padding: 1px 6px;
  font-size: 11px; font-weight: 700; border-radius: var(--sl-radius-sm, 6px); flex-shrink: 0;
}
.head-title-text {
  margin: 0; font-size: 15px; font-weight: 700; color: var(--sl-text-heading, #0f172a);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.copy-id-btn {
  background: transparent; border: none; cursor: pointer; color: var(--sl-text-secondary, #64748b);
  display: inline-flex; align-items: center; gap: 4px; padding: 2px 4px; font-size: 11px; border-radius: 4px;
}
.copy-id-btn:hover { background: #f1f5f9; color: var(--sl-primary, #2563eb); }
.head-status-group { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.close-drawer-btn {
  background: transparent; border: none; cursor: pointer; padding: 4px;
  color: var(--sl-text-secondary, #64748b); border-radius: 4px; display: inline-flex; font-size: 16px;
}
.close-drawer-btn:hover { background: #f1f5f9; color: var(--sl-text-heading, #0f172a); }
.drawer-metric-ribbon {
  height: 35px; padding: 0 16px; display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 16px; align-items: center; background: #fafbfc;
}
.m-col { display: flex; align-items: baseline; gap: 6px; min-width: 0; }
.m-lbl { font-size: 11px; color: var(--sl-text-secondary, #64748b); flex-shrink: 0; }
.m-val { font-size: 12.5px; font-weight: 600; color: var(--sl-text-heading, #0f172a); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-val.highlight { color: var(--sl-primary, #2563eb); }
.m-val.mono, .mono { font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace); }
.status-indicator { display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; font-weight: 600; }
.status-indicator .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--sl-text-disabled, #94a3b8); }
.status-indicator.pending { color: var(--sl-text-secondary, #64748b); }
.status-indicator.running { color: var(--sl-primary, #2563eb); }
.status-indicator.running .dot { background: var(--sl-primary, #2563eb); box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2); }
.status-indicator.success { color: var(--sl-success, #16a34a); }
.status-indicator.success .dot { background: var(--sl-success, #16a34a); }
.status-indicator.danger { color: var(--sl-danger, #dc2626); }
.status-indicator.danger .dot { background: var(--sl-danger, #dc2626); }
.status-indicator.warning { color: var(--sl-warning, #d97706); }
.status-indicator.warning .dot { background: var(--sl-warning, #d97706); }

.drawer-tabs-wrap { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: #ffffff; }
.execution-tabs { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.execution-tabs :deep(.el-tabs__header) {
  margin: 0; padding: 0 16px; background: #ffffff; border-bottom: 1px solid var(--sl-border-base, #e2e8f0); flex-shrink: 0;
}
.execution-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.execution-tabs :deep(.el-tabs__item) { height: 36px; color: var(--sl-text-secondary, #64748b); font-size: 12.5px; }
.execution-tabs :deep(.el-tabs__item.is-active) { color: var(--sl-primary, #2563eb); font-weight: 600; }
.execution-tabs :deep(.el-tabs__content) {
  flex: 1; min-height: 0; overflow: hidden; padding: 0; display: flex; flex-direction: column;
}
.execution-tabs :deep(.el-tab-pane) { height: 100%; min-height: 0; }

.graph-layout {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  overflow: hidden;
  background: var(--sl-bg-surface, #ffffff);
}
.graph-workspace { min-width: 0; min-height: 0; display: flex; flex-direction: column; border-right: 1px solid var(--sl-border-base, #e2e8f0); }
.panel-heading {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #f8fafc;
  flex-shrink: 0;
}
.panel-heading > div { min-width: 0; display: grid; gap: 1px; }
.panel-heading strong { color: var(--sl-text-heading, #0f172a); font-size: 13px; font-weight: 600; }
.panel-heading span, .panel-heading small { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.graph-canvas { flex: 1; min-height: 0; border: 0; }
.graph-loading { flex: 1; min-height: 0; padding: 24px; box-sizing: border-box; background: var(--sl-bg-hover, #f8fafc); }
.graph-workspace :deep(.runtime-graph) { height: 100%; min-height: 0; border: 0; }
.graph-detail { min-width: 0; min-height: 0; display: flex; flex-direction: column; background: var(--sl-bg-surface, #ffffff); }
.graph-detail-scroll { flex: 1; min-height: 0; overflow: auto; padding: 12px; }
.waiting-detail {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: start;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: var(--sl-bg-hover, #f8fafc);
}
.waiting-detail > div { display: grid; gap: 3px; }
.waiting-detail strong { font-size: 12.5px; color: var(--sl-text-heading, #0f172a); }
.waiting-detail span, .waiting-detail p { color: var(--sl-text-secondary, #64748b); font-size: 12px; }
.waiting-detail p { grid-column: 1 / -1; margin: 0; }

.log-workbench { height: 100%; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.log-scroll { flex: 1; min-height: 0; overflow: auto; }

.resource-layout {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding: 12px 16px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  align-content: start;
  background: var(--sl-bg-page, #f1f5f9);
}
.holistic-section-card {
  min-width: 0;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.section-card-head {
  min-height: 40px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}
.section-card-head > div { min-width: 0; display: grid; gap: 2px; }
.section-card-head strong { font-size: 13.5px; font-weight: 700; color: var(--sl-text-heading, #0f172a); }
.section-card-head span, .section-card-head small { font-size: 12px; color: var(--sl-text-secondary, #64748b); }
.section-card-body { padding: 10px 12px; display: grid; gap: 8px; }
.binding-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: var(--sl-bg-hover, #f8fafc);
}
.binding-row > div { display: grid; gap: 2px; min-width: 0; }
.binding-row strong { font-size: 12.5px; color: var(--sl-text-heading, #0f172a); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.binding-row span { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.binding-to { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.empty-hint {
  padding: 20px 4px;
  color: var(--sl-text-disabled, #94a3b8);
  font-size: 12.5px;
  line-height: 20px;
}
.constraint-view { gap: 16px; }
.rule-group { display: grid; gap: 8px; }
.rule-group h4 { margin: 0; font-size: 12px; font-weight: 600; color: var(--sl-text-heading, #0f172a); }
.rule-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: var(--sl-bg-hover, #f8fafc);
}
.rule-row strong { font-size: 12px; color: var(--sl-text-heading, #0f172a); }
.rule-row code {
  color: var(--sl-text-body, #334155);
  font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.drawer-footer-bar {
  padding: 10px 16px; background: #ffffff; border-top: 1px solid var(--sl-border-base, #e2e8f0);
  display: flex; align-items: center; justify-content: space-between; flex-shrink: 0;
}
.footer-right { display: flex; align-items: center; gap: 8px; }
.btn-aliyun {
  height: 30px; padding: 0 12px; background: #ffffff;
  border: 1px solid var(--sl-border-input, #cbd5e1); border-radius: var(--sl-radius-sm, 6px);
  color: var(--sl-text-heading, #0f172a); font-size: 12.5px; font-weight: 500; cursor: pointer;
}
.btn-aliyun:hover:not(:disabled) { border-color: var(--sl-primary, #2563eb); color: var(--sl-primary, #2563eb); }
.btn-aliyun:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-link {
  border: none; background: transparent; color: var(--sl-primary, #2563eb);
  font-size: 12.5px; font-weight: 500; cursor: pointer; padding: 2px 4px;
}
.btn-link.danger { color: var(--sl-danger, #dc2626); }
.btn-link:hover { text-decoration: underline; }

@media (max-width: 1100px) {
  .graph-layout { grid-template-columns: minmax(0, 1fr) 300px; }
  .resource-layout { grid-template-columns: 1fr; }
}
@media (max-width: 900px) {
  .graph-layout { grid-template-columns: 1fr; }
  .graph-workspace { border-right: 0; border-bottom: 1px solid var(--sl-border-base, #e2e8f0); min-height: 320px; }
  .graph-detail-scroll { max-height: 360px; }
  .drawer-metric-ribbon { grid-template-columns: repeat(2, 1fr); height: auto; padding: 8px 16px; }
}
</style>

<style>
.task-execution-drawer .el-drawer__body {
  overflow: hidden !important;
  background: var(--sl-bg-page);
}
</style>
