<template>
<el-drawer v-model="drawerVisible" :title="drawerTitle" direction="rtl" size="78%" destroy-on-close class="model-drawer unified-workflow-drawer">
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
              <div class="section-title"><div class="section-header-copy"><h3>契约来源</h3><p class="section-note">Adapter 定义由注册配置提供，不可在设备模型中修改；本页面仅选择契约并配置业务映射。</p></div><el-tag size="small" type="info" effect="plain">只读来源</el-tag></div>
              <el-form label-width="90px" size="small">
                <el-form-item label="协议类型">
                  <el-text>{{ draft.adapterContract.config.protocol || '—' }}</el-text>
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
              </el-form>
            </section>

                        <section class="drawer-section">
              <div class="section-title">
                <div>
                  <h3>Adapter 命令</h3>
                  <p class="section-note">来源于已注册 Adapter，仅展示系统可见参数；内部参数不会进入设备能力模型。</p>
                </div>
              </div>
              <div v-if="draft.adapterContract.commands.length > 0" class="editor-card-list command-editor-list">
                <article v-for="(command, commandIndex) in draft.adapterContract.commands" :key="command._key" class="editor-card command-editor-card">
                  <div class="editor-card-head">
                    <div class="editor-card-title">
                      <span class="item-index">{{ commandIndex + 1 }}</span>
                      <strong>{{ command.commandName }}</strong>
                    </div>
                    <el-text type="info">{{ command.description || '暂无说明' }}</el-text>
                  </div>
                  <el-table v-if="visibleCommandParameters(command).length > 0" :data="visibleCommandParameters(command)" border size="small" class="nested-table">
                    <el-table-column prop="paramName" label="参数名" min-width="180" />
                    <el-table-column prop="dataType" label="数据类型" width="140" />
                    <el-table-column prop="description" label="说明" min-width="220">
                      <template #default="{ row }">{{ row.description || '—' }}</template>
                    </el-table-column>
                  </el-table>
                  <div v-else class="compact-empty inline-empty">该命令没有系统可见参数</div>
                </article>
              </div>
              <div v-else class="compact-empty block-empty">当前 Adapter 类别未声明命令</div>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <div>
                  <h3>Adapter 遥测属性</h3>
                  <p class="section-note">属性定义由注册配置提供；模型仅配置业务属性映射。</p>
                </div>
              </div>
              <div v-if="draft.adapterContract.telemetry.adapterAttributes.length === 0" class="compact-empty block-empty">当前 Adapter 类别未声明遥测属性</div>
              <el-table v-else :data="draft.adapterContract.telemetry.adapterAttributes" border size="small">
                <el-table-column prop="name" label="属性字段" min-width="180" />
                <el-table-column prop="dataType" label="数据类型" width="140" />
                <el-table-column prop="description" label="说明" min-width="220">
                  <template #default="{ row }">{{ row.description || '—' }}</template>
                </el-table-column>
              </el-table>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <div>
                  <h3>Adapter 事件</h3>
                  <p class="section-note">命令事件用于执行生命周期，功能事件用于设备业务状态转移。</p>
                </div>
              </div>
              <el-table :data="draft.adapterContract.events" border size="small">
                <el-table-column label="事件域" width="120">
                  <template #default="{ row }"><el-tag size="small" effect="plain" :type="row.eventType === 'CMD' ? 'primary' : 'success'">{{ row.eventType }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="eventName" label="事件名" min-width="220" />
                <el-table-column prop="description" label="说明" min-width="260">
                  <template #default="{ row }">{{ row.description || '—' }}</template>
                </el-table-column>
              </el-table>
              <div v-if="draft.adapterContract.events.length === 0" class="compact-empty block-empty">当前 Adapter 类别未声明事件</div>
            </section>
            </div>

          <div id="edit-mapping" class="anchor-section industrial-section">
            <h2 class="section-heading"><span class="section-index">04</span>映射关系</h2>
            <section class="drawer-section">
              <div class="section-title">
                <div class="section-header-copy">
                  <h3>属性映射</h3>
                  <p class="section-note">将模型业务属性与 Adapter 遥测字段逐一对应。</p>
                </div>
                <div class="section-actions"><el-button type="primary" plain size="small" :icon="Plus" @click="addAttributeMapping">新增映射</el-button></div>
              </div>
              <div v-if="draft.adapterContract.telemetry.attributesMapping.length === 0" class="compact-empty block-empty">暂无属性映射，可使用上方按钮添加</div>
              <el-table v-else :data="draft.adapterContract.telemetry.attributesMapping" border size="small" class="editor-table">
                <el-table-column label="模型属性" min-width="220">
                  <template #default="{ row }">
                    <el-select v-model="row.modelAttributeKey" size="small" filterable placeholder="选择模型属性" @change="handleAttributeMappingModelChange(row)">
                      <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" :disabled="isAttributeOptionUsed(attr.key, row)" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="映射" width="72" align="center"><template #default>→</template></el-table-column>
                <el-table-column label="Adapter 属性" min-width="220">
                  <template #default="{ row }">
                    <el-select v-model="row.adapterAttrName" size="small" filterable placeholder="选择 Adapter 属性" @change="handleAdapterAttributeMappingChange(row)">
                      <el-option v-for="attr in adapterAttributeNameOptionsDetailed" :key="attr.name" :label="attr.name + (isAttrDataTypeMatch(row.modelAttributeKey, attr.dataType) ? '' : '（类型不匹配）')" :value="attr.name" :disabled="isAdapterAttributeUsed(attr.name, row) || !isAttrDataTypeMatch(row.modelAttributeKey, attr.dataType)" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="64" fixed="right" align="center">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" title="删除映射" @click="removeRow(draft.adapterContract.telemetry.attributesMapping, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <div class="section-header-copy">
                  <h3>操作映射</h3>
                  <p class="section-note">将模型操作及其参数映射到 Adapter 命令的系统可见参数。</p>
                </div>
                <div class="section-actions"><el-button type="primary" plain size="small" :icon="Plus" @click="addFunctionMapping">新增映射</el-button></div>
              </div>
              <div v-if="draft.functionMappings.length === 0" class="compact-empty block-empty">暂无操作映射，可使用上方按钮添加</div>
              <div v-else class="function-mapping-card-list">
                <article v-for="(row, rowIndex) in draft.functionMappings" :key="row._key || rowIndex" class="function-mapping-editor-card">
                  <div class="function-map-editor-head">
                    <div class="function-map-selects">
                      <label><span>模型操作</span><el-select v-model="row.capabilityKey" size="small" filterable placeholder="选择模型操作" @change="handleFunctionMappingCapabilityChange(row)"><el-option v-for="capability in capabilitySelectOptions" :key="capability.key" :label="capability.label" :value="capability.key" :disabled="isCapabilityMapped(capability.key, row)" /></el-select></label>
                      <span class="mapping-direction">→</span>
                      <label><span>Adapter 命令</span><el-select v-model="row.adapterCommandName" size="small" filterable clearable placeholder="选择 Adapter 命令" @change="handleFunctionMappingCommandChange(row)"><el-option v-for="cmd in commandNameOptions" :key="cmd" :label="cmd" :value="cmd" /></el-select></label>
                    </div>
                    <el-button link type="danger" :icon="Delete" @click="removeRow(draft.functionMappings, rowIndex)">删除</el-button>
                  </div>
                  <div class="param-map-editor compact-param-editor">
                    <div v-for="(mapping, index) in row.parameterMapping" :key="mapping._key || index" class="param-map-row" :class="{ invalid: isParameterMappingInvalid(row, mapping), fixed: mapping.isFixedValue }">
                      <div class="param-map-field"><span class="param-map-label">功能参数</span><el-select v-model="mapping.capabilityParamKey" size="small" filterable placeholder="选择功能参数" @change="handleCapabilityParameterChange(row, mapping)"><el-option v-for="param in capabilityParameterOptionsDetailedByKey(row.capabilityKey)" :key="param._key" :label="(param.displayName || param.name) + ' · ' + param.dataType + (isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType) ? '' : '（类型不匹配）')" :value="param._key" :disabled="isCapabilityParamMapped(row, param._key, mapping) || !isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType)" /></el-select></div>
                      <div class="param-map-field"><span class="param-map-label">Adapter 参数</span><el-select v-model="mapping.commandParamName" size="small" filterable placeholder="选择命令参数" @change="handleParameterCommandChange(row, mapping)"><el-option v-for="param in commandParameterOptionsDetailed(row.adapterCommandName)" :key="param.paramName" :label="param.paramName + ' · ' + param.dataType" :value="param.paramName" :disabled="isCommandParamMapped(row, param.paramName, mapping)" /></el-select></div>
                      <div class="param-map-mode"><span class="param-map-label">取值方式</span><el-switch v-model="mapping.isFixedValue" size="small" active-text="固定" inactive-text="映射" @change="handleParameterFixedChange(mapping)" /></div>
                      <div v-if="mapping.isFixedValue" class="param-map-field fixed-input-field"><span class="param-map-label">固定值</span><el-input v-model="mapping.fixedValue" size="small" :placeholder="fixedValuePlaceholder(row, mapping)" /></div>
                      <el-button link type="danger" :icon="Delete" class="param-delete-btn" @click="removeRow(row.parameterMapping, index)" />
                      <span v-if="isParameterMappingInvalid(row, mapping)" class="map-warning">{{ parameterMappingWarning(row, mapping) }}</span>
                    </div>
                    <div class="param-map-toolbar"><span v-if="row.parameterMapping.length === 0" class="no-mapping-placeholder">未配置参数映射</span><span v-else></span><el-button size="small" type="primary" plain :icon="Plus" class="add-mapping-btn" @click="addParameterMapping(row)">新增参数映射</el-button></div>
                  </div>
                </article>
              </div>
            </section>
          </div>

          <div id="edit-state" class="anchor-section industrial-section">
            <h2 class="section-heading"><span class="section-index">05</span>状态机</h2>
            <!-- 05 状态机 (上下布局：上为执行生命周期与规则表，下为功能状态分区) -->
            <section class="drawer-section state-lifecycle-top-card">
              <div class="section-title">
                <div class="locked-heading">
                  <el-icon><Lock /></el-icon>
                  <h3>执行生命周期</h3>
                  <el-tag size="small" effect="plain" type="info">系统内置规范</el-tag>
                </div>
              </div>
              <div class="lifecycle-flow-chain-bar">
                <div class="chain-item"><span class="chain-label">初始状态</span><el-tag size="small" type="success" effect="light">IDLE</el-tag></div>
                <div class="chain-sep">|</div>
                <div class="chain-item"><span class="chain-label">主主推移链</span><div class="chain-pills"><el-tag size="small" effect="plain" type="info">IDLE</el-tag><el-icon><Right /></el-icon><el-tag size="small" effect="plain" type="primary">SENT</el-tag><el-icon><Right /></el-icon><el-tag size="small" effect="plain" type="primary">RECEIVED</el-tag><el-icon><Right /></el-icon><el-tag size="small" effect="plain" type="primary">RUNNING</el-tag><el-icon><Right /></el-icon><el-tag size="small" effect="light" type="success">COMPLETED</el-tag></div></div>
                <div class="chain-sep">|</div>
                <div class="chain-item"><span class="chain-label">分支终态</span><div class="chain-pills"><el-tag size="small" effect="light" type="danger">FAILED</el-tag><span class="slash">/</span><el-tag size="small" effect="plain" type="warning">ABORTING</el-tag><el-icon><Right /></el-icon><el-tag size="small" effect="light" type="warning">ABORTED</el-tag></div></div>
              </div>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <div class="section-header-copy">
                  <h3>执行生命周期转移规则</h3>
                  <p class="section-note">描述指令在设备上的全生命周期机制。包含系统内置的自动发信/中止下发规则，以及可绑定适配器的阶段推进事件。</p>
                </div>
              </div>
              <el-table :data="mergedLifecycleRules" border size="small" class="stacked-lifecycle-table" style="width: 100%;">
                <el-table-column label="阶段流转规则" width="220" align="center">
                  <template #default="{ row }">
                    <div class="stacked-cell align-center">
                      <div class="cell-line-upper">
                        <span class="flow-label-sub">原状态:</span>
                        <el-tag size="small" type="info" effect="plain" class="state-pill">{{ row.fromStateNames ? row.fromStateNames.join(' / ') : row.fromStateName }}</el-tag>
                      </div>
                      <div class="cell-line-lower">
                        <span class="flow-label-sub">目标:</span>
                        <el-tag size="small" :type="row.toStateName === 'COMPLETED' ? 'success' : row.toStateName === 'ABORTED' ? 'warning' : row.toStateName === 'FAILED' ? 'danger' : 'primary'" effect="light" class="state-pill">{{ row.toStateName || '保持原状态' }}</el-tag>
                      </div>
                    </div>
                  </template>
                </el-table-column>

                <el-table-column label="说明" min-width="240">
                  <template #default="{ row }">
                    <div class="stacked-cell">
                      <div class="cell-line-upper">
                        <el-tag size="small" effect="light" type="info" class="status-badge" v-if="row.type === 'system'">系统固定规则</el-tag>
                        <el-tag size="small" effect="light" :type="row.kind === 'FAILURE' ? 'danger' : row.kind === 'TERMINATION' ? 'warning' : 'primary'" class="status-badge" v-else>
                          {{ row.kind === 'FAILURE' ? '失败分支规则' : row.kind === 'TERMINATION' ? '终止分支规则' : '正常推进阶段' }}
                        </el-tag>
                      </div>
                      <div class="cell-line-lower desc-text">{{ row.description }}</div>
                    </div>
                  </template>
                </el-table-column>

                <el-table-column label="触发条件与事件绑定" min-width="320">
                  <template #default="{ row }">
                    <div class="stacked-cell">
                      <div v-if="row.type === 'system'" class="system-triggers-list">
                        <div v-for="(trig, tIdx) in row.triggers" :key="tIdx" class="trigger-chip-item">
                          <span class="trig-label">{{ trig.label }}:</span>
                          <span class="locked-action-badge compact"><el-icon><Lock /></el-icon>{{ trig.interfaceName }}</span>
                          <span class="signal-tag-bold"><el-icon><Discount /></el-icon>{{ trig.signalName }}</span>
                        </div>
                      </div>
                      <div v-else class="adapter-trigger-binding">
                        <div class="cell-line-upper">
                          <span class="locked-action-badge compact"><el-icon><Lock /></el-icon>{{ adapterInterfaceName() }}</span>
                          <span class="binding-tip-text" v-if="row.kind === 'FAILURE' || row.kind === 'TERMINATION'">(可选绑定适配器事件)</span>
                        </div>
                        <div class="cell-line-lower">
                          <div class="binding-select-wrapper">
                            <el-select v-model="executionLifecycleBindings[row.key]" clearable filterable size="small" style="width: 100%;" :placeholder="row.kind === 'FAILURE' || row.kind === 'TERMINATION' ? '可选绑定事件（留空则不启用该分支）' : row.triggerPolicy === 'REQUIRED' ? '请选择完成事件' : '可选绑定事件（留空则自动推进下个阶段）'">
                              <el-option v-for="eventName in adapterCmdEventOptions" :key="eventName" :label="eventName" :value="eventName" />
                            </el-select>
                          </div>
                        </div>
                      </div>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <!-- 下方：功能状态 (OP State Regions 全宽独占) -->
            <section class="drawer-section">
              <div class="section-title">
                <div class="section-header-copy">
                  <h3>功能状态</h3>
                  <p class="section-note">描述设备并行的业务维度（Regions），可自定义多个分区。</p>
                </div>
                <div class="section-actions"><el-button type="primary" plain size="small" :icon="Plus" @click="addOpStateRegion">新增分区</el-button></div>
              </div>
              
              <div v-for="(region, rIndex) in draft.opState.regions" :key="region._key || rIndex" class="region-block" style="border: 1px solid var(--el-border-color-light); border-radius: 4px; padding: 12px; margin-bottom: 12px;">
                <div class="region-header" style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
                  <el-input v-model="region.regionName" size="small" placeholder="分区名称" style="width: 200px;" />
                  <el-button link type="danger" :icon="Delete" @click="removeOpStateRegion(rIndex)" />
                </div>
                <div class="state-summary-row">
                  <span class="state-summary-label">初始状态</span>
                  <div class="state-input-with-warning">
                    <el-select v-model="region.initialStateName" filterable allow-create size="small" class="state-inline-select">
                      <el-option v-for="name in getRegionStateOptions(region)" :key="name" :label="name" :value="name" />
                    </el-select>
                    <span class="warning-slot"><el-tooltip v-if="isRegionInitialStateInvalid(region)" content="该状态不存在" placement="top"><el-icon class="inline-warning-icon"><Warning /></el-icon></el-tooltip></span>
                  </div>
                </div>
                <div class="state-summary-row align-top">
                  <span class="state-summary-label">状态列表</span>
                  <div class="state-token-list">
                    <el-tag v-for="(state, $index) in region.states" :key="state._key || $index" size="small" closable @close="removeOpState(region, $index)" class="state-token filled closable-state-token">
                      <span class="state-token-text">{{ state.stateName || '未命名' }}</span>
                      <span class="state-token-warning-slot"><el-tooltip v-if="stateUsageWarning(state.stateName)" :content="stateUsageWarning(state.stateName)" placement="top"><el-icon class="state-warning-icon"><Warning /></el-icon></el-tooltip></span>
                    </el-tag>
                    <el-input v-if="opStateInputVisibleMap[region._key]" :ref="el => setOpStateInputRef(el, region._key)" v-model="opStateInputValueMap[region._key]" size="small" class="state-name-input" @keyup.enter="handleOpStateInputConfirm(region)" @blur="handleOpStateInputConfirm(region)" />
                    <el-button v-else size="small" plain class="compact-action-btn" @click="showOpStateInput(region)">新增状态</el-button>
                  </div>
                </div>
              </div>
            </section>

            <section class="drawer-section">
              <div class="section-title"><div class="section-header-copy"><h3>功能状态转移规则</h3><p class="section-note">只使用 Adapter 功能事件（OP）触发设备业务状态变化。</p></div><div class="section-actions"><el-button type="primary" plain size="small" :icon="Plus" :disabled="adapterOpEventOptions.length === 0" @click="addStateTransition">新增规则</el-button></div></div>
              <div v-if="stateMachineWarningMessages.length" class="state-warning-panel"><div v-for="message in stateMachineWarningMessages" :key="message" class="state-warning-item"><el-icon class="inline-warning-icon"><Warning /></el-icon><span>{{ message }}</span></div></div>
              <div v-if="operationTransitionRows.length === 0" class="compact-empty block-empty">暂无功能状态转移规则，可使用上方按钮添加</div>
              <el-table v-else :data="operationTransitionRows" border size="small" class="transition-table editor-table operation-transition-table">
                <el-table-column label="说明" min-width="120"><template #default="{ row }"><div class="field-with-warning"><span class="warning-slot"><el-tooltip v-if="transitionWarning(row)" :content="transitionWarning(row)" placement="top"><el-icon class="inline-warning-icon"><Warning /></el-icon></el-tooltip></span><el-input v-model="row.description" size="small" placeholder="可选" /></div></template></el-table-column>
                <el-table-column label="所属分区" min-width="120"><template #default="{ row }"><el-select v-model="row.regionName" size="small"><el-option v-for="region in draft.opState.regions" :key="region._key" :label="region.regionName" :value="region.regionName" /></el-select></template></el-table-column>
                <el-table-column label="状态流转" min-width="300"><template #default="{ row }"><div class="transition-state-pair"><state-select v-model="row.fromStateName" :options="getRegionStateOptionsByName(row.regionName)" /><el-icon><Right /></el-icon><state-select v-model="row.toStateName" :options="getRegionStateOptionsByName(row.regionName)" /></div></template></el-table-column>
                <el-table-column label="触发条件" min-width="240"><template #default="{ row }"><div class="transition-trigger-editor"><span class="locked-action"><el-icon><Lock /></el-icon>{{ adapterInterfaceName() }}</span><state-select v-model="row.trigger.signalName" :options="adapterOpEventOptions" /></div></template></el-table-column>
                <el-table-column label="转移动作" min-width="280"><template #default="{ row }"><div class="transition-action-list"><div v-for="(act, aIdx) in row.actions" :key="aIdx" class="transition-action-row"><span class="action-editor-label">发送</span><el-select v-model="act.payload.signalName" size="small" placeholder="选择信号"><el-option v-for="sig in getSignalsForInterface(act.payload.interfaceName)" :key="sig" :label="sig" :value="sig" /></el-select><el-button link type="info" :icon="Close" title="移除动作" @click="row.actions.splice(aIdx, 1)" /></div><el-button size="small" plain :icon="Plus" class="compact-action-btn" @click="ensureTransitionAction(row)">添加动作</el-button></div></template></el-table-column>
                <el-table-column label="操作" width="64" fixed="right" align="center"><template #default="{ row }"><el-button link type="danger" :icon="Delete" title="删除规则" @click="removeObjectRow(draft.stateTransitions, row)" /></template></el-table-column>
              </el-table>
            </section>


          </div>

          <div id="edit-constraint" class="anchor-section industrial-section">
            <h2 class="section-heading"><span class="section-index">06</span>内置约束</h2>
            <section class="drawer-section">
              <div class="section-title">
                <div class="section-header-copy">
                  <h3>内置约束</h3>
                  <p class="section-note">设备自动监测的参数限制。配置此处的违规状态会被引擎识别为异常状态跳转规则并内部闭环触发，无需在功能状态转移中重复配置。</p>
                </div>
                <div class="section-actions">
                  <el-button type="primary" plain size="small" :icon="Plus" @click="addIntrinsicConstraint">新增约束</el-button>
                </div>
              </div>
              <div v-if="draft.intrinsicConstraints.length === 0" class="compact-empty block-empty">暂无内置约束，可使用上方按钮添加</div>
              <el-table v-else :data="draft.intrinsicConstraints" border size="small" class="editor-table constraint-table">
                <el-table-column label="约束属性" min-width="170">
                  <template #default="{ row }">
                    <el-select v-model="row.objectAttributeKey" size="small" filterable>
                      <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="比较符" width="100" align="center">
                  <template #default="{ row }"><el-select v-model="row.operator" size="small"><el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" /></el-select></template>
                </el-table-column>
                <el-table-column label="阈值" width="120" align="center">
                  <template #default="{ row }"><el-input v-model="row.boundaryValue" size="small" /></template>
                </el-table-column>
                <el-table-column label="违规状态" min-width="160" align="center">
                  <template #default="{ row }"><state-select v-model="row.violationStateName" :options="exceptionOpStateNameOptions" /></template>
                </el-table-column>
                <el-table-column label="操作" width="64" fixed="right" align="center">
                  <template #default="{ $index }"><el-button link type="danger" :icon="Delete" @click="removeRow(draft.intrinsicConstraints, $index)" /></template>
                </el-table-column>
              </el-table>
            </section>
          </div>

          <div id="edit-bom" class="anchor-section industrial-section">
            <h2 class="section-heading"><span class="section-index">07</span>组件结构清单</h2>
            <section class="drawer-section">
              <div class="section-title">
                <h3>BOM 清单</h3>
                <el-button type="primary" plain size="small" :icon="Plus" @click="addBomComponent">新增组件</el-button>
              </div>
              <div v-if="draft.componentsBom.length === 0" class="compact-empty block-empty">暂无组件，可使用上方按钮添加</div>
              <el-table v-else :data="draft.componentsBom" border size="small" class="editor-table bom-table">
                <el-table-column label="组件名称" min-width="160">
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
                <el-table-column label="数量" width="120">
                  <template #default="{ row }"><el-input-number v-model="row.quantity" size="small" :min="1" style="width: 100%" /></template>
                </el-table-column>
                <el-table-column label="说明" min-width="200">
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
</template>

<script setup>
import { ref, reactive, computed, watch, defineComponent, nextTick, resolveComponent, h, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Warning, Lock, Right, Close, Refresh, Discount, Promotion } from '@element-plus/icons-vue'
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
  adapterCategoryKey,
  adapterCategoryLabel,
  attributeDataTypes,
  adapterDataTypes,
  operators,
  ensureProtocolMetadataLoaded,
  defaultStateSpace,
  defaultAdapterContract,
  defaultInterfaces,
  defaultCommandLifecycleTransitions,
  executionLifecycleMainPath,
  defaultCommandLifecycle,
  getSignalsForInterface,
  defaultStateEntryActions,
  adapterInterfaceName
} from './deviceModelConstants'
import { buildLifecycleRuleRows, serializeLifecycleTransitions, hydrateLifecycleBindings } from './deviceModelLifecycle.js'
import {
  asArray, firstDefined, deepClone, ensureArrayField, isNumeric,
  numericOrNull, stringValue, stringId, normalizeDataType, makeUiKey,
  findKeyByName, lookupName, reserveIdentifier, materializedName,
  eventTypeLabel, operatorLabel, valueKindLabel, directionLabel,
  formatTime, formatJson, dataTypeOptions, describeAction,
  capabilityParamDisplayName, functionMappingGroups, capabilityMappingRows,
  displayAttributeName, normalizeCommandParameter, isAdapterInternalParameter,
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

// ── Adapter 注册相关 ──────────────────────────────────
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
onMounted(() => {
  ensureProtocolMetadataLoaded().catch(error => console.warn('[Drawer] 状态机元数据预加载失败，将在打开抽屉时重试', error))
})

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
const executionLifecycleBindings = reactive({})

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

async function openForCreate(categoryId) {
  try {
    await ensureProtocolMetadataLoaded()
    drawerMode.value = 'create'
    replaceDraft(emptyDraft())
    if (categoryId) {
      draft.basic.categoryValue = String(categoryId)
    }
    loadPropertyTypesForTemplate()
    fetchRegisteredAdapters() // 自动加载已注册的 Adapter 列表，免去手动刷新
    drawerVisible.value = true
  } catch (err) {
    drawerVisible.value = false
    ElMessage.error('加载状态机模型元数据失败：' + (err?.message || '未知错误'))
    console.error('[Drawer] Failed in openForCreate:', err)
  }
}

async function openForEdit(model, defaultAttrs) {
  try {
    await ensureProtocolMetadataLoaded()
    drawerMode.value = 'edit'
    // 立刻打开抽屉，让滑入动画即时开始，不被任何计算阻塞
    drawerVisible.value = true

    // 等 Vue 完成本轮渲染帧后，再执行重数据填充，确保不阻塞动画
    await nextTick()

    replaceDraft(fromModelToDraft(model, defaultAttrs))
    loadPropertyTypesForTemplate()
    prepareAdapterSourcePicker(draft.adapterContract?.config?.adapterName, draft.adapterContract?.config?.categoryName)

    // 纯净的异步兜底逻辑：仅在缓存为空且有模型ID时启动静默网络抓取
    if ((!defaultAttrs || defaultAttrs.length === 0) && model?.modelId) {
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
    drawerVisible.value = false
    ElMessage.error('打开设备模型失败：' + (err?.message || '未知错误'))
    console.error('[Drawer] Failed in openForEdit:', err)
  }
}

defineExpose({ openForCreate, openForEdit, buildSavePayload, fromModelToDraft, replaceDraft, loadPropertyTypesForTemplate })

// ─── Extracted editor logic ──────────────────────────
function addBomComponent() {
  draft.componentsBom.push({ _key: makeUiKey('bom'), slotName: '', categoryId: '', quantity: 1, description: '' })
}
const adapterCmdEvents = computed(() => asArray(draft.adapterContract.events).filter(event => event.eventType === 'CMD'))
const adapterOpEvents = computed(() => asArray(draft.adapterContract.events).filter(event => event.eventType === 'OP'))
const adapterCmdEventOptions = computed(() => adapterEventNames(adapterCmdEvents.value))
const adapterOpEventOptions = computed(() => adapterEventNames(adapterOpEvents.value))
watch(adapterCmdEventOptions, eventNames => {
  Object.keys(executionLifecycleBindings).forEach(key => {
    if (executionLifecycleBindings[key] && !eventNames.includes(executionLifecycleBindings[key])) {
      executionLifecycleBindings[key] = ''
    }
  })
})
const adapterSignalOptions = computed(() => uniqueStrings([...adapterCmdEventOptions.value, ...adapterOpEventOptions.value]))
const generatedInterfaces = computed(() => defaultInterfaces(adapterSignalOptions.value))

const stateMachineInterfaceRows = computed(() => interfaceLocked.value ? generatedInterfaces.value : draft.stateMachineInterfaces)
const commandLifecycleTransitionRows = computed(() => defaultCommandLifecycleTransitions())
const groupedCommandLifecycleTransitions = computed(() => {
  const rules = commandLifecycleTransitionRows.value
  const groups = []
  
  // 1. 合并 IDLE -> SENT
  const startRules = rules.filter(r => r.fromStateName === 'IDLE' && r.toStateName === 'SENT')
  if (startRules.length > 0) {
    groups.push({
      key: 'sys_start',
      fromStateName: 'IDLE',
      toStateName: 'SENT',
      description: '下发指令并启动 (自动向适配器发送报文)',
      triggers: startRules.map(r => ({
        interfaceName: r.trigger?.interfaceName,
        signalName: r.trigger?.signalName,
        label: r.trigger?.signalName === 'WF_EXECUTE_START' ? '工作流触发' : '控制台手动下发'
      })),
      actions: startRules[0].actions
    })
  }

  // 2. 合并 RUNNING -> ABORTING
  const abortRules = rules.filter(r => r.actions?.[0]?.payload?.signalName === 'CMD_ABORT')
  if (abortRules.length > 0) {
    groups.push({
      key: 'sys_abort',
      fromStateName: 'RUNNING',
      toStateName: 'ABORTING',
      description: '下发指令中止请求 (自动向适配器发送 ABORT 报文)',
      triggers: abortRules.map(r => ({
        interfaceName: r.trigger?.interfaceName,
        signalName: r.trigger?.signalName,
        label: r.trigger?.signalName === 'WF_EXECUTE_ABORT' ? '工作流请求中止' : r.trigger?.signalName === 'MANUAL_EXECUTE_ABORT' ? '控制台手动中止' : '约束违规中止'
      })),
      actions: abortRules[0].actions
    })
  }

  return groups
})
const executionLifecycleRuleRows = computed(() => buildLifecycleRuleRows(executionLifecycleMainPath))
const mergedLifecycleRules = computed(() => {
  const sysRules = groupedCommandLifecycleTransitions.value.map(r => ({ ...r, type: 'system' }))
  const bindRules = executionLifecycleRuleRows.value.map(r => {
    let desc = ''
    if (r.kind === 'FAILURE') {
      desc = '适配器异常失败 (可选绑定失败上报)'
    } else if (r.kind === 'TERMINATION') {
      desc = '适配器中止确认 (可选绑定中止确认)'
    } else {
      if (r.fromStateName === 'SENT' && r.toStateName === 'RECEIVED') desc = '适配器确认接收 (消息自动推移)'
      else if (r.fromStateName === 'RECEIVED' && r.toStateName === 'RUNNING') desc = '适配器确认运行 (运行自动推移)'
      else if (r.fromStateName === 'RUNNING' && r.toStateName === 'COMPLETED') desc = '适配器完成执行 (需绑定完成事件)'
      else desc = '适配器回传执行进度'
    }
    return { ...r, type: 'binding', description: desc }
  })
  return [...sysRules, ...bindRules]
})
const operationTransitionRows = computed(() => draft.stateTransitions.filter(row => row.stateSpace === 'OP'))
const commandStateNameOptions = computed(() => draft.cmdState.states.map(item => item.stateName).filter(Boolean))
const opStateNameOptions = computed(() => {
  return (draft.opState.regions || []).flatMap(r => r.states || []).map(item => item.stateName).filter(Boolean)
})

const exceptionOpStateNameOptions = computed(() => {
  return (draft.opState.regions || [])
    .filter(r => r.regionName === 'Exception')
    .flatMap(r => r.states || [])
    .map(item => item.stateName)
    .filter(Boolean)
})

function getRegionStateOptions(region) {
  return (region.states || []).map(item => item.stateName).filter(Boolean)
}

function getRegionStateOptionsByName(regionName) {
  const region = (draft.opState.regions || []).find(r => r.regionName === regionName)
  return region ? getRegionStateOptions(region) : []
}

function isRegionInitialStateInvalid(region) {
  return !!region.initialStateName && !getRegionStateOptions(region).includes(region.initialStateName)
}

function stateUsageWarning(stateName) {
  if (!stateName) return '状态名未填写'
  const used = operationTransitionRows.value.some(t => t.fromStateName === stateName || t.toStateName === stateName)
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
  if (operationTransitionRows.value.length > 0) {
    (draft.opState.regions || []).forEach(region => {
      (region.states || []).forEach(state => {
        const warning = stateUsageWarning(state.stateName)
        if (warning) messages.push('分区 ' + (region.regionName || '未命名') + ' 的状态 ' + (state.stateName || '未命名') + '：' + warning)
      })
    })
  }
  operationTransitionRows.value.forEach((row, index) => {
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
  if (type === 'OP') {
    return {
      regions: (norm.regions || []).map(r => ({
        regionName: stringValue(r.regionName),
        initialStateName: stringValue(r.initialStateName),
        states: (r.states || []).map(s => {
          const stateName = stringValue(s.stateName)
          const onEntry = s.onEntry.map(a => ({ actionName: stringValue(a.actionName), payload: normalizePayload(a.payload || a.parameters) })).filter(a => a.actionName)
          return {
            stateName,
            onEntry: onEntry.length ? onEntry : defaultStateEntryActions(type, stateName)
          }
        }).filter(s => s.stateName)
      })).filter(r => r.regionName)
    }
  }

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
    stateSpace: String(r.stateSpace || '').toUpperCase(),
    regionName: String(r.stateSpace || '').toUpperCase() === 'OP' ? stringValue(r.regionName) : undefined,
    description: stringValue(r.description),
    fromStateName: stringValue(r.fromStateName),
    toStateName: stringValue(r.toStateName),
    trigger: r.trigger == null ? null : {
      interfaceName: stringValue(r.trigger.interfaceName || adapterInterfaceName()),
      signalName: stringValue(r.trigger.signalName)
    },
    actions: asArray(r.actions).map(a => ({ actionName: stringValue(a.actionName), payload: normalizePayload(a.payload || a.parameters) })).filter(a => a.actionName)
  })).filter(r => ['CMD', 'OP'].includes(r.stateSpace)
    && r.fromStateName
    && r.toStateName
    && (r.trigger == null || (r.trigger.interfaceName && r.trigger.signalName)))
}

function buildExecutionLifecycleTransitions() {
  if (executionLifecycleMainPath.length === 0) {
    throw new Error('状态机执行生命周期元数据尚未加载')
  }
  return serializeLifecycleTransitions(
    executionLifecycleRuleRows.value,
    executionLifecycleBindings,
    adapterInterfaceName()
  )
}

function hydrateExecutionLifecycleBindings(rows) {
  Object.keys(executionLifecycleBindings).forEach(key => { delete executionLifecycleBindings[key] })
  Object.assign(executionLifecycleBindings, hydrateLifecycleBindings(rows, executionLifecycleRuleRows.value))
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
    stateTransitions: [...buildExecutionLifecycleTransitions(), ...cleanTransitions(operationTransitionRows.value)],
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
  const normalizedTransitions = normalizeTransitions(next.stateTransitions)
  hydrateExecutionLifecycleBindings(normalizedTransitions)
  draft.stateTransitions.splice(0, draft.stateTransitions.length, ...normalizedTransitions.filter(row => row.stateSpace === 'OP'))
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
  return false
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
  return asArray(command?.commandParameters).filter(param => !isAdapterInternalParameter(param))
}

function addIntrinsicConstraint() {
  draft.intrinsicConstraints.push({ _key: makeUiKey('constraint'), objectAttributeKey: '', objectAttributeName: '', operator: '>', boundaryValue: '', violationStateName: '' })
}

function removeRow(rows, index) { rows.splice(index, 1) }

const opStateInputVisibleMap = reactive({})
const opStateInputValueMap = reactive({})
const opStateInputRefs = reactive({})

function setOpStateInputRef(el, key) {
  if (el) {
    opStateInputRefs[key] = el
  }
}

function addOpStateRegion() {
  const newKey = makeUiKey('region')
  if (!draft.opState.regions) {
    draft.opState.regions = []
  }
  draft.opState.regions.push({
    _key: newKey,
    regionName: '新建分区',
    initialStateName: 'IDLE',
    states: [{ _key: makeUiKey('state'), stateName: 'IDLE', onEntry: [] }]
  })
}

function removeOpStateRegion(index) {
  draft.opState.regions.splice(index, 1)
}

function showOpStateInput(region) {
  const key = region._key
  opStateInputVisibleMap[key] = true
  nextTick(() => {
    opStateInputRefs[key]?.focus()
  })
}

function handleOpStateInputConfirm(region) {
  const key = region._key
  const value = opStateInputValueMap[key]
  if (value) {
    const stateName = value.trim()
    if (!region.states) {
      region.states = []
    }
    if (stateName && !region.states.find(s => s.stateName === stateName)) {
      region.states.push({
        _key: makeUiKey('state'),
        stateName: stateName,
        onEntry: defaultStateEntryActions('OP', stateName)
      })
    }
  }
  opStateInputVisibleMap[key] = false
  opStateInputValueMap[key] = ''
}

function removeOpState(region, index) {
  region.states.splice(index, 1)
}

function removeObjectRow(rows, row) { const index = rows.indexOf(row); if (index >= 0) rows.splice(index, 1) }

function addStateTransition() {
  const firstRegion = draft.opState.regions?.[0]?.regionName || 'Main'
  draft.stateTransitions.push({
    _key: makeUiKey('transition'),
    stateSpace: 'OP',
    regionName: firstRegion,
    description: '',
    fromStateName: '',
    toStateName: '',
    trigger: { interfaceName: adapterInterfaceName(), signalName: adapterOpEventOptions.value[0] || '' },
    actions: []
  })
}

function ensureTransitionAction(row) {
  if (!row.actions) row.actions = []
  row.actions.push(adapterOutAction(''))
}

function parsedAdapterConfig(adapter) {
  if (!adapter?.parsedConfig) return {}
  if (typeof adapter.parsedConfig === 'string') {
    try { return JSON.parse(adapter.parsedConfig) } catch { return {} }
  }
  return adapter.parsedConfig || {}
}

function prepareAdapterSourcePicker(adapterName = '', categoryName = '') {
  selectedRegisteredAdapterName.value = adapterName || ''
  selectedRegisteredAdapterTemplate.value = categoryName || ''
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
    if (selectedRegisteredAdapterName.value) {
      const options = registeredAdapterTemplateOptions.value
      if (options.length > 0) {
        const hasTemplate = options.some(option => adapterCategoryKey(option) === selectedRegisteredAdapterTemplate.value)
        if (!hasTemplate || !selectedRegisteredAdapterTemplate.value) {
          selectedRegisteredAdapterTemplate.value = adapterCategoryKey(options[0])
        }
      } else {
        selectedRegisteredAdapterTemplate.value = ''
      }
    }
  } catch (error) {
    registeredAdapters.value = []
  } finally {
    registeredAdapterLoading.value = false
  }
}

function handleRegisteredAdapterChange() {
  selectedRegisteredAdapterTemplate.value = ''
  nextTick(() => {
    const options = registeredAdapterTemplateOptions.value
    if (options.length > 0) {
      selectedRegisteredAdapterTemplate.value = adapterCategoryKey(options[0])
    }
  })
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
function adapterEventNames(events) {
  const rows = Array.isArray(events)
    ? events
    : [...asArray(events?.cmdEvents), ...asArray(events?.opEvents)]
  return rows.map(event => event.eventName || event.name).filter(Boolean)
}



function signalOptionsForInterface(interfaceName) {
  const iface = stateMachineInterfaceRows.value.find(item => item.name === interfaceName)
  return asArray(iface?.allowedSignals).filter(Boolean)
}

function uniqueStrings(values) {
  return [...new Set(asArray(values).map(stringValue).filter(Boolean))]
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
.state-card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 10px; }
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
.drawer-section { margin-top: 12px; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px 16px; background: #ffffff; box-shadow: 0 1px 3px 0 rgba(15, 23, 42, 0.03); transition: all 0.2s ease-in-out; }
.drawer-section:hover { box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.06), 0 2px 4px -2px rgba(15, 23, 42, 0.04); }
.drawer-footer { justify-content: flex-end; display: flex; align-items: center; gap: 8px; }
.model-edit-workbench { display: flex; min-height: 0; overflow: hidden; height: calc(100vh - 120px); }
.edit-scroll-content { flex: 1; min-width: 0; }
.edit-scroll-content :deep(.el-scrollbar__view) { padding: 14px 18px 28px; }
.capability-editor-card, .capability-editor-list, .capability-title-editor { border-left: 3px solid #3b82f6; }
.command-editor-card, .command-editor-list { border-left: 3px solid #10b981; }
.locked-table :deep(.el-table__body-wrapper) { background: #ffffff; }
.locked-action { max-width: 100%; color: #475569; font-size: 12px; line-height: 1.45; display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; white-space: normal; }

/* ── 锚点布局与导航样式 ── */
.detail-anchor-layout { min-height: 0; background: #fff; border-top: 1px solid #e5e7eb; }
.detail-anchor-menu {
  width: 172px;
  flex-shrink: 0;
  padding: 12px 8px;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0 !important;
}
.detail-anchor-menu :deep(.el-anchor__link) {
  margin-bottom: 4px;
  padding: 8px 12px;
  border-radius: 6px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.15s ease;
}
.detail-anchor-menu :deep(.el-anchor__link:hover) {
  background: #f1f5f9;
  color: #0f172a;
}
.detail-anchor-menu :deep(.el-anchor__link.is-active) {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 700;
}
.anchor-section { scroll-margin-top: 8px; }
.anchor-section.industrial-section {
  margin-bottom: 16px;
  padding-top: 4px;
}
.section-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 10px !important;
  padding: 0 0 8px;
  border-bottom: 2px solid #f1f5f9;
  color: #0f172a;
  font-size: 16px;
  font-weight: 800;
}
.section-index {
  display: inline-flex;
  width: 32px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.1);
}
.section-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; min-height: 28px; }
.section-title h3 { margin: 0; font-size: 15px; line-height: 1.35; letter-spacing: 0; }
.section-header-copy { min-width: 0; flex: 1; }
.section-actions { flex: 0 0 auto; display: flex; align-items: center; gap: 8px; margin-left: auto; }
.editor-table { width: 100%; }
.editor-table :deep(.el-table__cell) { padding: 7px 8px; vertical-align: middle; }
.editor-table :deep(.cell) { line-height: 1.45; }
.editor-table :deep(.el-select), .editor-table :deep(.el-input) { width: 100%; }
.operation-transition-table :deep(.el-table__body td) { vertical-align: top; }
.state-name-input { width: 132px; }
.constraint-table, .bom-table { table-layout: fixed; }
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

.event-domain-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.event-domain-panel { min-width: 0; }
.event-domain-title { margin-bottom: 6px; color: #334155; font-size: 13px; font-weight: 700; }
.section-note { margin: 3px 0 0; color: #64748b; font-size: 12px; line-height: 1.4; }
/* ── 上下对齐 (Stacked Table Cells) 专属样式 ── */
.stacked-lifecycle-table {
  margin-top: 12px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.02);
}

.stacked-lifecycle-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: #475569;
  font-weight: 700;
  font-size: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid #e2e8f0;
}

.stacked-lifecycle-table :deep(.el-table__row td) {
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: top;
}

.stacked-lifecycle-table :deep(.el-table__row:hover td) {
  background-color: #f8fafc !important;
}

.stacked-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.stacked-cell.align-center {
  align-items: center;
}

.cell-line-upper {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.cell-line-lower {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-height: 24px;
}

.flow-label-sub {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 600;
  min-width: 42px;
}

.desc-text {
  color: #475569;
  font-size: 12px;
  line-height: 1.4;
  word-break: break-word;
}

.signal-tag-bold {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #0f172a;
  font-weight: 700;
  font-size: 12px;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
}

.binding-select-wrapper {
  width: 100%;
}

.action-tag-bold {
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-target-text {
  color: #047857;
  font-size: 11px;
  font-weight: 500;
}

/* ── 全局根治 Element Plus 表格对不齐 物理硬性修正样式 ── */
:deep(.el-table) {
  table-layout: fixed !important;
  width: 100% !important;
}
:deep(.el-table__header),
:deep(.el-table__body) {
  width: 100% !important;
  table-layout: fixed !important;
}
:deep(.el-table table) {
  width: 100% !important;
  table-layout: fixed !important;
}

/* ── 05 状态机顶部紧凑生命周期链条 ── */
.state-lifecycle-top-card {
  margin-bottom: 12px;
}

.lifecycle-flow-chain-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 14px;
}

.chain-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chain-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.chain-sep {
  color: #cbd5e1;
  font-weight: 300;
}

.chain-pills {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.chain-pills .slash {
  color: #94a3b8;
  font-size: 11px;
}

.system-triggers-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.trigger-chip-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.trig-label {
  color: #475569;
  font-weight: 600;
  font-size: 11px;
  min-width: 90px;
}

.adapter-trigger-binding {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.binding-tip-text {
  color: #94a3b8;
  font-size: 11px;
  font-style: italic;
}

.no-action-tip {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

.no-action-sub {
  color: #94a3b8;
  font-size: 11px;
  font-style: italic;
}

.pipeline-card-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03);
  transition: all 0.2s ease-in-out;
}

.pipeline-card-item:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 12px -2px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.pipeline-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  gap: 12px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pipeline-flow-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #ffffff;
  padding: 3px 10px;
  border-radius: 6px;
  border: 1px solid #cbd5e1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  font-weight: 700;
}

.state-chip { border-radius: 4px; padding: 1px 6px; }
.state-chip.from { color: #2563eb; background: #eff6ff; }
.state-chip.to { color: #059669; background: #ecfdf5; }
.state-chip.to.aborted { color: #d97706; background: #fffbeb; }
.state-chip.to.failed { color: #dc2626; background: #fef2f2; }
.state-chip.muted { color: #64748b; background: #f1f5f9; }
.state-chip.warning { color: #d97706; background: #fffbeb; }
.flow-icon { color: #94a3b8; font-size: 12px; font-weight: bold; }

.kind-tag { font-weight: 600; }

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.system-action-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #047857;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(16, 185, 129, 0.05);
}

.act-icon { font-size: 13px; }
.act-sig { font-weight: 800; color: #065f46; }
.act-arrow { color: #10b981; margin: 0 2px; }
.act-iface { color: #047857; opacity: 0.85; font-size: 11px; }

.no-action-badge {
  color: #94a3b8;
  font-size: 12px;
  font-style: italic;
  padding: 3px 8px;
  background: #ffffff;
  border-radius: 4px;
  border: 1px solid #f1f5f9;
}

.pipeline-card-body {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.body-desc {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.45;
}

.info-icon { color: #3b82f6; font-size: 14px; flex-shrink: 0; }

.body-trigger-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #f1f5f9;
  flex-wrap: wrap;
}

.trigger-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  flex-shrink: 0;
}

.system-trigger-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 6px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  font-size: 12px;
}

.system-trigger-pill .sep { color: #cbd5e1; }

.adapter-trigger-editor {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 280px;
  flex-wrap: wrap;
}

.iface-lock {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  color: #64748b;
  font-size: 12px;
  flex-shrink: 0;
}

.trigger-select-input {
  flex: 1;
  min-width: 240px;
}

@media (max-width: 1100px) {
  .registered-adapter-picker { grid-template-columns: 1fr 1fr; }
  .function-map-selects, .param-map-row, .param-map-row.fixed { grid-template-columns: 1fr; }
  .mapping-direction { display: none; }
  .state-card-grid, .model-json-grid { grid-template-columns: 1fr; }
}
@media (max-width: 900px) {
  .event-domain-grid { grid-template-columns: 1fr; }
  .section-title { align-items: stretch; }
  .section-actions { width: 100%; margin-left: 0; }
}</style>
