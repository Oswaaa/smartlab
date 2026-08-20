<template>
  <div class="table-scroll-container">
    <div class="table-card" v-loading="loading">
      <el-table
        v-if="instances.length > 0"
        :data="instances"
        stripe
        size="small"
        class="instance-table"
        height="100%"
        row-key="instanceId"
        @row-click="row => emit('view-details', row)"
      >
        <el-table-column label="设备实例名称 / ID" min-width="90">
          <template #default="{ row }">
            <div class="instance-name-cell">
              <span class="inst-title">{{ row.instanceName }}</span>
              <span class="inst-id-sub mono">ID: {{ row.instanceId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="所属模型" min-width="80">
          <template #default="{ row }">
            <span class="model-name-text">{{ getModelName(row.modelId, models, modelOptions) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Adapter 代理 / 绑定点位" min-width="120">
          <template #default="{ row }">
            <div class="binding-cell">
              <div class="binding-item">
                <span class="binding-k">Adapter:</span>
                <span class="binding-v">{{ row.boundAdapterName || '未分配' }}</span>
              </div>
              <div class="binding-item">
                <span class="binding-k">点位:</span>
                <span class="binding-v">{{ row.boundDevicePoint || '未分配' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="MQTT 物理通信主题" min-width="250">
          <template #default="{ row }">
            <div class="topic-compact-cell">
              <div v-for="topic in mqttTopicRows(row.boundAdapterName, row.boundDevicePoint)" :key="topic.type" class="topic-item">
                <span class="topic-lbl">{{ topic.label }}:</span>
                <code class="topic-code" :title="topic.topic">{{ topic.topic || '-' }}</code>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="使用状态" width="95" align="center">
          <template #default="{ row }">
            <span v-if="row.lifecycleStatus === 'RETIRED'" class="status-indicator retired">
              <span class="dot"></span>已注销
            </span>
            <span v-else class="status-indicator in-use">
              <span class="dot"></span>使用中
            </span>
          </template>
        </el-table-column>
        <el-table-column label="通信状态" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.lifecycleStatus === 'RETIRED'" class="muted">-</span>
            <span v-else-if="row.isOnline" class="status-indicator online">
              <span class="dot"></span>在线
            </span>
            <span v-else class="status-indicator offline">
              <span class="dot"></span>离线
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" @click.stop>
              <button class="btn-link" type="button" @click="emit('view-details', row)">监控与配置</button>
              <button v-if="canDelete && row.lifecycleStatus !== 'RETIRED'" class="btn-link danger" type="button" @click="emit('retire', row)">注销</button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-wrap">
        <el-empty description="暂无匹配的设备实例" :image-size="100" />
      </div>
      <div class="pager-wrap">
        <el-pagination
          :current-page="pageNo"
          :page-size="pageSize"
          :page-sizes="[12, 24, 48, 96]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          size="small"
          @update:current-page="val => emit('update:pageNo', val)"
          @update:page-size="val => emit('update:pageSize', val)"
          @size-change="val => emit('page-size-change', val)"
          @current-change="val => emit('page-change', val)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getModelName, mqttTopicRows } from './normalizers'
import type { DeviceInstance, DeviceModel } from './types'

defineProps<{
  instances: DeviceInstance[]
  models: DeviceModel[]
  modelOptions: DeviceModel[]
  loading: boolean
  pageNo: number
  pageSize: number
  total: number
  canDelete: boolean
}>()

const emit = defineEmits<{
  'view-details': [row: DeviceInstance]
  retire: [row: DeviceInstance]
  'update:pageNo': [value: number]
  'update:pageSize': [value: number]
  'page-size-change': [value: number]
  'page-change': [page: number]
}>()
</script>

<style scoped>
.table-scroll-container {
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.table-card {
  flex: 1;
  min-height: 0;
  border: none;
  border-radius: 0;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: none;
}
.instance-table { flex: 1; width: 100%; }
.instance-table :deep(.el-table__row) { cursor: pointer; }
.instance-table :deep(.el-table__row:hover) { background: var(--sl-bg-hover) !important; }
.instance-name-cell { display: flex; flex-direction: column; gap: 2px; }
.inst-title { font-size: 13px; font-weight: 600; color: var(--sl-text-heading); }
.inst-id-sub { font-size: 11px; color: var(--sl-text-secondary); }
.model-name-text { font-size: 12.5px; color: var(--sl-text-body); font-weight: 500; }
.binding-cell { display: flex; flex-direction: column; gap: 3px; font-size: 11.5px; }
.binding-item { display: flex; align-items: center; gap: 6px; }
.binding-k { color: var(--sl-text-secondary); font-weight: 600; min-width: 52px; }
.binding-v { color: var(--sl-text-body); }
.topic-compact-cell { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.topic-item { display: flex; align-items: center; gap: 6px; font-size: 11px; min-width: 0; }
.topic-lbl { color: var(--sl-text-secondary); font-weight: 600; min-width: 36px; flex-shrink: 0; }
.topic-code { color: var(--sl-text-heading); font-family: var(--sl-font-mono); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
.status-indicator { display: inline-flex; align-items: center; gap: 5px; font-size: 11.5px; font-weight: 600; }
.status-indicator .dot { width: 6px; height: 6px; border-radius: 50%; }
.status-indicator.in-use { color: var(--sl-success); }
.status-indicator.in-use .dot { background: var(--sl-success); }
.status-indicator.retired { color: var(--sl-text-secondary); }
.status-indicator.retired .dot { background: var(--sl-text-disabled); }
.status-indicator.online { color: #0284c7; }
.status-indicator.online .dot { background: #0284c7; box-shadow: 0 0 6px rgba(2, 132, 199, 0.5); }
.status-indicator.offline { color: var(--sl-text-disabled); }
.status-indicator.offline .dot { background: var(--sl-text-disabled); }
.row-actions { display: flex; align-items: center; justify-content: center; gap: 8px; }
.pager-wrap {
  padding: 6px 12px;
  background: #ffffff;
  border-top: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}
.mono { font-family: var(--sl-font-mono); }
.muted { color: var(--sl-text-disabled); font-size: 12px; }
.empty-wrap { padding: 60px 0; }
</style>
