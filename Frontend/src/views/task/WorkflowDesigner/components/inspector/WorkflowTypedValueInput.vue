<template>
  <el-input-number v-if="dataType === 'INTEGER'" :model-value="modelValue as number" :precision="0" @update:model-value="emitValue" />
  <el-input-number v-else-if="dataType === 'DOUBLE'" :model-value="modelValue as number" @update:model-value="emitValue" />
  <el-switch v-else-if="dataType === 'BOOLEAN'" :model-value="modelValue as boolean" @update:model-value="emitValue" />
  <el-input v-else-if="dataType === 'STRING'" :model-value="modelValue as string" @update:model-value="emitValue" />
  <div v-else class="json-entry-list">
    <div v-for="(entry, index) in jsonEntries" :key="entry.id" class="json-entry-row">
      <el-input v-model="entry.key" placeholder="键" @input="emitJson" />
      <el-input v-model="entry.value" placeholder="值" @input="emitJson" />
      <el-button class="btn-aliyun-danger-link" link @click="removeEntry(index)">删除</el-button>
    </div>
    <el-button class="btn-aliyun" @click="addEntry">添加键值</el-button>
    <p v-if="jsonError" class="json-error">{{ jsonError }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

type DataType = 'INTEGER' | 'DOUBLE' | 'STRING' | 'BOOLEAN' | 'JSON'
type JsonEntry = { id: number, key: string, value: unknown }

const props = defineProps<{ modelValue: unknown, dataType: DataType }>()
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()
const jsonEntries = ref<JsonEntry[]>([])
const jsonError = ref('')
let nextEntryId = 0

function toEntries(value: unknown): JsonEntry[] {
  if (!value || Array.isArray(value) || typeof value !== 'object') return []
  return Object.entries(value as Record<string, unknown>).map(([key, entryValue]) => ({ id: nextEntryId++, key, value: entryValue }))
}

watch(() => [props.dataType, props.modelValue], () => {
  if (props.dataType === 'JSON') jsonEntries.value = toEntries(props.modelValue)
}, { immediate: true, deep: true })

function emitValue(value: unknown) {
  emit('update:modelValue', value)
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
.json-entry-list{display:grid;gap:8px}.json-entry-row{display:grid;grid-template-columns:1fr 1fr auto;gap:8px}.json-error{margin:0;color:var(--el-color-danger);font-size:12px}
</style>
