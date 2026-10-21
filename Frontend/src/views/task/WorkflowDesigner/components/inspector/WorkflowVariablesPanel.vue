<template>
  <section class="variables-panel">
    <section class="variables-config-card">
      <div class="panel-section-heading">
        <div class="heading-text">
          <h4>内部变量配置</h4>
          <p>节点内部变量，可供表达式、数据端口和触发器引用。</p>
        </div>
        <div class="heading-actions">
          <span class="param-count-badge">{{ variables.length }} 项变量</span>
          <button v-if="!readonly" type="button" class="btn-aliyun-cta" @click="addVariable">
            + 新增变量
          </button>
        </div>
      </div>

      <div v-if="variables.length" class="variables-inline-table" :class="{ 'is-device': node.nodeType === 'DEV_NODE' }">
        <!-- 极简表头 -->
        <div class="inline-table-head">
          <span class="col-head-name">变量名称</span>
          <span class="col-head-type">数据类型</span>
          <span v-if="node.nodeType === 'DEV_NODE'" class="col-head-mapping-kind">映射类型</span>
          <span v-if="node.nodeType === 'DEV_NODE'" class="col-head-mapping">映射对象</span>
          <span class="col-head-action">操作</span>
        </div>

        <!-- 变量数据行 (单行 36px 紧凑排布，使用稳定 index 作为 key 防止重绘丢失焦点) -->
        <div v-for="(row, index) in variables" :key="index" class="inline-table-row">
          <!-- 1. 变量名称 -->
          <div class="col-cell-name">
            <el-input
              :model-value="row.name"
              placeholder="变量名"
              :disabled="readonly || isSystemItem(row)"
              @update:model-value="updateVariable(row, 'name', $event)"
            />
          </div>

          <!-- 2. 数据类型 -->
          <div class="col-cell-type">
            <el-select
              :model-value="row.dataType"
              :disabled="readonly || isSystemItem(row) || !!row.stateMapping"
              @update:model-value="updateVariable(row, 'dataType', $event)"
            >
              <el-option v-for="type in dataTypes" :key="type" :label="type" :value="type" />
            </el-select>
          </div>

          <!-- 3. 映射类型 (DEV_NODE 专属) -->
          <div v-if="node.nodeType === 'DEV_NODE'" class="col-cell-mapping-kind">
            <el-select
              :model-value="mappingKind(row)"
              :disabled="readonly || isSystemItem(row)"
              @update:model-value="changeMappingKind(row, $event)"
            >
              <el-option label="无" value="NONE" />
              <el-option label="设备属性" value="ATTRIBUTE" />
              <el-option label="设备状态" value="STATE" />
            </el-select>
          </div>

          <!-- 4. 映射对象：设备属性或设备状态机区域 -->
          <div v-if="node.nodeType === 'DEV_NODE'" class="col-cell-mapping">
            <el-select
              v-if="mappingKind(row) === 'ATTRIBUTE'"
              :model-value="row.attributesMapping"
              filterable
              :disabled="readonly || isSystemItem(row)"
              placeholder="选择绑定的设备属性"
              @update:model-value="mapAttribute(row, $event)"
            >
              <el-option
                v-for="attribute in deviceAttributes"
                :key="attribute.attributeName"
                :label="attributeOptionLabel(attribute)"
                :value="attribute.attributeName"
              >
                <div class="attr-option-layout">
                  <span class="attr-opt-main">{{ attribute.displayName || attribute.attributeName }}</span>
                  <span class="attr-opt-type-badge">{{ attribute.dataType || '未知' }}</span>
                </div>
              </el-option>
            </el-select>
            <el-select
              v-else-if="mappingKind(row) === 'STATE'"
              :model-value="row.stateMapping"
              filterable
              :disabled="readonly || isSystemItem(row)"
              placeholder="选择设备状态区域"
              @update:model-value="mapState(row, $event)"
            >
              <el-option
                v-for="region in deviceStateRegions"
                :key="region.regionName"
                :label="regionOptionLabel(region)"
                :value="region.regionName"
              >
                <div class="attr-option-layout">
                  <span class="attr-opt-main">{{ region.regionName }}</span>
                  <span class="attr-opt-type-badge">{{ regionTypeLabel(region.regionType) }}</span>
                </div>
              </el-option>
            </el-select>
            <span v-else class="mapping-none">—</span>
          </div>

          <!-- 5. 操作 -->
          <div class="col-cell-action">
            <el-tag v-if="isSystemItem(row)" size="small" type="info">系统</el-tag>
            <button
              v-else-if="!readonly"
              type="button"
              class="btn-aliyun-danger-link"
              @click="deleteVariable(row.name)"
            >
              删除
            </button>
          </div>
        </div>
      </div>

      <WorkflowConfigurationEmpty
        v-else
        title="尚未配置内部变量"
        description="内部变量可被计算表达式、数据端口和控制触发器引用。"
        :action-label="readonly ? '' : '新增变量'"
        @action="addVariable"
      />
    </section>

    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import WorkflowConfigurationEmpty from './WorkflowConfigurationEmpty.vue'
import { isSystemItem, removeVariable, stateMappingDataType } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, readonly?: boolean, deviceAttributes?: Item[], deviceStateRegions?: Item[] }>(), { readonly: false, deviceAttributes: () => [], deviceStateRegions: () => [] })
const emit = defineEmits<{ 'update:node': [node: Item] }>()
const dataTypes = ['INTEGER', 'DOUBLE', 'STRING', 'BOOLEAN', 'JSON']
const message = ref('')
const variables = computed(() => props.node.internalVariables || [])

function publish(internalVariables: Item[]) { if (props.readonly) return; message.value = ''; emit('update:node', { ...props.node, internalVariables }) }
function uniqueName() { let index = 1; while (variables.value.some((item: Item) => item.name === `variable${index}`)) index += 1; return `variable${index}` }
function addVariable() { publish([...variables.value, { name: uniqueName(), dataType: 'STRING' }]) }
function updateVariable(target: Item, field: string, value: unknown) {
  publish(variables.value.map((item: Item) => item === target ? { ...item, [field]: value } : item))
}
function withoutMappings(item: Item) {
  const { attributesMapping, stateMapping, ...rest } = item
  return rest
}
function mappingKind(row: Item) {
  if (row.stateMapping) return 'STATE'
  if (Object.hasOwn(row, 'attributesMapping')) return 'ATTRIBUTE'
  return 'NONE'
}
function changeMappingKind(target: Item, kind: string) {
  publish(variables.value.map((item: Item) => {
    if (item !== target) return item
    const base = withoutMappings(item)
    if (kind === 'ATTRIBUTE') return { ...base, attributesMapping: '' }
    if (kind === 'STATE') {
      const region = props.deviceStateRegions[0]
      return region ? { ...base, stateMapping: region.regionName, dataType: stateMappingDataType(region.regionType) } : base
    }
    return base
  }))
}
function mapAttribute(target: Item, attributeName: string) {
  publish(variables.value.map((item: Item) => item === target ? { ...withoutMappings(item), attributesMapping: attributeName } : item))
}
function mapState(target: Item, regionName: string) {
  const region = props.deviceStateRegions.find((item: Item) => item.regionName === regionName)
  publish(variables.value.map((item: Item) => item === target
    ? { ...withoutMappings(item), stateMapping: regionName, dataType: stateMappingDataType(region?.regionType) }
    : item))
}
function attributeOptionLabel(attribute: Item) {
  const name = attribute.displayName || attribute.attributeName || '未命名属性'
  const type = attribute.dataType ? ` · ${attribute.dataType}` : ''
  return `${name}${type}`
}
function regionTypeLabel(regionType: string) {
  return regionType === 'EXCEPTION' ? '异常区域 · JSON' : '运行区域 · STRING'
}
function regionOptionLabel(region: Item) {
  return `${region.regionName} · ${regionTypeLabel(region.regionType)}`
}
function deleteVariable(name: string) {
  try {
    publish(removeVariable(props.node, name).internalVariables || [])
  } catch (error: any) {
    message.value = error.message
  }
}
</script>

<style scoped>
.variables-panel {
  display: grid;
  gap: 12px;
  padding: 0;
  background: transparent;
}
.variables-panel :deep(.el-select),
.variables-panel :deep(.el-input) {
  width: 100%;
}

/* 一体化卡片容器：对齐业务配置卡片规范 */
.variables-config-card {
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.02);
  overflow: hidden;
}

.panel-section-heading {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  box-sizing: border-box;
}

.heading-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.heading-text h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}

.heading-text p {
  margin: 0;
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}

.param-count-badge {
  font-size: 10.5px;
  font-weight: 600;
  color: var(--sl-primary, #2563eb);
  background: var(--sl-primary-light, #eff6ff);
  padding: 1px 6px;
  border-radius: 4px;
  flex: none;
}

/* 列宽按抽屉内容区分配，映射类型和删除按钮不再被裁切 */
.variables-inline-table {
  display: flex;
  flex-direction: column;
  overflow-x: auto;
}

.inline-table-head,
.inline-table-row {
  display: grid;
  grid-template-columns: minmax(88px, 1.2fr) 108px 48px;
  align-items: center;
  gap: 8px;
  min-width: 280px;
}

.variables-inline-table.is-device .inline-table-head,
.variables-inline-table.is-device .inline-table-row {
  grid-template-columns: minmax(84px, 0.8fr) 96px 112px minmax(168px, 1.9fr) 48px;
  min-width: 540px;
}

.inline-table-head {
  padding: 6px 12px;
  background: var(--sl-bg-hover, #f8fafc);
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-text-secondary, #64748b);
}

.inline-table-row {
  padding: 6px 12px;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  transition: background-color 0.15s ease;
}

.inline-table-row:last-child {
  border-bottom: none;
}

.inline-table-row:hover {
  background: var(--sl-bg-hover, #f8fafc);
}

.col-head-name,
.col-cell-name,
.col-head-type,
.col-cell-type,
.col-head-mapping-kind,
.col-cell-mapping-kind,
.col-head-mapping,
.col-cell-mapping {
  min-width: 0;
}

.mapping-none {
  font-size: 12px;
  color: var(--sl-text-secondary, #64748b);
}

.col-head-action,
.col-cell-action {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  white-space: nowrap;
}

.variables-inline-table :deep(.el-select__wrapper),
.variables-inline-table :deep(.el-input__wrapper) {
  font-size: 12px;
}

.variables-inline-table :deep(.el-select__selected-item),
.variables-inline-table :deep(.el-select__placeholder) {
  font-size: 12px;
}

/* 下拉菜单丰富信息展示：只显示 displayName 与类型徽章 */
.attr-option-layout {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}
.attr-opt-main {
  font-weight: 500;
  color: var(--sl-text-heading, #0f172a);
}
.attr-opt-meta {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  font-family: var(--sl-font-mono, monospace);
}
</style>
