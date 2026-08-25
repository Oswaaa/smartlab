<template>
  <div class="typed-value-input">
    <el-select v-if="options.length" :model-value="modelValue" :disabled="disabled" :clearable="nullable" placeholder="请选择" @update:model-value="emitValue">
      <el-option v-for="option in options" :key="String(option)" :label="String(option)" :value="option" />
    </el-select>
    <el-input-number v-else-if="dataType === 'INTEGER'" :model-value="numericValue" :disabled="disabled" :precision="0" :controls="false" placeholder="请输入整数" @update:model-value="emitValue" />
    <el-input-number v-else-if="dataType === 'DOUBLE'" :model-value="numericValue" :disabled="disabled" :controls="false" placeholder="请输入数值" @update:model-value="emitValue" />
    <el-select v-else-if="dataType === 'BOOLEAN'" :model-value="booleanValue" :disabled="disabled" :clearable="nullable" placeholder="请选择" @update:model-value="emitValue">
      <el-option label="是" :value="true" />
      <el-option label="否" :value="false" />
    </el-select>
    <el-input v-else-if="dataType === 'STRING'" :model-value="modelValue as string" :disabled="disabled" :clearable="nullable" placeholder="请输入" @update:model-value="emitValue" />
    <div v-else class="json-entry-list">
      <div v-for="(entry, index) in jsonEntries" :key="entry.id" class="json-entry-row">
        <el-input v-model="entry.key" :disabled="disabled" placeholder="键" @input="emitJson" />
        <el-input v-model="entry.value" :disabled="disabled" placeholder="值" @input="emitJson" />
        <el-button v-if="!disabled" class="btn-aliyun-danger-link" link @click="removeEntry(index)">删除</el-button>
      </div>
      <el-button v-if="!disabled" class="btn-aliyun" @click="addEntry">添加键值</el-button>
      <p v-if="jsonError" class="json-error">{{ jsonError }}</p>
    </div>
    <button v-if="showClear" class="clear-value" type="button" @click="emitValue(null)">清除</button>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'

type DataType = 'INTEGER' | 'DOUBLE' | 'STRING' | 'BOOLEAN' | 'JSON'
type JsonEntry = { id: number, key: string, value: unknown }

const props = withDefaults(defineProps<{ modelValue: unknown, dataType: DataType, disabled?: boolean, nullable?: boolean, options?: unknown[] }>(), {
  disabled: false,
  nullable: false,
  options: () => []
})
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()
const jsonEntries = ref<JsonEntry[]>([])
const jsonError = ref('')
let nextEntryId = 0
const numericValue = computed(() => typeof props.modelValue === 'number' ? props.modelValue : undefined)
const booleanValue = computed(() => typeof props.modelValue === 'boolean' ? props.modelValue : undefined)
const showClear = computed(() => props.nullable && !props.disabled && !props.options.length
  && (props.dataType === 'INTEGER' || props.dataType === 'DOUBLE' || props.dataType === 'JSON')
  && props.modelValue !== null && props.modelValue !== undefined)

function toEntries(value: unknown): JsonEntry[] {
  if (!value || Array.isArray(value) || typeof value !== 'object') return []
  return Object.entries(value as Record<string, unknown>).map(([key, entryValue]) => ({ id: nextEntryId++, key, value: entryValue }))
}

watch(() => [props.dataType, props.modelValue], () => {
  if (props.dataType === 'JSON') jsonEntries.value = toEntries(props.modelValue)
}, { immediate: true, deep: true })

function emitValue(value: unknown) {
  emit('update:modelValue', value === undefined && props.nullable ? null : value)
}

function addEntry() {
  jsonEntries.value.push({ id: nextEntryId++, key: '', value: '' })
}

function removeEntry(index: number) {
  jsonEntries.value.splice(index, 1)
  emitJson()
}

function emitJson() {
  const keys = jsonEntries.value.map(entry => entry.key.trim())
  if (keys.some(key => !key)) {
    jsonError.value = 'JSON键不能为空'
    return
  }
  if (new Set(keys).size !== keys.length) {
    jsonError.value = 'JSON键不能重复'
    return
  }
  jsonError.value = ''
  emit('update:modelValue', Object.fromEntries(jsonEntries.value.map(entry => [entry.key.trim(), entry.value])))
}
</script>
<style scoped>
.typed-value-input{display:grid;gap:6px}.json-entry-list{display:grid;gap:10px}.json-entry-row{display:grid;grid-template-columns:minmax(120px,1fr) minmax(160px,2fr) auto;gap:10px;padding:10px;border:1px solid #e5e6eb;background:var(--sl-bg-hover)}.json-error{margin:0;color:#ff4d4f;font-size:12px}.clear-value{justify-self:start;padding:0;border:0;background:transparent;color:#8a94a3;font-size:11px;cursor:pointer}:deep(.el-input-number),:deep(.el-input),:deep(.el-select){width:100%}
</style>
