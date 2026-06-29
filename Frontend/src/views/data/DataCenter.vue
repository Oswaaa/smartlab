
<template>
  <div class="data-center-page">
    <aside class="left-panel">
      <div class="left-header">
        <span>数据列表</span>
        <el-button type="primary" size="small" @click="openCreateDrawer">新建</el-button>
      </div>
      <div class="list-body" v-loading="loadingTemplates">
        <div
          v-for="tpl in templates"
          :key="tpl.templateId"
          :class="['list-item', { active: selectedTemplateId === tpl.templateId }]"
          @click="selectTemplate(tpl)"
        >
          <div class="item-name">{{ tpl.templateName }}</div>
          <div class="item-id">{{ tpl.templateId }}</div>
          <el-button link type="danger" @click.stop="deleteTemplate(tpl)">删除</el-button>
        </div>
        <el-empty v-if="!loadingTemplates && templates.length === 0" description="暂无数据表" :image-size="60" />
      </div>
    </aside>

    <section class="right-panel">
      <div class="top-area" v-if="selectedTemplate">
        <el-card shadow="never" class="top-card records-card">
          <template #header>
            <div class="card-header">
              <span>数据记录</span>
              <el-button size="small" @click="refreshSelectedRecords">刷新</el-button>
            </div>
          </template>
          <el-table :data="selectedRecords" border stripe size="small" class="records-table" v-loading="loadingSelectedRecords">
            <el-table-column label="采集时间" min-width="165">
              <template #default="{ row }">{{ formatTime(row.collectTime) }}</template>
            </el-table-column>
            <el-table-column prop="generatorInstanceId" label="设备实例" min-width="140" />
            <el-table-column
              v-for="field in selectedSchemaRows"
              :key="field.key"
              :label="field.label + (field.unit ? ` (${field.unit})` : '')"
              min-width="130"
            >
              <template #default="{ row }">{{ renderPayloadValue(row.dataPayload?.[field.key]) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="records-pagination"
            layout="total, sizes, prev, pager, next"
            :total="selectedRecordTotal"
            :current-page="selectedRecordPage.pageNo"
            :page-size="selectedRecordPage.pageSize"
            :page-sizes="[50, 100, 200, 500]"
            small
            background
            @current-change="onRecordPageChange"
            @size-change="onRecordPageSizeChange"
          />
        </el-card>

        <el-card shadow="never" class="top-card schema-card">
          <template #header><div class="card-header"><span>模板结构</span></div></template>
          <el-table :data="selectedSchemaRows" border size="small" height="100%">
            <el-table-column prop="label" label="字段名" min-width="120" />
            <el-table-column prop="type" label="类型" width="90" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column label="映射设备" min-width="130">
              <template #default="{ row }">{{ instanceNameById(row.instanceId) }}</template>
            </el-table-column>
            <el-table-column prop="attributeName" label="映射属性" min-width="120" />
          </el-table>
        </el-card>
      </div>
      <div class="top-area empty" v-else>
        <el-empty description="请选择左侧数据表" />
      </div>

      <el-card shadow="never" class="bottom-area">
        <template #header>
          <div class="card-header chart-header">
            <span>数据可视化</span>
            <div class="chart-tools">
              <el-select v-model="compareTemplateIds" multiple collapse-tags collapse-tags-tooltip style="width: 280px" placeholder="选择数据表" @change="onCompareTemplatesChange">
                <el-option v-for="tpl in templates" :key="tpl.templateId" :label="tpl.templateName" :value="tpl.templateId" />
              </el-select>
              <el-select v-model="compareFieldKeys" multiple collapse-tags collapse-tags-tooltip style="width: 340px" placeholder="选择字段" @change="renderCompareChart">
                <el-option
                  v-for="op in compareFieldOptions"
                  :key="op.value"
                  :label="op.label"
                  :value="op.value"
                />
              </el-select>
              <el-button size="small" @click="refreshCompareData">刷新图表</el-button>
            </div>
          </div>
        </template>
        <div class="chart-tip">不同单位使用不同 Y 轴；可同时对比多个数据表。</div>
        <div ref="chartRef" class="chart-box"></div>
      </el-card>
    </section>

    <el-drawer v-model="createDrawerVisible" title="新建数据模板" size="760px">
      <el-form :model="createForm" :rules="formRules" ref="formRef" label-width="96px" size="small">
        <el-form-item label="模板名称" prop="templateName"><el-input v-model="createForm.templateName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" :rows="2" /></el-form-item>

        <el-divider content-position="left">字段设计</el-divider>
        <div class="table-actions"><el-button size="small" @click="addFieldRow">新增字段</el-button></div>
        <el-table :data="createForm.fields" border size="small">
          <el-table-column label="字段名" min-width="130">
            <template #default="{ row }"><el-input v-model="row.fieldName" placeholder="例如 反应温度" /></template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-select v-model="row.dataType" style="width: 100%">
                <el-option label="数字" value="number" />
                <el-option label="字符串" value="string" />
                <el-option label="布尔" value="boolean" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="95"><template #default="{ row }"><el-input v-model="row.unit" placeholder="如 ℃" /></template></el-table-column>
          <el-table-column label="设备实例" min-width="130">
            <template #default="{ row }">
              <el-select v-model="row.instanceId" style="width: 100%" @change="onFieldInstanceChange(row)">
                <el-option v-for="ins in deviceInstances" :key="ins.instanceId" :label="ins.instanceName" :value="ins.instanceId" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="设备属性" min-width="150">
            <template #default="{ row }">
              <el-select v-model="row.attributeId" style="width: 100%" @change="onFieldAttributeChange(row)">
                <el-option v-for="attr in attrsByInstance(row.instanceId)" :key="attr.identifier" :label="attr.name || attr.identifier" :value="attr.identifier" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="130"><template #default="{ row }"><el-input v-model="row.description" /></template></el-table-column>
          <el-table-column label="操作" width="70"><template #default="{ $index }"><el-button link type="danger" @click="removeFieldRow($index)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="createDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="submitCreateTemplate">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import axios from 'axios'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import * as echarts from 'echarts'

type TemplateFieldRow = {
  key: string
  label: string
  type: string
  unit: string
  instanceId: string
  attributeId: string
  attributeName: string
}

type CreateFieldRow = {
  fieldName: string
  dataType: 'number' | 'string' | 'boolean'
  unit: string
  instanceId: string
  attributeId: string
  description: string
}

const templates = ref<any[]>([])
const loadingTemplates = ref(false)
const selectedTemplateId = ref('')
const selectedTemplate = ref<any>(null)

const recordsByTemplate = ref<Record<string, any[]>>({})
const recordPageByTemplate = ref<Record<string, { pageNo: number; pageSize: number; total: number }>>({})
const loadingRecordsMap = ref<Record<string, boolean>>({})

const chartRef = ref<HTMLElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)

const compareTemplateIds = ref<string[]>([])
const compareFieldKeys = ref<string[]>([])

const createDrawerVisible = ref(false)
const formRef = ref<FormInstance>()
const savingTemplate = ref(false)
const deviceInstances = ref<any[]>([])
const modelMap = ref<Record<string, any>>({})

const createForm = ref({
  templateName: '',
  description: '',
  fields: [] as CreateFieldRow[]
})

const formRules: FormRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }]
}

const parseSchemaRows = (tpl: any): TemplateFieldRow[] => {
  const spec = tpl?.dataSchemaSpec || {}
  return Object.keys(spec).map((k: string) => ({
    key: k,
    label: spec[k]?.label || k,
    type: spec[k]?.type || 'string',
    unit: spec[k]?.unit || '',
    instanceId: spec[k]?.instanceId || '',
    attributeId: spec[k]?.attributeId || '',
    attributeName: spec[k]?.attributeName || spec[k]?.attributeId || ''
  }))
}

const selectedSchemaRows = computed(() => (selectedTemplate.value ? parseSchemaRows(selectedTemplate.value) : []))
const selectedRecords = computed(() => recordsByTemplate.value[selectedTemplateId.value] || [])
const selectedRecordPage = computed(() => pageState(selectedTemplateId.value))
const selectedRecordTotal = computed(() => selectedRecordPage.value.total || 0)
const loadingSelectedRecords = computed(() => !!loadingRecordsMap.value[selectedTemplateId.value])

const compareFieldOptions = computed(() => {
  const arr: Array<{ label: string; value: string }> = []
  for (const tid of compareTemplateIds.value) {
    const tpl = templates.value.find((v: any) => v.templateId === tid)
    if (!tpl) continue
    const schemaRows = parseSchemaRows(tpl)
    schemaRows.forEach(row => {
      arr.push({
        value: `${tid}__${row.key}`,
        label: `${tpl.templateName} / ${row.label}${row.unit ? ` (${row.unit})` : ''}`
      })
    })
  }
  return arr
})

const formatTime = (v?: string) => (v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '-')
const renderPayloadValue = (v: any) => (v === undefined || v === null || v === '' ? '-' : String(v))

const fetchTemplates = async () => {
  loadingTemplates.value = true
  try {
    const res = await axios.get('/api/data/template/list')
    if (res.data?.success) {
      templates.value = res.data.data || []
      if (!selectedTemplateId.value && templates.value.length > 0) {
        selectTemplate(templates.value[0])
      } else if (selectedTemplateId.value) {
        const curr = templates.value.find((v: any) => v.templateId === selectedTemplateId.value)
        if (curr) selectedTemplate.value = curr
      }
      if (compareTemplateIds.value.length === 0 && templates.value.length > 0) {
        compareTemplateIds.value = [templates.value[0].templateId]
      }
    } else {
      ElMessage.error(res.data?.message || '加载数据表失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载数据表失败')
  } finally {
    loadingTemplates.value = false
  }
}

const fetchModelAndInstance = async () => {
  try {
    const [insRes, modelRes] = await Promise.all([
      axios.get('/api/device/instance/list'),
      axios.get('/api/device/model/list')
    ])
    if (insRes.data?.success) deviceInstances.value = insRes.data.data || []
    if (modelRes.data?.success) {
      const map: Record<string, any> = {}
      ;(modelRes.data.data || []).forEach((m: any) => (map[m.modelId] = m))
      modelMap.value = map
    }
  } catch {}
}

const fetchRecordsByTemplateId = async (templateId: string) => {
  if (!templateId) return
  loadingRecordsMap.value[templateId] = true
  try {
    const state = pageState(templateId)
    const res = await axios.get(`/api/data/record/page/${templateId}`, {
      params: { pageNo: state.pageNo, pageSize: state.pageSize }
    })
    if (res.data?.success) {
      const page = res.data.data || {}
      recordsByTemplate.value[templateId] = page.records || []
      recordPageByTemplate.value[templateId] = {
        pageNo: Number(page.pageNo || state.pageNo),
        pageSize: Number(page.pageSize || state.pageSize),
        total: Number(page.total || 0)
      }
    } else {
      ElMessage.error(res.data?.message || '加载记录失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载记录失败')
  } finally {
    loadingRecordsMap.value[templateId] = false
  }
}

const pageState = (templateId: string) => {
  if (!recordPageByTemplate.value[templateId]) {
    recordPageByTemplate.value[templateId] = { pageNo: 1, pageSize: 100, total: 0 }
  }
  return recordPageByTemplate.value[templateId]
}

const selectTemplate = async (tpl: any) => {
  selectedTemplate.value = tpl
  selectedTemplateId.value = tpl.templateId
  if (!recordsByTemplate.value[tpl.templateId]) {
    await fetchRecordsByTemplateId(tpl.templateId)
  }
  renderCompareChart()
}

const refreshSelectedRecords = async () => {
  if (!selectedTemplateId.value) return
  await fetchRecordsByTemplateId(selectedTemplateId.value)
  renderCompareChart()
}

const onRecordPageChange = async (pageNo: number) => {
  if (!selectedTemplateId.value) return
  pageState(selectedTemplateId.value).pageNo = pageNo
  await refreshSelectedRecords()
}

const onRecordPageSizeChange = async (pageSize: number) => {
  if (!selectedTemplateId.value) return
  const state = pageState(selectedTemplateId.value)
  state.pageNo = 1
  state.pageSize = pageSize
  await refreshSelectedRecords()
}

const onCompareTemplatesChange = async () => {
  for (const tid of compareTemplateIds.value) {
    if (!recordsByTemplate.value[tid]) await fetchRecordsByTemplateId(tid)
  }
  const optionsSet = new Set(compareFieldOptions.value.map(v => v.value))
  compareFieldKeys.value = compareFieldKeys.value.filter(v => optionsSet.has(v))
  if (compareFieldKeys.value.length === 0) {
    compareFieldKeys.value = compareFieldOptions.value.slice(0, 3).map(v => v.value)
  }
  renderCompareChart()
}

const refreshCompareData = async () => {
  for (const tid of compareTemplateIds.value) {
    await fetchRecordsByTemplateId(tid)
  }
  renderCompareChart()
}

const instanceNameById = (id: string) => {
  if (!id) return '-'
  const ins = deviceInstances.value.find((v: any) => v.instanceId === id)
  return ins?.instanceName || id
}

const attrsByInstance = (instanceId: string) => {
  if (!instanceId) return []
  const ins = deviceInstances.value.find((v: any) => v.instanceId === instanceId)
  if (!ins) return []
  return modelMap.value[ins.modelId]?.capabilitySpec?.attributes || []
}

const addFieldRow = () => {
  createForm.value.fields.push({
    fieldName: '',
    dataType: 'number',
    unit: '',
    instanceId: '',
    attributeId: '',
    description: ''
  })
}

const removeFieldRow = (idx: number) => {
  createForm.value.fields.splice(idx, 1)
}

const onFieldInstanceChange = (row: CreateFieldRow) => {
  row.attributeId = ''
}

const onFieldAttributeChange = (row: CreateFieldRow) => {
  const attrs = attrsByInstance(row.instanceId)
  const attr = attrs.find((a: any) => a.identifier === row.attributeId)
  if (!attr) return
  if (!row.fieldName) row.fieldName = attr.name || attr.identifier
  if (!row.unit) row.unit = attr.unit || ''
  if (!row.dataType) {
    const t = String(attr.dataType || '').toLowerCase()
    row.dataType = t.includes('int') || t.includes('float') || t.includes('double') || t.includes('number') ? 'number' : t.includes('bool') ? 'boolean' : 'string'
  }
}

const openCreateDrawer = async () => {
  await fetchModelAndInstance()
  createForm.value = {
    templateName: '',
    description: '',
    fields: []
  }
  addFieldRow()
  createDrawerVisible.value = true
}

const submitCreateTemplate = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return

    if (createForm.value.fields.length === 0) {
      ElMessage.warning('请至少配置一个字段')
      return
    }

    for (const [i, f] of createForm.value.fields.entries()) {
      if (!f.fieldName || !f.instanceId || !f.attributeId) {
        ElMessage.warning(`第 ${i + 1} 行字段信息不完整`)
        return
      }
    }

    const schema: Record<string, any> = {}
    createForm.value.fields.forEach((f, idx) => {
      const attrs = attrsByInstance(f.instanceId)
      const attr = attrs.find((a: any) => a.identifier === f.attributeId)
      const key = `f_${idx + 1}`
      schema[key] = {
        label: f.fieldName,
        type: f.dataType,
        unit: f.unit || attr?.unit || '',
        instanceId: f.instanceId,
        attributeId: f.attributeId,
        attributeName: attr?.name || f.attributeId,
        description: f.description || ''
      }
    })

    savingTemplate.value = true
    try {
      const res = await axios.post('/api/data/template/save', {
        templateId: '',
        templateName: createForm.value.templateName,
        description: createForm.value.description,
        dataSchemaSpec: schema
      })

      if (res.data?.success) {
        ElMessage.success('数据模板保存成功')
        createDrawerVisible.value = false
        await fetchTemplates()
      } else {
        ElMessage.error(res.data?.message || '保存失败')
      }
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message || '保存失败')
    } finally {
      savingTemplate.value = false
    }
  })
}

const deleteTemplate = async (tpl: any) => {
  try {
    const res = await axios.delete(`/api/data/template/delete/${tpl.templateId}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      delete recordsByTemplate.value[tpl.templateId]
      delete recordPageByTemplate.value[tpl.templateId]
      if (selectedTemplateId.value === tpl.templateId) {
        selectedTemplateId.value = ''
        selectedTemplate.value = null
      }
      compareTemplateIds.value = compareTemplateIds.value.filter(v => v !== tpl.templateId)
      compareFieldKeys.value = compareFieldKeys.value.filter(v => !v.startsWith(`${tpl.templateId}__`))
      await fetchTemplates()
      renderCompareChart()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '删除失败')
  }
}

const buildSeriesAndAxis = () => {
  const selected = compareFieldKeys.value
  const templateMap: Record<string, any> = {}
  templates.value.forEach((t: any) => (templateMap[t.templateId] = t))

  const allTimes = new Set<string>()
  const parsedTargets = selected.map(v => {
    const [templateId, key] = v.split('__')
    return { templateId, key }
  })

  parsedTargets.forEach(t => {
    const recs = recordsByTemplate.value[t.templateId] || []
    recs.forEach(r => {
      if (r.collectTime) allTimes.add(new Date(r.collectTime).toISOString())
    })
  })

  const timeList = Array.from(allTimes).sort((a, b) => new Date(a).getTime() - new Date(b).getTime())
  const xAxisData = timeList.map(v => formatTime(v))

  const unitAxisMap = new Map<string, number>()
  const yAxis: any[] = []
  const series: any[] = []

  const getAxisIdx = (unit: string) => {
    const u = unit || '无单位'
    if (unitAxisMap.has(u)) return unitAxisMap.get(u) as number
    const idx = yAxis.length
    unitAxisMap.set(u, idx)
    yAxis.push({
      type: 'value',
      name: u,
      position: idx % 2 === 0 ? 'left' : 'right',
      offset: idx > 1 ? Math.floor((idx - 1) / 2) * 55 : 0,
      axisLine: { show: true },
      splitLine: { show: idx === 0, lineStyle: { color: '#e5e7eb' } }
    })
    return idx
  }

  parsedTargets.forEach(target => {
    const tpl = templateMap[target.templateId]
    if (!tpl) return
    const schema = tpl.dataSchemaSpec || {}
    const spec = schema[target.key] || {}
    const axisIdx = getAxisIdx(spec.unit || '')

    const recs = [...(recordsByTemplate.value[target.templateId] || [])].sort((a, b) => new Date(a.collectTime).getTime() - new Date(b.collectTime).getTime())
    const valueMap = new Map<string, number | null>()
    recs.forEach(r => {
      const raw = r?.dataPayload?.[target.key]
      const num = Number(raw)
      valueMap.set(new Date(r.collectTime).toISOString(), Number.isFinite(num) ? num : null)
    })

    const data = timeList.map(t => (valueMap.has(t) ? valueMap.get(t) : null))
    series.push({
      type: 'line',
      smooth: true,
      connectNulls: false,
      yAxisIndex: axisIdx,
      name: `${tpl.templateName} / ${spec.label || target.key}`,
      data
    })
  })

  return { xAxisData, yAxis, series }
}

const renderCompareChart = () => {
  if (!chart.value) return
  const { xAxisData, yAxis, series } = buildSeriesAndAxis()
  chart.value.setOption(
    {
      backgroundColor: '#ffffff',
      tooltip: { trigger: 'axis' },
      legend: { type: 'scroll', top: 8 },
      grid: { left: 56, right: 56, top: 54, bottom: 40 },
      xAxis: { type: 'category', data: xAxisData, axisLabel: { color: '#6b7280' } },
      yAxis,
      series
    },
    true
  )
}

const onResize = () => chart.value?.resize()

watch(compareFieldKeys, () => renderCompareChart())

onMounted(async () => {
  await Promise.all([fetchTemplates(), fetchModelAndInstance()])
  await nextTick()
  if (chartRef.value) chart.value = echarts.init(chartRef.value)
  if (compareTemplateIds.value.length > 0) await onCompareTemplatesChange()
  renderCompareChart()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart.value?.dispose()
})
</script>

<style scoped>
.data-center-page {
  display: flex;
  gap: 12px;
  padding: 12px;
  height: calc(100vh - 52px);
  background: #f1f3f6;
}

.left-panel {
  width: 280px;
  min-width: 280px;
  border: 1px solid #d4d9e0;
  border-radius: 8px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
}

.left-header {
  height: 48px;
  padding: 0 10px;
  border-bottom: 1px solid #e2e6ec;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #1f2937;
  font-weight: 600;
}

.list-body {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.list-item {
  border: 1px solid #e3e7ed;
  border-radius: 8px;
  background: #fff;
  padding: 8px;
  margin-bottom: 8px;
  cursor: pointer;
}

.list-item.active {
  border-color: #aab2be;
  background: #f6f8fb;
}

.item-name {
  font-size: 13px;
  color: #111827;
  font-weight: 600;
}

.item-id {
  font-size: 11px;
  color: #6b7280;
  font-family: 'JetBrains Mono', Consolas, monospace;
  margin-top: 2px;
  margin-bottom: 4px;
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.top-area {
  display: grid;
  grid-template-columns: 1.45fr 1fr;
  gap: 12px;
  height: 44%;
  min-height: 260px;
}

.top-area.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #d4d9e0;
  border-radius: 8px;
  background: #fff;
}

.top-card,
.bottom-area {
  border: 1px solid #d4d9e0;
  border-radius: 8px;
}

.top-card :deep(.el-card__body) {
  height: calc(100% - 56px);
}

.records-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.records-table {
  flex: 1;
  min-height: 0;
}

.records-pagination {
  justify-content: flex-end;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #1f2937;
  font-weight: 600;
}

.bottom-area {
  flex: 1;
  min-height: 280px;
}

.chart-header {
  align-items: center;
  gap: 8px;
}

.chart-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-tip {
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 6px;
}

.chart-box {
  width: 100%;
  height: calc(100% - 30px);
  min-height: 260px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.table-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

@media (max-width: 1200px) {
  .top-area {
    grid-template-columns: 1fr;
    height: 58%;
  }
}

@media (max-width: 900px) {
  .data-center-page {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 52px);
  }

  .left-panel {
    width: 100%;
    min-width: 100%;
    max-height: 260px;
  }
}
</style>
