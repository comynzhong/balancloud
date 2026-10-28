<template>
  <view class="charging-page">
    <view class="charge-head">
      <view class="status-space" :style="nav && nav.statusBar"></view>
      <view class="nav-row" :style="nav && nav.navBar">
        <view class="nav-back" @tap="goBack"><view class="back-arrow"></view></view>
        <text class="nav-title">充电中</text>
        <view class="nav-space"></view>
      </view>

      <view v-if="!order" class="loading">正在加载订单…</view>

      <template v-else>
        <view class="state">
          <view :class="['state-dot', active ? 'on' : '']"></view>
          <text>{{ orderStatus(order.status) }}</text>
          <text v-if="order.status === 'PENDING_START'" class="state-hint">等待设备响应</text>
          <text v-else-if="order.status === 'STOPPING'" class="state-hint">正在结算</text>
        </view>

        <view class="ring-wrap">
          <view class="ring" :style="ringStyle">
            <view class="ring-inner">
              <view class="ring-cur">¥</view>
              <view class="ring-fee num">{{ money(order.totalFee) }}</view>
              <view class="ring-label">当前费用（元）</view>
              <view class="ring-limit">上限 ¥{{ money(order.chargeLimitAmount) }}</view>
            </view>
          </view>
        </view>

        <view class="hero-stats">
          <view class="hero-stat">
            <text class="hero-value num">{{ kwh(order.energyKwh) }}</text>
            <text class="hero-label">已充 kWh</text>
          </view>
          <view class="hero-divider"></view>
          <view class="hero-stat">
            <text class="hero-value num">{{ duration(order.durationSeconds) }}</text>
            <text class="hero-label">已充时长</text>
          </view>
          <view class="hero-divider"></view>
          <view class="hero-stat">
            <text class="hero-value num">{{ livePower }}</text>
            <text class="hero-label">当前功率 kW</text>
          </view>
        </view>
      </template>
    </view>

    <view class="body" v-if="order">
      <view class="card progress-card">
        <view class="progress-head">
          <text>时长进度</text>
          <text class="num progress-value">{{ duration(order.durationSeconds) }} / {{ order.maxDurationMinutes }} 分钟</text>
        </view>
        <view class="bar"><view class="bar-inner" :style="{ width: timePercent + '%' }"></view></view>
        <view class="progress-head second">
          <text>金额进度</text>
          <text class="num progress-value">{{ money(order.totalFee) }} / {{ money(order.chargeLimitAmount) }} 元</text>
        </view>
        <view class="bar"><view class="bar-inner amber" :style="{ width: amountPercent + '%' }"></view></view>
        <view class="progress-hint">达到时长或金额上限时，平台将自动停止充电并结算。</view>
      </view>

      <view class="card device-card">
        <view class="device-head">
          <view class="flex-1">
            <view class="device-name ellipsis">{{ order.stationName }} · {{ order.portNo }} 号端口</view>
            <view class="device-sub">{{ order.deviceName }} · {{ order.deviceNo }}</view>
          </view>
          <text class="chip chip-gray">{{ order.rateStrategyName || '平台计费' }}</text>
        </view>
        <view class="live-grid">
          <view class="live-cell">
            <text class="live-value num">{{ live.voltage !== null && live.voltage !== undefined ? Number(live.voltage).toFixed(1) : '--' }}</text>
            <text class="live-label">电压 V</text>
          </view>
          <view class="live-cell">
            <text class="live-value num">{{ live.currentA !== null && live.currentA !== undefined ? Number(live.currentA).toFixed(2) : '--' }}</text>
            <text class="live-label">电流 A</text>
          </view>
          <view class="live-cell">
            <text class="live-value num">{{ live.temperature !== null && live.temperature !== undefined ? Number(live.temperature).toFixed(1) : '--' }}</text>
            <text class="live-label">温度 ℃</text>
          </view>
        </view>
      </view>

      <view class="card fee-card">
        <view class="fee-head">
          <text class="fee-title">费用构成</text>
          <text class="muted tiny">实时计算</text>
        </view>
        <view class="fee-row">
          <text>时长费</text>
          <text class="fee-val num">{{ money(order.timeFee) }} 元</text>
        </view>
        <view class="fee-row">
          <text>电费</text>
          <text class="fee-val num">{{ money(order.energyFee) }} 元</text>
        </view>
        <view class="fee-row">
          <text>服务费</text>
          <text class="fee-val num">{{ money(order.serviceFee) }} 元</text>
        </view>
        <view class="fee-row total">
          <text>合计</text>
          <text class="fee-val brand num">{{ money(order.totalFee) }} 元</text>
        </view>
      </view>

      <view class="stop-area">
        <button v-if="active" class="stop-btn" :disabled="stopping" @tap="stopCharging">
          {{ stopping ? '正在停止…' : order.status === 'STOPPING' ? '结算中，请稍候' : '停止充电' }}
        </button>
        <button v-else class="btn-primary" @tap="goDetail">查看订单详情</button>
        <view class="stop-tip">开始于 {{ datetime(order.startTime) }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { useOrderStore } from '@/stores/order'
import { money, kwh, duration, datetime, orderStatus, isActive } from '@/utils/format'
import { capsuleNav } from '@/utils/nav'

const nav = capsuleNav()
const userStore = useUserStore()
const orderStore = useOrderStore()

const orderId = ref(null)
const order = ref(null)
const stopping = ref(false)
let timer = null
let inFlight = false
let redirected = false

const active = computed(() => order.value && isActive(order.value.status))
const live = computed(() => (order.value && order.value.live) || {})
const livePower = computed(() => {
  const value = live.value.powerKw
  return value === null || value === undefined ? '--' : Number(value).toFixed(3)
})

const timePercent = computed(() => {
  if (!order.value) return 0
  const limit = Number(order.value.maxDurationMinutes || 0) * 60
  if (!limit) return 0
  return Math.min(100, Math.round((Number(order.value.durationSeconds || 0) / limit) * 100))
})

const amountPercent = computed(() => {
  if (!order.value) return 0
  const limit = Number(order.value.chargeLimitAmount || 0)
  if (!limit) return 0
  return Math.min(100, Math.round((Number(order.value.totalFee || 0) / limit) * 100))
})

const ringStyle = computed(() => {
  const deg = Math.max(2, Math.round((amountPercent.value / 100) * 360))
  return `background: conic-gradient(from 180deg, #34D399 0deg, #10B981 ${deg}deg, rgba(255,255,255,0.08) ${deg}deg 360deg)`
})

async function fetchOrder() {
  if (inFlight || !orderId.value) return
  inFlight = true
  try {
    const data = await request({ url: `/api/app/orders/${orderId.value}` })
    order.value = data
    if (data && !isActive(data.status)) {
      orderStore.clearActive()
      stopTimer()
      if (!redirected) {
        redirected = true
        setTimeout(() => {
          uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` })
        }, 900)
      }
    }
  } catch (e) {
    if (e.code !== 401) {
      // 轮询失败不打断页面，下一次继续
      console.warn('poll order failed', e.message)
    }
  } finally {
    inFlight = false
  }
}

function startTimer() {
  stopTimer()
  timer = setInterval(fetchOrder, 3000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function stopCharging() {
  if (stopping.value || !order.value) return
  uni.showModal({
    title: '停止充电',
    content: '停止后将立即结算本次充电费用，确定停止吗？',
    confirmText: '停止',
    confirmColor: '#00a870',
    success: async (res) => {
      if (!res.confirm) return
      stopping.value = true
      try {
        await request({ url: `/api/app/orders/${orderId.value}/stop`, method: 'POST' })
        uni.showToast({ title: '停止指令已下发', icon: 'none' })
        await fetchOrder()
      } catch (e) {
        if (e.code !== 401) showError(e)
        await fetchOrder()
      } finally {
        stopping.value = false
      }
    }
  })
}

function goDetail() {
  uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` })
}

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: '/pages/index/index' })
    }
  })
}

onLoad(async (options) => {
  orderId.value = options.orderId
  try {
    await userStore.ensureLogin()
    await fetchOrder()
  } catch (e) {
    if (e.code === 403) {
      uni.showToast({ title: '无权查看该订单', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 1200)
    } else if (e.code !== 401) {
      showError(e)
    }
  }
})

onShow(() => {
  if (orderId.value) {
    fetchOrder()
    startTimer()
  }
})

onHide(stopTimer)
onUnload(stopTimer)
</script>

<style lang="scss" scoped>
.charging-page {
  min-height: 100vh;
  background: #f5f7f6;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.charge-head {
  padding: 0 32rpx 34rpx;
  background: linear-gradient(165deg, #052a20 0%, #073d2d 48%, #0a5b42 100%);
  color: #ffffff;
  border-radius: 0 0 44rpx 44rpx;
  position: relative;
  overflow: hidden;
}

.charge-head::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 200rpx;
  width: 620rpx;
  height: 620rpx;
  transform: translateX(-50%);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(52, 211, 153, 0.16), transparent 62%);
}

.status-space {
  height: calc(var(--status-bar-height) + 12rpx);
}

.nav-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 70rpx;
  position: relative;
  z-index: 1;
}

.nav-back,
.nav-space {
  width: 60rpx;
  height: 60rpx;
}

.nav-back {
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-arrow {
  width: 18rpx;
  height: 18rpx;
  border-left: 4rpx solid #ffffff;
  border-bottom: 4rpx solid #ffffff;
  transform: rotate(45deg);
  margin-left: 6rpx;
}

.nav-title {
  font-size: 28rpx;
  font-weight: 600;
}

.loading {
  padding: 160rpx 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.6);
  font-size: 24rpx;
}

.state {
  margin-top: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #a7f3d0;
  position: relative;
  z-index: 1;
}

.state-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  margin-right: 12rpx;
}

.state-dot.on {
  background: #34d399;
  box-shadow: 0 0 0 8rpx rgba(52, 211, 153, 0.18);
}

.state-hint {
  margin-left: 12rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.55);
}

.ring-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24rpx;
  position: relative;
  z-index: 1;
}

.ring {
  width: 360rpx;
  height: 360rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 36rpx 80rpx -36rpx rgba(0, 0, 0, 0.55);
}

.ring::before {
  content: '';
  position: absolute;
  top: 14rpx;
  left: 14rpx;
  right: 14rpx;
  bottom: 14rpx;
  border-radius: 50%;
  background: #072e23;
}

.ring-inner {
  position: relative;
  text-align: center;
}

.ring-cur {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.6);
}

.ring-fee {
  font-size: 84rpx;
  line-height: 1.1;
  letter-spacing: -4rpx;
}

.ring-label {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.55);
}

.ring-limit {
  display: inline-block;
  margin-top: 16rpx;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  border: 2rpx solid rgba(52, 211, 153, 0.22);
  background: rgba(52, 211, 153, 0.12);
  font-size: 18rpx;
  color: #6ee7b7;
}

.hero-stats {
  display: flex;
  align-items: center;
  margin-top: 18rpx;
  position: relative;
  z-index: 1;
}

.hero-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-value {
  font-size: 28rpx;
}

.hero-label {
  margin-top: 6rpx;
  font-size: 18rpx;
  color: rgba(255, 255, 255, 0.5);
}

.hero-divider {
  width: 2rpx;
  height: 32rpx;
  background: rgba(255, 255, 255, 0.1);
}

.body {
  padding: 24rpx 32rpx 0;
}

.progress-card {
  padding: 26rpx 28rpx;
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 22rpx;
  color: #475467;
}

.progress-head.second {
  margin-top: 26rpx;
}

.progress-value {
  font-size: 22rpx;
  font-weight: 600;
  color: #101828;
}

.progress-card .bar {
  margin-top: 14rpx;
}

.progress-hint {
  margin-top: 22rpx;
  font-size: 20rpx;
  color: #98a2b3;
}

.device-head {
  display: flex;
  align-items: center;
}

.device-name {
  font-size: 28rpx;
  font-weight: 600;
}

.device-sub {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #98a2b3;
}

.live-grid {
  display: flex;
  margin-top: 22rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid #f5f7f6;
}

.live-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.live-cell + .live-cell {
  border-left: 2rpx solid #f5f7f6;
}

.live-value {
  font-size: 30rpx;
}

.live-label {
  margin-top: 6rpx;
  font-size: 18rpx;
  color: #98a2b3;
}

.fee-card {
  padding: 24rpx 28rpx 18rpx;
}

.fee-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6rpx;
}

.fee-title {
  font-size: 26rpx;
  font-weight: 600;
}

.fee-row {
  display: flex;
  justify-content: space-between;
  padding: 14rpx 0;
  font-size: 24rpx;
  color: #475467;
}

.fee-row.total {
  margin-top: 4rpx;
  padding-top: 20rpx;
  border-top: 2rpx dashed #edf0ee;
  color: #101828;
  font-weight: 600;
}

.fee-val {
  font-weight: 600;
  color: #101828;
}

.stop-area {
  margin-top: 36rpx;
}

.stop-btn {
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 999rpx;
  background: #ffffff;
  border: 2rpx solid #f9d3d0;
  color: #d92d20;
  font-size: 28rpx;
  font-weight: 600;
  text-align: center;
}

.stop-btn::after {
  border: none;
}

.stop-btn[disabled] {
  color: #98a2b3;
  border-color: #edf0ee;
}

.stop-tip {
  margin-top: 16rpx;
  text-align: center;
  font-size: 18rpx;
  color: #98a2b3;
}
</style>
