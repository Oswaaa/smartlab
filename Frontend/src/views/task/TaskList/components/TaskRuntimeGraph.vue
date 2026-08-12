<template>
  <section class="runtime-graph">
    <VueFlow :nodes="flowNodes" :edges="flowEdges" :nodes-draggable="false" :nodes-connectable="false" :elements-selectable="true" fit-view-on-init :min-zoom="0.45" :max-zoom="1.4" @node-click="handleNodeClick">
      <Background pattern-color="#d9e1eb" :gap="18" />
      <template #node-runtime="{ data }">
        <article :class="['runtime-node', `status-${data.status.toLowerCase()}`, { selected: data.stepId === selectedStepId }]">
          <header><span>{{ nodeGlyph(data) }}</span><div><strong>{{ data.name }}</strong><small>{{ nodeTypeLabel(data) }}</small></div></header>
          <div class="status-line"><i></i><b>{{ statusLabel(data.status) }}</b><span v-if="data.stepId">步骤 #{{ data.stepId }}</span><span v-else>尚未创建步骤</span></div>
          <div v-if="data.children?.length" class="child-steps"><span>子流程步骤 {{ data.children.length }}</span><small v-for="child in data.children.slice(0, 3)" :key="child.id">{{ child.nodeName || child.nodeIdRef }} · {{ child.nodeStatus }}</small></div>
        </article>
      </template>
    </VueFlow>
    <div class="graph-legend"><span v-for="status in statuses" :key="status"><i :class="`status-${status.toLowerCase()}`"></i>{{ statusLabel(status) }}</span></div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { buildRuntimeGraph } from '../../../../utils/workflowExecution.js'
type Item=Record<string,any>
const props=withDefaults(defineProps<{ workflow?:Item|null, steps?:Item[], selectedStepId?:number|null }>(),{workflow:null,steps:()=>[],selectedStepId:null})
const emit=defineEmits<{ 'select-step':[stepId:number|null, node:Item] }>()
const graph=computed(()=>buildRuntimeGraph(props.workflow||{},props.steps))
const statuses=['WAITING','PENDING','RUNNING','SUCCEEDED','FAILED','TERMINATED']
const flowNodes=computed(()=>graph.value.nodes.map((node:Item,index:number)=>({id:`runtime:${node.name}`,type:'runtime',position:node.position||{x:60+(index%2)*300,y:60+Math.floor(index/2)*170},data:node,selectable:true,draggable:false})))
const flowEdges=computed(()=>graph.value.edges.map((edge:Item)=>({id:edge.id,source:`runtime:${edge.source}`,target:`runtime:${edge.target}`,animated:false,style:{stroke:edge.kind==='PORT'?'#7c4dce':'#4a7fb8',strokeWidth:edge.kind==='PORT'?1.5:2,strokeDasharray:edge.kind==='PORT'?'6 4':undefined}})))
function handleNodeClick({node}:{node:Item}){emit('select-step',node.data.stepId??null,node.data)}
function nodeGlyph(node:Item){if(node.nodeType==='DEV_NODE')return 'D';if(node.nodeType==='SUBFLOW_NODE')return '↳';return ({START:'▶',END:'■',BRANCH:'◇',AGGREGATE:'◆'} as Record<string,string>)[node.functionType]||'N'}
function nodeTypeLabel(node:Item){if(node.nodeType==='DEV_NODE')return '设备能力';if(node.nodeType==='SUBFLOW_NODE')return '子流程';return ({START:'开始',END:'结束',BRANCH:'条件分支',AGGREGATE:'聚合'} as Record<string,string>)[node.functionType]||'功能节点'}
function statusLabel(status:string){return ({WAITING:'等待创建',PENDING:'待执行',RUNNING:'运行中',SUCCEEDED:'已完成',FAILED:'失败',TERMINATING:'终止中',TERMINATED:'已终止'} as Record<string,string>)[status]||status}
</script>
<style scoped>
.runtime-graph{position:relative;height:520px;overflow:hidden;border:1px solid #dfe5ec;background:#f8fafc}.runtime-graph :deep(.vue-flow){height:100%}.runtime-node{width:220px;overflow:hidden;border:1px solid #cfd8e3;border-left:4px solid #94a3b8;border-radius:6px;background:#fff;box-shadow:0 3px 10px rgba(24,39,58,.08)}.runtime-node.selected{box-shadow:0 0 0 3px rgba(22,119,255,.2),0 3px 10px rgba(24,39,58,.08)}.runtime-node.status-running{border-left-color:#1677ff}.runtime-node.status-succeeded{border-left-color:#16a34a}.runtime-node.status-failed{border-left-color:#dc2626}.runtime-node.status-terminating,.runtime-node.status-terminated{border-left-color:#d97706}.runtime-node header{display:flex;align-items:center;gap:8px;padding:9px 10px;border-bottom:1px solid #edf0f3}.runtime-node header>span{display:grid;place-items:center;width:24px;height:24px;border-radius:4px;background:#e9f1fb;color:#2e6fae;font-size:10px;font-weight:800}.runtime-node header>div{display:grid;gap:2px;min-width:0}.runtime-node strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.runtime-node small{color:#8490a0;font-size:9px}.status-line{display:grid;grid-template-columns:8px auto 1fr;align-items:center;gap:6px;padding:8px 10px}.status-line i{width:7px;height:7px;border-radius:50%;background:#94a3b8}.status-running .status-line i{background:#1677ff}.status-succeeded .status-line i{background:#16a34a}.status-failed .status-line i{background:#dc2626}.status-line b{font-size:10px}.status-line span{text-align:right;color:#8490a0;font-size:9px}.child-steps{display:grid;gap:3px;padding:7px 10px;border-top:1px dashed #dce3eb;background:#f8fafc}.child-steps>span{color:#51667d;font-size:9px;font-weight:600}.graph-legend{position:absolute;right:10px;bottom:10px;z-index:5;display:flex;flex-wrap:wrap;gap:8px;padding:6px 8px;border:1px solid #dfe5ec;background:rgba(255,255,255,.94)}.graph-legend span{display:flex;align-items:center;gap:4px;color:#697789;font-size:9px}.graph-legend i{width:7px;height:7px;border-radius:50%;background:#94a3b8}.graph-legend i.status-running{background:#1677ff}.graph-legend i.status-succeeded{background:#16a34a}.graph-legend i.status-failed{background:#dc2626}.graph-legend i.status-terminated{background:#d97706}
</style>
