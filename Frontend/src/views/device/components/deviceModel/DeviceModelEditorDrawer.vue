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
            <div id="edit-basic" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">01</span>
                  <span class="sec-title-text">基础信息</span>
                  <span class="sec-desc-text">定义设备模型的基本标识、显示名称与所属分类</span>
                </div>
              </div>
              <div class="section-card-body padded">
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
              </div>
            </div>

            <div id="edit-ability" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">02</span>
                  <span class="sec-title-text">设备属性定义</span>
                  <span class="sec-desc-text">定义设备对外暴露的遥测监测指标与状态参数</span>
                </div>
                <button class="btn-aliyun-cta" type="button" @click="addAttribute">
                  <el-icon><Plus /></el-icon><span>新增属性</span>
                </button>
              </div>
              <div class="section-card-body">
                <div v-if="draft.attributes.length === 0" class="compact-empty block-empty">暂无设备属性，请点击右上角“新增属性”进行配置</div>
                <div v-else class="grid-table-container">
                  <div class="grid-table-header attr-grid-cols">
                    <span class="grid-th">属性名称</span>
                    <span class="grid-th">取值类型</span>
                    <span class="grid-th">数据类型</span>
                    <span class="grid-th">单位</span>
                    <span class="grid-th col-center">操作</span>
                  </div>
                  <div v-for="(row, $index) in draft.attributes" :key="row._key || $index" class="grid-table-row attr-grid-cols">
                    <div class="grid-td"><el-input v-model="row.displayName" size="small" placeholder="例如：当前温度" /></div>
                    <div class="grid-td">
                      <el-select v-model="row.valueKind" size="small" style="width: 100%;">
                        <el-option label="连续值" value="CONTINUOUS" />
                        <el-option label="离散值" value="DISCRETE" />
                      </el-select>
                    </div>
                    <div class="grid-td"><data-type-select v-model="row.dataType" :options="attributeDataTypes" /></div>
                    <div class="grid-td"><el-input v-model="row.unit" size="small" placeholder="℃ / rpm" /></div>
                    <div class="grid-td col-center"><button class="btn-link danger" type="button" @click="removeAttribute($index)">删除</button></div>
                  </div>
                </div>
              </div>
            </div>

            <div class="holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">02</span>
                  <span class="sec-title-text">设备操作</span>
                  <span class="sec-desc-text">设备支持的动作指令及终止能力配置</span>
                </div>
                <button class="btn-aliyun-cta" type="button" @click="addCapability">
                  <el-icon><Plus /></el-icon><span>新增操作</span>
                </button>
              </div>
              <div class="section-card-body padded">
                <div v-if="draft.capabilities.length > 0" class="editor-card-list capability-editor-list">
                  <article v-for="(capability, capIndex) in draft.capabilities" :key="capability._key" class="editor-card capability-editor-card">
                    <div class="editor-card-head">
                      <div class="editor-card-title capability-title-editor">
                        <span class="item-index">{{ capIndex + 1 }}</span>
                        <el-input v-model="capability.displayName" size="small" placeholder="操作名称，例如：加热" />
                      </div>
                      <button class="btn-link danger" type="button" @click="removeCapability(capability)">删除</button>
                    </div>
                    <div class="capability-execution-config">
                      <label class="capability-config-field">
                        <span>操作类型</span>
                        <el-switch v-model="capability.isAbort" size="small" active-text="终止能力" inactive-text="普通操作" @change="handleCapabilityAbortTypeChange(capability)" />
                      </label>
                      <label v-if="!capability.isAbort" class="capability-config-field capability-config-select">
                        <span>终止能力</span>
                        <el-select v-model="capability.abortCapabilityKey" size="small" clearable filterable placeholder="可选：选择终止操作" @change="handleAbortCapabilityChange(capability)">
                          <el-option v-for="option in terminationCapabilityOptions(capability)" :key="option._key" :label="capabilityLabel(option)" :value="option._key" />
                        </el-select>
                      </label>
                      <label v-else class="capability-config-field capability-config-select">
                        <span>影响范围</span>
                        <el-select v-model="capability.scopeCapabilityKeys" size="small" multiple filterable placeholder="选择受影响的普通操作" @change="handleAbortScopeChange(capability)">
                          <el-option v-for="option in normalCapabilityOptions(capability)" :key="option._key" :label="capabilityLabel(option)" :value="option._key" />
                        </el-select>
                      </label>
                    </div>
                    <div class="nested-toolbar">
                      <span>操作参数</span>
                      <button class="btn-aliyun" type="button" style="padding: 2px 8px; font-size: 11px;" @click="addCapabilityParameter(capability)">
                        <el-icon><Plus /></el-icon><span>添加参数</span>
                      </button>
                    </div>
                    <div v-if="capability.parameters.length > 0" class="grid-table-container nested-grid-table">
                      <div class="grid-table-header cap-param-grid-cols">
                        <span class="grid-th">参数名称</span>
                        <span class="grid-th">数据类型</span>
                        <span class="grid-th col-center">操作</span>
                      </div>
                      <div v-for="(row, $index) in capability.parameters" :key="$index" class="grid-table-row cap-param-grid-cols">
                        <div class="grid-td"><el-input v-model="row.displayName" size="small" placeholder="例如：目标温度" /></div>
                        <div class="grid-td"><data-type-select v-model="row.dataType" :options="attributeDataTypes" /></div>
                        <div class="grid-td col-center"><button class="btn-link danger" type="button" @click="removeCapabilityParameter(capability, $index)">删除</button></div>
                      </div>
                    </div>
                    <div v-if="capability.parameters.length === 0" class="compact-empty inline-empty">暂无参数</div>
                  </article>
                </div>
                <div v-else class="compact-empty block-empty">暂无设备操作，请点击右上角“新增操作”进行配置</div>
              </div>
            </div>

            <div class="holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">02</span>
                  <span class="sec-title-text">端口配置</span>
                  <span class="sec-desc-text">定义设备输入与输出物理或逻辑端口</span>
                </div>
                <button class="btn-aliyun-cta" type="button" @click="addPort">
                  <el-icon><Plus /></el-icon><span>新增端口</span>
                </button>
              </div>
              <div class="section-card-body">
                <div v-if="draft.ports.length === 0" class="compact-empty block-empty">暂无端口配置，请点击右上角“新增端口”进行配置</div>
                <div v-else class="grid-table-container">
                  <div class="grid-table-header port-grid-cols">
                    <span class="grid-th">端口名称</span>
                    <span class="grid-th">方向</span>
                    <span class="grid-th">绑定属性</span>
                    <span class="grid-th col-center">操作</span>
                  </div>
                  <div v-for="(row, $index) in draft.ports" :key="$index" class="grid-table-row port-grid-cols">
                    <div class="grid-td"><el-input v-model="row.portName" size="small" placeholder="例如：温度输出口" /></div>
                    <div class="grid-td">
                      <el-select v-model="row.direction" size="small" style="width: 100%;">
                        <el-option label="输入" value="IN" />
                        <el-option label="输出" value="OUT" />
                      </el-select>
                    </div>
                    <div class="grid-td">
                      <el-select v-model="row.bindingAttrKey" size="small" filterable clearable placeholder="选择绑定属性" style="width: 100%;">
                        <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" />
                      </el-select>
                    </div>
                    <div class="grid-td col-center"><button class="btn-link danger" type="button" @click="removeRow(draft.ports, $index)">删除</button></div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 03 Adapter 契约配置（命令清单、遥测属性、事件清单合并在同一卡片中） -->
            <div id="edit-adapter" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">03</span>
                  <span class="sec-title-text">Adapter 契约</span>
                  <span class="sec-desc-text">选择契约驱动来源，包含命令清单、遥测属性与事件清单（Adapter 定义由注册配置提供）</span>
                </div>
                <el-tag size="small" type="info" effect="plain">驱动契约</el-tag>
              </div>
              <div class="section-card-body padded">
                <!-- 1. 契约驱动来源 -->
                <div class="contract-source-box">
                  <el-form label-width="84px" size="small">
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
                        <button class="btn-aliyun-cta" type="button" :disabled="!selectedRegisteredAdapterName || !selectedRegisteredAdapterTemplate" @click="applyRegisteredAdapterContract">
                          <span>载入契约</span>
                        </button>
                        <button class="btn-aliyun" type="button" @click="fetchRegisteredAdapters">
                          <el-icon><Refresh /></el-icon><span>刷新</span>
                        </button>
                      </div>
                    </el-form-item>
                  </el-form>
                </div>

                <!-- 2. Adapter 命令清单 -->
                <div class="contract-sub-section">
                  <div class="contract-sub-header">
                    <span class="sub-header-title">3.1 命令清单</span>
                    <span class="sub-header-count">{{ draft.adapterContract.commands.length }} 项</span>
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
                      <div v-if="visibleCommandParameters(command).length > 0" class="grid-table-container nested-grid-table" style="margin-top: 6px;">
                        <div class="grid-table-header cmd-param-grid-cols">
                          <span class="grid-th">参数名</span>
                          <span class="grid-th">数据类型</span>
                          <span class="grid-th">说明</span>
                        </div>
                        <div v-for="(row, $index) in visibleCommandParameters(command)" :key="$index" class="grid-table-row cmd-param-grid-cols">
                          <div class="grid-td font-mono-text">{{ row.paramName }}</div>
                          <div class="grid-td">{{ row.dataType }}</div>
                          <div class="grid-td desc-sub-text">{{ row.description || '—' }}</div>
                        </div>
                      </div>
                      <div v-else class="compact-empty inline-empty">该命令没有系统可见参数</div>
                    </article>
                  </div>
                  <div v-else class="compact-empty block-empty">当前 Adapter 类别未声明命令</div>
                </div>

                <!-- 3. Adapter 遥测属性 -->
                <div class="contract-sub-section">
                  <div class="contract-sub-header">
                    <span class="sub-header-title">3.2 遥测属性</span>
                    <span class="sub-header-count">{{ draft.adapterContract.telemetry.adapterAttributes.length }} 项</span>
                  </div>
                  <div v-if="draft.adapterContract.telemetry.adapterAttributes.length === 0" class="compact-empty block-empty">当前 Adapter 类别未声明遥测属性</div>
                  <div v-else class="grid-table-container">
                    <div class="grid-table-header telemetry-grid-cols">
                      <span class="grid-th">属性字段</span>
                      <span class="grid-th">数据类型</span>
                      <span class="grid-th">说明</span>
                    </div>
                    <div v-for="(row, $index) in draft.adapterContract.telemetry.adapterAttributes" :key="$index" class="grid-table-row telemetry-grid-cols">
                      <div class="grid-td font-mono-text">{{ row.telemetryName }}</div>
                      <div class="grid-td">{{ row.dataType }}</div>
                      <div class="grid-td desc-sub-text">{{ row.description || '—' }}</div>
                    </div>
                  </div>
                </div>

                <!-- 4. Adapter 事件清单 -->
                <div class="contract-sub-section">
                  <div class="contract-sub-header">
                    <span class="sub-header-title">3.3 事件清单</span>
                    <span class="sub-header-count">{{ draft.adapterContract.events.length }} 项</span>
                  </div>
                  <div v-if="draft.adapterContract.events.length === 0" class="compact-empty block-empty">当前 Adapter 类别未声明事件</div>
                  <div v-else class="grid-table-container">
                    <div class="grid-table-header event-grid-cols">
                      <span class="grid-th">事件域</span>
                      <span class="grid-th">事件名</span>
                      <span class="grid-th">说明</span>
                    </div>
                    <div v-for="(row, $index) in draft.adapterContract.events" :key="$index" class="grid-table-row event-grid-cols">
                      <div class="grid-td"><el-tag size="small" effect="plain" :type="row.eventType === 'CMD' ? 'primary' : 'success'">{{ row.eventType }}</el-tag></div>
                      <div class="grid-td font-mono-text">{{ row.eventName }}</div>
                      <div class="grid-td desc-sub-text">{{ row.description || '—' }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div id="edit-mapping" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">04</span>
                  <span class="sec-title-text">属性映射</span>
                  <span class="sec-desc-text">将模型业务属性与 Adapter 遥测字段逐一对应</span>
                </div>
                <button class="btn-aliyun-cta" type="button" @click="addAttributeMapping">
                  <el-icon><Plus /></el-icon><span>新增映射</span>
                </button>
              </div>
              <div class="section-card-body">
                <div v-if="draft.adapterContract.telemetry.attributesMapping.length === 0" class="compact-empty block-empty">暂无属性映射，可使用上方按钮添加</div>
                <div v-else class="grid-table-container">
                  <div class="grid-table-header attrmap-grid-cols">
                    <span class="grid-th">模型属性</span>
                    <span class="grid-th col-center"></span>
                    <span class="grid-th">Adapter 属性</span>
                    <span class="grid-th col-center">操作</span>
                  </div>
                  <div v-for="(row, $index) in draft.adapterContract.telemetry.attributesMapping" :key="$index" class="grid-table-row attrmap-grid-cols">
                    <div class="grid-td">
                      <el-select v-model="row.modelAttributeKey" size="small" filterable placeholder="选择模型属性" style="width: 100%;" @change="handleAttributeMappingModelChange(row)">
                        <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" :disabled="isAttributeOptionUsed(attr.key, row)" />
                      </el-select>
                    </div>
                    <div class="grid-td col-center map-arrow">→</div>
                    <div class="grid-td">
                      <el-select v-model="row.adapterAttrName" size="small" filterable placeholder="选择 Adapter 属性" style="width: 100%;" @change="handleAdapterAttributeMappingChange(row)">
                        <el-option v-for="attr in adapterAttributeNameOptionsDetailed" :key="attr.telemetryName" :label="attr.telemetryName + (isAttrDataTypeMatch(row.modelAttributeKey, attr.dataType) ? '' : '（类型不匹配）')" :value="attr.telemetryName" :disabled="isAdapterAttributeUsed(attr.telemetryName, row) || !isAttrDataTypeMatch(row.modelAttributeKey, attr.dataType)" />
                      </el-select>
                    </div>
                    <div class="grid-td col-center"><button class="btn-link danger" type="button" @click="removeRow(draft.adapterContract.telemetry.attributesMapping, $index)">删除</button></div>
                  </div>
                </div>
              </div>
            </div>

            <div class="holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">04</span>
                  <span class="sec-title-text">操作映射</span>
                  <span class="sec-desc-text">将模型操作及其参数映射到 Adapter 命令的系统可见参数</span>
                </div>
                <button class="btn-aliyun-cta" type="button" @click="addFunctionMapping">
                  <el-icon><Plus /></el-icon><span>新增映射</span>
                </button>
              </div>
              <div class="section-card-body padded">
                <div v-if="draft.functionMappings.length === 0" class="compact-empty block-empty">暂无操作映射，可使用上方按钮添加</div>
                <div v-else class="function-mapping-card-list">
                  <article v-for="(row, rowIndex) in draft.functionMappings" :key="row._key || rowIndex" class="function-mapping-editor-card">
                    <div class="function-map-editor-head">
                      <div class="function-map-selects">
                        <label><span>模型操作</span><el-select v-model="row.capabilityKey" size="small" filterable placeholder="选择模型操作" @change="handleFunctionMappingCapabilityChange(row)"><el-option v-for="capability in capabilitySelectOptions" :key="capability.key" :label="capability.label" :value="capability.key" :disabled="isCapabilityMapped(capability.key, row)" /></el-select></label>
                        <span class="mapping-direction">→</span>
                        <label><span>Adapter 命令</span><el-select v-model="row.adapterCommandName" size="small" filterable clearable placeholder="选择 Adapter 命令" @change="handleFunctionMappingCommandChange(row)"><el-option v-for="cmd in commandNameOptions" :key="cmd" :label="cmd" :value="cmd" /></el-select></label>
                      </div>
                      <button class="btn-link danger" type="button" @click="removeRow(draft.functionMappings, rowIndex)">删除</button>
                    </div>
                    <div class="nested-toolbar">
                      <span>参数映射关系</span>
                      <button class="btn-aliyun" type="button" style="padding: 2px 8px; font-size: 11px;" @click="addParameterMapping(row)">
                        <el-icon><Plus /></el-icon><span>新增参数映射</span>
                      </button>
                    </div>
                    <div v-if="row.parameterMapping.length === 0" class="compact-empty block-empty">未配置参数映射，可使用上方按钮新增</div>
                    <div v-else class="nested-param-table-card">
                      <div class="nested-param-table-head">
                        <span class="pm-col-cap">功能参数</span>
                        <span class="pm-col-arr"></span>
                        <span class="pm-col-cmd">Adapter 命令参数</span>
                        <span class="pm-col-mode">取值模式</span>
                        <span class="pm-col-act">操作</span>
                      </div>
                      <div v-for="(mapping, index) in row.parameterMapping" :key="mapping._key || index" class="nested-param-table-row" :class="{ invalid: isParameterMappingInvalid(row, mapping) }">
                        <div class="pm-col-cap">
                          <el-select v-model="mapping.capabilityParamKey" size="small" filterable placeholder="选择功能参数" style="width: 100%;" @change="handleCapabilityParameterChange(row, mapping)">
                            <el-option v-for="param in capabilityParameterOptionsDetailedByKey(row.capabilityKey)" :key="param._key" :label="(param.displayName || param.name) + ' · ' + param.dataType + (isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType) ? '' : '（类型不匹配）')" :value="param._key" :disabled="isCapabilityParamMapped(row, param._key, mapping) || !isParamDataTypeMatch(mapping.commandParamName, row.adapterCommandName, param.dataType)" />
                          </el-select>
                        </div>
                        <div class="pm-col-arr">→</div>
                        <div class="pm-col-cmd">
                          <el-select v-model="mapping.commandParamName" size="small" filterable placeholder="选择命令参数" style="width: 100%;" @change="handleParameterCommandChange(row, mapping)">
                            <el-option v-for="param in commandParameterOptionsDetailed(row.adapterCommandName)" :key="param.paramName" :label="param.paramName + ' · ' + param.dataType" :value="param.paramName" :disabled="isCommandParamMapped(row, param.paramName, mapping)" />
                          </el-select>
                        </div>
                        <div class="pm-col-mode">
                          <div class="mode-control-wrap">
                            <el-radio-group v-model="mapping.isFixedValue" size="small" class="mode-radio-group" @change="handleParameterFixedChange(mapping)">
                              <el-radio-button :value="false">映射</el-radio-button>
                              <el-radio-button :value="true">固定值</el-radio-button>
                            </el-radio-group>
                            <el-input v-if="mapping.isFixedValue" v-model="mapping.fixedValue" size="small" class="fixed-val-input" :placeholder="fixedValuePlaceholder(row, mapping)" />
                          </div>
                        </div>
                        <div class="pm-col-act">
                          <button class="btn-link danger" type="button" @click="removeRow(row.parameterMapping, index)">删除</button>
                        </div>
                        <div v-if="isParameterMappingInvalid(row, mapping)" class="row-warning-full">{{ parameterMappingWarning(row, mapping) }}</div>
                      </div>
                    </div>
                  </article>
                </div>
              </div>
            </div>

            <!-- 05 状态机（严格一体化章节大卡片） -->
            <div id="edit-state" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">05</span>
                  <span class="sec-title-text">状态机</span>
                  <span class="sec-desc-text">通信接口、指令生命周期与功能流转规则</span>
                </div>
                <span class="tag tag-info">系统标准契约</span>
              </div>

              <div class="section-card-body">
                <!-- 5.1 接口定义 -->
                <div class="flat-sub-table">
                  <div class="sub-section-title-bar">
                    <span class="sub-title">5.1 接口定义</span>
                    <span class="sub-count">共 {{ stateMachineInterfaceRows.length }} 个接口</span>
                  </div>
                  <table class="industrial-table">
                    <thead>
                      <tr>
                        <th style="width: 220px;">接口标识</th>
                        <th style="width: 80px;">方向</th>
                        <th style="width: 120px;">类型</th>
                        <th>允许流通信号</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="iface in stateMachineInterfaceRows" :key="iface.name">
                        <td><strong class="mono-text text-heading">{{ iface.name }}</strong></td>
                        <td>
                          <span class="text-primary">{{ directionLabel(iface.direction) }}</span>
                        </td>
                        <td><span class="mono-text">{{ iface.interfaceType || '-' }}</span></td>
                        <td>
                          <div v-if="iface.allowedSignals?.length" class="signal-tags-wrap">
                            <span v-for="sig in iface.allowedSignals" :key="sig" class="signal-tag-pill mono-text">{{ sig }}</span>
                          </div>
                          <span v-else class="text-secondary">-</span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <!-- 5.2 执行生命周期 -->
                <div class="flat-sub-table" style="margin-top: 12px;">
                  <div class="sub-section-title-bar">
                    <span class="sub-title">5.2 执行生命周期</span>
                    <span class="sub-count">内置规范 + Adapter 事件绑定</span>
                  </div>
                  <table class="industrial-table lifecycle-table-deep-border">
                    <thead>
                      <tr>
                        <th style="width: 210px;">阶段转移</th>
                        <th style="width: 200px;">规则说明</th>
                        <th>触发接口信号与驱动事件绑定</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, $index) in mergedLifecycleRules" :key="$index" :class="{ 'system-row-bg': row.type === 'system' }">
                        <td>
                          <div class="flow-pill-left">
                            <template v-if="row.fromStateNames?.length">
                              <span v-for="(name, nIdx) in row.fromStateNames" :key="name" class="tag tag-gray">
                                {{ name }}<span v-if="nIdx < row.fromStateNames.length - 1" style="margin-left: 2px; color: #94a3b8;">/</span>
                              </span>
                            </template>
                            <span v-else class="tag tag-gray">{{ row.fromStateName }}</span>
                            <span style="margin: 0 4px; font-weight: 700; color: var(--sl-text-secondary);">➔</span>
                            <span class="tag tag-gray">{{ row.toStateName || '保持原状态' }}</span>
                          </div>
                        </td>
                        <td>
                          <div class="rule-meta-wrap-left">
                            <span class="tag tag-primary" v-if="row.type === 'system'">系统固定规则</span>
                            <span class="tag tag-info" v-else>{{ ruleKindLabel(row.kind) }}</span>
                            <div style="font-size: 11px; color: #64748b; margin-top: 2px;">{{ row.description }}</div>
                          </div>
                        </td>
                        <td>
                          <div v-if="row.type === 'system'" class="system-triggers-grid-2col">
                            <div v-for="(trig, tIdx) in row.triggers" :key="tIdx" class="system-trigger-line-2col">
                              <div class="trig-left-col">
                                <span style="color: #64748b;">{{ trig.label }}:</span>
                                <span class="mono-text">{{ trig.interfaceName }}</span>
                              </div>
                              <div class="trig-right-col">
                                <span class="tag tag-primary">{{ trig.signalName }}</span>
                              </div>
                            </div>
                          </div>
                          <div v-else class="adapter-binding-wrap-left">
                            <div v-if="row.kind === 'TERMINATION'" class="termination-default-note" style="margin-bottom: 4px;">
                              <span class="tag tag-warning">终止成功默认转换</span>
                              <span style="font-size: 11px; color: #64748b; margin-left: 6px;">终止能力完成后，系统自动将原指令转为 ABORTED（可选Adapter事件）</span>
                            </div>
                            <div class="system-triggers-grid-2col">
                              <div class="trig-left-col">
                                <span :style="row.triggerPolicy === 'REQUIRED' ? 'color: var(--sl-danger); font-weight: 600;' : 'color: #64748b;'">{{ row.triggerPolicy === 'REQUIRED' ? '必选绑定:' : '可选绑定:' }}</span>
                                <span class="mono-text">{{ adapterInterfaceName() }}</span>
                              </div>
                              <div class="trig-right-col">
                                <el-select v-model="executionLifecycleBindings[row.key]" clearable filterable size="small" style="width: 100%;" :placeholder="row.triggerPolicy === 'REQUIRED' ? '请选择Adapter命令事件' : '可选：选择Adapter明确终止事件'">
                                  <el-option v-for="eventName in adapterCmdEventOptions" :key="eventName" :label="eventName" :value="eventName" />
                                </el-select>
                              </div>
                            </div>
                          </div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <!-- 5.3 功能状态分区 (完全改回原来样式与全量增删能力) -->
                <div class="flat-sub-table" style="margin-top: 12px;">
                  <div class="sub-section-title-bar">
                    <span class="sub-title">5.3 功能状态分区</span>
                    <el-dropdown v-if="!hasExceptionRegion" trigger="click" @command="handleAddRegionCommand">
                      <button class="btn-aliyun-cta" type="button">
                        <el-icon><Plus /></el-icon><span>新增分区</span>
                      </button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="OPERATIONAL">功能分区 (Operational)</el-dropdown-item>
                          <el-dropdown-item command="EXCEPTION">异常分区 (Exception)</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                    <button v-else class="btn-aliyun-cta" type="button" @click="addOpStateRegion('OPERATIONAL')">
                      <el-icon><Plus /></el-icon><span>新增分区</span>
                    </button>
                  </div>
                  <div style="padding: 10px 12px;">
                    <div v-for="(region, rIndex) in draft.opState.regions" :key="region._key || rIndex" class="region-block">
                      <div class="region-header" style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                        <div style="display: flex; gap: 8px; align-items: center;">
                          <el-input v-model="region.regionName" size="small" placeholder="分区名称" style="width: 180px;" />
                          <el-tag size="small" :type="region.regionType === 'EXCEPTION' ? 'danger' : 'primary'" effect="plain">
                            {{ region.regionType === 'EXCEPTION' ? '异常区域' : '功能区域' }}
                          </el-tag>
                        </div>
                        <button class="btn-link danger" type="button" @click="removeOpStateRegion(rIndex)">删除分区</button>
                      </div>

                      <div v-if="region.regionType === 'OPERATIONAL'" class="state-summary-row">
                        <span class="state-summary-label">初始状态</span>
                        <div class="state-input-with-warning">
                          <el-select v-model="region.initialStateName" filterable allow-create size="small" class="state-inline-select">
                            <el-option v-for="name in getRegionStateOptions(region)" :key="name" :label="name" :value="name" />
                          </el-select>
                          <span class="warning-slot"><el-tooltip v-if="isRegionInitialStateInvalid(region)" content="该状态不存在" placement="top"><el-icon class="inline-warning-icon"><Warning /></el-icon></el-tooltip></span>
                        </div>
                      </div>
                      <div v-else class="state-summary-row"><span class="state-summary-label">初始状态</span><span class="section-note">异常区域不设置初始状态</span></div>

                      <div class="state-summary-row align-top">
                        <span class="state-summary-label">状态列表</span>
                        <div class="state-token-list">
                          <el-tag v-for="(state, $index) in region.states" :key="state._key || $index" size="small" closable @close="removeOpState(region, $index)" class="state-token filled closable-state-token">
                            <span class="state-token-text">{{ state.stateName || '未命名' }}</span>
                            <span class="state-token-warning-slot"><el-tooltip v-if="stateUsageWarning(state.stateName, region.regionName)" :content="stateUsageWarning(state.stateName, region.regionName)" placement="top"><el-icon class="state-warning-icon"><Warning /></el-icon></el-tooltip></span>
                          </el-tag>
                          <el-input v-if="opStateInputVisibleMap[region._key]" :ref="el => setOpStateInputRef(el, region._key)" v-model="opStateInputValueMap[region._key]" size="small" class="state-name-input" @keyup.enter="handleOpStateInputConfirm(region)" @blur="handleOpStateInputConfirm(region)" />
                          <button v-else class="btn-aliyun" type="button" style="padding: 2px 8px; font-size: 11px;" @click="showOpStateInput(region)">
                            <el-icon><Plus /></el-icon><span>新增状态</span>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 5.4 功能状态转移规则 -->
                <div class="flat-sub-table" style="margin-top: 12px;">
                  <div class="sub-section-title-bar">
                    <span class="sub-title">5.4 功能状态转移规则</span>
                    <button class="btn-aliyun-cta" type="button" @click="addStateTransition">
                      <el-icon><Plus /></el-icon><span>新增规则</span>
                    </button>
                  </div>
                  <div style="padding: 10px 12px;">
                    <div v-if="stateMachineWarningMessages.length" class="state-warning-panel" style="margin-bottom: 10px;">
                      <div v-for="message in stateMachineWarningMessages" :key="message" class="state-warning-item">
                        <el-icon class="inline-warning-icon"><Warning /></el-icon><span>{{ message }}</span>
                      </div>
                    </div>
                    <div v-if="operationTransitionRows.length === 0" class="compact-empty block-empty">暂无功能状态转移规则，可使用上方按钮添加</div>
                    <div v-else class="trans-rule-card-list">
                      <div v-for="(row, $index) in operationTransitionRows" :key="row._key || $index" class="trans-rule-item-card">
                        <!-- 第一行：序号、说明、所属分区、状态流转、删除 -->
                        <div class="trans-card-row-top">
                          <span style="font-size: 11px; font-weight: 700; color: var(--sl-text-secondary);">#{{ $index + 1 }}</span>
                          <div class="field-with-warning" style="flex: 1;">
                            <span class="warning-slot"><el-tooltip v-if="transitionWarning(row)" :content="transitionWarning(row)" placement="top"><el-icon class="inline-warning-icon"><Warning /></el-icon></el-tooltip></span>
                            <el-input v-model="row.description" size="small" placeholder="规则说明，例如：启动完成进入运行态" />
                          </div>

                          <span style="font-size: 11px; color: #64748b;">所属分区:</span>
                          <el-select v-model="row.regionName" size="small" style="width: 120px;">
                            <el-option v-for="region in functionalOpRegions" :key="region._key" :label="region.regionName" :value="region.regionName" />
                          </el-select>

                          <span style="font-size: 11px; color: #64748b;">状态转移:</span>
                          <div class="transition-state-pair">
                            <state-select v-model="row.fromStateName" :options="getRegionStateOptionsByName(row.regionName)" placeholder="原状态" style="width: 100px;" />
                            <span style="color: #94a3b8; font-weight: 700; margin: 0 2px;">→</span>
                            <state-select v-model="row.toStateName" :options="getRegionStateOptionsByName(row.regionName)" placeholder="目标状态" style="width: 100px;" />
                          </div>

                          <button class="btn-link danger" type="button" style="font-size: 11.5px; margin-left: auto;" @click="removeObjectRow(draft.stateTransitions, row)">
                            <el-icon><Delete /></el-icon><span>删除</span>
                          </button>
                        </div>

                        <!-- 第二行：触发条件、转移动作 -->
                        <div class="trans-card-row-bottom">
                          <div style="display: flex; align-items: center; gap: 8px;">
                            <span style="font-size: 11px; color: #64748b; flex-shrink: 0;">触发接口信号:</span>
                            <div style="display: flex; flex-direction: column; gap: 2px;">
                              <span class="mono-text" style="font-size: 11px; color: #64748b; line-height: 1.2;">{{ adapterInterfaceName() || 'Interface_adapter_in' }}</span>
                              <state-select v-model="row.trigger.signalName" :options="adapterOpEventOptions" placeholder="选择或输入触发事件" style="width: 220px;" />
                            </div>
                          </div>

                          <div style="display: flex; align-items: center; gap: 6px; margin-left: auto;">
                            <span style="font-size: 11px; color: #64748b;">转移动作:</span>
                            <div v-if="row.actions?.length" class="transition-action-horizontal-list">
                              <div v-for="(act, aIdx) in row.actions" :key="aIdx" class="transition-action-chip">
                                <span class="action-badge-verb" style="font-size: 10px;">SEND</span>
                                <el-select v-model="act.payload.signalName" size="small" filterable clearable allow-create placeholder="选择/输入信号" style="width: 130px;">
                                  <el-option v-for="sig in getAvailableActionSignals(act.payload?.interfaceName)" :key="sig" :label="sig" :value="sig" />
                                </el-select>
                                <span class="action-sep">➔</span>
                                <span class="mono-text" style="font-size: 11px; color: #475569;">{{ act.payload?.interfaceName || 'Interface_adapter_out' }}</span>
                                <button class="btn-chip-del" type="button" @click="row.actions.splice(aIdx, 1)">✕</button>
                              </div>
                            </div>
                            <span v-else style="font-size: 11px; color: #94a3b8;">暂无额外过程动作</span>
                            <button class="btn-aliyun" type="button" style="padding: 1px 6px; font-size: 11px;" @click="ensureTransitionAction(row)">
                              <el-icon><Plus /></el-icon><span>添加动作</span>
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

              </div>
            </div>

            <div id="edit-constraint" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">06</span>
                  <span class="sec-title-text">内置约束条件</span>
                  <span class="sec-desc-text">设备自动监测的参数限制与异常闭环触发</span>
                </div>
                <button class="btn-aliyun-cta" type="button" @click="addIntrinsicConstraint">
                  <el-icon><Plus /></el-icon><span>新增约束</span>
                </button>
              </div>
              <div class="section-card-body">
                <div v-if="draft.intrinsicConstraints.length === 0" class="compact-empty block-empty">暂无内置约束，可使用上方按钮添加</div>
                <div v-else class="grid-table-container">
                  <div class="grid-table-header constraint-grid-cols">
                    <span class="grid-th">约束属性</span>
                    <span class="grid-th">比较符</span>
                    <span class="grid-th">阈值</span>
                    <span class="grid-th">违规状态</span>
                    <span class="grid-th col-center">操作</span>
                  </div>
                  <div v-for="(row, $index) in draft.intrinsicConstraints" :key="$index" class="grid-table-row constraint-grid-cols">
                    <div class="grid-td">
                      <el-select v-model="row.objectAttributeKey" size="small" filterable style="width: 100%;">
                        <el-option v-for="attr in attributeOptions" :key="attr.key" :label="attr.label" :value="attr.key" />
                      </el-select>
                    </div>
                    <div class="grid-td">
                      <el-select v-model="row.operator" size="small" style="width: 100%;">
                        <el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" />
                      </el-select>
                    </div>
                    <div class="grid-td"><el-input v-model="row.boundaryValue" size="small" placeholder="数值" /></div>
                    <div class="grid-td"><state-select v-model="row.violationStateName" :options="exceptionOpStateNameOptions" /></div>
                    <div class="grid-td col-center"><button class="btn-link danger" type="button" @click="removeRow(draft.intrinsicConstraints, $index)">删除</button></div>
                  </div>
                </div>
              </div>
            </div>

            <div id="edit-bom" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">07</span>
                  <span class="sec-title-text">组件结构清单</span>
                  <span class="sec-desc-text">声明设备下挂的子组件、电机或传感器槽位</span>
                </div>
                <button class="btn-aliyun-cta" type="button" @click="addBomComponent">
                  <el-icon><Plus /></el-icon><span>新增组件</span>
                </button>
              </div>
              <div class="section-card-body">
                <div v-if="draft.componentsBom.length === 0" class="compact-empty block-empty">暂无组件，可使用上方按钮添加</div>
                <div v-else class="grid-table-container">
                  <div class="grid-table-header bom-grid-cols">
                    <span class="grid-th">组件名称</span>
                    <span class="grid-th">设备类别</span>
                    <span class="grid-th">数量</span>
                    <span class="grid-th">说明</span>
                    <span class="grid-th col-center">操作</span>
                  </div>
                  <div v-for="(row, $index) in draft.componentsBom" :key="$index" class="grid-table-row bom-grid-cols">
                    <div class="grid-td"><el-input v-model="row.slotName" size="small" placeholder="例如：搅拌电机" /></div>
                    <div class="grid-td">
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
                    </div>
                    <div class="grid-td"><el-input-number v-model="row.quantity" size="small" :min="1" :controls="false" placeholder="数量" style="width: 100%" /></div>
                    <div class="grid-td"><el-input v-model="row.description" size="small" placeholder="说明" /></div>
                    <div class="grid-td col-center"><button class="btn-link danger" type="button" @click="removeRow(draft.componentsBom, $index)">删除</button></div>
                  </div>
                </div>
              </div>
            </div>

            <div id="edit-template" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">08</span>
                  <span class="sec-title-text">默认数据模板字段</span>
                  <span class="sec-desc-text">创建设备实例时将自动创建对应的数据表字段</span>
                </div>
              </div>
              <div class="section-card-body padded">
                <div v-if="draft.attributes.length === 0" class="compact-empty block-empty">请先添加设备属性</div>
                <el-checkbox-group v-else v-model="draft.defaultDataTemplateAttrs">
                  <el-checkbox v-for="attr in draft.attributes" :key="attr._key" :label="attr._key" :value="attr._key" class="template-attr-option">
                    <span class="template-attr-name">{{ attr.displayName || attr.name || '未命名属性' }}</span>
                    <span class="template-attr-meta">{{ attr.dataType || '-' }}<template v-if="attr.unit"> · {{ attr.unit }}</template></span>
                  </el-checkbox>
                </el-checkbox-group>
                <div style="margin-top: 8px; font-size: 12px; color: var(--sl-text-secondary);">
                  选中的属性将作为该设备模型的默认数据表字段。在创建设备实例时，系统将自动创建对应的数据表。
                </div>
              </div>
            </div>

            <div id="edit-file" class="anchor-section holistic-section-card">
              <div class="section-card-head">
                <div class="section-card-title">
                  <span class="sec-idx-badge">09</span>
                  <span class="sec-title-text">模型文件预览</span>
                  <span class="sec-desc-text">预览并核对生成的设备能力模型与状态机 JSON</span>
                </div>
                <button class="btn-aliyun-cta" type="button" :disabled="generatingPreview" @click="generatePreview">
                  <el-icon><Refresh /></el-icon><span>{{ generatingPreview ? '生成中...' : '生成 / 刷新预览' }}</span>
                </button>
              </div>
              <div class="section-card-body padded">
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
            </div>

          </el-scrollbar>
        </div>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <button class="btn-aliyun" type="button" @click="drawerVisible = false">取消</button>
          <button class="btn-primary-blue" type="button" :disabled="saving" @click="saveDraft">
            <span v-if="saving">保存中...</span>
            <span v-else>保存模型</span>
          </button>
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
  deviceCommandTransitionRequirements,
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
  eventTypeLabel, operatorLabel, valueKindLabel, directionLabel, interfaceRoleLabel,
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
  categories: Array,
  models: Array
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

const usedTemplateKeys = computed(() => {
  const keys = new Set()
  for (const model of (props.models || [])) {
    const ac = model?.adapterContract || {}
    const cfg = (typeof ac === 'object' && !Array.isArray(ac)) ? (ac.config || {}) : {}
    const adapterName = cfg?.adapterName || ''
    const categoryName = cfg?.categoryName || ''
    if (adapterName && categoryName) {
      keys.add(adapterName + '::' + categoryName)
    }
  }
  return keys
})

const registeredAdapterTemplateOptions = computed(() => {
  const adapter = registeredAdapters.value.find(item => item.adapterName === selectedRegisteredAdapterName.value)
  const allOptions = adapterDeviceCategoryOptions(parsedAdapterConfig(adapter))
  const adapterName = adapter?.adapterName || ''
  return allOptions.filter(option => {
    const key = adapterCategoryKey(option)
    return !usedTemplateKeys.value.has(adapterName + '::' + key)
  })
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
const systemTriggerLabels = {
  WF_EXECUTE_START: '工作流触发',
  MANUAL_EXECUTE_START: '控制台手动下发',
  CONSTRAINT_EXECUTE: '约束能力执行',
  WF_EXECUTE_ABORT: '工作流请求中止',
  MANUAL_EXECUTE_ABORT: '控制台手动中止',
  MANUAL_EXECUTE_RESET: '控制台人工复位',
  CONSTRAINT_ABORT: '约束违规中止'
}
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
      description: '下发指令并启动 (自动向 Adapter 发送报文)',
      triggers: startRules.map(r => ({
        interfaceName: r.trigger?.interfaceName,
        signalName: r.trigger?.signalName,
        label: systemTriggerLabels[r.trigger?.signalName] || r.trigger?.signalName
      })),
      actions: startRules[0].actions
    })
  }

  // 2. 合并 RUNNING -> ABORTING
  const abortRules = rules.filter(r => r.actions?.[0]?.payload?.signalName === 'CMD_ABORT')
  if (abortRules.length > 0) {
    groups.push({
      key: 'sys_abort',
      fromStateNames: [...new Set(abortRules.map(r => r.fromStateName))],
      toStateName: 'ABORTING',
      description: '下发指令中止请求 (自动向 Adapter 发送 ABORT 报文)',
      triggers: abortRules.map(r => ({
        interfaceName: r.trigger?.interfaceName,
        signalName: r.trigger?.signalName,
        label: systemTriggerLabels[r.trigger?.signalName] || r.trigger?.signalName
      })),
      actions: abortRules[0].actions
    })
  }

  return groups
})
const executionLifecycleRuleRows = computed(() => buildLifecycleRuleRows(deviceCommandTransitionRequirements))
const mergedLifecycleRules = computed(() => {
  const sysRules = groupedCommandLifecycleTransitions.value.map(r => ({ ...r, type: 'system' }))
  const bindRules = executionLifecycleRuleRows.value.map(r => {
    let desc = ''
    if (r.kind === 'FAILURE') {
      desc = 'Adapter执行失败事件（必须绑定）'
    } else if (r.kind === 'TERMINATION') {
      desc = r.triggerPolicy === 'REQUIRED'
        ? 'Adapter终止成功事件（必须绑定）'
        : '终止成功默认转换；可选绑定Adapter明确终止事件'
    } else if (r.toStateName === 'RUNNING') {
      desc = 'Adapter开始执行事件（必须绑定）'
    } else if (r.toStateName === 'COMPLETED') {
      desc = 'Adapter完成执行事件（必须绑定）'
    } else {
      desc = 'Adapter命令生命周期事件（必须绑定）'
    }
    return { ...r, type: 'binding', description: desc }
  })
  return [...sysRules, ...bindRules]
})
const functionalOpRegions = computed(() => (draft.opState.regions || []).filter(region => region.regionType === 'OPERATIONAL'))
const operationTransitionRows = computed(() => draft.stateTransitions.filter(row => row.stateSpace === 'OP' && functionalOpRegions.value.some(region => region.regionName === row.regionName)))
const commandStateNameOptions = computed(() => draft.cmdState.states.map(item => item.stateName).filter(Boolean))
const opStateNameOptions = computed(() => {
  return (draft.opState.regions || []).flatMap(r => r.states || []).map(item => item.stateName).filter(Boolean)
})

const exceptionOpStateNameOptions = computed(() => {
  return (draft.opState.regions || [])
    .filter(r => r.regionType === 'EXCEPTION')
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

function stateUsageWarning(stateName, regionName = '') {
  if (!stateName) return '状态名未填写'
  const region = (draft.opState.regions || []).find(item => item.regionName === regionName)
  if (region?.regionType === 'EXCEPTION') {
    const usedByIntrinsicConstraint = asArray(draft.intrinsicConstraints)
      .some(constraint => stringValue(constraint.violationStateName) === stateName)
    return usedByIntrinsicConstraint ? '' : '异常状态未被任何内置约束使用'
  }
  const used = operationTransitionRows.value.some(t => t.fromStateName === stateName || t.toStateName === stateName)
  if (!used) return '状态未在任何转移规则中使用'
  return ''
}

function transitionWarning(row) {
  if (!row.fromStateName) return '来源状态为空'
  if (!row.toStateName) return '目标状态为空'
  return ''
}

function stateTagClass(stateName) {
  const s = String(stateName || '').toUpperCase()
  if (s === 'COMPLETED') return 'tag-success'
  if (s === 'FAILED') return 'tag-danger'
  if (s === 'ABORTING' || s === 'ABORTED') return 'tag-warning'
  if (s === 'RUNNING') return 'tag-primary'
  if (s === 'SENT') return 'tag-purple'
  if (s === 'IDLE') return 'tag-gray'
  return 'tag-gray'
}

function targetStateClass(toStateName) {
  const s = String(toStateName || '').toUpperCase()
  if (s === 'COMPLETED') return 'success'
  if (s === 'ABORTED' || s === 'ABORTING') return 'warning'
  if (s === 'FAILED') return 'danger'
  if (s === 'RUNNING') return 'primary'
  return 'info'
}

function ruleKindLabel(kind) {
  if (kind === 'FAILURE') return '失败分支规则'
  if (kind === 'TERMINATION') return '终止分支规则'
  return '正常推进阶段'
}

const stateMachineWarningMessages = computed(() => {
  const messages = []
  if (operationTransitionRows.value.length > 0 || (draft.opState.regions || []).some(region => region.regionType === 'EXCEPTION')) {
    (draft.opState.regions || []).forEach(region => {
      if (region.regionType !== 'EXCEPTION' && operationTransitionRows.value.length === 0) return
      (region.states || []).forEach(state => {
        const warning = stateUsageWarning(state.stateName, region.regionName)
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
  const validAttributes = asArray(draft.attributes)
    .filter(attribute => stringValue(attribute.name || attribute.displayName))
  if (!validAttributes.length) {
    ElMessage.error('设备模型至少需要一个属性，才能生成默认数据模板')
    return
  }
  if (!asArray(draft.defaultDataTemplateAttrs).length) {
    draft.defaultDataTemplateAttrs = validAttributes.map(attribute => attribute._key).filter(Boolean)
  }
  const abortWithoutScope = asArray(draft.capabilities)
    .find(capability => capability.isAbort === true && !normalizeScopeKeys(capability.scopeCapabilityKeys).length)
  if (abortWithoutScope) {
    ElMessage.error(`终止能力“${capabilityLabel(abortWithoutScope)}”至少需要选择一个受影响的普通操作`)
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
        regionType: r.regionType,
        initialStateName: r.regionType === 'EXCEPTION' ? '' : stringValue(r.initialStateName),
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
  if (deviceCommandTransitionRequirements.length === 0) {
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
        telemetryName: stringValue(a.telemetryName), dataType: a.dataType, description: stringValue(a.description)
      })).filter(a => a.telemetryName),
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
    const attributeName = stringValue(attr.attributeName || attr.name)
    const columnName = reserveIdentifier(toSafeColumnName(attributeName, index), 'column_' + (index + 1), usedColumns)
    details.push({
      columnName,
      columnDesc: attr.displayName || attributeName || columnName,
      propertyTypeId: propertyTypeIdForDataType(attr.dataType, propertyTypes),
      columnLength: 255,
      deviceAttrKey: attributeName,
      defaultValue: ''
    })
    const unitValue = stringValue(attr.unit)
    if (unitValue) {
      const unitColumnName = reserveIdentifier(columnName + '_unit', 'column_' + (index + 1) + '_unit', usedColumns)
      details.push({
        columnName: unitColumnName,
        columnDesc: (attr.displayName || attributeName || columnName) + '单位',
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

function addPort() { draft.ports.push({ _key: makeUiKey('port'), portName: '', direction: 'OUT', bindingAttrKey: '', bindingAttrName: '', description: '' }) }
function capabilityLabel(capability) {
  const index = draft.capabilities.indexOf(capability)
  return capability.displayName || capability.capabilityName || '操作' + (index + 1)
}

function terminationCapabilityOptions(capability) {
  return draft.capabilities.filter(item => item._key !== capability._key && item.isAbort === true)
}

function normalCapabilityOptions(capability) {
  return draft.capabilities.filter(item => item._key !== capability._key && item.isAbort !== true && (!item.abortCapabilityKey || item.abortCapabilityKey === capability._key))
}

function normalizeScopeKeys(keys) {
  return [...new Set(asArray(keys).filter(key => draft.capabilities.some(item => item._key === key && item.isAbort !== true)))]
}

function syncCapabilityReferenceNames(capability) {
  if (!capability) return
  if (capability.isAbort === true) {
    capability.abortCapabilityKey = ''
    capability.abortCapabilityName = null
    capability.scopeCapabilityKeys = normalizeScopeKeys(capability.scopeCapabilityKeys)
    capability.scope = capability.scopeCapabilityKeys.map(key => draft.capabilities.find(item => item._key === key)?.name).filter(Boolean)
    return
  }
  const target = draft.capabilities.find(item => item._key === capability.abortCapabilityKey && item.isAbort === true)
  capability.abortCapabilityKey = target?._key || ''
  capability.abortCapabilityName = target?.name || null
  capability.scopeCapabilityKeys = []
  capability.scope = []
}

function handleCapabilityAbortTypeChange(capability) {
  capability.isAbort = capability.isAbort === true
  if (capability.isAbort) {
    draft.capabilities.forEach(item => {
      if (item.isAbort === true && item !== capability) {
        item.scopeCapabilityKeys = asArray(item.scopeCapabilityKeys).filter(key => key !== capability._key)
        syncCapabilityReferenceNames(item)
      }
    })
  } else {
    draft.capabilities.forEach(item => {
      if (item.abortCapabilityKey === capability._key) {
        item.abortCapabilityKey = ''
        syncCapabilityReferenceNames(item)
      }
    })
  }
  syncCapabilityReferenceNames(capability)
}

function handleAbortCapabilityChange(capability) {
  const targetAbortKey = capability.abortCapabilityKey
  draft.capabilities.forEach(item => {
    if (item.isAbort === true) {
      const keys = new Set(asArray(item.scopeCapabilityKeys))
      if (item._key === targetAbortKey) {
        keys.add(capability._key)
      } else {
        keys.delete(capability._key)
      }
      item.scopeCapabilityKeys = normalizeScopeKeys([...keys])
      syncCapabilityReferenceNames(item)
    }
  })
  syncCapabilityReferenceNames(capability)
}

function handleAbortScopeChange(capability) {
  capability.scopeCapabilityKeys = normalizeScopeKeys(capability.scopeCapabilityKeys)
  const selectedScopeKeys = new Set(capability.scopeCapabilityKeys)
  draft.capabilities.forEach(item => {
    if (item.isAbort !== true) {
      if (selectedScopeKeys.has(item._key)) {
        item.abortCapabilityKey = capability._key
      } else if (item.abortCapabilityKey === capability._key) {
        item.abortCapabilityKey = ''
      }
      syncCapabilityReferenceNames(item)
    }
  })
  syncCapabilityReferenceNames(capability)
}

function addCapability() {
  draft.capabilities.push({
    _key: makeUiKey('cap'), name: '', displayName: '', adapterCommandName: '',
    isAbort: false, abortCapabilityName: null, abortCapabilityKey: '', scope: [], scopeCapabilityKeys: [],
    parameters: [], parameterMapping: []
  })
}
function removeCapability(capability) {
  draft.capabilities.forEach(item => {
    if (item.abortCapabilityKey === capability._key) {
      item.abortCapabilityKey = ''
      syncCapabilityReferenceNames(item)
    }
    if (item.isAbort === true) {
      item.scopeCapabilityKeys = asArray(item.scopeCapabilityKeys).filter(key => key !== capability._key)
      syncCapabilityReferenceNames(item)
    }
  })
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

const hasExceptionRegion = computed(() => {
  return (draft.opState.regions || []).some(r => r.regionType === 'EXCEPTION')
})

function addOpStateRegion(type = 'OPERATIONAL') {
  const newKey = makeUiKey('region')
  if (!draft.opState.regions) {
    draft.opState.regions = []
  }
  if (type === 'EXCEPTION') {
    draft.opState.regions.push({
      _key: newKey,
      regionName: 'Exception',
      regionType: 'EXCEPTION',
      initialStateName: '',
      states: [{ _key: makeUiKey('state'), stateName: 'ABNORMAL', onEntry: defaultStateEntryActions('OP', 'ABNORMAL') }]
    })
  } else {
    draft.opState.regions.push({
      _key: newKey,
      regionName: '新建分区',
      regionType: 'OPERATIONAL',
      initialStateName: 'IDLE',
      states: [{ _key: makeUiKey('state'), stateName: 'IDLE', onEntry: defaultStateEntryActions('OP', 'IDLE') }]
    })
  }
}

function handleAddRegionCommand(command) {
  addOpStateRegion(command)
}

function handleRegionTypeChange(region) {
  if (region.regionType === 'EXCEPTION') {
    region.initialStateName = ''
    return
  }
  region.initialStateName = getRegionStateOptions(region)[0] || 'IDLE'
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
  if (!draft.adapterContract?.config?.adapterName && asArray(draft.adapterContract?.events).length === 0) {
    ElMessage.warning('请先载入 Adapter 契约配置')
    return
  }
  if (functionalOpRegions.value.length === 0) {
    if (!draft.opState.regions) draft.opState.regions = []
    draft.opState.regions.push({
      _key: makeUiKey('region'),
      regionName: 'Main',
      regionType: 'OPERATIONAL',
      initialStateName: '',
      states: []
    })
    ElMessage.info('已自动初始化默认功能状态分区 Main')
  }
  const firstRegion = functionalOpRegions.value[0]?.regionName || 'Main'
  const defaultSignal = adapterOpEventOptions.value[0] || ''
  draft.stateTransitions.push({
    _key: makeUiKey('transition'),
    stateSpace: 'OP',
    regionName: firstRegion,
    description: '',
    fromStateName: '',
    toStateName: '',
    trigger: { interfaceName: adapterInterfaceName(), signalName: defaultSignal },
    actions: []
  })
}

function getAvailableActionSignals(interfaceName) {
  const list = getSignalsForInterface(interfaceName)
  if (Array.isArray(list) && list.length > 0) return list
  return adapterOutSignals.length > 0 ? adapterOutSignals : ['CMD_START', 'CMD_ABORT', 'OP_STATE']
}

function ensureTransitionAction(row) {
  if (!Array.isArray(row.actions)) {
    row.actions = []
  }
  const iface = 'Interface_adapter_out'
  const signals = getAvailableActionSignals(iface)
  const defaultSig = signals[0] || 'CMD_START'
  row.actions.push({
    actionName: 'SEND',
    payload: {
      interfaceName: iface,
      signalName: defaultSig
    }
  })
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
.basic-form, .mapping-form { max-width: 100%; }
.basic-form :deep(.el-select), .mapping-form :deep(.el-select) { width: 100%; }
.config-import { margin-bottom: 8px; }
.registered-adapter-picker { width: 100%; display: grid; grid-template-columns: minmax(160px, 1.1fr) minmax(160px, 1fr) auto auto; gap: 8px; align-items: center; }
.registered-adapter-picker :deep(.el-select) { width: 100%; }

/* ── 操作映射卡片与嵌套子表 ── */
.function-mapping-card-list { display: grid; gap: 10px; }
.function-mapping-editor-card { border: 1px solid var(--sl-border-base); border-left: 3px solid var(--sl-primary); border-radius: var(--sl-radius-sm); background: #ffffff; padding: 10px; }
.function-map-editor-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.function-map-selects { flex: 1; min-width: 0; display: grid; grid-template-columns: minmax(150px, 1fr) 24px minmax(150px, 1fr); align-items: center; gap: 8px; }
.function-map-selects label { min-width: 0; display: flex; align-items: center; gap: 6px; }
.function-map-selects label > span { color: var(--sl-text-secondary); font-size: 12px; font-weight: 600; white-space: nowrap; flex-shrink: 0; }
.mapping-direction { align-self: center; color: var(--sl-text-secondary); text-align: center; font-weight: 700; }

.nested-param-table-card {
  margin-top: 6px;
  border: 1px solid var(--sl-border-base);
  border-radius: 4px;
  background: #ffffff;
  overflow: hidden;
}
.nested-param-table-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base);
  font-size: 11.5px;
  font-weight: 600;
  color: var(--sl-text-secondary);
}
.nested-param-table-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px solid #f1f5f9;
  background: #ffffff;
  flex-wrap: wrap;
}
.nested-param-empty-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: #ffffff;
}
.nested-param-table-row:last-of-type {
  border-bottom: none;
}
.nested-param-table-row.invalid {
  background: #fff7f7;
  border-color: #fecaca;
}
.pm-col-cap {
  flex: 1.2;
  min-width: 130px;
}
.pm-col-arr {
  width: 16px;
  flex-shrink: 0;
  text-align: center;
  color: var(--sl-text-secondary);
  font-weight: 700;
}
.pm-col-cmd {
  flex: 1.2;
  min-width: 130px;
}
.pm-col-mode {
  flex: 1.6;
  min-width: 170px;
}
.mode-control-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.mode-radio-group {
  flex-shrink: 0;
}
.mode-radio-group :deep(.el-radio-button__inner) {
  padding: 4px 8px !important;
  font-size: 11px !important;
}
.fixed-val-input {
  flex: 1;
  min-width: 80px;
}
.pm-col-act {
  width: 45px;
  flex-shrink: 0;
  text-align: center;
}
.row-warning-full {
  width: 100%;
  margin-top: 4px;
  color: #dc2626;
  font-size: 11.5px;
}
.param-map-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: #fafbfc;
  border-top: 1px solid #f1f5f9;
}
.no-mapping-placeholder {
  font-size: 12px;
  color: var(--sl-text-secondary);
  font-style: italic;
}

.locked-heading { display: flex; align-items: center; gap: 8px; min-width: 0; }
.locked-heading h3 { margin: 0; }
.locked-section { background: #ffffff; border: 1px solid var(--sl-border-base); border-radius: var(--sl-radius-sm); padding: 10px; }
.state-group-title { margin: 20px 0 10px; padding-bottom: 6px; border-bottom: 1px solid var(--sl-border-base); color: var(--sl-text-heading); font-size: 15px; font-weight: 600; }
.state-group-title.first { margin-top: 0; }
.state-card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 8px; }
.state-card { background: #ffffff; border: 1px solid var(--sl-border-base); border-radius: var(--sl-radius-sm); padding: 10px; }
.region-block { border: 1px solid var(--sl-border-base); border-radius: var(--sl-radius-sm); padding: 10px 12px; margin-bottom: 10px; background: #ffffff; }
.state-summary-row { display: grid; grid-template-columns: 64px minmax(0, 1fr); align-items: center; gap: 8px; min-height: 28px; margin-top: 6px; }
.state-summary-row.align-top { align-items: flex-start; }
.state-summary-label { color: var(--sl-text-secondary); font-size: 12px; font-weight: 500; }
.state-token-list { display: flex; flex-wrap: wrap; gap: 6px; min-width: 0; align-items: center; }
.state-token { border: 0; }
.state-token.filled { background: #ecfdf5; color: #047857; }
.closable-state-token :deep(.el-tag__content) { display: inline-flex; align-items: center; gap: 4px; min-width: 0; }
.state-token-text { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; font-size: 11.5px; }
.state-token-warning-slot, .warning-slot { width: 16px; min-width: 16px; display: inline-flex; align-items: center; justify-content: center; }
.state-warning-icon, .inline-warning-icon { color: #dc2626; font-size: 13px; line-height: 1; }
.state-input-with-warning, .field-with-warning { display: grid; grid-template-columns: 16px minmax(0, 1fr); align-items: center; gap: 4px; width: 100%; }
.state-input-with-warning { grid-template-columns: minmax(0, 1fr) 16px; }
.state-warning-panel { margin: 0 0 8px; padding: 6px 8px; border: 1px solid #fecaca; border-radius: 4px; background: #fff7f7; display: flex; flex-direction: column; gap: 4px; }
.state-warning-item { display: flex; align-items: center; gap: 6px; color: #991b1b; font-size: 11.5px; line-height: 1.4; }
.state-inline-select { width: 100%; }
.compact-state-table { margin-top: 8px; background: #ffffff; }
.compact-transition-table { width: 100%; }
.compact-transition-table :deep(.el-table__cell) { padding: 5px 6px; }
.transition-action-cell { display: grid; grid-template-columns: minmax(0, 1fr) 26px; gap: 4px; align-items: start; }
.transition-action-list { display: flex; flex-direction: column; gap: 4px; min-width: 0; align-items: flex-start; }
.transition-action-row { display: grid; grid-template-columns: 28px minmax(100px, 1fr) 20px; align-items: center; gap: 4px; width: 100%; }
.action-editor-label { color: var(--sl-text-secondary); font-size: 11.5px; }
.compact-action-btn { padding: 3px 6px; font-size: 11px; }
.template-attr-option { margin-right: 12px; margin-bottom: 6px; padding: 6px 10px; border: 1px solid var(--sl-border-base); border-radius: 4px; background: #f8fafc; }
.template-attr-name { font-weight: 600; color: var(--sl-text-heading); font-size: 12px; }
.template-attr-meta { margin-left: 6px; color: var(--sl-text-secondary); font-size: 11px; }
.nested-toolbar { display: flex; align-items: center; justify-content: space-between; margin: 8px 0 6px; color: var(--sl-text-secondary); font-size: 12px; font-weight: 600; }
.nested-table-card { margin-top: 4px; }
.btn-link {
  white-space: nowrap !important;
  word-break: keep-all !important;
  display: inline-block;
}

/* ── 一体化数据网格卡片容器规范 (Unified Grid Table/Card Container) ── */
.grid-table-container {
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  overflow: hidden;
  width: 100%;
}
.nested-grid-table {
  border-color: #e2e8f0;
}
.grid-table-header {
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base);
  padding: 7px 10px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--sl-text-secondary);
  line-height: 1.4;
}
.grid-table-header .grid-th {
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.grid-table-header .grid-th.col-center {
  text-align: center;
}
.grid-table-row {
  padding: 6px 10px;
  border-bottom: 1px solid #f1f5f9;
  background: #ffffff;
  transition: background-color 0.15s ease;
  min-height: 40px;
}
.grid-table-row:last-of-type {
  border-bottom: none;
}
.grid-table-row:hover {
  background-color: #f8fafc;
}
.grid-table-row.system-row-bg {
  background-color: #fafbfc;
}
.grid-table-row.system-row-bg:hover {
  background-color: #f1f5f9;
}
.grid-td {
  min-width: 0;
  display: flex;
  align-items: center;
}
.grid-td.col-center {
  justify-content: center;
  text-align: center;
}
.grid-td.lifecycle-td {
  align-items: flex-start;
  padding: 2px 0;
}
.grid-td :deep(.el-input__wrapper),
.grid-td :deep(.el-select__wrapper),
.grid-td :deep(.el-tree-select .el-select__wrapper) {
  box-shadow: 0 0 0 1px #dbe4ef inset !important;
  border-radius: 4px;
  background-color: #ffffff;
  padding: 1px 7px !important;
  height: 28px !important;
  min-height: 28px !important;
  line-height: 28px !important;
  width: 100% !important;
}
.grid-td :deep(.el-input__wrapper:hover),
.grid-td :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #94a3b8 inset !important;
}
.grid-td :deep(.el-input__wrapper.is-focus),
.grid-td :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1.5px var(--sl-primary) inset !important;
}
.font-mono-text {
  font-family: var(--sl-font-mono);
  font-size: 12px;
  color: var(--sl-text-heading);
  font-weight: 500;
}
.desc-sub-text {
  font-size: 12px;
  color: var(--sl-text-secondary);
}
.map-arrow {
  color: var(--sl-text-secondary);
  font-weight: 700;
  font-size: 13px;
}

/* ── 各模块专用网格列定义 (精确比例且表头与数据行绝对同步) ── */
.attr-grid-cols {
  display: grid;
  grid-template-columns: minmax(130px, 1.4fr) minmax(100px, 1fr) minmax(110px, 1.1fr) minmax(80px, 0.8fr) 50px;
  gap: 8px;
  align-items: center;
}
.cap-param-grid-cols {
  display: grid;
  grid-template-columns: minmax(130px, 1.4fr) minmax(120px, 1.1fr) 50px;
  gap: 8px;
  align-items: center;
}
.port-grid-cols {
  display: grid;
  grid-template-columns: minmax(130px, 1.4fr) minmax(90px, 0.9fr) minmax(140px, 1.4fr) 50px;
  gap: 8px;
  align-items: center;
}
.cmd-param-grid-cols {
  display: grid;
  grid-template-columns: minmax(130px, 1.2fr) minmax(100px, 1fr) minmax(150px, 1.8fr);
  gap: 8px;
  align-items: center;
}
.telemetry-grid-cols {
  display: grid;
  grid-template-columns: minmax(140px, 1.4fr) minmax(110px, 1fr) minmax(160px, 1.8fr);
  gap: 8px;
  align-items: center;
}
.event-grid-cols {
  display: grid;
  grid-template-columns: 80px minmax(150px, 1.4fr) minmax(160px, 1.8fr);
  gap: 8px;
  align-items: center;
}
.attrmap-grid-cols {
  display: grid;
  grid-template-columns: minmax(140px, 1.4fr) 24px minmax(140px, 1.4fr) 50px;
  gap: 8px;
  align-items: center;
}
.op-trans-grid-cols {
  display: grid;
  grid-template-columns: minmax(110px, 1fr) minmax(90px, 0.9fr) minmax(160px, 1.4fr) minmax(130px, 1.2fr) minmax(160px, 1.5fr) 50px;
  gap: 8px;
  align-items: center;
}
.constraint-grid-cols {
  display: grid;
  grid-template-columns: minmax(100px, 1fr) minmax(100px, 1fr) minmax(100px, 1fr) minmax(100px, 1fr) 50px;
  gap: 8px;
  align-items: center;
}
.bom-grid-cols {
  display: grid;
  grid-template-columns: minmax(140px, 1.4fr) minmax(150px, 1.4fr) 110px minmax(130px, 1.8fr) 50px;
  gap: 8px;
  align-items: center;
}

/* ── 执行生命周期专属排版 (左对齐、清晰药丸流与触发器) ── */
.lifecycle-grid-cols {
  display: grid;
  grid-template-columns: minmax(180px, 1.1fr) minmax(180px, 1.2fr) minmax(280px, 2fr);
  gap: 12px;
  align-items: flex-start;
}
.flow-pill-left {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.flow-state-tag {
  font-weight: 600;
  font-size: 11.5px;
}
.flow-arrow-icon {
  color: var(--sl-text-secondary);
  font-size: 12px;
}
.rule-meta-wrap-left {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
.system-rule-tag {
  display: inline-block;
  padding: 2px 6px;
  background: #f1f5f9;
  color: #475569;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.user-rule-tag {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background: #eff6ff;
  color: #1d4ed8;
}
.user-rule-tag.failure {
  background: #fef2f2;
  color: #b91c1c;
}
.user-rule-tag.termination {
  background: #fffbeb;
  color: #b45309;
}
.rule-desc-text-left {
  font-size: 11.5px;
  color: var(--sl-text-body);
  line-height: 1.4;
}
.system-triggers-grid-2col {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.system-trigger-line-2col {
  display: grid;
  grid-template-columns: 240px 1fr;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.binding-interface-grid-2col {
  display: grid;
  grid-template-columns: 240px 1fr;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.trig-left-col {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.trig-right-col {
  display: flex;
  align-items: center;
  min-width: 0;
}
.trig-label-tag {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  white-space: nowrap;
  flex-shrink: 0;
  min-width: 54px;
}
/* ── 子表与顶格平铺工业表格 (对齐效果图规范) ── */
.flat-sub-table {
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 4px);
  overflow: hidden;
  background: #ffffff;
}
.sub-section-title-bar {
  height: 30px;
  padding: 0 10px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sub-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}
.sub-count {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  font-family: var(--sl-font-mono, monospace);
}

.industrial-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  text-align: left;
  background: #ffffff;
}
.industrial-table th {
  background: #f8fafc;
  color: var(--sl-text-secondary, #64748b);
  font-weight: 600;
  padding: 7px 10px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  white-space: nowrap;
}
.industrial-table td {
  padding: 7px 10px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  vertical-align: middle;
  color: var(--sl-text-body, #334155);
}
.industrial-table tr:hover td {
  background: var(--sl-bg-hover, #f8fafc);
}
.industrial-table tr:last-child td {
  border-bottom: none;
}

/* 标签徽章体系 */
.tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.25;
  white-space: nowrap;
  font-family: var(--sl-font-mono, monospace);
}
.tag-primary { background: #eff6ff; color: #2563eb; border: 1px solid #bfdbfe; }
.tag-success { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }
.tag-danger { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
.tag-warning { background: #fffbeb; color: #d97706; border: 1px solid #fde68a; }
.tag-purple { background: #f5f3ff; color: #7c3aed; border: 1px solid #ddd6fe; }
.tag-gray { background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1; }
.tag-info { background: #f8fafc; color: #0284c7; border: 1px solid #bae6fd; }

/* 允许信号清晰小标签药丸 */
.signal-tags-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 6px;
  align-items: center;
}
.signal-tag-pill {
  display: inline-block;
  padding: 2px 7px;
  border-radius: 4px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #0f172a;
  font-size: 11.5px;
  font-weight: 500;
  font-family: var(--sl-font-mono, monospace);
}

.action-badge-verb {
  display: inline-block;
  padding: 0 4px;
  border-radius: 3px;
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
  font-size: 10px;
  font-weight: 700;
  font-family: var(--sl-font-mono, monospace);
  letter-spacing: 0.5px;
}
.action-sep {
  color: #94a3b8;
  font-weight: 700;
  font-size: 11px;
}

.lifecycle-table-deep-border {
  border-top: 1px solid #94a3b8 !important;
}

.trig-label-left {
  color: var(--sl-text-secondary);
  font-size: 11px;
  min-width: 60px;
  flex-shrink: 0;
}
.locked-action-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  font-size: 11px;
  color: #334155;
  white-space: nowrap;
}
.signal-tag-bold {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 6px;
  background: #f0fdf4;
  border: 1px solid #86efac;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #15803d;
  white-space: nowrap;
}
.adapter-binding-wrap-left {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  width: 100%;
}
.termination-default-note {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: #b45309;
  background: #fffbeb;
  padding: 4px 8px;
  border-radius: 4px;
  margin-bottom: 2px;
  width: 100%;
}
.binding-req-tip {
  font-size: 11px;
  color: var(--sl-text-secondary);
  font-style: italic;
}
.transition-state-pair {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}
.transition-trigger-editor {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.adapter-iface-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  font-size: 11px;
  color: var(--sl-text-secondary);
  white-space: nowrap;
  flex-shrink: 0;
}

/* ── 影响范围标签输入器 ── */
.capability-scope-editor-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1 1 100%;
}
.field-label-text {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--sl-text-body);
}
.scope-tags-container {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.scope-tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.scope-input-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 360px;
}

/* ── 适配器契约 3合1 子区域 ── */
.contract-source-box {
  background: #f8fafc;
  border: 1px solid var(--sl-border-base);
  border-radius: 4px;
  padding: 8px 12px;
  margin-bottom: 12px;
}
.contract-sub-section {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--sl-border-base);
}
.contract-sub-section:first-of-type {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}
.contract-sub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sub-header-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading);
}
.sub-header-count {
  font-size: 11px;
  color: var(--sl-text-secondary);
  font-family: var(--sl-font-mono);
}

/* ── 功能状态转移规则双行紧凑卡片排布 ── */
.trans-rule-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.trans-rule-item-card {
  border: 1px solid var(--sl-border-base);
  border-radius: 6px;
  background: #ffffff;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  transition: all 0.15s ease;
}
.trans-rule-item-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}
.trans-card-row-top {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.trans-card-row-bottom {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding-top: 6px;
  border-top: 1px dashed #f1f5f9;
}
.trans-field-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.trans-field-cell.desc-cell {
  flex: 1;
}
.trans-field-cell.region-cell {
  flex-shrink: 0;
}
.trans-field-cell.flow-cell {
  flex: 1;
  min-width: 240px;
}
.trans-field-cell.trigger-cell {
  flex: 1;
  max-width: 380px;
}
.trans-field-cell.action-cell {
  flex: 2;
}
.field-mini-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-text-secondary);
  white-space: nowrap;
  flex-shrink: 0;
}
.trans-del-btn {
  font-size: 12px;
  margin-left: auto;
  flex-shrink: 0;
}
.transition-action-horizontal-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}
.transition-action-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base);
  border-radius: 4px;
}
.action-editor-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--sl-text-secondary);
}
.btn-chip-del {
  border: none;
  background: transparent;
  color: #ef4444;
  cursor: pointer;
  padding: 0 2px;
  font-size: 11px;
  line-height: 1;
}
.btn-chip-del:hover {
  color: #b91c1c;
}
.btn-add-action-inline {
  padding: 2px 8px;
  font-size: 11px;
  flex-shrink: 0;
  margin-left: 2px;
}

.block-empty { margin-top: 4px; }
.drawer-section { margin-top: 8px; border: 1px solid var(--sl-border-base); border-radius: var(--sl-radius-sm); padding: 10px 12px; background: #ffffff; box-shadow: none; }
.drawer-footer { justify-content: flex-end; display: flex; align-items: center; gap: 8px; }
.model-edit-workbench { display: flex; min-height: 0; overflow: hidden; height: calc(100vh - 110px); }
.edit-scroll-content { flex: 1; min-width: 0; }
.edit-scroll-content :deep(.el-scrollbar__view) { padding: 12px 16px 24px; }
.capability-editor-card, .capability-editor-list, .capability-title-editor { border-left: 3px solid var(--sl-primary); }
.capability-execution-config { display: flex; flex-wrap: wrap; gap: 8px 12px; margin: 8px 0; padding: 6px 8px; border: 1px solid var(--sl-border-base); border-radius: 4px; background: #f8fafc; }
.capability-config-field { display: flex; align-items: center; gap: 6px; min-width: 160px; color: var(--sl-text-body); font-size: 11.5px; font-weight: 600; }
.capability-config-field > span { flex: 0 0 auto; }
.capability-config-select { flex: 1 1 240px; }
.capability-config-select :deep(.el-select) { flex: 1; min-width: 0; }
.command-editor-card, .command-editor-list { border-left: 3px solid var(--sl-success); }
.locked-table :deep(.el-table__body-wrapper) { background: #ffffff; }
.locked-action { max-width: 100%; color: var(--sl-text-body); font-size: 11.5px; line-height: 1.4; display: inline-flex; align-items: center; gap: 4px; flex-wrap: wrap; white-space: normal; }

/* ── 方案一：一体化章节大卡片规范 (Holistic Section Card) ── */
.holistic-section-card {
  border: 1px solid var(--sl-border-base);
  border-radius: var(--sl-radius-sm);
  background: #ffffff;
  margin-bottom: 14px;
  overflow: hidden;
}
.section-card-head {
  background: #ffffff;
  padding: 10px 14px;
  border-bottom: 1px solid var(--sl-border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sec-idx-badge {
  display: inline-flex;
  width: 22px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--sl-primary-border);
  border-radius: 3px;
  background: var(--sl-primary-light);
  color: var(--sl-primary);
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono);
}
.sec-title-text {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--sl-text-heading);
}
.sec-desc-text {
  font-size: 12px;
  color: var(--sl-text-secondary);
  margin-left: 4px;
  font-weight: 400;
}
.section-card-body {
  padding: 0;
}
.section-card-body.padded {
  padding: 12px 14px;
}

/* ── 列表卡片与输入框对齐 ── */
.table-card :deep(.el-input__wrapper),
.table-card :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px #dbe4ef inset !important;
  border-radius: 4px;
  background-color: #ffffff;
  padding: 1px 7px !important;
  height: 28px !important;
  min-height: 28px !important;
  line-height: 28px !important;
  transition: all 0.15s ease;
}
.table-card :deep(.el-input__wrapper:hover),
.table-card :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #94a3b8 inset !important;
}
.table-card :deep(.el-input__wrapper.is-focus),
.table-card :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1.5px var(--sl-primary) inset !important;
}
.table-card :deep(.el-input__inner) {
  font-size: 12px;
  color: var(--sl-text-heading);
}

/* ── 锚点布局与导航样式 (Release 1.1 规范) ── */
.detail-anchor-layout { min-height: 0; background: #ffffff; }
.detail-anchor-menu {
  width: 148px;
  flex-shrink: 0;
  padding: 8px 6px;
  background: #ffffff;
  border-right: 1px solid var(--sl-border-base) !important;
}
.detail-anchor-menu :deep(.el-anchor__link) {
  margin-bottom: 2px;
  padding: 6px 8px;
  border-radius: 4px;
  color: var(--sl-text-body);
  font-size: 12px;
  font-weight: 500;
  transition: all 0.15s ease;
}
.detail-anchor-menu :deep(.el-anchor__link:hover) {
  background: #f1f5f9;
  color: var(--sl-text-heading);
}
.detail-anchor-menu :deep(.el-anchor__link.is-active) {
  background: #eff6ff;
  color: var(--sl-primary);
  font-weight: 600;
}
.anchor-section { scroll-margin-top: 8px; }
.editor-table { width: 100%; }
.editor-table :deep(.el-table__cell) { padding: 5px 6px; vertical-align: middle; }
.editor-table :deep(.cell) { line-height: 1.4; }
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
  border: none !important;
  box-shadow: none !important;
}

.stacked-lifecycle-table :deep(.el-table__header th) {
  background: #f8fafc !important;
  color: var(--sl-text-secondary);
  font-weight: 600;
  font-size: 11.5px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--sl-border-base);
}

.stacked-lifecycle-table :deep(.el-table__row td) {
  padding: 8px 10px;
  border-bottom: 1px solid var(--sl-border-base);
  vertical-align: top;
}

.stacked-lifecycle-table :deep(.el-table__row:hover td) {
  background-color: #f1f5f9 !important;
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

.termination-default-transition {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border: 1px solid #fde68a;
  border-radius: 5px;
  background: #fffbeb;
  color: #92400e;
  font-size: 11px;
  line-height: 1.4;
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
