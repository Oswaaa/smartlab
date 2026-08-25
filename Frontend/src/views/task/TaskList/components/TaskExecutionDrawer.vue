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
            <div class="drawer-metric-ribbon">
              <div class="m-col">
                <span class="m-lbl">关联流程</span>
                <span class="m-val highlight">{{ workflowModelDisplayName(workflow) || '-' }}</span>
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
      </header>

      <div class="drawer-tabs-wrap">
        <el-tabs v-model="activeTab" class="execution-tabs">
          <el-tab-pane label="运行状态" name="runtime">
            <div class="graph-layout">
              <section class="graph-workspace">
                <TaskRuntimeGraph
                  v-if="graphReady"
                  class="graph-canvas"
                  :workflow="workflow"
                  :steps="steps"
                  :models="models"
                  :bindings="bindings"
                  :selected-step-id="selectedStepId"
                  :active-node-name="selectedGraphNode?.name"
                  @select-step="selectGraphStep"
                />
                <div v-else class="graph-loading"><el-skeleton :rows="5" animated /></div>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="业务事件" name="events">
            <div class="log-workbench">
              <div class="panel-heading">
                <div>
                  <strong>业务事件</strong>
                  <span>按节点区分生命周期、接口收发与约束动作</span>
                </div>
                <small>{{ eventCount }} 条</small>
              </div>
              <div class="log-scroll">
                <TaskBusinessEventList :logs="logs" :steps="steps" :workflow="workflow" />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="设备绑定" name="bindings">
            <div class="table-tab">
              <el-table :data="bindings" size="small" stripe border class="resource-table" empty-text="该任务没有设备实例绑定">
                <el-table-column label="流程节点" min-width="160">
                  <template #default="{ row }">
                    <button class="btn-link" type="button" @click="jumpToRuntimeNode(row)">{{ row.nodeName || '-' }}</button>
                  </template>
                </el-table-column>
                <el-table-column label="设备模型" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.deviceModelName || row.deviceModelId || '-' }}</template>
                </el-table-column>
                <el-table-column label="绑定设备" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.instanceName || row.deviceInstanceName || row.deviceInstanceId || '待绑定' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="280" fixed="right">
                  <template #default="{ row }">
                    <button class="btn-link" type="button" :disabled="!row.deviceInstanceId" @click="openDeviceInstance(row)">设备实例</button>
                    <router-link
                      v-if="row.deviceInstanceId"
                      class="datacenter-link"
                      :to="`/data-management?instanceId=${row.deviceInstanceId}`"
                    >前往数据中心查看历史数据 ➔</router-link>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="约束实例" name="constraints">
            <div class="table-tab">
              <el-table :data="constraintRows" size="small" stripe border class="resource-table" empty-text="暂无有效约束模型">
                <el-table-column label="来源" width="90">
                  <template #default="{ row }">{{ row.scope }}</template>
                </el-table-column>
                <el-table-column prop="ruleName" label="规则名称" min-width="140" show-overflow-tooltip />
                <el-table-column label="业务判定逻辑" min-width="440">
                  <template #default="{ row }">
                    <div class="sentence-stream">
                      <span class="sentence-static">当</span>
                      <span class="sentence-chip emphasis">{{ sentenceTokens(row).conditionExpr || row.expression || '-' }}</span>
                      <span class="sentence-static">持续</span>
                      <span class="sentence-chip">{{ sentenceTokens(row).windowText }}</span>
                      <span class="sentence-arrow">➔</span>
                      <span class="sentence-static">执行</span>
                      <span v-for="(act, idx) in sentenceTokens(row).actionLabels" :key="idx" class="sentence-chip action">
                        {{ act }}
                      </span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="判定窗口" width="95">
                  <template #default="{ row }">
                    <span>{{ row.windowSeconds && row.windowSeconds > 0 ? row.windowSeconds + ' 秒' : '瞬时' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }">
                    <router-link v-if="row.ruleId" class="btn-link" :to="`/constraint-management?ruleId=${row.ruleId}`">查看详情</router-link>
                    <span v-else class="muted-action">任务内规则</span>
                  </template>
                </el-table-column>
              </el-table>
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

    <el-drawer
      v-model="stepDrawerVisible"
      append-to-body
      size="480px"
      class="task-step-detail-drawer"
      :title="stepDrawerTitle"
    >
      <TaskStepDetail :step="selectedStep" :node="stepDetailNode" />
    </el-drawer>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Close, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { workflowModelDisplayName } from '../../../../utils/workflowAuthoring.js'
import { formatLogDateTime } from '../../../../utils/formatLogTime.js'
import { businessExecutionEvents, workflowNodeNameByIdRef } from '../../../../utils/workflowExecution.js'
import { formatRuleSentenceTokens } from '../../../../utils/constraintExpression.js'
import TaskBusinessEventList from './TaskBusinessEventList.vue'
import TaskRuntimeGraph from './TaskRuntimeGraph.vue'
import TaskStepDetail from './TaskStepDetail.vue'
import { statusLabel } from '../taskExecutionPresentation.js'

type Item = Record<string, any>

const props = withDefaults(defineProps<{
  modelValue: boolean
  task?: Item | null
  workflow?: Item | null
  steps?: Item[]
  logs?: Item[]
  bindings?: Item[]
  constraints?: Item | null
  models?: Item[]
  instances?: Item[]
  loading?: boolean
}>(), {
  task: null,
  workflow: null,
  steps: () => [],
  logs: () => [],
  bindings: () => [],
  constraints: null,
  models: () => [],
  instances: () => [],
  loading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
  terminate: []
  'select-step': [stepId: number | null]
}>()

const router = useRouter()
const activeTab = ref('runtime')
const graphReady = ref(false)
const stepDrawerVisible = ref(false)
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
const stepDetailNode = computed(() => {
  if (selectedGraphNode.value) return selectedGraphNode.value
  const name = selectedStep.value?.nodeName
  return (props.workflow?.nodesDef || props.workflow?.nodes || []).find((item: Item) => item.name === name) || null
})
const stepDrawerTitle = computed(() => selectedGraphNode.value?.name || selectedStep.value?.nodeName || '步骤详情')
const completedStepCount = computed(() => props.steps.filter(step => step.nodeStatus === 'SUCCEEDED').length)
const eventCount = computed(() => businessExecutionEvents(props.logs).length)
const currentNodeDisplay = computed(() => {
  const refId = props.task?.currentNodeIdRef
  if (refId == null) return '-'
  return workflowNodeNameByIdRef(props.workflow, refId) || `#${refId}`
})
const constraintRows = computed(() => {
  const globalRows = (props.constraints?.globalConstraints || []).map((rule: Item) => ({
    ...rule,
    scope: '全局级',
  }))
  const taskRows = (props.constraints?.taskConstraints || []).map((rule: Item) => ({
    ...rule,
    scope: '任务级',
  }))
  return [...globalRows, ...taskRows]
})

function sentenceTokens(row: Item) {
  return formatRuleSentenceTokens(row, props.models, props.instances)
}

watch(() => props.task?.id, () => {
  activeTab.value = 'runtime'
  selectedGraphNode.value = null
  selectedStepId.value = null
  stepDrawerVisible.value = false
}, { immediate: true })

function handleDrawerOpening() {
  graphReady.value = false
}

function handleDrawerOpened() {
  graphReady.value = true
}

function handleDrawerClosed() {
  graphReady.value = false
  stepDrawerVisible.value = false
}

function selectGraphStep(stepId: number | null, node: Item) {
  selectedGraphNode.value = node
  const resolved = resolveStepId(stepId, node)
  selectedStepId.value = resolved
  stepDrawerVisible.value = true
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

function jumpToRuntimeNode(binding: Item) {
  activeTab.value = 'runtime'
  const nodes = props.workflow?.nodesDef || props.workflow?.nodes || []
  const node = nodes.find((item: Item) => item.name === binding.nodeName)
    || { name: binding.nodeName, nodeType: 'DEV_NODE', nodeIdRef: binding.nodeIdRef }
  selectGraphStep(null, node)
}

function openDeviceInstance(binding: Item) {
  if (!binding.deviceInstanceId) return
  router.push({ path: '/device-instance-management', query: { instanceId: String(binding.deviceInstanceId) } })
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
.head-title-wrap { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; }
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
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: baseline;
  gap: 16px;
  margin-left: 8px;
  overflow: hidden;
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
  display: flex;
  overflow: hidden;
  background: var(--sl-bg-surface, #ffffff);
}
.graph-workspace { flex: 1; min-width: 0; min-height: 0; display: flex; flex-direction: column; }
.panel-heading {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  flex-shrink: 0;
}
.panel-heading > div { min-width: 0; display: grid; gap: 1px; }
.panel-heading strong { color: var(--sl-text-heading, #0f172a); font-size: 13px; font-weight: 600; }
.panel-heading span, .panel-heading small { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.graph-canvas { flex: 1; min-height: 0; border: 0; }
.graph-loading { flex: 1; min-height: 0; padding: 24px; box-sizing: border-box; background: var(--sl-bg-hover, #f8fafc); }
.graph-workspace :deep(.runtime-graph) { height: 100%; min-height: 0; border: 0; }

.log-workbench { height: 100%; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.log-scroll { flex: 1; min-height: 0; overflow: auto; }

.table-tab {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding: 0;
  background: #ffffff;
  box-sizing: border-box;
}
.resource-table { width: 100%; }
.sentence-stream {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  line-height: 1.6;
}
.sentence-static { color: var(--sl-text-secondary, #64748b); }
.sentence-chip {
  padding: 1px 6px;
  border-radius: var(--sl-radius-sm, 6px);
  background: var(--sl-bg-page, #f1f5f9);
  border: 1px solid var(--sl-border-input, #cbd5e1);
  color: var(--sl-text-body, #334155);
  font-size: 12px;
  font-weight: 500;
}
.sentence-chip.emphasis { font-weight: 600; }
.sentence-chip.action {
  background: var(--sl-danger-light, #fef2f2);
  border-color: var(--sl-danger-border, #fecaca);
  color: var(--sl-danger, #dc2626);
}
.sentence-arrow { color: var(--sl-text-disabled, #94a3b8); margin: 0 2px; }
.datacenter-link {
  margin-left: 8px;
  color: var(--sl-primary, #2563eb);
  font-size: 12px;
  text-decoration: none;
}
.datacenter-link:hover { text-decoration: underline; }
.muted-action { color: var(--sl-text-disabled, #94a3b8); font-size: 12px; }

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
.btn-link:hover:not(:disabled) { text-decoration: underline; }
.btn-link:disabled { color: var(--sl-text-disabled, #94a3b8); cursor: not-allowed; }

@media (max-width: 1100px) {
  .head-top-row { flex-wrap: wrap; }
  .drawer-metric-ribbon { flex-wrap: wrap; margin-left: 0; }
}
</style>

<style>
.task-execution-drawer .el-drawer__body {
  overflow: hidden !important;
  background: var(--sl-bg-page);
}
.task-step-detail-drawer .el-drawer__body {
  padding: 0;
  background: #fff;
}
</style>
