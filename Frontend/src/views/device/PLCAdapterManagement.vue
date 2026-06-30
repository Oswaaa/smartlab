<template>
  <div class="adapter-page">
    <header class="page-head">
      <h1>设备执行代理</h1>
      <div class="head-actions">
        <el-input v-model="keyword" placeholder="搜索 Adapter" size="small" clearable />
        <el-button :icon="Refresh" size="small" @click="fetchData">刷新</el-button>
      </div>
    </header>

    <main class="workspace">
      <aside class="left-list">
        <div class="panel-title">
          <strong>Adapter 列表</strong>
          <span>{{ filteredAdapters.length }} 个</span>
        </div>
        <div class="adapter-list" v-loading="loading">
          <el-empty v-if="filteredAdapters.length === 0" description="暂无 Adapter" :image-size="90" />
          <button
            v-for="item in filteredAdapters"
            :key="adapterIdOf(item)"
            class="adapter-item"
            :class="{ active: activeKey === adapterIdOf(item) }"
            type="button"
            @click="activeKey = adapterIdOf(item)"
          >
            <span class="adapter-name">{{ item.adapterName || '未命名 Adapter' }}</span>
            <span class="adapter-meta">{{ templateCount(item) }} 模板 / {{ pointCount(item) }} 点位</span>
            <span class="status" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span>
          </button>
        </div>
      </aside>

      <section v-if="activeAdapter" class="detail-area">
        <div class="summary-row">
          <div class="summary-cell primary">
            <span>Adapter</span>
            <strong>{{ activeAdapter.adapterName }}</strong>
          </div>
          <div class="summary-cell">
            <span>连接状态</span>
            <el-tag :type="tagType(activeAdapter.status)" effect="plain">{{ statusLabel(activeAdapter.status) }}</el-tag>
          </div>
          <div class="summary-cell">
            <span>最后心跳</span>
            <strong>{{ formatTime(activeAdapter.lastHeartbeat) }}</strong>
          </div>
          <div class="summary-cell">
            <span>绑定设备</span>
            <strong>{{ boundInstances.length }} 台</strong>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="adapter-tabs">
          <el-tab-pane label="连接与点位" name="runtime">
            <div class="runtime-grid">
              <section class="panel compact-panel">
                <div class="panel-header"><h2>注册信息</h2></div>
                <div class="kv-grid">
                  <div><span>协议入口</span><strong>smartlab/adapter/register</strong></div>
                  <div><span>当前状态</span><strong>{{ statusLabel(activeAdapter.status) }}</strong></div>
                  <div><span>创建时间</span><strong>{{ formatTime(activeAdapter.createTime) }}</strong></div>
                  <div><span>更新时间</span><strong>{{ formatTime(activeAdapter.updateTime) }}</strong></div>
                </div>
              </section>

              <section class="panel compact-panel">
                <div class="panel-header"><h2>设备模板</h2><span>{{ activeTemplates.length }} 个</span></div>
                <div class="chip-grid">
                  <span v-for="tpl in activeTemplates" :key="tpl.templateName" class="template-chip">
                    {{ tpl.description || tpl.templateName }}
                  </span>
                  <span v-if="activeTemplates.length === 0" class="empty-inline">暂无模板</span>
                </div>
              </section>
            </div>

            <section class="panel points-panel">
              <div class="panel-header"><h2>设备点</h2><span>{{ activePoints.length }} 个</span></div>
              <div class="point-grid">
                <article v-for="point in activePoints" :key="point.devicePoint" class="point-card">
                  <div>
                    <strong>{{ point.description || point.devicePoint }}</strong>
                    <span>{{ point.templateName || '-' }}</span>
                  </div>
                  <em v-if="point.index !== undefined">#{{ point.index }}</em>
                </article>
                <div v-if="activePoints.length === 0" class="empty-inline">暂无设备点</div>
              </div>
            </section>
          </el-tab-pane>

          <el-tab-pane label="绑定设备" name="bindings">
            <section class="panel bindings-panel">
              <div class="panel-header"><h2>设备实例绑定</h2><span>{{ boundInstances.length }} 台</span></div>
              <div class="binding-grid">
                <article v-for="instance in boundInstances" :key="instance.instanceId || instance.id" class="binding-card">
                  <strong>{{ instance.instanceName || '未命名设备' }}</strong>
                  <span>{{ modelNameOf(instance.modelId || instance.deviceModelId) }}</span>
                  <em>{{ boundDevicePointOf(instance) || '-' }}</em>
                </article>
                <div v-if="boundInstances.length === 0" class="empty-inline">暂无绑定设备</div>
              </div>
            </section>
          </el-tab-pane>

          <el-tab-pane label="配置快照" name="json">
            <section class="panel json-panel">
              <div class="panel-header"><h2>Adapter 配置</h2></div>
              <pre>{{ activeConfigText }}</pre>
            </section>
          </el-tab-pane>
        </el-tabs>
      </section>

      <section v-else class="detail-area empty-area">
        <el-empty description="请选择 Adapter" :image-size="110" />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import axios from 'axios'

const keyword = ref('')
const activeTab = ref('runtime')
const activeKey = ref('')
const loading = ref(false)
const adapters = ref([])
const instances = ref([])
const models = ref({})

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
const boundInstances = computed(() => {
  const name = activeAdapter.value?.adapterName
  if (!name) return []
  return instances.value.filter(instance => boundAdapterOf(instance) === name)
})
const activeConfigText = computed(() => JSON.stringify(activeConfig.value || {}, null, 2))

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
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const statusLabel = status => status || 'UNKNOWN'
const statusClass = status => String(status || 'UNKNOWN').toLowerCase()
const tagType = status => ['ONLINE', 'ALIVE', 'REGISTERED'].includes(String(status || '').toUpperCase()) ? 'success' : 'info'

onMounted(fetchData)
</script>

<style scoped>
.adapter-page { height: calc(100vh - 52px); background: #eef2f6; display: flex; flex-direction: column; overflow: hidden; color: #172033; }
.page-head { height: 56px; padding: 0 16px; background: #fff; border-bottom: 1px solid #ccd6e3; display: flex; align-items: center; justify-content: space-between; }
.page-head h1 { margin: 0; font-size: 19px; color: #101828; }
.head-actions { display: flex; align-items: center; gap: 8px; }
.head-actions .el-input { width: 220px; }
.workspace { flex: 1; min-height: 0; display: grid; grid-template-columns: 320px 1fr; gap: 0; }
.left-list { min-width: 0; background: #fff; border-right: 1px solid #ccd6e3; display: flex; flex-direction: column; overflow: hidden; }
.panel-title { height: 48px; padding: 0 12px; border-bottom: 1px solid #e2e8f0; display: flex; align-items: center; justify-content: space-between; }
.panel-title strong { font-size: 15px; color: #0f172a; }
.panel-title span { color: #64748b; font-size: 12px; }
.adapter-list { flex: 1; min-height: 0; overflow: auto; padding: 10px; background: #f8fafc; }
.adapter-item { width: 100%; padding: 10px 11px; margin-bottom: 8px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; text-align: left; cursor: pointer; display: grid; gap: 5px; }
.adapter-item.active { border-color: #2563eb; background: #eff6ff; }
.adapter-name { color: #0f172a; font-size: 14px; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-meta { color: #64748b; font-size: 12px; }
.status { width: fit-content; padding: 2px 7px; border-radius: 999px; background: #e2e8f0; color: #475569; font-size: 11px; font-weight: 700; }
.status.online, .status.alive, .status.registered { background: #dcfce7; color: #15803d; }
.status.degraded { background: #fef3c7; color: #b45309; }
.detail-area { min-width: 0; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.empty-area { align-items: center; justify-content: center; background: #fff; }
.summary-row { margin: 12px; display: grid; grid-template-columns: minmax(220px, 1.2fr) repeat(3, minmax(140px, 0.8fr)); gap: 10px; }
.summary-cell { min-width: 0; padding: 12px; border: 1px solid #ccd6e3; border-radius: 6px; background: #fff; display: grid; gap: 5px; }
.summary-cell.primary { border-left: 4px solid #2563eb; }
.summary-cell span { color: #64748b; font-size: 12px; }
.summary-cell strong { color: #0f172a; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-tabs { flex: 1; min-height: 0; padding: 0 12px 12px; overflow: hidden; }
.adapter-tabs :deep(.el-tabs__content) { height: calc(100% - 48px); overflow: auto; }
.runtime-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 10px; }
.panel { border: 1px solid #ccd6e3; border-radius: 6px; background: #fff; overflow: hidden; }
.panel-header { height: 42px; padding: 0 12px; border-bottom: 1px solid #e2e8f0; display: flex; align-items: center; justify-content: space-between; }
.panel-header h2 { margin: 0; color: #0f172a; font-size: 15px; }
.panel-header span { color: #64748b; font-size: 12px; }
.kv-grid { padding: 12px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.kv-grid div { min-width: 0; padding: 9px; background: #f8fafc; border-left: 3px solid #3b82f6; }
.kv-grid span { display: block; color: #64748b; font-size: 12px; }
.kv-grid strong { display: block; margin-top: 3px; color: #0f172a; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chip-grid, .point-grid, .binding-grid { padding: 12px; display: grid; gap: 8px; }
.chip-grid { grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); }
.template-chip { padding: 8px 10px; border: 1px solid #bfdbfe; border-radius: 5px; background: #eff6ff; color: #1d4ed8; font-size: 13px; font-weight: 600; }
.point-grid { grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); }
.point-card, .binding-card { padding: 10px 12px; border: 1px solid #dbe4ef; border-radius: 5px; background: #f8fafc; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.point-card strong, .binding-card strong { display: block; color: #0f172a; font-size: 14px; }
.point-card span, .binding-card span { display: block; margin-top: 3px; color: #64748b; font-size: 12px; }
.point-card em, .binding-card em { color: #0f766e; font-style: normal; font-size: 12px; background: #e7f8f1; padding: 3px 6px; border-radius: 4px; }
.binding-grid { grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
.empty-inline { color: #94a3b8; font-size: 13px; }
.json-panel { height: 100%; display: flex; flex-direction: column; }
pre { flex: 1; margin: 0; padding: 12px; background: #101827; color: #d1d5db; font-size: 12px; line-height: 1.55; overflow: auto; }
@media (max-width: 1100px) { .workspace, .summary-row, .runtime-grid { grid-template-columns: 1fr; } .workspace { overflow: auto; } .left-list { min-height: 260px; } }
</style>
