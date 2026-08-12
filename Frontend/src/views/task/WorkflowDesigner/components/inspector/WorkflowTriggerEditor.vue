<template>
  <section class="trigger-editor">
    <div class="editor-heading"><div><strong>触发器</strong><span>条件首次满足时执行当前接口上的动作</span></div><el-button v-if="editable" class="btn-aliyun" size="small" :disabled="!actions.length" @click="addTrigger">添加触发器</el-button></div>
    <template v-for="(trigger,index) in triggers" :key="trigger._systemKey || index">
      <article class="trigger-card" :class="{ 'system-trigger-card': isSystemItem(trigger) }">
        <header class="trigger-head"><strong>触发器 {{ index + 1 }}</strong><span v-if="isSystemItem(trigger)" class="system-lock">系统预置 · 只读</span><el-button v-else-if="canEditTrigger(trigger)" class="btn-aliyun-danger-link" link @click="removeTrigger(index)">删除</el-button></header>
        <div class="condition-rule-row">
          <span class="rule-label">当</span>
          <el-select :model-value="trigger.condition?.object" placeholder="观测对象" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index,'object',$event)">
            <el-option-group v-for="group in conditionGroups(trigger)" :key="group.label" :label="group.label"><el-option v-for="item in group.options" :key="item.value" :label="item.label" :value="item.value" /></el-option-group>
          </el-select>
          <el-select :model-value="trigger.condition?.operator" placeholder="比较" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index,'operator',$event)"><el-option v-for="operator in operators" :key="operator" :label="operatorLabel(operator)" :value="operator" /></el-select>
          <el-select v-if="thresholdOptions(trigger).length" :model-value="trigger.condition?.threshold" placeholder="比较值" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index,'threshold',$event)"><el-option v-for="value in thresholdOptions(trigger)" :key="String(value)" :label="String(value)" :value="value" /></el-select>
          <el-input v-else :model-value="displayJson(trigger.condition?.threshold)" placeholder="比较值" :disabled="!canEditTrigger(trigger)" @change="updateThreshold(index,$event)" />
        </div>
        <div class="action-rule-row">
          <span class="rule-label action">则</span>
          <el-select :model-value="trigger.action?.actionName" placeholder="动作" :disabled="!canEditTrigger(trigger)" @update:model-value="changeAction(index,$event)"><el-option v-for="name in availableActions" :key="name" :label="name" :value="name" /></el-select>
          <template v-if="trigger.action?.actionName === 'EMIT'">
            <div class="host-interface-summary"><span>发送接口</span><strong>{{ interfaceItem.name }}</strong></div>
            <el-select :model-value="trigger.action?.payload?.signalName" placeholder="发送信号" :disabled="!canEditTrigger(trigger)" @update:model-value="updatePayload(index,'signalName',$event)"><el-option v-for="signal in interfaceItem.allowedSignals || []" :key="signal" :label="signal" :value="signal" /></el-select>
          </template>
          <template v-else-if="trigger.action?.actionName === 'UPDATE'">
            <el-select :model-value="trigger.action?.payload?.updateType" placeholder="更新类型" :disabled="!canEditTrigger(trigger)" @update:model-value="changeUpdateType(index,$event)"><el-option label="内部变量" value="INTERNAL_VARIABLE" /><el-option label="节点生命周期" value="NODE_LIFECYCLE" /></el-select>
            <el-select :model-value="trigger.action?.payload?.targetName" placeholder="更新目标" :disabled="!canEditTrigger(trigger)" @update:model-value="updatePayload(index,'targetName',$event)"><el-option v-for="target in updateTargets(trigger)" :key="target" :label="target" :value="target" /></el-select>
          </template>
        </div>
        <div v-if="trigger.action?.actionName === 'UPDATE' && trigger.action?.payload?.updateType !== 'NODE_LIFECYCLE'" class="action-extra-row">
          <span class="rule-label action">值</span><el-select :model-value="updateSource(trigger)" :disabled="!canEditTrigger(trigger)" @update:model-value="changeUpdateSource(index,$event)"><el-option label="直接常量" value="VALUE" /><el-option label="计算表达式" value="EXPRESSION" /></el-select>
          <WorkflowExpressionEditor v-if="updateSource(trigger)==='EXPRESSION'" :model-value="trigger.action?.payload?.valueExpression" :readonly="!canEditTrigger(trigger)" :variables="node.internalVariables || []" @update:model-value="updatePayload(index,'valueExpression',$event)" />
          <el-input v-else :model-value="displayJson(trigger.action?.payload?.value)" placeholder="输入常量" :disabled="!canEditTrigger(trigger)" @change="updateConstant(index,$event)" />
        </div>
      </article>
    </template>
    <WorkflowConfigurationEmpty v-if="!triggers.length" title="尚未配置触发器" description="触发器仅在条件首次满足时执行对应动作。" :action-label="editable && actions.length ? '添加触发器' : ''" @action="addTrigger" />
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import WorkflowConfigurationEmpty from './WorkflowConfigurationEmpty.vue'
import WorkflowExpressionEditor from './WorkflowExpressionEditor.vue'
import { isSystemItem } from '../../../../../utils/workflowNodeDefinition.js'
type Item=Record<string,any>
const props=withDefaults(defineProps<{node:Item,interfaceItem:Item,editable?:boolean}>(),{editable:false})
const emit=defineEmits<{'update:interface':[value:Item]}>()
const actions=computed<string[]>(()=>props.node.actions||[])
const availableActions=computed(()=>actions.value.filter(name=>name!=='EMIT'||props.interfaceItem.direction==='OUT'))
const triggers=computed<Item[]>(()=>props.interfaceItem.bindingTriggers||[])
const operators=['>','<','>=','<=','=','!=','IN']
function conditionGroups(trigger:Item){return[
 {label:'接口',options:[{label:'当前接口信号',value:'signalName'},...(trigger.condition?.object==='payload.stateName'?[{label:'设备命令状态',value:'payload.stateName'}]:[])]},
 ...(props.node.lifecycle?.states?.length?[{label:'节点',options:[{label:'节点生命周期',value:'nodeLifecycleState'}]}]:[]),
 ...((props.node.internalVariables||[]).length?[{label:'内部变量',options:props.node.internalVariables.map((item:Item)=>({label:`${item.name} · ${item.dataType}`,value:item.name}))}]:[]),
]}
function canEditTrigger(trigger:Item){return props.editable&&!isSystemItem(trigger)}
function publish(bindingTriggers:Item[]){emit('update:interface',{...props.interfaceItem,bindingTriggers})}
function edit(index:number,fn:(value:Item)=>Item){publish(triggers.value.map((item,i)=>i===index?fn(item):item))}
function defaultAction(name=availableActions.value[0]||'UPDATE'){if(name==='EMIT')return{actionName:'EMIT',payload:{targetInterfaceName:props.interfaceItem.name,signalName:props.interfaceItem.allowedSignals?.[0]||''}};return{actionName:'UPDATE',payload:{updateType:'INTERNAL_VARIABLE',targetName:props.node.internalVariables?.[0]?.name||'',value:null}}}
function addTrigger(){publish([...triggers.value,{condition:{object:'signalName',operator:'=',threshold:props.interfaceItem.allowedSignals?.[0]||''},action:defaultAction()}])}
function removeTrigger(index:number){publish(triggers.value.filter((_item,i)=>i!==index))}
function updateCondition(index:number,field:string,value:unknown){edit(index,t=>({...t,condition:{...t.condition,[field]:value}}))}
function updateThreshold(index:number,value:string){updateCondition(index,'threshold',parseTyped(value))}
function changeAction(index:number,name:string){edit(index,t=>({...t,action:defaultAction(name)}))}
function updatePayload(index:number,field:string,value:unknown){edit(index,t=>({...t,action:{...t.action,payload:{...t.action?.payload,targetInterfaceName:t.action?.actionName==='EMIT'?props.interfaceItem.name:t.action?.payload?.targetInterfaceName,[field]:value}}}))}
function changeUpdateType(index:number,type:string){edit(index,t=>({...t,action:{actionName:'UPDATE',payload:type==='NODE_LIFECYCLE'?{updateType:type,targetName:props.node.lifecycle?.states?.[0]||''}:{updateType:type,targetName:props.node.internalVariables?.[0]?.name||'',value:null}}}))}
function updateTargets(trigger:Item){return trigger.action?.payload?.updateType==='NODE_LIFECYCLE'?(props.node.lifecycle?.states||[]):(props.node.internalVariables||[]).map((item:Item)=>item.name)}
function updateSource(trigger:Item){return Object.hasOwn(trigger.action?.payload||{},'valueExpression')?'EXPRESSION':'VALUE'}
function changeUpdateSource(index:number,source:string){edit(index,t=>{const{value:_v,valueExpression:_e,...payload}=t.action?.payload||{};return{...t,action:{...t.action,payload:source==='EXPRESSION'?{...payload,valueExpression:''}:{...payload,value:null}}}})}
function updateConstant(index:number,value:string){updatePayload(index,'value',parseTyped(value))}
function thresholdOptions(trigger:Item){if(trigger.condition?.object==='signalName')return props.interfaceItem.allowedSignals||[];if(trigger.condition?.object==='nodeLifecycleState')return props.node.lifecycle?.states||[];return[]}
function parseTyped(value:string){try{return JSON.parse(value)}catch{return value}}
function displayJson(value:unknown){return typeof value==='string'?value:value===undefined?'':JSON.stringify(value)}
function operatorLabel(value:string){return({'=':'等于','!=':'不等于','>':'大于','<':'小于','>=':'大于等于','<=':'小于等于',IN:'属于'} as Item)[value]||value}
</script>
<style scoped>
.trigger-editor{display:grid;border-top:1px solid #e5e5e5}.editor-heading{min-height:50px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-bottom:1px solid #e5e5e5}.editor-heading>div{display:grid;gap:2px}.editor-heading strong{font-size:13px;font-weight:500}.editor-heading span{color:#8c8c8c;font-size:10px}.trigger-card{display:grid;gap:10px;padding:0 14px 14px;border-bottom:1px solid #e5e5e5}.system-trigger-card{background:#fafafa}.trigger-head{height:40px;display:flex;align-items:center;gap:10px;border-bottom:1px solid #f0f0f0}.trigger-head strong{font-size:11px;font-weight:500}.trigger-head .el-button{margin-left:auto}.system-lock{padding:2px 7px;background:#e6f4ff;color:#1677ff;font-size:9px}.condition-rule-row,.action-rule-row,.action-extra-row{display:grid;align-items:start;gap:8px}.condition-rule-row{grid-template-columns:32px minmax(180px,240px) 96px minmax(130px,190px)}.action-rule-row{grid-template-columns:32px 110px minmax(180px,240px) minmax(130px,190px)}.action-extra-row{grid-template-columns:32px 110px minmax(260px,1fr)}.rule-label{height:30px;display:grid;place-items:center;background:#e6f4ff;color:#1677ff;font-size:12px}.rule-label.action{background:#f6ffed;color:#389e0d}.host-interface-summary{height:30px;display:flex;align-items:center;gap:8px;padding:0 10px;border:1px solid #d9d9d9;background:#fafafa;box-sizing:border-box}.host-interface-summary span{color:#8c8c8c;font-size:10px}.host-interface-summary strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:500}@media(max-width:820px){.condition-rule-row,.action-rule-row{grid-template-columns:32px 1fr 96px}.condition-rule-row>*:last-child,.action-rule-row>*:last-child{grid-column:2/-1}.action-extra-row{grid-template-columns:32px 110px 1fr}}
.system-lock{margin-left:auto}
</style>
