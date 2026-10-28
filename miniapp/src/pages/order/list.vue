<template>
  <view class="order-page">
    <view class="seg">
      <view
        v-for="item in tabs"
        :key="item.value"
        :class="['seg-item', tab === item.value ? 'on' : '']"
        @tap="switchTab(item.value)"
      >{{ item.label }}</view>
    </view>

    <view class="list">
      <view v-if="loading && list.length === 0" class="card muted small">正在加载订单…</view>
      <view v-else-if="!loading && list.length === 0" class="card empty">
        <view class="empty-mark">单</view>
        <view>{{ emptyText }}</view>
        <button v-if="tab !== 'FINISHED'" class="btn-primary empty-btn" @tap="goScan">去扫码充电</button>
      </view>

      <view v-for="item in list" :key="item.id" class="card order-item" @tap="openOrder(item)">
        <view class="item-head">
          <view :class="['state', stateClass(item)]">
            <view class="state-dot"></view>
            <text>{{ orderStatus(item.status) }}</text>
          </view>
          <text class="time num">{{ shortTime(item.startTime) }}</text>
        </view>

        <view class="item-main">
          <view class="thumb">{{ item.stationName ? item.stationName.slice(0, 1) : '充' }}</view>
          <view class="item-info">
            <view class="station ellipsis">{{ item.stationName }}</view>
            <view class="sub ellipsis">{{ item.deviceNo }} · {{ item.portNo }} 号端口</view>
          </view>
          <view class="item-amount">
            <text class="amount num">¥{{ money(item.totalFee) }}</text>
            <text :class="['pay-tag', item.paymentStatus === 'PAID' ? 'paid' : '', item.paymentStatus === 'REFUNDED' ? 'refunded' : '']">
              {{ paymentStatus(item.paymentStatus) }}
            </text>
          </view>
        </view>

        <view class="item-foot">
          <view class="metrics">
            <text class="metric"><text class="metric-val num">{{ kwh(item.energyKwh) }}</text> kWh</text>
            <text class="metric"><text class="metric-val num">{{ duration(item.durationSeconds) }}</text></text>
          </view>
          <view :class="['item-btn', actionOf(item).solid ? 'solid' : '']">{{ actionOf(item).label }}</view>
        </view>
      </view>

      <view v-if="list.length > 0" class="load-more">
        {{ loading ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onHide, onUnload, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { money, kwh, duration, shortTime, orderStatus, isActive, paymentStatus } from '@/utils/format'

const userStore = useUserStore()
const tab = ref('ALL')
const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(true)
let timer = null

const tabs = [
  { label: '全部', value: 'ALL' },
  { label: '进行中', value: 'ACTIVE' },
  { label: '已完成', value: 'FINISHED' }
]

const emptyText = computed(() => {
  if (tab.value === 'ACTIVE') return '当前没有进行中的充电订单'
  if (tab.value === 'FINISHED') return '还没有已完成订单'
  return '还没有充电订单'
})

function stateClass(item) {
  if (isActive(item.status)) return 'active'
  if (item.status === 'ABNORMAL' || item.status === 'FAILED') return 'abnormal'
  return 'done'
}

function actionOf(item) {
  if (isActive(item.status)) return { label: '查看充电', solid: false }
  if (item.paymentStatus === 'UNPAID' && item.status !== 'FAILED') return { label: '去支付', solid: true }
  return { label: '查看详情', solid: false }
}

async function load(reset) {
  if (loading.value) return
  if (reset) {
    pageNum.value = 1
    total.value = 0
    hasMore.value = true
  }
  if (!hasMore.value && !reset) return
  loading.value = true
  try {
    await userStore.ensureLogin()
    const params = { pageNum: pageNum.value, pageSize: 10 }
    if (tab.value !== 'ALL') params.status = tab.value
    const data = await request({ url: '/api/app/orders', data: params })
    const rows = (data && data.rows) || []
    total.value = (data && data.total) || 0
    list.value = reset ? rows : list.value.concat(rows)
    hasMore.value = list.value.length < total.value
    if (hasMore.value) pageNum.value += 1
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    loading.value = false
  }
}

function switchTab(value) {
  if (tab.value === value) return
  tab.value = value
  list.value = []
  load(true)
}

function openOrder(item) {
  if (isActive(item.status)) {
    uni.navigateTo({ url: `/pages/charge/charging?orderId=${item.id}` })
  } else {
    uni.navigateTo({ url: `/pages/order/detail?id=${item.id}` })
  }
}

function goScan() {
  uni.navigateTo({ url: '/pages/scan/scan' })
}

function startTimer() {
  stopTimer()
  timer = setInterval(() => {
    if (pageNum.value <= 2) load(true)
  }, 10000)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onShow(() => {
  load(true)
  startTimer()
})

onHide(stopTimer)
onUnload(stopTimer)

onReachBottom(() => {
  if (hasMore.value && !loading.value) load(false)
})

onPullDownRefresh(async () => {
  await load(true)
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.order-page {
  min-height: 100vh;
  background: #f5f7f6;
  padding: 24rpx 32rpx calc(140rpx + env(safe-area-inset-bottom));
}

.list {
  margin-top: 24rpx;
}

.order-item {
  padding: 24rpx 26rpx;
}

.item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.state {
  display: flex;
  align-items: center;
  font-size: 22rpx;
  font-weight: 600;
}

.state-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  margin-right: 10rpx;
  background: currentColor;
}

.state.active {
  color: #00895c;
}

.state.abnormal {
  color: #b54708;
}

.state.done {
  color: #98a2b3;
}

.time {
  font-size: 20rpx;
  font-weight: 400;
  color: #98a2b3;
}

.item-main {
  margin-top: 20rpx;
  display: flex;
  align-items: center;
}

.thumb {
  width: 80rpx;
  height: 80rpx;
  flex: 0 0 80rpx;
  border-radius: 22rpx;
  margin-right: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 800;
  background:
    radial-gradient(circle at 70% 20%, rgba(255, 255, 255, 0.5), transparent 45%),
    linear-gradient(145deg, #19c08a, #007a52);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.station {
  font-size: 28rpx;
  font-weight: 600;
}

.sub {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #98a2b3;
}

.item-amount {
  margin-left: 16rpx;
  text-align: right;
}

.amount {
  display: block;
  font-size: 36rpx;
}

.pay-tag {
  display: inline-block;
  margin-top: 6rpx;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
  background: #fff6e8;
  color: #b54708;
  font-size: 18rpx;
}

.pay-tag.paid {
  background: #f2f4f7;
  color: #667085;
}

.pay-tag.refunded {
  background: #eef4ff;
  color: #3538cd;
}

.item-foot {
  margin-top: 20rpx;
  padding-top: 18rpx;
  border-top: 2rpx dashed #edf0ee;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metrics {
  display: flex;
  align-items: center;
  color: #98a2b3;
  font-size: 20rpx;
}

.metric + .metric {
  margin-left: 20rpx;
}

.metric-val {
  font-size: 22rpx;
  color: #475467;
}

.item-btn {
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  border: 2rpx solid #c6ebdd;
  color: #00895c;
  font-size: 22rpx;
}

.item-btn.solid {
  background: #00a870;
  border-color: #00a870;
  color: #ffffff;
  font-weight: 600;
}

.load-more {
  text-align: center;
  padding: 36rpx 0 12rpx;
  font-size: 20rpx;
  color: #c6cdd5;
}

.empty-btn {
  margin: 32rpx auto 0;
  width: 360rpx;
  height: 78rpx;
  line-height: 78rpx;
  font-size: 26rpx;
}
</style>
