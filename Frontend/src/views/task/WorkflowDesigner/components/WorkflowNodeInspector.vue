<template>
  <section v-if="visible && node" class="node-inspector">
    <header class="node-profile-header">
      <div class="node-profile-title">
        <span :class="['node-type-mark', nodeTypeClass]">{{ nodeTypeGlyph }}</span>
        <div class="title-text-wrap">
          <span class="profile-eyebrow">工作流节点 · {{ nodeGuidance.title }} · {{ nodeTypeLabel }}</span>
          <input
            v-model="nameDraft"
            :disabled="readonly"
            class="node-name-input"
            maxlength="80"
            placeholder="请输入节点名称"
            @change="rename"
          />
        </div>
      </div>
      <div class="node-header-actions">
        <el-tag v-if="errors.length" type="danger" effect="plain">{{ errors.length }} 个配置错误</el-tag>
        <button type="button" class="btn-aliyun" @click="emit('close')">关闭</button>
        <button v-if="!readonly" type="button" class="btn-link danger" @click="emit('remove-node')">删除节点</button>
      </div>
    </header>

    <div class="config-workspace-heading">
      <div><h3>配置详情</h3></div>
      <p>{{ nodeGuidance.runtime }}</p>
    </div>

    <div v-if="errors.length" class="node-error-summary">
      <el-alert :title="`当前节点有 ${errors.length} 个配置错误`" type="error" :closable="false" show-icon>
        <ul class="node-error-list">
          <li v-for="(error,index) in errors" :key="`${error.path || 'configuration'}-${index}`"><strong>{{ errorTitle(error) }}</strong><span>{{ error.message }}</span></li>
        </ul>
      </el-alert>
    </div>

    <main class="node-config-workspace">
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
  BRANCH:{ title:'条件路由', runtime:'节点激活后，可先计算派生变量，再由各接口触发器独立判断是否激活下游。', detail:'按业务需要配置输出接口和触发条件。' },
  AGGREGATE:{ title:'多路汇聚', runtime:'每个独立输入首次到达时累计一次，由输出触发器决定达到多少路后继续。', detail:'为每条上游路径使用独立输入接口，并配置可达到的聚合阈值。' }
}
const guidanceKey = computed(() => props.node?.nodeType === 'FUNC_NODE' ? props.node?.functionType : props.node?.nodeType)
const nodeGuidance = computed(() => guidanceMap[guidanceKey.value || ''] || { title:'节点配置', runtime:'按节点契约执行。', detail:'配置节点业务参数。' })
const nodeTypeClass = computed(() => String(guidanceKey.value || '').toLowerCase())
const nodeTypeGlyph = computed(() => ({ DEV_NODE:'D', SUBFLOW_NODE:'↳', START:'▶', END:'■', BRANCH:'◇', AGGREGATE:'◆' } as Record<string,string>)[guidanceKey.value || ''] || 'N')
const nodeTypeLabel = computed(() => ({ DEV_NODE:'设备能力节点', SUBFLOW_NODE:'子流程节点', START:'开始节点', END:'结束节点', BRANCH:'条件分支节点', AGGREGATE:'聚合节点' } as Record<string,string>)[guidanceKey.value || ''] || '功能节点')
function errorTitle(error:Item) {
  if (error.title) return error.title
  const path = String(error.path || '')
  if (path === 'topology') return '流程连接'
  if (path === 'expression') return '计算表达式'
  if (path.startsWith('internalVariables')) return '变量空间'
  if (path.startsWith('ports')) return '数据端口'
  if (path.startsWith('interfaces') || path.startsWith('actions')) return '控制接口'
  if (path.startsWith('lifecycle')) return '生命周期'
  return '节点配置'
}
function rename() { if (props.readonly) return; const name = nameDraft.value.trim(); if (name && name !== props.node?.name) emit('rename', name); else nameDraft.value = props.node?.name || '' }
</script>
<style scoped>
.node-inspector {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--sl-text-body, #334155);
  background: #ffffff;
  font-family: var(--sl-font-family);
}
.node-profile-header {
  flex: none;
  min-height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  box-sizing: border-box;
}
.node-profile-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.title-text-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.profile-eyebrow {
  display: block;
  margin-bottom: 2px;
  color: var(--sl-text-secondary, #64748b);
  font-size: 10.5px;
  line-height: 1.2;
}
.node-name-input {
  font-size: 14.5px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
  border: 1px solid transparent;
  border-radius: 4px;
  padding: 2px 4px;
  background: transparent;
  width: 100%;
  outline: none;
  transition: all 0.15s ease;
  font-family: inherit;
}
.node-name-input:hover {
  border-color: var(--sl-border-base, #e2e8f0);
  background: var(--sl-bg-hover, #f8fafc);
}
.node-name-input:focus {
  border-color: var(--sl-primary, #2563eb);
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(37,99,235,0.08);
}
.node-type-mark {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  flex: none;
  border-radius: var(--sl-radius-sm, 6px);
  background: var(--sl-primary-light, #eff6ff);
  color: var(--sl-primary, #2563eb);
  font-size: 13px;
  font-weight: 600;
}
.node-type-mark.start { background: var(--sl-success-light, #f0fdf4); color: var(--sl-success, #16a34a); }
.node-type-mark.end { background: var(--sl-bg-page, #f1f5f9); color: var(--sl-text-secondary, #64748b); }
.node-type-mark.branch { background: var(--sl-warning-light, #fffbeb); color: var(--sl-warning, #d97706); }
.node-type-mark.aggregate { background: #f5f3ff; color: #6d28d9; }
.node-type-mark.subflow_node { background: #ecfeff; color: #0f766e; }
.node-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.config-workspace-heading {
  flex: none;
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}
.config-workspace-heading h3 {
  margin: 0;
  color: var(--sl-text-heading, #0f172a);
  font-size: 12.5px;
  font-weight: 600;
}
.config-workspace-heading p {
  margin: 0;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  line-height: 16px;
  text-align: right;
}
.node-error-summary {
  flex: none;
  padding: 8px 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}
.node-error-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}
.node-error-list li{display:flex;align-items:baseline;gap:8px;flex-wrap:wrap}
.node-error-list strong {
  color: var(--sl-danger, #dc2626);
  font-size: 12px;
  font-weight: 500;
}
.node-error-list span {
  color: var(--sl-text-body, #334155);
  font-size: 12px;
  line-height: 1.6;
}
.node-config-workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.node-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.node-tabs :deep(.el-tabs__header) {
  flex: none;
  margin: 0;
  padding: 0 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}
.node-tabs :deep(.el-tabs__nav-wrap:after) { display: none; }
.node-tabs :deep(.el-tabs__item) {
  height: 36px;
  padding: 0 12px;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12.5px;
}
.node-tabs :deep(.el-tabs__item.is-active) {
  color: var(--sl-primary, #2563eb);
  font-weight: 600;
}
.node-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background: var(--sl-primary, #2563eb);
}
.node-tabs :deep(.el-tabs__item small) {
  min-width: 16px;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--sl-danger, #dc2626);
  color: #ffffff;
  text-align: center;
  font-size: 10.5px;
}
.node-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  background: #fafbfc;
  padding: 14px 16px;
  min-height: 0;
  box-sizing: border-box;
}
.node-tabs :deep(.el-tabs__content)::-webkit-scrollbar {
  width: 5px;
}
.node-tabs :deep(.el-tabs__content)::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 2.5px;
}
.node-tabs :deep(.el-tabs__content)::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
.node-tabs :deep(.el-tab-pane) {
  min-height: 100%;
}
.node-inspector :deep(.el-input__wrapper),
.node-inspector :deep(.el-select__wrapper) {
  min-height: 28px;
  border-radius: var(--sl-radius-sm, 6px);
  background-color: transparent !important;
  box-shadow: 0 0 0 1px var(--sl-border-base, #e2e8f0) inset !important;
  transition: all 0.15s ease;
}
.node-inspector :deep(.el-input__wrapper:hover),
.node-inspector :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset !important;
  background-color: rgba(248, 250, 252, 0.4) !important;
}
.node-inspector :deep(.el-input__wrapper.is-focus),
.node-inspector :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--sl-primary, #2563eb) inset !important;
  background-color: #ffffff !important;
}
.node-inspector :deep(.el-textarea__inner) {
  border-radius: var(--sl-radius-sm, 6px);
  font-size: 12px;
  line-height: 1.6;
  background-color: transparent;
  box-shadow: 0 0 0 1px var(--sl-border-base, #e2e8f0) inset;
}
.node-inspector :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}
.node-inspector :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--sl-primary, #2563eb) inset;
  background-color: #ffffff;
}
.node-inspector :deep(.el-table th.el-table__cell) {
  background: var(--sl-bg-hover, #f8fafc);
  color: var(--sl-text-secondary, #64748b);
  font-weight: 600;
}
@media (max-width: 900px) {
  .config-workspace-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
    padding: 8px 16px;
  }
  .config-workspace-heading p { text-align: left; }
  .node-profile-header { align-items: flex-start; }
  .node-header-actions { flex-wrap: wrap; justify-content: flex-end; }
}
</style>
