<template>
  <div class="model-detail-view">
    <!-- 头部工具栏 -->
    <header class="model-view-header">
      <div class="header-left">
        <h2 class="model-title">{{ model.modelName }}</h2>
        <span class="model-subtitle">{{ model.categoryName || '未分类' }} · 更新于 {{ formatTime(model.updateTime) }}</span>
      </div>
      <div class="header-actions">
        <button class="btn-aliyun" type="button" @click="downloadModelBundle">
          <el-icon><Download /></el-icon>
          <span>导出模型文件</span>
        </button>
        <el-tooltip :disabled="!hasInstances" content="该模型下已有设备实例运行，已被锁定，禁止编辑" placement="top">
          <span>
            <button v-if="canEditModel" class="btn-aliyun-cta" type="button" :disabled="hasInstances" @click="openEditDrawer(model)">
              <el-icon><EditPen /></el-icon>
              <span>编辑模型</span>
            </button>
          </span>
        </el-tooltip>
        <el-popconfirm v-if="canDeleteModel && !hasInstances" title="确认删除该模型？有设备实例时不可删除。" @confirm="deleteModel(model.modelId)">
          <template #reference>
            <button class="btn-link danger" type="button" style="margin-left: 8px;">
              <span>删除模型</span>
            </button>
          </template>
        </el-popconfirm>
        <el-tooltip v-else-if="canDeleteModel && hasInstances" content="该模型下已有设备实例运行，已被锁定，禁止删除" placement="top">
          <span>
            <button class="btn-link danger" type="button" :disabled="true" style="margin-left: 8px; opacity: 0.5; cursor: not-allowed;">
              <span>删除模型</span>
            </button>
          </span>
        </el-tooltip>
      </div>
    </header>

    <!-- 顶部 35px 像素级高度锁定指标横幅 -->
    <div class="metric-ribbon">
      <div class="ribbon-items-flow">
        <div class="ribbon-cell">
          <span class="label">所属设备类别</span>
          <strong class="val">{{ model.categoryName || '-' }}</strong>
        </div>
        <div class="ribbon-cell">
          <span class="label">属性定义项数</span>
          <strong class="val highlight mono">{{ selectedAttributes.length }} 项</strong>
        </div>
        <div class="ribbon-cell">
          <span class="label">操作数</span>
          <strong class="val highlight mono">{{ selectedCapabilities.length }} 项</strong>
        </div>
        <div class="ribbon-cell">
          <span class="label">绑定 Adapter 驱动</span>
          <strong class="val mono">{{ model.adapterContract?.config?.adapterName || '-' }}</strong>
        </div>
      </div>
    </div>

    <!-- 主工作台：左侧 148px 锚点导航 + 右侧顶格全表格无界画卷 -->
    <div class="model-detail-workbench">
      <el-anchor
        class="detail-anchor-menu"
        @click="(e) => e.preventDefault()"
        container=".detail-scroll-content .el-scrollbar__wrap"
        :offset="10"
      >
        <el-anchor-link href="#view-basic" title="基础信息" />
        <el-anchor-link href="#view-ability" title="属性功能" />
        <el-anchor-link href="#view-adapter" title="Adapter 契约" />
        <el-anchor-link href="#view-mapping" title="映射关系" />
        <el-anchor-link href="#view-state" title="状态机" />
        <el-anchor-link href="#view-constraint" title="内置约束" />
        <el-anchor-link href="#view-bom" title="组件结构" />
        <el-anchor-link href="#view-template" title="默认数据模板" />
        <el-anchor-link href="#view-file" title="模型文件" />
      </el-anchor>

      <el-scrollbar class="detail-scroll-content">
        <!-- 01 基础信息 -->
        <section id="view-basic" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">01</span>
              <span class="sec-main-title">基础信息</span>
            </div>
          </div>
          <el-descriptions :column="2" border size="small" class="industrial-desc-table">
            <el-descriptions-item label="模型名称">
              <strong class="text-heading">{{ model.modelName || '-' }}</strong>
            </el-descriptions-item>
            <el-descriptions-item label="设备类别">{{ model.categoryName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="通信协议">
              <span class="mono-text text-primary">{{ model.adapterContract?.config?.protocol || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              <span class="mono-text">{{ formatTime(model.updateTime) }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <!-- 02 属性功能 -->
        <section id="view-ability" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">02</span>
              <span class="sec-main-title">属性功能定义</span>
            </div>
            <span class="sec-right-count">{{ selectedAttributes.length }} 属性 · {{ selectedCapabilities.length }} 操作 · {{ selectedPorts.length }} 端口</span>
          </div>

          <!-- 2.1 设备属性 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">设备属性</span>
              <span class="sub-count">共 {{ selectedAttributes.length }} 项</span>
            </div>
            <div v-if="selectedAttributes.length === 0" class="compact-empty">暂无设备属性定义</div>
            <el-table
              v-else
              :data="selectedAttributes"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="属性名称" min-width="160">
                <template #default="{ row }">
                  <span class="text-heading" style="font-weight: 500;">{{ row.displayName || row.attributeName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="属性标识" min-width="150">
                <template #default="{ row }">
                  <span class="mono-text text-secondary">{{ row.attributeName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="数据类型" width="120">
                <template #default="{ row }">
                  <span class="mono-text text-primary">{{ row.dataType || 'STRING' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="取值类型" width="120">
                <template #default="{ row }">
                  <span>{{ valueKindLabel(row.valueKind) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="单位" width="100">
                <template #default="{ row }">
                  <span class="mono-text">{{ row.unit || '-' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 2.2 设备操作 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">设备操作</span>
              <span class="sub-count">共 {{ selectedCapabilities.length }} 项</span>
            </div>
            <div v-if="selectedCapabilities.length === 0" class="compact-empty">暂无设备操作定义</div>
            <el-table
              v-else
              :data="selectedCapabilities"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="操作名称" min-width="160">
                <template #default="{ row }">
                  <span class="text-heading" style="font-weight: 500;">{{ row.displayName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作标识" min-width="140">
                <template #default="{ row }">
                  <span class="mono-text text-secondary">{{ row.capabilityName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作类型" width="120">
                <template #default="{ row }">
                  <span :class="row.isAbort ? 'tag tag-warning' : 'tag tag-primary'">
                    {{ row.isAbort ? '终止操作' : '普通操作' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="作用范围与终止目标" min-width="220">
                <template #default="{ row }">
                  <div v-if="row.isAbort" class="scope-tags">
                    <span class="text-secondary" style="font-size: 11px;">受影响操作:</span>
                    <span v-for="scope in asArray(row.scopeCapabilityKeys)" :key="scope" class="tag tag-gray" style="margin-left: 4px;">
                      {{ displayCapabilityName(scope, selectedCapabilities) }}
                    </span>
                  </div>
                  <div v-else-if="row.abortCapabilityKey || row.abortCapabilityName" class="scope-tags">
                    <span class="text-secondary" style="font-size: 11px;">指定终止操作:</span>
                    <span class="tag tag-warning" style="margin-left: 4px;">{{ displayCapabilityName(row.abortCapabilityKey || row.abortCapabilityName, selectedCapabilities) }}</span>
                  </div>
                  <span v-else class="text-secondary">-</span>
                </template>
              </el-table-column>
              <el-table-column label="输入参数" min-width="280">
                <template #default="{ row }">
                  <div v-if="row.parameters?.length" class="param-clean-flow">
                    <span v-for="(param, pIdx) in row.parameters" :key="param.name" class="param-clean-item">
                      <span class="mono-text text-heading">{{ param.displayName }}</span>
                      <span class="mono-text text-primary" style="margin-left: 3px;">{{ param.dataType }}</span>
                      <span v-if="pIdx < row.parameters.length - 1" class="param-clean-sep">·</span>
                    </span>
                  </div>
                  <span v-else class="text-secondary">无参数</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 2.3 端口配置 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">端口配置</span>
              <span class="sub-count">共 {{ selectedPorts.length }} 个端口</span>
            </div>
            <div v-if="selectedPorts.length === 0" class="compact-empty">暂无端口配置</div>
            <el-table
              v-else
              :data="selectedPorts"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="端口名称" min-width="180">
                <template #default="{ row }">
                  <span class="text-heading" style="font-weight: 500;">{{ row.portName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="方向" width="120">
                <template #default="{ row }">
                  <span class="text-primary">{{ directionLabel(row.direction) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="关联属性" min-width="200">
                <template #default="{ row }">
                  <span class="text-body">{{ displayAttributeName(row.bindingAttrName, selectedAttributes) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="说明" min-width="220">
                <template #default="{ row }">
                  <span>{{ row.description || '-' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <!-- 03 Adapter 契约 -->
        <section id="view-adapter" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">03</span>
              <span class="sec-main-title">Adapter 契约</span>
            </div>
            <span class="sec-right-count">{{ selectedAdapterCommands.length }} 命令 · {{ selectedAdapterAttributes.length }} 遥测 · {{ selectedAdapterEvents.length }} 事件</span>
          </div>

          <!-- 3.1 Adapter 命令 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">命令清单</span>
              <span class="sub-count">共 {{ selectedAdapterCommands.length }} 项</span>
            </div>
            <div v-if="selectedAdapterCommands.length === 0" class="compact-empty">暂无 Adapter 命令契约</div>
            <el-table
              v-else
              :data="selectedAdapterCommands"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="命令名称" min-width="160">
                <template #default="{ row }">
                  <span class="mono-text text-primary" style="font-weight: 500;">{{ row.commandName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="说明" min-width="160">
                <template #default="{ row }">
                  <span>{{ row.description || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="参数规格" min-width="320">
                <template #default="{ row }">
                  <div v-if="row.commandParameters?.length" class="param-clean-flow">
                    <span v-for="(param, pIdx) in row.commandParameters" :key="param.paramName" class="param-clean-item">
                      <span class="mono-text text-heading">{{ param.paramName }}</span>
                      <span class="mono-text text-primary" style="margin-left: 3px;">{{ param.dataType || '-' }}</span>
                      <span v-if="pIdx < row.commandParameters.length - 1" class="param-clean-sep">·</span>
                    </span>
                  </div>
                  <span v-else class="text-secondary">无参数</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 3.2 Adapter 遥测属性 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">遥测属性</span>
              <span class="sub-count">共 {{ selectedAdapterAttributes.length }} 项</span>
            </div>
            <div v-if="selectedAdapterAttributes.length === 0" class="compact-empty">暂无 Adapter 遥测属性契约</div>
            <el-table
              v-else
              :data="selectedAdapterAttributes"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="遥测属性标识" min-width="160">
                <template #default="{ row }">
                  <span class="mono-text text-heading">{{ row.telemetryName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="数据类型" width="120">
                <template #default="{ row }">
                  <span class="mono-text text-primary">{{ row.dataType || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="物理度量与说明" min-width="260">
                <template #default="{ row }">
                  <span>{{ row.description || '-' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 3.3 Adapter 事件 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">事件清单</span>
              <span class="sub-count">共 {{ selectedAdapterEvents.length }} 项</span>
            </div>
            <div v-if="selectedAdapterEvents.length === 0" class="compact-empty">暂无 Adapter 事件契约</div>
            <el-table
              v-else
              :data="selectedAdapterEvents"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="事件标识" min-width="180">
                <template #default="{ row }">
                  <span class="mono-text text-heading">{{ row.eventName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="事件分类" width="120">
                <template #default="{ row }">
                  <span :class="row.eventType === 'CMD' ? 'text-primary' : 'text-success'">
                    {{ row.eventType === 'CMD' ? '指令周期' : '业务事件' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="触发场景说明" min-width="260">
                <template #default="{ row }">
                  <span>{{ row.description || eventTypeLabel(row.eventType) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <!-- 04 映射关系 -->
        <section id="view-mapping" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">04</span>
              <span class="sec-main-title">映射关系</span>
            </div>
            <span class="sec-right-count">{{ selectedAttributeMappings.length }} 属性映射 · {{ functionMappingGroups(selectedCapabilities).length }} 功能映射</span>
          </div>

          <!-- 4.1 属性映射 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">属性映射</span>
              <span class="sub-count">共 {{ selectedAttributeMappings.length }} 项</span>
            </div>
            <div v-if="selectedAttributeMappings.length === 0" class="compact-empty">暂无属性映射</div>
            <el-table
              v-else
              :data="selectedAttributeMappings"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="模型逻辑属性" min-width="220">
                <template #default="{ row }">
                  <span class="text-heading" style="font-weight: 500;">{{ displayAttributeName(row.modelAttributeName, selectedAttributes) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="Adapter 物理遥测属性" min-width="220">
                <template #default="{ row }">
                  <span class="mono-text text-primary" style="font-weight: 500;">{{ row.adapterAttrName || '-' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 4.2 功能映射 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">功能映射</span>
              <span class="sub-count">共 {{ functionMappingGroups(selectedCapabilities).length }} 项</span>
            </div>
            <div v-if="functionMappingGroups(selectedCapabilities).length === 0" class="compact-empty">暂无功能映射</div>
            <el-table
              v-else
              :data="functionMappingGroups(selectedCapabilities)"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="功能操作名称" min-width="160">
                <template #default="{ row }">
                  <span class="text-heading" style="font-weight: 500;">{{ row.capabilityDisplayName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="Adapter 目标命令" min-width="160">
                <template #default="{ row }">
                  <span class="mono-text text-primary" style="font-weight: 500;">{{ row.adapterCommandName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="参数映射规则" min-width="320">
                <template #default="{ row }">
                  <div v-if="row.parameters?.length" class="param-clean-flow">
                    <span v-for="(param, pIdx) in row.parameters" :key="param.key" class="param-clean-item">
                      <span v-if="param.isFixedValue" class="mono-text text-success">固定值: {{ param.fixedValue }}</span>
                      <span v-else class="mono-text text-heading">{{ param.capabilityParamDisplayName }}</span>
                      <span class="mono-text text-secondary" style="margin: 0 4px; font-weight: 700;">→</span>
                      <span class="mono-text text-primary">{{ param.commandParamName }}</span>
                      <span v-if="pIdx < row.parameters.length - 1" class="param-clean-sep" style="margin: 0 6px;">·</span>
                    </span>
                  </div>
                  <span v-else class="text-secondary">未配置参数映射</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <!-- 05 状态机 -->
        <section id="view-state" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">05</span>
              <span class="sec-main-title">状态机</span>
            </div>
            <span class="sec-right-count">{{ selectedStateMachineInterfaces.length }} 接口 · {{ selectedCmdStates.length }} 指令状态 · {{ selectedOpStateRegions.length }} 功能分区 · {{ selectedStateTransitions.length }} 转移规则</span>
          </div>

          <!-- 5.1 接口定义 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">5.1 接口定义</span>
              <span class="sub-count">共 {{ selectedStateMachineInterfaces.length }} 个接口</span>
            </div>
            <div v-if="selectedStateMachineInterfaces.length === 0" class="compact-empty">暂无状态机接口定义</div>
            <el-table
              v-else
              :data="selectedStateMachineInterfaces"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="接口标识" min-width="200">
                <template #default="{ row }">
                  <strong class="mono-text text-heading">{{ row.name }}</strong>
                </template>
              </el-table-column>
              <el-table-column label="方向" width="100">
                <template #default="{ row }">
                  <span class="text-primary">{{ directionLabel(row.direction) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="接口类型" width="130">
                <template #default="{ row }">
                  <span class="mono-text text-heading">{{ row.interfaceType || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="允许流通信号" min-width="300">
                <template #default="{ row }">
                  <div v-if="asArray(row.allowedSignals).length" class="signal-tags-wrap">
                    <span v-for="sig in asArray(row.allowedSignals)" :key="sig" class="signal-tag-pill mono-text">{{ sig }}</span>
                  </div>
                  <span v-else class="text-secondary">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 5.2 指令生命周期 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">5.2 指令生命周期</span>
              <span class="sub-count">共 {{ selectedCmdStates.length }} 个状态</span>
            </div>
            <div v-if="selectedCmdStates.length === 0" class="compact-empty">暂无指令生命周期状态</div>
            <el-table
              v-else
              :data="selectedCmdStates"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="状态标识" min-width="150">
                <template #default="{ row }">
                  <span class="tag tag-gray">{{ row.stateName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="初始状态" width="120">
                <template #default="{ row }">
                  <span v-if="row.stateName === 'IDLE'" class="text-primary" style="font-weight: 500;">初始状态</span>
                  <span v-else class="text-secondary">-</span>
                </template>
              </el-table-column>
              <el-table-column label="进入动作" min-width="260">
                <template #default="{ row }">
                  <div v-if="row.onEntry?.length" style="display: flex; flex-wrap: wrap; gap: 6px;">
                    <div v-for="(act, aIdx) in row.onEntry" :key="aIdx" class="action-display-chip">
                      <span class="action-badge-verb">{{ getActionParts(act).verb }}</span>
                      <span class="action-target-sig">{{ getActionParts(act).signal }}</span>
                      <span class="action-sep">➔</span>
                      <span class="action-target-iface">{{ getActionParts(act).iface }}</span>
                    </div>
                  </div>
                  <div v-else class="action-display-chip">
                    <span class="action-badge-verb">SEND</span>
                    <span class="action-target-sig">CMD_STATE</span>
                    <span class="action-sep">➔</span>
                    <span class="action-target-iface">Interface_state_out</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 5.3 功能分区 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">5.3 功能分区</span>
              <span class="sub-count">共 {{ selectedOpStateRegions.length }} 个分区</span>
            </div>
            <div v-if="selectedOpStateRegions.length === 0" class="compact-empty">暂无功能状态分区</div>
            <el-table
              v-else
              :data="selectedOpStateRegions"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="分区名称" min-width="150">
                <template #default="{ row }">
                  <strong class="mono-text text-heading">{{ row.regionName }}</strong>
                </template>
              </el-table-column>
              <el-table-column label="分区类型" width="120">
                <template #default="{ row }">
                  <span :class="row.regionType === 'EXCEPTION' ? 'tag tag-danger' : 'tag tag-success'">
                    {{ row.regionType === 'EXCEPTION' ? '异常区域' : '功能区域' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="初始状态" width="130">
                <template #default="{ row }">
                  <span v-if="row.initialStateName" class="tag" :class="stateTagClass(row.initialStateName)">{{ row.initialStateName }}</span>
                  <span v-else class="text-secondary">-</span>
                </template>
              </el-table-column>
              <el-table-column label="包含状态列表" min-width="240">
                <template #default="{ row }">
                  <div class="param-tags-wrap" style="display: flex; flex-wrap: wrap; gap: 4px;">
                    <span v-for="state in asArray(row.states)" :key="state.stateName" class="tag" :class="stateTagClass(state.stateName)">
                      {{ state.stateName }}
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="进入动作" min-width="260">
                <template #default>
                  <div class="action-display-chip">
                    <span class="action-badge-verb">SEND</span>
                    <span class="action-target-sig">OP_STATE</span>
                    <span class="action-sep">➔</span>
                    <span class="action-target-iface">Interface_state_out</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 5.4 指令转移规则 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">5.4 指令转移规则</span>
              <span class="sub-count">共 {{ commandLifecycleTransitions.length }} 条</span>
            </div>
            <div v-if="commandLifecycleTransitions.length === 0" class="compact-empty">暂无指令生命周期规则</div>
            <el-table
              v-else
              :data="commandLifecycleTransitions"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="规则描述" min-width="200">
                <template #default="{ row }">
                  <span class="text-heading">{{ row.description || '指令生命周期流转' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态转移" min-width="200">
                <template #default="{ row }">
                  <span class="tag tag-gray">{{ row.fromStateName || '-' }}</span>
                  <span class="mono-text text-secondary" style="margin: 0 6px; font-weight: 700;">→</span>
                  <span class="tag tag-gray">{{ row.toStateName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="触发接口信号" min-width="240">
                <template #default="{ row }">
                  <div class="trigger-cell-list">
                    <div v-for="(trig, tIdx) in getTriggerList(row)" :key="tIdx" class="trigger-cell-item">
                      <div class="trigger-iface-line">{{ trig.interfaceName || '-' }}</div>
                      <div class="trigger-signal-line">{{ trig.signalName || '-' }}</div>
                    </div>
                    <span v-if="!getTriggerList(row).length" class="text-secondary">-</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="过程转移动作" min-width="260">
                <template #default="{ row }">
                  <div v-if="row.actions?.length" style="display: flex; flex-wrap: wrap; gap: 6px;">
                    <div v-for="(act, aIdx) in row.actions" :key="aIdx" class="action-display-chip">
                      <span class="action-badge-verb">{{ getActionParts(act).verb }}</span>
                      <span class="action-target-sig">{{ getActionParts(act).signal }}</span>
                      <span class="action-sep">➔</span>
                      <span class="action-target-iface">{{ getActionParts(act).iface }}</span>
                    </div>
                  </div>
                  <span v-else class="text-secondary">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 5.5 业务状态转移规则 -->
          <div class="flat-sub-table">
            <div class="sub-section-title-bar">
              <span class="sub-title">5.5 业务状态转移规则</span>
              <span class="sub-count">共 {{ operationStateTransitions.length }} 条</span>
            </div>
            <div v-if="operationStateTransitions.length === 0" class="compact-empty">暂无业务功能状态转移规则</div>
            <el-table
              v-else
              :data="operationStateTransitions"
              stripe
              size="small"
              class="industrial-fullbleed-table"
            >
              <el-table-column label="规则描述" min-width="180">
                <template #default="{ row }">
                  <span class="text-heading">{{ row.description || '业务状态转移规则' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="所属分区" width="130">
                <template #default="{ row }">
                  <span class="text-heading">{{ row.regionName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态转移" min-width="200">
                <template #default="{ row }">
                  <span class="tag" :class="stateTagClass(row.fromStateName)">{{ row.fromStateName || '-' }}</span>
                  <span class="mono-text text-secondary" style="margin: 0 6px; font-weight: 700;">→</span>
                  <span class="tag" :class="stateTagClass(row.toStateName)">{{ row.toStateName || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="触发接口信号" min-width="240">
                <template #default="{ row }">
                  <div class="trigger-cell-list">
                    <div v-for="(trig, tIdx) in getTriggerList(row)" :key="tIdx" class="trigger-cell-item">
                      <div class="trigger-iface-line">{{ trig.interfaceName || '-' }}</div>
                      <div class="trigger-signal-line">{{ trig.signalName || '-' }}</div>
                    </div>
                    <span v-if="!getTriggerList(row).length" class="text-secondary">-</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="过程转移动作" min-width="260">
                <template #default="{ row }">
                  <div v-if="row.actions?.length" style="display: flex; flex-wrap: wrap; gap: 6px;">
                    <div v-for="(act, aIdx) in row.actions" :key="aIdx" class="action-display-chip">
                      <span class="action-badge-verb">{{ getActionParts(act).verb }}</span>
                      <span class="action-target-sig">{{ getActionParts(act).signal }}</span>
                      <span class="action-sep">➔</span>
                      <span class="action-target-iface">{{ getActionParts(act).iface }}</span>
                    </div>
                  </div>
                  <span v-else class="text-secondary">-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <!-- 06 内置约束 -->
        <section id="view-constraint" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">06</span>
              <span class="sec-main-title">内置约束</span>
            </div>
            <span class="sec-right-count">共 {{ selectedConstraints.length }} 条</span>
          </div>
          <div v-if="selectedConstraints.length === 0" class="compact-empty">暂无内置约束</div>
          <el-table
            v-else
            :data="selectedConstraints"
            stripe
            size="small"
            class="industrial-fullbleed-table"
          >
            <el-table-column label="目标属性" min-width="180">
              <template #default="{ row }">
                <span class="text-heading" style="font-weight: 500;">{{ displayAttributeName(row.objectAttributeName, selectedAttributes) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="判定条件" min-width="160">
              <template #default="{ row }">
                <span class="text-primary">{{ operatorLabel(row.operator) }} <span class="mono-text">{{ row.boundaryValue }}</span></span>
              </template>
            </el-table-column>
            <el-table-column label="违规状态触发" min-width="180">
              <template #default="{ row }">
                <span class="text-danger" style="font-weight: 500;">{{ row.violationStateName || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- 07 组件结构 -->
        <section id="view-bom" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">07</span>
              <span class="sec-main-title">组件结构清单</span>
            </div>
            <span class="sec-right-count">共 {{ selectedComponentsBom.length }} 项</span>
          </div>
          <div v-if="selectedComponentsBom.length === 0" class="compact-empty">暂无组件结构清单</div>
          <el-table
            v-else
            :data="selectedComponentsBom"
            stripe
            size="small"
            class="industrial-fullbleed-table"
          >
            <el-table-column label="组件名称" min-width="180">
              <template #default="{ row }">
                <span class="text-heading" style="font-weight: 500;">{{ row.slotName || row.componentName || row.name || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="设备类别" min-width="150">
              <template #default="{ row }">
                <span>{{ row.categoryName || categoryNameById(row.categoryId) || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="100">
              <template #default="{ row }">
                <span class="mono-text">{{ row.quantity || 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="描述" min-width="220">
              <template #default="{ row }">
                <span>{{ row.description || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- 08 默认数据模板 -->
        <section id="view-template" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">08</span>
              <span class="sec-main-title">默认数据模板字段</span>
            </div>
            <span class="sec-right-count">共 {{ defaultTemplateAttributes.length }} 项</span>
          </div>
          <div v-if="defaultTemplateAttributes.length === 0" class="compact-empty">暂无默认数据模板配置</div>
          <el-table
            v-else
            :data="defaultTemplateAttributes"
            stripe
            size="small"
            class="industrial-fullbleed-table"
          >
            <el-table-column label="字段名称" min-width="180">
              <template #default="{ row }">
                <span class="text-heading" style="font-weight: 500;">{{ row.displayName || row.attributeName || row.name || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数据类型" width="140">
              <template #default="{ row }">
                <span class="mono-text text-primary">{{ row.dataType || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单位" width="120">
              <template #default="{ row }">
                <span class="mono-text">{{ row.unit || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- 09 模型文件 -->
        <section id="view-file" class="anchor-section industrial-section">
          <div class="section-heading-bar">
            <div class="heading-left">
              <span class="sec-index-badge">09</span>
              <span class="sec-main-title">模型文件</span>
            </div>
            <button class="btn-aliyun" type="button" @click="emit('download')">
              <el-icon><Download /></el-icon><span>导出模型文件</span>
            </button>
          </div>
          <div class="model-json-grid">
            <div class="json-panel">
              <div class="section-title"><h3>设备能力模型</h3></div>
              <pre>{{ formatJson(capabilityModelJson) }}</pre>
            </div>
            <div class="json-panel">
              <div class="section-title"><h3>设备状态机模型</h3></div>
              <pre>{{ formatJson(stateMachineModelJson) }}</pre>
            </div>
          </div>
        </section>
      </el-scrollbar>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Download, EditPen } from '@element-plus/icons-vue'
import {
  asArray, formatTime, formatJson,
  valueKindLabel, directionLabel, describeAction, operatorLabel, eventTypeLabel,
  interfaceRoleLabel, functionMappingGroups, displayAttributeName,
  normalizeEventsToFlatList
} from './normalizers.js'

const props = defineProps({
  model: Object,
  modelBundle: Object,
  defaultTemplateAttributes: Array,
  categories: Array,
  canEditModel: Boolean,
  canDeleteModel: Boolean,
  hasInstances: Boolean,
})

const emit = defineEmits(['edit', 'delete', 'download'])

function downloadModelBundle() { emit('download') }
function openEditDrawer(model) { emit('edit', model) }
function deleteModel(id) { emit('delete', id) }
function categoryNameById(id) {
  return props.categories.find(item => String(item.id) === String(id))?.categoryName || ''
}

function cmdStateDescription(stateName) {
  const map = {
    IDLE: '待机就绪状态，准备接收来自工作流或控制台的启动指令',
    SENT: '指令已下发，过程动作已向适配器发出启动报文，等待驱动确认',
    RUNNING: '底层硬件已确认开始执行，正在持续推进业务操作',
    COMPLETED: '指令正常执行结束，硬件完成业务操作闭环',
    FAILED: '指令执行失败，底层驱动报告错误中断',
    ABORTING: '中止请求中，过程动作已向适配器发出中止报文，等待硬件刹车',
    ABORTED: '指令已彻底中止完成，系统安全复位'
  }
  return map[stateName] || '指令执行阶段状态'
}

const selectedAttributes = computed(() => asArray(props.model?.attributes))
const selectedCapabilities = computed(() => asArray(props.model?.capabilities))
const selectedPorts = computed(() => asArray(props.model?.ports))
const selectedAdapterCommands = computed(() => asArray(props.model?.adapterContract?.commands))
const selectedAdapterAttributes = computed(() => asArray(props.model?.adapterContract?.telemetry?.adapterAttributes))
const selectedAdapterEvents = computed(() => normalizeEventsToFlatList(props.model?.adapterContract?.events))
const selectedAttributeMappings = computed(() => asArray(props.model?.adapterContract?.telemetry?.attributesMapping))

const capabilityModelJson = computed(() => props.modelBundle?.capabilityModel || {})
const stateMachineModelJson = computed(() => props.modelBundle?.stateMachineModel || {})

const selectedStateMachineInterfaces = computed(() => {
  const bundleInterfaces = asArray(stateMachineModelJson.value?.interfaces)
  if (bundleInterfaces.length > 0) return bundleInterfaces
  const modelInterfaces = asArray(props.model?.stateMachineInterfaces)
  if (modelInterfaces.length > 0) return modelInterfaces
  return []
})

const selectedCmdStates = computed(() => {
  const modelStates = asArray(props.model?.cmdState?.states)
  if (modelStates.length > 0) return modelStates
  const bundleStates = asArray(stateMachineModelJson.value?.cmdLifecycleSpace?.states)
  if (bundleStates.length > 0) return bundleStates
  return []
})

const selectedOpStateRegions = computed(() => {
  const modelRegions = asArray(props.model?.opState?.regions)
  if (modelRegions.length > 0) return modelRegions
  const bundleRegions = asArray(stateMachineModelJson.value?.opStateSpace?.regions)
  if (bundleRegions.length > 0) return bundleRegions
  return []
})

const selectedConstraints = computed(() => asArray(props.model?.intrinsicConstraints))
const selectedComponentsBom = computed(() => asArray(props.model?.componentsBom))
const selectedStateTransitions = computed(() => asArray(stateMachineModelJson.value?.transitions))

const commandLifecycleTransitions = computed(() => {
  return asArray(selectedStateTransitions.value).filter(row => row.stateSpace === 'CMD')
})

const operationStateTransitions = computed(() => {
  return asArray(selectedStateTransitions.value).filter(row => row.stateSpace === 'OP')
})

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

function getActionParts(act) {
  if (!act) return { verb: 'SEND', iface: 'Interface_adapter_out', signal: 'CMD_START' }
  const verb = String(act.actionName || act.action || 'SEND').toUpperCase()
  const payload = act.payload || act.parameters || {}
  const iface = payload.interfaceName || act.interfaceName || 'Interface_adapter_out'
  const sig = payload.signalName || act.signalName || payload.signal || 'CMD_START'
  return { verb, iface, signal: sig }
}

function getTriggerList(row) {
  if (Array.isArray(row.triggers) && row.triggers.length > 0) return row.triggers
  if (row.trigger && (row.trigger.interfaceName || row.trigger.signalName)) return [row.trigger]
  return []
}
</script>

<style scoped>
/* 触发机制双行排版 */
.trigger-cell-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.trigger-cell-item {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.trigger-iface-line {
  font-family: var(--sl-font-mono, monospace);
  font-size: 11px;
  color: #64748b;
  line-height: 1.2;
}
.trigger-signal-line {
  font-family: var(--sl-font-mono, monospace);
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}

/* 结构化转移动作与进入动作芯片 */
.action-display-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 1px 7px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  line-height: 1.4;
  vertical-align: middle;
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
.action-target-sig {
  font-family: var(--sl-font-mono, monospace);
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
}
.action-sep {
  color: #94a3b8;
  font-weight: 700;
  font-size: 11px;
}
.action-target-iface {
  font-family: var(--sl-font-mono, monospace);
  font-size: 11px;
  color: #475569;
}

.model-detail-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #ffffff;
}

/* 顶部视图头 */
.model-view-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.model-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--sl-text-heading, #0f172a);
  line-height: 1.3;
}
.model-subtitle {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 顶部 35px 像素级锁定指标横幅 */
.metric-ribbon {
  height: 35px;
  max-height: 35px;
  padding: 0 16px;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.ribbon-items-flow {
  display: flex;
  align-items: center;
  gap: 24px;
}
.ribbon-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  white-space: nowrap;
}
.ribbon-cell .label {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  line-height: 1;
}
.ribbon-cell .val {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  line-height: 1;
}
.ribbon-cell .val.highlight {
  color: var(--sl-primary, #2563eb);
}
.ribbon-cell .val.mono {
  font-family: var(--sl-font-mono, monospace);
}

/* 工作台主体 */
.model-detail-workbench {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
  background: #ffffff;
}

/* 锚点导航 */
.detail-anchor-menu {
  width: 148px;
  flex-shrink: 0;
  padding: 8px 6px;
  background: #ffffff;
  border-right: 1px solid var(--sl-border-base, #e2e8f0) !important;
}
.detail-anchor-menu :deep(.el-anchor__link) {
  margin-bottom: 2px;
  padding: 6px 8px;
  border-radius: var(--sl-radius-sm, 6px);
  color: var(--sl-text-body, #334155);
  font-size: 12px;
  font-weight: 500;
  transition: all 0.15s ease;
}
.detail-anchor-menu :deep(.el-anchor__link:hover) {
  background: #f1f5f9;
}
.detail-anchor-menu :deep(.el-anchor__link.is-active) {
  background: #eff6ff;
  color: var(--sl-primary, #2563eb);
  font-weight: 600;
}

.detail-scroll-content {
  flex: 1;
  min-width: 0;
}
.detail-scroll-content :deep(.el-scrollbar__view) {
  padding: 0;
}

.anchor-section {
  scroll-margin-top: 0;
}
.industrial-section {
  display: flex;
  flex-direction: column;
  width: 100%;
}

/* 一级章节标题条 (Section Heading Bar) */
.section-heading-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 16px;
  background: #f8fafc;
  border-top: 1px solid var(--sl-border-base, #e2e8f0);
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  flex-shrink: 0;
}
.industrial-section:first-child .section-heading-bar {
  border-top: none;
}
.heading-left {
  display: flex;
  align-items: center;
}
.sec-index-badge {
  display: inline-flex;
  width: 22px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--sl-primary-border, #bfdbfe);
  border-radius: 3px;
  background: var(--sl-primary-light, #eff6ff);
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  font-weight: 700;
  font-family: var(--sl-font-mono, monospace);
  margin-right: 8px;
}
.sec-main-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}
.sec-right-count {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  font-family: var(--sl-font-mono, monospace);
}

/* 二级子表容器与标题条 */
.flat-sub-table {
  display: flex;
  flex-direction: column;
  width: 100%;
}
.sub-section-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 32px;
  padding: 0 16px;
  background: #ffffff;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  flex-shrink: 0;
}
.flat-sub-table:not(:first-of-type) .sub-section-title-bar {
  border-top: 1px solid var(--sl-border-base, #e2e8f0);
}
.sub-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  line-height: 1;
  display: inline-flex;
  align-items: center;
}
.sub-count {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
  line-height: 1;
}

/* 顶格全幅表格 */
.industrial-fullbleed-table {
  width: 100%;
  border-radius: 0;
}
.industrial-fullbleed-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.industrial-fullbleed-table :deep(th.el-table__cell) {
  background: #ffffff !important;
  color: var(--sl-text-secondary, #64748b) !important;
  font-size: 11.5px !important;
  font-weight: 600 !important;
  padding: 8px 16px !important;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0) !important;
  vertical-align: middle !important;
}
.industrial-fullbleed-table :deep(td.el-table__cell) {
  padding: 8px 16px !important;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9) !important;
  color: var(--sl-text-body, #334155) !important;
  font-size: 12.5px !important;
  font-family: var(--sl-font-family) !important;
  vertical-align: middle !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.industrial-fullbleed-table :deep(.cell) {
  display: flex;
  align-items: center;
  line-height: 1.4;
}

.industrial-desc-table {
  width: 100%;
  border-radius: 0;
}
.industrial-desc-table :deep(.el-descriptions__body) {
  border-radius: 0;
}
.industrial-desc-table :deep(.el-descriptions__cell) {
  padding: 8px 16px !important;
  font-size: 12.5px !important;
  font-family: var(--sl-font-family) !important;
  vertical-align: middle !important;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.industrial-desc-table :deep(.el-descriptions__label) {
  background: #f8fafc !important;
  color: var(--sl-text-secondary, #64748b) !important;
  font-weight: 500 !important;
  width: 140px;
}
.industrial-desc-table :deep(.el-descriptions__content) {
  color: var(--sl-text-body, #334155) !important;
}

.state-token-clean-row {
  padding: 10px 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}
.state-clean-item {
  display: inline-flex;
  align-items: center;
}

/* 纯净参数流 */
.param-clean-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 4px;
}
.param-clean-item {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
}
.param-clean-sep {
  color: var(--sl-text-secondary, #94a3b8);
  margin-left: 6px;
  font-weight: 600;
}

/* JSON 面板 (与抽屉保持一致的暗色高对比度黑底代码框) */
.model-json-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
}
.json-panel {
  min-width: 0;
  border: 1px solid #dfe4ed;
  border-radius: 6px;
  padding: 12px;
  background: #ffffff;
}
.json-panel .section-title {
  margin-bottom: 8px;
}
.json-panel .section-title h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
}
.json-panel pre {
  margin: 0;
  max-height: 560px;
  overflow: auto;
  padding: 12px;
  border-radius: 6px;
  background: #111827;
  color: #e5e7eb;
  font-size: 12px;
  line-height: 1.55;
  tab-size: 2;
  font-family: var(--sl-font-mono, monospace);
}

.compact-empty {
  padding: 20px 16px;
  text-align: center;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
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

/* 文本色彩体系 */
.mono-text {
  font-family: var(--sl-font-mono, monospace);
  font-size: 12px;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.text-heading {
  color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-family);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.text-body {
  color: var(--sl-text-body, #334155);
  font-family: var(--sl-font-family);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.text-secondary { color: var(--sl-text-secondary, #64748b); }
.text-primary { color: var(--sl-primary, #2563eb); }
.text-success { color: var(--sl-success, #16a34a); }
.text-danger { color: var(--sl-danger, #dc2626); }
.text-purple { color: #6366f1; }

/* 四级按钮 */
.btn-aliyun-cta {
  height: 28px;
  padding: 0 12px;
  background: #ffffff;
  color: var(--sl-primary, #2563eb);
  border: 1px solid var(--sl-primary-border, #bfdbfe);
  border-radius: var(--sl-radius-sm, 6px);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.18s ease;
}
.btn-aliyun-cta:hover {
  background: var(--sl-primary-light, #eff6ff);
  border-color: var(--sl-primary, #2563eb);
}
.btn-aliyun-cta:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-aliyun {
  height: 28px;
  padding: 0 10px;
  background: #ffffff;
  color: var(--sl-text-body, #334155);
  border: 1px solid var(--sl-border-input, #cbd5e1);
  border-radius: var(--sl-radius-sm, 6px);
  font-size: 12px;
  font-weight: 400;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: all 0.18s ease;
}
.btn-aliyun:hover {
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
  background: var(--sl-bg-hover, #f8fafc);
}

.btn-link {
  background: transparent;
  border: none;
  color: var(--sl-primary, #2563eb);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.btn-link:hover {
  text-decoration: underline;
}
.btn-link.danger {
  color: var(--sl-danger, #dc2626);
}
</style>
