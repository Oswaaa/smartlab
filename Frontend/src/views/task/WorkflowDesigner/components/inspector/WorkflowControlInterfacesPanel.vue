<template>
  <section class="control-interfaces-panel">
    <!-- 卡片 1：控制接口总览卡片 (左右双栏动态端子树) -->
    <section class="control-card control-overview-card">
      <div class="panel-section-heading">
        <div class="heading-text">
          <h4>控制接口总览</h4>
          <p>按输出与输入分类展示节点的所有事件接口。</p>
        </div>
        <div class="heading-actions">
          <span v-if="!canAddInterface" class="param-count-badge">系统默认契约</span>
          <span class="count-summary">{{ interfaces.length }} 项接口</span>
        </div>
      </div>

      <div v-if="interfaces.length" class="matrix-dual-column">
        <!-- 左栏: 输出接口 (OUT) -->
        <div class="matrix-column out-col">
          <div class="matrix-col-header">
            <div class="col-title-group">
              <span class="badge-dir out">OUT</span>
              <span class="col-title-text">输出接口 ({{ outInterfaces.length }})</span>
            </div>
            <button
              v-if="canAddInterface"
              type="button"
              class="btn-aliyun-cta"
              @click="addInterfaceWithDirection('OUT')"
            >
              + 新增输出
            </button>
          </div>

          <div class="matrix-items-list">
            <div
              v-for="item in outInterfaces"
              :key="identity(item)"
              class="matrix-item-row"
              :class="{ active: identity(item) === selectedIdentity }"
              @click="selectedIdentity = identity(item)"
            >
              <div class="item-main">
                <span class="item-name" :title="item.name">{{ item.name }}</span>
              </div>
              <div class="item-meta">
                <span class="trigger-count-tag">{{ item.bindingTriggers?.length || 0 }} 触发</span>
                <el-tag v-if="isSystemItem(item)" size="small" type="info">系统</el-tag>
                <button
                  v-else-if="canEditControlItem(node, item)"
                  type="button"
                  class="btn-del-interface"
                  title="删除接口"
                  @click.stop="deleteInterfaceItem(item)"
                >
                  ✕
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 右栏: 输入接口 (IN) -->
        <div class="matrix-column in-col">
          <div class="matrix-col-header">
            <div class="col-title-group">
              <span class="badge-dir in">IN</span>
              <span class="col-title-text">输入接口 ({{ inInterfaces.length }})</span>
            </div>
            <button
              v-if="canAddInterface"
              type="button"
              class="btn-aliyun-cta"
              @click="addInterfaceWithDirection('IN')"
            >
              + 新增输入
            </button>
          </div>

          <div class="matrix-items-list">
            <div
              v-for="item in inInterfaces"
              :key="identity(item)"
              class="matrix-item-row"
              :class="{ active: identity(item) === selectedIdentity }"
              @click="selectedIdentity = identity(item)"
            >
              <div class="item-main">
                <span class="item-name" :title="item.name">{{ item.name }}</span>
              </div>
              <div class="item-meta">
                <span class="trigger-count-tag">{{ item.bindingTriggers?.length || 0 }} 触发</span>
                <el-tag v-if="isSystemItem(item)" size="small" type="info">系统</el-tag>
                <button
                  v-else-if="canEditControlItem(node, item)"
                  type="button"
                  class="btn-del-interface"
                  title="删除接口"
                  @click.stop="deleteInterfaceItem(item)"
                >
                  ✕
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <WorkflowConfigurationEmpty
        v-else
        title="尚未配置控制接口"
        description="控制接口承载节点信号，并在条件满足时执行绑定动作。"
        :action-label="canAddInterface ? '新增接口' : ''"
        @action="addInterfaceWithDirection('OUT')"
      />
    </section>

    <!-- 卡片 2：当前接口配置与触发规则卡片 -->
    <section v-if="selectedInterface" class="control-card control-detail-card">
      <div class="panel-section-heading">
        <div class="heading-text">
          <h4>接口配置</h4>
          <p>
            配置 <strong>{{ selectedInterface.name }}</strong> 的接口属性与绑定的事件触发器。
          </p>
        </div>
        <div class="heading-actions">
          <button
            v-if="selectedEditable"
            type="button"
            class="btn-aliyun-danger-link"
            @click="deleteSelectedInterface"
          >
            删除接口
          </button>
          <span v-else class="param-count-badge">
            {{ selectedTriggerEditable ? '契约预置 (可配触发器)' : '系统契约 (只读)' }}
          </span>
        </div>
      </div>

      <!-- 1. 用户自定义接口：两行宽裕表单排布 -->
      <div v-if="selectedEditable" class="interface-form-two-rows">
        <div class="form-row-first">
          <div class="form-item item-name">
            <span class="field-label">接口名称</span>
            <el-input
              :model-value="selectedInterface.name"
              placeholder="接口名称 (如: on_success)"
              @update:model-value="updateInterfaceField('name', $event)"
            />
          </div>
          <div class="form-item item-dir">
            <span class="field-label">传输方向</span>
            <el-select
              :model-value="selectedInterface.direction"
              @update:model-value="changeInterfaceDirection"
            >
              <el-option label="输出" value="OUT" />
              <el-option label="输入" value="IN" />
            </el-select>
          </div>
          <div class="form-item item-type">
            <span class="field-label">接口类型</span>
            <el-select
              :model-value="selectedInterface.interfaceType"
              @update:model-value="updateInterfaceField('interfaceType', $event)"
            >
              <el-option label="WORKFLOW" value="WORKFLOW" />
              <el-option label="STATE" value="STATE" />
            </el-select>
          </div>
        </div>

        <div class="form-row-second">
          <div class="form-item item-signals">
            <span class="field-label">允许信号</span>
            <el-select
              :model-value="selectedInterface.allowedSignals || []"
              multiple
              filterable
              clearable
              :disabled="!signalCandidates.length"
              placeholder="选择该接口允许传递的事件信号"
              @update:model-value="updateAllowedSignals"
            >
              <el-option v-for="signal in signalCandidates" :key="signal" :label="signal" :value="signal" />
            </el-select>
          </div>
        </div>
      </div>

      <!-- 2. 系统只读契约接口：优雅单行元信息摘要条 -->
      <div v-else class="interface-meta-bar">
        <div class="meta-bar-left">
          <strong class="interface-active-title">{{ selectedInterface.name }}</strong>
          <span class="meta-tag">{{ selectedInterface.direction === 'OUT' ? '输出' : '输入' }}</span>
          <span class="meta-tag">{{ selectedInterface.interfaceType }}</span>
        </div>
        <div class="meta-bar-right">
          <span class="signals-caption">允许信号:</span>
          <div v-if="selectedInterface.allowedSignals?.length" class="signals-tags">
            <span
              v-for="sig in selectedInterface.allowedSignals"
              :key="sig"
              class="signal-pill"
            >
              {{ sig }}
            </span>
          </div>
          <span v-else class="signal-empty-text">全部信号</span>
        </div>
      </div>

      <!-- 3. 触发器编辑器 -->
      <WorkflowTriggerEditor
        :node="node"
        :interface-item="selectedInterface"
        :editable="selectedTriggerEditable"
        @update:interface="replaceSelectedInterface"
      />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import WorkflowTriggerEditor from './WorkflowTriggerEditor.vue'
import WorkflowConfigurationEmpty from './WorkflowConfigurationEmpty.vue'
import { protocolSignalCandidates } from '../../../../../utils/workflowDesignerRules.js'
import {
  canCustomizeControlInterfaces,
  canEditControlItem,
  canEditControlTriggers,
  changeWorkflowInterfaceDirection,
  createWorkflowInterfaceDefinition,
  isSystemItem,
  orderedControlInterfaces,
  removeInterface,
  renameWorkflowInterface,
  customTriggerActionNames,
} from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(
  defineProps<{
    node: Item
    readonly?: boolean
    protocolMetadata?: Item
    interfaceConnections?: Item[]
  }>(),
  { readonly: false, protocolMetadata: () => ({}), interfaceConnections: () => [] }
)
const emit = defineEmits<{
  'update:node': [node: Item]
  'update:interfaceConnections': [connections: Item[]]
}>()

const selectedIdentity = ref('')
const interfaces = computed(() => orderedControlInterfaces(props.node))
const outInterfaces = computed(() => interfaces.value.filter((item: Item) => item.direction === 'OUT'))
const inInterfaces = computed(() => interfaces.value.filter((item: Item) => item.direction === 'IN'))

const canAddInterface = computed(() => !props.readonly && canCustomizeControlInterfaces(props.node))
const selectedInterface = computed(() => interfaces.value.find(item => identity(item) === selectedIdentity.value) || interfaces.value[0] || null)
const selectedEditable = computed(() => !props.readonly && canEditControlItem(props.node, selectedInterface.value))
const selectedTriggerEditable = computed(() => !props.readonly && canEditControlTriggers(props.node, selectedInterface.value))
const signalCandidates = computed(() => selectedInterface.value ? protocolSignalCandidates(props.protocolMetadata, selectedInterface.value, isSystemItem(selectedInterface.value)) : [])

watch(
  interfaces,
  current => {
    if (!current.some(item => identity(item) === selectedIdentity.value)) {
      selectedIdentity.value = current[0] ? identity(current[0]) : ''
    }
  },
  { immediate: true }
)

function identity(item: Item) {
  return item?._systemKey || item?.name || ''
}
function publish(next: Item) {
  emit('update:node', next)
}
function uniqueName(direction: string) {
  const prefix = direction === 'IN' ? 'Interface_workflow_in_' : 'Interface_workflow_out_'
  let index = 1
  while ((props.node.interfaces || []).some((item: Item) => item.name === `${prefix}${index}`)) index += 1
  return `${prefix}${index}`
}
function addInterfaceWithDirection(direction: 'OUT' | 'IN') {
  if (!canAddInterface.value) return
  const name = uniqueName(direction)
  const definition = createWorkflowInterfaceDefinition(props.node, direction, name)
  publish({
    ...props.node,
    actions: customTriggerActionNames(props.node),
    interfaces: [...(props.node.interfaces || []), definition]
  })
  selectedIdentity.value = name
  void nextTick(() => {
    const detailCard = document.querySelector('.control-detail-card')
    if (detailCard) {
      detailCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  })
}
function replaceSelectedInterface(nextInterface: Item) {
  const current = selectedInterface.value
  if (!current || !selectedTriggerEditable.value) return
  const currentIdentity = identity(current)
  publish({
    ...props.node,
    interfaces: (props.node.interfaces || []).map((item: Item) =>
      identity(item) === currentIdentity ? nextInterface : item
    )
  })
  selectedIdentity.value = identity(nextInterface)
}
function updateInterfaceField(field: string, value: unknown) {
  const current = selectedInterface.value
  if (!current || !selectedEditable.value) return
  if (field === 'name') {
    const result = renameWorkflowInterface(props.node, current.name, String(value ?? ''), props.interfaceConnections)
    publish(result.node)
    emit('update:interfaceConnections', result.interfaceConnections)
    selectedIdentity.value = identity(result.node.interfaces?.find((item: Item) => item.name === value) || { ...current, name: value })
    return
  }
  replaceSelectedInterface({ ...current, [field]: value })
}
async function changeInterfaceDirection(direction: string) {
  const current = selectedInterface.value
  if (!current || !selectedEditable.value || current.direction === direction) return
  const result = changeWorkflowInterfaceDirection(props.node, current.name, direction, props.interfaceConnections)
  const cleanup = [
    result.removedConnectionCount ? `${result.removedConnectionCount} 条已有连线` : '',
    result.removedTriggerCount ? `${result.removedTriggerCount} 个不兼容触发器` : ''
  ].filter(Boolean).join('、')
  if (cleanup) {
    try {
      await ElMessageBox.confirm(
        `切换为 ${direction} 将自动删除${cleanup}，是否继续？`,
        '确认切换接口方向',
        { type: 'warning', confirmButtonText: '确认切换', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  publish(result.node)
  emit('update:interfaceConnections', result.interfaceConnections)
  selectedIdentity.value = current.name
}
function updateAllowedSignals(value: string[]) {
  updateInterfaceField('allowedSignals', value)
}
function deleteSelectedInterface() {
  if (!selectedInterface.value || !selectedEditable.value) return
  deleteInterfaceItem(selectedInterface.value)
}
function deleteInterfaceItem(target: Item) {
  if (!canEditControlItem(props.node, target)) return
  const result = removeInterface(props.node, target.name, props.interfaceConnections)
  publish(result.node)
  emit('update:interfaceConnections', result.interfaceConnections)
  if (selectedIdentity.value === identity(target)) {
    selectedIdentity.value = ''
  }
}
</script>

<style scoped>
.control-interfaces-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0;
  background: transparent;
}
.control-interfaces-panel :deep(.el-select),
.control-interfaces-panel :deep(.el-input) {
  width: 100%;
}

/* 一体化卡片容器：对齐系统统一规范 */
.control-card {
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
  overflow: hidden;
}

.panel-section-heading {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  background: #ffffff;
}

.heading-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.panel-section-heading h4 {
  margin: 0;
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 700;
}

.panel-section-heading p {
  margin: 0;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  line-height: 16px;
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.param-count-badge {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--sl-primary, #2563eb);
  background: var(--sl-primary-light, #eff6ff);
  padding: 1px 6px;
  border-radius: 4px;
  flex: none;
}

.count-summary {
  font-size: 10.5px;
  color: var(--sl-text-secondary, #64748b);
  font-family: var(--sl-font-mono, monospace);
  flex: none;
}

/* 工业双栏动态端子树 */
.matrix-dual-column {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 10px 14px;
  background: #ffffff;
}

.matrix-column {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.matrix-col-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2px;
}

.col-title-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.col-title-text {
  font-size: 11px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
}

.badge-dir {
  font-size: 9px;
  font-weight: 700;
  font-family: var(--sl-font-mono, monospace);
  padding: 1px 4px;
  border-radius: 2px;
}
.badge-dir.out {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
}
.badge-dir.in {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.matrix-items-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 170px;
  overflow-y: auto;
}

.matrix-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: 3px;
  background: #ffffff;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.matrix-item-row:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.matrix-item-row.active {
  border-color: var(--sl-primary, #2563eb);
  background: #eff6ff;
  box-shadow: inset 3.5px 0 0 var(--sl-primary, #2563eb);
}

.item-main {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.item-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.matrix-item-row.active .item-name {
  color: var(--sl-primary, #2563eb);
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

.trigger-count-tag {
  font-size: 9.5px;
  color: var(--sl-text-secondary, #64748b);
  font-family: var(--sl-font-mono, monospace);
  background: #f1f5f9;
  padding: 1px 4px;
  border-radius: 2px;
}

.matrix-item-row.active .trigger-count-tag {
  background: #ffffff;
  color: var(--sl-primary, #2563eb);
  font-weight: 600;
}

.btn-del-interface {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  padding: 0 2px;
  line-height: 1;
  border-radius: 2px;
}
.btn-del-interface:hover {
  color: var(--sl-danger, #dc2626);
  background: #fee2e2;
}

.interface-form-two-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}

.form-row-first {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.item-name {
  flex: 1.4;
  min-width: 0;
}

.item-dir {
  width: 120px;
  flex: none;
}

.item-type {
  width: 130px;
  flex: none;
}

.form-row-second {
  display: flex;
  width: 100%;
}

.item-signals {
  width: 100%;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--sl-text-secondary, #64748b);
}

.interface-meta-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  font-size: 11.5px;
}

.meta-bar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.interface-active-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
}

.meta-tag {
  font-size: 10px;
  font-family: var(--sl-font-mono, monospace);
  background: #f1f5f9;
  color: #475569;
  padding: 1px 6px;
  border-radius: 3px;
}

.meta-bar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.signals-caption {
  font-size: 10.5px;
  color: var(--sl-text-secondary, #64748b);
}

.signals-tags {
  display: flex;
  align-items: center;
  gap: 4px;
}

.signal-pill {
  font-size: 9.5px;
  font-family: var(--sl-font-mono, monospace);
  background: #f8fafc;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  color: #334155;
  padding: 1px 5px;
  border-radius: 2px;
}

.signal-empty-text {
  font-size: 10px;
  color: var(--sl-text-secondary, #94a3b8);
}
</style>
