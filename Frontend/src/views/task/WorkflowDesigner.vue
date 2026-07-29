<template>
  <div class="workflow-page">
    <header class="workspace-header">
      <div class="title-block">
        <div class="breadcrumb">任务中心 / 流程建模</div>
        <div class="title-line">
          <h1>{{ form.name || '未命名流程' }}</h1>
          <span class="status-pill" :class="form.status.toLowerCase()">{{ form.status }}</span>
          <span v-if="form.id" class="model-id">模型ID {{ form.id }}</span>
        </div>
        <p>拖入节点、连接接口并配置能力，设备实例在创建任务时绑定</p>
      </div>
      <div class="header-actions">
        <el-select v-model="openedWorkflowId" clearable filterable placeholder="打开已有流程" class="workflow-select" @change="loadWorkflow">
          <el-option v-for="item in workflows" :key="item.id" :label="item.flowName" :value="item.id" />
        </el-select>
        <el-tooltip content="刷新设备模型与流程资源" placement="bottom"><el-button :icon="Refresh" circle @click="loadAll" /></el-tooltip>
        <el-button :icon="Plus" @click="create">新建</el-button>
        <el-button :icon="Setting" @click="openSettings">流程设置</el-button>
        <el-button type="primary" :loading="saving" :disabled="!contractReady" @click="save">保存模型</el-button>
      </div>
    </header>

    <div class="workspace-shell">
      <aside class="resource-panel">
        <div class="resource-heading">
          <div><strong>节点资源</strong><span>拖入画布或单击添加</span></div>
          <el-input v-model="resourceKeyword" :prefix-icon="Search" clearable placeholder="搜索设备或流程" />
        </div>

        <section class="function-section">
          <el-alert v-if="contractError" class="contract-error" type="error" :closable="false" :title="contractError" />
          <div class="section-title"><span>功能节点</span><small>流程控制</small></div>
          <div class="function-grid">
            <button v-for="item in palette" :key="item.type" class="function-card" :disabled="!contractReady" :draggable="contractReady" @dragstart="drag($event,{kind:'function',type:item.type})" @click="addResource({kind:'function',type:item.type})">
              <span class="function-icon" :class="item.type.toLowerCase()">{{ item.glyph }}</span>
              <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
            </button>
          </div>
        </section>

        <el-tabs v-model="tab" class="resource-tabs" stretch>
          <el-tab-pane label="设备模型" name="devices">
            <el-tree v-if="filteredDeviceTree.length" :data="filteredDeviceTree" node-key="key" default-expand-all :expand-on-click-node="false" class="resource-tree">
              <template #default="{ data }">
                <div class="tree-item" :class="{ draggable:data.kind==='model' && contractReady }" :draggable="data.kind==='model' && contractReady" @dragstart="drag($event,data)">
                  <span class="tree-label"><span class="tree-dot" :class="data.kind"></span><span>{{ data.label }}</span></span>
                  <el-button v-if="data.kind==='model'" link type="primary" title="添加到画布" :disabled="!contractReady" @click.stop="addResource({kind:'model',model:data.model})">＋</el-button>
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
        </el-tabs>
      </aside>

      <main class="canvas-panel">
        <div class="canvas-toolbar">
          <div class="canvas-title">
            <strong>流程画布</strong>
            <span class="canvas-count">{{ form.nodesDef.length }}个节点 · {{ nodeConnections.length }}条连接</span>
          </div>
          <div class="canvas-actions">
            <el-button v-if="selectedEdgeId" type="danger" plain size="small" @click="deleteSelectedEdge">删除选中连接</el-button>
            <el-tooltip content="重新排列全部节点" placement="bottom"><el-button :icon="MagicStick" circle @click="autoLayout" /></el-tooltip>
            <el-tooltip content="适应画布内容" placement="bottom"><el-button :icon="Aim" circle @click="fitCanvas" /></el-tooltip>
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
          <div v-if="!flowNodes.length" class="empty-canvas">
            <div class="empty-illustration"><span></span><span></span><span></span></div>
            <strong>从左侧添加第一个节点</strong>
            <p>拖入设备模型、子流程或功能节点，节点之间通过连接点建立流程关系</p>
          </div>
        </div>

        <footer class="canvas-statusbar">
          <span><i class="status-dot"></i>布局自动保存在当前浏览器</span>
          <span>拖动画布平移 · 滚轮缩放 · 拖动节点两侧连接点建立连线</span>
        </footer>
      </main>
    </div>

    <el-drawer v-model="settingsVisible" title="流程设置" size="420px" :append-to-body="true">
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="流程名称"><el-input v-model="form.name" maxlength="80" show-word-limit placeholder="请输入流程名称" /></el-form-item>
        <div class="two-column-form"><el-form-item label="版本"><el-input-number v-model="form.version" :min="1" :precision="0" controls-position="right" /></el-form-item><el-form-item label="模型状态"><el-select v-model="form.status"><el-option label="DRAFT" value="DRAFT" /><el-option label="ACTIVE" value="ACTIVE" /></el-select></el-form-item></div>
        <el-form-item label="模型描述"><el-input v-model="form.description" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="说明流程用途、前置条件和执行目标" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="settingsVisible=false">完成</el-button></template>
    </el-drawer>

    <WorkflowNodeInspector
      :visible="nodeDrawerVisible"
      :node="selectedNode"
      :errors="selectedNodeIssues"
      :device-capabilities="selectedDeviceModel?.capabilities || []"
      :device-attributes="selectedDeviceModel?.attributes || []"
      :port-connections="form.portConnections"
      :contract-ready="contractReady"
      @close="closeNodeDrawer"
      @rename="renameSelectedNode"
      @update:node="replaceSelectedNode"
      @update:port-connections="replacePortConnections"
      @remove-port-request="confirmRemovePort"
      @remove-node="removeSelectedNode"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
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
  sanitizeWorkflowPayload,
  serializeLayout
} from '../../utils/workflowCanvas.js'
import WorkflowCanvasNode from '../../components/task/workflow/WorkflowCanvasNode.vue'
import WorkflowNodeInspector from '../../components/task/workflow/WorkflowNodeInspector.vue'
import { invalidateFrontendContractMetadata, loadFrontendContractMetadata } from '../../services/frontendContractMetadata.js'
import { configureWorkflowNodeTemplates, createDeviceNode, createFunctionNode, createSubflowNode, removePort, validateNodeDefinition } from '../../utils/workflowNodeDefinition.js'

type NodeDefinition = Record<string, any>
type FlowNode = Record<string, any>
type FlowEdge = Record<string, any>

const tab = ref('devices')
const workflows = ref<any[]>([])
const models = ref<any[]>([])
const instances = ref<any[]>([])
const categories = ref<any[]>([])
const saving = ref(false)
const resourceKeyword = ref('')
const contractReady = ref(false)
const contractError = ref('')
const openedWorkflowId = ref<number | null>(null)
const settingsVisible = ref(false)
const nodeDrawerVisible = ref(false)
const selectedNodeName = ref('')
const selectedEdgeId = ref('')
const flowNodes = ref<FlowNode[]>([])
const flowEdges = ref<FlowEdge[]>([])
const draftLayoutKey = ref(newDraftKey())
let workflowListPromise: Promise<void> | null = null
let contractInitializationPromise: Promise<void> | null = null
const { screenToFlowCoordinate, fitView } = useVueFlow()

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

function newDraftKey() {
  return 'draft-'+Date.now()+'-'+Math.random().toString(36).slice(2, 8)
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

function reset(value:any) {
  Object.assign(form, empty(), value, { nodesDef:value.nodesDef || [], interfaceConnections:value.interfaceConnections || [], portConnections:value.portConnections || [] })
  selectedNodeName.value = ''
  nodeDrawerVisible.value = false
  openedWorkflowId.value = value.id || null
  rebuildCanvas()
}

function create() {
  draftLayoutKey.value = newDraftKey()
  openedWorkflowId.value = null
  reset(empty())
  settingsVisible.value = true
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
  nodeDrawerVisible.value = true
}

function closeNodeDrawer() {
  nodeDrawerVisible.value = false
  selectedNodeName.value = ''
}

function clearSelection() {
  selectedEdgeId.value = ''
}

function selectEdge(event:any) {
  selectedEdgeId.value = event.edge.id
  nodeDrawerVisible.value = false
}

function removeEdge(edge:any) {
  const next = removeCanvasEdge(edge, form.interfaceConnections, form.portConnections)
  form.interfaceConnections = next.interfaceConnections
  form.portConnections = next.portConnections
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
  return validateNodeDefinition(node, { deviceModel:modelById(node.deviceModelId) }).map((issue:any) => ({ ...issue, nodeName:node.name }))
}

function loadList() {
  if (!workflowListPromise) {
    workflowListPromise = (async () => {
      const response = await axios.get('/api/workflow/list')
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

async function save() {
  if (!contractReady.value) return ElMessage.error(contractError.value || '工作流系统模板尚未加载，不能保存')
  if (!form.name.trim()) {
    settingsVisible.value = true
    return ElMessage.error('请输入流程名称')
  }
  const errors = form.nodesDef.flatMap((node:any) =>
    validateNodeDefinition(node, { deviceModel:modelById(node.deviceModelId) }).map((issue:any) => ({ ...issue, nodeName:node.name })))
  if (errors.length) {
    selectedNodeName.value = errors[0].nodeName
    settingsVisible.value = false
    nodeDrawerVisible.value = true
    ElMessage.error(errors[0].message)
    return
  }
  saving.value = true
  const previousLayoutKey = currentLayoutKey()
  try {
    const payload = sanitizeWorkflowPayload(form)
    const response = await axios.post('/api/workflow/save', payload)
    if (!response.data?.success) throw Error(response.data?.message || '保存失败')
    form.id = response.data.data.workflowId
    openedWorkflowId.value = form.id
    persistLayout()
    if (previousLayoutKey !== currentLayoutKey()) localStorage.removeItem(previousLayoutKey)
    ElMessage.success('工作流模型已保存')
    await loadList()
  } catch (error:any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function loadWorkflow(id:number | null) {
  if (!id) return
  try {
    const response = await axios.get('/api/workflow/detail/'+id)
    if (!response.data?.success) throw Error(response.data?.message || '加载失败')
    reset(response.data.data)
  } catch (error:any) {
    ElMessage.error(error.message || '加载流程失败')
  }
}

onMounted(loadAll)
</script>

<style scoped>
.workflow-page{height:calc(100vh - 64px);min-height:680px;padding:16px 18px;box-sizing:border-box;overflow:hidden;background:#f3f6fa;color:#172033}.workspace-header{height:66px;display:flex;align-items:center;justify-content:space-between;gap:20px}.title-block{min-width:0}.breadcrumb{margin-bottom:3px;color:#8693a6;font-size:11px}.title-line{display:flex;align-items:center;gap:9px}.title-line h1{max-width:420px;margin:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:22px;line-height:30px}.title-block p{margin:3px 0 0;color:#69778c;font-size:12px}.status-pill{padding:2px 8px;border:1px solid #d6dee9;border-radius:999px;background:#fff;color:#67758a;font-size:10px;font-weight:700;letter-spacing:.04em}.status-pill.active{border-color:#b9e4cd;background:#effaf4;color:#16804a}.model-id{color:#8b97a8;font-size:11px}.header-actions{display:flex;align-items:center;gap:8px}.workflow-select{width:220px}.workspace-shell{height:calc(100% - 66px);display:flex;gap:12px;min-height:0}.resource-panel{width:286px;flex:0 0 286px;display:flex;flex-direction:column;min-height:0;border:1px solid #dfe6ef;border-radius:10px;background:#fff;box-shadow:0 2px 10px rgba(28,48,74,.04);overflow:hidden}.resource-heading{padding:16px 14px 12px;border-bottom:1px solid #edf1f6}.resource-heading>div{display:flex;align-items:baseline;justify-content:space-between;margin-bottom:12px}.resource-heading strong{font-size:15px}.resource-heading span{color:#8a96a8;font-size:11px}.function-section{padding:13px 14px 4px}.section-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:9px}.section-title span{color:#4c5a70;font-size:12px;font-weight:700}.section-title small{color:#9aa5b5}.function-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.function-card{display:flex;align-items:center;gap:8px;min-width:0;padding:9px 8px;border:1px solid #e1e8f1;border-radius:7px;background:#fafcff;color:#26344a;text-align:left;cursor:grab;transition:.16s ease}.function-card:hover{border-color:#98b8df;background:#f4f8fd;box-shadow:0 3px 10px rgba(59,105,160,.08);transform:translateY(-1px)}.function-icon{width:27px;height:27px;display:grid;place-items:center;flex:none;border-radius:6px;background:#eef4fb;color:#2f6fb9;font-size:11px;font-weight:800}.function-icon.start{background:#eaf8f0;color:#218654}.function-icon.end{background:#f1f3f6;color:#536073}.function-icon.branch{background:#fff5df;color:#ad7213}.function-icon.aggregate{background:#f2edff;color:#7251b6}.function-card>span:last-child{display:grid;min-width:0}.function-card strong{font-size:12px}.function-card small{color:#8a96a8;font-size:10px}.resource-tabs{min-height:0;display:flex;flex:1;flex-direction:column;padding:0 12px}.resource-tabs :deep(.el-tabs__header){margin:8px 0}.resource-tabs :deep(.el-tabs__content){min-height:0;flex:1;overflow:auto}.resource-tabs :deep(.el-tab-pane){height:100%}.resource-tree{background:transparent}.tree-item{width:100%;display:flex;align-items:center;justify-content:space-between;gap:5px;padding-right:4px}.tree-item.draggable{cursor:grab}.tree-label{display:flex;align-items:center;gap:7px;min-width:0}.tree-label>span:last-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.tree-dot{width:7px;height:7px;border-radius:50%;background:#c2cad5}.tree-dot.model{background:#4f8fd4}.tree-dot.instance{border:1px solid #7f9bb9;background:#fff}.tree-item small{color:#9aa5b5;font-size:10px}.flow-list{display:grid;gap:7px;padding-bottom:10px}.flow-item{width:100%;display:grid;grid-template-columns:28px minmax(0,1fr) 20px;align-items:center;gap:8px;padding:10px;border:1px solid #e5eaf1;border-radius:7px;background:#fff;color:#26344a;text-align:left;cursor:grab}.flow-item:hover{border-color:#9ebdde;background:#f8fbff}.flow-icon{width:27px;height:27px;display:grid;place-items:center;border-radius:6px;background:#ecf8f7;color:#23827b;font-weight:700}.flow-item>span:nth-child(2){display:grid;min-width:0}.flow-item strong,.flow-item small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.flow-item strong{font-size:12px}.flow-item small{color:#8a96a8;font-size:10px}.add-mark{color:#4a86c7;font-size:16px}.canvas-panel{min-width:0;display:flex;flex:1;flex-direction:column;border:1px solid #dfe6ef;border-radius:10px;background:#fff;box-shadow:0 3px 12px rgba(28,48,74,.05);overflow:hidden}.canvas-toolbar{height:48px;display:flex;align-items:center;justify-content:space-between;flex:none;padding:0 13px 0 16px;border-bottom:1px solid #e7ecf2}.canvas-title{display:flex;align-items:center;gap:10px}.canvas-title strong{font-size:14px}.canvas-count{padding-left:10px;border-left:1px solid #dfe5ed;color:#7c899b;font-size:11px}.canvas-actions{display:flex;align-items:center;gap:7px}.flow-stage{position:relative;min-height:0;flex:1;background:#f8fafc}.workflow-flow{width:100%;height:100%}.workflow-flow :deep(.vue-flow__pane){cursor:grab}.workflow-flow :deep(.vue-flow__edge-path){stroke:#6f8fb7;stroke-width:2}.workflow-flow :deep(.vue-flow__edge.selected .vue-flow__edge-path){stroke:#e05252;stroke-width:2.5}.workflow-flow :deep(.vue-flow__controls){overflow:hidden;border:1px solid #dce4ee;border-radius:7px;box-shadow:0 3px 12px rgba(30,50,75,.1)}.workflow-flow :deep(.vue-flow__controls-button){border-bottom-color:#e7ecf2;background:#fff}.canvas-node{position:relative;width:218px;min-height:96px;display:flex;border:1px solid #c8d5e5;border-radius:9px;background:#fff;box-shadow:0 4px 13px rgba(34,61,94,.09);overflow:visible;transition:border-color .15s,box-shadow .15s,transform .15s}.canvas-node:hover{border-color:#83a7d3;box-shadow:0 6px 18px rgba(34,61,94,.14)}.canvas-node.selected{border-color:#3277c5;box-shadow:0 0 0 3px rgba(50,119,197,.14),0 6px 18px rgba(34,61,94,.13)}.canvas-node.warning{border-color:#e0ae54}.node-accent{width:5px;flex:none;border-radius:8px 0 0 8px;background:#5f8fc8}.canvas-node.start .node-accent{background:#3ca36b}.canvas-node.end .node-accent{background:#657386}.canvas-node.branch .node-accent{background:#d89427}.canvas-node.aggregate .node-accent{background:#8566c2}.canvas-node.subflow .node-accent{background:#32958d}.node-main{min-width:0;display:flex;flex:1;flex-direction:column;padding:12px 14px}.node-topline{display:flex;align-items:center;gap:6px;margin-bottom:8px}.node-glyph{width:20px;height:20px;display:grid;place-items:center;border-radius:5px;background:#eef4fb;color:#3978bd;font-size:9px;font-weight:800}.start .node-glyph{background:#eaf8f0;color:#218654}.end .node-glyph{background:#f0f2f5;color:#536073}.branch .node-glyph{background:#fff4de;color:#ad7213}.aggregate .node-glyph{background:#f2edff;color:#7251b6}.subflow .node-glyph{background:#eaf7f6;color:#237f78}.node-kind{color:#78869a;font-size:10px;font-weight:700}.warning-dot{width:16px;height:16px;display:grid;place-items:center;margin-left:auto;border-radius:50%;background:#fff1d6;color:#aa6a00;font-size:10px;font-weight:800}.node-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#213047;font-size:14px}.node-summary{margin-top:5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#7d899a;font-size:11px}.workflow-handle{width:11px!important;height:11px!important;border:2px solid #fff!important;background:#4f83bf!important;box-shadow:0 0 0 1px #4f83bf}.input-handle{left:-6px!important}.output-handle{right:-6px!important}.empty-canvas{position:absolute;left:50%;top:47%;display:grid;place-items:center;width:340px;transform:translate(-50%,-50%);pointer-events:none;text-align:center}.empty-illustration{position:relative;width:120px;height:68px;margin-bottom:15px}.empty-illustration span{position:absolute;width:42px;height:28px;border:1px solid #c8d6e7;border-radius:6px;background:#fff;box-shadow:0 3px 8px rgba(55,78,106,.05)}.empty-illustration span:nth-child(1){left:0;top:20px}.empty-illustration span:nth-child(2){right:0;top:20px}.empty-illustration span:nth-child(3){left:39px;top:0;border-color:#92afd2;background:#f5f9fd}.empty-illustration:before,.empty-illustration:after{content:'';position:absolute;top:35px;width:39px;border-top:1px dashed #a9bed7}.empty-illustration:before{left:34px;transform:rotate(-17deg)}.empty-illustration:after{right:34px;transform:rotate(17deg)}.empty-canvas strong{color:#44536a;font-size:14px}.empty-canvas p{margin:7px 0 0;color:#8a96a8;font-size:11px;line-height:1.7}.canvas-statusbar{height:31px;display:flex;align-items:center;justify-content:space-between;flex:none;padding:0 13px;border-top:1px solid #e7ecf2;background:#fbfcfe;color:#8490a1;font-size:10px}.canvas-statusbar span{display:flex;align-items:center;gap:6px}.status-dot{width:6px;height:6px;border-radius:50%;background:#43a46d}.drawer-form :deep(.el-form-item){margin-bottom:20px}.drawer-form :deep(.el-select),.drawer-form :deep(.el-input-number){width:100%}.two-column-form{display:grid;grid-template-columns:1fr 1fr;gap:12px}.node-inspector-head{display:flex;align-items:center;gap:11px;padding:12px;margin-bottom:14px;border:1px solid #e3e9f1;border-radius:8px;background:#f8fafc}.inspector-icon{width:38px;height:38px;display:grid;place-items:center;border-radius:8px;background:#eaf2fb;color:#3476bd;font-weight:800}.inspector-icon.start{background:#eaf8f0;color:#218654}.inspector-icon.end{background:#eef1f4;color:#536073}.inspector-icon.branch{background:#fff4de;color:#ad7213}.inspector-icon.aggregate{background:#f2edff;color:#7251b6}.inspector-icon.subflow{background:#eaf7f6;color:#237f78}.node-inspector-head>div{display:grid;gap:3px}.node-inspector-head strong{font-size:14px}.node-inspector-head span{color:#7f8b9b;font-size:11px}.config-alert{margin-bottom:17px}.capability-preview{display:grid;gap:5px;margin:-7px 0 18px;padding:11px;border-left:3px solid #4f87c6;border-radius:4px;background:#f5f8fc}.capability-preview strong{font-size:12px}.capability-preview span,.form-hint{color:#7c899b;font-size:11px;line-height:1.6}.form-hint{margin-top:-10px;margin-bottom:18px}.inspector-section{margin-top:20px;padding-top:17px;border-top:1px solid #e8edf3}.inspector-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:9px}.inspector-title strong{font-size:13px}.inspector-title span{min-width:21px;padding:2px 6px;border-radius:10px;background:#eef2f7;color:#617085;text-align:center;font-size:10px}.interface-list,.connection-list{display:grid;gap:7px}.interface-row{display:flex;align-items:center;gap:9px;padding:9px;border:1px solid #e7ecf2;border-radius:6px}.interface-row>span:last-child{display:grid;gap:2px;min-width:0}.interface-row strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.interface-row small{color:#8b97a8;font-size:10px}.direction{width:28px;padding:3px 4px;border-radius:4px;background:#edf3fa;color:#3476bd;text-align:center;font-size:9px;font-weight:800}.direction.out{background:#edf8f2;color:#238052}.connection-row{display:grid;grid-template-columns:minmax(0,1fr) 16px minmax(0,1fr);align-items:center;gap:4px;padding:8px 9px;border-radius:6px;background:#f6f8fb;color:#5f6e83;font-size:10px}.connection-row span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.connection-row b{text-align:center;color:#9ba7b6}.drawer-footer{display:flex;justify-content:space-between;width:100%}@media(max-width:1100px){.resource-panel{width:250px;flex-basis:250px}.function-grid{grid-template-columns:1fr}.workflow-select{width:180px}.title-block p{display:none}}@media(max-width:820px){.workflow-page{height:auto;min-height:calc(100vh - 64px);overflow:auto}.workspace-header{height:auto;align-items:flex-start;flex-direction:column;padding-bottom:12px}.header-actions{width:100%;flex-wrap:wrap}.workspace-shell{height:720px}.resource-panel{width:220px;flex-basis:220px}.canvas-statusbar span:last-child{display:none}}
</style>
