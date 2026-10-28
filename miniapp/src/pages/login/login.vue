<template>
  <view class="login-page">
    <view class="brand-block">
      <view class="logo">{{ runtime.brand.name.slice(0, 1) }}</view>
      <view class="brand-name">{{ runtime.brand.name }}</view>
      <view class="brand-sub">{{ runtime.brand.subtitle }}</view>
    </view>

    <view v-if="runtime.maintenance.enabled" class="maintenance-tip">
      <view class="maintenance-title">平台维护中</view>
      <view class="maintenance-text">{{ runtime.maintenance.message || '暂停新充电业务，请稍后再试。' }}</view>
    </view>

    <view class="card login-card">
      <view class="welcome">欢迎使用</view>
      <view class="muted small welcome-sub">登录后即可扫码充电、查看订单</view>

      <view v-if="runtime.multiTenantEnabled" class="field">
        <text class="label">运营商编码</text>
        <input
          v-model="tenantCode"
          class="input"
          maxlength="32"
          placeholder="留空则使用默认运营商"
          placeholder-class="ph"
        />
      </view>
      <view class="field">
        <text class="label">手机号</text>
        <input
          v-model="phone"
          class="input"
          type="number"
          maxlength="11"
          placeholder="请输入 11 位手机号"
          placeholder-class="ph"
        />
      </view>
      <view class="field">
        <text class="label">验证码</text>
        <input class="input" value="123456" disabled />
      </view>

      <button class="btn-primary login-btn" :disabled="submitting" @tap="devLogin">
        {{ submitting ? '登录中…' : '开发登录' }}
      </button>

      <!-- #ifdef MP-WEIXIN -->
      <button class="wechat-btn" :disabled="submitting" @tap="wechatLogin">微信一键登录</button>
      <!-- #endif -->

      <view class="agree" @tap="agreed = !agreed">
        <view :class="['checkbox', agreed ? 'checked' : '']"></view>
        <text class="agree-text">我已阅读并同意</text>
        <text class="agree-link" @tap.stop="openAgreement('user')">《用户协议》</text>
        <text class="agree-text">与</text>
        <text class="agree-link" @tap.stop="openAgreement('privacy')">《隐私政策》</text>
      </view>

      <view class="muted small note">
        开发登录仅用于本地与演示环境；配置微信 AppID 后可使用微信一键登录。
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { silentLogin, DEFAULT_DEV_PHONE, getTenantCode, setTenantCode } from '@/utils/auth'
import { showError } from '@/utils/request'
import { runtimeConfig as runtime } from '@/utils/runtime'

const userStore = useUserStore()
const phone = ref(DEFAULT_DEV_PHONE)
const tenantCode = ref(getTenantCode())
const agreed = ref(true)
const submitting = ref(false)

async function afterLogin() {
  await userStore.fetchMe()
  uni.reLaunch({ url: '/pages/index/index' })
}

async function devLogin() {
  if (submitting.value) return
  if (!/^1\d{10}$/.test(phone.value)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  if (!agreed.value) {
    uni.showToast({ title: '请先阅读并同意用户协议', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    setTenantCode(tenantCode.value)
    await userStore.devLogin(phone.value)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(afterLogin, 500)
  } catch (e) {
    showError(e)
  } finally {
    submitting.value = false
  }
}

async function wechatLogin() {
  if (submitting.value) return
  if (!agreed.value) {
    uni.showToast({ title: '请先阅读并同意用户协议', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    setTenantCode(tenantCode.value)
    await silentLogin()
    await afterLogin()
  } catch (e) {
    showError(e)
  } finally {
    submitting.value = false
  }
}

function openAgreement(type) {
  uni.navigateTo({ url: `/pkg-standard/pages/common/agreement?type=${type}` })
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  padding: 120rpx 40rpx 60rpx;
  background:
    radial-gradient(circle at 20% 0%, rgba(0, 168, 112, 0.1), transparent 42%),
    #f5f7f6;
}

.brand-block {
  text-align: center;
}

.logo {
  width: 120rpx;
  height: 120rpx;
  margin: 0 auto;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #00a870, #00895c);
  color: #ffffff;
  font-size: 60rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16rpx 32rpx rgba(0, 168, 112, 0.28);
}

.brand-name {
  margin-top: 24rpx;
  font-size: 40rpx;
  font-weight: 700;
  letter-spacing: 3rpx;
}

.brand-sub {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #475467;
  letter-spacing: 2rpx;
}

.login-card {
  margin-top: 60rpx;
  padding: 40rpx 34rpx;
}

.maintenance-tip {
  margin-top: 32rpx;
  padding: 22rpx 26rpx;
  border-radius: 20rpx;
  background: #fff6e8;
  border: 2rpx solid #fde8c4;
}

.maintenance-tip + .login-card {
  margin-top: 24rpx;
}

.maintenance-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #b54708;
}

.maintenance-text {
  margin-top: 6rpx;
  font-size: 22rpx;
  line-height: 1.55;
  color: #b56a19;
}

.welcome {
  font-size: 34rpx;
  font-weight: 700;
}

.welcome-sub {
  margin-top: 8rpx;
}

.field {
  margin-top: 30rpx;
  padding: 18rpx 24rpx;
  background: #f7f9f8;
  border-radius: 18rpx;
}

.label {
  font-size: 22rpx;
  color: #98a2b3;
}

.input {
  margin-top: 8rpx;
  height: 56rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.ph {
  color: #c6cdd5;
  font-weight: 400;
  font-size: 27rpx;
}

.login-btn {
  margin-top: 36rpx;
}

.wechat-btn {
  margin-top: 20rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 999rpx;
  background: #e6f7f1;
  border: 2rpx solid #c6ebdd;
  color: #00895c;
  font-size: 29rpx;
  font-weight: 600;
}

.wechat-btn::after {
  border: none;
}

.agree {
  margin-top: 28rpx;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  font-size: 22rpx;
}

.checkbox {
  width: 30rpx;
  height: 30rpx;
  border-radius: 8rpx;
  border: 2rpx solid #c6cdd5;
  margin-right: 10rpx;
  position: relative;
}

.checkbox.checked {
  background: #00a870;
  border-color: #00a870;
}

.checkbox.checked::after {
  content: '';
  position: absolute;
  left: 8rpx;
  top: 4rpx;
  width: 10rpx;
  height: 16rpx;
  border: 4rpx solid #ffffff;
  border-top: none;
  border-left: none;
  transform: rotate(45deg);
}

.agree-text {
  color: #98a2b3;
}

.agree-link {
  color: #00a870;
}

.note {
  margin-top: 26rpx;
  line-height: 1.7;
  font-size: 20rpx;
}
</style>
