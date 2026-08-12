<template>
  <section class="variables-panel">
    <div class="panel-heading"><div><strong>变量空间</strong><span>节点内部变量，可供表达式、数据端口和触发器引用</span></div><el-button class="btn-aliyun" size="small" @click="addVariable">新增变量</el-button></div>
    <div v-for="variable in variables" :key="variable.name || variable._systemKey" class="editor-row">
      <el-input :model-value="variable.name" placeholder="变量名" :disabled="isSystemItem(variable)" @update:model-value="updateVariable(variable, 'name', $event)" />
      <el-select :model-value="variable.dataType" :disabled="isSystemItem(variable)" @update:model-value="updateVariable(variable, 'dataType', $event)">
        <el-option v-for="type in dataTypes" :key="type" :label="type" :value="type" />
      </el-select>
      <el-select v-if="node.nodeType === 'DEV_NODE'" :model-value="variable.attributesMapping" clearable :disabled="isSystemItem(variable)" placeholder="绑定设备属性" @update:model-value="mapAttribute(variable, $event)">
        <el-option v-for="attribute in deviceAttributes" :key="attribute.attributeName" :label="attribute.displayName || attribute.attributeName" :value="attribute.attributeName" />
      </el-select>
      <el-tag v-if="isSystemItem(variable)" size="small" type="info">系统</el-tag>
      <el-button v-else class="btn-aliyun-danger-link" link @click="deleteVariable(variable.name)">删除</el-button>
    </div>
    <el-empty v-if="!variables.length" description="尚未定义内部变量" :image-size="40" />
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { isSystemItem, removeVariable } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, deviceAttributes?: Item[] }>(), { deviceAttributes: () => [] })
const emit = defineEmits<{ 'update:node': [node: Item] }>()
const dataTypes = ['INTEGER', 'DOUBLE', 'STRING', 'BOOLEAN', 'JSON']
const message = ref('')
const variables = computed(() => props.node.internalVariables || [])

function publish(internalVariables: Item[]) { message.value = ''; emit('update:node', { ...props.node, internalVariables }) }
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
.variables-panel{display:grid;gap:9px;padding:12px}.panel-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.panel-heading>div{display:grid;gap:2px}.panel-heading strong{font-size:12px}.panel-heading span{color:#8490a0;font-size:10px}.editor-row{display:grid;grid-template-columns:minmax(100px,1fr) 104px minmax(120px,1fr) auto;gap:8px;align-items:center}.editor-row :deep(.el-select){width:100%}@media(max-width:520px){.editor-row{grid-template-columns:1fr 1fr}.editor-row :last-child{justify-self:end}}
</style>
