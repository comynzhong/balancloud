<template>
  <view class="detail-page" v-if="order">
    <view class="hero">
      <view class="status-space" :style="nav && nav.statusBar"></view>
      <view class="nav-row" :style="nav && nav.navBar">
        <view class="nav-back" @tap="goBack"><view class="back-arrow"></view></view>
        <text class="nav-title">订单详情</text>
        <view class="nav-space"></view>
      </view>

      <view class="hero-state">
        <text class="state-pill">{{ orderStatus(order.status) }}</text>
        <text class="state-hint">{{ statusHint }}</text>
      </view>
      <view class="hero-amount num"><text class="symbol">¥</text>{{ money(order.totalFee) }}</view>
      <view class="hero-sub ellipsis">{{ order.stationName }} · {{ order.deviceNo }} · {{ order.portNo }} 号端口</view>
    </view>

    <view class="body">
      <view class="card timeline-card">
        <view class="tl-row">
          <view class="tl-mark"><view class="tl-dot"></view><view class="tl-line"></view></view>
          <view class="tl-body">
            <text class="tl-title">充电开始</text>
            <text class="tl-time num">{{ datetime(order.startTime) }}</text>
          </view>
        </view>
        <view class="tl-row">
          <view class="tl-mark"><view class="tl-dot"></view><view class="tl-line"></view></view>
          <view class="tl-body">
            <text class="tl-title">{{ order.endTime ? '充电结束' : '充电进行中' }}</text>
            <text class="tl-time num">{{ order.endTime ? datetime(order.endTime) : '实时计费中' }}</text>
            <text v-if="order.stopReason || order.stopMessage" class="tl-note">{{ stopReason(order.stopReason, order.stopMessage) }}</text>
          </view>
        </view>
        <view class="tl-row last">
          <view class="tl-mark"><view :class="['tl-dot', order.paymentStatus === 'PAID' ? '' : 'gray']"></view></view>
          <view class="tl-body">
            <text class="tl-title">{{ payTitle }}</text>
            <text class="tl-time num">{{ payTime }}</text>
          </view>
        </view>

        <view class="tl-metrics">
          <view class="tl-metric">
            <text class="tl-value num">{{ kwh(order.energyKwh) }}</text>
            <text class="tl-label">充电电量 kWh</text>
          </view>
          <view class="tl-metric">
            <text class="tl-value num">{{ duration(order.durationSeconds) }}</text>
            <text class="tl-label">充电时长</text>
          </view>
          <view class="tl-metric">
            <text class="tl-value num">{{ avgPower }}</text>
            <text class="tl-label">平均功率 kW</text>
          </view>
        </view>
      </view>

      <view class="section-title"><text>费用明细</text></view>
      <view class="card fee-card">
        <view class="fee-row"><text>时长费</text><text class="fee-val num">{{ money(order.timeFee) }} 元</text></view>
        <view class="fee-row"><text>电费</text><text class="fee-val num">{{ money(order.energyFee) }} 元</text></view>
        <view class="fee-row"><text>服务费</text><text class="fee-val num">{{ money(order.serviceFee) }} 元</text></view>
        <view v-if="Number(order.occupancyFee || 0) > 0" class="fee-row">
          <text>占位费 · {{ Math.round(Number(order.occupancySeconds || 0) / 60) }} 分钟</text>
          <text class="fee-val num">{{ money(order.occupancyFee) }} 元</text>
        </view>
        <view class="fee-row total"><text>合计</text><text class="fee-val brand total-val num">{{ money(order.totalFee) }} 元</text></view>
      </view>

      <template v-if="order.details && order.details.length">
        <view class="section-title"><text>分时段计费</text></view>
        <view class="card table-card">
          <view class="t-head">
            <text class="t-type">时段</text>
            <text class="t-duration">时长</text>
            <text class="t-energy">电量</text>
            <text class="t-fee">费用</text>
          </view>
          <view v-for="(d, index) in order.details" :key="index" class="t-row">
            <view class="t-type"><text class="period-tag">{{ periodLabel(d.periodType) }}</text></view>
            <text class="t-duration num">{{ duration(d.durationSeconds) }}</text>
            <text class="t-energy num">{{ kwh(d.energyKwh) }}</text>
            <text class="t-fee num">{{ money(Number(d.timeFee) + Number(d.energyFee) + Number(d.serviceFee)) }}</text>
          </view>
          <view class="t-note">各时段单价：时长 / 电 / 服务 = {{ detailPrices }}</view>
        </view>
      </template>

      <template v-if="order.rateSnapshot && order.rateSnapshot.periods">
        <view class="section-title"><text>费率快照</text><text class="more">启动时冻结</text></view>
        <view class="card table-card">
          <view class="t-head">
            <text class="t-time">时段</text>
            <text class="t-type">类型</text>
            <text class="t-fee">时长/电/服务</text>
          </view>
          <view v-for="p in order.rateSnapshot.periods" :key="p.periodType + p.startTime" class="t-row">
            <text class="t-time num">{{ p.startTime }}-{{ p.endTime }}</text>
            <text class="t-type">{{ periodLabel(p.periodType) }}</text>
            <text class="t-fee num">{{ Number(p.timePrice).toFixed(2) }}/{{ Number(p.energyPrice).toFixed(2) }}/{{ Number(p.servicePrice).toFixed(2) }}</text>
          </view>
        </view>
      </template>

      <template v-if="order.refund">
        <view class="section-title"><text>退款信息</text></view>
        <view class="card info-card">
          <view class="info-row"><text class="info-label">退款金额</text><text class="info-value num">{{ money(order.refund.amount) }} 元</text></view>
          <view class="info-row"><text class="info-label">退款原因</text><text class="info-value">{{ order.refund.reason || '--' }}</text></view>
          <view class="info-row"><text class="info-label">退款单号</text><text class="info-value num">{{ order.refund.refundNo }}</text></view>
          <view class="info-row"><text class="info-label">退款时间</text><text class="info-value num">{{ datetime(order.refund.refundTime) }}</text></view>
        </view>
      </template>

      <view class="section-title"><text>订单信息</text></view>
      <view class="card info-card">
        <view class="info-row"><text class="info-label">场站</text><text class="info-value">{{ order.stationName }}</text></view>
        <view class="info-row"><text class="info-label">设备</text><text class="info-value">{{ order.deviceName }}（{{ order.deviceNo }}）</text></view>
        <view class="info-row"><text class="info-label">端口</text><text class="info-value">{{ order.portNo }} 号端口</text></view>
        <view v-if="order.vehiclePlateNo" class="info-row"><text class="info-label">车辆</text><text class="info-value">{{ order.vehiclePlateNo }}</text></view>
        <view v-if="order.startMethod === 'PLUG_AND_CHARGE'" class="info-row"><text class="info-label">启动方式</text><text class="info-value">插枪即充</text></view>
        <view v-if="order.vin" class="info-row"><text class="info-label">VIN</text><text class="info-value num">****{{ order.vin.slice(-6) }}</text></view>
        <view class="info-row"><text class="info-label">起止电表</text><text class="info-value num">{{ Number(order.startMeter).toFixed(4) }} → {{ order.endMeter !== null ? Number(order.endMeter).toFixed(4) : '--' }}</text></view>
        <view class="info-row"><text class="info-label">订单号</text><text class="info-value num copy" @tap="copyOrderNo">{{ order.orderNo }}</text></view>
      </view>

      <view class="bottom-space"></view>
    </view>

    <view class="sticky">
      <view class="sticky-info">
        <view class="sticky-price num">¥{{ money(order.totalFee) }}<text class="sticky-unit">{{ paymentStatus(order.paymentStatus) }}</text></view>
        <view class="sticky-hint">{{ paymentHint }}</view>
      </view>
      <button v-if="active" class="btn-ghost sticky-btn" @tap="goCharging">返回充电中</button>
      <button
        v-else-if="order.paymentStatus === 'UNPAID' && order.status !== 'FAILED' && runtime.paymentEnabled"
        class="btn-primary sticky-btn"
        :disabled="paying || confirming"
        @tap="pay"
      >{{ payButtonText }}</button>
      <button v-else class="btn-ghost sticky-btn" disabled>{{ paymentStatus(order.paymentStatus) }}</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { runtimeConfig as runtime } from '@/utils/runtime'
import { useUserStore } from '@/stores/user'
import {
  money,
  kwh,
  duration,
  datetime,
  orderStatus,
  isActive,
  stopReason,
  periodLabel,
  paymentStatus
} from '@/utils/format'
import { capsuleNav } from '@/utils/nav'

const nav = capsuleNav()
const userStore = useUserStore()
const orderId = ref(null)
const order = ref(null)
const paying = ref(false)
const confirming = ref(false)
let timer = null
let inFlight = false
let confirmTimer = null
let confirmDeadline = 0

const active = computed(() => order.value && isActive(order.value.status))

/** 真实通道（微信）与内置 mock 共用同一按钮，文案与提示按 payment.mode 切换。 */
const realPayment = computed(() => runtime.paymentEnabled && runtime.paymentMode !== 'mock')

const payButtonText = computed(() => {
  if (confirming.value) return '支付确认中…'
  if (paying.value) return '支付处理中…'
  return realPayment.value ? '微信支付' : '模拟支付'
})

const payTitle = computed(() => {
  if (!order.value) return ''
  if (order.value.paymentStatus === 'PAID') return '支付完成'
  if (order.value.paymentStatus === 'REFUNDED') return '已退款'
  return '待支付'
})

const payTime = computed(() => {
  if (!order.value) return ''
  if (order.value.paymentStatus === 'REFUNDED') return datetime(order.value.refund && order.value.refund.refundTime)
  if (order.value.paymentStatus === 'PAID') return datetime(order.value.paidAt)
  if (confirming.value) return '支付确认中，以微信通知为准'
  return realPayment.value ? '待微信支付' : '本期为模拟支付'
})

const paymentHint = computed(() => {
  if (!runtime.paymentEnabled) return '支付功能未开启'
  if (!order.value) return ''
  if (confirming.value) return '支付确认中，可下拉刷新'
  if (order.value.paymentStatus === 'REFUNDED') {
    return realPayment.value ? '退款已发起，以微信到账通知为准' : '退款已完成（模拟）'
  }
  if (order.value.paymentStatus === 'PAID') return realPayment.value ? '微信支付' : '模拟支付已完成'
  return realPayment.value ? '微信支付 · 结果以微信通知为准' : '模拟支付，不产生真实扣款'
})

const statusHint = computed(() => {
  if (!order.value) return ''
  if (active.value) return '充电进行中，费用实时计算'
  if (order.value.status === 'COMPLETED') return '本次充电已结束'
  if (order.value.status === 'ABNORMAL') return '设备异常导致本次充电提前结束'
  if (order.value.status === 'FAILED') return '设备未能启动本次充电'
  return ''
})

const avgPower = computed(() => {
  if (!order.value) return '--'
  const seconds = Number(order.value.durationSeconds || 0)
  if (!seconds) return '--'
  return (Number(order.value.energyKwh || 0) / (seconds / 3600)).toFixed(3)
})

const detailPrices = computed(() => {
  const list = (order.value && order.value.details) || []
  return list
    .map(
      (d) =>
        `${periodLabel(d.periodType)} ${Number(d.timePrice).toFixed(2)}/${Number(d.energyPrice).toFixed(2)}/${Number(
          d.servicePrice
        ).toFixed(2)}`
    )
    .join('；')
})

async function fetchOrder() {
  if (inFlight || !orderId.value) return
  inFlight = true
  try {
    order.value = await request({ url: `/api/app/orders/${orderId.value}` })
    if (order.value && !isActive(order.value.status)) stopTimer()
  } catch (e) {
    if (e.code !== 401) showError(e)
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

function copyOrderNo() {
  uni.setClipboardData({ data: order.value.orderNo })
}

function goCharging() {
  uni.navigateTo({ url: `/pages/charge/charging?orderId=${orderId.value}` })
}

function pay() {
  if (paying.value || confirming.value) return
  if (!runtime.paymentEnabled) {
    showError(new Error('支付功能已关闭'))
    return
  }
  const real = realPayment.value
  if (real && uni.getSystemInfoSync().uniPlatform !== 'mp-weixin') {
    showError(new Error('请在微信小程序内完成微信支付'))
    return
  }
  uni.showModal({
    title: real ? '微信支付' : '模拟支付',
    content: real
      ? `确认支付 ¥${money(order.value.totalFee)}？将调起微信支付。`
      : `确认支付 ¥${money(order.value.totalFee)}？本次为模拟支付，不会真实扣款。`,
    confirmText: '确认支付',
    confirmColor: '#00a870',
    success: async (res) => {
      if (!res.confirm) return
      await startPay()
    }
  })
}

async function startPay() {
  paying.value = true
  try {
    const res = await request({ url: `/api/app/orders/${orderId.value}/pay`, method: 'POST' })
    order.value = res
    if (!res.payParams) {
      // mock 同步成功，或真实通道判重后订单已是 PAID
      uni.showToast({ title: '支付成功', icon: 'success' })
      return
    }
    const invoked = await requestWechatPayment(res.payParams)
    if (invoked) {
      startConfirming()
    }
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    paying.value = false
  }
}

/** 调起微信收银台；用户主动取消不算错误，也不进入确认轮询。 */
function requestWechatPayment(params) {
  return new Promise((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: String(params.timeStamp),
      nonceStr: params.nonceStr,
      package: params.package,
      signType: params.signType || 'RSA',
      paySign: params.paySign,
      success: () => resolve(true),
      fail: (err) => {
        const msg = (err && err.errMsg) || ''
        if (msg.indexOf('cancel') >= 0) {
          resolve(false)
        } else {
          reject(new Error(`微信支付未完成：${msg || '未知错误'}`))
        }
      }
    })
  })
}

function startConfirming() {
  confirming.value = true
  confirmDeadline = Date.now() + 120000
  stopConfirmTimer()
  confirmTimer = setInterval(checkPaid, 2000)
}

function stopConfirmTimer() {
  if (confirmTimer) {
    clearInterval(confirmTimer)
    confirmTimer = null
  }
}

function stopConfirming() {
  confirming.value = false
  stopConfirmTimer()
}

async function checkPaid() {
  try {
    await fetchOrder()
  } catch (e) {
    return
  }
  if (order.value && order.value.paymentStatus === 'PAID') {
    stopConfirming()
    uni.showToast({ title: '支付成功', icon: 'success' })
  } else if (Date.now() > confirmDeadline) {
    stopConfirming()
    uni.showToast({ title: '支付结果确认中，请稍后下拉刷新', icon: 'none', duration: 2500 })
  }
}

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: '/pages/order/list' })
    }
  })
}

onLoad(async (options) => {
  orderId.value = options.id
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
  if (order.value && active.value) {
    fetchOrder()
    startTimer()
  }
  if (confirming.value && order.value && order.value.paymentStatus !== 'PAID') {
    confirmDeadline = Math.max(confirmDeadline, Date.now() + 30000)
    stopConfirmTimer()
    confirmTimer = setInterval(checkPaid, 2000)
  }
})

onPullDownRefresh(async () => {
  try {
    await fetchOrder()
  } finally {
    uni.stopPullDownRefresh()
  }
  if (confirming.value && order.value && order.value.paymentStatus === 'PAID') {
    stopConfirming()
    uni.showToast({ title: '支付成功', icon: 'success' })
  }
})

onHide(() => {
  stopTimer()
  stopConfirmTimer()
})
onUnload(() => {
  stopTimer()
  stopConfirmTimer()
})
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f7f6;
  padding-bottom: calc(170rpx + env(safe-area-inset-bottom));
}

.hero {
  padding: 0 32rpx 34rpx;
  background: linear-gradient(150deg, #07372a, #0a664b);
  color: #ffffff;
  border-radius: 0 0 44rpx 44rpx;
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -110rpx;
  top: -120rpx;
  width: 360rpx;
  height: 360rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(52, 211, 153, 0.22), transparent 68%);
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

.hero-state {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  position: relative;
  z-index: 1;
}

.state-pill {
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(167, 243, 208, 0.15);
  color: #a7f3d0;
  font-size: 22rpx;
  font-weight: 600;
}

.state-hint {
  margin-left: 14rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.55);
}

.hero-amount {
  margin-top: 14rpx;
  font-size: 76rpx;
  line-height: 1.1;
  letter-spacing: -3rpx;
  position: relative;
  z-index: 1;
}

.symbol {
  font-size: 30rpx;
  margin-right: 8rpx;
  opacity: 0.7;
}

.hero-sub {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.6);
  position: relative;
  z-index: 1;
}

.body {
  padding: 24rpx 32rpx calc(170rpx + env(safe-area-inset-bottom));
}

.timeline-card {
  padding: 26rpx 28rpx;
}

.tl-row {
  display: flex;
}

.tl-mark {
  width: 30rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.tl-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #00a870;
  margin-top: 8rpx;
  flex: 0 0 16rpx;
}

.tl-dot.gray {
  background: #d0d5dd;
}

.tl-line {
  width: 2rpx;
  flex: 1;
  background: #edf0ee;
  margin-top: 6rpx;
}

.tl-body {
  flex: 1;
  padding: 0 0 26rpx 16rpx;
  min-width: 0;
}

.tl-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
}

.tl-time {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  font-weight: 400;
  color: #98a2b3;
}

.tl-note {
  display: inline-block;
  margin-top: 8rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #fff6e8;
  color: #b54708;
  font-size: 18rpx;
}

.tl-row.last .tl-body {
  padding-bottom: 4rpx;
}

.tl-metrics {
  display: flex;
  margin-top: 8rpx;
  padding-top: 20rpx;
  border-top: 2rpx dashed #edf0ee;
}

.tl-metric {
  flex: 1;
  text-align: center;
}

.tl-metric + .tl-metric {
  border-left: 2rpx solid #f5f7f6;
}

.tl-value {
  font-size: 28rpx;
}

.tl-label {
  display: block;
  margin-top: 4rpx;
  font-size: 18rpx;
  color: #98a2b3;
}

.fee-card {
  padding: 10rpx 28rpx 14rpx;
}

.fee-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
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

.total-val {
  font-size: 30rpx;
}

.table-card {
  padding: 18rpx 24rpx 22rpx;
}

.t-head,
.t-row {
  display: flex;
  align-items: center;
  font-size: 20rpx;
  padding: 12rpx 0;
}

.t-head {
  color: #98a2b3;
}

.t-row {
  border-top: 2rpx solid #f5f7f6;
  color: #475467;
}

.t-time {
  width: 34%;
}

.t-type {
  width: 18%;
}

.t-duration {
  width: 24%;
  text-align: right;
}

.t-energy {
  width: 22%;
  text-align: right;
}

.t-fee {
  flex: 1;
  text-align: right;
  font-weight: 600;
  color: #101828;
}

.period-tag {
  display: inline-flex;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  background: #e6f7f1;
  color: #00895c;
  font-size: 18rpx;
}

.t-note {
  margin-top: 14rpx;
  font-size: 18rpx;
  line-height: 1.6;
  color: #98a2b3;
}

.info-card {
  padding: 8rpx 28rpx;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 20rpx 0;
  font-size: 24rpx;
  border-bottom: 2rpx solid #f5f7f6;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  color: #98a2b3;
}

.info-value {
  color: #101828;
  font-weight: 500;
  max-width: 68%;
  text-align: right;
  word-break: break-all;
}

.copy {
  color: #00895c;
}

.bottom-space {
  height: 20rpx;
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
  min-width: 0;
}

.sticky-price {
  font-size: 32rpx;
}

.sticky-unit {
  margin-left: 10rpx;
  font-size: 18rpx;
  font-weight: 400;
  color: #98a2b3;
}

.sticky-hint {
  margin-top: 4rpx;
  font-size: 18rpx;
  color: #98a2b3;
}

.sticky-btn {
  flex: 0 0 260rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 26rpx;
  padding: 0;
}
</style>
