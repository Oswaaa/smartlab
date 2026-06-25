
<template>
  <div class="instance-page">
    <el-container class="layout">
      <el-aside width="280px" class="sidebar">
        <div class="sidebar-header">
          <div class="header-title">
            <el-icon><Cpu /></el-icon>
            <span>设备模型</span>
          </div>
        </div>
        <el-scrollbar class="sidebar-scroll" v-loading="modelsLoading">
          <div class="model-list">
            <div :class="['model-item', { active: selectedModelId === '' }]" @click="selectModel('')">
              <div class="model-name">全部模型</div>
              <div class="model-meta">显示全部设备实例</div>
            </div>
            <div
              v-for="model in models"
              :key="model.modelId"
              :class="['model-item', { active: selectedModelId === model.modelId }]"
              @click="selectModel(model.modelId)"
            >
              <div class="model-name">{{ model.modelName }}</div>
              <div class="model-meta">编号: {{ model.modelId }}</div>
            </div>
          </div>
        </el-scrollbar>
      </el-aside>

      <el-main class="content">
        <div class="main-header">
          <div>
            <h2>{{ selectedModelName }}</h2>
            <span class="subtitle">设备实例查看与配置</span>
          </div>
          <div class="actions">
            <el-input
              v-model="instanceKeyword"
              class="instance-search"
              size="small"
              clearable
              placeholder="搜索实例或设备SN"
              @input="onInstanceSearchInput"
            />
            <el-button :icon="Refresh" circle size="small" @click="loadData" title="刷新" />
            <el-button type="primary" size="small" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 添加设备
            </el-button>
          </div>
        </div>

        <div class="stats-bar">
          <div class="stat-item">
            <span>总数</span>
            <strong>{{ stats.total }}</strong>
          </div>
          <div class="stat-item">
            <span>在线</span>
            <strong>{{ stats.online }}</strong>
          </div>
          <div class="stat-item">
            <span>离线</span>
            <strong>{{ stats.offline }}</strong>
          </div>
          <div class="stat-item">
            <span>当前列表</span>
            <strong>{{ instances.length }}</strong>
          </div>
        </div>

        <el-scrollbar class="card-scroll" v-loading="loading">
          <div v-if="instances.length > 0" class="card-grid">
            <div v-for="instance in instances" :key="instance.instanceId" class="instance-card" @click="viewDetails(instance)">
              <div class="card-head">
                <span class="name">{{ instance.instanceName }}</span>
                <el-tag :type="instance.isOnline ? 'success' : 'info'" size="small">{{ instance.isOnline ? '在线' : '离线' }}</el-tag>
              </div>
              <div class="line"><span>实例编号</span><span class="mono">{{ instance.instanceId }}</span></div>
              <div class="line"><span>模型</span><span>{{ getModelName(instance.modelId) }}</span></div>
              <div class="line"><span>设备SN</span><span class="mono">{{ instance.commConfig?.deviceSn || '-' }}</span></div>
              <div class="line"><span>实验室</span><span class="mono">{{ instance.commConfig?.labId || 'lab1' }}</span></div>
              <div class="line"><span>MQTT主题</span><span class="mono">{{ instance.commConfig?.mqttTopic || '-' }}</span></div>
            </div>
          </div>
          <div v-else class="empty-wrap">
            <el-empty description="暂无设备实例" :image-size="100" />
          </div>
        </el-scrollbar>
        <div class="instance-pagination">
          <el-pagination
            v-model:current-page="instancePageNo"
            v-model:page-size="instancePageSize"
            :page-sizes="[12, 24, 48, 96]"
            :total="instanceTotal"
            layout="total, sizes, prev, pager, next"
            background
            small
            @size-change="handleInstancePageSizeChange"
            @current-change="loadInstances"
          />
        </div>
      </el-main>
    </el-container>

    <el-drawer
      v-model="drawerVisible"
      :title="`设备实例: ${activeInstance?.instanceName || ''}`"
      size="560px"
      :destroy-on-close="true"
      @close="closeDrawer"
    >
      <div v-if="activeInstance" class="drawer-body">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基础配置" name="info">
            <el-form label-position="left" label-width="120px" size="small" class="density-form">
              <el-form-item label="实例编号">
                <el-input v-model="activeInstance.instanceId" disabled />
              </el-form-item>
              <el-form-item label="实例名称">
                <el-input v-model="activeInstance.instanceName" />
              </el-form-item>
              <el-form-item label="设备模型">
                <el-select v-model="activeInstance.modelId" style="width: 100%" @change="onModelChangeInDrawer">
                  <el-option v-for="m in models" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                </el-select>
              </el-form-item>
              <el-form-item label="租户">
                <el-input v-model="activeInstance.commConfig.tenantId" placeholder="default" />
              </el-form-item>
              <el-form-item label="实验室">
                <el-input v-model="activeInstance.commConfig.labId" placeholder="lab1" />
              </el-form-item>
              <el-form-item label="设备类型">
                <el-input v-model="activeInstance.commConfig.deviceType" />
              </el-form-item>
              <el-form-item label="设备SN">
                <el-input v-model="activeInstance.commConfig.deviceSn" />
              </el-form-item>
              <el-form-item label="MQTT主题">
                <el-input v-model="activeInstance.commConfig.mqttTopic" />
              </el-form-item>
              <el-form-item label="在线状态">
                <el-switch v-model="activeInstance.isOnline" active-text="在线" inactive-text="离线" />
              </el-form-item>
            </el-form>

            <div class="footer-actions">
              <el-popconfirm title="确认删除该设备实例？" @confirm="deleteInstance(activeInstance.instanceId)">
                <template #reference>
                  <el-button type="danger" plain size="small">删除设备</el-button>
                </template>
              </el-popconfirm>
              <el-button type="primary" size="small" :loading="saving" @click="saveInstance">保存</el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane label="手动控制" name="control">
            <el-alert
              title="手动控制会走同一条状态机与 Adapter/MQTT 链路，适合设备接入验证。"
              type="info"
              show-icon
              :closable="false"
              class="mb-12"
            />
            <el-form label-position="left" label-width="110px" size="small">
              <el-form-item label="设备能力">
                <el-select v-model="controlCommandId" style="width: 100%" placeholder="选择命令">
                  <el-option
                    v-for="cmd in activeInstanceCommands"
                    :key="cmd.commandId"
                    :label="`${cmd.commandName || cmd.commandId}（${cmd.commandId}）`"
                    :value="cmd.commandId"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="参数 JSON">
                <el-input
                  v-model="controlParamsText"
                  type="textarea"
                  :rows="8"
                  class="mono-textarea"
                  placeholder='{"target": 80}'
                />
              </el-form-item>
            </el-form>
            <div class="footer-actions">
              <el-button type="primary" size="small" :loading="sendingControl" @click="sendManualCommand">
                发送指令
              </el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane label="实时状态" name="status">
            <div class="status-top">
              <el-tag type="success" size="small">状态扫描中（每 3 秒刷新）</el-tag>
              <span>更新时间: {{ lastSnapshotTime }}</span>
            </div>
            <el-descriptions :column="1" border size="small" class="mb-12">
              <el-descriptions-item label="指令状态">{{ snapshot?.currentCommandState || '-' }}</el-descriptions-item>
              <el-descriptions-item label="功能状态">{{ snapshot?.currentOperationState || '-' }}</el-descriptions-item>
            </el-descriptions>
            <el-card shadow="never">
              <template #header>属性快照</template>
              <div v-if="Object.keys(snapshotAttributes).length > 0" class="attr-list">
                <div v-for="(val, key) in snapshotAttributes" :key="key" class="attr-row">
                  <span>{{ getAttributeName(key) }}（{{ key }}）</span>
                  <span class="mono">{{ val }}</span>
                </div>
              </div>
              <div v-else class="empty-inline">暂无数据</div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="局部约束" name="constraints">
            <el-alert
              title="为当前设备实例设置局部安全约束。超出阈值时，系统会执行告警或熔断。"
              type="warning"
              show-icon
              :closable="false"
              class="mb-12"
            />
            <div class="constraint-actions">
              <el-button type="primary" plain size="small" @click="addConstraint">
                <el-icon><Plus /></el-icon> 新增约束
              </el-button>
            </div>
            <el-table :data="localConstraints" border size="small">
              <el-table-column label="监控属性">
                <template #default="{ row }">
                  <el-select v-model="row.targetAttr" size="small" style="width: 100%" placeholder="选择属性">
                    <el-option
                      v-for="prop in getModelAttributes(activeInstance.modelId)"
                      :key="prop.identifier"
                      :label="prop.name"
                      :value="prop.identifier"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="条件" width="100">
                <template #default="{ row }">
                  <el-select v-model="row.operator" size="small">
                    <el-option label=">" value=">" />
                    <el-option label=">=" value=">=" />
                    <el-option label="<" value="<" />
                    <el-option label="<=" value="<=" />
                    <el-option label="=" value="==" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="阈值" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.threshold" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeConstraint($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="footer-actions mt-12">
              <el-button type="primary" size="small" :loading="saving" @click="saveConstraints">保存约束</el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="createDialogVisible" title="添加设备" width="520px" :destroy-on-close="true">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="110px" label-position="left" size="small">
        <el-form-item label="设备名称" prop="instanceName">
          <el-input v-model="createForm.instanceName" />
        </el-form-item>
        <el-form-item label="设备模型" prop="modelId">
          <el-select
            v-model="createForm.modelId"
            style="width: 100%"
            placeholder="搜索并选择设备模型"
            filterable
            remote
            reserve-keyword
            :remote-method="searchModels"
            :loading="modelSearchLoading"
            @change="onModelChangeInCreate"
          >
            <el-option v-for="m in modelOptions" :key="m.modelId" :label="`${m.modelName}（${m.modelId}）`" :value="m.modelId" />
          </el-select>
        </el-form-item>
        <el-form-item label="租户">
          <el-input v-model="createForm.tenantId" placeholder="default" />
        </el-form-item>
        <el-form-item label="实验室">
          <el-input v-model="createForm.labId" placeholder="lab1" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-input v-model="createForm.deviceType" />
        </el-form-item>
        <el-form-item label="设备SN" prop="deviceSn" v-if="showDeviceSnInput">
          <el-input v-model="createForm.deviceSn" :placeholder="deviceSnPlaceholder" />
        </el-form-item>
        <el-form-item label="设备SN" v-else>
          <el-input v-model="createForm.deviceSn" placeholder="例如: plc0001" />
        </el-form-item>
        <el-form-item label="命令主题预览">
          <el-input v-model="createForm.mqttTopicPreview" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="createDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="creating" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { Cpu, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import axios from 'axios'

interface DeviceModel {
  modelId: string
  modelName: string
  deviceCategory: string
  capabilitySpec: any
}

interface DeviceInstance {
  instanceId: string
  modelId: string
  stateMachineId: string
  instanceName: string
  commConfig: {
    deviceSn?: string
    tenantId?: string
    labId?: string
    deviceType?: string
    mqttTopic?: string
    constraints?: Array<{ targetAttr: string; operator: string; threshold: string }>
  }
  isOnline: boolean
}

interface DeviceSnapshot {
  instanceId: string
  currentCommandState?: string
  currentOperationState?: string
  latestAttributes?: Record<string, any>
}

const models = ref<DeviceModel[]>([])
const modelOptions = ref<DeviceModel[]>([])
const instances = ref<DeviceInstance[]>([])
const selectedModelId = ref('')
const instanceKeyword = ref('')
const loading = ref(false)
const modelsLoading = ref(false)
const modelSearchLoading = ref(false)
const saving = ref(false)
const creating = ref(false)
const sendingControl = ref(false)

const drawerVisible = ref(false)
const activeInstance = ref<DeviceInstance | null>(null)
const activeTab = ref('info')

const snapshot = ref<DeviceSnapshot | null>(null)
const lastSnapshotTime = ref('-')
let pollingTimer: any = null
const instancePageNo = ref(1)
const instancePageSize = ref(24)
const instanceTotal = ref(0)
const instanceStats = ref({ total: 0, online: 0, offline: 0 })
let modelSearchTimer: any = null
let instanceLoadSeq = 0
let instanceSearchTimer: any = null

const localConstraints = ref<Array<{ targetAttr: string; operator: string; threshold: string }>>([])
const controlCommandId = ref('')
const controlParamsText = ref('{}')

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({
  instanceName: '',
  modelId: '',
  tenantId: 'default',
  labId: 'lab1',
  deviceType: '',
  deviceSn: '',
  mqttTopicPreview: ''
})

const createRules = ref<FormRules>({
  instanceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  modelId: [{ required: true, message: '请选择设备模型', trigger: 'change' }],
  deviceSn: [{ required: true, message: '请输入设备SN', trigger: 'blur' }]
})

const selectedModelName = computed(() => {
  if (!selectedModelId.value) return '全部设备实例'
  const model = models.value.find(m => m.modelId === selectedModelId.value)
  return model ? `${model.modelName}（编号: ${model.modelId}）` : '未知模型'
})

const stats = computed(() => {
  return {
    total: instanceStats.value.total,
    online: instanceStats.value.online,
    offline: instanceStats.value.offline
  }
})

const snapshotAttributes = computed(() => snapshot.value?.latestAttributes || {})

const selectedCreateModel = computed(() => findModelById(createForm.value.modelId))
const activeInstanceModel = computed(() => activeInstance.value ? findModelById(activeInstance.value.modelId) : null)
const activeInstanceCommands = computed(() => {
  const commands = activeInstanceModel.value?.capabilitySpec?.adapterContract?.commands
  return Array.isArray(commands) ? commands : []
})

const mqttTemplate = computed(() => {
  const model = selectedCreateModel.value
  const topics = model?.capabilitySpec?.adapterContract?.mqttTopics
  const defaultCommand = model?.capabilitySpec?.protocol?.defaultTopics?.command
  if (defaultCommand) return defaultCommand
  if (!Array.isArray(topics)) return defaultTopicTemplate.value
  const commandTopic = topics.find((t: any) => t.direction === 'pub' && String(t.topic || '').includes('/cmd'))
  return commandTopic?.topic || topics[0]?.topic || defaultTopicTemplate.value
})

const defaultTopicTemplate = computed(() => 'smartlab/v1/${tenantId}/${labId}/${deviceType}/${modelId}/${deviceSn}/cmd/${commandId}')

const hasDevicePlaceholder = computed(() => {
  const tpl = mqttTemplate.value
  return tpl.includes('${deviceSn}') || tpl.includes('{deviceSn}')
})

const showDeviceSnInput = computed(() => !!createForm.value.modelId)

const deviceSnPlaceholder = computed(() => {
  if (hasDevicePlaceholder.value) {
    return '该模型主题包含设备SN占位符，请填写真实设备SN'
  }
  return '请输入真实设备SN'
})

const loadData = async () => {
  modelsLoading.value = true
  try {
    await Promise.all([loadSidebarModels(), loadModelOptions()])
    await loadInstances()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备数据失败')
  } finally {
    modelsLoading.value = false
  }
}

const loadSidebarModels = async () => {
  const res = await axios.get('/api/device/model/page', {
    params: {
      pageNo: 1,
      pageSize: 100
    }
  })
  if (res.data?.success) {
    models.value = res.data.data?.records || []
  }
}

const loadModelOptions = async (keyword = '') => {
  const res = await axios.get('/api/device/model/page', {
    params: {
      pageNo: 1,
      pageSize: 100,
      keyword: keyword.trim() || undefined
    }
  })
  if (res.data?.success) {
    const records = res.data.data?.records || []
    const selected = findModelById(createForm.value.modelId)
    modelOptions.value = selected && !records.some((v: DeviceModel) => v.modelId === selected.modelId)
      ? [selected, ...records]
      : records
  }
}

const searchModels = (keyword: string) => {
  if (modelSearchTimer) window.clearTimeout(modelSearchTimer)
  modelSearchTimer = window.setTimeout(async () => {
    modelSearchLoading.value = true
    try {
      await loadModelOptions(keyword)
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '搜索设备模型失败')
    } finally {
      modelSearchLoading.value = false
    }
  }, 250)
}

const loadInstances = async () => {
  const seq = ++instanceLoadSeq
  loading.value = true
  try {
    const [instancesRes, summaryRes] = await Promise.all([
      axios.get('/api/device/instance/page', {
        params: {
          pageNo: instancePageNo.value,
          pageSize: instancePageSize.value,
          modelId: selectedModelId.value || undefined,
          keyword: instanceKeyword.value.trim() || undefined
        }
      }),
      axios.get('/api/device/instance/summary', {
        params: {
          modelId: selectedModelId.value || undefined
        }
      })
    ])
    if (seq !== instanceLoadSeq) return
    if (instancesRes.data?.success) {
      const pageData = instancesRes.data.data || {}
      instances.value = pageData.records || []
      instanceTotal.value = pageData.total || 0
    }
    if (summaryRes.data?.success) {
      instanceStats.value = {
        total: summaryRes.data.data?.total || 0,
        online: summaryRes.data.data?.online || 0,
        offline: summaryRes.data.data?.offline || 0
      }
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备实例失败')
  } finally {
    if (seq === instanceLoadSeq) loading.value = false
  }
}

const selectModel = (id: string) => {
  selectedModelId.value = id
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

const handleInstancePageSizeChange = (size: number) => {
  instancePageSize.value = size
  instancePageNo.value = 1
  loadInstances()
}

const getModelName = (modelId: string) => {
  const model = findModelById(modelId)
  return model ? model.modelName : modelId
}

const getModelAttributes = (modelId: string) => {
  const model = findModelById(modelId)
  return model?.capabilitySpec?.attributes || []
}

const getAttributeName = (key: string) => {
  if (!activeInstance.value) return key
  const attr = getModelAttributes(activeInstance.value.modelId).find((v: any) => v.identifier === key)
  return attr?.name || key
}

const viewDetails = (instance: DeviceInstance) => {
  activeInstance.value = JSON.parse(JSON.stringify(instance))
  if (!activeInstance.value.commConfig) {
    activeInstance.value.commConfig = {}
  }
  activeInstance.value.commConfig.tenantId ||= 'default'
  activeInstance.value.commConfig.labId ||= 'lab1'
  activeInstance.value.commConfig.deviceType ||= getModelCategory(activeInstance.value.modelId)
  localConstraints.value = JSON.parse(JSON.stringify(activeInstance.value.commConfig.constraints || []))
  controlCommandId.value = activeInstanceCommands.value[0]?.commandId || ''
  controlParamsText.value = '{}'
  activeTab.value = 'info'
  drawerVisible.value = true
}

const onModelChangeInDrawer = (modelId: string) => {
  if (!activeInstance.value) return
  activeInstance.value.stateMachineId = `${modelId}StateMachine`
  activeInstance.value.commConfig.deviceType = getModelCategory(modelId)
}

const saveInstance = async () => {
  if (!activeInstance.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    payload.commConfig.constraints = localConstraints.value
    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('保存成功')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const deleteInstance = async (id: string) => {
  try {
    const res = await axios.delete(`/api/device/instance/delete/${id}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

const addConstraint = () => {
  localConstraints.value.push({ targetAttr: '', operator: '>', threshold: '' })
}

const removeConstraint = (index: number) => {
  localConstraints.value.splice(index, 1)
}

const saveConstraints = async () => {
  if (!activeInstance.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    payload.commConfig.constraints = localConstraints.value.filter(v => v.targetAttr && v.threshold)
    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('约束保存成功')
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const fetchSnapshot = async () => {
  if (!activeInstance.value) return
  try {
    const res = await axios.get(`/api/device/instance/snapshot/${activeInstance.value.instanceId}`)
    if (res.data?.success) {
      snapshot.value = res.data.data || null
      lastSnapshotTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    }
  } catch {
    snapshot.value = null
  }
}

const startPolling = () => {
  stopPolling()
  fetchSnapshot()
  pollingTimer = setInterval(fetchSnapshot, 3000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const closeDrawer = () => {
  stopPolling()
  snapshot.value = null
  activeInstance.value = null
  controlCommandId.value = ''
  controlParamsText.value = '{}'
}

watch(activeTab, (tab) => {
  if (tab === 'status') {
    startPolling()
  } else {
    stopPolling()
  }
})

const renderMqttTopic = (template: string, deviceSn: string) => {
  return template
    .replaceAll('{deviceSn}', deviceSn)
    .replaceAll('${deviceSn}', deviceSn)
    .replaceAll('${tenantId}', createForm.value.tenantId || 'default')
    .replaceAll('${labId}', createForm.value.labId || 'lab1')
    .replaceAll('${deviceType}', createForm.value.deviceType || selectedCreateModel.value?.deviceCategory || 'DEVICE')
    .replaceAll('${modelId}', createForm.value.modelId || '{modelId}')
    .replaceAll('${commandId}', '{commandId}')
}

const onModelChangeInCreate = () => {
  const deviceSn = createForm.value.deviceSn || ''
  createForm.value.deviceType = selectedCreateModel.value?.deviceCategory || ''
  if (mqttTemplate.value) {
    createForm.value.mqttTopicPreview = renderMqttTopic(mqttTemplate.value, deviceSn || '{deviceSn}')
  } else {
    createForm.value.mqttTopicPreview = ''
  }
}

watch(() => createForm.value.deviceSn, (val) => {
  if (!mqttTemplate.value) {
    createForm.value.mqttTopicPreview = ''
    return
  }
  createForm.value.mqttTopicPreview = renderMqttTopic(mqttTemplate.value, val || '{deviceSn}')
})

watch(() => [createForm.value.tenantId, createForm.value.labId, createForm.value.deviceType, createForm.value.modelId], () => {
  if (mqttTemplate.value) {
    createForm.value.mqttTopicPreview = renderMqttTopic(mqttTemplate.value, createForm.value.deviceSn || '{deviceSn}')
  }
})

const openCreateDialog = () => {
  createForm.value = {
    instanceName: '',
    modelId: '',
    tenantId: 'default',
    labId: 'lab1',
    deviceType: '',
    deviceSn: '',
    mqttTopicPreview: ''
  }
  createDialogVisible.value = true
  if (!models.value.length) {
    searchModels('')
  }
}

const submitCreate = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      const mqttTopic = mqttTemplate.value
        ? renderMqttTopic(mqttTemplate.value, createForm.value.deviceSn)
        : createForm.value.mqttTopicPreview

      const payload: DeviceInstance = {
        instanceId: '',
        modelId: createForm.value.modelId,
        stateMachineId: `${createForm.value.modelId}StateMachine`,
        instanceName: createForm.value.instanceName,
        commConfig: {
          tenantId: createForm.value.tenantId || 'default',
          labId: createForm.value.labId || 'lab1',
          deviceType: createForm.value.deviceType || selectedCreateModel.value?.deviceCategory || 'DEVICE',
          deviceSn: createForm.value.deviceSn,
          mqttTopic,
          constraints: []
        },
        isOnline: true
      }

      const res = await axios.post('/api/device/instance/save', payload)
      if (res.data?.success) {
        ElMessage.success('设备添加成功')
        createDialogVisible.value = false
        await loadData()
      } else {
        ElMessage.error(res.data?.message || '添加失败')
      }
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '添加失败')
    } finally {
      creating.value = false
    }
  })
}

const sendManualCommand = async () => {
  if (!activeInstance.value) return
  if (!controlCommandId.value) {
    ElMessage.warning('请选择命令')
    return
  }
  let parameters: Record<string, any> = {}
  try {
    parameters = controlParamsText.value.trim() ? JSON.parse(controlParamsText.value) : {}
  } catch {
    ElMessage.warning('参数 JSON 格式错误')
    return
  }
  sendingControl.value = true
  try {
    const res = await axios.post(`/api/device/instance/control/${activeInstance.value.instanceId}`, {
      commandId: controlCommandId.value,
      parameters
    })
    if (res.data?.success) {
      ElMessage.success('指令已发送')
      await fetchSnapshot()
    } else {
      ElMessage.error(res.data?.message || '指令发送失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '指令发送失败')
  } finally {
    sendingControl.value = false
  }
}

const getModelCategory = (modelId: string) => {
  return findModelById(modelId)?.deviceCategory || 'DEVICE'
}

function findModelById(modelId: string) {
  if (!modelId) return null
  return modelOptions.value.find(v => v.modelId === modelId) || models.value.find(v => v.modelId === modelId) || null
}

onMounted(loadData)
onUnmounted(() => {
  stopPolling()
  if (modelSearchTimer) window.clearTimeout(modelSearchTimer)
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
})
</script>

<style scoped>
.instance-page {
  height: calc(100vh - 52px);
  background: #f3f4f6;
}

.layout { height: 100%; }

.sidebar {
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.sidebar-scroll { flex: 1; }

.model-list { padding: 10px; }

.model-item {
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 8px;
  cursor: pointer;
}

.model-item.active {
  border-color: #9ca3af;
  background: #eef1f5;
}

.model-name { font-size: 13px; font-weight: 600; color: #111827; }
.model-meta { font-size: 11px; color: #6b7280; margin-top: 4px; }

.content { padding: 0; background: #f7f8fa; display: flex; flex-direction: column; }

.main-header {
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.main-header h2 { margin: 0; font-size: 18px; color: #111827; }
.subtitle { font-size: 12px; color: #6b7280; }
.actions { display: flex; gap: 8px; align-items: center; }

.instance-search {
  width: 220px;
}

.stats-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  padding: 12px 20px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.stat-item {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
}

.stat-item strong { font-size: 18px; color: #111827; }

.card-scroll { flex: 1; }

.instance-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}

.card-grid {
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.instance-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
}

.instance-card:hover { border-color: #9ca3af; }

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.name { font-size: 14px; font-weight: 600; color: #111827; }

.line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #4b5563;
  margin-bottom: 6px;
  gap: 8px;
}

.mono {
  font-family: 'Consolas', 'Menlo', monospace;
  color: #111827;
}

.mono-textarea :deep(textarea) {
  font-family: 'Consolas', 'Menlo', monospace;
  font-size: 12px;
}

.empty-wrap { padding: 80px 0; }

.drawer-body { padding: 0 4px; }

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.status-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 10px;
}

.attr-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attr-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.empty-inline {
  color: #9ca3af;
  font-size: 12px;
}

.constraint-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.mb-12 { margin-bottom: 12px; }
.mt-12 { margin-top: 12px; }

@media (max-width: 960px) {
  .main-header {
    align-items: flex-start;
    gap: 12px;
  }

  .actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .instance-search {
    width: 180px;
  }

  .stats-bar {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
