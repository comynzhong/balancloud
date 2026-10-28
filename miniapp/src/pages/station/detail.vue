<template>
  <view class="detail-page" v-if="station">
    <view class="hero">
      <view class="status-space" :style="nav && nav.statusBar"></view>
      <view class="nav-row" :style="nav && nav.navBar">
        <view class="nav-back" @tap="goBack"><view class="back-arrow"></view></view>
        <text class="nav-title">场站详情</text>
        <view class="nav-space"></view>
      </view>
      <view class="hero-name">{{ station.name }}</view>
      <view class="hero-addr">
        <text class="ellipsis">{{ station.address || '暂无地址' }}</text>
        <text v-if="distanceText" class="dist-chip">{{ distanceText }}</text>
      </view>
      <view class="hero-actions">
        <view class="hero-action" @tap="openMap">导航前往</view>
        <view class="hero-action" @tap="callManager">联系场站</view>
      </view>
    </view>

    <view class="body">
      <view class="section-title">
        <text>当前电价</text>
        <text class="more">{{ station.rate && station.rate.strategyName }}</text>
      </view>
      <view class="card rate-card">
        <template v-if="station.rate && station.rate.configured">
          <view class="rate-top">
            <view class="rate-left">
              <view class="badge">{{ station.rate.current ? station.rate.current.periodLabel : '—' }}</view>
              <view>
                <view class="rate-label">当前时段 {{ rangeText(station.rate.current) }}</view>
                <view class="rate-value num">¥{{ currentEnergyPrice }}<text class="rate-unit">元/kWh 起</text></view>
              </view>
            </view>
            <text class="chip chip-green">{{ billingText }}</text>
          </view>

          <view class="rate-lines">
            <view class="rate-line">
              <text class="rate-num num">{{ price(current.timePrice) }}</text>
              <text class="rate-desc">时长费 元/小时</text>
            </view>
            <view class="rate-line">
              <text class="rate-num num">{{ price(current.energyPrice) }}</text>
              <text class="rate-desc">电费 元/kWh</text>
            </view>
            <view class="rate-line">
              <text class="rate-num num">{{ price(current.servicePrice) }}</text>
              <text class="rate-desc">服务费 {{ serviceUnitText(current.serviceUnit) }}</text>
            </view>
          </view>

          <view class="rate-toggle" @tap="showPeriods = !showPeriods">
            <text>{{ showPeriods ? '收起全天分时电价' : '查看全天分时电价' }}</text>
            <view :class="['chev', showPeriods ? 'up' : '']"></view>
          </view>

          <view v-if="showPeriods" class="period-table">
            <view class="period-head">
              <text class="p-time">时段</text>
              <text class="p-type">类型</text>
              <text class="p-price">时长/电/服务</text>
            </view>
            <view v-for="p in station.rate.periods" :key="p.periodType + p.startTime" class="period-row">
              <text class="p-time num">{{ p.startTime }}-{{ p.endTime }}</text>
              <text class="p-type">{{ p.periodLabel }}</text>
              <text class="p-price num">{{ price(p.timePrice) }}/{{ price(p.energyPrice) }}/{{ price(p.servicePrice) }}</text>
            </view>
          </view>
        </template>
        <view v-else class="muted small">该场站暂未配置费率，暂时无法启动充电。</view>
      </view>

      <view class="section-title">
        <view class="legend-title">
          <text>端口看板</text>
          <view class="legend">
            <view class="legend-item"><view class="legend-dot idle"></view>空闲</view>
            <view class="legend-item"><view class="legend-dot charging"></view>充电中</view>
            <view class="legend-item"><view class="legend-dot fault"></view>故障</view>
          </view>
        </view>
      </view>

      <view v-for="device in station.devices" :key="device.id" class="card device-card">
        <view class="dev-head">
          <view class="flex-1">
            <view class="device-name ellipsis">{{ device.name }}</view>
            <view class="device-no">{{ device.deviceNo }} · {{ device.portCount }} 路</view>
          </view>
          <view class="dev-meta">
            <view :class="['chip', device.onlineStatus === 'ONLINE' ? 'chip-green' : 'chip-gray']">
              <view v-if="device.onlineStatus === 'ONLINE'" class="online-dot"></view>
              <text>{{ device.onlineStatus === 'ONLINE' ? '在线' : '离线' }}</text>
            </view>
            <text class="chip chip-gray">空闲 {{ device.idlePorts }}/{{ device.portCount }}</text>
            <text v-if="runtime.features.reservation" class="chip chip-green reserve-chip" @tap="goReserve(device)">预约</text>
          </view>
        </view>

        <view class="ports">
          <view
            v-for="port in device.ports"
            :key="port.portNo"
            :class="['port', statusClass(port.status)]"
            @tap="tapPort(device, port)"
          >
            <text class="port-no num">{{ port.portNo }}</text>
            <text class="port-status">{{ statusText(port.status) }}</text>
          </view>
        </view>
      </view>

      <view class="bottom-hint">点击空闲端口即可开始充电</view>
    </view>

    <view class="sticky">
      <view class="sticky-info">
        <view class="sticky-price num">¥{{ currentEnergyPrice }}<text class="sticky-unit">/kWh 起</text></view>
        <view class="sticky-hint">费用以平台结算为准</view>
      </view>
      <view class="sticky-btn">选择端口</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { getLocation, distanceMeters, formatDistance } from '@/utils/geo'
import { money, periodLabel } from '@/utils/format'
import { capsuleNav } from '@/utils/nav'
import { runtimeConfig as runtime } from '@/utils/runtime'

const nav = capsuleNav()
const userStore = useUserStore()
const station = ref(null)
const distanceText = ref('')
const showPeriods = ref(false)

const current = computed(() => (station.value && station.value.rate && station.value.rate.current) || {})
const currentEnergyPrice = computed(() => {
  const c = current.value
  if (!c.energyPrice) return '0.00'
  if (c.serviceUnit === 'KWH') return money(Number(c.energyPrice) + Number(c.servicePrice || 0))
  return money(c.energyPrice)
})

const billingText = computed(() => {
  const mode = station.value && station.value.rate && station.value.rate.billingMode
  if (mode === 'ENERGY') return '按电量计费'
  if (mode === 'TIME') return '按时长计费'
  return '时长 + 电量 + 服务费'
})

function price(value) {
  return Number(value || 0).toFixed(2)
}

function serviceUnitText(unit) {
  return unit === 'HOUR' ? '元/小时' : '元/kWh'
}

function rangeText(period) {
  if (!period) return '--'
  return `${period.startTime}-${period.endTime}`
}

const STATUS_TEXT = { IDLE: '空闲', CHARGING: '充电中', OCCUPIED: '占用中', FAULT: '故障', OFFLINE: '离线' }

function statusText(status) {
  return STATUS_TEXT[status] || status
}

function statusClass(status) {
  if (status === 'IDLE') return 'idle'
  if (status === 'CHARGING') return 'busy'
  if (status === 'OCCUPIED') return 'busy'
  if (status === 'FAULT') return 'fault'
  return 'offline'
}

async function load(id) {
  try {
    await userStore.ensureLogin()
    const data = await request({ url: `/api/app/stations/${id}` })
    station.value = data
    if (data.rate) {
      data.rate.periods = (data.rate.periods || []).map((p) => ({
        ...p,
        periodLabel: p.periodLabel || periodLabel(p.periodType)
      }))
    }
    const loc = await getLocation()
    if (loc && data.latitude !== null && data.longitude !== null) {
      const meters = distanceMeters(loc.latitude, loc.longitude, data.latitude, data.longitude)
      distanceText.value = formatDistance(meters)
    }
  } catch (e) {
    if (e.code !== 401) showError(e)
  }
}

function openMap() {
  if (!station.value || station.value.latitude === null || station.value.latitude === undefined) {
    uni.showToast({ title: '场站暂未配置坐标', icon: 'none' })
    return
  }
  uni.openLocation({
    latitude: Number(station.value.latitude),
    longitude: Number(station.value.longitude),
    name: station.value.name,
    address: station.value.address || ''
  })
}

function callManager() {
  const phone = station.value && station.value.phone
  if (!phone) {
    uni.showToast({ title: '场站暂未留电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({ phoneNumber: String(phone) })
}

function tapPort(device, port) {
  if (port.status !== 'IDLE') {
    uni.showToast({ title: `该端口${statusText(port.status)}，请选择空闲端口`, icon: 'none' })
    return
  }
  if (!station.value.rate || !station.value.rate.configured) {
    uni.showToast({ title: '该场站暂未配置费率', icon: 'none' })
    return
  }
  if (station.value.status !== 'ACTIVE') {
    uni.showToast({ title: '场站暂停运营，暂不能启动', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/pages/charge/confirm?deviceNo=${device.deviceNo}&portNo=${port.portNo}`
  })
}

function goReserve(device) {
  uni.navigateTo({
    url: `/pkg-standard/pages/reservation/index?stationId=${station.value.id}&chargerId=${device.id}`
  })
}

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: '/pages/station/list' })
    }
  })
}

onLoad((options) => {
  load(options.id)
})
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f7f6;
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}

.hero {
  padding: 0 32rpx 36rpx;
  background: linear-gradient(155deg, #06382a 0%, #08553f 55%, #0a7a5a 100%);
  color: #ffffff;
  border-radius: 0 0 44rpx 44rpx;
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -120rpx;
  top: -140rpx;
  width: 400rpx;
  height: 400rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(52, 211, 153, 0.25), transparent 68%);
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
  background: rgba(255, 255, 255, 0.14);
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
  opacity: 0.92;
}

.hero-name {
  margin-top: 24rpx;
  font-size: 42rpx;
  font-weight: 700;
  letter-spacing: -0.4rpx;
  position: relative;
  z-index: 1;
}

.hero-addr {
  margin-top: 12rpx;
  display: flex;
  align-items: center;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.66);
  position: relative;
  z-index: 1;
}

.dist-chip {
  margin-left: 16rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  color: #a7f3d0;
  font-size: 20rpx;
  flex: 0 0 auto;
}

.hero-actions {
  display: flex;
  margin-top: 28rpx;
  position: relative;
  z-index: 1;
}

.hero-action {
  flex: 1;
  height: 68rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.13);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
}

.hero-action + .hero-action {
  margin-left: 16rpx;
}

.body {
  padding: 0 32rpx calc(170rpx + env(safe-area-inset-bottom));
}

.section-title {
  margin: 36rpx 4rpx 20rpx;
}

.rate-card {
  padding: 26rpx 28rpx;
}

.rate-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.rate-left {
  display: flex;
  align-items: center;
}

.badge {
  width: 68rpx;
  height: 68rpx;
  border-radius: 20rpx;
  background: #e6f7f1;
  color: #00895c;
  font-size: 26rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.rate-label {
  font-size: 22rpx;
  color: #98a2b3;
}

.rate-value {
  margin-top: 4rpx;
  font-size: 34rpx;
  color: #101828;
}

.rate-unit {
  margin-left: 6rpx;
  font-size: 20rpx;
  font-weight: 400;
  color: #98a2b3;
}

.rate-lines {
  display: flex;
  margin-top: 24rpx;
  padding-top: 22rpx;
  border-top: 2rpx dashed #edf0ee;
}

.rate-line {
  flex: 1;
  text-align: center;
}

.rate-line + .rate-line {
  border-left: 2rpx solid #edf0ee;
}

.rate-num {
  font-size: 26rpx;
  font-weight: 700;
  color: #101828;
}

.rate-desc {
  display: block;
  margin-top: 4rpx;
  font-size: 18rpx;
  color: #98a2b3;
}

.rate-toggle {
  margin-top: 20rpx;
  padding-top: 18rpx;
  border-top: 2rpx solid #f5f7f6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 22rpx;
  color: #98a2b3;
}

.chev {
  width: 12rpx;
  height: 12rpx;
  border-top: 3rpx solid #98a2b3;
  border-right: 3rpx solid #98a2b3;
  transform: rotate(135deg);
  margin-right: 6rpx;
}

.chev.up {
  transform: rotate(-45deg);
}

.period-table {
  margin-top: 12rpx;
}

.period-head,
.period-row {
  display: flex;
  align-items: center;
  font-size: 20rpx;
  padding: 12rpx 0;
}

.period-head {
  color: #98a2b3;
}

.period-row {
  color: #475467;
  border-top: 2rpx solid #f5f7f6;
}

.p-time {
  width: 36%;
}

.p-type {
  width: 16%;
}

.p-price {
  flex: 1;
  text-align: right;
}

.legend-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.legend {
  display: flex;
  align-items: center;
  font-size: 18rpx;
  font-weight: 400;
  color: #98a2b3;
}

.legend-item {
  display: flex;
  align-items: center;
  margin-left: 18rpx;
}

.legend-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  margin-right: 6rpx;
}

.legend-dot.idle {
  background: #00a870;
}

.legend-dot.charging {
  background: #f79009;
}

.legend-dot.fault {
  background: #f04438;
}

.device-card {
  padding: 24rpx 26rpx 26rpx;
}

.dev-head {
  display: flex;
  align-items: center;
}

.device-name {
  font-size: 28rpx;
  font-weight: 600;
}

.device-no {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #98a2b3;
}

.dev-meta {
  display: flex;
  align-items: center;
}

.dev-meta .chip + .chip {
  margin-left: 10rpx;
}

.reserve-chip {
  border: 2rpx solid rgba(0, 168, 112, 0.35);
}

.online-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #00a870;
}

.ports {
  display: flex;
  flex-wrap: wrap;
  margin-top: 20rpx;
}

.port {
  width: calc(25% - 12rpx);
  margin: 6rpx;
  height: 100rpx;
  border-radius: 18rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2rpx solid transparent;
}

.port-no {
  font-size: 30rpx;
  line-height: 1;
}

.port-status {
  margin-top: 6rpx;
  font-size: 18rpx;
}

.port.idle {
  background: #e6f7f1;
  border-color: #c6ebdd;
  color: #00895c;
}

.port.busy {
  background: #fff6e8;
  border-color: #fbe3c0;
  color: #b54708;
}

.port.fault {
  background: #fef0ef;
  border-color: #f9d3d0;
  color: #b42318;
}

.port.offline {
  background: #f5f7f6;
  border-color: #edf0ee;
  color: #98a2b3;
}

.bottom-hint {
  text-align: center;
  padding: 36rpx 0 12rpx;
  font-size: 20rpx;
  color: #c6cdd5;
}

.sticky {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 32rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 2rpx solid #edf0ee;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sticky-price {
  font-size: 32rpx;
  color: #101828;
}

.sticky-unit {
  font-size: 20rpx;
  font-weight: 400;
  color: #98a2b3;
}

.sticky-hint {
  margin-top: 4rpx;
  font-size: 18rpx;
  color: #98a2b3;
}

.sticky-btn {
  height: 76rpx;
  padding: 0 44rpx;
  border-radius: 999rpx;
  background: #e6f7f1;
  color: #00895c;
  font-size: 26rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
}
</style>
