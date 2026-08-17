<template>
  <div class="industrial-workbench instance-workbench">
    <!-- G2: 复用 DeviceModelTree.vue 侧边栏（只读模式） -->
    <DeviceModelTree
      v-model:keyword="sidebarKeyword"
      :categories="categories"
      :models="models"
      :selected-model-id="selectedModelId"
      :selected-category-id="selectedCategoryId"
      :loading="modelsLoading"
      :readonly="true"
      class="workbench-sidebar"
      @select-model="selectModel"
      @select-category="selectCategory"
    />

    <main class="workbench-main">
      <!-- 顶部工具栏 -->
      <div class="workbench-header">
        <div class="header-left">
          <h2 class="header-title">{{ selectedModelName }}</h2>
          <span class="header-subtitle">设备实例资产管理、实时状态监控与物理点位映射</span>
        </div>
        <div class="header-actions">
          <el-select v-model="instanceLifecycleFilter" size="small" class="lifecycle-filter" @change="onLifecycleFilterChange">
            <el-option label="使用中 (在役)" value="IN_USE" />
            <el-option label="已注销 (退役)" value="RETIRED" />
            <el-option label="全部状态" value="" />
          </el-select>
          <el-input
            v-model="instanceKeyword"
            class="instance-search"
            size="small"
            clearable
            placeholder="搜索实例名称或出厂 SN..."
            :prefix-icon="Search"
            @input="onInstanceSearchInput"
          />
          <button class="btn-aliyun" type="button" @click="loadData">
            <el-icon><Refresh /></el-icon><span>刷新</span>
          </button>
          <button v-if="canCreateInstance" class="btn-aliyun-cta" type="button" @click="openCreateDrawer">
            <el-icon><Plus /></el-icon><span>添加设备</span>
          </button>
        </div>
      </div>

      <!-- 设备实例列表微边距表格卡片 -->
      <div class="table-scroll-container">
        <div class="table-card" v-loading="loading">
          <el-table
            v-if="instances.length > 0"
            :data="instances"
            stripe
            size="small"
            class="instance-table"
            height="100%"
            row-key="instanceId"
            @row-click="viewDetails"
          >
            <el-table-column label="设备实例名称 / ID" min-width="190">
              <template #default="{ row }">
                <div class="instance-name-cell">
                  <span class="inst-title">{{ row.instanceName }}</span>
                  <span class="inst-id-sub mono">ID: {{ row.instanceId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="所属物模型" min-width="160">
              <template #default="{ row }">
                <span class="model-name-text">{{ getModelName(row.modelId) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Adapter 代理 / 绑定点位" min-width="220">
              <template #default="{ row }">
                <div class="binding-cell">
                  <div class="binding-item">
                    <span class="binding-k">Adapter:</span>
                    <span class="binding-v">{{ row.boundAdapterName || '未分配' }}</span>
                  </div>
                  <div class="binding-item">
                    <span class="binding-k">点位:</span>
                    <span class="binding-v">{{ row.boundDevicePoint || '未分配' }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="MQTT 物理通信主题" min-width="320">
              <template #default="{ row }">
                <div class="topic-compact-cell">
                  <div v-for="topic in mqttTopicRows(row.boundAdapterName, row.boundDevicePoint)" :key="topic.type" class="topic-item">
                    <span class="topic-lbl">{{ topic.label }}:</span>
                    <code class="topic-code">{{ topic.topic || '-' }}</code>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="生命周期" width="115" align="center">
              <template #default="{ row }">
                <span v-if="row.lifecycleStatus === 'RETIRED'" class="status-indicator retired">
                  <span class="dot"></span>已注销
                </span>
                <span v-else class="status-indicator in-use">
                  <span class="dot"></span>使用中
                </span>
              </template>
            </el-table-column>
            <el-table-column label="通信状态" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.lifecycleStatus === 'RETIRED'" class="muted">-</span>
                <span v-else-if="row.isOnline" class="status-indicator online">
                  <span class="dot"></span>在线
                </span>
                <span v-else class="status-indicator offline">
                  <span class="dot"></span>离线
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center" fixed="right">
              <template #default="{ row }">
                <div class="row-actions" @click.stop>
                  <button class="btn-link" type="button" @click="viewDetails(row)">监控与配置</button>
                  <button v-if="canDeleteInstance && row.lifecycleStatus !== 'RETIRED'" class="btn-link danger" type="button" @click="confirmRetireInstance(row)">注销</button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-wrap">
            <el-empty description="暂无匹配的设备实例" :image-size="100" />
          </div>
          <!-- 底部分页器 -->
          <div class="pager-wrap">
            <el-pagination
              v-model:current-page="instancePageNo"
              v-model:page-size="instancePageSize"
              :page-sizes="[12, 24, 48, 96]"
              :total="instanceTotal"
              layout="total, sizes, prev, pager, next"
              background
              size="small"
              @size-change="handleInstancePageSizeChange"
              @current-change="loadInstances"
            />
          </div>
        </div>
      </div>
    </main>

    <!-- 设备实例运行监控与详细配置全功能大抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      size="84%"
      class="instance-detail-drawer unified-workflow-drawer"
      :destroy-on-close="true"
      :with-header="false"
      @close="closeDrawer"
    >
      <div v-if="activeInstance" class="drawer-container">
        <!-- 抽屉顶部自定义标题栏与指标横幅 -->
        <header class="drawer-custom-head">
          <div class="head-top-row">
            <div class="head-title-wrap">
              <span class="inst-type-tag">设备实例</span>
              <h3 class="head-title-text">{{ activeInstance.instanceName }}</h3>
              <span v-if="activeInstance.boundDevicePoint" class="point-badge">{{ activeInstance.boundDevicePoint }}</span>
              <button class="copy-id-btn" type="button" title="点击复制实例 ID" @click="copyText(activeInstance.instanceId)">
                <span class="mono">ID: {{ activeInstance.instanceId }}</span>
                <el-icon><CopyDocument /></el-icon>
              </button>
            </div>
            <div class="head-status-group">
              <span v-if="activeInstanceRetired" class="status-indicator retired">
                <span class="dot"></span>已注销 (退役)
              </span>
              <span v-else class="status-indicator in-use">
                <span class="dot"></span>使用中 (在役)
              </span>
              <template v-if="!activeInstanceRetired">
                <span class="head-sep">|</span>
                <span v-if="activeInstanceOnline" class="status-indicator online">
                  <span class="dot"></span>在线通信
                </span>
                <span v-else class="status-indicator offline">
                  <span class="dot"></span>离线
                </span>
              </template>
              <button class="close-drawer-btn" type="button" @click="drawerVisible = false">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <!-- 35px 像素级高度锁定指标横幅 -->
          <div class="drawer-metric-ribbon">
            <div class="m-col">
              <span class="m-lbl">所属模型</span>
              <span class="m-val highlight">{{ getModelName(activeInstance.modelId) }}</span>
            </div>
            <div class="m-col">
              <span class="m-lbl">Adapter名称</span>
              <span class="m-val">{{ activeInstance.boundAdapterName || '未分配' }}</span>
            </div>
            <div class="m-col">
              <span class="m-lbl">物理点位</span>
              <span class="m-val">{{ activeInstance.boundDevicePoint || '未分配' }}</span>
            </div>
            <div class="m-col">
              <span class="m-lbl">最后心跳时间</span>
              <span class="m-val mono">{{ lastSnapshotTime }}</span>
            </div>
          </div>
        </header>

        <!-- 注销警示横幅 -->
        <div v-if="activeInstanceRetired" class="retired-alert-bar">
          <el-icon class="alert-icon"><WarningFilled /></el-icon>
          <span>该设备实例已注销。实例、孪生快照、组件拓扑和历史数据仅供只读归档查阅，不再参与控制调度或新业务。</span>
        </div>

        <!-- 抽屉主体 6 大 Tab 工作台 -->
        <div class="drawer-tabs-wrap">
          <el-tabs v-model="activeTab" class="instance-tabs">
            <!-- Tab 1: 实时运行状态 -->
            <el-tab-pane label="实时运行状态" name="status">
              <div class="tab-pane-content">
                <!-- 卡片 1: 实时属性遥测 -->
                <div class="holistic-section-card telemetry-card">
                  <div class="section-card-head">
                    <div class="section-card-title">
                      <span class="sec-title-text">实时属性遥测</span>
                      <span class="sec-desc-text">设备各项物理传感器实时采样指标</span>
                    </div>
                    <div class="card-head-meta">
                      <span class="pulse-dot" :class="{ retired: activeInstanceRetired }"></span>
                      <span class="mono last-time">快照采样: {{ lastSnapshotTime }}</span>
                    </div>
                  </div>
                  <div class="section-card-body">
                    <div class="telemetry-kpi-grid" v-if="kpiAttributes.length">
                      <div v-for="kpi in kpiAttributes" :key="kpi.key" class="kpi-card">
                        <div class="kpi-label">
                          <span class="kpi-dot"></span>
                          <span class="kpi-label-text">{{ kpi.label }}</span>
                        </div>
                        <div class="kpi-value-row">
                          <span class="kpi-val mono">{{ kpi.value ?? 'N/A' }}</span>
                          <span class="kpi-unit" v-if="kpi.unit && kpi.value != null">{{ kpi.unit }}</span>
                        </div>
                      </div>
                    </div>
                    <div v-else class="compact-empty block-empty">暂无遥测属性数据</div>
                  </div>
                </div>

                <!-- 卡片 2: 状态机监控 -->
                <div class="holistic-section-card statemachine-card">
                  <div class="section-card-head">
                    <div class="section-card-title">
                      <span class="sec-title-text">状态机监控</span>
                    </div>
                  </div>
                  <div class="section-card-body statemachine-body-split">
                    <!-- 左侧子卡片: 设备指令执行周期 -->
                    <div class="statemachine-subcard cmd-lifecycle-subcard">
                      <div class="subcard-header">
                        <span class="subcard-title">设备指令执行周期</span>
                      </div>
                      <div class="subcard-content cmd-content-body">
                        <div class="cmd-state-main">
                          <span class="cmd-dot" :class="cmdStateClass(snapshot?.currentCommandState)"></span>
                          <span class="cmd-state-name mono">{{ snapshot?.currentCommandState || 'IDLE' }}</span>
                        </div>
                        <div class="cmd-state-desc">{{ cmdStateDescription(snapshot?.currentCommandState) }}</div>
                      </div>
                    </div>

                    <!-- 右侧子卡片: 当前功能状态 (清晰区分功能区域与异常区域) -->
                    <div class="statemachine-subcard op-state-subcard">
                      <div class="subcard-header">
                        <span class="subcard-title">当前功能状态</span>
                      </div>
                      <div class="subcard-content op-regions-container">
                        <!-- 功能区域 -->
                        <div class="region-section-box operational-box">
                          <div class="region-box-head">
                            <span class="region-type-pill op-pill">功能区域</span>
                          </div>
                          <div class="region-tags-wrap" v-if="operationalRegions.length">
                            <div v-for="region in operationalRegions" :key="region.regionName" class="region-row-item">
                              <span class="region-name-badge">{{ region.regionName }}</span>
                              <div class="region-state-tags">
                                <el-tag v-for="state in region.states" :key="region.regionName + state" size="small" type="success" effect="plain" class="state-value-tag">
                                  {{ state }}
                                </el-tag>
                                <span v-if="region.states.length === 0" class="muted">就绪态 (IDLE)</span>
                              </div>
                            </div>
                          </div>
                          <div v-else class="empty-region-hint">设备处于正常运行就绪态</div>
                        </div>

                        <!-- 异常区域 -->
                        <div class="region-section-box exception-box">
                          <div class="region-box-head">
                            <span class="region-type-pill err-pill">异常区域</span>
                          </div>
                          <div class="region-tags-wrap" v-if="activeExceptionStates.length">
                            <div v-for="item in activeExceptionStates" :key="item.regionName + item.state" class="exception-row-item">
                              <el-tag size="small" type="danger" effect="plain" class="state-value-tag">
                                {{ item.state }}
                              </el-tag>
                              <el-button
                                v-if="canControlActiveInstance"
                                size="small"
                                type="danger"
                                plain
                                class="clear-exception-btn"
                                :loading="clearingException === item.state"
                                @click="clearException(item.state)"
                              >
                                解除异常
                              </el-button>
                            </div>
                          </div>
                          <div v-else class="no-exception-box">
                            <el-icon class="safe-check-icon"><CircleCheck /></el-icon>
                            <span>无异常，系统处于安全就绪空间</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <!-- Tab 2: 控制调试 (双栏大厂调试背板) -->
            <el-tab-pane v-if="canControlActiveInstance" label="控制调试" name="control">
              <div class="tab-pane-content control-tab-workbench">
                <div class="control-left-panel">
                  <div class="sub-section-head">
                    <span class="head-title">手动指令下发</span>
                    <span class="head-desc">向物理适配器发送单次执行或调试命令</span>
                  </div>
                  <el-form label-position="top" size="small" class="manual-control-form">
                    <el-form-item label="选择设备操作能力" required>
                      <el-select v-model="controlCapabilityName" style="width: 100%" placeholder="选择模型定义的能力" @change="resetControlParams">
                        <el-option
                          v-for="capability in activeInstanceCapabilities"
                          :key="capability.capabilityName"
                          :label="`${capability.displayName || capability.capabilityName} · Adapter: ${capability.adapterCommandName || '-'}`"
                          :value="capability.capabilityName"
                        />
                      </el-select>
                    </el-form-item>
                    <div class="control-param-grid" v-if="activeControlParams.length">
                      <el-form-item v-for="param in activeControlParams" :key="paramKey(param)" :label="param.displayName || param.name || param.paramName">
                        <el-switch v-if="isBooleanType(param.dataType)" v-model="controlParamValues[paramKey(param)]" />
                        <el-input-number
                          v-else-if="isNumberType(param.dataType)"
                          v-model="controlParamValues[paramKey(param)]"
                          :precision="isIntegerType(param.dataType) ? 0 : undefined"
                          :controls="false"
                          style="width: 100%"
                        />
                        <el-input v-else v-model="controlParamValues[paramKey(param)]" :placeholder="param.dataType || 'STRING'" />
                        <div class="field-hint">数据类型: {{ param.dataType || '-' }}</div>
                      </el-form-item>
                    </div>
                    <div v-else class="empty-inline">该操作无需配置参数。</div>
                  </el-form>
                  <div class="control-action-bar">
                    <button
                      class="btn-primary-blue"
                      type="button"
                      :disabled="sendingControl || !controlCapabilityName || activeInstanceCommandState !== 'IDLE'"
                      @click="sendManualCommand"
                    >
                      <el-icon v-if="sendingControl" class="is-loading"><Loading /></el-icon>
                      <el-icon v-else><VideoPlay /></el-icon>
                      <span>{{ commandButtonText }}</span>
                    </button>
                    <button
                      v-if="activeInstanceCommandState !== 'IDLE'"
                      class="btn-danger-outline"
                      type="button"
                      :disabled="abortingControl || !canAbortActiveCapability"
                      :title="!canAbortActiveCapability ? '该能力未在物模型中定义中止命令，无法下发硬件停机。如需解除上位机锁请使用右上角【人工复位】' : '向物理设备发送真实停机指令 (MANUAL_EXECUTE_ABORT)'"
                      @click="handleAbortCommand"
                    >
                      <el-icon v-if="abortingControl" class="is-loading"><Loading /></el-icon>
                      <el-icon v-else><VideoPause /></el-icon>
                      <span>{{ abortingControl ? '中止中...' : (canAbortActiveCapability ? '终止当前执行' : '未配置终止能力') }}</span>
                    </button>
                  </div>
                </div>

                <!-- 黑色控制台调试日志反馈 -->
                <div class="control-right-panel">
                  <div class="console-header">
                    <div class="console-title">
                      <span class="console-dot"></span>
                      <span>下发反馈与通信终端</span>
                    </div>
                    <div class="console-actions">
                      <button
                        v-if="activeInstanceCommandState !== 'IDLE'"
                        class="btn-link console-reset-btn"
                        type="button"
                        :disabled="resettingControl"
                        @click="handleForceResetCommand"
                        title="向状态机下发 MANUAL_EXECUTE_RESET 信号复位周期"
                      >
                        <el-icon><Warning /></el-icon>
                        <span>人工复位</span>
                      </button>
                      <button class="btn-link console-clear-btn" type="button" @click="clearConsoleLogs">清空记录</button>
                    </div>
                  </div>
                  <div class="console-body" ref="consoleBodyRef">
                    <div v-for="(log, idx) in consoleLogs" :key="idx" :class="['console-line', log.type]">
                      <span class="console-time mono">[{{ log.time }}]</span>
                      <span class="console-tag">[{{ log.tag }}]</span>
                      <span class="console-text">{{ log.text }}</span>
                    </div>
                    <div v-if="consoleLogs.length === 0" class="console-empty">
                      暂无指令下发记录。请在左侧选择操作、配置参数并点击下发...
                    </div>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <!-- Tab 3: 结构拓扑 (BOM 组件) -->
            <el-tab-pane label="结构拓扑" name="components">
              <div class="tab-pane-content">
                <div class="sub-section-head">
                  <span class="head-title">组件结构清单 (BOM)</span>
                  <span class="head-desc">当前设备实例安装的物理子组件与孪生绑定关系</span>
                </div>
                <el-table :data="instanceComponents" stripe border size="small" v-loading="loadingComponents" class="component-table">
                  <el-table-column label="组件槽位名称" min-width="150" prop="componentName" />
                  <el-table-column label="所属类别" min-width="130">
                    <template #default="{ row }">{{ categoryNameById(row.categoryId) || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="绑定孪生实例" min-width="160">
                    <template #default="{ row }">
                      <span class="mono">{{ instanceNameById(row.selfInstanceId) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="组件状态" width="120" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" :type="componentStatusType(row.status)" effect="plain">{{ row.status || '未配置' }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="安装时间" min-width="160">
                    <template #default="{ row }"><span class="mono">{{ formatTime(row.installTime) }}</span></template>
                  </el-table-column>
                  <el-table-column label="规格参数" width="100" align="center">
                    <template #default="{ row }">
                      <button class="btn-link" type="button" @click="showComponentSpecification(row)">
                        {{ componentSpecificationLabel(row.specification) }}
                      </button>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="220" align="center" fixed="right">
                    <template #default="{ row }">
                      <div class="row-actions">
                        <button v-if="canEditActiveInstance && row.status === 'IN_USE'" class="btn-link" type="button" @click="openComponentAction(row, 'configure')">配置</button>
                        <button v-if="canEditActiveInstance && row.status === 'IN_USE'" class="btn-link danger" type="button" @click="markPendingReplacement(row)">标记待换</button>
                        <button v-if="canEditActiveInstance && row.status === 'IN_USE'" class="btn-link" type="button" @click="openComponentAction(row, 'replace')">更换</button>
                        <button v-if="canEditActiveInstance && row.status === 'PENDING_REPLACEMENT'" class="btn-link" type="button" @click="openComponentAction(row, 'replace')">安装新件</button>
                        <button class="btn-link" type="button" @click="showComponentHistory(row)">历史</button>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-tab-pane>

            <!-- Tab 4: 归档数据 -->
            <el-tab-pane label="归档数据" name="datasets">
              <div class="tab-pane-content">
                <div class="sub-section-head">
                  <span class="head-title">归档遥测数据集</span>
                  <span class="head-desc">已为该设备实例创建的时序存储与采样数据表</span>
                </div>
                <div v-if="canCreateActiveDataset" class="dataset-create-toolbar">
                  <div class="dataset-form-inline">
                    <el-select v-model="datasetCreateForm.templateId" size="small" filterable clearable placeholder="选择关联数据模板" style="width: 220px;">
                      <el-option v-for="tpl in availableDataTemplates" :key="templateIdOf(tpl)" :label="tpl.templateName || '未命名模板'" :value="templateIdOf(tpl)" />
                    </el-select>
                    <el-input v-model="datasetCreateForm.dataDesc" size="small" placeholder="数据集描述（例如：1号反应釜温度采样表）" style="flex: 1; max-width: 380px;" />
                    <button class="btn-aliyun" type="button" :disabled="creatingDataSet || !datasetCreateForm.templateId" @click="createDataSetForInstance">
                      <el-icon><Plus /></el-icon><span>关联建表</span>
                    </button>
                  </div>
                  <button class="btn-aliyun" type="button" @click="loadDataSets">
                    <el-icon><Refresh /></el-icon><span>刷新</span>
                  </button>
                </div>
                <el-table :data="instanceDataSets" stripe border size="small" v-loading="loadingDataSets">
                  <el-table-column prop="id" label="数据集 ID" width="100">
                    <template #default="{ row }"><code class="mono">{{ row.id }}</code></template>
                  </el-table-column>
                  <el-table-column prop="dataDesc" label="数据集描述" min-width="180" />
                  <el-table-column prop="dataTable" label="物理底层表名" min-width="200">
                    <template #default="{ row }"><code class="mono">{{ row.dataTable }}</code></template>
                  </el-table-column>
                  <el-table-column prop="createTime" label="创建时间" min-width="160">
                    <template #default="{ row }"><span class="mono">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</span></template>
                  </el-table-column>
                  <el-table-column v-if="canDeleteActiveDataset" label="操作" width="90" align="center" fixed="right">
                    <template #default="{ row }">
                      <el-popconfirm title="确认注销该归档数据表？物理存储将被清空。" @confirm="deleteInstanceDataSet(row)">
                        <template #reference>
                          <button class="btn-link danger" type="button">删除</button>
                        </template>
                      </el-popconfirm>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-tab-pane>

            <!-- Tab 5: 安全约束 -->
            <el-tab-pane label="安全约束" name="constraints">
              <div class="tab-pane-content">
                <div class="sub-section-head">
                  <div class="head-left">
                    <span class="head-title">设备实例安全监控约束</span>
                    <span class="head-desc">继承自模型并允许针对当前物理实例个性化定制的数值安全阈值规则</span>
                  </div>
                  <div class="head-actions" style="display: flex; align-items: center; gap: 8px;">
                    <template v-if="!isEditingConstraints">
                      <button
                        v-if="canEditActiveInstance"
                        class="btn-aliyun"
                        type="button"
                        @click="startEditConstraints"
                      >
                        <el-icon><Edit /></el-icon><span>编辑约束</span>
                      </button>
                    </template>
                    <template v-else>
                      <button class="btn-aliyun" type="button" @click="addConstraint">
                        <el-icon><Plus /></el-icon><span>添加约束规则</span>
                      </button>
                      <button class="btn-aliyun" type="button" @click="cancelEditConstraints">
                        <el-icon><Close /></el-icon><span>取消</span>
                      </button>
                    </template>
                  </div>
                </div>

                <!-- 1. 只读模式 (Read-Only Mode) -->
                <div v-if="!isEditingConstraints" class="constraint-view-container">
                  <el-table
                    :data="localConstraints"
                    border
                    stripe
                    size="small"
                    style="width: 100%"
                    empty-text="暂未配置安全约束规则"
                  >
                    <el-table-column label="监控物模型属性" min-width="160">
                      <template #default="{ row }">
                        <div class="constraint-prop-cell">
                          <span class="prop-name">{{ getAttributeName(row.objectAttributeName) }}</span>
                          <span v-if="row.objectAttributeName" class="prop-code">({{ row.objectAttributeName }})</span>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="条件算子" min-width="110" align="center">
                      <template #default="{ row }">
                        <el-tag size="small" type="info" effect="plain">{{ operatorLabel(row.operator) }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="判定阈值" min-width="110">
                      <template #default="{ row }">
                        <span class="threshold-value">{{ row.boundaryValue != null ? row.boundaryValue : '-' }}</span>
                        <span v-if="row.unit" class="threshold-unit">{{ row.unit }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="违规转向状态" min-width="140">
                      <template #default="{ row }">
                        <el-tag v-if="row.violationStateName" size="small" type="danger" effect="light">
                          {{ row.violationStateName }}
                        </el-tag>
                        <span v-else class="muted">-</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="约束描述" min-width="200" show-overflow-tooltip>
                      <template #default="{ row }">
                        <span>{{ row.description || '-' }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <!-- 2. 编辑模式 (Edit Mode) -->
                <div v-else class="constraint-edit-container">
                  <el-table
                    :data="localConstraints"
                    border
                    size="small"
                    style="width: 100%"
                    empty-text="暂无约束规则，请点击右上角“添加约束规则”"
                  >
                    <el-table-column label="监控物模型属性" min-width="160">
                      <template #default="{ row }">
                        <el-select
                          v-model="row.objectAttributeName"
                          size="small"
                          style="width: 100%"
                          placeholder="选择属性"
                          @change="(val: string) => onConstraintAttrChange(row, val)"
                        >
                          <el-option
                            v-for="prop in getModelAttributes(activeInstance.modelId)"
                            :key="propKey(prop)"
                            :label="propLabel(prop)"
                            :value="propKey(prop)"
                          />
                        </el-select>
                      </template>
                    </el-table-column>
                    <el-table-column label="条件算子" min-width="130">
                      <template #default="{ row }">
                        <el-select v-model="row.operator" size="small" style="width: 100%">
                          <el-option v-for="op in validOperatorsList" :key="op" :label="operatorLabel(op)" :value="op" />
                        </el-select>
                      </template>
                    </el-table-column>
                    <el-table-column label="判定阈值" min-width="110">
                      <template #default="{ row }">
                        <el-input-number v-model="row.boundaryValue" size="small" :controls="false" placeholder="数值" style="width: 100%" />
                      </template>
                    </el-table-column>
                    <el-table-column label="物理单位" min-width="90">
                      <template #default="{ row }">
                        <el-input v-model="row.unit" size="small" placeholder="如: ℃" />
                      </template>
                    </el-table-column>
                    <el-table-column label="违规转向状态" min-width="140">
                      <template #default="{ row }">
                        <el-input v-model="row.violationStateName" size="small" placeholder="如: 温度异常" />
                      </template>
                    </el-table-column>
                    <el-table-column label="约束描述" min-width="180">
                      <template #default="{ row }">
                        <el-input v-model="row.description" size="small" placeholder="异常规则说明..." />
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="70" align="center">
                      <template #default="{ $index }">
                        <button class="btn-link danger" type="button" @click="removeConstraint($index)">删除</button>
                      </template>
                    </el-table-column>
                  </el-table>

                  <div class="bottom-action-bar" style="margin-top: 16px; display: flex; justify-content: flex-end; gap: 10px;">
                    <button class="btn-aliyun" type="button" @click="cancelEditConstraints">
                      <span>取消</span>
                    </button>
                    <button class="btn-primary-blue" type="button" :disabled="saving" @click="saveConstraints">
                      <span>{{ saving ? '保存中...' : '保存约束规则' }}</span>
                    </button>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <!-- Tab 6: 资产信息与 MQTT 主题 -->
            <el-tab-pane label="资产管理" name="info">
              <div class="tab-pane-content">
                <div class="sub-section-head">
                  <span class="head-title">物理资产与通信部署参数</span>
                  <span class="head-desc">维护硬件唯一编码、安装位置、运维日期与 MQTT 通信主题映射</span>
                </div>
                <el-form label-position="top" size="small" class="instance-info-grid" :disabled="activeInstanceRetired">
                  <el-form-item label="设备实例名称" required>
                    <el-input v-model="activeInstance.instanceName" />
                  </el-form-item>
                  <el-form-item label="绑定物模型">
                    <el-select v-model="activeInstance.modelId" style="width: 100%" :disabled="!!activeInstance.instanceId" @change="onModelChangeInDrawer">
                      <el-option v-for="m in models" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="物理 Adapter 代理" required>
                    <el-select v-model="activeInstance.boundAdapterName" style="width: 100%" filterable @change="onAdapterChangeInDrawer">
                      <el-option v-for="adapter in adapterOptions" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="Adapter 设备点位" required>
                    <el-select v-model="activeInstance.boundDevicePoint" style="width: 100%" filterable :disabled="!activeInstance.boundAdapterName">
                      <el-option v-for="point in drawerDevicePointOptions" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="出厂序列号 (SN)">
                    <el-input v-model="activeInstance.instanceConfig.assetInfo.serialNumber" placeholder="出厂硬件 SN 唯一标识" />
                  </el-form-item>
                  <el-form-item label="部署实验室/具体位置">
                    <el-input v-model="activeInstance.instanceConfig.assetInfo.location" placeholder="楼宇-房间-槽位" />
                  </el-form-item>
                  <el-form-item label="采购日期">
                    <el-date-picker v-model="activeInstance.instanceConfig.assetInfo.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                  </el-form-item>
                  <el-form-item label="安装日期">
                    <el-date-picker v-model="activeInstance.instanceConfig.assetInfo.instalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                  </el-form-item>
                  <el-form-item label="资产运维备注" class="form-wide">
                    <el-input v-model="activeInstance.instanceConfig.assetInfo.notes" type="textarea" :rows="2" placeholder="填写设备运维、厂商支持或维保备注..." />
                  </el-form-item>
                </el-form>

                <!-- MQTT 订阅/发布通信主题 -->
                <div class="sub-section-block mt-16">
                  <div class="sub-section-head">
                    <span class="head-title">MQTT 订阅/发布物理通信主题</span>
                    <span class="head-desc">基于 Adapter 与点位自动推导的物理通道主题</span>
                  </div>
                  <el-table :data="activeMqttTopicRows" stripe border size="small" class="topic-table">
                    <el-table-column label="通信用途" width="130" prop="label" />
                    <el-table-column label="数据流向" width="160" prop="direction" />
                    <el-table-column label="MQTT 物理主题">
                      <template #default="{ row }"><code class="mono">{{ row.topic || '-' }}</code></template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- 抽屉常驻底部操作栏 -->
        <footer class="drawer-footer-bar">
          <div class="footer-left">
            <el-popconfirm v-if="canRetireActiveInstance" title="注销后设备将不能参与控制、任务和新业务，现有数据与组件历史会完整保留。确认注销？" @confirm="retireInstance(activeInstance.instanceId)">
              <template #reference>
                <button class="btn-link danger" type="button">注销设备实例</button>
              </template>
            </el-popconfirm>
          </div>
          <div class="footer-right">
            <button class="btn-aliyun" type="button" @click="drawerVisible = false">关闭</button>
            <button v-if="canEditActiveInstance" class="btn-primary-blue" type="button" :disabled="saving" @click="saveInstance">
              <span>{{ saving ? '保存中...' : '保存资产修改' }}</span>
            </button>
          </div>
        </footer>
      </div>
    </el-drawer>

    <!-- G5: 组件拓扑配置与更换二路分流对话框 -->
    <el-dialog v-model="componentActionVisible" :title="componentActionMode === 'replace' ? '更换组件' : '配置组件'" width="580px" append-to-body>
      <div v-loading="componentCategoryModelLoading">
        <template v-if="componentCategoryHasModels">
          <el-form label-position="top" size="small" class="component-action-form">
            <el-form-item label="组件槽位">
              <el-input v-model="componentActionForm.componentName" :readonly="componentActionMode === 'configure'" />
            </el-form-item>
            <el-form-item v-if="componentActionMode === 'replace'" label="安装说明（可选）">
              <el-input v-model="componentActionForm.remark" type="textarea" :rows="2" placeholder="可填写安装、维修或更换说明" />
            </el-form-item>
            <el-form-item label="选择设备模型">
              <el-select v-model="componentSelectedModelId" style="width: 100%" placeholder="选择该类别下的物模型" @change="onComponentModelSelect">
                <el-option v-for="m in componentCategoryModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="componentSelectedModelId" label="绑定设备实例">
              <el-select v-model="componentActionForm.selfInstanceId" clearable filterable style="width: 100%" placeholder="选择一个具体设备实例以绑定孪生">
                <el-option v-for="item in componentCandidateInstances" :key="item.instanceId" :label="`${item.instanceName} (${item.instanceId})`" :value="item.instanceId" />
              </el-select>
            </el-form-item>
            <el-form-item label="规格说明">
              <div class="spec-editor-container">
                <div v-for="(pair, index) in componentActionForm.specificationPairs" :key="index" class="spec-pair-row">
                  <el-input v-model="pair.key" placeholder="参数名 (如: 品牌)" style="flex: 1" />
                  <span class="spec-sep">:</span>
                  <el-input v-model="pair.value" placeholder="参数值 (如: 罗氏)" style="flex: 1.2" />
                  <button class="btn-link danger" type="button" @click="componentActionForm.specificationPairs.splice(index, 1)">删除</button>
                </div>
                <button class="btn-link" type="button" @click="componentActionForm.specificationPairs.push({ key: '', value: '' })">
                  <el-icon><Plus /></el-icon><span>添加规格参数</span>
                </button>
              </div>
            </el-form-item>
          </el-form>
        </template>
        <template v-else>
          <el-alert type="warning" :closable="false" class="mb-12" show-icon>
            该组件对应的类别下没有定义任何数字化模型。系统已退化到非数字孪生组件，仅供记录常规采购规格说明。
          </el-alert>
          <el-form label-position="top" size="small" class="component-action-form">
            <el-form-item label="组件槽位">
              <el-input v-model="componentActionForm.componentName" :readonly="componentActionMode === 'configure'" />
            </el-form-item>
            <el-form-item v-if="componentActionMode === 'replace'" label="安装说明（可选）">
              <el-input v-model="componentActionForm.remark" type="textarea" :rows="2" placeholder="可填写安装、维修或更换说明" />
            </el-form-item>
            <el-form-item label="规格说明">
              <div class="spec-editor-container">
                <div v-for="(pair, index) in componentActionForm.specificationPairs" :key="index" class="spec-pair-row">
                  <el-input v-model="pair.key" placeholder="参数名 (如: 品牌)" style="flex: 1" />
                  <span class="spec-sep">:</span>
                  <el-input v-model="pair.value" placeholder="参数值 (如: Roche)" style="flex: 1.2" />
                  <button class="btn-link danger" type="button" @click="componentActionForm.specificationPairs.splice(index, 1)">删除</button>
                </div>
                <button class="btn-link" type="button" @click="componentActionForm.specificationPairs.push({ key: '', value: '' })">
                  <el-icon><Plus /></el-icon><span>添加规格参数</span>
                </button>
              </div>
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <div class="dialog-footer-actions">
          <button class="btn-aliyun" type="button" @click="componentActionVisible = false">取消</button>
          <button class="btn-primary-blue" type="button" :disabled="savingComponent" @click="submitComponentAction">
            <span>{{ savingComponent ? '保存中...' : '保存' }}</span>
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 更换历史时间轴弹窗 -->
    <el-dialog v-model="componentHistoryVisible" title="组件更换与维修历史" width="600px" append-to-body>
      <div v-loading="loadingComponentHistory" class="history-timeline-wrap">
        <el-timeline v-if="componentHistoryRows.length > 0">
          <el-timeline-item
            v-for="(row, idx) in componentHistoryRows"
            :key="idx"
            :timestamp="formatTime(row.installTime)"
            :type="row.status === 'IN_USE' ? 'primary' : 'info'"
          >
            <h4 class="timeline-title">插槽槽位: {{ row.componentName }}</h4>
            <div class="timeline-desc">
              <div>绑定实例: <b>{{ instanceNameById(row.selfInstanceId) }}</b></div>
              <div>更换状态: <el-tag size="small" :type="componentStatusType(row.status)" effect="plain">{{ row.status }}</el-tag></div>
              <div>备注: {{ row.remark || '无' }}</div>
              <div>规格: <button class="btn-link" type="button" @click="showComponentSpecification(row)">查看详情</button></div>
              <div v-if="row.predecessorId">前置坏件组件 ID: <code class="mono">{{ row.predecessorId }}</code></div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无该槽位的更换历史记录" />
      </div>
    </el-dialog>

    <!-- G1: 添加设备实例侧边滚动面板 (01~04 章节大卡片 + 锚点导航) -->
    <el-drawer
      v-model="createDrawerVisible"
      title="添加设备实例"
      direction="rtl"
      size="78%"
      destroy-on-close
      class="instance-create-drawer unified-workflow-drawer"
    >
      <div class="drawer-workbench-body">
        <div class="detail-anchor-layout">
          <!-- 锚点菜单 (148px) -->
          <el-anchor
            class="detail-anchor-menu"
            @click="(e) => e.preventDefault()"
            container=".edit-scroll-content .el-scrollbar__wrap"
            :offset="20"
          >
            <el-anchor-link href="#create-model" title="01 选择模型" />
            <el-anchor-link href="#create-bind" title="02 点位绑定" />
            <el-anchor-link href="#create-asset" title="03 资产信息" />
            <el-anchor-link href="#create-preview" title="04 契约预览" />
          </el-anchor>

          <!-- 滚动主体区 -->
          <el-scrollbar class="edit-scroll-content">
            <!-- 01 选择模型 -->
            <div id="create-model" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">01</span>
                  <span class="sec-title-text">选择设备模型</span>
                  <span class="sec-desc-text">选择所属设备分类并指定数字物模型定义</span>
                </div>
              </div>
              <div class="section-card-body">
                <el-form label-position="top" size="small">
                  <el-form-item label="所属类别" required>
                    <el-tree-select
                      v-model="wizardSelectedCategoryId"
                      :data="categoryTreeForSelect"
                      node-key="id"
                      check-strictly
                      :render-after-expand="false"
                      placeholder="选择分类"
                      style="width: 100%"
                      @change="onWizardCategoryChange"
                    />
                  </el-form-item>
                  <el-form-item label="设备物模型" required>
                    <el-select
                      v-model="wizardModelId"
                      style="width: 100%"
                      placeholder="选择该类别下的模型"
                      :disabled="!wizardSelectedCategoryId"
                      @change="onWizardModelChange"
                    >
                      <el-option v-for="m in wizardCategoryModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                    </el-select>
                  </el-form-item>
                </el-form>
              </div>
            </div>

            <!-- 02 点位绑定 -->
            <div id="create-bind" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">02</span>
                  <span class="sec-title-text">物理点位绑定</span>
                  <span class="sec-desc-text">指定该设备物理接入的 Adapter 驱动与设备通道点位</span>
                </div>
              </div>
              <div class="section-card-body">
                <el-form label-position="top" size="small">
                  <el-form-item label="物理 Adapter 代理" required>
                    <el-select
                      v-model="wizardAdapterName"
                      style="width: 100%"
                      filterable
                      placeholder="选择 Adapter"
                      @change="onWizardAdapterChange"
                    >
                      <el-option v-for="adapter in adapterOptions" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="Adapter 设备点位" required :disabled="!wizardAdapterName">
                    <el-select
                      v-model="wizardDevicePoint"
                      style="width: 100%"
                      filterable
                      :loading="pointsLoading"
                      placeholder="选择具体设备通道点位 (例如: Reactor_01)"
                    >
                      <el-option v-for="point in wizardDevicePoints" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
                    </el-select>
                  </el-form-item>
                </el-form>
              </div>
            </div>

            <!-- 03 资产信息 -->
            <div id="create-asset" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">03</span>
                  <span class="sec-title-text">资产信息</span>
                  <span class="sec-desc-text">填写物理设备的台账编码、位置与采购运维信息</span>
                </div>
              </div>
              <div class="section-card-body">
                <el-form :model="wizardAssetInfo" label-position="top" size="small" class="instance-info-grid">
                  <el-form-item label="设备实例名称" required>
                    <el-input v-model="wizardInstanceName" placeholder="例如：1号高压反应釜" />
                  </el-form-item>
                  <el-form-item label="出厂序列号 (SN)">
                    <el-input v-model="wizardAssetInfo.serialNumber" placeholder="SN 出厂编码" />
                  </el-form-item>
                  <el-form-item label="部署具体位置">
                    <el-input v-model="wizardAssetInfo.location" placeholder="例如：有机合成实验室-A3" />
                  </el-form-item>
                  <el-form-item label="采购日期">
                    <el-date-picker v-model="wizardAssetInfo.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                  </el-form-item>
                  <el-form-item label="安装日期">
                    <el-date-picker v-model="wizardAssetInfo.instalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                  </el-form-item>
                  <el-form-item label="资产运维备注" class="form-wide">
                    <el-input v-model="wizardAssetInfo.notes" type="textarea" :rows="2" placeholder="填写维保或厂商备注信息..." />
                  </el-form-item>
                </el-form>
              </div>
            </div>

            <!-- 04 契约预览 -->
            <div id="create-preview" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">04</span>
                  <span class="sec-title-text">物模型契约继承预览</span>
                  <span class="sec-desc-text">该实例自动继承的模型数据属性、操作指令与状态空间</span>
                </div>
              </div>
              <div class="section-card-body">
                <div class="step3-preview">
                  <div class="preview-section">
                    <span class="preview-label">继承的数据属性 (Attributes)</span>
                    <div class="preview-tags" v-if="wizardPreviewAttributes.length">
                      <el-tag v-for="attr in wizardPreviewAttributes" :key="attr.attributeName" size="small" effect="plain" type="info">
                        {{ attr.displayName || attr.attributeName }} ({{ attr.dataType }}{{ attr.unit ? ', ' + attr.unit : '' }})
                      </el-tag>
                    </div>
                    <div v-else class="preview-empty">无属性定义</div>
                  </div>

                  <div class="preview-section">
                    <span class="preview-label">继承的设备能力操作 (Capabilities)</span>
                    <div class="preview-tags" v-if="wizardPreviewCapabilities.length">
                      <el-tag v-for="capability in wizardPreviewCapabilities" :key="capability.capabilityName" size="small" type="warning" effect="plain">
                        {{ capability.displayName }}
                      </el-tag>
                    </div>
                    <div v-else class="preview-empty">无操作能力定义</div>
                  </div>

                  <div class="preview-section">
                    <span class="preview-label">功能状态机状态 (OpStates)</span>
                    <div class="preview-tags" v-if="wizardPreviewStates.length">
                      <el-tag v-for="st in wizardPreviewStates" :key="st.stateName" size="small" type="success" effect="plain">
                        {{ st.stateName }}
                      </el-tag>
                    </div>
                    <div v-else class="preview-empty">无内置自定义功能状态</div>
                  </div>
                </div>
              </div>
            </div>
          </el-scrollbar>
        </div>
      </div>
      <template #footer>
        <div class="drawer-footer unified-drawer-footer">
          <button class="btn-aliyun" type="button" @click="createDrawerVisible = false">取消</button>
          <button class="btn-primary-blue" type="button" :disabled="creating || !canSubmitCreate" @click="submitCreate">
            <span>{{ creating ? '创建中...' : '确认并保存实例' }}</span>
          </button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { Plus, Edit, Refresh, RefreshRight, CopyDocument, Search, Close, VideoPlay, VideoPause, Loading, Warning, WarningFilled, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { useAuthStore } from '../../stores/authStore'
import DeviceModelTree from './components/DeviceModelTree.vue'
import { normalizeManualControlCapabilities } from '../../utils/manualControlCapabilities.js'
import {
  loadProtocolMetadata,
  manualControlSignals,
  mqttTopics,
  operators
} from './components/deviceModel/deviceModelConstants'

interface DeviceModel {
  modelId: string
  modelName: string
  deviceCategory: string
  categoryId?: number | string
  capabilitySpec: any
  opState?: any
  cmdState?: any
  attributes?: any[]
  capabilities?: any[]
  intrinsicConstraints?: any[]
  intrinsicConstraint?: any[]
}

interface AdapterOption {
  adapterName: string
  parsedConfig?: any
}

interface AdapterDevicePoint {
  devicePoint: string
  templateName: string
  description?: string
}

interface DeviceInstance {
  instanceId: string
  modelId: string
  stateMachineId: string
  instanceName: string
  boundAdapterName?: string
  boundDevicePoint?: string
  instanceConfig?: any
  lifecycleStatus: 'IN_USE' | 'RETIRED'
  isOnline: boolean
}

interface DeviceSnapshot {
  instanceId: string
  currentCommandState?: string
  currentOperationState?: Record<string, string[]>
  latestAttributes?: Record<string, any>
}

interface InstanceConstraint {
  objectAttributeName: string
  operator: '>' | '<' | '>=' | '<=' | '=' | '!=' | 'BETWEEN' | 'IN'
  boundaryValue: number | null
  unit: string
  violationStateName: string
  description: string
}

interface ConsoleLogEntry {
  time: string
  tag: '下发' | '成功' | '失败' | '系统'
  type: 'send' | 'success' | 'fail' | 'info'
  text: string
}

const authStore = useAuthStore()

const models = ref<DeviceModel[]>([])
const modelOptions = ref<DeviceModel[]>([])
const instances = ref<DeviceInstance[]>([])
const adapterOptions = ref<AdapterOption[]>([])
const drawerDevicePointOptions = ref<AdapterDevicePoint[]>([])
const categories = ref<any[]>([])
const categoriesMap = ref<Record<string, string>>({})
const selectedModelId = ref('')
const sidebarKeyword = ref('')
const instanceKeyword = ref('')
const instanceLifecycleFilter = ref<'IN_USE' | 'RETIRED' | ''>('IN_USE')
const loading = ref(false)
const modelsLoading = ref(false)
const saving = ref(false)
const creating = ref(false)
const sendingControl = ref(false)
const clearingException = ref('')
const pointsLoading = ref(false)

const drawerVisible = ref(false)
const activeInstance = ref<DeviceInstance | null>(null)
const activeTab = ref('status')

const snapshot = ref<DeviceSnapshot | null>(null)
const lastSnapshotTime = ref('-')
let pollingTimer: any = null
const instancePageNo = ref(1)
const instancePageSize = ref(24)
const instanceTotal = ref(0)
let instanceLoadSeq = 0
let instanceSearchTimer: any = null

const localConstraints = ref<InstanceConstraint[]>([])
const isEditingConstraints = ref(false)
const originalConstraints = ref<InstanceConstraint[]>([])
const controlCapabilityName = ref('')
const controlParamValues = ref<Record<string, any>>({})

const consoleLogs = ref<ConsoleLogEntry[]>([])
const consoleBodyRef = ref<HTMLElement | null>(null)

const instanceDataSets = ref<any[]>([])
const loadingDataSets = ref(false)
const dataTemplates = ref<any[]>([])
const creatingDataSet = ref(false)
const datasetCreateForm = ref({ templateId: '', dataDesc: '' })
const instanceComponents = ref<any[]>([])
const loadingComponents = ref(false)
const savingComponent = ref(false)
const componentActionVisible = ref(false)
const componentActionMode = ref<'configure' | 'replace'>('configure')
const activeComponent = ref<any>(null)
const componentActionForm = ref({ componentName: '', selfInstanceId: '', remark: '', specificationPairs: [] as Array<{ key: string, value: string }> })
const componentHistoryVisible = ref(false)
const componentHistoryRows = ref<any[]>([])
const loadingComponentHistory = ref(false)

// G1: 新建抽屉状态
const createDrawerVisible = ref(false)
const wizardSelectedCategoryId = ref('')
const wizardModelId = ref('')
const wizardModel = computed(() => findModelById(wizardModelId.value))
const wizardAdapterName = ref('')
const wizardDevicePoints = ref<AdapterDevicePoint[]>([])
const wizardDevicePoint = ref('')
const wizardInstanceName = ref('')
const wizardAssetInfo = ref({
  serialNumber: '',
  purchaseDate: '',
  instalDate: '',
  location: '',
  notes: ''
})
const wizardCategoryModels = ref<DeviceModel[]>([])
const selectedCategoryId = ref<string>('')

const validOperatorsList = computed(() => {
  const list = operators.length ? operators : ['>', '<', '>=', '<=', '=', '!=']
  return list.filter(op => op !== 'BETWEEN' && op !== 'IN')
})

const canCreateInstance = computed(() => authStore.hasPermission('device_instance:create'))
const canEditInstance = computed(() => authStore.hasPermission('device_instance:edit'))
const canDeleteInstance = computed(() => authStore.hasPermission('device_instance:delete'))
const canControlInstance = computed(() => authStore.hasPermission('device_instance:control'))
const canCreateDataset = computed(() => authStore.hasPermission('data_dataset:create'))
const canDeleteDataset = computed(() => authStore.hasPermission('data_dataset:delete'))
const activeInstanceRetired = computed(() => activeInstance.value?.lifecycleStatus === 'RETIRED')
const activeInstanceOnline = computed(() => {
  if (snapshot.value && snapshot.value.onlineStatus) {
    return snapshot.value.onlineStatus.toUpperCase() === 'ONLINE'
  }
  return activeInstance.value?.isOnline === true || activeInstance.value?.onlineStatus === 'ONLINE'
})
const canEditActiveInstance = computed(() => canEditInstance.value && !activeInstanceRetired.value)
const canRetireActiveInstance = computed(() => canDeleteInstance.value && !activeInstanceRetired.value)
const canControlActiveInstance = computed(() => canControlInstance.value && !activeInstanceRetired.value)
const canCreateActiveDataset = computed(() => canCreateDataset.value && !activeInstanceRetired.value)
const canDeleteActiveDataset = computed(() => canDeleteDataset.value && !activeInstanceRetired.value)

const selectedModelName = computed(() => {
  if (!selectedModelId.value) {
    if (selectedCategoryId.value) {
      return `${categoryNameById(selectedCategoryId.value)} - 类别全部设备实例`
    }
    return '全部设备实例'
  }
  const model = models.value.find(m => String(m.modelId) === String(selectedModelId.value))
  return model ? `${model.modelName} (${model.modelId})` : '未知模型'
})

const snapshotAttributes = computed(() => snapshot.value?.latestAttributes || {})
const activeInstanceModel = computed(() => activeInstance.value ? findModelById(activeInstance.value.modelId) : null)
const operationStateRegions = computed(() => {
  const current = snapshot.value?.currentOperationState
  if (!current || typeof current !== 'object' || Array.isArray(current)) return []
  const configured = asArray(activeInstanceModel.value?.opState?.regions)
  return Object.entries(current).map(([regionName, value]) => ({
    regionName,
    regionType: configured.find((region: any) => region.regionName === regionName)?.regionType || 'OPERATIONAL',
    states: Array.isArray(value) ? value.map(String).filter(Boolean) : []
  }))
})
const operationalRegions = computed(() => operationStateRegions.value.filter(r => r.regionType !== 'EXCEPTION'))
const activeExceptionStates = computed(() => {
  const list: Array<{ regionName: string, state: string }> = []
  operationStateRegions.value
    .filter(r => r.regionType === 'EXCEPTION')
    .forEach(r => {
      r.states.forEach(s => list.push({ regionName: r.regionName, state: s }))
    })
  return list
})
const operationStateSummary = computed(() => operationStateRegions.value
  .filter(region => region.states.length)
  .map(region => `${region.regionName}: ${region.states.join(', ')}`).join(' | '))

const cmdStateClass = (state?: string) => {
  const s = String(state || '').toUpperCase()
  if (s.includes('RUN') || s.includes('EXEC') || s.includes('DO')) return 'running'
  if (s.includes('COMPLET') || s.includes('FINISH') || s.includes('SUCCESS')) return 'completed'
  if (s.includes('FAIL') || s.includes('ERR') || s.includes('ABORT')) return 'error'
  return 'idle'
}

const cmdStateDescription = (state?: string) => {
  const s = String(state || '').toUpperCase()
  if (!s || s === 'IDLE' || s === 'CMD_IDLE') return '设备处于空闲待命状态，准备接收下行控制指令。'
  if (s.includes('RUN') || s.includes('EXEC')) return '设备当前正在执行指令动作，实时推进任务周期。'
  if (s.includes('COMPLET') || s.includes('SUCCESS')) return '上一指令周期已成功执行完毕，已退出动作。'
  if (s.includes('FAIL') || s.includes('ERR')) return '指令执行过程中发生异常中断或被安全机制拒执。'
  return `当前指令执行周期状态: ${state}`
}

const activeInstanceCommandState = computed(() => {
  return String(snapshot.value?.currentCommandState || 'IDLE').toUpperCase()
})

const commandButtonText = computed(() => {
  if (sendingControl.value || activeInstanceCommandState.value === 'SENT') {
    return '指令下发中'
  }
  if (activeInstanceCommandState.value === 'RECEIVED' || activeInstanceCommandState.value === 'RUNNING') {
    return '指令执行中'
  }
  if (activeInstanceCommandState.value !== 'IDLE') {
    return '指令执行中'
  }
  return '开始执行'
})

const activeInstanceIsOnline = computed(() => {
  if (!activeInstance.value) return false
  const state = snapshot.value
  return state?.onlineStatus === 'ONLINE' || activeInstance.value.online === true
})

const activeInstanceCapabilities = computed(() => {
  const capabilities = asArray(activeInstanceModel.value?.capabilitySpec?.capabilities || activeInstanceModel.value?.capabilities)
  return normalizeManualControlCapabilities(capabilities)
})

const activeControlCapability = computed(() => activeInstanceCapabilities.value.find((capability: any) => capability.capabilityName === controlCapabilityName.value) || null)
const activeControlParams = computed(() => asArray(activeControlCapability.value?.parameters).filter((param: any) => !param.internal))
const canAbortActiveCapability = computed(() => {
  if (!activeInstance.value || !activeControlCapability.value) return false
  const cap = activeControlCapability.value
  if (cap.abortCapabilityName) return true
  const allCaps = activeInstanceCapabilities.value
  return allCaps.some((c: any) => c.isAbort && asArray(c.scope).includes(cap.capabilityName))
})
const activeMqttTopicRows = computed(() => mqttTopicRows(activeInstance.value?.boundAdapterName, activeInstance.value?.boundDevicePoint))

// 遥测属性指标卡（展示全部物模型属性，严格对齐 Schema 标准字段 attributeName）
const kpiAttributes = computed(() => {
  if (!activeInstance.value) return []
  const attrs = asArray(getModelAttributes(activeInstance.value.modelId))
  const snapshotMap = snapshotAttributes.value
  const used = new Set<string>()

  const mapped = attrs.map((attr: any) => {
    const key = String(attr.attributeName || '')
    if (key) used.add(key)
    return {
      key: key || '-',
      label: attr.displayName || attr.attributeName || key || '-',
      dataType: attr.dataType || '-',
      unit: attr.unit || '',
      value: key && Object.prototype.hasOwnProperty.call(snapshotMap, key) ? snapshotMap[key] : undefined
    }
  })

  // 补充在快照中但未在模型显式声明的遥测数据
  Object.keys(snapshotMap).forEach(key => {
    if (!used.has(key)) {
      mapped.push({
        key,
        label: getAttributeName(key),
        dataType: '-',
        unit: '',
        value: snapshotMap[key]
      })
    }
  })

  return mapped
})

const availableDataTemplates = computed(() => {
  const modelId = activeInstance.value?.modelId
  return dataTemplates.value.filter(tpl => !tpl.deviceModelId || !modelId || String(tpl.deviceModelId) === String(modelId))
})

const resolveTopicPattern = (pattern: string, variables: Record<string, string>) =>
  String(pattern || '').replace(/\{([^}]+)\}/g, (_, key) => variables[key] || '')

const mqttTopicRows = (adapterName?: string, devicePoint?: string) => {
  const ready = !!adapterName && !!devicePoint
  const variables = { adapterName: adapterName || '', devicePoint: devicePoint || '' }
  return [
    { type: 'command', label: '命令 (Command)', direction: '系统 → Adapter', topic: ready ? resolveTopicPattern(mqttTopics.commandTopic, variables) : '' },
    { type: 'telemetry', label: '遥测 (Telemetry)', direction: 'Adapter → 系统', topic: ready ? resolveTopicPattern(mqttTopics.telemetryTopic, variables) : '' },
    { type: 'event', label: '事件 (Event)', direction: 'Adapter → 系统', topic: ready ? resolveTopicPattern(mqttTopics.eventTopic, variables) : '' },
    { type: 'heartbeat', label: '心跳 (Heartbeat)', direction: 'Adapter → 系统', topic: adapterName ? resolveTopicPattern(mqttTopics.heartbeatTopic, variables) : '' }
  ]
}

const buildCategoryTree = (flat: any[]): any[] => {
  const map = new Map<string, any>()
  flat.forEach(c => map.set(String(c.id), { ...c, children: [] }))
  const roots: any[] = []
  flat.forEach(c => {
    if (c.parentCategoryId) {
      const parent = map.get(String(c.parentCategoryId))
      if (parent) parent.children!.push(map.get(String(c.id))!)
    } else {
      roots.push(map.get(String(c.id))!)
    }
  })
  const markLeaf = (nodes: any[]) => {
    nodes.forEach(n => {
      if (!n.children || n.children.length === 0) {
        n.isLeaf = true
        n.children = undefined
      } else {
        markLeaf(n.children)
      }
    })
  }
  markLeaf(roots)
  return roots
}

const categoryTreeForSelect = computed(() => {
  const tree = buildCategoryTree(categories.value)
  const mapNode = (n: any): any => ({
    id: String(n.id),
    label: n.categoryName,
    children: n.children && n.children.length ? n.children.map(mapNode) : undefined
  })
  return tree.map(mapNode)
})

const loadData = async () => {
  modelsLoading.value = true
  try {
    const catRes = await axios.get('/api/device/category/list')
    if (catRes.data?.success) {
      const map: Record<string, string> = {}
      categories.value = catRes.data.data || []
      ;(catRes.data.data || []).forEach((c: any) => {
        map[c.id] = c.categoryName
      })
      categoriesMap.value = map
    }
    await Promise.all([loadSidebarModels(), loadModelOptions(), loadAdapters(), loadDataTemplates()])
    await loadInstances()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备数据失败')
  } finally {
    modelsLoading.value = false
  }
}

const loadSidebarModels = async () => {
  const res = await axios.get('/api/device/model/page', {
    params: {
      pageNo: 1,
      pageSize: 100
    }
  })
  if (res.data?.success) {
    models.value = (res.data.data?.records || []).map(normalizeModel)
  }
}

const loadModelOptions = async (keyword = '') => {
  const res = await axios.get('/api/device/model/page', {
    params: {
      pageNo: 1,
      pageSize: 100,
      keyword: keyword.trim() || undefined
    }
  })
  if (res.data?.success) {
    const records = (res.data.data?.records || []).map(normalizeModel)
    const selected = findModelById(wizardModelId.value)
    modelOptions.value = selected && !records.some((v: DeviceModel) => v.modelId === selected.modelId)
      ? [selected, ...records]
      : records
  }
}

const loadAdapters = async () => {
  try {
    const res = await axios.get('/api/adapter/index/list')
    if (res.data?.success) {
      adapterOptions.value = res.data.data || []
    }
  } catch {
    adapterOptions.value = []
  }
}

const loadDataTemplates = async () => {
  try {
    const res = await axios.get('/api/data/template/list')
    if (res.data?.success) {
      dataTemplates.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载数据模板失败')
  }
}

const loadDevicePoints = async (adapterName: string, target: 'create' | 'drawer' = 'create') => {
  const listRef = target === 'drawer' ? drawerDevicePointOptions : wizardDevicePoints
  listRef.value = []
  if (!adapterName) return
  pointsLoading.value = true
  try {
    const model = target === 'drawer' ? activeInstanceModel.value : wizardModel.value
    const adapterConfig = model?.capabilitySpec?.adapterContract?.config || {}
    const categoryName = adapterConfig.categoryName || undefined
    const params = { categoryName }
    const res = await axios.get(`/api/adapter/index/${encodeURIComponent(adapterName)}/device-points`, { params })
    if (res.data?.success) {
      listRef.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载 Adapter 设备点失败')
  } finally {
    pointsLoading.value = false
  }
}

const loadInstances = async () => {
  const seq = ++instanceLoadSeq
  loading.value = true
  try {
    const params: any = {
      pageNo: instancePageNo.value,
      pageSize: instancePageSize.value,
      keyword: instanceKeyword.value.trim() || undefined,
      lifecycleStatus: instanceLifecycleFilter.value || undefined
    }
    if (selectedModelId.value) {
      params.modelId = selectedModelId.value
    }
    const instancesRes = await axios.get('/api/device/instance/page', { params })
    
    if (seq !== instanceLoadSeq) return
    if (instancesRes.data?.success) {
      const pageData = instancesRes.data.data || {}
      instances.value = (pageData.records || []).map(normalizeInstance)
      instanceTotal.value = pageData.total || 0
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备实例失败')
  } finally {
    if (seq === instanceLoadSeq) loading.value = false
  }
}

const selectModel = (id: any) => {
  selectedModelId.value = String(id || '')
  instancePageNo.value = 1
  loadInstances()
}

const selectCategory = (categoryData: any) => {
  selectedCategoryId.value = categoryData?.categoryId ? String(categoryData.categoryId) : ''
  selectedModelId.value = ''
  instancePageNo.value = 1
  loadInstances()
}

const onInstanceSearchInput = () => {
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
  instanceSearchTimer = window.setTimeout(() => {
    instancePageNo.value = 1
    loadInstances()
  }, 250)
}

const onLifecycleFilterChange = () => {
  instancePageNo.value = 1
  loadInstances()
}

const handleInstancePageSizeChange = (size: number) => {
  instancePageSize.value = size
  instancePageNo.value = 1
  loadInstances()
}

const getModelName = (modelId: string) => {
  const model = findModelById(modelId)
  return model ? model.modelName : modelId
}

const getModelAttributes = (modelId: string) => {
  const model = findModelById(modelId)
  return model?.capabilitySpec?.attributes || model?.attributes || []
}

const propKey = (prop: any) => String(prop?.attributeName || '')
const propLabel = (prop: any) => String(prop?.displayName || prop?.attributeName || '未命名属性')

const getAttributeName = (key: string) => {
  if (!activeInstance.value) return key
  const attr = getModelAttributes(activeInstance.value.modelId).find((v: any) => String(v.attributeName) === String(key))
  return attr?.displayName || attr?.attributeName || key
}

const normalizeConstraintOperator = (op: string) => op || '<='

const operatorLabel = (op: string) => {
  if (op === '<=') return '≤ 小于等于'
  if (op === '>=') return '≥ 大于等于'
  if (op === '<') return '< 小于'
  if (op === '>') return '> 大于'
  if (op === '=') return '= 等于'
  if (op === '!=' || op === 'NE') return '≠ 不等于'
  return op
}

function asArray<T = any>(value: any): T[] {
  return Array.isArray(value) ? value : []
}

const paramKey = (param: any) => String(param.name || param.paramName || '')
const normalizeType = (type: any) => String(type || '').toUpperCase()
const isBooleanType = (type: any) => normalizeType(type) === 'BOOLEAN' || normalizeType(type) === 'BOOL'
const isIntegerType = (type: any) => ['INTEGER', 'INT', 'LONG'].includes(normalizeType(type))
const isNumberType = (type: any) => isIntegerType(type) || ['DOUBLE', 'FLOAT', 'NUMBER', 'DECIMAL'].includes(normalizeType(type))

const defaultValueForType = (type: any) => {
  if (isBooleanType(type)) return false
  if (isNumberType(type)) return 0
  return ''
}

const resetControlParams = () => {
  const next: Record<string, any> = {}
  activeControlParams.value.forEach((param: any) => {
    next[paramKey(param)] = controlParamValues.value[paramKey(param)] ?? defaultValueForType(param.dataType)
  })
  controlParamValues.value = next
}

const buildControlParameters = () => {
  const parameters: Record<string, any> = {}
  activeControlParams.value.forEach((param: any) => {
    const key = paramKey(param)
    let value = controlParamValues.value[key]
    if (isIntegerType(param.dataType)) value = value === '' || value == null ? 0 : Number.parseInt(String(value), 10)
    else if (isNumberType(param.dataType)) value = value === '' || value == null ? 0 : Number(value)
    parameters[key] = value
  })
  return parameters
}

const viewDetails = (instance: DeviceInstance) => {
  activeInstance.value = JSON.parse(JSON.stringify(instance))
  if (!activeInstance.value.instanceConfig) {
    activeInstance.value.instanceConfig = {}
  }
  if (!activeInstance.value.instanceConfig.assetInfo) {
    activeInstance.value.instanceConfig.assetInfo = {
      serialNumber: '',
      purchaseDate: '',
      instalDate: '',
      location: '',
      notes: ''
    }
  }

  activeInstance.value.boundAdapterName ||= activeInstance.value.instanceConfig.boundAdapterName || ''
  activeInstance.value.boundDevicePoint ||= activeInstance.value.instanceConfig.boundDevicePoint || ''
  
  // 加载实例安全约束
  const cfg = activeInstance.value.instanceConfig || {}
  let list = cfg.intrinsicConstraints
  if (!list || list.length === 0) {
    const model = activeInstanceModel.value
    list = asArray(model?.intrinsicConstraints || model?.intrinsicConstraint)
  }

  localConstraints.value = asArray(list).map((c: any) => ({
    objectAttributeName: c.objectAttributeName || '',
    operator: normalizeConstraintOperator(c.operator || '<='),
    boundaryValue: c.boundaryValue != null ? c.boundaryValue : null,
    unit: c.unit || '',
    violationStateName: c.violationStateName || '',
    description: c.description || ''
  }))

  if (!activeInstanceRetired.value) {
    loadDevicePoints(activeInstance.value.boundAdapterName, 'drawer')
  } else {
    drawerDevicePointOptions.value = []
  }
  datasetCreateForm.value = { templateId: '', dataDesc: '' }
  controlCapabilityName.value = activeInstanceCapabilities.value[0]?.capabilityName || ''
  resetControlParams()
  isEditingConstraints.value = false
  
  activeTab.value = 'status'
  drawerVisible.value = true
  loadDataSets()
  loadComponents()
  
  consoleLogs.value = loadPersistedConsoleLogs(activeInstance.value?.instanceId)
}

const onModelChangeInDrawer = (modelId: string) => {
  if (!activeInstance.value) return
  activeInstance.value.boundDevicePoint = ''
  loadDevicePoints(activeInstance.value.boundAdapterName || '', 'drawer')
}

const saveInstance = async () => {
  if (!activeInstance.value || activeInstanceRetired.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    
    const asset = {
      serialNumber: activeInstance.value.instanceConfig?.assetInfo?.serialNumber || '',
      purchaseDate: activeInstance.value.instanceConfig?.assetInfo?.purchaseDate || '',
      instalDate: activeInstance.value.instanceConfig?.assetInfo?.instalDate || '',
      location: activeInstance.value.instanceConfig?.assetInfo?.location || '',
      notes: activeInstance.value.instanceConfig?.assetInfo?.notes || ''
    }

    const constraints = localConstraints.value
      .filter(c => c.objectAttributeName && c.boundaryValue !== null)
      .map(c => ({
        objectAttributeName: c.objectAttributeName,
        operator: c.operator,
        boundaryValue: c.boundaryValue,
        unit: c.unit || '',
        violationStateName: c.violationStateName || '',
        description: c.description || ''
      }))

    payload.boundAdapterName = activeInstance.value.boundAdapterName
    payload.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.instanceConfig = {
      assetInfo: asset,
      intrinsicConstraints: constraints
    }

    delete payload.assetInfo
    delete payload.localConstraints
    delete payload.commConfig

    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('保存成功')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const confirmRetireInstance = async (row: DeviceInstance) => {
  try {
    await ElMessageBox.confirm(`确认注销设备实例“${row.instanceName}”？注销后该设备将不能参与控制与任务调度，现有历史数据完整保留。`, '注销确认', {
      confirmButtonText: '确认注销',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await retireInstance(row.instanceId)
  } catch {
    // cancelled
  }
}

const retireInstance = async (id: string) => {
  try {
    const res = await axios.post(`/api/device/instance/retire/${id}`)
    if (res.data?.success) {
      ElMessage.success('设备实例已注销')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '注销失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '注销失败')
  }
}

const startEditConstraints = () => {
  originalConstraints.value = JSON.parse(JSON.stringify(localConstraints.value))
  isEditingConstraints.value = true
}

const cancelEditConstraints = () => {
  localConstraints.value = JSON.parse(JSON.stringify(originalConstraints.value))
  isEditingConstraints.value = false
}

const onConstraintAttrChange = (row: any, val: string) => {
  if (!activeInstance.value) return
  const attr = getModelAttributes(activeInstance.value.modelId).find((v: any) => String(v.attributeName) === String(val))
  if (attr?.unit && !row.unit) {
    row.unit = attr.unit
  }
}

const addConstraint = () => {
  localConstraints.value.push({
    objectAttributeName: '',
    operator: '<=',
    boundaryValue: null,
    unit: '',
    violationStateName: '',
    description: ''
  })
}

const removeConstraint = (index: number) => {
  localConstraints.value.splice(index, 1)
}

const saveConstraints = async () => {
  if (!activeInstance.value || activeInstanceRetired.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    
    const asset = {
      serialNumber: activeInstance.value.instanceConfig?.assetInfo?.serialNumber || '',
      purchaseDate: activeInstance.value.instanceConfig?.assetInfo?.purchaseDate || '',
      instalDate: activeInstance.value.instanceConfig?.assetInfo?.instalDate || '',
      location: activeInstance.value.instanceConfig?.assetInfo?.location || '',
      notes: activeInstance.value.instanceConfig?.assetInfo?.notes || ''
    }

    const constraints = localConstraints.value
      .filter(c => c.objectAttributeName && c.boundaryValue !== null)
      .map(c => ({
        objectAttributeName: c.objectAttributeName,
        operator: c.operator,
        boundaryValue: c.boundaryValue,
        unit: c.unit || '',
        violationStateName: c.violationStateName || '',
        description: c.description || ''
      }))

    payload.boundAdapterName = activeInstance.value.boundAdapterName
    payload.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.instanceConfig = {
      assetInfo: asset,
      intrinsicConstraints: constraints
    }

    delete payload.assetInfo
    delete payload.localConstraints
    delete payload.commConfig

    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('约束保存成功')
      isEditingConstraints.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const fetchSnapshot = async () => {
  if (!activeInstance.value) return
  try {
    const res = await axios.get(`/api/device/instance/snapshot/${activeInstance.value.instanceId}`)
    if (res.data?.success) {
      snapshot.value = res.data.data || null
      lastSnapshotTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    }
  } catch {
    snapshot.value = null
  }
}

const clearException = async (violationStateName: string) => {
  if (!activeInstance.value || !violationStateName || activeInstanceRetired.value) return
  try {
    await ElMessageBox.confirm(`确认解除异常状态“${violationStateName}”？系统会先复核当前遥测值。`, '解除异常', {
      confirmButtonText: '解除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    clearingException.value = violationStateName
    const res = await axios.post(`/api/device/instance/${activeInstance.value.instanceId}/exception/clear`, { violationStateName })
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '解除异常失败')
      return
    }
    ElMessage.success('异常已解除')
    await fetchSnapshot()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || error.message || '解除异常失败')
  } finally {
    clearingException.value = ''
  }
}

let consoleEventSource: EventSource | null = null

const resolveCapabilityDisplayName = (capName: string) => {
  if (!capName) return '设备操作'
  const match = activeInstanceCapabilities.value.find((c: any) => c.capabilityName === capName || c.displayName === capName)
  return match?.displayName || capName
}

const formatParamsWithDisplayName = (params: Record<string, any>, capName: string) => {
  if (!params || typeof params !== 'object' || Object.keys(params).length === 0) return ''
  const capability = activeInstanceCapabilities.value.find((c: any) => c.capabilityName === capName || c.displayName === capName)
  const paramDefs: any[] = asArray(capability?.parameters)
  const formatted: Record<string, any> = {}
  for (const [k, v] of Object.entries(params)) {
    const pDef = paramDefs.find((p: any) => (p.name === k || p.paramName === k || p.parameterName === k || p.displayName === k))
    const label = pDef?.displayName || pDef?.name || k
    formatted[label] = v
  }
  return JSON.stringify(formatted)
}

let lastSignalKey = ''

const handleIncomingSseSignal = (data: any) => {
  if (!data) return
  const { stateName, capabilityName, messageId, parameters, regionName, regionState } = data

  if (stateName) {
    const s = String(stateName).toUpperCase()
    if (snapshot.value) {
      snapshot.value.currentCmdState = s
      snapshot.value.currentCommandState = s
    }
    const signalKey = `${messageId || ''}_${s}`
    if (signalKey !== '_' && signalKey === lastSignalKey) {
      return
    }
    lastSignalKey = signalKey

    const capLabel = resolveCapabilityDisplayName(capabilityName || controlCapabilityName.value)
    if (s === 'SENT') {
      appendConsoleLog('下发', 'send', '指令下发中，等待设备确认...')
    } else if (s === 'RECEIVED') {
      appendConsoleLog('执行中', 'running', 'Adapter 已确认接收指令...')
    } else if (s === 'RUNNING' || s === 'EXECUTING') {
      const activeParams = parameters || buildControlParameters()
      const paramStr = formatParamsWithDisplayName(activeParams, capabilityName || controlCapabilityName.value)
      appendConsoleLog('执行中', 'running', `设备已确认并开始执行【${capLabel}】操作${paramStr ? '，参数: ' + paramStr : ''}...`)
    } else if (s === 'COMPLETED' || s === 'SUCCESS') {
      appendConsoleLog('完成', 'success', `设备操作【${capLabel}】执行成功`)
    } else if (s === 'FAILED') {
      appendConsoleLog('失败', 'fail', '指令响应超时或执行失败，状态机已复位')
    } else if (s === 'ABORTED') {
      appendConsoleLog('中止', 'fail', `指令【${capLabel}】已被安全机制或人工中止`)
    }
  } else if (regionName && regionState) {
    if (snapshot.value) {
      if (!snapshot.value.currentOpState) snapshot.value.currentOpState = {}
      snapshot.value.currentOpState[regionName] = regionState
    }
  }
  lastSnapshotTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

const startSseStream = () => {
  stopSseStream()
  if (!activeInstance.value || activeInstanceRetired.value) return
  const id = activeInstance.value.instanceId
  if (!id) return
  try {
    const token = authStore.token
    const url = token
      ? `/api/device/instance/console/stream/${id}?token=${encodeURIComponent(token)}`
      : `/api/device/instance/console/stream/${id}`
    consoleEventSource = new EventSource(url)
    consoleEventSource.addEventListener('connected', () => {
      console.log('SSE console stream connected for instance', id)
    })
    consoleEventSource.addEventListener('signal', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        handleIncomingSseSignal(data)
      } catch {
        // ignore parse error
      }
    })
    consoleEventSource.onerror = () => {
      // EventSource 浏览器原生自带断线重连机制
    }
  } catch (e) {
    console.error('SSE connection failed', e)
  }
}

const stopSseStream = () => {
  if (consoleEventSource) {
    consoleEventSource.close()
    consoleEventSource = null
  }
}

const startPolling = () => {
  stopPolling()
  fetchSnapshot()
  if (activeInstanceRetired.value) return
  pollingTimer = setInterval(fetchSnapshot, 1000)
  startSseStream()
}

const stopPolling = () => {
  stopSseStream()
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const closeDrawer = () => {
  stopPolling()
  snapshot.value = null
  activeInstance.value = null
  controlCapabilityName.value = ''
  controlParamValues.value = {}
  instanceDataSets.value = []
  isEditingConstraints.value = false
}

const loadComponents = async () => {
  if (!activeInstance.value) return
  loadingComponents.value = true
  try {
    const res = await axios.get('/api/device/component/list', { params: { parentInstanceId: activeInstance.value.instanceId } })
    if (res.data?.success) instanceComponents.value = res.data.data || []
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载结构拓扑失败')
  } finally {
    loadingComponents.value = false
  }
}

const componentSpecificationLabel = (value: any) => {
  if (!value || (typeof value === 'object' && !Object.keys(value).length)) return '未填写'
  return '查看详情'
}

const showComponentSpecification = (row: any) => {
  const spec = row?.specification || {}
  const pairs = Object.entries(spec).map(([k, v]) => `${k}: ${String(v)}`)
  if (!pairs.length) return ElMessage.info('暂无规格信息')
  ElMessageBox.alert(pairs.join('\n'), `${row.componentName}规格详情`)
}

const componentStatusType = (status?: string) => {
  if (status === 'IN_USE') return 'success'
  if (status === 'PENDING_REPLACEMENT') return 'warning'
  if (status === '已更换') return 'info'
  return 'info'
}

const componentCategoryModels = ref<DeviceModel[]>([])
const componentCategoryModelLoading = ref(false)
const componentSelectedModelId = ref('')
const componentCandidateInstances = ref<DeviceInstance[]>([])
const componentCategoryHasModels = ref(true)

const openComponentAction = async (row: any, mode: 'configure' | 'replace') => {
  if (activeInstanceRetired.value) return
  activeComponent.value = row
  componentActionMode.value = mode
  componentSelectedModelId.value = ''
  componentCandidateInstances.value = []
  const specPairs: Array<{ key: string; value: string }> = []
  if (row?.specification && typeof row.specification === 'object') {
    Object.entries(row.specification).forEach(([k, v]) => {
      specPairs.push({ key: k, value: String(v) })
    })
  }
  if (specPairs.length === 0) {
    specPairs.push({ key: '', value: '' })
  }

  componentActionForm.value = {
    componentName: row?.componentName || '',
    selfInstanceId: row?.selfInstanceId ? String(row.selfInstanceId) : '',
    remark: mode === 'replace' ? '' : (row?.remark || ''),
    specificationPairs: specPairs
  }

  const componentCategoryId = row?.categoryId ? String(row.categoryId) : ''

  if (componentCategoryId) {
    componentCategoryModelLoading.value = true
    try {
      const res = await axios.get('/api/device/model/page', {
        params: { pageNo: 1, pageSize: 100, categoryId: componentCategoryId }
      })
      if (res.data?.success) {
        const list = (res.data.data?.records || [])
          .map(normalizeModel)
          .filter(model => String(model.categoryId || '') === componentCategoryId)
        componentCategoryModels.value = list
        componentCategoryHasModels.value = list.length > 0
        
        if (row.selfInstanceId) {
          const boundInst = instances.value.find(ins => String(ins.instanceId) === String(row.selfInstanceId))
          const boundModel = boundInst ? findModelById(boundInst.modelId) : null
          if (boundInst && boundModel && String(boundModel.categoryId || '') === componentCategoryId) {
            componentSelectedModelId.value = boundInst.modelId
            await onComponentModelSelect(boundInst.modelId)
            componentActionForm.value.selfInstanceId = String(row.selfInstanceId)
          }
        }
      }
    } catch {
      componentCategoryModels.value = []
      componentCategoryHasModels.value = false
    } finally {
      componentCategoryModelLoading.value = false
    }
  } else {
    componentCategoryModels.value = []
    componentCategoryHasModels.value = false
  }

  componentActionVisible.value = true
}

const onComponentModelSelect = async (modelId: string) => {
  componentSelectedModelId.value = modelId
  componentCandidateInstances.value = []
  if (!modelId) return
  try {
    const res = await axios.get('/api/device/instance/page', {
      params: { pageNo: 1, pageSize: 100, modelId, lifecycleStatus: 'IN_USE' }
    })
    if (res.data?.success) {
      componentCandidateInstances.value = (res.data.data?.records || []).map(normalizeInstance)
        .filter(ins => String(ins.instanceId) !== String(activeInstance.value?.instanceId))
    }
  } catch (err: any) {
    ElMessage.error('加载模型设备实例失败')
  }
}

const submitComponentAction = async () => {
  if (!activeComponent.value?.id || activeInstanceRetired.value) return
  const specification: any = {}
  asArray(componentActionForm.value.specificationPairs).forEach(pair => {
    const k = pair.key?.trim()
    const v = pair.value?.trim()
    if (k) {
      if (/^(true|false)$/i.test(v)) {
        specification[k] = v.toLowerCase() === 'true'
      } else if (/^\d+$/.test(v)) {
        specification[k] = parseInt(v, 10)
      } else if (/^\d+\.\d+$/.test(v)) {
        specification[k] = parseFloat(v)
      } else {
        specification[k] = v
      }
    }
  })
  savingComponent.value = true
  try {
    const payload = {
      componentName: componentActionForm.value.componentName,
      selfInstanceId: componentActionForm.value.selfInstanceId ? Number(componentActionForm.value.selfInstanceId) : null,
      remark: componentActionForm.value.remark?.trim() || null,
      specification
    }
    const action = componentActionMode.value === 'replace' ? 'replace' : 'configure'
    const res = await axios.post('/api/device/component/' + activeComponent.value.id + '/' + action, payload)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '保存组件失败')
      return
    }
    componentActionVisible.value = false
    await loadComponents()
    ElMessage.success(componentActionMode.value === 'replace' ? '组件已更换' : '组件已配置')
  } finally {
    savingComponent.value = false
  }
}

const markPendingReplacement = async (row: any) => {
  if (!row?.id || activeInstanceRetired.value) return
  try {
    const { value } = await ElMessageBox.prompt('请填写待更换原因或维修说明', '标记待更换', {
      inputType: 'textarea',
      inputPlaceholder: '例如：读数漂移，等待采购替代件',
      inputValidator: (value: string) => value?.trim() ? true : '备注不能为空'
    })
    const res = await axios.post(`/api/device/component/${row.id}/mark-pending-replacement`, { remark: value.trim() })
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '标记待更换失败')
      return
    }
    await loadComponents()
    ElMessage.success('组件已标记为待更换')
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '标记待更换异常')
  }
}

const showComponentHistory = async (row: any) => {
  if (!row?.id) return
  componentHistoryVisible.value = true
  loadingComponentHistory.value = true
  try {
    const res = await axios.get('/api/device/component/' + row.id + '/history')
    if (res.data?.success) {
      componentHistoryRows.value = res.data.data || []
    } else {
      componentHistoryRows.value = []
      ElMessage.error(res.data?.message || '加载组件历史失败')
    }
  } catch (error: any) {
    componentHistoryRows.value = []
    ElMessage.error(error.response?.data?.message || '加载组件历史失败')
  } finally {
    loadingComponentHistory.value = false
  }
}

const loadDataSets = async () => {
  if (!activeInstance.value) return
  loadingDataSets.value = true
  try {
    const res = await axios.get('/api/data/index/list', {
      params: { deviceInstanceId: activeInstance.value.instanceId }
    })
    if (res.data?.success) {
      instanceDataSets.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载数据集失败')
  } finally {
    loadingDataSets.value = false
  }
}

const createDataSetForInstance = async () => {
  if (!activeInstance.value || activeInstanceRetired.value) return
  if (!datasetCreateForm.value.templateId) {
    ElMessage.warning('请选择数据模板')
    return
  }
  creatingDataSet.value = true
  try {
    const template = dataTemplates.value.find(tpl => String(templateIdOf(tpl)) === String(datasetCreateForm.value.templateId))
    const res = await axios.post('/api/data/index/create-dataset', {
      templateId: datasetCreateForm.value.templateId,
      deviceInstanceId: activeInstance.value.instanceId,
      dataDesc: datasetCreateForm.value.dataDesc || (activeInstance.value.instanceName + ' - ' + (template?.templateName || '自定义数据表'))
    })
    if (res.data?.success) {
      ElMessage.success('数据表创建成功')
      datasetCreateForm.value = { templateId: '', dataDesc: '' }
      await loadDataSets()
    } else {
      ElMessage.error(res.data?.message || '创建失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '创建失败')
  } finally {
    creatingDataSet.value = false
  }
}

const deleteInstanceDataSet = async (row: any) => {
  const id = row?.id || row?.dataIndexId
  if (!id) return
  try {
    const res = await axios.delete('/api/data/index/delete/' + id)
    if (res.data?.success) {
      ElMessage.success('数据表已删除')
      await loadDataSets()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

watch(drawerVisible, (visible) => {
  if (visible && !activeInstanceRetired.value) {
    startPolling()
  } else {
    stopPolling()
  }
})

const devicePointLabel = (point: AdapterDevicePoint) => {
  return point.description ? `${point.devicePoint} · ${point.description}` : point.devicePoint
}

const onAdapterChangeInDrawer = async () => {
  if (!activeInstance.value) return
  activeInstance.value.boundDevicePoint = ''
  await loadDevicePoints(activeInstance.value.boundAdapterName || '', 'drawer')
}

const categoryNameById = (id: any) => categoriesMap.value[String(id)] || ''
const instanceNameById = (id: any) => instances.value.find(item => String(item.instanceId) === String(id))?.instanceName || (id ? String(id) : '-')

function normalizeModel(raw: any): DeviceModel {
  return {
    ...raw,
    modelId: String(raw.modelId || raw.id || ''),
    modelName: raw.modelName || raw.name || '',
    deviceCategory: raw.deviceCategory || raw.categoryName || '',
    capabilitySpec: raw.capabilitySpec || {
      attributes: raw.attributes || [],
      capabilities: raw.capabilities || [],
      adapterContract: raw.adapterContract || { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
    }
  }
}

function normalizeInstance(raw: any): DeviceInstance {
  const instanceConfig = raw.instanceConfig || {}
  const boundAdapterName = raw.boundAdapterName || instanceConfig.boundAdapterName || ''
  const boundDevicePoint = raw.boundDevicePoint || instanceConfig.boundDevicePoint || ''
  return {
    ...raw,
    instanceId: String(raw.instanceId || raw.id || ''),
    modelId: String(raw.modelId || raw.deviceModelId || ''),
    stateMachineId: raw.stateMachineId || `${raw.modelId || raw.deviceModelId || ''}StateMachine`,
    instanceName: raw.instanceName || raw.name || '',
    boundAdapterName,
    boundDevicePoint,
    instanceConfig,
    lifecycleStatus: raw.lifecycleStatus,
    isOnline: raw.isOnline === true || raw.onlineStatus === 'ONLINE'
  }
}

const templateIdOf = (template: any) => String(template?.templateId || template?.id || '')

function findModelById(modelId: string) {
  if (!modelId) return null
  return modelOptions.value.find(v => v.modelId === modelId) || models.value.find(v => v.modelId === modelId) || null
}

const openCreateDrawer = () => {
  wizardSelectedCategoryId.value = ''
  wizardModelId.value = ''
  wizardCategoryModels.value = []
  wizardAdapterName.value = ''
  wizardDevicePoints.value = []
  wizardDevicePoint.value = ''
  wizardInstanceName.value = ''
  wizardAssetInfo.value = {
    serialNumber: '',
    purchaseDate: '',
    instalDate: '',
    location: '',
    notes: ''
  }
  createDrawerVisible.value = true
  if (!modelOptions.value.length) {
    loadModelOptions()
  }
  if (!adapterOptions.value.length) {
    loadAdapters()
  }
}

const onWizardCategoryChange = async (catId: any) => {
  wizardModelId.value = ''
  wizardCategoryModels.value = []
  if (!catId) return
  try {
    const res = await axios.get('/api/device/model/page', {
      params: { pageNo: 1, pageSize: 100, categoryId: catId }
    })
    if (res.data?.success) {
      wizardCategoryModels.value = (res.data.data?.records || []).map(normalizeModel)
    }
  } catch (err: any) {
    ElMessage.error('加载该类别的模型列表失败')
  }
}

const onWizardModelChange = async () => {
  wizardAdapterName.value = ''
  wizardDevicePoint.value = ''
  wizardDevicePoints.value = []
  const model = wizardModel.value
  const contractAdapterName = model?.capabilitySpec?.adapterContract?.config?.adapterName || ''
  if (contractAdapterName) {
    wizardAdapterName.value = contractAdapterName
    await loadWizardDevicePoints()
  }
}

const loadWizardDevicePoints = async () => {
  wizardDevicePoints.value = []
  if (!wizardAdapterName.value) return
  pointsLoading.value = true
  try {
    const model = wizardModel.value
    const adapterConfig = model?.capabilitySpec?.adapterContract?.config || {}
    const categoryName = adapterConfig.categoryName || undefined
    const params = { categoryName }
    const res = await axios.get(`/api/adapter/index/${encodeURIComponent(wizardAdapterName.value)}/device-points`, { params })
    if (res.data?.success) {
      wizardDevicePoints.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载 Adapter 设备点失败')
  } finally {
    pointsLoading.value = false
  }
}

const onWizardAdapterChange = async () => {
  wizardDevicePoint.value = ''
  await loadWizardDevicePoints()
}

const submitCreate = async () => {
  if (!wizardInstanceName.value.trim()) {
    ElMessage.warning('请填写实例名称')
    return
  }
  creating.value = true
  try {
    const asset = {
      serialNumber: wizardAssetInfo.value.serialNumber,
      purchaseDate: wizardAssetInfo.value.purchaseDate,
      instalDate: wizardAssetInfo.value.instalDate,
      location: wizardAssetInfo.value.location,
      notes: wizardAssetInfo.value.notes
    }

    const modelConstraints = asArray(wizardModel.value?.intrinsicConstraints || wizardModel.value?.intrinsicConstraint)
    const constraints = modelConstraints.map((c: any) => ({
      objectAttributeName: c.objectAttributeName || '',
      operator: normalizeConstraintOperator(c.operator || '<='),
      boundaryValue: c.boundaryValue != null ? c.boundaryValue : null,
      unit: c.unit || '',
      violationStateName: c.violationStateName || '',
      description: c.description || ''
    }))

    const payload: any = {
      instanceId: '',
      modelId: wizardModelId.value,
      stateMachineId: wizardModelId.value + 'StateMachine',
      instanceName: wizardInstanceName.value.trim(),
      boundAdapterName: wizardAdapterName.value,
      boundDevicePoint: wizardDevicePoint.value,
      instanceConfig: {
        assetInfo: asset,
        intrinsicConstraints: constraints
      },
      isOnline: false
    }

    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('设备添加成功')
      createDrawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '添加失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '添加失败')
  } finally {
    creating.value = false
  }
}

const canSubmitCreate = computed(() => {
  return !!wizardModelId.value && !!wizardAdapterName.value && !!wizardDevicePoint.value && !!wizardInstanceName.value.trim()
})

const wizardPreviewAttributes = computed(() => {
  return asArray(wizardModel.value?.capabilitySpec?.attributes || wizardModel.value?.attributes)
})

const wizardPreviewCapabilities = computed(() => {
  return normalizeManualControlCapabilities(
    asArray(wizardModel.value?.capabilitySpec?.capabilities || wizardModel.value?.capabilities)
  )
})

const wizardPreviewStates = computed(() => {
  const opState = wizardModel.value?.opState || wizardModel.value?.capabilitySpec?.operationStateSpace
  return asArray(opState?.states)
})

const formatTime = (time: any) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

const copyText = (text: string) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已成功复制实例 ID')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

const getConsoleStorageKey = (instanceId?: string | number) => `smartlab_console_logs_${instanceId || 'default'}`

const loadPersistedConsoleLogs = (instanceId?: string | number): ConsoleLogEntry[] => {
  if (!instanceId) return []
  try {
    const raw = localStorage.getItem(getConsoleStorageKey(instanceId))
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const savePersistedConsoleLogs = (instanceId?: string | number, logs?: ConsoleLogEntry[]) => {
  if (!instanceId) return
  try {
    const capped = (logs || []).slice(-150)
    localStorage.setItem(getConsoleStorageKey(instanceId), JSON.stringify(capped))
  } catch {
    // ignore
  }
}

const appendConsoleLog = (
  tag: '已投递' | '执行中' | '完成' | '失败' | '中止' | '系统' | '下发' | '成功',
  type: 'send' | 'running' | 'success' | 'fail' | 'info',
  text: string
) => {
  const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  const newEntry: ConsoleLogEntry = { time, tag, type, text }
  consoleLogs.value.push(newEntry)
  if (activeInstance.value?.instanceId) {
    savePersistedConsoleLogs(activeInstance.value.instanceId, consoleLogs.value)
  }
  nextTick(() => {
    if (consoleBodyRef.value) {
      consoleBodyRef.value.scrollTop = consoleBodyRef.value.scrollHeight
    }
  })
}

const clearConsoleLogs = () => {
  consoleLogs.value = []
  if (activeInstance.value?.instanceId) {
    localStorage.removeItem(getConsoleStorageKey(activeInstance.value.instanceId))
  }
}

const resettingControl = ref(false)
const abortingControl = ref(false)

const handleAbortCommand = async () => {
  if (!activeInstance.value) return
  try {
    await ElMessageBox.confirm(
      '确定要向物理设备下发终止信号 (MANUAL_EXECUTE_ABORT) 吗？物理设备将执行安全停机并退出当前指令周期。',
      '终止执行确认',
      { confirmButtonText: '确定终止', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  abortingControl.value = true
  appendConsoleLog('中止', 'fail', '已向物理设备发送终止信号 (MANUAL_EXECUTE_ABORT)，等待安全停机...')
  try {
    const res = await axios.post(`/api/device/instance/control/${activeInstance.value.instanceId}`, {
      signalName: 'MANUAL_EXECUTE_ABORT'
    })
    if (res.data?.success) {
      ElMessage.success('终止信号已下发')
      fetchSnapshot()
    } else {
      const errMsg = res.data?.message || '终止请求被拒'
      appendConsoleLog('失败', 'fail', `终止被拒: ${errMsg}`)
      ElMessage.error(`终止失败: ${errMsg}`)
    }
  } catch (err: any) {
    const errMsg = err.response?.data?.message || err.message || '网络连接异常'
    appendConsoleLog('失败', 'fail', `终止异常: ${errMsg}`)
    ElMessage.error(`网络错误: ${errMsg}`)
  } finally {
    abortingControl.value = false
  }
}

const handleForceResetCommand = async () => {
  if (!activeInstance.value) return
  if (activeInstanceCommandState.value === 'IDLE') {
    ElMessage.info('当前状态机处于空闲就绪态 (IDLE)，无需人工复位')
    return
  }
  try {
    await ElMessageBox.confirm(
      '【人工复位提示】此操作将向设备状态机下发 MANUAL_EXECUTE_RESET 信号，强制复位指令生命周期并将状态机重置为 IDLE 空闲就绪态。是否确认复位？',
      '人工复位确认',
      { confirmButtonText: '确认复位', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  resettingControl.value = true
  try {
    const res = await axios.post(`/api/device/instance/control/${activeInstance.value.instanceId}`, {
      signalName: 'MANUAL_EXECUTE_RESET'
    })
    if (res.data?.success) {
      ElMessage.success('已成功人工复位')
      appendConsoleLog('系统', 'info', '已发送人工复位信号 (MANUAL_EXECUTE_RESET)，状态机已恢复空闲就绪 (IDLE)')
      fetchSnapshot()
    } else {
      ElMessage.error(`复位失败: ${res.data?.message || '未知错误'}`)
    }
  } catch (err: any) {
    ElMessage.error(`复位请求失败: ${err.response?.data?.message || err.message}`)
  } finally {
    resettingControl.value = false
  }
}

const sendManualCommand = async () => {
  if (!activeInstance.value || activeInstanceRetired.value || !controlCapabilityName.value) return
  if (!activeInstanceIsOnline.value) {
    ElMessage.warning('物理 Adapter 处于离线状态 (OFFLINE)，无法下发指令')
    appendConsoleLog('系统', 'fail', '物理 Adapter 当前离线，已阻断指令下发')
    return
  }
  sendingControl.value = true
  
  const capability = activeControlCapability.value
  const params = buildControlParameters()

  try {
    const res = await axios.post(`/api/device/instance/control/${activeInstance.value.instanceId}`, {
      capabilityName: controlCapabilityName.value,
      signalName: manualControlSignals[0] || 'MANUAL_EXECUTE_START',
      parameters: params
    })
    
    if (res.data?.success) {
      fetchSnapshot()
    } else {
      const errMsg = res.data?.message || '未知异常'
      appendConsoleLog('失败', 'fail', `指令发送被拒: ${errMsg}`)
      ElMessage.error(`下发失败: ${errMsg}`)
    }
  } catch (err: any) {
    const errMsg = err.response?.data?.message || err.message || '网络连接异常'
    appendConsoleLog('失败', 'fail', `通信异常: ${errMsg}`)
    ElMessage.error(`网络错误: ${errMsg}`)
  } finally {
    sendingControl.value = false
  }
}

onMounted(() => {
  loadProtocolMetadata().catch(() => {})
  loadData()
})
onUnmounted(() => {
  stopPolling()
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
})
</script>

<style scoped>
/* ── 一体化无界工作台基准 ── */
.instance-workbench {
  display: flex;
  height: calc(100vh - 50px);
  overflow: hidden;
  background: var(--sl-bg-page);
}

.workbench-sidebar {
  width: 270px;
  flex-shrink: 0;
  border-right: 1px solid var(--sl-border-base);
  background: #ffffff;
}

.workbench-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
}

/* ── 顶部工具栏 ── */
.workbench-header {
  padding: 10px 16px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  gap: 12px;
}
.header-left { display: flex; flex-direction: column; gap: 2px; }
.header-title { margin: 0; font-size: 15px; font-weight: 700; color: var(--sl-text-heading); }
.header-subtitle { font-size: 11.5px; color: var(--sl-text-secondary); }
.header-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.lifecycle-filter { width: 125px; }
.instance-search { width: 220px; }

/* ── 表格全铺满容器 ── */
.table-scroll-container {
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-card {
  flex: 1;
  min-height: 0;
  border: none;
  border-radius: 0;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: none;
}

.instance-table {
  flex: 1;
  width: 100%;
}
.instance-table :deep(.el-table__row) {
  cursor: pointer;
}
.instance-table :deep(.el-table__row:hover) {
  background: var(--sl-bg-hover) !important;
}

.instance-name-cell { display: flex; flex-direction: column; gap: 2px; }
.inst-title { font-size: 13px; font-weight: 600; color: var(--sl-text-heading); }
.inst-id-sub { font-size: 11px; color: var(--sl-text-secondary); }
.model-name-text { font-size: 12.5px; color: var(--sl-text-body); font-weight: 500; }

.binding-cell { display: flex; flex-direction: column; gap: 3px; font-size: 11.5px; }
.binding-item { display: flex; align-items: center; gap: 6px; }
.binding-k { color: var(--sl-text-secondary); font-weight: 600; min-width: 52px; }
.binding-v { color: var(--sl-text-body); }

.topic-compact-cell { display: flex; flex-direction: column; gap: 2px; }
.topic-item { display: flex; align-items: center; gap: 6px; font-size: 11px; }
.topic-lbl { color: var(--sl-text-secondary); font-weight: 600; min-width: 36px; flex-shrink: 0; }
.topic-code { color: var(--sl-text-heading); font-family: var(--sl-font-mono); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ── 状态指示灯 ── */
.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  font-weight: 600;
}
.status-indicator .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.status-indicator.in-use { color: var(--sl-success); }
.status-indicator.in-use .dot { background: var(--sl-success); }
.status-indicator.retired { color: var(--sl-text-secondary); }
.status-indicator.retired .dot { background: var(--sl-text-disabled); }
.status-indicator.online { color: #0284c7; }
.status-indicator.online .dot { background: #0284c7; box-shadow: 0 0 6px rgba(2, 132, 199, 0.5); }
.status-indicator.offline { color: var(--sl-text-disabled); }
.status-indicator.offline .dot { background: var(--sl-text-disabled); }

.row-actions { display: flex; align-items: center; justify-content: center; gap: 8px; }
.pager-wrap {
  padding: 6px 12px;
  background: #ffffff;
  border-top: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

/* ── 运行监控与详细配置抽屉 ── */
.instance-detail-drawer :deep(.el-drawer__body) {
  padding: 0 !important;
  overflow: hidden;
  background: var(--sl-bg-page);
}

.drawer-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.drawer-custom-head {
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base);
  flex-shrink: 0;
}

.head-top-row {
  padding: 10px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--sl-border-subtle);
  gap: 12px;
}

.head-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.inst-type-tag {
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  border: 1px solid var(--sl-primary-border);
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 700;
  border-radius: var(--sl-radius-sm);
  flex-shrink: 0;
}
.head-title-text {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--sl-text-heading);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.point-badge {
  background: #f1f5f9;
  color: var(--sl-text-secondary);
  padding: 1px 6px;
  font-size: 11.5px;
  border-radius: 4px;
  font-weight: 600;
}
.copy-id-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--sl-text-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 4px;
  font-size: 11px;
  border-radius: 4px;
}
.copy-id-btn:hover {
  background: #f1f5f9;
  color: var(--sl-primary);
}

.head-status-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.head-sep { color: var(--sl-border-input); }
.close-drawer-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: var(--sl-text-secondary);
  border-radius: 4px;
  display: inline-flex;
  font-size: 16px;
}
.close-drawer-btn:hover { background: #f1f5f9; color: var(--sl-text-heading); }

/* ── 35px 像素级高度锁定指标横幅 ── */
.drawer-metric-ribbon {
  height: 35px;
  padding: 0 16px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  align-items: center;
  background: #fafbfc;
}
.m-col { display: flex; align-items: baseline; gap: 6px; min-width: 0; }
.m-lbl { font-size: 11px; color: var(--sl-text-secondary); flex-shrink: 0; }
.m-val { font-size: 12.5px; font-weight: 600; color: var(--sl-text-heading); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-val.highlight { color: var(--sl-primary); }

.retired-alert-bar {
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
  padding: 6px 16px;
  font-size: 12px;
  color: #b45309;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.alert-icon { font-size: 14px; flex-shrink: 0; }

.drawer-tabs-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.instance-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.instance-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base);
  flex-shrink: 0;
}
.instance-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 16px;
}

.tab-pane-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── 运行状态 Tab (两大卡片 + 双栏子卡片) ── */
.holistic-section-card {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  box-shadow: var(--sl-shadow-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.section-card-head {
  padding: 8px 12px;
  background: #fafbfc;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-card-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.sec-title-text {
  font-size: 13px;
  font-weight: 700;
  color: var(--sl-text-heading);
}

.sec-desc-text {
  font-size: 11.5px;
  color: var(--sl-text-secondary);
}

.card-head-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-card-body {
  padding: 12px;
}

.pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--sl-success);
  box-shadow: 0 0 6px rgba(22, 163, 74, 0.6);
  animation: blink 1.6s infinite alternate;
}
.pulse-dot.retired { background: var(--sl-text-disabled); animation: none; box-shadow: none; }
.last-time { color: var(--sl-text-secondary); font-size: 11px; }

.telemetry-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}
.kpi-card {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-left: 3px solid var(--sl-primary);
  border-radius: var(--sl-radius-sm);
  padding: 10px 12px;
  box-shadow: var(--sl-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kpi-label { display: flex; align-items: center; gap: 6px; }
.kpi-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--sl-primary); }
.kpi-label-text { font-size: 12px; font-weight: 600; color: var(--sl-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kpi-value-row { display: flex; align-items: baseline; gap: 4px; margin: 2px 0; }
.kpi-val { font-size: 22px; font-weight: 700; color: var(--sl-text-heading); line-height: 1.1; }
.kpi-unit { font-size: 12px; color: var(--sl-text-secondary); font-weight: 600; }

/* 状态机双栏拆分 */
.statemachine-body-split {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
}

.statemachine-subcard {
  background: #f8fafc;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.subcard-header {
  padding: 6px 10px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.subcard-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--sl-text-heading);
}

.subcard-badge {
  font-size: 10.5px;
  font-family: var(--sl-font-mono);
  color: var(--sl-text-secondary);
  background: #f1f5f9;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 600;
}

.cmd-content-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
  min-height: 110px;
}

.cmd-state-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cmd-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.cmd-dot.idle { background: #64748b; }
.cmd-dot.running { background: #0284c7; box-shadow: 0 0 6px rgba(2, 132, 199, 0.6); animation: blink 1.6s infinite alternate; }
.cmd-dot.completed { background: var(--sl-success); }
.cmd-dot.error { background: #ef4444; }

.cmd-state-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--sl-text-heading);
}

.cmd-state-desc {
  font-size: 11.5px;
  color: var(--sl-text-secondary);
  line-height: 1.45;
}

.op-regions-container {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.region-section-box {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: 4px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.operational-box {
  border-left: 3px solid var(--sl-success);
}

.exception-box {
  border-left: 3px solid #ef4444;
}

.region-box-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.region-type-pill {
  font-size: 11px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 3px;
}
.op-pill { background: #ecfdf5; color: #047857; border: 1px solid #a7f3d0; }
.err-pill { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }

.region-tags-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.region-row-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.region-name-badge {
  color: var(--sl-text-heading);
  font-weight: 700;
  font-size: 11.5px;
  background: #f1f5f9;
  border: 1px solid var(--sl-border-base);
  padding: 1px 7px;
  border-radius: 4px;
  letter-spacing: 0.2px;
}

.region-state-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.state-value-tag {
  font-weight: 600;
}

.exception-row-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}

.clear-exception-btn {
  font-size: 11.5px;
  height: 24px;
  padding: 0 10px;
}

.no-exception-box {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--sl-success);
  font-size: 11.5px;
  font-weight: 600;
  padding: 2px 0;
}

.safe-check-icon {
  font-size: 14px;
  color: var(--sl-success);
}

.empty-region-hint {
  font-size: 11.5px;
  color: var(--sl-text-secondary);
}

.sub-section-block {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  overflow: hidden;
}
.sub-section-head {
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.sub-section-head .head-title { font-size: 12.5px; font-weight: 700; color: var(--sl-text-heading); }
.sub-section-head .head-desc { font-size: 11px; color: var(--sl-text-secondary); }
.snapshot-val-highlight { font-weight: 600; color: var(--sl-primary); }

/* ── 控制调试 Tab ── */
.control-tab-workbench {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.control-left-panel {
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.manual-control-form {
  padding: 12px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.control-param-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 10px;
}
.control-action-bar {
  padding: 10px 12px;
  background: #f8fafc;
  border-top: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-start;
}

.btn-danger-outline {
  height: 32px;
  padding: 0 14px;
  border-radius: var(--sl-radius-sm);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #fff;
  border: 1px solid #f87171;
  color: #dc2626;
  transition: all var(--sl-transition-base);
}
.btn-danger-outline:hover {
  background: #fef2f2;
  border-color: #ef4444;
}
.btn-danger-outline:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.control-right-panel {
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: var(--sl-radius-sm);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.console-header {
  background: #1e293b;
  border-bottom: 1px solid #334155;
  padding: 7px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.console-title { display: flex; align-items: center; gap: 6px; color: #f8fafc; font-size: 12px; font-weight: 700; }
.console-dot { width: 6px; height: 6px; border-radius: 50%; background: #38bdf8; }
.console-actions { display: flex; align-items: center; gap: 12px; }
.console-reset-btn { color: #f59e0b; font-size: 11px; display: inline-flex; align-items: center; gap: 4px; }
.console-reset-btn:hover { color: #fbbf24; }
.console-clear-btn { color: #94a3b8; font-size: 11px; }
.console-clear-btn:hover { color: #f8fafc; }
.console-body {
  flex: 1;
  padding: 10px 12px;
  font-family: var(--sl-font-mono);
  font-size: 11px;
  color: #cbd5e1;
  line-height: 1.6;
  overflow-y: auto;
  max-height: 380px;
}
.console-line { margin-bottom: 4px; word-break: break-all; }
.console-line.send { color: #38bdf8; }
.console-line.running { color: #facc15; }
.console-line.success { color: #4ade80; }
.console-line.fail { color: #f87171; }
.console-line.abort { color: #fb923c; }
.console-line.info { color: #94a3b8; }
.console-time { color: #64748b; margin-right: 4px; }
.console-tag { font-weight: 700; margin-right: 4px; }
.console-empty { color: #475569; text-align: center; padding-top: 100px; font-style: italic; }

/* ── 归档数据与安全约束 ── */
.dataset-create-toolbar {
  padding: 8px 12px;
  background: #ffffff;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.dataset-form-inline { display: flex; align-items: center; gap: 8px; flex: 1; }

.constraint-view-container,
.constraint-edit-container {
  margin-top: 4px;
}

.constraint-prop-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.constraint-prop-cell .prop-name {
  font-weight: 500;
  color: var(--sl-text-primary, #1e293b);
}
.constraint-prop-cell .prop-code {
  font-size: 11px;
  color: var(--sl-text-muted, #94a3b8);
  font-family: monospace;
}
.threshold-value {
  font-family: var(--sl-font-mono, monospace);
  font-weight: 600;
  color: var(--sl-text-primary, #1e293b);
}
.threshold-unit {
  font-size: 12px;
  color: var(--sl-text-muted, #64748b);
  margin-left: 4px;
}

.bottom-action-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

/* ── 资产管理 ── */
.instance-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 12px;
  background: #ffffff;
  padding: 12px;
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
}
.instance-info-grid :deep(.el-form-item) { margin-bottom: 0; }
.form-wide { grid-column: 1 / -1; }
.mt-16 { margin-top: 16px; }

/* ── 抽屉常驻底部操作栏 ── */
.drawer-footer-bar {
  padding: 10px 16px;
  background: #ffffff;
  border-top: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.footer-right { display: flex; align-items: center; gap: 8px; }

/* ── 添加设备实例抽屉 (01~04 章节大卡片) ── */
.drawer-workbench-body { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.detail-anchor-layout { display: flex; height: 100%; overflow: hidden; }
.detail-anchor-menu {
  width: 148px;
  flex-shrink: 0;
  padding: 10px 8px;
  background: #f8fafc;
  border-right: 1px solid var(--sl-border-base) !important;
}
.detail-anchor-menu :deep(.el-anchor__link) {
  margin-bottom: 2px;
  padding: 6px 10px;
  border-radius: 4px;
  color: var(--sl-text-body);
  font-size: 12.5px;
  font-weight: 600;
}
.detail-anchor-menu :deep(.el-anchor__link.is-active) {
  background: var(--sl-primary-light);
  color: var(--sl-primary);
}
.edit-scroll-content { flex: 1; min-width: 0; }
.edit-scroll-content :deep(.el-scrollbar__view) { padding: 12px 16px 24px; }

.holistic-section-card {
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  margin-bottom: 12px;
  overflow: hidden;
  box-shadow: var(--sl-shadow-sm);
}
.section-card-head {
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-card-title { display: flex; align-items: center; gap: 6px; }
.sec-idx-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  border: 1px solid var(--sl-primary-border);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono);
}
.sec-title-text { font-size: 13px; font-weight: 700; color: var(--sl-text-heading); }
.sec-desc-text { font-size: 11.5px; color: var(--sl-text-secondary); }
.section-card-body { padding: 12px; }

.step3-preview { display: flex; flex-direction: column; gap: 12px; }
.preview-section { display: flex; flex-direction: column; gap: 6px; }
.preview-label { font-size: 12px; font-weight: 700; color: var(--sl-text-secondary); }
.preview-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.preview-empty { font-size: 11.5px; color: var(--sl-text-disabled); font-style: italic; }

.unified-drawer-footer {
  padding: 10px 16px;
  background: #ffffff;
  border-top: 1px solid var(--sl-border-base);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ── 组件弹窗与时间轴 ── */
.spec-editor-container { display: flex; flex-direction: column; gap: 6px; }
.spec-pair-row { display: flex; align-items: center; gap: 8px; }
.spec-sep { font-weight: 700; color: var(--sl-text-secondary); }
.dialog-footer-actions { display: flex; justify-content: flex-end; gap: 8px; }

.history-timeline-wrap { padding: 8px 12px; max-height: 420px; overflow-y: auto; }
.timeline-title { margin: 0 0 4px; font-size: 13px; color: var(--sl-text-heading); }
.timeline-desc { font-size: 12px; color: var(--sl-text-secondary); line-height: 1.6; display: flex; flex-direction: column; gap: 2px; }

/* ── 通用工具类 ── */
.mono { font-family: var(--sl-font-mono); }
.muted { color: var(--sl-text-disabled); font-size: 12px; }
.empty-wrap { padding: 60px 0; }
.empty-inline { font-size: 12px; color: var(--sl-text-disabled); padding: 8px 0; }
.field-hint { font-size: 11px; color: var(--sl-text-secondary); margin-top: 2px; }

@keyframes blink {
  0% { opacity: 0.35; }
  100% { opacity: 1; }
}
</style>
