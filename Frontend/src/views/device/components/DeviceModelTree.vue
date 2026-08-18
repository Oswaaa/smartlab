<template>
  <aside class="device-model-tree">
    <header class="tree-header">
      <div class="tree-heading">
        <span class="tree-heading-title">类别 / 模型</span>
        <span class="tree-heading-count">{{ visibleModelCount }} 个模型</span>
      </div>
      <button v-if="!readonly" class="btn-aliyun-cta" type="button" aria-label="新建根类别" @click="startCreateRoot">
        <el-icon><FolderAdd /></el-icon><span>新增类别</span>
      </button>
    </header>

    <div class="tree-search-row">
      <el-input
        :model-value="keyword"
        placeholder="搜索类别或模型..."
        clearable
        size="small"
        :prefix-icon="Search"
        @update:model-value="value => emit('update:keyword', value)"
      />
    </div>

    <el-scrollbar class="tree-body" v-loading="loading">
      <el-tree
        v-if="treeData.length"
        ref="treeRef"
        :data="treeData"
        node-key="id"
        :props="treeProps"
        :expand-on-click-node="false"
        :current-node-key="currentNodeKey"
        :default-expanded-keys="rootExpandedKeys"
        highlight-current
        :indent="14"
        class="category-model-tree"
        @node-click="handleNodeClick"
      >
        <template #default="{ node, data }">
          <form v-if="data.type === 'editor'" class="tree-inline-editor" @click.stop @submit.prevent="submitEditor">
            <span class="tree-expander-placeholder"></span>
            <el-icon class="t-icon category-icon"><FolderAdd /></el-icon>
            <el-input v-model="editor.categoryName" size="small" class="inline-editor-input" placeholder="类别名称" autofocus />
            <div class="inline-editor-actions">
              <button class="inline-editor-action confirm" type="submit" aria-label="保存"><el-icon><Check /></el-icon></button>
              <button class="inline-editor-action" type="button" aria-label="取消" @click="closeEditor"><el-icon><Close /></el-icon></button>
            </div>
          </form>

          <form v-else-if="isRenamingCategory(data)" class="tree-inline-editor" @click.stop @submit.prevent="submitEditor">
            <span class="tree-expander-placeholder"></span>
            <el-icon class="t-icon category-icon"><Folder /></el-icon>
            <el-input v-model="editor.categoryName" size="small" class="inline-editor-input" placeholder="类别名称" autofocus />
            <div class="inline-editor-actions">
              <button class="inline-editor-action confirm" type="submit" aria-label="保存"><el-icon><Check /></el-icon></button>
              <button class="inline-editor-action" type="button" aria-label="取消" @click="closeEditor"><el-icon><Close /></el-icon></button>
            </div>
          </form>

          <div v-else class="t-row" :class="[data.type, { active: isNodeActive(data) }]">
            <div class="t-row-left">
              <button
                v-if="data.type === 'category' && hasVisibleChildren(data)"
                class="tree-expander"
                type="button"
                :aria-label="node.expanded ? '收起' : '展开'"
                @click.stop="toggleNode(node)"
              >
                <el-icon :class="{ expanded: node.expanded }"><CaretRight /></el-icon>
              </button>
              <span v-else class="tree-expander-placeholder"></span>

              <el-icon v-if="data.type === 'category'" class="t-icon category-icon">
                <FolderOpened v-if="node.expanded && hasVisibleChildren(data)" />
                <Folder v-else />
              </el-icon>
              <el-icon v-else class="t-icon model-icon"><Document /></el-icon>

              <span class="t-label" :title="nodeTitle(data)">{{ data.label }}</span>
            </div>

            <div class="t-row-right">
              <div v-if="data.type === 'category' && !data.readonly && !readonly" class="node-actions" @click.stop>
                <el-tooltip content="新增子类别" placement="top">
                  <button class="node-action" type="button" aria-label="新增子类别" @click.stop="startCreateChild(data)"><el-icon><FolderAdd /></el-icon></button>
                </el-tooltip>
                <el-tooltip v-if="data.canCreateModel && canCreateModel" content="为该类别新建模型" placement="top">
                  <button class="node-action model-action" type="button" aria-label="为该类别新建模型" @click.stop="emit('create-model', data)"><el-icon><DocumentAdd /></el-icon></button>
                </el-tooltip>
                <el-tooltip :content="data.canRename ? '重命名类别' : '已有子类别或模型的分类禁止直接重命名'" placement="top">
                  <button class="node-action" type="button" :disabled="!data.canRename" aria-label="重命名类别" @click.stop="startRename(data)"><el-icon><EditPen /></el-icon></button>
                </el-tooltip>
                <el-tooltip :content="data.canDelete ? '删除类别' : '仅无模型且无子类别的空分类可删除'" placement="top">
                  <button class="node-action danger" type="button" :disabled="!data.canDelete" aria-label="删除类别" @click.stop="emit('delete-category', data)"><el-icon><Delete /></el-icon></button>
                </el-tooltip>
              </div>
              <span v-if="data.type === 'category' && categoryMeta(data)" class="t-badge">{{ categoryMeta(data) }}</span>
              <span v-else-if="data.type === 'model' && data.meta" class="t-badge">{{ data.meta }}</span>
            </div>
          </div>
        </template>
      </el-tree>
      <div v-else class="tree-empty">暂无类别或模型</div>
    </el-scrollbar>
  </aside>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CaretRight, Check, Close, Delete, Document, DocumentAdd, EditPen, Folder, FolderAdd, FolderOpened, Search, List } from '@element-plus/icons-vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  models: { type: Array, default: () => [] },
  selectedModelId: { type: [String, Number], default: '' },
  selectedCategoryId: { type: [String, Number], default: '' },
  keyword: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  canCreateModel: { type: Boolean, default: false },
  readonly: { type: Boolean, default: false }
})

const emit = defineEmits([
  'update:keyword',
  'select-category',
  'select-model',
  'save-category',
  'migrate-category',
  'delete-category',
  'create-model'
])

const treeRef = ref(null)
const treeProps = { children: 'children', label: 'label' }
const editor = reactive({
  visible: false,
  mode: 'create',
  categoryId: null,
  parentCategoryId: null,
  parentLabel: '',
  categoryName: '',
  description: ''
})

const normalizedKeyword = computed(() => String(props.keyword || '').trim().toLowerCase())
const currentNodeKey = computed(() => {
  if (props.selectedModelId) return `model:${props.selectedModelId}`
  if (props.selectedCategoryId) return `category:${props.selectedCategoryId}`
  return ''
})
const rootExpandedKeys = computed(() => rootExpandableCategoryKeys(treeData.value))

const modelsByCategory = computed(() => {
  const map = new Map()
  props.models.forEach(model => {
    const categoryId = model.categoryId == null ? '' : String(model.categoryId)
    if (!map.has(categoryId)) map.set(categoryId, [])
    map.get(categoryId).push(model)
  })
  return map
})

const categoriesByParent = computed(() => {
  const map = new Map()
  props.categories.forEach(category => {
    const parentId = category.parentCategoryId == null ? '' : String(category.parentCategoryId)
    if (!map.has(parentId)) map.set(parentId, [])
    map.get(parentId).push(category)
  })
  return map
})

const treeData = computed(() => {
  const roots = asArray(categoriesByParent.value.get('')).map(category => buildCategoryNode(category, false)).filter(Boolean)
  if (editor.visible && editor.mode === 'create' && editor.parentCategoryId == null) roots.unshift(editorNode('root'))
  const uncategorizedModels = asArray(modelsByCategory.value.get('')).filter(model => modelMatches(model)).map(modelNode)
  if (uncategorizedModels.length) {
    roots.push({
      id: 'category:uncategorized',
      type: 'category',
      label: '未分类',
      categoryId: '',
      description: '',
      directModelCount: uncategorizedModels.length,
      totalModelCount: uncategorizedModels.length,
      hasCategoryChildren: false,
      canCreateModel: false,
      canRename: false,
      canDelete: false,
      readonly: true,
      children: uncategorizedModels
    })
  }
  if (roots.length) return roots
  return props.models.filter(modelMatches).map(modelNode)
})

const visibleModelCount = computed(() => countModels(treeData.value))

watch([treeData, normalizedKeyword], syncExpandedNodes, { immediate: true })

function syncExpandedNodes() {
  nextTick(() => {
    const tree = treeRef.value
    if (!tree) return
    const expanded = new Set(normalizedKeyword.value ? collectExpandableCategoryKeys(treeData.value) : rootExpandableCategoryKeys(treeData.value))
    if (editor.visible && editor.mode === 'create' && editor.parentCategoryId != null) expanded.add(`category:${editor.parentCategoryId}`)
    collectExpandableCategoryKeys(treeData.value).forEach(key => {
      const node = tree.getNode(key)
      if (node) node.expanded = expanded.has(key)
    })
  })
}

function buildCategoryNode(category, forceInclude) {
  const id = category.id == null ? '' : String(category.id)
  const directModels = asArray(modelsByCategory.value.get(id))
  const childCategories = asArray(categoriesByParent.value.get(id))
  const categorySelfMatched = categoryMatches(category)
  const includeAll = forceInclude || categorySelfMatched
  const categoryChildren = childCategories.map(child => buildCategoryNode(child, includeAll)).filter(Boolean)
  const modelChildren = directModels.filter(model => includeAll || modelMatches(model)).map(modelNode)
  const children = [...categoryChildren, ...modelChildren]
  if (editor.visible && editor.mode === 'create' && String(editor.parentCategoryId ?? '') === id) children.unshift(editorNode(id))
  const hasCategoryChildren = childCategories.length > 0
  const totalModelCount = directModels.length + categoryChildren.reduce((sum, child) => sum + countCategoryModels(child), 0)

  if (normalizedKeyword.value && !includeAll && children.length === 0) return null

  return {
    id: `category:${id}`,
    type: 'category',
    label: category.categoryName || '未命名类别',
    categoryId: id,
    parentCategoryId: category.parentCategoryId ?? null,
    description: category.description || '',
    directModelCount: directModels.length,
    directModels: directModels.map(modelNode),
    totalModelCount,
    hasCategoryChildren,
    canCreateModel: !hasCategoryChildren,
    canRename: !hasCategoryChildren && directModels.length === 0,
    canDelete: !hasCategoryChildren && directModels.length === 0,
    children
  }
}

function countCategoryModels(category) {
  const id = category.id == null ? '' : String(category.id)
  return asArray(modelsByCategory.value.get(id)).length + asArray(categoriesByParent.value.get(id)).reduce((sum, child) => sum + countCategoryModels(child), 0)
}

function editorNode(parentKey) {
  return {
    id: `editor:${editor.mode}:${parentKey || 'root'}`,
    type: 'editor',
    label: editor.mode === 'rename' ? '重命名类别' : '新增类别'
  }
}

function modelNode(model) {
  return {
    id: `model:${model.modelId}`,
    type: 'model',
    label: model.modelName || '未命名模型',
    meta: summarizeModel(model),
    modelId: model.modelId
  }
}

function handleNodeClick(data, node) {
  if (data.type === 'editor' || isRenamingCategory(data)) return
  if (data.type === 'category') {
    if (!data.readonly) emit('select-category', data)
    toggleNode(node)
    return
  }
  emit('select-model', data.modelId)
}

function isNodeActive(data) {
  if (data.type === 'model') return String(data.modelId) === String(props.selectedModelId)
  if (data.type === 'category') return String(data.categoryId) === String(props.selectedCategoryId)
  return false
}

function toggleNode(node) {
  if (!node || !hasVisibleChildren(node.data)) return
  node.expanded = !node.expanded
}

function hasVisibleChildren(data) {
  return asArray(data?.children).some(item => item.type !== 'editor')
}

function isRenamingCategory(data) {
  return editor.visible && editor.mode === 'rename' && data?.type === 'category' && String(data.categoryId) === String(editor.categoryId)
}

function startCreateRoot() {
  openEditor({ mode: 'create', parentCategoryId: null, parentLabel: '' })
}

function startCreateChild(data) {
  if (data.directModelCount > 0) {
    emit('migrate-category', data)
    return
  }
  openEditor({ mode: 'create', parentCategoryId: Number(data.categoryId), parentLabel: data.label })
}

function startRename(data) {
  openEditor({
    mode: 'rename',
    categoryId: Number(data.categoryId),
    parentCategoryId: data.parentCategoryId,
    parentLabel: '',
    categoryName: data.label,
    description: data.description || ''
  })
}

function openEditor(payload) {
  editor.visible = true
  editor.mode = payload.mode || 'create'
  editor.categoryId = payload.categoryId ?? null
  editor.parentCategoryId = payload.parentCategoryId ?? null
  editor.parentLabel = payload.parentLabel || ''
  editor.categoryName = payload.categoryName || ''
  editor.description = payload.description || ''
  nextTick(() => {
    const tree = treeRef.value
    if (editor.mode === 'create' && editor.parentCategoryId != null) {
      const node = tree?.getNode(`category:${editor.parentCategoryId}`)
      if (node) node.expanded = true
    }
  })
}

function closeEditor() {
  editor.visible = false
  editor.categoryId = null
  editor.parentCategoryId = null
  editor.parentLabel = ''
  editor.categoryName = ''
  editor.description = ''
}

function submitEditor() {
  const categoryName = editor.categoryName.trim()
  if (!categoryName) {
    ElMessage.warning('请输入类别名称')
    return
  }
  emit('save-category', {
    categoryId: editor.categoryId,
    parentCategoryId: editor.parentCategoryId,
    categoryName,
    description: editor.description.trim()
  })
  closeEditor()
}

function categoryMeta(data) {
  const count = Number(data.totalModelCount || 0)
  return count > 0 ? `(${count})` : ''
}

function nodeTitle(data) {
  if (data.type === 'model') return data.label
  return data.description ? `${data.label}：${data.description}` : data.label
}

function summarizeModel(model) {
  const parts = []
  if (asArray(model.attributes).length) parts.push('属性')
  if (asArray(model.capabilities).length) parts.push('操作')
  return parts.length ? parts.join('、') : '未配置'
}

function categoryMatches(category) {
  const keyword = normalizedKeyword.value
  if (!keyword) return true
  return `${category.categoryName || ''} ${category.description || ''}`.toLowerCase().includes(keyword)
}

function modelMatches(model) {
  const keyword = normalizedKeyword.value
  if (!keyword) return true
  return `${model.modelName || ''} ${model.categoryName || ''}`.toLowerCase().includes(keyword)
}

function collectExpandableCategoryKeys(nodes) {
  return nodes.flatMap(node => node.type === 'category' && hasVisibleChildren(node) ? [node.id, ...collectExpandableCategoryKeys(asArray(node.children))] : [])
}

function rootExpandableCategoryKeys(nodes) {
  return nodes.filter(node => node.type === 'category' && hasVisibleChildren(node)).map(node => node.id)
}

function countModels(nodes) {
  return nodes.reduce((sum, node) => sum + (node.type === 'model' ? 1 : countModels(asArray(node.children))), 0)
}

function asArray(value) {
  return Array.isArray(value) ? value : []
}
</script>

<style scoped>
.device-model-tree {
  width: 270px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid var(--sl-border-base);
  box-sizing: border-box;
  overflow: hidden;
  flex-shrink: 0;
}

.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base);
  flex-shrink: 0;
}

.tree-heading {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.tree-heading-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading);
}
.tree-heading-count {
  font-size: 11px;
  color: var(--sl-text-secondary);
}

.tree-search-row {
  padding: 6px 10px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-subtle);
  flex-shrink: 0;
}

.tree-body {
  flex: 1;
  min-height: 0;
  padding: 4px 6px;
}

.category-model-tree {
  background: transparent;
}

:deep(.el-tree-node__content) {
  height: 27px;
  padding-left: 0 !important;
  border-radius: 4px;
}
:deep(.el-tree-node__expand-icon) {
  display: none;
}

/* 树状层级细虚线引导系统 */
:deep(.el-tree-node__children) {
  position: relative;
  margin-left: 10px;
  padding-left: 2px;
  border-left: 1px dashed #e2e8f0;
}

.t-row {
  width: 100%;
  height: 27px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
  border-radius: 4px;
  cursor: pointer;
  margin: 0;
  background: transparent;
  transition: all 0.15s ease;
  box-sizing: border-box;
}
.t-row:hover {
  background: #f1f5f9;
}

.t-row-left {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.tree-expander {
  display: inline-flex;
  width: 16px;
  height: 16px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  font-size: 10px;
  flex-shrink: 0;
}
.tree-expander .el-icon {
  transition: transform 0.14s ease;
}
.tree-expander .el-icon.expanded {
  transform: rotate(90deg);
}
.tree-expander-placeholder {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.t-icon {
  font-size: 13.5px;
  color: var(--sl-text-secondary);
  flex-shrink: 0;
}
.category-icon { color: #64748b; }
.model-icon { color: #2563eb; }

.t-label {
  font-size: 12.5px;
  color: var(--sl-text-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.t-row.category .t-label {
  font-weight: 600;
  color: #1e293b;
}
.t-row.model .t-label {
  font-weight: 500;
  color: #334155;
}

.t-row-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.t-badge {
  font-size: 10.5px;
  padding: 0 5px;
  border-radius: 8px;
  background: #f1f5f9;
  color: var(--sl-text-secondary);
  font-family: var(--sl-font-mono);
  font-weight: 400;
}

/* 激活高亮状态 (方案三：柔和微圆角浅蓝药丸高亮) */
.t-row.active {
  background: #eff6ff !important;
  font-weight: 600;
}
.t-row.active .t-label {
  color: #2563eb !important;
}
.t-row.active .t-icon {
  color: #2563eb !important;
}
.t-row.active .t-badge {
  background: #dbeafe;
  color: #1d4ed8;
}

.node-actions {
  display: none;
  align-items: center;
  gap: 2px;
}
.t-row:hover .node-actions {
  display: inline-flex;
}

.node-action {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--sl-border-input);
  background: #ffffff;
  border-radius: 3px;
  cursor: pointer;
  color: var(--sl-text-secondary);
  font-size: 11px;
  transition: var(--sl-ease-smooth);
  padding: 0;
}
.node-action:hover {
  border-color: var(--sl-primary);
  color: var(--sl-primary);
  background: #eff6ff;
}
.node-action.model-action {
  color: var(--sl-success);
}
.node-action.model-action:hover {
  border-color: var(--sl-success);
  color: var(--sl-success);
  background: var(--sl-success-light);
}
.node-action.danger:hover {
  border-color: var(--sl-danger);
  color: var(--sl-danger);
  background: #fef2f2;
}
.node-action:disabled,
.node-action[disabled] {
  opacity: 0.35 !important;
  cursor: not-allowed !important;
  pointer-events: none !important;
  border-color: #e2e8f0 !important;
  color: #94a3b8 !important;
  background: transparent !important;
  transform: none !important;
  transition: none !important;
  box-shadow: none !important;
  animation: none !important;
}

/* 内联编辑器 */
.tree-inline-editor {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 1px 4px;
}
.inline-editor-input {
  flex: 1;
}
.inline-editor-actions {
  display: inline-flex;
  gap: 2px;
}
.inline-editor-action {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--sl-border-input);
  background: #ffffff;
  border-radius: 3px;
  cursor: pointer;
  font-size: 11px;
}
.inline-editor-action.confirm {
  color: var(--sl-success);
  border-color: var(--sl-success);
}
.inline-editor-action.confirm:hover {
  background: var(--sl-success-light);
}

.tree-empty {
  padding: 24px;
  text-align: center;
  font-size: 12px;
  color: var(--sl-text-disabled);
}
</style>
