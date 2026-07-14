<template>
  <div class="device-model-page">
    <section class="content-shell">
      <DeviceModelTree
        v-model:keyword="keyword"
        class="model-list-panel"
        :categories="categories"
        :models="models"
        :selected-model-id="selectedModelId"
        :selected-category-id="selectedCategoryId"
        :loading="loading"
        :can-create-model="canCreateModel"
        @select-model="selectModel"
        @select-category="selectCategory"
        @save-category="saveCategoryFromTree"
        @migrate-category="startCategoryMigration"
        @delete-category="deleteCategory"
        @create-model="openCreateDrawerWithCategory"
      />

      <main class="detail-panel">
        <div class="global-top-bar">
          <span class="global-toolbar-title">设备模型管理</span>
          <div class="global-toolbar-actions">
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
            <el-button v-if="canCreateModel" type="primary" :icon="Plus" @click="openCreateDrawer">新建设备模型</el-button>
          </div>
        </div>
        <DeviceCategoryDetail v-if="selectedCategory" :category="selectedCategory" :categoryPathLabel="selectedCategoryPathLabel" :categoryChildren="selectedCategoryChildren" :categoryModels="selectedCategoryModels" :categoryInstances="selectedCategoryInstances" :categoryBomUsages="selectedCategoryBomUsages" :categoryComponentSlots="selectedCategoryComponentSlots" :categoryDataAssets="selectedCategoryDataAssets" :canCreateModel="canCreateModel" :selectedCategoryCanCreateModel="selectedCategoryCanCreateModel" :categories="categories" :models="models" :categoryChildrenByParent="categoryChildrenByParent" @select-category="selectCategoryById" @select-model="selectModel" @create-model-in-category="openCreateDrawerWithCategory" />

        <DeviceModelDetail v-else-if="selectedModel" :model="selectedModel" :modelBundle="selectedModelBundle" :defaultTemplateAttributes="defaultTemplateAttributes" :categories="categories" :canEditModel="canEditModel" :canDeleteModel="canDeleteModel" :hasInstances="selectedModelHasInstances" @edit="openEditDrawer" @delete="deleteModel" @download="downloadModelBundle" />

        <el-empty v-else description="请选择或新建设备模型" :image-size="120" />
      </main>
    </section>




<DeviceModelEditorDrawer ref="editorDrawerRef" :categories="categories" @saved="handleModelSaved" />
    <el-dialog v-model="migrationDialogVisible" title="类别结构变更向导" width="800px" :close-on-click-modal="false" destroy-on-close>
      <el-alert title="类别下已有设备模型" type="warning" show-icon :closable="false" style="margin-bottom: 20px;">
        【{{ migrationState.parentCategory?.label }}】当前是叶子节点并挂载了设备模型。添加子类别后，它将变为中间节点。请在下方为其创建新子类别，并将现有模型分配到新类别下。
      </el-alert>
      <div style="display: flex; gap: 24px;">
        <div style="flex: 1; min-width: 0;">
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
            <h4 style="margin: 0;">1. 创建新子类别</h4>
            <el-button link type="primary" :icon="Plus" @click="migrationState.newCategories.push({ name: '' })">添加</el-button>
          </div>
          <div v-for="(item, index) in migrationState.newCategories" :key="index" style="display: flex; gap: 8px; margin-bottom: 12px;">
            <el-input v-model="item.name" placeholder="请输入子类别名称" />
            <el-button type="danger" plain :icon="Delete" @click="migrationState.newCategories.splice(index, 1)" :disabled="migrationState.newCategories.length <= 1" />
          </div>
        </div>
        <div style="flex: 1; min-width: 0; border-left: 1px solid var(--el-border-color-light); padding-left: 24px;">
          <h4 style="margin: 0 0 12px 0;">2. 现有模型分配</h4>
          <div v-for="assignment in migrationState.modelAssignments" :key="assignment.modelId" style="margin-bottom: 16px;">
            <div style="font-size: 13px; color: var(--el-text-color-regular); margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" :title="assignment.modelName">
              <el-icon style="vertical-align: middle; margin-right: 4px;"><Cpu /></el-icon>{{ assignment.modelName }}
            </div>
            <el-select v-model="assignment.targetCategoryIndex" style="width: 100%;" placeholder="选择目标子类别">
              <el-option v-for="(cat, cIndex) in migrationState.newCategories" :key="cIndex" :label="cat.name || `[未命名类别 ${cIndex + 1}]`" :value="cIndex" />
            </el-select>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="migrationDialogVisible = false" :disabled="migrationSubmitting">取消</el-button>
        <el-button type="primary" @click="confirmMigration" :loading="migrationSubmitting">确认迁移</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Cpu, Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'
import DeviceModelDetail from './components/deviceModel/DeviceModelDetail.vue'
import DeviceCategoryDetail from './components/deviceModel/DeviceCategoryDetail.vue'
import DeviceModelEditorDrawer from './components/deviceModel/DeviceModelEditorDrawer.vue'
import DeviceModelTree from './components/DeviceModelTree.vue'
import { loadModelWorkspace, listDeviceCategories, saveDeviceCategory, deleteDeviceCategory } from './components/deviceModel/deviceModelManagementApi'
import { saveDeviceModel, deleteDeviceModel, previewDeviceModel, fetchModelBundle as fetchModelBundleApi } from './components/deviceModel/deviceModelManagementApi'
import { listDataPropertyTypes, fetchDefaultTemplateAttributes, listRegisteredAdapters, fetchRegisteredAdapterContract } from './components/deviceModel/deviceModelManagementApi'
import { stringId, normalizeModelBundle, normalizeModel, asArray } from './components/deviceModel/normalizers.js'
import { adapterDeviceCategoryOptions, adapterCategoryKey, adapterCategoryLabel, buildAdapterContractFromManifestCategory } from './components/deviceModel/deviceModelConstants'
import { attributeDataTypes, adapterDataTypes, operators, interfaceTypes, stateActionNames, standardCmdEvents } from './components/deviceModel/deviceModelConstants'
import { getSignalTagType, formatSignalName, formatSignalShortName, getSignalsForInterface } from './components/deviceModel/deviceModelConstants'
import { defaultAdapterContract, defaultStateSpace, defaultCommandLifecycle, commandLifecycleStateNames } from './components/deviceModel/deviceModelConstants'
import { defaultInterfaces, adapterInterfaceName, adapterOutAction, defaultStateEntryActions, defaultCommandLifecycleTransitions } from './components/deviceModel/deviceModelConstants'

const authStore = useAuthStore()

const keyword = ref('')
const categories = ref([])
const models = ref([])
const deviceInstances = ref([])
const deviceComponents = ref([])
const dataIndexes = ref([])
const selectedModelId = ref('')
const selectedCategoryId = ref('')
const loading = ref(false)
const migrationDialogVisible = ref(false)
const migrationSubmitting = ref(false)
const editorDrawerRef = ref(null)

const migrationState = reactive({
  parentCategory: null,
  newCategories: [],
  modelAssignments: []
})

const selectedModel = computed(() => {
  const id = stringId(selectedModelId.value)
  if (!id) return null
  return models.value.find(item => stringId(item.modelId) === id) || null
})

const selectedCategory = computed(() => {
  const id = stringId(selectedCategoryId.value)
  if (!id) return null
  return categories.value.find(item => stringId(item.id) === id) || null
})

const selectedCategoryPathLabel = computed(() => {
  if (!selectedCategoryId.value) return ''
  return categoryPath(selectedCategoryId.value).join(' / ')
})

const categoryChildrenByParent = computed(() => {
  const groups = new Map()
  asArray(categories.value).forEach(item => {
    const pid = stringId(item.parentCategoryId)
    if (!groups.has(pid)) groups.set(pid, [])
    groups.get(pid).push(item)
  })
  return groups
})

const selectedCategoryChildren = computed(() => {
  if (!selectedCategoryId.value) return []
  return asArray(categoryChildrenByParent.value.get(selectedCategoryId.value))
})

const selectedCategoryModels = computed(() => {
  if (!selectedCategoryId.value) return []
  return models.value.filter(item => stringId(item.categoryId) === selectedCategoryId.value)
})

const selectedCategoryInstances = computed(() => {
  const ids = new Set(collectCategoryDescendantIds(selectedCategoryId.value))
  return deviceInstances.value.filter(item => ids.has(instanceModelId(item)))
})

const selectedCategoryBomUsages = computed(() => {
  const id = selectedCategoryId.value
  if (!id) return []
  const results = []
  models.value.forEach(m => {
    asArray(m.componentsBom).forEach(bom => {
      if (stringId(bom.deviceCategoryId) === id) {
        results.push({
          _key: makeUiKey('bom_use'),
          modelId: m.modelId,
          modelName: m.modelName,
          bomKey: bom._key,
          componentName: bom.componentName,
          minQuantity: bom.minQuantity,
          maxQuantity: bom.maxQuantity
        })
      }
    })
  })
  return results
})

const selectedCategoryComponentSlots = computed(() => {
  const id = selectedCategoryId.value
  if (!id) return []
  return deviceComponents.value.filter(item => stringId(item.deviceCategoryId) === id)
})

const selectedCategoryDataAssets = computed(() => {
  const ids = new Set(collectCategoryDescendantIds(selectedCategoryId.value))
  return dataIndexes.value.filter(item => {
    const modelId = instanceModelId(item)
    if (modelId) return ids.has(modelId)
    return false
  })
})

const canCreateModel = computed(() => authStore.hasPermission('device_model:create'))
const canEditModel = computed(() => authStore.hasPermission('device_model:edit'))
const canDeleteModel = computed(() => authStore.hasPermission('device_model:delete'))

const selectedModelHasInstances = computed(() => {
  if (!selectedModelId.value) return false
  return deviceInstances.value.some(ins => String(ins.modelId) === String(selectedModelId.value))
})

const selectedCategoryCanCreateModel = computed(() => {
  if (!selectedCategoryId.value) return false
  return selectedCategoryChildren.value.length === 0
})

function isCommandLifecycleTransition(row) {
  return defaultCommandLifecycleTransitions().some(item => item.fromStateName === row?.fromStateName && item.toStateName === row?.toStateName && item.trigger.interfaceName === row?.trigger?.interfaceName && item.trigger.signalName === row?.trigger?.signalName)
}


function startCategoryMigration(data) {
  const modelsInNode = data?.directModels?.length ? data.directModels : (data?.children ? data.children.filter(c => c.type === 'model') : [])
  if (modelsInNode.length === 0) return
  migrationState.parentCategory = data
  migrationState.newCategories = [{ name: '' }]
  migrationState.modelAssignments = modelsInNode.map(c => ({
    modelId: c.modelId,
    modelName: c.label,
    targetCategoryIndex: 0
  }))
  migrationDialogVisible.value = true
}

const confirmMigration = async () => {
  if (migrationState.newCategories.some(c => !c.name.trim())) {
    ElMessage.warning('子类别名称不能为空')
    return
  }
  if (migrationState.modelAssignments.some(a => a.targetCategoryIndex === null || a.targetCategoryIndex === undefined)) {
    ElMessage.warning('请为所有模型分配目标类别')
    return
  }

  migrationSubmitting.value = true
  try {
    const newCatIds = []
    for (const c of migrationState.newCategories) {
      const res = await saveDeviceCategory({
        categoryName: c.name.trim(),
        parentCategoryId: Number(migrationState.parentCategory.categoryId),
        description: ''
      })
      if (res.success) {
        newCatIds.push(res.data?.id || res.data?.categoryId)
      } else {
        throw new Error('创建类别 ' + c.name + ' 失败: ' + (res.message || ''))
      }
    }

    const drawer = editorDrawerRef.value
    const propertyTypes = await drawer.loadPropertyTypesForTemplate()
    for (const assignment of migrationState.modelAssignments) {
      const targetCatId = newCatIds[assignment.targetCategoryIndex]
      const modelData = models.value.find(m => m.modelId === assignment.modelId)
      if (modelData) {
        await loadDefaultTemplateForModel(modelData.modelId, modelData.attributes || [])
        const draft = drawer.fromModelToDraft(modelData)
        draft.basic.categoryValue = String(targetCatId)
        drawer.replaceDraft(draft)
        const payload = drawer.buildSavePayload(true, propertyTypes)
        await saveDeviceModel(payload)
      }
    }

    ElMessage.success('迁移成功')
    migrationDialogVisible.value = false
    await loadCategories()
    await loadData()
  } catch (error) {
    console.error(error)
    ElMessage.error(error.message || '迁移过程中发生错误')
  } finally {
    migrationSubmitting.value = false
  }
}


const deleteCategory = async (data) => {
  try {
    await ElMessageBox.confirm(`确认删除类别【${data.label}】吗？`, '提示', { type: 'warning' })
    const res = await deleteDeviceCategory(data.categoryId)
    if (res.success) {
      ElMessage.success('删除成功')
      await loadCategories()
      await loadData()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) { }
}

async function saveCategoryFromTree(payload) {
  await saveCategoryApi(payload.categoryName, payload.parentCategoryId ?? null, payload.categoryId || null, payload.description || '')
}

async function saveCategoryApi(categoryName, parentCategoryId, categoryId = null, description = '') {
  try {
    const payload = {
      categoryName,
      parentCategoryId,
      description
    }
    if (categoryId) payload.id = categoryId
    const res = await saveDeviceCategory(payload)
    if (res.success) {
      ElMessage.success('保存成功')
      await loadCategories()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存异常')
  }
}

function openCreateDrawerWithCategory(data) {
  const catId = data?.categoryId ?? data?.id
  editorDrawerRef.value?.openForCreate(catId)
}

const emptyModelBundle = () => ({ capabilityModel: {}, stateMachineModel: {} })
const selectedModelBundle = ref(emptyModelBundle())
const defaultTemplateAttributes = ref([])
const capabilityModelJson = computed(() => selectedModelBundle.value.capabilityModel || {})
const stateMachineModelJson = computed(() => selectedModelBundle.value.stateMachineModel || {})

async function loadData() {
  loading.value = true
  try {
    const workspace = await loadModelWorkspace()
    categories.value = workspace.categories
    models.value = workspace.models.map(raw => normalizeModel(raw, categories.value))
    deviceInstances.value = workspace.instances
    deviceComponents.value = workspace.components
    dataIndexes.value = workspace.dataIndexes

    const previousModelId = stringId(selectedModelId.value)
    const previousCategoryId = stringId(selectedCategoryId.value)
    let nextModelId = models.value.some(function(item) { return stringId(item.modelId) === previousModelId }) ? previousModelId : ''
    let nextCategoryId = categories.value.some(function(item) { return stringId(item.id) === previousCategoryId }) ? previousCategoryId : ''

    if (!nextModelId && !nextCategoryId) {
      if (models.value[0]?.modelId) nextModelId = stringId(models.value[0].modelId)
      else if (categories.value[0]?.id) nextCategoryId = stringId(categories.value[0].id)
    }

    const modelChanged = nextModelId !== previousModelId
    selectedModelId.value = nextModelId
    selectedCategoryId.value = nextModelId ? '' : nextCategoryId

    if (!nextModelId) {
      selectedModelBundle.value = emptyModelBundle()
      defaultTemplateAttributes.value = []
    } else if (!modelChanged) {
      await loadSelectedModelBundle(nextModelId)
    }
  } catch (err) {
    console.error('loadData error:', err)
    ElMessage.error(err.response?.data?.message || '加载设备模型失败')
  } finally {
    loading.value = false
  }
}
async function loadCategories() {
  categories.value = await listDeviceCategories()
}

async function fetchModelBundle(modelId) {
  return normalizeModelBundle(await fetchModelBundleApi(modelId))
}

async function loadDefaultTemplateForModel(modelId, attributes) {
  defaultTemplateAttributes.value = await fetchDefaultTemplateAttributes(modelId, attributes)
  return defaultTemplateAttributes.value
}

async function loadSelectedModelBundle(modelId = selectedModelId.value) {
  if (!modelId) {
    selectedModelBundle.value = emptyModelBundle()
    defaultTemplateAttributes.value = []
    return
  }
  try {
    selectedModelBundle.value = await fetchModelBundle(modelId)
    const currentModel = models.value.find(function(item) { return stringId(item.modelId) === stringId(modelId) })
    await loadDefaultTemplateForModel(modelId, currentModel?.attributes || [])
  } catch (err) {
    selectedModelBundle.value = emptyModelBundle()
    defaultTemplateAttributes.value = []
    ElMessage.error(err.message || '加载模型文件失败')
  }
}



watch(selectedModelId, id => {
  loadSelectedModelBundle(id)
})


function openCreateDrawer() {
  editorDrawerRef.value?.openForCreate(selectedCategoryId.value)
}

function handleModelSaved(savedModelId) {
  selectedCategoryId.value = ''
  selectedModelId.value = savedModelId
  loadData()
}

function openEditDrawer(model) {
  editorDrawerRef.value?.openForEdit(model, defaultTemplateAttributes.value)
}

async function downloadModelBundle() {
  if (!selectedModel.value) return
  try {
    const bundle = await fetchModelBundle(selectedModel.value.modelId)
    selectedModelBundle.value = bundle
    downloadJson('device-model-' + selectedModel.value.modelId + '.json', bundle)
  } catch (err) {
    ElMessage.error(err.message || '导出模型文件失败')
  }
}

function downloadJson(filename, data) {
  const blob = new Blob([formatJson(data)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

async function deleteModel(id) {
  try {
    const res = await deleteDeviceModel(id)
    if (!res.success) {
      ElMessage.error(res.message || '删除失败')
      return
    }
    ElMessage.success('删除成功')
    selectedModelId.value = ''
    await loadData()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}


function selectCategory(data) {
  selectCategoryById(data?.categoryId ?? data?.id)
}

function selectCategoryById(categoryId) {
  const id = stringId(categoryId)
  if (!id) return
  selectedModelId.value = ''
  selectedCategoryId.value = id
}

function parentCategoryName(category) {
  if (!category?.parentCategoryId) return '根类别'
  return categoryNameById(category.parentCategoryId) || '根类别'
}

function categoryPath(categoryId) {
  const path = []
  const visited = new Set()
  let currentId = stringId(categoryId)
  while (currentId && !visited.has(currentId)) {
    visited.add(currentId)
    const category = categories.value.find(item => stringId(item.id) === currentId)
    if (!category) break
    path.unshift(category.categoryName || '未命名类别')
    currentId = stringId(category.parentCategoryId)
  }
  return path
}

function collectCategoryDescendantIds(categoryId) {
  const rootId = stringId(categoryId)
  if (!rootId) return []
  const ids = []
  const visit = (id) => {
    ids.push(id)
    asArray(categoryChildrenByParent.value.get(id)).forEach(child => visit(stringId(child.id)))
  }
  visit(rootId)
  return ids
}

function categoryModelCount(categoryId) {
  const ids = new Set(collectCategoryDescendantIds(categoryId))
  return models.value.filter(model => ids.has(stringId(model.categoryId))).length
}

function modelNameById(modelId) {
  return models.value.find(model => stringId(model.modelId) === stringId(modelId))?.modelName || '-'
}

function instanceIdOf(instance) {
  return stringId(instance?.instanceId ?? instance?.id)
}

function instanceModelId(instance) {
  return stringId(instance?.deviceModelId ?? instance?.modelId)
}

function instanceNameOf(instance) {
  if (!instance) return '-'
  return instance.instanceName || instance.deviceName || `设备实例 ${instanceIdOf(instance)}`
}

function instanceNameById(instanceId) {
  const id = stringId(instanceId)
  if (!id) return '-'
  return instanceNameOf(deviceInstances.value.find(instance => instanceIdOf(instance) === id))
}

function selectModel(modelId) {
  selectedCategoryId.value = ''
  selectedModelId.value = modelId ? String(modelId) : ''
}
function categoryNameById(id) { return categories.value.find(item => String(item.id) === String(id))?.categoryName || '' }






function stringifyBrief(value) {
  const text = JSON.stringify(value || {})
  return text.length > 90 ? text.slice(0, 87) + '...' : text
}































// Normalize capabilities


















onMounted(loadData)
</script>

<style scoped>
.device-model-page { display: flex; flex-direction: column; height: calc(100vh - 52px); min-height: 0; padding: 0; gap: 0; background: #eef2f6; color: var(--color-text-main); font-size: 14px; }
.page-header { display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; gap: 16px; }
.page-title h1 { margin: 0 0 4px; font-size: 22px; line-height: 1.25; letter-spacing: 0; }
.page-title p { margin: 0; color: var(--color-text-sub); font-size: 13px; }
.header-actions, .detail-actions, .section-title, .config-import, .drawer-footer, .section-actions { display: flex; align-items: center; gap: 8px; }
.content-shell { flex: 1; min-height: 0; display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 0; border-top: 1px solid #ccd6e3; }
.model-list-panel, .detail-panel { min-height: 0; background: #fff; border: 0; border-radius: 0; box-shadow: none; }
.model-list-panel { display: flex; flex-direction: column; overflow: hidden; border-right: 1px solid #ccd6e3; }

.detail-panel { display: flex; flex-direction: column; overflow: hidden; padding: 0; }
.global-top-bar { flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0 !important; padding: 10px 14px !important; background: #fff; border-bottom: 1px solid #e5e7eb !important; }
.global-toolbar-title { color: #0f172a; font-size: 16px; font-weight: 750; }
.global-toolbar-actions { display: flex; align-items: center; gap: 8px; }
.detail-head { flex-shrink: 0; display: flex; justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.detail-title { min-width: 0; }
.detail-title h2 { margin: 0 0 4px; font-size: 20px; line-height: 1.25; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; letter-spacing: 0; }
.detail-title p { margin: 0; color: var(--color-text-sub); font-size: 13px; }
.detail-tabs { flex: 1; min-height: 0; overflow: hidden; }
.detail-tabs :deep(.el-tabs__content) { height: calc(100% - 46px); overflow: auto; padding-right: 4px; }
.info-section, .drawer-section { margin-top: 8px; }
.info-section { border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px; background: #fff; }
.drawer-section { border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px; background: #fff; }
.section-title { justify-content: space-between; margin-bottom: 8px; min-height: 26px; }
.section-title h3 { margin: 0; font-size: 15px; line-height: 1.3; letter-spacing: 0; }
.tag-gap { margin: 2px 6px 2px 0; }
.muted-text { color: var(--color-text-sub); font-size: 12px; }
.summary-card-list, .editor-card-list { display: grid; gap: 8px; }
.summary-card, .editor-card { border: 1px solid #dfe4ed; border-radius: 4px; background: #fff; overflow: hidden; }
.summary-card { padding: 9px 10px; box-shadow: none; }
.summary-card-head, .editor-card-head, .nested-toolbar, .locked-heading, .locked-action, .action-toolbar { display: flex; align-items: center; gap: 8px; }
.summary-card-head, .editor-card-head { justify-content: space-between; }
.summary-card-title, .editor-card-title { min-width: 0; display: flex; align-items: center; gap: 8px; flex: 1; }
.summary-card-title { flex-direction: column; align-items: flex-start; gap: 2px; }
.summary-card-title strong { font-size: 14px; line-height: 1.35; }
.summary-card-title span { max-width: 100%; color: var(--color-text-sub); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-card-body { margin-top: 6px; padding-left: 0; }
.item-index { width: 22px; height: 22px; flex: 0 0 22px; display: inline-flex; align-items: center; justify-content: center; border-radius: 6px; background: #eef2f7; color: #475569; font-size: 12px; font-weight: 700; }
.capability-summary-card, .capability-editor-card { border-left: 3px solid #409eff; }
.command-summary-card, .command-editor-card { border-left: 3px solid #10b981; }
.editor-card { padding: 9px; background: #fbfcfe; }
.editor-card-title :deep(.el-input) { flex: 1; min-width: 180px; }
.capability-title-editor { flex-wrap: wrap; }
.capability-title-editor :deep(.el-input) { max-width: 320px; }
.nested-toolbar { justify-content: space-between; margin: 10px 0 8px; color: var(--color-text-sub); font-size: 12px; font-weight: 600; }
.nested-table { background: #fff; }
.compact-empty { display: flex; align-items: center; min-height: 30px; padding: 7px 10px; border: 1px dashed #cbd5e1; border-radius: 4px; background: #f8fafc; color: #64748b; font-size: 13px; line-height: 1.4; }
.block-empty { margin-top: 4px; }
.inline-empty { margin-top: 8px; }
.template-attr-option { margin-right: 14px; margin-bottom: 8px; padding: 7px 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #f8fafc; }
.template-attr-name { font-weight: 600; color: #0f172a; }
.template-attr-meta { margin-left: 8px; color: #64748b; font-size: 12px; }
.model-json-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; min-height: 0; }
.json-panel { min-width: 0; border: 1px solid #dfe4ed; border-radius: 4px; padding: 10px; background: #fff; }
.json-panel pre { margin: 0; max-height: 560px; overflow: auto; padding: 12px; border-radius: 6px; background: #111827; color: #e5e7eb; font-size: 12px; line-height: 1.55; tab-size: 2; }
.info-section :deep(.el-table) { --el-table-header-bg-color: #f8fafc; }
.info-section :deep(.el-table th.el-table__cell) { color: #334155; font-weight: 600; }
.drawer-body { height: 100%; min-height: 0; }
.drawer-tabs { height: 100%; }
.drawer-tabs :deep(.el-tabs__content) { height: 100%; overflow: auto; padding: 0 4px 18px 18px; }
.drawer-tabs :deep(.el-tabs__header) { width: 116px; }
.basic-form, .mapping-form { max-width: 760px; }
.basic-form :deep(.el-select), .mapping-form :deep(.el-select) { width: 100%; }
.config-import { margin-bottom: 8px; }
.registered-adapter-picker { width: 100%; display: grid; grid-template-columns: minmax(180px, 1.1fr) minmax(180px, 1fr) auto auto; gap: 8px; align-items: center; }
.registered-adapter-picker :deep(.el-select) { width: 100%; }
.param-map-editor { display: flex; flex-direction: column; gap: 8px; }
.function-mapping-card-list { display: grid; gap: 10px; }
.function-mapping-editor-card { border: 1px solid #dbe4ef; border-left: 3px solid #2563eb; border-radius: 6px; background: #fff; padding: 10px; }
.function-map-editor-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.function-map-selects { flex: 1; min-width: 0; display: grid; grid-template-columns: minmax(180px, 1fr) 28px minmax(180px, 1fr); align-items: end; gap: 8px; }
.function-map-selects label { min-width: 0; display: grid; gap: 4px; }
.function-map-selects label > span { color: #64748b; font-size: 12px; font-weight: 700; }
.mapping-direction { align-self: center; color: #64748b; text-align: center; font-weight: 700; }
.compact-param-editor { margin-top: 10px; }
.param-map-row { display: grid; grid-template-columns: minmax(160px, 1fr) minmax(160px, 1fr) 104px 28px; align-items: end; gap: 8px; padding: 8px; border: 1px solid transparent; border-radius: 6px; background: #f8fafc; }
.param-map-row.fixed { grid-template-columns: minmax(150px, 1fr) minmax(150px, 1fr) 104px minmax(150px, 1fr) 28px; }
.param-map-row.invalid { border-color: #f4b4b4; background: #fff7f7; }
.param-map-field, .param-map-mode { min-width: 0; display: grid; gap: 4px; }
.param-map-label { color: #64748b; font-size: 11px; font-weight: 700; line-height: 1.2; }
.fixed-input-field .el-input { width: 100%; }
.param-delete-btn { align-self: center; }
.map-warning { grid-column: 1 / -1; color: #c2410c; font-size: 12px; line-height: 1.4; }
.locked-heading { display: flex; align-items: center; gap: 8px; min-width: 0; }
.locked-heading h3 { margin: 0; }
.locked-section { background: #ffffff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; }
.state-group-title { margin: 24px 0 12px; padding-bottom: 8px; border-bottom: 1px solid var(--el-border-color-lighter); color: var(--el-text-color-primary); font-size: 16px; font-weight: 600; }
.state-group-title.first { margin-top: 0; }
.state-card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.state-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; }
.state-summary-row { display: grid; grid-template-columns: 72px minmax(0, 1fr); align-items: center; gap: 10px; min-height: 32px; margin-top: 8px; }
.state-summary-row.align-top { align-items: flex-start; }
.state-summary-label { color: #64748b; font-size: 12px; line-height: 24px; }
.state-token-list { display: flex; flex-wrap: wrap; gap: 6px; min-width: 0; }
.state-token { border: 0; }
.state-token.filled { background: #dcfce7; color: #166534; }
.closable-state-token :deep(.el-tag__content) { display: inline-flex; align-items: center; gap: 4px; min-width: 0; }
.state-token-text { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.state-token-warning-slot, .warning-slot { width: 16px; min-width: 16px; display: inline-flex; align-items: center; justify-content: center; }
.state-warning-icon, .inline-warning-icon { color: #dc2626; font-size: 14px; line-height: 1; }
.state-input-with-warning, .field-with-warning { display: grid; grid-template-columns: 16px minmax(0, 1fr); align-items: center; gap: 6px; width: 100%; }
.state-input-with-warning { grid-template-columns: minmax(0, 1fr) 16px; }
.state-warning-panel { margin: 0 0 10px; padding: 8px 10px; border: 1px solid #fecaca; border-radius: 6px; background: #fff7f7; display: flex; flex-direction: column; gap: 4px; }
.state-warning-item { display: flex; align-items: center; gap: 6px; color: #991b1b; font-size: 12px; line-height: 1.4; }
.state-inline-select { width: 100%; }
.compact-state-table { margin-top: 10px; background: #fff; }
.compact-transition-table { width: 100%; }
.compact-transition-table :deep(.el-table__cell) { padding: 6px 8px; }
.transition-action-cell { display: grid; grid-template-columns: minmax(0, 1fr) 28px; gap: 6px; align-items: start; }
.transition-action-list { display: flex; flex-direction: column; gap: 6px; min-width: 0; align-items: flex-start; }
.transition-action-row { display: grid; grid-template-columns: 34px minmax(120px, 1fr) 26px; align-items: center; gap: 6px; width: 100%; }

.boxed-section { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px; background-color: #fff; }
.locked-table :deep(.el-table__body-wrapper) { background: #ffffff; }
.locked-action { max-width: 100%; color: #475569; font-size: 12px; line-height: 1.45; display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; white-space: normal; }
.locked-value { font-weight: 600; color: #334155; }
.action-editor { display: flex; flex-direction: column; gap: 6px; }
.action-row { display: grid; grid-template-columns: 112px minmax(220px, 1fr) 34px; align-items: center; gap: 6px; }
.action-toolbar { justify-content: flex-start; }
.drawer-footer { justify-content: flex-end; }
.category-manager-layout { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(260px, 0.8fr); gap: 12px; }
.category-existing-panel, .category-create-panel { border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; padding: 10px; }
.dialog-section-title { margin-bottom: 8px; color: #0f172a; font-weight: 800; font-size: 14px; }
.category-create-panel :deep(.el-select) { width: 100%; }

/* Premium Action & Parameter Mapping Styles */
.action-visual-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 2px 6px;
  margin: 2px 0;
  max-width: 100%;
}
.inline-action {
  margin-left: 6px !important;
  vertical-align: middle;
}
.compact-payload {
  font-family: Consolas, Monaco, Lucida Console, monospace;
  font-size: 11px;
  color: #0f172a;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 3px;
  padding: 1px 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
}
.actions-list-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  max-width: 100%;
}
.locked-action-container {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}
.lock-tag-flex {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  line-height: 22px;
}
.lock-tag-flex :deep(.el-icon) {
  margin-right: 2px;
  font-size: 12px;
}
.param-map-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 4px;
  padding: 2px 4px;
}
.no-mapping-placeholder {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-style: italic;
  display: inline-flex;
  align-items: center;
}
.add-mapping-btn {
  align-self: flex-end;
}


.section-heading { display: flex; align-items: center; gap: 8px; margin: 0 0 8px !important; padding-left: 10px; border-left: 4px solid #2563eb; color: #0f172a; font-size: 16px; font-weight: 700; }
.section-cluster { box-shadow: inset 3px 0 0 #dbeafe; }
.compact-section { padding-bottom: 8px; }
.section-count { color: #64748b; font-size: 12px; font-weight: 500; }
.attribute-grid, .operation-grid, .port-grid, .mapping-grid, .event-chip-grid, .constraint-list, .transition-card-list, .function-map-list { display: grid; gap: 8px; }
.attribute-grid { grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); }
.adapter-attribute-grid { grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
.operation-grid { grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); }
.port-grid { grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
.mapping-grid { grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
.attribute-tile, .operation-card, .port-tile, .mapping-tile, .function-map-card, .transition-view-card, .constraint-card { min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; }
.attribute-tile, .port-tile, .mapping-tile, .constraint-card { padding: 9px 10px; }
.tile-title, .port-name { color: #0f172a; font-size: 14px; font-weight: 650; line-height: 1.35; }
.tile-meta, .port-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; color: #475569; font-size: 12px; }
.tile-meta span, .port-meta span { display: inline-flex; align-items: center; min-height: 22px; padding: 1px 7px; border: 1px solid #d7dee8; border-radius: 4px; background: #fff; }
.operation-card { padding: 10px; border-left: 3px solid #2563eb; }
.adapter-command-card { border-left-color: #059669; }
.operation-head { display: flex; align-items: flex-start; gap: 9px; min-width: 0; }
.operation-head > div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.operation-head strong { color: #0f172a; font-size: 15px; line-height: 1.35; }
.operation-head span:not(.item-index) { color: #64748b; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.param-chip-row, .action-chip-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.info-chip, .action-chip, .fixed-value-chip { display: inline-flex; align-items: center; min-height: 24px; padding: 2px 8px; border-radius: 4px; font-size: 12px; line-height: 1.3; }
.info-chip { border: 1px solid #bfdbfe; background: #eff6ff; color: #1d4ed8; }
.action-chip { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
.fixed-value-chip { border: 1px solid #fde68a; background: #fffbeb; color: #92400e; }
.event-chip-grid { grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); }
.event-chip { min-width: 0; display: grid; gap: 3px; padding: 8px 10px; border: 1px solid #dbe2ea; border-left: 3px solid #94a3b8; border-radius: 5px; background: #fbfdff; }
.event-chip b { color: #0f172a; font-size: 13px; line-height: 1.35; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-chip em { color: #64748b; font-size: 12px; font-style: normal; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-chip-cmd { border-left-color: #2563eb; }
.event-chip-op { border-left-color: #059669; }
.mapping-tile { display: grid; grid-template-columns: minmax(150px, 1fr) 28px minmax(150px, 1fr); align-items: center; gap: 8px; }
.mapping-tile-labeled { padding: 8px; background: #f8fafc; }
.mapping-source, .mapping-target { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.mapping-source { color: #0f172a; font-weight: 600; }
.mapping-target { color: #1d4ed8; }
.mapping-side { min-width: 0; display: grid; grid-template-columns: 64px minmax(0, 1fr); align-items: center; gap: 8px; padding: 7px 8px; border: 1px solid #dbe4ef; border-radius: 5px; background: #fff; }
.mapping-side em { color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.mapping-side b { color: #0f172a; font-size: 13px; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-side { border-left: 3px solid #10b981; }
.fixed-side { border-left: 3px solid #f59e0b; background: #fffbeb; }
.mapping-arrow, .flow-arrow { color: #64748b; text-align: center; }
.adapter-command-line { display: inline-flex; align-items: center; gap: 6px; min-width: 0; color: #475569; }
.adapter-command-line em { padding: 2px 5px; border: 1px solid #dbe4ef; border-radius: 4px; background: #f1f5f9; color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.adapter-command-line b { color: #0f172a; font-size: 13px; font-weight: 750; }
.info-chip { display: inline-flex; align-items: center; gap: 5px; padding: 4px 7px; border: 1px solid #bfdbfe; border-radius: 5px; background: #eff6ff; color: #1d4ed8; font-size: 13px; }
.info-chip em, .info-chip i { padding: 1px 4px; border: 1px solid #dbe4ef; border-radius: 4px; background: #fff; color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.info-chip b { color: #1d4ed8; font-weight: 700; }
.function-map-card { padding: 10px; border-left: 3px solid #2563eb; }
.function-map-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.function-map-head strong { color: #0f172a; font-size: 15px; }
.function-map-head span { color: #64748b; font-size: 12px; }
.function-param-list { display: grid; gap: 6px; }
.function-param-row { display: grid; grid-template-columns: minmax(150px, 1fr) 28px minmax(150px, 1fr); align-items: center; gap: 8px; min-height: 34px; padding: 6px; border: 1px solid #e5e7eb; border-radius: 4px; background: #fff; }
.state-token-panel { display: flex; flex-wrap: wrap; gap: 7px; min-height: 32px; }
.filled-state-token { display: inline-flex; align-items: center; min-height: 26px; max-width: 180px; padding: 3px 10px; border-radius: 4px; background: #dcfce7; color: #166534; font-size: 13px; font-weight: 650; line-height: 1.35; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-state-token { background: #e0f2fe; color: #075985; }
.op-state-token { background: #dcfce7; color: #166534; }
.light-state-token { background: #eef6ee; color: #166534; }
.transition-card-list { grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); }
.transition-view-card { padding: 10px; }
.transition-card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.transition-card-head strong { min-width: 0; color: #0f172a; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.transition-card-head span { color: #64748b; font-size: 12px; white-space: nowrap; }
.transition-flow { display: flex; align-items: center; gap: 8px; }
.constraint-list { grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
.constraint-card { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 10px; }
.constraint-card strong { color: #0f172a; font-size: 14px; }
.constraint-card span { color: #b45309; font-weight: 650; }
.constraint-card em { color: #64748b; font-size: 12px; font-style: normal; }
.drawer-section { margin-top: 8px; }
.drawer-section :deep(.el-table__cell) { padding: 5px 6px; }
.drawer-section :deep(.el-input__wrapper), .drawer-section :deep(.el-select__wrapper) { min-height: 30px; }
.drawer-section .nested-toolbar { margin: 8px 0 6px; }
.drawer-section .editor-card { padding: 8px; }
.drawer-section .editor-card-list { gap: 8px; }


.category-detail-scroll { flex: 1; min-height: 0; background: #f8fafc; }
.category-detail-scroll :deep(.el-scrollbar__view) { padding: 12px 14px 18px; }
.category-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin-bottom: 10px; }
.category-stat-item { min-width: 0; display: grid; gap: 5px; padding: 10px 12px; border: 1px solid #dbe4ef; border-left: 3px solid #2563eb; border-radius: 4px; background: #fff; }
.category-stat-item span { color: #64748b; font-size: 12px; font-weight: 700; }
.category-stat-item strong { color: #0f172a; font-size: 22px; line-height: 1; }
.category-section { margin-top: 10px; }
.category-child-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; }
.category-child-item { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 10px; border: 1px solid #dbe4ef; border-radius: 4px; background: #fff; color: #0f172a; cursor: pointer; text-align: left; }
.category-child-item:hover { border-color: #93c5fd; background: #eff6ff; }
.category-child-item span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 700; }
.category-child-item em { color: #64748b; font-size: 12px; font-style: normal; }
.compact-category-table :deep(.el-table__cell) { padding: 7px 8px; }

@media (max-width: 1120px) { .content-shell { grid-template-columns: 240px minmax(0, 1fr); } .model-json-grid { grid-template-columns: 1fr; } }
@media (max-width: 820px) { .state-card-grid { grid-template-columns: 1fr; } .device-model-page { padding: 10px; } .page-header, .detail-head { align-items: stretch; flex-direction: column; } .content-shell { grid-template-columns: 1fr; } .model-list-panel { min-height: 260px; } .drawer-tabs :deep(.el-tabs__header) { width: 92px; } .registered-adapter-picker, .function-map-selects, .param-map-row, .param-map-row.fixed, .action-row { grid-template-columns: 1fr; } .mapping-direction { display: none; } .summary-card-body { padding-left: 0; } }

/* 严肃的函数式动作展示与编辑器排版 */
.serious-actions-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.serious-action-wrapper {
  display: inline-flex;
  align-items: center;
}
.serious-action-tag {
  font-weight: 500;
  color: #334155;
  background-color: #ffffff;
  border-color: #cbd5e1;
}
.serious-action-editor {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.action-editor-label { color: #64748b; font-size: 12px; }
.no-action-cell { display: inline-flex; align-items: center; gap: 8px; color: #64748b; }
.compact-action-btn { padding: 4px 8px; }
/* Device model workbench refinements */
.model-summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  flex-shrink: 0;
  border-top: 1px solid #dfe6ef;
  border-bottom: 1px solid #dfe6ef;
  background: #dfe6ef;
}
.model-summary-strip > div {
  min-width: 0;
  padding: 8px 12px;
  background: #f8fafc;
}
.model-summary-strip span {
  display: block;
  margin-bottom: 3px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}
.model-summary-strip strong {
  display: block;
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}


</style>