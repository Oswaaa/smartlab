<template>
  <div class="workflow-expression-editor">
    <div v-if="!readonly" class="expression-tools">
      <el-dropdown v-if="temporal" trigger="click" :disabled="numericVariables.length === 0" @command="applyTemplate"><el-button class="btn-aliyun" size="small" :disabled="numericVariables.length === 0">时序函数</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in workflowTemporalFunctions" :key="item.key" :command="item.key">{{ item.name }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      <span>输入 @ 可选择数值变量</span>
    </div>
    <el-autocomplete :model-value="displayValue" :disabled="readonly" :fetch-suggestions="suggestVariables" :placeholder="assignment ? '例如：@temp_out = @temp_internal * 100' : '例如：@temperature / 100'" clearable @update:model-value="update" @select="insertVariable" />
    <div v-if="mentions.length" class="mention-list"><span>引用变量</span><el-tag v-for="name in mentions" :key="name" size="small">@{{ name }}</el-tag></div>
    <p v-if="errors.length" class="expression-error">{{ errors[0] }}</p>
    <p v-else class="expression-help">{{ assignment ? '结果写入等号左侧变量，并在本轮供触发器判断。' : '结果写入当前 UPDATE 动作的目标变量。' }}</p>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { serializeWorkflowExpression, validateWorkflowExpression, workflowTemporalFunctions } from '../../../../../utils/workflowDesignerRules.js'
type Item = Record<string, any>
const props = withDefaults(defineProps<{ modelValue?: string, variables?: Item[], readonly?: boolean, assignment?: boolean, temporal?: boolean }>(), { modelValue: '', variables: () => [], readonly: false, assignment: false, temporal: false })
const emit = defineEmits<{ 'update:modelValue':[value:string] }>()
const displayValue = ref('')
const numericVariables = computed(() => props.variables.filter(item => ['INTEGER','DOUBLE'].includes(item.dataType)))
const numericNames = computed(() => new Set(numericVariables.value.map(item => item.name)))
watch(() => props.modelValue, value => { displayValue.value = value ? String(value).replace(/\b([A-Za-z_][A-Za-z0-9_]*)\b/g, (name) => numericNames.value.has(name) ? `@${name}` : name) : '' }, { immediate:true })
const mentions = computed(() => [...new Set([...displayValue.value.matchAll(/@([A-Za-z_][A-Za-z0-9_]*)/g)].map(match => match[1]))])
const errors = computed(() => validateWorkflowExpression(displayValue.value, props.variables, { assignment: props.assignment, temporal: props.temporal }))
function update(value:string) { displayValue.value = value; emit('update:modelValue', serializeWorkflowExpression(value)) }
function suggestVariables(query:string, callback:(items:Item[])=>void) { const match=query.match(/@([A-Za-z0-9_]*)$/); callback(match ? numericVariables.value.filter(item => item.name.toLowerCase().includes(match[1].toLowerCase())).map(item => ({ value:`@${item.name}`, name:item.name })) : []) }
function insertVariable(item:Item) { update(displayValue.value.replace(/@[A-Za-z0-9_]*$/, `@${item.name}`)) }
function applyTemplate(command:string) { const names=numericVariables.value.map(item=>`@${item.name}`); if (!names.length) return; const target=names[0]; const source=names[1]??names[0]; const fn=workflowTemporalFunctions.find(item=>item.key===command); if(fn) update(`${props.assignment?`${target} = `:''}${fn.template(source)}`) }
</script>
<style scoped>
.workflow-expression-editor{display:grid;gap:8px}.expression-tools{display:flex;align-items:center;gap:10px}.expression-tools span,.expression-help{color:#8c8c8c;font-size:10px}.workflow-expression-editor :deep(.el-autocomplete){width:100%;max-width:560px}.mention-list{display:flex;align-items:center;gap:6px}.mention-list>span{color:#595959;font-size:10px}.expression-error{margin:0;color:#cf1322;font-size:10px}.expression-help{margin:0}.mention-list :deep(.el-tag){border-color:#91caff;background:#e6f4ff;color:#0958d9}
</style>
