<template>
  <section class="trigger-editor-section">
    <div class="trigger-section-header">
      <div class="header-titles">
        <strong class="title-main">触发器</strong>
        <span class="title-sub">当条件满足时，按先后顺序执行绑定的接口动作。</span>
      </div>
      <button
        v-if="editable"
        type="button"
        class="btn-aliyun-cta"
        :disabled="!actions.length"
        @click="addTrigger"
      >
        + 添加触发器
      </button>
    </div>

    <!-- 触发器列表 -->
    <div v-if="triggers.length" class="triggers-list">
      <article
        v-for="(trigger, index) in triggers"
        :key="trigger._systemKey || index"
        class="trigger-item-card"
        :class="{ 'system-trigger-card': isSystemItem(trigger) }"
        @mouseenter="onCardMouseEnter(index)"
      >
        <!-- 触发器小标题栏 -->
        <header class="trigger-item-head">
          <div class="head-left">
            <span
              class="trigger-idx-badge"
              :class="{ 'is-new-red': newlyAddedIndexes.has(index) }"
            >
              #{{ index + 1 }}
            </span>
          </div>
          <div class="head-right">
            <span v-if="isSystemItem(trigger)" class="system-tag-pill">系统预置 · 只读</span>
            <button
              v-else-if="canEditTrigger(trigger)"
              type="button"
              class="btn-aliyun-danger-link"
              @click="removeTrigger(index)"
            >
              删除
            </button>
          </div>
        </header>

        <!-- 1. 条件区域 (Condition Block) -->
        <div class="trigger-block condition-block">
          <div v-if="conditionItems(trigger).length > 1" class="condition-logic-bar">
            <strong>全部满足 (AND)</strong>
            <span>以下条件同时成立时触发</span>
          </div>

          <div
            v-for="(condition, conditionIndex) in conditionItems(trigger)"
            :key="conditionIndex"
            class="rule-row-container"
          >
            <div class="rule-inputs-row condition-grid">
              <span class="rule-badge cond-badge">{{ conditionIndex ? '且' : '当' }}</span>

              <!-- 观测对象 -->
              <el-select
                :model-value="condition.object"
                placeholder="观测对象"
                :disabled="!canEditTrigger(trigger)"
                class="col-cond-obj"
                @update:model-value="updateCondition(index, conditionIndex, 'object', $event)"
              >
                <el-option-group
                  v-for="group in conditionGroups(condition)"
                  :key="group.label"
                  :label="group.label"
                >
                  <el-option
                    v-for="item in group.options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-option-group>
              </el-select>

              <!-- 比较运算符 -->
              <el-select
                :model-value="condition.operator"
                placeholder="比较"
                :disabled="!canEditTrigger(trigger)"
                class="col-cond-op"
                @update:model-value="updateCondition(index, conditionIndex, 'operator', $event)"
              >
                <el-option
                  v-for="operator in operatorsFor(condition)"
                  :key="operator"
                  :label="operatorLabel(operator)"
                  :value="operator"
                />
              </el-select>

              <!-- 比较值 -->
              <div class="col-cond-val">
                <el-select
                  v-if="condition.operator === 'IN' && thresholdOptions(condition).length"
                  :model-value="thresholdArray(condition)"
                  multiple
                  :disabled="!canEditTrigger(trigger)"
                  placeholder="比较值"
                  @update:model-value="updateThresholdList(index, conditionIndex, $event)"
                >
                  <el-option
                    v-for="value in thresholdOptions(condition)"
                    :key="String(value)"
                    :label="String(value)"
                    :value="value"
                  />
                </el-select>
                <el-select
                  v-else-if="thresholdOptions(condition).length"
                  :model-value="condition.threshold"
                  placeholder="比较值"
                  :disabled="!canEditTrigger(trigger)"
                  @update:model-value="updateCondition(index, conditionIndex, 'threshold', $event)"
                >
                  <el-option
                    v-for="value in thresholdOptions(condition)"
                    :key="String(value)"
                    :label="String(value)"
                    :value="value"
                  />
                </el-select>
                <el-select
                  v-else-if="condition.operator === 'IN'"
                  :model-value="thresholdArray(condition)"
                  multiple
                  allow-create
                  filterable
                  default-first-option
                  :disabled="!canEditTrigger(trigger)"
                  placeholder="输入多个比较值"
                  @update:model-value="updateThresholdList(index, conditionIndex, $event)"
                />
                <WorkflowTypedValueInput
                  v-else-if="conditionVariable(condition)"
                  :model-value="condition.threshold"
                  :data-type="conditionVariable(condition).dataType"
                  :disabled="!canEditTrigger(trigger)"
                  @update:model-value="updateCondition(index, conditionIndex, 'threshold', $event)"
                />
                <el-input
                  v-else
                  :model-value="displayJson(condition.threshold)"
                  placeholder="比较值"
                  :disabled="!canEditTrigger(trigger)"
                  @change="updateThreshold(index, conditionIndex, $event)"
                />
              </div>

              <!-- 移除条件按钮 (行内右侧，绝不换行) -->
              <button
                v-if="canRemoveTriggerCondition(trigger)"
                type="button"
                class="btn-row-action"
                title="移除此条件"
                @click="removeTriggerCondition(index, conditionIndex)"
              >
                移除
              </button>
            </div>
          </div>

          <div v-if="canEditTrigger(trigger)" class="row-add-wrap">
            <button type="button" class="btn-sub-add" @click="addTriggerCondition(index)">
              + 添加条件
            </button>
          </div>
        </div>

        <!-- 2. 动作区域 (Action Block) -->
        <div class="trigger-block action-block">
          <div
            v-for="(action, actionIndex) in actionsOf(trigger)"
            :key="actionIndex"
            class="action-item-group"
          >
            <!-- 动作主配置行 -->
            <div
              class="rule-inputs-row action-grid"
              :class="{
                'emit-grid': action.actionName === 'EMIT',
                'update-grid': action.actionName === 'UPDATE'
              }"
            >
              <span class="rule-badge act-badge">{{ actionIndex ? '再' : '则' }}</span>

              <!-- 动作类型 (EMIT / UPDATE) -->
              <el-select
                :model-value="action.actionName"
                placeholder="动作"
                :disabled="!canEditTrigger(trigger)"
                class="col-act-name"
                @update:model-value="changeAction(index, actionIndex, $event)"
              >
                <el-option v-for="name in availableActions" :key="name" :label="name" :value="name" />
              </el-select>

              <!-- EMIT：发送接口标签 + 发送信号选择 -->
              <template v-if="action.actionName === 'EMIT'">
                <div class="host-interface-tag" title="发送接口">
                  <span class="tag-lbl">接口:</span>
                  <span class="tag-val">{{ interfaceItem.name }}</span>
                </div>
                <el-select
                  :model-value="action.payload?.signalName"
                  placeholder="选择发送信号"
                  :disabled="!canEditTrigger(trigger)"
                  class="col-act-signal"
                  @update:model-value="updatePayload(index, actionIndex, 'signalName', $event)"
                >
                  <el-option
                    v-for="signal in interfaceItem.allowedSignals || []"
                    :key="signal"
                    :label="signal"
                    :value="signal"
                  />
                </el-select>
              </template>

              <!-- UPDATE：选择更新类型与目标 -->
              <template v-else-if="action.actionName === 'UPDATE'">
                <el-select
                  :model-value="action.payload?.updateType"
                  placeholder="更新类型"
                  :disabled="!canEditTrigger(trigger)"
                  class="col-act-update-type"
                  @update:model-value="changeUpdateType(index, actionIndex, $event)"
                >
                  <el-option label="内部变量" value="INTERNAL_VARIABLE" />
                  <el-option label="节点生命周期" value="NODE_LIFECYCLE" />
                </el-select>
                <el-select
                  :model-value="action.payload?.targetName"
                  placeholder="更新目标"
                  :disabled="!canEditTrigger(trigger)"
                  class="col-act-update-target"
                  @update:model-value="changeUpdateTarget(index, actionIndex, $event)"
                >
                  <el-option
                    v-for="target in updateTargets(action)"
                    :key="target"
                    :label="target"
                    :value="target"
                  />
                </el-select>
              </template>

              <!-- 移除动作按钮 (行内右侧，绝不换行) -->
              <button
                v-if="canEditTrigger(trigger) && actionsOf(trigger).length > 1"
                type="button"
                class="btn-row-action"
                title="移除此动作"
                @click="removeAction(index, actionIndex)"
              >
                移除
              </button>
            </div>

            <!-- UPDATE 变量专属：值设置第二行 -->
            <div
              v-if="action.actionName === 'UPDATE' && action.payload?.updateType !== 'NODE_LIFECYCLE'"
              class="rule-inputs-row action-extra-grid"
            >
              <span class="rule-badge val-badge">值</span>
              <el-select
                :model-value="updateSource(action)"
                :disabled="!canEditTrigger(trigger)"
                class="col-val-source"
                @update:model-value="changeUpdateSource(index, actionIndex, $event)"
              >
                <el-option label="直接常量" value="VALUE" />
                <el-option label="计算表达式" value="EXPRESSION" />
              </el-select>
              <div class="col-val-input">
                <WorkflowExpressionEditor
                  v-if="updateSource(action) === 'EXPRESSION'"
                  :model-value="action.payload?.valueExpression"
                  :readonly="!canEditTrigger(trigger)"
                  :variables="node.internalVariables || []"
                  @update:model-value="updatePayload(index, actionIndex, 'valueExpression', $event)"
                />
                <WorkflowTypedValueInput
                  v-else-if="updateVariableDataType(action)"
                  :model-value="action.payload?.value"
                  :data-type="updateVariableDataType(action)"
                  :disabled="!canEditTrigger(trigger)"
                  @update:model-value="updatePayload(index, actionIndex, 'value', $event)"
                />
                <el-input v-else disabled placeholder="请先选择内部变量" />
              </div>
            </div>
          </div>

          <div v-if="canEditTrigger(trigger)" class="row-add-wrap">
            <button type="button" class="btn-sub-add" @click="addAction(index)">
              + 添加动作
            </button>
          </div>
        </div>
      </article>
    </div>

    <!-- 空状态 -->
    <WorkflowConfigurationEmpty
      v-else
      title="尚未配置触发器"
      description="触发器仅在条件首次满足时按顺序执行对应动作。"
      :action-label="editable && actions.length ? '添加触发器' : ''"
      @action="addTrigger"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import WorkflowConfigurationEmpty from './WorkflowConfigurationEmpty.vue'
import WorkflowExpressionEditor from './WorkflowExpressionEditor.vue'
import WorkflowTypedValueInput from './WorkflowTypedValueInput.vue'
import {
  addWorkflowTriggerCondition,
  customTriggerActionNames,
  defaultRoutingOutTrigger,
  emptyWorkflowUpdateValue,
  isSystemItem,
  removeWorkflowTriggerCondition,
  replaceWorkflowTriggerCondition,
  workflowTriggerActions,
  workflowTriggerConditions,
  workflowUpdateVariableDataType
} from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(
  defineProps<{ node: Item, interfaceItem: Item, editable?: boolean }>(),
  { editable: false }
)
const emit = defineEmits<{ 'update:interface': [value: Item] }>()

const newlyAddedIndexes = ref<Set<number>>(new Set())

function onCardMouseEnter(index: number) {
  if (newlyAddedIndexes.value.has(index)) {
    newlyAddedIndexes.value.delete(index)
  }
}

const actions = computed<string[]>(() => customTriggerActionNames(props.node))
const availableActions = computed(() =>
  actions.value.filter(name => name !== 'EMIT' || props.interfaceItem.direction === 'OUT')
)
const triggers = computed<Item[]>(() => props.interfaceItem.bindingTriggers || [])
const taskStates = ['PENDING', 'RUNNING', 'PAUSED', 'SUCCEEDED', 'FAILED', 'TERMINATING', 'TERMINATED']

function conditionItems(trigger: Item) {
  return workflowTriggerConditions(trigger.condition)
}
function actionsOf(trigger: Item) {
  return workflowTriggerActions(trigger)
}
function isDeviceCommandInput() {
  return (
    props.node.nodeType === 'DEV_NODE' &&
    props.interfaceItem.direction === 'IN' &&
    props.interfaceItem.interfaceType === 'STATE' &&
    (props.interfaceItem.allowedSignals || []).includes('CMD_STATE')
  )
}
function conditionGroups(condition: Item) {
  return [
    ...(props.interfaceItem.direction === 'IN'
      ? [
          {
            label: '接收信号',
            options: (props.interfaceItem.allowedSignals || []).map((signal: string) => ({
              label: `信号 · ${signal}`,
              value: 'signalName'
            }))
          }
        ]
      : []),
    ...(props.node.lifecycle?.states?.length
      ? [
          {
            label: '节点生命周期',
            options: [{ label: '节点生命周期状态', value: 'nodeLifecycleState' }]
          }
        ]
      : []),
    ...(isDeviceCommandInput()
      ? [
          {
            label: '控制面状态',
            options: [
              { label: '任务生命周期', value: 'taskLifecycleState' },
              { label: '负载状态 (payload.stateName)', value: 'payload.stateName' }
            ]
          }
        ]
      : []),
    ...(props.node.internalVariables?.length
      ? [
          {
            label: '内部变量',
            options: props.node.internalVariables.map((v: Item) => ({
              label: v.name,
              value: v.name
            }))
          }
        ]
      : [])
  ]
}

function canEditTrigger(trigger: Item) {
  return props.editable && !isSystemItem(trigger)
}
function canRemoveTriggerCondition(trigger: Item) {
  return canEditTrigger(trigger) && conditionItems(trigger).length > 1
}
function publish(bindingTriggers: Item[]) {
  emit('update:interface', { ...props.interfaceItem, bindingTriggers })
}
function edit(index: number, fn: (value: Item) => Item) {
  publish(triggers.value.map((item, i) => (i === index ? fn(item) : item)))
}
function writeActions(trigger: Item, next: Item[]) {
  const actionsList = next.length ? next : [defaultAction()]
  return { ...trigger, actions: actionsList, action: actionsList[0] }
}
function defaultAction(name = 'UPDATE'): Item {
  if (name === 'EMIT') {
    return {
      actionName: 'EMIT',
      payload: {
        targetInterfaceName: props.interfaceItem.name,
        signalName: props.interfaceItem.allowedSignals?.[0] || ''
      }
    }
  }
  const targetName = props.node.internalVariables?.[0]?.name || ''
  return {
    actionName: 'UPDATE',
    payload: {
      updateType: 'INTERNAL_VARIABLE',
      targetName,
      value: emptyWorkflowUpdateValue(workflowUpdateVariableDataType(props.node, targetName))
    }
  }
}
function defaultCondition() {
  if (props.interfaceItem.direction === 'IN') {
    return { object: 'signalName', operator: '=', threshold: props.interfaceItem.allowedSignals?.[0] || '' }
  }
  if (props.node.lifecycle?.states?.length) {
    return { object: 'nodeLifecycleState', operator: '=', threshold: props.node.lifecycle.states[0] }
  }
  const variable = props.node.internalVariables?.[0]
  return {
    object: variable?.name || '',
    operator: '=',
    threshold: variable?.dataType === 'BOOLEAN' ? false : variable?.dataType === 'STRING' ? '' : 0
  }
}
function addTrigger() {
  const routing =
    props.interfaceItem.direction === 'OUT' &&
    props.node.nodeType === 'FUNC_NODE' &&
    ['BRANCH', 'AGGREGATE'].includes(props.node.functionType)
  const nextTriggers = [
    ...triggers.value,
    routing
      ? defaultRoutingOutTrigger(props.node, props.interfaceItem.name)
      : { condition: defaultCondition(), action: defaultAction() }
  ]
  const newIndex = nextTriggers.length - 1
  newlyAddedIndexes.value.add(newIndex)
  publish(nextTriggers)

  void nextTick(() => {
    const scrollContainer =
      document.querySelector('.node-tabs .el-tabs__content') ||
      document.querySelector('.node-config-workspace')
    if (scrollContainer) {
      scrollContainer.scrollTo({ top: scrollContainer.scrollHeight, behavior: 'smooth' })
    }
    const cards = document.querySelectorAll('.trigger-item-card')
    const lastCard = cards[cards.length - 1] as HTMLElement | undefined
    if (lastCard) {
      lastCard.scrollIntoView({ behavior: 'smooth', block: 'end' })
      const firstInput = lastCard.querySelector('input, select, button') as HTMLElement | null
      if (firstInput) {
        firstInput.focus()
      }
    }
  })
}
function removeTrigger(index: number) {
  publish(triggers.value.filter((_item, i) => i !== index))
}
function addTriggerCondition(index: number) {
  edit(index, t => ({ ...t, condition: addWorkflowTriggerCondition(t.condition, defaultCondition()) }))
}
function removeTriggerCondition(index: number, conditionIndex: number) {
  edit(index, t => ({ ...t, condition: removeWorkflowTriggerCondition(t.condition, conditionIndex) }))
}
function addAction(index: number) {
  edit(index, t => writeActions(t, [...actionsOf(t), defaultAction()]))
}
function removeAction(index: number, actionIndex: number) {
  edit(index, t => writeActions(t, actionsOf(t).filter((_item, i) => i !== actionIndex)))
}
function conditionVariable(condition: Item) {
  return (props.node.internalVariables || []).find((item: Item) => item.name === condition?.object) || null
}
function conditionType(condition: Item) {
  if (
    condition?.object === 'signalName' ||
    condition?.object === 'nodeLifecycleState' ||
    condition?.object === 'taskLifecycleState' ||
    condition?.object === 'payload.stateName'
  ) {
    return 'ENUM'
  }
  return conditionVariable(condition)?.dataType || 'STRING'
}
function operatorsFor(condition: Item) {
  const type = conditionType(condition)
  if (type === 'ENUM') return ['=']
  if (type === 'BOOLEAN') return ['=', '!=']
  if (type === 'STRING') return ['=', '!=', 'IN']
  return ['>', '<', '>=', '<=', '=', '!=']
}
function defaultThreshold(condition: Item, operator = '=') {
  const object = condition?.object
  if (object === 'signalName') return props.interfaceItem.allowedSignals?.[0] || ''
  if (object === 'nodeLifecycleState') return props.node.lifecycle?.states?.[0] || ''
  if (object === 'taskLifecycleState') return 'TERMINATING'
  if (object === 'payload.stateName') return 'COMPLETED'
  const variable = conditionVariable(condition)
  if (!variable) return ''
  if (operator === 'IN') return []
  if (variable.dataType === 'BOOLEAN') return false
  if (variable.dataType === 'INTEGER') return 0
  if (variable.dataType === 'DOUBLE') return 0
  return ''
}
function updateCondition(index: number, conditionIndex: number, field: string, value: unknown) {
  edit(index, t => {
    const current = workflowTriggerConditions(t.condition)[conditionIndex] || {}
    const next = { ...current, [field]: value }
    if (field === 'object') {
      const operator = operatorsFor(next).includes(next.operator) ? next.operator : '='
      next.operator = operator
      next.threshold = defaultThreshold(next, operator)
    } else if (field === 'operator') {
      if (value === 'IN' && !Array.isArray(next.threshold)) next.threshold = []
      if (value !== 'IN' && Array.isArray(next.threshold)) next.threshold = next.threshold[0] ?? defaultThreshold(next, String(value))
    }
    return { ...t, condition: replaceWorkflowTriggerCondition(t.condition, conditionIndex, next) }
  })
}
function updateThreshold(index: number, conditionIndex: number, value: string) {
  updateCondition(index, conditionIndex, 'threshold', parseTyped(value))
}
function thresholdArray(condition: Item) {
  return Array.isArray(condition?.threshold)
    ? condition.threshold
    : condition?.threshold === '' || condition?.threshold === undefined || condition?.threshold === null
    ? []
    : [condition.threshold]
}
function updateThresholdList(index: number, conditionIndex: number, values: string[]) {
  const condition = conditionItems(triggers.value[index])[conditionIndex]
  const variable = conditionVariable(condition)
  const typed = values.map(value => {
    if (variable?.dataType === 'INTEGER') {
      const parsed = Number(value)
      return Number.isInteger(parsed) ? parsed : value
    }
    if (variable?.dataType === 'DOUBLE') {
      const parsed = Number(value)
      return Number.isFinite(parsed) ? parsed : value
    }
    return value
  })
  updateCondition(index, conditionIndex, 'threshold', typed)
}
function changeAction(index: number, actionIndex: number, name: string) {
  edit(index, t => writeActions(t, actionsOf(t).map((item, i) => (i === actionIndex ? defaultAction(name) : item))))
}
function updatePayload(index: number, actionIndex: number, field: string, value: unknown) {
  edit(index, t =>
    writeActions(
      t,
      actionsOf(t).map((item, i) =>
        i === actionIndex
          ? {
              ...item,
              payload: {
                ...item.payload,
                targetInterfaceName: item.actionName === 'EMIT' ? props.interfaceItem.name : item.payload?.targetInterfaceName,
                [field]: value
              }
            }
          : item
      )
    )
  )
}
function changeUpdateType(index: number, actionIndex: number, type: string) {
  edit(index, t => {
    const targetName = props.node.internalVariables?.[0]?.name || ''
    const next = {
      actionName: 'UPDATE',
      payload:
        type === 'NODE_LIFECYCLE'
          ? { updateType: type, targetName: props.node.lifecycle?.states?.[0] || '' }
          : {
              updateType: type,
              targetName,
              value: emptyWorkflowUpdateValue(workflowUpdateVariableDataType(props.node, targetName))
            }
    }
    return writeActions(t, actionsOf(t).map((item, i) => (i === actionIndex ? next : item)))
  })
}
function updateTargets(action: Item) {
  return action.payload?.updateType === 'NODE_LIFECYCLE'
    ? props.node.lifecycle?.states || []
    : (props.node.internalVariables || []).map((item: Item) => item.name)
}
function updateVariableDataType(action: Item) {
  return workflowUpdateVariableDataType(props.node, action.payload?.targetName)
}
function changeUpdateTarget(index: number, actionIndex: number, targetName: string) {
  edit(index, t =>
    writeActions(
      t,
      actionsOf(t).map((item, i) => {
        if (i !== actionIndex) return item
        const payload = { ...item.payload, targetName }
        if (payload.updateType === 'INTERNAL_VARIABLE' && !Object.hasOwn(payload, 'valueExpression')) {
          payload.value = emptyWorkflowUpdateValue(workflowUpdateVariableDataType(props.node, targetName))
        }
        return { ...item, payload }
      })
    )
  )
}
function updateSource(action: Item) {
  return Object.hasOwn(action.payload || {}, 'valueExpression') ? 'EXPRESSION' : 'VALUE'
}
function changeUpdateSource(index: number, actionIndex: number, source: string) {
  edit(index, t =>
    writeActions(
      t,
      actionsOf(t).map((item, i) => {
        if (i !== actionIndex) return item
        const { value: _v, valueExpression: _e, ...payload } = item.payload || {}
        return {
          ...item,
          payload:
            source === 'EXPRESSION'
              ? { ...payload, valueExpression: '' }
              : {
                  ...payload,
                  value: emptyWorkflowUpdateValue(workflowUpdateVariableDataType(props.node, payload.targetName))
                }
        }
      })
    )
  )
}
function thresholdOptions(condition: Item) {
  if (condition?.object === 'signalName') return props.interfaceItem.allowedSignals || []
  if (condition?.object === 'nodeLifecycleState') return props.node.lifecycle?.states || []
  if (condition?.object === 'taskLifecycleState') return taskStates
  if (condition?.object === 'payload.stateName') {
    return ['IDLE', 'SENT', 'RUNNING', 'COMPLETED', 'FAILED', 'ABORTING', 'ABORTED']
  }
  return []
}
function parseTyped(value: string) {
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}
function displayJson(value: unknown) {
  return typeof value === 'string' ? value : value === undefined ? '' : JSON.stringify(value)
}
function operatorLabel(value: string) {
  return (
    ({
      '=': '等于',
      '!=': '不等于',
      '>': '大于',
      '<': '小于',
      '>=': '大于等于',
      '<=': '小于等于',
      IN: '属于'
    } as Item)[value] || value
  )
}
</script>

<style scoped>
.trigger-editor-section {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.trigger-editor-section :deep(.el-select),
.trigger-editor-section :deep(.el-input) {
  width: 100%;
}

/* 紧凑下拉框内边距，彻底杜绝 UPDA... 等... 截断 */
.trigger-editor-section :deep(.el-select .el-input__wrapper) {
  padding-left: 6px;
  padding-right: 6px;
}
.trigger-editor-section :deep(.el-select .el-input__suffix) {
  margin-left: 2px;
}
.trigger-editor-section :deep(.el-input__inner) {
  font-size: 11.5px;
}

/* 标题栏：1:1 对齐业务配置卡片规范 */
.trigger-section-header {
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}

.header-titles {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.title-main {
  color: var(--sl-text-heading, #0f172a);
  font-size: 12px;
  font-weight: 700;
}

.title-sub {
  color: var(--sl-text-secondary, #64748b);
  font-size: 10.5px;
}

/* 触发器列表 */
.triggers-list {
  display: flex;
  flex-direction: column;
}

.trigger-item-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 14px 12px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  background: #ffffff;
  transition: background-color 0.15s ease;
}

.trigger-item-card:last-child {
  border-bottom: none;
}

.trigger-item-card:hover {
  background: var(--sl-bg-hover, #fafbfc);
}

.trigger-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 4px;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.trigger-idx-badge {
  font-size: 11.5px;
  font-family: var(--sl-font-mono, monospace);
  font-weight: 700;
  color: var(--sl-primary, #2563eb);
  transition: color 0.2s ease;
}

.trigger-idx-badge.is-new-red {
  color: var(--sl-danger, #dc2626) !important;
  font-weight: 800;
}

.system-tag-pill {
  font-size: 10px;
  color: var(--sl-text-secondary, #64748b);
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 3px;
}

/* 块结构 */
.trigger-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.condition-logic-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  background: #eff6ff;
  border-left: 2.5px solid var(--sl-primary, #2563eb);
  border-radius: 0 3px 3px 0;
  margin-bottom: 2px;
}

.condition-logic-bar strong {
  font-size: 10.5px;
  color: var(--sl-primary, #2563eb);
  font-weight: 700;
}

.condition-logic-bar span {
  font-size: 10px;
  color: var(--sl-text-secondary, #64748b);
}

.rule-row-container {
  display: flex;
  flex-direction: column;
}

/* 行栅格结构 */
.rule-inputs-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.condition-grid {
  display: grid;
  grid-template-columns: 24px minmax(110px, 1.2fr) 74px minmax(90px, 1fr) 36px;
  gap: 6px;
  align-items: center;
}

.action-grid {
  display: grid;
  grid-template-columns: 24px 100px minmax(0, 1fr) 36px;
  gap: 6px;
  align-items: center;
}

/* EMIT 动作行：接口名称自适应，信号选择拓宽至 102px 确保 ACTIVE 完整展示 */
.action-grid.emit-grid {
  grid-template-columns: 24px 100px minmax(105px, 1fr) 102px 36px;
}

/* UPDATE 动作行：节点生命周期扩宽至 135px，目标值选择保持 88px+ 自适应 */
.action-grid.update-grid {
  grid-template-columns: 24px 100px 135px minmax(88px, 1fr) 36px;
}

.action-extra-grid {
  display: grid;
  grid-template-columns: 24px 115px minmax(0, 1fr);
  gap: 6px;
  align-items: flex-start;
  margin-top: 4px;
}

.host-interface-tag {
  height: 28px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: 3px;
  font-size: 11px;
  box-sizing: border-box;
  overflow: hidden;
  min-width: 0;
}

.tag-lbl {
  color: var(--sl-text-secondary, #64748b);
  font-size: 10.5px;
  flex: none;
}

.tag-val {
  color: var(--sl-text-heading, #0f172a);
  font-weight: 600;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

/* 徽标 */
.rule-badge {
  height: 28px;
  display: grid;
  place-items: center;
  font-size: 10.5px;
  font-weight: 700;
  border-radius: 3px;
  user-select: none;
}

.cond-badge {
  background: var(--sl-primary-light, #eff6ff);
  color: var(--sl-primary, #2563eb);
}

.act-badge {
  background: #f0fdf4;
  color: #16a34a;
}

.val-badge {
  background: #faf5ff;
  color: #9333ea;
}

.col-cond-obj { min-width: 0; }
.col-cond-op { min-width: 0; }
.col-cond-val { min-width: 0; }
.col-act-name { min-width: 0; }
.col-act-signal { min-width: 0; }
.col-act-update-type { min-width: 0; }
.col-act-update-target { min-width: 0; }
.col-val-source { min-width: 0; }
.col-val-input { min-width: 0; }

.btn-row-action {
  background: transparent;
  border: none;
  color: var(--sl-danger, #dc2626);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  padding: 0 4px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}

.btn-row-action:hover {
  text-decoration: underline;
}

.row-add-wrap {
  display: flex;
  margin-left: 30px;
}

.btn-sub-add {
  background: transparent;
  border: none;
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  padding: 2px 0;
}

.btn-sub-add:hover {
  text-decoration: underline;
}
</style>
