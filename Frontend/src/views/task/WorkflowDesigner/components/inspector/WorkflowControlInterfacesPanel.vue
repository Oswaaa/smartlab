<template>
  <section class="control-interfaces-panel">
    <div class="panel-heading">
      <div><strong>控制接口</strong><span>按 OUT → IN 展示；触发器属于接口，不区分接口方向</span></div>
      <el-button v-if="canAddInterface" size="small" type="primary" plain @click="addInterface">新增接口</el-button>
    </div>
    <div class="action-capabilities">
      <span>动作能力</span>
      <el-tag v-for="name in actions" :key="name" size="small">{{ name }}</el-tag>
      <el-button v-for="name in missingActions" :key="name" v-if="canAddInterface" size="small" link type="primary" @click="enableAction(name)">启用 {{ name }}</el-button>
    </div>
    <el-alert v-if="!canAddInterface" title="设备能力节点和子流程节点使用系统默认控制契约，仅供查看。" type="info" :closable="false" />

    <div v-if="interfaces.length" class="master-detail">
      <nav class="interface-list" aria-label="控制接口列表">
        <button v-for="item in interfaces" :key="identity(item)" type="button" :class="['interface-item', { active: identity(item) === selectedIdentity }]" @click="selectedIdentity = identity(item)">
          <span class="direction">{{ item.direction }}</span>
          <span class="interface-name" :title="item.name">{{ item.name }}</span>
          <small>{{ item.bindingTriggers?.length || 0 }} 个触发器</small>
          <el-tag v-if="isSystemItem(item)" size="small" type="info">系统默认</el-tag>
        </button>
      </nav>

      <div v-if="selectedInterface" class="interface-detail">
        <div class="detail-heading">
          <div><strong>{{ selectedInterface.name }}</strong><span>{{ selectedEditable ? '用户自定义接口' : '系统默认接口，只读' }}</span></div>
          <el-button v-if="selectedEditable" link type="danger" @click="deleteSelectedInterface">删除接口</el-button>
        </div>
        <div class="interface-fields">
          <label><span>接口名称</span><el-input :model-value="selectedInterface.name" :disabled="!selectedEditable" @update:model-value="updateInterfaceField('name', $event)" /></label>
          <label><span>方向</span><el-select :model-value="selectedInterface.direction" :disabled="!selectedEditable" @update:model-value="updateInterfaceField('direction', $event)"><el-option label="OUT" value="OUT" /><el-option label="IN" value="IN" /></el-select></label>
          <label><span>接口类型</span><el-select :model-value="selectedInterface.interfaceType" :disabled="!selectedEditable" @update:model-value="updateInterfaceField('interfaceType', $event)"><el-option label="WORKFLOW" value="WORKFLOW" /><el-option label="STATE" value="STATE" /></el-select></label>
          <label class="signals"><span>允许信号</span><el-input :model-value="(selectedInterface.allowedSignals || []).join(', ')" :disabled="!selectedEditable" placeholder="逗号分隔信号名" @change="updateAllowedSignals" /></label>
        </div>
        <WorkflowTriggerEditor :node="node" :interface-item="selectedInterface" :editable="selectedEditable" @update:interface="replaceSelectedInterface" />
      </div>
    </div>
    <el-empty v-else description="尚未定义控制接口" :image-size="42" />
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import WorkflowTriggerEditor from './WorkflowTriggerEditor.vue'
import {
  canCustomizeControlInterfaces,
  canEditControlItem,
  isSystemItem,
  orderedControlInterfaces,
  removeInterface,
} from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, interfaceConnections?: Item[] }>(), { interfaceConnections: () => [] })
const emit = defineEmits<{ 'update:node': [node: Item], 'update:interfaceConnections': [connections: Item[]] }>()
const selectedIdentity = ref('')
const interfaces = computed(() => orderedControlInterfaces(props.node))
const actions = computed<string[]>(() => props.node.actions || [])
const missingActions = computed(() => ['UPDATE', 'EMIT'].filter(name => !actions.value.includes(name)))
const canAddInterface = computed(() => canCustomizeControlInterfaces(props.node))
const selectedInterface = computed(() => interfaces.value.find(item => identity(item) === selectedIdentity.value) || interfaces.value[0] || null)
const selectedEditable = computed(() => canEditControlItem(props.node, selectedInterface.value))

watch(interfaces, current => {
  if (!current.some(item => identity(item) === selectedIdentity.value)) selectedIdentity.value = current[0] ? identity(current[0]) : ''
}, { immediate: true })

function identity(item: Item) { return item?._systemKey || item?.name || '' }
function publish(next: Item) { emit('update:node', next) }
function uniqueName() { let index = 1; while ((props.node.interfaces || []).some((item: Item) => item.name === `interface${index}`)) index += 1; return `interface${index}` }
function enableAction(name: string) { publish({ ...props.node, actions: [...actions.value, name] }) }
function addInterface() {
  if (!canAddInterface.value) return
  const name = uniqueName()
  publish({ ...props.node, interfaces: [...(props.node.interfaces || []), { name, direction: 'OUT', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [] }] })
  selectedIdentity.value = name
}
function replaceSelectedInterface(nextInterface: Item) {
  const current = selectedInterface.value
  if (!current || !selectedEditable.value) return
  const currentIdentity = identity(current)
  publish({ ...props.node, interfaces: (props.node.interfaces || []).map((item: Item) => identity(item) === currentIdentity ? nextInterface : item) })
  selectedIdentity.value = identity(nextInterface)
}
function updateInterfaceField(field: string, value: unknown) { if (selectedInterface.value) replaceSelectedInterface({ ...selectedInterface.value, [field]: value }) }
function updateAllowedSignals(value: string) { updateInterfaceField('allowedSignals', value.split(',').map(item => item.trim()).filter(Boolean)) }
function deleteSelectedInterface() {
  if (!selectedInterface.value || !selectedEditable.value) return
  const result = removeInterface(props.node, selectedInterface.value.name, props.interfaceConnections)
  publish(result.node)
  emit('update:interfaceConnections', result.interfaceConnections)
  selectedIdentity.value = ''
}
</script>

<style scoped>
.control-interfaces-panel{display:grid;gap:10px;padding:12px}.panel-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.panel-heading>div,.detail-heading>div{display:grid;gap:2px}.panel-heading strong,.detail-heading strong{font-size:12px}.panel-heading span,.detail-heading span{color:#8490a0;font-size:10px}.action-capabilities{display:flex;align-items:center;gap:6px;min-height:28px;padding:6px 8px;border:1px solid #e2e7ed;background:#fafbfc}.action-capabilities>span{margin-right:4px;color:#687588;font-size:10px}.master-detail{display:grid;grid-template-columns:180px minmax(0,1fr);min-height:380px;border:1px solid #dfe4ea}.interface-list{display:flex;flex-direction:column;border-right:1px solid #dfe4ea;background:#f7f9fb}.interface-item{display:grid;grid-template-columns:34px minmax(0,1fr);gap:3px 6px;padding:9px;border:0;border-bottom:1px solid #e6eaf0;background:transparent;color:#26364b;text-align:left;cursor:pointer}.interface-item.active{background:#eaf2fb;box-shadow:inset 3px 0 #3276d2}.interface-item .direction{grid-row:1 / span 2;align-self:center;color:#3276d2;font-size:10px;font-weight:700}.interface-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:600}.interface-item small{color:#8793a3;font-size:9px}.interface-item :deep(.el-tag){grid-column:2;justify-self:start}.interface-detail{display:grid;align-content:start;gap:12px;padding:12px;min-width:0}.detail-heading{display:flex;align-items:center;justify-content:space-between}.interface-fields{display:grid;grid-template-columns:1fr 100px 120px;gap:8px}.interface-fields label{display:grid;gap:4px;min-width:0}.interface-fields label>span{color:#697688;font-size:10px}.interface-fields .signals{grid-column:1 / -1}.interface-fields :deep(.el-select){width:100%}@media(max-width:720px){.master-detail{grid-template-columns:1fr}.interface-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));border-right:0;border-bottom:1px solid #dfe4ea}.interface-fields{grid-template-columns:1fr 1fr}.interface-fields .signals{grid-column:1 / -1}}
</style>
