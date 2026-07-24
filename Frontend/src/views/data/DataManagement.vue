<template>
  <div class="data-workbench">
    <aside class="asset-pane">
      <div class="asset-header">
        <div>
          <strong>数据中心</strong>
          <span>设备数据 / 模板库</span>
        </div>
        <el-button size="small" type="primary" @click="openTemplateDrawer">新增模板</el-button>
      </div>
      <el-input v-model="keyword" size="small" clearable placeholder="搜索设备、模型、数据表" class="asset-search" />
      <div class="asset-tree" v-loading="loading">
        <el-tree :data="filteredTree" node-key="key" default-expand-all :expand-on-click-node="false" @node-click="handleTreeClick">
          <template #default="{ data }">
            <div class="asset-node" :class="{ active: activeKey === data.key, dataset: data.type === 'dataset', template: data.type === 'template' }">
              <span class="asset-node-main">
                <el-icon v-if="['root','category'].includes(data.type)"><Folder /></el-icon>
                <el-icon v-else-if="['model','instance'].includes(data.type)"><Cpu /></el-icon>
                <el-icon v-else><Tickets /></el-icon>
                <span>{{ data.label }}</span>
              </span>
              <em v-if="data.count != null">{{ data.count }}</em>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!filteredTree.length" description="暂无数据资产" />
      </div>
    </aside>

    <section class="data-workspace">
      <div class="workspace-toolbar">
        <div>
          <span>{{ workspaceTag }}</span>
          <h1>{{ workspaceTitle }}</h1>
        </div>
        <div class="toolbar-actions">
          <el-button @click="loadAll">刷新</el-button>
          <el-button v-if="selectedTemplate" type="primary" @click="openDatasetDrawer(selectedTemplate)">用模板建表</el-button>
          <el-button v-if="selectedInstance && isUsableInstance(selectedInstance)" type="primary" @click="openDatasetDrawer(null, selectedInstance)">为该设备建表</el-button>
          <el-button v-if="selectedDataset" @click="exportDataset">导出 CSV</el-button>
          <el-popconfirm v-if="selectedDataset" title="确认删除该数据表？物理表会同步删除" @confirm="deleteDataset(selectedDataset)">
            <template #reference><el-button type="danger" plain>删除数据表</el-button></template>
          </el-popconfirm>
          <el-popconfirm v-if="selectedTemplate && !selectedTemplate.isDefault" title="确认删除该模板？已有数据表时后端会阻止删除" @confirm="deleteTemplate(selectedTemplate)">
            <template #reference><el-button type="danger" plain>删除模板</el-button></template>
          </el-popconfirm>
        </div>
      </div>

      <main class="workspace-body">
        <section v-if="selectedDataset" class="workspace-section dataset-section">
          <div class="meta-table">
            <div><span>数据表</span><strong>{{ selectedDataset.dataTable || '-' }}</strong></div>
            <div><span>绑定设备</span><strong>{{ instanceName(selectedDataset.deviceInstanceId) }}</strong></div>
            <div><span>数据模板</span><strong>{{ templateName(selectedDataset.dataTemplateId) }}</strong></div>
            <div><span>创建时间</span><strong>{{ formatTime(selectedDataset.createTime) }}</strong></div>
          </div>
          <div ref="chartRef" class="chart-box"></div>
          <div class="table-title"><strong>历史数据</strong><span>{{ recordPage.total }} 条</span></div>
          <el-table :data="records" border stripe size="small" v-loading="loadingRecords" class="record-table">
            <el-table-column label="采集时间" width="180"><template #default="{ row }">{{ formatTime(recordTime(row)) }}</template></el-table-column>
            <el-table-column v-for="field in valueFields" :key="field.columnName" :label="fieldLabel(field)" min-width="150">
              <template #default="{ row }">{{ valueOf(row, field.columnName) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background size="small" layout="total, sizes, prev, pager, next" :total="recordPage.total" :current-page="recordPage.pageNo" :page-size="recordPage.pageSize" :page-sizes="[50,100,200,500]" @current-change="page => { recordPage.pageNo = page; loadRecords() }" @size-change="size => { recordPage.pageNo = 1; recordPage.pageSize = size; loadRecords() }" />
        </section>

        <section v-else-if="selectedTemplate" class="workspace-section">
          <div class="meta-table">
            <div><span>模板名称</span><strong>{{ selectedTemplate.templateName }}</strong></div>
            <div><span>来源模型</span><strong>{{ modelName(selectedTemplate.deviceModelId) }}</strong></div>
            <div><span>模板类型</span><strong>{{ selectedTemplate.isDefault ? '默认模板' : '自定义模板' }}</strong></div>
            <div><span>创建时间</span><strong>{{ formatTime(selectedTemplate.createTime) }}</strong></div>
          </div>
          <div class="table-title"><strong>模板字段</strong><span>{{ templateDetails.length }} 个字段</span></div>
          <el-table :data="templateDetails" border stripe size="small" class="template-table">
            <el-table-column prop="columnName" label="模板字段" min-width="160" />
            <el-table-column prop="columnDesc" label="字段说明" min-width="160" />
            <el-table-column label="绑定属性" min-width="150"><template #default="{ row }">{{ bindingLabel(row) }}</template></el-table-column>
            <el-table-column label="默认值" width="130"><template #default="{ row }">{{ row.defaultValue || '-' }}</template></el-table-column>
            <el-table-column label="字段用途" width="120"><template #default="{ row }">{{ isUnitField(row) ? '单位' : '采集值' }}</template></el-table-column>
          </el-table>
        </section>

        <section v-else-if="selectedInstance" class="workspace-section">
          <div class="meta-table">
            <div><span>设备实例</span><strong>{{ selectedInstance.instanceName }}</strong></div>
            <div><span>所属模型</span><strong>{{ modelName(selectedInstance.deviceModelId) }}</strong></div>
            <div><span>绑定 Adapter</span><strong>{{ selectedInstance.boundAdapterName || '-' }}</strong></div>
            <div><span>设备点位</span><strong>{{ selectedInstance.boundDevicePoint || '-' }}</strong></div>
          </div>
          <el-empty description="该设备实例下暂无数据表"><el-button v-if="isUsableInstance(selectedInstance)" type="primary" @click="openDatasetDrawer(null, selectedInstance)">为该设备建表</el-button></el-empty>
        </section>

        <section v-else class="workspace-empty"><el-empty description="请选择左侧数据表、模板或设备实例" /></section>
      </main>
    </section>

    <el-drawer v-model="datasetDrawer.visible" title="新建数据表" size="78%" class="unified-workflow-drawer">
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="数据表说明"><el-input v-model="datasetDrawer.dataDesc" placeholder="例如：高压报警专项数据" /></el-form-item>
        <el-form-item label="数据模板"><el-select v-model="datasetDrawer.templateId" filterable placeholder="请选择模板"><el-option v-for="tpl in templates" :key="tpl.id" :label="tpl.templateName" :value="tpl.id" /></el-select></el-form-item>
        <el-form-item label="绑定设备实例"><el-select v-model="datasetDrawer.deviceInstanceId" filterable placeholder="请选择设备"><el-option v-for="ins in usableInstances" :key="instanceId(ins)" :label="instancePath(ins)" :value="Number(instanceId(ins))" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="datasetDrawer.visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="createDataset">保存建表</el-button></template>
    </el-drawer>

    <el-drawer v-model="templateDrawer.visible" title="新增数据模板" size="78%" class="unified-workflow-drawer">
      <el-form label-position="top" class="drawer-form">
        <div class="form-grid two">
          <el-form-item label="模板名称"><el-input v-model="templateDrawer.templateName" /></el-form-item>
          <el-form-item label="绑定模型"><el-select v-model="templateDrawer.deviceModelId" filterable @change="generateFieldsFromModel"><el-option v-for="model in models" :key="modelId(model)" :label="model.modelName" :value="Number(modelId(model))" /></el-select></el-form-item>
        </div>
        <el-form-item label="模板说明"><el-input v-model="templateDrawer.templateDesc" /></el-form-item>
        <div class="table-title"><strong>模板字段</strong><el-button size="small" @click="addTemplateField">新增字段</el-button></div>
        <div class="field-editor">
          <div class="field-head"><span>模板字段</span><span>字段说明</span><span>绑定属性</span><span>默认值</span><span></span></div>
          <div v-for="(field, idx) in templateDrawer.details" :key="field._key" class="field-row">
            <el-input v-model="field.columnName" />
            <el-input v-model="field.columnDesc" />
            <el-select v-model="field.deviceAttrKey" clearable placeholder="可为空"><el-option v-for="attr in selectedTemplateModelAttrs" :key="attr.attributeName" :label="attr.displayName || attr.attributeName" :value="attr.attributeName" /></el-select>
            <el-input v-model="field.defaultValue" placeholder="无默认值" />
            <el-button text type="danger" @click="templateDrawer.details.splice(idx, 1)">删除</el-button>
          </div>
        </div>
      </el-form>
      <template #footer><el-button @click="templateDrawer.visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveTemplate">保存模板</el-button></template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Cpu, Folder, Tickets } from '@element-plus/icons-vue'
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
const templateDetails = ref([])
const records = ref([])
const selectedDataset = ref(null)
const selectedTemplate = ref(null)
const selectedInstance = ref(null)
const chartRef = ref(null)
const chart = shallowRef(null)
const recordPage = reactive({ pageNo: 1, pageSize: 100, total: 0 })
const detailCache = reactive({})

const datasetDrawer = reactive({ visible: false, templateId: null, deviceInstanceId: null, dataDesc: '' })
const templateDrawer = reactive({ visible: false, templateName: '', templateDesc: '', deviceModelId: null, details: [] })

const modelMap = computed(() => Object.fromEntries(models.value.map(m => [String(modelId(m)), m])))
const templateMap = computed(() => Object.fromEntries(templates.value.map(t => [String(t.id), t])))
const usableInstances = computed(() => instances.value.filter(isUsableInstance))
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
const assetTree = computed(() => [
  { key: 'root_device_data', type: 'root', label: '设备数据', count: datasets.value.length, children: buildCategoryNodes('root') },
  { key: 'root_templates', type: 'root', label: '模板库', count: templates.value.length, children: templates.value.map(t => ({ key: 'template_' + t.id, type: 'template', label: t.templateName, data: t, count: t.isDefault ? '默认' : '自定义' })) }
])
const filteredTree = computed(() => filterTree(assetTree.value, keyword.value.trim().toLowerCase()))

async function loadAll() {
  loading.value = true
  try {
    const [catRes, modelRes, insRes, datasetRes, tplRes] = await Promise.all([
      axios.get('/api/device/category/list').catch(() => ({ data: { data: [] } })),
      axios.get('/api/device/model/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/data/index/list'),
      axios.get('/api/data/template/list')
    ])
    categories.value = asArray(catRes.data?.data)
    models.value = asArray(modelRes.data?.data)
    instances.value = asArray(insRes.data?.data)
    datasets.value = asArray(datasetRes.data?.data)
    templates.value = asArray(tplRes.data?.data)
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
}

async function selectDataset(dataset) {
  selectedDataset.value = dataset
  selectedTemplate.value = null
  selectedInstance.value = instances.value.find(ins => String(instanceId(ins)) === String(dataset.deviceInstanceId)) || null
  activeKey.value = 'dataset_' + dataset.id
  recordPage.pageNo = 1
  await loadTemplateDetails(dataset.dataTemplateId)
  await loadRecords()
}

async function selectTemplate(template) {
  selectedTemplate.value = template
  selectedDataset.value = null
  selectedInstance.value = null
  activeKey.value = 'template_' + template.id
  records.value = []
  disposeChart()
  await loadTemplateDetails(template.id)
}

function selectInstance(instance) {
  selectedInstance.value = instance
  selectedDataset.value = null
  selectedTemplate.value = null
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

async function loadRecords() {
  if (!selectedDataset.value) return
  loadingRecords.value = true
  try {
    const res = await axios.get(`/api/data/record/dataset/${selectedDataset.value.id}`, { params: { pageNo: recordPage.pageNo, pageSize: recordPage.pageSize } })
    const page = res.data?.data || {}
    records.value = asArray(page.records)
    recordPage.total = Number(page.total || 0)
    await nextTick()
    renderChart()
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载数据记录失败'))
  } finally {
    loadingRecords.value = false
  }
}

function renderChart() {
  if (!chartRef.value || !selectedDataset.value) return
  if (!chart.value) chart.value = echarts.init(chartRef.value)
  const sorted = [...records.value].sort((a, b) => new Date(recordTime(a) || 0) - new Date(recordTime(b) || 0))
  const xData = sorted.map(row => formatTime(recordTime(row)))
  const units = [...new Set(valueFields.value.map(field => unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'))]
  const yAxis = units.map((unit, idx) => ({ type: 'value', name: unit, position: idx % 2 ? 'right' : 'left', offset: idx > 1 ? (idx - 1) * 42 : 0, splitLine: { show: idx === 0 } }))
  const series = valueFields.value.map(field => {
    const unit = unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'
    return { name: field.columnDesc || field.columnName, type: 'line', smooth: true, yAxisIndex: Math.max(0, units.indexOf(unit)), data: sorted.map(row => numericOrNull(valueOf(row, field.columnName))) }
  })
  chart.value.setOption({ tooltip: { trigger: 'axis' }, legend: { top: 8 }, grid: { left: 56, right: Math.max(24, units.length * 42), top: 54, bottom: 36 }, xAxis: { type: 'category', data: xData }, yAxis: yAxis.length ? yAxis : [{ type: 'value' }], series }, true)
}

function openDatasetDrawer(template = null, instance = null) {
  if (instance && !isUsableInstance(instance)) {
    ElMessage.warning('已注销设备不能新建数据表')
    return
  }
  datasetDrawer.visible = true
  datasetDrawer.templateId = template?.id || selectedDataset.value?.dataTemplateId || selectedTemplate.value?.id || null
  if (instance) datasetDrawer.deviceInstanceId = Number(instanceId(instance))
  else if (selectedDataset.value?.deviceInstanceId) datasetDrawer.deviceInstanceId = Number(selectedDataset.value.deviceInstanceId)
  else if (selectedInstance.value) datasetDrawer.deviceInstanceId = Number(instanceId(selectedInstance.value))
  else datasetDrawer.deviceInstanceId = null
  datasetDrawer.dataDesc = ''
}

async function createDataset() {
  const selected = instances.value.find(ins => Number(instanceId(ins)) === Number(datasetDrawer.deviceInstanceId))
  if (selected && !isUsableInstance(selected)) { ElMessage.warning('已注销设备不能新建数据表'); return }
  if (!datasetDrawer.templateId || !datasetDrawer.deviceInstanceId) { ElMessage.warning('请选择模板和设备实例'); return }
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

function generateFieldsFromModel() {
  const model = modelMap.value[String(templateDrawer.deviceModelId)]
  const attrs = asArray(model?.attributes)
  templateDrawer.details = attrs.flatMap(attr => {
    const name = attr.attributeName || attr.displayName
    const desc = attr.displayName || attr.attributeName
    const rows = [{ _key: uid(), columnName: name, columnDesc: desc, propertyTypeId: propertyTypeId(attr.dataType), columnLength: 255, deviceAttrKey: name, defaultValue: '' }]
    if (attr.unit) rows.push({ _key: uid(), columnName: `${name}_unit`, columnDesc: `${desc}单位`, propertyTypeId: 6, columnLength: 50, deviceAttrKey: '', defaultValue: attr.unit })
    return rows
  })
}

function addTemplateField() { templateDrawer.details.push({ _key: uid(), columnName: '', columnDesc: '', propertyTypeId: 6, columnLength: 255, deviceAttrKey: '', defaultValue: '' }) }

async function saveTemplate() {
  if (!templateDrawer.templateName.trim()) { ElMessage.warning('请输入模板名称'); return }
  if (!templateDrawer.deviceModelId) { ElMessage.warning('请选择绑定模型'); return }
  saving.value = true
  try {
    const payload = { templateName: templateDrawer.templateName, templateDesc: templateDrawer.templateDesc, deviceModelId: templateDrawer.deviceModelId, isDefault: false, details: templateDrawer.details.map(({ _key, ...row }) => ({ ...row, columnLength: row.columnLength || 255 })) }
    const res = await axios.post('/api/data/template/save', payload)
    if (res.data?.success) {
      ElMessage.success('模板已保存')
      templateDrawer.visible = false
      await loadAll()
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

function exportDataset() { if (selectedDataset.value?.id) window.open(`/api/data/record/export/${selectedDataset.value.id}`, '_blank') }
function selectFirstAvailable() { const firstDataset = datasets.value[0]; if (firstDataset) selectDataset(firstDataset); else if (templates.value[0]) selectTemplate(templates.value[0]) }
function fieldLabel(field) { const unit = unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey); return `${field.columnDesc || field.columnName}${unit ? ' (' + unit + ')' : ''}` }
function bindingLabel(row) { if (isUnitField(row)) return '-'; return row.deviceAttrKey || '-' }
function valueOf(row, key) { const value = row?.[key] ?? row?.[String(key).toLowerCase()] ?? row?.data?.[key] ?? row?.payload?.[key]; return value == null || value === '' ? '-' : value }
function numericOrNull(value) { const n = Number(value); return Number.isFinite(n) ? n : null }
function recordTime(row) { return row?.create_time || row?.createTime || row?.timestamp || row?.collectTime }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
function isUnitField(row) { return String(row?.columnName || '').endsWith('_unit') }
function stripUnit(value) { return String(value || '').replace(/_unit$/, '') }
function modelId(model) { return model?.modelId || model?.id }
function isUsableInstance(instance) { return instance?.lifecycleStatus === '使用中' }

function instanceId(instance) { return instance?.instanceId || instance?.id }
function modelName(id) { return modelMap.value[String(id)]?.modelName || '-' }
function templateName(id) { return templateMap.value[String(id)]?.templateName || '-' }
function instanceName(id) { return instances.value.find(ins => String(instanceId(ins)) === String(id))?.instanceName || '-' }
function instancePath(ins) { return `${modelName(ins.deviceModelId)} / ${ins.instanceName}` }
function asArray(value) { return Array.isArray(value) ? value : [] }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function propertyTypeId(type) { const t = String(type || '').toUpperCase(); return t === 'DOUBLE' ? 4 : t === 'INTEGER' ? 3 : t === 'BOOLEAN' ? 5 : 6 }
function uid() { return Math.random().toString(36).slice(2, 10) }
function disposeChart() { chart.value?.dispose(); chart.value = null }
function resizeChart() { chart.value?.resize() }

watch(templateDetails, () => nextTick(renderChart))
onMounted(() => { loadAll(); window.addEventListener('resize', resizeChart) })
onUnmounted(() => { window.removeEventListener('resize', resizeChart); disposeChart() })
</script>

<style scoped>
.data-workbench { height: calc(100vh - 52px); display: flex; background: #eef2f6; color: #0f172a; }
.asset-pane { width: 360px; min-width: 360px; background: #f8fafc; border-right: 1px solid #cbd5e1; display: flex; flex-direction: column; }
.asset-header { min-height: 58px; padding: 10px 12px; background: #fff; border-bottom: 1px solid #dbe3ee; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.asset-header strong { display: block; font-size: 16px; }
.asset-header span { color: #64748b; font-size: 12px; }
.asset-search { width: auto; margin: 10px 12px; }
.asset-tree { flex: 1; min-height: 0; overflow: auto; padding: 4px 8px 12px; }
.asset-node { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.asset-node-main { min-width: 0; display: flex; align-items: center; gap: 6px; }
.asset-node-main span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.asset-node em { min-width: 26px; min-height: 18px; border-radius: 9px; background: #e2e8f0; color: #475569; display: inline-flex; align-items: center; justify-content: center; font-size: 11px; font-style: normal; }
.asset-node.active .asset-node-main span { color: #1d4ed8; font-weight: 800; }
.asset-node.dataset .asset-node-main span { color: #047857; }
.asset-node.template .asset-node-main span { color: #6d28d9; }
.data-workspace { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.workspace-toolbar { min-height: 72px; padding: 10px 16px; background: #fff; border-bottom: 1px solid #cbd5e1; display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.workspace-toolbar span { color: #64748b; font-size: 12px; }
.workspace-toolbar h1 { margin: 2px 0 0; font-size: 24px; line-height: 1.25; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
.workspace-body { flex: 1; min-height: 0; overflow: auto; padding: 10px 12px 22px; }
.workspace-section { background: #fff; border: 1px solid #cbd5e1; padding: 12px; }
.meta-table { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid #dbe3ee; margin-bottom: 10px; }
.meta-table div { min-width: 0; padding: 9px 10px; border-right: 1px solid #e2e8f0; background: #f8fafc; }
.meta-table div:last-child { border-right: 0; }
.meta-table span { display: block; color: #64748b; font-size: 12px; margin-bottom: 3px; }
.meta-table strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.chart-box { height: 320px; border: 1px solid #dbe3ee; margin-bottom: 10px; }
.table-title { min-height: 36px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.table-title strong { font-size: 16px; }
.table-title span { color: #64748b; font-size: 12px; }
.record-table, .template-table { width: 100%; }
.pager { margin-top: 10px; display: flex; justify-content: flex-end; }
.workspace-empty { min-height: 480px; display: flex; align-items: center; justify-content: center; background: #fff; border: 1px solid #cbd5e1; }
.drawer-form { padding: 0 18px; }
.form-grid.two { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.field-editor { border: 1px solid #dbe3ee; }
.field-head, .field-row { display: grid; grid-template-columns: minmax(130px, 1fr) minmax(130px, 1fr) minmax(130px, 1fr) minmax(110px, 0.8fr) 60px; align-items: center; gap: 8px; padding: 8px; border-bottom: 1px solid #e2e8f0; }
.field-head { background: #f1f5f9; font-weight: 800; color: #334155; }
.field-row:last-child { border-bottom: 0; }
@media (max-width: 1180px) { .meta-table { grid-template-columns: repeat(2, minmax(0, 1fr)); } .field-head, .field-row { grid-template-columns: 1fr; } }

/* Enterprise data console overrides */
.data-workbench { background: #f5f7fa; }
.asset-header { min-height: 56px; padding: 8px 12px; border-color: #e5e7eb; }
.asset-search { margin: 8px 12px; }
.workspace-toolbar h1 { font-size: 20px; font-weight: 600; }
.workspace-body { padding: 12px 16px 20px; }
.workspace-section { border-radius: 6px; }
.chart-box { height: 300px; border-color: #e5e7eb; }
.field-editor { border-radius: 6px; overflow: hidden; }
</style>
