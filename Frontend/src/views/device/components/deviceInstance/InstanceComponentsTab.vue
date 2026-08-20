<template>
  <div class="tab-pane-content">
    <div class="sub-section-head">
      <span class="head-title">组件结构清单 (BOM)</span>
      <span class="head-desc">当前设备实例安装的物理子组件与孪生绑定关系</span>
    </div>
    <el-table :data="instanceComponents" stripe border size="small" v-loading="loadingComponents" class="component-table">
      <el-table-column label="组件槽位名称" min-width="150" prop="componentName" />
      <el-table-column label="所属类别" min-width="130">
        <template #default="{ row }">{{ categoriesMap[String(row.categoryId)] || '-' }}</template>
      </el-table-column>
      <el-table-column label="绑定孪生实例" min-width="160">
        <template #default="{ row }">
          <span class="mono">{{ instanceNameById(row.selfInstanceId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="组件状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="componentStatusType(row.status)" effect="plain">{{ row.status || '未配置' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="安装时间" min-width="160">
        <template #default="{ row }"><span class="mono">{{ formatTime(row.installTime) }}</span></template>
      </el-table-column>
      <el-table-column label="规格参数" width="100" align="center">
        <template #default="{ row }">
          <button class="btn-link" type="button" @click="showComponentSpecification(row)">
            {{ componentSpecificationLabel(row.specification) }}
          </button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <button v-if="canEdit && row.status === 'IN_USE'" class="btn-link" type="button" @click="openComponentAction(row, 'configure')">配置</button>
            <button v-if="canEdit && row.status === 'IN_USE'" class="btn-link danger" type="button" @click="markPending(row)">标记待换</button>
            <button v-if="canEdit && row.status === 'IN_USE'" class="btn-link" type="button" @click="openComponentAction(row, 'replace')">更换</button>
            <button v-if="canEdit && row.status === 'PENDING_REPLACEMENT'" class="btn-link" type="button" @click="openComponentAction(row, 'replace')">安装新件</button>
            <button class="btn-link" type="button" @click="showHistory(row)">历史</button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <ComponentActionDialog
      v-model="actionVisible"
      :mode="actionMode"
      :loading="categoryModelLoading"
      :saving="savingComponent"
      :has-models="categoryHasModels"
      :models="categoryModels"
      :candidates="candidateInstances"
      :form="actionForm"
      :selected-model-id="selectedModelId"
      @update:selected-model-id="val => selectedModelId = val"
      @model-select="onComponentModelSelect"
      @submit="submitAction"
    />
    <ComponentHistoryDialog
      v-model="historyVisible"
      :loading="loadingHistory"
      :rows="historyRows"
      :instances="instances"
      @show-spec="showComponentSpecification"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ComponentActionDialog from './ComponentActionDialog.vue'
import ComponentHistoryDialog from './ComponentHistoryDialog.vue'
import * as api from './deviceInstanceApi'
import {
  asArray,
  componentSpecificationLabel,
  componentStatusType,
  findModelById,
  formatTime,
  normalizeInstance,
  normalizeModel
} from './normalizers'
import type { DeviceInstance, DeviceModel } from './types'

const props = defineProps<{
  instance: DeviceInstance
  instances: DeviceInstance[]
  models: DeviceModel[]
  modelOptions: DeviceModel[]
  categoriesMap: Record<string, string>
  canEdit: boolean
  retired: boolean
}>()

const emit = defineEmits<{
  'components-changed': [rows: any[]]
}>()

const instanceComponents = ref<any[]>([])
const loadingComponents = ref(false)
const savingComponent = ref(false)
const actionVisible = ref(false)
const actionMode = ref<'configure' | 'replace'>('configure')
const activeComponent = ref<any>(null)
const actionForm = ref({ componentName: '', selfInstanceId: '', remark: '', specificationPairs: [] as Array<{ key: string, value: string }> })
const categoryModels = ref<DeviceModel[]>([])
const categoryModelLoading = ref(false)
const selectedModelId = ref('')
const candidateInstances = ref<DeviceInstance[]>([])
const categoryHasModels = ref(true)
const historyVisible = ref(false)
const historyRows = ref<any[]>([])
const loadingHistory = ref(false)

const instanceNameById = (id: any) =>
  props.instances.find(item => String(item.instanceId) === String(id))?.instanceName || (id ? String(id) : '-')

const loadComponents = async () => {
  const id = props.instance?.instanceId
  if (!id) {
    instanceComponents.value = []
    return
  }
  loadingComponents.value = true
  try {
    const data = await api.listComponents(id)
    if (props.instance?.instanceId !== id) return
    if (data?.success) {
      instanceComponents.value = data.data || []
      emit('components-changed', instanceComponents.value)
    }
  } catch (err: any) {
    if (props.instance?.instanceId !== id) return
    ElMessage.error(err.response?.data?.message || '加载结构拓扑失败')
  } finally {
    if (props.instance?.instanceId === id) loadingComponents.value = false
  }
}

watch(() => props.instance?.instanceId, () => loadComponents(), { immediate: true })

const showComponentSpecification = (row: any) => {
  const spec = row?.specification || {}
  const pairs = Object.entries(spec).map(([k, v]) => `${k}: ${String(v)}`)
  if (!pairs.length) return ElMessage.info('暂无规格信息')
  ElMessageBox.alert(pairs.join('\n'), `${row.componentName}规格详情`)
}

const openComponentAction = async (row: any, mode: 'configure' | 'replace') => {
  if (props.retired) return
  activeComponent.value = row
  actionMode.value = mode
  selectedModelId.value = ''
  candidateInstances.value = []
  const specPairs: Array<{ key: string, value: string }> = []
  if (row?.specification && typeof row.specification === 'object') {
    Object.entries(row.specification).forEach(([k, v]) => specPairs.push({ key: k, value: String(v) }))
  }
  if (!specPairs.length) specPairs.push({ key: '', value: '' })
  actionForm.value = {
    componentName: row?.componentName || '',
    selfInstanceId: row?.selfInstanceId ? String(row.selfInstanceId) : '',
    remark: mode === 'replace' ? '' : (row?.remark || ''),
    specificationPairs: specPairs
  }
  const componentCategoryId = row?.categoryId ? String(row.categoryId) : ''
  if (componentCategoryId) {
    categoryModelLoading.value = true
    try {
      const data = await api.pageModels({ pageNo: 1, pageSize: 100, categoryId: componentCategoryId })
      if (data?.success) {
        const list = (data.data?.records || []).map(normalizeModel).filter((model: DeviceModel) => String(model.categoryId || '') === componentCategoryId)
        categoryModels.value = list
        categoryHasModels.value = list.length > 0
        if (row.selfInstanceId) {
          const boundInst = props.instances.find(ins => String(ins.instanceId) === String(row.selfInstanceId))
          const boundModel = boundInst ? findModelById(boundInst.modelId, props.models, props.modelOptions) : null
          if (boundInst && boundModel && String(boundModel.categoryId || '') === componentCategoryId) {
            selectedModelId.value = boundInst.modelId
            await onComponentModelSelect(boundInst.modelId)
            actionForm.value.selfInstanceId = String(row.selfInstanceId)
          }
        }
      }
    } catch {
      categoryModels.value = []
      categoryHasModels.value = false
    } finally {
      categoryModelLoading.value = false
    }
  } else {
    categoryModels.value = []
    categoryHasModels.value = false
  }
  actionVisible.value = true
}

const onComponentModelSelect = async (modelId: string) => {
  selectedModelId.value = modelId
  candidateInstances.value = []
  if (!modelId) return
  try {
    const data = await api.pageInstances({ pageNo: 1, pageSize: 100, modelId, lifecycleStatus: 'IN_USE' })
    if (data?.success) {
      candidateInstances.value = (data.data?.records || []).map(normalizeInstance)
        .filter((ins: DeviceInstance) => String(ins.instanceId) !== String(props.instance.instanceId))
    }
  } catch {
    ElMessage.error('加载模型设备实例失败')
  }
}

const submitAction = async () => {
  if (!activeComponent.value?.id || props.retired) return
  const specification: any = {}
  asArray(actionForm.value.specificationPairs).forEach(pair => {
    const k = pair.key?.trim()
    const v = pair.value?.trim()
    if (!k) return
    if (/^(true|false)$/i.test(v)) specification[k] = v.toLowerCase() === 'true'
    else if (/^\d+$/.test(v)) specification[k] = parseInt(v, 10)
    else if (/^\d+\.\d+$/.test(v)) specification[k] = parseFloat(v)
    else specification[k] = v
  })
  savingComponent.value = true
  try {
    const payload = {
      componentName: actionForm.value.componentName,
      selfInstanceId: actionForm.value.selfInstanceId ? Number(actionForm.value.selfInstanceId) : null,
      remark: actionForm.value.remark?.trim() || null,
      specification
    }
    const action = actionMode.value === 'replace' ? 'replace' : 'configure'
    const data = await api.submitComponentAction(activeComponent.value.id, action, payload)
    if (!data?.success) {
      ElMessage.error(data?.message || '保存组件失败')
      return
    }
    actionVisible.value = false
    await loadComponents()
    ElMessage.success(actionMode.value === 'replace' ? '组件已更换' : '组件已配置')
  } finally {
    savingComponent.value = false
  }
}

const markPending = async (row: any) => {
  if (!row?.id || props.retired) return
  try {
    const { value } = await ElMessageBox.prompt('请填写待更换原因或维修说明', '标记待更换', {
      inputType: 'textarea',
      inputPlaceholder: '例如：读数漂移，等待采购替代件',
      inputValidator: (value: string) => value?.trim() ? true : '备注不能为空'
    })
    const data = await api.markPendingReplacement(row.id, value.trim())
    if (!data?.success) {
      ElMessage.error(data?.message || '标记待更换失败')
      return
    }
    await loadComponents()
    ElMessage.success('组件已标记为待更换')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '标记待更换异常')
  }
}

const showHistory = async (row: any) => {
  if (!row?.id) return
  historyVisible.value = true
  loadingHistory.value = true
  try {
    const data = await api.getComponentHistory(row.id)
    if (data?.success) historyRows.value = data.data || []
    else {
      historyRows.value = []
      ElMessage.error(data?.message || '加载组件历史失败')
    }
  } catch (error: any) {
    historyRows.value = []
    ElMessage.error(error.response?.data?.message || '加载组件历史失败')
  } finally {
    loadingHistory.value = false
  }
}
</script>

<style scoped>
.tab-pane-content { display: flex; flex-direction: column; gap: 12px; }
.sub-section-head {
  padding: 8px 12px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.sub-section-head .head-title { font-size: 12.5px; font-weight: 700; color: var(--sl-text-heading); }
.sub-section-head .head-desc { font-size: 11px; color: var(--sl-text-secondary); }
.row-actions { display: flex; align-items: center; justify-content: center; gap: 8px; }
.mono { font-family: var(--sl-font-mono); }
</style>
