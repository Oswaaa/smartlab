<template>
  <div class="adapter-page">
    <header class="page-head">
      <div>
        <h1>设备执行代理</h1>
        <p>聚合展示设备实例的代理运行配置及 Adapter 北向契约</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
        <!-- Removed "Save Config" because this page is read-only for monitoring adapter contracts -->
      </div>
    </header>

    <main class="workspace">
      <aside class="left-list">
        <div class="panel-title">
          <span>设备实例</span>
          <el-input v-model="keyword" placeholder="搜索实例名称" size="small" clearable />
        </div>

        <div class="model-list" v-loading="loading">
          <el-empty v-if="filteredInstances.length === 0" description="无匹配设备实例" />
          <button
            v-for="item in filteredInstances"
            :key="item.instanceId"
            class="model-item"
            :class="{ active: activeKey === item.instanceId }"
            @click="activeKey = item.instanceId"
          >
            <span class="name">{{ item.instanceName }}</span>
            <span class="meta">{{ getModelName(item.modelId) }}</span>
            <span class="status" :class="item.status">{{ item.status }}</span>
          </button>
        </div>
      </aside>

      <section class="detail-area" v-if="activeRow">
        <div class="summary-row">
          <div class="summary-cell">
            <span class="label">设备模型</span>
            <strong>{{ getModelName(activeRow.modelId) }}</strong>
          </div>
          <div class="summary-cell">
            <span class="label">设备实例</span>
            <strong>{{ activeRow.instanceName }}</strong>
          </div>
          <div class="summary-cell">
            <span class="label">在线状态</span>
            <el-tag :type="activeRow.status === 'ONLINE' ? 'success' : 'info'" effect="plain">
              {{ activeRow.status }}
            </el-tag>
          </div>
          <div class="summary-cell">
            <span class="label">绑定通道 (Adapter)</span>
            <strong>{{ activeRow.boundAdapterName || '未绑定' }} / {{ activeRow.boundDevicePoint || '-' }}</strong>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="adapter-tabs">
          <el-tab-pane label="北向契约 (Adapter Binding)" name="contract">
            <div class="two-column">
              <section class="panel">
                <div class="panel-header">
                  <h2>系统与 Adapter 的通信契约</h2>
                  <el-tag>实例级</el-tag>
                </div>
                <el-form label-position="top" class="form-grid">
                  <el-form-item label="通信方式">
                    <el-select :model-value="adapterBinding?.protocol || 'N/A'" disabled>
                      <el-option :label="adapterBinding?.protocol || 'N/A'" :value="adapterBinding?.protocol || 'N/A'" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="契约版本">
                    <el-input :model-value="adapterBinding?.version || 'N/A'" readonly />
                  </el-form-item>
                  <el-form-item label="命令主题 (Command)">
                    <el-input :model-value="adapterBinding?.topics?.command || 'N/A'" readonly />
                  </el-form-item>
                  <el-form-item label="状态主题 (Status)">
                    <el-input :model-value="adapterBinding?.topics?.status || 'N/A'" readonly />
                  </el-form-item>
                  <el-form-item label="遥测主题 (Telemetry)">
                    <el-input :model-value="adapterBinding?.topics?.telemetry || 'N/A'" readonly />
                  </el-form-item>
                  <el-form-item label="配置快照">
                    <el-input :model-value="adapterBinding?.topics?.config || 'N/A'" readonly />
                  </el-form-item>
                </el-form>
              </section>

              <section class="panel">
                <div class="panel-header">
                  <h2>命令与遥测映射摘要</h2>
                </div>
                <el-table :data="contractCommands" size="small" border style="width: 100%; height: calc(100% - 44px); overflow-y: auto;">
                  <el-table-column prop="capability" label="能力引用" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="command" label="命令 ID" min-width="120" show-overflow-tooltip />
                  <el-table-column prop="direction" label="方向" width="90" />
                  <el-table-column prop="payload" label="描述/载荷" min-width="160" show-overflow-tooltip />
                </el-table>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="JSON 视图 (Instance Config)" name="json">
            <div class="json-grid">
              <section class="panel" style="grid-column: span 2;">
                <div class="panel-header"><h2>INSTANCE_CONFIG</h2></div>
                <pre>{{ instanceConfigPreview }}</pre>
              </section>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
      
      <section class="detail-area" v-else style="align-items: center; justify-content: center;">
        <el-empty description="请选择左侧设备实例" />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import axios from 'axios'

const keyword = ref('')
const activeTab = ref('contract')
const activeKey = ref('')

const loading = ref(false)
const instances = ref([])
const models = ref({})
const twinStates = ref({})

const fetchData = async () => {
  loading.value = true
  try {
    const [instRes, modelRes, twinRes] = await Promise.all([
      axios.get('/api/device/instance/list'),
      axios.get('/api/device/model/list'),
      axios.get('/api/device/instance/snapshots')
    ])
    
    if (modelRes.data?.success) {
      const modelMap = {}
      modelRes.data.data.forEach(m => {
        modelMap[m.modelId] = m.modelName
      })
      models.value = modelMap
    }

    if (twinRes.data?.success) {
      const twinMap = {}
      twinRes.data.data.forEach(t => {
        twinMap[t.instanceId] = t.onlineStatus || 'UNKNOWN'
      })
      twinStates.value = twinMap
    }

    if (instRes.data?.success) {
      instances.value = instRes.data.data.map(inst => ({
        ...inst,
        status: twinStates.value[inst.instanceId] || 'UNKNOWN'
      }))
      if (instances.value.length > 0 && !activeKey.value) {
        activeKey.value = instances.value[0].instanceId
      }
    }
  } catch (err) {
    console.error('Failed to fetch adapter page data', err)
  } finally {
    loading.value = false
  }
}

const filteredInstances = computed(() => {
  if (!keyword.value) return instances.value
  const kw = keyword.value.toLowerCase()
  return instances.value.filter(inst => 
    inst.instanceName?.toLowerCase().includes(kw) || 
    getModelName(inst.modelId).toLowerCase().includes(kw)
  )
})

const activeRow = computed(() => {
  return instances.value.find(item => item.instanceId === activeKey.value) || null
})

const getModelName = (modelId) => {
  return models.value[modelId] || modelId
}

const adapterBinding = computed(() => {
  if (!activeRow.value || !activeRow.value.instanceConfig) return null
  return activeRow.value.instanceConfig.adapterBinding || null
})

const contractCommands = computed(() => {
  const binding = adapterBinding.value
  if (!binding) return []
  const commands = []
  
  if (binding.services) {
    Object.keys(binding.services).forEach(key => {
      commands.push({
        capability: `Service: ${key}`,
        command: binding.services[key]?.topic || key,
        direction: '系统发布 (SYS -> ADAPTER)',
        payload: '根据物模型 capabilities 定义'
      })
    })
  }
  
  if (binding.properties) {
    commands.push({
      capability: 'Properties Telemetry',
      command: binding.topics?.telemetry || 'telemetry',
      direction: '系统订阅 (ADAPTER -> SYS)',
      payload: `包含属性: ${Object.keys(binding.properties).join(', ')}`
    })
  }

  return commands
})

const instanceConfigPreview = computed(() => {
  if (!activeRow.value) return '{}'
  return JSON.stringify(activeRow.value.instanceConfig || {}, null, 2)
})

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.adapter-page {
  height: calc(100vh - 52px);
  background: #f3f5f8;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-head {
  height: 72px;
  padding: 12px 18px;
  background: #fff;
  border-bottom: 1px solid #dfe4ec;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-head h1 {
  margin: 0;
  font-size: 18px;
  color: #172033;
}

.page-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7280;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 292px 1fr;
  gap: 12px;
  padding: 12px;
}

.left-list,
.detail-area,
.panel,
.summary-row {
  background: #fff;
  border: 1px solid #dfe4ec;
  border-radius: 8px;
}

.left-list {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-title {
  padding: 12px;
  border-bottom: 1px solid #edf0f5;
  display: grid;
  gap: 8px;
  font-weight: 700;
  color: #1f2937;
}

.model-list {
  padding: 8px;
  overflow: auto;
  flex: 1;
}

.model-item {
  width: 100%;
  border: 1px solid #e2e7ef;
  background: #fff;
  border-radius: 7px;
  padding: 10px;
  text-align: left;
  margin-bottom: 8px;
  cursor: pointer;
  display: grid;
  gap: 4px;
}

.model-item.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.name {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
}

.meta {
  font-size: 12px;
  color: #6b7280;
}

.status {
  width: fit-content;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #edf0f5;
  color: #6b7280;
}

.status.ONLINE {
  background: #dcfce7;
  color: #15803d;
}
.status.OFFLINE {
  background: #fef2f2;
  color: #dc2626;
}

.detail-area {
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.summary-row {
  margin: 12px;
  padding: 12px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-cell {
  display: grid;
  gap: 4px;
}

.label {
  font-size: 12px;
  color: #6b7280;
}

.summary-cell strong {
  font-size: 13px;
  color: #111827;
}

.adapter-tabs {
  flex: 1;
  min-height: 0;
  padding: 0 12px 12px;
  overflow: hidden;
}

.adapter-tabs :deep(.el-tabs__content) {
  height: calc(100% - 48px);
  overflow: auto;
}

.two-column,
.json-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  height: 100%;
}

.panel {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-header {
  height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-header h2 {
  margin: 0;
  font-size: 14px;
  color: #172033;
}

.form-grid {
  padding: 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 12px;
  overflow: auto;
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

pre {
  margin: 0;
  padding: 12px;
  min-height: 420px;
  background: #111827;
  color: #d1d5db;
  font-size: 12px;
  line-height: 1.55;
  overflow: auto;
  flex: 1;
}

@media (max-width: 1100px) {
  .workspace,
  .two-column,
  .json-grid,
  .summary-row {
    grid-template-columns: 1fr;
  }

  .workspace {
    overflow: auto;
  }

  .left-list {
    min-height: 260px;
  }
}
</style>
