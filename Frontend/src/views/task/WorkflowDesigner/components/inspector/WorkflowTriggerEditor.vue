<template>
  <section class="trigger-editor">
    <div class="editor-heading">
      <div><strong>触发器</strong><span>每个 condition 独立求值，首次满足时执行自己的内联 action</span></div>
      <el-button v-if="editable" size="small" :disabled="!actions.length" @click="addTrigger">添加触发器</el-button>
    </div>
    <div v-for="(trigger, index) in triggers" :key="trigger._systemKey || index" class="trigger-card">
      <div class="trigger-head">
        <div><strong>触发器 {{ index + 1 }}</strong><el-tag v-if="isSystemItem(trigger)" size="small" type="info">系统默认</el-tag></div>
        <el-button v-if="canEditTrigger(trigger)" link type="danger" @click="removeTrigger(index)">删除</el-button>
      </div>
      <div class="section-label">当条件满足</div>
      <div class="condition-grid">
        <el-select :model-value="trigger.condition?.object" filterable allow-create default-first-option placeholder="条件对象" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index, 'object', $event)">
          <el-option v-for="objectName in conditionObjects" :key="objectName" :label="objectName" :value="objectName" />
        </el-select>
        <el-select :model-value="trigger.condition?.operator" placeholder="操作符" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index, 'operator', $event)">
          <el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" />
        </el-select>
        <el-input :model-value="displayJson(trigger.condition?.threshold)" placeholder="阈值（支持 JSON）" :disabled="!canEditTrigger(trigger)" @change="updateThreshold(index, $event)" />
      </div>
      <div class="section-label">执行动作</div>
      <div class="action-grid">
        <el-select :model-value="trigger.action?.actionName" placeholder="动作" :disabled="!canEditTrigger(trigger)" @update:model-value="changeAction(index, $event)">
          <el-option v-for="name in actions" :key="name" :label="name" :value="name" />
        </el-select>
        <template v-if="trigger.action?.actionName === 'EMIT'">
          <el-select :model-value="trigger.action?.payload?.targetInterfaceName" placeholder="目标 OUT 接口" :disabled="!canEditTrigger(trigger)" @update:model-value="updatePayload(index, 'targetInterfaceName', $event)">
            <el-option v-for="output in outputInterfaces" :key="output.name" :label="output.name" :value="output.name" />
          </el-select>
          <el-select :model-value="trigger.action?.payload?.signalName" placeholder="发送信号" :disabled="!canEditTrigger(trigger)" @update:model-value="updatePayload(index, 'signalName', $event)">
            <el-option v-for="signal in allowedSignals(trigger.action?.payload?.targetInterfaceName)" :key="signal" :label="signal" :value="signal" />
          </el-select>
        </template>
        <template v-else-if="trigger.action?.actionName === 'UPDATE'">
          <el-select :model-value="trigger.action?.payload?.updateType" placeholder="更新类型" :disabled="!canEditTrigger(trigger)" @update:model-value="changeUpdateType(index, $event)">
            <el-option label="内部变量" value="INTERNAL_VARIABLE" />
            <el-option label="节点生命周期" value="NODE_LIFECYCLE" />
          </el-select>
          <el-select v-if="trigger.action?.payload?.updateType === 'NODE_LIFECYCLE'" :model-value="trigger.action?.payload?.targetName" placeholder="目标生命周期" :disabled="!canEditTrigger(trigger)" @update:model-value="updatePayload(index, 'targetName', $event)">
            <el-option v-for="state in node.lifecycle?.states || []" :key="state" :label="state" :value="state" />
          </el-select>
          <template v-else>
            <el-select :model-value="trigger.action?.payload?.targetName" placeholder="目标内部变量" :disabled="!canEditTrigger(trigger)" @update:model-value="updatePayload(index, 'targetName', $event)">
              <el-option v-for="variable in node.internalVariables || []" :key="variable.name" :label="variable.name" :value="variable.name" />
            </el-select>
            <el-select :model-value="updateSource(trigger)" :disabled="!canEditTrigger(trigger)" @update:model-value="changeUpdateSource(index, $event)"><el-option label="直接常量" value="VALUE" /><el-option label="计算表达式" value="EXPRESSION" /></el-select>
            <el-input v-if="updateSource(trigger) === 'EXPRESSION'" :model-value="trigger.action?.payload?.valueExpression" placeholder="例如 temp / 100" :disabled="!canEditTrigger(trigger)" @update:model-value="updatePayload(index, 'valueExpression', $event)" />
            <el-input v-else :model-value="displayJson(trigger.action?.payload?.value)" placeholder="常量（数字、布尔、字符串或 JSON）" :disabled="!canEditTrigger(trigger)" @change="updateConstant(index, $event)" />
          </template>
        </template>
      </div>
    </div>
    <el-empty v-if="!triggers.length" description="该接口尚未定义触发器" :image-size="36" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { isSystemItem } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, interfaceItem: Item, editable?: boolean }>(), { editable: false })
const emit = defineEmits<{ 'update:interface': [interfaceItem: Item] }>()
const operators = ['>', '<', '>=', '<=', '=', '!=', 'BETWEEN', 'IN']
const actions = computed<string[]>(() => props.node.actions || [])
const triggers = computed<Item[]>(() => props.interfaceItem.bindingTriggers || [])
const outputInterfaces = computed<Item[]>(() => (props.node.interfaces || []).filter((item: Item) => item.direction === 'OUT'))
const conditionObjects = computed(() => [
  'signalName',
  'payload',
  'payload.stateName',
  'nodeLifecycleState',
  ...(['BRANCH', 'AGGREGATE'].includes(props.node.functionType) ? ['expression'] : []),
  ...(props.node.internalVariables || []).map((item: Item) => item.name),
])

function canEditTrigger(trigger: Item) { return props.editable && !isSystemItem(trigger) }
function publish(bindingTriggers: Item[]) { emit('update:interface', { ...props.interfaceItem, bindingTriggers }) }
function editTrigger(index: number, edit: (trigger: Item) => Item) { publish(triggers.value.map((trigger, triggerIndex) => triggerIndex === index ? edit(trigger) : trigger)) }
function allowedSignals(interfaceName: string) { return outputInterfaces.value.find(item => item.name === interfaceName)?.allowedSignals || [] }
function defaultAction(actionName = actions.value[0] || 'UPDATE') {
  if (actionName === 'EMIT') {
    const target = outputInterfaces.value[0]
    return { actionName, payload: { targetInterfaceName: target?.name || '', signalName: target?.allowedSignals?.[0] || '' } }
  }
  return { actionName: 'UPDATE', payload: { updateType: 'INTERNAL_VARIABLE', targetName: props.node.internalVariables?.[0]?.name || '', value: null } }
}
function addTrigger() { publish([...triggers.value, { condition: { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' }, action: defaultAction() }]) }
function removeTrigger(index: number) { publish(triggers.value.filter((_trigger, triggerIndex) => triggerIndex !== index)) }
function updateCondition(index: number, field: string, value: unknown) { editTrigger(index, trigger => ({ ...trigger, condition: { ...trigger.condition, [field]: value } })) }
function updateThreshold(index: number, value: string) { updateCondition(index, 'threshold', parseTyped(value)) }
function changeAction(index: number, actionName: string) { editTrigger(index, trigger => ({ ...trigger, action: defaultAction(actionName) })) }
function updatePayload(index: number, field: string, value: unknown) { editTrigger(index, trigger => ({ ...trigger, action: { ...trigger.action, payload: { ...trigger.action?.payload, [field]: value } } })) }
function changeUpdateType(index: number, updateType: string) {
  editTrigger(index, trigger => ({ ...trigger, action: { actionName: 'UPDATE', payload: updateType === 'NODE_LIFECYCLE'
    ? { updateType, targetName: props.node.lifecycle?.states?.[0] || '' }
    : { updateType, targetName: props.node.internalVariables?.[0]?.name || '', value: null } } }))
}
function updateSource(trigger: Item) { return Object.hasOwn(trigger.action?.payload || {}, 'valueExpression') ? 'EXPRESSION' : 'VALUE' }
function changeUpdateSource(index: number, source: string) {
  editTrigger(index, trigger => {
    const { value: _value, valueExpression: _expression, ...payload } = trigger.action?.payload || {}
    const nextPayload = source === 'EXPRESSION' ? { ...payload, valueExpression: '' } : { ...payload, value: null }
    return { ...trigger, action: { ...trigger.action, payload: nextPayload } }
  })
}
function updateConstant(index: number, value: string) { updatePayload(index, 'value', parseTyped(value)) }
function parseTyped(value: string) { try { return JSON.parse(value) } catch { return value } }
function displayJson(value: unknown) { return typeof value === 'string' ? value : value === undefined ? '' : JSON.stringify(value) }
</script>

<style scoped>
.trigger-editor{display:grid;gap:10px}.editor-heading,.trigger-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.editor-heading>div{display:grid;gap:2px}.editor-heading span{color:#7d8999;font-size:10px}.trigger-card{display:grid;gap:8px;padding:10px;border:1px solid #e0e5eb;border-radius:5px;background:#fafbfc}.trigger-head>div{display:flex;align-items:center;gap:7px}.trigger-head strong{font-size:11px}.section-label{color:#6c7889;font-size:10px;font-weight:600}.condition-grid,.action-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.condition-grid :deep(.el-select),.action-grid :deep(.el-select){width:100%}@media(max-width:700px){.condition-grid,.action-grid{grid-template-columns:1fr}}
</style>
