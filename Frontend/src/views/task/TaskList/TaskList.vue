<template>
  <div class="task-list-fullscreen">
    <!-- Main Table Card -->
    <el-card class="table-card-fullscreen" shadow="never">
      <template #header>
        <div class="card-header-fullscreen">
          <div class="header-copy">
            <h1 class="header-title">任务列表</h1>
            <p>创建实验任务、绑定设备资源并持续跟踪执行状态</p>
          </div>
          <div class="header-actions-right">
            <el-button class="btn-aliyun" :icon="Refresh" aria-label="刷新任务列表" @click="refreshTaskList" :loading="loading">刷新</el-button>
            <el-button class="btn-aliyun-cta" :icon="Plus" @click="openCreateDrawer">新建任务</el-button>
          </div>
        </div>
      </template>

      <TaskSummaryStrip :summary="taskSummary" :active-status="taskStatusFilter" @select-status="setTaskStatus" />
      <TaskFilterBar v-model:keyword="taskKeyword" v-model:status="taskStatusFilter" :total="taskTotal" :last-updated-at="lastUpdatedAt" :has-filters="hasTaskFilters" @query="applyTaskFilters" @reset="clearTaskFilters" />

      <el-table
        :data="tasks"
        v-loading="loading"
        style="width: 100%; height: 100%;"
        height="100%"
        class="custom-table"
      >
        <el-table-column prop="id" label="ID" width="72" align="center"><template #default="{ row }"><span class="task-id">#{{ row.id }}</span></template></el-table-column>
        <el-table-column prop="taskName" label="任务名称" min-width="160">
          <template #default="{ row }">
            <div class="task-name">{{ row.taskName }}</div>
            <div v-if="row.taskDesc" class="task-description">{{ row.taskDesc }}</div>
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
              <el-tag :type="getStatusType(row.taskStatus)" effect="plain" :class="['status-tag', `status-${String(row.taskStatus || '').toLowerCase()}`]">
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
        <el-table-column label="任务约束" width="100" align="center"><template #default="{row}"><el-tag v-if="row.taskConstraints?.length" type="warning" size="small" effect="plain">{{ row.taskConstraints.length }} 条</el-tag><span v-else style="color:#94a3b8;font-size:12px">无</span></template></el-table-column>        <el-table-column label="操作" width="190" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons" @click.stop>
              <el-button v-if="row.taskStatus === 'PENDING'" class="btn-aliyun-link" size="small" link @click="startTask(row.id)">启动</el-button>
              <el-button v-if="['RUNNING', 'PAUSED'].includes(row.taskStatus)" class="btn-aliyun-danger-link" size="small" link @click="abortTask(row.id)">终止</el-button>
              <el-button class="detail-button btn-aliyun-link" size="small" link @click="openTaskDetail(row)">详情</el-button>
              <el-button class="btn-aliyun-danger-link" size="small" link @click="confirmDeleteTask(row.id)">删除</el-button>
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

    <TaskCreateDrawer ref="createFormRef" v-model="createDrawerVisible" :form="createForm" :workflows="executableProcessTemplates" :loading-workflows="loadingWorkflows" :requirements="selectedWorkflowRequirements" :groups="selectedWorkflowGroups" :errors="selectedWorkflowErrors" :instances="deviceInstances" :models="deviceModels" :preflight-result="preflightResult" :preflighting="preflighting" :creating="creating" :constraint-reviews="taskConstraintReviews" @update:task-name="createForm.taskName = $event" @update:flow-model-id="handleTemplateChange" @update:resource-bindings="updateResourceBindings" @edit-constraint="openTaskConstraint" @remove-constraint="removeTaskConstraint" @preflight="runPreflight" @submit="submitCreateTask" />

    <el-dialog v-model="taskConstraintDialogVisible" :title="editingTaskConstraintIndex == null ? '添加任务级约束' : '编辑任务级约束'" width="1040px" append-to-body destroy-on-close @opened="loadTaskConstraintEditor">
      <ConstraintRuleEditor ref="taskConstraintEditorRef" :models="deviceModels" :instances="deviceInstances" :workflows="executableProcessTemplates" :tasks="[]" task-mode :task-resources="selectedTaskResources" :task-workflow-nodes="selectedWorkflowNodes" />
      <template #footer><el-button class="btn-aliyun" @click="taskConstraintDialogVisible=false">取消</el-button><el-button class="btn-aliyun-cta" @click="saveTaskConstraint">保存任务约束</el-button></template>
    </el-dialog>
    <TaskExecutionDrawer v-model="monitorDrawerVisible" :task="activeTask" :workflow="activeWorkflowDefinition" :steps="nodeSnapshots" :logs="executionLogs" :bindings="activeDeviceRoutes" :constraints="effectiveConstraintView" :loading="loadingDetails" @refresh="fetchLogsAndSnapshots()" @terminate="activeTask && abortTask(activeTask.id)" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import ConstraintRuleEditor from '../../../components/constraint/ConstraintRuleEditor.vue'
import TaskCreateDrawer from './components/TaskCreateDrawer.vue'
import TaskExecutionDrawer from './components/TaskExecutionDrawer.vue'
import TaskFilterBar from './components/TaskFilterBar.vue'
import TaskSummaryStrip from './components/TaskSummaryStrip.vue'
import {
  buildBindingWorkflowView,
  buildDeviceBindings,
  buildTaskCreatePayload,
  expandWorkflowDefinition
} from '../../../utils/taskResourceBindings.js'
import { filterExecutableWorkflows, isExecutableWorkflow } from '../../../utils/workflowExecution.js'
import { taskApi } from '../../../services/taskApi.js'
import { workflowApi } from '../../../services/workflowApi.js'
import { reviewTaskConstraintsAfterBindingChange } from './taskConstraintReview.js'

interface TaskInstance {
  id: number
  taskName: string
  taskDesc?: string
  flowModelId: number
  taskVariables?: Record<string, any>
  resourceMap?: any
  taskConstraints?: any[]
  taskStatus: string
  currentNodeIdRef?: number
  currentFlowNodeId?: number
  startTime?: string
  endTime?: string
}

interface DeviceRoute {
  flowModelId: number
  flowName: string
  nodeName: string
  bindingKey: string
  inheritanceKey: string
  occurrencePath: string
  depth: number
  deviceModelId: number
  deviceInputInterfaceName: string
  deviceOutputInterfaceName: string
  interfaceValid: boolean
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
  status?: string
}

// Stats & lists
const tasks = ref<TaskInstance[]>([])
const loading = ref(false)
const taskTotal = ref(0)
const taskPageNo = ref(1)
const taskPageSize = ref(20)
const taskKeyword = ref('')
const taskStatusFilter = ref('')
const lastUpdatedAt = ref('')
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
const executableProcessTemplates = computed(() => filterExecutableWorkflows(processTemplates.value))

// Drawer state
const createDrawerVisible = ref(false)
const workflowRequirementsByFlow = ref<Record<string, any[]>>({})
const preflightResult = ref<any>(null)
const preflighting = ref(false)
const createFormRef = ref<any>()
const createForm = ref({
  taskName: '',
  flowModelId: null as number | null,
  resourceBindings: {} as Record<string, number | null>,
  taskConstraints: [] as any[]
})
const taskConstraintReviews = ref<Array<string | null>>([])
const unresolvedTaskConstraintReviews = computed(() => taskConstraintReviews.value.some(Boolean))
const creating = ref(false)
const workflowDeviceRoutes = ref<Record<string, DeviceRoute[]>>({})
const workflowGroups = ref<Record<string, any[]>>({})
const workflowNodes = ref<Record<string, any[]>>({})
const workflowErrors = ref<Record<string, string[]>>({})
const workflowDefinitions = ref<Record<string, any>>({})
const selectedWorkflowRequirements = computed(() => createForm.value.flowModelId == null ? [] : workflowRequirementsByFlow.value[String(createForm.value.flowModelId)] || [])
const selectedWorkflowGroups = computed(() => createForm.value.flowModelId == null ? [] : workflowGroups.value[String(createForm.value.flowModelId)] || [])
const selectedWorkflowErrors = computed(() => createForm.value.flowModelId == null ? [] : workflowErrors.value[String(createForm.value.flowModelId)] || [])
const activeDeviceRoutes = computed(() => activeTask.value == null ? [] : (workflowRequirementsByFlow.value[String(activeTask.value.flowModelId)] || []).map(requirement => {
  const deviceInstanceId = activeTask.value?.resourceMap?.deviceBindings?.[requirement.slotId]?.deviceInstanceId
  return { ...requirement, bindingKey: requirement.slotId, deviceInstanceId, instanceName: getInstanceName(deviceInstanceId), deviceModelName: getModelName(requirement.deviceModelId) }
}))
const activeWorkflowDefinition = computed(() => activeTask.value == null ? null : workflowDefinitions.value[String(activeTask.value.flowModelId)] || null)
const selectedTaskResources = computed(() => {
  const seen = new Set()
  return selectedWorkflowRequirements.value
    .map(requirement => ({...requirement, bindingKey: requirement.slotId, deviceInstanceId: createForm.value.resourceBindings[requirement.slotId], instanceName: getInstanceName(createForm.value.resourceBindings[requirement.slotId])}))
    .filter(item => {
      const id = item.deviceInstanceId
      if (!id || seen.has(id)) return false
      seen.add(id)
      return true
    })
})
const selectedWorkflowNodes = computed(() => createForm.value.flowModelId == null ? [] : workflowNodes.value[String(createForm.value.flowModelId)] || [])

const hasTaskFilters = computed(() => Boolean(taskKeyword.value.trim() || taskStatusFilter.value))
const applyTaskFilters = () => { taskPageNo.value = 1; fetchTasks() }
const setTaskStatus = (status: string) => { taskStatusFilter.value = status; applyTaskFilters() }
const clearTaskFilters = () => { taskKeyword.value = ''; taskStatusFilter.value = ''; applyTaskFilters() }


async function runPreflight() {
  if (!createForm.value.flowModelId) return
  if (unresolvedTaskConstraintReviews.value) return ElMessage.warning('请先复核受设备绑定变更影响的任务约束')
  const missingRequirements = selectedWorkflowRequirements.value.filter(requirement => !createForm.value.resourceBindings[requirement.slotId])
  if (missingRequirements.length) {
    preflightResult.value = {
      ready: false,
      message: `还有 ${missingRequirements.length} 个设备未绑定`,
      issues: missingRequirements.map(requirement => ({
        code: 'TASK_BINDING_MISSING',
        elementId: requirement.slotId,
        message: `${requirement.occurrencePath || requirement.nodeName}未绑定设备实例`,
        suggestion: '请选择可用的设备实例',
        blocking: true
      }))
    }
    return
  }
  preflighting.value = true
  try {
    const payload = {
      flowModelId: createForm.value.flowModelId,
      taskVariables: {},
      deviceBindings: buildDeviceBindings(selectedWorkflowRequirements.value, createForm.value.resourceBindings),
      taskConstraints: createForm.value.taskConstraints || []
    }
    const response = await taskApi.preflight(payload)
    if (response.data?.success) {
      preflightResult.value = response.data.data
    }
  } catch (error: any) {
    preflightResult.value = { ready: false, message: error.message || '前置检查失败' }
  } finally {
    preflighting.value = false
  }
}

const handleTemplateChange = async (value: number | null) => {
  createForm.value.flowModelId = value
  createForm.value.resourceBindings = {}
  createForm.value.taskConstraints = []
  taskConstraintReviews.value = []
  preflightResult.value = null
  if (value == null) return
  await loadWorkflowRoutes(value)
  for (const requirement of workflowRequirementsByFlow.value[String(value)] || []) createForm.value.resourceBindings[requirement.slotId] = null
}

const updateResourceBindings = (next: Record<string, number | null>) => {
  const changed = JSON.stringify(createForm.value.resourceBindings) !== JSON.stringify(next)
  if (changed && createForm.value.taskConstraints.length) {
    const previousReviewCount = taskConstraintReviews.value.filter(Boolean).length
    taskConstraintReviews.value = reviewTaskConstraintsAfterBindingChange(
      createForm.value.taskConstraints,
      createForm.value.resourceBindings,
      next,
      taskConstraintReviews.value
    )
    if (taskConstraintReviews.value.filter(Boolean).length > previousReviewCount) {
      ElMessage.warning('设备实例绑定已变更，受影响的任务约束已保留并标记为需要复核')
    }
  }
  createForm.value.resourceBindings = next
  preflightResult.value = null
}

// Monitor Drawer
const monitorDrawerVisible = ref(false)
const activeTask = ref<TaskInstance | null>(null)
const nodeSnapshots = ref<TaskStep[]>([])
const executionLogs = ref<StepLog[]>([])
const loadingDetails = ref(false)
// All device instances cache for name lookup
const allInstances = ref<Record<string, string>>({})
const deviceInstances = ref<any[]>([])
const deviceModels = ref<any[]>([])
const taskConstraintDialogVisible = ref(false)
const taskConstraintEditorRef = ref<any>()
const editingTaskConstraintIndex = ref<number | null>(null)
const effectiveConstraintView = ref<any>(null)
const loadingEffectiveConstraints = ref(false)

// Auto refresh interval id
let pollIntervalId: any = null
let mainListPollIntervalId: any = null
let latestDetailLogId = 0

// Fetch all task instances. Background refresh stays quiet and preserves active filters.
const fetchTasks = async (silent = false) => {
  if (!silent) loading.value = true
  try {
    const res = await axios.get('/api/task/page', {
      params: {
        pageNo: taskPageNo.value,
        pageSize: taskPageSize.value,
        keyword: taskKeyword.value || undefined,
        status: taskStatusFilter.value || undefined
      }
    })
    if (res.data?.success) {
      const pageData = res.data.data || {}
      tasks.value = pageData.records || []
      taskTotal.value = pageData.total || 0
      lastUpdatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      await Promise.all([...new Set(tasks.value.map(task => task.flowModelId).filter(Boolean))].map(loadWorkflowRoutes))
    } else if (!silent) {
      ElMessage.error(res.data?.message || '加载任务列表失败')
    }
  } catch (error: any) {
    if (!silent) ElMessage.error('无法加载任务数据: ' + error.message)
  } finally {
    if (!silent) loading.value = false
  }
}

// Fetch all device instances for name lookup
const fetchAllInstances = async () => {
  const res = await axios.get('/api/device/instance/page', { params: { pageSize: 500, lifecycleStatus: 'IN_USE' } })
  if (!res.data?.success) throw new Error(res.data?.message || '加载设备实例失败')
  const records = res.data.data?.records || []
  deviceInstances.value = records
  allInstances.value = Object.fromEntries(records.map((inst:any) => [String(inst.id),inst.instanceName || inst.deviceName || String(inst.id)]))
}

const fetchAllModels = async () => {
  const res = await axios.get('/api/device/model/list')
  if (!res.data?.success) throw new Error(res.data?.message || '加载设备模型失败')
  deviceModels.value = res.data.data || []
}

const getInstanceName = (instanceId: any) => {
  return instanceId ? (allInstances.value[String(instanceId)] || String(instanceId)) : '待绑定'
}
const instancesForModel = (modelId:any) => deviceInstances.value.filter((instance:any) => Number(instance.deviceModelId || instance.modelId) === Number(modelId))
const getModelName = (modelId:any) => deviceModels.value.find((model:any) => Number(model.id || model.modelId) === Number(modelId))?.modelName || String(modelId)

const workflowDetail = async (flowModelId: number) => {
  const key = String(flowModelId)
  if (workflowDefinitions.value[key]) return workflowDefinitions.value[key]
  const res = await axios.get(`/api/workflow/detail/${flowModelId}`)
  if (!res.data?.success || !res.data.data) throw new Error(res.data?.message || '加载工作流详情失败')
  workflowDefinitions.value[key] = res.data.data
  return res.data.data
}

const loadWorkflowRoutes = async (flowModelId: number) => {
  const key = String(flowModelId)
  if (Object.prototype.hasOwnProperty.call(workflowDeviceRoutes.value,key)) return
  const [expanded, requirementsResponse] = await Promise.all([
    expandWorkflowDefinition(flowModelId, workflowDetail),
    workflowApi.requirements(flowModelId)
  ])
  if (!requirementsResponse.data?.success) throw new Error(requirementsResponse.data?.message || '加载设备绑定要求失败')
  const requirements = requirementsResponse.data.data?.bindings || []
  workflowRequirementsByFlow.value[key] = requirements
  workflowGroups.value[key] = expanded.groups
  workflowDeviceRoutes.value[key] = expanded.deviceRoutes
  workflowNodes.value[key] = expanded.workflowNodes
  workflowErrors.value[key] = buildBindingWorkflowView(expanded, requirements).errors
}
const fetchTaskSummary = async (silent = false) => {
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
    if (!silent) ElMessage.error('加载任务统计失败')
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
    case 'FAILED': return '失败'
    case 'PAUSED': return '已暂停'
    case 'TERMINATING': return '终止中'
    case 'TERMINATED': return '已终止'
    default: return status
  }
}

const openTaskDetail = (row: TaskInstance) => {
  activeTask.value = row
  nodeSnapshots.value = []
  executionLogs.value = []
  effectiveConstraintView.value = null
  latestDetailLogId = 0
  monitorDrawerVisible.value = true
  
  fetchLogsAndSnapshots()
  fetchEffectiveConstraints(row.id)
  
  // Start polling detail details
  startDetailsPolling()
}

const fetchEffectiveConstraints = async (taskId = activeTask.value?.id, silent = false) => {
  if (!taskId) return
  if (!silent) loadingEffectiveConstraints.value = true
  try {
    const res = await axios.get(`/api/constraint/model/task/${taskId}`)
    if (!res.data?.success) throw new Error(res.data?.message || '加载有效约束模型失败')
    effectiveConstraintView.value = res.data.data
  } catch (error: any) {
    if (!silent) ElMessage.error(error.message || '加载有效约束模型失败')
  } finally {
    if (!silent) loadingEffectiveConstraints.value = false
  }
}

const TERMINAL_TASK_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'TERMINATED'])

const syncRuntimeRefresh = () => {
  stopDetailsPolling()
  if (!monitorDrawerVisible.value || activeTask.value?.taskStatus !== 'RUNNING' || TERMINAL_TASK_STATUSES.has(activeTask.value?.taskStatus || '')) return
  pollIntervalId = setInterval(() => {
    if (monitorDrawerVisible.value && activeTask.value?.taskStatus === 'RUNNING') fetchLogsAndSnapshots(true)
    else stopDetailsPolling()
  }, 1000)
}

const startDetailsPolling = syncRuntimeRefresh

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
    const logParams = latestDetailLogId > 0
      ? { afterLogId: latestDetailLogId, limit: 300 }
      : undefined
    const [snapRes, logRes] = await Promise.all([
      taskApi.snapshots(taskId),
      taskApi.executionLogs(taskId, logParams)
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
      
    }
    
    // Also sync the task instance itself in case status updated
    const taskRes = await taskApi.detail(taskId)
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
  if (!executableProcessTemplates.value.length) {
    ElMessage.warning('当前没有已启用的工作流，请先在流程设计器中保存并启用流程')
    return
  }
  createForm.value = { taskName: '', flowModelId: null, resourceBindings: {}, taskConstraints: [] }
  taskConstraintReviews.value = []
  createDrawerVisible.value = true
  nextTick(() => {
    createFormRef.value?.clearValidate()
  })
}

const openTaskConstraint = (index?:number) => {
  if (!createForm.value.flowModelId) return ElMessage.warning('请先选择关联流程')
  if (selectedWorkflowErrors.value.length) return ElMessage.error('流程模型存在设备接口连接错误，不能配置任务约束')
  if (selectedWorkflowRequirements.value.some(requirement => !createForm.value.resourceBindings[requirement.slotId])) return ElMessage.warning('请先完成全部设备实例绑定')
  editingTaskConstraintIndex.value = typeof index === 'number' ? index : null
  taskConstraintDialogVisible.value = true
}
const loadTaskConstraintEditor = () => taskConstraintEditorRef.value?.loadRule(editingTaskConstraintIndex.value == null ? null : createForm.value.taskConstraints[editingTaskConstraintIndex.value])
const saveTaskConstraint = () => {
  try {
    const rule = taskConstraintEditorRef.value?.validateAndBuild()
    if (!rule) return
    if (editingTaskConstraintIndex.value == null) {
      createForm.value.taskConstraints.push(rule)
      taskConstraintReviews.value.push(null)
    } else {
      createForm.value.taskConstraints.splice(editingTaskConstraintIndex.value,1,rule)
      taskConstraintReviews.value.splice(editingTaskConstraintIndex.value,1,null)
    }
    preflightResult.value = null
    taskConstraintDialogVisible.value = false
  } catch (error:any) { ElMessage.error(error.message || '任务约束配置不完整') }
}
const removeTaskConstraint = (index:number) => {
  createForm.value.taskConstraints.splice(index, 1)
  taskConstraintReviews.value.splice(index, 1)
  preflightResult.value = null
}
// Submit Create task
const submitCreateTask = async () => {
  if (!createFormRef.value) return
  if (unresolvedTaskConstraintReviews.value) return ElMessage.warning('请先复核受设备绑定变更影响的任务约束')
  try { await createFormRef.value.validate() } catch { return }
  creating.value = true
  try {
    const selectedWorkflow = processTemplates.value.find(item => item.id === Number(createForm.value.flowModelId))
    if (!isExecutableWorkflow(selectedWorkflow)) throw new Error('请选择已启用的工作流；草稿流程不能创建任务')
    if (selectedWorkflowErrors.value.length) throw new Error('流程模型存在设备接口连接错误，请先修复流程模型')
    for (const requirement of selectedWorkflowRequirements.value) {
      const instanceId = Number(createForm.value.resourceBindings[requirement.slotId])
      if (!Number.isInteger(instanceId) || instanceId <= 0) throw new Error(`请为${requirement.occurrencePath || requirement.nodeName}绑定设备实例`)
    }
    const payload = buildTaskCreatePayload(createForm.value, selectedWorkflowRequirements.value)
    const res = await axios.post('/api/task/save', payload)
    if (!res.data?.success) throw new Error(res.data?.message || '任务创建失败')
    ElMessage.success('任务创建成功')
    createDrawerVisible.value = false
    taskPageNo.value = 1
    refreshTaskList()
  } catch (error: any) {
    ElMessage.error('保存失败: ' + error.message)
  } finally {
    creating.value = false
  }
}

watch([monitorDrawerVisible, () => activeTask.value?.taskStatus], syncRuntimeRefresh)

// Format time utility

const formatTime = (timeStr: string) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  return date.toLocaleString()
}
const startMainListPolling = () => {
  if (mainListPollIntervalId) return
  mainListPollIntervalId = setInterval(() => {
    if (!loading.value) Promise.all([fetchTasks(true), fetchTaskSummary(true)])
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
  Promise.all([fetchAllInstances(), fetchAllModels()]).catch((error:any) => ElMessage.error(error.message || '加载设备资源失败'))
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
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0 20px;
}

.monitor-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;
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
.task-filter-bar { display:flex; gap:10px; margin-bottom:12px; }.task-filter-bar .el-input { width:280px; }.task-filter-bar .el-select { width:150px; }

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
@media (max-width: 800px) { .task-filter-bar { display:flex; gap:10px; margin-bottom:12px; }.task-filter-bar .el-input { width:280px; }.task-filter-bar .el-select { width:150px; }

.task-list-fullscreen { padding: 12px; } .task-summary-strip { grid-template-columns: repeat(2, 1fr); } }
.section-title-row{display:flex;align-items:center;justify-content:space-between}.resource-instance-select{flex:1;min-width:240px}.resource-sub{margin-top:4px;color:#64748b;font-size:11px}.task-constraint-row{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:11px 12px;border-bottom:1px solid #e5e7eb}.task-constraint-row>div:first-child{display:grid;gap:4px}.task-constraint-row code{font-family:Consolas,monospace;color:#2563eb}.task-constraint-row span{color:#64748b;font-size:11px}
.effective-constraint-panel{display:grid;gap:12px}.effective-constraint-toolbar,.effective-group-title,.effective-rule-row{display:flex;align-items:center;justify-content:space-between;gap:12px}.effective-constraint-toolbar>div:first-child{display:grid;gap:3px}.effective-constraint-toolbar>div:first-child span,.effective-model-meta,.effective-rule-row span{color:#64748b;font-size:11px}.effective-constraint-toolbar .el-button+.el-button{margin-left:8px}.effective-constraint-group{border:1px solid #e5e7eb;border-radius:6px;background:#fff}.effective-group-title{padding:10px 12px;border-bottom:1px solid #edf0f3;background:#fafbfc}.effective-group-title>div{display:flex;align-items:center;gap:8px}.effective-group-title>span{color:#64748b;font-size:12px}.effective-rule-row{padding:10px 12px;border-bottom:1px solid #edf0f3}.effective-rule-row:last-child{border-bottom:0}.effective-rule-row>div:first-child{min-width:0;display:grid;gap:4px}.effective-rule-row code{overflow:hidden;color:#2563eb;font-family:Consolas,monospace;text-overflow:ellipsis;white-space:nowrap}
/* Task operations workspace */
.task-list-fullscreen { padding: 20px 24px 24px; display: flex; flex-direction: column; overflow: hidden; background: #f6f7f9; }
.table-card-fullscreen { flex: 1; min-height: 0; height: auto; border: 1px solid #e7e9ee !important; border-radius: 10px !important; overflow: hidden; box-shadow: 0 1px 2px rgba(15, 23, 42, .02); }
.table-card-fullscreen :deep(.el-card__header) { min-height: 64px; padding: 0 20px; border-bottom-color: #eceef2; background: #fff; }
.card-header-fullscreen { min-height: 64px; }
.header-copy { display: grid; gap: 2px; }
.header-title { margin: 0; color: #1f2329; font-size: 18px; font-weight: 600; letter-spacing: -.01em; line-height: 24px; }
.header-copy p { margin: 0; color: #8a93a6; font-size: 11px; line-height: 18px; }
.header-actions-right { gap: 10px; }
.header-actions-right .el-button { min-width: 94px; font-weight: 500; }
.header-actions-right .el-button + .el-button { margin-left: 0; }
.task-summary-strip { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); border: 0; border-bottom: 1px solid #eceef2; border-radius: 0; background: #fff; }
.task-summary-strip > button { min-width: 0; min-height: 76px; padding: 12px 20px; border: 0; border-right: 1px solid #f0f1f3; position: relative; display: grid; grid-template-columns: 1fr auto; align-content: center; gap: 2px 10px; color: inherit; text-align: left; background: #fff; cursor: pointer; transition: background .15s ease; }
.task-summary-strip > button:last-child { border-right: 0; }
.task-summary-strip > button:hover { background: #f8fafc; }
.task-summary-strip > button.active { background: #f1f7ff; }
.task-summary-strip > button.active::after { content: ''; position: absolute; right: 12px; bottom: 0; left: 12px; height: 2px; border-radius: 2px 2px 0 0; background: #1677ff; }
.task-summary-strip span { display: flex; align-items: center; gap: 6px; color: #6b7280; font-size: 12px; }
.task-summary-strip strong { grid-row: 1 / 3; grid-column: 2; align-self: center; color: #111827; font-size: 22px; line-height: 28px; }
.task-summary-strip small { overflow: hidden; color: #9ca3af; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.summary-dot { width: 7px; height: 7px; border-radius: 50%; background: #94a3b8; }
.summary-dot.pending { background: #8a93a6; }
.summary-dot.running { background: #1677ff; box-shadow: 0 0 0 3px rgba(22, 119, 255, .12); }
.summary-dot.success { background: #16a34a; }
.summary-dot.danger { background: #dc2626; }
.task-filter-bar { min-height: 60px; margin: 0; padding: 12px 20px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px solid #eceef2; background: #fff; }
.task-filter-fields { min-width: 0; display: flex; align-items: center; gap: 8px; }
.task-filter-fields .el-input { width: 300px; }
.task-filter-fields .el-select { width: 150px; }
.task-filter-fields .el-button + .el-button { margin-left: 0; }
.task-result-meta { flex: 0 0 auto; color: #8a93a6; font-size: 11px; white-space: nowrap; }
.custom-table { flex: 1; min-height: 0; cursor: default; }
.custom-table :deep(.el-table__inner-wrapper::before) { display: none; }
.custom-table :deep(th.el-table__cell) { height: 44px; background: #f7f8fa !important; }
.custom-table :deep(td.el-table__cell) { height: 58px; padding: 6px 0; border-bottom-color: #f0f1f3; }
.custom-table :deep(.el-table__row) { cursor: default; }
.task-id { color: #8a93a6; font-family: Consolas, monospace; font-size: 11px; }
.task-name { overflow: hidden; color: #111827; font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.task-description { margin-top: 2px; overflow: hidden; color: #8a93a6; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.status-badge-container { gap: 6px; }
.status-tag { min-width: 62px; justify-content: center; border-radius: 10px !important; font-weight: 600; box-shadow: none; }
.status-tag.status-running { color: #125bb5; border-color: #bad7fb; background: #edf5ff; }
.status-tag.status-succeeded { color: #137a45; border-color: #bde5ce; background: #eefaf3; }
.status-tag.status-failed { color: #b42318; border-color: #f2c4c0; background: #fff2f0; }
.status-tag.status-terminated, .status-tag.status-terminating { color: #9a5b0a; border-color: #f1d2a5; background: #fff8e8; }
.status-tag.status-pending { color: #5b6472; border-color: #d8dde5; background: #f5f6f8; }
.pulse-dot { width: 6px; height: 6px; }
.time-range-cell { color: #4b5563; line-height: 1.55; }
.time-label { color: #9ca3af; }
.action-buttons { gap: 2px; }
.action-buttons .el-button + .el-button { margin-left: 0; }
.detail-button { font-weight: 600; }
.pagination-bar { min-height: 56px; padding: 11px 20px; background: #fff; }

@media (max-width: 1050px) {
  .task-summary-strip { grid-template-columns: repeat(5, minmax(140px, 1fr)); overflow-x: auto; }
  .task-result-meta { display: none; }
}
@media (max-width: 800px) {
  .task-list-fullscreen { padding: 12px; }
  .table-card-fullscreen :deep(.el-card__header) { padding: 0 14px; }
  .header-actions-right .el-button { min-width: auto; }
  .task-filter-bar { align-items: stretch; }
  .task-filter-fields { flex: 1; flex-wrap: wrap; }
  .task-filter-fields .el-input { flex: 1; width: 220px; }
}
</style>
