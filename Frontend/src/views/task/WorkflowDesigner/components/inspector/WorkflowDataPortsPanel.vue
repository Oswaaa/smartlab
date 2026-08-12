<template>
  <section class="data-ports-panel">
    <div class="panel-heading"><div><strong>数据端口</strong><span>端口读写绑定的内部变量，紫色连接用于节点间传递数据</span></div><el-button class="btn-aliyun" size="small" @click="addPort">新增端口</el-button></div>
    <div v-for="port in ports" :key="port.name || port._systemKey" class="editor-row">
      <el-input :model-value="port.name" placeholder="端口名" :disabled="isSystemItem(port)" @update:model-value="updatePort(port, 'name', $event)" />
      <el-select :model-value="port.direction" :disabled="isSystemItem(port)" @update:model-value="updatePort(port, 'direction', $event)"><el-option label="IN" value="IN" /><el-option label="OUT" value="OUT" /></el-select>
      <el-select :model-value="port.internalVariableName" :disabled="isSystemItem(port)" placeholder="选择节点变量" @update:model-value="updatePort(port, 'internalVariableName', $event)">
        <el-option v-for="variable in node.internalVariables || []" :key="variable.name" :label="`${variable.name} · ${variable.dataType}`" :value="variable.name" />
      </el-select>
      <el-tag v-if="isSystemItem(port)" size="small" type="info">系统</el-tag>
      <el-button v-else class="btn-aliyun-danger-link" link @click="emit('remove-port-request', port.name)">删除</el-button>
    </div>
    <el-empty v-if="!ports.length" description="尚未定义数据端口" :image-size="40" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { isSystemItem } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = defineProps<{ node: Item }>()
const emit = defineEmits<{ 'update:node': [node: Item], 'remove-port-request': [portName: string] }>()
const ports = computed(() => props.node.ports || [])
function publish(nextPorts: Item[]) { emit('update:node', { ...props.node, ports: nextPorts }) }
function uniqueName() { let index = 1; while (ports.value.some((item: Item) => item.name === `port${index}`)) index += 1; return `port${index}` }
function addPort() { publish([...ports.value, { name: uniqueName(), direction: 'IN', internalVariableName: props.node.internalVariables?.[0]?.name || '' }]) }
function updatePort(target: Item, field: string, value: unknown) { publish(ports.value.map((item: Item) => item === target ? { ...item, [field]: value } : item)) }
</script>

<style scoped>
.data-ports-panel{display:grid;gap:9px;padding:12px}.panel-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.panel-heading>div{display:grid;gap:2px}.panel-heading strong{font-size:12px}.panel-heading span{color:#8490a0;font-size:10px}.editor-row{display:grid;grid-template-columns:minmax(100px,1fr) 88px minmax(150px,1fr) auto;gap:8px;align-items:center}.editor-row :deep(.el-select){width:100%}@media(max-width:520px){.editor-row{grid-template-columns:1fr 1fr}.editor-row :nth-child(3){grid-column:1 / -1}.editor-row :last-child{justify-self:end}}
</style>
