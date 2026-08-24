<template>
  <section class="business-events">
    <div v-for="event in events" :key="event.id" class="event-row">
      <span class="event-time mono">{{ formatLogDateTime(event.logTime) }}</span>
      <span class="event-source">{{ sourceLabel(event.sourceType) }}</span>
      <span :class="['event-level', String(event.logLevel || '').toLowerCase()]">{{ event.logLevel || 'INFO' }}</span>
      <span class="event-text">{{ event.logInfo }}</span>
    </div>
    <div v-if="!events.length" class="events-empty">暂无业务事件</div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { formatLogDateTime } from '../../../../utils/formatLogTime.js'
import { businessExecutionEvents } from '../../../../utils/workflowExecution.js'
type Item = Record<string, any>
const props = defineProps<{ logs: Item[] }>()
const events = computed(() => businessExecutionEvents(props.logs))
function sourceLabel(source: string) {
  return ({ TASK: '任务', MANUAL: '人工操作', CONSTRAINT: '任务约束', ADAPTER: '设备适配' } as Record<string, string>)[source] || '节点'
}
</script>
<style scoped>
.business-events { min-height: 100%; }
.event-row {
  display: grid;
  grid-template-columns: 176px 72px 52px minmax(0, 1fr);
  align-items: start;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}
.event-row:hover { background: var(--sl-bg-hover, #f8fafc); }
.event-time { color: var(--sl-text-secondary, #64748b); font-size: 12px; line-height: 18px; white-space: nowrap; }
.mono { font-family: var(--sl-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace); }
.event-source { color: var(--sl-text-secondary, #64748b); font-size: 11.5px; line-height: 18px; }
.event-level { font-size: 11px; font-weight: 600; line-height: 18px; color: var(--sl-text-secondary, #64748b); }
.event-level.error { color: var(--sl-danger, #dc2626); }
.event-level.warn { color: var(--sl-warning, #d97706); }
.event-text { color: var(--sl-text-body, #334155); font-size: 12.5px; line-height: 20px; }
.events-empty {
  padding: 48px 16px;
  text-align: center;
  color: var(--sl-text-disabled, #94a3b8);
  font-size: 12.5px;
}
@media (max-width: 720px) {
  .event-row { grid-template-columns: 1fr auto; }
  .event-text { grid-column: 1 / -1; }
}
</style>
