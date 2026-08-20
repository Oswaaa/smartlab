<template>
  <div class="task-list-page">
    <div class="task-workbench-canvas">
      <!-- 顶部工具栏 (统一对齐 Adapter 与设备实例页面规范) -->
      <div class="workbench-header">
        <div class="header-left">
          <h2 class="header-title">任务列表</h2>
          <span class="header-subtitle">实验任务调度、设备资源绑定与实时执行跟踪</span>
        </div>
        <div class="header-actions">
          <el-select
            v-model="taskWorkflowFilter"
            size="small"
            class="workflow-filter"
            placeholder="全部流程"
            clearable
            @change="applyTaskFilters"
          >
            <el-option label="全部流程" value="" />
            <el-option
              v-for="wf in executableProcessTemplates"
              :key="wf.id"
              :label="wf.flowName"
              :value="wf.id"
            />
          </el-select>
          <el-select
            v-model="taskStatusFilter"
            size="small"
            class="lifecycle-filter"
            placeholder="全部状态"
            clearable
            @change="applyTaskFilters"
          >
            <el-option label="全部状态" value="" />
            <el-option label="排队中" value="PENDING" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="SUCCEEDED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已终止" value="TERMINATED" />
          </el-select>
          <el-input
            v-model="taskKeyword"
            class="task-search"
            size="small"
            clearable
            placeholder="搜索任务名称或说明..."
            :prefix-icon="Search"
            @keyup.enter="applyTaskFilters"
            @clear="applyTaskFilters"
          />
          <button class="btn-aliyun" type="button" :disabled="loading" @click="refreshTaskList">
            <el-icon><Refresh /></el-icon><span>刷新</span>
          </button>
          <button class="btn-aliyun-cta" type="button" @click="openCreateDrawer">
            <el-icon><Plus /></el-icon><span>新建任务</span>
          </button>
        </div>
      </div>

      <!-- 任务列表微边距表格卡片 -->
      <div class="table-scroll-container">
        <div class="table-card" v-loading="loading">
          <el-table
            :data="tasks"
            size="small"
            style="width: 100%; height: 100%;"
            height="100%"
            class="task-table"
            row-key="id"
          >
            <el-table-column prop="id" label="ID" width="72" align="center">
              <template #default="{ row }">
                <span class="task-id">#{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="taskName" label="任务名称" min-width="160">
              <template #default="{ row }">
                <div class="task-name">{{ row.taskName }}</div>
                <div v-if="row.taskDesc" class="task-description">{{ row.taskDesc }}</div>
              </template>
            </el-table-column>
            <el-table-column label="关联流程" min-width="160">
              <template #default="{ row }">
                <div v-if="getWorkflowName(row.flowModelId) !== String(row.flowModelId)" class="workflow-name">{{ getWorkflowName(row.flowModelId) }}</div>
                <div class="workflow-id">ID: {{ row.flowModelId || '-' }}</div>
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
            <el-table-column label="当前节点" min-width="120" align="center">
              <template #default="{ row }">
                <span v-if="row.currentNodeIdRef != null" class="node-badge" :title="`节点 ID: ${row.currentNodeIdRef}`">
                  {{ getNodeName(row) }}
                </span>
                <span v-else class="text-disabled">-</span>
              </template>
            </el-table-column>
            <el-table-column label="执行周期" min-width="200">
              <template #default="{ row }">
                <div class="time-range-cell">
                  <div><span class="time-label">始：</span><span class="mono-time">{{ formatTime(row.startTime) }}</span></div>
                  <div v-if="row.endTime"><span class="time-label">终：</span><span class="mono-time">{{ formatTime(row.endTime) }}</span></div>
                  <div v-else-if="row.taskStatus === 'RUNNING'" class="time-running">运行中...</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="关联设备" width="105" align="center">
              <template #default="{ row }">
                <span v-if="getBoundDevicesText(row)" class="count-pill green">
                  {{ getBoundDevicesText(row) }}
                </span>
                <span v-else class="text-disabled">未绑定</span>
              </template>
            </el-table-column>
            <el-table-column label="任务约束" width="95" align="center">
              <template #default="{ row }">
                <span v-if="row.taskConstraints?.length" class="count-pill warning">
                  {{ row.taskConstraints.length }} 条
                </span>
                <span v-else class="text-disabled">无</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-buttons" @click.stop>
                  <button v-if="row.taskStatus === 'PENDING'" class="btn-link" type="button" @click="startTask(row.id)">启动</button>
                  <button v-if="['RUNNING', 'PAUSED'].includes(row.taskStatus)" class="btn-link danger" type="button" @click="abortTask(row.id)">终止</button>
                  <button class="btn-link" type="button" @click="openTaskDetail(row)">详情</button>
                  <button class="btn-link danger" type="button" @click="confirmDeleteTask(row.id)">删除</button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

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
      </div>
    </div>

    <TaskCreateDrawer ref="createFormRef" v-model="createDrawerVisible" :form="createForm" :workflows="executableProcessTemplates" :loading-workflows="loadingWorkflows" :requirements="selectedWorkflowRequirements" :groups="selectedWorkflowGroups" :errors="selectedWorkflowErrors" :instances="deviceInstances" :models="deviceModels" :preflight-result="preflightResult" :preflighting="preflighting" :creating="creating" :constraint-reviews="taskConstraintReviews" @update:task-name="createForm.taskName = $event" @update:flow-model-id="handleTemplateChange" @update:resource-bindings="updateResourceBindings" @edit-constraint="openTaskConstraint" @remove-constraint="removeTaskConstraint" @preflight="runPreflight" @submit="submitCreateTask" />

    <el-dialog v-model="taskConstraintDialogVisible" :title="editingTaskConstraintIndex == null ? '添加任务级约束' : '编辑任务级约束'" width="1040px" append-to-body destroy-on-close @opened="loadTaskConstraintEditor">
      <ConstraintRuleEditor ref="taskConstraintEditorRef" :models="deviceModels" :instances="deviceInstances" :workflows="executableProcessTemplates" :tasks="[]" task-mode :task-resources="selectedTaskResources" :task-workflow-nodes="selectedWorkflowNodes" />
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <button class="btn-aliyun" type="button" @click="taskConstraintDialogVisible=false">取消</button>
          <button class="btn-primary-blue" type="button" @click="saveTaskConstraint">保存任务约束</button>
        </div>
      </template>
    </el-dialog>

    <TaskExecutionDrawer v-model="monitorDrawerVisible" :task="activeTask" :workflow="activeWorkflowDefinition" :steps="nodeSnapshots" :logs="executionLogs" :bindings="activeDeviceRoutes" :constraints="effectiveConstraintView" :loading="loadingDetails" @refresh="fetchLogsAndSnapshots()" @terminate="activeTask && abortTask(activeTask.id)" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import ConstraintRuleEditor from '../../../components/constraint/ConstraintRuleEditor.vue'
import TaskCreateDrawer from './components/TaskCreateDrawer.vue'
import TaskExecutionDrawer from './components/TaskExecutionDrawer.vue'
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
import { useAuthStore } from '../../../stores/authStore.js'

const authStore = useAuthStore()

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
const taskWorkflowFilter = ref<number | string>('')
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
        status: taskStatusFilter.value || undefined,
        flowModelId: taskWorkflowFilter.value || undefined
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
const getNodeName = (row: any) => {
  if (row.currentNodeIdRef == null) return '-'
  const flowId = String(row.flowModelId)
  const nodes = workflowNodes.value[flowId] || []
  const matched = nodes.find(
    (n: any) =>
      String(n.id) === String(row.currentNodeIdRef) ||
      String(n.nodeId) === String(row.currentNodeIdRef) ||
      String(n.stepId) === String(row.currentNodeIdRef)
  )
  if (matched) {
    return matched.nodeName || matched.name || matched.actionName || matched.actionType || matched.type || `节点 ${row.currentNodeIdRef}`
  }
  return `节点 (${row.currentNodeIdRef})`
}

const getBoundDevicesText = (row: any) => {
  const flowId = String(row.flowModelId)
  const routes = workflowDeviceRoutes.value[flowId]
  if (routes && routes.length > 0) {
    return `${routes.length} 台设备`
  }
  const reqs = workflowRequirementsByFlow.value[flowId]
  if (reqs && reqs.length > 0) {
    return `${reqs.length} 台设备`
  }
  return null
}

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

let taskEventSource: EventSource | null = null

const startTaskSseStream = (taskId: number) => {
  stopTaskSseStream()
  if (!taskId) return
  try {
    const token = authStore.token
    const url = token
      ? `/api/task/stream/${taskId}?token=${encodeURIComponent(token)}`
      : `/api/task/stream/${taskId}`
    taskEventSource = new EventSource(url)

    taskEventSource.addEventListener('log', (event: MessageEvent) => {
      try {
        const logData = JSON.parse(event.data)
        if (logData && !executionLogs.value.some(l => l.id === logData.id)) {
          executionLogs.value = [...executionLogs.value, logData]
          latestDetailLogId = Math.max(latestDetailLogId, Number(logData.id || 0))
        }
      } catch {}
    })

    taskEventSource.addEventListener('step', (event: MessageEvent) => {
      try {
        const stepData = JSON.parse(event.data)
        if (stepData) {
          const existing = nodeSnapshots.value.find(s => s.id === stepData.taskStepId || (s.nodeIdRef && s.nodeIdRef === stepData.nodeIdRef))
          if (existing) {
            existing.nodeStatus = stepData.nodeLifecycleState
            if (stepData.variableSpace) existing.variableSpace = stepData.variableSpace
          } else {
            taskApi.snapshots(taskId).then(snapRes => {
              if (snapRes.data?.success) nodeSnapshots.value = snapRes.data.data || []
            }).catch(() => {})
          }
        }
      } catch {}
    })

    taskEventSource.addEventListener('task', (event: MessageEvent) => {
      try {
        const taskData = JSON.parse(event.data)
        if (taskData && activeTask.value) {
          activeTask.value.taskStatus = taskData.taskStatus
          const mainMatch = tasks.value.find(t => t.id === taskId)
          if (mainMatch) {
            mainMatch.taskStatus = taskData.taskStatus
          }
          if (TERMINAL_TASK_STATUSES.has(taskData.taskStatus)) {
            fetchLogsAndSnapshots(true)
            stopTaskSseStream()
          }
        }
      } catch {}
    })

    taskEventSource.onerror = () => {
      // EventSource 自动重连
    }
  } catch (e) {
    console.error('Task SSE initialization error', e)
  }
}

const stopTaskSseStream = () => {
  if (taskEventSource) {
    taskEventSource.close()
    taskEventSource = null
  }
}

const syncRuntimeRefresh = () => {
  stopDetailsPolling()
  if (!monitorDrawerVisible.value || !activeTask.value) return
  if (TERMINAL_TASK_STATUSES.has(activeTask.value.taskStatus || '')) {
    stopTaskSseStream()
    return
  }
  startTaskSseStream(activeTask.value.id)
}

const startDetailsPolling = syncRuntimeRefresh

const stopDetailsPolling = () => {
  stopTaskSseStream()
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
/* ==================== Task List Workbench SSOT (Release 1.3) ==================== */
.task-list-page {
  height: 100%;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  background: var(--sl-bg-page, #f1f5f9);
  display: flex;
  flex-direction: column;
}

.task-workbench-canvas {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  background: var(--sl-bg-surface, #ffffff);
  border: 1px solid var(--sl-border-base, #e2e8f0);
  overflow: hidden;
  box-sizing: border-box;
}

/* 顶部工具栏 (52px 标准高度) */
.workbench-header {
  height: 52px;
  min-height: 52px;
  padding: 0 16px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  gap: 16px;
  box-sizing: border-box;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex-shrink: 0;
}

.header-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
  line-height: 1.2;
}

.header-subtitle {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  line-height: 1.2;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.workflow-filter {
  width: 140px !important;
  flex-shrink: 0;
}

.lifecycle-filter {
  width: 125px !important;
  flex-shrink: 0;
}

.task-search {
  width: 220px !important;
  flex-shrink: 0;
}

/* 阿里云风按钮 */
.btn-aliyun {
  height: 30px;
  padding: 0 12px;
  background: #ffffff;
  border: 1px solid var(--sl-border-input, #cbd5e1);
  border-radius: var(--sl-radius-sm, 6px);
  color: var(--sl-text-heading, #0f172a);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.15s ease;
  white-space: nowrap;
  flex-shrink: 0;
}
.btn-aliyun:hover:not(:disabled) {
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
  background: var(--sl-bg-hover, #f8fafc);
}
.btn-aliyun:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-aliyun-cta {
  height: 30px;
  padding: 0 14px;
  background: #ffffff;
  border: 1px solid var(--sl-primary, #2563eb);
  border-radius: var(--sl-radius-sm, 6px);
  color: var(--sl-primary, #2563eb);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.15s ease;
  white-space: nowrap;
  flex-shrink: 0;
}
.btn-aliyun-cta:hover {
  background: var(--sl-primary, #2563eb);
  color: #ffffff;
}

.btn-primary-blue {
  height: 32px;
  padding: 0 16px;
  background: var(--sl-primary, #2563eb);
  border: 1px solid var(--sl-primary, #2563eb);
  border-radius: var(--sl-radius-sm, 6px);
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.15s ease;
  white-space: nowrap;
}
.btn-primary-blue:hover {
  background: var(--sl-primary-hover, #1d4ed8);
}

.btn-link {
  border: none;
  background: transparent;
  color: var(--sl-primary, #2563eb);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  padding: 2px 4px;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  transition: color 0.15s ease;
}
.btn-link:hover {
  text-decoration: underline;
}
.btn-link.danger {
  color: var(--sl-danger, #dc2626);
}

/* ── 表格全铺满容器 ── */
.table-scroll-container {
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-card {
  flex: 1;
  min-height: 0;
  border: none;
  border-radius: 0;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: none;
}

.task-table {
  flex: 1;
  width: 100%;
  font-size: 12.5px;
}

.task-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.task-table :deep(th.el-table__cell) {
  height: 38px;
  padding: 0;
  background: #f8fafc !important;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}

.task-table :deep(td.el-table__cell) {
  padding: 6px 0;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  color: var(--sl-text-body, #334155);
}

.task-table :deep(.el-table__row:hover > td.el-table__cell) {
  background-color: var(--sl-bg-hover, #f8fafc) !important;
}

.task-id {
  color: var(--sl-text-secondary, #64748b);
  font-family: var(--sl-font-mono, monospace);
  font-size: 11.5px;
  font-weight: 500;
}

.task-name {
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-description {
  margin-top: 2px;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-name {
  color: var(--sl-text-body, #334155);
  font-size: 12.5px;
  font-weight: 500;
}

.workflow-id {
  font-size: 11px;
  color: var(--sl-text-disabled, #94a3b8);
  font-family: var(--sl-font-mono, monospace);
}

.status-badge-container {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-tag {
  border-radius: var(--sl-radius-sm, 6px) !important;
  font-size: 11px;
  font-weight: 500;
  box-shadow: none;
}

.status-tag.status-running {
  color: #1d4ed8;
  border-color: #bfdbfe;
  background: #eff6ff;
}

.status-tag.status-succeeded {
  color: var(--sl-success, #16a34a);
  border-color: var(--sl-success-border, #bbf7d0);
  background: var(--sl-success-light, #f0fdf4);
}

.status-tag.status-failed {
  color: var(--sl-danger, #dc2626);
  border-color: var(--sl-danger-border, #fecaca);
  background: var(--sl-danger-light, #fef2f2);
}

.status-tag.status-terminated,
.status-tag.status-terminating {
  color: var(--sl-warning, #d97706);
  border-color: var(--sl-warning-border, #fde68a);
  background: var(--sl-warning-light, #fffbeb);
}

.status-tag.status-pending {
  color: var(--sl-text-secondary, #64748b);
  border-color: var(--sl-border-input, #cbd5e1);
  background: #f8fafc;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sl-primary, #2563eb);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
}

.node-badge {
  display: inline-block;
  padding: 1px 6px;
  font-family: var(--sl-font-mono, monospace);
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  background: #f1f5f9;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: 4px;
}

.time-range-cell {
  font-size: 11.5px;
  line-height: 1.45;
  color: var(--sl-text-body, #334155);
}

.time-label {
  color: var(--sl-text-secondary, #64748b);
}

.mono-time {
  font-family: var(--sl-font-mono, monospace);
}

.time-running {
  color: var(--sl-primary, #2563eb);
  font-size: 11.5px;
  font-weight: 500;
}

.count-pill {
  display: inline-block;
  padding: 1px 7px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 10px;
  background: #f0fdf4;
  color: var(--sl-success, #16a34a);
  border: 1px solid var(--sl-success-border, #bbf7d0);
}

.count-pill.warning {
  background: #fffbeb;
  color: var(--sl-warning, #d97706);
  border: 1px solid var(--sl-warning-border, #fde68a);
}

.text-disabled {
  color: var(--sl-text-disabled, #94a3b8);
  font-size: 12px;
}

.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.pagination-bar {
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid var(--sl-border-base, #e2e8f0);
  background: var(--sl-bg-surface, #ffffff);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
}

@media (max-width: 800px) {
  .workbench-header {
    height: auto;
    padding: 10px 14px;
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }
  .task-search {
    flex: 1;
    width: auto !important;
  }
}
</style>
