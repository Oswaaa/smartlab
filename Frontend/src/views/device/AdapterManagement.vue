<template>
  <div class="adapter-management-page">
    <main class="adapter-workspace">
      <aside class="adapter-sidebar">
        <div class="sidebar-toolbar">
          <div class="list-title"><strong>Adapter 列表</strong><em>{{ filteredAdapters.length }} 个</em></div>
          <el-tag class="broker-state" size="small" :type="mqttConnected ? 'success' : 'danger'" effect="plain">
            {{ mqttConnected ? 'Broker 在线' : 'Broker 未连接' }}
          </el-tag>
          <el-button type="primary" size="small" :icon="Plus" @click="openRegisterDrawer">注册</el-button>
        </div>
        <div class="sidebar-search"><el-input v-model="keyword" placeholder="搜索 Adapter" clearable :prefix-icon="Search" /></div>
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
            <span class="adapter-status" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span>
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
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="delete" class="danger-menu-item">删除 Adapter</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
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
          <el-tab-pane label="配置契约" name="runtime">
            <section class="content-block">
              <div class="block-head"><h3>模板能力</h3><em>来自已保存的 Adapter 配置</em></div>
              <el-table :data="activeCategories" border size="small" class="industrial-table contract-table">
                <el-table-column label="类别 / 模板" min-width="210">
                  <template #default="{ row }"><strong>{{ row.categoryName || '-' }}</strong><span class="contract-description">{{ row.deviceTemplate?.templateName || row.deviceTemplate?.name || '-' }}</span></template>
                </el-table-column>
                <el-table-column label="属性" min-width="220">
                  <template #default="{ row }"><div class="contract-tag-list"><el-tag v-for="attr in asArray(row.deviceTemplate?.attributes)" :key="attr.name" size="small" effect="plain">{{ attr.name }} · {{ attr.dataType }}</el-tag><span v-if="!asArray(row.deviceTemplate?.attributes).length">-</span></div></template>
                </el-table-column>
                <el-table-column label="命令 / 参数" min-width="250">
                  <template #default="{ row }"><div class="contract-tag-list"><el-tag v-for="command in asArray(row.deviceTemplate?.commands)" :key="command.name" size="small" type="primary" effect="plain">{{ command.name }} · {{ asArray(command.parameters).length }} 参数</el-tag><span v-if="!asArray(row.deviceTemplate?.commands).length">-</span></div></template>
                </el-table-column>
                <el-table-column label="事件" min-width="220">
                  <template #default="{ row }"><div class="contract-tag-list"><el-tag v-for="event in eventList(row.deviceTemplate?.events)" :key="`${event.type}-${event.name}`" size="small" type="warning" effect="plain">{{ event.name }}</el-tag><span v-if="!eventList(row.deviceTemplate?.events).length">-</span></div></template>
                </el-table-column>
              </el-table>
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

    <el-drawer v-model="registerDrawerVisible" title="注册 Adapter" size="78%" append-to-body class="unified-workflow-drawer">
      <div class="adapter-register-workbench">
        <el-anchor
          class="adapter-register-nav"
          @click="(event) => event.preventDefault()"
          container=".adapter-register-scroll .el-scrollbar__wrap"
          :offset="20"
        >
          <el-anchor-link href="#register-source" title="01 选择来源" />
          <el-anchor-link href="#register-parse" title="02 解析结果" />
          <el-anchor-link href="#register-review" title="03 审阅配置" />
          <el-anchor-link href="#register-save" title="04 保存注册" />
        </el-anchor>

        <el-scrollbar class="adapter-register-scroll">
          <section id="register-source" class="register-section source-section">
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
                <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button type="primary" size="small" @click="reviewPendingRegistration(row)">解析并审阅</el-button><el-button type="danger" link size="small" @click="discardPendingRegistration(row)">忽略</el-button></template></el-table-column>
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

          <section id="register-parse" class="register-section" :class="{ disabled: !registerPreview }">
            <div class="drawer-section-head"><h3>解析结果</h3><span v-if="registerPreview">已生成统一契约</span></div>
            <div v-if="!registerPreview" class="compact-empty">完成来源配置后，系统将在此显示解析结果</div>
            <div v-else class="parse-summary">
              <div><span>Adapter</span><strong>{{ registerPreview.adapterName }}</strong></div>
              <div><span>类别</span><strong>{{ adapterCategoriesOf(registerPreview).length }}</strong></div>
              <div><span>模板</span><strong>{{ adapterCategoriesOf(registerPreview).length }}</strong></div>
              <div><span>点位</span><strong>{{ adapterPointsOf(registerPreview).length }}</strong></div>
            </div>
          </section>

          <section id="register-review" class="register-section" :class="{ disabled: !registerPreview }">
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
                  <template #default="{ row }"><div class="contract-tag-list"><el-tag v-for="attr in asArray(row.deviceTemplate?.attributes)" :key="attr.name" size="small" effect="plain">{{ attr.name }} · {{ attr.dataType }}</el-tag><span v-if="!asArray(row.deviceTemplate?.attributes).length">-</span></div></template>
                </el-table-column>
                <el-table-column label="命令" min-width="180">
                  <template #default="{ row }"><div class="contract-tag-list"><el-tag v-for="command in asArray(row.deviceTemplate?.commands)" :key="command.name" size="small" type="primary" effect="plain">{{ command.name }} · {{ asArray(command.parameters).length }} 参数</el-tag><span v-if="!asArray(row.deviceTemplate?.commands).length">-</span></div></template>
                </el-table-column>
                <el-table-column label="事件" min-width="180">
                  <template #default="{ row }"><div class="contract-tag-list"><el-tag v-for="event in eventList(row.deviceTemplate?.events)" :key="`${event.type}-${event.name}`" size="small" type="warning" effect="plain">{{ event.name }}</el-tag><span v-if="!eventList(row.deviceTemplate?.events).length">-</span></div></template>
                </el-table-column>
                <el-table-column label="设备点位" min-width="180">
                  <template #default="{ row }"><div class="contract-tag-list"><el-tag v-for="point in asArray(row.devicePoints)" :key="point.devicePoint" size="small" type="success" effect="plain">{{ point.devicePoint }} · {{ point.index ?? '-' }}</el-tag><span v-if="!asArray(row.devicePoints).length">-</span></div></template>
                </el-table-column>
              </el-table>
            </template>
          </section>

          <section id="register-save" class="register-section" :class="{ disabled: !registerPreview }">
            <div class="drawer-section-head"><h3>保存注册</h3></div>
            <div class="save-panel"><span>确认后将保存审阅后的统一配置，并注册到设备模型可选择的 Adapter 列表。</span><el-button type="primary" :disabled="!registerPreview" :loading="registerLoading" @click="saveReviewedRegistration">确认并保存</el-button></div>
          </section>
        </el-scrollbar>
      </div>
      <template #footer><div class="drawer-footer"><el-button @click="registerDrawerVisible = false">取消</el-button></div></template>
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
const selectedPendingAdapterName = ref('')
const registerForm = reactive({ adapterName: '', rawConfigFormat: 'JSON', rawConfigContent: '' })
let registrationStream = null
let mqttStatusTimer = null

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

const handleAdapterAction = async (command) => {
  if (command === 'delete' && activeAdapter.value) await deleteAdapter(activeAdapter.value)
}
const filteredAdapters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return adapters.value
  return adapters.value.filter(item => String(item.adapterName || '').toLowerCase().includes(kw))
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
const mqttStatusLabel = computed(() => mqttConnected.value ? 'Broker \u5728\u7ebf' : 'Broker \u672a\u8fde\u63a5')


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
      selectedPendingAdapterName.value = item.adapterName
      registerPreview.value = cloneJson(item.parsedConfig)
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
}

const resetManualPreview = () => {
  if (registerSource.value === 'manual') resetRegisterPreview()
}
const reviewPendingRegistration = (item) => {
  registerSource.value = 'mqtt'
  selectedPendingAdapterName.value = item?.adapterName || ''
  registerPreview.value = cloneJson(item?.parsedConfig || null)
}

const completeReviewedRegistration = () => {
  if (!selectedPendingAdapterName.value) return
  const item = pendingRegistrations.value.find(row => row.adapterName === selectedPendingAdapterName.value)
  if (item) completePendingRegistration(item)
}

const openRegisterDrawer = () => {
  registerPreview.value = null
  registerSource.value = 'mqtt'
  selectedPendingAdapterName.value = ''
  connectRegistrationStream()
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
    const reviewedConfig = selectedPendingAdapterName.value === item.adapterName ? registerPreview.value : item.parsedConfig
    const res = await axios.post(`/api/adapter/protocol/pending-registrations/${encodeURIComponent(item.adapterName)}/complete`, { parsedConfig: reviewedConfig || item.parsedConfig })
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '注册失败')
      return
    }
    ElMessage.success('Adapter \u5df2\u6ce8\u518c')
    registerDrawerVisible.value = false
    selectedPendingAdapterName.value = ''
    registerPreview.value = null
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
  await ElMessageBox.confirm(`确认删除 Adapter「${adapter.adapterName}」？`, '删除确认', { type: 'warning' })
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
const statusLabel = status => status || 'UNKNOWN'
const statusClass = status => String(status || 'unknown').toLowerCase()
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const eventList = events => [
  ...asArray(events?.cmdEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '指令周期' })),
  ...asArray(events?.opEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '业务事件' }))
]
const eventCount = events => eventList(events).length
const visibleCommandParams = command => asArray(command?.parameters || command?.commandParameters).filter(param => !param.internal)

onMounted(() => {
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
.adapter-workspace { flex: 1; min-height: 0; display: grid; grid-template-columns: 286px minmax(0, 1fr); }
.adapter-sidebar { min-width: 0; display: flex; flex-direction: column; overflow: hidden; border-right: 1px solid #d9dde6; background: #fff; }
.list-title { height: 38px; padding: 0 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #edf0f4; }
.list-title strong { color: #344054; font-size: 13px; font-weight: 600; }
.list-title em { color: #8a93a6; font-size: 12px; font-style: normal; }
.sidebar-toolbar { height: 42px; padding: 0 10px 0 12px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e5e7eb; }
.sidebar-toolbar .list-title { height: auto; padding: 0; border: 0; flex: 1; }
.sidebar-search { padding: 8px; border-bottom: 1px solid #edf0f4; }
.sidebar-search :deep(.el-input__wrapper) { min-height: 28px; }
.detail-actions { display: flex; align-items: center; gap: 8px; }
.danger-menu-item { color: var(--color-danger) !important; }
.compact-empty { padding: 14px 0; color: #8a93a6; font-size: 12px; text-align: center; }
.adapter-list { flex: 1; min-height: 0; overflow: auto; padding: 4px; }
.adapter-list-item { width: 100%; min-height: 60px; padding: 8px; display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; column-gap: 6px; row-gap: 2px; border: 0; border-radius: 3px; background: transparent; text-align: left; cursor: pointer; transition: background-color .15s ease; }
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
.runtime-table { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); margin-bottom: 8px; border: 1px solid #d9dde6; border-top: 0; background: #fff; }
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
@media (max-width: 760px) {
  .adapter-management-page { height: auto; min-height: calc(100vh - 52px); overflow: visible; }
  .adapter-workspace { grid-template-columns: 1fr; }
  .adapter-sidebar { max-height: 280px; border-right: 0; border-bottom: 1px solid #d9dde6; }
  .adapter-detail { overflow: visible; padding: 8px; }
  .detail-header { padding: 10px; }
  .runtime-table { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .runtime-table div:nth-child(2n) { border-right: 0; }
  .adapter-tabs { padding: 0 8px 8px; }
  .binding-node { grid-template-columns: 1fr; gap: 1px; }
  .parse-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .parse-summary div:nth-child(2n) { border-right: 0; }
  .save-panel { align-items: flex-start; flex-direction: column; }
}</style>
