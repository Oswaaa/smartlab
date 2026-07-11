<template>
  <div class="instance-page">
    <el-container class="layout">
      <!-- G2: 复用 DeviceModelTree.vue 侧边栏（只读模式） -->
      <DeviceModelTree
        v-model:keyword="sidebarKeyword"
        :categories="categories"
        :models="models"
        :selected-model-id="selectedModelId"
        :selected-category-id="selectedCategoryId"
        :loading="modelsLoading"
        :readonly="true"
        class="model-list-panel"
        @select-model="selectModel"
        @select-category="selectCategory"
      />

      <el-main class="content">
        <div class="main-header">
          <div>
            <h2>{{ selectedModelName }}</h2>
            <span class="subtitle">设备实例查看与配置</span>
          </div>
          <div class="actions">
            <el-input
              v-model="instanceKeyword"
              class="instance-search"
              size="small"
              clearable
              placeholder="搜索实例或设备SN"
              @input="onInstanceSearchInput"
            />
            <el-button :icon="Refresh" circle size="small" @click="loadData" title="刷新" />
            <el-button v-if="canCreateInstance" type="primary" class="add-device-trigger" @click="openCreateDrawer">
              <el-icon><Plus /></el-icon> 添加设备
            </el-button>
          </div>
        </div>

        <div class="instance-list-wrap" v-loading="loading">
          <el-table
            v-if="instances.length > 0"
            :data="instances"
            border
            stripe
            size="small"
            class="instance-table"
            row-key="instanceId"
            @row-click="viewDetails"
          >
            <el-table-column label="设备实例" min-width="180">
              <template #default="{ row }">
                <div class="instance-name-cell">
                  <strong>{{ row.instanceName }}</strong>
                  <span>实例ID {{ row.instanceId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="设备模型" min-width="160">
              <template #default="{ row }">{{ getModelName(row.modelId) }}</template>
            </el-table-column>
            <el-table-column label="Adapter / 设备点" min-width="220">
              <template #default="{ row }">
                <div class="binding-cell">
                  <span><b>Adapter</b>{{ row.boundAdapterName || '未分配' }}</span>
                  <span><b>设备点</b>{{ row.boundDevicePoint || '未分配' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="MQTT 主题" min-width="360">
              <template #default="{ row }">
                <div class="topic-cell">
                  <span v-for="topic in mqttTopicRows(row.boundAdapterName, row.boundDevicePoint)" :key="topic.type">
                    <b>{{ topic.label }}</b><code>{{ topic.topic || '-' }}</code>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="在线状态" width="105" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isOnline ? 'success' : 'info'" size="small" effect="plain">{{ row.isOnline ? '在线' : '离线' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-wrap">
            <el-empty description="暂无设备实例" :image-size="100" />
          </div>
        </div>
        <div class="instance-pagination">
          <el-pagination
            v-model:current-page="instancePageNo"
            v-model:page-size="instancePageSize"
            :page-sizes="[12, 24, 48, 96]"
            :total="instanceTotal"
            layout="total, sizes, prev, pager, next"
            background
            small
            @size-change="handleInstancePageSizeChange"
            @current-change="loadInstances"
          />
        </div>
      </el-main>
    </el-container>

    <!-- 设备实例详情 Drawer/Dialog (固定高度 75vh 并集成滚动条) -->
    <el-dialog
      v-model="drawerVisible"
      width="1010px"
      class="instance-detail-dialog"
      :destroy-on-close="true"
      @close="closeDrawer"
    >
      <template #header>
        <span style="font-weight: 700; color: #1e293b;">设备实例运行监控与详细配置</span>
      </template>
      <div v-if="activeInstance" class="drawer-body">
        <!-- G6: 实例详情页 Header 区域 -->
        <div class="instance-detail-header">
          <div class="header-primary">
            <h3>设备实例：{{ activeInstance.instanceName }}
              <span v-if="activeInstance.boundDevicePoint">（{{ activeInstance.boundDevicePoint }}）</span>
              <el-button link type="primary" :icon="CopyDocument" @click.stop="copyText(activeInstance.instanceId)" title="复制实例 ID" style="margin-left: 6px; padding: 0;">
                <span style="font-size: 11px; font-weight: normal; color: #94a3b8; font-family: monospace;">ID: {{ activeInstance.instanceId }}</span>
              </el-button>
            </h3>
            <div class="header-status">
              <span :class="['status-dot', activeInstance.isOnline ? 'online' : 'offline']"></span>
              <span style="font-weight: 600; color: #334155;">{{ activeInstance.isOnline ? '在线' : '离线' }}</span>
              <span v-if="snapshot?.currentOperationState" class="divider">/</span>
              <strong v-if="snapshot?.currentOperationState" style="color: #2563eb;">{{ snapshot.currentOperationState }}</strong>
            </div>
          </div>
          <div class="header-meta">
            <span>模型：{{ getModelName(activeInstance.modelId) }}</span>
            <span class="sep">|</span>
            <span>Adapter：{{ activeInstance.boundAdapterName || '未绑定' }}</span>
            <span v-if="activeInstance.boundDevicePoint" class="sep">|</span>
            <span v-if="activeInstance.boundDevicePoint">设备点：{{ activeInstance.boundDevicePoint }}</span>
            <span v-if="lastSnapshotTime !== '-'" class="sep">|</span>
            <span v-if="lastSnapshotTime !== '-'">最后通信: {{ lastSnapshotTime }}</span>
          </div>
        </div>

        <el-tabs v-model="activeTab" style="padding: 12px 20px 20px;">
          <!-- Tab 1: 运行状态 (最高优先级业务数据) -->
          <el-tab-pane label="运行状态" name="status">
            <div class="status-top">
              <el-tag type="success" size="small">实时状态监控（每 3 秒自动轮询）</el-tag>
              <span>上报时间: {{ lastSnapshotTime }}</span>
            </div>

            <!-- KPI 大尺寸属性指标卡网格 -->
            <div class="telemetry-kpi-grid" v-if="kpiAttributes.length">
              <div v-for="kpi in kpiAttributes" :key="kpi.key" class="kpi-card">
                <div class="kpi-label">
                  <span class="kpi-dot"></span>
                  {{ kpi.label }}
                </div>
                <div class="kpi-value-row">
                  <span class="kpi-val">{{ kpi.value ?? 'N/A' }}</span>
                  <span class="kpi-unit" v-if="kpi.unit && kpi.value != null">{{ kpi.unit }}</span>
                </div>
                <div class="kpi-meta">
                  属性标识: <code>{{ kpi.key }}</code>
                </div>
              </div>
            </div>

            <el-descriptions :column="2" border size="small" class="mb-12">
              <el-descriptions-item label="最近指令状态">
                <el-tag size="small" type="warning" effect="plain">{{ snapshot?.currentCommandState || '无历史指令' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="当前功能状态">
                <el-tag size="small" type="success" effect="plain">{{ snapshot?.currentOperationState || '已就绪' }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <section class="snapshot-section">
              <div class="section-caption">完整属性快照</div>
              <el-table :data="snapshotAttributeRows" border size="small" class="snapshot-table">
                <el-table-column label="模型属性" min-width="160" prop="label" />
                <el-table-column label="属性标识" min-width="150" prop="key" />
                <el-table-column label="数据类型" width="110" prop="dataType" />
                <el-table-column label="单位" width="90" prop="unit" />
                <el-table-column label="当前上报值" min-width="160">
                  <template #default="{ row }"><span class="mono">{{ row.value ?? 'N/A' }}</span></template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <!-- Tab 2: 控制调试 (大厂调试背板交互) -->
          <el-tab-pane v-if="canControlInstance" label="控制调试" name="control">
            <div class="control-tab-layout">
              <div class="control-left-form">
                <el-form label-position="top" size="small" class="manual-control-form">
                  <el-form-item label="选择设备功能 (Command)" required>
                    <el-select v-model="controlCommandId" style="width: 100%" placeholder="选择模型定义的功能" @change="resetControlParams">
                      <el-option
                        v-for="cmd in activeInstanceCommands"
                        :key="cmd.commandId"
                        :label="`${cmd.commandName || cmd.commandId} / Adapter: ${cmd.adapterCommandName || '-'}`"
                        :value="cmd.commandId"
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
                        controls-position="right"
                        style="width: 100%"
                      />
                      <el-input v-else v-model="controlParamValues[paramKey(param)]" :placeholder="param.dataType || 'STRING'" />
                      <div class="field-hint">数据类型：{{ param.dataType || '-' }}</div>
                    </el-form-item>
                  </div>
                  <div v-else class="empty-inline" style="margin: 10px 0;">该功能无需传参。</div>
                </el-form>
                <div class="footer-actions mt-12" style="justify-content: flex-start;">
                  <el-button type="primary" size="small" :loading="sendingControl" @click="sendManualCommand">
                    下发调试指令
                  </el-button>
                </div>
              </div>

              <!-- 黑色控制台调试日志反馈 -->
              <div class="control-right-console">
                <div class="console-header">
                  <span>下发反馈控制台</span>
                  <el-button link type="primary" size="small" @click="clearConsoleLogs" style="padding: 0;">清空</el-button>
                </div>
                <div class="console-body" ref="consoleBodyRef">
                  <div v-for="(log, idx) in consoleLogs" :key="idx" :class="['console-line', log.type]">
                    <span class="console-time">[{{ log.time }}]</span>
                    <span class="console-tag">[{{ log.tag }}]</span>
                    <span class="console-text">{{ log.text }}</span>
                  </div>
                  <div v-if="consoleLogs.length === 0" class="console-empty">
                    暂无下发记录，请在左侧配置参数并点击发送...
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 3: 结构拓扑 -->
          <el-tab-pane label="结构拓扑" name="components">
            <section class="component-create-panel" v-if="false">
              <el-input v-model="componentForm.componentName" size="small" placeholder="新增槽位名称" />
              <el-select v-model="componentForm.categoryId" size="small" clearable filterable placeholder="组件类别">
                <el-option v-for="cat in categories" :key="String(cat.id)" :label="cat.categoryName" :value="String(cat.id)" />
              </el-select>
              <el-button type="primary" size="small" :loading="savingComponent" @click="saveComponent">新增槽位</el-button>
            </section>
            <el-table :data="instanceComponents" border size="small" v-loading="loadingComponents" class="component-table">
              <el-table-column label="组件槽位" min-width="150" prop="componentName" />
              <el-table-column label="类别" min-width="130">
                <template #default="{ row }">{{ categoryNameById(row.categoryId) || '-' }}</template>
              </el-table-column>
              <el-table-column label="绑定实例" min-width="150">
                <template #default="{ row }">{{ instanceNameById(row.selfInstanceId) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="componentStatusType(row.status)" effect="plain">{{ row.status || '未配置' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="安装时间" min-width="150">
                <template #default="{ row }">{{ formatTime(row.installTime) }}</template>
              </el-table-column>
              <el-table-column label="规格" min-width="110">
                <template #default="{ row }">
                  <el-button link type="primary" size="small"
                    @click="showComponentSpecification(row)">
                    {{ componentSpecificationLabel(row.specification) }}
                  </el-button>
                </template>
              </el-table-column>

              <el-table-column v-if="canEditInstance" label="操作" width="230" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.status === '使用中'" link type="primary" size="small" @click="openComponentAction(row, 'configure')">配置</el-button>
                  <el-button v-if="row.status === '使用中'" link type="warning" size="small" @click="markPendingReplacement(row)">标记待更换</el-button>
                  <el-button v-if="row.status === '使用中'" link type="warning" size="small" @click="openComponentAction(row, 'replace')">直接更换</el-button>
                  <el-button v-if="row.status === '待更换'" link type="primary" size="small" @click="openComponentAction(row, 'replace')">安装新组件</el-button>
                  <el-button link size="small" @click="showComponentHistory(row)">历史</el-button>
                                  </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab 4: 归档数据 -->
          <el-tab-pane label="归档数据" name="datasets">
            <div v-if="canCreateDataset" class="dataset-create-panel">
              <el-select v-model="datasetCreateForm.templateId" size="small" filterable clearable placeholder="选择数据模板">
                <el-option v-for="tpl in availableDataTemplates" :key="templateIdOf(tpl)" :label="tpl.templateName || '未命名模板'" :value="templateIdOf(tpl)" />
              </el-select>
              <el-input v-model="datasetCreateForm.dataDesc" size="small" placeholder="数据集名称，例如：温度变化记录表" />
              <el-button type="primary" size="small" :loading="creatingDataSet" @click="createDataSetForInstance">关联建表</el-button>
            </div>
            <el-table :data="instanceDataSets" border size="small" v-loading="loadingDataSets">
              <el-table-column prop="id" label="数据集ID" width="90" />
              <el-table-column prop="dataDesc" label="数据集描述" min-width="160" />
              <el-table-column prop="dataTable" label="物理数据表名" min-width="180" />
              <el-table-column prop="createTime" label="创建时间" min-width="160">
                <template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</template>
              </el-table-column>
              <el-table-column v-if="canDeleteDataset" label="操作" width="90" align="center">
                <template #default="{ row }">
                  <el-popconfirm title="确认注销该归档数据表？物理存储将被清空。" @confirm="deleteInstanceDataSet(row)">
                    <template #reference>
                      <el-button link type="danger" size="small">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
            <div class="footer-actions mt-12">
              <el-button type="primary" size="small" @click="loadDataSets">刷新列表</el-button>
            </div>
          </el-tab-pane>

          <!-- Tab 5: 安全约束 -->
          <el-tab-pane label="安全约束" name="constraints">
            <div class="constraint-actions">
              <el-button v-if="canEditInstance" type="primary" plain size="small" @click="addConstraint">
                <el-icon><Plus /></el-icon> 添加监控约束规则
              </el-button>
            </div>
            <el-table :data="localConstraints" border size="small">
              <el-table-column label="监控物理属性" min-width="160">
                <template #default="{ row }">
                  <el-select v-model="row.objectAttributeName" size="small" style="width: 100%" placeholder="选择监控的物模型属性">
                    <el-option
                      v-for="prop in getModelAttributes(activeInstance.modelId)"
                      :key="prop.identifier || prop.name"
                      :label="prop.displayName || prop.name || prop.identifier"
                      :value="prop.identifier || prop.name"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="条件算子" width="110">
                <template #default="{ row }">
                  <el-select v-model="row.operator" size="small">
                    <el-option label="≤ (小于等于)" value="LTE" />
                    <el-option label="≥ (大于等于)" value="GTE" />
                    <el-option label="< (小于)" value="LT" />
                    <el-option label="> (大于)" value="GT" />
                    <el-option label="= (等于)" value="EQ" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="判定阈值" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.boundaryValue" size="small" style="width: 100%" controls-position="right" />
                </template>
              </el-table-column>
              <el-table-column label="物理单位" width="90">
                <template #default="{ row }">
                  <el-input v-model="row.unit" size="small" placeholder="℃" />
                </template>
              </el-table-column>
              <el-table-column label="违规转向状态" min-width="130">
                <template #default="{ row }">
                  <el-input v-model="row.violationStateName" size="small" placeholder="如: 超温故障" />
                </template>
              </el-table-column>
              <el-table-column label="约束描述" min-width="160">
                <template #default="{ row }">
                  <el-input v-model="row.description" size="small" placeholder="异常规则含义..." />
                </template>
              </el-table-column>
              <el-table-column v-if="canEditInstance" label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeConstraint($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="footer-actions mt-12">
              <el-button v-if="canEditInstance" type="primary" size="small" :loading="saving" @click="saveConstraints">保存约束</el-button>
            </div>
          </el-tab-pane>

          <!-- Tab 6: 资产管理 (资产部署与静态设置) -->
          <el-tab-pane label="资产管理" name="info">
            <el-form label-position="top" size="small" class="instance-info-grid">
              <el-form-item label="设备实例名称">
                <el-input v-model="activeInstance.instanceName" />
              </el-form-item>
              <el-form-item label="绑定物模型">
                <el-select v-model="activeInstance.modelId" style="width: 100%" :disabled="!!activeInstance.instanceId" @change="onModelChangeInDrawer">
                  <el-option v-for="m in models" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                </el-select>
              </el-form-item>
              <el-form-item label="物理 Adapter 代理">
                <el-select v-model="activeInstance.boundAdapterName" style="width: 100%" filterable @change="onAdapterChangeInDrawer">
                  <el-option v-for="adapter in adapterOptions" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
                </el-select>
              </el-form-item>
              <el-form-item label="Adapter 设备点位">
                <el-select v-model="activeInstance.boundDevicePoint" style="width: 100%" filterable :disabled="!activeInstance.boundAdapterName">
                  <el-option v-for="point in drawerDevicePointOptions" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
                </el-select>
              </el-form-item>

              <!-- 静态资产细节 -->
              <el-form-item label="资产序列号 (SN)">
                <el-input v-model="activeInstance.instanceConfig.assetInfo.serialNumber" placeholder="出厂 SN 唯一标识" />
              </el-form-item>
              <el-form-item label="部署实验室/位置">
                <el-input v-model="activeInstance.instanceConfig.assetInfo.location" placeholder="楼宇-房间-槽位" />
              </el-form-item>
              <el-form-item label="采购日期">
                <el-date-picker v-model="activeInstance.instanceConfig.assetInfo.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
              <el-form-item label="安装日期">
                <el-date-picker v-model="activeInstance.instanceConfig.assetInfo.instalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
              <el-form-item label="资产运维备注" class="form-wide">
                <el-input v-model="activeInstance.instanceConfig.assetInfo.notes" type="textarea" :rows="2" />
              </el-form-item>
            </el-form>

            <section class="topic-section">
              <div class="section-caption">MQTT 订阅/发布通信主题</div>
              <el-table :data="activeMqttTopicRows" border size="small" class="topic-table">
                <el-table-column label="通信用途" width="120" prop="label" />
                <el-table-column label="方向" width="140" prop="direction" />
                <el-table-column label="MQTT 物理主题">
                  <template #default="{ row }"><code>{{ row.topic || '-' }}</code></template>
                </el-table-column>
              </el-table>
            </section>

            <div class="footer-actions">
              <el-popconfirm v-if="canDeleteInstance" title="注销此物理设备？其孪生拓扑关系也将被物理删除！" @confirm="deleteInstance(activeInstance.instanceId)">
                <template #reference>
                  <el-button type="danger" plain size="small">注销设备实例</el-button>
                </template>
              </el-popconfirm>
              <el-button v-if="canEditInstance" type="primary" size="small" :loading="saving" @click="saveInstance">保存资产修改</el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

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
                <div v-for="(pair, index) in componentActionForm.specificationPairs" :key="index" style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
                  <el-input v-model="pair.key" placeholder="参数名 (如: 品牌)" style="flex: 1" />
                  <span>:</span>
                  <el-input v-model="pair.value" placeholder="参数值 (如: 罗氏)" style="flex: 1.2" />
                  <el-button link type="danger" :icon="Delete" @click="componentActionForm.specificationPairs.splice(index, 1)" />
                </div>
                <el-button type="primary" link :icon="Plus" size="small" style="padding: 0" @click="componentActionForm.specificationPairs.push({ key: '', value: '' })">
                  添加规格参数
                </el-button>
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
                <div v-for="(pair, index) in componentActionForm.specificationPairs" :key="index" style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
                  <el-input v-model="pair.key" placeholder="参数名 (如: 品牌)" style="flex: 1" />
                  <span>:</span>
                  <el-input v-model="pair.value" placeholder="参数值 (如: Roche)" style="flex: 1.2" />
                  <el-button link type="danger" :icon="Delete" @click="componentActionForm.specificationPairs.splice(index, 1)" />
                </div>
                <el-button type="primary" link :icon="Plus" size="small" style="padding: 0" @click="componentActionForm.specificationPairs.push({ key: '', value: '' })">
                  添加规格参数
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <el-button size="small" @click="componentActionVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="savingComponent" @click="submitComponentAction">保存</el-button>
      </template>
    </el-dialog>

    <!-- 更换历史时间轴弹窗 -->
    <el-dialog v-model="componentHistoryVisible" title="组件更换与维修历史" width="600px" append-to-body>
      <div v-loading="loadingComponentHistory" style="padding: 10px 20px;">
        <el-timeline v-if="componentHistoryRows.length > 0">
          <el-timeline-item
            v-for="(row, idx) in componentHistoryRows"
            :key="idx"
            :timestamp="formatTime(row.installTime)"
            :type="row.status === '使用中' ? 'primary' : 'info'"
          >
            <h4 style="margin: 0; color: #1e293b;">插槽槽位: {{ row.componentName }}</h4>
            <p style="margin: 6px 0 0; font-size: 13px; color: #64748b; line-height: 1.6;">
              绑定实例: <b>{{ instanceNameById(row.selfInstanceId) }}</b> <br/>
              更换状态: <el-tag size="small" :type="componentStatusType(row.status)" effect="plain">{{ row.status }}</el-tag> <br/>
              备注: {{ row.remark || '无' }} <br/>
              规格: <el-button link type="primary" size="small" @click="showComponentSpecification(row)">查看详情</el-button> <br/>
              <span v-if="row.predecessorId">前置坏件组件 ID: <code>{{ row.predecessorId }}</code></span>
            </p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无该槽位的更换历史记录" />
      </div>
    </el-dialog>

    <!-- G1: 添加设备实例侧边滚动面板 (Drawer 锚点导航形式) -->
    <el-drawer
      v-model="createDrawerVisible"
      title="添加设备实例"
      direction="rtl"
      size="78%"
      destroy-on-close
      class="instance-create-drawer"
    >
      <div class="drawer-body">
        <div class="detail-anchor-layout instance-edit-workbench">
          <!-- 锚点菜单 -->
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
            <div id="create-model" class="anchor-section industrial-section">
              <h2 class="section-heading"><span class="section-index">01</span>选择模型</h2>
              <section class="drawer-section">
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
                  <el-form-item label="设备模型" required>
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
              </section>
            </div>

            <!-- 02 点位绑定 -->
            <div id="create-bind" class="anchor-section industrial-section">
              <h2 class="section-heading"><span class="section-index">02</span>物理点位绑定</h2>
              <section class="drawer-section">
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
                      placeholder="选择具体设备通道点位(例如 Reactor_01)"
                    >
                      <el-option v-for="point in wizardDevicePoints" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
                    </el-select>
                  </el-form-item>
                </el-form>
              </section>
            </div>

            <!-- 03 资产信息 -->
            <div id="create-asset" class="anchor-section industrial-section">
              <h2 class="section-heading"><span class="section-index">03</span>资产信息</h2>
              <section class="drawer-section">
                <el-form :model="wizardAssetInfo" label-position="top" size="small" class="instance-info-grid">
                  <el-form-item label="设备实例名称" required>
                    <el-input v-model="wizardInstanceName" placeholder="例如：1号反应釜" />
                  </el-form-item>
                  <el-form-item label="出厂序列号 (SN)">
                    <el-input v-model="wizardAssetInfo.serialNumber" placeholder="SN 编码" />
                  </el-form-item>
                  <el-form-item label="部署具体位置">
                    <el-input v-model="wizardAssetInfo.location" placeholder="例如：合成实验室" />
                  </el-form-item>
                  <el-form-item label="采购日期">
                    <el-date-picker v-model="wizardAssetInfo.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                  </el-form-item>
                  <el-form-item label="安装日期">
                    <el-date-picker v-model="wizardAssetInfo.instalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                  </el-form-item>
                  <el-form-item label="备注" class="form-wide">
                    <el-input v-model="wizardAssetInfo.notes" type="textarea" :rows="2" placeholder="备注信息..." />
                  </el-form-item>
                </el-form>
              </section>
            </div>

            <!-- 04 契约预览 -->
            <div id="create-preview" class="anchor-section industrial-section">
              <h2 class="section-heading"><span class="section-index">04</span>物模型契约预览</h2>
              <section class="drawer-section">
                <div class="step3-preview">
                  <div class="preview-section">
                    <span class="preview-label">继承的数据属性 (Attributes)</span>
                    <div class="preview-tags" v-if="wizardPreviewAttributes.length">
                      <el-tag v-for="attr in wizardPreviewAttributes" :key="attr.name" size="small" effect="plain" type="info">
                        {{ attr.displayName || attr.name }} ({{ attr.dataType }}{{ attr.unit ? ', ' + attr.unit : '' }})
                      </el-tag>
                    </div>
                    <div v-else class="preview-empty">无属性定义</div>
                  </div>

                  <div class="preview-section">
                    <span class="preview-label">继承的操作命令 (Commands)</span>
                    <div class="preview-tags" v-if="wizardPreviewCommands.length">
                      <el-tag v-for="cmd in wizardPreviewCommands" :key="cmd.commandId" size="small" type="warning" effect="plain">
                        {{ cmd.commandName }}
                      </el-tag>
                    </div>
                    <div v-else class="preview-empty">无操作命令定义</div>
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
              </section>
            </div>
          </el-scrollbar>
        </div>
      </div>
      <template #footer>
        <div style="padding: 10px 20px; border-top: 1px solid #e2e8f0; display: flex; justify-content: flex-end; gap: 8px;">
          <el-button size="small" @click="createDrawerVisible = false">取消</el-button>
          <el-button size="small" type="primary" :loading="creating" :disabled="!canSubmitCreate" @click="submitCreate">确认并保存</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { Cpu, Plus, Refresh, Folder, FolderOpened, CaretRight, Check, Close, Document, List, CopyDocument, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { useAuthStore } from '../../stores/authStore'
import DeviceModelTree from './components/DeviceModelTree.vue'

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
  isOnline: boolean
}

interface DeviceSnapshot {
  instanceId: string
  currentCommandState?: string
  currentOperationState?: string
  latestAttributes?: Record<string, any>
}

interface InstanceConstraint {
  objectAttributeName: string
  operator: 'LTE' | 'GTE' | 'LT' | 'GT' | 'EQ'
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
const loading = ref(false)
const modelsLoading = ref(false)
const saving = ref(false)
const creating = ref(false)
const sendingControl = ref(false)
const adapterLoading = ref(false)
const pointsLoading = ref(false)

const drawerVisible = ref(false)
const activeInstance = ref<DeviceInstance | null>(null)
const activeTab = ref('status') // 运行状态作为默认展现

const snapshot = ref<DeviceSnapshot | null>(null)
const lastSnapshotTime = ref('-')
let pollingTimer: any = null
const instancePageNo = ref(1)
const instancePageSize = ref(24)
const instanceTotal = ref(0)
let instanceLoadSeq = 0
let instanceSearchTimer: any = null

const localConstraints = ref<InstanceConstraint[]>([])
const controlCommandId = ref('')
const controlParamValues = ref<Record<string, any>>({})

// 调试下发控制台日志数据
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
const componentForm = ref({ componentName: '', categoryId: '', selfInstanceId: '', status: '未配置' })
const componentActionVisible = ref(false)
const componentActionMode = ref<'configure' | 'replace'>('configure')
const activeComponent = ref<any>(null)
const componentActionForm = ref({ componentName: '', selfInstanceId: '', remark: '', specificationPairs: [] as Array<{ key: string, value: string }> })
const componentHistoryVisible = ref(false)
const componentHistoryRows = ref<any[]>([])
const loadingComponentHistory = ref(false)

// G1: 新的滚动面板创建抽屉状态
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

// G2: 左侧类别树状态
const selectedCategoryId = ref<string>('')

const canCreateInstance = computed(() => authStore.hasPermission('device_instance:create'))
const canEditInstance = computed(() => authStore.hasPermission('device_instance:edit'))
const canDeleteInstance = computed(() => authStore.hasPermission('device_instance:delete'))
const canControlInstance = computed(() => authStore.hasPermission('device_instance:control'))
const canCreateDataset = computed(() => authStore.hasPermission('data_dataset:create'))
const canDeleteDataset = computed(() => authStore.hasPermission('data_dataset:delete'))

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

const activeInstanceCommands = computed(() => {
  const capabilities = asArray(activeInstanceModel.value?.capabilitySpec?.capabilities || activeInstanceModel.value?.capabilities)
  return capabilities.map((cap: any) => ({
    ...cap,
    commandId: cap.name || cap.commandId || cap.adapterCommandName,
    commandName: cap.displayName || cap.name || cap.commandId,
    adapterCommandName: cap.adapterCommandName || cap.commandName || cap.name,
    parameters: asArray(cap.parameters)
  })).filter((cap: any) => cap.commandId)
})

const activeControlCommand = computed(() => activeInstanceCommands.value.find((cmd: any) => cmd.commandId === controlCommandId.value) || null)
const activeControlParams = computed(() => asArray(activeControlCommand.value?.parameters).filter((param: any) => !param.internal))
const activeMqttTopicRows = computed(() => mqttTopicRows(activeInstance.value?.boundAdapterName, activeInstance.value?.boundDevicePoint))

// 大厂运行状态 - KPI 关键数据指标卡
const kpiAttributes = computed(() => {
  if (!activeInstance.value) return []
  const attrs = asArray(getModelAttributes(activeInstance.value.modelId))
  const snapshotMap = snapshotAttributes.value
  
  const mapped = attrs.map((attr: any) => {
    const candidates = [attr.name, attr.identifier, attr.displayName].filter(Boolean).map(String)
    const matchedKey = candidates.find(key => Object.prototype.hasOwnProperty.call(snapshotMap, key)) || candidates[0] || ''
    return {
      key: attr.name || attr.identifier || attr.displayName || '-',
      label: attr.displayName || attr.name || attr.identifier || '-',
      dataType: attr.dataType || '-',
      unit: attr.unit || '',
      value: matchedKey ? snapshotMap[matchedKey] : undefined,
      valueKind: attr.valueKind || 'CONTINUOUS'
    }
  })

  // 优先提取有测量值/属于连续值的属性作为 KPI 卡片
  const withValue = mapped.filter((attr: any) => attr.valueKind === 'CONTINUOUS' || attr.unit || attr.value !== undefined)
  if (withValue.length > 0) return withValue.slice(0, 4)
  return mapped.slice(0, 4)
})

const snapshotAttributeRows = computed(() => {
  if (!activeInstance.value) return []
  const attrs = asArray(getModelAttributes(activeInstance.value.modelId))
  const snapshotMap = snapshotAttributes.value
  const used = new Set<string>()
  const rows = attrs.map((attr: any) => {
    const candidates = [attr.name, attr.identifier, attr.displayName].filter(Boolean).map(String)
    const matchedKey = candidates.find(key => Object.prototype.hasOwnProperty.call(snapshotMap, key)) || candidates[0] || ''
    if (matchedKey) used.add(matchedKey)
    return {
      key: attr.name || attr.identifier || attr.displayName || '-',
      label: attr.displayName || attr.name || attr.identifier || '-',
      dataType: attr.dataType || '-',
      unit: attr.unit || '-',
      value: matchedKey ? snapshotMap[matchedKey] : undefined
    }
  })
  Object.keys(snapshotMap).forEach(key => {
    if (!used.has(key)) {
      rows.push({ key, label: getAttributeName(key), dataType: '-', unit: '-', value: snapshotMap[key] })
    }
  })
  return rows
})

const availableDataTemplates = computed(() => {
  const modelId = activeInstance.value?.modelId
  return dataTemplates.value.filter(tpl => !tpl.deviceModelId || !modelId || String(tpl.deviceModelId) === String(modelId))
})

const mqttTopicRows = (adapterName?: string, devicePoint?: string) => {
  const ready = !!adapterName && !!devicePoint
  const base = ready ? `smartlab/adapter/${adapterName}/${devicePoint}` : ''
  return [
    { type: 'command', label: '命令', direction: '系统 → Adapter', topic: ready ? `${base}/command` : '' },
    { type: 'telemetry', label: '遥测', direction: 'Adapter → 系统', topic: ready ? `${base}/telemetry` : '' },
    { type: 'event', label: '事件', direction: 'Adapter → 系统', topic: ready ? `${base}/event` : '' },
    { type: 'heartbeat', label: '心跳', direction: 'Adapter → 系统', topic: adapterName ? `smartlab/adapter/${adapterName}/heartbeat` : '' }
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
  adapterLoading.value = true
  try {
    const res = await axios.get('/api/adapter/index/list')
    if (res.data?.success) {
      adapterOptions.value = res.data.data || []
    }
  } finally {
    adapterLoading.value = false
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
    const templateName = adapterConfig.templateName || undefined
    const params = categoryName ? { categoryName } : { templateName }
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
      keyword: instanceKeyword.value.trim() || undefined
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

const getAttributeName = (key: string) => {
  if (!activeInstance.value) return key
  const attr = getModelAttributes(activeInstance.value.modelId).find((v: any) => [v.identifier, v.name, v.displayName].filter(Boolean).map(String).includes(String(key)))
  return attr?.displayName || attr?.name || key
}

const getAttributeDisplayName = (identifier: string) => {
  if (!activeInstance.value) return identifier
  const prop = getModelAttributes(activeInstance.value.modelId).find((p: any) => p.name === identifier || p.identifier === identifier)
  return prop ? (prop.displayName || prop.name) : identifier
}

const operatorLabel = (op: string) => {
  if (op === 'LTE') return '≤ 小于等于'
  if (op === 'GTE') return '≥ 大于等于'
  if (op === 'LT') return '< 小于'
  if (op === 'GT') return '> 大于'
  if (op === 'EQ') return '= 等于'
  return op
}

function asArray<T = any>(value: any): T[] {
  return Array.isArray(value) ? value : []
}

const paramKey = (param: any) => String(param.name || param.paramName || param.displayName || '')
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
  
  // G3/G4: 加载实例自身的安全约束。如果实例未配置，则自动拷贝模型约束作为初始默认模板值
  const cfg = activeInstance.value.instanceConfig || {}
  let list = cfg.intrinsicConstraints
  if (!list || list.length === 0) {
    const model = activeInstanceModel.value
    list = asArray(model?.intrinsicConstraints || model?.intrinsicConstraint)
  }

  localConstraints.value = asArray(list).map((c: any) => ({
    objectAttributeName: c.objectAttributeName || '',
    operator: c.operator || 'LTE',
    boundaryValue: c.boundaryValue != null ? c.boundaryValue : null,
    unit: c.unit || '',
    violationStateName: c.violationStateName || '',
    description: c.description || ''
  }))

  loadDevicePoints(activeInstance.value.boundAdapterName, 'drawer')
  datasetCreateForm.value = { templateId: '', dataDesc: '' }
  controlCommandId.value = activeInstanceCommands.value[0]?.commandId || ''
  resetControlParams()
  
  // 运行状态作为首选 Tab 展示
  activeTab.value = 'status'
  drawerVisible.value = true
  loadDataSets()
  loadComponents()
  
  // 自动开始首次快照抓取
  fetchSnapshot()
  consoleLogs.value = []
}

const onModelChangeInDrawer = (modelId: string) => {
  if (!activeInstance.value) return
  activeInstance.value.boundDevicePoint = ''
  loadDevicePoints(activeInstance.value.boundAdapterName || '', 'drawer')
}

const saveInstance = async () => {
  if (!activeInstance.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    
    // G8: 完全使用 instalDate 结构，无需兼容性 HACKS
    const asset = {
      serialNumber: activeInstance.value.instanceConfig?.assetInfo?.serialNumber || '',
      purchaseDate: activeInstance.value.instanceConfig?.assetInfo?.purchaseDate || '',
      instalDate: activeInstance.value.instanceConfig?.assetInfo?.instalDate || '',
      location: activeInstance.value.instanceConfig?.assetInfo?.location || '',
      notes: activeInstance.value.instanceConfig?.assetInfo?.notes || ''
    }

    // G3/G4: 扁平化保存该设备实例的全部安全约束（来自模型拷贝与自定义追加的完整集合）
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

    // 移除废弃属性
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

const deleteInstance = async (id: string) => {
  try {
    const res = await axios.delete(`/api/device/instance/delete/${id}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

const addConstraint = () => {
  localConstraints.value.push({
    objectAttributeName: '',
    operator: 'LTE',
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
  if (!activeInstance.value) return
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

const startPolling = () => {
  stopPolling()
  fetchSnapshot()
  pollingTimer = setInterval(fetchSnapshot, 3000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const closeDrawer = () => {
  stopPolling()
  snapshot.value = null
  activeInstance.value = null
  controlCommandId.value = ''
  controlParamValues.value = {}
  instanceDataSets.value = []
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

const saveComponent = async () => {
  if (!activeInstance.value || !componentForm.value.componentName.trim()) {
    ElMessage.warning('请输入组件名称')
    return
  }
  savingComponent.value = true
  try {
    const res = await axios.post('/api/device/component/save', {
      componentName: componentForm.value.componentName.trim(),
      categoryId: componentForm.value.categoryId ? Number(componentForm.value.categoryId) : null,
      parentInstanceId: Number(activeInstance.value.instanceId),
      selfInstanceId: null,
      status: '未配置',
      specification: {}
    })
    if (res.data?.success) {
      componentForm.value = { componentName: '', categoryId: '', selfInstanceId: '', status: '未配置' }
      await loadComponents()
      ElMessage.success('组件槽位已新增')
    } else {
      ElMessage.error(res.data?.message || '保存组件失败')
    }
  } finally {
    savingComponent.value = false
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
  if (status === '使用中') return 'success'
  if (status === '待更换') return 'warning'
  if (status === '已更换') return 'info'
  return 'info'
}

// G5: 组件配置与更换，联动模型查询
const componentCategoryModels = ref<DeviceModel[]>([])
const componentCategoryModelLoading = ref(false)
const componentSelectedModelId = ref('')
const componentCandidateInstances = ref<DeviceInstance[]>([])
const componentCategoryHasModels = ref(true)

const openComponentAction = async (row: any, mode: 'configure' | 'replace') => {
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

  // 二路过滤：只查询并展示当前组件槽位类别下的模型
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
        
        // 自动选择槽位中已有实例对应的模型
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
      params: { pageNo: 1, pageSize: 100, modelId }
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
  if (!activeComponent.value?.id) return
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

const discardComponent = async (row: any) => {
  if (!row?.id) return
  try {
    await ElMessageBox.confirm('确认将该组件标记为已废弃？', '废弃组件', { type: 'warning' })
    const res = await axios.post('/api/device/component/' + row.id + '/discard')
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '废弃组件失败')
      return
    }
    await loadComponents()
    ElMessage.success('组件已废弃')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.response?.data?.message || '废弃组件异常')
  }
}

const markPendingReplacement = async (row: any) => {
  if (!row?.id) return
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

const deleteComponent = async (row: any) => {
  const id = row?.id
  if (!id) return
  const res = await axios.delete('/api/device/component/delete/' + id)
  if (res.data?.success) {
    ElMessage.success('组件已删除')
    await loadComponents()
  } else {
    ElMessage.error(res.data?.message || '删除组件失败')
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
  if (!activeInstance.value) return
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

watch(activeTab, (tab) => {
  if (tab === 'status') {
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
    isOnline: raw.isOnline === true || raw.onlineStatus === 'ONLINE'
  }
}

const templateIdOf = (template: any) => String(template?.templateId || template?.id || '')

function findModelById(modelId: string) {
  if (!modelId) return null
  return modelOptions.value.find(v => v.modelId === modelId) || models.value.find(v => v.modelId === modelId) || null
}

// G1: 新建抽屉相关方法
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
    const templateName = adapterConfig.templateName || undefined
    const params = categoryName ? { categoryName } : { templateName }
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

// G1: 提交创建
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
      operator: c.operator || 'LTE',
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

// G7: Wizard 预览 computed
const wizardPreviewAttributes = computed(() => {
  return asArray(wizardModel.value?.capabilitySpec?.attributes || wizardModel.value?.attributes)
})

const wizardPreviewCommands = computed(() => {
  return asArray(wizardModel.value?.capabilitySpec?.capabilities || wizardModel.value?.capabilities).map((cap: any) => ({
    ...cap,
    commandId: cap.name || cap.commandId,
    commandName: cap.displayName || cap.name || cap.commandId
  }))
})

const wizardPreviewStates = computed(() => {
  const opState = wizardModel.value?.opState || wizardModel.value?.capabilitySpec?.operationStateSpace
  return asArray(opState?.states)
})

// 物理时间格式化
const formatTime = (time: any) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', { hour12: false })
}

// 复制文本
const copyText = (text: string) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已成功复制实例 ID')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

// 调试台终端打印方法
const appendConsoleLog = (tag: '下发' | '成功' | '失败' | '系统', type: 'send' | 'success' | 'fail' | 'info', text: string) => {
  const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  consoleLogs.value.push({ time, tag, type, text })
  nextTick(() => {
    if (consoleBodyRef.value) {
      consoleBodyRef.value.scrollTop = consoleBodyRef.value.scrollHeight
    }
  })
}

const clearConsoleLogs = () => {
  consoleLogs.value = []
}

// 发送手动指令并记录进反馈终端
const sendManualCommand = async () => {
  if (!activeInstance.value || !controlCommandId.value) return
  sendingControl.value = true
  
  const cmd = activeControlCommand.value
  const cmdName = cmd?.commandName || controlCommandId.value
  const params = buildControlParameters()
  
  appendConsoleLog('下发', 'send', `发送指令: ${cmdName}, 参数负载: ${JSON.stringify(params)}`)

  try {
    const res = await axios.post(`/api/device/instance/control/${activeInstance.value.instanceId}`, {
      commandId: controlCommandId.value,
      signalName: 'MANUAL_EXECUTE',
      parameters: params
    })
    
    if (res.data?.success) {
      appendConsoleLog('成功', 'success', `下发成功. 返回包: ${JSON.stringify(res.data.data)}`)
      ElMessage.success('指令下发成功')
      fetchSnapshot()
    } else {
      const errMsg = res.data?.message || '未知异常'
      appendConsoleLog('失败', 'fail', `执行被拒: ${errMsg}`)
      ElMessage.error(`下发失败: ${errMsg}`)
    }
  } catch (err: any) {
    const errMsg = err.response?.data?.message || err.message || '网络断开'
    appendConsoleLog('失败', 'fail', `服务端响应异常: ${errMsg}`)
    ElMessage.error(`网络错误: ${errMsg}`)
  } finally {
    sendingControl.value = false
  }
}

onMounted(loadData)
onUnmounted(() => {
  stopPolling()
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
})
</script>

<style scoped>
.instance-page {
  height: calc(100vh - 52px);
  background: #eef2f6;
}

.layout { height: 100%; }

.model-list-panel {
  width: 280px;
  background: #fff;
  border-right: 1px solid #cbd5e1;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.content { padding: 0; background: #eef2f6; display: flex; flex-direction: column; }

.main-header {
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.main-header h2 { margin: 0; font-size: 19px; color: #111827; }
.subtitle { font-size: 12px; color: #6b7280; }
.actions { display: flex; gap: 8px; align-items: center; }
.add-device-trigger { height: 34px; border-radius: 18px; padding: 0 16px; box-shadow: 0 8px 18px rgba(37, 99, 235, 0.18); }

.component-create-panel { margin-bottom: 10px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #f8fafc; display: grid; grid-template-columns: 1.1fr 1fr 1fr 110px auto; gap: 8px; align-items: center; }

.instance-search {
  width: 220px;
}

.instance-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}

.mono {
  font-family: 'Consolas', 'Menlo', monospace;
  color: #111827;
}

.empty-wrap { padding: 80px 0; }

.drawer-body { padding: 0; }

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.status-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 10px;
}

.empty-inline {
  color: #9ca3af;
  font-size: 12px;
}

.constraint-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.dataset-create-panel {
  display: grid;
  grid-template-columns: minmax(180px, 0.9fr) minmax(220px, 1.1fr) auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #ccd6e3;
  border-left: 3px solid #2563eb;
  border-radius: 6px;
  background: #f8fafc;
}

.drawer-body :deep(.el-tabs__content) {
  padding-top: 8px;
}

.drawer-body :deep(.el-table th) {
  background: #f3f6fa;
  color: #243244;
}

.mb-12 { margin-bottom: 12px; }
.mt-12 { margin-top: 12px; }

.instance-list-wrap { flex: 1; min-height: 0; padding: 12px; overflow: auto; }
.instance-table { cursor: pointer; }
.instance-table :deep(.el-table__row:hover) { background: #eef6ff; }
.instance-name-cell { display: grid; gap: 3px; }
.instance-name-cell strong { color: #0f172a; font-size: 14px; }
.instance-name-cell span { color: #64748b; font-size: 12px; }
.binding-cell { display: grid; grid-template-columns: 1fr; gap: 4px; }
.binding-cell span { display: grid; grid-template-columns: 58px minmax(0, 1fr); gap: 8px; align-items: center; }
.binding-cell b { color: #64748b; font-weight: 700; }
.topic-cell { display: grid; gap: 3px; }
.topic-cell span { display: grid; grid-template-columns: 42px minmax(0, 1fr); gap: 8px; align-items: center; }
.topic-cell b { color: #64748b; font-weight: 700; }
.topic-cell code, .topic-table code, .topic-preview-grid code { display: block; overflow: hidden; color: #0f172a; font-family: Consolas, Menlo, monospace; text-overflow: ellipsis; white-space: nowrap; }

/* G6: 详情框固定高度 75vh 并集成滚动条 */
.instance-detail-dialog :deep(.el-dialog__body) {
  padding: 0;
  height: 75vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.instance-detail-dialog :deep(.el-tabs) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.instance-detail-dialog :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.instance-info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; }
.instance-info-grid :deep(.el-form-item) { margin-bottom: 2px; }
.readonly-status { height: 32px; display: flex; align-items: center; gap: 8px; color: #64748b; font-size: 12px; }
.topic-section, .snapshot-section { margin-top: 10px; border: 1px solid #cbd5e1; }
.section-caption { height: 34px; padding: 0 10px; border-bottom: 1px solid #dbe4ef; background: #f8fafc; display: flex; align-items: center; color: #0f172a; font-weight: 800; }
.manual-control-form { display: grid; gap: 10px; }
.control-param-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; }
.control-param-grid :deep(.el-form-item) { margin-bottom: 0; }
.field-hint { margin-top: 3px; color: #64748b; font-size: 12px; }
.form-wide { grid-column: 1 / -1; }

/* G6: Header 样式 */
.instance-detail-header {
  padding: 16px 20px 12px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(135deg, #f0f7ff 0%, #ffffff 100%);
  flex-shrink: 0;
}
.header-primary {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-primary h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}
.header-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.status-dot.online {
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.6);
  animation: blink 1.6s infinite alternate;
}
.status-dot.offline {
  background: #94a3b8;
}
.header-status .divider {
  color: #e2e8f0;
  margin: 0 2px;
}
.header-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-meta .sep {
  color: #cbd5e1;
}

@keyframes blink {
  0% { opacity: 0.4; }
  100% { opacity: 1; }
}

/* G1: 侧边滚动抽屉式布局 (Drawer) */
.instance-create-drawer :deep(.el-drawer__body) {
  padding: 0;
  overflow: hidden;
}
.instance-edit-workbench {
  display: flex;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
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
.edit-scroll-content { flex: 1; min-width: 0; }
.edit-scroll-content :deep(.el-scrollbar__view) { padding: 12px 14px 22px; }

.anchor-section { scroll-margin-top: 8px; }
.industrial-section { margin-bottom: 10px; padding-top: 4px; }
.section-heading { display: flex; align-items: center; gap: 8px; margin: 0 0 8px !important; padding: 0 0 7px; border-bottom: 1px solid #dfe6ef; color: #0f172a; font-size: 16px; font-weight: 800; }
.section-index { display: inline-flex; width: 30px; height: 22px; align-items: center; justify-content: center; border: 1px solid #bfdbfe; border-radius: 4px; background: #eff6ff; color: #1d4ed8; font-size: 12px; font-weight: 800; }
.drawer-section { margin-top: 8px; border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px; background: #fff; box-shadow: inset 3px 0 0 #dbeafe; }

.step3-preview {
  padding: 0 4px;
}
.preview-section {
  margin-bottom: 16px;
}
.preview-label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 6px;
}
.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.preview-empty {
  font-size: 12px;
  color: #94a3b8;
  font-style: italic;
}

/* Tab 1: 运行状态 KPI 卡片网络布局 */
.telemetry-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(215px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}
.kpi-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
  border-left: 4px solid #2563eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.2s ease;
}
.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.06);
}
.kpi-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
}
.kpi-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
}
.kpi-value-row {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.kpi-val {
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  font-family: Consolas, Monaco, monospace;
}
.kpi-unit {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
}
.kpi-meta {
  margin-top: 6px;
  font-size: 11px;
  color: #94a3b8;
}
.kpi-meta code {
  font-family: monospace;
  background: #f1f5f9;
  padding: 1px 3px;
  border-radius: 3px;
}

/* Tab 2: 控制下发页面左右分栏与反馈控制台布局 */
.control-tab-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  height: 100%;
}
.control-left-form {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.control-right-console {
  display: flex;
  flex-direction: column;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #334155;
  background: #0f172a;
}
.console-header {
  background: #1e293b;
  color: #f8fafc;
  padding: 8px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  border-bottom: 1px solid #334155;
}
.console-body {
  padding: 12px;
  font-family: Consolas, Menlo, Monaco, monospace;
  font-size: 11px;
  height: 290px;
  overflow-y: auto;
  color: #cbd5e1;
  line-height: 1.6;
}
.console-line {
  margin-bottom: 6px;
  white-space: pre-wrap;
  word-break: break-all;
}
.console-line.send { color: #38bdf8; }
.console-line.success { color: #4ade80; }
.console-line.fail { color: #f87171; }
.console-line.info { color: #94a3b8; }
.console-time {
  color: #64748b;
  margin-right: 6px;
}
.console-tag {
  font-weight: 700;
  margin-right: 6px;
}
.console-empty {
  color: #475569;
  text-align: center;
  padding-top: 100px;
  font-style: italic;
}

@media (max-width: 960px) {
  .main-header {
    align-items: flex-start;
    gap: 12px;
  }

  .actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .instance-search {
    width: 180px;
  }
  
  .control-tab-layout {
    grid-template-columns: 1fr;
  }
}
</style>
