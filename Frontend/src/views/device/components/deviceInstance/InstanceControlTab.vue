<template>
  <div class="tab-pane-content control-tab-workbench full-height-workbench">
    <div class="control-left-col">
      <div class="control-card control-form-card">
        <div class="card-title-bar">
          <span class="card-title">下发指令</span>
        </div>
        <el-form label-position="top" size="small" class="manual-control-form">
          <el-form-item label="设备操作能力" required class="tree-select-form-item">
            <div class="custom-tree-select-trigger" :class="{ open: treeDropdownOpen }" @click="treeDropdownOpen = !treeDropdownOpen">
              <div class="trigger-selected-content" v-if="activeControlCapability">
                <span class="trigger-entity-tag">{{ activeControlGroup?.shortName }}</span>
                <span class="trigger-sep">·</span>
                <span class="trigger-cap-title">{{ activeControlCapability.displayName || activeControlCapability.capabilityName }}</span>
              </div>
              <span v-else class="trigger-placeholder">请选择设备操作能力</span>
              <span class="custom-select-caret"></span>
            </div>
            <div v-if="treeDropdownOpen" class="tree-select-backdrop" @click="treeDropdownOpen = false"></div>
            <div v-if="treeDropdownOpen" class="tree-select-dropdown-panel">
              <div
                v-for="group in aggregatedControlGroups"
                :key="group.key"
                class="tree-group-section"
                :class="{ collapsed: !!collapsedEntityGroups[group.key] }"
              >
                <div class="tree-group-header" @click.stop="toggleGroupCollapse(group.key)">
                  <div class="group-header-left">
                    <span class="group-fold-caret" :class="{ 'is-collapsed': !!collapsedEntityGroups[group.key] }"></span>
                    <span class="group-title-text">{{ group.name }}</span>
                  </div>
                  <span class="group-model-badge">{{ group.modelName }}</span>
                </div>
                <div v-show="!collapsedEntityGroups[group.key]" class="tree-group-items">
                  <div
                    v-for="cap in group.capabilities"
                    :key="cap.capabilityName"
                    class="tree-cap-row"
                    :class="{ active: selectedControlEntityKey === group.key && controlCapabilityName === cap.capabilityName }"
                    @click.stop="selectControlCapability(group.key, cap.capabilityName)"
                  >
                    <div class="cap-row-left">
                      <span class="cap-bullet"></span>
                      <span class="cap-display-name">{{ cap.displayName || cap.capabilityName }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-form-item>

          <div class="control-param-grid" v-if="activeControlParams.length">
            <el-form-item v-for="param in activeControlParams" :key="paramKey(param)" :label="param.displayName || param.name || param.paramName">
              <el-switch v-if="isBooleanType(param.dataType)" v-model="controlParamValues[paramKey(param)]" />
              <el-input-number
                v-else-if="isNumberType(param.dataType)"
                v-model="controlParamValues[paramKey(param)]"
                :precision="isIntegerType(param.dataType) ? 0 : undefined"
                :controls="false"
                style="width: 100%"
              />
              <el-input v-else v-model="controlParamValues[paramKey(param)]" :placeholder="param.dataType || 'STRING'" />
              <div class="field-hint">数据类型: {{ param.dataType || '-' }}</div>
            </el-form-item>
          </div>
          <div v-else class="empty-inline">当前所选操作无需配置外部参数。</div>
        </el-form>
        <div class="control-action-bar">
          <button
            class="btn-primary-blue"
            type="button"
            :disabled="sendingControl || !controlCapabilityName || activeInstanceCommandState !== 'IDLE'"
            @click="sendManualCommand"
          >
            <span>{{ commandButtonText }}</span>
          </button>
          <button
            v-if="activeInstanceCommandState !== 'IDLE'"
            class="btn-danger-outline"
            type="button"
            :disabled="abortingControl || !canAbortActiveCapability"
            :title="!canAbortActiveCapability ? '该能力未在物模型中定义中止命令' : '向物理设备发送停机指令 (MANUAL_EXECUTE_ABORT)'"
            @click="handleAbortCommand"
          >
            <span>{{ abortingControl ? '中止中...' : (canAbortActiveCapability ? '终止执行' : '未配置终止能力') }}</span>
          </button>
        </div>
      </div>

      <div class="control-card control-meta-card">
        <div class="card-title-bar">
          <span class="card-title">指令执行生命周期</span>
          <span class="m-val status-val" :class="cmdStateClass(activeInstanceCommandState)">
            <span class="dot"></span>{{ activeInstanceCommandState }}
          </span>
        </div>
        <div class="meta-card-body">
          <el-descriptions :column="1" border size="small" class="meta-descriptions">
            <el-descriptions-item label="目标实体">
              <strong class="text-heading">{{ activeControlGroup?.name || '-' }}</strong>
            </el-descriptions-item>
            <el-descriptions-item label="当前动作">
              <strong class="text-heading">{{ activeControlCapability?.displayName || activeControlCapability?.capabilityName || '未选择操作' }}</strong>
              <span v-if="activeControlCapability?.adapterCommandName" class="mono-text text-primary" style="margin-left: 6px;">(Adapter: {{ activeControlCapability.adapterCommandName }})</span>
            </el-descriptions-item>
            <el-descriptions-item label="生命周期状态">
              <span class="text-heading">{{ cmdStateDescription(activeInstanceCommandState) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="下发点位">
              <strong class="mono-text text-primary">{{ activeControlGroup?.boundDevicePoint || '-' }}</strong>
              <span class="text-secondary" style="margin-left: 6px;">(所属 Adapter: {{ activeControlGroup?.boundAdapterName || '-' }})</span>
            </el-descriptions-item>
            <el-descriptions-item label="指令主题">
              <code class="mono-text topic-inline-code">smartlab/adapter/{{ activeControlGroup?.boundAdapterName || '-' }}/{{ activeControlGroup?.boundDevicePoint || '-' }}/command</code>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </div>

    <div class="control-right-panel full-height-console">
      <div class="console-header">
        <div class="console-title">
          <span class="console-dot"></span>
          <span>下发反馈与通信终端</span>
          <span class="log-count-tag" v-if="consoleLogs.length">({{ consoleLogs.length }})</span>
        </div>
        <div class="console-actions">
          <button
            v-if="activeInstanceCommandState !== 'IDLE'"
            class="btn-link console-reset-btn"
            type="button"
            :disabled="resettingControl"
            @click="handleForceResetCommand"
            title="向状态机下发 MANUAL_EXECUTE_RESET 信号复位周期"
          >
            <el-icon><Warning /></el-icon>
            <span>人工复位</span>
          </button>
          <button class="btn-link console-copy-btn" type="button" @click="copyConsoleLogs" v-if="consoleLogs.length">
            <el-icon><CopyDocument /></el-icon>
            <span>复制</span>
          </button>
          <button class="btn-link console-clear-btn" type="button" @click="clearConsoleLogs">清空记录</button>
        </div>
      </div>
      <div class="console-body" ref="consoleBodyRef">
        <div v-for="(log, idx) in consoleLogs" :key="idx" :class="['console-line', log.type]">
          <span class="console-time mono">[{{ log.time }}]</span>
          <span class="console-tag">[{{ log.tag }}]</span>
          <span class="console-text">{{ log.text }}</span>
        </div>
        <div v-if="consoleLogs.length === 0" class="console-empty">
          <div class="empty-icon"><el-icon :size="24"><Connection /></el-icon></div>
          <div class="empty-title">暂无指令下发与通信记录</div>
          <div class="empty-hint">在左侧选择操作能力配置参数并点击【开始执行】，将在此处实时捕获下发报文及设备响应</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Connection, CopyDocument, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { normalizeManualControlCapabilities } from '../../../../utils/manualControlCapabilities.js'
import { useConsoleStore } from '../../../../stores/consoleStore'
import { manualControlSignals } from '../deviceModel/deviceModelConstants'
import * as api from './deviceInstanceApi'
import {
  asArray,
  cmdStateClass,
  cmdStateDescription,
  defaultValueForType,
  findModelById,
  isBooleanType,
  isIntegerType,
  isNumberType,
  paramKey
} from './normalizers'
import type { ControlGroup, DeviceInstance, DeviceModel, DeviceSnapshot } from './types'

const props = defineProps<{
  instance: DeviceInstance
  snapshot: DeviceSnapshot | null
  models: DeviceModel[]
  modelOptions: DeviceModel[]
  instances: DeviceInstance[]
  instanceComponents: any[]
  retired: boolean
}>()

const emit = defineEmits<{
  'refresh-snapshot': []
}>()

const consoleStore = useConsoleStore()
const sendingControl = ref(false)
const resettingControl = ref(false)
const abortingControl = ref(false)
const controlCapabilityName = ref('')
const controlParamValues = ref<Record<string, any>>({})
const selectedControlEntityKey = ref('main')
const treeDropdownOpen = ref(false)
const collapsedEntityGroups = ref<Record<string, boolean>>({})
const consoleBodyRef = ref<HTMLElement | null>(null)

const consoleLogs = computed(() => consoleStore.getLogs(props.instance?.instanceId).value)
const activeInstanceModel = computed(() => findModelById(props.instance?.modelId || '', props.models, props.modelOptions))
const activeInstanceCommandState = computed(() => String(props.snapshot?.currentCommandState || 'IDLE').toUpperCase())

const aggregatedControlGroups = computed(() => {
  const groups: ControlGroup[] = []
  const inst = props.instance
  if (!inst?.instanceId) return groups
  const mainCaps = normalizeManualControlCapabilities(
    asArray(activeInstanceModel.value?.capabilitySpec?.capabilities || activeInstanceModel.value?.capabilities)
  )
  groups.push({
    key: 'main',
    name: inst.instanceName || inst.instanceId,
    shortName: inst.instanceName || inst.instanceId,
    modelName: activeInstanceModel.value?.modelName || inst.modelId || '-',
    isComponent: false,
    targetInstanceId: inst.instanceId,
    boundAdapterName: inst.boundAdapterName || '',
    boundDevicePoint: inst.boundDevicePoint || '',
    capabilities: mainCaps
  })
  const comps = asArray(props.instanceComponents).filter((c: any) => c.selfInstanceId && c.status !== '已更换')
  for (const comp of comps) {
    const childInstance = props.instances.find(d => String(d.instanceId) === String(comp.selfInstanceId))
    if (!childInstance) continue
    const childModel = findModelById(childInstance.modelId, props.models, props.modelOptions)
    const childCaps = normalizeManualControlCapabilities(
      asArray(childModel?.capabilitySpec?.capabilities || childModel?.capabilities)
    )
    if (childCaps.length > 0) {
      groups.push({
        key: `comp_${comp.selfInstanceId}`,
        name: `${comp.componentName || '组件'} (${comp.slotCode ? '槽位: ' + comp.slotCode + ' · ' : ''}${childInstance.instanceName || childInstance.instanceId})`,
        shortName: `${comp.componentName || '组件'}`,
        modelName: childModel?.modelName || childInstance.modelId || '-',
        isComponent: true,
        targetInstanceId: childInstance.instanceId,
        boundAdapterName: childInstance.boundAdapterName || '',
        boundDevicePoint: childInstance.boundDevicePoint || '',
        capabilities: childCaps
      })
    }
  }
  return groups
})

const activeControlGroup = computed(() =>
  aggregatedControlGroups.value.find(g => g.key === selectedControlEntityKey.value) || aggregatedControlGroups.value[0] || null
)
const activeControlCapability = computed(() => {
  if (!activeControlGroup.value) return null
  return activeControlGroup.value.capabilities.find((capability: any) => capability.capabilityName === controlCapabilityName.value) || null
})
const activeControlParams = computed(() => asArray(activeControlCapability.value?.parameters).filter((param: any) => !param.internal))
const canAbortActiveCapability = computed(() => {
  if (!activeControlGroup.value || !activeControlCapability.value) return false
  const cap = activeControlCapability.value
  if (cap.abortCapabilityName) return true
  return activeControlGroup.value.capabilities.some((c: any) => c.isAbort && asArray(c.scope).includes(cap.capabilityName))
})
const commandButtonText = computed(() => {
  if (sendingControl.value || activeInstanceCommandState.value === 'SENT') return '指令下发中'
  if (activeInstanceCommandState.value === 'RECEIVED' || activeInstanceCommandState.value === 'RUNNING') return '指令执行中'
  if (activeInstanceCommandState.value !== 'IDLE') return '指令执行中'
  return '开始执行'
})

const resetControlParams = () => {
  const next: Record<string, any> = {}
  activeControlParams.value.forEach((param: any) => {
    next[paramKey(param)] = controlParamValues.value[paramKey(param)] ?? defaultValueForType(param.dataType)
  })
  controlParamValues.value = next
}

const registerControlEntities = (groups: ControlGroup[]) => {
  const inst = props.instance
  if (!inst?.instanceId || !groups.length) return
  groups.forEach((g: ControlGroup) => {
    consoleStore.registerEntityMeta(
      g.targetInstanceId,
      g.shortName || g.name,
      g.capabilities,
      g.isComponent ? inst.instanceId : undefined
    )
  })
}

const applyDefaultCapability = (groups: ControlGroup[]) => {
  selectedControlEntityKey.value = 'main'
  controlCapabilityName.value = groups[0]?.capabilities[0]?.capabilityName || ''
  resetControlParams()
}

const reconcileControlSelection = (groups: ControlGroup[]) => {
  if (!groups.length) {
    selectedControlEntityKey.value = 'main'
    controlCapabilityName.value = ''
    controlParamValues.value = {}
    return
  }
  const currentGroup = groups.find(g => g.key === selectedControlEntityKey.value) || groups[0]
  selectedControlEntityKey.value = currentGroup.key
  const stillValid = currentGroup.capabilities.some((c: any) => c.capabilityName === controlCapabilityName.value)
  if (!stillValid) {
    controlCapabilityName.value = currentGroup.capabilities[0]?.capabilityName || ''
    resetControlParams()
  }
}

watch(
  () => props.instance?.instanceId,
  (instanceId) => {
    if (!instanceId) {
      selectedControlEntityKey.value = 'main'
      controlCapabilityName.value = ''
      controlParamValues.value = {}
      return
    }
    applyDefaultCapability(aggregatedControlGroups.value)
  },
  { immediate: true }
)

watch(
  aggregatedControlGroups,
  (groups) => {
    if (!props.instance?.instanceId) return
    registerControlEntities(groups)
    reconcileControlSelection(groups)
  },
  { immediate: true }
)

const toggleGroupCollapse = (key: string) => {
  collapsedEntityGroups.value[key] = !collapsedEntityGroups.value[key]
}

const selectControlCapability = (groupKey: string, capName: string) => {
  selectedControlEntityKey.value = groupKey
  controlCapabilityName.value = capName
  resetControlParams()
  treeDropdownOpen.value = false
}

const buildControlParameters = () => {
  const parameters: Record<string, any> = {}
  activeControlParams.value.forEach((param: any) => {
    const key = paramKey(param)
    let value = controlParamValues.value[key]
    if (isIntegerType(param.dataType)) value = value === '' || value == null ? 0 : Number.parseInt(String(value), 10)
    else if (isNumberType(param.dataType)) value = value === '' || value == null ? 0 : Number(value)
    parameters[key] = value
  })
  return parameters
}

const appendConsoleLog = (tag: any, type: any, text: string) => {
  const instanceId = props.instance?.instanceId
  if (!instanceId) return
  const targetInstanceId = activeControlGroup.value?.targetInstanceId || instanceId
  consoleStore.appendLog(targetInstanceId, tag, type, text, instanceId)
  nextTick(() => {
    if (consoleBodyRef.value) consoleBodyRef.value.scrollTop = consoleBodyRef.value.scrollHeight
  })
}

const clearConsoleLogs = () => {
  if (!props.instance?.instanceId) return
  consoleStore.clearLogs(props.instance.instanceId)
}

const copyConsoleLogs = async () => {
  if (!consoleLogs.value.length) return
  const text = consoleLogs.value.map((l: any) => `[${l.time}] [${l.tag}] ${l.text}`).join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('通信日志已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

const sendManualCommand = async () => {
  if (props.retired || !controlCapabilityName.value || !props.instance?.instanceId) return
  sendingControl.value = true
  const targetInstanceId = activeControlGroup.value?.targetInstanceId || props.instance.instanceId
  const params = buildControlParameters()
  try {
    const data = await api.controlInstance(targetInstanceId, {
      capabilityName: controlCapabilityName.value,
      signalName: manualControlSignals[0] || 'MANUAL_EXECUTE_START',
      parameters: params
    })
    if (data?.success) {
      emit('refresh-snapshot')
    } else {
      const errMsg = data?.message || '未知异常'
      appendConsoleLog('失败', 'fail', `指令发送被拒: ${errMsg}`)
      ElMessage.error(`下发失败: ${errMsg}`)
    }
  } catch (err: any) {
    const errMsg = err.response?.data?.message || err.message || '网络连接异常'
    appendConsoleLog('失败', 'fail', `通信异常: ${errMsg}`)
    ElMessage.error(`网络错误: ${errMsg}`)
  } finally {
    sendingControl.value = false
  }
}

const handleAbortCommand = async () => {
  if (!props.instance?.instanceId) return
  const targetInstanceId = activeControlGroup.value?.targetInstanceId || props.instance.instanceId
  const targetName = activeControlGroup.value?.shortName || '设备'
  try {
    await ElMessageBox.confirm(
      `确定要向【${targetName}】下发终止信号 (MANUAL_EXECUTE_ABORT) 吗？物理设备将执行安全停机并退出当前指令周期。`,
      '终止执行确认',
      { confirmButtonText: '确定终止', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  abortingControl.value = true
  appendConsoleLog('中止', 'fail', `已向【${targetName}】发送终止信号 (MANUAL_EXECUTE_ABORT)，等待安全停机...`)
  try {
    const data = await api.controlInstance(targetInstanceId, { signalName: 'MANUAL_EXECUTE_ABORT' })
    if (data?.success) {
      ElMessage.success('终止信号已下发')
      emit('refresh-snapshot')
    } else {
      const errMsg = data?.message || '终止请求被拒'
      appendConsoleLog('失败', 'fail', `终止被拒: ${errMsg}`)
      ElMessage.error(`终止失败: ${errMsg}`)
    }
  } catch (err: any) {
    const errMsg = err.response?.data?.message || err.message || '网络连接异常'
    appendConsoleLog('失败', 'fail', `终止异常: ${errMsg}`)
    ElMessage.error(`网络错误: ${errMsg}`)
  } finally {
    abortingControl.value = false
  }
}

const handleForceResetCommand = async () => {
  if (activeInstanceCommandState.value === 'IDLE') {
    ElMessage.info('当前状态机处于空闲就绪态 (IDLE)，无需人工复位')
    return
  }
  try {
    await ElMessageBox.confirm(
      '【人工复位提示】此操作将向设备状态机下发 MANUAL_EXECUTE_RESET 信号，强制复位指令生命周期并将状态机重置为 IDLE 空闲就绪态。是否确认复位？',
      '人工复位确认',
      { confirmButtonText: '确认复位', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  resettingControl.value = true
  try {
    if (!props.instance?.instanceId) return
    const targetInstanceId = activeControlGroup.value?.targetInstanceId || props.instance.instanceId
    const data = await api.controlInstance(targetInstanceId, { signalName: 'MANUAL_EXECUTE_RESET' })
    if (data?.success) {
      ElMessage.success('已成功人工复位')
      appendConsoleLog('系统', 'info', '已发送人工复位信号 (MANUAL_EXECUTE_RESET)，状态机已恢复空闲就绪 (IDLE)')
      emit('refresh-snapshot')
    } else {
      ElMessage.error(`复位失败: ${data?.message || '未知错误'}`)
    }
  } catch (err: any) {
    ElMessage.error(`复位请求失败: ${err.response?.data?.message || err.message}`)
  } finally {
    resettingControl.value = false
  }
}
</script>

<style scoped>
.control-tab-workbench {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
.full-height-workbench { height: 100%; min-height: 0; }
.control-left-col { display: flex; flex-direction: column; gap: 12px; min-height: 0; height: 100%; }
.control-card {
  background: #ffffff;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.control-form-card { flex-shrink: 0; }
.control-meta-card { flex: 1; min-height: 0; }
.card-title-bar {
  padding: 8px 14px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
}
.card-title { font-size: 13px; font-weight: 600; color: var(--sl-text-heading, #0f172a); }
.manual-control-form { padding: 10px 12px; display: flex; flex-direction: column; gap: 8px; }
.manual-control-form :deep(.el-form-item) { margin-bottom: 0 !important; }
.manual-control-form :deep(.el-form-item__label) {
  padding-bottom: 3px !important; font-size: 12px; font-weight: 600; color: var(--sl-text-heading, #0f172a); line-height: 1.2;
}
.tree-select-form-item { position: relative; }
.custom-tree-select-trigger {
  width: 100%; height: 32px; min-height: 32px;
  border: 1px solid var(--sl-border-input, #cbd5e1);
  border-radius: var(--sl-radius-sm, 4px);
  padding: 0 10px; font-size: 12.5px; background: #ffffff;
  color: var(--sl-text-heading, #0f172a);
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  cursor: pointer; user-select: none;
}
.custom-tree-select-trigger:hover,
.custom-tree-select-trigger.open { border-color: var(--sl-primary, #2563eb); box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1); }
.trigger-selected-content { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; min-width: 0; line-height: 1.3; }
.trigger-entity-tag { font-size: 11.5px; font-weight: 600; color: var(--sl-text-heading, #0f172a); }
.trigger-sep { font-size: 10.5px; color: var(--sl-text-secondary, #94a3b8); }
.trigger-cap-title { font-weight: 600; color: var(--sl-primary, #2563eb); }
.trigger-placeholder { color: var(--sl-text-placeholder, #94a3b8); font-size: 12px; }
.custom-select-caret {
  width: 0; height: 0;
  border-left: 4.5px solid transparent; border-right: 4.5px solid transparent;
  border-top: 5px solid var(--sl-text-secondary, #94a3b8);
  transition: transform 0.2s ease; flex-shrink: 0;
}
.custom-tree-select-trigger.open .custom-select-caret { transform: rotate(180deg); }
.tree-select-backdrop { position: fixed; inset: 0; z-index: 199; }
.tree-select-dropdown-panel {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0;
  background: #ffffff; border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px); box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);
  z-index: 200; max-height: 260px; overflow-y: auto; padding: 4px;
}
.tree-group-section { border-radius: 4px; margin-bottom: 2px; overflow: hidden; }
.tree-group-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 8px; background: #f8fafc; font-size: 12px; font-weight: 600;
  color: var(--sl-text-heading, #0f172a); cursor: pointer; border-radius: 4px; user-select: none;
}
.tree-group-header:hover { background: #eff6ff; }
.group-header-left { display: flex; align-items: center; gap: 6px; min-width: 0; }
.group-fold-caret {
  width: 0; height: 0;
  border-left: 4px solid transparent; border-right: 4px solid transparent;
  border-top: 5px solid var(--sl-text-secondary, #64748b);
  transition: transform 0.18s ease; flex-shrink: 0;
}
.group-fold-caret.is-collapsed { transform: rotate(-90deg); }
.group-title-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.group-model-badge {
  font-size: 10.5px; font-weight: 500; font-family: var(--sl-font-mono, monospace);
  color: var(--sl-text-secondary, #64748b); background: #f1f5f9;
  border: 1px solid var(--sl-border-base, #e2e8f0); padding: 1px 7px; border-radius: 4px; flex-shrink: 0;
}
.tree-group-items { padding: 2px 0 2px 10px; }
.tree-cap-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 4px 8px; font-size: 12px; color: var(--sl-text-body, #334155);
  border-radius: 4px; cursor: pointer; user-select: none;
}
.tree-cap-row:hover { background: #eff6ff; color: var(--sl-primary, #2563eb); }
.tree-cap-row.active { background: #dbeafe; color: var(--sl-primary, #2563eb); font-weight: 600; }
.cap-row-left { display: flex; align-items: center; gap: 6px; }
.cap-bullet { width: 4px; height: 4px; border-radius: 50%; background: var(--sl-border-input, #94a3b8); }
.tree-cap-row.active .cap-bullet { background: var(--sl-primary, #2563eb); }
.control-param-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px 12px; }
.control-param-grid .field-hint { font-size: 10.5px; color: var(--sl-text-secondary, #64748b); margin-top: 1px; line-height: 1.2; }
.control-action-bar {
  padding: 8px 12px; background: #f8fafc; border-top: 1px solid var(--sl-border-base, #e2e8f0);
  display: flex; align-items: center; gap: 8px;
}
.meta-card-body { flex: 1; padding: 8px 12px; overflow: hidden; display: flex; flex-direction: column; }
.meta-descriptions { flex: 1; }
.meta-descriptions :deep(.el-descriptions__cell) { padding: 5px 8px !important; }
.topic-inline-code {
  font-size: 10.5px; color: var(--sl-primary, #2563eb); background: var(--sl-primary-light, #eff6ff);
  padding: 1px 5px; border-radius: 3px; word-break: break-all;
}
.control-right-panel {
  background: #0b1120; border: 1px solid #1e293b; border-radius: var(--sl-radius-sm, 6px);
  display: flex; flex-direction: column; overflow: hidden; height: 100%;
}
.console-header {
  background: #111827; border-bottom: 1px solid #1e293b; padding: 8px 14px;
  display: flex; align-items: center; justify-content: space-between; flex-shrink: 0;
}
.console-title { display: flex; align-items: center; gap: 7px; color: #f3f4f6; font-size: 12.5px; font-weight: 600; }
.console-dot { width: 7px; height: 7px; border-radius: 50%; background: #38bdf8; box-shadow: 0 0 6px rgba(56, 189, 248, 0.6); }
.log-count-tag { font-size: 11px; color: #94a3b8; font-weight: normal; }
.console-actions { display: flex; align-items: center; gap: 12px; }
.console-reset-btn { color: #f59e0b; font-size: 11.5px; display: inline-flex; align-items: center; gap: 4px; }
.console-reset-btn:hover { color: #fbbf24; }
.console-copy-btn { color: #38bdf8; font-size: 11.5px; display: inline-flex; align-items: center; gap: 4px; }
.console-copy-btn:hover { color: #7dd3fc; }
.console-clear-btn { color: #94a3b8; font-size: 11.5px; }
.console-clear-btn:hover { color: #f8fafc; }
.console-body {
  flex: 1; padding: 12px 14px; font-family: var(--sl-font-mono, monospace);
  font-size: 11.5px; color: #e2e8f0; line-height: 1.65; overflow-y: auto; min-height: 0;
}
.console-line { margin-bottom: 4px; word-break: break-all; }
.console-line.send { color: #38bdf8; }
.console-line.running { color: #facc15; }
.console-line.success { color: #4ade80; }
.console-line.fail { color: #f87171; }
.console-line.info { color: #94a3b8; }
.console-time { color: #64748b; margin-right: 6px; white-space: nowrap; }
.console-tag { font-weight: 700; margin-right: 6px; }
.console-empty {
  color: #475569; text-align: center; height: 100%;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 40px 20px;
}
.console-empty .empty-icon { color: #334155; }
.console-empty .empty-title { color: #64748b; font-size: 13px; font-weight: 500; }
.console-empty .empty-hint { color: #475569; font-size: 11.5px; max-width: 320px; line-height: 1.5; }
.m-val.status-val { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; }
.m-val.status-val .dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.empty-inline { font-size: 12px; color: var(--sl-text-disabled); padding: 8px 0; }
.mono { font-family: var(--sl-font-mono); }
.btn-danger-outline {
  background: #ffffff;
  border: 1px solid #ef4444;
  color: #b91c1c;
  font-size: 12.5px;
  font-weight: 500;
  height: 28px;
  padding: 0 12px;
  border-radius: var(--sl-radius-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}
.btn-danger-outline:hover:not(:disabled) { background: #fef2f2; }
.btn-danger-outline:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
