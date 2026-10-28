<template>
  <view class="mine-page">
    <view class="hero">
      <view class="status-space" :style="nav && nav.statusBar"></view>
      <view class="hero-top" :style="nav && nav.navBar">
        <text class="hero-title">我的</text>
      </view>
      <view class="user">
        <view class="avatar">
          <image v-if="user && user.avatar" :src="user.avatar" class="avatar-img" mode="aspectFill" />
          <text v-else class="avatar-text">{{ avatarText }}</text>
        </view>
        <view class="flex-1">
          <view class="nickname">{{ userStore.displayName }}</view>
          <view class="phone">{{ maskedPhone(user && user.phone) }}</view>
        </view>
        <view class="user-id num">ID {{ user ? user.id : '--' }}</view>
      </view>
    </view>

    <view class="body">
      <view class="card stats">
        <view class="stat" @tap="goOrders">
          <text class="stat-num num">{{ activeCount }}</text>
          <text class="stat-label">进行中</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat" @tap="goOrders">
          <text class="stat-num num">{{ finishedCount }}</text>
          <text class="stat-label">已完成</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat" @tap="goActive">
          <text class="stat-num num">{{ activeCount }}</text>
          <text class="stat-label">充电中</text>
        </view>
      </view>

      <view class="card menu">
        <view v-if="runtime.features.benefits" class="menu-item" @tap="goGrowth">
          <view class="menu-ic">会</view>
          <text class="menu-text">会员权益</text>
          <text class="menu-value">积分 · 优惠券</text>
          <view class="chev"></view>
        </view>
        <view v-if="runtime.features.wallet" class="menu-item" @tap="goWallet">
          <view class="menu-ic">钱</view>
          <text class="menu-text">我的钱包</text>
          <text class="menu-value num">¥ {{ walletBalance }}</text>
          <view class="chev"></view>
        </view>
        <view class="menu-item" @tap="goOrders">
          <view class="menu-ic">单</view>
          <text class="menu-text">我的订单</text>
          <text class="menu-value">查看全部</text>
          <view class="chev"></view>
        </view>
        <view class="menu-item" @tap="goActive">
          <view class="menu-ic">充</view>
          <text class="menu-text">进行中充电</text>
          <view class="chev"></view>
        </view>
        <view class="menu-item" @tap="goVehicles">
          <view class="menu-ic">车</view>
          <text class="menu-text">我的车辆</text>
          <text class="menu-value">{{ vehicleCount }} 辆</text>
          <view class="chev"></view>
        </view>
        <view v-if="runtime.features.reservation" class="menu-item" @tap="goReservation">
          <view class="menu-ic">约</view>
          <text class="menu-text">端口预约</text>
          <text class="menu-value">到点前留桩</text>
          <view class="chev"></view>
        </view>
        <view class="menu-item" @tap="callSupport">
          <view class="menu-ic">电</view>
          <text class="menu-text">联系客服</text>
          <text class="menu-value num">{{ supportPhone }}</text>
          <view class="chev"></view>
        </view>
        <view v-if="staffProfile" class="menu-item" @tap="goStaffWorkbench">
          <view class="menu-ic">维</view>
          <text class="menu-text">运维工作台</text>
          <text class="menu-value">{{ staffProfile.nickName || staffProfile.username }}</text>
          <view class="chev"></view>
        </view>
      </view>

      <view class="card menu">
        <view class="menu-item" @tap="openAgreement('user')">
          <view class="menu-ic">协</view>
          <text class="menu-text">用户协议</text>
          <view class="chev"></view>
        </view>
        <view class="menu-item" @tap="openAgreement('privacy')">
          <view class="menu-ic">私</view>
          <text class="menu-text">隐私政策</text>
          <view class="chev"></view>
        </view>
      </view>

      <view class="card about">
        <view class="about-row">
          <text class="about-label">版本</text>
          <text class="about-value num">0.1.0</text>
        </view>
        <view v-if="runtime.multiTenantEnabled" class="about-row" @tap="switchTenant">
          <text class="about-label">运营商</text>
          <text class="about-value">{{ tenantCode || '默认运营商' }} · 点击切换</text>
        </view>
        <view class="about-row">
          <text class="about-label">开源平台</text>
          <text class="about-value">{{ runtime.brand.name }} BALANCLOUD</text>
        </view>
        <view class="about-note">基于 AGPL-3.0 协议开源，面向低功率多端口充电场景。</view>
      </view>

      <view class="card logout" @tap="logout">退出登录</view>
      <view class="footer">{{ runtime.brand.name }} · 开源充电运营平台</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request from '@/utils/request'
import staffRequest, { getStaffToken } from '@/utils/staffRequest'
import { useUserStore } from '@/stores/user'
import { useOrderStore } from '@/stores/order'
import { maskedPhone, money } from '@/utils/format'
import { SUPPORT_PHONE } from '@/utils/config'
import { getTenantCode } from '@/utils/auth'
import { runtimeConfig as runtime } from '@/utils/runtime'
import { capsuleNav } from '@/utils/nav'

const nav = capsuleNav()
const userStore = useUserStore()
const orderStore = useOrderStore()

const user = computed(() => userStore.user)
const tenantCode = computed(() => getTenantCode())
const stationPhone = ref('')
const supportPhone = computed(() => runtime.brand.servicePhone || stationPhone.value || SUPPORT_PHONE)
const activeCount = ref(0)
const finishedCount = ref(0)
const vehicleCount = ref(0)
const walletBalance = ref('0.00')
const staffProfile = ref(null)

const avatarText = computed(() => {
  const name = userStore.displayName || '充'
  return name.slice(0, 1)
})

async function load() {
  try {
    await userStore.ensureLogin()
    const [activePage, finishedPage, stations, vehicles, wallet] = await Promise.all([
      request({ url: '/api/app/orders', data: { status: 'ACTIVE', pageNum: 1, pageSize: 1 } }),
      request({ url: '/api/app/orders', data: { status: 'FINISHED', pageNum: 1, pageSize: 1 } }),
      request({ url: '/api/app/stations' }).catch(() => []),
      request({ url: '/api/app/vehicles' }).catch(() => []),
      runtime.features.wallet
        ? request({ url: '/api/app/wallet' }).catch(() => null)
        : Promise.resolve(null)
    ])
    activeCount.value = (activePage && activePage.total) || 0
    finishedCount.value = (finishedPage && finishedPage.total) || 0
    vehicleCount.value = (vehicles && vehicles.length) || 0
    walletBalance.value = money(wallet && wallet.balance)
    const firstWithPhone = (stations || []).find((s) => s.phone)
    if (firstWithPhone) stationPhone.value = firstWithPhone.phone
    await orderStore.refreshActive().catch(() => {})
    await probeStaff()
  } catch (e) {
    if (e.code !== 401) {
      // 静默失败，页面仍可展示本地资料
    }
  }
}

/**
 * 运维入口由服务端判定：只有后台账号能在 /staff/profile 拿到 200，
 * 探不到（无运维会话、非员工、需要改密）都按普通用户处理，不在前端猜身份。
 */
async function probeStaff() {
  if (!getStaffToken()) {
    staffProfile.value = null
    return
  }
  try {
    staffProfile.value = await staffRequest({ url: '/api/v1/staff/profile', silent: true })
  } catch (e) {
    staffProfile.value = null
  }
}

function goStaffWorkbench() {
  uni.navigateTo({ url: '/pkg-staff/pages/workbench/index' })
}

function goOrders() {
  uni.switchTab({ url: '/pages/order/list' })
}

function goVehicles() {
  uni.navigateTo({ url: '/pkg-standard/pages/vehicle/list' })
}

function goReservation() {
  uni.navigateTo({ url: '/pkg-standard/pages/reservation/index' })
}

function goWallet() {
  uni.navigateTo({ url: '/pkg-standard/pages/wallet/index' })
}

function goGrowth() {
  uni.navigateTo({ url: '/pkg-standard/pages/growth/index' })
}

/** 换运营商在登录页改编码，改完重新登录才换会话；直接退回来说明用户不改，旧会话继续可用。 */
function switchTenant() {
  uni.navigateTo({ url: '/pages/login/login' })
}

async function goActive() {
  try {
    const active = await orderStore.refreshActive()
    if (active) {
      uni.navigateTo({ url: `/pages/charge/charging?orderId=${active.id}` })
    } else {
      uni.switchTab({ url: '/pages/order/list' })
    }
  } catch (e) {
    uni.switchTab({ url: '/pages/order/list' })
  }
}

function callSupport() {
  uni.makePhoneCall({ phoneNumber: String(supportPhone.value) })
}

function openAgreement(type) {
  uni.navigateTo({ url: `/pkg-standard/pages/common/agreement?type=${type}` })
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '退出后将清除本地登录状态，确定退出吗？',
    confirmColor: '#00a870',
    success: (res) => {
      if (res.confirm) userStore.logout()
    }
  })
}

onShow(load)
</script>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  background: #f5f7f6;
}

.hero {
  padding: 0 32rpx 120rpx;
  background: linear-gradient(160deg, #06382a 0%, #08553f 60%, #0a7a5a 100%);
  color: #ffffff;
  border-radius: 0 0 44rpx 44rpx;
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -100rpx;
  top: -110rpx;
  width: 380rpx;
  height: 380rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(52, 211, 153, 0.24), transparent 68%);
}

.status-space {
  height: calc(var(--status-bar-height) + 12rpx);
}

.hero-top {
  height: 70rpx;
  display: flex;
  align-items: center;
  position: relative;
  z-index: 1;
}

.hero-title {
  font-size: 28rpx;
  font-weight: 600;
  opacity: 0.9;
}

.user {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  position: relative;
  z-index: 1;
}

.avatar {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  border: 3rpx solid rgba(255, 255, 255, 0.3);
  margin-right: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
}

.avatar-text {
  font-size: 42rpx;
  font-weight: 700;
}

.nickname {
  font-size: 36rpx;
  font-weight: 700;
}

.phone {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.6);
}

.user-id {
  font-size: 18rpx;
  font-weight: 400;
  color: rgba(255, 255, 255, 0.4);
}

.body {
  margin-top: -72rpx;
  padding: 0 32rpx calc(140rpx + env(safe-area-inset-bottom));
  position: relative;
  z-index: 2;
}

.stats {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-num {
  font-size: 40rpx;
  color: #101828;
}

.stat-label {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #98a2b3;
}

.stat-divider {
  width: 2rpx;
  height: 44rpx;
  background: #f5f7f6;
}

.menu {
  padding: 4rpx 24rpx;
  margin-top: 20rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 24rpx 4rpx;
}

.menu-item + .menu-item {
  border-top: 2rpx solid #f5f7f6;
}

.menu-ic {
  width: 60rpx;
  height: 60rpx;
  border-radius: 18rpx;
  margin-right: 20rpx;
  background: #f3f7f5;
  color: #00895c;
  font-size: 26rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-text {
  flex: 1;
  font-size: 26rpx;
  color: #101828;
}

.menu-value {
  font-size: 20rpx;
  font-weight: 400;
  color: #98a2b3;
  margin-right: 8rpx;
}

.chev {
  width: 12rpx;
  height: 12rpx;
  border-top: 3rpx solid #c6cdd5;
  border-right: 3rpx solid #c6cdd5;
  transform: rotate(45deg);
}

.about {
  margin-top: 20rpx;
  padding: 10rpx 24rpx 22rpx;
}

.about-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 2rpx solid #f5f7f6;
  font-size: 22rpx;
}

.about-label {
  color: #98a2b3;
}

.about-value {
  color: #475467;
}

.about-note {
  margin-top: 18rpx;
  font-size: 20rpx;
  line-height: 1.7;
  color: #98a2b3;
}

.logout {
  margin-top: 20rpx;
  padding: 28rpx;
  text-align: center;
  font-size: 26rpx;
  color: #d92d20;
}

.footer {
  margin-top: 40rpx;
  text-align: center;
  font-size: 18rpx;
  color: #c6cdd5;
}
</style>
