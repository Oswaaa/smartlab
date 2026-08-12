<template>
  <section v-if="visible && node" class="node-inspector">
    <header class="node-profile-header">
      <div class="node-profile-title">
        <span :class="['node-type-mark', nodeTypeClass]">{{ nodeTypeGlyph }}</span>
        <div>
          <span class="profile-eyebrow">工作流节点</span>
          <h2>{{ node.name }}</h2>
          <p>{{ nodeGuidance.title }} · {{ nodeTypeLabel }}</p>
        </div>
      </div>
      <div class="node-header-actions">
        <el-tag v-if="errors.length" type="danger" effect="plain">{{ errors.length }} 个配置错误</el-tag>
        <el-button class="btn-aliyun console-button" @click="emit('close')">关闭</el-button>
        <el-button v-if="!readonly" class="btn-aliyun-danger-link console-danger-button" plain @click="emit('remove-node')">删除节点</el-button>
      </div>
    </header>

    <section class="node-profile-details">
      <label class="profile-field profile-name-field"><span>节点名称</span><el-input v-model="nameDraft" :disabled="readonly" maxlength="80" @change="rename" /></label>
      <div class="profile-field"><span>节点类型</span><strong>{{ nodeTypeLabel }}</strong></div>
      <div class="profile-field"><span>内部变量</span><strong>{{ node.internalVariables?.length || 0 }} 项</strong></div>
      <div class="profile-field"><span>数据端口</span><strong>{{ node.ports?.length || 0 }} 个</strong></div>
      <div class="profile-field"><span>控制接口</span><strong>{{ node.interfaces?.length || 0 }} 个</strong></div>
    </section>

    <main class="node-config-workspace">
      <div class="config-workspace-heading">
        <div><h3>配置详情</h3></div>
        <p>{{ nodeGuidance.runtime }}</p>
      </div>
      <el-tabs v-model="activeTab" class="node-tabs">
        <el-tab-pane name="business">
          <template #label>业务配置 <small v-if="tabErrors.business">{{ tabErrors.business }}</small></template>
          <WorkflowBusinessPanel :node="node" :readonly="readonly" :contract-ready="contractReady" :device-capabilities="deviceCapabilities" :guidance="nodeGuidance" @update:node="emit('update:node', $event)" />
        </el-tab-pane>
        <el-tab-pane name="variables"><template #label>变量空间 <small v-if="tabErrors.variables">{{ tabErrors.variables }}</small></template><WorkflowVariablesPanel :node="node" :readonly="readonly" :device-attributes="deviceAttributes" @update:node="emit('update:node', $event)" /></el-tab-pane>
        <el-tab-pane name="ports"><template #label>数据端口 <small v-if="tabErrors.ports">{{ tabErrors.ports }}</small></template><WorkflowDataPortsPanel :node="node" :readonly="readonly" @update:node="emit('update:node', $event)" @remove-port-request="emit('remove-port-request', $event)" /></el-tab-pane>
        <el-tab-pane name="interfaces">
          <template #label>控制接口 <small v-if="tabErrors.interfaces">{{ tabErrors.interfaces }}</small></template>
          <WorkflowControlInterfacesPanel
            :node="node"
            :readonly="readonly"
            :protocol-metadata="protocolMetadata"
            :interface-connections="interfaceConnections"
            @update:node="emit('update:node', $event)"
            @update:interface-connections="emit('update:interfaceConnections', $event)"
          />
        </el-tab-pane>
        <el-tab-pane name="lifecycle">
          <template #label>生命周期 <small v-if="tabErrors.lifecycle">{{ tabErrors.lifecycle }}</small></template>
          <WorkflowLifecyclePanel :lifecycle="node.lifecycle" />
        </el-tab-pane>
      </el-tabs>
    </main>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import WorkflowBusinessPanel from './inspector/WorkflowBusinessPanel.vue'
import WorkflowVariablesPanel from './inspector/WorkflowVariablesPanel.vue'
import WorkflowDataPortsPanel from './inspector/WorkflowDataPortsPanel.vue'
import WorkflowControlInterfacesPanel from './inspector/WorkflowControlInterfacesPanel.vue'
import WorkflowLifecyclePanel from './inspector/WorkflowLifecyclePanel.vue'
type Item = Record<string, any>
const props = withDefaults(defineProps<{ visible: boolean, node: Item | null, readonly?: boolean, protocolMetadata?: Item, contractReady?: boolean, errors?: Item[], deviceCapabilities?: Item[], deviceAttributes?: Item[], interfaceConnections?: Item[], portConnections?: Item[] }>(), { readonly: false, protocolMetadata: () => ({}), contractReady: false, errors: () => [], deviceCapabilities: () => [], deviceAttributes: () => [], interfaceConnections: () => [], portConnections: () => [] })
const emit = defineEmits<{ rename: [name: string], 'update:node': [node: Item], 'update:interfaceConnections': [connections: Item[]], 'update:portConnections': [connections: Item[]], 'remove-port-request': [portName: string], 'remove-node': [], close: [] }>()
const activeTab = ref('business')
const nameDraft = ref('')
watch(() => props.node?.name, value => { nameDraft.value = value || '' }, { immediate: true })
const tabErrors = computed(() => props.errors.reduce((counts: Record<string, number>, error: Item) => {
  const path = error.path || ''
  const tab = path.startsWith('internalVariables') ? 'variables'
    : path.startsWith('ports') ? 'ports'
      : path.startsWith('actions') || path.startsWith('interfaces') ? 'interfaces'
        : path.startsWith('lifecycle') ? 'lifecycle' : 'business'
  counts[tab] += 1
  return counts
}, { business: 0, variables: 0, ports: 0, interfaces: 0, lifecycle: 0 }))
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
const nodeTypeLabel = computed(() => ({ DEV_NODE:'设备能力节点', SUBFLOW_NODE:'子流程节点', START:'开始节点', END:'结束节点', BRANCH:'条件分支节点', AGGREGATE:'聚合节点' } as Record<string,string>)[guidanceKey.value || ''] || '功能节点')
function rename() { if (props.readonly) return; const name = nameDraft.value.trim(); if (name && name !== props.node?.name) emit('rename', name); else nameDraft.value = props.node?.name || '' }
</script>
<style scoped>
.node-inspector{height:100%;overflow-y:auto;color:#262626;background:#fff;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",Arial,sans-serif}.node-profile-header{min-height:72px;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:14px 20px;border-bottom:1px solid #e5e5e5;background:#fff;box-sizing:border-box}.node-profile-title{display:flex;align-items:center;gap:10px;min-width:0}.node-profile-title>div{min-width:0}.profile-eyebrow{display:block;margin-bottom:2px;color:#8c8c8c;font-size:10px;line-height:15px}.node-profile-title h2{margin:0;overflow:hidden;color:#262626;font-size:16px;font-weight:500;line-height:24px;text-overflow:ellipsis;white-space:nowrap}.node-profile-title p{margin:1px 0 0;color:#8c8c8c;font-size:11px}.node-type-mark{width:34px;height:34px;display:grid;place-items:center;flex:none;border-radius:2px;background:#e6f4ff;color:#1677ff;font-size:13px;font-weight:500}.node-type-mark.start{background:#f6ffed;color:#389e0d}.node-type-mark.end{background:#f5f5f5;color:#595959}.node-type-mark.branch{background:#fffbe6;color:#d48806}.node-type-mark.aggregate{background:#f9f0ff;color:#722ed1}.node-type-mark.subflow_node{background:#e6fffb;color:#08979c}.node-header-actions{display:flex;align-items:center;gap:8px;flex:none}.console-button,.console-danger-button{height:30px;border-radius:2px;font-size:12px}.console-danger-button{border-color:#ffccc7;color:#cf1322;background:#fff}.console-danger-button:hover{border-color:#ff4d4f!important;color:#ff4d4f!important;background:#fff1f0!important}.node-profile-details{display:grid;grid-template-columns:minmax(230px,2fr) repeat(4,minmax(92px,1fr));gap:0;margin:0;border:0;border-bottom:1px solid #e5e5e5;background:#fff}.profile-field{min-height:62px;display:flex;flex-direction:column;justify-content:center;gap:4px;padding:9px 16px;border-right:1px solid #f0f0f0;box-sizing:border-box}.profile-field:last-child{border-right:0}.profile-field>span{color:#8c8c8c;font-size:10px}.profile-field>strong{color:#262626;font-size:12px;font-weight:500}.profile-name-field :deep(.el-input){max-width:250px}.node-config-workspace{margin:0;border:0;background:#fff}.config-workspace-heading{min-height:52px;display:flex;align-items:center;justify-content:space-between;gap:20px;padding:0 20px;border-bottom:1px solid #e5e5e5;background:#fff}.config-workspace-heading h3{margin:0;color:#262626;font-size:13px;font-weight:500}.config-workspace-heading p{max-width:560px;margin:0;color:#8c8c8c;font-size:11px;line-height:18px;text-align:right}.node-tabs :deep(.el-tabs__header){margin:0;padding:0 16px;border-bottom:1px solid #e5e5e5;background:#fff}.node-tabs :deep(.el-tabs__nav-wrap:after){display:none}.node-tabs :deep(.el-tabs__item){height:44px;padding:0 16px;color:#595959;font-size:12px;font-weight:400}.node-tabs :deep(.el-tabs__item.is-active){color:#1677ff;font-weight:500}.node-tabs :deep(.el-tabs__active-bar){height:2px;background:#1677ff}.node-tabs :deep(.el-tabs__item small){min-width:16px;margin-left:4px;padding:0 4px;border-radius:8px;background:#ff4d4f;color:#fff;text-align:center;font-size:9px}.node-tabs :deep(.el-tabs__content){overflow:visible;background:#fff}.node-inspector :deep(.el-input__wrapper),.node-inspector :deep(.el-select__wrapper){min-height:30px;border-radius:2px;background:#fff;box-shadow:0 0 0 1px #d9d9d9 inset;transition:box-shadow .15s ease}.node-inspector :deep(.el-input__wrapper:hover),.node-inspector :deep(.el-select__wrapper:hover){box-shadow:0 0 0 1px #4096ff inset}.node-inspector :deep(.el-input__wrapper.is-focus),.node-inspector :deep(.el-select__wrapper.is-focused){box-shadow:0 0 0 1px #1677ff inset}.node-inspector :deep(.el-textarea__inner){border-radius:2px;box-shadow:0 0 0 1px #d9d9d9 inset;font-family:inherit;font-size:12px;line-height:1.6}.node-inspector :deep(.el-input__inner),.node-inspector :deep(.el-select__selected-item){font-size:12px}.node-inspector :deep(.el-alert){border-radius:2px}.node-inspector :deep(.el-table){font-size:12px}.node-inspector :deep(.el-table th.el-table__cell){background:#fafafa;color:#595959;font-weight:500}@media(max-width:900px){.node-profile-details{grid-template-columns:1fr 1fr}.profile-field{border-bottom:1px solid #f0f0f0}.config-workspace-heading{align-items:flex-start;flex-direction:column;gap:4px;padding:10px 16px}.config-workspace-heading p{text-align:left}.node-profile-header{align-items:flex-start}.node-header-actions{flex-wrap:wrap;justify-content:flex-end}}
</style>
