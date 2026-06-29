<template>
  <div class="device-model-page">
    <header class="page-header">
      <div class="page-title">
        <h1>设备模型管理</h1>
        <p>按设备能力模型与状态机模型维护设备对象，保存后可导出模型文件。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        <el-button v-if="canCreateModel" type="primary" :icon="Plus" @click="openCreateDrawer">新建设备模型</el-button>
      </div>
    </header>

    <section class="content-shell">
      <aside class="model-list-panel">
        <div class="list-tools">
          <el-input v-model="keyword" placeholder="搜索模型名称" clearable :prefix-icon="Search" @input="onKeywordInput" />
        </div>

        <el-scrollbar class="model-list" v-loading="loading">
          <button
            v-for="model in models"
            :key="model.modelId"
            class="model-row"
            :class="{ active: selectedModelId === model.modelId }"
            @click="selectedModelId = model.modelId"
          >
            <span class="model-name">{{ model.modelName || '未命名模型' }}</span>
            <span class="model-meta">{{ model.categoryName || '未分类' }}</span>
            <span class="model-summary">{{ summaryText(model) }}</span>
          </button>
          <el-empty v-if="!loading && models.length === 0" description="暂无设备模型" :image-size="90" />
        </el-scrollbar>

        <div class="list-footer">
          <el-pagination
            v-model:current-page="pageNo"
            :page-size="pageSize"
            :total="total"
            small
            background
            layout="prev, pager, next"
            @current-change="loadData"
          />
        </div>
      </aside>

      <main class="detail-panel">
        <template v-if="selectedModel">
          <div class="detail-head">
            <div class="detail-title">
              <h2>{{ selectedModel.modelName }}</h2>
              <p>{{ selectedModel.categoryName || '未分类' }} · 模型 ID {{ selectedModel.modelId }} · {{ formatTime(selectedModel.updateTime) }}</p>
            </div>
            <div class="detail-actions">
              <el-button :icon="Download" @click="downloadModelBundle">导出模型文件</el-button>
              <el-button v-if="canEditModel" type="primary" plain :icon="EditPen" @click="openEditDrawer(selectedModel)">编辑模型</el-button>
              <el-popconfirm v-if="canDeleteModel" title="确认删除该设备模型？已有设备实例时后端会阻止删除。" @confirm="deleteModel(selectedModel.modelId)">
                <template #reference>
                  <el-button type="danger" plain :icon="Delete">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>

          <el-tabs v-model="activeDetailTab" class="detail-tabs">
            <el-tab-pane label="基础信息" name="basic">
              <section class="info-section">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="模型名称">{{ selectedModel.modelName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="设备类别">{{ selectedModel.categoryName || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="通信协议">{{ selectedModel.adapterContract.config?.protocol || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="更新时间">{{ formatTime(selectedModel.updateTime) }}</el-descriptions-item>
                </el-descriptions>
              </section>
            </el-tab-pane>

            <el-tab-pane label="属性功能" name="ability">
              <section class="info-section">
                <div class="section-title"><h3>设备属性</h3></div>
                <el-table :data="selectedModel.attributes" border size="small" height="230">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column label="属性名" min-width="180">
                    <template #default="{ row }">{{ row.displayName || row.name || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="取值类型" width="120">
                    <template #default="{ row }">{{ valueKindLabel(row.valueKind) }}</template>
                  </el-table-column>
                  <el-table-column prop="dataType" label="数据类型" width="120" />
                  <el-table-column prop="unit" label="单位" width="120">
                    <template #default="{ row }">{{ row.unit || '-' }}</template>
                  </el-table-column>
                </el-table>
              </section>

              <section class="info-section">
                <div class="section-title"><h3>设备操作</h3></div>
                <div class="summary-card-list">
                  <article v-for="(capability, index) in selectedModel.capabilities" :key="capability.name || index" class="summary-card capability-summary-card">
                    <div class="summary-card-head">
                      <span class="item-index">{{ index + 1 }}</span>
                      <div class="summary-card-title">
                        <strong>{{ capability.displayName || capability.name || '未命名操作' }}</strong>
                        <span v-if="capability.name && capability.displayName && capability.name !== capability.displayName">{{ capability.name }}</span>
                      </div>
                    </div>
                    <div class="summary-card-body">
                      <el-tag v-for="param in capability.parameters" :key="param.name || param.displayName" size="small" effect="plain" class="tag-gap">
                        {{ param.displayName || param.name }} · {{ param.dataType }}
                      </el-tag>
                      <span v-if="!capability.parameters?.length" class="muted-text">无参数</span>
                    </div>
                  </article>
                  <el-empty v-if="selectedModel.capabilities.length === 0" description="暂无设备操作" :image-size="80" />
                </div>
              </section>

              <section class="info-section">
                <div class="section-title"><h3>端口连接</h3></div>
                <el-table :data="selectedModel.ports" border size="small" height="220">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column label="端口名称" min-width="180">
                    <template #default="{ row }">{{ row.displayName || row.portName || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="方向" width="120">
                    <template #default="{ row }">{{ directionLabel(row.direction) }}</template>
                  </el-table-column>
                  <el-table-column label="绑定属性" min-width="180">
                    <template #default="{ row }">{{ displayAttributeName(row.bindingAttrName, selectedModel.attributes) }}</template>
                  </el-table-column>
                </el-table>
              </section>
            </el-tab-pane>

            <el-tab-pane label="Adapter 契约" name="adapter">
              <section class="info-section">
                <div class="section-title"><h3>Adapter 命令</h3></div>
                <div class="summary-card-list">
                  <article v-for="(command, index) in selectedModel.adapterContract.commands" :key="command.commandName || index" class="summary-card command-summary-card">
                    <div class="summary-card-head">
                      <span class="item-index">{{ index + 1 }}</span>
                      <div class="summary-card-title">
                        <strong>{{ command.commandName || '未命名命令' }}</strong>
                      </div>
                    </div>
                    <div class="summary-card-body">
                      <el-tag v-for="param in command.commandParameters" :key="param.paramName" size="small" effect="plain" class="tag-gap">
                        {{ param.paramName }} · {{ param.dataType }}
                      </el-tag>
                      <span v-if="!command.commandParameters?.length" class="muted-text">无参数</span>
                    </div>
                  </article>
                  <el-empty v-if="selectedModel.adapterContract.commands.length === 0" description="暂无 Adapter 命令" :image-size="80" />
                </div>
              </section>

              <section class="info-section">
                <div class="section-title"><h3>Adapter 属性</h3></div>
                <el-table :data="selectedModel.adapterContract.telemetry.adapterAttributes" border size="small" height="220">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column prop="name" label="属性字段" min-width="180" />
                  <el-table-column prop="dataType" label="数据类型" width="120" />
                  <el-table-column prop="description" label="说明" min-width="220">
                    <template #default="{ row }">{{ row.description || '-' }}</template>
                  </el-table-column>
                </el-table>
              </section>

              <section class="info-section">
                <div class="section-title"><h3>Adapter 事件</h3></div>
                <el-table :data="selectedModel.adapterContract.events" border size="small" height="200">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column prop="eventName" label="事件名" min-width="180" />
                  <el-table-column prop="description" label="说明" min-width="240">
                    <template #default="{ row }">{{ row.description || '-' }}</template>
                  </el-table-column>
                </el-table>
              </section>
            </el-tab-pane>

            <el-tab-pane label="映射关系" name="mapping">
              <section class="info-section">
                <div class="section-title"><h3>属性映射</h3></div>
                <el-table :data="selectedModel.adapterContract.telemetry.attributesMapping" border size="small" height="230">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column label="模型属性" min-width="180">
                    <template #default="{ row }">{{ displayAttributeName(row.modelAttributeName, selectedModel.attributes) }}</template>
                  </el-table-column>
                  <el-table-column prop="adapterAttrName" label="Adapter 属性" min-width="180" />
                </el-table>
              </section>

              <section class="info-section">
                <div class="section-title"><h3>功能映射</h3></div>
                <el-table :data="capabilityMappingRows(selectedModel.capabilities)" border size="small" height="320">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column prop="capabilityDisplayName" label="模型操作" min-width="160" />
                  <el-table-column prop="adapterCommandName" label="Adapter 命令" min-width="160" />
                  <el-table-column prop="commandParamName" label="命令参数" min-width="160" />
                  <el-table-column prop="sourceLabel" label="功能参数 / 固定值" min-width="220" />
                </el-table>
              </section>
            </el-tab-pane>

            <el-tab-pane label="状态机" name="state">
              <section class="info-section">
                <div class="section-title"><h3>功能状态</h3></div>
                <el-table :data="selectedModel.opState.states" border size="small" height="220">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column prop="stateName" label="状态名称" min-width="180" />
                  <el-table-column label="进入动作" min-width="260">
                    <template #default="{ row }">
                      <div class="serious-actions-container">
                        <div v-for="(act, aIdx) in stateEntryActions(row)" :key="aIdx" class="serious-action-wrapper">
                          <el-tag size="small" type="info" effect="plain" class="serious-action-tag">
                            {{ describeAction(act) }}
                          </el-tag>
                        </div>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </section>

              <section class="info-section">
                <div class="section-title"><h3>指令生命周期</h3></div>
                <el-table :data="selectedModel.cmdState.states" border size="small" height="220">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column prop="stateName" label="状态名称" min-width="180" />
                  <el-table-column label="进入动作" min-width="260">
                    <template #default="{ row }">
                      <div class="serious-actions-container">
                        <div v-for="(act, aIdx) in stateEntryActions(row)" :key="aIdx" class="serious-action-wrapper">
                          <el-tag size="small" type="info" effect="plain" class="serious-action-tag">
                            {{ describeAction(act) }}
                          </el-tag>
                        </div>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </section>

              <section class="info-section">
                <div class="section-title"><h3>状态转移</h3></div>
                <el-table :data="selectedModel.stateTransitions" border size="small" height="280">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column prop="description" label="说明" min-width="150">
                    <template #default="{ row }">{{ row.description || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="fromStateName" label="来源状态" min-width="120" />
                  <el-table-column label="触发接口" min-width="150">
                    <template #default="{ row }">{{ row.trigger?.interfaceName || 'Interface_adapter_in' }}</template>
                  </el-table-column>
                  <el-table-column label="触发信号" min-width="180">
                    <template #default="{ row }">{{ row.trigger?.signalName || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="toStateName" label="目标状态" min-width="120" />
                  <el-table-column label="转移动作" min-width="240">
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
            </el-tab-pane>

            <el-tab-pane label="内置约束" name="constraint">
              <section class="info-section">
                <div class="section-title"><h3>内置约束</h3></div>
                <el-table :data="selectedModel.intrinsicConstraints" border size="small" height="320">
                  <template #empty>
                    <el-empty description="暂无配置数据" :image-size="60" />
                  </template>
                  <el-table-column label="约束属性" min-width="170">
                    <template #default="{ row }">{{ displayAttributeName(row.objectAttributeName, selectedModel.attributes) }}</template>
                  </el-table-column>
                  <el-table-column prop="operator" label="比较符" width="110" />
                  <el-table-column prop="boundaryValue" label="阈值" width="130" />
                  <el-table-column prop="violationStateName" label="违规状态" min-width="150" />
                </el-table>
              </section>
            </el-tab-pane>

            <el-tab-pane label="模型文件" name="file">
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
            </el-tab-pane>
          </el-tabs>
        </template>

        <el-empty v-else description="请选择或新建设备模型" :image-size="120" />
      </main>
    </section>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" direction="rtl" size="78%" destroy-on-close class="model-drawer">
      <div class="drawer-body">
        <el-tabs v-model="activeEditTab" tab-position="left" class="drawer-tabs">
          <el-tab-pane label="基础信息" name="basic">
            <section class="drawer-section">
              <div class="section-title"><h3>基础信息</h3></div>
              <el-form label-width="96px" size="small" class="basic-form">
                <el-form-item label="模型名称" required>
                  <el-input v-model="draft.basic.modelName" placeholder="例如：反应釜温控模块" maxlength="80" show-word-limit />
                </el-form-item>
                <el-form-item label="设备类别">
                  <el-select v-model="draft.basic.categoryValue" filterable allow-create clearable default-first-option placeholder="选择或输入新的设备类别">
                    <el-option v-for="category in categories" :key="String(category.id)" :label="category.categoryName" :value="String(category.id)" />
                  </el-select>
                </el-form-item>
              </el-form>
            </section>
          </el-tab-pane>

          <el-tab-pane label="属性功能" name="ability">
            <section class="drawer-section">
              <div class="section-title">
                <h3>设备属性</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addAttribute">新增属性</el-button>
              </div>
              <div v-if="draft.attributes.length === 0" class="compact-empty block-empty">暂无设备属性，请点击右上角“新增属性”进行配置</div>
              <el-table v-else :data="draft.attributes" border size="small">
                <el-table-column label="属性名" min-width="180">
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
                    <div class="editor-card-title">
                      <span class="item-index">{{ capIndex + 1 }}</span>
                      <el-input v-model="capability.displayName" size="small" placeholder="操作名称，例如：开始加热" />
                    </div>
                    <el-button link type="danger" :icon="Delete" @click="removeCapability(capability)">删除</el-button>
                  </div>
                  <div class="nested-toolbar">
                    <span>操作参数</span>
                    <el-button size="small" type="primary" plain circle :icon="Plus" title="添加参数" @click="addCapabilityParameter(capability)" />
                  </div>
                  <el-table :data="capability.parameters" border size="small" class="nested-table">
                    <el-table-column label="参数名" min-width="180">
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
                <h3>端口连接</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addPort">新增端口</el-button>
              </div>
              <div v-if="draft.ports.length === 0" class="compact-empty block-empty">暂无端口连接，请点击右上角“新增端口”进行配置</div>
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
                    <el-select v-model="row.bindingAttrKey" size="small" filterable>
                      <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.ports, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="Adapter 契约" name="adapter">
            <section class="drawer-section">
              <div class="section-title"><h3>契约来源</h3></div>
              <el-form label-width="90px" size="small">
                <el-form-item label="协议类型">
                  <el-select v-model="draft.adapterContract.config.protocol">
                    <el-option label="MQTT" value="MQTT" />
                    <el-option label="HTTP" value="HTTP" />
                  </el-select>
                </el-form-item>
                <el-form-item label="配置文本">
                  <div class="config-import">
                    <el-upload :auto-upload="false" :show-file-list="false" accept=".json,.txt" :on-change="importAdapterFile">
                      <el-button :icon="Upload">上传配置</el-button>
                    </el-upload>
                    <el-button type="primary" plain @click="applyAdapterConfigText">解析到契约</el-button>
                  </div>
                  <el-input v-model="adapterConfigText" type="textarea" :rows="6" placeholder="可粘贴 JSON 格式的 Adapter 配置文本；MQTT 在线注册解析后续接入。" />
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
                  <el-table :data="visibleCommandParameters(command)" border size="small" class="nested-table">
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
          </el-tab-pane>

          <el-tab-pane label="映射关系" name="mapping">
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
              <el-table v-else :data="draft.functionMappings" border size="small" class="function-mapping-table">
                <el-table-column label="模型操作" min-width="180">
                  <template #default="{ row }">
                    <el-select v-model="row.capabilityKey" size="small" filterable>
                      <el-option
                        v-for="capability in capabilitySelectOptions"
                        :key="capability.key"
                        :label="capability.label"
                        :value="capability.key"
                        :disabled="isCapabilityMapped(capability.key, row)"
                      />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="Adapter 命令" min-width="180">
                  <template #default="{ row }">
                    <el-select v-model="row.adapterCommandName" size="small" filterable @change="handleFunctionMappingCommandChange(row)">
                      <el-option v-for="cmd in commandNameOptions" :key="cmd" :label="cmd" :value="cmd" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="参数映射" min-width="560">
                  <template #default="{ row }">
                    <div class="param-map-editor">
                      <div v-for="(mapping, index) in row.parameterMapping" :key="mapping._key || index" class="param-map-row" :class="{ invalid: isParameterMappingInvalid(row, mapping) }">
                        <el-select v-model="mapping.commandParamName" size="small" filterable placeholder="命令参数" @change="handleParameterCommandChange(row, mapping)">
                          <el-option v-for="param in commandParameterOptionsDetailed(row.adapterCommandName)" :key="param.paramName" :label="param.paramName + ' · ' + param.dataType" :value="param.paramName" />
                        </el-select>
                        <el-switch v-model="mapping.isFixedValue" size="small" active-text="固定" @change="handleParameterFixedChange(mapping)" />
                        <el-input v-if="mapping.isFixedValue" v-model="mapping.fixedValue" size="small" :placeholder="fixedValuePlaceholder(row, mapping)" />
                        <el-select v-else v-model="mapping.capabilityParamKey" size="small" filterable placeholder="操作参数" @change="handleCapabilityParameterChange(row, mapping)">
                          <el-option v-for="param in capabilityParameterOptionsDetailedByKey(row.capabilityKey)" :key="param._key" :label="(param.displayName || param.name) + ' · ' + param.dataType + (isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType) ? '' : ' (类型不匹配)')" :value="param._key" :disabled="!isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType)" />
                        </el-select>
                        <el-button link type="danger" :icon="Delete" @click="removeRow(row.parameterMapping, index)" />
                        <span v-if="isParameterMappingInvalid(row, mapping)" class="map-warning">{{ parameterMappingWarning(row, mapping) }}</span>
                      </div>
                      <div class="param-map-toolbar">
                        <span v-if="row.parameterMapping.length === 0" class="no-mapping-placeholder">未配置参数映射</span>
                        <span v-else></span>
                        <el-button size="small" type="primary" plain :icon="Plus" class="add-mapping-btn" @click="addParameterMapping(row)">新增参数映射</el-button>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.functionMappings, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="状态机" name="state">
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

            <section class="drawer-section">
              <div class="section-title">
                <div class="locked-heading">
                  <h3>功能状态</h3>
                  <el-tag size="small" effect="plain" type="info" class="lock-tag-flex">
                    <el-icon><Lock /></el-icon>
                    <span>进入动作锁定</span>
                  </el-tag>
                </div>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addOpState">新增状态</el-button>
              </div>
              <el-form label-width="86px" size="small" class="mapping-form">
                <el-form-item label="初始状态">
                  <el-select v-model="draft.opState.initialStateName" filterable allow-create>
                     <el-option v-for="name in opStateNameOptions" :key="name" :label="name" :value="name" />
                  </el-select>
                </el-form-item>
              </el-form>
              <el-table :data="draft.opState.states" border size="small" class="state-table">
                <el-table-column label="状态名称" min-width="180">
                  <template #default="{ row }"><el-input v-model="row.stateName" size="small" placeholder="例如：IDLE" /></template>
                </el-table-column>
                <el-table-column label="进入动作" min-width="320">
                  <template #default="{ row }">
                    <div class="locked-action-container">
                      <el-tag size="small" effect="plain" type="info" class="lock-tag-flex">
                        <el-icon><Lock /></el-icon>
                        <span>进入动作锁定</span>
                      </el-tag>
                      <div v-for="(act, aIdx) in stateEntryActions(row)" :key="aIdx" class="serious-action-wrapper inline-action">
                        <el-tag size="small" type="info" effect="plain" class="serious-action-tag">
                          {{ describeAction(act) }}
                        </el-tag>
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.opState.states, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>

            <section class="drawer-section locked-section">
              <div class="section-title">
                <div class="locked-heading">
                  <el-icon><Lock /></el-icon>
                  <h3>指令生命周期</h3>
                  <el-tag size="small" effect="plain" type="info">系统固定</el-tag>
                </div>
              </div>
              <el-form label-width="86px" size="small" class="mapping-form">
                <el-form-item label="初始状态">
                  <span class="locked-value">{{ draft.cmdState.initialStateName }}</span>
                </el-form-item>
              </el-form>
              <el-table :data="draft.cmdState.states" border size="small" class="state-table locked-table">
                <el-table-column label="状态名称" min-width="180">
                  <template #default="{ row }"><span>{{ row.stateName }}</span></template>
                </el-table-column>
                <el-table-column label="进入动作" min-width="320">
                  <template #default="{ row }">
                    <div class="locked-action-container">
                      <el-tag size="small" effect="plain" type="info" class="lock-tag-flex">
                        <el-icon><Lock /></el-icon>
                        <span>进入动作锁定</span>
                      </el-tag>
                      <div v-for="(act, aIdx) in stateEntryActions(row)" :key="aIdx" class="serious-action-wrapper inline-action">
                        <el-tag size="small" type="info" effect="plain" class="serious-action-tag">
                          {{ describeAction(act) }}
                        </el-tag>
                      </div>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <section class="drawer-section locked-section">
              <div class="section-title">
                <div class="locked-heading">
                  <el-icon><Lock /></el-icon>
                  <h3>指令生命周期转移规则</h3>
                  <el-tag size="small" effect="plain" type="info">系统固定</el-tag>
                </div>
              </div>
              <el-table :data="commandLifecycleTransitionRows" border size="small" class="transition-table locked-table">
                <el-table-column prop="description" label="规则" min-width="160" />
                <el-table-column prop="fromStateName" label="来源状态" min-width="120" />
                <el-table-column prop="toStateName" label="目标状态" min-width="120" />
                <el-table-column label="触发接口" min-width="190">
                  <template #default="{ row }">{{ row.trigger.interfaceName }}</template>
                </el-table-column>
                <el-table-column label="接收信号" min-width="190">
                  <template #default="{ row }">{{ row.trigger.signalName }}</template>
                </el-table-column>
                <el-table-column label="转移动作" min-width="240">
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
              <div v-if="draft.stateTransitions.length === 0" class="compact-empty block-empty">暂无功能状态转移规则，请点击右上角“新增规则”进行配置</div>
              <el-table v-else :data="draft.stateTransitions" border size="small" class="transition-table">
                <el-table-column label="说明" min-width="150">
                  <template #default="{ row }"><el-input v-model="row.description" size="small" placeholder="可选" /></template>
                </el-table-column>
                <el-table-column label="来源状态" min-width="130">
                  <template #default="{ row }"><state-select v-model="row.fromStateName" :options="opStateNameOptions" /></template>
                </el-table-column>
                <el-table-column label="触发接口" min-width="160">
                  <template #default>
                    <span class="locked-action"><el-icon><Lock /></el-icon><span>Interface_adapter_in</span></span>
                  </template>
                </el-table-column>
                <el-table-column label="触发信号" min-width="190">
                  <template #default="{ row }"><state-select v-model="row.trigger.signalName" :options="signalOptionsForInterface('Interface_adapter_in')" /></template>
                </el-table-column>
                <el-table-column label="目标状态" min-width="130">
                  <template #default="{ row }"><state-select v-model="row.toStateName" :options="opStateNameOptions" /></template>
                </el-table-column>
                <el-table-column label="转移动作" min-width="360">
                  <template #default="{ row }">
                    <div v-if="row.actions?.length" class="serious-action-editor">
                      <span class="action-editor-label">输出</span>
                      <el-select v-model="row.actions[0].payload.interfaceName" size="small" style="width: 170px" placeholder="选择接口" @change="onActionInterfaceChange(row.actions[0])">
                        <el-option label="状态接口" value="Interface_status_out" />
                        <el-option label="Adapter 接口" value="Interface_adapter_out" />
                      </el-select>
                      <el-select v-model="row.actions[0].payload.signalName" size="small" style="width: 150px" placeholder="选择信号">
                        <el-option v-for="sig in getSignalsForInterface(row.actions[0].payload.interfaceName)" :key="sig" :label="sig" :value="sig" />
                      </el-select>
                      <el-button link type="danger" :icon="Delete" @click="removeTransitionAction(row)" />
                    </div>
                    <div v-else class="no-action-cell">
                      <span>无转移动作</span>
                      <el-button size="small" plain :icon="Plus" class="compact-action-btn" @click="ensureTransitionAction(row)">添加</el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="" width="54" fixed="right">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.stateTransitions, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="内置约束" name="constraint">
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
          </el-tab-pane>

          <el-tab-pane label="模型文件" name="file">
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
          </el-tab-pane>
        </el-tabs>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveDraft">保存模型</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref, resolveComponent, watch } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Connection, Cpu, Delete, Download, EditPen, Lock, Notification, Plus, Refresh, Search, Unlock, Upload } from '@element-plus/icons-vue'
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
const activeDetailTab = ref('basic')
const activeEditTab = ref('basic')
const adapterConfigText = ref('')
let searchTimer = null
let loadSeq = 0
let uiSeq = 0

const draft = reactive(emptyDraft())
const interfaceLocked = ref(true)
const expandedCapabilityKeys = ref([])

const canCreateModel = computed(() => authStore.hasPermission('device_model:create'))
const canEditModel = computed(() => authStore.hasPermission('device_model:edit'))
const canDeleteModel = computed(() => authStore.hasPermission('device_model:delete'))
const selectedModel = computed(() => models.value.find(item => item.modelId === selectedModelId.value) || null)
const drawerTitle = computed(() => drawerMode.value === 'create' ? '新建设备模型' : '编辑设备模型')
const attributeOptions = computed(() => draft.attributes.map((item, index) => ({ key: item._key, label: item.displayName || item.name || '属性' + (index + 1) })).filter(item => item.key))
const adapterAttributeNameOptions = computed(() => draft.adapterContract.telemetry.adapterAttributes.map(item => item.name).filter(Boolean))
const commandNameOptions = computed(() => draft.adapterContract.commands.map(item => item.commandName).filter(Boolean))
const adapterEventOptions = computed(() => opEventNames(draft.adapterContract.events))
const adapterSignalOptions = computed(() => uniqueStrings([...standardCmdEvents, ...adapterEventOptions.value]))
const inboundInterfaceNameOptions = computed(() => stateMachineInterfaceRows.value.filter(item => item.direction !== 'OUT').map(item => item.name).filter(Boolean))
const capabilitySelectOptions = computed(() => draft.capabilities.map((item, index) => ({ key: item._key, label: item.displayName || item.name || '操作' + (index + 1) })).filter(item => item.key))
const generatedInterfaces = computed(() => defaultInterfaces(adapterSignalOptions.value))
const stateMachineInterfaceRows = computed(() => interfaceLocked.value ? generatedInterfaces.value : draft.stateMachineInterfaces)
const commandLifecycleTransitionRows = computed(() => defaultCommandLifecycleTransitions())
const opStateNameOptions = computed(() => draft.opState.states.map(item => item.stateName).filter(Boolean))
const cmdStateNameOptions = computed(() => draft.cmdState.states.map(item => item.stateName).filter(Boolean))
const capabilityModelJson = computed(() => selectedModel.value ? buildCapabilityModel(selectedModel.value) : {})
const stateMachineModelJson = computed(() => selectedModel.value ? buildStateMachineModel(selectedModel.value) : {})
const draftCapabilityModelJson = computed(() => buildCapabilityModelFromPayload(buildSavePayload(false)))
const draftStateMachineModelJson = computed(() => buildStateMachineModelFromPayload(buildSavePayload(false)))

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
    componentsBom: []
  }
}

function defaultAdapterContract() {
  return { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
}

function defaultStateSpace(initialStateName) {
  return { initialStateName, states: [{ _key: makeUiKey('state'), stateName: initialStateName, onEntry: [{ actionName: 'SEND', payload: { interfaceName: 'Interface_status_out', signalName: 'OP_STATE' } }] }] }
}

function defaultCommandLifecycle() {
  return { initialStateName: 'IDLE', states: commandLifecycleStateNames().map(stateName => ({ _key: makeUiKey('cmd_state'), stateName, onEntry: [{ actionName: 'SEND', payload: { interfaceName: 'Interface_status_out', signalName: 'CMD_STATE' } }] })) }
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

watch(adapterSignalOptions, (newEvents) => {
  if (interfaceLocked.value) {
    const adapterIface = draft.stateMachineInterfaces.find(i => i.name === 'Interface_adapter_in' || (i.interfaceType === 'ADAPTER' && i.direction !== 'OUT'));
    if (adapterIface) {
      adapterIface.allowedSignals = [...newEvents];
    }
  }
}, { deep: true });

function adapterInterfaceName() {
  return 'Interface_adapter_in'
}

function toggleInterfaceLock() {
  if (interfaceLocked.value) {
    draft.stateMachineInterfaces.splice(0, draft.stateMachineInterfaces.length, ...deepClone(generatedInterfaces.value))
  }
  interfaceLocked.value = !interfaceLocked.value
}

function defaultEntryActions() {
  return []
}

function stateEntryActions(row) {
  return asArray(row?.onEntry)
}

function adapterOutAction(signalName) {
  return { actionName: 'SEND', payload: { interfaceName: 'Interface_adapter_out', signalName } }
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
  const seq = ++loadSeq
  loading.value = true
  try {
    const [modelRes, categoryRes] = await Promise.all([
      axios.get('/api/device/model/page', { params: { pageNo: pageNo.value, pageSize: pageSize.value, keyword: keyword.value.trim() || undefined } }),
      axios.get('/api/device/category/list')
    ])
    if (seq !== loadSeq) return
    categories.value = asArray(categoryRes.data?.data)
    const pageData = modelRes.data?.data || {}
    models.value = asArray(pageData.records).map(normalizeModel)
    total.value = Number(pageData.total || 0)
    if (!models.value.some(item => item.modelId === selectedModelId.value)) selectedModelId.value = models.value[0]?.modelId || ''
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '加载设备模型失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

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
  adapterConfigText.value = ''
  drawerMode.value = 'create'
  interfaceLocked.value = true
  activeEditTab.value = 'basic'
  drawerVisible.value = true
}

function openEditDrawer(model) {
  replaceDraft(fromModelToDraft(model))
  adapterConfigText.value = ''
  drawerMode.value = 'edit'
  interfaceLocked.value = true
  activeEditTab.value = 'basic'
  expandedCapabilityKeys.value = []
  drawerVisible.value = true
}

async function saveDraft() {
  if (!draft.basic.modelName?.trim()) {
    activeEditTab.value = 'basic'
    ElMessage.error('请填写模型名称')
    return
  }
  if (!validateDraftBeforeSave()) return
  saving.value = true
  try {
    const payload = buildSavePayload(true)
    const res = await axios.post('/api/device/model/save', payload)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '保存失败')
      return
    }
    selectedModelId.value = String(res.data?.data?.modelId || payload.modelId || '')
    drawerVisible.value = false
    ElMessage.success('保存成功')
    await loadData()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
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


function validateDraftBeforeSave() {
  const badAttrMapping = draft.adapterContract.telemetry.attributesMapping.find(isAttributeMappingInvalid)
  if (badAttrMapping) {
    activeEditTab.value = 'mapping'
    ElMessage.error('属性映射类型不一致，请检查模型属性与 Adapter 属性')
    return false
  }

  for (const row of draft.functionMappings) {
    for (const mapping of asArray(row.parameterMapping)) {
      if (isParameterMappingInvalid(row, mapping)) {
        activeEditTab.value = 'mapping'
        ElMessage.error(parameterMappingWarning(row, mapping) || '参数映射类型不一致')
        return false
      }
    }
  }
  return true
}

function buildSavePayload(includeBlankBasic = true) {
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
    componentsBom: asArray(draft.componentsBom)
  }
  if (isNumeric(draft.basic.categoryValue)) payload.categoryId = Number(draft.basic.categoryValue)
  else if (draft.basic.categoryValue) payload.categoryName = String(draft.basic.categoryValue).trim()
  return payload
}

function fromModelToDraft(model) {
  return {
    basic: { modelId: model.modelId, modelName: model.modelName || '', categoryValue: model.categoryId || '' },
    attributes: deepClone(model.attributes),
    capabilities: deepClone(model.capabilities),
    adapterContract: deepClone(model.adapterContract),
    ports: deepClone(model.ports),
    intrinsicConstraints: deepClone(model.intrinsicConstraints),
    stateMachineInterfaces: normalizeInterfaces(model.stateMachineInterfaces).length ? normalizeInterfaces(model.stateMachineInterfaces) : defaultInterfaces(asArray(model.adapterContract?.events).map(event => event.eventName).filter(Boolean)),
    functionMappings: [],
    opState: deepClone(model.opState),
    cmdState: deepClone(model.cmdState),
    stateTransitions: deepClone(model.stateTransitions),
    componentsBom: deepClone(model.componentsBom)
  }
}

function replaceDraft(next) {
  Object.assign(draft.basic, next.basic)
  const normalizedCapabilities = normalizeCapabilities(next.capabilities)
  draft.attributes.splice(0, draft.attributes.length, ...normalizeAttributes(next.attributes))
  draft.capabilities.splice(0, draft.capabilities.length, ...normalizedCapabilities)
  const nextFunctionMappings = asArray(next.functionMappings).length ? normalizeFunctionMappings(next.functionMappings) : extractFunctionMappings(next.capabilities, normalizedCapabilities)
  draft.functionMappings.splice(0, draft.functionMappings.length, ...nextFunctionMappings)
  draft.ports.splice(0, draft.ports.length, ...normalizePorts(next.ports, draft.attributes))
  draft.intrinsicConstraints.splice(0, draft.intrinsicConstraints.length, ...normalizeIntrinsicConstraints(next.intrinsicConstraints, draft.attributes))
  draft.adapterContract = normalizeAdapterContract(next.adapterContract, draft.attributes)
  const nextInterfaces = normalizeInterfaces(next.stateMachineInterfaces)
  draft.stateMachineInterfaces.splice(0, draft.stateMachineInterfaces.length, ...(nextInterfaces.length ? nextInterfaces : defaultInterfaces(adapterSignalOptions.value)))
  draft.stateTransitions.splice(0, draft.stateTransitions.length, ...normalizeTransitions(next.stateTransitions).filter(row => !isCommandLifecycleTransition(row)))
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
  draft.adapterContract.telemetry.attributesMapping = draft.adapterContract.telemetry.attributesMapping.filter(item => item.modelAttributeKey !== removed._key)
  draft.intrinsicConstraints.forEach(rule => { if (rule.objectAttributeKey === removed._key) rule.objectAttributeKey = '' })
}

function addPort() { draft.ports.push({ _key: makeUiKey('port'), portName: '', displayName: '', direction: 'OUT', bindingAttrKey: draft.attributes[0]?._key || '', bindingAttrName: '' }) }
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
  const removed = draft.adapterContract.telemetry.adapterAttributes[index]
  removeRow(draft.adapterContract.telemetry.adapterAttributes, index)
  if (removed?.name) draft.adapterContract.telemetry.attributesMapping = draft.adapterContract.telemetry.attributesMapping.filter(item => item.adapterAttrName !== removed.name)
}

function addAdapterEvent() { draft.adapterContract.events.push({ _key: makeUiKey('event'), eventName: '', description: '' }) }
function removeAdapterEvent(index) { removeRow(draft.adapterContract.events, index) }

function addAttributeMapping() {
  draft.adapterContract.telemetry.attributesMapping.push({ _key: makeUiKey('attr_map'), adapterAttrName: firstUnusedAdapterAttributeName(), modelAttributeKey: firstUnusedAttributeKey(), modelAttributeName: '' })
}

function handleAttributeMappingModelChange(row) {
  if (!row.modelAttributeKey) return
  if (draft.adapterContract.telemetry.attributesMapping.find(item => item !== row && item.modelAttributeKey === row.modelAttributeKey)) row.modelAttributeKey = ''
  if (isAttributeMappingInvalid(row)) row.adapterAttrName = ''
}

function handleAdapterAttributeMappingChange(row) {
  if (!row.adapterAttrName) return
  if (draft.adapterContract.telemetry.attributesMapping.find(item => item !== row && item.adapterAttrName === row.adapterAttrName)) row.adapterAttrName = ''
  if (isAttributeMappingInvalid(row)) row.adapterAttrName = ''
}

function isAttributeOptionUsed(key, row) { return !!key && draft.adapterContract.telemetry.attributesMapping.some(item => item !== row && item.modelAttributeKey === key) }
function isAdapterAttributeUsed(name, row) { return !!name && draft.adapterContract.telemetry.attributesMapping.some(item => item !== row && item.adapterAttrName === name) }
function firstUnusedAttributeKey() { return attributeOptions.value.find(attr => !draft.adapterContract.telemetry.attributesMapping.some(item => item.modelAttributeKey === attr.key))?.key || '' }
function firstUnusedAdapterAttributeName() { return adapterAttributeNameOptions.value.find(name => !draft.adapterContract.telemetry.attributesMapping.some(item => item.adapterAttrName === name)) || '' }

function addFunctionMapping() {
  draft.functionMappings.push({ _key: makeUiKey('function_map'), capabilityKey: firstUnmappedCapabilityKey(), adapterCommandName: commandNameOptions.value[0] || '', parameterMapping: [] })
}

function addParameterMapping(mappingOwner) {
  const commandParamName = commandParameterOptions(mappingOwner.adapterCommandName)[0] || ''
  const capabilityParamKey = firstCompatibleCapabilityParamKey(mappingOwner.capabilityKey, mappingOwner.adapterCommandName, commandParamName)
  ensureArrayField(mappingOwner, 'parameterMapping').push({ _key: makeUiKey('param_map'), commandParamName, capabilityParamKey, capabilityParamName: '', isFixedValue: false, fixedValue: '' })
}

function handleFunctionMappingCommandChange(row) {
  asArray(row.parameterMapping).forEach(mapping => handleParameterCommandChange(row, mapping))
}

function handleParameterCommandChange(row, mapping) {
  if (mapping.commandParamName && !commandParameterByName(row.adapterCommandName, mapping.commandParamName)) {
    mapping.commandParamName = ''
    mapping.capabilityParamKey = ''
    return
  }
  if (!mapping.isFixedValue && !isCapabilityParamKeyCompatible(row, mapping)) mapping.capabilityParamKey = ''
}

function handleParameterFixedChange(mapping) {
  if (mapping.isFixedValue) {
    mapping.capabilityParamKey = ''
    mapping.capabilityParamName = ''
  } else {
    mapping.fixedValue = ''
  }
}

function handleCapabilityParameterChange(row, mapping) {
  if (!isCapabilityParamKeyCompatible(row, mapping)) mapping.capabilityParamKey = ''
}

function firstUnmappedCapabilityKey() {
  return capabilitySelectOptions.value.find(option => !draft.functionMappings.some(mapping => mapping.capabilityKey === option.key))?.key || ''
}

function isCapabilityMapped(key, row) {
  return !!key && draft.functionMappings.some(mapping => mapping !== row && mapping.capabilityKey === key)
}

function findCapabilityByKey(key) {
  return draft.capabilities.find(item => item._key === key) || null
}

function capabilityParameterOptionsByKey(key) {
  const capability = findCapabilityByKey(key)
  return capability ? capabilityParameterOptions(capability) : []
}

function capabilityParameterOptionsDetailedByKey(key) {
  const capability = findCapabilityByKey(key)
  return capability ? asArray(capability.parameters) : []
}

function commandParameterOptionsDetailed(commandName) {
  return visibleCommandParameters(commandByName(commandName))
}

function visibleCommandParameters(command) {
  return asArray(command?.commandParameters).filter(param => param.hidden !== true)
}

function commandByName(commandName) {
  return draft.adapterContract.commands.find(item => item.commandName === commandName) || null
}

function commandParameterByName(commandName, paramName) {
  return commandParameterOptionsDetailed(commandName).find(param => param.paramName === paramName) || null
}

function adapterAttributeByName(name) {
  return draft.adapterContract.telemetry.adapterAttributes.find(attr => attr.name === name) || null
}

function modelAttributeByKey(key) {
  return draft.attributes.find(attr => attr._key === key) || null
}

function capabilityParameterByKey(capabilityKey, paramKey) {
  return capabilityParameterOptionsDetailedByKey(capabilityKey).find(param => param._key === paramKey) || null
}

function firstCompatibleCapabilityParamKey(capabilityKey, commandName, commandParamName) {
  return capabilityParameterOptionsDetailedByKey(capabilityKey).find(param => isParamDataTypeMatch(commandParamName, commandName, param.dataType))?._key || ''
}

function isCapabilityParamKeyCompatible(row, mapping) {
  if (!mapping.commandParamName || !mapping.capabilityParamKey) return true
  const param = capabilityParameterByKey(row.capabilityKey, mapping.capabilityParamKey)
  return !!param && isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType)
}

function isParamDataTypeMatch(commandParamName, commandName, capabilityParamDataType) {
  if (!commandParamName || !capabilityParamDataType) return true
  const cmdParam = commandParameterByName(commandName, commandParamName)
  if (!cmdParam) return true
  return cmdParam.dataType === capabilityParamDataType
}

function isAttrDataTypeMatch(modelAttributeKey, adapterDataType) {
  if (!modelAttributeKey || !adapterDataType) return true
  const modelAttr = modelAttributeByKey(modelAttributeKey)
  if (!modelAttr) return true
  return modelAttr.dataType === adapterDataType
}

function isAttributeMappingInvalid(row) {
  if (!row.modelAttributeKey || !row.adapterAttrName) return false
  const modelAttr = modelAttributeByKey(row.modelAttributeKey)
  const adapterAttr = adapterAttributeByName(row.adapterAttrName)
  return !modelAttr || !adapterAttr || modelAttr.dataType !== adapterAttr.dataType
}

function isParameterMappingInvalid(row, mapping) {
  if (!mapping.commandParamName) return false
  const cmdParam = commandParameterByName(row.adapterCommandName, mapping.commandParamName)
  if (!cmdParam) return true
  if (mapping.isFixedValue) return !isFixedValueCompatible(cmdParam.dataType, mapping.fixedValue)
  const capabilityParam = capabilityParameterByKey(row.capabilityKey, mapping.capabilityParamKey)
  return !capabilityParam || capabilityParam.dataType !== cmdParam.dataType
}

function parameterMappingWarning(row, mapping) {
  const cmdParam = commandParameterByName(row.adapterCommandName, mapping.commandParamName)
  if (!cmdParam) return '命令参数不存在'
  if (mapping.isFixedValue) return isFixedValueCompatible(cmdParam.dataType, mapping.fixedValue) ? '' : '固定值与命令参数类型不匹配'
  const capabilityParam = capabilityParameterByKey(row.capabilityKey, mapping.capabilityParamKey)
  if (!capabilityParam) return '请选择操作参数'
  return capabilityParam.dataType === cmdParam.dataType ? '' : '操作参数与命令参数类型不一致'
}

function fixedValuePlaceholder(row, mapping) {
  const type = commandParameterByName(row.adapterCommandName, mapping.commandParamName)?.dataType
  return type ? '固定默认值（' + type + '）' : '固定默认值'
}

function isFixedValueCompatible(dataType, value) {
  if (value === '' || value == null) return false
  const text = String(value).trim()
  if (dataType === 'BOOLEAN') return ['true', 'false'].includes(text.toLowerCase())
  if (dataType === 'INTEGER') return /^-?\d+$/.test(text)
  if (dataType === 'DOUBLE') return !Number.isNaN(Number(text))
  return true
}

const adapterAttributeNameOptionsDetailed = computed(() => draft.adapterContract.telemetry.adapterAttributes)

function addOpState() { draft.opState.states.push({ _key: makeUiKey('op_state'), stateName: '', onEntry: [{ actionName: 'SEND', payload: { interfaceName: 'Interface_status_out', signalName: 'OP_STATE' } }] }) }
function addStateTransition() {
  const interfaceName = defaultAdapterInterfaceName()
  draft.stateTransitions.push({ _key: makeUiKey('transition'), description: '', fromStateName: opStateNameOptions.value[0] || '', toStateName: opStateNameOptions.value[1] || opStateNameOptions.value[0] || '', trigger: { interfaceName, signalName: signalOptionsForInterface(interfaceName)[0] || '' }, actions: [] })
}

function ensureTransitionAction(row) {
  row.actions = [{ actionName: 'SEND', payload: { interfaceName: 'Interface_status_out', signalName: 'OP_STATE' } }]
}

function removeTransitionAction(row) {
  row.actions = []
}

function defaultAdapterInterfaceName() { return adapterInterfaceName() }

function handleTransitionInterfaceChange(row) {
  const options = signalOptionsForInterface(row?.trigger?.interfaceName)
  if (!options.includes(row.trigger.signalName)) row.trigger.signalName = options[0] || ''
}

function addIntrinsicConstraint() {
  draft.intrinsicConstraints.push({ _key: makeUiKey('constraint'), objectAttributeKey: draft.attributes[0]?._key || '', objectAttributeName: '', operator: 'GT', boundaryValue: '', violationStateName: opStateNameOptions.value[0] || 'FAULT' })
}

function removeRow(rows, index) { rows.splice(index, 1) }
function removeObjectRow(rows, row) { const index = rows.indexOf(row); if (index >= 0) rows.splice(index, 1) }

function buildCapabilityModel(model) {
  return { metadata: { modelId: numericOrNull(model.modelId), modelName: model.modelName, deviceCategoryId: numericOrNull(model.categoryId) }, attributes: cleanAttributesForExport(model.attributes), capabilities: cleanCapabilitiesForExport(model.capabilities), adapterContract: cleanAdapterContract(model.adapterContract), ports: cleanPortsForExport(model.ports), intrinsicConstraints: cleanIntrinsicConstraintsForExport(model.intrinsicConstraints) }
}

function buildStateMachineModel(model) {
  return { deviceModelId: numericOrNull(model.modelId), interfaces: cleanInterfaces(defaultInterfaces(opEventNames(model.adapterContract?.events))), opStateSpace: cleanStateSpace(model.opState, 'IDLE', 'OP'), cmdLifecycleSpace: cleanStateSpace(model.cmdState, 'IDLE', 'CMD'), transitions: allStateTransitions(model.stateTransitions) }
}

function opEventNames(events) {
  return asArray(events).filter(event => event.eventType !== 'CMD' && !standardCmdEvents.includes(event.eventName)).map(event => event.eventName).filter(Boolean)
}

function allStateTransitions(rows) {
  return [...defaultCommandLifecycleTransitions(), ...cleanTransitions(rows)]
}

function signalOptionsForInterface(interfaceName) {
  const iface = stateMachineInterfaceRows.value.find(item => item.name === interfaceName) || defaultInterfaces(adapterEventOptions.value).find(item => item.name === interfaceName)
  return asArray(iface?.allowedSignals).filter(Boolean)
}

function uniqueStrings(values) {
  return [...new Set(asArray(values).map(stringValue).filter(Boolean))]
}

function buildCapabilityModelFromPayload(payload) {
  return { metadata: { modelId: numericOrNull(payload.modelId), modelName: payload.modelName, deviceCategoryId: numericOrNull(payload.categoryId) }, attributes: cleanAttributesForExport(payload.attributes), capabilities: cleanCapabilitiesForExport(payload.capabilities), adapterContract: cleanAdapterContract(payload.adapterContract), ports: cleanPortsForExport(payload.ports), intrinsicConstraints: cleanIntrinsicConstraintsForExport(payload.intrinsicConstraints) }
}

function buildStateMachineModelFromPayload(payload) {
  return { deviceModelId: numericOrNull(payload.modelId), interfaces: cleanInterfaces(payload.stateMachineInterfaces), opStateSpace: cleanStateSpace(payload.opState, 'IDLE', 'OP'), cmdLifecycleSpace: cleanStateSpace(payload.cmdState, 'IDLE', 'CMD'), transitions: allStateTransitions(payload.stateTransitions) }
}

function downloadModelBundle() {
  if (!selectedModel.value) return
  downloadJson('device-model-' + selectedModel.value.modelId + '.json', { capabilityModel: capabilityModelJson.value, stateMachineModel: stateMachineModelJson.value })
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

function applyAdapterConfigText() {
  if (!adapterConfigText.value.trim()) {
    ElMessage.warning('请先导入或粘贴 Adapter 配置文本')
    return
  }
  try {
    const parsed = JSON.parse(adapterConfigText.value)
    draft.adapterContract = normalizeAdapterContract(extractAdapterContract(parsed), draft.attributes)
    ElMessage.success('已解析到 Adapter 契约')
  } catch {
    ElMessage.error('配置文本不是有效的 JSON')
  }
}

function extractAdapterContract(source) {
  const root = source?.adapterContract || source?.contract || source?.parsedConfig || source?.parsed_config || source || {}
  if (asArray(root.deviceTemplates).length) return extractManifestAdapterContract(root)
  const telemetry = root.telemetry || {}
  return {
    config: { protocol: root.config?.protocol || root.protocol || 'MQTT', adapterName: root.config?.adapterName || root.adapterName || '', templateName: root.config?.templateName || root.templateName || '' },
    commands: asArray(root.commands || root.commandDefs || root.adapterCommands).map(command => ({ commandName: stringValue(command.commandName || command.name || command.command), description: stringValue(command.description || command.desc), commandParameters: asArray(command.commandParameters || command.parameters || command.params).map(normalizeCommandParameter) })),
    telemetry: { adapterAttributes: asArray(telemetry.adapterAttributes || telemetry.fields || root.adapterAttributes || root.telemetryFields).map(attr => ({ name: stringValue(attr.name || attr.fieldName || attr.key), dataType: normalizeDataType(attr.dataType || attr.type, 'DOUBLE', adapterDataTypes), description: stringValue(attr.description || attr.desc) })), attributesMapping: asArray(telemetry.attributesMapping || root.attributesMapping) },
    events: normalizeEventsToFlatList(root.events || root.adapterEvents)
  }
}

function extractManifestAdapterContract(manifest) {
  const template = asArray(manifest.deviceTemplates)[0] || {}
  const eventGroups = normalizeEventGroups(template.events)
  return {
    config: { protocol: 'MQTT', adapterName: stringValue(manifest.adapterName), templateName: stringValue(template.templateName) },
    commands: asArray(template.commands).map(command => ({
      commandName: stringValue(command.commandName || command.name),
      description: stringValue(command.description),
      commandParameters: asArray(command.commandParameters || command.parameters).map(normalizeCommandParameter)
    })),
    telemetry: {
      adapterAttributes: asArray(template.attributes).map(attr => ({ name: stringValue(attr.name), dataType: normalizeDataType(attr.dataType || attr.type, 'DOUBLE', adapterDataTypes), description: stringValue(attr.description) })),
      attributesMapping: []
    },
    events: [...eventGroups.cmdEvents.map(event => ({ ...event, eventType: 'CMD' })), ...eventGroups.opEvents.map(event => ({ ...event, eventType: 'OP' }))]
  }
}

function normalizeCommandParameter(param) {
  const row = { paramName: stringValue(param.paramName || param.name || param.key), dataType: normalizeDataType(param.dataType || param.type, 'DOUBLE', adapterDataTypes), description: stringValue(param.description || param.desc) }
  if (param.hidden === true) {
    row.hidden = true
    row.sourceField = stringValue(param.sourceField)
  }
  return row
}

function normalizeEventGroups(events) {
  const source = events || {}
  if (Array.isArray(source)) {
    const rows = normalizeEventsToFlatList(source)
    return { cmdEvents: rows.filter(event => event.eventName.startsWith('COMMAND_')), opEvents: rows.filter(event => !event.eventName.startsWith('COMMAND_')) }
  }
  return {
    cmdEvents: normalizeEventsToFlatList(source.cmdEvents || source.commandLifecycleEvents),
    opEvents: normalizeEventsToFlatList(source.opEvents || source.businessEvents)
  }
}

function normalizeEventsToFlatList(events) {
  const list = []
  if (events && typeof events === 'object' && !Array.isArray(events)) {
    asArray(events.cmdEvents).forEach(event => {
      list.push({ eventName: stringValue(event.eventName || event.name), description: stringValue(event.description || event.desc), eventType: 'CMD' })
    })
    asArray(events.opEvents).forEach(event => {
      list.push({ eventName: stringValue(event.eventName || event.name), description: stringValue(event.description || event.desc), eventType: 'OP' })
    })
  } else {
    asArray(events).forEach(event => {
      list.push({
        eventName: stringValue(event.eventName || event.name),
        description: stringValue(event.description || event.desc),
        eventType: event.eventType || (event.eventName?.startsWith('COMMAND_') ? 'CMD' : 'OP')
      })
    })
  }
  return list.filter(event => event.eventName)
}

function capabilityMappingRows(capabilities) {
  return asArray(capabilities).flatMap(capability => asArray(capability.parameterMapping).map(mapping => ({ capabilityDisplayName: capability.displayName || capability.name || '-', adapterCommandName: capability.adapterCommandName || '-', commandParamName: mapping.commandParamName || '-', sourceLabel: mapping.isFixedValue ? '固定值：' + (mapping.fixedValue ?? '') : displayCapabilityParam(capability, mapping.capabilityParamName) })))
}

function summaryText(model) {
  const parts = []
  if (model.attributes.length) parts.push('属性')
  if (model.capabilities.length) parts.push('操作')
  if (model.adapterContract.commands.length) parts.push('Adapter 契约')
  return parts.length ? parts.join('、') : '尚未补充模型内容'
}

function onKeywordInput() {
  if (searchTimer) window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { pageNo.value = 1; loadData() }, 260)
}

function categoryNameById(id) { return categories.value.find(item => String(item.id) === String(id))?.categoryName || '' }
function commandParameterOptions(commandName) { return visibleCommandParameters(draft.adapterContract.commands.find(item => item.commandName === commandName)).map(item => item.paramName).filter(Boolean) }
function capabilityParameterOptions(capability) { return asArray(capability.parameters).map((item, index) => ({ key: item._key, label: item.displayName || item.name || '参数' + (index + 1) })).filter(item => item.key) }
function displayAttributeName(name, attributes) { if (!name) return '-'; const attr = asArray(attributes).find(item => item.name === name); return attr?.displayName || name }
function displayCapabilityParam(capability, name) { if (!name) return '-'; const param = asArray(capability.parameters).find(item => item.name === name); return param?.displayName || name }
function stateActionSummary(actions) { const names = asArray(actions).map(describeAction).filter(Boolean); return names.length ? names.join('、') : '-' }
function describeAction(action) {
  if (!action?.actionName) return ''
  if (action.actionName === 'SEND') {
    const interfaceName = action.payload?.interfaceName || '接口'
    const signalName = action.payload?.signalName || '信号'
    const verb = interfaceName === 'Interface_adapter_out' ? '下发' : '输出'
    return verb + ' ' + interfaceName + ' / ' + signalName
  }
  if (action.actionName === 'ASSIGN') return '更新 ' + (action.payload?.target || '状态变量')
  return action.actionName
}

function valueKindLabel(value) { return value === 'DISCRETE' ? '离散值' : '连续值' }
function directionLabel(value) { return value === 'IN' ? '输入' : '输出' }
function formatTime(value) { return value ? String(value).replace('T', ' ') : '-' }
function formatJson(value) { return JSON.stringify(value || {}, null, 2) }
function dataTypeOptions(base, current) { return current && !base.includes(current) ? [...base, current] : base }
function asArray(value) { return Array.isArray(value) ? value : [] }
function firstDefined(...values) { return values.find(value => value !== undefined && value !== null) }
function deepClone(value) { return JSON.parse(JSON.stringify(value ?? [])) }
function ensureArrayField(target, key) { if (!Array.isArray(target[key])) target[key] = []; return target[key] }
function isNumeric(value) { return value !== '' && value != null && !Number.isNaN(Number(value)) }
function numericOrNull(value) { return isNumeric(value) ? Number(value) : null }
function stringValue(value) { return value == null ? '' : String(value).trim() }
function parseBoundaryValue(value) { if (value === '' || value == null) return value; const num = Number(value); return Number.isNaN(num) ? value : num }
function normalizeOperator(value) { const map = { '>': 'GT', '<': 'LT', '>=': 'GE', '<=': 'LE', '=': 'EQ', '==': 'EQ', '!=': 'NE' }; return map[value] || value || 'GT' }
function normalizeDataType(value, fallback, allowed) { const text = String(value || '').toUpperCase(); return allowed.includes(text) ? text : fallback }
function makeUiKey(prefix) { uiSeq += 1; return prefix + '_' + Date.now() + '_' + uiSeq }
function findKeyByName(rows, name) { return asArray(rows).find(item => item.name === name)?._key || '' }

function uniqueName(base, used) {
  let name = String(base || 'item').replace(/[^A-Za-z0-9_]+/g, '_').replace(/^_+|_+$/g, '') || 'item'
  let next = name
  let seq = 2
  while (used.has(next)) { next = name + '_' + seq; seq += 1 }
  used.add(next)
  return next
}

function generatedName(row, prefix, index, used) {
  const existing = stringValue(row.name || row.portName || row.paramName)
  return uniqueName(existing || prefix + '_' + (index + 1), used)
}

function materializeAttributes(rows) {
  const used = new Set()
  const nameByKey = new Map()
  const materializedRows = []
  asArray(rows).forEach((item, index) => {
    if (!stringValue(item.displayName || item.name)) return
    const name = generatedName(item, 'attr', index, used)
    nameByKey.set(item._key, name)
    materializedRows.push({ name, displayName: stringValue(item.displayName || item.name), valueKind: item.valueKind || 'CONTINUOUS', dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes), unit: stringValue(item.unit) })
  })
  return { rows: materializedRows, nameByKey }
}

function materializePorts(rows, attrNameByKey) {
  const used = new Set()
  return asArray(rows).filter(item => stringValue(item.displayName || item.portName) || item.bindingAttrKey || item.bindingAttrName).map((item, index) => ({ portName: generatedName({ name: item.portName }, 'port', index, used), displayName: stringValue(item.displayName || item.portName), direction: item.direction || 'OUT', bindingAttrName: attrNameByKey.get(item.bindingAttrKey) || stringValue(item.bindingAttrName) })).filter(item => item.bindingAttrName)
}

function materializeCapabilities(rows, functionMappings = []) {
  const usedCapabilities = new Set()
  return asArray(rows).filter(item => stringValue(item.displayName || item.name)).map((item, index) => {
    const parameterResult = materializeCapabilityParameters(item.parameters)
    const mapping = functionMappings.find(row => row.capabilityKey === item._key)
    return {
      name: generatedName(item, 'capability', index, usedCapabilities),
      adapterCommandName: stringValue(mapping?.adapterCommandName),
      displayName: stringValue(item.displayName || item.name),
      parameters: parameterResult.rows,
      parameterMapping: materializeParameterMapping(mapping?.parameterMapping, parameterResult.nameByKey)
    }
  })
}

function materializeCapabilityParameters(rows) {
  const used = new Set()
  const nameByKey = new Map()
  const materializedRows = []
  asArray(rows).forEach((item, index) => {
    if (!stringValue(item.displayName || item.name)) return
    const name = generatedName(item, 'param', index, used)
    nameByKey.set(item._key, name)
    materializedRows.push({ name, displayName: stringValue(item.displayName || item.name), dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes) })
  })
  return { rows: materializedRows, nameByKey }
}

function materializeFunctionMappings(rows) {
  return asArray(rows).map(row => ({
    capabilityKey: row.capabilityKey,
    adapterCommandName: stringValue(row.adapterCommandName),
    parameterMapping: asArray(row.parameterMapping)
  })).filter(row => row.capabilityKey || row.adapterCommandName || row.parameterMapping.length)
}

function materializeParameterMapping(rows, nameByKey) {
  return asArray(rows).filter(item => stringValue(item.commandParamName)).map(item => {
    const mapped = { commandParamName: stringValue(item.commandParamName), isFixedValue: !!item.isFixedValue }
    if (mapped.isFixedValue) mapped.fixedValue = parseFixedValue(item.fixedValue)
    else mapped.capabilityParamName = nameByKey.get(item.capabilityParamKey) || stringValue(item.capabilityParamName)
    return mapped
  })
}

function parseFixedValue(value) { const text = value == null ? '' : String(value).trim(); if (text.toLowerCase() === 'true') return true; if (text.toLowerCase() === 'false') return false; if (text !== '' && !Number.isNaN(Number(text))) return Number(text); return value }

function materializeIntrinsicConstraints(rows, attrNameByKey) {
  return asArray(rows).map(item => ({ objectAttributeName: attrNameByKey.get(item.objectAttributeKey) || stringValue(item.objectAttributeName), operator: normalizeOperator(item.operator), boundaryValue: parseBoundaryValue(item.boundaryValue), violationStateName: stringValue(item.violationStateName) })).filter(item => item.objectAttributeName && item.operator && item.violationStateName)
}

function cleanAttributesForExport(rows) {
  return asArray(rows).map(item => ({ name: stringValue(item.name), displayName: stringValue(item.displayName || item.name), valueKind: item.valueKind || 'CONTINUOUS', dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes), unit: stringValue(item.unit) })).filter(item => item.name)
}

function cleanCapabilitiesForExport(rows) {
  return asArray(rows).map(item => ({ name: stringValue(item.name), adapterCommandName: stringValue(item.adapterCommandName), displayName: stringValue(item.displayName || item.name), parameters: asArray(item.parameters).map(param => ({ name: stringValue(param.name), displayName: stringValue(param.displayName || param.name), dataType: normalizeDataType(param.dataType, 'DOUBLE', attributeDataTypes) })).filter(param => param.name), parameterMapping: asArray(item.parameterMapping).map(mapping => { const row = { commandParamName: stringValue(mapping.commandParamName), isFixedValue: !!mapping.isFixedValue }; if (row.isFixedValue) row.fixedValue = mapping.fixedValue; else row.capabilityParamName = stringValue(mapping.capabilityParamName); return row }).filter(mapping => mapping.commandParamName) })).filter(item => item.name)
}

function cleanAdapterContract(contract, attrNameByKey = new Map()) {
  const source = normalizeAdapterContract(contract)

  const cmdEvents = []
  const opEvents = []

  if (source.events && typeof source.events === 'object' && !Array.isArray(source.events)) {
    asArray(source.events.cmdEvents).forEach(event => {
      cmdEvents.push({ eventName: stringValue(event.eventName), description: stringValue(event.description) })
    })
    asArray(source.events.opEvents).forEach(event => {
      opEvents.push({ eventName: stringValue(event.eventName), description: stringValue(event.description) })
    })
  } else {
    asArray(source.events).forEach(event => {
      const row = { eventName: stringValue(event.eventName), description: stringValue(event.description) }
      if (event.eventType === 'CMD' || event.eventName.startsWith('COMMAND_')) {
        cmdEvents.push(row)
      } else {
        opEvents.push(row)
      }
    })
  }

  return {
    config: { protocol: source.config.protocol || 'MQTT', adapterName: stringValue(source.config.adapterName), templateName: stringValue(source.config.templateName) },
    commands: asArray(source.commands).map(command => ({
      commandName: stringValue(command.commandName),
      description: stringValue(command.description),
      commandParameters: asArray(command.commandParameters)
        .filter(param => param.hidden !== true) // Filter out hidden parameters
        .map(param => ({
          paramName: stringValue(param.paramName),
          dataType: normalizeDataType(param.dataType, 'DOUBLE', adapterDataTypes),
          description: stringValue(param.description)
        })).filter(param => param.paramName)
    })).filter(command => command.commandName),
    telemetry: {
      adapterAttributes: asArray(source.telemetry.adapterAttributes).map(attr => ({ name: stringValue(attr.name), dataType: normalizeDataType(attr.dataType, 'DOUBLE', adapterDataTypes), description: stringValue(attr.description) })).filter(attr => attr.name),
      attributesMapping: asArray(source.telemetry.attributesMapping).map(mapping => ({ adapterAttrName: stringValue(mapping.adapterAttrName), modelAttributeName: attrNameByKey.get(mapping.modelAttributeKey) || stringValue(mapping.modelAttributeName) })).filter(mapping => mapping.adapterAttrName && mapping.modelAttributeName)
    },
    events: {
      cmdEvents: cmdEvents.filter(e => e.eventName),
      opEvents: opEvents.filter(e => e.eventName)
    }
  }
}

function cleanPortsForExport(rows) { return asArray(rows).map(item => ({ portName: stringValue(item.portName), direction: item.direction || 'OUT', bindingAttrName: stringValue(item.bindingAttrName) })).filter(item => item.portName && item.bindingAttrName) }
function cleanIntrinsicConstraintsForExport(rows) { return asArray(rows).map(item => ({ objectAttributeName: stringValue(item.objectAttributeName), operator: normalizeOperator(item.operator), boundaryValue: parseBoundaryValue(item.boundaryValue), violationStateName: stringValue(item.violationStateName) })).filter(item => item.objectAttributeName && item.operator && item.violationStateName) }
function cleanInterfaces(rows) { return asArray(rows).map(item => ({ name: stringValue(item.name), direction: item.direction || 'IN', interfaceType: item.interfaceType || 'ADAPTER', allowedSignals: asArray(item.allowedSignals).map(stringValue).filter(Boolean) })).filter(item => item.name) }
function cleanActions(actions) { return asArray(actions).map(action => ({ actionName: stringValue(action.actionName), payload: normalizePayload(action.payload || action.parameters) })).filter(action => action.actionName) }
function cleanStateSpace(space, fallback, spaceType) { const source = normalizeStateSpace(space, fallback, spaceType); const states = asArray(source.states).map(item => ({ stateName: stringValue(item.stateName), onEntry: cleanActions(item.onEntry) })).filter(item => item.stateName); return { initialStateName: stringValue(source.initialStateName || states[0]?.stateName || fallback), states } }
function cleanTransitions(rows) { return asArray(rows).filter(row => !isCommandLifecycleTransition(row)).map(item => ({ description: stringValue(item.description), fromStateName: stringValue(item.fromStateName), toStateName: stringValue(item.toStateName), trigger: { interfaceName: stringValue(item.trigger?.interfaceName || defaultAdapterInterfaceName()), signalName: stringValue(item.trigger?.signalName) }, actions: cleanActions(item.actions) })).filter(item => item.fromStateName && item.toStateName && item.trigger.interfaceName && item.trigger.signalName) }

function normalizeAttributes(value) { return asArray(value).map(item => ({ _key: item._key || makeUiKey('attr'), name: stringValue(item.name), displayName: stringValue(item.displayName || item.name), valueKind: item.valueKind === 'DISCRETE' ? 'DISCRETE' : 'CONTINUOUS', dataType: normalizeDataType(item.dataType, 'DOUBLE', attributeDataTypes), unit: stringValue(item.unit) })) }

// Normalize capabilities
function normalizeCapabilities(value) {
  return asArray(value).map(item => {
    const params = asArray(item.parameters).map(param => ({ _key: param._key || makeUiKey('param'), name: stringValue(param.name), displayName: stringValue(param.displayName || param.name), dataType: normalizeDataType(param.dataType, 'DOUBLE', attributeDataTypes) }))
    return { _key: item._key || makeUiKey('cap'), name: stringValue(item.name), displayName: stringValue(item.displayName || item.name), adapterCommandName: stringValue(item.adapterCommandName), parameters: params, parameterMapping: [] }
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
        capabilityParamKey: findKeyByName(capability.parameters, mapping.capabilityParamName),
        isFixedValue: !!mapping.isFixedValue,
        fixedValue: mapping.fixedValue ?? ''
      }))
    }
  }).filter(mapping => mapping.adapterCommandName || mapping.parameterMapping.length)
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
      eventType: event.eventType || (event.eventName?.startsWith('COMMAND_') ? 'CMD' : 'OP')
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
.device-model-page { display: flex; flex-direction: column; height: 100%; min-height: 0; padding: 16px; gap: 14px; background: #f0f2f5; color: var(--color-text-main); }
.page-header { display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; gap: 16px; }
.page-title h1 { margin: 0 0 4px; font-size: 22px; line-height: 1.25; letter-spacing: 0; }
.page-title p { margin: 0; color: var(--color-text-sub); font-size: 13px; }
.header-actions, .detail-actions, .section-title, .config-import, .drawer-footer, .section-actions { display: flex; align-items: center; gap: 8px; }
.content-shell { flex: 1; min-height: 0; display: grid; grid-template-columns: 286px minmax(0, 1fr); gap: 14px; }
.model-list-panel, .detail-panel { min-height: 0; background: #fff; border: 1px solid var(--panel-border); border-radius: 8px; box-shadow: var(--shadow-sm); }
.model-list-panel { display: flex; flex-direction: column; overflow: hidden; }
.list-tools { padding: 12px; border-bottom: 1px solid #e5e7eb; }
.model-list { flex: 1; min-height: 0; padding: 8px; }
.model-row { width: 100%; display: flex; flex-direction: column; align-items: flex-start; gap: 4px; border: 1px solid transparent; background: #fff; border-radius: 6px; padding: 10px 12px; text-align: left; cursor: pointer; color: var(--color-text-main); }
.model-row:hover { background: #f6f8fb; border-color: #dbe2ef; }
.model-row.active { background: #e8f1fb; border-color: #9bc2ec; }
.model-name { max-width: 100%; font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.model-meta, .model-summary { max-width: 100%; color: var(--color-text-sub); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.list-footer { flex-shrink: 0; padding: 10px 12px; border-top: 1px solid #e5e7eb; }
.detail-panel { display: flex; flex-direction: column; overflow: hidden; padding: 14px; }
.detail-head { flex-shrink: 0; display: flex; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid #e5e7eb; }
.detail-title { min-width: 0; }
.detail-title h2 { margin: 0 0 4px; font-size: 20px; line-height: 1.25; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; letter-spacing: 0; }
.detail-title p { margin: 0; color: var(--color-text-sub); font-size: 13px; }
.detail-tabs { flex: 1; min-height: 0; overflow: hidden; }
.detail-tabs :deep(.el-tabs__content) { height: calc(100% - 46px); overflow: auto; padding-right: 4px; }
.info-section, .drawer-section { margin-top: 14px; }
.section-title { justify-content: space-between; margin-bottom: 10px; min-height: 28px; }
.section-title h3 { margin: 0; font-size: 15px; line-height: 1.3; letter-spacing: 0; }
.tag-gap { margin: 2px 6px 2px 0; }
.muted-text { color: var(--color-text-sub); font-size: 12px; }
.summary-card-list, .editor-card-list { display: grid; gap: 10px; }
.summary-card, .editor-card { border: 1px solid #dfe4ed; border-radius: 8px; background: #fff; overflow: hidden; }
.summary-card { padding: 10px 12px; }
.summary-card-head, .editor-card-head, .nested-toolbar, .locked-heading, .locked-action, .action-toolbar { display: flex; align-items: center; gap: 8px; }
.summary-card-head, .editor-card-head { justify-content: space-between; }
.summary-card-title, .editor-card-title { min-width: 0; display: flex; align-items: center; gap: 8px; flex: 1; }
.summary-card-title { flex-direction: column; align-items: flex-start; gap: 2px; }
.summary-card-title strong { font-size: 14px; line-height: 1.35; }
.summary-card-title span { max-width: 100%; color: var(--color-text-sub); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-card-body { margin-top: 8px; padding-left: 30px; }
.item-index { width: 22px; height: 22px; flex: 0 0 22px; display: inline-flex; align-items: center; justify-content: center; border-radius: 6px; background: #eef2f7; color: #475569; font-size: 12px; font-weight: 700; }
.capability-summary-card, .capability-editor-card { border-left: 3px solid #409eff; }
.command-summary-card, .command-editor-card { border-left: 3px solid #10b981; }
.editor-card { padding: 10px; background: #fbfcfe; }
.editor-card-title :deep(.el-input) { flex: 1; min-width: 180px; }
.nested-toolbar { justify-content: space-between; margin: 10px 0 8px; color: var(--color-text-sub); font-size: 12px; font-weight: 600; }
.nested-table { background: #fff; }
.compact-empty { display: flex; align-items: center; min-height: 34px; padding: 8px 12px; border: 1px dashed #d5dce8; border-radius: 6px; background: #f8fafc; color: #64748b; font-size: 13px; line-height: 1.4; }
.block-empty { margin-top: 4px; }
.inline-empty { margin-top: 8px; }
.model-json-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; min-height: 0; }
.json-panel { min-width: 0; border: 1px solid #dfe4ed; border-radius: 8px; padding: 12px; background: #fff; }
.json-panel pre { margin: 0; max-height: 560px; overflow: auto; padding: 12px; border-radius: 6px; background: #111827; color: #e5e7eb; font-size: 12px; line-height: 1.55; }
.drawer-body { height: 100%; min-height: 0; }
.drawer-tabs { height: 100%; }
.drawer-tabs :deep(.el-tabs__content) { height: 100%; overflow: auto; padding: 0 4px 18px 18px; }
.drawer-tabs :deep(.el-tabs__header) { width: 116px; }
.basic-form, .mapping-form { max-width: 760px; }
.basic-form :deep(.el-select), .mapping-form :deep(.el-select) { width: 100%; }
.config-import { margin-bottom: 8px; }
.param-map-editor { display: flex; flex-direction: column; gap: 8px; }
.param-map-row { display: grid; grid-template-columns: minmax(150px, 1fr) 74px minmax(170px, 1fr) 34px; align-items: center; gap: 6px; padding: 6px; border: 1px solid transparent; border-radius: 6px; background: #f8fafc; }
.param-map-row.invalid { border-color: #f4b4b4; background: #fff7f7; }
.map-warning { grid-column: 1 / -1; color: #c2410c; font-size: 12px; line-height: 1.4; }
.locked-heading { display: flex; align-items: center; gap: 8px; min-width: 0; }
.locked-heading h3 { margin: 0; }
.locked-section { background: #ffffff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; }
.locked-table :deep(.el-table__body-wrapper) { background: #ffffff; }
.locked-action { max-width: 100%; color: #475569; font-size: 12px; line-height: 1.45; display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; white-space: normal; }
.locked-value { font-weight: 600; color: #334155; }
.action-editor { display: flex; flex-direction: column; gap: 6px; }
.action-row { display: grid; grid-template-columns: 112px minmax(220px, 1fr) 34px; align-items: center; gap: 6px; }
.action-toolbar { justify-content: flex-start; }
.drawer-footer { justify-content: flex-end; }

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

@media (max-width: 1120px) { .content-shell { grid-template-columns: 240px minmax(0, 1fr); } .model-json-grid { grid-template-columns: 1fr; } }
@media (max-width: 820px) { .device-model-page { padding: 10px; } .page-header, .detail-head { align-items: stretch; flex-direction: column; } .content-shell { grid-template-columns: 1fr; } .model-list-panel { min-height: 260px; } .drawer-tabs :deep(.el-tabs__header) { width: 92px; } .param-map-row, .action-row { grid-template-columns: 1fr; } .summary-card-body { padding-left: 0; } }

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
