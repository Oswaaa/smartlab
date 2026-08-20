<template>
  <el-drawer
    :model-value="modelValue"
    title="添加设备实例"
    direction="rtl"
    size="80%"
    :destroy-on-close="true"
    class="instance-create-drawer unified-workflow-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="drawer-workbench-body">
      <div class="detail-anchor-layout">
        <el-anchor
          class="detail-anchor-menu"
          @click="(e: Event) => e.preventDefault()"
          container=".edit-scroll-content .el-scrollbar__wrap"
          :offset="20"
        >
          <el-anchor-link href="#create-model" title="01 选择模型" />
          <el-anchor-link href="#create-bind" title="02 点位绑定" />
          <el-anchor-link href="#create-asset" title="03 资产信息" />
          <el-anchor-link href="#create-preview" title="04 契约预览" />
        </el-anchor>

        <el-scrollbar class="edit-scroll-content">
          <div id="create-model" class="anchor-section holistic-section-card">
            <div class="section-card-head">
              <div class="section-card-title">
                <span class="sec-idx-badge">01</span>
                <span class="sec-title-text">选择设备模型</span>
                <span class="sec-desc-text">选择所属设备分类并指定数字物模型定义</span>
              </div>
            </div>
            <div class="section-card-body">
              <el-form label-position="top" size="small">
                <el-form-item label="所属类别" required>
                  <el-tree-select
                    v-model="wizardSelectedCategoryId"
                    :data="categoryTree"
                    node-key="id"
                    check-strictly
                    :render-after-expand="false"
                    placeholder="选择分类"
                    style="width: 100%"
                    @change="onWizardCategoryChange"
                  />
                </el-form-item>
                <el-form-item label="设备物模型" required>
                  <el-select
                    v-model="wizardModelId"
                    style="width: 100%"
                    placeholder="选择该类别下的模型"
                    :disabled="!wizardSelectedCategoryId"
                    @change="onWizardModelChange"
                  >
                    <el-option v-for="m in wizardCategoryModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                  </el-select>
                </el-form-item>
              </el-form>
            </div>
          </div>

          <div id="create-bind" class="anchor-section holistic-section-card">
            <div class="section-card-head">
              <div class="section-card-title">
                <span class="sec-idx-badge">02</span>
                <span class="sec-title-text">物理点位绑定</span>
                <span class="sec-desc-text">指定该设备物理接入的 Adapter 驱动与设备通道点位</span>
              </div>
            </div>
            <div class="section-card-body">
              <el-form label-position="top" size="small">
                <el-form-item label="物理 Adapter 代理" required>
                  <el-select v-model="wizardAdapterName" style="width: 100%" filterable placeholder="选择 Adapter" @change="onWizardAdapterChange">
                    <el-option v-for="adapter in localAdapters" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
                  </el-select>
                </el-form-item>
                <el-form-item label="Adapter 设备点位" required>
                  <el-select
                    v-model="wizardDevicePoint"
                    style="width: 100%"
                    filterable
                    :loading="pointsLoading"
                    :disabled="!wizardAdapterName"
                    placeholder="选择具体设备通道点位 (例如: Reactor_01)"
                  >
                    <el-option v-for="point in wizardDevicePoints" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
                  </el-select>
                </el-form-item>
              </el-form>
            </div>
          </div>

          <div id="create-asset" class="anchor-section holistic-section-card">
            <div class="section-card-head">
              <div class="section-card-title">
                <span class="sec-idx-badge">03</span>
                <span class="sec-title-text">资产信息</span>
                <span class="sec-desc-text">填写物理设备的台账编码、位置与采购运维信息</span>
              </div>
            </div>
            <div class="section-card-body">
              <el-form :model="wizardAssetInfo" label-position="top" size="small" class="instance-info-grid">
                <el-form-item label="设备实例名称" required>
                  <el-input v-model="wizardInstanceName" placeholder="例如：1号高压反应釜" />
                </el-form-item>
                <el-form-item label="出厂序列号 (SN)">
                  <el-input v-model="wizardAssetInfo.serialNumber" placeholder="SN 出厂编码" />
                </el-form-item>
                <el-form-item label="部署具体位置">
                  <el-input v-model="wizardAssetInfo.location" placeholder="例如：有机合成实验室-A3" />
                </el-form-item>
                <el-form-item label="采购日期">
                  <el-date-picker v-model="wizardAssetInfo.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                </el-form-item>
                <el-form-item label="安装日期">
                  <el-date-picker v-model="wizardAssetInfo.instalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                </el-form-item>
                <el-form-item label="资产运维备注" class="form-wide">
                  <el-input v-model="wizardAssetInfo.notes" type="textarea" :rows="2" placeholder="填写维保或厂商备注信息..." />
                </el-form-item>
              </el-form>
            </div>
          </div>

          <div id="create-preview" class="anchor-section holistic-section-card">
            <div class="section-card-head">
              <div class="section-card-title">
                <span class="sec-idx-badge">04</span>
                <span class="sec-title-text">物模型契约继承预览</span>
                <span class="sec-desc-text">该实例自动继承的模型数据属性、操作指令与状态空间</span>
              </div>
            </div>
            <div class="section-card-body">
              <div class="step3-preview">
                <div class="preview-section">
                  <span class="preview-label">继承的数据属性 (Attributes)</span>
                  <div class="preview-tags" v-if="wizardPreviewAttributes.length">
                    <el-tag v-for="attr in wizardPreviewAttributes" :key="attr.attributeName" size="small" effect="plain" type="info">
                      {{ attr.displayName || attr.attributeName }} ({{ attr.dataType }}{{ attr.unit ? ', ' + attr.unit : '' }})
                    </el-tag>
                  </div>
                  <div v-else class="preview-empty">无属性定义</div>
                </div>
                <div class="preview-section">
                  <span class="preview-label">继承的设备能力操作 (Capabilities)</span>
                  <div class="preview-tags" v-if="wizardPreviewCapabilities.length">
                    <el-tag v-for="capability in wizardPreviewCapabilities" :key="capability.capabilityName" size="small" type="warning" effect="plain">
                      {{ capability.displayName }}
                    </el-tag>
                  </div>
                  <div v-else class="preview-empty">无操作能力定义</div>
                </div>
                <div class="preview-section">
                  <span class="preview-label">功能状态机状态 (OpStates)</span>
                  <div class="preview-tags" v-if="wizardPreviewStates.length">
                    <el-tag v-for="st in wizardPreviewStates" :key="st.stateName" size="small" type="success" effect="plain">
                      {{ st.stateName }}
                    </el-tag>
                  </div>
                  <div v-else class="preview-empty">无内置自定义功能状态</div>
                </div>
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </div>
    <template #footer>
      <div class="drawer-footer unified-drawer-footer">
        <button class="btn-aliyun" type="button" @click="emit('update:modelValue', false)">取消</button>
        <button class="btn-primary-blue" type="button" :disabled="creating || !canSubmitCreate" @click="submitCreate">
          <span>{{ creating ? '创建中...' : '确认并保存实例' }}</span>
        </button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { normalizeManualControlCapabilities } from '../../../../utils/manualControlCapabilities.js'
import * as api from './deviceInstanceApi'
import {
  asArray,
  categoryTreeForSelect,
  constraintsFromSource,
  devicePointLabel,
  emptyAssetInfo,
  normalizeModel
} from './normalizers'
import type { AdapterDevicePoint, AdapterOption, DeviceModel } from './types'

const props = defineProps<{
  modelValue: boolean
  categories: any[]
  adapterOptions: AdapterOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: []
}>()

const wizardSelectedCategoryId = ref('')
const wizardModelId = ref('')
const wizardAdapterName = ref('')
const wizardDevicePoints = ref<AdapterDevicePoint[]>([])
const wizardDevicePoint = ref('')
const wizardInstanceName = ref('')
const wizardAssetInfo = ref(emptyAssetInfo())
const wizardCategoryModels = ref<DeviceModel[]>([])
const localAdapters = ref<AdapterOption[]>([])
const pointsLoading = ref(false)
const creating = ref(false)

const categoryTree = computed(() => categoryTreeForSelect(props.categories))
const wizardModel = computed(() => wizardCategoryModels.value.find(m => m.modelId === wizardModelId.value) || null)
const canSubmitCreate = computed(() =>
  !!wizardModelId.value && !!wizardAdapterName.value && !!wizardDevicePoint.value && !!wizardInstanceName.value.trim()
)
const wizardPreviewAttributes = computed(() => asArray(wizardModel.value?.capabilitySpec?.attributes || wizardModel.value?.attributes))
const wizardPreviewCapabilities = computed(() =>
  normalizeManualControlCapabilities(asArray(wizardModel.value?.capabilitySpec?.capabilities || wizardModel.value?.capabilities))
)
const wizardPreviewStates = computed(() => {
  const opState = wizardModel.value?.opState || wizardModel.value?.capabilitySpec?.operationStateSpace
  return asArray(opState?.states)
})

const resetWizard = () => {
  wizardSelectedCategoryId.value = ''
  wizardModelId.value = ''
  wizardCategoryModels.value = []
  wizardAdapterName.value = ''
  wizardDevicePoints.value = []
  wizardDevicePoint.value = ''
  wizardInstanceName.value = ''
  wizardAssetInfo.value = emptyAssetInfo()
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  resetWizard()
  localAdapters.value = props.adapterOptions.slice()
  if (!localAdapters.value.length) {
    try {
      const data = await api.listAdapters()
      if (data?.success) localAdapters.value = data.data || []
    } catch {
      localAdapters.value = []
    }
  }
})

const onWizardCategoryChange = async (catId: any) => {
  wizardModelId.value = ''
  wizardCategoryModels.value = []
  if (!catId) return
  try {
    const data = await api.pageModels({ pageNo: 1, pageSize: 100, categoryId: catId })
    if (data?.success) wizardCategoryModels.value = (data.data?.records || []).map(normalizeModel)
  } catch {
    ElMessage.error('加载该类别的模型列表失败')
  }
}

const loadWizardDevicePoints = async () => {
  wizardDevicePoints.value = []
  if (!wizardAdapterName.value) return
  pointsLoading.value = true
  try {
    const adapterConfig = wizardModel.value?.capabilitySpec?.adapterContract?.config || {}
    const data = await api.listDevicePoints(wizardAdapterName.value, adapterConfig.categoryName)
    if (data?.success) wizardDevicePoints.value = data.data || []
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载 Adapter 设备点失败')
  } finally {
    pointsLoading.value = false
  }
}

const onWizardModelChange = async () => {
  wizardAdapterName.value = ''
  wizardDevicePoint.value = ''
  wizardDevicePoints.value = []
  const contractAdapterName = wizardModel.value?.capabilitySpec?.adapterContract?.config?.adapterName || ''
  if (contractAdapterName) {
    wizardAdapterName.value = contractAdapterName
    await loadWizardDevicePoints()
  }
}

const onWizardAdapterChange = async () => {
  wizardDevicePoint.value = ''
  await loadWizardDevicePoints()
}

const submitCreate = async () => {
  if (!wizardInstanceName.value.trim()) {
    ElMessage.warning('请填写实例名称')
    return
  }
  creating.value = true
  try {
    const payload = {
      instanceId: '',
      modelId: wizardModelId.value,
      stateMachineId: wizardModelId.value + 'StateMachine',
      instanceName: wizardInstanceName.value.trim(),
      boundAdapterName: wizardAdapterName.value,
      boundDevicePoint: wizardDevicePoint.value,
      instanceConfig: {
        assetInfo: { ...wizardAssetInfo.value },
        intrinsicConstraints: constraintsFromSource(
          wizardModel.value?.intrinsicConstraints || wizardModel.value?.intrinsicConstraint
        )
      },
      isOnline: false
    }
    const data = await api.saveInstance(payload)
    if (data?.success) {
      ElMessage.success('设备添加成功')
      emit('update:modelValue', false)
      emit('created')
    } else {
      ElMessage.error(data?.message || '添加失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '添加失败')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.drawer-workbench-body { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.detail-anchor-layout { display: flex; height: 100%; overflow: hidden; }
.detail-anchor-menu {
  width: 148px;
  flex-shrink: 0;
  padding: 10px 8px;
  background: #f8fafc;
  border-right: 1px solid var(--sl-border-base) !important;
}
.detail-anchor-menu :deep(.el-anchor__link) {
  margin-bottom: 2px;
  padding: 6px 10px;
  border-radius: 4px;
  color: var(--sl-text-body);
  font-size: 12.5px;
  font-weight: 600;
}
.detail-anchor-menu :deep(.el-anchor__link.is-active) {
  background: var(--sl-primary-light);
  color: var(--sl-primary);
}
.edit-scroll-content { flex: 1; min-width: 0; }
.edit-scroll-content :deep(.el-scrollbar__view) { padding: 12px 16px 24px; }
.holistic-section-card {
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  margin-bottom: 12px;
  overflow: hidden;
  box-shadow: var(--sl-shadow-sm);
}
.section-card-head {
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-card-title { display: flex; align-items: center; gap: 6px; }
.sec-idx-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  border: 1px solid var(--sl-primary-border);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono);
}
.sec-title-text { font-size: 13px; font-weight: 700; color: var(--sl-text-heading); }
.sec-desc-text { font-size: 11.5px; color: var(--sl-text-secondary); }
.section-card-body { padding: 12px; }
.instance-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 12px;
  background: #ffffff;
  padding: 12px;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
}
.instance-info-grid :deep(.el-form-item) { margin-bottom: 0; }
.form-wide { grid-column: 1 / -1; }
.step3-preview { display: flex; flex-direction: column; gap: 12px; }
.preview-section { display: flex; flex-direction: column; gap: 6px; }
.preview-label { font-size: 12px; font-weight: 700; color: var(--sl-text-secondary); }
.preview-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.preview-empty { font-size: 11.5px; color: var(--sl-text-disabled); font-style: italic; }
.unified-drawer-footer {
  padding: 10px 16px;
  background: #ffffff;
  border-top: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

<style>
.instance-create-drawer .el-drawer__body {
  overflow: hidden !important;
  padding: 0 !important;
  display: flex;
  flex-direction: column;
}
</style>
