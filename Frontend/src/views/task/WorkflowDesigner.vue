<template>
  <div class="workflow-page">
    <header class="designer-commandbar">
      <div class="flow-identity">
        <span class="product-path">任务中心 / 流程设计</span>
        <div class="flow-title-row">
          <strong>{{ form.name || '未命名流程' }}</strong>
          <span class="status-chip" :class="form.status.toLowerCase()">{{ form.status === 'ACTIVE' ? '已启用' : '草稿' }}</span>
          <span v-if="form.id" class="meta-chip">ID {{ form.id }}</span>
          <span class="meta-chip">V{{ form.version }}</span>
          <span v-if="dirty" class="dirty-mark">未保存</span>
        </div>
      </div>
      <div class="command-actions">
        <el-select v-model="openedWorkflowId" clearable filterable placeholder="打开流程" class="workflow-select" @change="loadWorkflow">
          <el-option v-for="item in workflows" :key="item.id" :label="workflowOptionLabel(item)" :value="item.id" />
        </el-select>
        <el-tooltip content="刷新流程、设备模型与契约" placement="bottom"><el-button :icon="Refresh" class="btn-aliyun" @click="loadAll">刷新</el-button></el-tooltip>
        <el-button :icon="Plus" class="btn-aliyun" @click="create">新建</el-button>
        <el-button class="btn-aliyun" @click="runValidation">校验</el-button>
        <el-button :icon="Setting" class="btn-aliyun" @click="openSettings">流程配置</el-button>
        <el-button class="btn-aliyun" :disabled="!form.name" @click="exportWorkflow">导出 JSON</el-button>
        <el-button class="btn-aliyun-cta" :loading="draftSaving" :disabled="!contractReady" @click="saveDraft">保存草稿</el-button>
        <el-button class="btn-aliyun-cta" :loading="publishSaving" :disabled="!contractReady" @click="publishAndValidate">检查并发布</el-button>
      </div>
    </header>

    <div class="designer-grid">
      <aside class="resource-panel">
        <div class="panel-titlebar">
          <div><strong>节点库</strong><span>{{ models.length }} 个设备模型</span></div>
          <small>单击或拖入画布</small>
        </div>
        <div class="resource-search">
          <el-input v-model="resourceKeyword" :prefix-icon="Search" clearable placeholder="搜索设备模型或子流程" />
        </div>

        <section class="function-section">
          <el-alert v-if="contractError" class="contract-error" type="error" :closable="false" :title="contractError" />
          <div class="group-title"><strong>流程控制</strong><span>4</span></div>
          <div class="function-list">
            <button v-for="item in palette" :key="item.type" class="function-item" :disabled="!contractReady" :draggable="contractReady" @dragstart="drag($event,{kind:'function',type:item.type})" @click="addResource({kind:'function',type:item.type})">
              <span class="function-icon" :class="item.type.toLowerCase()">{{ item.glyph }}</span>
              <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
              <b>＋</b>
            </button>
          </div>
        </section>

        <el-tabs v-model="tab" class="resource-tabs" stretch>
          <el-tab-pane label="设备模型" name="devices">
            <el-tree v-if="filteredDeviceTree.length" :data="filteredDeviceTree" node-key="key" default-expand-all :expand-on-click-node="false" class="resource-tree">
              <template #default="{ data }">
                <div class="tree-item" :class="{ draggable:data.kind==='model' && contractReady }" :draggable="data.kind==='model' && contractReady" @dragstart="drag($event,data)">
                  <span class="tree-label"><span class="tree-dot" :class="data.kind"></span><span>{{ data.label }}</span></span>
                  <el-button v-if="data.kind==='model'" link class="btn-aliyun-link" title="添加到画布" :disabled="!contractReady" @click.stop="addResource({kind:'model',model:data.model})">＋</el-button>
                  <small v-else-if="data.kind==='instance'">实例</small>
                </div>
              </template>
            </el-tree>
            <el-empty v-else description="没有匹配的设备模型" :image-size="48" />
          </el-tab-pane>
          <el-tab-pane label="子流程" name="workflows">
            <div v-if="filteredWorkflows.length" class="flow-list">
              <button v-for="item in filteredWorkflows" :key="item.id" class="flow-item" :disabled="!contractReady" :draggable="contractReady" @dragstart="drag($event,{kind:'workflow',workflow:item})" @click="addResource({kind:'workflow',workflow:item})">
                <span class="flow-icon">↳</span><span><strong>{{ item.flowName }}</strong><small>{{ item.description || '作为子流程节点引用' }}</small></span><span class="add-mark">＋</span>
              </button>
            </div>
            <el-empty v-else description="没有可引用的流程" :image-size="48" />
          </el-tab-pane>
          <el-tab-pane label="已有流程" name="all-flows">
            <div v-if="allFilteredWorkflows.length" class="flow-list">
              <button
                v-for="item in allFilteredWorkflows"
                :key="item.id"
                class="flow-item existing-flow-item"
                :class="{ 'is-active': item.id === form.id }"
                @click="loadWorkflow(item.id)"
              >
                <span class="flow-icon existing-icon">⊙</span>
                <span>
                  <strong>{{ item.flowName || '未命名流程' }}</strong>
                  <small>V{{ item.version }} · {{ item.status === 'ACTIVE' ? '已启用' : '草稿' }}{{ item.id === form.id ? ' · 当前' : '' }}</small>
                </span>
                <span class="load-mark">{{ item.id === form.id ? '●' : '↗' }}</span>
              </button>
            </div>
            <el-empty v-else description="暂无流程" :image-size="48" />
          </el-tab-pane>
        </el-tabs>
      </aside>

      <main class="canvas-panel">
        <div class="canvas-toolbar">
          <div class="canvas-title">
            <strong>流程画布</strong>
            <span>{{ form.nodesDef.length }} 节点</span>
            <span>{{ nodeConnections.length }} 连接</span>
            <span v-if="validationSummary.errors" class="error-count">{{ validationSummary.errors }} 错误</span>
          </div>
          <div class="canvas-actions">
            <el-button v-if="selectedEdgeId" class="btn-aliyun-danger-link" text @click="deleteSelectedEdge">删除连接</el-button>
            <el-button class="btn-aliyun" text :icon="MagicStick" @click="autoLayout">自动布局</el-button>
            <el-button class="btn-aliyun" text :icon="Aim" @click="fitCanvas">适应画布</el-button>
          </div>
        </div>

        <div class="flow-stage" @dragover.prevent @drop="drop">
          <VueFlow v-model:nodes="flowNodes" v-model:edges="flowEdges" class="workflow-flow" :min-zoom="0.35" :max-zoom="1.8" :default-edge-options="defaultEdgeOptions" :delete-key-code="null" :fit-view-on-init="true" @connect="connectNodes" @node-click="selectCanvasNode" @pane-click="clearSelection" @node-drag-stop="persistLayout" @edge-click="selectEdge" @edges-delete="removeDeletedEdges">
            <Background pattern-color="#cbd8e8" :gap="20" :size="1.2" />
            <Controls position="bottom-left" />
            <template #node-workflow="slotProps">
              <WorkflowCanvasNode :node="nodeByName(slotProps.data.nodeName)" :selected="slotProps.selected" :issues="nodeIssues(slotProps.data.nodeName)" />
            </template>
          </VueFlow>
          <section v-if="!flowNodes.length" class="empty-workbench">
            <div class="empty-head"><span>01</span><div><strong>建立流程骨架</strong><small>一个可执行流程从 START 开始，在 END 结束</small></div></div>
            <div class="quick-start">
              <button :disabled="!contractReady" @click.stop="addResource({kind:'function',type:'START'})"><b>▶</b><span>添加开始节点</span></button>
              <i>→</i>
              <button :disabled="!contractReady" @click.stop="addResource({kind:'function',type:'END'})"><b>■</b><span>添加结束节点</span></button>
            </div>
            <ol>
              <li><span>02</span><div><strong>编排执行节点</strong><small>加入设备能力、条件分支或子流程</small></div></li>
              <li><span>03</span><div><strong>连接执行与数据</strong><small>实线表示执行流，虚线表示端口数据流</small></div></li>
              <li><span>04</span><div><strong>校验并保存</strong><small>修复错误后保存为草稿或启用模型</small></div></li>
            </ol>
          </section>
        </div>

        <footer class="canvas-statusbar">
          <span><i class="legend-line workflow"></i>执行流</span>
          <span><i class="legend-line data"></i>数据流</span>
          <span class="status-grow">拖动节点连接点建立关系 · 滚轮缩放画布</span>
          <span><i class="status-dot"></i>布局保存在当前浏览器</span>
        </footer>
      </main>
      <aside class="inspector-panel">
        <div class="panel-titlebar inspector-titlebar">
          <div><strong>{{ inspectorTitle }}</strong><span>{{ inspectorSubtitle }}</span></div>
          <el-button v-if="hasInspectorSelection" link class="btn-aliyun-link" @click="showOverview">返回总览</el-button>
        </div>
        <div class="inspector-scroll">
          <section v-if="settingsVisible" class="settings-view">
            <div class="business-note"><b>01</b><div><strong>定义模型身份</strong><span>名称和版本用于创建任务时识别流程；已启用模型可被执行和引用。</span></div></div>
            <el-form label-position="top" class="dense-form">
              <el-form-item label="流程名称"><el-input v-model="form.name" maxlength="80" placeholder="例如：恒温反应实验流程" @input="markDirty" /></el-form-item>
              <div class="two-column-form"><el-form-item label="版本"><el-input-number v-model="form.version" :min="1" :precision="0" controls-position="right" @change="markDirty" /></el-form-item></div>
              <el-form-item label="业务说明"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" placeholder="说明前置条件、执行目标和适用范围" @input="markDirty" /></el-form-item>
            </el-form>
            <div class="business-note"><b>02</b><div><strong>编排执行路径</strong><span>START → 设备/分支/子流程 → END；AGGREGATE 用于等待多条上游路径。</span></div></div>
            <div class="business-note"><b>03</b><div><strong>校验后保存</strong><span>保存前检查节点契约、入口出口、断路和环路；任务运行时再绑定设备实例。</span></div></div>
            <el-button class="wide-action btn-aliyun-cta" plain @click="runValidation">执行完整校验</el-button>
          </section>
          <WorkflowNodeInspector v-else-if="nodeDrawerVisible && selectedNode" :visible="nodeDrawerVisible" :node="selectedNode" :errors="selectedNodeIssues" :device-capabilities="selectedDeviceModel?.capabilities || []" :device-attributes="selectedDeviceModel?.attributes || []" :port-connections="form.portConnections" :contract-ready="contractReady" @close="closeNodeDrawer" @rename="renameSelectedNode" @update:node="replaceSelectedNode" @update:port-connections="replacePortConnections" @remove-port-request="confirmRemovePort" @remove-node="removeSelectedNode" />
          <section v-else-if="selectedEdge" class="edge-view">
            <div class="connection-type" :class="selectedEdge.data?.connectionKind?.toLowerCase()"><span>{{ selectedEdge.data?.connectionKind === 'PORT' ? '数据流' : '执行流' }}</span><b>{{ selectedEdgeEndpoint.source }} → {{ selectedEdgeEndpoint.target }}</b></div>
            <dl class="property-list"><div><dt>源连接点</dt><dd>{{ selectedEdgeEndpoint.sourceHandle }}</dd></div><div><dt>目标连接点</dt><dd>{{ selectedEdgeEndpoint.targetHandle }}</dd></div><div><dt>业务语义</dt><dd>{{ selectedEdge.data?.connectionKind === 'PORT' ? '将上游节点内部变量传递给下游节点' : '上游节点完成后激活下游节点' }}</dd></div></dl>
            <el-button class="wide-action btn-aliyun-danger-link" plain @click="deleteSelectedEdge">删除该连接</el-button>
          </section>
          <section v-else-if="validationVisible" class="validation-view">
            <div class="validation-summary"><div><strong>{{ validationSummary.errors }}</strong><span>错误</span></div><div><strong>{{ validationSummary.warnings }}</strong><span>提醒</span></div><div><strong>{{ form.nodesDef.length }}</strong><span>节点</span></div></div>
            <div v-if="workflowValidationIssues.length" class="issue-list"><button v-for="issue in workflowValidationIssues" :key="issue.code" :class="issue.severity" @click="focusValidationIssue(issue)"><b>{{ issue.severity === 'error' ? '错误' : '提醒' }}</b><span><strong>{{ issue.title }}</strong><small>{{ issue.detail }}</small></span><i>›</i></button></div>
            <el-result v-else icon="success" title="流程校验通过" sub-title="节点契约与执行拓扑均满足保存要求" />
          </section>
          <section v-else class="overview-view">
            <div class="overview-metrics"><div><strong>{{ form.nodesDef.length }}</strong><span>节点</span></div><div><strong>{{ executionConnectionCount }}</strong><span>执行连接</span></div><div><strong>{{ form.portConnections.length }}</strong><span>数据连接</span></div><div><strong>{{ deviceNodeCount }}</strong><span>设备节点</span></div></div>
            <div class="section-heading"><strong>建模检查</strong><span>保存前必须通过</span></div>
            <ul class="check-list"><li :class="{ok:hasSingleStartEnd}"><i></i><span><strong>唯一入口与出口</strong><small>需要且仅需要一个 START 和 END</small></span></li><li :class="{ok:!validationSummary.errors}"><i></i><span><strong>节点与拓扑有效</strong><small>{{ validationSummary.errors ? `${validationSummary.errors} 个问题待处理` : '节点契约、连接和路径正常' }}</small></span></li><li :class="{ok:contractReady}"><i></i><span><strong>系统契约已加载</strong><small>{{ contractReady ? '可安全创建并保存节点' : contractError || '契约加载中' }}</small></span></li></ul>
            <div class="section-heading"><strong>标准业务顺序</strong><span>运行时语义</span></div>
            <div class="business-flow"><span>创建任务并绑定设备实例</span><b>↓</b><span>START 激活首个执行节点</span><b>↓</b><span>能力调用 / 分支 / 子流程</span><b>↓</b><span>AGGREGATE 汇聚后进入 END</span></div>
            <div class="overview-actions"><el-button class="btn-aliyun" @click="openSettings">流程配置</el-button><el-button class="btn-aliyun-cta" @click="runValidation">校验流程</el-button></div>
          </section>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, MagicStick, Plus, Refresh, Search, Setting } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import {
  buildFlowEdges,
  buildFlowNodes,
  createCanvasConnection,
  layoutKey,
  removeCanvasEdge,
  renameNodeConnections,
  serializeLayout
} from '../../utils/workflowCanvas.js'
import WorkflowCanvasNode from '../../components/task/workflow/WorkflowCanvasNode.vue'
import WorkflowNodeInspector from '../../components/task/workflow/WorkflowNodeInspector.vue'
import { invalidateFrontendContractMetadata, loadFrontendContractMetadata } from '../../services/frontendContractMetadata.js'
import { workflowApi } from '../../services/workflowApi.js'
import { adoptPreparedWorkflow, indexWorkflowIssues, toAuthoringPayload } from '../../utils/workflowAuthoring.js'
import { configureWorkflowNodeTemplates, createDeviceNode, createFunctionNode, createSubflowNode, removePort, validateNodeDefinition } from '../../utils/workflowNodeDefinition.js'

type NodeDefinition = Record<string, any>
type FlowNode = Record<string, any>
type FlowEdge = Record<string, any>
type ValidationIssue = { code:string, severity:'error'|'warning', title:string, detail:string, nodeName?:string, path?:string }

const tab = ref('devices')
const workflows = ref<any[]>([])
const models = ref<any[]>([])
const instances = ref<any[]>([])
const categories = ref<any[]>([])
const draftSaving = ref(false)
const publishSaving = ref(false)
const resourceKeyword = ref('')
const contractReady = ref(false)
const contractError = ref('')
const openedWorkflowId = ref<number | null>(null)
const settingsVisible = ref(false)
const validationVisible = ref(false)
const dirty = ref(false)
const nodeDrawerVisible = ref(false)
const selectedNodeName = ref('')
const selectedEdgeId = ref('')
const flowNodes = ref<FlowNode[]>([])
const flowEdges = ref<FlowEdge[]>([])
const draftLayoutKey = ref(newDraftKey())
let workflowListPromise: Promise<void> | null = null
let contractInitializationPromise: Promise<void> | null = null
const { screenToFlowCoordinate, fitView } = useVueFlow()
const workflowIssueIndex = ref(indexWorkflowIssues())

const empty = () => ({ id:null as number | null, name:'', description:'', version:1, status:'DRAFT', nodesDef:[] as NodeDefinition[], interfaceConnections:[] as any[], portConnections:[] as any[] })
const form = reactive<any>(empty())
const palette = [
  { type:'START', label:'开始', glyph:'▶', description:'流程入口' },
  { type:'END', label:'结束', glyph:'■', description:'流程终点' },
  { type:'BRANCH', label:'分支', glyph:'◇', description:'条件路由' },
  { type:'AGGREGATE', label:'汇聚', glyph:'◆', description:'合并路径' }
]
const defaultEdgeOptions = { type:'smoothstep', style:{ stroke:'#6f8fb7', strokeWidth:2 } }

const selectedNode = computed(() => nodeByName(selectedNodeName.value))
const selectedDeviceModel = computed(() => selectedNode.value?.nodeType === 'DEV_NODE' ? modelById(selectedNode.value.deviceModelId) : null)
const selectedNodeIssues = computed(() => selectedNode.value ? nodeIssues(selectedNode.value.name) : [])
const nodeConnections = computed(() => [
  ...form.interfaceConnections.filter((item:any) => item.connectionType === 'NODE_TO_NODE'),
  ...form.portConnections
])
const executionConnections = computed(() => form.interfaceConnections.filter((item:any) => item.connectionType === 'NODE_TO_NODE'))
const executionConnectionCount = computed(() => executionConnections.value.length)
const deviceNodeCount = computed(() => form.nodesDef.filter((node:any) => node.nodeType === 'DEV_NODE').length)
const hasSingleStartEnd = computed(() => form.nodesDef.filter((node:any) => node.functionType === 'START').length === 1 && form.nodesDef.filter((node:any) => node.functionType === 'END').length === 1)
const selectedEdge = computed(() => flowEdges.value.find(edge => edge.id === selectedEdgeId.value) || null)
const selectedEdgeEndpoint = computed(() => {
  const edge = selectedEdge.value
  if (!edge) return { source:'-', target:'-', sourceHandle:'-', targetHandle:'-' }
  return {
    source: editorNodeName(edge.source),
    target: editorNodeName(edge.target),
    sourceHandle: editorHandleName(edge.sourceHandle),
    targetHandle: editorHandleName(edge.targetHandle)
  }
})
const workflowValidationIssues = computed<ValidationIssue[]>(() => [...buildWorkflowValidationIssues(), ...serverValidationIssues()])
const validationSummary = computed(() => ({
  errors: workflowValidationIssues.value.filter(issue => issue.severity === 'error').length,
  warnings: workflowValidationIssues.value.filter(issue => issue.severity === 'warning').length
}))
const hasInspectorSelection = computed(() => settingsVisible.value || nodeDrawerVisible.value || Boolean(selectedEdge.value) || validationVisible.value)
const inspectorTitle = computed(() => {
  if (settingsVisible.value) return '流程配置'
  if (nodeDrawerVisible.value && selectedNode.value) return '节点配置'
  if (selectedEdge.value) return '连接配置'
  if (validationVisible.value) return '流程校验'
  return '流程总览'
})
const inspectorSubtitle = computed(() => {
  if (settingsVisible.value) return '模型身份、版本与启用状态'
  if (nodeDrawerVisible.value && selectedNode.value) return `${selectedNode.value.name} · ${nodeBusinessLabel(selectedNode.value)}`
  if (selectedEdge.value) return selectedEdge.value.data?.connectionKind === 'PORT' ? '端口数据传递关系' : '节点执行顺序关系'
  if (validationVisible.value) return `${validationSummary.value.errors} 个错误 · ${validationSummary.value.warnings} 个提醒`
  return '结构、连接与运行时业务语义'
})

const deviceTree = computed(() => {
  const map = new Map<any, any>()
  categories.value.forEach((category:any) => map.set(category.id || category.categoryId, { key:'c-'+(category.id || category.categoryId), kind:'category', label:category.categoryName || category.name, children:[] }))
  const roots:any[] = []
  map.forEach((item:any) => {
    const raw = categories.value.find((category:any) => 'c-'+(category.id || category.categoryId) === item.key)
    const parent = map.get(raw?.parentCategoryId)
    ;(parent ? parent.children : roots).push(item)
  })
  models.value.forEach((model:any) => {
    const category = map.get(model.categoryId)
    const parent = category ? category.children : roots
    parent.push({
      key:'m-'+model.id,
      kind:'model',
      label:model.modelName,
      model,
      children:instances.value.filter((item:any) => Number(item.deviceModelId || item.modelId) === Number(model.id)).map((item:any) => ({ key:'i-'+item.id, kind:'instance', label:item.instanceName || '设备'+item.id, instance:item, model }))
    })
  })
  return roots
})

const filteredDeviceTree = computed(() => {
  const keyword = resourceKeyword.value.trim().toLowerCase()
  if (!keyword) return deviceTree.value
  const filter = (items:any[]):any[] => items.map(item => {
    const children = filter(item.children || [])
    return item.label?.toLowerCase().includes(keyword) || children.length ? { ...item, children } : null
  }).filter(Boolean)
  return filter(deviceTree.value)
})

const filteredWorkflows = computed(() => {
  const keyword = resourceKeyword.value.trim().toLowerCase()
  return workflows.value.filter(item => item.id !== form.id && (!keyword || item.flowName?.toLowerCase().includes(keyword) || item.description?.toLowerCase().includes(keyword)))
})

const allFilteredWorkflows = computed(() => {
  const keyword = resourceKeyword.value.trim().toLowerCase()
  return workflows.value.filter(item => !keyword || item.flowName?.toLowerCase().includes(keyword) || item.description?.toLowerCase().includes(keyword))
})

function newDraftKey() {
  return 'draft-'+Date.now()+'-'+Math.random().toString(36).slice(2, 8)
}

function workflowOptionLabel(item:any) {
  return `${item.flowName || '未命名流程'} · V${item.version || 1} · ${item.status === 'ACTIVE' ? '已启用' : '草稿'}`
}

function markDirty() {
  dirty.value = true
}

function editorNodeName(value:any) {
  return decodeURIComponent(String(value || '').replace(/^workflow-node:/, '')) || '-'
}

function editorHandleName(value:any) {
  const raw = String(value || '')
  return raw.includes(':') ? raw.slice(raw.indexOf(':') + 1) : raw || '-'
}

function nodeBusinessLabel(node:any) {
  if (node.nodeType === 'DEV_NODE') return '设备能力调用'
  if (node.nodeType === 'SUBFLOW_NODE') return '子流程调用'
  return ({ START:'流程入口', END:'流程出口', BRANCH:'条件路由', AGGREGATE:'多路汇聚' } as Record<string,string>)[node.functionType] || '功能节点'
}

function buildWorkflowValidationIssues():ValidationIssue[] {
  const issues:ValidationIssue[] = []
  const push = (issue:ValidationIssue) => issues.push(issue)
  if (!String(form.name || '').trim()) push({ code:'flow-name', severity:'error', title:'流程名称不能为空', detail:'配置用于任务创建和运维识别的流程名称。' })
  if (!form.nodesDef.length) push({ code:'flow-empty', severity:'error', title:'流程没有任何节点', detail:'先创建 START 与 END，再加入实际执行节点。' })

  const starts = form.nodesDef.filter((node:any) => node.functionType === 'START')
  const ends = form.nodesDef.filter((node:any) => node.functionType === 'END')
  if (starts.length !== 1) push({ code:'start-count', severity:'error', title:'START 节点数量不正确', detail:`当前 ${starts.length} 个，流程需要且仅需要一个入口。`, nodeName:starts[0]?.name })
  if (ends.length !== 1) push({ code:'end-count', severity:'error', title:'END 节点数量不正确', detail:`当前 ${ends.length} 个，流程需要且仅需要一个出口。`, nodeName:ends[0]?.name })

  form.nodesDef.forEach((node:any) => {
    validateNodeDefinition(node, { deviceModel:modelById(node.deviceModelId) }).forEach((issue:any, index:number) => push({
      code:`node-${node.name}-${issue.path || index}`,
      severity:'error',
      title:`节点 ${node.name} 配置无效`,
      detail:issue.message,
      nodeName:node.name,
      path:issue.path
    }))
  })

  const nodeNames = new Set(form.nodesDef.map((node:any) => node.name))
  const outgoing = new Map<string,string[]>([...nodeNames].map(name => [name, []]))
  const incoming = new Map<string,string[]>([...nodeNames].map(name => [name, []]))
  executionConnections.value.forEach((connection:any, index:number) => {
    const source = connection.source?.nodeName
    const target = connection.target?.nodeName
    if (!nodeNames.has(source) || !nodeNames.has(target)) {
      push({ code:`dangling-${index}`, severity:'error', title:'连接引用了不存在的节点', detail:`${source || '-'} → ${target || '-'}` })
      return
    }
    outgoing.get(source)?.push(target)
    incoming.get(target)?.push(source)
  })

  form.nodesDef.forEach((node:any) => {
    const inCount = incoming.get(node.name)?.length || 0
    const outCount = outgoing.get(node.name)?.length || 0
    if (node.functionType === 'START' && inCount) push({ code:`start-in-${node.name}`, severity:'error', title:'START 不能有上游节点', detail:'入口只能发起流程，不能被其他节点激活。', nodeName:node.name })
    if (node.functionType !== 'START' && !inCount) push({ code:`missing-in-${node.name}`, severity:'error', title:`节点 ${node.name} 没有上游路径`, detail:'该节点在运行时不会被激活。', nodeName:node.name })
    if (node.functionType === 'END' && outCount) push({ code:`end-out-${node.name}`, severity:'error', title:'END 不能有下游节点', detail:'出口表示流程已经结束，不能再激活其他节点。', nodeName:node.name })
    if (node.functionType !== 'END' && !outCount) push({ code:`missing-out-${node.name}`, severity:'error', title:`节点 ${node.name} 没有下游路径`, detail:'执行到此处后无法抵达 END。', nodeName:node.name })
    if (node.functionType === 'BRANCH') {
      const branchOutputs = (node.interfaces || []).filter((item:any) => item.direction === 'OUT' && item.interfaceType === 'WORKFLOW')
      const connectedOutputs = new Set(executionConnections.value.filter((item:any) => item.source?.nodeName === node.name).map((item:any) => item.source?.interfaceName))
      if (branchOutputs.length) branchOutputs.filter((item:any) => !connectedOutputs.has(item.name)).forEach((item:any) => push({
        code:`branch-output-${node.name}-${item.name}`,
        severity:'error',
        title:`分支 ${node.name} 的 ${item.name} 未连接`,
        detail:'真假分支出口都必须连接一条可执行的下游路径。',
        nodeName:node.name
      }))
      else if (outCount < 2) push({ code:`branch-routes-${node.name}`, severity:'error', title:`分支 ${node.name} 缺少路由`, detail:'条件分支至少需要两条下游执行路径。', nodeName:node.name })
    }
    if (node.functionType === 'BRANCH' && !String(node.expression || '').trim()) push({ code:`branch-expression-${node.name}`, severity:'error', title:`分支 ${node.name} 缺少表达式`, detail:'配置可在运行时求值的条件表达式。', nodeName:node.name, path:'expression' })
    if (node.functionType === 'AGGREGATE' && inCount < 2) push({ code:`aggregate-inputs-${node.name}`, severity:'error', title:`汇聚 ${node.name} 上游不足`, detail:'汇聚节点至少等待两条上游路径。', nodeName:node.name })
  })

  if (starts.length === 1) {
    const reachable = walkGraph(starts[0].name, outgoing)
    form.nodesDef.filter((node:any) => !reachable.has(node.name)).forEach((node:any) => push({ code:`unreachable-${node.name}`, severity:'error', title:`节点 ${node.name} 无法从 START 到达`, detail:'连接入口到该节点，或删除孤立节点。', nodeName:node.name }))
  }
  if (ends.length === 1) {
    const reachesEnd = walkGraph(ends[0].name, incoming)
    form.nodesDef.filter((node:any) => !reachesEnd.has(node.name)).forEach((node:any) => push({ code:`no-end-${node.name}`, severity:'error', title:`节点 ${node.name} 无法抵达 END`, detail:'补齐后续执行路径，避免任务永久停留。', nodeName:node.name }))
  }

  const indegree = new Map<string,number>([...nodeNames].map(name => [name, incoming.get(name)?.length || 0]))
  const queue = [...nodeNames].filter(name => indegree.get(name) === 0)
  let visited = 0
  while (queue.length) {
    const name = queue.shift() as string
    visited++
    ;(outgoing.get(name) || []).forEach(target => { const next = (indegree.get(target) || 0) - 1; indegree.set(target, next); if (next === 0) queue.push(target) })
  }
  if (form.nodesDef.length && visited !== form.nodesDef.length) push({ code:'graph-cycle', severity:'error', title:'执行路径存在环路', detail:'当前运行模型不支持循环执行，请移除回边。' })
  if (!deviceNodeCount.value && form.nodesDef.length) push({ code:'no-device-node', severity:'warning', title:'流程中没有设备执行节点', detail:'若该流程仅用于控制或调用子流程可忽略此提醒。' })
  return issues
}

function walkGraph(start:string, graph:Map<string,string[]>) {
  const visited = new Set<string>()
  const queue = [start]
  while (queue.length) {
    const name = queue.shift() as string
    if (visited.has(name)) continue
    visited.add(name)
    ;(graph.get(name) || []).forEach(next => { if (!visited.has(next)) queue.push(next) })
  }
  return visited
}

function runValidation() {
  settingsVisible.value = false
  nodeDrawerVisible.value = false
  selectedNodeName.value = ''
  selectedEdgeId.value = ''
  validationVisible.value = true
  if (validationSummary.value.errors) ElMessage.error(`流程有 ${validationSummary.value.errors} 个错误，请按右侧清单处理`)
  else ElMessage.success('流程校验通过')
}

function focusValidationIssue(issue:ValidationIssue) {
  if (issue.nodeName && nodeByName(issue.nodeName)) openNodeDrawer(issue.nodeName)
  else if (issue.code === 'flow-name') openSettings()
}

function showOverview() {
  settingsVisible.value = false
  nodeDrawerVisible.value = false
  validationVisible.value = false
  selectedNodeName.value = ''
  selectedEdgeId.value = ''
}

function currentLayoutKey() {
  return layoutKey(form.id, draftLayoutKey.value)
}

function readLayout() {
  try {
    return JSON.parse(localStorage.getItem(currentLayoutKey()) || '{}')
  } catch {
    return {}
  }
}

function persistLayout() {
  try {
    localStorage.setItem(currentLayoutKey(), JSON.stringify(serializeLayout(flowNodes.value)))
  } catch {
    ElMessage.warning('浏览器无法保存画布布局，本次编辑仍可继续')
  }
}

function rebuildCanvas(layout = readLayout()) {
  flowNodes.value = buildFlowNodes(form.nodesDef, layout)
  flowEdges.value = buildFlowEdges(form.interfaceConnections, form.portConnections)
  selectedEdgeId.value = ''
  void nextTick(() => fitCanvas())
}

function reset(value:any, layout = readLayout()) {
  Object.assign(form, empty(), value, { nodesDef:value.nodesDef || [], interfaceConnections:value.interfaceConnections || [], portConnections:value.portConnections || [] })
  selectedNodeName.value = ''
  nodeDrawerVisible.value = false
  settingsVisible.value = false
  validationVisible.value = false
  selectedEdgeId.value = ''
  openedWorkflowId.value = value.id || null
  rebuildCanvas(layout)
  dirty.value = false
}

async function confirmDiscardChanges() {
  if (!dirty.value) return true
  try {
    await ElMessageBox.confirm('当前流程有未保存修改，继续后这些修改会丢失。', '切换流程', { type:'warning', confirmButtonText:'放弃修改', cancelButtonText:'继续编辑' })
    return true
  } catch {
    return false
  }
}

async function create() {
  if (!await confirmDiscardChanges()) return
  draftLayoutKey.value = newDraftKey()
  openedWorkflowId.value = null
  reset(empty())
  settingsVisible.value = true
}

function exportWorkflow() {
  const payload = {
    flowName: form.name || '未命名流程',
    description: form.description,
    version: form.version,
    status: form.status,
    nodesDef: form.nodesDef,
    interfaceConnections: form.interfaceConnections,
    portConnections: form.portConnections,
  }
  const json = JSON.stringify(payload, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  const safeName = (form.name || '未命名流程').replace(/[\\/:*?"<>|]/g, '_')
  anchor.href = url
  anchor.download = `${safeName}_V${form.version}.json`
  anchor.click()
  URL.revokeObjectURL(url)
}

function uniqueName(prefix:string) {
  let index = 1
  while (form.nodesDef.some((node:any) => node.name === prefix+index)) index++
  return prefix+index
}

function createResourceNode(data:any):NodeDefinition | null {
  if (data.kind === 'model') return createDeviceNode(data.model, uniqueName('device'))
  if (data.kind === 'workflow') return createSubflowNode(data.workflow, uniqueName('subflow'))
  if (data.kind === 'function') return createFunctionNode(data.type, uniqueName(data.type === 'START' ? 'start' : data.type === 'END' ? 'end' : data.type === 'BRANCH' ? 'branch' : 'aggregate'))
  return null
}

function drag(event:DragEvent, data:any) {
  const payload = data.kind === 'model' ? { kind:'model', model:data.model } : data
  if (!contractReady.value) return
  event.dataTransfer?.setData('workflow-resource', JSON.stringify(payload))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}

function suggestedPosition() {
  const index = flowNodes.value.length
  return { x:80+(index%3)*290, y:100+Math.floor(index/3)*170 }
}

function drop(event:DragEvent) {
  try {
    if (!contractReady.value) return
    const data = JSON.parse(event.dataTransfer?.getData('workflow-resource') || '{}')
    const point = screenToFlowCoordinate({ x:event.clientX, y:event.clientY })
    addResource(data, { x:point.x-110, y:point.y-55 })
  } catch {
    ElMessage.error('无法识别拖入的资源')
  }
}

function addResource(data:any, position = suggestedPosition()) {
  if (!contractReady.value) return ElMessage.error(contractError.value || '工作流系统模板尚未加载，暂时不能添加节点')
  let node:NodeDefinition | null
  try {
    node = createResourceNode(data)
  } catch (error:any) {
    return ElMessage.error(error.message || '无法创建节点')
  }
  if (!node) return

  if (node.nodeType === 'DEV_NODE') {
    const model = models.value.find((item:any) => Number(item.id) === Number(node.deviceModelId))
    const deviceIn = (model?.stateMachineInterfaces || []).find((item:any) => item.direction === 'IN' && item.interfaceType === 'WORKFLOW')?.name
    const deviceOut = (model?.stateMachineInterfaces || []).find((item:any) => item.direction === 'OUT' && item.interfaceType === 'STATE')?.name
    if (!deviceIn || !deviceOut) return ElMessage.error('设备模型缺少WORKFLOW输入接口或STATE输出接口')
    const stateOutputs = (node.interfaces || []).filter((item:any) => item.direction === 'OUT' && item.interfaceType === 'STATE')
    const stateInputs = (node.interfaces || []).filter((item:any) => item.direction === 'IN' && item.interfaceType === 'STATE')
    if (stateOutputs.length !== 1 || stateInputs.length !== 1 || !stateOutputs[0].name || !stateInputs[0].name) {
      return ElMessage.error('DEV_NODE系统模板必须各包含一个命名的STATE输入和输出接口')
    }
    form.interfaceConnections.push(
      { connectionType:'NODE_TO_DEVICE', source:{ nodeName:node.name, interfaceName:stateOutputs[0].name }, target:{ deviceModelId:node.deviceModelId, interfaceName:deviceIn } },
      { connectionType:'DEVICE_TO_NODE', source:{ deviceModelId:node.deviceModelId, interfaceName:deviceOut }, target:{ nodeName:node.name, interfaceName:stateInputs[0].name } }
    )
  }

  form.nodesDef.push(node)
  markDirty()
  const canvasNode = buildFlowNodes([node], { [node.name]:position })[0]
  flowNodes.value = [...flowNodes.value, canvasNode]
  syncEdges()
  persistLayout()
  openNodeDrawer(node.name)
}

function syncEdges() {
  flowEdges.value = buildFlowEdges(form.interfaceConnections, form.portConnections)
}

function connectNodes(params:any) {
  const sourceNodeName = flowNodes.value.find(node => node.id === params.source)?.data?.nodeName
  const targetNodeName = flowNodes.value.find(node => node.id === params.target)?.data?.nodeName
  try {
    const created = createCanvasConnection({
      sourceNodeName,
      targetNodeName,
      sourceHandle: params.sourceHandle,
      targetHandle: params.targetHandle,
      nodes: form.nodesDef,
      interfaceConnections: form.interfaceConnections,
      portConnections: form.portConnections
    })
    form[created.collection].push(created.value)
    markDirty()
    syncEdges()
    ElMessage.success(created.collection === 'portConnections' ? '数据端口连接已创建' : '流程接口连接已创建')
  } catch (error:any) {
    ElMessage.error(error.message || '无法创建连接')
  }
}

function selectCanvasNode(event:any) {
  openNodeDrawer(event.node.data.nodeName)
}

function openNodeDrawer(nodeName:string) {
  selectedNodeName.value = nodeName
  selectedEdgeId.value = ''
  settingsVisible.value = false
  validationVisible.value = false
  nodeDrawerVisible.value = true
}

function closeNodeDrawer() {
  showOverview()
}

function clearSelection() {
  showOverview()
}

function selectEdge(event:any) {
  selectedEdgeId.value = event.edge.id
  nodeDrawerVisible.value = false
  selectedNodeName.value = ''
  settingsVisible.value = false
  validationVisible.value = false
}

function removeEdge(edge:any) {
  const next = removeCanvasEdge(edge, form.interfaceConnections, form.portConnections)
  form.interfaceConnections = next.interfaceConnections
  form.portConnections = next.portConnections
  markDirty()
}

function removeDeletedEdges(edges:any[]) {
  edges.forEach(removeEdge)
  syncEdges()
}

function deleteSelectedEdge() {
  const edge = flowEdges.value.find(item => item.id === selectedEdgeId.value)
  if (!edge) return
  removeEdge(edge)
  selectedEdgeId.value = ''
  syncEdges()
}

function renameSelectedNode(value:string) {
  const node = selectedNode.value
  if (!node) return
  const oldName = node.name
  const newName = value.trim()
  if (!newName) return ElMessage.error('节点名称不能为空')
  if (form.nodesDef.some((item:any) => item !== node && item.name === newName)) return ElMessage.error('节点名称不能重复')
  if (oldName === newName) return
  markDirty()
  const layout = serializeLayout(flowNodes.value)
  layout[newName] = layout[oldName] || suggestedPosition()
  delete layout[oldName]
  renameNodeConnections(form.interfaceConnections, oldName, newName)
  renameNodeConnections(form.portConnections, oldName, newName)
  node.name = newName
  selectedNodeName.value = newName
  rebuildCanvas(layout)
  persistLayout()
}

function replaceSelectedNode(node:NodeDefinition) {
  const index = form.nodesDef.findIndex((item:any) => item.name === selectedNodeName.value)
  if (index < 0) return
  markDirty()
  form.nodesDef[index] = node
  selectedNodeName.value = node.name
  flowNodes.value = flowNodes.value.map(item => item.data?.nodeName === node.name ? {
    ...item,
    data: { ...item.data, nodeName:node.name, nodeType:node.nodeType, functionType:node.functionType, interfaces:node.interfaces || [], ports:node.ports || [] }
  } : item)
  syncEdges()
}

function replacePortConnections(connections:any[]) {
  form.portConnections = connections
  markDirty()
  syncEdges()
}

function portConnectionsFor(nodeName:string, portName:string) {
  return form.portConnections.filter((connection:any) =>
    (connection.source?.nodeName === nodeName && connection.source?.portName === portName) ||
    (connection.target?.nodeName === nodeName && connection.target?.portName === portName))
}

function portConnectionLabel(connection:any) {
  return `${connection.source?.nodeName}.${connection.source?.portName} → ${connection.target?.nodeName}.${connection.target?.portName}`
}

async function confirmRemovePort(portName:string) {
  const node = selectedNode.value
  if (!node) return
  const connections = portConnectionsFor(node.name, portName)
  try {
    await ElMessageBox.confirm(`端口 ${portName} 正在被以下连接使用，确认后将同时删除：\n${connections.map(portConnectionLabel).join('\n')}`, '确认删除端口', { type:'warning', confirmButtonText:'删除端口及连接' })
  } catch {
    return
  }
  const next = removePort(node, portName, form.portConnections)
  replaceSelectedNode(next.node)
  replacePortConnections(next.portConnections)
}

async function removeSelectedNode() {
  const node = selectedNode.value
  if (!node) return
  try {
    await ElMessageBox.confirm('删除节点后，与该节点相关的全部连接也会删除','确认删除节点',{ type:'warning' })
  } catch {
    return
  }
  form.nodesDef = form.nodesDef.filter((item:any) => item !== node)
  form.interfaceConnections = form.interfaceConnections.filter((item:any) => item.source?.nodeName !== node.name && item.target?.nodeName !== node.name)
  form.portConnections = form.portConnections.filter((item:any) => item.source?.nodeName !== node.name && item.target?.nodeName !== node.name)
  flowNodes.value = flowNodes.value.filter(item => item.data?.nodeName !== node.name)
  nodeDrawerVisible.value = false
  selectedNodeName.value = ''
  validationVisible.value = false
  markDirty()
  syncEdges()
  persistLayout()
}

function autoLayout() {
  flowNodes.value = buildFlowNodes(form.nodesDef)
  persistLayout()
  void nextTick(() => fitCanvas())
}

function fitCanvas() {
  if (flowNodes.value.length) void fitView({ padding:0.2, duration:260 })
}

function openSettings() {
  nodeDrawerVisible.value = false
  selectedNodeName.value = ''
  selectedEdgeId.value = ''
  validationVisible.value = false
  settingsVisible.value = true
}

function modelById(id:any) {
  return models.value.find((item:any) => Number(item.id) === Number(id)) || null
}

function nodeByName(nodeName:string) {
  return form.nodesDef.find((node:any) => node.name === nodeName) || null
}

function nodeIssues(nodeName:string) {
  const node = nodeByName(nodeName)
  if (!node) return []
  const contractIssues = validateNodeDefinition(node, { deviceModel:modelById(node.deviceModelId) }).map((issue:any) => ({ ...issue, nodeName:node.name }))
  const serverIssues = issuesForNode(node.name).map((issue:any) => ({
    path: issue.path || issue.elementId || 'configuration',
    message: [issue.message, issue.suggestion].filter(Boolean).join('。'),
    nodeName: node.name,
  }))
  const businessIssues = workflowValidationIssues.value
    .filter(issue => issue.nodeName === nodeName && !issue.code.startsWith('node-'))
    .map(issue => ({ path:issue.path || 'topology', message:issue.detail, nodeName }))
  return [...contractIssues, ...serverIssues, ...businessIssues]
}

function serverValidationIssues():ValidationIssue[] {
  return workflowIssueIndex.value.all.map((issue:any, index:number) => ({
    code: `server-${issue.code || index}-${issue.path || issue.elementId || 'workflow'}`,
    severity: issue.blocking ? 'error' : 'warning',
    title: issue.code || '服务端校验提示',
    detail: [issue.message, issue.suggestion].filter(Boolean).join('。'),
    nodeName: nodeNameForIssue(issue),
    path: issue.path,
  }))
}

function nodeNameForIssue(issue:any) {
  if (issue.elementType === 'NODE' && nodeByName(issue.elementId)) return issue.elementId
  const match = String(issue.path || '').match(/^nodes\[(\d+)]/)
  return match ? form.nodesDef[Number(match[1])]?.name : undefined
}

function issuesForNode(nodeName:string) {
  const nodeIndex = form.nodesDef.findIndex((node:any) => node.name === nodeName)
  return workflowIssueIndex.value.all.filter((issue:any) =>
    (issue.elementType === 'NODE' && issue.elementId === nodeName) ||
    String(issue.path || '').startsWith(`nodes[${nodeIndex}]`))
}

function setWorkflowIssues(issues:any[] = []) {
  workflowIssueIndex.value = indexWorkflowIssues(issues)
}

function loadList() {
  if (!workflowListPromise) {
    workflowListPromise = (async () => {
      const response = await workflowApi.list()
      if (!response.data?.success) throw Error(response.data?.message || '加载失败')
      workflows.value = response.data.data || []
    })().finally(() => {
      workflowListPromise = null
    })
  }
  return workflowListPromise
}

async function loadCreationResources() {
  const [modelResponse, instanceResponse, categoryResponse] = await Promise.all([axios.get('/api/device/model/list'),axios.get('/api/device/instance/list'),axios.get('/api/device/category/list')])
  if (modelResponse.data?.success) models.value = modelResponse.data.data || []
  if (instanceResponse.data?.success) instances.value = instanceResponse.data.data || []
  if (categoryResponse.data?.success) categories.value = categoryResponse.data.data || []
}

function initializeContract() {
  if (!contractInitializationPromise) {
    contractInitializationPromise = (async () => {
      try {
        const metadata = await loadFrontendContractMetadata()
        configureWorkflowNodeTemplates(metadata?.workflow?.nodeTemplates)
        contractError.value = ''
        contractReady.value = true
      } catch (error:any) {
        invalidateFrontendContractMetadata()
        contractReady.value = false
        contractError.value = `工作流系统模板加载失败：${error.message || '请检查后端契约服务'}`
        ElMessage.error(contractError.value)
        return
      }
      try {
        await loadCreationResources()
      } catch (error:any) {
        ElMessage.error(error.message || '加载设计资源失败')
      }
    })().finally(() => {
      contractInitializationPromise = null
    })
  }
  return contractInitializationPromise
}

async function loadAll() {
  const [workflowResult] = await Promise.allSettled([loadList(), initializeContract()])
  if (workflowResult.status === 'rejected') ElMessage.error(workflowResult.reason?.message || '加载已有工作流失败')
}

async function saveDraft() {
  if (!contractReady.value) return ElMessage.error(contractError.value || '工作流系统模板尚未加载，不能保存')
  draftSaving.value = true
  const previousLayoutKey = currentLayoutKey()
  const currentLayout = serializeLayout(flowNodes.value)
  try {
    const payload = toAuthoringPayload(form)
    const response = await workflowApi.saveDraft(payload)
    if (!response.data?.success) throw Error(response.data?.message || '保存失败')
    const prepared = response.data.data
    const definition = adoptPreparedWorkflow(prepared)
    if (!definition?.nodesDef) throw Error('服务端没有返回规范化流程定义')
    setWorkflowIssues(prepared.issues || [])
    reset(definition, currentLayout)
    persistLayout()
    if (previousLayoutKey !== currentLayoutKey()) localStorage.removeItem(previousLayoutKey)
    ElMessage.success('流程草稿已保存')
    if ((prepared.issues || []).some((issue:any) => issue.blocking)) runValidation()
    await loadList()
  } catch (error:any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    draftSaving.value = false
  }
}



async function publishAndValidate() {
  if (!contractReady.value) return ElMessage.error(contractError.value || '工作流系统模板尚未加载')
  publishSaving.value = true
  const previousLayoutKey = currentLayoutKey()
  const currentLayout = serializeLayout(flowNodes.value)
  try {
    const payload = toAuthoringPayload(form)
    const response = await workflowApi.publish(payload)
    if (!response.data?.success) throw Error(response.data?.message || '发布失败')
    const prepared = response.data.data
    const definition = adoptPreparedWorkflow(prepared)
    if (!definition?.nodesDef) throw Error('服务端没有返回规范化流程定义')
    setWorkflowIssues(prepared.issues || [])
    if (prepared.published) {
      reset(definition, currentLayout)
      persistLayout()
      if (previousLayoutKey !== currentLayoutKey()) localStorage.removeItem(previousLayoutKey)
      ElMessage.success('流程已保存并启用')
    } else {
      reset(definition, currentLayout)
      persistLayout()
      ElMessage.warning('发布检查未通过，请修复问题后重新发布')
    }
    if ((prepared.issues || []).some((issue) => issue.blocking)) runValidation()
    await loadList()
  } catch (error) {
    ElMessage.error(error.message || '发布失败')
  } finally {
    publishSaving.value = false
  }
}

async function loadWorkflow(id:number | null) {
  if (!id) return
  const previousId = form.id || null
  if (Number(id) === Number(previousId)) return
  if (!await confirmDiscardChanges()) {
    openedWorkflowId.value = previousId
    return
  }
  try {
    await initializeContract()
    if (!contractReady.value) throw Error(contractError.value || '工作流系统模板尚未加载')
    const response = await workflowApi.detail(id)
    if (!response.data?.success) throw Error(response.data?.message || '加载失败')
    const workflow = response.data.data
    setWorkflowIssues([])
    reset(workflow)
  } catch (error:any) {
    openedWorkflowId.value = previousId
    ElMessage.error(error.message || '加载流程失败')
  }
}

function handleBeforeUnload(event:BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => { void loadAll(); window.addEventListener('beforeunload', handleBeforeUnload) })
onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))
</script>

<style scoped>
.existing-flow-item{cursor:pointer}
.existing-flow-item:hover{background:#f2f6fc;border-color:#9ebdde}
.existing-flow-item.is-active{background:#eef6ff;border-color:#4f87d4}
.existing-flow-item.is-active strong{color:#1a5fb4}
.existing-icon{background:#f0f4ff;color:#3b6ec7}
.load-mark{color:#4a86c7;font-size:13px;font-weight:700}
.existing-flow-item.is-active .load-mark{color:#1a5fb4}
.workflow-page{height:calc(100vh - 64px);min-height:680px;padding:16px 18px;box-sizing:border-box;overflow:hidden;background:#f3f6fa;color:#172033}.workspace-header{height:66px;display:flex;align-items:center;justify-content:space-between;gap:20px}.title-block{min-width:0}.breadcrumb{margin-bottom:3px;color:#8693a6;font-size:11px}.title-line{display:flex;align-items:center;gap:9px}.title-line h1{max-width:420px;margin:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:22px;line-height:30px}.title-block p{margin:3px 0 0;color:#69778c;font-size:12px}.status-pill{padding:2px 8px;border:1px solid #d6dee9;border-radius:999px;background:#fff;color:#67758a;font-size:10px;font-weight:700;letter-spacing:.04em}.status-pill.active{border-color:#b9e4cd;background:#effaf4;color:#16804a}.model-id{color:#8b97a8;font-size:11px}.header-actions{display:flex;align-items:center;gap:8px}.workflow-select{width:220px}.workspace-shell{height:calc(100% - 66px);display:flex;gap:12px;min-height:0}.resource-panel{width:286px;flex:0 0 286px;display:flex;flex-direction:column;min-height:0;border:1px solid #dfe6ef;border-radius:10px;background:#fff;box-shadow:0 2px 10px rgba(28,48,74,.04);overflow:hidden}.resource-heading{padding:16px 14px 12px;border-bottom:1px solid #edf1f6}.resource-heading>div{display:flex;align-items:baseline;justify-content:space-between;margin-bottom:12px}.resource-heading strong{font-size:15px}.resource-heading span{color:#8a96a8;font-size:11px}.function-section{padding:13px 14px 4px}.section-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:9px}.section-title span{color:#4c5a70;font-size:12px;font-weight:700}.section-title small{color:#9aa5b5}.function-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.function-card{display:flex;align-items:center;gap:8px;min-width:0;padding:9px 8px;border:1px solid #e1e8f1;border-radius:7px;background:#fafcff;color:#26344a;text-align:left;cursor:grab;transition:.16s ease}.function-card:hover{border-color:#98b8df;background:#f4f8fd;box-shadow:0 3px 10px rgba(59,105,160,.08);transform:translateY(-1px)}.function-icon{width:27px;height:27px;display:grid;place-items:center;flex:none;border-radius:6px;background:#eef4fb;color:#2f6fb9;font-size:11px;font-weight:800}.function-icon.start{background:#eaf8f0;color:#218654}.function-icon.end{background:#f1f3f6;color:#536073}.function-icon.branch{background:#fff5df;color:#ad7213}.function-icon.aggregate{background:#f2edff;color:#7251b6}.function-card>span:last-child{display:grid;min-width:0}.function-card strong{font-size:12px}.function-card small{color:#8a96a8;font-size:10px}.resource-tabs{min-height:0;display:flex;flex:1;flex-direction:column;padding:0 12px}.resource-tabs :deep(.el-tabs__header){margin:8px 0}.resource-tabs :deep(.el-tabs__content){min-height:0;flex:1;overflow:auto}.resource-tabs :deep(.el-tab-pane){height:100%}.resource-tree{background:transparent}.tree-item{width:100%;display:flex;align-items:center;justify-content:space-between;gap:5px;padding-right:4px}.tree-item.draggable{cursor:grab}.tree-label{display:flex;align-items:center;gap:7px;min-width:0}.tree-label>span:last-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.tree-dot{width:7px;height:7px;border-radius:50%;background:#c2cad5}.tree-dot.model{background:#4f8fd4}.tree-dot.instance{border:1px solid #7f9bb9;background:#fff}.tree-item small{color:#9aa5b5;font-size:10px}.flow-list{display:grid;gap:7px;padding-bottom:10px}.flow-item{width:100%;display:grid;grid-template-columns:28px minmax(0,1fr) 20px;align-items:center;gap:8px;padding:10px;border:1px solid #e5eaf1;border-radius:7px;background:#fff;color:#26344a;text-align:left;cursor:grab}.flow-item:hover{border-color:#9ebdde;background:#f8fbff}.flow-icon{width:27px;height:27px;display:grid;place-items:center;border-radius:6px;background:#ecf8f7;color:#23827b;font-weight:700}.flow-item>span:nth-child(2){display:grid;min-width:0}.flow-item strong,.flow-item small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.flow-item strong{font-size:12px}.flow-item small{color:#8a96a8;font-size:10px}.add-mark{color:#4a86c7;font-size:16px}.canvas-panel{min-width:0;display:flex;flex:1;flex-direction:column;border:1px solid #dfe6ef;border-radius:10px;background:#fff;box-shadow:0 3px 12px rgba(28,48,74,.05);overflow:hidden}.canvas-toolbar{height:48px;display:flex;align-items:center;justify-content:space-between;flex:none;padding:0 13px 0 16px;border-bottom:1px solid #e7ecf2}.canvas-title{display:flex;align-items:center;gap:10px}.canvas-title strong{font-size:14px}.canvas-count{padding-left:10px;border-left:1px solid #dfe5ed;color:#7c899b;font-size:11px}.canvas-actions{display:flex;align-items:center;gap:7px}.flow-stage{position:relative;min-height:0;flex:1;background:#f8fafc}.workflow-flow{width:100%;height:100%}.workflow-flow :deep(.vue-flow__pane){cursor:grab}.workflow-flow :deep(.vue-flow__edge-path){stroke:#6f8fb7;stroke-width:2}.workflow-flow :deep(.vue-flow__edge.selected .vue-flow__edge-path){stroke:#e05252;stroke-width:2.5}.workflow-flow :deep(.vue-flow__controls){overflow:hidden;border:1px solid #dce4ee;border-radius:7px;box-shadow:0 3px 12px rgba(30,50,75,.1)}.workflow-flow :deep(.vue-flow__controls-button){border-bottom-color:#e7ecf2;background:#fff}.canvas-node{position:relative;width:218px;min-height:96px;display:flex;border:1px solid #c8d5e5;border-radius:9px;background:#fff;box-shadow:0 4px 13px rgba(34,61,94,.09);overflow:visible;transition:border-color .15s,box-shadow .15s,transform .15s}.canvas-node:hover{border-color:#83a7d3;box-shadow:0 6px 18px rgba(34,61,94,.14)}.canvas-node.selected{border-color:#3277c5;box-shadow:0 0 0 3px rgba(50,119,197,.14),0 6px 18px rgba(34,61,94,.13)}.canvas-node.warning{border-color:#e0ae54}.node-accent{width:5px;flex:none;border-radius:8px 0 0 8px;background:#5f8fc8}.canvas-node.start .node-accent{background:#3ca36b}.canvas-node.end .node-accent{background:#657386}.canvas-node.branch .node-accent{background:#d89427}.canvas-node.aggregate .node-accent{background:#8566c2}.canvas-node.subflow .node-accent{background:#32958d}.node-main{min-width:0;display:flex;flex:1;flex-direction:column;padding:12px 14px}.node-topline{display:flex;align-items:center;gap:6px;margin-bottom:8px}.node-glyph{width:20px;height:20px;display:grid;place-items:center;border-radius:5px;background:#eef4fb;color:#3978bd;font-size:9px;font-weight:800}.start .node-glyph{background:#eaf8f0;color:#218654}.end .node-glyph{background:#f0f2f5;color:#536073}.branch .node-glyph{background:#fff4de;color:#ad7213}.aggregate .node-glyph{background:#f2edff;color:#7251b6}.subflow .node-glyph{background:#eaf7f6;color:#237f78}.node-kind{color:#78869a;font-size:10px;font-weight:700}.warning-dot{width:16px;height:16px;display:grid;place-items:center;margin-left:auto;border-radius:50%;background:#fff1d6;color:#aa6a00;font-size:10px;font-weight:800}.node-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#213047;font-size:14px}.node-summary{margin-top:5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#7d899a;font-size:11px}.workflow-handle{width:11px!important;height:11px!important;border:2px solid #fff!important;background:#4f83bf!important;box-shadow:0 0 0 1px #4f83bf}.input-handle{left:-6px!important}.output-handle{right:-6px!important}.empty-canvas{position:absolute;left:50%;top:47%;display:grid;place-items:center;width:340px;transform:translate(-50%,-50%);pointer-events:none;text-align:center}.empty-illustration{position:relative;width:120px;height:68px;margin-bottom:15px}.empty-illustration span{position:absolute;width:42px;height:28px;border:1px solid #c8d6e7;border-radius:6px;background:#fff;box-shadow:0 3px 8px rgba(55,78,106,.05)}.empty-illustration span:nth-child(1){left:0;top:20px}.empty-illustration span:nth-child(2){right:0;top:20px}.empty-illustration span:nth-child(3){left:39px;top:0;border-color:#92afd2;background:#f5f9fd}.empty-illustration:before,.empty-illustration:after{content:'';position:absolute;top:35px;width:39px;border-top:1px dashed #a9bed7}.empty-illustration:before{left:34px;transform:rotate(-17deg)}.empty-illustration:after{right:34px;transform:rotate(17deg)}.empty-canvas strong{color:#44536a;font-size:14px}.empty-canvas p{margin:7px 0 0;color:#8a96a8;font-size:11px;line-height:1.7}.canvas-statusbar{height:31px;display:flex;align-items:center;justify-content:space-between;flex:none;padding:0 13px;border-top:1px solid #e7ecf2;background:#fbfcfe;color:#8490a1;font-size:10px}.canvas-statusbar span{display:flex;align-items:center;gap:6px}.status-dot{width:6px;height:6px;border-radius:50%;background:#43a46d}.drawer-form :deep(.el-form-item){margin-bottom:20px}.drawer-form :deep(.el-select),.drawer-form :deep(.el-input-number){width:100%}.two-column-form{display:grid;grid-template-columns:1fr 1fr;gap:12px}.node-inspector-head{display:flex;align-items:center;gap:11px;padding:12px;margin-bottom:14px;border:1px solid #e3e9f1;border-radius:8px;background:#f8fafc}.inspector-icon{width:38px;height:38px;display:grid;place-items:center;border-radius:8px;background:#eaf2fb;color:#3476bd;font-weight:800}.inspector-icon.start{background:#eaf8f0;color:#218654}.inspector-icon.end{background:#eef1f4;color:#536073}.inspector-icon.branch{background:#fff4de;color:#ad7213}.inspector-icon.aggregate{background:#f2edff;color:#7251b6}.inspector-icon.subflow{background:#eaf7f6;color:#237f78}.node-inspector-head>div{display:grid;gap:3px}.node-inspector-head strong{font-size:14px}.node-inspector-head span{color:#7f8b9b;font-size:11px}.config-alert{margin-bottom:17px}.capability-preview{display:grid;gap:5px;margin:-7px 0 18px;padding:11px;border-left:3px solid #4f87c6;border-radius:4px;background:#f5f8fc}.capability-preview strong{font-size:12px}.capability-preview span,.form-hint{color:#7c899b;font-size:11px;line-height:1.6}.form-hint{margin-top:-10px;margin-bottom:18px}.inspector-section{margin-top:20px;padding-top:17px;border-top:1px solid #e8edf3}.inspector-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:9px}.inspector-title strong{font-size:13px}.inspector-title span{min-width:21px;padding:2px 6px;border-radius:10px;background:#eef2f7;color:#617085;text-align:center;font-size:10px}.interface-list,.connection-list{display:grid;gap:7px}.interface-row{display:flex;align-items:center;gap:9px;padding:9px;border:1px solid #e7ecf2;border-radius:6px}.interface-row>span:last-child{display:grid;gap:2px;min-width:0}.interface-row strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.interface-row small{color:#8b97a8;font-size:10px}.direction{width:28px;padding:3px 4px;border-radius:4px;background:#edf3fa;color:#3476bd;text-align:center;font-size:9px;font-weight:800}.direction.out{background:#edf8f2;color:#238052}.connection-row{display:grid;grid-template-columns:minmax(0,1fr) 16px minmax(0,1fr);align-items:center;gap:4px;padding:8px 9px;border-radius:6px;background:#f6f8fb;color:#5f6e83;font-size:10px}.connection-row span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.connection-row b{text-align:center;color:#9ba7b6}.drawer-footer{display:flex;justify-content:space-between;width:100%}@media(max-width:1100px){.resource-panel{width:250px;flex-basis:250px}.function-grid{grid-template-columns:1fr}.workflow-select{width:180px}.title-block p{display:none}}@media(max-width:820px){.workflow-page{height:auto;min-height:calc(100vh - 64px);overflow:auto}.workspace-header{height:auto;align-items:flex-start;flex-direction:column;padding-bottom:12px}.header-actions{width:100%;flex-wrap:wrap}.workspace-shell{height:720px}.resource-panel{width:220px;flex-basis:220px}.canvas-statusbar span:last-child{display:none}}
.workflow-page{height:calc(100vh - 64px);min-height:620px;padding:0;overflow:hidden;background:#fff;color:#182230}.designer-commandbar{height:54px;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:0 12px 0 14px;border-bottom:1px solid #d9dee6;background:#fff;box-sizing:border-box}.flow-identity{display:grid;gap:2px;min-width:0}.product-path{color:#7a8595;font-size:10px}.flow-title-row{display:flex;align-items:center;gap:7px;min-width:0}.flow-title-row>strong{max-width:320px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:15px}.status-chip,.meta-chip,.dirty-mark{height:18px;display:inline-flex;align-items:center;padding:0 6px;border:1px solid #d7dde6;border-radius:2px;background:#f7f8fa;color:#637083;font-size:9px;font-weight:700}.status-chip.active{border-color:#9fd5b7;background:#edf8f2;color:#167a45}.dirty-mark{border-color:#e8c684;background:#fff8e8;color:#9b650b}.command-actions{display:flex;align-items:center;gap:6px}.command-actions :deep(.el-button+.el-button){margin-left:0}.command-actions :deep(.el-button){border-radius:2px}.workflow-select{width:240px}.designer-grid{height:calc(100% - 54px);min-height:0;display:grid;grid-template-columns:250px minmax(430px,1fr) 368px;gap:0;overflow:hidden}.resource-panel{width:auto;min-width:0;display:flex;flex-direction:column;border:0;border-right:1px solid #d9dee6;border-radius:0;background:#fff;box-shadow:none;overflow:hidden}.panel-titlebar{height:49px;display:flex;align-items:center;justify-content:space-between;gap:8px;flex:none;padding:0 12px;border-bottom:1px solid #e2e6ec;background:#fafbfc;box-sizing:border-box}.panel-titlebar>div{display:grid;gap:1px;min-width:0}.panel-titlebar strong{font-size:13px}.panel-titlebar span,.panel-titlebar small{overflow:hidden;color:#7b8797;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.resource-search{padding:8px 10px;border-bottom:1px solid #e8ebf0}.resource-search :deep(.el-input__wrapper){border-radius:2px;box-shadow:0 0 0 1px #dfe4ea inset}.function-section{padding:0;border-bottom:1px solid #dfe4ea}.contract-error{margin:8px;width:auto}.group-title{height:30px;display:flex;align-items:center;justify-content:space-between;padding:0 10px;background:#f5f6f8;color:#526074}.group-title strong{font-size:10px;letter-spacing:.04em}.group-title span{font-size:9px}.function-list{display:grid;grid-template-columns:1fr 1fr}.function-item{height:48px;display:grid;grid-template-columns:26px minmax(0,1fr) 12px;align-items:center;gap:6px;padding:5px 8px;border:0;border-right:1px solid #e7eaf0;border-bottom:1px solid #e7eaf0;background:#fff;color:#26364b;text-align:left;cursor:grab}.function-item:nth-child(even){border-right:0}.function-item:hover{background:#edf5ff}.function-item:disabled{cursor:not-allowed;opacity:.5}.function-item>span:nth-child(2){display:grid;min-width:0}.function-item strong,.function-item small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.function-item strong{font-size:11px}.function-item small{color:#8a95a5;font-size:9px}.function-item>b{color:#3978bd;font-size:13px}.function-icon{width:24px;height:24px;border-radius:2px}.resource-tabs{padding:0;min-height:0}.resource-tabs :deep(.el-tabs__header){margin:0}.resource-tabs :deep(.el-tabs__nav-wrap){padding:0 8px;border-bottom:1px solid #e2e6ec}.resource-tabs :deep(.el-tabs__nav-wrap:after){display:none}.resource-tabs :deep(.el-tabs__item){height:36px;font-size:11px}.resource-tabs :deep(.el-tabs__content){padding:4px 6px 8px}.resource-tabs :deep(.el-tree-node__content){height:30px}.tree-item{padding-right:2px}.flow-list{gap:0;padding:0}.flow-item{grid-template-columns:26px minmax(0,1fr) 16px;gap:7px;padding:8px;border:0;border-bottom:1px solid #e5e9ef;border-radius:0}.flow-icon{width:24px;height:24px;border-radius:2px}.canvas-panel{min-width:0;display:flex;flex-direction:column;border:0;border-right:1px solid #d9dee6;border-radius:0;background:#fff;box-shadow:none;overflow:hidden}.canvas-toolbar{height:49px;padding:0 8px 0 12px}.canvas-title{gap:0}.canvas-title strong{padding-right:10px;font-size:13px}.canvas-title>span{padding:0 9px;border-left:1px solid #dfe4ea;color:#738094;font-size:10px}.canvas-title>.error-count{color:#c43d3d;font-weight:700}.canvas-actions{gap:0}.canvas-actions :deep(.el-button+.el-button){margin-left:0}.flow-stage{background:#f7f9fb}.workflow-flow :deep(.vue-flow__controls){border-radius:2px}.empty-workbench{position:absolute;left:50%;top:50%;z-index:3;width:430px;border:1px solid #cfd6df;background:#fff;transform:translate(-50%,-50%);box-shadow:0 8px 26px rgba(30,45,65,.08)}.empty-head{display:flex;align-items:center;gap:10px;padding:12px 14px;border-bottom:1px solid #dfe4ea;background:#f5f7fa}.empty-head>span,.empty-workbench li>span{color:#3576b8;font-size:10px;font-weight:800}.empty-head>div,.empty-workbench li>div{display:grid;gap:2px}.empty-head strong,.empty-workbench li strong{font-size:12px}.empty-head small,.empty-workbench li small{color:#7b8797;font-size:10px}.quick-start{height:72px;display:grid;grid-template-columns:1fr 28px 1fr;align-items:center;padding:0 14px;border-bottom:1px solid #e4e8ed}.quick-start button{height:38px;display:flex;align-items:center;justify-content:center;gap:7px;border:1px solid #bfcbd9;border-radius:2px;background:#fff;color:#2f6196;cursor:pointer}.quick-start button:hover{border-color:#3379bd;background:#eef6ff}.quick-start button:disabled{cursor:not-allowed;opacity:.5}.quick-start i{text-align:center;color:#8793a3}.empty-workbench ol{display:grid;grid-template-columns:1fr 1fr 1fr;margin:0;padding:0;list-style:none}.empty-workbench li{display:flex;gap:7px;padding:10px;border-right:1px solid #e4e8ed}.empty-workbench li:last-child{border-right:0}.canvas-statusbar{height:28px;justify-content:flex-start;gap:15px;padding:0 10px}.status-grow{flex:1}.legend-line{width:15px;height:0;border-top:2px solid #3276d2}.legend-line.data{border-top-color:#7c4dce;border-top-style:dashed}.inspector-panel{min-width:0;display:flex;flex-direction:column;background:#fff;overflow:hidden}.inspector-titlebar{height:49px}.inspector-scroll{min-height:0;flex:1;overflow:auto}.settings-view,.edge-view,.validation-view,.overview-view{min-height:100%;box-sizing:border-box}.settings-view{padding-bottom:12px}.business-note{display:grid;grid-template-columns:26px 1fr;gap:8px;padding:10px 12px;border-bottom:1px solid #e3e7ed}.business-note>b{color:#3276b8;font-size:10px}.business-note>div{display:grid;gap:3px}.business-note strong{font-size:11px}.business-note span{color:#6d798b;font-size:10px;line-height:1.55}.dense-form{padding:12px 12px 2px;border-bottom:1px solid #e3e7ed}.dense-form :deep(.el-form-item){margin-bottom:11px}.dense-form :deep(.el-form-item__label){height:20px;padding:0;color:#556276;font-size:10px;line-height:20px}.dense-form :deep(.el-select),.dense-form :deep(.el-input-number){width:100%}.two-column-form{grid-template-columns:108px 1fr;gap:8px}.wide-action{width:calc(100% - 24px);margin:12px}.connection-type{display:grid;gap:5px;padding:13px 12px;border-bottom:3px solid #3276d2;background:#edf5ff}.connection-type.port{border-color:#7c4dce;background:#f5f0ff}.connection-type span{color:#607086;font-size:10px}.connection-type b{font-size:12px}.property-list{margin:0;border-bottom:1px solid #e1e5eb}.property-list>div{display:grid;grid-template-columns:90px 1fr;border-bottom:1px solid #e8ebef}.property-list>div:last-child{border-bottom:0}.property-list dt,.property-list dd{margin:0;padding:9px 10px;font-size:10px;line-height:1.55}.property-list dt{background:#f7f8fa;color:#697587}.property-list dd{color:#27364a}.validation-summary,.overview-metrics{display:grid;grid-template-columns:repeat(3,1fr);border-bottom:1px solid #dfe4ea}.validation-summary>div,.overview-metrics>div{display:grid;place-items:center;gap:2px;padding:12px 4px;border-right:1px solid #e2e6eb}.validation-summary>div:last-child,.overview-metrics>div:last-child{border-right:0}.validation-summary strong,.overview-metrics strong{font-size:18px}.validation-summary span,.overview-metrics span{color:#7d8898;font-size:9px}.issue-list{display:grid}.issue-list button{display:grid;grid-template-columns:38px minmax(0,1fr) 12px;align-items:start;gap:8px;padding:10px 12px;border:0;border-bottom:1px solid #e4e8ed;background:#fff;text-align:left;cursor:pointer}.issue-list button:hover{background:#f6f8fb}.issue-list button>b{padding:2px 4px;background:#fdeaea;color:#b52f2f;font-size:9px;text-align:center}.issue-list button.warning>b{background:#fff2d7;color:#9a6200}.issue-list button>span{display:grid;gap:3px}.issue-list strong{font-size:11px}.issue-list small{color:#748194;font-size:10px;line-height:1.5}.issue-list i{color:#9aa4b2}.overview-metrics{grid-template-columns:repeat(4,1fr)}.section-heading{height:34px;display:flex;align-items:center;justify-content:space-between;padding:0 12px;border-top:1px solid #dfe4ea;border-bottom:1px solid #dfe4ea;background:#f6f7f9}.section-heading strong{font-size:11px}.section-heading span{color:#8a95a4;font-size:9px}.check-list{margin:0;padding:0;list-style:none}.check-list li{display:grid;grid-template-columns:14px 1fr;gap:8px;padding:10px 12px;border-bottom:1px solid #e6e9ee}.check-list i{width:10px;height:10px;margin-top:2px;border:2px solid #d34e4e;border-radius:50%;box-sizing:border-box}.check-list li.ok i{border-color:#2a9a5b;background:#2a9a5b;box-shadow:inset 0 0 0 2px #fff}.check-list span{display:grid;gap:2px}.check-list strong{font-size:11px}.check-list small{color:#788496;font-size:9px;line-height:1.5}.business-flow{display:grid;grid-template-columns:1fr;place-items:center;padding:10px 12px}.business-flow span{width:100%;padding:7px;border:1px solid #dce2e9;background:#fafbfc;box-sizing:border-box;text-align:center;font-size:10px}.business-flow b{color:#5383b4;font-size:10px}.overview-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;padding:0 12px 12px}.overview-actions :deep(.el-button+.el-button){margin-left:0}@media(max-width:1280px){.designer-grid{grid-template-columns:224px minmax(400px,1fr) 340px}.workflow-select{width:190px}.command-actions :deep(.el-button){padding-left:10px;padding-right:10px}}@media(max-width:1050px){.workflow-page{overflow:auto}.designer-grid{min-width:980px}.product-path,.canvas-title>span:not(.error-count),.status-grow{display:none}}
</style>
