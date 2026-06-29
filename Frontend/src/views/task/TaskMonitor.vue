<template>
  <div class="task-monitor">
    <!-- Page Header & Thin Summary Bar -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">
          任务监控
          <span class="live-indicator"><span class="live-dot"></span>实时</span>
        </h1>
        <div class="thin-summary-bar">
          <div class="summary-badge summary-badge--running">
            <span class="s-dot s-dot--running"></span>运行中: {{ runningTasks.length }}
          </div>
          <div class="summary-badge">
            <span class="s-dot"></span>等待执行: {{ pendingTasks.length }}
          </div>
          <div class="summary-badge summary-badge--completed">今日完成: {{ monitorSummary.todayCompletedCount }}</div>
          <div class="summary-badge summary-badge--failed">今日失败: {{ monitorSummary.todayFailedCount }}</div>
          <div class="refresh-time">上次更新：{{ lastRefreshedStr }}</div>
        </div>
      </div>
      <div class="header-right">
        <el-button
          :icon="refreshIconLoading ? Loading : Refresh"
          @click="refreshAll"
          :loading="refreshIconLoading"
          size="small"
          plain
        >
          立即刷新
        </el-button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="initialLoading" class="loading-screen">
      <el-icon class="spin-icon" size="24"><Loading /></el-icon>
      <span>正在加载监控数据...</span>
    </div>

    <template v-else>
      <!-- RUNNING TASKS -->
      <div class="section-header">
        <span class="section-title">运行中任务 ({{ runningTasks.length }})</span>
      </div>

      <div v-if="runningTasks.length === 0" class="empty-state">
        <el-icon size="24" color="#909399"><Odometer /></el-icon>
        <span class="empty-text">当前无正在执行的任务</span>
      </div>

      <div v-else class="running-grid">
        <div v-for="task in runningTasks" :key="task.taskId" class="task-card" @click="openLogDrawer(task)">
          <div class="card-header">
            <div class="card-id">#{{ task.taskId }}</div>
            <div class="card-status"><span class="live-pulse-dot"></span> 运行中</div>
          </div>
          <div class="card-body">
            <div class="task-name" :title="task.taskName">{{ task.taskName }}</div>
            <div class="task-meta">
              <span><el-icon><Clock /></el-icon> {{ formatTime(task.startTime) }}</span>
              <span class="elapsed-time"><el-icon><Timer /></el-icon> {{ getElapsed(task.startTime) }}</span>
            </div>
            <div class="progress-wrap">
              <div class="progress-label">执行进度 <span>实时</span></div>
              <div class="progress-bg">
                <div class="progress-fill progress-fill-running"></div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- PENDING TASKS -->
      <div class="section-header mt-4" v-if="pendingTasks.length > 0">
        <span class="section-title section-title--pending">等待执行 ({{ pendingTasks.length }})</span>
      </div>

      <div class="pending-list" v-if="pendingTasks.length > 0">
        <div v-for="task in pendingTasks" :key="task.taskId" class="pending-item">
          <div class="p-left">
            <el-icon class="p-icon"><Clock /></el-icon>
            <div class="p-info">
              <span class="p-name">{{ task.taskName }}</span>
              <span class="p-meta">ID: #{{ task.taskId }} | 流程: {{ task.templateId }}</span>
            </div>
          </div>
          <div class="p-right">
            <span class="status-badge">等待中</span>
          </div>
        </div>
      </div>
    </template>

    <!-- Live Log Drawer -->
    <el-drawer
      v-model="logDrawerVisible"
      direction="rtl"
      size="600px"
      class="log-drawer"
      :destroy-on-close="false"
      @close="stopLiveLogs"
    >
      <template #header>
        <div class="drawer-header-compact">
          <div class="drawer-title-area">
            <span class="drawer-task-name">{{ logDrawerTask?.taskName }}</span>
            <span class="card-status"><span class="live-pulse-dot"></span> 运行中</span>
          </div>
          <div class="drawer-meta">ID: #{{ logDrawerTask?.taskId }} | 已运行 {{ getElapsed(logDrawerTask?.startTime) }}</div>
        </div>
      </template>

      <div class="log-controls">
        <div class="log-stats">
          <span class="l-stat l-info">信息 {{ logCountByLevel('INFO') }}</span>
          <span class="l-stat l-warn">警告 {{ logCountByLevel('WARN') }}</span>
          <span class="l-stat l-error">错误 {{ logCountByLevel('ERROR') }}</span>
        </div>
        <div class="log-actions">
          <el-switch v-model="autoScroll" size="small" active-text="自动滚动" />
          <el-button size="small" :icon="Refresh" @click="fetchLiveLogs" :loading="liveLogsLoading" plain>刷新</el-button>
        </div>
      </div>

      <div class="log-filter">
        <el-input v-model="logFilter" placeholder="过滤日志..." size="small" clearable style="flex:1">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="logLevelFilter" size="small" style="width:100px" clearable placeholder="级别">
          <el-option label="全部" value="" />
          <el-option label="INFO" value="INFO" />
          <el-option label="WARN" value="WARN" />
          <el-option label="ERROR" value="ERROR" />
        </el-select>
      </div>

      <div class="log-terminal" ref="logTerminal" v-loading="liveLogsLoading">
        <div v-if="!liveLogsLoading && filteredLiveLogs.length === 0" class="log-empty">无日志输出</div>
        <div v-for="(log, i) in filteredLiveLogs" :key="i" :class="['log-line', `ll-${(log.logLevel || 'info').toLowerCase()}`]">
          <span class="ll-seq">{{ String(i + 1).padStart(4, '0') }}</span>
          <span class="ll-lvl">{{ (log.logLevel || 'INFO').padEnd(5) }}</span>
          <span class="ll-ts">{{ shortTime(log.occurTime) }}</span>
          <span v-if="log.sourceModule" class="ll-mod">[{{ log.sourceModule }}]</span>
          <span class="ll-msg">{{ log.message }}</span>
        </div>
      </div>

      <template #footer>
        <div class="drawer-footer-actions">
          <span style="font-size: 12px; color: #909399;">共 {{ filteredLiveLogs.length }} 条日志</span>
          <el-button size="small" @click="scrollToBottom"><el-icon><Bottom /></el-icon> 跳到底部</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Loading, Refresh, Clock, Timer, Search, Bottom, Odometer
} from '@element-plus/icons-vue'

const allTasks = ref([])
const initialLoading = ref(true)
const refreshIconLoading = ref(false)
const lastRefreshed = ref(null)
const monitorSummary = ref({
  todayCompletedCount: 0,
  todayFailedCount: 0
})

let refreshTimer = null
let elapsedTimer = null
const now = ref(Date.now())

const logDrawerVisible = ref(false)
const logDrawerTask = ref(null)
const liveLogs = ref([])
const liveLogsLoading = ref(false)
const autoScroll = ref(true)
const logFilter = ref('')
const logLevelFilter = ref('')
const logTerminal = ref(null)

let liveLogInterval = null
let lastLiveLogId = 0

const runningTasks = computed(() => allTasks.value.filter(t => t.currentStatus === 'RUNNING'))
const pendingTasks = computed(() => allTasks.value.filter(t => t.currentStatus === 'PENDING'))

const lastRefreshedStr = computed(() => lastRefreshed.value ? new Date(lastRefreshed.value).toLocaleTimeString('zh-CN', { hour12: false }) : '—')

const filteredLiveLogs = computed(() => {
  let logs = liveLogs.value
  if (logLevelFilter.value) logs = logs.filter(l => (l.logLevel || 'INFO').toUpperCase() === logLevelFilter.value)
  if (logFilter.value.trim()) {
    const kw = logFilter.value.toLowerCase()
    logs = logs.filter(l => l.message?.toLowerCase().includes(kw) || l.sourceModule?.toLowerCase().includes(kw))
  }
  return logs
})

const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—'
const shortTime = (t) => t ? new Date(t).toLocaleTimeString('zh-CN', { hour12: false }) : ''

const getElapsed = (startTime) => {
  if (!startTime) return '—'
  const diff = Math.max(0, now.value - new Date(startTime).getTime())
  const s = Math.floor(diff / 1000)
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ${s % 60}s`
  const h = Math.floor(m / 60)
  return `${h}h ${m % 60}m`
}

const logCountByLevel = (level) => liveLogs.value.filter(l => (l.logLevel || 'INFO').toUpperCase() === level).length

const fetchTasks = async () => {
  try {
    const res = await fetch('/api/task/monitor/summary').then(r => r.json())
    if (res?.success) {
      const data = res.data || {}
      allTasks.value = [
        ...(data.runningTasks || []),
        ...(data.pendingTasks || [])
      ]
      monitorSummary.value = {
        todayCompletedCount: data.todayCompletedCount || 0,
        todayFailedCount: data.todayFailedCount || 0
      }
    }
    lastRefreshed.value = Date.now()
  } catch (e) {
    ElMessage.error('加载任务监控数据失败')
  }
}

const fetchLiveLogs = async () => {
  if (!logDrawerTask.value) return
  liveLogsLoading.value = true
  try {
    const res = await fetch(`/api/task/logs/${logDrawerTask.value.taskId}?limit=300`).then(r => r.json())
    liveLogs.value = res?.success ? (res.data || []) : []
    lastLiveLogId = liveLogs.value.reduce((max, log) => Math.max(max, Number(log.logId || 0)), 0)
    if (autoScroll.value) { await nextTick(); scrollToBottom() }
  } catch (e) {
    ElMessage.error('加载任务日志失败')
  } finally { liveLogsLoading.value = false }
}

const fetchLiveLogUpdates = async () => {
  if (!logDrawerTask.value) return
  try {
    const res = await fetch(`/api/task/logs/${logDrawerTask.value.taskId}?afterLogId=${lastLiveLogId}&limit=200`).then(r => r.json())
    if (res?.success && Array.isArray(res.data) && res.data.length > 0) {
      liveLogs.value = [...liveLogs.value, ...res.data].slice(-500)
      lastLiveLogId = liveLogs.value.reduce((max, log) => Math.max(max, Number(log.logId || 0)), lastLiveLogId)
      if (autoScroll.value) { await nextTick(); scrollToBottom() }
    }
  } catch (e) {
    ElMessage.error('同步任务日志失败')
  }
}

const refreshAll = async () => {
  refreshIconLoading.value = true
  await fetchTasks()
  refreshIconLoading.value = false
}

const openLogDrawer = async (task) => {
  logDrawerTask.value = task
  liveLogs.value = []
  lastLiveLogId = 0
  logFilter.value = ''
  logLevelFilter.value = ''
  autoScroll.value = true
  logDrawerVisible.value = true
  await fetchLiveLogs()
  stopLiveLogs()
  liveLogInterval = setInterval(fetchLiveLogUpdates, 4000)
}

const stopLiveLogs = () => {
  if (liveLogInterval) { clearInterval(liveLogInterval); liveLogInterval = null }
}

const scrollToBottom = async () => {
  await nextTick()
  if (logTerminal.value) logTerminal.value.scrollTop = logTerminal.value.scrollHeight
}

watch(filteredLiveLogs, async () => {
  if (autoScroll.value) { await nextTick(); scrollToBottom() }
})

onMounted(async () => {
  await fetchTasks(); initialLoading.value = false
  refreshTimer = setInterval(fetchTasks, 5000)
  elapsedTimer = setInterval(() => { now.value = Date.now() }, 1000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  if (elapsedTimer) clearInterval(elapsedTimer)
  stopLiveLogs()
})
</script>

<style scoped>
.task-monitor {
  padding: 16px 20px;
  background: #f0f2f5;
  min-height: 100vh;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', sans-serif;
}

/* Header & Thin Summary Bar */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.header-left { display: flex; align-items: center; gap: 20px; }
.page-title { margin: 0; font-size: 16px; font-weight: 600; color: #303133; display: flex; align-items: center; gap: 8px; }
.live-indicator { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #67c23a; background: #f0f9eb; padding: 2px 6px; border-radius: 10px; border: 1px solid #e1f3d8; }
.live-dot { width: 6px; height: 6px; border-radius: 50%; background: #67c23a; animation: pulse 1.5s infinite; }
@keyframes pulse { 50% { opacity: 0.3; } }

.thin-summary-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  border-left: 1px solid #e4e7ed;
  padding-left: 20px;
}
.summary-badge { font-size: 13px; color: #606266; display: flex; align-items: center; gap: 4px; background: #f4f4f5; padding: 4px 10px; border-radius: 4px; border: 1px solid #e9e9eb; }
.summary-badge--running { color: #409eff; background: #ecf5ff; border-color: #d9ecff; }
.summary-badge--completed { color: #67c23a; background: #f0f9eb; border-color: #e1f3d8; }
.summary-badge--failed { color: #f56c6c; background: #fef0f0; border-color: #fde2e2; }
.s-dot { width: 6px; height: 6px; border-radius: 50%; background: #909399; }
.s-dot--running { background: #409eff; animation: pulse 1.5s infinite; }
.refresh-time { font-size: 12px; color: #909399; margin-left: 8px; }

/* Loading & Empty */
.loading-screen { display: flex; align-items: center; justify-content: center; padding: 40px; color: #909399; gap: 10px; font-size: 13px; }
.spin-icon { animation: spin 1s linear infinite; }
@keyframes spin { 100% { transform: rotate(360deg); } }
.empty-state { background: #fff; border: 1px dashed #c0c4cc; border-radius: 4px; padding: 30px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #909399; gap: 8px; }
.empty-text { font-size: 13px; }

/* Section Header */
.section-header { margin-bottom: 12px; }
.section-title { font-size: 14px; font-weight: bold; color: #303133; border-left: 3px solid #409eff; padding-left: 6px; }
.section-title--pending { border-left-color: #909399; color: #606266; }
.mt-4 { margin-top: 20px; }

/* Running Cards */
.running-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.task-card { background: #fff; border: 1px solid #e4e7ed; border-top: 3px solid #409eff; border-radius: 4px; padding: 12px; cursor: pointer; transition: all 0.2s; box-shadow: 0 2px 4px rgba(0,0,0,0.02); }
.task-card:hover { border-color: #a0cfff; box-shadow: 0 4px 8px rgba(64,158,255,0.1); transform: translateY(-1px); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.card-id { font-family: monospace; font-size: 12px; color: #909399; background: #f4f4f5; padding: 2px 6px; border-radius: 4px; }
.card-status { font-size: 12px; font-weight: bold; color: #409eff; display: flex; align-items: center; gap: 4px; }
.live-pulse-dot { width: 6px; height: 6px; background: #409eff; border-radius: 50%; animation: pulse 1.5s infinite; }
.task-name { font-size: 14px; font-weight: bold; color: #303133; margin-bottom: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-meta { display: flex; justify-content: space-between; font-size: 12px; color: #606266; margin-bottom: 12px; }
.elapsed-time { color: #409eff; font-weight: 500; }
.progress-wrap { background: #f9fafc; padding: 8px; border-radius: 4px; border: 1px solid #ebeef5; }
.progress-label { display: flex; justify-content: space-between; font-size: 12px; color: #606266; margin-bottom: 4px; }
.progress-label span { color: #409eff; font-weight: bold; }
.progress-bg { height: 4px; background: #ebeef5; border-radius: 2px; overflow: hidden; }
.progress-fill { height: 100%; background: #409eff; transition: width 0.3s; }
.progress-fill-running {
  width: 100%;
  animation: runningBar 1.6s linear infinite;
}
@keyframes runningBar {
  0% { transform: translateX(-70%); }
  100% { transform: translateX(100%); }
}

/* Pending List */
.pending-list { display: flex; flex-direction: column; gap: 8px; }
.pending-item { display: flex; justify-content: space-between; align-items: center; background: #fff; border: 1px solid #e4e7ed; padding: 10px 16px; border-radius: 4px; }
.p-left { display: flex; align-items: center; gap: 12px; }
.p-icon { font-size: 18px; color: #909399; }
.p-info { display: flex; flex-direction: column; gap: 2px; }
.p-name { font-size: 13px; font-weight: bold; color: #303133; }
.p-meta { font-size: 12px; color: #909399; }
.status-badge { font-size: 12px; color: #909399; background: #f4f4f5; padding: 2px 8px; border-radius: 10px; border: 1px solid #e9e9eb; }

/* Drawer */
.log-drawer :deep(.el-drawer__body) { padding: 0 16px 16px; display: flex; flex-direction: column; }
.drawer-header-compact { display: flex; justify-content: space-between; align-items: center; background: #fafafa; padding: 10px 16px; border: 1px solid #e4e7ed; border-radius: 4px; margin-bottom: 12px; }
.drawer-title-area { display: flex; align-items: center; gap: 12px; }
.drawer-task-name { font-size: 14px; font-weight: bold; color: #303133; }
.drawer-meta { font-size: 12px; color: #909399; }
.log-controls { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.log-stats { display: flex; gap: 8px; }
.l-stat { font-size: 12px; font-weight: bold; padding: 2px 6px; border-radius: 4px; }
.l-info { color: #409eff; background: #ecf5ff; }
.l-warn { color: #e6a23c; background: #fdf6ec; }
.l-error { color: #f56c6c; background: #fef0f0; }
.log-actions { display: flex; align-items: center; gap: 12px; }
.log-filter { display: flex; gap: 8px; margin-bottom: 8px; }
.log-terminal { flex: 1; background: #1e1e1e; border-radius: 4px; padding: 8px; overflow-y: auto; font-family: monospace; font-size: 12px; }
.log-empty { color: #909399; text-align: center; padding: 20px; }
.log-line { display: flex; gap: 8px; padding: 2px 4px; line-height: 1.4; }
.ll-info { color: #67c23a; }
.ll-warn { color: #e6a23c; }
.ll-error { color: #f56c6c; background: rgba(245,108,108,0.1); }
.ll-seq { color: #606266; }
.ll-lvl { font-weight: bold; width: 40px; }
.ll-ts { color: #909399; }
.ll-mod { color: #c678dd; }
.ll-msg { color: #dcdfe6; word-break: break-all; }
.drawer-footer-actions { display: flex; justify-content: space-between; align-items: center; padding-top: 12px; }
</style>
