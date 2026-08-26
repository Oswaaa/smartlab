<template>
  <div class="tab-pane-content">
    <section class="runtime-block">
      <div class="runtime-block-head">
        <div>
          <span class="sec-title-text">实时属性遥测</span>
          <span class="sec-desc-text">设备各项物理传感器实时采样指标</span>
        </div>
        <div class="card-head-meta">
          <span class="pulse-dot" :class="{ retired: retired }"></span>
          <span class="mono last-time">快照采样: {{ lastSnapshotTime }}</span>
        </div>
      </div>
      <div v-if="kpiAttributes.length" class="runtime-list">
        <div v-for="kpi in kpiAttributes" :key="kpi.key" class="runtime-row">
          <span class="runtime-label">{{ kpi.label }}</span>
          <span class="runtime-value">
            <span class="mono">{{ kpi.value ?? 'N/A' }}</span>
            <span v-if="kpi.unit && kpi.value != null" class="runtime-unit">{{ kpi.unit }}</span>
          </span>
        </div>
      </div>
      <div v-else class="compact-empty">暂无遥测属性数据</div>
    </section>

    <section class="runtime-block">
      <div class="runtime-block-head">
        <span class="sec-title-text">状态机监控</span>
      </div>
      <div class="runtime-list">
        <div class="runtime-row">
          <span class="runtime-label">设备指令执行周期</span>
          <span class="runtime-value">
            <span class="cmd-dot" :class="cmdStateClass(snapshot?.currentCommandState)"></span>
            <span class="mono">{{ snapshot?.currentCommandState || 'IDLE' }}</span>
            <span class="runtime-unit">{{ cmdStateDescription(snapshot?.currentCommandState) }}</span>
          </span>
        </div>

        <div class="runtime-row">
          <span class="runtime-label">功能区域</span>
          <div class="runtime-value op-regions-container">
            <template v-if="operationalRegions.length">
              <div
                v-for="region in operationalRegions"
                :key="region.regionName"
                class="region-item"
              >
                <span class="region-label">{{ region.regionName }}:</span>
                <span :class="['region-state', region.states.length ? 'has-active' : 'is-idle']">
                  {{ region.states.length ? region.states.join(', ') : 'IDLE' }}
                </span>
              </div>
            </template>
            <span v-else class="muted">设备处于正常运行就绪态</span>
          </div>
        </div>

        <div class="runtime-row">
          <span class="runtime-label">异常区域</span>
          <div class="runtime-value exception-container">
            <template v-if="activeExceptionStates.length">
              <div
                v-for="item in activeExceptionStates"
                :key="item.regionName + item.state"
                class="exception-item"
              >
                <span class="exception-label">{{ item.regionName }}:</span>
                <span class="exception-state">{{ item.state }}</span>
                <el-button
                  v-if="canControl"
                  size="small"
                  type="danger"
                  plain
                  class="clear-exception-btn"
                  :loading="clearingException === item.state"
                  @click="emit('clear-exception', item.state)"
                >
                  解除异常
                </el-button>
              </div>
            </template>
            <span v-else class="success-text">
              <el-icon class="safe-check-icon"><CircleCheck /></el-icon>
              无异常
            </span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck } from '@element-plus/icons-vue'
import { asArray, cmdStateClass, cmdStateDescription, findModelById, getModelAttributes } from './normalizers'
import type { DeviceInstance, DeviceModel, DeviceSnapshot } from './types'

const props = defineProps<{
  instance: DeviceInstance
  snapshot: DeviceSnapshot | null
  models: DeviceModel[]
  modelOptions: DeviceModel[]
  lastSnapshotTime: string
  retired: boolean
  canControl: boolean
  clearingException: string
}>()

const emit = defineEmits<{
  'clear-exception': [state: string]
}>()

const snapshotAttributes = computed(() => props.snapshot?.latestAttributes || {})
const activeInstanceModel = computed(() => findModelById(props.instance.modelId, props.models, props.modelOptions))

const kpiAttributes = computed(() => {
  const attrs = asArray(getModelAttributes(props.instance.modelId, props.models, props.modelOptions))
  const snapshotMap = snapshotAttributes.value
  const used = new Set<string>()
  const mapped = attrs.map((attr: any) => {
    const key = String(attr.attributeName || '')
    if (key) used.add(key)
    return {
      key: key || '-',
      label: attr.displayName || attr.attributeName || key || '-',
      dataType: attr.dataType || '-',
      unit: attr.unit || '',
      value: key && Object.prototype.hasOwnProperty.call(snapshotMap, key) ? snapshotMap[key] : undefined
    }
  })
  Object.keys(snapshotMap).forEach(key => {
    if (!used.has(key)) {
      mapped.push({
        key,
        label: attrs.find((v: any) => String(v.attributeName) === key)?.displayName || key,
        dataType: '-',
        unit: '',
        value: snapshotMap[key]
      })
    }
  })
  return mapped
})

const operationStateRegions = computed(() => {
  const current = props.snapshot?.currentOperationState
  if (!current || typeof current !== 'object' || Array.isArray(current)) return []
  const configured = asArray(activeInstanceModel.value?.opState?.regions)
  return Object.entries(current).map(([regionName, value]) => ({
    regionName,
    regionType: configured.find((region: any) => region.regionName === regionName)?.regionType || 'OPERATIONAL',
    states: Array.isArray(value) ? value.map(String).filter(Boolean) : []
  }))
})
const operationalRegions = computed(() => operationStateRegions.value.filter(r => r.regionType !== 'EXCEPTION'))
const activeExceptionStates = computed(() => {
  const list: Array<{ regionName: string, state: string }> = []
  operationStateRegions.value
    .filter(r => r.regionType === 'EXCEPTION')
    .forEach(r => r.states.forEach(s => list.push({ regionName: r.regionName, state: s })))
  return list
})
</script>

<style scoped>
.tab-pane-content { display: flex; flex-direction: column; gap: 12px; }
.runtime-block {
  background: #ffffff;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  overflow: hidden;
}
.runtime-block-head {
  padding: 8px 12px;
  background: #fafbfc;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.runtime-block-head > div { display: flex; align-items: baseline; gap: 8px; }
.sec-title-text { font-size: 13px; font-weight: 700; color: var(--sl-text-heading, #0f172a); }
.sec-desc-text { font-size: 11.5px; color: var(--sl-text-secondary, #64748b); }
.card-head-meta { display: flex; align-items: center; gap: 6px; }
.pulse-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--sl-success, #16a34a);
  box-shadow: 0 0 6px rgba(22, 163, 74, 0.6);
  animation: blink 1.6s infinite alternate;
}
.pulse-dot.retired { background: var(--sl-text-disabled, #94a3b8); animation: none; box-shadow: none; }
.last-time { color: var(--sl-text-secondary, #64748b); font-size: 11px; }
.runtime-list { display: flex; flex-direction: column; }
.runtime-row {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  min-height: 36px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}
.runtime-row:last-child { border-bottom: none; }
.runtime-label { font-size: 12.5px; color: var(--sl-text-secondary, #64748b); flex-shrink: 0; }
.runtime-value {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}
.runtime-unit { font-size: 12px; font-weight: 400; color: var(--sl-text-secondary, #64748b); margin-left: 2px; }
.cmd-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.cmd-dot.idle { background: #64748b; }
.cmd-dot.running { background: #0284c7; box-shadow: 0 0 6px rgba(2, 132, 199, 0.6); animation: blink 1.6s infinite alternate; }
.cmd-dot.completed { background: var(--sl-success, #16a34a); }
.cmd-dot.error { background: #ef4444; }

/* 功能区域列表（无边框胶囊，紧凑排布） */
.op-regions-container {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
}
.region-item {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  font-size: 12.5px;
}
.region-label {
  font-size: 12px;
  color: var(--sl-text-secondary, #64748b);
  font-weight: 500;
}
.region-state {
  color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-mono, monospace);
  font-weight: 600;
}
.region-state.is-idle {
  color: var(--sl-text-secondary, #64748b);
  font-weight: 500;
}
.region-state.has-active {
  color: var(--sl-success, #16a34a);
  font-weight: 700;
}

/* 异常区域 */
.exception-container {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.exception-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.exception-label {
  font-size: 12px;
  color: var(--sl-danger, #dc2626);
  font-weight: 500;
}
.exception-state {
  color: var(--sl-danger, #dc2626);
  font-family: var(--sl-font-mono, monospace);
  font-weight: 700;
  font-size: 12px;
}
.clear-exception-btn { font-size: 11.5px; height: 22px; padding: 0 8px; }
.success-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--sl-success, #16a34a);
  font-size: 12px;
  font-weight: 500;
}
.safe-check-icon { font-size: 14px; color: var(--sl-success, #16a34a); }
.mono { font-family: var(--sl-font-mono, monospace); }
.muted { color: var(--sl-text-disabled, #94a3b8); font-size: 12px; font-weight: 400; }
.compact-empty { font-size: 12px; color: var(--sl-text-disabled, #94a3b8); padding: 12px; }
@keyframes blink { 0% { opacity: 0.35; } 100% { opacity: 1; } }
</style>
