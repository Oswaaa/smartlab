<template>
  <aside class="device-model-tree">
    <header class="tree-header">
      <div class="tree-heading">
        <strong>类别 / 模型</strong>
        <span>{{ visibleModelCount }} 个模型</span>
      </div>
      <button v-if="!readonly" class="tree-toolbar-action" type="button" aria-label="新建根类别" @click="startCreateRoot">
        <el-icon><FolderAdd /></el-icon><span>新增类别</span>
      </button>
      <button v-else class="tree-toolbar-action" type="button" @click="emit('select-category', { categoryId: '' })">
        <el-icon><List /></el-icon><span>全部实例</span>
      </button>
    </header>

    <div class="tree-search-row">
      <el-input
        :model-value="keyword"
        placeholder="搜索类别或模型"
        clearable
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
        class="category-model-tree"
        @node-click="handleNodeClick"
      >
        <template #default="{ node, data }">
          <form v-if="data.type === 'editor'" class="tree-inline-editor" @click.stop @submit.prevent="submitEditor">
            <span class="tree-expander-placeholder"></span>
            <el-icon class="node-icon category-icon"><FolderAdd /></el-icon>
            <el-input v-model="editor.categoryName" size="small" class="inline-editor-input" placeholder="类别名称" autofocus />
            <div class="inline-editor-actions">
              <button class="inline-editor-action confirm" type="submit" aria-label="保存"><el-icon><Check /></el-icon></button>
              <button class="inline-editor-action" type="button" aria-label="取消" @click="closeEditor"><el-icon><Close /></el-icon></button>
            </div>
          </form>

          <form v-else-if="isRenamingCategory(data)" class="tree-inline-editor" @click.stop @submit.prevent="submitEditor">
            <span class="tree-expander-placeholder"></span>
            <el-icon class="node-icon category-icon"><Folder /></el-icon>
            <el-input v-model="editor.categoryName" size="small" class="inline-editor-input" placeholder="类别名称" autofocus />
            <div class="inline-editor-actions">
              <button class="inline-editor-action confirm" type="submit" aria-label="保存"><el-icon><Check /></el-icon></button>
              <button class="inline-editor-action" type="button" aria-label="取消" @click="closeEditor"><el-icon><Close /></el-icon></button>
            </div>
          </form>

          <div v-else class="tree-node" :class="[data.type, { active: isNodeActive(data) }]">
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

            <el-icon v-if="data.type === 'category'" class="node-icon category-icon">
              <FolderOpened v-if="node.expanded && hasVisibleChildren(data)" />
              <Folder v-else />
            </el-icon>
            <el-icon v-else class="node-icon model-icon"><Document /></el-icon>

            <div class="node-text" :title="nodeTitle(data)">
              <span class="node-label">{{ data.label }}</span>
              <span v-if="data.type === 'category' && categoryMeta(data)" class="node-meta">{{ categoryMeta(data) }}</span>
              <span v-else-if="data.type === 'model'" class="node-meta">{{ data.meta }}</span>
            </div>

            <div v-if="data.type === 'category' && !data.readonly && !readonly" class="node-actions" @click.stop>
              <button class="node-action" type="button" aria-label="新增子类别" @click="startCreateChild(data)"><el-icon><FolderAdd /></el-icon></button>
              <button v-if="data.canCreateModel && canCreateModel" class="node-action model-action" type="button" aria-label="新增模型" @click="emit('create-model', data)"><el-icon><DocumentAdd /></el-icon></button>
              <button class="node-action" type="button" aria-label="重命名类别" @click="startRename(data)"><el-icon><EditPen /></el-icon></button>
              <button class="node-action danger" type="button" :disabled="!data.canDelete" :aria-label="data.canDelete ? '删除类别' : '仅空叶子类别可删除'" @click="emit('delete-category', data)"><el-icon><Delete /></el-icon></button>
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
      canDelete: false,
      readonly: true,
      children: uncategorizedModels
    })
  }
  if (roots.length) return roots
  return props.models.filter(modelMatches).map(modelNode)
})

const visibleModelCount = computed(() => countModels(treeData.value))
const editorTitle = computed(() => {
  if (editor.mode === 'rename') return '重命名类别'
  return editor.parentLabel ? `新增到：${editor.parentLabel}` : '新增根类别'
})

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
  display: flex;
  min-height: 0;
  flex-direction: column;
  background: #f8fafc;
  border-right: 1px solid #ccd6e3;
}

.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 10px 8px;
  background: #fff;
  border-bottom: 1px solid #dfe6ef;
}

.tree-toolbar-action {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #bfdbfe;
  border-radius: 5px;
  background: #eff6ff;
  color: #1d4ed8;
  cursor: pointer;
  font-size: 16px;
}

.tree-toolbar-action:hover {
  background: #dbeafe;
  border-color: #60a5fa;
}

.tree-heading {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.tree-heading strong {
  color: #0f172a;
  font-size: 15px;
  line-height: 1.2;
}

.tree-heading span,
.node-meta {
  color: #64748b;
  font-size: 12px;
  line-height: 1.3;
}

.tree-search-row {
  padding: 8px 10px;
  background: #fff;
  border-bottom: 1px solid #e5eaf1;
}

.tree-body {
  flex: 1;
  min-height: 0;
  padding: 6px 0;
}

.category-model-tree {
  --tree-row-height: 34px;
  background: transparent;
}

.category-model-tree :deep(.el-tree-node__content) {
  height: var(--tree-row-height);
  min-height: var(--tree-row-height);
  padding-right: 6px;
  border-bottom: 1px solid #e5ebf3;
  position: relative;
}

.category-model-tree :deep(.el-tree-node__content:has(.tree-inline-editor)) {
  height: 40px;
  min-height: 40px;
}

.category-model-tree :deep(.el-tree-node__expand-icon) {
  display: none;
}

.category-model-tree :deep(.el-tree-node__content:hover) {
  background: #eef6ff;
}

.category-model-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #dbeafe;
}

.tree-node,
.tree-inline-editor {
  position: relative;
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: 18px 22px minmax(0, 1fr) auto;
  align-items: center;
  gap: 5px;
  color: #334155;
}

.tree-node.model.active {
  color: #0f3f91;
  font-weight: 700;
}

.tree-inline-editor {
  padding-right: 2px;
}

.inline-editor-input :deep(.el-input__wrapper) {
  min-height: 28px;
  box-shadow: 0 0 0 1px #93c5fd inset;
}

.inline-editor-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.inline-editor-action {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  font-size: 14px;
}

.inline-editor-action.confirm {
  color: #047857;
  border-color: #a7f3d0;
}

.inline-editor-action:hover {
  background: #f8fafc;
}


.tree-expander {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.tree-expander .el-icon {
  transition: transform 0.14s ease;
}

.tree-expander .el-icon.expanded {
  transform: rotate(90deg);
}

.tree-expander-placeholder {
  width: 18px;
  height: 18px;
}

.node-icon {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.category-icon {
  color: #64748b;
}

.model-icon {
  color: #2563eb;
}

.node-text {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.node-label {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  font-size: 13px;
  font-weight: 650;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node.model .node-label {
  font-weight: 600;
}

.node-actions {
  position: absolute;
  top: 50%;
  right: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 3px;
  padding-left: 18px;
  background: linear-gradient(90deg, rgba(238, 246, 255, 0), #eef6ff 22px, #eef6ff 100%);
  opacity: 0;
  pointer-events: none;
  transform: translateY(-50%);
  transition: opacity 0.12s ease;
}

.category-model-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .node-actions {
  background: linear-gradient(90deg, rgba(219, 234, 254, 0), #dbeafe 22px, #dbeafe 100%);
}

.tree-node:hover .node-actions,
.category-model-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .node-actions {
  opacity: 1;
  pointer-events: auto;
}

.node-action {
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  background: #fff;
  color: #1d4ed8;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.node-action:hover {
  background: #eff6ff;
  border-color: #60a5fa;
}

.node-action.model-action {
  color: #047857;
  border-color: #a7f3d0;
}

.node-action.danger {
  color: #dc2626;
  border-color: #fecaca;
}

.node-action:disabled {
  color: #94a3b8;
  border-color: #e2e8f0;
  background: #f8fafc;
  cursor: not-allowed;
}

.tree-empty {
  margin: 8px 10px;
  padding: 12px;
  color: #64748b;
  font-size: 13px;
  text-align: center;
  border: 1px dashed #cbd5e1;
  background: #fff;
}
/* Resource-tree refinements */
.tree-toolbar-action {
  width: auto;
  min-width: 88px;
  gap: 6px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 750;
}
.tree-toolbar-action span { line-height: 1; }
.category-model-tree { --tree-row-height: 36px; }
.tree-node {
  grid-template-columns: 18px 22px minmax(0, 1fr) 96px;
  gap: 5px;
}
.tree-node.model {
  grid-template-columns: 18px 22px minmax(0, 1fr) 0;
}
.node-text { padding-right: 4px; }
.node-actions {
  position: static;
  justify-self: end;
  display: inline-flex;
  width: 96px;
  gap: 3px;
  padding-left: 0;
  background: transparent;
  opacity: 0;
  pointer-events: none;
  transform: none;
  visibility: hidden;
}
.tree-node:hover .node-actions,
.category-model-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .node-actions {
  opacity: 1;
  pointer-events: auto;
  visibility: visible;
}
.category-model-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .node-actions { background: transparent; }
.node-action {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  font-size: 13px;
}
.node-label { font-size: 13px; }
.node-meta { flex-shrink: 0; }


/* Unified asset navigator */
.model-tree-panel { background: #fff; border-color: #e5e7eb; }
.tree-header { padding: 8px 12px; border-color: #e5e7eb; }
.category-model-tree :deep(.el-tree-node__content) { min-height: 34px; border-radius: 4px; }
.category-model-tree :deep(.el-tree-node__content:hover) { background: #f7f8fa; }
.category-model-tree :deep(.el-tree-node.is-current > .el-tree-node__content) { background: #eaf3ff; box-shadow: inset 3px 0 0 #1677ff; }
.category-model-tree :deep(.el-tree-node.is-current > .el-tree-node__content)::before,
.category-model-tree :deep(.el-tree-node.is-current > .el-tree-node__content)::after { display: none; }
.tree-footer { border-color: #e5e7eb; background: #fafbfc; }
</style>
