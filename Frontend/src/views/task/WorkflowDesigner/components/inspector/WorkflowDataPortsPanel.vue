<template>
  <section class="data-ports-panel">
    <div class="panel-heading"><div><strong>数据端口</strong><span>端口读写绑定的内部变量，紫色连接用于节点间传递数据</span></div><el-button v-if="!readonly" class="btn-aliyun" size="small" @click="addPort">新增端口</el-button></div>
    <el-table v-if="ports.length" :data="ports" class="editable-config-table" table-layout="fixed">
      <el-table-column label="端口名称" width="260"><template #default="{ row }"><el-input :model-value="row.name" placeholder="端口名" :disabled="readonly || isSystemItem(row)" @update:model-value="updatePort(row, 'name', $event)" /></template></el-table-column>
      <el-table-column label="方向" width="112"><template #default="{ row }"><el-select :model-value="row.direction" :disabled="readonly || isSystemItem(row)" @update:model-value="updatePort(row, 'direction', $event)"><el-option label="输入" value="IN" /><el-option label="输出" value="OUT" /></el-select></template></el-table-column>
      <el-table-column label="绑定变量" width="360"><template #default="{ row }"><el-select :model-value="row.internalVariableName" :disabled="readonly || isSystemItem(row)" placeholder="选择节点变量" @update:model-value="updatePort(row, 'internalVariableName', $event)"><el-option v-for="variable in node.internalVariables || []" :key="variable.name" :label="`${variable.name} · ${variable.dataType}`" :value="variable.name" /></el-select></template></el-table-column>
      <el-table-column min-width="40" />
      <el-table-column label="操作" width="88" align="right"><template #default="{ row }"><el-tag v-if="isSystemItem(row)" size="small" type="info">系统</el-tag><el-button v-else-if="!readonly" class="btn-aliyun-danger-link" link @click="emit('remove-port-request', row.name)">删除</el-button></template></el-table-column>
    </el-table>
    <WorkflowConfigurationEmpty v-else title="尚未配置数据端口" description="数据端口用于在节点之间传递内部变量的当前值。" :action-label="readonly ? '' : '新增端口'" @action="addPort" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WorkflowConfigurationEmpty from './WorkflowConfigurationEmpty.vue'
import { isSystemItem } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, readonly?: boolean }>(), { readonly: false })
const emit = defineEmits<{ 'update:node': [node: Item], 'remove-port-request': [portName: string] }>()
const ports = computed(() => props.node.ports || [])
function publish(nextPorts: Item[]) { if (!props.readonly) emit('update:node', { ...props.node, ports: nextPorts }) }
function uniqueName() { let index = 1; while (ports.value.some((item: Item) => item.name === `port${index}`)) index += 1; return `port${index}` }
function addPort() { publish([...ports.value, { name: uniqueName(), direction: 'IN', internalVariableName: props.node.internalVariables?.[0]?.name || '' }]) }
function updatePort(target: Item, field: string, value: unknown) { publish(ports.value.map((item: Item) => item === target ? { ...item, [field]: value } : item)) }
</script>

<style scoped>
.data-ports-panel{display:grid;gap:0;padding:20px}.panel-heading{min-height:48px;display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:14px}.panel-heading>div{display:grid;gap:3px}.panel-heading strong{color:#1f2329;font-size:14px;font-weight:600}.panel-heading span{color:#8f959e;font-size:12px}.editable-config-table{width:100%;border:1px solid #e5e6eb}.editable-config-table :deep(.el-table__inner-wrapper::before){display:none}.editable-config-table :deep(.el-table__header th.el-table__cell){height:40px;padding:0;background:#f5f7fa;color:#646a73;font-size:12px;font-weight:500}.editable-config-table :deep(.el-table__body td.el-table__cell){height:56px;padding:8px 0}.editable-config-table :deep(.el-table__cell .cell){padding:0 12px}.editable-config-table :deep(.el-select){width:100%}
</style>
