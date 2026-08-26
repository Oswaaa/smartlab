<template>
  <section class="resource-binding-panel">
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

        <div v-if="canvasReady" class="graph-stage">
          <WorkflowStageGraph
            :key="currentGroup?.groupKey || 'empty'"
            :nodes="currentGroup?.nodes || []"
            :interface-connections="currentGroup?.interfaceConnections || []"
            :port-connections="currentGroup?.portConnections || []"
            :selected-node-name="selectedNodeName"
            :min-zoom="0.45"
            :max-zoom="1.35"
            :overlay-for="bindingOverlay"
            :capabilities-for="capabilitiesFor"
            :issues-for="issuesFor"
            :legend-items="legendItems"
            @node-click="handleNodeClick"
          />
        </div>
        <div v-else class="graph-empty">正在加载流程图</div>
      </section>

      <aside class="binding-editor">
        <div class="binding-progress">
          <span>绑定进度　<b>已绑定 {{ boundCount }}</b> · 待绑定 {{ unboundCount }}</span>
          <button v-if="unboundCount" class="btn-link" type="button" @click="locateNextUnbound">定位待绑定</button>
        </div>
        <template v-if="selectedRequirement">
          <header class="editor-header">
            <div>
              <strong>{{ selectedRequirement.nodeName }}</strong>
              <span>{{ selectedRequirement.occurrencePath }}</span>
            </div>
            <el-tag :type="modelValue[selectedRequirement.slotId] ? 'success' : 'warning'" effect="plain">
              {{ modelValue[selectedRequirement.slotId] ? '已绑定' : '待绑定' }}
            </el-tag>
          </header>

          <dl class="binding-details">
            <div><dt>设备模型</dt><dd>{{ modelName(selectedRequirement.deviceModelId) }}</dd></div>
            <div><dt>设备能力</dt><dd>{{ capabilityLabel(selectedRequirement) }}</dd></div>
          </dl>

          <div v-if="selectedParameters.length" class="parameter-field">
            <label>能力参数</label>
            <div v-for="parameter in selectedParameters" :key="parameter.name" class="parameter-row">
              <div class="parameter-label">
                <strong>{{ parameter.displayName || parameter.name }}</strong>
                <span>{{ parameter.hole ? '创建任务时填写' : '流程已写死' }}</span>
              </div>
              <WorkflowTypedValueInput
                :model-value="parameterDisplayValue(parameter)"
                :disabled="!parameter.hole"
                :nullable="parameter.hole"
                :data-type="parameter.dataType || 'STRING'"
                :options="parameter.allowedValues || parameter.enumValues || parameter.options || []"
                @update:model-value="setParameter(parameter.name, $event)"
              />
            </div>
          </div>

          <div class="instance-field">
            <label>绑定设备</label>
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
            <p v-if="modelValue[selectedRequirement.slotId] && sameModelUnboundCount" class="sync-hint">
              还有 {{ sameModelUnboundCount }} 个使用同一设备模型的节点尚未选择实例，可以把当前这台设备一并填入。
              <button class="btn-link" type="button" @click="applyToSameModel">同步到其余节点</button>
            </p>
          </div>
        </template>

        <div v-else class="editor-empty">
          <span>选择流程图中的设备节点</span>
          <p>在此绑定实例并填写能力参数。</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { buildBindingWorkflowView, compatibleInstances, presentCapabilityDisplayName } from '../../../../utils/taskResourceBindings.js'
import WorkflowStageGraph from '../../components/WorkflowStageGraph.vue'
import WorkflowTypedValueInput from '../../WorkflowDesigner/components/inspector/WorkflowTypedValueInput.vue'

type Item = Record<string, any>
const props = withDefaults(defineProps<{
  requirements?: Item[]
  groups?: Item[]
  errors?: string[]
  modelValue: Record<string, number | null>
  parameterBindings?: Record<string, Record<string, unknown>>
  instances?: Item[]
  models?: Item[]
  canvasReady?: boolean
}>(), { requirements: () => [], groups: () => [], errors: () => [], parameterBindings: () => ({}), instances: () => [], models: () => [], canvasReady: true })

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, number | null>]
  'update:parameterBindings': [value: Record<string, Record<string, unknown>>]
}>()
const activeGroupKey = ref('root')
const selectedSlotId = ref('')
const view = computed(() => buildBindingWorkflowView({ groups: props.groups, errors: props.errors }, props.requirements))
const groupsByKey = computed(() => new Map(view.value.groups.map(group => [group.groupKey, group])))
const currentGroup = computed(() => groupsByKey.value.get(activeGroupKey.value) || view.value.groups[0] || null)
const selectedRequirement = computed(() => props.requirements.find(requirement => String(requirement.slotId) === selectedSlotId.value) || null)
const selectedNodeName = computed(() => {
  const node = (currentGroup.value?.nodes || []).find((item: Item) => item.slotId != null && String(item.slotId) === selectedSlotId.value)
  return node?.name || null
})
const selectedParameters = computed(() => selectedRequirement.value?.capabilityParameters || [])
const boundCount = computed(() => props.requirements.filter(requirement => props.modelValue[requirement.slotId]).length)
const unboundCount = computed(() => props.requirements.length - boundCount.value)
const selectedInstances = computed(() => selectedRequirement.value ? compatibleInstances(selectedRequirement.value, props.instances, props.models) : [])
const sameModelUnboundCount = computed(() => selectedRequirement.value == null ? 0 : props.requirements.filter(requirement =>
  Number(requirement.deviceModelId) === Number(selectedRequirement.value.deviceModelId) && !props.modelValue[requirement.slotId]
).length)
const legendItems = [
  { label: '执行流', className: 'workflow' },
  { label: '数据流', className: 'data' },
  { label: '设备节点可绑定', className: 'device' },
  { label: '点击进入子流程', className: 'subflow' },
]
const groupTrail = computed(() => {
  const trail: Item[] = []
  let group = currentGroup.value
  while (group) {
    trail.unshift(group)
    group = group.parentGroupKey ? groupsByKey.value.get(group.parentGroupKey) : null
  }
  return trail
})

watch(() => props.groups, groups => {
  if (!groups.some(group => group.groupKey === activeGroupKey.value)) activeGroupKey.value = groups[0]?.groupKey || 'root'
  if (!props.requirements.some(requirement => String(requirement.slotId) === selectedSlotId.value)) selectedSlotId.value = ''
}, { immediate: true, deep: true })

function bindingOverlay(node: Item) {
  if (node.nodeType !== 'DEV_NODE') return { mode: 'binding' }
  return {
    mode: 'binding',
    bound: node.slotId != null && Boolean(props.modelValue[node.slotId]),
    boundLabel: node.slotId == null ? '' : instanceNameById(props.modelValue[node.slotId]),
  }
}

function capabilitiesFor(node: Item) {
  if (node?.nodeType !== 'DEV_NODE') return []
  return props.models.find(model => Number(model.id ?? model.modelId) === Number(node.deviceModelId))?.capabilities || []
}

function issuesFor(node: Item) {
  return node.bindingInvalid ? [{ message: '流程图与设备绑定要求不一致' }] : []
}

function handleNodeClick(node: Item) {
  if (node.nodeType === 'DEV_NODE' && node.slotId && !node.bindingInvalid) selectedSlotId.value = String(node.slotId)
  if (node.nodeType === 'SUBFLOW_NODE') enterChildFlow(node)
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

function capabilityLabel(requirement: Item) {
  const node = (currentGroup.value?.nodes || []).find((item: Item) => String(item.slotId) === String(requirement.slotId))
  return presentCapabilityDisplayName(requirement, props.models, node)
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

function parameterDisplayValue(parameter: Item) {
  if (!parameter.hole) return parameter.modelValue
  return props.parameterBindings[selectedRequirement.value?.slotId]?.[parameter.name]
}

function setParameter(name: string, value: unknown) {
  if (!selectedRequirement.value) return
  const slotId = selectedRequirement.value.slotId
  emit('update:parameterBindings', {
    ...props.parameterBindings,
    [slotId]: {
      ...(props.parameterBindings[slotId] || {}),
      [name]: value
    }
  })
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
.resource-binding-panel {
  overflow: hidden;
  background: #ffffff;
}

.resource-binding-panel > .el-alert {
  margin: 8px 14px;
  width: auto;
}

.binding-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  min-height: 480px;
}

.graph-panel {
  min-width: 0;
  border-right: 1px solid var(--sl-border-base, #e2e8f0);
}

.flow-breadcrumb {
  height: 34px;
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}
.flow-breadcrumb button {
  position: relative;
  padding: 0 18px 0 4px;
  border: 0;
  background: transparent;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11.5px;
  cursor: pointer;
}
.flow-breadcrumb button::after {
  content: '›';
  position: absolute;
  right: 6px;
  color: var(--sl-text-disabled, #94a3b8);
}
.flow-breadcrumb button:last-child::after {
  display: none;
}
.flow-breadcrumb button.current {
  color: var(--sl-text-heading, #0f172a);
  font-weight: 600;
  cursor: default;
}

.graph-stage {
  position: relative;
  height: 446px;
  overflow: hidden;
  isolation: isolate;
}
.graph-empty {
  height: 446px;
  display: grid;
  place-items: center;
  color: var(--sl-text-disabled, #94a3b8);
  font-size: 11.5px;
}

.binding-editor {
  padding: 12px 14px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.binding-progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
}
.binding-progress b {
  color: var(--sl-text-heading, #0f172a);
  font-weight: 650;
}

.editor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}
.editor-header > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}
.editor-header strong {
  overflow: hidden;
  color: var(--sl-text-heading, #0f172a);
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.editor-header span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 10px;
  line-height: 1.4;
}

.binding-details {
  display: grid;
  margin: 0;
}
.binding-details > div {
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 8px;
  padding: 3px 0;
  font-size: 11.5px;
}
.binding-details dt {
  color: var(--sl-text-secondary, #64748b);
  margin: 0;
}
.binding-details dd {
  color: var(--sl-text-heading, #0f172a);
  font-weight: 500;
  margin: 0;
}

.instance-field,
.parameter-field {
  display: grid;
  gap: 6px;
}
.instance-field label,
.parameter-field > label {
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  font-weight: 600;
}
.instance-field :deep(.el-select) {
  width: 100%;
}
.instance-status {
  float: right;
  margin-left: 12px;
  color: var(--sl-text-secondary, #64748b);
}
.field-warning {
  color: var(--sl-warning, #d97706);
  font-size: 10px;
}
.sync-hint {
  margin: 2px 0 0;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  line-height: 1.5;
}

.parameter-row {
  display: grid;
  gap: 4px;
}
.parameter-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.parameter-label strong {
  color: var(--sl-text-heading, #0f172a);
  font-size: 11px;
}
.parameter-label span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 9.5px;
}

.editor-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 8px;
  text-align: center;
  box-sizing: border-box;
}
.editor-empty span {
  color: var(--sl-text-heading, #0f172a);
  font-size: 12px;
  font-weight: 600;
}
.editor-empty p {
  max-width: 220px;
  margin: 4px 0 0;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11.5px;
  line-height: 1.5;
}

@media (max-width: 1050px) {
  .binding-workspace {
    grid-template-columns: 1fr;
  }
  .graph-panel {
    border-right: 0;
    border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  }
  .binding-editor {
    min-height: 190px;
  }
  .editor-empty {
    min-height: 150px;
  }
}

@media (max-width: 760px) {
  .graph-stage, .graph-empty {
    height: 390px;
  }
}
</style>
