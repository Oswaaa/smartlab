<template>
  <section class="control-interfaces-panel">
    <div class="panel-heading">
      <div><strong>控制接口</strong><span>按输出 → 输入展示；触发器属于接口，不区分接口方向</span></div>
      <el-button v-if="canAddInterface" class="btn-aliyun" size="small" @click="addInterface">新增接口</el-button>
    </div>
    <div class="action-capabilities">
      <span>动作能力</span>
      <el-tag v-for="name in actions" :key="name" size="small">{{ actionLabel(name) }}</el-tag>
    </div>
    <el-alert v-if="!canAddInterface" title="设备能力节点和子流程节点使用系统默认控制契约，仅供查看。" type="info" :closable="false" />

    <div v-if="interfaces.length" class="master-detail">
      <nav class="interface-list" aria-label="控制接口列表">
        <button v-for="item in interfaces" :key="identity(item)" type="button" :class="['interface-item', { active: identity(item) === selectedIdentity }]" @click="selectedIdentity = identity(item)">
          <span class="direction">{{ directionLabel(item.direction) }}</span>
          <span class="interface-name" :title="item.name">{{ item.name }}</span>
          <small>{{ item.bindingTriggers?.length || 0 }} 个触发器</small>
          <el-tag v-if="isSystemItem(item)" size="small" type="info">系统默认</el-tag>
        </button>
      </nav>

      <div v-if="selectedInterface" class="interface-detail">
        <div class="detail-heading">
          <div><strong>{{ selectedInterface.name }}</strong><span>{{ selectedEditable ? '用户自定义接口' : selectedTriggerEditable ? '系统接口，可配置业务触发器' : '系统默认接口，只读' }}</span></div>
          <el-button v-if="selectedEditable" class="btn-aliyun-danger-link" link @click="deleteSelectedInterface">删除接口</el-button>
        </div>
        <div class="interface-fields">
          <label><span>接口名称</span><el-input :model-value="selectedInterface.name" :disabled="!selectedEditable" @update:model-value="updateInterfaceField('name', $event)" /></label>
          <label><span>方向</span><el-select :model-value="selectedInterface.direction" :disabled="!selectedEditable" @update:model-value="changeInterfaceDirection"><el-option label="OUT" value="OUT" /><el-option label="IN" value="IN" /></el-select></label>
          <label><span>接口类型</span><el-select :model-value="selectedInterface.interfaceType" :disabled="!selectedEditable" @update:model-value="updateInterfaceField('interfaceType', $event)"><el-option label="WORKFLOW" value="WORKFLOW" /><el-option label="STATE" value="STATE" /></el-select></label>
          <label class="signals"><span>允许信号</span><el-select :model-value="selectedInterface.allowedSignals || []" multiple collapse-tags :max-collapse-tags="3" :disabled="!selectedEditable || !signalCandidates.length" placeholder="选择允许信号" @update:model-value="updateAllowedSignals"><el-option v-for="signal in signalCandidates" :key="signal" :label="signal" :value="signal" /></el-select></label>
        </div>
        <WorkflowTriggerEditor :node="node" :interface-item="selectedInterface" :editable="selectedTriggerEditable" @update:interface="replaceSelectedInterface" />
      </div>
    </div>
    <WorkflowConfigurationEmpty v-else title="尚未配置控制接口" description="控制接口承载节点信号，并在条件满足时执行绑定动作。" :action-label="canAddInterface ? '新增接口' : ''" @action="addInterface" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
  defaultWorkflowInterfaceDirection,
  isSystemItem,
  orderedControlInterfaces,
  removeInterface,
  customTriggerActionNames,
} from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, readonly?: boolean, protocolMetadata?: Item, interfaceConnections?: Item[] }>(), { readonly: false, protocolMetadata: () => ({}), interfaceConnections: () => [] })
const emit = defineEmits<{ 'update:node': [node: Item], 'update:interfaceConnections': [connections: Item[]] }>()
const selectedIdentity = ref('')
const interfaces = computed(() => orderedControlInterfaces(props.node))
const actions = computed<string[]>(() => customTriggerActionNames(props.node))
const canAddInterface = computed(() => !props.readonly && canCustomizeControlInterfaces(props.node))
const selectedInterface = computed(() => interfaces.value.find(item => identity(item) === selectedIdentity.value) || interfaces.value[0] || null)
const selectedEditable = computed(() => !props.readonly && canEditControlItem(props.node, selectedInterface.value))
const selectedTriggerEditable = computed(() => !props.readonly && canEditControlTriggers(props.node, selectedInterface.value))
const signalCandidates = computed(() => selectedInterface.value ? protocolSignalCandidates(props.protocolMetadata, selectedInterface.value, isSystemItem(selectedInterface.value)) : [])

watch(interfaces, current => {
  if (!current.some(item => identity(item) === selectedIdentity.value)) selectedIdentity.value = current[0] ? identity(current[0]) : ''
}, { immediate: true })

function identity(item: Item) { return item?._systemKey || item?.name || '' }
function actionLabel(name: string) { return name }
function directionLabel(direction: string) { return direction }
function publish(next: Item) { emit('update:node', next) }
function uniqueName(direction: string) {
  const prefix = direction === 'IN' ? 'Interface_workflow_in_' : 'Interface_workflow_out_'
  let index = 2
  while ((props.node.interfaces || []).some((item: Item) => item.name === `${prefix}${index}`)) index += 1
  return `${prefix}${index}`
}
function addInterface() {
  if (!canAddInterface.value) return
  const direction = defaultWorkflowInterfaceDirection(props.node)
  const name = uniqueName(direction)
  const definition = createWorkflowInterfaceDefinition(props.node, direction, name)
  publish({ ...props.node, actions: customTriggerActionNames(props.node), interfaces: [...(props.node.interfaces || []), definition] })
  selectedIdentity.value = name
}
function replaceSelectedInterface(nextInterface: Item) {
  const current = selectedInterface.value
  if (!current || !selectedTriggerEditable.value) return
  const currentIdentity = identity(current)
  publish({ ...props.node, interfaces: (props.node.interfaces || []).map((item: Item) => identity(item) === currentIdentity ? nextInterface : item) })
  selectedIdentity.value = identity(nextInterface)
}
function updateInterfaceField(field: string, value: unknown) {
  if (selectedInterface.value && selectedEditable.value) replaceSelectedInterface({ ...selectedInterface.value, [field]: value })
}
async function changeInterfaceDirection(direction: string) {
  const current = selectedInterface.value
  if (!current || !selectedEditable.value || current.direction === direction) return
  const result = changeWorkflowInterfaceDirection(props.node, current.name, direction, props.interfaceConnections)
  const cleanup = [
    result.removedConnectionCount ? `${result.removedConnectionCount} 条已有连线` : '',
    result.removedTriggerCount ? `${result.removedTriggerCount} 个不兼容触发器` : '',
  ].filter(Boolean).join('、')
  if (cleanup) {
    try {
      await ElMessageBox.confirm(
        `切换为 ${direction} 将自动删除${cleanup}，是否继续？`,
        '确认切换接口方向',
        { type: 'warning', confirmButtonText: '确认切换', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  publish(result.node)
  emit('update:interfaceConnections', result.interfaceConnections)
  selectedIdentity.value = current.name
}
function updateAllowedSignals(value: string[]) { updateInterfaceField('allowedSignals', value) }
function deleteSelectedInterface() {
  if (!selectedInterface.value || !selectedEditable.value) return
  const result = removeInterface(props.node, selectedInterface.value.name, props.interfaceConnections)
  publish(result.node)
  emit('update:interfaceConnections', result.interfaceConnections)
  selectedIdentity.value = ''
}
</script>

<style scoped>
.control-interfaces-panel{display:grid;gap:0;padding:0;background:#fff}.panel-heading{min-height:52px;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:0 20px;border-bottom:1px solid #e5e5e5}.panel-heading>div,.detail-heading>div{display:grid;gap:2px}.panel-heading strong,.detail-heading strong{color:#262626;font-size:13px;font-weight:500}.panel-heading span,.detail-heading span{color:#8c8c8c;font-size:10px}.action-capabilities{display:flex;align-items:center;gap:7px;min-height:42px;padding:5px 20px;border:0;border-bottom:1px solid #e5e5e5;background:#fafafa}.action-capabilities>span{margin-right:5px;color:#595959;font-size:11px}.control-interfaces-panel>:deep(.el-alert){margin:10px 20px;border-radius:2px}.master-detail{display:grid;grid-template-columns:196px minmax(0,1fr);min-height:460px;border:0;border-top:1px solid #e5e5e5;background:#fff}.interface-list{display:flex;flex-direction:column;border-right:1px solid #e5e5e5;background:#fafafa}.interface-item{display:grid;grid-template-columns:38px minmax(0,1fr);gap:3px 7px;min-height:58px;padding:9px 11px;border:0;border-bottom:1px solid #f0f0f0;background:transparent;color:#262626;text-align:left;cursor:pointer;transition:background-color .15s ease}.interface-item:hover{background:#f0f7ff}.interface-item.active{background:#e6f4ff;box-shadow:inset 3px 0 #1677ff}.interface-item .direction{grid-row:1 / span 2;align-self:center;color:#1677ff;font-size:10px;font-weight:500}.interface-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:500}.interface-item small{color:#8c8c8c;font-size:9px}.interface-item :deep(.el-tag){grid-column:2;justify-self:start}.interface-detail{display:grid;align-content:start;gap:0;min-width:0;padding:0;background:#fff}.detail-heading{height:52px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-bottom:1px solid #e5e5e5}.interface-fields{display:grid;grid-template-columns:240px 110px 140px;align-items:end;justify-content:start;gap:12px;padding:14px}.interface-fields label{display:grid;gap:6px;min-width:0}.interface-fields label>span{color:#595959;font-size:11px;font-weight:500}.interface-fields .signals{grid-column:1 / -1;width:360px}.interface-fields :deep(.el-select){width:100%}@media(max-width:980px){.master-detail{grid-template-columns:176px minmax(0,1fr)}.interface-fields{grid-template-columns:minmax(180px,240px) 100px 128px}.interface-fields .signals{width:100%}}@media(max-width:720px){.master-detail{grid-template-columns:1fr}.interface-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));border-right:0;border-bottom:1px solid #e5e5e5}.interface-fields{grid-template-columns:1fr 1fr}.interface-fields .signals{grid-column:1 / -1}}
</style>
