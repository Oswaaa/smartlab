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
            placeholder="搜索任务、类别、模型、设备或模板..."
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
                  
                  <el-icon v-else-if="data.type === 'task'" class="t-icon task-icon">
                    <List />
                  </el-icon>
                  
                  <!-- 4. 设备实例 / 任务下物理实例：设备/芯片图标 -->
                  <el-icon v-else-if="data.type === 'instance' || data.type === 'task-instance'" class="t-icon instance-icon">
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
              <strong class="m-val">{{ selectedDatasetBinding.modelLabel }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">绑定实例</span>
              <strong class="m-val">{{ selectedDatasetBinding.instanceLabel }}</strong>
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
                  <span class="count-pill">{{ chartLiveLabel }}</span>
                  <span v-if="chartWindowLabel" class="count-pill chart-window-label">{{ chartWindowLabel }}</span>
                </div>
                <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
                  <template v-if="chartUnits.length > 2">
                    <el-radio-group v-model="chartViewMode" size="small">
                      <el-radio-button label="facet">分面视图</el-radio-button>
                      <el-radio-button label="compare">对比视图</el-radio-button>
                    </el-radio-group>
                    <template v-if="chartViewModeEffective === 'compare'">
                      <el-select v-model="compareLeftUnit" size="small" style="width: 108px" placeholder="左轴单位">
                        <el-option v-for="unit in chartUnits" :key="'left-' + unit" :label="unit" :value="unit" />
                      </el-select>
                      <el-select v-model="compareRightUnit" size="small" style="width: 108px" placeholder="右轴单位">
                        <el-option v-for="unit in chartUnits" :key="'right-' + unit" :label="unit" :value="unit" />
                      </el-select>
                    </template>
                  </template>
                  <button v-if="canShiftChartEarlier" class="btn-aliyun" type="button" @click="shiftChartWindow(-1)">上一窗</button>
                  <button v-if="!chartLiveFollow" class="btn-aliyun" type="button" @click="shiftChartWindow(1)">下一窗</button>
                  <button v-if="!chartLiveFollow" class="btn-aliyun" type="button" @click="resumeChartLive">回到最新</button>
                  <button class="btn-aliyun" type="button" @click="resetChartZoom">重置缩放</button>
                  <button class="btn-aliyun" type="button" @click="refreshTelemetry(false)">刷新采样</button>
                  <button class="btn-primary-blue" type="button" @click="exportChartImage">导出图表图片</button>
                </div>
              </div>
              <div class="chart-section">
                <div ref="chartRef" class="chart-box" :style="{ height: chartBoxHeight + 'px' }"></div>
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
              <span class="m-label">已建数据表</span>
              <strong class="m-val highlight">{{ templateDatasets(selectedTemplate).length }} 张</strong>
            </div>
          </div>

          <div class="canvas-fixed-layout">
            <!-- 核心列表 1: 模板字段定义 -->
            <div class="table-panel-flex" style="flex: 1.1;">
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

            <!-- 核心列表 2: 基于此模板创建的数据表 -->
            <div class="table-panel-flex" style="flex: 0.9; border-top: 1px solid var(--sl-border-base);">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>基于此模板创建的数据表</span>
                  <span class="count-pill">{{ templateDatasets(selectedTemplate).length }} 张表</span>
                </div>
                <div style="display: flex; gap: 8px;">
                  <button v-if="templateHasModel(selectedTemplate)" class="btn-aliyun-cta" type="button" @click="openDatasetDrawer(selectedTemplate)">+ 用此模板建表</button>
                </div>
              </div>
              <div class="table-scroll-container">
                <div v-if="templateDatasets(selectedTemplate).length" class="table-card">
                  <el-table :data="templateDatasets(selectedTemplate)" border stripe size="small" height="100%" class="unified-table-el">
                    <el-table-column prop="dataTable" label="数据表名" min-width="180" />
                    <el-table-column prop="dataDesc" label="数据表说明" min-width="200" />
                    <el-table-column label="绑定设备实例" min-width="180">
                      <template #default="{ row }">{{ instancePathByDataset(row) }}</template>
                    </el-table-column>
                    <el-table-column label="创建时间" width="170">
                      <template #default="{ row }"><span class="time-stamp">{{ formatTime(row.createTime) }}</span></template>
                    </el-table-column>
                    <el-table-column label="操作" width="120">
                      <template #default="{ row }">
                        <button class="btn-link" type="button" @click="selectDataset(row, { template: selectedTemplate })">查看详情</button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-empty v-else description="暂无基于该模板创建的数据表">
                  <button v-if="templateHasModel(selectedTemplate)" class="btn-aliyun-cta" type="button" @click="openDatasetDrawer(selectedTemplate)">用此模板建表</button>
                </el-empty>
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
                  <span>{{ selectedTask ? '该任务下该设备的数据表' : '该设备已关联的数据表' }}</span>
                  <span class="count-pill">{{ displayedInstanceDatasets(selectedInstance).length }} 张表</span>
                </div>
                <div style="display: flex; gap: 8px;">
                  <button v-if="!selectedTask && isUsableInstance(selectedInstance)" class="btn-aliyun-cta" type="button" @click="openDatasetDrawer(null, selectedInstance)">+ 为该设备建表</button>
                </div>
              </div>
              <div class="table-scroll-container">
                <div v-if="displayedInstanceDatasets(selectedInstance).length" class="table-card">
                  <el-table :data="displayedInstanceDatasets(selectedInstance)" border stripe size="small" height="100%" class="unified-table-el">
                    <el-table-column prop="dataTable" label="数据表名" min-width="180" />
                    <el-table-column prop="dataDesc" label="数据表说明" min-width="200" />
                    <el-table-column label="依赖模板" min-width="160">
                      <template #default="{ row }">{{ templateName(row.dataTemplateId) }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="120">
                      <template #default="{ row }">
                        <button class="btn-link" type="button" @click="openInstanceDataset(row)">查看详情</button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-empty v-else :description="selectedTask ? '该任务下暂无数据表' : '该设备实例下暂无数据表'">
                  <button v-if="!selectedTask && isUsableInstance(selectedInstance)" class="btn-aliyun-cta" type="button" @click="openDatasetDrawer(null, selectedInstance)">为该设备建表</button>
                </el-empty>
              </div>
            </div>
          </div>
        </template>

        <!-- 2.3b 视角：任务视角 -->
        <template v-else-if="selectedTask">
          <div class="metric-ribbon">
            <div class="m-cell">
              <span class="m-label">任务</span>
              <strong class="m-val">{{ selectedTask.taskName }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">执行种类</span>
              <strong class="m-val">{{ selectedTask.executionKind === 'SIMULATION' ? '模拟执行' : '正式执行' }}</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">绑定物理实例</span>
              <strong class="m-val highlight">{{ taskAssetInstances(selectedTask).length }} 个</strong>
            </div>
            <div class="m-cell">
              <span class="m-label">数据表</span>
              <strong class="m-val highlight">{{ taskAssetDatasetCount(selectedTask) }} 张</strong>
            </div>
          </div>

          <div class="canvas-fixed-layout">
            <div class="table-panel-flex">
              <div class="section-toolbar">
                <div class="section-title">
                  <span>任务绑定的物理实例与数据表</span>
                  <span class="count-pill">{{ taskAssetInstances(selectedTask).length }} 个实例</span>
                </div>
              </div>
              <div class="table-scroll-container">
                <div v-if="taskAssetInstances(selectedTask).length" class="table-card">
                  <el-table :data="taskAssetInstances(selectedTask)" border stripe size="small" height="100%" class="unified-table-el">
                    <el-table-column prop="instanceName" label="物理实例" min-width="180" />
                    <el-table-column label="数据表" min-width="220">
                      <template #default="{ row }">{{ asArray(row.datasets).map(ds => ds.dataTable).join('、') || '暂无' }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="140">
                      <template #default="{ row }">
                        <button class="btn-link" type="button" @click="selectTaskInstance({ task: selectedTask, physicalInstanceId: row.physicalInstanceId, instanceName: row.instanceName })">查看实例</button>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-empty v-else description="该任务没有绑定设备或尚无归档数据" />
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
import { Cpu, Folder, Tickets, Plus, DocumentAdd, Document, List } from '@element-plus/icons-vue'
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
const chartRecords = ref([])
const chartWindowStart = ref(null)
const chartWindowEnd = ref(null)
const chartEarliestAvailable = ref(null)
const chartLatestAvailable = ref(null)
const chartLiveFollow = ref(true)
const CHART_WINDOW_MINUTES = 60
const CHART_MAX_POINTS = 4000
/** 时间轴最小可视跨度：避免缩放到「1 秒占半屏」导致折线呈阶梯跳跃 */
const CHART_MIN_VALUE_SPAN_MS = 60 * 1000
let chartLoadSeq = 0
let tableLoadSeq = 0
let chartPanLoading = false
let chartEdgeIgnoreUntil = 0
let tableRequestInFlight = false
let chartRequestInFlight = false
let tableReloadQueued = false
let chartReloadQueued = false
const selectedDataset = ref(null)
const selectedTemplate = ref(null)
const selectedInstance = ref(null)
const selectedTask = ref(null)
const selectedModel = ref(null)
const selectedCategory = ref(null)
const tasks = ref([])
const taskAssets = ref({})
const virtualLeases = ref([])
const chartRef = ref(null)
const chart = shallowRef(null)
const chartLegendSelected = ref({})
const chartLegendBound = ref(false)
const chartZoomRange = ref({ start: 0, end: 100 })
const chartDataZoomBound = ref(false)
const chartViewMode = ref('facet')
const compareLeftUnit = ref('')
const compareRightUnit = ref('')
const CHART_COLORS = ['#2563eb', '#0284c7', '#16a34a', '#d97706', '#7c3aed']
const recordPage = reactive({ pageNo: 1, pageSize: 100, total: 0 })
const detailCache = reactive({})

const datasetDrawer = reactive({ visible: false, templateId: null, deviceInstanceId: null, dataDesc: '' })
const templateDrawer = reactive({ visible: false, templateName: '', templateDesc: '', deviceModelId: null, details: [] })

const modelMap = computed(() => Object.fromEntries(models.value.map(m => [String(modelId(m)), m])))
const templateMap = computed(() => Object.fromEntries(templates.value.map(t => [String(t.id), t])))
const leaseByDataIndexId = computed(() => {
  const map = {}
  for (const lease of virtualLeases.value) {
    if (lease?.dataIndexId == null) continue
    const key = String(lease.dataIndexId)
    const existing = map[key]
    if (!existing || Number(lease.id) > Number(existing.id)) {
      map[key] = lease
    }
  }
  return map
})
const selectedDatasetBinding = computed(() => resolveDatasetBinding(selectedDataset.value, selectedInstance.value))
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
function fieldUnit(field) {
  return unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'
}
const chartUnits = computed(() => [
  ...new Set(valueFields.value.map(field => fieldUnit(field)))
])
const chartViewModeEffective = computed(() => (
  chartUnits.value.length <= 2 ? 'compare' : chartViewMode.value
))
const chartBoxHeight = computed(() => {
  const zoomPad = 26
  if (chartViewModeEffective.value !== 'facet') return 255 + zoomPad
  const count = Math.max(chartUnits.value.length, 1)
  return Math.min(540, Math.max(255, 56 + count * 88)) + zoomPad
})
const canShiftChartEarlier = computed(() => {
  if (chartWindowStart.value == null) return false
  if (chartEarliestAvailable.value == null) return true
  return chartWindowStart.value > chartEarliestAvailable.value + 1000
})
const chartWindowLabel = computed(() => {
  if (chartWindowStart.value == null || chartWindowEnd.value == null) return ''
  return `${formatChartAxisTime(chartWindowStart.value)} ~ ${formatChartAxisTime(chartWindowEnd.value)}`
})
const chartLiveLabel = computed(() => {
  if (!chartLiveFollow.value) return '历史窗口（固定）'
  if (chartEarliestAvailable.value && chartWindowEnd.value) {
    const spanMs = chartWindowEnd.value - chartEarliestAvailable.value
    if (spanMs < 55 * 60 * 1000) {
      return '实时监控（对齐最早采样）'
    }
  }
  return '最近 1 小时（滑动）'
})

// 侧边栏：任务树 + 设备类别 / 数据模板
const treeData = computed(() => {
  const taskGroup = {
    key: 'group-tasks',
    label: '任务',
    type: 'group-header',
    count: tasks.value.length,
    children: tasks.value.map(task => {
      const instanceRows = taskAssetInstances(task)
      const tNode = {
        key: `task-${task.id}`,
        label: task.taskName || `任务 ${task.id}`,
        type: 'task',
        data: task,
        count: instanceRows.length,
        children: []
      }
      instanceRows.forEach(row => {
        const physical = physicalInstanceForTask(row)
        const iNode = {
          key: `task-${task.id}-instance-${row.physicalInstanceId}`,
          label: row.instanceName || physical.instanceName,
          type: 'task-instance',
          data: { task, physicalInstanceId: row.physicalInstanceId, instanceName: row.instanceName },
          count: asArray(row.datasets).length,
          children: asArray(row.datasets).map(ds => ({
            key: `task-${task.id}-dataset-${ds.id}`,
            label: ds.dataTable,
            type: 'task-dataset',
            data: { task, physicalInstanceId: row.physicalInstanceId, instanceName: row.instanceName, dataset: ds }
          }))
        }
        tNode.children.push(iNode)
      })
      return tNode
    })
  }
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
    children: templates.value.map(tpl => {
      const tplDatasets = templateDatasets(tpl)
      return {
        key: `template-${tpl.id}`,
        label: tpl.templateName,
        type: 'template', // 数据模板 -> 文档图标
        data: tpl,
        count: tplDatasets.length,
        children: tplDatasets.map(ds => ({
          key: `template-${tpl.id}-dataset-${ds.id}`,
          label: ds.dataTable,
          type: 'template-dataset', // 数据表 -> 票据/表格图标
          data: { template: tpl, dataset: ds }
        }))
      }
    })
  }

  return [taskGroup, categoryGroup, templateGroup]
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
    const [cRes, mRes, iRes, dRes, tRes, pRes, taskRes, leaseRes] = await Promise.all([
      axios.get('/api/device/category/list'),
      axios.get('/api/device/model/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/data/index/list'),
      axios.get('/api/data/template/list'),
      axios.get('/api/data/property-type/list'),
      axios.get('/api/task/list'),
      axios.get('/api/adapter/lease/list').catch(() => ({ data: { data: [] } }))
    ])
    categories.value = asArray(cRes.data?.data)
    models.value = asArray(mRes.data?.data)
    instances.value = asArray(iRes.data?.data)
    datasets.value = asArray(dRes.data?.data)
    templates.value = asArray(tRes.data?.data)
    propertyTypes.value = asArray(pRes.data?.data)
    tasks.value = asArray(taskRes.data?.data)
    virtualLeases.value = asArray(leaseRes.data?.data)
    await Promise.all(tasks.value.map(task => ensureTaskAssets(task.id)))
    
    // 如果 URL query 传了 taskId / instanceId，则优先按照 query 选中目标
    if (!await applyRouteQuery()) {
      if (!selectedDataset.value && !selectedTemplate.value && !selectedInstance.value && !selectedTask.value && !selectedModel.value && !selectedCategory.value) {
        selectFirstAvailable()
      }
    }
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载数据中心资产失败'))
  } finally {
    loading.value = false
  }
}

async function applyRouteQuery() {
  const qTaskId = route.query.taskId
  const qDatasetId = route.query.datasetId
  const qInstanceId = route.query.instanceId
  const qTemplateId = route.query.templateId
  if (qTaskId) {
    await ensureTaskAssets(qTaskId)
    const task = tasks.value.find(item => String(taskIdOf(item)) === String(qTaskId))
    if (!task) return false
    if (qDatasetId) {
      const found = findTaskDataset(task, qDatasetId)
      if (found) {
        await selectDataset(found.dataset, {
          task,
          physicalInstanceId: found.physicalInstanceId,
          instanceName: found.instanceName
        })
        return true
      }
    }
    if (qInstanceId) {
      selectTaskInstance({ task, physicalInstanceId: qInstanceId })
      return true
    }
    selectTask(task)
    return true
  }
  if (qDatasetId && datasets.value.length) {
    const targetDataset = datasets.value.find(ds => String(ds.id) === String(qDatasetId))
    if (targetDataset) {
      await selectDataset(targetDataset)
      return true
    }
  }
  if (qTemplateId && templates.value.length) {
    const targetTemplate = templates.value.find(tpl => String(tpl.id) === String(qTemplateId))
    if (targetTemplate) {
      selectTemplate(targetTemplate)
      return true
    }
  }
  if (!qInstanceId || !instances.value.length) return false
  
  const targetInstance = instances.value.find(ins => String(instanceId(ins)) === String(qInstanceId))
  if (!targetInstance) return false
  
  const relatedDatasets = instanceDatasets(targetInstance)
  if (relatedDatasets.length === 1) {
    await selectDataset(relatedDatasets[0])
    return true
  }
  selectInstance(targetInstance)
  return true
}

watch(() => [route.query.instanceId, route.query.datasetId, route.query.taskId, route.query.templateId], () => {
  if (instances.value.length || datasets.value.length || tasks.value.length || templates.value.length) {
    applyRouteQuery()
  }
})

function handleTreeClick(node) {
  if (node.type === 'task-dataset') selectDataset(node.data.dataset, node.data)
  else if (node.type === 'template-dataset') selectDataset(node.data.dataset, node.data)
  else if (node.type === 'task-instance') selectTaskInstance(node.data)
  else if (node.type === 'task') selectTask(node.data)
  else if (node.type === 'dataset') selectDataset(node.data)
  else if (node.type === 'template') selectTemplate(node.data)
  else if (node.type === 'instance') selectInstance(node.data)
  else if (node.type === 'model') selectModel(node.data)
  else if (node.type === 'category') selectCategory(node.data)
}

function clearAllSelections() {
  selectedDataset.value = null
  selectedTemplate.value = null
  selectedInstance.value = null
  selectedTask.value = null
  selectedModel.value = null
  selectedCategory.value = null
}

async function selectDataset(dataset, context) {
  clearAllSelections()
  chartLoadSeq += 1
  tableLoadSeq += 1
  chartRecords.value = []
  records.value = []
  chartWindowStart.value = null
  chartWindowEnd.value = null
  chartEarliestAvailable.value = null
  chartLatestAvailable.value = null
  chartLiveFollow.value = true
  disposeChart()
  resetChartLegendState()
  resetChartViewState()
  selectedDataset.value = dataset
  if (context?.task) {
    selectedTask.value = context.task
    selectedInstance.value = physicalInstanceForTask(context)
    activeKey.value = `task-${context.task.id}-dataset-${dataset.id}`
  } else if (context?.template) {
    selectedTemplate.value = context.template
    selectedInstance.value = instances.value.find(ins => String(instanceId(ins)) === String(dataset.deviceInstanceId)) || null
    activeKey.value = `template-${context.template.id}-dataset-${dataset.id}`
  } else {
    selectedInstance.value = instances.value.find(ins => String(instanceId(ins)) === String(dataset.deviceInstanceId)) || null
    activeKey.value = `dataset-${dataset.id}`
  }
  await loadTemplateDetail(dataset.dataTemplateId)
  recordPage.pageNo = 1
  await nextTick()
  await Promise.all([loadRecords(), loadChartRecords()])
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

function selectTask(task) {
  clearAllSelections()
  selectedTask.value = task
  activeKey.value = `task-${task.id}`
}

function selectTaskInstance(context) {
  const task = context?.task
  if (!task) return
  clearAllSelections()
  selectedTask.value = task
  selectedInstance.value = physicalInstanceForTask(context)
  activeKey.value = `task-${task.id}-instance-${context.physicalInstanceId}`
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
  if (silent && tableRequestInFlight) {
    tableReloadQueued = true
    return
  }
  const datasetId = selectedDataset.value.id
  const seq = ++tableLoadSeq
  if (!silent) loadingRecords.value = true
  tableRequestInFlight = true
  try {
    const res = await axios.get(`/api/data/record/dataset/${datasetId}`, { params: { pageNo: recordPage.pageNo, pageSize: recordPage.pageSize } })
    if (seq !== tableLoadSeq || selectedDataset.value?.id !== datasetId) return
    const data = res.data?.data
    records.value = asArray(data?.list || data?.records)
    recordPage.total = Number(data?.total || records.value.length || 0)
  } catch (err) {
    if (!silent) ElMessage.error(errorMessage(err, '加载采样数据失败'))
  } finally {
    if (seq === tableLoadSeq) {
      tableRequestInFlight = false
      if (!silent) loadingRecords.value = false
      if (tableReloadQueued && selectedDataset.value?.id === datasetId) {
        tableReloadQueued = false
        loadRecords(true)
      }
    }
  }
}

async function loadChartRecords(silent = false, range = null) {
  if (!selectedDataset.value?.id) {
    chartRecords.value = []
    chartWindowStart.value = null
    chartWindowEnd.value = null
    chartEarliestAvailable.value = null
    chartLatestAvailable.value = null
    return
  }
  if (silent && chartRequestInFlight && range == null) {
    chartReloadQueued = true
    return
  }
  const datasetId = selectedDataset.value.id
  const seq = ++chartLoadSeq
  const previousWindowStart = chartWindowStart.value
  const previousWindowEnd = chartWindowEnd.value
  const requestIsLive = range == null
  chartRequestInFlight = true
  try {
    const params = { windowMinutes: CHART_WINDOW_MINUTES, maxPoints: CHART_MAX_POINTS }
    if (range?.from != null && range?.to != null) {
      params.from = range.from
      params.to = range.to
    }
    const res = await axios.get(`/api/data/record/dataset/${datasetId}/series`, { params })
    if (seq !== chartLoadSeq || selectedDataset.value?.id !== datasetId) return
    if (res.data?.success === false) {
      throw new Error(res.data?.message || '加载遥测走势失败')
    }
    const data = res.data?.data
    chartRecords.value = asArray(data?.records)
    chartWindowStart.value = data?.windowStart ? new Date(data.windowStart).getTime() : null
    chartWindowEnd.value = data?.windowEnd ? new Date(data.windowEnd).getTime() : null
    chartEarliestAvailable.value = data?.earliestAvailable ? new Date(data.earliestAvailable).getTime() : null
    chartLatestAvailable.value = data?.latestAvailable ? new Date(data.latestAvailable).getTime() : null

    // 如果是实时 live 模式，且最早数据距今不足 1 小时，起点直接对齐最早数据，不要再早了
    if (requestIsLive && chartEarliestAvailable.value != null && chartWindowStart.value != null) {
      if (chartWindowStart.value < chartEarliestAvailable.value) {
        chartWindowStart.value = chartEarliestAvailable.value
      }
      if (chartWindowEnd.value != null && chartWindowEnd.value <= chartWindowStart.value + 5000) {
        chartWindowEnd.value = chartWindowStart.value + 5000
      }
    }
    // 历史窗请求绝不能被「live 响应」写回跟随最新；仅本次请求本身是 live 才跟随
    chartLiveFollow.value = requestIsLive && data?.live !== false
    const windowChanged = previousWindowStart !== chartWindowStart.value || previousWindowEnd !== chartWindowEnd.value
    if (windowChanged || !requestIsLive) {
      chartEdgeIgnoreUntil = Date.now() + 1200
    }
    await nextTick()
    if (seq !== chartLoadSeq || selectedDataset.value?.id !== datasetId) return
    renderChart()
    await nextTick()
    chart.value?.resize()
  } catch (err) {
    if (!silent) ElMessage.error(errorMessage(err, '加载遥测走势失败'))
  } finally {
    if (seq === chartLoadSeq) {
      chartRequestInFlight = false
      if (chartReloadQueued && requestIsLive && chartLiveFollow.value && selectedDataset.value?.id === datasetId) {
        chartReloadQueued = false
        loadChartRecords(true)
      } else {
        chartReloadQueued = false
      }
    }
  }
}

async function refreshTelemetry(silent = false) {
  if (chartLiveFollow.value) {
    await Promise.all([loadChartRecords(silent), loadRecords(silent)])
    return
  }
  await Promise.all([
    loadChartRecords(silent, { from: chartWindowStart.value, to: chartWindowEnd.value }),
    loadRecords(silent)
  ])
}

async function resumeChartLive() {
  chartLiveFollow.value = true
  chartZoomRange.value = { start: 0, end: 100 }
  await loadChartRecords(false)
}

async function shiftChartWindow(direction) {
  if (chartPanLoading || Date.now() < chartEdgeIgnoreUntil) return
  const start = chartWindowStart.value
  const end = chartWindowEnd.value
  if (start == null || end == null) return
  const span = Math.max(end - start, CHART_MIN_VALUE_SPAN_MS)
  const earliest = chartEarliestAvailable.value
  const latest = chartLatestAvailable.value
  const visiblePct = Math.min(100, Math.max(8, chartZoomRange.value.end - chartZoomRange.value.start))

  let newStart
  let newEnd
  if (direction < 0) {
    if (!canShiftChartEarlier.value) return
    newEnd = start
    newStart = start - span
    if (earliest != null && newStart < earliest) {
      newStart = earliest
      newEnd = Math.min(earliest + span, latest ?? (earliest + span))
    }
    if (newEnd <= newStart) return
  } else {
    if (latest != null && end >= latest - 1000) {
      await resumeChartLive()
      return
    }
    newStart = end
    newEnd = end + span
    if (latest != null && newEnd > latest) {
      newEnd = latest
      newStart = Math.max(latest - span, earliest ?? (latest - span))
    }
    if (newEnd <= newStart) return
  }

  chartPanLoading = true
  chartLiveFollow.value = false
  chartEdgeIgnoreUntil = Date.now() + 1500
  // 落到新窗口衔接侧；故意不贴死 0/100，避免 setOption 后被误判成「又撞到另一侧边缘」
  if (direction < 0) {
    const startPct = Math.max(0, Math.min(90, 100 - visiblePct))
    chartZoomRange.value = { start: startPct, end: Math.min(99, startPct + visiblePct) }
  } else {
    const endPct = Math.min(100, Math.max(10, visiblePct))
    chartZoomRange.value = { start: Math.max(1, endPct - visiblePct), end: endPct }
  }
  try {
    await loadChartRecords(true, { from: newStart, to: newEnd })
  } finally {
    chartPanLoading = false
    chartEdgeIgnoreUntil = Date.now() + 800
  }
}

function resetChartLegendState() {
  chartLegendSelected.value = {}
  chartLegendBound.value = false
}

function resetChartZoomState() {
  chartZoomRange.value = { start: 0, end: 100 }
  chartDataZoomBound.value = false
}

function resetChartViewState() {
  chartViewMode.value = 'facet'
  compareLeftUnit.value = ''
  compareRightUnit.value = ''
  resetChartZoomState()
}

function buildDataZoomOption(xAxisIndex) {
  const { start, end } = chartZoomRange.value
  const windowSpan = (chartWindowStart.value && chartWindowEnd.value)
    ? Math.max(chartWindowEnd.value - chartWindowStart.value, 1000)
    : CHART_WINDOW_MINUTES * 60 * 1000
  const zoomBase = {
    xAxisIndex,
    filterMode: 'none',
    start,
    end,
    minValueSpan: Math.min(CHART_MIN_VALUE_SPAN_MS, windowSpan),
    maxValueSpan: windowSpan
  }
  return [
    {
      type: 'inside',
      ...zoomBase,
      zoomOnMouseWheel: true,
      moveOnMouseMove: true,
      moveOnMouseWheel: false,
      preventDefaultMouseMove: true
    },
    {
      type: 'slider',
      ...zoomBase,
      height: 18,
      bottom: 4,
      borderColor: '#cbd5e1',
      backgroundColor: '#f8fafc',
      fillerColor: 'rgba(37, 99, 235, 0.12)',
      handleStyle: { color: '#2563eb', borderColor: '#2563eb' },
      dataBackground: {
        lineStyle: { color: '#94a3b8', width: 1 },
        areaStyle: { color: '#e2e8f0' }
      },
      selectedDataBackground: {
        lineStyle: { color: '#2563eb', width: 1 },
        areaStyle: { color: 'rgba(37, 99, 235, 0.08)' }
      },
      textStyle: { color: '#64748b', fontSize: 10 },
      brushSelect: false
    }
  ]
}

function resetChartZoom() {
  chartEdgeIgnoreUntil = Date.now() + 800
  chartZoomRange.value = { start: 0, end: 100 }
  if (chart.value && !chart.value.isDisposed()) {
    chart.value.dispatchAction({
      type: 'dataZoom',
      start: 0,
      end: 100
    })
  }
}

function syncCompareUnitDefaults(units) {
  if (!units.length) {
    compareLeftUnit.value = ''
    compareRightUnit.value = ''
    return
  }
  if (!units.includes(compareLeftUnit.value)) {
    compareLeftUnit.value = units[0]
  }
  if (!units.includes(compareRightUnit.value) || compareRightUnit.value === compareLeftUnit.value) {
    compareRightUnit.value = units.find(unit => unit !== compareLeftUnit.value) || units[0]
  }
}

function buildLineSeriesItem(field, index, sorted, extra = {}) {
  const color = CHART_COLORS[index % CHART_COLORS.length]
  return {
    name: field.columnDesc || field.columnName,
    type: 'line',
    smooth: false,
    symbol: 'circle',
    symbolSize: 4,
    showSymbol: false,
    connectNulls: false,
    itemStyle: { color, borderWidth: 1.5 },
    lineStyle: { width: 2 },
    areaStyle: {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: color + '1f' },
        { offset: 1, color: color + '00' }
      ])
    },
    data: sorted.map(row => {
      const x = chartPointTime(row)
      const y = numericOrNull(valueOf(row, field.columnName))
      return x == null ? null : [x, y]
    }).filter(Boolean),
    ...extra
  }
}

function buildTimeXAxis(extra = {}) {
  return {
    type: 'time',
    min: chartWindowStart.value ?? undefined,
    max: chartWindowEnd.value ?? undefined,
    boundaryGap: false,
    axisLine: { lineStyle: { color: '#cbd5e1' } },
    axisLabel: {
      color: '#64748b',
      fontSize: 10.5,
      padding: [4, 0, 0, 0],
      formatter: value => formatChartAxisTime(value)
    },
    ...extra
  }
}

function baseChartOption(series, extra = {}) {
  return {
    color: CHART_COLORS,
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0f172a',
      borderColor: '#1e293b',
      padding: [8, 12],
      textStyle: { color: '#f8fafc', fontSize: 12 },
      axisPointer: {
        type: 'line',
        lineStyle: { color: '#94a3b8', type: 'dashed' }
      },
      formatter: (params) => {
        if (!params || !params.length) return ''
        const list = Array.isArray(params) ? params : [params]
        const firstTime = list[0]?.value?.[0] || list[0]?.axisValue
        const timeStr = formatChartAxisTime(firstTime)
        let html = `<div style="font-size:12px;font-weight:600;margin-bottom:6px;color:#f1f5f9;">${timeStr}</div>`
        for (const item of list) {
          const val = item.value?.[1] != null ? item.value[1] : '-'
          const marker = item.marker || `<span style="display:inline-block;margin-right:6px;border-radius:10px;width:9px;height:9px;background-color:${item.color};"></span>`
          html += `<div style="display:flex;justify-content:space-between;gap:16px;line-height:1.6;font-size:12px;">
            <span>${marker}${item.seriesName}</span>
            <strong style="color:#ffffff;">${val}</strong>
          </div>`
        }
        return html
      }
    },
    legend: {
      top: 0,
      itemWidth: 14,
      itemHeight: 6,
      icon: 'roundRect',
      textStyle: { color: '#475569', fontSize: 11 },
      selected: legendSelectedForSeries(series)
    },
    ...extra,
    series
  }
}

function buildCompareChartOption(sorted, units) {
  const activeUnits = units.length <= 2
    ? units
    : [compareLeftUnit.value, compareRightUnit.value].filter(Boolean)
  const yAxis = activeUnits.map((unit, idx) => ({
    type: 'value',
    name: unit,
    position: idx % 2 ? 'right' : 'left',
    splitLine: { show: idx === 0, lineStyle: { type: 'dashed', color: '#e2e8f0' } },
    axisLabel: { color: '#64748b', fontSize: 10.5 },
    nameTextStyle: { color: '#64748b', fontSize: 10.5 }
  }))

  const series = valueFields.value.flatMap((field, index) => {
    const unit = fieldUnit(field)
    const axisIndex = activeUnits.indexOf(unit)
    if (axisIndex < 0) return []
    return [buildLineSeriesItem(field, index, sorted, { yAxisIndex: axisIndex })]
  })

  return baseChartOption(series, {
    grid: {
      left: 10,
      right: 16,
      top: 26,
      bottom: 34,
      containLabel: true
    },
    dataZoom: buildDataZoomOption(0),
    xAxis: buildTimeXAxis(),
    yAxis: yAxis.length ? yAxis : [{ type: 'value' }]
  })
}

function buildFacetChartOption(sorted, units) {
  const legendTop = 26
  const facetHeight = 88
  const facetGap = 10
  const grids = units.map((_, idx) => ({
    left: 10,
    right: 16,
    top: legendTop + idx * (facetHeight + facetGap),
    height: facetHeight,
    containLabel: true
  }))
  const xAxes = units.map((_, idx) => buildTimeXAxis({
    gridIndex: idx,
    show: idx === units.length - 1,
    axisLine: { show: idx === units.length - 1, lineStyle: { color: '#cbd5e1' } },
    axisTick: { show: idx === units.length - 1 },
    axisLabel: {
      show: idx === units.length - 1,
      color: '#64748b',
      fontSize: 10.5,
      padding: [4, 0, 0, 0],
      formatter: value => formatChartAxisTime(value)
    }
  }))
  const yAxes = units.map((unit, idx) => ({
    type: 'value',
    name: unit,
    gridIndex: idx,
    splitLine: { show: true, lineStyle: { type: 'dashed', color: '#e2e8f0' } },
    axisLabel: { color: '#64748b', fontSize: 10.5 },
    nameTextStyle: { color: '#64748b', fontSize: 10.5 }
  }))
  const series = valueFields.value.map((field, index) => {
    const gridIndex = Math.max(0, units.indexOf(fieldUnit(field)))
    return buildLineSeriesItem(field, index, sorted, {
      xAxisIndex: gridIndex,
      yAxisIndex: gridIndex
    })
  })

  return baseChartOption(series, {
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    dataZoom: buildDataZoomOption('all'),
    grid: grids,
    xAxis: xAxes,
    yAxis: yAxes.length ? yAxes : [{ type: 'value' }]
  })
}

function legendSelectedForSeries(seriesList) {
  const selected = {}
  for (const item of seriesList) {
    const name = item.name
    selected[name] = chartLegendSelected.value[name] !== false
  }
  return selected
}

function bindChartLegend(chartInstance) {
  if (!chartInstance || chartLegendBound.value) return
  chartInstance.on('legendselectchanged', (params) => {
    if (params?.selected) {
      chartLegendSelected.value = { ...params.selected }
    }
  })
  chartLegendBound.value = true
}

function bindChartDataZoom(chartInstance) {
  if (!chartInstance || chartDataZoomBound.value) return
  let prevStart = chartZoomRange.value.start
  let prevEnd = chartZoomRange.value.end
  let isPointerDown = false
  let dragStartX = 0

  const zr = chartInstance.getZr()
  zr.on('mousedown', (e) => {
    isPointerDown = true
    dragStartX = e.offsetX
  })
  const endPointerDrag = () => {
    isPointerDown = false
  }
  zr.on('mouseup', endPointerDrag)
  zr.on('globalout', endPointerDrag)

  chartInstance.on('datazoom', (params) => {
    const batch = params?.batch?.length ? params.batch : [params]
    for (const item of batch) {
      if (item?.start != null && item?.end != null) {
        chartZoomRange.value = { start: item.start, end: item.end }
      }
    }
    const { start, end } = chartZoomRange.value
    // 关键判据：鼠标滚轮缩小（Zoom Out）时，两端同时向外扩散（start 减小 且 end 增大）
    const isWheelZoomOut = (start < prevStart - 0.2) && (end > prevEnd + 0.2)

    // 只有在按下鼠标拖拽滑块（isPointerDown）且不是滚轮缩小（!isWheelZoomOut）时，拖到边界才翻窗
    if (isPointerDown && !isWheelZoomOut && Date.now() >= chartEdgeIgnoreUntil && !chartPanLoading) {
      // 拖动滑块向左到达最左边缘（start <= 1）且之前在右侧（prevStart > 1.5）-> 往前推一个小时（更早）
      if (start <= 1 && prevStart > 1.5) {
        isPointerDown = false
        if (canShiftChartEarlier.value) {
          shiftChartWindow(-1)
        } else {
          ElMessage.info('已到达任务最早数据起点')
        }
      }
      // 拖动滑块向右到达最右边缘（end >= 99）且之前在左侧（prevEnd < 98.5）-> 往后推一个小时（更晚 / 回到最新）
      else if (end >= 99 && prevEnd < 98.5) {
        isPointerDown = false
        if (!chartLiveFollow.value) {
          shiftChartWindow(1)
        }
      }
    }

    prevStart = chartZoomRange.value.start
    prevEnd = chartZoomRange.value.end
  })

  // 当时间轴已经贴死在最左侧（start <= 1）或最右侧（end >= 99），再拖拽无法产生 datazoom 事件时，用鼠标拖动位移翻窗
  zr.on('mousemove', (e) => {
    if (!isPointerDown || chartPanLoading || Date.now() < chartEdgeIgnoreUntil) return
    const totalDx = e.offsetX - dragStartX
    const { start, end } = chartZoomRange.value

    // 向左拖拽超过 35px 且处于最左边缘：往前推一个小时（更早）
    if (totalDx < -35 && start <= 1.5) {
      isPointerDown = false
      if (canShiftChartEarlier.value) {
        shiftChartWindow(-1)
      } else {
        ElMessage.info('已到达任务最早数据起点')
      }
    }
    // 向右拖拽超过 35px 且处于最右边缘：往后推一个小时（更晚 / 回到最新）
    else if (totalDx > 35 && end >= 98.5) {
      isPointerDown = false
      if (!chartLiveFollow.value) {
        shiftChartWindow(1)
      } else {
        ElMessage.info('当前已处于最新实时窗口')
      }
    }
  })

  chartDataZoomBound.value = true
}

function renderChart() {
  if (!selectedDataset.value) return
  if (!chartRef.value) {
    nextTick(() => {
      if (selectedDataset.value && chartRef.value) renderChart()
    })
    return
  }
  const datasetId = selectedDataset.value.id
  // 如果旧实例绑定了已经废弃或重新创建的 DOM，先行妥善释放，彻底解决二次点击不渲染问题
  if (chart.value) {
    if (chart.value.getDom() !== chartRef.value || chart.value.isDisposed()) {
      try { chart.value.dispose() } catch (e) {}
      chart.value = null
      chartLegendBound.value = false
      chartDataZoomBound.value = false
    }
  }
  if (!chart.value) {
    chart.value = echarts.init(chartRef.value)
    bindChartLegend(chart.value)
    bindChartDataZoom(chart.value)
  } else {
    if (!chartLegendBound.value) bindChartLegend(chart.value)
    if (!chartDataZoomBound.value) bindChartDataZoom(chart.value)
  }
  
  const sorted = [...chartRecords.value].sort((a, b) => chartPointTime(a) - chartPointTime(b))
  const units = chartUnits.value
  if (units.length > 2) {
    syncCompareUnitDefaults(units)
  }

  const option = chartViewModeEffective.value === 'facet'
    ? buildFacetChartOption(sorted, units)
    : buildCompareChartOption(sorted, units)

  if (selectedDataset.value?.id !== datasetId) return
  chart.value.setOption(option, { replaceMerge: ['grid', 'series', 'xAxis', 'yAxis', 'dataZoom'] })

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
function chartPointTime(row) {
  const value = recordTime(row)
  if (!value) return 0
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : 0
}
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
function formatChartAxisTime(value) {
  if (value == null) return ''
  return new Date(value).toLocaleString('zh-CN', {
    hour12: false,
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}
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
function templateDatasets(template) { if (!template) return []; const tId = String(template.id); return datasets.value.filter(ds => String(ds.dataTemplateId) === tId) }
function leaseForDataset(dataset) {
  if (!dataset?.id) return null
  return leaseByDataIndexId.value[String(dataset.id)] || null
}
function resolveDatasetBinding(dataset, preferredInstance) {
  if (!dataset) {
    return { modelLabel: '未关联模型', instanceLabel: '-', virtual: false }
  }
  const lease = leaseForDataset(dataset)
  if (lease) {
    const physical = instances.value.find(ins => String(instanceId(ins)) === String(lease.physicalInstanceId))
      || preferredInstance
    const point = (lease.virtualDevicePoint && String(lease.virtualDevicePoint).trim())
      || `租约 #${lease.id}`
    return {
      modelLabel: modelName(physical?.deviceModelId || physical?.modelId),
      instanceLabel: `${point}（虚拟机）`,
      virtual: true
    }
  }
  const live = preferredInstance
    || instances.value.find(ins => String(instanceId(ins)) === String(dataset.deviceInstanceId))
  if (live?.deviceModelId || live?.modelId || live?.instanceName) {
    return {
      modelLabel: modelName(live.deviceModelId || live.modelId),
      instanceLabel: live.instanceName || instanceName(dataset.deviceInstanceId),
      virtual: false
    }
  }
  if (dataset.deviceInstanceId) {
    return {
      modelLabel: '未关联模型',
      instanceLabel: `实例 #${dataset.deviceInstanceId}`,
      virtual: false
    }
  }
  return { modelLabel: '未关联模型', instanceLabel: '-', virtual: false }
}
function instancePathByDataset(dataset) {
  const binding = resolveDatasetBinding(dataset, null)
  if (binding.virtual) {
    return `${binding.modelLabel} / ${binding.instanceLabel}`
  }
  if (!dataset?.deviceInstanceId) return binding.instanceLabel
  const ins = instances.value.find(i => String(instanceId(i)) === String(dataset.deviceInstanceId))
  if (!ins) return binding.instanceLabel === '-' ? `实例 #${dataset.deviceInstanceId}` : binding.instanceLabel
  return instancePath(ins)
}
function modelInstances(model) { if (!model) return []; const mId = String(modelId(model)); return instances.value.filter(ins => String(ins.deviceModelId || ins.modelId) === mId) }
function modelDatasets(model) { const mInstances = modelInstances(model); const insIds = new Set(mInstances.map(ins => String(instanceId(ins)))); return datasets.value.filter(ds => insIds.has(String(ds.deviceInstanceId))) }
function instanceDatasets(instance) { if (!instance) return []; const iId = String(instanceId(instance)); return datasets.value.filter(ds => String(ds.deviceInstanceId) === iId) }
function displayedInstanceDatasets(instance) {
  if (selectedTask.value) return taskInstanceDatasets(selectedTask.value, instance)
  return instanceDatasets(instance)
}
function taskIdOf(task) { return task?.id }
function taskAssetInstances(task) {
  if (!task) return []
  const seen = new Set()
  return asArray(taskAssets.value[task.id]?.instances).filter(row => {
    const id = String(row.physicalInstanceId)
    if (!id || seen.has(id)) return false
    seen.add(id)
    return true
  })
}
function taskAssetDatasetCount(task) {
  return taskAssetInstances(task).reduce((sum, row) => sum + asArray(row.datasets).length, 0)
}
function taskInstanceDatasets(task, instance) {
  if (!task || !instance) return []
  const physicalId = String(instance.physicalInstanceId || instanceId(instance))
  const row = taskAssetInstances(task).find(item => String(item.physicalInstanceId) === physicalId)
  return asArray(row?.datasets)
}
function physicalInstanceForTask(context) {
  const physicalId = context?.physicalInstanceId
  const live = instances.value.find(ins => String(instanceId(ins)) === String(physicalId))
  if (live) return { ...live, physicalInstanceId: physicalId }
  return {
    id: physicalId,
    instanceId: physicalId == null ? null : String(physicalId),
    instanceName: context?.instanceName || String(physicalId || '-'),
    physicalInstanceId: physicalId
  }
}
function findTaskDataset(task, datasetId) {
  for (const row of taskAssetInstances(task)) {
    const dataset = asArray(row.datasets).find(ds => String(ds.id) === String(datasetId))
    if (dataset) return { dataset, physicalInstanceId: row.physicalInstanceId, instanceName: row.instanceName }
  }
  return null
}
async function ensureTaskAssets(taskId) {
  if (taskId == null) return null
  if (taskAssets.value[taskId]) return taskAssets.value[taskId]
  try {
    const res = await axios.get(`/api/task/${taskId}/data-assets`)
    taskAssets.value = { ...taskAssets.value, [taskId]: res.data?.data || { instances: [] } }
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载任务数据失败'))
    taskAssets.value = { ...taskAssets.value, [taskId]: { instances: [] } }
  }
  return taskAssets.value[taskId]
}
function openInstanceDataset(row) {
  if (selectedTask.value) {
    selectDataset(row, {
      task: selectedTask.value,
      physicalInstanceId: selectedInstance.value?.physicalInstanceId || instanceId(selectedInstance.value),
      instanceName: selectedInstance.value?.instanceName
    })
    return
  }
  selectDataset(row)
}
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
function disposeChart() {
  chart.value?.dispose()
  chart.value = null
  resetChartLegendState()
  resetChartViewState()
}
function resizeChart() { chart.value?.resize() }

let refreshInterval = null
watch(templateDetails, () => {
  if (!selectedDataset.value) return
  nextTick(renderChart)
})
watch([chartViewMode, compareLeftUnit, compareRightUnit, chartBoxHeight], () => {
  if (!selectedDataset.value) return
  nextTick(() => {
    renderChart()
    nextTick(() => chart.value?.resize())
  })
})
watch(compareLeftUnit, (left) => {
  if (!left || left === compareRightUnit.value) {
    syncCompareUnitDefaults(chartUnits.value)
  }
})
watch(compareRightUnit, (right) => {
  if (!right || right === compareLeftUnit.value) {
    syncCompareUnitDefaults(chartUnits.value)
  }
})
onMounted(() => { 
  loadAll(); 
  window.addEventListener('resize', resizeChart)
  refreshInterval = setInterval(() => {
    if (!selectedDataset.value) return
    if (chartLiveFollow.value) {
      loadChartRecords(true)
    }
    loadRecords(true)
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
  min-height: 255px;
  transition: height 0.2s ease;
}
.chart-window-label {
  font-weight: 500;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
