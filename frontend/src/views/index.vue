<template>
  <div class="app-container home">
    <section class="band">
      <svg class="band-rings" viewBox="0 0 320 200" aria-hidden="true">
        <circle cx="250" cy="40" r="86" />
        <circle cx="250" cy="40" r="58" />
        <circle cx="250" cy="40" r="30" />
      </svg>

      <div class="band-main">
        <p class="band-k">TODAY · 今日经营</p>
        <div class="band-big">
          <strong>{{ formatMoney(summary.todayRevenue) }}</strong>
          <small>充电营收</small>
          <span v-if="revenueDelta" class="band-delta" :class="revenueDelta.up ? 'up' : 'down'">{{ revenueDelta.text }}</span>
        </div>
        <p class="band-sub">
          今日充电 {{ formatNumber(summary.todayEnergyKwh, 2) }} 度 ·
          订单 {{ summary.todayOrderCount || 0 }} 笔 ·
          退款 {{ summary.todayRefundCount || 0 }} 笔 {{ formatMoney(summary.todayRefundAmount) }}
        </p>
      </div>

      <div class="band-stats">
        <div v-for="item in bandStats" :key="item.label" class="band-stat">
          <span class="bs-label">{{ item.label }}</span>
          <span class="bs-value">{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></span>
        </div>
      </div>

      <div class="band-side">
        <el-select v-model="stationId" placeholder="全部场站" clearable size="small" class="station-select" @change="reloadAll">
          <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <div class="live-chip" :class="{ offline: !live }">
          <span class="dot" />
          {{ live ? 'WebSocket 实时' : '定时轮询' }} · {{ countdown }}s 后刷新
        </div>
        <el-button v-if="canLive" link class="band-link" @click="goLive()">进入充电实况 →</el-button>
      </div>
    </section>

    <div class="kpi-row">
      <article
        v-for="card in assetCards"
        :key="card.label"
        class="kpi"
        :class="[card.tone, { clickable: card.link }]"
        @click="openCard(card.link)"
      >
        <div class="ic"><svg-icon :icon-class="card.icon" /></div>
        <div class="kpi-body">
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value">{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></div>
          <div class="kpi-note">{{ card.note }}</div>
        </div>
      </article>
    </div>

    <div class="kpi-row">
      <article
        v-for="card in periodCards"
        :key="card.key"
        class="kpi block clickable"
        :class="{ active: period === card.key && card.key !== 'energy' }"
        @click="pickPeriod(card.key)"
      >
        <div class="kpi-head">
          <span class="kpi-label">{{ card.label }}</span>
          <span v-if="card.delta" class="delta" :class="card.delta.up ? 'up' : 'down'">{{ card.delta.text }}</span>
        </div>
        <div class="kpi-value">{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></div>
        <div class="kpi-note">{{ card.note }}</div>
      </article>
    </div>

    <section class="panel">
      <div class="panel-head">
        <h3>{{ periodLabel }}场站经营</h3>
        <el-radio-group v-model="period" size="small">
          <el-radio-button label="today">今日</el-radio-button>
          <el-radio-button label="month">本月</el-radio-button>
          <el-radio-button label="year">今年</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tile-row">
        <div v-for="tile in tiles" :key="tile.label" class="tile">
          <div class="tile-label">{{ tile.label }}</div>
          <div class="tile-value">{{ tile.value }}<small v-if="tile.unit">{{ tile.unit }}</small></div>
          <div class="tile-note">{{ tile.note }}</div>
        </div>
      </div>
    </section>

    <el-row :gutter="14">
      <el-col :xs="24" :lg="15">
        <section class="panel">
          <div class="panel-head">
            <h3>全站经营趋势</h3>
            <el-radio-group v-model="trendRange" size="small" @change="loadTrend">
              <el-radio-button label="7d">近 7 天</el-radio-button>
              <el-radio-button label="30d">近 30 天</el-radio-button>
              <el-radio-button label="12m">近 12 月</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="trendRef" class="chart chart-box" />
          <p class="panel-note">按订单开始时间统计，金额口径不含已退款订单。</p>
        </section>
      </el-col>

      <el-col :xs="24" :lg="9">
        <section class="panel">
          <div class="panel-head">
            <h3>告警类型分布</h3>
            <el-tag size="small" :type="summary.openAlertCount ? 'danger' : 'success'" effect="plain">
              未闭环 {{ summary.openAlertCount || 0 }}
            </el-tag>
          </div>
          <div ref="alertRef" class="chart chart-box" />
        </section>
      </el-col>
    </el-row>

    <section class="panel">
      <div class="panel-head">
        <div class="panel-title">
          <h3>{{ detailMode === 'station' ? '场站明细' : '分组明细' }}</h3>
          <el-radio-group v-model="detailMode" size="small" @change="loadDetail">
            <el-radio-button label="station">按场站</el-radio-button>
            <el-radio-button label="group">按分组</el-radio-button>
          </el-radio-group>
        </div>
        <el-button link type="primary" icon="Refresh" @click="reloadAll">刷新</el-button>
      </div>
      <el-table v-if="detailMode === 'station'" v-loading="tableLoading" :data="stationRows" size="small" class="station-table">
        <el-table-column label="场站" min-width="150">
          <template #default="scope">
            <div class="st-name">{{ scope.row.stationName }}</div>
            <div class="st-sub">{{ scope.row.todayOrderCount || 0 }} 笔订单</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="88" align="center">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="light">
              {{ scope.row.status === 'ACTIVE' ? '运营中' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="设备 / 端口" min-width="140">
          <template #default="scope">
            <div>{{ scope.row.deviceCount || 0 }} 台 · 在线 {{ scope.row.onlineDeviceCount || 0 }}</div>
            <div class="st-sub">
              端口 {{ scope.row.portCount || 0 }} · 在充 {{ scope.row.chargingPortCount || 0 }}
              <span v-if="scope.row.faultPortCount" class="st-fault">· 故障 {{ scope.row.faultPortCount }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="实时功率" min-width="120">
          <template #default="scope">
            <div class="st-power">{{ formatNumber(scope.row.currentPowerKw, 2) }} kW</div>
            <div class="st-sub">额定 {{ formatNumber(scope.row.capacityKw, 1) }} kW</div>
          </template>
        </el-table-column>
        <el-table-column label="今日电量" min-width="104">
          <template #default="scope">{{ formatNumber(scope.row.todayEnergyKwh, 2) }} 度</template>
        </el-table-column>
        <el-table-column label="今日营收" min-width="104">
          <template #default="scope"><span class="st-fee">{{ formatMoney(scope.row.todayRevenue) }}</span></template>
        </el-table-column>
        <el-table-column label="未闭环告警" width="106" align="center">
          <template #default="scope">
            <span :class="{ 'st-fault': scope.row.openAlertCount }">{{ scope.row.openAlertCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canLive" label="操作" width="90" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="goLive(scope.row.stationId)">实况</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-table v-else v-loading="groupLoading" :data="groupRows" size="small" class="station-table">
        <el-table-column label="分组" min-width="150">
          <template #default="scope">
            <el-tag v-if="scope.row.groupName !== '未分组'" size="small" effect="plain">{{ scope.row.groupName }}</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">未分组</el-tag>
            <div class="st-sub">{{ scope.row.deviceCount || 0 }} 台设备</div>
          </template>
        </el-table-column>
        <el-table-column label="在线设备" min-width="120">
          <template #default="scope">
            <div>{{ scope.row.onlineDeviceCount || 0 }} 台</div>
            <div class="st-sub">离线 {{ (scope.row.deviceCount || 0) - (scope.row.onlineDeviceCount || 0) }} 台</div>
          </template>
        </el-table-column>
        <el-table-column label="端口 / 在充" min-width="130">
          <template #default="scope">
            <div>端口 {{ scope.row.portCount || 0 }}</div>
            <div class="st-sub">在充 {{ scope.row.chargingPortCount || 0 }}</div>
          </template>
        </el-table-column>
        <el-table-column label="故障端口" width="100" align="center">
          <template #default="scope">
            <span :class="{ 'st-fault': scope.row.faultPortCount }">{{ scope.row.faultPortCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="充电中占比" min-width="160">
          <template #default="scope">
            <el-progress :percentage="portBusyRate(scope.row)" :stroke-width="8" />
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getAlertStats, getBusinessTrend, getDashboardSummary, getGroupOverview, getStationOverview } from '@/api/balan/dashboard'
import { getStationOptions } from '@/api/balan/station'
import { subscribeEventStream } from '@/utils/eventStream'
import { checkPermi } from '@/utils/permission'
import { alertTypeLabel, formatMoney, formatNumber } from '@/utils/balan'

defineOptions({ name: 'Index' })

const REFRESH_SECONDS = 5
const LIVE_DEBOUNCE = 2000
const PERIOD_LABEL = { today: '今日', month: '本月', year: '今年' }

const router = useRouter()

const summary = ref({})
const stationRows = ref([])
const groupRows = ref([])
const detailMode = ref('station')
const stationOptions = ref([])
const stationId = ref(undefined)
const period = ref('today')
const trendRange = ref('7d')
const live = ref(false)
const countdown = ref(REFRESH_SECONDS)
const tableLoading = ref(true)
const groupLoading = ref(false)

const trendRef = ref()
const alertRef = ref()

let trendChart = null
let alertChart = null
let ticker = null
let debounceTimer = null
let unsubscribe = null

const canLive = computed(() => checkPermi(['order:list']))
const periodLabel = computed(() => PERIOD_LABEL[period.value])

function delta(current, previous) {
  const base = Number(previous || 0)
  const value = Number(current || 0)
  if (!base) {
    return null
  }
  const rate = ((value - base) / base) * 100
  return { up: rate >= 0, text: `较昨日 ${rate >= 0 ? '▲' : '▼'} ${Math.abs(rate).toFixed(1)}%` }
}

function share(part, total) {
  const base = Number(total || 0)
  if (!base) {
    return '占比 —'
  }
  return `占比 ${((Number(part || 0) / base) * 100).toFixed(1)}%`
}

function portBusyRate(row) {
  const total = Number(row?.portCount || 0)
  if (!total) {
    return 0
  }
  return Math.min(100, Math.round((Number(row.chargingPortCount || 0) / total) * 100))
}

function perUnit(amount, count) {
  const base = Number(count || 0)
  if (!base) {
    return '0.00'
  }
  return (Number(amount || 0) / base).toFixed(2)
}

function money(value) {
  return formatMoney(value)
}

function bucket(key) {
  const data = summary.value || {}
  if (key === 'month') {
    return {
      orderCount: data.monthOrderCount,
      energyKwh: data.monthEnergyKwh,
      revenue: data.monthRevenue,
      energyFee: data.monthEnergyFee,
      serviceFee: data.monthServiceFee
    }
  }
  if (key === 'year') {
    return {
      orderCount: data.yearOrderCount,
      energyKwh: data.yearEnergyKwh,
      revenue: data.yearRevenue,
      energyFee: data.yearEnergyFee,
      serviceFee: data.yearServiceFee
    }
  }
  return {
    orderCount: data.todayOrderCount,
    energyKwh: data.todayEnergyKwh,
    revenue: data.todayRevenue,
    energyFee: data.todayEnergyFee,
    serviceFee: data.todayServiceFee
  }
}

const revenueDelta = computed(() => delta(summary.value.todayRevenue, summary.value.yesterdayRevenue))

const bandStats = computed(() => {
  const data = summary.value || {}
  return [
    { label: '实时总功率', value: formatNumber(data.currentPowerKw, 2), unit: 'kW' },
    { label: '在充端口', value: data.chargingPortCount || 0, unit: '口' },
    { label: '在线设备', value: `${data.onlineDeviceCount || 0}/${data.deviceCount || 0}` },
    { label: '未闭环告警', value: data.openAlertCount || 0, unit: '条' }
  ]
})

const assetCards = computed(() => {
  const data = summary.value || {}
  const capacity = stationRows.value.reduce((acc, row) => acc + Number(row.capacityKw || 0), 0)
  const portTotal = stationRows.value.reduce((acc, row) => acc + Number(row.portCount || 0), 0)
  return [
    {
      label: '场站',
      value: data.stationCount || 0,
      unit: '座',
      note: `额定总容量 ${formatNumber(capacity, 1)} kW`,
      icon: 'guide',
      tone: ''
    },
    {
      label: '充电桩',
      value: data.deviceCount || 0,
      unit: '台',
      note: `在线 ${data.onlineDeviceCount || 0} · 离线 ${(data.deviceCount || 0) - (data.onlineDeviceCount || 0)}`,
      icon: 'monitor',
      tone: 'blue'
    },
    {
      label: '充电口',
      value: portTotal || data.portCount || 0,
      unit: '口',
      note: `在充 ${data.chargingPortCount || 0} · 故障 ${data.faultPortCount || 0}`,
      icon: 'switch',
      tone: 'gray'
    },
    {
      label: '进行中订单',
      value: data.activeOrderCount || 0,
      unit: '单',
      note: data.activeOrderCount ? '点击查看充电实况' : '当前没有充电中订单',
      icon: 'time',
      tone: data.activeOrderCount ? 'warm' : '',
      link: data.activeOrderCount && canLive.value ? '/operations/charging' : undefined
    }
  ]
})

const periodCards = computed(() => {
  const data = summary.value || {}
  const today = bucket('today')
  const month = bucket('month')
  const year = bucket('year')
  const dayOfMonth = new Date().getDate()
  return [
    {
      key: 'today',
      label: '今日营收',
      value: money(today.revenue),
      note: `${today.orderCount || 0} 笔 · 未支付 ${data.todayUnpaidCount || 0} 笔`,
      delta: delta(today.revenue, data.yesterdayRevenue)
    },
    {
      key: 'month',
      label: '本月营收',
      value: money(month.revenue),
      note: `${month.orderCount || 0} 笔 · 日均 ${money((Number(month.revenue || 0) / dayOfMonth).toFixed(2))}`,
      delta: delta(month.revenue, data.lastMonthRevenue)
    },
    {
      key: 'year',
      label: '今年营收',
      value: money(year.revenue),
      note: `${year.orderCount || 0} 笔 · 客单 ${money(perUnit(year.revenue, year.orderCount))}`,
      delta: delta(year.revenue, data.lastYearRevenue)
    },
    {
      key: 'energy',
      label: '今日充电量',
      value: formatNumber(today.energyKwh, 2),
      unit: '度',
      note: `度电均价 ${perUnit(today.revenue, today.energyKwh)} 元`,
      delta: delta(today.energyKwh, data.yesterdayEnergyKwh)
    }
  ]
})

const tiles = computed(() => {
  const data = bucket(period.value)
  const revenue = Number(data.revenue || 0)
  return [
    {
      label: `${periodLabel.value}订单`,
      value: data.orderCount || 0,
      unit: '笔',
      note: `进行中 ${summary.value.activeOrderCount || 0} 单`
    },
    {
      label: `${periodLabel.value}充电量`,
      value: formatNumber(data.energyKwh, 2),
      unit: '度',
      note: `度电均价 ${perUnit(revenue, data.energyKwh)} 元`
    },
    {
      label: `${periodLabel.value}金额`,
      value: money(revenue),
      note: period.value === 'today'
        ? `退款 ${summary.value.todayRefundCount || 0} 笔 · 不含已退款`
        : '不含已退款订单'
    },
    { label: `${periodLabel.value}电费`, value: money(data.energyFee), note: share(data.energyFee, revenue) },
    { label: `${periodLabel.value}服务费`, value: money(data.serviceFee), note: share(data.serviceFee, revenue) }
  ]
})

function params() {
  return stationId.value ? { stationId: stationId.value } : {}
}

function pickPeriod(key) {
  if (PERIOD_LABEL[key]) {
    period.value = key
  }
}

function openCard(link) {
  if (link) {
    router.push(link)
  }
}

function loadSummary() {
  return getDashboardSummary(params()).then(res => {
    summary.value = res.data || {}
  })
}

function loadStations() {
  tableLoading.value = true
  return getStationOverview(params()).then(res => {
    const rows = res.data || []
    stationRows.value = stationId.value ? rows.filter(row => row.stationId === stationId.value) : rows
  }).finally(() => {
    tableLoading.value = false
  })
}

function loadGroups() {
  if (detailMode.value !== 'group') {
    return Promise.resolve()
  }
  groupLoading.value = true
  return getGroupOverview(params()).then(res => {
    groupRows.value = res.data || []
  }).finally(() => {
    groupLoading.value = false
  })
}

function loadDetail() {
  return detailMode.value === 'group' ? loadGroups() : loadStations()
}

function loadTrend() {
  return getBusinessTrend({ ...params(), range: trendRange.value }).then(res => {
    renderTrend(res.data || [])
  })
}

function loadAlertStats() {
  return getAlertStats(params()).then(res => {
    const stats = res.data || {}
    renderAlert(stats.byType || [])
  })
}

function reloadAll() {
  return Promise.all([loadSummary(), loadStations(), loadGroups(), loadTrend(), loadAlertStats()])
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

function renderTrend(points) {
  trendChart = ensureChart(trendChart, trendRef.value)
  if (!trendChart) {
    return
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['充电量/度', '订单金额/元', '服务费/元'], right: 8, top: 0, textStyle: { color: '#63736c' } },
    grid: { left: 56, right: 58, top: 42, bottom: 30 },
    xAxis: {
      type: 'category',
      data: points.map(point => point.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e3eae6' } },
      axisLabel: { color: '#899a92', interval: points.length > 16 ? 2 : 0 }
    },
    yAxis: [
      { type: 'value', name: '度', axisLabel: { color: '#899a92' }, splitLine: { lineStyle: { color: '#eef2f0' } } },
      { type: 'value', name: '元', axisLabel: { color: '#899a92' }, splitLine: { show: false } }
    ],
    series: [
      {
        name: '充电量/度',
        type: 'bar',
        barMaxWidth: 22,
        data: points.map(point => Number(point.energyKwh || 0)),
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#00c88a' },
            { offset: 1, color: 'rgba(0, 168, 112, 0.42)' }
          ])
        }
      },
      {
        name: '订单金额/元',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        data: points.map(point => Number(point.amount || 0)),
        lineStyle: { color: '#f79009', width: 2 },
        itemStyle: { color: '#f79009' }
      },
      {
        name: '服务费/元',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        data: points.map(point => Number(point.serviceFee || 0)),
        lineStyle: { color: '#528bfd', width: 2, type: 'dashed' },
        itemStyle: { color: '#528bfd' }
      }
    ]
  }, true)
  trendChart.resize()
}

function renderAlert(byType) {
  alertChart = ensureChart(alertChart, alertRef.value)
  if (!alertChart) {
    return
  }
  const data = byType.map(item => ({ name: item.label || alertTypeLabel(item.code), value: item.count }))
  alertChart.setOption({
    color: ['#00a870', '#528bfd', '#f79009', '#8b5cf6', '#f04438', '#7be3bd'],
    tooltip: { trigger: 'item', formatter: '{b}：{c} 条（{d}%）' },
    legend: { bottom: 0, left: 'center', textStyle: { color: '#63736c' } },
    series: [{
      type: 'pie',
      radius: ['52%', '72%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}\n{c}', color: '#4a5b53', fontSize: 11 },
      data: data.length ? data : [{ name: '今日暂无告警', value: 1, itemStyle: { color: '#e8eee9' } }]
    }]
  }, true)
  alertChart.resize()
}

function goLive(id) {
  if (!canLive.value) {
    return
  }
  const target = id || stationId.value
  router.push({ path: '/operations/charging', query: target ? { stationId: target } : {} })
}

function tick() {
  if (document.hidden) {
    return
  }
  countdown.value -= 1
  if (countdown.value <= 0) {
    countdown.value = REFRESH_SECONDS
    reloadAll()
  }
}

function scheduleRefresh(withStats) {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  debounceTimer = setTimeout(() => {
    debounceTimer = null
    loadSummary()
    loadStations()
    if (withStats) {
      loadAlertStats()
    }
  }, LIVE_DEBOUNCE)
}

function handleVisibility() {
  if (!document.hidden) {
    countdown.value = REFRESH_SECONDS
    reloadAll()
  }
}

function handleResize() {
  trendChart?.resize()
  alertChart?.resize()
}

onMounted(() => {
  getStationOptions().then(res => {
    stationOptions.value = res.data || []
  })
  reloadAll().then(() => nextTick(handleResize))
  ticker = setInterval(tick, 1000)
  document.addEventListener('visibilitychange', handleVisibility)
  window.addEventListener('resize', handleResize)
  unsubscribe = subscribeEventStream({
    power: () => {
      loadSummary()
      loadStations()
    },
    alert: () => scheduleRefresh(true),
    order: () => scheduleRefresh(false),
    device: () => scheduleRefresh(false)
  }, value => {
    live.value = value
  })
})

onBeforeUnmount(() => {
  unsubscribe?.()
  if (ticker) {
    clearInterval(ticker)
  }
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  document.removeEventListener('visibilitychange', handleVisibility)
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  alertChart?.dispose()
  trendChart = null
  alertChart = null
})
</script>

<style lang="scss" scoped>
.home {
  .band {
    position: relative;
    display: grid;
    grid-template-columns: minmax(300px, 1.15fr) auto minmax(210px, auto);
    align-items: center;
    gap: 26px;
    padding: 22px 28px;
    overflow: hidden;
    border-radius: 16px;
    color: #eaf6f1;
    background:
      radial-gradient(circle at 82% 18%, rgba(0, 220, 160, 0.28), transparent 46%),
      linear-gradient(112deg, #0a2b22 0%, #007953 58%, #00a870 100%);
    box-shadow: 0 18px 38px rgba(6, 60, 42, 0.22);
  }

  .band-rings {
    position: absolute;
    right: 150px;
    top: -30px;
    width: 300px;
    height: 200px;
    opacity: 0.18;
    pointer-events: none;

    circle {
      fill: none;
      stroke: #d6fff0;
      stroke-width: 1.4;
    }
  }

  .band-k {
    margin: 0 0 8px;
    font-size: 12px;
    letter-spacing: 2.4px;
    color: #8ff0cb;
  }

  .band-big {
    display: flex;
    align-items: baseline;
    gap: 10px;

    strong {
      font-size: 38px;
      font-weight: 700;
      letter-spacing: 1px;
      line-height: 1.1;
    }

    small {
      font-size: 13px;
      color: #b6e9d5;
    }
  }

  .band-delta {
    padding: 3px 9px;
    border-radius: 999px;
    font-size: 12px;
    background: rgba(0, 200, 138, 0.24);
    color: #a7f5d5;

    &.down {
      background: rgba(240, 68, 56, 0.24);
      color: #ffc9c4;
    }
  }

  .band-sub {
    margin: 10px 0 0;
    font-size: 13px;
    color: #b6e9d5;
  }

  .band-stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(96px, auto));
    gap: 10px;
  }

  .band-stat {
    display: flex;
    flex-direction: column;
    gap: 5px;
    padding: 12px 14px;
    border: 1px solid rgba(190, 245, 220, 0.22);
    border-radius: 12px;
    background: rgba(4, 40, 28, 0.22);

    .bs-label {
      font-size: 12px;
      color: #9adfc4;
    }

    .bs-value {
      font-size: 20px;
      font-weight: 600;

      small {
        margin-left: 3px;
        font-size: 11px;
        font-weight: 400;
        color: #9adfc4;
      }
    }
  }

  .band-side {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 10px;
  }

  .station-select {
    width: 190px;
  }

  .band-link {
    color: #8ff0cb;
    margin-left: 0;
  }

  .live-chip {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 5px 13px;
    border: 1px solid rgba(143, 240, 203, 0.4);
    border-radius: 999px;
    background: rgba(0, 200, 138, 0.14);
    color: #cdf7e7;
    font-size: 12px;

    .dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #22e3a6;
      animation: bc-pulse 1.6s ease-in-out infinite;
    }

    &.offline {
      border-color: rgba(203, 223, 214, 0.35);
      color: #d6e7de;

      .dot {
        background: #b7c9c0;
        animation: none;
      }
    }
  }

  .kpi-row {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin-top: 14px;
  }

  .kpi {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
    border: 1px solid var(--bc-border);
    border-radius: 12px;
    background: var(--bc-surface);
    box-shadow: 0 4px 14px rgba(11, 31, 26, 0.04);

    .ic {
      flex: none;
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border-radius: 10px;
      background: linear-gradient(140deg, #00a870, #00c88a);
      color: #fff;
      font-size: 17px;
    }

    &.blue .ic {
      background: linear-gradient(140deg, #528bfd, #7aa8ff);
    }

    &.gray .ic {
      background: linear-gradient(140deg, #6b7d75, #98a9a1);
    }

    &.warm .ic {
      background: linear-gradient(140deg, #f79009, #ffb24d);
    }

    .kpi-body {
      min-width: 0;
    }

    .kpi-label {
      color: var(--bc-text-muted);
      font-size: 13px;
    }

    .kpi-value {
      margin: 7px 0 5px;
      font-size: 23px;
      font-weight: 700;
      color: var(--bc-text);
      line-height: 1.1;

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

    &.block {
      display: block;
      padding: 16px 18px;
    }

    &.clickable {
      cursor: pointer;
      transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 12px 26px rgba(11, 31, 26, 0.1);
      }
    }

    &.active {
      border-color: var(--bc-brand);
      box-shadow: 0 8px 22px rgba(0, 168, 112, 0.16);
    }
  }

  .kpi-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .delta {
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    background: var(--el-color-primary-light-9);
    color: var(--bc-brand-deep);

    &.down {
      background: #feecec;
      color: #d92d20;
    }
  }

  .panel {
    margin-top: 14px;
    padding: 16px 18px;
    border: 1px solid var(--bc-border);
    border-radius: 12px;
    background: var(--bc-surface);
    box-shadow: 0 4px 14px rgba(11, 31, 26, 0.04);
  }

  .panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;

    .panel-title {
      display: flex;
      align-items: center;
      gap: 14px;
    }

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

  .tile-row {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 12px;
  }

  .tile {
    padding: 13px 15px;
    border: 1px solid #eaf0ec;
    border-radius: 10px;
    background: var(--bc-surface-inset);

    .tile-label {
      font-size: 12px;
      color: var(--bc-text-muted);
    }

    .tile-value {
      margin: 8px 0 5px;
      font-size: 21px;
      font-weight: 700;
      color: var(--bc-text);

      small {
        margin-left: 3px;
        font-size: 12px;
        font-weight: 400;
        color: #93a49c;
      }
    }

    .tile-note {
      font-size: 12px;
      color: #93a49c;
    }
  }

  .panel-note {
    margin: 6px 0 0;
    font-size: 12px;
    color: #93a49c;
  }

  .chart {
    width: 100%;
  }

  .chart-box {
    height: 268px;
  }

  .station-table {
    .st-name {
      font-weight: 600;
      color: var(--bc-text);
    }

    .st-sub {
      margin-top: 2px;
      font-size: 12px;
      color: #93a49c;
    }

    .st-power {
      font-weight: 600;
      color: var(--bc-brand-deep);
    }

    .st-fee {
      font-weight: 600;
    }

    .st-fault {
      color: var(--bc-danger);
    }

    :deep(.el-table__header th) {
      background: #f4f8f5;
      color: var(--bc-text-muted);
      font-weight: 600;
    }
  }

  @keyframes bc-pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.35; transform: scale(0.78); }
  }

  @media (max-width: 1200px) {
    .band {
      grid-template-columns: 1fr;
    }

    .band-rings {
      display: none;
    }

    .band-side {
      flex-direction: row;
      align-items: center;
      flex-wrap: wrap;
    }

    .kpi-row {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .tile-row {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  @media (max-width: 768px) {
    .band-stats,
    .kpi-row,
    .tile-row {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
}
</style>
