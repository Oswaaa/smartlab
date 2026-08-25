<template>
  <div class="tab-pane-content">
    <div class="sub-section-head">
      <span class="head-title">归档遥测数据集</span>
      <span class="head-desc">已为该设备实例创建的时序存储与采样数据表</span>
      <router-link :to="datacenterRedirectUrl" class="datacenter-link">前往数据中心查看历史数据 ➔</router-link>
    </div>
    <div v-if="canCreate" class="dataset-create-toolbar">
      <div class="dataset-form-inline">
        <el-select v-model="datasetCreateForm.templateId" size="small" filterable clearable placeholder="选择关联数据模板" style="width: 220px;">
          <el-option v-for="tpl in availableDataTemplates" :key="templateIdOf(tpl)" :label="tpl.templateName || '未命名模板'" :value="templateIdOf(tpl)" />
        </el-select>
        <el-input v-model="datasetCreateForm.dataDesc" size="small" placeholder="数据集描述（例如：1号反应釜温度采样表）" style="flex: 1; max-width: 380px;" />
        <button class="btn-aliyun" type="button" :disabled="creatingDataSet || !datasetCreateForm.templateId" @click="createDataSetForInstance">
          <el-icon><Plus /></el-icon><span>关联建表</span>
        </button>
      </div>
      <button class="btn-aliyun" type="button" @click="loadDataSets">
        <el-icon><Refresh /></el-icon><span>刷新</span>
      </button>
    </div>
    <el-table :data="instanceDataSets" stripe border size="small" v-loading="loadingDataSets">
      <el-table-column prop="id" label="数据集 ID" width="100">
        <template #default="{ row }"><code class="mono">{{ row.id }}</code></template>
      </el-table-column>
      <el-table-column prop="dataDesc" label="数据集描述" min-width="180" />
      <el-table-column prop="dataTable" label="物理底层表名" min-width="200">
        <template #default="{ row }"><code class="mono">{{ row.dataTable }}</code></template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160">
        <template #default="{ row }"><span class="mono">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</span></template>
      </el-table-column>
      <el-table-column label="操作" :width="canDelete ? 160 : 90" align="center" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <router-link class="btn-link" :to="datasetRedirectUrl(row)">查看数据表</router-link>
            <el-popconfirm v-if="canDelete" title="确认注销该归档数据表？物理存储将被清空。" @confirm="deleteInstanceDataSet(row)">
              <template #reference>
                <button class="btn-link danger" type="button">删除</button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as api from './deviceInstanceApi'
import { templateIdOf } from './normalizers'
import type { DeviceInstance } from './types'

const props = defineProps<{
  instance: DeviceInstance
  canCreate: boolean
  canDelete: boolean
  retired: boolean
}>()

const instanceDataSets = ref<any[]>([])
const loadingDataSets = ref(false)
const dataTemplates = ref<any[]>([])
const creatingDataSet = ref(false)
const datasetCreateForm = ref({ templateId: '', dataDesc: '' })

const datacenterRedirectUrl = computed(() => {
  const id = props.instance?.instanceId
  return id ? `/data-management?instanceId=${id}` : '/data-management'
})

function datasetRedirectUrl(row: any) {
  const instanceId = props.instance?.instanceId
  const datasetId = row?.id || row?.dataIndexId
  if (datasetId && instanceId) return `/data-management?instanceId=${instanceId}&datasetId=${datasetId}`
  if (instanceId) return `/data-management?instanceId=${instanceId}`
  return '/data-management'
}

const availableDataTemplates = computed(() => {
  const modelId = props.instance.modelId
  return dataTemplates.value.filter(tpl => !tpl.deviceModelId || !modelId || String(tpl.deviceModelId) === String(modelId))
})

const loadDataSets = async () => {
  const id = props.instance?.instanceId
  if (!id) {
    instanceDataSets.value = []
    return
  }
  loadingDataSets.value = true
  try {
    const data = await api.listDatasets(id)
    if (props.instance?.instanceId !== id) return
    if (data?.success) instanceDataSets.value = data.data || []
  } catch (err: any) {
    if (props.instance?.instanceId !== id) return
    ElMessage.error(err.response?.data?.message || '加载数据集失败')
  } finally {
    if (props.instance?.instanceId === id) loadingDataSets.value = false
  }
}

const loadTemplates = async () => {
  try {
    const data = await api.listDataTemplates()
    if (data?.success) dataTemplates.value = data.data || []
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载数据模板失败')
  }
}

watch(() => props.instance?.instanceId, (instanceId) => {
  if (!instanceId) {
    instanceDataSets.value = []
    return
  }
  loadDataSets()
  loadTemplates()
}, { immediate: true })

const createDataSetForInstance = async () => {
  if (props.retired) return
  if (!datasetCreateForm.value.templateId) {
    ElMessage.warning('请选择数据模板')
    return
  }
  creatingDataSet.value = true
  try {
    const template = dataTemplates.value.find(tpl => String(templateIdOf(tpl)) === String(datasetCreateForm.value.templateId))
    const data = await api.createDataset({
      templateId: datasetCreateForm.value.templateId,
      deviceInstanceId: props.instance.instanceId,
      dataDesc: datasetCreateForm.value.dataDesc || (props.instance.instanceName + ' - ' + (template?.templateName || '自定义数据表'))
    })
    if (data?.success) {
      ElMessage.success('数据表创建成功')
      datasetCreateForm.value = { templateId: '', dataDesc: '' }
      await loadDataSets()
    } else {
      ElMessage.error(data?.message || '创建失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '创建失败')
  } finally {
    creatingDataSet.value = false
  }
}

const deleteInstanceDataSet = async (row: any) => {
  const id = row?.id || row?.dataIndexId
  if (!id) return
  try {
    const data = await api.deleteDataset(id)
    if (data?.success) {
      ElMessage.success('数据表已删除')
      await loadDataSets()
    } else {
      ElMessage.error(data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
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
.sub-section-head .head-desc { font-size: 11px; color: var(--sl-text-secondary); margin-right: auto; }
.datacenter-link {
  color: var(--sl-primary);
  text-decoration: none;
  font-weight: 500;
  font-size: 12px;
  flex-shrink: 0;
}
.datacenter-link:hover { text-decoration: underline; }
.row-actions { display: inline-flex; align-items: center; justify-content: center; gap: 8px; }
.dataset-create-toolbar {
  padding: 8px 12px;
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.dataset-form-inline { display: flex; align-items: center; gap: 8px; flex: 1; }
.mono { font-family: var(--sl-font-mono); }
</style>
