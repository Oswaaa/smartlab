<template>
  <section class="variables-panel">
    <div class="panel-heading"><div><strong>变量空间</strong><span>节点内部变量，可供表达式、数据端口和触发器引用</span></div><el-button v-if="!readonly" class="btn-aliyun" size="small" @click="addVariable">新增变量</el-button></div>
    <el-table v-if="variables.length" :data="variables" class="editable-config-table" table-layout="fixed">
      <el-table-column label="变量名称" min-width="220">
        <template #default="{ row }"><el-input :model-value="row.name" placeholder="变量名" :disabled="readonly || isSystemItem(row)" @update:model-value="updateVariable(row, 'name', $event)" /></template>
      </el-table-column>
      <el-table-column label="数据类型" width="160">
        <template #default="{ row }"><el-select :model-value="row.dataType" :disabled="readonly || isSystemItem(row)" @update:model-value="updateVariable(row, 'dataType', $event)"><el-option v-for="type in dataTypes" :key="type" :label="type" :value="type" /></el-select></template>
      </el-table-column>
      <el-table-column v-if="node.nodeType === 'DEV_NODE'" label="设备属性映射" min-width="240">
        <template #default="{ row }"><el-select :model-value="row.attributesMapping" clearable :disabled="readonly || isSystemItem(row)" placeholder="绑定设备属性" @update:model-value="mapAttribute(row, $event)"><el-option v-for="attribute in deviceAttributes" :key="attribute.attributeName" :label="attribute.displayName || attribute.attributeName" :value="attribute.attributeName" /></el-select></template>
      </el-table-column>
      <el-table-column label="操作" width="88" align="right">
        <template #default="{ row }"><el-tag v-if="isSystemItem(row)" size="small" type="info">系统</el-tag><el-button v-else-if="!readonly" class="btn-aliyun-danger-link" link @click="deleteVariable(row.name)">删除</el-button></template>
      </el-table-column>
    </el-table>
    <WorkflowConfigurationEmpty v-else title="尚未配置内部变量" description="内部变量可被计算表达式、数据端口和控制触发器引用。" :action-label="readonly ? '' : '新增变量'" @action="addVariable" />
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import WorkflowConfigurationEmpty from './WorkflowConfigurationEmpty.vue'
import { isSystemItem, removeVariable } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, readonly?: boolean, deviceAttributes?: Item[] }>(), { readonly: false, deviceAttributes: () => [] })
const emit = defineEmits<{ 'update:node': [node: Item] }>()
const dataTypes = ['INTEGER', 'DOUBLE', 'STRING', 'BOOLEAN', 'JSON']
const message = ref('')
const variables = computed(() => props.node.internalVariables || [])

function publish(internalVariables: Item[]) { if (props.readonly) return; message.value = ''; emit('update:node', { ...props.node, internalVariables }) }
function uniqueName() { let index = 1; while (variables.value.some((item: Item) => item.name === `variable${index}`)) index += 1; return `variable${index}` }
function addVariable() { publish([...variables.value, { name: uniqueName(), dataType: 'STRING' }]) }
function updateVariable(target: Item, field: string, value: unknown) {
  publish(variables.value.map((item: Item) => item === target ? { ...item, [field]: value } : item))
}
function mapAttribute(target: Item, attributeName: string) {
  const attribute = props.deviceAttributes.find(item => item.attributeName === attributeName)
  publish(variables.value.map((item: Item) => item === target ? { ...item, attributesMapping: attributeName, ...(attribute?.dataType ? { dataType: attribute.dataType } : {}) } : item))
}
function deleteVariable(name: string) {
  try { publish(removeVariable(props.node, name).internalVariables || []) } catch (error: any) { message.value = error.message }
}
</script>

<style scoped>
.variables-panel{display:grid;gap:0;padding:20px}.panel-heading{min-height:48px;display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:14px}.panel-heading>div{display:grid;gap:3px}.panel-heading strong{color:#1f2329;font-size:14px;font-weight:600}.panel-heading span{color:#8f959e;font-size:12px}.editable-config-table{width:100%;border:1px solid #e5e6eb}.editable-config-table :deep(.el-table__inner-wrapper::before){display:none}.editable-config-table :deep(.el-table__header th.el-table__cell){height:40px;padding:0;background:#f5f7fa;color:#646a73;font-size:12px;font-weight:500}.editable-config-table :deep(.el-table__body td.el-table__cell){height:56px;padding:8px 0}.editable-config-table :deep(.el-table__cell .cell){padding:0 12px}.editable-config-table :deep(.el-select){width:100%}.editable-config-table :deep(.el-tag){vertical-align:middle}
</style>
