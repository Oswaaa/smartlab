<template>
  <el-dialog :model-value="modelValue" :title="mode === 'replace' ? '更换组件' : '配置组件'" width="580px" append-to-body @update:model-value="emit('update:modelValue', $event)">
    <div v-loading="loading">
      <template v-if="hasModels">
        <el-form label-position="top" size="small" class="component-action-form">
          <el-form-item label="组件槽位">
            <el-input v-model="form.componentName" :readonly="mode === 'configure'" />
          </el-form-item>
          <el-form-item v-if="mode === 'replace'" label="安装说明（可选）">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可填写安装、维修或更换说明" />
          </el-form-item>
          <el-form-item label="选择设备模型">
            <el-select :model-value="selectedModelId" style="width: 100%" placeholder="选择该类别下的物模型" @change="onModelSelect">
              <el-option v-for="m in models" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedModelId" label="绑定设备实例">
            <el-select v-model="form.selfInstanceId" clearable filterable style="width: 100%" placeholder="选择一个具体设备实例以绑定孪生">
              <el-option v-for="item in candidates" :key="item.instanceId" :label="`${item.instanceName} (${item.instanceId})`" :value="item.instanceId" />
            </el-select>
          </el-form-item>
          <el-form-item label="规格说明">
            <div class="spec-editor-container">
              <div v-for="(pair, index) in form.specificationPairs" :key="index" class="spec-pair-row">
                <el-input v-model="pair.key" placeholder="参数名 (如: 品牌)" style="flex: 1" />
                <span class="spec-sep">:</span>
                <el-input v-model="pair.value" placeholder="参数值 (如: 罗氏)" style="flex: 1.2" />
                <button class="btn-link danger" type="button" @click="form.specificationPairs.splice(index, 1)">删除</button>
              </div>
              <button class="btn-link" type="button" @click="form.specificationPairs.push({ key: '', value: '' })">
                <el-icon><Plus /></el-icon><span>添加规格参数</span>
              </button>
            </div>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-alert type="warning" :closable="false" class="mb-12" show-icon>
          该组件对应的类别下没有定义任何数字化模型。系统已退化到非数字孪生组件，仅供记录常规采购规格说明。
        </el-alert>
        <el-form label-position="top" size="small" class="component-action-form">
          <el-form-item label="组件槽位">
            <el-input v-model="form.componentName" :readonly="mode === 'configure'" />
          </el-form-item>
          <el-form-item v-if="mode === 'replace'" label="安装说明（可选）">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可填写安装、维修或更换说明" />
          </el-form-item>
          <el-form-item label="规格说明">
            <div class="spec-editor-container">
              <div v-for="(pair, index) in form.specificationPairs" :key="index" class="spec-pair-row">
                <el-input v-model="pair.key" placeholder="参数名 (如: 品牌)" style="flex: 1" />
                <span class="spec-sep">:</span>
                <el-input v-model="pair.value" placeholder="参数值 (如: Roche)" style="flex: 1.2" />
                <button class="btn-link danger" type="button" @click="form.specificationPairs.splice(index, 1)">删除</button>
              </div>
              <button class="btn-link" type="button" @click="form.specificationPairs.push({ key: '', value: '' })">
                <el-icon><Plus /></el-icon><span>添加规格参数</span>
              </button>
            </div>
          </el-form-item>
        </el-form>
      </template>
    </div>
    <template #footer>
      <div class="dialog-footer-actions">
        <button class="btn-aliyun" type="button" @click="emit('update:modelValue', false)">取消</button>
        <button class="btn-primary-blue" type="button" :disabled="saving" @click="emit('submit', { form, selectedModelId })">
          <span>{{ saving ? '保存中...' : '保存' }}</span>
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import type { DeviceInstance, DeviceModel } from './types'

defineProps<{
  modelValue: boolean
  mode: 'configure' | 'replace'
  loading: boolean
  saving: boolean
  hasModels: boolean
  models: DeviceModel[]
  candidates: DeviceInstance[]
  form: {
    componentName: string
    selfInstanceId: string
    remark: string
    specificationPairs: Array<{ key: string, value: string }>
  }
  selectedModelId: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update:selectedModelId': [value: string]
  'model-select': [modelId: string]
  submit: [payload: any]
}>()

const onModelSelect = (modelId: string) => {
  emit('update:selectedModelId', modelId)
  emit('model-select', modelId)
}
</script>

<style scoped>
.spec-editor-container { display: flex; flex-direction: column; gap: 6px; }
.spec-pair-row { display: flex; align-items: center; gap: 8px; }
.spec-sep { font-weight: 700; color: var(--sl-text-secondary); }
.dialog-footer-actions { display: flex; justify-content: flex-end; gap: 8px; }
.mb-12 { margin-bottom: 12px; }
</style>
