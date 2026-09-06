<template>
  <div class="dashboard-master-canvas">
    <template v-if="!agentStore.isChatOpen">
      <header class="dashboard-top-bar">
        <div class="header-left-meta">
          <h1 class="page-title">首页</h1>
          <span :class="['status-pill', mqttStatusTagClass]">
            <i class="pulse-dot"></i> MQTT {{ mqttStatusLabel }}
          </span>
          <span class="clock-tag">{{ currentTime }}</span>
        </div>

        <div class="kpi-inline-group">
          <div class="kpi-unit">
            <span>设备在线</span>
            <strong class="green">{{ stats.onlineDevices || 0 }} / {{ stats.totalDevices || 0 }}</strong>
          </div>
          <div class="kpi-unit">
            <span>进行中任务</span>
            <strong class="blue">{{ stats.runningTasks || 0 }}</strong>
          </div>
        </div>

        <div class="header-actions">
          <button class="btn-aliyun-cta btn-ai-entry" type="button" @click="agentStore.openChat()">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
            </svg>
            <span v-if="agentStore.generating" class="ai-running-pulse"></span>
            <span>{{ agentStore.generating ? 'AI 正在推演中...' : 'AI 对话助手' }}</span>
          </button>
          <button class="btn-aliyun" type="button" @click="goToTaskList">任务列表</button>
          <button class="btn-aliyun" type="button" @click="goToTaskDesigner">流程设计</button>
        </div>
      </header>

      <!-- 2. MQTT 异常警告条 (仅在未连接时出现) -->
      <div v-if="showMqttAlert" class="mqtt-inline-alert">
        <i class="alert-icon">!</i>
        <span>{{ mqttAlertTitle }}</span>
      </div>

      <div class="dashboard-center-grid">
        <div class="scene-viewport-pane">
          <div class="scene-header-control">
            <div class="scene-select-group">
              <span class="sec-label">实验室</span>
              <el-select
                v-if="sceneList.length"
                v-model="activeSceneId"
                size="small"
                placeholder="选择场景"
                style="width: 160px;"
                @change="onSceneChange"
              >
                <el-option
                  v-for="scene in sceneList"
                  :key="scene.id"
                  :label="scene.sceneName"
                  :value="scene.id"
                />
              </el-select>
              <span v-else class="empty-scene-tag">暂无场景</span>
            </div>
            <button class="btn-aliyun" type="button" @click="goToDevicePage">设备中心</button>
          </div>

          <div class="scene-canvas-body">
            <img
              v-if="currentScene?.scenePicture"
              :src="currentScene.scenePicture"
              class="scene-background-image"
              alt="场景底图"
            />
            <div
              v-for="node in displayDeviceNodes"
              :key="node.instanceId"
              :class="['device-instance-node', { selected: currentDevice?.instanceId === node.instanceId }]"
              :style="{ left: node.posX + 'px', top: node.posY + 'px' }"
              @click="selectDevice(node)"
            >
              <div class="node-header">
                <span class="node-title" :title="node.instanceName">{{ node.instanceName }}</span>
                <span :class="['status-pill', node.isOnline ? 'online' : 'offline']">
                  {{ node.isOnline ? '在线' : '离线' }}
                </span>
              </div>
              <div class="node-sub-meta">{{ getModelLabel(node.modelId) }}</div>
            </div>
            <div v-if="!displayDeviceNodes.length" class="scene-empty-watermark">
              <p class="empty-main-text">暂无设备实例</p>
              <p class="empty-sub-text">可在设备中心添加后再回到首页查看</p>
            </div>
          </div>
        </div>

        <aside class="dashboard-side-pane">
          <section v-if="currentDevice" class="side-device-card">
            <div class="side-section-head">
              <span>当前设备</span>
              <span :class="['status-pill', currentDevice.isOnline ? 'online' : 'offline']">
                {{ currentDevice.isOnline ? '在线' : '离线' }}
              </span>
            </div>
            <table class="meta-table-dense">
              <tbody>
                <tr>
                  <td class="lbl">名称</td>
                  <td class="val"><strong>{{ currentDevice.instanceName }}</strong></td>
                </tr>
                <tr>
                  <td class="lbl">模型</td>
                  <td class="val">{{ getModelLabel(currentDevice.modelId) }}</td>
                </tr>
                <tr>
                  <td class="lbl">指令</td>
                  <td class="val mono">{{ liveSnapshot?.currentCommandState || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section class="side-task-list">
            <div class="side-section-head">
              <span>进行中任务</span>
              <button class="btn-link" type="button" @click="goToTaskList">全部</button>
            </div>
            <div v-if="runningTasks.length" class="side-task-rows">
              <button
                v-for="task in runningTasks.slice(0, 4)"
                :key="task.taskId"
                class="side-task-row"
                type="button"
                @click="goToTaskList"
              >
                <strong>{{ task.taskName }}</strong>
                <span class="mono">#{{ task.taskId }}</span>
              </button>
            </div>
            <p v-else class="side-empty">暂无进行中的任务</p>
          </section>
        </aside>
      </div>
    </template>

    <WorkflowAgentChat
      v-else
      @close="agentStore.closeChat()"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import WorkflowAgentChat from './components/WorkflowAgentChat.vue'
import { useAgentConversationStore } from '../../stores/agentConversationStore'

const router = useRouter()
const agentStore = useAgentConversationStore()

const currentTime = ref('')
let timer: number | undefined
let mqttTimer: number | undefined

// 真实场景主表列表 (SCENE_MAIN)
const sceneList = ref<any[]>([])
const activeSceneId = ref<number | null>(null)
const currentScene = computed(() => sceneList.value.find(s => s.id === activeSceneId.value) || null)

// 真实场景详细表 (SCENE_DETAIL) 与资源结构表 (RESOURCE_STRUCTURE)
const sceneDetailList = ref<any[]>([])
const resourceStructureList = ref<any[]>([])

// 统计与设备数据
const stats = ref<any>({})
const devices = ref<any[]>([])
const runningTasks = ref<any[]>([])
const modelMap = ref<Record<string, any>>({})
const mqttStatus = ref<any>(null)

const currentDevice = ref<any>(null)
const liveSnapshot = ref<any>(null)

const mqttStatusLabel = computed(() => {
  if (mqttStatus.value == null) return '查询中'
  if (mqttStatus.value.enabled === false) return '未启用'
  if (mqttStatus.value.connected) return '已连接'
  const status = mqttStatus.value.status || 'UNKNOWN'
  if (status === 'CONNECTING') return '连接中'
  if (status === 'NOT_STARTED') return '未启动'
  return '未连接'
})

const mqttStatusTagClass = computed(() => {
  if (mqttStatus.value == null) return 'pending'
  if (mqttStatus.value.enabled === false) return 'offline'
  return mqttStatus.value.connected ? 'online' : 'busy'
})

const showMqttAlert = computed(() => mqttStatus.value != null
  && mqttStatus.value.enabled !== false
  && mqttStatus.value.connected === false)

const mqttAlertTitle = computed(() => {
  const broker = mqttStatus.value?.broker || '未配置 broker'
  const topic = mqttStatus.value?.registerTopic || 'smartlab/adapter/register'
  const reason = mqttStatus.value?.lastError ? '，原因：' + mqttStatus.value.lastError : ''
  return 'MQTT 未连接：' + broker + '。正在后台重试；注册话题：' + topic + reason
})

const updateTime = () => {
  currentTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

const getModelLabel = (modelId: string | number) => {
  if (!modelId) return '-'
  const model = modelMap.value[String(modelId)]
  return model ? model.modelName : `模型 #${modelId}`
}

/**
 * 真实设备节点在场景中的坐标映射
 * 优先取 SCENE_DETAIL 中的 posX, posY；若无则自动在画布中流式排布
 */
const displayDeviceNodes = computed(() => {
  if (!devices.value.length) return []

  const detailMap = new Map<number, any>()
  sceneDetailList.value.forEach(detail => {
    if (detail.deviceInstanceId != null) {
      detailMap.set(Number(detail.deviceInstanceId), detail)
    }
  })

  return devices.value.map((device, idx) => {
    const isOnline = device.isOnline || device.onlineStatus === 'ONLINE'
    const detail = detailMap.get(Number(device.instanceId))

    const col = idx % 2
    const row = Math.floor(idx / 2)
    const defaultX = 12 + col * 176
    const defaultY = 12 + row * 76

    const posX = detail?.posX != null ? detail.posX : defaultX
    const posY = detail?.posY != null ? detail.posY : defaultY

    return {
      ...device,
      posX,
      posY,
      posZ: detail?.posZ,
      hasCustomPos: detail?.posX != null,
      location: device.commConfig?.location || '',
      isOnline
    }
  })
})

const selectDevice = async (device: any) => {
  currentDevice.value = JSON.parse(JSON.stringify(device))
  liveSnapshot.value = null
  try {
    const res = await axios.get(`/api/device/instance/snapshot/${device.instanceId}`)
    if (res.data?.success) {
      liveSnapshot.value = res.data.data
    }
  } catch {}
}

/**
 * 路由跳转 (严格对齐 router/index.js 真实注册路径)
 */
const goToDevicePage = () => {
  router.push('/device-instance-management')
}

const goToTaskList = () => {
  router.push('/task-management')
}

const goToTaskDesigner = () => {
  router.push('/task-designer')
}

const fetchScenes = async () => {
  try {
    const res = await axios.get('/api/scene/list')
    if (res.data?.success) {
      sceneList.value = res.data.data || []
      if (sceneList.value.length && activeSceneId.value == null) {
        activeSceneId.value = sceneList.value[0].id
        await loadSceneDetails(activeSceneId.value)
      }
    }
  } catch {
    sceneList.value = []
  }
}

const onSceneChange = async (sceneId: number) => {
  await loadSceneDetails(sceneId)
}

const loadSceneDetails = async (sceneId: number | null) => {
  if (!sceneId) {
    sceneDetailList.value = []
    resourceStructureList.value = []
    return
  }
  try {
    const [detailRes, structRes] = await Promise.all([
      axios.get('/api/scene/detail/list', { params: { sceneId } }),
      axios.get('/api/resource/structure/list', { params: { sceneId } })
    ])
    sceneDetailList.value = detailRes.data?.data || []
    resourceStructureList.value = structRes.data?.data || []
  } catch {
    sceneDetailList.value = []
    resourceStructureList.value = []
  }
}

const fetchMqttStatus = async () => {
  try {
    const res = await axios.get('/api/adapter/protocol/mqtt/status')
    if (res.data?.success) {
      mqttStatus.value = res.data.data || { status: 'UNKNOWN', connected: false }
    }
  } catch (err: any) {
    mqttStatus.value = {
      enabled: true,
      status: 'UNKNOWN',
      connected: false,
      lastError: err?.response?.data?.message || '无法获取 MQTT 状态'
    }
  }
}

const fetchData = async () => {
  try {
    const [deviceRes, modelRes, taskSummaryRes, runningTaskRes] = await Promise.all([
      axios.get('/api/device/instance/list', { params: { lifecycleStatus: 'IN_USE' } }),
      axios.get('/api/device/model/list'),
      axios.get('/api/task/summary'),
      axios.get('/api/task/page', { params: { pageNo: 1, pageSize: 10, status: 'RUNNING' } }),
    ])
    const deviceList = deviceRes.data?.data || []
    const modelList = modelRes.data?.data || []
    const taskSummary = taskSummaryRes.data?.data || {}
    const runningTaskPage = runningTaskRes.data?.data || {}

    const onlineDevices = deviceList.filter((device: any) => device.isOnline || device.onlineStatus === 'ONLINE').length
    stats.value = {
      totalDevices: deviceList.length,
      onlineDevices: onlineDevices,
      runningTasks: taskSummary.running || runningTaskPage.records?.length || 0,
    }
    devices.value = deviceList
    runningTasks.value = runningTaskPage.records || []

    const map: Record<string, any> = {}
    modelList.forEach((m: any) => (map[String(m.modelId)] = m))
    modelMap.value = map

    if (displayDeviceNodes.value.length && !currentDevice.value) {
      selectDevice(displayDeviceNodes.value[0])
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载首页数据失败')
  }
}

onMounted(() => {
  updateTime()
  timer = window.setInterval(updateTime, 1000)
  fetchScenes()
  fetchData()
  fetchMqttStatus()
  mqttTimer = window.setInterval(fetchMqttStatus, 10000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  if (mqttTimer) window.clearInterval(mqttTimer)
})
</script>

<style scoped>
/* ==========================================================================
   首页一体化无界画卷 - 严禁任何模拟数据 · 严格遵循系统 UI 设计规范四级按钮体系
   ========================================================================== */
.dashboard-master-canvas {
  width: 100%;
  height: calc(100vh - 54px);
  background: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "微软雅黑", sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 顶部指标横幅 (44px) */
.dashboard-top-bar {
  height: 44px;
  padding: 0 16px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  flex-shrink: 0;
  gap: 12px;
}

.header-left-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.clock-tag {
  font-size: 12px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.kpi-inline-group {
  display: flex;
  align-items: center;
  gap: 20px;
}

.kpi-unit {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
}

.kpi-unit strong {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.kpi-unit strong.green { color: #16a34a; }
.kpi-unit strong.blue { color: #2563eb; }

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.mqtt-inline-alert {
  padding: 6px 16px;
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
  color: #b45309;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.alert-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  background: #d97706;
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
}

/* 状态药丸 */
.status-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.status-pill.online { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }
.status-pill.busy { background: #fffbeb; color: #d97706; border: 1px solid #fde68a; }
.status-pill.offline { background: #f1f5f9; color: #64748b; border: 1px solid #cbd5e1; }
.status-pill.pending { background: #f8fafc; color: #64748b; border: 1px solid #e2e8f0; }

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.ai-running-pulse {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.7);
  animation: pulse-ring 1.2s infinite;
}

@keyframes pulse-ring {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.7);
  }
  70% {
    transform: scale(1);
    box-shadow: 0 0 0 6px rgba(37, 99, 235, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(37, 99, 235, 0);
  }
}

/* ==========================================================================
   全系统统一四级按钮标准 (Strictly follows 系统ui设计规范.md 四)
   ========================================================================== */
/* 1. 实体蓝主按钮 (.btn-primary-blue) */
.btn-primary-blue {
  height: 26px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #ffffff;
  background: #2563eb;
  border: 1px solid #2563eb;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-primary-blue:hover {
  background: #1d4ed8;
  border-color: #1d4ed8;
}
.btn-primary-blue:active {
  background: #1e40af;
  border-color: #1e40af;
}

/* 2. 引导/新建 CTA 按钮 (.btn-aliyun-cta) */
.btn-aliyun-cta {
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-aliyun-cta:hover {
  background: #dbeafe;
  border-color: #93c5fd;
  color: #1d4ed8;
}
.btn-aliyun-cta:active {
  background: #bfdbfe;
  color: #1e40af;
}

/* 3. 次要工具按钮 (.btn-aliyun) */
.btn-aliyun {
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: #334155;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-aliyun:hover {
  border-color: #94a3b8;
  background: #f8fafc;
  color: #0f172a;
}
.btn-aliyun:active {
  background: #f1f5f9;
}

/* 4. 行内操作链接 (.btn-link) */
.btn-link {
  font-size: 12px;
  font-weight: 500;
  color: #2563eb;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  transition: color 0.15s;
}
.btn-link:hover {
  color: #1d4ed8;
  text-decoration: underline;
}

.dashboard-center-grid {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  min-height: 0;
  background: #ffffff;
}

.dashboard-side-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-left: 1px solid #e2e8f0;
  background: #ffffff;
}

.scene-viewport-pane {
  position: relative;
  background: #ffffff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 180px;
}

.scene-header-control {
  height: 38px;
  padding: 0 12px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fafbfc;
  flex-shrink: 0;
  gap: 8px;
}

.scene-select-group {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.sec-label {
  font-size: 12.5px;
  font-weight: 700;
  color: #0f172a;
}

.empty-scene-tag {
  font-size: 11.5px;
  color: #64748b;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 3px;
  border: 1px solid #cbd5e1;
}

.scene-canvas-body {
  position: relative;
  flex: 1;
  width: 100%;
  min-height: 0;
  background: #f8fafc;
  background-image:
    linear-gradient(rgba(203, 213, 225, 0.35) 1px, transparent 1px),
    linear-gradient(90deg, rgba(203, 213, 225, 0.35) 1px, transparent 1px);
  background-size: 24px 24px;
  overflow: auto;
}

.scene-background-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
  opacity: 0.9;
}

.device-instance-node {
  position: absolute;
  width: 164px;
  min-height: 58px;
  background: #ffffff;
  border: 1px solid #2563eb;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 6px 8px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  z-index: 6;
  user-select: none;
  box-sizing: border-box;
}
.device-instance-node:hover {
  border-color: #1d4ed8;
}
.device-instance-node.selected {
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.25);
}

.node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.node-title {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-sub-meta {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.scene-empty-watermark {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #94a3b8;
  pointer-events: none;
  max-width: 240px;
}

.empty-main-text {
  font-size: 13px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 4px;
}

.empty-sub-text {
  font-size: 12px;
  line-height: 1.5;
  color: #94a3b8;
}

.side-device-card,
.side-task-list {
  flex-shrink: 0;
  border-top: 1px solid #e2e8f0;
  padding: 8px 12px 10px;
  background: #ffffff;
}

.side-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 6px;
}

.meta-table-dense {
  width: 100%;
  font-size: 12px;
  border-collapse: collapse;
}
.meta-table-dense td {
  padding: 3px 0;
  border-bottom: 1px solid #f1f5f9;
}
.meta-table-dense td.lbl { color: #64748b; width: 48px; }
.meta-table-dense td.val { color: #0f172a; font-weight: 500; }

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.side-task-rows {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 132px;
  overflow-y: auto;
}

.side-task-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  text-align: left;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 6px 8px;
  cursor: pointer;
}

.side-task-row strong {
  font-size: 12px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-task-row .mono {
  font-size: 11px;
  color: #64748b;
}

.side-empty {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
