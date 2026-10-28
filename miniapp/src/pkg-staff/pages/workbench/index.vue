<template>
  <view class="bc-page workbench">
    <view class="staff-bar">
      <view class="flex-1">
        <text class="who">{{ profile.nickName || profile.username || '运维' }}</text>
        <text class="scope">{{ scopeText }}</text>
      </view>
      <text class="chip" :class="onlineChip">{{ refreshedText }}</text>
      <text class="link" @tap="logout">退出</text>
    </view>

    <view v-if="notice" class="notice">{{ notice }}</view>

    <view class="card finder">
      <view class="row">
        <input
          v-model="deviceNoInput"
          class="input flex-1"
          placeholder="设备编号，或扫桩上二维码"
          placeholder-class="ph"
          @confirm="search"
        />
      </view>
      <view class="acts">
        <button class="btn-ghost act" @tap="scan">扫码</button>
        <button class="btn-primary act" :disabled="loading" @tap="search">
          {{ loading ? '加载中…' : '查看设备' }}
        </button>
      </view>
    </view>

    <block v-if="card">
      <view class="card device">
        <view class="row-between">
          <text class="device-name">{{ card.deviceName || card.deviceNo }}</text>
          <text class="chip" :class="card.onlineStatus === 'ONLINE' ? 'chip-green' : 'chip-gray'">
            {{ card.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
          </text>
        </view>
        <view class="device-no num">{{ card.deviceNo }}</view>
        <view class="kv">
          <text class="k">场站</text>
          <text class="v">{{ card.stationName || '--' }}</text>
          <text class="k">端口</text>
          <text class="v num">{{ card.portCount || 0 }}</text>
        </view>
        <view class="kv">
          <text class="k">类型</text>
          <text class="v">{{ deviceTypeText }}</text>
          <text class="k">额定</text>
          <text class="v num">{{ ratedText }}</text>
        </view>
        <view class="kv">
          <text class="k">协议</text>
          <text class="v">{{ card.protocol || '--' }}</text>
          <text class="k">启动</text>
          <text class="v">{{ startMethodText }}</text>
        </view>
        <view class="kv">
          <text class="k">固件</text>
          <text class="v num">{{ card.firmwareVersion || '--' }}</text>
          <text class="k">测点</text>
          <text class="v">{{ shortTime(card.lastMetricAt) }}</text>
        </view>
      </view>

      <view class="section-title"><text>端口</text><text class="more">{{ portSummary }}</text></view>
      <view v-for="port in card.portList" :key="port.portNumber" class="card port">
        <view class="row-between">
          <view class="row">
            <text class="port-no num">{{ port.portNumber }}</text>
            <text class="chip" :class="portChip(port.portStatus)">{{ portStatusText(port.portStatus) }}</text>
            <text v-if="port.plugged === '1'" class="chip chip-gray">已插枪</text>
          </view>
          <text class="muted tiny">{{ shortTime(port.portLastMetricAt) }}</text>
        </view>

        <view class="metrics">
          <view class="metric">
            <text class="metric-num num">{{ decimal(port.liveVoltage, 0) }}</text>
            <text class="metric-label">V</text>
          </view>
          <view class="metric">
            <text class="metric-num num">{{ decimal(port.liveCurrentA, 1) }}</text>
            <text class="metric-label">A</text>
          </view>
          <view class="metric">
            <text class="metric-num num">{{ decimal(port.liveTemperature, 1) }}</text>
            <text class="metric-label">℃</text>
          </view>
          <view class="metric">
            <text class="metric-num num">{{ decimal(port.livePowerKw, 2) }}</text>
            <text class="metric-label">kW</text>
          </view>
        </view>

        <view v-if="port.currentOrder" class="order">
          <view class="row-between">
            <text class="order-no num">{{ port.currentOrder.orderNumber }}</text>
            <text class="chip chip-green">{{ orderStatusText(port.currentOrder.orderStatus) }}</text>
          </view>
          <view class="kv">
            <text class="k">车主</text>
            <text class="v">{{ ownerText(port.currentOrder) }}</text>
            <text class="k">车牌</text>
            <text class="v">{{ port.currentOrder.vehiclePlate || '未绑定' }}</text>
          </view>
          <view class="kv">
            <text class="k">已充</text>
            <text class="v num">{{ duration(port.currentOrder.durationSeconds) }}</text>
            <text class="k">电量</text>
            <text class="v num">{{ port.currentOrder.energyKwh }} kWh</text>
            <text class="k">金额</text>
            <text class="v num">¥ {{ money(port.currentOrder.totalAmount) }}</text>
          </view>
          <view v-if="port.currentOrder.vinCode" class="kv">
            <text class="k">VIN</text>
            <text class="v num">{{ port.currentOrder.vinCode }}</text>
          </view>
        </view>

        <view v-if="card.canStop || card.canUnlock" class="ops">
          <button
            v-if="card.canStop && port.currentOrder"
            class="op danger"
            :disabled="!!pending"
            @tap="command(port, 'stop')"
          >
            {{ pending === key(port.portNumber, 'stop') ? '处理中…' : '停充' }}
          </button>
          <button v-if="card.canUnlock" class="op" :disabled="!!pending" @tap="command(port, 'unlock')">
            {{ pending === key(port.portNumber, 'unlock') ? '处理中…' : '解锁' }}
          </button>
        </view>
      </view>

      <view class="section-title">
        <text>近 24 小时未闭环告警</text>
        <text class="more">{{ (card.alertList || []).length }} 条</text>
      </view>
      <view v-if="!(card.alertList || []).length" class="card empty-card">
        <text class="muted small">没有未闭环告警。端口故障需人工确认，恢复前不会自动消失。</text>
      </view>
      <view v-for="item in card.alertList" :key="item.alertId" class="card alert">
        <view class="row-between">
          <view class="row">
            <text class="chip" :class="severityChip(item.alertSeverity)">{{ severityText(item.alertSeverity) }}</text>
            <text class="alert-name">{{ item.alertName }}</text>
            <text v-if="item.alertPortNo" class="muted tiny">{{ item.alertPortNo }} 号枪</text>
          </view>
          <text class="muted tiny">{{ statusText(item.alertStatus) }}</text>
        </view>
        <view class="alert-content">{{ item.alertContent }}</view>
        <view class="row-between alert-foot">
          <text class="muted tiny">{{ shortTime(item.alertLastOccurredAt || item.alertOccurredAt) }} · {{ item.alertCode }}</text>
          <view class="row">
            <text
              v-if="profile.canAckAlert && item.alertStatus === 'OPEN'"
              class="op-link"
              @tap="handleAlert(item, 'ack')"
            >确认</text>
            <text
              v-if="profile.canResolveAlert"
              class="op-link"
              @tap="handleAlert(item, 'resolve')"
            >已处理</text>
          </view>
        </view>
      </view>
    </block>

    <view v-else-if="!loading" class="card empty-card">
      <text class="muted small">扫码或输入设备编号查看现场状态。只能看到自己有权的场站设备。</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onHide, onLoad, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import staffRequest, { clearStaffToken, getStaffToken } from '@/utils/staffRequest'
import { scanDeviceCode, isScanCancel } from '@/utils/deviceCode'
import { duration, money, shortTime } from '@/utils/format'

const AUTO_REFRESH_MS = 30000
const POLL_ROUNDS = 3
const POLL_INTERVAL_MS = 3000

const PORT_STATUS = { IDLE: '空闲', CHARGING: '充电中', OCCUPIED: '占用', FAULT: '故障', OFFLINE: '离线' }
const SEVERITY = { CRITICAL: '严重', MAJOR: '重要', INFO: '提示' }
const ALERT_STATUS = { OPEN: '未确认', ACKNOWLEDGED: '已确认', RESOLVED: '已闭环' }

const profile = ref({})
const card = ref(null)
const deviceNoInput = ref('')
const loading = ref(false)
const pending = ref('')
const notice = ref('')
const refreshedAt = ref('')
let timer = null

const scopeText = computed(() => {
  const data = profile.value
  if (!data.username) return '未确认运维身份'
  const roles = (data.roleKeys || []).join(' / ') || '无角色'
  const scope = data.stationLimited ? `${data.stationCount || 0} 个场站` : '全部场站'
  return `${roles} · ${scope}`
})

const onlineChip = computed(() => {
  if (!refreshedAt.value) return 'chip-gray'
  return card.value && card.value.onlineStatus === 'ONLINE' ? 'chip-green' : 'chip-gray'
})

const refreshedText = computed(() => (refreshedAt.value ? `${refreshedAt.value} 已刷新` : '未加载'))

const deviceTypeText = computed(() => {
  const data = card.value || {}
  return [data.deviceType, data.scene, data.groupName].filter(Boolean).join(' · ') || '--'
})

const startMethodText = computed(() => {
  const map = { SCAN: '扫码', CARD: '刷卡', VIN: '即插即充', PASSWORD: '密码', REMOTE: '远程' }
  const value = (card.value || {}).startMethod
  return map[value] || value || '--'
})

const ratedText = computed(() => {
  const data = card.value || {}
  if (!data.ratedVoltage && !data.ratedPowerKw) return '--'
  return `${data.ratedVoltage || '--'}V · ${data.ratedPowerKw || '--'}kW`
})

const portSummary = computed(() => {
  const ports = (card.value && card.value.portList) || []
  const charging = ports.filter((item) => item.portStatus === 'CHARGING').length
  const fault = ports.filter((item) => item.portStatus === 'FAULT').length
  return `充电中 ${charging} · 故障 ${fault}`
})

onLoad((options) => {
  if (options && options.deviceNo) deviceNoInput.value = String(options.deviceNo).toUpperCase()
})

onShow(() => {
  if (!getStaffToken()) {
    uni.redirectTo({ url: '/pkg-staff/pages/login/index' })
    return
  }
  startAutoRefresh()
  loadProfile().then(() => {
    if (deviceNoInput.value) loadCard()
  })
})

onHide(stopAutoRefresh)
onUnload(stopAutoRefresh)

onPullDownRefresh(async () => {
  await Promise.all([loadProfile(), deviceNoInput.value ? loadCard() : Promise.resolve()])
  uni.stopPullDownRefresh()
})

function startAutoRefresh() {
  stopAutoRefresh()
  if (!deviceNoInput.value) return
  timer = setInterval(() => {
    if (pending.value) return
    loadCard({ silent: true })
  }, AUTO_REFRESH_MS)
}

function stopAutoRefresh() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function loadProfile() {
  try {
    profile.value = await staffRequest({ url: '/api/v1/staff/profile', silent: true })
    notice.value = ''
  } catch (e) {
    notice.value = describe(e)
  }
}

async function loadCard(options = {}) {
  const deviceNo = String(deviceNoInput.value || '').trim().toUpperCase()
  if (!deviceNo) {
    if (!options.silent) notice.value = '请先扫码或输入设备编号'
    return null
  }
  if (!options.silent) loading.value = true
  try {
    const data = await staffRequest({ url: `/api/v1/staff/devices/${deviceNo}`, silent: !!options.silent })
    card.value = data
    refreshedAt.value = nowText()
    notice.value = ''
    return data
  } catch (e) {
    if (e.code === 404 || e.code === 403) card.value = null
    if (!options.silent) notice.value = describe(e)
    return null
  } finally {
    if (!options.silent) loading.value = false
  }
}

function search() {
  loadCard().then(() => startAutoRefresh())
}

async function scan() {
  try {
    const parsed = await scanDeviceCode()
    deviceNoInput.value = String(parsed.deviceNo).toUpperCase()
    await search()
  } catch (e) {
    if (isScanCancel(e)) return
    uni.showToast({
      title: e.message || '当前环境不支持扫码，请手动输入',
      icon: 'none',
      duration: 2500
    })
  }
}

/** 指令下发成功只代表设备收到了，端口状态才是结果；轮询几轮看不出来就如实提示继续观察。 */
async function command(port, action) {
  const deviceNo = card.value.deviceNo
  const label = action === 'stop' ? '停充' : '解锁'
  const confirmed = await confirmModal(
    `确认${label}`,
    action === 'stop'
      ? `将停止 ${deviceNo} ${port.portNumber} 号枪的充电，车主侧会同步结束订单。`
      : `将向 ${deviceNo} ${port.portNumber} 号枪下发解锁指令，枪头是否弹开以现场为准。`
  )
  if (!confirmed) return

  pending.value = key(port.portNumber, action)
  notice.value = ''
  try {
    await staffRequest({
      url: `/api/v1/chargers/${card.value.chargerId}/ports/${port.portNumber}/${action}`,
      method: 'POST'
    })
    notice.value = `${deviceNo} ${port.portNumber} 号枪${label}指令已下发`
    if (action === 'stop') await watchStop(port.portNumber)
  } catch (e) {
    notice.value = describe(e)
  } finally {
    pending.value = ''
    startAutoRefresh()
  }
}

async function watchStop(portNo) {
  for (let round = 0; round < POLL_ROUNDS; round += 1) {
    await sleep(POLL_INTERVAL_MS)
    const fresh = await loadCard({ silent: true })
    const port = ((fresh && fresh.portList) || []).find((item) => item.portNumber === portNo)
    if (!port) continue
    if (port.portStatus !== 'CHARGING' && !port.currentOrder) {
      notice.value = `${portNo} 号枪已退出充电，当前${portStatusText(port.portStatus)}`
      return
    }
  }
  notice.value = '指令已下发，端口仍在充电中，请观察端口状态或稍后重进'
}

async function handleAlert(item, action) {
  const label = action === 'ack' ? '确认' : '标记为已处理'
  if (!(await confirmModal(`${label}告警`, `${item.alertName}：${item.alertContent || ''}`))) return
  try {
    await staffRequest({ url: `/api/v1/alerts/${item.alertId}/${action}`, method: 'POST' })
    notice.value = `告警已${label}`
    await loadCard({ silent: true })
  } catch (e) {
    notice.value = describe(e)
  }
}

async function logout() {
  if (!(await confirmModal('退出运维登录', '只退出运维会话，用户端登录状态不受影响。'))) return
  try {
    await staffRequest({ url: '/logout', method: 'POST', silent: true })
  } catch (e) {
    // 会话已失效时本地清理即可
  }
  clearStaffToken()
  card.value = null
  profile.value = {}
  stopAutoRefresh()
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.switchTab({ url: '/pages/mine/index' })
}

function confirmModal(title, content) {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmColor: '#00a870',
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false)
    })
  })
}

function describe(e) {
  if (!e) return '请求失败'
  if (e.code === 428) return '该账号需先在电脑端完成首次改密'
  if (e.code === 403) return '没有该操作的权限，请联系管理员分配角色'
  if (e.code === -1) return e.message
  return e.message || '请求失败'
}

function key(portNo, action) {
  return `${portNo}:${action}`
}

function portStatusText(status) {
  return PORT_STATUS[status] || status || '--'
}

function orderStatusText(status) {
  if (status === 'CHARGING') return '充电中'
  if (status === 'STOPPING') return '结算中'
  return status || '--'
}

function severityText(level) {
  return SEVERITY[level] || level || '--'
}

function statusText(status) {
  return ALERT_STATUS[status] || status || '--'
}

function ownerText(order) {
  const nickname = order.appUserNickname
  const phone = order.appUserPhone
  if (nickname && phone) return `${nickname} · ${phone}`
  return nickname || phone || '--'
}

function portChip(status) {
  if (status === 'CHARGING') return 'chip-green'
  if (status === 'FAULT') return 'chip-red'
  if (status === 'OCCUPIED') return 'chip-amber'
  return 'chip-gray'
}

function severityChip(level) {
  if (level === 'CRITICAL') return 'chip-red'
  if (level === 'MAJOR') return 'chip-amber'
  return 'chip-gray'
}

function decimal(value, digits) {
  if (value === null || value === undefined || value === '') return '--'
  return Number(value).toFixed(digits)
}

function nowText() {
  const date = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
</script>

<style lang="scss" scoped>
.workbench {
  padding-top: 16rpx;
}

.staff-bar {
  display: flex;
  align-items: center;
  padding: 8rpx 4rpx 20rpx;
}

.who {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--ink);
}

.scope {
  display: block;
  margin-top: 6rpx;
  font-size: 20rpx;
  color: var(--ink-3);
}

.notice {
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: var(--amber-soft);
  color: #b54708;
  font-size: 22rpx;
  line-height: 1.6;
  margin-bottom: 20rpx;
}

.finder {
  padding: 20rpx 24rpx 24rpx;
}

.input {
  font-size: 28rpx;
  height: 64rpx;
  line-height: 64rpx;
}

.ph {
  color: #c6cdd5;
  font-size: 24rpx;
}

.acts {
  margin-top: 20rpx;
  display: flex;
  gap: 16rpx;
}

.act {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 26rpx;
}

.device-name {
  font-size: 30rpx;
  font-weight: 700;
}

.device-no {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--brand-600);
}

.kv {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8rpx 12rpx;
  font-size: 22rpx;
}

.k {
  color: var(--ink-3);
}

.v {
  color: var(--ink-2);
  margin-right: 8rpx;
}

.port-no {
  font-size: 32rpx;
  margin-right: 14rpx;
}

.metrics {
  margin-top: 20rpx;
  display: flex;
}

.metric {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 6rpx;
}

.metric-num {
  font-size: 30rpx;
  color: var(--ink);
}

.metric-label {
  font-size: 20rpx;
  color: var(--ink-3);
}

.order {
  margin-top: 20rpx;
  padding: 20rpx;
  border-radius: 20rpx;
  background: var(--line-soft);
}

.order-no {
  font-size: 22rpx;
  color: var(--ink-2);
}

.ops {
  margin-top: 22rpx;
  display: flex;
  gap: 16rpx;
}

.op {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 999rpx;
  background: #ffffff;
  border: 2rpx solid var(--brand-line);
  color: var(--brand-600);
  font-size: 26rpx;
  text-align: center;
}

.op.danger {
  border-color: #fecdca;
  color: #b42318;
}

.op::after {
  border: none;
}

.empty-card {
  padding: 34rpx 28rpx;
}

.alert-name {
  font-size: 26rpx;
  font-weight: 600;
  margin-right: 10rpx;
}

.alert-content {
  margin-top: 14rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: var(--ink-2);
}

.alert-foot {
  margin-top: 16rpx;
}

.op-link {
  margin-left: 24rpx;
  font-size: 22rpx;
  color: var(--brand-600);
}
</style>
