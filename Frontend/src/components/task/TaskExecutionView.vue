<template>
  <section class="execution-view" v-loading="loading">
    <div v-if="view" class="execution-content">
      <div class="execution-header">
        <div class="execution-meta">
          <strong>{{ view.taskName }}</strong>
          <span>任务 #{{ view.taskId }}</span>
        </div>
        <el-tag :type="statusTagType(view.status)" size="large" effect="dark">{{ statusLabel(view.status) }}</el-tag>
      </div>

      <div class="execution-progress">
        <el-progress
          :percentage="progressPercent"
          :color="progressColor"
          :stroke-width="8"
        />
        <span class="progress-text">{{ view.completedNodes }} / {{ view.totalNodes }} 节点完成</span>
      </div>

      <div class="node-timeline">
        <div
          v-for="node in view.nodes"
          :key="node.stepId"
          class="node-card"
          :class="'status-' + (node.status || '').toLowerCase()"
        >
          <div class="node-card-header">
            <div class="node-identity">
              <span class="node-ref">#{{ node.nodeIdRef }}</span>
              <strong>{{ node.nodeName }}</strong>
              <small>{{ node.nodeType === 'DEV_NODE' ? '设备能力' : node.nodeType === 'SUBFLOW_NODE' ? '子流程' : node.nodeType || '功能节点' }}</small>
            </div>
            <el-tag size="small" :type="stepStatusTagType(node.status)" effect="plain">{{ node.status || '未知' }}</el-tag>
          </div>

          <div class="node-card-body">
            <div v-if="node.deviceName" class="node-detail-row">
              <label>绑定设备</label>
              <span>{{ node.deviceName }}</span>
            </div>
            <div v-if="node.capabilityName" class="node-detail-row">
              <label>执行能力</label>
              <span>{{ node.capabilityName }}</span>
            </div>
            <div v-if="node.startTime" class="node-detail-row">
              <label>开始时间</label>
              <span>{{ formatTime(node.startTime) }}</span>
            </div>
            <div v-if="node.durationMs != null" class="node-detail-row">
              <label>耗时</label>
              <span>{{ node.durationMs }}ms</span>
            </div>
            <div v-if="node.inputs && Object.keys(node.inputs).length" class="node-detail-row">
              <label>输入参数</label>
              <div class="json-preview">{{ formatJson(node.inputs) }}</div>
            </div>
            <div v-if="node.outputs && Object.keys(node.outputs).length" class="node-detail-row">
              <label>输出结果</label>
              <div class="json-preview">{{ formatJson(node.outputs) }}</div>
            </div>
            <div v-if="node.issues?.length" class="node-issues">
              <div v-for="issue in node.issues" :key="issue.code" class="issue-row">
                <el-tag size="small" :type="issue.blocking ? 'danger' : 'warning'">{{ issue.blocking ? '阻断' : '提醒' }}</el-tag>
                <span>{{ [issue.message, issue.suggestion].filter(Boolean).join('。') }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!view.nodes?.length" description="暂无执行步骤记录" :image-size="60" />
    </div>
    <el-empty v-else-if="!loading" description="无法加载执行视图" :image-size="60" />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { taskApi } from '../../services/taskApi.js'

type Item = Record<string, any>
const props = defineProps<{ taskId: number | null }>()
const view = ref<Item | null>(null)
const loading = ref(false)

const progressPercent = computed(() => {
  if (!view.value || !view.value.totalNodes) return 0
  return Math.round((view.value.completedNodes / view.value.totalNodes) * 100)
})

const progressColor = computed(() => {
  if (!view.value) return '#1677ff'
  if (view.value.status === 'FAILED') return '#dc2626'
  if (view.value.status === 'SUCCEEDED') return '#16a34a'
  return '#1677ff'
})

function statusTagType(status: string) {
  return ({ RUNNING: '', SUCCEEDED: 'success', FAILED: 'danger', PENDING: 'info', TERMINATED: 'warning' } as Record<string, string>)[status] || 'info'
}

function statusLabel(status: string) {
  return ({ RUNNING: '运行中', SUCCEEDED: '已完成', FAILED: '失败', PENDING: '排队中', TERMINATED: '已终止' } as Record<string, string>)[status] || status
}

function stepStatusTagType(status: string) {
  return ({ COMPLETED: 'success', RUNNING: '', FAILED: 'danger', PENDING: 'info', ABORTED: 'warning' } as Record<string, string>)[status] || 'info'
}

function formatTime(value: string | number) {
  if (!value) return '-'
  try {
    const date = new Date(value)
    if (isNaN(date.getTime())) return String(value)
    return date.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return String(value)
  }
}

function formatJson(value: any) {
  try { return JSON.stringify(value, null, 2) }
  catch { return String(value) }
}

async function fetchView() {
  if (!props.taskId) return
  loading.value = true
  try {
    const response = await taskApi.executionView(props.taskId)
    if (response.data?.success) {
      view.value = response.data.data
    }
  } catch {
    view.value = null
  } finally {
    loading.value = false
  }
}

watch(() => props.taskId, () => { if (props.taskId) fetchView() })
onMounted(() => { if (props.taskId) fetchView() })
</script>

<style scoped>
.execution-view{min-height:200px}.execution-content{display:grid;gap:18px}.execution-header{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border:1px solid #e4e8ed;border-radius:6px;background:#fafbfc}.execution-meta{display:grid;gap:3px}.execution-meta strong{font-size:15px}.execution-meta span{color:#8a93a6;font-size:11px}.execution-progress{display:flex;align-items:center;gap:14px}.progress-text{color:#667488;font-size:12px;white-space:nowrap}.node-timeline{display:grid;gap:10px}.node-card{border:1px solid #e4e8ed;border-radius:6px;background:#fff;overflow:hidden}.node-card.status-completed{border-left:4px solid #16a34a}.node-card.status-running{border-left:4px solid #1677ff}.node-card.status-failed{border-left:4px solid #dc2626}.node-card-header{display:flex;align-items:center;justify-content:space-between;padding:10px 14px;border-bottom:1px solid #eef0f4;background:#f7f8fa}.node-identity{display:flex;align-items:center;gap:8px}.node-ref{min-width:28px;color:#8a93a6;font-size:11px;font-family:Consolas,monospace}.node-identity strong{font-size:13px}.node-identity small{color:#94a3b8;font-size:10px}.node-card-body{display:grid;gap:0;padding:4px 14px 12px}.node-detail-row{display:grid;grid-template-columns:80px 1fr;gap:4px;padding:6px 0;border-bottom:1px solid #f0f1f3}.node-detail-row:last-child{border-bottom:0}.node-detail-row label{color:#64748b;font-size:11px}.node-detail-row span{color:#1f2937;font-size:12px}.json-preview{max-height:120px;overflow:auto;padding:6px 8px;border:1px solid #e4e8ed;border-radius:4px;background:#f8f9fb;font-family:Consolas,monospace;font-size:10px;line-height:1.5;white-space:pre-wrap}.node-issues{margin-top:8px;display:grid;gap:6px}.issue-row{display:flex;align-items:flex-start;gap:6px;padding:6px 8px;border:1px solid #fdeaea;border-radius:4px;background:#fef8f8}.issue-row span{color:#7f1d1d;font-size:11px}
</style>
