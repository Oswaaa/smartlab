<template>
  <div class="tab-pane-content">
    <div class="holistic-section-card telemetry-card">
      <div class="section-card-head">
        <div class="section-card-title">
          <span class="sec-title-text">实时属性遥测</span>
          <span class="sec-desc-text">设备各项物理传感器实时采样指标</span>
        </div>
        <div class="card-head-meta">
          <span class="pulse-dot" :class="{ retired: retired }"></span>
          <span class="mono last-time">快照采样: {{ lastSnapshotTime }}</span>
        </div>
      </div>
      <div class="section-card-body">
        <div class="telemetry-kpi-grid" v-if="kpiAttributes.length">
          <div v-for="kpi in kpiAttributes" :key="kpi.key" class="kpi-card">
            <div class="kpi-label">
              <span class="kpi-dot"></span>
              <span class="kpi-label-text">{{ kpi.label }}</span>
            </div>
            <div class="kpi-value-row">
              <span class="kpi-val mono">{{ kpi.value ?? 'N/A' }}</span>
              <span class="kpi-unit" v-if="kpi.unit && kpi.value != null">{{ kpi.unit }}</span>
            </div>
          </div>
        </div>
        <div v-else class="compact-empty block-empty">暂无遥测属性数据</div>
      </div>
    </div>

    <div class="holistic-section-card statemachine-card">
      <div class="section-card-head">
        <div class="section-card-title">
          <span class="sec-title-text">状态机监控</span>
        </div>
      </div>
      <div class="section-card-body statemachine-body-split">
        <div class="statemachine-subcard cmd-lifecycle-subcard">
          <div class="subcard-header">
            <span class="subcard-title">设备指令执行周期</span>
          </div>
          <div class="subcard-content cmd-content-body">
            <div class="cmd-state-main">
              <span class="cmd-dot" :class="cmdStateClass(snapshot?.currentCommandState)"></span>
              <span class="cmd-state-name mono">{{ snapshot?.currentCommandState || 'IDLE' }}</span>
            </div>
            <div class="cmd-state-desc">{{ cmdStateDescription(snapshot?.currentCommandState) }}</div>
          </div>
        </div>

        <div class="statemachine-subcard op-state-subcard">
          <div class="subcard-header">
            <span class="subcard-title">当前功能状态</span>
          </div>
          <div class="subcard-content op-regions-container">
            <div class="region-section-box operational-box">
              <div class="region-box-head">
                <span class="region-type-pill op-pill">功能区域</span>
              </div>
              <div class="region-tags-wrap" v-if="operationalRegions.length">
                <div v-for="region in operationalRegions" :key="region.regionName" class="region-row-item">
                  <span class="region-name-badge">{{ region.regionName }}</span>
                  <div class="region-state-tags">
                    <el-tag v-for="state in region.states" :key="region.regionName + state" size="small" type="success" effect="plain" class="state-value-tag">
                      {{ state }}
                    </el-tag>
                    <span v-if="region.states.length === 0" class="muted">就绪态 (IDLE)</span>
                  </div>
                </div>
              </div>
              <div v-else class="empty-region-hint">设备处于正常运行就绪态</div>
            </div>

            <div class="region-section-box exception-box">
              <div class="region-box-head">
                <span class="region-type-pill err-pill">异常区域</span>
              </div>
              <div class="region-tags-wrap" v-if="activeExceptionStates.length">
                <div v-for="item in activeExceptionStates" :key="item.regionName + item.state" class="exception-row-item">
                  <el-tag size="small" type="danger" effect="plain" class="state-value-tag">{{ item.state }}</el-tag>
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
              </div>
              <div v-else class="no-exception-box">
                <el-icon class="safe-check-icon"><CircleCheck /></el-icon>
                <span>无异常，系统处于安全就绪空间</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
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

defineEmits<{
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
.holistic-section-card {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  box-shadow: var(--sl-shadow-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.section-card-head {
  padding: 8px 12px;
  background: #fafbfc;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-card-title { display: flex; align-items: baseline; gap: 8px; }
.sec-title-text { font-size: 13px; font-weight: 700; color: var(--sl-text-heading); }
.sec-desc-text { font-size: 11.5px; color: var(--sl-text-secondary); }
.card-head-meta { display: flex; align-items: center; gap: 6px; }
.section-card-body { padding: 12px; }
.pulse-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--sl-success);
  box-shadow: 0 0 6px rgba(22, 163, 74, 0.6);
  animation: blink 1.6s infinite alternate;
}
.pulse-dot.retired { background: var(--sl-text-disabled); animation: none; box-shadow: none; }
.last-time { color: var(--sl-text-secondary); font-size: 11px; }
.telemetry-kpi-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px; }
.kpi-card {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-left: 3px solid var(--sl-primary);
  border-radius: var(--sl-radius-sm);
  padding: 10px 12px;
  box-shadow: var(--sl-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kpi-label { display: flex; align-items: center; gap: 6px; }
.kpi-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--sl-primary); }
.kpi-label-text { font-size: 12px; font-weight: 600; color: var(--sl-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kpi-value-row { display: flex; align-items: baseline; gap: 4px; margin: 2px 0; }
.kpi-val { font-size: 22px; font-weight: 700; color: var(--sl-text-heading); line-height: 1.1; }
.kpi-unit { font-size: 12px; color: var(--sl-text-secondary); font-weight: 600; }
.statemachine-body-split { display: grid; grid-template-columns: 280px 1fr; gap: 12px; }
.statemachine-subcard {
  background: #f8fafc;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.subcard-header {
  padding: 6px 10px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.subcard-title { font-size: 12px; font-weight: 700; color: var(--sl-text-heading); }
.cmd-content-body { padding: 12px; display: flex; flex-direction: column; gap: 8px; justify-content: center; min-height: 110px; }
.cmd-state-main { display: flex; align-items: center; gap: 8px; }
.cmd-dot { width: 8px; height: 8px; border-radius: 50%; }
.cmd-dot.idle { background: #64748b; }
.cmd-dot.running { background: #0284c7; box-shadow: 0 0 6px rgba(2, 132, 199, 0.6); animation: blink 1.6s infinite alternate; }
.cmd-dot.completed { background: var(--sl-success); }
.cmd-dot.error { background: #ef4444; }
.cmd-state-name { font-size: 16px; font-weight: 700; color: var(--sl-text-heading); }
.cmd-state-desc { font-size: 11.5px; color: var(--sl-text-secondary); line-height: 1.45; }
.op-regions-container { padding: 10px; display: flex; flex-direction: column; gap: 8px; }
.region-section-box { background: #ffffff; border: 1px solid var(--sl-border-base); border-radius: 4px; padding: 8px 10px; display: flex; flex-direction: column; gap: 6px; }
.operational-box { border-left: 3px solid var(--sl-success); }
.exception-box { border-left: 3px solid #ef4444; }
.region-box-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.region-type-pill { font-size: 11px; font-weight: 700; padding: 1px 6px; border-radius: 3px; }
.op-pill { background: #ecfdf5; color: #047857; border: 1px solid #a7f3d0; }
.err-pill { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
.region-tags-wrap { display: flex; flex-direction: column; gap: 6px; }
.region-row-item { display: flex; align-items: center; gap: 10px; font-size: 12px; }
.region-name-badge {
  color: var(--sl-text-heading); font-weight: 700; font-size: 11.5px;
  background: #f1f5f9; border: 1px solid var(--sl-border-base); padding: 1px 7px; border-radius: 4px;
}
.region-state-tags { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
.state-value-tag { font-weight: 600; }
.exception-row-item { display: flex; align-items: center; justify-content: space-between; gap: 10px; width: 100%; }
.clear-exception-btn { font-size: 11.5px; height: 24px; padding: 0 10px; }
.no-exception-box { display: flex; align-items: center; gap: 6px; color: var(--sl-success); font-size: 11.5px; font-weight: 600; padding: 2px 0; }
.safe-check-icon { font-size: 14px; color: var(--sl-success); }
.empty-region-hint { font-size: 11.5px; color: var(--sl-text-secondary); }
.mono { font-family: var(--sl-font-mono); }
.muted { color: var(--sl-text-disabled); font-size: 12px; }
.compact-empty.block-empty { font-size: 12px; color: var(--sl-text-disabled); padding: 12px 0; }
@keyframes blink { 0% { opacity: 0.35; } 100% { opacity: 1; } }
</style>
