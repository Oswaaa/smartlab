<template>
  <div class="workflow-page">
    <div class="designer-grid">
      <aside class="resource-panel sl-asset-tree">
        <div class="tree-header-bar">
          <strong class="tree-header-title">节点资源</strong>
        </div>
        <el-tabs v-model="tab" class="resource-tabs" stretch>
          <el-tab-pane label="设备库" name="devices">
            <div class="tree-search-bar">
              <input v-model="resourceKeyword" class="tree-search-input" placeholder="搜索类别、模型或实例..." />
            </div>
            <div class="tree-list-scroll">
              <el-tree v-if="filteredDeviceTree.length" :data="filteredDeviceTree" node-key="key" default-expand-all :indent="16" :expand-on-click-node="false">
                <template #default="{ data }">
                  <div
                    class="t-row"
                    :class="[`node-type-${data.kind}`, { 'is-draggable': data.kind === 'instance' && canEdit }]"
                    :draggable="data.kind === 'instance' && canEdit"
                    @dragstart="drag($event, data)"
                    @dblclick="data.kind === 'instance' && addResource({ kind:'instance', instance:data.instance, model:data.model })"
                    :title="data.kind === 'instance' ? '按住拖拽至画布，或双击添加设备实例' : data.kind === 'model' ? '设备模型' : '设备分类'"
                  >
                    <span class="t-row-left">
                      <el-icon v-if="data.kind === 'category'" class="t-icon category-icon"><Folder /></el-icon>
                      <el-icon v-else-if="data.kind === 'model'" class="t-icon model-icon"><Document /></el-icon>
                      <el-icon v-else class="t-icon instance-icon"><Cpu /></el-icon>
                      <span class="t-label">{{ data.label }}</span>
                    </span>
                    <div class="t-row-right">
                      <span v-if="data.count != null" class="t-badge">{{ data.count }}</span>
                    </div>
                  </div>
                </template>
              </el-tree>
              <div v-else class="tree-empty">没有匹配的设备资源</div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="流程库" name="workflows">
            <div class="tree-search-bar">
              <input v-model="resourceKeyword" class="tree-search-input" placeholder="搜索流程..." />
            </div>
            <div class="tree-list-scroll">
              <el-tree :data="workflowTree" node-key="key" default-expand-all :indent="16" :expand-on-click-node="false">
                <template #default="{ data }">
                  <div
                    class="t-row"
                    :class="[
                      `node-type-${data.type}`,
                      {
                        'group-header-row': data.type === 'group-header',
                        active: data.workflow?.id === openedWorkflowId,
                        'is-draggable': data.workflow && canDragWorkflowResource(data.workflow, form.id, canEdit)
                      }
                    ]"
                    :draggable="data.workflow && canDragWorkflowResource(data.workflow, form.id, canEdit)"
                    @dragstart.stop="data.workflow && drag($event, { kind:'workflow', workflow:data.workflow })"
                    @click="data.workflow && loadWorkflow(data.workflow.id)"
                  >
                    <span class="t-row-left" :title="data.label">
                      <span v-if="data.type === 'group-header'" class="t-group-label">{{ data.label }}</span>
                      <span v-else-if="data.type === 'empty-hint'" class="t-empty-label">{{ data.label }}</span>
                      <template v-else>
                        <el-icon class="t-icon workflow-icon"><Document /></el-icon>
                        <span class="t-label">{{ data.label }}</span>
                      </template>
                    </span>
                    <div v-if="data.type === 'workflow'" class="t-row-right">
                      <span class="t-badge">{{ data.versionLabel }}</span>
                      <span class="t-badge" :class="data.status === 'ACTIVE' ? 'is-active' : 'is-draft'">{{ data.statusLabel }}</span>
                    </div>
                    <div v-else-if="data.count != null" class="t-row-right">
                      <span class="t-badge">{{ data.count }}</span>
                    </div>
                  </div>
                </template>
              </el-tree>
            </div>
          </el-tab-pane>
        </el-tabs>
      </aside>

      <main class="canvas-panel">
        <!-- 顶部信息栏 -->
        <div class="canvas-commandbar">
          <div class="island-left">
            <div class="flow-title-row">
              <strong>{{ form.name || '未命名流程' }}</strong>
              <span class="status-chip" :class="form.status === 'ACTIVE' ? 'is-active' : 'is-draft'">
                <i class="chip-dot"></i>{{ form.status === 'ACTIVE' ? '已启用' : '草稿' }}
              </span>
              <span class="meta-chip">V{{ form.version }}</span>
              <span class="stats-chip">{{ form.nodesDef.length }} 节点 · {{ nodeConnections.length }} 连接</span>
              <span v-if="validationSummary.errors" class="error-badge">{{ validationSummary.errors }} 错误</span>
              <span v-if="dirty" class="dirty-mark">未保存</span>
            </div>
          </div>

          <div class="island-right">
            <button type="button" class="btn-aliyun-cta" @click="create">
              <el-icon><Plus /></el-icon><span>新建</span>
            </button>
            <button v-if="form.id && !isEditing" type="button" class="btn-aliyun" @click="startEditing">编辑</button>
            <button v-if="canEdit" type="button" class="btn-aliyun" :disabled="!form.nodesDef.length" @click="clearCanvas">清空</button>
            <button type="button" class="btn-aliyun" :disabled="validating || draftSaving || publishSaving || copySaving" @click="runValidation">{{ validating ? '校验中' : '校验' }}</button>
            <button type="button" class="btn-aliyun" :disabled="!form.name" @click="exportWorkflow">导出</button>
            <button type="button" class="btn-aliyun" :disabled="!canEdit || draftSaving || copySaving" @click="saveDraft">{{ draftSaving ? '保存中' : '保存草稿' }}</button>
            <button type="button" class="btn-aliyun" :disabled="copySaving || draftSaving || publishSaving" @click="saveAsNew">{{ copySaving ? '保存中' : '保存为新流程' }}</button>
            <button type="button" class="btn-aliyun-cta" :disabled="!canEdit || publishSaving || copySaving" @click="publishAndValidate">{{ publishSaving ? '发布中' : '发布启用' }}</button>
            <button
              v-if="form.id"
              type="button"
              class="btn-link danger"
              :disabled="deleteLoading || draftSaving || publishSaving || copySaving"
              @click="deleteCurrentWorkflow"
            >删除</button>
          </div>
        </div>

        <div class="flow-stage" @dragover.prevent @drop="drop">
          <div class="canvas-floating-controls">
            <span class="control-bar-label">功能节点</span>
            <div class="control-btn-group"><button v-for="item in palette" :key="item.type" class="flow-control-tool" :disabled="!canEdit" :draggable="canEdit" @dragstart="drag($event,{kind:'function',type:item.type})" @click="addResource({kind:'function',type:item.type})"><span class="tool-icon" :class="item.type.toLowerCase()">{{ item.glyph }}</span><span class="tool-name">{{ item.label }}</span></button></div>
          </div>
          <VueFlow v-model:nodes="flowNodes" v-model:edges="flowEdges" class="workflow-flow" :nodes-draggable="canEdit" :nodes-connectable="canEdit" :min-zoom="0.35" :max-zoom="1.8" :snap-to-grid="false" :edge-types="edgeTypes" :default-edge-options="defaultEdgeOptions" :delete-key-code="null" :fit-view-on-init="true" @connect="connectNodes" @connect-start="beginCanvasConnection" @connect-end="endCanvasConnection" @node-click="selectCanvasNode" @pane-click="clearSelection" @node-drag="snapDraggedNode" @node-drag-stop="onNodeDragStop" @edge-click="selectEdge" @edges-delete="removeDeletedEdges">
            <Background id="grid-lines" variant="lines" pattern-color="#f3f5f8" :gap="8" :size="1" />
            <Controls position="bottom-left" :show-interactive="true" :show-zoom="true" :show-fit-view="true">
              <ControlButton title="按执行关系从左向右自动布局" @click="autoLayout">
                <el-icon class="auto-layout-icon"><Grid /></el-icon>
              </ControlButton>
            </Controls>
            <template #node-workflow="slotProps">
              <WorkflowCanvasNode :node="nodeByName(slotProps.data.nodeName)" :selected="slotProps.selected" :issues="nodeIssues(slotProps.data.nodeName)" :device-capabilities="capabilitiesForCanvasNode(slotProps.data.nodeName)" :hide-port-tooltips="connectionInProgress" />
            </template>
            <template #edge-workflow="edgeProps">
              <WorkflowCanvasEdge v-bind="edgeProps" :hide-tooltips="connectionInProgress" />
            </template>
          </VueFlow>
          <section v-if="!flowNodes.length" class="empty-workbench">
            <div class="empty-head"><span>01</span><div><strong>建立流程骨架</strong><small>一个可执行流程从开始节点进入，在结束节点退出</small></div></div>
            <div class="quick-start single-action">
              <button :disabled="!canEdit" @click.stop="addResource({kind:'function',type:'START'})"><b>▶</b><span>添加开始节点</span></button>
            </div>
          </section>
        </div>

        <footer class="canvas-statusbar">
          <span><i class="legend-line workflow"></i>执行流</span>
          <span><i class="legend-line data"></i>数据流</span>
          <span class="status-grow">拖动节点连接点建立关系 · 滚轮缩放画布</span>
          <span><i class="status-dot"></i>布局保存在当前浏览器</span>
        </footer>
      </main>
      <aside class="inspector-panel workflow-overview-panel">
        <div class="panel-titlebar inspector-titlebar">
          <div><strong>流程属性</strong><span>设置与建模检查</span></div>
        </div>
        <div class="inspector-scroll">
          <section class="overview-view">
            <div class="config-section">
              <div class="section-heading"><strong>流程基本配置</strong><span>身份与描述</span></div>
              <el-form label-position="top" class="dense-form">
                <el-form-item label="流程名称">
                  <el-input v-model="form.name" :disabled="!canEdit" maxlength="80" placeholder="例如：恒温反应实验流程" @input="markDirty" />
                </el-form-item>
                <el-form-item label="流程描述">
                  <el-input v-model="form.description" :disabled="!canEdit" type="textarea" :rows="3" maxlength="500" placeholder="说明前置条件、执行目标和适用范围" @input="markDirty" />
                </el-form-item>
              </el-form>
            </div>
            <div class="section-heading"><strong>静态建模检查</strong><span>即时本地提示 · {{ validationSummary.errors }} 错误 · {{ validationSummary.warnings }} 提醒</span></div>
            <div class="overview-metrics"><div><strong>{{ form.nodesDef.length }}</strong><span>节点</span></div><div><strong>{{ executionConnectionCount }}</strong><span>执行连接</span></div><div><strong>{{ form.portConnections.length }}</strong><span>数据连接</span></div><div><strong>{{ deviceNodeCount }}</strong><span>设备节点</span></div></div>
            <div v-if="workflowValidationIssues.length" class="validation-groups">
              <section v-if="flowValidationIssues.length" class="validation-group">
                <div class="issue-group-heading"><strong>流程问题</strong><span>{{ flowValidationIssues.length }} 项</span></div>
                <div class="issue-list">
                  <button v-for="issue in flowValidationIssues" :key="issue.code" :class="issue.severity" @click="focusValidationIssue(issue)">
                    <b>{{ issue.severity === 'error' ? '错误' : '提醒' }}</b>
                    <span><strong>{{ issue.title }}</strong><small>{{ issue.detail }}</small></span>
                    <i>›</i>
                  </button>
                </div>
              </section>
              <section v-if="nodeValidationIssues.length" class="validation-group">
                <div class="issue-group-heading"><strong>节点问题</strong><span>{{ nodeValidationIssues.length }} 项</span></div>
                <div class="issue-list">
                  <button v-for="issue in nodeValidationIssues" :key="issue.code" :class="issue.severity" @click="focusValidationIssue(issue)">
                    <b>{{ issue.severity === 'error' ? '错误' : '提醒' }}</b>
                    <span><strong>{{ issue.title }}</strong><small>{{ issue.detail }}</small></span>
                    <i>›</i>
                  </button>
                </div>
              </section>
            </div>
            <p v-else-if="lastPublishCheck?.executable" class="overview-empty">
              <strong>发布检查通过</strong>
              <span>编译、设备模型与子流程引用检查均已通过</span>
            </p>
            <p v-else class="overview-empty">
              <strong>暂无即时建模问题</strong>
              <span>点校验可核对设备模型与子流程引用</span>
            </p>
          </section>
        </div>
      </aside>
    </div>
    <el-drawer v-model="elementDrawerVisible" :with-header="false" class="workflow-element-drawer" size="50%" append-to-body @closed="clearElementSelection">
      <div class="workflow-element-drawer-body">
        <WorkflowNodeInspector v-if="selectedNode" :visible="elementDrawerVisible" :node="selectedNode" :readonly="!canEdit" :protocol-metadata="protocolMetadata" :errors="selectedNodeIssues" :device-capabilities="selectedDeviceModel?.capabilities || []" :device-attributes="selectedDeviceModel?.attributes || []" :interface-connections="form.interfaceConnections" :port-connections="form.portConnections" :contract-ready="contractReady" @close="closeElementDrawer" @rename="renameSelectedNode" @update:node="replaceSelectedNode" @update:interface-connections="replaceInterfaceConnections" @update:port-connections="replacePortConnections" @remove-port-request="confirmRemovePort" @remove-node="removeSelectedNode" />
        <section v-else-if="selectedEdge" class="edge-view">
          <div class="connection-type" :class="selectedEdgeDetails.kind.toLowerCase()"><span>{{ selectedEdgeDetails.kind === 'PORT' ? '数据流' : '执行流' }}</span><b>{{ selectedEdgeDetails.title }}</b></div>
          <dl class="property-list">
            <div v-for="row in selectedEdgeDetails.rows" :key="row.label"><dt>{{ row.label }}</dt><dd>{{ row.value }}</dd></div>
          </dl>
          <button v-if="canEdit" type="button" class="wide-action btn-link danger" @click="deleteSelectedEdge">删除该连接</button>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Cpu, Document, Folder, Grid, Plus } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls, ControlButton } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import {
  buildFlowEdges,
  buildFlowNodes,
  buildWorkflowAutoLayout,
  createCanvasConnection,
  layoutKey,
  removeCanvasEdge,
  renameNodeConnections,
  serializeLayout,
  unwrapStoredLayout,
  wrapStoredLayout,
  snapWorkflowNodePosition,
  formatWorkflowTriggerText,
  workflowPortValueSummary,
  parseHandleId,
} from '../../../utils/workflowCanvas.js'
import WorkflowCanvasNode from './components/WorkflowCanvasNode.vue'
import WorkflowCanvasEdge from './components/WorkflowCanvasEdge.vue'
import WorkflowNodeInspector from './components/WorkflowNodeInspector.vue'
import { invalidateFrontendContractMetadata, loadFrontendContractMetadata } from '../../../services/frontendContractMetadata.js'
import { workflowApi } from '../../../services/workflowApi.js'
import { adoptPreparedWorkflow, indexWorkflowIssues, toDesignerWorkflow, toWorkflowModelDocument, workflowModelName } from '../../../utils/workflowAuthoring.js'
import { configureWorkflowNodeTemplates, createDeviceNode, createFunctionNode, createSubflowNode, rehydrateWorkflowNodes, removePort, validateNodeDefinition } from '../../../utils/workflowNodeDefinition.js'
import { canDragWorkflowResource, clearWorkflowCanvas, workflowLibraryGroups, workflowNodeConnectionIssues, workflowStatusLabel, workflowSuccessorConflict, workflowUnconnectedPortIssues, workflowVersionLabel } from '../../../utils/workflowDesignerRules.js'

type NodeDefinition = Record<string, any>
type FlowNode = Record<string, any>
type FlowEdge = Record<string, any>
type ValidationIssue = { code:string, severity:'error'|'warning', scope:'flow'|'node', title:string, detail:string, nodeName?:string, path?:string }

const tab = ref('devices')
const workflows = ref<any[]>([])
const models = ref<any[]>([])
const instances = ref<any[]>([])
const categories = ref<any[]>([])
const draftSaving = ref(false)
const publishSaving = ref(false)
const copySaving = ref(false)
const validating = ref(false)
const deleteLoading = ref(false)
const lastPublishCheck = ref<{ executable: boolean } | null>(null)
const resourceKeyword = ref('')
const contractReady = ref(false)
const contractError = ref('')
const protocolMetadata = ref<Record<string, any>>({})
const isEditing = ref(true)
const openedWorkflowId = ref<number | null>(null)
const dirty = ref(false)
const elementDrawerVisible = ref(false)
const selectedNodeName = ref('')
const selectedEdgeId = ref('')
const connectionInProgress = ref(false)
const flowNodes = ref<FlowNode[]>([])
const flowEdges = ref<FlowEdge[]>([])
const draftLayoutKey = ref(newDraftKey())
let workflowListPromise: Promise<void> | null = null
let contractInitializationPromise: Promise<void> | null = null
const { screenToFlowCoordinate, fitView, updateNodeInternals } = useVueFlow()
const workflowIssueIndex = ref(indexWorkflowIssues())

const empty = () => ({ id:null as number | null, name:'', description:'', version:1, status:'DRAFT', nodesDef:[] as NodeDefinition[], interfaceConnections:[] as any[], portConnections:[] as any[] })
const form = reactive<any>(empty())
const palette = [
  { type:'START', label:'开始', glyph:'▶', description:'流程入口' },
  { type:'END', label:'结束', glyph:'■', description:'流程终点' },
  { type:'BRANCH', label:'分支', glyph:'◇', description:'条件路由' },
  { type:'AGGREGATE', label:'汇聚', glyph:'◆', description:'合并路径' }
]
const defaultEdgeOptions = { type:'workflow', markerEnd:'arrowclosed', style:{ stroke:'#7c93b8', strokeWidth:1.8 } }
const edgeTypes = { workflow: markRaw(WorkflowCanvasEdge) }
const canEdit = computed(() => contractReady.value && isEditing.value)

const selectedNode = computed(() => nodeByName(selectedNodeName.value))
const selectedDeviceModel = computed(() => selectedNode.value?.nodeType === 'DEV_NODE' ? modelById(selectedNode.value.deviceModelId) : null)
const selectedNodeIssues = computed(() => nodeValidationIssues.value
  .filter(issue => issue.nodeName === selectedNode.value?.name && issue.severity !== 'warning')
  .map(issue => ({ path:issue.path, title:stripNodeName(issue.title, issue.nodeName), message:issue.detail, nodeName:issue.nodeName })))
const nodeConnections = computed(() => [
  ...form.interfaceConnections.filter((item:any) => item.connectionType === 'NODE_TO_NODE'),
  ...form.portConnections
])
const executionConnections = computed(() => form.interfaceConnections.filter((item:any) => item.connectionType === 'NODE_TO_NODE'))
const executionConnectionCount = computed(() => executionConnections.value.length)
const deviceNodeCount = computed(() => form.nodesDef.filter((node:any) => node.nodeType === 'DEV_NODE').length)
const hasSingleStartEnd = computed(() => form.nodesDef.filter((node:any) => node.functionType === 'START').length === 1 && form.nodesDef.filter((node:any) => node.functionType === 'END').length === 1)
const selectedEdge = computed(() => flowEdges.value.find(edge => edge.id === selectedEdgeId.value) || null)
const selectedEdgeDetails = computed(() => {
  const edge = selectedEdge.value
  if (!edge) return { kind: 'INTERFACE', title: '-', rows: [] }
  const sourceName = editorNodeName(edge.source)
  const targetName = editorNodeName(edge.target)
  const sourceHandle = safeParseHandle(edge.sourceHandle)
  const targetHandle = safeParseHandle(edge.targetHandle)
  const sourceNode = nodeByName(sourceName)
  const targetNode = nodeByName(targetName)
  if (edge.data?.connectionKind === 'PORT') {
    const source = workflowPortValueSummary(sourceNode, sourceHandle.name)
    const target = workflowPortValueSummary(targetNode, targetHandle.name)
    return {
      kind: 'PORT',
      title: `${sourceName} → ${targetName}`,
      rows: [
        { label: '源端口', value: `${source.portName} · ${source.variableName} = ${source.value}` },
        { label: '目标端口', value: `${target.portName} · ${target.variableName} = ${target.value}` },
      ],
    }
  }
  const sourceInterface = (sourceNode?.interfaces || []).find((item: any) => item.name === sourceHandle.name) || {}
  return {
    kind: 'INTERFACE',
    title: `${sourceName} → ${targetName}`,
    rows: [
      { label: '源接口', value: sourceHandle.name || '-' },
      { label: '目标接口', value: targetHandle.name || '-' },
      { label: '触发条件', value: formatWorkflowTriggerText(sourceInterface) || '未配置触发条件' },
    ],
  }
})
const backendValidationIssues = computed<ValidationIssue[]>(() => serverValidationIssues())
const nodeValidationIssues = computed<ValidationIssue[]>(() => [
  ...buildNodeValidationIssues(),
  ...backendValidationIssues.value.filter(issue => issue.scope === 'node'),
])
const flowValidationIssues = computed<ValidationIssue[]>(() => [
  ...buildFlowValidationIssues(),
  ...backendValidationIssues.value.filter(issue => issue.scope === 'flow'),
])
const workflowValidationIssues = computed<ValidationIssue[]>(() => [...flowValidationIssues.value, ...nodeValidationIssues.value])
const validationSummary = computed(() => ({
  errors: workflowValidationIssues.value.filter(issue => issue.severity === 'error').length,
  warnings: workflowValidationIssues.value.filter(issue => issue.severity === 'warning').length
}))
const deviceTree = computed(() => {
  const map = new Map<any, any>()
  categories.value.forEach((category:any) => map.set(category.id || category.categoryId, { key:'c-'+(category.id || category.categoryId), kind:'category', type:'category', label:category.categoryName || category.name, children:[] }))
  const roots:any[] = []
  map.forEach((item:any) => {
    const raw = categories.value.find((category:any) => 'c-'+(category.id || category.categoryId) === item.key)
    const parent = map.get(raw?.parentCategoryId)
    ;(parent ? parent.children : roots).push(item)
  })
  models.value.forEach((model:any) => {
    const category = map.get(model.categoryId)
    const parent = category ? category.children : roots
    const modelInstances = instances.value.filter((item:any) => Number(item.deviceModelId || item.modelId) === Number(model.id))
    parent.push({
      key:'m-'+model.id,
      kind:'model',
      type:'model',
      label:model.modelName,
      model,
      count: modelInstances.length,
      children: modelInstances.map((item:any) => ({ key:'i-'+item.id, kind:'instance', type:'instance', label:item.instanceName || '设备'+item.id, instance:item, model }))
    })
  })
  const withCounts = (nodes:any[]):any[] => nodes.map(node => {
    if (node.kind !== 'category') return node
    const children = withCounts(node.children || [])
    return { ...node, children, count: children.reduce((total:number, child:any) => total + (child.kind === 'instance' ? 1 : Number(child.count || 0)), 0) }
  })
  return withCounts(roots)
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
  return workflows.value.filter(item => !keyword || workflowModelName(item).toLowerCase().includes(keyword) || item.description?.toLowerCase().includes(keyword))
})

const workflowTree = computed(() => workflowLibraryGroups(filteredWorkflows.value).map(group => {
  const children = group.children.map(item => ({
    key:`workflow-${item.id}`,
    label: workflowModelName(item),
    type: 'workflow',
    workflow: item,
    status: item.status,
    versionLabel: workflowVersionLabel(item),
    statusLabel: workflowStatusLabel(item.status),
  }))
  if (!children.length) {
    children.push({
      key: `workflow-empty-${group.key}`,
      type: 'empty-hint',
      label: group.key === 'active' ? '暂无已启用流程' : '暂无草稿流程',
    })
  }
  return {
    key: `workflow-group-${group.key}`,
    label: group.label,
    type: 'group-header',
    count: group.children.length,
    children,
  }
}))

function newDraftKey() {
  return 'draft-'+Date.now()+'-'+Math.random().toString(36).slice(2, 8)
}

function markDirty() {
  if (!canEdit.value) return
  dirty.value = true
  setWorkflowIssues()
  lastPublishCheck.value = null
}

function startEditing() { isEditing.value = true }

async function deleteCurrentWorkflow() {
  const workflowId = Number(form.id)
  if (!Number.isInteger(workflowId) || workflowId <= 0 || deleteLoading.value) return
  const workflowName = form.name || `流程 ${workflowId}`
  try {
    await ElMessageBox.confirm(
      `确认删除流程“${workflowName}”？删除流程后无法恢复。`,
      '删除流程',
      { type:'warning', confirmButtonText:'确认删除', cancelButtonText:'取消' },
    )
  } catch {
    return
  }

  deleteLoading.value = true
  try {
    const response = await workflowApi.delete(workflowId)
    if (!response.data?.success) throw Error(response.data?.message || '删除流程失败')
    localStorage.removeItem(layoutKey(workflowId))
    draftLayoutKey.value = newDraftKey()
    reset(empty())
    isEditing.value = true
    ElMessage.success('流程已删除')
    try {
      await loadList()
    } catch (refreshError:any) {
      ElMessage.warning(refreshError.message || '流程已删除，但流程库刷新失败')
    }
  } catch (error:any) {
    ElMessage.error(error.response?.data?.message || error.message || '删除流程失败')
  } finally {
    deleteLoading.value = false
  }
}

async function clearCanvas() {
  if (!canEdit.value || !form.nodesDef.length) return
  try {
    await ElMessageBox.confirm('只清空画布中的节点和连线，流程名称与描述会保留。保存前不会影响数据库。', '清空画布', { type:'warning', confirmButtonText:'确认清空', cancelButtonText:'取消' })
  } catch { return }
  Object.assign(form, clearWorkflowCanvas(form))
  closeElementDrawer()
  flowNodes.value = []
  flowEdges.value = []
  markDirty()
}

function editorNodeName(value:any) {
  return decodeURIComponent(String(value || '').replace(/^workflow-node:/, '')) || '-'
}

function editorHandleName(value:any) {
  const raw = String(value || '')
  return raw.includes(':') ? raw.slice(raw.indexOf(':') + 1) : raw || '-'
}

function safeParseHandle(value:any) {
  try {
    return parseHandleId(value)
  } catch {
    return { kind: 'INTERFACE', name: editorHandleName(value) }
  }
}

function nodeBusinessLabel(node:any) {
  if (node.nodeType === 'DEV_NODE') return '设备能力调用'
  if (node.nodeType === 'SUBFLOW_NODE') return '子流程调用'
  return ({ START:'流程入口', END:'流程出口', BRANCH:'条件路由', AGGREGATE:'多路汇聚' } as Record<string,string>)[node.functionType] || '功能节点'
}

function buildNodeValidationIssues():ValidationIssue[] {
  const issues:ValidationIssue[] = []
  form.nodesDef.forEach((node:any) => {
    const deviceModel = modelById(node.deviceModelId)
    validateNodeDefinition(node, { deviceModel, deviceAttributes:deviceModel?.attributes || [] }).forEach((issue:any, index:number) => issues.push({
      code:`node-${node.name}-${issue.path || index}`,
      severity:'error',
      scope:'node',
      title:`${node.name}：${nodeIssueTitle(issue.path)}`,
      detail:issue.message,
      nodeName:node.name,
      path:issue.path
    }))
  })
  workflowUnconnectedPortIssues(form.nodesDef, form.portConnections).forEach((issue:any) => issues.push({
    ...issue,
    severity:'warning',
    scope:'node',
    title:`${issue.nodeName}：${issue.title}`,
  }))
  return issues
}

function buildFlowValidationIssues():ValidationIssue[] {
  const issues:ValidationIssue[] = []
  const push = (issue:ValidationIssue) => issues.push(issue)
  if (!String(form.name || '').trim()) push({ code:'flow-name', severity:'error', scope:'flow', title:'未填写流程名称', detail:'请填写用于任务创建和识别的流程名称。' })
  if (!form.nodesDef.length) push({ code:'flow-empty', severity:'error', scope:'flow', title:'流程中没有节点', detail:'请先添加开始节点和结束节点。' })

  const starts = form.nodesDef.filter((node:any) => node.functionType === 'START')
  const ends = form.nodesDef.filter((node:any) => node.functionType === 'END')
  if (starts.length !== 1) push({ code:'start-count', severity:'error', scope:'flow', title:starts.length ? '开始节点过多' : '缺少开始节点', detail:starts.length ? `当前有 ${starts.length} 个开始节点，请仅保留一个。` : '请添加一个开始节点。' })
  if (ends.length !== 1) push({ code:'end-count', severity:'error', scope:'flow', title:ends.length ? '结束节点过多' : '缺少结束节点', detail:ends.length ? `当前有 ${ends.length} 个结束节点，请仅保留一个。` : '请添加一个结束节点。' })

  const nodeNames = new Set(form.nodesDef.map((node:any) => node.name))
  const outgoing = new Map<string,string[]>([...nodeNames].map(name => [name, []]))
  const incoming = new Map<string,string[]>([...nodeNames].map(name => [name, []]))
  executionConnections.value.forEach((connection:any, index:number) => {
    const source = connection.source?.nodeName
    const target = connection.target?.nodeName
    if (!nodeNames.has(source) || !nodeNames.has(target)) {
      push({ code:`dangling-${index}`, severity:'error', scope:'flow', title:'存在失效连接', detail:'请删除该连接，或重新连接有效节点。' })
      return
    }
    outgoing.get(source)?.push(target)
    incoming.get(target)?.push(source)
  })

  workflowNodeConnectionIssues(form.nodesDef, executionConnections.value).forEach((issue:any) => push({
    ...issue,
    severity:'error',
    scope:'flow',
    title:`${issue.nodeName}：${issue.title}`,
  }))

  const indegree = new Map<string,number>([...nodeNames].map(name => [name, incoming.get(name)?.length || 0]))
  const queue = [...nodeNames].filter(name => indegree.get(name) === 0)
  let visited = 0
  while (queue.length) {
    const name = queue.shift() as string
    visited++
    ;(outgoing.get(name) || []).forEach(target => { const next = (indegree.get(target) || 0) - 1; indegree.set(target, next); if (next === 0) queue.push(target) })
  }
  if (form.nodesDef.length && visited !== form.nodesDef.length) push({ code:'graph-cycle', severity:'error', scope:'flow', title:'执行路径存在循环', detail:'请删除形成循环的执行连接。' })
  if (!deviceNodeCount.value && form.nodesDef.length) push({ code:'no-device-node', severity:'warning', scope:'flow', title:'流程中没有设备节点', detail:'如果该流程只负责控制或调用子流程，可以忽略此提醒。' })
  return issues
}

function nodeIssueTitle(path = '') {
  if (path === 'expression') return '计算表达式'
  if (path.startsWith('internalVariables')) return '变量空间'
  if (path.startsWith('ports')) return '数据端口'
  if (path.startsWith('interfaces') || path.startsWith('actions')) return '控制接口'
  if (path.startsWith('lifecycle')) return '生命周期'
  if (path.startsWith('capability')) return '业务配置'
  return '节点配置'
}

function stripNodeName(title = '', nodeName = '') {
  return nodeName && title.startsWith(`${nodeName}：`) ? title.slice(nodeName.length + 1) : title
}

async function runValidation() {
  closeElementDrawer()
  if (validating.value) return
  validating.value = true
  try {
    const payload = toWorkflowModelDocument(form)
    const response = await workflowApi.validate(payload)
    if (!response.data?.success) throw Error(response.data?.message || '校验失败')
    const prepared = response.data.data
    setWorkflowIssues(prepared.issues || [])
    lastPublishCheck.value = { executable: !!prepared.executable }
    if (prepared.executable) ElMessage.success('发布检查通过')
    else {
      const blocking = (prepared.issues || []).filter((issue:any) => issue.blocking).length
      ElMessage.error(blocking ? `发布检查未通过，有 ${blocking} 个阻断问题` : '发布检查未通过，请按右侧清单处理')
    }
  } catch (error:any) {
    ElMessage.error(error.message || '校验失败')
  } finally {
    validating.value = false
  }
}

function focusValidationIssue(issue:ValidationIssue) {
  if (issue.scope === 'node' && issue.nodeName && nodeByName(issue.nodeName)) openNodeDrawer(issue.nodeName)
  else if (issue.code === 'flow-name') ElMessage.info('请在右侧“流程基本配置”中填写流程名称')
  else ElMessage.info(issue.detail)
}

function closeElementDrawer() {
  elementDrawerVisible.value = false
}

function clearElementSelection() {
  selectedNodeName.value = ''
  selectedEdgeId.value = ''
}

function currentLayoutKey() {
  return layoutKey(form.id, draftLayoutKey.value)
}

function readLayout() {
  try {
    return unwrapStoredLayout(JSON.parse(localStorage.getItem(currentLayoutKey()) || 'null'))
  } catch {
    return {}
  }
}

function persistLayout() {
  if (!canEdit.value && form.id) return
  try {
    localStorage.setItem(currentLayoutKey(), JSON.stringify(wrapStoredLayout(flowNodes.value)))
  } catch {
    ElMessage.warning('浏览器无法保存画布布局，本次编辑仍可继续')
  }
}

function snapDraggedNode(event: any) {
  const node = event?.node
  if (!node) return
  const snapped = snapWorkflowNodePosition(nodeByName(node.data?.nodeName), node.position.x, node.position.y)
  node.position.x = snapped.x
  node.position.y = snapped.y
}

function onNodeDragStop(event: any) {
  snapDraggedNode(event)
  persistLayout()
}

function rebuildCanvas(layout = readLayout()) {
  const auto = buildWorkflowAutoLayout(form.nodesDef, executionConnections.value, form.portConnections)
  flowNodes.value = buildFlowNodes(form.nodesDef, { ...auto, ...layout })
  flowEdges.value = buildFlowEdges(form.interfaceConnections, form.portConnections, form.nodesDef)
  selectedEdgeId.value = ''
  void nextTick(() => fitCanvas())
}

function reset(value:any, layout = readLayout()) {
  const designer = toDesignerWorkflow(value)
  Object.assign(form, empty(), designer, { nodesDef:rehydrateWorkflowNodes(designer.nodesDef || []), interfaceConnections:designer.interfaceConnections || [], portConnections:designer.portConnections || [] })
  closeElementDrawer()
  clearElementSelection()
  openedWorkflowId.value = designer.id || null
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
  setWorkflowIssues()
  lastPublishCheck.value = null
  reset(empty())
  isEditing.value = true
}

function exportWorkflow() {
  void downloadWorkflowDocument()
}

async function downloadWorkflowDocument() {
  try {
    let modelDocument
    if (form.id && !dirty.value) {
      const response = await workflowApi.export(form.id)
      if (!response.data?.success) throw Error(response.data?.message || '导出失败')
      modelDocument = response.data.data
    } else {
      modelDocument = toWorkflowModelDocument(form)
    }
    const json = JSON.stringify(modelDocument, null, 2)
    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const anchor = window.document.createElement('a')
    const safeName = (form.name || '未命名流程').replace(/[\\/:*?"<>|]/g, '_')
    anchor.href = url
    anchor.download = `${safeName}_V${form.version || 1}.json`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error:any) {
    ElMessage.error(error.message || '导出失败')
  }
}

function uniqueName(prefix:string) {
  let index = 1
  while (form.nodesDef.some((node:any) => node.name === prefix+index)) index++
  return prefix+index
}

function createResourceNode(data:any):NodeDefinition | null {
  if (data.kind === 'model') return createDeviceNode(data.model, uniqueName('device'))
  if (data.kind === 'instance') {
    const node = createDeviceNode(data.model, uniqueName('device'))
    node.boundInstanceId = data.instance?.id
    return node
  }
  if (data.kind === 'workflow') return createSubflowNode(data.workflow, uniqueName('subflow'))
  if (data.kind === 'function') return createFunctionNode(data.type, uniqueName(data.type === 'START' ? 'start' : data.type === 'END' ? 'end' : data.type === 'BRANCH' ? 'branch' : 'aggregate'))
  return null
}

function drag(event:DragEvent, data:any) {
  if (!canEdit.value) return event.preventDefault()
  if (data.kind === 'workflow' && !canDragWorkflowResource(data.workflow, form.id, canEdit.value)) return event.preventDefault()
  if (data.kind === 'model') {
    event.preventDefault()
    return
  }
  const payload = data.kind === 'instance' ? { kind:'instance', instance:data.instance, model:data.model } : data
  if (!canEdit.value) return
  event.dataTransfer?.setData('workflow-resource', JSON.stringify(payload))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}

function suggestedPosition() {
  if (!flowNodes.value.length) return { x: 80, y: 120 }
  const startY = flowNodes.value.find((node:any) => node.data?.functionType === 'START')?.position?.y
  const rowY = Number.isFinite(Number(startY)) ? Number(startY) : (Number(flowNodes.value[0].position?.y) || 120)
  const right = Math.max(...flowNodes.value.map((node:any) => Number(node.position?.x) || 0))
  return { x: right + 280, y: rowY }
}

function drop(event:DragEvent) {
  try {
    if (!canEdit.value) return
    const data = JSON.parse(event.dataTransfer?.getData('workflow-resource') || '{}')
    const point = screenToFlowCoordinate({ x:event.clientX, y:event.clientY })
    addResource(data, { x:point.x-110, y:point.y-55 })
  } catch {
    ElMessage.error('无法识别拖入的资源')
  }
}

function addResource(data:any, position = suggestedPosition()) {
  if (!canEdit.value) return
  if (data.kind === 'workflow' && !canDragWorkflowResource(data.workflow, form.id, canEdit.value)) {
    return ElMessage.warning('当前流程不能引用自身')
  }
  if (data.kind === 'model') {
    return ElMessage.warning('设备模型不能直接作为节点放至画布，请展开并拖拽具体的“设备实例”')
  }
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
  flowNodes.value = [...flowNodes.value.map(item => ({ ...item, selected: false })), { ...canvasNode, selected: true }]
  selectedNodeName.value = node.name
  selectedEdgeId.value = ''
  syncEdges()
  persistLayout()
}

function syncEdges() {
  flowEdges.value = buildFlowEdges(form.interfaceConnections, form.portConnections, form.nodesDef)
}

function connectNodes(params:any) {
  if (!canEdit.value) return
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

function beginCanvasConnection() {
  connectionInProgress.value = true
}

function endCanvasConnection() {
  connectionInProgress.value = false
}

function selectCanvasNode(event:any) {
  openNodeDrawer(event.node.data.nodeName)
}

function openNodeDrawer(nodeName:string) {
  selectedNodeName.value = nodeName
  selectedEdgeId.value = ''
  elementDrawerVisible.value = true
}

function clearSelection() {
  closeElementDrawer()
}

function selectEdge(event:any) {
  selectedEdgeId.value = event.edge.id
  selectedNodeName.value = ''
  elementDrawerVisible.value = true
}

function removeEdge(edge:any) {
  const next = removeCanvasEdge(edge, form.interfaceConnections, form.portConnections)
  form.interfaceConnections = next.interfaceConnections
  form.portConnections = next.portConnections
  markDirty()
}

function removeDeletedEdges(edges:any[]) {
  if (!canEdit.value) return syncEdges()
  edges.forEach(removeEdge)
  syncEdges()
}

function deleteSelectedEdge() {
  if (!canEdit.value) return
  const edge = flowEdges.value.find(item => item.id === selectedEdgeId.value)
  if (!edge) return
  removeEdge(edge)
  closeElementDrawer()
  syncEdges()
}

function renameSelectedNode(value:string) {
  if (!canEdit.value) return
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
  if (!canEdit.value) return
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
  const canvasNode = flowNodes.value.find(item => item.data?.nodeName === node.name)
  void nextTick(() => updateNodeInternals(canvasNode ? [canvasNode.id] : undefined))
}

function replacePortConnections(connections:any[]) {
  if (!canEdit.value) return
  form.portConnections = connections
  markDirty()
  syncEdges()
}

function replaceInterfaceConnections(connections:any[]) {
  if (!canEdit.value) return
  form.interfaceConnections = connections
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
  if (!canEdit.value) return
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
  if (!canEdit.value) return
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
  closeElementDrawer()
  markDirty()
  syncEdges()
  persistLayout()
}

function autoLayout() {
  if (!canEdit.value) return
  const layout = buildWorkflowAutoLayout(form.nodesDef, executionConnections.value, form.portConnections)
  flowNodes.value = buildFlowNodes(form.nodesDef, layout)
  persistLayout()
  void nextTick(() => {
    updateNodeInternals(flowNodes.value.map(node => node.id))
    fitCanvas()
  })
}

function fitCanvas() {
  if (flowNodes.value.length) void fitView({ padding:0.2, duration:260 })
}

function modelById(id:any) {
  return models.value.find((item:any) => Number(item.id) === Number(id)) || null
}

function capabilitiesForCanvasNode(nodeName:string) {
  const node = nodeByName(nodeName)
  if (!node || node.nodeType !== 'DEV_NODE') return []
  return modelById(node.deviceModelId)?.capabilities || []
}

function nodeByName(nodeName:string) {
  return form.nodesDef.find((node:any) => node.name === nodeName) || null
}

function nodeIssues(nodeName:string) {
  return nodeValidationIssues.value
    .filter(issue => issue.nodeName === nodeName)
    .map(issue => ({ path:issue.path, title:stripNodeName(issue.title, issue.nodeName), message:issue.detail, nodeName }))
}

function serverValidationIssues():ValidationIssue[] {
  return workflowIssueIndex.value.all.map((issue:any, index:number) => ({
    code: `server-${issue.code || index}-${issue.path || issue.elementId || 'workflow'}`,
    severity: issue.blocking ? 'error' : 'warning',
    scope: nodeNameForIssue(issue) ? 'node' : 'flow',
    title: serverIssueTitle(issue),
    detail: [issue.message, issue.suggestion].filter(Boolean).join('。'),
    nodeName: nodeNameForIssue(issue),
    path: issue.path,
  }))
}

function serverIssueTitle(issue:any) {
  const nodeName = nodeNameForIssue(issue)
  if (nodeName) return `${nodeName}：${nodeIssueTitle(issue.path || '')}`
  if (issue.stage === 'CANONICALIZATION') return '模型定义已自动修正'
  if (issue.stage === 'COMPILATION') return '流程定义无法执行'
  return issue.blocking ? '流程检查未通过' : '流程检查提醒'
}

function nodeNameForIssue(issue:any) {
  if (issue.elementType === 'NODE' && nodeByName(issue.elementId)) return issue.elementId
  const match = String(issue.path || '').match(/^nodes\[(\d+)]/)
  return match ? form.nodesDef[Number(match[1])]?.name : undefined
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
        protocolMetadata.value = metadata?.protocol || {}
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

async function openSuccessorIfPresent(body:any) {
  const conflict = workflowSuccessorConflict(body)
  if (!conflict) return false
  try {
    await ElMessageBox.confirm(
      conflict.message || '已有后续版本，请打开该版本继续编辑。',
      '已有后续版本',
      { type:'warning', confirmButtonText:'打开该版本', cancelButtonText:'留在当前页' },
    )
  } catch {
    return true
  }
  dirty.value = false
  await loadWorkflow(conflict.successorId)
  return true
}

async function saveDraft() {
  if (!canEdit.value) return ElMessage.warning('请先点击编辑')
  draftSaving.value = true
  const previousLayoutKey = currentLayoutKey()
  const currentLayout = serializeLayout(flowNodes.value)
  try {
    const payload = toWorkflowModelDocument(form)
    const response = await workflowApi.saveDraft(payload)
    if (!response.data?.success) {
      if (await openSuccessorIfPresent(response.data)) return
      throw Error(response.data?.message || '保存失败')
    }
    const prepared = response.data.data
    const definition = adoptPreparedWorkflow(prepared)
    if (!Array.isArray(definition?.nodesDef)) throw Error('服务端没有返回规范化流程定义')
    setWorkflowIssues(prepared.issues || [])
    reset(definition, currentLayout)
    persistLayout()
    if (previousLayoutKey !== currentLayoutKey()) localStorage.removeItem(previousLayoutKey)
    ElMessage.success(definition.predecessorId ? `已保存为草稿 ${workflowVersionLabel(definition)}` : '流程草稿已保存')
    await loadList()
  } catch (error:any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    draftSaving.value = false
  }
}

async function saveAsNew() {
  if (copySaving.value) return
  copySaving.value = true
  const currentLayout = serializeLayout(flowNodes.value)
  try {
    const payload = toWorkflowModelDocument(form)
    const response = await workflowApi.saveAsNew(payload)
    if (!response.data?.success) throw Error(response.data?.message || '保存失败')
    const prepared = response.data.data
    const definition = adoptPreparedWorkflow(prepared)
    if (!Array.isArray(definition?.nodesDef)) throw Error('服务端没有返回规范化流程定义')
    setWorkflowIssues(prepared.issues || [])
    lastPublishCheck.value = null
    isEditing.value = true
    reset(definition, currentLayout)
    persistLayout()
    ElMessage.success(`已保存为新流程 ${workflowVersionLabel(definition)}`)
    await loadList()
  } catch (error:any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    copySaving.value = false
  }
}



async function publishAndValidate() {
  if (!canEdit.value) return ElMessage.warning('请先点击编辑')
  publishSaving.value = true
  const previousLayoutKey = currentLayoutKey()
  const currentLayout = serializeLayout(flowNodes.value)
  try {
    const payload = toWorkflowModelDocument(form)
    const response = await workflowApi.publish(payload)
    if (!response.data?.success) {
      if (await openSuccessorIfPresent(response.data)) return
      throw Error(response.data?.message || '发布失败')
    }
    const prepared = response.data.data
    const definition = adoptPreparedWorkflow(prepared)
    if (!Array.isArray(definition?.nodesDef)) throw Error('服务端没有返回规范化流程定义')
    setWorkflowIssues(prepared.issues || [])
    lastPublishCheck.value = { executable: !!prepared.executable }
    if (prepared.published) {
      reset(definition, currentLayout)
      persistLayout()
      if (previousLayoutKey !== currentLayoutKey()) localStorage.removeItem(previousLayoutKey)
      isEditing.value = false
      ElMessage.success(prepared.predecessorId
        ? `已发布为 ${workflowVersionLabel(definition)}，原已启用版本仍保留给已绑定任务`
        : '流程已保存并启用')
    } else {
      reset(definition, currentLayout)
      persistLayout()
      if (previousLayoutKey !== currentLayoutKey()) localStorage.removeItem(previousLayoutKey)
      ElMessage.warning(prepared.predecessorId
        ? `已创建草稿 ${workflowVersionLabel(definition)}，发布检查未通过，请按右侧清单修复后重新发布`
        : '发布检查未通过，请修复问题后重新发布')
    }
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
    setWorkflowIssues()
    lastPublishCheck.value = null
    reset(workflow)
    isEditing.value = false
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
/* SmartLab 2.0 Workflow Designer — Design System SSOT (Release 1.3) */
.workflow-page {
  height: calc(100vh - 50px);
  padding: 10px 14px 14px;
  box-sizing: border-box;
  overflow: hidden;
  background: var(--sl-bg-page);
  color: var(--sl-text-body, #334155);
  font-family: var(--sl-font-family);
}

.designer-grid {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr) 304px;
  background: var(--sl-bg-surface, #ffffff);
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-lg, 10px);
  overflow: hidden;
  box-shadow: var(--sl-shadow-container);
}

.resource-panel,
.canvas-panel,
.inspector-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
}

.canvas-panel {
  border-right: 1px solid var(--sl-border-base, #e2e8f0);
}

.panel-titlebar {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex: none;
  padding: 0 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  box-sizing: border-box;
}

.panel-titlebar > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.panel-titlebar strong {
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.panel-titlebar span {
  overflow: hidden;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-tabs {
  min-height: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 0;
  height: 100%;
}

.resource-tabs :deep(.el-tabs__header) {
  margin: 0;
  flex: none;
}

.resource-tabs :deep(.el-tabs__nav-wrap) {
  padding: 0 8px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: var(--sl-bg-page, #f1f5f9);
}

.resource-tabs :deep(.el-tabs__nav-wrap:after) {
  display: none;
}

.resource-tabs :deep(.el-tabs__item) {
  height: 36px;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12.5px;
}

.resource-tabs :deep(.el-tabs__item.is-active) {
  color: var(--sl-primary, #2563eb);
  font-weight: 600;
}

.resource-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background: var(--sl-primary, #2563eb);
}

.resource-tabs :deep(.el-tabs__content) {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
}

.resource-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.canvas-commandbar {
  height: 52px;
  min-height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  box-sizing: border-box;
  flex: none;
}

.island-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.flow-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.flow-title-row > strong {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--sl-text-heading, #0f172a);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
}

.status-chip,
.meta-chip,
.stats-chip,
.dirty-mark,
.error-badge {
  height: 20px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: var(--sl-bg-hover, #f8fafc);
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.status-chip .chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sl-text-secondary, #64748b);
}

.status-chip.is-active .chip-dot {
  background: var(--sl-success, #16a34a);
}

.error-badge {
  border-color: var(--sl-danger-border, #fecaca);
  background: var(--sl-danger-light, #fef2f2);
  color: var(--sl-danger, #dc2626);
}

.dirty-mark {
  border-color: var(--sl-warning-border, #fde68a);
  background: var(--sl-warning-light, #fffbeb);
  color: var(--sl-warning, #d97706);
}

.island-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  flex: none;
}

.island-right .btn-aliyun,
.island-right .btn-aliyun-cta,
.island-right .btn-primary-blue {
  flex-shrink: 0;
}

.island-right .btn-aliyun:disabled,
.island-right .btn-primary-blue:disabled,
.island-right .btn-link:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.flow-stage {
  position: relative;
  min-height: 0;
  flex: 1;
  background: var(--sl-bg-page);
}

.canvas-floating-controls {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 15;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
  box-shadow: var(--sl-shadow-sm);
}

.control-bar-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-text-secondary, #64748b);
}

.control-btn-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.flow-control-tool {
  height: 28px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--sl-border-input, #cbd5e1);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
  color: var(--sl-text-heading, #0f172a);
  cursor: grab;
  transition: var(--sl-ease-smooth);
}

.flow-control-tool:hover:not(:disabled) {
  border-color: var(--sl-primary, #2563eb);
  background: var(--sl-primary-light, #eff6ff);
}

.flow-control-tool:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.flow-control-tool .tool-icon {
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border-radius: 4px;
  background: var(--sl-primary-light, #eff6ff);
  color: var(--sl-primary, #2563eb);
  font-size: 9px;
  font-weight: 700;
}

.flow-control-tool .tool-icon.start {
  background: var(--sl-success-light, #f0fdf4);
  color: var(--sl-success, #16a34a);
}

.flow-control-tool .tool-icon.end {
  background: var(--sl-bg-page, #f1f5f9);
  color: var(--sl-text-secondary, #64748b);
}

.flow-control-tool .tool-icon.branch {
  background: var(--sl-warning-light, #fffbeb);
  color: var(--sl-warning, #d97706);
}

.flow-control-tool .tool-icon.aggregate {
  background: #f5f3ff;
  color: #6d28d9;
}

.flow-control-tool .tool-name {
  font-size: 12px;
  font-weight: 500;
}

.workflow-flow {
  width: 100%;
  height: 100%;
}

.workflow-flow :deep(.vue-flow__pane) {
  cursor: grab;
}

.workflow-flow :deep(.vue-flow__edge-path) {
  stroke: #7c93b8;
  stroke-width: 1.8;
  fill: none;
}

.workflow-flow :deep(.execution-edge .vue-flow__edge-path) {
  stroke: #7c93b8;
}

.workflow-flow :deep(.execution-edge:hover .vue-flow__edge-path),
.workflow-flow :deep(.execution-edge.selected .vue-flow__edge-path) {
  stroke: #3b6fd4;
}

.workflow-flow :deep(.data-edge .vue-flow__edge-path) {
  stroke: #9a8ab5;
  stroke-dasharray: 6 5;
}

.workflow-flow :deep(.data-edge:hover .vue-flow__edge-path),
.workflow-flow :deep(.data-edge.selected .vue-flow__edge-path) {
  stroke: #7a5fc0;
}

.workflow-flow :deep(.vue-flow__edge) {
  cursor: pointer;
}

.workflow-flow :deep(.vue-flow__edge:hover) {
  z-index: 12;
}

.workflow-flow :deep(.vue-flow__edge:hover .vue-flow__edge-path),
.workflow-flow :deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke-width: 2.2;
}

.workflow-flow :deep(.vue-flow__controls) {
  overflow: hidden;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  box-shadow: var(--sl-shadow-sm);
}

.workflow-flow :deep(.vue-flow__controls-button) {
  border-bottom-color: var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}

.empty-workbench {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 3;
  width: 360px;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-md, 8px);
  background: #ffffff;
  transform: translate(-50%, -50%);
  box-shadow: var(--sl-shadow-container);
}

.empty-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: var(--sl-bg-hover, #f8fafc);
}

.empty-head > span {
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono);
}

.empty-head strong {
  display: block;
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 600;
}

.empty-head small {
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
}

.quick-start.single-action {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 16px;
}

.quick-start.single-action button {
  width: 100%;
  max-width: 240px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--sl-primary, #2563eb);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
  color: var(--sl-primary, #2563eb);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
}

.quick-start.single-action button:hover:not(:disabled) {
  background: var(--sl-primary, #2563eb);
  color: #ffffff;
}

.quick-start.single-action button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.canvas-statusbar {
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  flex: none;
  padding: 0 16px;
  border-top: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
}

.canvas-statusbar span {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-grow {
  flex: 1;
}

.legend-line {
  width: 16px;
  height: 0;
  border-top: 2px solid #7c93b8;
}

.legend-line.data {
  border-top-color: #9a8ab5;
  border-top-style: dashed;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sl-text-secondary, #64748b);
}

.inspector-scroll {
  min-height: 0;
  flex: 1;
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.inspector-scroll::-webkit-scrollbar {
  width: 6px;
}

.inspector-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 6px;
}

.overview-view,
.edge-view {
  min-height: 100%;
  box-sizing: border-box;
}

.section-heading {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}

.section-heading strong {
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 600;
}

.section-heading span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
}

.config-section {
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}

.dense-form {
  padding: 12px 12px 4px;
}

.dense-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.dense-form :deep(.el-form-item__label) {
  height: 20px;
  padding: 0;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
  line-height: 20px;
}

.dense-form :deep(.el-input__wrapper),
.dense-form :deep(.el-textarea__inner) {
  min-height: 28px;
  border-radius: var(--sl-radius-sm, 6px);
}

.overview-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}

.overview-metrics > div {
  display: grid;
  align-content: center;
  justify-items: start;
  gap: 2px;
  min-height: 58px;
  padding: 10px 14px;
  border-right: 1px solid var(--sl-border-base, #e2e8f0);
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}

.overview-metrics > div:nth-child(2n) {
  border-right: 0;
}

.overview-metrics > div:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.overview-metrics strong {
  color: var(--sl-text-heading, #0f172a);
  font-size: 14.5px;
  font-weight: 700;
  font-family: var(--sl-font-mono);
  line-height: 1.3;
}

.overview-metrics span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
}

.validation-groups {
  display: grid;
}

.issue-group-heading {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}

.issue-group-heading strong {
  color: var(--sl-text-heading, #0f172a);
  font-size: 12px;
  font-weight: 600;
}

.issue-group-heading span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
}

.issue-list {
  display: grid;
}

.issue-list button {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 12px;
  align-items: start;
  gap: 8px;
  padding: 8px 12px;
  border: 0;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.issue-list button:hover {
  background: var(--sl-bg-hover, #f8fafc);
}

.issue-list button > b {
  padding: 2px 4px;
  border-radius: 4px;
  background: var(--sl-danger-light, #fef2f2);
  color: var(--sl-danger, #dc2626);
  font-size: 11px;
  font-weight: 500;
  text-align: center;
}

.issue-list button.warning > b {
  background: var(--sl-warning-light, #fffbeb);
  color: var(--sl-warning, #d97706);
}

.issue-list button > span {
  display: grid;
  gap: 2px;
}

.issue-list strong {
  color: var(--sl-text-heading, #0f172a);
  font-size: 12.5px;
}

.issue-list small {
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
  line-height: 18px;
}

.issue-list i {
  color: var(--sl-text-disabled, #94a3b8);
}

.overview-empty {
  display: grid;
  gap: 4px;
  margin: 0;
  padding: 16px 14px 20px;
}

.overview-empty strong {
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 600;
}

.overview-empty span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
  line-height: 18px;
}

.wide-action {
  width: calc(100% - 24px);
  margin: 12px;
  justify-content: center;
}

.connection-type {
  display: grid;
  gap: 4px;
  padding: 12px 16px;
  border-bottom: 3px solid var(--sl-primary, #2563eb);
  background: var(--sl-primary-light, #eff6ff);
}

.connection-type.port {
  border-color: #6d28d9;
  background: #f5f3ff;
}

.connection-type span {
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
}

.connection-type b {
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
}

.property-list {
  margin: 0;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}

.property-list > div {
  display: grid;
  grid-template-columns: 90px 1fr;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
}

.property-list > div:last-child {
  border-bottom: 0;
}

.property-list dt,
.property-list dd {
  margin: 0;
  padding: 8px 12px;
  font-size: 12.5px;
  line-height: 20px;
}

.property-list dt {
  background: var(--sl-bg-hover, #f8fafc);
  color: var(--sl-text-secondary, #64748b);
}

.property-list dd {
  color: var(--sl-text-body, #334155);
  font-family: var(--sl-font-mono);
  white-space: normal;
  word-break: break-word;
}

.workflow-element-drawer-body {
  height: 100%;
  overflow: hidden;
  box-sizing: border-box;
  background: #ffffff;
}

.workflow-element-drawer-body > .edge-view {
  min-height: 100%;
  overflow-y: auto;
}

@media (max-width: 1280px) {
  .designer-grid {
    grid-template-columns: 240px minmax(0, 1fr) 280px;
  }

  .flow-title-row > strong {
    max-width: 160px;
  }
}
</style>

<style>
.workflow-element-drawer {
  width: 50% !important;
  max-width: 92vw;
}

.workflow-element-drawer .el-drawer__body {
  min-height: 0;
  padding: 0;
  overflow: hidden;
  background: #ffffff;
}
</style>
