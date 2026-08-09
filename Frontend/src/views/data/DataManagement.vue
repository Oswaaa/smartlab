<template>
  <div class="data-workbench">
    <aside class="asset-pane">
      <div class="asset-header">
        <div>
          <strong>数据中心</strong>
          <span>设备数据 / 模板库</span>
        </div>
      </div>
      <div class="tree-search-row">
        <el-input v-model="keyword" size="small" clearable :prefix-icon="Search" placeholder="搜索设备、模型、数据表" />
      </div>
      <div class="asset-tree" v-loading="loading">
        <el-tree :data="filteredTree" node-key="key" default-expand-all :expand-on-click-node="false" @node-click="handleTreeClick">
          <template #default="{ data }">
            <div class="asset-node" :class="{ active: activeKey === data.key, dataset: data.type === 'dataset', template: data.type === 'template' }">
              <span class="asset-node-main" :title="data.label">
                <el-icon v-if="['root','category'].includes(data.type)" class="node-icon category-icon"><FolderOpened v-if="data.type === 'category'" /><Folder v-else /></el-icon>
                <el-icon v-else-if="data.type === 'model'" class="node-icon model-icon"><Document /></el-icon>
                <el-icon v-else-if="data.type === 'instance'" class="node-icon instance-icon"><Cpu /></el-icon>
                <el-icon v-else-if="data.type === 'template'" class="node-icon template-icon"><Document /></el-icon>
                <el-icon v-else class="node-icon dataset-icon"><Tickets /></el-icon>
                <span class="node-label">{{ data.label }}</span>
              </span>
              <div class="asset-node-side">
                <div class="node-actions" @click.stop>
                  <el-tooltip v-if="data.type === 'model'" content="用该模型新建模板" placement="top">
                    <button class="node-action model-action" type="button" aria-label="新建模板" @click.stop="openTemplateDrawerWithModel(data.data)">
                      <el-icon><DocumentAdd /></el-icon>
                    </button>
                  </el-tooltip>
                  <el-tooltip v-if="data.type === 'instance' && isUsableInstance(data.data)" content="为该设备建表" placement="top">
                    <button class="node-action" type="button" aria-label="新建数据表" @click.stop="openDatasetDrawer(null, data.data)">
                      <el-icon><Plus /></el-icon>
                    </button>
                  </el-tooltip>
                  <el-tooltip v-if="data.type === 'template' && templateHasModel(data.data)" content="用此模板建表" placement="top">
                    <button class="node-action" type="button" aria-label="用模板建表" @click.stop="openDatasetDrawer(data.data)">
                      <el-icon><Plus /></el-icon>
                    </button>
                  </el-tooltip>
                </div>
                <em v-if="data.count != null" class="count-tag">{{ data.count }}</em>
              </div>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!filteredTree.length" description="暂无数据资产" />
      </div>
    </aside>

    <section class="data-workspace">
      <!-- 1. Dataset View -->
      <template v-if="selectedDataset">
        <div class="metric-ribbon">
          <div class="metric-cell"><span class="label">设备模型</span><strong class="val">{{ modelName(selectedInstance?.deviceModelId) }}</strong></div>
          <div class="metric-cell"><span class="label">绑定实例</span><strong class="val">{{ instanceName(selectedDataset.deviceInstanceId) }}</strong></div>
          <div class="metric-cell"><span class="label">数据模板</span><strong class="val">{{ templateName(selectedDataset.dataTemplateId) }}</strong></div>
          <div class="metric-cell"><span class="label">历史数据总量</span><strong class="val highlight">{{ recordPage.total }} 条</strong></div>
        </div>

        <main class="workspace-body">
          <div class="chart-box-wrapper">
            <div class="chart-header">
              <strong>遥测趋势分析</strong>
              <span class="chart-live-tag"><i class="live-dot"></i> 实时采样 (1s)</span>
            </div>
            <div ref="chartRef" class="chart-box"></div>
          </div>
          <div class="table-section">
            <div class="table-header">
              <div class="table-title"><strong>历史数据明细</strong><span class="count-pill">{{ recordPage.total }} 条记录</span></div>
              <div class="table-actions">
                <el-button size="small" class="btn-aliyun" @click="loadRecords()">刷新</el-button>
                <el-button size="small" class="btn-aliyun" @click="exportDataset">导出 CSV</el-button>
                <el-popconfirm title="确认删除该数据表？物理表会同步删除" @confirm="deleteDataset(selectedDataset)">
                  <template #reference><el-button size="small" link class="btn-aliyun-danger-link">删除数据表</el-button></template>
                </el-popconfirm>
              </div>
            </div>
            <el-table :data="records" border stripe size="small" v-loading="loadingRecords" class="record-table">
              <el-table-column label="Adapter 采集时间" width="180"><template #default="{ row }">{{ formatTime(recordTime(row)) }}</template></el-table-column>
              <el-table-column label="系统入库时间" width="180"><template #default="{ row }">{{ formatTime(recordIngestTime(row)) }}</template></el-table-column>
              <el-table-column v-for="field in valueFields" :key="field.columnName" :label="fieldLabel(field)" min-width="150">
                <template #default="{ row }">{{ valueOf(row, field.columnName) }}</template>
              </el-table-column>
            </el-table>
            <el-pagination class="pager" background size="small" layout="total, sizes, prev, pager, next" :total="recordPage.total" :current-page="recordPage.pageNo" :page-size="recordPage.pageSize" :page-sizes="[50,100,200,500]" @current-change="page => { recordPage.pageNo = page; loadRecords() }" @size-change="size => { recordPage.pageNo = 1; recordPage.pageSize = size; loadRecords() }" />
          </div>
        </main>
      </template>

      <!-- 2. Template View -->
      <template v-else-if="selectedTemplate">
        <div class="metric-ribbon">
          <div class="metric-cell"><span class="label">模板名称</span><strong class="val">{{ selectedTemplate.templateName }}</strong></div>
          <div class="metric-cell"><span class="label">来源模型</span><strong class="val">{{ modelName(selectedTemplate.deviceModelId) }}</strong></div>
          <div class="metric-cell"><span class="label">模板类型</span><strong class="val">{{ selectedTemplate.isDefault ? '默认模板' : '自定义模板' }}</strong></div>
          <div class="metric-cell"><span class="label">创建时间</span><strong class="val">{{ formatTime(selectedTemplate.createTime) }}</strong></div>
        </div>

        <main class="workspace-body">
          <div class="table-section">
            <div class="table-header">
              <div class="table-title"><strong>模板字段定义</strong><span class="count-pill">{{ templateDetails.length }} 个字段</span></div>
              <div class="table-actions">
                <el-button v-if="templateHasModel(selectedTemplate)" size="small" class="btn-aliyun" @click="openDatasetDrawer(selectedTemplate)">+ 用此模板建表</el-button>
                <el-popconfirm v-if="!selectedTemplate.isDefault" title="确认删除该模板？" @confirm="deleteTemplate(selectedTemplate)">
                  <template #reference><el-button size="small" link class="btn-aliyun-danger-link">删除模板</el-button></template>
                </el-popconfirm>
              </div>
            </div>
            <el-table :data="templateDetails" border stripe size="small" class="template-table">
              <el-table-column prop="columnName" label="模板字段" min-width="160" />
              <el-table-column prop="columnDesc" label="字段说明" min-width="160" />
              <el-table-column label="绑定属性" min-width="150"><template #default="{ row }">{{ bindingLabel(row) }}</template></el-table-column>
              <el-table-column label="默认值" width="130"><template #default="{ row }">{{ row.defaultValue || '-' }}</template></el-table-column>
              <el-table-column label="字段用途" width="120"><template #default="{ row }">{{ isUnitField(row) ? '单位' : '采集值' }}</template></el-table-column>
            </el-table>
          </div>
        </main>
      </template>

      <!-- 3. Instance View -->
      <template v-else-if="selectedInstance">
        <div class="metric-ribbon">
          <div class="metric-cell"><span class="label">设备实例</span><strong class="val">{{ selectedInstance.instanceName }}</strong></div>
          <div class="metric-cell"><span class="label">所属模型</span><strong class="val">{{ modelName(selectedInstance.deviceModelId) }}</strong></div>
          <div class="metric-cell"><span class="label">绑定 Adapter</span><strong class="val">{{ selectedInstance.boundAdapterName || '已配置' }}</strong></div>
          <div class="metric-cell"><span class="label">设备状态</span><strong class="val active-status">{{ selectedInstance.lifecycleStatus || 'IN_USE 正常运行' }}</strong></div>
        </div>

        <main class="workspace-body">
          <div class="table-section">
            <div class="table-header">
              <div class="table-title"><strong>该设备已关联的数据表</strong><span class="count-pill">{{ instanceDatasets(selectedInstance).length }} 张表</span></div>
              <div class="table-actions">
                <el-button v-if="isUsableInstance(selectedInstance)" size="small" class="btn-aliyun" @click="openDatasetDrawer(null, selectedInstance)">+ 为该设备建表</el-button>
              </div>
            </div>
            <el-table v-if="instanceDatasets(selectedInstance).length" :data="instanceDatasets(selectedInstance)" border stripe size="small">
              <el-table-column prop="dataTable" label="数据表名" min-width="180" />
              <el-table-column prop="dataDesc" label="数据表说明" min-width="200" />
              <el-table-column label="依赖模板" min-width="160"><template #default="{ row }">{{ templateName(row.dataTemplateId) }}</template></el-table-column>
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button size="small" link class="btn-aliyun-link" @click="selectDataset(row)">查看详情</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="该设备实例下暂无数据表"><el-button v-if="isUsableInstance(selectedInstance)" class="btn-aliyun" @click="openDatasetDrawer(null, selectedInstance)">为该设备建表</el-button></el-empty>
          </div>
        </main>
      </template>

      <!-- 4. Model View -->
      <template v-else-if="selectedModel">
        <div class="metric-ribbon">
          <div class="metric-cell"><span class="label">设备模型</span><strong class="val">{{ selectedModel.modelName }}</strong></div>
          <div class="metric-cell"><span class="label">物理分类</span><strong class="val">{{ categoryName(selectedModel.categoryId) }}</strong></div>
          <div class="metric-cell"><span class="label">衍生实例数</span><strong class="val highlight">{{ modelInstances(selectedModel).length }} 个</strong></div>
          <div class="metric-cell"><span class="label">关联模板数</span><strong class="val">{{ modelTemplates(selectedModel).length }} 个</strong></div>
        </div>

        <main class="workspace-body">
          <!-- 核心列表 1: 设备实例列表 -->
          <div class="table-section">
            <div class="table-header">
              <div class="table-title"><strong>该模型下的设备实例列表</strong><span class="count-pill">{{ modelInstances(selectedModel).length }} 个实例</span></div>
            </div>
            <el-table v-if="modelInstances(selectedModel).length" :data="modelInstances(selectedModel)" border stripe size="small">
              <el-table-column prop="instanceName" label="设备实例名称" min-width="180" />
              <el-table-column label="绑定 Adapter" min-width="150"><template #default="{ row }">{{ row.boundAdapterName || '-' }}</template></el-table-column>
              <el-table-column label="设备点位" min-width="140"><template #default="{ row }">{{ row.boundDevicePoint || '-' }}</template></el-table-column>
              <el-table-column label="已建数据表" width="120"><template #default="{ row }">{{ instanceDatasets(row).length }} 张表</template></el-table-column>
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button size="small" link class="btn-aliyun-link" @click="selectInstance(row)">查看详情</el-button>
                  <el-button v-if="isUsableInstance(row)" size="small" link class="btn-aliyun-link" @click="openDatasetDrawer(null, row)">建表</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="该模型下暂无设备实例" />
          </div>

          <!-- 核心列表 2: 数据模板列表 -->
          <div class="table-section" style="margin-top: 12px; border-top: 1px solid #e2e8f0;">
            <div class="table-header">
              <div class="table-title"><strong>关联的数据模板</strong><span class="count-pill">{{ modelTemplates(selectedModel).length }} 个模板</span></div>
              <div class="table-actions">
                <el-button size="small" class="btn-aliyun" @click="openTemplateDrawerWithModel(selectedModel)">+ 为该模型新建模板</el-button>
              </div>
            </div>
            <el-table v-if="modelTemplates(selectedModel).length" :data="modelTemplates(selectedModel)" border stripe size="small">
              <el-table-column prop="templateName" label="模板名称" min-width="180" />
              <el-table-column prop="templateDesc" label="模板说明" min-width="200" />
              <el-table-column label="模板类型" width="120"><template #default="{ row }">{{ row.isDefault ? '默认模板' : '自定义模板' }}</template></el-table-column>
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button size="small" link class="btn-aliyun-link" @click="selectTemplate(row)">查看 Schema</el-button>
                  <el-button size="small" link class="btn-aliyun-link" @click="openDatasetDrawer(row)">建表</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="该设备模型下暂无数据模板"><el-button class="btn-aliyun" @click="openTemplateDrawerWithModel(selectedModel)">为该模型新建模板</el-button></el-empty>
          </div>
        </main>
      </template>

      <!-- 5. Category View -->
      <template v-else-if="selectedCategory">
        <div class="metric-ribbon">
          <div class="metric-cell"><span class="label">物理分类</span><strong class="val">{{ selectedCategory.categoryName }}</strong></div>
          <div class="metric-cell"><span class="label">包含模型数</span><strong class="val">{{ categoryModels(selectedCategory).length }} 个</strong></div>
          <div class="metric-cell"><span class="label">包含实例数</span><strong class="val highlight">{{ categoryInstances(selectedCategory).length }} 个</strong></div>
          <div class="metric-cell"><span class="label">关联数据表数</span><strong class="val highlight">{{ categoryDatasets(selectedCategory).length }} 张</strong></div>
        </div>

        <main class="workspace-body">
          <div class="table-section">
            <div class="table-header">
              <div class="table-title"><strong>该分类下的设备模型</strong><span class="count-pill">{{ categoryModels(selectedCategory).length }} 个模型</span></div>
            </div>
            <el-table v-if="categoryModels(selectedCategory).length" :data="categoryModels(selectedCategory)" border stripe size="small">
              <el-table-column prop="modelName" label="模型名称" min-width="200" />
              <el-table-column prop="modelCode" label="模型编码" min-width="160" />
              <el-table-column label="关联模板数" width="120"><template #default="{ row }">{{ modelTemplates(row).length }}</template></el-table-column>
              <el-table-column label="衍生实例数" width="120"><template #default="{ row }">{{ modelInstances(row).length }}</template></el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button size="small" link class="btn-aliyun-link" @click="selectModel(row)">进入模型</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="该分类下暂无设备模型" />
          </div>
        </main>
      </template>

      <main v-else class="workspace-body">
        <section class="workspace-empty"><el-empty description="请选择左侧数据资产节点" /></section>
      </main>
    </section>

    <el-drawer v-model="datasetDrawer.visible" title="新建数据表" size="680px" class="unified-workflow-drawer">
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="数据表说明"><el-input v-model="datasetDrawer.dataDesc" placeholder="例如：高压报警专项数据" /></el-form-item>
        <el-form-item label="数据模板">
          <div class="template-select-row">
            <el-select v-model="datasetDrawer.templateId" filterable placeholder="请选择同模型模板" @change="onDatasetTemplateChange">
              <el-option v-for="tpl in compatibleTemplates" :key="tpl.id" :label="`${modelName(tpl.deviceModelId)} / ${tpl.templateName}`" :value="tpl.id" />
            </el-select>
            <el-button size="small" link class="btn-aliyun-link" @click="openTemplateDrawerForCurrentInstance">
              + 自定义新模板
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="绑定设备实例"><el-select v-model="datasetDrawer.deviceInstanceId" filterable placeholder="请选择同模型设备" @change="onDatasetInstanceChange"><el-option v-for="ins in compatibleInstances" :key="instanceId(ins)" :label="instancePath(ins)" :value="Number(instanceId(ins))" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button class="btn-aliyun" @click="datasetDrawer.visible = false">取消</el-button><el-button class="btn-aliyun-cta" :loading="saving" @click="createDataset">保存建表</el-button></template>
    </el-drawer>

    <el-drawer v-model="templateDrawer.visible" title="新增数据模板" size="680px" class="unified-workflow-drawer">
      <el-form label-position="top" class="drawer-form">
        <div class="form-grid two">
          <el-form-item label="模板名称"><el-input v-model="templateDrawer.templateName" /></el-form-item>
          <el-form-item label="绑定模型"><el-select v-model="templateDrawer.deviceModelId" filterable @change="generateFieldsFromModel"><el-option v-for="model in models" :key="modelId(model)" :label="model.modelName" :value="Number(modelId(model))" /></el-select></el-form-item>
        </div>
        <el-form-item label="模板说明"><el-input v-model="templateDrawer.templateDesc" /></el-form-item>
        <div class="table-header"><strong>模板字段</strong><el-button size="small" class="btn-aliyun" @click="addTemplateField">新增字段</el-button></div>
        <div class="field-editor">
          <div class="field-head"><span>模板字段</span><span>字段说明</span><span>绑定属性</span><span>默认值</span><span></span></div>
          <div v-for="(field, idx) in templateDrawer.details" :key="field._key" class="field-row">
            <el-input v-model="field.columnName" />
            <el-input v-model="field.columnDesc" />
            <el-select v-model="field.deviceAttrKey" clearable placeholder="可为空"><el-option v-for="attr in selectedTemplateModelAttrs" :key="attr.attributeName" :label="attr.displayName || attr.attributeName" :value="attr.attributeName" /></el-select>
            <el-input v-model="field.defaultValue" placeholder="无默认值" />
            <el-button link class="btn-aliyun-danger-link" @click="templateDrawer.details.splice(idx, 1)">删除</el-button>
          </div>
        </div>
      </el-form>
      <template #footer><el-button class="btn-aliyun" @click="templateDrawer.visible = false">取消</el-button><el-button class="btn-aliyun-cta" :loading="saving" @click="saveTemplate">保存模板</el-button></template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Cpu, Folder, FolderOpened, Tickets, Menu, Clock, Odometer, List, Plus, DocumentAdd, Document, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const loading = ref(false)
const loadingRecords = ref(false)
const saving = ref(false)
const keyword = ref('')
const activeKey = ref('')
const categories = ref([])
const models = ref([])
const instances = ref([])
const datasets = ref([])
const templates = ref([])
const propertyTypes = ref([])
const templateDetails = ref([])
const records = ref([])
const selectedDataset = ref(null)
const selectedTemplate = ref(null)
const selectedInstance = ref(null)
const selectedModel = ref(null)
const selectedCategory = ref(null)
const chartRef = ref(null)
const chart = shallowRef(null)
const recordPage = reactive({ pageNo: 1, pageSize: 100, total: 0 })
const detailCache = reactive({})

const datasetDrawer = reactive({ visible: false, templateId: null, deviceInstanceId: null, dataDesc: '' })
const templateDrawer = reactive({ visible: false, templateName: '', templateDesc: '', deviceModelId: null, details: [] })

const modelMap = computed(() => Object.fromEntries(models.value.map(m => [String(modelId(m)), m])))
const templateMap = computed(() => Object.fromEntries(templates.value.map(t => [String(t.id), t])))
const usableInstances = computed(() => instances.value.filter(isUsableInstance))
const compatibleTemplates = computed(() => {
  const instance = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  const selectedModelId = instance?.deviceModelId || instance?.modelId
  return templates.value.filter(template => templateHasModel(template)
    && (!selectedModelId || String(template.deviceModelId) === String(selectedModelId)))
})
const compatibleInstances = computed(() => {
  const template = templates.value.find(row => Number(row.id) === Number(datasetDrawer.templateId))
  const selectedModelId = template?.deviceModelId
  return usableInstances.value.filter(instance => !selectedModelId
    || String(instance.deviceModelId || instance.modelId) === String(selectedModelId))
})
const childrenByCategory = computed(() => {
  const map = new Map()
  categories.value.forEach(cat => {
    const parent = cat.parentCategoryId == null ? 'root' : String(cat.parentCategoryId)
    if (!map.has(parent)) map.set(parent, [])
    map.get(parent).push(cat)
  })
  return map
})
const selectedTemplateModelAttrs = computed(() => asArray(modelMap.value[String(templateDrawer.deviceModelId)]?.attributes))
const valueFields = computed(() => templateDetails.value.filter(row => !isUnitField(row)))
const unitMap = computed(() => {
  const map = new Map()
  templateDetails.value.filter(isUnitField).forEach(row => {
    map.set(stripUnit(row.columnName), row.defaultValue || '')
    if (row.deviceAttrKey) map.set(stripUnit(row.deviceAttrKey), row.defaultValue || '')
  })
  return map
})
const workspaceTag = computed(() => selectedDataset.value ? '数据表' : selectedTemplate.value ? '数据模板' : selectedInstance.value ? '设备实例' : '数据资产')
const workspaceTitle = computed(() => selectedDataset.value?.dataDesc || selectedDataset.value?.dataTable || selectedTemplate.value?.templateName || selectedInstance.value?.instanceName || '请选择数据对象')
const breadcrumbPath = computed(() => {
  if (selectedDataset.value) {
    const mName = modelName(selectedInstance.value?.deviceModelId)
    const iName = selectedInstance.value?.instanceName || '-'
    return `设备模型 (${mName}) / 实例 (${iName})`
  }
  if (selectedTemplate.value) {
    const mName = modelName(selectedTemplate.value.deviceModelId)
    return `设备模型 (${mName})`
  }
  if (selectedInstance.value) {
    const mName = modelName(selectedInstance.value.deviceModelId)
    return `设备模型 (${mName})`
  }
  return '数据资产中心'
})
const assetTree = computed(() => [
  { key: 'root_device_data', type: 'root', label: '设备数据', count: datasets.value.length, children: buildCategoryNodes('root') },
  { key: 'root_templates', type: 'root', label: '模板库', count: templates.value.length, children: templates.value.map(t => ({ key: 'template_' + t.id, type: 'template', label: t.templateName + (templateHasModel(t) ? '' : '（模型缺失）'), data: t, count: t.isDefault ? '默认' : '自定义' })) }
])
const filteredTree = computed(() => filterTree(assetTree.value, keyword.value.trim().toLowerCase()))

async function loadAll() {
  loading.value = true
  try {
    const [catRes, modelRes, insRes, datasetRes, tplRes, typeRes] = await Promise.all([
      axios.get('/api/device/category/list').catch(() => ({ data: { data: [] } })),
      axios.get('/api/device/model/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/data/index/list'),
      axios.get('/api/data/template/list'),
      axios.get('/api/data/property-type/list')
    ])
    categories.value = asArray(catRes.data?.data)
    models.value = asArray(modelRes.data?.data)
    instances.value = asArray(insRes.data?.data)
    datasets.value = asArray(datasetRes.data?.data)
    templates.value = asArray(tplRes.data?.data)
    propertyTypes.value = asArray(typeRes.data?.data)
    if (!selectedDataset.value && !selectedTemplate.value && !selectedInstance.value) selectFirstAvailable()
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载数据中心失败'))
  } finally {
    loading.value = false
  }
}

function buildCategoryNodes(parentKey) {
  return asArray(childrenByCategory.value.get(parentKey)).map(cat => {
    const modelNodes = models.value.filter(m => String(m.categoryId) === String(cat.id)).map(model => buildModelNode(model))
    return { key: 'category_' + cat.id, type: 'category', label: cat.categoryName, data: cat, count: modelNodes.length, children: [...buildCategoryNodes(String(cat.id)), ...modelNodes] }
  }).filter(node => node.children.length > 0 || node.count > 0)
}

function buildModelNode(model) {
  const modelInstances = instances.value.filter(ins => String(ins.deviceModelId || ins.modelId) === String(modelId(model)))
  return { key: 'model_' + modelId(model), type: 'model', label: model.modelName, data: model, count: modelInstances.length, children: modelInstances.map(buildInstanceNode) }
}

function buildInstanceNode(instance) {
  const rows = datasets.value.filter(ds => String(ds.deviceInstanceId) === String(instanceId(instance)))
  return { key: 'instance_' + instanceId(instance), type: 'instance', label: instance.instanceName, data: instance, count: rows.length, children: rows.map(ds => ({ key: 'dataset_' + ds.id, type: 'dataset', label: ds.dataDesc || ds.dataTable, data: ds })) }
}

function filterTree(nodes, kw) {
  if (!kw) return nodes
  return nodes.map(node => {
    const children = filterTree(asArray(node.children), kw)
    const hit = String(node.label || '').toLowerCase().includes(kw)
    return hit || children.length ? { ...node, children } : null
  }).filter(Boolean)
}

async function handleTreeClick(node) {
  activeKey.value = node.key
  if (node.type === 'dataset') return selectDataset(node.data)
  if (node.type === 'template') return selectTemplate(node.data)
  if (node.type === 'instance') return selectInstance(node.data)
  if (node.type === 'model') return selectModel(node.data)
  if (node.type === 'category') return selectCategory(node.data)
}

async function selectDataset(dataset) {
  selectedDataset.value = dataset
  selectedTemplate.value = null
  selectedInstance.value = instances.value.find(ins => String(instanceId(ins)) === String(dataset.deviceInstanceId)) || null
  selectedModel.value = null
  selectedCategory.value = null
  activeKey.value = 'dataset_' + dataset.id
  recordPage.pageNo = 1
  await loadTemplateDetails(dataset.dataTemplateId)
  await loadRecords()
}

async function selectTemplate(template) {
  selectedTemplate.value = template
  selectedDataset.value = null
  selectedInstance.value = null
  selectedModel.value = null
  selectedCategory.value = null
  activeKey.value = 'template_' + template.id
  records.value = []
  disposeChart()
  await loadTemplateDetails(template.id)
}

function selectInstance(instance) {
  selectedInstance.value = instance
  selectedDataset.value = null
  selectedTemplate.value = null
  selectedModel.value = null
  selectedCategory.value = null
  activeKey.value = 'instance_' + instanceId(instance)
  records.value = []
  templateDetails.value = []
  disposeChart()
}

function selectModel(model) {
  selectedModel.value = model
  selectedCategory.value = null
  selectedDataset.value = null
  selectedTemplate.value = null
  selectedInstance.value = null
  activeKey.value = 'model_' + modelId(model)
  records.value = []
  templateDetails.value = []
  disposeChart()
}

function selectCategory(category) {
  selectedCategory.value = category
  selectedModel.value = null
  selectedDataset.value = null
  selectedTemplate.value = null
  selectedInstance.value = null
  activeKey.value = 'category_' + category.id
  records.value = []
  templateDetails.value = []
  disposeChart()
}

async function loadTemplateDetails(templateId) {
  if (!templateId) { templateDetails.value = []; return }
  if (detailCache[templateId]) { templateDetails.value = detailCache[templateId]; return }
  const res = await axios.get(`/api/data/template/${templateId}/details`)
  detailCache[templateId] = asArray(res.data?.data)
  templateDetails.value = detailCache[templateId]
}

async function loadRecords(silent = false) {
  if (!selectedDataset.value) return
  if (!silent) loadingRecords.value = true
  try {
    const res = await axios.get(`/api/data/record/dataset/${selectedDataset.value.id}`, { params: { pageNo: recordPage.pageNo, pageSize: recordPage.pageSize } })
    const page = res.data?.data || {}
    records.value = asArray(page.records)
    recordPage.total = Number(page.total || 0)
    await nextTick()
    renderChart()
  } catch (err) {
    if (!silent) ElMessage.error(errorMessage(err, '加载数据记录失败'))
  } finally {
    if (!silent) loadingRecords.value = false
  }
}

function renderChart() {
  if (!chartRef.value || !selectedDataset.value) return
  if (!chart.value) chart.value = echarts.init(chartRef.value)
  const sorted = [...records.value].sort((a, b) => new Date(recordTime(a) || 0) - new Date(recordTime(b) || 0))
  const xData = sorted.map(row => formatTime(recordTime(row)))
  const units = [...new Set(valueFields.value.map(field => unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'))]
  const yAxis = units.map((unit, idx) => ({ 
    type: 'value', 
    name: unit, 
    position: idx % 2 ? 'right' : 'left', 
    offset: idx > 1 ? (idx - 1) * 42 : 0, 
    splitLine: { show: idx === 0, lineStyle: { type: 'dashed', color: '#cbd5e1' } },
    axisLabel: { color: '#64748b', fontSize: 11 },
    nameTextStyle: { color: '#64748b' }
  }))
  
  const colors = ['#1677ff', '#00b2a9', '#f43f5e', '#faad14', '#722ed1'];
  
  const series = valueFields.value.map((field, index) => {
    const unit = unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'
    const color = colors[index % colors.length]
    return { 
      name: field.columnDesc || field.columnName, 
      type: 'line', 
      smooth: 0.35, 
      symbol: 'circle',
      symbolSize: 5,
      showSymbol: false,
      itemStyle: { color: color, borderWidth: 2 },
      lineStyle: { width: 2.5 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: color + '33' },
          { offset: 1, color: color + '00' }
        ])
      },
      yAxisIndex: Math.max(0, units.indexOf(unit)), 
      data: sorted.map(row => numericOrNull(valueOf(row, field.columnName))) 
    }
  })
  
  chart.value.setOption({ 
    color: colors,
    tooltip: { 
      trigger: 'axis',
      backgroundColor: '#1e293b',
      borderColor: '#0f172a',
      padding: [10, 14],
      textStyle: { color: '#f8fafc', fontSize: 12 }
    }, 
    legend: { top: 4, itemWidth: 16, itemHeight: 8, icon: 'roundRect', textStyle: { color: '#475569', fontSize: 12 } }, 
    grid: { left: 48, right: Math.max(24, units.length * 42), top: 48, bottom: 32 }, 
    xAxis: { 
      type: 'category', 
      data: xData, 
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b', fontSize: 11, padding: [6, 0, 0, 0] }
    }, 
    yAxis: yAxis.length ? yAxis : [{ type: 'value' }], 
    series 
  }, true)
}

function openDatasetDrawer(template = null, instance = null) {
  if (template && !templateHasModel(template)) {
    ElMessage.error('该模板的来源模型不存在，已禁止继续建表')
    return
  }
  if (instance && !isUsableInstance(instance)) {
    ElMessage.warning('RETIRED device cannot create data table')
    return
  }
  datasetDrawer.visible = true
  datasetDrawer.templateId = template?.id || selectedDataset.value?.dataTemplateId || selectedTemplate.value?.id || null
  if (instance) datasetDrawer.deviceInstanceId = Number(instanceId(instance))
  else if (selectedDataset.value?.deviceInstanceId) datasetDrawer.deviceInstanceId = Number(selectedDataset.value.deviceInstanceId)
  else if (selectedInstance.value) datasetDrawer.deviceInstanceId = Number(instanceId(selectedInstance.value))
  else datasetDrawer.deviceInstanceId = null
  datasetDrawer.dataDesc = ''
  normalizeDatasetSelection()
}

function normalizeDatasetSelection() {
  const template = templates.value.find(row => Number(row.id) === Number(datasetDrawer.templateId))
  const instance = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  if (template && instance && String(template.deviceModelId) !== String(instance.deviceModelId || instance.modelId)) {
    datasetDrawer.deviceInstanceId = null
  }
}

function onDatasetTemplateChange() {
  const template = templates.value.find(row => Number(row.id) === Number(datasetDrawer.templateId))
  const instance = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  if (template && instance && String(template.deviceModelId) !== String(instance.deviceModelId || instance.modelId)) {
    datasetDrawer.deviceInstanceId = null
  }
}

function onDatasetInstanceChange() {
  const template = templates.value.find(row => Number(row.id) === Number(datasetDrawer.templateId))
  const instance = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  if (template && instance && String(template.deviceModelId) !== String(instance.deviceModelId || instance.modelId)) {
    datasetDrawer.templateId = null
  }
}
async function createDataset() {
  const selected = instances.value.find(ins => Number(instanceId(ins)) === Number(datasetDrawer.deviceInstanceId))
  const template = templates.value.find(row => Number(row.id) === Number(datasetDrawer.templateId))
  if (selected && !isUsableInstance(selected)) { ElMessage.warning('RETIRED device cannot create data table'); return }
  if (!template || !selected) { ElMessage.warning('请选择模板和设备实例'); return }
  if (!templateHasModel(template)) { ElMessage.error('模板来源模型不存在，不能建表'); return }
  if (String(template.deviceModelId) !== String(selected.deviceModelId || selected.modelId)) {
    ElMessage.error('模板与设备实例必须属于同一设备模型')
    return
  }
  saving.value = true
  try {
    const res = await axios.post('/api/data/index/create-dataset', { templateId: datasetDrawer.templateId, deviceInstanceId: datasetDrawer.deviceInstanceId, dataDesc: datasetDrawer.dataDesc })
    if (res.data?.success) {
      ElMessage.success('数据表已创建')
      datasetDrawer.visible = false
      await loadAll()
      if (res.data.data?.id) await selectDataset(res.data.data)
    } else ElMessage.error(res.data?.message || '创建失败')
  } catch (err) { ElMessage.error(errorMessage(err, '创建失败')) } finally { saving.value = false }
}

function openTemplateDrawer() {
  Object.assign(templateDrawer, { visible: true, templateName: '', templateDesc: '', deviceModelId: null, details: [] })
}

function openTemplateDrawerWithModel(model) {
  const modelIdVal = Number(modelId(model))
  Object.assign(templateDrawer, { visible: true, templateName: '', templateDesc: '', deviceModelId: modelIdVal, details: [] })
  if (modelIdVal) {
    generateFieldsFromModel()
  }
}

function openTemplateDrawerForCurrentInstance() {
  const instance = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  const modelIdVal = instance ? Number(instance.deviceModelId || instance.modelId) : (selectedTemplate.value?.deviceModelId || null)
  Object.assign(templateDrawer, { visible: true, templateName: '', templateDesc: '', deviceModelId: modelIdVal, details: [] })
  if (modelIdVal) {
    generateFieldsFromModel()
  }
}

function generateFieldsFromModel() {
  const model = modelMap.value[String(templateDrawer.deviceModelId)]
  const attrs = asArray(model?.attributes)
  templateDrawer.details = attrs.flatMap(attr => {
    const name = attr.attributeName || attr.displayName
    const desc = attr.displayName || attr.attributeName
    const rows = [{ _key: uid(), columnName: name, columnDesc: desc, propertyTypeId: propertyTypeId(attr.dataType), columnLength: 255, deviceAttrKey: name, defaultValue: '' }]
    if (attr.unit) rows.push({ _key: uid(), columnName: `${name}_unit`, columnDesc: `${desc}单位`, propertyTypeId: propertyTypeId('STRING'), columnLength: 50, deviceAttrKey: '', defaultValue: attr.unit })
    return rows
  })
}

function addTemplateField() { templateDrawer.details.push({ _key: uid(), columnName: '', columnDesc: '', propertyTypeId: propertyTypeId('STRING'), columnLength: 255, deviceAttrKey: '', defaultValue: '' }) }

function validateTemplateFields(details) {
  if (!Array.isArray(details) || details.length === 0) return '数据模板至少需要一个字段'
  const used = new Set()
  const reserved = new Set(['id', 'data_index_id', 'create_time', 'ingest_time'])
  for (const field of details) {
    const name = String(field.columnName || '').trim().toLowerCase()
    if (!/^[a-z_][a-z0-9_]*$/.test(name)) return `字段名“${field.columnName || ''}”不合法，只能使用字母、数字和下划线且不能以数字开头`
    if (reserved.has(name) || used.has(name)) return `字段“${name}”重复或占用系统字段`
    used.add(name)
    if (!String(field.deviceAttrKey || '').trim() && !String(field.defaultValue ?? '').trim()) {
      return `字段“${name}”未绑定模型属性，必须设置默认值`
    }
    if (!field.propertyTypeId && !String(field.deviceAttrKey || '').trim()) return `字段“${name}”缺少数据类型`
  }
  return ''
}
async function saveTemplate() {
  if (!templateDrawer.templateName.trim()) { ElMessage.warning('请输入模板名称'); return }
  if (!templateDrawer.deviceModelId) { ElMessage.warning('请选择绑定模型'); return }
  const fieldError = validateTemplateFields(templateDrawer.details)
  if (fieldError) { ElMessage.warning(fieldError); return }
  saving.value = true
  try {
    const payload = { templateName: templateDrawer.templateName, templateDesc: templateDrawer.templateDesc, deviceModelId: templateDrawer.deviceModelId, isDefault: false, details: templateDrawer.details.map(({ _key, ...row }) => ({ ...row, columnLength: row.columnLength || 255 })) }
    const res = await axios.post('/api/data/template/save', payload)
    if (res.data?.success) {
      ElMessage.success('模板已保存')
      templateDrawer.visible = false
      const newTemplateId = res.data.data?.id
      await loadAll()
      if (newTemplateId && datasetDrawer.visible) {
        datasetDrawer.templateId = Number(newTemplateId)
        onDatasetTemplateChange()
      }
    } else ElMessage.error(res.data?.message || '保存失败')
  } catch (err) { ElMessage.error(errorMessage(err, '保存失败')) } finally { saving.value = false }
}

async function deleteDataset(dataset) {
  const res = await axios.delete(`/api/data/index/delete/${dataset.id}`)
  if (res.data?.success) { ElMessage.success('数据表已删除'); selectedDataset.value = null; records.value = []; await loadAll() } else ElMessage.error(res.data?.message || '删除失败')
}

async function deleteTemplate(template) {
  const res = await axios.delete(`/api/data/template/delete/${template.id}`)
  if (res.data?.success) { ElMessage.success('模板已删除'); selectedTemplate.value = null; templateDetails.value = []; delete detailCache[template.id]; await loadAll() } else ElMessage.error(res.data?.message || '删除失败')
}

async function exportDataset() {
  if (!selectedDataset.value?.id) return
  try {
    const response = await axios.get(`/api/data/record/export/${selectedDataset.value.id}`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([response.data], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${selectedDataset.value.dataTable || 'dataset'}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (err) {
    ElMessage.error(errorMessage(err, '导出失败'))
  }
}
function selectFirstAvailable() { const firstDataset = datasets.value[0]; if (firstDataset) selectDataset(firstDataset); else if (templates.value[0]) selectTemplate(templates.value[0]) }
function fieldLabel(field) { const unit = unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey); return `${field.columnDesc || field.columnName}${unit ? ' (' + unit + ')' : ''}` }
function bindingLabel(row) { if (isUnitField(row)) return '-'; return row.deviceAttrKey || '-' }
function valueOf(row, key) { const value = row?.[key] ?? row?.[String(key).toLowerCase()] ?? row?.data?.[key] ?? row?.payload?.[key]; return value == null || value === '' ? '-' : value }
function numericOrNull(value) { const n = Number(value); return Number.isFinite(n) ? n : null }
function recordTime(row) { return row?.create_time || row?.createTime || row?.timestamp || row?.collectTime }
function recordIngestTime(row) { return row?.ingest_time || row?.ingestTime }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
function isUnitField(row) { return String(row?.columnName || '').endsWith('_unit') }
function stripUnit(value) { return String(value || '').replace(/_unit$/, '') }
function modelId(model) { return model?.modelId || model?.id }
function isUsableInstance(instance) { return instance?.lifecycleStatus === 'IN_USE' }

function instanceId(instance) { return instance?.instanceId || instance?.id }
function modelName(id) { return modelMap.value[String(id)]?.modelName || '模型缺失（一致性异常）' }
function templateHasModel(template) { return Boolean(template?.deviceModelId && modelMap.value[String(template.deviceModelId)]) }
function templateName(id) { return templateMap.value[String(id)]?.templateName || '-' }
function instanceName(id) { return instances.value.find(ins => String(instanceId(ins)) === String(id))?.instanceName || '-' }
function instancePath(ins) { return `${modelName(ins.deviceModelId)} / ${ins.instanceName}` }
function categoryName(id) { return categories.value.find(c => String(c.id) === String(id))?.categoryName || '未分类' }
function modelTemplates(model) { if (!model) return []; const mId = String(modelId(model)); return templates.value.filter(t => String(t.deviceModelId) === mId) }
function modelInstances(model) { if (!model) return []; const mId = String(modelId(model)); return instances.value.filter(ins => String(ins.deviceModelId || ins.modelId) === mId) }
function modelDatasets(model) { const mInstances = modelInstances(model); const insIds = new Set(mInstances.map(ins => String(instanceId(ins)))); return datasets.value.filter(ds => insIds.has(String(ds.deviceInstanceId))) }
function instanceDatasets(instance) { if (!instance) return []; const iId = String(instanceId(instance)); return datasets.value.filter(ds => String(ds.deviceInstanceId) === iId) }
function categoryModels(category) { if (!category) return []; const cId = String(category.id); return models.value.filter(m => String(m.categoryId) === cId) }
function categoryInstances(category) { const cModels = categoryModels(category); const mIds = new Set(cModels.map(m => String(modelId(m)))); return instances.value.filter(ins => mIds.has(String(ins.deviceModelId || ins.modelId))) }
function categoryDatasets(category) { const cInstances = categoryInstances(category); const iIds = new Set(cInstances.map(ins => String(instanceId(ins)))); return datasets.value.filter(ds => iIds.has(String(ds.deviceInstanceId))) }
function asArray(value) { return Array.isArray(value) ? value : [] }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function propertyTypeId(type) {
  const candidates = {
    DOUBLE: ['double precision', 'double', 'float8', 'numeric', 'decimal'],
    INTEGER: ['integer', 'int4', 'int', 'bigint'],
    BOOLEAN: ['boolean', 'bool'],
    JSON: ['jsonb', 'json'],
    STRING: ['varchar', 'character varying', 'text', 'string']
  }[String(type || 'STRING').toUpperCase()] || ['varchar', 'text']
  const match = propertyTypes.value.find(row => candidates.includes(String(row.dbType || '').trim().toLowerCase()))
  return match?.id || null
}
function uid() { return Math.random().toString(36).slice(2, 10) }
function disposeChart() { chart.value?.dispose(); chart.value = null }
function resizeChart() { chart.value?.resize() }

let refreshInterval = null
watch(templateDetails, () => nextTick(renderChart))
onMounted(() => { 
  loadAll(); 
  window.addEventListener('resize', resizeChart)
  refreshInterval = setInterval(() => {
    if (selectedDataset.value && !loadingRecords.value) {
      loadRecords(true) // 后台静默刷新
    }
  }, 1000)
})
onUnmounted(() => { 
  window.removeEventListener('resize', resizeChart); 
  disposeChart();
  if (refreshInterval) clearInterval(refreshInterval)
})
</script>

<style scoped>
.data-workbench {
  height: calc(100vh - 52px);
  display: flex;
  background: #f4f6f9;
  color: #1e293b;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* Asset Pane (Left Sidebar - 320px) */
.asset-pane {
  width: 320px;
  min-width: 320px;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  z-index: 10;
}
.asset-header {
  min-height: 50px;
  padding: 8px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.asset-header strong {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}
.asset-header span {
  font-size: 12px;
  color: #64748b;
  margin-left: 6px;
}
.tree-search-row {
  padding: 8px 12px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  width: 100%;
  box-sizing: border-box;
}
.tree-search-row :deep(.el-input) {
  width: 100%;
}
.tree-search-row :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #cbd5e1 inset;
  border-radius: 4px;
}
.tree-search-row :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #1677ff inset;
}
.asset-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 8px 12px;
}
.asset-node {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3px 6px;
  border-radius: 4px;
  position: relative;
  transition: background-color 0.15s ease;
}
.asset-node:hover {
  background: #f1f5f9;
}
.asset-node.active {
  background: #e6f4ff;
  border-left: 3px solid #1677ff;
}
.asset-node.active .node-label {
  color: #0958d9;
  font-weight: 700;
}
.asset-node-main {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  margin-right: 6px;
}
.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #1e293b;
}

/* Node Icons matching DeviceModelTree */
.node-icon {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  flex-shrink: 0;
}
.category-icon { color: #64748b; }
.model-icon { color: #2563eb; }
.instance-icon { color: #0284c7; }
.dataset-icon { color: #00b2a9; }
.template-icon { color: #7c3aed; }

/* Side Count & Action Buttons */
.asset-node-side {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.count-tag {
  font-size: 11px;
  font-style: normal;
  padding: 1px 6px;
  border-radius: 10px;
  background: #e2e8f0;
  color: #475569;
}

/* Node Action Buttons - Always Visible */
.node-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
/* Alibaba Cloud White Background Button Standard */
.btn-aliyun {
  background: #ffffff !important;
  border: 1px solid #d9d9d9 !important;
  color: rgba(0, 0, 0, 0.88) !important;
  font-weight: 400 !important;
  transition: all 0.15s ease;
}
.btn-aliyun:hover:not(:disabled):not(.is-disabled) {
  background: #ffffff !important;
  border-color: #4096ff !important;
  color: #1677ff !important;
}

.btn-aliyun-cta {
  background: #ffffff !important;
  border: 1px solid #1677ff !important;
  color: #1677ff !important;
  font-weight: 500 !important;
  transition: all 0.15s ease;
}
.btn-aliyun-cta:hover:not(:disabled):not(.is-disabled) {
  background: #1677ff !important;
  border-color: #1677ff !important;
  color: #ffffff !important;
}

.btn-aliyun:disabled,
.btn-aliyun.is-disabled,
.btn-aliyun-cta:disabled,
.btn-aliyun-cta.is-disabled {
  background: #f5f5f5 !important;
  border-color: #d9d9d9 !important;
  color: rgba(0, 0, 0, 0.25) !important;
  cursor: not-allowed !important;
}

.btn-aliyun-link {
  background: transparent !important;
  border: none !important;
  color: #1677ff !important;
  padding: 0 4px !important;
  font-weight: 400 !important;
}
.btn-aliyun-link:hover {
  color: #4096ff !important;
  text-decoration: underline !important;
  background: transparent !important;
}

.btn-aliyun-danger-link {
  background: transparent !important;
  border: none !important;
  color: #ff4d4f !important;
  padding: 0 4px !important;
  font-weight: 400 !important;
}
.btn-aliyun-danger-link:hover {
  color: #ff7875 !important;
  text-decoration: underline !important;
  background: transparent !important;
}

.node-action {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #ffffff;
  color: #595959;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  transition: all 0.15s ease;
}
.node-action:hover {
  background: #ffffff;
  border-color: #1677ff;
  color: #1677ff;
}
.node-action.model-action {
  color: #047857;
  border-color: #a7f3d0;
  background: #ffffff;
}
.node-action.model-action:hover {
  background: #ffffff;
  border-color: #059669;
  color: #059669;
}
.template-select-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.template-select-row .el-select {
  flex: 1;
}

/* Workspace Area */
.data-workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #f4f6f9;
}

/* Workspace Area */
.data-workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #f4f6f9;
}

/* Metric Ribbon - Edge-to-edge Flat Splitter Ribbon (No Cards, No Margins) */
.metric-ribbon {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
}
.metric-cell {
  padding: 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  border-right: 1px solid #f1f5f9;
}
.metric-cell:last-child {
  border-right: none;
}
.metric-cell .label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}
.metric-cell .val {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.metric-cell .val.highlight {
  color: #1677ff;
}
.metric-cell .val.active-status {
  color: #059669;
}

/* Workspace Body (Zero Gap Edge-to-Edge) */
.workspace-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0;
}

/* ECharts Chart Container */
.chart-box-wrapper {
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  padding: 12px 16px;
}
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.chart-header strong {
  font-size: 14px;
  color: #1e293b;
}
.chart-live-tag {
  font-size: 12px;
  color: #10b981;
  display: flex;
  align-items: center;
  gap: 5px;
}
.chart-live-tag i.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
}
.chart-box {
  height: 340px;
  width: 100%;
}

/* Data Table Section */
.table-section {
  background: #ffffff;
  padding: 12px 16px;
}
.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.table-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.table-header strong {
  font-size: 14px;
  color: #0f172a;
}
.count-pill {
  font-size: 12px;
  color: #475569;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 12px;
}
.record-table, .template-table {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
}
:deep(.el-table) {
  --el-table-border-color: #e2e8f0;
  --el-table-header-bg-color: #f8fafc;
  --el-table-header-text-color: #334155;
  --el-table-row-hover-bg-color: #f1f5f9;
}
:deep(.el-table th.el-table__cell) {
  font-weight: 700;
  font-size: 12px;
  padding: 8px 0;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
:deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: #1677ff;
}

.workspace-empty {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
}

.drawer-form { padding: 0 16px; }
.form-grid.two { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.field-editor { border: 1px solid #cbd5e1; border-radius: 4px; overflow: hidden; }
.field-head, .field-row { display: grid; grid-template-columns: minmax(130px, 1fr) minmax(130px, 1fr) minmax(130px, 1fr) minmax(110px, 0.8fr) 60px; align-items: center; gap: 8px; padding: 8px 10px; border-bottom: 1px solid #e2e8f0; }
.field-head { background: #f8fafc; font-weight: 700; color: #475569; font-size: 12px; }
.field-row:last-child { border-bottom: 0; }

@media (max-width: 1180px) {
  .metric-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
