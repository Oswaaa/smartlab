<template>
  <div class="workflow-expression-editor" :class="[variantClass]">
    <div class="expression-inner-box" :class="[variantClass]">
      <!-- 1. 操作符与变量工具栏 (宽敞业务模式下清晰分为三行) -->
      <div v-if="variant === 'spacious'" class="spacious-toolbar">
        <!-- 第一行：常用运算符 -->
        <div class="toolbar-row">
          <span class="section-label">运算符:</span>
          <div class="operator-chips">
            <button
              v-for="op in operatorTemplates"
              :key="op.label"
              type="button"
              class="btn-op-chip"
              :title="`插入 ${op.label}`"
              @mousedown.prevent.stop
              @click.stop="insertText(op.value)"
            >
              {{ op.label }}
            </button>
          </div>
        </div>

        <!-- 第二行：内部变量 -->
        <div class="toolbar-row">
          <span class="section-label">内部变量:</span>
          <div class="variable-chips">
            <button
              v-for="variable in formulaVariables"
              :key="variable.name"
              type="button"
              class="btn-var-chip"
              :title="`${variable.name} (${variable.dataType})`"
              @mousedown.prevent.stop
              @click.stop="insertVariable(variable.name)"
            >
              @{{ variable.name }}
            </button>
            <span v-if="!formulaVariables.length" class="var-empty-hint">暂无可用内部变量</span>
          </div>
        </div>

        <!-- 第三行：时序聚合函数 -->
        <div v-if="temporal" class="toolbar-row temporal-row">
          <span class="section-label">时序聚合:</span>
          <div class="temporal-chips-spacious">
            <el-tooltip
              v-for="item in workflowTemporalFunctions"
              :key="item.key"
              placement="top"
              :show-after="250"
              effect="dark"
            >
              <template #content>
                <div class="function-tooltip">
                  <strong>{{ item.name }} ({{ item.functionName }})</strong>
                  <span>{{ item.description }}</span>
                  <code>示例：{{ item.example }}</code>
                </div>
              </template>
              <button
                type="button"
                class="btn-fn-chip-spacious"
                @mousedown.prevent.stop
                @click.stop="applyTemplate(item.key)"
              >
                {{ item.name }}
              </button>
            </el-tooltip>
          </div>
        </div>
      </div>

      <!-- 紧凑单行工具栏 (针对触发器等嵌入式场景) -->
      <div v-else class="compact-toolbar">
        <div class="toolbar-chips-group">
          <!-- 常用运算符号 -->
          <div class="operator-chips">
            <button
              v-for="op in operatorTemplates"
              :key="op.label"
              type="button"
              class="btn-op-chip"
              :title="`插入 ${op.label}`"
              @mousedown.prevent.stop
              @click.stop="insertText(op.value)"
            >
              {{ op.label }}
            </button>
          </div>

          <div class="chips-divider"></div>

          <!-- 内部变量标签 -->
          <div class="variable-chips">
            <button
              v-for="variable in formulaVariables"
              :key="variable.name"
              type="button"
              class="btn-var-chip"
              :title="`${variable.name} (${variable.dataType})`"
              @mousedown.prevent.stop
              @click.stop="insertVariable(variable.name)"
            >
              @{{ variable.name }}
            </button>
            <span v-if="!formulaVariables.length" class="var-empty-hint">暂无可用变量</span>
          </div>

          <!-- 紧凑时序函数 -->
          <template v-if="temporal">
            <div class="chips-divider"></div>
            <div class="temporal-chips">
              <el-tooltip
                v-for="item in workflowTemporalFunctions"
                :key="item.key"
                placement="top"
                :show-after="250"
                effect="dark"
              >
                <template #content>
                  <div class="function-tooltip">
                    <strong>{{ item.name }}</strong>
                    <span>{{ item.description }}</span>
                    <code>示例：{{ item.example }}</code>
                  </div>
                </template>
                <button
                  type="button"
                  class="btn-fn-chip"
                  @mousedown.prevent.stop
                  @click.stop="applyTemplate(item.key)"
                >
                  {{ item.name }}
                </button>
              </el-tooltip>
            </div>
          </template>
        </div>
      </div>

      <!-- 2. 公式输入栏 (像素级对齐的双层高亮胶囊输入) -->
      <div class="formula-composer-row" :class="[variantClass]">
        <div class="formula-input-shell" :class="[variantClass, { 'is-readonly': readonly }]">
          <!-- 语法高亮层 (蓝色变量胶囊) -->
          <div class="formula-input-highlight" aria-hidden="true">
            <div
              class="formula-input-highlight-content"
              :class="[variantClass]"
              :style="{ transform: `translateX(-${inputScrollLeft}px)` }"
            >
              <template v-for="(token, index) in inputTokens" :key="`${index}-${token.value}`">
                <span :class="{ 'formula-input-token': token.variable }">{{ token.value }}</span>
              </template>
            </div>
          </div>

          <!-- 真实输入层 (完全透明同步，光标对齐) -->
          <input
            ref="formulaInput"
            class="formula-plain-input"
            :class="[variantClass]"
            :value="displayValue"
            :placeholder="assignment ? '例如：@目标变量 = @输入变量 * 2' : '例如：@temp + 1'"
            :disabled="readonly"
            spellcheck="false"
            @input="onInput"
            @focus="onFocus"
            @blur="commitTypedValue"
            @keydown="onKeydown"
            @scroll="syncHighlightScroll"
          />

          <!-- 变量智能补全浮层 -->
          <div
            v-if="!readonly && suggestionVisible && suggestions.length"
            class="formula-suggestions"
          >
            <button
              v-for="item in suggestions"
              :key="item.name"
              type="button"
              @mousedown.prevent.stop
              @click.stop="selectSuggestion(item)"
            >
              <span class="sug-name">@{{ item.name }}</span>
              <small class="sug-type">{{ item.dataType }}</small>
            </button>
          </div>
        </div>
      </div>

      <!-- 3. 底部提示与校验信息 -->
      <div class="formula-footer" :class="[variantClass]">
        <span v-if="errors.length" class="expression-error">{{ errors[0] }}</span>
        <span v-else class="expression-help">
          {{ assignment ? '计算结果将写入等号左侧变量。' : '计算结果将在触发时自动写入目标变量。' }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  formatWorkflowExpression,
  serializeWorkflowExpression,
  validateWorkflowExpression,
  workflowExpressionDisplayTokens,
  workflowTemporalFunctions,
} from '../../../../../utils/workflowDesignerRules.js'

type Item = Record<string, any>
const props = withDefaults(
  defineProps<{
    modelValue?: string
    variables?: Item[]
    readonly?: boolean
    assignment?: boolean
    temporal?: boolean
    variant?: 'compact' | 'spacious'
  }>(),
  {
    modelValue: '',
    variables: () => [],
    readonly: false,
    assignment: false,
    temporal: false,
    variant: 'compact',
  }
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const displayValue = ref('')
const formulaInput = ref<HTMLInputElement | null>(null)
const suggestions = ref<Item[]>([])
const suggestionVisible = ref(false)
const inputScrollLeft = ref(0)
const formulaVariables = computed(() => props.variables || [])
const variableNames = computed(() => new Set(formulaVariables.value.map(item => item.name)))
const variantClass = computed(() => props.variant === 'spacious' ? 'is-spacious' : 'is-compact')

watch(
  [() => props.modelValue, () => props.variables],
  ([value]) => {
    const next = bindVariables(String(value ?? ''))
    if (next !== displayValue.value) displayValue.value = next
  },
  { immediate: true, deep: true }
)

const operatorTemplates = [
  { label: '+', value: ' + ' },
  { label: '-', value: ' - ' },
  { label: '*', value: ' * ' },
  { label: '/', value: ' / ' },
  { label: '=', value: ' = ' },
  { label: '(', value: '(' },
  { label: ')', value: ')' },
]

const inputTokens = computed(() =>
  workflowExpressionDisplayTokens(displayValue.value, [...variableNames.value])
)

const errors = computed(() =>
  displayValue.value.trim()
    ? validateWorkflowExpression(displayValue.value, props.variables, {
        assignment: props.assignment,
        temporal: props.temporal
      })
    : []
)

function bindVariables(value: string) {
  const names = [...variableNames.value].sort((left, right) => right.length - left.length)
  if (!names.length) return value
  return value.replace(
    new RegExp(`(?<![@\\p{L}\\p{N}_])(${names.map(escapeRegExp).join('|')})(?![\\p{L}\\p{N}_])`, 'gu'),
    '@$1'
  )
}

function publishValue(value: string) {
  displayValue.value = value
  emit('update:modelValue', serializeWorkflowExpression(value, [...variableNames.value]))
}

function onInput(event: Event) {
  const value = (event.target as HTMLInputElement).value
  const next = bindVariables(value)
  displayValue.value = next
  emit('update:modelValue', serializeWorkflowExpression(next, [...variableNames.value]))
  refreshSuggestions(next)
}

function onFocus() {
  refreshSuggestions()
}

function commitTypedValue() {
  setTimeout(() => {
    suggestionVisible.value = false
  }, 180)
}

function syncHighlightScroll(event: Event) {
  inputScrollLeft.value = (event.target as HTMLInputElement).scrollLeft
}

function refreshSuggestions(query = displayValue.value) {
  if (props.readonly) return
  const token = partialMention(query)
  if (!token) {
    suggestions.value = []
    suggestionVisible.value = false
    return
  }
  const needle = token.partial.toLowerCase()
  suggestions.value = formulaVariables.value.filter(item =>
    item.name.toLowerCase().includes(needle)
  )
  suggestionVisible.value = suggestions.value.length > 0
}

function selectSuggestion(item: Item) {
  const query = displayValue.value
  const token = partialMention(query)
  if (!token) return
  const next = `${query.slice(0, token.start)}@${item.name} ${query.slice(token.end)}`
  publishValue(next)
  suggestions.value = []
  suggestionVisible.value = false
  void nextTick(() => {
    formulaInput.value?.focus()
    formulaInput.value?.setSelectionRange(next.length, next.length)
  })
}

function partialMention(query: string) {
  const start = query.lastIndexOf('@')
  if (start < 0) return null
  let end = start + 1
  while (end < query.length && !/[\s+\-*/(),=]/.test(query[end])) end += 1
  return { start, end, partial: query.slice(start + 1, end) }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    suggestionVisible.value = false
    return
  }
  if (event.key === 'Enter' && suggestionVisible.value && suggestions.value.length) {
    event.preventDefault()
    selectSuggestion(suggestions.value[0])
  }
}

function insertText(value: string) {
  const input = formulaInput.value
  const focused = !!input && document.activeElement === input
  const start = focused ? input?.selectionStart ?? displayValue.value.length : displayValue.value.length
  const end = focused ? input?.selectionEnd ?? start : start
  const next = displayValue.value.slice(0, start) + value + displayValue.value.slice(end)
  publishValue(next)
  suggestions.value = []
  suggestionVisible.value = false
  void nextTick(() => {
    const caret = start + value.length
    input?.focus()
    input?.setSelectionRange(caret, caret)
  })
}

function insertVariable(name: string) {
  insertText(`@${name}`)
}

function applyTemplate(command: string) {
  const fn = workflowTemporalFunctions.find(item => item.key === command)
  if (!fn) return
  const placeholder = fn.template
  const next = props.assignment ? ` = ${placeholder}` : placeholder
  publishValue(next)
  suggestions.value = []
  suggestionVisible.value = false
  void nextTick(() => {
    const input = formulaInput.value
    const caret = props.assignment ? 0 : next.indexOf('(') + 1
    input?.focus()
    input?.setSelectionRange(caret, caret)
  })
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
</script>

<style scoped>
.workflow-expression-editor {
  width: 100%;
  box-sizing: border-box;
}

.expression-inner-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  box-sizing: border-box;
}

.expression-inner-box.is-compact {
  gap: 4px;
  padding: 6px 8px;
}

/* 宽敞业务配置工具栏 */
.spacious-toolbar {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.chip-section {
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-text-secondary, #64748b);
  flex: none;
}

.temporal-chips-spacious {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.btn-fn-chip-spacious {
  height: 24px;
  line-height: 22px;
  padding: 0 8px;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  background: #ffffff;
  color: #334155;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-fn-chip-spacious:hover {
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
  background: #eff6ff;
}

/* 紧凑单行工具栏 */
.compact-toolbar {
  display: flex;
  align-items: center;
  overflow-x: auto;
}

.toolbar-chips-group {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}

.operator-chips {
  display: flex;
  align-items: center;
  gap: 3px;
  flex: none;
}

.btn-op-chip {
  height: 22px;
  min-width: 22px;
  padding: 0 5px;
  border: 1px solid #cbd5e1;
  border-radius: 3px;
  background: #ffffff;
  color: var(--sl-text-heading, #334155);
  font-size: 11.5px;
  font-family: var(--sl-font-mono, monospace);
  font-weight: 600;
  line-height: 20px;
  cursor: pointer;
  transition: all 0.15s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-op-chip:hover {
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
  background: #eff6ff;
}

.chips-divider {
  width: 1px;
  height: 16px;
  background: #cbd5e1;
  margin: 0 4px;
  flex: none;
}

.variable-chips {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.btn-var-chip {
  height: 22px;
  line-height: 20px;
  padding: 0 7px;
  border: 1px solid #bfdbfe;
  border-radius: 3px;
  background: #eff6ff;
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  font-family: var(--sl-font-mono, monospace);
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}

.btn-var-chip:hover {
  border-color: #93c5fd;
  background: #dbeafe;
}

.var-empty-hint {
  font-size: 10.5px;
  color: #94a3b8;
  padding: 0 4px;
}

.temporal-chips {
  display: flex;
  align-items: center;
  gap: 3px;
}

.btn-fn-chip {
  height: 22px;
  line-height: 20px;
  padding: 0 6px;
  border: 1px solid #cbd5e1;
  border-radius: 3px;
  background: #ffffff;
  color: #475569;
  font-size: 10.5px;
  cursor: pointer;
  white-space: nowrap;
}

.btn-fn-chip:hover {
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
  background: #eff6ff;
}

.function-tooltip {
  display: grid;
  gap: 3px;
  max-width: 260px;
  padding: 2px;
}

.function-tooltip strong {
  font-size: 11.5px;
  font-weight: 600;
}

.function-tooltip span {
  font-size: 10.5px;
  line-height: 1.4;
}

.function-tooltip code {
  color: #93c5fd;
  font-family: var(--sl-font-mono, monospace);
  font-size: 10px;
}

/* 输入栏 (对齐胶囊渲染) */
.formula-composer-row {
  display: flex;
  width: 100%;
}

.formula-input-shell {
  position: relative;
  width: 100%;
  height: 30px;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  background: #ffffff;
  transition: all 0.15s ease;
  box-sizing: border-box;
  overflow: hidden;
}

.formula-input-shell.is-spacious {
  height: 36px;
}

.formula-input-shell.is-readonly {
  background: #f8fafc;
}

.formula-input-shell:focus-within {
  border-color: var(--sl-primary, #2563eb);
  box-shadow: 0 0 0 1.5px rgba(37, 99, 235, 0.15);
}

.formula-input-highlight {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  pointer-events: none;
  display: flex;
  align-items: center;
}

.formula-input-highlight-content {
  width: max-content;
  min-width: 100%;
  box-sizing: border-box;
  padding: 0 8px;
  color: var(--sl-text-heading, #0f172a);
  font-family: Consolas, Monaco, "Courier New", monospace !important;
  font-size: 11.5px;
  line-height: 28px;
  height: 28px;
  white-space: pre;
  letter-spacing: 0;
  word-spacing: 0;
  display: inline-block;
  will-change: transform;
}

.formula-input-highlight-content.is-spacious {
  font-size: 12.5px;
  line-height: 34px;
  height: 34px;
  padding: 0 10px;
}

/* 变量高亮蓝色胶囊：0水平内外边距，阴影模拟边框，零像素位移 */
.formula-input-token {
  display: inline;
  padding: 0;
  margin: 0;
  background-color: #dbeafe;
  color: #1d4ed8;
  border-radius: 3px;
  box-shadow: 0 0 0 1px #93c5fd;
  font-weight: inherit;
}

.formula-plain-input {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 28px;
  box-sizing: border-box;
  padding: 0 8px;
  border: 0;
  background: transparent !important;
  color: transparent;
  caret-color: #0f172a;
  font-family: Consolas, Monaco, "Courier New", monospace !important;
  font-size: 11.5px;
  line-height: 28px;
  letter-spacing: 0;
  word-spacing: 0;
  outline: 0;
}

.formula-plain-input.is-spacious {
  font-size: 12.5px;
  line-height: 34px;
  height: 34px;
  padding: 0 10px;
}

.formula-plain-input::selection {
  background: rgba(37, 99, 235, 0.25);
  color: transparent;
}

.formula-plain-input::placeholder {
  color: #94a3b8;
}

.formula-plain-input:disabled {
  background: transparent !important;
  cursor: not-allowed;
}

.formula-suggestions {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% + 2px);
  z-index: 50;
  max-height: 160px;
  overflow-y: auto;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: 4px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.formula-suggestions button {
  width: 100%;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 8px;
  border: 0;
  border-bottom: 1px solid #f1f5f9;
  background: #ffffff;
  color: var(--sl-text-heading, #0f172a);
  text-align: left;
  cursor: pointer;
  font-size: 11px;
}

.formula-suggestions button:hover {
  background: #eff6ff;
}

.sug-name {
  font-family: var(--sl-font-mono, monospace);
  color: var(--sl-primary, #2563eb);
  font-weight: 600;
}

.sug-type {
  color: var(--sl-text-secondary, #64748b);
  font-size: 9.5px;
}

.formula-footer {
  display: flex;
  align-items: center;
  padding: 0 2px;
}

.expression-error {
  color: var(--sl-danger, #dc2626);
  font-size: 11px;
  font-weight: 500;
}

.expression-help {
  color: var(--sl-text-secondary, #94a3b8);
  font-size: 11px;
}
</style>
