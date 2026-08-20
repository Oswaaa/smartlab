<template>
  <div class="task-filter-bar">
    <div class="task-filter-fields">
      <el-input
        :model-value="keyword"
        clearable
        size="small"
        placeholder="搜索任务名称或说明..."
        class="search-input"
        @update:model-value="emit('update:keyword', $event)"
        @keyup.enter="emit('query')"
      />
      <el-select
        :model-value="status"
        clearable
        size="small"
        placeholder="全部状态"
        class="status-select"
        @update:model-value="emit('update:status', $event)"
        @change="emit('query')"
      >
        <el-option label="排队中" value="PENDING" />
        <el-option label="运行中" value="RUNNING" />
        <el-option label="已完成" value="SUCCEEDED" />
        <el-option label="失败" value="FAILED" />
        <el-option label="已终止" value="TERMINATED" />
      </el-select>
      <button class="btn-aliyun-cta" type="button" @click="emit('query')">查询</button>
      <button v-if="hasFilters" class="btn-aliyun" type="button" @click="emit('reset')">重置</button>
    </div>
    <span class="result-meta">
      共 <strong class="mono-count">{{ total }}</strong> 条记录
      <span v-if="lastUpdatedAt" class="update-time"> · 更新于 {{ lastUpdatedAt }}</span>
    </span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  keyword: string
  status: string
  total: number
  lastUpdatedAt?: string
  hasFilters?: boolean
}>()

const emit = defineEmits<{
  'update:keyword': [value: string]
  'update:status': [value: string]
  query: []
  reset: []
}>()
</script>

<style scoped>
.task-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: var(--sl-bg-surface, #ffffff);
  flex-shrink: 0;
}

.task-filter-fields {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.search-input {
  width: 280px;
}

.status-select {
  width: 130px;
}

.result-meta {
  color: var(--sl-text-secondary, #64748b);
  font-size: 11.5px;
  white-space: nowrap;
}

.mono-count {
  font-family: var(--sl-font-mono, monospace);
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}

.update-time {
  color: var(--sl-text-disabled, #94a3b8);
}

@media (max-width: 1024px) {
  .task-filter-bar {
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }
  .task-filter-fields {
    flex: 1;
    flex-wrap: wrap;
  }
  .search-input {
    flex: 1;
    width: 200px;
  }
}
</style>
