<template>
  <view class="bc-page reservation-page">
    <view class="page-head">
      <text class="page-title">端口预约</text>
      <text class="muted small">到点未启动自动失效</text>
    </view>

    <view v-if="activeReservation" class="card active-card">
      <view class="row-between">
        <text class="active-title">当前预约</text>
        <text class="chip chip-green">{{ activeCountdownText }}</text>
      </view>
      <view class="active-plate num">{{ activeReservation.plateNo || '未填车牌' }}</view>
      <view class="active-port">
        {{ activeReservation.deviceNo || '设备' }} · {{ activeReservation.portNo }} 号枪
      </view>
      <view class="active-window num">
        {{ shortTime(activeReservation.startTime) }} — {{ shortTime(activeReservation.endTime) }}
      </view>
      <view class="active-no tiny muted">预约号 {{ activeReservation.reservationNo }}</view>
      <view class="active-actions">
        <button class="btn-primary flex-1" @tap="goScan">扫码启动</button>
        <button class="btn-ghost" :loading="submitting" @tap="cancelReservation(activeReservation)">取消</button>
      </view>
      <view class="active-tip tiny">
        启动充电时会自动核销这条预约；预约只锁端口，不锁电量，也不产生费用。
      </view>
    </view>

    <view class="card">
      <view class="section-title">选择场站与设备</view>
      <picker mode="selector" :range="stationNames" :value="stationIndex" @change="onStationChange">
        <view class="field">
          <text class="field-label">场站</text>
          <text class="field-value ellipsis">{{ stationNames[stationIndex] || '加载中…' }}</text>
          <text class="field-arrow">›</text>
        </view>
      </picker>
      <picker mode="selector" :range="deviceNames" :value="deviceIndex" :disabled="!devices.length"
              @change="onDeviceChange">
        <view class="field">
          <text class="field-label">设备</text>
          <text class="field-value ellipsis">{{ deviceNames[deviceIndex] || (devices.length ? '请选择' : '该场站暂无设备') }}</text>
          <text class="field-arrow">›</text>
        </view>
      </picker>

      <view class="ports-title">端口占用情况</view>
      <view v-if="ports.length" class="ports">
        <view v-for="port in ports" :key="port.portNo"
              :class="['port', portClass(port), pickedPort === port.portNo ? 'on' : '']"
              @tap="pickPort(port)">
          <text class="port-no num">{{ port.portNo }}</text>
          <text class="port-status">{{ portStatusText(port) }}</text>
        </view>
      </view>
      <view v-else class="muted small">{{ loadingPorts ? '加载中…' : '请先选择设备' }}</view>
    </view>

    <view class="card">
      <view class="section-title">预约时段</view>
      <view class="chips">
        <view v-for="item in startOptions" :key="item.minutes"
              :class="['pick-chip', startOffset === item.minutes ? 'on' : '']" @tap="setStartOffset(item.minutes)">
          <text>{{ item.label }}</text>
          <text class="pick-chip-sub tiny num">{{ windowPreview(item.minutes, durationMinutes).startText }}</text>
        </view>
      </view>
      <view class="chips">
        <view v-for="item in durationOptions" :key="item.minutes"
              :class="['pick-chip', durationMinutes === item.minutes ? 'on' : '']" @tap="durationMinutes = item.minutes">
          <text>{{ item.label }}</text>
        </view>
      </view>
      <view class="window">
        <text class="window-label">实际时段</text>
        <text class="window-value num">{{ windowText }}</text>
      </view>
      <view class="rule tiny muted">
        规则：单次不少于 30 分钟、最长 24 小时，最多约到 7 天内；同一账号同时只能有一条生效中的预约。
      </view>
    </view>

    <view class="card">
      <view class="section-title">车辆与备注</view>
      <picker mode="selector" :range="vehiclePlates" :value="vehicleIndex" :disabled="!vehicles.length"
              @change="onVehicleChange">
        <view class="field">
          <text class="field-label">车牌</text>
          <text class="field-value num">{{ plateText }}</text>
          <text class="field-arrow">›</text>
        </view>
      </picker>
      <view class="field">
        <text class="field-label">备注</text>
        <input v-model="remark" class="field-input" maxlength="60" placeholder="如「到站后需帮忙降地锁」" />
      </view>
      <button class="btn-primary submit" :disabled="!canSubmit || submitting" :loading="submitting"
              @tap="submitReservation">
        {{ submitting ? '提交中…' : '确认预约' }}
      </button>
      <view class="submit-tip tiny muted">{{ submitHint }}</view>
    </view>

    <view class="card">
      <view class="section-title">
        <text>预约记录</text>
        <text class="more" @tap="load">刷新</text>
      </view>
      <view v-if="reservations.length" class="list">
        <view v-for="item in reservations" :key="item.id" class="record">
          <view class="flex-1">
            <text class="record-title ellipsis">{{ item.deviceNo || '设备' }} · {{ item.portNo }} 号枪</text>
            <text class="record-sub tiny muted num">
              {{ shortTime(item.startTime) }} — {{ shortTime(item.endTime) }}
            </text>
            <text v-if="item.orderNo" class="record-sub tiny muted">核销订单 {{ item.orderNo }}</text>
          </view>
          <view class="record-side">
            <text :class="['chip', statusChip(item.status)]">{{ statusText(item.status) }}</text>
            <text v-if="item.status === 'ACTIVE'" class="record-cancel tiny" @tap="cancelReservation(item)">取消</text>
          </view>
        </view>
      </view>
      <view v-else class="empty small">{{ loading ? '加载中…' : '还没有预约记录' }}</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { shortTime } from '@/utils/format'

const startOptions = [
  { label: '现在', minutes: 0 },
  { label: '15 分后', minutes: 15 },
  { label: '30 分后', minutes: 30 },
  { label: '1 小时后', minutes: 60 },
  { label: '2 小时后', minutes: 120 }
]
const durationOptions = [
  { label: '30 分钟', minutes: 30 },
  { label: '1 小时', minutes: 60 },
  { label: '2 小时', minutes: 120 },
  { label: '4 小时', minutes: 240 }
]
const PORT_STATUS = { IDLE: '空闲', CHARGING: '充电中', OCCUPIED: '占用', FAULT: '故障' }
const STATUS_TEXT = { ACTIVE: '生效中', USED: '已核销', CANCELED: '已取消', EXPIRED: '已过期' }

const stations = ref([])
const devices = ref([])
const ports = ref([])
const reservations = ref([])
const vehicles = ref([])
const stationIndex = ref(0)
const deviceIndex = ref(-1)
const vehicleIndex = ref(-1)
const pickedPort = ref(null)
const startOffset = ref(30)
const durationMinutes = ref(60)
const remark = ref('')
const plateNo = ref('')
const loading = ref(false)
const loadingPorts = ref(false)
const submitting = ref(false)
const nowTick = ref(Date.now())
// 从场站详情带过来的预选：只在第一次加载时应用，避免刷新时覆盖用户的手动选择。
const preset = ref({})

let timer = null

const stationNames = computed(() => stations.value.map(item => item.name || `场站 #${item.id}`))
const deviceNames = computed(() => devices.value.map(item => `${item.deviceNo}（${item.portCount} 路）`))
const vehiclePlates = computed(() => vehicles.value.map(item => item.plateNo || '未命名车辆'))

const currentStation = computed(() => stations.value[stationIndex.value])
const currentDevice = computed(() => devices.value[deviceIndex.value])

const plateText = computed(() => plateNo.value || vehiclePlates.value[vehicleIndex.value] || '可留空，到场再填')

const windowRange = computed(() => windowPreview(startOffset.value, durationMinutes.value))
const windowText = computed(() => `${windowRange.value.startText} — ${windowRange.value.endText}`)

const activeReservation = computed(() => reservations.value.find(item => item.status === 'ACTIVE') || null)

const activeCountdownText = computed(() => {
  const item = activeReservation.value
  if (!item) {
    return ''
  }
  const start = toMs(item.startTime)
  const end = toMs(item.endTime)
  const now = nowTick.value
  if (now < start) {
    return `${Math.max(1, Math.round((start - now) / 60000))} 分钟后开始`
  }
  if (now <= end) {
    return `剩 ${Math.max(1, Math.round((end - now) / 60000))} 分钟`
  }
  return '已过时段'
})

const canSubmit = computed(() => Boolean(currentDevice.value && pickedPort.value && windowRange.value.start))

const submitHint = computed(() => {
  if (!currentDevice.value) return '请先选择设备'
  if (!pickedPort.value) return '请选择一个未被占用的端口'
  const port = ports.value.find(item => item.portNo === pickedPort.value)
  if (port && clashes(port)) {
    return '所选时段该端口已被预约，请换时段或换端口'
  }
  return `将预约 ${currentDevice.value.deviceNo} ${pickedPort.value} 号枪 ${windowText.value}`
})

function toMs(value) {
  if (!value) {
    return 0
  }
  return new Date(String(value).replace('T', ' ').replace(/-/g, '/')).getTime()
}

function isoLocal(date) {
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function windowPreview(offsetMinutes, duration) {
  const start = new Date(Date.now() + offsetMinutes * 60000)
  start.setSeconds(0, 0)
  const end = new Date(start.getTime() + duration * 60000)
  return {
    start: isoLocal(start),
    end: isoLocal(end),
    startText: shortTime(isoLocal(start)),
    endText: shortTime(isoLocal(end))
  }
}

function setStartOffset(minutes) {
  startOffset.value = minutes
}

function clashes(port) {
  if (port.reservedByMe) {
    return false
  }
  if (!port.reservedStart || !port.reservedEnd) {
    return false
  }
  const start = toMs(windowRange.value.start)
  const end = toMs(windowRange.value.end)
  return toMs(port.reservedStart) < end && toMs(port.reservedEnd) > start
}

function portStatusText(port) {
  if (port.reservedByMe) {
    return '我的预约'
  }
  if (clashes(port)) {
    return '被预约'
  }
  return PORT_STATUS[port.status] || port.status || '—'
}

function portClass(port) {
  if (clashes(port) || port.status === 'FAULT') {
    return 'is-busy'
  }
  if (port.status !== 'IDLE') {
    return 'is-busy'
  }
  return 'is-free'
}

function pickPort(port) {
  if (portClass(port) === 'is-busy') {
    uni.showToast({ title: `${port.portNo} 号枪${portStatusText(port)}，换一支试试`, icon: 'none' })
    return
  }
  pickedPort.value = port.portNo
}

function statusText(status) {
  return STATUS_TEXT[status] || status || '—'
}

function statusChip(status) {
  if (status === 'ACTIVE') return 'chip-amber'
  if (status === 'USED') return 'chip-green'
  return 'chip-gray'
}

async function loadStations() {
  stations.value = (await request({ url: '/api/app/stations' })) || []
  if (stationIndex.value >= stations.value.length) {
    stationIndex.value = 0
  }
}
async function loadStationDetail() {
  devices.value = []
  ports.value = []
  deviceIndex.value = -1
  pickedPort.value = null
  const station = currentStation.value
  if (!station) {
    return
  }
  const data = await request({ url: `/api/app/stations/${station.id}` })
  devices.value = (data && data.devices) || []
  if (devices.value.length) {
    const wantedDevice = devices.value.findIndex(item => String(item.id) === String(preset.value.chargerId))
    deviceIndex.value = wantedDevice >= 0 ? wantedDevice : 0
    await loadPorts()
    const wantedPort = ports.value.find(item => String(item.portNo) === String(preset.value.portNo))
    if (wantedPort && portClass(wantedPort) !== 'is-busy') {
      pickedPort.value = wantedPort.portNo
    }
  }
  preset.value = {}
}

async function loadPorts() {
  ports.value = []
  pickedPort.value = null
  const device = currentDevice.value
  if (!device) {
    return
  }
  loadingPorts.value = true
  try {
    ports.value = (await request({ url: '/api/app/reservations/ports', data: { chargerId: device.id } })) || []
  } finally {
    loadingPorts.value = false
  }
}

async function loadReservations() {
  reservations.value = (await request({ url: '/api/app/reservations' })) || []
}

async function loadVehicles() {
  vehicles.value = (await request({ url: '/api/app/vehicles' }).catch(() => [])) || []
  const index = vehicles.value.findIndex(item => item.isDefault === '1' || item.defaultFlag === '1')
  vehicleIndex.value = index >= 0 ? index : (vehicles.value.length ? 0 : -1)
}

async function load() {
  loading.value = true
  try {
    await Promise.all([loadStations(), loadVehicles()])
    const wantedStation = stations.value.findIndex(item => String(item.id) === String(preset.value.stationId))
    if (wantedStation >= 0) {
      stationIndex.value = wantedStation
    }
    await loadStationDetail()
    await loadReservations()
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    loading.value = false
  }
}

async function onStationChange(event) {
  stationIndex.value = Number(event.detail.value)
  await loadStationDetail()
}

async function onDeviceChange(event) {
  deviceIndex.value = Number(event.detail.value)
  await loadPorts()
}

function onVehicleChange(event) {
  vehicleIndex.value = Number(event.detail.value)
  const hit = vehicles.value[vehicleIndex.value]
  plateNo.value = hit ? hit.plateNo : ''
}

async function submitReservation() {
  const device = currentDevice.value
  if (!device || !pickedPort.value) {
    return
  }
  const port = ports.value.find(item => item.portNo === pickedPort.value)
  if (port && clashes(port)) {
    uni.showToast({ title: '该端口在所选时段已被预约', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await request({
      url: '/api/app/reservations',
      method: 'POST',
      data: {
        chargerId: device.id,
        portNo: pickedPort.value,
        startTime: windowRange.value.start,
        endTime: windowRange.value.end,
        plateNo: plateNo.value || null,
        remark: remark.value || null
      }
    })
    uni.showToast({ title: '预约成功', icon: 'success' })
    remark.value = ''
    await loadPorts()
    await loadReservations()
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    submitting.value = false
  }
}

function cancelReservation(item) {
  uni.showModal({
    title: '取消预约',
    content: `释放 ${item.deviceNo || '设备'} ${item.portNo} 号枪的 ${shortTime(item.startTime)}—${shortTime(item.endTime)} 时段？`,
    confirmText: '取消预约',
    cancelText: '再想想',
    async success(res) {
      if (!res.confirm) {
        return
      }
      submitting.value = true
      try {
        await request({ url: `/api/app/reservations/${item.id}/cancel`, method: 'POST' })
        uni.showToast({ title: '已取消', icon: 'success' })
        await loadPorts()
        await loadReservations()
      } catch (e) {
        if (e.code !== 401) showError(e)
      } finally {
        submitting.value = false
      }
    }
  })
}

function goScan() {
  uni.navigateTo({ url: '/pages/scan/scan' })
}

onLoad(query => {
  preset.value = { stationId: query.stationId, chargerId: query.chargerId, portNo: query.portNo }
})

onShow(() => {
  load()
  timer = setInterval(() => {
    nowTick.value = Date.now()
  }, 30000)
})

onUnload(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
.active-card {
  background: linear-gradient(140deg, #ffffff, #f2fbf7);
}

.active-title {
  font-size: 26rpx;
  color: var(--ink-3);
}

.active-plate {
  margin-top: 14rpx;
  font-size: 44rpx;
}

.active-port {
  font-size: 28rpx;
  color: var(--ink-2);
}

.active-window {
  margin-top: 6rpx;
  font-size: 26rpx;
  color: var(--brand-600);
}

.active-no {
  margin-top: 4rpx;
}

.active-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 20rpx;
}

.active-tip {
  margin-top: 12rpx;
}

.field {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  border-bottom: 2rpx solid #f1f4f2;
}

.field:last-of-type {
  border-bottom: none;
}

.field-label {
  width: 130rpx;
  font-size: 26rpx;
  color: var(--ink-3);
}

.field-value {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: var(--ink);
}

.field-input {
  flex: 1;
  font-size: 28rpx;
}

.field-arrow {
  font-size: 34rpx;
  color: var(--ink-3);
}

.ports-title {
  margin: 26rpx 0 14rpx;
  font-size: 26rpx;
  color: var(--ink-3);
}

.ports {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.port {
  width: 140rpx;
  padding: 12rpx 0;
  border-radius: 20rpx;
  border: 2rpx solid #e7ebe9;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #fafbfa;
}

.port.is-free {
  border-color: rgba(0, 184, 124, 0.35);
  background: #f4fbf8;
}

.port.is-busy {
  opacity: 0.55;
}

.port.on {
  border-color: var(--brand-600);
  background: var(--brand-soft);
}

.port-no {
  font-size: 30rpx;
}

.port-status {
  font-size: 20rpx;
  color: var(--ink-3);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-bottom: 10rpx;
}

.pick-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12rpx 20rpx;
  border-radius: 20rpx;
  border: 2rpx solid #e7ebe9;
  font-size: 26rpx;
  background: #fafbfa;
}

.pick-chip.on {
  border-color: var(--brand-600);
  background: var(--brand-soft);
  color: var(--brand-600);
}

.pick-chip-sub {
  color: var(--ink-3);
}

.window {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  padding: 16rpx 20rpx;
  border-radius: 20rpx;
  background: #f7f9f8;
}

.window-label {
  font-size: 26rpx;
  color: var(--ink-3);
}

.window-value {
  font-size: 28rpx;
  color: var(--ink);
}

.rule {
  margin-top: 12rpx;
  line-height: 34rpx;
}

.submit {
  margin-top: 24rpx;
}

.submit-tip {
  margin-top: 12rpx;
  text-align: center;
}

.list {
  margin-top: 6rpx;
}

.record {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 0;
  border-bottom: 2rpx solid #f1f4f2;
}

.record:last-child {
  border-bottom: none;
}

.record-title {
  display: block;
  font-size: 28rpx;
}

.record-sub {
  display: block;
  margin-top: 4rpx;
}

.record-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
}

.record-cancel {
  color: var(--red, #b42318);
}

.empty {
  padding: 40rpx 0;
  text-align: center;
  color: var(--ink-3);
}
</style>
