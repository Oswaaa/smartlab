<template>
  <div class="task-filter-bar">
    <div class="task-filter-fields">
      <el-input :model-value="keyword" clearable placeholder="搜索任务名称或说明" @update:model-value="emit('update:keyword', $event)" @keyup.enter="emit('query')" />
      <el-select :model-value="status" clearable placeholder="全部状态" @update:model-value="emit('update:status', $event)" @change="emit('query')">
        <el-option label="排队中" value="PENDING"/><el-option label="运行中" value="RUNNING"/><el-option label="已完成" value="SUCCEEDED"/><el-option label="失败" value="FAILED"/><el-option label="已终止" value="TERMINATED"/>
      </el-select>
      <el-button class="btn-aliyun-cta" @click="emit('query')">查询</el-button><el-button v-if="hasFilters" class="btn-aliyun" @click="emit('reset')">重置</el-button>
    </div>
    <span class="result-meta">共 {{ total }} 条<span v-if="lastUpdatedAt"> · 更新于 {{ lastUpdatedAt }}</span></span>
  </div>
</template>
<script setup lang="ts">
defineProps<{ keyword: string, status: string, total: number, lastUpdatedAt?: string, hasFilters?: boolean }>()
const emit = defineEmits<{ 'update:keyword':[value:string], 'update:status':[value:string], query:[], reset:[] }>()
</script>
<style scoped>
.task-filter-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:60px;padding:12px 20px;border-bottom:1px solid #eceef2;background:#fff}.task-filter-fields{display:flex;align-items:center;gap:8px;min-width:0}.task-filter-fields :deep(.el-input){width:300px}.task-filter-fields :deep(.el-select){width:150px}.result-meta{color:#8a93a6;font-size:11px;white-space:nowrap}@media(max-width:1024px){.task-filter-bar{align-items:stretch}.task-filter-fields{flex:1;flex-wrap:wrap}.task-filter-fields :deep(.el-input){flex:1;width:220px}}
</style>
