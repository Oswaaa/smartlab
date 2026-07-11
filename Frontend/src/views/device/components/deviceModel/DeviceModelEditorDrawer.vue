<template>
<el-drawer v-model="drawerVisible" :title="drawerTitle" direction="rtl" size="78%" destroy-on-close class="model-drawer">
      <div class="drawer-body">
        <div class="detail-anchor-layout model-edit-workbench">
          <el-anchor class="detail-anchor-menu" @click="(e) => e.preventDefault()" container=".edit-scroll-content .el-scrollbar__wrap" :offset="20">
            <el-anchor-link href="#edit-basic" title="01 基础信息" />
            <el-anchor-link href="#edit-ability" title="02 属性功能" />
            <el-anchor-link href="#edit-adapter" title="03 Adapter 契约" />
            <el-anchor-link href="#edit-mapping" title="04 映射关系" />
            <el-anchor-link href="#edit-state" title="05 状态机" />
            <el-anchor-link href="#edit-constraint" title="06 内置约束" />
            <el-anchor-link href="#edit-bom" title="07 组件结构" />
            <el-anchor-link href="#edit-template" title="08 默认数据模板" />
            <el-anchor-link href="#edit-file" title="09 模型文件" />
          </el-anchor>
          <el-scrollbar class="edit-scroll-content">
                    <div id="edit-basic" class="anchor-section industrial-section">
            <h2 class="section-heading"><span class="section-index">01</span>基础信息</h2>
            <section class="drawer-section">
              <div class="section-title"><h3>基础信息</h3></div>
              <el-form label-width="96px" size="small" class="basic-form">
                <el-form-item label="模型名称" required>
                  <el-input v-model="draft.basic.modelName" placeholder="例如：反应釜温控模块" maxlength="80" show-word-limit />
                </el-form-item>
                <el-form-item label="所属分类" prop="categoryValue">
                  <el-tree-select
                    v-model="draft.basic.categoryValue"
                    :data="categoryTreeForSelect"
                    node-key="id"
                    check-strictly
                    :render-after-expand="false"
                    placeholder="选择分类"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-form>
            </section>
          </div>

                    <div id="edit-ability" class="anchor-section industrial-section">
            <h2 class="section-heading"><span class="section-index">02</span>属性功能</h2>
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
            <h2 class="section-heading"><span class="section-index">03</span>Adapter 契约</h2>
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
                    <el-select v-model="selectedRegisteredAdapterTemplate" filterable clearable placeholder="选择设备类别" :disabled="!registeredAdapterTemplateOptions.length">
                      <el-option
                        v-for="tpl in registeredAdapterTemplateOptions"
                        :key="adapterCategoryKey(tpl)"
                        :label="adapterCategoryLabel(tpl)"
                        :value="adapterCategoryKey(tpl)"
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
            <h2 class="section-heading"><span class="section-index">04</span>映射关系</h2>
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
            <h2 class="section-heading"><span class="section-index">05</span>状态机</h2>
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
                      <state-select v-model="row.trigger.signalName" :options="adapterEventOptions" style="width: 100%" />
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
            <h2 class="section-heading"><span class="section-index">06</span>内置约束</h2>
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
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.componentsBom, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </div>

                    <div id="edit-template" class="anchor-section industrial-section">
            <h2 class="section-heading"><span class="section-index">08</span>默认数据模板</h2>
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
            <h2 class="section-heading"><span class="section-index">09</span>模型文件</h2>
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

  <el-dialog v-model="adapterTemplateDialogVisible" title="选择要绑定的 Adapter 类别" width="500px">
    <div style="margin-bottom: 15px;">检测到配置文件中包含 Adapter 类别，请选择需要绑定到当前设备模型的类别：</div>
    <el-form label-width="80px">
      <el-form-item label="选择类别">
        <el-select v-model="selectedAdapterTemplate" placeholder="请选择类别" style="width: 100%">
          <el-option
            v-for="item in adapterTemplatesList"
            :key="adapterCategoryKey(item)"
            :label="adapterCategoryLabel(item)"
            :value="adapterCategoryKey(item)"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="adapterTemplateDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmAdapterTemplateSelection">确认绑定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, defineComponent, nextTick, resolveComponent, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Upload, Warning, Lock, Right, Close, Refresh } from '@element-plus/icons-vue'
import {
  saveDeviceModel,
  previewDeviceModel,
  listDataPropertyTypes,
  listRegisteredAdapters,
  fetchRegisteredAdapterContract,
  fetchDefaultTemplateAttributes,
} from './deviceModelManagementApi'
import {
  adapterDeviceCategoryOptions,
  buildAdapterContractFromManifestCategory,
  adapterCategoryKey,
  adapterCategoryLabel,
  attributeDataTypes,
  adapterDataTypes,
  operators,
  standardCmdEvents,
  defaultStateSpace,
  defaultAdapterContract,
  defaultInterfaces,
  defaultCommandLifecycleTransitions,
  defaultCommandLifecycle,
  getSignalsForInterface,
  defaultStateEntryActions,
  adapterInterfaceName
} from './deviceModelConstants'
import {
  asArray, firstDefined, deepClone, ensureArrayField, isNumeric,
  numericOrNull, stringValue, stringId, normalizeDataType, makeUiKey,
  findKeyByName, lookupName, reserveIdentifier, materializedName,
  eventTypeLabel, operatorLabel, valueKindLabel, directionLabel,
  formatTime, formatJson, dataTypeOptions, describeAction,
  capabilityParamDisplayName, functionMappingGroups, capabilityMappingRows,
  displayAttributeName, normalizeCommandParameter, normalizeEventsToFlatList,
  normalizeAttributes, normalizeCapabilities, extractFunctionMappings,
  normalizeFunctionMappings, normalizeAdapterContract, normalizePorts,
  normalizeOperator, normalizeIntrinsicConstraints, normalizeInterfaces,
  normalizeStateSpace, normalizeTransitions, normalizePayload,
  normalizeModelBundle, materializeAttributes, materializePorts,
  materializeCapabilities, materializeParameterMappings,
  materializeFunctionMappings, materializeIntrinsicConstraints
} from './normalizers.js'

const props = defineProps({
  categories: Array
})

const emit = defineEmits(['saved', 'update:visible'])

// ── Drawer 可见性 ──────────────────────────────────────
const drawerVisible = ref(false)
const drawerMode = ref('create')

const drawerTitle = computed(() => drawerMode.value === 'create' ? '新建设备模型' : '编辑设备模型')

// ── Adapter 选择对话框 ────────────────────────────────
const adapterTemplateDialogVisible = ref(false)
const adapterTemplatesList = ref([])
const selectedAdapterTemplate = ref('')
const tempAdapterConfigRaw = ref(null)

// ── Adapter 注册相关 ──────────────────────────────────
const adapterConfigText = ref('')
const registeredAdapters = ref([])
const registeredAdapterLoading = ref(false)
const selectedRegisteredAdapterName = ref('')
const selectedRegisteredAdapterTemplate = ref('')

const registeredAdapterTemplateOptions = computed(() => {
  const adapter = registeredAdapters.value.find(item => item.adapterName === selectedRegisteredAdapterName.value)
  return adapterDeviceCategoryOptions(parsedAdapterConfig(adapter))
})

// ── 接口锁定 ──────────────────────────────────────────
const interfaceLocked = ref(true)

const commandNameOptions = computed(() => draft.adapterContract.commands.map(item => item.commandName).filter(Boolean))
const adapterAttributeNameOptionsDetailed = computed(() => draft.adapterContract.telemetry.adapterAttributes || [])

const attributeOptions = computed(() => draft.attributes.map((item, index) => ({ key: item._key, label: item.displayName || item.name || '属性' + (index + 1) })).filter(item => item.key))
const capabilitySelectOptions = computed(() => draft.capabilities.map((item, index) => ({ key: item._key, label: item.displayName || item.name || '功能' + (index + 1) })).filter(item => item.key))

// ─── Parameter Helpers ────────────────────────────────
function commandParameterOptionsDetailed(commandName) {
  const cmd = draft.adapterContract.commands.find(c => c.commandName === commandName)
  return cmd ? cmd.commandParameters.filter(p => !p.internal) : []
}

function capabilityParameterOptionsDetailedByKey(capabilityKey) {
  const cap = draft.capabilities.find(c => c._key === capabilityKey)
  return cap ? cap.parameters : []
}

// ─── Local UI Components ──────────────────────────────
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

// ─── CategoryTree for el-tree-select ────────────────
const categoryTreeForSelect = computed(() => {
  const buildTree = (parentId) => {
    return (props.categories || [])
      .filter(c => String(c.parentCategoryId || '') === String(parentId || ''))
      .map(c => {
        const children = buildTree(c.id)
        return {
          id: String(c.id),
          label: c.categoryName,
          disabled: children.length > 0,
          children: children.length === 0 ? undefined : children
        }
      })
  }
  return buildTree('')
})

// ─── Public API (exposed to parent) ─────────────────
const saving = ref(false)
const draft = reactive(emptyDraft())

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

function openForCreate(categoryId) {
  console.log('[Drawer] openForCreate called with categoryId:', categoryId)
  try {
    drawerMode.value = 'create'
    replaceDraft(emptyDraft())
    if (categoryId) {
      draft.basic.categoryValue = String(categoryId)
    }
    loadPropertyTypesForTemplate()
    fetchRegisteredAdapters() // 自动加载已注册的 Adapter 列表，免去手动刷新
    drawerVisible.value = true
    console.log('[Drawer] drawerVisible set to true successfully.')
  } catch (err) {
    console.error('[Drawer] Failed in openForCreate:', err)
  }
}

async function openForEdit(model, defaultAttrs) {
  console.log('[Drawer] openForEdit called for model:', model?.modelId)
  try {
    drawerMode.value = 'edit'
    // 立刻打开抽屉，让滑入动画即时开始，不被任何计算阻塞
    drawerVisible.value = true
    console.log('[Drawer] drawerVisible set to true successfully.')

    // 等 Vue 完成本轮渲染帧后，再执行重数据填充，确保不阻塞动画
    await nextTick()

    replaceDraft(fromModelToDraft(model, defaultAttrs))
    loadPropertyTypesForTemplate()
    prepareAdapterSourcePicker(draft.adapterContract?.config?.adapterName, draft.adapterContract?.config?.categoryName || draft.adapterContract?.config?.templateName)

    // 纯净的异步兜底逻辑：仅在缓存为空且有模型ID时启动静默网络抓取
    if ((!defaultAttrs || defaultAttrs.length === 0) && model?.modelId) {
      console.log('[Drawer] defaultAttrs is empty. Fetching fallback template attributes in background...')
      const fallbackAttrs = await fetchDefaultTemplateAttributes(model.modelId, model.attributes || [])
      if (asArray(fallbackAttrs).length > 0) {
        const normalizedAttributes = normalizeAttributes(draft.attributes)
        const attrKeys = asArray(fallbackAttrs).map(a => a.name).filter(Boolean).map(nameOrKey => {
          const attr = normalizedAttributes.find(a => a.name === nameOrKey || a._key === nameOrKey)
          return attr ? attr._key : null
        }).filter(Boolean)
        draft.defaultDataTemplateAttrs = attrKeys
      }
    }
  } catch (err) {
    console.error('[Drawer] Failed in openForEdit:', err)
  }
}

defineExpose({ openForCreate, openForEdit, buildSavePayload, fromModelToDraft, replaceDraft, loadPropertyTypesForTemplate })

// ─── Extracted editor logic ──────────────────────────
function addBomComponent() {
  draft.componentsBom.push({ _key: makeUiKey('bom'), slotName: '', categoryId: '', quantity: 1, description: '' })
}
const adapterEventOptions = computed(() => opEventNames(draft.adapterContract.events))
const adapterSignalOptions = computed(() => uniqueStrings([...standardCmdEvents, ...adapterEventOptions.value]))
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

const draftCapabilityModelJson = ref({})
const draftStateMachineModelJson = ref({})
const generatingPreview = ref(false)

const generatePreview = async () => {
  generatingPreview.value = true
  try {
    const payload = buildSavePayload(false)
    const res = await previewDeviceModel(payload)
    if (!res?.success) {
      ElMessage.error(res?.message || '生成预览失败')
      return
    }
    const bundle = normalizeModelBundle(res?.data)
    draftCapabilityModelJson.value = bundle.capabilityModel
    draftStateMachineModelJson.value = bundle.stateMachineModel
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '生成预览异常')
    console.error(error)
  } finally {
    generatingPreview.value = false
  }
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
    const res = await saveDeviceModel(payload)
    if (!res.success) {
      ElMessage.error(res.message || '保存失败')
      return
    }
    const savedModelId = String(res.data?.modelId || payload.modelId || '')
    drawerVisible.value = false
    ElMessage.success('保存成功')
    emit('saved', savedModelId)
  } catch (err) {
    ElMessage.error(err.response?.data?.message || err.message || '保存失败')
  } finally {
    saving.value = false
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
      commandParameters: cmd.commandParameters.filter(p => p.internal !== true).map(p => ({
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
    componentsBom: asArray(draft.componentsBom).map(item => ({
      slotName: stringValue(item.slotName),
      categoryId: item.categoryId ? Number(item.categoryId) : null,
      categoryName: categoryNameById(item.categoryId),
      quantity: Number(item.quantity) || 1,
      description: stringValue(item.description)
    })).filter(item => item.slotName),
    defaultDataTemplate: buildDefaultDataTemplate(attributeResult.rowByKey, propertyTypes)
  }
  if (isNumeric(draft.basic.categoryValue)) payload.categoryId = Number(draft.basic.categoryValue)
  else if (draft.basic.categoryValue) payload.categoryName = String(draft.basic.categoryValue).trim()
  return payload
}

function categoryNameById(id) {
  if (!id) return ''
  const cat = props.categories?.find(c => String(c.id) === String(id))
  return cat ? cat.categoryName : ''
}

async function loadPropertyTypesForTemplate() {
  return await listDataPropertyTypes()
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

function fromModelToDraft(model, defaultAttrs = []) {
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
    defaultDataTemplateAttrs: asArray(defaultAttrs).map(a => a.name).filter(Boolean)
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
  autoPopulateParameterMappings(row)
}

function handleFunctionMappingCommandChange(row) {
  if (isAdapterCommandMapped(row.adapterCommandName, row)) row.adapterCommandName = ''
  autoPopulateParameterMappings(row)
}

function autoPopulateParameterMappings(row) {
  if (!row.adapterCommandName) {
    row.parameterMapping = []
    return
  }

  const adapterParams = commandParameterOptionsDetailed(row.adapterCommandName)
  const capabilityParams = capabilityParameterOptionsDetailedByKey(row.capabilityKey)

  const existingMap = new Map()
  asArray(row.parameterMapping).forEach(m => {
    if (m.commandParamName) {
      existingMap.set(m.commandParamName, m)
    }
  })

  row.parameterMapping = adapterParams.map(param => {
    const existing = existingMap.get(param.paramName)

    if (existing) {
      const isBoundValid = capabilityParams.some(p => p._key === existing.capabilityParamKey)
      if (!existing.isFixedValue && !isBoundValid) {
        const matched = findSmartMatchParam(param, capabilityParams, row.adapterCommandName)
        existing.capabilityParamKey = matched ? matched._key : ''
        existing.capabilityParamName = matched ? stringValue(matched.name || matched.displayName) : ''
      }
      return existing
    }

    const matched = findSmartMatchParam(param, capabilityParams, row.adapterCommandName)
    return {
      _key: makeUiKey('param_map'),
      commandParamName: param.paramName,
      capabilityParamKey: matched ? matched._key : '',
      capabilityParamName: matched ? stringValue(matched.name || matched.displayName) : '',
      isFixedValue: false,
      fixedValue: ''
    }
  })
}

function findSmartMatchParam(adapterParam, capabilityParams, commandName) {
  if (!capabilityParams || !capabilityParams.length) return null
  return capabilityParams.find(p => {
    const pName = stringValue(p.name || p.displayName).toLowerCase()
    const paramName = stringValue(adapterParam.paramName).toLowerCase()
    return pName === paramName && isParamDataTypeMatch(adapterParam.paramName, commandName, p.dataType)
  })
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
  return asArray(command?.commandParameters).filter(param => param.internal !== true)
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
    actionName: 'SEND',
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
    registeredAdapters.value = await listRegisteredAdapters()
    if (selectedRegisteredAdapterName.value && !registeredAdapters.value.some(item => item.adapterName === selectedRegisteredAdapterName.value)) {
      selectedRegisteredAdapterName.value = ''
      selectedRegisteredAdapterTemplate.value = ''
    }
    if (selectedRegisteredAdapterName.value && selectedRegisteredAdapterTemplate.value) {
      const hasTemplate = registeredAdapterTemplateOptions.value.some(option => adapterCategoryKey(option) === selectedRegisteredAdapterTemplate.value)
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
    ElMessage.warning('请选择已注册 Adapter 和设备类别')
    return
  }
  registeredAdapterLoading.value = true
  try {
    const res = await fetchRegisteredAdapterContract(selectedRegisteredAdapterName.value, selectedRegisteredAdapterTemplate.value)
    if (!res.success) {
      ElMessage.error(res.message || '载入 Adapter 契约失败')
      return
    }
    assignAdapterContract(res.data)
    ElMessage.success(`已载入 Adapter 契约: ${selectedRegisteredAdapterName.value} / ${selectedRegisteredAdapterTemplate.value}`)
  } finally {
    registeredAdapterLoading.value = false
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
    const categories = adapterDeviceCategoryOptions(parsed)
    if (categories.length === 0) {
      ElMessage.warning('配置文本中未找到合法的 deviceCategories 数组')
      return
    }
    tempAdapterConfigRaw.value = parsed
    adapterTemplatesList.value = categories
    selectedAdapterTemplate.value = adapterCategoryKey(categories[0])
    adapterTemplateDialogVisible.value = true
  } catch (error) {
    ElMessage.error('配置文本不是有效的 JSON')
  }
}

function confirmAdapterTemplateSelection() {
  if (!selectedAdapterTemplate.value) {
    ElMessage.warning('请选择一个 Adapter 类别')
    return
  }
  const parsed = tempAdapterConfigRaw.value
  const selected = adapterTemplatesList.value.find(function(item) { return adapterCategoryKey(item) === selectedAdapterTemplate.value })
  if (!selected) return

  assignAdapterContract(buildAdapterContractFromManifestCategory(parsed, selected.category))

  adapterTemplateDialogVisible.value = false
  ElMessage.success('已成功解析并绑定类别: ' + selected.name)
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



function importAdapterFile(file) {
  const rawFile = file?.raw
  if (!rawFile) return false
  const reader = new FileReader()
  reader.onload = () => { adapterConfigText.value = String(reader.result || '') }
  reader.readAsText(rawFile, 'utf-8')
  return false
}
















function summaryText(model) {
  const parts = []
  if (model.attributes.length) parts.push('属性')
  if (model.capabilities.length) parts.push('操作')
  return parts.length ? parts.join('、') : '尚未补充模型内容'
}


</script>

<style scoped>
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
.action-editor-label { color: #64748b; font-size: 12px; }
.compact-action-btn { padding: 4px 8px; }
.serious-actions-container { display: flex; flex-direction: column; gap: 4px; align-items: flex-start; }
.serious-action-wrapper { display: inline-flex; align-items: center; }
.serious-action-tag { font-weight: 500; color: #334155; background-color: #ffffff; border-color: #cbd5e1; }
.serious-action-editor { display: inline-flex; align-items: center; gap: 6px; min-width: 0; }
.template-attr-option { margin-right: 14px; margin-bottom: 8px; padding: 7px 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #f8fafc; }
.template-attr-name { font-weight: 600; color: #0f172a; }
.template-attr-meta { margin-left: 8px; color: #64748b; font-size: 12px; }
.nested-toolbar { justify-content: space-between; margin: 10px 0 8px; color: var(--color-text-sub); font-size: 12px; font-weight: 600; }
.nested-table { background: #fff; }
.block-empty { margin-top: 4px; }
.drawer-section { margin-top: 8px; border: 1px solid #e5e7eb; border-radius: 4px; padding: 10px; background: #fff; }
.drawer-footer { justify-content: flex-end; display: flex; align-items: center; gap: 8px; }
.model-edit-workbench { display: flex; min-height: 0; overflow: hidden; height: calc(100vh - 120px); }
.edit-scroll-content { flex: 1; min-width: 0; }
.edit-scroll-content :deep(.el-scrollbar__view) { padding: 12px 14px 22px; }
.capability-editor-card, .capability-editor-list, .capability-title-editor { border-left: 3px solid #409eff; }
.command-editor-card, .command-editor-list { border-left: 3px solid #10b981; }
.locked-table :deep(.el-table__body-wrapper) { background: #ffffff; }
.locked-action { max-width: 100%; color: #475569; font-size: 12px; line-height: 1.45; display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; white-space: normal; }

/* ── 锚点布局与导航样式 ── */
.detail-anchor-layout { min-height: 0; background: #fff; border-top: 1px solid #e5e7eb; }
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
.detail-anchor-menu :deep(.el-anchor__link.is-active) {
  background: #dbeafe;
  color: #1d4ed8;
}
.anchor-section { scroll-margin-top: 8px; }
.anchor-section.industrial-section {
  margin-bottom: 12px;
  padding-top: 2px;
}
.section-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px !important;
  padding: 0 0 7px;
  border-bottom: 1px solid #dfe6ef;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}
.section-index {
  display: inline-flex;
  width: 30px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
}
.section-title { justify-content: space-between; margin-bottom: 8px; min-height: 26px; }
.section-title h3 { margin: 0; font-size: 15px; line-height: 1.3; letter-spacing: 0; }
.tag-gap { margin: 2px 6px 2px 0; }

/* ── 列表卡片编辑器样式 ── */
.editor-card-list { display: grid; gap: 8px; }
.editor-card { border: 1px solid #dfe4ed; border-radius: 4px; background: #fff; overflow: hidden; padding: 9px; }
.editor-card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.editor-card-title { min-width: 0; display: flex; align-items: center; gap: 8px; flex: 1; }
.editor-card-title :deep(.el-input) { flex: 1; min-width: 180px; }
.item-index { width: 22px; height: 22px; flex: 0 0 22px; display: inline-flex; align-items: center; justify-content: center; border-radius: 6px; background: #eef2f7; color: #475569; font-size: 12px; font-weight: 700; }
.drawer-section .editor-card { padding: 8px; }
.drawer-section .editor-card-list { gap: 8px; }

/* ── 缺省与占位样式 ── */
.compact-empty { display: flex; align-items: center; min-height: 30px; padding: 7px 10px; border: 1px dashed #cbd5e1; border-radius: 4px; background: #f8fafc; color: #64748b; font-size: 13px; line-height: 1.4; }
.inline-empty { margin-top: 8px; }
.no-mapping-placeholder {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-style: italic;
  display: inline-flex;
  align-items: center;
}
.param-map-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 4px;
  padding: 2px 4px;
}
.add-mapping-btn {
  align-self: flex-end;
}

/* ── JSON分栏面板样式 ── */
.model-json-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; min-height: 0; }
.json-panel { min-width: 0; border: 1px solid #dfe4ed; border-radius: 4px; padding: 10px; background: #fff; }
.json-panel pre { margin: 0; max-height: 560px; overflow: auto; padding: 12px; border-radius: 6px; background: #111827; color: #e5e7eb; font-size: 12px; line-height: 1.55; tab-size: 2; }
</style>

