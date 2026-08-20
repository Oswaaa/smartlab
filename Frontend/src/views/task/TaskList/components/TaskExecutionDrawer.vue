<template>
  <el-drawer
    :model-value="modelValue"
    size="80%"
    append-to-body
    class="task-execution-drawer unified-workflow-drawer"
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleDrawerOpening"
    @opened="handleDrawerOpened"
    @closed="handleDrawerClosed"
  >
    <template #header="{ titleId, titleClass }">
      <div v-if="task" class="drawer-heading">
        <div class="drawer-identity">
          <div class="drawer-title-row">
            <h2 :id="titleId" :class="titleClass">{{ task.taskName }}</h2>
            <el-tag :type="statusType(task.taskStatus)" effect="plain" round class="status-tag">
              {{ statusLabel(task.taskStatus) }}
            </el-tag>
          </div>
          <p class="drawer-meta-desc">任务 #{{ task.id }} · {{ workflow?.flowName || `流程 ${task.flowModelId}` }} · 开始于 {{ formatTime(task.startTime) }}</p>
        </div>
        <div class="drawer-actions">
          <button class="btn-aliyun" type="button" :disabled="loading" @click="emit('refresh')">刷新</button>
          <button v-if="task.taskStatus === 'RUNNING'" class="btn-link danger" type="button" @click="emit('terminate')">终止任务</button>
        </div>
      </div>
    </template>

    <div v-if="task" class="execution-drawer" v-loading="loading">
      <el-tabs v-model="activeTab" class="execution-tabs">
        <el-tab-pane label="运行概览" name="overview">
          <div class="overview-grid">
            <div><span>执行状态</span><strong>{{ statusLabel(task.taskStatus) }}</strong></div>
            <div><span>执行步骤</span><strong>{{ completedStepCount }} / {{ steps.length }}</strong></div>
            <div><span>设备绑定</span><strong>{{ bindings.length }}</strong></div>
            <div><span>当前节点</span><strong>{{ task.currentNodeIdRef == null ? '-' : `#${task.currentNodeIdRef}` }}</strong></div>
          </div>
          <el-descriptions :column="2" border size="small" class="task-descriptions">
            <el-descriptions-item label="流程名称">{{ workflow?.flowName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="任务编号">#{{ task.id }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(task.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ formatTime(task.endTime) }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="执行流程图" name="graph">
          <div class="graph-layout">
            <section class="graph-workspace">
              <div class="panel-heading"><div><strong>执行流程图</strong><span>{{ workflow?.flowName || '-' }}</span></div><small>{{ steps.length }} 个执行步骤</small></div>
              <TaskRuntimeGraph v-if="graphReady" class="graph-canvas" :workflow="workflow" :steps="steps" :selected-step-id="selectedStepId" @select-step="selectGraphStep" />
              <div v-else class="graph-loading"><el-skeleton :rows="5" animated /></div>
            </section>
            <aside class="graph-detail">
              <div class="panel-heading"><div><strong>当前步骤</strong><span>{{ selectedStep ? `步骤 #${selectedStep.id}` : '等待选择' }}</span></div></div>
              <div class="graph-detail-scroll">
                <TaskStepDetail v-if="selectedStep" :step="selectedStep" />
                <div v-else-if="selectedGraphNode" class="waiting-detail">
                  <div><strong>{{ selectedGraphNode.name }}</strong><span>{{ nodeTypeLabel(selectedGraphNode) }}</span></div>
                  <el-tag type="info" effect="plain">WAITING</el-tag>
                  <p>该节点尚未创建执行步骤。</p>
                </div>
                <el-empty v-else description="选择流程节点查看详情" :image-size="44" />
              </div>
            </aside>
          </div>
        </el-tab-pane>

        <el-tab-pane label="步骤详情" name="steps">
          <div class="step-layout">
            <TaskStepTree :steps="steps" :selected-id="selectedStepId" @select="selectStep" />
            <div class="step-detail-scroll"><TaskStepDetail :step="selectedStep" /></div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="设备绑定" name="bindings">
          <div class="binding-list">
            <div v-for="binding in bindings" :key="binding.bindingKey || binding.nodeName" class="binding-row">
              <div><strong>{{ binding.nodeName }}</strong><span>{{ binding.flowName }} · {{ binding.deviceModelName || binding.deviceModelId }}</span></div>
              <span class="binding-arrow">→</span>
              <div><strong>{{ binding.instanceName || binding.deviceInstanceName || binding.deviceInstanceId || '待绑定' }}</strong><span>设备实例</span></div>
            </div>
            <el-empty v-if="!bindings.length" description="该任务没有设备实例绑定" :image-size="44" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="任务约束" name="constraints">
          <div class="constraint-view">
            <el-alert type="info" :closable="false" title="全局约束实时生效；任务级约束在任务启动后保持固定。" />
            <template v-if="constraints">
              <section><h4>全局约束 · 实时</h4><div v-for="rule in constraints.globalConstraints || []" :key="rule.ruleId" class="rule-row"><strong>{{ rule.ruleName }}</strong><code>{{ rule.expression }}</code></div><el-empty v-if="!constraints.globalConstraints?.length" description="当前没有启用的全局约束" :image-size="36" /></section>
              <section><h4>任务约束 · 固定</h4><div v-for="rule in constraints.taskConstraints || []" :key="rule.taskRuleIndex" class="rule-row"><strong>{{ rule.ruleName }}</strong><code>{{ rule.expression }}</code></div><el-empty v-if="!constraints.taskConstraints?.length" description="该任务未配置任务约束" :image-size="36" /></section>
            </template>
            <el-empty v-else description="暂无有效约束模型" :image-size="44" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="业务事件" name="events"><TaskBusinessEventList :logs="logs" /></el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import TaskBusinessEventList from './TaskBusinessEventList.vue'
import TaskRuntimeGraph from './TaskRuntimeGraph.vue'
import TaskStepDetail from './TaskStepDetail.vue'
import TaskStepTree from './TaskStepTree.vue'
import { selectPreferredStepId, statusLabel, statusType } from '../taskExecutionPresentation.js'

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

const activeTab = ref('graph')
const graphReady = ref(false)
const selectedStepId = ref<number | null>(null)
const selectedGraphNode = ref<Item | null>(null)
const selectedStep = computed(() => props.steps.find(step => step.id === selectedStepId.value) || null)
const completedStepCount = computed(() => props.steps.filter(step => step.nodeStatus === 'SUCCEEDED').length)

watch(() => props.task?.id, () => {
  activeTab.value = 'graph'
  selectedGraphNode.value = null
  selectedStepId.value = selectPreferredStepId(props.steps, null)
}, { immediate: true })

watch(() => props.steps, current => {
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

function selectStep(step: Item) {
  selectedStepId.value = step.id
  emit('select-step', step.id)
}

function selectGraphStep(stepId: number | null, node: Item) {
  selectedStepId.value = stepId
  selectedGraphNode.value = node
  emit('select-step', stepId)
}

function nodeTypeLabel(node: Item) {
  if (node.nodeType === 'DEV_NODE') return '设备能力节点'
  if (node.nodeType === 'SUBFLOW_NODE') return '子流程节点'
  return '功能节点'
}

function formatTime(value: unknown) {
  if (!value) return '-'
  const date = new Date(value as any)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.drawer-heading { min-width: 0; width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 20px; padding-right: 12px; }
.drawer-identity { min-width: 0; display: grid; gap: 5px; }
.drawer-title-row { display: flex; align-items: center; gap: 10px; }
.drawer-title-row h2 { min-width: 0; margin: 0; overflow: hidden; color: #1f2329; font-size: 16px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.drawer-identity p { margin: 0; color: #8a93a6; font-size: 11px; }
.drawer-actions { flex: 0 0 auto; display: flex; align-items: center; gap: 8px; }
.drawer-identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.drawer-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.drawer-meta-desc {
  color: var(--sl-text-secondary, #64748b);
  font-size: 11.5px;
  margin: 0;
}
.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.execution-drawer { height: 100%; min-height: 0; }
.execution-tabs { height: 100%; display: flex; flex-direction: column; }
.execution-tabs :deep(.el-tabs__header) { flex: 0 0 auto; margin: 0; padding: 0 20px; border-bottom: 1px solid var(--sl-border-base, #e2e8f0); background: #fafbfc; }
.execution-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.execution-tabs :deep(.el-tabs__item) { height: 42px; color: var(--sl-text-secondary, #64748b); font-size: 12.5px; }
.execution-tabs :deep(.el-tabs__item.is-active) { color: var(--sl-primary, #2563eb); font-weight: 600; }
.execution-tabs :deep(.el-tabs__content) { flex: 1; min-height: 0; overflow: auto; padding: 16px 20px 20px; }
.execution-tabs :deep(.el-tab-pane) { height: 100%; }

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 16px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  overflow: hidden;
}
.overview-grid > div {
  display: grid;
  gap: 4px;
  padding: 12px 16px;
  border-right: 1px solid var(--sl-border-base, #e2e8f0);
  background: var(--sl-bg-surface, #ffffff);
}
.overview-grid > div:last-child { border-right: 0; }
.overview-grid span { color: var(--sl-text-secondary, #64748b); font-size: 11.5px; }
.overview-grid strong {
  font-family: var(--sl-font-mono, monospace);
  color: var(--sl-text-heading, #0f172a);
  font-size: 18px;
  font-weight: 700;
}
.task-descriptions { border-radius: var(--sl-radius-sm, 6px); overflow: hidden; }

.graph-layout {
  height: 100%;
  min-height: 520px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  overflow: hidden;
  background: var(--sl-bg-surface, #ffffff);
}
.graph-workspace { min-width: 0; display: flex; flex-direction: column; border-right: 1px solid var(--sl-border-base, #e2e8f0); }
.panel-heading {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #f8fafc;
}
.panel-heading > div { min-width: 0; display: grid; gap: 2px; }
.panel-heading strong { color: var(--sl-text-heading, #0f172a); font-size: 12px; }
.panel-heading span, .panel-heading small { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.graph-canvas { flex: 1; min-height: 470px; border: 0; }
.graph-loading { flex: 1; min-height: 470px; padding: 32px; box-sizing: border-box; background: var(--sl-bg-hover, #f8fafc); }
.graph-workspace :deep(.runtime-graph) { height: 100%; min-height: 470px; border: 0; }
.graph-detail { min-width: 0; display: flex; flex-direction: column; background: var(--sl-bg-surface, #ffffff); }
.graph-detail-scroll, .step-detail-scroll { flex: 1; min-height: 0; overflow: auto; padding: 12px; }
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
.waiting-detail strong { font-size: 12px; }
.waiting-detail span, .waiting-detail p { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.waiting-detail p { grid-column: 1 / -1; margin: 0; }
.step-layout { height: 100%; min-height: 480px; display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 12px; }
.step-layout > :first-child { min-height: 0; overflow: auto; padding-right: 4px; }
.binding-list, .constraint-view { display: grid; gap: 10px; }
.binding-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: var(--sl-bg-surface, #ffffff);
}
.binding-row > div { display: grid; gap: 3px; }
.binding-row strong { font-size: 12px; }
.binding-row span { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.binding-arrow { text-align: center; color: var(--sl-primary, #2563eb) !important; font-size: 14px !important; }
.constraint-view section { display: grid; gap: 8px; }
.constraint-view h4 { margin: 8px 0 0; font-size: 12px; }
.rule-row {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  background: #f8fafc;
}
.rule-row strong { font-size: 11.5px; }
.rule-row code {
  color: var(--sl-text-body, #334155);
  font-family: var(--sl-font-mono, monospace);
  font-size: 11px;
}
@media (max-width: 1100px) { .graph-layout { grid-template-columns: minmax(0, 1fr) 320px; } }
@media (max-width: 900px) {
  .drawer-heading { align-items: flex-start; flex-direction: column; gap: 8px; }
  .overview-grid { grid-template-columns: repeat(2, 1fr); }
  .graph-layout { height: auto; grid-template-columns: 1fr; }
  .graph-workspace { border-right: 0; border-bottom: 1px solid var(--sl-border-base, #e2e8f0); }
  .graph-detail-scroll { max-height: 420px; }
  .step-layout { grid-template-columns: 1fr; }
}
</style>
