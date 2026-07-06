
<template>
  <div class="instance-page">
    <el-container class="layout">
      <el-aside width="320px" class="sidebar">
        <div class="sidebar-header">
          <div class="header-title">
            <span>设备模型分类</span>
          </div>
        </div>
        <el-scrollbar class="sidebar-scroll" v-loading="modelsLoading">
          <div class="model-list" v-for="group in groupedModels" :key="group.category">
            <div class="category-title" style="padding: 10px 16px; font-weight: bold; color: #909399; font-size: 12px; background: #f8f9fa;">
              {{ group.category }}
            </div>
            <div
              v-for="model in group.items"
              :key="model.modelId"
              :class="['model-item', { active: selectedModelId === model.modelId }]"
              @click="selectModel(model.modelId)"
            >
              <div class="model-name">{{ model.modelName }}</div>
              <div class="model-meta">编号: {{ model.modelId }}</div>
            </div>
          </div>
        </el-scrollbar>
      </el-aside>

      <el-main class="content">
        <div class="main-header">
          <div>
            <h2>{{ selectedModelName }}</h2>
            <span class="subtitle">设备实例查看与配置</span>
          </div>
          <div class="actions">
            <el-input
              v-model="instanceKeyword"
              class="instance-search"
              size="small"
              clearable
              placeholder="搜索实例或设备SN"
              @input="onInstanceSearchInput"
            />
            <el-button :icon="Refresh" circle size="small" @click="loadData" title="刷新" />
            <el-button v-if="canCreateInstance" type="primary" class="add-device-trigger" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 添加设备
            </el-button>
          </div>
        </div>



        <div class="instance-list-wrap" v-loading="loading">
          <el-table
            v-if="instances.length > 0"
            :data="instances"
            border
            stripe
            size="small"
            class="instance-table"
            row-key="instanceId"
            @row-click="viewDetails"
          >
            <el-table-column label="设备实例" min-width="180">
              <template #default="{ row }">
                <div class="instance-name-cell">
                  <strong>{{ row.instanceName }}</strong>
                  <span>实例ID {{ row.instanceId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="设备模型" min-width="160">
              <template #default="{ row }">{{ getModelName(row.modelId) }}</template>
            </el-table-column>
            <el-table-column label="Adapter / 设备点" min-width="220">
              <template #default="{ row }">
                <div class="binding-cell">
                  <span><b>Adapter</b>{{ row.boundAdapterName || row.commConfig?.boundAdapterName || '\u672a\u5206\u914d' }}</span>
                  <span><b>\u8bbe\u5907\u70b9</b>{{ row.boundDevicePoint || row.commConfig?.boundDevicePoint || '\u672a\u5206\u914d' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="MQTT 主题" min-width="360">
              <template #default="{ row }">
                <div class="topic-cell">
                  <span v-for="topic in mqttTopicRows(row.boundAdapterName, row.boundDevicePoint)" :key="topic.type">
                    <b>{{ topic.label }}</b><code>{{ topic.topic || '-' }}</code>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="在线状态" width="105" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isOnline ? 'success' : 'info'" size="small" effect="plain">{{ row.isOnline ? '在线' : '离线' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-wrap">
            <el-empty description="暂无设备实例" :image-size="100" />
          </div>
        </div>
        <div class="instance-pagination">
          <el-pagination
            v-model:current-page="instancePageNo"
            v-model:page-size="instancePageSize"
            :page-sizes="[12, 24, 48, 96]"
            :total="instanceTotal"
            layout="total, sizes, prev, pager, next"
            background
            small
            @size-change="handleInstancePageSizeChange"
            @current-change="loadInstances"
          />
        </div>
      </el-main>
    </el-container>

    <el-dialog
      v-model="drawerVisible"
      :title="`设备实例: ${activeInstance?.instanceName || ''}`"
      width="980px"
      :destroy-on-close="true"
      @close="closeDrawer"
    >
      <div v-if="activeInstance" class="drawer-body">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基础配置" name="info">
            <el-form label-position="top" size="small" class="instance-info-grid">
              <el-form-item label="实例编号">
                <el-input v-model="activeInstance.instanceId" disabled />
              </el-form-item>
              <el-form-item label="实例名称">
                <el-input v-model="activeInstance.instanceName" />
              </el-form-item>
              <el-form-item label="设备模型">
                <el-select v-model="activeInstance.modelId" style="width: 100%" @change="onModelChangeInDrawer">
                  <el-option v-for="m in models" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                </el-select>
              </el-form-item>
              <el-form-item label="在线状态">
                <div class="readonly-status">
                  <el-tag :type="activeInstance.isOnline ? 'success' : 'info'" effect="plain">{{ activeInstance.isOnline ? '在线' : '离线' }}</el-tag>
                  <span>由 Adapter 心跳 / 运行状态返回</span>
                </div>
              </el-form-item>
              <el-form-item label="Adapter">
                <el-select v-model="activeInstance.boundAdapterName" style="width: 100%" filterable @change="onAdapterChangeInDrawer">
                  <el-option v-for="adapter in adapterOptions" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
                </el-select>
              </el-form-item>
              <el-form-item label="设备点">
                <el-select v-model="activeInstance.boundDevicePoint" style="width: 100%" filterable :disabled="!activeInstance.boundAdapterName">
                  <el-option v-for="point in drawerDevicePointOptions" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
                </el-select>
              </el-form-item>
            </el-form>

            <section class="topic-section">
              <div class="section-caption">MQTT 主题</div>
              <el-table :data="activeMqttTopicRows" border size="small" class="topic-table">
                <el-table-column label="用途" width="110" prop="label" />
                <el-table-column label="方向" width="135" prop="direction" />
                <el-table-column label="主题">
                  <template #default="{ row }"><code>{{ row.topic || '-' }}</code></template>
                </el-table-column>
              </el-table>
            </section>

            <div class="footer-actions">
              <el-popconfirm v-if="canDeleteInstance" title="确认删除该设备实例？" @confirm="deleteInstance(activeInstance.instanceId)">
                <template #reference>
                  <el-button type="danger" plain size="small">删除设备</el-button>
                </template>
              </el-popconfirm>
              <el-button v-if="canEditInstance" type="primary" size="small" :loading="saving" @click="saveInstance">保存</el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane v-if="canControlInstance" label="手动控制" name="control">
            <el-form label-position="top" size="small" class="manual-control-form">
              <el-form-item label="设备功能">
                <el-select v-model="controlCommandId" style="width: 100%" placeholder="选择模型定义的功能" @change="resetControlParams">
                  <el-option
                    v-for="cmd in activeInstanceCommands"
                    :key="cmd.commandId"
                    :label="`${cmd.commandName || cmd.commandId} / Adapter: ${cmd.adapterCommandName || '-'}`"
                    :value="cmd.commandId"
                  />
                </el-select>
              </el-form-item>
              <div class="control-param-grid" v-if="activeControlParams.length">
                <el-form-item v-for="param in activeControlParams" :key="paramKey(param)" :label="param.displayName || param.name || param.paramName">
                  <el-switch v-if="isBooleanType(param.dataType)" v-model="controlParamValues[paramKey(param)]" />
                  <el-input-number
                    v-else-if="isNumberType(param.dataType)"
                    v-model="controlParamValues[paramKey(param)]"
                    :precision="isIntegerType(param.dataType) ? 0 : undefined"
                    controls-position="right"
                    style="width: 100%"
                  />
                  <el-input v-else v-model="controlParamValues[paramKey(param)]" :placeholder="param.dataType || 'STRING'" />
                  <div class="field-hint">数据类型：{{ param.dataType || '-' }}</div>
                </el-form-item>
              </div>
              <div v-else class="empty-inline">该功能无外部参数</div>
            </el-form>
            <div class="footer-actions">
              <el-button type="primary" size="small" :loading="sendingControl" @click="sendManualCommand">
                发送指令
              </el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane label="实时状态" name="status">
            <div class="status-top">
              <el-tag type="success" size="small">状态扫描中（每 3 秒刷新）</el-tag>
              <span>更新时间: {{ lastSnapshotTime }}</span>
            </div>
            <el-descriptions :column="1" border size="small" class="mb-12">
              <el-descriptions-item label="指令状态">{{ snapshot?.currentCommandState || '-' }}</el-descriptions-item>
              <el-descriptions-item label="功能状态">{{ snapshot?.currentOperationState || '-' }}</el-descriptions-item>
            </el-descriptions>
            <section class="snapshot-section">
              <div class="section-caption">属性快照</div>
              <el-table :data="snapshotAttributeRows" border size="small" class="snapshot-table">
                <el-table-column label="模型属性" min-width="160" prop="label" />
                <el-table-column label="属性标识" min-width="150" prop="key" />
                <el-table-column label="数据类型" width="110" prop="dataType" />
                <el-table-column label="单位" width="90" prop="unit" />
                <el-table-column label="当前值" min-width="160">
                  <template #default="{ row }"><span class="mono">{{ row.value ?? '-' }}</span></template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="局部约束" name="constraints">
            <div class="constraint-actions">
              <el-button v-if="canEditInstance" type="primary" plain size="small" @click="addConstraint">
                <el-icon><Plus /></el-icon> 新增约束
              </el-button>
            </div>
            <el-table :data="localConstraints" border size="small">
              <el-table-column label="监控属性">
                <template #default="{ row }">
                  <el-select v-model="row.targetAttr" size="small" style="width: 100%" placeholder="选择属性">
                    <el-option
                      v-for="prop in getModelAttributes(activeInstance.modelId)"
                      :key="prop.identifier"
                      :label="prop.name"
                      :value="prop.identifier"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="条件" width="100">
                <template #default="{ row }">
                  <el-select v-model="row.operator" size="small">
                    <el-option label=">" value=">" />
                    <el-option label=">=" value=">=" />
                    <el-option label="<" value="<" />
                    <el-option label="<=" value="<=" />
                    <el-option label="=" value="==" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="阈值" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.threshold" size="small" />
                </template>
              </el-table-column>
              <el-table-column v-if="canEditInstance" label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeConstraint($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="footer-actions mt-12">
              <el-button v-if="canEditInstance" type="primary" size="small" :loading="saving" @click="saveConstraints">保存约束</el-button>
            </div>
          </el-tab-pane>


          <el-tab-pane label="结构拓扑" name="components">
            <section class="component-create-panel" v-if="canEditInstance">
              <el-input v-model="componentForm.componentName" size="small" placeholder="新增槽位名称" />
              <el-select v-model="componentForm.categoryId" size="small" clearable filterable placeholder="组件类别">
                <el-option v-for="cat in categories" :key="String(cat.id)" :label="cat.categoryName" :value="String(cat.id)" />
              </el-select>
              <el-button type="primary" size="small" :loading="savingComponent" @click="saveComponent">新增槽位</el-button>
            </section>
            <el-table :data="instanceComponents" border size="small" v-loading="loadingComponents" class="component-table">
              <el-table-column label="组件槽位" min-width="150" prop="componentName" />
              <el-table-column label="类别" min-width="130">
                <template #default="{ row }">{{ categoryNameById(row.categoryId) || '-' }}</template>
              </el-table-column>
              <el-table-column label="绑定实例" min-width="150">
                <template #default="{ row }">{{ instanceNameById(row.selfInstanceId) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="componentStatusType(row.status)" effect="plain">{{ row.status || '未配置' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="安装时间" min-width="150">
                <template #default="{ row }">{{ formatTime(row.installTime) }}</template>
              </el-table-column>
              <el-table-column label="规格" min-width="180">
                <template #default="{ row }"><code>{{ componentSpecBrief(row.specification) }}</code></template>
              </el-table-column>
              <el-table-column v-if="canEditInstance" label="操作" width="230" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="!row.selfInstanceId && row.status !== '使用中'" link type="primary" size="small" @click="openComponentAction(row, 'configure')">配置</el-button>
                  <el-button v-else link type="primary" size="small" @click="openComponentAction(row, 'replace')">更换</el-button>
                  <el-button link size="small" @click="showComponentHistory(row)">历史</el-button>
                  <el-button link type="warning" size="small" @click="discardComponent(row)">废弃</el-button>
                  <el-popconfirm title="确认删除该组件槽位？" @confirm="deleteComponent(row)">
                    <template #reference><el-button link type="danger" size="small">删除</el-button></template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="数据集" name="datasets">
            <div v-if="canCreateDataset" class="dataset-create-panel">
              <el-select v-model="datasetCreateForm.templateId" size="small" filterable clearable placeholder="选择数据模板">
                <el-option v-for="tpl in availableDataTemplates" :key="templateIdOf(tpl)" :label="tpl.templateName || '未命名模板'" :value="templateIdOf(tpl)" />
              </el-select>
              <el-input v-model="datasetCreateForm.dataDesc" size="small" placeholder="数据集名称，例如：高压报警专项数据" />
              <el-button type="primary" size="small" :loading="creatingDataSet" @click="createDataSetForInstance">绑定模板建表</el-button>
            </div>
            <el-table :data="instanceDataSets" border size="small" v-loading="loadingDataSets">
              <el-table-column prop="id" label="数据集ID" width="90" />
              <el-table-column prop="dataDesc" label="数据集描述" min-width="160" />
              <el-table-column prop="dataTable" label="底层物理表" min-width="180" />
              <el-table-column prop="createTime" label="创建时间" min-width="160">
                <template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</template>
              </el-table-column>
              <el-table-column v-if="canDeleteDataset" label="操作" width="90" align="center">
                <template #default="{ row }">
                  <el-popconfirm title="确认删除该数据表？物理表会同时删除。" @confirm="deleteInstanceDataSet(row)">
                    <template #reference>
                      <el-button link type="danger" size="small">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
            <div class="footer-actions mt-12">
              <el-button type="primary" size="small" @click="loadDataSets">刷新列表</el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>


    <el-dialog v-model="componentActionVisible" :title="componentActionMode === 'replace' ? '更换组件' : '配置组件'" width="560px" append-to-body>
      <el-form label-position="top" size="small" class="component-action-form">
        <el-form-item label="组件槽位">
          <el-input v-model="componentActionForm.componentName" />
        </el-form-item>
        <el-form-item label="绑定设备实例">
          <el-select v-model="componentActionForm.selfInstanceId" clearable filterable style="width: 100%" placeholder="不绑定数字化实例时可只填写规格">
            <el-option v-for="item in instances" :key="item.instanceId" :label="item.instanceName" :value="item.instanceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格信息 JSON">
          <el-input v-model="componentActionForm.specificationText" type="textarea" :rows="4" placeholder="例如：{&quot;brand&quot;:&quot;A&quot;,&quot;model&quot;:&quot;M1&quot;}" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="componentActionVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="savingComponent" @click="submitComponentAction">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="componentHistoryVisible" title="组件历史" width="720px" append-to-body>
      <el-table :data="componentHistoryRows" border size="small" v-loading="loadingComponentHistory">
        <el-table-column label="组件槽位" min-width="150" prop="componentName" />
        <el-table-column label="绑定实例" min-width="150">
          <template #default="{ row }">{{ instanceNameById(row.selfInstanceId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" prop="status" />
        <el-table-column label="安装时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.installTime) }}</template>
        </el-table-column>
        <el-table-column label="前序ID" width="90" prop="predecessorId" />
      </el-table>
    </el-dialog>
    <el-dialog v-model="createDialogVisible" title="添加设备实例" width="760px" class="instance-create-dialog" append-to-body :destroy-on-close="true">
      <div class="instance-create-body">
        <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="92px" label-position="top" size="small" class="instance-create-form">
          <el-form-item label="设备名称" prop="instanceName">
            <el-input v-model="createForm.instanceName" />
          </el-form-item>
          <el-form-item label="设备模型" prop="modelId">
            <el-select
              v-model="createForm.modelId"
              style="width: 100%"
              placeholder="搜索并选择设备模型"
              filterable
              remote
              reserve-keyword
              :remote-method="searchModels"
              :loading="modelSearchLoading"
              @change="onModelChangeInCreate"
            >
              <el-option v-for="m in modelOptions" :key="m.modelId" :label="`${m.modelName}（${m.modelId}）`" :value="m.modelId" />
            </el-select>
          </el-form-item>

          <el-form-item label="Adapter" prop="boundAdapterName">
            <el-select v-model="createForm.boundAdapterName" style="width: 100%" filterable :loading="adapterLoading" @change="onAdapterChangeInCreate">
              <el-option v-for="adapter in adapterOptions" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
            </el-select>
          </el-form-item>
          <el-form-item label="设备点" prop="boundDevicePoint">
            <el-select v-model="createForm.boundDevicePoint" style="width: 100%" filterable :loading="pointsLoading" :disabled="!createForm.boundAdapterName" @change="updateCreateTopicPreview">
              <el-option v-for="point in createDevicePointOptions" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
            </el-select>
          </el-form-item>
          <el-form-item label="资产编号">
            <el-input v-model="createForm.assetInfo.serialNumber" placeholder="可选" />
          </el-form-item>
          <el-form-item label="安装位置">
            <el-input v-model="createForm.assetInfo.location" placeholder="例如：A区实验台 1" />
          </el-form-item>
          <el-form-item label="采购日期">
            <el-date-picker v-model="createForm.assetInfo.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="安装日期">
            <el-date-picker v-model="createForm.assetInfo.installDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注" class="form-wide">
            <el-input v-model="createForm.assetInfo.notes" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="MQTT 主题预览" class="form-wide">
            <div class="topic-preview-grid">
              <div v-for="topic in createMqttTopicRows" :key="topic.type">
                <span>{{ topic.label }}</span>
                <code>{{ topic.topic || '-' }}</code>
              </div>
            </div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button size="small" @click="createDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="creating" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { Cpu, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import axios from 'axios'
import { useAuthStore } from '../../stores/authStore'

interface DeviceModel {
  modelId: string
  modelName: string
  deviceCategory: string
  capabilitySpec: any
}

interface AdapterOption {
  adapterName: string
  parsedConfig?: any
}

interface AdapterDevicePoint {
  devicePoint: string
  templateName: string
  description?: string
}

interface DeviceInstance {
  instanceId: string
  modelId: string
  stateMachineId: string
  instanceName: string
  boundAdapterName?: string
  boundDevicePoint?: string
  commConfig: {
    boundAdapterName?: string
    boundDevicePoint?: string
    mqttTopic?: string
    mqttTopics?: Array<{ type: string; label: string; direction: string; topic: string }>
    constraints?: Array<{ targetAttr: string; operator: string; threshold: string }>
  }
  isOnline: boolean
}

interface DeviceSnapshot {
  instanceId: string
  currentCommandState?: string
  currentOperationState?: string
  latestAttributes?: Record<string, any>
}

const authStore = useAuthStore()

const models = ref<DeviceModel[]>([])
const modelOptions = ref<DeviceModel[]>([])
const instances = ref<DeviceInstance[]>([])
const adapterOptions = ref<AdapterOption[]>([])
const createDevicePointOptions = ref<AdapterDevicePoint[]>([])
const drawerDevicePointOptions = ref<AdapterDevicePoint[]>([])
const categories = ref<any[]>([])
const categoriesMap = ref<Record<string, string>>({})
const selectedModelId = ref('')
const instanceKeyword = ref('')
const loading = ref(false)
const modelsLoading = ref(false)
const modelSearchLoading = ref(false)
const saving = ref(false)
const creating = ref(false)
const sendingControl = ref(false)
const adapterLoading = ref(false)
const pointsLoading = ref(false)

const drawerVisible = ref(false)
const activeInstance = ref<DeviceInstance | null>(null)
const activeTab = ref('info')

const snapshot = ref<DeviceSnapshot | null>(null)
const lastSnapshotTime = ref('-')
let pollingTimer: any = null
const instancePageNo = ref(1)
const instancePageSize = ref(24)
const instanceTotal = ref(0)
let modelSearchTimer: any = null
let instanceLoadSeq = 0
let instanceSearchTimer: any = null

const localConstraints = ref<Array<{ targetAttr: string; operator: string; threshold: string }>>([])
const controlCommandId = ref('')
const controlParamValues = ref<Record<string, any>>({})

const instanceDataSets = ref<any[]>([])
const loadingDataSets = ref(false)
const dataTemplates = ref<any[]>([])
const creatingDataSet = ref(false)
const datasetCreateForm = ref({ templateId: '', dataDesc: '' })
const instanceComponents = ref<any[]>([])
const loadingComponents = ref(false)
const savingComponent = ref(false)
const componentForm = ref({ componentName: '', categoryId: '', selfInstanceId: '', status: '\u672a\u914d\u7f6e' })
const componentActionVisible = ref(false)
const componentActionMode = ref<'configure' | 'replace'>('configure')
const activeComponent = ref<any>(null)
const componentActionForm = ref({ componentName: '', selfInstanceId: '', specificationText: '{}' })
const componentHistoryVisible = ref(false)
const componentHistoryRows = ref<any[]>([])
const loadingComponentHistory = ref(false)

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({
  instanceName: '',
  modelId: '',
  boundAdapterName: '',
  boundDevicePoint: '',
  mqttTopicPreview: '',
  assetInfo: { serialNumber: '', purchaseDate: '', installDate: '', location: '', notes: '' }
})
const createRules = ref<FormRules>({
  instanceName: [{ required: true, message: '\u8bf7\u8f93\u5165\u8bbe\u5907\u540d\u79f0', trigger: 'blur' }],
  modelId: [{ required: true, message: '\u8bf7\u9009\u62e9\u8bbe\u5907\u6a21\u578b', trigger: 'change' }],
  boundAdapterName: [{ required: true, message: '\u8bf7\u9009\u62e9 Adapter', trigger: 'change' }],
  boundDevicePoint: [{ required: true, message: '\u8bf7\u9009\u62e9\u8bbe\u5907\u70b9', trigger: 'change' }]
})

const canCreateInstance = computed(() => authStore.hasPermission('device_instance:create'))
const canEditInstance = computed(() => authStore.hasPermission('device_instance:edit'))
const canDeleteInstance = computed(() => authStore.hasPermission('device_instance:delete'))
const canControlInstance = computed(() => authStore.hasPermission('device_instance:control'))
const canCreateDataset = computed(() => authStore.hasPermission('data_dataset:create'))
const canDeleteDataset = computed(() => authStore.hasPermission('data_dataset:delete'))

const selectedModelName = computed(() => {
  if (!selectedModelId.value) return '\u5168\u90e8\u8bbe\u5907\u5b9e\u4f8b'
  const model = models.value.find(m => String(m.modelId) === String(selectedModelId.value))
  return model ? `${model.modelName} (${model.modelId})` : '\u672a\u77e5\u6a21\u578b'
})

const groupedModels = computed(() => {
  const groups: Record<string, DeviceModel[]> = {}
  models.value.forEach(model => {
    const catId = (model as any).categoryId
    const catName = (catId && categoriesMap.value[catId]) || '\u672a\u5206\u7c7b'
    if (!groups[catName]) groups[catName] = []
    groups[catName].push(model)
  })
  return Object.entries(groups).map(([category, items]) => ({ category, items }))
})

const snapshotAttributes = computed(() => snapshot.value?.latestAttributes || {})

const selectedCreateModel = computed(() => findModelById(createForm.value.modelId))
const activeInstanceModel = computed(() => activeInstance.value ? findModelById(activeInstance.value.modelId) : null)
const activeInstanceCommands = computed(() => {
  const capabilities = asArray(activeInstanceModel.value?.capabilitySpec?.capabilities)
  return capabilities.map((cap: any) => ({
    ...cap,
    commandId: cap.name || cap.commandId || cap.adapterCommandName,
    commandName: cap.displayName || cap.name || cap.commandId,
    adapterCommandName: cap.adapterCommandName || cap.commandName || cap.name,
    parameters: asArray(cap.parameters)
  })).filter((cap: any) => cap.commandId)
})
const activeControlCommand = computed(() => activeInstanceCommands.value.find((cmd: any) => cmd.commandId === controlCommandId.value) || null)
const activeControlParams = computed(() => asArray(activeControlCommand.value?.parameters).filter((param: any) => !param.hidden))
const createMqttTopicRows = computed(() => mqttTopicRows(createForm.value.boundAdapterName, createForm.value.boundDevicePoint))
const activeMqttTopicRows = computed(() => mqttTopicRows(activeInstance.value?.boundAdapterName, activeInstance.value?.boundDevicePoint))
const snapshotAttributeRows = computed(() => {
  if (!activeInstance.value) return []
  const attrs = asArray(getModelAttributes(activeInstance.value.modelId))
  const snapshotMap = snapshotAttributes.value
  const used = new Set<string>()
  const rows = attrs.map((attr: any) => {
    const candidates = [attr.name, attr.identifier, attr.displayName].filter(Boolean).map(String)
    const matchedKey = candidates.find(key => Object.prototype.hasOwnProperty.call(snapshotMap, key)) || candidates[0] || ''
    if (matchedKey) used.add(matchedKey)
    return {
      key: attr.name || attr.identifier || attr.displayName || '-',
      label: attr.displayName || attr.name || attr.identifier || '-',
      dataType: attr.dataType || '-',
      unit: attr.unit || '-',
      value: matchedKey ? snapshotMap[matchedKey] : undefined
    }
  })
  Object.keys(snapshotMap).forEach(key => {
    if (!used.has(key)) rows.push({ key, label: getAttributeName(key), dataType: '-', unit: '-', value: snapshotMap[key] })
  })
  return rows
})
const availableDataTemplates = computed(() => {
  const modelId = activeInstance.value?.modelId
  return dataTemplates.value.filter(tpl => !tpl.deviceModelId || !modelId || String(tpl.deviceModelId) === String(modelId))
})

const mqttTopicRows = (adapterName?: string, devicePoint?: string) => {
  const ready = !!adapterName && !!devicePoint
  const base = ready ? `smartlab/adapter/${adapterName}/${devicePoint}` : ''
  return [
    { type: 'command', label: '命令', direction: '系统 → Adapter', topic: ready ? `${base}/command` : '' },
    { type: 'telemetry', label: '遥测', direction: 'Adapter → 系统', topic: ready ? `${base}/telemetry` : '' },
    { type: 'event', label: '事件', direction: 'Adapter → 系统', topic: ready ? `${base}/event` : '' },
    { type: 'heartbeat', label: '心跳', direction: 'Adapter → 系统', topic: adapterName ? `smartlab/adapter/${adapterName}/heartbeat` : '' }
  ]
}

const loadData = async () => {
  modelsLoading.value = true
  try {
    const catRes = await axios.get('/api/device/category/list')
    if (catRes.data?.success) {
      const map: Record<string, string> = {}
      categories.value = catRes.data.data || []
      ;(catRes.data.data || []).forEach((c: any) => {
        map[c.id] = c.categoryName
      })
      categoriesMap.value = map
    }
    await Promise.all([loadSidebarModels(), loadModelOptions(), loadAdapters(), loadDataTemplates()])
    await loadInstances()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备数据失败')
  } finally {
    modelsLoading.value = false
  }
}

const loadSidebarModels = async () => {
  const res = await axios.get('/api/device/model/page', {
    params: {
      pageNo: 1,
      pageSize: 100
    }
  })
  if (res.data?.success) {
    models.value = (res.data.data?.records || []).map(normalizeModel)
    if (models.value.length > 0 && !selectedModelId.value) {
      selectedModelId.value = models.value[0].modelId
    }
  }
}

const loadModelOptions = async (keyword = '') => {
  const res = await axios.get('/api/device/model/page', {
    params: {
      pageNo: 1,
      pageSize: 100,
      keyword: keyword.trim() || undefined
    }
  })
  if (res.data?.success) {
    const records = (res.data.data?.records || []).map(normalizeModel)
    const selected = findModelById(createForm.value.modelId)
    modelOptions.value = selected && !records.some((v: DeviceModel) => v.modelId === selected.modelId)
      ? [selected, ...records]
      : records
  }
}

const loadAdapters = async () => {
  adapterLoading.value = true
  try {
    const res = await axios.get('/api/adapter/index/list')
    if (res.data?.success) {
      adapterOptions.value = res.data.data || []
    }
  } finally {
    adapterLoading.value = false
  }
}

const loadDataTemplates = async () => {
  try {
    const res = await axios.get('/api/data/template/list')
    if (res.data?.success) {
      dataTemplates.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载数据模板失败')
  }
}

const loadDevicePoints = async (adapterName: string, target: 'create' | 'drawer' = 'create') => {
  const listRef = target === 'drawer' ? drawerDevicePointOptions : createDevicePointOptions
  listRef.value = []
  if (!adapterName) return
  pointsLoading.value = true
  try {
    const model = target === 'drawer' ? activeInstanceModel.value : selectedCreateModel.value
    const adapterConfig = model?.capabilitySpec?.adapterContract?.config || {}
    const categoryName = adapterConfig.categoryName || undefined
    const templateName = adapterConfig.templateName || undefined
    const params = categoryName ? { categoryName } : { templateName }
    const res = await axios.get(`/api/adapter/index/${encodeURIComponent(adapterName)}/device-points`, { params })
    if (res.data?.success) {
      listRef.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '\u52a0\u8f7d Adapter \u8bbe\u5907\u70b9\u5931\u8d25')
  } finally {
    pointsLoading.value = false
  }
}
const searchModels = (keyword: string) => {
  if (modelSearchTimer) window.clearTimeout(modelSearchTimer)
  modelSearchTimer = window.setTimeout(async () => {
    modelSearchLoading.value = true
    try {
      await loadModelOptions(keyword)
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '搜索设备模型失败')
    } finally {
      modelSearchLoading.value = false
    }
  }, 250)
}

const loadInstances = async () => {
  const seq = ++instanceLoadSeq
  loading.value = true
  try {
    const instancesRes = await axios.get('/api/device/instance/page', {
      params: {
        pageNo: instancePageNo.value,
        pageSize: instancePageSize.value,
        modelId: selectedModelId.value || undefined,
        keyword: instanceKeyword.value.trim() || undefined
      }
    })
    
    if (seq !== instanceLoadSeq) return
    if (instancesRes.data?.success) {
      const pageData = instancesRes.data.data || {}
      instances.value = (pageData.records || []).map(normalizeInstance)
      instanceTotal.value = pageData.total || 0
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备实例失败')
  } finally {
    if (seq === instanceLoadSeq) loading.value = false
  }
}

const selectModel = (id: string) => {
  selectedModelId.value = id
  instancePageNo.value = 1
  loadInstances()
}

const onInstanceSearchInput = () => {
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
  instanceSearchTimer = window.setTimeout(() => {
    instancePageNo.value = 1
    loadInstances()
  }, 250)
}

const handleInstancePageSizeChange = (size: number) => {
  instancePageSize.value = size
  instancePageNo.value = 1
  loadInstances()
}

const getModelName = (modelId: string) => {
  const model = findModelById(modelId)
  return model ? model.modelName : modelId
}

const getModelAttributes = (modelId: string) => {
  const model = findModelById(modelId)
  return model?.capabilitySpec?.attributes || []
}

const getAttributeName = (key: string) => {
  if (!activeInstance.value) return key
  const attr = getModelAttributes(activeInstance.value.modelId).find((v: any) => [v.identifier, v.name, v.displayName].filter(Boolean).map(String).includes(String(key)))
  return attr?.displayName || attr?.name || key
}

function asArray<T = any>(value: any): T[] {
  return Array.isArray(value) ? value : []
}

const paramKey = (param: any) => String(param.name || param.paramName || param.displayName || '')
const normalizeType = (type: any) => String(type || '').toUpperCase()
const isBooleanType = (type: any) => normalizeType(type) === 'BOOLEAN' || normalizeType(type) === 'BOOL'
const isIntegerType = (type: any) => ['INTEGER', 'INT', 'LONG'].includes(normalizeType(type))
const isNumberType = (type: any) => isIntegerType(type) || ['DOUBLE', 'FLOAT', 'NUMBER', 'DECIMAL'].includes(normalizeType(type))

const defaultValueForType = (type: any) => {
  if (isBooleanType(type)) return false
  if (isNumberType(type)) return 0
  return ''
}

const resetControlParams = () => {
  const next: Record<string, any> = {}
  activeControlParams.value.forEach((param: any) => {
    next[paramKey(param)] = controlParamValues.value[paramKey(param)] ?? defaultValueForType(param.dataType)
  })
  controlParamValues.value = next
}

const buildControlParameters = () => {
  const parameters: Record<string, any> = {}
  activeControlParams.value.forEach((param: any) => {
    const key = paramKey(param)
    let value = controlParamValues.value[key]
    if (isIntegerType(param.dataType)) value = value === '' || value == null ? 0 : Number.parseInt(String(value), 10)
    else if (isNumberType(param.dataType)) value = value === '' || value == null ? 0 : Number(value)
    parameters[key] = value
  })
  return parameters
}

const viewDetails = (instance: DeviceInstance) => {
  activeInstance.value = JSON.parse(JSON.stringify(instance))
  if (!activeInstance.value.commConfig) {
    activeInstance.value.commConfig = {}
  }
  activeInstance.value.boundAdapterName ||= activeInstance.value.commConfig.boundAdapterName || ''
  activeInstance.value.boundDevicePoint ||= activeInstance.value.commConfig.boundDevicePoint || ''
  activeInstance.value.commConfig.mqttTopic = commandTopicPreview(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
  activeInstance.value.commConfig.mqttTopics = mqttTopicRows(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
  localConstraints.value = JSON.parse(JSON.stringify(activeInstance.value.commConfig.constraints || []))
  loadDevicePoints(activeInstance.value.boundAdapterName, 'drawer')
  datasetCreateForm.value = { templateId: '', dataDesc: '' }
  controlCommandId.value = activeInstanceCommands.value[0]?.commandId || ''
  resetControlParams()
  activeTab.value = 'info'
  drawerVisible.value = true
  loadDataSets()
  loadComponents()
}

const onModelChangeInDrawer = (modelId: string) => {
  if (!activeInstance.value) return
  activeInstance.value.stateMachineId = `${modelId}StateMachine`
  activeInstance.value.boundDevicePoint = ''
  loadDevicePoints(activeInstance.value.boundAdapterName || '', 'drawer')
}

const saveInstance = async () => {
  if (!activeInstance.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    payload.boundAdapterName = activeInstance.value.boundAdapterName
    payload.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.commConfig.boundAdapterName = activeInstance.value.boundAdapterName
    payload.commConfig.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.commConfig.mqttTopic = commandTopicPreview(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
    payload.commConfig.mqttTopics = mqttTopicRows(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
    payload.commConfig.constraints = localConstraints.value
    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('保存成功')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const deleteInstance = async (id: string) => {
  try {
    const res = await axios.delete(`/api/device/instance/delete/${id}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

const addConstraint = () => {
  localConstraints.value.push({ targetAttr: '', operator: '>', threshold: '' })
}

const removeConstraint = (index: number) => {
  localConstraints.value.splice(index, 1)
}

const saveConstraints = async () => {
  if (!activeInstance.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    payload.boundAdapterName = activeInstance.value.boundAdapterName
    payload.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.commConfig.boundAdapterName = activeInstance.value.boundAdapterName
    payload.commConfig.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.commConfig.mqttTopic = commandTopicPreview(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
    payload.commConfig.mqttTopics = mqttTopicRows(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
    payload.commConfig.constraints = localConstraints.value.filter(v => v.targetAttr && v.threshold)
    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('约束保存成功')
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const fetchSnapshot = async () => {
  if (!activeInstance.value) return
  try {
    const res = await axios.get(`/api/device/instance/snapshot/${activeInstance.value.instanceId}`)
    if (res.data?.success) {
      snapshot.value = res.data.data || null
      lastSnapshotTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    }
  } catch {
    snapshot.value = null
  }
}

const startPolling = () => {
  stopPolling()
  fetchSnapshot()
  pollingTimer = setInterval(fetchSnapshot, 3000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const closeDrawer = () => {
  stopPolling()
  snapshot.value = null
  activeInstance.value = null
  controlCommandId.value = ''
  controlParamValues.value = {}
  instanceDataSets.value = []
}

const loadComponents = async () => {
  if (!activeInstance.value) return
  loadingComponents.value = true
  try {
    const res = await axios.get('/api/device/component/list', { params: { parentInstanceId: activeInstance.value.instanceId } })
    if (res.data?.success) instanceComponents.value = res.data.data || []
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载结构拓扑失败')
  } finally {
    loadingComponents.value = false
  }
}

const saveComponent = async () => {
  if (!activeInstance.value || !componentForm.value.componentName.trim()) {
    ElMessage.warning('\u8bf7\u8f93\u5165\u7ec4\u4ef6\u540d\u79f0')
    return
  }
  savingComponent.value = true
  try {
    const res = await axios.post('/api/device/component/save', {
      componentName: componentForm.value.componentName.trim(),
      categoryId: componentForm.value.categoryId ? Number(componentForm.value.categoryId) : null,
      parentInstanceId: Number(activeInstance.value.instanceId),
      selfInstanceId: null,
      status: '\u672a\u914d\u7f6e',
      specification: {}
    })
    if (res.data?.success) {
      componentForm.value = { componentName: '', categoryId: '', selfInstanceId: '', status: '\u672a\u914d\u7f6e' }
      await loadComponents()
      ElMessage.success('\u7ec4\u4ef6\u69fd\u4f4d\u5df2\u65b0\u589e')
    } else {
      ElMessage.error(res.data?.message || '\u4fdd\u5b58\u7ec4\u4ef6\u5931\u8d25')
    }
  } finally {
    savingComponent.value = false
  }
}

const componentSpecBrief = (value: any) => {
  if (!value || (typeof value === 'object' && Object.keys(value).length === 0)) return '-'
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return text.length > 80 ? text.slice(0, 77) + '...' : text
}
const componentStatusType = (status?: string) => {
  if (status === '\u4f7f\u7528\u4e2d') return 'success'
  if (status === '\u5df2\u66f4\u6362') return 'warning'
  if (status === '\u5df2\u5e9f\u5f03') return 'info'
  return 'info'
}
const openComponentAction = (row: any, mode: 'configure' | 'replace') => {
  activeComponent.value = row
  componentActionMode.value = mode
  componentActionForm.value = {
    componentName: row?.componentName || '',
    selfInstanceId: row?.selfInstanceId ? String(row.selfInstanceId) : '',
    specificationText: row?.specification && Object.keys(row.specification || {}).length ? JSON.stringify(row.specification, null, 2) : '{}'
  }
  componentActionVisible.value = true
}
const submitComponentAction = async () => {
  if (!activeComponent.value?.id) return
  let specification: any = {}
  try {
    specification = componentActionForm.value.specificationText?.trim() ? JSON.parse(componentActionForm.value.specificationText) : {}
  } catch {
    ElMessage.error('\u89c4\u683c\u4fe1\u606f\u5fc5\u987b\u662f\u5408\u6cd5 JSON')
    return
  }
  savingComponent.value = true
  try {
    const payload = {
      componentName: componentActionForm.value.componentName,
      selfInstanceId: componentActionForm.value.selfInstanceId ? Number(componentActionForm.value.selfInstanceId) : null,
      specification
    }
    const action = componentActionMode.value === 'replace' ? 'replace' : 'configure'
    const res = await axios.post('/api/device/component/' + activeComponent.value.id + '/' + action, payload)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '\u4fdd\u5b58\u7ec4\u4ef6\u5931\u8d25')
      return
    }
    componentActionVisible.value = false
    await loadComponents()
    ElMessage.success(componentActionMode.value === 'replace' ? '\u7ec4\u4ef6\u5df2\u66f4\u6362' : '\u7ec4\u4ef6\u5df2\u914d\u7f6e')
  } finally {
    savingComponent.value = false
  }
}
const discardComponent = async (row: any) => {
  if (!row?.id) return
  try {
    await ElMessageBox.confirm('\u786e\u8ba4\u5c06\u8be5\u7ec4\u4ef6\u6807\u8bb0\u4e3a\u5df2\u5e9f\u5f03\uff1f', '\u5e9f\u5f03\u7ec4\u4ef6', { type: 'warning' })
    const res = await axios.post('/api/device/component/' + row.id + '/discard')
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '\u5e9f\u5f03\u7ec4\u4ef6\u5931\u8d25')
      return
    }
    await loadComponents()
    ElMessage.success('\u7ec4\u4ef6\u5df2\u5e9f\u5f03')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.response?.data?.message || '\u5e9f\u5f03\u7ec4\u4ef6\u5f02\u5e38')
  }
}

const showComponentHistory = async (row: any) => {
  if (!row?.id) return
  componentHistoryVisible.value = true
  loadingComponentHistory.value = true
  try {
    const res = await axios.get('/api/device/component/' + row.id + '/history')
    if (res.data?.success) {
      componentHistoryRows.value = res.data.data || []
    } else {
      componentHistoryRows.value = []
      ElMessage.error(res.data?.message || '\u52a0\u8f7d\u7ec4\u4ef6\u5386\u53f2\u5931\u8d25')
    }
  } catch (error: any) {
    componentHistoryRows.value = []
    ElMessage.error(error.response?.data?.message || '\u52a0\u8f7d\u7ec4\u4ef6\u5386\u53f2\u5931\u8d25')
  } finally {
    loadingComponentHistory.value = false
  }
}

const deleteComponent = async (row: any) => {
  const id = row?.id
  if (!id) return
  const res = await axios.delete('/api/device/component/delete/' + id)
  if (res.data?.success) {
    ElMessage.success('组件已删除')
    await loadComponents()
  } else {
    ElMessage.error(res.data?.message || '删除组件失败')
  }
}

const loadDataSets = async () => {
  if (!activeInstance.value) return
  loadingDataSets.value = true
  try {
    const res = await axios.get('/api/data/index/list', {
      params: { deviceInstanceId: activeInstance.value.instanceId }
    })
    if (res.data?.success) {
      instanceDataSets.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载数据集失败')
  } finally {
    loadingDataSets.value = false
  }
}

const createDataSetForInstance = async () => {
  if (!activeInstance.value) return
  if (!datasetCreateForm.value.templateId) {
    ElMessage.warning('请选择数据模板')
    return
  }
  creatingDataSet.value = true
  try {
    const template = dataTemplates.value.find(tpl => String(templateIdOf(tpl)) === String(datasetCreateForm.value.templateId))
    const res = await axios.post('/api/data/index/create-dataset', {
      templateId: datasetCreateForm.value.templateId,
      deviceInstanceId: activeInstance.value.instanceId,
      dataDesc: datasetCreateForm.value.dataDesc || (activeInstance.value.instanceName + ' - ' + (template?.templateName || '自定义数据表'))
    })
    if (res.data?.success) {
      ElMessage.success('数据表创建成功')
      datasetCreateForm.value = { templateId: '', dataDesc: '' }
      await loadDataSets()
    } else {
      ElMessage.error(res.data?.message || '创建失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '创建失败')
  } finally {
    creatingDataSet.value = false
  }
}

const deleteInstanceDataSet = async (row: any) => {
  const id = row?.id || row?.dataIndexId
  if (!id) return
  try {
    const res = await axios.delete('/api/data/index/delete/' + id)
    if (res.data?.success) {
      ElMessage.success('数据表已删除')
      await loadDataSets()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

watch(activeTab, (tab) => {
  if (tab === 'status') {
    startPolling()
  } else {
    stopPolling()
  }
})


const commandTopicPreview = (adapterName?: string, devicePoint?: string) => {
  if (!adapterName || !devicePoint) return ''
  return `smartlab/adapter/${adapterName}/${devicePoint}/command`
}

const updateCreateTopicPreview = () => {
  createForm.value.mqttTopicPreview = commandTopicPreview(createForm.value.boundAdapterName, createForm.value.boundDevicePoint)
}

const devicePointLabel = (point: AdapterDevicePoint) => {
  return point.description ? `${point.devicePoint} · ${point.description}` : point.devicePoint
}

const onModelChangeInCreate = async () => {
  const adapterName = selectedCreateModel.value?.capabilitySpec?.adapterContract?.config?.adapterName || createForm.value.boundAdapterName
  createForm.value.boundAdapterName = adapterName || ''
  createForm.value.boundDevicePoint = ''
  await loadDevicePoints(createForm.value.boundAdapterName, 'create')
  updateCreateTopicPreview()
}

const onAdapterChangeInCreate = async () => {
  createForm.value.boundDevicePoint = ''
  await loadDevicePoints(createForm.value.boundAdapterName, 'create')
  updateCreateTopicPreview()
}

const onAdapterChangeInDrawer = async () => {
  if (!activeInstance.value) return
  activeInstance.value.boundDevicePoint = ''
  await loadDevicePoints(activeInstance.value.boundAdapterName || '', 'drawer')
}

watch(() => [createForm.value.boundAdapterName, createForm.value.boundDevicePoint], () => {
  updateCreateTopicPreview()
})

const openCreateDialog = () => {
  createForm.value = {
    instanceName: '',
    modelId: '',
    boundAdapterName: '',
    boundDevicePoint: '',
    mqttTopicPreview: '',
    assetInfo: { serialNumber: '', purchaseDate: '', installDate: '', location: '', notes: '' }
  }
  createDialogVisible.value = true
  if (!models.value.length) {
    searchModels('')
  }
  if (!adapterOptions.value.length) {
    loadAdapters()
  }
}
const submitCreate = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      const mqttTopic = commandTopicPreview(createForm.value.boundAdapterName, createForm.value.boundDevicePoint)
      const payload: any = {
        instanceId: '',
        modelId: createForm.value.modelId,
        stateMachineId: createForm.value.modelId + 'StateMachine',
        instanceName: createForm.value.instanceName,
        boundAdapterName: createForm.value.boundAdapterName,
        boundDevicePoint: createForm.value.boundDevicePoint,
        instanceConfig: { assetInfo: createForm.value.assetInfo },
        assetInfo: createForm.value.assetInfo,
        commConfig: {
          boundAdapterName: createForm.value.boundAdapterName,
          boundDevicePoint: createForm.value.boundDevicePoint,
          mqttTopic,
          mqttTopics: mqttTopicRows(createForm.value.boundAdapterName, createForm.value.boundDevicePoint),
          constraints: []
        },
        isOnline: false
      }
      const res = await axios.post('/api/device/instance/save', payload)
      if (res.data?.success) {
        ElMessage.success('\u8bbe\u5907\u6dfb\u52a0\u6210\u529f')
        createDialogVisible.value = false
        await loadData()
      } else {
        ElMessage.error(res.data?.message || '\u6dfb\u52a0\u5931\u8d25')
      }
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '\u6dfb\u52a0\u5931\u8d25')
    } finally {
      creating.value = false
    }
  })
}
const sendManualCommand = async () => {
  if (!activeInstance.value) return
  if (!controlCommandId.value) {
    ElMessage.warning('\u8bf7\u9009\u62e9\u547d\u4ee4')
    return
  }
  const parameters = buildControlParameters()
  sendingControl.value = true
  try {
    const res = await axios.post('/api/device/instance/control/' + activeInstance.value.instanceId, {
      commandId: controlCommandId.value,
      parameters
    })
    if (res.data?.success) {
      ElMessage.success('\u6307\u4ee4\u6d88\u606f\u5df2\u751f\u6210')
    } else {
      ElMessage.error(res.data?.message || '\u6307\u4ee4\u53d1\u9001\u5931\u8d25')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '\u6307\u4ee4\u53d1\u9001\u5931\u8d25')
  } finally {
    sendingControl.value = false
  }
}

const categoryNameById = (id: any) => categoriesMap.value[String(id)] || ''
const instanceNameById = (id: any) => instances.value.find(item => String(item.instanceId) === String(id))?.instanceName || (id ? String(id) : '-')


function normalizeModel(raw: any): DeviceModel {
  return {
    ...raw,
    modelId: String(raw.modelId || raw.id || ''),
    modelName: raw.modelName || raw.name || '',
    deviceCategory: raw.deviceCategory || raw.categoryName || '',
    capabilitySpec: raw.capabilitySpec || {
      attributes: raw.attributes || [],
      capabilities: raw.capabilities || [],
      adapterContract: raw.adapterContract || { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
    }
  }
}

function normalizeInstance(raw: any): DeviceInstance {
  const commConfig = raw.commConfig || raw.instanceConfig || {}
  const boundAdapterName = raw.boundAdapterName || commConfig.boundAdapterName || commConfig.adapterName || ''
  const boundDevicePoint = raw.boundDevicePoint || commConfig.boundDevicePoint || commConfig.devicePoint || ''
  return {
    ...raw,
    instanceId: String(raw.instanceId || raw.id || ''),
    modelId: String(raw.modelId || raw.deviceModelId || ''),
    stateMachineId: raw.stateMachineId || `${raw.modelId || raw.deviceModelId || ''}StateMachine`,
    instanceName: raw.instanceName || raw.name || '',
    boundAdapterName,
    boundDevicePoint,
    commConfig: { ...commConfig, boundAdapterName, boundDevicePoint, mqttTopic: commConfig.mqttTopic || commandTopicPreview(boundAdapterName, boundDevicePoint), mqttTopics: commConfig.mqttTopics || mqttTopicRows(boundAdapterName, boundDevicePoint) },
    isOnline: raw.isOnline === true || raw.onlineStatus === 'ONLINE'
  }
}

const templateIdOf = (template: any) => String(template?.templateId || template?.id || '')

function findModelById(modelId: string) {
  if (!modelId) return null
  return modelOptions.value.find(v => v.modelId === modelId) || models.value.find(v => v.modelId === modelId) || null
}

onMounted(loadData)
onUnmounted(() => {
  stopPolling()
  if (modelSearchTimer) window.clearTimeout(modelSearchTimer)
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
})
</script>

<style scoped>
.instance-page {
  height: calc(100vh - 52px);
  background: #eef2f6;
}

.layout { height: 100%; }

.sidebar {
  background: #fff;
  border-right: 1px solid #ccd6e3;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.sidebar-scroll { flex: 1; }

.model-list { padding: 10px; }

.model-item {
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 8px;
  cursor: pointer;
}

.model-item.active {
  border-color: #9ca3af;
  background: #eef1f5;
}

.model-name { font-size: 13px; font-weight: 600; color: #111827; }
.model-meta { font-size: 11px; color: #6b7280; margin-top: 4px; }

.content { padding: 0; background: #eef2f6; display: flex; flex-direction: column; }

.main-header {
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.main-header h2 { margin: 0; font-size: 19px; color: #111827; }
.subtitle { font-size: 12px; color: #6b7280; }
.actions { display: flex; gap: 8px; align-items: center; }
.add-device-trigger { height: 34px; border-radius: 18px; padding: 0 16px; box-shadow: 0 8px 18px rgba(37, 99, 235, 0.18); }
.instance-create-body { padding: 2px 4px 0; }
.instance-create-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; }
.instance-create-form :deep(.el-form-item) { margin-bottom: 8px; }
.instance-create-form :deep(.el-select) { width: 100%; }
.instance-create-form :deep(.el-form-item:nth-last-child(1)) { grid-column: 1 / -1; }
.component-create-panel { margin-bottom: 10px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #f8fafc; display: grid; grid-template-columns: 1.1fr 1fr 1fr 110px auto; gap: 8px; align-items: center; }

.instance-search {
  width: 220px;
}

.stats-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  padding: 12px 20px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.stat-item {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
}

.stat-item strong { font-size: 18px; color: #111827; }

.card-scroll { flex: 1; }

.instance-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}

.card-grid {
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.instance-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
}

.instance-card:hover { border-color: #9ca3af; }

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.name { font-size: 14px; font-weight: 600; color: #111827; }

.line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #4b5563;
  margin-bottom: 6px;
  gap: 8px;
}

.mono {
  font-family: 'Consolas', 'Menlo', monospace;
  color: #111827;
}

.mono-textarea :deep(textarea) {
  font-family: 'Consolas', 'Menlo', monospace;
  font-size: 12px;
}

.empty-wrap { padding: 80px 0; }

.drawer-body { padding: 0 4px; }

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.status-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 10px;
}

.attr-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attr-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.empty-inline {
  color: #9ca3af;
  font-size: 12px;
}

.constraint-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.dataset-create-panel {
  display: grid;
  grid-template-columns: minmax(180px, 0.9fr) minmax(220px, 1.1fr) auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #ccd6e3;
  border-left: 3px solid #2563eb;
  border-radius: 6px;
  background: #f8fafc;
}

.drawer-body :deep(.el-tabs__content) {
  padding-top: 8px;
}

.drawer-body :deep(.el-table th) {
  background: #f3f6fa;
  color: #243244;
}

.mb-12 { margin-bottom: 12px; }
.mt-12 { margin-top: 12px; }

.instance-list-wrap { flex: 1; min-height: 0; padding: 12px; overflow: auto; }
.instance-table { cursor: pointer; }
.instance-table :deep(.el-table__row:hover) { background: #eef6ff; }
.instance-name-cell { display: grid; gap: 3px; }
.instance-name-cell strong { color: #0f172a; font-size: 14px; }
.instance-name-cell span { color: #64748b; font-size: 12px; }
.binding-cell { display: grid; grid-template-columns: 1fr; gap: 4px; }
.binding-cell span { display: grid; grid-template-columns: 58px minmax(0, 1fr); gap: 8px; align-items: center; }
.binding-cell b { color: #64748b; font-weight: 700; }
.topic-cell { display: grid; gap: 3px; }
.topic-cell span { display: grid; grid-template-columns: 42px minmax(0, 1fr); gap: 8px; align-items: center; }
.topic-cell b { color: #64748b; font-weight: 700; }
.topic-cell code, .topic-table code, .topic-preview-grid code { display: block; overflow: hidden; color: #0f172a; font-family: Consolas, Menlo, monospace; text-overflow: ellipsis; white-space: nowrap; }
.instance-detail-dialog :deep(.el-dialog__body) { padding: 8px 14px 14px; }
.drawer-body { padding: 0; }
.instance-info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; }
.instance-info-grid :deep(.el-form-item) { margin-bottom: 2px; }
.readonly-status { height: 32px; display: flex; align-items: center; gap: 8px; color: #64748b; font-size: 12px; }
.topic-section, .snapshot-section { margin-top: 10px; border: 1px solid #cbd5e1; }
.section-caption { height: 34px; padding: 0 10px; border-bottom: 1px solid #dbe4ef; background: #f8fafc; display: flex; align-items: center; color: #0f172a; font-weight: 800; }
.manual-control-form { display: grid; gap: 10px; }
.control-param-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; }
.control-param-grid :deep(.el-form-item) { margin-bottom: 0; }
.field-hint { margin-top: 3px; color: #64748b; font-size: 12px; }
.topic-preview-grid { width: 100%; border: 1px solid #cbd5e1; }
.topic-preview-grid div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 10px; min-height: 32px; padding: 6px 8px; border-bottom: 1px solid #e2e8f0; align-items: center; }
.topic-preview-grid div:last-child { border-bottom: 0; }
.topic-preview-grid span { color: #64748b; font-weight: 700; }
.form-wide { grid-column: 1 / -1; }
@media (max-width: 960px) {
  .main-header {
    align-items: flex-start;
    gap: 12px;
  }

  .actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .instance-search {
    width: 180px;
  }

  .stats-bar {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
