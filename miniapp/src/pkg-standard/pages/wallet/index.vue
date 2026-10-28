<template>
  <view class="wallet-page">
    <view class="balance-hero">
      <view class="hero-top">
        <text class="hero-label">可用余额（元）</text>
        <view class="hero-badge" @tap="showRules">资金说明</view>
      </view>
      <view class="hero-amount num">{{ money(summary.balance) }}</view>
      <view class="hero-sub">
        <view class="hero-sub-item">
          <text class="hero-sub-num num">{{ money(summary.frozen) }}</text>
          <text class="hero-sub-label">冻结中</text>
        </view>
        <view class="hero-sub-item">
          <text class="hero-sub-num num">{{ money(summary.totalRecharge) }}</text>
          <text class="hero-sub-label">累计充值</text>
        </view>
        <view class="hero-sub-item">
          <text class="hero-sub-num num">{{ money(summary.totalWithdrawn) }}</text>
          <text class="hero-sub-label">累计提现</text>
        </view>
      </view>
      <view class="hero-actions">
        <view class="hero-btn primary" @tap="toggleAction('recharge')">充值</view>
        <view class="hero-btn" @tap="toggleAction('withdraw')">提现</view>
      </view>
      <view v-if="summary.pendingWithdrawalCount" class="hero-tip">
        有 {{ summary.pendingWithdrawalCount }} 笔提现在审核或待打款，对应金额已计入冻结。
      </view>
    </view>

    <view class="body">
      <view v-if="action === 'recharge'" class="card form-card">
        <view class="form-head">
          <text class="form-title">余额充值</text>
          <text class="form-close" @tap="action = ''">收起</text>
        </view>
        <view class="presets">
          <view v-for="item in presets" :key="item" class="preset"
                :class="{ active: Number(amount) === item }" @tap="pickPreset(item)">
            <text class="num">{{ item }}</text>
            <text class="preset-unit">元</text>
          </view>
        </view>
        <view class="amount-input">
          <text class="unit">¥</text>
          <input v-model="amount" type="digit" placeholder="输入充值金额，最多两位小数" class="input num" />
        </view>
        <view class="form-note">{{ rechargeNote }}</view>
        <button class="submit" :loading="submitting" :disabled="!runtime.paymentEnabled || realPayment" @tap="submitRecharge">确认充值</button>
      </view>

      <view v-if="action === 'withdraw'" class="card form-card">
        <view class="form-head">
          <text class="form-title">申请提现</text>
          <text class="form-close" @tap="action = ''">收起</text>
        </view>
        <view class="amount-input">
          <text class="unit">¥</text>
          <input v-model="amount" type="digit" placeholder="输入提现金额，最多两位小数" class="input num" />
        </view>
        <view class="form-note">
          提交后立即从可用余额转入冻结，平台审核通过并打款后出账；驳回时冻结金额退回余额。
        </view>
        <button class="submit" :loading="submitting" @tap="submitWithdraw">提交提现申请</button>
      </view>

      <view class="card tabs-card">
        <view class="tabs">
          <view v-for="tab in tabs" :key="tab.key" class="tab" :class="{ active: activeTab === tab.key }"
                @tap="switchTab(tab.key)">
            {{ tab.label }}
          </view>
        </view>

        <view v-if="activeTab === 'transactions'">
          <view v-if="transactions.length" class="list">
            <view v-for="item in transactions" :key="item.id" class="row">
              <view class="row-main">
                <text class="row-title">{{ txLabel(item.bizType) }}</text>
                <text class="row-sub">{{ item.remark || item.refNo }}</text>
                <text class="row-time">{{ datetime(item.createTime) }}</text>
              </view>
              <view class="row-side">
                <text class="row-amount num" :class="{ plus: Number(item.amount) > 0, minus: Number(item.amount) < 0 }">
                  {{ signed(item.amount) }}
                </text>
                <text class="row-balance num">余额 {{ money(item.balanceAfter) }}</text>
              </view>
            </view>
          </view>
          <view v-else class="empty">{{ loading ? '加载中…' : '还没有资金流水' }}</view>
        </view>

        <view v-if="activeTab === 'recharges'">
          <view v-if="recharges.length" class="list">
            <view v-for="item in recharges" :key="item.id" class="row">
              <view class="row-main">
                <text class="row-title">充值 {{ money(item.amount) }} 元</text>
                <text class="row-sub num">{{ item.rechargeNo }}</text>
                <text class="row-time">{{ datetime(item.paidAt || item.createTime) }} · {{ item.channel || '—' }}</text>
              </view>
              <view class="row-side">
                <text class="row-status" :class="statusTone(item.status)">{{ rechargeLabel(item.status) }}</text>
              </view>
            </view>
          </view>
          <view v-else class="empty">{{ loading ? '加载中…' : '还没有充值记录' }}</view>
        </view>

        <view v-if="activeTab === 'withdrawals'">
          <view v-if="withdrawals.length" class="list">
            <view v-for="item in withdrawals" :key="item.id" class="row">
              <view class="row-main">
                <text class="row-title">提现 {{ money(item.amount) }} 元</text>
                <text class="row-sub num">{{ item.withdrawalNo }}</text>
                <text class="row-time">{{ datetime(item.createTime) }}{{ item.reviewer ? ' · ' + item.reviewer : '' }}</text>
              </view>
              <view class="row-side">
                <text class="row-status" :class="statusTone(item.status)">{{ withdrawalLabel(item.status) }}</text>
              </view>
            </view>
          </view>
          <view v-else class="empty">{{ loading ? '加载中…' : '还没有提现记录' }}</view>
        </view>

        <view v-if="canLoadMore" class="more" @tap="loadMore">加载更多</view>
      </view>

      <view class="footer">充值为模拟支付；提现由运营人工审核，到账时间以实际打款为准。</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { runtimeConfig as runtime } from '@/utils/runtime'
import { datetime, money } from '@/utils/format'

const PAGE_SIZE = 20
const presets = [50, 100, 200, 500]
const tabs = [
  { key: 'transactions', label: '资金流水' },
  { key: 'recharges', label: '充值记录' },
  { key: 'withdrawals', label: '提现记录' }
]
const ACTIVE_TABS = ['transactions', 'recharges', 'withdrawals']

const summary = ref({})
const loading = ref(false)
const submitting = ref(false)
const realPayment = computed(() => runtime.paymentEnabled && runtime.paymentMode !== 'mock')
const rechargeNote = computed(() => {
  if (!runtime.paymentEnabled) return '支付功能未开启，暂不能充值。'
  if (realPayment.value) return '钱包在线充值暂未开通；订单支付请到订单详情用微信支付。'
  return '当前为模拟支付，确认后立即到账；真实支付接入前请勿用于生产收款。'
})
const action = ref('')
const amount = ref('')
const activeTab = ref('transactions')
const transactions = ref([])
const recharges = ref([])
const withdrawals = ref([])
const totals = ref({ transactions: 0, recharges: 0, withdrawals: 0 })
const pages = ref({ transactions: 1, recharges: 1, withdrawals: 1 })

const canLoadMore = computed(() => {
  const key = activeTab.value
  return listOf(key).length < (totals.value[key] || 0)
})

function listOf(key) {
  return key === 'recharges' ? recharges.value : key === 'withdrawals' ? withdrawals.value : transactions.value
}

function setList(key, rows) {
  if (key === 'recharges') recharges.value = rows
  else if (key === 'withdrawals') withdrawals.value = rows
  else transactions.value = rows
}

async function loadSummary() {
  summary.value = await request({ url: '/api/app/wallet' })
}

async function loadList(key, append = false) {
  const pageNum = append ? pages.value[key] + 1 : 1
  const page = await request({
    url: `/api/app/wallet/${key}`,
    data: { pageNum, pageSize: PAGE_SIZE }
  })
  const rows = (page && page.rows) || []
  setList(key, append ? listOf(key).concat(rows) : rows)
  totals.value[key] = (page && page.total) || rows.length
  pages.value[key] = pageNum
}

async function load() {
  loading.value = true
  try {
    await Promise.all([
      loadSummary(),
      ...ACTIVE_TABS.map(key => loadList(key).catch(() => {}))
    ])
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    loading.value = false
  }
}

function toggleAction(next) {
  action.value = action.value === next ? '' : next
  amount.value = ''
}

function pickPreset(value) {
  amount.value = String(value)
}

function normalizedAmount() {
  const value = Number(amount.value)
  if (!Number.isFinite(value) || value <= 0) {
    uni.showToast({ title: '请输入大于 0 的金额', icon: 'none' })
    return null
  }
  if (!/^\d+(\.\d{1,2})?$/.test(String(amount.value).trim())) {
    uni.showToast({ title: '金额最多两位小数', icon: 'none' })
    return null
  }
  return value
}

async function submitRecharge() {
  if (!runtime.paymentEnabled) {
    showError(new Error('支付功能已关闭'))
    return
  }
  const value = normalizedAmount()
  if (value == null) return
  submitting.value = true
  try {
    await request({ url: '/api/app/wallet/recharges', method: 'POST', data: { amount: value } })
    uni.showToast({ title: '充值成功', icon: 'success' })
    action.value = ''
    amount.value = ''
    await Promise.all([loadSummary(), loadList('transactions'), loadList('recharges')])
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    submitting.value = false
  }
}

function submitWithdraw() {
  const value = normalizedAmount()
  if (value == null) return
  if (value > Number(summary.value.balance || 0)) {
    uni.showToast({ title: '可用余额不足', icon: 'none' })
    return
  }
  uni.showModal({
    title: '提交提现申请',
    content: `提现 ${money(value)} 元后将先从可用余额转入冻结，平台审核通过并打款后出账。`,
    confirmColor: '#00a870',
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await request({ url: '/api/app/wallet/withdrawals', method: 'POST', data: { amount: value } })
        uni.showToast({ title: '已提交审核', icon: 'success' })
        action.value = ''
        amount.value = ''
        await Promise.all([loadSummary(), loadList('withdrawals'), loadList('transactions')])
      } catch (e) {
        if (e.code !== 401) showError(e)
      } finally {
        submitting.value = false
      }
    }
  })
}

async function switchTab(key) {
  activeTab.value = key
  if (!listOf(key).length) {
    try {
      await loadList(key)
    } catch (e) {
      if (e.code !== 401) showError(e)
    }
  }
}

async function loadMore() {
  try {
    await loadList(activeTab.value, true)
  } catch (e) {
    if (e.code !== 401) showError(e)
  }
}

function showRules() {
  uni.showModal({
    title: '资金说明',
    content: '充值即时到账；提现申请后金额转入冻结，驳回退回余额，平台打款后从冻结出账。全部流水保留变更后余额快照。',
    showCancel: false,
    confirmText: '知道了',
    confirmColor: '#00a870'
  })
}

function txLabel(type) {
  return {
    RECHARGE: '余额充值',
    WITHDRAW_FREEZE: '提现冻结',
    WITHDRAW_REVERSAL: '提现驳回退回',
    WITHDRAW_PAY: '提现打款',
    ADJUST: '人工调整'
  }[type] || type || '资金变动'
}

function rechargeLabel(status) {
  return { PENDING: '待支付', PAID: '已到账', CANCELLED: '已取消' }[status] || status
}

function withdrawalLabel(status) {
  return { PENDING: '审核中', APPROVED: '待打款', REJECTED: '已驳回', PAID: '已打款' }[status] || status
}

function statusTone(status) {
  if (status === 'PAID') return 'ok'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'bad'
  return 'wait'
}

function signed(value) {
  const number = Number(value || 0)
  if (number > 0) return '+' + money(number)
  if (number < 0) return '-' + money(Math.abs(number))
  return '0.00'
}

onShow(load)
onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.wallet-page {
  min-height: 100vh;
  background: var(--bg);
}

.balance-hero {
  padding: 36rpx 36rpx 40rpx;
  background: linear-gradient(160deg, #06382a 0%, #08553f 58%, #0a7a5a 100%);
  color: #ffffff;
  border-radius: 0 0 44rpx 44rpx;
  position: relative;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    right: -120rpx;
    top: -140rpx;
    width: 420rpx;
    height: 420rpx;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(52, 211, 153, 0.28), transparent 68%);
  }
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
}

.hero-label {
  font-size: 26rpx;
  opacity: 0.82;
}

.hero-badge {
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.32);
  font-size: 22rpx;
  opacity: 0.92;
}

.hero-amount {
  margin-top: 16rpx;
  font-size: 76rpx;
  font-weight: 700;
  letter-spacing: -1rpx;
  position: relative;
  z-index: 1;
}

.hero-sub {
  display: flex;
  gap: 52rpx;
  margin-top: 16rpx;
  position: relative;
  z-index: 1;
}

.hero-sub-item {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.hero-sub-num {
  font-size: 28rpx;
  font-weight: 600;
}

.hero-sub-label {
  font-size: 22rpx;
  opacity: 0.68;
}

.hero-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 32rpx;
  position: relative;
  z-index: 1;
}

.hero-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.14);
  border: 1rpx solid rgba(255, 255, 255, 0.26);

  &.primary {
    background: #ffffff;
    color: var(--brand-700);
    border-color: #ffffff;
  }
}

.hero-tip {
  margin-top: 20rpx;
  font-size: 22rpx;
  opacity: 0.78;
  position: relative;
  z-index: 1;
}

.body {
  padding: 24rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
  margin-top: -20rpx;
}

.form-card {
  margin-bottom: 20rpx;
}

.form-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.form-title {
  font-size: 32rpx;
  font-weight: 700;
}

.form-close {
  font-size: 24rpx;
  color: var(--ink-3);
}

.presets {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.preset {
  flex: 1;
  height: 92rpx;
  border-radius: 20rpx;
  border: 2rpx solid var(--line);
  background: var(--line-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2rpx;
  font-size: 30rpx;
  font-weight: 600;

  &.active {
    border-color: var(--brand);
    background: var(--brand-soft);
    color: var(--brand-700);
  }
}

.preset-unit {
  font-size: 22rpx;
  color: var(--ink-3);
}

.amount-input {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: var(--line-soft);
  border: 2rpx solid var(--line);
}

.unit {
  font-size: 30rpx;
  color: var(--ink-2);
}

.input {
  flex: 1;
  font-size: 34rpx;
  font-weight: 600;
}

.form-note {
  margin-top: 16rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: var(--ink-3);
}

.submit {
  margin-top: 24rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 999rpx;
  background: var(--brand);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;

  &::after {
    border: none;
  }
}

.tabs-card {
  padding-top: 16rpx;
}

.tabs {
  display: flex;
  gap: 40rpx;
  padding: 8rpx 4rpx 20rpx;
  border-bottom: 2rpx solid var(--line);
}

.tab {
  font-size: 28rpx;
  color: var(--ink-2);
  padding-bottom: 12rpx;
  position: relative;

  &.active {
    color: var(--ink);
    font-weight: 700;

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      bottom: -2rpx;
      transform: translateX(-50%);
      width: 40rpx;
      height: 6rpx;
      border-radius: 999rpx;
      background: var(--brand);
    }
  }
}

.list {
  display: flex;
  flex-direction: column;
}

.row {
  display: flex;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid var(--line-soft);

  &:last-child {
    border-bottom: none;
  }
}

.row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.row-title {
  font-size: 28rpx;
  font-weight: 600;
}

.row-sub {
  font-size: 22rpx;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-time {
  font-size: 22rpx;
  color: var(--ink-3);
}

.row-side {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
}

.row-amount {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--ink-2);

  &.plus {
    color: var(--brand);
  }

  &.minus {
    color: var(--red);
  }
}

.row-balance {
  font-size: 22rpx;
  color: var(--ink-3);
}

.row-status {
  font-size: 24rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;

  &.ok {
    color: var(--brand-700);
    background: var(--brand-soft);
  }

  &.wait {
    color: #b54708;
    background: var(--amber-soft);
  }

  &.bad {
    color: var(--red);
    background: var(--red-soft);
  }
}

.empty {
  padding: 60rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: var(--ink-3);
}

.more {
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--brand-700);
}

.footer {
  margin-top: 28rpx;
  text-align: center;
  font-size: 22rpx;
  line-height: 1.6;
  color: var(--ink-3);
}
</style>
