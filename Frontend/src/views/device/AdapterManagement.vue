<template>
  <div class="adapter-management-page">
    <section class="adapter-overview-bar">
      <div class="page-heading"><h1>Adapter 管理</h1><span>设备执行代理注册、运行状态与能力契约</span></div>
      <div class="overview-metrics">
        <div class="overview-metric"><span>Broker</span><strong :class="mqttConnected ? 'ok' : 'danger'">{{ mqttConnected ? '在线' : '未连接' }}</strong></div>
        <div class="overview-metric"><span>已注册</span><strong>{{ adapters.length }}</strong></div>
        <div class="overview-metric"><span>在线</span><strong class="ok">{{ onlineAdapterCount }}</strong></div>
        <div class="overview-metric"><span>异常</span><strong :class="staleAdapterCount ? 'danger' : ''">{{ staleAdapterCount }}</strong></div>
        <div class="overview-metric"><span>待审核</span><strong>{{ pendingRegistrations.length }}</strong></div>
      </div>
      <div class="overview-actions">
        <el-button size="small" @click="fetchData">刷新</el-button>
        <el-button v-if="!mqttConnected" size="small" type="warning" @click="reconnectMqtt">Broker 重连</el-button>
        <el-button size="small" @click="openRegisterDrawer('manual')">导入配置</el-button>
        <el-button size="small" type="primary" @click="openRegisterDrawer('mqtt')">待审核注册<span v-if="pendingRegistrations.length">（{{ pendingRegistrations.length }}）</span></el-button>
      </div>
    </section>
    <el-alert v-if="!mqttConnected" class="broker-alert" type="warning" :closable="false" show-icon title="Broker 当前不可用：Adapter 实时注册与心跳不会更新，手动导入仍可使用。" />
    <main class="adapter-workspace">
      <aside class="adapter-sidebar">
        <div class="sidebar-toolbar">
          <div class="list-title"><strong>Adapter 列表</strong><em>{{ filteredAdapters.length }} 个</em></div>
          <el-button type="primary" size="small" circle :icon="Plus" aria-label="导入 Adapter 配置" @click="openRegisterDrawer('manual')" />
        </div>
        <div class="sidebar-search">
          <el-input v-model="keyword" placeholder="搜索 Adapter" clearable :prefix-icon="Search" />
          <el-radio-group v-model="statusFilter" size="small" class="status-filter">
            <el-radio-button label="all">全部</el-radio-button><el-radio-button label="online">在线</el-radio-button><el-radio-button label="stale">异常</el-radio-button>
          </el-radio-group>
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
            <span class="adapter-subline">{{ categoryCount(item) }} 类别 / {{ templateCount(item) }} 模板 / {{ pointCount(item) }} 点位</span>
            <span class="adapter-status" :class="runtimeStatusClass(item)">{{ runtimeStatusLabel(item) }}</span>
          </button>
        </div>
      </aside>

      <section v-if="activeAdapter" class="adapter-detail">
        <div class="detail-header">
          <div>
            <h2>{{ activeAdapter.adapterName }}</h2>
            <p>{{ activeConfig.adapterDescription || '暂无 Adapter 说明' }}</p>
          </div>
          <div class="detail-actions">
            <el-dropdown trigger="click" @command="handleAdapterAction">
              <el-button circle size="small" :icon="MoreFilled" aria-label="Adapter 更多操作" />
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="update">更新配置</el-dropdown-item><el-dropdown-item command="delete" class="danger-menu-item">删除 Adapter</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </div>
        </div>

        <section class="runtime-table">
          <div><span>运行状态</span><strong :class="runtimeStatusClass(activeAdapter)">{{ runtimeStatusLabel(activeAdapter) }}</strong></div>
          <div><span>最后心跳</span><strong>{{ formatTime(activeAdapter.lastHeartbeat) }}</strong></div>
          <div><span>注册状态</span><strong>{{ registrationStatusLabel(activeAdapter.status) }}</strong></div>
          <div><span>设备模板</span><strong>{{ activeTemplates.length }}</strong></div>
          <div><span>设备点位</span><strong>{{ activePoints.length }}</strong></div>
          <div><span>绑定实例</span><strong>{{ boundInstances.length }}</strong></div>
        </section>

        <el-tabs v-model="activeTab" class="adapter-tabs">
          <el-tab-pane label="概览" name="runtime">
            <section class="content-block">
              <div class="block-head"><h3>模板能力</h3><em>来自已保存的 Adapter 配置</em></div>
              <div v-if="activeCategories.length" class="capability-summary-list">
                <section v-for="row in activeCategories" :key="row.categoryName" class="capability-summary">
                  <div class="capability-identity"><strong>{{ row.categoryName || '-' }}</strong><span>{{ row.deviceTemplate?.templateName || row.deviceTemplate?.name || '-' }}</span></div>
                  <div class="capability-groups">
                    <div><label>属性</label><p>{{ capabilityText(asArray(row.deviceTemplate?.attributes), attr => `${attr.name} · ${attr.dataType}`) }}</p></div>
                    <div><label>命令</label><p>{{ capabilityText(asArray(row.deviceTemplate?.commands), command => `${command.name} · ${asArray(command.parameters).length} 参数`) }}</p></div>
                    <div><label>指令周期事件</label><p>{{ capabilityText(asArray(row.deviceTemplate?.events?.cmdEvents), event => event.name || event.eventName) }}</p></div>
                    <div><label>业务事件</label><p>{{ capabilityText(asArray(row.deviceTemplate?.events?.opEvents), event => event.name || event.eventName) }}</p></div>
                  </div>
                </section>
              </div>
              <div v-else class="compact-empty">暂无模板能力</div>
            </section>

            <section class="content-block">
              <div class="block-head"><h3>设备点映射</h3><em>{{ activeTemplates.length }} 模板 / {{ activePoints.length }} 点位</em></div>
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
                      <el-tag size="small" :type="row.nodeType === 'category' ? 'warning' : (row.nodeType === 'template' ? 'primary' : 'success')" effect="plain">
                        {{ row.nodeType === 'category' ? '类别' : (row.nodeType === 'template' ? '模板' : '点位') }}
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
                    <span v-if="row.nodeType === 'template' || row.nodeType === 'category'">{{ row.summary }}</span>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="点位编号" width="100">
                  <template #default="{ row }">{{ row.nodeType === 'point' ? (row.index ?? '-') : '-' }}</template>
                </el-table-column>
                <el-table-column label="属性点映射" min-width="260">
                  <template #default="{ row }">
                    <div v-if="row.nodeType === 'point'" class="mapping-chips">
                      <span>-</span>
                    </div>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="能力契约" name="contract">
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

          <el-tab-pane label="配置版本" name="json">
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

    <el-drawer v-model="registerDrawerVisible" :title="registerDrawerTitle" size="78%" append-to-body class="unified-workflow-drawer" :before-close="confirmCloseRegisterDrawer">
      <div class="adapter-register-workbench">
        <nav class="adapter-register-nav" aria-label="注册流程导航">
          <button type="button" :class="{ active: registerStep === 0 }" @click="goRegisterStep(0)">{{ registerSource === 'mqtt' ? '01 选择待审核请求' : '01 导入配置' }}</button>
          <button type="button" :class="{ active: registerStep === 1, disabled: !registerPreview }" :disabled="!registerPreview" @click="goRegisterStep(1)">02 校验与审阅</button>
          <button type="button" :class="{ active: registerStep === 2, disabled: !registerPreview }" :disabled="!registerPreview" @click="goRegisterStep(2)">03 确认保存</button>
        </nav>

        <el-scrollbar class="adapter-register-scroll">
          <section v-show="registerStep === 0" id="register-source" class="register-section source-section">
            <div class="drawer-section-head"><h3>选择配置来源</h3></div>
            <el-radio-group v-model="registerSource" size="small" class="register-source-picker" @change="resetRegisterPreview">
              <el-radio-button label="mqtt">MQTT 接收</el-radio-button>
              <el-radio-button label="manual">手动导入</el-radio-button>
            </el-radio-group>

            <div v-if="registerSource === 'mqtt'" class="source-content" v-loading="pendingLoading">
              <div class="source-hint">
                <span>监听主题</span><code>{{ mqttTopics.registerTopic }}</code>
                <el-tag size="small" :type="mqttConnected ? 'success' : 'danger'" effect="plain">{{ mqttConnected ? 'Broker 在线' : 'Broker 未连接' }}</el-tag>


              </div>
              <div v-if="pendingRegistrations.length === 0" class="compact-empty">等待 Adapter 向注册主题发布配置</div>
              <el-table v-else :data="pendingRegistrations" border size="small" class="industrial-table">
                <el-table-column label="Adapter" min-width="180"><template #default="{ row }"><strong>{{ row.adapterName }}</strong></template></el-table-column>
                <el-table-column label="收到时间" min-width="155"><template #default="{ row }">{{ formatTime(row.receivedAt) }}</template></el-table-column>
                <el-table-column label="类别 / 模板 / 点位" min-width="150"><template #default="{ row }">{{ pendingCategoryCount(row) }} / {{ pendingTemplateCount(row) }} / {{ pendingPointCount(row) }}</template></el-table-column>
                <el-table-column label="格式" width="80"><template #default="{ row }">{{ row.rawConfigFormat || 'JSON' }}</template></el-table-column>
                <el-table-column label="操作" width="175" fixed="right"><template #default="{ row }"><el-button type="primary" size="small" @click="reviewPendingRegistration(row)">审阅</el-button><el-button type="danger" link size="small" @click="discardPendingRegistration(row)">移除请求</el-button></template></el-table-column>
              </el-table>
            </div>

            <div v-else class="source-content manual-source">
              <p class="source-copy">上传原始配置文件，或直接粘贴配置内容。系统会自动识别 Adapter 名称与配置格式，再进入审阅。</p>
              <el-form label-width="92px" size="small" class="register-form">
                <el-form-item label="配置格式"><el-select v-model="registerForm.rawConfigFormat"><el-option v-for="format in adapterRegisterFormats" :key="format" :label="format" :value="format" /></el-select></el-form-item>
                <el-form-item label="配置文件">
                  <el-upload drag :auto-upload="false" :show-file-list="false" accept=".json,.txt,.ini,.yaml,.yml,.xml" :on-change="importRegisterFile">
                    <el-icon class="upload-icon"><Upload /></el-icon><div class="el-upload__text">拖拽配置文件到此处，或 <em>点击选择</em></div>
                  </el-upload>
                </el-form-item>
                <el-form-item label="配置内容"><el-input v-model="registerForm.rawConfigContent" type="textarea" :rows="8" @input="resetManualPreview" placeholder="粘贴 Adapter 原始配置内容" /></el-form-item>
                <el-form-item label=""><el-button type="primary" :loading="registerLoading" @click="parseRegisterConfig">解析配置</el-button></el-form-item>
              </el-form>
            </div>
          </section>

          <section v-show="registerStep === 1" id="register-parse" class="register-section" :class="{ disabled: !registerPreview }">
            <div class="drawer-section-head"><h3>解析结果</h3><span v-if="registerPreview">已生成统一契约</span></div>
            <div v-if="!registerPreview" class="compact-empty">完成来源配置后，系统将在此显示解析结果</div>
            <div v-else class="parse-summary">
              <div><span>Adapter</span><strong>{{ registerPreview.adapterName }}</strong></div>
              <div><span>类别</span><strong>{{ adapterCategoriesOf(registerPreview).length }}</strong></div>
              <div><span>模板</span><strong>{{ adapterCategoriesOf(registerPreview).length }}</strong></div>
              <div><span>点位</span><strong>{{ adapterPointsOf(registerPreview).length }}</strong></div>
            </div>
          </section>

          <section v-show="registerStep === 1" id="register-review" class="register-section" :class="{ disabled: !registerPreview }">
            <div class="drawer-section-head"><h3>审阅配置</h3><span v-if="registerPreview">仅可编辑说明字段</span></div>
            <div v-if="!registerPreview" class="compact-empty">请先解析配置</div>
            <template v-else>
              <el-form label-width="92px" size="small" class="review-form"><el-form-item label="Adapter 说明"><el-input v-model="registerPreview.adapterDescription" placeholder="用于管理页面显示，不影响底层协议" /></el-form-item></el-form>
              <el-table :data="adapterCategoriesOf(registerPreview)" border size="small" class="industrial-table review-table">
                <el-table-column label="类别 / 说明" min-width="200">
                  <template #default="{ row }"><strong>{{ row.categoryName || '-' }}</strong><el-input v-model="row.categoryDescription" class="description-input" size="small" placeholder="类别说明" /></template>
                </el-table-column>
                <el-table-column label="模板 / 说明" min-width="230">
                  <template #default="{ row }"><strong>{{ row.deviceTemplate?.templateName || row.deviceTemplate?.name || '-' }}</strong><el-input v-model="row.deviceTemplate.description" class="description-input" size="small" placeholder="模板说明" /></template>
                </el-table-column>
                <el-table-column label="属性" min-width="190">
                  <template #default="{ row }"><div class="contract-tag-list"><span v-for="attr in asArray(row.deviceTemplate?.attributes)" :key="attr.name" class="contract-text">{{ attr.name }} · {{ attr.dataType }}</span><span v-if="!asArray(row.deviceTemplate?.attributes).length">-</span></div></template>
                </el-table-column>
                <el-table-column label="命令" min-width="180">
                  <template #default="{ row }"><div class="contract-tag-list"><span v-for="command in asArray(row.deviceTemplate?.commands)" :key="command.name" class="contract-text">{{ command.name }} · {{ asArray(command.parameters).length }} 参数</span><span v-if="!asArray(row.deviceTemplate?.commands).length">-</span></div></template>
                </el-table-column>
                <el-table-column label="事件" min-width="180">
                  <template #default="{ row }"><div class="contract-tag-list"><span v-for="event in eventList(row.deviceTemplate?.events)" :key="`${event.type}-${event.name}`" class="contract-text">{{ event.name }}</span><span v-if="!eventList(row.deviceTemplate?.events).length">-</span></div></template>
                </el-table-column>
                <el-table-column label="设备点位" min-width="180">
                  <template #default="{ row }"><div class="contract-tag-list"><span v-for="point in asArray(row.devicePoints)" :key="point.devicePoint" class="contract-text">{{ point.devicePoint }} · {{ point.index ?? '-' }}</span><span v-if="!asArray(row.devicePoints).length">-</span></div></template>
                </el-table-column>
              </el-table>
              <div class="review-actions"><el-button @click="registerStep = 0">返回</el-button><el-button type="primary" @click="registerStep = 2">下一步：确认</el-button></div>
            </template>
          </section>

          <section v-show="registerStep === 2" id="register-save" class="register-section" :class="{ disabled: !registerPreview }">
            <div class="drawer-section-head"><h3>{{ existingAdapterForPreview ? '确认更新 Adapter' : '确认注册 Adapter' }}</h3></div>
            <el-alert v-if="existingAdapterForPreview" type="warning" :closable="false" show-icon :title="`已存在同名 Adapter，将更新现有配置；当前绑定 ${boundCountFor(existingAdapterForPreview.adapterName)} 个设备实例。`" />
            <div class="save-summary"><span>Adapter</span><strong>{{ registerPreview?.adapterName || '-' }}</strong><span>操作</span><strong>{{ existingAdapterForPreview ? '更新现有配置' : '创建新 Adapter' }}</strong><span>类别 / 点位</span><strong>{{ adapterCategoriesOf(registerPreview).length }} / {{ adapterPointsOf(registerPreview).length }}</strong></div>
            <div class="save-panel"><el-button @click="registerStep = 1">返回审阅</el-button><el-button type="primary" :disabled="!registerPreview" :loading="registerLoading" @click="saveReviewedRegistration">{{ existingAdapterForPreview ? '确认更新' : '确认注册' }}</el-button></div>
          </section>
        </el-scrollbar>
      </div>
      <template #footer><div class="drawer-footer"><el-button @click="confirmCloseRegisterDrawer(() => closeRegisterDrawer())">取消</el-button></div></template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled, Plus, Search, Upload } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'
import { adapterRegisterFormats, loadProtocolMetadata, mqttTopics } from './components/deviceModel/deviceModelConstants'

const authStore = useAuthStore()

const keyword = ref('')
const statusFilter = ref('all')
const activeTab = ref('runtime')
const activeKey = ref('')
const loading = ref(false)
const adapters = ref([])
const instances = ref([])
const models = ref({})
const mqttStatus = ref(null)
const registerDrawerVisible = ref(false)
const registerLoading = ref(false)
const pendingLoading = ref(false)
const pendingRegistrations = ref([])
const registerPreview = ref(null)
const registerSource = ref('mqtt')
const registerStep = ref(0)
const selectedPendingAdapterName = ref('')
const pendingDrafts = reactive({})
const registerForm = reactive({ adapterName: '', rawConfigFormat: 'JSON', rawConfigContent: '' })
let registrationStream = null
let mqttStatusTimer = null

const fetchData = async () => {
  loading.value = true
  try {
    const [adapterResult, instanceResult, modelResult] = await Promise.allSettled([
      axios.get('/api/adapter/index/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/device/model/list')
    ])
    if (adapterResult.status === 'fulfilled') {
      adapters.value = adapterResult.value.data?.success ? asArray(adapterResult.value.data.data) : []
    } else {
      ElMessage.error('Adapter 列表加载失败')
    }
    if (instanceResult.status === 'fulfilled') instances.value = instanceResult.value.data?.success ? asArray(instanceResult.value.data.data) : []
    if (modelResult.status === 'fulfilled' && modelResult.value.data?.success) {
      const map = {}
      asArray(modelResult.value.data.data).forEach(model => {
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

const handleAdapterAction = async (command) => {
  if (command === 'delete' && activeAdapter.value) await deleteAdapter(activeAdapter.value)
  if (command === 'update' && activeAdapter.value) openRegisterDrawer('manual', activeAdapter.value)
}
const filteredAdapters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return adapters.value.filter(item => {
    const matchesKeyword = !kw || String(item.adapterName || '').toLowerCase().includes(kw)
    const matchesStatus = statusFilter.value === 'all' || runtimeStatusClass(item) === statusFilter.value
    return matchesKeyword && matchesStatus
  })
})

const activeAdapter = computed(() => adapters.value.find(item => adapterIdOf(item) === activeKey.value) || null)
const activeConfig = computed(() => parsedConfigOf(activeAdapter.value))
const activeCategories = computed(() => adapterCategoriesOf(activeConfig.value))
const activeTemplates = computed(() => activeCategories.value.map(category => ({
  ...category.deviceTemplate,
  categoryName: category.categoryName,
  categoryDescription: category.categoryDescription
})))
const activePoints = computed(() => adapterPointsOf(activeConfig.value))
const activeCommands = computed(() => activeTemplates.value.flatMap(tpl => asArray(tpl.commands)))
const activeEvents = computed(() => activeTemplates.value.flatMap(tpl => eventList(tpl.events)))
const activeConfigText = computed(() => JSON.stringify(activeConfig.value || {}, null, 2))
const templatePointTree = computed(() => activeCategories.value.map(category => {
  const template = category.deviceTemplate || {}
  const templateName = template.templateName || template.name || category.categoryName || '\u672a\u547d\u540d\u6a21\u677f'
  const categoryName = category.categoryName || templateName
  const pointChildren = asArray(category.devicePoints).map(point => ({
    ...point,
    id: `point:${categoryName}:${point.devicePoint}`,
    nodeType: 'point',
    label: point.description || point.devicePoint || '\u672a\u547d\u540d\u70b9\u4f4d',
    templateName
  }))
  return {
    id: `category:${categoryName}`,
    nodeType: 'category',
    label: category.categoryDescription || categoryName,
    templateName,
    summary: `${asArray(template.attributes).length} 属性 / ${asArray(template.commands).length} 命令 / ${eventCount(template.events)} 事件`,
    children: [{
      id: `template:${categoryName}:${templateName}`,
      nodeType: 'template',
      label: template.description || templateName,
      templateName,
      summary: `${asArray(template.attributes).length} 属性 / ${asArray(template.commands).length} 命令 / ${eventCount(template.events)} 事件`,
      children: pointChildren
    }]
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
        label: `\u8bbe\u5907\u6a21\u578b:${modelNameOf(modelId)}`,
        meta: modelId ? `\u6a21\u578bID ${modelId}` : '\u672a\u7ed1\u5b9a\u6a21\u578b',
        children: []
      })
    }
    const point = boundDevicePointOf(instance) || '\u672a\u7ed1\u5b9a\u70b9\u4f4d'
    groups.get(modelId).children.push({
      id: `instance:${instance.instanceId || instance.id || instance.instanceName}`,
      type: 'instance',
      label: instance.instanceName || '\u672a\u547d\u540d\u5b9e\u4f8b',
      meta: `\u5b9e\u4f8bID ${instance.instanceId || instance.id || '-'}\uff0c\u70b9\u4f4d ${point}`,
      children: [{ id: `point:${instance.instanceId || instance.id}:${point}`, type: 'point', label: `\u7ed1\u5b9a\u70b9\u4f4d:${point}`, meta: activeAdapter.value?.adapterName || '-' }]
    })
  })
  return Array.from(groups.values())
})
const mqttConnected = computed(() => mqttStatus.value?.connected === true || mqttStatus.value?.available === true || String(mqttStatus.value?.status || '').toUpperCase() === 'CONNECTED')
const onlineAdapterCount = computed(() => adapters.value.filter(item => runtimeStatusClass(item) === 'online').length)
const staleAdapterCount = computed(() => adapters.value.filter(item => runtimeStatusClass(item) === 'stale').length)
const existingAdapterForPreview = computed(() => {
  const name = registerPreview.value?.adapterName
  return name ? adapters.value.find(item => item.adapterName === name) || null : null
})
const registerDrawerTitle = computed(() => registerSource.value === 'mqtt' ? '审核 Adapter 注册请求' : '导入 Adapter 配置')


const connectRegistrationStream = () => {
  if (registrationStream) return
  try {
    const token = authStore.token
    registrationStream = new EventSource('/api/adapter/protocol/pending-registrations/stream?token=' + encodeURIComponent(token))
    registrationStream.addEventListener('pending_snapshot', event => {
      pendingRegistrations.value = asArray(JSON.parse(event.data || '[]'))
    })
    registrationStream.addEventListener('adapter_register_request', event => {
      const item = JSON.parse(event.data || '{}')
      if (!item?.adapterName) return
      const index = pendingRegistrations.value.findIndex(row => row.adapterName === item.adapterName)
      if (index >= 0) pendingRegistrations.value.splice(index, 1, item)
      else pendingRegistrations.value.unshift(item)
      if (!pendingDrafts[item.adapterName]) pendingDrafts[item.adapterName] = cloneJson(item.parsedConfig)
      if (registerDrawerVisible.value) ElMessage.info(`收到 Adapter「${item.adapterName}」注册请求，请在待审核列表中审阅`)
    })
    registrationStream.onerror = () => {
      closeRegistrationStream()
    }
  } catch (error) {
    registrationStream = null
  }
}

const closeRegistrationStream = () => {
  if (registrationStream) {
    registrationStream.close()
    registrationStream = null
  }
}

const resetRegisterPreview = () => {
  registerPreview.value = null
  selectedPendingAdapterName.value = ''
  registerStep.value = 0
}

const resetManualPreview = () => {
  if (registerSource.value === 'manual') resetRegisterPreview()
}
const reviewPendingRegistration = (item) => {
  registerSource.value = 'mqtt'
  selectedPendingAdapterName.value = item?.adapterName || ''
  registerPreview.value = cloneJson(pendingDrafts[selectedPendingAdapterName.value] || item?.parsedConfig || null)
  registerStep.value = registerPreview.value ? 1 : 0
}

const goRegisterStep = step => {
  if (step > 0 && !registerPreview.value) return
  registerStep.value = step
}

const completeReviewedRegistration = () => {
  if (!selectedPendingAdapterName.value) return
  const item = pendingRegistrations.value.find(row => row.adapterName === selectedPendingAdapterName.value)
  if (item) completePendingRegistration(item)
}

const openRegisterDrawer = (source = 'mqtt', adapter = null) => {
  registerPreview.value = null
  registerSource.value = source
  registerStep.value = 0
  selectedPendingAdapterName.value = ''
  connectRegistrationStream()
  registerForm.adapterName = ''
  registerForm.rawConfigFormat = 'JSON'
  registerForm.rawConfigContent = ''
  registerDrawerVisible.value = true
  if (adapter) {
    registerForm.adapterName = adapter.adapterName || ''
    registerForm.rawConfigFormat = adapter.parsedConfig?.registerMeta?.rawConfigFormat || 'JSON'
    registerForm.rawConfigContent = adapter.originalConfig || ''
  }
  fetchPendingRegistrations()
}

const fetchPendingRegistrations = async () => {
  pendingLoading.value = true
  try {
    const res = await axios.get('/api/adapter/protocol/pending-registrations')
    pendingRegistrations.value = res.data?.success ? asArray(res.data.data) : []
    pendingRegistrations.value.forEach(item => { if (item?.adapterName && !pendingDrafts[item.adapterName]) pendingDrafts[item.adapterName] = cloneJson(item.parsedConfig) })
  } finally {
    pendingLoading.value = false
  }
}

const completePendingRegistration = async (item) => {
  if (!item?.adapterName) return
  registerLoading.value = true
  try {
    const reviewedConfig = selectedPendingAdapterName.value === item.adapterName ? registerPreview.value : pendingDrafts[item.adapterName] || item.parsedConfig
    const res = await axios.post(`/api/adapter/protocol/pending-registrations/${encodeURIComponent(item.adapterName)}/complete`, { parsedConfig: reviewedConfig || item.parsedConfig })
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '注册失败')
      return
    }
    ElMessage.success('Adapter \u5df2\u6ce8\u518c')
    registerDrawerVisible.value = false
    selectedPendingAdapterName.value = ''
    registerPreview.value = null
    delete pendingDrafts[item.adapterName]
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
  delete pendingDrafts[item.adapterName]
}
const parseRegisterConfig = async () => {
  if (!registerForm.rawConfigContent.trim()) {
    ElMessage.warning('\u8bf7\u5148\u586b\u5199\u6216\u4e0a\u4f20\u914d\u7f6e\u5185\u5bb9')
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
    registerStep.value = 1
    ElMessage.success('解析成功')
  } finally {
    registerLoading.value = false
  }
}

const saveReviewedRegistration = async () => {
  if (!registerPreview.value) {
    ElMessage.warning('请先完成配置解析')
    return
  }
  if (registerSource.value === 'mqtt') {
    await completeReviewedRegistration()
    return
  }
  await registerAdapter()
}
const registerAdapter = async () => {
  if (!registerPreview.value) {
    ElMessage.warning('请先解析并审阅配置')
    return
  }
  if (!registerForm.rawConfigContent.trim()) {
    ElMessage.warning('\u8bf7\u5148\u586b\u5199\u6216\u4e0a\u4f20\u914d\u7f6e\u5185\u5bb9')
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
    ElMessage.success('Adapter \u5df2\u6ce8\u518c')
    await fetchData()
    activeKey.value = adapterIdOf(res.data.data)
  } finally {
    registerLoading.value = false
  }
}

const deleteAdapter = async (adapter) => {
  const boundCount = boundCountFor(adapter.adapterName)
  if (boundCount > 0) {
    ElMessage.warning(`Adapter「${adapter.adapterName}」仍绑定 ${boundCount} 个设备实例，请先解除绑定`)
    return
  }
  await ElMessageBox.confirm(`确认删除 Adapter「${adapter.adapterName}」？删除后将无法用于新的设备实例绑定。`, '删除确认', { type: 'warning' })
  const res = await axios.delete(`/api/adapter/index/delete/${adapter.id}`)
  if (!res.data?.success) {
    ElMessage.error(res.data?.message || '删除失败')
    return
  }
  ElMessage.success('\u5df2\u5220\u9664')
  await fetchData()
}

const importRegisterFile = (file) => {
  const rawFile = file?.raw
  if (!rawFile) return false
  resetManualPreview()
  const reader = new FileReader()
  reader.onload = () => { registerForm.rawConfigContent = String(reader.result || '') }
  reader.readAsText(rawFile, 'utf-8')
  return false
}

const buildRegisterPayload = () => {
  const payload = {
    adapterName: registerForm.adapterName || manifestAdapterName(registerForm.rawConfigContent),
    rawConfigFormat: registerForm.rawConfigFormat,
    rawConfigContent: registerForm.rawConfigContent,
    timestamp: Date.now()
  }
  if (registerPreview.value) payload.parsedConfig = registerPreview.value
  return payload
}

const closeRegisterDrawer = () => {
  registerDrawerVisible.value = false
  resetRegisterPreview()
}

const confirmCloseRegisterDrawer = (done) => {
  if (!registerPreview.value || registerStep.value === 0) {
    done()
    return
  }
  ElMessageBox.confirm('当前审阅内容尚未保存，确认放弃吗？', '关闭注册流程', { type: 'warning' })
    .then(() => done())
    .catch(() => {})
}

const reconnectMqtt = async () => {
  try {
    const res = await axios.post('/api/adapter/protocol/mqtt/reconnect')
    mqttStatus.value = res.data?.data || res.data || null
    await fetchMqttStatus()
    ElMessage.success(mqttConnected.value ? 'Broker 已连接' : 'Broker 重连请求已发送')
  } catch (error) {
    ElMessage.error('Broker 重连失败')
  }
}

const manifestAdapterName = (content) => {
  try {
    const jsonName = JSON.parse(content)?.adapterName
    if (jsonName) return String(jsonName).trim()
  } catch { /* fall through to INI */ }
  const iniMatch = String(content || '').match(/^\s*adapterName\s*=\s*([^;#\r\n]+)\s*$/mi)
  return iniMatch ? iniMatch[1].trim() : ''
}

const cloneJson = value => value == null ? null : JSON.parse(JSON.stringify(value))
const asArray = value => Array.isArray(value) ? value : []
const adapterIdOf = adapter => String(adapter?.id || adapter?.adapterName || '')
const parsedConfigOf = adapter => {
  if (!adapter?.parsedConfig) return {}
  if (typeof adapter.parsedConfig === 'string') {
    try { return JSON.parse(adapter.parsedConfig) } catch { return {} }
  }
  return adapter.parsedConfig || {}
}
const adapterCategoriesOf = config => asArray(config?.deviceCategories)
const adapterPointsOf = config => adapterCategoriesOf(config).flatMap(category =>
  asArray(category.devicePoints).map(point => ({ ...point, categoryName: category.categoryName }))
)
const categoryCount = adapter => adapterCategoriesOf(parsedConfigOf(adapter)).length
const pendingCategoryCount = row => adapterCategoriesOf(row?.parsedConfig).length
const pendingTemplateCount = row => adapterCategoriesOf(row?.parsedConfig).length
const pendingPointCount = row => adapterPointsOf(row?.parsedConfig).length
const templateCount = adapter => adapterCategoriesOf(parsedConfigOf(adapter)).length
const pointCount = adapter => adapterPointsOf(parsedConfigOf(adapter)).length
const modelNameOf = id => models.value[String(id)] || String(id || '-')
const boundAdapterOf = instance => instance?.boundAdapterName || instance?.instanceConfig?.boundAdapterName || instance?.instanceConfig?.adapterName || ''
const boundDevicePointOf = instance => instance?.boundDevicePoint || instance?.instanceConfig?.boundDevicePoint || instance?.instanceConfig?.devicePoint || ''
const runtimeStatusClass = adapter => {
  if (!adapter?.lastHeartbeat) return 'unknown'
  const heartbeat = new Date(adapter.lastHeartbeat).getTime()
  if (!Number.isFinite(heartbeat)) return 'unknown'
  const age = Date.now() - heartbeat
  if (age <= 120000 && String(adapter.status || '').toUpperCase() !== 'OFFLINE') return 'online'
  return 'stale'
}
const runtimeStatusLabel = adapter => ({ online: '在线', stale: '心跳超时', unknown: '未连接' }[runtimeStatusClass(adapter)] || '未知')
const registrationStatusLabel = status => ({ REGISTERED: '已注册', ONLINE: '已注册', OFFLINE: '已注册', DISABLED: '已停用' }[String(status || '').toUpperCase()] || '待确认')
const statusLabel = status => registrationStatusLabel(status)
const statusClass = status => String(status || 'unknown').toLowerCase()
const boundCountFor = adapterName => instances.value.filter(instance => boundAdapterOf(instance) === adapterName).length
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const eventList = events => [
  ...asArray(events?.cmdEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '指令周期' })),
  ...asArray(events?.opEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '业务事件' }))
]
const eventCount = events => eventList(events).length
const visibleCommandParams = command => asArray(command?.parameters || command?.commandParameters).filter(param => !param.internal)
const capabilityText = (items, formatter) => {
  const values = asArray(items).map(formatter).filter(Boolean)
  return values.length ? values.join(' · ') : '-'
}

onMounted(() => {
  loadProtocolMetadata().catch(() => ElMessage.warning('协议元数据加载失败，配置格式列表可能不完整'))
  fetchData()
  connectRegistrationStream()
  mqttStatusTimer = window.setInterval(fetchMqttStatus, 30000)
})
onUnmounted(() => {
  closeRegistrationStream()
  if (mqttStatusTimer) window.clearInterval(mqttStatusTimer)
})
</script>

<style scoped>
.adapter-management-page { height: calc(100vh - 52px); min-height: 0; display: flex; flex-direction: column; overflow: hidden; background: #f0f2f5; color: #1e2533; }
.adapter-management-page, .adapter-management-page * { box-sizing: border-box; }
.adapter-overview-bar { min-height: 54px; padding: 7px 12px; display: flex; align-items: center; gap: 14px; border-bottom: 1px solid #d9dde6; background: #fff; }
.page-heading { min-width: 190px; display: grid; gap: 1px; }
.page-heading h1 { margin: 0; color: #1e2533; font-size: 17px; font-weight: 600; }
.page-heading span { color: #7b8798; font-size: 11px; }
.overview-metrics { display: flex; flex: 1; min-width: 0; align-items: center; gap: 14px; }
.overview-metric { min-width: 52px; display: grid; gap: 1px; }
.overview-metric span { color: #7b8798; font-size: 11px; }
.overview-metric strong { color: #344054; font-size: 15px; font-weight: 600; }
.overview-metric strong.ok { color: #1a8754; }
.overview-metric strong.danger { color: #c2413b; }
.overview-actions { display: flex; align-items: center; gap: 6px; flex: 0 0 auto; }
.broker-alert { flex: 0 0 auto; border-radius: 0; }
.adapter-workspace { flex: 1; min-height: 0; display: grid; grid-template-columns: 248px minmax(0, 1fr); overflow: hidden; }
.adapter-sidebar { width: 248px; min-width: 0; max-width: 100%; display: flex; flex-direction: column; overflow: hidden; border-right: 1px solid #d9dde6; background: #fff; }
.list-title { height: 38px; padding: 0 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #edf0f4; }
.list-title strong { color: #344054; font-size: 13px; font-weight: 600; }
.list-title em { color: #8a93a6; font-size: 12px; font-style: normal; }
.sidebar-toolbar { height: 46px; padding: 0 10px 0 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e5e7eb; }
.sidebar-toolbar .list-title { height: auto; min-width: 0; padding: 0; border: 0; flex: 1 1 auto; justify-content: flex-start; gap: 6px; overflow: hidden; }
.sidebar-toolbar .list-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sidebar-toolbar .el-button { flex: 0 0 auto; }
.sidebar-search { width: 100%; min-width: 0; max-width: 100%; padding: 8px; display: grid; grid-template-columns: minmax(0, 1fr); gap: 7px; overflow: hidden; border-bottom: 1px solid #edf0f4; }
.sidebar-search > * { min-width: 0; max-width: 100%; }
.sidebar-search :deep(.el-input) { width: 100%; min-width: 0; }
.sidebar-search :deep(.el-input__wrapper) { min-height: 28px; }
.status-filter { width: 100%; min-width: 0; max-width: 100%; display: flex; overflow: hidden; }
.status-filter :deep(.el-radio-button) { min-width: 0; flex: 1 1 0; }
.status-filter :deep(.el-radio-button__inner) { width: 100%; padding: 5px 0; font-size: 11px; }
.detail-actions { display: flex; align-items: center; gap: 8px; }
.danger-menu-item { color: var(--color-danger) !important; }
.compact-empty { padding: 14px 0; color: #8a93a6; font-size: 12px; text-align: center; }
.adapter-list { width: 100%; min-width: 0; flex: 1; min-height: 0; overflow: auto; padding: 6px; }
.adapter-list-item { width: 100%; min-height: 66px; padding: 10px; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; column-gap: 6px; row-gap: 3px; border: 0; border-radius: 4px; background: transparent; text-align: left; cursor: pointer; transition: background-color .15s ease; }
.adapter-list-item:hover { background: #f6f8fb; }
.adapter-list-item.active { background: #e8f1fb; box-shadow: inset 3px 0 0 var(--color-primary); }
.adapter-name { min-width: 0; overflow: hidden; color: #1e2533; font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.adapter-subline { grid-column: 1 / 3; overflow: hidden; color: #7b8798; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.adapter-status { padding: 0 5px; border-radius: 2px; background: #eef1f5; color: #596579; font-size: 11px; line-height: 18px; }
.adapter-status.online, .adapter-status.alive, .adapter-status.registered { background: #e7f7ee; color: #1a8754; }
.adapter-status.degraded { background: #fff4df; color: #b76a00; }
.adapter-detail { min-width: 0; min-height: 0; overflow: auto; padding: 10px 12px 12px; background: #f0f2f5; }
.empty-detail { display: flex; align-items: center; justify-content: center; }
.detail-header { min-height: 50px; padding: 10px 12px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border: 1px solid #d9dde6; background: #fff; }
.detail-header h2 { margin: 0; color: #1e2533; font-size: 18px; font-weight: 600; line-height: 24px; }
.detail-header p { max-width: 720px; margin: 2px 0 0; overflow: hidden; color: #6b7280; font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.detail-actions { flex: 0 0 auto; }
.runtime-table { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); margin-bottom: 8px; border: 1px solid #d9dde6; border-top: 0; background: #fff; }
.runtime-table div { min-width: 0; padding: 8px 12px; display: grid; gap: 2px; border-right: 1px solid #e5e7eb; }
.runtime-table div:last-child { border-right: 0; }
.runtime-table span { color: #7b8798; font-size: 11px; }
.runtime-table strong { overflow: hidden; color: #344054; font-size: 14px; font-weight: 600; line-height: 20px; text-overflow: ellipsis; white-space: nowrap; }
.adapter-tabs { padding: 0 10px 10px; border: 1px solid #d9dde6; background: #fff; }
.adapter-tabs :deep(.el-tabs__header) { margin: 0; }
.adapter-tabs :deep(.el-tabs__item) { height: 40px; padding: 0 14px; font-size: 13px; }
.content-block { margin-top: 8px; border: 1px solid #e0e4eb; background: #fff; }
.block-head { height: 36px; padding: 0 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e5e7eb; background: #f8fafc; }
.block-head h3 { margin: 0; color: #344054; font-size: 13px; font-weight: 600; }
.block-head em { color: #7b8798; font-size: 11px; font-style: normal; }
.industrial-table :deep(.el-table__cell) { padding: 6px 8px; }
.industrial-table :deep(th.el-table__cell) { background: #f3f6fa; color: #4a5568; font-size: 11px; font-weight: 600; }
.capability-summary-list { padding: 0 12px; }
.capability-summary { padding: 14px 0; border-bottom: 1px solid #edf0f4; }
.capability-summary:last-child { border-bottom: 0; }
.capability-identity { margin-bottom: 12px; display: flex; align-items: baseline; gap: 8px; }
.capability-identity strong { color: #344054; font-size: 14px; font-weight: 600; }
.capability-identity span { color: #667085; font-size: 12px; }
.capability-groups { display: grid; grid-template-columns: minmax(150px, .8fr) minmax(220px, 1.25fr) repeat(2, minmax(200px, 1fr)); gap: 16px; }
.capability-groups div { min-width: 0; }
.capability-groups label { display: block; margin-bottom: 4px; color: #7b8798; font-size: 11px; }
.capability-groups p { margin: 0; color: #596579; font-size: 12px; line-height: 20px; overflow-wrap: anywhere; }
.relation-name { display: flex; align-items: center; gap: 6px; min-width: 0; }
.relation-name strong { overflow: hidden; color: #344054; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.relation-name span { color: #7b8798; font-size: 11px; }
.mapping-chips, .param-table-list { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.mapping-chips span { padding: 0 5px; border: 1px solid #d9dde6; border-radius: 2px; color: #596579; font-size: 11px; line-height: 19px; }
.mapping-chips b { color: #344054; }
.mapping-chips i { margin: 0 3px; color: #8a93a6; font-style: normal; }
.mapping-chips em, .param-table-list i { color: #a1a9b7; font-size: 11px; font-style: normal; }
.param-table-list span { display: inline-flex; overflow: hidden; border: 1px solid #d9dde6; border-radius: 2px; }
.param-table-list b { padding: 0 6px; color: #344054; font-size: 11px; line-height: 19px; }
.param-table-list em { padding: 0 6px; border-left: 1px solid #d9dde6; background: #f8fafc; color: #7b8798; font-size: 11px; font-style: normal; line-height: 19px; }
.binding-tree { padding: 4px 8px; }
.binding-tree :deep(.el-tree-node__content) { height: 30px; border-bottom: 1px solid #f0f2f5; }
.binding-node { width: 100%; display: grid; grid-template-columns: minmax(180px, .6fr) minmax(220px, 1fr); gap: 12px; }
.binding-label { color: #344054; font-size: 13px; }
.binding-node.model .binding-label { font-weight: 600; }
.binding-node.instance .binding-label { color: var(--color-primary); }
.binding-node.point .binding-label { color: var(--color-success); }
.binding-meta { color: #7b8798; font-size: 11px; }
.json-block pre { max-height: 500px; margin: 0; padding: 12px; overflow: auto; background: #1e2533; color: #e8edf4; font-size: 11px; line-height: 1.5; }
.drawer-section-head { height: 28px; margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; }
.drawer-section-head h3 { margin: 0; color: #344054; font-size: 13px; font-weight: 600; }
.drawer-section-head span { color: #7b8798; font-size: 11px; }
.review-form { margin-bottom: 8px; }
.review-actions, .manual-actions, .drawer-footer { display: flex; justify-content: flex-end; gap: 8px; }
.review-actions { padding-top: 8px; }
.review-table :deep(.el-input__wrapper) { min-height: 28px; box-shadow: none; }
.description-input { width: 100%; margin-top: 6px; }
.contract-tag-list { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; min-height: 24px; }
.contract-tag-list > span { color: #98a2b3; font-size: 12px; }
.contract-tag-list .contract-text { padding: 0; border: 0; background: transparent; color: #596579; font-size: 12px; line-height: 20px; }
.contract-description { display: block; margin-top: 3px; color: #667085; font-size: 12px; }
.broker-state { flex: 0 0 auto; margin-right: 8px; }
.manual-register-panel summary { color: #344054; font-size: 13px; font-weight: 600; cursor: pointer; }
.manual-register-hint { margin: 6px 0 0; color: #7b8798; font-size: 11px; }
.manual-register-panel .register-form { margin-top: 8px; }
.register-form :deep(.el-select) { width: 100%; }
.upload-row { display: flex; align-items: center; gap: 6px; }
.preview-box { margin: 8px 0; padding: 8px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; border: 1px solid #e0e4eb; background: #f8fafc; }
.preview-box div { display: grid; gap: 2px; }
.preview-box span { color: #7b8798; font-size: 11px; }
.preview-box strong { color: #344054; font-size: 13px; font-weight: 600; }
.register-section { margin: 0; padding: 12px; border: 1px solid #d9dde6; background: #fff; }
.register-section + .register-section { margin-top: 8px; }
.register-section.disabled { background: #fafbfd; }
.register-source-picker { margin-bottom: 10px; }
.source-content { min-width: 0; }
.source-hint { min-height: 30px; margin-bottom: 8px; display: flex; align-items: center; flex-wrap: wrap; gap: 6px; color: #6b7280; font-size: 12px; }
.source-hint code { padding: 2px 5px; border: 1px solid #d9dde6; background: #f8fafc; color: #344054; font-family: Consolas, Menlo, monospace; font-size: 11px; }
.source-copy { margin: 0 0 10px; color: #6b7280; font-size: 12px; line-height: 18px; }
.manual-source :deep(.el-upload), .manual-source :deep(.el-upload-dragger) { width: 100%; }
.manual-source :deep(.el-upload-dragger) { height: 76px; padding: 12px; border-radius: 3px; }
.upload-icon { margin: 0 0 4px; color: var(--color-primary); font-size: 22px; }
.parse-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid #e0e4eb; background: #f8fafc; }
.parse-summary div { min-width: 0; padding: 8px 10px; display: grid; gap: 2px; border-right: 1px solid #e0e4eb; }
.parse-summary div:last-child { border-right: 0; }
.parse-summary span { color: #7b8798; font-size: 11px; }
.parse-summary strong { overflow: hidden; color: #344054; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.save-panel { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #6b7280; font-size: 12px; line-height: 18px; }
.save-panel .el-button { flex: 0 0 auto; }
.adapter-register-nav button { width: 100%; margin: 0 0 3px; padding: 8px 10px; display: block; border: 0; border-radius: 4px; background: transparent; color: var(--color-text-sub, #667085); font: inherit; font-size: 13px; font-weight: 500; text-align: left; cursor: pointer; }
.adapter-register-nav button:hover { background: #eef4fb; color: var(--color-primary, #1a6bbf); }
.adapter-register-nav button.active { background: var(--color-primary-light, #e8f1fb); color: var(--color-primary, #1a6bbf); }
.adapter-register-nav button.disabled { color: #a1a9b7; cursor: not-allowed; }
.save-summary { margin: 10px 0; padding: 10px; display: grid; grid-template-columns: 90px 1fr 90px 1fr 90px 1fr; gap: 6px 10px; border: 1px solid #e0e4eb; background: #f8fafc; }
.save-summary span { color: #7b8798; font-size: 11px; }
.save-summary strong { color: #344054; font-size: 13px; }
@media (min-width: 761px) and (max-width: 1180px) {
  .adapter-overview-bar { flex-wrap: wrap; }
  .page-heading { flex: 1 0 180px; }
  .overview-metrics { order: 3; flex-basis: 100%; }
  .overview-actions { margin-left: auto; }
  .capability-groups { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .adapter-management-page { height: auto; min-height: calc(100vh - 52px); overflow: visible; }
  .adapter-overview-bar { align-items: flex-start; flex-direction: column; gap: 8px; }
  .overview-metrics { width: 100%; gap: 12px; overflow: auto; }
  .overview-actions { width: 100%; flex-wrap: wrap; }
  .adapter-workspace { grid-template-columns: 1fr; }
  .adapter-sidebar { width: 100%; max-height: 320px; border-right: 0; border-bottom: 1px solid #d9dde6; }
  .adapter-detail { overflow: visible; padding: 8px; }
  .detail-header { padding: 10px; }
  .runtime-table { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .runtime-table div:nth-child(2n) { border-right: 0; }
  .adapter-tabs { padding: 0 8px 8px; }
  .binding-node { grid-template-columns: 1fr; gap: 1px; }
  .capability-groups { grid-template-columns: 1fr; gap: 10px; }
  .parse-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .parse-summary div:nth-child(2n) { border-right: 0; }
  .save-summary { grid-template-columns: 80px 1fr; }
  .save-panel { align-items: flex-start; flex-direction: column; }
}</style>
