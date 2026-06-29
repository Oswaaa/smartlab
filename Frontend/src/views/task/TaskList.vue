<template>
  <div class="task-list">
    <!-- Header Summary Toolbar -->
    <div class="header-actions">
      <div>
        <h2 class="title">任务中心</h2>
        <p class="subtitle">管理任务实例、约束配置和执行进度</p>
      </div>
      <el-button type="primary" class="premium-btn" @click="openCreateDrawer">
        <el-icon class="mr-1"><Plus /></el-icon> 新建任务
      </el-button>
    </div>

    <!-- Quick Stats Cards -->
    <el-row :gutter="20" class="mb-6">
      <el-col :span="6">
        <div class="stat-card stat-total">
          <span class="stat-label">任务总数</span>
          <span class="stat-val">{{ stats.total }}</span>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-running">
          <span class="stat-label">正在执行</span>
          <span class="stat-val text-blue">{{ stats.running }}</span>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-completed">
          <span class="stat-label">已成功完成</span>
          <span class="stat-val text-green">{{ stats.completed }}</span>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-failed">
          <span class="stat-label">异常与终止</span>
          <span class="stat-val text-red">{{ stats.failed }}</span>
        </div>
      </el-col>
    </el-row>

    <!-- Main Table Card -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <el-button @click="refreshTaskList" :loading="loading" size="small">
            <el-icon><Refresh /></el-icon> 刷新列表
          </el-button>
        </div>
      </template>

      <el-table
        :data="tasks"
        v-loading="loading"
        style="width: 100%"
        border
        stripe
        highlight-current-row
        @row-click="handleRowClick"
        class="custom-table"
      >
        <el-table-column prop="taskId" label="任务编号" width="120" align="center" />
        <el-table-column prop="taskName" label="任务名称" min-width="160" />
        <el-table-column prop="templateId" label="关联流程" min-width="180">
          <template #default="{ row }">
            <span class="font-semibold text-slate-700">{{ getWorkflowName(row.templateId) }}</span>
            <span class="block text-xs text-slate-400 font-mono">{{ row.templateId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="currentStatus" label="生命周期状态" width="130" align="center">
          <template #default="{ row }">
            <div class="status-badge-container">
              <span :class="['pulse-dot', (row.currentStatus || '').toLowerCase()]" v-if="row.currentStatus === 'RUNNING'"></span>
              <el-tag :type="getStatusType(row.currentStatus)" effect="dark" class="status-tag">
                {{ getStatusLabel(row.currentStatus) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="任务执行区间" min-width="260">
          <template #default="{ row }">
            <div class="time-range-cell">
              <div><span class="time-label">始：</span>{{ formatTime(row.startTime) }}</div>
              <div v-if="row.endTime"><span class="time-label">终：</span>{{ formatTime(row.endTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons" @click.stop>
              <el-button
                type="primary"
                size="small"
                plain
                :disabled="row.currentStatus !== 'PENDING'"
                @click="startTask(row.taskId)"
              >
                启动
              </el-button>
              <el-button
                type="warning"
                size="small"
                plain
                :disabled="!['PENDING', 'RUNNING'].includes(row.currentStatus)"
                @click="abortTask(row.taskId)"
              >
                终止
              </el-button>
              <el-button
                type="danger"
                size="small"
                plain
                @click="confirmDeleteTask(row.taskId)"
              >
                注销
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
    <el-drawer v-model="createDrawerVisible" title="新建任务" size="480px">
      <el-form
        :model="createForm"
        :rules="createRules"
        ref="createFormRef"
        label-width="120px"
        label-position="top"
        class="create-form"
      >
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="createForm.taskName" placeholder="例如：批次PCR扩增与物料搅拌" />
        </el-form-item>
        <el-form-item label="关联流程" prop="templateId">
          <el-select
            v-model="createForm.templateId"
            placeholder="请选择流程"
            style="width: 100%"
            v-loading="loadingWorkflows"
          >
            <el-option
              v-for="tpl in processTemplates"
              :key="tpl.templateId"
              :label="tpl.templateName"
              :value="tpl.templateId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务补充约束（JSON）" prop="constraintsText">
          <div class="schema-info-bar">
            <span>留空表示不配置补充约束。</span>
          </div>
          <el-input
            v-model="createForm.constraintsText"
            type="textarea"
            :rows="12"
            class="monospace-textarea"
            placeholder='{
  "observableObjects_O": [],
  "handlingActions_H": [],
  "globalConstraints_B0": [],
  "taskRequirements_U": {
    "goals_G": [],
    "taskConstraints_Btau": []
  }
}'
          />
        </el-form-item>
      </el-form>
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
    <el-drawer v-model="monitorDrawerVisible" :title="`任务详情: ${activeTask?.taskName || ''}`" size="640px">
      <div v-if="activeTask" class="monitor-container">
        <!-- Meta Cards -->
        <el-descriptions border :column="2" size="small" class="mb-4">
          <el-descriptions-item label="任务编号">{{ activeTask.taskId }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusType(activeTask.currentStatus)" size="small" effect="dark">
              {{ getStatusLabel(activeTask.currentStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="起始时间" :span="2">
            {{ formatTime(activeTask.startTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="结束时间" :span="2" v-if="activeTask.endTime">
            {{ formatTime(activeTask.endTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- Drawer Content Tabs -->
        <el-tabs v-model="monitorActiveTab" class="monitor-tabs">
          <!-- Tab 1: Nodes Progress Map -->
          <el-tab-pane label="节点状态快照" name="snapshots">
            <div class="snapshots-timeline" v-loading="loadingDetails">
              <div v-for="node in nodeSnapshots" :key="node.snapshotId" class="node-snapshot-card">
                <div class="node-snap-header">
                  <span class="node-name">节点: {{ node.nodeId }}</span>
                  <el-tag :type="getNodeStateType(node.lifecycleState)" size="small" effect="plain">
                    {{ node.lifecycleState }}
                  </el-tag>
                </div>
                <div class="node-snap-body">
                  <div class="snap-row">
                    <span class="snap-label">绑定设备:</span>
                    <span class="snap-val font-mono">{{ node.boundInstanceId || '未绑定' }}</span>
                  </div>
                  <div class="snap-row" v-if="node.internalVariables && Object.keys(node.internalVariables).length > 0">
                    <span class="snap-label">内部变量:</span>
                    <div class="snap-tags mt-1">
                      <el-tag
                        v-for="(val, key) in node.internalVariables"
                        :key="key"
                        type="info"
                        size="small"
                        class="mr-2 mb-1"
                      >
                        {{ key }}: {{ val }}
                      </el-tag>
                    </div>
                  </div>
                  <div class="snap-row text-xs text-slate-400 mt-2">
                    更新时间: {{ formatTime(node.updateTime) }}
                  </div>
                </div>
              </div>
              <el-empty v-if="nodeSnapshots.length === 0" description="未检测到活跃节点快照" />
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
              <div v-for="log in executionLogs" :key="log.logId" class="log-item">
                <span class="log-time">[{{ formatLogTime(log.occurTime) }}]</span>
                <span class="log-module">[{{ log.sourceModule || 'TASK' }}]</span>
                <span :class="['log-level', log.logLevel.toLowerCase()]">{{ log.logLevel }}</span>
                <span class="log-msg">{{ log.message }}</span>
              </div>
              <div v-if="executionLogs.length === 0" class="empty-terminal">
                &gt; 暂无任务日志。
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 3: Supplemental constraints mapping -->
          <el-tab-pane label="需求约束" name="constraints">
            <div class="constraints-section">
              <h4 class="section-title">任务补充约束</h4>
              <pre class="constraints-code"><code>{{ activeTask.globalConstraints && Object.keys(activeTask.globalConstraints).length > 0 ? JSON.stringify(activeTask.globalConstraints, null, 2) : '// 未配置补充约束' }}</code></pre>
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
import { Plus, Refresh } from '@element-plus/icons-vue'

interface TaskInstance {
  taskId: number
  taskName: string
  templateId: string
  globalConstraints: Record<string, any>
  currentStatus: string
  startTime: string
  endTime?: string
}

interface TaskNodeSnapshot {
  snapshotId: number
  taskId: number
  nodeId: string
  lifecycleState: string
  boundInstanceId?: string
  internalVariables: Record<string, any>
  updateTime: string
}

interface TaskExecutionLog {
  logId: number
  taskId: number
  logLevel: string
  sourceModule?: string
  occurTime: string
  message: string
}

interface WorkflowTemplate {
  templateId: string
  templateName: string
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
  completed: 0,
  failed: 0,
  aborted: 0
})
const processTemplates = ref<WorkflowTemplate[]>([])
const loadingWorkflows = ref(false)

// Drawer state
const createDrawerVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({
  taskName: '',
  templateId: '',
  constraintsText: ''
})
const creating = ref(false)

// Monitor Drawer
const monitorDrawerVisible = ref(false)
const activeTask = ref<TaskInstance | null>(null)
const monitorActiveTab = ref('snapshots')
const nodeSnapshots = ref<TaskNodeSnapshot[]>([])
const executionLogs = ref<TaskExecutionLog[]>([])
const loadingDetails = ref(false)
const logContainerRef = ref<HTMLElement | null>()

// Auto refresh interval id
let pollIntervalId: any = null
let latestDetailLogId = 0

// Form rules
const createRules = ref<FormRules>({
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  templateId: [{ required: true, message: '请选择流程', trigger: 'change' }],
  constraintsText: [
    {
      validator: (rule, value, callback) => {
        if (!value || value.trim() === '') {
          callback()
          return
        }
        try {
          const parsed = JSON.parse(value)
          if (typeof parsed !== 'object' || parsed === null) {
            callback(new Error('补充约束必须是合法的 JSON 对象'))
          } else {
            callback()
          }
        } catch (e) {
          callback(new Error('JSON 语法错误，请检查'))
        }
      },
      trigger: 'blur'
    }
  ]
})

// Stats computed
const stats = computed(() => {
  return {
    total: taskSummary.value.total,
    running: taskSummary.value.running,
    completed: taskSummary.value.completed,
    failed: taskSummary.value.failed + taskSummary.value.aborted
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
    } else {
      ElMessage.error(res.data?.message || '加载任务列表失败')
    }
  } catch (error: any) {
    ElMessage.error('无法加载任务数据: ' + error.message)
  } finally {
    loading.value = false
  }
}

const fetchTaskSummary = async () => {
  try {
    const res = await axios.get('/api/task/summary')
    if (res.data?.success) {
      taskSummary.value = {
        total: res.data.data?.total || 0,
        pending: res.data.data?.pending || 0,
        running: res.data.data?.running || 0,
        completed: res.data.data?.completed || 0,
        failed: res.data.data?.failed || 0,
        aborted: res.data.data?.aborted || 0
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
const getWorkflowName = (tplId: string) => {
  const match = processTemplates.value.find(w => w.templateId === tplId)
  return match ? match.templateName : tplId
}

// Status Badges helpers
const getStatusType = (status: string) => {
  switch (status) {
    case 'PENDING': return 'info'
    case 'RUNNING': return 'primary'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'ABORTED': return 'warning'
    default: return 'info'
  }
}

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'PENDING': return '排队中'
    case 'RUNNING': return '运行中'
    case 'COMPLETED': return '完成'
    case 'FAILED': return '终止'
    case 'ABORTED': return '已终止'
    default: return status
  }
}

const getNodeStateType = (state: string) => {
  switch (state) {
    case 'PENDING': return 'info'
    case 'RUNNING': return 'warning'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'ABORTED': return 'warning'
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
    const taskId = activeTask.value.taskId
    
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
      latestDetailLogId = executionLogs.value.reduce((max, log) => Math.max(max, Number(log.logId || 0)), latestDetailLogId)
      
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
      activeTask.value.currentStatus = updated.currentStatus
      activeTask.value.startTime = updated.startTime
      activeTask.value.endTime = updated.endTime
      
      // Find matching item in main list and sync
      const mainMatch = tasks.value.find(t => t.taskId === taskId)
      if (mainMatch) {
        mainMatch.currentStatus = updated.currentStatus
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
    const res = await axios.post(`/api/task/abort/${taskId}`)
    if (res.data?.success) {
      ElMessage.success('任务已终止')
      await refreshTaskList()
      if (activeTask.value?.taskId === taskId) {
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
      if (activeTask.value?.taskId === taskId) {
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

// Open creation drawer
const openCreateDrawer = () => {
  createForm.value = {
    taskName: '',
    templateId: '',
    constraintsText: ''
  }
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
        let globalConstraintsVal: Record<string, any> = {}
        if (createForm.value.constraintsText && createForm.value.constraintsText.trim() !== '') {
          globalConstraintsVal = JSON.parse(createForm.value.constraintsText)
        }
        
        const payload = {
          taskName: createForm.value.taskName,
          templateId: createForm.value.templateId,
          globalConstraints: globalConstraintsVal
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

const formatLogTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return [
    String(date.getHours()).padStart(2, '0'),
    String(date.getMinutes()).padStart(2, '0'),
    String(date.getSeconds()).padStart(2, '0')
  ].join(':')
}

onMounted(() => {
  refreshTaskList()
  fetchWorkflows()
})

onUnmounted(() => {
  stopDetailsPolling()
})
</script>

<style scoped>
.task-list {
  padding: 24px;
  background-color: #f8fafc;
  min-height: 100vh;
  box-sizing: border-box;
  font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.title {
  margin: 0;
  color: #0f172a;
  font-weight: 700;
  font-size: 22px;
  letter-spacing: -0.025em;
}

.subtitle {
  margin: 4px 0 0 0;
  color: #64748b;
  font-size: 13px;
}

.mb-6 {
  margin-bottom: 24px;
}

.mb-4 {
  margin-bottom: 16px;
}

.mt-1 {
  margin-top: 4px;
}

.mt-2 {
  margin-top: 8px;
}

.mr-1 {
  margin-right: 4px;
}

.mr-2 {
  margin-right: 8px;
}

.mb-1 {
  margin-bottom: 4px;
}

.stat-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.stat-val {
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 4px;
}

.text-blue { color: #3b82f6; }
.text-green { color: #10b981; }
.text-red { color: #ef4444; }

.table-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #1e293b;
}

.custom-table :deep(.el-table__header-wrapper) th {
  background-color: #f8fafc;
  color: #334155;
  font-weight: 600;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.status-badge-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #3b82f6;
  box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7);
  animation: pulse 1.6s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7);
  }
  70% {
    transform: scale(1);
    box-shadow: 0 0 0 6px rgba(59, 130, 246, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0);
  }
}

.time-range-cell {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
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
  border-radius: 6px;
  font-weight: 500;
}

.create-form {
  padding: 0 10px;
}

.monospace-textarea :deep(textarea) {
  font-family: "Fira Code", Consolas, Monaco, monospace;
  font-size: 12px;
  background-color: #f8fafc;
  color: #0f172a;
}

.schema-info-bar {
  background-color: #eff6ff;
  border-left: 4px solid #3b82f6;
  padding: 8px 12px;
  font-size: 11px;
  color: #1e3a8a;
  margin-bottom: 8px;
  border-radius: 0 4px 4px 0;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.monitor-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
}

.monitor-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}

:deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding-top: 12px;
}

.snapshots-timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}

.node-snapshot-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px 16px;
}

.node-snap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #f1f5f9;
  margin-bottom: 8px;
}

.node-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.node-snap-body {
  font-size: 12px;
}

.snap-row {
  margin-bottom: 6px;
  display: flex;
  flex-direction: column;
}

.snap-label {
  color: #64748b;
  font-weight: 500;
  margin-bottom: 2px;
}

.snap-val {
  color: #0f172a;
}

.snap-tags {
  display: flex;
  flex-wrap: wrap;
}

.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 8px;
  font-weight: 500;
}

.log-container {
  background-color: #0f172a;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 6px;
  font-family: "Fira Code", Consolas, Monaco, monospace;
  font-size: 12px;
  height: 380px;
  overflow-y: auto;
  line-height: 1.6;
}

.log-item {
  margin-bottom: 6px;
  word-wrap: break-word;
}

.log-time {
  color: #64748b;
  margin-right: 8px;
}

.log-module {
  color: #38bdf8;
  margin-right: 8px;
  font-weight: bold;
}

.log-level {
  display: inline-block;
  width: 55px;
  font-weight: bold;
  text-transform: uppercase;
  margin-right: 8px;
}
.log-level.info { color: #3b82f6; }
.log-level.warn { color: #f59e0b; }
.log-level.error { color: #ef4444; }
.log-level.success { color: #10b981; }

.log-msg {
  color: #f1f5f9;
}

.empty-terminal {
  color: #64748b;
  font-style: italic;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.constraints-section {
  background-color: #fafafa;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 16px;
}

.section-title {
  margin: 0 0 12px 0;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.constraints-code {
  margin: 0;
  background-color: #0f172a;
  color: #38bdf8;
  padding: 16px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 12px;
  overflow-x: auto;
}
</style>


