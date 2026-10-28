<template>
  <view class="growth-page">
    <view class="hero">
      <view class="hero-top">
        <text class="hero-kicker">MEMBER</text>
        <text class="hero-title">会员权益</text>
      </view>
      <view class="level-card">
        <view class="level-main">
          <view class="level-name">{{ profile.levelName || '普通会员' }}</view>
          <view class="level-code num">{{ profile.levelCode || 'NORMAL' }}</view>
        </view>
        <view class="level-discount">
          <text class="discount-num num">{{ discountText }}</text>
          <text class="discount-label">会员折扣</text>
        </view>
      </view>
      <view class="points-line">
        <view class="points-block">
          <text class="points-num num">{{ profile.points || 0 }}</text>
          <text class="points-label">可用积分</text>
        </view>
        <view class="points-block">
          <text class="points-num num">{{ money(profile.totalSpent) }}</text>
          <text class="points-label">累计消费（元）</text>
        </view>
        <view class="points-block">
          <text class="points-num num">{{ profile.couponCount || 0 }}</text>
          <text class="points-label">持有券</text>
        </view>
      </view>
      <view class="progress">
        <view class="progress-head">
          <text>{{ profile.nextLevelName ? '距 ' + profile.nextLevelName : '已是最高等级' }}</text>
          <text class="num">{{ profile.nextLevelName ? '还差 ' + (profile.pointsToNext || 0) + ' 积分' : '' }}</text>
        </view>
        <view class="progress-track">
          <view class="progress-fill" :style="{ width: progressWidth }"></view>
        </view>
      </view>
    </view>

    <view class="body">
      <view class="card invite-card">
        <view class="card-head">
          <text class="card-title">邀请好友</text>
          <text class="muted">好友首笔已支付订单后双方得积分</text>
        </view>
        <view class="invite-code">
          <text class="code-label">我的邀请码</text>
          <text class="code num">{{ profile.inviteCode || '--' }}</text>
          <text v-if="profile.inviteCode" class="code-copy" @tap="copyCode">复制</text>
        </view>
        <view v-if="!profile.inviterUserId" class="bind-row">
          <input v-model="inviteCode" class="bind-input" placeholder="输入好友邀请码" maxlength="20" />
          <view class="bind-btn" :class="{ disabled: !inviteCode || binding }" @tap="bindInvite">
            {{ binding ? '绑定中' : '绑定' }}
          </view>
        </view>
        <view v-else class="bound-tip">已绑定邀请人，推荐奖励关系不可重复绑定。</view>
      </view>

      <view class="card">
        <view class="card-head">
          <text class="card-title">可领取优惠券</text>
          <text class="muted">每张券按每人限领</text>
        </view>
        <view v-if="templates.length" class="coupon-list">
          <view v-for="item in templates" :key="item.id" class="coupon" :class="{ dim: !item.claimable }">
            <view class="coupon-left">
              <view class="coupon-value">
                <text v-if="item.couponType === 'AMOUNT'" class="coupon-num num">¥{{ money(item.valueAmount) }}</text>
                <text v-else class="coupon-num num">{{ (Number(item.valueAmount) * 10).toFixed(1) }}折</text>
                <text class="coupon-min">满 {{ money(item.minAmount) }} 可用</text>
              </view>
            </view>
            <view class="coupon-main">
              <text class="coupon-name">{{ item.name }}</text>
              <text class="coupon-meta">{{ item.startTime.slice(0, 10) }} 至 {{ item.endTime.slice(0, 10) }}</text>
              <text class="coupon-meta">{{ item.stackable === '1' ? '可叠加' : '不可叠加' }} · 剩余 {{ item.remaining || 0 }} 张</text>
            </view>
            <view class="coupon-action" :class="{ disabled: !item.claimable || claiming === item.id }"
                  @tap="claim(item)">
              {{ claiming === item.id ? '领取中' : item.claimable ? '领取' : '已领' }}
            </view>
          </view>
        </view>
        <view v-else class="empty">暂无可领取的优惠券</view>
      </view>

      <view class="card">
        <view class="card-head">
          <text class="card-title">我的优惠券</text>
          <text class="muted">{{ coupons.length }} 张</text>
        </view>
        <view v-if="coupons.length" class="coupon-list">
          <view v-for="item in coupons" :key="item.id" class="coupon" :class="{ dim: item.status !== 'UNUSED' }">
            <view class="coupon-left">
              <view class="coupon-value">
                <text v-if="item.couponType === 'AMOUNT'" class="coupon-num num">¥{{ money(item.valueAmount) }}</text>
                <text v-else class="coupon-num num">{{ (Number(item.valueAmount) * 10).toFixed(1) }}折</text>
                <text class="coupon-min">满 {{ money(item.minAmount) }} 可用</text>
              </view>
            </view>
            <view class="coupon-main">
              <text class="coupon-name">{{ item.name }}</text>
              <text class="coupon-meta num">{{ item.couponNo }}</text>
              <text class="coupon-meta">{{ item.startTime.slice(0, 10) }} 至 {{ item.endTime.slice(0, 10) }}</text>
            </view>
            <view class="coupon-status" :class="item.status.toLowerCase()">
              {{ statusLabel(item.status) }}
            </view>
          </view>
        </view>
        <view v-else class="empty">还没有优惠券，先领取一张吧</view>
      </view>

      <view class="card">
        <view class="card-head">
          <text class="card-title">积分明细</text>
          <text class="muted">支付完成后入账</text>
        </view>
        <view v-if="pointLogs.length" class="log-list">
          <view v-for="item in pointLogs" :key="item.id" class="log-row">
            <view class="log-main">
              <text class="log-title">{{ logLabel(item.bizType) }}</text>
              <text class="log-time">{{ formatTime(item.createTime) }}</text>
            </view>
            <text class="log-change num" :class="{ plus: item.points > 0 }">
              {{ item.points > 0 ? '+' : '' }}{{ item.points }}
            </text>
            <text class="log-balance num">余 {{ item.balanceAfter }}</text>
          </view>
        </view>
        <view v-else class="empty">还没有积分记录</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import request from '@/utils/request'
import { money } from '@/utils/format'

const profile = ref({})
const templates = ref([])
const coupons = ref([])
const pointLogs = ref([])
const inviteCode = ref('')
const claiming = ref(null)
const binding = ref(false)

const discountText = computed(() => {
  const rate = Number(profile.value.discountRate || 1)
  return rate >= 1 ? '无折扣' : (rate * 10).toFixed(1) + ' 折'
})

const progressWidth = computed(() => {
  const current = Number(profile.value.points || 0)
  const next = Number(profile.value.nextLevelPoints || 0)
  if (!next || next <= 0) return '100%'
  const prev = Math.max(next - 100, 0)
  const span = Math.max(next - prev, 1)
  return Math.min(Math.max((current - prev) / span * 100, 4), 100) + '%'
})

async function load() {
  try {
    const [p, t, c, l] = await Promise.all([
      request({ url: '/api/app/growth/profile' }),
      request({ url: '/api/app/growth/coupon-templates' }).catch(() => []),
      request({ url: '/api/app/growth/coupons' }).catch(() => []),
      request({ url: '/api/app/growth/point-logs' }).catch(() => [])
    ])
    profile.value = p || {}
    templates.value = t || []
    coupons.value = c || []
    pointLogs.value = l || []
  } catch (e) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
  }
}

async function claim(item) {
  if (!item.claimable || claiming.value) return
  claiming.value = item.id
  try {
    await request({ url: `/api/app/growth/coupons/${item.id}/claim`, method: 'POST' })
    uni.showToast({ title: '领取成功', icon: 'success' })
    await load()
  } catch (e) {
    uni.showToast({ title: e.message || '领取失败', icon: 'none' })
  } finally {
    claiming.value = null
  }
}

async function bindInvite() {
  const code = inviteCode.value.trim().toUpperCase()
  if (!code || binding.value) return
  binding.value = true
  try {
    await request({ url: '/api/app/growth/referral', method: 'POST', data: { inviteCode: code } })
    uni.showToast({ title: '绑定成功', icon: 'success' })
    inviteCode.value = ''
    await load()
  } catch (e) {
    uni.showToast({ title: e.message || '绑定失败', icon: 'none' })
  } finally {
    binding.value = false
  }
}

function copyCode() {
  uni.setClipboardData({
    data: profile.value.inviteCode,
    success: () => uni.showToast({ title: '已复制邀请码', icon: 'none' })
  })
}

function statusLabel(status) {
  return { UNUSED: '可用', LOCKED: '锁定', USED: '已用', EXPIRED: '过期' }[status] || status
}

function logLabel(type) {
  return {
    ORDER: '充电订单积分',
    REFERRAL: '邀请奖励',
    REFERRED: '被邀请注册奖励',
    ADJUST: '人工调整'
  }[type] || type
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

onShow(load)
onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.growth-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 40rpx;
}

.hero {
  padding: 48rpx 32rpx 56rpx;
  background: linear-gradient(160deg, #0b3d2e 0%, #00704a 62%, #00a870 100%);
  border-radius: 0 0 40rpx 40rpx;
  color: #fff;
}

.hero-kicker {
  font-size: 20rpx;
  letter-spacing: 4rpx;
  opacity: 0.7;
}

.hero-title {
  display: block;
  margin-top: 6rpx;
  font-size: 40rpx;
  font-weight: 600;
}

.level-card {
  margin-top: 28rpx;
  padding: 32rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.level-name {
  font-size: 44rpx;
  font-weight: 600;
}

.level-code {
  margin-top: 6rpx;
  font-size: 22rpx;
  letter-spacing: 2rpx;
  opacity: 0.7;
}

.level-discount {
  text-align: right;
}

.discount-num {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
}

.discount-label {
  font-size: 22rpx;
  opacity: 0.75;
}

.points-line {
  margin-top: 28rpx;
  display: flex;
}

.points-block {
  flex: 1;
  text-align: center;
}

.points-num {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
}

.points-label {
  font-size: 22rpx;
  opacity: 0.72;
}

.progress {
  margin-top: 28rpx;
}

.progress-head {
  display: flex;
  justify-content: space-between;
  font-size: 22rpx;
  opacity: 0.82;
}

.progress-track {
  margin-top: 12rpx;
  height: 10rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.22);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 999rpx;
  background: #7ef0bc;
}

.body {
  padding: 24rpx 24rpx 0;
}

.card {
  margin-bottom: 24rpx;
  padding: 28rpx;
  border-radius: 28rpx;
  background: #fff;
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2d3d;
}

.muted {
  font-size: 22rpx;
  color: #8a94a6;
}

.invite-code {
  display: flex;
  align-items: center;
  padding: 22rpx 24rpx;
  border-radius: 20rpx;
  background: #f2faf6;
}

.code-label {
  font-size: 24rpx;
  color: #5b6b7f;
}

.code {
  flex: 1;
  margin-left: 24rpx;
  font-size: 34rpx;
  font-weight: 600;
  letter-spacing: 3rpx;
  color: #00704a;
}

.code-copy {
  font-size: 24rpx;
  color: #00a870;
}

.bind-row {
  margin-top: 20rpx;
  display: flex;
  gap: 16rpx;
}

.bind-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 18rpx;
  background: #f5f7fa;
  font-size: 26rpx;
}

.bind-btn {
  width: 140rpx;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 18rpx;
  background: #00a870;
  color: #fff;
  font-size: 26rpx;
}

.bind-btn.disabled {
  opacity: 0.45;
}

.bound-tip {
  margin-top: 18rpx;
  font-size: 22rpx;
  color: #8a94a6;
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.coupon {
  display: flex;
  align-items: stretch;
  border-radius: 20rpx;
  background: #fffdf6;
  border: 1rpx solid #f3e6c8;
  overflow: hidden;
}

.coupon.dim {
  opacity: 0.55;
  background: #f7f8fa;
  border-color: #e8ebf0;
}

.coupon-left {
  width: 176rpx;
  padding: 20rpx 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1rpx dashed #e8d9b4;
  background: #fff8e7;
}

.coupon-value {
  text-align: center;
}

.coupon-num {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #b8860b;
}

.coupon-min {
  display: block;
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #96825a;
}

.coupon-main {
  flex: 1;
  padding: 20rpx 18rpx;
  min-width: 0;
}

.coupon-name {
  display: block;
  font-size: 27rpx;
  font-weight: 600;
  color: #1f2d3d;
}

.coupon-meta {
  display: block;
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #8a94a6;
}

.coupon-action {
  width: 120rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 25rpx;
  color: #b8860b;
  font-weight: 600;
}

.coupon-action.disabled {
  color: #b7bfcc;
}

.coupon-status {
  width: 100rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23rpx;
  color: #00a870;
}

.coupon-status.used,
.coupon-status.expired {
  color: #b7bfcc;
}

.log-list {
  display: flex;
  flex-direction: column;
}

.log-row {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f0f2f5;
}

.log-row:last-child {
  border-bottom: none;
}

.log-main {
  flex: 1;
}

.log-title {
  display: block;
  font-size: 26rpx;
  color: #1f2d3d;
}

.log-time {
  display: block;
  margin-top: 4rpx;
  font-size: 21rpx;
  color: #9aa4b2;
}

.log-change {
  width: 120rpx;
  text-align: right;
  font-size: 28rpx;
  color: #5b6b7f;
}

.log-change.plus {
  color: #00a870;
}

.log-balance {
  width: 120rpx;
  text-align: right;
  font-size: 22rpx;
  color: #9aa4b2;
}

.empty {
  padding: 30rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #9aa4b2;
}
</style>
