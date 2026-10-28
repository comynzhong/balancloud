<template>
  <div class="app-container morning-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · MORNING REPORT</p>
        <h2>运营晨报</h2>
        <p class="page-desc">
          昨日订单、电量、营收、用户、告警与设备状态一页看完；经营、告警与设备指标按当前账号的场站授权收敛，
          新增用户为租户口径。每日 08:00（北京时间）自动向租户推一条站内信，同一租户同一天只推一次。
        </p>
      </div>
      <div class="page-actions">
        <el-date-picker
          v-model="queryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="默认昨天"
          :disabled-date="disableFuture"
          :clearable="true"
          style="width: 150px"
        />
        <el-button type="primary" icon="Search" :loading="loading" @click="load">查询</el-button>
        <el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button
          v-hasPermi="['report:morning:push']"
          type="success"
          plain
          icon="Promotion"
          :loading="pushing"
          @click="handlePush"
        >
          推送晨报
        </el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="tip" />

    <section class="band">
      <div class="band-main">
        <p class="band-k">{{ dateLabel(report.date) }} · 昨日经营</p>
        <div class="band-big">
          <strong>{{ formatMoney(report.revenue) }}</strong>
          <small>充电营收</small>
          <span class="growth" :class="growthClass(growth.revenuePercent)">
            {{ growthText(growth.revenuePercent) }}
          </span>
        </div>
        <p class="band-sub">{{ report.headline || '正在生成晨报摘要…' }}</p>
      </div>
      <div class="band-stats">
        <div v-for="item in bandStats" :key="item.label" class="band-stat">
          <span class="bs-label">{{ item.label }}</span>
          <span class="bs-value">{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></span>
        </div>
      </div>
    </section>

    <div v-loading="loading" class="kpi-row">
      <article class="kpi">
        <div class="kpi-label">订单</div>
        <div class="kpi-value">{{ report.orderCount || 0 }}<small>笔</small></div>
        <div class="kpi-note">
          完成 {{ report.completedCount || 0 }} · 异常 {{ report.abnormalCount || 0 }} · 失败 {{ report.failedCount || 0 }}
        </div>
        <div class="kpi-trend" :class="growthClass(growth.orderPercent)">
          环比 {{ growthText(growth.orderPercent) }}
        </div>
      </article>

      <article class="kpi blue">
        <div class="kpi-label">充电量</div>
        <div class="kpi-value">{{ formatNumber(report.energyKwh, 1) }}<small>kWh</small></div>
        <div class="kpi-note">电费 {{ formatMoney(report.energyFee) }} · 服务费 {{ formatMoney(report.serviceFee) }}</div>
        <div class="kpi-trend" :class="growthClass(growth.energyPercent)">
          环比 {{ growthText(growth.energyPercent) }}
        </div>
      </article>

      <article class="kpi" :class="{ danger: report.unresolvedAlertCount > 0 }">
        <div class="kpi-label">告警</div>
        <div class="kpi-value">{{ report.alertCount || 0 }}<small>条</small></div>
        <div class="kpi-note">
          紧急 {{ report.criticalAlertCount || 0 }} · 重要 {{ report.majorAlertCount || 0 }} · 未闭环 {{ report.unresolvedAlertCount || 0 }}
        </div>
      </article>

      <article class="kpi warm">
        <div class="kpi-label">设备在线</div>
        <div class="kpi-value">{{ report.onlineDeviceCount || 0 }}<small>/ {{ report.deviceCount || 0 }} 台</small></div>
        <div class="kpi-note">离线 {{ report.offlineDeviceCount || 0 }} 台 · 故障端口 {{ report.faultPortCount || 0 }} 口</div>
      </article>

      <article class="kpi gray">
        <div class="kpi-label">新增用户</div>
        <div class="kpi-value">{{ report.newUserCount || 0 }}<small>人</small></div>
        <div class="kpi-note">当日注册的小程序账号（租户口径）</div>
      </article>

      <article class="kpi gray">
        <div class="kpi-label">活跃用户</div>
        <div class="kpi-value">{{ report.activeUserCount || 0 }}<small>人</small></div>
        <div class="kpi-note">当日产生过订单的用户</div>
      </article>

      <article class="kpi blue">
        <div class="kpi-label">未支付</div>
        <div class="kpi-value">{{ report.unpaidCount || 0 }}<small>笔</small></div>
        <div class="kpi-note">完成或异常结束但未支付</div>
      </article>

      <article class="kpi" :class="{ danger: report.refundCount > 0 }">
        <div class="kpi-label">退款</div>
        <div class="kpi-value">{{ report.refundCount || 0 }}<small>笔</small></div>
        <div class="kpi-note">金额 {{ formatMoney(report.refundAmount) }}</div>
      </article>
    </div>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">场站贡献 Top 5</span>
          <span class="muted">按下单时间统计，失败单不计，退款单不计营收。</span>
        </div>
      </template>
      <el-table :data="report.topStations || []" row-key="stationId">
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column label="场站" min-width="220">
          <template #default="{ row }">
            <span class="strong">{{ row.stationName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单" width="110" align="right">
          <template #default="{ row }">{{ row.orderCount || 0 }} 笔</template>
        </el-table-column>
        <el-table-column label="充电量" width="140" align="right">
          <template #default="{ row }">{{ formatNumber(row.energyKwh, 1) }} kWh</template>
        </el-table-column>
        <el-table-column label="营收" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.revenue) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="昨日没有可统计的订单" :image-size="80" />
        </template>
      </el-table>
    </el-card>

    <el-card shadow="never" class="table-card detail-card">
      <template #header>
        <span class="card-title">费用与状态明细</span>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="时间费">{{ formatMoney(report.timeFee) }}</el-descriptions-item>
        <el-descriptions-item label="电费">{{ formatMoney(report.energyFee) }}</el-descriptions-item>
        <el-descriptions-item label="服务费">{{ formatMoney(report.serviceFee) }}</el-descriptions-item>
        <el-descriptions-item label="营收合计">{{ formatMoney(report.revenue) }}</el-descriptions-item>
        <el-descriptions-item label="退款金额">{{ formatMoney(report.refundAmount) }}</el-descriptions-item>
        <el-descriptions-item label="端口总数">{{ report.portCount || 0 }} 口</el-descriptions-item>
        <el-descriptions-item label="故障端口">{{ report.faultPortCount || 0 }} 口</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ formatTime(report.generatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <p class="foot-note">
        每日 08:00 自动推送的站内信是租户级口径；手动推送不受当前账号场站权限影响，仅超管可见。
        多实例部署下由 Redis 窗口锁先挡一层，站内信 bizType + bizId 再挡一层。
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMorningReport, pushMorningReport } from '@/api/balan/report'
import { formatMoney, formatNumber, formatTime } from '@/utils/balan'

defineOptions({ name: 'BalanReportMorning' })

const loading = ref(false)
const pushing = ref(false)
const error = ref('')
const queryDate = ref(yesterday())
const data = ref({})

const report = computed(() => data.value || {})
const growth = computed(() => report.value.growth || {})
const bandStats = computed(() => [
  { label: '订单', value: report.value.orderCount || 0, unit: '笔' },
  { label: '充电量', value: formatNumber(report.value.energyKwh, 1), unit: 'kWh' },
  { label: '告警', value: report.value.alertCount || 0, unit: '条' },
  { label: '在线设备', value: `${report.value.onlineDeviceCount || 0}/${report.value.deviceCount || 0}` }
])

function yesterday() {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return localDate(date)
}

function localDate(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function disableFuture(date) {
  return date.getTime() > Date.now()
}

function dateLabel(value) {
  if (!value) {
    return '昨日'
  }
  const parts = String(value).split('-')
  if (parts.length !== 3) {
    return value
  }
  return `${parts[0]} 年 ${Number(parts[1])} 月 ${Number(parts[2])} 日`
}

function growthText(value) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  const number = Number(value)
  return `${number > 0 ? '+' : ''}${number.toFixed(1)}%`
}

function growthClass(value) {
  if (value === null || value === undefined || value === '') {
    return 'flat'
  }
  const number = Number(value)
  if (number > 0) {
    return 'up'
  }
  return number < 0 ? 'down' : 'flat'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getMorningReport(queryDate.value || undefined)
    data.value = res.data || {}
  } catch (e) {
    error.value = e?.message || '晨报加载失败'
  } finally {
    loading.value = false
  }
}

async function handlePush() {
  const targetDate = queryDate.value || report.value.date
  try {
    await ElMessageBox.confirm(
      `将向全租户推送 ${dateLabel(targetDate)} 的运营晨报站内信，口径为全租户、不受当前账号场站权限影响。确定继续？`,
      '推送晨报',
      { type: 'warning', confirmButtonText: '推送', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  pushing.value = true
  try {
    const res = await pushMorningReport(targetDate)
    const result = res.data || {}
    if (result.pushed) {
      ElMessage.success(`已推送：${result.title || '运营晨报'}`)
    } else {
      ElMessage.info(result.reason || '未推送')
    }
  } catch (e) {
    ElMessage.error(e?.message || '推送失败')
  } finally {
    pushing.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.morning-page {
  .page-head {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    margin-bottom: 14px;
    gap: 12px;
    flex-wrap: wrap;
  }

  .page-kicker {
    margin: 0 0 4px;
    font-size: 12px;
    letter-spacing: 1px;
    color: var(--el-color-primary);
  }

  .page-head h2 {
    margin: 0 0 4px;
    font-size: 20px;
  }

  .page-desc {
    margin: 0;
    max-width: 840px;
    font-size: 13px;
    line-height: 1.7;
    color: var(--el-text-color-secondary);
  }

  .page-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .tip {
    margin-bottom: 14px;
  }

  .band {
    display: grid;
    grid-template-columns: minmax(300px, 1.15fr) auto;
    align-items: center;
    gap: 24px;
    padding: 22px 26px;
    border-radius: 16px;
    color: #eaf6f1;
    background:
      radial-gradient(circle at 84% 18%, rgba(255, 196, 92, 0.26), transparent 48%),
      linear-gradient(112deg, #0a2b22 0%, #007953 58%, #00a870 100%);
    box-shadow: 0 18px 38px rgba(6, 60, 42, 0.22);
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
    flex-wrap: wrap;

    strong {
      font-size: 36px;
      font-weight: 700;
      line-height: 1.1;
    }

    small {
      font-size: 13px;
      color: #b6e9d5;
    }
  }

  .band-sub {
    margin: 10px 0 0;
    font-size: 13px;
    line-height: 1.7;
    color: #b6e9d5;
  }

  .growth {
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    background: rgba(4, 40, 28, 0.28);
    color: #c9f5e2;

    &.up {
      color: #9ff5cf;
    }

    &.down {
      color: #ffb4ae;
    }
  }

  .band-stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(100px, auto));
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

  .kpi-row {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin-top: 14px;
    min-height: 120px;
  }

  .kpi {
    padding: 14px 16px;
    border: 1px solid var(--bc-border, var(--el-border-color-lighter));
    border-left: 3px solid var(--el-color-primary);
    border-radius: 12px;
    background: var(--bc-surface, #fff);
    box-shadow: 0 4px 14px rgba(11, 31, 26, 0.04);

    &.blue {
      border-left-color: #528bfd;
    }

    &.gray {
      border-left-color: #6b7d75;
    }

    &.warm {
      border-left-color: #f79009;
    }

    &.danger {
      border-left-color: var(--el-color-danger);
    }
  }

  .kpi-label {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .kpi-value {
    margin: 7px 0 5px;
    font-size: 23px;
    font-weight: 700;
    line-height: 1.1;

    small {
      margin-left: 4px;
      font-size: 12px;
      font-weight: 400;
      color: #93a49c;
    }
  }

  .kpi-note {
    font-size: 12px;
    line-height: 1.6;
    color: #93a49c;
  }

  .kpi-trend {
    margin-top: 6px;
    font-size: 12px;
    color: #93a49c;

    &.up {
      color: var(--el-color-success);
    }

    &.down {
      color: var(--el-color-danger);
    }
  }

  .table-card {
    margin-top: 14px;
    border-radius: 10px;
  }

  .detail-card {
    margin-bottom: 8px;
  }

  .card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .card-title {
    font-weight: 600;
  }

  .strong {
    font-weight: 600;
  }

  .muted {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .foot-note {
    margin: 14px 0 0;
    font-size: 12px;
    line-height: 1.7;
    color: var(--el-text-color-secondary);
  }

  @media (max-width: 1200px) {
    .band {
      grid-template-columns: 1fr;
    }

    .kpi-row {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 768px) {
    .band-stats,
    .kpi-row {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
}
</style>
