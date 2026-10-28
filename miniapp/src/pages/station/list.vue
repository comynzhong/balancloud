<template>
  <view class="bc-page station-list-page">
    <view class="search-box">
      <view class="search-icon"></view>
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索场站名称或地址"
        placeholder-class="search-ph"
        confirm-type="search"
        @input="onSearchInput"
      />
      <text v-if="keyword" class="clear" @tap="clearKeyword">×</text>
    </view>

    <view class="filters">
      <view
        v-for="item in filters"
        :key="item.value"
        :class="['filter', filter === item.value ? 'on' : '']"
        @tap="filter = item.value"
      >{{ item.label }}</view>
    </view>

    <view class="result-line">
      <text>共 <text class="strong">{{ resultList.length }}</text> 个场站</text>
      <text class="locate-action" @tap="refreshLocation">{{ locateText }}</text>
    </view>

    <view v-if="loading" class="card muted small">正在查询场站…</view>
    <view v-else-if="resultList.length === 0" class="card empty">
      <view class="empty-mark">桩</view>
      <view>没有找到符合条件的场站</view>
    </view>

    <view v-for="station in resultList" :key="station.id" class="card station-card" @tap="goDetail(station.id)">
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
          <text class="st-charging">{{ station.chargingPortCount }} 充电中</text>
          <text v-if="station.faultPortCount" class="st-fault">{{ station.faultPortCount }} 故障</text>
        </view>
      </view>
      <view class="side">
        <text v-if="station.distanceText" class="st-dist">{{ station.distanceText }}</text>
        <text v-else class="st-dist gray">未知</text>
        <view class="nav-btn" @tap.stop="navigate(station)">
          <view class="nav-mark"></view>
        </view>
      </view>
    </view>

    <view v-if="resultList.length" class="footer-note">共 {{ stationCount }} 台设备 · {{ onlineCount }} 台在线</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import { getLocation, sortByDistance, formatDistance } from '@/utils/geo'

const userStore = useUserStore()
const keyword = ref('')
const coords = ref(null)
const list = ref([])
const loading = ref(true)
const locating = ref(false)
const filter = ref('ALL')
let searchTimer = null

const filters = [
  { label: '距离最近', value: 'ALL' },
  { label: '空闲优先', value: 'IDLE' },
  { label: '营业中', value: 'ACTIVE' }
]

const locateText = ref('正在获取定位…')

const stationCount = computed(() => list.value.reduce((sum, item) => sum + Number(item.deviceCount || 0), 0))
const onlineCount = computed(() => list.value.reduce((sum, item) => sum + Number(item.onlineDeviceCount || 0), 0))

const resultList = computed(() => {
  let rows = list.value
  if (filter.value === 'ACTIVE') {
    rows = rows.filter((item) => item.status === 'ACTIVE')
  }
  if (filter.value === 'IDLE') {
    rows = rows.slice().sort((a, b) => {
      if (b.idlePortCount !== a.idlePortCount) return b.idlePortCount - a.idlePortCount
      if (a.distance === null && b.distance === null) return 0
      if (a.distance === null) return 1
      if (b.distance === null) return -1
      return a.distance - b.distance
    })
  }
  return rows
})

async function locate() {
  locating.value = true
  const loc = await getLocation()
  coords.value = loc
  locateText.value = loc ? '已按距离排序' : '点击定位排序'
  locating.value = false
  applySort()
}

function applySort() {
  const sorted = sortByDistance(list.value, coords.value)
  list.value = sorted.map((item) => ({ ...item, distanceText: formatDistance(item.distance) }))
}

async function load() {
  loading.value = true
  try {
    await userStore.ensureLogin()
    const data = await request({
      url: '/api/app/stations',
      data: keyword.value ? { keyword: keyword.value } : {}
    })
    const raw = data || []
    const sorted = sortByDistance(raw, coords.value)
    list.value = sorted.map((item) => ({ ...item, distanceText: formatDistance(item.distance) }))
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    loading.value = false
  }
}

function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(load, 350)
}

function clearKeyword() {
  keyword.value = ''
  load()
}

async function refreshLocation() {
  if (locating.value) return
  await locate()
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

function goDetail(id) {
  uni.navigateTo({ url: `/pages/station/detail?id=${id}` })
}

onShow(() => {
  load()
  if (!coords.value) locate()
})
</script>

<style lang="scss" scoped>
.search-box {
  display: flex;
  align-items: center;
  height: 80rpx;
  padding: 0 28rpx;
  background: #ffffff;
  border: 2rpx solid #edf0ee;
  border-radius: 999rpx;
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

.search-input {
  flex: 1;
  height: 80rpx;
  font-size: 26rpx;
}

.search-ph {
  color: #98a2b3;
}

.clear {
  font-size: 36rpx;
  color: #c6cdd5;
  padding: 0 8rpx;
}

.filters {
  display: flex;
  margin-top: 24rpx;
}

.filter {
  margin-right: 16rpx;
  padding: 12rpx 26rpx;
  border-radius: 999rpx;
  background: #ffffff;
  border: 2rpx solid #edf0ee;
  font-size: 24rpx;
  color: #475467;
}

.filter.on {
  background: #00a870;
  border-color: #00a870;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 12rpx 28rpx -16rpx rgba(0, 168, 112, 0.8);
}

.result-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 28rpx 4rpx 20rpx;
  font-size: 22rpx;
  color: #98a2b3;
}

.strong {
  color: #475467;
  font-weight: 600;
}

.locate-action {
  color: #00895c;
}

.station-card {
  padding: 24rpx;
  display: flex;
  align-items: center;
}

.thumb {
  width: 136rpx;
  height: 136rpx;
  flex: 0 0 136rpx;
  border-radius: 28rpx;
  margin-right: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 48rpx;
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
  font-size: 22rpx;
  color: #475467;
}

.st-idle {
  margin-right: 16rpx;
}

.idle-num {
  font-size: 28rpx;
  color: #00a870;
}

.st-charging {
  color: #b54708;
  margin-right: 12rpx;
}

.st-fault {
  color: #b42318;
}

.side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  height: 136rpx;
  margin-left: 12rpx;
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
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  border: 2rpx solid #edf0ee;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-mark {
  width: 20rpx;
  height: 20rpx;
  border-top: 4rpx solid #00a870;
  border-right: 4rpx solid #00a870;
  transform: rotate(45deg);
  margin-top: -4rpx;
}

.footer-note {
  margin-top: 36rpx;
  text-align: center;
  font-size: 20rpx;
  color: #c6cdd5;
}

.station-list-page {
  padding-bottom: calc(140rpx + env(safe-area-inset-bottom));
}
</style>
