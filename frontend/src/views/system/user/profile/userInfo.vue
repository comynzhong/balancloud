<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="profile-form">
    <el-form-item label="登录名">
      <el-input :model-value="user.userName" disabled />
      <div class="form-hint">登录名由管理员创建后不可自行修改。</div>
    </el-form-item>
    <el-form-item label="姓名" prop="nickName">
      <el-input v-model="form.nickName" maxlength="30" show-word-limit placeholder="请输入姓名" />
    </el-form-item>
    <el-form-item label="手机号码" prop="phoneNumber">
      <el-input v-model="form.phoneNumber" maxlength="20" placeholder="请输入手机号码" />
    </el-form-item>
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" maxlength="50" placeholder="请输入邮箱" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      <el-button @click="reset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { updateUserProfile } from '@/api/system/user'

const props = defineProps({
  user: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['saved'])

const { proxy } = getCurrentInstance()

const formRef = ref(null)
const saving = ref(false)
const form = ref({ nickName: '', phoneNumber: '', email: '' })

const rules = {
  nickName: [
    { required: true, message: '姓名不能为空', trigger: 'blur' },
    { max: 30, message: '姓名长度不能超过 30', trigger: 'blur' }
  ],
  phoneNumber: [
    { pattern: /^\d{6,20}$/, message: '请输入 6-20 位数字号码', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] },
    { max: 50, message: '邮箱长度不能超过 50', trigger: 'blur' }
  ]
}

function fill() {
  form.value = {
    nickName: props.user.nickName || '',
    phoneNumber: props.user.phoneNumber || '',
    email: props.user.email || ''
  }
}

function submit() {
  formRef.value.validate(valid => {
    if (!valid) {
      return
    }
    saving.value = true
    updateUserProfile(form.value)
      .then(() => {
        proxy.$modal.msgSuccess('资料已保存')
        emit('saved', { ...form.value })
      })
      .finally(() => {
        saving.value = false
      })
  })
}

watch(() => props.user, fill, { immediate: true })
</script>

<style lang="scss" scoped>
.profile-form {
  max-width: 520px;
  padding-top: 4px;
}

.form-hint {
  color: var(--bc-text-muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
