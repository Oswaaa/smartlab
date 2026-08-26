<template>
  <section class="snapshot-panel">
    <el-alert v-if="snapshotState.error" :title="snapshotState.error" type="error" :closable="false" show-icon />
    <el-collapse v-if="!snapshotState.error && snapshotState.rows.length" v-model="openInterfaces">
      <el-collapse-item
        v-for="row in snapshotState.rows"
        :key="rowKey(row)"
        :name="rowKey(row)"
        class="iface-collapse-item"
      >
        <template #title>
          <div class="iface-title">
            <strong class="iface-name">{{ row.interfaceName }}</strong>
            <span class="iface-direction">({{ row.direction === 'IN' ? '输入' : '输出' }})</span>
          </div>
        </template>

        <!-- 接口展开内容区域（灰色背景体现父子层级，无竖线） -->
        <div class="iface-body">
          <!-- 子区域 1：当前信号（支持展开折叠） -->
          <div class="sub-section signal-section">
            <div
              class="sub-section-header"
              @click="toggleSection(rowKey(row), 'signal')"
            >
              <el-icon :class="['toggle-arrow', { 'is-expanded': isSectionExpanded(rowKey(row), 'signal') }]">
                <ArrowRight />
              </el-icon>
              <span class="sub-section-title">当前信号</span>
              <span :class="['sub-section-signal', row.signalName ? 'has-signal' : 'idle']">
                {{ signalText(row) }}
              </span>
            </div>
            <div
              v-show="isSectionExpanded(rowKey(row), 'signal')"
              class="sub-section-body"
            >
              <div v-if="hasSnapshotPayload(row.payload)" class="payload-wrap">
                <div class="payload-label">信号负载：</div>
                <RuntimeStructuredValue :value="row.payload" />
              </div>
              <div v-else-if="row.signalName" class="payload-empty-row">
                <span class="payload-label">信号负载：</span>
                <span class="payload-empty">无负载数据</span>
              </div>
            </div>
          </div>

          <!-- 子区域 2：触发器绑定列表（支持展开折叠） -->
          <div class="sub-section triggers-section">
            <div
              class="sub-section-header"
              @click="toggleSection(rowKey(row), 'triggers')"
            >
              <el-icon :class="['toggle-arrow', { 'is-expanded': isSectionExpanded(rowKey(row), 'triggers') }]">
                <ArrowRight />
              </el-icon>
              <span class="sub-section-title">触发器绑定列表</span>
              <span class="sub-section-count">({{ triggersOf(row).length }})</span>
            </div>
            <div
              v-show="isSectionExpanded(rowKey(row), 'triggers')"
              class="sub-section-body"
            >
              <div v-if="triggersOf(row).length" class="triggers-list">
                <div
                  v-for="item in triggersOf(row)"
                  :key="`${rowKey(row)}:${item.index}`"
                  class="trigger-item"
                >
                  <!-- 触发器项标题 -->
                  <div class="trigger-item-head">
                    <strong class="trigger-name">触发器 #{{ item.index + 1 }}</strong>
                    <span v-if="item.fired" class="trigger-fired-text">[已触发]</span>
                  </div>

                  <!-- 触发条件与动作属性列表（无卡片、无胶囊、无蓝色边框） -->
                  <div class="trigger-item-body">
                    <div class="prop-row">
                      <span class="prop-label">触发条件：</span>
                      <span class="prop-value condition-text">{{ item.conditionText }}</span>
                    </div>
                    <div class="prop-row">
                      <span class="prop-label">触发动作：</span>
                      <div class="prop-value actions-text">
                        <span v-if="!item.actionParts.length" class="payload-empty">未配置动作</span>
                        <span
                          v-for="(part, idx) in item.actionParts"
                          :key="`${item.index}:${idx}`"
                          class="action-item-text"
                        >
                          <span v-if="idx > 0" class="action-separator">; </span>
                          <strong class="action-verb-text">{{ formatAction(part).verb }}</strong>
                          <strong v-if="formatAction(part).target" class="action-target-text">{{ formatAction(part).target }}</strong>
                          <span v-if="formatAction(part).rest" class="action-rest-text">{{ formatAction(part).rest }}</span>
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="empty-hint">未绑定触发器</div>
            </div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
    <div v-if="!snapshotState.error && !snapshotState.rows.length" class="empty-hint">暂无接口快照</div>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import RuntimeStructuredValue from './RuntimeStructuredValue.vue'
import { hasSnapshotPayload, interfaceDefinitionForSnapshot, isWorkflowTriggerFired, normalizeInterfaceSnapshot } from '../../../../utils/workflowExecution.js'
import { workflowTriggerSummaries } from '../../../../utils/workflowCanvas.js'

type Item = Record<string, any>

const props = defineProps<{
  inputSnapshot?: unknown
  outputSnapshot?: unknown
  node?: Item | null
  triggerStates?: Item
}>()

const openInterfaces = ref<string[]>([])
const sectionExpansion = reactive<Record<string, { signal?: boolean, triggers?: boolean }>>({})

const snapshotState = computed(() => {
  try {
    return {
      rows: [
        ...normalizeInterfaceSnapshot(props.inputSnapshot, 'IN'),
        ...normalizeInterfaceSnapshot(props.outputSnapshot, 'OUT'),
      ],
      error: '',
    }
  } catch (error: any) {
    return { rows: [] as Item[], error: error.message || '接口快照格式错误' }
  }
})

watch(() => snapshotState.value.rows.map(row => rowKey(row)).join('|'), keys => {
  const list = keys ? keys.split('|') : []
  openInterfaces.value = list.slice(0, 1)
}, { immediate: true })

function rowKey(row: Item) {
  return `${row.direction}:${row.interfaceName}`
}

function signalText(row: Item) {
  return row.signalName === null ? (row.direction === 'IN' ? '尚未收到' : '尚未发送') : row.signalName
}

function triggersOf(row: Item) {
  return workflowTriggerSummaries(interfaceDefinitionForSnapshot(props.node, row) || {}, 0).items.map(item => ({
    ...item,
    fired: isWorkflowTriggerFired(props.triggerStates, row.interfaceName, item.index),
  }))
}

function isSectionExpanded(key: string, section: 'signal' | 'triggers'): boolean {
  if (!sectionExpansion[key]) {
    return true
  }
  return sectionExpansion[key][section] ?? true
}

function toggleSection(key: string, section: 'signal' | 'triggers') {
  if (!sectionExpansion[key]) {
    sectionExpansion[key] = { signal: true, triggers: true }
  }
  sectionExpansion[key][section] = !isSectionExpanded(key, section)
}

function formatAction(actionPart: { verb: string, rest: string }) {
  const verb = actionPart.verb
  if (verb === 'EMIT') {
    return {
      verb: 'EMIT',
      target: actionPart.rest,
      rest: '',
    }
  }
  if (verb === 'UPDATE') {
    const match = actionPart.rest.match(/^(.+?)\s*(为\s*.*)$/)
    if (match) {
      return {
        verb: 'UPDATE',
        target: match[1].trim(),
        rest: ` ${match[2].trim()}`,
      }
    }
    return {
      verb: 'UPDATE',
      target: actionPart.rest,
      rest: '',
    }
  }
  return {
    verb,
    target: actionPart.rest,
    rest: '',
  }
}
</script>

<style scoped>
.snapshot-panel {
  padding: 0;
  font-family: var(--sl-font-family);
  color: var(--sl-text-heading, #0f172a);
  font-size: 12.5px;
}
.snapshot-panel :deep(.el-collapse) {
  border: 0;
}
.snapshot-panel :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 38px;
  padding: 8px 12px;
  line-height: 1.45;
  font-size: 12.5px;
  color: var(--sl-text-heading, #0f172a);
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}
.snapshot-panel :deep(.el-collapse-item__header.is-active) {
  border-bottom-color: var(--sl-border-subtle, #f1f5f9);
}
.snapshot-panel :deep(.el-collapse-item__arrow) {
  margin-left: 4px;
}
.snapshot-panel :deep(.el-collapse-item__wrap) {
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}
.snapshot-panel :deep(.el-collapse-item__content) {
  padding: 0;
}

/* 接口标题 */
.iface-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.iface-name {
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.iface-direction {
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
  font-weight: 400;
  flex: none;
}

/* 接口展开区域（灰色背景标识父子关系，无竖线） */
.iface-body {
  padding: 10px 14px 12px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 折叠子区域 */
.sub-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sub-section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
  padding: 3px 0;
}
.sub-section-header:hover .sub-section-title {
  color: var(--sl-primary, #2563eb);
}

.toggle-arrow {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  transition: transform 0.2s ease;
  flex: none;
}
.toggle-arrow.is-expanded {
  transform: rotate(90deg);
}

.sub-section-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  transition: color 0.15s ease;
  flex: none;
}

.sub-section-signal {
  font-size: 12px;
  font-family: var(--sl-font-mono, monospace);
  font-weight: 600;
  margin-left: 4px;
}
.sub-section-signal.has-signal {
  color: var(--sl-success, #16a34a);
}
.sub-section-signal.idle {
  color: var(--sl-text-secondary, #64748b);
  font-family: var(--sl-font-family);
  font-weight: 400;
}

.sub-section-count {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
}

.sub-section-body {
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 负载区域 */
.payload-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}
.payload-label {
  color: var(--sl-text-secondary, #64748b);
  font-size: 11.5px;
  flex: none;
}
.payload-wrap :deep(.runtime-value),
.payload-wrap :deep(.runtime-fields dd) {
  font-family: var(--sl-font-family);
  font-size: 12px;
}
.payload-wrap :deep(.runtime-fields > div) {
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 6px;
}
.payload-wrap :deep(.runtime-fields dt) {
  font-size: 11.5px;
}
.payload-empty-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
}

/* 触发器列表 */
.triggers-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trigger-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.trigger-item-head {
  display: flex;
  align-items: center;
  gap: 6px;
  line-height: 1.4;
}

.trigger-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}

.trigger-fired-text {
  font-size: 11px;
  color: var(--sl-success, #16a34a);
  font-weight: 600;
}

/* 触发属性（条件 & 动作） */
.trigger-item-body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-left: 4px;
}

.prop-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 6px;
  align-items: baseline;
  font-size: 12px;
  line-height: 1.5;
}

.prop-label {
  color: var(--sl-text-secondary, #64748b);
  font-size: 11.5px;
  font-weight: 500;
}

.prop-value {
  color: var(--sl-text-body, #334155);
  word-break: break-all;
}

.condition-text {
  color: var(--sl-text-heading, #0f172a);
}

.actions-text {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px;
}

.action-item-text {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
}

.action-separator {
  color: var(--sl-text-disabled, #94a3b8);
  margin-right: 2px;
}

/* 动作动词：统一蓝色加粗 */
.action-verb-text {
  color: var(--sl-primary, #2563eb);
  font-weight: 700;
  font-family: var(--sl-font-mono, monospace);
  font-size: 11.5px;
}

/* 执行对象：黑色加粗 */
.action-target-text {
  color: var(--sl-text-heading, #0f172a);
  font-weight: 700;
  font-size: 12px;
}

.action-rest-text {
  color: var(--sl-text-body, #334155);
  font-size: 12px;
}

.payload-empty, .empty-hint {
  color: var(--sl-text-disabled, #94a3b8);
  font-size: 12px;
}
.empty-hint {
  padding: 2px 0;
}
</style>
