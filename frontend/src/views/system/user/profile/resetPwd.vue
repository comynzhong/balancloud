<template>
  <el-form ref="pwdRef" :model="form" :rules="rules" label-width="90px" class="profile-form">
    <el-form-item label="旧密码" prop="oldPassword">
      <el-input v-model="form.oldPassword" placeholder="请输入旧密码" type="password" show-password />
    </el-form-item>
    <el-form-item label="新密码" prop="newPassword">
      <el-input v-model="form.newPassword" placeholder="请输入新密码" type="password" show-password />
      <div class="form-hint">长度 8-30 位，需同时包含字母和数字，不能含空格，且不能与旧密码相同。</div>
    </el-form-item>
    <el-form-item label="确认密码" prop="confirmPassword">
      <el-input v-model="form.confirmPassword" placeholder="请再次输入新密码" type="password" show-password />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { updateUserPwd } from '@/api/system/user'
import { setToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'

const { proxy } = getCurrentInstance()
const userStore = useUserStore()

const pwdRef = ref(null)
const saving = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const equalToPassword = (rule, value, callback) => {
  callback(form.newPassword === value ? undefined : new Error('两次输入的密码不一致'))
}

const rules = {
  oldPassword: [{ required: true, message: '旧密码不能为空', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 8, max: 30, message: '长度需为 8-30 位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d)\S+$/, message: '需同时包含字母和数字且不含空格', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '确认密码不能为空', trigger: 'blur' },
    { validator: equalToPassword, trigger: 'blur' }
  ]
}

/**
 * 改密会重建会话（其它设备上的旧会话失效），所以要把新 token 换进本地，否则下一次请求就 401。
 */
function submit() {
  pwdRef.value.validate(valid => {
    if (!valid) {
      return
    }
    saving.value = true
    updateUserPwd(form.oldPassword, form.newPassword)
      .then(response => {
        setToken(response.token)
        userStore.token = response.token
        userStore.pwdResetRequired = false
        proxy.$modal.msgSuccess('密码已修改')
        pwdRef.value.resetFields()
      })
      .finally(() => {
        saving.value = false
      })
  })
}
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
