<template>
  <section class="snapshot-panel">
    <el-alert v-if="snapshotState.error" :title="snapshotState.error" type="error" :closable="false" show-icon />
    <el-collapse v-if="!snapshotState.error && snapshotState.rows.length" v-model="openInterfaces">
      <el-collapse-item
        v-for="row in snapshotState.rows"
        :key="rowKey(row)"
        :name="rowKey(row)"
      >
        <template #title>
          <div class="iface-title">
            <strong>{{ row.interfaceName }}</strong>
            <span>{{ row.direction === 'IN' ? '输入' : '输出' }}</span>
            <em :class="['signal-text', row.signalName ? 'has-signal' : 'idle']">{{ signalText(row) }}</em>
          </div>
        </template>
        <el-collapse class="iface-sections" :model-value="openSections[rowKey(row)] || []" @update:model-value="setOpenSections(rowKey(row), $event)">
          <el-collapse-item :name="`${rowKey(row)}:signal`">
            <template #title>
              <div class="section-title">当前信号</div>
            </template>
            <div class="payload-block">
              <RuntimeStructuredValue v-if="hasSnapshotPayload(row.payload)" :value="row.payload" />
              <span v-else class="payload-empty">-</span>
            </div>
          </el-collapse-item>
          <el-collapse-item :name="`${rowKey(row)}:triggers`">
            <template #title>
              <div class="section-title">
                <span>触发器绑定列表</span>
                <small>{{ triggersOf(row).length }}</small>
              </div>
            </template>
            <el-collapse v-if="triggersOf(row).length" class="trigger-collapse">
              <el-collapse-item
                v-for="item in triggersOf(row)"
                :key="`${rowKey(row)}:${item.index}`"
                :name="`${rowKey(row)}:${item.index}`"
                :class="{ fired: item.fired }"
              >
                <template #title>
                  <div :class="['trigger-title', { fired: item.fired }]">
                    <b>{{ item.title }}</b>
                    <code>{{ item.conditionText }}</code>
                  </div>
                </template>
                <dl class="snapshot-list">
                  <div>
                    <dt>触发条件</dt>
                    <dd>{{ item.conditionText }}</dd>
                  </div>
                  <div>
                    <dt>触发动作</dt>
                    <dd>{{ item.actionLabel }}</dd>
                  </div>
                </dl>
              </el-collapse-item>
            </el-collapse>
            <div v-else class="empty-hint">未绑定触发器</div>
          </el-collapse-item>
        </el-collapse>
      </el-collapse-item>
    </el-collapse>
    <div v-if="!snapshotState.error && !snapshotState.rows.length" class="empty-hint">暂无接口快照</div>
  </section>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
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
const openSections = reactive<Record<string, string[]>>({})
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
function setOpenSections(key: string, names: string | string[]) {
  openSections[key] = Array.isArray(names) ? names : [names]
}
</script>
<style scoped>
.snapshot-panel { padding: 0; }
.snapshot-panel :deep(.el-collapse) { border: 0; }
.snapshot-panel :deep(.el-collapse-item__header) {
  height: auto;
  min-height: 36px;
  padding: 6px 12px;
  line-height: 1.4;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.snapshot-panel :deep(.el-collapse-item__wrap) { border-bottom: 0; }
.snapshot-panel :deep(.el-collapse-item__content) { padding: 0; }
.iface-title,
.trigger-title,
.section-title {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: baseline;
  gap: 8px;
  padding-right: 8px;
}
.iface-title strong,
.trigger-title b,
.section-title {
  overflow: hidden;
  color: var(--sl-text-heading, #0f172a);
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.iface-title span { color: var(--sl-text-disabled, #94a3b8); font-size: 10.5px; flex: none; }
.section-title small {
  margin-left: auto;
  color: var(--sl-text-secondary, #64748b);
  font-size: 10.5px;
  font-weight: 500;
}
.signal-text { margin-left: auto; flex: none; font-style: normal; font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace); font-size: 10.5px; }
.signal-text.has-signal { color: var(--sl-success, #16a34a); }
.signal-text.idle { color: var(--sl-text-secondary, #64748b); }
.trigger-title code {
  min-width: 0;
  overflow: hidden;
  color: var(--sl-primary, #2563eb);
  font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace);
  font-size: 10.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trigger-title.fired b { color: var(--sl-danger, #dc2626); }
.trigger-title.fired code { color: var(--sl-danger, #dc2626); }
.snapshot-panel :deep(.el-collapse-item.fired > .el-collapse-item__header) {
  background: #fef2f2;
  color: var(--sl-danger, #dc2626);
}
.iface-sections { border-top: 1px solid var(--sl-border-subtle, #f1f5f9); }
.trigger-collapse { border-top: 1px solid var(--sl-border-subtle, #f1f5f9); }
.payload-block { padding: 8px 12px 10px; }
.snapshot-list { margin: 0; display: grid; }
.snapshot-list > div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.snapshot-list > div:last-child { border-bottom: 0; }
.snapshot-list dt { margin: 0; color: var(--sl-text-secondary, #64748b); font-size: 11px; font-weight: 600; }
.snapshot-list dd { margin: 0; color: var(--sl-text-heading, #0f172a); font-size: 12px; word-break: break-all; }
.payload-empty, .empty-hint { color: var(--sl-text-disabled, #94a3b8); font-size: 12px; }
.empty-hint { padding: 8px 12px; }
</style>
