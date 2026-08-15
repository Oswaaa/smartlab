<template>
  <section class="resource-binding-panel">
    <header class="binding-summary">
      <div>
        <strong>绑定进度</strong>
        <span>在流程图中选择设备节点并绑定实际设备</span>
      </div>
      <div class="summary-actions">
        <div class="summary-counts">
          <el-tag type="success" effect="plain">已绑定 {{ boundCount }}</el-tag>
          <el-tag :type="unboundCount ? 'warning' : 'info'" effect="plain">待绑定 {{ unboundCount }}</el-tag>
        </div>
        <el-button v-if="unboundCount" class="btn-aliyun" size="small" @click="locateNextUnbound">定位待绑定节点</el-button>
      </div>
    </header>

    <el-alert v-if="view.errors.length" type="error" :closable="false" title="流程图与设备绑定要求不一致">
      <template #default><div v-for="error in view.errors" :key="error">{{ error }}</div></template>
    </el-alert>

    <div class="binding-workspace">
      <section class="graph-panel">
        <nav class="flow-breadcrumb" aria-label="流程路径">
          <button
            v-for="(group, index) in groupTrail"
            :key="group.groupKey"
            type="button"
            :class="{ current: index === groupTrail.length - 1 }"
            @click="openGroup(group.groupKey)"
          >{{ group.flowName }}</button>
        </nav>

        <div v-if="currentGroup" class="graph-stage">
          <VueFlow
            :key="currentGroup.groupKey"
            :nodes="flowNodes"
            :edges="flowEdges"
            :nodes-draggable="false"
            :nodes-connectable="false"
            :elements-selectable="true"
            fit-view-on-init
            :min-zoom="0.45"
            :max-zoom="1.35"
            @node-click="handleNodeClick"
          >
            <Background pattern-color="#dfe5ec" :gap="18" />
            <Controls :show-interactive="false" />
            <template #node-binding="{ data }">
              <TaskBindingWorkflowNode :data="data" />
            </template>
          </VueFlow>
          <div class="graph-legend">
            <span><i class="device"></i>设备节点可绑定</span>
            <span><i class="subflow"></i>点击进入子流程</span>
            <span><i class="logic"></i>功能节点仅展示</span>
          </div>
        </div>
        <div v-else class="graph-empty">当前流程没有可显示的节点</div>
      </section>

      <aside class="binding-editor">
        <template v-if="selectedRequirement">
          <header class="editor-header">
            <div><strong>{{ selectedRequirement.nodeName }}</strong><span>{{ selectedRequirement.occurrencePath }}</span></div>
            <el-tag :type="modelValue[selectedRequirement.slotId] ? 'success' : 'warning'" effect="plain">
              {{ modelValue[selectedRequirement.slotId] ? '已绑定' : '待绑定' }}
            </el-tag>
          </header>

          <dl class="binding-details">
            <div><dt>设备模型</dt><dd>{{ modelName(selectedRequirement.deviceModelId) }}</dd></div>
            <div><dt>设备能力</dt><dd>{{ selectedRequirement.capabilityName || '未指定' }}</dd></div>
          </dl>

          <div class="instance-field">
            <label>执行设备</label>
            <el-select
              :model-value="modelValue[selectedRequirement.slotId]"
              filterable
              clearable
              placeholder="选择兼容的设备实例"
              @change="setBinding"
            >
              <el-option
                v-for="instance in selectedInstances"
                :key="instance.id"
                :label="instanceName(instance)"
                :value="Number(instance.id)"
              >
                <span>{{ instanceName(instance) }}</span>
                <small class="instance-status">{{ instanceStatusLabel(instance) }}</small>
              </el-option>
            </el-select>
            <small v-if="!selectedInstances.length" class="field-warning">没有兼容且可用的设备实例</small>
          </div>

          <el-button
            v-if="modelValue[selectedRequirement.slotId] && sameModelUnboundCount"
            class="btn-aliyun"
            plain
            @click="applyToSameModel"
          >应用到其他同模型节点</el-button>
        </template>

        <div v-else class="editor-empty">
          <span>选择设备节点</span>
          <p>点击流程图中的设备能力节点，在此选择实际执行设备。</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import { buildBindingWorkflowView, compatibleInstances } from '../../../../utils/taskResourceBindings.js'
import TaskBindingWorkflowNode from './TaskBindingWorkflowNode.vue'

type Item = Record<string, any>
const props = withDefaults(defineProps<{
  requirements?: Item[]
  groups?: Item[]
  errors?: string[]
  modelValue: Record<string, number | null>
  instances?: Item[]
  models?: Item[]
}>(), { requirements: () => [], groups: () => [], errors: () => [], instances: () => [], models: () => [] })

const emit = defineEmits<{ 'update:modelValue': [value: Record<string, number | null>] }>()
const activeGroupKey = ref('root')
const selectedSlotId = ref('')
const view = computed(() => buildBindingWorkflowView({ groups: props.groups, errors: props.errors }, props.requirements))
const groupsByKey = computed(() => new Map(view.value.groups.map(group => [group.groupKey, group])))
const currentGroup = computed(() => groupsByKey.value.get(activeGroupKey.value) || view.value.groups[0] || null)
const selectedRequirement = computed(() => props.requirements.find(requirement => String(requirement.slotId) === selectedSlotId.value) || null)
const boundCount = computed(() => props.requirements.filter(requirement => props.modelValue[requirement.slotId]).length)
const unboundCount = computed(() => props.requirements.length - boundCount.value)
const selectedInstances = computed(() => selectedRequirement.value ? compatibleInstances(selectedRequirement.value, props.instances, props.models) : [])
const sameModelUnboundCount = computed(() => selectedRequirement.value == null ? 0 : props.requirements.filter(requirement =>
  Number(requirement.deviceModelId) === Number(selectedRequirement.value.deviceModelId) && !props.modelValue[requirement.slotId]
).length)
const groupTrail = computed(() => {
  const trail: Item[] = []
  let group = currentGroup.value
  while (group) {
    trail.unshift(group)
    group = group.parentGroupKey ? groupsByKey.value.get(group.parentGroupKey) : null
  }
  return trail
})
const flowNodes = computed(() => (currentGroup.value?.nodes || []).map((node: Item, index: number) => ({
  id: nodeId(currentGroup.value.groupKey, node.name),
  type: 'binding',
  position: node.position || { x: 70 + (index % 3) * 300, y: 70 + Math.floor(index / 3) * 150 },
  draggable: false,
  selectable: true,
  data: {
    ...node,
    selected: node.slotId != null && String(node.slotId) === selectedSlotId.value,
    bound: node.slotId != null && Boolean(props.modelValue[node.slotId]),
    boundInstanceName: node.slotId == null ? '' : instanceNameById(props.modelValue[node.slotId])
  }
})))
const flowEdges = computed(() => (currentGroup.value?.interfaceConnections || []).map((connection: Item, index: number) => ({
  id: `binding-edge:${currentGroup.value.groupKey}:${index}`,
  source: nodeId(currentGroup.value.groupKey, connection.source?.nodeName),
  target: nodeId(currentGroup.value.groupKey, connection.target?.nodeName),
  type: 'smoothstep',
  pathOptions: { offset: 26, borderRadius: 6 },
  style: { stroke: '#7890ad', strokeWidth: 1.8 }
})))

watch(() => props.groups, groups => {
  if (!groups.some(group => group.groupKey === activeGroupKey.value)) activeGroupKey.value = groups[0]?.groupKey || 'root'
  if (!props.requirements.some(requirement => String(requirement.slotId) === selectedSlotId.value)) selectedSlotId.value = ''
}, { immediate: true, deep: true })

function nodeId(groupKey: string, nodeName: string) {
  return `task-binding:${encodeURIComponent(groupKey)}:${encodeURIComponent(nodeName || '')}`
}

function handleNodeClick({ node }: { node: Item }) {
  const data = node.data || {}
  if (data.nodeType === 'DEV_NODE' && data.slotId && !data.bindingInvalid) selectedSlotId.value = String(data.slotId)
  if (data.nodeType === 'SUBFLOW_NODE') enterChildFlow(data)
}

function enterChildFlow(node: Item) {
  if (!node.childGroupKey || !groupsByKey.value.has(node.childGroupKey)) return
  activeGroupKey.value = node.childGroupKey
  selectedSlotId.value = ''
}

function openGroup(groupKey: string) {
  if (!groupsByKey.value.has(groupKey)) return
  activeGroupKey.value = groupKey
  selectedSlotId.value = ''
}

function locateNextUnbound() {
  const requirement = props.requirements.find(item => !props.modelValue[item.slotId])
  if (!requirement) return
  const group = view.value.groups.find(item => item.nodes.some((node: Item) => String(node.slotId) === String(requirement.slotId)))
  if (group) activeGroupKey.value = group.groupKey
  selectedSlotId.value = String(requirement.slotId)
}

function modelName(id: unknown) {
  return props.models.find(model => Number(model.id ?? model.modelId) === Number(id))?.modelName || `设备模型 #${id}`
}

function instanceName(instance: Item) {
  return instance.instanceName || instance.deviceName || `设备实例 #${instance.id}`
}

function instanceNameById(id: unknown) {
  const instance = props.instances.find(item => Number(item.id) === Number(id))
  return instance ? instanceName(instance) : ''
}

function instanceStatusLabel(instance: Item) {
  const status = String(instance.onlineStatus || instance.lifecycleStatus || '').toUpperCase()
  return ({ ONLINE: '在线', OFFLINE: '离线', IN_USE: '可用', RETIRED: '已停用', BUSY: '忙碌' } as Record<string, string>)[status] || '状态未知'
}

function setBinding(value: number | null) {
  if (!selectedRequirement.value) return
  emit('update:modelValue', { ...props.modelValue, [selectedRequirement.value.slotId]: value == null ? null : Number(value) })
}

function applyToSameModel() {
  if (!selectedRequirement.value) return
  const instanceId = props.modelValue[selectedRequirement.value.slotId]
  if (!instanceId) return
  const next = { ...props.modelValue }
  for (const requirement of props.requirements) {
    if (Number(requirement.deviceModelId) === Number(selectedRequirement.value.deviceModelId) && !next[requirement.slotId]) next[requirement.slotId] = instanceId
  }
  emit('update:modelValue', next)
}
</script>

<style scoped>
.resource-binding-panel{overflow:hidden;border:1px solid #dfe4ea;border-radius:6px;background:#fff}.binding-summary{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:58px;padding:10px 14px;border-bottom:1px solid #e5e9ef;background:#fafbfc;box-sizing:border-box}.binding-summary>div:first-child{display:grid;gap:3px}.binding-summary strong{color:#1f2329;font-size:13px}.binding-summary span{color:#7b8494;font-size:10px}.summary-actions,.summary-counts{display:flex;align-items:center;gap:7px}.resource-binding-panel>.el-alert{margin:12px;width:auto}.binding-workspace{display:grid;grid-template-columns:minmax(0,1fr) 286px;min-height:500px}.graph-panel{min-width:0;border-right:1px solid #e5e9ef;background:#f7f9fc}.flow-breadcrumb{height:42px;display:flex;align-items:center;gap:0;padding:0 12px;border-bottom:1px solid #e5e9ef;background:#fff}.flow-breadcrumb button{position:relative;padding:0 20px 0 6px;border:0;background:transparent;color:#687487;font-size:11px;cursor:pointer}.flow-breadcrumb button::after{content:'›';position:absolute;right:7px;color:#a0a8b4}.flow-breadcrumb button:last-child::after{display:none}.flow-breadcrumb button.current{color:#1f2937;font-weight:600;cursor:default}.graph-stage{position:relative;height:458px}.graph-stage :deep(.vue-flow){height:100%}.graph-stage :deep(.vue-flow__pane){cursor:default}.graph-stage :deep(.vue-flow__controls){border:1px solid #d8dee7;border-radius:4px;box-shadow:0 2px 7px rgba(31,45,61,.08)}.graph-stage :deep(.vue-flow__edge-path){stroke:#7890ad;stroke-width:1.8}.graph-stage :deep(.vue-flow__edge.selected .vue-flow__edge-path){stroke:#1677ff;stroke-width:2.2}.graph-legend{position:absolute;right:10px;bottom:10px;z-index:4;display:flex;align-items:center;gap:11px;padding:6px 8px;border:1px solid #dfe4ea;border-radius:4px;background:rgba(255,255,255,.94);color:#7c8796;font-size:9px}.graph-legend span{display:flex;align-items:center;gap:4px}.graph-legend i{width:7px;height:7px;border-radius:2px;background:#aeb7c4}.graph-legend i.device{background:#1677ff}.graph-legend i.subflow{background:#7053b3}.graph-empty{height:458px;display:grid;place-items:center;color:#8a94a3;font-size:11px}.binding-editor{padding:15px;background:#fff}.editor-header{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;padding-bottom:13px;border-bottom:1px solid #edf0f3}.editor-header>div{display:grid;gap:4px;min-width:0}.editor-header strong{overflow:hidden;color:#1f2937;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.editor-header span{color:#8a94a3;font-size:9px;line-height:1.5}.binding-details{display:grid;margin:0;border-bottom:1px solid #edf0f3}.binding-details>div{display:grid;grid-template-columns:66px 1fr;gap:9px;padding:10px 0}.binding-details dt,.binding-details dd{margin:0;font-size:10px}.binding-details dt{color:#8a94a3}.binding-details dd{color:#354052}.instance-field{display:grid;gap:7px;padding:14px 0}.instance-field label{color:#4f5d70;font-size:10px;font-weight:600}.instance-field :deep(.el-select){width:100%}.instance-status{float:right;margin-left:16px;color:#8a94a3}.field-warning{color:#d97706;font-size:9px}.binding-editor>.el-button{width:100%;margin-top:4px}.editor-empty{height:100%;display:grid;align-content:center;justify-items:center;padding:24px;text-align:center;box-sizing:border-box}.editor-empty span{color:#4d596a;font-size:12px;font-weight:600}.editor-empty p{max-width:210px;margin:7px 0 0;color:#8a94a3;font-size:10px;line-height:1.6}@media(max-width:1050px){.binding-workspace{grid-template-columns:1fr}.graph-panel{border-right:0;border-bottom:1px solid #e5e9ef}.binding-editor{min-height:190px}.editor-empty{min-height:150px}}@media(max-width:760px){.binding-summary{align-items:flex-start;flex-direction:column}.summary-actions{width:100%;justify-content:space-between}.graph-stage,.graph-empty{height:390px}.graph-legend{display:none}}
</style>
