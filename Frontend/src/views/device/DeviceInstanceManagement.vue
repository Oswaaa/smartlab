<template>
  <div class="device-instance-page">
    <div class="instance-workbench-canvas">
      <DeviceModelTree
        v-model:keyword="sidebarKeyword"
        :categories="categories"
        :models="models"
        :selected-model-id="selectedModelId"
        :selected-category-id="selectedCategoryId"
        :loading="modelsLoading"
        :readonly="true"
        class="workbench-sidebar"
        @select-model="selectModel"
        @select-category="selectCategory"
      />

      <main class="workbench-main">
        <div class="workbench-header">
          <div class="header-left">
            <h2 class="header-title">{{ selectedModelName }}</h2>
            <span class="header-subtitle">设备实例资产管理、实时状态监控与物理点位映射</span>
          </div>
          <div class="header-actions">
            <el-select v-model="instanceLifecycleFilter" size="small" class="lifecycle-filter" @change="onLifecycleFilterChange">
              <el-option label="使用中 (在役)" value="IN_USE" />
              <el-option label="已注销 (退役)" value="RETIRED" />
              <el-option label="全部状态" value="" />
            </el-select>
            <el-input
              v-model="instanceKeyword"
              class="instance-search"
              size="small"
              clearable
              placeholder="搜索实例名称或出厂 SN..."
              :prefix-icon="Search"
              @input="onInstanceSearchInput"
            />
            <button class="btn-aliyun" type="button" @click="loadData">
              <el-icon><Refresh /></el-icon><span>刷新</span>
            </button>
            <button v-if="canCreateInstance" class="btn-aliyun-cta" type="button" @click="createDrawerVisible = true">
              <el-icon><Plus /></el-icon><span>添加设备</span>
            </button>
          </div>
        </div>

        <InstanceListTable
          :instances="instances"
          :models="models"
          :model-options="modelOptions"
          :loading="loading"
          :page-no="instancePageNo"
          :page-size="instancePageSize"
          :total="instanceTotal"
          :can-delete="canDeleteInstance"
          @update:page-no="val => instancePageNo = val"
          @update:page-size="val => instancePageSize = val"
          @page-size-change="handleInstancePageSizeChange"
          @page-change="onInstancePageChange"
          @view-details="viewDetails"
          @retire="confirmRetireInstance"
        />
      </main>
    </div>

    <InstanceDetailDrawer
      v-model="drawerVisible"
      :instance="detailSource"
      :models="models"
      :model-options="modelOptions"
      :instances="instances"
      :adapter-options="adapterOptions"
      :categories-map="categoriesMap"
      @saved="loadData"
      @retired="loadData"
    />

    <InstanceCreateDrawer
      v-model="createDrawerVisible"
      :categories="categories"
      :adapter-options="adapterOptions"
      @created="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../../stores/authStore'
import DeviceModelTree from './components/DeviceModelTree.vue'
import { loadProtocolMetadata } from './components/deviceModel/deviceModelConstants'
import InstanceListTable from './components/deviceInstance/InstanceListTable.vue'
import InstanceCreateDrawer from './components/deviceInstance/InstanceCreateDrawer.vue'
import InstanceDetailDrawer from './components/deviceInstance/InstanceDetailDrawer.vue'
import * as api from './components/deviceInstance/deviceInstanceApi'
import { normalizeInstance, normalizeModel } from './components/deviceInstance/normalizers'
import type { AdapterOption, DeviceInstance, DeviceModel } from './components/deviceInstance/types'

const authStore = useAuthStore()
const route = useRoute()

const models = ref<DeviceModel[]>([])
const modelOptions = ref<DeviceModel[]>([])
const instances = ref<DeviceInstance[]>([])
const adapterOptions = ref<AdapterOption[]>([])
const categories = ref<any[]>([])
const categoriesMap = ref<Record<string, string>>({})
const selectedModelId = ref('')
const selectedCategoryId = ref('')
const sidebarKeyword = ref('')
const instanceKeyword = ref('')
const instanceLifecycleFilter = ref<'IN_USE' | 'RETIRED' | ''>('IN_USE')
const loading = ref(false)
const modelsLoading = ref(false)
const instancePageNo = ref(1)
const instancePageSize = ref(24)
const instanceTotal = ref(0)
let instanceLoadSeq = 0
let instanceSearchTimer: any = null

const createDrawerVisible = ref(false)
const drawerVisible = ref(false)
const detailSource = ref<DeviceInstance | null>(null)

const canCreateInstance = computed(() => authStore.hasPermission('device_instance:create'))
const canDeleteInstance = computed(() => authStore.hasPermission('device_instance:delete'))

const categoryNameById = (id: any) => categoriesMap.value[String(id)] || ''
const selectedModelName = computed(() => {
  if (!selectedModelId.value) {
    if (selectedCategoryId.value) return `${categoryNameById(selectedCategoryId.value)} - 类别全部设备实例`
    return '全部设备实例'
  }
  const model = models.value.find(m => String(m.modelId) === String(selectedModelId.value))
  return model ? `${model.modelName} (${model.modelId})` : '未知模型'
})

const loadSidebarModels = async () => {
  const data = await api.pageModels({ pageNo: 1, pageSize: 100 })
  if (data?.success) models.value = (data.data?.records || []).map(normalizeModel)
}

const loadModelOptions = async (keyword = '') => {
  const data = await api.pageModels({
    pageNo: 1,
    pageSize: 100,
    keyword: keyword.trim() || undefined
  })
  if (data?.success) modelOptions.value = (data.data?.records || []).map(normalizeModel)
}

const loadAdapters = async () => {
  try {
    const data = await api.listAdapters()
    adapterOptions.value = data?.success ? (data.data || []) : []
  } catch {
    adapterOptions.value = []
  }
}

const loadInstances = async () => {
  const seq = ++instanceLoadSeq
  loading.value = true
  try {
    const params: any = {
      pageNo: instancePageNo.value,
      pageSize: instancePageSize.value,
      keyword: instanceKeyword.value.trim() || undefined,
      lifecycleStatus: instanceLifecycleFilter.value || undefined
    }
    if (selectedModelId.value) params.modelId = selectedModelId.value
    const data = await api.pageInstances(params)
    if (seq !== instanceLoadSeq) return
    if (data?.success) {
      const pageData = data.data || {}
      instances.value = (pageData.records || []).map(normalizeInstance)
      instanceTotal.value = pageData.total || 0
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备实例失败')
  } finally {
    if (seq === instanceLoadSeq) loading.value = false
  }
}

const loadData = async () => {
  modelsLoading.value = true
  try {
    const catData = await api.listCategories()
    if (catData?.success) {
      const map: Record<string, string> = {}
      categories.value = catData.data || []
      ;(catData.data || []).forEach((c: any) => { map[c.id] = c.categoryName })
      categoriesMap.value = map
    }
    await Promise.all([loadSidebarModels(), loadModelOptions(), loadAdapters()])
    await loadInstances()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备数据失败')
  } finally {
    modelsLoading.value = false
  }
}

const selectModel = (id: any) => {
  selectedModelId.value = String(id || '')
  instancePageNo.value = 1
  loadInstances()
}

const selectCategory = (categoryData: any) => {
  selectedCategoryId.value = categoryData?.categoryId ? String(categoryData.categoryId) : ''
  selectedModelId.value = ''
  instancePageNo.value = 1
  loadInstances()
}

const onInstanceSearchInput = () => {
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
  instanceSearchTimer = window.setTimeout(() => {
    instancePageNo.value = 1
    loadInstances()
  }, 250)
}

const onLifecycleFilterChange = () => {
  instancePageNo.value = 1
  loadInstances()
}

const handleInstancePageSizeChange = (size: number) => {
  instancePageSize.value = size
  instancePageNo.value = 1
  loadInstances()
}

const onInstancePageChange = (page: number) => {
  instancePageNo.value = page
  loadInstances()
}

const viewDetails = (instance: DeviceInstance) => {
  detailSource.value = instance
  drawerVisible.value = true
}

async function openInstanceFromQuery() {
  const instanceId = route.query.instanceId
  if (instanceId == null || instanceId === '') return
  instanceLifecycleFilter.value = ''
  try {
    const data = await api.listInstances()
    const records = data?.success ? (data.data || []) : []
    const raw = records.find((item: any) => String(item.instanceId || item.id) === String(instanceId))
    if (!raw) {
      ElMessage.warning('未找到对应设备实例')
      return
    }
    const instance = normalizeInstance(raw)
    selectedModelId.value = String(instance.modelId || '')
    instancePageNo.value = 1
    await loadInstances()
    viewDetails(instance)
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || err.message || '打开设备实例失败')
  }
}

const confirmRetireInstance = async (row: DeviceInstance) => {
  try {
    await ElMessageBox.confirm(
      `确认注销设备实例“${row.instanceName}”？注销后该设备将不能参与控制与任务调度，现有历史数据完整保留。`,
      '注销确认',
      { confirmButtonText: '确认注销', cancelButtonText: '取消', type: 'warning' }
    )
    const data = await api.retireInstance(row.instanceId)
    if (data?.success) {
      ElMessage.success('设备实例已注销')
      await loadData()
    } else {
      ElMessage.error(data?.message || '注销失败')
    }
  } catch {
    // cancelled
  }
}

onMounted(async () => {
  loadProtocolMetadata().catch(() => {})
  await loadData()
  await openInstanceFromQuery()
})

watch(() => route.query.instanceId, () => {
  openInstanceFromQuery()
})

onUnmounted(() => {
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
})
</script>

<style scoped>
.device-instance-page {
  height: calc(100vh - 50px);
  padding: 10px 14px 14px;
  background-color: var(--sl-bg-page);
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
}
.instance-workbench-canvas {
  flex: 1;
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-lg);
  box-shadow: var(--sl-shadow-container);
  overflow: hidden;
  min-height: 0;
}
.workbench-sidebar {
  height: 100%;
  min-width: 0;
}
.workbench-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
  height: 100%;
}
.workbench-header {
  padding: 10px 16px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  gap: 12px;
}
.header-left { display: flex; flex-direction: column; gap: 2px; }
.header-title { margin: 0; font-size: 15px; font-weight: 700; color: var(--sl-text-heading); }
.header-subtitle { font-size: 11.5px; color: var(--sl-text-secondary); }
.header-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.lifecycle-filter { width: 125px; }
.instance-search { width: 220px; }
</style>
