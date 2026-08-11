<template>
  <section class="interfaces-ports-panel">
    <div class="panel-section">
      <div class="panel-heading"><strong>接口</strong><span>系统接口只读，自定义接口可编辑</span></div>
      <div v-for="item in nodeInterfaces" :key="item.name || item._systemKey" class="interface-card">
        <div class="card-head">
          <strong>{{ item.name || item._systemKey }}</strong>
          <el-tag size="small" :type="isSystemItem(item) ? 'info' : 'success'">{{ isSystemItem(item) ? '系统' : '自定义' }}</el-tag>
          <el-icon v-if="isSystemItem(item)" title="系统接口，禁止编辑"><Lock /></el-icon>
        </div>
        <div class="form-grid">
          <div class="form-field">
            <label>名称</label>
            <el-input :model-value="item.name" placeholder="接口名" :disabled="isSystemItem(item)" @update:model-value="updateInterface(item, 'name', $event)" />
          </div>
          <div class="form-field">
            <label>方向</label>
            <el-select :model-value="item.direction" :disabled="isSystemItem(item)" @update:model-value="updateInterface(item, 'direction', $event)">
              <el-option label="IN" value="IN" />
              <el-option label="OUT" value="OUT" />
            </el-select>
          </div>
          <div class="form-field">
            <label>类型</label>
            <el-select :model-value="item.interfaceType" :disabled="isSystemItem(item)" @update:model-value="updateInterface(item, 'interfaceType', $event)">
              <el-option label="WORKFLOW" value="WORKFLOW" />
              <el-option label="STATE" value="STATE" />
              <el-option label="ADAPTER" value="ADAPTER" />
              <el-option label="CONTROL" value="CONTROL" />
              <el-option label="CONSTRAINT" value="CONSTRAINT" />
            </el-select>
          </div>
          <div v-if="item.direction === 'OUT'" class="form-field full-width">
            <label>允许信号</label>
            <el-input :model-value="(item.allowedSignals || []).join(', ')" placeholder="逗号分隔信号名" :disabled="isSystemItem(item)" @update:model-value="updateInterface(item, 'allowedSignals', $event.split(',').map(s => s.trim()).filter(Boolean))" />
          </div>
        </div>
        <div v-if="!isSystemItem(item)" class="card-footer">
          <el-button link type="danger" @click="deleteInterface(item)">删除接口</el-button>
        </div>
      </div>
      <el-button size="small" class="add-button" @click="addInterface">添加接口</el-button>
      <el-empty v-if="!nodeInterfaces.length" description="尚未定义接口" :image-size="36" />
    </div>

    <div class="panel-section ports-section">
      <div class="panel-heading"><strong>数据端口</strong><span>端口连接内部变量后可在节点间传递数据</span></div>
      <div v-for="item in nodePorts" :key="item.name || item._systemKey" class="port-card">
        <div class="card-head">
          <strong>{{ item.name || item._systemKey }}</strong>
          <el-icon v-if="isSystemItem(item)" title="系统端口，禁止编辑"><Lock /></el-icon>
        </div>
        <div class="form-grid">
          <div class="form-field">
            <label>名称</label>
            <el-input :model-value="item.name" placeholder="端口名" :disabled="isSystemItem(item)" @update:model-value="updatePort(item, 'name', $event)" />
          </div>
          <div class="form-field">
            <label>方向</label>
            <el-select :model-value="item.direction" :disabled="isSystemItem(item)" @update:model-value="updatePort(item, 'direction', $event)">
              <el-option label="IN" value="IN" />
              <el-option label="OUT" value="OUT" />
            </el-select>
          </div>
          <div class="form-field full-width">
            <label>绑定内部变量</label>
            <el-select :model-value="item.internalVariableName" :disabled="isSystemItem(item)" filterable allow-create default-first-option placeholder="选择或输入变量名" @update:model-value="updatePort(item, 'internalVariableName', $event)">
              <el-option v-for="variable in node.internalVariables || []" :key="variable.name" :label="variable.name" :value="variable.name" />
            </el-select>
          </div>
        </div>
        <div v-if="!isSystemItem(item)" class="card-footer">
          <el-button link type="danger" @click="deletePort(item)">删除端口</el-button>
        </div>
      </div>
      <el-button size="small" class="add-button" @click="addPort">添加端口</el-button>
      <el-empty v-if="!nodePorts.length" description="尚未定义数据端口" :image-size="36" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { isSystemItem, removeInterface } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>

const props = withDefaults(defineProps<{ node: Item, interfaceConnections?: Item[] }>(), { interfaceConnections: () => [] })
const emit = defineEmits<{ 'update:node': [node: Item], 'update:interfaceConnections': [connections: Item[]], 'remove-port-request': [portName: string] }>()

const nodeInterfaces = computed(() => props.node.interfaces || [])
const nodePorts = computed(() => props.node.ports || [])

function publish(next: Item) { emit('update:node', next) }

function uniqueItemName(prefix: string, items: Item[], key = 'name') {
  let index = 1
  while (items.some((item: Item) => item[key] === prefix + index)) index += 1
  return prefix + index
}

function updateInterface(target: Item, field: string, value: unknown) {
  const interfaces = nodeInterfaces.value.map((item: Item) =>
    (item.name || item._systemKey) === (target.name || target._systemKey) ? { ...item, [field]: value } : item)
  publish({ ...props.node, interfaces })
}

function addInterface() {
  const name = uniqueItemName('interface', nodeInterfaces.value)
  const item = { name, direction: props.node.functionType === 'BRANCH' ? 'OUT' : 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['ACTIVE'], bindingTriggers: [] }
  publish({ ...props.node, interfaces: [...nodeInterfaces.value, item] })
}

function deleteInterface(target: Item) {
  const result = removeInterface(props.node, target.name, props.interfaceConnections)
  publish(result.node)
  emit('update:interfaceConnections', result.interfaceConnections)
}

function updatePort(target: Item, field: string, value: unknown) {
  const ports = nodePorts.value.map((item: Item) =>
    (item.name || item._systemKey) === (target.name || target._systemKey) ? { ...item, [field]: value } : item)
  publish({ ...props.node, ports })
}

function addPort() {
  const name = uniqueItemName('port', nodePorts.value)
  const item = { name, direction: 'IN', internalVariableName: '' }
  publish({ ...props.node, ports: [...nodePorts.value, item] })
}

function deletePort(target: Item) {
  emit('remove-port-request', target.name || target._systemKey)
}
</script>

<style scoped>
.interfaces-ports-panel{display:grid;gap:0}.panel-section{padding:10px 12px;border-bottom:1px solid #e8ebf0}.ports-section{border-bottom:0}.panel-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.panel-heading strong{font-size:12px}.panel-heading span{color:#8a96a5;font-size:10px}.interface-card,.port-card{display:grid;gap:6px;padding:8px;margin-bottom:7px;border:1px solid #e1e6ec;border-radius:4px;background:#fafcfd}.card-head{display:flex;align-items:center;gap:8px}.card-head strong{font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.card-head .el-icon{color:#8f9aab}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px}.form-field{display:grid;gap:3px;min-width:0}.form-field.full-width{grid-column:1 / -1}.form-field label{color:#667385;font-size:10px}.form-field :deep(.el-select),.form-field :deep(.el-input){width:100%}.card-footer{display:flex;justify-content:flex-end}.add-button{width:100%;margin-top:4px;border:1px dashed #cdd5e0;color:#617083}.add-button:hover{border-color:#6f8fbf;color:#2d619a;background:#f3f8fd}
</style>
