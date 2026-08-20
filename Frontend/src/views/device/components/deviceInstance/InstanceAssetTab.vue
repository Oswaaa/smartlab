<template>
  <div class="tab-pane-content">
    <div class="sub-section-head">
      <span class="head-title">物理资产与通信部署参数</span>
      <span class="head-desc">维护硬件唯一编码、安装位置、运维日期与 MQTT 通信主题映射</span>
    </div>
    <el-form label-position="top" size="small" class="instance-info-grid" :disabled="retired">
      <el-form-item label="设备实例名称" required>
        <el-input :model-value="instance.instanceName" @update:model-value="patch({ instanceName: $event })" />
      </el-form-item>
      <el-form-item label="绑定物模型">
        <el-select :model-value="instance.modelId" style="width: 100%" :disabled="!!instance.instanceId" @change="onModelChange">
          <el-option v-for="m in models" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
        </el-select>
      </el-form-item>
      <el-form-item label="物理 Adapter 代理" required>
        <el-select :model-value="instance.boundAdapterName" style="width: 100%" filterable @change="onAdapterChange">
          <el-option v-for="adapter in adapterOptions" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
        </el-select>
      </el-form-item>
      <el-form-item label="Adapter 设备点位" required>
        <el-select :model-value="instance.boundDevicePoint" style="width: 100%" filterable :disabled="!instance.boundAdapterName" @update:model-value="patch({ boundDevicePoint: $event })">
          <el-option v-for="point in devicePoints" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
        </el-select>
      </el-form-item>
      <el-form-item label="出厂序列号 (SN)">
        <el-input :model-value="asset.serialNumber" placeholder="出厂硬件 SN 唯一标识" @update:model-value="patchAsset({ serialNumber: $event })" />
      </el-form-item>
      <el-form-item label="部署实验室/具体位置">
        <el-input :model-value="asset.location" placeholder="楼宇-房间-槽位" @update:model-value="patchAsset({ location: $event })" />
      </el-form-item>
      <el-form-item label="采购日期">
        <el-date-picker :model-value="asset.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @update:model-value="patchAsset({ purchaseDate: $event })" />
      </el-form-item>
      <el-form-item label="安装日期">
        <el-date-picker :model-value="asset.instalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @update:model-value="patchAsset({ instalDate: $event })" />
      </el-form-item>
      <el-form-item label="资产运维备注" class="form-wide">
        <el-input :model-value="asset.notes" type="textarea" :rows="2" placeholder="填写设备运维、厂商支持或维保备注..." @update:model-value="patchAsset({ notes: $event })" />
      </el-form-item>
    </el-form>

    <div class="sub-section-block mt-16">
      <div class="sub-section-head">
        <span class="head-title">MQTT 订阅/发布物理通信主题</span>
        <span class="head-desc">基于 Adapter 与点位自动推导的物理通道主题</span>
      </div>
      <el-table :data="topicRows" stripe border size="small" class="topic-table">
        <el-table-column label="通信用途" width="130" prop="label" />
        <el-table-column label="数据流向" width="160" prop="direction" />
        <el-table-column label="MQTT 物理主题">
          <template #default="{ row }"><code class="mono">{{ row.topic || '-' }}</code></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { devicePointLabel, emptyAssetInfo, mqttTopicRows } from './normalizers'
import type { AdapterDevicePoint, AdapterOption, DeviceInstance, DeviceModel } from './types'

const props = defineProps<{
  instance: DeviceInstance
  models: DeviceModel[]
  adapterOptions: AdapterOption[]
  devicePoints: AdapterDevicePoint[]
  retired: boolean
}>()

const emit = defineEmits<{
  'update:instance': [value: DeviceInstance]
  'adapter-change': [adapterName: string]
  'model-change': [modelId: string]
}>()

const asset = computed(() => props.instance.instanceConfig?.assetInfo || emptyAssetInfo())
const topicRows = computed(() => mqttTopicRows(props.instance.boundAdapterName, props.instance.boundDevicePoint))

const patch = (partial: Partial<DeviceInstance>) => {
  emit('update:instance', { ...props.instance, ...partial })
}

const patchAsset = (partial: Record<string, any>) => {
  emit('update:instance', {
    ...props.instance,
    instanceConfig: {
      ...(props.instance.instanceConfig || {}),
      assetInfo: { ...asset.value, ...partial }
    }
  })
}

const onModelChange = (modelId: string) => {
  patch({ modelId, boundDevicePoint: '' })
  emit('model-change', modelId)
}

const onAdapterChange = (adapterName: string) => {
  patch({ boundAdapterName: adapterName, boundDevicePoint: '' })
  emit('adapter-change', adapterName)
}
</script>

<style scoped>
.tab-pane-content { display: flex; flex-direction: column; gap: 12px; }
.sub-section-head {
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.sub-section-head .head-title { font-size: 12.5px; font-weight: 700; color: var(--sl-text-heading); }
.sub-section-head .head-desc { font-size: 11px; color: var(--sl-text-secondary); }
.sub-section-block {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  overflow: hidden;
}
.instance-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 12px;
  background: #ffffff;
  padding: 12px;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
}
.instance-info-grid :deep(.el-form-item) { margin-bottom: 0; }
.form-wide { grid-column: 1 / -1; }
.mt-16 { margin-top: 4px; }
.mono { font-family: var(--sl-font-mono); }
</style>
