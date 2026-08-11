<template>
  <section class="variables-ports-panel">
    <div class="panel-heading"><strong>内部变量</strong><el-button size="small" @click="addVariable">添加变量</el-button></div>
    <div v-for="variable in editableVariables" :key="variable.name" class="editor-row">
      <el-input v-model="variable.name" placeholder="变量名" @change="emitNode" />
      <el-select v-model="variable.dataType" placeholder="数据类型" @change="emitNode">
        <el-option v-for="type in dataTypes" :key="type" :label="type" :value="type" />
      </el-select>
      <el-select v-if="node.nodeType === 'DEV_NODE'" v-model="variable.attributesMapping" clearable placeholder="设备属性" @change="syncAttributeType(variable)">
        <el-option v-for="attribute in deviceAttributes" :key="attribute.attributeName" :label="attribute.displayName || attribute.attributeName" :value="attribute.attributeName" />
      </el-select>
      <el-button link type="danger" @click="deleteVariable(variable.name)">删除</el-button>
    </div>
    <el-empty v-if="!editableVariables.length" description="尚未定义内部变量" :image-size="40" />

    <div class="panel-heading ports-heading"><strong>数据端口</strong><el-button size="small" @click="addPort">添加端口</el-button></div>
    <div v-for="port in editablePorts" :key="port.name" class="editor-row">
      <el-input v-model="port.name" placeholder="端口名" @change="emitNode" />
      <el-select v-model="port.direction" @change="emitNode"><el-option label="IN" value="IN" /><el-option label="OUT" value="OUT" /></el-select>
      <el-select v-model="port.internalVariableName" placeholder="选择当前节点变量" @change="emitNode">
        <el-option v-for="variable in editableVariables" :key="variable.name" :label="variable.name" :value="variable.name" />
      </el-select>
      <el-button link type="danger" @click="deletePort(port.name)">删除</el-button>
    </div>
    <el-empty v-if="!editablePorts.length" description="尚未定义数据端口" :image-size="40" />
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { isSystemItem, removePort, removeVariable } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>

const props = defineProps<{ node: Item, deviceAttributes: Item[], portConnections: Item[] }>()
const emit = defineEmits<{
  'update:node': [node: Item]
  'update:portConnections': [connections: Item[]]
  'validation-change': [state: { valid: boolean, message: string }]
  'remove-port-request': [portName: string]
}>()
const dataTypes = ['INTEGER', 'DOUBLE', 'STRING', 'BOOLEAN', 'JSON']
const message = ref('')

const editableVariables = computed(() => (props.node.internalVariables || []).filter((item: Item) => !isSystemItem(item)))
const editablePorts = computed(() => (props.node.ports || []).filter((item: Item) => !isSystemItem(item)))

function publishError(nextMessage = '') {
  message.value = nextMessage
  emit('validation-change', { valid: !nextMessage, message: nextMessage })
}

function emitNode() {
  publishError()
  emit('update:node', { ...props.node, internalVariables: [...(props.node.internalVariables || [])], ports: [...(props.node.ports || [])] })
}

function addVariable() {
  const name = uniqueName('variable', props.node.internalVariables || [])
  props.node.internalVariables = [...(props.node.internalVariables || []), { name, dataType: 'STRING' }]
  emitNode()
}

function addPort() {
  const name = uniqueName('port', props.node.ports || [])
  props.node.ports = [...(props.node.ports || []), { name, direction: 'IN', internalVariableName: editableVariables.value[0]?.name || '' }]
  emitNode()
}

function uniqueName(prefix: string, items: Item[]) {
  let index = 1
  while (items.some(item => item.name === `${prefix}${index}`)) index += 1
  return `${prefix}${index}`
}

function syncAttributeType(variable: Item) {
  const attribute = props.deviceAttributes.find(item => item.attributeName === variable.attributesMapping)
  if (attribute) variable.dataType = attribute.dataType
  emitNode()
}

function deleteVariable(name: string) {
  try {
    const next = removeVariable(props.node, name)
    publishError()
    emit('update:node', next)
  } catch (error: any) {
    publishError(error.message)
  }
}

function connectionsFor(portName: string) {
  return props.portConnections.filter(connection => connection.portName === portName || connection.sourcePortName === portName || connection.targetPortName === portName || (connection.source?.nodeName === props.node.name && connection.source?.portName === portName) || (connection.target?.nodeName === props.node.name && connection.target?.portName === portName))
}

function deletePort(name: string) {
  if (connectionsFor(name).length) {
    emit('remove-port-request', name)
    return
  }
  try {
    const next = removePort(props.node, name, props.portConnections)
    publishError()
    emit('update:node', next.node)
    emit('update:portConnections', next.portConnections)
  } catch (error: any) {
    publishError(error.message)
  }
}
</script>

<style scoped>
.variables-ports-panel{display:grid;gap:10px}.panel-heading{display:flex;align-items:center;justify-content:space-between}.ports-heading{margin-top:14px;padding-top:14px;border-top:1px solid var(--el-border-color-lighter)}.editor-row{display:grid;grid-template-columns:minmax(96px,1fr) 100px minmax(120px,1fr) auto;gap:8px;align-items:center}.editor-row :deep(.el-select){width:100%}@media(max-width:520px){.editor-row{grid-template-columns:1fr 1fr}.editor-row :last-child{justify-self:end}}
</style>
