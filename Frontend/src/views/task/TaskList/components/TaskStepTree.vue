<template><div class="step-tree"><TaskStepBranch v-for="step in tree" :key="step.id" :step="step" :selected-id="selectedId" @select="emit('select', $event)" /><el-empty v-if="!tree.length" description="暂无执行步骤" :image-size="40" /></div></template>
<script setup lang="ts">
import { computed, defineComponent, h } from 'vue'
import { buildStepTree } from '../../../../utils/workflowExecution.js'
type Item = Record<string, any>
const props = defineProps<{ steps: Item[], selectedId?: number|null }>()
const emit = defineEmits<{ select:[step:Item] }>()
const tree = computed(() => buildStepTree(props.steps))
const TaskStepBranch = defineComponent({
  name:'TaskStepBranch', props:{ step:{ type:Object, required:true }, selectedId:{ type:Number, default:null } }, emits:['select'],
  setup(branchProps,{emit:branchEmit}) { return () => h('div',{class:'step-branch'},[
    h('button',{class:['step-item',{active:branchProps.step.id===branchProps.selectedId}],onClick:()=>branchEmit('select',branchProps.step)},[
      h('span',{class:'step-depth'},String(branchProps.step.stepDepth ?? 0)),h('strong',branchProps.step.nodeName || `节点 ${branchProps.step.nodeIdRef ?? '-'}`),h('small',branchProps.step.nodeStatus || 'PENDING')
    ]),
    branchProps.step.children?.length ? h('div',{class:'step-children'},branchProps.step.children.map((child:Item)=>h(TaskStepBranch,{key:child.id,step:child,selectedId:branchProps.selectedId,onSelect:(value:Item)=>branchEmit('select',value)}))) : null,
  ]) }
})
</script>
<style scoped>
.step-tree{display:grid;align-content:start;gap:3px}.step-branch{display:grid;gap:3px}.step-item{display:grid;grid-template-columns:24px minmax(0,1fr) auto;align-items:center;gap:7px;width:100%;padding:8px;border:1px solid transparent;background:#f7f9fb;text-align:left;cursor:pointer}.step-item:hover,.step-item.active{border-color:#b9d3ef;background:#eaf3fc}.step-depth{display:grid;place-items:center;width:20px;height:20px;border-radius:50%;background:#dde8f5;color:#456686;font-size:9px}.step-item strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.step-item small{color:#738094;font-size:9px}.step-children{display:grid;gap:3px;margin-left:18px;padding-left:8px;border-left:1px dashed #b8c7d8}
</style>
