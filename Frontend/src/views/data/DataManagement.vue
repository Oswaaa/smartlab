<template>
  <div class="data-center-page">
    <aside class="left-panel">
      <div class="left-header">
        <div>
          <strong>数据资产</strong>
        </div>
        <el-button type="primary" size="small" @click="openCreateDrawer">新建表</el-button>
      </div>

      <div class="list-body" v-loading="loadingTree">
        <div class="tree-section-title">设备数据</div>
        <el-tree
          v-if="treeData.length"
          :data="treeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
        >
          <template #default="{ node, data }">
            <span
              class="custom-tree-node"
              :class="{
                'is-instance': data.type === 'instance',
                'is-dataset': data.type === 'dataset',
                'is-active': selectedDataset && data.type === 'dataset' && String(selectedDataset.id) === String(data.data?.dataset?.id)
              }"
            >
              <el-icon v-if="data.type === 'category'"><Folder /></el-icon>
              <el-icon v-else-if="data.type === 'instance'"><Cpu /></el-icon>
              <el-icon v-else><Box /></el-icon>
              <span class="node-label">{{ node.label }}</span>
              <span v-if="data.type === 'instance'" class="node-count">{{ data.children?.length || 0 }}</span>
            </span>
          </template>
        </el-tree>
        <div v-else class="tree-empty">暂无设备数据集</div>

        <div class="tree-section-title template-title">模板库</div>
        <div v-if="templates.length" class="template-list">
          <button
            v-for="tpl in templates"
            :key="templateIdOf(tpl)"
            class="template-row"
            :class="{ active: selectedTemplate && String(templateIdOf(selectedTemplate)) === String(templateIdOf(tpl)) }"
            type="button"
            @click="selectTemplate(tpl)"
          >
            <span>{{ tpl.templateName || '未命名模板' }}</span>
            <em>{{ tpl.isDefault ? '默认' : '自定义' }}</em>
          </button>
        </div>
        <div v-else class="tree-empty">暂无数据模板</div>
      </div>
    </aside>

    <section class="right-panel">
      <div v-if="selectedDataset" class="dataset-workspace">
        <section class="dataset-meta-card">
          <div class="dataset-title">
            <span>数据集</span>
            <h2>{{ selectedDataset.dataDesc || selectedDataset.dataTable || '未命名数据集' }}</h2>
            <p>{{ selectedDataset.dataTable || '-' }}</p>
          </div>
          <div class="dataset-meta-grid">
            <div><span>绑定设备</span><strong>{{ instanceNameById(selectedDataset.deviceInstanceId) }}</strong></div>
            <div><span>数据模板</span><strong>{{ templateNameById(selectedDataset.dataTemplateId) }}</strong></div>
          </div>
          <div class="dataset-actions">
            <el-button size="small" @click="openCreateDrawer">新建数据表</el-button>
            <el-button size="small" @click="exportCsv">导出 CSV</el-button>
            <el-button size="small" :loading="loadingRecords" @click="fetchRecords">刷新</el-button>
            <el-popconfirm title="确认删除该数据表？物理表会同时删除。" @confirm="deleteDataset(selectedDataset)">
              <template #reference>
                <el-button size="small" type="danger" plain>删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </section>

        <section class="content-card chart-card">
          <div class="card-header">
            <span>趋势曲线</span>
            <em>{{ selectedSchemaRows.length }} 个字段</em>
          </div>
          <div ref="chartRef" class="chart-box"></div>
        </section>

        <section class="content-card records-card">
          <div class="card-header">
            <span>原始数据</span>
            <div class="header-actions">
              <el-button size="small" @click="exportCsv">导出 CSV</el-button>
            </div>
          </div>
          <el-table :data="selectedRecords" border stripe size="small" class="records-table" v-loading="loadingRecords">
            <el-table-column label="采集时间" min-width="170">
              <template #default="{ row }">{{ formatTime(recordTime(row)) }}</template>
            </el-table-column>
            <el-table-column
              v-for="field in selectedSchemaRows"
              :key="field.key"
              :label="field.label + (field.unit ? ' (' + field.unit + ')' : '')"
              min-width="130"
            >
              <template #default="{ row }">{{ renderPayloadValue(recordValue(row, field.key)) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="records-pagination"
            layout="total, sizes, prev, pager, next"
            :total="recordPage.total"
            :current-page="recordPage.pageNo"
            :page-size="recordPage.pageSize"
            :page-sizes="[50, 100, 200, 500]"
            small
            background
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </section>
      </div>

      <div v-else-if="selectedTemplate" class="template-workspace">
        <section class="dataset-meta-card">
          <div class="dataset-title">
            <span>数据模板</span>
            <h2>{{ selectedTemplate.templateName || '未命名模板' }}</h2>
            <p>{{ selectedTemplate.templateDesc || selectedTemplate.templateName || '-' }}</p>
          </div>
          <div class="dataset-meta-grid">
            <div><span>来源模型</span><strong>{{ modelNameById(selectedTemplate.deviceModelId) }}</strong></div>
            <div><span>类型</span><strong>{{ selectedTemplate.isDefault ? '默认模板' : '自定义模板' }}</strong></div>
          </div>
          <div class="dataset-actions">
            <el-button size="small" type="primary" @click="openCreateDrawerWithTemplate(selectedTemplate)">用此模板建表</el-button>
            <el-popconfirm title="确认删除该模板？已有数据表时不可删除。" @confirm="deleteTemplate(selectedTemplate)">
              <template #reference>
                <el-button size="small" type="danger" plain>删除模板</el-button>
              </template>
            </el-popconfirm>
          </div>
        </section>

        <section class="content-card records-card">
          <div class="card-header"><span>模板字段</span><em>{{ selectedTemplateDetails.length }} 个字段</em></div>
          <div class="template-field-list">
            <div v-for="row in selectedTemplateDetails" :key="row.id || row.columnName" class="template-field-card">
              <div>
                <strong>{{ row.columnDesc || '未命名字段' }}</strong>
                <span>{{ isUnitTemplateField(row) ? '单位字段' : '采集字段' }}</span>
              </div>
              <em v-if="row.defaultValue">默认 {{ row.defaultValue }}</em>
            </div>
          </div>
        </section>
      </div>

      <div v-else-if="selectedInstance" class="top-area empty">
        <el-empty description="该设备下暂无数据表">
          <el-button type="primary" @click="openCreateDrawer">新建自定义数据表</el-button>
        </el-empty>
      </div>
      <div v-else class="top-area empty">
        <el-empty description="请选择左侧数据集或模板" />
      </div>
    </section>

    <el-drawer v-model="createDrawerVisible" title="新建自定义数据表" size="480px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="96px" size="small" label-position="top" class="create-form">
        <el-form-item label="数据表名称" prop="dataDesc">
          <el-input v-model="createForm.dataDesc" placeholder="例如：高压报警专项数据" />
        </el-form-item>
        <el-form-item label="关联数据模板" prop="templateId">
          <el-select v-model="createForm.templateId" style="width: 100%" filterable>
            <el-option v-for="tpl in templates" :key="templateIdOf(tpl)" :label="tpl.templateName" :value="templateIdOf(tpl)" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定设备实例" prop="deviceInstanceId">
          <el-select v-model="createForm.deviceInstanceId" style="width: 100%" filterable>
            <el-option v-for="ins in instances" :key="instanceIdOf(ins)" :label="ins.instanceName" :value="instanceIdOf(ins)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDataset" @click="submitCreateDataset">保存建表</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import axios from 'axios'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Folder, Cpu, Box } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

interface TreeNode {
  id: string
  label: string
  type: 'category' | 'instance' | 'dataset'
  data?: any
  children?: TreeNode[]
}

const treeData = ref<TreeNode[]>([])
const loadingTree = ref(false)
const selectedInstance = ref<any>(null)
const selectedDataset = ref<any>(null)
const selectedTemplate = ref<any>(null)
const selectedRecords = ref<any[]>([])
const selectedTemplateDetails = ref<any[]>([])
const templateDetailsCache = ref<Record<string, any[]>>({})
const recordPage = ref({ pageNo: 1, pageSize: 100, total: 0 })
const loadingRecords = ref(false)

const chartRef = ref<HTMLElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)

const createDrawerVisible = ref(false)
const createFormRef = ref<FormInstance>()
const templates = ref<any[]>([])
const createForm = ref({ templateId: '', deviceInstanceId: '', dataDesc: '' })
const createRules: FormRules = {
  templateId: [{ required: true, message: '请选择数据模板', trigger: 'change' }],
  deviceInstanceId: [{ required: true, message: '请选择关联设备', trigger: 'change' }],
  dataDesc: [{ required: true, message: '请输入数据集名称', trigger: 'blur' }]
}
const savingDataset = ref(false)

const categories = ref<any[]>([])
const instances = ref<any[]>([])
const datasets = ref<any[]>([])
const modelMap = ref<Record<string, any>>({})
const templateMap = ref<Record<string, any>>({})

const selectedSchemaRows = computed(() => {
  const unitMap = new Map<string, string>()
  selectedTemplateDetails.value.filter(isUnitTemplateField).forEach((detail: any) => {
    const baseColumn = stripUnitSuffix(detail.columnName)
    const baseAttr = stripUnitSuffix(detail.deviceAttrKey)
    const value = String(detail.defaultValue || '').trim()
    if (baseColumn && value) unitMap.set(baseColumn, value)
    if (baseAttr && value) unitMap.set(baseAttr, value)
  })
  return selectedTemplateDetails.value
    .filter((detail: any) => !isUnitTemplateField(detail))
    .map((detail: any) => ({
      key: detail.columnName,
      label: detail.columnDesc || detail.columnName,
      type: detail.dataType || 'string',
      unit: unitMap.get(detail.columnName) || unitMap.get(detail.deviceAttrKey) || ''
    }))
    .filter(row => row.key)
})

const fetchTreeData = async () => {
  loadingTree.value = true
  try {
    const [catRes, insRes, dsRes, modRes, tplRes] = await Promise.all([
      axios.get('/api/device/category/list').catch(() => ({ data: { data: [] } })),
      axios.get('/api/device/instance/list'),
      axios.get('/api/data/index/list'),
      axios.get('/api/device/model/list'),
      axios.get('/api/data/template/list')
    ])
    categories.value = asArray(catRes.data?.data)
    instances.value = asArray(insRes.data?.data)
    datasets.value = asArray(dsRes.data?.data)
    templates.value = asArray(tplRes.data?.data)

    const mMap: Record<string, any> = {}
    asArray(modRes.data?.data).forEach((model: any) => { mMap[String(modelIdOf(model))] = model })
    modelMap.value = mMap

    const tMap: Record<string, any> = {}
    templates.value.forEach((template: any) => { tMap[String(templateIdOf(template))] = template })
    templateMap.value = tMap

    treeData.value = buildDeviceDatasetTree()
    await restoreSelectionAfterReload()
  } catch (err: any) {
    console.error(err)
    ElMessage.error(err.response?.data?.message || '加载数据中心失败')
  } finally {
    loadingTree.value = false
  }
}

function buildDeviceDatasetTree() {
  const categoryMap = new Map<string, TreeNode>()
  categories.value.forEach((category: any) => {
    const id = String(category.id)
    categoryMap.set(id, { id: 'cat_' + id, label: category.categoryName || '未命名类别', type: 'category', children: [] })
  })
  const uncategorizedKey = '__uncategorized__'
  categoryMap.set(uncategorizedKey, { id: 'cat_none', label: '未分类设备', type: 'category', children: [] })

  const datasetsByInstance: Record<string, any[]> = {}
  datasets.value.forEach(dataset => {
    const insId = String(dataset.deviceInstanceId || '')
    if (!insId) return
    if (!datasetsByInstance[insId]) datasetsByInstance[insId] = []
    datasetsByInstance[insId].push(dataset)
  })

  instances.value.forEach(instance => {
    const insId = String(instanceIdOf(instance))
    const model = modelMap.value[String(instance.deviceModelId || instance.modelId)]
    const categoryId = model?.categoryId == null ? uncategorizedKey : String(model.categoryId)
    if (!categoryMap.has(categoryId)) {
      categoryMap.set(categoryId, { id: 'cat_' + categoryId, label: '类别 ' + categoryId, type: 'category', children: [] })
    }
    const dataChildren = asArray(datasetsByInstance[insId]).map(dataset => ({
      id: 'ds_' + datasetIdOf(dataset),
      label: dataset.dataDesc || dataset.dataTable || '未命名数据集',
      type: 'dataset' as const,
      data: { dataset, instance }
    }))
    categoryMap.get(categoryId)!.children!.push({
      id: 'ins_' + insId,
      label: instance.instanceName || '未命名设备',
      type: 'instance',
      data: instance,
      children: dataChildren
    })
  })

  return Array.from(categoryMap.values())
    .map(node => ({ ...node, children: asArray(node.children).sort((a, b) => String(a.label).localeCompare(String(b.label), 'zh-CN')) }))
    .filter(node => asArray(node.children).length > 0)
}

async function restoreSelectionAfterReload() {
  const currentDatasetId = selectedDataset.value ? String(datasetIdOf(selectedDataset.value)) : ''
  if (currentDatasetId) {
    const found = findDatasetWithInstance(currentDatasetId)
    if (found) {
      await selectDataset(found.dataset, found.instance, false)
      return
    }
  }
  const first = findFirstDataset()
  if (first) {
    await selectDataset(first.dataset, first.instance, false)
    return
  }
  if (instances.value.length > 0) {
    selectedInstance.value = instances.value[0]
    selectedDataset.value = null
    selectedTemplate.value = null
    selectedRecords.value = []
    selectedTemplateDetails.value = []
    chart.value?.dispose()
    chart.value = null
    return
  }
  if (templates.value.length > 0) {
    await selectTemplate(templates.value[0])
  }
}

function findFirstDataset() {
  for (const category of treeData.value) {
    for (const instanceNode of asArray(category.children)) {
      const datasetNode = asArray(instanceNode.children)[0]
      if (datasetNode?.data?.dataset) return datasetNode.data
    }
  }
  return null
}

function findDatasetWithInstance(datasetId: string) {
  for (const category of treeData.value) {
    for (const instanceNode of asArray(category.children)) {
      for (const datasetNode of asArray(instanceNode.children)) {
        if (String(datasetIdOf(datasetNode.data?.dataset)) === String(datasetId)) return datasetNode.data
      }
    }
  }
  return null
}

const handleNodeClick = async (node: TreeNode) => {
  if (node.type === 'dataset' && node.data?.dataset) {
    await selectDataset(node.data.dataset, node.data.instance)
    return
  }
  if (node.type === 'instance' && node.data) {
    selectedInstance.value = node.data
    selectedDataset.value = null
    selectedTemplate.value = null
    selectedRecords.value = []
    selectedTemplateDetails.value = []
    chart.value?.dispose()
    chart.value = null
  }
}

async function selectDataset(dataset: any, instance?: any, resetPage = true) {
  selectedDataset.value = dataset
  selectedTemplate.value = null
  selectedInstance.value = instance || instances.value.find(item => String(instanceIdOf(item)) === String(dataset.deviceInstanceId)) || null
  if (resetPage) recordPage.value.pageNo = 1
  await loadTemplateDetails(dataset.dataTemplateId)
  await fetchRecords()
}

async function selectTemplate(template: any) {
  selectedTemplate.value = template
  selectedDataset.value = null
  selectedInstance.value = null
  selectedRecords.value = []
  chart.value?.dispose()
  chart.value = null
  await loadTemplateDetails(templateIdOf(template))
}

async function loadTemplateDetails(templateId: any) {
  const key = String(templateId || '')
  if (!key) {
    selectedTemplateDetails.value = []
    return
  }
  if (templateDetailsCache.value[key]) {
    selectedTemplateDetails.value = templateDetailsCache.value[key]
    return
  }
  const res = await axios.get('/api/data/template/' + key + '/details')
  const details = asArray(res.data?.data)
  templateDetailsCache.value[key] = details
  selectedTemplateDetails.value = details
}

const fetchRecords = async () => {
  if (!selectedDataset.value) return
  loadingRecords.value = true
  try {
    const res = await axios.get('/api/data/record/dataset/' + datasetIdOf(selectedDataset.value), {
      params: { pageNo: recordPage.value.pageNo, pageSize: recordPage.value.pageSize }
    })
    if (res.data?.success) {
      const page = res.data.data || {}
      selectedRecords.value = asArray(page.records)
      recordPage.value.total = Number(page.total || 0)
      nextTick(() => {
        if (!chart.value && chartRef.value) chart.value = echarts.init(chartRef.value)
        renderChart()
      })
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载记录失败')
  } finally {
    loadingRecords.value = false
  }
}

const onPageChange = (pageNo: number) => {
  recordPage.value.pageNo = pageNo
  fetchRecords()
}

const onSizeChange = (pageSize: number) => {
  recordPage.value.pageNo = 1
  recordPage.value.pageSize = pageSize
  fetchRecords()
}

const renderChart = () => {
  if (!chart.value || !selectedDataset.value) return
  const schemaRows = selectedSchemaRows.value
  const recs = [...selectedRecords.value].sort((a, b) => new Date(recordTime(a) || 0).getTime() - new Date(recordTime(b) || 0).getTime())
  const timeList = recs.map(row => formatTime(recordTime(row)))
  const unitAxisMap = new Map<string, number>()
  const yAxis: any[] = []
  const series: any[] = []
  const getAxisIdx = (unit: string) => {
    const u = unit || '数值'
    if (unitAxisMap.has(u)) return unitAxisMap.get(u) as number
    const idx = yAxis.length
    unitAxisMap.set(u, idx)
    yAxis.push({
      type: 'value',
      name: u,
      position: idx % 2 === 0 ? 'left' : 'right',
      offset: idx > 1 ? Math.floor((idx - 1) / 2) * 45 : 0,
      axisLine: { show: true },
      splitLine: { show: idx === 0, lineStyle: { color: '#e5e7eb' } }
    })
    return idx
  }
  schemaRows.forEach(row => {
    const axisIdx = getAxisIdx(row.unit)
    const data = recs.map(record => {
      const num = Number(recordValue(record, row.key))
      return Number.isFinite(num) ? num : null
    })
    series.push({ type: 'line', smooth: true, connectNulls: false, yAxisIndex: axisIdx, name: row.label, data })
  })
  chart.value.setOption({
    backgroundColor: '#ffffff',
    tooltip: { trigger: 'axis' },
    legend: { top: 8 },
    grid: { left: 56, right: Math.max(16, (yAxis.length - 1) * 45 + 16), top: 54, bottom: 40 },
    xAxis: { type: 'category', data: timeList },
    yAxis: yAxis.length ? yAxis : [{ type: 'value' }],
    series
  }, true)
}

const onResize = () => chart.value?.resize()
const asArray = (value: any) => Array.isArray(value) ? value : []
const modelIdOf = (model: any) => model?.modelId || model?.id || ''
const instanceIdOf = (instance: any) => instance?.instanceId || instance?.id || ''
const datasetIdOf = (dataset: any) => dataset?.id || dataset?.dataIndexId || ''
const templateIdOf = (template: any) => template?.templateId || template?.id || ''
const formatTime = (v?: string) => (v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '-')
const recordTime = (row: any) => row?.create_time || row?.createTime || row?.collectTime || row?.timestamp
const recordValue = (row: any, key: string) => row?.[key] ?? row?.[String(key).toLowerCase()] ?? row?.dataPayload?.[key]
const renderPayloadValue = (v: any) => (v === undefined || v === null || v === '' ? '-' : String(v))
const isUnitTemplateField = (detail: any) => String(detail?.columnName || '').endsWith('_unit') || String(detail?.deviceAttrKey || '').endsWith('_unit')
const stripUnitSuffix = (value: any) => String(value || '').endsWith('_unit') ? String(value).slice(0, -5) : String(value || '')
const instanceNameById = (id: any) => instances.value.find(v => String(instanceIdOf(v)) === String(id))?.instanceName || String(id || '-')
const templateNameById = (id: any) => templateMap.value[String(id)]?.templateName || String(id || '-')
const modelNameById = (id: any) => modelMap.value[String(id)]?.modelName || String(id || '-')

const openCreateDrawer = () => {
  createForm.value = {
    templateId: selectedTemplate.value ? String(templateIdOf(selectedTemplate.value)) : '',
    deviceInstanceId: selectedInstance.value ? String(instanceIdOf(selectedInstance.value)) : '',
    dataDesc: ''
  }
  createDrawerVisible.value = true
}

const openCreateDrawerWithTemplate = (template: any) => {
  createForm.value = { templateId: String(templateIdOf(template)), deviceInstanceId: '', dataDesc: '' }
  createDrawerVisible.value = true
}

const submitCreateDataset = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async valid => {
    if (!valid) return
    savingDataset.value = true
    try {
      const res = await axios.post('/api/data/index/create-dataset', createForm.value)
      if (res.data?.success) {
        ElMessage.success('自定义数据表创建成功')
        createDrawerVisible.value = false
        const created = res.data.data
        await fetchTreeData()
        if (created?.id) {
          const found = findDatasetWithInstance(String(created.id))
          if (found) await selectDataset(found.dataset, found.instance)
        }
      } else {
        ElMessage.error(res.data?.message || '创建失败')
      }
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '创建失败')
    } finally {
      savingDataset.value = false
    }
  })
}

const exportCsv = () => {
  if (!selectedDataset.value) return
  window.open('/api/data/record/export/' + datasetIdOf(selectedDataset.value), '_blank')
}

const deleteDataset = async (dataset: any) => {
  if (!dataset) return
  const id = datasetIdOf(dataset)
  if (!id) return
  try {
    const res = await axios.delete('/api/data/index/delete/' + id)
    if (res.data?.success) {
      ElMessage.success('数据表已删除')
      selectedDataset.value = null
      selectedRecords.value = []
      selectedTemplateDetails.value = []
      chart.value?.dispose()
      chart.value = null
      await fetchTreeData()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

const deleteTemplate = async (template: any) => {
  if (!template) return
  const id = templateIdOf(template)
  if (!id) return
  try {
    const res = await axios.delete('/api/data/template/delete/' + id)
    if (res.data?.success) {
      ElMessage.success('模板已删除')
      delete templateDetailsCache.value[String(id)]
      selectedTemplate.value = null
      selectedTemplateDetails.value = []
      await fetchTreeData()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

watch(selectedSchemaRows, () => nextTick(renderChart))

onMounted(() => {
  fetchTreeData()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart.value?.dispose()
})
</script>

<style scoped>
.data-center-page { display: flex; gap: 0; height: calc(100vh - 52px); background: #eef2f6; color: #1f2937; }
.left-panel { width: 320px; min-width: 320px; border-right: 1px solid #ccd5e2; background: #fff; display: flex; flex-direction: column; }
.left-header { min-height: 52px; padding: 8px 12px; border-bottom: 1px solid #dbe3ee; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.left-header div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.left-header strong { font-size: 15px; color: #0f172a; }
.left-header span { color: #64748b; font-size: 12px; }
.list-body { flex: 1; min-height: 0; overflow: auto; padding: 8px; background: #f8fafc; }
.tree-section-title { margin: 4px 2px 6px; color: #334155; font-size: 12px; font-weight: 700; }
.template-title { margin-top: 14px; padding-top: 10px; border-top: 1px solid #e2e8f0; }
.custom-tree-node { min-width: 0; width: 100%; display: flex; align-items: center; gap: 6px; font-size: 13px; color: #334155; }
.custom-tree-node.is-instance { color: #1d4ed8; }
.custom-tree-node.is-dataset { color: #047857; }
.custom-tree-node.is-active { font-weight: 700; color: #0f766e; }
.node-label { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-count { margin-left: auto; min-width: 20px; height: 18px; display: inline-flex; align-items: center; justify-content: center; border-radius: 10px; background: #e2e8f0; color: #475569; font-size: 11px; }
.tree-empty { padding: 10px; border: 1px dashed #cbd5e1; border-radius: 4px; color: #64748b; font-size: 12px; background: #fff; }
.template-list { display: grid; gap: 6px; }
.template-row { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 9px; border: 1px solid #dbe2ea; border-radius: 4px; background: #fff; color: #334155; cursor: pointer; text-align: left; }
.template-row:hover, .template-row.active { border-color: #93c5fd; background: #eff6ff; }
.template-row span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.template-row em { flex: 0 0 auto; color: #64748b; font-size: 11px; font-style: normal; }
.right-panel { flex: 1; min-width: 0; overflow: auto; padding: 10px; }
.dataset-workspace, .template-workspace { display: grid; gap: 10px; }
.dataset-meta-card, .content-card { border: 1px solid #ccd6e3; border-radius: 6px; background: #fff; box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04); }
.dataset-meta-card { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(280px, 0.9fr) auto; align-items: center; gap: 12px; padding: 12px 14px; }
.dataset-title { min-width: 0; }
.dataset-title span { color: #1d4ed8; font-size: 12px; font-weight: 700; }
.dataset-title h2 { margin: 2px 0 3px; color: #0f172a; font-size: 20px; line-height: 1.25; }
.dataset-title p { margin: 0; color: #64748b; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dataset-meta-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.dataset-meta-grid div { min-width: 0; padding: 8px 9px; border-left: 3px solid #3b82f6; background: #f7f9fc; }
.dataset-meta-grid span { display: block; color: #64748b; font-size: 11px; }
.dataset-meta-grid strong { display: block; margin-top: 3px; color: #0f172a; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dataset-actions { display: flex; align-items: center; justify-content: flex-end; gap: 6px; flex-wrap: wrap; }
.content-card { padding: 10px; }
.card-header { min-height: 30px; display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.card-header span { color: #0f172a; font-size: 15px; font-weight: 700; }
.card-header em { color: #64748b; font-size: 12px; font-style: normal; }
.header-actions { display: flex; align-items: center; gap: 6px; }
.chart-box { height: 300px; border: 1px solid #e2e8f0; border-radius: 4px; }
.records-table { width: 100%; }
.records-pagination { margin-top: 10px; display: flex; justify-content: flex-end; }
.template-field-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; }
.template-field-card { min-height: 58px; padding: 10px 12px; border: 1px solid #dbe4ef; border-left: 3px solid #0ea5e9; border-radius: 5px; background: #f8fafc; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.template-field-card strong { display: block; color: #0f172a; font-size: 14px; }
.template-field-card span { display: block; margin-top: 3px; color: #64748b; font-size: 12px; }
.template-field-card em { color: #0f766e; font-style: normal; font-size: 12px; background: #e7f8f1; padding: 3px 6px; border-radius: 4px; }
.top-area.empty { height: 100%; min-height: 420px; display: flex; align-items: center; justify-content: center; border: 1px solid #dbe2ea; border-radius: 4px; background: #fff; }
.create-form { padding: 0 20px; }
@media (max-width: 1180px) { .dataset-meta-card { grid-template-columns: 1fr; align-items: stretch; } .dataset-actions { justify-content: flex-start; } }
@media (max-width: 820px) { .data-center-page { flex-direction: column; } .left-panel { width: 100%; min-width: 0; max-height: 320px; border-right: 0; border-bottom: 1px solid #d8dee8; } .dataset-meta-grid { grid-template-columns: 1fr; } }
</style>
