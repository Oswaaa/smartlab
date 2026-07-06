const fs = require('fs');
const path = 'd:/SmartLab2.0/Frontend/src/views/device/DeviceModelManagement.vue';
let content = fs.readFileSync(path, 'utf-8');

// Edit 1: list-tools and tree
content = content.replace(
  /<div class="list-tools model-tree-tools">[\s\S]*?<el-button type="primary" plain @click="showAddCategoryDialog = true">设备类别<\/el-button>\s*<\/div>/,
  '<div class="list-tools model-tree-tools">\n          <el-input v-model="keyword" placeholder="搜索模型名称" clearable :prefix-icon="Search" @input="onKeywordInput" />\n          <el-button type="primary" plain :icon="Plus" @click="promptAddRootCategory">根类别</el-button>\n        </div>'
);

content = content.replace(
  /<template #default="\{ data \}">\s*<div class="category-model-node"[^>]*>\s*<span class="node-title">\{\{ data.label \}\}<\/span>\s*<span class="node-meta">\{\{ data.meta \}\}<\/span>\s*<\/div>\s*<\/template>/,
  \<template #default="{ data }">
              <div class="category-model-node" :class="[data.type, { active: data.type === 'model' && selectedModelId === data.modelId }]">
                <div class="node-main" style="display: flex; align-items: center; gap: 6px; overflow: hidden; flex: 1" :title="data.label">
                  <el-icon v-if="data.type === 'category'" style="color: #64748b;"><Folder /></el-icon>
                  <el-icon v-else style="color: var(--el-color-primary);"><Cpu /></el-icon>
                  <span class="node-title">{{ data.label }}</span>
                </div>
                <div class="node-actions" v-if="data.type === 'category'" style="display: flex; gap: 4px; flex-shrink: 0;" @click.stop>
                  <el-button link type="primary" size="small" @click.stop="promptAddSubCategory(data)" title="新增子类别"><el-icon><Plus /></el-icon></el-button>
                  <el-button link type="primary" size="small" @click.stop="promptRenameCategory(data)" title="重命名"><el-icon><EditPen /></el-icon></el-button>
                  <el-button link type="danger" size="small" @click.stop="deleteCategory(data)" :disabled="data.children && data.children.length > 0" title="删除"><el-icon><Delete /></el-icon></el-button>
                  <el-button v-if="!data.children || !data.children.some(c => c.type === 'category')" link type="success" size="small" title="新增模型" @click.stop="openCreateDrawerWithCategory(data)"><el-icon><Cpu /></el-icon></el-button>
                </div>
              </div>
            </template>\
);

// Edit 2: detail anchor link
content = content.replace(
  /<el-anchor-link href="#view-topology" title="结构拓扑" \/>/,
  '<el-anchor-link href="#view-bom" title="组件结构" />'
);

// Edit 3: detail view BOM
content = content.replace(
  /<div id="view-topology"[^>]*>[\s\S]*?<\/div>\s*<div id="view-adapter"/,
  \<div id="view-bom" class="anchor-section industrial-section">
                <h2 class="section-heading">组件结构</h2>
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

              <div id="view-adapter"\
);

// Edit 4: edit drawer anchor link
content = content.replace(
  /<el-anchor-link href="#edit-ability" title="属性功能" \/>/,
  '<el-anchor-link href="#edit-ability" title="属性功能" />\\n            <el-anchor-link href="#edit-bom" title="组件结构" />'
);

// Edit 5: Basic form categoryValue select
content = content.replace(
  /<el-form-item label="所属分类" prop="categoryValue">[\s\S]*?<\/el-form-item>/,
  \<el-form-item label="所属分类" prop="categoryValue">
                  <el-tree-select
                    v-model="draft.basic.categoryValue"
                    :data="categoryTreeForSelect"
                    node-key="id"
                    check-strictly
                    :render-after-expand="false"
                    placeholder="选择分类"
                    style="width: 100%"
                  />
                </el-form-item>\
);

// Edit 6: Insert #edit-bom
content = content.replace(
  /<\/div>\s*<div id="edit-adapter"/,
  \</div>

          <div id="edit-bom" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">组件结构清单</h2>
            <section class="drawer-section">
              <div class="section-title">
                <h3>BOM 清单</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addBomComponent">新增组件</el-button>
              </div>
              <div v-if="draft.componentsBom.length === 0" class="compact-empty block-empty">暂无组件，请点击右上角“新增组件”进行配置</div>
              <el-table v-else :data="draft.componentsBom" border size="small">
                <el-table-column label="组件名称 (slotName)" min-width="160">
                  <template #default="{ row }"><el-input v-model="row.slotName" size="small" placeholder="例如：搅拌电机" /></template>
                </el-table-column>
                <el-table-column label="设备类别" min-width="180">
                  <template #default="{ row }">
                    <el-tree-select
                      v-model="row.categoryId"
                      :data="categoryTreeForSelect"
                      node-key="id"
                      check-strictly
                      :render-after-expand="false"
                      size="small"
                      placeholder="选择类别"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="数量 (quantity)" width="120">
                  <template #default="{ row }"><el-input-number v-model="row.quantity" size="small" :min="1" style="width: 100%" /></template>
                </el-table-column>
                <el-table-column label="描述 (description)" min-width="200">
                  <template #default="{ row }"><el-input v-model="row.description" size="small" placeholder="说明" /></template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ \\ }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.componentsBom, \\)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </div>

          <div id="edit-adapter"\
);

// Edit 7: adapter deviceTemplates to deviceCategories dropdown
content = content.replace(
  /<el-select v-model="selectedRegisteredAdapterTemplate" filterable clearable placeholder="选择设备模板" :disabled="!registeredAdapterTemplateOptions.length">\s*<el-option\s*v-for="tpl in registeredAdapterTemplateOptions"\s*:key="tpl.templateName"\s*:label="tpl.description \? tpl.templateName \+ ' · ' \+ tpl.description : tpl.templateName"\s*:value="tpl.templateName"\s*\/>\s*<\/el-select>/,
  \<el-select v-model="selectedRegisteredAdapterTemplate" filterable clearable placeholder="选择设备类别" :disabled="!registeredAdapterTemplateOptions.length">
                      <el-option
                        v-for="tpl in registeredAdapterTemplateOptions"
                        :key="tpl.categoryName || tpl.templateName"
                        :label="tpl.categoryDescription ? (tpl.categoryName || tpl.templateName) + ' · ' + tpl.categoryDescription : (tpl.categoryName || tpl.templateName)"
                        :value="tpl.categoryName || tpl.templateName"
                      />
                    </el-select>\
);

content = content.replace(
  /<el-button type="primary" plain :disabled="!selectedRegisteredAdapterName || !selectedRegisteredAdapterTemplate" @click="applyRegisteredAdapterContract">载入契约<\/el-button>/,
  \<el-button type="primary" plain :disabled="!selectedRegisteredAdapterName || !selectedRegisteredAdapterTemplate" @click="applyRegisteredAdapterContract">载入契约</el-button>\
);

// Edit 8: Remove AddCategoryDialog
content = content.replace(
  /<el-dialog v-model="showAddCategoryDialog" title="设备类别管理" width="720px" class="category-manager-dialog">[\s\S]*?<\/el-dialog>/,
  ''
);

// Edit 9: Imports
content = content.replace(
  /import \{ Connection, Cpu, Delete, Download, EditPen, Lock, Notification, Plus, Refresh, Search, Unlock, Upload, Close, Right, Warning, InfoFilled \} from '@element-plus\/icons-vue'/,
  "import { Connection, Cpu, Delete, Download, EditPen, Lock, Notification, Plus, Refresh, Search, Unlock, Upload, Close, Right, Warning, InfoFilled, Folder } from '@element-plus/icons-vue'"
);

// Edit 10: registeredAdapterTemplateOptions computed and remove category dialog states
content = content.replace(
  /const registeredAdapterTemplateOptions = computed\(\(\) => \{\s*const adapter = registeredAdapters.value.find\(item => item.adapterName === selectedRegisteredAdapterName.value\)\s*return asArray\(parsedAdapterConfig\(adapter\).deviceTemplates\)\s*\}\)\s*const showAddCategoryDialog = ref\(false\)\s*const savingCategory = ref\(false\)\s*const newCategoryDraft = ref\(\{ categoryName: '', parentCategoryId: '', description: '' \}\)\s*const newCategoryFormRef = ref\(null\)/,
  \const registeredAdapterTemplateOptions = computed(() => {
  const adapter = registeredAdapters.value.find(item => item.adapterName === selectedRegisteredAdapterName.value)
  return asArray(parsedAdapterConfig(adapter).deviceCategories || parsedAdapterConfig(adapter).deviceTemplates)
})\
);

content = content.replace(
  /const newCategoryRules = \{\s*categoryName: \[\{ required: true, message: '请输入类别名称', trigger: 'blur' \}\]\s*\}/,
  ''
);

// Edit 11: Remove saveNewCategory
content = content.replace(
  /async function saveNewCategory\(\) \{[\s\S]*?finally \{\s*savingCategory.value = false\s*\}\s*\}\)\s*\}/,
  ''
);

// Edit 12: Add categoryTreeForSelect, prompt methods etc
content = content.replace(
  /const generatedInterfaces = computed\(\(\) => defaultInterfaces\(adapterSignalOptions.value\)\)/,
  \const generatedInterfaces = computed(() => defaultInterfaces(adapterSignalOptions.value))

const categoryTreeForSelect = computed(() => {
  const buildTree = (parentId) => {
    return categories.value
      .filter(c => String(c.parentCategoryId || '') === String(parentId || ''))
      .map(c => ({
        id: String(c.id),
        label: c.categoryName,
        children: buildTree(c.id)
      }))
      .map(node => (node.children.length === 0 ? { ...node, children: undefined } : node))
  }
  return buildTree('')
})

const promptAddRootCategory = () => {
  ElMessageBox.prompt('请输入根类别名称', '新增根类别', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).then(async ({ value }) => {
    if (!value || !value.trim()) return
    await saveCategoryApi(value.trim(), null)
  }).catch(() => {})
}

const promptAddSubCategory = async (data) => {
  const hasModels = data.children && data.children.some(c => c.type === 'model')
  if (hasModels) {
    try {
      await ElMessageBox.confirm('该类别下已有设备模型，添加子类别后该类别将变为中间节点，其下的模型需要迁移至具体的叶子类别。是否继续？', '警告', { type: 'warning', confirmButtonText: '继续', cancelButtonText: '取消' })
    } catch (e) {
      return
    }
  }
  ElMessageBox.prompt(\\\请输入【\】的子类别名称\\\, '新增子类别', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).then(async ({ value }) => {
    if (!value || !value.trim()) return
    await saveCategoryApi(value.trim(), Number(data.categoryId))
  }).catch(() => {})
}

const promptRenameCategory = (data) => {
  ElMessageBox.prompt('请输入新的类别名称', '重命名类别', {
    inputValue: data.label,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).then(async ({ value }) => {
    if (!value || !value.trim() || value.trim() === data.label) return
    await saveCategoryApi(value.trim(), categories.value.find(c => String(c.id) === String(data.categoryId))?.parentCategoryId, data.categoryId)
  }).catch(() => {})
}

const deleteCategory = async (data) => {
  try {
    await ElMessageBox.confirm(\\\确认删除类别【\】吗？\\\, '提示', { type: 'warning' })
    const res = await axios.delete('/api/device/category/delete/' + data.categoryId)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      await loadCategories()
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (e) { }
}

async function saveCategoryApi(categoryName, parentCategoryId, categoryId = null) {
  try {
    const payload = {
      categoryName,
      parentCategoryId,
      description: ''
    }
    if (categoryId) payload.id = categoryId
    const res = await axios.post('/api/device/category/save', payload)
    if (res.data.success) {
      ElMessage.success('保存成功')
      await loadCategories()
    } else {
      ElMessage.error(res.data.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存异常')
  }
}

function openCreateDrawerWithCategory(data) {
  replaceDraft(emptyDraft())
  draft.basic.categoryValue = String(data.categoryId)
  drawerMode.value = 'create'
  prepareAdapterSourcePicker()
  drawerVisible.value = true
}

function addBomComponent() {
  draft.componentsBom.push({ _key: makeUiKey('bom'), slotName: '', categoryId: '', quantity: 1, description: '' })
}
\
);

// Edit 13: update buildSavePayload componentsBom
content = content.replace(
  /componentsBom: asArray\(draft\.componentsBom\),/,
  \componentsBom: asArray(draft.componentsBom).map(item => ({
      slotName: stringValue(item.slotName),
      categoryId: item.categoryId ? Number(item.categoryId) : null,
      categoryName: categoryNameById(item.categoryId),
      quantity: Number(item.quantity) || 1,
      description: stringValue(item.description)
    })).filter(item => item.slotName),\
);

// Edit 14: openCreateDrawer logic (use replaceDraft first)
content = content.replace(
  /function openCreateDrawer\(\) \{\s*replaceDraft\(emptyDraft\(\)\)\s*drawerMode\.value = 'create'\s*prepareAdapterSourcePicker\(\)\s*drawerVisible\.value = true\s*\}/,
  \unction openCreateDrawer() {
  replaceDraft(emptyDraft())
  drawerMode.value = 'create'
  prepareAdapterSourcePicker()
  drawerVisible.value = true
}\
);

// Fix template dialog fields
content = content.replace(
  /const hasTemplate = registeredAdapterTemplateOptions.value.some\(tpl => tpl.templateName === selectedRegisteredAdapterTemplate.value\)/,
  \const hasTemplate = registeredAdapterTemplateOptions.value.some(tpl => (tpl.categoryName || tpl.templateName) === selectedRegisteredAdapterTemplate.value)\
);

content = content.replace(
  /params: \{ templateName: selectedRegisteredAdapterTemplate.value \}/,
  \params: { categoryName: selectedRegisteredAdapterTemplate.value }\
);

content = content.replace(
  /name: t.templateName,/,
  \
ame: t.categoryName || t.templateName,\
);

fs.writeFileSync(path, content, 'utf-8');
console.log('Update complete');
