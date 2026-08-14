<template>
  <div class="workflow-expression-editor">
    <div v-if="!readonly" class="expression-toolbar">
      <div class="toolbar-group operator-group">
        <span class="toolbar-label">操作符</span>
        <button
          v-for="item in operatorTemplates"
          :key="item.value"
          type="button"
          :title="item.label"
          @mousedown.prevent.stop
          @click.stop="insertText(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
      <div v-if="temporal" class="toolbar-group temporal-function-picker">
        <span class="toolbar-label">时序函数</span>
        <el-tooltip v-for="item in workflowTemporalFunctions" :key="item.key" placement="top" :show-after="250" effect="dark">
          <template #content>
            <div class="function-tooltip"><strong>{{ item.name }}</strong><span>{{ item.description }}</span><code>示例：{{ item.example }}</code></div>
          </template>
          <button type="button" class="function-button" @mousedown.prevent.stop @click.stop="applyTemplate(item.key)">{{ item.name }}</button>
        </el-tooltip>
      </div>
      <div class="toolbar-group variable-picker">
        <span class="toolbar-label">内部变量</span>
        <button
          v-for="variable in formulaVariables"
          :key="variable.name"
          type="button"
          :title="`${variable.name} · ${variable.dataType}`"
          @mousedown.prevent.stop
          @click.stop="insertVariable(variable.name)"
        >
          @{{ variable.name }}
        </button>
        <span v-if="!formulaVariables.length" class="picker-empty">暂无内部变量</span>
      </div>
    </div>

    <div class="formula-composer">
      <div class="formula-input-shell">
        <div class="formula-input-highlight" aria-hidden="true">
          <div class="formula-input-highlight-content" :style="{ transform: `translateX(-${inputScrollLeft}px)` }">
            <template v-for="(token,index) in inputTokens" :key="`${index}-${token.value}`">
              <span :class="{ 'formula-input-token': token.variable }">{{ token.value }}</span>
            </template>
          </div>
        </div>
        <input
          ref="formulaInput"
          class="formula-plain-input"
          :value="displayValue"
          :placeholder="assignment ? '例如：@温度变量 = @温度输入 / 10' : '例如：@温度输入 / 10'"
          :disabled="readonly"
          spellcheck="false"
          @input="onInput"
          @focus="onFocus"
          @blur="commitTypedValue"
          @keydown="onKeydown"
          @scroll="syncHighlightScroll"
        />
        <div v-if="!readonly && suggestionVisible && suggestions.length" class="formula-suggestions">
          <button v-for="item in suggestions" :key="item.name" type="button" @mousedown.prevent.stop @click.stop="selectSuggestion(item)">
            <span>@{{ item.name }}</span>
            <small>{{ item.dataType }}</small>
          </button>
        </div>
      </div>
      <div class="formula-result-preview">
        <code>{{ compiledFormula || '等待输入' }}</code>
      </div>
    </div>

    <p v-if="errors.length" class="expression-error">{{ errors[0] }}</p>
    <p v-else class="expression-help">{{ assignment ? '结果写入等号左侧变量，并在本轮供触发器判断。' : '结果写入当前 UPDATE 动作的目标变量。' }}</p>
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
const props = withDefaults(defineProps<{ modelValue?: string, variables?: Item[], readonly?: boolean, assignment?: boolean, temporal?: boolean }>(), {
  modelValue: '',
  variables: () => [],
  readonly: false,
  assignment: false,
  temporal: false,
})
const emit = defineEmits<{ 'update:modelValue':[value:string] }>()
const displayValue = ref('')
const formulaInput = ref<HTMLInputElement | null>(null)
const suggestions = ref<Item[]>([])
const suggestionVisible = ref(false)
const inputScrollLeft = ref(0)
const formulaVariables = computed(() => props.variables || [])
const variableNames = computed(() => new Set(formulaVariables.value.map(item => item.name)))

watch(() => props.modelValue, value => {
  const next = bindVariables(String(value ?? ''))
  if (next !== displayValue.value) displayValue.value = next
}, { immediate:true })

const operatorTemplates = [
  { label:'=', value:' = ' },
  { label:'+', value:' + ' },
  { label:'-', value:' - ' },
  { label:'*', value:' * ' },
  { label:'/', value:' / ' },
  { label:'(', value:'(' },
  { label:')', value:')' },
]

const inputTokens = computed(() => workflowExpressionDisplayTokens(displayValue.value, [...variableNames.value]))
const compiledFormula = computed(() => formatWorkflowExpression(displayValue.value, [...variableNames.value]))

const errors = computed(() => displayValue.value.trim()
  ? validateWorkflowExpression(displayValue.value, props.variables, { assignment: props.assignment, temporal: props.temporal })
  : [])

function bindVariables(value:string) {
  const names = [...variableNames.value].sort((left,right) => right.length - left.length)
  if (!names.length) return value
  return value.replace(new RegExp(`(?<![@\\p{L}\\p{N}_])(${names.map(escapeRegExp).join('|')})(?![\\p{L}\\p{N}_])`, 'gu'), '@$1')
}

function publishValue(value:string) {
  displayValue.value = value
  emit('update:modelValue', serializeWorkflowExpression(value, [...variableNames.value]))
}

function onInput(event:Event) {
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
  setTimeout(() => { suggestionVisible.value = false }, 150)
  const next = bindVariables(displayValue.value)
  if (next !== displayValue.value) publishValue(next)
}

function refreshSuggestions(query = displayValue.value) {
  const mention = partialMention(query)
  if (!mention) {
    suggestions.value = []
    suggestionVisible.value = false
    return
  }
  suggestions.value = formulaVariables.value
    .filter(item => item.name.startsWith(mention.partial))
    .map(item => ({ value:`@${item.name}`, name:item.name, dataType:item.dataType }))
  suggestionVisible.value = suggestions.value.length > 0
}

function selectSuggestion(item:Item) {
  const selected = `@${item.name}`
  const mention = partialMention(displayValue.value)
  const next = mention
    ? displayValue.value.slice(0,mention.start)+selected+displayValue.value.slice(mention.end)
    : displayValue.value+selected
  publishValue(next)
  suggestions.value = []
  suggestionVisible.value = false
  void nextTick(() => {
    formulaInput.value?.focus()
    formulaInput.value?.setSelectionRange(next.length,next.length)
  })
}

function partialMention(query:string) {
  const start = query.lastIndexOf('@')
  if (start < 0) return null
  let end = start + 1
  while (end < query.length && !/[\s+\-*/(),=]/.test(query[end])) end += 1
  return { start, end, partial:query.slice(start + 1,end) }
}

function onKeydown(event:KeyboardEvent) {
  if (event.key === 'Escape') {
    suggestionVisible.value = false
    return
  }
  if (event.key === 'Enter' && suggestionVisible.value && suggestions.value.length) {
    event.preventDefault()
    selectSuggestion(suggestions.value[0])
  }
}

function syncHighlightScroll(event:Event) {
  inputScrollLeft.value = (event.target as HTMLInputElement).scrollLeft
}

function insertText(value:string) {
  const input = formulaInput.value
  const focused = !!input && document.activeElement === input
  const start = focused ? input?.selectionStart ?? displayValue.value.length : displayValue.value.length
  const end = focused ? input?.selectionEnd ?? start : start
  const next = displayValue.value.slice(0,start)+value+displayValue.value.slice(end)
  publishValue(next)
  suggestions.value = []
  suggestionVisible.value = false
  void nextTick(() => {
    const caret = start+value.length
    input?.focus()
    input?.setSelectionRange(caret,caret)
  })
}

function insertVariable(name:string) {
  insertText(`@${name}`)
}

function applyTemplate(command:string) {
  const fn = workflowTemporalFunctions.find(item => item.key === command)
  if (!fn) return
  const placeholder = fn.template
  const next = props.assignment ? ` = ${placeholder}` : placeholder
  publishValue(next)
  suggestions.value = []
  suggestionVisible.value = false
  void nextTick(() => {
    const input = formulaInput.value
    const caret = props.assignment ? 0 : next.indexOf('(')+1
    input?.focus()
    input?.setSelectionRange(caret,caret)
  })
}

function escapeRegExp(value:string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')
}
</script>

<style scoped>
.workflow-expression-editor{display:grid;gap:10px}
.expression-toolbar{display:grid;gap:0;border:1px solid #e5e5e5;background:#fff}
.toolbar-group{display:flex;align-items:center;flex-wrap:wrap;gap:6px;min-height:38px;padding:6px 10px;border-bottom:1px solid #f0f0f0;box-sizing:border-box}.toolbar-group:last-child{border-bottom:0}
.toolbar-label{width:54px;flex:none;color:#8c8c8c;font-size:10px}
.toolbar-group button{height:24px;min-width:26px;padding:0 7px;border:1px solid #d9d9d9;border-radius:2px;background:#fff;color:#262626;font-size:11px;line-height:22px;cursor:pointer;transition:all .15s ease}
.toolbar-group button:hover{border-color:#4096ff;background:#e6f4ff;color:#1677ff}
.temporal-function-picker{background:#fafafa}.temporal-function-picker .function-button{padding:0 9px;background:#fff}
.variable-picker button{border-color:#91caff;background:#e6f4ff;color:#0958d9}
.function-tooltip{display:grid;gap:4px;max-width:300px;padding:2px}.function-tooltip strong{font-size:12px;font-weight:500}.function-tooltip span{font-size:11px;line-height:1.6}.function-tooltip code{color:#d6e4ff;font-family:Consolas,Menlo,monospace;font-size:10px}
.picker-empty{color:#bfbfbf;font-size:10px}
.formula-composer{display:grid;gap:7px}
.formula-input-shell{position:relative;height:32px;border:1px solid #d9d9d9;background:#fff}
.formula-input-highlight{position:absolute;inset:0;z-index:1;overflow:hidden;pointer-events:none}
.formula-input-highlight-content{width:max-content;min-width:100%;box-sizing:border-box;padding:5px 9px;color:#262626;font-family:Consolas,Menlo,monospace;font-size:12px;line-height:20px;white-space:pre;will-change:transform}
.formula-input-token{border-radius:2px;background:#e6f4ff;box-shadow:0 0 0 2px #e6f4ff;color:#0958d9}
.formula-plain-input{position:relative;z-index:2;width:100%;height:30px;box-sizing:border-box;padding:5px 9px;border:0;background:transparent;color:transparent;caret-color:#262626;font-family:Consolas,Menlo,monospace;font-size:12px;line-height:20px;outline:0}
.formula-plain-input::selection{background:rgba(22,119,255,.2);color:transparent}
.formula-plain-input::placeholder{color:#bfbfbf}
.formula-plain-input:focus{border-color:#1677ff;box-shadow:0 0 0 1px #1677ff inset}
.formula-input-shell:focus-within{border-color:#1677ff;box-shadow:0 0 0 1px #1677ff}
.formula-plain-input:disabled{background:rgba(245,245,245,.72);color:transparent}
.formula-suggestions{position:absolute;left:0;right:0;top:calc(100% + 3px);z-index:20;max-height:220px;overflow-y:auto;border:1px solid #d9d9d9;background:#fff;box-shadow:0 5px 14px rgba(0,0,0,.12)}
.formula-suggestions button{width:100%;height:34px;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:0 10px;border:0;border-bottom:1px solid #f0f0f0;background:#fff;color:#262626;text-align:left;cursor:pointer}
.formula-suggestions button:hover{background:#e6f4ff}
.formula-suggestions button>span{font-family:Consolas,Menlo,monospace;color:#1677ff}
.formula-suggestions button>small{color:#8c8c8c;font-size:10px}
.formula-result-preview{display:flex;align-items:baseline;min-height:28px;padding:5px 8px;border:1px solid #f0f0f0;background:#fafafa}
.formula-result-preview code{min-width:0;color:#595959;font-family:Consolas,Menlo,monospace;font-size:11px;line-height:18px;white-space:pre-wrap;word-break:break-all}
.expression-error,.expression-help{margin:0}
.expression-error{color:#cf1322;font-size:10px}
.expression-help{color:#8c8c8c;font-size:10px}
</style>
