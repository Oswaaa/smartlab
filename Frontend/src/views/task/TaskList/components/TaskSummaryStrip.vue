<template>
  <section class="task-summary-strip" aria-label="任务状态概览">
    <button v-for="item in items" :key="item.status" type="button" :class="{ active: activeStatus === item.status }" @click="emit('select-status', item.status)">
      <span><i v-if="item.dot" :class="['summary-dot', item.dot]"></i>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.note }}</small>
    </button>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
type Summary = { total:number, pending:number, running:number, succeeded:number, failed:number, terminated:number }
const props = defineProps<{ summary: Summary, activeStatus: string }>()
const emit = defineEmits<{ 'select-status': [status: string] }>()
const items = computed(() => [
  { status:'', label:'全部任务', value:props.summary.total, note:'所有执行记录', dot:'' },
  { status:'PENDING', label:'排队中', value:props.summary.pending, note:'等待启动', dot:'pending' },
  { status:'RUNNING', label:'运行中', value:props.summary.running, note:'正在执行', dot:'running' },
  { status:'SUCCEEDED', label:'已完成', value:props.summary.succeeded, note:'执行成功', dot:'success' },
  { status:'FAILED', label:'失败任务', value:props.summary.failed, note:`另 ${props.summary.terminated} 项已终止`, dot:'danger' },
])
</script>
<style scoped>
.task-summary-strip{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));border-bottom:1px solid #eceef2;background:#fff}.task-summary-strip>button{position:relative;display:grid;grid-template-columns:1fr auto;align-content:center;gap:2px 10px;min-width:0;min-height:76px;padding:12px 20px;border:0;border-right:1px solid #f0f1f3;color:inherit;text-align:left;background:#fff;cursor:pointer}.task-summary-strip>button:last-child{border-right:0}.task-summary-strip>button:hover{background:#f8fafc}.task-summary-strip>button.active{background:#f1f7ff}.task-summary-strip>button.active:after{position:absolute;right:12px;bottom:0;left:12px;height:2px;background:#1677ff;content:''}.task-summary-strip span{display:flex;align-items:center;gap:6px;color:#6b7280;font-size:12px}.task-summary-strip strong{grid-row:1/3;grid-column:2;align-self:center;color:#111827;font-size:22px}.task-summary-strip small{overflow:hidden;color:#9ca3af;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.summary-dot{width:7px;height:7px;border-radius:50%;background:#94a3b8}.summary-dot.running{background:#1677ff;box-shadow:0 0 0 3px rgba(22,119,255,.12)}.summary-dot.success{background:#16a34a}.summary-dot.danger{background:#dc2626}@media(max-width:1024px){.task-summary-strip{grid-template-columns:repeat(5,minmax(140px,1fr));overflow-x:auto}}
</style>
