import re

file_path = "src/views/device/components/deviceModel/DeviceModelDetail.vue"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Replace the OP state rendering block
old_op_state = """                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>功能状态</h3>
                    <span class="section-count">{{ selectedOpStates.length }} 个状态</span>
                  </div>
                  <div v-if="selectedOpStates.length === 0" class="compact-empty inline-empty">暂无功能状态</div>
                  <div v-else class="state-token-panel">
                    <span v-for="state in selectedOpStates" :key="state.stateName" class="filled-state-token op-state-token">{{ state.stateName }}</span>
                  </div>
                </section>"""

new_op_state = """                <section class="info-section section-cluster compact-section">
                  <div class="section-title">
                    <h3>功能状态 (按分区)</h3>
                    <span class="section-count">{{ selectedOpStateRegions.length }} 个分区</span>
                  </div>
                  <div v-if="selectedOpStateRegions.length === 0" class="compact-empty inline-empty">暂无功能状态分区</div>
                  <div v-else class="region-token-panels">
                    <div v-for="region in selectedOpStateRegions" :key="region.regionName" class="region-token-panel" style="margin-bottom: 12px;">
                      <div class="region-title" style="margin-bottom: 8px; font-weight: bold; color: var(--el-text-color-regular);">
                        <el-tag size="small" effect="dark" style="margin-right: 8px;">{{ region.regionName }}</el-tag>
                        <span style="font-size: 12px; color: var(--el-text-color-secondary);">初始状态: {{ region.initialStateName }}</span>
                      </div>
                      <div class="state-token-panel">
                        <span v-for="state in region.states" :key="state.stateName" class="filled-state-token op-state-token">{{ state.stateName }}</span>
                      </div>
                    </div>
                  </div>
                </section>"""

content = content.replace(old_op_state, new_op_state)

# 2. Replace the computed property
content = content.replace(
    "const selectedOpStates = computed(() => asArray(props.model?.opState?.states))",
    "const selectedOpStateRegions = computed(() => asArray(props.model?.opState?.regions))"
)

# 3. Add region tag in transitions
old_transition_title = """<div class="transition-title-with-space"><el-tag size="small" effect="plain">{{ row.stateSpace }}</el-tag><strong>{{ row.description || '未命名规则' }}</strong></div>"""
new_transition_title = """<div class="transition-title-with-space">
                          <el-tag size="small" effect="plain">{{ row.stateSpace }}</el-tag>
                          <el-tag v-if="row.regionName" size="small" type="info" effect="plain" style="margin-left: 4px;">{{ row.regionName }}</el-tag>
                          <strong style="margin-left: 8px;">{{ row.description || '未命名规则' }}</strong>
                        </div>"""

content = content.replace(old_transition_title, new_transition_title)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Fixed!")
