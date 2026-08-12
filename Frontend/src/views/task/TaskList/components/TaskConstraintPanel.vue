<template>
  <section class="task-constraint-panel">
    <div class="section-heading"><div><strong>任务级约束</strong><span>随当前任务保存，只能观测或操作本任务及其绑定设备</span></div><el-button class="btn-aliyun-cta" @click="emit('edit')">添加任务约束</el-button></div>
    <el-empty v-if="!rules.length" description="未配置任务级约束" :image-size="48"/>
    <div v-for="(rule,index) in rules" :key="index" class="constraint-row" :class="{ 'needs-review': reviews[index] }">
      <div>
        <div class="constraint-name"><strong>{{ rule.ruleName }}</strong><el-tag v-if="reviews[index]" size="small" type="warning" effect="plain">需要复核</el-tag></div>
        <code>{{ rule.expression }}</code>
        <span>{{ Object.keys(rule.bindings||{}).length }} 个变量 · {{ (rule.violationActions||[]).length }} 个动作</span>
        <span v-if="reviews[index]" class="review-message">{{ reviews[index] }}</span>
      </div>
      <div><el-button class="btn-aliyun-link" link @click="emit('edit', index)">编辑</el-button><el-button class="btn-aliyun-danger-link" link @click="emit('remove', index)">删除</el-button></div>
    </div>
  </section>
</template>
<script setup lang="ts">
type Item = Record<string, any>
withDefaults(defineProps<{ rules: Item[], reviews?: Array<string | null> }>(), { reviews:()=>[] })
const emit = defineEmits<{ edit:[index?:number], remove:[index:number] }>()
</script>
<style scoped>
.task-constraint-panel{display:grid;gap:10px}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-heading>div{display:grid;gap:2px}.section-heading strong{font-size:13px}.section-heading span{color:#7f8b9b;font-size:10px}.constraint-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid #e4e8ed;background:#fafbfc}.constraint-row.needs-review{border-color:#e6bd72;background:#fffaf0}.constraint-row>div:first-child{display:grid;gap:3px}.constraint-name{display:flex;align-items:center;gap:8px}.constraint-row strong{font-size:12px}.constraint-row code{color:#3c5777;font-size:10px}.constraint-row span{color:#8994a3;font-size:9px}.constraint-row .review-message{color:#a86600}
</style>
