<template>
  <div class="adapter-page">
    <header class="page-head">
      <div>
        <h1>设备执行代理</h1>
        <p>聚合展示设备模型的 Adapter 北向契约与设备实例的代理运行配置</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh">刷新</el-button>
        <el-button type="primary" :icon="EditPen">保存配置</el-button>
      </div>
    </header>

    <main class="workspace">
      <aside class="left-list">
        <div class="panel-title">
          <span>设备模型</span>
          <el-input v-model="keyword" placeholder="搜索模型/实例" size="small" clearable />
        </div>

        <div class="model-list">
          <button
            v-for="item in adapterRows"
            :key="`${item.modelId}-${item.instanceId}`"
            class="model-item"
            :class="{ active: activeKey === `${item.modelId}-${item.instanceId}` }"
            @click="activeKey = `${item.modelId}-${item.instanceId}`"
          >
            <span class="name">{{ item.instanceName }}</span>
            <span class="meta">{{ item.modelName }}</span>
            <span class="status" :class="item.status">{{ item.status }}</span>
          </button>
        </div>
      </aside>

      <section class="detail-area">
        <div class="summary-row">
          <div class="summary-cell">
            <span class="label">设备模型</span>
            <strong>{{ activeRow.modelName }}</strong>
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
            <span class="label">配置来源</span>
            <strong>DEVICE_MODELS / DEVICE_INSTANCES</strong>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="adapter-tabs">
          <el-tab-pane label="北向契约" name="contract">
            <div class="two-column">
              <section class="panel">
                <div class="panel-header">
                  <h2>系统与 Adapter 的通信契约</h2>
                  <el-tag>模型级</el-tag>
                </div>
                <el-form label-position="top" class="form-grid">
                  <el-form-item label="通信方式">
                    <el-select model-value="MQTT" disabled>
                      <el-option label="MQTT" value="MQTT" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="契约版本">
                    <el-input model-value="v1" />
                  </el-form-item>
                  <el-form-item label="命令主题模板">
                    <el-input model-value="smartlab/v1/{lab}/{model}/{instance}/cmd" />
                  </el-form-item>
                  <el-form-item label="状态主题模板">
                    <el-input model-value="smartlab/v1/{lab}/{model}/{instance}/status" />
                  </el-form-item>
                </el-form>
              </section>

              <section class="panel">
                <div class="panel-header">
                  <h2>命令与遥测映射</h2>
                  <el-button text type="primary" :icon="Plus">新增命令</el-button>
                </div>
                <el-table :data="contractCommands" size="small" border>
                  <el-table-column prop="capability" label="能力引用" min-width="130" />
                  <el-table-column prop="command" label="命令 ID" min-width="120" />
                  <el-table-column prop="direction" label="方向" width="90" />
                  <el-table-column prop="payload" label="载荷模板" min-width="160" />
                </el-table>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="实例配置" name="instance">
            <div class="two-column">
              <section class="panel">
                <div class="panel-header">
                  <h2>实例级连接参数</h2>
                  <el-tag type="warning">实例级</el-tag>
                </div>
                <el-form label-position="top" class="form-grid">
                  <el-form-item label="Adapter 标识">
                    <el-input model-value="reactor-01-adapter" />
                  </el-form-item>
                  <el-form-item label="部署位置">
                    <el-input model-value="实验室 A / 工控机 01" />
                  </el-form-item>
                  <el-form-item label="南向协议">
                    <el-select model-value="MODBUS_TCP">
                      <el-option label="Modbus TCP" value="MODBUS_TCP" />
                      <el-option label="HTTP 服务程序" value="HTTP" />
                      <el-option label="TCP 服务程序" value="TCP" />
                      <el-option label="串口 / RS-485" value="SERIAL_485" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="服务地址">
                    <el-input model-value="192.168.1.20:502" />
                  </el-form-item>
                </el-form>
              </section>

              <section class="panel">
                <div class="panel-header">
                  <h2>点位/动作配置摘要</h2>
                  <el-button text type="primary" :icon="Plus">新增点位</el-button>
                </div>
                <el-table :data="pointRows" size="small" border>
                  <el-table-column prop="attr" label="设备属性" min-width="120" />
                  <el-table-column prop="address" label="点位/地址" min-width="120" />
                  <el-table-column prop="rw" label="读写" width="80" />
                  <el-table-column prop="transform" label="换算" min-width="120" />
                </el-table>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="JSON 视图" name="json">
            <div class="json-grid">
              <section class="panel">
                <div class="panel-header"><h2>ADAPTER_CONTRACT</h2></div>
                <pre>{{ adapterContractPreview }}</pre>
              </section>
              <section class="panel">
                <div class="panel-header"><h2>INSTANCE_CONFIG</h2></div>
                <pre>{{ instanceConfigPreview }}</pre>
              </section>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { EditPen, Plus, Refresh } from '@element-plus/icons-vue'

const keyword = ref('')
const activeTab = ref('contract')
const activeKey = ref('1-1')

const adapterRows = [
  { modelId: 1, instanceId: 1, modelName: '反应釜模型', instanceName: '1号反应釜', status: 'ONLINE' },
  { modelId: 2, instanceId: 2, modelName: '三轴运动平台模型', instanceName: '三轴平台 A', status: 'OFFLINE' },
  { modelId: 3, instanceId: 3, modelName: '温控模块模型', instanceName: '温控模块 01', status: 'UNKNOWN' }
]

const activeRow = computed(() => {
  return adapterRows.find(item => `${item.modelId}-${item.instanceId}` === activeKey.value) || adapterRows[0]
})

const contractCommands = [
  { capability: 'reactor.setTemperature', command: 'set_temperature', direction: '系统发布', payload: '{ target: number }' },
  { capability: 'reactor.startStir', command: 'start_stir', direction: '系统发布', payload: '{ rpm: number }' },
  { capability: 'reactor.telemetry', command: 'telemetry', direction: '系统订阅', payload: '{ temp, pressure }' }
]

const pointRows = [
  { attr: 'temperature', address: 'holding_register:40001', rw: 'R', transform: 'raw * 0.1' },
  { attr: 'targetTemperature', address: 'holding_register:40011', rw: 'RW', transform: 'value / 0.1' },
  { attr: 'pressure', address: 'holding_register:40002', rw: 'R', transform: 'raw * 0.01' }
]

const adapterContractPreview = computed(() => JSON.stringify({
  protocol: 'MQTT',
  topics: {
    command: 'smartlab/v1/{lab}/{model}/{instance}/cmd',
    status: 'smartlab/v1/{lab}/{model}/{instance}/status',
    telemetry: 'smartlab/v1/{lab}/{model}/{instance}/telemetry'
  },
  commands: contractCommands
}, null, 2))

const instanceConfigPreview = computed(() => JSON.stringify({
  adapterId: `${activeRow.value.instanceName}-adapter`,
  southbound: {
    protocol: 'MODBUS_TCP',
    endpoint: '192.168.1.20:502'
  },
  points: pointRows
}, null, 2))
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
}

.panel {
  overflow: hidden;
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
