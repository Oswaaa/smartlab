<template>
  <div class="task-list-fullscreen">
    <!-- Main Table Card -->
    <el-card class="table-card-fullscreen" shadow="never">
      <template #header>
        <div class="card-header-fullscreen">
          <span class="header-title">任务列表</span>
          <div class="header-actions-right">
            <el-button @click="refreshTaskList" :loading="loading">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-button type="primary" @click="openCreateDrawer">
              <el-icon class="mr-1"><Plus /></el-icon> 新建任务
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="tasks"
        v-loading="loading"
        style="width: 100%; height: 100%;"
        height="100%"
        border
        stripe
        highlight-current-row
        @row-click="handleRowClick"
        class="custom-table"
      >
        <el-table-column prop="id" label="ID" width="72" align="center" />
        <el-table-column prop="taskName" label="任务名称" min-width="160">
          <template #default="{ row }">
            <div style="font-weight: 600; color: #0f172a;">{{ row.taskName }}</div>
            <div v-if="row.taskDesc" style="font-size: 12px; color: #64748b; margin-top: 2px;">{{ row.taskDesc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="关联流程" min-width="160">
          <template #default="{ row }">
            <div v-if="getWorkflowName(row.flowModelId) !== String(row.flowModelId)" style="font-weight: 500; color: #334155;">{{ getWorkflowName(row.flowModelId) }}</div>
            <div style="font-size: 11px; color: #94a3b8; font-family: monospace;">ID: {{ row.flowModelId || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="taskStatus" label="状态" width="110" align="center">
          <template #default="{ row }">
            <div class="status-badge-container">
              <span :class="['pulse-dot', (row.taskStatus || '').toLowerCase()]" v-if="row.taskStatus === 'RUNNING'"></span>
              <el-tag :type="getStatusType(row.taskStatus)" effect="dark" class="status-tag">
                {{ getStatusLabel(row.taskStatus) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.currentNodeIdRef != null" type="info" size="small" effect="plain">节点 #{{ row.currentNodeIdRef }}</el-tag>
            <span v-else style="color: #94a3b8; font-size: 12px;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="执行周期" min-width="200">
          <template #default="{ row }">
            <div class="time-range-cell">
              <div><span class="time-label">始：</span>{{ formatTime(row.startTime) }}</div>
              <div v-if="row.endTime"><span class="time-label">终：</span>{{ formatTime(row.endTime) }}</div>
              <div v-else-if="row.taskStatus === 'RUNNING'" style="color: #2563eb; font-size: 12px;">运行中...</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="设备路由" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="workflowDeviceRoutes[String(row.flowModelId)]?.length" type="success" size="small" effect="plain">
              {{ workflowDeviceRoutes[String(row.flowModelId)].length }} 条
            </el-tag>
            <span v-else style="color: #94a3b8; font-size: 12px;">待加载</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons" @click.stop>
              <el-button
                type="primary"
                size="small"
                plain
                :disabled="row.taskStatus !== 'PENDING'"
                @click="startTask(row.id)"
              >
                启动
              </el-button>
              <el-button
                type="warning"
                size="small"
                plain
                :disabled="!['RUNNING', 'PAUSED'].includes(row.taskStatus)"
                @click="abortTask(row.id)"
              >
                终止
              </el-button>
              <el-button
                type="danger"
                size="small"
                plain
                @click="confirmDeleteTask(row.id)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="taskPageNo"
          v-model:page-size="taskPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="taskTotal"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleTaskPageSizeChange"
          @current-change="handleTaskPageChange"
        />
      </div>
    </el-card>

    <!-- Create Task Drawer -->
    <el-drawer v-model="createDrawerVisible" title="新建任务" size="78%" class="model-drawer unified-workflow-drawer" destroy-on-close>
      <div class="drawer-body" style="padding: 0 16px;">
        <el-form
          ref="createFormRef"
          :model="createForm"
          :rules="createRules"
          label-width="120px"
          label-position="top"
          class="basic-form"
          style="max-width: 100%;"
        >
          <div class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">基础配置</h2>
            <section class="drawer-section">
              <el-form-item label="任务名称" prop="taskName">
                <el-input v-model="createForm.taskName" placeholder="例如：批次PCR扩增与物料搅拌" />
              </el-form-item>
              <el-form-item label="关联流程" prop="flowModelId">
                <el-select
                  v-model="createForm.flowModelId"
                  placeholder="请选择流程"
                  style="width: 100%"
                  v-loading="loadingWorkflows"
                  @change="handleTemplateChange"
                >
                  <el-option
                    v-for="tpl in processTemplates"
                    :key="tpl.id"
                    :label="tpl.flowName"
                    :value="tpl.id"
                  />
                </el-select>
              </el-form-item>
            </section>
          </div>

          <div class="anchor-section industrial-section" v-if="selectedDeviceRoutes.length">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px; margin-top: 24px;">设备路由</h2>
            <section class="drawer-section">
              <el-alert type="info" :closable="false" title="设备实例由工作流模型的NODE_TO_DEVICE和DEVICE_TO_NODE连接固定声明，创建任务时不能二次替换" style="margin-bottom: 12px;" />
              <div v-for="route in selectedDeviceRoutes" :key="route.nodeName" class="resource-map-row">
                <div class="resource-node-label">{{ route.nodeName }}</div>
                <el-icon style="color: #2563eb;"><ArrowRight /></el-icon>
                <div class="resource-instance-label">{{ getInstanceName(route.deviceInstanceId) }}<span style="font-size: 11px; color: #94a3b8; margin-left: 6px;">(ID: {{ route.deviceInstanceId }})</span><div style="font-size: 11px; color: #64748b; margin-top: 3px;">状态机输入: {{ route.deviceInputInterfaceName }}，状态机输出: {{ route.deviceOutputInterfaceName }}</div></div>
              </div>
            </section>
          </div>
          <div class="anchor-section industrial-section" v-else-if="createForm.flowModelId && workflowHasDeviceNodes[String(createForm.flowModelId)]">
            <el-alert type="warning" :closable="false" title="该工作流未声明设备接口连接，后端会拒绝启动包含DEV_NODE的任务" />
          </div>
          <div class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px; margin-top: 24px;">约束配置</h2>
            <section class="drawer-section"><el-alert type="info" :closable="false" title="约束规则在约束管理页面独立配置并由约束引擎持续执行，任务只负责执行工作流模型声明的节点与设备路由" /></section>
          </div>
          </el-form>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="createDrawerVisible = false">取消</el-button>
          <el-button type="primary" @click="submitCreateTask" :loading="creating">
            创建任务
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- Monitor Tab Drawer -->
    <el-drawer v-model="monitorDrawerVisible" :title="`任务详情 · ${activeTask?.taskName || ''}`" size="78%" class="unified-workflow-drawer">
      <div v-if="activeTask" class="monitor-container">
        <!-- Meta Cards -->
        <div class="monitor-header-card">
          <div class="monitor-header-left">
            <div class="monitor-task-name">{{ activeTask.taskName }}</div>
            <div class="monitor-task-meta">
              <span>ID: {{ activeTask.id }}</span>
              <span>·</span>
              <span>流程: {{ getWorkflowName(activeTask.flowModelId) }}</span>
            </div>
          </div>
          <div class="monitor-header-right">
            <el-tag :type="getStatusType(activeTask.taskStatus)" size="large" effect="dark">
              {{ getStatusLabel(activeTask.taskStatus) }}
            </el-tag>
          </div>
        </div>
        <el-descriptions border :column="2" size="small" class="mb-4" style="margin-top: 12px;">
          <el-descriptions-item label="设备路由">
            <el-tag type="success" size="small" v-if="activeDeviceRoutes.length">{{ activeDeviceRoutes.length }} 条固定路由</el-tag>
            <span v-else style="color: #94a3b8;">未声明</span>
          </el-descriptions-item>
          <el-descriptions-item label="当前节点">
            <el-tag type="info" size="small" v-if="activeTask.currentNodeIdRef != null">#{{ activeTask.currentNodeIdRef }}</el-tag>
            <span v-else style="color: #94a3b8;">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(activeTask.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ activeTask.endTime ? formatTime(activeTask.endTime) : '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- Drawer Content Tabs -->
        <el-tabs v-model="monitorActiveTab" class="monitor-tabs">
          <!-- Tab 1: Nodes Progress Map -->
          <el-tab-pane label="步骤执行" name="snapshots">
            <div class="snapshots-timeline" v-loading="loadingDetails">
              <div v-for="step in nodeSnapshots" :key="step.id" class="node-snapshot-card">
                <div class="node-snap-header">
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <span class="item-index">{{ step.nodeIdRef }}</span>
                    <span class="node-name">深度 {{ step.stepDepth || 0 }}</span>
                  </div>
                  <el-tag :type="getNodeStateType(step.nodeStatus)" size="small" effect="plain">
                    {{ step.nodeStatus }}
                  </el-tag>
                </div>
                <div class="node-snap-body">
                  <div class="snap-row" v-if="step.startTime">
                    <span class="snap-label">开始：</span>
                    <span class="snap-val">{{ formatTime(step.startTime) }}</span>
                  </div>
                  <div class="snap-row" v-if="step.durationMs != null">
                    <span class="snap-label">耗时：</span>
                    <span class="snap-val">{{ step.durationMs }}ms</span>
                  </div>
                  <div class="snap-row" v-if="step.parentStepId != null">
                    <span class="snap-label">父步骤：</span>
                    <span class="snap-val">#{{ step.parentStepId }}</span>
                  </div>
                  <div class="snap-row" v-if="step.interfaceInSnapshot?.messageId">
                    <span class="snap-label">外部调用：</span>
                    <span class="snap-val runtime-id">{{ step.interfaceInSnapshot.messageId }}</span>
                  </div>
                  <div class="snap-row" v-if="step.interfaceInSnapshot?.capabilityRef">
                    <span class="snap-label">设备能力：</span>
                    <span class="snap-val">{{ step.interfaceInSnapshot.capabilityRef }} · 实例 {{ step.interfaceInSnapshot.deviceInstanceId }}</span>
                  </div>
                  <div class="snap-row" v-if="step.variableSpace && Object.keys(step.variableSpace).length > 0">
                    <span class="snap-label">变量空间：</span>
                    <div class="snap-tags mt-1">
                      <el-tag
                        v-for="(val, key) in step.variableSpace"
                        :key="key" type="info" size="small" class="mr-2 mb-1"
                      >
                        {{ key }}: {{ formatRuntimeValue(val) }}
                      </el-tag>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-if="nodeSnapshots.length === 0" description="暂无执行步骤记录" />
            </div>
          </el-tab-pane>

          <!-- Tab 2: Live Log Terminal -->
          <el-tab-pane label="执行日志" name="logs">
            <div class="terminal-header">
              <span>任务执行日志（自动刷新）</span>
              <el-button link type="primary" size="small" @click="fetchLogsAndSnapshots">
                手动同步
              </el-button>
            </div>
            <div class="log-container" ref="logContainerRef" v-loading="loadingDetails">
              <div v-for="log in executionLogs" :key="log.id" class="log-item">
                <span class="log-time">[{{ formatLogTime(log.logTime) }}]</span>
                <span class="log-module">[{{ log.sourceType || 'TASK' }}]</span>
                <span :class="['log-level', (log.logLevel || '').toLowerCase()]">{{ log.logLevel }}</span>
                <span class="log-msg">{{ log.logInfo }}</span>
              </div>
              <div v-if="executionLogs.length === 0" class="empty-terminal">
                &gt; 暂无任务日志。
              </div>
            </div>
          </el-tab-pane>
          <!-- Tab 3: Fixed device routes declared by the workflow model -->
          <el-tab-pane label="设备路由" name="resources">
            <div class="constraints-section">
              <div v-if="activeDeviceRoutes.length">
                <div v-for="route in activeDeviceRoutes" :key="route.nodeName" class="resource-map-row">
                  <div class="resource-node-label">{{ route.nodeName }}</div>
                  <el-icon style="color: #2563eb;"><ArrowRight /></el-icon>
                  <div class="resource-instance-label">{{ getInstanceName(route.deviceInstanceId) }}<span style="font-size: 11px; color: #94a3b8; margin-left: 6px;">(ID: {{ route.deviceInstanceId }})</span><div style="font-size: 11px; color: #64748b; margin-top: 3px;">状态机输入: {{ route.deviceInputInterfaceName }}，状态机输出: {{ route.deviceOutputInterfaceName }}</div></div>
                </div>
              </div>
              <el-empty v-else description="工作流未声明设备接口连接" />
            </div>
          </el-tab-pane>


        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Delete, ArrowRight } from '@element-plus/icons-vue'

interface TaskInstance {
  id: number
  taskName: string
  taskDesc?: string
  flowModelId: number
  taskVariables?: Record<string, any>
  taskStatus: string
  currentNodeIdRef?: number
  currentFlowNodeId?: number
  startTime?: string
  endTime?: string
}

interface DeviceRoute {
  nodeName: string
  deviceInstanceId: number
  deviceInputInterfaceName: string
  deviceOutputInterfaceName: string
}

interface TaskStep {
  id: number
  taskId: number
  flowNodeId?: number
  nodeIdRef?: number
  parentStepId?: number
  stepDepth?: number
  nodeStatus: string
  interfaceInSnapshot?: any
  interfaceOutSnapshot?: any
  portInSnapshot?: any
  portOutSnapshot?: any
  variableSpace?: Record<string, any>
  startTime?: string
  endTime?: string
  durationMs?: number
}

interface StepLog {
  id: number
  sourceType?: string
  taskId: number
  deviceInstanceId?: number
  taskStepId?: number
  logLevel: string
  logInfo: string
  logTime: string
}

interface WorkflowTemplate {
  id: number
  flowName: string
}

// Stats & lists
const tasks = ref<TaskInstance[]>([])
const loading = ref(false)
const taskTotal = ref(0)
const taskPageNo = ref(1)
const taskPageSize = ref(20)
const taskSummary = ref({
  total: 0,
  pending: 0,
  running: 0,
  succeeded: 0,
  failed: 0,
  terminated: 0
})
const processTemplates = ref<WorkflowTemplate[]>([])
const loadingWorkflows = ref(false)

// Drawer state
const createDrawerVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({
  taskName: '',
  flowModelId: null as number | null
})
const creating = ref(false)
const workflowDeviceRoutes = ref<Record<string, DeviceRoute[]>>({})
const workflowHasDeviceNodes = ref<Record<string, boolean>>({})
const selectedDeviceRoutes = computed(() => createForm.value.flowModelId == null ? [] : workflowDeviceRoutes.value[String(createForm.value.flowModelId)] || [])
const activeDeviceRoutes = computed(() => activeTask.value == null ? [] : workflowDeviceRoutes.value[String(activeTask.value.flowModelId)] || [])

const handleTemplateChange = async (value: number | null) => {
  if (value != null) await loadWorkflowRoutes(value)
}

// Monitor Drawer
const monitorDrawerVisible = ref(false)
const activeTask = ref<TaskInstance | null>(null)
const monitorActiveTab = ref('snapshots')
const nodeSnapshots = ref<TaskStep[]>([])
const executionLogs = ref<StepLog[]>([])
const loadingDetails = ref(false)
const logContainerRef = ref<HTMLElement | null>()
// All device instances cache for name lookup
const allInstances = ref<Record<string, string>>({})

// Auto refresh interval id
let pollIntervalId: any = null
let mainListPollIntervalId: any = null
let latestDetailLogId = 0

// Form rules
const createRules = ref<FormRules>({
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  flowModelId: [{ required: true, message: '请选择流程', trigger: 'change' }]
})

// Stats computed
const stats = computed(() => {
  return {
    total: taskSummary.value.total,
    running: taskSummary.value.running,
    completed: taskSummary.value.succeeded,
    failed: taskSummary.value.failed + taskSummary.value.terminated
  }
})

// Fetch all task instances
const fetchTasks = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/task/page', {
      params: {
        pageNo: taskPageNo.value,
        pageSize: taskPageSize.value
      }
    })
    if (res.data?.success) {
      const pageData = res.data.data || {}
      tasks.value = pageData.records || []
      taskTotal.value = pageData.total || 0
      await Promise.all([...new Set(tasks.value.map(task => task.flowModelId).filter(Boolean))].map(loadWorkflowRoutes))
    } else {
      ElMessage.error(res.data?.message || '加载任务列表失败')
    }
  } catch (error: any) {
    ElMessage.error('无法加载任务数据: ' + error.message)
  } finally {
    loading.value = false
  }
}

// Fetch all device instances for name lookup
const fetchAllInstances = async () => {
  try {
    const res = await axios.get('/api/device/instance/page?pageSize=500')
    if (res.data?.success) {
      const records = res.data.data?.records || []
      const map: Record<string, string> = {}
      records.forEach((inst: any) => {
        map[String(inst.id)] = inst.instanceName || inst.deviceName || String(inst.id)
      })
      allInstances.value = map
    }
  } catch (e) {}
}

const getInstanceName = (instanceId: any) => {
  return allInstances.value[String(instanceId)] || String(instanceId)
}

const loadWorkflowRoutes = async (flowModelId: number) => {
  const key = String(flowModelId)
  if (Object.prototype.hasOwnProperty.call(workflowDeviceRoutes.value, key)) return
  const res = await axios.get(`/api/workflow/detail/${flowModelId}`)
  if (!res.data?.success || !res.data.data) throw new Error(res.data?.message || '加载工作流详情失败')
  const definition = res.data.data
  workflowHasDeviceNodes.value[key] = (definition.nodesDef || []).some((node: any) => node.nodeType === 'DEV_NODE')
  const inbound = new Map<string, any>()
  for (const connection of definition.interfaceConnections || []) {
    if (connection.connectionType === 'DEVICE_TO_NODE') inbound.set(connection.target?.nodeName, connection)
  }
  workflowDeviceRoutes.value[key] = (definition.interfaceConnections || []).flatMap((connection: any) => {
    if (connection.connectionType !== 'NODE_TO_DEVICE') return []
    const reverse = inbound.get(connection.source?.nodeName)
    const deviceInstanceId = Number(connection.target?.deviceInstanceId)
    if (!reverse || !Number.isInteger(deviceInstanceId) || deviceInstanceId <= 0 || Number(reverse.source?.deviceInstanceId) !== deviceInstanceId) return []
    const deviceInputInterfaceName = connection.target?.interfaceName
    const deviceOutputInterfaceName = reverse.source?.interfaceName
    if (!connection.source?.nodeName || !deviceInputInterfaceName || !deviceOutputInterfaceName) return []
    return [{ nodeName: connection.source.nodeName, deviceInstanceId, deviceInputInterfaceName, deviceOutputInterfaceName }]
  })
}

const fetchTaskSummary = async () => {
  try {
    const res = await axios.get('/api/task/summary')
    if (res.data?.success) {
      taskSummary.value = {
        total: res.data.data?.total || 0,
        pending: res.data.data?.pending || 0,
        running: res.data.data?.running || 0,
        succeeded: res.data.data?.succeeded || 0,
        failed: res.data.data?.failed || 0,
        terminated: res.data.data?.terminated || 0
      }
    }
  } catch (error) {
    ElMessage.error('加载任务统计失败')
  }
}

const refreshTaskList = async () => {
  await Promise.all([fetchTasks(), fetchTaskSummary()])
}

const handleTaskPageChange = (page: number) => {
  taskPageNo.value = page
  fetchTasks()
}

const handleTaskPageSizeChange = (size: number) => {
  taskPageSize.value = size
  taskPageNo.value = 1
  fetchTasks()
}

// Fetch process workflows templates
const fetchWorkflows = async () => {
  loadingWorkflows.value = true
  try {
    const res = await axios.get('/api/workflow/list')
    if (res.data?.success) {
      processTemplates.value = res.data.data || []
    }
  } catch (error) {
    ElMessage.error('加载流程列表失败')
  } finally {
    loadingWorkflows.value = false
  }
}

// Workflow Name mapping helper
const getWorkflowName = (flowModelId: number) => {
  const match = processTemplates.value.find(workflow => workflow.id === Number(flowModelId))
  return match ? match.flowName : String(flowModelId || '-')
}

// Status Badges helpers
const getStatusType = (status: string) => {
  switch (status) {
    case 'PENDING': return 'info'
    case 'RUNNING': return 'primary'
    case 'SUCCEEDED': return 'success'
    case 'FAILED': return 'danger'
    case 'TERMINATING': return 'warning'
    case 'TERMINATED': return 'warning'
    default: return 'info'
  }
}

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'PENDING': return '排队中'
    case 'RUNNING': return '运行中'
    case 'SUCCEEDED': return '成功'
    case 'FAILED': return '终止'
    case 'PAUSED': return '已暂停'
    case 'TERMINATING': return '终止中'
    case 'TERMINATED': return '已终止'
    default: return status
  }
}

const getNodeStateType = (state: string) => {
  switch (state) {
    case 'PENDING': return 'info'
    case 'RUNNING': return 'warning'
    case 'SUCCEEDED': return 'success'
    case 'FAILED': return 'danger'
    case 'TERMINATING': return 'warning'
    case 'TERMINATED': return 'warning'
    default: return 'info'
  }
}

// Row click trigger Drawer
const handleRowClick = (row: TaskInstance) => {
  activeTask.value = row
  monitorActiveTab.value = 'snapshots'
  nodeSnapshots.value = []
  executionLogs.value = []
  latestDetailLogId = 0
  monitorDrawerVisible.value = true
  
  fetchLogsAndSnapshots()
  
  // Start polling detail details
  startDetailsPolling()
}

// Start details polling
const startDetailsPolling = () => {
  stopDetailsPolling()
  pollIntervalId = setInterval(() => {
    if (monitorDrawerVisible.value && activeTask.value) {
      // If task is running, actively pull progress
      fetchLogsAndSnapshots(true)
    } else {
      stopDetailsPolling()
    }
  }, 3000)
}

const stopDetailsPolling = () => {
  if (pollIntervalId) {
    clearInterval(pollIntervalId)
    pollIntervalId = null
  }
}

// Fetch logs and snapshots for active task
const fetchLogsAndSnapshots = async (silent = false) => {
  if (!activeTask.value) return
  if (!silent) {
    loadingDetails.value = true
  }
  
  try {
    const taskId = activeTask.value.id
    
    // Concurrently load snapshots & logs
    const logsUrl = latestDetailLogId > 0
      ? `/api/task/logs/${taskId}?afterLogId=${latestDetailLogId}&limit=300`
      : `/api/task/logs/${taskId}`
    const [snapRes, logRes] = await Promise.all([
      axios.get(`/api/task/snapshots/${taskId}`),
      axios.get(logsUrl)
    ])
    
    if (snapRes.data?.success) {
      nodeSnapshots.value = snapRes.data.data || []
    }
    
    if (logRes.data?.success) {
      const logs = logRes.data.data || []
      if (latestDetailLogId > 0) {
        executionLogs.value = [...executionLogs.value, ...logs]
      } else {
        executionLogs.value = logs
      }
      latestDetailLogId = executionLogs.value.reduce((max, log) => Math.max(max, Number(log.id || 0)), latestDetailLogId)
      
      // Auto scroll terminal to bottom
      nextTick(() => {
        if (logContainerRef.value) {
          logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight
        }
      })
    }
    
    // Also sync the task instance itself in case status updated
    const taskRes = await axios.get(`/api/task/${taskId}`)
    if (taskRes.data?.success && taskRes.data.data) {
      const updated = taskRes.data.data
      activeTask.value.taskStatus = updated.taskStatus
      activeTask.value.startTime = updated.startTime
      activeTask.value.endTime = updated.endTime
      
      // Find matching item in main list and sync
      const mainMatch = tasks.value.find(t => t.id === taskId)
      if (mainMatch) {
        mainMatch.taskStatus = updated.taskStatus
        mainMatch.startTime = updated.startTime
        mainMatch.endTime = updated.endTime
      }
    }
  } catch (error) {
    ElMessage.error('加载任务详情失败')
  } finally {
    if (!silent) {
      loadingDetails.value = false
    }
  }
}

// Start task on engine
const startTask = async (taskId: number) => {
  try {
    await ElMessageBox.confirm('确认启动该任务吗？', '启动确认', {
      confirmButtonText: '确认启动',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const res = await axios.post(`/api/task/start/${taskId}`)
    if (res.data?.success) {
      ElMessage.success('任务已启动')
      refreshTaskList()
    } else {
      ElMessage.error(res.data?.message || '启动任务失败')
    }
  } catch (e) {
    // cancelled
  }
}

const abortTask = async (taskId: number) => {
  try {
    await ElMessageBox.confirm('确认终止该任务吗？活动节点会停止推进，并释放已占用的设备资源。', '终止确认', {
      confirmButtonText: '确认终止',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await axios.post(`/api/task/terminate/${taskId}`)
    if (res.data?.success) {
      ElMessage.success('任务已进入终止流程')
      await refreshTaskList()
      if (activeTask.value?.id === taskId) {
        await fetchLogsAndSnapshots()
      }
    } else {
      ElMessage.error(res.data?.message || '终止任务失败')
    }
  } catch (e) {
    // cancelled
  }
}

// Confirm Delete Task
const confirmDeleteTask = async (taskId: number) => {
  try {
    await ElMessageBox.confirm('确认删除该任务及关联快照吗？该操作不可恢复。', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'danger'
    })
    
    const res = await axios.delete(`/api/task/delete/${taskId}`)
    if (res.data?.success) {
      ElMessage.success('任务已删除')
      if (activeTask.value?.id === taskId) {
        monitorDrawerVisible.value = false
      }
      refreshTaskList()
    } else {
      ElMessage.error(res.data?.message || '注销失败')
    }
  } catch (e) {
    // cancelled
  }
}

const openCreateDrawer = () => {
  createForm.value = { taskName: '', flowModelId: null }
  createDrawerVisible.value = true
  nextTick(() => {
    createFormRef.value?.clearValidate()
  })
}

// Submit Create task
const submitCreateTask = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (valid) {
      creating.value = true
      try {
        const payload = {
          taskName: createForm.value.taskName,
          flowModelId: createForm.value.flowModelId
        }
        
        const res = await axios.post('/api/task/save', payload)
        if (res.data?.success) {
          ElMessage.success('任务创建成功')
          createDrawerVisible.value = false
          taskPageNo.value = 1
          refreshTaskList()
        } else {
          ElMessage.error(res.data?.message || '任务创建失败')
        }
      } catch (error: any) {
        ElMessage.error('保存失败: ' + error.message)
      } finally {
        creating.value = false
      }
    }
  })
}

// Format time utility

const formatTime = (timeStr: string) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  return date.toLocaleString()
}
const formatRuntimeValue = (value: unknown) => {
  if (value == null) return 'null'
  if (typeof value === 'object') {
    try { return JSON.stringify(value) } catch { return String(value) }
  }
  return String(value)
}

const formatLogTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return [
    String(date.getHours()).padStart(2, '0'),
    String(date.getMinutes()).padStart(2, '0'),
    String(date.getSeconds()).padStart(2, '0')
  ].join(':')
}

const startMainListPolling = () => {
  if (mainListPollIntervalId) return
  mainListPollIntervalId = setInterval(() => {
    // Only fetch tasks in background quietly
    axios.get(`/api/task/page?pageNo=${taskPageNo.value}&pageSize=${taskPageSize.value}`)
      .then((res) => {
        if (res.data?.success) {
          tasks.value = res.data.data.records || []
          taskTotal.value = res.data.data.total || 0
        }
      })
      .catch(() => {})
      
    axios.get('/api/task/summary').then((res) => {
      if (res.data?.success) taskSummary.value = res.data.data
    }).catch(() => {})
  }, 3000)
}

const stopMainListPolling = () => {
  if (mainListPollIntervalId) {
    clearInterval(mainListPollIntervalId)
    mainListPollIntervalId = null
  }
}


onMounted(() => {
  refreshTaskList()
  fetchWorkflows()
  fetchAllInstances()
  startMainListPolling()
})

onUnmounted(() => {
  stopDetailsPolling()
  stopMainListPolling()
})
</script>

<style scoped>
.task-list {
  padding: 32px;
  background-color: #f1f5f9;
  min-height: 100vh;
  box-sizing: border-box;
  font-family: "Inter", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.title {
  margin: 0;
  color: #0f172a;
  font-weight: 800;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.subtitle {
  margin: 6px 0 0 0;
  color: #64748b;
  font-size: 14px;
}

.mb-6 {
  margin-bottom: 32px;
}

.mb-4 {
  margin-bottom: 16px;
}

.mt-1 { margin-top: 4px; }
.mt-2 { margin-top: 8px; }
.mr-1 { margin-right: 4px; }
.mr-2 { margin-right: 8px; }
.mb-1 { margin-bottom: 4px; }

/* Premium Glassmorphism Stat Cards */
.stat-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.stat-val {
  font-size: 36px;
  font-weight: 800;
  color: #0f172a;
  margin-top: 8px;
}

/* Gradient Texts for Val */
.text-blue {
  background: linear-gradient(135deg, #3b82f6 0%, #06b6d4 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.text-green {
  background: linear-gradient(135deg, #10b981 0%, #34d399 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.text-red {
  background: linear-gradient(135deg, #f43f5e 0%, #fb923c 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* Table Card */
.table-card {
  background: #ffffff;
  border: none;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  font-size: 16px;
  color: #1e293b;
  padding: 16px 20px;
}

/* Custom Table Styles */
.custom-table {
  border-radius: 0 0 16px 16px;
  overflow: hidden;
}

.custom-table :deep(th.el-table__cell) {
  background-color: #f8fafc;
  color: #475569;
  font-weight: 600;
  text-transform: uppercase;
  font-size: 12px;
  letter-spacing: 0.05em;
  border-bottom: 2px solid #e2e8f0;
}

.custom-table :deep(td.el-table__cell) {
  border-bottom: 1px solid #f1f5f9;
  padding: 12px 0;
}

.custom-table :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.custom-table :deep(.el-table__row:hover > td.el-table__cell) {
  background-color: #f8fafc;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 20px;
}

/* Status Badge with Neon Pulse */
.status-badge-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.status-tag {
  border-radius: 20px;
  font-weight: 600;
  border: none;
  padding: 0 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #3b82f6;
  box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7);
  animation: pulse 1.6s infinite cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(59, 130, 246, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(59, 130, 246, 0); }
}

.time-range-cell {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.time-label {
  color: #94a3b8;
  font-weight: 500;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.premium-btn {
  background: linear-gradient(135deg, #2563eb 0%, #3b82f6 100%);
  border: none;
  border-radius: 8px;
  font-weight: 600;
  padding: 10px 20px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.2);
}
.premium-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.3);
}

/* Forms */
.create-form {
  padding: 10px 20px;
}

.monospace-textarea :deep(textarea) {
  font-family: "Fira Code", Consolas, Monaco, monospace;
  font-size: 13px;
  background-color: #f8fafc;
  color: #0f172a;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.schema-info-bar {
  background: linear-gradient(90deg, #eff6ff 0%, #f8fafc 100%);
  border-left: 4px solid #3b82f6;
  padding: 10px 14px;
  font-size: 12px;
  color: #1e3a8a;
  border-radius: 0 6px 6px 0;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
}

/* Monitor Drawer */
.monitor-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  padding: 0 20px;
}

.monitor-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}

:deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding-top: 16px;
}

/* Timeline Snapshots */
.snapshots-timeline {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 8px;
}

.node-snapshot-card {
  background: #ffffff;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  transition: transform 0.2s ease;
  position: relative;
  overflow: hidden;
}
.node-snapshot-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: #3b82f6;
  border-radius: 4px 0 0 4px;
}

.node-snap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e2e8f0;
  margin-bottom: 12px;
}

.dynamic-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  width: 100%;
}

.drawer-body {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
}

.drawer-section {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 10px 16px;
  background: #fff;
  margin-top: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  min-height: 26px;
}

.section-title h3 {
  margin: 0;
  font-size: 15px;
  line-height: 1.3;
  color: #0f172a;
}

.compact-empty {
  display: flex;
  align-items: center;
  min-height: 30px;
  padding: 7px 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
}

.node-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.node-snap-body {
  font-size: 13px;
}

.snap-row {
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
}

.snap-label {
  color: #64748b;
  font-weight: 600;
  margin-bottom: 4px;
  text-transform: uppercase;
  font-size: 11px;
}

.snap-val {
  color: #0f172a;
  font-weight: 500;
}

.snap-tags {
  display: flex;
  flex-wrap: wrap;
}

/* Terminal Log UI */
.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 12px;
  font-weight: 600;
  text-transform: uppercase;
}

.log-container {
  background-color: #f8fafc;
  color: #334155;
  padding: 20px;
  border-radius: 12px;
  font-family: "Fira Code", Consolas, Monaco, monospace;
  font-size: 13px;
  height: 420px;
  overflow-y: auto;
  line-height: 1.6;
  border: 1px solid #e2e8f0;
  box-shadow: inset 0 2px 10px rgba(0,0,0,0.02);
}

.log-item {
  margin-bottom: 8px;
  word-wrap: break-word;
}

.log-time {
  color: #64748b;
  margin-right: 12px;
}

.log-module {
  color: #0284c7;
  margin-right: 12px;
  font-weight: 600;
}

.log-level {
  display: inline-block;
  width: 60px;
  font-weight: 700;
  text-transform: uppercase;
  margin-right: 12px;
}
.log-level.info { color: #3b82f6; }
.log-level.warn { color: #f59e0b; }
.log-level.error { color: #ef4444; }
.log-level.success { color: #10b981; }

.log-msg {
  color: #0f172a;
}

.empty-terminal {
  color: #475569;
  font-style: italic;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.constraints-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}

.constraint-group {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 16px;
}

.constraint-group-title {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

/* Monitor header card */
.monitor-header-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 4px;
}

.monitor-task-name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
}

.monitor-task-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
}

/* Resource Map */
.resource-map-row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 8px;
}

.resource-node-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  background: #f1f5f9;
  padding: 4px 10px;
  border-radius: 6px;
  font-family: monospace;
  min-width: 80px;
  text-align: center;
}

.resource-instance-label {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  flex: 1;
}

/* item-index badge */
.item-index {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: #eef2f7;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}


/* Enterprise task console overrides */
.task-list-fullscreen { padding: 16px 20px 20px; overflow: hidden; }
.table-card-fullscreen { height: 100%; display: flex; flex-direction: column; border: 1px solid #e5e7eb !important; border-radius: 6px !important; }
.table-card-fullscreen :deep(.el-card__header) { min-height: 58px; padding: 10px 16px; border-bottom: 1px solid #e5e7eb; }
.table-card-fullscreen :deep(.el-card__body) { flex: 1; min-height: 0; padding: 0; display: flex; flex-direction: column; }
.card-header-fullscreen { height: 100%; display: flex; align-items: center; justify-content: space-between; }
.header-title { color: #111827; font-size: 20px; font-weight: 600; }
.header-actions-right { display: flex; align-items: center; gap: 8px; }
.task-summary-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; }
.task-summary-strip > div { min-height: 62px; padding: 10px 16px; border-right: 1px solid #e5e7eb; display: flex; flex-direction: column; justify-content: center; }
.task-summary-strip > div:last-child { border-right: 0; }
.task-summary-strip span { color: #6b7280; font-size: 12px; }
.task-summary-strip strong { margin-top: 2px; color: #111827; font-size: 20px; line-height: 24px; }
.task-summary-strip .metric-label { color: #6b7280; font-size: 12px; }
.task-summary-strip .metric-value { margin-top: 2px; color: #111827; font-size: 20px; line-height: 24px; }
.task-summary-strip .metric-note { color: #8a93a6; font-size: 12px; }
.custom-table { flex: 1; min-height: 0; border-radius: 0; }
.custom-table :deep(th.el-table__cell) { border-bottom: 1px solid #e5e7eb; text-transform: none; letter-spacing: 0; }
.custom-table :deep(td.el-table__cell) { padding: 8px 0; }
.pagination-bar { min-height: 52px; box-sizing: border-box; padding: 10px 16px; border-top: 1px solid #e5e7eb; }
.status-tag { border-radius: 4px; box-shadow: none; }
.premium-btn { background: #1677ff; box-shadow: none; transform: none; }
.monitor-header-card, .schema-info-bar { background: #fafbfc; border: 1px solid #e5e7eb; border-radius: 6px; box-shadow: none; }
.node-snapshot-card { border-radius: 6px; box-shadow: none; transform: none; }
.log-container { border-radius: 6px; box-shadow: none; }
.model-drawer .drawer-body { padding: 0 24px !important; }
@media (max-width: 800px) { .task-list-fullscreen { padding: 12px; } .task-summary-strip { grid-template-columns: repeat(2, 1fr); } }
</style>
