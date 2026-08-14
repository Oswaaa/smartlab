<template>
  <section class="runtime-graph">
    <VueFlow :nodes="flowNodes" :edges="flowEdges" :nodes-draggable="false" :nodes-connectable="false" :elements-selectable="true" fit-view-on-init :min-zoom="0.45" :max-zoom="1.4" @node-click="handleNodeClick">
      <Background pattern-color="#d9e1eb" :gap="18" />
      <template #node-runtime="{ data }">
        <div class="runtime-node-shell" :style="runtimeNodeStyle(data)">
          <div v-for="(item,index) in runtimeInterfaces(data,'IN')" :key="item.name" class="runtime-handle-row input" :style="handlePosition(index)">
            <Handle :id="interfaceHandleId(item.name)" type="target" :position="Position.Left" class="runtime-handle" />
            <span :title="item.name">{{ item.name }}</span>
          </div>
          <article :class="['runtime-node', `status-${data.status.toLowerCase()}`, { selected: data.stepId === selectedStepId }]">
            <header><span>{{ nodeGlyph(data) }}</span><div><strong>{{ data.name }}</strong><small>{{ nodeTypeLabel(data) }}</small></div></header>
            <div class="status-line"><i></i><b>{{ statusLabel(data.status) }}</b><span v-if="data.stepId">步骤 #{{ data.stepId }}</span><span v-else>尚未创建步骤</span></div>
            <div v-if="data.children?.length" class="child-steps"><span>子流程步骤 {{ data.children.length }}</span><small v-for="child in data.children.slice(0, 3)" :key="child.id">{{ child.nodeName || child.nodeIdRef }} · {{ child.nodeStatus }}</small></div>
          </article>
          <div v-for="(item,index) in runtimeInterfaces(data,'OUT')" :key="item.name" class="runtime-handle-row output" :style="handlePosition(index)">
            <span :title="item.name">{{ item.name }}</span>
            <Handle :id="interfaceHandleId(item.name)" type="source" :position="Position.Right" class="runtime-handle" />
          </div>
        </div>
      </template>
    </VueFlow>
    <div class="graph-legend"><span v-for="status in statuses" :key="status"><i :class="`status-${status.toLowerCase()}`"></i>{{ statusLabel(status) }}</span></div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { buildRuntimeGraph } from '../../../../utils/workflowExecution.js'
import { interfaceHandleId } from '../../../../utils/workflowCanvas.js'
type Item=Record<string,any>
const props=withDefaults(defineProps<{ workflow?:Item|null, steps?:Item[], selectedStepId?:number|null }>(),{workflow:null,steps:()=>[],selectedStepId:null})
const emit=defineEmits<{ 'select-step':[stepId:number|null, node:Item] }>()
const graph=computed(()=>buildRuntimeGraph(props.workflow||{},props.steps))
const statuses=['WAITING','PENDING','RUNNING','SUCCEEDED','FAILED','TERMINATING','TERMINATED']
const flowNodes=computed(()=>graph.value.nodes.map((node:Item,index:number)=>({id:`runtime:${node.name}`,type:'runtime',position:node.position||{x:60+(index%2)*300,y:60+Math.floor(index/2)*170},data:node,selectable:true,draggable:false})))
const flowEdges=computed(()=>graph.value.edges.map((edge:Item)=>({id:edge.id,source:`runtime:${edge.source}`,target:`runtime:${edge.target}`,sourceHandle:edge.sourceHandle,targetHandle:edge.targetHandle,type:'smoothstep',pathOptions:{offset:28,borderRadius:4},animated:edge.kind==='INTERFACE'&&edge.used&&edge.targetStatus==='RUNNING',class:edge.used?'used-runtime-edge':'',style:{stroke:edge.kind==='PORT'?'#7c4dce':edge.used?'#52c41a':'#7892b0',strokeWidth:edge.used?2.6:edge.kind==='PORT'?1.5:2,strokeDasharray:edge.kind==='PORT'?'6 4':undefined}})))
function handleNodeClick({node}:{node:Item}){emit('select-step',node.data.stepId??null,node.data)}
function nodeGlyph(node:Item){if(node.nodeType==='DEV_NODE')return 'D';if(node.nodeType==='SUBFLOW_NODE')return '↳';return ({START:'▶',END:'■',BRANCH:'◇',AGGREGATE:'◆'} as Record<string,string>)[node.functionType]||'N'}
function nodeTypeLabel(node:Item){if(node.nodeType==='DEV_NODE')return '设备能力';if(node.nodeType==='SUBFLOW_NODE')return '子流程';return ({START:'开始',END:'结束',BRANCH:'条件分支',AGGREGATE:'聚合'} as Record<string,string>)[node.functionType]||'功能节点'}
function statusLabel(status:string){return ({WAITING:'等待创建',PENDING:'待执行',RUNNING:'运行中',SUCCEEDED:'已完成',FAILED:'失败',TERMINATING:'终止中',TERMINATED:'已终止'} as Record<string,string>)[status]||status}
function runtimeInterfaces(node:Item,direction:string){return(node.interfaces||[]).filter((item:Item)=>item.interfaceType==='WORKFLOW'&&item.direction===direction)}
function handlePosition(index:number){return{top:`${24+index*20}px`}}
function runtimeNodeStyle(node:Item){return{minHeight:`${Math.max(78,Math.max(runtimeInterfaces(node,'IN').length,runtimeInterfaces(node,'OUT').length)*20+42)}px`}}
</script>
<style scoped>
.runtime-graph{position:relative;height:520px;overflow:hidden;border:1px solid #dfe5ec;background:#f8fafc}.runtime-graph :deep(.vue-flow){height:100%}.runtime-graph :deep(.vue-flow__edge.used-runtime-edge .vue-flow__edge-path){filter:drop-shadow(0 0 2px rgba(82,196,26,.45))}.runtime-node-shell{position:relative;width:224px;min-height:78px}.runtime-node{width:224px;min-height:inherit;overflow:hidden;border:1px solid #cfd8e3;border-left:4px solid #94a3b8;border-radius:4px;background:#fff;box-shadow:0 3px 10px rgba(24,39,58,.08);box-sizing:border-box;transition:border-color .18s ease,box-shadow .18s ease}.runtime-node.selected{box-shadow:0 0 0 3px rgba(22,119,255,.2),0 3px 10px rgba(24,39,58,.08)}.runtime-node.status-waiting{border-left-color:#cbd5e1}.runtime-node.status-pending{border-left-color:#94a3b8}.runtime-node.status-running{border-left-color:#1677ff}.runtime-node.status-succeeded{border-left-color:#52c41a}.runtime-node.status-failed{border-left-color:#ff4d4f}.runtime-node.status-terminating{border-left-color:#fadb14}.runtime-node.status-terminated{border-left-color:#fa8c16}.runtime-node header{display:flex;align-items:center;gap:8px;padding:9px 10px;border-bottom:1px solid #edf0f3}.runtime-node header>span{display:grid;place-items:center;width:24px;height:24px;border-radius:3px;background:#e9f1fb;color:#2e6fae;font-size:10px;font-weight:800}.runtime-node header>div{display:grid;gap:2px;min-width:0}.runtime-node strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.runtime-node small{color:#8490a0;font-size:9px}.status-line{display:grid;grid-template-columns:8px auto 1fr;align-items:center;gap:6px;padding:8px 10px}.status-line i{width:7px;height:7px;border-radius:50%;background:#94a3b8}.status-waiting .status-line i{background:#cbd5e1}.status-pending .status-line i{background:#94a3b8}.status-running .status-line i{background:#1677ff}.status-succeeded .status-line i{background:#52c41a}.status-failed .status-line i{background:#ff4d4f}.status-terminating .status-line i{background:#fadb14}.status-terminated .status-line i{background:#fa8c16}.status-line b{font-size:10px}.status-line span{text-align:right;color:#8490a0;font-size:9px}.child-steps{display:grid;gap:3px;padding:7px 10px;border-top:1px dashed #dce3eb;background:#f8fafc}.child-steps>span{color:#51667d;font-size:9px;font-weight:600}.runtime-handle-row{position:absolute;z-index:4;height:18px;display:flex;align-items:center;color:#7a8797;font-size:8px;pointer-events:none}.runtime-handle-row.input{left:0}.runtime-handle-row.output{right:0}.runtime-handle-row span{max-width:58px;overflow:hidden;padding:1px 3px;background:rgba(255,255,255,.94);text-overflow:ellipsis;white-space:nowrap}.runtime-handle-row.input span{margin-left:8px}.runtime-handle-row.output span{margin-right:8px}.runtime-handle{width:9px!important;height:9px!important;border:2px solid #fff!important;background:#7892b0!important;box-shadow:0 0 0 1px #7892b0;pointer-events:none}.runtime-handle.vue-flow__handle-left{left:-5px!important}.runtime-handle.vue-flow__handle-right{right:-5px!important}.graph-legend{position:absolute;right:10px;bottom:10px;z-index:5;display:flex;max-width:440px;flex-wrap:wrap;gap:8px;padding:6px 8px;border:1px solid #dfe5ec;background:rgba(255,255,255,.94)}.graph-legend span{display:flex;align-items:center;gap:4px;color:#697789;font-size:9px}.graph-legend i{width:7px;height:7px;border-radius:50%;background:#cbd5e1}.graph-legend i.status-pending{background:#94a3b8}.graph-legend i.status-running{background:#1677ff}.graph-legend i.status-succeeded{background:#52c41a}.graph-legend i.status-failed{background:#ff4d4f}.graph-legend i.status-terminating{background:#fadb14}.graph-legend i.status-terminated{background:#fa8c16}
</style>
