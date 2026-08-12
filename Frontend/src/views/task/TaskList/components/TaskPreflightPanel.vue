<template>
  <section class="preflight-panel">
    <div v-if="loading" class="preflight-loading">
      <el-skeleton :rows="3" animated />
      <span>正在检查任务前置条件...</span>
    </div>
    <template v-else-if="result">
      <div class="preflight-result-header" :class="{ ready: result.ready, blocked: !result.ready }">
        <el-icon :size="28"><component :is="result.ready ? 'CircleCheckFilled' : 'WarningFilled'" /></el-icon>
        <div>
          <strong>{{ result.ready ? '就绪' : '检查未通过' }}</strong>
          <span>{{ result.ready ? '任务可以创建并启动' : result.message || '请修复以下问题后重新检查' }}</span>
        </div>
      </div>
      <div v-if="result.checks?.length" class="preflight-checks">
        <div v-for="check in result.checks" :key="check.code || check.label" class="preflight-check-row">
          <el-icon :size="16" :class="check.passed ? 'check-passed' : 'check-failed'">
            <component :is="check.passed ? 'CircleCheck' : 'CircleClose'" />
          </el-icon>
          <div>
            <strong>{{ check.label || check.code }}</strong>
            <span v-if="!check.passed">{{ check.detail || check.message }}</span>
          </div>
          <el-tag v-if="check.severity === 'ERROR'" size="small" type="danger">阻断</el-tag>
          <el-tag v-else-if="check.severity === 'WARNING'" size="small" type="warning">提醒</el-tag>
        </div>
      </div>
      <div v-if="result.issues?.length" class="preflight-issues">
        <div v-for="issue in result.issues" :key="issue.code" class="preflight-issue-row">
          <el-tag size="small" :type="issue.blocking ? 'danger' : 'warning'">{{ issue.blocking ? '阻断' : '提醒' }}</el-tag>
          <div><strong>{{ issue.code }}</strong><span>{{ [issue.message, issue.suggestion].filter(Boolean).join('。') }}</span></div>
        </div>
      </div>
    </template>
    <el-empty v-else description="尚未执行前置检查" :image-size="48" />
    <div class="preflight-actions" v-if="!loading">
      <el-button class="btn-aliyun" @click="$emit('retry')" :loading="loading">重新检查</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { CircleCheck, CircleClose, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'

type Item = Record<string, any>
defineProps<{ result?: Item | null, loading?: boolean }>()
defineEmits<{ retry: [] }>()
</script>

<style scoped>
.preflight-panel{display:grid;gap:14px;padding:4px 0}.preflight-loading{display:grid;gap:10px;padding:20px 0}.preflight-loading span{color:#8a96a8;font-size:12px}.preflight-result-header{display:flex;align-items:center;gap:14px;padding:14px 16px;border-radius:6px;background:#f6f8fb}.preflight-result-header.ready{background:#edf8f2}.preflight-result-header.blocked{background:#fef2f2}.preflight-result-header>div{display:grid;gap:3px}.preflight-result-header strong{font-size:14px}.preflight-result-header span{color:#667488;font-size:11px}.preflight-checks{display:grid;gap:0;border:1px solid #e4e8ed;border-radius:6px}.preflight-check-row{display:flex;align-items:flex-start;gap:10px;padding:10px 12px;border-bottom:1px solid #ebedf1}.preflight-check-row:last-child{border-bottom:0}.preflight-check-row .el-icon{margin-top:1px;flex:none}.check-passed{color:#16a34a}.check-failed{color:#dc2626}.preflight-check-row>div{display:grid;gap:2px;min-width:0;flex:1}.preflight-check-row strong{font-size:12px}.preflight-check-row span{color:#768499;font-size:10px;line-height:1.5}.preflight-issues{display:grid;gap:6px}.preflight-issue-row{display:flex;align-items:flex-start;gap:8px;padding:9px 11px;border:1px solid #e7eaf0;border-radius:4px;background:#fcfdfe}.preflight-issue-row .el-tag{flex:none}.preflight-issue-row>div{display:grid;gap:3px}.preflight-issue-row strong{font-size:11px;color:#374151}.preflight-issue-row span{color:#6b7c93;font-size:10px}.preflight-actions{display:flex;justify-content:flex-end}
</style>
