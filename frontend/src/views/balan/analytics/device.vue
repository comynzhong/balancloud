<template>
  <div class="app-container analytics-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · DEVICE ANALYTICS</p>
        <h2>设备分析</h2>
        <p class="page-desc">
          按设备统计利用率、故障率与平均充电时长；利用率 = 充电时长 ÷（端口数 × 统计时长），故障口径为故障、离线与停止超时。
        </p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" label-width="72px">
        <el-form-item label="统计区间">
          <el-date-picker
            v-model="query.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :disabled-date="disabledDate"
            @change="load"
          />
        </el-form-item>
        <el-form-item label="场站">
          <el-select v-model="query.stationId" placeholder="全部场站" clearable style="width: 180px" @change="load">
            <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="14" class="summary-row">
      <el-col v-for="item in summaryCards" :key="item.label" :xs="12" :sm="6">
        <el-card shadow="never" class="summary-card" :class="item.tone">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ devices.length }} 台设备</el-tag>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="devices" row-key="chargerId">
        <el-table-column label="设备" min-width="200">
          <template #default="{ row }">
            <div class="strong">{{ row.deviceName || row.deviceNo }}</div>
            <div class="muted">{{ row.deviceNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="场站" min-width="150" prop="stationName" show-overflow-tooltip />
        <el-table-column label="端口" width="80" align="center" prop="portCount" />
        <el-table-column label="订单" width="80" align="center" prop="orderCount" />
        <el-table-column label="充电时长" width="110" align="right">
          <template #default="{ row }">{{ formatHours(row.chargingHours) }}</template>
        </el-table-column>
        <el-table-column label="利用率" width="150" align="center">
          <template #default="{ row }">
            <el-progress :percentage="Number(row.utilizationRate || 0)" :stroke-width="8" :color="rateColor(row.utilizationRate)" />
          </template>
        </el-table-column>
        <el-table-column label="故障率" width="120" align="center">
          <template #default="{ row }">
            <span :class="{ 'danger-text': Number(row.faultRate || 0) > 0 }">{{ formatPercent(row.faultRate) }}</span>
            <div class="muted">{{ row.faultOrders }} 单</div>
          </template>
        </el-table-column>
        <el-table-column label="平均时长" width="110" align="right">
          <template #default="{ row }">{{ formatMinutes(row.avgDurationMinutes) }}</template>
        </el-table-column>
        <el-table-column label="电量" width="110" align="right">
          <template #default="{ row }">{{ formatKwh(row.energyKwh) }}</template>
        </el-table-column>
        <el-table-column label="营收" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.revenue) }}</template>
        </el-table-column>
        <el-table-column label="故障端口" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.faultPorts > 0" size="small" type="danger" effect="plain">{{ row.faultPorts }}</el-tag>
            <span v-else class="muted">0</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="所选区间内没有设备数据" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getDeviceAnalytics } from '@/api/balan/analytics'
import { getStationOptions } from '@/api/balan/station'

defineOptions({ name: 'DeviceAnalytics' })

const loading = ref(false)
const stationOptions = ref([])
const summary = ref({})
const devices = ref([])
const query = reactive({ range: defaultRange(), stationId: undefined })

function defaultRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 6)
  return [formatDate(start), formatDate(end)]
}

function formatDate(date) {
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function disabledDate(date) {
  return date.getTime() > Date.now()
}

function params() {
  const [startDate, endDate] = query.range || []
  return { startDate, endDate, stationId: query.stationId }
}

function load() {
  loading.value = true
  getDeviceAnalytics(params())
    .then(res => {
      summary.value = res.data?.summary || {}
      devices.value = res.data?.devices || []
    })
    .finally(() => {
      loading.value = false
    })
}

const summaryCards = computed(() => {
  const data = summary.value
  return [
    {
      label: '充电电量',
      value: `${formatKwh(data.energyKwh)} kWh`,
      note: `营收 ${formatMoney(data.revenue)}`,
      tone: 'success'
    },
    {
      label: '设备利用率',
      value: formatPercent(data.utilizationRate),
      note: `${data.chargerCount || 0} 台设备 · ${data.portCount || 0} 个端口`,
      tone: 'success'
    },
    {
      label: '故障率',
      value: formatPercent(data.faultRate),
      note: `${data.faultOrders || 0} 单故障 · ${data.faultPorts || 0} 个端口`,
      tone: Number(data.faultRate || 0) > 0 ? 'warning' : ''
    },
    {
      label: '平均充电时长',
      value: formatMinutes(data.avgDurationMinutes),
      note: `累计 ${formatHours(data.chargingHours)} · ${data.orderCount || 0} 单`,
      tone: ''
    }
  ]
})

function rateColor(value) {
  const rate = Number(value || 0)
  if (rate >= 60) return '#16a34a'
  if (rate >= 30) return '#0ea5e9'
  return '#94a3b8'
}

function formatPercent(value) {
  return `${Number(value || 0).toFixed(2)}%`
}

function formatHours(value) {
  return `${Number(value || 0).toFixed(2)} h`
}

function formatKwh(value) {
  return Number(value || 0).toFixed(2)
}

function formatMinutes(value) {
  const minutes = Number(value || 0)
  if (minutes < 60) return `${minutes.toFixed(1)} 分`
  return `${Math.floor(minutes / 60)} 时 ${Math.round(minutes % 60)} 分`
}

function formatMoney(value) {
  return `¥${Number(value || 0).toFixed(2)}`
}

onMounted(() => {
  getStationOptions().then(res => {
    stationOptions.value = res.data || []
  })
  load()
})
</script>

<style scoped lang="scss">
.analytics-page {
  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 18px;
  }

  .page-kicker {
    margin: 0 0 6px;
    color: #16a34a;
    font-size: 11px;
    letter-spacing: 2px;
  }

  h2 {
    margin: 0;
    color: #0f172a;
    font-size: 24px;
  }

  .page-desc {
    margin: 8px 0 0;
    color: #64748b;
  }

  .filter-card {
    margin-bottom: 14px;
    border: 1px solid #e2e8f0;
    border-radius: 12px;

    :deep(.el-card__body) {
      padding-bottom: 2px;
    }
  }

  .summary-row {
    margin-bottom: 14px;
  }

  .summary-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;

    :deep(.el-card__body) {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 16px 18px;
    }

    span {
      color: #64748b;
      font-size: 13px;
    }

    strong {
      color: #0f172a;
      font-size: 24px;
      line-height: 1.2;
    }

    small {
      color: #94a3b8;
      font-size: 12px;
    }

    &.success strong {
      color: #16a34a;
    }

    &.warning strong {
      color: #d97706;
    }
  }

  .table-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;
  }

  .toolbar {
    display: flex;
    align-items: center;
    margin-bottom: 10px;
  }

  .strong {
    color: #0f172a;
    font-weight: 600;
  }

  .muted {
    color: #94a3b8;
    font-size: 12px;
  }

  .danger-text {
    color: #dc2626;
  }
}
</style>
