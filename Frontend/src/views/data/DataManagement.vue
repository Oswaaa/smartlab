<template>
  <div class="data-point-page">
    <header class="page-head">
      <div>
        <h1>数据点管理</h1>
        <p>按照设备实例查看 DATA_INDEX 数据集与 DATA_RECORD_XXXX 物理记录表</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh">刷新</el-button>
        <el-button type="primary" :icon="Download">导出</el-button>
      </div>
    </header>

    <main class="workspace">
      <aside class="tree-panel">
        <div class="panel-title">设备数据树</div>
        <el-tree
          :data="treeData"
          node-key="id"
          default-expand-all
          :props="{ label: 'label', children: 'children' }"
          class="data-tree"
        />
      </aside>

      <section class="content-panel">
        <div class="dataset-summary">
          <div>
            <span class="label">当前数据集</span>
            <strong>1号反应釜基础监控数据</strong>
          </div>
          <div>
            <span class="label">物理表</span>
            <strong>DATA_RECORD_0001</strong>
          </div>
          <div>
            <span class="label">绑定设备</span>
            <strong>1号反应釜</strong>
          </div>
          <div>
            <span class="label">采集状态</span>
            <el-tag type="success" effect="plain">采集中</el-tag>
          </div>
        </div>

        <section class="chart-panel">
          <div class="panel-header">
            <h2>趋势预览</h2>
            <div class="filters">
              <el-date-picker type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" />
              <el-select model-value="temperature" placeholder="字段">
                <el-option label="温度" value="temperature" />
                <el-option label="压力" value="pressure" />
              </el-select>
            </div>
          </div>
          <div class="chart-placeholder">
            <div class="axis"></div>
            <div class="line line-a"></div>
            <div class="line line-b"></div>
          </div>
        </section>

        <section class="table-panel">
          <div class="panel-header">
            <h2>数据记录</h2>
            <el-button text type="primary">新建自定义数据表</el-button>
          </div>
          <el-table :data="rows" border size="small" height="100%">
            <el-table-column prop="time" label="采集时间" min-width="170" />
            <el-table-column prop="temperature" label="温度 (℃)" min-width="110" />
            <el-table-column prop="pressure" label="压力 (MPa)" min-width="120" />
            <el-table-column prop="status" label="状态" min-width="100" />
          </el-table>
        </section>
      </section>
    </main>
  </div>
</template>

<script setup>
import { Download, Refresh } from '@element-plus/icons-vue'

const treeData = [
  {
    id: 'cat-reactor',
    label: '反应釜',
    children: [
      {
        id: 'reactor-1',
        label: '1号反应釜',
        children: [
          { id: 'data-1', label: '基础监控数据' },
          { id: 'data-2', label: '高压实验数据' }
        ]
      },
      {
        id: 'reactor-2',
        label: '2号反应釜',
        children: [{ id: 'data-3', label: '基础监控数据' }]
      }
    ]
  },
  {
    id: 'cat-motion',
    label: '运动平台',
    children: [{ id: 'stage-a', label: '三轴平台 A', children: [{ id: 'data-4', label: '位置反馈数据' }] }]
  }
]

const rows = [
  { time: '2026-06-11 09:00:00', temperature: '81.2', pressure: '1.21', status: '正常' },
  { time: '2026-06-11 09:00:05', temperature: '81.5', pressure: '1.22', status: '正常' },
  { time: '2026-06-11 09:00:10', temperature: '82.0', pressure: '1.22', status: '正常' },
  { time: '2026-06-11 09:00:15', temperature: '82.4', pressure: '1.23', status: '正常' }
]
</script>

<style scoped>
.data-point-page {
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

.head-actions,
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workspace {
  flex: 1;
  min-height: 0;
  padding: 12px;
  display: grid;
  grid-template-columns: 292px 1fr;
  gap: 12px;
}

.tree-panel,
.content-panel,
.chart-panel,
.table-panel,
.dataset-summary {
  background: #fff;
  border: 1px solid #dfe4ec;
  border-radius: 8px;
}

.tree-panel {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-title,
.panel-header {
  height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
  color: #172033;
}

.data-tree {
  padding: 8px;
  overflow: auto;
}

.content-panel {
  min-width: 0;
  padding: 12px;
  display: grid;
  grid-template-rows: auto 260px 1fr;
  gap: 12px;
  overflow: hidden;
}

.dataset-summary {
  padding: 12px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.dataset-summary strong {
  font-size: 13px;
  color: #111827;
}

.chart-panel,
.table-panel {
  min-height: 0;
  overflow: hidden;
}

.panel-header h2 {
  margin: 0;
  font-size: 14px;
}

.chart-placeholder {
  position: relative;
  height: calc(100% - 45px);
  margin: 0 12px 12px;
  border: 1px solid #edf0f5;
  border-radius: 6px;
  background:
    linear-gradient(#eef2f7 1px, transparent 1px) 0 0 / 100% 48px,
    linear-gradient(90deg, #eef2f7 1px, transparent 1px) 0 0 / 80px 100%,
    #fff;
}

.axis {
  position: absolute;
  left: 44px;
  right: 20px;
  bottom: 34px;
  height: 1px;
  background: #9ca3af;
}

.line {
  position: absolute;
  left: 52px;
  right: 28px;
  height: 3px;
  border-radius: 999px;
  transform-origin: left center;
}

.line-a {
  top: 92px;
  background: #2563eb;
  transform: rotate(-4deg);
}

.line-b {
  top: 145px;
  background: #16a34a;
  transform: rotate(3deg);
}

.table-panel {
  display: flex;
  flex-direction: column;
}

.table-panel :deep(.el-table) {
  flex: 1;
}

@media (max-width: 1100px) {
  .workspace,
  .dataset-summary {
    grid-template-columns: 1fr;
  }

  .workspace {
    overflow: auto;
  }

  .tree-panel {
    min-height: 260px;
  }
}
</style>
