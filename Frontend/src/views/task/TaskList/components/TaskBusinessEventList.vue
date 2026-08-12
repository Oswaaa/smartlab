<template>
  <section class="business-events">
    <div v-for="event in events" :key="event.id" class="event-row"><span class="event-time">{{ formatTime(event.logTime) }}</span><el-tag size="small" effect="plain">{{ sourceLabel(event.sourceType) }}</el-tag><span :class="['event-level', String(event.logLevel || '').toLowerCase()]">{{ event.logLevel || 'INFO' }}</span><strong>{{ event.logInfo }}</strong></div>
    <el-empty v-if="!events.length" description="暂无业务事件" :image-size="44" />
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { businessExecutionEvents } from '../../../../utils/workflowExecution.js'
type Item=Record<string,any>
const props=defineProps<{ logs:Item[] }>()
const events=computed(()=>businessExecutionEvents(props.logs))
function sourceLabel(source:string){ return ({TASK:'任务',MANUAL:'人工操作',CONSTRAINT:'任务约束',ADAPTER:'设备适配'} as Record<string,string>)[source] || '节点' }
function formatTime(value:unknown){ if(!value)return '-';const date=new Date(value as any);return Number.isNaN(date.getTime())?String(value):date.toLocaleString('zh-CN',{hour12:false}) }
</script>
<style scoped>
.business-events{display:grid;border:1px solid #e3e7ed}.event-row{display:grid;grid-template-columns:150px 80px 60px minmax(0,1fr);align-items:center;gap:8px;padding:9px 11px;border-bottom:1px solid #edf0f3}.event-row:last-child{border-bottom:0}.event-time{color:#788598;font:10px Consolas,monospace}.event-level{font-size:9px;font-weight:700}.event-level.error{color:#c93333}.event-level.warn{color:#b36b00}.event-row strong{font-size:11px;font-weight:500}@media(max-width:720px){.event-row{grid-template-columns:1fr auto}.event-row strong{grid-column:1/-1}}
</style>
