<template>
  <section class="trigger-editor">
    <div class="editor-heading"><div><strong>触发器</strong><span>条件首次满足时执行当前接口上的动作</span></div><el-button v-if="editable" class="btn-aliyun" size="small" :disabled="!actions.length" @click="addTrigger">添加触发器</el-button></div>
    <template v-for="(trigger,index) in triggers" :key="trigger._systemKey || index">
      <article class="trigger-card" :class="{ 'system-trigger-card': isSystemItem(trigger) }">
        <header class="trigger-head"><strong>触发器 {{ index + 1 }}</strong><span v-if="isSystemItem(trigger)" class="system-lock">系统预置 · 只读</span><el-button v-else-if="canEditTrigger(trigger)" class="btn-aliyun-danger-link" link @click="removeTrigger(index)">删除</el-button></header>
        <div class="condition-block">
          <div v-if="conditionItems(trigger).length > 1" class="condition-logic-bar"><strong>满足全部条件</strong><span>以下条件同时成立时触发</span></div>
          <div v-for="(condition,conditionIndex) in conditionItems(trigger)" :key="conditionIndex" class="condition-row-shell">
            <div class="condition-rule-row">
              <span class="rule-label">{{ conditionIndex ? '且' : '当' }}</span>
              <el-select :model-value="condition.object" placeholder="观测对象" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index,conditionIndex,'object',$event)">
                <el-option-group v-for="group in conditionGroups(condition)" :key="group.label" :label="group.label"><el-option v-for="item in group.options" :key="item.value" :label="item.label" :value="item.value" /></el-option-group>
              </el-select>
              <el-select :model-value="condition.operator" placeholder="比较" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index,conditionIndex,'operator',$event)"><el-option v-for="operator in operatorsFor(condition)" :key="operator" :label="operatorLabel(operator)" :value="operator" /></el-select>
              <el-select v-if="condition.operator === 'IN' && thresholdOptions(condition).length" :model-value="thresholdArray(condition)" multiple :disabled="!canEditTrigger(trigger)" placeholder="比较值" @update:model-value="updateThresholdList(index,conditionIndex,$event)"><el-option v-for="value in thresholdOptions(condition)" :key="String(value)" :label="String(value)" :value="value" /></el-select>
              <el-select v-else-if="thresholdOptions(condition).length" :model-value="condition.threshold" placeholder="比较值" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index,conditionIndex,'threshold',$event)"><el-option v-for="value in thresholdOptions(condition)" :key="String(value)" :label="String(value)" :value="value" /></el-select>
              <el-select v-else-if="condition.operator === 'IN'" :model-value="thresholdArray(condition)" multiple allow-create filterable default-first-option :disabled="!canEditTrigger(trigger)" placeholder="输入多个比较值" @update:model-value="updateThresholdList(index,conditionIndex,$event)" />
              <WorkflowTypedValueInput v-else-if="conditionVariable(condition)" :model-value="condition.threshold" :data-type="conditionVariable(condition).dataType" :disabled="!canEditTrigger(trigger)" @update:model-value="updateCondition(index,conditionIndex,'threshold',$event)" />
              <el-input v-else :model-value="displayJson(condition.threshold)" placeholder="比较值" :disabled="!canEditTrigger(trigger)" @change="updateThreshold(index,conditionIndex,$event)" />
            </div>
            <el-button v-if="canRemoveTriggerCondition(trigger)" class="condition-remove btn-aliyun-danger-link" link @click="removeTriggerCondition(index,conditionIndex)">移除</el-button>
          </div>
          <el-button v-if="canEditTrigger(trigger)" class="condition-add btn-aliyun-link" link @click="addTriggerCondition(index)">添加条件</el-button>
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
          <el-select :model-value="trigger.action?.payload?.targetName" placeholder="更新目标" :disabled="!canEditTrigger(trigger)" @update:model-value="changeUpdateTarget(index,$event)"><el-option v-for="target in updateTargets(trigger)" :key="target" :label="target" :value="target" /></el-select>
          </template>
        </div>
        <div v-if="trigger.action?.actionName === 'UPDATE' && trigger.action?.payload?.updateType !== 'NODE_LIFECYCLE'" class="action-extra-row">
          <span class="rule-label action">值</span><el-select :model-value="updateSource(trigger)" :disabled="!canEditTrigger(trigger)" @update:model-value="changeUpdateSource(index,$event)"><el-option label="直接常量" value="VALUE" /><el-option label="计算表达式" value="EXPRESSION" /></el-select>
          <WorkflowExpressionEditor v-if="updateSource(trigger)==='EXPRESSION'" :model-value="trigger.action?.payload?.valueExpression" :readonly="!canEditTrigger(trigger)" :variables="node.internalVariables || []" @update:model-value="updatePayload(index,'valueExpression',$event)" />
          <WorkflowTypedValueInput v-else-if="updateVariableDataType(trigger)" :model-value="trigger.action?.payload?.value" :data-type="updateVariableDataType(trigger)" :disabled="!canEditTrigger(trigger)" @update:model-value="updatePayload(index,'value',$event)" />
          <el-input v-else disabled placeholder="请先选择内部变量" />
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
import WorkflowTypedValueInput from './WorkflowTypedValueInput.vue'
import { addWorkflowTriggerCondition, customTriggerActionNames, emptyWorkflowUpdateValue, isSystemItem, removeWorkflowTriggerCondition, workflowTriggerConditions, workflowUpdateVariableDataType } from '../../../../../utils/workflowNodeDefinition.js'
type Item=Record<string,any>
const props=withDefaults(defineProps<{node:Item,interfaceItem:Item,editable?:boolean}>(),{editable:false})
const emit=defineEmits<{'update:interface':[value:Item]}>()
const actions=computed<string[]>(()=>customTriggerActionNames(props.node))
const availableActions=computed(()=>actions.value.filter(name=>name!=='EMIT'||props.interfaceItem.direction==='OUT'))
const triggers=computed<Item[]>(()=>props.interfaceItem.bindingTriggers||[])
function conditionItems(trigger:Item){return workflowTriggerConditions(trigger.condition)}
function isDeviceCommandInput(){return props.node.nodeType==='DEV_NODE'&&props.interfaceItem.direction==='IN'&&props.interfaceItem.interfaceType==='STATE'&&(props.interfaceItem.allowedSignals||[]).includes('CMD_STATE')}
function conditionGroups(condition:Item){return[
 ...(props.interfaceItem.direction==='IN'?[{label:'接口',options:[{label:'当前接口接收信号',value:'signalName'},...(isDeviceCommandInput()?[{label:'指令执行状态',value:'payload.stateName'}]:[])]}]:[]),
 ...(props.node.lifecycle?.states?.length?[{label:'节点',options:[{label:'节点生命周期',value:'nodeLifecycleState'}]}]:[]),
 ...((props.node.internalVariables||[]).length?[{label:'内部变量',options:props.node.internalVariables.map((item:Item)=>({label:`${item.name} · ${item.dataType}`,value:item.name}))}]:[]),
]}
function canEditTrigger(trigger:Item){return props.editable&&!isSystemItem(trigger)}
function canRemoveTriggerCondition(trigger:Item){return canEditTrigger(trigger)&&conditionItems(trigger).length>1}
function publish(bindingTriggers:Item[]){emit('update:interface',{...props.interfaceItem,bindingTriggers})}
function edit(index:number,fn:(value:Item)=>Item){publish(triggers.value.map((item,i)=>i===index?fn(item):item))}
function defaultAction(name=availableActions.value[0]||'UPDATE'){if(name==='EMIT')return{actionName:'EMIT',payload:{targetInterfaceName:props.interfaceItem.name,signalName:props.interfaceItem.allowedSignals?.[0]||''}};const targetName=props.node.internalVariables?.[0]?.name||'';return{actionName:'UPDATE',payload:{updateType:'INTERNAL_VARIABLE',targetName,value:emptyWorkflowUpdateValue(workflowUpdateVariableDataType(props.node,targetName))}}}
function defaultCondition(){if(props.interfaceItem.direction==='IN')return{object:'signalName',operator:'=',threshold:props.interfaceItem.allowedSignals?.[0]||''};if(props.node.lifecycle?.states?.length)return{object:'nodeLifecycleState',operator:'=',threshold:props.node.lifecycle.states[0]};const variable=props.node.internalVariables?.[0];return{object:variable?.name||'',operator:'=',threshold:variable?.dataType==='BOOLEAN'?false:variable?.dataType==='STRING'?'':0}}
function addTrigger(){publish([...triggers.value,{condition:defaultCondition(),action:defaultAction()}])}
function removeTrigger(index:number){publish(triggers.value.filter((_item,i)=>i!==index))}
function addTriggerCondition(index:number){edit(index,t=>({...t,condition:addWorkflowTriggerCondition(t.condition,defaultCondition())}))}
function removeTriggerCondition(index:number,conditionIndex:number){edit(index,t=>({...t,condition:removeWorkflowTriggerCondition(t.condition,conditionIndex)}))}
function conditionVariable(condition:Item){return (props.node.internalVariables||[]).find((item:Item)=>item.name===condition?.object)||null}
function conditionType(condition:Item){if(condition?.object==='signalName'||condition?.object==='nodeLifecycleState'||condition?.object==='payload.stateName')return'ENUM';return conditionVariable(condition)?.dataType||'STRING'}
function operatorsFor(condition:Item){const type=conditionType(condition);if(type==='ENUM')return['='];if(type==='BOOLEAN')return['=','!='];if(type==='STRING')return['=','!=','IN'];return['>','<','>=','<=','=','!=']}
function defaultThreshold(condition:Item,operator='='){const object=condition?.object;if(object==='signalName')return props.interfaceItem.allowedSignals?.[0]||'';if(object==='nodeLifecycleState')return props.node.lifecycle?.states?.[0]||'';if(object==='payload.stateName')return'COMPLETED';const variable=conditionVariable(condition);if(!variable)return'';if(operator==='IN')return[];if(variable.dataType==='BOOLEAN')return false;if(variable.dataType==='INTEGER')return 0;if(variable.dataType==='DOUBLE')return 0;return''}
function updateCondition(index:number,conditionIndex:number,field:string,value:unknown){edit(index,t=>{const conditions=workflowTriggerConditions(t.condition).map(item=>structuredClone(item));const next={...conditions[conditionIndex],[field]:value};if(field==='object'){const operator=operatorsFor(next).includes(next.operator)?next.operator:'=';next.operator=operator;next.threshold=defaultThreshold(next,operator)}else if(field==='operator'){if(value==='IN'&&!Array.isArray(next.threshold))next.threshold=[];if(value!=='IN'&&Array.isArray(next.threshold))next.threshold=next.threshold[0]??defaultThreshold(next,String(value))}conditions[conditionIndex]=next;return{...t,condition:conditions.length===1?conditions[0]:{logic:'AND',conditions}}})}
function updateThreshold(index:number,conditionIndex:number,value:string){updateCondition(index,conditionIndex,'threshold',parseTyped(value))}
function thresholdArray(condition:Item){return Array.isArray(condition?.threshold)?condition.threshold:condition?.threshold===''||condition?.threshold===undefined||condition?.threshold===null?[]:[condition.threshold]}
function updateThresholdList(index:number,conditionIndex:number,values:string[]){const condition=conditionItems(triggers.value[index])[conditionIndex];const variable=conditionVariable(condition);const typed=values.map(value=>{if(variable?.dataType==='INTEGER'){const parsed=Number(value);return Number.isInteger(parsed)?parsed:value}if(variable?.dataType==='DOUBLE'){const parsed=Number(value);return Number.isFinite(parsed)?parsed:value}return value});updateCondition(index,conditionIndex,'threshold',typed)}
function changeAction(index:number,name:string){edit(index,t=>({...t,action:defaultAction(name)}))}
function updatePayload(index:number,field:string,value:unknown){edit(index,t=>({...t,action:{...t.action,payload:{...t.action?.payload,targetInterfaceName:t.action?.actionName==='EMIT'?props.interfaceItem.name:t.action?.payload?.targetInterfaceName,[field]:value}}}))}
function changeUpdateType(index:number,type:string){edit(index,t=>{const targetName=props.node.internalVariables?.[0]?.name||'';return{...t,action:{actionName:'UPDATE',payload:type==='NODE_LIFECYCLE'?{updateType:type,targetName:props.node.lifecycle?.states?.[0]||''}:{updateType:type,targetName,value:emptyWorkflowUpdateValue(workflowUpdateVariableDataType(props.node,targetName))}}}})}
function updateTargets(trigger:Item){return trigger.action?.payload?.updateType==='NODE_LIFECYCLE'?(props.node.lifecycle?.states||[]):(props.node.internalVariables||[]).map((item:Item)=>item.name)}
function updateVariableDataType(trigger:Item){return workflowUpdateVariableDataType(props.node,trigger.action?.payload?.targetName)}
function changeUpdateTarget(index:number,targetName:string){edit(index,t=>{const payload={...t.action?.payload,targetName};if(payload.updateType==='INTERNAL_VARIABLE'&&!Object.hasOwn(payload,'valueExpression'))payload.value=emptyWorkflowUpdateValue(workflowUpdateVariableDataType(props.node,targetName));return{...t,action:{...t.action,payload}}})}
function updateSource(trigger:Item){return Object.hasOwn(trigger.action?.payload||{},'valueExpression')?'EXPRESSION':'VALUE'}
function changeUpdateSource(index:number,source:string){edit(index,t=>{const{value:_v,valueExpression:_e,...payload}=t.action?.payload||{};return{...t,action:{...t.action,payload:source==='EXPRESSION'?{...payload,valueExpression:''}:{...payload,value:emptyWorkflowUpdateValue(workflowUpdateVariableDataType(props.node,payload.targetName))}}}})}
function thresholdOptions(condition:Item){if(condition?.object==='signalName')return props.interfaceItem.allowedSignals||[];if(condition?.object==='nodeLifecycleState')return props.node.lifecycle?.states||[];if(condition?.object==='payload.stateName')return['IDLE','SENT','RUNNING','COMPLETED','FAILED','ABORTING','ABORTED'];return[]}
function parseTyped(value:string){try{return JSON.parse(value)}catch{return value}}
function displayJson(value:unknown){return typeof value==='string'?value:value===undefined?'':JSON.stringify(value)}
function operatorLabel(value:string){return({'=':'等于','!=':'不等于','>':'大于','<':'小于','>=':'大于等于','<=':'小于等于',IN:'属于'} as Item)[value]||value}
</script>
<style scoped>
.trigger-editor{display:grid;border-top:1px solid #e5e5e5}.editor-heading{min-height:50px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-bottom:1px solid #e5e5e5}.editor-heading>div{display:grid;gap:2px}.editor-heading strong{font-size:13px;font-weight:500}.editor-heading span{color:#8c8c8c;font-size:10px}.trigger-card{display:grid;gap:10px;padding:0 14px 14px;border-bottom:1px solid #e5e5e5}.system-trigger-card{background:#fafafa}.trigger-head{height:40px;display:flex;align-items:center;gap:10px;border-bottom:1px solid #f0f0f0}.trigger-head strong{font-size:11px;font-weight:500}.trigger-head .el-button{margin-left:auto}.system-lock{padding:2px 7px;background:#e6f4ff;color:#1677ff;font-size:9px}.condition-block{display:grid;gap:8px}.condition-logic-bar{height:30px;display:flex;align-items:center;gap:10px;padding:0 10px;border-left:2px solid #1677ff;background:#f5f9ff}.condition-logic-bar strong{font-size:11px;font-weight:500;color:#1677ff}.condition-logic-bar span{font-size:10px;color:#8c8c8c}.condition-row-shell{display:flex;align-items:start;gap:6px}.condition-rule-row,.action-rule-row,.action-extra-row{display:grid;align-items:start;gap:8px}.condition-rule-row{flex:1;grid-template-columns:32px minmax(180px,240px) 96px minmax(130px,190px)}.action-rule-row{grid-template-columns:32px 110px minmax(180px,240px) minmax(130px,190px)}.action-extra-row{grid-template-columns:32px 110px minmax(260px,1fr)}.condition-remove{height:30px;color:#ff4d4f}.condition-add{justify-self:start;margin-left:40px}.rule-label{height:30px;display:grid;place-items:center;background:#e6f4ff;color:#1677ff;font-size:12px}.rule-label.action{background:#f6ffed;color:#389e0d}.host-interface-summary{height:30px;display:flex;align-items:center;gap:8px;padding:0 10px;border:1px solid #d9d9d9;background:#fafafa;box-sizing:border-box}.host-interface-summary span{color:#8c8c8c;font-size:10px}.host-interface-summary strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:500}@media(max-width:820px){.condition-rule-row,.action-rule-row{grid-template-columns:32px 1fr 96px}.condition-rule-row>*:last-child,.action-rule-row>*:last-child{grid-column:2/-1}.action-extra-row{grid-template-columns:32px 110px 1fr}}
.system-lock{margin-left:auto}
</style>
