<template>
  <el-dialog :model-value="modelValue" :title="mode === 'create' ? '新建用户' : '编辑用户'" width="500px" destroy-on-close @update:model-value="$emit('update:modelValue', $event)" @closed="reset">
    <el-form ref="refForm" :model="f" :rules="rules" label-position="top" class="uf-form">
      <div class="uf-grid">
        <el-form-item label="用户名" prop="userName"><el-input v-model="f.userName" placeholder="登录账号" /></el-form-item>
        <el-form-item :label="mode === 'create' ? '密码' : '新密码（留空不变）'" :prop="mode === 'create' ? 'password' : undefined"><el-input v-model="f.password" type="password" show-password :placeholder="mode === 'create' ? '设置密码' : '留空保持原密码'" /></el-form-item>
        <el-form-item label="角色" prop="roleName"><el-select v-model="f.roleName"><el-option label="系统管理员" value="system_admin" /><el-option label="实验室管理员" value="lab_admin" /><el-option label="研究员" value="researcher" /><el-option label="观察员" value="observer" /></el-select></el-form-item>
        <el-form-item label="实验室" prop="lab"><el-input v-model="f.lab" placeholder="所属实验室" /></el-form-item>
      </div>
      <el-alert v-if="mode === 'edit' && roleChanged" type="warning" :closable="false" show-icon title="角色变更后默认权限将重新计算" style="margin-bottom:12px" />
      <el-form-item label="说明"><el-input v-model="f.description" type="textarea" :rows="2" placeholder="课题组、岗位或备注" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">{{ mode === 'create' ? '创建' : '保存' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { type FormInstance, type FormRules } from 'element-plus'

const props = withDefaults(defineProps<{ modelValue: boolean; mode: string; user?: any; loading?: boolean }>(), { user: null, loading: false })
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; submit: [p: any] }>()

const refForm = ref<FormInstance>()
const originalRole = ref('')
const f = ref({ id: 0, userName: '', password: '', roleName: 'researcher', lab: '', description: '' })
const rules: FormRules = { userName: [{ required: true, message: '必填' }], roleName: [{ required: true, message: '必选' }], lab: [{ required: true, message: '必填' }], ...(props.mode === 'create' ? { password: [{ required: true, message: '必填' }] } : {}) }
const roleChanged = computed(() => props.mode === 'edit' && originalRole.value && originalRole.value !== f.value.roleName)

watch(() => [props.user, props.mode, props.modelValue], ([u, m, v]) => {
  if (m === 'edit' && u && v) { const r = u?.roleName || u?.role || ''; originalRole.value = r; f.value = { id: u.id, userName: u.userName || '', password: '', roleName: r || 'researcher', lab: u.lab || '', description: (() => { const s = u?.userBasicInfo ?? u?.userBasicinfo; if (!s) return ''; if (typeof s === 'object') return s.description || ''; try { return JSON.parse(s).description || '' } catch { return '' } })() } }
  if (m === 'create' && v) { f.value = { id: 0, userName: '', password: '', roleName: 'researcher', lab: '', description: '' }; originalRole.value = '' }
})

function reset() { refForm.value?.resetFields() }
async function submit() { if (!refForm.value) return; await refForm.value.validate(v => { if (v) emit('submit', { ...f.value }) }) }
</script>

<style scoped>
.uf-form :deep(.el-select){width:100%}
.uf-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}
</style>