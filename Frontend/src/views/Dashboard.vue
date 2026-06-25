<template>
  <div class="dashboard-container">
    <div class="header-row">
      <h1 class="page-title">首页</h1>
      <el-tag type="info" class="time-tag">{{ currentTime }}</el-tag>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :md="6"><div class="stat-card"><span>设备总数</span><strong>{{ stats.totalDevices || 0 }}</strong></div></el-col>
      <el-col :xs="12" :md="6"><div class="stat-card"><span>在线设备</span><strong>{{ stats.onlineDevices || 0 }}</strong></div></el-col>
      <el-col :xs="12" :md="6"><div class="stat-card"><span>任务总数</span><strong>{{ stats.totalTasks || 0 }}</strong></div></el-col>
      <el-col :xs="12" :md="6"><div class="stat-card"><span>进行中任务</span><strong>{{ stats.runningTasks || 0 }}</strong></div></el-col>
    </el-row>

    <el-row :gutter="16" class="main-row">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card" header="实验室级约束">
          <el-form label-position="top">
            <el-form-item label="最大并发任务数">
              <el-input-number v-model="labConstraint.maxConcurrentTasks" :min="1" :max="100" style="width: 100%" />
            </el-form-item>
            <el-form-item label="允许夜间执行">
              <el-switch v-model="labConstraint.allowNightOperations" active-text="允许" inactive-text="禁止" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" style="width: 100%" @click="saveLabConstraint">保存约束</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="panel-card" header="实验室场景概览">
          <div class="scene-list">
            <div class="scene-item">数据模板：{{ stats.totalTemplates || 0 }}</div>
            <div class="scene-item">数据记录：{{ stats.totalDataRecords || 0 }}</div>
            <div class="scene-item">离线设备：{{ stats.offlineDevices || 0 }}</div>
            <div class="scene-item">失败任务：{{ stats.failedTasks || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="panel-card" header="进行中的任务">
          <el-table :data="runningTasks" size="small" border stripe>
            <el-table-column prop="taskId" label="任务编号" width="90" />
            <el-table-column prop="taskName" label="任务名称" min-width="140" />
            <el-table-column prop="templateId" label="流程" min-width="120" />
            <el-table-column prop="startTime" label="开始时间" min-width="140">
              <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="panel-card" header="设备实时状态">
          <el-row :gutter="12">
            <el-col :xs="24" :sm="12" :md="8" v-for="device in devices" :key="device.instanceId" style="margin-bottom: 12px;">
              <div class="device-card" @click="openDeviceDrawer(device)">
                <div class="device-name">{{ device.instanceName }}</div>
                <div class="device-meta">{{ getModelLabel(device.modelId) }}</div>
                <el-tag size="small" :type="device.isOnline ? 'success' : 'info'">{{ device.isOnline ? '在线' : '离线' }}</el-tag>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="deviceDrawerVisible" :title="`设备详情：${currentDevice?.instanceName || ''}`" size="520px">
      <div v-if="currentDevice">
        <el-descriptions :column="1" border size="small" class="mb-12">
          <el-descriptions-item label="设备实例">{{ currentDevice.instanceId }}</el-descriptions-item>
          <el-descriptions-item label="设备模型">{{ currentDevice.modelId }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ currentDevice.commConfig?.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="MQTT 主题">{{ currentDevice.commConfig?.mqttTopic || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">实时状态</el-divider>
        <el-descriptions :column="1" border size="small" class="mb-12">
          <el-descriptions-item label="指令状态">{{ liveSnapshot?.currentCommandState || '-' }}</el-descriptions-item>
          <el-descriptions-item label="功能状态">{{ liveSnapshot?.currentOperationState || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实时属性">
            <div v-if="liveSnapshot?.latestAttributes">
              <el-tag
                v-for="(val, key) in liveSnapshot.latestAttributes"
                :key="key"
                size="small"
                type="info"
                class="mr-8 mb-8"
              >{{ key }}: {{ val }}</el-tag>
            </div>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">设备约束</el-divider>
        <el-table :data="deviceConstraints" size="small" border>
          <el-table-column label="属性">
            <template #default="{ row }">
              <el-select v-model="row.targetAttr" style="width: 100%" placeholder="选择属性">
                <el-option v-for="attr in currentDeviceAttrs" :key="attr.identifier" :label="attr.name" :value="attr.identifier" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="条件" width="100">
            <template #default="{ row }">
              <el-select v-model="row.operator" style="width: 100%">
                <el-option label=">" value=">" />
                <el-option label="<" value="<" />
                <el-option label=">=" value=">=" />
                <el-option label="<=" value="<=" />
                <el-option label="=" value="==" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="阈值" width="120">
            <template #default="{ row }"><el-input v-model="row.threshold" /></template>
          </el-table-column>
          <el-table-column label="操作" width="64">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeConstraint($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="drawer-actions">
          <el-button @click="addConstraint">新增约束</el-button>
          <el-button type="primary" :loading="savingConstraint" @click="saveDeviceConstraint">保存约束</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const currentTime = ref('')
let timer: number | undefined

const stats = ref<any>({})
const devices = ref<any[]>([])
const runningTasks = ref<any[]>([])
const modelMap = ref<Record<string, any>>({})

const labConstraint = ref({
  maxConcurrentTasks: Number(localStorage.getItem('lab:maxConcurrentTasks') || 10),
  allowNightOperations: localStorage.getItem('lab:allowNightOperations') === 'true'
})

const deviceDrawerVisible = ref(false)
const currentDevice = ref<any>(null)
const liveSnapshot = ref<any>(null)
const deviceConstraints = ref<Array<{ targetAttr: string; operator: string; threshold: string }>>([])
const savingConstraint = ref(false)

const currentDeviceAttrs = computed(() => {
  if (!currentDevice.value?.modelId) return []
  return modelMap.value[currentDevice.value.modelId]?.capabilitySpec?.attributes || []
})

const updateTime = () => {
  currentTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

const formatTime = (v?: string) => (v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '-')

const getModelLabel = (modelId: string) => {
  const model = modelMap.value[modelId]
  return model ? model.modelName : modelId
}

const fetchData = async () => {
  try {
    const [deviceRes, modelRes, taskSummaryRes, runningTaskRes, templateRes, dataIndexRes] = await Promise.all([
      axios.get('/api/device/instance/list'),
      axios.get('/api/device/model/list'),
      axios.get('/api/task/summary'),
      axios.get('/api/task/page', { params: { pageNo: 1, pageSize: 10, status: 'RUNNING' } }),
      axios.get('/api/data/template/list'),
      axios.get('/api/data/index/list')
    ])
    const responses = [deviceRes, modelRes, taskSummaryRes, runningTaskRes, templateRes, dataIndexRes]
    const failed = responses.find(res => !res.data?.success)
    if (failed) {
      ElMessage.error(failed.data?.message || '加载首页数据失败')
      return
    }
    const deviceList = deviceRes.data.data || []
    const modelList = modelRes.data.data || []
    const taskSummary = taskSummaryRes.data.data || {}
    const runningTaskPage = runningTaskRes.data.data || {}
    const templateList = templateRes.data.data || []
    const dataIndexList = dataIndexRes.data.data || []

    const onlineDevices = deviceList.filter((device: any) => device.isOnline || device.onlineStatus === 'ONLINE').length
    stats.value = {
      totalDevices: deviceList.length,
      onlineDevices,
      offlineDevices: Math.max(0, deviceList.length - onlineDevices),
      totalTasks: taskSummary.total || 0,
      runningTasks: taskSummary.running || 0,
      failedTasks: taskSummary.failed || 0,
      totalTemplates: templateList.length,
      totalDataRecords: dataIndexList.length
    }
    devices.value = deviceList
    runningTasks.value = runningTaskPage.records || []
    const map: Record<string, any> = {}
    modelList.forEach((m: any) => (map[m.modelId] = m))
    modelMap.value = map
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载首页数据失败')
  }
}

const saveLabConstraint = () => {
  localStorage.setItem('lab:maxConcurrentTasks', String(labConstraint.value.maxConcurrentTasks))
  localStorage.setItem('lab:allowNightOperations', String(labConstraint.value.allowNightOperations))
  ElMessage.success('实验室约束已保存')
}

const openDeviceDrawer = async (device: any) => {
  currentDevice.value = JSON.parse(JSON.stringify(device))
  const constraints = currentDevice.value?.commConfig?.constraints
  deviceConstraints.value = Array.isArray(constraints) ? constraints : []
  liveSnapshot.value = null
  deviceDrawerVisible.value = true
  try {
    const res = await axios.get(`/api/device/instance/snapshot/${device.instanceId}`)
    if (res.data?.success) liveSnapshot.value = res.data.data
  } catch {}
}

const addConstraint = () => {
  deviceConstraints.value.push({ targetAttr: '', operator: '>', threshold: '' })
}

const removeConstraint = (idx: number) => {
  deviceConstraints.value.splice(idx, 1)
}

const saveDeviceConstraint = async () => {
  if (!currentDevice.value) return
  savingConstraint.value = true
  try {
    const payload = JSON.parse(JSON.stringify(currentDevice.value))
    if (!payload.commConfig) payload.commConfig = {}
    payload.commConfig.constraints = deviceConstraints.value.filter(c => c.targetAttr && c.threshold)
    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('设备约束已保存')
      await fetchData()
      deviceDrawerVisible.value = false
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '保存失败')
  } finally {
    savingConstraint.value = false
  }
}

onMounted(() => {
  updateTime()
  timer = window.setInterval(updateTime, 1000)
  fetchData()
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.dashboard-container { padding: 16px; background: #f2f4f7; min-height: calc(100vh - 52px); }
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-title { margin: 0; font-size: 20px; color: #1f2937; }
.time-tag { font-family: 'JetBrains Mono', monospace; }
.stats-row { margin-bottom: 12px; }
.stat-card { background: #fff; border: 1px solid #d8dce3; border-radius: 6px; padding: 12px; display: flex; flex-direction: column; gap: 4px; }
.stat-card span { font-size: 12px; color: #6b7280; }
.stat-card strong { font-size: 22px; color: #111827; }
.main-row { margin-top: 0; }
.panel-card { border: 1px solid #d8dce3; margin-bottom: 12px; }
.scene-list { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.scene-item { background: #f6f7f9; border: 1px solid #e5e7eb; border-radius: 4px; padding: 8px; font-size: 12px; color: #374151; }
.device-card { border: 1px solid #d8dce3; background: #fff; border-radius: 6px; padding: 10px; cursor: pointer; display: flex; flex-direction: column; gap: 4px; }
.device-card:hover { border-color: #9aa4b2; }
.device-name { font-size: 13px; color: #1f2937; font-weight: 600; }
.device-meta { font-size: 12px; color: #6b7280; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.mb-12 { margin-bottom: 12px; }
.mr-8 { margin-right: 8px; }
</style>
