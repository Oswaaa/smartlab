import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useAuthStore } from './authStore'

export type ConsoleLogTag = '已投递' | '执行中' | '完成' | '失败' | '中止' | '系统' | '下发' | '成功'
export type ConsoleLogType = 'send' | 'running' | 'success' | 'fail' | 'info'

export interface ConsoleLogEntry {
  time: string
  tag: ConsoleLogTag
  type: ConsoleLogType
  text: string
}

export interface EntityCapabilityMeta {
  capabilityName: string
  displayName?: string
  parameters?: any[]
}

const STORAGE_PREFIX = 'smartlab_console_logs_'

const loadPersistedLogs = (instanceId: string | number): ConsoleLogEntry[] => {
  if (!instanceId) return []
  try {
    const raw = localStorage.getItem(`${STORAGE_PREFIX}${instanceId}`)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const savePersistedLogs = (instanceId: string | number, logs: ConsoleLogEntry[]) => {
  if (!instanceId) return
  try {
    const capped = (logs || []).slice(-150)
    localStorage.setItem(`${STORAGE_PREFIX}${instanceId}`, JSON.stringify(capped))
  } catch {
    // ignore storage quota error
  }
}

export const useConsoleStore = defineStore('console', () => {
  const authStore = useAuthStore()

  // 设备独立日志桶字典: instanceId -> ConsoleLogEntry[]
  const logsByInstance = ref<Record<string, ConsoleLogEntry[]>>({})

  // 实体元数据字典: instanceId -> { name: string, capabilities: EntityCapabilityMeta[], parentInstanceId?: string }
  const entityMetaMap = ref<Record<string, {
    name: string
    capabilities: EntityCapabilityMeta[]
    parentInstanceId?: string
  }>>({})

  let globalEventSource: EventSource | null = null
  let lastSignalKey = ''

  /**
   * 注册实体元数据（主设备与组件），方便后台静默流精准翻译中文名与参数
   */
  const registerEntityMeta = (
    instanceId: string | number,
    name: string,
    capabilities: any[],
    parentInstanceId?: string | number
  ) => {
    if (!instanceId) return
    const idKey = String(instanceId)
    entityMetaMap.value[idKey] = {
      name: name || '设备',
      capabilities: Array.isArray(capabilities) ? capabilities : [],
      parentInstanceId: parentInstanceId ? String(parentInstanceId) : undefined
    }
  }

  /**
   * 获取某设备的响应式日志列表（懒加载本地缓存）
   */
  const getLogs = (instanceId: string | number | undefined) => {
    if (!instanceId) return computed(() => [])
    const idKey = String(instanceId)
    if (!logsByInstance.value[idKey]) {
      logsByInstance.value[idKey] = loadPersistedLogs(idKey)
    }
    return computed(() => logsByInstance.value[idKey] || [])
  }

  /**
   * 向指定设备日志桶追加单条日志，并自动同步写入 localStorage
   */
  const appendLog = (
    instanceId: string | number,
    tag: ConsoleLogTag,
    type: ConsoleLogType,
    text: string,
    forwardParentId?: string | number
  ) => {
    if (!instanceId) return
    const idKey = String(instanceId)
    if (!logsByInstance.value[idKey]) {
      logsByInstance.value[idKey] = loadPersistedLogs(idKey)
    }

    const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    const entry: ConsoleLogEntry = { time, tag, type, text }
    logsByInstance.value[idKey].push(entry)
    savePersistedLogs(idKey, logsByInstance.value[idKey])

    // 如果属于组件且指定了父设备，也向父设备控制台同步一条
    if (forwardParentId && String(forwardParentId) !== idKey) {
      const parentKey = String(forwardParentId)
      if (!logsByInstance.value[parentKey]) {
        logsByInstance.value[parentKey] = loadPersistedLogs(parentKey)
      }
      logsByInstance.value[parentKey].push(entry)
      savePersistedLogs(parentKey, logsByInstance.value[parentKey])
    }
  }

  /**
   * 清空指定设备的日志
   */
  const clearLogs = (instanceId: string | number) => {
    if (!instanceId) return
    const idKey = String(instanceId)
    logsByInstance.value[idKey] = []
    localStorage.removeItem(`${STORAGE_PREFIX}${idKey}`)
  }

  const resolveCapabilityLabel = (instanceId: string, capName: string) => {
    if (!capName) return '设备操作'
    const meta = entityMetaMap.value[instanceId]
    if (meta?.capabilities?.length) {
      const match = meta.capabilities.find((c: any) => c.capabilityName === capName || c.displayName === capName)
      if (match?.displayName) return match.displayName
    }
    return capName
  }

  const formatParams = (instanceId: string, capName: string, params: Record<string, any>) => {
    if (!params || typeof params !== 'object' || Object.keys(params).length === 0) return ''
    const meta = entityMetaMap.value[instanceId]
    const capability = meta?.capabilities?.find((c: any) => c.capabilityName === capName || c.displayName === capName)
    const paramDefs: any[] = Array.isArray(capability?.parameters) ? capability.parameters : []
    const formatted: Record<string, any> = {}
    for (const [k, v] of Object.entries(params)) {
      const pDef = paramDefs.find((p: any) => (p.name === k || p.paramName === k || p.parameterName === k || p.displayName === k))
      const label = pDef?.displayName || pDef?.name || k
      formatted[label] = v
    }
    return JSON.stringify(formatted)
  }

  /**
   * 处理全局 SSE 推送的 signal 状态机事件
   */
  const handleIncomingSignal = (data: any) => {
    if (!data || !data.instanceId) return
    const idKey = String(data.instanceId)
    const { stateName, capabilityName, messageId, parameters } = data

    if (!stateName) return
    const s = String(stateName).toUpperCase()

    const signalKey = `${messageId || ''}_${idKey}_${s}`
    if (signalKey !== `_${idKey}_` && signalKey === lastSignalKey) {
      return
    }
    lastSignalKey = signalKey

    const meta = entityMetaMap.value[idKey]
    const targetName = meta?.name || '设备'
    const capLabel = resolveCapabilityLabel(idKey, capabilityName)
    const parentId = meta?.parentInstanceId

    if (s === 'SENT') {
      // 下发指令时前端已输出完整下发日志，此处忽略 SENT 初始冗余提示
    } else if (s === 'RECEIVED') {
      appendLog(idKey, '执行中', 'running', `Adapter 已确认接收【${targetName} · ${capLabel}】指令...`, parentId)
    } else if (s === 'RUNNING' || s === 'EXECUTING') {
      const paramStr = formatParams(idKey, capabilityName, parameters)
      appendLog(idKey, '执行中', 'running', `设备已确认并开始执行【${capLabel}】操作${paramStr ? '，参数: ' + paramStr : ''}...`, parentId)
    } else if (s === 'COMPLETED' || s === 'SUCCESS') {
      appendLog(idKey, '完成', 'success', `设备操作【${capLabel}】执行成功`, parentId)
    } else if (s === 'FAILED') {
      appendLog(idKey, '失败', 'fail', `指令【${capLabel}】响应超时或执行失败，状态机已复位`, parentId)
    } else if (s === 'ABORTED') {
      appendLog(idKey, '中止', 'fail', `指令【${capLabel}】已被安全机制或人工中止`, parentId)
    }
  }

  /**
   * 启动全局单通道 SSE 长连接（全系统只存在 1 条）
   */
  const initGlobalStream = () => {
    if (globalEventSource && globalEventSource.readyState !== EventSource.CLOSED) {
      return
    }
    try {
      const token = authStore.token
      const url = token
        ? `/api/device/instance/console/stream?token=${encodeURIComponent(token)}`
        : `/api/device/instance/console/stream`
      
      globalEventSource = new EventSource(url)
      globalEventSource.addEventListener('connected', () => {
        // SSE connection ready
      })
      globalEventSource.addEventListener('signal', (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data)
          handleIncomingSignal(data)
        } catch {
          // ignore parse error
        }
      })
      globalEventSource.onerror = () => {
        // EventSource 具备浏览器原生断线指数避让重连机制
      }
    } catch (e) {
      console.error('Global console SSE stream initialization failed:', e)
    }
  }

  const closeGlobalStream = () => {
    if (globalEventSource) {
      globalEventSource.close()
      globalEventSource = null
    }
  }

  return {
    logsByInstance,
    getLogs,
    appendLog,
    clearLogs,
    registerEntityMeta,
    initGlobalStream,
    closeGlobalStream
  }
})
