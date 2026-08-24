<template>
  <div class="data-workbench-page">
    <div class="workbench-canvas">
      
      <!-- 1. 左侧资产树侧边栏 (Tree Sidebar Pane - 设备类别/数据模板双分区，紧凑左对齐) -->
      <aside class="tree-pane sl-asset-tree">
        <div class="tree-header-bar">
          <strong class="tree-header-title">数据中心资产树</strong>
          <button class="btn-aliyun-cta" type="button" @click="openDatasetDrawer(null, null)">+ 建表</button>
        </div>

        <div class="tree-search-bar">
          <input
            v-model="keyword"
            class="tree-search-input"
            placeholder="搜索类别、模型、设备或模板..."
          />
        </div>

        <div class="tree-list-scroll" v-loading="loading">
          <el-tree
            :data="filteredTree"
            node-key="key"
            default-expand-all
            :indent="16"
            :expand-on-click-node="false"
            @node-click="handleTreeClick"
          >
            <template #default="{ data }">
              <div
                class="t-row"
                :class="[
                  `node-type-${data.type}`,
                  {
                    active: activeKey === data.key,
                    'group-header-row': data.type === 'group-header'
                  }
                ]"
              >
                <span class="t-row-left" :title="data.label">
                  <!-- 1. 大标题：设备类别 / 数据模板 (无图标) -->
                  <template v-if="data.type === 'group-header'">
                    <span class="t-group-label">{{ data.label }}</span>
                  </template>
                  
                  <!-- 2. 具体设备类别：文件夹图标 -->
                  <el-icon v-else-if="data.type === 'category'" class="t-icon category-icon">
                    <Folder />
                  </el-icon>
                  
                  <!-- 3. 设备模型：文档图标 -->
                  <el-icon v-else-if="data.type === 'model'" class="t-icon model-icon">
                    <Document />
                  </el-icon>
                  
                  <!-- 4. 设备实例：设备/芯片图标 -->
                  <el-icon v-else-if="data.type === 'instance'" class="t-icon instance-icon">
                    <Cpu />
                  </el-icon>
                  
                  <!-- 5. 数据模板：文档图标 -->
                  <el-icon v-else-if="data.type === 'template'" class="t-icon template-icon">
                    <Document />
                  </el-icon>
                  
                  <!-- 6. 数据表：数据表图标 -->
                  <el-icon v-else class="t-icon dataset-icon">
                    <Tickets />
                  </el-icon>

                  <span v-if="data.type !== 'group-header'" class="t-label">{{ data.label }}</span>
                </span>

                <div class="t-row-right">
                  <div class="node-actions" @click.stop>
                    <el-tooltip v-if="data.type === 'model'" content="为该模型新建模板" placement="top">
                      <button class="node-action" type="button" aria-label="新建模板" @click.stop="openTemplateDrawerWithModel(data.data)">
                        <el-icon><DocumentAdd /></el-icon>
                      </button>
                    </el-tooltip>
                    <el-tooltip v-if="data.type === 'instance' && isUsableInstance(data.data)" content="为该设备建表" placement="top">
                      <button class="node-action" type="button" aria-label="新建数据表" @click.stop="openDatasetDrawer(null, data.data)">
                        <el-icon><Plus /></el-icon>
                      </button>
                    </el-tooltip>
                    <el-tooltip v-if="data.type === 'template' && templateHasModel(data.data)" content="用此模板建表" placement="top">
                      <button class="node-action" type="button" aria-label="用模板建表" @click.stop="openDatasetDrawer(data.data)">
                        <el-icon><Plus /></el-icon>
                      </button>
                    </el-tooltip>
                  </div>
                  <span v-if="data.count != null" class="t-badge">{{ data.count }}</span>
                </div>
              </div>
            </template>
          </el-tree>
          <el-empty v-if="!filteredTree.length" description="暂无数据资产" />
        </div>
      </aside>

      <!-- 2. 右侧一体化无界画卷 (Seamless Main Canvas - 锁屏高密度布局) -->
      <section class="main-canvas-pane">
        
        <!-- 2.1 视角一：数据表详情视角 (Dataset View - 核心走势与宽幅卡片表格) -->
        <template v-if="selectedDataset">
          <div class="metric-ribbon">
            <div class="m-cell">
              <span class="m-label">设备模型</span>
              <strong class="m-val">{{ modelName(selectedInstance?.deviceModelId) }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">绑定实例</span>
              <strong class="m-val">{{ instanceName(selectedDataset.deviceInstanceId) }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">数据模板</span>
              <strong class="m-val">{{ templateName(selectedDataset.dataTemplateId) }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">历史采样总量</span>
              <strong class="m-val highlight">{{ recordPage.total }} 条</strong>
            </div>
          </div>

          <div class="canvas-fixed-layout">
            <!-- 1. 遥测走势图 (满幅填满固定区) -->
            <div class="chart-panel-fixed">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>遥测趋势走势图</span>
                  <span class="status-dot-badge normal" style="margin-left: 8px;">
                    <span class="dot"></span> 实时采样 (1s)
                  </span>
                </div>
                <div style="display: flex; gap: 8px;">
                  <button class="btn-aliyun" type="button" @click="loadRecords(false)">刷新采样</button>
                  <button class="btn-primary-blue" type="button" @click="exportChartImage">导出图表图片</button>
                </div>
              </div>
              <div class="chart-section">
                <div ref="chartRef" class="chart-box"></div>
              </div>
            </div>

            <!-- 2. 历史遥测采样明细 (微边距大卡片，仅在卡片内部平滑滚动) -->
            <div class="table-panel-flex">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>历史遥测采样明细</span>
                  <span class="count-pill">{{ recordPage.total }} 条记录</span>
                </div>
                <div style="display: flex; align-items: center; gap: 8px;">
                  <button class="btn-aliyun" type="button" @click="loadRecords()">刷新数据</button>
                  <button class="btn-primary-blue" type="button" @click="exportDataset">导出 CSV</button>
                  <el-popconfirm title="确认删除该数据表？物理表会同步删除" @confirm="deleteDataset(selectedDataset)">
                    <template #reference>
                      <button class="btn-link danger" type="button" style="margin-left: 6px;">删除数据表</button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>

              <div class="table-scroll-container">
                <div class="table-card">
                  <el-table
                    :data="records"
                    border
                    stripe
                    size="small"
                    v-loading="loadingRecords"
                    height="100%"
                    class="unified-table-el"
                  >
                    <el-table-column label="Adapter 采集时间" width="180">
                      <template #default="{ row }">
                        <span class="time-stamp">{{ formatTime(recordTime(row)) }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="系统入库时间" width="180">
                      <template #default="{ row }">
                        <span class="time-stamp">{{ formatTime(recordIngestTime(row)) }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column v-for="field in valueFields" :key="field.columnName" :label="fieldLabel(field)" min-width="150">
                      <template #default="{ row }">
                        <span class="field-val">{{ valueOf(row, field.columnName) }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>

              <div class="pager-wrap">
                <el-pagination
                  background
                  size="small"
                  layout="total, sizes, prev, pager, next"
                  :total="recordPage.total"
                  :current-page="recordPage.pageNo"
                  :page-size="recordPage.pageSize"
                  :page-sizes="[50,100,200,500]"
                  @current-change="page => { recordPage.pageNo = page; loadRecords() }"
                  @size-change="size => { recordPage.pageNo = 1; recordPage.pageSize = size; loadRecords() }"
                />
              </div>
            </div>
          </div>
        </template>

        <!-- 2.2 视角二：数据模板详情视角 (Template View) -->
        <template v-else-if="selectedTemplate">
          <div class="metric-ribbon">
            <div class="m-cell">
              <span class="m-label">模板名称</span>
              <strong class="m-val">{{ selectedTemplate.templateName }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">来源模型</span>
              <strong class="m-val">{{ modelName(selectedTemplate.deviceModelId) }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">模板类型</span>
              <strong class="m-val">{{ selectedTemplate.isDefault ? '默认模板' : '自定义模板' }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">创建时间</span>
              <strong class="m-val time-stamp">{{ formatTime(selectedTemplate.createTime) }}</strong>
            </div>
          </div>

          <div class="canvas-fixed-layout">
            <div class="table-panel-flex">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>模板字段定义</span>
                  <span class="count-pill">{{ templateDetails.length }} 个字段</span>
                </div>
                <div style="display: flex; align-items: center; gap: 8px;">
                  <button v-if="templateHasModel(selectedTemplate)" class="btn-aliyun-cta" type="button" @click="openDatasetDrawer(selectedTemplate)">+ 用此模板建表</button>
                  <el-popconfirm v-if="!selectedTemplate.isDefault" title="确认删除该模板？" @confirm="deleteTemplate(selectedTemplate)">
                    <template #reference>
                      <button class="btn-link danger" type="button" style="margin-left: 6px;">删除模板</button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
              <div class="table-scroll-container">
                <div class="table-card">
                  <el-table :data="templateDetails" border stripe size="small" height="100%" class="unified-table-el">
                    <el-table-column prop="columnName" label="模板字段" min-width="160" />
                    <el-table-column prop="columnDesc" label="字段说明" min-width="160" />
                    <el-table-column label="绑定属性" min-width="150">
                      <template #default="{ row }">{{ bindingLabel(row) }}</template>
                    </el-table-column>
                    <el-table-column label="默认值" width="130">
                      <template #default="{ row }"><span class="field-val">{{ row.defaultValue || '-' }}</span></template>
                    </el-table-column>
                    <el-table-column label="字段用途" width="120">
                      <template #default="{ row }">
                        <span class="status-dot-badge info">{{ isUnitField(row) ? '单位' : '采集值' }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 2.3 视角三：设备实例视角 (Instance View) -->
        <template v-else-if="selectedInstance">
          <div class="metric-ribbon">
            <div class="m-cell">
              <span class="m-label">设备实例</span>
              <strong class="m-val">{{ selectedInstance.instanceName }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">所属模型</span>
              <strong class="m-val">{{ modelName(selectedInstance.deviceModelId) }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">绑定 Adapter</span>
              <strong class="m-val">{{ selectedInstance.boundAdapterName || '已配置' }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">资产生命周期</span>
              <strong class="m-val status-val" :class="instanceLifecycleClass(selectedInstance)">
                <span class="dot"></span> {{ formatLifecycleStatus(selectedInstance.lifecycleStatus) }}
              </strong>
            </div>
          </div>

          <div class="canvas-fixed-layout">
            <div class="table-panel-flex">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>该设备已关联的数据表</span>
                  <span class="count-pill">{{ instanceDatasets(selectedInstance).length }} 张表</span>
                </div>
                <div style="display: flex; gap: 8px;">
                  <button v-if="isUsableInstance(selectedInstance)" class="btn-aliyun-cta" type="button" @click="openDatasetDrawer(null, selectedInstance)">+ 为该设备建表</button>
                </div>
              </div>
              <div class="table-scroll-container">
                <div v-if="instanceDatasets(selectedInstance).length" class="table-card">
                  <el-table :data="instanceDatasets(selectedInstance)" border stripe size="small" height="100%" class="unified-table-el">
                    <el-table-column prop="dataTable" label="数据表名" min-width="180" />
                    <el-table-column prop="dataDesc" label="数据表说明" min-width="200" />
                    <el-table-column label="依赖模板" min-width="160">
                      <template #default="{ row }">{{ templateName(row.dataTemplateId) }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="120">
                      <template #default="{ row }">
                        <button class="btn-link" type="button" @click="selectDataset(row)">查看详情</button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-empty v-else description="该设备实例下暂无数据表">
                  <button v-if="isUsableInstance(selectedInstance)" class="btn-aliyun-cta" type="button" @click="openDatasetDrawer(null, selectedInstance)">为该设备建表</button>
                </el-empty>
              </div>
            </div>
          </div>
        </template>

        <!-- 2.4 视角四：设备模型视角 (Model View) -->
        <template v-else-if="selectedModel">
          <div class="metric-ribbon">
            <div class="m-cell">
              <span class="m-label">设备模型</span>
              <strong class="m-val">{{ selectedModel.modelName }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">物理分类</span>
              <strong class="m-val">{{ categoryName(selectedModel.categoryId) }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">衍生实例数</span>
              <strong class="m-val highlight">{{ modelInstances(selectedModel).length }} 个</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">关联模板数</span>
              <strong class="m-val">{{ modelTemplates(selectedModel).length }} 个</strong>
            </div>
          </div>

          <div class="canvas-fixed-layout">
            <!-- 核心列表 1: 设备实例列表 -->
            <div class="table-panel-flex" style="flex: 1.1;">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>该模型下的设备实例列表</span>
                  <span class="count-pill">{{ modelInstances(selectedModel).length }} 个实例</span>
                </div>
              </div>
              <div class="table-scroll-container">
                <div v-if="modelInstances(selectedModel).length" class="table-card">
                  <el-table :data="modelInstances(selectedModel)" border stripe size="small" height="100%" class="unified-table-el">
                    <el-table-column prop="instanceName" label="设备实例名称" min-width="180" />
                    <el-table-column label="绑定 Adapter" min-width="150">
                      <template #default="{ row }">{{ row.boundAdapterName || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="设备点位" min-width="140">
                      <template #default="{ row }">{{ row.boundDevicePoint || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="已建数据表" width="120">
                      <template #default="{ row }"><span class="count-pill">{{ instanceDatasets(row).length }} 张表</span></template>
                    </el-table-column>
                    <el-table-column label="操作" width="160">
                      <template #default="{ row }">
                        <button class="btn-link" type="button" @click="selectInstance(row)">查看详情</button>
                        <button v-if="isUsableInstance(row)" class="btn-link" type="button" style="margin-left: 10px;" @click="openDatasetDrawer(null, row)">建表</button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-empty v-else description="该模型下暂无设备实例" />
              </div>
            </div>

            <!-- 核心列表 2: 数据模板列表 -->
            <div class="table-panel-flex" style="flex: 0.9; border-top: 1px solid var(--sl-border-base);">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>关联的数据模板</span>
                  <span class="count-pill">{{ modelTemplates(selectedModel).length }} 个模板</span>
                </div>
                <div style="display: flex; gap: 8px;">
                  <button class="btn-aliyun-cta" type="button" @click="openTemplateDrawerWithModel(selectedModel)">+ 为该模型新建模板</button>
                </div>
              </div>
              <div class="table-scroll-container">
                <div v-if="modelTemplates(selectedModel).length" class="table-card">
                  <el-table :data="modelTemplates(selectedModel)" border stripe size="small" height="100%" class="unified-table-el">
                    <el-table-column prop="templateName" label="模板名称" min-width="180" />
                    <el-table-column prop="templateDesc" label="模板说明" min-width="200" />
                    <el-table-column label="模板类型" width="120">
                      <template #default="{ row }">
                        <span class="status-dot-badge info">{{ row.isDefault ? '默认模板' : '自定义模板' }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="160">
                      <template #default="{ row }">
                        <button class="btn-link" type="button" @click="selectTemplate(row)">查看 Schema</button>
                        <button class="btn-link" type="button" style="margin-left: 10px;" @click="openDatasetDrawer(row)">建表</button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-empty v-else description="该设备模型下暂无数据模板">
                  <button class="btn-aliyun-cta" type="button" @click="openTemplateDrawerWithModel(selectedModel)">为该模型新建模板</button>
                </el-empty>
              </div>
            </div>
          </div>
        </template>

        <!-- 2.5 视角五：分类视角 (Category View) -->
        <template v-else-if="selectedCategory">
          <div class="metric-ribbon">
            <div class="m-cell">
              <span class="m-label">物理分类</span>
              <strong class="m-val">{{ selectedCategory.categoryName }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">包含模型数</span>
              <strong class="m-val">{{ categoryModels(selectedCategory).length }} 个</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">包含实例数</span>
              <strong class="m-val highlight">{{ categoryInstances(selectedCategory).length }} 个</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">关联数据表数</span>
              <strong class="m-val highlight">{{ categoryDatasets(selectedCategory).length }} 张</strong>
            </div>
          </div>

          <div class="canvas-fixed-layout">
            <div class="table-panel-flex">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>该分类下的设备模型</span>
                  <span class="count-pill">{{ categoryModels(selectedCategory).length }} 个模型</span>
                </div>
              </div>
              <div class="table-scroll-container">
                <div v-if="categoryModels(selectedCategory).length" class="table-card">
                  <el-table :data="categoryModels(selectedCategory)" border stripe size="small" height="100%" class="unified-table-el">
                    <el-table-column prop="modelName" label="模型名称" min-width="200" />
                    <el-table-column prop="modelCode" label="模型编码" min-width="160" />
                    <el-table-column label="关联模板数" width="120">
                      <template #default="{ row }">{{ modelTemplates(row).length }}</template>
                    </el-table-column>
                    <el-table-column label="衍生实例数" width="120">
                      <template #default="{ row }">{{ modelInstances(row).length }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="120">
                      <template #default="{ row }">
                        <button class="btn-link" type="button" @click="selectModel(row)">进入模型</button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-empty v-else description="该分类下暂无设备模型" />
              </div>
            </div>
          </div>
        </template>

        <!-- 2.6 空状态 -->
        <div v-else class="canvas-fixed-layout workspace-empty-view">
          <el-empty description="请选择左侧数据资产节点查看数据" />
        </div>

      </section>

    </div>

    <!-- 3. 新建数据表抽屉 (统一规范) -->
    <el-drawer v-model="datasetDrawer.visible" title="新建数据表" size="620px" class="unified-workflow-drawer">
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="数据表说明">
          <el-input v-model="datasetDrawer.dataDesc" placeholder="例如：高压报警专项数据" />
        </el-form-item>
        <el-form-item label="数据模板">
          <div class="template-select-row">
            <el-select v-model="datasetDrawer.templateId" filterable placeholder="请选择同模型模板" @change="onDatasetTemplateChange">
              <el-option v-for="tpl in compatibleTemplates" :key="tpl.id" :label="`${modelName(tpl.deviceModelId)} / ${tpl.templateName}`" :value="tpl.id" />
            </el-select>
            <button type="button" class="btn-link" @click="openTemplateDrawerForCurrentInstance">
              + 自定义新模板
            </button>
          </div>
        </el-form-item>
        <el-form-item label="绑定设备实例">
          <el-select v-model="datasetDrawer.deviceInstanceId" filterable placeholder="请选择同模型设备" @change="onDatasetInstanceChange">
            <el-option v-for="ins in compatibleInstances" :key="instanceId(ins)" :label="instancePath(ins)" :value="Number(instanceId(ins))" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <button class="btn-aliyun" type="button" @click="datasetDrawer.visible = false">取消</button>
          <button class="btn-primary-blue" type="button" :disabled="saving" @click="createDataset">保存建表</button>
        </div>
      </template>
    </el-drawer>

    <!-- 4. 新增数据模板抽屉 (统一规范) -->
    <el-drawer v-model="templateDrawer.visible" title="新增数据模板" size="680px" class="unified-workflow-drawer">
      <el-form label-position="top" class="drawer-form">
        <div class="form-grid two">
          <el-form-item label="模板名称">
            <el-input v-model="templateDrawer.templateName" placeholder="输入模板名称" />
          </el-form-item>
          <el-form-item label="绑定模型">
            <el-select v-model="templateDrawer.deviceModelId" filterable placeholder="选择所属模型" @change="generateFieldsFromModel">
              <el-option v-for="model in models" :key="modelId(model)" :label="model.modelName" :value="Number(modelId(model))" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="模板说明">
          <el-input v-model="templateDrawer.templateDesc" placeholder="简要说明模板用途" />
        </el-form-item>
        <div class="section-toolbar" style="margin: 8px 0; border: 1px solid var(--sl-border-base); border-radius: 4px;">
          <div class="section-title"><strong>模板字段定义</strong></div>
          <button type="button" class="btn-aliyun" @click="addTemplateField">+ 新增字段</button>
        </div>
        <div class="field-editor">
          <div class="field-head">
            <span>模板字段</span>
            <span>字段说明</span>
            <span>绑定属性</span>
            <span>默认值</span>
            <span></span>
          </div>
          <div v-for="(field, idx) in templateDrawer.details" :key="field._key" class="field-row">
            <el-input v-model="field.columnName" placeholder="字段名" />
            <el-input v-model="field.columnDesc" placeholder="说明" />
            <el-select v-model="field.deviceAttrKey" clearable placeholder="可为空">
              <el-option v-for="attr in selectedTemplateModelAttrs" :key="attr.attributeName" :label="attr.displayName || attr.attributeName" :value="attr.attributeName" />
            </el-select>
            <el-input v-model="field.defaultValue" placeholder="默认值" />
            <button type="button" class="btn-link danger" @click="templateDrawer.details.splice(idx, 1)">删除</button>
          </div>
        </div>
      </el-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <button class="btn-aliyun" type="button" @click="templateDrawer.visible = false">取消</button>
          <button class="btn-primary-blue" type="button" :disabled="saving" @click="saveTemplate">保存模板</button>
        </div>
      </template>
    </el-drawer>

  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Cpu, Folder, Tickets, Plus, DocumentAdd, Document } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const route = useRoute()

const loading = ref(false)
const loadingRecords = ref(false)
const saving = ref(false)

const keyword = ref('')
const activeKey = ref('')
const categories = ref([])
const models = ref([])
const instances = ref([])
const datasets = ref([])
const templates = ref([])
const propertyTypes = ref([])
const templateDetails = ref([])
const records = ref([])
const selectedDataset = ref(null)
const selectedTemplate = ref(null)
const selectedInstance = ref(null)
const selectedModel = ref(null)
const selectedCategory = ref(null)
const chartRef = ref(null)
const chart = shallowRef(null)
const recordPage = reactive({ pageNo: 1, pageSize: 100, total: 0 })
const detailCache = reactive({})

const datasetDrawer = reactive({ visible: false, templateId: null, deviceInstanceId: null, dataDesc: '' })
const templateDrawer = reactive({ visible: false, templateName: '', templateDesc: '', deviceModelId: null, details: [] })

const modelMap = computed(() => Object.fromEntries(models.value.map(m => [String(modelId(m)), m])))
const templateMap = computed(() => Object.fromEntries(templates.value.map(t => [String(t.id), t])))
const usableInstances = computed(() => instances.value.filter(isUsableInstance))
const compatibleTemplates = computed(() => {
  const instance = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  const selectedModelId = instance?.deviceModelId || instance?.modelId
  return templates.value.filter(template => templateHasModel(template)
    && (!selectedModelId || String(template.deviceModelId) === String(selectedModelId)))
})
const compatibleInstances = computed(() => {
  const template = templates.value.find(row => Number(row.id) === Number(datasetDrawer.templateId))
  if (!template) return usableInstances.value
  return usableInstances.value.filter(ins => String(ins.deviceModelId || ins.modelId) === String(template.deviceModelId))
})
const selectedTemplateModelAttrs = computed(() => {
  const model = models.value.find(m => Number(modelId(m)) === Number(templateDrawer.deviceModelId))
  return asArray(model?.attributes || model?.attributesPayload || model?.attributesJson)
})

const valueFields = computed(() => templateDetails.value.filter(f => !isUnitField(f)))
const unitMap = computed(() => {
  const map = new Map()
  templateDetails.value.forEach(f => {
    if (isUnitField(f)) map.set(stripUnit(f.columnName), f.defaultValue || '')
  })
  return map
})

// 侧边栏资产树：明确分为「设备类别」与「数据模板」两个一级独立根节点
const treeData = computed(() => {
  // 1. 设备类别分组 (大标题无图标)
  const categoryGroup = {
    key: 'group-categories',
    label: '设备类别',
    type: 'group-header',
    count: categories.value.length,
    children: []
  }
  categories.value.forEach(cat => {
    const cNode = {
      key: `cat-${cat.id}`,
      label: cat.categoryName,
      type: 'category', // 具体的设备类别 -> 文件夹图标
      data: cat,
      count: categoryModels(cat).length,
      children: []
    }
    categoryModels(cat).forEach(model => {
      const mNode = {
        key: `model-${modelId(model)}`,
        label: model.modelName,
        type: 'model', // 设备模型 -> 文档图标
        data: model,
        count: modelInstances(model).length,
        children: []
      }
      modelInstances(model).forEach(ins => {
        const iNode = {
          key: `instance-${instanceId(ins)}`,
          label: ins.instanceName,
          type: 'instance', // 设备实例 -> 设备/芯片图标
          data: ins,
          count: instanceDatasets(ins).length,
          children: []
        }
        instanceDatasets(ins).forEach(ds => {
          iNode.children.push({
            key: `dataset-${ds.id}`,
            label: ds.dataTable,
            type: 'dataset', // 数据表 -> 票据/表格图标
            data: ds
          })
        })
        mNode.children.push(iNode)
      })
      cNode.children.push(mNode)
    })
    categoryGroup.children.push(cNode)
  })

  // 2. 数据模板分组 (大标题无图标)
  const templateGroup = {
    key: 'group-templates',
    label: '数据模板',
    type: 'group-header',
    count: templates.value.length,
    children: templates.value.map(tpl => ({
      key: `template-${tpl.id}`,
      label: tpl.templateName,
      type: 'template', // 数据模板 -> 文档图标
      data: tpl
    }))
  }

  return [categoryGroup, templateGroup]
})

const filteredTree = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return treeData.value
  const filterNode = node => {
    const matched = String(node.label || '').toLowerCase().includes(q)
    const children = asArray(node.children).map(filterNode).filter(Boolean)
    if (matched || children.length) return { ...node, children }
    return null
  }
  return treeData.value.map(filterNode).filter(Boolean)
})

async function loadAll() {
  loading.value = true
  try {
    const [cRes, mRes, iRes, dRes, tRes, pRes] = await Promise.all([
      axios.get('/api/device/category/list'),
      axios.get('/api/device/model/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/data/index/list'),
      axios.get('/api/data/template/list'),
      axios.get('/api/data/property-type/list')
    ])
    categories.value = asArray(cRes.data?.data)
    models.value = asArray(mRes.data?.data)
    instances.value = asArray(iRes.data?.data)
    datasets.value = asArray(dRes.data?.data)
    templates.value = asArray(tRes.data?.data)
    propertyTypes.value = asArray(pRes.data?.data)
    
    // 如果 URL query 传了 instanceId，则优先按照 query 选中目标实例
    if (!applyRouteQuery()) {
      if (!selectedDataset.value && !selectedTemplate.value && !selectedInstance.value && !selectedModel.value && !selectedCategory.value) {
        selectFirstAvailable()
      }
    }
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载数据中心资产失败'))
  } finally {
    loading.value = false
  }
}

function applyRouteQuery() {
  const qInstanceId = route.query.instanceId
  if (!qInstanceId || !instances.value.length) return false
  
  const targetInstance = instances.value.find(ins => String(instanceId(ins)) === String(qInstanceId))
  if (!targetInstance) return false
  
  const relatedDatasets = instanceDatasets(targetInstance)
  if (relatedDatasets.length === 1) {
    selectDataset(relatedDatasets[0])
    return true
  } else {
    selectInstance(targetInstance)
    return true
  }
}

watch(() => route.query.instanceId, () => {
  if (instances.value.length) {
    applyRouteQuery()
  }
})

function handleTreeClick(node) {
  if (node.type === 'dataset') selectDataset(node.data)
  else if (node.type === 'template') selectTemplate(node.data)
  else if (node.type === 'instance') selectInstance(node.data)
  else if (node.type === 'model') selectModel(node.data)
  else if (node.type === 'category') selectCategory(node.data)
}

function clearAllSelections() {
  selectedDataset.value = null
  selectedTemplate.value = null
  selectedInstance.value = null
  selectedModel.value = null
  selectedCategory.value = null
}

async function selectDataset(dataset) {
  clearAllSelections()
  selectedDataset.value = dataset
  activeKey.value = `dataset-${dataset.id}`
  selectedInstance.value = instances.value.find(ins => String(instanceId(ins)) === String(dataset.deviceInstanceId)) || null
  await loadTemplateDetail(dataset.dataTemplateId)
  recordPage.pageNo = 1
  await loadRecords()
}

async function selectTemplate(template) {
  clearAllSelections()
  selectedTemplate.value = template
  activeKey.value = `template-${template.id}`
  await loadTemplateDetail(template.id)
}

function selectInstance(instance) {
  clearAllSelections()
  selectedInstance.value = instance
  activeKey.value = `instance-${instanceId(instance)}`
}

function selectModel(model) {
  clearAllSelections()
  selectedModel.value = model
  activeKey.value = `model-${modelId(model)}`
}

function selectCategory(category) {
  clearAllSelections()
  selectedCategory.value = category
  activeKey.value = `cat-${category.id}`
}

async function loadTemplateDetail(templateId) {
  if (!templateId) { templateDetails.value = []; return }
  if (detailCache[templateId]) { templateDetails.value = detailCache[templateId]; return }
  try {
    const res = await axios.get(`/api/data/template/${templateId}/details`)
    const details = asArray(res.data?.data)
    detailCache[templateId] = details
    templateDetails.value = details
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载模板字段失败'))
  }
}

async function loadRecords(silent = false) {
  if (!selectedDataset.value?.id) { records.value = []; return }
  if (!silent) loadingRecords.value = true
  try {
    const res = await axios.get(`/api/data/record/dataset/${selectedDataset.value.id}`, { params: { pageNo: recordPage.pageNo, pageSize: recordPage.pageSize } })
    const data = res.data?.data
    records.value = asArray(data?.list || data?.records)
    recordPage.total = Number(data?.total || records.value.length || 0)
    nextTick(() => {
      renderChart()
      nextTick(() => chart.value?.resize())
    })
  } catch (err) {
    if (!silent) ElMessage.error(errorMessage(err, '加载采样数据失败'))
  } finally {
    if (!silent) loadingRecords.value = false
  }
}

function renderChart() {
  if (!chartRef.value || !selectedDataset.value) return
  
  // 如果旧实例绑定了已经废弃或重新创建的 DOM，先行妥善释放，彻底解决二次点击不渲染问题
  if (chart.value) {
    if (chart.value.getDom() !== chartRef.value || chart.value.isDisposed()) {
      try { chart.value.dispose() } catch (e) {}
      chart.value = null
    }
  }
  if (!chart.value) {
    chart.value = echarts.init(chartRef.value)
  }
  
  const sorted = [...records.value].sort((a, b) => new Date(recordTime(a) || 0) - new Date(recordTime(b) || 0))
  const xData = sorted.map(row => formatTime(recordTime(row)))
  const units = [...new Set(valueFields.value.map(field => unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'))]
  const yAxis = units.map((unit, idx) => ({ 
    type: 'value', 
    name: unit, 
    position: idx % 2 ? 'right' : 'left', 
    offset: idx > 1 ? (idx - 1) * 36 : 0, 
    splitLine: { show: idx === 0, lineStyle: { type: 'dashed', color: '#e2e8f0' } },
    axisLabel: { color: '#64748b', fontSize: 10.5 },
    nameTextStyle: { color: '#64748b', fontSize: 10.5 }
  }))
  
  const colors = ['#2563eb', '#0284c7', '#16a34a', '#d97706', '#7c3aed'];
  
  const series = valueFields.value.map((field, index) => {
    const unit = unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'
    const color = colors[index % colors.length]
    return { 
      name: field.columnDesc || field.columnName, 
      type: 'line', 
      smooth: 0.35, 
      symbol: 'circle',
      symbolSize: 4,
      showSymbol: false,
      itemStyle: { color: color, borderWidth: 2 },
      lineStyle: { width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: color + '28' },
          { offset: 1, color: color + '00' }
        ])
      },
      yAxisIndex: Math.max(0, units.indexOf(unit)), 
      data: sorted.map(row => numericOrNull(valueOf(row, field.columnName))) 
    }
  })
  
  chart.value.setOption({ 
    color: colors,
    tooltip: { 
      trigger: 'axis',
      backgroundColor: '#0f172a',
      borderColor: '#1e293b',
      padding: [8, 12],
      textStyle: { color: '#f8fafc', fontSize: 12 }
    }, 
    legend: { top: 0, itemWidth: 14, itemHeight: 6, icon: 'roundRect', textStyle: { color: '#475569', fontSize: 11 } }, 
    grid: { 
      left: 10, 
      right: 16, 
      top: 26, 
      bottom: 8, 
      containLabel: true 
    }, 
    xAxis: { 
      type: 'category', 
      data: xData, 
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b', fontSize: 10.5, padding: [4, 0, 0, 0] }
    }, 
    yAxis: yAxis.length ? yAxis : [{ type: 'value' }], 
    series 
  }, true)

  setTimeout(() => {
    chart.value?.resize()
  }, 50)
}

function exportChartImage() {
  if (!chart.value) {
    ElMessage.warning('图表尚未初始化完成')
    return
  }
  const dataURL = chart.value.getDataURL({
    type: 'png',
    pixelRatio: 2,
    backgroundColor: '#ffffff'
  })
  const link = document.createElement('a')
  link.href = dataURL
  link.download = `${selectedDataset.value?.dataTable || 'telemetry_chart'}.png`
  document.body.appendChild(link)
  link.click()
  link.remove()
}

function openDatasetDrawer(template = null, instance = null) {
  if (template && !templateHasModel(template)) {
    ElMessage.warning('当前模板所属的设备模型在系统中不存在，无法建表')
    return
  }
  datasetDrawer.dataDesc = ''
  datasetDrawer.templateId = template?.id ? Number(template.id) : null
  datasetDrawer.deviceInstanceId = instance ? Number(instanceId(instance)) : null

  if (!datasetDrawer.templateId && templateDrawer.deviceModelId) {
    const firstMatched = templates.value.find(t => String(t.deviceModelId) === String(templateDrawer.deviceModelId))
    if (firstMatched) datasetDrawer.templateId = Number(firstMatched.id)
  }

  if (datasetDrawer.templateId && !datasetDrawer.deviceInstanceId) {
    const tpl = templates.value.find(t => Number(t.id) === Number(datasetDrawer.templateId))
    const firstIns = usableInstances.value.find(ins => String(ins.deviceModelId || ins.modelId) === String(tpl?.deviceModelId))
    if (firstIns) datasetDrawer.deviceInstanceId = Number(instanceId(firstIns))
  }

  if (!datasetDrawer.templateId && datasetDrawer.deviceInstanceId) {
    const ins = instances.value.find(i => Number(instanceId(i)) === Number(datasetDrawer.deviceInstanceId))
    const firstTpl = templates.value.find(t => String(t.deviceModelId) === String(ins?.deviceModelId || ins?.modelId))
    if (firstTpl) datasetDrawer.templateId = Number(firstTpl.id)
  }

  datasetDrawer.visible = true
}

function onDatasetTemplateChange() {
  const currentInstance = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  const tpl = templates.value.find(row => Number(row.id) === Number(datasetDrawer.templateId))
  if (!tpl) return
  if (!currentInstance || String(currentInstance.deviceModelId || currentInstance.modelId) !== String(tpl.deviceModelId)) {
    const matched = usableInstances.value.find(ins => String(ins.deviceModelId || ins.modelId) === String(tpl.deviceModelId))
    datasetDrawer.deviceInstanceId = matched ? Number(instanceId(matched)) : null
  }
}

function onDatasetInstanceChange() {
  const currentTpl = templates.value.find(row => Number(row.id) === Number(datasetDrawer.templateId))
  const ins = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  if (!ins) return
  const insModelId = ins.deviceModelId || ins.modelId
  if (!currentTpl || String(currentTpl.deviceModelId) !== String(insModelId)) {
    const matched = templates.value.find(tpl => String(tpl.deviceModelId) === String(insModelId))
    datasetDrawer.templateId = matched ? Number(matched.id) : null
  }
}

function openTemplateDrawerWithModel(model) {
  if (!model) return
  templateDrawer.templateName = ''
  templateDrawer.templateDesc = ''
  templateDrawer.deviceModelId = Number(modelId(model))
  templateDrawer.details = []
  generateFieldsFromModel()
  templateDrawer.visible = true
}

function openTemplateDrawerForCurrentInstance() {
  const ins = instances.value.find(row => Number(instanceId(row)) === Number(datasetDrawer.deviceInstanceId))
  if (ins?.deviceModelId || ins?.modelId) {
    const model = models.value.find(m => String(modelId(m)) === String(ins.deviceModelId || ins.modelId))
    if (model) {
      openTemplateDrawerWithModel(model)
      return
    }
  }
  openTemplateDrawerWithModel(models.value[0] || null)
}

function generateFieldsFromModel() {
  const model = models.value.find(m => Number(modelId(m)) === Number(templateDrawer.deviceModelId))
  if (!model) { templateDrawer.details = []; return }
  const attrs = asArray(model.attributes || model.attributesPayload || model.attributesJson)
  const rows = []
  attrs.forEach(attr => {
    const colName = attr.attributeName || attr.name || ''
    const pId = propertyTypeId(attr.dataType || attr.attributeType || attr.type)
    rows.push({
      _key: uid(),
      columnName: colName,
      columnDesc: attr.displayName || attr.description || colName,
      deviceAttrKey: colName,
      propertyTypeId: pId,
      defaultValue: '',
      isMetric: true,
      nullable: true
    })
    if (attr.unit) {
      rows.push({
        _key: uid(),
        columnName: `${colName}_unit`,
        columnDesc: `${attr.displayName || colName}单位`,
        deviceAttrKey: '',
        propertyTypeId: propertyTypeId('STRING'),
        defaultValue: attr.unit,
        isMetric: false,
        nullable: true
      })
    }
  })
  templateDrawer.details = rows
}

function addTemplateField() {
  templateDrawer.details.push({
    _key: uid(),
    columnName: '',
    columnDesc: '',
    deviceAttrKey: '',
    propertyTypeId: propertyTypeId('DOUBLE'),
    defaultValue: '',
    isMetric: true,
    nullable: true
  })
}

async function createDataset() {
  if (!datasetDrawer.templateId) { ElMessage.warning('请选择数据模板'); return }
  if (!datasetDrawer.deviceInstanceId) { ElMessage.warning('请选择设备实例'); return }
  saving.value = true
  try {
    const payload = {
      templateId: datasetDrawer.templateId,
      deviceInstanceId: datasetDrawer.deviceInstanceId,
      dataDesc: datasetDrawer.dataDesc
    }
    const res = await axios.post('/api/data/index/create-dataset', payload)
    if (res.data?.success) {
      ElMessage.success('数据表创建成功')
      datasetDrawer.visible = false
      await loadAll()
      const created = res.data.data
      if (created) selectDataset(created)
    } else ElMessage.error(res.data?.message || '创建失败')
  } catch (err) { ElMessage.error(errorMessage(err, '创建失败')) } finally { saving.value = false }
}

function validateTemplateFields(fields) {
  if (!fields.length) return '请至少添加一个模板字段'
  const names = new Set()
  for (const field of fields) {
    const name = String(field.columnName || '').trim()
    if (!name) return '存在未填写字段名的模板字段'
    if (names.has(name)) return `字段名“${name}”重复，请修改`
    names.add(name)
    if (!field.propertyTypeId && !String(field.deviceAttrKey || '').trim()) return `字段“${name}”缺少数据类型`
  }
  return ''
}

async function saveTemplate() {
  if (!templateDrawer.templateName.trim()) { ElMessage.warning('请输入模板名称'); return }
  if (!templateDrawer.deviceModelId) { ElMessage.warning('请选择绑定模型'); return }
  const fieldError = validateTemplateFields(templateDrawer.details)
  if (fieldError) { ElMessage.warning(fieldError); return }
  saving.value = true
  try {
    const payload = {
      templateName: templateDrawer.templateName,
      templateDesc: templateDrawer.templateDesc,
      deviceModelId: templateDrawer.deviceModelId,
      isDefault: false,
      details: templateDrawer.details.map(({ _key, ...row }) => ({ ...row, columnLength: row.columnLength || 255 }))
    }
    const res = await axios.post('/api/data/template/save', payload)
    if (res.data?.success) {
      ElMessage.success('模板已保存')
      templateDrawer.visible = false
      const newTemplateId = res.data.data?.id
      await loadAll()
      if (newTemplateId && datasetDrawer.visible) {
        datasetDrawer.templateId = Number(newTemplateId)
        onDatasetTemplateChange()
      }
    } else ElMessage.error(res.data?.message || '保存失败')
  } catch (err) { ElMessage.error(errorMessage(err, '保存失败')) } finally { saving.value = false }
}

async function deleteDataset(dataset) {
  const res = await axios.delete(`/api/data/index/delete/${dataset.id}`)
  if (res.data?.success) {
    ElMessage.success('数据表已删除')
    selectedDataset.value = null
    records.value = []
    await loadAll()
  } else ElMessage.error(res.data?.message || '删除失败')
}

async function deleteTemplate(template) {
  const res = await axios.delete(`/api/data/template/delete/${template.id}`)
  if (res.data?.success) {
    ElMessage.success('模板已删除')
    selectedTemplate.value = null
    templateDetails.value = []; delete detailCache[template.id]
    await loadAll()
  } else ElMessage.error(res.data?.message || '删除失败')
}

async function exportDataset() {
  if (!selectedDataset.value?.id) return
  try {
    const response = await axios.get(`/api/data/record/export/${selectedDataset.value.id}`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([response.data], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `${selectedDataset.value.dataTable || 'dataset'}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (err) {
    ElMessage.error(errorMessage(err, '导出失败'))
  }
}

function selectFirstAvailable() {
  const firstDataset = datasets.value[0]
  if (firstDataset) selectDataset(firstDataset)
  else if (templates.value[0]) selectTemplate(templates.value[0])
}

function fieldLabel(field) {
  const unit = unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey)
  return `${field.columnDesc || field.columnName}${unit ? ' (' + unit + ')' : ''}`
}

function bindingLabel(row) {
  if (isUnitField(row)) return '-'
  return row.deviceAttrKey || '-'
}

function valueOf(row, key) {
  const value = row?.[key] ?? row?.[String(key).toLowerCase()] ?? row?.data?.[key] ?? row?.payload?.[key]
  return value == null || value === '' ? '-' : value
}

function numericOrNull(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

function recordTime(row) { return row?.create_time || row?.createTime || row?.timestamp || row?.collectTime }
function recordIngestTime(row) { return row?.ingest_time || row?.ingestTime }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
function isUnitField(row) { return String(row?.columnName || '').endsWith('_unit') }
function stripUnit(value) { return String(value || '').replace(/_unit$/, '') }
function modelId(model) { return model?.modelId || model?.id }
function isUsableInstance(instance) { return instance?.lifecycleStatus === 'IN_USE' }
function formatLifecycleStatus(status) {
  if (status === 'RETIRED') return '已注销 (退役)'
  return '使用中 (在役)'
}
function instanceLifecycleClass(instance) {
  return instance?.lifecycleStatus === 'RETIRED' ? 'retired' : 'in-use'
}

function instanceId(instance) { return instance?.instanceId || instance?.id }
function modelName(id) { return modelMap.value[String(id)]?.modelName || '未关联模型' }
function templateHasModel(template) { return Boolean(template?.deviceModelId && modelMap.value[String(template.deviceModelId)]) }
function templateName(id) { return templateMap.value[String(id)]?.templateName || '-' }
function instanceName(id) { return instances.value.find(ins => String(instanceId(ins)) === String(id))?.instanceName || '-' }
function instancePath(ins) { return `${modelName(ins.deviceModelId)} / ${ins.instanceName}` }
function categoryName(id) { return categories.value.find(c => String(c.id) === String(id))?.categoryName || '未分类' }
function modelTemplates(model) { if (!model) return []; const mId = String(modelId(model)); return templates.value.filter(t => String(t.deviceModelId) === mId) }
function modelInstances(model) { if (!model) return []; const mId = String(modelId(model)); return instances.value.filter(ins => String(ins.deviceModelId || ins.modelId) === mId) }
function modelDatasets(model) { const mInstances = modelInstances(model); const insIds = new Set(mInstances.map(ins => String(instanceId(ins)))); return datasets.value.filter(ds => insIds.has(String(ds.deviceInstanceId))) }
function instanceDatasets(instance) { if (!instance) return []; const iId = String(instanceId(instance)); return datasets.value.filter(ds => String(ds.deviceInstanceId) === iId) }
function categoryModels(category) { if (!category) return []; const cId = String(category.id); return models.value.filter(m => String(m.categoryId) === cId) }
function categoryInstances(category) { const cModels = categoryModels(category); const mIds = new Set(cModels.map(m => String(modelId(m)))); return instances.value.filter(ins => mIds.has(String(ins.deviceModelId || ins.modelId))) }
function categoryDatasets(category) { const cInstances = categoryInstances(category); const iIds = new Set(cInstances.map(ins => String(instanceId(ins)))); return datasets.value.filter(ds => iIds.has(String(ds.deviceInstanceId))) }
function asArray(value) { return Array.isArray(value) ? value : [] }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function propertyTypeId(type) {
  const candidates = {
    DOUBLE: ['double precision', 'double', 'float8', 'numeric', 'decimal'],
    INTEGER: ['integer', 'int4', 'int', 'bigint'],
    BOOLEAN: ['boolean', 'bool'],
    JSON: ['jsonb', 'json'],
    STRING: ['varchar', 'character varying', 'text', 'string']
  }[String(type || 'STRING').toUpperCase()] || ['varchar', 'text']
  const match = propertyTypes.value.find(row => candidates.includes(String(row.dbType || '').trim().toLowerCase()))
  return match?.id || null
}
function uid() { return Math.random().toString(36).slice(2, 10) }
function disposeChart() { chart.value?.dispose(); chart.value = null }
function resizeChart() { chart.value?.resize() }

let refreshInterval = null
watch(templateDetails, () => nextTick(renderChart))
onMounted(() => { 
  loadAll(); 
  window.addEventListener('resize', resizeChart)
  refreshInterval = setInterval(() => {
    if (selectedDataset.value && !loadingRecords.value) {
      loadRecords(true) // 后台静默刷新
    }
  }, 1000)
})
onUnmounted(() => { 
  window.removeEventListener('resize', resizeChart); 
  disposeChart();
  if (refreshInterval) clearInterval(refreshInterval)
})
</script>

<style scoped>
.data-workbench-page {
  height: calc(100vh - 50px);
  padding: 10px 14px 14px;
  background-color: var(--sl-bg-page);
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
}

.workbench-canvas {
  flex: 1;
  display: grid;
  grid-template-columns: 270px 1fr;
  background: var(--sl-bg-surface);
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-lg);
  overflow: hidden;
  box-shadow: var(--sl-shadow-container);
  min-height: 0;
  height: 100%;
}

/* 2. 右侧工作台画卷 (Main Canvas - 锁死视口) */
.main-canvas-pane {
  background: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  height: 100%;
}

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
.m-val.status-val {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.m-val.status-val .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.m-val.status-val.in-use {
  color: var(--sl-success);
}
.m-val.status-val.in-use .dot {
  background: var(--sl-success);
}
.m-val.status-val.retired {
  color: var(--sl-text-secondary);
}
.m-val.status-val.retired .dot {
  background: var(--sl-text-secondary);
}

.canvas-fixed-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
}

.workspace-empty-view {
  align-items: center;
  justify-content: center;
}

.section-toolbar {
  padding: 6px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base);
  flex-shrink: 0;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading);
  display: flex;
  align-items: center;
}
.count-pill {
  font-size: 11px;
  color: var(--sl-text-secondary);
  background: #e2e8f0;
  padding: 1px 7px;
  border-radius: 10px;
  margin-left: 8px;
  font-weight: normal;
  font-family: var(--sl-font-mono);
}

/* 走势图固定区 (满幅伸展) */
.chart-panel-fixed {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}
.chart-section {
  padding: 2px 14px 2px 14px;
  border-bottom: 1px solid var(--sl-border-base);
  background: #ffffff;
  width: 100%;
  box-sizing: border-box;
}
.chart-box {
  width: 100%;
  height: 255px;
}

/* 表格自适应区 (微边距大卡片包裹) */
.table-panel-flex {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
}

.table-scroll-container {
  flex: 1;
  min-height: 0;
  padding: 6px 12px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
}

.table-card {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  display: flex;
  flex-direction: column;
}

.unified-table-el {
  width: 100%;
  height: 100% !important;
  border: none !important;
}

:deep(.table-card .el-table) {
  border: none !important;
}
:deep(.table-card .el-table__inner-wrapper) {
  border: none !important;
}
:deep(.table-card .el-table__inner-wrapper::before) {
  display: none !important;
}

.time-stamp {
  font-family: var(--sl-font-mono);
  font-size: 12px;
  color: var(--sl-text-secondary);
}
.field-val {
  font-size: 13px;
  color: var(--sl-text-heading);
}

.pager-wrap {
  flex-shrink: 0;
  padding: 6px 14px;
  border-top: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: flex-end;
  background: #ffffff;
}

/* 抽屉样式 */
.drawer-form {
  padding: 16px 20px;
}
.template-select-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.template-select-row .el-select {
  flex: 1;
}

.form-grid.two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field-editor {
  border: 1px solid var(--sl-border-input);
  border-radius: var(--sl-radius-sm);
  overflow: hidden;
}
.field-head,
.field-row {
  display: grid;
  grid-template-columns: minmax(110px, 1fr) minmax(110px, 1fr) minmax(120px, 1fr) minmax(90px, 0.8fr) 50px;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-bottom: 1px solid var(--sl-border-base);
}
.field-head {
  background: #f8fafc;
  font-weight: 600;
  color: var(--sl-text-secondary);
  font-size: 12px;
}
.field-row:last-child {
  border-bottom: 0;
}
</style>
