<template>
  <section v-if="visible && node" class="node-inspector">
    <div class="node-identity">
      <span :class="['node-type-mark', nodeTypeClass]">{{ nodeTypeGlyph }}</span>
      <div><strong>{{ node.name }}</strong><small>{{ nodeGuidance.title }}</small></div>
      <el-tag v-if="errors.length" type="danger" effect="plain" size="small">{{ errors.length }} 错误</el-tag>
    </div>
    <el-form label-position="top" class="node-form"><el-form-item label="节点名称"><el-input v-model="nameDraft" maxlength="80" @change="rename" /></el-form-item></el-form>
    <div class="runtime-note"><b>运行时</b><span>{{ nodeGuidance.runtime }}</span></div>
    <el-tabs v-model="activeTab" class="node-tabs">
      <el-tab-pane name="basic">
        <template #label>业务配置 <small v-if="tabErrors.basic">{{ tabErrors.basic }}</small></template>
        <section class="basic-configuration">
          <template v-if="node.nodeType === 'DEV_NODE'">
            <el-alert title="这里只选择设备模型能力；具体设备实例在创建任务时绑定。" type="info" :closable="false" />
            <el-form label-position="top"><el-form-item label="执行能力"><el-select :model-value="node.capability?.capabilityName" filterable :disabled="!contractReady" placeholder="选择运行时调用的能力" @update:model-value="changeCapability"><el-option v-for="item in deviceCapabilities" :key="item.capabilityName" :label="item.displayName || item.capabilityName" :value="item.capabilityName" /></el-select></el-form-item><el-form-item v-for="parameter in selectedCapability?.parameters || []" :key="parameter.name" :label="`${parameter.displayName || parameter.name} · ${parameter.dataType}`"><WorkflowTypedValueInput :model-value="node.capability?.capabilityParameters?.[parameter.name]" :data-type="parameter.dataType" @update:model-value="updateParameter(parameter.name, $event)" /></el-form-item></el-form>
          </template>
          <template v-else-if="node.nodeType === 'SUBFLOW_NODE'">
            <dl class="property-list"><div><dt>引用流程模型</dt><dd>ID {{ node.subFlowModelId || '-' }}</dd></div><div><dt>进入条件</dt><dd>上游执行完成后创建子流程执行上下文</dd></div><div><dt>完成条件</dt><dd>子流程到达 END 后继续激活下游节点</dd></div></dl>
          </template>
          <template v-else-if="node.functionType === 'BRANCH'">
            <el-form label-position="top"><el-form-item label="条件表达式"><el-input :model-value="node.expression" type="textarea" :rows="4" placeholder="例如：temperature >= 80" @update:model-value="updateBasic('expression', $event)" /></el-form-item></el-form>
            <el-alert title="至少连接两条下游执行路径；表达式在分支节点被激活时求值。" type="warning" :closable="false" />
          </template>
          <div v-else class="semantic-card"><strong>{{ nodeGuidance.title }}</strong><span>{{ nodeGuidance.detail }}</span></div>
        </section>
      </el-tab-pane>
      <el-tab-pane name="variables"><template #label>数据与端口 <small v-if="tabErrors.variables">{{ tabErrors.variables }}</small></template><WorkflowVariablesPortsPanel :node="node" :device-attributes="deviceAttributes" :port-connections="portConnections" @update:node="emit('update:node', $event)" @update:port-connections="emit('update:portConnections', $event)" @remove-port-request="emit('remove-port-request', $event)" /></el-tab-pane>
      <el-tab-pane name="triggers"><template #label>触发与动作 <small v-if="tabErrors.triggers">{{ tabErrors.triggers }}</small></template><WorkflowTriggersActionsPanel :node="node" @update:node="emit('update:node', $event)" /></el-tab-pane>
      <el-tab-pane name="contracts">
        <template #label>接口与端口 <small v-if="tabErrors.contracts">{{ tabErrors.contracts }}</small></template>
        <WorkflowInterfacesPortsPanel
          :node="node"
          @update:node="emit('update:node', $event)"
          @remove-port-request="emit('remove-port-request', $event)"
        />
      </el-tab-pane>
      <el-tab-pane name="lifecycle">
        <template #label>生命周期 <small v-if="tabErrors.lifecycle">{{ tabErrors.lifecycle }}</small></template>
        <WorkflowLifecyclePanel :lifecycle="node.lifecycle" />
      </el-tab-pane>
    </el-tabs>
    <div class="node-footer"><span>删除会同时移除关联的执行流和数据流</span><el-button class="btn-aliyun-danger-link" text @click="emit('remove-node')">删除节点</el-button></div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import WorkflowTypedValueInput from './WorkflowTypedValueInput.vue'
import WorkflowVariablesPortsPanel from './WorkflowVariablesPortsPanel.vue'
import WorkflowTriggersActionsPanel from './WorkflowTriggersActionsPanel.vue'
import WorkflowInterfacesPortsPanel from './WorkflowInterfacesPortsPanel.vue'
import WorkflowLifecyclePanel from './WorkflowLifecyclePanel.vue'
import { replaceCapability } from '../../../utils/workflowNodeDefinition.js'
type Item = Record<string, any>
const props = withDefaults(defineProps<{ visible: boolean, node: Item | null, contractReady?: boolean, errors?: Item[], deviceCapabilities?: Item[], deviceAttributes?: Item[], portConnections?: Item[] }>(), { contractReady: false, errors: () => [], deviceCapabilities: () => [], deviceAttributes: () => [], portConnections: () => [] })
const emit = defineEmits<{ rename: [name: string], 'update:node': [node: Item], 'update:portConnections': [connections: Item[]], 'remove-port-request': [portName: string], 'remove-node': [], close: [] }>()
const activeTab = ref('basic')
const nameDraft = ref('')
watch(() => props.node?.name, value => { nameDraft.value = value || '' }, { immediate: true })
const selectedCapability = computed(() => props.deviceCapabilities.find(item => item.capabilityName === props.node?.capability?.capabilityName))
const tabErrors = computed(() => props.errors.reduce((counts: Record<string, number>, error: Item) => { const path = error.path || ''; const tab = path.startsWith('internalVariables') || path.startsWith('ports') ? 'variables' : path.startsWith('actions') || path.startsWith('interfaces') && path.includes('bindingTriggers') ? 'triggers' : path.startsWith('interfaces') || (path.startsWith('ports') && error.path?.includes('port')) ? 'contracts' : path.startsWith('lifecycle') ? 'lifecycle' : 'basic'; counts[tab] += 1; return counts }, { basic: 0, variables: 0, triggers: 0, contracts: 0, lifecycle: 0 }))
const guidanceMap:Record<string,Item> = {
  DEV_NODE:{ title:'设备能力调用', runtime:'任务创建时绑定一个匹配该模型的设备实例，再按参数调用所选能力。', detail:'选择设备能力并配置参数。' },
  SUBFLOW_NODE:{ title:'子流程调用', runtime:'创建独立的子流程执行上下文，子流程结束后当前节点才完成。', detail:'引用目标流程模型的入口与出口。' },
  START:{ title:'流程唯一入口', runtime:'任务启动后首先激活 START，再沿执行流激活下游节点。', detail:'START 只能有下游连接，不能有上游连接。' },
  END:{ title:'流程唯一出口', runtime:'根流程到达 END 后任务完成；子流程到达 END 后返回父流程。', detail:'END 只能有上游连接，不能有下游连接。' },
  BRANCH:{ title:'条件路由', runtime:'节点激活时计算表达式，并选择符合条件的下游执行路径。', detail:'至少配置两条下游路径。' },
  AGGREGATE:{ title:'多路汇聚', runtime:'等待全部上游路径完成后，仅激活一次下游节点。', detail:'至少需要两条上游执行路径。' }
}
const guidanceKey = computed(() => props.node?.nodeType === 'FUNC_NODE' ? props.node?.functionType : props.node?.nodeType)
const nodeGuidance = computed(() => guidanceMap[guidanceKey.value || ''] || { title:'节点配置', runtime:'按节点契约执行。', detail:'配置节点业务参数。' })
const nodeTypeClass = computed(() => String(guidanceKey.value || '').toLowerCase())
const nodeTypeGlyph = computed(() => ({ DEV_NODE:'D', SUBFLOW_NODE:'↳', START:'▶', END:'■', BRANCH:'◇', AGGREGATE:'◆' } as Record<string,string>)[guidanceKey.value || ''] || 'N')
function rename() { const name = nameDraft.value.trim(); if (name && name !== props.node?.name) emit('rename', name); else nameDraft.value = props.node?.name || '' }
function updateBasic(field: string, value: unknown) { if (props.node) emit('update:node', { ...props.node, [field]: value }) }
function changeCapability(name: string) { if (!props.contractReady) return; const capability = props.deviceCapabilities.find(item => item.capabilityName === name); if (props.node && capability) emit('update:node', replaceCapability(props.node, capability, selectedCapability.value)) }
function updateParameter(name: string, value: unknown) { if (props.node) emit('update:node', { ...props.node, capability: { ...props.node.capability, capabilityParameters: { ...props.node.capability?.capabilityParameters, [name]: value } } }) }
</script>
<style scoped>
.node-inspector{min-height:100%;color:#172033}.node-identity{display:grid;grid-template-columns:36px minmax(0,1fr) auto;align-items:center;gap:10px;padding:12px;border-bottom:1px solid #e5e9ef;background:#f7f8fa}.node-identity>div{display:grid;gap:2px;min-width:0}.node-identity strong,.node-identity small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.node-identity strong{font-size:13px}.node-identity small{color:#737f90;font-size:11px}.node-type-mark{width:34px;height:34px;display:grid;place-items:center;border-radius:4px;background:#eaf1fb;color:#2368b2;font-size:12px;font-weight:800}.node-type-mark.start{background:#e8f6ee;color:#177b45}.node-type-mark.end{background:#eceff3;color:#4e5969}.node-type-mark.branch{background:#fff3d9;color:#a96500}.node-type-mark.aggregate{background:#f0eafd;color:#6846a7}.node-type-mark.subflow_node{background:#e5f5f3;color:#14766f}.node-form{padding:12px 12px 0}.node-form :deep(.el-form-item){margin-bottom:10px}.runtime-note{display:grid;grid-template-columns:52px 1fr;gap:8px;margin:0 12px 10px;padding:8px 9px;border-left:3px solid #367cc1;background:#f2f6fb;font-size:11px;line-height:1.55}.runtime-note b{color:#2d6eae}.runtime-note span{color:#59677a}.node-tabs :deep(.el-tabs__header){margin:0;padding:0 10px;border-top:1px solid #e5e9ef;border-bottom:1px solid #e5e9ef;background:#fafbfc}.node-tabs :deep(.el-tabs__nav-wrap:after){display:none}.node-tabs :deep(.el-tabs__item){height:38px;padding:0 10px;font-size:11px}.node-tabs :deep(.el-tabs__item small){min-width:16px;margin-left:3px;padding:0 4px;border-radius:8px;background:#d84c4c;color:#fff;text-align:center;font-size:9px}.node-tabs :deep(.el-tabs__content){overflow:visible}.basic-configuration{display:grid;gap:10px;padding:12px}.basic-configuration :deep(.el-select),.basic-configuration :deep(.el-input-number){width:100%}.basic-configuration :deep(.el-form-item){margin-bottom:10px}.semantic-card{display:grid;gap:7px;padding:12px;border:1px solid #dce3ec;background:#f8f9fb}.semantic-card strong{font-size:13px}.semantic-card span{color:#667488;font-size:11px;line-height:1.6}.property-list{margin:0;border:1px solid #e0e5eb}.property-list>div{display:grid;grid-template-columns:104px 1fr;border-bottom:1px solid #e8ecf1}.property-list>div:last-child{border-bottom:0}.property-list dt,.property-list dd{margin:0;padding:9px;font-size:11px;line-height:1.5}.property-list dt{background:#f6f7f9;color:#667386}.property-list dd{color:#28364a}.read-only-system-skeleton{display:grid;gap:10px;padding:12px}.node-footer{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-top:1px solid #e5e9ef;background:#fafbfc}.node-footer span{color:#8a5560;font-size:10px}.node-inspector :deep(.panel-section),.node-inspector :deep(.variables-ports-panel),.node-inspector :deep(.triggers-actions-panel){padding:12px!important}.node-inspector :deep(.el-alert){border-radius:2px}.node-inspector :deep(.el-table){font-size:11px}
</style>
