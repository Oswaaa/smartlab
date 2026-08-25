<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'create' ? '新建用户' : '编辑用户资料'"
    width="520px"
    destroy-on-close
    class="uf-custom-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="reset"
  >
    <el-form
      ref="refForm"
      :model="f"
      :rules="rules"
      label-position="top"
      class="uf-form"
    >
      <div class="uf-grid">
        <el-form-item label="登录账号" prop="userName">
          <el-input v-model="f.userName" placeholder="请输入登录用户名" size="small" />
        </el-form-item>
        <el-form-item
          :label="mode === 'create' ? '登录密码' : '重置密码 (留空不变)'"
          :prop="mode === 'create' ? 'password' : undefined"
        >
          <el-input
            v-model="f.password"
            type="password"
            show-password
            size="small"
            :placeholder="mode === 'create' ? '设置登录密码' : '留空保持原密码不变'"
          />
        </el-form-item>
        <el-form-item label="系统角色" prop="roleName">
          <el-select v-model="f.roleName" size="small" style="width: 100%;">
            <el-option label="系统管理员" value="system_admin" />
            <el-option label="实验室管理员" value="lab_admin" />
            <el-option label="研究员" value="researcher" />
            <el-option label="观察员" value="observer" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属实验室" prop="lab">
          <el-input v-model="f.lab" placeholder="例如: 智能分子生化实验室" size="small" />
        </el-form-item>
      </div>

      <div v-if="mode === 'edit' && roleChanged" class="uf-warning-tip">
        <span>● 角色变更后，该用户的默认继承权限将按新角色重新计算。</span>
      </div>

      <el-form-item label="用户说明与备注" style="margin-bottom: 0;">
        <el-input
          v-model="f.description"
          type="textarea"
          :rows="2"
          placeholder="请输入课题组、岗位职责或备注信息"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="uf-dialog-footer">
        <button class="btn-aliyun" type="button" @click="$emit('update:modelValue', false)">
          取消
        </button>
        <button
          class="btn-primary-blue"
          type="button"
          :disabled="loading"
          @click="submit"
        >
          {{ loading ? '正在提交...' : (mode === 'create' ? '确认创建' : '保存修改') }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { type FormInstance, type FormRules } from 'element-plus'

const props = withDefaults(
  defineProps<{ modelValue: boolean; mode: string; user?: any; loading?: boolean }>(),
  { user: null, loading: false }
)
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; submit: [p: any] }>()

const refForm = ref<FormInstance>()
const originalRole = ref('')
const f = ref({ id: 0, userName: '', password: '', roleName: 'researcher', lab: '', description: '' })

const rules: FormRules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  roleName: [{ required: true, message: '请选择系统角色', trigger: 'change' }],
  lab: [{ required: true, message: '请输入归属实验室', trigger: 'blur' }],
  ...(props.mode === 'create' ? { password: [{ required: true, message: '请输入密码', trigger: 'blur' }] } : {})
}

const roleChanged = computed(() => props.mode === 'edit' && originalRole.value && originalRole.value !== f.value.roleName)

watch(
  () => [props.user, props.mode, props.modelValue],
  ([u, m, v]) => {
    if (m === 'edit' && u && v) {
      const r = u?.roleName || u?.role || ''
      originalRole.value = r
      f.value = {
        id: u.id,
        userName: u.userName || '',
        password: '',
        roleName: r || 'researcher',
        lab: u.lab || '',
        description: (() => {
          const s = u?.userBasicInfo ?? u?.userBasicinfo
          if (!s) return ''
          if (typeof s === 'object') return s.description || ''
          try {
            return JSON.parse(s).description || ''
          } catch {
            return ''
          }
        })()
      }
    }
    if (m === 'create' && v) {
      f.value = { id: 0, userName: '', password: '', roleName: 'researcher', lab: '', description: '' }
      originalRole.value = ''
    }
  }
)

function reset() {
  refForm.value?.resetFields()
}

async function submit() {
  if (!refForm.value) return
  await refForm.value.validate(v => {
    if (v) emit('submit', { ...f.value })
  })
}
</script>

<style scoped>
/* ==========================================================================
   用户表单弹窗 - 28px 标准控件与四级按钮体系
   ========================================================================== */
.uf-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.uf-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.uf-form :deep(.el-form-item__label) {
  font-size: 12px !important;
  font-weight: 600 !important;
  color: #334155 !important;
  padding-bottom: 2px !important;
}

.uf-warning-tip {
  padding: 6px 12px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 4px;
  color: #92400e;
  font-size: 11.5px;
  margin-bottom: 12px;
}

.uf-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
}

/* 四级按钮体系标准 */
.btn-primary-blue {
  height: 28px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 600;
  color: #ffffff;
  background: #2563eb;
  border: 1px solid #2563eb;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-primary-blue:hover:not(:disabled) {
  background: #1d4ed8;
  border-color: #1d4ed8;
}
.btn-primary-blue:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-aliyun {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  color: #334155;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}
.btn-aliyun:hover {
  border-color: #94a3b8;
  background: #f8fafc;
  color: #0f172a;
}
</style>