<template>
  <div class="action-editor">
    <div class="action-toolbar">
      <div>
        <div class="section-title">节点内部动作</div>
        <div class="section-help">按顺序执行；等待必须在首位，设备信号必须在末位。</div>
      </div>
      <el-dropdown trigger="click" @command="addAction">
        <el-button size="small">新增动作</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="item in availableCatalog" :key="item.actionName" :command="item.actionName">
              {{ item.displayName || item.actionName }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-empty v-if="modelValue.length === 0" description="该节点没有内部动作" :image-size="56" />
    <div v-for="(action, index) in modelValue" :key="`${action.actionName}-${index}`" class="action-row">
      <div class="action-row-head">
        <div><el-tag size="small">{{ labelOf(action.actionName) }}</el-tag><span class="action-code">{{ action.actionName }}</span></div>
        <el-button link type="danger" :disabled="nodeType === 'DEVICE_CAPABILITY_NODE' && action.actionName === 'EMIT_SIGNAL'" @click="removeAction(index)">删除</el-button>
      </div>
      <el-form label-width="92px" size="small">
        <template v-if="action.actionName === 'WAIT'">
          <el-form-item label="等待时长"><el-input-number v-model="action.payload.durationMs" :min="0" :max="86400000" /><span class="unit">毫秒</span></el-form-item>
        </template>
        <template v-else-if="action.actionName === 'ASSIGN'">
          <el-form-item label="目标变量"><el-input v-model="action.payload.target" placeholder="例如 command.target" /></el-form-item>
          <ValueSourceEditor v-model="action.payload.source" />
        </template>
        <template v-else-if="action.actionName === 'CALCULATE'">
          <el-form-item label="目标变量"><el-input v-model="action.payload.target" /></el-form-item>
          <el-form-item label="计算方式"><el-select v-model="action.payload.operator" style="width:100%"><el-option v-for="op in calculationOperators" :key="op" :label="op" :value="op" /></el-select></el-form-item>
          <el-form-item label="精度"><el-input-number v-model="action.payload.scale" :min="0" :max="12" /></el-form-item>
          <div v-for="(operand, operandIndex) in action.payload.operands" :key="operandIndex" class="operand-row">
            <ValueSourceEditor v-model="action.payload.operands[operandIndex]" compact />
            <el-button link type="danger" @click="action.payload.operands.splice(operandIndex, 1)">删除</el-button>
          </div>
          <el-button size="small" @click="action.payload.operands.push({ kind: 'LITERAL', value: 0 })">新增操作数</el-button>
        </template>
        <template v-else-if="action.actionName === 'EMIT_SIGNAL'">
          <el-form-item label="接口类型"><el-input v-model="action.payload.interfaceType" disabled /></el-form-item>
          <el-form-item label="控制信号"><el-select v-model="action.payload.signalName" style="width:100%"><el-option v-for="signal in workflowControlSignals" :key="signal" :label="signal" :value="signal" /></el-select></el-form-item>
        </template>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, defineComponent, h } from 'vue'
import { actionsAllowedForNode, createWorkflowAction } from '../workflowActions.js'

const props = defineProps({
  modelValue: { type: Array, required: true },
  nodeType: { type: String, required: true },
  catalog: { type: Array, required: true },
  calculationOperators: { type: Array, default: () => [] },
  workflowControlSignals: { type: Array, default: () => ['WF_EXECUTE_START', 'WF_EXECUTE_ABORT'] }
})
const emit = defineEmits(['update:modelValue'])
const availableCatalog = computed(() => actionsAllowedForNode(props.catalog, props.nodeType))
const labelOf = name => props.catalog.find(item => item.actionName === name)?.displayName || name
const addAction = name => {
  const action = createWorkflowAction(props.catalog, name)
  if (name === 'EMIT_SIGNAL') action.payload.signalName = props.workflowControlSignals[0] || ''
  emit('update:modelValue', [...props.modelValue, action])
}
const removeAction = index => emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))

const ValueSourceEditor = defineComponent({
  props: { modelValue: { type: Object, required: true }, compact: Boolean },
  emits: ['update:modelValue'],
  setup(valueProps, { emit: valueEmit }) {
    const update = patch => valueEmit('update:modelValue', { ...valueProps.modelValue, ...patch })
    return () => h('div', { class: 'value-source' }, [
      h('label', {}, valueProps.compact ? '来源' : '值来源'),
      h('select', { value: valueProps.modelValue.kind, onChange: event => update({ kind: event.target.value }) }, [
        h('option', { value: 'LITERAL' }, '固定值'), h('option', { value: 'VARIABLE' }, '变量')
      ]),
      h('input', {
        value: valueProps.modelValue.kind === 'VARIABLE' ? valueProps.modelValue.path || '' : valueProps.modelValue.value ?? '',
        placeholder: valueProps.modelValue.kind === 'VARIABLE' ? '变量点路径' : '固定值',
        onInput: event => update(valueProps.modelValue.kind === 'VARIABLE' ? { path: event.target.value } : { value: event.target.value })
      })
    ])
  }
})
</script>

<style scoped>
.action-editor{display:flex;flex-direction:column;gap:12px}.action-toolbar,.action-row-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-title{font-size:14px;font-weight:600;color:#111827}.section-help{margin-top:3px;font-size:12px;color:#6b7280}.action-row{border:1px solid #e5e7eb;border-radius:6px;padding:12px;background:#fff}.action-row-head{margin-bottom:12px}.action-code{margin-left:8px;color:#6b7280;font:12px Consolas,monospace}.unit{margin-left:8px;color:#6b7280}.operand-row,.value-source{display:flex;align-items:center;gap:8px;margin-bottom:8px}.value-source{flex:1}.value-source label{width:84px;color:#606266;font-size:13px}.value-source select,.value-source input{height:30px;border:1px solid #dcdfe6;border-radius:4px;padding:0 8px;background:#fff}.value-source input{flex:1;min-width:0}
</style>
