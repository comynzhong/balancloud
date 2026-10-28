<template>
  <div class="app-container live-page">
    <section class="live-head">
      <div>
        <p class="kicker">OPERATIONS · LIVE CHARGING</p>
        <h2>
          充电实况
          <span class="count-pill">{{ filteredRows.length }} 路在充</span>
        </h2>
        <p class="desc">
          端口级实时监控：{{ intervalSeconds }} 秒轮询进行中订单与设备影子功率，曲线中的端口序列在本页按刷新节拍累积。
        </p>
      </div>
      <div class="head-right">
        <div class="seg">
          <button
            v-for="item in INTERVAL_OPTIONS"
            :key="item"
            type="button"
            :class="{ on: intervalSeconds === item }"
            @click="chooseInterval(item)"
          >{{ item }}s</button>
        </div>
        <el-button :type="paused ? 'primary' : 'default'" :icon="paused ? 'VideoPlay' : 'VideoPause'" @click="togglePause">
          {{ paused ? '继续刷新' : '暂停刷新' }}
        </el-button>
        <el-button icon="Refresh" :loading="loading" @click="refresh(true)">立即刷新</el-button>
        <div class="live-chip" :class="{ offline: !live || paused }">
          <span class="dot" />
          {{ paused ? '已暂停' : (live ? 'WebSocket 实时' : '定时轮询') }} · {{ countdown }}s · {{ lastRefresh || '等待数据' }}
        </div>
      </div>
    </section>

    <section class="panel">
      <el-form :inline="true" class="query-form">
        <el-form-item label="场站">
          <el-select v-model="stationId" placeholder="全部场站" clearable style="width: 180px" @change="onStationChange">
            <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="chargerId" placeholder="全部设备" clearable style="width: 190px" @change="renderAll">
            <el-option
              v-for="item in chargerOptions"
              :key="item.id"
              :label="`${item.deviceNo} ${item.name || ''}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="renderAll">
            <el-option label="充电中" value="CHARGING" />
            <el-option label="启动中" value="PENDING_START" />
            <el-option label="停止中" value="STOPPING" />
          </el-select>
        </el-form-item>
        <el-form-item label="端口状态">
          <el-select v-model="portStatusFilter" placeholder="全部端口" clearable style="width: 140px">
            <el-option label="充电中" value="CHARGING" />
            <el-option label="空闲" value="IDLE" />
            <el-option label="故障" value="FAULT" />
            <el-option label="离线" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="keyword"
            placeholder="订单号 / 用户 / 手机号 / 车牌 / 设备"
            clearable
            style="width: 250px"
            @keyup.enter="renderAll"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="refresh(true)">查询</el-button>
          <el-button icon="RefreshLeft" @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <div class="kpi-row">
      <div v-for="item in kpiCards" :key="item.label" class="kpi" :class="item.tone">
        <div class="kpi-label">{{ item.label }}</div>
        <div class="kpi-value">{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></div>
        <div class="kpi-note">{{ item.note }}</div>
      </div>
    </div>

    <el-row :gutter="14">
      <el-col :xs="24" :lg="16">
        <section class="panel">
          <div class="panel-head">
            <h3>实时功率曲线</h3>
            <div class="head-tools">
              <div class="legend">
                <span v-for="item in legendItems" :key="item.name" class="legend-item">
                  <i :style="{ background: item.color }" />{{ item.name }}
                </span>
              </div>
              <div class="seg">
                <button
                  v-for="item in WINDOW_OPTIONS"
                  :key="item"
                  type="button"
                  :class="{ on: powerWindow === item }"
                  @click="chooseWindow(item)"
                >{{ item }} 分钟</button>
              </div>
            </div>
          </div>
          <div ref="powerRef" class="chart chart-power" />
          <div class="chart-foot">
            <span>窗口峰值 <strong>{{ formatNumber(powerStats.peak, 2) }} kW</strong></span>
            <span>窗口均值 <strong>{{ formatNumber(powerStats.avg, 2) }} kW</strong></span>
            <span>当前 <strong>{{ formatNumber(powerStats.latest, 2) }} kW</strong></span>
            <span class="foot-tip">全站序列来自 Redis 功率栅格（10 秒采样）；端口序列由本页 {{ intervalSeconds }} 秒轮询累积</span>
          </div>
        </section>
      </el-col>

      <el-col :xs="24" :lg="8">
        <section class="panel panel-fill">
          <div class="panel-head">
            <h3>实时事件流</h3>
            <el-tag size="small" :type="events.length ? 'success' : 'info'" effect="plain">{{ events.length }} 条</el-tag>
          </div>
          <ul v-loading="eventLoading" class="event-list">
            <li v-for="(item, index) in events" :key="`${item.category}-${item.refNo}-${index}`" class="event-item">
              <span class="event-dot" :class="eventTone(item)" />
              <div class="event-main">
                <div class="event-title">
                  <strong>{{ eventTitle(item) }}</strong>
                  <span class="event-when">{{ formatTime(item.time) }}</span>
                </div>
                <div class="event-desc">
                  {{ item.deviceNo || '—' }}<template v-if="item.portNo"> · {{ item.portNo }} 号口</template>
                  <span v-if="item.detail" class="event-detail">{{ item.detail }}</span>
                </div>
              </div>
              <div v-if="item.category === 'ORDER'" class="event-value">
                {{ formatNumber(item.energyKwh, 3) }} 度
                <small>{{ formatMoney(item.totalFee) }}</small>
              </div>
            </li>
            <li v-if="!events.length" class="event-empty">暂无事件，启动充电或注入故障后会实时出现</li>
          </ul>
        </section>
      </el-col>
    </el-row>

    <section class="panel">
      <div class="panel-head">
        <h3>实时充电名单</h3>
        <span class="head-tip">已充时长按秒递增；进度取「时长上限」与「金额上限」中的较高者</span>
      </div>
      <el-table v-loading="loading" :data="pagedRows" row-key="id" empty-text="当前没有用户正在充电">
        <el-table-column label="订单号" min-width="176">
          <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
        </el-table-column>
        <el-table-column label="用户 / 车辆" min-width="186">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="30" :src="row.appUserAvatar || undefined">
                {{ (row.appUserNickname || '充').slice(0, 1) }}
              </el-avatar>
              <div>
                <div class="strong">{{ row.appUserNickname || '后台发起' }}</div>
                <div class="muted">
                  {{ row.appUserPhone || '无手机号' }}
                  <el-tag v-if="row.vehiclePlateNo" size="small" effect="plain" class="plate">{{ row.vehiclePlateNo }}</el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="设备 / 端口" min-width="150">
          <template #default="{ row }">
            <div class="strong">{{ row.deviceName || row.stationName || '—' }}</div>
            <div class="muted">{{ row.deviceNo }} · {{ row.portNo }} 号口</div>
          </template>
        </el-table-column>
        <el-table-column label="开始 / 已充时长" min-width="180">
          <template #default="{ row }">
            <div class="muted">{{ formatTime(row.startTime) }}</div>
            <div class="dur">{{ liveDuration(row) }}</div>
            <el-progress
              :percentage="progressPercent(row)"
              :stroke-width="5"
              :show-text="false"
              :color="progressColor(row)"
              class="prog"
            />
          </template>
        </el-table-column>
        <el-table-column label="实时功率" width="112" align="right">
          <template #default="{ row }">
            <span class="power">{{ row.currentPowerKw == null ? '—' : formatNumber(row.currentPowerKw, 2) }}</span>
            <small class="muted"> kW</small>
          </template>
        </el-table-column>
        <el-table-column label="已充电量" width="112" align="right">
          <template #default="{ row }">{{ formatNumber(row.energyKwh, 3) }} 度</template>
        </el-table-column>
        <el-table-column label="实时金额" width="132" align="right">
          <template #default="{ row }">
            <div class="strong fee">{{ formatMoney(row.totalFee) }}</div>
            <div class="muted">上限 {{ formatMoney(row.chargeLimitAmount) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="orderStatusType(row.status)" effect="plain">{{ orderStatusLabel(row.status) }}</el-tag>
            <div v-if="isNearLimit(row)" class="near">临近上限</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="152" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openOrder(row)">详情</el-button>
            <el-button
              v-if="row.status === 'CHARGING'"
              v-hasPermi="['charger:stop']"
              link
              type="danger"
              icon="SwitchButton"
              :loading="stoppingId === row.id"
              @click="handleStop(row)"
            >停止</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="PAGE_SIZE"
        :total="filteredRows.length"
        layout="total, prev, pager, next"
        class="pager"
        small
      />
    </section>

    <section v-if="canQueryCharger" class="panel">
      <div class="panel-head">
        <h3>端口实时矩阵</h3>
        <span class="head-tip">共 {{ matrix.length }} 台设备 · 悬停端口查看用户与实时功率</span>
      </div>
      <div v-loading="matrixLoading" class="matrix">
        <div v-for="group in matrix" :key="group.charger.id" class="device-block">
          <div class="device-head">
            <div>
              <div class="strong">{{ group.charger.deviceNo }}</div>
              <div class="muted">{{ group.charger.name }} · {{ group.charger.stationName || '—' }}</div>
            </div>
            <div class="device-stat">
              <el-tag size="small" :type="group.charger.onlineStatus === 'ONLINE' ? 'success' : 'info'" effect="plain">
                {{ group.charger.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
              </el-tag>
              <span class="power">{{ formatNumber(group.charger.currentPowerKw, 2) }} kW</span>
            </div>
          </div>
          <div class="ports">
            <el-tooltip
              v-for="port in visiblePorts(group.ports)"
              :key="`${group.charger.id}-${port.portNo}`"
              :content="portTooltip(port)"
              placement="top"
            >
              <div class="port" :class="portClass(port)">
                <span class="port-no">{{ port.portNo }}</span>
                <span class="port-power">{{ port.powerKw == null ? '—' : formatNumber(port.powerKw, 1) }}</span>
                <span class="port-user">{{ port.appUserNickname || portStatusLabel(port.status) }}</span>
              </div>
            </el-tooltip>
            <div v-if="!visiblePorts(group.ports).length" class="port-empty">无匹配端口</div>
          </div>
        </div>
        <div v-if="!matrix.length && !matrixLoading" class="event-empty">没有可展示的设备端口</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { listActiveOrders } from '@/api/balan/order'
import { stopCharging } from '@/api/balan/charging'
import { getChargerPorts, listCharger } from '@/api/balan/charger'
import { getStationOptions } from '@/api/balan/station'
import { getPowerTrend, listDashboardEvents } from '@/api/balan/dashboard'
import { subscribeEventStream } from '@/utils/eventStream'
import { checkPermi } from '@/utils/permission'
import {
  alertLevelLabel,
  alertStatusLabel,
  alertTypeLabel,
  formatDuration,
  formatMoney,
  formatNumber,
  formatTime,
  orderStatusLabel,
  orderStatusType,
  portStatusLabel,
  stopReasonLabel
} from '@/utils/balan'

defineOptions({ name: 'BalanChargingLive' })

const INTERVAL_OPTIONS = [5, 10, 30]
const WINDOW_OPTIONS = [10, 30, 60]
const PAGE_SIZE = 10
const EVENT_LIMIT = 14
const MATRIX_LIMIT = 12
const PORT_PALETTE = ['#00a870', '#528bfd', '#f79009', '#8b5cf6', '#e37fdf', '#22b8cf']

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const matrixLoading = ref(false)
const eventLoading = ref(false)
const rows = ref([])
const stationSeries = ref([])
const events = ref([])
const chargers = ref([])
const matrix = ref([])
const stationOptions = ref([])
const stationId = ref(route.query.stationId ? Number(route.query.stationId) : undefined)
const chargerId = ref(undefined)
const statusFilter = ref(undefined)
const portStatusFilter = ref(undefined)
const keyword = ref('')
const intervalSeconds = ref(5)
const powerWindow = ref(10)
const paused = ref(false)
const countdown = ref(5)
const live = ref(false)
const lastRefresh = ref('')
const pageNum = ref(1)
const stoppingId = ref(null)
const nowTick = ref(Date.now())

const powerRef = ref()

let powerChart = null
let ticker = null
let secondTicker = null
let debounceTimer = null
let unsubscribe = null
const PORT_MAX_POINTS = WINDOW_OPTIONS[WINDOW_OPTIONS.length - 1] * 12
const portSeries = ref({})

const canQueryCharger = computed(() => checkPermi(['charger:query']))

const chargerOptions = computed(() => {
  const list = chargers.value
  return stationId.value ? list.filter(item => item.stationId === stationId.value) : list
})

const filteredRows = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return rows.value.filter(row => {
    if (chargerId.value && row.chargerId !== chargerId.value) {
      return false
    }
    if (statusFilter.value && row.status !== statusFilter.value) {
      return false
    }
    if (!text) {
      return true
    }
    return [row.orderNo, row.appUserNickname, row.appUserPhone, row.vehiclePlateNo, row.deviceNo, row.deviceName, row.stationName]
      .some(value => value && String(value).toLowerCase().includes(text))
  })
})

const pagedRows = computed(() => {
  const start = (pageNum.value - 1) * PAGE_SIZE
  return filteredRows.value.slice(start, start + PAGE_SIZE)
})

const kpiCards = computed(() => {
  const list = filteredRows.value
  const power = list.reduce((sum, row) => sum + Number(row.currentPowerKw || 0), 0)
  const energy = list.reduce((sum, row) => sum + Number(row.energyKwh || 0), 0)
  const fee = list.reduce((sum, row) => sum + Number(row.totalFee || 0), 0)
  const users = new Set(list.map(row => row.appUserId).filter(Boolean)).size
  const longest = list.reduce((max, row) => Math.max(max, liveSeconds(row)), 0)
  return [
    { label: '在充订单', value: list.length, unit: '单', note: `${users} 位用户`, tone: 'brand' },
    { label: '实时总功率', value: formatNumber(power, 2), unit: 'kW', note: '设备影子最新上报' },
    { label: '端口均功率', value: formatNumber(list.length ? power / list.length : 0, 2), unit: 'kW', note: '按在充端口折算' },
    { label: '已充电量', value: formatNumber(energy, 2), unit: '度', note: `最长已充 ${formatDuration(longest)}` },
    { label: '实时金额', value: formatMoney(fee), note: '按分时计费累计，不含退款', tone: 'warm' }
  ]
})

const activePorts = computed(() => {
  const cutoff = Date.now() - powerWindow.value * 60000
  return Object.entries(portSeries.value)
    .map(([key, points]) => ({ key, points, power: points[points.length - 1][0] >= cutoff
      ? points[points.length - 1][1] : -1 }))
    .filter(entry => entry.power >= 0)
    .sort((a, b) => b.power - a.power)
})

const legendItems = computed(() => {
  const items = [{ name: '全站功率', color: PORT_PALETTE[0] }]
  activePorts.value.slice(0, 4).forEach((entry, index) => {
    items.push({ name: entry.key, color: PORT_PALETTE[(index + 1) % PORT_PALETTE.length] })
  })
  return items
})

const powerStats = computed(() => {
  const cutoff = Date.now() - powerWindow.value * 60000
  const values = stationSeries.value
    .filter(point => point.ts * 1000 >= cutoff)
    .map(point => Number(point.powerKw || 0))
  if (!values.length) {
    return { peak: 0, avg: 0, latest: 0 }
  }
  return {
    peak: Math.max(...values),
    avg: values.reduce((sum, value) => sum + value, 0) / values.length,
    latest: values[values.length - 1]
  }
})

function liveSeconds(row) {
  const base = Number(row.durationSeconds || 0)
  if (!row.startTime) {
    return base
  }
  const fromStart = (nowTick.value - new Date(row.startTime).getTime()) / 1000
  return Math.max(base, Math.max(0, Math.floor(fromStart)))
}

function liveDuration(row) {
  return formatDuration(liveSeconds(row))
}

function progressPercent(row) {
  const timeLimit = Number(row.maxDurationMinutes || 0) * 60
  const feeLimit = Number(row.chargeLimitAmount || 0)
  const byTime = timeLimit ? (liveSeconds(row) / timeLimit) * 100 : 0
  const byFee = feeLimit ? (Number(row.totalFee || 0) / feeLimit) * 100 : 0
  return Math.min(100, Math.max(0, Number(Math.max(byTime, byFee).toFixed(0))))
}

function progressColor(row) {
  const percent = progressPercent(row)
  if (percent >= 90) {
    return '#f04438'
  }
  if (percent >= 70) {
    return '#f79009'
  }
  return '#00a870'
}

function isNearLimit(row) {
  return progressPercent(row) >= 80
}

function collectPortSeries(list) {
  const ts = Date.now()
  const staleBefore = ts - PORT_MAX_POINTS * 5000
  const next = { ...portSeries.value }
  const seen = new Set()
  list.forEach(row => {
    if (row.currentPowerKw == null) {
      return
    }
    const key = `${row.deviceNo || row.chargerId}#${row.portNo}`
    seen.add(key)
    const points = (next[key] || []).concat([[ts, Number(row.currentPowerKw || 0)]])
    next[key] = points.slice(-PORT_MAX_POINTS)
  })
  Object.keys(next).forEach(key => {
    const last = next[key][next[key].length - 1]
    if (!seen.has(key) && last[0] < staleBefore) {
      delete next[key]
    }
  })
  portSeries.value = next
}

function ensureChart(chart, el) {
  // 容器宽高为 0 时 echarts 会画出空白图并告警，跳过本次渲染，下一轮刷新会重建
  if (!el || !el.clientWidth || !el.clientHeight) {
    return null
  }
  if (chart && chart.getDom() === el) {
    return chart
  }
  chart?.dispose()
  return echarts.init(el)
}

function renderPowerChart() {
  powerChart = ensureChart(powerChart, powerRef.value)
  if (!powerChart) {
    return
  }
  const cutoff = Date.now() - powerWindow.value * 60000
  const series = [{
    name: '全站功率',
    type: 'line',
    smooth: true,
    showSymbol: false,
    data: stationSeries.value
      .filter(point => point.ts * 1000 >= cutoff)
      .map(point => [point.ts * 1000, Number(point.powerKw || 0)]),
    lineStyle: { color: PORT_PALETTE[0], width: 2.4 },
    itemStyle: { color: PORT_PALETTE[0] },
    areaStyle: {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(0, 200, 138, 0.34)' },
        { offset: 1, color: 'rgba(0, 168, 112, 0.02)' }
      ])
    }
  }]
  activePorts.value.slice(0, 4).forEach((entry, index) => {
    series.push({
      name: entry.key,
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: entry.points.filter(point => point[0] >= cutoff),
      lineStyle: { color: PORT_PALETTE[(index + 1) % PORT_PALETTE.length], width: 1.8 },
      itemStyle: { color: PORT_PALETTE[(index + 1) % PORT_PALETTE.length] }
    })
  })
  powerChart.setOption({
    tooltip: {
      trigger: 'axis',
      valueFormatter: value => `${formatNumber(value, 2)} kW`
    },
    grid: { left: 54, right: 22, top: 18, bottom: 30 },
    xAxis: {
      type: 'time',
      axisLine: { lineStyle: { color: '#e3eae6' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#899a92',
        formatter: value => new Date(value).toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
      }
    },
    yAxis: {
      type: 'value',
      name: 'kW',
      axisLabel: { color: '#899a92' },
      splitLine: { lineStyle: { color: '#eef2f0' } }
    },
    series
  }, true)
  powerChart.resize()
}

function visiblePorts(ports) {
  if (!portStatusFilter.value) {
    return ports
  }
  return ports.filter(port => port.status === portStatusFilter.value)
}

function portClass(port) {
  return `s-${(port.status || 'IDLE').toLowerCase()}`
}

function portTooltip(port) {
  const parts = [`端口 ${port.portNo} · ${portStatusLabel(port.status)}`]
  if (port.appUserNickname) {
    parts.push(port.appUserNickname)
  }
  if (port.vehiclePlateNo) {
    parts.push(port.vehiclePlateNo)
  }
  parts.push(`${formatNumber(port.powerKw, 2)} kW`)
  if (port.temperature != null) {
    parts.push(`${formatNumber(port.temperature, 1)} ℃`)
  }
  return parts.join(' · ')
}

function loadOrders() {
  loading.value = true
  const params = {}
  if (stationId.value) {
    params.stationId = stationId.value
  }
  return listActiveOrders(params).then(res => {
    rows.value = res.data || []
    lastRefresh.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    collectPortSeries(rows.value)
    renderPowerChart()
  }).finally(() => {
    loading.value = false
  })
}

function loadPowerTrend() {
  const params = { minutes: powerWindow.value }
  if (stationId.value) {
    params.stationId = stationId.value
  }
  return getPowerTrend(params).then(res => {
    stationSeries.value = res.data || []
    renderPowerChart()
  })
}

function loadChargers() {
  return listCharger({ pageNum: 1, pageSize: 100 }).then(res => {
    chargers.value = res.rows || []
  })
}

function loadMatrix() {
  if (!canQueryCharger.value) {
    return Promise.resolve()
  }
  const target = chargerOptions.value.filter(item => !chargerId.value || item.id === chargerId.value).slice(0, MATRIX_LIMIT)
  if (!target.length) {
    matrix.value = []
    return Promise.resolve()
  }
  matrixLoading.value = true
  return Promise.all(target.map(charger => getChargerPorts(charger.id)
    .then(res => ({ charger, ports: res.data || [] }))
    .catch(() => ({ charger, ports: [] }))))
    .then(list => {
      matrix.value = list
    }).finally(() => {
      matrixLoading.value = false
    })
}

function loadEvents() {
  eventLoading.value = true
  const params = { limit: 30 }
  if (stationId.value) {
    params.stationId = stationId.value
  }
  return listDashboardEvents(params).then(res => {
    events.value = (res.data || []).slice(0, EVENT_LIMIT)
  }).finally(() => {
    eventLoading.value = false
  })
}

function renderAll() {
  pageNum.value = 1
  renderPowerChart()
}

function refresh(soon = false) {
  if (soon) {
    countdown.value = intervalSeconds.value
  }
  return Promise.all([loadOrders(), loadPowerTrend(), loadMatrix(), loadEvents()])
}

function chooseInterval(value) {
  intervalSeconds.value = value
  countdown.value = value
}

function chooseWindow(value) {
  powerWindow.value = value
  loadPowerTrend()
}

function togglePause() {
  paused.value = !paused.value
  if (!paused.value) {
    countdown.value = intervalSeconds.value
    refresh()
  }
}

function onStationChange() {
  chargerId.value = undefined
  refresh(true)
}

function resetFilters() {
  stationId.value = undefined
  chargerId.value = undefined
  statusFilter.value = undefined
  portStatusFilter.value = undefined
  keyword.value = ''
  refresh(true)
}

function openOrder(row) {
  router.push({ path: '/order/list', query: { keyword: row.orderNo } })
}

function handleStop(row) {
  ElMessageBox.confirm(
    `确认停止 ${row.deviceNo} ${row.portNo} 号口（用户 ${row.appUserNickname || '-'}）的充电吗？`,
    '停止充电',
    { type: 'warning', confirmButtonText: '停止充电', cancelButtonText: '取消' }
  ).then(() => {
    stoppingId.value = row.id
    return stopCharging(row.chargerId, row.portNo).then(() => {
      ElMessage.success('停止指令已下发')
      refresh()
    })
  }).catch(() => {}).finally(() => {
    stoppingId.value = null
  })
}

function eventTitle(item) {
  if (item.category === 'ORDER') {
    return orderStatusLabel(item.code || item.status)
  }
  const level = item.level ? ` · ${alertLevelLabel(item.level)}` : ''
  return `${alertTypeLabel(item.code)}${level} · ${alertStatusLabel(item.status)}`
}

function eventTone(item) {
  if (item.category === 'ORDER') {
    return item.status === 'FAILED' || item.status === 'ABNORMAL' ? 'danger' : 'order'
  }
  if (item.status === 'RESOLVED') {
    return 'resolved'
  }
  return item.level === 'CRITICAL' ? 'danger' : 'warning'
}

function prependEvent(item) {
  events.value = [item, ...events.value].slice(0, EVENT_LIMIT)
}

function alertToEvent(alert) {
  return {
    category: 'ALERT',
    code: alert.alertType,
    level: alert.alertLevel,
    status: alert.status,
    refNo: String(alert.id),
    stationId: alert.stationId,
    deviceNo: alert.deviceNo,
    portNo: alert.portNo,
    detail: alert.content,
    time: alert.resolvedAt || alert.lastOccurredAt || alert.occurredAt
  }
}

function orderToEvent(order) {
  return {
    category: 'ORDER',
    code: order.status,
    status: order.status,
    refNo: order.orderNo,
    stationId: order.stationId,
    deviceNo: order.deviceNo,
    portNo: order.portNo,
    detail: order.stopReason ? stopReasonLabel(order.stopReason) : '',
    energyKwh: order.energyKwh,
    totalFee: order.totalFee,
    time: order.endTime || new Date()
  }
}

function scheduleRefresh(needsEvents) {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = setTimeout(() => {
    debounceTimer = null
    loadOrders()
    loadPowerTrend()
    if (needsEvents) {
      loadEvents()
    }
  }, 2000)
}

function tick() {
  if (paused.value || document.hidden) {
    return
  }
  countdown.value -= 1
  if (countdown.value <= 0) {
    countdown.value = intervalSeconds.value
    refresh()
  }
}

function handleResize() {
  powerChart?.resize()
}

onMounted(() => {
  getStationOptions().then(res => {
    stationOptions.value = res.data || []
  })
  loadChargers()
  refresh().then(() => nextTick(handleResize))
  ticker = setInterval(tick, 1000)
  secondTicker = setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
  window.addEventListener('resize', handleResize)
  unsubscribe = subscribeEventStream({
    power: () => loadPowerTrend(),
    alert: alert => {
      prependEvent(alertToEvent(alert))
      scheduleRefresh(true)
    },
    order: order => {
      prependEvent(orderToEvent(order))
      scheduleRefresh(false)
    },
    device: () => {
      loadChargers()
      loadMatrix()
    }
  }, value => {
    live.value = value
  })
})

onBeforeUnmount(() => {
  unsubscribe?.()
  if (ticker) {
    clearInterval(ticker)
  }
  if (secondTicker) {
    clearInterval(secondTicker)
  }
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  window.removeEventListener('resize', handleResize)
  powerChart?.dispose()
  powerChart = null
})
</script>

<style scoped lang="scss">
.live-page {
  .live-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 18px;
    margin-bottom: 14px;

    .kicker {
      margin: 0 0 6px;
      color: var(--bc-brand);
      font-size: 11px;
      letter-spacing: 2px;
    }

    h2 {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 0;
      font-size: 23px;
      color: var(--bc-text);
    }

    .desc {
      margin: 8px 0 0;
      max-width: 660px;
      color: var(--bc-text-muted);
      font-size: 13px;
    }
  }

  .count-pill {
    padding: 3px 10px;
    border-radius: 999px;
    background: var(--el-color-primary-light-9);
    color: var(--bc-brand-deep);
    font-size: 12px;
    font-weight: 500;
  }

  .head-right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .seg {
    display: inline-flex;
    padding: 2px;
    border: 1px solid var(--bc-border);
    border-radius: 8px;
    background: var(--bc-surface-inset);

    button {
      padding: 4px 11px;
      border: none;
      border-radius: 6px;
      background: transparent;
      color: var(--bc-text-muted);
      font-size: 12px;
      cursor: pointer;
      transition: all 0.16s ease;

      &:hover {
        color: var(--bc-brand-deep);
      }

      &.on {
        background: var(--bc-brand);
        color: #fff;
        box-shadow: 0 3px 9px rgba(0, 168, 112, 0.26);
      }
    }
  }

  .live-chip {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 5px 13px;
    border: 1px solid rgba(0, 168, 112, 0.32);
    border-radius: 999px;
    background: var(--el-color-primary-light-9);
    color: var(--bc-brand-deep);
    font-size: 12px;

    .dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--bc-brand);
      animation: live-pulse 1.6s ease-in-out infinite;
    }

    &.offline {
      border-color: #e3eae6;
      background: #f4f7f5;
      color: var(--bc-text-muted);

      .dot {
        background: #a8b8b0;
        animation: none;
      }
    }
  }

  .panel {
    margin-bottom: 14px;
    padding: 15px 18px;
    border: 1px solid var(--bc-border);
    border-radius: 12px;
    background: var(--bc-surface);
    box-shadow: 0 4px 14px rgba(11, 31, 26, 0.04);

    &.panel-fill {
      display: flex;
      flex-direction: column;
      height: calc(100% - 14px);
    }
  }

  .query-form {
    margin-bottom: -18px;
  }

  .panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
    flex-wrap: wrap;

    h3 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      color: var(--bc-text);

      &::before {
        content: '';
        width: 3px;
        height: 14px;
        border-radius: 2px;
        background: linear-gradient(#00c88a, #00a870);
      }
    }
  }

  .head-tools {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .head-tip {
    color: #93a49c;
    font-size: 12px;
  }

  .legend {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    .legend-item {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      color: var(--bc-text-muted);
      font-size: 12px;

      i {
        width: 10px;
        height: 3px;
        border-radius: 2px;
      }
    }
  }

  .kpi-row {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 14px;
  }

  .kpi {
    padding: 14px 16px;
    border: 1px solid var(--bc-border);
    border-radius: 12px;
    background: var(--bc-surface);
    box-shadow: 0 4px 14px rgba(11, 31, 26, 0.04);

    .kpi-label {
      color: var(--bc-text-muted);
      font-size: 12px;
    }

    .kpi-value {
      margin: 8px 0 4px;
      font-size: 24px;
      font-weight: 700;
      color: var(--bc-text);

      small {
        margin-left: 4px;
        font-size: 12px;
        font-weight: 400;
        color: #93a49c;
      }
    }

    .kpi-note {
      color: #93a49c;
      font-size: 12px;
    }

    &.brand .kpi-value {
      color: var(--bc-brand-deep);
    }

    &.warm .kpi-value {
      color: #c2410c;
    }
  }

  .chart {
    width: 100%;
  }

  .chart-power {
    height: 288px;
  }

  .chart-foot {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
    margin-top: 8px;
    padding-top: 10px;
    border-top: 1px dashed #e6ece8;
    color: var(--bc-text-muted);
    font-size: 12px;

    strong {
      color: var(--bc-text);
      font-weight: 600;
    }

    .foot-tip {
      min-width: 0;
      margin-left: auto;
      color: #a2b1a9;
      text-align: right;
    }
  }

  .mono {
    font-family: 'JetBrains Mono', 'Cascadia Mono', Consolas, monospace;
    font-size: 12px;
    color: #4a5b53;
  }

  .strong {
    color: var(--bc-text);
    font-weight: 600;
  }

  .muted {
    color: #93a49c;
    font-size: 12px;
  }

  .user-cell {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .plate {
    margin-left: 4px;
  }

  .dur {
    color: var(--bc-text);
    font-size: 13px;
    font-weight: 600;
  }

  .prog {
    margin-top: 4px;
  }

  .power {
    color: var(--bc-brand-deep);
    font-weight: 600;
  }

  .fee {
    color: var(--bc-text);
  }

  .near {
    margin-top: 2px;
    color: var(--bc-danger);
    font-size: 11px;
  }

  .pager {
    margin-top: 12px;
    justify-content: flex-end;
  }

  .event-list {
    flex: 1 1 0;
    min-height: 120px;
    margin: 0;
    padding: 0 4px 0 0;
    list-style: none;
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: 3px;
      background: #d6e0da;
    }
  }

  .event-item {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 9px 0;
    border-bottom: 1px dashed #e6ece8;

    &:last-child {
      border-bottom: none;
    }
  }

  .event-dot {
    flex: none;
    width: 8px;
    height: 8px;
    margin-top: 6px;
    border-radius: 50%;
    background: #a8b8b0;

    &.danger { background: var(--bc-danger); }
    &.warning { background: var(--bc-amber); }
    &.resolved { background: var(--bc-brand); }
    &.order { background: var(--bc-blue); }
  }

  .event-main {
    flex: 1;
    min-width: 0;
  }

  .event-title {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;

    strong {
      min-width: 0;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      color: var(--bc-text);
      font-size: 13px;
      font-weight: 600;
    }
  }

  .event-when {
    flex: none;
    color: #a2b1a9;
    font-size: 11px;
    white-space: nowrap;
  }

  .event-desc {
    margin-top: 2px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    color: var(--bc-text-muted);
    font-size: 12px;
  }

  .event-detail {
    margin-left: 6px;
    color: #a2b1a9;
  }

  .event-value {
    flex: none;
    padding-left: 6px;
    text-align: right;
    white-space: nowrap;
    color: var(--bc-text);
    font-size: 12px;

    small {
      display: block;
      color: var(--bc-brand-deep);
      font-weight: 600;
    }
  }

  .event-empty {
    padding: 22px 0;
    color: #a2b1a9;
    text-align: center;
    font-size: 13px;
  }

  .matrix {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 12px;
  }

  .device-block {
    padding: 12px 14px;
    border: 1px solid #e9efeb;
    border-radius: 10px;
    background: var(--bc-surface-inset);
  }

  .device-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;

    .device-stat {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 5px;
      font-size: 12px;
    }
  }

  .ports {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .port {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1px;
    min-width: 62px;
    padding: 7px 8px;
    border: 1px solid #e2e9e4;
    border-radius: 9px;
    background: #fff;
    cursor: default;
    transition: transform 0.15s ease, box-shadow 0.15s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(11, 31, 26, 0.1);
    }

    .port-no {
      font-size: 12px;
      font-weight: 700;
      color: var(--bc-text);
    }

    .port-power {
      font-size: 12px;
      color: var(--bc-text-muted);
    }

    .port-user {
      max-width: 66px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 11px;
      color: #a2b1a9;
    }

    &.s-charging {
      border-color: rgba(0, 168, 112, 0.4);
      background: var(--el-color-primary-light-9);

      .port-power { color: var(--bc-brand-deep); }
    }

    &.s-fault {
      border-color: rgba(240, 68, 56, 0.4);
      background: #feecec;

      .port-power { color: var(--bc-danger); }
    }

    &.s-offline {
      background: #f2f4f3;
      opacity: 0.72;
    }
  }

  .port-empty {
    padding: 8px 0;
    color: #a2b1a9;
    font-size: 12px;
  }

  @keyframes live-pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.32; transform: scale(0.78); }
  }

  @media (max-width: 1200px) {
    .kpi-row {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .live-head {
      flex-direction: column;
      align-items: flex-start;
    }

    .head-right {
      justify-content: flex-start;
    }
  }

  @media (max-width: 768px) {
    .kpi-row {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
}
</style>
