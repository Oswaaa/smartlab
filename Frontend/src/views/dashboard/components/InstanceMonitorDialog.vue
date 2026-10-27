<template>
  <Teleport to="body">
    <div v-if="visible && instance" class="m-overlay" @click.self="close">
      <div class="m-modal" role="dialog" aria-modal="true">
        <header class="m-head">
          <div class="m-head-top">
            <div class="m-head-left">
              <span class="m-tag">设备实例</span>
              <h3 class="m-title" :title="instance.instanceName">{{ instance.instanceName }}</h3>
              <span v-if="instance.boundDevicePoint" class="m-point">{{ instance.boundDevicePoint }}</span>
              <button class="m-id" type="button" title="点击复制实例 ID" @click="copyInstanceId">
                ID: {{ instance.instanceId }}
              </button>
            </div>
            <div class="m-head-right">
              <span class="m-badge" :class="retired ? 'is-retired' : 'is-use'">
                <span class="m-dot"></span>{{ retired ? '已注销' : '使用中' }}
              </span>
              <span v-if="!retired" class="m-badge" :class="online ? 'is-online' : 'is-offline'">
                <span class="m-dot"></span>{{ online ? '在线' : '离线' }}
              </span>
              <button
                class="m-console-btn"
                :class="{ active: consoleOpen }"
                type="button"
                @click="consoleOpen = !consoleOpen"
              >
                控制台日志
              </button>
              <button class="m-close" type="button" title="关闭" @click="close">✕</button>
            </div>
          </div>

          <div class="m-ribbon">
            <div class="m-col">
              <span class="m-lbl">所属模型</span>
              <span class="m-val strong">{{ modelName }}</span>
            </div>
            <div class="m-col">
              <span class="m-lbl">Adapter 名称</span>
              <span class="m-val">{{ instance.boundAdapterName || '未分配' }}</span>
            </div>
            <div class="m-col">
              <span class="m-lbl">物理点位</span>
              <span class="m-val">{{ instance.boundDevicePoint || '未分配' }}</span>
            </div>
            <div class="m-col">
              <span class="m-lbl">最后心跳</span>
              <span class="m-val mono">{{ lastSnapshotTime }}</span>
            </div>
          </div>
        </header>

        <div v-if="retired" class="m-retired-bar">
          该设备实例已注销，实时状态与控制仅供只读查阅，不再参与控制调度或新业务。
        </div>

        <div class="m-body" :class="{ 'console-open': consoleOpen }">
          <div class="m-pane-status">
            <InstanceRuntimeTab
              :instance="instance"
              :snapshot="snapshot"
              :models="models"
              :model-options="models"
              :last-snapshot-time="lastSnapshotTime"
              :retired="retired"
              :can-control="canControl"
              :clearing-exception="clearingException"
              @clear-exception="clearExceptionState"
            />
          </div>

          <div v-if="canControl" class="m-pane-control">
            <InstanceControlTab
              :instance="instance"
              :snapshot="snapshot"
              :models="models"
              :model-options="models"
              :instances="devices"
              :instance-components="instanceComponents"
              :retired="retired"
              @refresh-snapshot="fetchSnapshot"
            />
          </div>

          <div v-else class="m-pane-control m-no-permission">
            <div class="m-no-perm-inner">
              <p class="m-no-perm-title">无控制权限</p>
              <p class="m-no-perm-desc">当前账号缺少 device_instance:control 权限，仅可查看实时运行状态。</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import InstanceRuntimeTab from '../../device/components/deviceInstance/InstanceRuntimeTab.vue'
import InstanceControlTab from '../../device/components/deviceInstance/InstanceControlTab.vue'
import * as api from '../../device/components/deviceInstance/deviceInstanceApi'
import { getModelName } from '../../device/components/deviceInstance/normalizers'
import { useAuthStore } from '../../../stores/authStore'

const props = defineProps<{
  modelValue: boolean
  instance: any | null
  devices: any[]
  models: any[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const authStore = useAuthStore()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const snapshot = ref<any>(null)
const lastSnapshotTime = ref('-')
const instanceComponents = ref<any[]>([])
const clearingException = ref('')
const consoleOpen = ref(false)
let pollingTimer: any = null
let bodyOverflowBackup = ''

const retired = computed(() => props.instance?.lifecycleStatus === 'RETIRED')
const online = computed(() => {
  if (snapshot.value?.onlineStatus) return String(snapshot.value.onlineStatus).toUpperCase() === 'ONLINE'
  return props.instance?.isOnline === true || props.instance?.onlineStatus === 'ONLINE'
})
const canControl = computed(() => authStore.hasPermission('device_instance:control') && !retired.value)
const modelName = computed(() => getModelName(props.instance?.modelId, props.models, props.models))

const close = () => {
  visible.value = false
}

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') close()
}

const fetchSnapshot = async () => {
  const id = props.instance?.instanceId
  if (!id || !props.modelValue) return
  try {
    const data = await api.getSnapshot(id)
    if (props.instance?.instanceId !== id || !props.modelValue) return
    if (data?.success) {
      snapshot.value = data.data || null
      lastSnapshotTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    }
  } catch {
    if (props.instance?.instanceId !== id || !props.modelValue) return
    snapshot.value = null
  }
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const startPolling = () => {
  stopPolling()
  fetchSnapshot()
  if (retired.value) return
  pollingTimer = setInterval(fetchSnapshot, 1000)
}

const loadComponents = async () => {
  const id = props.instance?.instanceId
  if (!id) {
    instanceComponents.value = []
    return
  }
  try {
    const data = await api.listComponents(id)
    if (props.instance?.instanceId !== id) return
    if (data?.success) instanceComponents.value = data.data || []
  } catch {
    instanceComponents.value = []
  }
}

const copyInstanceId = () => {
  const id = props.instance?.instanceId
  if (!id) return
  navigator.clipboard
    .writeText(String(id))
    .then(() => ElMessage.success('已复制实例 ID'))
    .catch(() => ElMessage.error('复制失败'))
}

const clearExceptionState = async (stateName: string) => {
  const id = props.instance?.instanceId
  if (!id || !stateName || retired.value) return
  try {
    await ElMessageBox.confirm(`确认解除异常状态“${stateName}”？系统会先复核当前遥测值。`, '解除异常', {
      confirmButtonText: '解除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    clearingException.value = stateName
    const data = await api.clearException(id, stateName)
    if (!data?.success) {
      ElMessage.error(data?.message || '解除异常失败')
      return
    }
    ElMessage.success('异常已解除')
    await fetchSnapshot()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || error?.message || '解除异常失败')
    }
  } finally {
    clearingException.value = ''
  }
}

watch(
  () => props.modelValue,
  open => {
    if (open) {
      consoleOpen.value = false
      snapshot.value = null
      lastSnapshotTime.value = '-'
      instanceComponents.value = []
      bodyOverflowBackup = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      window.addEventListener('keydown', onKeydown)
      loadComponents()
      startPolling()
    } else {
      stopPolling()
      window.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = bodyOverflowBackup
    }
  },
  { immediate: true }
)

onUnmounted(() => {
  stopPolling()
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = bodyOverflowBackup
})
</script>

<style scoped>
.m-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}

.m-modal {
  width: min(1120px, 94vw);
  height: min(700px, 90vh);
  background: #ffffff;
  border-radius: 10px;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.32);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  font-family: var(--sl-font-sans, inherit);
  color: var(--sl-text-heading, #0f172a);
  box-sizing: border-box;
}

/* ===== 头部 ===== */
.m-head {
  flex-shrink: 0;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}
.m-head-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px 8px;
}
.m-head-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.m-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 600;
  flex-shrink: 0;
}
.m-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 360px;
}
.m-point {
  font-size: 11px;
  font-family: var(--sl-font-mono, monospace);
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 1px 6px;
  flex-shrink: 0;
}
.m-id {
  font-size: 11px;
  font-family: var(--sl-font-mono, monospace);
  color: #64748b;
  background: transparent;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  padding: 1px 6px;
  cursor: pointer;
  flex-shrink: 0;
}
.m-id:hover {
  color: #1d4ed8;
  border-color: #93c5fd;
}
.m-head-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.m-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  font-weight: 600;
  white-space: nowrap;
}
.m-badge .m-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.m-badge.is-use { color: #16a34a; }
.m-badge.is-online { color: #16a34a; }
.m-badge.is-offline { color: #64748b; }
.m-badge.is-retired { color: #94a3b8; }
.m-console-btn {
  font-size: 11.5px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 4px;
  border: 1px solid #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
  cursor: pointer;
  white-space: nowrap;
}
.m-console-btn:hover {
  background: #dbeafe;
}
.m-console-btn.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
}
.m-close {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  line-height: 1;
  border-radius: 4px;
  cursor: pointer;
}
.m-close:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.m-ribbon {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 0 14px 10px;
  flex-wrap: wrap;
}
.m-col {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}
.m-lbl {
  font-size: 11px;
  color: #64748b;
  flex-shrink: 0;
}
.m-val {
  font-size: 11.5px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.m-val.strong { color: #1d4ed8; }
.mono { font-family: var(--sl-font-mono, monospace); }

.m-retired-bar {
  flex-shrink: 0;
  padding: 7px 14px;
  background: #fef3c7;
  border-bottom: 1px solid #fde68a;
  color: #92400e;
  font-size: 11.5px;
}

/* ===== 主体：左 1/3 状态 + 右 2/3 控制 ===== */
.m-body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}
.m-pane-status {
  width: 33.333%;
  min-width: 0;
  padding: 10px 12px;
  background: #f8fafc;
  border-right: 1px solid var(--sl-border-base, #e2e8f0);
  overflow-y: auto;
  box-sizing: border-box;
}
.m-pane-control {
  flex: 1;
  min-width: 0;
  position: relative;
  padding: 10px 12px;
  overflow: hidden;
  box-sizing: border-box;
}

.m-no-permission {
  display: flex;
  align-items: center;
  justify-content: center;
}
.m-no-perm-inner {
  text-align: center;
}
.m-no-perm-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}
.m-no-perm-desc {
  margin: 0;
  font-size: 11.5px;
  color: #94a3b8;
}

/* 控制调试内部原本是 1fr / 1fr 两栏，这里压成单栏，
   并把日志右栏改成绝对定位的滑出面板（组件本身不改）。
   选择器用双 class 提高特异性，避免被组件自身的 scoped 样式覆盖 */
.m-body .m-pane-control :deep(.control-tab-workbench) {
  display: block;
  position: static;
  height: 100%;
  overflow: visible;
}
.m-body .m-pane-control :deep(.control-left-col) {
  height: 100%;
  overflow-y: auto;
  padding-right: 2px;
}
.m-body .m-pane-control :deep(.control-right-panel) {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  height: auto;
  width: min(560px, 96%);
  z-index: 200;
  display: flex;
  flex-direction: column;
  border-radius: 6px 0 0 6px;
  box-shadow: -10px 0 26px rgba(15, 23, 42, 0.28);
  transform: translateX(calc(100% + 24px));
  transition: transform 0.22s ease;
}
.m-body.console-open .m-pane-control :deep(.control-right-panel) {
  transform: none;
}

@media (max-width: 1180px) {
  .m-pane-status {
    width: 38%;
  }
}
</style>
