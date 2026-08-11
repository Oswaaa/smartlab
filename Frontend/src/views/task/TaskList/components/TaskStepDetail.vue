<template>
  <section v-if="step" class="step-detail">
    <div class="detail-head"><div><strong>{{ step.nodeName || `节点 ${step.nodeIdRef ?? '-'}` }}</strong><span>层级 {{ step.stepDepth ?? 0 }}<template v-if="step.parentStepId != null"> · 父步骤 #{{ step.parentStepId }}</template></span></div><el-tag :type="statusType(step.nodeStatus)" effect="plain">{{ step.nodeStatus }}</el-tag></div>
    <el-descriptions :column="2" border size="small"><el-descriptions-item label="开始时间">{{ formatTime(step.startTime) }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ formatTime(step.endTime) }}</el-descriptions-item><el-descriptions-item label="耗时">{{ step.durationMs == null ? '-' : `${step.durationMs} ms` }}</el-descriptions-item><el-descriptions-item label="步骤深度">{{ step.stepDepth ?? 0 }}</el-descriptions-item></el-descriptions>
    <div class="detail-section"><h4>接口快照</h4><InterfaceSnapshotPanel :input-snapshot="step.interfaceInSnapshot" :output-snapshot="step.interfaceOutSnapshot" /></div>
    <div class="detail-section"><h4>变量空间</h4><div v-if="variables.length" class="variable-grid"><div v-for="[name,value] in variables" :key="name"><strong>{{ name }}</strong><WorkflowJsonValue :value="value" /></div></div><el-empty v-else description="暂无用户变量" :image-size="32" /></div>
    <div class="detail-section"><h4>数据端口</h4><div class="port-grid"><div><span>输入</span><WorkflowJsonValue :value="step.portInSnapshot || {}" /></div><div><span>输出</span><WorkflowJsonValue :value="step.portOutSnapshot || {}" /></div></div></div>
  </section>
  <el-empty v-else description="请选择一个已创建的执行步骤" :image-size="52" />
</template>
<script setup lang="ts">
import { computed } from 'vue'
import WorkflowJsonValue from '../../components/WorkflowJsonValue.vue'
import InterfaceSnapshotPanel from './InterfaceSnapshotPanel.vue'
import { visibleVariableEntries } from '../../../../utils/workflowExecution.js'
type Item = Record<string, any>
const props = defineProps<{ step?: Item|null }>()
const variables = computed(() => visibleVariableEntries(props.step?.variableSpace))
function statusType(status:string){ return ({RUNNING:'primary',SUCCEEDED:'success',FAILED:'danger',TERMINATING:'warning',TERMINATED:'warning',PENDING:'info'} as Record<string,string>)[status] || 'info' }
function formatTime(value:unknown){ if(!value) return '-'; const date=new Date(value as any); return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('zh-CN',{hour12:false}) }
</script>
<style scoped>
.step-detail{display:grid;gap:12px}.detail-head{display:flex;align-items:center;justify-content:space-between;padding:10px;border:1px solid #e2e7ed;background:#f7f9fb}.detail-head>div{display:grid;gap:2px}.detail-head strong{font-size:13px}.detail-head span{color:#7d8999;font-size:10px}.detail-section{display:grid;gap:8px}.detail-section h4{margin:0;color:#34445a;font-size:11px}.variable-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.variable-grid>div{display:grid;gap:4px}.variable-grid strong,.port-grid span{color:#687588;font-size:10px}.port-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.port-grid>div{display:grid;gap:4px}@media(max-width:760px){.variable-grid,.port-grid{grid-template-columns:1fr}}
</style>
