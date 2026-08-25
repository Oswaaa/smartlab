<template>
  <section class="data-ports-panel">
    <section class="ports-config-card">
      <div class="panel-section-heading">
        <div class="heading-text">
          <h4>数据端口映射</h4>
          <p>端口读写绑定的内部变量，紫色连接用于节点间传递数据。</p>
        </div>
        <div class="heading-actions">
          <span class="param-count-badge">{{ ports.length }} 项端口</span>
          <button v-if="!readonly" type="button" class="btn-aliyun-cta" @click="addPort">
            + 新增端口
          </button>
        </div>
      </div>

      <div v-if="ports.length" class="ports-inline-table">
        <!-- 极简表头 -->
        <div class="inline-table-head">
          <span class="col-head-name">端口名称</span>
          <span class="col-head-dir">传输方向</span>
          <span class="col-head-binding">绑定内部变量</span>
          <span class="col-head-action">操作</span>
        </div>

        <!-- 数据端口行 (单行 36px 紧凑排布，使用稳定 index 作为 key 防止重绘丢失焦点) -->
        <div v-for="(row, index) in ports" :key="index" class="inline-table-row">
          <!-- 1. 端口名称 -->
          <div class="col-cell-name">
            <el-input
              :model-value="row.name"
              placeholder="端口名"
              :disabled="readonly || isSystemItem(row)"
              @update:model-value="updatePort(row, 'name', $event)"
            />
          </div>

          <!-- 2. 传输方向 -->
          <div class="col-cell-dir">
            <el-select
              :model-value="row.direction"
              :disabled="readonly || isSystemItem(row)"
              @update:model-value="updatePort(row, 'direction', $event)"
            >
              <el-option label="输出" value="OUT" />
              <el-option label="输入" value="IN" />
            </el-select>
          </div>

          <!-- 3. 绑定内部变量 -->
          <div class="col-cell-binding">
            <el-select
              :model-value="row.internalVariableName"
              clearable
              filterable
              :disabled="readonly || isSystemItem(row)"
              placeholder="选择绑定的节点内部变量"
              @update:model-value="updatePort(row, 'internalVariableName', $event)"
            >
              <el-option
                v-for="variable in node.internalVariables || []"
                :key="variable.name"
                :label="portOptionLabel(variable)"
                :value="variable.name"
              >
                <div class="port-option-layout">
                  <span class="port-opt-main">{{ variable.name }}</span>
                  <span class="port-opt-type-badge">{{ variable.dataType }}</span>
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
              @click="emit('remove-port-request', row.name)"
            >
              删除
            </button>
          </div>
        </div>
      </div>

      <WorkflowConfigurationEmpty
        v-else
        title="尚未配置数据端口"
        description="数据端口用于在节点之间传递内部变量的当前值。"
        :action-label="readonly ? '' : '新增端口'"
        @action="addPort"
      />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WorkflowConfigurationEmpty from './WorkflowConfigurationEmpty.vue'
import { isSystemItem } from '../../../../../utils/workflowNodeDefinition.js'

type Item = Record<string, any>
const props = withDefaults(defineProps<{ node: Item, readonly?: boolean }>(), { readonly: false })
const emit = defineEmits<{ 'update:node': [node: Item], 'remove-port-request': [portName: string] }>()
const ports = computed(() => props.node.ports || [])

function publish(nextPorts: Item[]) {
  if (!props.readonly) emit('update:node', { ...props.node, ports: nextPorts })
}
function uniqueName() {
  let index = 1
  while (ports.value.some((item: Item) => item.name === `port${index}`)) index += 1
  return `port${index}`
}
function addPort() {
  publish([
    ...ports.value,
    { name: uniqueName(), direction: 'IN', internalVariableName: props.node.internalVariables?.[0]?.name || '' }
  ])
}
function updatePort(target: Item, field: string, value: unknown) {
  publish(ports.value.map((item: Item) => item === target ? { ...item, [field]: value } : item))
}
function portOptionLabel(variable: Item) {
  return `${variable.name} · ${variable.dataType || '未知'}`
}
</script>

<style scoped>
.data-ports-panel {
  display: grid;
  gap: 12px;
  padding: 0;
  background: transparent;
}
.data-ports-panel :deep(.el-select),
.data-ports-panel :deep(.el-input) {
  width: 100%;
}

/* 一体化卡片容器：对齐业务配置卡片规范 */
.ports-config-card {
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
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9);
  background: #ffffff;
}

.heading-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.panel-section-heading h4 {
  margin: 0;
  color: var(--sl-text-heading, #0f172a);
  font-size: 13px;
  font-weight: 700;
}

.panel-section-heading p {
  margin: 0;
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
  line-height: 16px;
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

/* 极简单行网格 */
.ports-inline-table {
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
  flex: 1.2;
  min-width: 120px;
}

.col-head-dir,
.col-cell-dir {
  width: 90px;
  flex: none;
}

.col-head-binding,
.col-cell-binding {
  flex: 1.6;
  min-width: 140px;
}

.col-head-action,
.col-cell-action {
  width: 44px;
  flex: none;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

/* 下拉菜单丰富信息展示：变量名与变量类型清晰辨识 */
.port-option-layout {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}
.port-opt-main {
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-mono, monospace);
}
.port-opt-type-badge {
  font-size: 10px;
  color: var(--sl-primary, #2563eb);
  background: var(--sl-primary-light, #eff6ff);
  border: 1px solid #bfdbfe;
  padding: 0 5px;
  border-radius: 3px;
  font-family: var(--sl-font-mono, monospace);
  font-weight: 600;
  line-height: 16px;
}
</style>
