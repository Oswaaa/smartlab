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

      <div v-if="variables.length" class="variables-inline-table">
        <!-- 极简表头 -->
        <div class="inline-table-head">
          <span class="col-head-name">变量名称</span>
          <span class="col-head-type">数据类型</span>
          <span v-if="node.nodeType === 'DEV_NODE'" class="col-head-mapping">设备属性映射</span>
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
              :disabled="readonly || isSystemItem(row)"
              @update:model-value="updateVariable(row, 'dataType', $event)"
            >
              <el-option v-for="type in dataTypes" :key="type" :label="type" :value="type" />
            </el-select>
          </div>

          <!-- 3. 设备属性映射 (DEV_NODE 专属，精简回显，只显示 displayName 和 数据类型) -->
          <div v-if="node.nodeType === 'DEV_NODE'" class="col-cell-mapping">
            <el-select
              :model-value="row.attributesMapping"
              clearable
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
          </div>

          <!-- 4. 操作 -->
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
import { isSystemItem, removeVariable } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, readonly?: boolean, deviceAttributes?: Item[] }>(), { readonly: false, deviceAttributes: () => [] })
const emit = defineEmits<{ 'update:node': [node: Item] }>()
const dataTypes = ['INTEGER', 'DOUBLE', 'STRING', 'BOOLEAN']
const message = ref('')
const variables = computed(() => props.node.internalVariables || [])

function publish(internalVariables: Item[]) { if (props.readonly) return; message.value = ''; emit('update:node', { ...props.node, internalVariables }) }
function uniqueName() { let index = 1; while (variables.value.some((item: Item) => item.name === `variable${index}`)) index += 1; return `variable${index}` }
function addVariable() { publish([...variables.value, { name: uniqueName(), dataType: 'STRING' }]) }
function updateVariable(target: Item, field: string, value: unknown) {
  publish(variables.value.map((item: Item) => item === target ? { ...item, [field]: value } : item))
}
function mapAttribute(target: Item, attributeName: string) {
  publish(variables.value.map((item: Item) => item === target ? { ...item, attributesMapping: attributeName } : item))
}
function attributeOptionLabel(attribute: Item) {
  const name = attribute.displayName || attribute.attributeName || '未命名属性'
  const type = attribute.dataType ? ` · ${attribute.dataType}` : ''
  return `${name}${type}`
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

/* 极简单行网格 (方案一) */
.variables-inline-table {
  display: flex;
  flex-direction: column;
}

.inline-table-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: var(--sl-bg-hover, #f8fafc);
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-text-secondary, #64748b);
}

.inline-table-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
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
.col-cell-name {
  flex: 1.4;
  min-width: 140px;
}

.col-head-type,
.col-cell-type {
  flex: 1;
  min-width: 110px;
}

.col-head-mapping,
.col-cell-mapping {
  flex: 1.6;
  min-width: 150px;
}

.col-head-action,
.col-cell-action {
  width: 44px;
  flex: none;
  display: flex;
  justify-content: flex-end;
  align-items: center;
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
