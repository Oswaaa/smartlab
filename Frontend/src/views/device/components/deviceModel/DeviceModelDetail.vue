<template>
<div class="detail-head">
            <div class="detail-title">
              <h2>{{ model.modelName }}</h2>
              <p>{{ model.categoryName || '未分类' }} · {{ formatTime(model.updateTime) }}</p>
            </div>
            <div class="detail-actions">
              <el-button :icon="Download" @click="downloadModelBundle">导出模型文件</el-button>
              <el-tooltip :disabled="!hasInstances" content="该模型下已有设备实例运行，已被锁定，禁止编辑" placement="top">
                <span>
                  <el-button v-if="canEditModel" type="primary" plain :icon="EditPen" :disabled="hasInstances" @click="openEditDrawer(model)">编辑模型</el-button>
                </span>
              </el-tooltip>
              <el-popconfirm v-if="canDeleteModel && !hasInstances" title="确认删除该模型？有设备实例时不可删除。" @confirm="deleteModel(model.modelId)">
                <template #reference>
                  <el-button type="danger" plain :icon="Delete">删除</el-button>
                </template>
              </el-popconfirm>
              <el-tooltip v-else-if="canDeleteModel && hasInstances" content="该模型下已有设备实例运行，已被锁定，禁止删除" placement="top">
                <span>
                  <el-button type="danger" plain :icon="Delete" :disabled="true">删除</el-button>
                </span>
              </el-tooltip>
            </div>
          </div>

          <div class="model-summary-strip">
            <div><span>设备类别</span><strong>{{ model.categoryName || '-' }}</strong></div>
            <div><span>属性</span><strong>{{ selectedAttributes.length }}</strong></div>
            <div><span>操作</span><strong>{{ selectedCapabilities.length }}</strong></div>
            <div><span>Adapter</span><strong>{{ model.adapterContract?.config?.adapterName || '-' }}</strong></div>
          </div>

          <div class="detail-anchor-layout model-detail-workbench">
            <el-anchor class="detail-anchor-menu" @click="(e) => e.preventDefault()" container=".detail-scroll-content .el-scrollbar__wrap" :offset="20">
              <el-anchor-link href="#view-basic" title="01 基础信息" />
              <el-anchor-link href="#view-ability" title="02 属性功能" />
              <el-anchor-link href="#view-adapter" title="03 Adapter 契约" />
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
                <h2 class="section-heading"><span class="section-index">03</span>Adapter 契约</h2>
                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>Adapter 命令</h3>
                    <span class="section-count">{{ selectedAdapterCommands.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterCommands.length === 0" class="compact-empty inline-empty">暂无 Adapter 命令</div>
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
                    <h3>Adapter 属性</h3>
                    <span class="section-count">{{ selectedAdapterAttributes.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterAttributes.length === 0" class="compact-empty inline-empty">暂无 Adapter 属性</div>
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
                    <h3>Adapter 事件</h3>
                    <span class="section-count">{{ selectedAdapterEvents.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterEvents.length === 0" class="compact-empty inline-empty">暂无 Adapter 事件</div>
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
                        <span style="font-size: 12px; color: var(--el-text-color-secondary);">初始状态: {{ region.initialStateName }}</span>
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
                        <strong>{{ row.description || '未命名规则' }}</strong>
                        <span>{{ row.trigger?.interfaceName || '-' }} / {{ row.trigger?.signalName || '-' }}</span>
                      </div>
                      <div class="transition-flow">
                        <span class="filled-state-token light-state-token">{{ row.fromStateName || '-' }}</span>
                        <span class="flow-arrow">→</span>
                        <span class="filled-state-token light-state-token">{{ row.toStateName || '-' }}</span>
                      </div>
                      <div v-if="row.actions?.length" class="action-chip-row">
                        <span v-for="(act, aIdx) in row.actions" :key="aIdx" class="action-chip">{{ describeAction(act) }}</span>
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
                  <el-table v-else :data="selectedComponentsBom" border size="small" class="industrial-table">
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
</template>

<script setup>
import { computed } from 'vue'
import { Download, EditPen, Delete } from '@element-plus/icons-vue'
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

const capabilityModelJson = computed(() => props.modelBundle?.capabilityModel || {})
const stateMachineModelJson = computed(() => props.modelBundle?.stateMachineModel || {})
const selectedStateTransitions = computed(() => asArray(stateMachineModelJson.value?.transitions))
</script>

<style scoped>
.detail-head { flex-shrink: 0; display: flex; justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.detail-title { min-width: 0; }
.detail-title h2 { margin: 0 0 4px; font-size: 20px; line-height: 1.25; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; letter-spacing: 0; }
.detail-title p { margin: 0; color: var(--color-text-sub); font-size: 13px; }
.detail-actions { display: flex; align-items: center; gap: 8px; }

.model-summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  flex-shrink: 0;
  border-top: 1px solid #dfe6ef;
  border-bottom: 1px solid #dfe6ef;
  background: #dfe6ef;
}
.model-summary-strip > div {
  min-width: 0;
  padding: 8px 12px;
  background: #f8fafc;
}
.model-summary-strip span {
  display: block;
  margin-bottom: 3px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}
.model-summary-strip strong {
  display: block;
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-detail-workbench {
  display: flex;
  min-height: 0;
  overflow: hidden;
  height: calc(100vh - 246px);
}
.detail-anchor-layout { min-height: 0; background: #fff; border-top: 1px solid #e5e7eb; }
.detail-anchor-menu {
  width: 168px;
  flex-shrink: 0;
  padding: 10px 8px;
  background: #f8fafc;
  border-right: 1px solid #dfe3ea !important;
}
.detail-anchor-menu :deep(.el-anchor__link) {
  margin-bottom: 3px;
  padding: 8px 10px;
  border-radius: 4px;
  color: #475569;
  font-size: 13px;
  font-weight: 650;
}
.detail-anchor-menu :deep(.el-anchor__link.is-active) { background: #dbeafe; color: #1d4ed8; }
.detail-scroll-content { flex: 1; min-width: 0; }
.detail-scroll-content :deep(.el-scrollbar__view) { padding: 12px 14px 22px; }
.anchor-section { scroll-margin-top: 8px; }
.industrial-section { margin-bottom: 10px; padding-top: 4px; }
.section-heading { display: flex; align-items: center; gap: 8px; margin: 0 0 8px !important; padding: 0 0 7px; border-bottom: 1px solid #dfe6ef; color: #0f172a; font-size: 16px; font-weight: 800; }
.section-index { display: inline-flex; width: 30px; height: 22px; align-items: center; justify-content: center; border: 1px solid #bfdbfe; border-radius: 4px; background: #eff6ff; color: #1d4ed8; font-size: 12px; font-weight: 800; }
.info-section { margin-top: 8px; border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px; background: #fff; }
.section-cluster { box-shadow: inset 3px 0 0 #dbeafe; }
.section-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; min-height: 26px; }
.section-title h3 { margin: 0; font-size: 15px; line-height: 1.3; letter-spacing: 0; }
.section-count { color: #64748b; font-size: 12px; font-weight: 500; }
.attribute-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 8px; }
.adapter-attribute-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 8px; }
.attribute-tile { min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; padding: 9px 10px; }
.tile-title { color: #0f172a; font-size: 14px; font-weight: 650; line-height: 1.35; }
.tile-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; color: #475569; font-size: 12px; }
.tile-meta span { display: inline-flex; align-items: center; min-height: 22px; padding: 1px 7px; border: 1px solid #d7dee8; border-radius: 4px; background: #fff; }
.operation-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 8px; }
.operation-card { min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; padding: 10px; border-left: 3px solid #2563eb; }
.adapter-command-card { border-left-color: #059669; }
.operation-head { display: flex; align-items: flex-start; gap: 9px; min-width: 0; }
.operation-head > div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.operation-head strong { color: #0f172a; font-size: 15px; line-height: 1.35; }
.operation-head span:not(.item-index) { color: #64748b; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.param-chip-row, .action-chip-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.info-chip { display: inline-flex; align-items: center; gap: 5px; padding: 4px 7px; border: 1px solid #bfdbfe; border-radius: 5px; background: #eff6ff; color: #1d4ed8; font-size: 13px; }
.info-chip em, .info-chip i { padding: 1px 4px; border: 1px solid #dbe4ef; border-radius: 4px; background: #fff; color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.info-chip b { color: #1d4ed8; font-weight: 700; }
.port-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 8px; }
.port-tile { min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; padding: 9px 10px; }
.port-name { color: #0f172a; font-size: 14px; font-weight: 650; line-height: 1.35; }
.port-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; color: #475569; font-size: 12px; }
.port-meta span { display: inline-flex; align-items: center; min-height: 22px; padding: 1px 7px; border: 1px solid #d7dee8; border-radius: 4px; background: #fff; }
.mapping-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 8px; }
.mapping-tile { display: grid; grid-template-columns: minmax(150px, 1fr) 28px minmax(150px, 1fr); align-items: center; gap: 8px; min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; }
.mapping-tile-labeled { padding: 8px; background: #f8fafc; }
.mapping-side { min-width: 0; display: grid; grid-template-columns: 64px minmax(0, 1fr); align-items: center; gap: 8px; padding: 7px 8px; border: 1px solid #dbe4ef; border-radius: 5px; background: #fff; }
.mapping-side em { color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.mapping-side b { color: #0f172a; font-size: 13px; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-side { border-left: 3px solid #10b981; }
.mapping-arrow, .flow-arrow { color: #64748b; text-align: center; }
.event-chip-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 8px; }
.event-chip { min-width: 0; display: grid; gap: 3px; padding: 8px 10px; border: 1px solid #dbe2ea; border-left: 3px solid #94a3b8; border-radius: 5px; background: #fbfdff; }
.event-chip b { color: #0f172a; font-size: 13px; line-height: 1.35; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-chip em { color: #64748b; font-size: 12px; font-style: normal; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-chip-cmd { border-left-color: #2563eb; }
.event-chip-op { border-left-color: #059669; }
.function-map-list { display: grid; gap: 8px; }
.function-map-card { min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; padding: 10px; border-left: 3px solid #2563eb; }
.function-map-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.function-map-head strong { color: #0f172a; font-size: 15px; }
.function-param-list { display: grid; gap: 6px; }
.function-param-row { display: grid; grid-template-columns: minmax(150px, 1fr) 28px minmax(150px, 1fr); align-items: center; gap: 8px; min-height: 34px; padding: 6px; border: 1px solid #e5e7eb; border-radius: 4px; background: #fff; }
.state-token-panel { display: flex; flex-wrap: wrap; gap: 7px; min-height: 32px; }
.filled-state-token { display: inline-flex; align-items: center; min-height: 26px; max-width: 180px; padding: 3px 10px; border-radius: 4px; background: #dcfce7; color: #166534; font-size: 13px; font-weight: 650; line-height: 1.35; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-state-token { background: #e0f2fe; color: #075985; }
.op-state-token { background: #dcfce7; color: #166534; }
.light-state-token { background: #eef6ee; color: #166534; }
.transition-card-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 8px; }
.transition-view-card { min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; padding: 10px; }
.transition-card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.transition-card-head strong { min-width: 0; color: #0f172a; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.transition-card-head span { color: #64748b; font-size: 12px; white-space: nowrap; }
.transition-flow { display: flex; align-items: center; gap: 8px; }
.action-chip { display: inline-flex; align-items: center; min-height: 24px; padding: 2px 8px; border-radius: 4px; font-size: 12px; line-height: 1.3; border: 1px solid #cbd5e1; background: #fff; color: #334155; }
.constraint-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 8px; }
.constraint-card { min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; padding: 9px 10px; display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 10px; }
.constraint-card strong { color: #0f172a; font-size: 14px; }
.constraint-card span { color: #b45309; font-weight: 650; }
.constraint-card em { color: #64748b; font-size: 12px; font-style: normal; }
.compact-empty { display: flex; align-items: center; min-height: 30px; padding: 7px 10px; border: 1px dashed #cbd5e1; border-radius: 4px; background: #f8fafc; color: #64748b; font-size: 13px; line-height: 1.4; }
.inline-empty { margin-top: 8px; }
.muted-text { color: var(--color-text-sub); font-size: 12px; }
.model-json-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; min-height: 0; }
.json-panel { min-width: 0; border: 1px solid #dfe4ed; border-radius: 4px; padding: 10px; background: #fff; }
.json-panel pre { margin: 0; max-height: 560px; overflow: auto; padding: 12px; border-radius: 6px; background: #111827; color: #e5e7eb; font-size: 12px; line-height: 1.55; tab-size: 2; }
.adapter-command-line { display: inline-flex; align-items: center; gap: 6px; min-width: 0; color: #475569; }
.adapter-command-line em { padding: 2px 5px; border: 1px solid #dbe4ef; border-radius: 4px; background: #f1f5f9; color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.adapter-command-line b { color: #0f172a; font-size: 13px; font-weight: 750; }
.item-index { width: 22px; height: 22px; flex: 0 0 22px; display: inline-flex; align-items: center; justify-content: center; border-radius: 6px; background: #eef2f7; color: #475569; font-size: 12px; font-weight: 700; }
.fixed-side { border-left: 3px solid #f59e0b; background: #fffbeb; }
</style>
