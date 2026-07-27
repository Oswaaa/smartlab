<template>
  <el-drawer :model-value="visible" :title="node ? `节点配置 · ${node.name}` : '节点配置'" size="520px" :append-to-body="true" @update:model-value="onVisibleChange">
    <template v-if="node"><el-form label-position="top" class="node-form"><el-form-item label="节点名称"><el-input v-model="nameDraft" maxlength="80" @change="rename" /></el-form-item></el-form><el-tabs v-model="activeTab">
      <el-tab-pane name="basic"><template #label>基础配置 <small v-if="tabErrors.basic">({{ tabErrors.basic }})</small></template><section class="basic-configuration"><template v-if="node.nodeType === 'DEV_NODE'"><el-form label-position="top"><el-form-item label="执行能力"><el-select :model-value="node.capability?.capabilityName" filterable @update:model-value="changeCapability"><el-option v-for="item in deviceCapabilities" :key="item.capabilityName" :label="item.capabilityName" :value="item.capabilityName" /></el-select></el-form-item><el-form-item v-for="parameter in selectedCapability?.parameters || []" :key="parameter.parameterName" :label="parameter.parameterName"><WorkflowTypedValueInput :model-value="node.capability?.capabilityParameters?.[parameter.parameterName]" :data-type="parameter.dataType" @update:model-value="updateParameter(parameter.parameterName, $event)" /></el-form-item></el-form></template><template v-else-if="node.nodeType === 'SUBFLOW_NODE'"><el-alert title="子流程节点会引用目标流程的入口与出口；接口和生命周期由系统维护。" type="info" :closable="false" /></template><template v-else-if="node.functionType === 'BRANCH'"><el-form label-position="top"><el-form-item label="分支表达式"><el-input :model-value="node.expression" type="textarea" :rows="4" @update:model-value="updateBasic('expression', $event)" /></el-form-item></el-form></template><el-empty v-else description="该节点没有额外基础配置" :image-size="42" /></section></el-tab-pane>
      <el-tab-pane name="variables"><template #label>变量与端口 <small v-if="tabErrors.variables">({{ tabErrors.variables }})</small></template><WorkflowVariablesPortsPanel :node="node" :device-attributes="deviceAttributes" :port-connections="portConnections" @update:node="emit('update:node', $event)" @update:port-connections="emit('update:portConnections', $event)" /></el-tab-pane>
      <el-tab-pane name="triggers"><template #label>触发器与动作 <small v-if="tabErrors.triggers">({{ tabErrors.triggers }})</small></template><WorkflowTriggersActionsPanel :node="node" @update:node="emit('update:node', $event)" /></el-tab-pane>
      <el-tab-pane name="interfaces"><template #label>接口与生命周期 <small v-if="tabErrors.interfaces">({{ tabErrors.interfaces }})</small></template><section class="read-only-system-skeleton"><el-table :data="node.interfaces || []" size="small"><el-table-column prop="name" label="接口" /><el-table-column prop="direction" label="方向" width="70" /><el-table-column prop="interfaceType" label="类型" width="100" /><el-table-column label="来源" width="80"><template #default="{ row }"><el-tag size="small" :type="isSystemItem(row) ? 'info' : 'success'">{{ isSystemItem(row) ? '系统' : '自定义' }}</el-tag></template></el-table-column></el-table><el-descriptions title="生命周期" :column="1" border size="small"><el-descriptions-item label="初始状态">{{ node.lifecycle?.initialStateName || '-' }}</el-descriptions-item><el-descriptions-item label="状态">{{ (node.lifecycle?.states || []).join('、') || '-' }}</el-descriptions-item></el-descriptions></section></el-tab-pane>
    </el-tabs></template>
    <template #footer><div class="drawer-footer"><el-button type="danger" plain @click="emit('remove-node')">删除节点</el-button><el-button @click="close">完成</el-button></div></template>
  </el-drawer>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import WorkflowTypedValueInput from './WorkflowTypedValueInput.vue'
import WorkflowVariablesPortsPanel from './WorkflowVariablesPortsPanel.vue'
import WorkflowTriggersActionsPanel from './WorkflowTriggersActionsPanel.vue'
import { isSystemItem, replaceCapability } from '../../../utils/workflowNodeDefinition.js'
type Item = Record<string, any>
const props = withDefaults(defineProps<{ visible: boolean, node: Item | null, errors?: Item[], deviceCapabilities?: Item[], deviceAttributes?: Item[], portConnections?: Item[] }>(), { errors: () => [], deviceCapabilities: () => [], deviceAttributes: () => [], portConnections: () => [] })
const emit = defineEmits<{ rename: [name: string], 'update:node': [node: Item], 'update:portConnections': [connections: Item[]], 'remove-node': [], close: [] }>()
const activeTab = ref('basic')
const nameDraft = ref('')
watch(() => props.node?.name, value => { nameDraft.value = value || '' }, { immediate: true })
const selectedCapability = computed(() => props.deviceCapabilities.find(item => item.capabilityName === props.node?.capability?.capabilityName))
const tabErrors = computed(() => props.errors.reduce((counts: Record<string, number>, error: Item) => { const path = error.path || ''; const tab = path.startsWith('internalVariables') || path.startsWith('ports') ? 'variables' : path.startsWith('actions') || path.startsWith('interfaces') && path.includes('bindingTriggers') ? 'triggers' : path.startsWith('interfaces') || path.startsWith('lifecycle') ? 'interfaces' : 'basic'; counts[tab] += 1; return counts }, { basic: 0, variables: 0, triggers: 0, interfaces: 0 }))
function rename() { const name = nameDraft.value.trim(); if (name && name !== props.node?.name) emit('rename', name); else nameDraft.value = props.node?.name || '' }
function close() { emit('close') }
function onVisibleChange(value: boolean) { if (!value) close() }
function updateBasic(field: string, value: unknown) { if (props.node) emit('update:node', { ...props.node, [field]: value }) }
function changeCapability(name: string) { const capability = props.deviceCapabilities.find(item => item.capabilityName === name); if (props.node && capability) emit('update:node', replaceCapability(props.node, capability, selectedCapability.value)) }
function updateParameter(name: string, value: unknown) { if (props.node) emit('update:node', { ...props.node, capability: { ...props.node.capability, capabilityParameters: { ...props.node.capability?.capabilityParameters, [name]: value } } }) }
</script>
<style scoped>
.node-form{padding-top:2px}.node-form :deep(.el-form-item){margin-bottom:10px}.basic-configuration{padding:8px 2px}.basic-configuration :deep(.el-select),.basic-configuration :deep(.el-input-number){width:100%}.read-only-system-skeleton{display:grid;gap:16px;padding:8px 2px}.drawer-footer{display:flex;justify-content:space-between;width:100%}.el-tabs small{color:var(--el-color-danger);font-weight:600}
</style>
