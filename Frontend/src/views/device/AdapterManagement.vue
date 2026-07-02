<template>
  <div class="adapter-management-page">
    <header class="page-toolbar">
      <div class="toolbar-title">
        <h1>设备执行代理</h1>
        <span>Adapter 注册、连接状态、设备点与实例绑定</span>
      </div>
      <div class="toolbar-actions">
        <el-input v-model="keyword" placeholder="搜索 Adapter" clearable :prefix-icon="Search" />
        <el-button :icon="Connection" :loading="mqttLoading" @click="reconnectMqtt">重连 MQTT</el-button>
        <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openRegisterDrawer">注册 Adapter</el-button>
      </div>
    </header>

    <main class="adapter-workspace">
      <aside class="adapter-sidebar">
        <section class="mqtt-strip">
          <span class="strip-label">MQTT Broker</span>
          <strong>{{ mqttStatusLabel }}</strong>
          <el-tag :type="mqttConnected ? 'success' : 'danger'" effect="plain" size="small">{{ mqttConnected ? '已连接' : '未连接' }}</el-tag>
        </section>

        <div class="list-title">
          <strong>Adapter 列表</strong>
          <em>{{ filteredAdapters.length }} 个</em>
        </div>
        <div class="adapter-list" v-loading="loading">
          <el-empty v-if="filteredAdapters.length === 0" description="暂无 Adapter" :image-size="88" />
          <button
            v-for="item in filteredAdapters"
            :key="adapterIdOf(item)"
            type="button"
            class="adapter-list-item"
            :class="{ active: activeKey === adapterIdOf(item) }"
            @click="activeKey = adapterIdOf(item)"
          >
            <span class="adapter-name">{{ item.adapterName || '未命名 Adapter' }}</span>
            <span class="adapter-subline">{{ templateCount(item) }} 模板 / {{ pointCount(item) }} 点位</span>
            <span class="adapter-status" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span>
          </button>
        </div>
      </aside>

      <section v-if="activeAdapter" class="adapter-detail">
        <div class="detail-header">
          <div>
            <span>Adapter</span>
            <h2>{{ activeAdapter.adapterName }}</h2>
          </div>
          <div class="detail-actions">
            <el-button type="danger" plain :icon="Delete" @click="deleteAdapter(activeAdapter)">删除</el-button>
          </div>
        </div>

        <section class="runtime-table">
          <div><span>运行状态</span><strong>{{ statusLabel(activeAdapter.status) }}</strong></div>
          <div><span>最后心跳</span><strong>{{ formatTime(activeAdapter.lastHeartbeat) }}</strong></div>
          <div><span>设备模板</span><strong>{{ activeTemplates.length }}</strong></div>
          <div><span>设备点位</span><strong>{{ activePoints.length }}</strong></div>
          <div><span>绑定实例</span><strong>{{ boundInstances.length }}</strong></div>
        </section>

        <el-tabs v-model="activeTab" class="adapter-tabs">
          <el-tab-pane label="模板与点位" name="runtime">
            <section class="content-block">
              <div class="block-head"><h3>设备模板 / 设备点</h3><em>{{ activeTemplates.length }} 模板 / {{ activePoints.length }} 点位</em></div>
              <el-table
                :data="templatePointTree"
                row-key="id"
                border
                size="small"
                default-expand-all
                class="industrial-table relation-table"
                :tree-props="{ children: 'children' }"
              >
                <el-table-column label="对象" min-width="220">
                  <template #default="{ row }">
                    <div class="relation-name">
                      <el-tag size="small" :type="row.nodeType === 'template' ? 'primary' : 'success'" effect="plain">
                        {{ row.nodeType === 'template' ? '模板' : '点位' }}
                      </el-tag>
                      <strong>{{ row.label }}</strong>
                      <span v-if="row.nodeType === 'point'">{{ row.devicePoint }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="模板标识" min-width="140">
                  <template #default="{ row }">{{ row.templateName || '-' }}</template>
                </el-table-column>
                <el-table-column label="模板能力" min-width="220">
                  <template #default="{ row }">
                    <span v-if="row.nodeType === 'template'">{{ row.summary }}</span>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="点位编号" width="100">
                  <template #default="{ row }">{{ row.nodeType === 'point' ? (row.index ?? '-') : '-' }}</template>
                </el-table-column>
                <el-table-column label="属性点映射" min-width="260">
                  <template #default="{ row }">
                    <div v-if="row.nodeType === 'point'" class="mapping-chips">
                      <span v-for="(point, attr) in row.attributeMapping || {}" :key="attr"><b>{{ attr }}</b><i>→</i>{{ point }}</span>
                      <em v-if="!Object.keys(row.attributeMapping || {}).length">-</em>
                    </div>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="命令与事件" name="contract">
            <section class="content-block">
              <div class="block-head"><h3>Adapter 命令</h3><em>{{ activeCommands.length }} 条</em></div>
              <el-table :data="activeCommands" border stripe size="small" class="industrial-table">
                <el-table-column label="命令名" min-width="170">
                  <template #default="{ row }"><strong>{{ row.name || row.commandName }}</strong></template>
                </el-table-column>
                <el-table-column label="说明" min-width="180">
                  <template #default="{ row }">{{ row.description || '-' }}</template>
                </el-table-column>
                <el-table-column label="命令参数" min-width="360">
                  <template #default="{ row }">
                    <div class="param-table-list">
                      <span v-for="param in visibleCommandParams(row)" :key="param.name || param.paramName">
                        <b>{{ param.name || param.paramName }}</b><em>{{ param.dataType || '-' }}</em>
                      </span>
                      <i v-if="visibleCommandParams(row).length === 0">无外部参数</i>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <section class="content-block">
              <div class="block-head"><h3>Adapter 事件</h3><em>{{ activeEvents.length }} 个</em></div>
              <el-table :data="activeEvents" border stripe size="small" class="industrial-table">
                <el-table-column label="事件名" min-width="200" prop="name" />
                <el-table-column label="事件类型" width="120" prop="type" />
                <el-table-column label="说明" min-width="220" prop="description" />
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="绑定实例" name="bindings">
            <section class="content-block">
              <div class="block-head"><h3>设备实例绑定</h3><em>{{ boundInstances.length }} 台</em></div>
              <el-tree
                v-if="bindingTreeData.length"
                :data="bindingTreeData"
                node-key="id"
                default-expand-all
                :expand-on-click-node="false"
                class="binding-tree"
              >
                <template #default="{ data }">
                  <div class="binding-node" :class="data.type">
                    <span class="binding-label">{{ data.label }}</span>
                    <span class="binding-meta">{{ data.meta }}</span>
                  </div>
                </template>
              </el-tree>
              <el-empty v-else description="暂无实例绑定" :image-size="80" />
            </section>
          </el-tab-pane>

          <el-tab-pane label="配置快照" name="json">
            <section class="content-block json-block">
              <div class="block-head"><h3>解析配置</h3></div>
              <pre>{{ activeConfigText }}</pre>
            </section>
          </el-tab-pane>
        </el-tabs>
      </section>

      <section v-else class="adapter-detail empty-detail">
        <el-empty description="请选择或注册 Adapter" :image-size="110" />
      </section>
    </main>

    <el-drawer v-model="registerDrawerVisible" title="注册 Adapter" size="640px" append-to-body>
      <section class="register-flow-card">
        <div class="flow-head">
          <div>
            <span>注册监听主题</span>
            <strong>smartlab/adapter/register</strong>
          </div>
          <el-tag :type="mqttConnected ? 'success' : 'danger'" effect="plain">{{ mqttConnected ? 'MQTT 已连接' : 'MQTT 未连接' }}</el-tag>
        </div>
        <div class="flow-actions">
          <el-button :icon="Connection" :loading="mqttLoading" @click="reconnectMqtt">连接 / 重连 MQTT</el-button>
          <el-button :icon="Refresh" :loading="pendingLoading" @click="fetchPendingRegistrations">刷新待确认</el-button>
        </div>
      </section>

      <section class="pending-register-panel" v-loading="pendingLoading">
        <div class="drawer-section-head">
          <h3>待确认注册</h3>
          <span>{{ pendingRegistrations.length }} 个</span>
        </div>
        <el-empty v-if="pendingRegistrations.length === 0" description="启动 Adapter 后，系统会在这里显示它发布的注册请求" :image-size="96" />
        <el-table v-else :data="pendingRegistrations" border size="small" class="industrial-table">
          <el-table-column label="Adapter" min-width="190">
            <template #default="{ row }"><strong>{{ row.adapterName }}</strong></template>
          </el-table-column>
          <el-table-column label="收到时间" min-width="160">
            <template #default="{ row }">{{ formatTime(row.receivedAt) }}</template>
          </el-table-column>
          <el-table-column label="模板 / 点位" min-width="130">
            <template #default="{ row }">{{ row.templateCount || asArray(row.parsedConfig?.deviceTemplates).length }} / {{ row.devicePointCount || asArray(row.parsedConfig?.devicePoints).length }}</template>
          </el-table-column>
          <el-table-column label="格式" width="90">
            <template #default="{ row }">{{ row.rawConfigFormat || 'JSON' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" :loading="registerLoading" @click="completePendingRegistration(row)">完成注册</el-button>
              <el-button size="small" plain @click="registerPreview = row.parsedConfig">解析</el-button>
              <el-button size="small" type="danger" link @click="discardPendingRegistration(row)">忽略</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <details class="manual-register-panel">
        <summary>手动导入配置</summary>
        <el-form label-width="96px" size="small" class="register-form">
          <el-form-item label="Adapter 名称">
            <el-input v-model="registerForm.adapterName" placeholder="可为空，解析配置后自动带出" />
          </el-form-item>
          <el-form-item label="配置格式">
            <el-select v-model="registerForm.rawConfigFormat">
              <el-option label="JSON" value="JSON" />
              <el-option label="INI" value="INI" />
              <el-option label="YAML" value="YAML" />
              <el-option label="XML" value="XML" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置文件">
            <div class="upload-row">
              <el-upload :auto-upload="false" :show-file-list="false" accept=".json,.txt,.ini,.yaml,.yml,.xml" :on-change="importRegisterFile">
                <el-button :icon="Upload">上传配置</el-button>
              </el-upload>
              <el-button plain @click="parseRegisterConfig">解析预览</el-button>
            </div>
          </el-form-item>
          <el-form-item label="配置内容">
            <el-input v-model="registerForm.rawConfigContent" type="textarea" :rows="8" placeholder="粘贴 adapter-manifest.json 或 AdapterRegisterRequest.rawConfigContent" />
          </el-form-item>
        </el-form>

        <section v-if="registerPreview" class="preview-box">
          <div><span>Adapter</span><strong>{{ registerPreview.adapterName }}</strong></div>
          <div><span>模板</span><strong>{{ asArray(registerPreview.deviceTemplates).length }}</strong></div>
          <div><span>设备点</span><strong>{{ asArray(registerPreview.devicePoints).length }}</strong></div>
        </section>

        <div class="manual-actions">
          <el-button plain :loading="registerLoading" @click="parseRegisterConfig">解析</el-button>
          <el-button type="primary" :loading="registerLoading" @click="registerAdapter">保存注册</el-button>
        </div>
      </details>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="registerDrawerVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Delete, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'

const keyword = ref('')
const activeTab = ref('runtime')
const activeKey = ref('')
const loading = ref(false)
const mqttLoading = ref(false)
const adapters = ref([])
const instances = ref([])
const models = ref({})
const mqttStatus = ref(null)
const registerDrawerVisible = ref(false)
const registerLoading = ref(false)
const pendingLoading = ref(false)
const pendingRegistrations = ref([])
const registerPreview = ref(null)
const registerForm = reactive({ adapterName: '', rawConfigFormat: 'JSON', rawConfigContent: '' })

const fetchData = async () => {
  loading.value = true
  try {
    const [adapterRes, instanceRes, modelRes] = await Promise.all([
      axios.get('/api/adapter/index/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/device/model/list')
    ])
    adapters.value = adapterRes.data?.success ? asArray(adapterRes.data.data) : []
    instances.value = instanceRes.data?.success ? asArray(instanceRes.data.data) : []
    if (modelRes.data?.success) {
      const map = {}
      asArray(modelRes.data.data).forEach(model => {
        map[String(model.modelId || model.id)] = model.modelName || model.name || String(model.modelId || model.id)
      })
      models.value = map
    }
    if (!adapters.value.some(item => adapterIdOf(item) === activeKey.value)) {
      activeKey.value = adapters.value[0] ? adapterIdOf(adapters.value[0]) : ''
    }
  } finally {
    loading.value = false
  }
  fetchMqttStatus()
}

const fetchMqttStatus = async () => {
  try {
    const res = await getMqttStatus()
    mqttStatus.value = res.data?.data || res.data || null
  } catch (error) {
    mqttStatus.value = { connected: false, status: 'UNAVAILABLE' }
  }
}

const getMqttStatus = async () => {
  try {
    return await axios.get('/api/adapter/protocol/mqtt/status')
  } catch (error) {
    return axios.get('/api/adapter/mqtt/mqtt/status')
  }
}

const reconnectMqtt = async () => {
  mqttLoading.value = true
  try {
    try {
      await axios.post('/api/adapter/protocol/mqtt/reconnect')
    } catch (error) {
      await axios.post('/api/adapter/mqtt/mqtt/reconnect')
    }
    ElMessage.success('已发起 MQTT 重连')
    await fetchMqttStatus()
  } finally {
    mqttLoading.value = false
  }
}

const filteredAdapters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return adapters.value
  return adapters.value.filter(item => String(item.adapterName || '').toLowerCase().includes(kw))
})

const activeAdapter = computed(() => adapters.value.find(item => adapterIdOf(item) === activeKey.value) || null)
const activeConfig = computed(() => parsedConfigOf(activeAdapter.value))
const activeTemplates = computed(() => asArray(activeConfig.value.deviceTemplates))
const activePoints = computed(() => asArray(activeConfig.value.devicePoints))
const activeCommands = computed(() => activeTemplates.value.flatMap(tpl => asArray(tpl.commands)))
const activeEvents = computed(() => activeTemplates.value.flatMap(tpl => eventList(tpl.events)))
const activeConfigText = computed(() => JSON.stringify(activeConfig.value || {}, null, 2))
const templatePointTree = computed(() => activeTemplates.value.map(tpl => {
  const templateName = tpl.templateName || tpl.name || '未命名模板'
  const children = activePoints.value
    .filter(point => String(point.templateName || '') === String(templateName))
    .map(point => ({
      ...point,
      id: `point:${templateName}:${point.devicePoint}`,
      nodeType: 'point',
      label: point.description || point.devicePoint || '未命名点位',
      templateName
    }))
  return {
    id: `template:${templateName}`,
    nodeType: 'template',
    label: tpl.description || templateName,
    templateName,
    summary: `${asArray(tpl.attributes).length} 属性 / ${asArray(tpl.commands).length} 命令 / ${eventCount(tpl.events)} 事件`,
    children
  }
}))
const boundInstances = computed(() => {
  const name = activeAdapter.value?.adapterName
  if (!name) return []
  return instances.value.filter(instance => boundAdapterOf(instance) === name)
})
const bindingTreeData = computed(() => {
  const groups = new Map()
  boundInstances.value.forEach(instance => {
    const modelId = String(instance.modelId || instance.deviceModelId || '')
    if (!groups.has(modelId)) {
      groups.set(modelId, {
        id: `model:${modelId}`,
        type: 'model',
        label: `设备模型：${modelNameOf(modelId)}`,
        meta: modelId ? `模型ID ${modelId}` : '未绑定模型',
        children: []
      })
    }
    const point = boundDevicePointOf(instance) || '未绑定点位'
    groups.get(modelId).children.push({
      id: `instance:${instance.instanceId || instance.id || instance.instanceName}`,
      type: 'instance',
      label: instance.instanceName || '未命名实例',
      meta: `实例ID ${instance.instanceId || instance.id || '-'}，点位 ${point}`,
      children: [{ id: `point:${instance.instanceId || instance.id}:${point}`, type: 'point', label: `绑定点位：${point}`, meta: activeAdapter.value?.adapterName || '-' }]
    })
  })
  return Array.from(groups.values())
})
const mqttConnected = computed(() => mqttStatus.value?.connected === true || mqttStatus.value?.available === true || String(mqttStatus.value?.status || '').toUpperCase() === 'CONNECTED')
const mqttStatusLabel = computed(() => mqttConnected.value ? 'Broker 在线' : 'Broker 未连接')

const openRegisterDrawer = () => {
  registerPreview.value = null
  registerForm.adapterName = ''
  registerForm.rawConfigFormat = 'JSON'
  registerForm.rawConfigContent = ''
  registerDrawerVisible.value = true
  fetchPendingRegistrations()
}

const fetchPendingRegistrations = async () => {
  pendingLoading.value = true
  try {
    const res = await axios.get('/api/adapter/protocol/pending-registrations')
    pendingRegistrations.value = res.data?.success ? asArray(res.data.data) : []
  } finally {
    pendingLoading.value = false
  }
}

const completePendingRegistration = async (item) => {
  if (!item?.adapterName) return
  registerLoading.value = true
  try {
    const res = await axios.post(`/api/adapter/protocol/pending-registrations/${encodeURIComponent(item.adapterName)}/complete`)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '注册失败')
      return
    }
    ElMessage.success('Adapter 已注册')
    registerDrawerVisible.value = false
    await fetchData()
    activeKey.value = adapterIdOf(res.data.data)
  } finally {
    registerLoading.value = false
  }
}

const discardPendingRegistration = async (item) => {
  if (!item?.adapterName) return
  await axios.delete(`/api/adapter/protocol/pending-registrations/${encodeURIComponent(item.adapterName)}`)
  pendingRegistrations.value = pendingRegistrations.value.filter(row => row.adapterName !== item.adapterName)
}
const parseRegisterConfig = async () => {
  if (!registerForm.rawConfigContent.trim()) {
    ElMessage.warning('请先填写或上传配置内容')
    return
  }
  registerLoading.value = true
  try {
    const res = await axios.post('/api/adapter/index/parse-register', buildRegisterPayload())
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '解析失败')
      return
    }
    registerPreview.value = res.data.data
    if (!registerForm.adapterName && registerPreview.value?.adapterName) registerForm.adapterName = registerPreview.value.adapterName
    ElMessage.success('解析成功')
  } finally {
    registerLoading.value = false
  }
}

const registerAdapter = async () => {
  if (!registerForm.rawConfigContent.trim()) {
    ElMessage.warning('请先填写或上传配置内容')
    return
  }
  registerLoading.value = true
  try {
    const res = await axios.post('/api/adapter/index/register', buildRegisterPayload())
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '注册失败')
      return
    }
    registerDrawerVisible.value = false
    ElMessage.success('Adapter 已注册')
    await fetchData()
    activeKey.value = adapterIdOf(res.data.data)
  } finally {
    registerLoading.value = false
  }
}

const deleteAdapter = async (adapter) => {
  await ElMessageBox.confirm(`确认删除 Adapter「${adapter.adapterName}」？`, '删除确认', { type: 'warning' })
  const res = await axios.delete(`/api/adapter/index/delete/${adapter.id}`)
  if (!res.data?.success) {
    ElMessage.error(res.data?.message || '删除失败')
    return
  }
  ElMessage.success('已删除')
  await fetchData()
}

const importRegisterFile = (file) => {
  const rawFile = file?.raw
  if (!rawFile) return false
  const reader = new FileReader()
  reader.onload = () => { registerForm.rawConfigContent = String(reader.result || '') }
  reader.readAsText(rawFile, 'utf-8')
  return false
}

const buildRegisterPayload = () => ({
  adapterName: registerForm.adapterName || manifestAdapterName(registerForm.rawConfigContent),
  rawConfigFormat: registerForm.rawConfigFormat,
  rawConfigContent: registerForm.rawConfigContent,
  timestamp: Date.now()
})

const manifestAdapterName = (content) => {
  try { return JSON.parse(content)?.adapterName || '' } catch { return '' }
}

const asArray = value => Array.isArray(value) ? value : []
const adapterIdOf = adapter => String(adapter?.id || adapter?.adapterName || '')
const parsedConfigOf = adapter => {
  if (!adapter?.parsedConfig) return {}
  if (typeof adapter.parsedConfig === 'string') {
    try { return JSON.parse(adapter.parsedConfig) } catch { return {} }
  }
  return adapter.parsedConfig || {}
}
const templateCount = adapter => asArray(parsedConfigOf(adapter).deviceTemplates).length
const pointCount = adapter => asArray(parsedConfigOf(adapter).devicePoints).length
const modelNameOf = id => models.value[String(id)] || String(id || '-')
const boundAdapterOf = instance => instance?.boundAdapterName || instance?.instanceConfig?.boundAdapterName || instance?.instanceConfig?.adapterName || ''
const boundDevicePointOf = instance => instance?.boundDevicePoint || instance?.instanceConfig?.boundDevicePoint || instance?.instanceConfig?.devicePoint || ''
const statusLabel = status => status || 'UNKNOWN'
const statusClass = status => String(status || 'unknown').toLowerCase()
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const eventList = events => [
  ...asArray(events?.cmdEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '指令周期' })),
  ...asArray(events?.opEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '业务事件' }))
]
const eventCount = events => eventList(events).length
const visibleCommandParams = command => asArray(command?.parameters || command?.commandParameters).filter(param => !param.hidden)

onMounted(fetchData)
</script>

<style scoped>
.adapter-management-page { height: calc(100vh - 52px); min-height: 0; display: flex; flex-direction: column; background: #eef2f6; color: #172033; overflow: hidden; }
.page-toolbar { height: 60px; padding: 0 16px; border-bottom: 1px solid #cbd5e1; background: #fff; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.toolbar-title h1 { margin: 0; font-size: 20px; line-height: 1.2; color: #0f172a; }
.toolbar-title span { color: #64748b; font-size: 12px; }
.toolbar-actions { display: flex; align-items: center; gap: 8px; }
.toolbar-actions .el-input { width: 220px; }
.adapter-workspace { flex: 1; min-height: 0; display: grid; grid-template-columns: 330px minmax(0, 1fr); }
.adapter-sidebar { min-width: 0; background: #f8fafc; border-right: 1px solid #cbd5e1; display: flex; flex-direction: column; overflow: hidden; }
.list-title { height: 42px; padding: 0 12px; border-top: 1px solid #e2e8f0; border-bottom: 1px solid #e2e8f0; background: #fff; display: flex; align-items: center; justify-content: space-between; }
.list-title strong { font-size: 14px; color: #0f172a; }
.list-title em { font-style: normal; color: #64748b; font-size: 12px; }
.adapter-list { flex: 1; min-height: 0; overflow: auto; padding: 10px; }
.adapter-list-item { width: 100%; margin-bottom: 8px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; text-align: left; cursor: pointer; display: grid; gap: 5px; }
.adapter-list-item.active { border-color: #2563eb; background: #eff6ff; }
.adapter-name { color: #0f172a; font-size: 14px; font-weight: 800; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-subline { color: #64748b; font-size: 12px; }
.adapter-status { width: fit-content; padding: 2px 7px; border-radius: 999px; background: #e2e8f0; color: #475569; font-size: 11px; font-weight: 800; }
.adapter-status.online, .adapter-status.alive, .adapter-status.registered { background: #dcfce7; color: #15803d; }
.adapter-status.degraded { background: #fef3c7; color: #b45309; }
.adapter-detail { min-width: 0; min-height: 0; overflow: auto; padding: 12px; }
.empty-detail { background: #fff; display: flex; align-items: center; justify-content: center; }
.detail-header { min-height: 58px; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 7px; background: #fff; display: flex; align-items: center; justify-content: space-between; }
.detail-header h2 { margin: 2px 0 0; font-size: 22px; line-height: 1.2; color: #0f172a; }
.detail-actions { display: flex; gap: 8px; }
.metric-grid { margin-top: 10px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.metric-grid article { padding: 10px 12px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; display: grid; gap: 5px; }
.metric-grid strong { font-size: 16px; color: #0f172a; }
.adapter-tabs { margin-top: 10px; padding: 0 10px 10px; border: 1px solid #cbd5e1; border-radius: 7px; background: #fff; }
.content-block { margin-top: 10px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; }
.block-head { margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; }
.block-head h3 { margin: 0; font-size: 16px; color: #0f172a; }
.block-head em { font-style: normal; color: #64748b; font-size: 12px; }
.template-tags, .param-tags, .mapping-chips { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.mapping-chips span { padding: 2px 6px; border: 1px solid #bfdbfe; border-radius: 4px; color: #1d4ed8; background: #eff6ff; font-size: 12px; }
.mapping-chips em { color: #94a3b8; font-style: normal; }
.industrial-table :deep(.el-table__cell) { padding: 7px 8px; }
.json-block pre { max-height: 500px; overflow: auto; margin: 0; padding: 10px; border: 1px solid #e2e8f0; border-radius: 5px; background: #0f172a; color: #e2e8f0; font-size: 12px; line-height: 1.5; }
.register-form :deep(.el-select) { width: 100%; }
.upload-row { display: flex; gap: 8px; align-items: center; }
.register-flow-card { margin-bottom: 10px; padding: 12px; border: 1px solid #bfdbfe; border-left: 3px solid #2563eb; border-radius: 6px; background: #eff6ff; }
.flow-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.flow-head div { display: grid; gap: 4px; min-width: 0; }
.flow-head span { color: #64748b; font-size: 12px; }
.flow-head strong { color: #0f172a; font-size: 15px; word-break: break-all; }
.flow-actions { margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap; }
.pending-register-panel { min-height: 130px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; }
.drawer-section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.drawer-section-head h3 { margin: 0; color: #0f172a; font-size: 15px; }
.drawer-section-head span { color: #64748b; font-size: 12px; }
.manual-register-panel { margin-top: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; padding: 10px; }
.manual-register-panel summary { cursor: pointer; color: #0f172a; font-weight: 700; }
.manual-register-panel .register-form { margin-top: 10px; }
.manual-actions { display: flex; justify-content: flex-end; gap: 8px; }
.preview-box { margin-top: 8px; padding: 10px; border: 1px solid #bfdbfe; border-radius: 6px; background: #eff6ff; display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.preview-box div { display: grid; gap: 4px; }
.preview-box span { color: #64748b; font-size: 12px; }
.preview-box strong { color: #0f172a; font-size: 15px; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 8px; }
.mqtt-strip { height: 46px; padding: 0 12px; border-bottom: 1px solid #cbd5e1; background: #fff; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 8px; }
.strip-label { color: #64748b; font-size: 12px; font-weight: 700; }
.mqtt-strip strong { color: #0f172a; font-size: 14px; }
.adapter-list { padding: 0; }
.adapter-list-item { margin: 0; border: 0; border-bottom: 1px solid #dbe4ef; border-radius: 0; background: #fff; grid-template-columns: 1fr auto; align-items: center; column-gap: 8px; }
.adapter-list-item.active { border-color: #dbe4ef; background: #eaf2ff; box-shadow: inset 3px 0 0 #2563eb; }
.adapter-subline { grid-column: 1 / 3; }
.adapter-status { border-radius: 3px; }
.adapter-detail { padding: 0; background: #eef2f6; }
.detail-header { min-height: 58px; padding: 10px 14px; border-width: 0 0 1px; border-radius: 0; }
.runtime-table { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); border-bottom: 1px solid #cbd5e1; background: #fff; }
.runtime-table div { min-width: 0; padding: 9px 12px; border-right: 1px solid #e2e8f0; display: grid; gap: 3px; }
.runtime-table div:last-child { border-right: 0; }
.runtime-table span { color: #64748b; font-size: 12px; }
.runtime-table strong { color: #0f172a; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-tabs { margin: 0; padding: 0 12px 12px; border-width: 0; border-radius: 0; background: #fff; }
.adapter-tabs :deep(.el-tabs__header) { margin: 0; }
.content-block { margin-top: 10px; padding: 0; border: 1px solid #cbd5e1; border-radius: 0; background: #fff; }
.block-head { height: 40px; margin: 0; padding: 0 10px; border-bottom: 1px solid #dbe4ef; background: #f8fafc; }
.relation-name { display: flex; align-items: center; gap: 8px; min-width: 0; }
.relation-name strong { color: #0f172a; }
.relation-name span { color: #64748b; font-size: 12px; }
.mapping-chips span { border-radius: 2px; background: #f8fbff; }
.mapping-chips b { margin-right: 4px; color: #0f172a; }
.mapping-chips i { margin: 0 4px; color: #64748b; font-style: normal; }
.param-table-list { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.param-table-list span { display: inline-flex; align-items: center; overflow: hidden; border: 1px solid #cbd5e1; background: #fff; }
.param-table-list b { padding: 2px 7px; color: #0f172a; font-weight: 700; }
.param-table-list em { padding: 2px 7px; border-left: 1px solid #cbd5e1; background: #f1f5f9; color: #475569; font-style: normal; }
.param-table-list i { color: #94a3b8; font-style: normal; }
.binding-tree { padding: 8px 10px; }
.binding-tree :deep(.el-tree-node__content) { height: 34px; border-bottom: 1px solid #eef2f6; }
.binding-node { width: 100%; display: grid; grid-template-columns: minmax(180px, 0.6fr) minmax(220px, 1fr); gap: 14px; align-items: center; }
.binding-node.model .binding-label { font-weight: 800; color: #0f172a; }
.binding-node.instance .binding-label { color: #1d4ed8; font-weight: 700; }
.binding-node.point .binding-label { color: #059669; }
.binding-meta { color: #64748b; font-size: 12px; }
.register-flow-card, .pending-register-panel, .manual-register-panel, .preview-box { border-radius: 0; }
.preview-box { background: #fff; border-color: #cbd5e1; }
@media (max-width: 1120px) { .adapter-workspace { grid-template-columns: 280px minmax(0, 1fr); } .template-grid, .command-grid, .metric-grid { grid-template-columns: 1fr; } }
</style>