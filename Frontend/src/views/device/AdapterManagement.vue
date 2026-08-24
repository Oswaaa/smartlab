<template>
  <div class="adapter-management-page">
    <div class="adapter-workbench-canvas">

      <!-- =========================================================================
           第 1 栏：Adapter 驱动列表栏 (270px，对齐数据中心资产树)
           ========================================================================= -->
      <aside class="adapter-sidebar sl-asset-tree">
        <div class="tree-header-bar">
          <div class="tree-header-left">
            <strong class="tree-header-title">Adapter 驱动</strong>
            <span class="tree-header-count">{{ filteredAdapters.length }} 个驱动</span>
          </div>
          <span
            class="broker-status-pill"
            :class="mqttConnected ? 'online' : 'offline'"
            :title="mqttConnected ? '系统 MQTT Broker 在线' : '系统 MQTT Broker 未连接，点击重连'"
            @click="!mqttConnected && reconnectMqtt()"
          >
            <span class="dot"></span>
            <span>{{ mqttConnected ? 'Broker 已连接' : 'Broker 离线' }}</span>
          </span>
        </div>

        <div class="tree-search-bar">
          <input
            v-model="keyword"
            class="tree-search-input"
            placeholder="搜索 Adapter 名称..."
          />
          <div class="status-radio-row">
            <el-radio-group v-model="statusFilter" size="small" class="status-filter-group">
              <el-radio-button value="all">全部</el-radio-button>
              <el-radio-button value="online">在线</el-radio-button>
              <el-radio-button value="stale">异常</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <div class="tree-list-scroll" v-loading="loading">
          <div v-if="filteredAdapters.length === 0" class="tree-empty">暂无匹配驱动</div>
          <div
            v-for="item in filteredAdapters"
            :key="adapterIdOf(item)"
            class="t-row"
            :class="{ active: activeKey === adapterIdOf(item) }"
            @click="handleSelectAdapter(adapterIdOf(item))"
          >
            <div class="t-row-left">
              <span class="m-val status-val" :class="runtimeStatusClass(item)">
                <span class="dot"></span>
              </span>
              <span class="t-label" :title="item.adapterName">{{ item.adapterName || '未命名 Adapter' }}</span>
            </div>
            <div class="t-row-right">
              <span class="t-badge">{{ categoryCount(item) }}/{{ pointCount(item) }}</span>
            </div>
          </div>
        </div>

        <div class="sidebar-foot">
          <button
            class="btn-aliyun-cta"
            style="width: 100%; justify-content: center;"
            type="button"
            @click="openRegisterDrawer(pendingRegistrations.length ? 'mqtt' : 'manual')"
          >
            <el-icon><Plus /></el-icon>
            <span>注册 Adapter</span>
            <span v-if="pendingRegistrations.length" class="count-badge">({{ pendingRegistrations.length }})</span>
          </button>
        </div>
      </aside>

      <!-- =========================================================================
           第 2 栏：当前 Adapter 的设备类别导航栏 (统一为设备模型页面风格)
           ========================================================================= -->
      <aside v-if="activeAdapter" class="category-nav-col sl-asset-tree">
        <div class="tree-header-bar">
          <div class="tree-header-left">
            <strong class="tree-header-title">设备类别</strong>
            <span class="tree-header-count">{{ activeCategories.length }} 个类别</span>
          </div>
        </div>

        <div class="tree-list-scroll">
          <div v-if="activeCategories.length === 0" class="tree-empty">该驱动无类别定义</div>
          <div
            v-for="(cat, idx) in activeCategories"
            :key="cat.categoryName || idx"
            class="t-row node-type-category"
            :class="{ active: selectedCategoryIndex === idx }"
            @click="selectedCategoryIndex = idx"
          >
            <div class="t-row-left">
              <el-icon class="t-icon category-icon"><Folder /></el-icon>
              <span class="t-label" :title="cat.categoryDescription || cat.categoryName">{{ cat.categoryName || '未命名类别' }}</span>
            </div>
            <div class="t-row-right">
              <span class="t-badge">{{ asArray(cat.devicePoints).length }}</span>
            </div>
          </div>
        </div>
      </aside>

      <!-- =========================================================================
           第 3 栏：右侧工作台主画卷 (上半部：Adapter 概览与按钮；下半部：纯净顶格全表格)
           ========================================================================= -->
      <main v-if="activeAdapter" class="detail-workbench-pane">

        <!-- ─── 上半部分：Adapter 驱动总览头与全功能操作工具栏 ─── -->
        <header class="workbench-top-header">
          <div class="top-header-main">
            <div class="adapter-title-group">
              <h2 class="adapter-main-title">{{ activeAdapter.adapterName }}</h2>
              <span class="adapter-subtitle">
                {{ activeAdapter.description || activeAdapter.parsedConfig?.adapterDescription || '物理驱动' }} · {{ activeAdapter?.parsedConfig?.registerMeta?.rawConfigFormat || 'INI' }} 格式
              </span>
            </div>

            <!-- 全量操作按钮平铺直达 -->
            <div class="adapter-action-toolbar">
              <button class="btn-aliyun" type="button" @click="configDrawerVisible = true">
                <el-icon><Document /></el-icon>
                <span>驱动配置对比</span>
              </button>
              <button class="btn-aliyun" type="button" @click="exportConfigJson">
                <el-icon><Download /></el-icon>
                <span>导出配置</span>
              </button>
              <button class="btn-aliyun-cta" type="button" @click="openRegisterDrawer('manual', activeAdapter)">
                <el-icon><EditPen /></el-icon>
                <span>更新配置</span>
              </button>
              <button
                class="btn-aliyun"
                :class="isAdapterEnabled(activeAdapter) ? 'btn-warning' : 'btn-success'"
                type="button"
                @click="toggleAdapterStatus(activeAdapter)"
              >
                <el-icon><VideoPause v-if="isAdapterEnabled(activeAdapter)" /><VideoPlay v-else /></el-icon>
                <span>{{ isAdapterEnabled(activeAdapter) ? '停用驱动' : '启用驱动' }}</span>
              </button>
              <button v-if="!mqttConnected" class="btn-aliyun" type="button" @click="reconnectMqtt">
                <el-icon><Connection /></el-icon>
                <span>重连 Broker</span>
              </button>
              <button class="btn-link danger" type="button" style="margin-left: 6px;" @click="deleteAdapter(activeAdapter)">
                <span>删除驱动</span>
              </button>
            </div>
          </div>

          <!-- 35px 像素级高度锁定指标横幅 -->
          <div class="metric-ribbon">
            <div class="ribbon-items-flow">
              <div class="ribbon-cell" title="物理驱动实时网络通信状态">
                <span class="label">通信状态</span>
                <span class="m-val status-val" :class="runtimeStatusClass(activeAdapter)">
                  <span class="dot"></span>{{ runtimeStatusLabel(activeAdapter) }}
                </span>
              </div>
              <div class="ribbon-cell">
                <span class="label">最后心跳</span>
                <span class="val mono">{{ formatTime(activeAdapter.lastHeartbeat) }}</span>
              </div>
              <div class="ribbon-cell">
                <span class="label">管理状态</span>
                <span class="val" :class="isAdapterEnabled(activeAdapter) ? 'text-success' : 'text-danger'">
                  {{ isAdapterEnabled(activeAdapter) ? '启用中' : '已停用' }}
                </span>
              </div>
              <div class="ribbon-cell">
                <span class="label">资源统计</span>
                <span class="val">
                  {{ activeCategories.length }} 类别 · {{ pointCount(activeAdapter) }} 点位 · {{ boundInstances.length }} 绑定
                </span>
              </div>
            </div>
          </div>
        </header>

        <!-- ─── 下半部分：设备类别具体配置 (顶格全表格，无背景框) ─── -->
        <div class="workbench-bottom-section">
          <!-- 36px 纯中文 SubTab 导航条 -->
          <nav class="subtabs-bar">
            <button
              type="button"
              class="subtab-btn"
              :class="{ active: activeSubTab === 'capabilities' }"
              @click="activeSubTab = 'capabilities'"
            >
              <span>Adapter 契约</span>
              <span class="tab-count-text">({{ asArray(currentCategory?.deviceTemplate?.attributes).length }} 遥测 · {{ asArray(currentCategory?.deviceTemplate?.commands).length }} 指令 · {{ eventCount(currentCategory?.deviceTemplate?.events) }} 事件)</span>
            </button>
            <button
              type="button"
              class="subtab-btn"
              :class="{ active: activeSubTab === 'points' }"
              @click="activeSubTab = 'points'"
            >
              <span>点位绑定</span>
              <span class="tab-count-text">({{ currentCategoryPoints.length }} 点位)</span>
            </button>
          </nav>

          <!-- 主体内容滚动区 (顶格全表格，纯净排版无背景色框) -->
          <div class="workbench-scroll-pane">

            <!-- ─── Tab 1: Adapter 契约 (遥测属性、设备指令、返回事件) ─── -->
            <div v-show="activeSubTab === 'capabilities'" class="fullbleed-table-block">
              <!-- 1. 遥测属性 -->
              <section class="flat-table-section">
                <div class="section-title-bar">
                  <span class="section-title">遥测属性</span>
                  <span class="section-sub">通信模板：{{ currentCategory?.deviceTemplate?.templateName || '-' }}</span>
                </div>
                <el-table
                  :data="asArray(currentCategory?.deviceTemplate?.attributes)"
                  stripe
                  size="small"
                  class="industrial-fullbleed-table"
                >
                  <el-table-column label="属性标识" min-width="180">
                    <template #default="{ row }">
                      <span class="mono-text text-heading">{{ row.name || row.fieldName || row.key }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="数据类型" width="140">
                    <template #default="{ row }">
                      <span class="mono-text text-primary">{{ row.dataType || 'STRING' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="说明" min-width="280">
                    <template #default="{ row }">
                      <span>{{ row.description || '-' }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </section>

              <!-- 2. 设备指令 -->
              <section class="flat-table-section">
                <div class="section-title-bar">
                  <span class="section-title">设备指令</span>
                </div>
                <el-table
                  :data="asArray(currentCategory?.deviceTemplate?.commands)"
                  stripe
                  size="small"
                  class="industrial-fullbleed-table"
                >
                  <el-table-column label="指令名称" width="140">
                    <template #default="{ row }">
                      <strong class="text-primary mono-text">{{ row.name || row.commandName }}</strong>
                    </template>
                  </el-table-column>
                  <el-table-column label="指令说明" min-width="160">
                    <template #default="{ row }">
                      <span>{{ row.description || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="输入参数" min-width="400">
                    <template #default="{ row }">
                      <div v-if="visibleCommandParams(row).length" class="param-clean-table">
                        <div v-for="param in visibleCommandParams(row)" :key="param.name || param.paramName" class="param-clean-row">
                          <span class="param-clean-name mono-text">{{ param.name || param.paramName }}</span>
                          <span class="param-clean-type mono-text">{{ param.dataType || 'STRING' }}</span>
                          <span class="param-clean-desc">{{ param.description || '-' }}</span>
                        </div>
                      </div>
                      <span v-else class="text-secondary">无外部控制参数</span>
                    </template>
                  </el-table-column>
                </el-table>
              </section>

              <!-- 3. 返回事件 -->
              <section class="flat-table-section">
                <div class="section-title-bar">
                  <span class="section-title">返回事件</span>
                </div>
                <el-table
                  :data="eventList(currentCategory?.deviceTemplate?.events)"
                  stripe
                  size="small"
                  class="industrial-fullbleed-table"
                >
                  <el-table-column label="事件标识" min-width="200">
                    <template #default="{ row }">
                      <span class="mono-text text-heading">{{ row.name }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="事件类型" width="130">
                    <template #default="{ row }">
                      <span :class="row.type === '指令周期' ? 'text-primary' : 'text-success'">
                        {{ row.type }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="说明" min-width="280">
                    <template #default="{ row }">
                      <span>{{ row.description || '-' }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </div>

            <!-- ─── Tab 2: 点位绑定 (顶格全表格，纯净排版) ─── -->
            <div v-show="activeSubTab === 'points'" class="fullbleed-table-block">
              <section class="flat-table-section">
                <div class="section-title-bar">
                  <span class="section-title">物理点位与绑定设备</span>
                  <span class="section-sub">当前设备类别（{{ currentCategory?.categoryName || '-' }}）注册的物理通道及绑定的设备实例</span>
                </div>

                <el-table
                  :data="currentCategoryPoints"
                  stripe
                  size="small"
                  class="industrial-fullbleed-table"
                >
                  <el-table-column label="点位通道" min-width="160">
                    <template #default="{ row }">
                      <strong class="text-primary mono-text">{{ row.devicePoint }}</strong>
                    </template>
                  </el-table-column>
                  <el-table-column label="说明" min-width="180">
                    <template #default="{ row }">
                      <span>{{ row.description || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="绑定设备" min-width="220">
                    <template #default="{ row }">
                      <div v-if="findBoundInstanceForPoint(row.devicePoint)" class="instance-bind-cell">
                        <strong class="text-heading">{{ findBoundInstanceForPoint(row.devicePoint).instanceName }}</strong>
                        <span class="mono-text text-secondary" style="margin-left: 6px;">{{ findBoundInstanceForPoint(row.devicePoint).instanceId || findBoundInstanceForPoint(row.devicePoint).id }}</span>
                      </div>
                      <span v-else class="text-secondary">未绑定实例</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="所属模型" min-width="180">
                    <template #default="{ row }">
                      <span v-if="findBoundInstanceForPoint(row.devicePoint)" class="text-body">
                        {{ modelNameOf(findBoundInstanceForPoint(row.devicePoint).modelId || findBoundInstanceForPoint(row.devicePoint).deviceModelId) }}
                      </span>
                      <span v-else class="text-secondary">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="100" fixed="right" align="center">
                    <template #default="{ row }">
                      <button class="btn-link" type="button" @click="openPointDetail(row)">
                        <span>查看详情</span>
                      </button>
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </div>

          </div>
        </div>
      </main>

      <!-- 未选中 Adapter 空状态 -->
      <main v-else class="detail-workbench-pane empty-pane">
        <el-empty description="请从左侧列表选择一个 Adapter 驱动" :image-size="90" />
      </main>

    </div>

    <!-- =========================================================================
         点位与实例详情抽屉 (包含完整实例信息及对应点位的全部 MQTT 订阅/发布话题)
         ========================================================================= -->
    <el-drawer
      v-model="pointDetailVisible"
      title="点位通道详情"
      size="720px"
      append-to-body
      :lock-scroll="true"
      class="unified-workflow-drawer"
    >
      <div v-if="selectedPointInfo" class="point-detail-workbench">
        <section class="detail-sub-section">
          <h4 class="detail-sub-title">点位通道信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="点位通道">
              <strong class="text-primary mono-text">{{ selectedPointInfo.devicePoint }}</strong>
            </el-descriptions-item>
            <el-descriptions-item label="说明">{{ selectedPointInfo.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所属类别">{{ currentCategory?.categoryName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所属 Adapter">{{ activeAdapter?.adapterName || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-sub-section" style="margin-top: 16px;">
          <h4 class="detail-sub-title">绑定设备</h4>
          <div v-if="selectedPointBoundInstance">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="实例名称">
                <strong class="text-heading">{{ selectedPointBoundInstance.instanceName }}</strong>
              </el-descriptions-item>
              <el-descriptions-item label="实例编号">
                <span class="mono-text">{{ selectedPointBoundInstance.instanceId || selectedPointBoundInstance.id }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="所属模型">
                <span>{{ modelNameOf(selectedPointBoundInstance.modelId || selectedPointBoundInstance.deviceModelId) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatTime(selectedPointBoundInstance.createTime) }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <div v-else class="compact-empty" style="padding: 16px;">
            当前点位通道尚未绑定物理设备实例（空闲物理通道）
          </div>
        </section>

        <section class="detail-sub-section" style="margin-top: 16px;">
          <h4 class="detail-sub-title">MQTT 通信主题规范</h4>
          <div class="mqtt-topic-detail-box">
            <div class="topic-item">
              <div class="topic-head">
                <span class="tag tag-primary">驱动上报</span>
                <strong>遥测数据上报主题</strong>
              </div>
              <code class="topic-code-str">smartlab/adapter/{{ activeAdapter?.adapterName }}/{{ selectedPointInfo.devicePoint }}/telemetry</code>
              <span class="topic-desc-note">驱动按照物模型属性定义周期或变化上报传感器数据</span>
            </div>

            <div class="topic-item">
              <div class="topic-head">
                <span class="tag tag-success">系统下发</span>
                <strong>控制指令下发主题</strong>
              </div>
              <code class="topic-code-str">smartlab/adapter/{{ activeAdapter?.adapterName }}/{{ selectedPointInfo.devicePoint }}/command</code>
              <span class="topic-desc-note">工作流引擎或人工控制台下发具体控制命令参数报文</span>
            </div>

            <div class="topic-item">
              <div class="topic-head">
                <span class="tag tag-primary">驱动上报</span>
                <strong>事件报警上报主题</strong>
              </div>
              <code class="topic-code-str">smartlab/adapter/{{ activeAdapter?.adapterName }}/{{ selectedPointInfo.devicePoint }}/event</code>
              <span class="topic-desc-note">上报命令执行状态确认与硬件故障异常报警</span>
            </div>
          </div>
        </section>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <button class="btn-aliyun" type="button" @click="pointDetailVisible = false">关闭</button>
        </div>
      </template>
    </el-drawer>

    <!-- =========================================================================
         全局配置对比抽屉 (右侧抽屉，高度 100% 满屏适应，彻底消除页面滚动)
         ========================================================================= -->
    <el-drawer
      v-model="configDrawerVisible"
      title="驱动配置对比"
      size="80%"
      append-to-body
      :lock-scroll="true"
      class="unified-workflow-drawer"
    >
      <div class="diff-drawer-body">
        <div class="diff-split-container">
          <div class="code-column">
            <div class="column-header">
              <span>原始驱动配置</span>
              <span class="mono-text" style="color:#93c5fd;">{{ activeAdapter?.parsedConfig?.registerMeta?.rawConfigFormat || 'INI' }}</span>
            </div>
            <pre class="code-box"><code>{{ activeAdapter?.originalConfig || activeConfigText }}</code></pre>
          </div>

          <div class="code-column">
            <div class="column-header">
              <span>物模型 JSON 契约</span>
              <button class="btn-link" type="button" @click="copyConfigJson">
                <el-icon><CopyDocument /></el-icon>
                <span>复制 JSON</span>
              </button>
            </div>
            <pre class="code-box"><code>{{ activeConfigText }}</code></pre>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <button class="btn-aliyun" type="button" @click="configDrawerVisible = false">关闭</button>
        </div>
      </template>
    </el-drawer>

    <!-- =========================================================================
         审核与注册 Adapter 抽屉 (Drawer)
         ========================================================================= -->
    <el-drawer
      v-model="registerDrawerVisible"
      :title="registerDrawerTitle"
      size="80%"
      append-to-body
      :lock-scroll="true"
      class="unified-workflow-drawer"
      :before-close="confirmCloseRegisterDrawer"
    >
      <div class="adapter-register-workbench">
        <nav class="adapter-register-nav" aria-label="注册流程导航">
          <button
            type="button"
            :class="{ active: registerStep === 0 }"
            @click="goRegisterStep(0)"
          >
            {{ registerSource === 'mqtt' ? '01 选择待审核请求' : '01 导入配置' }}
          </button>
          <button
            type="button"
            :class="{ active: registerStep === 1, disabled: !registerPreview }"
            :disabled="!registerPreview"
            @click="goRegisterStep(1)"
          >
            02 校验与审阅
          </button>
          <button
            type="button"
            :class="{ active: registerStep === 2, disabled: !registerPreview }"
            :disabled="!registerPreview"
            @click="goRegisterStep(2)"
          >
            03 确认保存
          </button>
        </nav>

        <el-scrollbar class="adapter-register-scroll">
          <section v-show="registerStep === 0" class="register-section">
            <div class="drawer-section-head">
              <h3>选择配置来源</h3>
            </div>
            <el-radio-group v-model="registerSource" size="small" class="register-source-picker" @change="resetRegisterPreview">
              <el-radio-button value="mqtt">MQTT 接收</el-radio-button>
              <el-radio-button value="manual">手动导入</el-radio-button>
            </el-radio-group>

            <div v-if="registerSource === 'mqtt'" class="source-content" v-loading="pendingLoading">
              <div class="source-hint">
                <span>监听主题：</span>
                <code class="mono-text">{{ mqttTopics.registerTopic }}</code>
                <span :class="mqttConnected ? 'text-success' : 'text-secondary'" style="margin-left: 10px; font-weight: 500;">
                  {{ mqttConnected ? '● Broker 在线' : '○ Broker 未连接' }}
                </span>
              </div>

              <div v-if="pendingRegistrations.length === 0" class="compact-empty">
                等待 Adapter 向注册主题发布配置报文
              </div>
              <el-table v-else :data="pendingRegistrations" border size="small" class="industrial-fullbleed-table">
                <el-table-column label="Adapter 名称" min-width="180">
                  <template #default="{ row }"><strong>{{ row.adapterName }}</strong></template>
                </el-table-column>
                <el-table-column label="收到时间" min-width="155">
                  <template #default="{ row }">{{ formatTime(row.receivedAt) }}</template>
                </el-table-column>
                <el-table-column label="类别 / 模板 / 点位" min-width="150">
                  <template #default="{ row }">
                    {{ pendingCategoryCount(row) }} / {{ pendingTemplateCount(row) }} / {{ pendingPointCount(row) }}
                  </template>
                </el-table-column>
                <el-table-column label="格式" width="80">
                  <template #default="{ row }">{{ row.rawConfigFormat || 'JSON' }}</template>
                </el-table-column>
                <el-table-column label="操作" width="160" fixed="right">
                  <template #default="{ row }">
                    <button class="btn-aliyun-cta" style="height:24px; padding:0 8px; font-size:11px;" @click="reviewPendingRegistration(row)">审阅</button>
                    <button class="btn-link" style="color:var(--sl-danger); margin-left:8px; font-size:11px;" @click="discardPendingRegistration(row)">移除</button>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div v-else class="source-content manual-source">
              <p class="source-copy">上传原始配置文件，或直接粘贴配置内容。系统会自动识别 Adapter 名称与配置格式。</p>
              <el-form label-width="92px" size="small" class="register-form">
                <el-form-item label="配置格式">
                  <el-select v-model="registerForm.rawConfigFormat">
                    <el-option v-for="format in adapterRegisterFormats" :key="format" :label="format" :value="format" />
                  </el-select>
                </el-form-item>
                <el-form-item label="配置文件">
                  <el-upload
                    drag
                    :auto-upload="false"
                    :show-file-list="false"
                    accept=".json,.txt,.ini,.yaml,.yml,.xml"
                    :on-change="importRegisterFile"
                  >
                    <el-icon class="upload-icon"><Upload /></el-icon>
                    <div class="el-upload__text">拖拽配置文件到此处，或 <em>点击选择</em></div>
                  </el-upload>
                </el-form-item>
                <el-form-item label="配置内容">
                  <el-input
                    v-model="registerForm.rawConfigContent"
                    type="textarea"
                    :rows="8"
                    @input="resetManualPreview"
                    placeholder="粘贴 Adapter 原始配置内容"
                  />
                </el-form-item>
                <el-form-item>
                  <button class="btn-aliyun-cta" type="button" :disabled="registerLoading" @click="parseRegisterConfig">
                    {{ registerLoading ? '解析中...' : '解析配置' }}
                  </button>
                </el-form-item>
              </el-form>
            </div>
          </section>

          <section v-show="registerStep === 1" class="register-section" :class="{ disabled: !registerPreview }">
            <div class="drawer-section-head">
              <h3>审阅与校验配置</h3>
              <span v-if="registerPreview" class="sec-sub">已生成规范化物模型契约</span>
            </div>
            <div v-if="!registerPreview" class="compact-empty">请先解析配置</div>
            <template v-else>
              <!-- 顶部 Adapter 基础信息 -->
              <el-form label-width="92px" size="small" class="review-form">
                <el-form-item label="Adapter 说明">
                  <el-input v-model="registerPreview.adapterDescription" placeholder="用于管理页面显示，不影响底层协议" />
                </el-form-item>
              </el-form>

              <!-- 类别选择栏 (多类别时支持切换) -->
              <div v-if="reviewCategories.length > 1" class="review-category-tabs">
                <button
                  v-for="(cat, cIdx) in reviewCategories"
                  :key="cat.categoryName || cIdx"
                  type="button"
                  class="review-category-tab-btn"
                  :class="{ active: reviewCategoryIndex === cIdx }"
                  @click="reviewCategoryIndex = cIdx"
                >
                  <el-icon><Folder /></el-icon>
                  <span>{{ cat.categoryName || '未命名类别' }}</span>
                  <span class="tab-badge">({{ asArray(cat.devicePoints).length }} 点位)</span>
                </button>
              </div>

              <!-- 当前审阅类别的详细信息与四张标准表格 -->
              <div v-if="currentReviewCategory" class="review-category-detail-card">
                <!-- 类别与模板定义编辑条 -->
                <div class="review-category-info-bar">
                  <div class="info-row">
                    <span class="info-label">设备类别:</span>
                    <strong class="text-heading">{{ currentReviewCategory.categoryName || '-' }}</strong>
                    <el-input v-model="currentReviewCategory.categoryDescription" size="small" placeholder="类别说明" style="flex: 1; max-width: 320px;" />
                  </div>
                  <div class="info-row" style="margin-top: 6px;">
                    <span class="info-label">通信模板:</span>
                    <strong class="text-primary">{{ currentReviewCategory.deviceTemplate?.templateName || currentReviewCategory.deviceTemplate?.name || '-' }}</strong>
                    <el-input v-model="currentReviewCategory.deviceTemplate.description" size="small" placeholder="模板说明" style="flex: 1; max-width: 320px;" />
                  </div>
                </div>

                <!-- 1. 遥测属性 -->
                <section class="flat-table-section" style="margin-top: 12px;">
                  <div class="section-title-bar">
                    <span class="section-title">遥测属性</span>
                    <span class="section-sub">共 {{ asArray(currentReviewCategory.deviceTemplate?.attributes).length }} 项</span>
                  </div>
                  <el-table
                    :data="asArray(currentReviewCategory.deviceTemplate?.attributes)"
                    stripe
                    size="small"
                    class="industrial-fullbleed-table"
                  >
                    <el-table-column label="属性标识" min-width="180">
                      <template #default="{ row }">
                        <span class="mono-text text-heading">{{ row.name || row.fieldName || row.key }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="数据类型" width="140">
                      <template #default="{ row }">
                        <span class="mono-text text-primary">{{ row.dataType || 'STRING' }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="说明" min-width="260">
                      <template #default="{ row }">
                        <span>{{ row.description || '-' }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </section>

                <!-- 2. 设备指令 -->
                <section class="flat-table-section" style="margin-top: 12px;">
                  <div class="section-title-bar">
                    <span class="section-title">设备指令</span>
                    <span class="section-sub">共 {{ asArray(currentReviewCategory.deviceTemplate?.commands).length }} 项</span>
                  </div>
                  <el-table
                    :data="asArray(currentReviewCategory.deviceTemplate?.commands)"
                    stripe
                    size="small"
                    class="industrial-fullbleed-table"
                  >
                    <el-table-column label="指令名称" width="140">
                      <template #default="{ row }">
                        <strong class="text-primary mono-text">{{ row.name || row.commandName }}</strong>
                      </template>
                    </el-table-column>
                    <el-table-column label="指令说明" min-width="160">
                      <template #default="{ row }">
                        <span>{{ row.description || '-' }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="输入参数" min-width="380">
                      <template #default="{ row }">
                        <div v-if="visibleCommandParams(row).length" class="param-clean-table">
                          <div v-for="param in visibleCommandParams(row)" :key="param.name || param.paramName" class="param-clean-row">
                            <span class="param-clean-name mono-text">{{ param.name || param.paramName }}</span>
                            <span class="param-clean-type mono-text">{{ param.dataType || 'STRING' }}</span>
                            <span class="param-clean-desc">{{ param.description || '-' }}</span>
                          </div>
                        </div>
                        <span v-else class="text-secondary">无外部控制参数</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </section>

                <!-- 3. 返回事件 -->
                <section class="flat-table-section" style="margin-top: 12px;">
                  <div class="section-title-bar">
                    <span class="section-title">返回事件</span>
                    <span class="section-sub">共 {{ eventList(currentReviewCategory.deviceTemplate?.events).length }} 项</span>
                  </div>
                  <el-table
                    :data="eventList(currentReviewCategory.deviceTemplate?.events)"
                    stripe
                    size="small"
                    class="industrial-fullbleed-table"
                  >
                    <el-table-column label="事件标识" min-width="200">
                      <template #default="{ row }">
                        <span class="mono-text text-heading">{{ row.name }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="事件类型" width="130">
                      <template #default="{ row }">
                        <span :class="row.type === '指令周期' ? 'text-primary' : 'text-success'">
                          {{ row.type }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column label="说明" min-width="260">
                      <template #default="{ row }">
                        <span>{{ row.description || '-' }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </section>

                <!-- 4. 物理点位通道 -->
                <section class="flat-table-section" style="margin-top: 12px;">
                  <div class="section-title-bar">
                    <span class="section-title">物理点位通道</span>
                    <span class="section-sub">共 {{ asArray(currentReviewCategory.devicePoints).length }} 个点位</span>
                  </div>
                  <el-table
                    :data="asArray(currentReviewCategory.devicePoints)"
                    stripe
                    size="small"
                    class="industrial-fullbleed-table"
                  >
                    <el-table-column label="点位通道" min-width="200">
                      <template #default="{ row }">
                        <strong class="text-primary mono-text">{{ row.devicePoint }}</strong>
                      </template>
                    </el-table-column>
                    <el-table-column label="说明" min-width="300">
                      <template #default="{ row }">
                        <span>{{ row.description || '-' }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </section>
              </div>

              <div class="review-actions" style="margin-top: 16px;">
                <button class="btn-aliyun" type="button" @click="registerStep = 0">返回上一步</button>
                <button class="btn-aliyun-cta" type="button" @click="registerStep = 2">下一步：确认保存</button>
              </div>
            </template>
          </section>

          <section v-show="registerStep === 2" class="register-section" :class="{ disabled: !registerPreview }">
            <div class="drawer-section-head">
              <h3>{{ existingAdapterForPreview ? '确认更新 Adapter' : '确认注册 Adapter' }}</h3>
            </div>
            <el-alert
              v-if="existingAdapterForPreview"
              type="warning"
              :closable="false"
              show-icon
              :title="`已存在同名 Adapter，将更新现有配置；当前已绑定 ${boundCountFor(existingAdapterForPreview.adapterName)} 个设备实例。`"
            />
            <div class="save-summary">
              <span>Adapter 名称：<strong>{{ registerPreview?.adapterName || '-' }}</strong></span>
              <span>操作类型：<strong>{{ existingAdapterForPreview ? '更新现有配置' : '新建注册' }}</strong></span>
              <span>包含类别与点位：<strong>{{ adapterCategoriesOf(registerPreview).length }} 类别 · {{ adapterPointsOf(registerPreview).length }} 点位</strong></span>
            </div>
            <div class="save-panel">
              <button class="btn-aliyun" type="button" @click="registerStep = 1">返回审阅</button>
              <button class="btn-primary-blue" type="button" :disabled="!registerPreview || registerLoading" @click="saveReviewedRegistration">
                {{ registerLoading ? '提交中...' : (existingAdapterForPreview ? '确认更新' : '确认注册') }}
              </button>
            </div>
          </section>
        </el-scrollbar>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <button class="btn-aliyun" type="button" @click="confirmCloseRegisterDrawer(() => closeRegisterDrawer())">取消</button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Upload,
  Connection,
  Document,
  CopyDocument,
  Download,
  EditPen,
  Folder,
  VideoPause,
  VideoPlay
} from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'
import { adapterRegisterFormats, loadProtocolMetadata, mqttTopics } from './components/deviceModel/deviceModelConstants'

const authStore = useAuthStore()

const keyword = ref('')
const statusFilter = ref('all')
const activeKey = ref('')
const selectedCategoryIndex = ref(0)
const activeSubTab = ref('capabilities')
const configDrawerVisible = ref(false)
const pointDetailVisible = ref(false)
const selectedPointInfo = ref(null)
const loading = ref(false)
const adapters = ref([])
const instances = ref([])
const models = ref({})
const mqttStatus = ref(null)
const registerDrawerVisible = ref(false)
const registerLoading = ref(false)
const pendingLoading = ref(false)
const pendingRegistrations = ref([])
const registerPreview = ref(null)
const registerSource = ref('mqtt')
const registerStep = ref(0)
const reviewCategoryIndex = ref(0)
const selectedPendingAdapterName = ref('')
const pendingDrafts = reactive({})
const registerForm = reactive({ adapterName: '', rawConfigFormat: 'JSON', rawConfigContent: '' })

const reviewCategories = computed(() => adapterCategoriesOf(registerPreview.value))
const currentReviewCategory = computed(() => {
  const cats = reviewCategories.value
  if (!cats.length) return null
  return cats[reviewCategoryIndex.value] || cats[0]
})
let registrationStream = null
let mqttStatusTimer = null

const copyConfigJson = async () => {
  if (!activeConfigText.value) return
  try {
    await navigator.clipboard.writeText(activeConfigText.value)
    ElMessage.success('配置 JSON 已成功复制到剪贴板')
  } catch (err) {
    ElMessage.error('复制失败，请手动复制')
  }
}

const exportConfigJson = () => {
  if (!activeAdapter.value) return
  const jsonStr = activeConfigText.value || JSON.stringify(activeConfig.value || {}, null, 2)
  const blob = new Blob([jsonStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${activeAdapter.value.adapterName || 'adapter'}_manifest.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('配置 JSON 文件已开始下载')
}

const openPointDetail = (point) => {
  selectedPointInfo.value = point
  pointDetailVisible.value = true
}

const selectedPointBoundInstance = computed(() => {
  if (!selectedPointInfo.value?.devicePoint) return null
  return findBoundInstanceForPoint(selectedPointInfo.value.devicePoint)
})

const fetchData = async () => {
  loading.value = true
  try {
    const [adapterResult, instanceResult, modelResult] = await Promise.allSettled([
      axios.get('/api/adapter/index/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/device/model/list')
    ])
    if (adapterResult.status === 'fulfilled') {
      adapters.value = adapterResult.value.data?.success ? asArray(adapterResult.value.data.data) : []
    } else {
      ElMessage.error('Adapter 列表加载失败')
    }
    if (instanceResult.status === 'fulfilled') instances.value = instanceResult.value.data?.success ? asArray(instanceResult.value.data.data) : []
    if (modelResult.status === 'fulfilled' && modelResult.value.data?.success) {
      const map = {}
      asArray(modelResult.value.data.data).forEach(model => {
        map[String(model.modelId || model.id)] = model.modelName || model.name || String(model.modelId || model.id)
      })
      models.value = map
    }
    if (!adapters.value.some(item => adapterIdOf(item) === activeKey.value)) {
      activeKey.value = adapters.value[0] ? adapterIdOf(adapters.value[0]) : ''
      selectedCategoryIndex.value = 0
    }
  } finally {
    loading.value = false
  }
  fetchMqttStatus()
}

const handleSelectAdapter = (id) => {
  activeKey.value = id
  selectedCategoryIndex.value = 0
}

const fetchMqttStatus = async () => {
  try {
    const res = await getMqttStatus()
    mqttStatus.value = res.data?.data || res.data || null
  } catch (error) {
    mqttStatus.value = { connected: false, status: 'UNAVAILABLE' }
  }
}

const getMqttStatus = async () => {
  try {
    return await axios.get('/api/adapter/protocol/mqtt/status')
  } catch (error) {
    return axios.get('/api/adapter/mqtt/mqtt/status')
  }
}

const toggleAdapterStatus = async (adapter) => {
  if (!adapter?.id) return
  const currentEnabled = isAdapterEnabled(adapter)
  const targetStatus = currentEnabled ? 'DISABLED' : 'ENABLED'
  const actionText = currentEnabled ? '停用' : '启用'
  try {
    const res = await axios.post(`/api/adapter/index/${adapter.id}/status`, { status: targetStatus })
    if (res.data?.success || res.data?.code === 200 || res.data?.code === 0) {
      adapter.status = targetStatus
      const found = adapters.value.find(a => String(a.id) === String(adapter.id))
      if (found) found.status = targetStatus
      ElMessage.success(`Adapter「${adapter.adapterName || ''}」已成功${actionText}`)
    } else {
      ElMessage.error(res.data?.message || `${actionText}失败`)
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || err.message || `${actionText}失败`)
  }
}

const reconnectMqtt = async () => {
  try {
    const res = await axios.post('/api/adapter/protocol/mqtt/reconnect')
    mqttStatus.value = res.data?.data || res.data || null
    await fetchMqttStatus()
    ElMessage.success(mqttConnected.value ? 'Broker 已连接' : 'Broker 重连请求已发送')
  } catch (error) {
    ElMessage.error('Broker 重连失败')
  }
}

const filteredAdapters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return adapters.value.filter(item => {
    const matchesKeyword = !kw || String(item.adapterName || '').toLowerCase().includes(kw)
    const matchesStatus = statusFilter.value === 'all' || runtimeStatusClass(item) === statusFilter.value
    return matchesKeyword && matchesStatus
  })
})

const activeAdapter = computed(() => adapters.value.find(item => adapterIdOf(item) === activeKey.value) || null)
const activeConfig = computed(() => parsedConfigOf(activeAdapter.value))
const activeCategories = computed(() => adapterCategoriesOf(activeConfig.value))

const currentCategory = computed(() => {
  const cats = activeCategories.value
  if (!cats.length) return null
  return cats[selectedCategoryIndex.value] || cats[0]
})

const currentCategoryPoints = computed(() => asArray(currentCategory.value?.devicePoints))

const boundInstances = computed(() => {
  const name = activeAdapter.value?.adapterName
  if (!name) return []
  return instances.value.filter(instance => boundAdapterOf(instance) === name)
})

const findBoundInstanceForPoint = (devicePoint) => {
  return boundInstances.value.find(inst => boundDevicePointOf(inst) === devicePoint) || null
}

const activeConfigText = computed(() => JSON.stringify(activeConfig.value || {}, null, 2))
const mqttConnected = computed(() => mqttStatus.value?.connected === true || mqttStatus.value?.available === true || String(mqttStatus.value?.status || '').toUpperCase() === 'CONNECTED')

const existingAdapterForPreview = computed(() => {
  const name = registerPreview.value?.adapterName
  return name ? adapters.value.find(item => item.adapterName === name) || null : null
})
const registerDrawerTitle = computed(() => registerSource.value === 'mqtt' ? '审核 Adapter 注册请求' : '导入 Adapter 配置')

const connectRegistrationStream = () => {
  if (registrationStream) return
  try {
    const token = authStore.token
    registrationStream = new EventSource('/api/adapter/protocol/pending-registrations/stream?token=' + encodeURIComponent(token))
    registrationStream.addEventListener('pending_snapshot', event => {
      pendingRegistrations.value = asArray(JSON.parse(event.data || '[]'))
    })
    registrationStream.addEventListener('adapter_register_request', event => {
      const item = JSON.parse(event.data || '{}')
      if (!item?.adapterName) return
      const index = pendingRegistrations.value.findIndex(row => row.adapterName === item.adapterName)
      if (index >= 0) pendingRegistrations.value.splice(index, 1, item)
      else pendingRegistrations.value.unshift(item)
      if (!pendingDrafts[item.adapterName]) pendingDrafts[item.adapterName] = cloneJson(item.parsedConfig)
      if (registerDrawerVisible.value) ElMessage.info(`收到 Adapter「${item.adapterName}」注册请求，请在待审核列表中审阅`)
    })
    registrationStream.onerror = () => {
      closeRegistrationStream()
    }
  } catch (error) {
    registrationStream = null
  }
}

const closeRegistrationStream = () => {
  if (registrationStream) {
    registrationStream.close()
    registrationStream = null
  }
}

const resetRegisterPreview = () => {
  registerPreview.value = null
  selectedPendingAdapterName.value = ''
  registerStep.value = 0
  reviewCategoryIndex.value = 0
}

const resetManualPreview = () => {
  if (registerSource.value === 'manual') resetRegisterPreview()
}
const reviewPendingRegistration = (item) => {
  registerSource.value = 'mqtt'
  selectedPendingAdapterName.value = item?.adapterName || ''
  reviewCategoryIndex.value = 0
  registerPreview.value = cloneJson(pendingDrafts[selectedPendingAdapterName.value] || item?.parsedConfig || null)
  registerStep.value = registerPreview.value ? 1 : 0
}

const goRegisterStep = step => {
  if (step > 0 && !registerPreview.value) return
  registerStep.value = step
}

const completeReviewedRegistration = () => {
  if (!selectedPendingAdapterName.value) return
  const item = pendingRegistrations.value.find(row => row.adapterName === selectedPendingAdapterName.value)
  if (item) completePendingRegistration(item)
}

const openRegisterDrawer = (source = 'mqtt', adapter = null) => {
  registerPreview.value = null
  registerSource.value = source
  registerStep.value = 0
  selectedPendingAdapterName.value = ''
  connectRegistrationStream()
  registerForm.adapterName = ''
  registerForm.rawConfigFormat = 'JSON'
  registerForm.rawConfigContent = ''
  registerDrawerVisible.value = true
  if (adapter) {
    registerForm.adapterName = adapter.adapterName || ''
    registerForm.rawConfigFormat = adapter.parsedConfig?.registerMeta?.rawConfigFormat || 'JSON'
    registerForm.rawConfigContent = adapter.originalConfig || ''
  }
  fetchPendingRegistrations()
}

const fetchPendingRegistrations = async () => {
  pendingLoading.value = true
  try {
    const res = await axios.get('/api/adapter/protocol/pending-registrations')
    pendingRegistrations.value = res.data?.success ? asArray(res.data.data) : []
    pendingRegistrations.value.forEach(item => { if (item?.adapterName && !pendingDrafts[item.adapterName]) pendingDrafts[item.adapterName] = cloneJson(item.parsedConfig) })
  } finally {
    pendingLoading.value = false
  }
}

const completePendingRegistration = async (item) => {
  if (!item?.adapterName) return
  registerLoading.value = true
  try {
    const reviewedConfig = selectedPendingAdapterName.value === item.adapterName ? registerPreview.value : pendingDrafts[item.adapterName] || item.parsedConfig
    const res = await axios.post(`/api/adapter/protocol/pending-registrations/${encodeURIComponent(item.adapterName)}/complete`, { parsedConfig: reviewedConfig || item.parsedConfig })
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '注册失败')
      return
    }
    ElMessage.success('Adapter 已注册')
    registerDrawerVisible.value = false
    selectedPendingAdapterName.value = ''
    registerPreview.value = null
    delete pendingDrafts[item.adapterName]
    await fetchData()
    activeKey.value = adapterIdOf(res.data.data)
  } finally {
    registerLoading.value = false
  }
}

const discardPendingRegistration = async (item) => {
  if (!item?.adapterName) return
  await axios.delete(`/api/adapter/protocol/pending-registrations/${encodeURIComponent(item.adapterName)}`)
  pendingRegistrations.value = pendingRegistrations.value.filter(row => row.adapterName !== item.adapterName)
  delete pendingDrafts[item.adapterName]
}

const parseRegisterConfig = async () => {
  if (!registerForm.rawConfigContent.trim()) {
    ElMessage.warning('请先填写或上传配置内容')
    return
  }
  registerLoading.value = true
  try {
    const res = await axios.post('/api/adapter/index/parse-register', buildRegisterPayload())
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '解析失败')
      return
    }
    registerPreview.value = res.data.data
    reviewCategoryIndex.value = 0
    if (!registerForm.adapterName && registerPreview.value?.adapterName) registerForm.adapterName = registerPreview.value.adapterName
    registerStep.value = 1
    ElMessage.success('解析成功')
  } finally {
    registerLoading.value = false
  }
}

const saveReviewedRegistration = async () => {
  if (!registerPreview.value) {
    ElMessage.warning('请先完成配置解析')
    return
  }
  if (registerSource.value === 'mqtt') {
    await completeReviewedRegistration()
    return
  }
  await registerAdapter()
}

const registerAdapter = async () => {
  if (!registerPreview.value) {
    ElMessage.warning('请先解析并审阅配置')
    return
  }
  if (!registerForm.rawConfigContent.trim()) {
    ElMessage.warning('请先填写或上传配置内容')
    return
  }
  registerLoading.value = true
  try {
    const res = await axios.post('/api/adapter/index/register', buildRegisterPayload())
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '注册失败')
      return
    }
    registerDrawerVisible.value = false
    ElMessage.success('Adapter 已注册')
    await fetchData()
    activeKey.value = adapterIdOf(res.data.data)
  } finally {
    registerLoading.value = false
  }
}

const deleteAdapter = async (adapter) => {
  const boundCount = boundCountFor(adapter.adapterName)
  if (boundCount > 0) {
    ElMessage.warning(`Adapter「${adapter.adapterName}」仍绑定 ${boundCount} 个设备实例，请先解除绑定`)
    return
  }
  await ElMessageBox.confirm(`确认删除 Adapter「${adapter.adapterName}」？删除后将无法用于新的设备实例绑定。`, '删除确认', { type: 'warning' })
  const res = await axios.delete(`/api/adapter/index/delete/${adapter.id}`)
  if (!res.data?.success) {
    ElMessage.error(res.data?.message || '删除失败')
    return
  }
  ElMessage.success('已删除')
  await fetchData()
}

const importRegisterFile = (file) => {
  const rawFile = file?.raw
  if (!rawFile) return false
  resetManualPreview()
  const reader = new FileReader()
  reader.onload = () => { registerForm.rawConfigContent = String(reader.result || '') }
  reader.readAsText(rawFile, 'utf-8')
  return false
}

const buildRegisterPayload = () => {
  const payload = {
    adapterName: registerForm.adapterName || manifestAdapterName(registerForm.rawConfigContent),
    rawConfigFormat: registerForm.rawConfigFormat,
    rawConfigContent: registerForm.rawConfigContent,
    timestamp: Date.now()
  }
  if (registerPreview.value) payload.parsedConfig = registerPreview.value
  return payload
}

const closeRegisterDrawer = () => {
  registerDrawerVisible.value = false
  resetRegisterPreview()
}

const confirmCloseRegisterDrawer = (done) => {
  if (!registerPreview.value || registerStep.value === 0) {
    done()
    return
  }
  ElMessageBox.confirm('当前审阅内容尚未保存，确认放弃吗？', '关闭注册流程', { type: 'warning' })
    .then(() => done())
    .catch(() => {})
}

const manifestAdapterName = (content) => {
  try {
    const jsonName = JSON.parse(content)?.adapterName
    if (jsonName) return String(jsonName).trim()
  } catch { /* fall through to INI */ }
  const iniMatch = String(content || '').match(/^\s*adapterName\s*=\s*([^;#\r\n]+)\s*$/mi)
  return iniMatch ? iniMatch[1].trim() : ''
}

const cloneJson = value => value == null ? null : JSON.parse(JSON.stringify(value))
const asArray = value => Array.isArray(value) ? value : []
const adapterIdOf = adapter => String(adapter?.id || adapter?.adapterName || '')
const parsedConfigOf = adapter => {
  if (!adapter?.parsedConfig) return {}
  if (typeof adapter.parsedConfig === 'string') {
    try { return JSON.parse(adapter.parsedConfig) } catch { return {} }
  }
  return adapter.parsedConfig || {}
}
const adapterCategoriesOf = config => asArray(config?.deviceCategories)
const adapterPointsOf = config => adapterCategoriesOf(config).flatMap(category =>
  asArray(category.devicePoints).map(point => ({ ...point, categoryName: category.categoryName }))
)
const categoryCount = adapter => adapterCategoriesOf(parsedConfigOf(adapter)).length
const pendingCategoryCount = row => adapterCategoriesOf(row?.parsedConfig).length
const pendingTemplateCount = row => adapterCategoriesOf(row?.parsedConfig).length
const pendingPointCount = row => adapterPointsOf(row?.parsedConfig).length
const pointCount = adapter => adapterPointsOf(parsedConfigOf(adapter)).length
const modelNameOf = id => models.value[String(id)] || String(id || '-')
const boundAdapterOf = instance => instance?.boundAdapterName || ''
const boundDevicePointOf = instance => instance?.boundDevicePoint || ''
const isAdapterEnabled = adapter => !adapter?.status || String(adapter.status).toUpperCase() !== 'DISABLED'
const runtimeStatusClass = adapter => {
  if (!adapter?.lastHeartbeat) return 'unknown'
  const heartbeat = new Date(adapter.lastHeartbeat).getTime()
  if (!Number.isFinite(heartbeat)) return 'unknown'
  const age = Date.now() - heartbeat
  if (age <= 120000) return 'online'
  return 'stale'
}
const runtimeStatusLabel = adapter => ({ online: '在线', stale: '心跳超时', unknown: '未连接' }[runtimeStatusClass(adapter)] || '未知')
const boundCountFor = adapterName => instances.value.filter(instance => boundAdapterOf(instance) === adapterName).length
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const eventList = events => [
  ...asArray(events?.cmdEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '指令周期' })),
  ...asArray(events?.opEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '业务事件' }))
]
const eventCount = events => eventList(events).length
const visibleCommandParams = command => asArray(command?.parameters || command?.commandParameters).filter(param => !param.internal)

onMounted(() => {
  loadProtocolMetadata().catch(() => ElMessage.warning('协议元数据加载失败，配置格式列表可能不完整'))
  fetchData()
  connectRegistrationStream()
  mqttStatusTimer = window.setInterval(fetchMqttStatus, 30000)
})
onUnmounted(() => {
  closeRegistrationStream()
  if (mqttStatusTimer) window.clearInterval(mqttStatusTimer)
})
</script>

<style scoped>
/* ==========================================================================
   1. 主页面与视口绝对锁屏 (calc(100vh - 50px))
   ========================================================================== */
.adapter-management-page {
  height: calc(100vh - 50px);
  padding: 10px 14px 14px;
  background-color: var(--sl-bg-page, #f1f5f9);
  overflow: hidden;
  display: flex;
}
.adapter-management-page,
.adapter-management-page * {
  box-sizing: border-box;
}

/* ==========================================================================
   2. 一体化三栏大工作台画卷 (240px ➔ 230px ➔ 1fr)
   ========================================================================== */
.adapter-workbench-canvas {
  flex: 1;
  display: flex;
  background: #ffffff;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-lg, 8px);
  box-shadow: var(--sl-shadow-container, 0 1px 3px rgba(15, 23, 42, 0.04));
  overflow: hidden;
  min-height: 0;
}

/* ── Adapter 侧边栏专用（行高/选中色见 asset-tree.css） ── */
.broker-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10.5px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 10px;
  line-height: 1.3;
  flex-shrink: 0;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.broker-status-pill .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}
.broker-status-pill.online {
  color: var(--sl-success, #16a34a);
  background: var(--sl-success-light, #f0fdf4);
}
.broker-status-pill.online .dot {
  background: var(--sl-success, #16a34a);
}
.broker-status-pill.offline {
  color: var(--sl-danger, #dc2626);
  background: #fef2f2;
  cursor: pointer;
}
.broker-status-pill.offline:hover {
  text-decoration: underline;
}
.broker-status-pill.offline .dot {
  background: var(--sl-danger, #dc2626);
}

.adapter-sidebar .tree-search-bar {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.status-radio-row {
  display: flex;
}
.status-filter-group {
  width: 100%;
  display: flex;
}
.status-filter-group :deep(.el-radio-button) {
  flex: 1;
}
.status-filter-group :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 4px 0 !important;
  font-size: 11px;
  border-radius: 0;
}
.status-filter-group :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-top-left-radius: var(--sl-radius-sm, 6px);
  border-bottom-left-radius: var(--sl-radius-sm, 6px);
}
.status-filter-group :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-top-right-radius: var(--sl-radius-sm, 6px);
  border-bottom-right-radius: var(--sl-radius-sm, 6px);
}

.adapter-sidebar {
  width: 270px;
}
.sidebar-foot {
  padding: 8px 10px;
  border-top: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  flex-shrink: 0;
}
.count-badge {
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
  font-weight: 600;
  margin-left: 2px;
}

.category-nav-col {
  width: 270px;
}

/* ── 第 3 栏：右侧工作台主画卷 ── */
.detail-workbench-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
  min-width: 0;
}
.detail-workbench-pane.empty-pane {
  align-items: center;
  justify-content: center;
}

/* ─── 上半部分：Adapter 驱动总览头与全功能操作工具栏 ─── */
.workbench-top-header {
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #ffffff;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.top-header-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  gap: 16px;
}
.adapter-title-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.adapter-main-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  font-family: var(--sl-font-mono, monospace);
  margin: 0;
}
.adapter-subtitle {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
}
.adapter-action-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* 35px 像素级高度锁定指标横幅 (Metric Ribbon) */
.metric-ribbon {
  height: 35px;
  max-height: 35px;
  padding: 0 16px;
  background: #fafbfc;
  border-top: 1px solid var(--sl-border-subtle, #f1f5f9);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.ribbon-items-flow {
  display: flex;
  align-items: center;
  gap: 20px;
}
.ribbon-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  white-space: nowrap;
}
.ribbon-cell .label {
  color: var(--sl-text-secondary, #64748b);
  font-size: 11px;
}
.ribbon-cell .val {
  color: var(--sl-text-heading, #0f172a);
  font-weight: 600;
  font-size: 12px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.ribbon-cell .val.mono {
  font-family: var(--sl-font-mono, "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* ─── 下半部分：设备类别具体配置契约 ─── */
.workbench-bottom-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

/* 36px 纯中文 SubTab 导航条 */
.subtabs-bar {
  height: 36px;
  display: flex;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  padding: 0 16px;
  background: #ffffff;
  flex-shrink: 0;
  gap: 6px;
}
.subtab-btn {
  padding: 0 12px;
  height: 100%;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--sl-text-secondary, #64748b);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.subtab-btn:hover {
  color: var(--sl-primary, #2563eb);
}
.subtab-btn.active {
  color: var(--sl-primary, #2563eb);
  font-weight: 600;
  border-bottom-color: var(--sl-primary, #2563eb);
}
.tab-count-text {
  font-size: 11.5px;
  color: var(--sl-text-secondary, #64748b);
}
.subtab-btn.active .tab-count-text {
  color: var(--sl-primary, #2563eb);
}

/* 主体内容滚动区 (顶格全表格，无外边距) */
.workbench-scroll-pane {
  flex: 1;
  overflow-y: auto;
  padding: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #ffffff;
}
.fullbleed-table-block {
  display: flex;
  flex-direction: column;
}

/* 紧贴边框的一体化表格区块 */
.flat-table-section {
  display: flex;
  flex-direction: column;
  width: 100%;
}
.section-title-bar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  height: 36px;
  padding: 0 16px;
  background: #f8fafc;
  border-top: 1px solid var(--sl-border-base, #e2e8f0);
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  flex-shrink: 0;
}
.flat-table-section:first-child .section-title-bar {
  border-top: none;
}
.section-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  line-height: 1;
}
.section-sub {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
  line-height: 1;
}

/* 满幅零边框扁平表格 */
.industrial-fullbleed-table {
  width: 100%;
  border-radius: 0;
}
.industrial-fullbleed-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}
.industrial-fullbleed-table :deep(th) {
  background: #ffffff !important;
  color: var(--sl-text-secondary, #64748b) !important;
  font-size: 11.5px !important;
  font-weight: 600 !important;
  padding: 6px 16px !important;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0) !important;
}
.industrial-fullbleed-table :deep(td) {
  padding: 7px 16px !important;
  border-bottom: 1px solid var(--sl-border-subtle, #f1f5f9) !important;
  color: var(--sl-text-body, #334155) !important;
  font-size: 12px !important;
}

.instance-bind-cell {
  display: flex;
  align-items: center;
}

/* 纯净分列参数微型列表 (无卡片框、无背景底色) */
.param-clean-table {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 4px 0;
  width: 100%;
}
.param-clean-row {
  display: grid;
  grid-template-columns: 160px 80px 1fr;
  align-items: baseline;
  gap: 10px;
  font-size: 12px;
  line-height: 1.4;
}
.param-clean-name {
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  word-break: break-all;
  white-space: normal;
}
.param-clean-type {
  color: var(--sl-primary, #2563eb);
  font-size: 11px;
}
.param-clean-desc {
  color: var(--sl-text-secondary, #64748b);
  word-break: break-word;
  white-space: normal;
}

.contract-text-list {
  font-size: 12px;
  line-height: 1.5;
}

/* 点位与实例详情抽屉 */
.point-detail-workbench {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
}
.detail-sub-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.detail-sub-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
  margin: 0;
}
.mqtt-topic-detail-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.topic-item {
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.topic-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--sl-text-heading, #0f172a);
}
.topic-code-str {
  font-family: var(--sl-font-mono, monospace);
  font-size: 11.5px;
  background: #0f172a;
  color: #60a5fa;
  padding: 4px 8px;
  border-radius: 4px;
  word-break: break-all;
}
.topic-desc-note {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
}

/* ==========================================================================
   4. 辅助状态类 (无大面积背景色框)
   ========================================================================== */
.m-val.status-val {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  line-height: 1.3;
  font-size: 12px;
  font-weight: 600;
}
.m-val.status-val .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.m-val.status-val.online {
  color: var(--sl-success, #16a34a);
}
.m-val.status-val.online .dot {
  background: var(--sl-success, #16a34a);
}
.m-val.status-val.stale {
  color: var(--sl-warning, #d97706);
}
.m-val.status-val.stale .dot {
  background: var(--sl-warning, #d97706);
}
.m-val.status-val.unknown {
  color: var(--sl-text-secondary, #64748b);
}
.m-val.status-val.unknown .dot {
  background: var(--sl-text-secondary, #64748b);
}

.text-primary { color: var(--sl-primary, #2563eb); }
.text-success { color: var(--sl-success, #16a34a); }
.text-danger { color: var(--sl-danger, #dc2626); }
.text-heading { color: var(--sl-text-heading, #0f172a); }
.text-body { color: var(--sl-text-body, #334155); }
.text-secondary { color: var(--sl-text-secondary, #64748b); }
.mono-text { font-family: var(--sl-font-mono, monospace); }

/* ==========================================================================
   5. 四级按钮体系规范 (.btn-primary-blue, .btn-aliyun-cta, .btn-aliyun, .btn-link)
   ========================================================================== */
.btn-primary-blue {
  height: 28px;
  padding: 0 12px;
  background: var(--sl-primary, #2563eb);
  color: #ffffff;
  border: 1px solid var(--sl-primary, #2563eb);
  border-radius: var(--sl-radius-sm, 6px);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: var(--sl-ease-smooth, all 0.18s ease);
  white-space: nowrap;
}
.btn-primary-blue:hover {
  background: var(--sl-primary-hover, #1d4ed8);
  border-color: var(--sl-primary-hover, #1d4ed8);
}

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
  transition: var(--sl-ease-smooth, all 0.18s ease);
  white-space: nowrap;
}
.btn-aliyun-cta:hover {
  background: var(--sl-primary-light, #eff6ff);
  border-color: var(--sl-primary, #2563eb);
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
  transition: var(--sl-ease-smooth, all 0.18s ease);
  white-space: nowrap;
}
.btn-aliyun:hover {
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
  background: var(--sl-bg-hover, #f8fafc);
}
.btn-aliyun.btn-warning {
  color: var(--sl-warning, #d97706);
  border-color: var(--sl-warning-border, #fde68a);
}
.btn-aliyun.btn-warning:hover {
  background: var(--sl-warning-light, #fffbeb);
}
.btn-aliyun.btn-success {
  color: var(--sl-success, #16a34a);
  border-color: var(--sl-success-border, #bbf7d0);
}
.btn-aliyun.btn-success:hover {
  background: var(--sl-success-light, #f0fdf4);
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

/* ==========================================================================
   6. 全局配置对比抽屉样式 (右侧 100% 满屏抽屉，零页面滚动)
   ========================================================================== */
.diff-drawer-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #0f172a;
  overflow: hidden;
}
.diff-split-container {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-height: 0;
  height: 100%;
}
.code-column {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: var(--sl-radius-sm, 6px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}
.column-header {
  height: 34px;
  padding: 0 12px;
  background: #0f172a;
  border-bottom: 1px solid #334155;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #94a3b8;
  font-size: 11.5px;
  font-weight: 500;
  flex-shrink: 0;
}
.code-box {
  flex: 1;
  padding: 12px;
  margin: 0;
  color: #e2e8f0;
  font-family: var(--sl-font-mono, monospace);
  font-size: 11.5px;
  line-height: 1.55;
  overflow-y: auto;
  overflow-x: auto;
  overscroll-behavior: contain;
  white-space: pre;
}

/* 抽屉通用样式 */
.adapter-register-workbench {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.adapter-register-nav {
  display: flex;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  background: #f8fafc;
  padding: 0 16px;
}
.adapter-register-nav button {
  padding: 10px 16px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 13px;
  font-weight: 500;
  color: var(--sl-text-secondary, #64748b);
  cursor: pointer;
}
.adapter-register-nav button.active {
  color: var(--sl-primary, #2563eb);
  border-bottom-color: var(--sl-primary, #2563eb);
  font-weight: 600;
}
.adapter-register-scroll {
  flex: 1;
  padding: 16px 20px;
}
.register-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.drawer-section-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  border-bottom: 1px solid var(--sl-border-base, #e2e8f0);
  padding-bottom: 8px;
}
.drawer-section-head h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--sl-text-heading, #0f172a);
}
.source-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--sl-text-secondary, #64748b);
  margin-bottom: 10px;
}
.source-copy {
  font-size: 12px;
  color: var(--sl-text-secondary, #64748b);
  margin-bottom: 12px;
}
.save-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  font-size: 12.5px;
}
.save-panel, .review-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}
.compact-empty {
  padding: 30px;
  text-align: center;
  color: var(--sl-text-secondary, #64748b);
  font-size: 12px;
}
.description-input {
  margin-top: 4px;
}

/* 审阅模式类别卡片与 Tab */
.review-category-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
}
.review-category-tab-btn {
  height: 30px;
  padding: 0 12px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  font-size: 12px;
  font-weight: 500;
  color: var(--sl-text-body, #334155);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: var(--sl-ease-smooth, all 0.18s ease);
}
.review-category-tab-btn:hover {
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
}
.review-category-tab-btn.active {
  background: var(--sl-primary-light, #eff6ff);
  border-color: var(--sl-primary, #2563eb);
  color: var(--sl-primary, #2563eb);
  font-weight: 600;
}
.review-category-tab-btn .tab-badge {
  font-size: 11px;
  color: var(--sl-text-secondary, #64748b);
}
.review-category-tab-btn.active .tab-badge {
  color: var(--sl-primary, #2563eb);
}

.review-category-detail-card {
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  background: #ffffff;
  padding: 12px;
  display: flex;
  flex-direction: column;
}
.review-category-info-bar {
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid var(--sl-border-base, #e2e8f0);
  border-radius: var(--sl-radius-sm, 6px);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.review-category-info-bar .info-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.review-category-info-bar .info-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--sl-text-secondary, #64748b);
  width: 65px;
  flex-shrink: 0;
}
</style>
