<template>
  <section class="triggers-actions-panel">
    <div class="panel-heading">
      <div><strong>动作能力</strong><span>节点只声明可用的 UPDATE / EMIT；具体参数配置在触发器中</span></div>
      <div class="heading-actions">
        <el-button v-for="name in missingActions" :key="name" size="small" @click="addAction(name)">启用 {{ name }}</el-button>
      </div>
    </div>
    <div class="action-capabilities">
      <el-tag v-for="name in actions" :key="name" closable @close="removeAction(name)">{{ name }}</el-tag>
      <el-empty v-if="!actions.length" description="尚未启用动作能力" :image-size="34" />
    </div>

    <div class="panel-heading trigger-heading">
      <div><strong>接口触发器</strong><span>按 OUT → IN 顺序展示；每个触发器独立判断并执行自己的内联动作</span></div>
    </div>
    <div v-for="item in orderedInterfaces" :key="item.name" class="trigger-group">
      <div class="trigger-group-head">
        <strong>{{ item.name }}</strong>
        <el-tag size="small" type="info">{{ item.direction }}</el-tag>
        <el-button size="small" :disabled="!actions.length" @click="addTrigger(item.name)">添加触发器</el-button>
      </div>

      <div v-for="(trigger, index) in item.bindingTriggers || []" :key="trigger._systemKey || index" class="trigger-card">
        <div class="trigger-card-head">
          <el-icon v-if="isSystemItem(trigger)" title="系统触发器，禁止编辑"><Lock /></el-icon>
          <strong>触发器 {{ index + 1 }}</strong>
          <el-button v-if="!isSystemItem(trigger)" link type="danger" @click="removeTrigger(item.name, index)">删除</el-button>
        </div>

        <div class="condition-grid">
          <el-select :model-value="trigger.condition?.object" filterable allow-create default-first-option placeholder="条件对象" :disabled="isSystemItem(trigger)" @update:model-value="updateCondition(item.name, index, 'object', $event)">
            <el-option v-for="object in conditionObjects" :key="object" :label="object" :value="object" />
          </el-select>
          <el-select :model-value="trigger.condition?.operator" placeholder="操作符" :disabled="isSystemItem(trigger)" @update:model-value="updateCondition(item.name, index, 'operator', $event)">
            <el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" />
          </el-select>
          <el-input :model-value="displayJson(trigger.condition?.threshold)" placeholder="阈值（支持 JSON）" :disabled="isSystemItem(trigger)" @change="updateThreshold(item.name, index, $event)" />
        </div>

        <div class="action-editor">
          <el-select :model-value="trigger.action?.actionName" placeholder="动作" :disabled="isSystemItem(trigger)" @update:model-value="changeAction(item.name, index, $event)">
            <el-option v-for="name in actions" :key="name" :label="name" :value="name" />
          </el-select>

          <template v-if="trigger.action?.actionName === 'EMIT'">
            <el-select :model-value="trigger.action?.payload?.targetInterfaceName" placeholder="目标 OUT 接口" :disabled="isSystemItem(trigger)" @update:model-value="updatePayload(item.name, index, 'targetInterfaceName', $event)">
              <el-option v-for="output in outputInterfaces" :key="output.name" :label="output.name" :value="output.name" />
            </el-select>
            <el-select :model-value="trigger.action?.payload?.signalName" placeholder="信号" :disabled="isSystemItem(trigger)" @update:model-value="updatePayload(item.name, index, 'signalName', $event)">
              <el-option v-for="signal in allowedSignals(trigger.action?.payload?.targetInterfaceName)" :key="signal" :label="signal" :value="signal" />
            </el-select>
          </template>

          <template v-else-if="trigger.action?.actionName === 'UPDATE'">
            <el-select :model-value="trigger.action?.payload?.updateType" placeholder="更新类型" :disabled="isSystemItem(trigger)" @update:model-value="changeUpdateType(item.name, index, $event)">
              <el-option label="内部变量" value="INTERNAL_VARIABLE" />
              <el-option label="节点生命周期" value="NODE_LIFECYCLE" />
            </el-select>

            <template v-if="trigger.action?.payload?.updateType === 'NODE_LIFECYCLE'">
              <el-select :model-value="trigger.action?.payload?.targetName" placeholder="目标生命周期" :disabled="isSystemItem(trigger)" @update:model-value="updatePayload(item.name, index, 'targetName', $event)">
                <el-option v-for="state in node.lifecycle?.states || []" :key="state" :label="state" :value="state" />
              </el-select>
            </template>

            <template v-else>
              <el-select :model-value="trigger.action?.payload?.targetName" placeholder="目标内部变量" :disabled="isSystemItem(trigger)" @update:model-value="updatePayload(item.name, index, 'targetName', $event)">
                <el-option v-for="variable in node.internalVariables || []" :key="variable.name" :label="variable.name" :value="variable.name" />
              </el-select>
              <el-select :model-value="updateSource(trigger)" :disabled="isSystemItem(trigger)" @update:model-value="changeUpdateSource(item.name, index, $event)">
                <el-option label="直接常量" value="VALUE" />
                <el-option label="计算表达式" value="EXPRESSION" />
              </el-select>
              <el-input v-if="updateSource(trigger) === 'EXPRESSION'" :model-value="trigger.action?.payload?.valueExpression" placeholder="例如 temperature / 100" :disabled="isSystemItem(trigger)" @update:model-value="updatePayload(item.name, index, 'valueExpression', $event)" />
              <el-input v-else :model-value="displayJson(trigger.action?.payload?.value)" placeholder="常量（支持数字、布尔和 JSON）" :disabled="isSystemItem(trigger)" @change="updateConstant(item.name, index, $event)" />
            </template>
          </template>
        </div>
      </div>
      <el-empty v-if="!(item.bindingTriggers || []).length" description="尚未定义触发器" :image-size="30" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import { isSystemItem, removeAction as removeNodeAction } from '../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = defineProps<{ node: Item }>()
const emit = defineEmits<{ 'update:node': [node: Item] }>()
const operators = ['>', '<', '>=', '<=', '=', '!=', 'BETWEEN', 'IN']
const actions = computed<string[]>(() => props.node.actions || [])
const missingActions = computed(() => ['UPDATE', 'EMIT'].filter(name => !actions.value.includes(name)))
const outputInterfaces = computed(() => (props.node.interfaces || []).filter((item: Item) => item.direction === 'OUT'))
const orderedInterfaces = computed(() => [
  ...(props.node.interfaces || []).filter((item: Item) => item.direction === 'OUT'),
  ...(props.node.interfaces || []).filter((item: Item) => item.direction === 'IN'),
])
const conditionObjects = computed(() => [
  'inputSignalName', 'inputPayload.stateName', 'inputPayload.state', 'nodeLifecycleState',
  ...(['BRANCH', 'AGGREGATE'].includes(props.node.functionType) ? ['expression'] : []),
  ...(props.node.internalVariables || []).map((item: Item) => item.name),
])

function publish(next: Item) { emit('update:node', next) }
function addAction(name: string) { publish({ ...props.node, actions: [...actions.value, name] }) }
function removeAction(name: string) { try { publish(removeNodeAction(props.node, name)) } catch {} }
function allowedSignals(interfaceName: string) { return outputInterfaces.value.find((item: Item) => item.name === interfaceName)?.allowedSignals || [] }

function defaultAction(actionName = actions.value[0] || 'UPDATE') {
  if (actionName === 'EMIT') {
    const target = outputInterfaces.value[0]
    return { actionName, payload: { targetInterfaceName: target?.name || '', signalName: target?.allowedSignals?.[0] || '' } }
  }
  return { actionName: 'UPDATE', payload: {
    updateType: 'INTERNAL_VARIABLE', targetName: props.node.internalVariables?.[0]?.name || '', value: null,
  } }
}

function addTrigger(interfaceName: string) {
  editInterface(interfaceName, item => ({ ...item, bindingTriggers: [...(item.bindingTriggers || []), {
    condition: { object: 'nodeLifecycleState', operator: '=', threshold: 'RUNNING' },
    action: defaultAction(),
  }] }))
}

function editInterface(interfaceName: string, edit: (item: Item) => Item) {
  publish({ ...props.node, interfaces: (props.node.interfaces || []).map((item: Item) => item.name === interfaceName ? edit(item) : item) })
}

function editTrigger(interfaceName: string, index: number, edit: (trigger: Item) => Item) {
  editInterface(interfaceName, item => ({ ...item, bindingTriggers: (item.bindingTriggers || []).map((trigger: Item, triggerIndex: number) => triggerIndex === index ? edit(trigger) : trigger) }))
}

function updateCondition(interfaceName: string, index: number, field: string, value: unknown) {
  editTrigger(interfaceName, index, trigger => ({ ...trigger, condition: { ...trigger.condition, [field]: value } }))
}
function updateThreshold(interfaceName: string, index: number, value: string) { updateCondition(interfaceName, index, 'threshold', parseTyped(value)) }
function changeAction(interfaceName: string, index: number, actionName: string) { editTrigger(interfaceName, index, trigger => ({ ...trigger, action: defaultAction(actionName) })) }
function updatePayload(interfaceName: string, index: number, field: string, value: unknown) { editTrigger(interfaceName, index, trigger => ({ ...trigger, action: { ...trigger.action, payload: { ...trigger.action?.payload, [field]: value } } })) }
function changeUpdateType(interfaceName: string, index: number, updateType: string) {
  editTrigger(interfaceName, index, trigger => ({ ...trigger, action: { actionName: 'UPDATE', payload: updateType === 'NODE_LIFECYCLE'
    ? { updateType, targetName: props.node.lifecycle?.states?.[0] || '' }
    : { updateType, targetName: props.node.internalVariables?.[0]?.name || '', value: null } } }))
}
function updateSource(trigger: Item) { return Object.hasOwn(trigger.action?.payload || {}, 'valueExpression') ? 'EXPRESSION' : 'VALUE' }
function changeUpdateSource(interfaceName: string, index: number, source: string) {
  editTrigger(interfaceName, index, trigger => {
    const { value: _value, valueExpression: _expression, ...payload } = trigger.action?.payload || {}
    return { ...trigger, action: { ...trigger.action, payload: source === 'EXPRESSION' ? { ...payload, valueExpression: '' } : { ...payload, value: null } } }
  })
}
function updateConstant(interfaceName: string, index: number, value: string) { updatePayload(interfaceName, index, 'value', parseTyped(value)) }
function removeTrigger(interfaceName: string, index: number) { editInterface(interfaceName, item => ({ ...item, bindingTriggers: (item.bindingTriggers || []).filter((_trigger: Item, triggerIndex: number) => triggerIndex !== index) })) }
function parseTyped(value: string) { try { return JSON.parse(value) } catch { return value } }
function displayJson(value: unknown) { return typeof value === 'string' ? value : value === undefined ? '' : JSON.stringify(value) }
</script>

<style scoped>
.triggers-actions-panel{display:grid;gap:10px}.panel-heading,.trigger-group-head,.trigger-card-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.panel-heading>div:first-child{display:grid;gap:2px}.panel-heading span{color:var(--el-text-color-secondary);font-size:12px}.heading-actions,.action-capabilities{display:flex;flex-wrap:wrap;gap:6px}.trigger-heading{margin-top:12px;padding-top:14px;border-top:1px solid var(--el-border-color-lighter)}.trigger-group,.trigger-card{display:grid;gap:8px;padding:10px;border:1px solid var(--el-border-color-lighter);border-radius:7px}.trigger-card{background:var(--el-fill-color-lighter)}.trigger-card-head{justify-content:flex-start}.trigger-card-head .el-button{margin-left:auto}.condition-grid,.action-editor{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.condition-grid :deep(.el-select),.action-editor :deep(.el-select){width:100%}@media(max-width:700px){.condition-grid,.action-editor{grid-template-columns:1fr}}
</style>
