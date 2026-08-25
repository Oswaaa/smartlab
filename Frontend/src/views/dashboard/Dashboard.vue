<template>
  <div class="dashboard-master-canvas">
    <!-- 1. 顶部指标横幅 (单行 44px，无卡片孤岛，1px 底边框) -->
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
        <div class="kpi-unit">
          <span>任务总数</span>
          <strong>{{ stats.totalTasks || 0 }}</strong>
        </div>
        <div class="kpi-unit">
          <span>数据模板</span>
          <strong>{{ stats.totalTemplates || 0 }}</strong>
        </div>
        <div class="kpi-unit">
          <span>数据记录</span>
          <strong>{{ stats.totalDataRecords || 0 }}</strong>
        </div>
      </div>
    </header>

    <!-- 2. MQTT 异常警告条 (仅在未连接时出现) -->
    <div v-if="showMqttAlert" class="mqtt-inline-alert">
      <i class="alert-icon">!</i>
      <span>{{ mqttAlertTitle }}</span>
    </div>

    <!-- 3. 中央主体：大区域实验室场景画布 + 右侧设备实例检视器 -->
    <div class="dashboard-center-grid">
      <!-- 左侧：留出的实验室场景大区域 (SCENE_MAIN / SCENE_DETAIL / RESOURCE_STRUCTURE) -->
      <div class="scene-viewport-pane">
        <!-- 场景顶部控制与切换条 -->
        <div class="scene-header-control">
          <div class="scene-select-group">
            <span class="sec-label">实验室场景:</span>
            <el-select
              v-if="sceneList.length"
              v-model="activeSceneId"
              size="small"
              placeholder="请选择场景"
              style="width: 240px;"
              @change="onSceneChange"
            >
              <el-option
                v-for="scene in sceneList"
                :key="scene.id"
                :label="scene.sceneName"
                :value="scene.id"
              />
            </el-select>
            <span v-else class="empty-scene-tag">暂无已创建场景 (待配置 SCENE_MAIN)</span>
          </div>

          <div class="scene-actions">
            <button class="btn-aliyun" type="button" @click="fetchScenes">
              刷新场景
            </button>
          </div>
        </div>

        <!-- 场景画布渲染区 (纯净工程底图网格，无自行拟造的区域/台面) -->
        <div class="scene-canvas-body">
          <!-- 若有场景底图 (SCENE_PICTURE) 则渲染底图 -->
          <img
            v-if="currentScene?.scenePicture"
            :src="currentScene.scenePicture"
            class="scene-background-image"
            alt="场景底图"
          />

          <!-- 真实设备实例节点 (若有 SCENE_DETAIL 坐标则按坐标定位，否则在视口中整齐陈列) -->
          <div
            v-for="node in displayDeviceNodes"
            :key="node.instanceId"
            :class="[
              'device-instance-node',
              { selected: currentDevice?.instanceId === node.instanceId }
            ]"
            :style="{ left: node.posX + 'px', top: node.posY + 'px' }"
            @click="selectDevice(node)"
          >
            <div class="node-header">
              <span class="node-title" :title="node.instanceName">{{ node.instanceName }}</span>
              <span :class="['status-pill', node.isOnline ? 'online' : 'offline']">
                {{ node.isOnline ? '在线' : '离线' }}
              </span>
            </div>
            <div class="node-sub-meta">
              物模型: {{ getModelLabel(node.modelId) }}
            </div>
            <div class="node-bottom-row">
              <span>编号: #{{ node.instanceId }}</span>
              <span v-if="node.location">位置: {{ node.location }}</span>
            </div>
          </div>

          <!-- 场景完全为空时的真实引导状态 -->
          <div v-if="!displayDeviceNodes.length" class="scene-empty-watermark">
            <p class="empty-main-text">实验室场景视口</p>
            <p class="empty-sub-text">用于呈现 SCENE_MAIN 场景底图、SCENE_DETAIL 设备空间坐标与 RESOURCE_STRUCTURE 资源拓扑</p>
            <p v-if="!devices.length" class="empty-action-text">当前系统暂无设备实例，请前往「设备中心」添加</p>
          </div>
        </div>

        <!-- 场景视口底栏数据库表联动状态 -->
        <footer class="scene-footer-bar">
          <div class="footer-db-status">
            <span>场景主表 (SCENE_MAIN): <b>{{ currentScene ? `${currentScene.sceneName} (#${currentScene.id})` : '未绑定' }}</b></span>
            <span class="status-sep">|</span>
            <span>场景详细表 (SCENE_DETAIL): <b>{{ sceneDetailList.length }} 项坐标配置</b></span>
            <span class="status-sep">|</span>
            <span>资源结构表 (RESOURCE_STRUCTURE): <b>{{ resourceStructureList.length }} 条拓扑连接</b></span>
          </div>
          <div class="footer-view-mode">
            <span>空间视口状态：已就绪</span>
          </div>
        </footer>
      </div>

      <!-- 右侧：真实设备实例实时检视器 (无任何虚构文本) -->
      <aside class="dashboard-inspector-pane">
        <div class="inspector-header-bar">
          <span>设备实例实时监视</span>
          <button class="btn-aliyun" type="button" @click="goToDevicePage">
            设备中心
          </button>
        </div>

        <div v-if="currentDevice" class="inspector-scroll-body">
          <!-- 基础信息 -->
          <div class="inspector-section">
            <div class="section-title-lead">实例基本信息</div>
            <table class="meta-table-dense">
              <tbody>
                <tr>
                  <td class="lbl">实例名称</td>
                  <td class="val"><strong>{{ currentDevice.instanceName }}</strong></td>
                </tr>
                <tr>
                  <td class="lbl">实例编号</td>
                  <td class="val mono">#{{ currentDevice.instanceId }}</td>
                </tr>
                <tr>
                  <td class="lbl">设备模型</td>
                  <td class="val">{{ getModelLabel(currentDevice.modelId) }}</td>
                </tr>
                <tr>
                  <td class="lbl">安装位置</td>
                  <td class="val">{{ currentDevice.commConfig?.location || '-' }}</td>
                </tr>
                <tr>
                  <td class="lbl">通信主题</td>
                  <td class="val mono">{{ currentDevice.commConfig?.mqttTopic || '-' }}</td>
                </tr>
                <tr>
                  <td class="lbl">在线状态</td>
                  <td class="val">
                    <span :class="['status-pill', currentDevice.isOnline ? 'online' : 'offline']">
                      {{ currentDevice.isOnline ? 'ONLINE' : 'OFFLINE' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 实时测点与运行状态 (真实后端快照) -->
          <div class="inspector-section">
            <div class="section-title-lead">实时运行快照</div>
            <table class="meta-table-dense">
              <tbody>
                <tr>
                  <td class="lbl">指令状态</td>
                  <td class="val mono">{{ liveSnapshot?.currentCommandState || '-' }}</td>
                </tr>
                <tr>
                  <td class="lbl">功能状态</td>
                  <td class="val">{{ formatOperationState(liveSnapshot?.currentOperationState) }}</td>
                </tr>
              </tbody>
            </table>

            <!-- 动态真实属性列表 -->
            <div v-if="liveSnapshot?.latestAttributes && Object.keys(liveSnapshot.latestAttributes).length" class="attributes-grid">
              <div
                v-for="(val, key) in liveSnapshot.latestAttributes"
                :key="key"
                class="attr-pill-box"
              >
                <span class="attr-k">{{ key }}</span>
                <span class="attr-v">{{ val }}</span>
              </div>
            </div>
            <div v-else class="empty-hint-text">
              等待遥测上报属性快照...
            </div>
          </div>

          <!-- 场景与拓扑归属 (真实数据库关联) -->
          <div class="inspector-section">
            <div class="section-title-lead">场景拓扑关联</div>
            <table class="meta-table-dense">
              <tbody>
                <tr>
                  <td class="lbl">所属场景</td>
                  <td class="val">{{ currentScene?.sceneName || '未分配场景' }}</td>
                </tr>
                <tr>
                  <td class="lbl">空间坐标 (X,Y,Z)</td>
                  <td class="val mono">{{ currentDevicePos || '未设置坐标' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-else class="inspector-empty-state">
          <p>请在场景画布中选择设备实例查看实时监测</p>
        </div>
      </aside>
    </div>

    <!-- 4. 底部任务流栏 (通栏一体化呈现，已去除全局约束配置，150px 固定) -->
    <footer class="dashboard-bottom-dock">
      <div class="bottom-task-feed">
        <div class="feed-head-bar">
          <div class="feed-title-left">
            <span class="feed-main-title">进行中的任务 ({{ runningTasks.length }})</span>
            <span class="feed-sub-meta">当前活跃工作流实例实时运行队列</span>
          </div>
          <div class="feed-actions-right">
            <button class="btn-aliyun-cta" type="button" @click="goToTaskDesigner">
              + 流程设计
            </button>
            <button class="btn-aliyun" type="button" @click="goToTaskList">
              全部任务
            </button>
          </div>
        </div>
        <div class="task-table-wrapper">
          <table class="mini-task-table">
            <thead>
              <tr>
                <th style="width: 110px;">任务编号</th>
                <th style="min-width: 180px;">任务名称</th>
                <th style="min-width: 150px;">流程模型</th>
                <th style="width: 180px;">开始时间</th>
                <th style="width: 110px;">状态</th>
                <th style="width: 90px; text-align: center;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in runningTasks" :key="task.taskId">
                <td class="mono">#{{ task.taskId }}</td>
                <td><strong class="task-name-link" @click="goToTaskList">{{ task.taskName }}</strong></td>
                <td>流程模型 #{{ task.flowModelId }}</td>
                <td class="mono">{{ formatTime(task.startTime) }}</td>
                <td><span class="status-pill busy">RUNNING</span></td>
                <td style="text-align: center;">
                  <button class="btn-link" type="button" @click="goToTaskList">
                    查看详情
                  </button>
                </td>
              </tr>
              <tr v-if="!runningTasks.length">
                <td colspan="6" class="empty-table-cell">暂无进行中的任务，可前往「任务列表」启动或创建任务</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()

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
const mqttStatus = ref<any>({ enabled: true, status: 'NOT_STARTED', connected: false })

const currentDevice = ref<any>(null)
const liveSnapshot = ref<any>(null)

const formatOperationState = (value: unknown) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return '-'
  const regions = Object.entries(value as Record<string, unknown>)
    .map(([regionName, states]) => {
      if (!Array.isArray(states) || states.length === 0) return ''
      return `${regionName}: ${states.join(', ')}`
    })
    .filter(Boolean)
  return regions.length ? regions.join(' | ') : '-'
}

const mqttStatusLabel = computed(() => {
  if (mqttStatus.value?.enabled === false) return '未启用'
  if (mqttStatus.value?.connected) return '已连接'
  const status = mqttStatus.value?.status || 'UNKNOWN'
  if (status === 'CONNECTING') return '连接中'
  if (status === 'NOT_STARTED') return '未启动'
  return '未连接'
})

const mqttStatusTagClass = computed(() => {
  if (mqttStatus.value?.enabled === false) return 'offline'
  return mqttStatus.value?.connected ? 'online' : 'busy'
})

const showMqttAlert = computed(() => mqttStatus.value?.enabled !== false && mqttStatus.value?.connected === false)

const mqttAlertTitle = computed(() => {
  const broker = mqttStatus.value?.broker || '未配置 broker'
  const topic = mqttStatus.value?.registerTopic || 'smartlab/adapter/register'
  const reason = mqttStatus.value?.lastError ? '，原因：' + mqttStatus.value.lastError : ''
  return 'MQTT 未连接：' + broker + '。正在后台重试；注册话题：' + topic + reason
})

const updateTime = () => {
  currentTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

const formatTime = (v?: string) => (v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '-')

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

    // 若数据库中有 POS_X, POS_Y 则使用真实坐标，否则按行列网格排布 (每行 3 个，宽 240px，高 110px)
    const col = idx % 3
    const row = Math.floor(idx / 3)
    const defaultX = 30 + col * 270
    const defaultY = 30 + row * 140

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

const currentDevicePos = computed(() => {
  if (!currentDevice.value) return ''
  const node = displayDeviceNodes.value.find(n => n.instanceId === currentDevice.value.instanceId)
  if (!node) return ''
  if (node.hasCustomPos) {
    return `X: ${node.posX}, Y: ${node.posY}, Z: ${node.posZ || 0}`
  }
  return '未在 SCENE_DETAIL 中配置三维坐标'
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
    const [deviceRes, modelRes, taskSummaryRes, runningTaskRes, templateRes, dataIndexRes] = await Promise.all([
      axios.get('/api/device/instance/list', { params: { lifecycleStatus: 'IN_USE' } }),
      axios.get('/api/device/model/list'),
      axios.get('/api/task/summary'),
      axios.get('/api/task/page', { params: { pageNo: 1, pageSize: 10, status: 'RUNNING' } }),
      axios.get('/api/data/template/list'),
      axios.get('/api/data/index/list')
    ])
    const deviceList = deviceRes.data?.data || []
    const modelList = modelRes.data?.data || []
    const taskSummary = taskSummaryRes.data?.data || {}
    const runningTaskPage = runningTaskRes.data?.data || {}
    const templateList = templateRes.data?.data || []
    const dataIndexList = dataIndexRes.data?.data || []

    const onlineDevices = deviceList.filter((device: any) => device.isOnline || device.onlineStatus === 'ONLINE').length
    stats.value = {
      totalDevices: deviceList.length,
      onlineDevices: onlineDevices,
      offlineDevices: Math.max(0, deviceList.length - onlineDevices),
      totalTasks: taskSummary.total || 0,
      runningTasks: taskSummary.running || runningTaskPage.records?.length || 0,
      failedTasks: taskSummary.failed || 0,
      totalTemplates: templateList.length,
      totalDataRecords: dataIndexList.length
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

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
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

/* 中央网格 (1fr : 330px) */
.dashboard-center-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 330px;
  min-height: 0;
  background: #ffffff;
}

/* 场景视口大区域 (SCENE_MAIN / SCENE_DETAIL / RESOURCE_STRUCTURE) */
.scene-viewport-pane {
  position: relative;
  background: #ffffff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-right: 1px solid #e2e8f0;
}

.scene-header-control {
  height: 38px;
  padding: 0 14px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fafbfc;
  flex-shrink: 0;
}

.scene-select-group {
  display: flex;
  align-items: center;
  gap: 8px;
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

/* 场景画布工作区 (纯净工程网格) */
.scene-canvas-body {
  position: relative;
  flex: 1;
  width: 100%;
  height: 100%;
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

/* 真实设备实例节点 (大尺寸、字迹清晰、无任何拟造分区) */
.device-instance-node {
  position: absolute;
  width: 240px;
  min-height: 110px;
  background: #ffffff;
  border: 1.5px solid #2563eb;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 8px 10px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  transition: all 0.15s ease;
  z-index: 6;
  user-select: none;
  box-sizing: border-box;
}
.device-instance-node:hover {
  border-color: #1d4ed8;
  transform: translateY(-2px);
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.2);
}
.device-instance-node.selected {
  border-color: #2563eb;
  border-width: 2px;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.25), 0 6px 16px rgba(0, 0, 0, 0.14);
}

.node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.node-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-sub-meta {
  font-size: 11.5px;
  color: #475569;
  margin-top: 4px;
}

.node-bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
  margin-top: 6px;
  padding-top: 4px;
  border-top: 1px solid #f1f5f9;
}

.scene-empty-watermark {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #94a3b8;
  pointer-events: none;
  max-width: 480px;
}

.empty-main-text {
  font-size: 14px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 6px;
}

.empty-sub-text {
  font-size: 12px;
  line-height: 1.5;
  color: #94a3b8;
}

.empty-action-text {
  font-size: 11.5px;
  color: #2563eb;
  margin-top: 6px;
}

.scene-footer-bar {
  height: 28px;
  padding: 0 14px;
  background: #ffffff;
  border-top: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
  z-index: 10;
  flex-shrink: 0;
}

.footer-db-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-db-status b {
  color: #0f172a;
}

.status-sep {
  color: #cbd5e1;
}

/* 右侧设备属性检视器 */
.dashboard-inspector-pane {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
}

.inspector-header-bar {
  height: 38px;
  padding: 0 14px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  font-weight: 700;
  color: #0f172a;
  background: #fafbfc;
  flex-shrink: 0;
}

.inspector-scroll-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inspector-section {
  display: flex;
  flex-direction: column;
}

.section-title-lead {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.section-title-lead::before {
  content: "";
  width: 3px;
  height: 11px;
  background: #2563eb;
  border-radius: 2px;
}

.meta-table-dense {
  width: 100%;
  font-size: 12px;
  border-collapse: collapse;
}
.meta-table-dense td {
  padding: 4px 0;
  border-bottom: 1px solid #f1f5f9;
}
.meta-table-dense td.lbl { color: #64748b; width: 95px; }
.meta-table-dense td.val { color: #0f172a; font-weight: 500; }

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.attributes-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-top: 6px;
}

.attr-pill-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 4px 8px;
  display: flex;
  flex-direction: column;
}
.attr-k { font-size: 11px; color: #64748b; }
.attr-v { font-size: 12.5px; font-weight: 700; color: #0f172a; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }

.inspector-empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

.empty-hint-text {
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 4px;
}

/* 底部任务流栏 (通栏一体化呈现，150px 固定) */
.dashboard-bottom-dock {
  height: 150px;
  border-top: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  flex-shrink: 0;
}

.bottom-task-feed {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.feed-head-bar {
  height: 32px;
  padding: 0 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.feed-title-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.feed-main-title {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.feed-sub-meta {
  font-size: 11px;
  color: #64748b;
}

.feed-actions-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-table-wrapper {
  flex: 1;
  overflow-y: auto;
}

.mini-task-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  text-align: left;
}
.mini-task-table th {
  background: #f8fafc;
  color: #64748b;
  padding: 6px 14px;
  border-bottom: 1px solid #e2e8f0;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 2;
}
.mini-task-table td {
  padding: 6px 14px;
  border-bottom: 1px solid #f1f5f9;
  color: #334155;
}

.task-name-link {
  color: #0f172a;
  cursor: pointer;
  transition: color 0.15s;
}
.task-name-link:hover {
  color: #2563eb;
}

.empty-table-cell {
  text-align: center;
  color: #94a3b8;
  padding: 24px !important;
  font-size: 12px;
}
</style>
