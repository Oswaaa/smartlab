<template>
  <div class="resource-binding-layout">
    <section class="workflow-instance-canvas">
      <header class="canvas-header">
        <div><strong>流程实例化视图</strong><span>每个路径代表一次真实的节点出现位置</span></div>
        <div class="canvas-summary"><el-tag type="success" effect="plain">已绑定{{ boundCount }}</el-tag><el-tag :type="unboundCount ? 'warning' : 'info'" effect="plain">待绑定{{ unboundCount }}</el-tag></div>
      </header>
      <el-alert v-if="errors.length" type="error" :closable="false" title="流程模型的设备接口连接不完整"><template #default><div v-for="error in errors" :key="error">{{ error }}</div></template></el-alert>
      <div class="flow-groups">
        <article v-for="group in groups" :key="group.groupKey" class="flow-group" :style="{ marginLeft: Math.min(group.depth, 4) * 20 + 'px' }">
          <header class="flow-group-header"><div><el-tag size="small" effect="plain">{{ group.depth ? '子流程' : '主流程' }}</el-tag><strong>{{ group.flowName }}</strong></div><code>{{ group.occurrencePath }}</code></header>
          <div class="node-strip">
            <template v-for="node in group.nodes" :key="node.occurrenceKey">
              <button type="button" :class="['workflow-node', node.nodeType.toLowerCase(), { selected: selectedKey === node.bindingKey, bound: node.bindingKey && modelValue[node.bindingKey] }]" :disabled="node.nodeType !== 'DEV_NODE'" @click="selectNode(node)">
                <span class="node-type">{{ nodeTypeLabel(node.nodeType) }}</span>
                <strong>{{ node.name }}</strong>
                <small v-if="node.nodeType === 'DEV_NODE'">{{ modelName(node.deviceModelId) }}</small>
                <small v-else-if="node.nodeType === 'SUBFLOW_NODE'">展开到下方子流程</small>
                <small v-else>{{ node.functionType || '流程逻辑' }}</small>
                <em v-if="node.bindingKey">{{ modelValue[node.bindingKey] ? instanceName(modelValue[node.bindingKey]) : '点击绑定实例' }}</em>
              </button>
              <span v-if="index < group.nodes.length - 1" class="node-arrow">→</span>
            </template>
          </div>
          <div v-if="group.connections.length" class="connection-list"><span v-for="connection in group.connections" :key="connection.sourceNodeName + '>' + connection.targetNodeName">{{ connection.sourceNodeName }}→{{ connection.targetNodeName }}</span></div>
        </article>
      </div>
    </section>

    <aside class="binding-panel">
      <template v-if="selectedRoute">
        <header><span>设备实例绑定</span><el-tag :type="modelValue[selectedRoute.bindingKey] ? 'success' : 'warning'">{{ modelValue[selectedRoute.bindingKey] ? '已绑定' : '待绑定' }}</el-tag></header>
        <dl><dt>节点路径</dt><dd><code>{{ selectedRoute.bindingKey }}</code></dd><dt>设备模型</dt><dd>{{ modelName(selectedRoute.deviceModelId) }}</dd><dt>状态机接口</dt><dd>{{ selectedRoute.deviceInputInterfaceName }}<br>{{ selectedRoute.deviceOutputInterfaceName }}</dd></dl>
        <el-select :model-value="modelValue[selectedRoute.bindingKey]" filterable clearable placeholder="选择可用设备实例" style="width:100%" @change="setBinding">
          <el-option v-for="instance in instancesForRoute" :key="instance.id" :label="instance.instanceName || instance.deviceName || ('设备实例#' + instance.id)" :value="Number(instance.id)"><span>{{ instance.instanceName || instance.deviceName || ('设备实例#' + instance.id) }}</span><small class="instance-option">{{ instance.onlineStatus || '在线状态未知' }}</small></el-option>
        </el-select>
        <el-checkbox v-model="inheritRepeatedSubflows" class="inherit-checkbox">同一子流程再次出现时默认继承该绑定</el-checkbox>
        <el-button native-type="button" plain style="width:100%" @click="applyToSameModel">应用到所有未绑定的同模型节点</el-button>
        <p class="binding-help">子流程节点本身不绑定设备，系统展开其内部DEV_NODE。继承只用于预填，所有节点路径仍独立保存，用户可单独覆盖</p>
      </template>
      <el-empty v-else description="点击画布中的设备节点开始绑定" :image-size="72" />
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { applyInheritedBinding } from '../../utils/taskResourceBindings.js'

const props = defineProps<{
  groups: any[]
  routes: any[]
  errors: string[]
  modelValue: Record<string, number | null>
  instances: any[]
  models: any[]
}>()
const emit = defineEmits<{ (event: 'update:modelValue', value: Record<string, number | null>): void }>()
const selectedKey = ref('')
const inheritRepeatedSubflows = ref(true)
const selectedRoute = computed(() => props.routes.find(route => route.bindingKey === selectedKey.value) || null)
const boundCount = computed(() => props.routes.filter(route => props.modelValue[route.bindingKey]).length)
const unboundCount = computed(() => props.routes.length - boundCount.value)
const instancesForRoute = computed(() => selectedRoute.value == null ? [] : props.instances.filter(instance => Number(instance.deviceModelId ?? instance.modelId) === Number(selectedRoute.value.deviceModelId) && (!instance.lifecycleStatus || instance.lifecycleStatus === 'IN_USE')))

watch(() => props.routes, routes => {
  if (!routes.some(route => route.bindingKey === selectedKey.value)) selectedKey.value = routes.find(route => !props.modelValue[route.bindingKey])?.bindingKey || routes[0]?.bindingKey || ''
}, { immediate: true, deep: true })

function selectNode(node: any) { if (node.nodeType === 'DEV_NODE') selectedKey.value = node.bindingKey }
function nodeTypeLabel(type: string) { return ({ DEV_NODE: '设备', SUBFLOW_NODE: '子流程', FUNC_NODE: '功能' } as Record<string, string>)[type] || type }
function modelName(id: any) { return props.models.find(model => Number(model.id ?? model.modelId) === Number(id))?.modelName || `设备模型#${id}` }
function instanceName(id: any) { const instance = props.instances.find(item => Number(item.id) === Number(id)); return instance?.instanceName || instance?.deviceName || `实例#${id}` }
function setBinding(value: number | null) {
  if (!selectedRoute.value) return
  if (value == null) { emit('update:modelValue', { ...props.modelValue, [selectedRoute.value.bindingKey]: null }); return }
  const next = inheritRepeatedSubflows.value && selectedRoute.value.depth > 0
    ? applyInheritedBinding(props.routes, props.modelValue, selectedRoute.value, Number(value))
    : { ...props.modelValue, [selectedRoute.value.bindingKey]: Number(value) }
  emit('update:modelValue', next)
}
function applyToSameModel() {
  if (!selectedRoute.value) return
  const value = props.modelValue[selectedRoute.value.bindingKey]
  if (!value) return
  const next = { ...props.modelValue }
  for (const route of props.routes) if (Number(route.deviceModelId) === Number(selectedRoute.value.deviceModelId) && !next[route.bindingKey]) next[route.bindingKey] = value
  emit('update:modelValue', next)
}
</script>

<style scoped>
.resource-binding-layout{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:14px;min-height:420px}.workflow-instance-canvas,.binding-panel{border:1px solid #dfe4ea;border-radius:8px;background:#fff}.workflow-instance-canvas{min-width:0;background:#f7f9fc}.canvas-header{display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border-bottom:1px solid #dfe4ea;background:#fff}.canvas-header>div:first-child{display:grid;gap:3px}.canvas-header span{font-size:12px;color:#64748b}.canvas-summary{display:flex;gap:6px}.flow-groups{display:grid;gap:12px;padding:14px;overflow:auto;max-height:560px}.flow-group{border:1px solid #dfe4ea;border-radius:7px;background:#fff;box-shadow:0 1px 2px rgba(15,23,42,.04)}.flow-group-header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:9px 12px;border-bottom:1px solid #edf0f4}.flow-group-header>div{display:flex;align-items:center;gap:8px}.flow-group-header code{font-size:11px;color:#64748b}.node-strip{display:flex;align-items:center;gap:8px;padding:14px;overflow-x:auto}.workflow-node{display:grid;gap:5px;min-width:150px;max-width:190px;padding:10px 12px;text-align:left;border:1px solid #cfd6df;border-radius:7px;background:#fff;color:#1f2937;cursor:pointer}.workflow-node:disabled{cursor:default;opacity:1}.workflow-node.dev_node:hover,.workflow-node.selected{border-color:#1677ff;box-shadow:0 0 0 2px rgba(22,119,255,.12)}.workflow-node.bound{border-left:4px solid #22a06b}.workflow-node.subflow_node{background:#f5f3ff;border-color:#d8b4fe}.workflow-node.func_node{background:#fffaf0;border-color:#f5d28b}.node-type{font-size:11px;color:#64748b}.workflow-node small{color:#64748b}.workflow-node em{font-size:11px;color:#1677ff;font-style:normal}.connection-list{display:flex;flex-wrap:wrap;gap:6px;padding:0 14px 12px}.connection-list span{padding:3px 7px;border-radius:4px;background:#eef2f7;color:#5b6472;font-size:11px}.binding-panel{padding:16px;align-self:start;position:sticky;top:0}.binding-panel>header{display:flex;align-items:center;justify-content:space-between;font-weight:600}.binding-panel dl{display:grid;grid-template-columns:72px 1fr;gap:9px;margin:18px 0;font-size:12px}.binding-panel dt{color:#64748b}.binding-panel dd{margin:0;min-width:0;color:#1f2937}.binding-panel code{overflow-wrap:anywhere}.inherit-checkbox{margin:12px 0 8px}.binding-help{margin:12px 0 0;color:#64748b;font-size:11px;line-height:1.6}.instance-option{float:right;color:#8a93a6;margin-left:16px}@media(max-width:1000px){.resource-binding-layout{grid-template-columns:1fr}.binding-panel{position:static}}
</style>
