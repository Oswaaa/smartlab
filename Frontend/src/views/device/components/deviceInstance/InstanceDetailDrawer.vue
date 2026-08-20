<template>
  <el-drawer
    :model-value="modelValue"
    size="80%"
    class="instance-detail-drawer unified-workflow-drawer"
    :destroy-on-close="true"
    :with-header="false"
    @update:model-value="emit('update:modelValue', $event)"
    @close="onClose"
  >
    <div v-if="activeInstance" class="drawer-container">
      <header class="drawer-custom-head">
        <div class="head-top-row">
          <div class="head-title-wrap">
            <span class="inst-type-tag">设备实例</span>
            <h3 class="head-title-text">{{ activeInstance.instanceName }}</h3>
            <span v-if="activeInstance.boundDevicePoint" class="point-badge">{{ activeInstance.boundDevicePoint }}</span>
            <button class="copy-id-btn" type="button" title="点击复制实例 ID" @click="copyText(activeInstance.instanceId)">
              <span class="mono">ID: {{ activeInstance.instanceId }}</span>
              <el-icon><CopyDocument /></el-icon>
            </button>
          </div>
          <div class="head-status-group">
            <span v-if="retired" class="status-indicator retired"><span class="dot"></span>已注销 (退役)</span>
            <span v-else class="status-indicator in-use"><span class="dot"></span>使用中 (在役)</span>
            <template v-if="!retired">
              <span class="head-sep">|</span>
              <span v-if="online" class="status-indicator online"><span class="dot"></span>在线通信</span>
              <span v-else class="status-indicator offline"><span class="dot"></span>离线</span>
            </template>
            <button class="close-drawer-btn" type="button" @click="emit('update:modelValue', false)">
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </div>
        <div class="drawer-metric-ribbon">
          <div class="m-col">
            <span class="m-lbl">所属模型</span>
            <span class="m-val highlight">{{ getModelName(activeInstance.modelId, models, modelOptions) }}</span>
          </div>
          <div class="m-col">
            <span class="m-lbl">Adapter名称</span>
            <span class="m-val">{{ activeInstance.boundAdapterName || '未分配' }}</span>
          </div>
          <div class="m-col">
            <span class="m-lbl">物理点位</span>
            <span class="m-val">{{ activeInstance.boundDevicePoint || '未分配' }}</span>
          </div>
          <div class="m-col">
            <span class="m-lbl">最后心跳时间</span>
            <span class="m-val mono">{{ lastSnapshotTime }}</span>
          </div>
        </div>
      </header>

      <div v-if="retired" class="retired-alert-bar">
        <el-icon class="alert-icon"><WarningFilled /></el-icon>
        <span>该设备实例已注销。实例、孪生快照、组件拓扑和历史数据仅供只读归档查阅，不再参与控制调度或新业务。</span>
      </div>

      <div class="drawer-tabs-wrap">
        <el-tabs v-model="activeTab" class="instance-tabs">
          <el-tab-pane label="实时运行状态" name="status">
            <InstanceRuntimeTab
              :instance="activeInstance"
              :snapshot="snapshot"
              :models="models"
              :model-options="modelOptions"
              :last-snapshot-time="lastSnapshotTime"
              :retired="retired"
              :can-control="canControl"
              :clearing-exception="clearingException"
              @clear-exception="clearExceptionState"
            />
          </el-tab-pane>
          <el-tab-pane v-if="canControl" label="控制调试" name="control">
            <InstanceControlTab
              :instance="activeInstance"
              :snapshot="snapshot"
              :models="models"
              :model-options="modelOptions"
              :instances="instances"
              :instance-components="instanceComponents"
              :retired="retired"
              @refresh-snapshot="fetchSnapshot"
            />
          </el-tab-pane>
          <el-tab-pane label="结构拓扑" name="components">
            <InstanceComponentsTab
              :instance="activeInstance"
              :instances="instances"
              :models="models"
              :model-options="modelOptions"
              :categories-map="categoriesMap"
              :can-edit="canEdit"
              :retired="retired"
              @components-changed="rows => instanceComponents = rows"
            />
          </el-tab-pane>
          <el-tab-pane label="归档数据" name="datasets">
            <InstanceDatasetsTab
              :instance="activeInstance"
              :can-create="canCreateDataset"
              :can-delete="canDeleteDataset"
              :retired="retired"
            />
          </el-tab-pane>
          <el-tab-pane label="安全约束" name="constraints">
            <InstanceConstraintsTab
              v-model:constraints="localConstraints"
              :instance="activeInstance"
              :models="models"
              :model-options="modelOptions"
              :can-edit="canEdit"
              :retired="retired"
              @saved="emit('saved')"
            />
          </el-tab-pane>
          <el-tab-pane label="资产管理" name="info">
            <InstanceAssetTab
              :instance="activeInstance"
              :models="assetModels"
              :adapter-options="adapterOptions"
              :device-points="devicePoints"
              :retired="retired"
              @update:instance="val => activeInstance = val"
              @adapter-change="loadDevicePoints"
              @model-change="onModelChange"
            />
          </el-tab-pane>
        </el-tabs>
      </div>

      <footer class="drawer-footer-bar">
        <div class="footer-left">
          <el-popconfirm v-if="canRetire" title="注销后设备将不能参与控制、任务和新业务，现有数据与组件历史会完整保留。确认注销？" @confirm="retireActive">
            <template #reference>
              <button class="btn-link danger" type="button">注销设备实例</button>
            </template>
          </el-popconfirm>
        </div>
        <div class="footer-right">
          <button class="btn-aliyun" type="button" @click="emit('update:modelValue', false)">关闭</button>
          <button v-if="canEdit" class="btn-primary-blue" type="button" :disabled="saving" @click="saveInstance">
            <span>{{ saving ? '保存中...' : '保存资产修改' }}</span>
          </button>
        </div>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { Close, CopyDocument, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../../../../stores/authStore'
import InstanceRuntimeTab from './InstanceRuntimeTab.vue'
import InstanceControlTab from './InstanceControlTab.vue'
import InstanceComponentsTab from './InstanceComponentsTab.vue'
import InstanceDatasetsTab from './InstanceDatasetsTab.vue'
import InstanceConstraintsTab from './InstanceConstraintsTab.vue'
import InstanceAssetTab from './InstanceAssetTab.vue'
import * as api from './deviceInstanceApi'
import { asArray, buildInstanceSavePayload, cloneInstanceForEdit, constraintsFromSource, findModelById, getModelName } from './normalizers'
import type { AdapterDevicePoint, AdapterOption, DeviceInstance, DeviceModel, DeviceSnapshot, InstanceConstraint } from './types'

const props = defineProps<{
  modelValue: boolean
  instance: DeviceInstance | null
  models: DeviceModel[]
  modelOptions: DeviceModel[]
  instances: DeviceInstance[]
  adapterOptions: AdapterOption[]
  categoriesMap: Record<string, string>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
  retired: []
}>()

const authStore = useAuthStore()
const activeInstance = ref<DeviceInstance | null>(null)
const activeTab = ref('status')
const snapshot = ref<DeviceSnapshot | null>(null)
const lastSnapshotTime = ref('-')
const localConstraints = ref<InstanceConstraint[]>([])
const instanceComponents = ref<any[]>([])
const devicePoints = ref<AdapterDevicePoint[]>([])
const saving = ref(false)
const clearingException = ref('')
let pollingTimer: any = null

const retired = computed(() => activeInstance.value?.lifecycleStatus === 'RETIRED')
const online = computed(() => {
  if (snapshot.value?.onlineStatus) return snapshot.value.onlineStatus.toUpperCase() === 'ONLINE'
  return activeInstance.value?.isOnline === true || activeInstance.value?.onlineStatus === 'ONLINE'
})
const canEdit = computed(() => authStore.hasPermission('device_instance:edit') && !retired.value)
const canRetire = computed(() => authStore.hasPermission('device_instance:delete') && !retired.value)
const canControl = computed(() => authStore.hasPermission('device_instance:control') && !retired.value)
const canCreateDataset = computed(() => authStore.hasPermission('data_dataset:create') && !retired.value)
const canDeleteDataset = computed(() => authStore.hasPermission('data_dataset:delete') && !retired.value)
const assetModels = computed(() => {
  const map = new Map<string, DeviceModel>()
  ;[...props.modelOptions, ...props.models].forEach(m => {
    if (m?.modelId) map.set(String(m.modelId), m)
  })
  return [...map.values()]
})

const fetchSnapshot = async () => {
  const id = activeInstance.value?.instanceId
  if (!id || !props.modelValue) return
  try {
    const data = await api.getSnapshot(id)
    if (activeInstance.value?.instanceId !== id || !props.modelValue) return
    if (data?.success) {
      snapshot.value = data.data || null
      lastSnapshotTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    }
  } catch {
    if (activeInstance.value?.instanceId !== id || !props.modelValue) return
    snapshot.value = null
  }
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const startPolling = () => {
  stopPolling()
  fetchSnapshot()
  if (retired.value) return
  pollingTimer = setInterval(fetchSnapshot, 1000)
}

const loadComponents = async () => {
  const id = activeInstance.value?.instanceId
  if (!id) {
    instanceComponents.value = []
    return
  }
  try {
    const data = await api.listComponents(id)
    if (activeInstance.value?.instanceId !== id) return
    if (data?.success) instanceComponents.value = data.data || []
  } catch {
    if (activeInstance.value?.instanceId !== id) return
    instanceComponents.value = []
  }
}

const loadDevicePoints = async (adapterName?: string) => {
  const inst = activeInstance.value
  const name = adapterName || inst?.boundAdapterName
  if (!inst?.instanceId || !name) {
    devicePoints.value = []
    return
  }
  const instanceId = inst.instanceId
  try {
    const model = findModelById(inst.modelId, props.models, props.modelOptions)
    const categoryName = model?.capabilitySpec?.adapterContract?.config?.categoryName
    const data = await api.listDevicePoints(name, categoryName)
    if (activeInstance.value?.instanceId !== instanceId) return
    if (data?.success) devicePoints.value = data.data || []
  } catch (err: any) {
    if (activeInstance.value?.instanceId !== instanceId) return
    ElMessage.error(err.response?.data?.message || '加载 Adapter 设备点失败')
  }
}

const onModelChange = () => {
  if (!activeInstance.value) return
  loadDevicePoints(activeInstance.value.boundAdapterName)
}

const copyText = (text: string) => {
  navigator.clipboard.writeText(text).then(() => ElMessage.success('已成功复制实例 ID')).catch(() => ElMessage.error('复制失败'))
}

const clearExceptionState = async (violationStateName: string) => {
  if (!activeInstance.value || !violationStateName || retired.value) return
  try {
    await ElMessageBox.confirm(`确认解除异常状态“${violationStateName}”？系统会先复核当前遥测值。`, '解除异常', {
      confirmButtonText: '解除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    clearingException.value = violationStateName
    const data = await api.clearException(activeInstance.value.instanceId, violationStateName)
    if (!data?.success) {
      ElMessage.error(data?.message || '解除异常失败')
      return
    }
    ElMessage.success('异常已解除')
    await fetchSnapshot()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || error.message || '解除异常失败')
  } finally {
    clearingException.value = ''
  }
}

const saveInstance = async () => {
  if (!activeInstance.value || retired.value) return
  saving.value = true
  try {
    const data = await api.saveInstance(buildInstanceSavePayload(activeInstance.value, localConstraints.value))
    if (data?.success) {
      ElMessage.success('保存成功')
      emit('update:modelValue', false)
      emit('saved')
    } else {
      ElMessage.error(data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const retireActive = async () => {
  if (!activeInstance.value) return
  try {
    const data = await api.retireInstance(activeInstance.value.instanceId)
    if (data?.success) {
      ElMessage.success('设备实例已注销')
      emit('update:modelValue', false)
      emit('retired')
    } else {
      ElMessage.error(data?.message || '注销失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '注销失败')
  }
}

const onClose = () => {
  stopPolling()
  snapshot.value = null
  activeInstance.value = null
  instanceComponents.value = []
  devicePoints.value = []
}

watch(
  () => [props.modelValue, props.instance] as const,
  ([visible, instance]) => {
    if (!visible || !instance) {
      stopPolling()
      return
    }
    activeInstance.value = cloneInstanceForEdit(instance)
    activeTab.value = 'status'
    snapshot.value = null
    lastSnapshotTime.value = '-'
    instanceComponents.value = []
    devicePoints.value = []
    const model = findModelById(activeInstance.value.modelId, props.models, props.modelOptions)
    const cfg = activeInstance.value.instanceConfig || {}
    let list = cfg.intrinsicConstraints
    if (!list || list.length === 0) list = asArray(model?.intrinsicConstraints || model?.intrinsicConstraint)
    localConstraints.value = constraintsFromSource(list)
    if (!retired.value) loadDevicePoints(activeInstance.value.boundAdapterName)
    loadComponents()
    startPolling()
  }
)

onUnmounted(stopPolling)
</script>

<style scoped>
.instance-detail-drawer :deep(.el-drawer__body) {
  padding: 0 !important;
  overflow: hidden;
  background: var(--sl-bg-page);
}
.drawer-container { display: flex; flex-direction: column; height: 100%; overflow: hidden; }
.drawer-custom-head { background: #ffffff; border-bottom: 1px solid var(--sl-border-base); flex-shrink: 0; }
.head-top-row {
  padding: 10px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--sl-border-subtle);
  gap: 12px;
}
.head-title-wrap { display: flex; align-items: center; gap: 8px; min-width: 0; }
.inst-type-tag {
  background: var(--sl-primary-light); color: var(--sl-primary);
  border: 1px solid var(--sl-primary-border); padding: 1px 6px;
  font-size: 11px; font-weight: 700; border-radius: var(--sl-radius-sm); flex-shrink: 0;
}
.head-title-text {
  margin: 0; font-size: 15px; font-weight: 700; color: var(--sl-text-heading);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.point-badge { background: #f1f5f9; color: var(--sl-text-secondary); padding: 1px 6px; font-size: 11.5px; border-radius: 4px; font-weight: 600; }
.copy-id-btn {
  background: transparent; border: none; cursor: pointer; color: var(--sl-text-secondary);
  display: inline-flex; align-items: center; gap: 4px; padding: 2px 4px; font-size: 11px; border-radius: 4px;
}
.copy-id-btn:hover { background: #f1f5f9; color: var(--sl-primary); }
.head-status-group { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.head-sep { color: var(--sl-border-input); }
.close-drawer-btn {
  background: transparent; border: none; cursor: pointer; padding: 4px;
  color: var(--sl-text-secondary); border-radius: 4px; display: inline-flex; font-size: 16px;
}
.close-drawer-btn:hover { background: #f1f5f9; color: var(--sl-text-heading); }
.drawer-metric-ribbon {
  height: 35px; padding: 0 16px; display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 16px; align-items: center; background: #fafbfc;
}
.m-col { display: flex; align-items: baseline; gap: 6px; min-width: 0; }
.m-lbl { font-size: 11px; color: var(--sl-text-secondary); flex-shrink: 0; }
.m-val { font-size: 12.5px; font-weight: 600; color: var(--sl-text-heading); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-val.highlight { color: var(--sl-primary); }
.status-indicator { display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; font-weight: 600; }
.status-indicator .dot { width: 6px; height: 6px; border-radius: 50%; }
.status-indicator.in-use { color: var(--sl-success); }
.status-indicator.in-use .dot { background: var(--sl-success); }
.status-indicator.retired { color: var(--sl-text-secondary); }
.status-indicator.retired .dot { background: var(--sl-text-disabled); }
.status-indicator.online { color: #0284c7; }
.status-indicator.online .dot { background: #0284c7; box-shadow: 0 0 6px rgba(2, 132, 199, 0.5); }
.status-indicator.offline { color: var(--sl-text-disabled); }
.status-indicator.offline .dot { background: var(--sl-text-disabled); }
.retired-alert-bar {
  background: #fffbeb; border-bottom: 1px solid #fde68a; padding: 6px 16px;
  font-size: 12px; color: #b45309; display: flex; align-items: center; gap: 6px; flex-shrink: 0;
}
.alert-icon { font-size: 14px; flex-shrink: 0; }
.drawer-tabs-wrap { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.instance-tabs { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.instance-tabs :deep(.el-tabs__header) {
  margin: 0; padding: 0 16px; background: #ffffff; border-bottom: 1px solid var(--sl-border-base); flex-shrink: 0;
}
.instance-tabs :deep(.el-tabs__content) {
  flex: 1; min-height: 0; overflow-y: auto; padding: 12px 16px; display: flex; flex-direction: column;
}
.instance-tabs :deep(.el-tab-pane) { height: 100%; }
.drawer-footer-bar {
  padding: 10px 16px; background: #ffffff; border-top: 1px solid var(--sl-border-base);
  display: flex; align-items: center; justify-content: space-between; flex-shrink: 0;
}
.footer-right { display: flex; align-items: center; gap: 8px; }
.mono { font-family: var(--sl-font-mono); }
</style>

<style>
.instance-detail-drawer .el-drawer__body {
  overflow: hidden !important;
  background: var(--sl-bg-page);
}
</style>
