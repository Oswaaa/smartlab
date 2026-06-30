<template>
  <div class="data-center-page">
    <aside class="left-panel">
      <div class="left-header">
        <span>数据视图</span>
        <el-button type="primary" size="small" @click="openCreateDrawer">新建表</el-button>
      </div>
      <div class="list-body" v-loading="loadingTree">
        <el-tree
          :data="treeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
        >
          <template #default="{ node, data }">
            <span class="custom-tree-node" :class="{ 'is-dataset': data.type === 'dataset', 'is-active': selectedDataset?.id === data.data?.id }">
              <el-icon v-if="data.type === 'category'"><Folder /></el-icon>
              <el-icon v-else-if="data.type === 'instance'"><Cpu /></el-icon>
              <el-icon v-else><Document /></el-icon>
              <span class="node-label">{{ node.label }}</span>
            </span>
          </template>
        </el-tree>
      </div>
    </aside>

    <section class="right-panel">
      <div class="top-area" v-if="selectedDataset">
        <el-card shadow="never" class="top-card records-card">
          <template #header>
            <div class="card-header">
              <div class="header-info">
                <span>{{ selectedDataset.dataDesc || selectedDataset.dataTable }}</span>
                <el-tag size="small" type="info" style="margin-left:8px">{{ instanceNameById(selectedDataset.deviceInstanceId) }}</el-tag>
              </div>
              <div class="header-actions">
                <el-button size="small" @click="exportCsv">导出 CSV</el-button>
                <el-button size="small" @click="fetchRecords">刷新</el-button>
              </div>
            </div>
          </template>
          <el-table :data="selectedRecords" border stripe size="small" class="records-table" v-loading="loadingRecords">
            <el-table-column label="采集时间" min-width="165">
              <template #default="{ row }">{{ formatTime(row.collectTime) }}</template>
            </el-table-column>
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
            :total="recordPage.total"
            :current-page="recordPage.pageNo"
            :page-size="recordPage.pageSize"
            :page-sizes="[50, 100, 200, 500]"
            small
            background
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </el-card>

        <el-card shadow="never" class="top-card schema-card">
          <template #header><div class="card-header"><span>数据可视化</span></div></template>
          <div ref="chartRef" class="chart-box"></div>
        </el-card>
      </div>
      <div class="top-area empty" v-else>
        <el-empty description="请选择左侧数据集" />
      </div>
    </section>

    <!-- Create Custom Dataset Drawer -->
    <el-drawer v-model="createDrawerVisible" title="新建自定义数据表" size="480px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="96px" size="small" label-position="top" style="padding: 0 20px;">
        <el-form-item label="数据表名称" prop="dataDesc">
          <el-input v-model="createForm.dataDesc" placeholder="例如：高压报警专项模板" />
        </el-form-item>
        <el-form-item label="关联数据模板" prop="templateId">
          <el-select v-model="createForm.templateId" style="width: 100%">
            <el-option v-for="tpl in templates" :key="tpl.templateId" :label="tpl.templateName" :value="tpl.templateId" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定设备实例" prop="deviceInstanceId">
          <el-select v-model="createForm.deviceInstanceId" style="width: 100%" filterable>
            <el-option v-for="ins in instances" :key="ins.instanceId" :label="ins.instanceName" :value="ins.instanceId" />
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
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef } from 'vue'
import axios from 'axios'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Folder, Document, Cpu } from '@element-plus/icons-vue'
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

const selectedDataset = ref<any>(null)
const selectedRecords = ref<any[]>([])
const recordPage = ref({ pageNo: 1, pageSize: 100, total: 0 })
const loadingRecords = ref(false)

const chartRef = ref<HTMLElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)

const createDrawerVisible = ref(false)
const createFormRef = ref<FormInstance>()
const templates = ref<any[]>([])
const createForm = ref({
  templateId: '',
  deviceInstanceId: '',
  dataDesc: ''
})
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

const fetchTreeData = async () => {
  loadingTree.value = true
  try {
    const [catRes, insRes, dsRes, modRes, tplRes] = await Promise.all([
      axios.get('/api/device/category/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/data/index/list'),
      axios.get('/api/device/model/list'),
      axios.get('/api/data/template/list')
    ])
    categories.value = catRes.data?.data || []
    instances.value = insRes.data?.data || []
    datasets.value = dsRes.data?.data || []
    
    const mMap: Record<string, any> = {}
    ;(modRes.data?.data || []).forEach((m: any) => mMap[m.modelId] = m)
    modelMap.value = mMap
    
    const tMap: Record<string, any> = {}
    ;(tplRes.data?.data || []).forEach((t: any) => tMap[t.templateId] = t)
    templateMap.value = tMap

    const rootNodes: TreeNode[] = []
    const instanceByCat: Record<string, any[]> = {}
    const uncategorized: any[] = []
    
    instances.value.forEach(ins => {
      const model = modelMap.value[ins.modelId]
      const catId = model?.categoryId
      if (catId) {
        if (!instanceByCat[catId]) instanceByCat[catId] = []
        instanceByCat[catId].push(ins)
      } else {
        uncategorized.push(ins)
      }
    })
    
    const dsByInstance: Record<string, any[]> = {}
    datasets.value.forEach(ds => {
      const insId = ds.deviceInstanceId
      if (!dsByInstance[insId]) dsByInstance[insId] = []
      dsByInstance[insId].push(ds)
    })

    categories.value.forEach(cat => {
      const insList = instanceByCat[cat.id] || []
      const catNode: TreeNode = {
        id: `cat_${cat.id}`,
        label: cat.categoryName,
        type: 'category',
        children: []
      }
      
      insList.forEach(ins => {
        const insNode: TreeNode = {
          id: `ins_${ins.instanceId}`,
          label: ins.instanceName,
          type: 'instance',
          children: []
        }
        const dsList = dsByInstance[ins.instanceId] || []
        dsList.forEach(ds => {
          insNode.children!.push({
            id: `ds_${ds.id}`,
            label: ds.dataDesc || ds.dataTable,
            type: 'dataset',
            data: ds
          })
        })
        catNode.children!.push(insNode)
      })
      
      if (catNode.children!.length > 0) {
        rootNodes.push(catNode)
      }
    })
    
    if (uncategorized.length > 0) {
      const uncatNode: TreeNode = {
        id: 'cat_none',
        label: '未分类',
        type: 'category',
        children: []
      }
      uncategorized.forEach(ins => {
        const insNode: TreeNode = {
          id: `ins_${ins.instanceId}`,
          label: ins.instanceName,
          type: 'instance',
          children: []
        }
        const dsList = dsByInstance[ins.instanceId] || []
        dsList.forEach(ds => {
          insNode.children!.push({
            id: `ds_${ds.id}`,
            label: ds.dataDesc || ds.dataTable,
            type: 'dataset',
            data: ds
          })
        })
        uncatNode.children!.push(insNode)
      })
      rootNodes.push(uncatNode)
    }

    treeData.value = rootNodes
  } catch (err: any) {
    ElMessage.error('加载树形结构失败')
  } finally {
    loadingTree.value = false
  }
}

const handleNodeClick = (node: TreeNode) => {
  if (node.type === 'dataset' && node.data) {
    selectedDataset.value = node.data
    recordPage.value.pageNo = 1
    fetchRecords()
  }
}

const selectedSchemaRows = computed(() => {
  if (!selectedDataset.value) return []
  const tplId = selectedDataset.value.dataTemplateId
  const tpl = templateMap.value[tplId]
  if (!tpl) return []
  const spec = tpl.dataSchemaSpec || {}
  return Object.keys(spec).map(k => ({
    key: k,
    label: spec[k]?.label || k,
    type: spec[k]?.type || 'string',
    unit: spec[k]?.unit || ''
  }))
})

const fetchRecords = async () => {
  if (!selectedDataset.value) return
  loadingRecords.value = true
  try {
    const res = await axios.get(`/api/data/record/dataset/${selectedDataset.value.id}`, {
      params: { pageNo: recordPage.value.pageNo, pageSize: recordPage.value.pageSize }
    })
    if (res.data?.success) {
      const page = res.data.data || {}
      selectedRecords.value = page.records || []
      recordPage.value.total = Number(page.total || 0)
      nextTick(() => {
        if (!chart.value && chartRef.value) chart.value = echarts.init(chartRef.value)
        renderChart()
      })
    }
  } catch (err: any) {
    ElMessage.error('加载记录失败')
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
  const recs = [...selectedRecords.value].sort((a, b) => new Date(a.collectTime).getTime() - new Date(b.collectTime).getTime())
  const timeList = recs.map(r => formatTime(r.collectTime))
  
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
      offset: idx > 1 ? Math.floor((idx - 1) / 2) * 45 : 0,
      axisLine: { show: true },
      splitLine: { show: idx === 0, lineStyle: { color: '#e5e7eb' } }
    })
    return idx
  }
  
  schemaRows.forEach(row => {
    const axisIdx = getAxisIdx(row.unit)
    const data = recs.map(r => {
      const val = r.dataPayload?.[row.key]
      const num = Number(val)
      return Number.isFinite(num) ? num : null
    })
    series.push({
      type: 'line',
      smooth: true,
      connectNulls: false,
      yAxisIndex: axisIdx,
      name: row.label,
      data
    })
  })
  
  chart.value.setOption({
    backgroundColor: '#ffffff',
    tooltip: { trigger: 'axis' },
    legend: { top: 8 },
    grid: { left: 56, right: (yAxis.length - 1) * 45 + 16, top: 54, bottom: 40 },
    xAxis: { type: 'category', data: timeList },
    yAxis,
    series
  }, true)
}

const onResize = () => chart.value?.resize()

const formatTime = (v?: string) => (v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '-')
const renderPayloadValue = (v: any) => (v === undefined || v === null || v === '' ? '-' : String(v))
const instanceNameById = (id: string) => {
  const ins = instances.value.find(v => v.instanceId == id)
  return ins?.instanceName || id
}

const openCreateDrawer = () => {
  createForm.value = { templateId: '', deviceInstanceId: '', dataDesc: '' }
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
        await fetchTreeData()
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
  window.open(`/api/data/record/export/${selectedDataset.value.id}`, '_blank')
}

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

.custom-tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #374151;
}

.custom-tree-node.is-dataset {
  color: #2563eb;
}

.custom-tree-node.is-active {
  font-weight: 600;
  color: #1d4ed8;
}

.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.top-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.top-area.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #d4d9e0;
  border-radius: 8px;
  background: #fff;
}

.top-card {
  border: 1px solid #d4d9e0;
  border-radius: 8px;
}

.records-card {
  flex: 3;
}

.schema-card {
  flex: 2;
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

.header-actions {
  display: flex;
  gap: 8px;
}

.chart-box {
  width: 100%;
  height: 100%;
  min-height: 260px;
}
</style>
