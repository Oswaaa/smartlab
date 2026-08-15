<template>
  <div class="model-detail-view">
    <div class="model-view-header">
      <div class="header-left">
        <h2 class="model-title">{{ model.modelName }}</h2>
        <span class="model-subtitle">{{ model.categoryName || '未分类' }} · 更新于 {{ formatTime(model.updateTime) }}</span>
      </div>
      <div class="header-actions">
        <button class="btn-aliyun" type="button" @click="downloadModelBundle">
          <el-icon><Download /></el-icon> 导出模型文件
        </button>
        <el-tooltip :disabled="!hasInstances" content="该模型下已有设备实例运行，已被锁定，禁止编辑" placement="top">
          <span>
            <button v-if="canEditModel" class="btn-aliyun-cta" type="button" :disabled="hasInstances" @click="openEditDrawer(model)">
              <el-icon><EditPen /></el-icon> 编辑模型
            </button>
          </span>
        </el-tooltip>
        <el-popconfirm v-if="canDeleteModel && !hasInstances" title="确认删除该模型？有设备实例时不可删除。" @confirm="deleteModel(model.modelId)">
          <template #reference>
            <button class="btn-link danger" type="button" style="margin-left: 8px;">删除模型</button>
          </template>
        </el-popconfirm>
        <el-tooltip v-else-if="canDeleteModel && hasInstances" content="该模型下已有设备实例运行，已被锁定，禁止删除" placement="top">
          <span>
            <button class="btn-link danger" type="button" :disabled="true" style="margin-left: 8px; opacity: 0.5; cursor: not-allowed;">删除模型</button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <!-- 顶部 35px 像素级锁定指标横幅 -->
    <div class="metric-ribbon">
      <div class="m-cell">
        <span class="m-label">所属设备类别</span>
        <strong class="m-val">{{ model.categoryName || '-' }}</strong>
      </div>
      <div class="m-cell">
        <span class="m-label">属性定义项数</span>
        <strong class="m-val highlight">{{ selectedAttributes.length }} 项</strong>
      </div>
      <div class="m-cell">
        <span class="m-label">定义操作数</span>
        <strong class="m-val highlight">{{ selectedCapabilities.length }} 项</strong>
      </div>
      <div class="m-cell">
        <span class="m-label">绑定适配器驱动</span>
        <strong class="m-val">{{ model.adapterContract?.config?.adapterName || '-' }}</strong>
      </div>
    </div>

    <div class="detail-anchor-layout model-detail-workbench">
      <el-anchor class="detail-anchor-menu" @click="(e) => e.preventDefault()" container=".detail-scroll-content .el-scrollbar__wrap" :offset="20">
              <el-anchor-link href="#view-basic" title="01 基础信息" />
              <el-anchor-link href="#view-ability" title="02 属性功能" />
              <el-anchor-link href="#view-adapter" title="03 适配器契约" />
              <el-anchor-link href="#view-mapping" title="04 映射关系" />
              <el-anchor-link href="#view-state" title="05 状态机" />
              <el-anchor-link href="#view-constraint" title="06 内置约束" />
              <el-anchor-link href="#view-bom" title="07 组件结构" />
              <el-anchor-link href="#view-template" title="08 默认数据模板" />
              <el-anchor-link href="#view-file" title="09 模型文件" />
            </el-anchor>
            <el-scrollbar class="detail-scroll-content">
                          <div id="view-basic" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">01</span>基础信息</h2>
              <section class="info-section">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="模型名称">{{ model.modelName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="设备类别">{{ model.categoryName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="通信协议">{{ model.adapterContract?.config?.protocol || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="更新时间">{{ formatTime(model.updateTime) }}</el-descriptions-item>
                </el-descriptions>
              </section>
            </div>

                          <div id="view-ability" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">02</span>属性功能</h2>
                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>设备属性</h3>
                    <span class="section-count">{{ selectedAttributes.length }} 项</span>
                  </div>
                  <div v-if="selectedAttributes.length === 0" class="compact-empty inline-empty">暂无设备属性</div>
                  <div v-else class="attribute-grid">
                    <article v-for="attr in selectedAttributes" :key="attr.attributeName || attr.displayName" class="attribute-tile">
                      <div class="tile-title">{{ attr.displayName || '-' }}</div>
                      <div class="tile-meta">
                        <span>{{ valueKindLabel(attr.valueKind) }}</span>
                        <span>{{ attr.dataType || '-' }}</span>
                        <span v-if="attr.unit">{{ attr.unit }}</span>
                      </div>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>设备操作</h3>
                    <span class="section-count">{{ selectedCapabilities.length }} 项</span>
                  </div>
                  <div v-if="selectedCapabilities.length === 0" class="compact-empty inline-empty">暂无设备操作</div>
                  <div v-else class="operation-grid">
                    <article v-for="(capability, index) in selectedCapabilities" :key="capability.capabilityName || index" class="operation-card">
                      <div class="operation-head">
                        <span class="item-index">{{ index + 1 }}</span>
                        <div>
                          <strong>{{ capability.displayName || '未命名操作' }}</strong>
                          <span class="adapter-command-line"><em>Adapter 命令</em><b>{{ capability.adapterCommandName || '-' }}</b></span>
                        </div>
                      </div>
                      <div class="param-chip-row">
                        <span class="info-chip" :class="capability.isAbort ? 'abort-chip' : 'normal-chip'">
                          <em>类型</em><b>{{ capability.isAbort ? '终止能力' : '普通能力' }}</b>
                        </span>
                        <span v-if="!capability.isAbort && capability.abortCapabilityName" class="info-chip relation-chip">
                          <em>终止能力</em><b>{{ capabilityReferenceLabel(capability.abortCapabilityName) }}</b>
                        </span>
                        <span v-if="capability.isAbort" class="info-chip relation-chip">
                          <em>影响范围</em><b>{{ capabilityScopeLabel(capability) || '未配置' }}</b>
                        </span>
                        <span v-for="param in capability.parameters" :key="param.name || param.displayName" class="info-chip">
                          <em>参数</em><b>{{ param.displayName || '未命名参数' }}</b><i>{{ param.dataType || '-' }}</i>
                        </span>
                        <span v-if="!capability.parameters?.length" class="muted-text">无参数</span>
                      </div>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>端口配置</h3>
                    <span class="section-count">{{ selectedPorts.length }} 项</span>
                  </div>
                  <div v-if="selectedPorts.length === 0" class="compact-empty inline-empty">暂无端口配置</div>
                  <div v-else class="port-grid">
                    <article v-for="(port, index) in selectedPorts" :key="port.portName || index" class="port-tile">
                      <div class="port-name">{{ port.displayName || port.portName || '未命名端口' }}</div>
                      <div class="port-meta">
                        <span>{{ directionLabel(port.direction) }}</span>
                        <span>{{ displayAttributeName(port.bindingAttrName, selectedAttributes) }}</span>
                      </div>
                    </article>
                  </div>
                </section>
              </div>

                          <div id="view-adapter" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">03</span>适配器契约</h2>
                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>适配器命令</h3>
                    <span class="section-count">{{ selectedAdapterCommands.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterCommands.length === 0" class="compact-empty inline-empty">暂无适配器命令</div>
                  <div v-else class="operation-grid">
                    <article v-for="(command, index) in selectedAdapterCommands" :key="command.commandName || index" class="operation-card adapter-command-card">
                      <div class="operation-head">
                        <span class="item-index">{{ index + 1 }}</span>
                        <div>
                          <strong>{{ command.commandName || '未命名命令' }}</strong>
                          <span>{{ command.description || '无说明' }}</span>
                        </div>
                      </div>
                      <div class="param-chip-row">
                        <span v-for="param in command.commandParameters" :key="param.paramName" class="info-chip">
                          <em>参数</em><b>{{ param.paramName }}</b><i>{{ param.dataType || '-' }}</i>
                        </span>
                        <span v-if="!command.commandParameters?.length" class="muted-text">无参数</span>
                      </div>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>适配器遥测属性</h3>
                    <span class="section-count">{{ selectedAdapterAttributes.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterAttributes.length === 0" class="compact-empty inline-empty">暂无适配器遥测属性</div>
                  <div v-else class="attribute-grid adapter-attribute-grid">
                    <article v-for="attr in selectedAdapterAttributes" :key="attr.telemetryName" class="attribute-tile">
                      <div class="tile-title">{{ attr.description || attr.telemetryName || '-' }}</div>
                      <div class="tile-meta">
                        <span>{{ attr.telemetryName || '-' }}</span>
                        <span>{{ attr.dataType || '-' }}</span>
                      </div>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>适配器事件</h3>
                    <span class="section-count">{{ selectedAdapterEvents.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterEvents.length === 0" class="compact-empty inline-empty">暂无适配器事件</div>
                  <div v-else class="event-chip-grid">
                    <span v-for="event in selectedAdapterEvents" :key="event.eventName" class="event-chip" :class="event.eventType === 'CMD' ? 'event-chip-cmd' : 'event-chip-op'">
                      <b>{{ event.eventName }}</b>
                      <em>{{ event.description || eventTypeLabel(event.eventType) }}</em>
                    </span>
                  </div>
                </section>
              </div>

                          <div id="view-mapping" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">04</span>映射关系</h2>
                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>属性映射</h3>
                    <span class="section-count">{{ selectedAttributeMappings.length }} 项</span>
                  </div>
                  <div v-if="selectedAttributeMappings.length === 0" class="compact-empty inline-empty">暂无属性映射</div>
                  <div v-else class="mapping-grid">
                    <article v-for="mapping in selectedAttributeMappings" :key="mapping.adapterAttrName" class="mapping-tile mapping-tile-labeled">
                      <span class="mapping-side"><em>模型属性</em><b>{{ displayAttributeName(mapping.modelAttributeName, selectedAttributes) }}</b></span>
                      <span class="mapping-arrow">→</span>
                      <span class="mapping-side adapter-side"><em>Adapter 属性</em><b>{{ mapping.adapterAttrName || '-' }}</b></span>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>功能映射</h3>
                    <span class="section-count">{{ functionMappingGroups(selectedCapabilities).length }} 项</span>
                  </div>
                  <div v-if="functionMappingGroups(selectedCapabilities).length === 0" class="compact-empty inline-empty">暂无功能映射</div>
                  <div v-else class="function-map-list">
                    <article v-for="group in functionMappingGroups(selectedCapabilities)" :key="group.key" class="function-map-card">
                      <div class="function-map-head">
                        <strong>{{ group.capabilityDisplayName }}</strong>
                        <span class="adapter-command-line"><em>Adapter 命令</em><b>{{ group.adapterCommandName }}</b></span>
                      </div>
                      <div v-if="group.parameters.length === 0" class="muted-text">未配置参数映射</div>
                      <div v-else class="function-param-list">
                        <div v-for="param in group.parameters" :key="param.key" class="function-param-row">
                          <span class="mapping-side adapter-side"><em>Adapter 参数</em><b>{{ param.commandParamName }}</b></span>
                          <span class="mapping-arrow">←</span>
                          <span v-if="param.isFixedValue" class="mapping-side fixed-side"><em>固定值</em><b>{{ param.fixedValue }}</b></span>
                          <span v-else class="mapping-side"><em>功能参数</em><b>{{ param.capabilityParamDisplayName }}</b></span>
                        </div>
                      </div>
                    </article>
                  </div>
                </section>
              </div>

                          <div id="view-state" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">05</span>状态机</h2>
                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>指令生命周期</h3>
                    <span class="section-count">{{ selectedCmdStates.length }} 个状态</span>
                  </div>
                  <div v-if="selectedCmdStates.length === 0" class="compact-empty inline-empty">暂无指令生命周期状态</div>
                  <div v-else class="state-token-panel">
                    <span v-for="state in selectedCmdStates" :key="state.stateName" class="filled-state-token cmd-state-token">{{ state.stateName }}</span>
                  </div>
                </section>

                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>功能状态 (按分区)</h3>
                    <span class="section-count">{{ selectedOpStateRegions.length }} 个分区</span>
                  </div>
                  <div v-if="selectedOpStateRegions.length === 0" class="compact-empty inline-empty">暂无功能状态分区</div>
                  <div v-else class="region-token-panels">
                    <div v-for="region in selectedOpStateRegions" :key="region.regionName" class="region-token-panel" style="margin-bottom: 12px;">
                      <div class="region-title" style="margin-bottom: 8px; font-weight: bold; color: var(--el-text-color-regular);">
                        <el-tag size="small" effect="dark" style="margin-right: 8px;">{{ region.regionName }}</el-tag>
                        <el-tag size="small" effect="plain" :type="region.regionType === 'EXCEPTION' ? 'danger' : 'success'" style="margin-right: 8px;">{{ region.regionType === 'EXCEPTION' ? '异常区域' : '功能区域' }}</el-tag>
                        <span v-if="region.regionType !== 'EXCEPTION'" style="font-size: 12px; color: var(--el-text-color-secondary);">初始状态: {{ region.initialStateName }}</span>
                      </div>
                      <div class="state-token-panel">
                        <span v-for="state in region.states" :key="state.stateName" class="filled-state-token op-state-token">{{ state.stateName }}</span>
                      </div>
                    </div>
                  </div>
                </section>

                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>转移规则</h3>
                    <span class="section-count">{{ selectedStateTransitions.length }} 条</span>
                  </div>
                  <div v-if="selectedStateTransitions.length === 0" class="compact-empty inline-empty">暂无转移规则</div>
                  <div v-else class="transition-card-list">
                    <article v-for="(row, index) in selectedStateTransitions" :key="index" class="transition-view-card">
                      <div class="transition-card-head">
                        <div class="head-left-info">
                          <span class="rule-index-tag">#{{ index + 1 }}</span>
                          <strong class="rule-title" :title="row.description || '状态流转规则'">{{ row.description || '状态流转规则' }}</strong>
                        </div>
                        <span v-if="row.regionName" class="rule-region-badge">{{ row.regionName }}</span>
                      </div>

                      <div class="transition-flow-row">
                        <span class="flow-pill origin-pill">{{ row.fromStateName || '-' }}</span>
                        <el-icon class="flow-arrow-icon"><Right /></el-icon>
                        <span class="flow-pill target-pill">{{ row.toStateName || '-' }}</span>
                      </div>

                      <div v-if="row.trigger?.signalName || row.trigger?.interfaceName" class="transition-meta-row">
                        <span class="meta-label">触发机制</span>
                        <div class="meta-content">
                          <span class="trigger-iface-tag">{{ row.trigger.interfaceName || '适配器' }}</span>
                          <span class="trigger-signal-badge">{{ formatTransitionSignal(row.trigger.signalName) }}</span>
                        </div>
                      </div>

                      <div v-if="row.actions?.length" class="transition-meta-row">
                        <span class="meta-label">转移动作</span>
                        <div class="meta-content action-tags-wrap">
                          <span v-for="(act, aIdx) in row.actions" :key="aIdx" class="action-chip">{{ describeAction(act) }}</span>
                        </div>
                      </div>
                    </article>
                  </div>
                </section>
              </div>

                          <div id="view-constraint" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">06</span>内置约束</h2>
                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>内置约束</h3>
                    <span class="section-count">{{ selectedConstraints.length }} 条</span>
                  </div>
                  <div v-if="selectedConstraints.length === 0" class="compact-empty inline-empty">暂无内置约束</div>
                  <div v-else class="constraint-list">
                    <article v-for="(row, index) in selectedConstraints" :key="index" class="constraint-card">
                      <strong>{{ displayAttributeName(row.objectAttributeName, selectedAttributes) }}</strong>
                      <span>{{ operatorLabel(row.operator) }} {{ row.boundaryValue }}</span>
                      <em>违规状态：{{ row.violationStateName || '-' }}</em>
                    </article>
                  </div>
                </section>
              </div>

              <div id="view-bom" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">07</span>组件结构</h2>
                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>组件结构清单 (BOM)</h3>
                    <span class="section-count">{{ selectedComponentsBom.length }} 项</span>
                  </div>
                  <div v-if="selectedComponentsBom.length === 0" class="compact-empty inline-empty">暂无组件结构清单</div>
                  <div v-else class="table-card">
                    <el-table :data="selectedComponentsBom" border stripe size="small" class="unified-table-el">
                      <el-table-column label="组件名称 (slotName)" min-width="160">
                        <template #default="{ row }"><strong>{{ row.slotName || row.componentName || row.name || '-' }}</strong></template>
                      </el-table-column>
                      <el-table-column label="设备类别" min-width="140">
                        <template #default="{ row }">{{ row.categoryName || categoryNameById(row.categoryId) || '-' }}</template>
                      </el-table-column>
                      <el-table-column label="数量" width="100">
                        <template #default="{ row }">{{ row.quantity || 1 }}</template>
                      </el-table-column>
                      <el-table-column label="描述" min-width="220">
                        <template #default="{ row }">{{ row.description || '-' }}</template>
                      </el-table-column>
                    </el-table>
                  </div>
                </section>
              </div>

              <div id="view-template" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">08</span>默认数据模板</h2>
                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>已选模板字段</h3>
                    <span class="section-count">{{ defaultTemplateAttributes.length }} 项</span>
                  </div>
                  <div v-if="defaultTemplateAttributes.length === 0" class="compact-empty inline-empty">暂无默认数据模板配置</div>
                  <div v-else class="attribute-grid">
                    <article v-for="attr in defaultTemplateAttributes" :key="attr.attributeName || attr.displayName" class="attribute-tile">
                      <div class="tile-title">{{ attr.displayName || '-' }}</div>
                      <div class="tile-meta">
                        <span>{{ attr.dataType || '-' }}</span>
                        <span v-if="attr.unit">{{ attr.unit }}</span>
                      </div>
                    </article>
                  </div>
                </section>
              </div>

              <div id="view-file" class="anchor-section industrial-section">
                <h2 class="section-heading"><span class="section-index">09</span>模型文件</h2>
                <section class="model-json-grid">
                  <div class="json-panel">
                    <div class="section-title"><h3>设备能力模型</h3></div>
                    <pre>{{ formatJson(capabilityModelJson) }}</pre>
                  </div>
                  <div class="json-panel">
                    <div class="section-title"><h3>设备状态机模型</h3></div>
                    <pre>{{ formatJson(stateMachineModelJson) }}</pre>
                  </div>
                </section>
              </div>
            </el-scrollbar>
          </div>
        </div>
      </template>

<script setup>
import { computed } from 'vue'
import { Download, EditPen, Delete, Right } from '@element-plus/icons-vue'
import {
  asArray, formatTime, formatJson,
  valueKindLabel, directionLabel, describeAction, operatorLabel, eventTypeLabel,
  functionMappingGroups, displayAttributeName,
  normalizeEventsToFlatList
} from './normalizers.js'

const props = defineProps({
  model: Object,
  modelBundle: Object,
  defaultTemplateAttributes: Array,
  categories: Array,
  canEditModel: Boolean,
  canDeleteModel: Boolean,
  hasInstances: Boolean,
})


const emit = defineEmits(['edit', 'delete', 'download'])

function downloadModelBundle() { emit('download') }
function openEditDrawer(model) { emit('edit', model) }
function deleteModel(id) { emit('delete', id) }
function categoryNameById(id) {
  return props.categories.find(item => String(item.id) === String(id))?.categoryName || ''
}
function selectModel(id) { /* Do nothing or emit event if needed, but in original it called selectModel */ }

function formatTransitionSignal(signalName) {
  if (!signalName) return '-'
  if (signalName === 'COMMAND_RUNNING') return '指令执行中 (COMMAND_RUNNING)'
  if (signalName === 'COMMAND_SUCCESS') return '指令执行成功 (COMMAND_SUCCESS)'
  if (signalName === 'COMMAND_FAILED') return '指令执行失败 (COMMAND_FAILED)'
  if (signalName === 'CMD_START') return '启动命令 (CMD_START)'
  if (signalName === 'CMD_ABORT') return '中止命令 (CMD_ABORT)'
  if (signalName === 'OP_STATE') return '功能状态上报 (OP_STATE)'
  if (signalName === 'CMD_STATE') return '指令状态上报 (CMD_STATE)'
  return signalName
}

const selectedAttributes = computed(() => asArray(props.model?.attributes))
const selectedCapabilities = computed(() => asArray(props.model?.capabilities))
const selectedPorts = computed(() => asArray(props.model?.ports))
const selectedAdapterCommands = computed(() => asArray(props.model?.adapterContract?.commands))
const selectedAdapterAttributes = computed(() => asArray(props.model?.adapterContract?.telemetry?.adapterAttributes))
const selectedAdapterEvents = computed(() => normalizeEventsToFlatList(props.model?.adapterContract?.events))
const selectedAttributeMappings = computed(() => asArray(props.model?.adapterContract?.telemetry?.attributesMapping))
const selectedCmdStates = computed(() => asArray(props.model?.cmdState?.states))
const selectedOpStateRegions = computed(() => asArray(props.model?.opState?.regions))
const selectedConstraints = computed(() => asArray(props.model?.intrinsicConstraints))
const selectedComponentsBom = computed(() => asArray(props.model?.componentsBom))

function capabilityReferenceLabel(capabilityName) {
  const capability = selectedCapabilities.value.find(item => item.capabilityName === capabilityName || item.name === capabilityName)
  return capability?.displayName || capabilityName || '-'
}

function capabilityScopeLabel(capability) {
  return asArray(capability?.scope).map(capabilityReferenceLabel).join('、')
}

const capabilityModelJson = computed(() => props.modelBundle?.capabilityModel || {})
const stateMachineModelJson = computed(() => props.modelBundle?.stateMachineModel || {})
const selectedStateTransitions = computed(() => asArray(stateMachineModelJson.value?.transitions))
</script>

<style scoped>
.model-detail-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #ffffff;
}

.model-view-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--sl-border-base);
  background: #ffffff;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.model-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--sl-text-heading);
  line-height: 1.3;
}
.model-subtitle {
  font-size: 11px;
  color: var(--sl-text-secondary);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 顶部 35px 像素级锁定指标横幅 */
.metric-ribbon {
  padding: 5px 16px;
  border-bottom: 1px solid var(--sl-border-base);
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  background: #ffffff;
  flex-shrink: 0;
}
.m-cell {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.m-label {
  font-size: 11px;
  color: var(--sl-text-secondary);
  line-height: 1.2;
}
.m-val {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
}
.m-val.highlight {
  font-size: 14.5px;
  color: var(--sl-primary);
  font-family: var(--sl-font-mono);
}

.model-detail-workbench {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
  background: #ffffff;
}

.detail-anchor-menu {
  width: 148px;
  flex-shrink: 0;
  padding: 8px 6px;
  background: #ffffff;
  border-right: 1px solid var(--sl-border-base) !important;
}
.detail-anchor-menu :deep(.el-anchor__link) {
  margin-bottom: 2px;
  padding: 6px 8px;
  border-radius: 4px;
  color: var(--sl-text-body);
  font-size: 12px;
  font-weight: 500;
  transition: all 0.15s ease;
}
.detail-anchor-menu :deep(.el-anchor__link:hover) {
  background: #f1f5f9;
}
.detail-anchor-menu :deep(.el-anchor__link.is-active) {
  background: #eff6ff;
  color: var(--sl-primary);
  font-weight: 600;
}

.detail-scroll-content {
  flex: 1;
  min-width: 0;
}
.detail-scroll-content :deep(.el-scrollbar__view) {
  padding: 12px 18px 24px;
}

.anchor-section {
  scroll-margin-top: 8px;
}
.industrial-section {
  margin-bottom: 12px;
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px !important;
  padding: 0 0 6px;
  border-bottom: 1px solid var(--sl-border-base);
  color: var(--sl-text-heading);
  font-size: 14px;
  font-weight: 700;
}
.section-index {
  display: inline-flex;
  width: 26px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--sl-primary-border);
  border-radius: 4px;
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono);
}

.info-section {
  margin-top: 6px;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 10px 12px;
  background: #ffffff;
}
.section-cluster {
  box-shadow: none;
}
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  min-height: 24px;
}
.section-title h3 {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--sl-text-heading);
}
.section-count {
  color: var(--sl-text-secondary);
  font-size: 11.5px;
  font-weight: 500;
}

.attribute-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
}
.adapter-attribute-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 8px;
}
.attribute-tile {
  min-width: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  padding: 8px 10px;
}
.tile-title {
  color: var(--sl-text-heading);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
}
.tile-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 5px;
  color: var(--sl-text-secondary);
  font-size: 11.5px;
}
.tile-meta span {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 1px 6px;
  border: 1px solid var(--sl-border-base);
  border-radius: 4px;
  background: #f8fafc;
}

.operation-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 8px;
}
.operation-card {
  min-width: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  padding: 10px;
  border-left: 3px solid var(--sl-primary);
}
.adapter-command-card {
  border-left-color: var(--sl-success);
}
.operation-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}
.operation-head > div {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.operation-head strong {
  color: var(--sl-text-heading);
  font-size: 13.5px;
  line-height: 1.35;
}
.operation-head span:not(.item-index) {
  color: var(--sl-text-secondary);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-chip-row, .action-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.info-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 7px;
  border: 1px solid var(--sl-primary-border);
  border-radius: 4px;
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  font-size: 11.5px;
}
.info-chip em, .info-chip i {
  padding: 0 3px;
  border: 1px solid var(--sl-border-base);
  border-radius: 3px;
  background: #ffffff;
  color: var(--sl-text-secondary);
  font-size: 10.5px;
  font-style: normal;
  font-weight: 600;
}
.info-chip b {
  color: var(--sl-primary);
  font-weight: 600;
}
.info-chip.abort-chip {
  border-color: var(--sl-danger-border);
  background: var(--sl-danger-light);
  color: var(--sl-danger);
}
.info-chip.abort-chip b {
  color: var(--sl-danger);
}
.info-chip.normal-chip {
  border-color: var(--sl-primary-border);
  background: var(--sl-primary-light);
}
.info-chip.relation-chip {
  max-width: 100%;
  border-color: var(--sl-border-base);
  background: #f8fafc;
}
.info-chip.relation-chip b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.port-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 8px;
}
.port-tile {
  min-width: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  padding: 8px 10px;
}
.port-name {
  color: var(--sl-text-heading);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
}
.port-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 5px;
  color: var(--sl-text-secondary);
  font-size: 11.5px;
}
.port-meta span {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 1px 6px;
  border: 1px solid var(--sl-border-base);
  border-radius: 4px;
  background: #f8fafc;
}

.mapping-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 8px;
}
.mapping-tile {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) 24px minmax(140px, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  padding: 6px 8px;
}
.mapping-side {
  min-width: 0;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  padding: 5px 6px;
  border: 1px solid var(--sl-border-base);
  border-radius: 4px;
  background: #ffffff;
}
.mapping-side em {
  color: var(--sl-text-secondary);
  font-size: 10.5px;
  font-style: normal;
  font-weight: 600;
}
.mapping-side b {
  color: var(--sl-text-heading);
  font-size: 12.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.adapter-side {
  border-left: 3px solid var(--sl-success);
}
.mapping-arrow, .flow-arrow {
  color: var(--sl-text-secondary);
  text-align: center;
  font-size: 12px;
}

.event-chip-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
}
.event-chip {
  min-width: 0;
  display: grid;
  gap: 2px;
  padding: 7px 9px;
  border: 1px solid var(--sl-border-base);
  border-left: 3px solid var(--sl-text-secondary);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
}
.event-chip b {
  color: var(--sl-text-heading);
  font-size: 12.5px;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.event-chip em {
  color: var(--sl-text-secondary);
  font-size: 11px;
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.event-chip-cmd {
  border-left-color: var(--sl-primary);
}
.event-chip-op {
  border-left-color: var(--sl-success);
}

.function-map-list {
  display: grid;
  gap: 8px;
}
.function-map-card {
  min-width: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  padding: 10px;
  border-left: 3px solid var(--sl-primary);
}
.function-map-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.function-map-head strong {
  color: var(--sl-text-heading);
  font-size: 13.5px;
}
.function-map-head span {
  color: var(--sl-text-secondary);
  font-size: 11.5px;
}
.function-param-list {
  display: grid;
  gap: 6px;
}
.function-param-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) 24px minmax(140px, 1fr);
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 4px 6px;
  border: 1px solid var(--sl-border-base);
  border-radius: 4px;
  background: #ffffff;
}

.state-token-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 28px;
}
.filled-state-token {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  max-width: 180px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--sl-success-light);
  color: var(--sl-success);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cmd-state-token {
  background: var(--sl-primary-light);
  color: var(--sl-primary);
}
.op-state-token {
  background: var(--sl-success-light);
  color: var(--sl-success);
}
.light-state-token {
  background: #f1f5f9;
  color: var(--sl-text-body);
}

.transition-card-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 10px;
}
.transition-view-card {
  min-width: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.15s ease;
}
.transition-view-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.transition-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.head-left-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}
.rule-index-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 1px 5px;
  border-radius: 3px;
  background: #f1f5f9;
  color: var(--sl-text-secondary);
  font-size: 11px;
  font-family: var(--sl-font-mono);
  font-weight: 600;
  flex-shrink: 0;
}
.rule-title {
  color: var(--sl-text-heading);
  font-size: 12.5px;
  font-weight: 600;
  line-height: 1.4;
  word-break: break-all;
}
.rule-region-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #2563eb;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
}

.transition-flow-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-subtle);
  border-radius: 4px;
}
.flow-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  font-family: var(--sl-font-mono);
}
.origin-pill {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  color: var(--sl-text-heading);
}
.flow-arrow-icon {
  color: var(--sl-primary);
  font-size: 13px;
}
.target-pill {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
}

.transition-meta-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 11.5px;
}
.meta-label {
  color: var(--sl-text-secondary);
  font-size: 11px;
  min-width: 52px;
  flex-shrink: 0;
  margin-top: 2px;
}
.meta-content {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}
.trigger-iface-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base);
  border-radius: 3px;
  font-size: 11px;
  color: var(--sl-text-secondary);
}
.trigger-signal-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  border-radius: 3px;
  font-size: 11px;
  color: #047857;
  font-weight: 500;
}
.action-tags-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.action-chip {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  line-height: 1.3;
  border: 1px solid var(--sl-border-base);
  background: #f8fafc;
  color: var(--sl-text-body);
}

button:disabled,
.btn-aliyun:disabled,
.btn-aliyun-cta:disabled,
.btn-link:disabled {
  cursor: not-allowed !important;
  opacity: 0.38 !important;
  pointer-events: none !important;
  transform: none !important;
  transition: none !important;
  box-shadow: none !important;
  animation: none !important;
}

.constraint-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px;
}
.constraint-card {
  min-width: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  padding: 8px 10px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 8px;
}
.constraint-card strong {
  color: var(--sl-text-heading);
  font-size: 13px;
}
.constraint-card span {
  color: var(--sl-warning);
  font-weight: 600;
  font-family: var(--sl-font-mono);
}
.constraint-card em {
  color: var(--sl-text-secondary);
  font-size: 11.5px;
  font-style: normal;
}

.compact-empty {
  display: flex;
  align-items: center;
  min-height: 28px;
  padding: 6px 10px;
  border: 1px dashed var(--sl-border-base);
  border-radius: 4px;
  background: #f8fafc;
  color: var(--sl-text-secondary);
  font-size: 12px;
}
.inline-empty {
  margin-top: 6px;
}

/* 一体化表格卡片容器 */
.table-card {
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}
.table-card :deep(.el-table) {
  border: none !important;
}

.model-json-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-height: 0;
}
.json-panel {
  min-width: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  padding: 10px;
  background: #ffffff;
}
.json-panel pre {
  margin: 0;
  max-height: 480px;
  overflow: auto;
  padding: 10px;
  border-radius: 4px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: var(--sl-font-mono);
  font-size: 11.5px;
  line-height: 1.5;
  tab-size: 2;
}

.adapter-command-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--sl-text-secondary);
}
.adapter-command-line em {
  padding: 1px 4px;
  border: 1px solid var(--sl-border-base);
  border-radius: 3px;
  background: #f1f5f9;
  color: var(--sl-text-secondary);
  font-size: 10.5px;
  font-style: normal;
  font-weight: 600;
}
.adapter-command-line b {
  color: var(--sl-text-heading);
  font-size: 12.5px;
  font-weight: 600;
}
.item-index {
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  background: #f1f5f9;
  color: var(--sl-text-secondary);
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono);
}
.fixed-side {
  border-left: 3px solid var(--sl-warning);
  background: var(--sl-warning-light);
}
</style>
