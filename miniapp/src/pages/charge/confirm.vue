<template>
  <view class="bc-page confirm-page" v-if="device">
    <view v-if="active" class="active-banner" @tap="goActive">
      <view class="pulse"></view>
      <view class="flex-1">
        <view class="banner-title">您有进行中的充电订单</view>
        <view class="banner-sub">{{ active.stationName }} · {{ active.deviceNo }} {{ active.portNo }} 号端口</view>
      </view>
      <text class="banner-link">去查看 ›</text>
    </view>

    <view class="card summary">
      <view class="summary-top">
        <view>
          <view class="summary-label">充电场站</view>
          <view class="summary-name">{{ device.stationName }}</view>
          <view class="summary-sub">{{ device.chargerName }} · {{ device.deviceNo }}</view>
        </view>
        <text :class="['chip', device.onlineStatus === 'ONLINE' ? 'chip-green' : 'chip-gray']">
          {{ device.onlineStatus === 'ONLINE' ? '设备在线' : '设备离线' }}
        </text>
      </view>
      <view class="summary-addr ellipsis">{{ device.stationAddress || '暂无地址' }}</view>
    </view>

    <view v-if="isPlugAndCharge" class="card plug-card">
      <view class="plug-title">插枪即充</view>
      <view class="plug-desc">该设备已开启插枪即充：把充电枪插入车辆后自动开始计费，无需扫码启动。</view>
    </view>

    <view class="section-title">
      <text>选择端口</text>
      <text class="more">{{ idleCount }} 个空闲</text>
    </view>
    <view class="card port-card">
      <view class="ports">
        <view
          v-for="port in device.ports"
          :key="port.portNo"
          :class="['port', port.status === 'IDLE' ? 'idle' : 'busy', selectedPort === port.portNo ? 'selected' : '']"
          @tap="selectPort(port)"
        >
          <text class="port-no num">{{ port.portNo }}</text>
          <text class="port-status">{{ port.status === 'IDLE' ? '空闲' : port.status === 'CHARGING' ? '充电中' : port.status === 'OCCUPIED' ? '占用中' : port.status === 'FAULT' ? '故障' : '离线' }}</text>
        </view>
      </view>
      <view v-if="!selectedPort" class="port-tip">请选择一个空闲端口</view>
    </view>

    <view class="section-title">
      <text>本次车辆</text>
      <text class="more" @tap="goVehicles">管理车辆</text>
    </view>
    <picker mode="selector" :range="vehicleLabels" :value="vehicleIndex" @change="onVehicleChange">
      <view class="card vehicle-card">
        <view class="vehicle-mark">车</view>
        <view class="flex-1">
          <view class="vehicle-plate num">{{ selectedVehicle ? selectedVehicle.plateNo : '不绑定车辆' }}</view>
          <view class="vehicle-sub">{{ selectedVehicle ? vehicleDesc : '可选填，订单将记录车牌' }}</view>
        </view>
        <view class="chev"></view>
      </view>
    </picker>

    <view class="section-title">
      <text>充电上限</text>
      <text class="more">默认 {{ maxDuration }} 分钟 / ¥{{ maxAmount }}，仅可调低</text>
    </view>
    <view class="card limit-card">
      <view class="limit-head">
        <text class="limit-label">充电时长上限</text>
        <view class="limit-value"><text class="num">{{ durationMinutes }}</text><text class="unit">分钟</text></view>
      </view>
      <view class="limit-chips">
        <view
          v-for="preset in durationPresets"
          :key="preset"
          :class="['chip-btn', durationMinutes === preset ? 'on' : '']"
          @tap="durationMinutes = preset"
        >{{ preset / 60 }} 小时</view>
      </view>
      <view class="stepper">
        <view class="step" @tap="adjustDuration(-10)">−</view>
        <view class="step-value">每次调整 10 分钟</view>
        <view class="step" @tap="adjustDuration(10)">+</view>
      </view>

      <view class="limit-head second">
        <text class="limit-label">充电金额上限</text>
        <view class="limit-value"><text class="num">{{ money(amountLimit) }}</text><text class="unit">元</text></view>
      </view>
      <view class="limit-chips">
        <view
          v-for="preset in amountPresets"
          :key="preset"
          :class="['chip-btn', Number(amountLimit) === preset ? 'on' : '']"
          @tap="amountLimit = preset"
        >¥{{ preset }}</view>
      </view>
      <view class="stepper">
        <view class="step" @tap="adjustAmount(-1)">−</view>
        <view class="step-value">每次调整 1 元</view>
        <view class="step" @tap="adjustAmount(1)">+</view>
      </view>
    </view>

    <view class="section-title"><text>计费说明</text></view>
    <view class="card rate-card">
      <template v-if="rate && rate.configured">
        <view class="rate-head">
          <view class="rate-left">
            <view class="badge">{{ current.periodLabel || '—' }}</view>
            <view>
              <view class="rate-current">当前时段 {{ current.startTime }}-{{ current.endTime }}</view>
              <view class="rate-sub">{{ billingText }}</view>
            </view>
          </view>
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
            <text class="rate-desc">服务费 {{ current.serviceUnit === 'HOUR' ? '元/小时' : '元/kWh' }}</text>
          </view>
        </view>
        <view class="estimate">
          <view class="estimate-icon">i</view>
          <text>按当前电价估算，上限内约可充 <text class="estimate-num">{{ estimatedKwh }}</text> kWh，仅供参考。</text>
        </view>
      </template>
      <view v-else class="muted small">该设备暂未配置费率，无法启动充电。</view>
    </view>

    <view class="agree">
      <view class="check"><view class="check-mark"></view></view>
      <text>启动即表示同意《用户充电协议》，费用以平台结算为准</text>
    </view>

    <view class="bottom-space"></view>

    <view class="sticky">
      <view class="sticky-info">
        <view class="sticky-price num">¥{{ currentEnergyPrice }}<text class="sticky-unit">/kWh 起</text></view>
        <view class="sticky-hint">上限 ¥{{ money(amountLimit) }}</view>
      </view>
      <button
        :class="['btn-primary', 'start-btn', canStart ? '' : 'disabled']"
        :disabled="!canStart || submitting"
        @tap="start"
      >
        {{ submitting ? '正在启动…' : isPlugAndCharge ? '请直接插枪' : active ? '有进行中订单' : '启动充电' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { useOrderStore } from '@/stores/order'
import { DEFAULT_MAX_AMOUNT, DEFAULT_MAX_DURATION } from '@/utils/config'
import { runtimeConfig as runtime } from '@/utils/runtime'
import { money } from '@/utils/format'

const userStore = useUserStore()
const orderStore = useOrderStore()

const device = ref(null)
const rate = ref(null)
const selectedPort = ref(null)
const durationMinutes = ref(runtime.miniapp.defaultMaxDurationMinutes || DEFAULT_MAX_DURATION)
const amountLimit = ref(Number(runtime.miniapp.defaultChargeLimitAmount) || DEFAULT_MAX_AMOUNT)
const submitting = ref(false)
const vehicles = ref([])
const selectedVehicleId = ref(null)
let vehicleTouched = false
const active = computed(() => orderStore.active)

const maxDuration = computed(() => runtime.miniapp.defaultMaxDurationMinutes || DEFAULT_MAX_DURATION)
const maxAmount = computed(() => Number(runtime.miniapp.defaultChargeLimitAmount) || DEFAULT_MAX_AMOUNT)
const durationPresets = computed(() => [60, 120, 240, 480].filter((v) => v <= maxDuration.value))
const amountPresets = computed(() => [5, 10, 20, 50, 100].filter((v) => v <= maxAmount.value))

watch(maxDuration, (value) => {
  if (durationMinutes.value > value) durationMinutes.value = value
})
watch(maxAmount, (value) => {
  if (Number(amountLimit.value) > value) amountLimit.value = value
})

const current = computed(() => (rate.value && rate.value.current) || {})

const currentEnergyPrice = computed(() => {
  const c = current.value
  if (!c.energyPrice) return '0.00'
  if (c.serviceUnit === 'KWH') return money(Number(c.energyPrice) + Number(c.servicePrice || 0))
  return money(c.energyPrice)
})

const idleCount = computed(() => {
  if (!device.value) return 0
  return device.value.ports.filter((p) => p.status === 'IDLE').length
})

const billingText = computed(() => {
  const mode = rate.value && rate.value.billingMode
  if (mode === 'ENERGY') return '按电量计费'
  if (mode === 'TIME') return '按时长计费'
  return '时长 + 电量 + 服务费'
})

const estimatedKwh = computed(() => {
  const unitPrice = Number(current.value.energyPrice || 0)
  const service = current.value.serviceUnit === 'KWH' ? Number(current.value.servicePrice || 0) : 0
  const total = unitPrice + service
  if (total <= 0) return '--'
  return (Number(amountLimit.value) / total).toFixed(1)
})

const isPlugAndCharge = computed(() => device.value?.startMethod === 'PLUG_AND_CHARGE')

const canStart = computed(
  () =>
    device.value &&
    !isPlugAndCharge.value &&
    rate.value &&
    rate.value.configured &&
    selectedPort.value &&
    !active.value
)

const selectedVehicle = computed(() => vehicles.value.find((item) => item.id === selectedVehicleId.value) || null)

const vehicleLabels = computed(() => [
  '不绑定车辆',
  ...vehicles.value.map((item) => `${item.plateNo}${item.isDefault === '1' ? '（默认）' : ''}`)
])

const vehicleIndex = computed(() => {
  const index = vehicles.value.findIndex((item) => item.id === selectedVehicleId.value)
  return index >= 0 ? index + 1 : 0
})

const vehicleDesc = computed(() => {
  if (!selectedVehicle.value) return ''
  const parts = [selectedVehicle.value.brand, selectedVehicle.value.model, selectedVehicle.value.color]
    .filter(Boolean)
    .join(' · ')
  return parts || '已绑定车辆'
})

function price(value) {
  return Number(value || 0).toFixed(2)
}

function selectPort(port) {
  if (port.status !== 'IDLE') {
    uni.showToast({ title: '该端口当前不可用', icon: 'none' })
    return
  }
  selectedPort.value = port.portNo
}

async function refreshVehicles() {
  try {
    await userStore.ensureLogin()
    vehicles.value = (await request({ url: '/api/app/vehicles' })) || []
    if (selectedVehicleId.value && !vehicles.value.some((item) => item.id === selectedVehicleId.value)) {
      selectedVehicleId.value = null
      vehicleTouched = false
    }
    if (!selectedVehicleId.value && !vehicleTouched) {
      const fallback = vehicles.value.find((item) => item.isDefault === '1')
      if (fallback) selectedVehicleId.value = fallback.id
    }
  } catch (e) {
    if (e.code !== 401) showError(e)
  }
}

function onVehicleChange(event) {
  const index = Number(event.detail.value)
  selectedVehicleId.value = index > 0 && vehicles.value[index - 1] ? vehicles.value[index - 1].id : null
  vehicleTouched = true
}

function goVehicles() {
  uni.navigateTo({ url: '/pkg-standard/pages/vehicle/list' })
}

function adjustDuration(delta) {
  const next = Number(durationMinutes.value) + delta
  durationMinutes.value = Math.min(maxDuration.value, Math.max(10, next))
}

function adjustAmount(delta) {
  const next = Number(amountLimit.value) + delta
  amountLimit.value = Math.min(maxAmount.value, Math.max(1, next))
}

async function load(deviceNo, portNo) {
  try {
    await userStore.ensureLogin()
    const data = await request({ url: `/api/app/chargers/by-no/${encodeURIComponent(deviceNo)}` })
    device.value = data
    rate.value = data.rate
    await refreshVehicles()
    orderStore.refreshActive().catch(() => {})

    if (portNo) {
      const target = data.ports.find((p) => Number(p.portNo) === Number(portNo))
      if (target && target.status === 'IDLE') {
        selectedPort.value = target.portNo
      } else if (target) {
        uni.showToast({ title: '该端口当前不可用，请选择其他端口', icon: 'none', duration: 2500 })
      }
    }
  } catch (e) {
    if (e.code !== 401) showError(e)
  }
}

async function goActive() {
  if (!active.value) return
  uni.navigateTo({ url: `/pages/charge/charging?orderId=${active.value.id}` })
}

async function start() {
  if (!canStart.value || submitting.value) return
  submitting.value = true
  try {
    const result = await request({
      url: `/api/app/chargers/${device.value.chargerId}/ports/${selectedPort.value}/start`,
      method: 'POST',
      data: {
        maxDurationMinutes: durationMinutes.value,
        chargeLimitAmount: amountLimit.value,
        vehicleId: selectedVehicleId.value
      }
    })
    uni.showToast({ title: '启动指令已下发', icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/charge/charging?orderId=${result.orderId}` })
    }, 600)
  } catch (e) {
    if (e.code === 409) {
      await orderStore.refreshActive().catch(() => {})
      if (orderStore.active) {
        uni.showModal({
          title: '已有进行中订单',
          content: '同一时间只能有一笔充电订单，请先完成当前订单。',
          confirmText: '去查看',
          success: (res) => {
            if (res.confirm) {
              uni.redirectTo({ url: `/pages/charge/charging?orderId=${orderStore.active.id}` })
            }
          }
        })
      } else {
        showError(e)
      }
    } else {
      showError(e)
    }
  } finally {
    submitting.value = false
  }
}

onLoad((options) => {
  load(options.deviceNo, options.portNo)
})

onShow(() => {
  if (device.value) refreshVehicles()
})
</script>

<style lang="scss" scoped>
.confirm-page {
  padding-top: 24rpx;
  padding-bottom: calc(170rpx + env(safe-area-inset-bottom));
}

.active-banner {
  display: flex;
  align-items: center;
  padding: 22rpx 26rpx;
  border-radius: 28rpx;
  background: #e6f7f1;
  border: 2rpx solid #c6ebdd;
  margin-bottom: 20rpx;
}

.plug-card {
  padding: 24rpx 28rpx;
  margin-bottom: 20rpx;
  border: 2rpx solid #fde3b3;
  background: #fffaf0;
}

.plug-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #b45309;
}

.plug-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: #92610c;
}

.pulse {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #00a870;
  margin-right: 16rpx;
  box-shadow: 0 0 0 8rpx rgba(0, 168, 112, 0.12);
}

.banner-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #00895c;
}

.banner-sub {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #5c8a78;
}

.banner-link {
  font-size: 22rpx;
  color: #00895c;
}

.page-title {
  margin: 4rpx 4rpx 24rpx;
}

.summary {
  padding: 28rpx 30rpx;
}

.summary-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.summary-label {
  font-size: 22rpx;
  color: #98a2b3;
}

.summary-name {
  margin-top: 6rpx;
  font-size: 32rpx;
  font-weight: 700;
}

.summary-sub {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #475467;
}

.summary-addr {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 2rpx solid #f5f7f6;
  font-size: 22rpx;
  color: #98a2b3;
}

.port-card {
  padding: 20rpx 24rpx 24rpx;
}

.ports {
  display: flex;
  flex-wrap: wrap;
}

.port {
  width: calc(25% - 12rpx);
  margin: 6rpx;
  height: 96rpx;
  border-radius: 18rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #edf0ee;
  background: #f5f7f6;
  color: #98a2b3;
}

.port.idle {
  background: #e6f7f1;
  border-color: #c6ebdd;
  color: #00895c;
}

.port.selected {
  background: #00a870;
  border-color: #00a870;
  color: #ffffff;
  box-shadow: 0 12rpx 24rpx -12rpx rgba(0, 168, 112, 0.85);
}

.port-no {
  font-size: 30rpx;
  line-height: 1;
}

.port-status {
  margin-top: 6rpx;
  font-size: 18rpx;
}

.port-tip {
  margin-top: 16rpx;
  text-align: center;
  font-size: 20rpx;
  color: #98a2b3;
}

.vehicle-card {
  display: flex;
  align-items: center;
  padding: 24rpx 28rpx;
}

.vehicle-mark {
  width: 72rpx;
  height: 72rpx;
  flex: 0 0 72rpx;
  margin-right: 20rpx;
  border-radius: 20rpx;
  background: #e6f7f1;
  color: #00895c;
  font-size: 30rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.vehicle-plate {
  font-size: 30rpx;
  font-weight: 700;
  color: #101828;
  letter-spacing: 1rpx;
}

.vehicle-sub {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #98a2b3;
}

.chev {
  width: 14rpx;
  height: 14rpx;
  border-top: 3rpx solid #c6cdd5;
  border-right: 3rpx solid #c6cdd5;
  transform: rotate(45deg);
  flex: none;
}

.limit-card {
  padding: 26rpx 28rpx;
}

.limit-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.limit-head.second {
  margin-top: 32rpx;
  padding-top: 26rpx;
  border-top: 2rpx dashed #edf0ee;
}

.limit-label {
  font-size: 28rpx;
  font-weight: 600;
}

.limit-value {
  display: flex;
  align-items: baseline;
  color: #00895c;
}

.limit-value .num {
  font-size: 40rpx;
}

.unit {
  font-size: 20rpx;
  margin-left: 6rpx;
  color: #98a2b3;
}

.limit-chips {
  display: flex;
  margin-top: 20rpx;
}

.chip-btn {
  flex: 1;
  height: 66rpx;
  border-radius: 16rpx;
  background: #f7f9f8;
  border: 2rpx solid #edf0ee;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: #475467;
}

.chip-btn + .chip-btn {
  margin-left: 14rpx;
}

.chip-btn.on {
  background: #e6f7f1;
  border-color: #00a870;
  color: #00895c;
  font-weight: 600;
}

.stepper {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8rpx;
  border-radius: 18rpx;
  background: #f7f9f8;
}

.step {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: #ffffff;
  color: #00895c;
  font-size: 36rpx;
  line-height: 60rpx;
  text-align: center;
  box-shadow: 0 2rpx 6rpx rgba(16, 24, 40, 0.06);
}

.step-value {
  font-size: 20rpx;
  color: #98a2b3;
}

.rate-card {
  padding: 26rpx 28rpx;
}

.rate-head {
  display: flex;
  align-items: center;
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

.rate-current {
  font-size: 26rpx;
  font-weight: 600;
}

.rate-sub {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #98a2b3;
}

.rate-lines {
  display: flex;
  margin-top: 22rpx;
  padding-top: 20rpx;
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
}

.rate-desc {
  display: block;
  margin-top: 4rpx;
  font-size: 18rpx;
  color: #98a2b3;
}

.estimate {
  margin-top: 22rpx;
  padding: 18rpx 20rpx;
  border-radius: 18rpx;
  background: #e6f7f1;
  display: flex;
  align-items: flex-start;
  font-size: 20rpx;
  line-height: 1.6;
  color: #006b49;
}

.estimate-icon {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  border: 2rpx solid #00895c;
  color: #00895c;
  font-size: 18rpx;
  line-height: 26rpx;
  text-align: center;
  margin-right: 12rpx;
  flex: 0 0 28rpx;
}

.estimate-num {
  font-weight: 700;
  font-size: 24rpx;
}

.agree {
  margin: 26rpx 6rpx 0;
  display: flex;
  align-items: center;
  font-size: 20rpx;
  color: #98a2b3;
}

.check {
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  background: #00a870;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
  flex: 0 0 30rpx;
}

.check-mark {
  width: 12rpx;
  height: 6rpx;
  border-left: 3rpx solid #ffffff;
  border-bottom: 3rpx solid #ffffff;
  transform: rotate(-45deg);
  margin-top: -3rpx;
}

.bottom-space {
  height: 180rpx;
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
}

.sticky-info {
  flex: 1;
}

.sticky-price {
  font-size: 32rpx;
}

.sticky-unit {
  font-size: 18rpx;
  font-weight: 400;
  color: #98a2b3;
}

.sticky-hint {
  margin-top: 4rpx;
  font-size: 18rpx;
  color: #98a2b3;
}

.start-btn {
  flex: 0 0 300rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 28rpx;
}

.start-btn.disabled {
  background: #e7ebe9;
  color: #98a2b3;
  box-shadow: none;
}
</style>
