<template>
  <div class="pwd-shell">
    <div class="pwd-card">
      <header class="brand">
        <span class="brand-mark" aria-hidden="true">
          <img :src="logo" alt="" />
        </span>
        <div class="brand-text">
          <div class="mark-cn">{{ runtime.brand.name }}</div>
          <div class="mark-en">BALANCLOUD</div>
        </div>
      </header>

      <h1>设置新密码</h1>
      <p class="tip">当前账号使用的是初始密码或管理员重置的密码，继续使用前请先设置自己的密码。</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="8-30 位，需包含字母和数字" maxlength="30" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" maxlength="30" @keyup.enter="submit" />
        </el-form-item>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="form-error" />
        <el-button type="primary" class="submit-btn" :loading="submitting" @click="submit">保存并进入系统</el-button>
      </el-form>

      <div class="foot">
        <button type="button" class="link-btn" @click="handleLogout">退出登录</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { updateUserPwd } from '@/api/system/user'
import { setToken } from '@/utils/auth'
import useRuntimeStore from '@/store/modules/runtime'
import useUserStore from '@/store/modules/user'
import logo from '@/assets/logo/logo.png'

defineOptions({ name: 'ChangePassword' })

const router = useRouter()
const runtime = useRuntimeStore()
const userStore = useUserStore()

const formRef = ref()
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateNewPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('新密码不能为空'))
  } else if (value.length < 8 || value.length > 30) {
    callback(new Error('新密码长度需为 8-30 位'))
  } else if (/\s/.test(value)) {
    callback(new Error('新密码不能包含空格'))
  } else if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    callback(new Error('新密码需同时包含字母和数字'))
  } else if (value === form.oldPassword) {
    callback(new Error('新密码不能与当前密码相同'))
  } else {
    callback()
  }
}

const validateConfirm = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入新密码'))
  } else if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ required: true, validator: validateNewPassword, trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

function submit() {
  errorMessage.value = ''
  formRef.value.validate(valid => {
    if (!valid) {
      return
    }
    submitting.value = true
    updateUserPwd(form.oldPassword, form.newPassword).then(res => {
      setToken(res.token)
      userStore.token = res.token
      userStore.pwdResetRequired = false
      ElMessage.success('密码修改成功')
      return userStore.getInfo().catch(() => {})
    }).then(() => {
      // 整页重进，确保动态路由按新会话重新生成
      window.location.replace('/')
    }).catch(error => {
      errorMessage.value = typeof error === 'string' ? error : '密码修改失败，请稍后重试'
    }).finally(() => {
      submitting.value = false
    })
  })
}

function handleLogout() {
  userStore.logOut().finally(() => {
    router.replace('/login')
  })
}

onMounted(() => {
  runtime.load()
})
</script>

<style scoped lang="scss">
.pwd-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 32px 20px;
  background:
    radial-gradient(circle at 15% 12%, rgba(0, 168, 112, 0.16), transparent 42%),
    radial-gradient(circle at 88% 82%, rgba(14, 165, 233, 0.14), transparent 46%),
    linear-gradient(160deg, #f4fbf8 0%, #eef4fb 100%);
}

.pwd-card {
  width: 100%;
  max-width: 440px;
  padding: 34px 36px 28px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.1);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
}

.brand-mark {
  flex: none;
  width: 42px;
  height: 42px;
  filter: drop-shadow(0 6px 14px rgba(4, 96, 70, 0.22));

  img {
    display: block;
    width: 100%;
    height: 100%;
  }
}

.mark-cn {
  color: #0f172a;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
}

.mark-en {
  margin-top: 2px;
  color: #94a3b8;
  font-size: 10px;
  letter-spacing: 2.4px;
}

h1 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 24px;
}

.tip {
  margin: 0 0 22px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.form-error {
  margin-bottom: 14px;
}

.submit-btn {
  width: 100%;
  height: 42px;
  margin-top: 4px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #00a870, #0fb981);
  font-weight: 600;
}

.foot {
  margin-top: 18px;
  text-align: center;
}

.link-btn {
  border: none;
  background: none;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;

  &:hover {
    color: #00a870;
  }
}
</style>
