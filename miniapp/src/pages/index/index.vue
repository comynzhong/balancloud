<template>
  <view class="index-page" :style="pageStyle">
    <view class="head" :style="headStyle">
      <view class="loc" @tap="refreshLocation">
        <view class="pin"></view>
        <text>{{ coords ? '当前位置' : '点击定位' }}</text>
        <view class="chev"></view>
      </view>
      <text class="hi" @tap="goMine">{{ greeting }}，{{ userStore.displayName }}</text>
    </view>

    <view v-if="runtime.maintenance.enabled" class="maintenance">
      <view class="maintenance-mark">!</view>
      <view class="flex-1">
        <view class="maintenance-title">平台维护中</view>
        <view class="maintenance-text">{{ runtime.maintenance.message || '暂停新充电业务，请稍后再试。' }}</view>
      </view>
    </view>

    <view class="search" @tap="goStations">
      <view class="search-icon"></view>
      <text class="search-text">搜索场站名称或地址</text>
    </view>

    <view v-if="active" class="live" @tap="goCharging">
      <view class="live-top">
        <view class="live-state"><view class="pulse"></view>{{ orderStatus(active.status) }} · {{ active.portNo }} 号端口</view>
        <view class="live-more">查看详情 <view class="chev white"></view></view>
      </view>
      <view class="live-station">{{ active.stationName }} · {{ active.deviceNo }}</view>
      <view class="live-main">
        <text class="cur">¥</text>
        <text class="amount num">{{ money(active.totalFee) }}</text>
        <text class="unit">元</text>
      </view>
      <view class="live-stats">
        <view class="live-stat">
          <text class="v num">{{ kwh(active.energyKwh) }}</text>
          <text class="k">已充 kWh</text>
        </view>
        <view class="live-stat">
          <text class="v num">{{ duration(active.durationSeconds) }}</text>
          <text class="k">已充时长</text>
        </view>
        <view class="live-stat">
          <text class="v num">{{ money(active.chargeLimitAmount) }}</text>
          <text class="k">金额上限</text>
        </view>
      </view>
      <view class="live-bar"><view class="live-bar-inner" :style="{ width: amountPercent + '%' }"></view></view>
      <view class="live-bar-label">
        <text>金额已完成 {{ amountPercent }}%</text>
        <text>达到上限自动停止</text>
      </view>
    </view>

    <view class="scan-hero" @tap="goScan">
      <view class="qr">
        <view class="qr-frame"><view class="qr-line"></view></view>
      </view>
      <view class="flex-1">
        <view class="scan-title">扫码充电</view>
        <view class="scan-sub">扫描桩上二维码，或手动输入设备编号</view>
      </view>
      <view class="scan-go"><view class="chev white"></view></view>
    </view>

    <view class="section-title">
      <text>附近场站</text>
      <text class="more" @tap="goStations">查看全部 ›</text>
    </view>

    <view v-if="loading" class="card muted small">正在加载场站…</view>
    <view v-else-if="nearby.length === 0" class="card empty">
      <view class="empty-mark">桩</view>
      <view>附近还没有可用的充电场站</view>
    </view>

    <view
      v-for="station in nearby"
      :key="station.id"
      class="card station-card"
      @tap="goStation(station.id)"
    >
      <view class="thumb">{{ station.name ? station.name.slice(0, 1) : '充' }}</view>
      <view class="st-main">
        <view class="st-top">
          <text class="st-name ellipsis">{{ station.name }}</text>
          <text :class="['chip', station.status === 'ACTIVE' ? 'chip-green' : 'chip-gray']">
            {{ station.status === 'ACTIVE' ? '营业中' : '暂停营业' }}
          </text>
        </view>
        <view class="st-addr ellipsis">{{ station.address || '暂无地址' }}</view>
        <view class="st-meta">
          <text class="st-idle"><text class="idle-num num">{{ station.idlePortCount }}</text>/{{ station.portCount }} 空闲</text>
          <text v-if="station.distanceText" class="st-dist">{{ station.distanceText }}</text>
          <text v-else class="st-dist gray">暂无距离</text>
        </view>
      </view>
      <view class="nav-btn" @tap.stop="navigate(station)">
        <view class="nav-mark"></view>
      </view>
    </view>

  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { useOrderStore } from '@/stores/order'
import { getLocation, sortByDistance, formatDistance } from '@/utils/geo'
import { money, kwh, duration, orderStatus } from '@/utils/format'
import { runtimeConfig as runtime } from '@/utils/runtime'

const userStore = useUserStore()
const orderStore = useOrderStore()

const stations = ref([])
const coords = ref(null)
const loading = ref(true)
const pageStyle = ref(null)
const headStyle = ref(null)

// 微信胶囊按钮占住状态栏下方的导航条区域，自定义导航必须按它的实测位置排布，
// 否则定位行与搜索框会被胶囊压住（H5 无胶囊，保持 CSS 默认）。
function initNav() {
  // #ifdef MP-WEIXIN
  const rect = uni.getMenuButtonBoundingClientRect()
  const sys = uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()
  if (!rect || !rect.height) return
  pageStyle.value = { paddingTop: `${rect.top}px` }
  headStyle.value = {
    height: `${rect.height}px`,
    paddingRight: `${Math.max(0, sys.windowWidth - rect.left + 8)}px`
  }
  // #endif
}

initNav()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const nearby = computed(() => {
  const list = sortByDistance(stations.value, coords.value).slice(0, 3)
  return list.map((item) => ({ ...item, distanceText: formatDistance(item.distance) }))
})

const active = computed(() => orderStore.active)

const amountPercent = computed(() => {
  if (!active.value) return 0
  const limit = Number(active.value.chargeLimitAmount || 0)
  if (!limit) return 0
  return Math.min(100, Math.round((Number(active.value.totalFee || 0) / limit) * 100))
})

async function load() {
  loading.value = true
  try {
    await userStore.ensureLogin()
    const [list] = await Promise.all([
      request({ url: '/api/app/stations' }),
      orderStore.refreshActive()
    ])
    stations.value = list || []
    if (!coords.value) {
      getLocation().then((loc) => {
        coords.value = loc
      })
    }
  } catch (e) {
    if (e.code === 401) return
    showError(e)
  } finally {
    loading.value = false
  }
}

async function refreshLocation() {
  const loc = await getLocation()
  coords.value = loc
  if (!loc) {
    uni.showToast({ title: '未获取定位，请检查定位权限', icon: 'none' })
  }
}

onShow(load)

function goScan() {
  uni.navigateTo({ url: '/pages/scan/scan' })
}

function goStations() {
  uni.switchTab({ url: '/pages/station/list' })
}

function goStation(id) {
  uni.navigateTo({ url: `/pages/station/detail?id=${id}` })
}

function navigate(station) {
  if (station.latitude === null || station.latitude === undefined) {
    uni.showToast({ title: '场站暂未配置坐标', icon: 'none' })
    return
  }
  uni.openLocation({
    latitude: Number(station.latitude),
    longitude: Number(station.longitude),
    name: station.name,
    address: station.address || ''
  })
}

function goCharging() {
  if (!active.value) return
  uni.navigateTo({ url: `/pages/charge/charging?orderId=${active.value.id}` })
}

function goMine() {
  uni.switchTab({ url: '/pages/mine/index' })
}
</script>

<style lang="scss" scoped>
.index-page {
  min-height: 100vh;
  background: #f5f7f6;
  padding: calc(var(--status-bar-height) + 16rpx) 32rpx calc(140rpx + env(safe-area-inset-bottom));
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4rpx;
}

.loc {
  display: flex;
  align-items: center;
  font-size: 30rpx;
  font-weight: 600;
}

.pin {
  width: 26rpx;
  height: 26rpx;
  border: 4rpx solid #00a870;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  margin-right: 14rpx;
}

.hi {
  font-size: 22rpx;
  color: #98a2b3;
  max-width: 300rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.maintenance {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  border-radius: 24rpx;
  background: #fff6e8;
  border: 2rpx solid #fde8c4;
}

.maintenance-mark {
  flex: none;
  width: 34rpx;
  height: 34rpx;
  margin-top: 2rpx;
  border-radius: 50%;
  background: #f79009;
  color: #ffffff;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 34rpx;
  text-align: center;
}

.maintenance-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #b54708;
}

.maintenance-text {
  margin-top: 4rpx;
  font-size: 22rpx;
  line-height: 1.55;
  color: #b56a19;
}

.search {
  margin-top: 24rpx;
  height: 80rpx;
  padding: 0 28rpx;
  border-radius: 999rpx;
  background: #ffffff;
  border: 2rpx solid #edf0ee;
  display: flex;
  align-items: center;
}

.search-icon {
  width: 24rpx;
  height: 24rpx;
  border: 4rpx solid #98a2b3;
  border-radius: 50%;
  margin-right: 18rpx;
  position: relative;
}

.search-icon::after {
  content: '';
  position: absolute;
  width: 10rpx;
  height: 4rpx;
  background: #98a2b3;
  right: -9rpx;
  bottom: -4rpx;
  transform: rotate(45deg);
}

.search-text {
  font-size: 26rpx;
  color: #98a2b3;
}

.live {
  margin-top: 24rpx;
  padding: 26rpx 30rpx;
  border-radius: 36rpx;
  background: linear-gradient(135deg, #0c3a2c 0%, #0a5c44 60%, #0a7b5a 100%);
  color: #ffffff;
  position: relative;
  overflow: hidden;
  box-shadow: 0 24rpx 48rpx -28rpx rgba(9, 70, 52, 0.65);
}

.live::after {
  content: '';
  position: absolute;
  right: -80rpx;
  top: -100rpx;
  width: 320rpx;
  height: 320rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(52, 211, 153, 0.32), transparent 68%);
}

.live-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 22rpx;
  position: relative;
  z-index: 1;
}

.live-state {
  display: flex;
  align-items: center;
  font-weight: 600;
  color: #a7f3d0;
}

.pulse {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #34d399;
  margin-right: 12rpx;
  box-shadow: 0 0 0 8rpx rgba(52, 211, 153, 0.2);
}

.live-more {
  display: flex;
  align-items: center;
  color: rgba(255, 255, 255, 0.7);
}

.chev {
  width: 12rpx;
  height: 12rpx;
  border-top: 3rpx solid #98a2b3;
  border-right: 3rpx solid #98a2b3;
  transform: rotate(45deg);
  margin-left: 10rpx;
}

.chev.white {
  border-color: #ffffff;
}

.live-station {
  margin-top: 16rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.78);
  position: relative;
  z-index: 1;
}

.live-main {
  display: flex;
  align-items: baseline;
  margin-top: 4rpx;
  position: relative;
  z-index: 1;
}

.live-main .cur {
  font-size: 24rpx;
  opacity: 0.7;
  margin-right: 6rpx;
}

.live-main .amount {
  font-size: 68rpx;
  letter-spacing: -2rpx;
  line-height: 1.1;
}

.live-main .unit {
  font-size: 20rpx;
  opacity: 0.6;
  margin-left: 8rpx;
}

.live-stats {
  display: flex;
  margin-top: 20rpx;
  position: relative;
  z-index: 1;
}

.live-stat {
  flex: 1;
}

.live-stat .v {
  font-size: 28rpx;
}

.live-stat .k {
  display: block;
  font-size: 18rpx;
  opacity: 0.6;
  margin-top: 4rpx;
}

.live-bar {
  margin-top: 24rpx;
  height: 6rpx;
  border-radius: 6rpx;
  background: rgba(255, 255, 255, 0.18);
  overflow: hidden;
  position: relative;
  z-index: 1;
}

.live-bar-inner {
  height: 100%;
  border-radius: 6rpx;
  background: linear-gradient(90deg, #6ee7b7, #34d399);
  transition: width 0.4s ease;
}

.live-bar-label {
  margin-top: 12rpx;
  display: flex;
  justify-content: space-between;
  font-size: 18rpx;
  color: rgba(255, 255, 255, 0.55);
  position: relative;
  z-index: 1;
}

.scan-hero {
  margin-top: 24rpx;
  padding: 36rpx 32rpx;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  background: linear-gradient(120deg, #00b87c 0%, #009a66 100%);
  color: #ffffff;
  box-shadow: 0 24rpx 44rpx -26rpx rgba(0, 168, 112, 0.8);
  position: relative;
  overflow: hidden;
}

.qr {
  width: 96rpx;
  height: 96rpx;
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.16);
  box-shadow: inset 0 0 0 2rpx rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 26rpx;
}

.qr-frame {
  width: 48rpx;
  height: 48rpx;
  border: 4rpx solid #ffffff;
  border-radius: 10rpx;
  position: relative;
}

.qr-line {
  position: absolute;
  left: 6rpx;
  right: 6rpx;
  top: 50%;
  height: 4rpx;
  border-radius: 4rpx;
  background: #fde68a;
  margin-top: -2rpx;
}

.scan-title {
  font-size: 34rpx;
  font-weight: 700;
}

.scan-sub {
  margin-top: 8rpx;
  font-size: 22rpx;
  opacity: 0.82;
}

.scan-go {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.station-card {
  padding: 24rpx;
  display: flex;
  align-items: center;
}

.thumb {
  width: 124rpx;
  height: 124rpx;
  flex: 0 0 124rpx;
  border-radius: 28rpx;
  margin-right: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 46rpx;
  font-weight: 800;
  background:
    radial-gradient(circle at 70% 20%, rgba(255, 255, 255, 0.5), transparent 45%),
    linear-gradient(145deg, #19c08a, #007a52);
  box-shadow: inset 0 0 0 2rpx rgba(255, 255, 255, 0.18);
}

.st-main {
  flex: 1;
  min-width: 0;
}

.st-top {
  display: flex;
  align-items: center;
}

.st-name {
  font-size: 30rpx;
  font-weight: 600;
  margin-right: 12rpx;
}

.st-addr {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #98a2b3;
}

.st-meta {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
}

.st-idle {
  font-size: 22rpx;
  color: #475467;
  margin-right: 16rpx;
}

.idle-num {
  font-size: 28rpx;
  color: #00a870;
}

.st-dist {
  font-size: 20rpx;
  color: #00895c;
  background: #e6f7f1;
  border-radius: 999rpx;
  padding: 4rpx 14rpx;
}

.st-dist.gray {
  color: #98a2b3;
  background: #f2f4f7;
}

.nav-btn {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  border: 2rpx solid #edf0ee;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 16rpx;
}

.nav-mark {
  width: 22rpx;
  height: 22rpx;
  border-top: 4rpx solid #00a870;
  border-right: 4rpx solid #00a870;
  transform: rotate(45deg);
  margin-top: -4rpx;
}

.footer-note {
  padding: 48rpx 0 12rpx;
  text-align: center;
  font-size: 20rpx;
  color: #c6cdd5;
}
</style>
