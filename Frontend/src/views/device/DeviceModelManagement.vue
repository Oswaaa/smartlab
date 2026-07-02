<template>
  <div class="device-model-page">
    <section class="content-shell">
      <aside class="model-list-panel">
        <div class="list-tools model-tree-tools">
          <el-input v-model="keyword" placeholder="搜索模型名称" clearable :prefix-icon="Search" @input="onKeywordInput" />
          <el-button type="primary" plain @click="showAddCategoryDialog = true">设备类别</el-button>
        </div>

        <el-scrollbar class="model-list" v-loading="loading">
          <el-tree
            v-if="modelTreeData.length"
            :data="modelTreeData"
            node-key="id"
            default-expand-all
            :expand-on-click-node="false"
            class="category-model-tree"
            @node-click="handleModelTreeNodeClick"
          >
            <template #default="{ data }">
              <div class="category-model-node" :class="[data.type, { active: data.type === 'model' && selectedModelId === data.modelId }]">
                <span class="node-title">{{ data.label }}</span>
                <span class="node-meta">{{ data.meta }}</span>
              </div>
            </template>
          </el-tree>
          <el-empty v-if="!loading && models.length === 0" description="暂无设备模型" :image-size="90" />
        </el-scrollbar>

        <div class="list-footer">
          <el-pagination
            v-model:current-page="pageNo"
            :page-size="pageSize"
            :total="total"
            size="small"
            background
            layout="prev, pager, next"
            @current-change="loadData"
          />
        </div>
      </aside>

      <main class="detail-panel">
        <div class="global-top-bar">
          <span class="global-toolbar-title">设备模型管理</span>
          <div class="global-toolbar-actions">
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
            <el-button v-if="canCreateModel" type="primary" :icon="Plus" @click="openCreateDrawer">新建设备模型</el-button>
          </div>
        </div>
        <template v-if="selectedModel">
          <div class="detail-head">
            <div class="detail-title">
              <h2>{{ selectedModel.modelName }}</h2>
              <p>{{ selectedModel.categoryName || '未分类' }} · {{ formatTime(selectedModel.updateTime) }}</p>
            </div>
            <div class="detail-actions">
              <el-button :icon="Download" @click="downloadModelBundle">导出模型文件</el-button>
              <el-button v-if="canEditModel" type="primary" plain :icon="EditPen" @click="openEditDrawer(selectedModel)">编辑模型</el-button>
              <el-popconfirm v-if="canDeleteModel" title="确认删除该模型？有设备实例时不可删除。" @confirm="deleteModel(selectedModel.modelId)">
                <template #reference>
                  <el-button type="danger" plain :icon="Delete">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>

                    <div class="detail-anchor-layout" style="display: flex; height: calc(100vh - 200px); overflow: hidden;">
            <el-anchor class="detail-anchor-menu" @click="(e) => e.preventDefault()" container=".detail-scroll-content .el-scrollbar__wrap" :offset="20" style="width: 150px; flex-shrink: 0; border-right: 1px solid var(--el-border-color-light);">
              <el-anchor-link href="#view-basic" title="基础信息" />
              <el-anchor-link href="#view-ability" title="属性功能" />
              <el-anchor-link href="#view-topology" title="结构拓扑" />
              <el-anchor-link href="#view-adapter" title="Adapter 契约" />
              <el-anchor-link href="#view-mapping" title="映射关系" />
              <el-anchor-link href="#view-state" title="状态机" />
              <el-anchor-link href="#view-constraint" title="内置约束" />
              <el-anchor-link href="#view-template" title="默认数据模板" />
              <el-anchor-link href="#view-file" title="模型文件" />
            </el-anchor>
            <el-scrollbar class="detail-scroll-content" style="flex-grow: 1; padding-left: 20px;">
                          <div id="view-basic" class="anchor-section industrial-section">
                <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">基础信息</h2>
              <section class="info-section">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="模型名称">{{ selectedModel.modelName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="设备类别">{{ selectedModel.categoryName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="通信协议">{{ selectedModel.adapterContract?.config?.protocol || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="更新时间">{{ formatTime(selectedModel.updateTime) }}</el-descriptions-item>
                </el-descriptions>
              </section>
            </div>

                          <div id="view-ability" class="anchor-section industrial-section">
                <h2 class="section-heading">属性功能</h2>
                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>设备属性</h3>
                    <span class="section-count">{{ selectedAttributes.length }} 项</span>
                  </div>
                  <div v-if="selectedAttributes.length === 0" class="compact-empty inline-empty">暂无设备属性</div>
                  <div v-else class="attribute-grid">
                    <article v-for="attr in selectedAttributes" :key="attr.name || attr.displayName" class="attribute-tile">
                      <div class="tile-title">{{ attr.displayName || '-' }}</div>
                      <div class="tile-meta">
                        <span>{{ valueKindLabel(attr.valueKind) }}</span>
                        <span>{{ attr.dataType || '-' }}</span>
                        <span v-if="attr.unit">{{ attr.unit }}</span>
                      </div>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>设备操作</h3>
                    <span class="section-count">{{ selectedCapabilities.length }} 项</span>
                  </div>
                  <div v-if="selectedCapabilities.length === 0" class="compact-empty inline-empty">暂无设备操作</div>
                  <div v-else class="operation-grid">
                    <article v-for="(capability, index) in selectedCapabilities" :key="capability.name || index" class="operation-card">
                      <div class="operation-head">
                        <span class="item-index">{{ index + 1 }}</span>
                        <div>
                          <strong>{{ capability.displayName || '未命名操作' }}</strong>
                          <span class="adapter-command-line"><em>Adapter 命令</em><b>{{ capability.adapterCommandName || '-' }}</b></span>
                        </div>
                      </div>
                      <div class="param-chip-row">
                        <span v-for="param in capability.parameters" :key="param.name || param.displayName" class="info-chip">
                          <em>参数</em><b>{{ param.displayName || '未命名参数' }}</b><i>{{ param.dataType || '-' }}</i>
                        </span>
                        <span v-if="!capability.parameters?.length" class="muted-text">无参数</span>
                      </div>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>端口配置</h3>
                    <span class="section-count">{{ selectedPorts.length }} 项</span>
                  </div>
                  <div v-if="selectedPorts.length === 0" class="compact-empty inline-empty">暂无端口配置</div>
                  <div v-else class="port-grid">
                    <article v-for="(port, index) in selectedPorts" :key="port.portName || index" class="port-tile">
                      <div class="port-name">{{ port.displayName || port.portName || '未命名端口' }}</div>
                      <div class="port-meta">
                        <span>{{ directionLabel(port.direction) }}</span>
                        <span>{{ displayAttributeName(port.bindingAttrName, selectedAttributes) }}</span>
                      </div>
                    </article>
                  </div>
                </section>
              </div>

              <div id="view-topology" class="anchor-section industrial-section">
                <h2 class="section-heading">结构拓扑模板</h2>
                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>模型组件模板</h3>
                    <span class="section-count">{{ selectedComponentsBom.length }} 项</span>
                  </div>
                  <div v-if="selectedComponentsBom.length === 0" class="compact-empty inline-empty">暂无结构拓扑模板</div>
                  <el-table v-else :data="selectedComponentsBom" border size="small" class="industrial-table">
                    <el-table-column label="组件名称" min-width="160">
                      <template #default="{ row }"><strong>{{ row.componentName || row.name || '-' }}</strong></template>
                    </el-table-column>
                    <el-table-column label="组件类别" min-width="140">
                      <template #default="{ row }">{{ row.categoryName || categoryNameById(row.categoryId) || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="父级组件" min-width="140">
                      <template #default="{ row }">{{ row.parentComponentName || row.parentName || row.parentComponentId || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="规格信息" min-width="220">
                      <template #default="{ row }"><code>{{ stringifyBrief(row.specification || row.spec || {}) }}</code></template>
                    </el-table-column>
                  </el-table>
                </section>
              </div>

                          <div id="view-adapter" class="anchor-section industrial-section">
                <h2 class="section-heading">Adapter 契约</h2>
                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>Adapter 命令</h3>
                    <span class="section-count">{{ selectedAdapterCommands.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterCommands.length === 0" class="compact-empty inline-empty">暂无 Adapter 命令</div>
                  <div v-else class="operation-grid">
                    <article v-for="(command, index) in selectedAdapterCommands" :key="command.commandName || index" class="operation-card adapter-command-card">
                      <div class="operation-head">
                        <span class="item-index">{{ index + 1 }}</span>
                        <div>
                          <strong>{{ command.commandName || '未命名命令' }}</strong>
                          <span>{{ command.description || '无说明' }}</span>
                        </div>
                      </div>
                      <div class="param-chip-row">
                        <span v-for="param in command.commandParameters" :key="param.paramName" class="info-chip">
                          <em>参数</em><b>{{ param.paramName }}</b><i>{{ param.dataType || '-' }}</i>
                        </span>
                        <span v-if="!command.commandParameters?.length" class="muted-text">无参数</span>
                      </div>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>Adapter 属性</h3>
                    <span class="section-count">{{ selectedAdapterAttributes.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterAttributes.length === 0" class="compact-empty inline-empty">暂无 Adapter 属性</div>
                  <div v-else class="attribute-grid adapter-attribute-grid">
                    <article v-for="attr in selectedAdapterAttributes" :key="attr.name" class="attribute-tile">
                      <div class="tile-title">{{ attr.description || attr.name || '-' }}</div>
                      <div class="tile-meta">
                        <span>{{ attr.name || '-' }}</span>
                        <span>{{ attr.dataType || '-' }}</span>
                      </div>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>Adapter 事件</h3>
                    <span class="section-count">{{ selectedAdapterEvents.length }} 项</span>
                  </div>
                  <div v-if="selectedAdapterEvents.length === 0" class="compact-empty inline-empty">暂无 Adapter 事件</div>
                  <div v-else class="event-chip-grid">
                    <span v-for="event in selectedAdapterEvents" :key="event.eventName" class="event-chip" :class="event.eventType === 'CMD' ? 'event-chip-cmd' : 'event-chip-op'">
                      <b>{{ event.eventName }}</b>
                      <em>{{ event.description || eventTypeLabel(event.eventType) }}</em>
                    </span>
                  </div>
                </section>
              </div>

                          <div id="view-mapping" class="anchor-section industrial-section">
                <h2 class="section-heading">映射关系</h2>
                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>属性映射</h3>
                    <span class="section-count">{{ selectedAttributeMappings.length }} 项</span>
                  </div>
                  <div v-if="selectedAttributeMappings.length === 0" class="compact-empty inline-empty">暂无属性映射</div>
                  <div v-else class="mapping-grid">
                    <article v-for="mapping in selectedAttributeMappings" :key="mapping.adapterAttrName" class="mapping-tile mapping-tile-labeled">
                      <span class="mapping-side"><em>模型属性</em><b>{{ displayAttributeName(mapping.modelAttributeName, selectedAttributes) }}</b></span>
                      <span class="mapping-arrow">→</span>
                      <span class="mapping-side adapter-side"><em>Adapter 属性</em><b>{{ mapping.adapterAttrName || '-' }}</b></span>
                    </article>
                  </div>
                </section>

                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>功能映射</h3>
                    <span class="section-count">{{ functionMappingGroups(selectedCapabilities).length }} 项</span>
                  </div>
                  <div v-if="functionMappingGroups(selectedCapabilities).length === 0" class="compact-empty inline-empty">暂无功能映射</div>
                  <div v-else class="function-map-list">
                    <article v-for="group in functionMappingGroups(selectedCapabilities)" :key="group.key" class="function-map-card">
                      <div class="function-map-head">
                        <strong>{{ group.capabilityDisplayName }}</strong>
                        <span class="adapter-command-line"><em>Adapter 命令</em><b>{{ group.adapterCommandName }}</b></span>
                      </div>
                      <div v-if="group.parameters.length === 0" class="muted-text">未配置参数映射</div>
                      <div v-else class="function-param-list">
                        <div v-for="param in group.parameters" :key="param.key" class="function-param-row">
                          <span class="mapping-side adapter-side"><em>Adapter 参数</em><b>{{ param.commandParamName }}</b></span>
                          <span class="mapping-arrow">←</span>
                          <span v-if="param.isFixedValue" class="mapping-side fixed-side"><em>固定值</em><b>{{ param.fixedValue }}</b></span>
                          <span v-else class="mapping-side"><em>功能参数</em><b>{{ param.capabilityParamDisplayName }}</b></span>
                        </div>
                      </div>
                    </article>
                  </div>
                </section>
              </div>

                          <div id="view-state" class="anchor-section industrial-section">
                <h2 class="section-heading">状态机</h2>
                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>指令生命周期</h3>
                    <span class="section-count">{{ selectedCmdStates.length }} 个状态</span>
                  </div>
                  <div v-if="selectedCmdStates.length === 0" class="compact-empty inline-empty">暂无指令生命周期状态</div>
                  <div v-else class="state-token-panel">
                    <span v-for="state in selectedCmdStates" :key="state.stateName" class="filled-state-token cmd-state-token">{{ state.stateName }}</span>
                  </div>
                </section>

                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>功能状态</h3>
                    <span class="section-count">{{ selectedOpStates.length }} 个状态</span>
                  </div>
                  <div v-if="selectedOpStates.length === 0" class="compact-empty inline-empty">暂无功能状态</div>
                  <div v-else class="state-token-panel">
                    <span v-for="state in selectedOpStates" :key="state.stateName" class="filled-state-token op-state-token">{{ state.stateName }}</span>
                  </div>
                </section>

                <section class="info-section section-cluster">
                  <div class="section-title">
                    <h3>转移规则</h3>
                    <span class="section-count">{{ selectedStateTransitions.length }} 条</span>
                  </div>
                  <div v-if="selectedStateTransitions.length === 0" class="compact-empty inline-empty">暂无转移规则</div>
                  <div v-else class="transition-card-list">
                    <article v-for="(row, index) in selectedStateTransitions" :key="index" class="transition-view-card">
                      <div class="transition-card-head">
                        <strong>{{ row.description || '未命名规则' }}</strong>
                        <span>{{ row.trigger?.interfaceName || '-' }} / {{ row.trigger?.signalName || '-' }}</span>
                      </div>
                      <div class="transition-flow">
                        <span class="filled-state-token light-state-token">{{ row.fromStateName || '-' }}</span>
                        <span class="flow-arrow">→</span>
                        <span class="filled-state-token light-state-token">{{ row.toStateName || '-' }}</span>
                      </div>
                      <div v-if="row.actions?.length" class="action-chip-row">
                        <span v-for="(act, aIdx) in row.actions" :key="aIdx" class="action-chip">{{ describeAction(act) }}</span>
                      </div>
                    </article>
                  </div>
                </section>
              </div>

                          <div id="view-constraint" class="anchor-section industrial-section">
                <h2 class="section-heading">内置约束</h2>
                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>内置约束</h3>
                    <span class="section-count">{{ selectedConstraints.length }} 条</span>
                  </div>
                  <div v-if="selectedConstraints.length === 0" class="compact-empty inline-empty">暂无内置约束</div>
                  <div v-else class="constraint-list">
                    <article v-for="(row, index) in selectedConstraints" :key="index" class="constraint-card">
                      <strong>{{ displayAttributeName(row.objectAttributeName, selectedAttributes) }}</strong>
                      <span>{{ operatorLabel(row.operator) }} {{ row.boundaryValue }}</span>
                      <em>违规状态：{{ row.violationStateName || '-' }}</em>
                    </article>
                  </div>
                </section>
              </div>

                          <div id="view-template" class="anchor-section industrial-section">
                <h2 class="section-heading">默认数据模板</h2>
                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>已选模板字段</h3>
                    <span class="section-count">{{ defaultTemplateAttributes.length }} 项</span>
                  </div>
                  <div v-if="defaultTemplateAttributes.length === 0" class="compact-empty inline-empty">暂无默认数据模板配置</div>
                  <div v-else class="attribute-grid">
                    <article v-for="attr in defaultTemplateAttributes" :key="attr.name || attr.displayName" class="attribute-tile">
                      <div class="tile-title">{{ attr.displayName || '-' }}</div>
                      <div class="tile-meta">
                        <span>{{ attr.dataType || '-' }}</span>
                        <span v-if="attr.unit">{{ attr.unit }}</span>
                      </div>
                    </article>
                  </div>
                </section>
              </div>

                          <div id="view-file" class="anchor-section industrial-section">
                <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">模型文件</h2>
              <section class="model-json-grid">
                <div class="json-panel">
                  <div class="section-title"><h3>设备能力模型</h3></div>
                  <pre>{{ formatJson(capabilityModelJson) }}</pre>
                </div>
                <div class="json-panel">
                  <div class="section-title"><h3>设备状态机模型</h3></div>
                  <pre>{{ formatJson(stateMachineModelJson) }}</pre>
                </div>
              </section>
            </div>

            </el-scrollbar>
          </div>
        </template>

        <el-empty v-else description="请选择或新建设备模型" :image-size="120" />
      </main>
    </section>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" direction="rtl" size="78%" destroy-on-close class="model-drawer">
      <div class="drawer-body">
                <div class="detail-anchor-layout" style="display: flex; height: calc(100vh - 120px); overflow: hidden;">
          <el-anchor class="detail-anchor-menu" @click="(e) => e.preventDefault()" container=".edit-scroll-content .el-scrollbar__wrap" :offset="20" style="width: 150px; flex-shrink: 0; border-right: 1px solid var(--el-border-color-light);">
            <el-anchor-link href="#edit-basic" title="基础信息" />
            <el-anchor-link href="#edit-ability" title="属性功能" />
            <el-anchor-link href="#edit-adapter" title="Adapter 契约" />
            <el-anchor-link href="#edit-mapping" title="映射关系" />
            <el-anchor-link href="#edit-state" title="状态机" />
            <el-anchor-link href="#edit-constraint" title="内置约束" />
            <el-anchor-link href="#edit-template" title="默认数据模板" />
            <el-anchor-link href="#edit-file" title="模型文件" />
          </el-anchor>
          <el-scrollbar class="edit-scroll-content" style="flex-grow: 1; padding-left: 20px;">
                    <div id="edit-basic" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">基础信息</h2>
            <section class="drawer-section">
              <div class="section-title"><h3>基础信息</h3></div>
              <el-form label-width="96px" size="small" class="basic-form">
                <el-form-item label="模型名称" required>
                  <el-input v-model="draft.basic.modelName" placeholder="例如：反应釜温控模块" maxlength="80" show-word-limit />
                </el-form-item>
                <el-form-item label="所属分类" prop="categoryValue">
                  <div style="display: flex; gap: 10px; width: 100%;">
                    <el-select v-model="draft.basic.categoryValue" placeholder="选择分类" filterable style="flex-grow: 1;">
                      <el-option v-for="cat in categories" :key="String(cat.id)" :label="cat.categoryName" :value="String(cat.id)" />
                    </el-select>
                    <el-button type="primary" link @click="showAddCategoryDialog = true">管理类别</el-button>
                  </div>
                </el-form-item>
              </el-form>
            </section>
          </div>

                    <div id="edit-ability" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">属性功能</h2>
            <section class="drawer-section">
              <div class="section-title">
                <h3>设备属性</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addAttribute">新增属性</el-button>
              </div>
              <div v-if="draft.attributes.length === 0" class="compact-empty block-empty">暂无设备属性，请点击右上角“新增属性”进行配置</div>
              <el-table v-else :data="draft.attributes" border size="small">
                <el-table-column label="属性名称" min-width="220">
                  <template #default="{ row }"><el-input v-model="row.displayName" size="small" placeholder="例如：当前温度" /></template>
                </el-table-column>
                <el-table-column label="取值类型" width="130">
                  <template #default="{ row }">
                    <el-select v-model="row.valueKind" size="small">
                      <el-option label="连续值" value="CONTINUOUS" />
                      <el-option label="离散值" value="DISCRETE" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="数据类型" width="130">
                  <template #default="{ row }"><data-type-select v-model="row.dataType" :options="attributeDataTypes" /></template>
                </el-table-column>
                <el-table-column label="单位" width="150">
                  <template #default="{ row }"><el-input v-model="row.unit" size="small" placeholder="℃ / rpm / mL" /></template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeAttribute($index)" /></template>
                </el-table-column>
              </el-table>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <h3>设备操作</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addCapability">新增操作</el-button>
              </div>
              <div v-if="draft.capabilities.length > 0" class="editor-card-list capability-editor-list">
                <article v-for="(capability, capIndex) in draft.capabilities" :key="capability._key" class="editor-card capability-editor-card">
                  <div class="editor-card-head">
                    <div class="editor-card-title capability-title-editor">
                      <span class="item-index">{{ capIndex + 1 }}</span>
                      <el-input v-model="capability.displayName" size="small" placeholder="操作名称，例如：加热" />
                    </div>
                    <el-button link type="danger" :icon="Delete" @click="removeCapability(capability)">删除</el-button>
                  </div>
                  <div class="nested-toolbar">
                    <span>操作参数</span>
                    <el-button size="small" type="primary" plain circle :icon="Plus" title="添加参数" @click="addCapabilityParameter(capability)" />
                  </div>
                  <el-table v-if="capability.parameters.length > 0" :data="capability.parameters" border size="small" class="nested-table">
                    <el-table-column label="参数名称" min-width="220">
                      <template #default="{ row }"><el-input v-model="row.displayName" size="small" placeholder="例如：目标温度" /></template>
                    </el-table-column>
                    <el-table-column label="数据类型" width="140">
                      <template #default="{ row }"><data-type-select v-model="row.dataType" :options="attributeDataTypes" /></template>
                    </el-table-column>
                    <el-table-column label="" width="54" fixed="right">
                      <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeCapabilityParameter(capability, $index)" /></template>
                    </el-table-column>
                  </el-table>
                  <div v-if="capability.parameters.length === 0" class="compact-empty inline-empty">暂无参数</div>
                </article>
              </div>
              <div v-else class="compact-empty block-empty">暂无设备操作，请点击右上角“新增操作”进行配置</div>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <h3>端口配置</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addPort">新增端口</el-button>
              </div>
              <div v-if="draft.ports.length === 0" class="compact-empty block-empty">暂无端口配置，请点击右上角“新增端口”进行配置</div>
              <el-table v-else :data="draft.ports" border size="small">
                <el-table-column label="端口名称" min-width="180">
                  <template #default="{ row }"><el-input v-model="row.displayName" size="small" placeholder="例如：温度输出口" /></template>
                </el-table-column>
                <el-table-column label="方向" width="120">
                  <template #default="{ row }">
                    <el-select v-model="row.direction" size="small">
                      <el-option label="输入" value="IN" />
                      <el-option label="输出" value="OUT" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="绑定属性" min-width="200">
                  <template #default="{ row }">
                    <el-select v-model="row.bindingAttrKey" size="small" filterable clearable placeholder="选择绑定属性">
                      <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.ports, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </div>

                    <div id="edit-adapter" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">Adapter 契约</h2>
            <section class="drawer-section">
              <div class="section-title"><h3>契约来源</h3></div>
              <el-form label-width="90px" size="small">
                <el-form-item label="协议类型">
                  <el-select v-model="draft.adapterContract.config.protocol">
                    <el-option label="MQTT" value="MQTT" />
                    <el-option label="HTTP" value="HTTP" />
                  </el-select>
                </el-form-item>
                <el-form-item label="注册 Adapter">
                  <div class="registered-adapter-picker" v-loading="registeredAdapterLoading">
                    <el-select v-model="selectedRegisteredAdapterName" filterable clearable placeholder="选择已注册 Adapter" @change="handleRegisteredAdapterChange">
                      <el-option
                        v-for="adapter in registeredAdapters"
                        :key="adapter.adapterName || adapter.id"
                        :label="adapter.adapterName + (adapter.status ? ' · ' + adapter.status : '')"
                        :value="adapter.adapterName"
                      />
                    </el-select>
                    <el-select v-model="selectedRegisteredAdapterTemplate" filterable clearable placeholder="选择设备模板" :disabled="!registeredAdapterTemplateOptions.length">
                      <el-option
                        v-for="tpl in registeredAdapterTemplateOptions"
                        :key="tpl.templateName"
                        :label="tpl.description ? tpl.templateName + ' · ' + tpl.description : tpl.templateName"
                        :value="tpl.templateName"
                      />
                    </el-select>
                    <el-button type="primary" plain :disabled="!selectedRegisteredAdapterName || !selectedRegisteredAdapterTemplate" @click="applyRegisteredAdapterContract">载入契约</el-button>
                    <el-button plain :icon="Refresh" @click="fetchRegisteredAdapters">刷新</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="配置文本">
                  <div class="config-import">
                    <el-upload :auto-upload="false" :show-file-list="false" accept=".json,.txt" :on-change="importAdapterFile">
                      <el-button :icon="Upload">上传配置</el-button>
                    </el-upload>
                    <el-button type="primary" plain @click="applyAdapterConfigText">解析到契约</el-button>
                  </div>
                  <el-input v-model="adapterConfigText" type="textarea" :rows="5" placeholder="可粘贴 JSON 格式的 Adapter 配置文本，或从上方选择已注册 Adapter。" />
                </el-form-item>
              </el-form>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <h3>Adapter 命令</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addAdapterCommand">新增命令</el-button>
              </div>
              <div v-if="draft.adapterContract.commands.length > 0" class="editor-card-list command-editor-list">
                <article v-for="(command, commandIndex) in draft.adapterContract.commands" :key="command._key" class="editor-card command-editor-card">
                  <div class="editor-card-head">
                    <div class="editor-card-title">
                      <span class="item-index">{{ commandIndex + 1 }}</span>
                      <el-input v-model="command.commandName" size="small" placeholder="命令名，例如：set_temperature" />
                    </div>
                    <el-button link type="danger" :icon="Delete" @click="removeAdapterCommand(command)">删除</el-button>
                  </div>
                  <div class="nested-toolbar">
                    <span>命令参数</span>
                    <el-button size="small" type="primary" plain circle :icon="Plus" title="添加参数" @click="addCommandParameter(command)" />
                  </div>
                  <el-table v-if="visibleCommandParameters(command).length > 0" :data="visibleCommandParameters(command)" border size="small" class="nested-table">
                    <el-table-column label="参数名" min-width="180">
                      <template #default="{ row }"><el-input v-model="row.paramName" size="small" placeholder="例如：target_temp" /></template>
                    </el-table-column>
                    <el-table-column label="数据类型" width="140">
                      <template #default="{ row }"><data-type-select v-model="row.dataType" :options="adapterDataTypes" /></template>
                    </el-table-column>
                    <el-table-column label="" width="54" fixed="right">
                      <template #default="{ row }"><el-button link type="danger" :icon="Delete" @click="removeObjectRow(command.commandParameters, row)" /></template>
                    </el-table-column>
                  </el-table>
                  <div v-if="visibleCommandParameters(command).length === 0" class="compact-empty inline-empty">暂无系统参数</div>
                </article>
              </div>
              <div v-else class="compact-empty block-empty">暂无 Adapter 命令，请点击右上角“新增命令”进行配置</div>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <h3>Adapter 属性</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addAdapterAttribute">新增属性</el-button>
              </div>
              <div v-if="draft.adapterContract.telemetry.adapterAttributes.length === 0" class="compact-empty block-empty">暂无 Adapter 属性，请点击右上角“新增属性”进行配置</div>
              <el-table v-else :data="draft.adapterContract.telemetry.adapterAttributes" border size="small">
                <el-table-column label="属性字段" min-width="180">
                  <template #default="{ row }"><el-input v-model="row.name" size="small" placeholder="例如：temperature" /></template>
                </el-table-column>
                <el-table-column label="数据类型" width="140">
                  <template #default="{ row }"><data-type-select v-model="row.dataType" :options="adapterDataTypes" /></template>
                </el-table-column>
                <el-table-column label="说明" min-width="220">
                  <template #default="{ row }"><el-input v-model="row.description" size="small" placeholder="可选" /></template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeAdapterAttribute($index)" /></template>
                </el-table-column>
              </el-table>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <h3>Adapter 事件</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addAdapterEvent">新增事件</el-button>
              </div>
              <div v-if="draft.adapterContract.events.length === 0" class="compact-empty block-empty">暂无 Adapter 事件，请点击右上角“新增事件”进行配置</div>
              <el-table v-else :data="draft.adapterContract.events" border size="small">
                <el-table-column label="事件名" min-width="180">
                  <template #default="{ row }"><el-input v-model="row.eventName" size="small" placeholder="例如：COMMAND_DONE" /></template>
                </el-table-column>
                <el-table-column label="说明" min-width="240">
                  <template #default="{ row }"><el-input v-model="row.description" size="small" placeholder="可选" /></template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeAdapterEvent($index)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </div>

                    <div id="edit-mapping" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">映射关系</h2>
            <section class="drawer-section">
              <div class="section-title">
                <h3>属性映射</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addAttributeMapping">新增映射</el-button>
              </div>
              <div v-if="draft.adapterContract.telemetry.attributesMapping.length === 0" class="compact-empty block-empty">暂无属性映射，请点击右上角“新增映射”进行配置</div>
              <el-table v-else :data="draft.adapterContract.telemetry.attributesMapping" border size="small">
                <el-table-column label="模型属性" min-width="200">
                  <template #default="{ row }">
                    <el-select v-model="row.modelAttributeKey" size="small" filterable @change="handleAttributeMappingModelChange(row)">
                      <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" :disabled="isAttributeOptionUsed(attr.key, row)" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="Adapter 属性" min-width="200">
                  <template #default="{ row }">
                    <el-select v-model="row.adapterAttrName" size="small" filterable @change="handleAdapterAttributeMappingChange(row)">
                      <el-option v-for="attr in adapterAttributeNameOptionsDetailed" :key="attr.name" :label="attr.name + (isAttrDataTypeMatch(row.modelAttributeKey, attr.dataType) ? '' : ' (类型不匹配)')" :value="attr.name" :disabled="isAdapterAttributeUsed(attr.name, row) || !isAttrDataTypeMatch(row.modelAttributeKey, attr.dataType)" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.adapterContract.telemetry.attributesMapping, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <h3>功能映射</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addFunctionMapping">新增映射</el-button>
              </div>
              <div v-if="draft.functionMappings.length === 0" class="compact-empty block-empty">暂无功能映射，请点击右上角“新增映射”进行配置</div>
              <div v-else class="function-mapping-card-list">
                <article v-for="(row, rowIndex) in draft.functionMappings" :key="row._key || rowIndex" class="function-mapping-editor-card">
                  <div class="function-map-editor-head">
                    <div class="function-map-selects">
                      <label>
                        <span>模型操作</span>
                        <el-select v-model="row.capabilityKey" size="small" filterable placeholder="选择模型操作" @change="handleFunctionMappingCapabilityChange(row)">
                          <el-option
                            v-for="capability in capabilitySelectOptions"
                            :key="capability.key"
                            :label="capability.label"
                            :value="capability.key"
                            :disabled="isCapabilityMapped(capability.key, row)"
                          />
                        </el-select>
                      </label>
                      <span class="mapping-direction">→</span>
                      <label>
                        <span>Adapter 命令</span>
                        <el-select v-model="row.adapterCommandName" size="small" filterable clearable placeholder="选择 Adapter 命令" @change="handleFunctionMappingCommandChange(row)">
                          <el-option v-for="cmd in commandNameOptions" :key="cmd" :label="cmd" :value="cmd" :disabled="isAdapterCommandMapped(cmd, row)" />
                        </el-select>
                      </label>
                    </div>
                    <el-button link type="danger" :icon="Delete" @click="removeRow(draft.functionMappings, rowIndex)">删除</el-button>
                  </div>

                  <div class="param-map-editor compact-param-editor">
                    <div v-for="(mapping, index) in row.parameterMapping" :key="mapping._key || index" class="param-map-row" :class="{ invalid: isParameterMappingInvalid(row, mapping), fixed: mapping.isFixedValue }">
                      <div class="param-map-field">
                        <span class="param-map-label">功能参数</span>
                        <el-select v-model="mapping.capabilityParamKey" size="small" filterable placeholder="选择功能参数" @change="handleCapabilityParameterChange(row, mapping)">
                          <el-option v-for="param in capabilityParameterOptionsDetailedByKey(row.capabilityKey)" :key="param._key" :label="(param.displayName || param.name) + ' · ' + param.dataType + (isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType) ? '' : ' (类型不匹配)')" :value="param._key" :disabled="isCapabilityParamMapped(row, param._key, mapping) || !isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType)" />
                        </el-select>
                      </div>
                      <div class="param-map-field">
                        <span class="param-map-label">Adapter 参数</span>
                        <el-select v-model="mapping.commandParamName" size="small" filterable placeholder="选择命令参数" @change="handleParameterCommandChange(row, mapping)">
                          <el-option v-for="param in commandParameterOptionsDetailed(row.adapterCommandName)" :key="param.paramName" :label="param.paramName + ' · ' + param.dataType" :value="param.paramName" :disabled="isCommandParamMapped(row, param.paramName, mapping)" />
                        </el-select>
                      </div>
                      <div class="param-map-mode">
                        <span class="param-map-label">取值方式</span>
                        <el-switch v-model="mapping.isFixedValue" size="small" active-text="固定" inactive-text="映射" @change="handleParameterFixedChange(mapping)" />
                      </div>
                      <div v-if="mapping.isFixedValue" class="param-map-field fixed-input-field">
                        <span class="param-map-label">固定值</span>
                        <el-input v-model="mapping.fixedValue" size="small" :placeholder="fixedValuePlaceholder(row, mapping)" />
                      </div>
                      <el-button link type="danger" :icon="Delete" class="param-delete-btn" @click="removeRow(row.parameterMapping, index)" />
                      <span v-if="isParameterMappingInvalid(row, mapping)" class="map-warning">{{ parameterMappingWarning(row, mapping) }}</span>
                    </div>
                    <div class="param-map-toolbar">
                      <span v-if="row.parameterMapping.length === 0" class="no-mapping-placeholder">未配置参数映射</span>
                      <span v-else></span>
                      <el-button size="small" type="primary" plain :icon="Plus" class="add-mapping-btn" @click="addParameterMapping(row)">新增参数映射</el-button>
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </div>

                    <div id="edit-state" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">状态机</h2>
            <h2 class="state-group-title first">接口定义</h2>
            <section class="drawer-section locked-section">
              <div class="section-title">
                <div class="locked-heading">
                  <el-icon><Lock /></el-icon>
                  <h3>对外接口定义</h3>
                  <el-tag size="small" effect="plain" type="info">系统自动生成</el-tag>
                </div>
              </div>
              <el-table :data="stateMachineInterfaceRows" border size="small" class="locked-table">
                <el-table-column label="接口名称" min-width="140">
                  <template #default="{ row }">
                    <span>{{ row.name }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="方向" width="100">
                  <template #default="{ row }">
                    <span>{{ row.direction }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="类型" width="120">
                  <template #default="{ row }">
                    <span>{{ row.interfaceType }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="允许的信号" min-width="240">
                  <template #default="{ row }">
                    <el-tag v-for="sig in row.allowedSignals" :key="sig" size="small" class="tag-gap">{{ sig }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <h2 class="state-group-title">状态</h2>
            <div class="state-card-grid">
              <section class="drawer-section state-card locked-section">
                <div class="section-title">
                  <div class="locked-heading">
                    <el-icon><Lock /></el-icon>
                    <h3>指令生命周期</h3>
                    <el-tag size="small" effect="plain" type="info">系统固定</el-tag>
                  </div>
                </div>
                <div class="state-summary-row">
                  <span class="state-summary-label">初始状态</span>
                  <div><el-tag size="small" type="success" effect="plain">{{ draft.cmdState.initialStateName }}</el-tag></div>
                </div>
                <div class="state-summary-row align-top">
                  <span class="state-summary-label">状态名称</span>
                  <div class="state-token-list">
                    <el-tag v-for="state in draft.cmdState.states" :key="state.stateName" size="small" class="state-token filled">{{ state.stateName }}</el-tag>
                  </div>
                </div>
              </section>

              <section class="drawer-section state-card">
                <div class="section-title">
                  <div class="locked-heading">
                    <h3>功能状态</h3>
                  </div>
                </div>
                <div class="state-summary-row">
                  <span class="state-summary-label">初始状态</span>
                  <div class="state-input-with-warning">
                    <el-select v-model="draft.opState.initialStateName" filterable allow-create size="small" class="state-inline-select">
                      <el-option v-for="name in opStateNameOptions" :key="name" :label="name" :value="name" />
                    </el-select>
                    <span class="warning-slot">
                      <el-tooltip v-if="isInitialOpStateInvalid" content="该状态已删除或不存在" placement="top">
                        <el-icon class="inline-warning-icon"><Warning /></el-icon>
                      </el-tooltip>
                    </span>
                  </div>
                </div>
                <div class="state-summary-row align-top" style="margin-top: 12px;">
                  <span class="state-summary-label">状态名称</span>
                  <div class="state-token-list">
                    <el-tag v-for="(state, $index) in draft.opState.states" :key="state._key || $index" size="small" closable @close="removeOpState($index)" class="state-token filled closable-state-token">
                      <span class="state-token-text">{{ state.stateName || '未命名' }}</span>
                      <span class="state-token-warning-slot">
                        <el-tooltip v-if="stateUsageWarning(state.stateName)" :content="stateUsageWarning(state.stateName)" placement="top">
                          <el-icon class="state-warning-icon"><Warning /></el-icon>
                        </el-tooltip>
                      </span>
                    </el-tag>
                    <el-input v-if="opStateInputVisible" ref="OpStateInputRef" v-model="opStateInputValue" size="small" style="width: 90px;" @keyup.enter="handleOpStateInputConfirm" @blur="handleOpStateInputConfirm" />
                    <el-button v-else size="small" plain class="compact-action-btn" @click="showOpStateInput">+ 新增</el-button>
                  </div>
                </div>
              </section>
            </div>

            <h2 class="state-group-title">转移规则</h2>
            <section class="drawer-section locked-section">
              <div class="section-title">
                <div class="locked-heading">
                  <el-icon><Lock /></el-icon>
                  <h3>指令生命周期转移规则</h3>
                  <el-tag size="small" effect="plain" type="info">系统固定</el-tag>
                </div>
              </div>
              <el-table :data="commandLifecycleTransitionRows" border size="small" class="transition-table locked-table compact-transition-table">
                <el-table-column prop="description" label="规则说明" width="145" />
                <el-table-column label="状态流转" width="150">
                  <template #default="{ row }">
                    <span style="display: flex; align-items: center; gap: 4px; color: var(--el-text-color-regular); font-size: 12px;">{{ row.fromStateName }} <el-icon><Right /></el-icon> {{ row.toStateName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="触发条件" width="190">
                  <template #default="{ row }">
                    <div style="display: flex; flex-direction: column; gap: 4px;">
                      <el-tag size="small" type="info" effect="plain" style="align-self: flex-start">{{ row.trigger.interfaceName }}</el-tag>
                      <span style="font-size: 12px; color: var(--el-text-color-regular);">{{ row.trigger.signalName }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="转移动作" min-width="160">
                  <template #default="{ row }">
                    <div class="serious-actions-container">
                      <div v-for="(act, aIdx) in row.actions" :key="aIdx" class="serious-action-wrapper">
                        <el-tag size="small" type="info" effect="plain" class="serious-action-tag">
                          {{ describeAction(act) }}
                        </el-tag>
                      </div>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <h3>功能状态转移规则</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addStateTransition">新增规则</el-button>
              </div>
              <div v-if="stateMachineWarningMessages.length" class="state-warning-panel">
                <div v-for="message in stateMachineWarningMessages" :key="message" class="state-warning-item">
                  <el-icon class="inline-warning-icon"><Warning /></el-icon>
                  <span>{{ message }}</span>
                </div>
              </div>
              <div v-if="draft.stateTransitions.length === 0" class="compact-empty block-empty">暂无功能状态转移规则，请点击右上角“新增规则”进行配置</div>
              <el-table v-else :data="draft.stateTransitions" border size="small" class="transition-table compact-transition-table">
                <el-table-column label="说明" width="150">
                  <template #default="{ row }">
                    <div class="field-with-warning">
                      <span class="warning-slot">
                        <el-tooltip v-if="transitionWarning(row)" :content="transitionWarning(row)" placement="top">
                          <el-icon class="inline-warning-icon"><Warning /></el-icon>
                        </el-tooltip>
                      </span>
                      <el-input v-model="row.description" size="small" placeholder="可选" />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="状态流转" width="210">
                  <template #default="{ row }">
                    <div style="display: flex; align-items: center; gap: 6px;">
                      <state-select v-model="row.fromStateName" :options="opStateNameOptions" style="flex: 1" />
                      <el-icon><Right /></el-icon>
                      <state-select v-model="row.toStateName" :options="opStateNameOptions" style="flex: 1" />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="触发条件" width="170">
                  <template #default="{ row }">
                    <div style="display: flex; flex-direction: column; gap: 6px;">
                      <span class="locked-action"><el-icon><Lock /></el-icon><span>Interface_adapter_in</span></span>
                      <state-select v-model="row.trigger.signalName" :options="signalOptionsForInterface('Interface_adapter_in')" style="width: 100%" />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="动作" min-width="210">
                  <template #default="{ row, $index }">
                    <div class="transition-action-cell">
                      <div class="transition-action-list">
                        <div v-for="(act, aIdx) in row.actions" :key="aIdx" class="transition-action-row">
                          <span class="action-editor-label">发送</span>
                          <el-select v-model="act.payload.signalName" size="small" placeholder="选择信号">
                            <el-option v-for="sig in getSignalsForInterface(act.payload.interfaceName)" :key="sig" :label="sig" :value="sig" />
                          </el-select>
                          <el-button link type="info" :icon="Close" @click="row.actions.splice(aIdx, 1)" title="取消动作" />
                        </div>
                        <el-button size="small" plain :icon="Plus" class="compact-action-btn" @click="ensureTransitionAction(row)">添加</el-button>
                      </div>
                      <el-button link type="danger" :icon="Delete" @click="removeRow(draft.stateTransitions, $index)" title="删除该规则" />
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </div>

                    <div id="edit-constraint" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">内置约束</h2>
            <section class="drawer-section">
              <div class="section-title">
                <h3>内置约束</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addIntrinsicConstraint">新增约束</el-button>
              </div>
              <div v-if="draft.intrinsicConstraints.length === 0" class="compact-empty block-empty">暂无内置约束，请点击右上角“新增约束”进行配置</div>
              <el-table v-else :data="draft.intrinsicConstraints" border size="small">
                <el-table-column label="约束属性" min-width="170">
                  <template #default="{ row }">
                    <el-select v-model="row.objectAttributeKey" size="small" filterable>
                      <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="比较符" width="120">
                  <template #default="{ row }"><el-select v-model="row.operator" size="small"><el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" /></el-select></template>
                </el-table-column>
                <el-table-column label="阈值" width="150">
                  <template #default="{ row }"><el-input v-model="row.boundaryValue" size="small" /></template>
                </el-table-column>
                <el-table-column label="违规状态" min-width="160">
                  <template #default="{ row }"><state-select v-model="row.violationStateName" :options="opStateNameOptions" /></template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.intrinsicConstraints, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </div>

                    <div id="edit-template" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">默认数据模板</h2>
            <section class="drawer-section">
              <div class="section-title">
                <h3>默认数据模板字段</h3>
              </div>
              <div v-if="draft.attributes.length === 0" class="compact-empty block-empty">请先添加设备属性</div>
              <el-checkbox-group v-else v-model="draft.defaultDataTemplateAttrs">
                <el-checkbox v-for="attr in draft.attributes" :key="attr._key" :label="attr._key" :value="attr._key" class="template-attr-option">
                  <span class="template-attr-name">{{ attr.displayName || attr.name || '未命名属性' }}</span>
                  <span class="template-attr-meta">{{ attr.dataType || '-' }}<template v-if="attr.unit"> · {{ attr.unit }}</template></span>
                </el-checkbox>
              </el-checkbox-group>
              <div style="margin-top: 10px; font-size: 12px; color: #909399;">
                选中的属性将作为该设备模型的默认数据表字段。在创建设备实例时，系统将自动创建对应的数据表。
              </div>
            </section>
          </div>

                    <div id="edit-file" class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">模型文件</h2>
            <div style="margin-bottom: 12px; display: flex; justify-content: flex-end;">
              <el-button type="primary" plain size="small" :loading="generatingPreview" @click="generatePreview">生成 / 刷新预览</el-button>
            </div>
            <section class="model-json-grid">
              <div class="json-panel">
                <div class="section-title"><h3>保存后的设备能力模型</h3></div>
                <pre>{{ formatJson(draftCapabilityModelJson) }}</pre>
              </div>
              <div class="json-panel">
                <div class="section-title"><h3>保存后的状态机模型</h3></div>
                <pre>{{ formatJson(draftStateMachineModelJson) }}</pre>
              </div>
            </section>
          </div>

          </el-scrollbar>
        </div>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveDraft">保存模型</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="adapterTemplateDialogVisible" title="选择要绑定的 Adapter 模板" width="500px">
      <div style="margin-bottom: 15px;">检测到配置文件中包含新模板，请选择需要绑定到当前设备模型的模板：</div>
      <el-form label-width="80px">
        <el-form-item label="选择模板">
          <el-select v-model="selectedAdapterTemplate" placeholder="请选择模板" style="width: 100%">
            <el-option
              v-for="item in adapterTemplatesList"
              :key="item.name"
              :label="item.name + (item.description ? ' (' + item.description + ')' : '')"
              :value="item.name"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adapterTemplateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAdapterTemplateSelection">确认解析并绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAddCategoryDialog" title="设备类别管理" width="720px" class="category-manager-dialog">
      <div class="category-manager-layout">
        <section class="category-existing-panel">
          <div class="dialog-section-title">已有类别</div>
          <el-table :data="categories" border stripe size="small" max-height="320">
            <el-table-column label="类别名" min-width="150">
              <template #default="{ row }"><strong>{{ row.categoryName }}</strong></template>
            </el-table-column>
            <el-table-column label="父类别" min-width="140">
              <template #default="{ row }">{{ categoryNameById(row.parentCategoryId) || '-' }}</template>
            </el-table-column>
            <el-table-column label="描述" min-width="180">
              <template #default="{ row }">{{ row.description || '-' }}</template>
            </el-table-column>
          </el-table>
        </section>
        <section class="category-create-panel">
          <div class="dialog-section-title">新增类别</div>
          <el-form ref="newCategoryFormRef" :model="newCategoryDraft" :rules="newCategoryRules" label-width="86px" size="small">
            <el-form-item label="类别名称" prop="categoryName">
              <el-input v-model="newCategoryDraft.categoryName" placeholder="例如：反应釜、泵、传感器" />
            </el-form-item>
            <el-form-item label="父类别">
              <el-select v-model="newCategoryDraft.parentCategoryId" clearable filterable placeholder="无父类别">
                <el-option v-for="cat in categories" :key="String(cat.id)" :label="cat.categoryName" :value="String(cat.id)" />
              </el-select>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="newCategoryDraft.description" type="textarea" :rows="3" placeholder="说明该类别的硬件范围、用途或边界" />
            </el-form-item>
          </el-form>
        </section>
      </div>
      <template #footer>
        <el-button @click="showAddCategoryDialog = false">关闭</el-button>
        <el-button type="primary" :loading="savingCategory" @click="saveNewCategory">保存类别</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref, resolveComponent, watch, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Cpu, Delete, Download, EditPen, Lock, Notification, Plus, Refresh, Search, Unlock, Upload, Close, Right, Warning, InfoFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'

const authStore = useAuthStore()

function getSignalTagType(signalName) {
  if (signalName === 'OP_STATE') return 'success'
  if (signalName === 'CMD_STATE') return 'primary'
  if (signalName === 'CMD_START') return 'danger'
  if (signalName === 'CMD_CANCEL') return 'warning'
  return 'info'
}

function formatSignalName(signalName) {
  if (signalName === 'OP_STATE') return '输出功能状态 (OP_STATE)'
  if (signalName === 'CMD_STATE') return '输出指令周期 (CMD_STATE)'
  if (signalName === 'CMD_START') return '下发启动命令 (CMD_START)'
  if (signalName === 'CMD_CANCEL') return '下发取消命令 (CMD_CANCEL)'
  return signalName || ''
}

function formatSignalShortName(signalName) {
  if (signalName === 'OP_STATE') return '输出状态'
  if (signalName === 'CMD_STATE') return '输出指令'
  if (signalName === 'CMD_START') return '启动命令'
  if (signalName === 'CMD_CANCEL') return '取消命令'
  if (signalName === 'CMD_PAUSE') return '暂停命令'
  if (signalName === 'CMD_RESUME') return '恢复命令'
  if (signalName === 'CMD_RESET') return '重置命令'
  return signalName || ''
}

function getSignalsForInterface(interfaceName) {
  if (interfaceName === 'Interface_status_out') return ['OP_STATE', 'CMD_STATE']
  if (interfaceName === 'Interface_adapter_out') return ['CMD_START', 'CMD_CANCEL', 'CMD_PAUSE', 'CMD_RESUME', 'CMD_RESET']
  return []
}

function onActionInterfaceChange(act) {
  if (!act.payload) act.payload = {}
  const sigs = getSignalsForInterface(act.payload.interfaceName)
  if (sigs.length > 0) {
    act.payload.signalName = sigs[0]
  } else {
    act.payload.signalName = ''
  }
}
const attributeDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN']
const adapterDataTypes = ['INTEGER', 'DOUBLE', 'BOOLEAN', 'STRING']
const operators = ['GT', 'LT', 'GE', 'LE', 'EQ', 'NE', 'BETWEEN', 'IN']
const interfaceTypes = ['WORKFLOW', 'STAT', 'ADAPTER', 'CONTROL', 'CONSTRAINT']
const stateActionNames = ['SEND', 'ASSIGN']
const standardCmdEvents = ['COMMAND_RECEIVED', 'COMMAND_RUNNING', 'COMMAND_COMPLETED', 'COMMAND_FAILED', 'COMMAND_TIMEOUT', 'COMMAND_CANCELLED']
const adapterOutSignals = ['CMD_START', 'CMD_CANCEL', 'CMD_PAUSE', 'CMD_RESUME', 'CMD_RESET']

const DataTypeSelect = defineComponent({
  name: 'DataTypeSelect',
  props: { modelValue: String, options: { type: Array, default: () => [] } },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const ElSelect = resolveComponent('ElSelect')
    const ElOption = resolveComponent('ElOption')
    return () => h(ElSelect, {
      modelValue: props.modelValue,
      size: 'small',
      'onUpdate:modelValue': value => emit('update:modelValue', value)
    }, () => dataTypeOptions(props.options, props.modelValue).map(type => h(ElOption, { key: type, label: type, value: type })))
  }
})

const StateSelect = defineComponent({
  name: 'StateSelect',
  props: { modelValue: String, options: { type: Array, default: () => [] } },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const ElSelect = resolveComponent('ElSelect')
    const ElOption = resolveComponent('ElOption')
    return () => h(ElSelect, {
      modelValue: props.modelValue,
      size: 'small',
      filterable: true,
      allowCreate: true,
      'onUpdate:modelValue': value => { emit('update:modelValue', value); emit('change', value) }
    }, () => props.options.map(option => h(ElOption, { key: option, label: option, value: option })))
  }
})


const models = ref([])
const categories = ref([])
const selectedModelId = ref('')
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const drawerMode = ref('create')
const adapterConfigText = ref('')
const registeredAdapters = ref([])
const registeredAdapterLoading = ref(false)
const selectedRegisteredAdapterName = ref('')
const selectedRegisteredAdapterTemplate = ref('')
const registeredAdapterTemplateOptions = computed(() => {
  const adapter = registeredAdapters.value.find(item => item.adapterName === selectedRegisteredAdapterName.value)
  return asArray(parsedAdapterConfig(adapter).deviceTemplates)
})

const showAddCategoryDialog = ref(false)
const savingCategory = ref(false)
const newCategoryDraft = ref({ categoryName: '', parentCategoryId: '', description: '' })
const newCategoryFormRef = ref(null)

const adapterTemplateDialogVisible = ref(false)
const adapterTemplatesList = ref([])
const selectedAdapterTemplate = ref('')
const tempAdapterConfigRaw = ref(null)

const newCategoryRules = {
  categoryName: [{ required: true, message: '请输入类别名称', trigger: 'blur' }]
}

const draft = reactive(emptyDraft())
const interfaceLocked = ref(true)

const canCreateModel = computed(() => authStore.hasPermission('device_model:create'))
const canEditModel = computed(() => authStore.hasPermission('device_model:edit'))
const canDeleteModel = computed(() => authStore.hasPermission('device_model:delete'))
const selectedModel = computed(() => models.value.find(item => item.modelId === selectedModelId.value) || null)
const modelTreeData = computed(() => {
  const modelsByCategory = new Map()
  models.value.forEach(model => {
    const categoryId = model.categoryId == null ? '' : String(model.categoryId)
    if (!modelsByCategory.has(categoryId)) modelsByCategory.set(categoryId, [])
    modelsByCategory.get(categoryId).push(model)
  })
  const categoryChildren = new Map()
  categories.value.forEach(category => {
    const parentId = category.parentCategoryId == null ? '' : String(category.parentCategoryId)
    if (!categoryChildren.has(parentId)) categoryChildren.set(parentId, [])
    categoryChildren.get(parentId).push(category)
  })
  const modelNode = model => ({
    id: `model:${model.modelId}`,
    type: 'model',
    label: model.modelName || '未命名模型',
    meta: summaryText(model),
    modelId: model.modelId
  })
  const categoryNode = category => {
    const id = category.id == null ? '' : String(category.id)
    const children = [
      ...asArray(categoryChildren.get(id)).map(categoryNode),
      ...asArray(modelsByCategory.get(id)).map(modelNode)
    ]
    return {
      id: `category:${id}`,
      type: 'category',
      label: category.categoryName || '未命名类别',
      meta: children.length ? `${children.length} 项` : '空类别',
      children
    }
  }
  const roots = asArray(categoryChildren.get('')).map(categoryNode)
  const uncategorized = asArray(modelsByCategory.get('')).map(modelNode)
  if (uncategorized.length) roots.push({ id: 'category:uncategorized', type: 'category', label: '未分类', meta: `${uncategorized.length} 个模型`, children: uncategorized })
  if (roots.length) return roots
  return models.value.map(modelNode)
})
const selectedAttributes = computed(() => asArray(selectedModel.value?.attributes))
const selectedCapabilities = computed(() => asArray(selectedModel.value?.capabilities))
const selectedPorts = computed(() => asArray(selectedModel.value?.ports))
const selectedAdapterCommands = computed(() => asArray(selectedModel.value?.adapterContract?.commands))
const selectedAdapterAttributes = computed(() => asArray(selectedModel.value?.adapterContract?.telemetry?.adapterAttributes))
const selectedAdapterEvents = computed(() => normalizeEventsToFlatList(selectedModel.value?.adapterContract?.events))
const selectedAttributeMappings = computed(() => asArray(selectedModel.value?.adapterContract?.telemetry?.attributesMapping))
const selectedCmdStates = computed(() => asArray(selectedModel.value?.cmdState?.states))
const selectedOpStates = computed(() => asArray(selectedModel.value?.opState?.states))
const selectedConstraints = computed(() => asArray(selectedModel.value?.intrinsicConstraints))
const selectedComponentsBom = computed(() => asArray(selectedModel.value?.componentsBom))
const drawerTitle = computed(() => drawerMode.value === 'create' ? '新建设备模型' : '编辑设备模型')
const attributeOptions = computed(() => draft.attributes.map((item, index) => ({ key: item._key, label: item.displayName || item.name || '属性' + (index + 1) })).filter(item => item.key))
const adapterAttributeNameOptions = computed(() => draft.adapterContract.telemetry.adapterAttributes.map(item => item.name).filter(Boolean))
const adapterAttributeNameOptionsDetailed = computed(() => draft.adapterContract.telemetry.adapterAttributes || [])
const commandNameOptions = computed(() => draft.adapterContract.commands.map(item => item.commandName).filter(Boolean))
function commandParameterOptionsDetailed(commandName) {
  const cmd = draft.adapterContract.commands.find(c => c.commandName === commandName)
  return cmd ? cmd.commandParameters.filter(p => !p.hidden) : []
}
function capabilityParameterOptionsDetailedByKey(capabilityKey) {
  const cap = draft.capabilities.find(c => c._key === capabilityKey)
  return cap ? cap.parameters : []
}
const adapterEventOptions = computed(() => opEventNames(draft.adapterContract.events))
const adapterSignalOptions = computed(() => uniqueStrings([...standardCmdEvents, ...adapterEventOptions.value]))
const capabilitySelectOptions = computed(() => draft.capabilities.map((item, index) => ({ key: item._key, label: item.displayName || item.name || '操作' + (index + 1) })).filter(item => item.key))
const generatedInterfaces = computed(() => defaultInterfaces(adapterSignalOptions.value))
const stateMachineInterfaceRows = computed(() => interfaceLocked.value ? generatedInterfaces.value : draft.stateMachineInterfaces)
const commandLifecycleTransitionRows = computed(() => defaultCommandLifecycleTransitions())
const opStateNameOptions = computed(() => draft.opState.states.map(item => item.stateName).filter(Boolean))
const isInitialOpStateInvalid = computed(() => !!draft.opState.initialStateName && !opStateNameOptions.value.includes(draft.opState.initialStateName))

function stateUsageWarning(stateName) {
  if (!stateName) return '状态名未填写'
  const used = draft.stateTransitions.some(t => t.fromStateName === stateName || t.toStateName === stateName)
  if (!used) return '状态未在任何转移规则中使用'
  return ''
}

function transitionWarning(row) {
  if (!row.fromStateName) return '来源状态为空'
  if (!row.toStateName) return '目标状态为空'
  return ''
}

const stateMachineWarningMessages = computed(() => {
  const messages = []
  if (draft.stateTransitions.length > 0) {
    draft.opState.states.forEach(state => {
      const warning = stateUsageWarning(state.stateName)
      if (warning) messages.push('状态 ' + (state.stateName || '未命名') + '：' + warning)
    })
  }
  draft.stateTransitions.forEach((row, index) => {
    const warning = transitionWarning(row)
    if (warning) messages.push('规则 ' + (index + 1) + '：' + warning)
  })
  return messages.slice(0, 5)
})
const emptyModelBundle = () => ({ capabilityModel: {}, stateMachineModel: {} })
const selectedModelBundle = ref(emptyModelBundle())
const defaultTemplateAttributes = ref([])
const capabilityModelJson = computed(() => selectedModelBundle.value.capabilityModel || {})
const stateMachineModelJson = computed(() => selectedModelBundle.value.stateMachineModel || {})
const selectedStateTransitions = computed(() => asArray(stateMachineModelJson.value?.transitions))
const draftCapabilityModelJson = ref({})
const draftStateMachineModelJson = ref({})
const generatingPreview = ref(false)

const generatePreview = async () => {
  generatingPreview.value = true
  try {
    const payload = buildSavePayload(false)
    const res = await axios.post('/api/device/model/preview', payload)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '生成预览失败')
      return
    }
    const bundle = normalizeModelBundle(res.data?.data)
    draftCapabilityModelJson.value = bundle.capabilityModel
    draftStateMachineModelJson.value = bundle.stateMachineModel
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '生成预览异常')
    console.error(error)
  } finally {
    generatingPreview.value = false
  }
}

function emptyDraft() {
  return {
    basic: { modelId: '', modelName: '', categoryValue: '' },
    attributes: [],
    capabilities: [],
    functionMappings: [],
    adapterContract: defaultAdapterContract(),
    ports: [],
    intrinsicConstraints: [],
    stateMachineInterfaces: defaultInterfaces([]),
    opState: defaultStateSpace('IDLE'),
    cmdState: defaultCommandLifecycle(),
    stateTransitions: [],
    componentsBom: [],
    defaultDataTemplateAttrs: []
  }
}

function defaultAdapterContract() {
  return { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
}

function defaultStateSpace(initialStateName) {
  return { initialStateName, states: [{ _key: makeUiKey('state'), stateName: initialStateName, onEntry: defaultStateEntryActions('OP', initialStateName) }] }
}

function defaultCommandLifecycle() {
  return { initialStateName: 'IDLE', states: commandLifecycleStateNames().map(stateName => ({ _key: makeUiKey('cmd_state'), stateName, onEntry: defaultStateEntryActions('CMD', stateName) })) }
}

function commandLifecycleStateNames() {
  return ['IDLE', 'SENT', 'RECEIVED', 'RUNNING', 'DONE', 'FAILED', 'TIMEOUT', 'CANCELLED']
}

function defaultInterfaces(adapterSignals = []) {
  const signals = uniqueStrings([...standardCmdEvents, ...asArray(adapterSignals).filter(Boolean)])
  return [
    { _key: 'iface_workflow', name: 'Interface_workflow_in', direction: 'IN', interfaceType: 'WORKFLOW', allowedSignals: ['EXECUTE_START', 'EXECUTE_PAUSE', 'EXECUTE_RESUME', 'EXECUTE_CANCEL', 'EXECUTE_RESET'] },
    { _key: 'iface_status', name: 'Interface_status_out', direction: 'OUT', interfaceType: 'STAT', allowedSignals: ['OP_STATE', 'CMD_STATE'] },
    { _key: 'iface_control', name: 'Interface_control_in', direction: 'IN', interfaceType: 'CONTROL', allowedSignals: ['MANUAL_EXECUTE', 'MANUAL_CANCEL', 'MANUAL_PAUSE', 'MANUAL_RESUME', 'MANUAL_RESET'] },
    { _key: 'iface_constraint', name: 'Interface_constraint_in', direction: 'IN', interfaceType: 'CONSTRAINT', allowedSignals: ['CONSTRAINT_CANCEL', 'CONSTRAINT_PAUSE', 'CONSTRAINT_RESUME', 'CONSTRAINT_RESET'] },
    { _key: 'iface_adapter_in', name: 'Interface_adapter_in', direction: 'IN', interfaceType: 'ADAPTER', allowedSignals: signals },
    { _key: 'iface_adapter_out', name: 'Interface_adapter_out', direction: 'OUT', interfaceType: 'ADAPTER', allowedSignals: adapterOutSignals }
  ]
}

function adapterInterfaceName() {
  return 'Interface_adapter_in'
}

function adapterOutAction(signalName) {
  return { actionName: 'SEND', payload: { interfaceName: 'Interface_adapter_out', signalName } }
}

function defaultStateEntryActions(type, stateName) {
  return [{
    actionName: 'SEND',
    payload: {
      interfaceName: 'Interface_status_out',
      signalName: type === 'CMD' ? 'CMD_STATE' : 'OP_STATE',
      stateName
    }
  }]
}

function defaultCommandLifecycleTransitions() {
  return [
    { description: '工作流触发指令下发', fromStateName: 'IDLE', toStateName: 'SENT', trigger: { interfaceName: 'Interface_workflow_in', signalName: 'EXECUTE_START' }, actions: [adapterOutAction('CMD_START')] },
    { description: '用户手动触发指令下发', fromStateName: 'IDLE', toStateName: 'SENT', trigger: { interfaceName: 'Interface_control_in', signalName: 'MANUAL_EXECUTE' }, actions: [adapterOutAction('CMD_START')] },
    { description: 'Adapter 已接收', fromStateName: 'SENT', toStateName: 'RECEIVED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_RECEIVED' }, actions: [] },
    { description: 'Adapter 执行中', fromStateName: 'RECEIVED', toStateName: 'RUNNING', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_RUNNING' }, actions: [] },
    { description: '执行完成', fromStateName: 'RUNNING', toStateName: 'DONE', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_COMPLETED' }, actions: [] },
    { description: '执行失败', fromStateName: 'RUNNING', toStateName: 'FAILED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_FAILED' }, actions: [] },
    { description: '执行超时', fromStateName: 'RUNNING', toStateName: 'TIMEOUT', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_TIMEOUT' }, actions: [] },
    { description: 'Adapter 确认取消', fromStateName: 'SENT', toStateName: 'CANCELLED', trigger: { interfaceName: adapterInterfaceName(), signalName: 'COMMAND_CANCELLED' }, actions: [] },
    { description: '工作流取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_workflow_in', signalName: 'EXECUTE_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] },
    { description: '用户手动取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_control_in', signalName: 'MANUAL_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] },
    { description: '约束引擎取消指令', fromStateName: 'RUNNING', toStateName: 'CANCELLED', trigger: { interfaceName: 'Interface_constraint_in', signalName: 'CONSTRAINT_CANCEL' }, actions: [adapterOutAction('CMD_CANCEL')] }
  ]
}

function isCommandLifecycleTransition(row) {
  return defaultCommandLifecycleTransitions().some(item => item.fromStateName === row?.fromStateName && item.toStateName === row?.toStateName && item.trigger.interfaceName === row?.trigger?.interfaceName && item.trigger.signalName === row?.trigger?.signalName)
}

async function loadData() {
  loading.value = true
  try {
    const [modelRes, categoryRes] = await Promise.all([
      axios.get('/api/device/model/page', { params: { pageNo: pageNo.value, pageSize: pageSize.value, keyword: keyword.value.trim() || undefined } }),
      axios.get('/api/device/category/list')
    ])
    categories.value = asArray(categoryRes.data?.data)
    const pageData = modelRes.data?.data || {}
    models.value = asArray(pageData.records).map(normalizeModel)
    total.value = Number(pageData.total || 0)
    if (!models.value.some(item => item.modelId === selectedModelId.value)) selectedModelId.value = models.value[0]?.modelId || ''
    await loadSelectedModelBundle(selectedModelId.value)
  } catch (err) {
    console.error('loadData error:', err); ElMessage.error(err.response?.data?.message || '加载设备模型失败')
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  const res = await axios.get('/api/device/category/list')
  categories.value = asArray(res.data?.data)
}

async function fetchModelBundle(modelId) {
  if (!modelId) return emptyModelBundle()
  const res = await axios.get('/api/device/model/' + modelId + '/bundle')
  if (!res.data?.success) throw new Error(res.data?.message || '加载模型文件失败')
  return normalizeModelBundle(res.data?.data)
}

async function loadSelectedModelBundle(modelId = selectedModelId.value) {
  if (!modelId) {
    selectedModelBundle.value = emptyModelBundle()
    defaultTemplateAttributes.value = []
    return
  }
  try {
    selectedModelBundle.value = await fetchModelBundle(modelId)
    try {
      const tplRes = await axios.get('/api/data/template/list')
      const templates = tplRes.data?.data || []
      const defaultTpl = templates.find(t => String(t.deviceModelId) === String(modelId) && t.isDefault)
      if (defaultTpl) {
        const detailRes = await axios.get(`/api/data/template/${defaultTpl.id}/details`)
        const details = detailRes.data?.data || []
        const currentModel = models.value.find(item => item.modelId === modelId)
        if (currentModel && currentModel.attributes) {
          const keys = details.map(d => d.deviceAttrKey)
          defaultTemplateAttributes.value = currentModel.attributes.filter(a => keys.includes(a.name))
        } else {
          defaultTemplateAttributes.value = []
        }
      } else {
        defaultTemplateAttributes.value = []
      }
    } catch (e) {
      console.error('Failed to load templates', e)
      defaultTemplateAttributes.value = []
    }
  } catch (err) {
    selectedModelBundle.value = emptyModelBundle()
    defaultTemplateAttributes.value = []
    ElMessage.error(err.message || '加载模型文件失败')
  }
}

function normalizeModelBundle(value) {
  return {
    capabilityModel: value?.capabilityModel || {},
    stateMachineModel: value?.stateMachineModel || {}
  }
}

watch(selectedModelId, id => {
  loadSelectedModelBundle(id)
})

function normalizeModel(raw) {
  const modelId = String(raw?.modelId || raw?.id || '')
  const categoryId = raw?.categoryId == null ? '' : String(raw.categoryId)
  const attributes = normalizeAttributes(firstDefined(raw?.attributes, raw?.capabilitySpec?.attributes))
  const adapterContract = normalizeAdapterContract(firstDefined(raw?.adapterContract, raw?.capabilitySpec?.adapterContract), attributes)
  const capabilities = normalizeCapabilities(firstDefined(raw?.capabilities, raw?.capabilitySpec?.capabilities, raw?.capabilitySpec?.functions))
  return {
    ...raw,
    modelId,
    categoryId,
    categoryName: raw?.categoryName || categoryNameById(categoryId),
    attributes,
    capabilities,
    adapterContract,
    ports: normalizePorts(firstDefined(raw?.ports, raw?.capabilitySpec?.ports), attributes),
    intrinsicConstraints: normalizeIntrinsicConstraints(firstDefined(raw?.intrinsicConstraints, raw?.intrinsicConstraint), attributes),
    stateMachineInterfaces: normalizeInterfaces(raw?.stateMachineInterfaces),
    opState: normalizeStateSpace(raw?.opState, 'IDLE', 'OP'),
    cmdState: normalizeStateSpace(raw?.cmdState, 'IDLE', 'CMD'),
    stateTransitions: normalizeTransitions(firstDefined(raw?.stateTransitions, raw?.opState?.transitions)).filter(row => !isCommandLifecycleTransition(row)),
    componentsBom: asArray(raw?.componentsBom)
  }
}

function openCreateDrawer() {
  replaceDraft(emptyDraft())
  drawerMode.value = 'create'
  prepareAdapterSourcePicker()
  drawerVisible.value = true
}

function openEditDrawer(model) {
  replaceDraft(fromModelToDraft(model))
  drawerMode.value = 'edit'
  prepareAdapterSourcePicker(draft.adapterContract?.config?.adapterName, draft.adapterContract?.config?.templateName)
  drawerVisible.value = true
}

async function saveDraft() {
  if (!draft.basic.modelName?.trim() || !draft.basic.categoryValue) {
    ElMessage.error('请填写完整模型基础信息')
    return
  }
  saving.value = true
  try {
    const propertyTypes = draft.defaultDataTemplateAttrs?.length ? await loadPropertyTypesForTemplate() : []
    const payload = buildSavePayload(true, propertyTypes)
    const res = await axios.post('/api/device/model/save', payload)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '保存失败')
      return
    }
    const savedModelId = String(res.data?.data?.modelId || payload.modelId || '')
    selectedModelId.value = savedModelId
    drawerVisible.value = false
    ElMessage.success('保存成功')
    await loadData()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function saveNewCategory() {
  if (!newCategoryFormRef.value) return
  await newCategoryFormRef.value.validate(async (valid) => {
    if (!valid) return
    savingCategory.value = true
    try {
      const payload = {
        categoryName: stringValue(newCategoryDraft.value.categoryName),
        parentCategoryId: newCategoryDraft.value.parentCategoryId ? Number(newCategoryDraft.value.parentCategoryId) : null,
        description: stringValue(newCategoryDraft.value.description)
      }
      const res = await axios.post('/api/device/category/save', payload)
      if (res.data.success) {
        ElMessage.success('类别已保存')
        await loadCategories()
        const savedId = res.data.data?.id || res.data.data?.categoryId
        if (savedId) draft.basic.categoryValue = String(savedId)
        newCategoryDraft.value = { categoryName: '', parentCategoryId: '', description: '' }
      } else {
        ElMessage.error(res.data.message || '保存类别失败')
      }
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '保存类别失败')
    } finally {
      savingCategory.value = false
    }
  })
}

async function deleteModel(id) {
  try {
    const res = await axios.delete('/api/device/model/delete/' + id)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '删除失败')
      return
    }
    ElMessage.success('删除成功')
    selectedModelId.value = ''
    await loadData()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}


function cleanInterfaces(rows) {
  return asArray(rows).map(row => ({
    name: stringValue(row.name),
    direction: row.direction || 'IN',
    interfaceType: row.interfaceType || 'ADAPTER',
    allowedSignals: asArray(row.allowedSignals).filter(Boolean)
  })).filter(row => row.name)
}

function cleanStateSpace(space, fallback, type) {
  const norm = normalizeStateSpace(space, fallback, type)
  return {
    initialStateName: norm.initialStateName,
    states: norm.states.map(s => {
      const stateName = stringValue(s.stateName)
      const onEntry = s.onEntry.map(a => ({ actionName: stringValue(a.actionName), payload: normalizePayload(a.payload || a.parameters) })).filter(a => a.actionName)
      return {
        stateName,
        onEntry: onEntry.length ? onEntry : defaultStateEntryActions(type, stateName)
      }
    }).filter(s => s.stateName)
  }
}

function cleanTransitions(rows) {
  return asArray(rows).map(r => ({
    description: stringValue(r.description),
    fromStateName: stringValue(r.fromStateName),
    toStateName: stringValue(r.toStateName),
    trigger: { interfaceName: stringValue(r.trigger?.interfaceName || adapterInterfaceName()), signalName: stringValue(r.trigger?.signalName) },
    actions: asArray(r.actions).map(a => ({ actionName: stringValue(a.actionName), payload: normalizePayload(a.payload || a.parameters) })).filter(a => a.actionName)
  })).filter(r => r.fromStateName && r.toStateName && r.trigger.interfaceName && r.trigger.signalName)
}

function cleanAdapterContract(contract, attrNameByKey) {
  const norm = normalizeAdapterContract(contract, [])
  return {
    config: norm.config,
    commands: norm.commands.map(cmd => ({
      commandName: stringValue(cmd.commandName),
      description: stringValue(cmd.description),
      commandParameters: cmd.commandParameters.filter(p => p.hidden !== true).map(p => ({
        paramName: stringValue(p.paramName),
        dataType: p.dataType,
        description: stringValue(p.description)
      })).filter(p => p.paramName)
    })).filter(cmd => cmd.commandName),
    telemetry: {
      adapterAttributes: norm.telemetry.adapterAttributes.map(a => ({
        name: stringValue(a.name), dataType: a.dataType, description: stringValue(a.description)
      })).filter(a => a.name),
      attributesMapping: norm.telemetry.attributesMapping.map(m => ({
        adapterAttrName: stringValue(m.adapterAttrName),
        modelAttributeName: lookupName(attrNameByKey, m.modelAttributeKey) || stringValue(m.modelAttributeName)
      })).filter(m => m.adapterAttrName && m.modelAttributeName)
    },
    events: {
      cmdEvents: norm.events.filter(e => e.eventType === 'CMD').map(e => ({ eventName: stringValue(e.eventName), description: stringValue(e.description) })).filter(e => e.eventName),
      opEvents: norm.events.filter(e => e.eventType === 'OP').map(e => ({ eventName: stringValue(e.eventName), description: stringValue(e.description) })).filter(e => e.eventName)
    }
  }
}

function buildSavePayload(includeBlankBasic = true, propertyTypes = []) {
  const attributeResult = materializeAttributes(draft.attributes)
  const attributes = attributeResult.rows
  const attrNameByKey = attributeResult.nameByKey
  const functionMappings = materializeFunctionMappings(draft.functionMappings)
  const capabilities = materializeCapabilities(draft.capabilities, functionMappings)
  const payload = {
    modelId: numericOrNull(draft.basic.modelId),
    modelName: includeBlankBasic ? draft.basic.modelName.trim() : draft.basic.modelName?.trim() || '',
    attributes,
    capabilities,
    adapterContract: cleanAdapterContract(draft.adapterContract, attrNameByKey),
    ports: materializePorts(draft.ports, attrNameByKey),
    intrinsicConstraints: materializeIntrinsicConstraints(draft.intrinsicConstraints, attrNameByKey),
    stateMachineInterfaces: cleanInterfaces(stateMachineInterfaceRows.value),
    opState: cleanStateSpace(draft.opState, 'IDLE', 'OP'),
    cmdState: cleanStateSpace(draft.cmdState, 'IDLE', 'CMD'),
    stateTransitions: cleanTransitions(draft.stateTransitions),
    componentsBom: asArray(draft.componentsBom),
    defaultDataTemplate: buildDefaultDataTemplate(attributeResult.rowByKey, propertyTypes)
  }
  if (isNumeric(draft.basic.categoryValue)) payload.categoryId = Number(draft.basic.categoryValue)
  else if (draft.basic.categoryValue) payload.categoryName = String(draft.basic.categoryValue).trim()
  return payload
}

async function loadPropertyTypesForTemplate() {
  const res = await axios.get('/api/data/property-type/list')
  return asArray(res.data?.data)
}

function buildDefaultDataTemplate(rowByKey, propertyTypes = []) {
  const selectedKeys = asArray(draft.defaultDataTemplateAttrs).filter(Boolean)
  if (selectedKeys.length === 0) return null
  const usedColumns = new Set()
  const details = []
  selectedKeys.map(key => rowByKey?.get(key)).filter(Boolean).forEach((attr, index) => {
    const columnName = reserveIdentifier(toSafeColumnName(attr.name, index), 'column_' + (index + 1), usedColumns)
    details.push({
      columnName,
      columnDesc: attr.displayName || attr.name || columnName,
      propertyTypeId: propertyTypeIdForDataType(attr.dataType, propertyTypes),
      columnLength: 255,
      deviceAttrKey: attr.name,
      defaultValue: ''
    })
    const unitValue = stringValue(attr.unit)
    if (unitValue) {
      const unitColumnName = reserveIdentifier(columnName + '_unit', 'column_' + (index + 1) + '_unit', usedColumns)
      details.push({
        columnName: unitColumnName,
        columnDesc: (attr.displayName || attr.name || columnName) + '单位',
        propertyTypeId: propertyTypeIdForDataType('STRING', propertyTypes),
        columnLength: 50,
        deviceAttrKey: '',
        defaultValue: unitValue
      })
    }
  })
  const validDetails = details.filter(detail => detail.columnName)
  if (validDetails.length === 0) return null
  return {
    enabled: true,
    templateName: (draft.basic.modelName?.trim() || '未命名模型') + ' 默认数据模板',
    templateDesc: '系统根据设备模型自动生成的默认数据模板',
    isDefault: true,
    details: validDetails
  }
}

function toSafeColumnName(name, index) {
  const text = stringValue(name)
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(text) ? text : 'column_' + (index + 1)
}

function propertyTypeIdForDataType(dataType, propertyTypes = []) {
  const type = normalizeDataType(dataType, 'STRING', attributeDataTypes)
  const candidates = {
    DOUBLE: ['double precision', 'double', 'float8', 'numeric', 'decimal'],
    INTEGER: ['integer', 'int4', 'int', 'bigint'],
    BOOLEAN: ['boolean', 'bool'],
    STRING: ['varchar', 'character varying', 'text', 'string']
  }[type] || ['text']
  const rows = asArray(propertyTypes)
  const exact = rows.find(row => candidates.includes(String(row.dbType || '').trim().toLowerCase()))
  if (exact) return exact.id
  const fuzzy = rows.find(row => candidates.some(candidate => String(row.dbType || '').trim().toLowerCase().includes(candidate)))
  return fuzzy?.id || null
}

function fromModelToDraft(model) {
  return {
    basic: { modelId: model.modelId, modelName: model.modelName || '', categoryValue: model.categoryId || '' },
    attributes: deepClone(model.attributes),
    capabilities: deepClone(model.capabilities),
    adapterContract: deepClone(model.adapterContract),
    ports: deepClone(model.ports),
    intrinsicConstraints: deepClone(model.intrinsicConstraints),
    stateMachineInterfaces: normalizeInterfaces(model.stateMachineInterfaces),
    functionMappings: extractFunctionMappings(model.capabilities, model.capabilities),
    opState: deepClone(model.opState),
    cmdState: deepClone(model.cmdState),
    stateTransitions: deepClone(model.stateTransitions),
    componentsBom: deepClone(model.componentsBom),
    defaultDataTemplateAttrs: defaultTemplateAttributes.value.map(a => a.name).filter(Boolean)
  }
}

function replaceDraft(next) {
  Object.assign(draft.basic, next.basic)
  const normalizedCapabilities = normalizeCapabilities(next.capabilities)
  const normalizedAttributes = normalizeAttributes(next.attributes)
  draft.attributes.splice(0, draft.attributes.length, ...normalizedAttributes)

  const attrKeys = (next.defaultDataTemplateAttrs || []).map(nameOrKey => {
     const attr = normalizedAttributes.find(a => a.name === nameOrKey || a._key === nameOrKey)
     return attr ? attr._key : null
  }).filter(Boolean)
  draft.defaultDataTemplateAttrs = attrKeys

  draft.capabilities.splice(0, draft.capabilities.length, ...normalizedCapabilities)
  draft.functionMappings.splice(0, draft.functionMappings.length, ...normalizeFunctionMappings(next.functionMappings))
  draft.ports.splice(0, draft.ports.length, ...normalizePorts(next.ports, draft.attributes))
  draft.intrinsicConstraints.splice(0, draft.intrinsicConstraints.length, ...normalizeIntrinsicConstraints(next.intrinsicConstraints, draft.attributes))
  draft.adapterContract = normalizeAdapterContract(next.adapterContract, draft.attributes)
  draft.stateMachineInterfaces.splice(0, draft.stateMachineInterfaces.length, ...normalizeInterfaces(next.stateMachineInterfaces))
  draft.stateTransitions.splice(0, draft.stateTransitions.length, ...normalizeTransitions(next.stateTransitions))
  draft.componentsBom.splice(0, draft.componentsBom.length, ...asArray(next.componentsBom))
  draft.opState = normalizeStateSpace(next.opState, 'IDLE', 'OP')
  draft.cmdState = normalizeStateSpace(next.cmdState, 'IDLE', 'CMD')
}

function addAttribute() { draft.attributes.push({ _key: makeUiKey('attr'), name: '', displayName: '', valueKind: 'CONTINUOUS', dataType: 'DOUBLE', unit: '' }) }

function removeAttribute(index) {
  const removed = draft.attributes[index]
  removeRow(draft.attributes, index)
  if (!removed?._key) return
  draft.ports.forEach(port => { if (port.bindingAttrKey === removed._key) port.bindingAttrKey = '' })
}

function addPort() { draft.ports.push({ _key: makeUiKey('port'), portName: '', displayName: '', direction: 'OUT', bindingAttrKey: '', bindingAttrName: '' }) }
function addCapability() { draft.capabilities.push({ _key: makeUiKey('cap'), name: '', displayName: '', adapterCommandName: '', parameters: [], parameterMapping: [] }) }
function removeCapability(capability) {
  removeObjectRow(draft.capabilities, capability)
  draft.functionMappings = draft.functionMappings.filter(mapping => mapping.capabilityKey !== capability._key)
}
function addCapabilityParameter(capability) { ensureArrayField(capability, 'parameters').push({ _key: makeUiKey('param'), name: '', displayName: '', dataType: 'DOUBLE' }) }

function removeCapabilityParameter(capability, index) {
  const removed = capability.parameters[index]
  removeRow(capability.parameters, index)
  if (removed?._key) capability.parameterMapping = asArray(capability.parameterMapping).filter(item => item.capabilityParamKey !== removed._key)
}

function addAdapterCommand() {
  draft.adapterContract.commands.push({ _key: makeUiKey('cmd'), commandName: '', commandParameters: [] })
}
function removeAdapterCommand(command) {
  removeObjectRow(draft.adapterContract.commands, command)
}
function addCommandParameter(command) {
  ensureArrayField(command, 'commandParameters').push({ _key: makeUiKey('cmd_param'), paramName: '', dataType: 'DOUBLE' })
}
function addAdapterAttribute() { draft.adapterContract.telemetry.adapterAttributes.push({ _key: makeUiKey('adapter_attr'), name: '', dataType: 'DOUBLE', description: '' }) }

function removeAdapterAttribute(index) {
  removeRow(draft.adapterContract.telemetry.adapterAttributes, index)
}

function addAdapterEvent() { draft.adapterContract.events.push({ _key: makeUiKey('event'), eventName: '', description: '' }) }
function removeAdapterEvent(index) { removeRow(draft.adapterContract.events, index) }

function addAttributeMapping() {
  draft.adapterContract.telemetry.attributesMapping.push({ _key: makeUiKey('attr_map'), adapterAttrName: '', modelAttributeKey: '', modelAttributeName: '' })
}

function handleAttributeMappingModelChange(row) {
  if (!row.modelAttributeKey) return
  if (draft.adapterContract.telemetry.attributesMapping.find(item => item !== row && item.modelAttributeKey === row.modelAttributeKey)) row.modelAttributeKey = ''
}

function handleAdapterAttributeMappingChange(row) {
  if (!row.adapterAttrName) return
  if (draft.adapterContract.telemetry.attributesMapping.find(item => item !== row && item.adapterAttrName === row.adapterAttrName)) row.adapterAttrName = ''
}

function isAttributeOptionUsed(key, row) { return !!key && draft.adapterContract.telemetry.attributesMapping.some(item => item !== row && item.modelAttributeKey === key) }
function isAdapterAttributeUsed(name, row) { return !!name && draft.adapterContract.telemetry.attributesMapping.some(item => item !== row && item.adapterAttrName === name) }

function addFunctionMapping() {
  draft.functionMappings.push({ _key: makeUiKey('function_map'), capabilityKey: '', adapterCommandName: '', parameterMapping: [] })
}

function addParameterMapping(mappingOwner) {
  ensureArrayField(mappingOwner, 'parameterMapping').push({ _key: makeUiKey('param_map'), commandParamName: '', capabilityParamKey: '', capabilityParamName: '', isFixedValue: false, fixedValue: '' })
}

function handleFunctionMappingCapabilityChange(row) {
  asArray(row.parameterMapping).forEach(mapping => mapping.capabilityParamKey = '')
}

function handleFunctionMappingCommandChange(row) {
  if (isAdapterCommandMapped(row.adapterCommandName, row)) row.adapterCommandName = ''
  asArray(row.parameterMapping).forEach(mapping => mapping.commandParamName = '')
}

function handleParameterFixedChange(mapping) {
  if (!mapping.isFixedValue) {
    mapping.fixedValue = ''
  }
}

function fixedValuePlaceholder(row, mapping) {
  if (!mapping.commandParamName) return '请输入固定值'
  const adapterParam = commandParameterByName(row.adapterCommandName, mapping.commandParamName)
  return adapterParam ? `输入值 (类型: ${adapterParam.dataType})` : '请输入固定值'
}

function handleParameterCommandChange(row, mapping) {
  if (isCommandParamMapped(row, mapping.commandParamName, mapping)) {
    mapping.commandParamName = ''
    return
  }
}

function handleCapabilityParameterChange(row, mapping) {
  const capability = draft.capabilities.find(c => c._key === row.capabilityKey)
  if (capability) {
    const param = (capability.parameters || []).find(p => p._key === mapping.capabilityParamKey)
    mapping.capabilityParamName = param ? stringValue(param.name || param.displayName) : ''
  }
}

function isCapabilityMapped(key, row) {
  return !!key && draft.functionMappings.some(mapping => mapping !== row && mapping.capabilityKey === key)
}

function isAdapterCommandMapped(name, row) {
  return !!name && draft.functionMappings.some(mapping => mapping !== row && mapping.adapterCommandName === name)
}

function isCommandParamMapped(row, paramName, current) {
  return asArray(row.parameterMapping).some(item => item !== current && item.commandParamName === paramName)
}

function findAttributeByKey(key) {
  return draft.attributes.find(a => a._key === key)
}

function commandParameterByName(commandName, paramName) {
  const cmd = draft.adapterContract.commands.find(c => c.commandName === commandName)
  if (!cmd) return null
  return cmd.commandParameters.find(p => p.paramName === paramName) || null
}

function capabilityParameterByKey(capabilityKey, paramKey) {
  const cap = draft.capabilities.find(c => c._key === capabilityKey)
  if (!cap) return null
  return cap.parameters.find(p => p._key === paramKey) || null
}

function isAttrDataTypeMatch(modelAttributeKey, adapterDataType) {
  if (!modelAttributeKey) return true
  const modelAttr = findAttributeByKey(modelAttributeKey)
  if (!modelAttr) return true
  return modelAttr.dataType === adapterDataType
}

function isParamDataTypeMatch(commandParamName, commandName, capabilityDataType) {
  if (!commandParamName || !commandName) return true
  const adapterParam = commandParameterByName(commandName, commandParamName)
  if (!adapterParam) return true
  return adapterParam.dataType === capabilityDataType
}

function isParameterMappingInvalid(row, mapping) {
  return !!parameterMappingWarning(row, mapping)
}

function parameterMappingWarning(row, mapping) {
  if (!mapping.commandParamName) return '未选择命令参数'
  if (!mapping.isFixedValue && !mapping.capabilityParamKey) return '未选择操作参数'
  if (mapping.isFixedValue && !mapping.fixedValue) return '未填写固定值'
  if (!mapping.isFixedValue) {
    const param = capabilityParameterByKey(row.capabilityKey, mapping.capabilityParamKey)
    if (param && !isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType)) {
      return '参数类型不匹配'
    }
  }
  return ''
}

function isCapabilityParamMapped(row, paramKey, current) {
  return !!paramKey && asArray(row.parameterMapping).some(mapping => mapping !== current && !mapping.isFixedValue && mapping.capabilityParamKey === paramKey)
}

function visibleCommandParameters(command) {
  return asArray(command?.commandParameters).filter(param => param.hidden !== true)
}

function addIntrinsicConstraint() {
  draft.intrinsicConstraints.push({ _key: makeUiKey('constraint'), objectAttributeKey: '', objectAttributeName: '', operator: 'GT', boundaryValue: '', violationStateName: '' })
}

function removeRow(rows, index) { rows.splice(index, 1) }

const opStateInputVisible = ref(false)
const opStateInputValue = ref('')
const OpStateInputRef = ref(null)

const showOpStateInput = () => {
  opStateInputVisible.value = true
  nextTick(() => {
    OpStateInputRef.value?.focus()
  })
}

const handleOpStateInputConfirm = () => {
  if (opStateInputValue.value) {
    if (!draft.opState.states) {
      draft.opState.states = []
    }
    const stateName = opStateInputValue.value.trim()
    if (stateName && !draft.opState.states.find(s => s.stateName === stateName)) {
      draft.opState.states.push({
        _key: makeUiKey('state'),
        stateName: stateName,
        onEntry: defaultStateEntryActions('OP', stateName)
      })
    }
  }
  opStateInputVisible.value = false
  opStateInputValue.value = ''
}

const removeOpState = (index) => {
  draft.opState.states.splice(index, 1)
}
function removeObjectRow(rows, row) { const index = rows.indexOf(row); if (index >= 0) rows.splice(index, 1) }

function addStateTransition() {
  draft.stateTransitions.push({
    _key: makeUiKey('transition'),
    fromStateName: '',
    toStateName: '',
    trigger: { interfaceName: adapterInterfaceName(), signalName: '' },
    actions: []
  })
}

function ensureTransitionAction(row) {
  if (!row.actions) row.actions = []
  row.actions.push({
    payload: { interfaceName: 'Interface_adapter_out', signalName: '' }
  })
}

function parsedAdapterConfig(adapter) {
  if (!adapter?.parsedConfig) return {}
  if (typeof adapter.parsedConfig === 'string') {
    try { return JSON.parse(adapter.parsedConfig) } catch { return {} }
  }
  return adapter.parsedConfig || {}
}

function prepareAdapterSourcePicker(adapterName = '', templateName = '') {
  selectedRegisteredAdapterName.value = adapterName || ''
  selectedRegisteredAdapterTemplate.value = templateName || ''
  fetchRegisteredAdapters()
}

async function fetchRegisteredAdapters() {
  registeredAdapterLoading.value = true
  try {
    const res = await axios.get('/api/adapter/index/list')
    registeredAdapters.value = res.data?.success ? asArray(res.data.data) : []
    if (selectedRegisteredAdapterName.value && !registeredAdapters.value.some(item => item.adapterName === selectedRegisteredAdapterName.value)) {
      selectedRegisteredAdapterName.value = ''
      selectedRegisteredAdapterTemplate.value = ''
    }
    if (selectedRegisteredAdapterName.value && selectedRegisteredAdapterTemplate.value) {
      const hasTemplate = registeredAdapterTemplateOptions.value.some(tpl => tpl.templateName === selectedRegisteredAdapterTemplate.value)
      if (!hasTemplate) selectedRegisteredAdapterTemplate.value = ''
    }
  } catch (error) {
    registeredAdapters.value = []
  } finally {
    registeredAdapterLoading.value = false
  }
}

function handleRegisteredAdapterChange() {
  selectedRegisteredAdapterTemplate.value = ''
}

async function applyRegisteredAdapterContract() {
  if (!selectedRegisteredAdapterName.value || !selectedRegisteredAdapterTemplate.value) {
    ElMessage.warning('请选择已注册 Adapter 和设备模板')
    return
  }
  registeredAdapterLoading.value = true
  try {
    const res = await axios.get(`/api/adapter/index/${encodeURIComponent(selectedRegisteredAdapterName.value)}/adapter-contract`, {
      params: { templateName: selectedRegisteredAdapterTemplate.value }
    })
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '载入 Adapter 契约失败')
      return
    }
    assignAdapterContract(res.data.data)
    ElMessage.success(`已载入 Adapter 契约: ${selectedRegisteredAdapterName.value} / ${selectedRegisteredAdapterTemplate.value}`)
  } finally {
    registeredAdapterLoading.value = false
  }
}

function buildAdapterContractFromManifestTemplate(parsed, template) {
  const commands = asArray(template.commands).map(command => ({
    commandName: stringValue(command.name || command.commandName),
    description: stringValue(command.description),
    commandParameters: asArray(command.parameters || command.commandParameters).map(normalizeCommandParameter)
  }))

  const adapterAttributes = asArray(template.attributes).map(attr => ({
    name: stringValue(attr.name),
    dataType: normalizeDataType(attr.dataType, 'DOUBLE', adapterDataTypes),
    description: stringValue(attr.description)
  }))

  return {
    config: { protocol: 'MQTT', adapterName: stringValue(parsed.adapterName), templateName: stringValue(template.templateName) },
    commands,
    telemetry: { adapterAttributes, attributesMapping: [] },
    events: normalizeEventsToFlatList(template.events)
  }
}

function assignAdapterContract(contract) {
  const normalized = normalizeAdapterContract(contract, draft.attributes)
  draft.adapterContract.config = normalized.config
  draft.adapterContract.commands.splice(0, draft.adapterContract.commands.length, ...normalized.commands)
  draft.adapterContract.telemetry.adapterAttributes.splice(0, draft.adapterContract.telemetry.adapterAttributes.length, ...normalized.telemetry.adapterAttributes)
  draft.adapterContract.telemetry.attributesMapping.splice(0, draft.adapterContract.telemetry.attributesMapping.length, ...normalized.telemetry.attributesMapping)
  draft.adapterContract.events.splice(0, draft.adapterContract.events.length, ...normalized.events)
}
function applyAdapterConfigText() {
  try {
    const parsed = JSON.parse(adapterConfigText.value)
    if (!parsed || !Array.isArray(parsed.deviceTemplates) || parsed.deviceTemplates.length === 0) {
      ElMessage.warning('配置文本中未找到合法的 deviceTemplates 数组')
      return
    }
    tempAdapterConfigRaw.value = parsed
    adapterTemplatesList.value = parsed.deviceTemplates.map(t => ({
      name: t.templateName,
      description: t.description
    }))
    if (adapterTemplatesList.value.length > 0) {
      selectedAdapterTemplate.value = adapterTemplatesList.value[0].name
    }
    adapterTemplateDialogVisible.value = true
  } catch (error) {
    ElMessage.error('配置文本不是有效的 JSON')
  }
}

function confirmAdapterTemplateSelection() {
  if (!selectedAdapterTemplate.value) {
    ElMessage.warning('请选择一个模板')
    return
  }
  const parsed = tempAdapterConfigRaw.value
  const template = parsed.deviceTemplates.find(t => t.templateName === selectedAdapterTemplate.value)
  if (!template) return

  assignAdapterContract(buildAdapterContractFromManifestTemplate(parsed, template))

  adapterTemplateDialogVisible.value = false
  ElMessage.success(`已成功解析并绑定模板: ${template.templateName}`)
}

function buildCapabilityModel(model) {
  const attributeResult = materializeAttributes(model.attributes)
  const functionMappings = extractFunctionMappings(model.capabilities, normalizeCapabilities(model.capabilities))
  return buildCapabilityModelFromPayload({
    modelId: model.modelId,
    modelName: model.modelName,
    categoryId: model.categoryId,
    attributes: attributeResult.rows,
    capabilities: materializeCapabilities(model.capabilities, functionMappings),
    adapterContract: cleanAdapterContract(model.adapterContract, attributeResult.nameByKey),
    ports: materializePorts(model.ports, attributeResult.nameByKey),
    intrinsicConstraints: materializeIntrinsicConstraints(model.intrinsicConstraints, attributeResult.nameByKey)
  })
}

function buildStateMachineModel(model) {
  const interfaces = asArray(model.stateMachineInterfaces).length ? cleanInterfaces(model.stateMachineInterfaces) : cleanInterfaces(defaultInterfaces(opEventNames(model.adapterContract?.events)))
  return buildStateMachineModelFromPayload({
    modelId: model.modelId,
    stateMachineInterfaces: interfaces,
    opState: cleanStateSpace(model.opState, 'IDLE', 'OP'),
    cmdState: cleanStateSpace(model.cmdState, 'IDLE', 'CMD'),
    stateTransitions: cleanTransitions(model.stateTransitions)
  })
}

function buildCapabilityModelFromPayload(payload) {
  return {
    metadata: {
      modelId: numericOrNull(payload.modelId),
      modelName: stringValue(payload.modelName),
      deviceCategoryId: numericOrNull(payload.categoryId)
    },
    attributes: asArray(payload.attributes),
    capabilities: asArray(payload.capabilities),
    adapterContract: payload.adapterContract || cleanAdapterContract(defaultAdapterContract()),
    ports: asArray(payload.ports),
    intrinsicConstraints: asArray(payload.intrinsicConstraints)
  }
}

function buildStateMachineModelFromPayload(payload) {
  return {
    deviceModelId: numericOrNull(payload.modelId),
    interfaces: cleanInterfaces(payload.stateMachineInterfaces),
    opStateSpace: cleanStateSpace(payload.opState, 'IDLE', 'OP'),
    cmdLifecycleSpace: cleanStateSpace(payload.cmdState, 'IDLE', 'CMD'),
    transitions: allStateTransitions(payload.stateTransitions)
  }
}

function opEventNames(events) {
  const rows = Array.isArray(events)
    ? events
    : [...asArray(events?.cmdEvents).map(event => ({ ...event, eventType: 'CMD' })), ...asArray(events?.opEvents).map(event => ({ ...event, eventType: 'OP' }))]
  return rows.filter(event => String(event.eventType || '').toUpperCase() !== 'CMD' && !standardCmdEvents.includes(event.eventName || event.name)).map(event => event.eventName || event.name).filter(Boolean)
}

function allStateTransitions(rows) {
  return [...defaultCommandLifecycleTransitions(), ...cleanTransitions(rows)]
}

function signalOptionsForInterface(interfaceName) {
  const iface = stateMachineInterfaceRows.value.find(item => item.name === interfaceName)
  return asArray(iface?.allowedSignals).filter(Boolean)
}

function uniqueStrings(values) {
  return [...new Set(asArray(values).map(stringValue).filter(Boolean))]
}

async function downloadModelBundle() {
  if (!selectedModel.value) return
  try {
    const bundle = await fetchModelBundle(selectedModel.value.modelId)
    selectedModelBundle.value = bundle
    downloadJson('device-model-' + selectedModel.value.modelId + '.json', bundle)
  } catch (err) {
    ElMessage.error(err.message || '导出模型文件失败')
  }
}

function downloadJson(filename, data) {
  const blob = new Blob([formatJson(data)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

function importAdapterFile(file) {
  const rawFile = file?.raw
  if (!rawFile) return false
  const reader = new FileReader()
  reader.onload = () => { adapterConfigText.value = String(reader.result || '') }
  reader.readAsText(rawFile, 'utf-8')
  return false
}


function normalizeCommandParameter(param) {
  const normalized = {
    paramName: stringValue(param.paramName || param.name),
    dataType: normalizeDataType(param.dataType, 'DOUBLE', adapterDataTypes),
    description: stringValue(param.description),
    hidden: !!param.hidden
  }
  if (param.hidden) {
    normalized.sourceField = stringValue(param.sourceField)
  }
  return normalized
}

function normalizeEventsToFlatList(events) {
  if (!events) return []
  const rawEvents = Array.isArray(events)
    ? events
    : [
        ...asArray(events.cmdEvents).map(e => ({ ...e, eventType: 'CMD' })),
        ...asArray(events.opEvents).map(e => ({ ...e, eventType: 'OP' }))
      ]
  return rawEvents.map(event => ({
    eventName: stringValue(event.eventName || event.name),
    description: stringValue(event.description),
    eventType: String(event.eventType || (String(event.eventName || event.name).startsWith('COMMAND_') ? 'CMD' : 'OP')).toUpperCase()
  }))
}

function capabilityMappingRows(capabilities) {
  return functionMappingGroups(capabilities).flatMap(group => {
    if (group.parameters.length === 0) {
      return [{
        capabilityDisplayName: group.capabilityDisplayName,
        adapterCommandName: group.adapterCommandName,
        commandParamName: '-',
        sourceLabel: '未配置参数映射'
      }]
    }
    return group.parameters.map(param => ({
      capabilityDisplayName: group.capabilityDisplayName,
      adapterCommandName: group.adapterCommandName,
      commandParamName: param.commandParamName,
      sourceLabel: param.isFixedValue ? '固定值：' + (param.fixedValue ?? '') : param.capabilityParamDisplayName
    }))
  })
}

function functionMappingGroups(capabilities) {
  return asArray(capabilities).map((capability, index) => {
    const mappings = asArray(capability.parameterMapping).map((mapping, mappingIndex) => ({
      key: String(index) + '-' + (mapping.commandParamName || mappingIndex),
      commandParamName: mapping.commandParamName || '-',
      capabilityParamDisplayName: mapping.isFixedValue ? '' : capabilityParamDisplayName(capability, mapping.capabilityParamName),
      isFixedValue: !!mapping.isFixedValue,
      fixedValue: mapping.fixedValue ?? ''
    }))
    return {
      key: capability.name || capability.displayName || String(index),
      capabilityDisplayName: capability.displayName || '未命名操作',
      adapterCommandName: capability.adapterCommandName || '-',
      parameters: mappings
    }
  })
}

function capabilityParamDisplayName(capability, paramName) {
  if (!paramName) return '-'
  const param = asArray(capability?.parameters).find(item => item.name === paramName || item.displayName === paramName)
  return param?.displayName || paramName
}

function eventTypeLabel(type) {
  const upper = String(type || '').toUpperCase()
  if (upper === 'CMD') return '指令周期'
  if (upper === 'OP') return '业务事件'
  return '事件'
}

function operatorLabel(operator) {
  const labels = { GT: '>', LT: '<', GE: '>=', LE: '<=', EQ: '=', NE: '!=', BETWEEN: '介于', IN: '属于' }
  return labels[operator] || operator || '-'
}

function summaryText(model) {
  const parts = []
  if (model.attributes.length) parts.push('属性')
  if (model.capabilities.length) parts.push('操作')
  return parts.length ? parts.join('、') : '尚未补充模型内容'
}

function onKeywordInput() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { pageNo.value = 1; loadData() }, 260)
}

function handleModelTreeNodeClick(data) {
  if (data?.type === 'model') selectedModelId.value = data.modelId
}
function categoryNameById(id) { return categories.value.find(item => String(item.id) === String(id))?.categoryName || '' }
function displayAttributeName(name, attributes) { if (!name) return '-'; const attr = asArray(attributes).find(item => item.name === name); return attr?.displayName || name }
function describeAction(action) {
  if (!action?.actionName) return ''
  if (action.actionName === 'SEND') {
    const interfaceName = action.payload?.interfaceName || '接口'
    const signalName = action.payload?.signalName || '信号'
    return '输出 ' + interfaceName + ' / ' + signalName
  }
  return action.actionName
}

function valueKindLabel(value) { return value === 'DISCRETE' ? '离散值' : '连续值' }
function directionLabel(value) { return value === 'IN' ? '输入' : '输出' }
function formatTime(value) { return value ? String(value).replace('T', ' ') : '-' }
function stringifyBrief(value) {
  const text = JSON.stringify(value || {})
  return text.length > 90 ? text.slice(0, 87) + '...' : text
}
function formatJson(value) { return JSON.stringify(value || {}, null, 2) }
function dataTypeOptions(base, current) { return current && !base.includes(current) ? [...base, current] : base }
function asArray(value) { return Array.isArray(value) ? value : [] }
function firstDefined(...values) { return values.find(value => value !== undefined && value !== null) }
function deepClone(value) { return JSON.parse(JSON.stringify(value ?? [])) }
function ensureArrayField(target, key) { if (!Array.isArray(target[key])) target[key] = []; return target[key] }
function isNumeric(value) { return value !== '' && value != null && !Number.isNaN(Number(value)) }
function numericOrNull(value) { return isNumeric(value) ? Number(value) : null }
function stringValue(value) { return value == null ? '' : String(value).trim() }
function normalizeDataType(value, fallback, allowed) { const text = String(value || '').toUpperCase(); return allowed.includes(text) ? text : fallback }
function makeUiKey(prefix) { return prefix + '_' + Math.random().toString(36).substr(2, 9) }
function findKeyByName(rows, name) { return asArray(rows).find(item => item.name === name)?._key || '' }
function lookupName(nameByKey, key) {
  if (!key || !nameByKey) return ''
  return nameByKey instanceof Map ? nameByKey.get(key) || '' : nameByKey[key] || ''
}
function reserveIdentifier(value, fallback, used) {
  const base = stringValue(value) || fallback
  let candidate = base
  let index = 2
  while (used.has(candidate)) {
    candidate = base + '_' + index
    index += 1
  }
  used.add(candidate)
  return candidate
}
function materializedName(item, fallback, used) {
  return reserveIdentifier(item?.name, fallback, used)
}

function materializeAttributes(rows) {
  const nameByKey = new Map()
  const rowByKey = new Map()
  const usedNames = new Set()
  const materializedRows = asArray(rows).filter(item => stringValue(item.name || item.displayName)).map((item, index) => {
    const name = materializedName(item, 'attribute_' + (index + 1), usedNames)
    const row = { name, displayName: stringValue(item.displayName || name), valueKind: item.valueKind || 'CONTINUOUS', dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes), unit: stringValue(item.unit) }
    nameByKey.set(item._key, name)
    rowByKey.set(item._key, row)
    return row
  })
  return { rows: materializedRows, nameByKey, rowByKey }
}

function materializePorts(rows, attrNameByKey) {
  const usedNames = new Set()
  return asArray(rows).filter(item => stringValue(item.portName || item.displayName) || item.bindingAttrKey || item.bindingAttrName).map((item, index) => ({
    portName: reserveIdentifier(item.portName || item.displayName, 'port_' + (index + 1), usedNames),
    direction: item.direction || 'OUT',
    bindingAttrName: lookupName(attrNameByKey, item.bindingAttrKey) || stringValue(item.bindingAttrName)
  })).filter(item => item.portName && item.bindingAttrName)
}

function materializeCapabilities(rows, functionMappings = []) {
  const usedCapabilityNames = new Set()
  return asArray(rows).filter(item => stringValue(item.name || item.displayName)).map((item, index) => {
    const mapping = functionMappings.find(fm => fm.capabilityKey === item._key)
    const paramNames = new Map()
    const usedParamNames = new Set()
    const parameters = asArray(item.parameters).filter(param => stringValue(param.name || param.displayName)).map((param, paramIndex) => {
      const name = materializedName(param, 'parameter_' + (paramIndex + 1), usedParamNames)
      paramNames.set(param._key, name)
      return { name, displayName: stringValue(param.displayName || name), dataType: normalizeDataType(param.dataType, 'DOUBLE', attributeDataTypes) }
    })
    const name = materializedName(item, 'capability_' + (index + 1), usedCapabilityNames)
    return {
      name,
      adapterCommandName: mapping?.adapterCommandName || item.adapterCommandName,
      displayName: stringValue(item.displayName || name),
      parameters,
      parameterMapping: materializeParameterMappings(mapping?.parameterMapping || item.parameterMapping, paramNames)
    }
  })
}

function materializeParameterMappings(rows, paramNames) {
  return asArray(rows).map(pm => {
    const commandParamName = stringValue(pm.commandParamName)
    const isFixedValue = !!pm.isFixedValue
    const out = { commandParamName, isFixedValue }
    if (isFixedValue) {
      out.fixedValue = pm.fixedValue
    } else {
      out.capabilityParamName = lookupName(paramNames, pm.capabilityParamKey) || stringValue(pm.capabilityParamName)
    }
    return out
  }).filter(pm => pm.commandParamName && (pm.isFixedValue || pm.capabilityParamName))
}

function materializeFunctionMappings(rows) {
  return asArray(rows).map(row => ({ capabilityKey: row.capabilityKey, adapterCommandName: row.adapterCommandName, parameterMapping: row.parameterMapping }))
}

function materializeIntrinsicConstraints(rows, attrNameByKey) {
  return asArray(rows).map(row => ({
    objectAttributeName: lookupName(attrNameByKey, row.objectAttributeKey) || stringValue(row.objectAttributeName),
    operator: normalizeOperator(row.operator),
    boundaryValue: isNumeric(row.boundaryValue) ? Number(row.boundaryValue) : row.boundaryValue,
    violationStateName: stringValue(row.violationStateName)
  })).filter(row => row.objectAttributeName && row.operator && row.violationStateName)
}

function normalizeAttributes(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('attr'), name: stringValue(item.name), displayName: stringValue(item.displayName || item.name), valueKind: item.valueKind === 'DISCRETE' ? 'DISCRETE' : 'CONTINUOUS', dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes), unit: stringValue(item.unit) })) }

// Normalize capabilities
function normalizeCapabilities(value) {
  return asArray(value).map(item => {
    const params = asArray(item.parameters).map(param => ({
      _key: param._key || makeUiKey('param'),
      name: stringValue(param.name),
      displayName: stringValue(param.displayName || param.name),
      dataType: normalizeDataType(param.dataType, 'DOUBLE', attributeDataTypes)
    }))
    const parameterMapping = asArray(item.parameterMapping).map(mapping => ({
      _key: mapping._key || makeUiKey('param_map'),
      commandParamName: stringValue(mapping.commandParamName),
      capabilityParamName: stringValue(mapping.capabilityParamName),
      capabilityParamKey: mapping.capabilityParamKey || findKeyByName(params, mapping.capabilityParamName),
      isFixedValue: !!mapping.isFixedValue,
      fixedValue: mapping.fixedValue ?? ''
    }))
    return {
      _key: item._key || makeUiKey('cap'),
      name: stringValue(item.name),
      displayName: stringValue(item.displayName || item.name),
      adapterCommandName: stringValue(item.adapterCommandName),
      parameters: params,
      parameterMapping
    }
  })
}

function extractFunctionMappings(capabilities, normalizedCapabilities = null) {
  const sourceRows = asArray(capabilities)
  const normalizedRows = normalizedCapabilities || normalizeCapabilities(capabilities)
  return normalizedRows.map((capability, index) => {
    const source = sourceRows.find(item => stringValue(item.name) === capability.name) || sourceRows[index] || {}
    return {
      _key: makeUiKey('function_map'),
      capabilityKey: capability._key,
      adapterCommandName: stringValue(source.adapterCommandName || capability.adapterCommandName),
      parameterMapping: asArray(source.parameterMapping).map(mapping => ({
        _key: mapping._key || makeUiKey('param_map'),
        commandParamName: stringValue(mapping.commandParamName),
        capabilityParamName: stringValue(mapping.capabilityParamName),
        capabilityParamKey: mapping.capabilityParamKey || findKeyByName(capability.parameters, mapping.capabilityParamName),
        isFixedValue: !!mapping.isFixedValue,
        fixedValue: mapping.fixedValue ?? ''
      }))
    }
  })
}

function normalizeFunctionMappings(value) {
  return asArray(value).map(item => ({
    _key: item._key || makeUiKey('function_map'),
    capabilityKey: item.capabilityKey || '',
    adapterCommandName: stringValue(item.adapterCommandName),
    parameterMapping: asArray(item.parameterMapping).map(mapping => ({
      _key: mapping._key || makeUiKey('param_map'),
      commandParamName: stringValue(mapping.commandParamName),
      capabilityParamName: stringValue(mapping.capabilityParamName),
      capabilityParamKey: mapping.capabilityParamKey || '',
      isFixedValue: !!mapping.isFixedValue,
      fixedValue: mapping.fixedValue ?? ''
    }))
  }))
}

function normalizeAdapterContract(value, attributes = []) {
  const contract = value || {}
  const telemetry = contract.telemetry || {}

  let eventsList = []
  if (contract.events && typeof contract.events === 'object' && !Array.isArray(contract.events)) {
    asArray(contract.events.cmdEvents).forEach(event => {
      eventsList.push({
        _key: event._key || makeUiKey('event'),
        eventName: stringValue(event.eventName || event.name),
        description: stringValue(event.description),
        eventType: 'CMD'
      })
    })
    asArray(contract.events.opEvents).forEach(event => {
      eventsList.push({
        _key: event._key || makeUiKey('event'),
        eventName: stringValue(event.eventName || event.name),
        description: stringValue(event.description),
        eventType: 'OP'
      })
    })
  } else {
    eventsList = asArray(contract.events || contract.adapterEvents).map(event => ({
      _key: event._key || makeUiKey('event'),
      eventName: stringValue(event.eventName || event.name),
      description: stringValue(event.description),
      eventType: String(event.eventType || (String(event.eventName || event.name).startsWith('COMMAND_') ? 'CMD' : 'OP')).toUpperCase()
    }))
  }

  return {
    config: { protocol: contract.config?.protocol || contract.protocol || 'MQTT', adapterName: stringValue(contract.config?.adapterName || contract.adapterName), templateName: stringValue(contract.config?.templateName || contract.templateName) },
    commands: asArray(contract.commands).map(command => ({
      _key: command._key || makeUiKey('cmd'),
      commandName: stringValue(command.commandName || command.name),
      description: stringValue(command.description),
      commandParameters: asArray(command.commandParameters || command.parameters).map(param => {
        const row = { _key: param._key || makeUiKey('cmd_param'), paramName: stringValue(param.paramName || param.name), dataType: normalizeDataType(param.dataType || param.type, 'DOUBLE', adapterDataTypes), description: stringValue(param.description) }
        if (param.hidden === true) {
          row.hidden = true
          row.sourceField = stringValue(param.sourceField)
        }
        return row
      })
    })),
    telemetry: { adapterAttributes: asArray(telemetry.adapterAttributes).map(attr => ({ _key: attr._key || makeUiKey('adapter_attr'), name: stringValue(attr.name), dataType: normalizeDataType(attr.dataType || attr.type, 'DOUBLE', adapterDataTypes), description: stringValue(attr.description) })), attributesMapping: asArray(telemetry.attributesMapping).map(mapping => ({ _key: mapping._key || makeUiKey('attr_map'), adapterAttrName: stringValue(mapping.adapterAttrName), modelAttributeName: stringValue(mapping.modelAttributeName), modelAttributeKey: mapping.modelAttributeKey || findKeyByName(attributes, mapping.modelAttributeName) })) },
    events: eventsList
  }
}

function normalizePorts(value, attributes = []) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('port'), portName: stringValue(item.portName), displayName: stringValue(item.displayName || item.portName), direction: item.direction || 'OUT', bindingAttrName: stringValue(item.bindingAttrName), bindingAttrKey: item.bindingAttrKey || findKeyByName(attributes, item.bindingAttrName) })) }
function normalizeOperator(value) { const text = String(value || '').toUpperCase(); return operators.includes(text) ? text : 'GT' }
function normalizeIntrinsicConstraints(value, attributes = []) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('constraint'), objectAttributeName: stringValue(item.objectAttributeName || item.targetAttr), objectAttributeKey: item.objectAttributeKey || findKeyByName(attributes, item.objectAttributeName || item.targetAttr), operator: normalizeOperator(item.operator), boundaryValue: firstDefined(item.boundaryValue, item.threshold, ''), violationStateName: stringValue(item.violationStateName || item.violationStateRef) })) }
function normalizeInterfaces(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('iface'), name: stringValue(item.name), direction: item.direction || 'IN', interfaceType: item.interfaceType || 'ADAPTER', allowedSignals: asArray(item.allowedSignals).map(stringValue).filter(Boolean) })) }

function normalizeStateSpace(value, fallback, spaceType) {
  const source = value && typeof value === 'object' ? value : defaultStateSpace(fallback)
  const states = asArray(source.states).length ? asArray(source.states) : defaultStateSpace(fallback).states
  return {
    initialStateName: stringValue(source.initialStateName || states[0]?.stateName || fallback),
    states: states.map(item => {
      const onEntry = asArray(item.onEntry).map(action => ({
        _key: action._key || makeUiKey('action'),
        actionName: stringValue(action.actionName),
        payload: normalizePayload(action.payload || action.parameters)
      }))
      return {
        _key: item._key || makeUiKey('state'),
        stateName: stringValue(item.stateName || item.name),
        onEntry
      }
    })
  }
}

function normalizeTransitions(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('transition'), description: stringValue(item.description), fromStateName: stringValue(item.fromStateName), toStateName: stringValue(item.toStateName), trigger: { interfaceName: stringValue(item.trigger?.interfaceName || adapterInterfaceName()), signalName: stringValue(item.trigger?.signalName) }, actions: asArray(item.actions).map(action => ({ _key: action._key || makeUiKey('action'), actionName: stringValue(action.actionName), payload: normalizePayload(action.payload || action.parameters) })) })) }
function normalizePayload(value) { return value && typeof value === 'object' && !Array.isArray(value) ? value : {} }

onMounted(loadData)
</script>

<style scoped>
.device-model-page { display: flex; flex-direction: column; height: calc(100vh - 52px); min-height: 0; padding: 0; gap: 0; background: #eef2f6; color: var(--color-text-main); font-size: 14px; }
.page-header { display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; gap: 16px; }
.page-title h1 { margin: 0 0 4px; font-size: 22px; line-height: 1.25; letter-spacing: 0; }
.page-title p { margin: 0; color: var(--color-text-sub); font-size: 13px; }
.header-actions, .detail-actions, .section-title, .config-import, .drawer-footer, .section-actions { display: flex; align-items: center; gap: 8px; }
.content-shell { flex: 1; min-height: 0; display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 0; border-top: 1px solid #ccd6e3; }
.model-list-panel, .detail-panel { min-height: 0; background: #fff; border: 0; border-radius: 0; box-shadow: none; }
.model-list-panel { display: flex; flex-direction: column; overflow: hidden; border-right: 1px solid #ccd6e3; }
.detail-anchor-layout { min-height: 0; background: #fff; border-top: 1px solid #e5e7eb; }
.detail-anchor-menu { padding: 8px; background: #f8fafc; border-right: 1px solid #dfe3ea !important; }
.detail-anchor-menu :deep(.el-anchor__link) { padding: 7px 10px; border-radius: 4px; font-size: 13px; }
.detail-anchor-menu :deep(.el-anchor__link.is-active) { background: #dbeafe; color: #1d4ed8; font-weight: 600; }
.anchor-section { scroll-margin-top: 8px; }
.industrial-section { margin-bottom: 10px; padding-top: 4px; }
.anchor-section > h2, .industrial-section > h2 { margin: 0 0 8px !important; color: #111827; font-size: 16px; line-height: 1.35; }
.list-tools { padding: 10px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.model-tree-tools { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.category-model-tree { background: transparent; }
.category-model-tree :deep(.el-tree-node__content) { min-height: 36px; height: auto; border-bottom: 1px solid #e2e8f0; }
.category-model-tree :deep(.el-tree-node__content:hover) { background: #eef6ff; }
.category-model-node { width: 100%; min-width: 0; padding: 4px 6px 4px 0; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: center; }
.category-model-node.category .node-title { color: #334155; font-weight: 800; }
.category-model-node.model .node-title { color: #0f172a; font-weight: 700; }
.category-model-node.model.active { box-shadow: inset 3px 0 0 #2563eb; background: #dbeafe; }
.category-model-node .node-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.category-model-node .node-meta { color: #64748b; font-size: 12px; white-space: nowrap; }
.model-list { flex: 1; min-height: 0; padding: 8px; background: #f8fafc; }
.model-row { width: 100%; display: flex; flex-direction: column; align-items: flex-start; gap: 3px; border: 1px solid #e5e7eb; background: #fff; border-radius: 4px; padding: 9px 10px; text-align: left; cursor: pointer; color: var(--color-text-main); }
.model-row + .model-row { margin-top: 6px; }
.model-row:hover { background: #f3f6fa; border-color: #cbd5e1; }
.model-row.active { background: #e8f1fb; border-color: #7fb3e8; box-shadow: inset 3px 0 0 #1d4ed8; }
.model-name { max-width: 100%; font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-meta, .model-summary { max-width: 100%; color: var(--color-text-sub); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.list-footer { flex-shrink: 0; padding: 10px 12px; border-top: 1px solid #e5e7eb; }
.detail-panel { display: flex; flex-direction: column; overflow: hidden; padding: 0; }
.global-top-bar { flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0 !important; padding: 10px 14px !important; background: #fff; border-bottom: 1px solid #e5e7eb !important; }
.global-toolbar-title { color: #0f172a; font-size: 16px; font-weight: 750; }
.global-toolbar-actions { display: flex; align-items: center; gap: 8px; }
.detail-head { flex-shrink: 0; display: flex; justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid #e5e7eb; background: #fff; }
.detail-title { min-width: 0; }
.detail-title h2 { margin: 0 0 4px; font-size: 20px; line-height: 1.25; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; letter-spacing: 0; }
.detail-title p { margin: 0; color: var(--color-text-sub); font-size: 13px; }
.detail-tabs { flex: 1; min-height: 0; overflow: hidden; }
.detail-tabs :deep(.el-tabs__content) { height: calc(100% - 46px); overflow: auto; padding-right: 4px; }
.info-section, .drawer-section { margin-top: 8px; }
.info-section { border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px; background: #fff; }
.drawer-section { border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px; background: #fff; }
.section-title { justify-content: space-between; margin-bottom: 8px; min-height: 26px; }
.section-title h3 { margin: 0; font-size: 15px; line-height: 1.3; letter-spacing: 0; }
.tag-gap { margin: 2px 6px 2px 0; }
.muted-text { color: var(--color-text-sub); font-size: 12px; }
.summary-card-list, .editor-card-list { display: grid; gap: 8px; }
.summary-card, .editor-card { border: 1px solid #dfe4ed; border-radius: 4px; background: #fff; overflow: hidden; }
.summary-card { padding: 9px 10px; box-shadow: none; }
.summary-card-head, .editor-card-head, .nested-toolbar, .locked-heading, .locked-action, .action-toolbar { display: flex; align-items: center; gap: 8px; }
.summary-card-head, .editor-card-head { justify-content: space-between; }
.summary-card-title, .editor-card-title { min-width: 0; display: flex; align-items: center; gap: 8px; flex: 1; }
.summary-card-title { flex-direction: column; align-items: flex-start; gap: 2px; }
.summary-card-title strong { font-size: 14px; line-height: 1.35; }
.summary-card-title span { max-width: 100%; color: var(--color-text-sub); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-card-body { margin-top: 6px; padding-left: 0; }
.item-index { width: 22px; height: 22px; flex: 0 0 22px; display: inline-flex; align-items: center; justify-content: center; border-radius: 6px; background: #eef2f7; color: #475569; font-size: 12px; font-weight: 700; }
.capability-summary-card, .capability-editor-card { border-left: 3px solid #409eff; }
.command-summary-card, .command-editor-card { border-left: 3px solid #10b981; }
.editor-card { padding: 9px; background: #fbfcfe; }
.editor-card-title :deep(.el-input) { flex: 1; min-width: 180px; }
.capability-title-editor { flex-wrap: wrap; }
.capability-title-editor :deep(.el-input) { max-width: 320px; }
.nested-toolbar { justify-content: space-between; margin: 10px 0 8px; color: var(--color-text-sub); font-size: 12px; font-weight: 600; }
.nested-table { background: #fff; }
.compact-empty { display: flex; align-items: center; min-height: 30px; padding: 7px 10px; border: 1px dashed #cbd5e1; border-radius: 4px; background: #f8fafc; color: #64748b; font-size: 13px; line-height: 1.4; }
.block-empty { margin-top: 4px; }
.inline-empty { margin-top: 8px; }
.template-attr-option { margin-right: 14px; margin-bottom: 8px; padding: 7px 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #f8fafc; }
.template-attr-name { font-weight: 600; color: #0f172a; }
.template-attr-meta { margin-left: 8px; color: #64748b; font-size: 12px; }
.model-json-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; min-height: 0; }
.json-panel { min-width: 0; border: 1px solid #dfe4ed; border-radius: 4px; padding: 10px; background: #fff; }
.json-panel pre { margin: 0; max-height: 560px; overflow: auto; padding: 12px; border-radius: 6px; background: #111827; color: #e5e7eb; font-size: 12px; line-height: 1.55; tab-size: 2; }
.info-section :deep(.el-table) { --el-table-header-bg-color: #f8fafc; }
.info-section :deep(.el-table th.el-table__cell) { color: #334155; font-weight: 600; }
.drawer-body { height: 100%; min-height: 0; }
.drawer-tabs { height: 100%; }
.drawer-tabs :deep(.el-tabs__content) { height: 100%; overflow: auto; padding: 0 4px 18px 18px; }
.drawer-tabs :deep(.el-tabs__header) { width: 116px; }
.basic-form, .mapping-form { max-width: 760px; }
.basic-form :deep(.el-select), .mapping-form :deep(.el-select) { width: 100%; }
.config-import { margin-bottom: 8px; }
.registered-adapter-picker { width: 100%; display: grid; grid-template-columns: minmax(180px, 1.1fr) minmax(180px, 1fr) auto auto; gap: 8px; align-items: center; }
.registered-adapter-picker :deep(.el-select) { width: 100%; }
.param-map-editor { display: flex; flex-direction: column; gap: 8px; }
.function-mapping-card-list { display: grid; gap: 10px; }
.function-mapping-editor-card { border: 1px solid #dbe4ef; border-left: 3px solid #2563eb; border-radius: 6px; background: #fff; padding: 10px; }
.function-map-editor-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.function-map-selects { flex: 1; min-width: 0; display: grid; grid-template-columns: minmax(180px, 1fr) 28px minmax(180px, 1fr); align-items: end; gap: 8px; }
.function-map-selects label { min-width: 0; display: grid; gap: 4px; }
.function-map-selects label > span { color: #64748b; font-size: 12px; font-weight: 700; }
.mapping-direction { align-self: center; color: #64748b; text-align: center; font-weight: 700; }
.compact-param-editor { margin-top: 10px; }
.param-map-row { display: grid; grid-template-columns: minmax(160px, 1fr) minmax(160px, 1fr) 104px 28px; align-items: end; gap: 8px; padding: 8px; border: 1px solid transparent; border-radius: 6px; background: #f8fafc; }
.param-map-row.fixed { grid-template-columns: minmax(150px, 1fr) minmax(150px, 1fr) 104px minmax(150px, 1fr) 28px; }
.param-map-row.invalid { border-color: #f4b4b4; background: #fff7f7; }
.param-map-field, .param-map-mode { min-width: 0; display: grid; gap: 4px; }
.param-map-label { color: #64748b; font-size: 11px; font-weight: 700; line-height: 1.2; }
.fixed-input-field .el-input { width: 100%; }
.param-delete-btn { align-self: center; }
.map-warning { grid-column: 1 / -1; color: #c2410c; font-size: 12px; line-height: 1.4; }
.locked-heading { display: flex; align-items: center; gap: 8px; min-width: 0; }
.locked-heading h3 { margin: 0; }
.locked-section { background: #ffffff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; }
.state-group-title { margin: 24px 0 12px; padding-bottom: 8px; border-bottom: 1px solid var(--el-border-color-lighter); color: var(--el-text-color-primary); font-size: 16px; font-weight: 600; }
.state-group-title.first { margin-top: 0; }
.state-card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.state-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; }
.state-summary-row { display: grid; grid-template-columns: 72px minmax(0, 1fr); align-items: center; gap: 10px; min-height: 32px; margin-top: 8px; }
.state-summary-row.align-top { align-items: flex-start; }
.state-summary-label { color: #64748b; font-size: 12px; line-height: 24px; }
.state-token-list { display: flex; flex-wrap: wrap; gap: 6px; min-width: 0; }
.state-token { border: 0; }
.state-token.filled { background: #dcfce7; color: #166534; }
.closable-state-token :deep(.el-tag__content) { display: inline-flex; align-items: center; gap: 4px; min-width: 0; }
.state-token-text { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.state-token-warning-slot, .warning-slot { width: 16px; min-width: 16px; display: inline-flex; align-items: center; justify-content: center; }
.state-warning-icon, .inline-warning-icon { color: #dc2626; font-size: 14px; line-height: 1; }
.state-input-with-warning, .field-with-warning { display: grid; grid-template-columns: 16px minmax(0, 1fr); align-items: center; gap: 6px; width: 100%; }
.state-input-with-warning { grid-template-columns: minmax(0, 1fr) 16px; }
.state-warning-panel { margin: 0 0 10px; padding: 8px 10px; border: 1px solid #fecaca; border-radius: 6px; background: #fff7f7; display: flex; flex-direction: column; gap: 4px; }
.state-warning-item { display: flex; align-items: center; gap: 6px; color: #991b1b; font-size: 12px; line-height: 1.4; }
.state-inline-select { width: 100%; }
.compact-state-table { margin-top: 10px; background: #fff; }
.compact-transition-table { width: 100%; }
.compact-transition-table :deep(.el-table__cell) { padding: 6px 8px; }
.transition-action-cell { display: grid; grid-template-columns: minmax(0, 1fr) 28px; gap: 6px; align-items: start; }
.transition-action-list { display: flex; flex-direction: column; gap: 6px; min-width: 0; align-items: flex-start; }
.transition-action-row { display: grid; grid-template-columns: 34px minmax(120px, 1fr) 26px; align-items: center; gap: 6px; width: 100%; }

.boxed-section { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px; background-color: #fff; }
.locked-table :deep(.el-table__body-wrapper) { background: #ffffff; }
.locked-action { max-width: 100%; color: #475569; font-size: 12px; line-height: 1.45; display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; white-space: normal; }
.locked-value { font-weight: 600; color: #334155; }
.action-editor { display: flex; flex-direction: column; gap: 6px; }
.action-row { display: grid; grid-template-columns: 112px minmax(220px, 1fr) 34px; align-items: center; gap: 6px; }
.action-toolbar { justify-content: flex-start; }
.drawer-footer { justify-content: flex-end; }
.category-manager-layout { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(260px, 0.8fr); gap: 12px; }
.category-existing-panel, .category-create-panel { border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; padding: 10px; }
.dialog-section-title { margin-bottom: 8px; color: #0f172a; font-weight: 800; font-size: 14px; }
.category-create-panel :deep(.el-select) { width: 100%; }

/* Premium Action & Parameter Mapping Styles */
.action-visual-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 2px 6px;
  margin: 2px 0;
  max-width: 100%;
}
.inline-action {
  margin-left: 6px !important;
  vertical-align: middle;
}
.compact-payload {
  font-family: Consolas, Monaco, Lucida Console, monospace;
  font-size: 11px;
  color: #0f172a;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 3px;
  padding: 1px 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
}
.actions-list-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  max-width: 100%;
}
.locked-action-container {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}
.lock-tag-flex {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  line-height: 22px;
}
.lock-tag-flex :deep(.el-icon) {
  margin-right: 2px;
  font-size: 12px;
}
.param-map-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 4px;
  padding: 2px 4px;
}
.no-mapping-placeholder {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-style: italic;
  display: inline-flex;
  align-items: center;
}
.add-mapping-btn {
  align-self: flex-end;
}


.section-heading { display: flex; align-items: center; gap: 8px; margin: 0 0 8px !important; padding-left: 10px; border-left: 4px solid #2563eb; color: #0f172a; font-size: 16px; font-weight: 700; }
.section-cluster { box-shadow: inset 3px 0 0 #dbeafe; }
.compact-section { padding-bottom: 8px; }
.section-count { color: #64748b; font-size: 12px; font-weight: 500; }
.attribute-grid, .operation-grid, .port-grid, .mapping-grid, .event-chip-grid, .constraint-list, .transition-card-list, .function-map-list { display: grid; gap: 8px; }
.attribute-grid { grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); }
.adapter-attribute-grid { grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
.operation-grid { grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); }
.port-grid { grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); }
.mapping-grid { grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
.attribute-tile, .operation-card, .port-tile, .mapping-tile, .function-map-card, .transition-view-card, .constraint-card { min-width: 0; border: 1px solid #dbe2ea; border-radius: 5px; background: #fbfdff; }
.attribute-tile, .port-tile, .mapping-tile, .constraint-card { padding: 9px 10px; }
.tile-title, .port-name { color: #0f172a; font-size: 14px; font-weight: 650; line-height: 1.35; }
.tile-meta, .port-meta { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; color: #475569; font-size: 12px; }
.tile-meta span, .port-meta span { display: inline-flex; align-items: center; min-height: 22px; padding: 1px 7px; border: 1px solid #d7dee8; border-radius: 4px; background: #fff; }
.operation-card { padding: 10px; border-left: 3px solid #2563eb; }
.adapter-command-card { border-left-color: #059669; }
.operation-head { display: flex; align-items: flex-start; gap: 9px; min-width: 0; }
.operation-head > div { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.operation-head strong { color: #0f172a; font-size: 15px; line-height: 1.35; }
.operation-head span:not(.item-index) { color: #64748b; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.param-chip-row, .action-chip-row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.info-chip, .action-chip, .fixed-value-chip { display: inline-flex; align-items: center; min-height: 24px; padding: 2px 8px; border-radius: 4px; font-size: 12px; line-height: 1.3; }
.info-chip { border: 1px solid #bfdbfe; background: #eff6ff; color: #1d4ed8; }
.action-chip { border: 1px solid #cbd5e1; background: #fff; color: #334155; }
.fixed-value-chip { border: 1px solid #fde68a; background: #fffbeb; color: #92400e; }
.event-chip-grid { grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); }
.event-chip { min-width: 0; display: grid; gap: 3px; padding: 8px 10px; border: 1px solid #dbe2ea; border-left: 3px solid #94a3b8; border-radius: 5px; background: #fbfdff; }
.event-chip b { color: #0f172a; font-size: 13px; line-height: 1.35; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-chip em { color: #64748b; font-size: 12px; font-style: normal; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-chip-cmd { border-left-color: #2563eb; }
.event-chip-op { border-left-color: #059669; }
.mapping-tile { display: grid; grid-template-columns: minmax(150px, 1fr) 28px minmax(150px, 1fr); align-items: center; gap: 8px; }
.mapping-tile-labeled { padding: 8px; background: #f8fafc; }
.mapping-source, .mapping-target { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.mapping-source { color: #0f172a; font-weight: 600; }
.mapping-target { color: #1d4ed8; }
.mapping-side { min-width: 0; display: grid; grid-template-columns: 64px minmax(0, 1fr); align-items: center; gap: 8px; padding: 7px 8px; border: 1px solid #dbe4ef; border-radius: 5px; background: #fff; }
.mapping-side em { color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.mapping-side b { color: #0f172a; font-size: 13px; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-side { border-left: 3px solid #10b981; }
.fixed-side { border-left: 3px solid #f59e0b; background: #fffbeb; }
.mapping-arrow, .flow-arrow { color: #64748b; text-align: center; }
.adapter-command-line { display: inline-flex; align-items: center; gap: 6px; min-width: 0; color: #475569; }
.adapter-command-line em { padding: 2px 5px; border: 1px solid #dbe4ef; border-radius: 4px; background: #f1f5f9; color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.adapter-command-line b { color: #0f172a; font-size: 13px; font-weight: 750; }
.info-chip { display: inline-flex; align-items: center; gap: 5px; padding: 4px 7px; border: 1px solid #bfdbfe; border-radius: 5px; background: #eff6ff; color: #1d4ed8; font-size: 13px; }
.info-chip em, .info-chip i { padding: 1px 4px; border: 1px solid #dbe4ef; border-radius: 4px; background: #fff; color: #64748b; font-size: 11px; font-style: normal; font-weight: 700; }
.info-chip b { color: #1d4ed8; font-weight: 700; }
.function-map-card { padding: 10px; border-left: 3px solid #2563eb; }
.function-map-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.function-map-head strong { color: #0f172a; font-size: 15px; }
.function-map-head span { color: #64748b; font-size: 12px; }
.function-param-list { display: grid; gap: 6px; }
.function-param-row { display: grid; grid-template-columns: minmax(150px, 1fr) 28px minmax(150px, 1fr); align-items: center; gap: 8px; min-height: 34px; padding: 6px; border: 1px solid #e5e7eb; border-radius: 4px; background: #fff; }
.state-token-panel { display: flex; flex-wrap: wrap; gap: 7px; min-height: 32px; }
.filled-state-token { display: inline-flex; align-items: center; min-height: 26px; max-width: 180px; padding: 3px 10px; border-radius: 4px; background: #dcfce7; color: #166534; font-size: 13px; font-weight: 650; line-height: 1.35; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-state-token { background: #e0f2fe; color: #075985; }
.op-state-token { background: #dcfce7; color: #166534; }
.light-state-token { background: #eef6ee; color: #166534; }
.transition-card-list { grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); }
.transition-view-card { padding: 10px; }
.transition-card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.transition-card-head strong { min-width: 0; color: #0f172a; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.transition-card-head span { color: #64748b; font-size: 12px; white-space: nowrap; }
.transition-flow { display: flex; align-items: center; gap: 8px; }
.constraint-list { grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
.constraint-card { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 10px; }
.constraint-card strong { color: #0f172a; font-size: 14px; }
.constraint-card span { color: #b45309; font-weight: 650; }
.constraint-card em { color: #64748b; font-size: 12px; font-style: normal; }
.drawer-section { margin-top: 8px; }
.drawer-section :deep(.el-table__cell) { padding: 5px 6px; }
.drawer-section :deep(.el-input__wrapper), .drawer-section :deep(.el-select__wrapper) { min-height: 30px; }
.drawer-section .nested-toolbar { margin: 8px 0 6px; }
.drawer-section .editor-card { padding: 8px; }
.drawer-section .editor-card-list { gap: 8px; }

@media (max-width: 1120px) { .content-shell { grid-template-columns: 240px minmax(0, 1fr); } .model-json-grid { grid-template-columns: 1fr; } }
@media (max-width: 820px) { .state-card-grid { grid-template-columns: 1fr; } .device-model-page { padding: 10px; } .page-header, .detail-head { align-items: stretch; flex-direction: column; } .content-shell { grid-template-columns: 1fr; } .model-list-panel { min-height: 260px; } .drawer-tabs :deep(.el-tabs__header) { width: 92px; } .registered-adapter-picker, .function-map-selects, .param-map-row, .param-map-row.fixed, .action-row { grid-template-columns: 1fr; } .mapping-direction { display: none; } .summary-card-body { padding-left: 0; } }

/* 严肃的函数式动作展示与编辑器排版 */
.serious-actions-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}
.serious-action-wrapper {
  display: inline-flex;
  align-items: center;
}
.serious-action-tag {
  font-weight: 500;
  color: #334155;
  background-color: #ffffff;
  border-color: #cbd5e1;
}
.serious-action-editor {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.action-editor-label { color: #64748b; font-size: 12px; }
.no-action-cell { display: inline-flex; align-items: center; gap: 8px; color: #64748b; }
.compact-action-btn { padding: 4px 8px; }
</style>



